package com.solfini.pool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.solfini.common.Constants;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.common.ReusableLog;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class ReusableLogPool implements Constants {
  // must use regular logger since its pooling the custom logging
  private static final Logger LOGGER = LogManager.getLogger(ReusableLogPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("LOG_POOL_QUEUE_CAPACITY", 2_097_152);
  private static final int START_CAPACITY = PropertyReader.getProperty("LOG_POOL_START_CAPACITY", 1_048_576);
  private static final ManyToManyConcurrentArrayQueueCustom<ReusableLog> poolQueue_128 =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "ReusableLogPool_128");
  private static final ManyToManyConcurrentArrayQueueCustom<ReusableLog> poolQueue_512 =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "ReusableLogPool_512");
  private static final ManyToManyConcurrentArrayQueueCustom<ReusableLog> poolQueue_1024 =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "ReusableLogPool_1024");
  private static final ManyToManyConcurrentArrayQueueCustom<ReusableLog> poolQueue_large =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "ReusableLogPool_large");

  private static final PoolBenchmark benchmark = new PoolBenchmark(ReusableLogPool.class, START_CAPACITY);
  static {
    init();
  }

  private ReusableLogPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();
    for (int i = 0; i < START_CAPACITY / 4; i++) {
      poolQueue_128.offer(new ReusableLog(POOL_128));
      poolQueue_512.offer(new ReusableLog(POOL_512));
      poolQueue_1024.offer(new ReusableLog(POOL_1024));
      poolQueue_large.offer(new ReusableLog(POOL_LARGE));
    }

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final ManyToManyConcurrentArrayQueueCustom<ReusableLog> lookupPool(final int poolId) {
    switch (poolId) {
      case POOL_128:
        return poolQueue_128;
      case POOL_512:
        return poolQueue_512;
      case POOL_1024:
        return poolQueue_1024;
      case POOL_LARGE:
        return poolQueue_large;
      default:
        return poolQueue_large;
    }
  }

  public static final ReusableLog get(final int poolId) {
    final ManyToManyConcurrentArrayQueueCustom<ReusableLog> pool = lookupPool(poolId);
    final ReusableLog reusableLog = pool.poll();
    if (reusableLog != null) {
      benchmark.hit();
      return reusableLog;
    }

    // if (LOGGER.isDebugEnabled()) {
    //   LOGGER.debug("pool creating new ReusableLog");
    // }
    benchmark.miss();
    return new ReusableLog(poolId);
  }

  public static final void returnObject(final ReusableLog reusableLog) {
    try {
      if (reusableLog != null) {
        final ManyToManyConcurrentArrayQueueCustom<ReusableLog> pool = lookupPool(reusableLog.getPoolId());

        pool.offer(reusableLog);
        benchmark.remit();
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public static final int getSize(final int poolId) {
    final ManyToManyConcurrentArrayQueueCustom<ReusableLog> pool = lookupPool(poolId);
    return pool.size();
  }

  public static final int getCapacity(final int poolId) {
    final ManyToManyConcurrentArrayQueueCustom<ReusableLog> pool = lookupPool(poolId);
    return pool.capacity();
  }
}
