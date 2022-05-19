package com.solfini.matchengine.orderbook.validator;

import com.google.common.collect.Sets;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;

import static com.solfini.common.Constants.ARRAY_ORDER_BOOK;

import java.util.Set;

public final class OrderBookValidatorFactory {
  private OrderBookValidatorFactory() {}

  private static Set<OrderBookValidator> orderBookValidators = Sets.newConcurrentHashSet();

  public static Set<OrderBookValidator> getOrderBookValidators() {
    return orderBookValidators;
  }

  public static OrderBookValidator newOrderBookValidator(final OrderBook orderBook) {

    OrderBookValidator validator = null;

    if (ARRAY_ORDER_BOOK == orderBook.getOrderBookStrategy()) {
      validator = new ArrayOrderBookValidator((ArrayOrderBook) orderBook);
    }

    if (validator != null) {
      orderBookValidators.add(validator);
    }

    return validator;
  }
}
