package com.solfini.user;

import java.util.Arrays;

import com.solfini.common.Appendable;
import com.solfini.common.Constants;
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
 */
public class UserOpenOrdersByPair implements Appendable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(UserOpenOrdersByPair.class);
  public static final short UPGRADE_THRESHOLD = 64;

  protected User user;
  protected InstrumentPair pair;
  protected Order[] bids;
  protected Order[] asks;
  protected short bidsCount = 0;
  protected short asksCount = 0;
  protected long bidsQuantity = 0;
  protected long asksQuantity = 0;
  protected double bidsNotional = 0;
  protected double asksNotional = 0;
  protected double maxNotional = 0;
  protected double openOrderRequiredMargin = 0;
  protected double positionRequiredMargin = 0;
  protected boolean markAsReturned = false;

  public UserOpenOrdersByPair() {
    this.bids = new Order[16];
    this.asks = new Order[16];
  }

  public void set(final User user, final InstrumentPair pair) {
    this.user = user;
    this.pair = pair;

    for (int i = 0; i < bids.length; i++) {
      bids[i] = null;
    }
    for (int i = 0; i < asks.length; i++) {
      asks[i] = null;
    }

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

  public Order[] getBids() {
    return bids;
  }

  public void setBids(final Order[] bids) {
    this.bids = bids;
  }

  public Order[] getAsks() {
    return asks;
  }

  public void setAsks(final Order[] asks) {
    this.asks = asks;
  }

  public short getBidsCount() {
    return bidsCount;
  }

  public short getAsksCount() {
    return asksCount;
  }

  public long getBidsQuantity() {
    return bidsQuantity;
  }

  public long getAsksQuantity() {
    return asksQuantity;
  }

  public double getBidsNotional() {
    return bidsNotional;
  }

  public double getAsksNotional() {
    return asksNotional;
  }

  public double getMaxNotional() {
    return maxNotional;
  }

  public double getPositionRequiredMargin() {
    return positionRequiredMargin;
  }

  public void resetMarkAsReturned() {
    markAsReturned = false;
  }

  public void markAsReturned() {
    markAsReturned = true;
  }

  public boolean isMarkAsReturned() {
    return markAsReturned;
  }

  // must be called from matcher thread
  public long getTotalBidsQuantity() {
    long total = 0;
    for (int i = 0; i < bidsCount; i++) {
      if (bids[i] != null)
        total += bids[i].getQuantityLong();
    }
    return total;
  }

  // must be called from matcher thread
  public long getTotalAsksQuantity() {
    long total = 0;
    for (int i = 0; i < asksCount; i++) {
      if (asks[i] != null)
        total += asks[i].getQuantityLong();
    }
    return total;
  }

  public boolean isClOrdIdUsed(final String clorId) {
    for (int i = 0; i < bidsCount; i++) {
      if (bids[i] != null && bids[i].getClOrdId().equals(clorId))
        return true;
    }
    for (int i = 0; i < asksCount; i++) {
      if (asks[i] != null && asks[i].getClOrdId().equals(clorId))
        return true;
    }

    return false;
  }

  public boolean add(final Order order, final int marginCheckReferencePrice) {
    if (Context.isRejectDuplicateClorIdsEnabled() && order.getClOrdId() != null && order.getClOrdId().length() > 1
        && isClOrdIdUsed(order.getClOrdId())) {
      LOGGER.info("Context.isRejectDuplicateClorIdsEnabled() = " + Context.isRejectDuplicateClorIdsEnabled() +
          " order.getClOrdId()= " + order.getClOrdId() + " isClOrdIdUsed: "isClOrdIdUsed(order.getClOrdId()));
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_6, ">> reject ClOrdId order=", order, REFERENCEPRICE_EQ, marginCheckReferencePrice, TOSTRING_EQ, this);
      }
      return false;
    }

    if (Context.getUserOpenOrderLimit() > 0 && bidsCount + asksCount >= Context.getUserOpenOrderLimit()) {
      LOGGER.info("Context.getUserOpenOrderLimit() = " + Context.getUserOpenOrderLimit() + " count: " + (bidsCount + asksCount));
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_6, ">> reject open order count order=", order, REFERENCEPRICE_EQ, marginCheckReferencePrice, TOSTRING_EQ,
            this);
      }
      return false;
    }

    order.setMarginCheckReferencePrice(marginCheckReferencePrice);
    if (Side.BUY == order.getSide()) {
      bids[bidsCount] = order;
      bidsCount++;
      bidsQuantity += order.getQuantityLong();
      bidsNotional += (pair.getPriceScaleFactor() * marginCheckReferencePrice * order.getQuantityLong() * pair.getQuantityScaleFactor());
      if (bidsCount >= bids.length)
        bids = Arrays.copyOf(bids, bids.length + 16);
    } else {
      asks[asksCount] = order;
      asksCount++;
      asksQuantity += order.getQuantityLong();
      asksNotional += (pair.getPriceScaleFactor() * marginCheckReferencePrice * order.getQuantityLong() * pair.getQuantityScaleFactor());
      if (asksCount >= asks.length)
        asks = Arrays.copyOf(asks, asks.length + 16);
    }
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, "<< add order=", order, REFERENCEPRICE_EQ, marginCheckReferencePrice, TOSTRING_EQ, this);
    }

    if (bidsCount > UPGRADE_THRESHOLD || asksCount > UPGRADE_THRESHOLD)
      upgradeToHFT();
    return true;
  }

  public boolean remove(final Order order) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(">> remove order=" + order + TOSTRING_EQ + toString());
    }

    if (Side.BUY == order.getSide()) {
      for (int i = 0; i < bids.length; i++) {
        if (bids[i] == null)
          break;
        if (order.getOrderId() == bids[i].getOrderId()) {
          bidsQuantity -= order.getQuantityLong();
          bidsNotional -=
              (pair.getPriceScaleFactor() * order.getMarginCheckReferencePrice() * order.getQuantityLong() * pair.getQuantityScaleFactor());
          bidsCount--;
          bids[i] = bids[bidsCount];
          bids[bidsCount] = null;
          break;
        }
      }
    } else {
      for (int i = 0; i < asks.length; i++) {
        if (asks[i] == null)
          break;
        if (order.getOrderId() == asks[i].getOrderId()) {
          asksQuantity -= order.getQuantityLong();
          asksNotional -=
              (pair.getPriceScaleFactor() * order.getMarginCheckReferencePrice() * order.getQuantityLong() * pair.getQuantityScaleFactor());
          asksCount--;
          asks[i] = asks[asksCount];
          asks[asksCount] = null;
          break;
        }
      }
    }
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("<< remove order=" + order + TOSTRING_EQ + toString());
    }
    return true;
  }

  public void update(final Order order, final int referencePrice, final long referenceQuantity) {
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

  // changed to return - for asks
  public double calcMaxNotional(final double posNotional) {
    final double maxBidsNotional = Math.abs(posNotional + bidsNotional);
    final double maxAsksNotional = Math.abs(posNotional - asksNotional);
    maxNotional = Math.max(maxBidsNotional, maxAsksNotional);

    if (maxAsksNotional > maxBidsNotional)
      return -maxAsksNotional;
    else
      return maxBidsNotional;
  }

  public double calcNotionalRequiredMargin(final double markPrice) {
    try {
      Position position = user.getPositionArr()[pair.getId()];
      // for some reason, only for DR, sometimes position is null when it shouldn't be
      if (position == null) {
        LOGGER.warn(LOG_FMT_4, "Warning in calcNotionalRequiredMargin, position was null, pairId=", pair.getId(),
            ", position=", position);
      }

      final double positionQuantity = position == null ? 0 : position.getQuantity() * pair.getQuantityScaleFactor();
      final double positionNotional = markPrice * positionQuantity;

      final double maxNotionalTemp = calcMaxNotional(positionNotional);

      final double requiredMaxMargin = NotionalMarginCalc.calcRequiredMargin(maxNotionalTemp, positionQuantity, markPrice, pair, user);
      positionRequiredMargin = NotionalMarginCalc.calcRequiredMargin(positionNotional, positionQuantity, markPrice, pair, user);
      openOrderRequiredMargin = requiredMaxMargin - positionRequiredMargin;

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      LOGGER.error(LOG_FMT_9, "Error in calcNotionalRequiredMargin markPrice=", markPrice, ", position=",
          user.getPositionArr()[pair.getId()], USER_EQ, user, PAIR_EQ, pair, e);
    }

    return openOrderRequiredMargin;
  }

  public Order lookupOrder(final String clorId, final Side side) {
    if (clorId == null)
      return null;

    if (Side.BUY == side || side == null) {
      for (final Order order : getBids()) {
        if (order == null)
          break;
        if (clorId.equals(order.getClOrdId()))
          return order;
      }
    }

    for (final Order order : getAsks()) {
      if (order == null)
        break;
      if (clorId.equals(order.getClOrdId()))
        return order;
    }
    return null;
  }

  public Order lookupOrder(final long orderId, final Side side) {
    if (Side.BUY == side || side == null) {
      for (final Order order : getBids()) {
        if (order == null)
          break;
        if (order.getOrderId() == orderId)
          return order;
      }
    }

    for (final Order order : getAsks()) {
      if (order == null)
        break;
      if (order.getOrderId() == orderId)
        return order;
    }
    return null;
  }

  public Order lookupSecondaryOrder(final long secondaryOrderId, final Side side) {
    if (Side.BUY == side || side == null) {
      for (final Order order : getBids()) {
        if (order == null)
          break;
        if (order.getSecondaryOrderId() == secondaryOrderId)
          return order;
      }
    }

    for (final Order order : getAsks()) {
      if (order == null)
        break;
      if (order.getSecondaryOrderId() == secondaryOrderId)
        return order;
    }
    return null;
  }

  public void clear() {
    this.user = null;
    this.pair = null;
    for (int i = 0; i < bids.length; i++) {
      bids[i] = null;
    }
    for (int i = 0; i < asks.length; i++) {
      asks[i] = null;
    }
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
  public void rebuild() {
    Order[] bidsTemp = Arrays.copyOf(bids, bids.length);
    Order[] asksTemp = Arrays.copyOf(asks, asks.length);
    try {
      set(user, pair);

      for (final Order order : bidsTemp) {
        if ((order != null) && !add(order, order.getMarginCheckReferencePrice())) {
          LOGGER.warn(LOG_FMT_2, "Warning, openOrders rebuild skipped order=", order);
        }
      }
      for (final Order order : asksTemp) {
        if ((order != null) && !add(order, order.getMarginCheckReferencePrice())) {
          LOGGER.warn(LOG_FMT_2, "Warning, openOrders rebuild skipped order=", order);
        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      bids = bidsTemp;
      asks = asksTemp;
    }
  }

  public void upgradeToHFT() {
    LOGGER.warn(LOG_FMT_2, "upgradeToHFT=", this);
    try {
      final HFTUserOpenOrdersByPair hft = new HFTUserOpenOrdersByPair(this);
      final Position position = user.getPosition(pair.getId());
      position.setUserOpenOrdersByPair(hft);
    } catch (Exception e) {
      LOGGER.error("error in upgradeToHFT", e);
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
    return s.append(USEROPENORDERSBYPAIR_USER_EQ).append(user).append(PAIR_EQ).append(pair).append(BIDS_EQ).append(Arrays.toString(bids))
        .append(ASKS_EQ).append(Arrays.toString(asks)).append(BIDSCOUNT_EQ).append(bidsCount).append(ASKSCOUNT_EQ).append(asksCount)
        .append(BIDSQUANTITY_EQ).append(bidsQuantity).append(ASKSQUANTITY_EQ).append(asksQuantity).append(BIDSNOTIONAL_EQ)
        .append(bidsNotional).append(ASKSNOTIONAL_EQ).append(asksNotional).append(MAXNOTIONAL_EQ).append(maxNotional)
        .append(OPENORDERREQUIREDMARGIN_EQ).append(openOrderRequiredMargin).append(POSITIONREQUIREDMARGIN_EQ).append(positionRequiredMargin)
        .append(']');
  }
}
