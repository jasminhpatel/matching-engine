package com.solfini.common;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.ControllableIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;

/**
 * 
 * @author Chris Mack
 *
 */
public class IdleStrategyFactory {
  private static final String BACKOFFIDLESTRATEGY = "BackoffIdleStrategy";
  private static final String BUSYSPINIDLESTRATEGY = "BusySpinIdleStrategy";
  private static final String CONTROLLABLEIDLESTRATEGY = "ControllableIdleStrategy";
  private static final String NOOPIDLESTRATEGY = "NoOpIdleStrategy";
  private static final String SLEEPINGIDLESTRATEGY = "SleepingIdleStrategy";
  private static final String SLEEPINGMILLISIDLESTRATEGY = "SleepingMillisIdleStrategy";
  private static final String YIELDINGIDLESTRATEGY = "YieldingIdleStrategy";

  private IdleStrategyFactory() {
    // hidden default constructor
  }

  public static final IdleStrategy create(final String name) {
    switch (name) {
      case BACKOFFIDLESTRATEGY:
        return new BackoffIdleStrategy(1, 1, 1, 1);
      case BUSYSPINIDLESTRATEGY:
        return new BusySpinIdleStrategy();
      case CONTROLLABLEIDLESTRATEGY:
        return new ControllableIdleStrategy(null);
      case NOOPIDLESTRATEGY:
        return new NoOpIdleStrategy();
      case SLEEPINGIDLESTRATEGY:
        return new SleepingIdleStrategy(1);
      case SLEEPINGMILLISIDLESTRATEGY:
        return new SleepingMillisIdleStrategy(1);
      case YIELDINGIDLESTRATEGY:
        return new YieldingIdleStrategy();
      default:
        return new NoOpIdleStrategy();
    }
  }
}
