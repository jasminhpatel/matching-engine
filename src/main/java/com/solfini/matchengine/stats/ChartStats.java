package com.solfini.matchengine.stats;

/**
 *
 * @author Chris Mack
 *
 */

public class ChartStats {
  private int high;
  private int low;
  private int open;
  private int close;
  private long quantity;
  private long startTime;
  private long volume;
  private long tradeCount;
  private double underlyerMark;

  public ChartStats(final long startTime, final long quantity, final int open, final int high, final int low, final int close,
      final double underlyerMark) {
    this.startTime = startTime;
    this.high = high;
    this.low = low;
    this.open = open;
    this.close = close;
    this.quantity = quantity;
    this.volume = quantity * close;
    this.underlyerMark = underlyerMark;
  }

  public ChartStats(final long startTime, final int last, final long quantity, final double underlyerMark) {
    this.startTime = startTime;
    this.high = last;
    this.low = last;
    this.open = last;
    this.close = last;
    this.quantity = quantity;
    this.volume = quantity * last;
    this.underlyerMark = underlyerMark;
  }

  public final void reset(final long startTime, final int last, final long quantity, final double underlyerMark) {
    this.startTime = startTime;
    this.high = last;
    this.low = last;
    this.open = last;
    this.close = last;
    this.quantity = quantity;
    this.volume = quantity * last;
    this.tradeCount = 1;
    this.underlyerMark = underlyerMark;
  }

  public final void add(final long startTime, final int last, final long quantity, final double underlyerMark) {
    this.startTime = startTime;
    this.close = last;
    if (last > high)
      high = last;
    if (last < low)
      low = last;

    this.quantity += quantity;
    this.volume += quantity * last;
    this.underlyerMark += underlyerMark;
    this.tradeCount++;
  }

  public final void add(final ChartStats source) {
    if (source == null)
      return;

    this.startTime = source.startTime;
    if (source.high > this.high)
      this.high = source.high;
    if (source.low < this.low)
      this.low = source.low;
    this.open = source.open;
    if (this.close == 0)
      this.close = source.close;

    this.underlyerMark =
        ((this.underlyerMark * this.tradeCount) + (source.underlyerMark * source.tradeCount)) / (tradeCount + source.tradeCount);
    this.quantity += source.quantity;
    this.volume += source.volume;
    this.tradeCount += source.tradeCount;
  }

  public final int getHigh() {
    return high;
  }

  public final void setHigh(final int high) {
    this.high = high;
  }

  public final int getLow() {
    return low;
  }

  public final void setLow(final int low) {
    this.low = low;
  }

  public final long getQuantity() {
    return quantity;
  }

  public final void setQuantity(final long quantity) {
    this.quantity = quantity;
  }

  public final long getStartTime() {
    return startTime;
  }

  public final void setStartTime(final long startTime) {
    this.startTime = startTime;
  }

  public final int getOpen() {
    return open;
  }

  public final void setOpen(final int open) {
    this.open = open;
  }

  public final int getClose() {
    return close;
  }

  public final void setClose(final int close) {
    this.close = close;
  }

  public final long getVolume() {
    return volume;
  }

  public final void setVolume(final long volume) {
    this.volume = volume;
  }

  public final double getUnderlyerMark() {
    return underlyerMark;
  }

  public final void setUnderlyerMark(final double underlyerMark) {
    this.underlyerMark = underlyerMark;
  }

  public final long getTradeCount() {
    return tradeCount;
  }

  public final void setTradeCount(final long tradeCount) {
    this.tradeCount = tradeCount;
  }

  public final long getVWAP() {
    if (quantity == 0)
      return 0;
    return volume / quantity;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("ChartStats [high=");
    sb.append(high).append(", low=").append(low).append(", open=").append(open).append(", close=").append(close).append(", quantity=")
        .append(quantity).append(", startTime=").append(startTime).append("]");
    return sb.toString();
  }

}
