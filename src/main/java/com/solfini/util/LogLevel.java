package com.solfini.util;

import org.slf4j.event.Level;

public class LogLevel {

  private static Level level = Level.INFO;

  private LogLevel() {
    // hidden default constructor
  }

  public static final void setLevel(final Level level) {
    LogLevel.level = level;
  }

  public static final Level getLevel() {
    return level;
  }

  private static final boolean enabled(final Level level) {
    return LogLevel.level.toInt() <= level.toInt();
  }

  public static final boolean error() {
    return enabled(Level.ERROR);
  }

  public static final boolean warn() {
    return enabled(Level.WARN);
  }

  public static final boolean info() {
    return enabled(Level.INFO);
  }

  public static final boolean debug() {
    return enabled(Level.DEBUG);
  }

  public static final boolean trace() {
    return enabled(Level.TRACE);
  }

  // change level after start
  public static final void changeLevelAfterStart(final int level) {
    switch (level) {
      case org.slf4j.event.EventConstants.DEBUG_INT:
        LogLevel.level = Level.DEBUG;
        break;
      case org.slf4j.event.EventConstants.ERROR_INT:
        LogLevel.level = Level.ERROR;
        break;
      case org.slf4j.event.EventConstants.INFO_INT:
        LogLevel.level = Level.INFO;
        break;
      case org.slf4j.event.EventConstants.TRACE_INT:
        LogLevel.level = Level.TRACE;
        break;
      case org.slf4j.event.EventConstants.WARN_INT:
        LogLevel.level = Level.WARN;
        break;
      default: // do nothing
    }
  }

  // change level after start
  public static final void changeLevelAfterStart(final Level level) {
    LogLevel.level = level;
  }
}
