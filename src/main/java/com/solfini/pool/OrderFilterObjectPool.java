package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.internal.OrderFilter;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

public class OrderFilterObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(OrderFilterObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("ORDER_FILTER_POOL_QUEUE_CAPACITY", 32768);
  private static final int START_CAPACITY = PropertyReader.getProperty("ORDER_FILTER_POOL_START_CAPACITY", 16384);
  private static final OneToOneConcurrentArrayQueueCustom<OrderFilter> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "OrderFilterObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(OrderFilterObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private OrderFilterObjectPool() {}

  public static void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new OrderFilter());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static OrderFilter get() {
    final OrderFilter orderFilter = pool.poll();
    if (orderFilter != null) {
      benchmark.hit();
      orderFilter.resetMarkAsReturned();
      return orderFilter;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new OrderFilter");
    }
    benchmark.miss();

    return new OrderFilter();
  }

  public static void returnObject(final OrderFilter orderFilter) {
    try {
      if (orderFilter != null) {
        if (Context.isUseOrderPoolEnabled()) {
          orderFilter.clear();
          pool.offer(orderFilter);
        }
        benchmark.remit();
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public static int getSize() {
    return pool.size();
  }

  public static int getCapacity() {
    return pool.capacity();
  }
}
