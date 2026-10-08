package com.solfini.matchengine.executionexchange;

/**
 * A symbol that is delisted, suspended, or scheduled for delisting on an external exchange.
 */
public class DelistedSymbol {
  public static final String TRADING = "TRADING";

  private String exchange;
  private boolean futures;
  private String symbol;
  private String base;
  private String quote;
  /** Raw exchange status, for display only (e.g. BREAK, Delivering, REMOVED); decide with isTradingDisabled(). */
  private String status;
  /** True when trading has stopped on the exchange (delisted or suspended), set by the exchange client. */
  private boolean tradingDisabled;
  /** Delisting time in millis, 0 when the exchange gives no date */
  private long delistTime;
  private long detectedAt;

  public final String getKey() {
    return getKey(exchange, futures, symbol);
  }

  public static String getKey(final String exchange, final boolean futures, final String symbol) {
    return (exchange + "_" + (futures ? "1" : "0") + "_" + symbol).toLowerCase();
  }

  /** Still trading, but a delisting date is scheduled. */
  public final boolean isUpcoming() {
    return !tradingDisabled && delistTime > 0;
  }

  /** Trading has stopped on the exchange (delisted or suspended). */
  public final boolean isTradingDisabled() {
    return tradingDisabled;
  }

  public final void setTradingDisabled(final boolean tradingDisabled) {
    this.tradingDisabled = tradingDisabled;
  }

  public final String getExchange() {
    return exchange;
  }

  public final void setExchange(final String exchange) {
    this.exchange = exchange;
  }

  public final boolean isFutures() {
    return futures;
  }

  public final void setFutures(final boolean futures) {
    this.futures = futures;
  }

  public final String getSymbol() {
    return symbol;
  }

  public final void setSymbol(final String symbol) {
    this.symbol = symbol;
  }

  public final String getBase() {
    return base;
  }

  public final void setBase(final String base) {
    this.base = base;
  }

  public final String getQuote() {
    return quote;
  }

  public final void setQuote(final String quote) {
    this.quote = quote;
  }

  public final String getStatus() {
    return status;
  }

  public final void setStatus(final String status) {
    this.status = status;
  }

  public final long getDelistTime() {
    return delistTime;
  }

  public final void setDelistTime(final long delistTime) {
    this.delistTime = delistTime;
  }

  public final long getDetectedAt() {
    return detectedAt;
  }

  public final void setDetectedAt(final long detectedAt) {
    this.detectedAt = detectedAt;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("{");
    sb.append("exchange='").append(exchange).append('\'');
    sb.append(", futures=").append(futures);
    sb.append(", symbol='").append(symbol).append('\'');
    sb.append(", base='").append(base).append('\'');
    sb.append(", quote='").append(quote).append('\'');
    sb.append(", status='").append(status).append('\'');
    sb.append(", tradingDisabled=").append(tradingDisabled);
    sb.append(", delistTime=").append(delistTime);
    sb.append(", detectedAt=").append(detectedAt);
    sb.append('}');
    return sb.toString();
  }
}
