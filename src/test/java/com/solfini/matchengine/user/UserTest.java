package com.solfini.matchengine.user;

import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import com.solfini.internal.admin.schema.TokenType;
import org.slf4j.event.Level;
import org.junit.Before;
import org.junit.Test;
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
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.message.session.LogonMessage;
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
public class UserTest implements Constants {

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

  protected static void createCarbonInstruments() {
    final Instrument usdc = new Instrument(1, "USDC", "USDC", (short) 2, (short) 6, 1, 1000, 0,false, 1);
    usdc.setIndexFeedUsdMark(1);
    InstrumentCache.addInstrument(usdc);

    final Instrument carbon = new Instrument(228, "CARBON", "CARBON", (short) 2, (short) 6, 1, 1000, 0,false, 2);
    carbon.setIndexFeedUsdMark(1);
    InstrumentCache.addInstrument(carbon);

    final InstrumentPair pair =
        new InstrumentPair(229, "CARBON/USD", "CARBON/USD", usdc, carbon, (short) 2, (short) 0, 2, AssetType.PAIR, 5_00, 10_00, 40, 0);

    InstrumentCache.addPair(pair);
  }

  @Test
  public void createUserTest() {
    createInstruments();

    final User user = createUser(18);
    user.addPosition(1, 10_000_000, null, 0, TokenType.ERC20);
    Position position = user.addPosition(14, 1_000_000, null, 0, TokenType.ERC20);
    user.setActive(true);


    BalanceAdminMessage balanceAdminMessage = user.buildBalanceAdminMessage();
    UserAdminMessage userAdminMessage = user.buildUserAdminMessage();
    user.copySetPositionArr(new BalanceAdminMessage());
    user.copySetPositionArr(new ExecutionReportMessage());
    user.copySetPositionArr(new LogonMessage());
    user.copySetPositionArr(new UserAdminMessage());
    user.getCollateralSwapState();
    user.setPassword("123");
    user.getPassword();
    user.setVerification(1);
    user.getVerification();
    user.getLeverageRatio();
    user.setLmm(true);
    user.isLmm();
    user.setExternalId(99);
    user.getExternalId();
    user.setFeeTier(3);
    user.getFeeTier();
    user.getFeeTierOrig();
    user.setFirmId(45);
    user.getFirmId();
    user.setLogin("");
    user.getLogin();
    user.getMarginRatio();
    user.setPosition(position);
    user.setPositionArr(user.getPositionArr());
    user.setMarginRatio(5);
    user.setUsdMarginMaintValue(6);
    user.setUsdMarginRequiredValue(7);
    user.setUsdMarginValue(8);
    user.setUseDiscountFeesCoin(true);
    user.isUseDiscountFeesCoin();
    user.setUser(balanceAdminMessage);
    user.setUser(userAdminMessage);
    user.setUsdMarginMaintValue(1);
    user.setUsdMarginRequiredValue(2);
    user.setUsdMarginValue(3);
    user.setUsdNotionalPositionValue(4);
    user.setUsdOpenOrdersRequiredValue(5);
    user.setUsdMaxExposurePositionAndOpenOrdersValue(6);
    user.setUsdUnrealized(7);
    user.setUserType(2);
    user.setVerification(3);
    user.updateIncrement(balanceAdminMessage);
    user.updateIncrement(userAdminMessage);


    assertTrue(userAdminMessage != null);
    assertTrue(balanceAdminMessage != null);

  }

  @Test
  public void copyPositionTest() {
    createCarbonInstruments();

    final User user = createUser(18);
    user.addPosition(1, 10_000_000, null, 0, TokenType.ERC20);
    Set<long[]> assetIdTreeSet = new HashSet<>();
    long[] token = {100l, 1};
    assetIdTreeSet.add(token);
    token = new long[]{100l, 2};
    assetIdTreeSet.add(token);
    Position position = user.addPosition(228, 1, assetIdTreeSet, 0, TokenType.ERC20);
    user.setActive(true);
    user.addPosition(228, 1, assetIdTreeSet, 0, TokenType.ERC20);
    user.setActive(true);

    BalanceAdminMessage balanceAdminMessage = user.buildBalanceAdminMessage();
  }

}
