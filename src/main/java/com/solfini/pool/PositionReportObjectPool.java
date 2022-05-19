package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.instrument.Position;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.PoolBenchmark;

/**
 *
 * @author Chris Mack
 *
 */
public class PositionReportObjectPool implements Constants {

  private static final int QUEUE_CAPACITY = PropertyReader.getProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", 2097152);
  private static final int START_CAPACITY = PropertyReader.getProperty("POSITION_REPORT_POOL_START_CAPACITY", 1048576);
  private static final PositionReportObjectPool[] pools = init();

  private final CustomLogger LOGGER = CustomLogger.getLogger(PositionReportObjectPool.class);
  private final OneToOneConcurrentArrayQueueCustom<PositionReportMessage> pool =
      new OneToOneConcurrentArrayQueueCustom<>(QUEUE_CAPACITY, "PositionReportObjectPool");
  private final PoolBenchmark benchmark = new PoolBenchmark(PositionReportObjectPool.class, START_CAPACITY);

  private static final PositionReportObjectPool[] init() {
    final PositionReportObjectPool[] pools = new PositionReportObjectPool[Math.max(Context.getEncoderThreads(), 1)];

    for (int i = 0; i < pools.length; ++i) {
      pools[i] = new PositionReportObjectPool();
    }

    return pools;
  }

  public static final PositionReportMessage get() {
    return getPool().getMessage();
  }

  public static final void returnObject(final PositionReportMessage message) {
    getPool().returnMessage(message);
  }

  private static final PositionReportObjectPool getPool() {
    if (Context.getEncoderThreads() > 0) {
      final String name = Thread.currentThread().getName();
      final int index = ((int) name.charAt(name.length() - 1)) - 48;
      if (index >= 0 && index < pools.length)
        return pools[index];
    }

    return pools[0];
  }

  private PositionReportObjectPool() {
    final long t0 = TimeUtil.getTime();

    for (int i = 0; i < START_CAPACITY; i++)
      pool.offer(new PositionReportMessage());

    LOGGER.info(LOG_FMT_4, "pool loaded ", (long) START_CAPACITY, ", time=", (TimeUtil.getTime() - t0));
  }

  private PositionReportMessage getMessage() {
    final PositionReportMessage message = pool.poll();
    if (message != null) {
      benchmark.hit();
      return message;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("pool creating new PositionReportMessage");
    }
    benchmark.miss();

    return new PositionReportMessage();
  }

  private void returnMessage(final PositionReportMessage message) {
    try {
      if (message != null) {

        // remove reference in arr, but don't return them to pool because its already been returned
        Position[] positionArr = message.getPositions();
        for (int i = 0; i < message.getPositionsLength(); i++) {
          positionArr[i] = null;
        }

        if (Context.isUseOrderPoolEnabled()) {
          message.clear();
          pool.offer(message);
        }
        benchmark.remit();
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public static final PositionReportObjectPool[] getPoolArr() {
    return pools;
  }

  public final int getSize() {
    return pool.size();
  }

  public final int getCapacity() {
    return pool.capacity();
  }
}
