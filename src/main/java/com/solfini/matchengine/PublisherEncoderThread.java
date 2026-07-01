package com.solfini.matchengine;

import com.solfini.common.MessageType;
import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.kafka.KafkaListener;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.util.benchmark.RateBenchmark;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.concurrent.IdleStrategy;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.LatencyDistributionBenchmark;
import com.solfini.util.benchmark.LatencyLogSwitcher;

/**
 *
 * @author Chris Mack
 *
 */

public class PublisherEncoderThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PublisherEncoderThread.class);

  private static final LatencyDistributionBenchmark[] benchmarks = buildLatencyDistributionBenchmarks();
  private static final RateBenchmark rateBenchmark = new RateBenchmark("Publisher BM Rate:");
  private static final LatencyDistributionBenchmark latencyBenchmark = new LatencyDistributionBenchmark("Publisher BM Latency:");
  private final OneToOneConcurrentArrayQueueCustom<Message> encoderQueue;

  private final IdleStrategy idleStrategy;
  private final int lockId;
  private final int nextId;
  private final AtomicInteger roundRobinLock;
  private final int NUM_ENCODER_THREADS = Context.getEncoderThreads();
  private static final LatencyLogSwitcher latencyLogSwitcher = new LatencyLogSwitcher();

  private static final LatencyDistributionBenchmark[] buildLatencyDistributionBenchmarks() {
    final LatencyDistributionBenchmark[] benchmarks = new LatencyDistributionBenchmark[4];
    benchmarks[0] = new LatencyDistributionBenchmark("Latency");
    benchmarks[1] = new LatencyDistributionBenchmark("Latency (decode)");
    benchmarks[2] = new LatencyDistributionBenchmark("Latency (matching)");
    benchmarks[3] = new LatencyDistributionBenchmark("Latency (publish)");

    return benchmarks;
  }

  public PublisherEncoderThread(final IdleStrategy idleStrategy, final int lockId, final AtomicInteger roundRobinLock) {
    this.encoderQueue = new OneToOneConcurrentArrayQueueCustom<>(Context.getQueueCapacity(), "PublisherEncoderThread" + lockId);
    this.idleStrategy = idleStrategy;
    this.lockId = lockId;
    this.roundRobinLock = roundRobinLock;
    this.nextId = (lockId + 1 >= NUM_ENCODER_THREADS) ? 0 : lockId + 1;
  }

  public final int getLockId() {
    return this.lockId;
  }

  public final int getNextId() {
    return this.nextId;
  }

  public final AtomicInteger getRoundRobinLock() {
    return this.roundRobinLock;
  }

  // called by publisher thread
  public boolean add(final Message message) {
    return encoderQueue.addGuaranteed(message);
  }

  public static void updateBenchmark(final long inputTime, final long decodedTime, final long matchTime, final long publishTime) {
    if (inputTime > 0) {
      benchmarks[0].sample(publishTime - inputTime);
      benchmarks[1].sample(decodedTime - inputTime);
      benchmarks[2].sample(matchTime - decodedTime);
      benchmarks[3].sample(publishTime - matchTime);

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_16, "published latency inputTime=", inputTime, DECODEDTIME_EQ, decodedTime, MATCHTIME_EQ, matchTime,
            ", publishTime=", publishTime, ", totaldiff= ", publishTime - inputTime, ", diff1= ", decodedTime - inputTime, ", diff2= ",
            matchTime - decodedTime, ", diff3= ", publishTime - matchTime);
      }

      if (benchmarks[0].getTotalSampleCount() >= 100_000) {
        benchmarks[0].resetTotalSamplesCount();

        final double averageLatency = benchmarks[0].getAverage();

        // Check if the logging status needs to be changed.
        latencyLogSwitcher.update(averageLatency);
      }
    }
  }

  public void run() {
    while (true) {
      try {
        final Message message = encoderQueue.poll();

        if (message != null) {
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(LOG_FMT_16, "encoder onPublish lockId=", this.lockId, NEXTID_EQ, nextId, MESSAGE_EQ, message);
          }

          try {
            boolean isExecution = message.getMessageType() == MessageType.EXECUTION_REPORT;
            final long inputTime = message.getInputTime();
            final long decodedTime = message.getDecodedTime();
            final long matchTime = message.getMatchTime();
            message.onPublish();

            final long publishTime = TimeUtil.getTime();
            updateBenchmark(inputTime, decodedTime, matchTime, publishTime);
            if (isExecution && inputTime > 0 && message.getKafkaRecordOffset() > KafkaListener.FIRST_MESSAGE_OFFSET) {
              rateBenchmark.sample();
              //latencyBenchmark.sample(publishTime - inputTime);
            }
          } catch (Exception e) {
            try {
              LOGGER.error(message.toJSON());
            } catch (Exception ex) {}
            LOGGER.error(ERROR_LOG, e);


            // Acquire the round robin lock in case it has not been done due to the exception.
            // This being reentrant makes sure that we dont break the round robin cycle and cause out of order messages.
            PublisherEncoderCache.blockWaitGetLock();
          }
          boolean rc = roundRobinLock.compareAndSet(this.lockId, nextId); // release lock to next thread
          if (!rc)
            LOGGER.error("error, lock release failed. lockId=" + this.lockId + NEXTID_EQ + nextId + ", roundRobinLockId=" + roundRobinLock
                + MESSAGE_EQ + message);

          if (Mode.PRIMARY == Context.getControllerMode() && message instanceof ExecutionReportMessage executionReportMessage) {
            ExecutionReportObjectPool.returnObjectIfNotUsed(executionReportMessage);
          }
        }

        idleStrategy.idle();
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }

  public final int getSize() {
    return encoderQueue.size();
  }

  public final int getCapacity() {
    return encoderQueue.capacity();
  }
}
