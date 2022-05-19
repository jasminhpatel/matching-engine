package com.solfini.matchengine.drmode;

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
public class DecoderThreadCache implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(DecoderThreadCache.class);

  private static final AtomicInteger decoderRoundRobinLock = new AtomicInteger();
  private static boolean running = false;
  private static final DecoderThread[] decoderThreadArr = buildDecoderThreads();

  public static final AtomicInteger counter = new AtomicInteger();

  private DecoderThreadCache() {
    // hidden default constructor
  }

  private static final DecoderThread[] buildDecoderThreads() {
    try {
      if (Context.getDecoderThreads() == 0) {
        return null;
      }
      final DecoderThread[] arr = new DecoderThread[Context.getDecoderThreads()]; // default to 8
      for (int i = 0; i < arr.length; i++) {
        arr[i] = new DecoderThread(IdleStrategyFactory.create(Context.getPublisherThreadIdle()), i, decoderRoundRobinLock);
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_2, "starting decoderThread", i);
        }
        new Thread(arr[i], "decoderThread" + i).start();
      }
      running = true;
      return arr;
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }

  public static final void start() {
    if (!running) {
      for (int i = 0; i < decoderThreadArr.length; i++) {
        new Thread(decoderThreadArr[i], "decoderThread" + i).start();
      }

      running = true;
    }
  }

  public static final void shutdown() {
    if (running) {
      for (int i = 0; i < decoderThreadArr.length; i++) {
        try {
          LOGGER.info(LOG_FMT_2, "Stopping decoderThread", i);
          decoderThreadArr[i].stopThread();
          LOGGER.info(LOG_FMT_2, "Stopped decoderThread", i);
        } catch (InterruptedException e) {
          LOGGER.info(LOG_FMT_2, "Stopping decoderThread", i, " interrupted");
        }
      }

      running = false;
    }
  }

  // blockWaitGetLock, block wait until id is this threads turn
  public static final void blockWaitGetLock() {
    if (null == decoderThreadArr) {
      return;
    }

    long t0 = System.currentTimeMillis();
    final long startTime = t0;
    final String name = Thread.currentThread().getName();

    // a bit of a hack but works fast, compares the last digit of the thread name to the lock
    final int lockId = ((int) name.charAt(name.length() - 1)) - 48;
    while (lockId != decoderRoundRobinLock.get()) {
      // spin
      final long now = System.currentTimeMillis();
      final long waitTime = now - t0;

      if (waitTime > 1000) {
        final long totalWaitTime = now - startTime;
        LOGGER.error(LOG_FMT_6, "Error, blocking in blockWaitGetLock(), waitTime=", totalWaitTime, ", roundRobinLockId=",
            decoderRoundRobinLock.get(), " this.thread=", Thread.currentThread().getName());

        t0 = System.currentTimeMillis();
        final DecoderThread[] arr = DecoderThreadCache.getDecoderThreadArr();
        for (int i = 0; i < arr.length; i++) {
          LOGGER.error(String.format("%d=%d, %s", i, arr[i].getLockId(), arr[i]));
        }
      }

    }
  }

  public static final AtomicInteger getDecoderRoundRobinLock() {
    return decoderRoundRobinLock;
  }

  public static final DecoderThread[] getDecoderThreadArr() {
    return decoderThreadArr;
  }

  private static int roundRobinId = 0;

  // adds RecieverData to the thread queue
  public static final void add(final RecieverData recieverData) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, "add recieverData to thread ", roundRobinId, ", recieverData=", recieverData);
    }

    decoderThreadArr[roundRobinId].add(recieverData);
    roundRobinId++;
    if (roundRobinId >= decoderThreadArr.length)
      roundRobinId = 0;
  }
}
