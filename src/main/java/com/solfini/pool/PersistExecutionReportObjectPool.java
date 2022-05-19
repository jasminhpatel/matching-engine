package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class PersistExecutionReportObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PersistExecutionReportObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("PERSIST_EXECUTION_REPORT_POOL_QUEUE_CAPACITY", 2_097_152);
  private static final int START_CAPACITY = PropertyReader.getProperty("PERSIST_EXECUTION_REPORT_POOL_START_CAPACITY", 1_048_576);
  private static final ManyToManyConcurrentArrayQueueCustom<ExecutionReportMessage> pool =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "PersistExecutionReportObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(PersistExecutionReportObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private PersistExecutionReportObjectPool() {
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
      executionReportMessage.clear();
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
        pool.offer(executionReportMessage);
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
