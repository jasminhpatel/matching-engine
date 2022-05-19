package com.solfini.user;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.preordercheck.NotionalMarginCalc;
import com.solfini.sbe.encoder.Side;

/**
 *
 * @author Chris Mack
 *
 *         UserOpenOrdersByPair is more efficient for most users
 *
 *         HFTUserOpenOrdersByPair is intended for active users with many open orders
 */
public class HFTUserOpenOrdersByPair extends UserOpenOrdersByPair {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(HFTUserOpenOrdersByPair.class);

  private Map<String, Order> clorIdToBidsMap;
  private Map<String, Order> clorIdToAsksMap;
  private LongObjectHashMap<Order> orderIdToBidsMap;
  private LongObjectHashMap<Order> orderIdToAsksMap;
  private LongObjectHashMap<Order> secondaryOrderIdToBidsMap;
  private LongObjectHashMap<Order> secondaryOrderIdToAsksMap;

  public HFTUserOpenOrdersByPair(final UserOpenOrdersByPair userOpenOrdersByPair) {
    this.user = userOpenOrdersByPair.user;
    this.pair = userOpenOrdersByPair.pair;
    this.bids = userOpenOrdersByPair.bids;
    this.asks = userOpenOrdersByPair.asks;
    this.bidsCount = userOpenOrdersByPair.bidsCount;
    this.asksCount = userOpenOrdersByPair.asksCount;
    this.bidsQuantity = userOpenOrdersByPair.bidsQuantity;
    this.asksQuantity = userOpenOrdersByPair.asksQuantity;
    this.bidsNotional = userOpenOrdersByPair.bidsNotional;
    this.asksNotional = userOpenOrdersByPair.asksNotional;
    this.maxNotional = userOpenOrdersByPair.maxNotional;
    this.openOrderRequiredMargin = userOpenOrdersByPair.openOrderRequiredMargin;
    this.positionRequiredMargin = userOpenOrdersByPair.positionRequiredMargin;
    this.markAsReturned = userOpenOrdersByPair.markAsReturned;

    this.clorIdToBidsMap = new HashMap<>();
    this.clorIdToAsksMap = new HashMap<>();
    this.orderIdToBidsMap = new LongObjectHashMap<>();
    this.orderIdToAsksMap = new LongObjectHashMap<>();
    this.secondaryOrderIdToBidsMap = new LongObjectHashMap<>();
    this.secondaryOrderIdToAsksMap = new LongObjectHashMap<>();

    for (int i = 0; i < bids.length; i++) {
      if (bids[i] != null) {
        if (bids[i].getClOrdId() != null)
          clorIdToBidsMap.put(bids[i].getClOrdId(), bids[i]);
        if (bids[i].getOrderId() > 0)
          orderIdToBidsMap.put(bids[i].getOrderId(), bids[i]);
        if (bids[i].getSecondaryOrderId() > 0)
          secondaryOrderIdToBidsMap.put(bids[i].getSecondaryOrderId(), bids[i]);
      }
    }
    for (int i = 0; i < asks.length; i++) {
      if (asks[i] != null) {
        if (asks[i].getClOrdId() != null)
          clorIdToAsksMap.put(asks[i].getClOrdId(), asks[i]);
        if (asks[i].getOrderId() > 0)
          orderIdToAsksMap.put(asks[i].getOrderId(), asks[i]);
        if (asks[i].getSecondaryOrderId() > 0)
          secondaryOrderIdToAsksMap.put(asks[i].getSecondaryOrderId(), asks[i]);
      }
    }
  }

  @Override
  public void set(final User user, final InstrumentPair pair) {
    this.user = user;
    this.pair = pair;

    clorIdToBidsMap.clear();
    clorIdToAsksMap.clear();
    orderIdToBidsMap.clear();
    orderIdToAsksMap.clear();
    secondaryOrderIdToBidsMap.clear();
    secondaryOrderIdToAsksMap.clear();

    bidsCount = 0;
    asksCount = 0;
    bidsQuantity = 0;
    asksQuantity = 0;
    bidsNotional = 0;
    asksNotional = 0;
    maxNotional = 0;
    openOrderRequiredMargin = 0;
    positionRequiredMargin = 0;
  }

  @Override
  public final Order[] getBids() {
    Order[] arr = new Order[orderIdToBidsMap.size()];
    return orderIdToBidsMap.values().toArray(arr);
  }

  @Override
  public final Order[] getAsks() {
    Order[] arr = new Order[orderIdToAsksMap.size()];
    return orderIdToAsksMap.values().toArray(arr);
  }

  // must be called from matcher thread
  @Override
  public final long getTotalBidsQuantity() {
    long total = 0;
    for (Order order : orderIdToBidsMap.values()) {
      if (order != null)
        total += order.getQuantityLong();
    }
    return total;
  }

  // must be called from matcher thread
  @Override
  public final long getTotalAsksQuantity() {
    long total = 0;
    for (Order order : orderIdToAsksMap.values()) {
      if (order != null)
        total += order.getQuantityLong();
    }
    return total;
  }

  @Override
  public final boolean isClOrdIdUsed(final String clorId) {
    if (clorId == null || clorId.length() == 0)
      return false;

    if (clorIdToBidsMap.containsKey(clorId))
      return true;
    return clorIdToAsksMap.containsKey(clorId);
  }

  @Override
  public final boolean add(final Order order, final int marginCheckReferencePrice) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, ">> add order=", order, REFERENCEPRICE_EQ, marginCheckReferencePrice, TOSTRING_EQ, this);
    }

    if (Context.isRejectDuplicateClorIdsEnabled() && order.getClOrdId() != null && order.getClOrdId().length() > 1
        && isClOrdIdUsed(order.getClOrdId())) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_6, ">> reject ClOrdId order=", order, REFERENCEPRICE_EQ, marginCheckReferencePrice, TOSTRING_EQ, this);
      }
      return false;
    }

    if (Context.getUserOpenOrderLimit() > 0 && bidsCount + asksCount + 1 > Context.getUserOpenOrderLimit()) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_6, ">> reject open order count order=", order, REFERENCEPRICE_EQ, marginCheckReferencePrice, TOSTRING_EQ,
            this);
      }
      return false;
    }

    order.setMarginCheckReferencePrice(marginCheckReferencePrice);
    if (Side.BUY == order.getSide()) {
      // add to all caches
      if (order.getClOrdId() != null)
        clorIdToBidsMap.put(order.getClOrdId(), order);
      if (order.getOrderId() > 0)
        orderIdToBidsMap.put(order.getOrderId(), order);
      if (order.getSecondaryOrderId() > 0)
        secondaryOrderIdToBidsMap.put(order.getSecondaryOrderId(), order);

      bidsCount++;
      bidsQuantity += order.getQuantityLong();
      bidsNotional += (pair.getPriceScaleFactor() * marginCheckReferencePrice * order.getQuantityLong() * pair.getQuantityScaleFactor());
    } else {
      // add to all caches
      if (order.getClOrdId() != null)
        clorIdToAsksMap.put(order.getClOrdId(), order);
      if (order.getOrderId() > 0)
        orderIdToAsksMap.put(order.getOrderId(), order);
      if (order.getSecondaryOrderId() > 0)
        secondaryOrderIdToAsksMap.put(order.getSecondaryOrderId(), order);

      asksCount++;
      asksQuantity += order.getQuantityLong();
      asksNotional += (pair.getPriceScaleFactor() * marginCheckReferencePrice * order.getQuantityLong() * pair.getQuantityScaleFactor());
    }
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, "<< add order=", order, REFERENCEPRICE_EQ, marginCheckReferencePrice, TOSTRING_EQ, this);
    }
    return true;
  }

  // is this the actual order with all ids set?
  @Override
  public final boolean remove(final Order order) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(">> remove order=" + order + TOSTRING_EQ + toString());
    }

    // actual remove
    if (Side.BUY == order.getSide()) {
      bidsQuantity -= order.getQuantityLong();
      bidsNotional -=
          (pair.getPriceScaleFactor() * order.getMarginCheckReferencePrice() * order.getQuantityLong() * pair.getQuantityScaleFactor());
      bidsCount--;

      if (order.getOrderId() > 0)
        orderIdToBidsMap.remove(order.getOrderId());
      if (order.getSecondaryOrderId() > 0)
        secondaryOrderIdToBidsMap.remove(order.getSecondaryOrderId());
      if (order.getClOrdId() != null && order.getClOrdId().length() > 0)
        clorIdToBidsMap.remove(order.getClOrdId());
    } else {
      asksQuantity -= order.getQuantityLong();
      asksNotional -=
          (pair.getPriceScaleFactor() * order.getMarginCheckReferencePrice() * order.getQuantityLong() * pair.getQuantityScaleFactor());
      asksCount--;

      if (order.getOrderId() > 0)
        orderIdToAsksMap.remove(order.getOrderId());
      if (order.getSecondaryOrderId() > 0)
        secondaryOrderIdToAsksMap.remove(order.getSecondaryOrderId());
      if (order.getClOrdId() != null && order.getClOrdId().length() > 0)
        clorIdToAsksMap.remove(order.getClOrdId());
    }


    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("<< remove order=" + order + TOSTRING_EQ + toString());
    }
    return true;
  }

  @Override
  public final void update(final Order order, final int referencePrice, final long referenceQuantity) {
    if (Side.BUY == order.getSide()) {
      bidsQuantity -= referenceQuantity;
      bidsNotional -=
          (pair.getPriceScaleFactor() * order.getMarginCheckReferencePrice() * referenceQuantity * pair.getQuantityScaleFactor());
    } else {
      asksQuantity -= referenceQuantity;
      asksNotional -=
          (pair.getPriceScaleFactor() * order.getMarginCheckReferencePrice() * referenceQuantity * pair.getQuantityScaleFactor());
    }

    // if quantity is 0, remove from open orders
    if (order.getQuantityLong() == 0)
      remove(order);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, ">> update order=", order, TOSTRING_EQ, this);
    }
  }

  @Override
  // changed to return - for asks
  public double calcMaxNotional(final double posNotional) {
    final double maxBidsNotional = Math.abs(posNotional + bidsNotional);
    final double maxAsksNotional = Math.abs(posNotional - asksNotional);

    if (maxAsksNotional > maxBidsNotional)
      return -maxAsksNotional;
    else
      return maxBidsNotional;
  }

  @Override
  public final double calcNotionalRequiredMargin(final double markPrice) {
    try {
      Position position = user.getPositionArr()[pair.getId()];
      // for some reason, only for DR, sometimes position is null when it shouldn't be
      if (position == null) {
        int i = 0;
        for (; i < 1_000_000; i++) {
          Thread.sleep(0);
          synchronized (this) {
            position = user.getPositionArr()[pair.getId()];
            if (position != null)
              break;
          }
        }
        LOGGER.warn(LOG_FMT_6, "Warning in calcNotionalRequiredMargin, position was null, retry=", i, ", pairId=", pair.getId(),
            ", position=", position);
      }


      final double quantity = position == null ? 0 : position.getQuantity() * pair.getQuantityScaleFactor();
      final double positionNotional = markPrice * quantity;

      final double maxNotionalTemp = calcMaxNotional(positionNotional);

      final double requiredMaxMargin = NotionalMarginCalc.calcRequiredMargin(maxNotionalTemp, quantity, markPrice, pair, user);
      positionRequiredMargin = NotionalMarginCalc.calcRequiredMargin(positionNotional, quantity, markPrice, pair, user);
      openOrderRequiredMargin = requiredMaxMargin - positionRequiredMargin;

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      LOGGER.error(LOG_FMT_2, "Error in calcNotionalRequiredMargin markPrice=", markPrice, ", position=",
          user.getPositionArr()[pair.getId()], USER_EQ, user, e);

      final Position position = user.getPositionArr()[pair.getId()];
      LOGGER.error(LOG_FMT_2, "Error in calcNotionalRequiredMargin1 pair.getId()=", pair.getId());
      LOGGER.error(LOG_FMT_2, "Error in calcNotionalRequiredMargin2 pair.getQuantityScaleFactor()=", pair.getQuantityScaleFactor());

      LOGGER.error(LOG_FMT_2, "Error in calcNotionalRequiredMargin3 position=", position);
      LOGGER.error(LOG_FMT_2, "Error in calcNotionalRequiredMargin4 markPrice=", markPrice);
      LOGGER.error(LOG_FMT_2, "Error in calcNotionalRequiredMargin5 position=", position);
      LOGGER.error(LOG_FMT_2, "Error in calcNotionalRequiredMargin6 position.getQuantity()=", position.getQuantity());
      double positionNotional = markPrice * position.getQuantity(); // TODO: exception here
      LOGGER.error(LOG_FMT_2, "Error in calcNotionalRequiredMargin7 position=", positionNotional);
      positionNotional = positionNotional * pair.getQuantityScaleFactor();
      LOGGER.error(LOG_FMT_2, "Error in calcNotionalRequiredMargin8 position=", positionNotional);
    }

    return openOrderRequiredMargin;
  }

  @Override
  public final Order lookupOrder(final long orderId, final Side side) {
    Order order = null;
    if (side != null && Side.BUY == side)
      order = orderIdToBidsMap.get(orderId);

    if (order != null)
      return order;

    return orderIdToAsksMap.get(orderId);
  }

  @Override
  public final Order lookupSecondaryOrder(final long secondaryOrderId, final Side side) {
    Order order = null;
    if (side != null && Side.BUY == side)
      order = secondaryOrderIdToBidsMap.get(secondaryOrderId);

    if (order != null)
      return order;

    return secondaryOrderIdToAsksMap.get(secondaryOrderId);
  }

  @Override
  public Order lookupOrder(final String clorId, final Side side) {
    if (clorId == null)
      return null;

    Order order = null;
    if (side != null && Side.BUY == side)
      order = clorIdToBidsMap.get(clorId);
    if (order != null)
      return order;

    return clorIdToAsksMap.get(clorId);
  }

  @Override
  public void clear() {
    this.user = null;
    this.pair = null;
    clorIdToBidsMap.clear();
    clorIdToAsksMap.clear();
    orderIdToBidsMap.clear();
    orderIdToAsksMap.clear();
    secondaryOrderIdToBidsMap.clear();
    secondaryOrderIdToAsksMap.clear();
    bidsCount = 0;
    asksCount = 0;
    bidsQuantity = 0;
    asksQuantity = 0;
    bidsNotional = 0;
    asksNotional = 0;
    maxNotional = 0;
    openOrderRequiredMargin = 0;
    positionRequiredMargin = 0;
  }

  // must be called from matching thread
  // rebuild state and recalc
  @Override
  public void rebuild() {
    try {
      clorIdToBidsMap.clear();
      clorIdToAsksMap.clear();
      secondaryOrderIdToBidsMap.clear();
      secondaryOrderIdToAsksMap.clear();

      bidsCount = 0;
      asksCount = 0;
      bidsQuantity = 0;
      asksQuantity = 0;
      bidsNotional = 0;
      asksNotional = 0;
      maxNotional = 0;
      openOrderRequiredMargin = 0;
      positionRequiredMargin = 0;

      for (Order order : orderIdToBidsMap.values()) {
        // add to all caches
        if (order.getClOrdId() != null)
          clorIdToBidsMap.put(order.getClOrdId(), order);
        if (order.getSecondaryOrderId() > 0)
          secondaryOrderIdToBidsMap.put(order.getSecondaryOrderId(), order);

        bidsCount++;
        bidsQuantity += order.getQuantityLong();
        bidsNotional +=
            (pair.getPriceScaleFactor() * order.getMarginCheckReferencePrice() * order.getQuantityLong() * pair.getQuantityScaleFactor());
      }
      for (Order order : orderIdToAsksMap.values()) {

        // add to all caches
        if (order.getClOrdId() != null)
          clorIdToAsksMap.put(order.getClOrdId(), order);
        if (order.getSecondaryOrderId() > 0)
          secondaryOrderIdToAsksMap.put(order.getSecondaryOrderId(), order);

        asksCount++;
        asksQuantity += order.getQuantityLong();
        asksNotional +=
            (pair.getPriceScaleFactor() * order.getMarginCheckReferencePrice() * order.getQuantityLong() * pair.getQuantityScaleFactor());
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    return s.append(USEROPENORDERSBYPAIR_USER_EQ).append(user).append(PAIR_EQ).append(pair).append(BIDS_EQ)
        .append(orderIdToBidsMap.values()).append(ASKS_EQ).append(orderIdToAsksMap.values()).append(BIDSCOUNT_EQ).append(bidsCount)
        .append(ASKSCOUNT_EQ).append(asksCount).append(BIDSQUANTITY_EQ).append(bidsQuantity).append(ASKSQUANTITY_EQ).append(asksQuantity)
        .append(BIDSNOTIONAL_EQ).append(bidsNotional).append(ASKSNOTIONAL_EQ).append(asksNotional).append(MAXNOTIONAL_EQ)
        .append(maxNotional).append(OPENORDERREQUIREDMARGIN_EQ).append(openOrderRequiredMargin).append(POSITIONREQUIREDMARGIN_EQ)
        .append(positionRequiredMargin).append(']');
  }
}
