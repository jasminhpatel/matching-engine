package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.instrument.Position;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class ExecutionReportObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExecutionReportObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", 16_777_216);
  private static final int START_CAPACITY = PropertyReader.getProperty("EXECUTION_REPORT_POOL_START_CAPACITY", 8_388_608);
/*  private static final OneToOneConcurrentArrayQueueCustom<ExecutionReportMessage> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "ExecutionReportObjectPool");*/
  private static final ManyToManyConcurrentArrayQueueCustom<ExecutionReportMessage> pool =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "ExecutionReportObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(ExecutionReportObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private ExecutionReportObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new ExecutionReportMessage());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final ExecutionReportMessage get() {
    final ExecutionReportMessage executionReportMessage = pool.poll();
    if (executionReportMessage != null) {
      benchmark.hit();
      executionReportMessage.resetMarkAsReturned(); // TODO: remove after testing
      return executionReportMessage;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new ExecutionReportMessage");
    }
    benchmark.miss();

    return new ExecutionReportMessage();
  }

  public static final void returnObject(final ExecutionReportMessage executionReportMessage) {
    try {
      if (executionReportMessage != null) {

        // Reclaim positions
        final Position[] positions = executionReportMessage.getPositionArr();

        for (int i = 0; i < positions.length; i++) {
          if (positions[i] != null) {
            PositionMatchThreadObjectPool.returnObject(positions[i]);
            positions[i] = null; // remove reference for returned positions
          }
        }

        if (Context.isUseOrderPoolEnabled()) {
          executionReportMessage.clear();
          pool.offer(executionReportMessage);
          benchmark.remit();
        }
        //
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public static void returnObjectIfNotUsed(final ExecutionReportMessage executionReportMessage) {
    try {
      if (executionReportMessage != null) {
        if (executionReportMessage.getConcurrentUsageCount().decrementAndGet() == 0) {
          // Reclaim positions if not claimed
          final Position[] positions = executionReportMessage.getPositionArr();
          if (positions != null) {
            for (int i = 0; i < positions.length; i++) {
              if (positions[i] != null) {
                PositionMatchThreadObjectPool.returnObject(positions[i]);
                positions[i] = null; // remove reference for returned positions
              }
            }
          }

          executionReportMessage.clear();
          pool.offer(executionReportMessage);
        }

        benchmark.remit();
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public static final int getSize() {
    return pool.size();
  }

  public static final int getCapacity() {
    return pool.capacity();
  }
}
