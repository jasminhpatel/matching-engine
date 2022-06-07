package com.solfini.matchengine.model.orderbook;

import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import org.junit.Test;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.user.User;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.DAY;
import java.util.Properties;

/**
 *
 * @author Chris Mack
 *
 */
public class MarginOrderBookTest extends OrderBookTest {

  @Override
  protected void onConfigure(final Properties properties) {
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "500000");
  }

  @Test
  public void testMarginPreOrderCheck() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    expectMessage("securityId=" + USDT + ", symbol=USDT");

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 4, 3));
    expectMessage("securityId=" + BTC + ", symbol=BTC");

    final SecurityDefinitionAdminMessage securityDefinitionAdminMessage =
        createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 2);
    InstrumentCache.updateSecurityDefinition(securityDefinitionAdminMessage);
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");

    final Instrument base = InstrumentCache.get(BTC);
    final Instrument quoted = InstrumentCache.get(USDT);
    final InstrumentPair instrumentPair = InstrumentCache.getPair(BTC_USDT_F);

    final OrderBook orderBook =
        OrderBookFactory.create(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, instrumentPair);
    orderBook.setSettleCoinUsdMarkInstrument(quoted);

    instrumentPair.setOrderBook(orderBook);

    final User user1 = createUser(18);
    expectMessage("userId=18");

    final User user2 = createUser(19);
    expectMessage("userId=19");

    quoted.setIndexFeedUsdMark(1.00);
    orderBook.setMark(125_000);
    base.setIndexFeedUsdMark(6_000);

    user1.setPosition(USDT, 1_000_000, null);
    user1.setPosition(BTC, 5_000, null);
    user1.setPosition(BTC_USDT_F, 300, null);
    user2.setPosition(USDT, 1_000_000, null);
    user2.setPosition(BTC, 5_000, null);

    int orderId = 0;
    orderBook.setMark(120_000);
    orderBook.addOrder(createOrder(orderId++, user1, BTC_USDT_F, 12_000, 500, Side.BUY, DAY));
    expectMessage("ordStatus=NEW");
    assertMessages();


    orderBook.setMark(140_000);
    base.setIndexFeedUsdMark(7_000);
    orderBook.addOrder(createOrder(orderId++, user1, BTC_USDT_F, 12_000, 500, Side.BUY, DAY));
    expectMessage("ordStatus=NEW");
    assertMessages();

    orderBook.setMark(145_000);
    orderBook.addOrder(createOrder(orderId++, user2, BTC_USDT_F, 12_000, 500, Side.SELL, DAY));
    expectMessage("ordStatus=NEW");
    expectMessage("ordStatus=FILLED");
    expectMessage("ordStatus=FILLED");
    assertMessages();

    orderBook.setMark(150_000);
    orderBook.addOrder(createOrder(orderId++, user2, BTC_USDT_F, 12_000, 500, Side.SELL, DAY));
    expectMessage("ordStatus=NEW");
    expectMessage("ordStatus=FILLED");
    expectMessage("ordStatus=FILLED");
    assertMessages();

    orderBook.setMark(155_000);
    orderBook.addOrder(createOrder(orderId++, user2, BTC_USDT_F, 12_000, 500, Side.BUY, DAY));
    expectMessage("ordStatus=NEW");
    assertMessages();

    orderBook.setMark(160_000);
    orderBook.addOrder(createOrder(orderId++, user1, BTC_USDT_F, 12_000, 500, Side.SELL, DAY));
    expectMessage("ordStatus=NEW");
    expectMessage("ordStatus=FILLED");
    expectMessage("ordStatus=FILLED");

    assertMessages();
  }

  @Test
  public void testCloseOrderMarginPreOrderCheck() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    expectMessage("securityId=" + USDT + ", symbol=USDT");

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 4, 3));
    expectMessage("securityId=" + BTC + ", symbol=BTC");

    final SecurityDefinitionAdminMessage securityDefinitionAdminMessage =
        createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 2);
    InstrumentCache.updateSecurityDefinition(securityDefinitionAdminMessage);
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");

    final Instrument base = InstrumentCache.get(BTC);
    final Instrument quoted = InstrumentCache.get(USDT);
    final InstrumentPair instrumentPair = InstrumentCache.getPair(BTC_USDT_F);

    final OrderBook orderBook =
        OrderBookFactory.create(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, instrumentPair);
    (orderBook).setSettleCoinUsdMarkInstrument(quoted);
    PreOrderCheck preOrderCheck = orderBook.getPreOrderCheck();

    instrumentPair.setOrderBook(orderBook);
    instrumentPair.setIndexFeedUsdMark(150);

    final User user1 = createUser(18);
    expectMessage("userId=18");

    final User user2 = createUser(19);
    expectMessage("userId=19");

    quoted.setIndexFeedUsdMark(1.00);
    orderBook.setMark(125_000);
    base.setIndexFeedUsdMark(6_000);

    user1.setPosition(USDT, 1_000_000, null);
    user1.setPosition(BTC, 5_000, null);
    user1.setPosition(BTC_USDT_F, 300, null);
    user2.setPosition(USDT, 1_000_000, null);
    user2.setPosition(BTC, 5_000, null);

    preOrderCheck.updateRisk(user, null);
    System.out.println("user=" + user);

    int orderId = 0;
    orderBook.setMark(145_000);
    orderBook.addOrder(createOrder(orderId++, user2, BTC_USDT_F, 123_000, 500, Side.SELL, DAY));
    expectMessage("symbol=BTC/USDT[F], ordType=LIMIT, side=SELL, price=123000, orderQty=500, ordStatus=NEW, execType=NEW");

    preOrderCheck.updateRisk(user, null);
    System.out.println("user2=" + user);

    assertMessages();
  }
}
