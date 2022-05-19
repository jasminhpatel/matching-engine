package com.solfini.report.check;

/**
 * Created by Shining.Cai on 2019/07/19.
 **/
public class ReconciliationResult {

  private final int symbolId;
  private long positionSum;
  private double unrealizedPnlSum;
  private double realizedPnlSum;

  public ReconciliationResult(final int symbolId) {
    this.symbolId = symbolId;
  }

  public final int getSymbolId() {
    return symbolId;
  }

  public final long getPositionSum() {
    return positionSum;
  }

  public final void addPositionSum(final long position) {
    this.positionSum += position;
  }

  public final double getUnrealizedPnlSum() {
    return unrealizedPnlSum;
  }

  public final void addUnrealizedPnlSum(final double pnl) {
    this.unrealizedPnlSum += pnl;
  }

  public final double getRealizedPnlSum() {
    return realizedPnlSum;
  }

  public final void addRealizedPnlSum(final double pnl) {
    this.realizedPnlSum += pnl;
  }

  @Override
  public String toString() {
    return "ReconciliationResult [symbolId=" + symbolId + ", positionSum=" + positionSum + ", unrealizedPnlSum=" + unrealizedPnlSum
        + ", realizedPnlSum=" + realizedPnlSum + "]";
  }
}
