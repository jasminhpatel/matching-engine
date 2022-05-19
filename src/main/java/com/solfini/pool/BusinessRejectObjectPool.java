package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class BusinessRejectObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BusinessRejectObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("BUSINESS_REJECT_POOL_QUEUE_CAPACITY", 2097152);
  private static final int START_CAPACITY = PropertyReader.getProperty("BUSINESS_REJECT_POOL_START_CAPACITY", 1048576);
  private static final OneToOneConcurrentArrayQueueCustom<BusinessRejectMessage> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "BusinessRejectObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(BusinessRejectObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private BusinessRejectObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();
    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new BusinessRejectMessage());
    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final BusinessRejectMessage get() {
    final BusinessRejectMessage businessRejectMessage = pool.poll();
    if (businessRejectMessage != null) {
      benchmark.hit();
      businessRejectMessage.resetMarkAsReturned(); // TODO: remove after testing
      return businessRejectMessage;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new BusinessRejectMessage");
    }
    benchmark.miss();

    return new BusinessRejectMessage();
  }

  public static final void returnObject(final BusinessRejectMessage businessRejectMessage) {
    try {
      if (businessRejectMessage != null) {
        if (Context.isUseOrderPoolEnabled()) {
          businessRejectMessage.clear();
          pool.offer(businessRejectMessage);
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
