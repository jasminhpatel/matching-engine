package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.message.outbound.OptionPricingMessage;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class OptionPricingObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(OptionPricingObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("OPTION_PRICING_POOL_QUEUE_CAPACITY", 131_072);
  private static final int START_CAPACITY = PropertyReader.getProperty("OPTION_PRICING_POOL_START_CAPACITY", 65_536);
  private static final OneToOneConcurrentArrayQueueCustom<OptionPricingMessage> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "OptionPricingObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(OptionPricingObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private OptionPricingObjectPool() {
    // hidden default constructor
  }

  public static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new OptionPricingMessage());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final OptionPricingMessage get() {
    final OptionPricingMessage optionPricingMessage = pool.poll();
    if (optionPricingMessage != null) {
      benchmark.hit();
      optionPricingMessage.resetMarkAsReturned(); // TODO: remove after testing
      return optionPricingMessage;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new OptionPricingMessage");
    }
    benchmark.miss();

    return new OptionPricingMessage();
  }

  public static final void returnObject(final OptionPricingMessage optionPricingMessage) {
    try {
      if (Context.isUseOrderPoolEnabled()) {
        optionPricingMessage.clear();
        pool.offer(optionPricingMessage);
      }

      benchmark.remit();
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
