package com.solfini.matchengine.model.orderbook;

import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.UserOpenOrdersByPair;

public class OrderBookStopOrderBidsNotionalTest extends OrderBookTest {


  // Add buy stop limit order
  // Add sell limit order with a price equal to the stop price
  // Assert that the stop limit order is triggered but not filled
  // Also assert that bidsnotional/asknotional is unchanged after stop is triggered
  @SuppressWarnings("deprecation")
  @Test
  public void buyStopLimitTriggeredBySellLimitWithEqualPriceWithBidsNotional() {
    UserOpenOrdersByPair openOrders0 = user4.getPosition(pair.getId()).getUserOpenOrdersByPair();
    Assert.assertEquals(0, openOrders0.getBidsNotional(), .1);

    orderBook.addOrder(createStopLimitOrder(1, user4, pair.getId(), 1012, 1015, 500, Side.BUY, TimeInForce.DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1015, orderQty=500, ordStatus=NEW");

    UserOpenOrdersByPair openOrders = user4.getPosition(pair.getId()).getUserOpenOrdersByPair();
    Assert.assertEquals(50.6, openOrders.getBidsNotional(), .1);

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 1015, 600, Side.SELL, TimeInForce.DAY));
    orderBook.addOrder(createOrder(3, user3, pair.getId(), 1015, 100, Side.BUY, TimeInForce.DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1015, orderQty=600, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1015, orderQty=100, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1015, orderQty=100, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1015, orderQty=600, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    assertMessages();

    UserOpenOrdersByPair openOrders2 = user4.getPosition(pair.getId()).getUserOpenOrdersByPair();
    Assert.assertEquals(50.6, openOrders2.getBidsNotional(), .1);
  }

  // Add sell stop limit order
  // Add buy limit order with a price equal to the stop price
  // Assert that the stop limit order is triggered but not filled
  // Also assert that bidsnotional/asknotional is unchanged after stop is triggered
  @Test
  @SuppressWarnings("deprecation")
  public void sellStopLimitTriggeredByBuyLimitWithEqualPriceWithAsksNotional() {
    UserOpenOrdersByPair openOrders0 = user4.getPosition(pair.getId()).getUserOpenOrdersByPair();
    Assert.assertEquals(0, openOrders0.getAsksNotional(), .1);

    orderBook.addOrder(createStopLimitOrder(1, user4, pair.getId(), 1015, 1012, 500, Side.SELL, TimeInForce.DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1015, stopPx=1012, orderQty=500, ordStatus=NEW");

    UserOpenOrdersByPair openOrders = user4.getPosition(pair.getId()).getUserOpenOrdersByPair();
    Assert.assertEquals(50.75, openOrders.getAsksNotional(), .1);

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 1012, 600, Side.BUY, TimeInForce.DAY));
    orderBook.addOrder(createOrder(3, user3, pair.getId(), 1012, 100, Side.SELL, TimeInForce.DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1012, orderQty=600, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1012, orderQty=100, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1012, orderQty=100, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1012, orderQty=600, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1015, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1015, orderQty=500, ordStatus=NEW");
    assertMessages();

    UserOpenOrdersByPair openOrders2 = user4.getPosition(pair.getId()).getUserOpenOrdersByPair();
    Assert.assertEquals(50.75, openOrders2.getAsksNotional(), .1);
  }

  public static void main(String[] args) {
    OrderBookStopOrderBidsNotionalTest test = new OrderBookStopOrderBidsNotionalTest();
    System.out.println("before");
    test.before();
    System.out.println("after");
    System.out.println("testStopLimit done");
  }
}
