package com.solfini.objects;

import com.solfini.instrument.*;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.FastArrayList;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Chris Mack
 */
public class InstrumentPairTest {

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
    properties.setProperty("NUM_ENCODER_THREADS", "1");
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


  @Test
  public void createInstrumentPairTest() {
    final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000);
    usdt.setIndexFeedUsdMark(1);
    InstrumentCache.addInstrument(usdt);

    final Instrument spy = new Instrument(10, "SPY", "SPY", (short) 2, (short) 6, 1, 1000);
    usdt.setIndexFeedUsdMark(1);
    InstrumentCache.addInstrument(spy);

    InstrumentPair pair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", spy, usdt, (short) 2, (short) 6, 2, AssetType.PAIR, 5_00, 10_00, 260, 0);
    pair.setMarketStatus(MarketStatus.OPEN);

    // OrderBook orderBook = OrderBookFactory.create(OrderBookFactory.ARRAY_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
    // pair.setOrderBook(orderBook);
    InstrumentCache.addPair(pair);

    final User user = createUser(18);
    user.addPosition(1, 10_000_000, null, 0, TokenType.ERC20);
    user.addPosition(14, 1_000_000, null, 0, TokenType.ERC20);
    user.setActive(true);

    assertEquals(pair.getBase(), spy);
    Fee fee = pair.getDiscountFee(1, true, new Order());
    Fee fee2 = pair.getDiscountFee(1, false, new Order());

    assertEquals(0, pair.getEstimatedVAR(), .1);
    assertEquals(0, pair.getEstimatedVolatility(), .1);

    List<FeeAdminMessage> list = pair.getFeeAdminRestateList();
    assertEquals(list.size(), new FastArrayList<FeeAdminMessage>().size());

    assertEquals(MarketStatus.OPEN, pair.getMarketStatus());
    assertEquals("SPY/USD", pair.getName());

    pair.setMarginCurveId(2);
    assertEquals(2, pair.getMarginCurveId());

    pair.setRequiredMarginBasisPoints(2);
    assertEquals(2, pair.getRequiredMarginBasisPoints());

    pair.setSettleType(2);
    assertEquals(2, pair.getSettleType());

    assertTrue(pair.toString().length() > 0);

  }

}
