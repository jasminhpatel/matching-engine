package com.solfini.matchengine.model.risk;

import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

/**
 * Withdrawal tests that run with the default withdrawal flags.
 *
 * The over-withdrawal cases live in BalanceWithdrawalSpotLimitsDisabledTest, which needs
 * ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS off and therefore needs a JVM of its own. The fixture is shared via
 * BalanceWithdrawalTestBase.
 */
public class BalanceWithdrawalTest extends BalanceWithdrawalTestBase {

  // Withdraw asset balance - positive to positive
  @Test
  public void withdrawBTC_PositiveToPositive() {
    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -50, 2, null,0, TokenType.ERC20));
    assertBalance(user, 50, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=0.50, balance_change=-0.50");
    assertMessages();
  }

  // Withdraw asset balance - positive to zero
  @Test
  public void withdrawBTC_PositiveToZero() {
    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -100, 2, null,0, TokenType.ERC20));
    assertBalance(user, 0, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=0.00, balance_change=-1.00");
    assertMessages();
  }

  // Withdraw asset balance with open orders - positive to positive
  @Test
  public void withdrawBTC_WithOpenOrders_PositiveToPositive() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 10_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -50, 2, null,0, TokenType.ERC20));
    assertBalance(user, 50, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=0.50, balance_change=-0.50");
    assertMessages();
  }

  // Withdraw asset balance with open orders - positive to zero
  @Test
  public void withdrawBTC_WithOpenOrders_PositiveToZero() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 10_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -100, 2, null,0, TokenType.ERC20));
    assertBalance(user, 0, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=0.00, balance_change=-1.00");
    assertMessages();
  }

  // Withdraw quote asset balance - positive to positive
  @Test
  public void withdrawUSDT_PositiveToPositive() {
    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -5000_00, 2, null,0, TokenType.ERC20));
    assertBalance(user, 1_00, 5000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=5000.00, balance_change=-5000.00");
    assertMessages();
  }

  // Withdraw quote asset balance - positive to zero
  @Test
  public void withdrawUSDT_PositiveToZero() {
    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -10000_00, 2, null,0, TokenType.ERC20));
    assertBalance(user, 1_00, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=0.00, balance_change=-10000.00");
    assertMessages();
  }

  // Withdraw quote asset balance with open orders - below withdrawal limit
  @Test
  public void withdrawUSDT_WithOpenOrders_BelowWithdrawalLimit() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 10_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -1000_00, 2, null,0, TokenType.ERC20));
    assertBalance(user, 1_00, 9000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=9000.00, balance_change=-1000.00");
    assertMessages();
  }

  // Withdraw quote asset balance with open orders - same as withdrawal limit
  @Test
  public void withdrawUSDT_WithOpenOrders_EqualToWithdrawalLimit() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 50_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -2625_00, 2, null,0, TokenType.ERC20));
    assertBalance(user, 1_00, 7375_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=7375.00, balance_change=-2625.00");
    assertMessages();
  }

  // Withdraw quote asset balance with open orders - above withdrawal limit
  @Test
  public void withdrawUSDT_WithOpenOrders_AboveWithdrawalLimit() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 50_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -9000_00, 2, null,0, TokenType.ERC20));
    assertBalance(user, 1_00, 7375_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=7375.00, balance_change=-2625.00");
    assertMessages();
  }
}
