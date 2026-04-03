package com.solfini.matchengine.liquidity;

import com.solfini.matchengine.ExecutionExchangeConfig;
import com.solfini.matchengine.executionexchange.ExternalOrder;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;

import java.math.BigDecimal;

public class LiquidityOrder implements ExternalOrder {
  private int userId;
  private int securityId;
  private long subscriptionId;
  private String baseSymbol;
  private String quotedSymbol;
  private String origClOrdId;
  private String clOrdId;
  private String exchange;
  private Side side;
  private OrdType ordType;
  private TimeInForce timeInForce;
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
  private boolean futuresEnabled;
  private double tradeValue;
  private double averagePrice;
  private double fee;

  private ExecutionExchangeConfig subscription;

  private BigDecimal xQuantity;
  private BigDecimal xPrice;
  private Instrument instrument;
  private CurrencyPair currencyPair;
  private String closeClOrdId;
  private double borrowedAmount;
  private boolean repaid;

  // in-memory
  private XExchange xExchange;

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("LiquidityOrder{");
    sb.append("userId=").append(userId);
    sb.append(", securityId=").append(securityId);
    sb.append(", subscriptionId=").append(subscriptionId);
    sb.append(", baseSymbol='").append(baseSymbol).append('\'');
    sb.append(", quotedSymbol='").append(quotedSymbol).append('\'');
    sb.append(", origClOrdId='").append(origClOrdId).append('\'');
    sb.append(", clOrdId='").append(clOrdId).append('\'');
    sb.append(", exchange='").append(exchange).append('\'');
    sb.append(", side=").append(side);
    sb.append(", ordType=").append(ordType);
    sb.append(", timeInForce=").append(timeInForce);
    sb.append(", orderQty=").append(orderQty);
    sb.append(", orderQtyScale=").append(orderQtyScale);
    sb.append(", price=").append(price);
    sb.append(", priceScale=").append(priceScale);
    sb.append(", result='").append(result).append('\'');
    sb.append(", created=").append(created);
    sb.append(", externalId='").append(externalId).append('\'');
    sb.append(", originalAmount=").append(originalAmount);
    sb.append(", cumulativeAmount=").append(cumulativeAmount);
    sb.append(", averagePrice=").append(averagePrice);
    sb.append(", status='").append(status).append('\'');
    sb.append(", futuresEnabled=").append(futuresEnabled);
    sb.append(", tradeValue=").append(tradeValue);
    // sb.append(", subscription=").append(subscription);
    sb.append(", xQuantity=").append(xQuantity);
    sb.append(", xPrice=").append(xPrice);
    // sb.append(", instrument=").append(instrument);
    // sb.append(", currencyPair=").append(currencyPair);
    sb.append(", closeClOrdId='").append(closeClOrdId).append('\'');
    sb.append(", borrowedAmount=").append(borrowedAmount);
    sb.append(", repaid=").append(repaid);
    // sb.append(", xExchange=").append(xExchange);
    sb.append('}');
    return sb.toString();
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

  public final ExecutionExchangeConfig getSubscription() {
    return subscription;
  }

  public final void setSubscription(final ExecutionExchangeConfig subscription) {
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

  public final String getCloseClOrdId() {
    return closeClOrdId;
  }

  public final void setCloseClOrdId(final String closeClOrdId) {
    this.closeClOrdId = closeClOrdId;
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

  public final XExchange getxExchange() {
    return xExchange;
  }

  public final void setxExchange(final XExchange xExchange) {
    this.xExchange = xExchange;
  }

  @Override
  public final double getFee() {
    return fee;
  }

  @Override
  public final void setFee(double fee) {
    this.fee = fee;
  }

  @Override
  public final double getAveragePrice() {
    return averagePrice;
  }

  @Override
  public final void setAveragePrice(final double averagePrice) {
    this.averagePrice = averagePrice;
  }
}
