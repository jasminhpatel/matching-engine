package com.solfini.matchengine.model.orderbook;

import java.util.List;

import com.solfini.common.Message;
import com.solfini.sbe.encoder.Side;
import org.junit.Assert;
import org.junit.Test;

import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookAvgPxTest extends OrderBookTest {

  // Add buy order and then a sell order with the same price and quality, assert fills
  @Test
  public void exactMatchSellOrderToBuyOrderOnBook() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, avgPx=1011, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, avgPx=1011, ordStatus=FILLED");
    assertMessages();

    expectOutput("BalanceAdminMessage", "userId=18", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("Order", "orderId=1, ordType=LIMIT, side=BUY, price=1011, qty=5, qty_scale=2");
    expectOutput("BalanceAdminMessage", "userId=18", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("Order", "orderId=2, ordType=LIMIT, side=SELL, price=1011,  qty=5, qty_scale=2");
    expectOutput("BalanceAdminMessage", "userId=18", "Balance [assetId=12, balance=-5.00, balance_change=0");
    expectOutput("DRExecutionReport", "side=SELL");
    expectOutput("BalanceAdminMessage", "userId=18", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("DRExecutionReport", "side=BUY");

    List<Message> baseMessages = assertOutputMessages();
    assertOutputBaseMessage(baseMessages.get(baseMessages.size() - 2), "ExecutionReportMessage", "avgPx=1011");
    assertOutputBaseMessage(baseMessages.get(baseMessages.size() - 1), "ExecutionReportMessage", "avgPx=1011");
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell order and then a buy order with the same price and quality, assert fills
  @Test
  public void exactMatchBuyOrderToSellOrderOnBook() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, avgPx=1011, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, avgPx=1011, execType=TRADE, ordStatus=FILLED");
    assertMessages();

    expectOutput("BalanceAdminMessage", "userId=18", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("Order", "orderId=1, ordType=LIMIT, side=SELL, price=1011, qty=5, qty_scale=2");
    expectOutput("BalanceAdminMessage", "userId=18", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("Order", "orderId=2, ordType=LIMIT, side=BUY, price=1011,  qty=5, qty_scale=2");
    expectOutput("BalanceAdminMessage", "userId=18", "Balance [assetId=12, balance=5.00, balance_change=0");
    expectOutput("DRExecutionReport", "side=BUY");
    expectOutput("BalanceAdminMessage", "userId=18", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("DRExecutionReport", "side=SELL");

    List<Message> baseMessages = assertOutputMessages();
    assertOutputBaseMessage(baseMessages.get(baseMessages.size() - 2), "ExecutionReportMessage", "avgPx=1011");
    assertOutputBaseMessage(baseMessages.get(baseMessages.size() - 1), "ExecutionReportMessage", "avgPx=1011");
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy order and then a sell order with the same quantity and prices crossed, assert fills
  @Test
  public void avgPxBuyOrderOnBookPricesCross() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1010, avgPx=1011, avgPxScale=2, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, avgPx=1011, avgPxScale=2, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

}
