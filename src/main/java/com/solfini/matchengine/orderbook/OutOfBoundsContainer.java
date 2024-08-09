package com.solfini.matchengine.orderbook;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.agrona.collections.Long2ObjectHashMap;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.message.internal.Order;

/**
 *
 * @author Chris Mack
 *
 */
// similar to stop limit container, but the triggers are reversed
public class OutOfBoundsContainer implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(OutOfBoundsContainer.class);

  private final int pairId;
  private final TreeSet<Order> buyTreeSet = new TreeSet<>(orderComparatorHigh); // high to low
  private final TreeSet<Order> sellTreeSet = new TreeSet<>(orderComparatorLow); // low to high

  private final Map<Long, Order> outOfBoundsOrderMap = new Long2ObjectHashMap<>(); // key is orderId
  private final Map<Long, Order> outOfBoundsOrderMapBySecondaryOrderId = new Long2ObjectHashMap<>();; // key is secondaryOrderId

  public OutOfBoundsContainer(final int pairId) {
    this.pairId = pairId;
  }

  public final void clear() {
    buyTreeSet.clear();
    sellTreeSet.clear();
    outOfBoundsOrderMap.clear();
    outOfBoundsOrderMapBySecondaryOrderId.clear();
  }

  public final int getPairId() {
    return pairId;
  }

  public final TreeSet<Order> getBuyTreeSet() {
    return buyTreeSet;
  }

  public final TreeSet<Order> getSellTreeSet() {
    return sellTreeSet;
  }

  public final void addBuyLimit(final Order order) {
    outOfBoundsOrderMap.put(order.getOrderId(), order);
    outOfBoundsOrderMapBySecondaryOrderId.put(order.getSecondaryOrderId(), order);
    buyTreeSet.add(order);
  }

  public final void addSellLimit(final Order order) {
    outOfBoundsOrderMap.put(order.getOrderId(), order);
    outOfBoundsOrderMapBySecondaryOrderId.put(order.getSecondaryOrderId(), order);
    sellTreeSet.add(order);
  }

  public final void removeBuyLimit(final Order order) {
    outOfBoundsOrderMap.remove(order.getOrderId());
    outOfBoundsOrderMapBySecondaryOrderId.remove(order.getSecondaryOrderId());
    buyTreeSet.remove(order);
  }

  public final void removeSellLimit(final Order order) {
    outOfBoundsOrderMap.remove(order.getOrderId());
    outOfBoundsOrderMapBySecondaryOrderId.remove(order.getSecondaryOrderId());
    sellTreeSet.remove(order);
  }

  public final Order get(final long orderId) {
    return outOfBoundsOrderMap.get(orderId);
  }

  public final Order remove(final long orderId) {
    final Order order = outOfBoundsOrderMap.remove(orderId);
    if (order != null)
      outOfBoundsOrderMapBySecondaryOrderId.remove(order.getSecondaryOrderId());
    return order;
  }

  public final Collection<Order> values() {
    return outOfBoundsOrderMap.values();
  }

  public Order getSecondaryOrderId(final long secondaryOrderId) {
    return outOfBoundsOrderMapBySecondaryOrderId.get(secondaryOrderId);
  }

  public Order removeSecondaryOrderId(final long secondaryOrderId) {
    return outOfBoundsOrderMapBySecondaryOrderId.get(secondaryOrderId);
  }

  // iterate from high limit to low
  // returns greater than or equal to price
  public final void getTriggeredBuyLimitList(final int price, final List<Order> target) {
    while (!buyTreeSet.isEmpty()) {
      final Order order = buyTreeSet.first();
      if (order.getPriceInt() >= price) {
        buyTreeSet.pollFirst();
        target.add(order);
      } else
        return;
    }
  }

  // iterate from low limit to high
  // returns less than or equal to price
  public final void getTriggeredSellLimitList(final int price, final List<Order> target) {
    while (!sellTreeSet.isEmpty()) {
      final Order order = sellTreeSet.first();
      if (order.getPriceInt() <= price) {
        sellTreeSet.pollFirst();
        target.add(order);
      } else
        return;
    }
  }

  // sort by smallest to largest
  private static final Comparator<Order> orderComparatorLow = new Comparator<Order>() {
    @Override
    public int compare(final Order order1, final Order order2) {
      try {
        if (order1.getPriceInt() == order2.getPriceInt()) {
          if (order1.getOrderId() == order2.getOrderId()) {
            return 0;
          } else if (order1.getOrderId() > order2.getOrderId())
            return 1;
          else
            return -1;
        } else if (order1.getPriceInt() > order2.getPriceInt())
          return 1;
        else
          return -1;
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
      return 0;
    }
  };

  // sort by smallest to largest
  private static final Comparator<Order> orderComparatorHigh = new Comparator<Order>() {
    @Override
    public int compare(final Order order1, final Order order2) {
      try {
        if (order1.getPriceInt() == order2.getPriceInt()) {
          if (order1.getOrderId() == order2.getOrderId()) {
            return 0;
          } else if (order1.getOrderId() > order2.getOrderId())
            return 1;
          else
            return -1;
        } else if (order1.getPriceInt() > order2.getPriceInt())
          return -1;
        else
          return 1;
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
      return 0;
    }
  };


}
