package com.solfini.matchengine.orderbook;

import org.agrona.collections.Long2ObjectHashMap;
import com.solfini.matchengine.message.internal.OrderFilter;

public class OrderFilterCache {
  private static final Long2ObjectHashMap<OrderFilter> ORDER_FILTER_HASH_MAP = new Long2ObjectHashMap<>();

  public static void onLoad(final OrderFilter orderFilter) {
    ORDER_FILTER_HASH_MAP.put(orderFilter.getFilterId(), orderFilter);
  }

  public static void onModel(final OrderFilter orderFilter) {
    onLoad(orderFilter);
  }

  public static OrderFilter remove(final long filterId) {
    return ORDER_FILTER_HASH_MAP.remove(filterId);
  }
}
