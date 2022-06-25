package com.solfini.matchengine.orderbook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.pool.CancelOrderMatchThreadObjectPool;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;

/**
 *
 * @author Chris Mack
 *
 */
// auction container, sorts bids and asks and matches
public class AuctionContainer implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(AuctionContainer.class);

  private final InstrumentPair pair;
  private final ConcurrentSkipListSet<Order> buyTreeSet = new ConcurrentSkipListSet<>(orderComparatorHigh); // high to low
  private final ConcurrentSkipListSet<Order> sellTreeSet = new ConcurrentSkipListSet<>(orderComparatorLow); // low to high

  private long maxQtyMatchedAtLevel = 0;
  private int optimalAskPrice = 0;
  private int optimalAskIndex = 0;
  private int optimalBidIndex = 0;
  private long highPriceBound;
  private long lowPriceBound;
  private long lastOpenedTime;
  private int auctionOpenedCount = 0;

  private long lastCalculatedPriceTime = 0;

  public AuctionContainer(final InstrumentPair pair) {
    this.pair = pair;
  }

  public final void clear() {
    buyTreeSet.clear();
    sellTreeSet.clear();
  }

  public final int getPairId() {
    return pair.getId();
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

  public final boolean removeBuyLimit(final Order order) {
    final boolean rc = buyTreeSet.remove(order);
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_6, ">>orderbook.removeBuyLimit rc=" + rc + ", order=", order);
    }
    return rc;
  }

  public final boolean removeSellLimit(final Order order) {
    final boolean rc = sellTreeSet.remove(order);
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_6, ">>orderbook.removeBuyLimit rc=" + rc + ", order=", order);
    }
    return rc;
  }

  public final long getMaxQtyMatchedAtLevel() {
    return maxQtyMatchedAtLevel;
  }

  public final int getOptimalAskPrice() {
    return optimalAskPrice;
  }

  public final int getOptimalAskIndex() {
    return optimalAskIndex;
  }

  public final int getOptimalBidIndex() {
    return optimalBidIndex;
  }

  public final long getHighPriceBound() {
    return highPriceBound;
  }

  public final long getLowPriceBound() {
    return lowPriceBound;
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

  public final void openAuction(final ArrayOrderBook orderBook) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_6, ">>orderbook.openAuction id=", orderBook.getId(), ", optimalAskPrice=", String.valueOf(optimalAskPrice),
          ", maxQtyMatchedAtLevel=", maxQtyMatchedAtLevel);
    }
    // populateOutOfBoundsOrderMapFromOrderBook();
    // move orders to map
    orderBook.setPublishAcks(false);
    final OrderBookPriceLevel[] level = orderBook.getBookArr();
    for (int i = 0; i < level.length; i++) { // this will take a while...
      Order tmp = level[i].getHead();
      if (tmp != null) {
        while (tmp != null) {
          if (tmp.getSide() == Side.BUY)
            addBuyLimit(tmp);
          else if (tmp.getSide() == Side.SELL)
            addSellLimit(tmp);

          tmp = tmp.getNext();
        }
      }
    }

    // clear orderbook
    orderBook.setPublishAcks(true);
    orderBook.clearOrderBook();
    lastOpenedTime = System.currentTimeMillis();
    auctionOpenedCount++;
  }

  // close auction by calculating the price and filling crossing orders
  // finally re-populate the orderbook
  // use volume weighted algo to calc optimal auction price
  // http://candidocs.nasdaqdubai.com/2014/May/04/de353449-6d66-4b2c-81a7-0b1c1fda8204/Calculation%20of%20the%20Theoretical%20Auction%20Price.pdf
  public final void closeAuction(final ArrayOrderBook orderBook) {
    try {
      // calcAuctionPrice
      final List<Order> bidsList = new ArrayList<>();
      final List<Order> asksList = new ArrayList<>();
      calcAuctionPrice(orderBook, bidsList, asksList);

      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_6, ">>orderbook.closeAuction optimalAskPrice=", String.valueOf(optimalAskPrice), ", maxQtyMatchedAtLevel=",
            maxQtyMatchedAtLevel, ", lowPriceBound=", lowPriceBound, ", highPriceBound=", highPriceBound);
      }

      // check bounds, cancel if out of bounds
      if (optimalAskPrice == 0 || maxQtyMatchedAtLevel == 0 || optimalAskPrice < lowPriceBound || optimalAskPrice < highPriceBound
          || bidsList.size() == 0 || asksList.size() == 0) {
        cancelAuction(orderBook);
        return;
      }

      // match
      int currentBidIndex = 0;
      int currentAskIndex = 0;
      Order bid = bidsList.get(currentBidIndex);
      Order ask = asksList.get(currentAskIndex);
      while (true) {
        if (bid == null || ask == null)
          break;
        if (bid.getQuantityLong() <= 0 && bidsList.size() > currentBidIndex + 1) {
          currentBidIndex++;
          bid = bidsList.get(currentBidIndex);
        }
        if (ask.getQuantityLong() <= 0 && asksList.size() > currentAskIndex + 1) {
          currentAskIndex++;
          ask = asksList.get(currentAskIndex);
        }
        if (bid == null || ask == null || (bid.getOrdType() != OrdType.MARKET && bid.getPriceInt() < optimalAskPrice)
            || (ask.getOrdType() != OrdType.MARKET && ask.getPriceInt() > optimalAskPrice))
          break;

        final Order takerOrder = ask; // TODO is the ask always taker?
        final Order makerOrder = bid;
        final long quantityFilled = Math.min(bid.getQuantityLong(), ask.getQuantityLong());
        if (quantityFilled == 0)
          break;

        makerOrder.setQuantityLong(makerOrder.getQuantityLong() - quantityFilled);
        takerOrder.setQuantityLong(takerOrder.getQuantityLong() - quantityFilled);
        orderBook.filled(quantityFilled, makerOrder, takerOrder, BUY_LIMIT);

        removeBuyLimit(bid);
        removeSellLimit(ask);
      }

      // re-populate the orderbook
      populateOrderBookFromAuctionMap(orderBook);
      // clear orders from auction
      clear();

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public final void cancelAuction(final ArrayOrderBook orderBook) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_6, ">>orderbook.cancelAuction id=", orderBook.getId(), ", optimalAskPrice=", String.valueOf(optimalAskPrice),
          ", maxQtyMatchedAtLevel=", maxQtyMatchedAtLevel, ", auction=", toString());
    }
    try {
      for (final Order order : buyTreeSet) {
        final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
        cancelOrder.set(order, order.getOrderId(), 0);
        cancelOrder.setUser(order.getUser());
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_6, ">>orderbook.cancelAuction order.getOrderId()=", order.getOrderId(), CANCELORDER_EQ, cancelOrder);
        }

        orderBook.cancelOrder(cancelOrder);
      }
      for (final Order order : sellTreeSet) {
        final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
        cancelOrder.set(order, order.getOrderId(), 0);
        cancelOrder.setUser(order.getUser());
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_6, ">>orderbook.cancelAuction order.getOrderId()=", order.getOrderId(), CANCELORDER_EQ, cancelOrder);
        }

        orderBook.cancelOrder(cancelOrder);
      }

      // re-populate the orderbook
      populateOrderBookFromAuctionMap(orderBook);
      // clear orders from auction
      clear();

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public final int calcAuctionPrice(final OrderBook orderBook, final long recalcTimeInterval) {
    if (recalcTimeInterval > 0 && System.currentTimeMillis() < recalcTimeInterval + lastCalculatedPriceTime)
      return optimalAskPrice;

    final List<Order> bidsList = new ArrayList<>();
    final List<Order> asksList = new ArrayList<>();
    return calcAuctionPrice(orderBook, bidsList, asksList);
  }

  // use volume weighted algo to calc optimal auction price
  // http://candidocs.nasdaqdubai.com/2014/May/04/de353449-6d66-4b2c-81a7-0b1c1fda8204/Calculation%20of%20the%20Theoretical%20Auction%20Price.pdf
  // accepts already created lists to populate
  public final int calcAuctionPrice(final OrderBook orderBook, final List<Order> bidsList, final List<Order> asksList) {
    try {
      this.maxQtyMatchedAtLevel = 0;
      this.optimalAskPrice = 0;
      this.optimalAskIndex = 0;
      this.optimalBidIndex = 0;

      getTriggeredBuyLimitList(0, bidsList);
      getTriggeredSellLimitList(Integer.MAX_VALUE, asksList);
      final long[] cumAskQtyArr = new long[asksList.size()];


      // cumulative ask qty
      for (int i = 0; i < asksList.size(); i++) {
        final Order ask = asksList.get(i);
        if (i == 0)
          cumAskQtyArr[i] = ask.getQuantityLong();
        else
          cumAskQtyArr[i] = cumAskQtyArr[i - 1] + ask.getQuantityLong();

        // calc cumulative bid qty for that ask
        int j = 0;
        long cumBidQty = 0;
        for (; j < bidsList.size(); j++) {
          final Order bid = bidsList.get(j);
          if (bid.getOrdType() != OrdType.MARKET && bid.getPriceInt() < ask.getPriceInt())
            break;

          cumBidQty += bid.getQuantityLong();
        }

        final long qtyMatchedAtLevel = Math.min(cumAskQtyArr[i], cumBidQty);
        if (qtyMatchedAtLevel > this.maxQtyMatchedAtLevel) {
          this.maxQtyMatchedAtLevel = qtyMatchedAtLevel;
          this.optimalAskIndex = i;
          this.optimalBidIndex = j;
          optimalAskPrice = asksList.get(i).getPriceInt();
        }
      }

      calcPriceBounds(orderBook);

      lastCalculatedPriceTime = System.currentTimeMillis();
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    LOGGER.info(LOG_FMT_1, "<< calcAuctionPrice, " + this);
    return optimalAskPrice;
  }

  private final void calcPriceBounds(final OrderBook orderBook) {
    final InstrumentPair instrumentPair = orderBook.getInstrumentPair();
    double usdMark = instrumentPair.getIndexFeedUsdMark();
    if (usdMark == 0) {
      usdMark = orderBook.getUsdMark();
    }
    final double highBound = usdMark * Context.getAuctionHighBound();
    final double lowBound = usdMark * Context.getAuctionLowBound();
    this.highPriceBound = instrumentPair.adjustPriceToScale((long) (highBound * 100), 2);
    this.lowPriceBound = instrumentPair.adjustPriceToScale((long) (lowBound * 100), 2);
  }


  private final void populateOrderBookFromAuctionMap(final ArrayOrderBook orderBook) {
    try {
      orderBook.setPublishAcks(false);
      orderBook.clearOrderBook();

      final List<Order> list = new ArrayList<>(buyTreeSet);
      final List<Order> list2 = new ArrayList<>(sellTreeSet);

      // orderBook.rebuildInProgress = true;
      for (final Order order : list) {
        orderBook.addOrder(order);
      }
      for (final Order order : list2) {
        orderBook.addOrder(order);
      }

      // orderBook.rebuildInProgress = false;

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    orderBook.setPublishAcks(true);
  }

  // sort by smallest to largest
  private static final Comparator<Order> orderComparatorLow = new Comparator<Order>() {
    @Override
    public int compare(final Order order1, final Order order2) {
      try {
        if (order1.getOrdType() == OrdType.MARKET && order2.getOrdType() == OrdType.MARKET) {
          if (order1.getOrderId() == order2.getOrderId()) {
            return 0;
          } else if (order1.getOrderId() > order2.getOrderId())
            return 1;
          else
            return -1;
        } else if (order2.getOrdType() == OrdType.MARKET)
          return 1;
        else if (order1.getOrdType() == OrdType.MARKET)
          return -1;

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
        if (order1.getOrdType() == OrdType.MARKET && order2.getOrdType() == OrdType.MARKET) {
          if (order1.getOrderId() == order2.getOrderId()) {
            return 0;
          } else if (order1.getOrderId() > order2.getOrderId())
            return 1;
          else
            return -1;
        } else if (order2.getOrdType() == OrdType.MARKET)
          return 1;
        else if (order1.getOrdType() == OrdType.MARKET)
          return -1;

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

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    s.append("AuctionContainer [pairId=").append(pair.getId()).append(", maxQtyMatchedAtLevel=").append(maxQtyMatchedAtLevel)
        .append(", optimalAskPrice=").append(optimalAskPrice).append(", optimalAskIndex=").append(optimalAskIndex)
        .append(", highPriceBound=").append(highPriceBound).append(", lowPriceBound=").append(lowPriceBound)
        .append(", AuctionDurationTime=").append(pair.getAuctionDurationTime()).append(", AuctionFixingAttempts=")
        .append(pair.getAuctionFixingAttempts()).append(", AuctionFixingWaitTime=").append(pair.getAuctionFixingWaitTime())
        .append(", AuctionLastStartedTime=").append(pair.getAuctionLastStartedTime()).append(", AuctionLastStoppedTime=")
        .append(pair.getAuctionLastStoppedTime()).append(", AuctionStartTimeHrGMT=").append(pair.getAuctionStartTimeHrGMT())
        .append(", lastOpenedTime=").append(lastOpenedTime).append(", auctionOpenedCount=").append(auctionOpenedCount).append(", pair=")
        .append(pair).append(']');
    return s;
  }

}
