package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.drmode.RecieverData;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class DRRecieverDataObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(DRRecieverDataObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("RecieverData_POOL_QUEUE_CAPACITY", 8_388_608);
  private static final int START_CAPACITY = PropertyReader.getProperty("RecieverData_POOL_START_CAPACITY", 4_194_304);
  private static final OneToOneConcurrentArrayQueueCustom<RecieverData> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "DRRecieverDataObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(DRRecieverDataObjectPool.class, START_CAPACITY);

  static {
    init();
  }

  private DRRecieverDataObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();
    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new RecieverData());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final RecieverData get() {
    final RecieverData recieverData = pool.poll();
    if (recieverData != null) {
      benchmark.hit();
      return recieverData;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new RecieverData");
    }
    benchmark.miss();

    return new RecieverData();
  }

  public static final void returnObject(final RecieverData recieverData) {
    try {
      if (recieverData != null) {
        recieverData.clear();
        if (Context.isUseOrderPoolEnabled()) {
          pool.offer(recieverData);
          benchmark.remit();
        }
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
