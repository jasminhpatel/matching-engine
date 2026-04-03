package com.solfini.matchengine.orderbook;

import com.solfini.common.CustomLogger;

public class GlobalOrderBook {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(GlobalOrderBook.class);

  protected static long filledCountGlobal; // this works because there is a single matcher thread
  private static long orderId = 0;

  protected GlobalOrderBook() {
    // Make sure this class is not instantiated
  }

  public static final void setFilledCountGlobal(final long newValue) {
    filledCountGlobal = newValue;
  }

  public static final void setFilledCountGlobalIfGreater(final long newValue) {
    if (newValue > filledCountGlobal) {
      filledCountGlobal = newValue;
    }
  }

  public static final long getFilledCountGlobal() {
    return filledCountGlobal;
  }

  public static final void setOrderIdIfGreater(final int caller, final long newValue) {
    if (newValue > orderId) {
      orderId = newValue;
    }
  }

  public static final long getOrderId() {
    return orderId;
  }

  public static final long incrementAndGetFilledCountGlobal() {
    return filledCountGlobal++;
  }
}
