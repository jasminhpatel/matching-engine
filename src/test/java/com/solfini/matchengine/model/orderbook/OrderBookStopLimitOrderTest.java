package com.solfini.matchengine.model.orderbook;

import com.solfini.sbe.encoder.Side;
import org.junit.Test;

import com.solfini.common.Constants;
import com.solfini.matchengine.message.internal.Order;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookStopLimitOrderTest extends OrderBookTest {

  // Add buy order, assert
  @Test
  public void addBuyOrder() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add sell order, assert
  @Test
  public void addSellOrder() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy order without user, assert business reject
  @Test
  public void rejectBuyOrderWithNoUser() {
    Order order = createStopLimitOrder(1, null, pair.getId(), 1012, 1010, 500, Side.BUY, DAY);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=USER_NOT_FOUND, text=User not found");
    assertMessages();
  }

  // Add sell order without user, assert business reject
  @Test
  public void rejectSellOrderWithNoUser() {
    Order order = createStopLimitOrder(1, null, pair.getId(), 1010, 1012, 500, Side.SELL, DAY);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=USER_NOT_FOUND, text=User not found");
    assertMessages();
  }

  // Add buy order with invalid user, assert business reject
  @Test
  public void rejectBuyOrderWithInvalidUser() {
    Order order = createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY);
    order.setAccount(10000);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=UNABLE_TO_LOAD_USER, text=Unable to load user");
    assertMessages();
  }

  // Add sell order with invalid user, assert business reject
  @Test
  public void rejectSellOrderWithInvalidUser() {
    Order order = createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY);
    order.setAccount(10000);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=UNABLE_TO_LOAD_USER, text=Unable to load user");
    assertMessages();
  }

  // Add buy stop limit order without price, assert business reject
  @Test
  public void rejectBuyOrderWithNoPrice() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 0, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, businessRejectReason=STOP_PRICE_IS_MISSING, text=Stop price is missing");
    assertMessages();
  }

  // Add sell stop limit order without price, assert business reject
  @Test
  public void rejectSellOrderWithNoPrice() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 0, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, businessRejectReason=STOP_PRICE_IS_MISSING, text=Stop price is missing");
    assertMessages();
  }

  // Add buy stop limit order without quantity, assert business reject
  @Test
  public void rejectBuyOrderWithNoQuantity() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 0, Side.BUY, DAY));
    expectMessage("orderId=1, businessRejectReason=QUANTITY_IS_MISSING, text=Quantity is missing");
    assertMessages();
  }

  // Add sell stop limit order without quantity, assert business reject
  @Test
  public void rejectSellOrderWithNoQuantity() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 0, Side.SELL, DAY));
    expectMessage("orderId=1, businessRejectReason=QUANTITY_IS_MISSING, text=Quantity is missing");
    assertMessages();
  }

  // Add buy stop limit missing stop price, assert business reject
  @Test
  public void rejectBuyOrderNoStopPrice() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 0, 500, Side.BUY, DAY));
    expectMessage("orderId=1, businessRejectReason=STOP_PRICE_IS_MISSING, text=Stop price is missing, businessRejectRefID=1");
    assertMessages();
  }

  // Add sell stop limit missing stop price, assert business reject
  @Test
  public void rejectSellOrderNoStopPrice() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 0, 500, Side.SELL, DAY));
    expectMessage("orderId=1, businessRejectReason=STOP_PRICE_IS_MISSING, text=Stop price is missing, businessRejectRefID=1");
    assertMessages();
  }

  // Add buy stop limit order
  // Add sell limit order with a price lower than the stop price
  // Assert that the stop limit order is not triggered
  @Test
  public void buyStopLimitNotTriggeredBySellLimitWithLowerPrice() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1008, 100, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1008, orderQty=100, ordStatus=NEW");
    assertMessages();
  }

  // Add sell stop limit order
  // Add buy limit order with a price higher than the stop price
  // Assert that the stop limit order is not triggered
  @Test
  public void sellStopLimitNotTriggeredByBuyLimitWithHigherPrice() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1014, 100, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1014, orderQty=100, ordStatus=NEW");
    assertMessages();
  }

  // Add sell limit order
  // Add buy stop limit order with a stop price higher than the limit order price
  // Assert that the stop limit order is not triggered
  @Test
  public void buyStopLimitNotTriggeredBySellLimitOnBookWithLowerPrice() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1008, 100, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1008, orderQty=100, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy limit order
  // Add sell stop limit order with a stop price lower than the limit order price
  // Assert that the stop limit order is not triggered
  @Test
  public void sellStopLimitNotTriggeredByBuyLimitOnBookWithHigherPrice() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1014, 100, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1014, orderQty=100, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy stop limit order
  // Add limit orders that trade at a price equal to the stop price
  // Assert that the stop limit order is triggered and filled
  @SuppressWarnings("deprecation")
  @Test
  public void buyStopLimitTriggeredBySellLimitWithEqualPrice() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 1010, 1000, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=1000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user2, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=1000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=1000, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell stop limit order
  // Add limit orders that trade at a price equal to the stop price
  // Assert that the stop limit order is triggered and filled
  @Test
  public void sellStopLimitTriggeredByBuyLimitWithEqualPrice() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 1012, 1000, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1012, orderQty=1000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user2, pair.getId(), 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1012, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1012, orderQty=1000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1012, orderQty=1000, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell limit order with stop price equal to the limit order price
  // Add buy stop limit order
  // Assert that the stop limit order is not triggered
  @Test
  public void buyStopLimitNotTriggeredBySellLimitOnBookWithEqualPrice() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy limit order order with stop price equal to the limit order price
  // Add sell stop limit
  // Assert that the stop limit order is not triggered
  @Test
  public void sellStopLimitNotTriggeredByBuyLimitOnBookWithEqualPrice() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1012, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy stop limit order
  // Add limit order that trade at a price higher than the stop price
  // Assert that the stop limit order is triggered and filled
  @Test
  public void buyStopLimitTriggeredBySellLimitWithHigherPrice1() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 1000, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, ordStatus=FILLED");
    assertMessages();
  }

  // Add buy stop limit order
  // Add limit orders that trade at a price higher than the stop price
  // Assert that the stop limit order is triggered and filled
  @Test
  public void buyStopLimitTriggeredBySellLimitWithHigherPrice2() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1012, 1000, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=1000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1012, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=1000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=1000, ordStatus=FILLED");
    assertMessages();
  }

  // Add buy stop limit order
  // Add limit orders that trade at a price higher than the stop price
  // Assert that the stop limit order is triggered and not filled
  @Test
  public void buyStopLimitTriggeredBySellLimitWithHigherPrice3() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1014, 1000, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1014, orderQty=1000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1014, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1014, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1014, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1014, orderQty=1000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add sell stop limit order
  // Add limit orders that trade at a price lower than the stop price
  // Assert that the stop limit order is triggered and filled
  @Test
  public void sellStopLimitTriggeredByBuyLimitWithLowerPrice1() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 1000, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell stop limit order
  // Add limit orders that trade at a price lower than the stop price
  // Assert that the stop limit order is triggered and filled
  @Test
  public void sellStopLimitTriggeredByBuyLimitWithLowerPrice2() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 1000, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=1000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=1000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=1000, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell stop limit order
  // Add limit orders that trade at a price lower than the stop price
  // Assert that the stop limit order is triggered but not filled
  @Test
  public void sellStopLimitTriggeredByBuyLimitWithLowerPrice3() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1008, 1000, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1008, orderQty=1000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1008, 500, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1008, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1008, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1008, orderQty=1000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add sell limit order with stop price higher than the limit order price
  // Add buy stop limit order
  // Assert that the stop limit order is not triggered
  @Test
  public void buyStopLimitNotTriggeredBySellLimitOnBookWithHigherPrice1() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add sell limit order with stop price higher than the limit order price
  // Add buy stop limit order
  // Assert that the stop limit order is not triggered
  @Test
  public void buyStopLimitNotTriggeredBySellLimitOnBookWithHigherPrice2() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add sell limit order with stop price higher than the limit order price
  // Add buy stop limit order
  // Assert that the stop limit order is not triggered
  @Test
  public void buyStopLimitNotTriggeredBySellLimitOnBookWithHigherPrice3() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1014, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1014, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy limit order order with stop price lower than the limit order price
  // Add sell stop limit
  // Assert that the stop limit order is not triggered
  @Test
  public void sellStopLimitNotTriggeredByBuyLimitOnBookWithLowerPrice1() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy limit order order with stop price lower than the limit order price
  // Add sell stop limit
  // Assert that the stop limit order is not triggered
  @Test
  public void sellStopLimitNotTriggeredByBuyLimitOnBookWithLowerPrice2() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy limit order order with stop price lower than the limit order price
  // Add sell stop limit
  // Assert that the stop limit order is not triggered
  @Test
  public void sellStopLimitNotTriggeredByBuyLimitOnBookWithLowerPrice3() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1008, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1008, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy stop limit order and trigger it without filling
  // Add a sell limit order, assert fill of stop limit order and fill of limit order
  @Test
  public void buyStopLimitTriggeredFilledByNewSellFilled() {
    buyStopLimitTriggeredBySellLimitWithHigherPrice3();

    orderBook.addOrder(createOrder(4, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    assertMessages();
  }

  // Add buy stop limit order and trigger it without filling
  // Add a sell limit order, assert fill of stop limit order and partial fill of limit order
  @Test
  public void buyStopLimitTriggeredFilledByNewSellPartiallyFilled() {
    buyStopLimitTriggeredBySellLimitWithHigherPrice3();

    orderBook.addOrder(createOrder(4, user, pair.getId(), 1012, 800, Side.SELL, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1012, orderQty=800, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1012, orderQty=800, leavesQty=300, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    assertMessages();
  }

  // Add buy stop limit order and trigger it without filling
  // Add a sell limit order, assert partial fill of stop limit order and fill of limit order
  @Test
  public void buyStopLimitTriggeredPartiallyFilledByNewSellFilled() {
    buyStopLimitTriggeredBySellLimitWithHigherPrice3();

    orderBook.addOrder(createOrder(4, user, pair.getId(), 1012, 300, Side.SELL, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1012, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1012, orderQty=300, leavesQty=0, cumQty=300, ordStatus=FILLED");
    expectMessage(
        "origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, leavesQty=200, cumQty=300, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add sell stop limit order and trigger it without filling
  // Add a buy limit order, assert fill of stop limit order and fill of limit order
  @Test
  public void sellStopLimitTriggeredFilledByNewBuyFilled() {
    sellStopLimitTriggeredByBuyLimitWithLowerPrice3();

    orderBook.addOrder(createOrder(4, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell stop limit order and trigger it without filling
  // Add a buy limit order, assert fill of stop limit order and partial fill of limit order
  @Test
  public void sellStopLimitTriggeredFilledByNewBuyPartiallyFilled() {
    sellStopLimitTriggeredByBuyLimitWithLowerPrice3();

    orderBook.addOrder(createOrder(4, user, pair.getId(), 1010, 800, Side.BUY, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=800, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=300, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell stop limit order and trigger it without filling
  // Add a buy limit order, assert partial fill of stop limit order and fill of limit order
  @Test
  public void sellStopLimitTriggeredPartiallyFilledByNewBuyFilled() {
    sellStopLimitTriggeredByBuyLimitWithLowerPrice3();

    orderBook.addOrder(createOrder(4, user, pair.getId(), 1010, 300, Side.BUY, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=0, cumQty=300, ordStatus=FILLED");
    expectMessage(
        "origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=200, cumQty=300, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add buy stop limit order
  // Add sell limit order that will trigger the stop limit order and fill
  // Assert fill of both orders
  @Test
  public void buyStopLimitTriggeredAndFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1012, 1000, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=1000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1012, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=1000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=1000, leavesQty=0, cumQty=1000, ordStatus=FILLED");
    assertMessages();
  }

  // Add buy stop limit order
  // Add sell limit order that will trigger the stop limit order and fill
  // Assert fill of stop limit order and partial fill of limit order
  @Test
  public void buyStopLimitTriggeredAndFilledLimitOrderPartiallyFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1012, 1300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=1300, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1012, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=1300, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=1300, leavesQty=300, cumQty=1000, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add buy stop limit order
  // Add limit orders that will trigger the stop limit order and fill
  // Assert partial fill of stop limit order
  @Test
  public void buyStopLimitTriggeredAndPartiallyFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1012, 800, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=800, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1012, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=800, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage(
        "origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, leavesQty=200, cumQty=300, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=800, leavesQty=0, cumQty=800, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell stop limit order
  // Add buy limit order that will trigger the stop limit order and fill
  // Assert fill of both orders
  @Test
  public void sellStopLimitPriceHitButNotTriggeredAndFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add sell stop limit order
  // Add buy limit order that will trigger the stop limit order and fill
  // Assert fill of both orders
  @Test
  public void sellStopLimitTriggeredAndFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");

    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");


    assertMessages();
  }

  // Add sell stop limit order
  // Add limit orders that trigger the stop limit order and fill
  // Assert fill of stop limit order
  @Test
  public void sellStopLimitTriggeredAndFilledLimitOrderPartiallyFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 1300, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=1300, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=1300, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");

    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=1300, leavesQty=300, cumQty=1000, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add sell stop limit order
  // Add limit orders that will trigger the stop limit order and fill
  // Assert partial fill of stop limit order
  @Test
  public void sellStopLimitTriggeredAndPartiallyFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 800, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=800, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=800, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage(
        "origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=200, cumQty=300, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=0, cumQty=800, ordStatus=FILLED");
    assertMessages();
  }

  // Add buy stop limit order
  // Add limit orders that will trigger the stop limit order and fill
  // Assert fill of both orders
  @Test
  public void buyStopLimitTriggeredCrossedAndFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 1000, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=1000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=1000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=1000, leavesQty=0, cumQty=1000, ordStatus=FILLED");
    assertMessages();
  }

  // Add buy stop limit order
  // Add limit orders that will trigger the stop limit order and fill
  // Assert fill of stop limit order and partial fill of limit order
  @Test
  public void buyStopLimitTriggeredCrossedAndFilledLimitOrderPartiallyFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 1300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=1300, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=1300, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=1300, leavesQty=300, cumQty=1000, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add buy stop limit order
  // Add sell limit order that will trigger the stop limit order and fill
  // Assert partial fill of stop limit order and fill of limit order
  @Test
  public void buyStopLimitTriggeredCrossedAndPartiallyFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(7, user, pair.getId(), 1010, 100, Side.BUY, DAY));
    expectMessage("orderId=7, ordType=LIMIT, side=BUY, price=1010, orderQty=100, ordStatus=NEW");

    orderBook.addOrder(createOrder(8, user, pair.getId(), 1010, 100, Side.SELL, DAY));
    expectMessage("orderId=8, ordType=LIMIT, side=SELL, price=1010, orderQty=100, ordStatus=NEW");
    expectMessage("orderId=8, ordType=LIMIT, side=SELL, price=1010, orderQty=100, ordStatus=FILLED");
    expectMessage("orderId=7, ordType=LIMIT, side=BUY, price=1010, orderQty=100, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED"); // trade triggers stop limit
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");


    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, cumQty=300, ordStatus=FILLED");
    expectMessage(
        "origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, leavesQty=200, cumQty=300, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add sell stop limit order
  // Add limit orders that will trigger the stop limit order and fill
  @Test
  public void sellStopLimitTriggeredCrossedAndFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 1000, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=0, cumQty=1000, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell stop limit order
  // Add buy limit order that will trigger the stop limit order and fill
  // Assert fill of stop limit order and partial fill of limit order
  @Test
  public void sellStopLimitTriggeredCrossedAndFilledLimitOrderPartiallyFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 1300, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=1300, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=1300, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=1300, leavesQty=300, cumQty=1000, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add sell stop limit order
  // Add buy limit order that will trigger the stop limit order and fill
  // Assert partial fill of stop limit order and fill of limit order
  @Test
  public void sellStopLimitTriggeredCrossedAndPartiallyFilled() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 800, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=800, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=800, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage(
        "origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=200, cumQty=300, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=800, leavesQty=0, cumQty=800, ordStatus=FILLED");
    assertMessages();
  }

  @Test
  public void buyStopLimitsTriggeredBySellLimitWhenBetterBuyLimitsAreAvailable1() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 100, 100, 500, Side.BUY, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, stopPx=100, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 100, 800, Side.SELL, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=800, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=800, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=800, ordStatus=FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=PARTIALLY_FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=NEW");
    }

    assertMessages();
  }

  @Test
  public void buyStopLimitsTriggeredBySellLimitWhenBetterBuyLimitsAreAvailable2() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 100, 100, 500, Side.BUY, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, stopPx=100, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 100, 1000, Side.SELL, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=1000, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=1000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=1000, ordStatus=FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=NEW");
    }

    assertMessages();
  }

  @Test
  public void buyStopLimitsTriggeredBySellLimitWhenBetterBuyLimitsAreAvailable3() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 100, 100, 500, Side.BUY, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, stopPx=100, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 100, 2000, Side.SELL, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=2000, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=2000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=2000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=NEW");
      if (i < 1) {
        expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=FILLED");
        expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=2000, ordStatus=PARTIALLY_FILLED");
      } else if (i < 2) {
        expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=FILLED");
        expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=2000, ordStatus=FILLED");
      }
    }

    assertMessages();
  }

  @Test
  public void buyStopLimitsTriggeredBySellLimitWhenBetterBuyLimitsAreAvailable4() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 100, 100, 500, Side.BUY, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, stopPx=100, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 100, 2100, Side.SELL, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=2100, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=2100, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=2100, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=NEW");
      if (i < 2) {
        expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=FILLED");
        expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=2100, ordStatus=PARTIALLY_FILLED");
      } else if (i < 3) {
        expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=PARTIALLY_FILLED");
        expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=2100, ordStatus=FILLED");
      }
    }

    assertMessages();
  }

  @Test
  public void buyStopLimitsTriggeredBySellLimitWhenBetterBuyLimitsAreAvailable5() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 100, 100, 500, Side.BUY, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, stopPx=100, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 100, 3500, Side.SELL, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=3500, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=3500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=3500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=NEW");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=FILLED");
      if (i < 4) {
        expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=3500, ordStatus=PARTIALLY_FILLED");
      } else {
        expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=3500, ordStatus=FILLED");
      }
    }

    assertMessages();
  }

  @Test
  public void buyStopLimitsTriggeredBySellLimitWhenBetterBuyLimitsAreAvailable6() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 100, 100, 500, Side.BUY, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, stopPx=100, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 100, 5000, Side.SELL, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=5000, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=5000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=5000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=NEW");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=FILLED");
      expectMessage("orderId=92, ordType=LIMIT, side=SELL, price=100, orderQty=5000, ordStatus=PARTIALLY_FILLED");
    }

    assertMessages();
  }

  @Test
  public void buyStopLimitsTriggeredBySellMarketWhenBetterBuyLimitsAreAvailable() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 1000, 500, Side.BUY, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 100, 100, 500, Side.BUY, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, stopPx=100, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createMarketOrder(192, user, pair.getId(), 5000, Side.SELL, DAY));
    expectMessage("orderId=192, ordType=MARKET, side=SELL, orderQty=5000, ordStatus=NEW");

    expectMessage("orderId=192, ordType=MARKET, side=SELL, orderQty=5000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=192, ordType=MARKET, side=SELL, orderQty=5000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=BUY, price=1000, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=192, ordType=MARKET, side=SELL, orderQty=5000, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=192, ordType=MARKET, side=SELL, ordStatus=CANCELED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=BUY, price=100, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=BUY, price=100, orderQty=500, ordStatus=NEW");
    }

    assertMessages();
  }

  @Test
  public void sellStopLimitsTriggeredByBuyLimitWhenBetterSellLimitsAreAvailable1() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 1000, 1000, 500, Side.SELL, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, stopPx=1000, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 1000, 800, Side.BUY, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=800, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=800, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=800, ordStatus=FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=PARTIALLY_FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=NEW");
    }

    assertMessages();
  }

  @Test
  public void sellStopLimitsTriggeredByBuyLimitWhenBetterSellLimitsAreAvailable2() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 1000, 1000, 500, Side.SELL, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, stopPx=1000, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 1000, 1000, Side.BUY, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=1000, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=1000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=1000, ordStatus=FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=NEW");
    }

    assertMessages();
  }

  @Test
  public void sellStopLimitsTriggeredByBuyLimitWhenBetterSellLimitsAreAvailable3() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 1000, 1000, 500, Side.SELL, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, stopPx=1000, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 1000, 2000, Side.BUY, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=2000, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=2000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=2000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=NEW");
      if (i < 1) {
        expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=FILLED");
        expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=2000, ordStatus=PARTIALLY_FILLED");
      } else if (i < 2) {
        expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=FILLED");
        expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=2000, ordStatus=FILLED");
      }
    }

    assertMessages();
  }

  @Test
  public void sellStopLimitsTriggeredByBuyLimitWhenBetterSellLimitsAreAvailable4() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 1000, 1000, 500, Side.SELL, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, stopPx=1000, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 1000, 2100, Side.BUY, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=2100, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=2100, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=2100, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=NEW");
      if (i < 2) {
        expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=FILLED");
        expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=2100, ordStatus=PARTIALLY_FILLED");
      } else if (i < 3) {
        expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=PARTIALLY_FILLED");
        expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=2100, ordStatus=FILLED");
      }
    }

    assertMessages();
  }

  @Test
  public void sellStopLimitsTriggeredByBuyLimitWhenBetterSellLimitsAreAvailable5() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 1000, 1000, 500, Side.SELL, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, stopPx=1000, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 1000, 3500, Side.BUY, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=3500, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=3500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=3500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=NEW");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=FILLED");
      if (i < 4) {
        expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=3500, ordStatus=PARTIALLY_FILLED");
      } else {
        expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=3500, ordStatus=FILLED");
      }
    }

    assertMessages();
  }

  @Test
  public void sellStopLimitsTriggeredByBuyLimitWhenBetterSellLimitsAreAvailable6() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 1000, 1000, 500, Side.SELL, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, stopPx=1000, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createOrder(92, user, pair.getId(), 1000, 5000, Side.BUY, DAY));
    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=5000, ordStatus=NEW");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=5000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=5000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=NEW");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=FILLED");
      expectMessage("orderId=92, ordType=LIMIT, side=BUY, price=1000, orderQty=5000, ordStatus=PARTIALLY_FILLED");
    }

    assertMessages();
  }

  @Test
  public void sellStopLimitsTriggeredByBuyMarketWhenBetterSellLimitsAreAvailable() {

    orderBook.addOrder(createOrder(90, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(91, user, pair.getId(), 100, 500, Side.SELL, DAY));
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=NEW");

    for (int i = 0; i < 5; i++) {
      orderBook.addOrder(createStopLimitOrder(100 + i, user, pair.getId(), 1000, 1000, 500, Side.SELL, DAY));
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, stopPx=1000, orderQty=500, ordStatus=NEW");
    }

    orderBook.addOrder(createMarketOrder(292, user, pair.getId(), 5000, Side.BUY, DAY));
    expectMessage("orderId=292, ordType=MARKET, side=BUY, orderQty=5000, ordStatus=NEW");

    expectMessage("orderId=292, ordType=MARKET, side=BUY, orderQty=5000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=90, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=292, ordType=MARKET, side=BUY, orderQty=5000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=91, ordType=LIMIT, side=SELL, price=100, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=292, ordType=MARKET, side=BUY, orderQty=5000, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=292, ordType=MARKET, ordStatus=CANCELED");

    for (int i = 0; i < 5; i++) {
      expectMessage("orderId=" + (100 + i) + ", ordType=STOP_LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=EXPIRED");
      expectMessage("origOrderId=" + (100 + i) + ", ordType=LIMIT, side=SELL, price=1000, orderQty=500, ordStatus=NEW");
    }

    assertMessages();
  }

  // Buy stop limits should trigger in stop price order (lowest first)
  @Test
  public void buyStopLimitTriggeringOrder1() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1012, 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user2, pair.getId(), 1014, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(4, user2, pair.getId(), 1014, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1014, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1014, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1014, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1014, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=2, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Buy stop limits should trigger in stop price order (lowest first)
  @Test
  public void buyStopLimitTriggeringOrder2() {
    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1012, 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user2, pair.getId(), 1014, 500, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1014, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(4, user2, pair.getId(), 1014, 500, Side.SELL, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1014, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1014, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1014, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=2, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");

    assertMessages();
  }

  // Sell stop limits should trigger in stop price order (highest first)
  @Test
  public void sellStopLimitTriggeringOrder1() {
    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY)); // to be triggered
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1010, 1011, 500, Side.SELL, DAY)); // to be triggered
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(8, user, pair.getId(), 1010, 1011, 500, Side.BUY, DAY)); // not to be triggered
    expectMessage("orderId=8, ordType=STOP_LIMIT, side=BUY, price=1010, stopPx=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user2, pair.getId(), 1008, 500, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1008, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(4, user2, pair.getId(), 1008, 500, Side.BUY, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1008, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1008, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1008, orderQty=500, ordStatus=FILLED");

    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");

    assertMessages();
  }

  // Sell stop limits should trigger in stop price order (highest first)
  @Test
  public void sellStopLimitTriggeringOrder2() {
    orderBook.addOrder(createStopLimitOrder(2, user, pair.getId(), 1010, 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createStopLimitOrder(1, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user2, pair.getId(), 1008, 500, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1008, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(4, user2, pair.getId(), 1008, 500, Side.BUY, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1008, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1008, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1008, orderQty=500, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=STOP_LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");

    assertMessages();
  }

  // Add buy order, assert
  @Test
  public void addTrailingStopMarketBuyOrder() {
    final Order order = createStopLimitOrder(1, user, pair.getId(), 1012, 1010, 500, Side.BUY, DAY);
    order.setTargetStrategy(Constants.TRAILING_STOP_MARKET);
    orderBook.addOrder(order);
    expectMessage(
        "orderId=1, ordType=STOP_LIMIT, side=BUY, price=1012, stopPx=1010, orderQty=500, leavesQty=500, ordStatus=NEW, targetStrategy=170");
    assertMessages();
  }
}
