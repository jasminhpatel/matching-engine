package com.solfini.instrument;

/**
 * 
 * @author Chris Mack
 *
 */
public class FeeCalcPercent implements FeeCalc {
  private final int fee;

  public FeeCalcPercent(final int fee) {
    this.fee = fee;
  }

  // 100 bps
  public final long calc(final long quantity) {
    return (quantity * fee) / 1000000;
  }

  @Override
  // 100 bps
  public final double calcUsdFee(final double notionalUsd) {
    return (notionalUsd * fee) * .000001;
  }
}
