package com.solfini.matchengine.orderbook;

import com.solfini.matchengine.message.internal.OrderFilter;

public class OrderBookFilterCacheTest {
  public static void main(String[] args) {
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(123);
    long[] filters = {2,4,6,8};
    orderFilter.setOrderIdGroup(filters);
    OrderFilterCache.onModel(orderFilter);

    OrderFilter of = OrderFilterCache.remove(123);
    for (int i = 0; i < 10; i++) {
      //System.out.println("Filters contain " + i + " is " + of.contains(i));
    }
  }
}
