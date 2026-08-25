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
 * The same withdrawal limits with ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS on - its default, so this is the production path
 * and the spot guard branch decides (User.java:547).
 *
 * That branch is all-or-nothing: a withdrawal larger than the available balance is refused outright rather than reduced,
 * the position is left exactly as it was, and the message is stamped TX_ADMIN_WITHDRAW_REJECTED (4024). A withdrawal
 * that fits is applied in full.
 *
 * The two cases are separate methods on purpose. The refusal mutates txType on the message, and a message whose txType
 * is 4024 no longer satisfies the guard predicate (`getTxType() <= TX_ADMIN_WITHDRAW`), so reusing one message across a
 * refusal and a later withdrawal would send the second one down the unguarded branch and test message reuse rather than
 * the withdrawal contract. Each method builds its own message, which is what the engine sees in production.
 *
 * Separate top level class rather than nested, because the flag is a static final read once per JVM and surefire forks
 * per class. See UserBalanceChangeTestBase.
 */
public class UserBalanceChangeSpotLimitsEnabledTest extends UserBalanceChangeTestBase {

  @Override
  protected void configureProperties(final Properties properties) {
    properties.setProperty("ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS", "TRUE");
  }

  private User newFundedUser(final int userId) {
    createInstruments();

    final User user = createUser(userId);
    user.addPosition(1, 10_000_000, null, 0, TokenType.ERC20);
    user.addPosition(14, 1_000_000, null, 0, TokenType.ERC20);
    user.setActive(true);
    user.setUsdValue(1000);
    user.setUsdMarginableValue(1000);
    return user;
  }

  private BalanceAdminMessage depositMessage(final User user) {
    BalanceAdminMessage balanceAdminMessage = new BalanceAdminMessage();
    balanceAdminMessage.setUserId(user.getId());
    balanceAdminMessage.setUser(user);
    balanceAdminMessage.setUpdateType(UpdateType.PUT);
    balanceAdminMessage.setTxType(TX_DEPOSIT);

    Balance balance = new Balance();
    balance.setAssetId(1);
    balanceAdminMessage.getBalanceList().add(balance);
    return balanceAdminMessage;
  }

  // A withdrawal that would drive the balance negative is refused, leaving the position untouched.
  @Test
  public void overWithdrawIsRejected() {
    final User user = newFundedUser(18);
    final Position position1 = user.getPositionArr()[1];

    BalanceAdminMessage balanceAdminMessage = depositMessage(user);
    Balance balance = balanceAdminMessage.getBalanceList().get(0);

    balance.setBalanceChange(2000_00, 2);
    user.updateIncrement(balanceAdminMessage);
    assertEquals(2010_000000, position1.getQuantity());

    // 3000 against a balance of 2010: refused outright rather than capped at zero.
    balance.setBalanceChange(-3000_00, 2);
    user.updateIncrement(balanceAdminMessage);

    assertEquals(2010_000000, position1.getQuantity());
    assertEquals(TX_ADMIN_WITHDRAW_REJECTED, balanceAdminMessage.getTxType());
  }

  // A withdrawal that fits inside the balance is applied in full and is not stamped as rejected.
  @Test
  public void withdrawWithinBalanceIsApplied() {
    final User user = createUser(19);
    createInstruments();
    final Position position1 = user.addPosition(1, 10_000_000, null, 0, TokenType.ERC20);
    user.setActive(true);
    user.setUsdValue(1000);
    user.setUsdMarginableValue(1000);

    BalanceAdminMessage balanceAdminMessage = depositMessage(user);
    Balance balance = balanceAdminMessage.getBalanceList().get(0);

    balance.setBalanceChange(2000_00, 2);
    user.updateIncrement(balanceAdminMessage);
    assertEquals(2010_000000, position1.getQuantity());

    balance.setBalanceChange(-1000_00, 2);
    user.updateIncrement(balanceAdminMessage);

    assertEquals(1010_000000, position1.getQuantity());
    assertEquals(TX_DEPOSIT, balanceAdminMessage.getTxType());
  }
}
