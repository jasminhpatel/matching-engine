package com.solfini.util;

/**
 *
 * @author Chris Mack
 *
 */
public class TimeUtil {

  private TimeUtil() {
    // hidden default constructor
  }

  public static final long getTime() {
    return System.currentTimeMillis() * 1_000_000 + (System.nanoTime() % 1_000_000);
  }
}
