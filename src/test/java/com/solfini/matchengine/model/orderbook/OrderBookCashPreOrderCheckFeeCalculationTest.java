package com.solfini.matchengine.model.orderbook;

import com.solfini.common.Constants;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.pool.OrderObjectPool;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import static com.solfini.sbe.encoder.TimeInForce.*;
import java.util.Properties;

public class OrderBookCashPreOrderCheckFeeCalculationTest extends OrderBookTest {

  @Override
  protected void onConfigure(final Properties properties) {
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "1300000");
  }

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDC, UpdateType.PUT, "USDC", 6, 6));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 6, 6));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDC, UpdateType.PUT, "BTC/USDC", BTC, USDC, 2, 6, CASH_PREORDER_CHECK));

    expectMessage("securityId=" + USDC + ", symbol=USDC");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDC + ", symbol=BTC/USDC, updateType=PUT");

    pair = InstrumentCache.getPair(BTC_USDC);

    orderBook = new OrderBookTest.OrderBookWrapper(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.CASH_PREORDER_CHECK, pair);
    orderBook.orderBook().setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));

    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(8000);
    pair.setFee(new Fee(pair.getId(), USDC, 1500, FeeType.PERCENT, MakerTaker.ALL, 0, true));
  }

  @Override
  protected void createUsers() {
    user = createUser(18, new Balance(USDC, 0, 0, 0, 0, null,0, TokenType.ERC20), new Balance(BTC, 0, 0, 0, 0, null,0, TokenType.ERC20));
    expectMessage("userId=18");

    user2 = createUser(19, new Balance(USDC, 0, 0, 0, 0, null,0, TokenType.ERC20), new Balance(BTC, 0, 0, 0, 0, null,0, TokenType.ERC20));
    expectMessage("userId=19");

    user3 = createUser(20, new Balance(USDC, 0, 0, 0, 0, null,0, TokenType.ERC20), new Balance(BTC, 0, 0, 0, 0, null,0, TokenType.ERC20));
    expectMessage("userId=20");

    user.getPosition(BTC_USDC).getUserOpenOrdersByPair().set(user, InstrumentCache.getPair(BTC_USDC));
    user2.getPosition(BTC_USDC).getUserOpenOrdersByPair().set(user2, InstrumentCache.getPair(BTC_USDC));
    user2.getPosition(BTC_USDC).getUserOpenOrdersByPair().set(user3, InstrumentCache.getPair(BTC_USDC));
  }

  private void setPosition(final User user, final int instrumentId, final long quantity, final long availableQuantity) {
    user.getPosition(instrumentId).setQuantity(quantity);
    user.getPosition(instrumentId).setAvailableQuantity(availableQuantity);
  }

  private void assertPosition(final User user, final int instrumentId, final long quantity, final long availableQuantity) {
    Assert.assertEquals("Expected quantity: " + quantity + ", Received quantity: " + user.getPosition(instrumentId).getQuantity(), quantity,
        user.getPosition(instrumentId).getQuantity());
    Assert.assertEquals(
        "Expected available quantity: " + availableQuantity + ", Received available quantity: "
            + user.getPosition(instrumentId).getAvailableQuantity(),
        availableQuantity, user.getPosition(instrumentId).getAvailableQuantity());
  }

  private Order createOrder(final int orderId, final User user, final OrdType orderType, final int securityId, final long price,
      final long quantity, final Side side, final TimeInForce timeInForce) {
    Order order = OrderObjectPool.get();
    order.setOrderId(orderId);
    order.setUser(user);
    order.setAccount(user == null ? 0 : user.getId());
    order.setClOrdId("ClOrdId");
    order.setOrdType(orderType);
    order.setSecurityId(securityId);
    order.setSide(side);
    order.setPrice(price, pair.getPriceScale());
    order.setPriceInt((int) price);
    order.setQty(quantity, pair.getQuantityScale());
    order.setQuantityLong(quantity);
    order.setQuantityOrigLong(quantity);
    order.setTimeInForce(timeInForce);
    if (side == Side.SELL)
      order.setType(Constants.SELL_LIMIT);
    else if (side == Side.BUY)
      order.setType(Constants.BUY_LIMIT);

    return order;
  }

  @Test
  public void ET_2071_A() {
    setPosition(user, USDC, 21_661_547944L, 12_766_895944L);
    assertPosition(user, USDC, 21_661_547944L, 12_766_895944L);

    setPosition(user2, BTC, 10_000000L, 10_000000L);
    setPosition(user2, USDC, 10_000_000000L, 10_000_000000L);

    orderBook.addOrder(createOrder(1, user2, OrdType.LIMIT, BTC_USDC, 12000_00, 100000, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user2, OrdType.LIMIT, BTC_USDC, 12012_00, 123000, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, OrdType.LIMIT, BTC_USDC, 12012_00, 300000, Side.BUY, DAY));

    // available 12012.00 * 0.300000 = 3603.600000
    // fee 12012.00 * 0.300000 * 0.15% = 5.405400
    expectMessage(
        "orderId=3, ordStatus=NEW, feeEstimatedQuantity=5405400, feeAccumulatedQuantity=0, availableEstimatedQuantity=3603600000, availableAccumulatedQuantity=0");

    // available 12000.00 * 0.100000 = 1200.000000
    // adjustment (12012.00 - 12000.00) * 0.100000 = 1.200000
    // fee 12000.00 * 0.100000 * 0.15% = 1.800000
    expectMessage(
        "orderId=3, ordStatus=PARTIALLY_FILLED, feeEstimatedQuantity=5405400, feeAccumulatedQuantity=1800000, availableEstimatedQuantity=3603600000, availableAccumulatedQuantity=1201200000");
    expectMessage("orderId=1, ordStatus=FILLED");

    // available 12012.00 * 0.123000 = 1477.470000
    // adjustment (12012.00 - 12012.00) * 0.123000 = 0.000000
    // fee 12012.00 * 0.123000 * 0.15% = 2.216214
    expectMessage(
        "orderId=3, ordStatus=PARTIALLY_FILLED, feeEstimatedQuantity=5405400, feeAccumulatedQuantity=4016214, availableEstimatedQuantity=3603600000, availableAccumulatedQuantity=2678670000");
    expectMessage("orderId=2, ordStatus=FILLED");

    assertMessages();

    assertPosition(user, USDC, 18_980_061730L, 9_159_090544L);
  }


  public void ET_2071_B() {
    setPosition(user, USDC, 21_661_547944L, 12_766_895944L);
    assertPosition(user, USDC, 21_661_547944L, 12_766_895944L);

    setPosition(user2, BTC, 10_000000L, 10_000000L);
    setPosition(user2, USDC, 10_000_000000L, 10_000_000000L);

    orderBook.addOrder(createOrder(1, user2, OrdType.LIMIT, BTC_USDC, 12012_00, 100000, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user2, OrdType.LIMIT, BTC_USDC, 12012_00, 123000, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");

    orderBook.addOrder(createOrder(3, user, OrdType.LIMIT, BTC_USDC, 12012_00, 300000, Side.BUY, DAY));

    // available 12012.00 * 0.300000 = 3603.600000
    // fee 12012.00 * 0.300000 * 0.15% = 5.405400
    expectMessage(
        "orderId=3, ordStatus=NEW, feeEstimatedQuantity=5405400, feeAccumulatedQuantity=0, availableEstimatedQuantity=3603600000, availableAccumulatedQuantity=0");

    // available 12000.00 * 0.100000 = 1200.000000
    // adjustment (12012.00 - 12000.00) * 0.100000 = 1.200000
    // fee 12000.00 * 0.100000 * 0.15% = 1.800000
    expectMessage(
        "orderId=3, ordStatus=PARTIALLY_FILLED, feeEstimatedQuantity=5405400, feeAccumulatedQuantity=1800000, availableEstimatedQuantity=3603600000, availableAccumulatedQuantity=1201200000");
    expectMessage("orderId=1, ordStatus=FILLED");

    // available 12012.00 * 0.123000 = 1477.470000
    // adjustment (12012.00 - 12012.00) * 0.123000 = 0.000000
    // fee 12012.00 * 0.123000 * 0.15% = 2.216214
    expectMessage(
        "orderId=3, ordStatus=PARTIALLY_FILLED, feeEstimatedQuantity=5405400, feeAccumulatedQuantity=4016214, availableEstimatedQuantity=3603600000, availableAccumulatedQuantity=2678670000");
    expectMessage("orderId=2, ordStatus=FILLED");

    assertMessages();

    assertPosition(user, USDC, 18_980_061730L, 9_159_090544L);
  }
}
