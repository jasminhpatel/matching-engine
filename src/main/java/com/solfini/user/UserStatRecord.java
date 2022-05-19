package com.solfini.user;

import java.util.Arrays;

import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;

public class UserStatRecord {

  private final int userId;

  private double makerVolume = 0;
  private double takerVolume = 0;
  private double effectiveVolume = 0;
  private long makerFillCount = 0;
  private long takerFillCount = 0;
  private long orderCount = 0;
  private long cancelCount = 0;
  private long bestOrderCount0 = 0;
  private long bestOrderCount20 = 0;
  private long bestOrderCount50 = 0;
  private long openOrderCount = 0;
  private double realizedPnl = 0d;
  private double unrealizedPnl = 0d;
  private long[] fees = new long[256];

  public UserStatRecord(final int userId) {
    this.userId = userId;
  }

  public void reset() {
    makerVolume = 0;
    takerVolume = 0;
    makerFillCount = 0;
    takerFillCount = 0;
    orderCount = 0;
    cancelCount = 0;
    bestOrderCount0 = 0;
    bestOrderCount20 = 0;
    bestOrderCount50 = 0;
    for (int i = 0; i < fees.length; i++) {
      fees[i] = 0;
    }
  }

  public final int getUserId() {
    return userId;
  }

  public final double getMakerVolume() {
    return makerVolume;
  }

  public final double getTakerVolume() {
    return takerVolume;
  }

  public final double getEffectiveVolume() {
    return effectiveVolume;
  }

  public final long getMakerFillCount() {
    return makerFillCount;
  }

  public final long getTakerFillCount() {
    return takerFillCount;
  }

  public final long getOrderCount() {
    return orderCount;
  }

  public final long getCancelCount() {
    return cancelCount;
  }

  public final long getBestOrderCount0() {
    return bestOrderCount0;
  }

  public final long getBestOrderCount20() {
    return bestOrderCount20;
  }

  public final long getBestOrderCount50() {
    return bestOrderCount50;
  }

  public final long getOpenOrderCount() {
    return openOrderCount;
  }

  public final double getRealizedPnl() {
    return realizedPnl;
  }

  public final double getUnrealizedPnl() {
    return unrealizedPnl;
  }

  public final long[] getFees() {
    return fees;
  }

  public final void setOpenOrderCount(final long openOrderCount) {
    this.openOrderCount = openOrderCount;
  }

  public final void addMakerTrade(final double notional, final double effective) {
    makerFillCount++;
    makerVolume += notional;
    effectiveVolume += effective;
  }

  public final void addTakerTrade(final double notional, final double effective) {
    takerFillCount++;
    takerVolume += notional;
    effectiveVolume += effective;
  }

  public final void addOrder(final double price, final double bestPx) {
    orderCount++;
    if (bestPx != 0) {
      final double bips = (10000 * Math.abs(price - bestPx)) / bestPx;
      if (bips < 1) {
        bestOrderCount0++;
      } else if (bips <= 20) {
        bestOrderCount20++;
      } else if (bips <= 50) {
        bestOrderCount50++;
      }
    }
  }

  public final void addCancelOrder() {
    cancelCount++;
  }

  public final void addFees(final int instrumentId, final long amount) {
    if (instrumentId >= fees.length) {
        fees = Arrays.copyOf(fees, instrumentId + 16);
      }

    fees[instrumentId] += amount;
  }

  private static String printFees(final long[] fees) {
    String result = "[";
    boolean first = true;
    for (int i = 0; i < fees.length; i++) {
      if (fees[i] != 0) {
        result += (first ? "" : ", ") + i + ":" + fees[i];
        first = false;
      }
    }

    return result + "]";
  }

  public final void update(final Position[] positions) {
    realizedPnl = 0d;
    unrealizedPnl = 0d;
    for (final Position position : positions) {
      if (position != null) {
        realizedPnl += position.getUsdRealizedDouble();
        unrealizedPnl += position.getUsdUnrealized();
      }
    }
  }

  @Override
  public String toString() {
    return "Record(userId=" + userId
        + ", orderCount=" + orderCount + ", cancelCount=" + cancelCount
        + ", makerFillCount=" + makerFillCount + ", takerFillCount=" + takerFillCount
        + ", makerVolume=" + makerVolume + ", takerVolume=" + takerVolume
        + ", bestOrderCount0bps=" + bestOrderCount0
        + ", bestOrderCount20bps=" + bestOrderCount20
        + ", bestOrderCount50bps=" + bestOrderCount50
        + ", fees=" + printFees(fees)
        + ")";
  }
}
