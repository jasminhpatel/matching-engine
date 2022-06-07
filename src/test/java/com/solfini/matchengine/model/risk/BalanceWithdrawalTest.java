package com.solfini.matchengine.model.risk;

import java.util.Properties;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.event.Level;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class BalanceWithdrawalTest extends ModelTest {

  private User user = null;

  @Override
  public void before() {
    LogLevel.setLevel(Level.TRACE);

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "500000");
      properties.setProperty("ENABLE_BALANCE_WITHDRAW_EXACT_LIMITS", "FALSE");
      properties.setProperty("ENABLE_BALANCE_WITHDRAW_LIMITS", "TRUE");
      PropertyReader.initialize(null, properties);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PUT, "BTC/USDT[F]", BTC, USDT, 2, 2));

    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F]");
    assertMessages();

    Assert.assertNotNull(InstrumentCache.get(BTC));
    Assert.assertNotNull(InstrumentCache.get(USDT));
    Assert.assertNotNull(InstrumentCache.getPair(BTC_USDT_F));

    user = createUser(100, new Balance(BTC, 1_00, 2, 0, 2, null), new Balance(USDT, 10000_00, 2, 0, 2, null),
        new Balance(BTC_USDT_F, 0, 2, 0, 2, null));

    assertBalance(user, 1_00, 10000_00, 0);

    expectMessage("userId=100");
    assertMessages();

    UserCache.processRisk(null);
    InstrumentCache.get(BTC).setIndexFeedUsdMark(5000);
    InstrumentCache.get(USDT).setIndexFeedUsdMark(1);
    InstrumentCache.getPair(BTC_USDT_F).setIndexFeedUsdMark(4950);
  }

  @Override
  public void after() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().expireLiveSessionOrders();
    super.after();
  }

  private BalanceAdminMessage updateBalance(final User user, final UpdateType updateType, final Balance... balances) {
    BalanceAdminMessage message = new BalanceAdminMessage();
    message.setUpdateType(updateType);
    message.setUserId(user.getId());
    message.setTxType(TX_WITHDRAW);

    if (null != balances) {
      for (final Balance balance : balances) {
        message.addBalance(balance);
      }
    }

    UserCache.addBalance(message);
    return message;
  }

  private void assertBalance(final User user, final long balanceBTC, final long balanceUSDT, final long balanceBTC_USDT_F) {
    Assert.assertEquals(balanceBTC, user.getPositionArr()[BTC].getQuantity());
    Assert.assertEquals(balanceUSDT, user.getPositionArr()[USDT].getQuantity());
    Assert.assertEquals(balanceBTC_USDT_F, user.getPositionArr()[BTC_USDT_F].getQuantity());
  }

  // Withdraw asset balance - positive to positive
  @Test
  public void withdrawBTC_PositiveToPositive() {
    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -50, 2, null));
    assertBalance(user, 50, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=.50, balance_change=-.50");
    assertMessages();
  }

  // Withdraw asset balance - positive to zero
  @Test
  public void withdrawBTC_PositiveToZero() {
    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -100, 2, null));
    assertBalance(user, 0, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=.00, balance_change=-1.00");
    assertMessages();
  }

  // Withdraw asset balance - positive to negative (limits at zero)
  @Test
  public void withdrawBTC_PositiveToNegative() {
    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -150, 2, null));
    assertBalance(user, 0, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=.00, balance_change=-1.00");
    assertMessages();
  }

  // Withdraw asset balance with open orders - positive to positive
  @Test
  public void withdrawBTC_WithOpenOrders_PositiveToPositive() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 10_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -50, 2, null));
    assertBalance(user, 50, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=.50, balance_change=-.50");
    assertMessages();
  }

  // Withdraw asset balance with open orders - positive to zero
  @Test
  public void withdrawBTC_WithOpenOrders_PositiveToZero() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 10_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -100, 2, null));
    assertBalance(user, 0, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=.00, balance_change=-1.00");
    assertMessages();
  }

  // Withdraw asset balance with open orders - positive to negative (limits at zero)
  @Test
  public void withdrawBTC_WithOpenOrders_PositiveToNegative() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 10_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -150, 2, null));
    assertBalance(user, 0, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=.00, balance_change=-1.00");
    assertMessages();
  }

  // Withdraw quote asset balance - positive to positive
  @Test
  public void withdrawUSDT_PositiveToPositive() {
    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -5000_00, 2, null));
    assertBalance(user, 1_00, 5000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=5000.00, balance_change=-5000.00");
    assertMessages();
  }

  // Withdraw quote asset balance - positive to zero
  @Test
  public void withdrawUSDT_PositiveToZero() {
    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -10000_00, 2, null));
    assertBalance(user, 1_00, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=.00, balance_change=-10000.00");
    assertMessages();
  }

  // Withdraw quote asset balance - positive to negative (limits at zero)
  @Test
  public void withdrawUSDT_PositiveToNegative() {
    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -15000_00, 2, null));
    assertBalance(user, 1_00, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=.00, balance_change=-10000.00");
    assertMessages();
  }

  // Withdraw quote asset balance with open orders - below withdrawal limit
  @Test
  public void withdrawUSDT_WithOpenOrders_BelowWithdrawalLimit() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 10_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -1000_00, 2, null));
    assertBalance(user, 1_00, 9000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=9000.00, balance_change=-1000.00");
    assertMessages();
  }

  // Withdraw quote asset balance with open orders - same as withdrawal limit
  @Test
  public void withdrawUSDT_WithOpenOrders_EqualToWithdrawalLimit() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 50_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -2625_00, 2, null));
    assertBalance(user, 1_00, 7375_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=7375.00, balance_change=-2625.00");
    assertMessages();
  }

  // Withdraw quote asset balance with open orders - above withdrawal limit
  @Test
  public void withdrawUSDT_WithOpenOrders_AboveWithdrawalLimit() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 50_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -9000_00, 2, null));
    assertBalance(user, 1_00, 7375_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=7375.00, balance_change=-2625.00");
    assertMessages();
  }
}
