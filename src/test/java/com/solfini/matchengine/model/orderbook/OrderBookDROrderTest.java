package com.solfini.matchengine.model.orderbook;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.matchengine.drmode.message.DRCancelOrder;
import com.solfini.matchengine.drmode.message.DROrder;
import com.solfini.pool.DROrderObjectPool;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookDROrderTest extends OrderBookTest {

  @Override
  public void before() {
    super.before();
    Context.setMarketStatus(MarketStatus.DR_MODE);
  }

  @Override
  public void after() {
    Context.setMarketStatus(MarketStatus.OPEN);
    super.after();
  }

  protected DROrder createDROrder(final int orderId, final User user, final int securityId, final long price, final long quantity,
      final Side side, final TimeInForce timeInForce) {
    DROrder order = createDROrder(orderId, user, OrdType.LIMIT, securityId, price, quantity, side, timeInForce);
    if (side == Side.SELL)
      order.setType(Constants.SELL_LIMIT);
    else if (side == Side.BUY)
      order.setType(Constants.BUY_LIMIT);

    return order;
  }

  protected DROrder createStopLimitDROrder(final int orderId, final User user, final int securityId, final long price, final long stopPx,
      final long quantity, final Side side, final TimeInForce timeInForce) {
    DROrder order = createDROrder(orderId, user, OrdType.STOP_LIMIT, securityId, price, quantity, side, timeInForce);
    order.setStopPx(stopPx, (short) 2);
    order.setStopPxInt((int) stopPx);
    if (side == Side.SELL)
      order.setType(Constants.STOP_SELL_LIMIT);
    else if (side == Side.BUY)
      order.setType(Constants.STOP_BUY_LIMIT);

    return order;
  }

  private DROrder createDROrder(final int orderId, final User user, final OrdType orderType, final int securityId, final long price,
      final long quantity, final Side side, final TimeInForce timeInForce) {
    DROrder order = DROrderObjectPool.get();
    order.setOrderId(orderId);
    order.setUser(user);
    order.setAccount(user.getId());
    order.setClOrdId("ClOrdId");
    order.setOrdType(orderType);
    order.setSecurityId(securityId);
    order.setSide(side);
    order.setPrice(price, (short) 2);
    order.setPriceInt((int) price);
    order.setQty(quantity, (short) 2);
    order.setQuantityLong(quantity);
    order.setQuantityOrigLong(quantity);
    order.setTimeInForce(timeInForce);

    return order;
  }

  protected DRCancelOrder createDRCancelOrder(int cancelId, final int orderId, final User user, final int securityId, final long price,
      final long quantity, Side side, TimeInForce timeInForce) {
    return createDRCancelOrder(cancelId, orderId, user, OrdType.LIMIT, securityId, price, quantity, side, timeInForce);
  }

  protected DRCancelOrder createDRCancelOrder(int cancelId, final int orderId, final User user, OrdType orderType, final int securityId,
      final long price, final long quantity, Side side, TimeInForce timeInForce) {
    DRCancelOrder cancelOrder = new DRCancelOrder();
    cancelOrder.setCancelId(cancelId);
    cancelOrder.setUser(user);
    cancelOrder.setOrigOrderId(orderId);
    cancelOrder.setAccount(user.getId());
    cancelOrder.setClOrdId("ClOrdId");
    cancelOrder.setOrdType(orderType);
    cancelOrder.setSecurityId(securityId);
    cancelOrder.setSide(side);
    cancelOrder.setPrice(price, (short) 2);
    cancelOrder.setPriceInt((int) price);
    cancelOrder.setQty(quantity, (short) 2);
    cancelOrder.setQuantityLong(quantity);
    cancelOrder.setQuantityOrigLong(quantity);

    return cancelOrder;
  }

  // Add buy order, assert
  @Test
  public void addBuyOrder() {
    orderBook.addOrder(createDROrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("ExecutionReportMessage", "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add sell order, assert
  @Test
  public void addSellOrder() {
    orderBook.addOrder(createDROrder(1, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy orders with same price, assert
  // Assert that buy orders at the same price are sorted by time priority
  @Test
  public void buyOrdersWithSamePriceSortByTimePriority() {
    orderBook.addOrder(createDROrder(1, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    orderBook.addOrder(createDROrder(2, user, pair.getId(), 1010, 300, Side.BUY, DAY));
    orderBook.addOrder(createDROrder(3, user, pair.getId(), 1010, 800, Side.BUY, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    // expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=300, ordStatus=NEW");
    // expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");
    assertMessages();

    String orders = orderBook.toString(1010);
    Assert.assertTrue(orders.indexOf("orderId=1") < orders.indexOf("orderId=2"));
    Assert.assertTrue(orders.indexOf("orderId=2") < orders.indexOf("orderId=3"));
  }

  // Add sell orders with same price, assert
  // Assert that sell orders at the same price are sorted by time priority
  @Test
  public void sellOrdersWithSamePriceSortByTimePriority() {
    orderBook.addOrder(createDROrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    orderBook.addOrder(createDROrder(2, user, pair.getId(), 1010, 300, Side.SELL, DAY));
    orderBook.addOrder(createDROrder(3, user, pair.getId(), 1010, 800, Side.SELL, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    // expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=300, ordStatus=NEW");
    // expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");
    assertMessages();

    String orders = orderBook.toString(1010);
    Assert.assertTrue(orders.indexOf("orderId=1") < orders.indexOf("orderId=2"));
    Assert.assertTrue(orders.indexOf("orderId=2") < orders.indexOf("orderId=3"));
  }

  // Add buy order and then a sell order with the same price and quality, assert fills
  @Test
  public void exactMatchSellOrderToBuyOrderOnBook() {
    orderBook.addOrder(createDROrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createDROrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    // expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add sell order and then a buy order with the same price and quality, assert fills
  @Test
  public void exactMatchBuyOrderToSellOrderOnBook() {
    orderBook.addOrder(createDROrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    orderBook.addOrder(createDROrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    // expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy order and then a sell order with the same quantity and prices crossed, assert fills
  @Test
  public void exactMatchSellOrderToBuyOrderOnBookPricesCross() {
    orderBook.addOrder(createDROrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createDROrder(2, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    // expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add sell order and then a buy order with the same quantity and prices crossed, assert fills
  @Test
  public void exactMatchBuyOrderToSellOrderOnBookPricesCross() {
    orderBook.addOrder(createDROrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    orderBook.addOrder(createDROrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    // expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add stop limit buy order, assert
  @Test
  public void addStopLimitBuyOrder() {
    orderBook.addOrder(createStopLimitDROrder(1, user, pair.getId(), 1010, 1012, 500, Side.BUY, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("orderId=1, ordType=STOP_LIMIT, side=BUY, price=1010, stopPx=1012, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add stop limit sell order, assert
  @Test
  public void addStopLimitSellOrder() {
    orderBook.addOrder(createStopLimitDROrder(1, user, pair.getId(), 1015, 1012, 500, Side.SELL, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("orderId=1, ordType=STOP_LIMIT, side=SELL, price=1015, stopPx=1012, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();
  }

  // Add buy order that rest
  // Cancel, assert cancellation
  @Test
  public void cancelBuyOrderResting() {
    orderBook.addOrder(createDROrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.cancelOrder(createDRCancelOrder(2, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    assertMessages();

    orderBook.build();
    assertMessages();
  }

  // Add sell order that rest
  // Cancel, assert cancellation
  @Test
  public void cancelSellOrderResting() {
    orderBook.addOrder(createDROrder(110, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    orderBook.cancelOrder(createDRCancelOrder(111, 110, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    assertMessages();

    orderBook.build();
    assertMessages();
  }

  // Add buy and sell orders to partially fill the buy order
  // Cancel the buy order, assert cancellation
  @Test
  public void cancelBuyOrderPartiallyFilled() {
    orderBook.addOrder(createDROrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createDROrder(2, user, pair.getId(), 1011, 300, Side.SELL, DAY));
    orderBook.cancelOrder(createDRCancelOrder(3, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");
    assertMessages();
  }

  // Add buy and sell orders to partially fill the sell order
  // Cancel the sell order, assert cancellation
  @Test
  public void cancelSellOrderPartiallyFilled() {
    orderBook.addOrder(createDROrder(1, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    orderBook.addOrder(createDROrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    orderBook.cancelOrder(createDRCancelOrder(3, 2, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    assertMessages();

    orderBook.build();
    // expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=NEW");
    assertMessages();
  }
}
