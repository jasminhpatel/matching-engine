package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.matchengine.drmode.message.DRExecutionReport;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class DRExecutionReportObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(DRExecutionReportObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", 8_388_608);
  private static final int START_CAPACITY = PropertyReader.getProperty("EXECUTION_REPORT_POOL_START_CAPACITY", 4_194_304);
  private static final ManyToManyConcurrentArrayQueueCustom<DRExecutionReport> pool =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "DRExecutionReportObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(DRExecutionReportObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private DRExecutionReportObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new DRExecutionReport());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final DRExecutionReport get() {
    final DRExecutionReport executionReport = pool.poll();
    if (executionReport != null) {
      benchmark.hit();
      executionReport.resetMarkAsReturned(); // TODO: remove after testing
      return executionReport;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new ExecutionReport");
    }
    benchmark.miss();

    return new DRExecutionReport();
  }

  public static final void returnObject(final DRExecutionReport executionReport) {
    try {
      if (executionReport != null) {
        if (Context.isUseOrderPoolEnabled()) {
          executionReport.clear();
          pool.offer(executionReport);
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
