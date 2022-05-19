package com.solfini.report.check;

import java.util.List;
import java.util.Set;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.matchengine.orderbook.validator.OrderBookValidatorFactory;

public class OrderBookStatesChecker {

  private static final String ORDER_DUPLICATE_EXCEPTION_MSG = "Order duplicate found in order book %s, order %s";

  public OrderBookCheckResult checkOrderBookStatus() {
    final OrderBookCheckResult checkReport = new OrderBookCheckResult();

    Set<OrderBookValidator> validators = OrderBookValidatorFactory.getOrderBookValidators();
    for (OrderBookValidator validator : validators) {
      final String pair = validator.getOrderBook().getInstrumentPair().getName();

      final List<Order> orders = Lists.newArrayList();
      final List<String> result = validator.validate(orders);
      final Set<Order> orderSet = Sets.newHashSet();

      for (Order order : orders) {
        if (orderSet.contains(order)) {
          result.add(String.format(ORDER_DUPLICATE_EXCEPTION_MSG, pair, order.toJSON()));
        } else {
          orderSet.add(order);
        }
      }
      checkReport.addValidateResult(pair, orderSet, result);
    }

    return checkReport;
  }
}
