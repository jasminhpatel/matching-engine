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
 * The same over-withdrawal cases as BalanceWithdrawalSpotLimitsDisabledTest, but with
 * ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS on - which is its default, so this is the production path.
 *
 * These tests assert the behaviour the engine SHOULD have, and currently FAIL. The class is listed in
 * .github/quarantined-tests.txt for that reason; delete its line there once the engine is fixed.
 *
 * What should happen when a withdrawal asks for more than the available balance:
 *
 *   - the position is left untouched                          (already true today)
 *   - the published message is marked rejected, txType=4024   (TX_ADMIN_WITHDRAW_REJECTED)
 *   - balance_change is 0, because nothing was applied
 *   - balance reports the real position, not the request
 *
 * That is not invented for this test. It is exactly what the sibling ENABLE_BALANCE_WITHDRAW_EXACT_LIMITS branch
 * already does on the same condition (User.java:574-583): it zeroes the change, sets TX_ADMIN_WITHDRAW_REJECTED, then
 * refreshes both fields from the position.
 *
 * What actually happens: the branch added ahead of the capping branch in User.updateIncrementInstrument is
 * all-or-nothing and returns without calling Balance.setBalance or Balance.setBalanceChange. UserCache.addBalance then
 * publishes unconditionally, so the message keeps the caller's values and is never marked rejected. The commented-out
 * expectation under each test records that current output, so the gap is visible side by side.
 *
 * requestStatus is deliberately not asserted. UserCache.addBalance sets it to SUCCESS unconditionally and the existing
 * EXACT_LIMITS rejection path does not change that, so whether a rejected withdrawal should also report
 * RequestStatus.FAIL is a separate decision this test does not pre-empt.
 *
 * Separate top level class rather than nested, because the flag is a static final read once per JVM and surefire forks
 * per class. See BalanceWithdrawalTestBase.
 */
public class BalanceWithdrawalSpotLimitsEnabledTest extends BalanceWithdrawalTestBase {

  @Override
  protected void configureProperties(final Properties properties) {
    properties.setProperty("ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS", "TRUE");
  }

  // Withdraw asset balance - positive to negative is rejected, balance left untouched at 1.00
  @Test
  public void withdrawBTC_PositiveToNegative() {
    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -150, 2, null,0, TokenType.ERC20));
    assertBalance(user, 1_00, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, txType=4024, assetId=" + BTC
        + ", balance=1.00, balance_change=0.00");
    // Current behaviour, for reference: not marked rejected, and both fields echoed back from the request.
    // expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, txType=4012, requestStatus=SUCCESS, assetId=" + BTC
    //     + ", balance=0.00, balance_change=-1.50");
    assertMessages();
  }

  // Withdraw asset balance with open orders - positive to negative is rejected, balance left untouched at 1.00
  @Test
  public void withdrawBTC_WithOpenOrders_PositiveToNegative() {
    InstrumentCache.getPair(BTC_USDT_F).getOrderBook().addOrder(createOrder(1, user, BTC_USDT_F, 4950_00, 10_00, Side.BUY, DAY));
    expectMessage("ExecutionReport", "orderId=1, ordType=LIMIT, ordStatus=NEW");

    updateBalance(user, UpdateType.PATCH, new Balance(BTC, 0, 2, -150, 2, null,0, TokenType.ERC20));
    assertBalance(user, 1_00, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, txType=4024, assetId=" + BTC
        + ", balance=1.00, balance_change=0.00");
    // Current behaviour, for reference: not marked rejected, and both fields echoed back from the request.
    // expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, txType=4012, requestStatus=SUCCESS, assetId=" + BTC
    //     + ", balance=0.00, balance_change=-1.50");
    assertMessages();
  }

  // Withdraw quote asset balance - positive to negative is rejected, balance left untouched at 10000.00
  @Test
  public void withdrawUSDT_PositiveToNegative() {
    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -15000_00, 2, null,0, TokenType.ERC20));
    assertBalance(user, 1_00, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, txType=4024, assetId=" + USDT
        + ", balance=10000.00, balance_change=0.00");
    // Current behaviour, for reference: not marked rejected, and both fields echoed back from the request.
    // expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, txType=4012, requestStatus=SUCCESS, assetId=" + USDT
    //     + ", balance=0.00, balance_change=-15000.00");
    assertMessages();
  }
}
