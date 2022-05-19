package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.matchengine.drmode.message.DRCancelOrder;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class DRCancelOrderObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(DRCancelOrderObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("CANCEL_ORDER_POOL_QUEUE_CAPACITY", 2097152);
  private static final int START_CAPACITY = PropertyReader.getProperty("CANCEL_ORDER_POOL_START_CAPACITY", 1048576);
  private static final ManyToManyConcurrentArrayQueueCustom<DRCancelOrder> pool =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "DRCancelOrderObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(DRCancelOrderObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private DRCancelOrderObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new DRCancelOrder());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final DRCancelOrder get() {
    final DRCancelOrder cancelOrder = pool.poll();
    if (cancelOrder != null) {
      benchmark.hit();
      cancelOrder.resetMarkAsReturned(); // TODO: remove after testing
      return cancelOrder;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new DRCancelOrder");
    }
    benchmark.miss();

    return new DRCancelOrder();
  }

  public static final void returnObject(final DRCancelOrder cancelOrder) {
    try {
      if (cancelOrder != null) {
        if (Context.isUseOrderPoolEnabled()) {
          cancelOrder.clear();
          pool.offer(cancelOrder);
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
