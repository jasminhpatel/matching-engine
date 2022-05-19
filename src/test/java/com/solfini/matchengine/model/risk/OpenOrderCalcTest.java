package com.solfini.matchengine.model.risk;

import static com.solfini.sbe.encoder.TimeInForce.DAY;
import static org.junit.Assert.assertEquals;

import java.util.Properties;

import com.solfini.instrument.Position;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.user.User;
import org.junit.*;
import com.solfini.sbe.encoder.Side;

public class OpenOrderCalcTest extends RiskTest {

  @Override
  protected void onConfigure(final Properties properties) {
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "500000");
  }

  // Add buy order, assert open orders and exposure
  @Test
  public void buyOrder() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 10000, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=10000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals((200 + 100) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add multiple buy orders, assert open orders and exposure
  @Test
  public void multipleBuyOrders() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 1000, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(2, user, pair.getId(), 495000, 1000, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(2, user.getOpenOrderCount());
    assertEquals((200 + 20) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(3, user, pair.getId(), 495000, 1000, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(3, user.getOpenOrderCount());
    assertEquals((200 + 30) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add multiple buy orders at different prices, assert open orders and exposure
  @Test
  public void multipleBuyOrdersAtDifferentPrices() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 1000, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(2, user, pair.getId(), 494000, 1000, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=494000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(2, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950 + 10 * 4940, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(3, user, pair.getId(), 493000, 1000, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=493000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(3, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950 + 10 * 4940 + 10 * 4930, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add multiple buy orders followed by multiple sell orders, assert open orders and exposure
  @Test
  public void multipleBuyOrdersFollowedByMultipleSellOrders() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 1000, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(2, user, pair.getId(), 494000, 1000, Side.BUY, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=494000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(2, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950 + 10 * 4940, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(3, user, pair.getId(), 493000, 1000, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=493000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(3, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950 + 10 * 4940 + 10 * 4930, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(4, user, pair.getId(), 496000, 1000, Side.SELL, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=496000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(4, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950 + 10 * 4940 + 10 * 4930, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(5, user, pair.getId(), 497000, 1000, Side.SELL, DAY));
    expectMessage("orderId=5, ordType=LIMIT, side=SELL, price=497000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(5, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950 + 10 * 4940 + 10 * 4930, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(6, user, pair.getId(), 498000, 1000, Side.SELL, DAY));
    expectMessage("orderId=6, ordType=LIMIT, side=SELL, price=498000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(6, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950 + 10 * 4940 + 10 * 4930, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add buy order and cancel it, assert open orders and exposure
  @Test
  public void buyOrderFollowedByCancel() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 1000, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.cancelOrder(createCancelOrder(2, 1, user, pair.getId(), 495000, 1000, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=CANCELED");
    assertMessages();

    assertEquals(0, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add buy order and cancel-replace it, assert open orders and exposure
  @Test
  public void buyOrderFollowedByCancelReplace() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 1000, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.cancelReplaceOrder(createCancelReplaceOrder(2, 1, user, pair.getId(), 495000, 1000, Side.BUY, DAY, createOrder(1, user, pair.getId(), 495100, 2000, Side.BUY, DAY)));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=CANCELED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495100, orderQty=2000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals(200 * 4950 + 20 * 4951, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add buy order, add sell order by counter-party that fills, assert open orders and exposure
  @Test
  public void buyOrderAndFill() {
    User user = nextUser();
    User counterparty = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 100, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 100, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, counterparty, pair.getId(), 495000, 100, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=100, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=495000, orderQty=100, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=495000, orderQty=100, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=100, ordStatus=FILLED");
    assertMessages();

    assertEquals(0, user.getOpenOrderCount());
    assertPositions(user, 20000, 20000, 20100);
    assertEquals((200 + 1) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add multiple buy orders, add sell order by counter-party that fill one of the buy orders, assert open orders and exposure
  @Test
  public void multipleBuyOrdersAndFill() {
    User user = nextUser();
    User counterparty = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 100, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 100, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 495000, 100, Side.BUY, DAY));
    orderBook.addOrder(createOrder(3, counterparty, pair.getId(), 495000, 100, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=100, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=495000, orderQty=100, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=495000, orderQty=100, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=495000, orderQty=100, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=100, ordStatus=FILLED");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertPositions(user, 20000, 20000, 20100);
    assertEquals((200 + 2) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add sell order, assert open orders and exposure
  @Test
  public void sellOrder() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 496000, 10000, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=496000, orderQty=10000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    // this is a worst case calc, so because the order is closing an existing position it isn't counted!
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add sell order to result in a short position, assert open orders and exposure
  @Test
  public void sellOrderShort() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 496000, 30000, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=496000, orderQty=30000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add multiple sell orders, assert open orders and exposure
  @Test
  public void multipleSellOrders() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 496000, 1000, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=496000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(2, user, pair.getId(), 496000, 1000, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=496000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(2, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(3, user, pair.getId(), 496000, 1000, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=496000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(3, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add multiple sell orders at different prices, assert open orders and exposure
  @Test
  public void multipleSellOrdersAtDifferentPrices() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 496000, 1000, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=496000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(2, user, pair.getId(), 497000, 1000, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=497000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(2, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(3, user, pair.getId(), 498000, 1000, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=498000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(3, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add multiple sell orders followed by multiple buy orders, assert open orders and exposure
  @Test
  public void multipleSellOrdersFollowedByMultipleBuyOrders() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 496000, 1000, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=496000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(2, user, pair.getId(), 497000, 1000, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=497000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(2, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(3, user, pair.getId(), 498000, 1000, Side.SELL, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=SELL, price=498000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(3, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(4, user, pair.getId(), 495000, 1000, Side.BUY, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=BUY, price=495000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(4, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(5, user, pair.getId(), 494000, 1000, Side.BUY, DAY));
    expectMessage("orderId=5, ordType=LIMIT, side=BUY, price=494000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(5, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950 + 10 * 4940, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.addOrder(createOrder(6, user, pair.getId(), 493000, 1000, Side.BUY, DAY));
    expectMessage("orderId=6, ordType=LIMIT, side=BUY, price=493000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(6, user.getOpenOrderCount());
    assertEquals((200 + 10) * 4950 + 10 * 4940 + 10 * 4930, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add sell order and cancel it, assert open orders and exposure
  @Test
  public void sellOrderFollowedByCancel() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 1000, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=495000, orderQty=1000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.cancelOrder(createCancelOrder(2, 1, user, pair.getId(), 495000, 1000, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=495000, orderQty=1000, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=495000, orderQty=1000, ordStatus=CANCELED");
    assertMessages();

    assertEquals(0, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add sell order and cancel-replace it, assert open orders and exposure
  @Test
  public void sellOrderFollowedByCancelReplace() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 496000, 10000, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=496000, orderQty=10000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);

    orderBook.cancelReplaceOrder(createCancelReplaceOrder(2, 1, user, pair.getId(), 496000, 1000, Side.SELL, DAY, createOrder(1, user, pair.getId(), 496100, 2000, Side.SELL, DAY)));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=496000, orderQty=1000, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=496000, orderQty=1000, ordStatus=CANCELED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=496100, orderQty=2000, ordStatus=NEW");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertEquals(200 * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add sell order, add buy order by counter-party that fills, assert open orders and exposure
  @Test
  public void sellOrderAndFill() {
    User user = nextUser();
    User counterparty = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 10000, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, counterparty, pair.getId(), 495000, 10000, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=495000, orderQty=10000, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=495000, orderQty=10000, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=BUY, price=495000, orderQty=10000, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=495000, orderQty=10000, ordStatus=FILLED");
    assertMessages();

    assertEquals(0, user.getOpenOrderCount());
    assertPositions(user, 20000, 20000 + 495000 * 100, 10000);
    assertEquals((200 - 100) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add multiple sell orders, add buy order by counter-party that fill one of the sell orders, assert open orders and exposure
  @Test
  public void multipleSellOrdersAndFill() {
    User user = nextUser();
    User counterparty = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 10000, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 495000, 10000, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, counterparty, pair.getId(), 495000, 10000, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=495000, orderQty=10000, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=495000, orderQty=10000, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=495000, orderQty=10000, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=495000, orderQty=10000, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=495000, orderQty=10000, ordStatus=FILLED");
    assertMessages();

    assertEquals(1, user.getOpenOrderCount());
    assertPositions(user, 20000, 20000 + 495000 * 100, 10000);
    assertEquals((200 - 100) * 4950, user.getUsdMaxExposurePositionAndOpenOrdersValue(), 0.001);
  }

  // Add buy order, assert open orders, exposure and other risk parameters
  @Test
  public void riskParameters() {
    User user = nextUser();

    setLiquidationMode(true);
    setIndexFeedUsdMark(5000, 1, 4950);

    assertPositions(user, 20000, 20000, 20000);
    assertOpenOrders(user, BTC_USDT_F, 0, 0);

    MarginPreOrderCheckAndSettle preOrderCheck = new MarginPreOrderCheckAndSettle();
    final double[] usdMarkPricesToSet = new double[32];
    preOrderCheck.updateRisk(user, usdMarkPricesToSet);
    // usdt = 200.00
    // btc = 200 pos & 5000 mark = 1_000_000
    // BTC_USDT_F unrealized = 200 pos * 4950 contract mark = 990_000
    // usd value sum = 1990200
    assertEquals(990000, user.getUsdNotionalPositionValue(), .001);
    assertEquals(1990200, user.getUsdValue(), .001);
    assertEquals(37000, user.getUsdMarginMaintValue(), .001);
    assertEquals(74000, user.getUsdMarginRequiredValue(), .001);
    assertEquals(0, user.getUsdOpenOrdersRequiredValue(), .001);
    assertEquals(0.0185910963722239, user.getMarginRatio(), .001); // MarginRatio=37000 / 1990200
    assertEquals(990000, user.getUsdMaxExposurePositionAndOpenOrdersValue(), .001); // NotionalPosition + OpenOrders

    Position[] positionArr = user.getPositionArr();
    assertEquals(990000, positionArr[BTC_USDT_F].getUsdUnrealized(), .001);
    assertEquals(4950, positionArr[BTC_USDT_F].getQuotedUsdMark(), .001);

    System.out.println("user0=" + user);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 495000, 10000, Side.BUY, DAY)); // buy 100 at 4950, notional = 495000, required=49500
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=495000, orderQty=10000, ordStatus=NEW");
    assertMessages();

    System.out.println("user1=" + user);

    assertEquals(990_000, positionArr[BTC_USDT_F].getUsdUnrealized(), .001);
    assertEquals(4950, positionArr[BTC_USDT_F].getQuotedUsdMark(), .001);
    assertEquals(49500, user.getUsdOpenOrdersRequiredValue(), .001); // .1 * notoional
    assertEquals(37000, user.getUsdMarginMaintValue(), .001); // no change with open order
    assertEquals(0.0185910963722239, user.getMarginRatio(), .001); // no change with open order, MarginRatio=37000 / 1990200
    assertEquals(123_500, user.getUsdMarginRequiredValue(), .001); // prev 74000 + 49500 open orders required

    assertEquals(1, user.getOpenOrderCount());
    assertEquals(990_000, user.getUsdNotionalPositionValue(), .001); // only NotionalPosition
    assertEquals(495_000, user.getUsdOpenOrdersValue(), .001); // only OpenOrders
    assertEquals(1_485_000, user.getUsdMaxExposurePositionAndOpenOrdersValue(), .001); // NotionalPosition + OpenOrders
  }
}
