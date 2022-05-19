package com.solfini.instrument;

/**
 * 
 * @author Chris Mack
 *
 */
public interface FeeCalc {
  public long calc(final long quantity);

  public double calcUsdFee(final double notionalUsd);
}
