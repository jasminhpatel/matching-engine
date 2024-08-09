package com.solfini.matchengine.orderbook;

import com.solfini.common.*;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.copytrade.CopyTradeCache;
import com.solfini.matchengine.copytrade.*;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import com.solfini.matchengine.drmode.message.DRCancelOrder;
import com.solfini.matchengine.drmode.message.DRExecutionReport;
import com.solfini.matchengine.drmode.message.DROrder;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.internal.*;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.preordercheck.NoPreOrderCheck;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.util.MbxMath;
import com.solfini.util.StringUtil;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.meta.InstrumentMetaData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CopyTradeOrderBook extends GlobalOrderBook implements OrderBook, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(CopyTradeOrderBook.class);
  private static final ManyToManyConcurrentArrayQueueCustom<Message> COPY_TRADE_QUEUE = Context.getCopyTradeQueue();
  private static final ManyToOneConcurrentArrayQueueCustom<Message> MATCHER_TO_PUBLISHER_QUEUE = Context.getMatcherToPublisherQueue();
  private static final int NO_OF_THREADS = Context.getRouterThreadPoolCoreSize();
  //private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(NO_OF_THREADS);
  private static final ExecutorService EXECUTOR_SERVICE = Executors.newVirtualThreadPerTaskExecutor();

  private final int id;
  private final int priceScale;
  private final int quantityScale;
  private final int orderBookStrategy;
  private final int preOrderCheckStrategy;
  private final InstrumentPair instrumentPair;
  private final HashSet<Integer> userPartitionMap = new HashSet<>();

  private PreOrderCheck preOrderCheck;
  private final PreOrderCheck preOrderCheckOrig;
  private MarketStatus marketStatus;

  static {
    if (Context.isCopyTradeEnabled()) {
      for (int i = 1; i <= NO_OF_THREADS; i++) {
        EXECUTOR_SERVICE.submit(new Router());
      }
    }
  }

  public CopyTradeOrderBook(InstrumentPair pair, PreOrderCheck preOrderCheck, int orderBookStrategy, int preOrderCheckStrategy) {
    this.id = pair.getId();
    this.priceScale = pair.getPriceScale();
    this.quantityScale = pair.getQuantityScale();
    this.instrumentPair = pair;
    this.preOrderCheck = preOrderCheck;
    this.preOrderCheckOrig = preOrderCheck;
    if (MarketStatus.DR_MODE == Context.getMarketStatus()) {
      this.preOrderCheck = new NoPreOrderCheck();
    } else if (pair.getMarketStatus() != null) {
      this.marketStatus = pair.getMarketStatus();
    }

    this.orderBookStrategy = orderBookStrategy;
    this.preOrderCheckStrategy = preOrderCheckStrategy;

    String userPartitionIds = Context.getCopyTradeUserPartitionIds();
    String[] partitionIds = userPartitionIds.split(",");
    for (String partitionId : partitionIds) {
      this.userPartitionMap.add(Integer.parseInt(partitionId));
    }
  }

  @Override
  public int getId() {
    return id;
  }

  @Override
  public int getQuanityScale() {
    return quantityScale;
  }

  @Override
  public int getPriceScale() {
    return priceScale;
  }

  @Override
  public int getOrderCount() {
    return 0;
  }

  @Override
  public void setFilledCount(final long newValue) {
  }

  @Override
  public void setFilledCountIfGreater(final long newValue) {
  }

  @Override
  public long getFilledCount() {
    return 0;
  }

  @Override
  public void setSecondaryOrderIdIfGreater(final long newValue) {
  }

  @Override
  public long getSecondaryOrderId() {
    return 0;
  }

  @Override
  public MarketStatus getMarketStatus() {
    return marketStatus;
  }

  @Override
  public Instrument getSettleCoinUsdMarkInstrument() {
    return null;
  }

  @Override
  public InstrumentPair getInstrumentPair() {
    return instrumentPair;
  }

  @Override
  public void setSettleCoinUsdMarkInstrument(final Instrument settleCoinUsdMarkInstrument) {
  }

  @Override
  public StopLimitContainer getStopLimitContainer() {
    return null;
  }

  @Override
  public StopProfitContainer getStopProfitContainer() {
    return null;
  }

  @Override
  public int getOrderBookStrategy() {
    return orderBookStrategy;
  }

  @Override
  public int getPreOrderCheckStrategy() {
    return preOrderCheckStrategy;
  }

  @Override
  public int getBid() {
    return 0;
  }

  @Override
  public int getAsk() {
    return 0;
  }

  @Override
  public int getLast() {
    return 0;
  }

  @Override
  public int getMark() {
    return 0;
  }

  @Override
  public TrailingStopContainer getTrailingStopContainer() {
    return null;
  }

  @Override
  public void setMark(final int mark) {
  }

  @Override
  public double getUsdMark() {
    return 0;
  }

  @Override
  public PreOrderCheck getPreOrderCheck() {
    return null;
  }

  @Override
  public void addOrder(final Order order) {
    if (!Context.isCopyTradeEnabled()) {
      LOGGER.error("Copy trade not enabled.");
      return;
    }
    final InstrumentPair pair = InstrumentCache.getPair(order.getSecurityId());
    if (pair == null) {
      LOGGER.error("Invalid instrument pair. id: " + order.getSecurityId() + " clOrdId: " + order.getClOrdId());
      return;
    }
    if (!order.isToClose() && (order.getSymbol() == null || order.getSymbol().isEmpty())) {
      LOGGER.error("Invalid symbol. symbol: " + order.getSymbol() + " clOrdId: " + order.getClOrdId());
      return;
    }

    //check for side change
    boolean hasSideChange = false;
    if (!order.isToClose() && order.getPlatform() != null && !order.getPlatform()
        .isEmpty() && order.getAccountId() != null && !order.getAccountId().isEmpty()) {
      Side openSide = CopyTradeCache.getOpenSide(order.getPlatform(), order.getAccountId());
      hasSideChange = openSide != null && openSide != order.getSide();
      if (hasSideChange) {
        LOGGER.info(Constants.LOG_FMT_2, "Order has a side change. platform: ", order.getPlatform(), " accountId: ", order.getAccountId(),
            " orderSide: ", order.getSide().name(), " openSide: ", openSide.name(), " order: ", order.getClOrdId());
        order.setToClose(true);
      }
    }

    if (!order.isToClose()) {//open positions
      final Collection<InfluencerSubscription> influencerSubscriptions =
          InfluencerSubscriptionCache.getSubscriptions(order.getPlatform(), order.getAccountId());
      if (influencerSubscriptions != null) {
        final List<InfluencerSubscription> subscriptionList = new ArrayList<>(influencerSubscriptions);
        Collections.shuffle(subscriptionList);
        String baseSymbol = order.getSymbol();
        String quotedSymbol = null;

        for (InfluencerSubscription subscription : subscriptionList) {
          if (!userPartitionMap.contains(subscription.getUserId() % Context.getNoOfTotalCopyTradeUserPartitions())) {
            LOGGER.info(Constants.LOG_FMT_2, "User does not belong to this partition. userId: ", subscription.getUserId());
            continue;
          }
          //quotedSymbol = subscription.getPreferredQuoteCurrency();
          quotedSymbol = MarketDepthCache.getBestQuoteCurrency(subscription.getExchange(), baseSymbol, order.getSide(),
              subscription.isFuturesEnabled());
          if (quotedSymbol != null) {
            if (subscription.getPreferredCurrencies() != null && subscription.getPreferredCurrencies().length() > 2) {
              if (!subscription.getPreferredCurrencies().contains(baseSymbol)) {
                LOGGER.info(Constants.LOG_FMT_2, "Symbol is not in the preferred list. symbol: ", baseSymbol, "/", quotedSymbol,
                    " exchange: ", subscription.getExchange(), " subscription: ", subscription.getId(), " order: ", order.getClOrdId());
                continue;
              }
            }
            if (subscription.isHasPendingClose()) {
              LOGGER.info(Constants.LOG_FMT_2, "Subscription has pending close orders. symbol: ", baseSymbol, "/", quotedSymbol,
                  " exchange: ", subscription.getExchange(), " subscription: ", subscription.getId(), " order: ", order.getClOrdId());
              continue;
            }
            if (ExternalInstrumentCache.isTradeableOnExchange(subscription.getExchange(), baseSymbol, quotedSymbol, subscription.isFuturesEnabled())) {
              final String clOrdId = order.getClOrdId() + subscription.getId();
              final CopyTrade openCopyTrade = new CopyTrade(clOrdId, baseSymbol, quotedSymbol, pair, order, subscription);

              openCopyTrade.setKafkaRecordOffset(order.getKafkaRecordOffset());

              COPY_TRADE_QUEUE.addGuaranteed(openCopyTrade);
            }
          } else {
            LOGGER.info(Constants.LOG_FMT_2, "Symbol is not tradable on the exchange. symbol: ", baseSymbol, "/", quotedSymbol,
                " exchange: ", subscription.getExchange(), " subscription: ", subscription.getId(), " order: ", order.getClOrdId());
          }
        }
        final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(order, pair);
        MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(executionReportMessage);
      } else {
        LOGGER.info(Constants.LOG_FMT_4, "No subscriptions for platform: ", order.getPlatform(), " accountId: ", order.getAccountId());
        final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(order, pair);
        MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(executionReportMessage);
      }
      LOGGER.info(Constants.LOG_FMT_2, "Open copy trade request completed. clOrdId: ", order.getClOrdId());
    } else {//close positions
      String closeMode = null;
      Collection<CopyTradeCache.CopyTradeData> copyTradeData = null;
      if (order.getAccountId() == null || order.getAccountId().isEmpty()) {
        closeMode = "GLOBAL";
        copyTradeData = CopyTradeCache.getAllCopyTrades();
      } else {
        closeMode = "ACCOUNT";
        copyTradeData = CopyTradeCache.getCopyTrades(order.getPlatform(), order.getAccountId());
      }

      //todo shuffle
      int count = 0;
      if (copyTradeData != null && !copyTradeData.isEmpty()) {
        for (CopyTradeCache.CopyTradeData data : copyTradeData) {
          for (CopyTrade openCopyTrade : data.getCopyTrades()) {
            boolean validSymbolToClose = false;
            if ("GLOBAL".equalsIgnoreCase(closeMode)) {
              validSymbolToClose = true;//all sumbols in all accounts
            } else if (order.getSymbol() == null) {
              validSymbolToClose = true;//all symbols in given account
            } else {//exact symbol
              validSymbolToClose = order.getSymbol() != null && !order.getSymbol().isEmpty() && order.getSymbol()
                  .equalsIgnoreCase(openCopyTrade.getBaseSymbol());
            }

            if (!userPartitionMap.contains(openCopyTrade.getUserId() % Context.getNoOfTotalCopyTradeUserPartitions())) {
              LOGGER.info(Constants.LOG_FMT_2, "User does not belong to this partition. userId: ", openCopyTrade.getUserId());
              continue;
            }

            if (openCopyTrade.isSuccessful() && validSymbolToClose && !openCopyTrade.isClosed() && !openCopyTrade.isToClose()) {
              final String clOrdId = order.getClOrdId() + openCopyTrade.getSubscriptionId() + String.valueOf(++count);
              final CopyTrade closeCopyTrade = new CopyTrade(openCopyTrade);
              closeCopyTrade.setClOrdId(clOrdId);
              closeCopyTrade.setOrigClOrdId(openCopyTrade.getClOrdId());
              closeCopyTrade.setSide(openCopyTrade.getSide() == Side.BUY ? Side.SELL : Side.BUY);
              closeCopyTrade.setOrdType(OrdType.LIMIT);
              closeCopyTrade.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
              closeCopyTrade.setToClose(true);
              closeCopyTrade.setCreated(System.currentTimeMillis());
              closeCopyTrade.setResult(null);
              closeCopyTrade.setExternalId(null);
              closeCopyTrade.setStatus(null);
              closeCopyTrade.setToClose(order.isToClose());
              closeCopyTrade.setOpenOrder(openCopyTrade);
              closeCopyTrade.setKafkaRecordOffset(order.getKafkaRecordOffset());
              closeCopyTrade.setSourceSendTime(order.getSourceSendTime());

              COPY_TRADE_QUEUE.addGuaranteed(closeCopyTrade);
            }
          }
        }
      } else {
        LOGGER.info(Constants.LOG_FMT_4, "No copy trades to close for platform: ", order.getPlatform(), " accountId: ",
            order.getAccountId());
        final Collection<CopyTrade> openCopyTrades = CopyTradeCache.getAllOpenCopyTrades();
        //check order status from exchange
        for (CopyTrade copyTrade : openCopyTrades) {
          if (!ORDER_STATUS_FILLED.equalsIgnoreCase(copyTrade.getStatus())) {
            COPY_TRADE_QUEUE.addGuaranteed(copyTrade);
          }
        }
      }

      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(order, pair);
      MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(executionReportMessage);

      LOGGER.info(Constants.LOG_FMT_2, "Close copy trade request completed. clOrdId: ", order.getClOrdId(), " count: ", count, " mode: ",
          closeMode);
    }

  }

  @Override
  public void cancelOrder(final CancelOrder cancelOrder) {
  }

  @Override
  public void uncross() {
  }

  @Override
  public String toString(final int priceLevel) {
    return null;
  }

  @Override
  public void changeState(final MarketStatus marketStatus, long snapId, Message causingMessage) {
    switch (marketStatus) {
      case OPEN:
        this.marketStatus = marketStatus;
        uncross();
        break;
      case CLOSE:
        this.marketStatus = marketStatus;
        expireLiveSessionOrders();
        break;
      case PAUSE:
        this.marketStatus = marketStatus;
        break;
      case CIRCUIT_BREAKER:
        this.marketStatus = marketStatus;
        break;
      case PREOPEN:
        this.marketStatus = marketStatus;
        break;
      case RESTATE:
        restate(snapId, causingMessage);
        break;
      case DR_MODE:
        this.marketStatus = marketStatus;
        this.preOrderCheck = new NoPreOrderCheck();
        restate(snapId, causingMessage);
        break;
      case DR_TO_OPEN:
        this.marketStatus = MarketStatus.OPEN;
        this.preOrderCheck = preOrderCheckOrig;
        uncross();
        restate(snapId, causingMessage);
        break;
      case OPEN_AUCTION:
        this.marketStatus = marketStatus;
        break;
      case CLOSE_AUCTION:
        this.marketStatus = marketStatus;
        break;
      case CANCEL_AUCTION:
        this.marketStatus = marketStatus;
        this.marketStatus = MarketStatus.OPEN;
        this.instrumentPair.setMarketStatus(MarketStatus.OPEN);
        break;
      default:
        break;
    }
    this.instrumentPair.setMarketStatus(marketStatus);
  }

  @Override
  public void expireLiveSessionOrders() {
  }

  @Override
  public void restate(final long snapId, final Message causingMessage) {
  }

  @Override
  public void cancelReplaceOrder(final CancelReplaceOrder cancelReplaceOrder) {
  }

  @Override
  public void massCancelOrder(final MassCancelOrder massCancelOrder) {
  }

  @Override
  public MarketDataSnapshotFullRefreshEncoder build(final MarketDataSnapshotFullRefreshEncoder marketDataSnapshotFullRefreshEncoder) {
    if (marketStatus != null)
      marketDataSnapshotFullRefreshEncoder.marketStatus(marketStatus.value());
    marketDataSnapshotFullRefreshEncoder.usdMark(instrumentPair.getIndexFeedUsdMark());
    marketDataSnapshotFullRefreshEncoder.fundingRateTime(instrumentPair.getFundingRateTime());
    marketDataSnapshotFullRefreshEncoder.estFundingRate(instrumentPair.getEstFundingRate());

    MarketDataSnapshotFullRefreshEncoder.MdEntrieGroupEncoder entry = marketDataSnapshotFullRefreshEncoder.mdEntrieGroupCount(0);

    return marketDataSnapshotFullRefreshEncoder;
  }

  @Override
  public void expireSettlePosition(final int markInSettleCoin) {
  }

  @Override
  public void updateSecurityDefinition(final InstrumentPair instrumentPair) {
    final SecurityDefinitionAdminMessage message = new SecurityDefinitionAdminMessage(instrumentPair);
    message.setUpdateType(UpdateType.PATCH);
    MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(message);
  }

  @Override
  public Order onCollateralSwapOrder(final User user, final long price, final short price_scale, final long qty, final short qty_scale,
      final Side side) {
    return null;
  }

  @Override
  public Order onUnderlyerPhysicalSettle(final User user, final long price, final short price_scale, final long qty, final short qty_scale,
      final Side side) {
    return null;
  }

  @Override
  public void onLiquidationOrder(final LiquidationOrder liquidationOrder) {
  }

  @Override
  public void disableOutputQueue() {
  }

  @Override
  public void restoreOutputQueue() {
  }

  @Override
  public OrderBookValidator getOrderBookValidator() {
    return null;
  }

  @Override
  public void copyTo(final OrderBook target, final Transform transform) {
  }

  @Override
  public void reclaim() {
  }

  @Override
  public void addOrderDR(final DROrder order) {
  }

  @Override
  public void cancelOrderDR(final DRCancelOrder cancelOrder) {
  }

  @Override
  public void execReportDR(final DRExecutionReport executionReport) {
  }

  @Override
  public void clearOrderBook() {
  }

  @Override
  public Order buildAlgoOrder(final Order source) {
    return null;
  }

  @Override
  public void expireAllOrders() {
  }

  private static org.knowm.xchange.instrument.Instrument getInstrument(final String exchange, final XExchange xExchange, final CurrencyPair currencyPair,
      final boolean isFuture) {
    String key = exchange + "_" + currencyPair.toString() + isFuture;
    org.knowm.xchange.instrument.Instrument instrument = ExternalInstrumentCache.getInstrument(key);
    if (instrument != null) {
      return instrument;
    } else {
      instrument = xExchange.getInstrument(currencyPair, isFuture);
      ExternalInstrumentCache.addInstrument(key, instrument);
      return instrument;
    }
  }

  public static class Router implements Runnable {
    private final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();

    @Override
    public void run() {
      while (true) {
        try {
          final Message message = COPY_TRADE_QUEUE.poll();

          if (message == null)
            continue;

          if (message instanceof CopyTrade copyTrade) {
            LOGGER.info(Constants.LOG_FMT_2, "Processing copy trade: ", copyTrade.getClOrdId(), " isToClose: ", copyTrade.isToClose());
            if (copyTrade.isToClose()) {
              if (copyTrade.getStatus() != null) {
                updateOrderStatus(copyTrade);
              } else {
                processCloseOrder(copyTrade, copyTrade.getOpenOrder());
              }
            } else {
              processOpenOrder(copyTrade);
            }
          } else {
            LOGGER.warn("Invalid message: " + message.toJSON());
          }

        } catch (Exception e) {
          LOGGER.error(Constants.ERROR_LOG, e);
        }
      }
    }

    private void processOpenOrder(final CopyTrade copyTrade) {
      final String clOrdId = copyTrade.getClOrdId();
      final long ordQtyPercentage = copyTrade.getSignalPercentage();
      final short ordQtyPercentageScale = copyTrade.getSignalPercentageScale();
      //final long ordPrice = copyTrade.getSignalPrice();
      //final short ordPriceScale = 2;
      final InfluencerSubscription subscription = copyTrade.getSubscription();

      final Side side = subscription.getInverseTrade() == 0 ? copyTrade.getSide() : copyTrade.getInverseSide();
      final String baseSymbol = copyTrade.getBaseSymbol();
      final String quotedSymbol = copyTrade.getQuotedSymbol();
      //currencyPair is used as a synchronise lock
      final CurrencyPair currencyPair = ExternalCurrencyPairCache.get(baseSymbol, quotedSymbol);
      double maxTradeValue = MbxMath.scaleDown(subscription.getAmountWithLeverage(), 2);
      double availableMaxAmount = MbxMath.scaleDown(subscription.getAvailableMaxAmount(), 2);
      final long now = System.currentTimeMillis();

      if ((copyTrade.getSourceSendTime() + Context.getMaxDelayToOpenOrderInMs()) < now) {
        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " timeout. sent: ", copyTrade.getSourceSendTime(),
            " processed: ", now);

        copyTrade.setResult("REJECTED: Timeout.");
        matcherToPublisherQueue.addGuaranteed(copyTrade);

        return;
      }

      LOGGER.info(Constants.LOG_FMT_2, "Order processing. clOrdId: ", clOrdId, " maxTradeValue: ", maxTradeValue);

      copyTrade.setSide(side);

      final XExchange xExchange = ExternalExchangeUtil.createXExchange(subscription);
      if (xExchange == null) {
        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " invalid exchange: ", subscription.getExchange());

        copyTrade.setResult("REJECTED: Invalid exchange.");
        matcherToPublisherQueue.addGuaranteed(copyTrade);

        return;
      }

      org.knowm.xchange.instrument.Instrument instrument = getInstrument(subscription.getExchange(), xExchange, currencyPair, subscription.isFuturesEnabled());
      if (instrument == null) {
        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " invalid instrument: ", baseSymbol, " ", quotedSymbol);

        copyTrade.setResult("REJECTED: Invalid instrument. " + baseSymbol + " " + quotedSymbol);
        matcherToPublisherQueue.addGuaranteed(copyTrade);

        return;
      }

      copyTrade.setxExchange(xExchange);
      copyTrade.setCurrencyPair(currencyPair);
      copyTrade.setInstrument(instrument);
      //get price
      double price = ExternalExchangeHandler.getPrice(subscription, currencyPair, instrument, side, xExchange);

      //check exchange balance
      XExchange.Balance balance = null;
      if (side == Side.BUY) {
        balance = ExternalExchangeHandler.getStableCoinBalance(subscription, xExchange);
        double totalStableCoinBalance = balance.getTotalStableCoinBalance();
        if (subscription.getPercentage() <= 10_000 /* no margin */) {
          if (totalStableCoinBalance <= 0) {
            LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " exchangeBalance: ", totalStableCoinBalance);

            copyTrade.setResult("REJECTED: Insufficient balance. balance: " + totalStableCoinBalance + ".");
            copyTrade.setxExchange(null);
            matcherToPublisherQueue.addGuaranteed(copyTrade);

            return;
          }
          maxTradeValue = Math.min(availableMaxAmount, totalStableCoinBalance);
          LOGGER.info(Constants.LOG_FMT_6, "Max trade value adjusted based on the exchange balance. clOrdId: ", clOrdId,
              " new maxTradeValue: ", maxTradeValue, " exchange balance: ", totalStableCoinBalance);
        }
      } else {
        balance = ExternalExchangeHandler.getBalance(subscription, xExchange, baseSymbol);
        if (subscription.getPercentage() <= 10_000 /* no margin */) {
          if (balance.getCoinBalance() <= 0) {
            LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " exchangeBalance: ", balance.getCoinBalance());

            copyTrade.setResult("REJECTED: Insufficient balance. balance: " + balance.getCoinBalance() + " " + baseSymbol + ".");
            copyTrade.setxExchange(null);
            matcherToPublisherQueue.addGuaranteed(copyTrade);

            return;
          }
          double maxExchangeValue = balance.getCoinBalance() * price;
          maxTradeValue = Math.min(availableMaxAmount, maxExchangeValue);
          LOGGER.info(Constants.LOG_FMT_6, "Max trade value adjusted based on the exchange balance. clOrdId: ", clOrdId,
              " new maxTradeValue: ", maxTradeValue, " exchange balance: ", maxExchangeValue);
        }
      }

      Set<String> symbolsToCalculateMarketCapRatio = null;
      //calculate based on user preferred currencies
      if (subscription.getPreferredCurrencies() != null && subscription.getPreferredCurrencies().length() > 2) {
        symbolsToCalculateMarketCapRatio = Set.of(subscription.getPreferredCurrencies().split("-"));
      }
      //calculate based on infulencer trade currencies
      if (symbolsToCalculateMarketCapRatio == null) {
        symbolsToCalculateMarketCapRatio = InfluencerSymbolsCache.getUserSymbols(subscription.getAccountId());
      }

      //calculate max trade value based on market cap of preferred symbols
      if (symbolsToCalculateMarketCapRatio != null && !symbolsToCalculateMarketCapRatio.isEmpty()) {
        double marketCapOfSymbol = 0, marketCapOdPreferredSymbols = 0;
        MarketCapCache.MarketCap marketCap = MarketCapCache.getMarketCap(baseSymbol);
        if (marketCap == null) {
          LOGGER.info(Constants.LOG_FMT_4, "Unable to load market cap for the order. clOrdId: ", clOrdId, " symbol: ", baseSymbol);
          copyTrade.setResult("REJECTED: Failed to fetch market cap.");
          copyTrade.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTrade);

          return;
        }
        marketCapOfSymbol = marketCap.getMarketCap();
        for (String s : symbolsToCalculateMarketCapRatio) {
          marketCap = MarketCapCache.getMarketCap(s);
          marketCapOdPreferredSymbols += marketCap.getMarketCap();
        }
        if (marketCapOfSymbol == 0 || marketCapOdPreferredSymbols == 0) {
          LOGGER.info(Constants.LOG_FMT_4, "Unable to load market cap for the order. clOrdId: ", clOrdId, " symbol: ", baseSymbol);
          copyTrade.setResult("REJECTED: Failed to fetch market cap.");
          copyTrade.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTrade);

          return;
        }
        maxTradeValue = maxTradeValue * marketCapOfSymbol / marketCapOdPreferredSymbols;
        LOGGER.info(Constants.LOG_FMT_6, "Max trade value adjusted based on the market cap. clOrdId: ", clOrdId, " new maxTradeValue: ",
            maxTradeValue, " marketCapRatio: ", (marketCapOfSymbol / marketCapOdPreferredSymbols));
      } else {
        //decide trade value based on market cap
        final MarketCapCache.MarketCap marketCap = MarketCapCache.getMarketCap(baseSymbol);
        if (marketCap != null) {
          maxTradeValue = maxTradeValue * marketCap.getMarketCapDominance() / 100;
          LOGGER.info(Constants.LOG_FMT_6, "Max trade value adjusted based on the market cap. clOrdId: ", clOrdId, " new maxTradeValue: ",
              maxTradeValue, " marketCap: ", marketCap.getMarketCapDominance());
        } else {
          LOGGER.info(Constants.LOG_FMT_4, "Unable to load market cap for the order. clOrdId: ", clOrdId, " symbol: ", baseSymbol);
          copyTrade.setResult("REJECTED: Failed to fetch market cap.");
          copyTrade.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTrade);

          return;
        }
      }
      double maxRemainingAmount = maxTradeValue;
      //check current open order value for the symbol
      //      double openOrderValue = CopyTradeCache.getOpenOrderValue(copyTrade.getSubscriptionId(), quotedSymbol, baseSymbol);
      //      if (openOrderValue > 0) {
      //        maxRemainingAmount = maxTradeValue - openOrderValue;
      //        LOGGER.info(Constants.LOG_FMT_6, "Max trade value adjusted based on current open orders. clOrdId: ", clOrdId, " new maxTradeValue: ",
      //            maxRemainingAmount, " openOrderValue: ", openOrderValue);
      //      }

      LOGGER.info(Constants.LOG_FMT_2, "Max trade value for order: ", copyTrade.getClOrdId(), " is ", maxRemainingAmount);
      if (maxRemainingAmount < Context.getMinCopyTradeAmountInUsd()) {
        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " insufficient maxTradeValue: ", maxRemainingAmount);

        copyTrade.setResult("REJECTED: Trade value is too low.");
        copyTrade.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(copyTrade);

        return;
      }

      BigDecimal xPrice = new BigDecimal(price);
      xPrice = xPrice.setScale(2, RoundingMode.HALF_UP);

      if (price == 0) {
        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " price: " + price);

        copyTrade.setResult("REJECTED: Failed to fetch price.");
        copyTrade.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(copyTrade);

        return;
      }

      //percentage set by the influencer
      double signalTradePercentage = MbxMath.scaleDown(ordQtyPercentage, ordQtyPercentageScale) / 100D;
      //percentage of subscription amount assigned by the user
      double userDefinedPercentage = MbxMath.scaleDown(Math.min(subscription.getPercentage(), 10000), 2) / 100D;
      double signalTradeValue = Math.min(maxRemainingAmount, maxTradeValue * signalTradePercentage * userDefinedPercentage);//0.01
      if (signalTradeValue < Context.getMinCopyTradeAmountInUsd()) {
        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " insufficient maxTradeValue: ", signalTradeValue);

        copyTrade.setResult("REJECTED: Trade value is too small. (" + signalTradeValue + ")");
        copyTrade.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(copyTrade);

        return;
      }

      LOGGER.info(Constants.LOG_FMT_2, "Trade value for order: ", copyTrade.getClOrdId(), " is ", signalTradeValue);
      /* signalTradeValue = Math.min(availableBalance, signalTradeValue);*/
      //convert stable coins if balance is insufficient
      if (subscription.getPercentage() <= 10_000 /* no margin */ && balance.getBalance(
          quotedSymbol) < signalTradeValue && side == Side.BUY) {
        double balanceRequired = signalTradeValue * Context.getCopyTradeStableCoinConversionSafeFactor();
        if (balanceRequired > balance.getTotalStableCoinBalance()) {
          LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " exchangeBalance: ", balance.getTotalStableCoinBalance());

          copyTrade.setResult("REJECTED: Insufficient balance. balance: " + balance.getTotalStableCoinBalance() + ".");
          copyTrade.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTrade);

          return;
        }
        double usd = balance.getUsdBalance();
        double usdc = balance.getUsdcBalance();
        double usdt = balance.getUsdtBalance();
        try {
          if (USD.equalsIgnoreCase(quotedSymbol)) {// best quote is in USD. convert other stable coins to USD by selling
            double availableBalance = usd;
            if (usdt > 0) {
              final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDT, USD);
              org.knowm.xchange.instrument.Instrument conversionInstrument = getInstrument(subscription.getExchange(), xExchange, conversionPair, false);
              double p = ExternalExchangeHandler.getPrice(subscription, conversionPair, conversionInstrument, Side.SELL, xExchange);
              double usdtAmountToConvert = Math.min(usdt, (balanceRequired - availableBalance));
              double convertedAmount =
                  xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument, new BigDecimal(usdtAmountToConvert),
                      new BigDecimal(p), Side.SELL, clOrdId + "S1");
              availableBalance += convertedAmount;
            }
            if (usdc > 0 && availableBalance < balanceRequired) {
              final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDC, USD);
              org.knowm.xchange.instrument.Instrument conversionInstrument = getInstrument(subscription.getExchange(), xExchange, conversionPair, false);
              double p = ExternalExchangeHandler.getPrice(subscription, conversionPair, conversionInstrument, Side.SELL, xExchange);
              double usdcAmountToConvert = Math.min(usdc, (balanceRequired - availableBalance));
              double convertedAmount =
                  xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument, new BigDecimal(usdcAmountToConvert),
                      new BigDecimal(p), Side.SELL, clOrdId + "S2");
              availableBalance += convertedAmount;
            }
          } else if (USDC.equalsIgnoreCase(quotedSymbol)) {// best quote is in USDC. convert other stable coins to USDC
            double availableBalance = usdc;
            if (usdt > 0) {
              final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDC, USDT);
              org.knowm.xchange.instrument.Instrument conversionInstrument = getInstrument(subscription.getExchange(), xExchange, conversionPair, false);
              double p = ExternalExchangeHandler.getPrice(subscription, conversionPair, conversionInstrument, Side.BUY, xExchange);
              double usdtAmountToConvert = Math.min(usdt, (balanceRequired - availableBalance));
              double convertedAmount =
                  xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument, new BigDecimal(usdtAmountToConvert),
                      new BigDecimal(p), Side.BUY, clOrdId + "B1");
              availableBalance += convertedAmount;
            }
            if (usd > 0 && availableBalance < balanceRequired) {
              final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDC, USD);
              org.knowm.xchange.instrument.Instrument conversionInstrument = getInstrument(subscription.getExchange(), xExchange, conversionPair, false);
              double p = ExternalExchangeHandler.getPrice(subscription, conversionPair, conversionInstrument, Side.BUY, xExchange);
              double usdAmountToConvert = Math.min(usd, (balanceRequired - availableBalance));
              double convertedAmount =
                  xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument, new BigDecimal(usdAmountToConvert),
                      new BigDecimal(p), Side.BUY, clOrdId + "B2");
              availableBalance += convertedAmount;
            }
          } else if (USDT.equalsIgnoreCase(quotedSymbol)) {// best quote is in USDT. convert other stable coins to USDT
            double availableBalance = usdt;
            if (usdc > 0) {
              final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDC, USDT);
              org.knowm.xchange.instrument.Instrument conversionInstrument = getInstrument(subscription.getExchange(), xExchange, conversionPair, false);
              double p = ExternalExchangeHandler.getPrice(subscription, conversionPair, conversionInstrument, Side.SELL, xExchange);
              double usdcAmountToConvert = Math.min(usdc, (balanceRequired - availableBalance));
              double convertedAmount =
                  xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument, new BigDecimal(usdcAmountToConvert),
                      new BigDecimal(p), Side.SELL, clOrdId + "S1");
              availableBalance += convertedAmount;
            }
            if (availableBalance < balanceRequired && usd > 0) {
              final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDT, USD);
              org.knowm.xchange.instrument.Instrument conversionInstrument = getInstrument(subscription.getExchange(), xExchange, conversionPair, false);
              double p = ExternalExchangeHandler.getPrice(subscription, conversionPair, conversionInstrument, Side.BUY, xExchange);
              double usdAmountToConvert = Math.min(usd, (balanceRequired - availableBalance));
              double convertedAmount =
                  xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument, new BigDecimal(usdAmountToConvert),
                      new BigDecimal(p), Side.BUY, clOrdId + "B2");
              availableBalance += convertedAmount;
            }
          }
          signalTradeValue = availableMaxAmount;
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, e);
          LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " failed to convert.");

          copyTrade.setResult("REJECTED: Failed to convert stable coins.");
          copyTrade.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTrade);

          return;
        }
      }
      double quantity = MbxMath.roundToBestPrecision(signalTradeValue / price);

      copyTrade.setOrderQty(MbxMath.changeScale(quantity, 4));
      copyTrade.setOrderQtyScale((short) 4);

      BigDecimal xQuantity = new BigDecimal(quantity);
      xQuantity = xQuantity.setScale(4, RoundingMode.HALF_UP);

      InstrumentMetaData instrumentMetaData = null;
      try {
        instrumentMetaData = xExchange.getExchangeMetaData().getInstruments().get(instrument);
      } catch (Exception e) {
        LOGGER.info(Constants.LOG_FMT_2, "Instrument metadata not available. clOrdId ", clOrdId);
      }
      if (instrumentMetaData != null) {
        if (instrumentMetaData.getMinimumAmount() != null && instrumentMetaData.getMinimumAmount().compareTo(xQuantity) > 0) {
          LOGGER.info(Constants.LOG_FMT_2, "Order rejected. Insufficient quantity. clOrdId: ", clOrdId, " qty: ", xQuantity,
              " min allowed: " + instrumentMetaData.getMinimumAmount());

          copyTrade.setResult("REJECTED: Insufficient qty.");
          copyTrade.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTrade);

          return;
        }

        if (instrumentMetaData.getMaximumAmount() != null && instrumentMetaData.getMaximumAmount().compareTo(xQuantity) < 0) {
          LOGGER.info(Constants.LOG_FMT_2, "Max quantity reached clOrdId: ", clOrdId, " qty: ", xQuantity,
              " max allowed: " + instrumentMetaData.getMaximumAmount());
          xQuantity = instrumentMetaData.getMaximumAmount();
        }

        if (instrumentMetaData.getVolumeScale() != null) {
          xQuantity = xQuantity.setScale(instrumentMetaData.getVolumeScale(), RoundingMode.FLOOR);
        }
        if (instrumentMetaData.getPriceScale() != null) {
          xPrice = xPrice.setScale(instrumentMetaData.getPriceScale(), RoundingMode.FLOOR);
        }

      }
      copyTrade.setxQuantity(xQuantity);
      copyTrade.setxPrice(xPrice);
      copyTrade.setTradeValue(signalTradeValue);
      try {
        xExchange.placeOrder(copyTrade);
        copyTrade.setClosed(false);
        CopyTradeCache.add(copyTrade);

        //if (copyTrade.getStatus().equalsIgnoreCase("FILLED")) {
        subscription.setAvailableMaxAmount(subscription.getAvailableMaxAmount() - MbxMath.changeScale(signalTradeValue, 2));
        matcherToPublisherQueue.addGuaranteed(subscription);
        //}

        LOGGER.info(Constants.LOG_FMT_2, "Copy trade (open) successful. clOrdId: ", clOrdId, " symbol: ", baseSymbol.toUpperCase(), "/",
            quotedSymbol.toUpperCase(), " side: ", copyTrade.getSide().name(), " quantity: ", xQuantity, " price: ", copyTrade.getPrice(),
            " orderId:", copyTrade.getExternalId(), " result: ", copyTrade.getResult());
      } catch (Exception e) {
        copyTrade.setResult("FAILED: " + e.getMessage());
        LOGGER.error("Error, Copy trade failed. clOrdId: " + clOrdId, e);
      }

      copyTrade.setxExchange(null);
      matcherToPublisherQueue.addGuaranteed(copyTrade);

    }

    private void processCloseOrder(final CopyTrade closeCopyTrade, final CopyTrade openCopyTrade) {
      final String clOrdId = closeCopyTrade.getClOrdId();
      final long now = System.currentTimeMillis();

      if ((closeCopyTrade.getSourceSendTime() + Context.getMaxDelayToCloseOrderInMs()) < now) {
        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " timeout. sent: ", closeCopyTrade.getSourceSendTime(),
            " processed: ", now);

        closeCopyTrade.setResult("REJECTED: timeout.");
        matcherToPublisherQueue.addGuaranteed(closeCopyTrade);

        return;
      }

      final XExchange xExchange = ExternalExchangeUtil.createXExchange(closeCopyTrade.getSubscription());
      if (xExchange == null) {
        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " invalid exchange: ",
            closeCopyTrade.getSubscription().getExchange());

        closeCopyTrade.setResult("REJECTED: Invalid exchange.");
        closeCopyTrade.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(closeCopyTrade);

        return;
      }
      closeCopyTrade.setxExchange(xExchange);

      final CurrencyPair currencyPair = ExternalCurrencyPairCache.get(closeCopyTrade.getBaseSymbol(), closeCopyTrade.getQuotedSymbol());
      final org.knowm.xchange.instrument.Instrument instrument =
          getInstrument(closeCopyTrade.getSubscription().getExchange(), xExchange, currencyPair, closeCopyTrade.getSubscription().isFuturesEnabled());
      if (instrument == null) {
        closeCopyTrade.setResult(
            "REJECTED: Invalid instrument. " + currencyPair.getBase().getSymbol() + " " + currencyPair.getCounter().getSymbol());
        closeCopyTrade.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(closeCopyTrade);

        return;
      }
      double price =
          ExternalExchangeHandler.getPrice(closeCopyTrade.getSubscription(), currencyPair, instrument, closeCopyTrade.getSide(), xExchange);

      closeCopyTrade.setCurrencyPair(currencyPair);
      closeCopyTrade.setInstrument(instrument);
      openCopyTrade.setCurrencyPair(currencyPair);
      openCopyTrade.setInstrument(instrument);

      if (price == 0) {
        LOGGER.warn(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " price: " + price);

        closeCopyTrade.setResult("REJECTED: Failed to fetch price.");
        closeCopyTrade.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(closeCopyTrade);

        return;
      }
      //closePricePercentage is scaled by 4
      long closePricePercentage =
          ExternalInstrumentCache.getClosePricePercentage(closeCopyTrade.getExchange(), closeCopyTrade.getBaseSymbol(),
              closeCopyTrade.getQuotedSymbol(), closeCopyTrade.isFuturesEnabled());
      if (Side.SELL == closeCopyTrade.getSide()) {
        price = price - (price * closePricePercentage) / 1000000D;
      } else {
        price = price + (price * closePricePercentage) / 1000000D;
      }
      BigDecimal xPrice = new BigDecimal(price);
      xPrice = xPrice.setScale(2, RoundingMode.HALF_UP);
      closeCopyTrade.setxPrice(xPrice);

      try {
        xExchange.updateOrderStatus(openCopyTrade);
        BigDecimal xQuantity = BigDecimal.valueOf(openCopyTrade.getCumulativeAmount());
        xQuantity = xQuantity.setScale(4, RoundingMode.HALF_UP);
        closeCopyTrade.setxQuantity(xQuantity);

        xExchange.placeOrder(closeCopyTrade);
        closeCopyTrade.setClosed(true);
        //update corresponding open order
        openCopyTrade.setClosed(true);
        openCopyTrade.setCloseClOrdId(closeCopyTrade.getClOrdId());
        CopyTradeCache.remove(openCopyTrade);
        matcherToPublisherQueue.addGuaranteed(openCopyTrade);

        if (ORDER_STATUS_FILLED.equalsIgnoreCase(closeCopyTrade.getStatus())) {
          closeCopyTrade.getSubscription().setAvailableMaxAmount(
              closeCopyTrade.getSubscription().getAvailableMaxAmount() + MbxMath.changeScale(xPrice.multiply(xQuantity).doubleValue(), 2));
          matcherToPublisherQueue.addGuaranteed(closeCopyTrade.getSubscription());
        } else {
          CopyTradeCache.addOpenOrder(closeCopyTrade);
        }

        LOGGER.info(Constants.LOG_FMT_2, "Copy trade (close) successful. clOrdId: ", clOrdId, " symbol: ",
            closeCopyTrade.getBaseSymbol().toUpperCase(), "/", closeCopyTrade.getQuotedSymbol().toUpperCase(), " side: ",
            closeCopyTrade.getSide().name(), " quantity: ", closeCopyTrade.getxQuantity(), " price: ", closeCopyTrade.getxPrice(),
            " orderId:", closeCopyTrade.getExternalId(), " result: ", closeCopyTrade.getResult());
      } catch (Exception e) {
        closeCopyTrade.setResult("FAILED: " + e.getMessage());
        LOGGER.error("Error, Copy trade failed. clOrdId: " + clOrdId, e);
      }
      //flag hasPending close
      if (closeCopyTrade.getResult() == null || closeCopyTrade.getResult().startsWith("FAILED")) {
        final InfluencerSubscription subscription = closeCopyTrade.getSubscription();
        subscription.setKafkaRecordOffset(closeCopyTrade.getKafkaRecordOffset());
        subscription.setHasPendingClose(true);
        matcherToPublisherQueue.addGuaranteed(subscription);
      }

      closeCopyTrade.setxExchange(null);
      matcherToPublisherQueue.addGuaranteed(closeCopyTrade);

    }

    private void updateOrderStatus(final CopyTrade copyTrade) throws Exception {
      XExchange xExchange = copyTrade.getxExchange();
      InfluencerSubscription subscription = copyTrade.getSubscription();
      if (xExchange == null) {
        if (subscription == null) {
          subscription = InfluencerSubscriptionCache.get(copyTrade.getSubscriptionId());
          copyTrade.setSubscription(subscription);
        }
        if (subscription != null) {
          xExchange = ExternalExchangeUtil.createXExchange(subscription);
        }
      }
      if (xExchange != null) {
        final String prevStatus = copyTrade.getStatus();
        xExchange.updateOrderStatus(copyTrade);
        if (!prevStatus.equalsIgnoreCase(copyTrade.getStatus()) && ORDER_STATUS_FILLED.equalsIgnoreCase(copyTrade.getStatus())) {
          CopyTradeCache.remove(copyTrade);
          CopyTradeCache.removeOpenOrder(copyTrade);

          subscription.setAvailableMaxAmount(subscription.getAvailableMaxAmount() + MbxMath.changeScale(
              MbxMath.scaleDown(copyTrade.getPrice(), copyTrade.getPriceScale()) * copyTrade.getCumulativeAmount(), 2));
          matcherToPublisherQueue.addGuaranteed(copyTrade);
          matcherToPublisherQueue.addGuaranteed(subscription);

          LOGGER.info(Constants.LOG_FMT_2, "Copy trade (status update) successful. clOrdId: ", copyTrade.getClOrdId(), " symbol: ",
              copyTrade.getBaseSymbol().toUpperCase(), "/", copyTrade.getQuotedSymbol().toUpperCase(), " side: ",
              copyTrade.getSide().name(), " quantity: ", copyTrade.getxQuantity(), " price: ", copyTrade.getxPrice(), " orderId:",
              copyTrade.getExternalId(), " result: ", copyTrade.getResult());

          return;
        } else if (!ORDER_STATUS_FILLED.equalsIgnoreCase(copyTrade.getStatus())) {
          subscription.setKafkaRecordOffset(copyTrade.getKafkaRecordOffset());
          subscription.setHasPendingClose(true);
          matcherToPublisherQueue.addGuaranteed(subscription);
        }
      }
      LOGGER.info(Constants.LOG_FMT_2, "Copy trade (status update) failed. clOrdId: ", copyTrade.getClOrdId(), " symbol: ",
          copyTrade.getBaseSymbol().toUpperCase(), "/", copyTrade.getQuotedSymbol().toUpperCase(), " side: ", copyTrade.getSide().name(),
          " quantity: ", copyTrade.getxQuantity(), " price: ", copyTrade.getxPrice(), " orderId:", copyTrade.getExternalId(), " result: ",
          copyTrade.getResult());

    }
  }
}
