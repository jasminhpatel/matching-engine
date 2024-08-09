package com.solfini.matchengine.decoder;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.matchengine.message.internal.OrderFilter;
import com.solfini.pool.OrderFilterObjectPool;
import com.solfini.sbe.encoder.*;

public class OrderFilterHandler implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(OrderFilterHandler.class);

  public final Message decodeOrderFilterRequest(final MessageHeaderDecoder headerDecoder, final OrderFilterDecoder orderFilterDecoder) {
    try {
      // validate
      final OrderFilter orderFilter = OrderFilterObjectPool.get();
      orderFilter.set(orderFilterDecoder);
      return orderFilter;
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }
}
