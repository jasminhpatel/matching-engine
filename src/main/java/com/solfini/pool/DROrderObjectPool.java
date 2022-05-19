package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.matchengine.drmode.message.DROrder;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class DROrderObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(DROrderObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("ORDER_POOL_QUEUE_CAPACITY", 8_388_608);
  private static final int START_CAPACITY = PropertyReader.getProperty("ORDER_POOL_START_CAPACITY", 4_194_304);
  private static final ManyToManyConcurrentArrayQueueCustom<DROrder> pool =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "DROrderObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(DROrderObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private DROrderObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();
    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new DROrder());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final DROrder get() {
    final DROrder order = pool.poll();
    if (order != null) {
      benchmark.hit();
      order.resetMarkAsReturned(); // TODO: remove after testing
      return order;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new Order");
    }
    benchmark.miss();

    return new DROrder();
  }

  public static final void returnObject(final DROrder order) {
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
