package com.solfini.matchengine.model.orderbook;

import com.solfini.internal.admin.schema.TokenType;
import org.junit.Assert;
import org.junit.Test;
import java.util.List;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.user.User;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;
import static com.solfini.sbe.encoder.TimeInForce.*;

public class OrderBookLimitOrderTest extends OrderBookTest {

  // Add order for non existing security, assert rejection
  @Test
  public void rejectOrderForNonExistingSecurity() {
    orderBook.addOrder(createOrder(1, user, 100, 1011, 300, Side.BUY, DAY));
    expectMessage("BusinessRejectMessage",
        "businessRejectReason=INSTRUMENT_NOT_FOUND, text=Instrument not found, refMsgType=ORDER_SINGLE, businessRejectRefID=1, orderId=1");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());
  }

  // Add order for invalid security, assert rejection
  @Test
  public void rejectOrderForInvalidSecurity() {
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(101, UpdateType.PUT, "BTC/USDT[F]", BTC, USDT, 2, 2));
    expectMessage("securityId=101, symbol=BTC/USDT[F], updateType=PUT");

    orderBook.orderBook().addOrder(createOrder(1, user, 101, 1011, 300, Side.BUY, DAY));
    expectMessage("BusinessRejectMessage",
        "businessRejectReason=INVALID_ORDER_SECURITY, text=Order security is invalid, refMsgType=ORDER_SINGLE, businessRejectRefID=1, orderId=1");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());
  }

  // Add buy order, assert open orders
  @Test
  public void addBuyOrder_ValidateOpenOrders() {
    User user = createUser(50);
    user.addPosition(USDT, 10_000_00, null, 0, TokenType.ERC20);
    expectMessage("userId=50");

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();

    Assert.assertEquals(1, user.getOpenOrderCount());
    Assert.assertEquals(10.11 * 5.0, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 2);
    Assert.assertEquals(0, validator.validate().size());
  }

  // Add buy order, assert
  @Test
  public void addBuyOrder() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("ExecutionReportMessage", "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();

    expectOutput("BalanceAdminMessage", "userId=" + user.getId());
    expectOutput("Order", "orderId=1, ordType=LIMIT, side=BUY, price=1011, price_scale=2, qty=500, qty_scale=2");
    assertOutputMessages();
    Assert.assertEquals(0, validator.validate().size());
  }

  // Add sell order, assert
  @Test
  public void addSellOrder() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();

    expectOutput("BalanceAdminMessage", "userId=" + user.getId());
    expectOutput("Order", "orderId=1, ordType=LIMIT, side=SELL, price=1012, price_scale=2, qty=5, qty_scale=2");
    assertOutputMessages();
    Assert.assertEquals(0, validator.validate().size());
  }

  // Add buy orders with same price, assert
  // Assert that buy orders at the same price are sorted by time priority
  @Test
  public void buyOrdersWithSamePriceSortByTimePriority() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 300, Side.BUY, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 800, Side.BUY, DAY));

    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");
    assertMessages();

    String orders = orderBook.toString(1010);
    Assert.assertTrue(orders.indexOf("orderId=1") < orders.indexOf("orderId=2"));
    Assert.assertTrue(orders.indexOf("orderId=2") < orders.indexOf("orderId=3"));
    Assert.assertEquals(0, validator.validate().size());
  }

  // Add sell orders with same price, assert
  // Assert that sell orders at the same price are sorted by time priority
  @Test
  public void sellOrdersWithSamePriceSortByTimePriority() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 300, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 800, Side.SELL, DAY));

    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");
    assertMessages();

    String orders = orderBook.toString(1010);
    Assert.assertTrue(orders.indexOf("orderId=1") < orders.indexOf("orderId=2"));
    Assert.assertTrue(orders.indexOf("orderId=2") < orders.indexOf("orderId=3"));
    Assert.assertEquals(0, validator.validate().size());
  }

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
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy order and then a sell order with the same quantity and prices crossed, assert fills
  @Test
  public void exactMatchSellOrderToBuyOrderOnBookPricesCross() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell order and then a buy order with the same quantity and prices crossed, assert fills
  @Test
  public void exactMatchBuyOrderToSellOrderOnBookPricesCross() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy order and then a sell order with a partial fill on buy (prices same)
  @Test
  public void partialFillBuyOrderOnBook() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=200, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell order and then a buy order with a partial fill on sell (prices same)
  @Test
  public void partialFillSellOrderOnBook() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 300, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=200, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell order and then a buy order with a partial fill on buy (prices same)
  @Test
  public void partialFillBuyOrderIncoming() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 800, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy order and then a sell order with a partial fill on sell (prices same)
  @Test
  public void partialFillSellOrderIncoming() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 800, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy order and then a sell order with a partial fill on buy (prices cross)
  @Test
  public void partialFillBuyOrderOnBookPricesCross() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=200, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell order and then a buy order with a partial fill on sell (prices cross)
  @Test
  public void partialFillSellOrderOnBookPricesCross() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=200, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell order and then a buy order with a partial fill on buy (prices cross)
  @Test
  public void partialFillBuyOrderIncomingPricesCross() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 800, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=800, leavesQty=800, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=800, leavesQty=300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy order and then a sell order with a partial fill on sell (prices cross)
  @Test
  public void partialFillSellOrderIncomingPricesCross() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 800, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Close current position and open a short position
  @Test
  public void closeLongPositionAndOpenShort() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 800, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");

    orderBook.addOrder(createOrder(4, user3, pair.getId(), 1010, 800, Side.BUY, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();

    Assert.assertEquals(0, validator.validate().size());
  }

  // Close current position and open a short position
  @Test
  public void closeShortPositionAndOpenLong() {
    orderBook.addOrder(createOrder(1, user4, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();

    orderBook.addOrder(createOrder(3, user4, pair.getId(), 1010, 800, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");

    orderBook.addOrder(createOrder(4, user3, pair.getId(), 1010, 800, Side.SELL, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();

    Assert.assertEquals(0, validator.validate().size());
  }

  // Add buy orders at different price points, assert
  // Add sell order that fills against a single buy order (partial)
  @Test
  public void sweepBuyOrdersSinglePriceSingleOrderPartialFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1011, 100, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=SELL, price=1011, orderQty=100, leavesQty=100, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1011, orderQty=100, leavesQty=0, lastQty=100, cumQty=100, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=400, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());
  }

  // Add buy orders at different price points, assert
  // Add sell order that fills against a single buy order (partial)
  @Test
  public void sweepBuyOrdersSinglePriceSingleOrderFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy orders at different price points, assert
  // Add sell order that fills against multiple buy orders at a single price point (with a partial fill on the last buy)
  @Test
  public void sweepBuyOrdersSinglePriceMultipleOrdersPartialFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1011, 900, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=SELL, price=1011, orderQty=900, leavesQty=900, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1011, orderQty=900, leavesQty=400, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1011, orderQty=900, leavesQty=100, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1011, orderQty=900, leavesQty=0, lastQty=100, cumQty=900, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=200, leavesQty=100, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy orders at different price points, assert
  // Add sell order that fills against multiple buy orders at a single price point (with a fill on the last buy)
  @Test
  public void sweepBuyOrdersSinglePriceMultipleOrdersFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1011, 800, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=SELL, price=1011, orderQty=800, leavesQty=800, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1011, orderQty=800, leavesQty=300, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1011, orderQty=800, leavesQty=0, lastQty=300, cumQty=800, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy orders at different price points, assert
  // Add sell order that fills against multiple buy orders at multiple price points (with a partial fill on the last buy)
  @Test
  public void sweepBuyOrdersMultiplePriceMultipleOrdersPartialFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1010, 1400, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=1400, leavesQty=1400, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=1400, leavesQty=900, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=1400, leavesQty=600, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=1400, leavesQty=400, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=1400, leavesQty=100, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=1400, leavesQty=0, lastQty=100, cumQty=1400, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=5, ordType=LIMIT, side=BUY, price=1010, orderQty=200, leavesQty=100, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy orders at different price points, assert
  // Add sell order that fills against multiple buy orders at multiple price points (with a fill on the last buy)
  @Test
  public void sweepBuyOrdersMultiplePriceMultipleOrdersFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1010, 1300, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=1300, leavesQty=1300, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=1300, leavesQty=800, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=1300, leavesQty=500, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=1300, leavesQty=300, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=1300, leavesQty=0, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy orders at different price points, assert
  // Add sell order that partially fills against multiple buy orders at multiple price points
  @Test
  public void sweepBuyOrdersMultiplePriceMultipleOrdersExhaust() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1010, 2000, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=2000, leavesQty=2000, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=2000, leavesQty=1500, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=2000, leavesQty=1200, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=2000, leavesQty=1000, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=2000, leavesQty=700, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=SELL, price=1010, orderQty=2000, leavesQty=500, lastQty=200, cumQty=1500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=5, ordType=LIMIT, side=BUY, price=1010, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell orders at different price points, assert
  // Add buy order that fills against a single buy order (partial)
  @Test
  public void sweepSellOrdersSinglePriceSingleOrderPartialFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1010, 100, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, price=1010, orderQty=100, leavesQty=100, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1010, orderQty=100, leavesQty=0, lastQty=100, cumQty=100, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=400, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell orders at different price points, assert
  // Add buy order that fills against a single buy order (partial)
  @Test
  public void sweepSellOrdersSinglePriceSingleOrderFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell orders at different price points, assert
  // Add buy order that fills against multiple buy orders at a single price point (with a partial fill on the last buy)
  @Test
  public void sweepSellOrdersSinglePriceMultipleOrdersPartialFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1010, 900, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, price=1010, orderQty=900, leavesQty=900, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1010, orderQty=900, leavesQty=400, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1010, orderQty=900, leavesQty=100, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1010, orderQty=900, leavesQty=0, lastQty=100, cumQty=900, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=200, leavesQty=100, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell orders at different price points, assert
  // Add buy order that fills against multiple buy orders at a single price point (with a fill on the last buy)
  @Test
  public void sweepSellOrdersSinglePriceMultipleOrdersFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1010, 800, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=800, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=300, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=0, lastQty=300, cumQty=800, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell orders at different price points, assert
  // Add buy order that fills against multiple buy orders at multiple price points (with a partial fill on the last buy)
  @Test
  public void sweepSellOrdersMultiplePriceMultipleOrdersPartialFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1011, 1400, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=1400, leavesQty=1400, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=1400, leavesQty=900, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=1400, leavesQty=600, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=1400, leavesQty=400, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=1400, leavesQty=100, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=1400, leavesQty=0, lastQty=100, cumQty=1400, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=5, ordType=LIMIT, side=SELL, price=1011, orderQty=200, leavesQty=100, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell orders at different price points, assert
  // Add buy order that fills against multiple buy orders at multiple price points (with a fill on the last buy)
  @Test
  public void sweepSellOrdersMultiplePriceMultipleOrdersFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createOrder(100, user2, pair.getId(), 1011, 1300, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=1300, leavesQty=1300, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=1300, leavesQty=800, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=1300, leavesQty=500, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=1300, leavesQty=300, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=1300, leavesQty=0, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell orders at different price points, assert
  // Add buy order that partially fills against multiple buy orders at multiple price points
  @Test
  public void sweepSellOrdersMultiplePriceMultipleOrdersExhaust() {
    addSellOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1011, 2000, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=2000, leavesQty=2000, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=2000, leavesQty=1500, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=2000, leavesQty=1200, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=2000, leavesQty=1000, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=2000, leavesQty=700, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=2000, leavesQty=500, lastQty=200, cumQty=1500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=5, ordType=LIMIT, side=SELL, price=1011, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add 2 buy orders
  // Add sell order
  // Assert partial fills and fills
  // Add 2 sell orders
  // Add another sell order
  // Add buy order, assert fills
  @Test
  public void multipleFillsAndPartialFills() {
    // Add 2 buy orders
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");

    // Add sell order
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 800, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=800, ordStatus=NEW");

    // Assert partial fills and fills
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, orderQty=800, leavesQty=300, lastQty=500, lastPx=1011, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, orderQty=500, leavesQty=0, lastQty=500, lastPx=1011, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, orderQty=800, leavesQty=0, lastQty=300, lastPx=1010, cumQty=800, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, orderQty=500, leavesQty=200, lastQty=300, lastPx=1010, ordStatus=PARTIALLY_FILLED");
    assertMessages();

    // Add 2 sell orders
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1015, 200, Side.SELL, DAY));
    orderBook.addOrder(createOrder(5, user, pair.getId(), 1015, 300, Side.SELL, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1015, orderQty=200, ordStatus=NEW");
    expectMessage("orderId=5, ordType=LIMIT, side=SELL, price=1015, orderQty=300, ordStatus=NEW");

    // Add another sell order
    orderBook.addOrder(createOrder(6, user, pair.getId(), 1015, 1800, Side.SELL, DAY));
    expectMessage("orderId=6, ordType=LIMIT, side=SELL, price=1015, orderQty=1800, ordStatus=NEW");

    // Add buy order, assert fills
    orderBook.addOrder(createOrder(7, user, pair.getId(), 5000, 1000, Side.BUY, DAY));
    expectMessage("orderId=7, ordType=LIMIT, side=BUY, price=5000, orderQty=1000, ordStatus=NEW");
    expectMessage(
        "orderId=7, ordType=LIMIT, side=BUY, price=5000, orderQty=1000, leavesQty=800, lastQty=200, lastPx=1015, cumQty=200, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1015, orderQty=200, leavesQty=0, lastQty=200, lastPx=1015, ordStatus=FILLED");
    expectMessage(
        "orderId=7, ordType=LIMIT, side=BUY, price=5000, orderQty=1000, leavesQty=500, lastQty=300, lastPx=1015, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=5, ordType=LIMIT, side=SELL, price=1015, orderQty=300, leavesQty=0, lastQty=300, lastPx=1015, ordStatus=FILLED");
    expectMessage(
        "orderId=7, ordType=LIMIT, side=BUY, price=5000, orderQty=1000, leavesQty=0, lastQty=500, lastPx=1015, cumQty=1000, ordStatus=FILLED");
    expectMessage(
        "orderId=6, ordType=LIMIT, side=SELL, price=1015, orderQty=1800, leavesQty=1300, lastQty=500, lastPx=1015, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add buy order that rest
  // Cancel, assert cancellation
  @Test
  public void cancelBuyOrderResting() {
    orderBook.addOrder(createOrder(1, user7, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 1, user7, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    assertMessages();

    expectOutput("BalanceAdminMessage", "userId=24", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("Order", "securityId=12, orderId=1, ordType=LIMIT, side=BUY, price=1011, qty=500, qty_scale=2");
    // no decoded output messages for PENDING_CANCEL exec report
    expectOutput("BalanceAdminMessage", "userId=24", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("CancelOrder", "securityId=12, price=1011, side=BUY, qty=5, origOrderId=1, cancelId=2, cancelPriority=0");
    assertOutputMessages();

    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy order that rest
  // Cancel by secondaryOrderId, assert cancellation
  @Test
  public void cancelBuyOrderBySecondaryOrderId() {
    Order order = createOrder(1, user7, pair.getId(), 1011, 500, Side.BUY, DAY);
    order.setSecondaryOrderId(10000);
    orderBook.addOrder(order);
    expectMessage("orderId=1, secondaryOrderId=10000, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    CancelOrder cancelOrder = createCancelOrder(2, 0, user7, pair.getId(), 1011, 500, Side.BUY, DAY);
    cancelOrder.setSecondaryOrderId(10000);
    orderBook.cancelOrder(cancelOrder);
    expectMessage("secondaryOrderId=10000, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, secondaryOrderId=10000, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());
  }

  // Add sell order that rest
  // Cancel, assert cancellation
  @Test
  public void cancelSellOrderResting() {
    orderBook.addOrder(createOrder(110, user8, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=110, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(111, 110, user8, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=110, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=110, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=CANCELED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell order that rest
  // Cancel by secondaryOrderId, assert cancellation
  @Test
  public void cancelSellOrderBySecondaryOrderId() {
    Order order = createOrder(1, user7, pair.getId(), 1011, 500, Side.SELL, DAY);
    order.setSecondaryOrderId(10000);
    orderBook.addOrder(order);
    expectMessage("orderId=1, secondaryOrderId=10000, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    CancelOrder cancelOrder = createCancelOrder(2, 0, user7, pair.getId(), 1011, 500, Side.SELL, DAY);
    cancelOrder.setSecondaryOrderId(10000);
    orderBook.cancelOrder(cancelOrder);
    expectMessage("secondaryOrderId=10000, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, secondaryOrderId=10000, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=CANCELED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());
  }

  // Add buy orders that rest
  // Cancel without order id, assert cancellation of first order at given price point
  @Test
  public void cancelFirstBuyOrderResting() {
    User user = createUser(401);
    user.addPosition(USDT, 10_000_00, null, 0, TokenType.ERC20);
    expectMessage("userId=401");

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(4, 0, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=0, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=0, ordType=LIMIT, side=BUY, execId=0, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell orders that rest
  // Cancel without order id, assert cancellation of first order at given price point
  @Test
  public void cancelFirstSellOrderResting() {
    User user = createUser(402);
    user.addPosition(USDT, 10_000_00, null, 0, TokenType.ERC20);
    expectMessage("userId=402");

    orderBook.addOrder(createOrder(110, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(111, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(112, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=110, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=111, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=112, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(113, 0, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=0, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=0, ordType=LIMIT, side=SELL, execId=0, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Add buy and sell orders to partially fill the buy order
  // Cancel the buy order, assert cancellation
  @Test
  public void cancelBuyOrderPartiallyFilled() {
    orderBook.addOrder(createOrder(1, user6, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user6, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=200, ordStatus=PARTIALLY_FILLED");

    orderBook.cancelOrder(createCancelOrder(3, 1, user6, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    assertMessages();
  }

  // Add buy and sell orders to partially fill the sell order
  // Cancel the sell order, assert cancellation
  @Test
  public void cancelSellOrderPartiallyFilled() {
    orderBook.addOrder(createOrder(1, user9, pair.getId(), 1011, 300, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user9, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=200, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, ordStatus=FILLED");

    orderBook.cancelOrder(createCancelOrder(3, 2, user9, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=CANCELED");
    assertMessages();
  }

  // Add buy and sell orders to fill the buy order
  // Cancel the buy order, assert rejection
  @Test
  public void cancelBuyOrderFilled() {
    orderBook.addOrder(createOrder(100, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    orderBook.addOrder(createOrder(101, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=101, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=101, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=200, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, ordStatus=FILLED");

    orderBook.cancelOrder(createCancelOrder(102, 100, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Add buy and sell orders to fill the sell order
  // Cancel the sell order, assert rejection
  @Test
  public void cancelSellOrderFilled() {
    User userk = createUser(301);
    userk.addPosition(USDT, 10_000_00, null, 0, TokenType.ERC20);

    expectMessage("userId=301");
    expectOutput("userId=301");

    orderBook.addOrder(createOrder(1, userk, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, userk, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=200, ordStatus=PARTIALLY_FILLED");

    orderBook.cancelOrder(createCancelOrder(3, 2, userk, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();

    expectOutput("BalanceAdminMessage", "Balance [assetId=1, balance=10000.00", "Balance [assetId=12, balance=.00");
    expectOutput("Order", "securityId=12, orderId=1, price=1011, price_scale=2, price2=0, price2_scale=0, qty=5, qty_scale=2, side=BUY");
    expectOutput("BalanceAdminMessage", "Balance [assetId=1, balance=10000.00", "Balance [assetId=12, balance=.00");
    expectOutput("Order", "securityId=12, orderId=2, price=1011, price_scale=2, price2=0, price2_scale=0, qty=3, qty_scale=2, side=SELL");
    expectOutput("BalanceAdminMessage", "Balance [assetId=1, balance=10000.00", "Balance [assetId=12, balance=-3.00");
    expectOutput("DRExecutionReport", "side=SELL, ordType=LIMIT");
    expectOutput("BalanceAdminMessage", "Balance [assetId=1, balance=10000.00", "Balance [assetId=12, balance=.00");
    expectOutput("DRExecutionReport", "side=BUY, ordType=LIMIT");

    assertOutputMessages();
  }

  // Add 2 buy orders
  // Add sell order
  // Assert partial fills and fills
  // Add 2 sell orders
  // Add another sell order
  // Add buy order, assert fills
  // Cancel partially filled order, assert
  // Cancel buy order already filled, assert business reject
  // Cancel sell order already filled, assert business reject
  @Test
  public void cancelMatchedOrders() {
    User user = createUser(400);
    user.addPosition(USDT, 10_000_00, null, 0, TokenType.ERC20);
    expectMessage("userId=400");

    // Add 2 buy orders
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");

    // Add sell order
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 800, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=800, ordStatus=NEW");

    // Assert partial fills and fills
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=300, lastQty=500, lastPx=1011, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, lastQty=500, lastPx=1011, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=0, lastQty=300, lastPx=1010, cumQty=800, ordStatus=FILLED");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=200, lastQty=300, lastPx=1010, ordStatus=PARTIALLY_FILLED");
    assertMessages();

    // Add 2 sell orders
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1015, 200, Side.SELL, DAY));
    orderBook.addOrder(createOrder(5, user, pair.getId(), 1015, 300, Side.SELL, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1015, orderQty=200, ordStatus=NEW");
    expectMessage("orderId=5, ordType=LIMIT, side=SELL, price=1015, orderQty=300, ordStatus=NEW");

    // Add another sell order
    orderBook.addOrder(createOrder(6, user, pair.getId(), 1015, 1800, Side.SELL, DAY));
    expectMessage("orderId=6, ordType=LIMIT, side=SELL, price=1015, orderQty=1800, ordStatus=NEW");

    // Add buy order, assert fills
    orderBook.addOrder(createOrder(7, user, pair.getId(), 5000, 1000, Side.BUY, DAY));
    expectMessage("orderId=7, ordType=LIMIT, side=BUY, price=5000, orderQty=1000, ordStatus=NEW");
    expectMessage(
        "orderId=7, ordType=LIMIT, side=BUY, price=5000, orderQty=1000, leavesQty=800, lastQty=200, lastPx=1015, cumQty=200, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1015, orderQty=200, leavesQty=0, lastQty=200, lastPx=1015, ordStatus=FILLED");
    expectMessage(
        "orderId=7, ordType=LIMIT, side=BUY, price=5000, orderQty=1000, leavesQty=500, lastQty=300, lastPx=1015, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=5, ordType=LIMIT, side=SELL, price=1015, orderQty=300, leavesQty=0, lastQty=300, lastPx=1015, ordStatus=FILLED");
    expectMessage(
        "orderId=7, ordType=LIMIT, side=BUY, price=5000, orderQty=1000, leavesQty=0, lastQty=500, lastPx=1015, cumQty=1000, ordStatus=FILLED");
    expectMessage(
        "orderId=6, ordType=LIMIT, side=SELL, price=1015, orderQty=1800, leavesQty=1300, lastQty=500, lastPx=1015, ordStatus=PARTIALLY_FILLED");
    assertMessages();

    // Cancel partially filled order, assert
    orderBook.cancelOrder(createCancelOrder(9, 6, user, pair.getId(), 1015, 1800, Side.SELL, DAY));
    expectMessage("orderId=6, ordType=LIMIT, side=SELL, price=1015, orderQty=1800, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=6, ordType=LIMIT, side=SELL, price=1015, orderQty=1800, ordStatus=CANCELED");
    assertMessages();

    // Cancel buy order already filled, assert business reject
    orderBook.cancelOrder(createCancelOrder(8, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();

    // Cancel sell order already filled, assert business reject
    orderBook.cancelOrder(createCancelOrder(9, 3, user, pair.getId(), 1010, 800, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=800, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=3, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Add IOC order that fills against orders on the book, assert fills
  @Test
  public void buyImmediateOrCancelFilled() {
    orderBook.addOrder(createOrder(1, user2, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user2, pair.getId(), 1011, 600, Side.BUY, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=600, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=NEW");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=600, leavesQty=100, cumQty=500, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=600, leavesQty=0, cumQty=600, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=200, cumQty=100, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add IOC order that fills against orders on the book, assert fills
  @Test
  public void sellImmediateOrCancelFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 600, Side.SELL, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=600, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=NEW");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=600, leavesQty=100, cumQty=500, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=600, leavesQty=0, cumQty=600, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=200, cumQty=100, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add IOD order that partially fills against orders on the book, assert fills and IOC expiry
  @Test
  public void buyImmediateOrCancelPartiallyFilledAndExpires() {
    orderBook.addOrder(createOrder(1, user2, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user2, pair.getId(), 1011, 1000, Side.BUY, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=NEW");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=500, cumQty=500, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=200, cumQty=800, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, cumQty=300, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, timeInForce=IMMEDIATE_OR_CANCEL, execType=EXPIRED, ordStatus=EXPIRED");
    assertMessages();
  }

  // Add IOD order that partially fills against orders on the book, assert fills and IOC expiry
  @Test
  public void sellImmediateOrCancelPartiallyFilledAndExpires() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 1000, Side.SELL, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=NEW");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=500, cumQty=500, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=200, cumQty=800, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, cumQty=300, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, timeInForce=IMMEDIATE_OR_CANCEL, execType=EXPIRED, ordStatus=EXPIRED");
    assertMessages();
  }

  // Add IOC order that fills against orders on the book across multiple price points, assert fills
  @Test
  public void buyImmediateOrCancelFilledBySweep() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 600, Side.BUY, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=600, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=NEW");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, lastPx=1010, orderQty=600, leavesQty=300, cumQty=300, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, cumQty=300, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, lastPx=1011, orderQty=600, leavesQty=0, cumQty=600, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=200, cumQty=300, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add IOC order that fills against orders on the book across multiple price points, assert fills
  @Test
  public void sellImmediateOrCancelFilledBySweep() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 600, Side.SELL, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=600, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=NEW");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1010, lastPx=1011, orderQty=600, leavesQty=300, cumQty=300, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, cumQty=300, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1010, lastPx=1010, orderQty=600, leavesQty=0, cumQty=600, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=200, cumQty=300, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add IOD order that partially fills against orders on the book across multiple price points, assert fills and IOC expiry
  @Test
  public void buyImmediateOrCancelPartiallyFilledBySweepAndExpires() {
    orderBook.addOrder(createOrder(1, user2, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user2, pair.getId(), 1011, 1000, Side.BUY, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=NEW");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, lastPx=1010, orderQty=1000, leavesQty=500, cumQty=500, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, lastPx=1011, orderQty=1000, leavesQty=200, cumQty=800, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, cumQty=300, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, timeInForce=IMMEDIATE_OR_CANCEL, execType=EXPIRED, ordStatus=EXPIRED");
    assertMessages();
  }

  // Add IOD order that partially fills against orders on the book across multiple price points, assert fills and IOC expiry
  @Test
  public void sellImmediateOrCancelPartiallyFilledBySweepAndExpires() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 300, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 1000, Side.SELL, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=1000, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=NEW");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1010, lastPx=1011, orderQty=1000, leavesQty=500, cumQty=500, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1010, lastPx=1010, orderQty=1000, leavesQty=200, cumQty=800, timeInForce=IMMEDIATE_OR_CANCEL, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=0, cumQty=300, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, timeInForce=IMMEDIATE_OR_CANCEL, execType=EXPIRED, ordStatus=EXPIRED");
    assertMessages();
  }

  // Add FOK buy order that does not fill, assert expiry
  @Test
  public void buyFillOrKillNotFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 800, Side.BUY, FILL_OR_KILL));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=800, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, timeInForce=FILL_OR_KILL, execType=EXPIRED, ordStatus=EXPIRED");
    assertMessages();
  }

  // Add FOK sell order that does not fill, assert expiry
  @Test
  public void sellFillOrKillNotFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 800, Side.SELL, FILL_OR_KILL));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=800, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, timeInForce=FILL_OR_KILL, execType=EXPIRED, ordStatus=EXPIRED");
    assertMessages();
  }

  // Add FOK buy order that fill (contra order filled), assert fills
  @Test
  public void buyFillOrKillFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, FILL_OR_KILL));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
  }

  // Add FOK sell order that fill (contra order filled), assert fills
  @Test
  public void sellFillOrKillFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, FILL_OR_KILL));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    assertMessages();
  }

  // Add FOK buy order that fill (contra order partially filled), assert fills
  @Test
  public void buyFillOrKillFilledPartially() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 800, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=800, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, FILL_OR_KILL));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=800, leavesQty=300, cumQty=500, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add FOK sell order that fill (contra order partially filled), assert fills
  @Test
  public void sellFillOrKillFilledPartially() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 800, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=800, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, FILL_OR_KILL));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=800, leavesQty=300, cumQty=500, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add FOK sell order that fill (multiple contra orders), assert fills
  @Test
  public void buyFillOrKillFilledMultiple() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 600, Side.BUY, FILL_OR_KILL));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=600, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=600, leavesQty=100, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=600, leavesQty=0, cumQty=600, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=200, cumQty=100, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add FOK buy order that fill (multiple contra orders), assert fills
  @Test
  public void sellFillOrKillFilledMultiple() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 600, Side.SELL, FILL_OR_KILL));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=600, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=600, leavesQty=100, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=600, leavesQty=0, cumQty=600, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=200, cumQty=100, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }


  // Add FOK sell order that fill (multiple crossed contra orders), assert fills
  @Test
  public void buyFillOrKillFilledMultipleCrossed() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 600, Side.BUY, FILL_OR_KILL));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=600, ordStatus=NEW");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, lastPx=1010, orderQty=600, leavesQty=100, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, lastPx=1010, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, lastPx=1011, orderQty=600, leavesQty=0, cumQty=600, ordStatus=FILLED");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, lastPx=1011, orderQty=300, leavesQty=200, cumQty=100, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add FOK buy order that fill (multiple crossed contra orders), assert fills
  @Test
  public void sellFillOrKillFilledMultipleCrossed() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1012, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 600, Side.SELL, FILL_OR_KILL));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=600, ordStatus=NEW");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, lastPx=1012, orderQty=600, leavesQty=100, cumQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1012, lastPx=1012, orderQty=500, leavesQty=0, cumQty=500, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1011, lastPx=1011, orderQty=600, leavesQty=0, cumQty=600, ordStatus=FILLED");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=BUY, price=1011, lastPx=1011, orderQty=300, leavesQty=200, cumQty=100, ordStatus=PARTIALLY_FILLED");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy order without user, assert business reject
  @Test
  public void rejectBuyOrderWithNoUser() {
    Order order = createOrder(1, null, pair.getId(), 1011, 500, Side.BUY, DAY);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=USER_NOT_FOUND, text=User not found");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell order without user, assert business reject
  @Test
  public void rejectSellOrderWithNoUser() {
    Order order = createOrder(1, null, pair.getId(), 1011, 500, Side.SELL, DAY);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=USER_NOT_FOUND, text=User not found");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy order with invalid user, assert business reject
  @Test
  public void rejectBuyOrderWithInvalidUser() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY);
    order.setAccount(10000);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=UNABLE_TO_LOAD_USER, text=Unable to load user");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell order with invalid user, assert business reject
  @Test
  public void rejectSellOrderWithInvalidUser() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    order.setAccount(10000);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=UNABLE_TO_LOAD_USER, text=Unable to load user");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy order without price, assert business reject
  @Test
  public void rejectBuyOrderWithNoPrice() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY);
    order.setPrice(0, (short) 0);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=PRICE_IS_MISSING, text=Price is missing");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell order without price, assert business reject
  @Test
  public void rejectSellOrderWithNoPrice() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    order.setPrice(0, (short) 0);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=PRICE_IS_MISSING, text=Price is missing");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add buy order without quantity, assert business reject
  @Test
  public void rejectBuyOrderWithNoQuantity() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY);
    order.setQty(0, (short) 0);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=QUANTITY_IS_MISSING, text=Quantity is missing");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add sell order without quantity, assert business reject
  @Test
  public void rejectSellOrderWithNoQuantity() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    order.setQty(0, (short) 0);


    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=QUANTITY_IS_MISSING, text=Quantity is missing");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Add order without balance, assert business reject because it failed preorder check
  // Add 2 sell orders that should pass preorder checks
  // Add another order that have too much quantity to pass preorder check, assert business reject because it failed preorder check
  // Cancel an order, verify that preorder check increases the available quantity, assert
  // Add another order, verify that is passes the preorder check, which would have failed if the order wasn't cancelled, assert
  // Add 2 buy orders, which fill against the sell orders, assert
  // Add another buy order that fails preorder check, assert
  @Test
  public void testPreOrderCheck() {
    User user = createUser(300);
    expectMessage("userId=300");

    // Add order without balance, assert business reject because it failed preorder check
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("ordType=LIMIT, side=BUY, ordStatus=REJECT");
    expectMessage("businessRejectReason=FAILED_PRE_CREDIT_CHECK, text=Failed pre-credit check");
    assertMessages();

    // Give some positions to the user
    user.addPosition(pair.getQuotedId(), 5000, null, 0, TokenType.ERC20);
    user.addPosition(pair.getId(), 50, null, 0, TokenType.ERC20);

    // Add 2 sell orders that should pass preorder checks
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 5000, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 5000, Side.SELL, DAY));
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    assertMessages();

    // Add another order that have too much quantity to pass preorder check, assert business reject because it failed preorder check
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1011, 500000, Side.SELL, DAY));
    expectMessage("orderQty=500000, leavesQty=0, ordStatus=REJECT");
    expectMessage("businessRejectReason=FAILED_PRE_CREDIT_CHECK, text=Failed pre-credit check");
    assertMessages();

    // Cancel an order, verify that preorder check increases the available quantity, assert
    orderBook.cancelOrder(createCancelOrder(5, 2, user, pair.getId(), 1011, 5000, Side.SELL, DAY));
    expectMessage("orderQty=5000, ordStatus=PENDING_CANCEL");
    expectMessage("orderQty=5000, ordStatus=CANCELED");
    assertMessages();

    // Add another order, verify that is passes the preorder check, which would have failed if the order wasn't cancelled, assert
    orderBook.addOrder(createOrder(6, user, pair.getId(), 1011, 5000, Side.SELL, DAY));
    expectMessage("ordType=LIMIT, side=SELL, ordStatus=NEW");
    assertMessages();

    // Add 2 buy orders, which fill against the sell orders, assert
    orderBook.addOrder(createOrder(7, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("ordType=LIMIT, side=BUY, ordStatus=NEW");
    expectMessage("orderQty=500, leavesQty=0, lastQty=500, lastPx=1011, ordStatus=FILLED");
    expectMessage("orderQty=5000, leavesQty=4500, lastQty=500, lastPx=1011, cumQty=500, ordStatus=PARTIALLY_FILLED");

    orderBook.addOrder(createOrder(7, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("ordType=LIMIT, side=BUY, ordStatus=NEW");
    expectMessage("orderQty=500, leavesQty=0, lastQty=500, lastPx=1011, ordStatus=FILLED");
    expectMessage("orderQty=5000, leavesQty=4000, lastQty=500, lastPx=1011, cumQty=1000, ordStatus=PARTIALLY_FILLED");
    assertMessages();

    // Add another buy order that fails preorder check, assert
    orderBook.addOrder(createOrder(8, user, pair.getId(), 1000, 150000, Side.BUY, DAY));
    expectMessage("orderQty=150000, leavesQty=0, ordStatus=REJECT");
    expectMessage("businessRejectReason=FAILED_PRE_CREDIT_CHECK, text=Failed pre-credit check");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());
  }

  @Test
  public void rejectSellOrderWithNoQuantity2() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    order.setQty(0, (short) 0);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=QUANTITY_IS_MISSING, text=Quantity is missing");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Cancel buy order of a different user providing all attributes, assert rejection
  @Test
  public void cancelAnotherUsersBuyOrder() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 1, user2, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, account=19, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER, ordStatus=NEW");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Cancel sell order of a different user providing all attributes, assert rejection
  @Test
  public void cancelAnotherUsersSellOrder() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 1, user2, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=19, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER, ordStatus=NEW");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Cancel buy order of a different user without providing order id, assert rejection
  @Test
  public void cancelAnotherUsersBuyOrderWithoutOrderId() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 0, user2, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=0, account=19, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=0, account=19, cxlRejReason=UNKNOWN_ORDER, ordStatus=NEW");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());

  }

  // Cancel sell order of a different user without providing order id, assert rejection
  @Test
  public void cancelAnotherUsersSellOrderWithoutOrderId() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 0, user2, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=0, account=19, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=0, account=19, cxlRejReason=UNKNOWN_ORDER, ordStatus=NEW");
    assertMessages();
  }

  // Cancel buy order of a different user without providing price, assert rejection
  @Test
  public void cancelAnotherUsersBuyOrderWithoutPrice() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 1, user2, pair.getId(), 0, 500, Side.BUY, DAY));
    expectMessage("orderId=1, account=19, ordType=LIMIT, side=BUY, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER, ordStatus=NEW");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());
  }

  // Cancel sell order of a different user without providing price, assert rejection
  @Test
  public void cancelAnotherUserSellOrderWithoutPrice() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=18, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 1, user2, pair.getId(), 0, 500, Side.SELL, DAY));
    expectMessage("orderId=1, account=19, ordType=LIMIT, side=SELL, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1, account=19, cxlRejReason=UNKNOWN_ORDER, ordStatus=NEW");
    assertMessages();
    Assert.assertEquals(0, validator.validate().size());
  }

  // Cancel on request
  @Test
  public void cancelOnRequest() {
    orderBook.addOrder(createOrder(1, user7, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    final CancelOrder cancelOrder = createCancelOrder(2, 1, user7, pair.getId(), 1011, 500, Side.BUY, DAY);
    cancelOrder.setCancelType((short) CANCEL_ON_REQUEST);

    orderBook.cancelOrder(cancelOrder);
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED, cancelType=0");
    assertMessages();

    expectOutput("BalanceAdminMessage", "userId=24", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("Order", "securityId=12, orderId=1, ordType=LIMIT, side=BUY, price=1011, qty=500, qty_scale=2");
    expectOutput("BalanceAdminMessage", "userId=24", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("CancelOrder", "securityId=12, price=1011, side=BUY, qty=5, origOrderId=1, cancelId=2, cancelPriority=0, cancelType=0");
    assertOutputMessages();

    Assert.assertEquals(0, validator.validate().size());
  }

  // Cancel on disconnect
  @Test
  public void cancelOnDisconnect() {
    orderBook.addOrder(createOrder(1, user7, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    final CancelOrder cancelOrder = createCancelOrder(2, 1, user7, pair.getId(), 1011, 500, Side.BUY, DAY);
    cancelOrder.setCancelType((short) CANCEL_ON_DISCONNECT);

    orderBook.cancelOrder(cancelOrder);
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED, cancelType=1");
    assertMessages();

    expectOutput("BalanceAdminMessage", "userId=24", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("Order", "securityId=12, orderId=1, ordType=LIMIT, side=BUY, price=1011, qty=500, qty_scale=2");
    expectOutput("BalanceAdminMessage", "userId=24", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("CancelOrder", "securityId=12, price=1011, side=BUY, qty=5, origOrderId=1, cancelId=2, cancelPriority=0, cancelType=1");
    assertOutputMessages();

    Assert.assertEquals(0, validator.validate().size());
  }

  // Cancel on logout
  @Test
  public void cancelOnLogout() {
    orderBook.addOrder(createOrder(1, user7, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    final CancelOrder cancelOrder = createCancelOrder(2, 1, user7, pair.getId(), 1011, 500, Side.BUY, DAY);
    cancelOrder.setCancelType((short) CANCEL_ON_LOGOUT);

    orderBook.cancelOrder(cancelOrder);
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED, cancelType=2");
    assertMessages();

    expectOutput("BalanceAdminMessage", "userId=24", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("Order", "securityId=12, orderId=1, ordType=LIMIT, side=BUY, price=1011, qty=500, qty_scale=2");
    expectOutput("BalanceAdminMessage", "userId=24", "Balance [assetId=12, balance=.00, balance_change=0");
    expectOutput("CancelOrder", "securityId=12, price=1011, side=BUY, qty=5, origOrderId=1, cancelId=2, cancelPriority=0, cancelType=2");
    assertOutputMessages();

    Assert.assertEquals(0, validator.validate().size());
  }
}
