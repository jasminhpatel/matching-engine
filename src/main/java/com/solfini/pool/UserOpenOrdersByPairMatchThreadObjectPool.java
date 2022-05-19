package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.user.UserOpenOrdersByPair;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class UserOpenOrdersByPairMatchThreadObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(UserOpenOrdersByPairMatchThreadObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", 67_108_864);
  private static final int START_CAPACITY = PropertyReader.getProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", 33_554_432);
  private static final OneToOneConcurrentArrayQueueCustom<UserOpenOrdersByPair> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "UserOpenOrdersByPairMatchThreadObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(UserOpenOrdersByPairMatchThreadObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private UserOpenOrdersByPairMatchThreadObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new UserOpenOrdersByPair());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final UserOpenOrdersByPair get() {
    final UserOpenOrdersByPair userOpenOrdersByPair = pool.poll();
    if (userOpenOrdersByPair != null) {
      benchmark.hit();
      userOpenOrdersByPair.resetMarkAsReturned(); // TODO: remove after testing
      return userOpenOrdersByPair;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new userOpenOrdersByPair");
    }
    benchmark.miss();

    return new UserOpenOrdersByPair();
  }

  // currently nothing is returned
  public static final void returnObject(final UserOpenOrdersByPair userOpenOrdersByPair) {
    try {
      if (userOpenOrdersByPair != null) {
        if (Context.isUseOrderPoolEnabled()) {
          userOpenOrdersByPair.clear();
          pool.offer(userOpenOrdersByPair);
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
