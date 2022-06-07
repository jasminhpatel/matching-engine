package com.solfini.preordercheck;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.Fee;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.message.internal.LiquidationOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.pool.PositionMatchThreadObjectPool;
import com.solfini.risk.InsuranceState;
import com.solfini.risk.RiskAutoLiquidationThread;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.user.UserOpenOrdersByPair;
import com.solfini.util.MbxMath;
import com.solfini.util.PropertyReader;

/**
 *
 * @author Chris Mack
 *
 */
public class MarginPreOrderCheckAndSettle implements PreOrderCheck, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MarginPreOrderCheckAndSettle.class);

  private static final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
  private static final ManyToManyConcurrentArrayQueueCustom<User> riskToAutoLiquidatorQueue = Context.getRiskToAutoLiquidatorQueue();
  public static final int SETTLE_INSTRUMENT_ID = 1;
  private static final int SETTLE_INSTRUMENT_PRICE_SCALE_MULT = InstrumentCache.getSettleInstrumentPriceScaleMult(); // USDC defaults to 100
  public static final double NAV_FEE_OFFSET = 1 - (0.000001 * Fee.LIQUIDATION_FEE_RATE); // .995
  private static boolean LIQUIDATON_MODE = false; // don't auto-liquidate until fully started up
  private static boolean REJECT_MODE = true; // don't reject until fully started up. set to false when loading snap
  public static final long ADL_COOLOFF_TIME = PropertyReader.getProperty("ADL_COOLOFF_TIME", 2_000); // default to 2 seconds

  public static final boolean isLIQUIDATON_MODE() {
    return LIQUIDATON_MODE;
  }

  public static final void setLIQUIDATON_MODE(final boolean value) {
    LOGGER.warn(LOG_FMT_2, "setLIQUIDATON_MODE ", value);
    LIQUIDATON_MODE = value;
  }

  public static final boolean isREJECT_MODE() {
    return REJECT_MODE;
  }

  public static final void setREJECT_MODE(final boolean value) {
    LOGGER.info(LOG_FMT_2, "setREJECT_MODE ", value);
    REJECT_MODE = value;
  }

  private static final double getUsdMark(final InstrumentPair pair) {
    final double usdMark = pair.getIndexFeedUsdMark();
    if (usdMark > 0)
      return usdMark;
    else
      return pair.getOrderBook().getUsdMark();
  }

  // called from both matching engine thread and risk threads, and autoLiquidatorThread
  // usdMarkPricesToSet is only set when called from autoLiquidatorThread
  @Override
  public final void updateRisk(final User user, final double[] usdMarkPricesToSet) {
    if (Context.isDebugLogRisk() && user.getId() == 18) {
      LOGGER.debug(LOG_FMT_2, "verbose updateRisk user", user);
    }
    double usdValue = 0;
    double usdMarginableValue = 0;
    double usdNotionalPositionValue = 0;
    double usdOpenOrdersRequiredValue = 0;
    double usdMaxExposurePositionAndOpenOrdersValue = 0;
    double usdMarginRequiredValue = 0;
    double usdMarginMaintValue = 0;
    double usdUnrealized = 0;
    double leverageRatio = 0;
    double usdCollateralValue = 0;
    double otherCoinCollateralValue = 0;

    try {
      Position[] positionArr = user.getPositionArr();
      for (final Position position : positionArr) { // TODO: limit loop using user.maxActivePositionIndexHint
        if ((position != null) && (position.getQuantity() == 0)) {
          position.setUsdValue(0);
        }
        if (position != null && (position.getQuantity() != 0 || position.getOpenOrdersCount() != 0)) {
          if (AssetType.ASSET == position.getAssetType()) {
            // collateral coin assets
            final Instrument instrument = InstrumentCache.get(position.getInstrumentId());
            if (instrument != null) {
              final double usdMark = instrument.getIndexFeedUsdMark();
              if (usdMarkPricesToSet != null)
                usdMarkPricesToSet[position.getInstrumentId()] = usdMark;

              final double value = instrument.getQuantityScaleFactor() * position.getQuantity() * usdMark;
              final double availableValue = instrument.getQuantityScaleFactor() * position.getAvailableQuantity() * usdMark;

              usdValue += value;
              usdMarginableValue += availableValue * (1 - instrument.getCollateralPremiumFactor());
              usdCollateralValue += value;
              position.setUsdValue(value);
              position.setQuotedUsdMark(instrument.getIndexFeedUsdMark());

              if (position.getInstrumentId() > 1)
                otherCoinCollateralValue += Math.abs(value);

              // System.out.println(">>> updateRisk instrument=" + instrument.getSymbol()
              // + ", value=" + value + ", usdValue=" + usdValue
              // + ", usdCollateralValue=" + usdCollateralValue
              // + ", otherCoinCollateralValue=" + otherCoinCollateralValue);

              if (Context.isDebugLogRisk() && user.getId() == 18) {
                LOGGER.debug(LOG_FMT_12, "verbose updateRisk", user.getId(), RISK_USER_EQ, user.getId(), ", coin=", instrument.getId(),
                    VALUE_EQ, value, MARK_EQ, instrument.getIndexFeedUsdMark(), QUANTITY_EQ, position.getQuantity());
              }
            }
          } else {
            final InstrumentPair instrumentPair = InstrumentCache.getPair(position.getInstrumentId());
            if (instrumentPair != null) {
              final double usdMark = getUsdMark(instrumentPair);

              if (Context.isDebugLogRisk() && user.getId() == 18) {
                LOGGER.debug(LOG_FMT_6, VERBOSE_UPDATERISK_EQ, user.getId(), INSTRUMENTPAIR_MARK_EQ, usdMark, INSTRUMENT_PAIR_EQ,
                    instrumentPair);
              }

              if (usdMarkPricesToSet != null)
                usdMarkPricesToSet[position.getInstrumentId()] = usdMark;

              double quantity = position.getQuantity();
              quantity = quantity * instrumentPair.getQuantityScaleFactor();

              final double notional = quantity * usdMark;

              final double usdNotional = notional * instrumentPair.getQuoted().getIndexFeedUsdMark();

              final double unrealized = quantity > 0 ? usdNotional - (position.getUsdAvgCostBasisDouble() * quantity)
                  : Math.abs((position.getUsdAvgCostBasisDouble() * quantity)) - Math.abs(usdNotional);

              final double absNotional = Math.abs(notional);
              final double requiredMargin = NotionalMarginCalc.calcRequiredMargin(notional, quantity, usdMark, instrumentPair, user);
              final double maintMargin = NotionalMarginCalc.calcMaintMargin(notional, quantity, usdMark, instrumentPair, user);

              // calc open orders required
              final UserOpenOrdersByPair userOpenOrdersByPair = position.getUserOpenOrdersByPair();
              if (userOpenOrdersByPair != null) {
                if (instrumentPair.getAssetType() != AssetType.PAIR) { // don't include spot open orders in exposure
                  usdOpenOrdersRequiredValue += userOpenOrdersByPair.calcNotionalRequiredMargin(usdMark);
                  usdMaxExposurePositionAndOpenOrdersValue += userOpenOrdersByPair.getMaxNotional();
                }
              }

              usdUnrealized += unrealized;
              usdValue += unrealized;
              usdMarginableValue += unrealized;
              usdNotionalPositionValue += absNotional;
              if (instrumentPair.getAssetType() != AssetType.PAIR) { // don't include spot open orders in exposure
                usdMarginRequiredValue += requiredMargin;
                usdMarginMaintValue += maintMargin;
              }
              position.setUsdValue(unrealized);
              position.setUsdUnrealized(unrealized);
              position.setQuotedUsdMark(usdMark);

              // System.out.println(">>> updateRisk instrument=" + instrumentPair.getSymbol()
              // + ", notional=" + notional
              // + ", usdNotional=" + usdNotional
              // + ", usdUnrealized=" + usdUnrealized + ", usdValue=" + usdValue
              // + ", usdNotionalPositionValue=" + usdNotionalPositionValue
              // + ", usdMarginRequiredValue=" + usdMarginRequiredValue
              // + ", usdMarginMaintValue=" + usdMarginMaintValue);

              if (Context.isDebugLogRisk() && user.getId() == 18) {
                LOGGER.debug(LOG_FMT_24, "verbose updateRisk", user.getId(), RISK_USER_EQ, user.getId(), PAIR_EQ, instrumentPair.getId(),
                    UNREALIZED_EQ, unrealized, MARK_EQ, usdMark, QUANTITY_EQ, position.getQuantity(), ABSNOTIONAL_EQ, absNotional,
                    REQUIRED_MARGIN_EQ, requiredMargin, MAINT_MARGIN_EQ, maintMargin, USDMARGINREQUIREDVALUE_EQ, usdMarginRequiredValue,
                    USDMARGINMAINTVALUE_EQ, usdMarginMaintValue, INSTRUMENT_PAIR_EQ, instrumentPair);
              }
            }
          }
        }
      }

      adjustAndSetRiskValues(user, usdMarginableValue, usdMarginMaintValue, usdMarginRequiredValue, usdOpenOrdersRequiredValue, positionArr,
          usdValue, usdMaxExposurePositionAndOpenOrdersValue, leverageRatio, usdNotionalPositionValue, usdUnrealized, usdCollateralValue,
          otherCoinCollateralValue);

    } catch (Exception e) {
      LOGGER.error("error in updateRisk e=" + e + USER_EQ + user, e);
      LOGGER.error("error in updateRisk " + user, e);
    }
  }

  private static final void adjustAndSetRiskValues(final User user, final double usdMarginableValue, double usdMarginMaintValue,
      double usdMarginRequiredValue, double usdOpenOrdersRequiredValue, final Position[] positionArr, final double usdValue,
      final double usdMaxExposurePositionAndOpenOrdersValue, double leverageRatio, final double usdNotionalPositionValue,
      final double usdUnrealized, final double usdCollateralValue, final double otherCoinCollateralValue) {
    double usdCollateralValueDiscounted = usdCollateralValue;

    // handle alt collateral discounts
    // commented out since usdMarginableValue is used
    /*
     * try { final Instrument[] arr = InstrumentCache.getAltCollateralInstrumentArr(); if (arr != null && arr.length > 0) { double
     * usdMarginMaintValueNew = usdMarginMaintValue; double usdMarginRequiredValueNew = usdMarginRequiredValue; double
     * usdOpenOrdersRequiredValueNew = usdOpenOrdersRequiredValue; for (final Instrument collateral : arr) { final Position position =
     * positionArr[collateral.getId()]; if (position == null || position.getQuantity() == 0) continue;
     *
     * final double usdMark = collateral.getIndexFeedUsdMark(); final double value = collateral.getQuantityScaleFactor() *
     * position.getQuantity() * usdMark; final double allocation = value / usdValue; final double premium = allocation *
     * collateral.getCollateralPremiumFactor();
     *
     * usdMarginMaintValueNew = MbxMath.roundToBestPrecision(usdMarginMaintValueNew + (usdMarginMaintValue * premium));
     * usdMarginRequiredValueNew = MbxMath.roundToBestPrecision(usdMarginRequiredValueNew + (usdMarginRequiredValue * premium));
     * usdOpenOrdersRequiredValueNew = MbxMath.roundToBestPrecision(usdOpenOrdersRequiredValueNew + (usdOpenOrdersRequiredValue * premium));
     * usdCollateralValueDiscounted -= premium;
     *
     * // System.out.println(">>> adjustAndSetRiskValues instrument=" + collateral.getSymbol() // + ", usdMark=" + usdMark // + ", value=" +
     * value // + ", allocation=" + allocation // + ", premium=" + premium // + ", usdMarginMaintValueNew=" + usdMarginMaintValueNew // +
     * ", usdMarginRequiredValueNew=" + usdMarginRequiredValueNew // + ", usdOpenOrdersRequiredValueNew=" + usdOpenOrdersRequiredValueNew);
     * } if (Context.isDebugLogRisk() && user.getId() == 18) { LOGGER.debug(LOG_FMT_18, ">>> verbose updateRisk collateralPremium userId=",
     * user.getId(), USDMARGINMAINTVALUE_EQ, usdMarginMaintValue, ", usdMarginMaintValueNew=", usdMarginMaintValueNew,
     * USDMARGINREQUIREDVALUE_EQ, usdMarginRequiredValue, ", usdMarginRequiredValueNew=", usdMarginRequiredValueNew,
     * USDOPENORDERSREQUIREDVALUE_EQ, usdOpenOrdersRequiredValue, ", usdOpenOrdersRequiredValueNew=", usdOpenOrdersRequiredValueNew,
     * ", usdCollateralValue=", usdCollateralValue, ", usdCollateralValueDiscounted=", usdCollateralValueDiscounted); } usdMarginMaintValue
     * = usdMarginMaintValueNew; usdMarginRequiredValue = usdMarginRequiredValueNew; usdOpenOrdersRequiredValue =
     * usdOpenOrdersRequiredValueNew; } } catch (Exception e) { LOGGER.error("error in updateRisk alt collateral " + user, e); }
     */

    // if cross collateral is disabled, increase margins by other coin values
    // commented out since usdMarginableValue is used
    // if (!Context.isCrossCollateralEnabled()) {
    // usdMarginMaintValue += otherCoinCollateralValue;
    // usdMarginRequiredValue += otherCoinCollateralValue;
    // }

    usdMarginRequiredValue += usdOpenOrdersRequiredValue;
    // final double updatedLeverageRatio = usdValue > 0 ? (usdMaxExposurePositionAndOpenOrdersValue) / usdValue : 0;
    final double updatedLeverageRatio = usdValue > 0 ? (usdNotionalPositionValue) / usdMarginableValue : 0; // don't include open orders
    final double marginRatio = usdValue > 0 ? usdMarginMaintValue / usdValue : 0;

    // set risk values back to user in one call
    user.setUsdRisk(MbxMath.roundToBestPrecision(usdValue), MbxMath.roundToBestPrecision(usdMarginableValue),
        MbxMath.roundToBestPrecision(usdNotionalPositionValue), MbxMath.roundToBestPrecision(usdMarginMaintValue),
        MbxMath.roundToBestPrecision(usdMarginRequiredValue), MbxMath.roundToBestPrecision(updatedLeverageRatio),
        MbxMath.roundToBestPrecision(usdUnrealized), MbxMath.roundToBestPrecision(marginRatio),
        MbxMath.roundToBestPrecision(usdOpenOrdersRequiredValue), MbxMath.roundToBestPrecision(usdMaxExposurePositionAndOpenOrdersValue),
        MbxMath.roundToBestPrecision(usdCollateralValue), MbxMath.roundToBestPrecision(usdCollateralValueDiscounted));

    if (Context.isDebugLogRisk() && user.getId() == 18) {
      LOGGER.debug(LOG_FMT_16, VERBOSE_UPDATERISK_EQ, user.getId(), LEVERAGERATIO_EQ, updatedLeverageRatio, USDVALUE_EQ, usdValue,
          " usdPositionValue=", usdNotionalPositionValue, " usdMarginMaintValue=", usdMarginMaintValue, " usdMarginRequiredValue=",
          usdMarginRequiredValue, " user.getUsdValue() =", user.getUsdValue(), USER_EQ, user);
    }

    // check if user needs to be liquidated
    isLiquidationCheck(user, usdMarginMaintValue, usdValue, updatedLeverageRatio, usdMarginableValue);
  }

  private static final void isLiquidationCheck(final User user, final double usdMarginMaintValue, final double usdValue,
      final double leverageRatio, final double usdMarginableValue) {
    // if below threshold, liquidate using
    // riskToMatcherQueue
    // user.getUsdValue() < 0 ??

    // Checking for leverageRatio >= 100 has been disabled in order to address
    if (LIQUIDATON_MODE
        && (usdMarginableValue < 0 || (usdMarginMaintValue > 0 && usdMarginMaintValue >= usdMarginableValue * NAV_FEE_OFFSET)
        /* || leverageRatio >= 100 */ || leverageRatio < 0)) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_12, VERBOSE_AUTOLIQUIDATE_MARGIN_CALL_TRIGGERED_USER_EQ, user, VALUE_EQ, user.getUsdValue(),
            USDMARGINMAINTVALUE_EQ, usdMarginMaintValue, USDVALUE_EQ, usdValue, LEVERAGERATIO_EQ, leverageRatio, AUTOLIQUIDATIONSTATE_EQ,
            user.getAutoLiquidationState().get(), USDMARGINABLEVALUE_EQ, usdMarginableValue);
      }

      // skip if user is insuranceUser
      final User insuranceUser = InsuranceState.getUser();
      if (insuranceUser != null && user.getId() == insuranceUser.getId())
        return;
      // skip if user is exchangeUser
      final User exchangeUser = UserCache.getExchangeUser();
      if (exchangeUser != null && user.getId() == exchangeUser.getId())
        return;
      // skip if liquidation already just occured with the ADL_COOLOFF_TIME, default to 2 seconds
      if (user.getLastLiquidationTime() + ADL_COOLOFF_TIME > System.currentTimeMillis()) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_14, VERBOSE_AUTOLIQUIDATE_MARGIN_CALL_TRIGGERED_USER2_EQ, user, VALUE_EQ, user.getUsdValue(),
              USDMARGINMAINTVALUE_EQ, usdMarginMaintValue, USDVALUE_EQ, usdValue, LEVERAGERATIO_EQ, leverageRatio, AUTOLIQUIDATIONSTATE_EQ,
              user.getAutoLiquidationState().get(), USDMARGINABLEVALUE_EQ, usdMarginableValue, LASTLIQUIDATIONTIME_EQ,
              user.getLastLiquidationTime(), TIMESTAMP_EQ, System.currentTimeMillis());
        }
        return;
      }

      // autoliquidate
      if (user.getAutoLiquidationState().compareAndSet(0, 1)) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_18, VERBOSE_AUTOLIQUIDATE_MARGIN_CALL_TRIGGERED_USER3_EQ, user, VALUE_EQ, user.getUsdValue(),
              USDMARGINMAINTVALUE_EQ, usdMarginMaintValue, USDVALUE_EQ, usdValue, LEVERAGERATIO_EQ, leverageRatio, AUTOLIQUIDATIONSTATE_EQ,
              user.getAutoLiquidationState().get(), USDMARGINABLEVALUE_EQ, usdMarginableValue, LASTLIQUIDATIONTIME_EQ,
              user.getLastLiquidationTime(), TIMESTAMP_EQ, System.currentTimeMillis());
        }
        user.setLastLiquidationTime(System.currentTimeMillis());
        riskToAutoLiquidatorQueue.addGuaranteed(user);
      }
    }
  }

  // must be called by matching thread
  private static final boolean checkReduceOnlyOrder(final Order order, final int referencePrice, final Position position) {
    // if no position, reject reduce only
    if (position.getQuantity() == 0) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_2, ">> reject REDUCE_ONLY, no position order=", order);
      }
      return false;
    }

    // if already long, reject BUY reduce order
    if (Side.BUY == order.getSide() && position.getQuantity() > 0) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_2, ">> reject REDUCE_ONLY BUY, no position order=", order);
      }
      return false;
    }

    // if already short, reject SELL reduce order
    if (Side.SELL == order.getSide() && position.getQuantity() < 0) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_2, ">> reject REDUCE_ONLY SELL, no position order=", order);
      }
      return false;
    }
    return true;
  }

  // must be called by matching thread
  public final boolean checkOrder(final Order order, final int referencePrice) {
    try {
      final User user = order.getUser();
      if (user.getAutoLiquidationState().get() == 1 && !(order instanceof LiquidationOrder))
        return false;

      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());
      double usdMark = instrumentPair.getIndexFeedUsdMark();
      if (usdMark == 0) {
        usdMark = instrumentPair.getOrderBook().getUsdMark();
      }

      if (Context.isDebugLogRisk() && user.getId() == 18) {
        LOGGER.debug(LOG_FMT_20, ">>> checkOrder1, requiredMarginChange=", user.getUsdMarginRequiredValue(), GETQUANTITYLONG_EQ,
            order.getQuantityLong(), OPENORDERCOUNT_EQ, user.getOpenOrderCount(), USDOPENORDERSVALUE_EQ,
            user.getUsdMaxExposurePositionAndOpenOrdersValue(), ORDER_EQ, order, USERID_EQ, user.getId(), INSTRUMENT_PAIR_EQ,
            instrumentPair, INSTRUMENT_PAIR_EQ, instrumentPair, USER_GETFEETIER_EQ, user.getFeeTier(), USDMARK_EQ, usdMark);
      }

      // calc open orders required
      Position position = user.getPosition(order.getSecurityId());
      if (position == null)
        position = user.setPosition(order.getSecurityId(), 0, null);
      final UserOpenOrdersByPair userOpenOrdersByPair = position.getUserOpenOrdersByPair();

      if (order.isReduceOnly() && !checkReduceOnlyOrder(order, referencePrice, position)) {
        return false;
      }

      if (!userOpenOrdersByPair.add(order, referencePrice) && REJECT_MODE) {
        return false;
      }
      userOpenOrdersByPair.calcNotionalRequiredMargin(usdMark);

      updateRisk(user, null);

      // reject order if UsdMarginRequiredValue() >= user.getUsdValue()
      if (user.getUsdMarginRequiredValue() >= user.getUsdMarginableValue() && REJECT_MODE) {
        // if its the only closing order, then allow it
        if (Side.SELL == order.getSide() && (position.getQuantity() - userOpenOrdersByPair.getTotalAsksQuantity() >= 0)) {
          // sell to close long position
        } else if (Side.BUY == order.getSide() && (userOpenOrdersByPair.getTotalBidsQuantity() + position.getQuantity() <= 0)) {
          // buy to close short position
        } else {
          userOpenOrdersByPair.remove(order);
          userOpenOrdersByPair.calcNotionalRequiredMargin(usdMark);
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(LOG_FMT_20, "precheck order rejected, usdMarginRequiredValue=", user.getUsdMarginRequiredValue(),
                GETQUANTITYLONG_EQ, order.getQuantityLong(), OPENORDERCOUNT_EQ, user.getOpenOrderCount(), USDOPENORDERSVALUE_EQ,
                user.getUsdMaxExposurePositionAndOpenOrdersValue(), ORDER_EQ, order, USERID_EQ, user.getId(), INSTRUMENT_PAIR_EQ,
                instrumentPair, INSTRUMENT_PAIR_EQ, instrumentPair, USER_GETFEETIER_EQ, user.getFeeTier(), USER_EQ, user, USDMARK_EQ,
                usdMark);
          }
          return false;
        }
      }

      user.incrementOpenOrderCount();

      if (Context.isDebugLogRisk() && user.getId() == 18) {
        LOGGER.debug(LOG_FMT_18, CHECKORDER2_REQUIREDMARGINCHANGE_EQ, user.getUsdMarginRequiredValue(), GETQUANTITYLONG_EQ,
            order.getQuantityLong(), OPENORDERCOUNT_EQ, user.getOpenOrderCount(), USDOPENORDERSVALUE_EQ,
            user.getUsdMaxExposurePositionAndOpenOrdersValue(), ORDER_EQ, order, USERID_EQ, user.getId(), INSTRUMENT_PAIR_EQ,
            instrumentPair, INSTRUMENT_PAIR_EQ, instrumentPair, USER_GETFEETIER_EQ, user.getFeeTier());
      }
      return true;

    } catch (Exception e) {
      LOGGER.error("error in checkOrder " + order + REFERENCEPRICE_EQ + referencePrice, e);
    }

    return false;
  }

  // must be called by matching thread
  // adds to user cache
  // open orders cache
  public final void addOrderDuringRebuild(final Order order, final int referencePrice) {
    try {
      // calc open orders required
      User user = order.getUser();
      Position position = user.getPosition(order.getSecurityId());
      if (position == null) {
        position = user.setPosition(order.getSecurityId(), 0, null);
      }

      UserOpenOrdersByPair userOpenOrdersByPair = position.getUserOpenOrdersByPair();
      userOpenOrdersByPair.add(order, referencePrice);
      user.incrementOpenOrderCount();

    } catch (Exception e) {
      LOGGER.error("error in addOrderDuringRebuild " + order + REFERENCEPRICE_EQ + referencePrice, e);
    }
  }

  // must be called from the matching engine thread
  private final boolean updateFillSell(final Order order, final int referencePrice, final long referenceQuantity,
      final ExecutionReportMessage execReport, final double quotedUsdMark, final double settleCoinUsdMark, final double quotedCoinUsdMark,
      final boolean isMaker, final Position pairPosition, final Position feePosition, final Position settlePosition, final User user,
      final Position[] positionArr, final InstrumentPair instrumentPair, final Fee fee) {
    try {
      final int SETTLE_INSTRUMENT_QUANTITY_SCALE_MULT = InstrumentCache.getSettleInstrumentQuantityScaleMult();
      if (Context.isDebugLogRisk() && 18 == user.getId()) {
        LOGGER.debug(LOG_FMT_10, UPDATEFILLSELL_ORDER_EQ, order, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ, referenceQuantity,
            SETTLECOINUSDMARK_EQ, settleCoinUsdMark, EXECREPORT_EQ, execReport);
      }
      final long origPosition = pairPosition.getQuantity();
      final double adjReferenceQuantity = MbxMath.roundToBestPrecision(referenceQuantity * instrumentPair.getQuantityScaleFactor());
      final double origPositionQuantity = MbxMath.roundToBestPrecision(origPosition * instrumentPair.getQuantityScaleFactor());

      // calc usdNotional
      final double notional = MbxMath.roundToBestPrecision(referencePrice * adjReferenceQuantity * instrumentPair.getPriceScaleFactor());
      final double usdNotional = MbxMath.roundToBestPrecision(Math.abs(notional * quotedCoinUsdMark));

      // calc usdFee
      final double usdFee = MbxMath.roundToBestPrecision(fee.calcUsdFee(usdNotional));
      long feeQuantity = 0;
      final Instrument feeInstrument = InstrumentCache.get(fee.getFeeInstrumentId());
      if (feeInstrument != null) {
        double feeInFeeInstrument = usdFee / feeInstrument.getIndexFeedUsdMark();
        feeQuantity = (long) (feeInFeeInstrument * feeInstrument.getQuantityMultiplier());

        if (Context.isDebugLogRisk() && 18 == user.getId()) {
          LOGGER.debug(LOG_FMT_8, UPDATEFILL_FEE_CALC_USDNOTIONAL_EQ, usdNotional, USD_FEE_EQ, usdFee, FEEMARK_EQ,
              feeInstrument.getIndexFeedUsdMark(), FEEQUANTITY_EQ, feeQuantity);
        }
      } else {
        if (Context.isDebugLogRisk()) {
          LOGGER.debug(LOG_FMT_2, ">>> updateFill fee calc not found for ", fee.getFeeInstrumentId());
        }
      }

      long settleQuantityChange = 0;
      final double totalOrigCostBasis = MbxMath.roundToBestPrecision(pairPosition.getUsdAvgCostBasisDouble() * origPositionQuantity);

      pairPosition.setSettleCoinUsdMark(settleCoinUsdMark);
      pairPosition.setQuotedUsdMark(quotedUsdMark);

      pairPosition.addQuantity(-referenceQuantity);
      final double newPositionQuantity = MbxMath.roundToBestPrecision(origPositionQuantity - adjReferenceQuantity);

      // sell to close
      if (origPosition >= referenceQuantity && origPosition > 0) {
        final double tradePnl =
            MbxMath.roundToBestPrecision(usdNotional - (pairPosition.getUsdAvgCostBasisDouble() * adjReferenceQuantity));
        final double settleCoinRealized = MbxMath.roundToBestPrecision(tradePnl / settleCoinUsdMark);

        settleQuantityChange = (long) (MbxMath.roundToBestPrecision(settleCoinRealized * SETTLE_INSTRUMENT_QUANTITY_SCALE_MULT));
        settlePosition.addQuantity(settleQuantityChange);
        settlePosition.addAvailableQuantity(settleQuantityChange);
        pairPosition.addUsdRealized(MbxMath.roundToBestPrecision(settleCoinRealized));

        if (Context.isDebugLogRisk() && 18 == user.getId()) {
          LOGGER.debug(LOG_FMT_22, UPDATEFILL_SELL_TO_CLOSE_ORDER_EQ, order, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ,
              referenceQuantity, TRADEPNL_EQ, tradePnl, D_SETTLECOINREALIZED_EQ, settleCoinRealized, D_SETTLEQUANTITYCHANGE_EQ,
              settleQuantityChange, ORIGPOSITIONQUANTITY_EQ, origPositionQuantity, PAIRPOSITION_GETUSDAVGCOSTBASIS_EQ,
              pairPosition.getUsdAvgCostBasisDouble(), USDNOTIONAL_EQ, usdNotional, ADJREFERENCEQUANTITY_EQ, adjReferenceQuantity,
              EXECREPORT_EQ, execReport);
        }
      } else { // open, add to short
        final double newAvgCostBasis =
            newPositionQuantity == 0 ? 0 : MbxMath.roundToBestPrecision((totalOrigCostBasis - usdNotional) / newPositionQuantity); // was

        // positive
        // for a short
        pairPosition.setUsdAvgCostBasisDouble(newAvgCostBasis);

        if (Context.isDebugLogRisk() && 18 == user.getId()) {
          long normalized = (long) (newAvgCostBasis * Position.DEFAULT_COST_BASIS_SCALE_MULT);

          LOGGER.debug(LOG_FMT_26, ">>> updateFill sell to close2 order=", order, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ,
              referenceQuantity, D_SETTLEQUANTITYCHANGE_EQ, settleQuantityChange, ORIGPOSITIONQUANTITY_EQ, origPositionQuantity,
              PAIRPOSITION_GETUSDAVGCOSTBASIS_EQ, pairPosition.getUsdAvgCostBasisDouble(), USDNOTIONAL_EQ, usdNotional,
              ADJREFERENCEQUANTITY_EQ, adjReferenceQuantity, EXECREPORT_EQ, execReport, NEWAVGCOSTBASIS_EQ, newAvgCostBasis,
              TOTALORIGCOSTBASIS_EQ, totalOrigCostBasis, USDNOTIONAL_EQ, usdNotional, NORMALIZED_EQ, normalized);
        }
      }

      double newUsdNotional = MbxMath.roundToBestPrecision(newPositionQuantity * referencePrice);
      newUsdNotional = MbxMath.roundToBestPrecision(newUsdNotional * instrumentPair.getPriceScaleFactor());
      newUsdNotional = MbxMath.roundToBestPrecision(newUsdNotional * instrumentPair.getQuoted().getIndexFeedUsdMark()); // was negative for
                                                                                                                        // a short
      final double unrealizedUsd =
          MbxMath.roundToBestPrecision(newUsdNotional - (pairPosition.getUsdAvgCostBasisDouble() * newPositionQuantity));
      pairPosition.setUsdUnrealized(unrealizedUsd);
      pairPosition.setSettleCoinUnrealized(MbxMath.roundToBestPrecision(unrealizedUsd / settleCoinUsdMark));
      pairPosition.setUsdValue(unrealizedUsd);

      feePosition.addQuantity(-feeQuantity);
      feePosition.addAvailableQuantity(-feeQuantity);
      fee.transferToExchange(feeQuantity);

      execReport.setSettleCoinUnrealized(pairPosition.getSettleCoinUnrealized());
      execReport.setSettleCoinRealized(pairPosition.getSettleCoinRealized());
      execReport.setUnrealizedUsd(pairPosition.getUsdUnrealized());
      execReport.setRealizedUsd(pairPosition.getUsdRealizedDouble());
      execReport.setAvgCostBasisUsd(pairPosition.getUsdAvgCostBasisDouble());
      execReport.setQuotedUsdMark(quotedUsdMark);
      execReport.setSettleCoinUsdMark(settleCoinUsdMark);


      execReport.setBasePositionId(order.getSecurityId());
      execReport.setBasePositionQuantity(pairPosition.getQuantity());
      execReport.setBasePositionQuantityChange(-referenceQuantity);

      execReport.setQuotedPositionId(instrumentPair.getQuotedId());
      execReport.setQuotedPositionQuantity(0);
      // assume price is in usd, precision=2
      execReport.setQuotedPositionQuantityChange((referenceQuantity * referencePrice) / instrumentPair.getPriceScaleMultiplier());

      execReport.setFeePositionId(feePosition.getInstrumentId());
      execReport.setFeePositionQuantity(feePosition.getQuantity());
      execReport.setFeePositionQuantityChange(-feeQuantity);
      execReport.setPaidToInsurance(fee.isPaidToInsurance());


      execReport.setSettlePositionId(SETTLE_INSTRUMENT_ID);
      execReport.setSettlePositionQuantity(settlePosition.getQuantity());
      execReport.setSettlePositionQuantityChange(settleQuantityChange);

      execReport.buildBalanceAdminMessage(); // sets the current balances from the matching thread

      if (Context.isDebugLogRisk() && 18 == user.getId()) {
        LOGGER.debug(LOG_FMT_10, UPDATEFILL_SELL_ORDER_EQ, order, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ,
            referenceQuantity, SETTLEQUANTITYCHANGE_EQ, settleQuantityChange, EXECREPORT_EQ, execReport);
      }

      // copy positions to execReport after updateFill
      user.copySetPositionArr(execReport);
      matcherToPublisherQueue.addGuaranteed(execReport);
      return true;
    } catch (Exception e) {
      LOGGER.error("error in updateFill " + order, e);
    }

    return false;
  }

  // must be called from the matching engine thread
  private final boolean updateFillBuy(final Order order, final int referencePrice, final long referenceQuantity,
      final ExecutionReportMessage execReport, final double quotedUsdMark, final double settleCoinUsdMark, final double quotedCoinUsdMark,
      final boolean isMaker, final Position pairPosition, final Position feePosition, final Position settlePosition, final User user,
      final Position[] positionArr, final InstrumentPair instrumentPair, final Fee fee) {
    try {
      final int SETTLE_INSTRUMENT_QUANTITY_SCALE_MULT = InstrumentCache.getSettleInstrumentQuantityScaleMult();
      if (Context.isDebugLogRisk() && 18 == user.getId()) {
        LOGGER.debug(LOG_FMT_10, UPDATEFILL_BUY_ORDER_EQ, order, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ, referenceQuantity,
            SETTLECOINUSDMARK_EQ, settleCoinUsdMark, EXECREPORT_EQ, execReport);
      }
      final long origPosition = pairPosition.getQuantity();

      final double adjReferenceQuantity = MbxMath.roundToBestPrecision(referenceQuantity * instrumentPair.getQuantityScaleFactor());
      final double origPositionQuantity = MbxMath.roundToBestPrecision(origPosition * instrumentPair.getQuantityScaleFactor());

      // calc usdNotional
      final double notional = MbxMath.roundToBestPrecision(referencePrice * adjReferenceQuantity * instrumentPair.getPriceScaleFactor());
      final double usdNotional = MbxMath.roundToBestPrecision(Math.abs(notional * quotedCoinUsdMark));

      // calc usdFee
      final double usdFee = MbxMath.roundToBestPrecision(fee.calcUsdFee(usdNotional));
      long feeQuantity = 0;
      final Instrument feeInstrument = InstrumentCache.get(fee.getFeeInstrumentId());
      if (feeInstrument != null) {
        double feeInFeeInstrument = usdFee / feeInstrument.getIndexFeedUsdMark();
        feeQuantity = (long) (feeInFeeInstrument * feeInstrument.getQuantityMultiplier());

        if (Context.isDebugLogRisk() && 18 == user.getId()) {
          LOGGER.debug(LOG_FMT_8, UPDATEFILL_FEE_CALC_USDNOTIONAL_EQ, usdNotional, USD_FEE_EQ, usdFee, FEEMARK_EQ,
              feeInstrument.getIndexFeedUsdMark(), FEEQUANTITY_EQ, feeQuantity);
        }
      } else {
        if (Context.isDebugLogRisk()) {
          LOGGER.debug(LOG_FMT_2, ">>> updateFill fee calc not found for ", fee.getFeeInstrumentId());
        }
      }

      long settleQuantityChange = 0;
      final double totalOrigCostBasis = MbxMath.roundToBestPrecision(pairPosition.getUsdAvgCostBasisDouble() * origPositionQuantity);

      pairPosition.setSettleCoinUsdMark(settleCoinUsdMark);
      pairPosition.setQuotedUsdMark(quotedUsdMark);
      pairPosition.addQuantity(referenceQuantity);
      final double newPositionQuantity = MbxMath.roundToBestPrecision(adjReferenceQuantity + origPositionQuantity);

      // buy to close
      if (origPosition <= referenceQuantity && origPosition < 0) {
        final double tradePnl =
            MbxMath.roundToBestPrecision((pairPosition.getUsdAvgCostBasisDouble() * adjReferenceQuantity) - usdNotional);
        final double settleCoinRealized = MbxMath.roundToBestPrecision(tradePnl / settleCoinUsdMark);

        settleQuantityChange = (long) (MbxMath.roundToBestPrecision(settleCoinRealized * SETTLE_INSTRUMENT_QUANTITY_SCALE_MULT));
        settlePosition.addQuantity(settleQuantityChange);
        settlePosition.addAvailableQuantity(settleQuantityChange);
        pairPosition.addUsdRealized(MbxMath.roundToBestPrecision(settleCoinRealized));

        if (Context.isDebugLogRisk() && 18 == user.getId()) {
          LOGGER.debug(LOG_FMT_22, ">>> updateFill buy to close order=", order, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ,
              referenceQuantity, TRADEPNL_EQ, tradePnl, SETTLECOINREALIZED_EQ, settleCoinRealized, SETTLEQUANTITYCHANGE_EQ,
              settleQuantityChange, ORIGPOSITIONQUANTITY_EQ, origPositionQuantity, PAIRPOSITION_GETUSDAVGCOSTBASIS_EQ,
              pairPosition.getUsdAvgCostBasisDouble(), USDNOTIONAL_EQ, usdNotional, ADJREFERENCEQUANTITY_EQ, adjReferenceQuantity,
              EXECREPORT_EQ, execReport);
        }
      } else {
        final double newAvgCostBasis =
            MbxMath.roundToBestPrecision(newPositionQuantity == 0 ? 0 : (totalOrigCostBasis + usdNotional) / newPositionQuantity);
        pairPosition.setUsdAvgCostBasisDouble(newAvgCostBasis);
      }

      double newUsdNotional = MbxMath.roundToBestPrecision(newPositionQuantity * referencePrice);
      for (int i = 0; i < instrumentPair.getPriceScale(); i++)
        newUsdNotional = newUsdNotional * 0.1;
      newUsdNotional = MbxMath.roundToBestPrecision(newUsdNotional * instrumentPair.getQuoted().getIndexFeedUsdMark());

      final double unrealizedUsd =
          MbxMath.roundToBestPrecision(newUsdNotional - (pairPosition.getUsdAvgCostBasisDouble() * newPositionQuantity)); // using the trade
      // price
      // instead of
      // mark here
      pairPosition.setUsdUnrealized(unrealizedUsd);
      pairPosition.setSettleCoinUnrealized(MbxMath.roundToBestPrecision(unrealizedUsd / settleCoinUsdMark));
      pairPosition.setUsdValue(unrealizedUsd);

      feePosition.addQuantity(-feeQuantity);
      feePosition.addAvailableQuantity(-feeQuantity);
      fee.transferToExchange(feeQuantity);

      execReport.setSettleCoinUnrealized(pairPosition.getSettleCoinUnrealized());
      execReport.setSettleCoinRealized(pairPosition.getSettleCoinRealized());
      execReport.setUnrealizedUsd(pairPosition.getUsdUnrealized());
      execReport.setRealizedUsd(pairPosition.getUsdRealizedDouble());
      execReport.setAvgCostBasisUsd(pairPosition.getUsdAvgCostBasisDouble());
      execReport.setQuotedUsdMark(quotedUsdMark);
      execReport.setSettleCoinUsdMark(settleCoinUsdMark);

      execReport.setBasePositionId(order.getSecurityId());
      execReport.setBasePositionQuantity(pairPosition.getQuantity());
      execReport.setBasePositionQuantityChange(referenceQuantity);

      execReport.setQuotedPositionId(instrumentPair.getQuotedId());
      execReport.setQuotedPositionQuantity(0);
      // assume price is in usd, precision=2
      execReport.setQuotedPositionQuantityChange(-(referenceQuantity * referencePrice) / instrumentPair.getPriceScaleMultiplier());

      execReport.setFeePositionId(feePosition.getInstrumentId());
      execReport.setFeePositionQuantity(feePosition.getQuantity());
      execReport.setFeePositionQuantityChange(-feeQuantity);
      execReport.setPaidToInsurance(fee.isPaidToInsurance());

      execReport.setSettlePositionId(SETTLE_INSTRUMENT_ID);
      execReport.setSettlePositionQuantity(settlePosition.getQuantity());
      execReport.setSettlePositionQuantityChange(settleQuantityChange);

      execReport.buildBalanceAdminMessage(); // sets the current balances from the matching thread

      if (Context.isDebugLogRisk() && 18 == user.getId()) {
        LOGGER.debug(LOG_FMT_10, ">>> updateFill BUY order=", order, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ,
            referenceQuantity, SETTLEQUANTITYCHANGE_EQ, settleQuantityChange, EXECREPORT_EQ, execReport);
      }

      // copy positions to execReport after updateFill
      user.copySetPositionArr(execReport);
      matcherToPublisherQueue.addGuaranteed(execReport);

      // add trade to chart for stats
      instrumentPair.getTradeHistory().addToChart(System.currentTimeMillis(), referencePrice, referenceQuantity);
      return true;
    } catch (Exception e) {
      LOGGER.error("error in updateFill " + order, e);
    }
    return false;
  }

  // must be called from the matching engine thread
  public final boolean updateFill(final Order order, final int referencePrice, final long referenceQuantity,
      final ExecutionReportMessage execReport, final double quotedUsdMark, final double settleCoinUsdMark, final double quotedCoinUsdMark,
      final boolean isMaker, final Order causingMessage, final int counterpartyId) {
    try {
      if (Context.isDebugLogRisk()) {
        LOGGER.debug(LOG_FMT_10, ">>> updateFill order=", order, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ, referenceQuantity,
            SETTLECOINUSDMARK_EQ, settleCoinUsdMark, EXECREPORT_EQ, execReport);
      }

      final User user = order.getUser();

      final Position[] positionArr = user.getPositionArr();
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());

      // get fee
      Fee fee = null;
      if (user.isUseDiscountFeesCoin()) {
        final Instrument instrument = InstrumentCache.get(Context.getDiscountFeesInstrumentId());
        if (instrument != null && instrument.getIndexFeedUsdMark() > 0) {
          final Position feePosition = positionArr[instrument.getId()];
          if (feePosition.getQuantity() > 0) {
            final double notional = MbxMath.roundToBestPrecision(
                instrumentPair.getPriceScaleFactor() * referencePrice * instrumentPair.getQuantityScaleFactor() * referenceQuantity);
            final double feeQuantityRequired = MbxMath.roundToBestPrecision(notional * .01 / instrument.getIndexFeedUsdMark());
            final double feePositionQuantity =
                MbxMath.roundToBestPrecision((double) (instrument.getQuantityScale() * feePosition.getQuantity()));
            if (feePositionQuantity >= feeQuantityRequired) {
              fee = instrumentPair.getDiscountFee(user.getFeeTier(), isMaker, causingMessage);
            }
          }
        }
      }

      if (fee == null)
        fee = instrumentPair.getFee(user.getFeeTier(), isMaker, causingMessage);

      Position pairPosition = positionArr[order.getSecurityId()];
      Position feePosition = positionArr[fee.getFeeInstrumentId()];
      Position settlePosition = positionArr[SETTLE_INSTRUMENT_ID];

      if (settlePosition == null) {
        settlePosition = Position.set(PositionMatchThreadObjectPool.get(), user, SETTLE_INSTRUMENT_ID, 0, 0);
        positionArr[SETTLE_INSTRUMENT_ID] = settlePosition;
      }
      if (feePosition == null) {
        feePosition = Position.set(PositionMatchThreadObjectPool.get(), user, fee.getFeeInstrumentId(), 0, 0);
        positionArr[fee.getFeeInstrumentId()] = feePosition;
      }
      if (pairPosition == null) {
        pairPosition = Position.set(PositionMatchThreadObjectPool.get(), user, order.getSecurityId(), 0, 0);
        positionArr[order.getSecurityId()] = pairPosition;
      }
      final long origPosition = pairPosition.getQuantity();

      // calc open orders required
      double usdMark = instrumentPair.getIndexFeedUsdMark();
      if (usdMark == 0) {
        usdMark = instrumentPair.getOrderBook().getUsdMark();
      }
      final UserOpenOrdersByPair userOpenOrdersByPair = pairPosition.getUserOpenOrdersByPair();

      if (ExecType.CALCULATED != execReport.getExecType())
        userOpenOrdersByPair.update(order, referencePrice, referenceQuantity);

      userOpenOrdersByPair.calcNotionalRequiredMargin(usdMark);

      if (order.getQuantityLong() <= 0 && ExecType.CALCULATED != execReport.getExecType()) {
        if (user.decrementOpenOrderCount() <= 0) {
          user.setUsdMaxExposurePositionAndOpenOrdersValue(user.getUsdNotionalPositionValue());
          user.setUsdOpenOrdersRequiredValue(0);
          user.setOpenOrderCount(0);
        }
      }

      if (Context.isDebugLogRisk()) {
        LOGGER.debug(LOG_FMT_20, ">>> updateFill origPosition=", origPosition, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ,
            referenceQuantity, SETTLECOINUSDMARK_EQ, settleCoinUsdMark, GETQUANTITYLONG_EQ, order.getQuantityLong(), OPENORDERCOUNT_EQ,
            user.getOpenOrderCount(), USDOPENORDERSVALUE_EQ, user.getUsdMaxExposurePositionAndOpenOrdersValue(), ORDER_EQ, order,
            EXECREPORT_EQ, execReport, USERID_EQ, user.getId());

        if (18 == user.getId()) {
          LOGGER.debug(LOG_FMT_14, ">>> updateFill fee userTier=", user.getFeeTier(), ORDER_EQ, order, REFERENCEPRICE_EQ, referencePrice,
              REFERENCEQUANTITY_EQ, referenceQuantity, SETTLECOINUSDMARK_EQ, settleCoinUsdMark, FEE_EQ, fee, FEEPOSITION_EQ, feePosition);
        }
      }

      boolean status = false;
      switch (order.getType()) {
        case SELL_LIMIT:
        case SELL_MARKET:
        case SELL_SELECT:
        case STOP_SELL_LIMIT:
          if (origPosition > 0 && referenceQuantity > origPosition) { // sell close and open new short
            final long diff = referenceQuantity - origPosition;
            if (LOGGER.isDebugEnabled() && Context.isDebugLogRisk()) {
              LOGGER.debug(LOG_FMT_6, ">>> updateFill sell close and open new short, origPosition=", origPosition, REFERENCEQUANTITY_EQ,
                  referenceQuantity, DIFF_EQ, diff);
            }

            updateFillSell(order, referencePrice, origPosition, execReport, quotedUsdMark, settleCoinUsdMark, quotedCoinUsdMark, isMaker,
                pairPosition, feePosition, settlePosition, user, positionArr, instrumentPair, fee);

            final ExecutionReportMessage execOpen = ExecutionReportMessage.createTradeExecutionReport(order, instrumentPair,
                execReport.getLastPx(), execReport.getLastPxScale(), execReport.getLastQty(), execReport.getLastQtyScale(),
                execReport.getExecId(), execReport.getSecondaryExecId(), causingMessage, counterpartyId, true, execReport.getAssetId(),
                execReport.getTokenId(), execReport.getSelectId());
            status = updateFillSell(order, referencePrice, diff, execOpen, quotedUsdMark, settleCoinUsdMark, quotedCoinUsdMark, isMaker,
                pairPosition, feePosition, settlePosition, user, positionArr, instrumentPair, fee);
          } else {
            status = updateFillSell(order, referencePrice, referenceQuantity, execReport, quotedUsdMark, settleCoinUsdMark,
                quotedCoinUsdMark, isMaker, pairPosition, feePosition, settlePosition, user, positionArr, instrumentPair, fee);
          }
          break;
        case BUY_LIMIT:
        case BUY_MARKET:
        case BUY_SELECT:
        case STOP_BUY_LIMIT:
          if (origPosition < 0 && (origPosition + referenceQuantity) > 0) { // buy close and open long
            final long diff = Math.abs(referenceQuantity) - Math.abs(origPosition);
            if (Context.isDebugLogRisk() && 18 == user.getId()) {
              LOGGER.debug(LOG_FMT_6, ">>> updateFill buy close and open long, origPosition=", origPosition, REFERENCEQUANTITY_EQ,
                  referenceQuantity, DIFF_EQ, diff);
            }

            updateFillBuy(order, referencePrice, Math.abs(origPosition), execReport, quotedUsdMark, settleCoinUsdMark, quotedCoinUsdMark,
                isMaker, pairPosition, feePosition, settlePosition, user, positionArr, instrumentPair, fee);

            final ExecutionReportMessage execOpen = ExecutionReportMessage.createTradeExecutionReport(order, instrumentPair,
                execReport.getLastPx(), execReport.getLastPxScale(), execReport.getLastQty(), execReport.getLastQtyScale(),
                execReport.getExecId(), execReport.getSecondaryExecId(), causingMessage, counterpartyId, true, execReport.getAssetId(),
                execReport.getTokenId(), execReport.getSelectId());

            status = updateFillBuy(order, referencePrice, diff, execOpen, quotedUsdMark, settleCoinUsdMark, quotedCoinUsdMark, isMaker,
                pairPosition, feePosition, settlePosition, user, positionArr, instrumentPair, fee);
          } else {
            status = updateFillBuy(order, referencePrice, referenceQuantity, execReport, quotedUsdMark, settleCoinUsdMark,
                quotedCoinUsdMark, isMaker, pairPosition, feePosition, settlePosition, user, positionArr, instrumentPair, fee);
          }
          break;
        default:
      }

      updateRisk(user, null);
      return status;

    } catch (Exception e) {
      LOGGER.error("error in updateFill " + order, e);
    }

    return false;
  }

  // must be called from the matching engine thread
  public final boolean updateCancel(final Order order) {
    try {
      final User user = order.getUser();
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());

      user.decrementOpenOrderCount();

      // calc open orders required
      double usdMark = instrumentPair.getIndexFeedUsdMark();
      if (usdMark == 0) {
        usdMark = instrumentPair.getOrderBook().getUsdMark();
      }
      Position postion = user.getPosition(order.getSecurityId());
      if (postion == null)
        postion = user.setPosition(order.getSecurityId(), 0, null);
      final UserOpenOrdersByPair userOpenOrdersByPair = postion.getUserOpenOrdersByPair();
      userOpenOrdersByPair.remove(order);
      userOpenOrdersByPair.calcNotionalRequiredMargin(usdMark);

      if (Context.isDebugLogRisk() && user.getId() == 18) {
        LOGGER.debug(LOG_FMT_18, ">>> updateCancel, getUsdMarginRequiredValue=", user.getUsdMarginRequiredValue(), GETQUANTITYLONG_EQ,
            order.getQuantityLong(), OPENORDERCOUNT_EQ, user.getOpenOrderCount(), USDOPENORDERSVALUE_EQ,
            user.getUsdMaxExposurePositionAndOpenOrdersValue(), ORDER_EQ, order, USERID_EQ, user.getId(), INSTRUMENT_PAIR_EQ,
            instrumentPair, USER_GETFEETIER_EQ, user.getFeeTier(), USEROPENORDERSBYPAIR_EQ, userOpenOrdersByPair);
      }

      updateRisk(user, null);

      if (user.getOpenOrderCount() <= 0) {
        user.setUsdMaxExposurePositionAndOpenOrdersValue(user.getUsdNotionalPositionValue());
        user.setUsdOpenOrdersRequiredValue(0);
      }

      return true;

    } catch (Exception e) {
      LOGGER.error("error in updateCancel " + order, e);
    }

    return false;
  }


  // CalcBankruptcyPrices is somewhat duplicated code from RiskAutoLiquidationThread
  public final void updateRiskAndCalcBankruptcyPrices(final User user, final double[] usdMarkPricesToSet) {
    // update risk and get arr of mark prices used
    updateRisk(user, usdMarkPricesToSet);

    final double usdValue = user.getUsdMarginableValue(); // user.getUsdValue();
    final double positionValue = user.getUsdNotionalPositionValue();
    double lossMargin = 0;
    boolean hasEquity = false;

    if (usdValue > 0 && positionValue > 0) {
      lossMargin = Math.abs(usdValue / (positionValue));
      hasEquity = true;
    } else if (positionValue != 0) {
      lossMargin = Math.abs(usdValue / (positionValue));
    }
    if (lossMargin > 1)
      lossMargin = RiskAutoLiquidationThread.SLIPPAGE_DISCOUNT;


    final Position[] positionArr = user.getPositionArr();
    for (Position position : positionArr) { // TODO: limit loop using user.maxActivePositionIndexHint
      if (position != null && position.getQuantity() != 0 && (AssetType.ASSET != position.getAssetType())) {
        final InstrumentPair instrumentPair = InstrumentCache.getPair(position.getInstrumentId());
        if (instrumentPair != null) {
          // get price
          int bankruptPriceInt = 0;

          Side side = Side.SELL;
          if (position.getQuantity() > 0) {
            bankruptPriceInt = RiskAutoLiquidationThread.getMarkIntPrice(instrumentPair, usdMarkPricesToSet, side);
            // sell to close
            bankruptPriceInt = hasEquity ? (int) (bankruptPriceInt * (RiskAutoLiquidationThread.SLIPPAGE_PREMIUM - lossMargin))
                : (int) (bankruptPriceInt * (RiskAutoLiquidationThread.SLIPPAGE_PREMIUM + lossMargin));

          } else {
            side = Side.BUY;
            bankruptPriceInt = RiskAutoLiquidationThread.getMarkIntPrice(instrumentPair, usdMarkPricesToSet, side);
            // buy to close
            bankruptPriceInt = hasEquity ? (int) (bankruptPriceInt * (RiskAutoLiquidationThread.SLIPPAGE_DISCOUNT + lossMargin))
                : (int) (bankruptPriceInt * (RiskAutoLiquidationThread.SLIPPAGE_DISCOUNT - lossMargin));
          }
          position.setBankruptPriceInt(bankruptPriceInt);

        }
      }
    }
  }


  // must be called from the matching engine thread
  // PhysicalSettle implementation
  // only in the money options and futures can be physically settled
  // this just closes the pair, the settle amount=0
  // calc markInSettleCoin for closing the derivative
  // mark at 0 for put or call
  // mark at costBasis for future, so the net pnl=0
  public final boolean updateFillPhysicalSettle(final Order order, final int adjMarkInSettleCoin, final long referenceQuantity,
      final ExecutionReportMessage execReport, final double quotedUsdMark, final double settleCoinUsdMark, final double quotedCoinUsdMark,
      final boolean isMaker, final Order causingMessage, final int counterpartyId, final double underlyerCoinUsdMark) {
    try {
      final User user = order.getUser();
      final Position[] positionArr = user.getPositionArr();
      final Position pairPosition = positionArr[order.getSecurityId()];
      final double usdAvgCostBasisDouble = pairPosition.getUsdAvgCostBasisDouble();

      // first close the pair at 0 for option, or cost basis for future
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());

      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_10, ">>> updateFillPhysicalSettle1 markInSettleCoin=", adjMarkInSettleCoin, ", order=", order,
            REFERENCEPRICE_EQ, adjMarkInSettleCoin, ", underlyerCoinUsdMark=", underlyerCoinUsdMark, REFERENCEQUANTITY_EQ,
            referenceQuantity, SETTLECOINUSDMARK_EQ, settleCoinUsdMark, EXECREPORT_EQ, execReport);
      }
      // cash settle close the derivative pair at the adjusted mark
      updateFill(order, adjMarkInSettleCoin, referenceQuantity, execReport, quotedUsdMark, settleCoinUsdMark, quotedCoinUsdMark, false,
          causingMessage, counterpartyId);



      // onUnderlyerPhysicalSettle
      // force a trade on the underlyer
      final Instrument settleInstrument = InstrumentCache.get(instrumentPair.getBaseId()); // settle to the base, usually BTC
      InstrumentPair underlyerPair = InstrumentCache.getPairBySymbol(settleInstrument.getSymbol() + "/USD");
      if (underlyerPair == null)
        underlyerPair = InstrumentCache.getPairBySymbol(settleInstrument.getSymbol() + "/USDC");

      final OrderBook underlyerOrderBook = underlyerPair.getOrderBook();

      if (Side.BUY == order.getSide()) {
        Side side = Side.SELL;
        final long qty = underlyerPair.adjustQuantityToScale(referenceQuantity, instrumentPair.getQuantityScale());
        long price = (long) (usdAvgCostBasisDouble * underlyerPair.getPriceScaleMultiplier()); // default if not option
        if (AssetType.OPTION_PUT == instrumentPair.getAssetType()) {
          side = Side.BUY;
          price = underlyerPair.adjustPriceToScale(instrumentPair.getStrikePrice(), instrumentPair.getPriceScale());
        } else if (AssetType.OPTION_CALL == instrumentPair.getAssetType()) {
          // side = Side.SELL;
          price = underlyerPair.adjustPriceToScale(instrumentPair.getStrikePrice(), instrumentPair.getPriceScale());
        }
        underlyerOrderBook.onUnderlyerPhysicalSettle(user, price, underlyerPair.getPriceScale(), qty, underlyerPair.getQuantityScale(),
            side);
      } else if (Side.SELL == order.getSide()) {
        Side side = Side.BUY;
        final long qty = underlyerPair.adjustQuantityToScale(referenceQuantity, instrumentPair.getQuantityScale());
        long price = (long) (usdAvgCostBasisDouble * underlyerPair.getPriceScaleMultiplier()); // default if not option
        if (AssetType.OPTION_PUT == instrumentPair.getAssetType()) {
          side = Side.SELL;
          price = underlyerPair.adjustPriceToScale(instrumentPair.getStrikePrice(), instrumentPair.getPriceScale());
        } else if (AssetType.OPTION_CALL == instrumentPair.getAssetType()) {
          // side = Side.BUY;
          price = underlyerPair.adjustPriceToScale(instrumentPair.getStrikePrice(), instrumentPair.getPriceScale());
        }
        underlyerOrderBook.onUnderlyerPhysicalSettle(user, price, underlyerPair.getPriceScale(), qty, underlyerPair.getQuantityScale(),
            side);
      }

      updateRisk(user, null);
      return true;
    } catch (Exception e) {
      LOGGER.error("error in updateFillPhysicalSettle " + order, e);
    }

    return false;
  }

  @Override
  public boolean checkOrderNoValidation(final Order order, final int referencePrice) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean updateCancelNoValidation(Order order) {
    // TODO Auto-generated method stub
    return false;
  }
}
