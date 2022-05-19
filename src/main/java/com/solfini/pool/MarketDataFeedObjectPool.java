package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.internal.MarketDataFeed;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class MarketDataFeedObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MarketDataFeedObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("MARKET_DATA_FEED_POOL_QUEUE_CAPACITY", 2097152);
  private static final int START_CAPACITY = PropertyReader.getProperty("MARKET_DATA_FEED_POOL_START_CAPACITY", 1048576);
  private static final OneToOneConcurrentArrayQueueCustom<MarketDataFeed> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "MarketDataFeedObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(MarketDataFeedObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private MarketDataFeedObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new MarketDataFeed());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final MarketDataFeed get() {
    final MarketDataFeed marketDataFeed = pool.poll();
    if (marketDataFeed != null) {
      benchmark.hit();
      marketDataFeed.resetMarkAsReturned(); // TODO: remove after testing
      return marketDataFeed;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new MarketDataFeed");
    }
    benchmark.miss();

    return new MarketDataFeed();
  }

  public static final void returnObject(final MarketDataFeed marketDataFeed) {
    try {
      if (marketDataFeed != null) {
        if (Context.isUseOrderPoolEnabled()) {
          marketDataFeed.clear();
          pool.offer(marketDataFeed);
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
