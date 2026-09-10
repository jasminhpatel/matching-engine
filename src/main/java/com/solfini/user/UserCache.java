package com.solfini.user;

import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.LiquidityOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.util.MbxMath;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.CollateralSwapMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.pool.BalanceAdminMessageObjectPool;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.report.PositionSummary;
import com.solfini.report.check.ReconciliationResult;
import com.solfini.risk.InsuranceState;
import com.solfini.risk.UserRiskCache;
import com.solfini.util.FastArrayList;
import com.solfini.util.TickerTrie;


/**
 *
 * @author Chris Mack
 *
 */
public class UserCache implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(UserCache.class);

  private static final int INITIAL_SIZE = Context.getInitialUserCacheSize();
  private static final int INCREASE_SIZE = 1024;

  private static final TickerTrie<User> loginToUser = new TickerTrie<>();
  private static ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
  private static ManyToManyConcurrentArrayQueueCustom<User> riskToAutoConvertQueue = Context.getRiskToAutoConvertQueue();
  private static User[] userArr = buildInitialUserCache();
  private static int maxUserId = 0;
  private static final PreOrderCheck marginCheck = new MarginPreOrderCheckAndSettle();
  private static final boolean IS_COLLATERAL_SWAP_ENABLED = Context.isCollateralSwapEnabled();

  // special users
  private static User ADMIN_USER = new User(0);
  private static User EXCHANGE_USER = new User(0);
  private static User INSURANCE_FUND_USER = new User(0);
  private static User MARKET_MAKER_USER = new User(0);
  private static User TEST_USER = new User(0);
  private static User OTHER_USER = new User(0);
  private static User TOKEN_MANAGER = new User(0);
  private static final List<BalanceAdminMessage> ADMIN_USER_BALANCE_LIST = new FastArrayList<>();
  private static final List<BalanceAdminMessage> EXCHANGE_USER_BALANCE_LIST = new FastArrayList<>();
  private static final List<BalanceAdminMessage> INSURANCE_FUND_BALANCE_LIST = new FastArrayList<>();
  private static final List<BalanceAdminMessage> MARKET_MAKER_BALANCE_LIST = new FastArrayList<>();


  private UserCache() {}

  public static final User getAdminUser() {
    return ADMIN_USER;
  }

  public static final User getExchangeUser() {
    return EXCHANGE_USER;
  }

  public static final User getInsuranceFundUser() {
    return INSURANCE_FUND_USER;
  }

  public static final User getMarketMakerUser() {
    return MARKET_MAKER_USER;
  }

  public static final User getTestUser() {
    return TEST_USER;
  }

  public static final User getOtherUser() {
    return OTHER_USER;
  }

  public static User getTokenManager() {
    return TOKEN_MANAGER;
  }

  public static final void setAdminUser(final User adminUser) {
    ADMIN_USER = adminUser;
  }

  public static final void setExchangeUser(final User exchangeUser) {
    EXCHANGE_USER = exchangeUser;
  }

  public static final void setMarketMakerUser(final User marketMakerUser) {
    MARKET_MAKER_USER = marketMakerUser;
  }

  public static final void setTestUser(final User testUser) {
    TEST_USER = testUser;
  }

  public static final void setOTHER_USER(final User otherUser) {
    OTHER_USER = otherUser;
  }

  public static final void setInsuranceFundUser(final User insuranceFundUser) {
    INSURANCE_FUND_USER = insuranceFundUser;
  }

  public static final List<BalanceAdminMessage> getAdminUserBalanceList() {
    return ADMIN_USER_BALANCE_LIST;
  }

  public static final List<BalanceAdminMessage> getExchangeUserBalanceList() {
    return EXCHANGE_USER_BALANCE_LIST;
  }

  public static final List<BalanceAdminMessage> getInsuranceFundBalanceList() {
    return INSURANCE_FUND_BALANCE_LIST;
  }

  public static final List<BalanceAdminMessage> getMarketMakerBalanceList() {
    return MARKET_MAKER_BALANCE_LIST;
  }

  public static final User[] buildInitialUserCache() {
    final User[] userArr = new User[INITIAL_SIZE];
    for (int i = 0; i < userArr.length; i++) {
      userArr[i] = new User(i);
      userArr[i].setActive(false);
    }
    return userArr;
  }

  public static void clearUserCache() {
    for (int i = 0; i < userArr.length; i++) {
      userArr[i] = new User(i);
      userArr[i].setActive(false);
    }
  }

  public static final void add(final UserAdminMessage userAdminMessage) {

    UserAdminMessage message = userAdminMessage;
    if (message.getUpdateType() == UpdateType.PATCH) {
      if (message.getPatchType() == PATCH_FEE_TIER) {
        message.setRequestStatus(RequestStatus.FAIL);
        if ((message.getUserId() != 0) && (message.getUserId() < userArr.length)) {
          final User user = UserCache.get(message.getUserId());
          if ((user != null) && user.isActive()) {
            user.setFeeTier(message.getFeeTier());
            user.setFeeTierOrig(message.getFeeTier());
            user.setMarginCurveIdOverride(message.getMarginCurveIdOverride());
            message = user.buildUserAdminMessage();
            user.copySetPositionArr(message);
          }
        }
      } else if (message.getPatchType() == PATCH_MARGIN_CURVE_ID) {
        if ((message.getUserId() != 0) && (message.getUserId() < userArr.length)) {
          message.setRequestStatus(RequestStatus.SUCCESS);
          final User user = UserCache.get(message.getUserId());
          if ((user != null) && user.isActive()) {
            user.setFeeTier(message.getFeeTier());
            user.setFeeTierOrig(message.getFeeTier());
            user.setMarginCurveIdOverride(message.getMarginCurveIdOverride());
            message = user.buildUserAdminMessage();
            user.copySetPositionArr(message);
          }
        }
      }
    } else {
      addToCache(message);
      message.setRequestStatus(RequestStatus.SUCCESS);
    }

    if (matcherToPublisherQueue == null) {
      matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
    }

    if (MarketStatus.DR_MODE != Context.getMarketStatus()) {
      matcherToPublisherQueue.addGuaranteed(message);
    }
  }

  public static final void increaseCacheSize(final int currentMaxId) {
    userArr = Arrays.copyOf(userArr, currentMaxId + INCREASE_SIZE);
    for (int i = userArr.length - 1; i >= 0; i--) {
      if (userArr[i] == null) {
        userArr[i] = new User(i);
        userArr[i].setActive(false);
      } else
        break;
    }
  }

  public static final void addToCache(final UserAdminMessage userAdminMessage) {
    if (userAdminMessage.getUserId() == 0) { // set userId
      int userId = ++maxUserId;
      userAdminMessage.setUserId(userId);
      if (userId >= userArr.length)
        increaseCacheSize(userAdminMessage.getUserId());

      userArr[userId].setUser(userAdminMessage);
      loginToUser.add(userArr[userAdminMessage.getUserId()].getLogin(), userArr[userAdminMessage.getUserId()]);
    } else if (userAdminMessage.getUserId() > maxUserId) // maxUserId
      maxUserId = userAdminMessage.getUserId();

    if (userAdminMessage.getUserId() >= userArr.length)
      increaseCacheSize(userAdminMessage.getUserId());


    User user = userArr[userAdminMessage.getUserId()];

    if (!user.isActive() && userAdminMessage.getUpdateType() != UpdateType.DELETE) {
      user.setUser(userAdminMessage);
      loginToUser.add(user.getLogin(), user);
    } else {
      switch (userAdminMessage.getUpdateType()) {
        case PUT:
          user.override(userAdminMessage); // override user values
          break;
        case PATCH:
          user.updateIncrement(userAdminMessage); // used to update balances
          break;
        case DELETE:
          break;
        default:
      }
    }
    // optimization: if isSnapLoaderMode(), don't clone the positions arr, just publish
    // this is only safe for snaps where the positions aren't changed from other messages
    if (SnapLoader.isSnapLoaderMode()) {
      userAdminMessage.setPositionArr(user.getPositionArr());
    } else {
      // we set clone here from matching thread for exact state
      user.copySetPositionArr(userAdminMessage);
    }

    // special userTypes
    if (userAdminMessage.getUserType() > 0) {
      switch (userAdminMessage.getUserType()) {
        case User.ADMIN:
          ADMIN_USER = user;
          break;
        case User.EXCHANGE:
          EXCHANGE_USER = user;
          break;
        case User.INSURANCE_FUND:
          INSURANCE_FUND_USER = user;
          Fee.LIQUIDATION_FEE.setCollectingUser(INSURANCE_FUND_USER);
          InsuranceState.setUser(INSURANCE_FUND_USER);
          break;
        case User.MARKET_MAKER:
          MARKET_MAKER_USER = user;
          break;
        case User.TEST:
          TEST_USER = user;
          break;
        case User.OTHER:
          OTHER_USER = user;
          break;
        case User.TOKEN_MANAGER:
          TOKEN_MANAGER = user;
          break;
        default:
      }
    }

    // Reset auto liquidation state
    if ((user.getAutoLiquidationState().get() == 1)
        && ((userAdminMessage.getStatus() & User.USER_STATUS_RESET_AUTO_LIQUIDATION_STATE) != 0)) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_4, "Reset user autoLiquidationState - user:", user, ", message: ", userAdminMessage);
      }

      user.getAutoLiquidationState().set(0);
    }
  }

  // must be called from the matching engine thread
  public static final void addBalance(final BalanceAdminMessage balanceAdminMessage) {
    // reject balanceAdminMessage with unexpected userId
    if (balanceAdminMessage.getUserId() < 0 || balanceAdminMessage.getUserId() >= userArr.length
        || userArr[balanceAdminMessage.getUserId()] == null || !userArr[balanceAdminMessage.getUserId()].isActive()) {
      LOGGER.info(Constants.LOG_FMT_8, " Reject addBalance. userId ", balanceAdminMessage.getUserId(),
          " within the userArr: ", balanceAdminMessage.getUserId() >= userArr.length,
          " user: ", userArr[balanceAdminMessage.getUserId()],
          " active: ", (userArr[balanceAdminMessage.getUserId()] != null && userArr[balanceAdminMessage.getUserId()].isActive()));

      try { // log error to trace the cause of this
        throw new NullPointerException();
      } catch (Exception e) {
        LOGGER.error("error TX_BALANCE_ADMIN_INVALID_USER_REJECTED balanceAdminMessage=" + balanceAdminMessage, e);
      }
      balanceAdminMessage.setTxType(TX_BALANCE_ADMIN_INVALID_USER_REJECTED);


      final List<Balance> list = balanceAdminMessage.getBalanceList();
      if (list != null)
        list.clear();

      if (matcherToPublisherQueue == null) {
        matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
      }

      matcherToPublisherQueue.addGuaranteed(balanceAdminMessage);
      return;
    }

    if (balanceAdminMessage.getUserId() >= userArr.length) {
      userArr = Arrays.copyOf(userArr, balanceAdminMessage.getUserId() + INCREASE_SIZE);
      for (int i = userArr.length - 1; i >= 0; i--) {
        if (userArr[i] == null) {
          userArr[i] = new User(i);
          userArr[i].setActive(false);
        } else
          break;
      }
    }

    BalanceAdminMessage counterBalanceAdminMessage = null;

    if (!userArr[balanceAdminMessage.getUserId()].isActive() && balanceAdminMessage.getUpdateType() != UpdateType.DELETE) {
      userArr[balanceAdminMessage.getUserId()].setUser(balanceAdminMessage);
      loginToUser.add(userArr[balanceAdminMessage.getUserId()].getLogin(), userArr[balanceAdminMessage.getUserId()]);
    } else {
      switch (balanceAdminMessage.getUpdateType()) {
        case PUT:
          userArr[balanceAdminMessage.getUserId()].override(balanceAdminMessage);
          break;
        case PATCH:
          int txType = balanceAdminMessage.getTxType();
          userArr[balanceAdminMessage.getUserId()].updateIncrement(balanceAdminMessage); // mostly used to update balances
          // special case to handle internal balance transfers from this account to another
          // the first use should be withdrawing
          if (balanceAdminMessage.getBalanceTransferToUserId() > 0) {
            counterBalanceAdminMessage = new BalanceAdminMessage(balanceAdminMessage, null);
            counterBalanceAdminMessage.setUserId(balanceAdminMessage.getBalanceTransferToUserId());
            counterBalanceAdminMessage.setBalanceTransferToUserId(0);
            counterBalanceAdminMessage.inverseBalancesForTransfer();
/*            if (txType == API_TX_COPY_TRADE_COMMISSION) {
              counterBalanceAdminMessage.setTxType(API_TX_COPY_TRADE_EARNINGS);// counter transaction
            }*/
            LOGGER.info("Counter BalanceAdminMessage: " + counterBalanceAdminMessage.toJSON());
          }
          break;
        case DELETE:
          break;
        default:
      }
    }

    final User user = userArr[balanceAdminMessage.getUserId()];
    if (MarketStatus.DR_MODE != Context.getMarketStatus()) {
      marginCheck.updateRisk(user, null);
    }

    user.copySetPositionArr(balanceAdminMessage);
    balanceAdminMessage.setRequestStatus(RequestStatus.SUCCESS);

    if (matcherToPublisherQueue == null) {
      matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_2, ADDBALANCE_BALANCEADMINMESSAGE_EQ, balanceAdminMessage);
    }

    if (MarketStatus.DR_MODE != Context.getMarketStatus()) {
      // if balance change sets user above liquidation levels, reset it
      // the first use should be withdrawing
      if (user.getAutoLiquidationState().get() == 1) {
        if (user.getMarginRatio() < Context.getMarginLiquidationSatisfiedThreshold() && user.getUsdValue() >= 0) {
          user.setFeeTier(user.getFeeTierOrig());
          user.getAutoLiquidationCounter().set(0);
          user.getAutoLiquidationState().set(0);
        }
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_6, ADDBALANCE_BALANCEADMINMESSAGE_EQ, balanceAdminMessage, " getAutoLiquidationState=",
              user.getAutoLiquidationState().get(), "user=", user);
        }
      }

      addToReportCache(balanceAdminMessage);
      matcherToPublisherQueue.addGuaranteed(balanceAdminMessage);
      if (counterBalanceAdminMessage != null) {
        LOGGER.info("Trigger BalanceAdminMessage: " + counterBalanceAdminMessage.toJSON());
        addBalance(counterBalanceAdminMessage);
      }
    } else { // Don't cache or publish when running as secondary
      BalanceAdminMessageObjectPool.returnObject(balanceAdminMessage);
    }
  }

  public static final int getCapacity() {
    return maxUserId;
  }

  public static final int getCacheSize() {
    return userArr.length;
  }

  public static final User get(final int id) {
    return userArr[id];
  }

  public static final User getByLogin(final String login) {
    return loginToUser.get(login);
  }

  // called from risk thread
  public static final void processRisk(final double[] usdMarkPricesToSet) {
    final boolean isTestnet = "TEST".equalsIgnoreCase(Context.getEnvironment());
    final Instrument usd = InstrumentCache.getBySymbol(USD);
    final Instrument usdc = InstrumentCache.getBySymbol(isTestnet? "T_USDC": USDC);
    final Instrument usdt = InstrumentCache.getBySymbol(isTestnet? "T_USDT": USDT);

    for (int i = 0; i < Math.min(maxUserId + 1, userArr.length); i++) {
      final User user = userArr[i];
      if (user == null || !user.isActive())
        continue;

      try {
        marginCheck.updateRiskAndCalcBankruptcyPrices(user, usdMarkPricesToSet);
        UserRiskCache.reIndex(user);
        if (IS_COLLATERAL_SWAP_ENABLED) {
          CollateralSwapMessage.checkCollateralBalance(user);
        }
        // USDC/USDT -> USD auto conversion
        // Auto converts Stable coins if user has a negative USD balance
        if (Context.isLiquidityDexEnabled() && Context.isLiquidityImbalanceSettleEnabled() && user.getPositionArr() != null) {
          if (UserCache.getMarketMakerUser().getId() != user.getId()) {
            final Position usdPosition = user.getPositionArr()[usd.getId()];
            if (usdPosition != null && usdPosition.getUsdValue() < 0) {
              final Position usdcPosition = user.getPositionArr()[usdc.getId()];
              final Position usdtPosition = user.getPositionArr()[usdt.getId()];
              final double stableCoinBalance = (usdcPosition != null ? usdcPosition.getUsdValue() : 0) +
                  (usdtPosition != null ? usdtPosition.getUsdValue() : 0);
              if (stableCoinBalance > 0) {
                riskToAutoConvertQueue.addGuaranteed(user);
              }
            }
          }
        }

      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }

  // restate all users
  // must be called from matching thread
  // start with user=1
  public static final void restateAllUsers(final long snapId) {
    for (int i = 1; i < Math.min(maxUserId + 1, userArr.length); i++) {
      final User user = userArr[i];
      if (user == null || !user.isActive())
        continue;

      final UserAdminMessage userAdminMessage = user.buildUserAdminMessage();
      userAdminMessage.setSnapId(snapId);
      user.copySetPositionArr(userAdminMessage);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_6, RESTATEALLUSERS_USERADMINMESSAGE_EQ, userAdminMessage, POSITIONARR_EQ,
            Arrays.toString(userAdminMessage.getPositionArr()), USER_EQ, user);
      }
      matcherToPublisherQueue.addGuaranteed(userAdminMessage);
    }
  }

  // restate all user positions,
  // must be called from matching thread
  // start with user=1
  public static final void restateAllUserPositions(final long snapId) {
    for (int i = 1; i < Math.min(maxUserId + 1, userArr.length); i++) {
      final User user = userArr[i];
      if (user == null || !user.isActive())
        continue;

      final BalanceAdminMessage balanceAdminMessage = user.buildBalanceAdminMessage();
      balanceAdminMessage.setSnapId(snapId);
      user.copySetPositionArr(balanceAdminMessage);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_6, RESTATEALLUSERS_BALANCEADMINMESSAGE_EQ, balanceAdminMessage, POSITIONARR_EQ,
            Arrays.toString(balanceAdminMessage.getPositionArr()), USER_EQ, user);
      }
      matcherToPublisherQueue.addGuaranteed(balanceAdminMessage);
    }
  }

  // reset all user available balances
  public static final void resetAvailableBalances() {
    for (int i = 1; i < Math.min(maxUserId + 1, userArr.length); i++) {
      final User user = userArr[i];
      if (user == null || !user.isActive())
        continue;

      final Position[] positions = user.getPositionArr();
      for (int j = 0; j < positions.length; j++) {
        if (positions[j] != null) {
          positions[j].setAvailableQuantity(positions[j].getQuantity());
        }
      }
    }
  }

  /**
   * process position reconciliation
   *
   * @param symbolId if symbolId<=0, do reconciliation for all symbols
   * @return result, key:symbolId, value: ReconciliationResult
   */

  public static final Map<Integer, ReconciliationResult> processReconciliation(final int symbolId) {
    final Map<Integer, ReconciliationResult> reconciliationResultMap = new HashMap<>();
    if (symbolId > 0) {
      final ReconciliationResult symbolResult = new ReconciliationResult(symbolId);
      for (int i = 1; i < Math.min(maxUserId + 1, userArr.length); i++) {
        final User user = userArr[i];
        if (user == null || user.getPositionArr() == null) {
          continue;
        }

        final Position position = user.getPosition(symbolId);
        if (position != null) {
          symbolResult.addPositionSum(position.getQuantity());
          symbolResult.addUnrealizedPnlSum(position.getUsdUnrealized());
          symbolResult.addRealizedPnlSum(position.getUsdRealizedDouble());
        }
      }
      reconciliationResultMap.put(symbolResult.getSymbolId(), symbolResult);
    } else {

      for (int i = 1; i < Math.min(maxUserId + 1, userArr.length); i++) {
        final User user = userArr[i];
        if (user == null || user.getPositionArr() == null) {
          continue;
        }
        for (final Position position : user.getPositionArr()) {
          if (position != null) {

            ReconciliationResult symbolResult = reconciliationResultMap.get(position.getInstrumentId());
            if (symbolResult == null) {
              symbolResult = new ReconciliationResult(position.getInstrumentId());
              reconciliationResultMap.put(symbolResult.getSymbolId(), symbolResult);
            }
            symbolResult.addPositionSum(position.getQuantity());
            symbolResult.addUnrealizedPnlSum(position.getUsdUnrealized());
            symbolResult.addRealizedPnlSum(position.getUsdRealizedDouble());
          }
        }
      }
    }
    return reconciliationResultMap;
  }

  /**
   * process position reconciliation for all symbols
   *
   * @return result, key:symbolId, value:ReconciliationResult
   */
  public static final Map<Integer, ReconciliationResult> processReconciliation() {
    return UserCache.processReconciliation(0);
  }

  public static final void addToReportCache(final BalanceAdminMessage balanceAdminMessage) {
    try {
      final int userId = balanceAdminMessage.getUserId();
      if (userId == EXCHANGE_USER.getId()) {
        EXCHANGE_USER_BALANCE_LIST.add(new BalanceAdminMessage(balanceAdminMessage, null));
      } else if (userId == INSURANCE_FUND_USER.getId()) {
        INSURANCE_FUND_BALANCE_LIST.add(new BalanceAdminMessage(balanceAdminMessage, null));
      } else if (userId == MARKET_MAKER_USER.getId()) {
        MARKET_MAKER_BALANCE_LIST.add(new BalanceAdminMessage(balanceAdminMessage, null));
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public static final PositionSummary[] recalcPositionsSummary() {
    final PositionSummary[] positionSummary =
        new PositionSummary[Math.max(InstrumentCache.getPairCapacity(), InstrumentCache.getInstrumentCapacity())];

    for (int i = 0; i < maxUserId; i++) {
      final User user = UserCache.get(i);
      if (user == null)
        continue;
      final Position[] positionArr = user.getPositionArr();
      if (positionArr == null)
        continue;

      for (int j = 0; j < Math.min(user.getMaxActivePositionIndexHint(), positionArr.length); j++) {
        final Position position = positionArr[j];
        if (position == null)
          continue;
        final long quantity = position.getQuantity();
        if (quantity == 0)
          continue;

        if (positionSummary[position.getInstrumentId()] == null)
          positionSummary[position.getInstrumentId()] = new PositionSummary(position.getInstrumentId());

        if (quantity > 0)
          positionSummary[position.getInstrumentId()].addLong(quantity);
        else if (quantity < 0)
          positionSummary[position.getInstrumentId()].addShort(quantity);
      }
    }
    return positionSummary;
  }

  public static void setRewardClaimed(final int id) {
    userArr[id].setRewardClaimed(true);
  }
}
