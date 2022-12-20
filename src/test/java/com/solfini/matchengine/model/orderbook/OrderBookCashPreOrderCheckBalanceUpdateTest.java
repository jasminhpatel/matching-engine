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

public class OrderBookCashPreOrderCheckBalanceUpdateTest extends OrderBookTest {

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
    pair.setIndexFeedUsdMark(10);
    pair.setFee(new Fee(pair.getId(), USDT, 0, FeeType.PERCENT, MakerTaker.ALL, 0, true));
  }

  @Override
  protected void createUsers() {
    user = createUser(18, new Balance(USDT, 50000, 0, 0, 0, null,0, TokenType.ERC20), new Balance(BTC, 1000, 0, 0, 0, null,0, TokenType.ERC20));
    expectMessage("userId=18");

    user2 = createUser(19, new Balance(USDT, 50000, 0, 0, 0, null,0, TokenType.ERC20), new Balance(BTC, 1000, 0, 0, 0, null,0, TokenType.ERC20));
    expectMessage("userId=19");

    user.getPosition(BTC_USDT).getUserOpenOrdersByPair().set(user, InstrumentCache.getPair(BTC_USDT));
    user2.getPosition(BTC_USDT).getUserOpenOrdersByPair().set(user, InstrumentCache.getPair(BTC_USDT));
  }

  private void assertPosition(final User user, final int instrumentId, final long quantity, final long availableQuantity) {
    Assert.assertEquals(quantity, user.getPosition(instrumentId).getQuantity());
    Assert.assertEquals(availableQuantity, user.getPosition(instrumentId).getAvailableQuantity());
  }

  // Limit Orders - BUY

  @Test
  public void buyLimitOrder_Open() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    assertMessages();

    assertPosition(user, USDT, 50000_0, 49500_0);
    assertPosition(user, BTC, 1000_00, 1000_00);
  }

  @Test
  public void buyLimitOrder_Cancel() {
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
  public void buyLimitOrder_Fill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 49500_0, 49500_0);
    assertPosition(user, BTC, 1050_00, 1050_00);

    assertPosition(user2, USDT, 50500_0, 50500_0);
    assertPosition(user2, BTC, 950_00, 950_00);
  }

  @Test
  public void buyLimitOrder_Fill_MultiplePricePoints_Maker() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user2, BTC_USDT, 5_00, 20_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 49500_0, 49500_0);
    assertPosition(user, BTC, 1050_00, 1050_00);

    assertPosition(user2, USDT, 50500_0, 50500_0);
    assertPosition(user2, BTC, 950_00, 950_00);
  }

  @Test
  public void buyLimitOrder_Fill_MultiplePricePoints_Taker() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user2, BTC_USDT, 5_00, 20_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=2, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 49600_0, 49600_0);
    assertPosition(user, BTC, 1050_00, 1050_00);

    assertPosition(user2, USDT, 50400_0, 50400_0);
    assertPosition(user2, BTC, 950_00, 950_00);
  }

  @Test
  public void buyLimitOrder_PartialFill1() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 60_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 49500_0, 49500_0);
    assertPosition(user, BTC, 1050_00, 1050_00);

    assertPosition(user2, USDT, 50500_0, 50500_0);
    assertPosition(user2, BTC, 950_00, 940_00);
  }

  @Test
  public void buyLimitOrder_PartialFill2() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user2, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 49500_0, 49500_0);
    assertPosition(user, BTC, 1050_00, 1050_00);

    assertPosition(user2, USDT, 50500_0, 50500_0);
    assertPosition(user2, BTC, 950_00, 940_00);
  }

  @Test
  public void buyLimitOrder_PartialFill3() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    assertMessages();

    assertPosition(user, USDT, 49700_0, 49500_0);
    assertPosition(user, BTC, 1030_00, 1030_00);

    assertPosition(user2, USDT, 50300_0, 50300_0);
    assertPosition(user2, BTC, 970_00, 970_00);
  }

  @Test
  public void buyLimitOrder_PartialFill4() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 20_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user2, BTC_USDT, 10_00, 10_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    assertMessages();

    assertPosition(user, USDT, 49700_0, 49500_0);
    assertPosition(user, BTC, 1030_00, 1030_00);

    assertPosition(user2, USDT, 50300_0, 50300_0);
    assertPosition(user2, BTC, 970_00, 970_00);
  }

  @Test
  public void buyLimitOrder_PartialFill_Cancel() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    orderBook.cancelOrder(createCancelOrder(3, 1, user, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordStatus=CANCELED");
    assertMessages();

    assertPosition(user, USDT, 49700_0, 49700_0);
    assertPosition(user, BTC, 1030_00, 1030_00);

    assertPosition(user2, USDT, 50300_0, 50300_0);
    assertPosition(user2, BTC, 970_00, 970_00);
  }

  // Limit Orders - SELL

  @Test
  public void sellLimitOrder_Open() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    assertMessages();

    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 950_00);
  }

  @Test
  public void sellLimitOrder_Cancel() {
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
  public void sellLimitOrder_Fill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 50500_0, 50500_0);
    assertPosition(user, BTC, 950_00, 950_00);

    assertPosition(user2, USDT, 49500_0, 49500_0);
    assertPosition(user2, BTC, 1050_00, 1050_00);
  }

  @Test
  public void sellLimitOrder_PartialFill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 60_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 50500_0, 50500_0);
    assertPosition(user, BTC, 950_00, 950_00);

    assertPosition(user2, USDT, 49500_0, 49400_0);
    assertPosition(user2, BTC, 1050_00, 1050_00);
  }

  @Test
  public void sellLimitOrder_Fill_PartialFill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 30_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    assertMessages();

    assertPosition(user, USDT, 50300_0, 50300_0);
    assertPosition(user, BTC, 970_00, 950_00);

    assertPosition(user2, USDT, 49700_0, 49700_0);
    assertPosition(user2, BTC, 1030_00, 1030_00);
  }

  @Test
  public void sellLimitOrder_PartialFill_Cancel() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 10_00, 30_00, Side.BUY, DAY));
    orderBook.cancelOrder(createCancelOrder(3, 1, user, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=1, ordStatus=CANCELED");
    assertMessages();

    assertPosition(user, USDT, 50300_0, 50300_0);
    assertPosition(user, BTC, 970_00, 970_00);

    assertPosition(user2, USDT, 49700_0, 49700_0);
    assertPosition(user2, BTC, 1030_00, 1030_00);
  }

  // Limit Orders - FOK

  @Test
  public void buyLimitOrder_FOK_Expire_NoLiquidity() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, FILL_OR_KILL));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);
  }

  @Test
  public void buyLimitOrder_FOK_Expire_InsufficientLiquidity() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT, 10_00, 50_00, Side.BUY, FILL_OR_KILL));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);
  }

  @Test
  public void sellLimitOrder_FOK_Expire_NoLiquidity() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, FILL_OR_KILL));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);
  }

  @Test
  public void sellLimitOrder_FOK_Expire_InsufficientLiquidity() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 30_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT, 10_00, 50_00, Side.SELL, FILL_OR_KILL));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);
  }

  // Limit Orders - IOC

  @Test
  public void buyLimitOrder_IOC_Expire() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.BUY, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);
  }

  @Test
  public void buyLimitOrder_IOC_PartialFill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 30_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT, 10_00, 50_00, Side.BUY, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=2, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user, USDT, 49700_0, 49700_0);
    assertPosition(user, BTC, 1030_00, 1030_00);

    assertPosition(user2, USDT, 50300_0, 50300_0);
    assertPosition(user2, BTC, 970_00, 970_00);
  }

  @Test
  public void sellLimitOrder_IOC_Expire() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user, BTC_USDT, 10_00, 50_00, Side.SELL, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=1, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);
  }

  @Test
  public void sellLimitOrder_IOC_PartialFill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 30_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT, 10_00, 50_00, Side.SELL, IMMEDIATE_OR_CANCEL));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=2, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user, USDT, 50300_0, 50300_0);
    assertPosition(user, BTC, 970_00, 970_00);

    assertPosition(user2, USDT, 49700_0, 49700_0);
    assertPosition(user2, BTC, 1030_00, 1030_00);
  }

  // Market Orders

  @Test
  public void buyMarketOrder_Fill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createMarketOrder(2, user, BTC_USDT, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 49500_0, 49500_0);
    assertPosition(user, BTC, 1050_00, 1050_00);

    assertPosition(user2, USDT, 50500_0, 50500_0);
    assertPosition(user2, BTC, 950_00, 950_00);
  }

  @Test
  public void buyMarketOrder_PartialFill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createMarketOrder(2, user, BTC_USDT, 60_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=2, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, ordStatus=CANCELED");
    assertMessages();

    assertPosition(user, USDT, 49500_0, 49500_0);
    assertPosition(user, BTC, 1050_00, 1050_00);

    assertPosition(user2, USDT, 50500_0, 50500_0);
    assertPosition(user2, BTC, 950_00, 950_00);
  }

  @Test
  public void buyMarketOrder_Fill_PartialFill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createMarketOrder(2, user, BTC_USDT, 30_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    assertMessages();

    assertPosition(user, USDT, 49700_0, 49700_0);
    assertPosition(user, BTC, 1030_00, 1030_00);

    assertPosition(user2, USDT, 50300_0, 50300_0);
    assertPosition(user2, BTC, 970_00, 950_00);
  }

  @Test
  public void buyMarketOrder_AggressFill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 10_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 11_00, 25_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user2, BTC_USDT, 10_00, 15_00, Side.SELL, DAY));
    orderBook.addOrder(createMarketOrder(4, user, BTC_USDT, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=4, ordStatus=NEW");
    expectMessage("orderId=4, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=4, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=4, ordStatus=FILLED");
    expectMessage("orderId=2, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 49475_0, 49475_0);
    assertPosition(user, BTC, 1050_00, 1050_00);

    assertPosition(user2, USDT, 50525_0, 50525_0);
    assertPosition(user2, BTC, 950_00, 950_00);
  }

  @Test
  public void buyMarketOrder_AggressPartialFill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 10_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user2, BTC_USDT, 11_00, 25_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user2, BTC_USDT, 10_00, 15_00, Side.SELL, DAY));
    orderBook.addOrder(createMarketOrder(4, user, BTC_USDT, 60_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=4, ordStatus=NEW");
    expectMessage("orderId=4, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=4, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=3, ordStatus=FILLED");
    expectMessage("orderId=4, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=4, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=4, ordStatus=CANCELED");
    assertMessages();

    assertPosition(user, USDT, 49475_0, 49475_0);
    assertPosition(user, BTC, 1050_00, 1050_00);

    assertPosition(user2, USDT, 50525_0, 50525_0);
    assertPosition(user2, BTC, 950_00, 950_00);
  }

  @Test
  public void sellMarketOrder_Fill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createMarketOrder(2, user, BTC_USDT, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user, USDT, 50500_0, 50500_0);
    assertPosition(user, BTC, 950_00, 950_00);

    assertPosition(user2, USDT, 49500_0, 49500_0);
    assertPosition(user2, BTC, 1050_00, 1050_00);
  }

  @Test
  public void sellMarketOrder_PartialFill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createMarketOrder(2, user, BTC_USDT, 60_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    expectMessage("orderId=2, ordStatus=PENDING_CANCEL");
    expectMessage("orderId=2, ordStatus=CANCELED");
    assertMessages();

    assertPosition(user, USDT, 50500_0, 50500_0);
    assertPosition(user, BTC, 950_00, 950_00);

    assertPosition(user2, USDT, 49500_0, 49500_0);
    assertPosition(user2, BTC, 1050_00, 1050_00);
  }

  @Test
  public void sellMarketOrder_Fill_PartialFill() {
    assertPosition(user, USDT, 50000_0, 50000_0);
    assertPosition(user, BTC, 1000_00, 1000_00);

    assertPosition(user2, USDT, 50000_0, 50000_0);
    assertPosition(user2, BTC, 1000_00, 1000_00);

    orderBook.addOrder(createOrder(1, user2, BTC_USDT, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createMarketOrder(2, user, BTC_USDT, 30_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=PARTIALLY_FILLED");
    assertMessages();

    assertPosition(user, USDT, 50300_0, 50300_0);
    assertPosition(user, BTC, 970_00, 970_00);

    assertPosition(user2, USDT, 49700_0, 49500_0);
    assertPosition(user2, BTC, 1030_00, 1030_00);
  }
}
