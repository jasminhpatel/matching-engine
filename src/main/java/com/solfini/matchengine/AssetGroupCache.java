package com.solfini.matchengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.sbe.encoder.TokenType;
import com.solfini.sbe.encoder.UpdateType;
import com.solfini.util.FastArrayList;

public class AssetGroupCache implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(AssetGroupCache.class);
  //<groupId, AssetGroup>
  private static final ConcurrentHashMap<Long, AssetGroup> idToAssetGroupMap = new ConcurrentHashMap<>();
  //<userId, <groupId, AssetGroup>>
  private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, AssetGroup>> userIdToAssetGroupsMap = new ConcurrentHashMap<>();
  //<userId, <assetId, AssetGroup>> ERC 20
  private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, AssetGroup>> userIdToAssetIdToAssetGroupsMap = new ConcurrentHashMap<>();

  private static long nextId = 1;
  private static long lastUpdatedTime = 0;
  private static final long TIME_THRESHOLD = 300_000;

  private static final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();

  public static final void onLoad(final AssetGroup assetGroup) {
    idToAssetGroupMap.put(assetGroup.getId(), assetGroup);
    if (assetGroup.getId() >= nextId)
      nextId = assetGroup.getId() + 1;

    // cache by userId
    final ConcurrentHashMap<Long, AssetGroup> assetGroupsForUser = userIdToAssetGroupsMap.computeIfAbsent(assetGroup.getOwnerUserId(),
        v -> new ConcurrentHashMap<>());
    assetGroupsForUser.put(assetGroup.getId(), assetGroup);

    // cache by securityId
    if (TokenType.ERC20 == assetGroup.getTokenType()) {
      final ConcurrentHashMap<Long, AssetGroup> userIdToERC20AssetGroupsMap =
          userIdToAssetIdToAssetGroupsMap.computeIfAbsent(assetGroup.getOwnerUserId(), v -> new ConcurrentHashMap<>());
      userIdToERC20AssetGroupsMap.put(assetGroup.getAssetId(), assetGroup);
    }
  }

  // update model
  public static final AssetGroup onModel(final AssetGroup assetGroup) {
    if (assetGroup.getId() == 0) { // assign new id
      assetGroup.setId(nextId);
      nextId++;
    }

    final AssetGroup prevAssetGroup = idToAssetGroupMap.get(assetGroup.getId());
    if (prevAssetGroup == null) { // new add
      if (UpdateType.DELETE != assetGroup.getUpdateType())
        onLoad(assetGroup);
    } else {
      //if owner has changed
      if (prevAssetGroup.getOwnerUserId() != assetGroup.getOwnerUserId()) {
        ConcurrentHashMap<Long, AssetGroup> userAssetGroups = userIdToAssetGroupsMap.get(prevAssetGroup.getOwnerUserId());
        if (userAssetGroups != null) {
          userAssetGroups.remove(prevAssetGroup.getId());
        }
        if (TokenType.ERC20 == prevAssetGroup.getTokenType()) {
          userAssetGroups = userIdToAssetIdToAssetGroupsMap.get(prevAssetGroup.getOwnerUserId());
          if (userAssetGroups != null) {
            userAssetGroups.remove(prevAssetGroup.getAssetId());
          }
        }
      }
      prevAssetGroup.copySet(assetGroup);

      if (UpdateType.DELETE == assetGroup.getUpdateType())
        prevAssetGroup.removeAll(assetGroup.getAssetIdGroupTreeSet());
      else
        prevAssetGroup.addAll(assetGroup.getAssetIdGroupTreeSet());
    }

    matcherToPublisherQueue.addGuaranteed(assetGroup);

    return assetGroup;
  }

  public static final AssetGroup get(final long id) {
    final AssetGroup assetGroup = idToAssetGroupMap.get(id);
    if (assetGroup != null)
      return assetGroup;

    return null;
  }

  // getAll with filters
  public static final Collection<AssetGroup> getAll() {
    return idToAssetGroupMap.values();
  }

  public static final Collection<AssetGroup> getByUserId(final long userId, final int securityId) {
    final ConcurrentHashMap<Long, AssetGroup> userAssetGroupMap = userIdToAssetGroupsMap.get(userId);
    if (userAssetGroupMap != null && userAssetGroupMap.size() > 0) {
      final ArrayList<AssetGroup> list = new ArrayList<>();
      for (AssetGroup assetGroup : userAssetGroupMap.values()) {
        if (assetGroup.getSecurityId() == securityId)
          list.add(assetGroup);
      }

      return list;
    }
    return new ArrayList<>();
  }

  public static final AssetGroup getByUserIdAndERC20Asset(final long userId, final long assetId) {
    final ConcurrentHashMap<Long, AssetGroup> userAssetGroupMap = userIdToAssetIdToAssetGroupsMap.get(userId);
    if (userAssetGroupMap != null) {
      return userAssetGroupMap.get(assetId);
    }

    return null;
  }

  // called for snapshots
  public static final void restateAllAssetGroups(final long snapId) {
    for (final AssetGroup assetGroupSrc : idToAssetGroupMap.values()) {
      final AssetGroup assetGroup = new AssetGroup();
      assetGroup.copySet(assetGroupSrc);
      assetGroup.addAll(assetGroupSrc.getAssetIdGroupTreeSet());
      assetGroup.setSnapId(snapId);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_6, RESTATEALL_ASSET_GROUPS_EQ, assetGroup);
      }
      matcherToPublisherQueue.addGuaranteed(assetGroup);
    }

  }
}
