package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.instrument.Position;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class BalanceAdminMessageObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BalanceAdminMessageObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", 262_144);
  private static final int START_CAPACITY = PropertyReader.getProperty("BALANCE_ADMIN_POOL_START_CAPACITY", 131_072);
  private static final ManyToManyConcurrentArrayQueueCustom<BalanceAdminMessage> pool =
      new ManyToManyConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "BalanceAdminMessageObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(BalanceAdminMessageObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private BalanceAdminMessageObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new BalanceAdminMessage(null));

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));

  }

  public static final BalanceAdminMessage get() {
    final BalanceAdminMessage message = pool.poll();
    if (message != null) {
      benchmark.hit();
      message.resetMarkAsReturned(); // TODO: remove after testing
      return message;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new BalanceAdminMessage");
    }
    benchmark.miss();

    return new BalanceAdminMessage(null);
  }

  public static final void returnObject(final BalanceAdminMessage message) {
    try {
      if (message != null) {
        // Reclaim positions
        final Position[] positions = message.getPositionArr();

        for (int i = 0; i < positions.length; i++) {
          if (positions[i] != null) {
            PositionMatchThreadObjectPool.returnObject(positions[i]);
            positions[i] = null; // remove reference for returned positions
          }
        }


        message.clear();

        // attempt to return to its own pool
        if (message.getPool() != null) {
          if (Context.isUseOrderPoolEnabled()) {
            message.getPool().offer(message);
          }
          benchmark.remit();
          return;
        }

        if (Context.isUseOrderPoolEnabled()) {
          pool.offer(message);
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
