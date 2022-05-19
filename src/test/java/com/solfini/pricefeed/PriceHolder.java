package com.solfini.pricefeed;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author Chris Mack
 *
 */
public class PriceHolder {
  private final String symbol;
  private final int sourceId;
  private volatile PriceSize priceSize;
  private final AtomicBoolean inQueue;

  public PriceHolder(final String symbol, final int sourceId) {
    this.symbol = symbol;
    this.sourceId = sourceId;
    this.inQueue = new AtomicBoolean(false);
  }

  public final AtomicBoolean getInQueue() {
    return inQueue;
  }

  public final String getSymbol() {
    return symbol;
  }

  public final int getSourceId() {
    return sourceId;
  }

  public final void set(final double price, final double size) {
    priceSize = new PriceSize(price, size);
  }

  public final PriceSize get() {
    return priceSize;
  }

  @Override
  public String toString() {
    return "PriceHolder [symbol=" + symbol + ", sourceId=" + sourceId + ", priceSize=" + priceSize + "]";
  }


  public static final class PriceSize {
    final double price;
    final double size;

    PriceSize(final double price, final double size) {
      this.price = price;
      this.size = size;
    }

    public final double getPrice() {
      return price;
    }

    public final double getSize() {
      return size;
    }

    @Override
    public String toString() {
      return "PriceSize [price=" + price + ", size=" + size + "]";
    }

  }
}
