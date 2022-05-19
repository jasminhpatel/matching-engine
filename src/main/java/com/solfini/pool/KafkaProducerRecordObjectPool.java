package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.kafka.PoolableProducerRecord;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class KafkaProducerRecordObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaProducerRecordObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("KAFKA_PRODUCER_POOL_QUEUE_CAPACITY", 2097152);
  private static final int START_CAPACITY = PropertyReader.getProperty("KAFKA_PRODUCER_POOL_START_CAPACITY", 1048576);
  private static final OneToOneConcurrentArrayQueueCustom<PoolableProducerRecord<String, byte[]>> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "KafkaProducerRecordObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(KafkaProducerRecordObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private KafkaProducerRecordObjectPool() {
    // hidden default constructor
  }

  private static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new PoolableProducerRecord<>(new byte[0]));

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final PoolableProducerRecord<String, byte[]> get() {
    final PoolableProducerRecord<String, byte[]> poolableProducerRecord = pool.poll();
    if (poolableProducerRecord != null) {
      benchmark.hit();
      return poolableProducerRecord;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new PoolableProducerRecord");
    }
    benchmark.miss();

    return new PoolableProducerRecord<>(new byte[0]);
  }

  public static final void returnObject(final PoolableProducerRecord<String, byte[]> poolableProducerRecord) {
    try {
      if (poolableProducerRecord != null) {
        pool.offer(poolableProducerRecord);
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
