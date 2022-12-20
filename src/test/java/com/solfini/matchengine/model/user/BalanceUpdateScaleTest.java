package com.solfini.matchengine.model.user;

import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BalanceUpdateScaleTest extends ModelTest {

  private User user100 = null;
  private User user101 = null;
  private User user102 = null;

  @Before
  public void before() {
    super.before();

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

    user100 =
        createUser(100, new Balance(BTC, 0, 2, 0, 2, null,0, TokenType.ERC20), new Balance(USDT, 0, 2, 0, 2, null,0, TokenType.ERC20), new Balance(BTC_USDT_F, 0, 2, 0, 2, null,0, TokenType.ERC20));

    user101 = createUser(101, new Balance(BTC, 1000, 2, 0, 2, null,0, TokenType.ERC20), new Balance(USDT, 2000, 2, 0, 2, null,0, TokenType.ERC20),
        new Balance(BTC_USDT_F, 3000, 2, 0, 2, null,0, TokenType.ERC20));

    user102 = createUser(102, new Balance(BTC, -1000, 2, 0, 2, null,0, TokenType.ERC20), new Balance(USDT, -2000, 2, 0, 2, null,0, TokenType.ERC20),
        new Balance(BTC_USDT_F, -3000, 2, 0, 2, null,0, TokenType.ERC20));

    assertBalance(user100, 0, 0, 0);
    assertBalance(user101, 1000, 2000, 3000);
    assertBalance(user102, -1000, -2000, -3000);

    expectMessage("userId=100");
    expectMessage("userId=101");
    expectMessage("userId=102");
    assertMessages();
  }

  private BalanceAdminMessage updateBalance(final User user, final UpdateType updateType, final Balance... balances) {
    BalanceAdminMessage message = new BalanceAdminMessage();
    message.setUpdateType(updateType);
    message.setUserId(user.getId());
    message.setTxType(TX_ADJUSTMENT);

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

  // Create user with zero balance
  @Test
  public void createUserZeroBalance() {
    User user =
        createUser(200, new Balance(BTC, 0, 3, 0, 3, null,0, TokenType.ERC20), new Balance(USDT, 0, 3, 0, 3, null,0, TokenType.ERC20), new Balance(BTC_USDT_F, 0, 3, 0, 3, null,0, TokenType.ERC20));

    assertBalance(user, 0, 0, 0);

    expectMessage("UserAdminMessage", "Balance [assetId=" + BTC + ", balance=.000, balance_change=.000",
        "Balance [assetId=" + USDT + ", balance=.000, balance_change=.000",
        "Balance [assetId=" + BTC_USDT_F + ", balance=.000, balance_change=.000");
    assertMessages();
  }

  // Create user with positive balance
  @Test
  public void createUserPositiveBalance() {
    User user = createUser(200, new Balance(BTC, 10_000, 3, 0, 3, null,0, TokenType.ERC20), new Balance(USDT, 20_000, 3, 0, 3, null,0, TokenType.ERC20),
        new Balance(BTC_USDT_F, 30_000, 3, 0, 3, null,0, TokenType.ERC20));

    assertBalance(user, 1000, 2000, 3000);

    expectMessage("UserAdminMessage", "Balance [assetId=" + BTC + ", balance=10.000, balance_change=.000",
        "Balance [assetId=" + USDT + ", balance=20.000, balance_change=.000",
        "Balance [assetId=" + BTC_USDT_F + ", balance=30.000, balance_change=.000");
    assertMessages();
  }

  // Create user with negative balance
  @Test
  public void createUserNegativeBalance() {
    User user = createUser(200, new Balance(BTC, -10_000, 3, 0, 3, null,0, TokenType.ERC20), new Balance(USDT, -20_000, 3, 0, 3, null,0, TokenType.ERC20),
        new Balance(BTC_USDT_F, -30_000, 3, 0, 3, null,0, TokenType.ERC20));

    assertBalance(user, -1000, -2000, -3000);

    expectMessage("UserAdminMessage", "Balance [assetId=" + BTC + ", balance=-10.000, balance_change=.000",
        "Balance [assetId=" + USDT + ", balance=-20.000, balance_change=.000",
        "Balance [assetId=" + BTC_USDT_F + ", balance=-30.000, balance_change=.000");
    assertMessages();
  }

  // Create user with positive balance change
  @Test
  public void createUserPositiveBalanceChange() {
    User user = createUser(200, new Balance(BTC, 0, 3, 10_000, 3, null,0, TokenType.ERC20), new Balance(USDT, 0, 3, 20_000, 3, null,0, TokenType.ERC20),
        new Balance(BTC_USDT_F, 0, 3, 30_000, 3, null,0, TokenType.ERC20));

    assertBalance(user, 0, 0, 0);

    expectMessage("UserAdminMessage", "Balance [assetId=" + BTC + ", balance=.000, balance_change=10.000",
        "Balance [assetId=" + USDT + ", balance=.000, balance_change=20.000",
        "Balance [assetId=" + BTC_USDT_F + ", balance=.000, balance_change=30.000");
    assertMessages();
  }

  // Create user with negative balance change
  @Test
  public void createUserNegativeBalanceChange() {
    User user = createUser(200, new Balance(BTC, 0, 3, -10_000, 3, null,0, TokenType.ERC20), new Balance(USDT, 0, 3, -20_000, 3, null,0, TokenType.ERC20),
        new Balance(BTC_USDT_F, 0, 3, -30_000, 3, null,0, TokenType.ERC20));

    assertBalance(user, 0, 0, 0);

    expectMessage("UserAdminMessage", "Balance [assetId=" + BTC + ", balance=.000, balance_change=-10.000",
        "Balance [assetId=" + USDT + ", balance=.000, balance_change=-20.000",
        "Balance [assetId=" + BTC_USDT_F + ", balance=.000, balance_change=-30.000");
    assertMessages();
  }

  // Update asset balance - a zero balance to a positive
  @Test
  public void updateZeroBalanceToPositive() {
    updateBalance(user100, UpdateType.PUT, new Balance(BTC, 50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 5000, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PUT, assetId=" + BTC + ", balance=50.00, balance_change=.00");
    assertMessages();
  }

  // Update asset balance - a zero balance to a negative
  @Test
  public void updateZeroBalanceToNegative() {
    updateBalance(user100, UpdateType.PUT, new Balance(BTC, -50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user100, -5000, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PUT, assetId=" + BTC + ", balance=-50.00, balance_change=.00");
    assertMessages();
  }

  // Update asset balance - a positive balance to a higher positive
  @Test
  public void updatePositiveBalanceToHigherPositive() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC, 50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 5000, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC + ", balance=50.00, balance_change=.00");
    assertMessages();
  }

  // Update asset balance - a positive balance to a lower positive
  @Test
  public void updatePositiveBalanceToLowerPositive() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC, 5000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 500, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC + ", balance=5.00, balance_change=.00");
    assertMessages();
  }

  // Update asset balance - a positive balance to zero
  @Test
  public void updatePositiveBalanceToZero() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC, 0, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 0, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC + ", balance=.00, balance_change=.00");
    assertMessages();
  }

  // Update asset balance - a positive balance to a negative
  @Test
  public void updatePositiveBalanceToNegative() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC, -50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user101, -5000, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC + ", balance=-50.00, balance_change=.00");
    assertMessages();
  }

  // Update asset balance - a negative balance to a higher negative
  @Test
  public void updateNegativeBalanceToHigherNegative() {
    updateBalance(user102, UpdateType.PUT, new Balance(USDT, -50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -5000, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + USDT + ", balance=-50.00, balance_change=.00");
    assertMessages();
  }

  // Update asset balance - a negative balance to a lower negative
  @Test
  public void updateNegativeBalanceToLowerNegative() {
    updateBalance(user102, UpdateType.PUT, new Balance(USDT, -5000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -500, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + USDT + ", balance=-5.00, balance_change=.00");
    assertMessages();
  }

  // Update asset balance - a negative balance to zero
  @Test
  public void updateNegativeBalanceToZero() {
    updateBalance(user102, UpdateType.PUT, new Balance(USDT, 0, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, 0, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + USDT + ", balance=.00, balance_change=.00");
    assertMessages();
  }

  // Update asset balance - a negative balance to a positive
  @Test
  public void updateNegativeBalanceToPositive() {
    updateBalance(user102, UpdateType.PUT, new Balance(USDT, 50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, 5000, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + USDT + ", balance=50.00, balance_change=.00");
    assertMessages();
  }

  // Update asset balance - a zero balance to a positive (with the update message having a balance change)
  @Test
  public void updateZeroBalanceToPositive_WithBalanceChange() {
    updateBalance(user100, UpdateType.PUT, new Balance(BTC, 50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 5000, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PUT, assetId=" + BTC + ", balance=50.00, balance_change=10.00");
    assertMessages();
  }

  // Update asset balance - a zero balance to a negative (with the update message having a balance change)
  @Test
  public void updateZeroBalanceToNegative_WithBalanceChange() {
    updateBalance(user100, UpdateType.PUT, new Balance(BTC, -50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, -5000, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PUT, assetId=" + BTC + ", balance=-50.00, balance_change=10.00");
    assertMessages();
  }

  // Update asset balance - a positive balance to a higher positive (with the update message having a balance change)
  @Test
  public void updatePositiveBalanceToHigherPositive_WithBalanceChange() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC, 50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 5000, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC + ", balance=50.00, balance_change=10.00");
    assertMessages();
  }

  // Update asset balance - a positive balance to a lower positive (with the update message having a balance change)
  @Test
  public void updatePositiveBalanceToLowerPositive_WithBalanceChange() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC, 5000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 500, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC + ", balance=5.00, balance_change=10.00");
    assertMessages();
  }

  // Update asset balance - a positive balance to zero (with the update message having a balance change)
  @Test
  public void updatePositiveBalanceToZero_WithBalanceChange() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC, 0, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 0, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC + ", balance=.00, balance_change=10.00");
    assertMessages();
  }

  // Update asset balance - a positive balance to a negative (with the update message having a balance change)
  @Test
  public void updatePositiveBalanceToNegative_WithBalanceChange() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC, -50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, -5000, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC + ", balance=-50.00, balance_change=10.00");
    assertMessages();
  }

  // Update asset balance - a negative balance to a higher negative (with the update message having a balance change)
  @Test
  public void updateNegativeBalanceToHigherNegative_WithBalanceChange() {
    updateBalance(user102, UpdateType.PUT, new Balance(USDT, -50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -5000, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + USDT + ", balance=-50.00, balance_change=10.00");
    assertMessages();
  }

  // Update asset balance - a negative balance to a lower negative (with the update message having a balance change)
  @Test
  public void updateNegativeBalanceToLowerNegative_WithBalanceChange() {
    updateBalance(user102, UpdateType.PUT, new Balance(USDT, -5000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -500, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + USDT + ", balance=-5.00, balance_change=10.00");
    assertMessages();
  }

  // Update asset balance - a negative balance to zero (with the update message having a balance change)
  @Test
  public void updateNegativeBalanceToZero_WithBalanceChange() {
    updateBalance(user102, UpdateType.PUT, new Balance(USDT, 0, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, 0, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + USDT + ", balance=.00, balance_change=10.00");
    assertMessages();
  }

  // Update asset balance - a negative balance to a positive (with the update message having a balance change)
  @Test
  public void updateNegativeBalanceToPositive_WithBalanceChange() {
    updateBalance(user102, UpdateType.PUT, new Balance(USDT, 50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, 5000, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + USDT + ", balance=50.00, balance_change=10.00");
    assertMessages();
  }

  // Update pair balance - a zero balance to a positive
  @Test
  public void updatePairZeroBalanceToPositive() {
    updateBalance(user100, UpdateType.PUT, new Balance(BTC_USDT_F, 50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 0, 0, 5000);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=50.00, balance_change=.00");
    assertMessages();
  }

  // Update pair balance - a zero balance to a negative
  @Test
  public void updatePairZeroBalanceToNegative() {
    updateBalance(user100, UpdateType.PUT, new Balance(BTC_USDT_F, -50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 0, 0, -5000);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=-50.00, balance_change=.00");
    assertMessages();
  }

  // Update pair balance - a positive balance to a higher positive
  @Test
  public void updatePairPositiveBalanceToHigherPositive() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC_USDT_F, 50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 5000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=50.00, balance_change=.00");
    assertMessages();
  }

  // Update pair balance - a positive balance to a lower positive
  @Test
  public void updatePairPositiveBalanceToLowerPositive() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC_USDT_F, 5000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 500);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=5.00, balance_change=.00");
    assertMessages();
  }

  // Update pair balance - a positive balance to zero
  @Test
  public void updatePairPositiveBalanceToZero() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC_USDT_F, 0, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 0);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=.00, balance_change=.00");
    assertMessages();
  }

  // Update pair balance - a positive balance to a negative
  @Test
  public void updatePairPositiveBalanceToNegative() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC_USDT_F, -50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, -5000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=-50.00, balance_change=.00");
    assertMessages();
  }

  // Update pair balance - a negative balance to a higher negative
  @Test
  public void updatePairNegativeBalanceToHigherNegative() {
    updateBalance(user102, UpdateType.PUT, new Balance(BTC_USDT_F, -50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, -5000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=-50.00, balance_change=.00");
    assertMessages();
  }

  // Update pair balance - a negative balance to a lower negative
  @Test
  public void updatePairNegativeBalanceToLowerNegative() {
    updateBalance(user102, UpdateType.PUT, new Balance(BTC_USDT_F, -5000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, -500);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=-5.00, balance_change=.00");
    assertMessages();
  }

  // Update pair balance - a negative balance to zero
  @Test
  public void updatePairNegativeBalanceToZero() {
    updateBalance(user102, UpdateType.PUT, new Balance(BTC_USDT_F, 0, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, 0);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=.00, balance_change=.00");
    assertMessages();
  }

  // Update pair balance - a negative balance to a positive
  @Test
  public void updatePairNegativeBalanceToPositive() {
    updateBalance(user102, UpdateType.PUT, new Balance(BTC_USDT_F, 50000, 3, 0, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, 5000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=50.00, balance_change=.00");
    assertMessages();
  }

  // Update pair balance - a zero balance to a positive (with the update message having a balance change)
  @Test
  public void updatePairZeroBalanceToPositive_WithBalanceChange() {
    updateBalance(user100, UpdateType.PUT, new Balance(BTC_USDT_F, 50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 0, 0, 5000);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=50.00, balance_change=10.00");
    assertMessages();
  }

  // Update pair balance - a zero balance to a negative (with the update message having a balance change)
  @Test
  public void updatePairZeroBalanceToNegative_WithBalanceChange() {
    updateBalance(user100, UpdateType.PUT, new Balance(BTC_USDT_F, -50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 0, 0, -5000);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=-50.00, balance_change=10.00");
    assertMessages();
  }

  // Update pair balance - a positive balance to a higher positive (with the update message having a balance change)
  @Test
  public void updatePairPositiveBalanceToHigherPositive_WithBalanceChange() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC_USDT_F, 50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 5000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=50.00, balance_change=10.00");
    assertMessages();
  }

  // Update pair balance - a positive balance to a lower positive (with the update message having a balance change)
  @Test
  public void updatePairPositiveBalanceToLowerPositive_WithBalanceChange() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC_USDT_F, 5000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 500);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=5.00, balance_change=10.00");
    assertMessages();
  }

  // Update pair balance - a positive balance to zero (with the update message having a balance change)
  @Test
  public void updatePairPositiveBalanceToZero_WithBalanceChange() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC_USDT_F, 0, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 0);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=.00, balance_change=10.00");
    assertMessages();
  }

  // Update pair balance - a positive balance to a negative (with the update message having a balance change)
  @Test
  public void updatePairPositiveBalanceToNegative_WithBalanceChange() {
    updateBalance(user101, UpdateType.PUT, new Balance(BTC_USDT_F, -50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, -5000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=-50.00, balance_change=10.00");
    assertMessages();
  }

  // Update pair balance - a negative balance to a higher negative (with the update message having a balance change)
  @Test
  public void updatePairNegativeBalanceToHigherNegative_WithBalanceChange() {
    updateBalance(user102, UpdateType.PUT, new Balance(BTC_USDT_F, -50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, -5000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=-50.00, balance_change=10.00");
    assertMessages();
  }

  // Update pair balance - a negative balance to a lower negative (with the update message having a balance change)
  @Test
  public void updatePairNegativeBalanceToLowerNegative_WithBalanceChange() {
    updateBalance(user102, UpdateType.PUT, new Balance(BTC_USDT_F, -5000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, -500);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=-5.00, balance_change=10.00");
    assertMessages();
  }

  // Update pair balance - a negative balance to zero (with the update message having a balance change)
  @Test
  public void updatePairNegativeBalanceToZero_WithBalanceChange() {
    updateBalance(user102, UpdateType.PUT, new Balance(BTC_USDT_F, 0, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, 0);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=.00, balance_change=10.00");
    assertMessages();
  }

  // Update pair balance - a negative balance to a positive (with the update message having a balance change)
  @Test
  public void updatePairNegativeBalanceToPositive_WithBalanceChange() {
    updateBalance(user102, UpdateType.PUT, new Balance(BTC_USDT_F, 50000, 3, 10000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, 5000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PUT, assetId=" + BTC_USDT_F + ", balance=50.00, balance_change=10.00");
    assertMessages();
  }

  // Incrementally update asset balance - a zero balance to a positive
  @Test
  public void incrementalUpdateZeroBalanceToPositive() {
    updateBalance(user100, UpdateType.PATCH, new Balance(BTC, 0, 3, 50000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 5000, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=50.00, balance_change=50.00");
    assertMessages();
  }

  // Incrementally update asset balance - a zero balance to a negative
  @Test
  public void incrementalUpdateZeroBalanceToNegative() {
    updateBalance(user100, UpdateType.PATCH, new Balance(BTC, 0, 3, -50000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, -5000, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=-50.00, balance_change=-50.00");
    assertMessages();
  }

  // Incrementally update asset balance - a positive balance to a higher positive
  @Test
  public void incrementalUpdatePositiveBalanceToHigherPositive() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC, 0, 3, 5000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1500, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC + ", balance=15.00, balance_change=5.00");
    assertMessages();
  }

  // Incrementally update asset balance - a positive balance to a lower positive
  @Test
  public void incrementalUpdatePositiveBalanceToLowerPositive() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC, 0, 3, -5000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 500, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC + ", balance=5.00, balance_change=-5.00");
    assertMessages();
  }

  // Incrementally update asset balance - a positive balance to zero
  @Test
  public void incrementalUpdatePositiveBalanceToZero() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC, 0, 3, -10000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 0, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC + ", balance=.00, balance_change=-10.00");
    assertMessages();
  }

  // Incrementally update asset balance - a positive balance to a negative
  @Test
  public void incrementalUpdatePositiveBalanceToNegative() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC, 0, 3, -50000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, -4000, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC + ", balance=-40.00, balance_change=-50.00");
    assertMessages();
  }

  // Incrementally update asset balance - a negative balance to a higher negative
  @Test
  public void incrementalUpdateNegativeBalanceToHigherNegative() {
    updateBalance(user102, UpdateType.PATCH, new Balance(USDT, 0, 3, -5000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2500, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + USDT + ", balance=-25.00, balance_change=-5.00");
    assertMessages();
  }

  // Incrementally update asset balance - a negative balance to a lower negative
  @Test
  public void incrementalUpdateNegativeBalanceToLowerNegative() {
    updateBalance(user102, UpdateType.PATCH, new Balance(USDT, 0, 3, 5000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -1500, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + USDT + ", balance=-15.00, balance_change=5.00");
    assertMessages();
  }

  // Incrementally update asset balance - a negative balance to zero
  @Test
  public void incrementalUpdateNegativeBalanceToZero() {
    updateBalance(user102, UpdateType.PATCH, new Balance(USDT, 0, 3, 20000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, 0, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + USDT + ", balance=.00, balance_change=20.00");
    assertMessages();
  }

  // Incrementally update asset balance - a negative balance to a positive
  @Test
  public void incrementalUpdateNegativeBalanceToPositive() {
    updateBalance(user102, UpdateType.PATCH, new Balance(USDT, 0, 3, 50000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, 3000, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + USDT + ", balance=30.00, balance_change=50.00");
    assertMessages();
  }

  // Incrementally update asset balance - a zero balance to a positive (with the balance update message having a balance)
  @Test
  public void incrementalUpdateZeroBalanceToPositive_WithBalance() {
    updateBalance(user100, UpdateType.PATCH, new Balance(BTC, 25000, 3, 50000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 5000, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=50.00, balance_change=50.00");
    assertMessages();
  }

  // Incrementally update asset balance - a zero balance to a negative (with the balance update message having a balance)
  @Test
  public void incrementalUpdateZeroBalanceToNegative_WithBalance() {
    updateBalance(user100, UpdateType.PATCH, new Balance(BTC, 25000, 3, -50000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, -5000, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=-50.00, balance_change=-50.00");
    assertMessages();
  }

  // Incrementally update asset balance - a positive balance to a higher positive (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePositiveBalanceToHigherPositive_WithBalance() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC, 25000, 3, 5000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1500, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC + ", balance=15.00, balance_change=5.00");
    assertMessages();
  }

  // Incrementally update asset balance - a positive balance to a lower positive (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePositiveBalanceToLowerPositive_WithBalance() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC, 25000, 3, -5000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 500, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC + ", balance=5.00, balance_change=-5.00");
    assertMessages();
  }

  // Incrementally update asset balance - a positive balance to zero (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePositiveBalanceToZero_WithBalance() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC, 25000, 3, -10000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 0, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC + ", balance=.00, balance_change=-10.00");
    assertMessages();
  }

  // Incrementally update asset balance - a positive balance to a negative (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePositiveBalanceToNegative_WithBalance() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC, 25000, 3, -50000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, -4000, 2000, 3000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC + ", balance=-40.00, balance_change=-50.00");
    assertMessages();
  }

  // Incrementally update asset balance - a negative balance to a higher negative (with the balance update message having a balance)
  @Test
  public void incrementalUpdateNegativeBalanceToHigherNegative_WithBalance() {
    updateBalance(user102, UpdateType.PATCH, new Balance(USDT, 25000, 3, -5000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2500, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + USDT + ", balance=-25.00, balance_change=-5.00");
    assertMessages();
  }

  // Incrementally update asset balance - a negative balance to a lower negative (with the balance update message having a balance)
  @Test
  public void incrementalUpdateNegativeBalanceToLowerNegative_WithBalance() {
    updateBalance(user102, UpdateType.PATCH, new Balance(USDT, 25000, 3, 5000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -1500, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + USDT + ", balance=-15.00, balance_change=5.00");
    assertMessages();
  }

  // Incrementally update asset balance - a negative balance to zero (with the balance update message having a balance)
  @Test
  public void incrementalUpdateNegativeBalanceToZero_WithBalance() {
    updateBalance(user102, UpdateType.PATCH, new Balance(USDT, 25000, 3, 20000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, 0, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + USDT + ", balance=.00, balance_change=20.00");
    assertMessages();
  }

  // Incrementally update asset balance - a negative balance to a positive (with the balance update message having a balance)
  @Test
  public void incrementalUpdateNegativeBalanceToPositive_WithBalance() {
    updateBalance(user102, UpdateType.PATCH, new Balance(USDT, 25000, 3, 50000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, 3000, -3000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + USDT + ", balance=30.00, balance_change=50.00");
    assertMessages();
  }

  // Incrementally update pair balance - a zero balance to a positive
  @Test
  public void incrementalUpdatePairZeroBalanceToPositive() {
    updateBalance(user100, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 3, 50000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 0, 0, 5000);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=50.00, balance_change=50.00");
    assertMessages();
  }

  // Incrementally update pair balance - a zero balance to a negative
  @Test
  public void incrementalUpdatePairZeroBalanceToNegative() {
    updateBalance(user100, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 3, -50000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 0, 0, -5000);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-50.00, balance_change=-50.00");
    assertMessages();
  }

  // Incrementally update pair balance - a positive balance to a higher positive
  @Test
  public void incrementalUpdatePairPositiveBalanceToHigherPositive() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 3, 5000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 3500);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=35.00, balance_change=5.00");
    assertMessages();
  }

  // Incrementally update pair balance - a positive balance to a lower positive
  @Test
  public void incrementalUpdatePairPositiveBalanceToLowerPositive() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 3, -5000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 2500);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=25.00, balance_change=-5.00");
    assertMessages();
  }

  // Incrementally update pair balance - a positive balance to zero
  @Test
  public void incrementalUpdatePairPositiveBalanceToZero() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 3, -30000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 0);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=.00, balance_change=-30.00");
    assertMessages();
  }

  // Incrementally update pair balance - a positive balance to a negative
  @Test
  public void incrementalUpdatePairPositiveBalanceToNegative() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 3, -50000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, -2000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-20.00, balance_change=-50.00");
    assertMessages();
  }

  // Incrementally update pair balance - a negative balance to a higher negative
  @Test
  public void incrementalUpdatePairNegativeBalanceToHigherNegative() {
    updateBalance(user102, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 3, -5000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, -3500);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-35.00, balance_change=-5.00");
    assertMessages();
  }

  // Incrementally update pair balance - a negative balance to a lower negative
  @Test
  public void incrementalUpdatePairNegativeBalanceToLowerNegative() {
    updateBalance(user102, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 3, 5000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, -2500);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-25.00, balance_change=5.00");
    assertMessages();
  }

  // Incrementally update pair balance - a negative balance to zero
  @Test
  public void incrementalUpdatePairNegativeBalanceToZero() {
    updateBalance(user102, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 3, 30000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, 0);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=.00, balance_change=30.00");
    assertMessages();
  }

  // Incrementally update pair balance - a negative balance to a positive
  @Test
  public void incrementalUpdatePairNegativeBalanceToPositive() {
    updateBalance(user102, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 3, 50000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, 2000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=20.00, balance_change=50.00");
    assertMessages();
  }

  // Incrementally update pair balance - a zero balance to a positive (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePairZeroBalanceToPositive_WithBalance() {
    updateBalance(user100, UpdateType.PATCH, new Balance(BTC_USDT_F, 10000, 3, 50000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 0, 0, 5000);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=50.00, balance_change=50.00");
    assertMessages();
  }

  // Incrementally update pair balance - a zero balance to a negative (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePairZeroBalanceToNegative_WithBalance() {
    updateBalance(user100, UpdateType.PATCH, new Balance(BTC_USDT_F, 10000, 3, -50000, 3, null,0, TokenType.ERC20));
    assertBalance(user100, 0, 0, -5000);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-50.00, balance_change=-50.00");
    assertMessages();
  }

  // Incrementally update pair balance - a positive balance to a higher positive (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePairPositiveBalanceToHigherPositive_WithBalance() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC_USDT_F, 10000, 3, 5000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 3500);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=35.00, balance_change=5.00");
    assertMessages();
  }

  // Incrementally update pair balance - a positive balance to a lower positive (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePairPositiveBalanceToLowerPositive_WithBalance() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC_USDT_F, 10000, 3, -5000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 2500);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=25.00, balance_change=-5.00");
    assertMessages();
  }

  // Incrementally update pair balance - a positive balance to zero (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePairPositiveBalanceToZero_WithBalance() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC_USDT_F, 10000, 3, -30000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, 0);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=.00, balance_change=-30.00");
    assertMessages();
  }

  // Incrementally update pair balance - a positive balance to a negative (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePairPositiveBalanceToNegative_WithBalance() {
    updateBalance(user101, UpdateType.PATCH, new Balance(BTC_USDT_F, 10000, 3, -50000, 3, null,0, TokenType.ERC20));
    assertBalance(user101, 1000, 2000, -2000);

    expectMessage("BalanceAdminMessage", "userId=101, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-20.00, balance_change=-50.00");
    assertMessages();
  }

  // Incrementally update pair balance - a negative balance to a higher negative (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePairNegativeBalanceToHigherNegative_WithBalance() {
    updateBalance(user102, UpdateType.PATCH, new Balance(BTC_USDT_F, 10000, 3, -5000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, -3500);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-35.00, balance_change=-5.00");
    assertMessages();
  }

  // Incrementally update pair balance - a negative balance to a lower negative (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePairNegativeBalanceToLowerNegative_WithBalance() {
    updateBalance(user102, UpdateType.PATCH, new Balance(BTC_USDT_F, 10000, 3, 5000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, -2500);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=-25.00, balance_change=5.00");
    assertMessages();
  }

  // Incrementally update pair balance - a negative balance to zero (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePairNegativeBalanceToZero_WithBalance() {
    updateBalance(user102, UpdateType.PATCH, new Balance(BTC_USDT_F, 10000, 3, 30000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, 0);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=.00, balance_change=30.00");
    assertMessages();
  }

  // Incrementally update pair balance - a negative balance to a positive (with the balance update message having a balance)
  @Test
  public void incrementalUpdatePairNegativeBalanceToPositive_WithBalance() {
    updateBalance(user102, UpdateType.PATCH, new Balance(BTC_USDT_F, 10000, 3, 50000, 3, null,0, TokenType.ERC20));
    assertBalance(user102, -1000, -2000, 2000);

    expectMessage("BalanceAdminMessage", "userId=102, updateType=PATCH, assetId=" + BTC_USDT_F + ", balance=20.00, balance_change=50.00");
    assertMessages();
  }
}
