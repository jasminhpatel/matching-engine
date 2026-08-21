package com.solfini.matchengine.user;

import java.io.IOException;
import java.util.Properties;

import com.solfini.internal.admin.schema.Sector;
import org.junit.Before;
import org.slf4j.event.Level;
import com.solfini.common.Constants;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

/**
 * Shared fixture for the balance-change withdrawal tests.
 *
 * Split into a base plus two subclasses because the outcome of an over-withdrawal is decided by
 * Context.ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS, which is a static final read once when Context initialises. Its value is
 * therefore fixed for the life of a JVM, and surefire forks one JVM per test *class* (reuseForks=false in
 * .github/workflows/ci.yml). A nested class shares the enclosing class's fork and cannot pick its own value, so the two
 * outcomes need two top level classes.
 *
 * Subclasses override configureProperties to set the flag they need. Note that PropertyReader.initialize replaces its
 * property map wholesale, so every property has to be present in the single call made below - a subclass must not call
 * PropertyReader.initialize itself.
 *
 * Deliberately abstract and deliberately not named *Test, so surefire neither collects nor forks it.
 */
public abstract class UserBalanceChangeTestBase implements Constants {

  /**
   * Hook for subclasses to add or override properties before PropertyReader is initialised.
   */
  protected void configureProperties(final Properties properties) {
    // no extra properties by default
  }

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
    properties.setProperty("DEFAULT_SETTLE_INSTRUMENT_PRICE_SCALE_MULT", "1000000");
    properties.setProperty("ENABLE_BALANCE_WITHDRAW_EXACT_LIMITS", "FALSE");
    properties.setProperty("ENABLE_BALANCE_WITHDRAW_LIMITS", "TRUE");
    configureProperties(properties);

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
    final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000, 0,false, 1, Sector.NOT_DEFINED);
    usdt.setIndexFeedUsdMark(1);
    InstrumentCache.addInstrument(usdt);

    InstrumentPair pair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, usdt, (short) 2, (short) 6, 2, AssetType.PAIR, 5_00, 10_00, 260, 0, Sector.NOT_DEFINED);

    // OrderBook orderBook = OrderBookFactory.create(OrderBookFactory.ARRAY_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
    // pair.setOrderBook(orderBook);
    InstrumentCache.addPair(pair);
  }
}
