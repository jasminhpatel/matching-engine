package com.solfini.matchengine.message.admin;

import com.solfini.common.AdminMessage;
import com.solfini.common.Context;
import com.solfini.common.MessageType;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.FeeAdminMessageDecoder;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.schema.PayloadType;

/**
 *
 * @author Chris Mack
 *
 */
public class FeeAdminMessage extends AdminMessage {
  private UpdateType updateType;
  private int assetId;
  private int feeInstrumentId;
  private int fee;
  private FeeType feeType;
  private MakerTaker makerTaker;
  private int tier;

  public FeeAdminMessage() {}

  public FeeAdminMessage(final FeeAdminMessageDecoder FEE_DECODER, final long connectionId) {
    this.connectionId = connectionId;
    this.updateType = FEE_DECODER.updateType();
    this.assetId = FEE_DECODER.assetId();
    this.feeInstrumentId = FEE_DECODER.feeAssetId();
    this.fee = FEE_DECODER.fee();
    this.feeType = FEE_DECODER.feeType();
    this.makerTaker = FEE_DECODER.makerTaker();
    this.tier = FEE_DECODER.tier();
    this.triggerTimeMillis = FEE_DECODER.triggerTimeMillis();
    this.routeToDestination = FEE_DECODER.routeToDestination();
    this.senderInstanceId = FEE_DECODER.senderInstanceId();
  }

  public FeeAdminMessage(final Fee fee) {
    this.updateType = UpdateType.PUT;
    this.feeInstrumentId = fee.getFeeInstrumentId();
    this.assetId = fee.getInstrumentPairId();
    this.tier = fee.getTier();
    this.fee = fee.getFee();
    this.feeType = fee.getFeeType();
    this.makerTaker = fee.getMakerTaker();
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  public final MessageType getMessageType() {
    return MessageType.FEE_ADMIN;
  }

  public final UpdateType getUpdateType() {
    return updateType;
  }

  public final void setUpdateType(final UpdateType updateType) {
    this.updateType = updateType;
  }

  public final int getAssetId() {
    return assetId;
  }

  public final void setAssetId(final int assetId) {
    this.assetId = assetId;
  }

  public final int getFeeInstrumentId() {
    return feeInstrumentId;
  }

  public final void setFeeInstrumentId(final int feeInstrumentId) {
    this.feeInstrumentId = feeInstrumentId;
  }

  public final int getFee() {
    return fee;
  }

  public final void setFee(final int fee) {
    this.fee = fee;
  }

  public final FeeType getFeeType() {
    return feeType;
  }

  public final void setFeeType(final FeeType feeType) {
    this.feeType = feeType;
  }

  public final MakerTaker getMakerTaker() {
    return makerTaker;
  }

  public final void setMakerTaker(final MakerTaker makerTaker) {
    this.makerTaker = makerTaker;
  }

  public final int getTier() {
    return tier;
  }

  public final void setTier(final int tier) {
    this.tier = tier;
  }

  @Override
  public final void onMatcher() {
    InstrumentCache.setFee(this);
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
    s.append(FEEADMINMESSAGE_SENDERCOMPID_EQ).append(senderCompId).append(CONNECTIONID_EQ).append(connectionId).append(TRIGGERTIMEMILLIS_EQ)
        .append(triggerTimeMillis).append(UPDATETYPE_EQ).append(updateType).append(ASSETID_EQ).append(assetId).append(FEEINSTRUMENTID_EQ)
        .append(feeInstrumentId).append(FEE_EQ).append(fee).append(FEETYPE_EQ).append(feeType).append(MAKERTAKER_EQ).append(makerTaker)
        .append(TIER_EQ).append(tier).append(ROUTETODESTINATION_EQ).append(routeToDestination).append(SOURCESEQNUM_EQ).append(sourceSeqNum)
        .append(SOURCESENDTIME_EQ).append(sourceSendTime).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"FeeAdminMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis).append(",\"externalId\":").append(externalId)
        .append(",\"updateType\":").append(updateType.value()).append(",\"assetId\":").append(assetId).append(",\"feeInstrumentId\":")
        .append(feeInstrumentId).append(",\"fee\":").append(fee).append(",\"feeType\":").append(feeType.value()).append(",\"makerTaker\":")
        .append(makerTaker.value()).append(",\"tier\":").append(tier).append(",\"routeToDestination\":").append("\"")
        .append(routeToDestination).append("\"");
    sb.append("}");
    return sb.toString();
  }
}
