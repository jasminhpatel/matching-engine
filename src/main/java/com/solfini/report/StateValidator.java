package com.solfini.report;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.TreeOrderBook2;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import uk.co.real_logic.artio.fields.DecimalFloat;

/**
 *
 * @author Chris Mack
 *
 */
public class StateValidator implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(StateValidator.class);
  private static final MarginPreOrderCheckAndSettle preOrderCheck = new MarginPreOrderCheckAndSettle();
  private static final long[] balanceChangeBySecurityArr = new long[Math.max(InstrumentCache.getPairCapacity(), 256)];
  private static double lastUsdUnrealized = 0;
  private static double lastUsdRealized = 0;

  private StateValidator() {
    // Make sure this class is not instantiated
  }

  public static final boolean calcSecurityStats(final InstrumentPair pair) {
    double usdUnrealized = 0;
    double usdRealized = 0;

    long total = 0;
    for (int i = 0; i < Math.min(UserCache.getCapacity() + 1, UserCache.getCacheSize()); i++) {
      final User user = UserCache.get(i);
      if (user != null && user.isActive()) {
        preOrderCheck.updateRisk(user, null);
        final Position position = user.getPosition(pair.getId());
        if (position != null) {
          total += position.getQuantity();

          if (position != null && (position.getUsdUnrealized() != 0 || position.getUsdRealizedDouble() != 0)) {
            usdUnrealized += position.getUsdUnrealized();
            usdRealized += position.getUsdRealizedDouble();
            preOrderCheck.updateRisk(user, null);
            preOrderCheck.updateRisk(user, null);
          }

        }
      }
    }

    // set totals
    int securityId = pair.getId();
    long positionTotal = total;

    if (positionTotal != 0) {
      LOGGER.error(LOG_FMT_8, "calcForSecurity securityIdIn=", securityId, ", positionTotal=", total, UNREALIZEDUSD_EQ, usdUnrealized,
          ", usdRealized=", usdRealized, PAIR_EQ, pair);
      return false;
    }


    // still debug log result
    double lastPnlSum = lastUsdUnrealized + lastUsdRealized;
    double pnlSum = usdUnrealized + usdRealized;

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_8, "calcForSecurity securityIdIn=", securityId, POSITION_TOTAL_EQ, total, UNREALIZEDUSD_EQ, usdUnrealized, ", usdRealized=",
          usdRealized, PAIR_EQ, pair);

      if (Math.abs(lastPnlSum - pnlSum) > .02) {
        LOGGER.warn(LOG_FMT_8, "calcForSecurity change in pnlSum securityIdIn=", securityId, ", positionTotal=", total, UNREALIZEDUSD_EQ,
            usdUnrealized, ", usdRealized=", usdRealized, PAIR_EQ, pair, ", lastUsdUnrealized=", lastUsdUnrealized, ", lastUsdRealized=",
            lastUsdRealized);
      }
    }

    lastUsdUnrealized = usdUnrealized;
    lastUsdRealized = usdRealized;
    return true;
  }

  public static final void updateBalanceCount(final BalanceAdminMessage balanceAdminMessage) {

    for (final Balance balance : balanceAdminMessage.getBalanceList()) {
      if (balance != null && balance.getAssetId() > 0) {

        final DecimalFloat quantityDecimal = balance.getBalance();
        long quantityLong = quantityDecimal.value();
        final int quantityScale = quantityDecimal.scale();
        final Instrument instrument = InstrumentCache.get(balance.getAssetId());
        if (instrument != null) {
          quantityLong = instrument.adjustQuantityToScale(quantityLong, quantityScale);
        } else {
          final InstrumentPair pair = InstrumentCache.getPair(balance.getAssetId());
          if (null != pair) {
            quantityLong = pair.adjustQuantityToScale(quantityLong, quantityScale);
          }
        }

        if (UpdateType.PATCH == balanceAdminMessage.getUpdateType()) {
          // mostly used to update balances
          balanceChangeBySecurityArr[balance.getAssetId()] += quantityLong;
        } else if (UpdateType.PUT == balanceAdminMessage.getUpdateType()) {
          // TODO: this only works on first PUT, but PATCH should normally be used
          // we may need to track the balance change when calling PUT
          balanceChangeBySecurityArr[balance.getAssetId()] += quantityLong;
        }

      }
    }
  }

  public static final boolean validateOrderBook(final InstrumentPair instrumentPair) {
    if (instrumentPair.getOrderBook() instanceof ArrayOrderBook) {
      ArrayOrderBook orderBook = (ArrayOrderBook) instrumentPair.getOrderBook();
      if ((orderBook.getBidLevelCachePtrArr()[0] != 0) && (orderBook.getAskLevelCachePtrArr()[0] != 0)) {
        if (orderBook.getBidLevelCachePtrArr()[0] >= orderBook.getAskLevelCachePtrArr()[0]) {
          LOGGER.error(LOG_FMT_6, "validateOrderBook ArrayOrderBook CROSSED securityIdIn=", instrumentPair.getId(), ", bestBid=",
              orderBook.getBidLevelCachePtrArr()[0], ", bestAsk=", orderBook.getAskLevelCachePtrArr()[0]);
          return false;
        }
      }
    } else if (instrumentPair.getOrderBook() instanceof TreeOrderBook2) {
      TreeOrderBook2 orderBook = (TreeOrderBook2) instrumentPair.getOrderBook();
      int bid = orderBook.getBid();
      int ask = orderBook.getAsk();
      if (bid > ask && ask > 0) {
        LOGGER.error(LOG_FMT_6, "validateOrderBook TreeOrderBook2 CROSSED securityIdIn=", instrumentPair.getId(), ", bestBid=", bid,
            ", bestAsk=", ask);
        return false;
      }
    }

    return true;
  }

  public static final boolean validate(final Message message) {
    if (Mode.PRIMARY != Context.getControllerMode())
      return true;

    if (!SnapLoader.isSnapLoaderMode()) {
      // filter execReport if trade is the taker/aggressor
      if (message != null) {
        if (message instanceof ExecutionReportMessage) {
          final ExecutionReportMessage execReport = (ExecutionReportMessage) message;
          if (ExecType.TRADE == execReport.getExecType() && execReport.getSide() == execReport.getAggressorSide()) {
            // if trade is the taker/aggressor then don't validate since balances are being updated
            return true;
          }
        }
        if (message instanceof BalanceAdminMessage) {
          updateBalanceCount((BalanceAdminMessage) message);
        }
      }

      // validate
      for (int i = 0; i < InstrumentCache.getPairCapacity(); i++) {
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair != null) {
          if (!calcSecurityStats(pair)) {
            stopIfInvalid(message);
            return false;
          }
          if (!validateOrderBook(pair)) {
            stopIfInvalid(message);
            return false;
          }
        }
      }
    }

    return true;
  }


  public static final boolean validateDR(final Message message) {
    if (!SnapLoader.isSnapLoaderMode()) {
      // filter execReport if trade is the taker/aggressor
      if (message != null) {
        if (message instanceof ExecutionReportMessage) {
          final ExecutionReportMessage execReport = (ExecutionReportMessage) message;
          if (ExecType.TRADE == execReport.getExecType() && execReport.getSide() == execReport.getAggressorSide()) {
            // if trade is the taker/aggressor then don't validate since balances are being updated
            return true;
          }
        }
        if (message instanceof BalanceAdminMessage) {
          updateBalanceCount((BalanceAdminMessage) message);
          return true; // can't validate DR balance messages due to sequence
        }
      }

      // validate
      for (int i = 0; i < InstrumentCache.getPairCapacity(); i++) {
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair != null) {
          if (!calcSecurityStats(pair)) {
            return false;
          }
          if (!validateOrderBook(pair)) {
            return false;
          }
        }
      }
    }

    return true;
  }

  public static final void stopIfInvalid(final Message message) {
    LOGGER.error(LOG_FMT_2, "Invalid State. Stopping. message=", message);
    try {
      Thread.sleep(5000);
      LOGGER.error(LOG_FMT_2, "Invalid State 2. Stopping. message=", message);
      System.exit(1);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

  }
}
