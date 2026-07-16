package com.solfini.matchengine.copytrade;

import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.executionexchange.ExternalOrder;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.persist.Persister;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;

import java.math.BigDecimal;
import java.util.Objects;

public class CopyTradeOrder extends Message implements ExternalOrder {
  private int userId;
  private int securityId;
  private long subscriptionId;
  private String baseSymbol;
  private String quotedSymbol;
  private String origClOrdId;
  private String clOrdId;
  private String platform;
  private String accountId;
  private String exchange;
  private Side side;
  private OrdType ordType;
  private TimeInForce timeInForce;
  private long signalPercentage;
  private short signalPercentageScale;
  private long signalPrice;
  private short signalPriceScale;
  private long orderQty;
  private short orderQtyScale;
  private long price;
  private short priceScale;
  private String result;
  private long created;
  private String externalId;
  private double originalAmount;
  private double cumulativeAmount;
  private String status;
  private boolean isToClose;
  private boolean closed;
  private boolean futuresEnabled;
  private double tradeValue;
  private double averagePrice;
  private double fee;

  private ExchangeSubscription subscription;

  private BigDecimal xQuantity;
  private BigDecimal xPrice;
  private Instrument instrument;
  private CurrencyPair currencyPair;
  private String closeClOrdId;
  private double borrowedAmount;
  private boolean repaid;
  //in-memory only
  private CopyTradeOrder openOrder;

  private List<CopyTradeOrder> openOrders = new ArrayList<>();

  //in-memory
  private XExchange xExchange;
  private Order order;

  private boolean pendingCloseOrder;

  public CopyTradeOrder() {
    this.pendingCloseOrder = false;
  }

  public CopyTradeOrder(final String clOrdId, final String baseSymbol, final String quotedSymbol, final InstrumentPair pair, final Order order,
      final ExchangeSubscription subscription, final String accountId) {
    this.userId = subscription.getUserId();
    this.securityId = pair.getId();
    this.subscriptionId = subscription.getId();
    this.baseSymbol = baseSymbol;
    this.quotedSymbol = quotedSymbol;
    this.origClOrdId = order.getClOrdId();
    this.clOrdId = clOrdId;
    this.platform = subscription.getPlatform();
    this.accountId = accountId;
    this.exchange = subscription.getExchange();
    this.side = order.getSide();
    this.ordType = order.getOrdType();
    this.timeInForce = order.getTimeInForce();
    this.signalPercentage = order.getQty();
    this.signalPercentageScale = order.getQtyScale();
    this.signalPrice = order.getPrice();
    this.signalPriceScale = order.getPriceScale();
    this.created = System.currentTimeMillis();
    this.isToClose = order.isToClose();
    this.sourceSendTime = order.getSourceSendTime();
    this.futuresEnabled = subscription.isFuturesEnabled();

    this.subscription = subscription;

    this.order = order;
    this.pendingCloseOrder = false;
  }

  public CopyTradeOrder(final CopyTradeOrder source) {
    this.userId = source.userId;
    this.securityId = source.securityId;
    this.subscriptionId = source.subscriptionId;
    this.baseSymbol = source.baseSymbol;
    this.quotedSymbol = source.quotedSymbol;
    this.origClOrdId = source.origClOrdId;
    this.clOrdId = source.clOrdId;
    this.platform = source.platform;
    this.accountId = source.accountId;
    this.exchange = source.exchange;
    //this.side = source.side;
    //this.ordType = source.ordType;
    //this.timeInForce = source.timeInForce;
    this.signalPercentage = source.signalPercentage;
    this.signalPercentageScale = source.signalPercentageScale;
    this.signalPrice = source.signalPrice;
    this.signalPriceScale = source.signalPriceScale;
    this.orderQty = source.orderQty;
    this.orderQtyScale = source.orderQtyScale;
    this.price = source.price;
    this.priceScale = source.priceScale;
    this.result = source.result;
    this.created = source.created;
    this.externalId = source.externalId;
    this.originalAmount = source.originalAmount;
    this.cumulativeAmount = source.cumulativeAmount;
    this.status = source.status;
    this.isToClose = source.isToClose;
    this.closed = source.closed;
    this.subscription = source.subscription;
    this.xQuantity = source.xQuantity;
    this.xPrice = source.xPrice;
    this.instrument = source.instrument;
    this.currencyPair = source.currencyPair;
    this.closeClOrdId = source.closeClOrdId;
    this.borrowedAmount = source.borrowedAmount;
    this.repaid = source.repaid;
    this.sourceSendTime = source.sourceSendTime;
    this.futuresEnabled = source.futuresEnabled;

    this.order = source.order;
    this.pendingCloseOrder = source.pendingCloseOrder;
  }

  public final int getUserId() {
    return userId;
  }

  public final void setUserId(final int userId) {
    this.userId = userId;
  }

  public final int getSecurityId() {
    return securityId;
  }

  public final void setSecurityId(final int securityId) {
    this.securityId = securityId;
  }

  public final long getSubscriptionId() {
    return subscriptionId;
  }

  public final void setSubscriptionId(final long subscriptionId) {
    this.subscriptionId = subscriptionId;
  }

  public final String getBaseSymbol() {
    return baseSymbol;
  }

  public final void setBaseSymbol(final String baseSymbol) {
    this.baseSymbol = baseSymbol;
  }

  public final String getQuotedSymbol() {
    return quotedSymbol;
  }

  public final void setQuotedSymbol(final String quotedSymbol) {
    this.quotedSymbol = quotedSymbol;
  }

  public final String getOrigClOrdId() {
    return origClOrdId;
  }

  public final void setOrigClOrdId(final String origClOrdId) {
    this.origClOrdId = origClOrdId;
  }

  public final String getClOrdId() {
    return clOrdId;
  }

  public final void setClOrdId(final String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public final String getPlatform() {
    return platform;
  }

  public final void setPlatform(final String platform) {
    this.platform = platform;
  }

  public final String getAccountId() {
    return accountId;
  }

  public final void setAccountId(final String accountId) {
    this.accountId = accountId;
  }

  public final String getExchange() {
    return exchange;
  }

  public final void setExchange(final String exchange) {
    this.exchange = exchange;
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

  public final TimeInForce getTimeInForce() {
    return timeInForce;
  }

  public final void setTimeInForce(final TimeInForce timeInForce) {
    this.timeInForce = timeInForce;
  }

  public final long getSignalPercentage() {
    return signalPercentage;
  }

  public final void setSignalPercentage(final long signalPercentage) {
    this.signalPercentage = signalPercentage;
  }

  public final short getSignalPercentageScale() {
    return signalPercentageScale;
  }

  public final void setSignalPercentageScale(final short signalPercentageScale) {
    this.signalPercentageScale = signalPercentageScale;
  }

  public final long getSignalPrice() {
    return signalPrice;
  }

  public final void setSignalPrice(final long signalPrice) {
    this.signalPrice = signalPrice;
  }

  public final short getSignalPriceScale() {
    return signalPriceScale;
  }

  public final void setSignalPriceScale(final short signalPriceScale) {
    this.signalPriceScale = signalPriceScale;
  }

  public final long getOrderQty() {
    return orderQty;
  }

  public final void setOrderQty(final long orderQty) {
    this.orderQty = orderQty;
  }

  public final short getOrderQtyScale() {
    return orderQtyScale;
  }

  public final void setOrderQtyScale(final short orderQtyScale) {
    this.orderQtyScale = orderQtyScale;
  }

  public final long getPrice() {
    return price;
  }

  public final void setPrice(final long price) {
    this.price = price;
  }

  public final short getPriceScale() {
    return priceScale;
  }

  public final void setPriceScale(final short priceScale) {
    this.priceScale = priceScale;
  }

  public final String getResult() {
    return result;
  }

  public final void setResult(final String result) {
    this.result = result;
  }

  @Override
  public final double getFee() {
    return fee;
  }

  @Override
  public final void setFee(final double fee) {
    this.fee = fee;
  }

  public final long getCreated() {
    return created;
  }

  public final void setCreated(final long created) {
    this.created = created;
  }

  public final String getExternalId() {
    return externalId;
  }

  public final void setExternalId(final String externalId) {
    this.externalId = externalId;
  }

  public final double getOriginalAmount() {
    return originalAmount;
  }

  public final void setOriginalAmount(final double originalAmount) {
    this.originalAmount = originalAmount;
  }

  public final double getCumulativeAmount() {
    return cumulativeAmount;
  }

  public final void setCumulativeAmount(final double cumulativeAmount) {
    this.cumulativeAmount = cumulativeAmount;
  }

  public final String getStatus() {
    return status;
  }

  public final void setStatus(final String status) {
    this.status = status;
  }

  public final ExchangeSubscription getSubscription() {
    if (this.subscription == null) {
      this.subscription = InfluencerSubscriptionCache.get(this.subscriptionId);
    }
    return subscription;
  }

  public final void setSubscription(final ExchangeSubscription subscription) {
    this.subscription = subscription;
  }

  public final BigDecimal getxQuantity() {
    return xQuantity;
  }

  public final void setxQuantity(final BigDecimal xQuantity) {
    this.xQuantity = xQuantity;
  }

  public final BigDecimal getxPrice() {
    return xPrice;
  }

  public final void setxPrice(final BigDecimal xPrice) {
    this.xPrice = xPrice;
  }

  public final Instrument getInstrument() {
    return instrument;
  }

  public final void setInstrument(final Instrument instrument) {
    this.instrument = instrument;
  }

  public final CurrencyPair getCurrencyPair() {
    return currencyPair;
  }

  public final void setCurrencyPair(final CurrencyPair currencyPair) {
    this.currencyPair = currencyPair;
  }

  public final boolean isToClose() {
    return isToClose;
  }

  public final void setToClose(final boolean isToClose) {
    this.isToClose = isToClose;
  }

  public final boolean isClosed() {
    return closed;
  }

  public final void setClosed(final boolean closed) {
    this.closed = closed;
  }

  public final String getCloseClOrdId() {
    return closeClOrdId;
  }

  public final void setCloseClOrdId(final String closeClOrdId) {
    this.closeClOrdId = closeClOrdId;
  }

  public final CopyTradeOrder getOpenOrder() {
    return openOrder;
  }

  public final void setOpenOrder(final CopyTradeOrder openOrder) {
    this.openOrder = openOrder;
  }

  public final List<CopyTradeOrder> getOpenOrders() {
    return openOrders;
  }

  public final XExchange getxExchange() {
    return xExchange;
  }

  public final void setxExchange(final XExchange xExchange) {
    this.xExchange = xExchange;
  }

  public final double getBorrowedAmount() {
    return borrowedAmount;
  }

  public final void setBorrowedAmount(final double borrowedAmount) {
    this.borrowedAmount = borrowedAmount;
  }

  public final boolean isRepaid() {
    return repaid;
  }

  public final void setRepaid(final boolean repaid) {
    this.repaid = repaid;
  }

  public final boolean isFuturesEnabled() {
    return futuresEnabled;
  }

  public final void setFuturesEnabled(final boolean futuresEnabled) {
    this.futuresEnabled = futuresEnabled;
  }

  public final double getTradeValue() {
    return tradeValue;
  }

  public final void setTradeValue(final double tradeValue) {
    this.tradeValue = tradeValue;
  }

  public Order getOrder() {
    return order;
  }

  public void setOrder(Order order) {
    this.order = order;
  }

  public boolean isPendingCloseOrder() {
    return pendingCloseOrder;
  }

  public void setPendingCloseOrder(boolean pendingCloseOrder) {
    this.pendingCloseOrder = pendingCloseOrder;
  }

  @Override
  public final double getAveragePrice() {
    return averagePrice;
  }

  @Override
  public final void setAveragePrice(final double averagePrice) {
    this.averagePrice = averagePrice;
  }

  public final Side getInverseSide() {
    if (side == Side.BUY) return Side.SELL;
    else if (side == Side.SELL) return Side.BUY;

    return null;
  }

  public final boolean isSuccessful() {
    return (result == null) || !(result.startsWith("FAILED") || result.startsWith("REJECTED"));
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    return null;
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.NULL_VAL;
  }

  @Override
  public final MessageType getMessageType() {
    return MessageType.COPY_TRADE;
  }

  @Override
  public final void onPersist() {
    Persister.onMessage(this);
  }

  @Override
  public final void onPublish() {
    //Copy trades are not published
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    final CopyTradeOrder copyTradeOrder = (CopyTradeOrder) o;
    return Objects.equals(clOrdId, copyTradeOrder.clOrdId) && Objects.equals(platform, copyTradeOrder.platform) && Objects.equals(accountId,
        copyTradeOrder.accountId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clOrdId, platform, accountId);
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder("{");
    sb.append("\"userId\":").append(userId);
    sb.append(",\"securityId\":").append(securityId);
    sb.append(",\"subscriptionId\":").append(subscriptionId);
    sb.append(",\"baseSymbol\":\"").append(baseSymbol).append('\"');
    sb.append(",\"quotedSymbol\":\"").append(quotedSymbol).append('\"');
    sb.append(",\"origClOrdId\":\"").append(origClOrdId).append('\"');
    sb.append(",\"clOrdId\":\"").append(clOrdId).append('\"');
    sb.append(",\"platform\":\"").append(platform).append('\"');
    sb.append(",\"accountId\":\"").append(accountId).append('\"');
    sb.append(",\"exchange\":\"").append(exchange).append('\"');
    sb.append(",\"side\":\"").append(side.name()).append('\"');
    sb.append(",\"ordType\":\"").append(ordType.name()).append('\"');
    sb.append(",\"timeInForce\":\"").append(timeInForce.name()).append('\"');
    sb.append(",\"ordQtyPercentage\":").append(signalPercentage);
    sb.append(",\"ordQtyPercentageScale\":").append(signalPercentageScale);
    sb.append(",\"ordPrice\":").append(signalPrice);
    sb.append(",\"ordPriceScale\":").append(signalPriceScale);
    sb.append(",\"orderQty\":").append(orderQty);
    sb.append(",\"orderQtyScale\":").append(orderQtyScale);
    sb.append(",\"price\":").append(price);
    sb.append(",\"priceScale\":").append(priceScale);
    sb.append(",\"result\":\"").append(result).append('\"');
    sb.append(",\"created\":").append(created);
    sb.append(",\"externalId\":\"").append(externalId).append('\"');
    sb.append(",\"originalAmount\":").append(originalAmount);
    sb.append(",\"cumulativeAmount\":").append(cumulativeAmount);
    sb.append(",\"status\":\"").append(status).append('\"');
    sb.append(",\"xQuantity\":\"").append(xQuantity);
    sb.append(",\"xPrice\":\"").append(xPrice);
    sb.append(",\"isToClose\":\"").append(isToClose);
    sb.append(",\"closed\":\"").append(closed);
    sb.append(",\"pendingCloseOrder\":\"").append(pendingCloseOrder);
    //sb.append(",\"currencyPair=").append(currencyPair);
    sb.append(",\"subscription\":").append(subscription.toJSON());
    sb.append('}');
    return sb.toString();
  }
}
