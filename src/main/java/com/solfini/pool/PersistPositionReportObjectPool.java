package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class PersistPositionReportObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PersistPositionReportObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("PERSIST_POSITION_REPORT_POOL_QUEUE_CAPACITY", 2_097_152);
  private static final int START_CAPACITY = PropertyReader.getProperty("PERSIST_POSITION_REPORT_POOL_START_CAPACITY", 1_048_576);
  private static final ManyToManyConcurrentArrayQueueCustom<PositionReportMessage> pool =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "PersistPositionReportObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(PersistPositionReportObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private PersistPositionReportObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new PositionReportMessage());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final PositionReportMessage get() {
    final PositionReportMessage positionReportMessage = pool.poll();
    if (positionReportMessage != null) {
      benchmark.hit();
      positionReportMessage.clear();
      positionReportMessage.resetMarkAsReturned(); // TODO: remove after testing
      return positionReportMessage;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new PositionReportMessage");
    }
    benchmark.miss();

    return new PositionReportMessage();
  }

  public static final void returnObject(final PositionReportMessage positionReportMessage) {
    try {
      if (positionReportMessage != null) {
        pool.offer(positionReportMessage);
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
