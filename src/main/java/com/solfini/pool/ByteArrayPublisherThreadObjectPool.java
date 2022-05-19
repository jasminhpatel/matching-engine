package com.solfini.pool;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.util.ByteArrDisruptor;
import com.solfini.util.TimeUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class ByteArrayPublisherThreadObjectPool implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ByteArrayPublisherThreadObjectPool.class);
  private static final ByteArrDisruptor[] pool = init();

  private ByteArrayPublisherThreadObjectPool() {
    // hidden default constructor
  }

  private static final ByteArrDisruptor[] init() {
    final ByteArrDisruptor[] pool = new ByteArrDisruptor[4096];
    long t0 = TimeUtil.getTime();

    for (int i = 0; i < 4096; i++) {
      int size = 256;
      if (i >= 128 && i <= 512)
        size = 16_384;
      else if (i > 512 && i <= 1280)
        size = 4096;
      else if (i > 1280 && i <= 2048)
        size = 1024;

      try {
        pool[i] = ByteArrDisruptor.buildDisruptor(size);
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
      for (int j = 0; j < size / 2; j++) {
        byte[] bytes = new byte[i];
        pool[i].offer(bytes);
      }
    }

    LOGGER.info(LOG_FMT_2, "pool loaded 4096, time=", (TimeUtil.getTime() - t0));
    return pool;
  }

  public static final byte[] get(final int length) {
    if (length >= 4096)
      return new byte[length];


    byte[] bytes = pool[length].tryGet();
    if (bytes == null) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_2, "pool creating new bytes, length=", length);
      }

      return new byte[length];
    }

    return bytes;
  }

  public static final void returnObject(final byte[] bytes) {
    try {
      pool[bytes.length].offer(bytes);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }
}
