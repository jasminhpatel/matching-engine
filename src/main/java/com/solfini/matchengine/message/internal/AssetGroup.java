package com.solfini.matchengine.message.internal;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.Position;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.AssetGroupCache;
import com.solfini.sbe.encoder.AssetGroupDecoder;
import com.solfini.sbe.encoder.TokenType;
import com.solfini.sbe.encoder.UpdateType;
import com.solfini.user.UserCache;

/**
 *
 * @author Chris Mack
 *
 */
public class AssetGroup extends Message {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(AssetGroup.class);
  private UpdateType updateType;
  private long id;//groupId
  private long ownerUserId;
  private long quantity;
  private String name;
  private long securityId;
  private long assetId;
  private TokenType tokenType;

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
    this.quantity = decoder.quantity();
    this.name = decoder.name();
    this.securityId = decoder.securityId();
    this.assetId = decoder.assetId();
    this.tokenType = decoder.tokenType();

    // parse assetId, tokenId, groupAssetId for position
    AssetGroupDecoder.PositionsAssetIdGroupDecoder positionsAssetIdGroupDecoder = decoder.positionsAssetIdGroup();
    final int positionsAssetIdGroupCount = positionsAssetIdGroupDecoder.count();
    for (int k = 0; k < positionsAssetIdGroupCount; k++) {
      positionsAssetIdGroupDecoder = positionsAssetIdGroupDecoder.next();
      final long assetId = positionsAssetIdGroupDecoder.assetId();
      final int tokenId = positionsAssetIdGroupDecoder.tokenId();

      addAssetIdToList(assetId, tokenId);
      this.assetId = 0;//group level values are zero if token list exists
      this.quantity = 0;
    }
  }

  public void copySet(final AssetGroup source) {
    this.updateType = source.updateType;
    this.id = source.id;
    this.ownerUserId = source.ownerUserId;
    this.quantity = source.quantity;
    this.name = source.name;
    this.securityId = source.securityId;
    this.assetId = source.assetId;
    this.tokenType = source.tokenType;
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

  public long getQuantity() {
    return quantity;
  }

  public void setQuantity(final long quantity) {
    this.quantity = quantity;
  }

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

  public long getAssetId() {
    return assetId;
  }

  public void setAssetId(final long assetId) {
    this.assetId = assetId;
  }

  public TokenType getTokenType() {
    return tokenType;
  }

  public void setTokenType(final TokenType tokenType) {
    this.tokenType = tokenType;
  }

  public final void addAssetIdToList(final long assetId, final int tokenId) {
    if (assetId == 0)
      return;

    final long[] value = {assetId, tokenId};
    assetIdGroupTreeSet.add(value);
  }

  public final void removeAll(final Collection<long[]> source) {
    assetIdGroupTreeSet.removeAll(source);
  }

  public final void addAll(final Collection<long[]> source) {
    assetIdGroupTreeSet.addAll(source);
  }

  @Override
  public void onMatcher() {
    //validate assets
    final int userId = (int) this.ownerUserId;
    final int securityId = (int) this.securityId;
    final Position position = UserCache.get(userId).getPosition(securityId);

    if (TokenType.ERC20_GROUP == this.tokenType) {
      this.setId(0);
      this.setError("Can npt create groups for ERC20 type tokens. Security id: " + securityId);
      LOGGER.info(Constants.LOG_FMT_2, "Can npt create groups for ERC20 type tokens. security: ", securityId, " userId: " , userId, "");
      return;
    }

    if (position == null || position.getAssetIdtreeSet() == null) {
      // todo reject
      this.setId(0);
      this.setError("User does not own any assets. Security id: " + securityId);
      LOGGER.info(Constants.LOG_FMT_2, "User does not own any assets. security: ", securityId, " userId: " , userId, "");
      return;
    }

    if (TokenType.ERC20_GROUP != this.tokenType) {
      for (final long[] assetToken : this.assetIdGroupTreeSet) {//validate ownership
        boolean ownsAsset = false;
        for (final long[] assetTokenInPositions : position.getAssetIdtreeSet()) {
          if (assetToken[0] == assetTokenInPositions[0] && assetToken[1] == assetTokenInPositions[1]) {
            ownsAsset = true;
            break;
          }
        }
        if (!ownsAsset) {
          // todo reject
          LOGGER.info(Constants.LOG_FMT_2, "User does not own the asset. security: ", securityId, " userId: ", userId, " assetId: ",
              assetToken[0], " tokenId: ", assetToken[1]);
          return;
        }
      }
    }

    AssetGroupCache.onModel(this);

    if (TokenType.ERC20_GROUP != this.tokenType) {
      //update positions
      for (final long[] assetToken : this.assetIdGroupTreeSet) {
        position.removeAssetId(assetToken[0], (int) assetToken[1], 0);// remove asset/token position
      }
    }
    position.addAssetId(0,0, this.id);// add group position. One record for the entire group
    //no change in position quantity
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
    s.append("AssetGroup [id=").append(id).append(", ownerUserId=").append(ownerUserId).append(", quantity=").append(quantity)
        .append(", securityId=").append(securityId).append(", name=").append(name).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"AssetGroup\"").append(",\"sequenceNumber\":").append(sequenceNumber) // .append(",\"persistTime\":").append(persistTime)
        .append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime).append(",\"snapId\":")
        .append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset)
        .append(",\"id\":").append(id).append(",\"ownerUserId\":").append(ownerUserId).append(",\"quantity\":").append(quantity)
        .append(",\"name\":\"").append(name).append("\"").append(",\"securityId\":").append(securityId).append(",\"assetId\":").append(assetId)
        .append(",\"tokenType\":").append(tokenType.value()).append(",\"groups\":[");

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
