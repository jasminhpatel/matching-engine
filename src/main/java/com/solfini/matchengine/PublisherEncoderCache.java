package com.solfini.matchengine;

import java.util.concurrent.atomic.AtomicInteger;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.IdleStrategyFactory;

/**
 *
 * @author Chris Mack
 *
 */
public class PublisherEncoderCache implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PublisherEncoderCache.class);

  private static final AtomicInteger publishEncoderRoundRobinLock = new AtomicInteger();
  private static final PublisherEncoderThread[] publisherEncoderThreadArr = buildPublisherEncoderThreads();

  private PublisherEncoderCache() {
    // hidden default constructor
  }

  private static final PublisherEncoderThread[] buildPublisherEncoderThreads() {
    try {
      if (Context.getEncoderThreads() == 0) {
        return null;
      }
      final PublisherEncoderThread[] arr = new PublisherEncoderThread[Context.getEncoderThreads()]; // default to 8
      for (int i = 0; i < arr.length; i++) {
        arr[i] = new PublisherEncoderThread(IdleStrategyFactory.create(Context.getPublisherThreadIdle()), i, publishEncoderRoundRobinLock);
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_2, "starting publisherEncoderThread", i);
        }
        new Thread(arr[i], "publisherEncoderThread" + i).start();
      }
      return arr;
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }

  // blockWaitGetLock, block wait until id is this threads turn
  public static final void blockWaitGetLock() {
    if (null == publisherEncoderThreadArr) {
      return;
    }

    long t0 = System.currentTimeMillis();
    final long startTime = t0;
    final String name = Thread.currentThread().getName();

    // a bit of a hack but works fast, compares the last digit of the thread name to the lock
    final int lockId = ((int) name.charAt(name.length() - 1)) - 48;
    while (lockId != publishEncoderRoundRobinLock.get()) {
      // spin
      final long now = System.currentTimeMillis();
      final long waitTime = now - t0;

      if (waitTime > 1000) {
        final long totalWaitTime = now - startTime;
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn(LOG_FMT_6, "Warning, blocking in blockWaitGetLock(), waitTime=", totalWaitTime, ROUNDROBINLOCKID_EQ,
              publishEncoderRoundRobinLock.get(), " this.thread=", Thread.currentThread().getName());
        }
        t0 = System.currentTimeMillis();
        final PublisherEncoderThread[] arr = PublisherEncoderCache.getPublisherEncoderThreadArr();
        for (int i = 0; i < arr.length; i++) {
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(LOG_FMT_6, i, '=', arr[i].getLockId(), COMA, arr[i].toString());
          }
        }
      }

    }
  }

  public static final AtomicInteger getPublishEncoderRoundRobinLock() {
    return publishEncoderRoundRobinLock;
  }

  public static final PublisherEncoderThread[] getPublisherEncoderThreadArr() {
    return publisherEncoderThreadArr;
  }
}
