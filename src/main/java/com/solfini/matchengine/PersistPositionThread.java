package com.solfini.matchengine;

import org.agrona.concurrent.IdleStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CountDownLatch;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.matchengine.persist.PersisterPosition;
import com.solfini.pool.PersistPositionReportObjectPool;
import com.solfini.util.FastArrayList;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.RateBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class PersistPositionThread implements Runnable, Constants {
  private static final Logger LOGGER = LogManager.getLogger(PersistPositionThread.class);

  private final ManyToOneConcurrentArrayQueueCustom<PositionReportMessage> persisterPositionQueue;
  private final IdleStrategy idleStrategy;
  private final FastArrayList<PositionReportMessage> list = new FastArrayList<>(4096);
  private final RateBenchmark benchmark = new RateBenchmark("PersistThread");

  private final CountDownLatch stopLatch = new CountDownLatch(1);
  private volatile boolean stop = false;

  public PersistPositionThread(final IdleStrategy idleStrategy) {
    this.persisterPositionQueue = Context.getPublisherToPersisterPositionQueue();
    this.idleStrategy = idleStrategy;
  }

  public void shutdown() throws InterruptedException {
    stop = true;
    stopLatch.await();
  }

  public void run() {
    LOGGER.info("Run persist position thread.");
    while (true) {
      try {
        final int count = persisterPositionQueue.drainTo(list, 4096);
        if (count > 0) {
          boolean ok = PersisterPosition.startBatch();
          if (ok) {
            for (int i = 0; i < count; i++) {
              final Message message = list.get(i);
              if (message != null) {
                if (LOGGER.isTraceEnabled()) {
                  LOGGER.trace(LOG_FMT_4, ONPERSIST_MESSAGE_EQ, i, MESSAGE_EQ, message);
                }

                final long inputTime = message.getInputTime();
                final long decodedTime = message.getDecodedTime();
                final long matchTime = message.getMatchTime();

                message.onPersist();

                final long publishTime = TimeUtil.getTime();
                PublisherEncoderThread.updateBenchmark(inputTime, decodedTime, matchTime, publishTime);

              }
              benchmark.sample();
            }

            ok = PersisterPosition.commitBatch();
          }

          if (!ok) {
            for (int i = 0; i < count; i++) {
              final Message message = list.get(i);
              LOGGER.warn(LOG_FMT_2, "Failed to persist message: ", message);
            }
          }
        }

        for (int i = 0; i < count; i++) {
          final PositionReportMessage message = list.get(i);
          PersistPositionReportObjectPool.returnObject((PositionReportMessage) message);
        }

        list.clear();
        idleStrategy.idle();

        if ((count == 0) && stop) {
          stopLatch.countDown();
          LOGGER.info(LOG_FMT_1, "Shutting down PersistPositionThread");
          return;
        }

      } catch (Exception e) {
        LOGGER.error("ERROR: ", e);
      }
    }
  }

}
