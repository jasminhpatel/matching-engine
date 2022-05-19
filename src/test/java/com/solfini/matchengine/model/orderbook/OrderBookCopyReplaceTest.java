package com.solfini.matchengine.model.orderbook;

import org.junit.Assert;
import org.junit.Test;

import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.sbe.encoder.Side;

import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookCopyReplaceTest extends OrderBookTest {

  private static final int USDT_PRICE_SCALE = 2;

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDC", USDT_PRICE_SCALE, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 4, 3));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", USDT, BTC, 2, 6));

    expectMessage("securityId=" + USDT + ", symbol=USDC");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F]");

    pair = InstrumentCache.getPair(BTC_USDT_F);
    Assert.assertNotNull(pair);

    orderBook = new OrderBookTest.OrderBookWrapper(pair);
    orderBook.orderBook().setSettleCoinUsdMarkInstrument(InstrumentCache.get(USDT));

    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(9000);
    pair.getQuoted().setIndexFeedUsdMark(1);
  }

  // USDT price scale 2
  // Buy 1 at 9000, sell 1 at 10000, assert balance increase by 1000
  @Test
  public void recreateReplaceOrderBook() {
    // Initial USDT balance = 10_000
    Assert.assertEquals(10_000_00, user.getPositionArr()[USDT].getQuantity());

    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 9_023, 1_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=9023, orderQty=100, leavesQty=100, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(2, user2, BTC_USDT_F, 9_025, 1_00, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=9025, orderQty=100, leavesQty=100, ordStatus=NEW");
    assertMessages();

    OrderBook newOrderBook = OrderBookFactory.recreateReplace(DEFAULT_TEST_ORDER_BOOK, MARGIN_PREORDER_CHECK, pair, pair, 10_000_00, 512);

    // new order book should not republish original order messages, only new messages

    Assert.assertEquals(9_023, newOrderBook.getBid());
    Assert.assertEquals(9_025, newOrderBook.getAsk());

    newOrderBook.addOrder(createOrder(3, user, BTC_USDT_F, 9_095, 1_00, Side.BUY, DAY));


    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=9095, orderQty=100, leavesQty=100, ordStatus=NEW");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, lastPx=9025, price=9095, leavesQty=0, orderQty=100, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=9025, orderQty=100, execType=TRADE, ordStatus=FILLED");
    assertMessages();
  }
}
