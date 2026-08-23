package com.solfini.matchengine.user;

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.junit.Test;

import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.Sector;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.user.User;
import com.solfini.user.UserCache;

/**
 * The withdraw fee is charged only when the withdrawal is actually applied.
 *
 * A withdrawal arrives as API_TX_WITHDRAW. The fee is taken from the withdrawing user and credited to the exchange user,
 * and the amount and the asset it is denominated in both come from the withdrawn instrument's own configuration
 * (getWithdrawFee / getWithdrawFeeInstrument). Whether the withdrawal is applied at all is decided further down
 * User.updateIncrementInstrument by the guard selected by ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS, which is on here because
 * that is its default and therefore the production path.
 *
 * These tests exist because the fee had no coverage whatsoever, which is how it came to be charged on withdrawals the
 * engine had refused. All three run with the fee mechanism switched on, so the "not charged" assertion in
 * feeIsNotChargedWhenTheWithdrawalIsRefused is meaningful rather than vacuous: its sibling proves the same fixture does
 * charge a fee when the withdrawal succeeds.
 *
 * Separate top level class rather than nested, because the withdraw-limit flags are static final read once per JVM and
 * surefire forks per class. See UserBalanceChangeTestBase.
 */
public class WithdrawFeeTest extends UserBalanceChangeTestBase {

  private static final int WITHDRAWN_INSTRUMENT = 1;
  private static final int SEPARATE_FEE_INSTRUMENT = 2;
  private static final short QUANTITY_SCALE = 6;

  /** Two units of the fee asset, in that asset's own scale. */
  private static final double WITHDRAW_FEE = 2d;
  private static final long FEE_IN_SCALE = 2_000_000L;

  /** One unit of either asset, in QUANTITY_SCALE. */
  private static final long ONE_UNIT = 1_000_000L;

  // A user id per test. UserCache is static and a PUT for an existing id keeps that user's positions, so two tests
  // sharing an id would have the second one start from the first one's closing balances. The sibling
  // UserBalanceChangeSpotLimits*Test classes take the same approach.
  private static final int APPLIED_USER_ID = 21;
  private static final int APPLIED_EXCHANGE_USER_ID = 22;
  private static final int REFUSED_USER_ID = 23;
  private static final int REFUSED_EXCHANGE_USER_ID = 24;
  private static final int SAME_ASSET_USER_ID = 25;
  private static final int SAME_ASSET_EXCHANGE_USER_ID = 26;
  private static final int FULL_BALANCE_USER_ID = 27;
  private static final int FULL_BALANCE_EXCHANGE_USER_ID = 28;

  @Override
  protected void configureProperties(final Properties properties) {
    properties.setProperty("ENABLE_BALANCE_WITHDRAW_SPOT_LIMITS", "TRUE");
  }

  /**
   * Defines the withdrawn instrument with a non-zero fee payable in feeInstrumentId, plus that fee instrument itself
   * when it is a different asset. InstrumentCache is static and never cleared, so each test defines what it needs at the
   * start rather than relying on a shared fixture.
   */
  private void createInstruments(final int feeInstrumentId) {
    final Instrument withdrawn = new Instrument(WITHDRAWN_INSTRUMENT, "USDT", "USDT", (short) 2, QUANTITY_SCALE, 1, 1000,
        WITHDRAW_FEE, false, feeInstrumentId, Sector.NOT_DEFINED);
    withdrawn.setIndexFeedUsdMark(1);
    InstrumentCache.addInstrument(withdrawn);

    if (feeInstrumentId != WITHDRAWN_INSTRUMENT) {
      final Instrument fee = new Instrument(feeInstrumentId, "USDC", "USDC", (short) 2, QUANTITY_SCALE, 1, 1000, 0, false,
          feeInstrumentId, Sector.NOT_DEFINED);
      fee.setIndexFeedUsdMark(1);
      InstrumentCache.addInstrument(fee);
    }
  }

  /**
   * A user holding 1000 units of the withdrawn asset and 50 of the fee asset, and a separate exchange user for the fee
   * to be credited to. Registering the exchange user explicitly matters: UserCache defaults it to a placeholder User(0),
   * so without this the fee would land on that placeholder and the credit side could not be asserted.
   */
  private User newFundedUser(final int feeInstrumentId, final int userId, final int exchangeUserId) {
    createInstruments(feeInstrumentId);

    final User exchangeUser = createUser(exchangeUserId);
    exchangeUser.setActive(true);
    UserCache.setExchangeUser(exchangeUser);

    final User user = createUser(userId);
    user.addPosition(WITHDRAWN_INSTRUMENT, 1000 * ONE_UNIT, null, 0, TokenType.ERC20);
    if (feeInstrumentId != WITHDRAWN_INSTRUMENT) {
      user.addPosition(feeInstrumentId, 50 * ONE_UNIT, null, 0, TokenType.ERC20);
    }
    user.setActive(true);
    user.setUsdValue(1000);
    user.setUsdMarginableValue(1000);
    return user;
  }

  /** A withdrawal of unitsToWithdraw units of the withdrawn asset, as the API server sends it. */
  private BalanceAdminMessage withdrawMessage(final User user, final long unitsToWithdraw) {
    final BalanceAdminMessage balanceAdminMessage = new BalanceAdminMessage();
    balanceAdminMessage.setUserId(user.getId());
    balanceAdminMessage.setUser(user);
    balanceAdminMessage.setUpdateType(UpdateType.PUT);
    balanceAdminMessage.setTxType(API_TX_WITHDRAW);

    final Balance balance = new Balance();
    balance.setAssetId(WITHDRAWN_INSTRUMENT);
    // Scale 2 is what the API server sends; updateIncrementInstrument rescales to the instrument's scale.
    balance.setBalanceChange(-unitsToWithdraw * 100, 2);
    balanceAdminMessage.getBalanceList().add(balance);
    return balanceAdminMessage;
  }

  // A withdrawal the engine applies is charged its fee, debited from the user and credited to the exchange user.
  @Test
  public void feeIsChargedWhenTheWithdrawalIsApplied() {
    final User user = newFundedUser(SEPARATE_FEE_INSTRUMENT, APPLIED_USER_ID, APPLIED_EXCHANGE_USER_ID);
    final User exchangeUser = UserCache.getExchangeUser();

    user.updateIncrement(withdrawMessage(user, 100));

    assertEquals("withdrawal applied in full", 900 * ONE_UNIT, user.getPosition(WITHDRAWN_INSTRUMENT).getQuantity());
    assertEquals("fee debited from the user", 50 * ONE_UNIT - FEE_IN_SCALE, user.getPosition(SEPARATE_FEE_INSTRUMENT).getQuantity());
    assertEquals("fee credited to the exchange user", FEE_IN_SCALE, exchangeUser.getPosition(SEPARATE_FEE_INSTRUMENT).getQuantity());
  }

  // A withdrawal the engine refuses is not charged a fee. This is the case the fix is for.
  @Test
  public void feeIsNotChargedWhenTheWithdrawalIsRefused() {
    final User user = newFundedUser(SEPARATE_FEE_INSTRUMENT, REFUSED_USER_ID, REFUSED_EXCHANGE_USER_ID);
    final User exchangeUser = UserCache.getExchangeUser();

    // 5000 against a balance of 1000, so the spot guard refuses it outright.
    final BalanceAdminMessage balanceAdminMessage = withdrawMessage(user, 5000);
    user.updateIncrement(balanceAdminMessage);

    assertEquals("refusal is visible on the message", TX_ADMIN_WITHDRAW_REJECTED, balanceAdminMessage.getTxType());
    assertEquals("withdrawn position untouched", 1000 * ONE_UNIT, user.getPosition(WITHDRAWN_INSTRUMENT).getQuantity());
    assertEquals("no fee debited from the user", 50 * ONE_UNIT, user.getPosition(SEPARATE_FEE_INSTRUMENT).getQuantity());
    assertEquals("no fee credited to the exchange user", 0L, exchangeUser.getPosition(SEPARATE_FEE_INSTRUMENT).getQuantity());
  }

  /**
   * When the fee is payable in the asset being withdrawn, both movements land on the one position and the fee is taken
   * after the withdrawal rather than reserved ahead of it.
   *
   * This is worth pinning down because it is the one behavioural change beyond "no fee on a refusal". Previously the fee
   * was debited before the guard read the available balance, so it was implicitly reserved and a request for the entire
   * balance refused itself. The guard now sees the un-reduced balance. Nothing here can overdraw the customer, because
   * the API server rejects a withdrawal whose amount plus fee exceeds the available balance before publishing it.
   */
  @Test
  public void sameAssetFeeIsChargedAfterTheWithdrawal() {
    final User user = newFundedUser(WITHDRAWN_INSTRUMENT, SAME_ASSET_USER_ID, SAME_ASSET_EXCHANGE_USER_ID);
    final User exchangeUser = UserCache.getExchangeUser();

    user.updateIncrement(withdrawMessage(user, 100));

    assertEquals("withdrawal and fee both applied to the one position", 900 * ONE_UNIT - FEE_IN_SCALE,
        user.getPosition(WITHDRAWN_INSTRUMENT).getQuantity());
    assertEquals("fee credited to the exchange user", FEE_IN_SCALE, exchangeUser.getPosition(WITHDRAWN_INSTRUMENT).getQuantity());
  }

  /**
   * Withdrawing the entire available balance, with the fee payable in that same asset, now goes through and leaves the
   * position short by the fee.
   *
   * This is a deliberate characterisation test rather than a statement that the outcome is desirable. Before the fee
   * moved, it was debited ahead of the guard, so the guard saw a balance already reduced by the fee and refused the
   * request; the fee was then charged anyway on the refusal, which is the bug this change fixes. Now the guard sees the
   * real balance and accepts, and the fee follows.
   *
   * The engine is not the control for this. The API server rejects a withdrawal whose amount plus fee exceeds the
   * available balance before it publishes anything, for both the same-asset and separate-asset fee cases, so this input
   * does not arise in production. If we ever decide the engine should reserve the fee itself, this test is the one that
   * will fail and force the discussion.
   */
  @Test
  public void fullBalanceWithdrawalIsAppliedAndTheSameAssetFeeFollowsIt() {
    final User user = newFundedUser(WITHDRAWN_INSTRUMENT, FULL_BALANCE_USER_ID, FULL_BALANCE_EXCHANGE_USER_ID);

    final BalanceAdminMessage balanceAdminMessage = withdrawMessage(user, 1000);
    user.updateIncrement(balanceAdminMessage);

    assertEquals("not refused", TX_WITHDRAW, balanceAdminMessage.getTxType());
    assertEquals("short by the fee", -FEE_IN_SCALE, user.getPosition(WITHDRAWN_INSTRUMENT).getQuantity());
  }
}
