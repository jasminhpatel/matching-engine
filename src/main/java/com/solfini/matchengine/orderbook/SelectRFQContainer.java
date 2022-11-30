package com.solfini.matchengine.orderbook;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import org.agrona.collections.Long2ObjectHashMap;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.pool.CancelOrderMatchThreadObjectPool;
import com.solfini.sbe.encoder.BusinessRejectReason;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.QuoteType;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.FastArrayList;

/**
 *
 * @author Chris Mack
 *
 */
// RFQ container, sorts bids and asks and matches
public class SelectRFQContainer implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(SelectRFQContainer.class);

  private final InstrumentPair pair;
  private final ConcurrentSkipListSet<Order> buyTreeSet = new ConcurrentSkipListSet<>(orderComparatorHigh); // high to low
  private final ConcurrentSkipListSet<Order> sellTreeSet = new ConcurrentSkipListSet<>(orderComparatorLow); // low to high
  private final Long2ObjectHashMap<Order> idToOrderMap = new Long2ObjectHashMap<>();

  private final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();

  private long maxQtyMatchedAtLevel = 0;
  private int optimalAskPrice = 0;
  private int optimalAskIndex = 0;
  private int optimalBidIndex = 0;
  private long highPriceBound;
  private long lowPriceBound;
  private long lastOpenedTime;
  private int auctionOpenedCount = 0;

  private long lastCalculatedPriceTime = 0;

  public SelectRFQContainer(final InstrumentPair pair) {
    this.pair = pair;
  }

  public final void clear() {
    buyTreeSet.clear();
    sellTreeSet.clear();
    idToOrderMap.clear();
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

  public final void addBuyLimit(final Order order, final SelectArrayOrderBook orderBook) {
    printTreeSet(sellTreeSet, "selectSellTreeSet(before): ");
    printTreeSet(buyTreeSet, "selectBuyTreeSet(before): ");
    // expireOld orders
    expireOld(orderBook);
    // if prev order exists, treat as cancel replace
    final Order prevOrder = idToOrderMap.remove(order.getOrderId());
    if (prevOrder != null) { // cancel replace
      if (prevOrder.getQuoteType() != QuoteType.INDICATIVE) { // once prev order is tradeable don't allow qty to change
        buyTreeSet.remove(prevOrder);
        order.setQuantityLong(prevOrder.getQuantityLong());
        order.setQty(prevOrder.getQty(), prevOrder.getQtyScale());
      }
    }
    // INDICATIVE can't be filled
    if (QuoteType.TRADEABLE != order.getQuoteType() && QuoteType.COUNTER_TRADEABLE != order.getQuoteType()
        && QuoteType.RESTRICTED_TRADEABLE != order.getQuoteType()) {
      idToOrderMap.put(order.getOrderId(), order);
      return;
    }

    if (order.getOrdType() == OrdType.SELECT) { // MATCH SELECT orders
      final Order selectedOrder = idToOrderMap.remove(order.getSelectId());

      boolean isFound = (selectedOrder != null) && (selectedOrder.getQuoteType() == QuoteType.INDICATIVE);

      if (!isFound && (selectedOrder != null) && selectedOrder.getQuoteType() != QuoteType.INDICATIVE) {
        isFound = sellTreeSet.remove(selectedOrder);
      }

      if (!isFound) {
        LOGGER.info(Constants.LOG_FMT_2, "Select Order not found: selectId: " + order.getSelectId());
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.SELECT_ORDER_NOT_FOUND, INVALID_ORDER_SELECT, order.getOrderId(),
            order.getSourceSeqNum(), order.getSelectId(), order.getSecurityId(), order.getSubmitterId()));

        return;
      }

      if (selectedOrder.isRFQ() && selectedOrder.getQuoteType() != QuoteType.INDICATIVE) {
        if (order.getOrdType() == OrdType.MARKET || selectedOrder.getPriceInt() <= order.getPriceInt()) {
          long fillPrice = selectedOrder.getPrice() > 0 ? selectedOrder.getPrice() : order.getPrice();
          if (order.getOrdType() == OrdType.MARKET) {
            fillPrice = selectedOrder.getPrice();
          }
          fillMatch(selectedOrder, order, fillPrice, orderBook);
          if (selectedOrder.getQuantityLong() > 0) { // if not fill, add selected order back
            idToOrderMap.put(selectedOrder.getOrderId(), selectedOrder);
            sellTreeSet.add(selectedOrder);
          }
        } else {
          idToOrderMap.put(selectedOrder.getOrderId(), selectedOrder);
          sellTreeSet.add(selectedOrder);
        }
      }

      if (selectedOrder.isRFQ() && selectedOrder.getQuoteType() == QuoteType.INDICATIVE) {//keep until order get converted to treadeable
        idToOrderMap.put(selectedOrder.getOrderId(), selectedOrder);
      }

    } else { // sweep orderbook
      //while (!sellTreeSet.isEmpty()) {
      for (final Order selectedOrder : sellTreeSet) {
        if (order.getQty() <= 0)
          break;

        //final Order selectedOrder = sellTreeSet.first();
        if (selectedOrder.getOrdType() == OrdType.SELECT && selectedOrder.getSelectId() != order.getSelectId()) {
          continue;
        }
        // filled
        if (order.getOrdType() == OrdType.MARKET || order.getPriceInt() >= selectedOrder.getPriceInt()) {
          final long fillPrice = selectedOrder.getPrice() > 0 ? selectedOrder.getPrice() : order.getPrice();
          if (fillPrice <= 0)
            break;

          fillMatch(selectedOrder, order, fillPrice, orderBook);
          if (selectedOrder.getQuantityLong() <= 0) {
            idToOrderMap.remove(selectedOrder.getOrderId());
            sellTreeSet.remove(selectedOrder);
          }
        } else {
          break; //sell prices are too high
        }
      }
    }

    // post resting order
    if (order.getQuantityLong() > 0) {
      buyTreeSet.add(order);
      idToOrderMap.put(order.getOrderId(), order);
    }
    printTreeSet(sellTreeSet, "selectSellTreeSet(after): ");
    printTreeSet(buyTreeSet, "selectBuyTreeSet(after): ");
  }

  public final void addSellLimit(final Order order, final SelectArrayOrderBook orderBook) {
    printTreeSet(sellTreeSet, "selectSellTreeSet(before): ");
    printTreeSet(buyTreeSet, "selectBuyTreeSet(before): ");
    // expireOld orders
    expireOld(orderBook);
    // if prev order exists, treat as cancel replace
    final Order prevOrder = idToOrderMap.remove(order.getOrderId());
    if (prevOrder != null) { // cancel replace
      if (prevOrder.getQuoteType() != QuoteType.INDICATIVE) { // once prev order is tradeable don't allow qty to change
        sellTreeSet.remove(prevOrder);
        order.setQuantityLong(prevOrder.getQuantityLong());
        order.setQty(prevOrder.getQty(), prevOrder.getQtyScale());
      }
    }

    // INDICATIVE can't be filled
    if (QuoteType.TRADEABLE != order.getQuoteType() && QuoteType.COUNTER_TRADEABLE != order.getQuoteType()
        && QuoteType.RESTRICTED_TRADEABLE != order.getQuoteType()) {
      idToOrderMap.put(order.getOrderId(), order);
      return;
    }

    if (order.getOrdType() == OrdType.SELECT) { // MATCH SELECT orders
      final Order selectedOrder = idToOrderMap.remove(order.getSelectId());
      boolean isFound = (selectedOrder != null) && (selectedOrder.isRFQ() && selectedOrder.getQuoteType() == QuoteType.INDICATIVE);

      if (!isFound && (selectedOrder != null) && selectedOrder.isRFQ() && selectedOrder.getQuoteType() != QuoteType.INDICATIVE) {
        isFound = buyTreeSet.remove(selectedOrder);
      }

      if (!isFound) {
        LOGGER.info(Constants.LOG_FMT_2, "Select Order not found: selectId: " + order.getSelectId());
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.SELECT_ORDER_NOT_FOUND, INVALID_ORDER_SELECT, order.getOrderId(),
            order.getSourceSeqNum(), order.getSelectId(), order.getSecurityId(), order.getSubmitterId()));

        return;
      }

      if (selectedOrder.isRFQ() && selectedOrder.getQuoteType() != QuoteType.INDICATIVE) {
        if (order.getOrdType() == OrdType.MARKET || selectedOrder.getPriceInt() >= order.getPriceInt()) {
          long fillPrice = order.getPrice() > 0 ? order.getPrice() : selectedOrder.getPrice();
          if (order.getOrdType() == OrdType.MARKET) {
            fillPrice = selectedOrder.getPrice();
          }
          fillMatch(selectedOrder, order, fillPrice, orderBook);
          if (selectedOrder.getQuantityLong() > 0) { // if not fill, add selected order back
            idToOrderMap.put(selectedOrder.getOrderId(), selectedOrder);
            buyTreeSet.add(selectedOrder);
          }
        } else {
          idToOrderMap.put(selectedOrder.getOrderId(), selectedOrder);
          buyTreeSet.add(selectedOrder);
        }
      }
      if (selectedOrder.isRFQ() && selectedOrder.getQuoteType() == QuoteType.INDICATIVE) {//keep until order get converted to treadeable
        idToOrderMap.put(selectedOrder.getOrderId(), selectedOrder);
      }
    } else { // sweep orderbook
      //while (!buyTreeSet.isEmpty()) {
      for (final Order selectedOrder : buyTreeSet) {
        if (order.getQty() <= 0)
          break;

        //final Order selectedOrder = buyTreeSet.pollFirst();
        if (selectedOrder.getOrdType() == OrdType.SELECT && selectedOrder.getSelectId() != order.getSelectId()) {
          continue;
        }
        // filled
        if (order.getOrdType() == OrdType.MARKET || order.getPriceInt() <= selectedOrder.getPriceInt()) {
          final long fillPrice = selectedOrder.getPrice() > 0 ? selectedOrder.getPrice() : order.getPrice();
          if (fillPrice <= 0)
            break;

          fillMatch(selectedOrder, order, fillPrice, orderBook);
          if (selectedOrder.getQuantityLong() <= 0) {
            idToOrderMap.remove(selectedOrder.getOrderId());
            buyTreeSet.remove(selectedOrder);
          }
        } else {
          break; //buy prices are too low
        }
      }
    }

    // post resting order
    if (order.getQuantityLong() > 0) {
      sellTreeSet.add(order);
      idToOrderMap.put(order.getOrderId(), order);
    }
    printTreeSet(sellTreeSet, "selectSellTreeSet(after): ");
    printTreeSet(buyTreeSet, "selectBuyTreeSet(after): ");
  }

  private final boolean fillMatch(final Order makerOrder, final Order takerOrder, final long fillPrice,
      final SelectArrayOrderBook orderBook) {
    final long quantityFilled = Math.min(makerOrder.getQuantityLong(), takerOrder.getQuantityLong());
    if (quantityFilled == 0 || fillPrice == 0)
      return false;

    makerOrder.setQuantityLong(makerOrder.getQuantityLong() - quantityFilled);
    takerOrder.setQuantityLong(takerOrder.getQuantityLong() - quantityFilled);
    orderBook.filled(quantityFilled, makerOrder, takerOrder, makerOrder.getSide() == Side.BUY ? BUY_LIMIT : SELL_LIMIT);
    return true;
  }



  public final boolean removeBuyLimit(final Order order) {
    final Order order2 = idToOrderMap.remove(order.getOrderId());

    final boolean rc = buyTreeSet.remove(order);
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_6, ">>RFQContainer.removeBuyLimit rc=" + rc + ", order=", order);
    }
    return rc || order2 != null;
  }

  public final boolean removeSellLimit(final Order order) {
    final Order order2 = idToOrderMap.remove(order.getOrderId());

    final boolean rc = sellTreeSet.remove(order);
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_6, ">>RFQContainer.removeBuyLimit rc=" + rc + ", order=", order);
    }
    return rc || order2 != null;
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

  public final void expireOld(final SelectArrayOrderBook orderBook) {
    final FastArrayList<Order> list = new FastArrayList<>();
    final long now = System.currentTimeMillis();

    // create filtered list
    for (final Order order : idToOrderMap.values()) {
      if (order.getExpireTime() > 0 && now > order.getExpireTime())
        list.add(order);
    }

    // cancel list
    for (final Order order : list) {
      final long cancelId = order.getOrderId();
      final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
      cancelOrder.set(order, cancelId, cancelId);
      cancelOrder.setUser(order.getUser());
      orderBook.cancelOrder(cancelOrder);
    }
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

  public Order getOrder(final long orderId) {
    return idToOrderMap.get(orderId);
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
    s.append("RFQContainer [pairId=").append(pair.getId()).append(", maxQtyMatchedAtLevel=").append(maxQtyMatchedAtLevel)
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

  private void printTreeSet(final Set<Order> set, final String prefix) {
    for (final Order o : set) {
      LOGGER.info("=============" + prefix + " orderId: " + o.getOrderId() + " qty: " + o.getQuantityLong() + " price: "
          + o.getPriceInt() + " selectId: " + o.getSelectId() + " orderType: " + o.getOrdType());
    }
  }
  
}
