package com.solfini.instrument;

/**
 * 
 * @author Chris Mack
 *
 */
public class FeeCalcAbsolute implements FeeCalc {
  private final int fee;

  public FeeCalcAbsolute(final int fee) {
    this.fee = fee;
  }

  // 100 bps
  public final long calc(final long quantity) {
    return (fee) / 1000000;
  }

  @Override
  // 100 bps
  public final double calcUsdFee(final double notionalUsd) {
    return (fee) * .000001;
  }
}
