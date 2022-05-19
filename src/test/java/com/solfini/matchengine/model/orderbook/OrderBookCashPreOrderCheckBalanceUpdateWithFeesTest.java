package com.solfini.matchengine.model.orderbook;

import com.solfini.instrument.Balance;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import static com.solfini.sbe.encoder.TimeInForce.*;

public class OrderBookCashPreOrderCheckBalanceUpdateWithFeesTest extends OrderBookTest {

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 4, 1));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 3, 2));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT, UpdateType.PUT, "BTC/USDT", BTC, USDT, 2, 3, CASH_PREORDER_CHECK));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT + ", symbol=BTC/USDT, updateType=PUT");

    pair = InstrumentCache.getPair(BTC_USDT);

    orderBook = new OrderBookTest.OrderBookWrapper(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.CASH_PREORDER_CHECK, pair);
    orderBook.orderBook().setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));

    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(10);
    pair.setFee(new Fee(pair.getId(), USDT, 1000, FeeType.PERCENT, MakerTaker.ALL, 0, true));
  }

  @Override
  protected void createUsers() {
    user = createUser(18, new Balance(USDT, 0, 0, 0, 0), new Balance(BTC, 1000, 0, 0, 0));
    expectMessage("userId=18");

    user2 = createUser(19, new Balance(USDT, 50000, 0, 0, 0), new Balance(BTC, 1000, 0, 0, 0));
    expectMessage("userId=19");

    user3 = createUser(20, new Balance(USDT, 50000, 0, 0, 0), new Balance(BTC, 1000, 0, 0, 0));
    expectMessage("userId=20");

    user.getPosition(BTC_USDT).getUserOpenOrdersByPair().set(user, InstrumentCache.getPair(BTC_USDT));
    user2.getPosition(BTC_USDT).getUserOpenOrdersByPair().set(user2, InstrumentCache.getPair(BTC_USDT));
    user2.getPosition(BTC_USDT).getUserOpenOrdersByPair().set(user3, InstrumentCache.getPair(BTC_USDT));
  }

  private void assertPosition(final User user, final int instrumentId, final long quantity, final long availableQuantity) {
    Assert.assertEquals("Expected quantity: " + quantity + ", Received quantity: " + user.getPosition(instrumentId).getQuantity(), quantity,
        user.getPosition(instrumentId).getQuantity());
    Assert.assertEquals(
        "Expected available quantity: " + availableQuantity + ", Received available quantity: "
            + user.getPosition(instrumentId).getAvailableQuantity(),
        availableQuantity, user.getPosition(instrumentId).getAvailableQuantity());
  }

  // Sell order with positive base balance and zero fee balance - should not be rejected
  @Test
  public void sellLimitOrder_ZeroFeeBalance() {
    assertPosition(user, USDT, 0_0, 0_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    assertMessages();

    assertPosition(user, USDT, 0_0, 0_0);
    assertPosition(user, BTC, 1000_00, 950_00);
  }

  // Sell order with positive base balance and zero fee balance - cancel
  @Test
  public void sellLimitOrder_ZeroFeeBalance_Cancel() {
    assertPosition(user, USDT, 0_0, 0_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");

    orderBook.cancelOrder(createCancelOrder(2, 1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordStatus=CANCELED");

    assertMessages();

    assertPosition(user, USDT, 0_0, 0_0);
    assertPosition(user, BTC, 1000_00, 1000_00);
  }

  // Sell order with positive base balance and zero fee balance - fee should be recovered from sale proceeds
  @Test
  public void sellLimitOrder_ZeroFeeBalance_FeeFromSaleProceeds1() {
    assertPosition(user, USDT, 0_0, 0_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");

    assertMessages();

    final long fee = 0_5;
    assertPosition(user, USDT, 500_0 - fee, 500_0 - fee);
    assertPosition(user, BTC, 950_00, 950_00);

    assertPosition(user2, USDT, 49500_0 - fee, 49500_0 - fee);
    assertPosition(user2, BTC, 1050_00, 1050_00);
  }

  // Sell order with positive base balance and zero fee balance - fee should be recovered from sale proceeds
  @Test
  public void sellLimitOrder_ZeroFeeBalance_FeeFromSaleProceeds2() {
    assertPosition(user, USDT, 0_0, 0_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 30_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(3, user2, BTC_USDT, 10_00, 20_00, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");

    assertMessages();

    final long fee = 0_5;
    assertPosition(user, USDT, 500_0 - fee, 500_0 - fee);
    assertPosition(user, BTC, 950_00, 950_00);

    assertPosition(user2, USDT, 49500_0 - fee, 49500_0 - fee);
    assertPosition(user2, BTC, 1050_00, 1050_00);
  }

  // Sell order with positive base balance and zero fee balance - fee should be recovered from sale proceeds
  @Test
  public void sellLimitOrder_ZeroFeeBalance_FeeFromSaleProceeds3() {
    assertPosition(user, USDT, 0_0, 0_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 30_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(3, user2, BTC_USDT, 10_00, 30_00, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");

    assertMessages();

    final long fee = 0_5;
    assertPosition(user, USDT, 500_0 - fee, 500_0 - fee);
    assertPosition(user, BTC, 950_00, 950_00);
  }

  // Sell order with positive base balance and zero fee balance - fee should be recovered from sale proceeds
  @Test
  public void sellLimitOrder_ZeroFeeBalance_FeeFromSaleProceeds4() {
    assertPosition(user, USDT, 0_0, 0_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 20_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(3, user2, BTC_USDT, 10_00, 20_00, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");

    assertMessages();

    final long fee = 0_4;
    assertPosition(user, USDT, 400_0 - fee, 400_0 - fee);
    assertPosition(user, BTC, 960_00, 950_00);
  }

  // Sell order with positive base balance and zero fee balance - fee should be recovered from sale proceeds
  @Test
  public void sellLimitOrder_ZeroFeeBalance_FeeFromSaleProceeds_Cancel() {
    assertPosition(user, USDT, 0_0, 0_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");

    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 20_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(3, user2, BTC_USDT, 10_00, 20_00, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");

    orderBook.cancelOrder(createCancelOrder(4, 1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordStatus=CANCELED");

    assertMessages();

    final long fee = 0_4;
    assertPosition(user, USDT, 400_0 - fee, 400_0 - fee);
    assertPosition(user, BTC, 960_00, 960_00);
  }

  @Test
  public void buyLimitOrderFill_Maker() {
    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDT, 50000_0, 50000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user3, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user2, USDT, 49500_0 - 5, 49500_0 - 5);
    assertPosition(user2, BTC, 1050_00, 1050_00);

    assertPosition(user3, USDT, 50500_0 - 5, 50500_0 - 5);
    assertPosition(user3, BTC, 950_00, 950_00);
  }

  @Test
  public void buyLimitOrderFill_Taker() {
    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDT, 50000_0, 50000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(2, user3, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=2, ordStatus=FILLED");
    assertMessages();

    assertPosition(user2, USDT, 49500_0 - 5, 49500_0 - 5);
    assertPosition(user2, BTC, 1050_00, 1050_00);

    assertPosition(user3, USDT, 50500_0 - 5, 50500_0 - 5);
    assertPosition(user3, BTC, 950_00, 950_00);
  }


  @Test
  public void buyLimitOrderFill_Higher_Taker() {
    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDT, 50000_0, 50000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(2, user3, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 100_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=2, ordStatus=FILLED");
    assertMessages();

    assertPosition(user2, USDT, 49500_0 - 5, 49500_0 - 5);
    assertPosition(user2, BTC, 1050_00, 1050_00);

    assertPosition(user3, USDT, 50500_0 - 5, 50500_0 - 5);
    assertPosition(user3, BTC, 950_00, 950_00);
  }

  @Test
  public void buyLimitOrderFill_MultipleContraOrders_Maker() {
    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDT, 50000_0, 50000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user3, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user3, BTC_USDT, 10_00, 20_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user2, USDT, 49500_0 - 5, 49500_0 - 5);
    assertPosition(user2, BTC, 1050_00, 1050_00);

    assertPosition(user3, USDT, 50500_0 - 5, 50500_0 - 5);
    assertPosition(user3, BTC, 950_00, 950_00);
  }

  @Test
  public void buyLimitOrderFill_MultipleContraOrders_Taker() {
    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDT, 50000_0, 50000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(2, user3, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user3, BTC_USDT, 10_00, 20_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=3, ordStatus=FILLED");
    assertMessages();

    assertPosition(user2, USDT, 49500_0 - 5, 49500_0 - 5);
    assertPosition(user2, BTC, 1050_00, 1050_00);

    assertPosition(user3, USDT, 50500_0 - 5, 50500_0 - 5);
    assertPosition(user3, BTC, 950_00, 950_00);
  }

  @Test
  public void buyLimitOrderFill_MultiplePricedContraOrders_Taker() {
    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDT, 50000_0, 50000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(2, user3, BTC_USDT, 60_00, 30_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user3, BTC_USDT, 100_00, 20_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 100_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=3, ordStatus=FILLED");
    assertMessages();

    {
      final int usdtbalance = 50000_0 - (60_0 * 30) - (100_0 * 20) - 38;
      assertPosition(user2, USDT, usdtbalance, usdtbalance);
      final int btcbalance = 1000_00 + 30_00 + 20_00;
      assertPosition(user2, BTC, btcbalance, btcbalance);
    }
    {
      final int usdtbalance = 50000_0 + (60_0 * 30) + (100_0 * 20) - 38;
      assertPosition(user3, USDT, usdtbalance, usdtbalance);
      final int btcbalance = 1000_00 - 30_00 - 20_00;
      assertPosition(user3, BTC, btcbalance, btcbalance);
    }
  }

  @Test
  public void buyLimitOrderFill_MultiplePricedContraOrders_Taker2() {
    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDT, 50000_0, 50000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    pair.setIndexFeedUsdMark(100);

    orderBook.addOrder(createOrder(2, user3, BTC_USDT, 60_00, 30_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user3, BTC_USDT, 100_00, 20_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 100_00, 50_00, Side.BUY, DAY));

    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=3, ordStatus=FILLED");
    assertMessages();

    {
        final int usdtbalance = 50000_0 - (60_0 * 30) - (100_0 * 20);
        assertPosition(user2, USDT, usdtbalance - 38, usdtbalance - 38);
        final int btcbalance = 1000_00 + 30_00 + 20_00;
        assertPosition(user2, BTC, btcbalance, btcbalance);
    }
    {
        final int usdtbalance = 50000_0 + (60_0 * 30) + (100_0 * 20);
        assertPosition(user3, USDT, usdtbalance - 38, usdtbalance - 38);
        final int btcbalance = 1000_00 - 30_00 - 20_00;
        assertPosition(user3, BTC, btcbalance, btcbalance);
    }
  }

  @Test
  public void buyLimitOrderFill_MultipleContraOrders_Maker_Available() {
    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDT, 50000_0, 50000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 35_00, 50_00, Side.BUY, DAY));
    assertPosition(user2, USDT, 50000_0, 48250_0 - 17);

    orderBook.addOrder(createOrder(2, user3, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user3, BTC_USDT, 10_00, 20_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user2, USDT, 48250_0 - 17, 48250_0 - 17);
    assertPosition(user2, BTC, 1050_00, 1050_00);

    assertPosition(user3, USDT, 51750_0 - 17, 51750_0 - 17);
    assertPosition(user3, BTC, 950_00, 950_00);
  }


  @Test
  public void buyLimitOrderFill_MultipleContraOrders_Taker_Available() {
    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDT, 50000_0, 50000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(2, user3, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user3, BTC_USDT, 10_00, 20_00, Side.SELL, DAY));

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 35_00, 50_00, Side.BUY, DAY));
    assertPosition(user2, USDT, 49500_0 - 5, 49500_0 - 5);

    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=3, ordStatus=FILLED");
    assertMessages();

    assertPosition(user2, USDT, 49500_0 - 5, 49500_0 - 5);
    assertPosition(user2, BTC, 1050_00, 1050_00);

    assertPosition(user3, USDT, 50500_0 - 5, 50500_0 - 5);
    assertPosition(user3, BTC, 950_00, 950_00);
  }

  @Test
  public void buyMarketOrder_Fill() {
    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDT, 50000_0, 50000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user3, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createMarketOrder(2, user2, BTC_USDT, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user2, USDT, 49500_0 - 5, 49500_0 - 5);
    assertPosition(user2, BTC, 1050_00, 1050_00);

    assertPosition(user3, USDT, 50500_0 - 5, 50500_0 - 5);
    assertPosition(user3, BTC, 950_00, 950_00);
  }

  @Test
  public void buyMarketOrder_PartialFill() {
    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDT, 50000_0, 50000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user3, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createMarketOrder(2, user2, BTC_USDT, 60_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=2, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, ordStatus=CANCELED");
    assertMessages();

    assertPosition(user2, USDT, 49500_0 - 5, 49500_0 - 5);
    assertPosition(user2, BTC, 1050_00, 1050_00);

    assertPosition(user3, USDT, 50500_0 - 5, 50500_0 - 5);
    assertPosition(user3, BTC, 950_00, 950_00);
  }

}
