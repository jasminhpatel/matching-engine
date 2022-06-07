package com.solfini.binance.risk;

import com.solfini.common.Context;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.binance.orderbook.BinanceOrderBookTest;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.risk.RiskAutoLiquidationThread;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.junit.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class BinanceRiskTest extends BinanceOrderBookTest {
  protected static int nextUserId = 1000;

  protected final int USER_ID_ONE = 100;
  protected final int USER_ID_TWO = 101;

  protected InstrumentPair instrumentPair = null;

  protected User user100 = null;
  protected User user101 = null;

  protected MarginPreOrderCheckAndSettle preOrderCheck = null;

  protected static ManyToManyConcurrentArrayQueueCustom<User> riskToAutoLiquidatorQueue = null;
  protected static ManyToOneConcurrentArrayQueueCustom<Message> riskToMatcherQueue = null;

  RiskAutoLiquidationThread riskAutoLiquidationThread;
  Method autoLiquidateMethod = null;
  public static final TimeInForce DAY = TimeInForce.DAY;

  @Before
  @Override
  public void before() {
    super.before();

    try {
      setUp();
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Override
  protected void onConfigure(final Properties properties) {
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "4000000");
  }

  public void setUp() throws Exception {
    riskAutoLiquidationThread = new RiskAutoLiquidationThread(new NoOpIdleStrategy());
    instrumentPair = InstrumentCache.getPair(BTC_USDT_F);

    user100 = createUser(USER_ID_ONE, new Balance(BTC, 200, 0, 0, 0, null), new Balance(USDT, 200, 0, 0, 0, null),
        new Balance(BTC_USDT_F, 200, 0, 0, 0, null));
    user101 = createUser(USER_ID_TWO, new Balance(BTC, 40, 0, 0, 0, null), new Balance(USDT, 40, 0, 0, 0, null),
        new Balance(BTC_USDT_F, 40, 0, 0, 0, null));

    expectMessage("userId=100");
    expectMessage("userId=101");
    assertMessages();

    Assert.assertNotNull(user100);
    Assert.assertNotNull(user101);

    assertPositions(user100, 200_00000000L, 200_00000000L, 200_000);
    assertPositions(user101, 40_00000000L, 40_00000000L, 40_000L);

    riskToAutoLiquidatorQueue = Context.getRiskToAutoLiquidatorQueue();
    Assert.assertNotNull(riskToAutoLiquidatorQueue);

    riskToMatcherQueue = Context.getRiskToMatcherQueue();
    Assert.assertNotNull(riskToMatcherQueue);

    Assert.assertNotNull(orderBook);
    instrumentPair.setOrderBook(orderBook.orderBook());
    (orderBook.orderBook()).setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));

    Field field = orderBook.orderBook().getClass().getDeclaredField("preOrderCheck");
    if (Modifier.isPrivate(field.getModifiers())) {
      field.setAccessible(true);
      preOrderCheck = (MarginPreOrderCheckAndSettle) field.get(orderBook.orderBook());
    }
    Assert.assertNotNull(preOrderCheck);
    preOrderCheck.setLIQUIDATON_MODE(false);

    Assert.assertNotNull(riskAutoLiquidationThread);
    autoLiquidateMethod = riskAutoLiquidationThread.getClass().getDeclaredMethod("autoLiquidate", User.class, List.class);
    if (Modifier.isPrivate(autoLiquidateMethod.getModifiers())) {
      autoLiquidateMethod.setAccessible(true);
    }
    Assert.assertNotNull(autoLiquidateMethod);

    createInsuranceFundUser(User.INSURANCE_FUND);
    expectMessage("userId=" + User.INSURANCE_FUND);
    assertMessages();

    drainMessages();
  }

  @After
  public void tearDown() throws Exception {
    assertMessages(); // To catch not validated messages

    resetBalance(UserCache.getInsuranceFundUser());
    orderBook.orderBook().expireLiveSessionOrders();
    drainMessages(); // Drain mass canceled messages without asserting
  }

  protected User nextUser() {
    User user = createUser(nextUserId++, new Balance(BTC, 200, 0, 0, 0, null), new Balance(USDT, 200, 0, 0, 0, null),
        new Balance(BTC_USDT_F, 200, 0, 0, 0, null));

    expectMessage("UserAdminMessage", "userId=" + user.getId());
    assertMessages();

    return user;
  }

  protected User nextUser(final Balance... balances) {
    User user = createUser(nextUserId++, balances);

    expectMessage("UserAdminMessage", "userId=" + user.getId());
    assertMessages();

    return user;
  }

  protected User createInsuranceFundUser(final int userId, final Balance... balances) {
    UserAdminMessage message = new UserAdminMessage();
    message.setUpdateType(UpdateType.PUT);
    message.setUserId(userId);
    message.setUserType(User.INSURANCE_FUND);
    if (null != balances) {
      for (final Balance balance : balances) {
        message.addBalance(balance);
      }
    }

    UserCache.add(message);

    return UserCache.get(userId);
  }

  protected void resetBalance(User user) {
    updateBalance(user, UpdateType.PUT, new Balance(BTC, 0, 0, 0, 0, null), new Balance(USDT, 0, 0, 0, 0, null),
        new Balance(BTC_USDT_F, 0, 0, 0, 0, null));
    expectMessage("userId=" + user.getId());
    assertMessages();

    assertPositions(user, 0, 0, 0);
  }

  protected long orderId() {
    return orderBook.newOrderSingleHandler().getOrderId();
  }

  protected void drainMessages() {
    ArrayList<Message> resultList = new ArrayList<>();
    Context.getMatcherToPublisherQueue().drainTo(resultList, 1_000);
    for (int i = 0; i < resultList.size(); i++) {
      System.out.println("Message (" + i + "): " + resultList.get(i));
      System.out.println();
    }
  }

  protected static void updateBalance(final int userId, final UpdateType updateType, final Balance... balances) {
    BalanceAdminMessage message = new BalanceAdminMessage();
    if (null != balances) {
      for (final Balance balance : balances) {
        message.addBalance(balance);
      }
    }
    message.setUpdateType(updateType);
    message.setUserId(userId);
    message.setTxType(TX_ADJUSTMENT);

    UserCache.addBalance(message);
  }

  protected void assertOpenOrders(User user, int securityId, double expectedOpenOrderValue, int expectedOpenOrderCount) {
    double openOrderValue = user.getPositionArr()[securityId].getUserOpenOrdersByPair().getAsksNotional()
        + user.getPositionArr()[securityId].getUserOpenOrdersByPair().getBidsNotional();
    int openOrderCount = user.getPositionArr()[securityId].getUserOpenOrdersByPair().getAsksCount()
        + user.getPositionArr()[securityId].getUserOpenOrdersByPair().getBidsCount();
    Assert.assertEquals(expectedOpenOrderValue, openOrderValue, 0.001);
    Assert.assertEquals(expectedOpenOrderCount, openOrderCount);
    Assert.assertEquals(expectedOpenOrderCount, user.getOpenOrderCount());
  }

  protected static void updateBalance(final User user, final UpdateType updateType, final Balance... balances) {
    updateBalance(user.getId(), updateType, balances);
  }

  protected void assertPositions(User user, long btcPosition, long usdtPosition, long btcUSDTPosition) {
    Assert.assertEquals(btcPosition, user.getPositionArr()[BTC].getQuantity());
    Assert.assertEquals(usdtPosition, user.getPositionArr()[USDT].getQuantity());
    Assert.assertEquals(btcUSDTPosition, user.getPositionArr()[BTC_USDT_F].getQuantity());
  }

  protected void assertAutoLiquidationState(User user, int autoLiquidationState, int autoLiquidationCounter) {
    Assert.assertEquals(autoLiquidationState, user.getAutoLiquidationState().get());
    Assert.assertEquals(autoLiquidationCounter, user.getAutoLiquidationCounter().get());
  }

  protected void assertAutoLiquidationTriggered(final User user) {
    List<User> users = new ArrayList<User>();
    riskToAutoLiquidatorQueue.drainTo(users, 1000);

    for (User triggeredUser : users) {
      if (triggeredUser.getId() == user.getId()) {
        return;
      }
    }

    Assert.fail("Auto liquidation not triggered for user: " + user.getId());
  }

  protected void assertAutoLiquidationNotTriggered(final User user) {
    List<User> users = new ArrayList<User>();
    riskToAutoLiquidatorQueue.drainTo(users, 1000);

    for (User triggeredUser : users) {
      if (triggeredUser.getId() == user.getId()) {
        Assert.fail("Auto liquidation triggered for user: " + user.getId());
        return;
      }
    }
  }

  protected List<Message> liquidate(final User user) throws Exception {
    ArrayList<Order> autoLiquidatedOrders = new ArrayList<>();
    autoLiquidateMethod.invoke(riskAutoLiquidationThread, user, autoLiquidatedOrders);

    List<Message> messages = new ArrayList<>();
    riskToMatcherQueue.drainTo(messages, 1000);

    return messages;
  }

  protected void match(final List<Message> messages) {
    for (Message message : messages) {
      message.onMatcher();
    }
  }

  protected void setLiquidationMode(final boolean liquidationMode) {
    preOrderCheck.setLIQUIDATON_MODE(liquidationMode);
  }

  protected void setIndexFeedUsdMark(final double btcMark, final double usdtMark, final double contractMark) {
    InstrumentCache.get(BTC).setIndexFeedUsdMark(btcMark);
    InstrumentCache.get(USDT).setIndexFeedUsdMark(usdtMark);
    instrumentPair.setIndexFeedUsdMark(contractMark);
  }
}
