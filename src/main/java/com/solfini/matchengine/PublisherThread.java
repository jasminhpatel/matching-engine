package com.solfini.matchengine;

import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.copytrade.CopyTradeOrder;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.pool.ExecutionReportObjectPool;
import org.agrona.concurrent.IdleStrategy;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.util.FastArrayList;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.RateBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class PublisherThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PublisherThread.class);

  private final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue;
  private final IdleStrategy idleStrategy;
  private final FastArrayList<Message> list = new FastArrayList<>(4096);
  private final PublisherEncoderThread[] arr;
  private final RateBenchmark benchmark = new RateBenchmark("PublisherThread");
  private final ManyToOneConcurrentArrayQueueCustom<com.solfini.common.Message> persisterQueue = Context.getPublisherToPersisterQueue();
  private final ManyToOneConcurrentArrayQueueCustom<PositionReportMessage> persisterPositionQueue =
      Context.getPublisherToPersisterPositionQueue();


  public PublisherThread(final IdleStrategy idleStrategy) {
    this.matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
    this.idleStrategy = idleStrategy;
    this.arr = PublisherEncoderCache.getPublisherEncoderThreadArr();
  }

  public void run() {
    int roundRobinId = 0;
    while (true) {
      try {
        int count = matcherToPublisherQueue.drainTo(list, 4096);
        for (int i = 0; i < count; i++) {
          Message message = list.get(i);
          if (message != null && !(message instanceof CopyTradeOrder)) { // copy trades are not published
            // LOGGER.info(LOG_FMT_2, "PUBLISH: ", message + " KafkaOffset=" + message.getKafkaRecordOffset());
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace(LOG_FMT_4, ONPUBLISH_ROUNDROBINID_EQ, roundRobinId, MESSAGE_EQ, message);
            }

            if (null != arr) {
              if (arr[roundRobinId].getLockId() != roundRobinId) {
                LOGGER.error(LOG_FMT_4, "WTF, arr[roundRobinId].getId()=", arr[roundRobinId].getLockId(), ROUNDROBINID_EQ, roundRobinId);
              }

              arr[roundRobinId].add(message);
              roundRobinId++;
              if (roundRobinId >= arr.length)
                roundRobinId = 0;
            } else {
              final long inputTime = message.getInputTime();
              final long decodedTime = message.getDecodedTime();
              final long matchTime = message.getMatchTime();
              message.onPublish();

              if (Mode.PRIMARY == Context.getControllerMode() && message instanceof ExecutionReportMessage executionReportMessage) {
                ExecutionReportObjectPool.returnObjectIfNotUsed(executionReportMessage);
              }

              final long publishTime = TimeUtil.getTime();
              PublisherEncoderThread.updateBenchmark(inputTime, decodedTime, matchTime, publishTime);
            }

          }
          benchmark.sample();

          // Do not persist when in primary mode. This code is broken as position messages
          // are not persisted, and only execution reports go in to the database.
           if (Context.isPersistModeEnabled() && (message.getSnapId() == 0)) {
            persisterQueue.add(message);
           } else {
             //LOGGER.info("Context.isPersistModeEnabled() " + Context.isPersistModeEnabled());
             //LOGGER.info("message.getSnapId() " + message.getSnapId());
           }
        }

        list.clear();
        idleStrategy.idle();
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }

  public final PublisherEncoderThread[] getEncoderThreadArr() {
    return arr;
  }
}
