package com.solfini.matchengine.message.outbound;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.pool.CancelRejectObjectPool;
import com.solfini.sbe.encoder.CxlRejReason;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;

/**
 *
 * @author Chris Mack
 *
 */
public class CancelRejectMessage extends Message {
  private String clOrdId;
  private String symbol;
  private Side side;
  private OrdType ordType;
  private int account;
  private int submitterId;
  private long orderId;
  private long secondaryOrderId;
  private long execId;
  private long secondaryExecId;
  private long cancelId;
  private long cancelReplaceId;

  private OrdStatus ordStatus;
  private CxlRejReason cxlRejReason;

  public static CancelRejectMessage createCancelReject(final CancelOrder cancelOrder, final InstrumentPair instrument,
      final CxlRejReason cxlRejReason) {
    CancelRejectMessage cancelRejectMessage = CancelRejectObjectPool.get();

    cancelRejectMessage.clOrdId = cancelOrder.getClOrdId();
    cancelRejectMessage.symbol = instrument.getSymbol();
    cancelRejectMessage.side = cancelOrder.getSide();
    cancelRejectMessage.ordType = cancelOrder.getOrdType();
    cancelRejectMessage.account = cancelOrder.getAccount();
    cancelRejectMessage.orderId = cancelOrder.getOrigOrderId();
    cancelRejectMessage.secondaryOrderId = cancelOrder.getSecondaryOrderId();
    cancelRejectMessage.ordStatus = OrdStatus.NEW;
    cancelRejectMessage.cxlRejReason = cxlRejReason;

    cancelRejectMessage.setSenderCompId(cancelOrder.getSenderCompId());
    cancelRejectMessage.sourceSeqNum = cancelOrder.getSourceSeqNum();
    cancelRejectMessage.cancelId = cancelOrder.getCancelId();
    cancelRejectMessage.submitterId = cancelOrder.getSubmitterId();

    return cancelRejectMessage;
  }

  public static CancelRejectMessage createCancelReject(final CancelReplaceOrder cancelOrder, final InstrumentPair instrument,
      final CxlRejReason cxlRejReason) {
    CancelRejectMessage cancelRejectMessage = CancelRejectObjectPool.get();

    cancelRejectMessage.clOrdId = cancelOrder.getClOrdId();
    cancelRejectMessage.symbol = instrument.getSymbol();
    cancelRejectMessage.side = cancelOrder.getSide();
    cancelRejectMessage.ordType = cancelOrder.getOrdType();
    cancelRejectMessage.account = cancelOrder.getAccount();
    cancelRejectMessage.orderId = cancelOrder.getOrigOrderId();
    cancelRejectMessage.secondaryOrderId = cancelOrder.getSecondaryOrderId();
    cancelRejectMessage.ordStatus = OrdStatus.NEW;
    cancelRejectMessage.cxlRejReason = cxlRejReason;

    cancelRejectMessage.setSenderCompId(cancelOrder.getSenderCompId());
    cancelRejectMessage.sourceSeqNum = cancelOrder.getSourceSeqNum();
    cancelRejectMessage.cancelId = cancelOrder.getCancelId();
    cancelRejectMessage.cancelReplaceId = cancelOrder.getNewOrderId();
    cancelRejectMessage.submitterId = cancelOrder.getSubmitterId();

    return cancelRejectMessage;
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.CANCEL_REJECT;
  }

  public final String getClOrdId() {
    return clOrdId;
  }

  public final void setClOrdId(final String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public final String getSymbol() {
    return symbol;
  }

  public final void setSymbol(final String symbol) {
    this.symbol = symbol;
  }

  public final Side getSide() {
    return side;
  }

  public final void setSide(final Side side) {
    this.side = side;
  }

  public final OrdType getOrdType() {
    return ordType;
  }

  public final void setOrdType(final OrdType ordType) {
    this.ordType = ordType;
  }

  public final int getAccount() {
    return account;
  }

  public final void setAccount(final int account) {
    this.account = account;
  }

  public final long getOrderId() {
    return orderId;
  }

  public final void setOrderId(final long orderId) {
    this.orderId = orderId;
  }

  public final long getExecId() {
    return execId;
  }

  public final void setExecId(final long execId) {
    this.execId = execId;
  }

  public final long getSecondaryExecId() {
    return secondaryExecId;
  }

  public final void setSecondaryExecId(final long secondaryExecId) {
    this.secondaryExecId = secondaryExecId;
  }

  public final OrdStatus getOrdStatus() {
    return ordStatus;
  }

  public final void setOrdStatus(final OrdStatus ordStatus) {
    this.ordStatus = ordStatus;
  }

  public final CxlRejReason getCxlRejReason() {
    return cxlRejReason;
  }

  public final void setCxlRejReason(final CxlRejReason cxlRejReason) {
    this.cxlRejReason = cxlRejReason;
  }

  public final long getSecondaryOrderId() {
    return secondaryOrderId;
  }

  public final void setSecondaryOrderId(final long secondaryOrderId) {
    this.secondaryOrderId = secondaryOrderId;
  }

  public final void setCancelId(final long id) {
    this.cancelId = id;
  }

  public final long getCancelId() {
    return this.cancelId;
  }

  public final void setCancelReplaceId(final long id) {
    this.cancelReplaceId = id;
  }

  public final long getCancelReplaceId() {
    return this.cancelReplaceId;
  }

  public final int getSubmitterId() {
    return submitterId;
  }

  public final void setSubmitterId(final int submitterId) {
    this.submitterId = submitterId;
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
    s.append(CANCELREJECTMESSAGE_CLORDID_EQ).append(clOrdId == null ? "" : String.valueOf(clOrdId)).append(SYMBOL_EQ).append(symbol)
        .append(SIDE_EQ).append(side).append(ORDTYPE_EQ).append(ordType).append(ACCOUNT_EQ).append(account).append(ORDERID_EQ)
        .append(orderId).append(SECONDARYORDERID_EQ).append(secondaryOrderId).append(EXECID_EQ).append(execId).append(ORDSTATUS_EQ)
        .append(ordStatus).append(CXLREJREASON_EQ).append(cxlRejReason).append(SENDERCOMPID_EQ).append(senderCompId).append(KAFKA_OFFSET_EQ)
        .append(kafkaRecordOffset).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"CancelRejectMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"clOrdId\":").append("\"").append(clOrdId == null ? "" : String.valueOf(clOrdId)).append("\"").append(",\"symbol\":")
        .append("\"").append(symbol).append("\"").append(",\"side\":").append("\"").append(side).append("\"").append(",\"ordType\":")
        .append("\"").append(ordType).append("\"").append(",\"account\":").append("\"").append(account).append("\"").append(",\"orderId\":")
        .append(orderId).append(",\"secondaryOrderId\":").append(secondaryOrderId).append(",\"execId\":").append(execId)
        .append(",\"secondaryExecId\":").append(secondaryExecId);

    sb.append(",\"ordStatus\":").append("\"").append(ordStatus).append("\"").append(",\"cxlRejReason\":").append("\"").append(cxlRejReason)
        .append("\"");
    sb.append("}");
    return sb.toString();
  }

}
