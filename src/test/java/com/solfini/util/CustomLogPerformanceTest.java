package com.solfini.util;

import org.slf4j.Logger;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.ReusableLog;
import com.solfini.matchengine.LoggingThread;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;

public class CustomLogPerformanceTest implements Constants {
  private static final String CLASS_NAME = LogPerformanceTest.class.getName();
  private static final ManyToOneConcurrentArrayQueueCustom<ReusableLog> loggingQueue = LoggingThread.getLoggingQueue();

  public static void main(String[] args) throws InterruptedException {
    final long count = 10_000_000;

    // uses old logger with performance issue
    Logger logger1 = CustomLogger.getLogger(LogPerformanceTest.class);
    logger1.debug("test");
    logger1.debug(LOG_FMT_1, "test1");
    logger1.debug(LOG_FMT_2, "test1", 5.5);
    logger1.debug(LOG_FMT_2, new BalanceAdminMessage(), new BalanceAdminMessage());

    // uses better logger
    CustomLogger logger2 = CustomLogger.getLogger(LogPerformanceTest.class);
    logger2.debug("test");
    logger2.debug(LOG_FMT_1, "test1");
    logger2.debug(LOG_FMT_2, "test1", 5.5);
    logger2.debug(LOG_FMT_2, new BalanceAdminMessage(), new BalanceAdminMessage());
    logger2.debug(LOG_FMT_2, new BalanceAdminMessage(), new BalanceAdminMessage(), new BalanceAdminMessage());

  }
}
