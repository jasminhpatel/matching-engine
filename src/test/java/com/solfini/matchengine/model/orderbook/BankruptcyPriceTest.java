package com.solfini.matchengine.model.orderbook;

import static com.solfini.sbe.encoder.TimeInForce.DAY;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.event.Level;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.risk.RiskAutoLiquidationThread;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

public class BankruptcyPriceTest extends OrderBookTest {
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
    user.addPosition(pair.getQuotedId(), 200_00000000L, null); // $200
    user.addPosition(BTC_USDT_F, 0, null); // 0

    expectMessage("userId=18");
    expectOutput("userId=18");

    user2 = createUser(19);
    user2.addPosition(pair.getQuotedId(), 200000_00000000L, null); // $200,000
    user2.addPosition(BTC_USDT_F, 0, null); // 0
    expectMessage("userId=19");
    expectOutput("userId=19");

    user3 = createUser(20);
    user3.addPosition(pair.getQuotedId(), 300000_00000000L, null); // $300,000
    user3.addPosition(BTC_USDT_F, 0, null); // 0

    expectMessage("userId=20");
    expectOutput("userId=20");
  }



  @Test
  public void checkBankrupt1() { // short loss
    final List<Order> list = new ArrayList<>();
    final double[] usdMarkPricesToSet = new double[128];
    usdMarkPricesToSet[USDT] = 1;
    usdMarkPricesToSet[BTC_USDT_F] = 10564.42;
    pair = InstrumentCache.getPair(BTC_USDT_F);
    pair.setIndexFeedUsdMark(10564.42);

    Position usdPosition = user.getPosition(USDT);
    usdPosition.setQuantity(200_00000000L);

    Position perpPosition = user.getPosition(BTC_USDT_F);
    perpPosition.setUsdCostBasis(9500);
    perpPosition.setUsdAvgCostBasis(9500_000000L);
    perpPosition.setQuantity(-300);

    RiskAutoLiquidationThread.calcBankruptcyPrices(user, list, usdMarkPricesToSet);

    System.out.println("usdPosition=" + usdPosition);
    System.out.println("perpPosition=" + perpPosition);

  }

  @Test
  public void checkBankrupt2() { // long gain
    final List<Order> list = new ArrayList<>();
    final double[] usdMarkPricesToSet = new double[128];
    usdMarkPricesToSet[USDT] = 1;
    usdMarkPricesToSet[BTC_USDT_F] = 10564.42;
    pair = InstrumentCache.getPair(BTC_USDT_F);
    pair.setIndexFeedUsdMark(10564.42);

    Position usdPosition = user.getPosition(USDT);
    usdPosition.setQuantity(200_00000000L);

    Position perpPosition = user.getPosition(BTC_USDT_F);
    perpPosition.setUsdCostBasis(9500);
    perpPosition.setUsdAvgCostBasis(9500_000000L);
    perpPosition.setQuantity(300);

    RiskAutoLiquidationThread.calcBankruptcyPrices(user, list, usdMarkPricesToSet);

    System.out.println("usdPosition=" + usdPosition);
    System.out.println("perpPosition=" + perpPosition);

  }

  @Test
  public void checkBankrupt3() { // short gain
    final List<Order> list = new ArrayList<>();
    final double[] usdMarkPricesToSet = new double[128];
    usdMarkPricesToSet[USDT] = 1;
    usdMarkPricesToSet[BTC_USDT_F] = 9500;
    pair = InstrumentCache.getPair(BTC_USDT_F);
    pair.setIndexFeedUsdMark(9500);

    Position usdPosition = user.getPosition(USDT);
    usdPosition.setQuantity(200_00000000L);

    Position perpPosition = user.getPosition(BTC_USDT_F);
    perpPosition.setUsdCostBasis(10564.42);
    perpPosition.setUsdAvgCostBasis(10564_420000L);
    perpPosition.setQuantity(-300);

    RiskAutoLiquidationThread.calcBankruptcyPrices(user, list, usdMarkPricesToSet);

    System.out.println("usdPosition=" + usdPosition);
    System.out.println("perpPosition=" + perpPosition);

  }
}
