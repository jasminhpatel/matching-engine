package com.solfini.pool;

import java.nio.ByteBuffer;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class ByteBufferObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ByteBufferObjectPool.class);

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("BYTE_BUFFER_POOL_QUEUE_CAPACITY", 16384);
  private static final int START_CAPACITY = PropertyReader.getProperty("BYTE_BUFFER_POOL_START_CAPACITY", 8192);
  private static final OneToOneConcurrentArrayQueueCustom<ByteBuffer> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "ByteBufferObjectPool");
  private static final PoolBenchmark benchmark = new PoolBenchmark(ByteBufferObjectPool.class, START_CAPACITY);
  static {
    init();
  }

  private ByteBufferObjectPool() {
    // hidden default constructor
  }

  private static final void init() {
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(ByteBuffer.allocateDirect(32768 * 2));

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  public static final ByteBuffer get() {
    final ByteBuffer byteBuffer = pool.poll();
    if (byteBuffer != null) {
      benchmark.hit();
      return byteBuffer;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new ByteBuffer");
    }
    benchmark.miss();

    return ByteBuffer.allocateDirect(32768 * 2);
  }

  public static final void returnObject(final ByteBuffer byteBuffer) {
    try {
      if (byteBuffer != null) {
        if (Context.isUseOrderPoolEnabled()) {
          byteBuffer.clear();
          pool.offer(byteBuffer);
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
