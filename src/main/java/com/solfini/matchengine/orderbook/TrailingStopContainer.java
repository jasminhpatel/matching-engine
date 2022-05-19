package com.solfini.matchengine.orderbook;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.util.FastArrayList;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class TrailingStopContainer implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(TrailingStopContainer.class);
  private final ManyToOneConcurrentArrayQueueCustom<Order> triggeredQueue;
  private final FastArrayList<Order> list = new FastArrayList<>(4096);

  private final InstrumentPair pair;
  private final TreeSet<Order> buyTreeSet = new TreeSet<>(orderComparatorHigh); // high to low
  private final TreeSet<Order> sellTreeSet = new TreeSet<>(orderComparatorLow); // low to high

  public TrailingStopContainer(final InstrumentPair pair) {
    this.pair = pair;
    this.triggeredQueue = new ManyToOneConcurrentArrayQueueCustom<Order>(4096, "triggeredQueue_" + pair.getId());
  }

  public final void clear() {
    buyTreeSet.clear();
    sellTreeSet.clear();
  }

  public final InstrumentPair getPair() {
    return pair;
  }

  public final TreeSet<Order> getBuyTreeSet() {
    return buyTreeSet;
  }

  public final TreeSet<Order> getSellTreeSet() {
    return sellTreeSet;
  }

  public final void addBuyLimit(final Order order) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "TrailingStop addBuyLimit order=", order);
    }
    buyTreeSet.add(order);
  }

  public final void addSellLimit(final Order order) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "TrailingStop addSellLimit order=", order);
    }
    sellTreeSet.add(order);
  }

  public final void removeBuyLimit(final Order order) {
    final boolean rc = buyTreeSet.remove(order);
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "TrailingStop removeBuyLimit rc=", rc, ", order=", order);
    }
  }

  public final void removeSellLimit(final Order order) {
    final boolean rc = sellTreeSet.remove(order);
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "TrailingStop removeSellLimit rc=", rc, ", order=", order);
    }
  }

  public final void recalc(final int currentPrice) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "TrailingStop recalc currentPrice=", currentPrice, ", pairId=", (double) pair.getId());
    }
    try {
      recalcBuyTrailingPrice(currentPrice);
      recalcSellTrailingPrice(currentPrice);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public final void recalcBuyTrailingPrice(final int currentPrice) {
    // update max price, and detect triggered
    for (final Order order : buyTreeSet) {
      final double minTrailingPrice = order.setMinTrailingPrice(currentPrice) * pair.getPriceScaleFactor();
      final double discount = StringUtil.toDouble(order.getPrice2(), order.getPrice2Scale());
      final int triggerPrice = (int) (pair.getPriceScaleMultiplier() * minTrailingPrice * (1D + (discount * .01)));
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_2, "recalcBuyTrailingPrice buy currentPrice=", currentPrice, ", minTrailingPrice=", minTrailingPrice,
            ", discount=", discount, ", triggerPrice=", triggerPrice, ", order=", order);
      }
      if (currentPrice >= triggerPrice) {
        list.add(order);
      }
    }
    // move order to triggered queue
    for (final Order order : list) {
      if (buyTreeSet.remove(order)) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_2, "TrailingStop triggered buy order=", order);
        }
        triggeredQueue.addGuaranteed(order);
      }
    }
    list.clear();
  }

  public final void recalcSellTrailingPrice(final int currentPrice) {
    // update max price, and detect triggered
    for (final Order order : sellTreeSet) {
      final double maxTrailingPrice = order.setMaxTrailingPrice(currentPrice) * pair.getPriceScaleFactor();
      final double discount = StringUtil.toDouble(order.getPrice2(), order.getPrice2Scale());
      final int triggerPrice = (int) (pair.getPriceScaleMultiplier() * maxTrailingPrice * (1D - (discount * .01)));
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_2, "recalcSellTrailingPrice currentPrice=", currentPrice, ", maxTrailingPrice=", maxTrailingPrice,
            ", discount=", discount, ", triggerPrice=", triggerPrice, ", order=", order);
      }
      if (currentPrice <= triggerPrice) {
        list.add(order);
      }
    }
    // move order to triggered queue
    for (final Order order : list) {
      if (sellTreeSet.remove(order)) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_2, "TrailingStop triggered sell order=", order);
        }
        triggeredQueue.addGuaranteed(order);
      }
    }
    list.clear();
  }

  // polls for triggered list
  public final void getTriggeredOrderList(final List<Order> target) {
    while (triggeredQueue.drainTo(target, 1024) > 0);
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

  public static void main2(String args[]) {
    final Instrument base = new Instrument(1, "BTC", "BTC", (short) 6, (short) 6, 3500, 1000);
    final Instrument quoted = new Instrument(2, "USD", "USD", (short) 6, (short) 6, 1, 1000);
    InstrumentPair pair = new InstrumentPair(14, "BTCUSD", "BTCUSD", base, quoted, (short) 2, (short) 2, 1, AssetType.PAIR, 0, 0, 0, 0);
    TrailingStopContainer container = new TrailingStopContainer(pair);
    FastArrayList<Order> list = new FastArrayList<>(4096);

    Order order1 = new Order();
    order1.setOrderId(1);
    order1.setPrice2(10L, (short) 2);
    container.addBuyLimit(order1);

    Order order2 = new Order();
    order2.setOrderId(2);
    order2.setPrice2(20L, (short) 2);
    container.addBuyLimit(order2);

    Order order3 = new Order();
    order3.setOrderId(3);
    order3.setPrice2(50L, (short) 2);
    container.addBuyLimit(order3);

    container.getTriggeredOrderList(list);
    container.recalcBuyTrailingPrice(9_000);
    container.getTriggeredOrderList(list);
    container.recalcBuyTrailingPrice(9_700);
    container.getTriggeredOrderList(list);
    container.recalcBuyTrailingPrice(9_900);
    container.getTriggeredOrderList(list);
    container.recalcBuyTrailingPrice(9_990);
    container.getTriggeredOrderList(list);
    container.recalcBuyTrailingPrice(10_000);

    container.getTriggeredOrderList(list);

    container.getTriggeredOrderList(list);
  }

  public static void main(String args[]) {
    final Instrument base = new Instrument(1, "BTC", "BTC", (short) 6, (short) 6, 3500, 1000);
    final Instrument quoted = new Instrument(2, "USD", "USD", (short) 6, (short) 6, 1, 1000);
    InstrumentPair pair = new InstrumentPair(14, "BTCUSD", "BTCUSD", base, quoted, (short) 2, (short) 2, 1, AssetType.PAIR, 0, 0, 0, 0);
    TrailingStopContainer container = new TrailingStopContainer(pair);
    FastArrayList<Order> list = new FastArrayList<>(4096);

    Order order1 = new Order();
    order1.setOrderId(1);
    order1.setPrice2(100L, (short) 2);
    container.addSellLimit(order1);

    Order order2 = new Order();
    order2.setOrderId(2);
    order2.setPrice2(20L, (short) 2);
    container.addSellLimit(order2);

    Order order3 = new Order();
    order3.setOrderId(3);
    order3.setPrice2(5L, (short) 2);
    container.addSellLimit(order3);

    container.getTriggeredOrderList(list);
    container.recalcSellTrailingPrice(10_000);
    container.getTriggeredOrderList(list);
    container.recalcSellTrailingPrice(9_990);
    container.getTriggeredOrderList(list);
    container.recalcSellTrailingPrice(9_900);
    container.getTriggeredOrderList(list);
    container.recalcSellTrailingPrice(9_700);
    container.getTriggeredOrderList(list);
    container.recalcSellTrailingPrice(9_000);
    container.getTriggeredOrderList(list);
    container.recalcSellTrailingPrice(9_700);
    container.getTriggeredOrderList(list);

    container.getTriggeredOrderList(list);
  }
}
