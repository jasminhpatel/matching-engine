package com.solfini.matchengine.model.orderbook;

import com.solfini.internal.admin.schema.MarketStatus;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;

import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookMarketCloseTest extends OrderBookTest {

  @Override
  public void before() {
    super.before();
    orderBook.orderBook().changeState(MarketStatus.CLOSE, 0, null);
  }

  @Override
  public void after() {
    orderBook.orderBook().changeState(MarketStatus.OPEN, 0, null);
    super.after();
  }

  // Add limit buy order, assert rejection
  @Test
  public void addLimitBuyOrder() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("BusinessRejectMessage", "orderId=1, businessRejectReason=MARKET_IS_PAUSED_OR_CLOSED, text=Market is paused or closed");
    assertMessages();
  }

  // Add limit sell order, assert rejection
  @Test
  public void addLimitSellOrder() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    expectMessage("BusinessRejectMessage", "orderId=1, businessRejectReason=MARKET_IS_PAUSED_OR_CLOSED, text=Market is paused or closed");
    assertMessages();
  }

  // Add stop limit buy order, assert rejection
  @Test
  public void addStopLimitBuyOrder() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("BusinessRejectMessage", "orderId=1, businessRejectReason=MARKET_IS_PAUSED_OR_CLOSED, text=Market is paused or closed");
    assertMessages();
  }

  // Add stop limit sell order, assert rejection
  @Test
  public void addStopLimitSellOrder() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    expectMessage("BusinessRejectMessage", "orderId=1, businessRejectReason=MARKET_IS_PAUSED_OR_CLOSED, text=Market is paused or closed");
    assertMessages();
  }

  // Cancel buy order, assert rejection
  @Test
  public void cancelBuyOrder() {
    orderBook.cancelOrder(createCancelOrder(2, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("ExecutionReportMessage", "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("BusinessRejectMessage", "orderId=1, businessRejectReason=MARKET_IS_CLOSED, text=Market is closed");
    assertMessages();
  }

  // Cancel sell order, assert rejection
  @Test
  public void cancelSellOrder() {
    orderBook.cancelOrder(createCancelOrder(2, 1, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    expectMessage("ExecutionReportMessage", "orderId=1, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("BusinessRejectMessage", "orderId=1, businessRejectReason=MARKET_IS_CLOSED, text=Market is closed");
    assertMessages();
  }

  // Cancel replace buy order, assert rejection
  @Test
  public void cancelReplaceBuyOrder() {
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY,
      createOrder(2, user, pair.getId(), 2301, 1000, Side.BUY, DAY)));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("BusinessRejectMessage", "orderId=1, businessRejectReason=MARKET_IS_CLOSED, text=Market is closed");
    expectMessage("BusinessRejectMessage", "orderId=2, businessRejectReason=MARKET_IS_PAUSED_OR_CLOSED, text=Market is paused or closed");
    assertMessages();
  }

  // Cancel replace sell order, assert rejection
  @Test
  public void cancelReplaceSellOrder() {
    orderBook.cancelReplaceOrder(createCancelReplaceOrder(3, 1, user, pair.getId(), 1011, 500, Side.SELL, DAY,
      createOrder(2, user, pair.getId(), 2301, 1000, Side.SELL, DAY)));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("BusinessRejectMessage", "orderId=1, businessRejectReason=MARKET_IS_CLOSED, text=Market is closed");
    expectMessage("BusinessRejectMessage", "orderId=2, businessRejectReason=MARKET_IS_PAUSED_OR_CLOSED, text=Market is paused or closed");
    assertMessages();
  }

  // Mass cancel orders, assert rejection
  @Test
  public void massCancelOrders() {
    orderBook.orderBook().massCancelOrder(createMassCancelOrder(2, createOrder(1, user, pair.getId(), 2301, 1000, Side.SELL, DAY)));
    expectMessage("BusinessRejectMessage", "orderId=1, businessRejectReason=MARKET_IS_CLOSED, text=Market is closed");
    assertMessages();
  }
}
