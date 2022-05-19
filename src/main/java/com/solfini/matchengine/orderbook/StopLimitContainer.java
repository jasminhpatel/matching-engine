package com.solfini.matchengine.orderbook;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.message.internal.Order;

/**
 *
 * @author Chris Mack
 *
 */
public class StopLimitContainer implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(StopLimitContainer.class);

  private final int pairId;
  private final TreeSet<Order> buyTreeSet = new TreeSet<>(orderComparatorLow); // low to high
  private final TreeSet<Order> sellTreeSet = new TreeSet<>(orderComparatorHigh); // high to low

  public StopLimitContainer(final int pairId) {
    this.pairId = pairId;
  }

  public final void clear() {
    buyTreeSet.clear();
    sellTreeSet.clear();
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
    buyTreeSet.add(order);
  }

  public final void addSellLimit(final Order order) {
    sellTreeSet.add(order);
  }

  public final void removeBuyLimit(final Order order) {
    buyTreeSet.remove(order);
  }

  public final void removeSellLimit(final Order order) {
    sellTreeSet.remove(order);
  }

  // iterate from low limit to high
  // returns less than or equal to price
  public final void getTriggeredBuyLimitList(final int price, final List<Order> target) {
    while (!buyTreeSet.isEmpty()) {
      final Order order = buyTreeSet.first();
      if (order.getPriceInt() <= price) {
        buyTreeSet.pollFirst();
        target.add(order);
      } else
        return;
    }
  }

  // iterate from high limit to low
  // returns greater than or equal to price
  public final void getTriggeredSellLimitList(final int price, final List<Order> target) {
    while (!sellTreeSet.isEmpty()) {
      final Order order = sellTreeSet.first();
      if (order.getPriceInt() >= price) {
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

  // sort by largest to smallest
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
