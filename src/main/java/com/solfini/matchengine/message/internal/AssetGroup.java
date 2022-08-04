package com.solfini.matchengine.message.internal;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.AssetGroupCache;
import com.solfini.sbe.encoder.AssetGroupDecoder;
import com.solfini.sbe.encoder.UpdateType;

/**
 *
 * @author Chris Mack
 *
 */
public class AssetGroup extends Message {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(AssetGroup.class);
  private UpdateType updateType;
  private long id;// assetId in DB
  private long ownerUserId;
  private long groupAssetId;
  private String name;
  private long securityId;

  private final ConcurrentSkipListSet<long[]> assetIdGroupTreeSet = new ConcurrentSkipListSet<>(assetComparatorLow); // low to high


  public AssetGroup() {
    // default constructor
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.ASSET_GROUP;
  }

  public void set(final AssetGroupDecoder decoder) {
    this.updateType = decoder.updateType();
    this.id = decoder.id();
    this.ownerUserId = decoder.ownerUserId();
    this.groupAssetId = decoder.groupAssetId();
    this.name = decoder.name();
    this.securityId = decoder.securityId();

    // parse assetId, tokenId, groupAssetId for position
    AssetGroupDecoder.PositionsAssetIdGroupDecoder positionsAssetIdGroupDecoder = decoder.positionsAssetIdGroup();
    final int positionsAssetIdGroupCount = positionsAssetIdGroupDecoder.count();
    for (int k = 0; k < positionsAssetIdGroupCount; k++) {
      // if (positionsAssetIdGroupDecoder.hasNext()) {
      positionsAssetIdGroupDecoder = positionsAssetIdGroupDecoder.next();
      final long assetId = positionsAssetIdGroupDecoder.assetId();
      final int tokenId = positionsAssetIdGroupDecoder.tokenId();
      // if (UpdateType.DELETE == updateType)
      // removeAssetId(assetId, tokenId);
      // else
      addAssetId(assetId, tokenId);
    }
  }

  public void copySet(final AssetGroup source) {
    this.updateType = source.updateType;
    this.id = source.id;
    this.ownerUserId = source.ownerUserId;
    this.groupAssetId = source.groupAssetId;
    this.name = source.name;
    this.securityId = source.securityId;
  }

  public final UpdateType getUpdateType() {
    return updateType;
  }

  public final void setUpdateType(final UpdateType updateType) {
    this.updateType = updateType;
  }

  public final long getId() {
    return id;
  }

  public final void setId(final long id) {
    this.id = id;
  }

  public final long getOwnerUserId() {
    return ownerUserId;
  }

  public final void setOwnerUserId(final long ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  public final long getGroupAssetId() {
    return groupAssetId;
  }

  public final void setGroupAssetId(final long groupAssetId) {
    this.groupAssetId = groupAssetId;
  }

  // public final void setAssetIdtreeSet(final Set<long[]> assetIdtreeSet) {
  // this.assetIdtreeSet = assetIdtreeSet;
  // }

  public final ConcurrentSkipListSet<long[]> getAssetIdGroupTreeSet() {
    return assetIdGroupTreeSet;
  }

  public final String getName() {
    return name;
  }

  public final void setName(final String name) {
    this.name = name;
  }

  public final long getSecurityId() {
    return securityId;
  }

  public final void setSecurityId(final long securityId) {
    this.securityId = securityId;
  }

  public final void addAssetId(final long assetId, final int tokenId) {
    if (assetId == 0)
      return;

    // if (assetIdGroupTreeSet == null)
    // assetIdGroupTreeSet = new ConcurrentSkipListSet<>(assetComparatorLow);

    final long[] value = {assetId, tokenId};
    assetIdGroupTreeSet.add(value);
  }

  public final void removeAssetId(final long assetId, final int tokenId) {
    if (assetId == 0)
      return;

    // if (assetIdGroupTreeSet == null)
    // assetIdGroupTreeSet = new ConcurrentSkipListSet<>(assetComparatorLow);

    final long[] value = {assetId, tokenId};
    assetIdGroupTreeSet.remove(value);
  }

  public final void removeAll(final Collection<long[]> source) {
    assetIdGroupTreeSet.removeAll(source);
  }

  public final void addAll(final Collection<long[]> source) {
    assetIdGroupTreeSet.addAll(source);
  }

  @Override
  public void onMatcher() {
    AssetGroupCache.onModel(this);
  }

  @Override
  public final void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append("AssetGroup [id=").append(id).append(", ownerUserId=").append(ownerUserId).append(", groupAssetId=").append(groupAssetId)
        .append(", securityId=").append(securityId).append(", name=").append(name).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"AssetGroup\"").append(",\"sequenceNumber\":").append(sequenceNumber) // .append(",\"persistTime\":").append(persistTime)
        .append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime).append(",\"snapId\":")
        .append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"id\":").append(id).append(",\"ownerUserId\":").append(ownerUserId).append(",\"groupAssetId\":").append(groupAssetId)
        .append(",\"securityId\":").append(securityId).append(",\"name\":\"").append(name).append("\"").append(",\"groups\":[");

    int count = 0;
    for (final long[] arr : assetIdGroupTreeSet) {
      if (count > 0)
        sb.append(',');
      sb.append("[").append(arr[0]).append(',').append(arr[1]).append("]");
      count++;
    }


    sb.append("]}");
    return sb.toString();
  }

  // sort by smallest to largest
  private static final Comparator<long[]> assetComparatorLow = new Comparator<long[]>() {
    @Override
    public int compare(final long[] asset1, final long[] asset2) {
      try {
        if (asset1[0] > asset2[0])
          return 1;
        if (asset1[0] < asset2[0])
          return -1;

        if (asset1[1] > asset2[1])
          return 1;
        if (asset1[1] < asset2[1])
          return -1;

      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
      return 0;
    }
  };
}
