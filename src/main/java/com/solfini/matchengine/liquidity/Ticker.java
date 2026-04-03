package com.solfini.matchengine.liquidity;

import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;

public class Ticker {

  private String symbol;
  private int instrumentType;
  private double open;
  private double last;
  private double bid;
  private double ask;
  private double high;
  private double low;
  private double vwap;
  private double volume;
  private double quoteVolume;
  private long timestamp;

  private double bidSize;
  private double askSize;
  private double percentageChange;

  public Ticker() {
  }

  public Ticker(final String symbol, final int instrumentType, final double open, final double last,
      final double bid, final double ask,
      final double high, final double low, final double volume, final double quoteVolume,
      final long timestamp, final double bidSize,
      final double askSize, final double percentageChange) {
    this.symbol = symbol;
    this.instrumentType = instrumentType;
    this.open = open;
    this.last = last;
    this.bid = bid;
    this.ask = ask;
    this.high = high;
    this.low = low;
    this.volume = volume;
    this.quoteVolume = quoteVolume;
    this.timestamp = timestamp;
    this.bidSize = bidSize;
    this.askSize = askSize;
    this.percentageChange = percentageChange;
  }

  public void copyFrom(final Ticker source) {
    if (source == null) {
      return;
    }
    this.symbol = source.symbol;
    this.instrumentType = source.instrumentType;
    this.open = source.open;
    this.last = source.last;
    this.bid = source.bid;
    this.ask = source.ask;
    this.high = source.high;
    this.low = source.low;
    this.vwap = source.vwap;
    this.volume = source.volume;
    this.quoteVolume = source.quoteVolume;
    this.bidSize = source.bidSize;
    this.askSize = source.askSize;
    this.percentageChange = source.percentageChange;

    this.timestamp = source.timestamp; // copy timestamp last
  }

  public double getPrice(final Side side) {
    double price = 0;
    if (side == Side.BUY) {
      if (this.getAsk() > 0)
        price = this.getAsk();
    } else {
      if (this.getBid() > 0)
        price = this.getBid();
    }
    if (price == 0) {
      if (this.getLast() > 0)
        price = this.getLast();
    }
    if (price == 0) {
      if (this.getOpen() > 0)
        price = this.getOpen();
    }
    return MbxMath.roundToBestPrecision(price);
  }

  public final String getSymbol() {
    return symbol;
  }

  public final void setSymbol(final String symbol) {
    this.symbol = symbol;
  }

  public final int getInstrumentType() {
    return instrumentType;
  }

  public final void setInstrumentType(final int instrumentType) {
    this.instrumentType = instrumentType;
  }

  public final double getOpen() {
    return open;
  }

  public final void setOpen(final double open) {
    this.open = open;
  }

  public final double getLast() {
    return last;
  }

  public final void setLast(final double last) {
    this.last = last;
  }

  public final double getBid() {
    return bid;
  }

  public final void setBid(final double bid) {
    this.bid = bid;
  }

  public final double getAsk() {
    return ask;
  }

  public final void setAsk(final double ask) {
    this.ask = ask;
  }

  public final double getHigh() {
    return high;
  }

  public final void setHigh(final double high) {
    this.high = high;
  }

  public final double getLow() {
    return low;
  }

  public final void setLow(final double low) {
    this.low = low;
  }

  public final double getVwap() {
    return vwap;
  }

  public final void setVwap(final double vwap) {
    this.vwap = vwap;
  }

  public final double getVolume() {
    return volume;
  }

  public final void setVolume(final double volume) {
    this.volume = volume;
  }

  public final double getQuoteVolume() {
    return quoteVolume;
  }

  public final void setQuoteVolume(final double quoteVolume) {
    this.quoteVolume = quoteVolume;
  }

  public final long getTimestamp() {
    return timestamp;
  }

  public final void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  public final double getBidSize() {
    return bidSize;
  }

  public final void setBidSize(final double bidSize) {
    this.bidSize = bidSize;
  }

  public final double getAskSize() {
    return askSize;
  }

  public final void setAskSize(final double askSize) {
    this.askSize = askSize;
  }

  public final double getPercentageChange() {
    return percentageChange;
  }

  public final void setPercentageChange(final double percentageChange) {
    this.percentageChange = percentageChange;
  }

  @Override
  public String toString() {
    return "{" +
        "symbol='" + symbol + '\'' +
        ", instrumentType=" + instrumentType +
        ", open=" + open +
        ", last=" + last +
        ", bid=" + bid +
        ", ask=" + ask +
        ", high=" + high +
        ", low=" + low +
        ", vwap=" + vwap +
        ", volume=" + volume +
        ", quoteVolume=" + quoteVolume +
        ", timestamp=" + timestamp +
        ", bidSize=" + bidSize +
        ", askSize=" + askSize +
        ", percentageChange=" + percentageChange +
        '}';
  }
}
