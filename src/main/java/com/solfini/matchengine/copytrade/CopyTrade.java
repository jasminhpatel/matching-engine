package com.solfini.matchengine.copytrade;

import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
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

public class CopyTrade extends Message {
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

  private InfluencerSubscription subscription;

  private BigDecimal xQuantity;
  private BigDecimal xPrice;
  private Instrument instrument;
  private CurrencyPair currencyPair;
  private String closeClOrdId;
  private double borrowedAmount;
  private boolean repaid;
  //in-memory only
  private CopyTrade openOrder;

  //in-memory
  private XExchange xExchange;

  private List<CopyTrade> openOrders = new ArrayList<>();

  public CopyTrade() {}

  public CopyTrade(final String clOrdId, final String baseSymbol, final String quotedSymbol, final InstrumentPair pair, final Order order,
      final InfluencerSubscription subscription, final String accountId) {
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
  }

  public CopyTrade(final CopyTrade source) {
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
  }

  public int getUserId() {
    return userId;
  }

  public void setUserId(int userId) {
    this.userId = userId;
  }

  public int getSecurityId() {
    return securityId;
  }

  public void setSecurityId(int securityId) {
    this.securityId = securityId;
  }

  public long getSubscriptionId() {
    return subscriptionId;
  }

  public void setSubscriptionId(long subscriptionId) {
    this.subscriptionId = subscriptionId;
  }

  public String getBaseSymbol() {
    return baseSymbol;
  }

  public void setBaseSymbol(String baseSymbol) {
    this.baseSymbol = baseSymbol;
  }

  public String getQuotedSymbol() {
    return quotedSymbol;
  }

  public void setQuotedSymbol(String quotedSymbol) {
    this.quotedSymbol = quotedSymbol;
  }

  public String getOrigClOrdId() {
    return origClOrdId;
  }

  public void setOrigClOrdId(String origClOrdId) {
    this.origClOrdId = origClOrdId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public void setClOrdId(String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  public String getExchange() {
    return exchange;
  }

  public void setExchange(String exchange) {
    this.exchange = exchange;
  }

  public Side getSide() {
    return side;
  }

  public void setSide(Side side) {
    this.side = side;
  }

  public OrdType getOrdType() {
    return ordType;
  }

  public void setOrdType(OrdType ordType) {
    this.ordType = ordType;
  }

  public TimeInForce getTimeInForce() {
    return timeInForce;
  }

  public void setTimeInForce(TimeInForce timeInForce) {
    this.timeInForce = timeInForce;
  }

  public long getSignalPercentage() {
    return signalPercentage;
  }

  public void setSignalPercentage(long signalPercentage) {
    this.signalPercentage = signalPercentage;
  }

  public short getSignalPercentageScale() {
    return signalPercentageScale;
  }

  public void setSignalPercentageScale(short signalPercentageScale) {
    this.signalPercentageScale = signalPercentageScale;
  }

  public long getSignalPrice() {
    return signalPrice;
  }

  public void setSignalPrice(long signalPrice) {
    this.signalPrice = signalPrice;
  }

  public short getSignalPriceScale() {
    return signalPriceScale;
  }

  public void setSignalPriceScale(short signalPriceScale) {
    this.signalPriceScale = signalPriceScale;
  }

  public long getOrderQty() {
    return orderQty;
  }

  public void setOrderQty(long orderQty) {
    this.orderQty = orderQty;
  }

  public short getOrderQtyScale() {
    return orderQtyScale;
  }

  public void setOrderQtyScale(short orderQtyScale) {
    this.orderQtyScale = orderQtyScale;
  }

  public long getPrice() {
    return price;
  }

  public void setPrice(long price) {
    this.price = price;
  }

  public short getPriceScale() {
    return priceScale;
  }

  public void setPriceScale(short priceScale) {
    this.priceScale = priceScale;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public long getCreated() {
    return created;
  }

  public void setCreated(long created) {
    this.created = created;
  }

  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  public double getOriginalAmount() {
    return originalAmount;
  }

  public void setOriginalAmount(double originalAmount) {
    this.originalAmount = originalAmount;
  }

  public double getCumulativeAmount() {
    return cumulativeAmount;
  }

  public void setCumulativeAmount(double cumulativeAmount) {
    this.cumulativeAmount = cumulativeAmount;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public InfluencerSubscription getSubscription() {
    if (this.subscription == null) {
      this.subscription = InfluencerSubscriptionCache.get(this.subscriptionId);
    }
    return subscription;
  }

  public void setSubscription(InfluencerSubscription subscription) {
    this.subscription = subscription;
  }

  public BigDecimal getxQuantity() {
    return xQuantity;
  }

  public void setxQuantity(BigDecimal xQuantity) {
    this.xQuantity = xQuantity;
  }

  public BigDecimal getxPrice() {
    return xPrice;
  }

  public void setxPrice(BigDecimal xPrice) {
    this.xPrice = xPrice;
  }

  public Instrument getInstrument() {
    return instrument;
  }

  public void setInstrument(Instrument instrument) {
    this.instrument = instrument;
  }

  public CurrencyPair getCurrencyPair() {
    return currencyPair;
  }

  public void setCurrencyPair(CurrencyPair currencyPair) {
    this.currencyPair = currencyPair;
  }

  public boolean isToClose() {
    return isToClose;
  }

  public void setToClose(boolean isToClose) {
    this.isToClose = isToClose;
  }

  public boolean isClosed() {
    return closed;
  }

  public void setClosed(boolean closed) {
    this.closed = closed;
  }

  public String getCloseClOrdId() {
    return closeClOrdId;
  }

  public void setCloseClOrdId(String closeClOrdId) {
    this.closeClOrdId = closeClOrdId;
  }

  public CopyTrade getOpenOrder() {
    return openOrder;
  }

  public void setOpenOrder(CopyTrade openOrder) {
    this.openOrder = openOrder;
  }

  public XExchange getxExchange() {
    return xExchange;
  }

  public void setxExchange(XExchange xExchange) {
    this.xExchange = xExchange;
  }

  public double getBorrowedAmount() {
    return borrowedAmount;
  }

  public void setBorrowedAmount(double borrowedAmount) {
    this.borrowedAmount = borrowedAmount;
  }

  public boolean isRepaid() {
    return repaid;
  }

  public void setRepaid(boolean repaid) {
    this.repaid = repaid;
  }

  public boolean isFuturesEnabled() {
    return futuresEnabled;
  }

  public void setFuturesEnabled(boolean futuresEnabled) {
    this.futuresEnabled = futuresEnabled;
  }

  public double getTradeValue() {
    return tradeValue;
  }

  public void setTradeValue(double tradeValue) {
    this.tradeValue = tradeValue;
  }

  public Side getInverseSide() {
    if (side == Side.BUY) return Side.SELL;
    else if (side == Side.SELL) return Side.BUY;

    return null;
  }

  public boolean isSuccessful() {
    return (result == null) || !(result.startsWith("FAILED") || result.startsWith("REJECTED"));
  }

  public List<CopyTrade> getOpenOrders() {
    return openOrders;
  }

  public void setOpenOrders(List<CopyTrade> openOrders) {
    this.openOrders = openOrders;
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    return null;
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.NULL_VAL;
  }

  @Override
  public MessageType getMessageType() {
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
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    CopyTrade copyTrade = (CopyTrade) o;
    return Objects.equals(clOrdId, copyTrade.clOrdId) && Objects.equals(platform, copyTrade.platform) && Objects.equals(accountId,
        copyTrade.accountId);
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
    //sb.append(",\"currencyPair=").append(currencyPair);
    sb.append(",\"subscription\":").append(subscription.toJSON());
    sb.append('}');
    return sb.toString();
  }
}
