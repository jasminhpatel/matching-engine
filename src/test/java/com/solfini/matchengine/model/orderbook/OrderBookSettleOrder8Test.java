package com.solfini.matchengine.model.orderbook;

import java.util.Properties;

import com.solfini.internal.admin.schema.TokenType;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookSettleOrder8Test extends OrderBookTest {

  private static final int USDT_PRICE_SCALE = 8;

  protected void onConfigure(final Properties properties) {
    int mult = 1;
    for (int i = 0; i < USDT_PRICE_SCALE; i++) {
      mult = mult * 10;
    }

    properties.setProperty("DEFAULT_SETTLE_INSTRUMENT_PRICE_SCALE_MULT", "" + mult);
  }

  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", USDT_PRICE_SCALE, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 4, 3));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 6));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F]");

    pair = InstrumentCache.getPair(BTC_USDT_F);

    orderBook = new OrderBookTest.OrderBookWrapper(pair);
    (orderBook.orderBook()).setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));


    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(100);
    pair.getQuoted().setIndexFeedUsdMark(1);
  }

  protected void createUsers() {
    user = createUser(18);
    user.addPosition(pair.getQuotedId(), 1_00000000, null, 0, TokenType.ERC20);
    expectMessage("userId=18");
    expectOutput("userId=18");

    user2 = createUser(19);
    user2.addPosition(pair.getQuotedId(), 1_00000000, null, 0, TokenType.ERC20);
    expectMessage("userId=19");
    expectOutput("userId=19");

    user3 = createUser(20);
    user3.addPosition(pair.getQuotedId(), 1_00000000, null, 0, TokenType.ERC20);
    expectMessage("userId=20");
    expectOutput("userId=20");
  }

  // USDT price scale 8
  // Buy 1 at 9000, sell 1 at 10000, assert balance increase by 1000
  // UDST instrument definition.
  @Test
  public void checkBalanceUpdateAfterSettlement_USDTPriceScale8() {
    // Initial USDT balance = 1.00000000
    Assert.assertEquals(1_00000000, user.getPositionArr()[pair.getQuotedId()].getQuantity());

    orderBook.addOrder(createOrder(1, user, pair.getId(), 9023, 1_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=9023, orderQty=100, leavesQty=100, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 9023, 1_00, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=9023, orderQty=100, leavesQty=100, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=9023, orderQty=100, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=9023, orderQty=100, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();

    // Bought BTC/USDT 1 at 9000, position = 1
    Assert.assertEquals(1_000000, user.getPositionArr()[pair.getId()].getQuantity());

    orderBook.addOrder(createOrder(3, user3, pair.getId(), 10000, 1_00, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=10000, orderQty=100, leavesQty=100, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(4, user, pair.getId(), 10000, 1_00, Side.SELL, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=10000, orderQty=100, leavesQty=100, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=10000, orderQty=100, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=10000, orderQty=100, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();

    // Sold BTC/USDT 1 at 10000, position = 0
    // Settled +1000 USDT (10000 - 9000)
    Assert.assertEquals(0, user.getPositionArr()[pair.getId()].getQuantity());
    Assert.assertEquals(1_00000977, user.getPositionArr()[pair.getQuotedId()].getQuantity());
  }
}
