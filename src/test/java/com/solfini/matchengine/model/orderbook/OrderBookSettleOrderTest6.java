package com.solfini.matchengine.model.orderbook;

import static com.solfini.sbe.encoder.TimeInForce.DAY;
import java.io.IOException;
import java.util.Properties;

import com.solfini.internal.admin.schema.TokenType;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.event.Level;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

public class OrderBookSettleOrderTest6 extends OrderBookTest {
  public static final MarginPreOrderCheckAndSettle preOrderCheck = new MarginPreOrderCheckAndSettle();

  @Override
  protected void configure() {
    LogLevel.setLevel(Level.TRACE);

    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "4194304");
    properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "4028");
    properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "1024");
    properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_POOL_START_CAPACITY", "32768");
    properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "32768");
    properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "32768");
    properties.setProperty("NUM_ENCODER_THREADS", "0");
    properties.setProperty("NUM_DECODER_THREADS", "8");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    properties.setProperty("DEFAULT_SETTLE_INSTRUMENT_PRICE_SCALE_MULT", "100000000");

    onConfigure(properties);

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void createInstruments() {
    // Asset scale is 8.
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 8, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 8, 8));
    // Symbol price & quantity scale both 2
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 3));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F]");

    pair = InstrumentCache.getPair(BTC_USDT_F);

    orderBook = new OrderBookWrapper(pair);
    (orderBook.orderBook()).setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));

    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(5000);
  }

  @Override
  protected void createUsers() {
    user = createUser(18);
    user.addPosition(pair.getQuotedId(), 100000_00000000L, null, 0, TokenType.ERC20); // $100,000
    user.addPosition(BTC_USDT_F, 0, null, 0, TokenType.ERC20); // 0

    expectMessage("userId=18");
    expectOutput("userId=18");

    user2 = createUser(19);
    user2.addPosition(pair.getQuotedId(), 200000_00000000L, null, 0, TokenType.ERC20); // $200,000
    user2.addPosition(BTC_USDT_F, 0, null, 0, TokenType.ERC20); // 0
    expectMessage("userId=19");
    expectOutput("userId=19");

    user3 = createUser(20);
    user3.addPosition(pair.getQuotedId(), 300000_00000000L, null, 0, TokenType.ERC20); // $300,000
    user3.addPosition(BTC_USDT_F, 0, null, 0, TokenType.ERC20); // 0

    expectMessage("userId=20");
    expectOutput("userId=20");
  }

  public static double[] calcPositionSummary() {
    long pairQuantity = 0;
    long usdQuantity = 0;
    long btcQuantity = 0;
    double usdUnrealized = 0;

    for (int i = 18; i < 21; i++) {
      User user = UserCache.get(i);
      preOrderCheck.updateRisk(user, null);
      pairQuantity += user.getPositionArr()[BTC_USDT_F].getQuantity();
      usdQuantity += user.getPositionArr()[USDT].getQuantity();
      // btcQuantity += user.getPositionArr()[BTC].getQuantity();
      usdUnrealized += user.getUsdUnrealized();
    }
    System.out.println("pairQuantity=" + pairQuantity + ", adj=" + (double) pairQuantity / 1000);
    System.out.println("usdQuantity=" + usdQuantity + ", adj=" + (double) usdQuantity / 100_000_000);
    System.out.println("btcQuantity=" + usdQuantity + ", adj=" + (double) btcQuantity / 100_000_000);
    System.out.println("usdUnrealized=" + usdUnrealized + ", adj=" + usdUnrealized);
    return new double[] {(double) pairQuantity / 1000, (double) usdQuantity / 100_000_000, (double) btcQuantity / 100_000_000,
        usdUnrealized};
  }

  @Test
  public void checkBalanceUpdateAfterSettlement() {
    Instrument instrumentUSDT = InstrumentCache.get(USDT);
    Instrument instrumentBTC = InstrumentCache.get(BTC);

    double[] startSummary = calcPositionSummary();

    // Initial USDT balance = 100_000
    Assert.assertEquals(100000_00000000L, user.getPositionArr()[pair.getQuotedId()].getQuantity());

    orderBook.addOrder(createOrder(1, user, pair.getId(), 5000_25, 1_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=500025, orderQty=1, leavesQty=1, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(2, user2, pair.getId(), 5000_25, 1_00, Side.SELL, DAY));
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=500025, orderQty=1, leavesQty=1, ordStatus=NEW");
    expectMessage("orderId=2, ordType=LIMIT, side=SELL, price=500025, orderQty=1, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=500025, orderQty=1, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();

    // Bought BTC/USDT 1 at 5000, position = 1
    Assert.assertEquals(1_000, user.getPositionArr()[pair.getId()].getQuantity());
    Assert.assertEquals(100000_00000000L, user.getPositionArr()[pair.getQuotedId()].getQuantity());

    orderBook.addOrder(createOrder(3, user3, pair.getId(), 6000_00, 1_00, Side.BUY, DAY));
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=6000, orderQty=1, leavesQty=1, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(4, user, pair.getId(), 6000_00, 1_00, Side.SELL, DAY));
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=600000, orderQty=1, leavesQty=1, ordStatus=NEW");
    expectMessage("orderId=4, ordType=LIMIT, side=SELL, price=600000, orderQty=1, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    expectMessage("orderId=3, ordType=LIMIT, side=BUY, price=600000, orderQty=1, leavesQty=0, execType=TRADE, ordStatus=FILLED");
    assertMessages();


    Assert.assertEquals(0, user.getPositionArr()[pair.getId()].getQuantity());
    // Buy at 5000.25
    // Sold BTC/USDT 1 at 6000, position = 0, profit is +999.75
    // Final USDT balance should be 10000 + 1000 = 11000
    Assert.assertEquals(100999_75000000L, user.getPositionArr()[pair.getQuotedId()].getQuantity());
    // user2 has -1 usdAvgCostBasis=5000.25, usdValue=0.25, usdUnrealized=0.25
    // user3 has 1 usdAvgCostBasis=6000.0, usdUnrealized=-1000.0


    double[] endSummary = calcPositionSummary();

  }
}
