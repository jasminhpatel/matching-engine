package com.solfini.matchengine.model.risk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.event.Level;
import org.junit.Before;
import org.junit.Test;

import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.model.orderbook.OrderBookTest;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.risk.InsuranceState;
import com.solfini.user.User;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

/**
 *
 * @author Chris Mack
 *
 */
public class RiskThreadTest extends OrderBookTest {
  protected OrderBookWrapper orderBook = null;
  protected InstrumentPair pair = null;
  protected User user = null;
  protected User user2 = null;
  protected User user3 = null;

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
    onConfigure(properties);

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected void onConfigure(final Properties properties) {}

  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 4, 3));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 2));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");

    pair = InstrumentCache.getPair(BTC_USDT_F);

    orderBook = new OrderBookTest.OrderBookWrapper(pair);
    (orderBook.orderBook()).setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));

    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(100);
  }

  protected void createUsers() {
    user = createUser(18);

    user.addPosition(USDT, 10_000_00);
    expectMessage("userId=18");

    user2 = createUser(19);
    user2.addPosition(USDT, 10_000_00);
    expectMessage("userId=19");

    user3 = createUser(20);
    user3.addPosition(USDT, 10_000_00);
    expectMessage("userId=20");
  }

  @Before
  @Override
  public void before() {
    configure();
    createInstruments();
    createUsers();
    assertMessages();
  }

  @Test
  public void useInsuranceStateTest() {
    InsuranceState.setUser(new User(11));
    User user = InsuranceState.getUser();
    assertTrue(user != null);

    InsuranceState.setInsuranceAutoCloseMode(1);
    assertEquals(1, InsuranceState.getInsuranceAutoCloseMode());

    InsuranceState.setInsuranceLossPercentLimit(50);
    assertEquals(50, InsuranceState.getInsuranceLossPercentLimit());

    InsuranceState.setInsurancePositonPercentLimit(10);
    assertEquals(10, InsuranceState.getInsurancePositonPercentLimit());

    InsuranceState.setUseInsurance(true);
    assertEquals(true, InsuranceState.isUseInsurance());
  }
}
