package com.solfini.matchengine.orderbook;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.OrdType;

/**
 *
 * @author Chris Mack
 *
 */
// similar to stop limit container, but the triggers are reversed
public class ADLMakerContainer implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ADLMakerContainer.class);

  private final int pairId;
  private final ConcurrentSkipListSet<Order> buyTreeSet = new ConcurrentSkipListSet<>(orderComparatorHigh); // high to low
  private final ConcurrentSkipListSet<Order> sellTreeSet = new ConcurrentSkipListSet<>(orderComparatorLow); // low to high

  public ADLMakerContainer(final int pairId) {
    this.pairId = pairId;
  }

  public final void clear() {
    buyTreeSet.clear();
    sellTreeSet.clear();
  }

  public final int getPairId() {
    return pairId;
  }

  public final ConcurrentSkipListSet<Order> getBuyTreeSet() {
    return buyTreeSet;
  }

  public final ConcurrentSkipListSet<Order> getSellTreeSet() {
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

  // called from matching thread, lookupOrder for cancelOrder
  public final Order removeOrder(final CancelOrder cancelOrder) {
    try {
      Order order = null;
      for (final Order algoOrder : buyTreeSet) {
        if (algoOrder.getOrderId() == cancelOrder.getOrigOrderId()
            || (algoOrder.getClOrdId() != null && algoOrder.getClOrdId().equals(cancelOrder.getClOrdId()))) {
          order = algoOrder;
          break;
        }
      }
      if (order != null) {
        buyTreeSet.remove(order);
        return order;
      }

      for (final Order algoOrder : sellTreeSet) {
        if (algoOrder.getOrderId() == cancelOrder.getOrigOrderId()
            || (algoOrder.getClOrdId() != null && algoOrder.getClOrdId().equals(cancelOrder.getClOrdId()))) {
          order = algoOrder;
          break;
        }
      }
      if (order != null) {
        sellTreeSet.remove(order);
        return order;
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }

  // iterate from high limit to low
  // returns greater than or equal to price
  public final void getTriggeredBuyLimitList(final int price, final List<Order> target) {
    for (final Order order : buyTreeSet) {
      if (order.getOrdType() == OrdType.MARKET || order.getPriceInt() >= price) {
        target.add(order);
      } else
        return;
    }
  }

  // iterate from low limit to high
  // returns less than or equal to price
  public final void getTriggeredSellLimitList(final int price, final List<Order> target) {
    for (final Order order : sellTreeSet) {
      if (order.getOrdType() == OrdType.MARKET || order.getPriceInt() <= price) {
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
