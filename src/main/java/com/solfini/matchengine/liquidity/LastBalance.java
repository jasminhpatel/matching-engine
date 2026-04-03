package com.solfini.matchengine.liquidity;

import com.google.common.util.concurrent.AtomicDouble;

public class LastBalance {
  private final String symbol;
  private volatile double quantity;
  private volatile double costBasis;
  private volatile double markPrice;
  private volatile double unrealized;
  private volatile boolean usdcQuoted;
  private volatile boolean usdtQuoted;
  private volatile long lastUpdatedTime;

  private final AtomicDouble reservedAmount = new AtomicDouble();

  public LastBalance(final String symbol, final double quantity) {
    this.symbol = symbol;
    this.quantity = quantity;
    if (symbol != null) {
      if (symbol.contains("USDC"))
        usdcQuoted = true;
      else if (symbol.contains("USDT"))
        usdtQuoted = true;
    }
  }

  public LastBalance(final String symbol, final double quantity, final double markPrice) {
    this.symbol = symbol;
    this.quantity = quantity;
    this.markPrice = markPrice;
    if (symbol != null) {
      if (symbol.contains("USDC"))
        usdcQuoted = true;
      else if (symbol.contains("USDT"))
        usdtQuoted = true;
    }
  }

  public LastBalance(final String symbol, final double quantity, final double markPrice, final double costBasis) {
    this.symbol = symbol;
    this.quantity = quantity;
    this.markPrice = markPrice;
    this.costBasis = costBasis;
    unrealized = (markPrice - costBasis) * quantity;
    if (symbol != null) {
      if (symbol.contains("USDC"))
        usdcQuoted = true;
      else if (symbol.contains("USDT"))
        usdtQuoted = true;
    }
  }

  public final String getSymbol() {
    return symbol;
  }

  public final double getQuantity() {
    return quantity;
  }

  public final void setQuantity(final double quantity) {
    this.quantity = quantity;
  }

  public final double getCostBasis() {
    return costBasis;
  }

  public final void setCostBasis(final double costBasis) {
    this.costBasis = costBasis;
  }

  public final double getMarkPrice() {
    return markPrice;
  }

  public final void setMarkPrice(final double markPrice) {
    this.markPrice = markPrice;
  }

  public final double getUnrealized() {
    return unrealized;
  }

  public final void setUnrealized(final double unrealized) {
    this.unrealized = unrealized;
  }

  public final boolean isUsdcQuoted() {
    return usdcQuoted;
  }

  public final void setUsdcQuoted(final boolean usdcQuoted) {
    this.usdcQuoted = usdcQuoted;
  }

  public final boolean isUsdtQuoted() {
    return usdtQuoted;
  }

  public final void setUsdtQuoted(final boolean usdtQuoted) {
    this.usdtQuoted = usdtQuoted;
  }

  public AtomicDouble getReservedAmount() {
    return reservedAmount;
  }

  public final void update(final double quantity, final double markPrice) {
    this.quantity = quantity;
    this.markPrice = markPrice;
    unrealized = (markPrice - costBasis) * quantity;
  }

  public final void update(final double quantity, final double markPrice, final double costBasis) {
    this.quantity = quantity;
    this.markPrice = markPrice;
    this.costBasis = costBasis;
    unrealized = (markPrice - costBasis) * quantity;
  }

  public final double calcNotoional() {
    return Math.abs(quantity) * markPrice;
  }

  @Override
  public String toString() {
    return "LastBalance{" + "symbol='" + symbol + '\'' + ", quantity=" + quantity + ", costBasis=" + costBasis + ", markPrice=" + markPrice
        + ", unrealized=" + unrealized + ", usdcQuoted=" + usdcQuoted + ", usdtQuoted=" + usdtQuoted + '}';
  }
}
