package com.solfini.preordercheck;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.Fee;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.AssetGroupCache;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.pool.PositionMatchThreadObjectPool;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserOpenOrdersByPair;
import com.solfini.util.MbxMath;

import java.util.Set;

/**
 *
 * @author Chris Mack
 *
 */
public class CashPreOrderCheck implements PreOrderCheck, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(CashPreOrderCheck.class);

  private static final boolean IS_MAKER_DEFAULT = false;
  private static final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
  public static final int SETTLE_INSTRUMENT_ID = 1;

  // convert pair quantity scale to base instrument quantity scale
  public static final long normalizeQuantity(final InstrumentPair instrumentPair, long quantity) {
    final Instrument instrument = instrumentPair.getBase();

    if (instrumentPair.getQuantityScale() == instrument.getQuantityScale())
      return quantity;
    if (instrumentPair.getQuantityScale() > instrument.getQuantityScale()) {
      for (int i = 0; i < instrumentPair.getQuantityScale() - instrument.getQuantityScale(); i++) {
        quantity = quantity / 10;
      }
    } else {
      for (int i = 0; i < instrument.getQuantityScale() - instrumentPair.getQuantityScale(); i++) {
        quantity = quantity * 10;
      }
    }
    return quantity;
  }

  // convert pair price scale to quoted instrument quantity scale
  public static final long normalizePrice(final InstrumentPair instrumentPair, long price) {
    final Instrument instrument = instrumentPair.getQuoted();

    if (instrumentPair.getPriceScale() == instrument.getQuantityScale())
      return price;
    if (instrumentPair.getPriceScale() > instrument.getQuantityScale()) {
      for (int i = 0; i < instrumentPair.getPriceScale() - instrument.getQuantityScale(); i++) {
        price = price / 10;
      }
    } else {
      for (int i = 0; i < instrument.getQuantityScale() - instrumentPair.getPriceScale(); i++) {
        price = price * 10;
      }
    }
    return price;
  }

  public boolean checkOrder(final Order order, final int referencePrice) {
    try {
      final User user = order.getUser();
      final Position[] positionArr = user.getPositionArr();
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());
      final Fee fee = instrumentPair.getFee(user.getFeeTier(), IS_MAKER_DEFAULT, null);
      final long normalizedQuantityLong = normalizeQuantity(instrumentPair, order.getQuantityLong());
      final double quotedCoinUsdMark = instrumentPair.getQuoted().getIndexFeedUsdMark();

      // calc usdNotional
      final double adjReferenceQuantity = MbxMath.roundToBestPrecision(order.getQuantityLong() * instrumentPair.getQuantityScaleFactor());
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
      order.setFeeEstimatedQuantity(feeQuantity);
      order.setFeeAccumulatedQuantity(0);
      order.setAvailableAccumulatedQuantity(0);

      final Position position = user.getPosition(instrumentPair.getId());
      final UserOpenOrdersByPair userOpenOrdersByPair = position.getUserOpenOrdersByPair();
      if (order.getSide() == Side.BUY && user.getUsdMarginableValue() > 0) {
        if (user.getUsdMarginableValue() - usdNotional < user.getUsdMarginRequiredValue()) {
          LOGGER.info(LOG_FMT_2, ">>> not enough margin usdNotional=", usdNotional, USDMARGINABLEVALUE_EQ, user.getUsdMarginableValue(),
              USDMARGINREQUIREDVALUE_EQ, user.getUsdMarginRequiredValue(), ORDER_EQ, order);
          return false;
        }
      }
      if (!userOpenOrdersByPair.add(order, referencePrice)) {
        return false;
      }
      user.incrementOpenOrderCount();

      switch (order.getType()) {
        case SELL_LIMIT:
        case SELL_MARKET:
        case SELL_SELECT:
        case STOP_SELL_LIMIT:
          final Position basePosition = positionArr[instrumentPair.getBaseId()];

          if (basePosition != null && basePosition.getInstrumentId() == fee.getFeeInstrumentId()) {
            if (basePosition.subtractAvailableQuantity(normalizedQuantityLong + feeQuantity)) {
              order.setAvailableEstimatedQuantity(normalizedQuantityLong);
              return true;
            }
          } else if (basePosition != null && instrumentPair.getQuotedId() == fee.getFeeInstrumentId()) {
            // no need to block off for fees as we can recover it from proceeds of sale
            if (basePosition.subtractAvailableQuantity(normalizedQuantityLong)) {
              order.setAvailableEstimatedQuantity(normalizedQuantityLong);
              order.setFeeEstimatedQuantity(0);
              return true;
            }
          } else {
            final Position feePosition = positionArr[fee.getFeeInstrumentId()];
            if (basePosition != null && basePosition.subtractAvailableQuantity(normalizedQuantityLong)
                && (feeQuantity == 0 || feePosition.subtractAvailableQuantity(feeQuantity))) {
              order.setAvailableEstimatedQuantity(normalizedQuantityLong);
              return true;
            }
          }
          break;
        case BUY_LIMIT:
        case BUY_MARKET:
        case BUY_SELECT:
        case STOP_BUY_LIMIT:
          final Position quotedPosition = positionArr[instrumentPair.getQuotedId()];
          long amount = normalizedQuantityLong * order.getMarginCheckReferencePrice();
          for (int i = 0; i < instrumentPair.getBase().getQuantityScale(); i++)
            amount = amount / 10;

          final long normalizedAmountLong = normalizePrice(instrumentPair, amount);

          if (quotedPosition != null && quotedPosition.getInstrumentId() == fee.getFeeInstrumentId()) {
            if (quotedPosition.subtractAvailableQuantity(normalizedAmountLong + feeQuantity)) {
              order.setAvailableEstimatedQuantity(normalizedAmountLong);
              return true;
            }
          } else {
            final Position feePosition = positionArr[fee.getFeeInstrumentId()];
            if ((quotedPosition != null) && (quotedPosition.getAvailableQuantity() >= normalizedAmountLong) && (feePosition != null)
                && (feeQuantity == 0 || (feePosition.getAvailableQuantity() >= feeQuantity))
                && quotedPosition.subtractAvailableQuantity(normalizedAmountLong)
                && (feeQuantity == 0 || feePosition.subtractAvailableQuantity(feeQuantity))) {
              order.setAvailableEstimatedQuantity(normalizedAmountLong);
              return true;
            }
          }

          break;
        default:
      }

      user.decrementOpenOrderCount();
      userOpenOrdersByPair.remove(order);

      // debug only
      final Position basePosition = positionArr[instrumentPair.getBaseId()];
      final Position quotedPosition = positionArr[instrumentPair.getQuotedId()];
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_10, ">>> checkOrder qtyLong=", order.getQuantityLong(), "order.getPriceInt()=", order.getPriceInt(),
            REFERENCEPRICE_EQ, referencePrice, ", basePosition=", basePosition, ", quotedPosition=", quotedPosition);
      }
      LOGGER.info(">>> checkOrder: order: " + order);
      LOGGER.info(">>> checkOrder: instrumentPair" + instrumentPair);
      LOGGER.info(">>> checkOrder: basePosition" + basePosition);
      LOGGER.info(">>> checkOrder: quotedPosition" + quotedPosition);

    } catch (Exception e) {
      LOGGER.error("error in checkOrder " + order + REFERENCEPRICE_EQ + referencePrice, e);
    }

    return false;
  }


  public boolean updateFill(final Order order, final int referencePrice, final long referenceQuantity,
      final ExecutionReportMessage execReport, final double quotedUsdMark, final double settleCoinUsdMark, final double quotedCoinUsdMark,
      final boolean isMaker, final Order causingMessage, final int counterpartyId) {

    // System.out.println(" >>> FILL orderId=" + order.getOrderId() + ", price=" + referencePrice + ", quantity=" + referenceQuantity);

    try {
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());
      final long normalizedQuantityLong = normalizeQuantity(instrumentPair, referenceQuantity);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_10, ">>> updateFill order=", order, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ, referenceQuantity,
            ", normalizedQuantityLong=", normalizedQuantityLong, EXECREPORT_EQ, execReport);
      }
      final User user = order.getUser();
      final Position[] positionArr = user.getPositionArr();
      final Fee fee = instrumentPair.getFee(user.getFeeTier(), isMaker, null);

      // calc usdNotional
      final double adjReferenceQuantity = MbxMath.roundToBestPrecision(referenceQuantity * instrumentPair.getQuantityScaleFactor());
      final double notional = MbxMath.roundToBestPrecision(referencePrice * adjReferenceQuantity * instrumentPair.getPriceScaleFactor());
      final double usdNotional = MbxMath.roundToBestPrecision(Math.abs(notional * quotedCoinUsdMark));

      // calc usdFee
      final double usdFee = MbxMath.roundToBestPrecision(fee.calcUsdFee(usdNotional));
      long feeQuantity = 0;
      final Instrument feeInstrument = InstrumentCache.get(fee.getFeeInstrumentId());
      if (feeInstrument != null) {
        double feeInFeeInstrument = usdFee / feeInstrument.getIndexFeedUsdMark();
        feeQuantity = (long) (feeInFeeInstrument * feeInstrument.getQuantityMultiplier());

        // never charge more than the allocated fee
        if (order.getFeeEstimatedQuantity() > 0 && feeQuantity > order.getFeeUncollectedQuantity()) {
          feeQuantity = order.getFeeUncollectedQuantity();
        }

        if (Context.isDebugLogRisk() && 18 == user.getId()) {
          LOGGER.debug(LOG_FMT_8, UPDATEFILL_FEE_CALC_USDNOTIONAL_EQ, usdNotional, USD_FEE_EQ, usdFee, FEEMARK_EQ,
              feeInstrument.getIndexFeedUsdMark(), FEEQUANTITY_EQ, feeQuantity);
        }
      } else {
        if (Context.isDebugLogRisk()) {
          LOGGER.debug(LOG_FMT_2, ">>> updateFill fee calc not found for ", fee.getFeeInstrumentId());
        }
      }

      // System.out.println(" >>> FEE from " + order.getFeeEstimatedQuantity() + " " + order.getFeeAccumulatedQuantity());
      order.incrementFeeAccumulatedQuantity(feeQuantity);
      // System.out.println(" >>> FEE to " + order.getFeeEstimatedQuantity() + " " + order.getFeeAccumulatedQuantity());

      Position feePosition = positionArr[fee.getFeeInstrumentId()];
      Position basePosition = positionArr[instrumentPair.getBaseId()];
      Position quotedPosition = positionArr[instrumentPair.getQuotedId()];

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_10, ">>> updateFill feeQuantity=", feeQuantity, FEE_EQ, fee, REFERENCEQUANTITY_EQ, referenceQuantity,
            ", normalizedQuantityLong=", normalizedQuantityLong);
      }

      if (feePosition == null) {
        feePosition = Position.set(PositionMatchThreadObjectPool.get(), user, fee.getFeeInstrumentId(), 0, 0);
        positionArr[fee.getFeeInstrumentId()] = feePosition;
      }
      if (basePosition == null) {
        basePosition = Position.set(PositionMatchThreadObjectPool.get(), user, instrumentPair.getBaseId(), 0, 0);
        positionArr[instrumentPair.getBaseId()] = basePosition;
      }
      if (quotedPosition == null) {
        quotedPosition = Position.set(PositionMatchThreadObjectPool.get(), user, instrumentPair.getQuotedId(), 0, 0);
        positionArr[instrumentPair.getQuotedId()] = quotedPosition;
      }

      long amount = normalizedQuantityLong * referencePrice;
      for (int i = 0; i < instrumentPair.getBase().getQuantityScale(); i++)
        amount = amount / 10;

      final long normalizedAmountLong = normalizePrice(instrumentPair, amount);

      final Position position = user.getPosition(instrumentPair.getId());
      final UserOpenOrdersByPair userOpenOrdersByPair = position.getUserOpenOrdersByPair();

      switch (order.getType()) {
        case SELL_LIMIT:
        case SELL_MARKET:
        case SELL_SELECT:
        case STOP_SELL_LIMIT:
          basePosition.addQuantity(-normalizedQuantityLong); // fill
          basePosition.removeAssetId(execReport.getAssetId(), execReport.getTokenId(), execReport.getGroupAssetId());
/*          if (execReport.getGroupAssetId() == 0) { // individual assets
            LOGGER.info(Constants.LOG_FMT_2, "AAE Removing from user: ", user.getId(), " assetId: ", execReport.getAssetId(),
                " tokenId: ", execReport.getTokenId(), " groupId: ",  execReport.getGroupAssetId());
            basePosition.removeAssetId(execReport.getAssetId(), execReport.getTokenId(), execReport.getGroupAssetId());
          } else { // purchase group
            final AssetGroup assetGroup = AssetGroupCache.get(execReport.getGroupAssetId());
            if (assetGroup != null) {
              final Set<long[]> assetIdSet = assetGroup.getAssetIdGroupTreeSet();
              long quantity = order.getQty();
              for (long[] assetIds : assetIdSet) {
                if (quantity > 0) {
                  LOGGER.info(Constants.LOG_FMT_2, "AAE Removing from user: ", user.getId(), " assetId: ", assetIds[0],
                      " tokenId: ", assetIds[1], " groupId: ",  execReport.getGroupAssetId(), " quantity: ", quantity);
                  basePosition.removeAssetId(assetIds[0], (int) assetIds[1], execReport.getGroupAssetId());
                } else {
                  break;
                }
                quantity--;
              }
            }
          }*/

          quotedPosition.addQuantity(normalizedAmountLong); // fill
          quotedPosition.addAvailableQuantity(normalizedAmountLong); // fill
          feePosition.addQuantity(-feeQuantity);
          if (instrumentPair.getQuotedId() == fee.getFeeInstrumentId()) {
            quotedPosition.subtractAvailableQuantity(feeQuantity);
          }
          order.incrementAvailableAccumulatedQuantity(normalizedQuantityLong);

          fee.transferToExchange(feeQuantity);

          execReport.setBasePositionId(basePosition.getInstrumentId());
          execReport.setBasePositionQuantity(basePosition.getQuantity());
          execReport.setBasePositionQuantityChange(-normalizedQuantityLong);

          execReport.setQuotedPositionId(quotedPosition.getInstrumentId());
          execReport.setQuotedPositionQuantity(quotedPosition.getQuantity());
          execReport.setQuotedPositionQuantityChange(normalizedAmountLong);

          execReport.setFeePositionId(feePosition.getInstrumentId());
          execReport.setFeePositionQuantity(feePosition.getQuantity());
          execReport.setFeePositionQuantityChange(-feeQuantity);

          execReport.setSettlePositionId(quotedPosition.getInstrumentId());
          execReport.setSettlePositionQuantity(0);
          execReport.setSettlePositionQuantityChange(0);

          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_10, UPDATEFILL_SELL_ORDER_EQ, order, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ,
                referenceQuantity, ", normalizedQuantityLong=", normalizedQuantityLong, EXECREPORT_EQ, execReport);
          }

          if (order.getQuantityLong() == 0) {
            user.decrementOpenOrderCount();
            userOpenOrdersByPair.remove(order);
            if (instrumentPair.getQuotedId() == fee.getFeeInstrumentId()) {
              quotedPosition.addAvailableQuantity(order.getFeeUncollectedQuantity());
            }
            if (basePosition != null)
              basePosition.addAvailableQuantity(order.getAvailableUncollectedQuantity());
          }

          // copy positions to execReport after updateFill
          user.copySetPositionArr(execReport);

          execReport.setFeeEstimatedQuantity(order.getFeeEstimatedQuantity());
          execReport.setFeeAccumulatedQuantity(order.getFeeAccumulatedQuantity());
          execReport.setAvailableEstimatedQuantity(order.getAvailableEstimatedQuantity());
          execReport.setAvailableAccumulatedQuantity(order.getAvailableAccumulatedQuantity());

          matcherToPublisherQueue.addGuaranteed(execReport);
          return true;
        case BUY_LIMIT:
        case BUY_MARKET:
        case BUY_SELECT:
        case STOP_BUY_LIMIT:
          basePosition.addQuantity(normalizedQuantityLong); // fill
          basePosition.addAvailableQuantity(normalizedQuantityLong); // fill
          //basePosition.addAssetId(execReport.getAssetId(), execReport.getTokenId(), execReport.getGroupAssetId());
          if (execReport.getGroupAssetId() == 0) { // individual assets
            basePosition.addAssetId(execReport.getAssetId(), execReport.getTokenId(), execReport.getGroupAssetId());
            LOGGER.info(Constants.LOG_FMT_2, "AAD Adding to user: ", user.getId(), " assetId: ", execReport.getAssetId(),
                " tokenId: ", execReport.getTokenId(), " groupId: ",  execReport.getGroupAssetId());
          } else { // purchase group
            final AssetGroup assetGroup = AssetGroupCache.get(execReport.getGroupAssetId());
            if (assetGroup != null) {
              final Set<long[]> assetIdSet = assetGroup.getAssetIdGroupTreeSet();
              long quantity = order.getQty();
              for (long[] assetIds : assetIdSet) {
                if (quantity > 0) {
                  basePosition.addAssetId(assetIds[0], (int) assetIds[1], execReport.getGroupAssetId());
                  LOGGER.info(Constants.LOG_FMT_2, "AAC Adding to user: ", user.getId(), " assetId: ", assetIds[0],
                      " tokenId: ", assetIds[1], " groupId: ",  execReport.getGroupAssetId()," quantity: ", quantity);
                } else {
                  break;
                }
                quantity--;
              }
            }
          }

          quotedPosition.addQuantity(-normalizedAmountLong); // fill

          long adjustment = normalizedQuantityLong * order.getMarginCheckReferencePrice();
          for (int i = 0; i < instrumentPair.getBase().getQuantityScale(); i++)
            adjustment = adjustment / 10;
          quotedPosition.addAvailableQuantity(normalizePrice(instrumentPair, adjustment - amount));

          feePosition.addQuantity(-feeQuantity);
          order.incrementAvailableAccumulatedQuantity(normalizedAmountLong);
          order.incrementAvailableAccumulatedQuantity(normalizePrice(instrumentPair, adjustment - amount));

          fee.transferToExchange(feeQuantity);

          // final double refNotional = MbxMath
          // .roundToBestPrecision(order.getMarginCheckReferencePrice() * adjReferenceQuantity * instrumentPair.getPriceScaleFactor());
          // final double refUsdNotional = MbxMath.roundToBestPrecision(Math.abs(refNotional * quotedCoinUsdMark));
          // final double refUsdFee = MbxMath.roundToBestPrecision(fee.calcUsdFee(refUsdNotional));
          // if (feeInstrument != null) {
          // double refFeeQuantity = refUsdFee / feeInstrument.getIndexFeedUsdMark();
          // for (int i = 0; i < feeInstrument.getQuantityScale(); i++)
          // refFeeQuantity *= 10;
          // adjustment = (long) refFeeQuantity - feeQuantity;
          // feePosition.addAvailableQuantity((long) adjustment);
          // }

          execReport.setBasePositionId(basePosition.getInstrumentId());
          execReport.setBasePositionQuantity(basePosition.getQuantity());
          execReport.setBasePositionQuantityChange(referenceQuantity);

          execReport.setQuotedPositionId(quotedPosition.getInstrumentId());
          execReport.setQuotedPositionQuantity(quotedPosition.getQuantity());
          execReport.setQuotedPositionQuantityChange(-normalizedAmountLong);

          execReport.setFeePositionId(feePosition.getInstrumentId());
          execReport.setFeePositionQuantity(feePosition.getQuantity());
          execReport.setFeePositionQuantityChange(-feeQuantity);

          execReport.setSettlePositionId(quotedPosition.getInstrumentId());
          execReport.setSettlePositionQuantity(0);
          execReport.setSettlePositionQuantityChange(0);
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_10, ">>> updateFill BUY order=", order, REFERENCEPRICE_EQ, referencePrice, REFERENCEQUANTITY_EQ,
                referenceQuantity, ", normalizedQuantityLong=", normalizedQuantityLong, EXECREPORT_EQ, execReport);
          }

          if (order.getQuantityLong() == 0) {
            user.decrementOpenOrderCount();
            userOpenOrdersByPair.remove(order);
            if (feeInstrument != null)
              feePosition.addAvailableQuantity(order.getFeeUncollectedQuantity());
            if (quotedPosition != null)
              quotedPosition.addAvailableQuantity(order.getAvailableUncollectedQuantity());
          }

          // copy positions to execReport after updateFill
          user.copySetPositionArr(execReport);

          execReport.setFeeEstimatedQuantity(order.getFeeEstimatedQuantity());
          execReport.setFeeAccumulatedQuantity(order.getFeeAccumulatedQuantity());
          execReport.setAvailableEstimatedQuantity(order.getAvailableEstimatedQuantity());
          execReport.setAvailableAccumulatedQuantity(order.getAvailableAccumulatedQuantity());

          matcherToPublisherQueue.addGuaranteed(execReport);

          // add trade to chart for stats
          try {
            instrumentPair.getTradeHistory().addToChart(System.currentTimeMillis(), referencePrice, referenceQuantity);
          } catch (Exception e) {
            LOGGER.error("error", e);
          }

          return true;
        default:
      }

      execReport.buildBalanceAdminMessage(); // sets the current balances from the matching thread

    } catch (Exception e) {
      LOGGER.error("error in updateFill " + order, e);
    }

    return false;
  }

  public boolean updateCancel(final Order order) {
    try {
      final User user = order.getUser();
      final Position[] positionArr = user.getPositionArr();
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());

      double usdMark = instrumentPair.getIndexFeedUsdMark();
      if (usdMark == 0) {
        usdMark = instrumentPair.getOrderBook().getUsdMark();
      }

      Position postion = user.getPositionArr()[order.getSecurityId()];
      if (postion == null) {
        postion = user.setPosition(order.getSecurityId(), 0, null);
      }

      final UserOpenOrdersByPair userOpenOrdersByPair = postion.getUserOpenOrdersByPair();
      user.decrementOpenOrderCount();
      userOpenOrdersByPair.remove(order);
      userOpenOrdersByPair.calcNotionalRequiredMargin(usdMark);

      final Fee fee = instrumentPair.getFee(user.getFeeTier(), IS_MAKER_DEFAULT, null);
      final long normalizedQuantityLong = normalizeQuantity(instrumentPair, order.getQuantityLong());
      // final double quotedCoinUsdMark = instrumentPair.getQuoted().getIndexFeedUsdMark();

      // calc usdNotional
      final int referencePrice = order.getMarginCheckReferencePrice() > 0 ? order.getMarginCheckReferencePrice() : order.getPriceInt();
      final double adjReferenceQuantity = MbxMath.roundToBestPrecision(order.getQuantityLong() * instrumentPair.getQuantityScaleFactor());
      final double notional = MbxMath.roundToBestPrecision(referencePrice * adjReferenceQuantity * instrumentPair.getPriceScaleFactor());
      // final double usdNotional = MbxMath.roundToBestPrecision(Math.abs(notional * quotedCoinUsdMark));

      // calc usdFee
      // final double usdFee = MbxMath.roundToBestPrecision(fee.calcUsdFee(usdNotional));
      // long feeQuantity = 0;
      // final Instrument feeInstrument = InstrumentCache.get(fee.getFeeInstrumentId());
      // if (feeInstrument != null) {
      // double feeInFeeInstrument = usdFee / feeInstrument.getIndexFeedUsdMark();
      // for (int i = 0; i < feeInstrument.getQuantityScale(); i++)
      // feeInFeeInstrument *= 10;
      // feeQuantity = (long) feeInFeeInstrument;

      // if (Context.isDebugLogRisk() && 18 == user.getId()) {
      // LOGGER.debug(LOG_FMT_8, UPDATEFILL_FEE_CALC_USDNOTIONAL_EQ, usdNotional, USD_FEE_EQ, usdFee, FEEMARK_EQ,
      // feeInstrument.getIndexFeedUsdMark(), FEEQUANTITY_EQ, feeQuantity);
      // }
      // } else {
      // if (Context.isDebugLogRisk()) {
      // LOGGER.debug(LOG_FMT_2, ">>> updateFill fee calc not found for ", fee.getFeeInstrumentId());
      // }
      // }

      final Position feePosition = positionArr[fee.getFeeInstrumentId()];

      switch (order.getType()) {
        case SELL_LIMIT:
        case SELL_MARKET:
        case SELL_SELECT:
        case STOP_SELL_LIMIT:
          final Position basePosition = positionArr[instrumentPair.getBaseId()];
          if (basePosition != null)
            basePosition.addAvailableQuantity(order.getAvailableUncollectedQuantity());
          if (feePosition != null) {
            // only adjust if we blocked off fee
            if (instrumentPair.getQuotedId() != fee.getFeeInstrumentId()) {
              feePosition.addAvailableQuantity(order.getFeeUncollectedQuantity());
            }
          }
          return true;
        case BUY_LIMIT:
        case BUY_MARKET:
        case BUY_SELECT:
        case STOP_BUY_LIMIT:
          long amount = normalizedQuantityLong * order.getMarginCheckReferencePrice();
          for (int i = 0; i < instrumentPair.getBase().getQuantityScale(); i++)
            amount = amount / 10;

          final long normalizedAmountLong = normalizePrice(instrumentPair, amount);

          final Position quotedPosition = positionArr[instrumentPair.getQuotedId()];
          if (quotedPosition != null)
            quotedPosition.addAvailableQuantity(order.getAvailableUncollectedQuantity());
          if (feePosition != null)
            feePosition.addAvailableQuantity(order.getFeeUncollectedQuantity());

          return true;
        default:
      }
    } catch (Exception e) {
      LOGGER.error(LOG_FMT_2, "error in updateCancel ", order, e);
    }

    return false;
  }


  @Override
  public void updateRisk(final User user, final double[] usdMarkPricesToSet) {
    if (Context.isDebugLogRisk() && user.getId() == 18) {
      LOGGER.debug(LOG_FMT_2, "verbose updateRisk user", user);
    }

    try {
      Position[] positionArr = user.getPositionArr();
      for (final Position position : positionArr) { // TODO: limit loop using user.maxActivePositionIndexHint
        if (position != null && AssetType.ASSET == position.getAssetType()) {
          // collateral coin assets
          final Instrument instrument = InstrumentCache.get(position.getInstrumentId());
          if (instrument != null) {
            final double usdMark = instrument.getIndexFeedUsdMark();
            if (usdMarkPricesToSet != null)
              usdMarkPricesToSet[position.getInstrumentId()] = usdMark;

            final double value = instrument.getQuantityScaleFactor() * position.getQuantity() * usdMark;
            position.setUsdValue(value);
            position.setQuotedUsdMark(instrument.getIndexFeedUsdMark());

            if (Context.isDebugLogRisk() && user.getId() == 18) {
              LOGGER.debug(LOG_FMT_12, "verbose updateRisk", user.getId(), RISK_USER_EQ, user.getId(), ", coin=", instrument.getId(),
                  VALUE_EQ, value, MARK_EQ, instrument.getIndexFeedUsdMark(), QUANTITY_EQ, position.getQuantity());
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("error in updateRisk e=" + e + USER_EQ + user, e);
      LOGGER.error("error in updateRisk " + user, e);
    }
  }

  @Override
  public void updateRiskAndCalcBankruptcyPrices(final User user, final double[] usdMarkPricesToSet) {
    // TODO Auto-generated method stub
  }

  @Override
  public void addOrderDuringRebuild(Order order, int referencePrice) {
    try {
      final User user = order.getUser();
      final Position[] positionArr = user.getPositionArr();
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());
      final Fee fee = instrumentPair.getFee(user.getFeeTier(), IS_MAKER_DEFAULT, null);

      final Position position = user.getPosition(instrumentPair.getId());
      final UserOpenOrdersByPair userOpenOrdersByPair = position.getUserOpenOrdersByPair();
      if (!userOpenOrdersByPair.add(order, referencePrice)) {
        return;
      }

      user.incrementOpenOrderCount();

      final Position feePosition = positionArr[fee.getFeeInstrumentId()];
      if (feePosition != null && order.getFeeEstimatedQuantity() > 0
          && order.getFeeEstimatedQuantity() > order.getFeeAccumulatedQuantity()) {
        feePosition.subtractAvailableQuantity(order.getFeeEstimatedQuantity() - order.getFeeAccumulatedQuantity());
      }

      switch (order.getType()) {
        case SELL_LIMIT:
        case SELL_MARKET:
        case SELL_SELECT:
        case STOP_SELL_LIMIT:
          final Position basePosition = positionArr[instrumentPair.getBaseId()];
          if (basePosition != null) {
            basePosition.subtractAvailableQuantity(order.getAvailableEstimatedQuantity() - order.getAvailableAccumulatedQuantity());
          }
          break;
        case BUY_LIMIT:
        case BUY_MARKET:
        case BUY_SELECT:
        case STOP_BUY_LIMIT:
          final Position quotedPosition = positionArr[instrumentPair.getQuotedId()];
          if (quotedPosition != null) {
            quotedPosition.subtractAvailableQuantity(order.getAvailableEstimatedQuantity() - order.getAvailableAccumulatedQuantity());
          }
          break;
        default:
      }

    } catch (Exception e) {
      LOGGER.error("error in checkOrder " + order + REFERENCEPRICE_EQ + referencePrice, e);
    }
  }

  // TODO: expiremental check on quantity available
  public static final long[] recalcAvailableQuantity(final User user) {
    if (user == null) {
      return null;
    }

    try {
      final Position[] positionArr = user.getPositionArr();
      final long[] available = new long[positionArr.length];
      boolean openOrders = false;

      // initially set available to the coin quantities
      for (int i = 0; i <= InstrumentCache.getInstrumentCapacity(); i++) {
        final Instrument instrument = InstrumentCache.get(i);
        if (instrument == null || positionArr[instrument.getId()] == null)
          continue;
        available[instrument.getId()] = positionArr[instrument.getId()].getQuantity();
      }
      // iterate through spot pairs and reduce by outstanding orders
      for (int i = InstrumentCache.getMinSpotPairIndex(); i <= InstrumentCache.getMaxSpotPairIndex(); i++) {
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair == null || AssetType.PAIR != pair.getAssetType() || positionArr[pair.getId()] == null)
          continue;
        final UserOpenOrdersByPair userOpenOrdersByPair = positionArr[pair.getId()].getUserOpenOrdersByPair();
        if (userOpenOrdersByPair == null || (userOpenOrdersByPair.getBidsCount() == 0 && userOpenOrdersByPair.getAsksCount() == 0))
          continue;

        openOrders = true;
        final Order[] bids = userOpenOrdersByPair.getBids();
        final Order[] asks = userOpenOrdersByPair.getAsks();
        final Fee fee = pair.getFee(user.getFeeTier(), IS_MAKER_DEFAULT, null);

        for (int j = bids.length - 1; j >= 0; j--) {
          final Order order = bids[j];
          if (order == null)
            continue;
          final long normalizedQuantityLong = normalizeQuantity(pair, order.getQuantityLong());
          long amount = normalizedQuantityLong * order.getMarginCheckReferencePrice();
          for (int k = 0; k < pair.getBase().getQuantityScale(); k++)
            amount = amount / 10;
          final long normalizedAmountLong = normalizePrice(pair, amount);
          final long feeEstimatedQuantity = order.getFeeEstimatedQuantity();
          final long feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
          available[pair.getQuotedId()] -= normalizedAmountLong;
          available[fee.getFeeInstrumentId()] -= (feeEstimatedQuantity - feeAccumulatedQuantity);
        }

        for (int j = asks.length - 1; j >= 0; j--) {
          final Order order = asks[j];
          if (order == null)
            continue;
          final long normalizedQuantityLong = normalizeQuantity(pair, order.getQuantityLong());
          final long feeEstimatedQuantity = order.getFeeEstimatedQuantity();
          final long feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
          available[pair.getBaseId()] -= normalizedQuantityLong;
          available[fee.getFeeInstrumentId()] -= (feeEstimatedQuantity - feeAccumulatedQuantity);
        }
      }

      // detect discrepancy
      for (int i = 0; i < positionArr.length; i++) {
        long positionAvailableQuantity = positionArr[i] == null ? 0 : positionArr[i].getAvailableQuantity();
        if (available[i] != positionAvailableQuantity) {
          System.err
              .println("available discrepancy id=" + i + ", " + positionAvailableQuantity + ", available=" + available + ", user=" + user);
          LOGGER.warn("available discrepancy id=" + i + ", " + positionAvailableQuantity + ", available=" + available + ", user=" + user);
        }
      }
      return available;
    } catch (Exception e) {
      LOGGER.error("error in recalcAvailableQuantity " + user, e);
    }
    return null;
  }

  // cash markets should already be physical
  public boolean updateFillPhysicalSettle(final Order order, final int referencePrice, final long referenceQuantity,
      final ExecutionReportMessage execReport, final double quotedUsdMark, final double settleCoinUsdMark, final double quotedCoinUsdMark,
      final boolean isMaker, final Order causingMessage, final int counterpartyId, final double baseCoinUsdMark) {
    return updateFill(order, referencePrice, referenceQuantity, execReport, quotedUsdMark, settleCoinUsdMark, quotedCoinUsdMark, isMaker,
        causingMessage, counterpartyId);
  }

  // use to set fee for CALCULATED orders
  public boolean checkOrderNoValidation(final Order order, final int referencePrice) {
    try {
      final User user = order.getUser();
      final Position[] positionArr = user.getPositionArr();
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());
      final Fee fee = instrumentPair.getFee(user.getFeeTier(), IS_MAKER_DEFAULT, null);
      final long normalizedQuantityLong = normalizeQuantity(instrumentPair, order.getQuantityLong());
      final double quotedCoinUsdMark = instrumentPair.getQuoted().getIndexFeedUsdMark();

      // calc usdNotional
      final double adjReferenceQuantity = MbxMath.roundToBestPrecision(order.getQuantityLong() * instrumentPair.getQuantityScaleFactor());
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
      order.setFeeEstimatedQuantity(feeQuantity);
      order.setFeeAccumulatedQuantity(0);
      order.setAvailableAccumulatedQuantity(0);

      final Position position = user.getPosition(instrumentPair.getId());

      user.incrementOpenOrderCount();

      switch (order.getType()) {
        case SELL_LIMIT:
        case SELL_MARKET:
        case SELL_SELECT:
        case STOP_SELL_LIMIT:
          final Position basePosition = positionArr[instrumentPair.getBaseId()];

          if (basePosition != null && basePosition.getInstrumentId() == fee.getFeeInstrumentId()) {
            basePosition.addAvailableQuantity(-(normalizedQuantityLong + feeQuantity));
            order.setAvailableEstimatedQuantity(normalizedQuantityLong);
            return true;
          } else if (basePosition != null && instrumentPair.getQuotedId() == fee.getFeeInstrumentId()) {
            // no need to block off for fees as we can recover it from proceeds of sale
            basePosition.addAvailableQuantity(-(normalizedQuantityLong));
            order.setAvailableEstimatedQuantity(normalizedQuantityLong);
            order.setFeeEstimatedQuantity(0);
            return true;
          } else {
            final Position feePosition = positionArr[fee.getFeeInstrumentId()];
            if (basePosition != null && basePosition.subtractAvailableQuantity(normalizedQuantityLong)
                && (feeQuantity == 0 || feePosition.subtractAvailableQuantity(feeQuantity))) {
              order.setAvailableEstimatedQuantity(normalizedQuantityLong);
              return true;
            }
          }
          break;
        case BUY_LIMIT:
        case BUY_MARKET:
        case BUY_SELECT:
        case STOP_BUY_LIMIT:
          final Position quotedPosition = positionArr[instrumentPair.getQuotedId()];
          long amount = normalizedQuantityLong * order.getMarginCheckReferencePrice();
          for (int i = 0; i < instrumentPair.getBase().getQuantityScale(); i++)
            amount = amount / 10;

          final long normalizedAmountLong = normalizePrice(instrumentPair, amount);

          if (quotedPosition != null && quotedPosition.getInstrumentId() == fee.getFeeInstrumentId()) {
            quotedPosition.addAvailableQuantity(-(normalizedAmountLong + feeQuantity));
            order.setAvailableEstimatedQuantity(normalizedAmountLong);
            return true;
          } else {
            final Position feePosition = positionArr[fee.getFeeInstrumentId()];
            if ((quotedPosition != null) && (quotedPosition.getAvailableQuantity() >= normalizedAmountLong) && (feePosition != null)
                && (feeQuantity == 0 || (feePosition.getAvailableQuantity() >= feeQuantity))
                && quotedPosition.subtractAvailableQuantity(normalizedAmountLong)
                && (feeQuantity == 0 || feePosition.subtractAvailableQuantity(feeQuantity))) {
              order.setAvailableEstimatedQuantity(normalizedAmountLong);
              return true;
            }
          }

          break;
        default:
      }

      user.decrementOpenOrderCount();

      // debug only
      final Position basePosition = positionArr[instrumentPair.getBaseId()];
      final Position quotedPosition = positionArr[instrumentPair.getQuotedId()];
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_10, ">>> checkOrder qtyLong=", order.getQuantityLong(), "order.getPriceInt()=", order.getPriceInt(),
            REFERENCEPRICE_EQ, referencePrice, ", basePosition=", basePosition, ", quotedPosition=", quotedPosition);
      }

    } catch (Exception e) {
      LOGGER.error("error in checkOrder " + order + REFERENCEPRICE_EQ + referencePrice, e);
    }

    return false;
  }

  public boolean updateCancelNoValidation(final Order order) {
    try {
      final User user = order.getUser();
      final Position[] positionArr = user.getPositionArr();
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());

      double usdMark = instrumentPair.getIndexFeedUsdMark();
      if (usdMark == 0) {
        usdMark = instrumentPair.getOrderBook().getUsdMark();
      }

      Position postion = user.getPositionArr()[order.getSecurityId()];
      if (postion == null) {
        postion = user.setPosition(order.getSecurityId(), 0, null);
      }

      final Fee fee = instrumentPair.getFee(user.getFeeTier(), IS_MAKER_DEFAULT, null);
      final long normalizedQuantityLong = normalizeQuantity(instrumentPair, order.getQuantityLong());
      // final double quotedCoinUsdMark = instrumentPair.getQuoted().getIndexFeedUsdMark();

      // calc usdNotional
      final int referencePrice = order.getMarginCheckReferencePrice() > 0 ? order.getMarginCheckReferencePrice() : order.getPriceInt();
      final double adjReferenceQuantity = MbxMath.roundToBestPrecision(order.getQuantityLong() * instrumentPair.getQuantityScaleFactor());
      final double notional = MbxMath.roundToBestPrecision(referencePrice * adjReferenceQuantity * instrumentPair.getPriceScaleFactor());
      // final double usdNotional = MbxMath.roundToBestPrecision(Math.abs(notional * quotedCoinUsdMark));

      // calc usdFee
      // final double usdFee = MbxMath.roundToBestPrecision(fee.calcUsdFee(usdNotional));
      // long feeQuantity = 0;
      // final Instrument feeInstrument = InstrumentCache.get(fee.getFeeInstrumentId());
      // if (feeInstrument != null) {
      // double feeInFeeInstrument = usdFee / feeInstrument.getIndexFeedUsdMark();
      // for (int i = 0; i < feeInstrument.getQuantityScale(); i++)
      // feeInFeeInstrument *= 10;
      // feeQuantity = (long) feeInFeeInstrument;

      // if (Context.isDebugLogRisk() && 18 == user.getId()) {
      // LOGGER.debug(LOG_FMT_8, UPDATEFILL_FEE_CALC_USDNOTIONAL_EQ, usdNotional, USD_FEE_EQ, usdFee, FEEMARK_EQ,
      // feeInstrument.getIndexFeedUsdMark(), FEEQUANTITY_EQ, feeQuantity);
      // }
      // } else {
      // if (Context.isDebugLogRisk()) {
      // LOGGER.debug(LOG_FMT_2, ">>> updateFill fee calc not found for ", fee.getFeeInstrumentId());
      // }
      // }

      final Position feePosition = positionArr[fee.getFeeInstrumentId()];

      switch (order.getType()) {
        case SELL_LIMIT:
        case SELL_MARKET:
        case SELL_SELECT:
        case STOP_SELL_LIMIT:
          final Position basePosition = positionArr[instrumentPair.getBaseId()];
          if (basePosition != null)
            basePosition.addAvailableQuantity(order.getAvailableUncollectedQuantity());
          if (feePosition != null) {
            // only adjust if we blocked off fee
            if (instrumentPair.getQuotedId() != fee.getFeeInstrumentId()) {
              feePosition.addAvailableQuantity(order.getFeeUncollectedQuantity());
            }
          }
          return true;
        case BUY_LIMIT:
        case BUY_MARKET:
        case BUY_SELECT:
        case STOP_BUY_LIMIT:
          long amount = normalizedQuantityLong * order.getMarginCheckReferencePrice();
          for (int i = 0; i < instrumentPair.getBase().getQuantityScale(); i++)
            amount = amount / 10;

          final long normalizedAmountLong = normalizePrice(instrumentPair, amount);

          final Position quotedPosition = positionArr[instrumentPair.getQuotedId()];
          if (quotedPosition != null)
            quotedPosition.addAvailableQuantity(order.getAvailableUncollectedQuantity());
          if (feePosition != null)
            feePosition.addAvailableQuantity(order.getFeeUncollectedQuantity());

          return true;
        default:
      }
    } catch (Exception e) {
      LOGGER.error(LOG_FMT_2, "error in updateCancel ", order, e);
    }

    return false;
  }

}
