package com.solfini.util;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.event.Level;

public class LogTest {

  @Test
  public void logLevelWarn() {
    LogLevel.setLevel(Level.WARN);
    Assert.assertTrue(LogLevel.warn());
    Assert.assertFalse(LogLevel.info());
    Assert.assertFalse(LogLevel.debug());
    Assert.assertFalse(LogLevel.trace());
  }

  @Test
  public void logLevelInfo() {
    LogLevel.setLevel(Level.INFO);
    Assert.assertTrue(LogLevel.warn());
    Assert.assertTrue(LogLevel.info());
    Assert.assertFalse(LogLevel.debug());
    Assert.assertFalse(LogLevel.trace());
  }

  @Test
  public void logLevelDebug() {
    LogLevel.setLevel(Level.DEBUG);
    Assert.assertTrue(LogLevel.warn());
    Assert.assertTrue(LogLevel.info());
    Assert.assertTrue(LogLevel.debug());
    Assert.assertFalse(LogLevel.trace());
  }

  @Test
  public void logLevelTrace() {
    LogLevel.setLevel(Level.TRACE);
    Assert.assertTrue(LogLevel.warn());
    Assert.assertTrue(LogLevel.info());
    Assert.assertTrue(LogLevel.debug());
    Assert.assertTrue(LogLevel.trace());
  }

  public static void main(String[] args) {
    System.out.println("STDOUT: Message written to standard output stream");
    System.err.println("STDERR: Message written to standard error stream");

    Logger logger = LoggerFactory.getLogger(LogTest.class);
    logger.error("LOG: This is a error log message");
    logger.warn("LOG: This is a warn log message");
    logger.info("LOG: This is a info log message");
    logger.debug("LOG: This is a debug log message");
  }
}
