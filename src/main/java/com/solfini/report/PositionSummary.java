package com.solfini.report;

/**
 *
 * @author Chris Mack
 *
 */
public class PositionSummary {
  private int instrumentId;
  private long quantityLong;
  private long quantityShort;
  private long countLong;
  private long countShort;

  public PositionSummary(final int instrumentId) {
    this.instrumentId = instrumentId;
  }

  public final void reset() {
    this.instrumentId = 0;
    this.quantityLong = 0;
    this.quantityShort = 0;
    this.countLong = 0;
    this.countShort = 0;
  }

  public final int getInstrumentId() {
    return instrumentId;
  }

  public final void setInstrumentId(final int instrumentId) {
    this.instrumentId = instrumentId;
  }

  public final long getQuantityLong() {
    return quantityLong;
  }

  public final void setQuantityLong(final long quantityLong) {
    this.quantityLong = quantityLong;
  }

  public final long getQuantityShort() {
    return quantityShort;
  }

  public final void setQuantityShort(final long quantityShort) {
    this.quantityShort = quantityShort;
  }

  public final long getCountLong() {
    return countLong;
  }

  public final void setCountLong(final long countLong) {
    this.countLong = countLong;
  }

  public final long getCountShort() {
    return countShort;
  }

  public final void setCountShort(final long countShort) {
    this.countShort = countShort;
  }

  public final void addLong(final long quantity) {
    this.countLong++;
    this.quantityLong += quantity;
  }

  public final void addShort(final long quantity) {
    this.countShort++;
    this.quantityShort += quantity;
  }
}
