package com.solfini.matchengine;

import org.agrona.concurrent.IdleStrategy;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.solfini.common.Constants;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.ReusableLog;
import com.solfini.pool.ReusableLogPool;
import com.solfini.util.FastArrayList;
import com.solfini.util.PropertyReader;
import com.solfini.util.benchmark.RateBenchmark;

/**
 *
 * @author Chris Mack
 *
 */

// Excluding the class
public class LoggingThread implements Runnable, Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingThread.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("QUEUE_CAPACITY", 8_388_608);
  private static final int LOGGING_QUEUE_CAPACITY = PropertyReader.getProperty("LOGGING_QUEUE_CAPACITY", QUEUE_CAPACITY);
  private static final ManyToOneConcurrentArrayQueueCustom<ReusableLog> loggingQueue =
      new ManyToOneConcurrentArrayQueueCustom<>(LOGGING_QUEUE_CAPACITY, "loggingQueue");

  private final IdleStrategy idleStrategy;
  private final FastArrayList<ReusableLog> list = new FastArrayList<>(4096);
  private final RateBenchmark benchmark = new RateBenchmark("LoggingThread");

  public LoggingThread(final IdleStrategy idleStrategy) {
    this.idleStrategy = idleStrategy;
  }

  public static final ManyToOneConcurrentArrayQueueCustom<ReusableLog> getLoggingQueue() {
    return loggingQueue;
  }

  public void run() {
    LOGGER.info("LoggingThread started");
    while (true) {
      try {
        // regular messages
        final int count = loggingQueue.drainTo(list, 4096);
        for (int i = 0; i < count; i++) {
          final ReusableLog reusableLog = list.get(i);
          if (reusableLog != null) {
            reusableLog.log();
            ReusableLogPool.returnObject(reusableLog);
            benchmark.sample();
          }
        }

        list.clear();
        idleStrategy.idle();
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }
}
