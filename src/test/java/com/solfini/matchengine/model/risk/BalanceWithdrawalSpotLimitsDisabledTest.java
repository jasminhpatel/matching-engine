package com.solfini.matchengine.model.risk;

import java.util.Properties;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

/**
 * The over-withdrawal cases, which require ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS to be off.
 *
 * These assert the capping behaviour: a withdrawal larger than the available balance is reduced to what is there,
 * leaving the balance at zero and reporting the amount actually applied. That is what
 * Context.isEnableBalanceWithdrawLimits() does.
 *
 * With ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS on - its default - a branch added ahead of it in
 * User.updateIncrementInstrument takes precedence for spot assets and is all-or-nothing: an over-withdrawal is dropped
 * without capping, without touching the position and without marking the message rejected, while the published
 * BalanceAdminMessage still reports requestStatus=SUCCESS and the full requested balance_change. These tests therefore
 * pin the capping contract by turning that branch off.
 *
 * This is a separate top level class, not a nested class, because the flag is a static final read once per JVM and
 * surefire forks per class. See BalanceWithdrawalTestBase for the detail.
 */
public class BalanceWithdrawalSpotLimitsDisabledTest extends BalanceWithdrawalTestBase {

  @Override
  protected void configureProperties(final Properties properties) {
    properties.setProperty("ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS", "FALSE");
  }

  // Withdraw asset balance - positive to negative (limits at zero)
  @Test
  public void withdrawBTC_PositiveToNegative() {
    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -150, 2, null,0, TokenType.ERC20));
    assertBalance(user, 0, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=0.00, balance_change=-1.00");
    assertMessages();
  }

  // Withdraw asset balance with open orders - positive to negative (limits at zero)
  @Test
  public void withdrawBTC_WithOpenOrders_PositiveToNegative() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 10_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -150, 2, null,0, TokenType.ERC20));
    assertBalance(user, 0, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + BTC + ", balance=0.00, balance_change=-1.00");
    assertMessages();
  }

  // Withdraw quote asset balance - positive to negative (limits at zero)
  @Test
  public void withdrawUSDT_PositiveToNegative() {
    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -15000_00, 2, null,0, TokenType.ERC20));
    assertBalance(user, 1_00, 0, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, assetId=" + USDT + ", balance=0.00, balance_change=-10000.00");
    assertMessages();
  }
}
