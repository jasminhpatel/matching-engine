package com.solfini.matchengine.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.Properties;

import com.solfini.internal.admin.schema.TokenType;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;
import com.solfini.common.Constants;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
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
public class UserBalanceChangeTest implements Constants {

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
  public void balanceAdminTestWithdrawLimit() {
    createInstruments();

    final User user = createUser(18);
    Position position1 = user.addPosition(1, 10_000_000, null, 0, TokenType.ERC20);
    Position position14 = user.addPosition(14, 1_000_000, null, 0, TokenType.ERC20);
    user.setActive(true);
    user.setUsdValue(1000);

    BalanceAdminMessage balanceAdminMessage = new BalanceAdminMessage();
    balanceAdminMessage.setUserId(user.getId());
    balanceAdminMessage.setUser(user);
    balanceAdminMessage.setUpdateType(UpdateType.PUT);
    balanceAdminMessage.setTxType(TX_DEPOSIT);

    Balance balance = new Balance();
    balance.setAssetId(1);
    balanceAdminMessage.getBalanceList().add(balance);

    balance.setBalanceChange(2000_00, 2);
    user.updateIncrement(balanceAdminMessage);


    assertEquals(2010_000000, position1.getQuantity());

    balance.setBalanceChange(-3000_00, 2);
    user.updateIncrement(balanceAdminMessage);

    assertEquals(0, position1.getQuantity()); // only allow to withdraw to 0

    balance.setBalanceChange(2000_00, 2);
    user.updateIncrement(balanceAdminMessage);
    user.setUsdMarginRequiredValue(100);
    assertEquals(2000_000000, position1.getQuantity()); // deposit again

    balance.setBalanceChange(-2900_00, 2);
    user.updateIncrement(balanceAdminMessage);

    // only allow to withdraw 900, the difference between usdvalue of 1000 minus UsdMarginRequiredValue of 100
    assertEquals(1100_000000, position1.getQuantity());

    assertTrue(balanceAdminMessage != null);


  }
}
