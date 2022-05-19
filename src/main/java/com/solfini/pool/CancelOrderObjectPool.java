package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class CancelOrderObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(CancelOrderObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("CANCEL_ORDER_POOL_QUEUE_CAPACITY", 2097152);
  private static final int START_CAPACITY = PropertyReader.getProperty("CANCEL_ORDER_POOL_START_CAPACITY", 1048576);
  private static final OneToOneConcurrentArrayQueueCustom<CancelOrder> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "CancelOrderObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(CancelOrderObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private CancelOrderObjectPool() {
    // hidden default constructor
  }

  private static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new CancelOrder());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final CancelOrder get() {
    final CancelOrder cancelOrder = pool.poll();
    if (cancelOrder != null) {
      benchmark.hit();
      cancelOrder.resetMarkAsReturned(); // TODO: remove after testing
      return cancelOrder;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new CancelOrder");
    }
    benchmark.miss();

    return new CancelOrder();
  }

  public static final void returnObject(final CancelOrder cancelOrder) {
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
