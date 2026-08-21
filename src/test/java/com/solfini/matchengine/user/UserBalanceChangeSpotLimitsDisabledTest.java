package com.solfini.matchengine.user;

import static org.junit.Assert.assertEquals;
import java.util.Properties;

import com.solfini.internal.admin.schema.TokenType;
import org.junit.Test;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.user.User;

/**
 * Withdrawal limits with ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS off, so the capping branch decides
 * (Context.isEnableBalanceWithdrawLimits, User.java:585).
 *
 * Here a withdrawal larger than the balance is reduced to what is available rather than refused, so the position lands
 * on zero. A later withdrawal is reduced again, this time to the free margin rather than to the raw balance.
 *
 * Note the fixture sets usdMarginableValue as well as usdValue. The capping branch gates on
 * `usdMarginableValue > usdMarginRequiredValue` and then limits to `usdMarginableValue - usdMarginRequiredValue`, so
 * with usdMarginableValue left at its default of 0 the gate is false, the branch caps to zero and applies nothing -
 * i.e. the capping this class exists to cover never happens. usdValue alone is not read by that branch.
 *
 * Separate top level class rather than nested, because the flag is a static final read once per JVM and surefire forks
 * per class. See UserBalanceChangeTestBase.
 */
public class UserBalanceChangeSpotLimitsDisabledTest extends UserBalanceChangeTestBase {

  @Override
  protected void configureProperties(final Properties properties) {
    properties.setProperty("ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS", "FALSE");
  }

  @Test
  public void balanceAdminTestWithdrawLimit() {
    createInstruments();

    final User user = createUser(18);
    Position position1 = user.addPosition(1, 10_000_000, null, 0, TokenType.ERC20);
    user.addPosition(14, 1_000_000, null, 0, TokenType.ERC20);
    user.setActive(true);
    user.setUsdValue(1000);
    user.setUsdMarginableValue(1000);

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

    // only allow to withdraw 900, the difference between usdMarginableValue of 1000 minus UsdMarginRequiredValue of 100
    assertEquals(1100_000000, position1.getQuantity());

    // Capping is not a refusal, so nothing stamps a rejection code.
    assertEquals(TX_DEPOSIT, balanceAdminMessage.getTxType());
  }
}
