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
 * Here an over-withdrawal is refused rather than capped. The branch at User.updateIncrementInstrument is
 * all-or-nothing: a request for more than the available balance leaves the position untouched and stamps the outbound
 * message with TX_ADMIN_WITHDRAW_REJECTED (4024).
 *
 * Note what the balance fields mean on a refusal. They are the request relayed back, not engine state: the caller sent
 * balance=0.00 with the movement in balance_change, and both are echoed unchanged because nothing was applied. The
 * rejection is carried by txType alone, so a consumer correlates the message against the request it sent and reads 4024
 * as "this one was refused". That is why balance_change is still the requested amount here, whereas the capping branch
 * in BalanceWithdrawalSpotLimitsDisabledTest reports the amount it actually applied.
 *
 * requestStatus is deliberately not asserted. UserCache.addBalance sets it to SUCCESS unconditionally
 * (user/UserCache.java:374) and the rejection path does not change that, so whether a refused withdrawal should also
 * report RequestStatus.FAIL is a separate decision this test does not pre-empt.
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
        + ", balance=0.00, balance_change=-1.50");
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
        + ", balance=0.00, balance_change=-1.50");
    assertMessages();
  }

  // Withdraw quote asset balance - positive to negative is rejected, balance left untouched at 10000.00
  @Test
  public void withdrawUSDT_PositiveToNegative() {
    updateBalance(user, UpdateType.PATCH, new Balance(USDT, 0, 2, -15000_00, 2, null,0, TokenType.ERC20));
    assertBalance(user, 1_00, 10000_00, 0);

    expectMessage("BalanceAdminMessage", "userId=100, updateType=PATCH, txType=4024, assetId=" + USDT
        + ", balance=0.00, balance_change=-15000.00");
    assertMessages();
  }
}
