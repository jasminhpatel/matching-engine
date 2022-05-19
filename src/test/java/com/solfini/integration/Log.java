package com.solfini.integration;

public class Log {

  private static boolean debugEnabled = false;

  public static void enableDebug() {
    debugEnabled = true;
  }

  public static void info(final String message) {
    System.out.println(message);
  }

  public static void debug(final String message) {
    if (debugEnabled) {
      System.out.println(message);
    }
  }
}
