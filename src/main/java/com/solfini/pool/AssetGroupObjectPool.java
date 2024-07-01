package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class AssetGroupObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(AssetGroupObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("ASSET_GROUP_POOL_QUEUE_CAPACITY", 4096);
  private static final int START_CAPACITY = PropertyReader.getProperty("ASSET_GROUP_POOL_START_CAPACITY", 1024);
  private static final OneToOneConcurrentArrayQueueCustom<AssetGroup> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "AssetGroupObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(AssetGroupObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private AssetGroupObjectPool() {
    // hidden default constructor
  }

  private static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new AssetGroup());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final AssetGroup get() {
    final AssetGroup assetGroup = pool.poll();
    if (assetGroup != null) {
      benchmark.hit();
      assetGroup.resetMarkAsReturned(); // TODO: remove after testing
      return assetGroup;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new AssetGroup");
    }
    benchmark.miss();

    return new AssetGroup();
  }

  public static final void returnObject(final AssetGroup assetGroup) {
    try {
      if (assetGroup != null) {
        if (Context.isUseOrderPoolEnabled()) {
          assetGroup.clear();
          pool.offer(assetGroup);
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
