package com.solfini.matchengine.message.internal;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.sbe.encoder.CancelOrderDecoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.UserCache;

/**
 *
 * @author Chris Mack
 *
 */
public class CancelOrder extends Message {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(CancelOrder.class);


  protected int securityId;
  private long price;
  private long qty;
  private short qtyScale;
  private Side side;
  private long origOrderId;
  private long secondaryOrderId; // orderId grouped by instrumentPair

  private long cancelId;
  private long cancelPriority;
  private String clOrdId;
  private int submitterId;
  private int account; // userid
  private OrdType ordType;
  private int type;
  private int priceInt;
  private long quantityLong;
  private long quantityOrigLong;
  private long feeEstimatedQuantity;
  private long feeAccumulatedQuantity;
  private long availableEstimatedQuantity;
  private long availableAccumulatedQuantity;
  private short cancelType;

  public CancelOrder() {
    // default constructor
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.CANCEL_ORDER;
  }


  public void set(final Order order, final long cancelId, final long cancelPriority) {
    this.cancelId = cancelId;
    this.cancelPriority = cancelPriority;
    clOrdId = order.getClOrdId();
    securityId = order.getSecurityId();
    origOrderId = order.getOrderId();
    secondaryOrderId = order.getSecondaryOrderId();

    price = order.getPrice();
    priceInt = order.getPriceInt();
    qty = order.getQty();
    side = order.getSide();
    ordType = order.getOrdType();
    senderCompId = order.getSenderCompId();
    cancelType = CANCEL_ON_REQUEST;

    account = order.getAccount();
    user = order.getUser();
    submitterId = order.getSubmitterId();
    feeEstimatedQuantity = order.getFeeEstimatedQuantity();
    feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
    availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
    availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();
  }

  public void set(final CancelOrderDecoder cancelOrderDecoder, final long cancelId, final long cancelPriority) {
    this.cancelId = cancelId;
    this.cancelPriority = cancelPriority;
    this.clOrdId = cancelOrderDecoder.clOrdID();
    if (clOrdId != null)
      clOrdId = clOrdId.trim();

    securityId = cancelOrderDecoder.securityId();
    origOrderId = cancelOrderDecoder.originalOrderId();
    secondaryOrderId = cancelOrderDecoder.secondaryOrderId();
    price = cancelOrderDecoder.price();
    // TODO: do we need price scale?

    qty = cancelOrderDecoder.qty();
    qtyScale = cancelOrderDecoder.qtyScale();

    side = cancelOrderDecoder.side();
    ordType = OrdType.LIMIT;
    account = cancelOrderDecoder.userId();
    user = UserCache.get(account);
    submitterId = cancelOrderDecoder.submitterId();
    cancelType = CANCEL_ON_REQUEST;

    // if neither origOrderId or secondaryOrderId are provided, attempt to lookup using clOrdId
    if (origOrderId <= 0 && secondaryOrderId <= 0 && user != null && clOrdId != null) {
      final Order order = user.lookupOrderByClorid(clOrdId, securityId, side);

      if (order != null && order.getUser() != null && account == order.getUser().getId()) {
        origOrderId = order.getOrderId();
        secondaryOrderId = order.getSecondaryOrderId();
        feeEstimatedQuantity = order.getFeeEstimatedQuantity();
        feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
        availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
        availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();
      }
    } else {
      try {
        final Order order = user.lookupOrder(origOrderId, securityId, side);
        if (order != null) {
          feeEstimatedQuantity = order.getFeeEstimatedQuantity();
          feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
          availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
          availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public final int getSecurityId() {
    return securityId;
  }

  public final void setSecurityId(final int securityId) {
    this.securityId = securityId;
  }


  public final long getPrice() {
    return price;
  }

  public final void setPrice(final long price, final short priceScale) {
    this.price = price;
    // TODO: do we need priceScale?
  }


  public final long getQty() {
    return qty;
  }

  public final short getQtyScale() {
    return qtyScale;
  }

  public final void setQty(final long qty, final short qtyScale) {
    this.qty = qty;
    this.qtyScale = qtyScale;
  }

  public final Side getSide() {
    return side;
  }

  public final void setSide(final Side side) {
    this.side = side;
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

  public final void setClOrdId(final String clOrdId) {
    this.clOrdId = clOrdId;
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

  public final short getCancelType() {
    return cancelType;
  }

  public final void setCancelType(final short cancelType) {
    this.cancelType = cancelType;
  }

  public final int getPriceInt() {
    return priceInt;
  }

  public final void setPriceInt(final int priceInt) {
    this.priceInt = priceInt;
  }

  public final long getQuantityLong() {
    return quantityLong;
  }

  public final void setQuantityLong(final long quantityLong) {
    this.quantityLong = quantityLong;
  }

  public final long getQuantityOrigLong() {
    return quantityOrigLong;
  }

  public final void setQuantityOrigLong(final long quantityOrigLong) {
    this.quantityOrigLong = quantityOrigLong;
  }

  public final long getSecondaryOrderId() {
    return secondaryOrderId;
  }

  public final void setSecondaryOrderId(final long secondaryOrderId) {
    this.secondaryOrderId = secondaryOrderId;
  }

  public final int getSubmitterId() {
    return submitterId;
  }

  public final void setSubmitterId(final int submitterId) {
    this.submitterId = submitterId;
  }

  public final long getFeeEstimatedQuantity() {
    return feeEstimatedQuantity;
  }

  public final void setFeeEstimatedQuantity(final long feeEstimatedQuantity) {
    this.feeEstimatedQuantity = feeEstimatedQuantity;
  }

  public final long getFeeAccumulatedQuantity() {
    return feeAccumulatedQuantity;
  }

  public final void setFeeAccumulatedQuantity(final long feeAccumulatedQuantity) {
    this.feeAccumulatedQuantity = feeAccumulatedQuantity;
  }

  public final long getAvailableEstimatedQuantity() {
    return availableEstimatedQuantity;
  }

  public final void setAvailableEstimatedQuantity(final long availableEstimatedQuantity) {
    this.availableEstimatedQuantity = availableEstimatedQuantity;
  }

  public final long getAvailableAccumulatedQuantity() {
    return availableAccumulatedQuantity;
  }

  public final void setAvailableAccumulatedQuantity(long availableAccumulatedQuantity) {
    this.availableAccumulatedQuantity = availableAccumulatedQuantity;
  }

  @Override
  public void onMatcher() {
    if (account != Context.getMarketMakerUserid()) {
      LOGGER.info("CancelOrder received: " + this.toJSON());
    }
    GlobalOrderBook.setOrderIdIfGreater(8, cancelId);

    final InstrumentPair instrumentPair = InstrumentCache.getPair(securityId);
    if (null != instrumentPair) {
      OrderBook orderbook = instrumentPair.getOrderBook();
      orderbook.cancelOrder(this);
    }
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append(CANCELORDER_SECURITYID_EQ).append(securityId).append(PRICE_EQ).append(price).append(SIDE_EQ).append(side).append(QTY_EQ)
        .append(qty).append(ORIGORDERID_EQ).append(origOrderId).append(CANCELID_EQ).append(cancelId).append(CANCELPRIORITY_EQ)
        .append(cancelPriority).append(CLORID_EQ).append(clOrdId == null ? "" : String.valueOf(clOrdId)).append(SENDERCOMPID_EQ)
        .append(senderCompId).append(ACCOUNT_EQ).append(account).append(ORDTYPE_EQ).append(ordType).append(TYPE_EQ).append(type)
        .append(PRICEINT_EQ).append(priceInt).append(QUANTITYLONG_EQ).append(quantityLong).append(QUANTITYORIGLONG_EQ)
        .append(quantityOrigLong).append(SECONDARYORDERID_EQ).append(secondaryOrderId).append(INPUTTIME_EQ).append(inputTime)
        .append(DECODEDTIME_EQ).append(decodedTime).append(SOURCESEQNUM_EQ).append(sourceSeqNum).append(SOURCESENDTIME_EQ)
        .append(sourceSendTime).append(", cancelType=").append(cancelType).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"CancelOrder\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"securityId\":").append(securityId).append(",\"price\":").append(price).append(",\"qty\":").append(qty)
        .append(",\"qty_scale\":").append(qtyScale).append(",\"side\":").append("\"").append(side).append("\"").append(",\"origOrderId\":")
        .append(origOrderId).append(",\"secondaryOrderId\":").append(secondaryOrderId).append(",\"cancelId\":").append(cancelId)
        .append(",\"cancelPriority\":").append(cancelPriority).append(",\"clOrdId\":").append("\"")
        .append(clOrdId == null ? "" : String.valueOf(clOrdId)).append("\"").append(",\"senderCompIdCharArr\":").append("\"")
        .append(senderCompId).append("\"").append(",\"senderCompIdAsString\":").append("\"").append(senderCompId).append("\"")
        .append(",\"account\":").append("\"").append(account).append("\"").append(",\"userId\":").append(account).append(",\"ordType\":")
        .append("\"").append(ordType).append("\"").append(",\"type\":").append(type).append(",\"priceInt\":").append(priceInt)
        .append(",\"quantityLong\":").append(quantityLong).append(",\"quantityOrigLong\":").append(quantityOrigLong);

    sb.append("}");
    return sb.toString();
  }

}
