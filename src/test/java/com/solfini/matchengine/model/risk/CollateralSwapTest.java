package com.solfini.matchengine.model.risk;

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
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;
import java.io.IOException;
import java.util.Properties;
import static org.junit.Assert.assertEquals;

/**
 * @author Chris Mack
 */
public class CollateralSwapTest {

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
    final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000);
    usdt.setIndexFeedUsdMark(1);
    InstrumentCache.addInstrument(usdt);

    InstrumentPair pair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, usdt, (short) 2, (short) 6, 2, AssetType.PAIR, 5_00, 10_00, 260, 0);

    // OrderBook orderBook = OrderBookFactory.create(OrderBookFactory.ARRAY_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
    // pair.setOrderBook(orderBook);
    InstrumentCache.addPair(pair);
  }

  @Test
  public void createCollateralSwapTest() {
    // TODO:
    createInstruments();
  }



  // public static void main(String[] args) {
  // final Instrument usdt = new Instrument(1, "USDT", "USDT", 2, 6, 1, 1000);
  // usdt.setIndexFeedUsdMark(1);
  // InstrumentCache.addInstrument(usdt);

  // final Instrument btc = new Instrument(2, "BTC", "BTC", 2, 6, 1, 1000);
  // btc.setIndexFeedUsdMark(5200);
  // InstrumentCache.addInstrument(btc);

  // final Instrument eth = new Instrument(3, "ETH", "ETH", 2, 6, 1, 1000);
  // eth.setIndexFeedUsdMark(160);
  // InstrumentCache.addInstrument(eth);

  // InstrumentPair pair = new InstrumentPair(14, "SPY/USD", "SPY/USD", null, usdt, 2, 6, 2, 5_00, 10_00, 260, 0);
  // pair.setIndexFeedUsdMark(300);
  // InstrumentCache.addPair(pair);

  // final int positionId = 22;
  // final User user = new User(18);
  // final Position[] positionArr = new Position[32];

  // positionArr[1] = new Position(user, 1);
  // positionArr[1].setQuantity(-50_000_000_000L);
  // positionArr[1].setAvailableQuantity(10_000_000);

  // positionArr[2] = new Position(user, 2);
  // positionArr[2].setQuantity(10_000_000);
  // positionArr[2].setAvailableQuantity(10_000_000);

  // positionArr[3] = new Position(user, 3);
  // positionArr[3].setQuantity(10_000_000);
  // positionArr[3].setAvailableQuantity(10_000_000);

  // user.setPositionArr(positionArr);

  // CollateralSwapMessage message = new CollateralSwapMessage(user);
  // message.onMatcher();
  // }


}
