package com.solfini.matchengine.instrument;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.Properties;

import com.solfini.internal.admin.schema.TokenType;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

/**
 *
 * @author Chris Mack
 *
 */
public class InstrumentTest {

  @Before
  public void before() {
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

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected User createUser(final int userId, final Balance... balances) {
    UserAdminMessage message = new UserAdminMessage();
    message.setUpdateType(UpdateType.PUT);
    message.setUserId(userId);
    if (null != balances) {
      for (final Balance balance : balances) {
        message.addBalance(balance);
      }
    }

    UserCache.add(message);

    return UserCache.get(userId);
  }

  protected static void createInstruments() {
    final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000, 0,false, 1);
    usdt.setIndexFeedUsdMark(1);
    InstrumentCache.addInstrument(usdt);

    InstrumentPair pair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, usdt, (short) 2, (short) 6, 2, AssetType.PAIR, 5_00, 10_00, 260, 0);

    // OrderBook orderBook = OrderBookFactory.create(OrderBookFactory.ARRAY_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
    // pair.setOrderBook(orderBook);
    InstrumentCache.addPair(pair);
  }

  @Test
  public void createInstrumentPairTest() {
    createInstruments();

    final User user = createUser(18);
    user.addPosition(1, 10_000_000, null, 0, TokenType.ERC20);
    user.addPosition(14, 1_000_000, null, 0, TokenType.ERC20);
    user.setActive(true);


    InstrumentPair pair = InstrumentCache.getPairBySymbol("SPY/USD");
    InstrumentCache.getPairIterator();
    InstrumentCache.resizeInstrumentCache(InstrumentCache.getPairCapacity() + 16);
    InstrumentCache.restateAllInstrumentsPairsAndFees(111);
    SecurityDefinitionAdminMessage delete = new SecurityDefinitionAdminMessage();
    delete.setUpdateType(UpdateType.DELETE);
    delete.setAssetType(AssetType.ASSET);
    InstrumentCache.updateSecurityDefinition(delete);
    delete.setAssetType(AssetType.PAIR);
    InstrumentCache.updateSecurityDefinition(delete);

    assertTrue(pair != null);
    assertTrue(delete != null);

  }

  @Test
  public void createInstrumentTest() {
    createInstruments();

    final User user = createUser(18);
    user.addPosition(1, 10_000_000, null, 0, TokenType.ERC20);
    user.addPosition(14, 1_000_000, null, 0, TokenType.ERC20);
    user.setActive(true);


    Instrument instrument = InstrumentCache.getBySymbol("USDT");
    assertTrue(instrument != null);

    instrument.getName();
    instrument.getPriceMultiplier();
    instrument.getPriceScaleFactor();
    instrument.getQuotedInstrumentId();
    // instrument.getSecurityType();
    instrument.setQuotedInstrumentId(1);
    // instrument.setSecurityType(SecurityType.FUTURE);
    instrument.setStatus(1);
    instrument.getStatus();

    assertEquals("USDT", instrument.getSymbol());

    instrument.setCollateralMarginPercentDiscount(10);
    assertEquals(10, instrument.getCollateralMarginPercentDiscount());
  }

  @Test
  public void createSecurityDefinitionAdminMessageTest() {
    createInstruments();

    final User user = createUser(18);
    user.addPosition(1, 10_000_000, null, 0, TokenType.ERC20);
    user.addPosition(14, 1_000_000, null, 0, TokenType.ERC20);
    user.setActive(true);


    Instrument instrument = InstrumentCache.getBySymbol("USDT");
    assertTrue(instrument != null);

    InstrumentPair pair = InstrumentCache.getPairBySymbol("SPY/USD");
    assertTrue(pair != null);

    SecurityDefinitionAdminMessage message = new SecurityDefinitionAdminMessage();

    message = new SecurityDefinitionAdminMessage(instrument);
    message = new SecurityDefinitionAdminMessage(pair);

    message.getBase();
    message.getBaseId();
    message.setDaysFeedIsActive(7);
    message.getDaysFeedIsActive();
    message.getEstimatedUserCount();
    message.getEstimatedVAR();
    message.getEstimatedVolatility();
    message.getPayloadType();
    message.getQuoted();
    message.getQuotedId();
    message.setCollateralMarginPercentDiscount(10);
    message.setCommissionType(5);
    message.setEstimatedUserCount(5);
    message.setEstimatedVAR(6);
    message.setEstimatedVolatility(7);
    message.setIndexFeedUsdMark(8);
    message.setRequiredMarginBasisPoints(9);
    message.setMarginCurveId(11);
    message.setMaxPrice(12);
    message.setMaxQty(13);
    message.setMinQty(14);
    message.setQuoted(instrument);
    message.setRequiredMarginBasisPoints(55);
    message.setSettleType(2);
    message.setSupportOrderType(3);

    // message.onPublish();


    assertEquals("USDT", instrument.getSymbol());

    instrument.setCollateralMarginPercentDiscount(10);
    assertEquals(10, instrument.getCollateralMarginPercentDiscount());
  }
}
