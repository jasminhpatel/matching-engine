package com.solfini.matchengine.model.risk;

import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

import com.solfini.common.Context;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.HFTUserOpenOrdersByPair;
import com.solfini.user.UserOpenOrdersByPair;

public class UserOpenOrderUpgradeTest extends RiskTest {

  @Override
  protected void onConfigure(final Properties properties) {
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "500000");
  }

  UserOpenOrdersByPair getUpgradedUserOPenOrdersByPair() {
    final UserOpenOrdersByPair userOpenOrdersByPair = new UserOpenOrdersByPair();
    userOpenOrdersByPair.set(user, pair);

    for (int i = 0; i < 200; i++) {
      Order order = createOrder(100 + i, user, pair.getId(), 495000, 10000, i % 2 == 0 ? Side.BUY : Side.SELL, TimeInForce.DAY);
      order.setClOrdId("clOrdIdX" + i);
      Assert.assertTrue(userOpenOrdersByPair.add(order, 495000));
    }

    UserOpenOrdersByPair upgraded = user.getPosition(pair.getId()).getUserOpenOrdersByPair();
    Assert.assertTrue(upgraded instanceof HFTUserOpenOrdersByPair);

    return upgraded;
  }

  // Add buy order, assert clorid is only used once
  @Test
  public void addBuyOrder() {
    Context.setRejectDuplicateClorIdsEnabled(true);

    final UserOpenOrdersByPair userOpenOrdersByPair = getUpgradedUserOPenOrdersByPair();

    Order order1 = createOrder(1, user, pair.getId(), 495000, 10000, Side.BUY, TimeInForce.DAY);
    order1.setClOrdId("clOrdId1");

    Order order2 = createOrder(2, user, pair.getId(), 495000, 10000, Side.BUY, TimeInForce.DAY);
    order2.setClOrdId("clOrdId2");

    boolean added1 = userOpenOrdersByPair.add(order1, 495000);
    boolean added2 = userOpenOrdersByPair.add(order1, 495000);
    boolean added3 = userOpenOrdersByPair.add(order2, 495000);
    boolean added4 = userOpenOrdersByPair.add(order1, 495000);

    Assert.assertTrue(added1);
    Assert.assertFalse(added2);
    Assert.assertTrue(added3);
    Assert.assertFalse(added4);
  }

  // Add sell order, assert clorid is only used once
  @Test
  public void addSellOrder() {
    Context.setRejectDuplicateClorIdsEnabled(true);

    final UserOpenOrdersByPair userOpenOrdersByPair = getUpgradedUserOPenOrdersByPair();

    Order order1 = createOrder(1, user, pair.getId(), 495000, 10000, Side.SELL, TimeInForce.DAY);
    order1.setClOrdId("clOrdId1");

    Order order2 = createOrder(2, user, pair.getId(), 495000, 10000, Side.SELL, TimeInForce.DAY);
    order2.setClOrdId("clOrdId2");

    boolean added1 = userOpenOrdersByPair.add(order1, 495000);
    boolean added2 = userOpenOrdersByPair.add(order1, 495000);
    boolean added3 = userOpenOrdersByPair.add(order2, 495000);
    boolean added4 = userOpenOrdersByPair.add(order1, 495000);

    Assert.assertTrue(added1);
    Assert.assertFalse(added2);
    Assert.assertTrue(added3);
    Assert.assertFalse(added4);
  }

  // Clear open orders
  @Test
  public void clearOpenOrders() {
    Context.setRejectDuplicateClorIdsEnabled(true);

    final UserOpenOrdersByPair userOpenOrdersByPair = getUpgradedUserOPenOrdersByPair();

    Order order1 = createOrder(1, user, pair.getId(), 495000, 10000, Side.BUY, TimeInForce.DAY);
    order1.setClOrdId("clOrdId1");

    Order order2 = createOrder(2, user, pair.getId(), 495000, 10000, Side.SELL, TimeInForce.DAY);
    order2.setClOrdId("clOrdId2");

    Assert.assertTrue(userOpenOrdersByPair.add(order1, 495000));
    Assert.assertTrue(userOpenOrdersByPair.add(order2, 495000));

    Assert.assertEquals(101, userOpenOrdersByPair.getBidsCount());
    Assert.assertEquals(101, userOpenOrdersByPair.getAsksCount());

    userOpenOrdersByPair.clear();
    Assert.assertEquals(0, userOpenOrdersByPair.getBidsCount());
    Assert.assertEquals(0, userOpenOrdersByPair.getAsksCount());
  }

  // Rebuild open order cache
  @Test
  public void rebuild() {
    Context.setRejectDuplicateClorIdsEnabled(true);

    final UserOpenOrdersByPair userOpenOrdersByPair = getUpgradedUserOPenOrdersByPair();

    for (int i = 1; i <= 10; i++) {
        Order order = createOrder(i, user, pair.getId(), 495000, 10000, i % 2 == 0 ? Side.BUY : Side.SELL, TimeInForce.DAY);
        order.setClOrdId("clOrdId" + i);
        Assert.assertTrue(userOpenOrdersByPair.add(order, 495000));
    }

    Assert.assertEquals(105, userOpenOrdersByPair.getBidsCount());
    Assert.assertEquals(105, userOpenOrdersByPair.getAsksCount());
    Assert.assertEquals(1050000, userOpenOrdersByPair.getBidsQuantity());
    Assert.assertEquals(1050000, userOpenOrdersByPair.getAsksQuantity());

    userOpenOrdersByPair.rebuild();
    Assert.assertEquals(105, userOpenOrdersByPair.getBidsCount());
    Assert.assertEquals(105, userOpenOrdersByPair.getAsksCount());
    Assert.assertEquals(1050000, userOpenOrdersByPair.getBidsQuantity());
    Assert.assertEquals(1050000, userOpenOrdersByPair.getAsksQuantity());
  }
}
