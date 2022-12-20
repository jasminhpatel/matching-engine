package com.solfini.matchengine.model.orderbook;

import com.solfini.instrument.Balance;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import static com.solfini.sbe.encoder.TimeInForce.*;

public class OrderBookCashPreOrderCheckTest4 extends OrderBookTest {

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 4, 1));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 3, 2));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT, UpdateType.PUT, "BTC/USDT", BTC, USDT, 2, 3, CASH_PREORDER_CHECK));

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
    user = createUser(18, new Balance(USDT, 50000, 0, 0, 0, null,0, TokenType.ERC20), new Balance(BTC, 1000, 0, 0, 0, null,0, TokenType.ERC20));
    expectMessage("userId=18");

    user2 = createUser(19, new Balance(USDT, 50000, 0, 0, 0, null,0, TokenType.ERC20), new Balance(BTC, 1000, 0, 0, 0, null,0, TokenType.ERC20));
    expectMessage("userId=19");
  }

  private void assertPosition(final User user, final int instrumentId, final long quantity, final long availableQuantity) {
    Assert.assertEquals(quantity, user.getPosition(instrumentId).getQuantity());
    Assert.assertEquals(availableQuantity, user.getPosition(instrumentId).getAvailableQuantity());
  }

  @Test
  public void availablePositionReducesForOpenBuyLimitOrder() {
    assertPosition(user, USDT, 50000_0, 50000_0); // 50,000.0 usdt
    assertPosition(user, BTC, 1000_00, 1000_00); // 1,000.00 btc

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY)); // buy 50.0 BTC at 10.00
    expectMessage("orderId=1, ordStatus=NEW");
    assertMessages();

    assertPosition(user, USDT, 50000_0, 49500_0); // reduced by 500.0 usdt
    assertPosition(user, BTC, 1000_00, 1000_00);
  }

  @Test
  public void availablePositionRevertsForOpenBuyLimitOrderAfterCancel() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.cancelOrder(createCancelOrder(2, 1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordStatus=CANCELED");
    assertMessages();

    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);
  }

  @Test
  public void availablePositionRevertsForOpenSellLimitOrderAfterCancel() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.cancelOrder(createCancelOrder(2, 1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordStatus=CANCELED");
    assertMessages();

    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);
  }

  @Test
  public void positionsForBuyLimitOrderAfterTrade() {
    assertPosition(user, USDT, 50000_0, 50000_0); // 50,000.0 usdt
    assertPosition(user, BTC, 1000_00, 1000_00); // 1,000.00 btc

    assertPosition(user2, USDT, 50000_0, 50000_0); // 50,000.0 usdt
    assertPosition(user2, BTC, 1000_00, 1000_00); // 1,000.00 btc

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 5_00, Side.BUY, DAY)); // buy 5.0 BTC at 10.00
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 5_00, Side.SELL, DAY)); // sell 5.0 BTC at 10.00
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 49950_0, 49950_0); // reduced by 50.0 usdt
    assertPosition(user, BTC, 1005_00, 1005_00); // increased by 5.00 BTC

    assertPosition(user2, USDT, 50050_0, 50050_0); // increased by 50.0 usdt
    assertPosition(user2, BTC, 995_00, 995_00); // reduced by 5.00 BTC
  }

  @Test
  public void positionsForBuyLimitOrderAfterTrade2() {
    assertPosition(user, USDT, 50000_0, 50000_0); // 50,000.0 usdt
    assertPosition(user, BTC, 1000_00, 1000_00); // 1,000.00 btc

    assertPosition(user2, USDT, 50000_0, 50000_0); // 50,000.0 usdt
    assertPosition(user2, BTC, 1000_00, 1000_00); // 1,000.00 btc

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 20_00, 6_00, Side.BUY, DAY)); // buy 6.0 BTC at 20.00
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 20_00, 6_00, Side.SELL, DAY)); // sell 6.0 BTC at 20.00
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 49880_0, 49880_0); // reduced by 60.0 usdt
    assertPosition(user, BTC, 1006_00, 1006_00); // increased by 6.00 BTC

    assertPosition(user2, USDT, 50120_0, 50120_0); // increased by 60.0 usdt
    assertPosition(user2, BTC, 994_00, 994_00); // reduced by 6.00 BTC
  }
}
