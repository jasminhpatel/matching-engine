package com.solfini.matchengine.message.internal;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.CancelReplaceOrderDecoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.UserCache;

/**
 *
 * @author Chris Mack
 *
 */
public class CancelReplaceOrder extends Message implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(CancelReplaceOrder.class);

  private int securityId;
  private long price;
  private short priceScale;
  private long qty;
  private short qtyScale;
  private long price2;
  private short price2Scale;
  private long qty2;
  private short qty2Scale;
  private Side side;
  private long origOrderId;
  private long cancelId;
  private long cancelPriority;
  private long newOrderId;
  private long secondaryOrderId; // orderId grouped by instrumentPair

  private long assetId;
  private int tokenId;
  private long selectId;

  private String clOrdId;
  private int submitterId;
  private int account; // userid
  private OrdType ordType;
  private int type;
  private int priceInt;
  private int price2Int;
  private long quantityLong;
  private long quantityOrigLong;
  private Order order;
  private boolean forceAddOrder = false; // Add replacing order even if the removal is failed.

  public CancelReplaceOrder() {}

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.CANCEL_ORDER;
  }

  public CancelReplaceOrder(final CancelReplaceOrderDecoder cancelReplaceOrderDecoder, final long cancelId, final long cancelPriority,
      final long newOrderId, final long kafkaRecordOffset) {
    this.cancelId = cancelId;
    this.cancelPriority = cancelPriority;
    this.newOrderId = newOrderId;
    clOrdId = cancelReplaceOrderDecoder.clOrdID();
    if (clOrdId != null)
      clOrdId = clOrdId.trim();
    securityId = cancelReplaceOrderDecoder.securityId();
    origOrderId = cancelReplaceOrderDecoder.originalOrderId();
    secondaryOrderId = cancelReplaceOrderDecoder.secondaryOrderId();

    price = cancelReplaceOrderDecoder.price();
    priceScale = cancelReplaceOrderDecoder.priceScale();
    qty = cancelReplaceOrderDecoder.qty();
    qtyScale = cancelReplaceOrderDecoder.qtyScale();


    price2 = cancelReplaceOrderDecoder.price2();
    price2Scale = cancelReplaceOrderDecoder.price2Scale();
    qty2 = cancelReplaceOrderDecoder.qty2();
    qty2Scale = cancelReplaceOrderDecoder.qty2Scale();
    side = cancelReplaceOrderDecoder.side();
    ordType = OrdType.LIMIT;

    account = cancelReplaceOrderDecoder.userId();
    user = UserCache.get(account);
    submitterId = cancelReplaceOrderDecoder.submitterId();

    assetId = cancelReplaceOrderDecoder.assetId();
    tokenId = cancelReplaceOrderDecoder.tokenId();
    selectId = cancelReplaceOrderDecoder.selectId();

    forceAddOrder = cancelReplaceOrderDecoder.forceAddOrder() == BooleanType.FALSE ? false : true;

    // create new order
    order = OrderObjectPool.get();
    order.setOrderId(newOrderId);
    order.setSecondaryOrderId(NewOrderSingleHandler.getSecondaryOrderId(securityId));
    order.setClOrdId(clOrdId);
    order.setSecurityId(securityId);
    order.setPrice(price2, price2Scale);
    order.setQty(qty2, qty2Scale);
    order.setSide(side);
    order.setOrdType(ordType);
    order.setSenderCompId(senderCompId);
    order.setUser(user);
    order.setAccount(account);
    order.setTimeInForce(cancelReplaceOrderDecoder.timeInForce());
    order.setExpireTime(cancelReplaceOrderDecoder.expireTime());
    order.setStopPx(cancelReplaceOrderDecoder.stopPx(), cancelReplaceOrderDecoder.stopPxScale());
    order.setInputTime(inputTime);
    order.setDecodedTime(decodedTime);
    order.setSubmitterId(submitterId);
    order.setKafkaRecordOffset(kafkaRecordOffset);

    Message message = NewOrderSingleHandler.parseOrder(order);
    if (!(message instanceof Order)) {
      // business reject
      order = null;
    }
  }

  public CancelReplaceOrder(final Order order, final long cancelId, final long cancelPriority) {
    this.cancelId = cancelId;
    this.cancelPriority = cancelPriority;
    clOrdId = order.getClOrdId();
    securityId = order.getSecurityId();
    origOrderId = order.getOrderId();

    price = order.getPrice();
    qty = order.getQty();
    side = order.getSide();
    ordType = order.getOrdType();
    senderCompId = order.getSenderCompId();
    account = order.getAccount();
    submitterId = order.getSubmitterId();
    assetId = order.getAssetId();
    tokenId = order.getTokenId();
    selectId = order.getSelectId();
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

  public final short getPriceScale() {
    return priceScale;
  }

  public final void setPrice(final long price, final short price_scale) {
    this.price = price;
    this.priceScale = price_scale;
  }

  public final long getQty() {
    return qty;
  }

  public final short getQtyScale() {
    return qtyScale;
  }

  public final void setQty(final long qty, final short qty_scale) {
    this.qty = qty;
    this.qtyScale = qty_scale;
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

  public final int getPriceInt() {
    return priceInt;
  }

  public final void setPriceInt(final int priceInt) {
    this.priceInt = priceInt;
  }

  public final int getPrice2Int() {
    return price2Int;
  }

  public final void setPrice2Int(final int price2Int) {
    this.price2Int = price2Int;
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

  public final long getPrice2() {
    return price2;
  }

  public final void setPrice2(final long price2) {
    this.price2 = price2;
  }

  public final short getPrice2Scale() {
    return price2Scale;
  }

  public final void setPrice2Scale(final short price2Scale) {
    this.price2Scale = price2Scale;
  }

  public final long getQty2() {
    return qty2;
  }

  public final void setQty2(final long qty2) {
    this.qty2 = qty2;
  }

  public final short getQty2Scale() {
    return qty2Scale;
  }

  public final void setQty2Scale(final short qty2Scale) {
    this.qty2Scale = qty2Scale;
  }

  public final void setPrice(final long price) {
    this.price = price;
  }

  public final void setQty(final long qty) {
    this.qty = qty;
  }

  public final long getNewOrderId() {
    return newOrderId;
  }

  public final void setNewOrderId(final long newOrderId) {
    this.newOrderId = newOrderId;
  }

  public final int getSubmitterId() {
    return submitterId;
  }

  public final void setSubmitterId(final int submitterId) {
    this.submitterId = submitterId;
  }

  public final boolean isForceAddOrder() {
    return this.forceAddOrder;
  }

  public final void setForceAddOrder(final boolean forceAddOrder) {
    this.forceAddOrder = forceAddOrder;
  }

  public final long getAssetId() {
    return assetId;
  }

  public final void setAssetId(final int assetId) {
    this.assetId = assetId;
  }

  public final int getTokenId() {
    return tokenId;
  }

  public final void setTokenId(final int tokenId) {
    this.tokenId = tokenId;
  }

  public final long getSelectId() {
    return selectId;
  }

  public final void setSelectId(final int selectId) {
    this.selectId = selectId;
  }

  public final Order getOrder() {
    if (order != null) {
      order.setInputTime(inputTime);
      order.setDecodedTime(decodedTime);
    }
    return order;
  }

  public final void setOrder(final Order order) {
    this.order = order;
  }

  public final long getSecondaryOrderId() {
    return secondaryOrderId;
  }

  public final void setSecondaryOrderId(final long secondaryOrderId) {
    this.secondaryOrderId = secondaryOrderId;
  }

  @Override
  public void onMatcher() {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(LOG_FMT_2, ">>> onMatcher", this);
    }

    // if neither origOrderId or secondaryOrderId are provided, attempt to lookup using clOrdId
    if (origOrderId <= 0 && secondaryOrderId <= 0 && user != null && clOrdId != null) {
      final Order orderByClorid = user.lookupOrderByClorid(clOrdId, securityId, side);

      if (orderByClorid != null && orderByClorid.getUser() != null && account == orderByClorid.getUser().getId()) {
        origOrderId = orderByClorid.getOrderId();
        secondaryOrderId = orderByClorid.getSecondaryOrderId();
      }
    }

    GlobalOrderBook.setOrderIdIfGreater(9, cancelId);
    if (order != null) {
      GlobalOrderBook.setOrderIdIfGreater(10, order.getOrderId());
    }

    final InstrumentPair instrumentPair = InstrumentCache.getPair(securityId);
    final OrderBook orderbook = instrumentPair.getOrderBook();
    orderbook.cancelReplaceOrder(this);

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(LOG_FMT_2, "<<< onMatcher", this);
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
    s.append(CANCELREPLACEORDER_SECURITYID_EQ).append(securityId).append(PRICE_EQ).append(price).append(PRICE_SCALE_EQ).append(priceScale)
        .append(QTY_EQ).append(qty).append(QTY_SCALE_EQ).append(qtyScale).append(PRICE2_EQ).append(price2).append(PRICE2_SCALE_EQ)
        .append(price2Scale).append(", qty2=").append(qty2).append(", qty2_scale=").append(qty2Scale).append(SIDE_EQ).append(side)
        .append(ORIGORDERID_EQ).append(origOrderId).append(CANCELID_EQ).append(cancelId).append(CANCELPRIORITY_EQ).append(cancelPriority)
        .append(", newOrderId=").append(newOrderId).append(CLORID_EQ).append(clOrdId == null ? "" : String.valueOf(clOrdId))
        .append(SENDERCOMPIDCHARARR_EQ).append(senderCompId).append(SENDERCOMPIDASSTRING_EQ).append(senderCompId).append(ACCOUNT_EQ)
        .append(account).append(USERID_EQ).append(account).append(ORDTYPE_EQ).append(ordType).append(TYPE_EQ).append(type)
        .append(PRICEINT_EQ).append(priceInt).append(PRICE2INT_EQ).append(price2Int).append(QUANTITYLONG_EQ).append(quantityLong)
        .append(QUANTITYORIGLONG_EQ).append(quantityOrigLong).append(SECONDARYORDERID_EQ).append(secondaryOrderId).append(ORDER_EQ)
        .append(order).append(INPUTTIME_EQ).append(inputTime).append(DECODEDTIME_EQ).append(decodedTime).append(SOURCESEQNUM_EQ)
        .append(sourceSeqNum).append(SOURCESENDTIME_EQ).append(sourceSendTime).append(ASSETID_EQ).append(assetId).append(TOKENID_EQ)
        .append(tokenId).append(SELECTID_EQ).append(selectId).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"CancelReplaceOrder\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"securityId\":").append(securityId).append(",\"price\":").append(price).append(",\"price_scale\":").append(priceScale)
        .append(",\"qty\":").append(qty).append(",\"qty_scale\":").append(qtyScale).append(",\"side\":").append("\"").append(side)
        .append("\"").append(",\"origOrderId\":").append(origOrderId).append(",\"secondaryOrderId\":").append(secondaryOrderId)
        .append(",\"cancelId\":").append(cancelId).append(",\"cancelPriority\":").append(cancelPriority).append(",\"clOrdId\":")
        .append("\"").append(clOrdId == null ? "" : String.valueOf(clOrdId)).append("\"").append(",\"senderCompIdCharArr\":").append("\"")
        .append(senderCompId).append("\"").append(",\"senderCompIdAsString\":").append("\"").append(senderCompId).append("\"")
        .append(",\"account\":").append("\"").append(account).append("\"").append(",\"userId\":").append(account).append(",\"ordType\":")
        .append("\"").append(ordType).append("\"").append(",\"type\":").append(type).append(",\"priceInt\":").append(priceInt)
        .append(",\"quantityLong\":").append(quantityLong).append(",\"quantityOrigLong\":").append(quantityOrigLong).append(",\"price2\":")
        .append(price2).append(",\"price2_scale\":").append(price2Scale).append(",\"qty2\":").append(qty2).append(",\"qty2_scale\":")
        .append(qty2Scale).append(",\"newOrderId\":").append(newOrderId).append(",\"cancelId\":").append(secondaryOrderId)
        .append(",\"price2Int\":").append(price2Int).append(",\"order\":").append(order.getOrderId()).append(",\"assetId\":")
        .append(assetId).append(",\"tokenId\":").append(tokenId).append(",\"selectId\":").append(selectId);
    sb.append("}");
    return sb.toString();
  }

}
