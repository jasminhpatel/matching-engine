package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.outbound.CancelRejectMessage;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class CancelRejectObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(CancelRejectObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("CANCEL_REJECT_POOL_QUEUE_CAPACITY", 2097152);
  private static final int START_CAPACITY = PropertyReader.getProperty("CANCEL_REJECT_POOL_START_CAPACITY", 1048576);
  private static final OneToOneConcurrentArrayQueueCustom<CancelRejectMessage> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "CancelRejectObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(CancelRejectObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private CancelRejectObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new CancelRejectMessage());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final CancelRejectMessage get() {
    final CancelRejectMessage cancelRejectMessage = pool.poll();
    if (cancelRejectMessage != null) {
      benchmark.hit();
      cancelRejectMessage.resetMarkAsReturned(); // TODO: remove after testing
      return cancelRejectMessage;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new CancelRejectMessage");
    }
    benchmark.miss();

    return new CancelRejectMessage();
  }

  public static final void returnObject(final CancelRejectMessage cancelRejectMessage) {
    try {
      if (cancelRejectMessage != null) {
        if (Context.isUseOrderPoolEnabled()) {
          cancelRejectMessage.clear();
          pool.offer(cancelRejectMessage);
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
