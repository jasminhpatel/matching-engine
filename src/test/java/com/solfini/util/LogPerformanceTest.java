package com.solfini.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogPerformanceTest {
  public static void main(String[] args) throws InterruptedException {
    final Logger logger = LoggerFactory.getLogger(LogPerformanceTest.class);
    final long count = 1_000_000;

    System.out.println("Starting");
    long start = System.nanoTime();
    for (long i = 0; i < count; i++) {
      logger.info("Logging performance test - log line " + i);
    }

    long elapsed = System.nanoTime() - start;
    System.out.println("Done in " + elapsed + " ns (" + (elapsed / count) + " ns per call)");
    System.exit(0);
  }
}
