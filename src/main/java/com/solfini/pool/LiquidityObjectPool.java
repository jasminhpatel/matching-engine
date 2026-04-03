package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.internal.LiquidityResponse;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

public class LiquidityObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LiquidityObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("LIQUIDITY_POOL_QUEUE_CAPACITY", 32768);
  private static final int START_CAPACITY = PropertyReader.getProperty("LIQUIDITY_POOL_START_CAPACITY", 16384);
  private static final OneToOneConcurrentArrayQueueCustom<LiquidityResponse.Liquidity> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "LiquidityObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(LiquidityObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private LiquidityObjectPool() {}

  public static void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new LiquidityResponse.Liquidity());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static LiquidityResponse.Liquidity get() {
    final LiquidityResponse.Liquidity liquidity = pool.poll();
    if (liquidity != null) {
      benchmark.hit();
      liquidity.clear();
      liquidity.resetMarkAsReturned();
      return liquidity;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new LiquidityMessage.Liquidity");
    }
    benchmark.miss();

    return new LiquidityResponse.Liquidity();
  }

  public static void returnObject(final LiquidityResponse.Liquidity liquidity) {
    try {
      if (liquidity != null) {
        if (Context.isUseOrderPoolEnabled()) {
          // clear when pool.get()
          //liquidity.clear();
          pool.offer(liquidity);
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
