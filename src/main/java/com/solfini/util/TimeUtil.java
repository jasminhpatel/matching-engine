package com.solfini.util;

/**
 *
 * @author Chris Mack
 *
 */
public class TimeUtil {

/*  private TimeUtil() {
    // hidden default constructor
  }

  public static final long getTime() {
    return System.currentTimeMillis() * 1_000_000 + (System.nanoTime() % 1_000_000);
  }*/

  private static final long BASE_MILLIS = System.currentTimeMillis();
  private static final long BASE_NANOS  = System.nanoTime();

  private TimeUtil() {
    // hidden default constructor
  }

  public static long getTime() {
    return BASE_MILLIS * 1_000_000L + (System.nanoTime() - BASE_NANOS);
  }
}
