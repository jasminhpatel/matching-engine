package com.solfini.matchengine.message.outbound;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import com.solfini.pool.BusinessRejectObjectPool;
import com.solfini.sbe.encoder.BusinessRejectReason;
import com.solfini.sbe.encoder.MsgType;

/**
 *
 * @author Chris Mack
 *
 */
public class BusinessRejectMessage extends Message {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(Order.class);
  private BusinessRejectReason businessRejectReason;
  private String text;
  private MsgType refMsgType;
  private String businessRejectRefID;
  private long orderId;
  private long secondaryOrderId;
  private int pairId;
  private long cancelId; // Set if there is a cancel id associated with the order rejection.
  private long cancelReplaceId; // Set if there is a cancel replace id associated with the order rejection.
  private long clOrdId; // Set if there is a clOrdId associated with the order rejection
  private int submitterId;
  private String clOrdIdStr; // String 64 clOrdId from external users.

  public static BusinessRejectMessage createBusinessReject(final String senderCompId, final MsgType refMsgType,
      final String businessRejectRefID, final BusinessRejectReason businessRejectReason, final String text, final long orderId,
      final long sourceSeqNum, final long secondaryOrderId, final int pairId, final int submitterId) {
    final BusinessRejectMessage businessRejectMessage = BusinessRejectObjectPool.get();

    businessRejectMessage.senderCompId = senderCompId;
    businessRejectMessage.refMsgType = refMsgType;
    businessRejectMessage.businessRejectRefID = businessRejectRefID;
    businessRejectMessage.businessRejectReason = businessRejectReason;
    businessRejectMessage.text = text;
    businessRejectMessage.orderId = orderId;
    businessRejectMessage.sourceSeqNum = sourceSeqNum;
    businessRejectMessage.secondaryOrderId = secondaryOrderId;
    businessRejectMessage.pairId = pairId;
    businessRejectMessage.submitterId = submitterId;
    LOGGER.info("Order rejected. orderId: " + orderId + " businessRejectRefID: " + businessRejectRefID + " businessRejectReason: " + businessRejectReason);
    return businessRejectMessage;
  }

  public static BusinessRejectMessage createBusinessRejectWithCancelId(final String senderCompId, final MsgType refMsgType,
      final String businessRejectRefID, final BusinessRejectReason businessRejectReason, final String text, final long orderId,
      final long sourceSeqNum, final long secondaryOrderId, final int pairId, final long cancelId, final int submitterId) {
    final BusinessRejectMessage message = createBusinessReject(senderCompId, refMsgType, businessRejectRefID, businessRejectReason, text,
        orderId, sourceSeqNum, secondaryOrderId, pairId, submitterId);

    message.cancelId = cancelId;
    return message;
  }

  public static BusinessRejectMessage createBusinessRejectWithCancelReplacelId(final String senderCompId, final MsgType refMsgType,
      final String businessRejectRefID, final BusinessRejectReason businessRejectReason, final String text, final long orderId,
      final long sourceSeqNum, final long secondaryOrderId, final int pairId, final long cancelId, final long cancelReplaceId, final int submitterId) {
    final BusinessRejectMessage message = createBusinessReject(senderCompId, refMsgType, businessRejectRefID, businessRejectReason, text,
        orderId, sourceSeqNum, secondaryOrderId, pairId, submitterId);

    message.cancelId = cancelId;
    message.cancelReplaceId = cancelReplaceId;
    return message;
  }

  public static BusinessRejectMessage createBusinessRejectWithClOrdId(final String senderCompId, final MsgType refMsgType,
      final String businessRejectRefID, final BusinessRejectReason businessRejectReason, final String text, final long orderId,
      final long sourceSeqNum, final long secondaryOrderId, final int pairId, final long clOrdId, final int submitterId) {
    final BusinessRejectMessage message = createBusinessReject(senderCompId, refMsgType, businessRejectRefID, businessRejectReason, text,
        orderId, sourceSeqNum, secondaryOrderId, pairId, submitterId);

    message.clOrdId = clOrdId;
    return message;
  }

  public static BusinessRejectMessage createBusinessRejectWithClOrdIdStr(final String senderCompId, final MsgType refMsgType,
      final String businessRejectRefID, final BusinessRejectReason businessRejectReason, final String text, final long orderId,
      final long sourceSeqNum, final long secondaryOrderId, final int pairId, final String clOrdId, final int submitterId) {
    final BusinessRejectMessage message = createBusinessReject(senderCompId, refMsgType, businessRejectRefID, businessRejectReason, text,
        orderId, sourceSeqNum, secondaryOrderId, pairId, submitterId);

    message.clOrdIdStr = clOrdId;
    return message;
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.BUSINESS_REJECT;
  }

  public final BusinessRejectReason getBusinessRejectReason() {
    return businessRejectReason;
  }

  public final void setBusinessRejectReason(BusinessRejectReason businessRejectReason) {
    this.businessRejectReason = businessRejectReason;
  }

  public final String getText() {
    return text;
  }

  public final void setText(final String text) {
    this.text = text;
  }

  public final MsgType getRefMsgType() {
    return refMsgType;
  }

  public final void setRefMsgType(final MsgType refMsgType) {
    this.refMsgType = refMsgType;
  }

  public final String getBusinessRejectRefID() {
    return businessRejectRefID;
  }

  public final void setBusinessRejectRefID(final String businessRejectRefID) {
    this.businessRejectRefID = businessRejectRefID;
  }

  public final long getOrderId() {
    return orderId;
  }

  public final void setOrderId(final long orderId) {
    this.orderId = orderId;
  }

  public final long getSecondaryOrderId() {
    return secondaryOrderId;
  }

  public final void setSecondaryOrderId(final long secondaryOrderId) {
    this.secondaryOrderId = secondaryOrderId;
  }

  public final int getPairId() {
    return pairId;
  }

  public final void setPairId(final int pairId) {
    this.pairId = pairId;
  }

  public final void setCancelId(final long cancelId) {
    this.cancelId = cancelId;
  }

  public final long getCancelId() {
    return this.cancelId;
  }

  public final void setCancelReplaceId(final long cancelReplaceId) {
    this.cancelReplaceId = cancelReplaceId;
  }

  public final long getCancelReplaceId() {
    return this.cancelReplaceId;
  }

  public final void setClOrdId(final long clOrdId) {
    this.clOrdId = clOrdId;
  }

  public final long getClOrdId() {
    return this.clOrdId;
  }

  public final int getSubmitterId() {
    return submitterId;
  }

  public final void setSubmitterId(final int submitterId) {
    this.submitterId = submitterId;
  }

  public final String getClOrdIdStr() {
    return clOrdIdStr;
  }

  public final void setClOrdIdStr(final String clOrdIdStr) {
    this.clOrdIdStr = clOrdIdStr;
  }

  @Override
  public void onMatcher() {
    GlobalOrderBook.setOrderIdIfGreater(12, getOrderId());
    GlobalOrderBook.setOrderIdIfGreater(13, getCancelId());
    GlobalOrderBook.setOrderIdIfGreater(14, getCancelReplaceId());

    InstrumentPair instrumentPair = InstrumentCache.getPair(getPairId());
    if (null != instrumentPair) {
      instrumentPair.getOrderBook().setSecondaryOrderIdIfGreater(getSecondaryOrderId());
    }

    // primary publishes reject messages so that secondaries can update order ids.
    Context.getMatcherToPublisherQueue().add(this);
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
    s.append(BUSINESSREJECTMESSAGE_BUSINESSREJECTREASON_EQ).append(businessRejectReason).append(TEXT_EQ).append(text).append(REFMSGTYPE_EQ)
        .append(refMsgType).append(BUSINESSREJECTREFID_EQ).append(businessRejectRefID).append(ORDERID_EQ).append(orderId)
        .append(SECONDARYORDERID_EQ).append(secondaryOrderId).append(PAIRID_EQ).append(pairId).append(KAFKA_OFFSET_EQ)
        .append(kafkaRecordOffset).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"BusinessRejectMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"businessRejectReason\":").append("\"").append(businessRejectReason).append("\"").append(",\"text\":").append("\"")
        .append(text).append("\"").append(",\"refMsgType\":").append("\"").append(refMsgType).append("\"")
        .append(",\"businessRejectRefID\":").append("\"").append(businessRejectRefID).append("\"")
        .append(",\"clOrdIdStr\":").append("\"").append(clOrdIdStr).append("\"").append(",\"orderId\":").append(orderId)
        .append(",\"secondaryOrderId\":").append(secondaryOrderId).append(",\"pairId\":").append(pairId).append(",\"submitterId\":").append(submitterId);
    sb.append("}");
    return sb.toString();
  }
}
