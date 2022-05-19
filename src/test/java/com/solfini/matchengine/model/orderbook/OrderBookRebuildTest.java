package com.solfini.matchengine.model.orderbook;

import java.util.ArrayList;
import java.util.List;

import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.user.User;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookRebuildTest extends ModelTest {

  private User user = null;
  private int orderId = 0;
  private List<String> orders = new ArrayList<String>();

  @Override
  public void before() {
    super.before();

    user = createUser(100, new Balance(USDT, 1_000_000, 0, 0, 0));
    expectMessage("userId=100");

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PUT, "BTC/USDT[F]", BTC, USDT, 2, 2));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PUT");
    assertMessages();
  }

  @Override
  public void after() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().expireLiveSessionOrders();
    super.after();
  }

  private void updateSecurityDefinition(final String symbol, final int priceScale, final int quantityScale, final int arraySize,
      final int cacheDepth) {
    SecurityDefinitionAdminMessage message =
        createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, symbol, BTC, USDT, priceScale, quantityScale);

    if (arraySize > 0) {
      message.setArrSize(arraySize);
    }

    if (cacheDepth > 0) {
      message.setCacheDepth(cacheDepth);
    }

    InstrumentCache.updateSecurityDefinition(message);
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=" + symbol + ", updateType=PATCH");
    assertMessages();
  }

  private void submitOrders() {
    submit(1_01, 10_00, Side.BUY);
    submit(1_02, 11_00, Side.BUY);
    submit(1_03, 12_00, Side.BUY);
    submit(1_04, 13_00, Side.BUY);
    submit(1_10, 20_00, Side.SELL);
    submit(1_11, 21_00, Side.SELL);
  }

  private Order submitStopLimitOrder() {
    long price = 1_20;
    long stopPx = 1_21;
    long quantity = 10_00;
    Side side = Side.BUY;

    orderId++;
    Order order = createStopLimitOrder(orderId, user, BTC_USDT_F, price, stopPx, quantity, side, DAY);
    order.setPrice2(price, (short) 2);
    order.setPrice2Int((int) price);
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(order);
    expectMessage("orderId=" + orderId + ", ordType=STOP_LIMIT, side=" + (Side.BUY == side ? "BUY" : "SELL") + ", price=" + price
        + ", stopPx=" + stopPx + ", price2=" + price + ", orderQty=" + quantity + ", ordStatus=NEW");
    orders.add("orderId=" + orderId + ", ordType=STOP_LIMIT, side=" + (Side.BUY == side ? "BUY" : "SELL") + ", price=" + price + ", stopPx="
        + stopPx + ", price2=" + price + ", orderQty=" + quantity + ", ordStatus=NEW");

    return order;
  }

  private void submitOutOfBoundOrders() {
    submit(100_01, 10_00, Side.SELL);
    submit(100_02, 11_00, Side.SELL);
    submit(100_03, 12_00, Side.SELL);
    submit(100_04, 13_00, Side.SELL);
    submit(100_10, 20_00, Side.SELL);
    submit(100_11, 21_00, Side.SELL);
  }

  private void submit(final long price, final long quantity, final Side side) {
    orderId++;
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(orderId, user, BTC_USDT_F, price, quantity, side, DAY));
    expectMessage("orderId=" + orderId + ", ordType=LIMIT, side=" + (Side.BUY == side ? "BUY" : "SELL") + ", price=" + price + ", orderQty="
        + quantity + ", ordStatus=NEW");
    orders.add("orderId=" + orderId + ", ordType=LIMIT, side=" + (Side.BUY == side ? "BUY" : "SELL") + ", price=" + price + ", orderQty="
        + quantity + ", ordStatus=NEW");
  }

  private void restate() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().restate(1, null);
    expectMessage("orderId=0, ordType=PREVIOUSLY_INDICATED, account=0, price=0, orderQty=0, execType=RESTATE");
    for (String order : orders) {
      expectMessage(order);
    }
  }

  @Test
  public void orderBookProperties() {
    Assert.assertTrue(InstrumentCache.getPair(BTC_USDT_F).getOrderBook() instanceof ArrayOrderBook);

    ArrayOrderBook orderBook = (ArrayOrderBook) InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    Assert.assertEquals(BTC_USDT_F, orderBook.getId());
    Assert.assertEquals(2, orderBook.getQuanityScale());
    Assert.assertEquals(2, orderBook.getPriceScale());
    Assert.assertEquals(0, orderBook.getFilledCountGlobal());

    orderBook.setSettleCoinUsdMarkInstrument(InstrumentCache.get(USDT));
    Assert.assertEquals(USDT, orderBook.getSettleCoinUsdMarkInstrument().getId());

    orderBook.setMark(100);
    Assert.assertEquals(100, orderBook.getMark());
    Assert.assertEquals(0.0, orderBook.getUsdMark(), 0.001);

    Assert.assertEquals(0, orderBook.getLast());
  }

  // Restate an order book without a security definition update.
  // Order book should not be rebuilt and remain consistent.
  @Test
  public void noRebuildForNoSecurityDefinitionUpdate() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();
    submitStopLimitOrder();
    assertMessages();

    restate();
    assertMessages();

    Assert.assertTrue(orderBook == InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }

  // Restate an order book with a security definition update that does not change anything.
  // Order book should not be rebuilt and remain consistent.
  @Test
  public void noRebuildForUnchangedSecurityDefinitionUpdate() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();
    submitStopLimitOrder();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 2, 2, 0, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook == InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }

  // Restate an order book with a security definition update that change the symbol.
  // Order book should not be rebuilt and remain consistent.
  @Test
  public void noRebuildForSymbolChange() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();
    submitStopLimitOrder();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[FUTURE]", 2, 2, 0, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook == InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }

  // Restate an order book with a security definition update that increase the array size.
  // Order book should be rebuilt and remain consistent.
  @Test
  public void rebuildForArraySizeIncrease() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();
    submitStopLimitOrder();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 2, 2, 10_000, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }

  // Restate an order book with a security definition update that decrease the array size.
  // Order book should be rebuilt and remain consistent.
  @Test
  public void rebuildForArraySizeDecrease() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();
    submitStopLimitOrder();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 2, 2, 500, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }

  // Restate an order book with a security definition update that increase the cache depth.
  // Order book should be rebuilt and remain consistent.
  @Test
  public void rebuildForCacheDepthIncrease() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();
    submitStopLimitOrder();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 2, 2, 0, 128);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }

  // Restate an order book with a security definition update that decrease the cache depth.
  // Order book should be rebuilt and remain consistent.
  @Test
  public void rebuildForCacheDepthDecrease() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();
    submitStopLimitOrder();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 2, 2, 0, 32);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }

  // Restate an order book with a security definition update that increase the array size.
  // Order book contains out of bound orders that remain out of bound after the resize.
  // Order book should be rebuilt and remain consistent.
  @Ignore
  @Test
  public void rebuildForArraySizeIncreaseOutOfBoundOrdersExcluded() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOutOfBoundOrders();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 2, 2, 5_000, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }

  // Restate an order book with a security definition update that increase the array size.
  // Order book contains out of bound orders that remain out of bound after the resize.
  // Order book should be rebuilt and remain consistent.
  @Ignore
  @Test
  public void rebuildForArraySizeIncreaseWithOrdersOutOfBoundOrdersExcluded() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();
    submitStopLimitOrder();
    submitOutOfBoundOrders();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 2, 2, 5_000, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }

  // Restate an order book with a security definition update that increase the array size.
  // Order book contains out of bound orders that are moved to the array after the resize.
  // Order book should be rebuilt and remain consistent.
  @Ignore
  @Test
  public void rebuildForArraySizeIncreaseOutOfBoundOrdersIncluded() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOutOfBoundOrders();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 2, 2, 15_000, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }

  // Restate an order book with a security definition update that increase the array size.
  // Order book contains out of bound orders that are moved to the array after the resize.
  // Order book should be rebuilt and remain consistent.
  @Ignore
  @Test
  public void rebuildForArraySizeIncreaseWithOrdersOutOfBoundOrdersIncluded() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();
    submitOutOfBoundOrders();
    submitStopLimitOrder();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 2, 2, 15_000, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }

  // Restate an order book with a security definition update that increase the price scale.
  // Order book should be rebuilt and remain consistent with scaled values update on orders.
  @Test
  public void rebuildForPriceScaleIncrease() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();

    Order stopLimitOrder = submitStopLimitOrder();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 3, 2, 0, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
    Assert.assertEquals("", ((ArrayOrderBook) InstrumentCache.getPair(BTC_USDT_F).getOrderBook()).toString(1_01));
    Assert.assertEquals(
        "-> Order [securityId=12, orderId=1, origOrderId=0, price=101, price_scale=2, price2=0, price2_scale=0, qty=1000, qty_scale=2, side=BUY, "
            + "orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=1010, clOrdId=ClOrdId, senderCompIdCharArr=null, senderCompIdAsString=null, "
            + "account=100, submitterId=0, ordType=LIMIT, type=0, priceInt=1010, quantityLong=1000, quantityOrigLong=1000, quantityOrig_scale=0, timeInForce=DAY, expireTime=0, "
            + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false, inTime=0, decodedTime=0, sourceSeqNum=0, sourceSendTime=0, targetStrategy=0, "
            + "isHidden=false, isLiquidation=false, isLastLook=false, feeEstimatedQuantity=0, feeAccumulatedQuantity=0, availableEstimatedQuantity=0, availableAccumulatedQuantity=0]",
        ((ArrayOrderBook) InstrumentCache.getPair(BTC_USDT_F).getOrderBook()).toString(1_010));

    Assert.assertEquals(1_200, stopLimitOrder.getPriceInt());
    Assert.assertEquals(1_200, stopLimitOrder.getPrice2Int());
    Assert.assertEquals(1_210, stopLimitOrder.getStopPxInt());
  }

  // Restate an order book with a security definition update that decrease the price scale.
  // Order book should be rebuilt and remain consistent with scaled values update on orders.
  @Test
  public void rebuildForPriceScaleDecrease() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();

    Order stopLimitOrder = submitStopLimitOrder();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 1, 2, 0, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
    Assert.assertEquals("", ((ArrayOrderBook) InstrumentCache.getPair(BTC_USDT_F).getOrderBook()).toString(1_01));
    Assert.assertEquals(
        "-> Order [securityId=12, orderId=1, origOrderId=0, price=101, price_scale=2, price2=0, price2_scale=0, qty=1000, qty_scale=2, side=BUY, "
            + "orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=10, clOrdId=ClOrdId, senderCompIdCharArr=null, senderCompIdAsString=null, "
            + "account=100, submitterId=0, ordType=LIMIT, type=0, priceInt=10, quantityLong=1000, quantityOrigLong=1000, quantityOrig_scale=0, timeInForce=DAY, expireTime=0, "
            + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false, inTime=0, decodedTime=0, sourceSeqNum=0, sourceSendTime=0, targetStrategy=0, "
            + "isHidden=false, isLiquidation=false, isLastLook=false, feeEstimatedQuantity=0, feeAccumulatedQuantity=0, availableEstimatedQuantity=0, availableAccumulatedQuantity=0]"
            + "-> Order [securityId=12, orderId=2, origOrderId=0, price=102, price_scale=2, price2=0, price2_scale=0, qty=1100, qty_scale=2, side=BUY, "
            + "orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=10, clOrdId=ClOrdId, senderCompIdCharArr=null, senderCompIdAsString=null, "
            + "account=100, submitterId=0, ordType=LIMIT, type=0, priceInt=10, quantityLong=1100, quantityOrigLong=1100, quantityOrig_scale=0, timeInForce=DAY, expireTime=0, "
            + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false, inTime=0, decodedTime=0, sourceSeqNum=0, sourceSendTime=0, targetStrategy=0, "
            + "isHidden=false, isLiquidation=false, isLastLook=false, feeEstimatedQuantity=0, feeAccumulatedQuantity=0, availableEstimatedQuantity=0, availableAccumulatedQuantity=0]"
            + "-> Order [securityId=12, orderId=3, origOrderId=0, price=103, price_scale=2, price2=0, price2_scale=0, qty=1200, qty_scale=2, side=BUY, "
            + "orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=10, clOrdId=ClOrdId, senderCompIdCharArr=null, senderCompIdAsString=null, "
            + "account=100, submitterId=0, ordType=LIMIT, type=0, priceInt=10, quantityLong=1200, quantityOrigLong=1200, quantityOrig_scale=0, timeInForce=DAY, expireTime=0, "
            + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false, inTime=0, decodedTime=0, sourceSeqNum=0, sourceSendTime=0, targetStrategy=0, "
            + "isHidden=false, isLiquidation=false, isLastLook=false, feeEstimatedQuantity=0, feeAccumulatedQuantity=0, availableEstimatedQuantity=0, availableAccumulatedQuantity=0]"
            + "-> Order [securityId=12, orderId=4, origOrderId=0, price=104, price_scale=2, price2=0, price2_scale=0, qty=1300, qty_scale=2, side=BUY, "
            + "orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=10, clOrdId=ClOrdId, senderCompIdCharArr=null, senderCompIdAsString=null, "
            + "account=100, submitterId=0, ordType=LIMIT, type=0, priceInt=10, quantityLong=1300, quantityOrigLong=1300, quantityOrig_scale=0, timeInForce=DAY, expireTime=0, "
            + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false, inTime=0, decodedTime=0, sourceSeqNum=0, sourceSendTime=0, targetStrategy=0, "
            + "isHidden=false, isLiquidation=false, isLastLook=false, feeEstimatedQuantity=0, feeAccumulatedQuantity=0, availableEstimatedQuantity=0, availableAccumulatedQuantity=0]",
        ((ArrayOrderBook) InstrumentCache.getPair(BTC_USDT_F).getOrderBook()).toString(1_0));

    Assert.assertEquals(1_2, stopLimitOrder.getPriceInt());
    Assert.assertEquals(1_2, stopLimitOrder.getPrice2Int());
    Assert.assertEquals(1_2, stopLimitOrder.getStopPxInt());
  }

  // Restate an order book with a security definition update that increase the quantity scale.
  // Order book should be rebuilt and remain consistent with scaled values update on orders.
  @Test
  @Ignore
  public void rebuildForQuantityScaleIncrease() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();

    Order stopLimitOrder = submitStopLimitOrder();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 2, 3, 0, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
    Assert.assertEquals(
        "-> Order [securityId=12, orderId=1, origOrderId=0, price=101, price_scale=2, price2=0, price2_scale=0, qty=1000, qty_scale=2, side=BUY, "
            + "orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=101, clOrdId=ClOrdId, senderCompIdCharArr=null, senderCompIdAsString=null, "
            + "account=100, submitterId=0, ordType=LIMIT, type=0, priceInt=101, quantityLong=10000, quantityOrigLong=10000, quantityOrig_scale=0, timeInForce=DAY, expireTime=0, "
            + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false, inTime=0, decodedTime=0, sourceSeqNum=0, sourceSendTime=0, targetStrategy=0, "
            + "isHidden=false, isLiquidation=false, isLastLook=false, feeEstimatedQuantity=0, feeAccumulatedQuantity=0, availableEstimatedQuantity=0, availableAccumulatedQuantity=0]",
        ((ArrayOrderBook) InstrumentCache.getPair(BTC_USDT_F).getOrderBook()).toString(1_01));

    Assert.assertEquals(1_20, stopLimitOrder.getPriceInt());
    Assert.assertEquals(1_20, stopLimitOrder.getPrice2Int());
    Assert.assertEquals(1_21, stopLimitOrder.getStopPxInt());
  }

  // Restate an order book with a security definition update that decrease the quantity scale.
  // Order book should be rebuilt and remain consistent with scaled values update on orders.
  @Test
  public void rebuildForQuantityScaleDecrease() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();

    Order stopLimitOrder = submitStopLimitOrder();
    assertMessages();

    updateSecurityDefinition("BTC/USDT[F]", 2, 1, 0, 0);
    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
    Assert.assertEquals(
        "-> Order [securityId=12, orderId=1, origOrderId=0, price=101, price_scale=2, price2=0, price2_scale=0, qty=1000, qty_scale=2, side=BUY, "
            + "orderPriority=0, secondaryOrderId=0, marginCheckReferencePrice=101, clOrdId=ClOrdId, senderCompIdCharArr=null, senderCompIdAsString=null, "
            + "account=100, submitterId=0, ordType=LIMIT, type=0, priceInt=101, quantityLong=100, quantityOrigLong=100, quantityOrig_scale=0, timeInForce=DAY, expireTime=0, "
            + "stopPx=0, stopPx_scale=0, stopPxInt=0, toClose=false, inTime=0, decodedTime=0, sourceSeqNum=0, sourceSendTime=0, targetStrategy=0, "
            + "isHidden=false, isLiquidation=false, isLastLook=false, feeEstimatedQuantity=0, feeAccumulatedQuantity=0, availableEstimatedQuantity=0, availableAccumulatedQuantity=0]",
        ((ArrayOrderBook) InstrumentCache.getPair(BTC_USDT_F).getOrderBook()).toString(1_01));

    Assert.assertEquals(1_20, stopLimitOrder.getPriceInt());
    Assert.assertEquals(1_20, stopLimitOrder.getPrice2Int());
    Assert.assertEquals(1_21, stopLimitOrder.getStopPxInt());
  }

  // Restate an order book with a security definition update that changes order book implementation.
  // Order book should be rebuilt and remain consistent.
  @Test
  public void rebuildForImplementationChange() {
    OrderBook orderBook = InstrumentCache.getPair(BTC_USDT_F).getOrderBook();
    submitOrders();
    //submitOutOfBoundOrders();
    assertMessages();

    SecurityDefinitionAdminMessage message = createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 2);
    message.setOrderBookStrategy(LINKED_LIST_ORDER_BOOK);

    InstrumentCache.updateSecurityDefinition(message);
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");
    assertMessages();

    restate();
    assertMessages();

    Assert.assertTrue(orderBook != InstrumentCache.getPair(BTC_USDT_F).getOrderBook());
  }
}
