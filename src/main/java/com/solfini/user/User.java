package com.solfini.user;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import com.solfini.common.Appendable;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.AssetGroupCache;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.matchengine.message.session.LogonMessage;
import com.solfini.pool.BalanceAdminMessageObjectPool;
import com.solfini.pool.PositionMatchThreadObjectPool;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;
import uk.co.real_logic.artio.fields.DecimalFloat;
import static com.solfini.instrument.Position.assetIdComparator;

/**
 *
 * @author Chris Mack
 *
 */
public class User implements Appendable, Serializable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(User.class);
  private static final int SETTLE_INSTRUMENT_QUANTITY_SCALE_MULT = InstrumentCache.getSettleInstrumentQuantityScaleMult();

  public static final int USER_STATUS_RESET_AUTO_LIQUIDATION_STATE = 0x0100;

  // user types
  public static final int TRADER = 0;
  public static final int ADMIN = 1;
  public static final int EXCHANGE = 2;
  public static final int INSURANCE_FUND = 3;
  public static final int MARKET_MAKER = 4;
  public static final int TEST = 5;
  public static final int OTHER = 6;

  private final int id;
  private String login;
  private String password;
  private int firmId;
  private int feeTier;
  private int feeTierOrig;
  private boolean lmm;
  private boolean useDiscountFeesCoin;
  private int verification;
  private int openOrderCount;
  private long externalId;
  private int userType;
  private boolean isActive;
  private int maxActivePositionIndexHint; // index of highest position the user has, used as a hint when iterating
  private int maxTouchedPositionsCount; // count of actual total positions user had
  private int marginCurveIdOverride; // use a different margin curve for this user

  // user risk checks
  private double usdValue;
  private double usdMarginableValue;
  private double usdNotionalPositionValue;
  private double usdMaxExposurePositionAndOpenOrdersValue;
  private double usdOpenOrdersRequiredValue;
  private double usdMarginValue;
  private double usdMarginRequiredValue;
  private double usdMarginMaintValue;
  private double leverageRatio;
  private double usdUnrealized;
  private double marginRatio;
  private double usdCollateralValue;
  private double usdCollateralValueDiscounted;

  private final AtomicInteger autoLiquidationState;
  private final AtomicInteger autoLiquidationCounter;
  private final AtomicInteger collateralSwapState;
  private volatile long lastLiquidationTime = 0;

  private Position[] positionArr;

  private PositionReportMessage prevPositionReport; // used for reporting

  public User(final int userId) {
    this.id = userId;
    this.autoLiquidationState = new AtomicInteger();
    this.autoLiquidationCounter = new AtomicInteger();
    this.collateralSwapState = new AtomicInteger();
    this.positionArr = new Position[Math.max(InstrumentCache.getInstrumentCapacity(), InstrumentCache.getPairCapacity())];
  }

  // must be called from the matching engine thread
  public void setUser(final UserAdminMessage userAdminMessage) {
    this.isActive = true;
    override(userAdminMessage);
  }

  // must be called from the matching engine thread
  public void setUser(final BalanceAdminMessage balanceAdminMessage) {
    this.isActive = true;
    this.firmId = balanceAdminMessage.getFirmId();
    override(balanceAdminMessage);
  }

  public final BalanceAdminMessage buildBalanceAdminMessage() {
    final BalanceAdminMessage balanceAdminMessage = BalanceAdminMessageObjectPool.get();
    balanceAdminMessage.setUpdateType(UpdateType.PUT);
    balanceAdminMessage.setUserId(id);
    balanceAdminMessage.setUser(this);
    balanceAdminMessage.setFirmId(firmId);
    balanceAdminMessage.setFeeTier(feeTierOrig); // feeTier
    balanceAdminMessage.setRequestStatus(RequestStatus.SUCCESS);
    balanceAdminMessage.setExternalId(externalId);
    balanceAdminMessage.setUserType(userType);

    final List<Balance> balanceList = balanceAdminMessage.getBalanceList();
    balanceList.clear();
    for (final Position position : positionArr) { // TODO: limit loop using user.maxActivePositionIndexHint
      if (position != null) {
        final Instrument instrument = InstrumentCache.get(position.getInstrumentId());
        int scale = instrument != null ? instrument.getQuantityScale() : 0;
        if (instrument == null) {
          final InstrumentPair pair = InstrumentCache.getPair(position.getInstrumentId());
          scale = pair != null ? pair.getQuantityScale() : 0;
        }
        final Balance balance = balanceAdminMessage.getCachedBalance();
        balance.set(position, scale);
        balanceList.add(balance);
/*        if (position.getAssetIdtreeSet() != null) {
          for (long[] assetIds : position.getAssetIdtreeSet()) {
            final long assetId = assetIds[0];
            final int tokenId = (int) assetIds[1];
            final long groupAssetId = assetIds[2];;
            balance.addAssetId(assetId, tokenId, groupAssetId);
          }
        }*/
      }
    }
    return balanceAdminMessage;
  }

  public final UserAdminMessage buildUserAdminMessage() {
    final UserAdminMessage userAdminMessage = new UserAdminMessage();
    userAdminMessage.setUpdateType(UpdateType.PUT);
    userAdminMessage.setUserId(id);
    userAdminMessage.setUser(this);
    userAdminMessage.setFirmId(firmId);
    userAdminMessage.setFeeTier(feeTierOrig); // feeTier
    userAdminMessage.setUsername(login);
    userAdminMessage.setPassword(password);
    userAdminMessage.setLmm(lmm);
    userAdminMessage.setRequestStatus(RequestStatus.SUCCESS);
    userAdminMessage.setExternalId(externalId);
    userAdminMessage.setUserType(userType);
    userAdminMessage.setStatus(0);
    userAdminMessage.setMarginCurveIdOverride(marginCurveIdOverride);

    final List<Balance> balanceList = userAdminMessage.getBalanceList();
    balanceList.clear();
    for (final Position position : positionArr) { // TODO: limit loop using user.maxActivePositionIndexHint
      if (position != null) {
        final Instrument instrument = InstrumentCache.get(position.getInstrumentId());
        int scale = instrument != null ? instrument.getQuantityScale() : 0;
        if (instrument == null) {
          final InstrumentPair pair = InstrumentCache.getPair(position.getInstrumentId());
          scale = pair != null ? pair.getQuantityScale() : 0;
        }
        final Balance balance = new Balance();
        balance.set(position, scale);
        balanceList.add(balance);
      }
    }
    return userAdminMessage;
  }

  // must be called from the matching engine thread
  public final void override(final UserAdminMessage userAdminMessage) {
    this.externalId = userAdminMessage.getExternalId();
    this.firmId = userAdminMessage.getFirmId();
    this.login = userAdminMessage.getUsername();
    this.password = userAdminMessage.getPassword();
    this.lmm = userAdminMessage.isLmm();
    this.useDiscountFeesCoin = userAdminMessage.isUseDiscountFeesCoin();
    this.feeTier = userAdminMessage.getFeeTier();
    this.userType = userAdminMessage.getUserType();
    this.marginCurveIdOverride = userAdminMessage.getMarginCurveIdOverride();
    this.feeTierOrig = feeTier;
    if (positionArr == null) // only recreate if null
      this.positionArr = new Position[Math.max(InstrumentCache.getInstrumentCapacity(), InstrumentCache.getPairCapacity())];

    for (final Balance balance : userAdminMessage.getBalanceList()) {
      if (balance != null && balance.getAssetId() >= 0) {

        final DecimalFloat quantityDecimal = balance.getBalance();
        long quantityLong = quantityDecimal.value();
        final int quantityScale = quantityDecimal.scale();
        final Instrument instrument = InstrumentCache.get(balance.getAssetId());
        final TokenType tokenType = balance.getTokenType();
        String name = null;

        if (instrument != null) {
          name = instrument.getName();
          if (instrument.getQuantityScale() > quantityScale) {
            for (int i = 0; i < (instrument.getQuantityScale() - quantityScale); i++)
              quantityLong = quantityLong * 10;
          } else if (instrument.getQuantityScale() < quantityScale) {
            for (int i = 0; i < (quantityScale - instrument.getQuantityScale()); i++)
              quantityLong = quantityLong / 10;
          }
        } else {
          final InstrumentPair pair = InstrumentCache.getPair(balance.getAssetId());
          if (null != pair) {
            name = pair.getName();
            if (pair.getQuantityScale() > quantityScale) {
              for (int i = 0; i < (pair.getQuantityScale() - quantityScale); i++)
                quantityLong = quantityLong * 10;
            } else if (pair.getQuantityScale() < quantityScale) {
              for (int i = 0; i < (quantityScale - pair.getQuantityScale()); i++)
                quantityLong = quantityLong / 10;
            }
          }
        }

        setPosition(balance.getAssetId(), quantityLong, balance.getAssetIdtreeSet(), balance.getAssetId2(), name, tokenType);
      }
    }
  }

  public final AtomicInteger getAutoLiquidationState() {
    return autoLiquidationState;
  }

  public final AtomicInteger getAutoLiquidationCounter() {
    return autoLiquidationCounter;
  }

  public final AtomicInteger getCollateralSwapState() {
    return collateralSwapState;
  }

  public final long getLastLiquidationTime() {
    return lastLiquidationTime;
  }

  public final void setLastLiquidationTime(final long lastLiquidationTime) {
    this.lastLiquidationTime = lastLiquidationTime;
  }

  public void updateIncrement(final UserAdminMessage userAdminMessage) {
    this.firmId = userAdminMessage.getFirmId();
    this.login = userAdminMessage.getUsername();
    this.password = userAdminMessage.getPassword();
    this.lmm = userAdminMessage.isLmm();
    this.feeTier = userAdminMessage.getFeeTier();
    this.feeTierOrig = feeTier;
    this.externalId = userAdminMessage.getExternalId();
    this.userType = userAdminMessage.getUserType();

    if (positionArr == null)
      positionArr = new Position[Math.max(InstrumentCache.getInstrumentCapacity(), InstrumentCache.getPairCapacity())];

    for (final Balance balance : userAdminMessage.getBalanceList()) {
      if (balance != null && balance.getAssetId() >= 0) {

        final DecimalFloat quantityDecimal = balance.getBalance();
        long quantityLong = quantityDecimal.value();
        final int quantityScale = quantityDecimal.scale();
        final Instrument instrument = InstrumentCache.get(balance.getAssetId());
        if (instrument != null) {
          if (instrument.getQuantityScale() > quantityScale) {
            for (int i = 0; i < (instrument.getQuantityScale() - quantityScale); i++)
              quantityLong = quantityLong * 10;
          } else if (instrument.getQuantityScale() < quantityScale) {
            for (int i = 0; i < (quantityScale - instrument.getQuantityScale()); i++)
              quantityLong = quantityLong / 10;
          }
        } else {
          final InstrumentPair pair = InstrumentCache.getPair(balance.getAssetId());
          if (null != pair) {
            if (pair.getQuantityScale() > quantityScale) {
              for (int i = 0; i < (pair.getQuantityScale() - quantityScale); i++)
                quantityLong = quantityLong * 10;
            } else if (pair.getQuantityScale() < quantityScale) {
              for (int i = 0; i < (quantityScale - pair.getQuantityScale()); i++)
                quantityLong = quantityLong / 10;
            }
          }
        }

        addPosition(balance.getAssetId(), quantityLong, balance.getAssetIdtreeSet(), balance.getAssetId2(), balance.getTokenType());
      }
    }
  }

  // must be called from the matching engine thread
  public void override(final BalanceAdminMessage balanceAdminMessage) {
    LOGGER.info("override.balanceAdminMessage: " + balanceAdminMessage.toJSON());
    this.externalId = balanceAdminMessage.getExternalId();
    if (positionArr == null) // only recreate if null
      this.positionArr = new Position[Math.max(InstrumentCache.getInstrumentCapacity(), InstrumentCache.getPairCapacity())];

    for (final Balance balance : balanceAdminMessage.getBalanceList()) {
      if (balance != null && balance.getAssetId() > 0) {

        final DecimalFloat quantityDecimal = balance.getBalance();
        long quantityLong = quantityDecimal.value();
        final int quantityScale = quantityDecimal.scale();
        final Instrument instrument = InstrumentCache.get(balance.getAssetId());
        final TokenType tokenType = balance.getTokenType();
        String name = null;
        if (instrument != null) {
          name = instrument.getName();
          if (instrument.getQuantityScale() > quantityScale) {
            for (int i = 0; i < (instrument.getQuantityScale() - quantityScale); i++)
              quantityLong = quantityLong * 10;
          } else if (instrument.getQuantityScale() < quantityScale) {
            for (int i = 0; i < (quantityScale - instrument.getQuantityScale()); i++)
              quantityLong = quantityLong / 10;
          }
        } else {
          final InstrumentPair pair = InstrumentCache.getPair(balance.getAssetId());
          if (null != pair) {
            name = pair.getName();
            if (pair.getQuantityScale() > quantityScale) {
              for (int i = 0; i < (pair.getQuantityScale() - quantityScale); i++)
                quantityLong = quantityLong * 10;
            } else if (pair.getQuantityScale() < quantityScale) {
              for (int i = 0; i < (quantityScale - pair.getQuantityScale()); i++)
                quantityLong = quantityLong / 10;
            }
          }
        }

        final Position position = setPosition(balance.getAssetId(), quantityLong, balance.getAssetIdtreeSet(), balance.getAssetId(), name,
            tokenType);

        position.setUsdUnrealized(balance.getUsdUnrealized());
        position.setUsdRealized(balance.getUsdRealized());
        position.setUsdAvgCostBasisDouble(balance.getUsdAvgCostBasis());
        position.setQuotedUsdMark(balance.getQuotedUsdMark());
        position.setSettleCoinUsdMark(balance.getSettleCoinUsdMark());
        position.setSettleCoinUnrealized(balance.getSettleCoinUnrealized());
        position.setSettleCoinRealized(balance.getSettleCoinRealized());
        position.setAssetIdtreeSet(balance.getAssetIdtreeSet());
      }
    }
  }

  // must be called from the matching engine thread
  private final void updateIncrementInstrument(final BalanceAdminMessage balanceAdminMessage, final Balance balance,
      final Instrument instrument) {
    LOGGER.info("updateIncrementInstrument.balanceAdminMessage: " + balanceAdminMessage.toJSON());
    LOGGER.info("updateIncrementInstrument.balance: " + balance.toJSON());
    final DecimalFloat quantityDecimal = balance.getBalanceChange();
    long quantityLong = quantityDecimal.value();
    final int quantityScale = quantityDecimal.scale();


    if (instrument.getQuantityScale() > quantityScale) {
      for (int i = 0; i < (instrument.getQuantityScale() - quantityScale); i++)
        quantityLong = quantityLong * 10;
    } else if (instrument.getQuantityScale() < quantityScale) {
      for (int i = 0; i < (quantityScale - instrument.getQuantityScale()); i++)
        quantityLong = quantityLong / 10;
    }

    if (balanceAdminMessage.getTxType() == 1) { // map old txn_type from api
      balanceAdminMessage.setTxType(TX_DEPOSIT);
    } else if (balanceAdminMessage.getTxType() == 2) {
      balanceAdminMessage.setTxType(TX_WITHDRAW);
      //process withdraw fee
      final Instrument feeInstrument = InstrumentCache.get(instrument.getWithdrawFeeInstrument());
      final User exchangeUser = UserCache.getExchangeUser();
      if (feeInstrument != null && exchangeUser != null) {
        double withdrawFee = instrument.getWithdrawFee();
        LOGGER.info("Instrument withdraw fee. symbol: " + instrument.getSymbol() + " withdrawFee: " + withdrawFee);
        if (withdrawFee > 0) {
          if (feeInstrument.getQuantityScale() > 0) {//qty scale is zero
            for (int i = 0; i < (feeInstrument.getQuantityScale()); i++)
              withdrawFee = withdrawFee * 10;
          }
          Position feePosition = getPosition(feeInstrument.getId());
          Position exchangePosition = exchangeUser.getPosition(feeInstrument.getId());

          DecimalFormat df = new DecimalFormat("###,###,###.##");
          String balBeforeWF = df.format(feePosition.getQuantity());
          String availBalBeforeWF = df.format(feePosition.getAvailableQuantity());

          String exBalBeforeWF = df.format(exchangePosition.getQuantity());
          String exAvailBalBeforeWF = df.format(exchangePosition.getAvailableQuantity());

          LOGGER.info("Withdraw fee. userId: " + balanceAdminMessage.getUserId() + " withdrawFee: " + withdrawFee);
          feePosition = addPosition(feeInstrument.getId(), (long) -withdrawFee, null, 0, TokenType.ERC20_GROUP);
          exchangePosition = exchangeUser.addPosition(feeInstrument.getId(), (long) withdrawFee, null, 0, TokenType.ERC20_GROUP);

          String balAfterWF = df.format(feePosition.getQuantity());
          String availBalAfterWF = df.format(feePosition.getAvailableQuantity());

          String exBalAfterWF = df.format(exchangePosition.getQuantity());
          String exAvailBalAfterWF = df.format(exchangePosition.getAvailableQuantity());

          LOGGER.info("\nbalBeforeWF\t\t\t:" + balBeforeWF + "\nexBalAfterWF\t\t:" + exBalAfterWF
              + "\navailBalBeforeWF\t:" + availBalBeforeWF + "\navailBalAfterWF\t\t:" + availBalAfterWF
              + "\nexBalBeforeWF\t\t:" + exBalBeforeWF + "\nbalAfterWF\t\t\t:" + balAfterWF
              + "\nexAvailBalBeforeWF\t:" + exAvailBalBeforeWF + "\nexAvailBalAfterWF\t:" + exAvailBalAfterWF);

        } else {
          LOGGER.info("Zero withdraw fee. symbol: " + instrument.getSymbol());
        }
      } else {
        LOGGER.info("Withdraw fee is not processed. feeInstrument: " + feeInstrument + " exchangeUser: " + exchangeUser);
      }
    } else if (balanceAdminMessage.getTxType() == 12) { // cancel withdraw
      balanceAdminMessage.setTxType(TX_DEPOSIT);
      //refund withdraw fee
      final Instrument feeInstrument = InstrumentCache.get(instrument.getWithdrawFeeInstrument());
      final User exchangeUser = UserCache.getExchangeUser();
      if (feeInstrument != null && exchangeUser != null) {
        double withdrawFee = instrument.getWithdrawFee();
        LOGGER.info("Instrument withdraw fee refund. symbol: " + instrument.getSymbol() + " withdrawFee: " + withdrawFee);
        if (withdrawFee > 0) {
          if (feeInstrument.getQuantityScale() > 0) {//qty scale is zero
            for (int i = 0; i < (feeInstrument.getQuantityScale()); i++)
              withdrawFee = withdrawFee * 10;
          }
          Position feePosition = getPosition(feeInstrument.getId());
          Position exchangePosition = exchangeUser.getPosition(feeInstrument.getId());

          DecimalFormat df = new DecimalFormat("###,###,###.##");
          String balBeforeWF = df.format(feePosition.getQuantity());
          String availBalBeforeWF = df.format(feePosition.getAvailableQuantity());

          String exBalBeforeWF = df.format(exchangePosition.getQuantity());
          String exAvailBalBeforeWF = df.format(exchangePosition.getAvailableQuantity());

          LOGGER.info("Withdraw fee refund. userId: " + balanceAdminMessage.getUserId() + " withdrawFee: " + withdrawFee);
          feePosition = addPosition(feeInstrument.getId(), (long) withdrawFee, null, 0, TokenType.ERC20_GROUP);
          exchangePosition = exchangeUser.addPosition(feeInstrument.getId(), (long) -withdrawFee, null, 0, TokenType.ERC20_GROUP);

          String balAfterWF = df.format(feePosition.getQuantity());
          String availBalAfterWF = df.format(feePosition.getAvailableQuantity());

          String exBalAfterWF = df.format(exchangePosition.getQuantity());
          String exAvailBalAfterWF = df.format(exchangePosition.getAvailableQuantity());

          LOGGER.info("\nbalBeforeWF\t\t\t:" + balBeforeWF + "\nexBalAfterWF\t\t:" + exBalAfterWF
              + "\navailBalBeforeWF\t:" + availBalBeforeWF + "\navailBalAfterWF\t\t:" + availBalAfterWF
              + "\nexBalBeforeWF\t\t:" + exBalBeforeWF + "\nbalAfterWF\t\t\t:" + balAfterWF
              + "\nexAvailBalBeforeWF\t:" + exAvailBalBeforeWF + "\nexAvailBalAfterWF\t:" + exAvailBalAfterWF);

        } else {
          LOGGER.info("Zero withdraw fee. symbol: " + instrument.getSymbol());
        }
      } else {
        LOGGER.info("Withdraw fee refund is not processed. feeInstrument: " + feeInstrument + " exchangeUser: " + exchangeUser);
      }
    }
    if (quantityLong <= 0 && Context.isEnableBalanceWithdrawExactLimits() && balanceAdminMessage.getTxType() <= TX_ADMIN_WITHDRAW) {
      // if withdrawing with limits, must be exact don't reduce amounts
      final Position position = getPosition(balance.getAssetId());
      long tempQuantityLong = quantityLong;
      if (position.getAvailableQuantity() > 0 && usdMarginableValue > usdMarginRequiredValue) {
        tempQuantityLong = -Math.min(Math.abs(quantityLong), position.getAvailableQuantity()); // limit to available position

        if (usdMarginRequiredValue > 0) { // limit to requiredMargin
          long usdAvailableAdjusted = (long) MbxMath.roundToBestPrecision(
              MbxMath.roundToBestPrecision(usdMarginableValue - usdMarginRequiredValue) * SETTLE_INSTRUMENT_QUANTITY_SCALE_MULT);
          tempQuantityLong = -Math.min(Math.abs(quantityLong), Math.abs(usdAvailableAdjusted));
        }
      } else
        tempQuantityLong = 0;

      if (tempQuantityLong == quantityLong) // accepted
        addPosition(balance.getAssetId(), quantityLong, balance.getAssetIdtreeSet(), balance.getAssetId2(), balance.getTokenType());
      else { // rejected
        quantityLong = 0;
        balanceAdminMessage.setTxType(TX_ADMIN_WITHDRAW_REJECTED);
      }

      balance.setBalance(position.getQuantity(), instrument.getQuantityScale());
      balance.setBalanceChange(quantityLong, instrument.getQuantityScale()); // update newly changed amount
    } else if (quantityLong <= 0 && Context.isEnableBalanceWithdrawLimits() && balanceAdminMessage.getTxType() <= TX_ADMIN_WITHDRAW) {
      // reduce size if needed, don't allow withdrawing more than current position
      final Position position = getPosition(balance.getAssetId());
      if (position.getAvailableQuantity() > 0 && usdMarginableValue > usdMarginRequiredValue) {
        quantityLong = -Math.min(Math.abs(quantityLong), position.getAvailableQuantity()); // limit to available position

        if (usdMarginRequiredValue > 0) { // limit to requiredMargin
          long usdAvailableAdjusted = (long) MbxMath.roundToBestPrecision(
              MbxMath.roundToBestPrecision(usdMarginableValue - usdMarginRequiredValue) * SETTLE_INSTRUMENT_QUANTITY_SCALE_MULT);
          quantityLong = -Math.min(Math.abs(quantityLong), Math.abs(usdAvailableAdjusted));
        }

        addPosition(balance.getAssetId(), quantityLong, balance.getAssetIdtreeSet(), balance.getAssetId2(), balance.getTokenType());
      } else
        quantityLong = 0;
      balance.setBalance(position.getQuantity(), instrument.getQuantityScale());
      balance.setBalanceChange(quantityLong, instrument.getQuantityScale()); // update newly changed amount
    } else { // original addPosition without checks
      final Position position = addPosition(balance.getAssetId(), quantityLong, balance.getAssetIdtreeSet(), balance.getAssetId2(), balance.getTokenType());
      balance.setBalance(position.getQuantity(), instrument.getQuantityScale());
      balance.setBalanceChange(quantityLong, instrument.getQuantityScale()); // update newly changed amount
    }
  }

  // must be called from the matching engine thread
  private final void updateIncrementPair(final BalanceAdminMessage balanceAdminMessage, final Balance balance, final InstrumentPair pair) {
    final DecimalFloat quantityDecimal = balance.getBalanceChange();
    long quantityLong = quantityDecimal.value();
    final int quantityScale = quantityDecimal.scale();

    if (pair.getQuantityScale() > quantityScale) {
      for (int i = 0; i < (pair.getQuantityScale() - quantityScale); i++)
        quantityLong = quantityLong * 10;
    } else if (pair.getQuantityScale() < quantityScale) {
      for (int i = 0; i < (quantityScale - pair.getQuantityScale()); i++)
        quantityLong = quantityLong / 10;
    }
    // if withdrawing with checks don't allow withdraw pairs
    if (Context.isEnableBalanceWithdrawLimits() && balanceAdminMessage.getTxType() <= TX_ADMIN_WITHDRAW) {
      quantityLong = 0;
      final Position position = addPosition(balance.getAssetId(), quantityLong, balance.getAssetIdtreeSet(), balance.getAssetId2(), balance.getTokenType());
      balance.setBalance(position.getQuantity(), pair.getQuantityScale());
      balance.setBalanceChange(quantityLong, pair.getQuantityScale()); // update newly changed amount
    } else {
      final Position position = addPosition(balance.getAssetId(), quantityLong, balance.getAssetIdtreeSet(), balance.getAssetId2(), balance.getTokenType());
      balance.setBalance(position.getQuantity(), pair.getQuantityScale());
      balance.setBalanceChange(quantityLong, pair.getQuantityScale()); // update newly changed amount
    }
  }

  // must be called from the matching engine thread
  public void updateIncrement(final BalanceAdminMessage balanceAdminMessage) {
    this.externalId = balanceAdminMessage.getExternalId();

    if (positionArr == null)
      positionArr = new Position[Math.max(InstrumentCache.getInstrumentCapacity(), InstrumentCache.getPairCapacity())];

    for (final Balance balance : balanceAdminMessage.getBalanceList()) {
      if (balance != null && balance.getAssetId() >= 0) {
        final Instrument instrument = InstrumentCache.get(balance.getAssetId());

        if (null != instrument) {
          updateIncrementInstrument(balanceAdminMessage, balance, instrument);
        } else {
          final InstrumentPair pair = InstrumentCache.getPair(balance.getAssetId());
          if (null != pair) {
            updateIncrementPair(balanceAdminMessage, balance, pair);
          }
        }
      }
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, "updateIncrement, userId=", id, ", positionArr", Arrays.toString(positionArr), ", balanceAdminMessage=",
          balanceAdminMessage);
    }
  }

  public int recalcMaxTouchedPositionsCount() {
    int count = 0;
    for (final Position position : positionArr) {
      if ((position != null) && (position.getQuantity() != 0 || position.isTouched()))
        count++;
    }
    maxTouchedPositionsCount = count;
    return maxTouchedPositionsCount;
  }

  public void resizePositionArr() {
    positionArr = Arrays.copyOf(positionArr, Math.max(InstrumentCache.getInstrumentCapacity(), InstrumentCache.getPairCapacity()));
  }

  public void resizePositionArr(final int newSize) {
    positionArr = Arrays.copyOf(positionArr, Math.min(newSize, 4096));
  }

  public final int getId() {
    return id;
  }

  public final String getLogin() {
    return login;
  }

  public final void setLogin(final String login) {
    this.login = login;
  }

  public final String getPassword() {
    return password;
  }

  public final void setPassword(final String password) {
    this.password = password;
  }

  public final int getFirmId() {
    return firmId;
  }

  public final void setFirmId(final int firmId) {
    this.firmId = firmId;
  }

  public final boolean isLmm() {
    return lmm;
  }

  public final void setLmm(final boolean lmm) {
    this.lmm = lmm;
  }

  public final int getFeeTier() {
    return feeTier;
  }

  public final void setFeeTier(final int feeTier) {
    this.feeTier = feeTier;
  }

  public final int getFeeTierOrig() {
    return feeTierOrig;
  }

  public final void setFeeTierOrig(final int feeTierOrig) {
    this.feeTierOrig = feeTierOrig;
  }

  public final double getMarginRatio() {
    return marginRatio;
  }

  public final void setMarginRatio(final double marginRatio) {
    this.marginRatio = marginRatio;
  }

  public final double getUsdValue() {
    return usdValue;
  }

  public final void setUsdValue(final double usdValue) {
    this.usdValue = usdValue;
  }

  public final double getUsdNotionalPositionValue() {
    return usdNotionalPositionValue;
  }

  public final void setUsdNotionalPositionValue(final double usdPositionValue) {
    this.usdNotionalPositionValue = usdPositionValue;
  }

  public final double getUsdMarginValue() {
    return usdMarginValue;
  }

  public final void setUsdMarginValue(final double usdMarginValue) {
    this.usdMarginValue = usdMarginValue;
  }

  public final double getUsdMarginableValue() {
    return usdMarginableValue;
  }

  public final void setUsdMarginableValue(final double usdMarginableValue) {
    this.usdMarginableValue = usdMarginableValue;
  }

  public final double getUsdMarginRequiredValue() {
    return usdMarginRequiredValue;
  }

  public final void setUsdMarginRequiredValue(final double usdMarginRequiredValue) {
    this.usdMarginRequiredValue = usdMarginRequiredValue;
  }

  public final double getUsdMarginMaintValue() {
    return usdMarginMaintValue;
  }

  public final void setUsdMarginMaintValue(final double usdMarginMaintValue) {
    this.usdMarginMaintValue = usdMarginMaintValue;
  }

  public final double getLeverageRatio() {
    return leverageRatio;
  }

  public final void setLeverageRatio(final double leverageRatio) {
    this.leverageRatio = leverageRatio;
  }

  public final double getUsdMaxExposurePositionAndOpenOrdersValue() {
    return usdMaxExposurePositionAndOpenOrdersValue;
  }

  public final void setUsdMaxExposurePositionAndOpenOrdersValue(final double usdMaxExposurePositionAndOpenOrdersValue) {
    this.usdMaxExposurePositionAndOpenOrdersValue = usdMaxExposurePositionAndOpenOrdersValue;
  }

  // use diff to only return open orders exposure
  public final double getUsdOpenOrdersValue() {
    return usdMaxExposurePositionAndOpenOrdersValue - usdNotionalPositionValue;
  }

  public final double getUsdUnrealized() {
    return usdUnrealized;
  }

  public final void setUsdUnrealized(final double usdUnrealized) {
    this.usdUnrealized = usdUnrealized;
  }

  public final double getUsdOpenOrdersRequiredValue() {
    return usdOpenOrdersRequiredValue;
  }

  public final void setUsdOpenOrdersRequiredValue(final double usdOpenOrdersRequiredValue) {
    this.usdOpenOrdersRequiredValue = usdOpenOrdersRequiredValue;
  }

  public final int getMarginCurveIdOverride() {
    return marginCurveIdOverride;
  }

  public final void setMarginCurveIdOverride(final int marginCurveIdOverride) {
    this.marginCurveIdOverride = marginCurveIdOverride;
  }

  public final int getVerification() {
    return verification;
  }

  public final void setVerification(final int verification) {
    this.verification = verification;
  }

  public final int incrementOpenOrderCount() {
    return ++openOrderCount;
  }

  public final int decrementOpenOrderCount() {
    return --openOrderCount;
  }

  public final int getOpenOrderCount() {
    return openOrderCount;
  }

  public final void setOpenOrderCount(final int openOrderCount) {
    this.openOrderCount = openOrderCount;
  }

  public final long getExternalId() {
    return externalId;
  }

  public final void setExternalId(final long externalId) {
    this.externalId = externalId;
  }

  public final int getUserType() {
    return userType;
  }

  public final void setUserType(final int userType) {
    this.userType = userType;
  }

  public final boolean isActive() {
    return isActive;
  }

  public final void setActive(final boolean isActive) {
    this.isActive = isActive;
  }

  public final boolean isUseDiscountFeesCoin() {
    return useDiscountFeesCoin;
  }

  public final void setUseDiscountFeesCoin(final boolean useDiscountFeesCoin) {
    this.useDiscountFeesCoin = useDiscountFeesCoin;
  }

  public final Position[] getPositionArr() {
    return positionArr;
  }

  public final double getUsdCollateralValue() {
    return usdCollateralValue;
  }

  public final double getUsdCollateralValueDiscounted() {
    return usdCollateralValueDiscounted;
  }

  public final int getMaxActivePositionIndexHint() {
    return maxActivePositionIndexHint;
  }

  public final void updateMaxActivePositionIndexHint(final int value) {
    if (value > maxActivePositionIndexHint)
      maxActivePositionIndexHint = value;
  }

  public final PositionReportMessage getPrevPositionReport() {
    return prevPositionReport;
  }

  public final void setPrevPositionReport(final PositionReportMessage prevPositionReport) {
    this.prevPositionReport = prevPositionReport;
  }

  // must be called from the matching thread
  // return consolidated array of only touched positions
  // default array size of 16
  public final Position[] copySetPositionArr(final BalanceAdminMessage balanceAdminMessage) {
    Position[] cloneArr = balanceAdminMessage.reservePositionArrSize(16);

    int cloneIndex = 1;
    for (int i = 0; i < positionArr.length; i++) {
      final Position position = positionArr[i];
      if (position == null)
        continue;
      if (position.getQuantity() == 0 && !position.isTouched() && i > 3)
        continue;
      if (cloneIndex >= cloneArr.length) {
        cloneArr = balanceAdminMessage.reservePositionArrSize(positionArr.length);
      }

      cloneArr[cloneIndex] = Position.set(PositionMatchThreadObjectPool.get(), this, positionArr[i]);
      cloneIndex++;
    }
    balanceAdminMessage.setPositionsLength(cloneIndex);
    return cloneArr;
  }

  // must be called from the matching thread
  // return consolidated array of only touched positions
  // default array size of 16
  public final Position[] copySetPositionArr(final UserAdminMessage userAdminMessage) {
    Position[] cloneArr = userAdminMessage.reservePositionArrSize(16);

    int cloneIndex = 1;
    for (int i = 0; i < positionArr.length; i++) {
      final Position position = positionArr[i];
      if (position == null)
        continue;
      if (position.getQuantity() == 0 && !position.isTouched() && i > 3)
        continue;

      if (cloneIndex >= cloneArr.length) {
        cloneArr = userAdminMessage.reservePositionArrSize(positionArr.length);
      }

      cloneArr[cloneIndex] = Position.set(PositionMatchThreadObjectPool.get(), this, positionArr[i]);
      cloneIndex++;
    }
    userAdminMessage.setPositionsLength(cloneIndex);
    return cloneArr;
  }

  // must be called from the matching thread
  // return consolidated array of only touched positions
  // default array size of 16
  public final Position[] copySetPositionArr(final ExecutionReportMessage executionReportMessage) {
    Position[] cloneArr = executionReportMessage.reservePositionArrSize(16);

    int cloneIndex = 1;
    for (int i = 0; i < positionArr.length; i++) {
      final Position position = positionArr[i];
      if (position == null)
        continue;
      if (position.getQuantity() == 0 && !position.isTouched() && i > 3)
        continue;

      if (cloneIndex >= cloneArr.length) {
        cloneArr = executionReportMessage.reservePositionArrSize(positionArr.length);
      }

      cloneArr[cloneIndex] = Position.set(PositionMatchThreadObjectPool.get(), this, positionArr[i]);
      cloneIndex++;
    }
    executionReportMessage.setPositionsLength(cloneIndex);
    return cloneArr;
  }

  // must be called from the matching thread
  // return consolidated array of only touched positions
  // default array size of 16
  public final Position[] copySetPositionArr(final LogonMessage logonMessage) {
    Position[] cloneArr = logonMessage.reservePositionArrSize(16);


    int cloneIndex = 1;
    for (int i = 0; i < positionArr.length; i++) {
      final Position position = positionArr[i];
      if (position == null)
        continue;
      if (position.getQuantity() == 0 && !position.isTouched() && i > 3)
        continue;

      if (cloneIndex >= cloneArr.length) {
        cloneArr = logonMessage.reservePositionArrSize(positionArr.length);
      }

      cloneArr[cloneIndex] = Position.set(PositionMatchThreadObjectPool.get(), this, positionArr[i]);
      cloneIndex++;
    }
    logonMessage.setPositionsLength(cloneIndex);
    return cloneArr;
  }

  public final void setPositionArr(final Position[] positionArr) {
    this.positionArr = positionArr;
  }

  public final void setPosition(final Position position) {
    if (position.getInstrumentId() >= positionArr.length - 1)
      resizePositionArr(position.getInstrumentId() + 1);
    position.touched();
    this.positionArr[position.getInstrumentId()] = position;
  }

  // must be called from the matching engine thread
  public final Position getPosition(final int instrumentId) {
    if (instrumentId >= positionArr.length - 1)
      resizePositionArr(instrumentId + 1);

    if (positionArr[instrumentId] == null) {
      positionArr[instrumentId] = Position.set(PositionMatchThreadObjectPool.get(), this, instrumentId, 0, 0);
    }
    return positionArr[instrumentId];
  }

  // must be called from the matching engine thread
  public final Position setPosition(final int instrumentId, final long quantity, final Set<long[]> assetIdtreeSet, final long assetId,
      final String name, final TokenType tokenType) {
    if (instrumentId >= positionArr.length - 1)
      resizePositionArr(instrumentId + 1);

    if (instrumentId > maxActivePositionIndexHint)
      maxActivePositionIndexHint = instrumentId;

    if (positionArr[instrumentId] == null) {
      positionArr[instrumentId] = Position.set(PositionMatchThreadObjectPool.get(), this, instrumentId, quantity, quantity);
      if (tokenType == TokenType.ERC20_GROUP) {
        final AssetGroup group = updateGroup(this.id, assetId, name, instrumentId, quantity, com.solfini.sbe.encoder.TokenType.ERC20_GROUP);
        positionArr[instrumentId].setAvailableQuantity(quantity, this.id, group.getId());
        positionArr[instrumentId].addAssetId(0, 0, group.getId());
      } else if (tokenType == TokenType.ERC721) {
        positionArr[instrumentId].addAssetIdtreeSet(assetIdtreeSet);
      }

      return positionArr[instrumentId];
    } else {
      long groupId = 0;
      positionArr[instrumentId].setQuantity(quantity);
      if (tokenType == TokenType.ERC20_GROUP) {
        final AssetGroup group = updateGroup(this.id, assetId, name, instrumentId, quantity, com.solfini.sbe.encoder.TokenType.ERC20_GROUP);
        groupId = group.getId();
        positionArr[instrumentId].addAssetId(0, 0, group.getId());
      } else if (tokenType == TokenType.ERC721) {
        positionArr[instrumentId].addAssetIdtreeSet(assetIdtreeSet);
      }
      positionArr[instrumentId].setAvailableQuantity(quantity, this.id, groupId);
      positionArr[instrumentId].touched();
      return positionArr[instrumentId];
    }
  }

  private final AssetGroup updateGroup(final int userId, final long assetId, final String name, final int securityId, final long quantity,
      final com.solfini.sbe.encoder.TokenType tokenType) {
    AssetGroup assetGroup = AssetGroupCache.getByUserIdAndERC20Asset(userId, assetId);
    if (assetGroup == null) {
      assetGroup = new AssetGroup();
      assetGroup.setUpdateType(com.solfini.sbe.encoder.UpdateType.PUT);
      assetGroup.setOwnerUserId(userId);
      assetGroup.setName(name);
      assetGroup.setSecurityId(securityId);
      assetGroup.setAssetId(assetId);
      assetGroup.setTokenType(tokenType);
      assetGroup.setAvailableQuantity(assetGroup.getAvailableQuantity() + quantity);
    }
    assetGroup.setQuantity(assetGroup.getQuantity() + quantity);

    AssetGroupCache.onModel(assetGroup);

    return assetGroup;
  }

  // must be called from the matching engine thread
  public final Position addPosition(final int instrumentId, final long quantity, final long assetId, final int tokenId,
      final long groupAssetId) {
    if (assetId == 0 && groupAssetId == 0)
      return addPosition(instrumentId, quantity, null, 0, TokenType.ERC20);
    else {
      AssetGroup group = groupAssetId > 0 ? AssetGroupCache.get(groupAssetId) : AssetGroupCache.getByUserIdAndERC20Asset(this.id, assetId);

      if (group != null && group.getTokenType() == com.solfini.sbe.encoder.TokenType.ERC20_GROUP) {
        return addPosition(instrumentId, quantity, null, assetId, TokenType.ERC20_GROUP);
      } else {
        final Set<long[]> assetIdtreeSet = new TreeSet<>(assetIdComparator);
        final long[] value = {assetId, tokenId, groupAssetId};
        assetIdtreeSet.add(value);

        return addPosition(instrumentId, quantity, assetIdtreeSet, 0, TokenType.ERC20_GROUP);
      }
    }
  }

  // must be called from the matching engine thread
  public final Position addPosition(final int instrumentId, final long quantity, final Set<long[]> assetIdtreeSet, final long assetId2,
      final TokenType tokenType) {
    if (instrumentId >= positionArr.length - 1)
      resizePositionArr(instrumentId + 1);

    if (instrumentId > maxActivePositionIndexHint)
      maxActivePositionIndexHint = instrumentId;

    if (positionArr[instrumentId] == null) {
      positionArr[instrumentId] = Position.set(PositionMatchThreadObjectPool.get(), this, instrumentId, quantity, quantity);
      if (tokenType == TokenType.ERC20_GROUP && assetId2 > 0) {
        final Instrument instrument = InstrumentCache.get(instrumentId);
        final AssetGroup assetGroup = updateGroup(this.id, assetId2, instrument.getName(), instrumentId, quantity, com.solfini.sbe.encoder.TokenType.ERC20_GROUP);
        final long[] value = {0, 0, assetGroup.getId()};
        final Set<long[]> treeSet = new TreeSet<>(assetIdComparator);
        treeSet.add(value);
        positionArr[instrumentId].setAssetIdtreeSet(treeSet);

      } else if (tokenType == TokenType.ERC721) {
        positionArr[instrumentId].addAssetIdtreeSet(assetIdtreeSet);
      }
      return positionArr[instrumentId];
    } else {
      long groupId = 0;
      positionArr[instrumentId].addQuantity(quantity);
      if (tokenType == TokenType.ERC20_GROUP && assetId2 > 0) {
        final Instrument instrument = InstrumentCache.get(instrumentId);
        final AssetGroup assetGroup = updateGroup(this.id, assetId2, instrument.getName(), instrumentId, quantity, com.solfini.sbe.encoder.TokenType.ERC20_GROUP);
        groupId = assetGroup.getId();
        final long[] value = {0, 0, assetGroup.getId()};
        positionArr[instrumentId].getAssetIdtreeSet().add(value);
      } else if (tokenType == TokenType.ERC721) {
        positionArr[instrumentId].addAssetIdtreeSet(assetIdtreeSet);
      }
      positionArr[instrumentId].addAvailableQuantity(quantity, this.id, groupId);
      positionArr[instrumentId].touched();

      return positionArr[instrumentId];
    }
  }

  public final Order lookupOrder(final long orderId, final int instrumentId, final Side side) {
    if (orderId == 0)
      return null;

    final Position position = positionArr[instrumentId];
    if (position == null)
      return null;

    final UserOpenOrdersByPair pair = position.getUserOpenOrdersByPair();
    if (pair == null)
      return null;

    return pair.lookupOrder(orderId, side);
  }

  public final Order lookupOrderBySecondaryOrderId(final long secondaryOrderId, final int instrumentId, final Side side) {
    if (secondaryOrderId == 0)
      return null;

    final Position position = positionArr[instrumentId];
    if (position == null)
      return null;

    final UserOpenOrdersByPair pair = position.getUserOpenOrdersByPair();
    if (pair == null)
      return null;

    return pair.lookupSecondaryOrder(secondaryOrderId, side);
  }

  // clorid required, instrumentId and side optional
  public final Order lookupOrderByClorid(final String clorid, final int instrumentId, final Side side) {
    if (instrumentId <= 0)
      return lookupOrderByClorid(clorid, side);

    final Position position = positionArr[instrumentId];
    if (position == null)
      return null;

    final UserOpenOrdersByPair pair = position.getUserOpenOrdersByPair();
    if (pair == null)
      return null;

    return pair.lookupOrder(clorid, side);
  }

  // iterate through pairs, slower lookup
  // clorid required, instrumentId and side optional
  public final Order lookupOrderByClorid(final String clorid, final Side side) {
    for (int i = 0; i < positionArr.length; i++) {
      final Position position = positionArr[i];
      if (position == null)
        continue;

      final UserOpenOrdersByPair pair = position.getUserOpenOrdersByPair();
      if (pair == null)
        continue;

      final Order order = pair.lookupOrder(clorid, side);
      if (order != null)
        return order;
    }

    return null;
  }

  public final void setUsdRisk(final double usdValue, final double usdMarginableValue, final double usdNotionalPositionValue,
      final double usdMarginMaintValue, final double usdMarginRequiredValue, final double leverageRatio, final double usdUnrealized,
      final double marginRatio, final double usdOpenOrdersRequiredValue, final double usdMaxExposurePositionAndOpenOrdersValue,
      final double usdCollateralValue, final double usdCollateralValueDiscounted) {
    this.usdValue = usdValue;
    this.usdMarginableValue = usdMarginableValue;
    this.usdNotionalPositionValue = usdNotionalPositionValue;
    this.usdMarginMaintValue = usdMarginMaintValue;
    this.usdMarginRequiredValue = usdMarginRequiredValue;
    this.leverageRatio = leverageRatio;
    this.usdUnrealized = usdUnrealized;
    this.marginRatio = marginRatio;
    this.usdOpenOrdersRequiredValue = usdOpenOrdersRequiredValue;
    this.usdMaxExposurePositionAndOpenOrdersValue = usdMaxExposurePositionAndOpenOrdersValue;
    this.usdCollateralValue = usdCollateralValue;
    this.usdCollateralValueDiscounted = usdCollateralValueDiscounted;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append(USER_ID_EQ).append(id).append(EXTERNALID_EQ).append(externalId).append(USERTYPE_EQ).append(userType).append(LOGIN_EQ)
        .append(login).append(FIRMID_EQ).append(firmId).append(FEETIER_EQ).append(feeTier).append(FEETIERORIG_EQ).append(feeTierOrig)
        .append(LMM_EQ).append(lmm).append(USEDISCOUNTFEESCOIN_EQ).append(useDiscountFeesCoin).append(VERIFICATION_EQ).append(verification)
        .append(USDVALUE_EQ).append(usdValue).append(USDNOTIONALPOSITIONVALUE_EQ).append(usdNotionalPositionValue)
        .append(USDOPENORDERSVALUE_EQ).append(usdMaxExposurePositionAndOpenOrdersValue).append(USDOPENORDERSREQUIREDVALUE_EQ)
        .append(usdOpenOrdersRequiredValue).append(USDMARGINVALUE_EQ).append(usdMarginValue).append(USDMARGINREQUIREDVALUE_EQ)
        .append(usdMarginRequiredValue).append(USDMARGINMAINTVALUE_EQ).append(usdMarginMaintValue).append(USDMARGINABLEVALUE_EQ)
        .append(usdMarginableValue).append(LEVERAGERATIO_EQ).append(leverageRatio).append(MARGINRATIO_EQ).append(marginRatio)
        .append(MARGINCURVEIDOVERRIDE_EQ).append(marginCurveIdOverride).append(USDUNREALIZED_EQ).append(usdUnrealized)
        .append(USDCOLLATERALVALUE_EQ).append(usdCollateralValue).append(USDCOLLATERALVALUEDISCOUNTED_EQ)
        .append(usdCollateralValueDiscounted).append(AUTOLIQUIDATIONSTATE_EQ).append(autoLiquidationState).append(COLLATERALSWAPSTATE_EQ)
        .append(collateralSwapState).append(POSITIONARR_EQ).append(Arrays.toString(positionArr)).append(']');
    return s;
  }

}
