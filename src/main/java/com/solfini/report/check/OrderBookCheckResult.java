package com.solfini.report.check;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.solfini.matchengine.message.internal.Order;



public class OrderBookCheckResult {

  private Map<String, Set<Order>> orderBookOrders = Maps.newHashMap();

  protected Map<String, List<String>> orderBookCheckResultMap = Maps.newHashMap();

  public Set<Order> getAllOrders() {
    final Set<Order> allOrders = Sets.newHashSet();
    for (Set<Order> orders : orderBookOrders.values()) {
      allOrders.addAll(orders);
    }
    return allOrders;
  }

  public void addValidateResult(final String pair, Set<Order> orders, final List<String> checkResult) {
    orderBookOrders.put(pair, orders);
    orderBookCheckResultMap.put(pair, checkResult);
  }

  public String getCheckResultStr() {
    final StringBuilder sb = new StringBuilder("Order book status check result:\r\n");
    for (Map.Entry<String, List<String>> entry : orderBookCheckResultMap.entrySet()) {
      sb.append("Order book ").append(entry.getKey()).append("'s result is:");
      final List<String> obResult = entry.getValue();
      if (null == obResult || obResult.isEmpty()) {
        sb.append(" OK\r\n");
      } else {
        for (String result : obResult) {
          sb.append(result).append(",");
        }
        sb.append("\r\n");
      }
    }
    return sb.toString();
  }

  public final Map<String, Set<Order>> getOrderBookOrders() {
    return orderBookOrders;
  }

  public final void setOrderBookOrders(final Map<String, Set<Order>> orderBookOrders) {
    this.orderBookOrders = orderBookOrders;
  }

  public final Map<String, List<String>> getOrderBookCheckResult() {
    return orderBookCheckResultMap;
  }

  public final void setOrderBookCheckResult(final Map<String, List<String>> orderBookCheckResult) {
    this.orderBookCheckResultMap = orderBookCheckResult;
  }

  @Override
  public String toString() {
    return "OrderBookCheckResult [orderBookOrders=" + orderBookOrders + ", orderBookCheckResult=" + orderBookCheckResultMap + "]";
  }
}
