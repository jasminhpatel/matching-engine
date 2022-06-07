package com.solfini.matchengine.model.orderbook;

import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.user.User;
import com.solfini.util.MbxMath;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.*;

public class OrderBookCashPreOrderCheckTest extends OrderBookTest {

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 4, 3));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT, UpdateType.PUT, "BTC/USDT", BTC, USDT, 2, 2, CASH_PREORDER_CHECK));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT + ", symbol=BTC/USDT, updateType=PUT");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT + ", symbol=BTC/USDT");

    pair = InstrumentCache.getPair(BTC_USDT);

    orderBook = new OrderBookTest.OrderBookWrapper(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.CASH_PREORDER_CHECK, pair);
    orderBook.orderBook().setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));

    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(100);
    pair.setFee(new Fee(pair.getId(), USDT, 1, FeeType.PERCENT, MakerTaker.ALL, 0, true));
  }

  @Override
  protected void createUsers() {
    super.createUsers();
    user.addPosition(BTC, 10_000_00, null);
    user.addPosition(BTC_USDT, 10_000_00, null);

    user.getPosition(BTC_USDT).getUserOpenOrdersByPair().set(user, InstrumentCache.getPair(BTC_USDT));
  }

  // Add order for non existing security, assert rejection
  @Test
  public void rejectOrderForNonExistingSecurity() {
    orderBook.addOrder(createOrder(1, user, 100, 1011, 300, Side.BUY, DAY));
    expectMessage("BusinessRejectMessage",
        "businessRejectReason=INSTRUMENT_NOT_FOUND, text=Instrument not found, refMsgType=ORDER_SINGLE, businessRejectRefID=1, orderId=1");
    assertMessages();
  }

  // Add order for invalid security, assert rejection
  @Test
  public void rejectOrderForInvalidSecurity() {
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(101, UpdateType.PUT, "BTC/USDT", BTC, USDT, 2, 2));
    expectMessage("securityId=101, symbol=BTC/USDT, updateType=PUT");

    orderBook.orderBook().addOrder(createOrder(1, user, 101, 1011, 300, Side.BUY, DAY));
    expectMessage("BusinessRejectMessage",
        "businessRejectReason=INVALID_ORDER_SECURITY, text=Order security is invalid, refMsgType=ORDER_SINGLE, businessRejectRefID=1, orderId=1");
    assertMessages();
  }

  // Add buy order, assert
  @Test
  public void addBuyOrder() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("ExecutionReportMessage", "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();

    expectOutput("BalanceAdminMessage", "userId=" + user.getId());
    expectOutput("Order", "orderId=1, ordType=LIMIT, side=BUY, price=1011, price_scale=2, qty=5, qty_scale=2");
    assertOutputMessages();
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
  }

  // Add buy order and then a sell order with the same price and quality, assert fills
  @Test
  public void exactMatchSellOrderToBuyOrderOnBook() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell order and then a buy order with the same price and quality, assert fills
  @Test
  public void exactMatchBuyOrderToSellOrderOnBook() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
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
  }

  // Add sell orders at different price points, assert
  // Add buy order that fills against multiple buy orders at multiple price points (with a fill on the last buy)
  @Test
  public void sweepSellOrdersMultiplePriceMultipleOrdersFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createOrder(100, user, pair.getId(), 1011, 1300, Side.BUY, DAY));
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
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    assertMessages();
  }

  // Add sell order that rest
  // Cancel, assert cancellation
  @Test
  public void cancelSellOrderResting() {
    orderBook.addOrder(createOrder(110, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=110, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(111, 110, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=110, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=110, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=CANCELED");
    assertMessages();
  }

  // Add buy and sell orders to partially fill the buy order
  // Cancel the buy order, assert cancellation
  @Test
  public void cancelBuyOrderPartiallyFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=200, ordStatus=PARTIALLY_FILLED");

    orderBook.cancelOrder(createCancelOrder(3, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    assertMessages();
  }

  // Add buy and sell orders to partially fill the sell order
  // Cancel the sell order, assert cancellation
  @Test
  public void cancelSellOrderPartiallyFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 300, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=300, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=200, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, ordStatus=FILLED");

    orderBook.cancelOrder(createCancelOrder(3, 2, user, pair.getId(), 1011, 500, Side.SELL, DAY));
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
    userk.addPosition(BTC, 10_000_00, null);
    userk.addPosition(USDT, 10_000_00, null);
    userk.addPosition(BTC_USDT, 10_000_00, null);

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
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 600, Side.BUY, IMMEDIATE_OR_CANCEL));
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
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 1000, Side.BUY, IMMEDIATE_OR_CANCEL));
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
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 300, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 1000, Side.BUY, IMMEDIATE_OR_CANCEL));
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
  }

  // Add buy order without user, assert business reject
  @Test
  public void rejectBuyOrderWithNoUser() {
    Order order = createOrder(1, null, pair.getId(), 1011, 500, Side.BUY, DAY);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=USER_NOT_FOUND, text=User not found");
    assertMessages();
  }

  // Add sell order without user, assert business reject
  @Test
  public void rejectSellOrderWithNoUser() {
    Order order = createOrder(1, null, pair.getId(), 1011, 500, Side.SELL, DAY);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=USER_NOT_FOUND, text=User not found");
    assertMessages();
  }

  // Add buy order with invalid user, assert business reject
  @Test
  public void rejectBuyOrderWithInvalidUser() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY);
    order.setAccount(10000);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=UNABLE_TO_LOAD_USER, text=Unable to load user");
    assertMessages();
  }

  // Add sell order with invalid user, assert business reject
  @Test
  public void rejectSellOrderWithInvalidUser() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    order.setAccount(10000);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=UNABLE_TO_LOAD_USER, text=Unable to load user");
    assertMessages();
  }

  // Add buy order without price, assert business reject
  @Test
  public void rejectBuyOrderWithNoPrice() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY);
    order.setPrice(0, (short) 0);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=PRICE_IS_MISSING, text=Price is missing");
    assertMessages();
  }

  // Add sell order without price, assert business reject
  @Test
  public void rejectSellOrderWithNoPrice() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    order.setPrice(0, (short) 0);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=PRICE_IS_MISSING, text=Price is missing");
    assertMessages();
  }

  // Add buy order without quantity, assert business reject
  @Test
  public void rejectBuyOrderWithNoQuantity() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY);
    order.setQty(0, (short) 0);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=QUANTITY_IS_MISSING, text=Quantity is missing");
    assertMessages();
  }

  // Add sell order without quantity, assert business reject
  @Test
  public void rejectSellOrderWithNoQuantity() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    order.setQty(0, (short) 0);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=QUANTITY_IS_MISSING, text=Quantity is missing");
    assertMessages();
  }

  @Test
  public void rejectSellOrderWithNoQuantity2() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY);
    order.setQty(0, (short) 0);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=QUANTITY_IS_MISSING, text=Quantity is missing");
    assertMessages();
  }

  // Cancelled buy order should be removed from user's open orders
  @Test
  public void cancelBuyOrderRemovedFromUserOpenOrders() {
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.BUY));
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.BUY));

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.BUY));
    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.BUY));

    orderBook.cancelOrder(createCancelOrder(2, 1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    assertMessages();

    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.BUY));
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.BUY));
  }

  // Cancelled sell order should be removed from user's open orders
  @Test
  public void cancelSellOrderRemovedFromUserOpenOrders() {
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.SELL));
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.SELL));

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.SELL));
    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.SELL));

    orderBook.cancelOrder(createCancelOrder(2, 1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=CANCELED");
    assertMessages();

    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.SELL));
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.SELL));
  }

  // Cancel replace buy order with the same client order id
  @Test
  public void cancelReplaceBuyOrderWithSameClientOrderId() {
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.BUY));
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.BUY));

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=NEW");

    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.BUY));
    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.BUY));
    Assert.assertEquals(1, user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.BUY).getOrderId());

    orderBook.cancelReplaceOrder(createCancelReplaceOrder(10, 0, user, pair.getId(), 1011, 500, Side.BUY, DAY,
        createOrder(2, user, pair.getId(), 1012, 500, Side.BUY, DAY)));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=NEW");
    assertMessages();

    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.BUY));
    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.BUY));
    Assert.assertEquals(2, user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.BUY).getOrderId());

    orderBook.cancelReplaceOrder(createCancelReplaceOrder(11, 0, user, pair.getId(), 1012, 500, Side.BUY, DAY,
        createOrder(3, user, pair.getId(), 1013, 500, Side.BUY, DAY)));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1012, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1013, orderQty=500, ordStatus=NEW");
    assertMessages();

    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.BUY));
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(2, Side.BUY));
    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.BUY));
    Assert.assertEquals(3, user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.BUY).getOrderId());
  }

  // Cancel replace sell order with the same client order id
  @Test
  public void cancelReplaceSellOrderWithSameClientOrderId() {
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.SELL));
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.SELL));

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=NEW");

    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.SELL));
    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.SELL));
    Assert.assertEquals(1, user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.SELL).getOrderId());

    orderBook.cancelReplaceOrder(createCancelReplaceOrder(10, 0, user, pair.getId(), 1011, 500, Side.SELL, DAY,
        createOrder(2, user, pair.getId(), 1012, 500, Side.SELL, DAY)));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=500, ordStatus=NEW");
    assertMessages();

    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.SELL));
    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.SELL));
    Assert.assertEquals(2, user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.SELL).getOrderId());

    orderBook.cancelReplaceOrder(createCancelReplaceOrder(11, 0, user, pair.getId(), 1012, 500, Side.SELL, DAY,
        createOrder(3, user, pair.getId(), 1013, 500, Side.SELL, DAY)));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=500, ordStatus=CANCELED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1013, orderQty=500, ordStatus=NEW");
    assertMessages();

    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(1, Side.SELL));
    Assert.assertNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder(2, Side.SELL));
    Assert.assertNotNull(user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.SELL));
    Assert.assertEquals(3, user.getPosition(pair.getId()).getUserOpenOrdersByPair().lookupOrder("ClOrdId", Side.SELL).getOrderId());
  }

  private static double scaleFactor(final int scale) {
    double factor = 1;
    for (int i = 0; i < scale; i++) {
      factor = factor * 0.1;
    }
    return factor;
  }

  public static long calcFeeQuantity(final long quantityLong, final long referencePrice, final int fee) {
    final double quantityScaleFactor = scaleFactor(6);
    final double priceScaleFactor = scaleFactor(2);
    final double quotedCoinUsdMark = 1.0;
    final double feeInstrumentUsdMark = 1.0;
    final int quantityScaleMultiplier = 1000000;

    final double adjReferenceQuantity = MbxMath.roundToBestPrecision(quantityLong * quantityScaleFactor);
    final double notional = MbxMath.roundToBestPrecision(referencePrice * adjReferenceQuantity * priceScaleFactor);
    final double usdNotional = MbxMath.roundToBestPrecision(Math.abs(notional * quotedCoinUsdMark));

    final double usdFee = MbxMath.roundToBestPrecision(usdNotional * fee * .000001);
    System.out.println("usdFee = " + usdFee);

    double feeInFeeInstrument = usdFee / feeInstrumentUsdMark;
    System.out.println("feeInFeeInstrument = " + feeInFeeInstrument);

    feeInFeeInstrument = feeInFeeInstrument * quantityScaleMultiplier;

    System.out.println("feeInFeeInstrument = " + feeInFeeInstrument);
    final long feeQuantity = (long) feeInFeeInstrument;
    System.out.println(feeQuantity);

    return feeQuantity;
  }

  @Test
  public void feeRoundOffError() {
    Assert.assertEquals(48600, calcFeeQuantity(6000, 9000_00, 900));
    Assert.assertEquals(48600, calcFeeQuantity(6000, 9000_00, 900));
    Assert.assertEquals(33600, calcFeeQuantity(2000, 11200_00, 1500));
    Assert.assertEquals(16800, calcFeeQuantity(1000, 11200_00, 1500));
  }
}
