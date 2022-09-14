package com.solfini.matchengine;

import org.agrona.concurrent.IdleStrategy;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.common.TransactionalOutputManyToOneConcurrentArrayQueue;
import com.solfini.report.StateValidator;
import com.solfini.util.FastArrayList;
import com.solfini.util.benchmark.RateBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class MatchingThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MatchingThread.class);

  private final ManyToOneConcurrentArrayQueueCustom<Message> receiverToMatcherQueue;
  private final ManyToOneConcurrentArrayQueueCustom<Message> riskToMatcherQueue;
  private final OneToOneConcurrentArrayQueueCustom<Message> adminReceiverToMatcherQueue;
  private final IdleStrategy idleStrategy;
  private final FastArrayList<Message> list = new FastArrayList<>(4096);
  private final RateBenchmark benchmark = new RateBenchmark("MatchingThread");
  private final TransactionalOutputManyToOneConcurrentArrayQueue matcherToPublisherQueue;

  public MatchingThread(final IdleStrategy idleStrategy) {
    this.receiverToMatcherQueue = Context.getReceiverToMatcherQueue();
    this.riskToMatcherQueue = Context.getRiskToMatcherQueue();
    this.adminReceiverToMatcherQueue = Context.getAdminReceiverToMatcherQueue();
    this.idleStrategy = idleStrategy;
    this.matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
  }

  public void run() {
    Message message = null;
    while (true) {
      try {

        long t2 = System.currentTimeMillis();
        // regular messages
        int count = receiverToMatcherQueue.drainTo(list, 128);
        for (int i = 0; i < count; i++) {
          message = list.get(i);
          if (message != null) {
            // LOGGER.info(LOG_FMT_2, "MATCH: ", message);
            final long t0 = System.currentTimeMillis();

            matcherToPublisherQueue.beginTransaction();
            try {
              message.onMatcher();
            } catch (Exception e) {
              //todo remove stack trace
              LOGGER.error(Constants.ERROR_LOG, e);
              LOGGER.error(Constants.ERROR_LOG, "ReceiverToMatcherQueue message onMatch error ", e);
            }
            matcherToPublisherQueue.endTransaction();

            if (Context.isStateValidatorEnabled() && !StateValidator.validate(message)) {
              LOGGER.error(LOG_FMT_2, Constants.VALIDATEERROR, message);
            }

            final long t1 = System.currentTimeMillis();
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace(LOG_FMT_6, ONMATCHER_T_EQ, (t1 - t0), MESSAGE_EQ, message, INPUT_OFFSET_EQ, message.getKafkaRecordOffset());
            }
            benchmark.sample();
          }
        }

        if (LOGGER.isTraceEnabled() && (!list.isEmpty())) {
          LOGGER.trace(LOG_FMT_4, ONMATCHER_LIST_EQ, (long) list.size(), COMA, (t2 - System.currentTimeMillis()));
        }
        list.clear();

        t2 = System.currentTimeMillis();
        // bias to process risk messages
        count = riskToMatcherQueue.drainTo(list, 4096);
        for (int i = 0; i < count; i++) {
          message = list.get(i);
          if (message != null) {
            final long t0 = System.currentTimeMillis();

            matcherToPublisherQueue.beginTransaction();
            try {
              message.onMatcher();
            } catch (Exception e) {
              LOGGER.error(Constants.ERROR_LOG,"matcherToPublisherQueue message onMatch error", e);
            }
            matcherToPublisherQueue.endTransaction();

            if (Context.isStateValidatorEnabled() && !StateValidator.validate(message)) {
              LOGGER.error(LOG_FMT_2, Constants.VALIDATEERROR, message);
            }
            final long t1 = System.currentTimeMillis();
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace(LOG_FMT_6, ONMATCHER_T_EQ, (t1 - t0), ",risk message=", message, INPUT_OFFSET_EQ,
                  message.getKafkaRecordOffset());
            }
            benchmark.sample();
          }
        }
        if (LOGGER.isTraceEnabled() && (!list.isEmpty())) {
          LOGGER.trace(LOG_FMT_4, ONMATCHER_LIST_EQ, (long) list.size(), COMA, (t2 - System.currentTimeMillis()));
        }
        list.clear();

        t2 = System.currentTimeMillis();
        // admin messages
        count = adminReceiverToMatcherQueue.drainTo(list, 128);
        for (int i = 0; i < count; i++) {
          message = list.get(i);
          if (message != null) {
            final long t0 = System.currentTimeMillis();

            matcherToPublisherQueue.beginTransaction();
            try {
              message.onMatcher();
            } catch (Exception e) {
              LOGGER.error("admin matcherToPublisherQueue message onMatch error", e);
            }
            matcherToPublisherQueue.endTransaction();

            if (Context.isStateValidatorEnabled() && !StateValidator.validate(message)) {
              LOGGER.error(LOG_FMT_2, Constants.VALIDATEERROR, message);
            }
            final long t1 = System.currentTimeMillis();
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace(LOG_FMT_6, ONMATCHER_T_EQ, (t1 - t0), ",admin message=", message, INPUT_OFFSET_EQ,
                  message.getKafkaRecordOffset());
            }
            benchmark.sample();
          }
        }
        if (LOGGER.isTraceEnabled() && (!list.isEmpty())) {
          LOGGER.trace(LOG_FMT_5, ONMATCHER_LIST_EQ, list.size(), COMA, (t2 - System.currentTimeMillis()), message);
        }
        list.clear();

        idleStrategy.idle();
      } catch (Throwable e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }
}
