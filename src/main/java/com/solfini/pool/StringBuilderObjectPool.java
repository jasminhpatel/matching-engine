package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class StringBuilderObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(StringBuilderObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("STRING_BUILDER_POOL_QUEUE_CAPACITY", 2097152);
  private static final int START_CAPACITY = PropertyReader.getProperty("STRING_BUILDER_POOL_START_CAPACITY", 1048576);
  private static final ManyToManyConcurrentArrayQueueCustom<StringBuilder> pool =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "StringBuilderObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(StringBuilderObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private StringBuilderObjectPool() {
    // hidden default constructor
  }

  private static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new StringBuilder());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final StringBuilder get() {
    final StringBuilder order = pool.poll();
    if (order != null) {
      benchmark.hit();
      return order;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new StringBuilder");
    }
    benchmark.miss();

    return new StringBuilder();
  }

  public static final void returnObject(final StringBuilder stringBuilder) {
    try {
      if (stringBuilder != null) {
        stringBuilder.setLength(0);
        pool.offer(stringBuilder);
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
