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
import com.solfini.sbe.encoder.UpdateType;
import com.solfini.util.FastArrayList;

public class AssetGroupCache implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(AssetGroupCache.class);

  private static final ConcurrentHashMap<Long, AssetGroup> idToAssetGroupMap = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, AssetGroup>> userIdToAssetGroupsMap = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, AssetGroup>> securityIdToAssetGroupsMap = new ConcurrentHashMap<>();

  private static long nextId = 1;
  private static long lastUpdatedTime = 0;
  private static final long TIME_THRESHOLD = 300_000;

  private static final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();

  public static final void onLoad(final AssetGroup assetGroup) {
    idToAssetGroupMap.put(assetGroup.getId(), assetGroup);
    if (assetGroup.getId() >= nextId)
      nextId = assetGroup.getId() + 1;

    // cache by userId
    ConcurrentHashMap<Long, AssetGroup> assetGroupsForUser = userIdToAssetGroupsMap.get((long) assetGroup.getOwnerUserId());
    if (assetGroupsForUser == null) {
      assetGroupsForUser = new ConcurrentHashMap<>();
      userIdToAssetGroupsMap.put((long) assetGroup.getOwnerUserId(), assetGroupsForUser);
    }
    assetGroupsForUser.put(assetGroup.getId(), assetGroup);

    // cache by securityId
    ConcurrentHashMap<Long, AssetGroup> assetGroupsForSecurityId = securityIdToAssetGroupsMap.get(assetGroup.getSecurityId());
    if (assetGroupsForSecurityId == null) {
      assetGroupsForSecurityId = new ConcurrentHashMap<>();
      securityIdToAssetGroupsMap.put(assetGroup.getSecurityId(), assetGroupsForSecurityId);
    }
    assetGroupsForSecurityId.put(assetGroup.getId(), assetGroup);
  }

  // update model
  public static final AssetGroup onModel(final AssetGroup assetGroup) {
    if (assetGroup.getId() == 0) { // assign new id
      assetGroup.setId(nextId);
      assetGroup.setGroupAssetId(nextId);
      nextId++;
    }

    final AssetGroup prevAssetGroup = idToAssetGroupMap.get(assetGroup.getId());
    if (prevAssetGroup == null) { // new add
      if (UpdateType.DELETE != assetGroup.getUpdateType())
        AssetGroupCache.onLoad(assetGroup);
    } else {
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
    // return loadFromDBWithId(id);
  }

  // getAll with filters
  public static final Collection<AssetGroup> getAll() {
    return idToAssetGroupMap.values();
  }

  public static final Collection<AssetGroup> getByUserId(final int userId) {
    final ConcurrentHashMap<Long, AssetGroup> userAssetGroupMap = userIdToAssetGroupsMap.get((long) userId);
    if (userAssetGroupMap != null && userAssetGroupMap.size() > 0) {
      return userAssetGroupMap.values();
    }
    return new ArrayList<>();
  }

  public static final Collection<AssetGroup> getBySecurityId(final int securityId) {
    final ConcurrentHashMap<Long, AssetGroup> securityIdAssetGroupMap = securityIdToAssetGroupsMap.get(securityId);
    if (securityIdAssetGroupMap != null && securityIdAssetGroupMap.size() > 0) {
      return securityIdAssetGroupMap.values();
    }
    return new ArrayList<>();
  }

  public static final Collection<AssetGroup> getByUserId(final int userId, final int securityId) {
    final ConcurrentHashMap<Long, AssetGroup> userAssetGroupMap = userIdToAssetGroupsMap.get((long) userId);
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

  public static final Collection<AssetGroup> getByIds(final long[] assetGroupIds) {
    if (assetGroupIds != null && assetGroupIds.length > 0) {
      final FastArrayList<AssetGroup> assetGroups = new FastArrayList<>(assetGroupIds.length);
      for (final long assetGroupId : assetGroupIds) {
        assetGroups.add(idToAssetGroupMap.get(assetGroupId));
      }
      return assetGroups;
    } else {
      return null;
    }
  }

  // called for snapshots
  public static final void restateAllAssetGroups(final long snapId) {
    LOGGER.info("Restate Asset Groups: " + idToAssetGroupMap.size());
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

  /*
   * public static final int loadFromDB() { int count = 0; final long t0 = System.currentTimeMillis(); lastUpdatedTime = t0; try (final
   * Connection conn = DBManager.getConnection(); final PreparedStatement userPS = conn.prepareStatement(SELECT); final ResultSet rs =
   * userPS.executeQuery();) { while (rs.next()) { final AssetGroup assetGroup = new AssetGroup(); // pk not used
   * assetGroup.setId(rs.getLong(2)); assetGroup.setSecurityId(rs.getInt(4));
   * 
   * onLoad(assetGroup); count++; } rs.close(); } catch (final Exception e) { LOGGER.error("error", e); } if (LOGGER.isInfoEnabled())
   * LOGGER.info(LOG_FMT_1, "AssetGroupCache.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0); return count; }
   * 
   * public static final AssetGroup loadFromDBWithId(final long id) { return loadFromDBWithQuery(SELECT_BY_ID, id); }
   * 
   * public static final void loadFromDBWithUpdatedTime() { final long t0 = System.currentTimeMillis(); loadFromDBWithQuery(SELECT_BY_ID,
   * lastUpdatedTime - TIME_THRESHOLD); lastUpdatedTime = t0; }
   * 
   * private static final AssetGroup loadFromDBWithQuery(final String query, final long id) { final long t0 = System.currentTimeMillis();
   * AssetGroup assetGroup = null; try (final Connection conn = DBManager.getConnection(); final PreparedStatement userPS =
   * conn.prepareStatement(query);) { userPS.setLong(1, id); final ResultSet rs = userPS.executeQuery(); while (rs.next()) { assetGroup =
   * new AssetGroup(); // pk not used assetGroup.setId(rs.getLong(2)); assetGroup.setSecurityId(rs.getInt(4));
   * 
   * onLoad(assetGroup); } rs.close(); } catch (final Exception e) { LOGGER.error("error", e); } if (LOGGER.isInfoEnabled())
   * LOGGER.info(LOG_FMT_1, "AssetGroupCache.loadFromDB id=", (long) id, ", time=", System.currentTimeMillis() - t0); return assetGroup; }
   * 
   * public static final void addToDB(final AssetGroup assetGroup) { final String timestamp = StringUtil.getCurrentDateYYYMMDDHHMMSSsss();
   * try (final Connection conn = DBManager.getConnection(); final PreparedStatement ps = conn.prepareStatement(INSERT); final
   * PreparedStatement selectAssetGroupPS = conn.prepareStatement(SELECT_ASSET_ID);) { ps.setInt(1, assetGroup.getTokenId()); ps.setInt(2,
   * assetGroup.getSecurityId()); ps.executeUpdate();
   * 
   * selectAssetGroupPS.setInt(1, assetGroup.getSecurityId()); final ResultSet rs = selectAssetGroupPS.executeQuery(); if (rs.next()) { int
   * id = rs.getInt(1); assetGroup.setId(id); } rs.close(); } catch (final Exception e) { LOGGER.error(ERROR_LOG, e); } }
   * 
   * public static final void updateDB(final AssetGroup assetGroup) { // final String timestamp =
   * StringUtil.getCurrentDateYYYMMDDHHMMSSsss(); try (final Connection conn = DBManager.getConnection(); final PreparedStatement ps =
   * conn.prepareStatement(UPDATE);) {
   * 
   * ps.setInt(3, assetGroup.getOwnerUserId());
   * 
   * 
   * ps.setLong(62, assetGroup.getId()); ps.executeUpdate(); } catch (final Exception e) { LOGGER.error(ERROR_LOG, e); } }
   */
}
