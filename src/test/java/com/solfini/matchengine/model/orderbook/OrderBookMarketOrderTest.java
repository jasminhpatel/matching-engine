package com.solfini.matchengine.model.orderbook;

import com.solfini.matchengine.message.internal.Order;
import org.junit.Test;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;

import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookMarketOrderTest extends OrderBookTest {

  // Add buy market order without sell orders, assert reject
  @Test
  public void buyMarketOrderWithoutSellOrders() {
    orderBook.addOrder(createMarketOrder(1, user, pair.getId(), 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=MARKET, side=BUY, price=0, orderQty=500, ordStatus=REJECTED");
    expectMessage("businessRejectReason=NO_LIQUIDITY_AVAILABLE");
    assertMessages();
  }

  // Add sell market order without buy orders, assert reject
  @Test
  public void sellMarketOrderWithoutBuyOrders() {
    orderBook.addOrder(createMarketOrder(1, user, pair.getId(), 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=MARKET, side=SELL, price=0, orderQty=500, ordStatus=REJECTED");
    expectMessage("businessRejectReason=NO_LIQUIDITY_AVAILABLE");
    assertMessages();
  }

  // Add sell limit order
  // Add buy market order with a larger quantity
  // Assert partial fill to match available quantity, and cancellation market order
  @Test
  public void buyMarketOrderWithoutSellLiquidity() {
    orderBook.addOrder(createOrder(1, user5, pair.getId(), 1010, 300, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user5, pair.getId(), 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=MARKET, side=BUY, price=0, orderQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=500, leavesQty=200, lastQty=300, cumQty=300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, lastQty=300, cumQty=300, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=MARKET, side=BUY, price=0, orderQty=500, leavesQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, ordType=MARKET, side=BUY, execType=CANCELED, ordStatus=CANCELED");
    assertMessages();
  }

  // Add buy limit order
  // Add sell market order with a larger quantity
  // Assert partial fill to match available quantity, and cancellation market order
  @Test
  public void sellMarketOrderWithoutBuyLiquidity() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 300, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=MARKET, side=SELL, price=0, orderQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=MARKET, side=SELL, price=0, lastPx=1010, orderQty=500, leavesQty=200, lastQty=300, cumQty=300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=0, lastQty=300, cumQty=300, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=MARKET, side=SELL, price=0, orderQty=500, leavesQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, ordType=MARKET, side=SELL, execType=CANCELED, ordStatus=CANCELED");
    assertMessages();
  }

  // Add sell limit order
  // Add buy market order with a exact quantity, assert fills
  @Test
  public void buyMarketOrderMatchSingleSellOrderFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=MARKET, side=BUY, price=0, orderQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }

  // Add buy limit order
  // Add sell market order with a exact quantity, assert fills
  @Test
  public void sellMarketOrderMatchSingleBuyOrderFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=MARKET, side=SELL, price=0, orderQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=MARKET, side=SELL, price=0, lastPx=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell limit order
  // Add buy market order with a lower quantity, assert partial fills and fills
  @Test
  public void buyMarketOrderMatchSingleSellOrderPartiallyFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 800, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=800, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=MARKET, side=BUY, price=0, orderQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=800, leavesQty=300, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add buy limit order
  // Add sell market order with a lower quantity, assert partial fills and fills
  @Test
  public void sellMarketOrderMatchSingleBuyOrderPartiallyFilled() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 800, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=800, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=MARKET, side=SELL, price=0, orderQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=MARKET, side=SELL, price=0, lastPx=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=800, leavesQty=300, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add buy orders at different price points, assert
  // Add sell market order that fills against a single buy order (partial)
  @Test
  public void sweepBuyOrdersSinglePriceSingleOrderPartialFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 100, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=SELL, price=0, orderQty=100, leavesQty=100, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=100, leavesQty=0, lastQty=100, cumQty=100, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=400, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add buy orders at different price points, assert
  // Add sell market order that fills against a single buy order (partial)
  @Test
  public void sweepBuyOrdersSinglePriceSingleOrderFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 500, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=SELL, price=0, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }

  // Add buy orders at different price points, assert
  // Add sell market order that fills against multiple buy orders at a single price point (with a partial fill on the last buy)
  @Test
  public void sweepBuyOrdersSinglePriceMultipleOrdersPartialFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 900, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=SELL, price=0, orderQty=900, leavesQty=900, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=900, leavesQty=400, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=900, leavesQty=100, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=900, leavesQty=0, lastQty=100, cumQty=900, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=200, leavesQty=100, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add buy orders at different price points, assert
  // Add sell market order that fills against multiple buy orders at a single price point (with a fill on the last buy)
  @Test
  public void sweepBuyOrdersSinglePriceMultipleOrdersFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 800, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=SELL, price=0, orderQty=800, leavesQty=800, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=800, leavesQty=300, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=800, leavesQty=0, lastQty=300, cumQty=800, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }

  // Add buy orders at different price points, assert
  // Add sell market order that fills against multiple buy orders at multiple price points (with a partial fill on the last buy)
  @Test
  public void sweepBuyOrdersMultiplePriceMultipleOrdersPartialFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 1400, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=SELL, price=0, orderQty=1400, leavesQty=1400, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=1400, leavesQty=900, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=1400, leavesQty=600, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=1400, leavesQty=400, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1010, orderQty=1400, leavesQty=100, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1010, orderQty=1400, leavesQty=0, lastQty=100, cumQty=1400, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=5, ordType=LIMIT, side=BUY, price=1010, orderQty=200, leavesQty=100, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add buy orders at different price points, assert
  // Add sell market order that fills against multiple buy orders at multiple price points (with a fill on the last buy)
  @Test
  public void sweepBuyOrdersMultiplePriceMultipleOrdersFill() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 1300, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=SELL, price=0, orderQty=1300, leavesQty=1300, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=1300, leavesQty=800, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=1300, leavesQty=500, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=1300, leavesQty=300, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1010, orderQty=1300, leavesQty=0, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }

  // Add buy orders at different price points, assert
  // Add sell market order that partially fills against multiple buy orders at multiple price points
  // Assert market order cancellation
  @Test
  public void sweepBuyOrdersMultiplePriceMultipleOrdersExhaust() {
    addBuyOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 2000, Side.SELL, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=SELL, price=0, orderQty=2000, leavesQty=2000, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=2000, leavesQty=1500, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=2000, leavesQty=1200, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1011, orderQty=2000, leavesQty=1000, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1010, orderQty=2000, leavesQty=700, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=SELL, price=0, lastPx=1010, orderQty=2000, leavesQty=500, lastQty=200, cumQty=1500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=5, ordType=LIMIT, side=BUY, price=1010, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=100, ordType=MARKET, side=SELL, price=0, orderQty=2000, leavesQty=2000, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=100, ordType=MARKET, side=SELL, execType=CANCELED, ordStatus=CANCELED");
    assertMessages();
  }

  // Add sell orders at different price points, assert
  // Add buy market order that fills against a single buy order (partial)
  @Test
  public void sweepSellOrdersSinglePriceSingleOrderPartialFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 100, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=BUY, price=0, orderQty=100, leavesQty=100, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=100, leavesQty=0, lastQty=100, cumQty=100, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=400, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add sell orders at different price points, assert
  // Add buy market order that fills against a single buy order (partial)
  @Test
  public void sweepSellOrdersSinglePriceSingleOrderFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 500, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=BUY, price=0, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell orders at different price points, assert
  // Add buy market order that fills against multiple buy orders at a single price point (with a partial fill on the last buy)
  @Test
  public void sweepSellOrdersSinglePriceMultipleOrdersPartialFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 900, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=BUY, price=0, orderQty=900, leavesQty=900, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=900, leavesQty=400, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=900, leavesQty=100, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=900, leavesQty=0, lastQty=100, cumQty=900, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=200, leavesQty=100, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add sell orders at different price points, assert
  // Add buy market order that fills against multiple buy orders at a single price point (with a fill on the last buy)
  @Test
  public void sweepSellOrdersSinglePriceMultipleOrdersFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 800, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=BUY, price=0, orderQty=800, leavesQty=800, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=800, leavesQty=300, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=800, leavesQty=0, lastQty=300, cumQty=800, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell orders at different price points, assert
  // Add buy market order that fills against multiple buy orders at multiple price points (with a partial fill on the last buy)
  @Test
  public void sweepSellOrdersMultiplePriceMultipleOrdersPartialFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 1400, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=BUY, price=0, orderQty=1400, leavesQty=1400, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=1400, leavesQty=900, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=1400, leavesQty=600, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=1400, leavesQty=400, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1011, orderQty=1400, leavesQty=100, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1011, orderQty=1400, leavesQty=0, lastQty=100, cumQty=1400, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=5, ordType=LIMIT, side=SELL, price=1011, orderQty=200, leavesQty=100, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  // Add sell orders at different price points, assert
  // Add buy market order that fills against multiple buy orders at multiple price points (with a fill on the last buy)
  @Test
  public void sweepSellOrdersMultiplePriceMultipleOrdersFill() {
    addSellOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 1300, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=BUY, price=0, orderQty=1300, leavesQty=1300, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=1300, leavesQty=800, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=1300, leavesQty=500, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=1300, leavesQty=300, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1011, orderQty=1300, leavesQty=0, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }

  // Add sell orders at different price points, assert
  // Add buy market order that partially fills against multiple buy orders at multiple price points
  // Assert market order cancellation
  @Test
  public void sweepSellOrdersMultiplePriceMultipleOrdersExhaust() {
    addSellOrdersForSweep();

    orderBook.addOrder(createMarketOrder(100, user, pair.getId(), 2000, Side.BUY, DAY));
    expectMessage("orderId=100, ordType=MARKET, side=BUY, price=0, orderQty=2000, leavesQty=2000, ordStatus=NEW");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=2000, leavesQty=1500, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=2000, leavesQty=1200, lastQty=300, cumQty=800, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=2000, leavesQty=1000, lastQty=200, cumQty=1000, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=1010, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1011, orderQty=2000, leavesQty=700, lastQty=300, cumQty=1300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=1011, orderQty=300, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=100, ordType=MARKET, side=BUY, price=0, lastPx=1011, orderQty=2000, leavesQty=500, lastQty=200, cumQty=1500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=5, ordType=LIMIT, side=SELL, price=1011, orderQty=200, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=100, ordType=MARKET, side=BUY, price=0, orderQty=2000, leavesQty=2000, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=100, ordType=MARKET, side=BUY, execType=CANCELED, ordStatus=CANCELED");
    assertMessages();
  }

  // Add buy market order without sell orders, assert reject
  // Cancel market order, assert reject
  @Test
  public void cancelBuyMarketOrder() {
    orderBook.addOrder(createMarketOrder(130, user, pair.getId(), 500, Side.BUY, DAY));
    expectMessage("orderId=130, ordType=MARKET, side=BUY, price=0, orderQty=500, ordStatus=REJECTED");
    expectMessage("businessRejectReason=NO_LIQUIDITY_AVAILABLE");
    assertMessages();

    orderBook.cancelOrder(createCancelOrder(131, 130, user, OrdType.MARKET, pair.getId(), 0, 500, Side.BUY, DAY));
    expectMessage("orderId=130, ordType=MARKET, side=BUY, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=130, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Add sell market order without buy orders, assert reject
  // Cancel market order, assert reject
  @Test
  public void cancelSellMarketOrder() {
    sellMarketOrderWithoutBuyOrders();

    orderBook.cancelOrder(createCancelOrder(2, 1, user, OrdType.MARKET, pair.getId(), 0, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=MARKET, side=SELL, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Add buy market order that partially fills
  // Cancel market order, assert reject
  @Test
  public void cancelBuyMarketOrderPartiallyFilled() {
    buyMarketOrderWithoutSellLiquidity();

    orderBook.cancelOrder(createCancelOrder(3, 2, user, OrdType.MARKET, pair.getId(), 0, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=MARKET, side=BUY, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Add sell market order that partially fills
  // Cancel market order, assert reject
  @Test
  public void cancelSellMarketOrderPartiallyFilled() {
    orderBook.addOrder(createOrder(120, user, pair.getId(), 1010, 300, Side.BUY, DAY));
    expectMessage("orderId=120, ordType=LIMIT, side=BUY, price=1010, orderQty=300, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(121, user, pair.getId(), 500, Side.SELL, DAY));
    expectMessage("orderId=121, ordType=MARKET, side=SELL, price=0, orderQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=121, ordType=MARKET, side=SELL, price=0, lastPx=1010, orderQty=500, leavesQty=200, lastQty=300, cumQty=300, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    expectMessage(
        "orderId=120, ordType=LIMIT, side=BUY, price=1010, orderQty=300, leavesQty=0, lastQty=300, cumQty=300, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=121, ordType=MARKET, side=SELL, price=0, orderQty=500, leavesQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=121, ordType=MARKET, side=SELL, execType=CANCELED, ordStatus=CANCELED");
    assertMessages();

    orderBook.cancelOrder(createCancelOrder(122, 121, user, OrdType.MARKET, pair.getId(), 0, 500, Side.SELL, DAY));
    expectMessage("orderId=121, ordType=MARKET, side=SELL, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=121, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Add buy market order that fills
  // Cancel market order, assert reject
  @Test
  public void cancelBuyMarketOrderFilled() {
    buyMarketOrderMatchSingleSellOrderFilled();

    orderBook.cancelOrder(createCancelOrder(3, 2, user7, OrdType.MARKET, pair.getId(), 0, 500, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=MARKET, side=BUY, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Add sell market order that fills
  // Cancel market order, assert reject
  @Test
  public void cancelSellMarketOrderFilled() {
    sellMarketOrderMatchSingleBuyOrderFilled();

    orderBook.cancelOrder(createCancelOrder(3, 2, user8, OrdType.MARKET, pair.getId(), 0, 500, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=MARKET, side=SELL, price=0, orderQty=500, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Add buy order without user, assert business reject
  @Test
  public void rejectBuyOrderWithNoUser() {
    Order order = createMarketOrder(1, null, pair.getId(), 500, Side.BUY, DAY);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=USER_NOT_FOUND, text=User not found");
    assertMessages();
  }

  // Add sell order without user, assert business reject
  @Test
  public void rejectSellOrderWithNoUser() {
    Order order = createMarketOrder(1, null, pair.getId(), 500, Side.SELL, DAY);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=USER_NOT_FOUND, text=User not found");
    assertMessages();
  }

  // Add buy order with invalid user, assert business reject
  @Test
  public void rejectBuyOrderWithInvalidUser() {
    Order order = createMarketOrder(1, user, pair.getId(), 500, Side.BUY, DAY);
    order.setAccount(10000);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=UNABLE_TO_LOAD_USER, text=Unable to load user");
    assertMessages();
  }

  // Add sell order with invalid user, assert business reject
  @Test
  public void rejectSellOrderWithInvalidUser() {
    Order order = createMarketOrder(1, user, pair.getId(), 500, Side.SELL, DAY);
    order.setAccount(10000);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=UNABLE_TO_LOAD_USER, text=Unable to load user");
    assertMessages();
  }

  // Add buy order without quantity, assert business reject
  @Test
  public void rejectBuyOrderWithNoQuantity() {
    Order order = createMarketOrder(1, user, pair.getId(), 500, Side.BUY, DAY);
    order.setQty(0, (short) 0);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=QUANTITY_IS_MISSING, text=Quantity is missing");
    assertMessages();
  }

  // Add sell order without quantity, assert business reject
  @Test
  public void rejectSellOrderWithNoQuantity() {
    Order order = createMarketOrder(1, user, pair.getId(), 500, Side.SELL, DAY);
    order.setQty(0, (short) 0);

    orderBook.addOrder(order);
    expectMessage("orderId=1, businessRejectReason=QUANTITY_IS_MISSING, text=Quantity is missing");
    assertMessages();
  }

  @Test
  public void buyMarketOrder_FOK_ExactLiquidity() {
    orderBook.addOrder(createOrder(1, user2, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 500, Side.BUY, TimeInForce.FILL_OR_KILL));
    expectMessage("orderId=2, ordType=MARKET, side=BUY, price=0, orderQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }

  @Test
  public void sellMarketOrder_FOK_ExactLiquidity() {
    orderBook.addOrder(createOrder(1, user2, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 500, Side.SELL, TimeInForce.FILL_OR_KILL));
    expectMessage("orderId=2, ordType=MARKET, side=SELL, price=0, orderQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=MARKET, side=SELL, price=0, lastPx=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }

  @Test
  public void buyMarketOrder_FOK_HigherLiquidity1() {
    orderBook.addOrder(createOrder(1, user2, pair.getId(), 1010, 1000, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=1000, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 500, Side.BUY, TimeInForce.FILL_OR_KILL));
    expectMessage("orderId=2, ordType=MARKET, side=BUY, price=0, orderQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=MARKET, side=BUY, price=0, lastPx=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=1000, leavesQty=500, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  @Test
  public void sellMarketOrder_FOK_HigherLiquidity1() {
    orderBook.addOrder(createOrder(1, user2, pair.getId(), 1010, 1000, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=1000, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 500, Side.SELL, TimeInForce.FILL_OR_KILL));
    expectMessage("orderId=2, ordType=MARKET, side=SELL, price=0, orderQty=500, ordStatus=NEW");
    expectMessage(
        "orderId=2, ordType=MARKET, side=SELL, price=0, lastPx=1010, orderQty=500, leavesQty=0, lastQty=500, cumQty=500, execType=TRADE, ordStatus=FILLED");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=1000, leavesQty=500, lastQty=500, cumQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  @Test
  public void buyMarketOrder_FOK_HigherLiquidity2() {
    orderBook.addOrder(createOrder(1, user2, pair.getId(), 1010, 400, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=400, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 1010, 400, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=1010, orderQty=400, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(3, user, pair.getId(), 500, Side.BUY, TimeInForce.FILL_OR_KILL));
    expectMessage("orderId=3, ordType=MARKET, side=BUY, price=0, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=2, ordStatus=PARTIALLY_FILLED");

    assertMessages();
  }

  @Test
  public void sellMarketOrder_FOK_HigherLiquidity2() {
    orderBook.addOrder(createOrder(1, user2, pair.getId(), 1010, 400, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=400, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 1010, 400, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=1010, orderQty=400, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(3, user, pair.getId(), 500, Side.SELL, TimeInForce.FILL_OR_KILL));
    expectMessage("orderId=3, ordType=MARKET, side=SELL, price=0, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=2, ordStatus=PARTIALLY_FILLED");

    assertMessages();
  }

  @Test
  public void buyMarketOrder_FOK_InadequateLiquidity() {
    orderBook.addOrder(createOrder(1, user2, pair.getId(), 1010, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 1000, Side.BUY, TimeInForce.FILL_OR_KILL));
    expectMessage("orderId=2, ordType=MARKET, side=BUY, price=0, orderQty=1000, ordStatus=NEW");
    expectMessage("orderId=2, ordType=MARKET, side=BUY, price=0, orderQty=1000, ordStatus=EXPIRED");
    assertMessages();
  }

  @Test
  public void sellMarketOrder_FOK_InadequateLiquidity() {
    orderBook.addOrder(createOrder(1, user2, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 1000, Side.SELL, TimeInForce.FILL_OR_KILL));
    expectMessage("orderId=2, ordType=MARKET, side=SELL, price=0, orderQty=1000, ordStatus=NEW");
    expectMessage("orderId=2, ordType=MARKET, side=SELL, price=0, orderQty=1000, ordStatus=EXPIRED");
    assertMessages();
  }

}
