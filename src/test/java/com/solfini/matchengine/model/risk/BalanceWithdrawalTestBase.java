package com.solfini.matchengine.model.risk;

import java.util.Properties;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import org.junit.Assert;
import org.slf4j.event.Level;

/**
 * Shared fixture for the withdrawal tests.
 *
 * This exists as a base class rather than as a nested class inside BalanceWithdrawalTest because the withdrawal branch
 * that some of these tests exercise is selected by Context.ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS, which is a static final
 * field read once when Context is initialised. Its value is therefore fixed for the lifetime of a JVM, and surefire
 * forks one JVM per test *class* (reuseForks=false in .github/workflows/ci.yml). Nested classes share the enclosing
 * class's fork, so they cannot each pick their own value; separate top level classes can.
 *
 * Subclasses override configureProperties to set the flags they need. Everything else about the fixture is identical,
 * which is the point: the subclasses are meant to differ by configuration only.
 *
 * Deliberately abstract and deliberately not named *Test, so surefire neither collects nor forks it.
 */
public abstract class BalanceWithdrawalTestBase extends ModelTest {

  protected User user = null;

  /**
   * Hook for subclasses to add or override properties before PropertyReader is initialised. Called after the shared
   * defaults are set, so a subclass can replace any of them.
   *
   * Note that PropertyReader.initialize replaces its property map wholesale, so every property has to be present in
   * the single call made below - a subclass must not call PropertyReader.initialize itself.
   */
  protected void configureProperties(final Properties properties) {
    // no extra properties by default
  }

  @Override
  public void before() {
    LogLevel.setLevel(Level.TRACE);

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "500000");
      properties.setProperty("ENABLE_BALANCE_WITHDRAW_EXACT_LIMITS", "FALSE");
      properties.setProperty("ENABLE_BALANCE_WITHDRAW_LIMITS", "TRUE");
      configureProperties(properties);
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

    user = createUser(100, new Balance(BTC, 1_00, 2, 0, 2, null,0, TokenType.ERC20), new Balance(USDT, 10000_00, 2, 0, 2, null,0, TokenType.ERC20),
        new Balance(BTC_USDT_F, 0, 2, 0, 2, null,0, TokenType.ERC20));

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

  protected BalanceAdminMessage updateBalance(final User user, final UpdateType updateType, final Balance... balances) {
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

  protected void assertBalance(final User user, final long balanceBTC, final long balanceUSDT, final long balanceBTC_USDT_F) {
    Assert.assertEquals(balanceBTC, user.getPositionArr()[BTC].getQuantity());
    Assert.assertEquals(balanceUSDT, user.getPositionArr()[USDT].getQuantity());
    Assert.assertEquals(balanceBTC_USDT_F, user.getPositionArr()[BTC_USDT_F].getQuantity());
  }
}
