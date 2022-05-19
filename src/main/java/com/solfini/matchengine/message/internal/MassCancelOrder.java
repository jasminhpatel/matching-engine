package com.solfini.matchengine.message.internal;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.preordercheck.CashPreOrderCheck;
import com.solfini.sbe.encoder.MassCancelOrderDecoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.user.UserCache;

/**
 *
 * @author Chris Mack
 *
 */
public class MassCancelOrder extends Message {

  private int securityId;
  private long origOrderId;
  private long cancelId;
  private long cancelPriority;
  private long secondaryOrderId; // orderId grouped by instrumentPair

  private String clOrdId;
  private int submitterId;
  private int account; // userId
  private OrdType ordType;
  private int type;

  public MassCancelOrder() {}

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public final MessageType getMessageType() {
    return MessageType.MASS_CANCEL_ORDER;
  }

  public MassCancelOrder(final MassCancelOrderDecoder massCancelOrderDecoder, final long cancelId, final long cancelPriority) {
    this.cancelId = cancelId;
    this.cancelPriority = cancelPriority;
    clOrdId = massCancelOrderDecoder.clOrdID();
    securityId = massCancelOrderDecoder.securityId();
    account = massCancelOrderDecoder.userId();
    user = (account == 0) ? null : UserCache.get(account);
    submitterId = massCancelOrderDecoder.submitterId();
    type = massCancelOrderDecoder.type();
  }

  public MassCancelOrder(final MassCancelOrderDecoder massCancelOrderDecoder) {
    cancelId = massCancelOrderDecoder.cancelId();
    cancelPriority = massCancelOrderDecoder.cancelPriority();
    clOrdId = massCancelOrderDecoder.clOrdID();
    securityId = massCancelOrderDecoder.securityId();
    account = massCancelOrderDecoder.userId();
    user = (account == 0) ? null : UserCache.get(account);
    submitterId = massCancelOrderDecoder.submitterId();
    type = massCancelOrderDecoder.type();
  }

  public MassCancelOrder(final Order order, final long cancelId, final long cancelPriority) {
    this.cancelId = cancelId;
    this.cancelPriority = cancelPriority;
    clOrdId = order.getClOrdId();
    securityId = order.getSecurityId();
    origOrderId = order.getOrderId();
    ordType = order.getOrdType();
    senderCompId = order.getSenderCompId();
    account = order.getAccount();
    user = (account == 0) ? null : UserCache.get(account);
    submitterId = order.getSubmitterId();
    type = CANCEL_ON_REQUEST;
  }

  public final int getSecurityId() {
    return securityId;
  }

  public final void setSecurityId(final int securityId) {
    this.securityId = securityId;
  }

  public final long getOrigOrderId() {
    return origOrderId;
  }

  public final void setOrigOrderId(final long origOrderId) {
    this.origOrderId = origOrderId;
  }

  public final long getCancelId() {
    return cancelId;
  }

  public final void setCancelId(final long cancelId) {
    this.cancelId = cancelId;
  }

  public final long getCancelPriority() {
    return cancelPriority;
  }

  public final void setCancelPriority(final long cancelPriority) {
    this.cancelPriority = cancelPriority;
  }

  public final String getClOrdId() {
    return clOrdId;
  }

  public final void setClOrdId(String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public final int getSubmitterId() {
    return submitterId;
  }

  public final void setSubmitterId(final int submitterId) {
    this.submitterId = submitterId;
  }

  public final int getAccount() {
    return account;
  }

  public final void setAccount(final int account) {
    this.account = account;
  }

  public final OrdType getOrdType() {
    return ordType;
  }

  public final void setOrdType(final OrdType ordType) {
    this.ordType = ordType;
  }

  public final int getType() {
    return type;
  }

  public final void setType(final int type) {
    this.type = type;
  }

  public final long getSecondaryOrderId() {
    return secondaryOrderId;
  }

  public final void setSecondaryOrderId(final long secondaryOrderId) {
    this.secondaryOrderId = secondaryOrderId;
  }

  @Override
  public void onMatcher() {
    GlobalOrderBook.setOrderIdIfGreater(11, cancelId);

    if (Context.getMarketStatus() != MarketStatus.DR_MODE) {
      if (securityId > 0) {
        final InstrumentPair instrumentPair = InstrumentCache.getPair(securityId);
        if (instrumentPair != null) {
          final OrderBook orderbook = instrumentPair.getOrderBook();
          orderbook.massCancelOrder(this);
        }
      } else {
        for (int i = 0; i < InstrumentCache.getInstrumentCapacity(); i++) {
          final InstrumentPair instrumentPair = InstrumentCache.getPair(i);
          if (instrumentPair != null) {
            final OrderBook orderbook = instrumentPair.getOrderBook();
            orderbook.massCancelOrder(this);
          }
        }
      }

      // TODO: expiremental check on quantity available when there are no open orders
      if (account > 0) {
        CashPreOrderCheck.recalcAvailableQuantity(UserCache.get(account));
      }
      if (submitterId > 0) {
        CashPreOrderCheck.recalcAvailableQuantity(UserCache.get(submitterId));
      }
    }
  }

  @Override
  public void onPublish() {
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
    s.append(MASSCANCELORDER_SECURITYID_EQ).append(securityId).append(ORIGORDERID_EQ).append(origOrderId).append(CANCELID_EQ)
        .append(cancelId).append(SECONDARYORDERID_EQ).append(secondaryOrderId).append(CANCELPRIORITY_EQ).append(cancelPriority)
        .append(CLORID_EQ).append(clOrdId == null ? "" : String.valueOf(clOrdId)).append(SENDERCOMPIDCHARARR_EQ).append(senderCompId)
        .append(SENDERCOMPIDASSTRING_EQ).append(senderCompId).append(ACCOUNT_EQ).append(account).append(USERID_EQ).append(account)
        .append(ORDTYPE_EQ).append(ordType).append(TYPE_EQ).append(type).append(SOURCESEQNUM_EQ).append(sourceSeqNum)
        .append(SOURCESENDTIME_EQ).append(sourceSendTime).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"MassCancelOrder\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"securityId\":").append(securityId).append(",\"origOrderId\":").append(origOrderId).append(",\"secondaryOrderId\":")
        .append(secondaryOrderId).append(",\"cancelId\":").append(cancelId).append(",\"cancelPriority\":").append(cancelPriority)
        .append(",\"clOrdId\":").append("\"").append(clOrdId == null ? "" : String.valueOf(clOrdId)).append("\"")
        .append(",\"senderCompIdCharArr\":").append("\"").append(senderCompId).append("\"").append(",\"senderCompIdAsString\":")
        .append("\"").append(senderCompId).append("\"").append(",\"account\":").append("\"").append(account).append("\"")
        .append(",\"userId\":").append(account).append(",\"ordType\":").append("\"").append(ordType).append("\"").append(",\"type\":")
        .append(type).append(",\"quantityLong\":");
    sb.append("}");
    return sb.toString();
  }

}
