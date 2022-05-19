package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.instrument.Position;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class PositionMatchThreadObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PositionMatchThreadObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("POSITION_POOL_QUEUE_CAPACITY", 67_108_864);
  private static final int START_CAPACITY = PropertyReader.getProperty("POSITION_POOL_START_CAPACITY", 33_554_432);
  private static final OneToOneConcurrentArrayQueueCustom<Position> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "PositionMatchThreadObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(PositionMatchThreadObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private PositionMatchThreadObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new Position());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final Position get() {
    final Position position = pool.poll();
    if (position != null) {
      benchmark.hit();
      position.resetMarkAsReturned(); // TODO: remove after testing
      return position;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new Position");
    }
    benchmark.miss();

    return new Position();
  }

  public static final void returnObject(final Position position) {
    try {
      if (position != null) {
        if (Context.isUseOrderPoolEnabled()) {
          position.clear();
          pool.offer(position);
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
