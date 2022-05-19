package com.solfini.matchengine.model.risk;

import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

import com.solfini.common.Context;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.UserOpenOrdersByPair;

public class UserOpenOrderLimitTest extends RiskTest {

  @Override
  protected void onConfigure(final Properties properties) {
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "500000");
    properties.setProperty("USER_OPEN_ORDER_LIMIT", "10");
  }

  // Add orders beyond limit
  @Test
  public void openOrdersBeyondLimitFails() {
    Context.setRejectDuplicateClorIdsEnabled(true);

    final UserOpenOrdersByPair userOpenOrdersByPair = new UserOpenOrdersByPair();
    userOpenOrdersByPair.set(user, pair);

    for (int i = 1; i <= 10; i++) {
      Order order = createOrder(i, user, pair.getId(), 495000, 10000, i % 2 == 0 ? Side.BUY : Side.SELL, TimeInForce.DAY);
      order.setClOrdId("clOrdId" + i);
      Assert.assertTrue(userOpenOrdersByPair.add(order, 495000));
    }

    Order reject = createOrder(11, user, pair.getId(), 495000, 10000, Side.SELL, TimeInForce.DAY);
    reject.setClOrdId("clOrdId11");
    Assert.assertFalse(userOpenOrdersByPair.add(reject, 495000));
  }
}
