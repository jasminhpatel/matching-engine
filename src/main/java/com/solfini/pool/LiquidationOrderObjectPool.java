package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.internal.LiquidationOrder;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class LiquidationOrderObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LiquidationOrderObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("LIQUIDATION_ORDER_POOL_QUEUE_CAPACITY", 2097152);
  private static final int START_CAPACITY = PropertyReader.getProperty("LIQUIDATION_ORDER_POOL_START_CAPACITY", 1048576);
  private static final OneToOneConcurrentArrayQueueCustom<LiquidationOrder> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "LiquidationOrderObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(LiquidationOrderObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private LiquidationOrderObjectPool() {
    // hidden default constructor
  }

  private static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new LiquidationOrder());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final LiquidationOrder get() {
    final LiquidationOrder order = pool.poll();
    if (order != null) {
      benchmark.hit();
      order.resetMarkAsReturned(); // TODO: remove after testing
      return order;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new LiquidationOrder");
    }
    benchmark.miss();

    return new LiquidationOrder();
  }

  public static final void returnObject(final LiquidationOrder order) {
    try {
      if (order != null) {

        if (Context.isUseOrderPoolEnabled()) {
          order.clear();
          pool.offer(order);
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
