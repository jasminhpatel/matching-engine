package com.solfini.matchengine.orderbook.validator;

import java.util.List;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBook;

public interface OrderBookValidator {

  int getOrderBookType();

  OrderBook getOrderBook();

  List<String> validate(List<Order> allOrders);

  List<String> validate();
}
