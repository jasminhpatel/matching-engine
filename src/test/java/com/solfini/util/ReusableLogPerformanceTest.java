package com.solfini.util;

import org.agrona.concurrent.NoOpIdleStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.solfini.common.Constants;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.ReusableLog;
import com.solfini.matchengine.LoggingThread;
import com.solfini.pool.ReusableLogPool;

public class ReusableLogPerformanceTest implements Constants {
  private static final String CLASS_NAME = LogPerformanceTest.class.getName();
  private static final ManyToOneConcurrentArrayQueueCustom<ReusableLog> loggingQueue = LoggingThread.getLoggingQueue();

  public static void main(String[] args) throws InterruptedException {
    final Logger logger = LogManager.getLogger(LogPerformanceTest.class);
    final long count = 10_000_000;

    new Thread(new LoggingThread(new NoOpIdleStrategy())).start();
    ReusableLogPool.init();

    System.out.println("Starting");
    long start = System.nanoTime();

    for (int i = 0; i < count; i++) {
      loggingQueue
          .addGuaranteed(ReusableLog.get(POOL_1024, CLASS_NAME, ReusableLog.INFO).append("Logging performance test - log line ").append(i));
    }

    long elapsed = System.nanoTime() - start;

    System.out.println(
        ">>> HealthMonitor ReusableLogPool POOL_128: " + ReusableLogPool.getCapacity(POOL_128) + " : " + ReusableLogPool.getSize(POOL_128));
    System.out.println(
        ">>> HealthMonitor ReusableLogPool POOL_512: " + ReusableLogPool.getCapacity(POOL_512) + " : " + ReusableLogPool.getSize(POOL_512));
    System.out.println(">>> HealthMonitor ReusableLogPool POOL_1024: " + ReusableLogPool.getCapacity(POOL_1024) + " : "
        + ReusableLogPool.getSize(POOL_1024));
    System.out.println(">>> HealthMonitor ReusableLogPool POOL_LARGE: " + ReusableLogPool.getCapacity(POOL_LARGE) + " : "
        + ReusableLogPool.getSize(POOL_LARGE));

    System.out.println("Done in " + elapsed + " ns (" + (elapsed / count) + " ns per call)");
    System.exit(0);
  }
}
