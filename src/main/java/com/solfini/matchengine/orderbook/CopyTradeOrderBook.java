package com.solfini.matchengine.orderbook;

import com.solfini.common.*;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.copytrade.CopyTradeCache;
import com.solfini.matchengine.copytrade.*;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.matchengine.drmode.message.DRCancelOrder;
import com.solfini.matchengine.drmode.message.DRExecutionReport;
import com.solfini.matchengine.drmode.message.DROrder;
import com.solfini.matchengine.executionexchange.*;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.FastClientFactory;
import com.solfini.matchengine.liquidity.direct.aiGenerated.ExternalExchangeClient;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.internal.*;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.pool.OrderObjectPool;
import com.solfini.preordercheck.NoPreOrderCheck;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.MbxMath;
import org.agrona.concurrent.IdleStrategy;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.exceptions.ExchangeException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static com.solfini.matchengine.executionexchange.ExternalInstrumentCache.PRICE_PERCENTAGE_SCALE;

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
  private static InstrumentMetaData defaultMetadata;

  public static final String BINANCE = "binance";
  public static final String BYBIT = "bybit";

  static {
    defaultMetadata = new InstrumentMetaData.Builder().marketOrderEnabled(false).minimumAmount(new BigDecimal("50"))
        .maximumAmount(new BigDecimal("5000000")).priceScale(2).volumeScale(2).build();
    if (Context.isCopyTradeEnabled()) {
      for (int i = 1; i <= NO_OF_THREADS; i++) {
        EXECUTOR_SERVICE.submit(new Router(IdleStrategyFactory.create(Context.getCopyTradeThreadIdle())));
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
        LOGGER.info(Constants.LOG_FMT_10, "Order has a side change. platform: ", order.getPlatform(), " accountId: ", order.getAccountId(),
            " orderSide: ", order.getSide().name(), " openSide: ", openSide.name(), " order: ", order.getClOrdId());
        order.setToClose(true);
      }
    }

    if (!order.isToClose()) {//open positions
      int count = 0;
      final Collection<ExchangeSubscription> influencerSubscriptions =
          InfluencerSubscriptionCache.getSubscriptions(order.getPlatform(), order.getAccountId(), order.getSymbol());
      LOGGER.info(Constants.LOG_FMT_4, "Number of subscriptions. clOrdId: ", order.getClOrdId(), " subscriptions: ",
          influencerSubscriptions.size());
      final List<ExchangeSubscription> subscriptionList = new ArrayList<>(influencerSubscriptions);
      Collections.shuffle(subscriptionList);
      String baseSymbol = order.getSymbol();

      for (ExchangeSubscription subscription : subscriptionList) {
        if (!userPartitionMap.contains(subscription.getUserId() % Context.getNoOfTotalCopyTradeUserPartitions())) {
          LOGGER.info(Constants.LOG_FMT_2, "User does not belong to this partition. userId: ", subscription.getUserId());
          continue;
        }
        //quotedSymbol = subscription.getPreferredQuoteCurrency();
        if (subscription.getPreferredCurrencies() != null && subscription.getPreferredCurrencies().length() > 2) {
          if (!subscription.getPreferredCurrencies().contains(baseSymbol)) {
            LOGGER.info(Constants.LOG_FMT_8, "Symbol is not in the preferred list. symbol: ", baseSymbol, " exchange: ",
                subscription.getExchange(), " subscription: ", subscription.getId(), " order: ", order.getClOrdId());
            continue;
          }
        }
/*        if (subscription.isHasPendingClose()) {
          LOGGER.info(Constants.LOG_FMT_8, "Subscription has pending close orders. symbol: ", baseSymbol, " exchange: ",
              subscription.getExchange(), " subscription: ", subscription.getId(), " order: ", order.getClOrdId());
          continue;
        }*/
        final String clOrdId = order.getClOrdId() + subscription.getId();
        final CopyTradeOrder openCopyTradeOrder =
            new CopyTradeOrder(clOrdId, baseSymbol, null, pair, order, subscription, order.getAccountId());

        openCopyTradeOrder.setKafkaRecordOffset(order.getKafkaRecordOffset());

        COPY_TRADE_QUEUE.addGuaranteed(openCopyTradeOrder);
        count++;
      }
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(order, pair);
      MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(executionReportMessage);

      LOGGER.info(Constants.LOG_FMT_3, "Open copy trade request completed. clOrdId: ", order.getClOrdId(), " count: " + count);
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
        final HashMap<Long, HashMap<String, CopyTradeOrder>> subscriptionWiseCopyTrades = new HashMap<Long, HashMap<String, CopyTradeOrder>>();

        for (CopyTradeCache.CopyTradeData data : copyTradeData) {
          for (CopyTradeOrder openCopyTradeOrder : data.getCopyTrades()) {
            boolean validSymbolToClose = false;
            if ("GLOBAL".equalsIgnoreCase(closeMode)) {
              validSymbolToClose = true;//all symbols in all accounts
            } else if (order.getSymbol() == null) {
              validSymbolToClose = true;//all symbols in given account
            } else {//exact symbol
              validSymbolToClose = order.getSymbol() != null && !order.getSymbol().isEmpty() && order.getSymbol()
                  .equalsIgnoreCase(openCopyTradeOrder.getBaseSymbol());
            }

//            if (!userPartitionMap.contains(openCopyTrade.getUserId() % Context.getNoOfTotalCopyTradeUserPartitions())) {
//              LOGGER.info(Constants.LOG_FMT_2, "User does not belong to this partition. userId: ", openCopyTrade.getUserId());
//              continue;
//            }

            if (openCopyTradeOrder.isSuccessful() && validSymbolToClose && !openCopyTradeOrder.isClosed() && !openCopyTradeOrder.isToClose()) {
              ExchangeSubscription sub = openCopyTradeOrder.getSubscription();
              if (sub == null) {
                sub = InfluencerSubscriptionCache.get(openCopyTradeOrder.getSubscriptionId());
                if (sub != null) {
                  openCopyTradeOrder.setSubscription(sub);
                }
              }
              if (!copyTradeSubscriptionActiveForClose(sub)) {
                LOGGER.info(Constants.LOG_FMT_6, "Skip MP copy-trade close (subscription not active). subscriptionId: ",
                    openCopyTradeOrder.getSubscriptionId(), " openClOrdId: ", openCopyTradeOrder.getClOrdId(), " signalClOrdId: ",
                    order.getClOrdId());
                continue;
              }
              final HashMap<String, CopyTradeOrder> pairWiseCopyTrades =
                  subscriptionWiseCopyTrades.computeIfAbsent(openCopyTradeOrder.getSubscriptionId(), v -> new HashMap<String, CopyTradeOrder>());

              CopyTradeOrder closeCopyTradeForSymbol = pairWiseCopyTrades.get(openCopyTradeOrder.getBaseSymbol());

              LOGGER.info(Constants.LOG_FMT_17,
                  "MP copy-trade to close - ",
                  " ordId: ", openCopyTradeOrder.getClOrdId(),
                  " side: ", openCopyTradeOrder.getSide().name(),
                  " quantity: ", openCopyTradeOrder.getxQuantity(),
                  " cumulativeAmount: ", openCopyTradeOrder.getCumulativeAmount(),
                  " status: ", openCopyTradeOrder.getStatus(),
                  " price: ", openCopyTradeOrder.getxPrice(),
                  " result: ", openCopyTradeOrder.getResult(),
                  " symbol: ", openCopyTradeOrder.getBaseSymbol());
              if (closeCopyTradeForSymbol == null) {
                count++;
                final String clOrdId = order.getClOrdId() + openCopyTradeOrder.getSubscriptionId() + count;
                closeCopyTradeForSymbol = new CopyTradeOrder(openCopyTradeOrder);
                closeCopyTradeForSymbol.setClOrdId(clOrdId);
                closeCopyTradeForSymbol.setOrigClOrdId(openCopyTradeOrder.getClOrdId());
                closeCopyTradeForSymbol.setSide(openCopyTradeOrder.getSide() == Side.BUY ? Side.SELL : Side.BUY);
                closeCopyTradeForSymbol.setOrdType(OrdType.LIMIT);
                closeCopyTradeForSymbol.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
                closeCopyTradeForSymbol.setToClose(true);
                closeCopyTradeForSymbol.setCreated(System.currentTimeMillis());
                closeCopyTradeForSymbol.setResult(null);
                closeCopyTradeForSymbol.setExternalId(null);
                closeCopyTradeForSymbol.setStatus(null);
                closeCopyTradeForSymbol.setToClose(order.isToClose());
                //clct.setOpenOrder(openCopyTradeOrder);
                closeCopyTradeForSymbol.setKafkaRecordOffset(order.getKafkaRecordOffset());
                //clct.setSourceSendTime(order.getSourceSendTime());
                closeCopyTradeForSymbol.setSourceSendTime(System.currentTimeMillis());

                closeCopyTradeForSymbol.getOpenOrders().add(openCopyTradeOrder);
                pairWiseCopyTrades.put(openCopyTradeOrder.getBaseSymbol(), closeCopyTradeForSymbol);
              } else {

                closeCopyTradeForSymbol.getOpenOrders().add(openCopyTradeOrder);
              }
            }
          }
        }

        for (HashMap<String, CopyTradeOrder> pairWiseCopyTrades : subscriptionWiseCopyTrades.values()) {
          for (CopyTradeOrder closeCopyTradeOrder : pairWiseCopyTrades.values()) {
            LOGGER.info(Constants.LOG_FMT_20, "CopyTrade Close order ", closeCopyTradeOrder.getPlatform(),
                " clOrdId: ", closeCopyTradeOrder.getClOrdId(),
                " subscriptionId: ", closeCopyTradeOrder.getSubscriptionId(),
                " accountId: ", closeCopyTradeOrder.getAccountId(),
                " Symbol: ", closeCopyTradeOrder.getBaseSymbol(),
                " Price: ", closeCopyTradeOrder.getPrice(),
                " xQuantity: ", closeCopyTradeOrder.getxQuantity(),
                " OrderQty: ", closeCopyTradeOrder.getOrderQty(),
                " OrderQtyScale: ", closeCopyTradeOrder.getOrderQtyScale(),
                " Open orders: ", closeCopyTradeOrder.getOpenOrders().size()
            );
            COPY_TRADE_QUEUE.addGuaranteed(closeCopyTradeOrder);
          }
        }

        subscriptionWiseCopyTrades.clear();
      } else {
        LOGGER.info(Constants.LOG_FMT_4, "No copy trades to close for platform: ", order.getPlatform(), " accountId: ",
            order.getAccountId());
        final Collection<CopyTradeOrder> openCopyTradeOrders = CopyTradeCache.getAllOpenCopyTrades();
        //check order status from exchange
        for (CopyTradeOrder copyTradeOrder : openCopyTradeOrders) {
          if (!ORDER_STATUS_FILLED.equalsIgnoreCase(copyTradeOrder.getStatus())) {
            COPY_TRADE_QUEUE.addGuaranteed(copyTradeOrder);
          }
        }
      }

      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(order, pair);
      MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(executionReportMessage);

      LOGGER.info(Constants.LOG_FMT_6, "Close copy trade request completed. clOrdId: ", order.getClOrdId(), " count: ", count, " mode: ",
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

  private static org.knowm.xchange.instrument.Instrument getInstrument(final String exchange, final XExchange xExchange,
      final CurrencyPair currencyPair, final boolean isFuture) {
    String key = (exchange + "_" + currencyPair.toString() + "_" + (isFuture ? "1" :"0")).toLowerCase();
    LOGGER.info("CurrencyPair key: " + key);
    org.knowm.xchange.instrument.Instrument instrument = ExternalInstrumentCache.getInstrument(key);
    if (instrument != null) {
      return instrument;
    } else {
      instrument = xExchange.getInstrument(currencyPair, isFuture);
      ExternalInstrumentCache.addInstrument(key, instrument);
      return instrument;
    }
  }

  private static ExternalSymbol getOrLoadSymbol(final String exchange, final int instrumentType,
      final String base, final String quote, final XExchange xExchange) {
    final String key = ExternalInstrumentCache.getKey(exchange, instrumentType, base, quote);
    ExternalSymbol externalSymbol = ExternalInstrumentCache.getSymbol(key);
    if (externalSymbol != null) {
      return externalSymbol;
    } else {
      LOGGER.info(LOG_FMT_2, "External symbol not found. key: ", key);
      //todo load from exchange using xExchange
      return null;
    }
  }

  /**
   * {@code subscription_state.status == 1} (Marketprophit copy-trade only; liquidity uses
   * {@link com.solfini.matchengine.liquidity.LiquiditySubscriptionCache} and does not use this order book).
   */
  private static boolean copyTradeSubscriptionActiveForClose(final ExchangeSubscription subscription) {
    return subscription != null && subscription.getStatus() == 1;
  }

  public static class Router implements Runnable {
    private final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
    private final IdleStrategy idleStrategy;

    public Router(IdleStrategy idleStrategy) {
      this.idleStrategy = idleStrategy;
    }

    @Override
    public void run() {
      while (true) {
        AtomicBoolean userLock = null;
        int userId = 0;
        String clOrdId = null;
        try {
          final Message message = COPY_TRADE_QUEUE.poll();

          if (message == null)
            continue;

          if (message instanceof CopyTradeOrder copyTradeOrder) {
            LOGGER.info(Constants.LOG_FMT_4, "Processing copy trade: ", copyTradeOrder.getClOrdId(), " isToClose: ", copyTradeOrder.isToClose());
            userId = copyTradeOrder.getUserId();
            clOrdId = copyTradeOrder.getClOrdId();
            //serialises all copy trades per user
            userLock = lock(copyTradeOrder);
            if (userLock == null) {
              LOGGER.info(Constants.LOG_FMT_8, "Order rejected. Failed to lock user. clOrdId: ", copyTradeOrder.getClOrdId(), " timeout. sent: ",
                  copyTradeOrder.getSourceSendTime(), " processed: ", System.currentTimeMillis(), " userId: ", userId);

              copyTradeOrder.setResult("REJECTED: Lock Timeout.");
              matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

              continue;
            } else {
              LOGGER.info(LOG_FMT_4, "User locked. id: ", userId, " orderId: ", clOrdId);
            }
            if (copyTradeOrder.isToClose()) {
              //processCloseOrder(copyTrade, copyTrade.getOpenOrder());
              processCloseOrder(copyTradeOrder, copyTradeOrder.getOpenOrders());
            } else {
              processOpenOrder(copyTradeOrder);
            }
          } else {
            LOGGER.warn("Invalid message: " + message.toJSON());
          }

        } catch (Exception e) {
          LOGGER.error(Constants.ERROR_LOG, e);
        }
        if (userLock != null) {
          release(userLock);
          LOGGER.info(LOG_FMT_4, "User lock released. id: ", userId, " orderId: ", clOrdId);
        }
        idleStrategy.idle();
      }
    }

    private AtomicBoolean lock(final CopyTradeOrder copyTradeOrder) {
      final long start = System.currentTimeMillis();
      final long lockWaitTime = copyTradeOrder.isToClose() ? TEN_MINUTES : ONE_MINUTE;
      final AtomicBoolean userLock = UserCache.get(copyTradeOrder.getUserId()).getCopyTradeLock();
      while (!userLock.compareAndSet(false, true)) {
        LockSupport.parkNanos(50_000_000);// 50 ms
        if (System.currentTimeMillis() - start > lockWaitTime) {
          LOGGER.info(LOG_FMT_6, "Waiting more than lockWaitTime mins to acquire a lock for order :", copyTradeOrder.getClOrdId(),
              " userId: ", copyTradeOrder.getUserId(), " lockWaitTime: ", lockWaitTime);
          return null;
        }
      }

      return userLock;
    }

    private void release(final AtomicBoolean userLock) {
      userLock.set(false);
    }

    private void processOpenOrder(final CopyTradeOrder copyTradeOrder) {
      final String clOrdId = copyTradeOrder.getClOrdId();
      final long ordQtyPercentage = copyTradeOrder.getSignalPercentage();
      final short ordQtyPercentageScale = copyTradeOrder.getSignalPercentageScale();
      //final long ordPrice = copyTrade.getSignalPrice();
      //final short ordPriceScale = 2;
      final ExchangeSubscription subscription = copyTradeOrder.getSubscription();
      final String exchange = subscription.getExchange();
      final int instrumentType = subscription.isFuturesEnabled() ? TARDIS_PERPS : TARDIS_SPOT;
      String baseSymbol = null, quotedSymbol = null;
      final Side side = subscription.getInverseTrade() == 0 ? copyTradeOrder.getSide() : copyTradeOrder.getInverseSide();
      final long now = System.currentTimeMillis();

      copyTradeOrder.setSide(side);

      if ((copyTradeOrder.getSourceSendTime() + Context.getMaxDelayToOpenOrderInMs()) < now) {
        LOGGER.info(Constants.LOG_FMT_6, "Order rejected. clOrdId: ", clOrdId, " timeout. sent: ", copyTradeOrder.getSourceSendTime(),
            " processed: ", now);

        copyTradeOrder.setResult("REJECTED: Timeout.");
        matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

        return;
      }

      baseSymbol = copyTradeOrder.getBaseSymbol();
      //todo check if this is required
/*      if (subscription.isFuturesEnabled() && (BYBIT.equalsIgnoreCase(subscription.getExchange()) || BINANCE.equalsIgnoreCase(subscription.getExchange()))) {
        final String key = (subscription.getExchange() + "_" + baseSymbol).toUpperCase();
        if (EXCHANGE_SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.containsKey(key)) {
          baseSymbol = EXCHANGE_SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.get(key);
        }
      }*/
      quotedSymbol = DefaultExchangeQuoteCache.get(subscription.getExchange(), baseSymbol);
      if (quotedSymbol == null) {
        quotedSymbol = MarketDepthCache.getBestQuoteCurrency(subscription.getExchange(), baseSymbol, copyTradeOrder.getSide(),
            subscription.isFuturesEnabled());
      }
      copyTradeOrder.setQuotedSymbol(quotedSymbol);
      if (quotedSymbol == null) {
        LOGGER.info(Constants.LOG_FMT_10, "Quote symbol is empty: exchange: ", subscription.getExchange(), " baseSymbol ", baseSymbol, "/",
            quotedSymbol, " subscription: ", subscription.getId(), " order: ", clOrdId);
        copyTradeOrder.setResult("REJECTED: Failed to calculate best quote symbol.");
        matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

        return;
      }
      final ExternalSymbol externalSymbol = ExternalInstrumentCache.getSymbol(exchange, instrumentType, baseSymbol, quotedSymbol);
      if (externalSymbol == null || !externalSymbol.isTradable()) {
        LOGGER.info(Constants.LOG_FMT_10, "Symbol is not tradable on the exchange. symbol: ", baseSymbol, "/", quotedSymbol,
            " exchange: ", subscription.getExchange(), " subscription: ", subscription.getId(), " order: ", copyTradeOrder.getClOrdId());

        copyTradeOrder.setResult("REJECTED: Symbol not tradable. " + baseSymbol + "/" + quotedSymbol);
        matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

        return;
      }

      double maxTradeValue = MbxMath.scaleDown(subscription.getAmountWithLeverage(), 2);
      double availableMaxAmount = MbxMath.scaleDown(subscription.getAvailableMaxAmount(), 2);

      LOGGER.info(Constants.LOG_FMT_4, "Order processing. clOrdId: ", clOrdId, " maxTradeValue: ", maxTradeValue);

      final double MIN_COPY_TRADE_OPEN_AMOUNT_IN_USD = 10.0d;
      // use direct API first if configured
      if (subscription.getConnectionType() == ExchangeSubscription.CONNECTION_VIA_DIRECT) {
        final ExternalExchangeClient fastClient = FastClientFactory.createRestOnlyClient(subscription);
        final Ticker ticker = ExternalTickerCache.getTicker(externalSymbol, fastClient);
        double price = ticker.getPrice(side);
        if (price == 0) {
          LOGGER.info(Constants.LOG_FMT_4, "Order rejected. Failed to get price. clOrdId: ", clOrdId, " price: ", price);

          copyTradeOrder.setResult("REJECTED: Failed to fetch price.");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return;
        }
        LOGGER.info(LOG_FMT_4, "Ticker key: ", externalSymbol.getKey(), " ticker: ", ticker.toString());
        //openPricePercentage is scaled by 4
        long openPricePercentage = externalSymbol.getOpenPricePercentage();
        if (Side.SELL == copyTradeOrder.getSide()) {
          price = price - (price * openPricePercentage) / PRICE_PERCENTAGE_SCALE;
          price = MbxMath.roundDown(price, externalSymbol.getPriceScale());
        } else {
          price = price + (price * openPricePercentage) / PRICE_PERCENTAGE_SCALE;
          price = MbxMath.roundUp(price, externalSymbol.getPriceScale());
        }

        if (side == Side.BUY) {
          double totalStableCoinBalance = subscription.getTotalStableCoinBalance();
          if (subscription.getPercentage() <= 10_000 /* no margin */) {
            if (totalStableCoinBalance <= 0) {
              LOGGER.info(Constants.LOG_FMT_4, "Order rejected. clOrdId: ", clOrdId, " exchangeBalance: ", totalStableCoinBalance);

              copyTradeOrder.setResult("REJECTED: Insufficient balance. balance: " + totalStableCoinBalance + ".");
              copyTradeOrder.setxExchange(null);
              matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

              return;
            }
            maxTradeValue = Math.min(availableMaxAmount, totalStableCoinBalance);
            LOGGER.info(Constants.LOG_FMT_6, "Max trade value adjusted based on the exchange balance. clOrdId: ", clOrdId,
                " new maxTradeValue: ", maxTradeValue, " exchange balance: ", totalStableCoinBalance);
          }
        } else {
          double tokenBalance = subscription.getBalance(baseSymbol);
          if (subscription.getPercentage() <= 10_000 /* no margin */) {
            if (subscription.isFuturesEnabled()) {
              if (tokenBalance > 0) {
                double maxExchangeValue = tokenBalance * price;
                maxTradeValue = Math.min(availableMaxAmount, maxExchangeValue);
                LOGGER.info(Constants.LOG_FMT_6, "Max trade value adjusted based on the exchange balance. clOrdId: ", clOrdId,
                    " new maxTradeValue: ", maxTradeValue, " exchange balance: ", maxExchangeValue);
              } else {
                // shorting
                double stableCoinBalance = subscription.getBalance(quotedSymbol);

                maxTradeValue = Math.min(availableMaxAmount, stableCoinBalance);
                LOGGER.info(Constants.LOG_FMT_6, "Max trade value adjusted based on the exchange balance. clOrdId: ", clOrdId,
                    " new maxTradeValue: ", maxTradeValue, " exchange stable balance: ", stableCoinBalance);
                if (maxTradeValue <= 0) {
                  LOGGER.info(Constants.LOG_FMT_6, "Order rejected. clOrdId: ", clOrdId, " futures baseBalance: ", tokenBalance,
                      " quoteBalance: ", stableCoinBalance);

                  copyTradeOrder.setResult("REJECTED: Insufficient balance. balance: " + tokenBalance + " " + baseSymbol
                      + " stable coin balance: " + stableCoinBalance + " " + quotedSymbol );
                  copyTradeOrder.setxExchange(null);
                  matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

                  return;
                }
              }
            } else {
              if (tokenBalance <= 0) {
                LOGGER.info(Constants.LOG_FMT_4, "Order rejected. clOrdId: ", clOrdId, " exchangeBalance: ", tokenBalance);

                copyTradeOrder.setResult("REJECTED: Insufficient balance. balance: " + tokenBalance + " " + baseSymbol + ".");
                copyTradeOrder.setxExchange(null);
                matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

                return;
              }
              double maxExchangeValue = tokenBalance * price;
              maxTradeValue = Math.min(availableMaxAmount, maxExchangeValue);
              LOGGER.info(Constants.LOG_FMT_6, "Max trade value adjusted based on the exchange balance. clOrdId: ", clOrdId,
                  " new maxTradeValue: ", maxTradeValue, " exchange balance: ", maxExchangeValue);
            }
          }
        }

        double maxRemainingAmount = calcMaxTradeValueByMarketCap(subscription, copyTradeOrder, maxTradeValue);
        if (maxRemainingAmount < 0) {
          return; //reject message already sent
        }
        LOGGER.info(Constants.LOG_FMT_4, "Max trade value for order: ", copyTradeOrder.getClOrdId(), " is ", maxRemainingAmount);
        /*if (maxRemainingAmount < Context.getMinCopyTradeAmountInUsd()) {
          LOGGER.info(Constants.LOG_FMT_6, "Override maxRemainingAmount to minCopyTradeAmount. clOrdId: ", clOrdId, " maxRemainingAmount: ",
              maxRemainingAmount, " minCopyTradeAmount: ", Context.getMinCopyTradeAmountInUsd());
          maxRemainingAmount = Context.getMinCopyTradeAmountInUsd();

        //LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " insufficient maxTradeValue: ", maxRemainingAmount);

        //copyTrade.setResult("REJECTED: Trade value is too low.");
        //copyTrade.setxExchange(null);
        //matcherToPublisherQueue.addGuaranteed(copyTrade);

        //return;
        }*/
        if (maxRemainingAmount < MIN_COPY_TRADE_OPEN_AMOUNT_IN_USD) {
          LOGGER.info(Constants.LOG_FMT_6, "Override maxRemainingAmount to minCopyTradeAmount. clOrdId: ", clOrdId, " maxRemainingAmount: ",
              maxRemainingAmount, " minCopyTradeAmount: ", MIN_COPY_TRADE_OPEN_AMOUNT_IN_USD);
          maxRemainingAmount = MIN_COPY_TRADE_OPEN_AMOUNT_IN_USD;

/*        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " insufficient maxTradeValue: ", maxRemainingAmount);

        copyTrade.setResult("REJECTED: Trade value is too low.");
        copyTrade.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(copyTrade);

        return;*/
        }

        //percentage set by the influencer
        double signalTradePercentage = MbxMath.scaleDown(ordQtyPercentage, ordQtyPercentageScale) / 100D;
        //percentage of subscription amount assigned by the user
        double userDefinedPercentage = MbxMath.scaleDown(Math.min(subscription.getPercentage(), 10000), 2) / 100D;
        final double signalTradeValue = Math.min(maxRemainingAmount, maxTradeValue * signalTradePercentage * userDefinedPercentage);//0.01
        /*if (signalTradeValue < Context.getMinCopyTradeAmountInUsd()) {
          LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " insufficient maxTradeValue: ", signalTradeValue);

          copyTradeOrder.setResult("REJECTED: Trade value is too small. (" + signalTradeValue + ")");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return;
        }*/
        if (signalTradeValue < MIN_COPY_TRADE_OPEN_AMOUNT_IN_USD) {
          LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " insufficient maxTradeValue: ", signalTradeValue);

          copyTradeOrder.setResult("REJECTED: Trade value is too small. (" + signalTradeValue + ")");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return;
        }
        double balanceRequired = signalTradeValue * Context.getCopyTradeStableCoinConversionSafeFactor();

        LOGGER.info(Constants.LOG_FMT_8, "Trade value for order after sentiment: ", copyTradeOrder.getClOrdId(), " is ", signalTradeValue,
            " balanceRequired: ", balanceRequired, " availableBalance: ",
            side == Side.BUY ? subscription.getBalance(quotedSymbol) : subscription.getBalance(baseSymbol));
        /* signalTradeValue = Math.min(availableBalance, signalTradeValue);*/

        //convert stable coins if balance is insufficient
        //double availableBalance = autoConvertStableCoins(subscription, copyTradeOrder, signalTradeValue, balanceRequired);

        // decide qty
        double quantity = MbxMath.roundToBestPrecision(MbxMath.roundDown(signalTradeValue/price, externalSymbol.getQtyScale()));

        copyTradeOrder.setOrderQty(MbxMath.changeScale(quantity, externalSymbol.getQtyScale()));
        copyTradeOrder.setOrderQtyScale((short) externalSymbol.getQtyScale());

        // check min qty and reject
        LOGGER.info(Constants.LOG_FMT_6, "ClOrdId: ", clOrdId, " qty: ", quantity, " min allowed: ",
            externalSymbol.getMinimumAmount(), " exchange: ", externalSymbol.getExchange());
        if (externalSymbol.getMinimumAmount() > 0 && quantity < externalSymbol.getMinimumAmount()) {
          LOGGER.info(Constants.LOG_FMT_6, "Order rejected. Insufficient quantity. clOrdId: ", clOrdId, " qty: ", quantity,
              " min allowed: ", externalSymbol.getMinimumAmount());

          copyTradeOrder.setResult("REJECTED: Insufficient qty.");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return;
        }
        if (externalSymbol.getMaximumAmount() > 0 && quantity > externalSymbol.getMaximumAmount() ) {
          LOGGER.info(Constants.LOG_FMT_6, "Max quantity reached clOrdId: ", clOrdId, " qty: ", quantity,
              " max allowed: ", externalSymbol.getMaximumAmount());
          quantity = externalSymbol.getMaximumAmount();
        }

        // set final values for price and qty
        copyTradeOrder.setxQuantity(BigDecimal.valueOf(quantity).setScale(externalSymbol.getQtyScale(), RoundingMode.HALF_UP));
        copyTradeOrder.setxPrice(BigDecimal.valueOf(price).setScale(externalSymbol.getPriceScale(), RoundingMode.HALF_UP));
        copyTradeOrder.setTradeValue(signalTradeValue);

        try {
          final User user = UserCache.get(copyTradeOrder.getUserId());
          final Order externalOrder = OrderObjectPool.get();
          externalOrder.setUser(user);
          externalOrder.setAccount(user.getId());
          externalOrder.setClOrdId(copyTradeOrder.getClOrdId());
          externalOrder.setSide(copyTradeOrder.getSide());
          externalOrder.setSymbol(externalSymbol.getSymbol());
          externalOrder.setOrdType(copyTradeOrder.getOrdType());
          externalOrder.setTimeInForce(copyTradeOrder.getTimeInForce());
          externalOrder.setPrice(MbxMath.changeScaleWithRounding(price, externalSymbol.getPriceScale()), (short) externalSymbol.getPriceScale());
          externalOrder.setQty(MbxMath.changeScaleWithRounding(quantity, externalSymbol.getQtyScale()), (short) externalSymbol.getQtyScale());
          externalOrder.setToClose(copyTradeOrder.isToClose());

          ExecutionReportMessage executionReport = subscription.getClient().sendOrder(externalOrder,
              subscription.isFuturesEnabled(), quotedSymbol, baseSymbol, externalSymbol.getPriceScale(),
              externalSymbol.getQtyScale(), 1); //todo set FX rate currently not being used
          if (executionReport != null) {
            if (executionReport.getOrdStatus() == OrdStatus.FILLED) {
              double orderNotional = executionReport.getNotional(); //todo check if notional value is calculated
              //todo fill the below fields from response
              //copyTradeOrder.setExternalId();
              copyTradeOrder.setPriceScale(executionReport.getPriceScale());
              copyTradeOrder.setPrice(executionReport.getAvgPx());
              copyTradeOrder.setAveragePrice(MbxMath.scaleDown(executionReport.getAvgPx(), executionReport.getPriceScale()));
              copyTradeOrder.setOriginalAmount(quantity);
              copyTradeOrder.setCumulativeAmount(MbxMath.scaleDown(executionReport.getCumQty(),
                  executionReport.getOrderQtyScale()));
              copyTradeOrder.setStatus(ORDER_STATUS_FILLED);
              // todo handle fee position
              //copyTradeOrder.setFee(MbxMath.scaleDown(executionReport.getFeeAccumulatedQuantity()));
              copyTradeOrder.setTradeValue(copyTradeOrder.getCumulativeAmount() * copyTradeOrder.getAveragePrice());
              //copyTradeOrder.setTradeValue(orderNotional);
              copyTradeOrder.setResult(SUCCESS);

              copyTradeOrder.setClosed(false);
            } else if (executionReport.getOrdStatus() == OrdStatus.REJECTED || executionReport.getOrdStatus() == OrdStatus.CANCELED
                || executionReport.getExecType() == ExecType.REJECTED) {
              copyTradeOrder.setStatus(ORDER_STATUS_REJECTED);
              copyTradeOrder.setResult(executionReport.getError());
            }
          }

          CopyTradeCache.add(copyTradeOrder);

          //if (copyTrade.getStatus().equalsIgnoreCase("FILLED")) {
          subscription.setAvailableMaxAmount(subscription.getAvailableMaxAmount() - MbxMath.changeScale(signalTradeValue, 2));
          matcherToPublisherQueue.addGuaranteed(subscription);
          //}

          LOGGER.info(Constants.LOG_FMT_16, "Copy trade (open) successful. clOrdId: ", clOrdId, " symbol: ", baseSymbol.toUpperCase(), "/",
              quotedSymbol.toUpperCase(), " side: ", copyTradeOrder.getSide().name(), " quantity: ", quantity, " price: ", copyTradeOrder.getPrice(),
              " orderId:", copyTradeOrder.getExternalId(), " result: ", copyTradeOrder.getResult());
        } catch (Exception e) {
          copyTradeOrder.setResult("FAILED: " + e.getMessage());
          LOGGER.error("Error, Copy trade failed. clOrdId: " + clOrdId, e);
        }

        copyTradeOrder.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

        return; //direct implementation processing done
      } else {

        final XExchange xExchange = ExternalExchangeUtil.createXExchange(subscription);
        if (xExchange == null) {
          LOGGER.info(Constants.LOG_FMT_4, "Order rejected. clOrdId: ", clOrdId,
              " invalid exchange: ", subscription.getExchange());

          copyTradeOrder.setResult("REJECTED: Unable to connect to exchange.");
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return;
        }
        final CurrencyPair currencyPair = ExternalCurrencyPairCache.get(baseSymbol, quotedSymbol);

        org.knowm.xchange.instrument.Instrument instrument =
            getInstrument(subscription.getExchange(), xExchange, currencyPair, subscription.isFuturesEnabled());
        if (instrument == null) {
          LOGGER.info(Constants.LOG_FMT_6, "Order rejected. clOrdId: ", clOrdId, " invalid instrument: ", baseSymbol, " ", quotedSymbol);

          copyTradeOrder.setResult("REJECTED: Invalid instrument. " + baseSymbol + " " + quotedSymbol);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return;
        }

        copyTradeOrder.setxExchange(xExchange);
        copyTradeOrder.setCurrencyPair(currencyPair);
        copyTradeOrder.setInstrument(instrument);

        //get price
        double price = ExternalExchangeHandler.getPrice(subscription, currencyPair, instrument,
            side, xExchange);
        if (price == 0) {
          LOGGER.info(Constants.LOG_FMT_4, "Order rejected. Failed to get price. clOrdId: ",
              clOrdId, " price: ", price);

          copyTradeOrder.setResult("REJECTED: Failed to fetch price.");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return;
        }
        //openPricePercentage is scaled by 4
        long openPricePercentage = externalSymbol.getOpenPricePercentage();
        if (Side.SELL == copyTradeOrder.getSide()) {
          price = price - (price * openPricePercentage) / PRICE_PERCENTAGE_SCALE;
        } else {
          price = price + (price * openPricePercentage) / PRICE_PERCENTAGE_SCALE;
        }

        //check exchange balance
        XExchange.Balance balance = null;
        //LOGGER.info(Constants.LOG_FMT_2, "is futures enabled: ", subscription.isFuturesEnabled());
        if (side == Side.BUY) {
          balance = ExternalExchangeHandler.getStableCoinBalance(subscription, xExchange);
          double totalStableCoinBalance = balance.getTotalStableCoinBalance();
          if (subscription.getPercentage() <= 10_000 /* no margin */) {
            if (totalStableCoinBalance <= 0) {
              LOGGER.info(Constants.LOG_FMT_4, "Order rejected. clOrdId: ", clOrdId,
                  " exchangeBalance: ", totalStableCoinBalance);

              copyTradeOrder.setResult(
                  "REJECTED: Insufficient balance. balance: " + totalStableCoinBalance + ".");
              copyTradeOrder.setxExchange(null);
              matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

              return;
            }
            maxTradeValue = Math.min(availableMaxAmount, totalStableCoinBalance);
            LOGGER.info(Constants.LOG_FMT_6,
                "Max trade value adjusted based on the exchange balance. clOrdId: ", clOrdId,
                " new maxTradeValue: ", maxTradeValue, " exchange balance: ",
                totalStableCoinBalance);
          }
        } else {
          balance = ExternalExchangeHandler.getBalance(subscription, xExchange, baseSymbol);
          if (subscription.getPercentage() <= 10_000 /* no margin */) {
            if (subscription.isFuturesEnabled()) {
              if (balance.getCoinBalance() > 0) {
                double maxExchangeValue = balance.getCoinBalance() * price;
                maxTradeValue = Math.min(availableMaxAmount, maxExchangeValue);
                LOGGER.info(Constants.LOG_FMT_6,
                    "Max trade value adjusted based on the exchange balance. clOrdId: ", clOrdId,
                    " new maxTradeValue: ", maxTradeValue, " exchange balance: ", maxExchangeValue);
              } else {
                balance = ExternalExchangeHandler.getStableCoinBalance(subscription, xExchange);
                double stableCoinBalance = balance.getBalance(quotedSymbol);

                maxTradeValue = Math.min(availableMaxAmount, stableCoinBalance);
                LOGGER.info(Constants.LOG_FMT_6,
                    "Max trade value adjusted based on the exchange balance. clOrdId: ", clOrdId,
                    " new maxTradeValue: ", maxTradeValue, " exchange stable balance: ",
                    stableCoinBalance);
                if (maxTradeValue <= 0) {
                  LOGGER.info(Constants.LOG_FMT_6, "Order rejected. clOrdId: ", clOrdId,
                      "futures exchangeCoinBalance: ", balance.getCoinBalance(),
                      " exchangeStableCoinBalance: ", stableCoinBalance);

                  copyTradeOrder.setResult(
                      "REJECTED: Insufficient balance. coin balance: " + balance.getCoinBalance()
                          + " " + baseSymbol
                          + " stable coin balance: " + stableCoinBalance + " " + quotedSymbol);
                  copyTradeOrder.setxExchange(null);
                  matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

                  return;
                }
              }
            } else {
              if (balance.getCoinBalance() <= 0) {
                LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId,
                    " exchangeBalance: ", balance.getCoinBalance());

                copyTradeOrder.setResult(
                    "REJECTED: Insufficient balance. balance: " + balance.getCoinBalance() + " "
                        + baseSymbol + ".");
                copyTradeOrder.setxExchange(null);
                matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

                return;
              }
              double maxExchangeValue = balance.getCoinBalance() * price;
              maxTradeValue = Math.min(availableMaxAmount, maxExchangeValue);
              LOGGER.info(Constants.LOG_FMT_6,
                  "Max trade value adjusted based on the exchange balance. clOrdId: ", clOrdId,
                  " new maxTradeValue: ", maxTradeValue, " exchange balance: ", maxExchangeValue);
            }
          }
        }

        double maxRemainingAmount = calcMaxTradeValueByMarketCap(subscription, copyTradeOrder,
            maxTradeValue);
        //check current open order value for the symbol
        //      double openOrderValue = CopyTradeCache.getOpenOrderValue(copyTrade.getSubscriptionId(), quotedSymbol, baseSymbol);
        //      if (openOrderValue > 0) {
        //        maxRemainingAmount = maxTradeValue - openOrderValue;
        //        LOGGER.info(Constants.LOG_FMT_6, "Max trade value adjusted based on current open orders. clOrdId: ", clOrdId, " new maxTradeValue: ",
        //            maxRemainingAmount, " openOrderValue: ", openOrderValue);
        //      }

        LOGGER.info(Constants.LOG_FMT_2, "Max trade value for order: ", copyTradeOrder.getClOrdId(),
            " is ", maxRemainingAmount);
        /*if (maxRemainingAmount < Context.getMinCopyTradeAmountInUsd()) {
          LOGGER.info(Constants.LOG_FMT_2,
              "Override maxRemainingAmount to minCopyTradeAmount. clOrdId: ", clOrdId,
              " maxRemainingAmount: ",
              maxRemainingAmount, " minCopyTradeAmount: ", Context.getMinCopyTradeAmountInUsd());
          maxRemainingAmount = Context.getMinCopyTradeAmountInUsd();

        //LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " insufficient maxTradeValue: ", maxRemainingAmount);

        //copyTrade.setResult("REJECTED: Trade value is too low.");
        //copyTrade.setxExchange(null);
        //matcherToPublisherQueue.addGuaranteed(copyTrade);

        //return;
        }*/
        if (maxRemainingAmount < MIN_COPY_TRADE_OPEN_AMOUNT_IN_USD) {
          LOGGER.info(Constants.LOG_FMT_2,
              "Override maxRemainingAmount to minCopyTradeAmount. clOrdId: ", clOrdId,
              " maxRemainingAmount: ",
              maxRemainingAmount, " minCopyTradeAmount: ", MIN_COPY_TRADE_OPEN_AMOUNT_IN_USD);
          maxRemainingAmount = MIN_COPY_TRADE_OPEN_AMOUNT_IN_USD;

/*        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " insufficient maxTradeValue: ", maxRemainingAmount);

        copyTrade.setResult("REJECTED: Trade value is too low.");
        copyTrade.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(copyTrade);

        return;*/
        }

        BigDecimal xPrice = new BigDecimal(price);
        xPrice = xPrice.setScale(externalSymbol.getPriceScale(),
            (side == Side.BUY) ? RoundingMode.HALF_UP : RoundingMode.HALF_DOWN);

        //percentage set by the influencer
        double signalTradePercentage =
            MbxMath.scaleDown(ordQtyPercentage, ordQtyPercentageScale) / 100D;
        //percentage of subscription amount assigned by the user
        double userDefinedPercentage =
            MbxMath.scaleDown(Math.min(subscription.getPercentage(), 10000), 2) / 100D;
        double signalTradeValue = Math.min(maxRemainingAmount,
            maxTradeValue * signalTradePercentage * userDefinedPercentage);//0.01
        /*if (signalTradeValue < Context.getMinCopyTradeAmountInUsd()) {
          LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " insufficient maxTradeValue: ", signalTradeValue);

          copyTradeOrder.setResult("REJECTED: Trade value is too small. (" + signalTradeValue + ")");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return;
        }*/
        if (signalTradeValue < MIN_COPY_TRADE_OPEN_AMOUNT_IN_USD) {
          LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " insufficient maxTradeValue: ", signalTradeValue);

          copyTradeOrder.setResult("REJECTED: Trade value is too small. (" + signalTradeValue + ")");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return;
        }
        double balanceRequired =
            signalTradeValue * Context.getCopyTradeStableCoinConversionSafeFactor();
        LOGGER.info(Constants.LOG_FMT_2, "Trade value for order after sentiment: ",
            copyTradeOrder.getClOrdId(), " is ", signalTradeValue,
            " balanceRequired: ", balanceRequired, " availableBalance: ",
            side == Side.BUY ? balance.getBalance(quotedSymbol) : balance.getCoinBalance());
        /* signalTradeValue = Math.min(availableBalance, signalTradeValue);*/
        //convert stable coins if balance is insufficient
        if (!subscription.isFuturesEnabled()
            && subscription.getPercentage() <= 10_000 /* no margin */ && balance.getBalance(
            quotedSymbol) < signalTradeValue && side == Side.BUY) {
          if (balanceRequired > balance.getTotalStableCoinBalance()) {
            LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId,
                " exchangeBalance: ", balance.getTotalStableCoinBalance());

            copyTradeOrder.setResult(
                "REJECTED: Insufficient balance. balance: " + balance.getTotalStableCoinBalance()
                    + ".");
            copyTradeOrder.setxExchange(null);
            matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

            return;
          }
          LOGGER.info(Constants.LOG_FMT_2, "Balance required for the order: ",
              copyTradeOrder.getClOrdId(), " is ", balanceRequired, " ",
              quotedSymbol);
          double usd = balance.getUsdBalance();
          double usdc = balance.getUsdcBalance();
          double usdt = balance.getUsdtBalance();
          double availableBalance = 0;
          try {
            if (USD.equalsIgnoreCase(
                quotedSymbol)) {// best quote is in USD. convert other stable coins to USD by selling
              availableBalance = usd;
              if (usdt > 0) {
                final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDT, USD);
                final org.knowm.xchange.instrument.Instrument conversionInstrument =
                    getInstrument(subscription.getExchange(), xExchange, conversionPair,
                        subscription.isFuturesEnabled());
                final ExternalSymbol metaData = ExternalInstrumentCache.getSymbolStatus(
                    copyTradeOrder.getExchange(), USDT, USD, false);
                final double priceValue =
                    ExternalExchangeHandler.getPrice(subscription, conversionPair,
                        conversionInstrument, Side.SELL, xExchange);
                final double usdtAmountToConvert = Math.min(usdt,
                    (balanceRequired - availableBalance));
                final double convertedAmount =
                    xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument,
                        usdtAmountToConvert, priceValue,
                        Side.SELL, clOrdId + "S1", metaData);
                availableBalance += convertedAmount;
              }
              if (usdc > 0 && availableBalance < balanceRequired) {
                final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDC, USD);
                final org.knowm.xchange.instrument.Instrument conversionInstrument =
                    getInstrument(subscription.getExchange(), xExchange, conversionPair,
                        subscription.isFuturesEnabled());
                final ExternalSymbol metaData = ExternalInstrumentCache.getSymbolStatus(
                    copyTradeOrder.getExchange(), USDC, USD, subscription.isFuturesEnabled());
                final double priceValue =
                    ExternalExchangeHandler.getPrice(subscription, conversionPair,
                        conversionInstrument, Side.SELL, xExchange);
                final double usdcAmountToConvert = Math.min(usdc,
                    (balanceRequired - availableBalance));

                final double convertedAmount =
                    xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument,
                        usdcAmountToConvert, priceValue,
                        Side.SELL, clOrdId + "S2", metaData);
                availableBalance += convertedAmount;
              }
            } else if (USDC.equalsIgnoreCase(
                quotedSymbol)) {// best quote is in USDC. convert other stable coins to USDC
              availableBalance = usdc;
              if (usdt > 0) {
                final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDC, USDT);
                final org.knowm.xchange.instrument.Instrument conversionInstrument =
                    getInstrument(subscription.getExchange(), xExchange, conversionPair,
                        subscription.isFuturesEnabled());
                final ExternalSymbol
                    metaData = ExternalInstrumentCache.getSymbolStatus(copyTradeOrder.getExchange(),
                    USDC, USDT, subscription.isFuturesEnabled());
                final double priceValue =
                    ExternalExchangeHandler.getPrice(subscription, conversionPair,
                        conversionInstrument, Side.BUY, xExchange);
                final double usdtAmountToConvert = Math.min(usdt,
                    (balanceRequired - availableBalance));
                final double convertedAmount =
                    xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument,
                        usdtAmountToConvert, priceValue,
                        Side.BUY, clOrdId + "B1", metaData);
                availableBalance += convertedAmount;
              }
              if (usd > 0 && availableBalance < balanceRequired) {
                final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDC, USD);
                final org.knowm.xchange.instrument.Instrument conversionInstrument =
                    getInstrument(subscription.getExchange(), xExchange, conversionPair,
                        subscription.isFuturesEnabled());
                final ExternalSymbol metaData = ExternalInstrumentCache.getSymbolStatus(
                    copyTradeOrder.getExchange(), USDC, USD, subscription.isFuturesEnabled());
                final double priceValue =
                    ExternalExchangeHandler.getPrice(subscription, conversionPair,
                        conversionInstrument, Side.BUY, xExchange);
                final double usdAmountToConvert = Math.min(usd,
                    (balanceRequired - availableBalance));
                final double convertedAmount =
                    xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument,
                        usdAmountToConvert, priceValue, Side.BUY,
                        clOrdId + "B2", metaData);
                availableBalance += convertedAmount;
              }
            } else if (USDT.equalsIgnoreCase(
                quotedSymbol)) {// best quote is in USDT. convert other stable coins to USDT
              availableBalance = usdt;
              if (usdc > 0) {
                final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDC, USDT);
                final org.knowm.xchange.instrument.Instrument conversionInstrument =
                    getInstrument(subscription.getExchange(), xExchange, conversionPair,
                        subscription.isFuturesEnabled());
                final ExternalSymbol
                    metaData = ExternalInstrumentCache.getSymbolStatus(copyTradeOrder.getExchange(),
                    USDC, USDT, subscription.isFuturesEnabled());
                final double priceValue =
                    ExternalExchangeHandler.getPrice(subscription, conversionPair,
                        conversionInstrument, Side.SELL, xExchange);
                final double usdcAmountToConvert = Math.min(usdc,
                    (balanceRequired - availableBalance));

                final double convertedAmount =
                    xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument,
                        usdcAmountToConvert, priceValue,
                        Side.SELL, clOrdId + "S1", metaData);
                availableBalance += convertedAmount;
              }
              if (availableBalance < balanceRequired && usd > 0) {
                final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDT, USD);
                final org.knowm.xchange.instrument.Instrument conversionInstrument =
                    getInstrument(subscription.getExchange(), xExchange, conversionPair,
                        subscription.isFuturesEnabled());
                final ExternalSymbol metaData = ExternalInstrumentCache.getSymbolStatus(
                    copyTradeOrder.getExchange(), USDT, USD, subscription.isFuturesEnabled());
                final double priceValue =
                    ExternalExchangeHandler.getPrice(subscription, conversionPair,
                        conversionInstrument, Side.BUY, xExchange);
                final double usdAmountToConvert = Math.min(usd,
                    (balanceRequired - availableBalance));
                final double convertedAmount =
                    xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument,
                        usdAmountToConvert, priceValue, Side.BUY,
                        clOrdId + "B2", metaData);
                availableBalance += convertedAmount;
              }
            }
            signalTradeValue = availableBalance;
          } catch (ExchangeException e) {
            LOGGER.error(ERROR_LOG, e);
            LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId,
                " failed to convert.");

            copyTradeOrder.setResult("REJECTED: " + e.getMessage());
            copyTradeOrder.setxExchange(null);
            matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

            return;
          } catch (Exception e) {
            LOGGER.error(ERROR_LOG, e);
            LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId,
                " failed to convert.");

            copyTradeOrder.setResult("REJECTED: failed to convert stable currencies.");
            copyTradeOrder.setxExchange(null);
            matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

            return;
          }
        } else if (subscription.isFuturesEnabled()
            && balance.getTotalStableCoinBalance() < signalTradeValue
            && side == Side.BUY) {//balance check for future orders
          LOGGER.info(Constants.LOG_FMT_2, "Order rejected. Insufficient funds. clOrdId: ", clOrdId,
              " totalBalance:", balance.getTotalStableCoinBalance(), " signalTradeValue: ",
              signalTradeValue);
          copyTradeOrder.setResult("REJECTED: insufficient funds.");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return;
        }

        double quantity = MbxMath.roundToBestPrecision(signalTradeValue / xPrice.doubleValue());

        copyTradeOrder.setOrderQty(MbxMath.changeScale(quantity, externalSymbol.getQtyScale()));
        copyTradeOrder.setOrderQtyScale((short) externalSymbol.getQtyScale());

        BigDecimal xQuantity = new BigDecimal(quantity);
        xQuantity = xQuantity.setScale(externalSymbol.getQtyScale(), RoundingMode.HALF_DOWN);

        InstrumentMetaData instrumentMetaData = getInstrumentMetadata(xExchange, instrument);
        if (instrumentMetaData.getMinimumAmount() != null
            && instrumentMetaData.getMinimumAmount().compareTo(xQuantity) > 0) {
          LOGGER.info(Constants.LOG_FMT_5, "Order rejected. Insufficient quantity. clOrdId: ",
              clOrdId, " qty: ", xQuantity,
              " min allowed: " + instrumentMetaData.getMinimumAmount());

          copyTradeOrder.setResult("REJECTED: Insufficient qty.");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return;
        }

        if (instrumentMetaData.getMaximumAmount() != null
            && instrumentMetaData.getMaximumAmount().compareTo(xQuantity) < 0) {
          LOGGER.info(Constants.LOG_FMT_6, "Max quantity reached clOrdId: ", clOrdId, " qty: ",
              xQuantity,
              " max allowed: ", instrumentMetaData.getMaximumAmount());
          xQuantity = instrumentMetaData.getMaximumAmount();
        }

        copyTradeOrder.setxQuantity(xQuantity);
        copyTradeOrder.setxPrice(xPrice);
        copyTradeOrder.setTradeValue(signalTradeValue);
        try {
          xExchange.placeOrder(copyTradeOrder, null);
          copyTradeOrder.setClosed(false);
          CopyTradeCache.add(copyTradeOrder);

          //if (copyTrade.getStatus().equalsIgnoreCase("FILLED")) {
          subscription.setAvailableMaxAmount(
              subscription.getAvailableMaxAmount() - MbxMath.changeScale(signalTradeValue, 2));
          matcherToPublisherQueue.addGuaranteed(subscription);
          //}

          LOGGER.info(Constants.LOG_FMT_16, "Copy trade (open) successful. clOrdId: ", clOrdId,
              " symbol: ", baseSymbol.toUpperCase(), "/",
              quotedSymbol.toUpperCase(), " side: ", copyTradeOrder.getSide().name(), " quantity: ",
              xQuantity, " price: ", copyTradeOrder.getPrice(),
              " orderId:", copyTradeOrder.getExternalId(), " result: ", copyTradeOrder.getResult());
        } catch (Exception e) {
          copyTradeOrder.setResult("FAILED: " + e.getMessage());
          LOGGER.error("Error, Copy trade failed. clOrdId: " + clOrdId, e);
        }

        copyTradeOrder.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(copyTradeOrder);
      }

    }

    // todo remove old implementation
/*
    private void processCloseOrder(final CopyTradeOrder closeCopyTradeOrder, final CopyTradeOrder openCopyTradeOrder) throws Exception {
      final String clOrdId = closeCopyTradeOrder.getClOrdId();
      final long now = System.currentTimeMillis();

      if ((closeCopyTradeOrder.getSourceSendTime() + Context.getMaxDelayToCloseOrderInMs()) < now) {
        LOGGER.info(Constants.LOG_FMT_6, "Order rejected. clOrdId: ", clOrdId, " timeout. sent: ", closeCopyTradeOrder.getSourceSendTime(),
            " processed: ", now);

        closeCopyTradeOrder.setResult("REJECTED: timeout.");
        matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

        return;
      }

      final XExchange xExchange = ExternalExchangeUtil.createXExchange(closeCopyTradeOrder.getSubscription());
      if (xExchange == null) {
        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " invalid exchange: ",
            closeCopyTradeOrder.getSubscription().getExchange());

        closeCopyTradeOrder.setResult("REJECTED: Invalid exchange.");
        closeCopyTradeOrder.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

        return;
      }
      closeCopyTradeOrder.setxExchange(xExchange);

      final CurrencyPair currencyPair = ExternalCurrencyPairCache.get(closeCopyTradeOrder.getBaseSymbol(), closeCopyTradeOrder.getQuotedSymbol());
      final org.knowm.xchange.instrument.Instrument instrument =
          getInstrument(closeCopyTradeOrder.getSubscription().getExchange(), xExchange, currencyPair,
              closeCopyTradeOrder.getSubscription().isFuturesEnabled());
      if (instrument == null) {
        closeCopyTradeOrder.setResult(
            "REJECTED: Invalid instrument. " + currencyPair.getBase().getSymbol() + " " + currencyPair.getCounter().getSymbol());
        closeCopyTradeOrder.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

        return;
      }

      closeCopyTradeOrder.setCurrencyPair(currencyPair);
      closeCopyTradeOrder.setInstrument(instrument);
      openCopyTradeOrder.setCurrencyPair(currencyPair);
      openCopyTradeOrder.setInstrument(instrument);
      //if open order status is not updated.
      if (!ORDER_STATUS_FILLED.equalsIgnoreCase(openCopyTradeOrder.getStatus())) {
        updateOrderStatus(openCopyTradeOrder);
      }
      if (!(openCopyTradeOrder.getStatus() != null && openCopyTradeOrder.getStatus().contains(ORDER_STATUS_FILLED))) {
        LOGGER.warn(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " open order is not filled. open orderId: ",
            openCopyTradeOrder.getClOrdId());

        closeCopyTradeOrder.setResult("REJECTED: Failed to fetch price.");
        closeCopyTradeOrder.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

        return;
      }
      ExternalSymbol symbolStatus =
          ExternalInstrumentCache.getSymbolStatus(closeCopyTradeOrder.getExchange(), closeCopyTradeOrder.getBaseSymbol(),
              closeCopyTradeOrder.getQuotedSymbol(), closeCopyTradeOrder.isFuturesEnabled());
      double price =
          ExternalExchangeHandler.getPrice(closeCopyTradeOrder.getSubscription(), currencyPair, instrument, closeCopyTradeOrder.getSide(), xExchange);

      if (price == 0) {
        LOGGER.warn(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " price: ", price);

        closeCopyTradeOrder.setResult("REJECTED: Failed to fetch price.");
        closeCopyTradeOrder.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

        return;
      }
      //closePricePercentage is scaled by 4
      long closePricePercentage = symbolStatus.getClosePricePercentage();
      BigDecimal xPrice;
      if (Side.SELL == closeCopyTradeOrder.getSide()) {
        price = price - (price * closePricePercentage) / PRICE_PERCENTAGE_SCALE;
        xPrice = new BigDecimal(price);
        xPrice = xPrice.setScale(symbolStatus.getPriceScale(), RoundingMode.HALF_DOWN);
      } else {
        price = price + (price * closePricePercentage) / PRICE_PERCENTAGE_SCALE;
        xPrice = new BigDecimal(price);
        xPrice = xPrice.setScale(symbolStatus.getPriceScale(), RoundingMode.HALF_UP);
      }

      closeCopyTradeOrder.setxPrice(xPrice);

      try {
        //xExchange.updateOrderStatus(openCopyTrade);
        BigDecimal xQuantity = BigDecimal.valueOf(openCopyTradeOrder.getCumulativeAmount());
        xQuantity = xQuantity.setScale(symbolStatus.getQtyScale(), RoundingMode.HALF_UP);
        closeCopyTradeOrder.setxQuantity(xQuantity);

        xExchange.placeOrder(closeCopyTradeOrder, null);
        closeCopyTradeOrder.setClosed(true);
        //update corresponding open order
        openCopyTradeOrder.setClosed(true);
        openCopyTradeOrder.setCloseClOrdId(closeCopyTradeOrder.getClOrdId());
        CopyTradeCache.remove(openCopyTradeOrder);
        matcherToPublisherQueue.addGuaranteed(openCopyTradeOrder);

        if (ORDER_STATUS_FILLED.equalsIgnoreCase(closeCopyTradeOrder.getStatus())) {
          closeCopyTradeOrder.getSubscription().setAvailableMaxAmount(
              closeCopyTradeOrder.getSubscription().getAvailableMaxAmount() + MbxMath.changeScale(xPrice.multiply(xQuantity).doubleValue(), 2));
          matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder.getSubscription());
        } else {
          CopyTradeCache.addOpenOrder(closeCopyTradeOrder);
        }

        LOGGER.info(Constants.LOG_FMT_2, "Copy trade (close) successful. clOrdId: ", clOrdId, " symbol: ",
            closeCopyTradeOrder.getBaseSymbol().toUpperCase(), "/", closeCopyTradeOrder.getQuotedSymbol().toUpperCase(), " side: ",
            closeCopyTradeOrder.getSide().name(), " quantity: ", closeCopyTradeOrder.getxQuantity(), " price: ", closeCopyTradeOrder.getxPrice(),
            " orderId:", closeCopyTradeOrder.getExternalId(), " result: ", closeCopyTradeOrder.getResult());
      } catch (Exception e) {
        closeCopyTradeOrder.setResult("FAILED: " + e.getMessage());
        LOGGER.error("Error, Copy trade failed. clOrdId: " + clOrdId, e);
      }
      //flag hasPending close
      if (closeCopyTradeOrder.getResult() == null || closeCopyTradeOrder.getResult().startsWith("FAILED")) {
        final ExchangeSubscription subscription = closeCopyTradeOrder.getSubscription();
        subscription.setKafkaRecordOffset(closeCopyTradeOrder.getKafkaRecordOffset());
        subscription.setHasPendingClose(true);
        matcherToPublisherQueue.addGuaranteed(subscription);
      }

      closeCopyTradeOrder.setxExchange(null);
      matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

    }
*/

    private void processCloseOrder(final CopyTradeOrder closeCopyTradeOrder, final List<CopyTradeOrder> openCopyTradeOrders) throws Exception {
      if (openCopyTradeOrders == null || openCopyTradeOrders.isEmpty())
        return;

      final String clOrdId = closeCopyTradeOrder.getClOrdId();
      final long now = System.currentTimeMillis();

/*      if ((closeCopyTradeOrder.getSourceSendTime() + Context.getMaxDelayToCloseOrderInMs()) < now) {
        LOGGER.info(Constants.LOG_FMT_6, "Order rejected. clOrdId: ", clOrdId, " timeout. sent: ", closeCopyTradeOrder.getSourceSendTime(),
            " processed: ", now);

        closeCopyTradeOrder.setResult("REJECTED: timeout.");
        matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

        return;
      }*/
      final ExchangeSubscription subscription = closeCopyTradeOrder.getSubscription();
      if (!copyTradeSubscriptionActiveForClose(subscription)) {
        LOGGER.info(Constants.LOG_FMT_4, "Close rejected: MP copy-trade subscription not active. clOrdId: ", clOrdId, " subscriptionId: ",
            closeCopyTradeOrder.getSubscriptionId());
        closeCopyTradeOrder.setResult("REJECTED: subscription not active.");
        closeCopyTradeOrder.setxExchange(null);
        // prevent duplicate publishes
        //matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

        return;
      }
      final String exchange = closeCopyTradeOrder.getExchange();
      final int instrumentType = closeCopyTradeOrder.isFuturesEnabled() ? TARDIS_PERPS : TARDIS_SPOT;
      final String baseSymbol = closeCopyTradeOrder.getBaseSymbol();
      final String quotedSymbol = closeCopyTradeOrder.getQuotedSymbol();
      final ExternalSymbol externalSymbol = ExternalInstrumentCache.getSymbol(exchange, instrumentType, baseSymbol, quotedSymbol);

      if (externalSymbol == null) {
        closeCopyTradeOrder.setResult(
            "REJECTED: Invalid instrument. " + exchange + " : " + instrumentType + " : " + baseSymbol + " : " + quotedSymbol);
        closeCopyTradeOrder.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

        return;
      }

      LOGGER.info(Constants.LOG_FMT_11,
          "MP close copy-trade - ",
          " ordId: ", clOrdId,
          " side: ", closeCopyTradeOrder.getSide().name(),
          " subscriptionId: ", closeCopyTradeOrder.getSubscriptionId(),
          " order result: ", closeCopyTradeOrder.getResult(),
          " symbol: ", closeCopyTradeOrder.getBaseSymbol());

      // use direct API first if configured
      if (subscription.getConnectionType() == ExchangeSubscription.CONNECTION_VIA_DIRECT) {
        final ExternalExchangeClient fastClient = FastClientFactory.createRestOnlyClient(subscription);
        final Ticker ticker = ExternalTickerCache.getTicker(externalSymbol, fastClient);
        double price = ticker.getPrice(closeCopyTradeOrder.getSide());
        if (price == 0) {
          LOGGER.warn(Constants.LOG_FMT_4, "Order rejected. clOrdId: ", clOrdId, " price: ", price);

          closeCopyTradeOrder.setResult("REJECTED: Failed to fetch price.");
          closeCopyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

          return;
        }

        final List<CopyTradeOrder> openCopyTradesToCloseOrder = new ArrayList<>(openCopyTradeOrders.size());
        double quantity = 0;
        for (final CopyTradeOrder openCopyTradeOrder : openCopyTradeOrders) {
          //openCopyTradeOrder.setCurrencyPair(currencyPair);
          //openCopyTradeOrder.setInstrument(instrument);
          //if open order status is not updated.
          if (!ORDER_STATUS_FILLED.equalsIgnoreCase(openCopyTradeOrder.getStatus())) {
            updateOrderStatusDirect(openCopyTradeOrder, subscription);
          }
          if (!(openCopyTradeOrder.getStatus() != null && openCopyTradeOrder.getStatus().contains(ORDER_STATUS_FILLED))) {
            LOGGER.warn(Constants.LOG_FMT_4, "Order rejected. clOrdId: ", clOrdId, " open order is not filled. open orderId: ",
                openCopyTradeOrder.getClOrdId());

            //closeCopyTradeOrder.setResult("REJECTED: Failed to fetch order status.");
            //closeCopyTradeOrder.setxExchange(null);

            //matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);
            continue;
          }
          if (ORDER_STATUS_FILLED.equalsIgnoreCase(openCopyTradeOrder.getStatus())) {
            openCopyTradesToCloseOrder.add(openCopyTradeOrder);
            //quantity += openCopyTradeOrder.getCumulativeAmount();

            if (openCopyTradeOrder.getSide().equals(Side.BUY)) {
              quantity += openCopyTradeOrder.getCumulativeAmount();
            } else {
              quantity -= openCopyTradeOrder.getCumulativeAmount();
            }
            LOGGER.info(Constants.LOG_FMT_17,
                "MP (direct) close combo copy-trade - ",
                " close ordId: ", clOrdId,
                " open ordId: ", openCopyTradeOrder.getClOrdId(),
                " open order side: ", openCopyTradeOrder.getSide().name(),
                " subscriptionId: ", openCopyTradeOrder.getSubscriptionId(),
                " order result: ", openCopyTradeOrder.getResult(),
                " open order qty: ", openCopyTradeOrder.getCumulativeAmount(),
                " combo qty: ", quantity,
                " symbol: ", openCopyTradeOrder.getBaseSymbol());
          }
        }
        if (quantity > 0) {
          closeCopyTradeOrder.setSide(Side.SELL);
        } else {
          closeCopyTradeOrder.setSide(Side.BUY);
          quantity = Math.abs(quantity);
        }
        LOGGER.info(Constants.LOG_FMT_17,
            "MP (direct) close combo copy-trade - ",
            " close ordId: ", clOrdId,
            " open order side: ", closeCopyTradeOrder.getSide().name(),
            " combo qty: ", quantity);


        //closePricePercentage is scaled by 4
        long closePricePercentage = externalSymbol.getClosePricePercentage();
        if (Side.SELL == closeCopyTradeOrder.getSide()) {
          price = price - (price * closePricePercentage) / PRICE_PERCENTAGE_SCALE;
          price = MbxMath.roundDown(price, externalSymbol.getPriceScale());
        } else {
          price = price + (price * closePricePercentage) / PRICE_PERCENTAGE_SCALE;
          price = MbxMath.roundUp(price, externalSymbol.getPriceScale());
        }

        closeCopyTradeOrder.setxPrice(new BigDecimal(price).setScale(externalSymbol.getPriceScale(), RoundingMode.HALF_UP));
        quantity = MbxMath.roundUp(quantity, externalSymbol.getQtyScale());
        closeCopyTradeOrder.setxQuantity(new BigDecimal(quantity).setScale(externalSymbol.getQtyScale(), RoundingMode.HALF_UP));
        closeCopyTradeOrder.setCumulativeAmount(quantity);
        closeCopyTradeOrder.setOrderQty(MbxMath.changeScale(quantity, closeCopyTradeOrder.getOrderQtyScale()));

        try {
          final User user = UserCache.get(subscription.getUserId());
          final Order externalOrder = OrderObjectPool.get();
          externalOrder.setUser(user);
          externalOrder.setAccount(user.getId());
          externalOrder.setClOrdId(closeCopyTradeOrder.getClOrdId());
          externalOrder.setSide(closeCopyTradeOrder.getSide());
          externalOrder.setSymbol(externalSymbol.getSymbol());
          externalOrder.setOrdType(closeCopyTradeOrder.getOrdType());
          externalOrder.setTimeInForce(closeCopyTradeOrder.getTimeInForce());
          externalOrder.setPrice(MbxMath.changeScaleWithRounding(price, externalSymbol.getPriceScale()), (short) externalSymbol.getPriceScale());
          externalOrder.setQty(MbxMath.changeScaleWithRounding(quantity, externalSymbol.getQtyScale()), (short) externalSymbol.getQtyScale());

          ExecutionReportMessage executionReport = subscription.getClient().sendOrder(externalOrder,
              subscription.isFuturesEnabled(), quotedSymbol, baseSymbol, externalSymbol.getPriceScale(),
              externalSymbol.getQtyScale(), 1); //todo set FX rate currently not being used
          if (executionReport != null) {
            if (executionReport.getOrdStatus() == OrdStatus.FILLED) {
              double orderNotional = executionReport.getNotional(); //todo check if notional value is calculated
              //todo fill the below fields from response
              //closeCopyTradeOrder.setExternalId();
              closeCopyTradeOrder.setPriceScale(executionReport.getPriceScale());
              closeCopyTradeOrder.setPrice(executionReport.getAvgPx());
              closeCopyTradeOrder.setAveragePrice(MbxMath.scaleDown(executionReport.getAvgPx(), executionReport.getPriceScale()));
              closeCopyTradeOrder.setOriginalAmount(quantity);
              closeCopyTradeOrder.setCumulativeAmount(MbxMath.scaleDown(executionReport.getCumQty(),
                  executionReport.getOrderQtyScale()));
              closeCopyTradeOrder.setStatus(ORDER_STATUS_FILLED);
              // todo handle fee position
              //closeCopyTradeOrder.setFee(MbxMath.scaleDown(executionReport.getFeeAccumulatedQuantity()));
              closeCopyTradeOrder.setTradeValue(closeCopyTradeOrder.getCumulativeAmount() * closeCopyTradeOrder.getAveragePrice());
              //closeCopyTradeOrder.setTradeValue(orderNotional);
              closeCopyTradeOrder.setResult(SUCCESS);

              closeCopyTradeOrder.setClosed(true);
              //update corresponding open order
              for (final CopyTradeOrder openCopyTradeOrder : openCopyTradesToCloseOrder) {
                openCopyTradeOrder.setClosed(true);
                openCopyTradeOrder.setCloseClOrdId(closeCopyTradeOrder.getClOrdId());
                CopyTradeCache.remove(openCopyTradeOrder);
                matcherToPublisherQueue.addGuaranteed(openCopyTradeOrder);
              }
            } else if (executionReport.getOrdStatus() == OrdStatus.REJECTED || executionReport.getOrdStatus() == OrdStatus.CANCELED
                || executionReport.getExecType() == ExecType.REJECTED) {
              closeCopyTradeOrder.setStatus(ORDER_STATUS_REJECTED);
              closeCopyTradeOrder.setResult(FAILURE);
            }
          }

          if (ORDER_STATUS_FILLED.equalsIgnoreCase(closeCopyTradeOrder.getStatus())) {
            closeCopyTradeOrder.getSubscription().setAvailableMaxAmount(
                closeCopyTradeOrder.getSubscription().getAvailableMaxAmount() + MbxMath.changeScale(price * quantity, 2));
            matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder.getSubscription());
          } else {
            CopyTradeCache.addOpenOrder(closeCopyTradeOrder);
          }

          LOGGER.info(Constants.LOG_FMT_16, "Copy trade (close) successful. clOrdId: ", clOrdId, " symbol: ",
              closeCopyTradeOrder.getBaseSymbol().toUpperCase(), "/", closeCopyTradeOrder.getQuotedSymbol().toUpperCase(), " side: ",
              closeCopyTradeOrder.getSide().name(), " quantity: ", closeCopyTradeOrder.getxQuantity(), " price: ", closeCopyTradeOrder.getxPrice(),
              " orderId:", closeCopyTradeOrder.getExternalId(), " result: ", closeCopyTradeOrder.getResult());
        } catch (Exception e) {
          closeCopyTradeOrder.setResult("FAILED: " + e.getMessage());
          LOGGER.error("Error, Copy trade failed. clOrdId: " + clOrdId, e);
        }

        //flag hasPending close
        if (closeCopyTradeOrder.getResult() == null || closeCopyTradeOrder.getResult().startsWith("FAILED")) {
          subscription.setKafkaRecordOffset(closeCopyTradeOrder.getKafkaRecordOffset());
          subscription.setHasPendingClose(true);
          matcherToPublisherQueue.addGuaranteed(subscription);
        }

        closeCopyTradeOrder.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

        return; // end of direct API
      } else {

        final XExchange xExchange = ExternalExchangeUtil.createXExchange(
            closeCopyTradeOrder.getSubscription());
        if (xExchange == null) {
          LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId,
              " invalid exchange: ",
              closeCopyTradeOrder.getSubscription().getExchange());

          closeCopyTradeOrder.setResult("REJECTED: Invalid exchange.");
          closeCopyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

          return;
        }
        closeCopyTradeOrder.setxExchange(xExchange);

        final CurrencyPair currencyPair = ExternalCurrencyPairCache.get(
            closeCopyTradeOrder.getBaseSymbol(), closeCopyTradeOrder.getQuotedSymbol());
        final org.knowm.xchange.instrument.Instrument instrument =
            getInstrument(closeCopyTradeOrder.getSubscription().getExchange(), xExchange,
                currencyPair,
                closeCopyTradeOrder.getSubscription().isFuturesEnabled());
        if (instrument == null) {
          closeCopyTradeOrder.setResult(
              "REJECTED: Invalid instrument. " + currencyPair.getBase().getSymbol() + " "
                  + currencyPair.getCounter().getSymbol());
          closeCopyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

          return;
        }

        closeCopyTradeOrder.setCurrencyPair(currencyPair);
        closeCopyTradeOrder.setInstrument(instrument);

        final List<CopyTradeOrder> openCopyTradesToCloseOrder = new ArrayList<>(
            openCopyTradeOrders.size());
        double xQuantity = 0;
        for (final CopyTradeOrder openCopyTradeOrder : openCopyTradeOrders) {
          openCopyTradeOrder.setCurrencyPair(currencyPair);
          openCopyTradeOrder.setInstrument(instrument);
          //if open order status is not updated.
          if (!ORDER_STATUS_FILLED.equalsIgnoreCase(openCopyTradeOrder.getStatus())) {
            updateOrderStatus(openCopyTradeOrder);
          }
          if (!(openCopyTradeOrder.getStatus() != null && openCopyTradeOrder.getStatus()
              .contains(ORDER_STATUS_FILLED))) {
            LOGGER.warn(Constants.LOG_FMT_4, "Order rejected. clOrdId: ", clOrdId, " open order is not filled. open orderId: ",
                openCopyTradeOrder.getClOrdId());

            //closeCopyTradeOrder.setResult("REJECTED: Failed to fetch order status.");
            //closeCopyTradeOrder.setxExchange(null);
            //matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);
            continue;
          }
          if (ORDER_STATUS_FILLED.equalsIgnoreCase(openCopyTradeOrder.getStatus())) {
            openCopyTradesToCloseOrder.add(openCopyTradeOrder);
            //xQuantity = xQuantity.add(BigDecimal.valueOf(openCopyTradeOrder.getCumulativeAmount()));
            if (openCopyTradeOrder.getSide().equals(Side.BUY)) {
              xQuantity += openCopyTradeOrder.getCumulativeAmount();
            } else {
              xQuantity -= openCopyTradeOrder.getCumulativeAmount();
            }
            LOGGER.info(Constants.LOG_FMT_11,
                "MP close combo copy-trade - ",
                " close ordId: ", clOrdId,
                " open ordId: ", openCopyTradeOrder.getClOrdId(),
                " open order side: ", openCopyTradeOrder.getSide().name(),
                " subscriptionId: ", openCopyTradeOrder.getSubscriptionId(),
                " order result: ", openCopyTradeOrder.getResult(),
                " open order qty: ", openCopyTradeOrder.getCumulativeAmount(),
                " combo qty: ", xQuantity,
                " symbol: ", openCopyTradeOrder.getBaseSymbol());
          }
        }
        if (xQuantity > 0) {
          closeCopyTradeOrder.setSide(Side.SELL);
        } else {
          closeCopyTradeOrder.setSide(Side.BUY);
          xQuantity = Math.abs(xQuantity);
        }
        LOGGER.info(Constants.LOG_FMT_17,
            "MP close combo copy-trade - ",
            " close ordId: ", clOrdId,
            " open order side: ", closeCopyTradeOrder.getSide().name(),
            " combo qty: ", xQuantity);

        double price =
            ExternalExchangeHandler.getPrice(closeCopyTradeOrder.getSubscription(), currencyPair,
                instrument, closeCopyTradeOrder.getSide(), xExchange);

        if (price == 0) {
          LOGGER.warn(Constants.LOG_FMT_4, "Order rejected. clOrdId: ", clOrdId, " price: ", price);

          closeCopyTradeOrder.setResult("REJECTED: Failed to fetch price.");
          closeCopyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

          return;
        }
        //closePricePercentage is scaled by 4
        long closePricePercentage = externalSymbol.getClosePricePercentage();
        BigDecimal xPrice;
        if (Side.SELL == closeCopyTradeOrder.getSide()) {
          price = price - (price * closePricePercentage) / PRICE_PERCENTAGE_SCALE;
          xPrice = new BigDecimal(price);
          xPrice = xPrice.setScale(externalSymbol.getPriceScale(), RoundingMode.HALF_DOWN);
        } else {
          price = price + (price * closePricePercentage) / PRICE_PERCENTAGE_SCALE;
          xPrice = new BigDecimal(price);
          xPrice = xPrice.setScale(externalSymbol.getPriceScale(), RoundingMode.HALF_UP);
        }

        closeCopyTradeOrder.setxPrice(xPrice);

        closeCopyTradeOrder.setxQuantity(new BigDecimal(xQuantity).setScale(externalSymbol.getQtyScale(), RoundingMode.HALF_UP));
        closeCopyTradeOrder.setCumulativeAmount(xQuantity);

        try {
          xExchange.placeOrder(closeCopyTradeOrder, null);
          closeCopyTradeOrder.setClosed(true);

          if (ORDER_STATUS_FILLED.equalsIgnoreCase(closeCopyTradeOrder.getStatus())) {
            //update corresponding open order
            for (final CopyTradeOrder openCopyTradeOrder : openCopyTradesToCloseOrder) {
              openCopyTradeOrder.setClosed(true);
              openCopyTradeOrder.setCloseClOrdId(closeCopyTradeOrder.getClOrdId());
              CopyTradeCache.remove(openCopyTradeOrder);
              matcherToPublisherQueue.addGuaranteed(openCopyTradeOrder);
            }
            closeCopyTradeOrder.getSubscription().setAvailableMaxAmount(
                closeCopyTradeOrder.getSubscription().getAvailableMaxAmount() + MbxMath.changeScale(
                    xPrice.multiply(BigDecimal.valueOf(xQuantity)).doubleValue(), 2));
            matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder.getSubscription());
          } else {
            CopyTradeCache.addOpenOrder(closeCopyTradeOrder);
          }

          LOGGER.info(Constants.LOG_FMT_2, "Copy trade (close) successful. clOrdId: ", clOrdId,
              " symbol: ",
              closeCopyTradeOrder.getBaseSymbol().toUpperCase(), "/",
              closeCopyTradeOrder.getQuotedSymbol().toUpperCase(), " side: ",
              closeCopyTradeOrder.getSide().name(), " quantity: ",
              closeCopyTradeOrder.getxQuantity(), " price: ", closeCopyTradeOrder.getxPrice(),
              " orderId:", closeCopyTradeOrder.getExternalId(), " result: ",
              closeCopyTradeOrder.getResult());
        } catch (Exception e) {
          closeCopyTradeOrder.setResult("FAILED: " + e.getMessage());
          LOGGER.error("Error, Copy trade failed. clOrdId: " + clOrdId, e);
        }

        //flag hasPending close
        if (closeCopyTradeOrder.getResult() == null || closeCopyTradeOrder.getResult()
            .startsWith("FAILED")) {
          subscription.setKafkaRecordOffset(closeCopyTradeOrder.getKafkaRecordOffset());
          subscription.setHasPendingClose(true);
          matcherToPublisherQueue.addGuaranteed(subscription);
        }

        closeCopyTradeOrder.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(closeCopyTradeOrder);

      }
    }

    private void updateOrderStatus(final CopyTradeOrder copyTradeOrder) throws Exception {
      XExchange xExchange = copyTradeOrder.getxExchange();
      ExchangeSubscription subscription = copyTradeOrder.getSubscription();
      if (xExchange == null) {
        if (subscription == null) {
          subscription = InfluencerSubscriptionCache.get(copyTradeOrder.getSubscriptionId());
          copyTradeOrder.setSubscription(subscription);
        }
        if (subscription != null) {
          xExchange = ExternalExchangeUtil.createXExchange(subscription);
        }
      }
      if (xExchange != null) {
        final String prevStatus = copyTradeOrder.getStatus();
        xExchange.requestGetOrderStatus(copyTradeOrder);
        //if (!prevStatus.equalsIgnoreCase(copyTradeOrder.getStatus()) && ORDER_STATUS_FILLED.equalsIgnoreCase(copyTradeOrder.getStatus())) {
        if (ORDER_STATUS_FILLED.equalsIgnoreCase(copyTradeOrder.getStatus())
            && (prevStatus == null || !prevStatus.equalsIgnoreCase(copyTradeOrder.getStatus()))) {
          CopyTradeCache.remove(copyTradeOrder);
          CopyTradeCache.removeOpenOrder(copyTradeOrder);

          subscription.setAvailableMaxAmount(subscription.getAvailableMaxAmount() + MbxMath.changeScale(
              MbxMath.scaleDown(
                  copyTradeOrder.getPrice(), copyTradeOrder.getPriceScale()) * copyTradeOrder.getCumulativeAmount(), 2));
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);
          matcherToPublisherQueue.addGuaranteed(subscription);

          LOGGER.info(Constants.LOG_FMT_2, "Copy trade (status update) successful. clOrdId: ", copyTradeOrder.getClOrdId(), " symbol: ",
              copyTradeOrder.getBaseSymbol().toUpperCase(), "/", copyTradeOrder.getQuotedSymbol().toUpperCase(), " side: ",
              copyTradeOrder.getSide().name(), " quantity: ", copyTradeOrder.getxQuantity(), " price: ", copyTradeOrder.getxPrice(), " orderId:",
              copyTradeOrder.getExternalId(), " result: ", copyTradeOrder.getResult());

          return;
        } else if (!ORDER_STATUS_FILLED.equalsIgnoreCase(copyTradeOrder.getStatus())) {
          subscription.setKafkaRecordOffset(copyTradeOrder.getKafkaRecordOffset());
          subscription.setHasPendingClose(true);
          matcherToPublisherQueue.addGuaranteed(subscription);
        }
      }
      LOGGER.info(Constants.LOG_FMT_2, "Copy trade (status update) failed. clOrdId: ", copyTradeOrder.getClOrdId(), " symbol: ",
          copyTradeOrder.getBaseSymbol().toUpperCase(), "/", copyTradeOrder.getQuotedSymbol().toUpperCase(), " side: ", copyTradeOrder.getSide().name(),
          " quantity: ", copyTradeOrder.getxQuantity(), " price: ", copyTradeOrder.getxPrice(), " orderId:", copyTradeOrder.getExternalId(), " result: ",
          copyTradeOrder.getResult());

    }

    private void updateOrderStatusDirect(final CopyTradeOrder copyTradeOrder, final ExchangeSubscription subscription) throws Exception {

      final String prevStatus = copyTradeOrder.getStatus();
      final ExecutionReportMessage executionReportMessage = subscription.getClient().getOrder(copyTradeOrder.getClOrdId(),
            null);
      if (executionReportMessage == null) {
        copyTradeOrder.setResult(FAILURE);
        copyTradeOrder.setStatus(ORDER_STATUS_REJECTED);
      } else {
        copyTradeOrder.setStatus(executionReportMessage.getOrdStatus().name());
      }
      LOGGER.info(Constants.LOG_FMT_6, "Check external order status, clOrdId: ", copyTradeOrder.getClOrdId(), " externalId: ",
          copyTradeOrder.getExternalId(), " status: ", copyTradeOrder.getStatus());
      if (ORDER_STATUS_FILLED.equalsIgnoreCase(copyTradeOrder.getStatus())) {
/*        if (summary.getFee() != null) {
          externalOrder.setFee(summary.getFee().doubleValue());
        }*/
        copyTradeOrder.setPriceScale(executionReportMessage.getPriceScale());
        copyTradeOrder.setPrice(executionReportMessage.getPrice());
        copyTradeOrder.setAveragePrice(MbxMath.scaleDown(executionReportMessage.getAvgPx(),
            executionReportMessage.getAvgPxScale()));
        copyTradeOrder.setOriginalAmount(MbxMath.scaleDown(executionReportMessage.getOrderQty(),
            executionReportMessage.getOrderQtyScale()));
        copyTradeOrder.setCumulativeAmount(MbxMath.scaleDown(executionReportMessage.getCumQty(),
            executionReportMessage.getCumQtyScale()));
        copyTradeOrder.setTradeValue(copyTradeOrder.getCumulativeAmount() * copyTradeOrder.getAveragePrice());
        copyTradeOrder.setResult(SUCCESS);

      } else if (ORDER_STATUS_CANCELED.equalsIgnoreCase(copyTradeOrder.getStatus())
          || ORDER_STATUS_REJECTED.equalsIgnoreCase(copyTradeOrder.getStatus())
          || ORDER_STATUS_EXPIRED.equalsIgnoreCase(copyTradeOrder.getStatus())) {
        copyTradeOrder.setResult(FAILURE);
      }
      //if (!prevStatus.equalsIgnoreCase(copyTradeOrder.getStatus()) && ORDER_STATUS_FILLED.equalsIgnoreCase(copyTradeOrder.getStatus())) {
      if (ORDER_STATUS_FILLED.equalsIgnoreCase(copyTradeOrder.getStatus())
          && (prevStatus == null || !prevStatus.equalsIgnoreCase(copyTradeOrder.getStatus()))) {
        CopyTradeCache.remove(copyTradeOrder);
        CopyTradeCache.removeOpenOrder(copyTradeOrder);

        subscription.setAvailableMaxAmount(subscription.getAvailableMaxAmount() + MbxMath.changeScale(
            MbxMath.scaleDown(
                copyTradeOrder.getPrice(), copyTradeOrder.getPriceScale()) * copyTradeOrder.getCumulativeAmount(), 2));
        matcherToPublisherQueue.addGuaranteed(copyTradeOrder);
        matcherToPublisherQueue.addGuaranteed(subscription);

        LOGGER.info(Constants.LOG_FMT_2, "Copy trade (status update) successful. clOrdId: ", copyTradeOrder.getClOrdId(), " symbol: ",
            copyTradeOrder.getBaseSymbol().toUpperCase(), "/", copyTradeOrder.getQuotedSymbol().toUpperCase(), " side: ",
            copyTradeOrder.getSide().name(), " quantity: ", copyTradeOrder.getxQuantity(), " price: ", copyTradeOrder.getxPrice(), " orderId:",
            copyTradeOrder.getExternalId(), " result: ", copyTradeOrder.getResult());

        return;
      } else if (!ORDER_STATUS_FILLED.equalsIgnoreCase(copyTradeOrder.getStatus())) {
        subscription.setKafkaRecordOffset(copyTradeOrder.getKafkaRecordOffset());
        subscription.setHasPendingClose(true);
        matcherToPublisherQueue.addGuaranteed(subscription);
      }

      LOGGER.info(Constants.LOG_FMT_2, "Copy trade (status update) failed. clOrdId: ", copyTradeOrder.getClOrdId(), " symbol: ",
          copyTradeOrder.getBaseSymbol().toUpperCase(), "/", copyTradeOrder.getQuotedSymbol().toUpperCase(), " side: ", copyTradeOrder.getSide().name(),
          " quantity: ", copyTradeOrder.getxQuantity(), " price: ", copyTradeOrder.getxPrice(), " orderId:", copyTradeOrder.getExternalId(), " result: ",
          copyTradeOrder.getResult());

    }

    private double calcMaxTradeValueByMarketCap(final ExchangeSubscription subscription, final CopyTradeOrder copyTradeOrder,
        double maxTradeValue) {
      final String clOrdId = copyTradeOrder.getClOrdId();
      final String baseSymbol = copyTradeOrder.getBaseSymbol();
      Set<String> symbolsToCalculateMarketCapRatio = null;
      //calculate based on user preferred currencies
      if (subscription.getPreferredCurrencies() != null && subscription.getPreferredCurrencies().length() > 2) {
        symbolsToCalculateMarketCapRatio = Set.of(subscription.getPreferredCurrencies().split("-"));
      }
      //calculate based on infulencer trade currencies
      if (symbolsToCalculateMarketCapRatio == null) {
        symbolsToCalculateMarketCapRatio = InfluencerSymbolsCache.getUserSymbols(copyTradeOrder.getAccountId());
      }

      //calculate max trade value based on market cap of preferred symbols
      if (symbolsToCalculateMarketCapRatio != null && !symbolsToCalculateMarketCapRatio.isEmpty()) {
        double marketCapOfSymbol = 0, marketCapOdPreferredSymbols = 0;
        MarketCapCache.MarketCap marketCap = MarketCapCache.getMarketCap(baseSymbol);
        if (marketCap == null) {
          LOGGER.info(Constants.LOG_FMT_4, "Unable to load market cap for the order. clOrdId: ", clOrdId, " symbol: ", baseSymbol);
          copyTradeOrder.setResult("REJECTED: Failed to fetch market cap.");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return -1;
        }
        marketCapOfSymbol = marketCap.getMarketCap();
        for (final String s : symbolsToCalculateMarketCapRatio) {
          marketCap = MarketCapCache.getMarketCap(s);
          marketCapOdPreferredSymbols += marketCap.getMarketCap();
        }
        if (marketCapOfSymbol == 0 || marketCapOdPreferredSymbols == 0) {
          LOGGER.info(Constants.LOG_FMT_4, "Unable to load market cap for the order. clOrdId: ", clOrdId, " symbol: ", baseSymbol);
          copyTradeOrder.setResult("REJECTED: Failed to fetch market cap.");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return -1;
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
          copyTradeOrder.setResult("REJECTED: Failed to fetch market cap.");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return -1;
        }
      }
      return maxTradeValue;
    }

/*    private boolean autoConvertStableCoins(final ExchangeSubscription subscription, final CopyTradeOrder copyTradeOrder,
    final double signalTradeValue, final double balanceRequired) {
      final String baseSymbol = copyTradeOrder.getBaseSymbol();
      final String quotedSymbol = copyTradeOrder.getQuotedSymbol();
      final String clOrdId = copyTradeOrder.getClOrdId();
      final Side side = copyTradeOrder.getSide();
      if (!subscription.isFuturesEnabled() && subscription.getPercentage() <= 10_000 *//* no margin *//* && subscription.getBalance(
          quotedSymbol) < signalTradeValue && side == Side.BUY) {
        double totalStableCoinBalance = subscription.getTotalStableCoinBalance();
        if (balanceRequired > totalStableCoinBalance) {
          LOGGER.info(Constants.LOG_FMT_4, "Order rejected. clOrdId: ", clOrdId, " exchangeBalance: ", totalStableCoinBalance);

          copyTradeOrder.setResult("REJECTED: Insufficient balance. balance: " + totalStableCoinBalance + ".");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return false;
        }
        LOGGER.info(Constants.LOG_FMT_6, "Balance required for the order: ", copyTradeOrder.getClOrdId(), " is ", balanceRequired, " ",
            quotedSymbol);
        double usd = subscription.getBalance(USD);
        double usdc = subscription.getBalance(USDC);
        double usdt = subscription.getBalance(USDT);
        double availableBalance = 0;
        try {
          if (USD.equalsIgnoreCase(quotedSymbol)) {// best quote is in USD. convert other stable coins to USD by selling
            availableBalance = usd;
            if (usdt > 0) {
              final double usdtAmountToConvert = Math.min(usdt, (balanceRequired - availableBalance));
              final Ticker ticker = subscription.getClient().getTicker(USDT, USD, TARDIS_SPOT);
              final double priceValue = ticker.getBid();
              final ExternalSymbol externalSymbol = ExternalInstrumentCache.getSymbolStatus(copyTradeOrder.getExchange(), USDT, USD, false);//todo check if spot or futures

              final Order externalOrder = OrderObjectPool.get();
              externalOrder.setUser(copyTradeOrder.getUser());
              externalOrder.setClOrdId(copyTradeOrder.getClOrdId() + "S1");
              externalOrder.setSide(Side.SELL);
              externalOrder.setSymbol(externalSymbol.getSymbol());
              externalOrder.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
              externalOrder.setPrice(MbxMath.changeScaleWithRounding(priceValue, externalSymbol.getPriceScale()), (short) externalSymbol.getPriceScale());
              externalOrder.setQty(MbxMath.changeScaleWithRounding(usdtAmountToConvert, externalSymbol.getQtyScale()), (short) externalSymbol.getQtyScale());

              ExecutionReportMessage executionReport = subscription.getClient().sendOrder(externalOrder, subscription.isFuturesEnabled(), USD, USDT, externalSymbol.getPriceScale(), externalSymbol.getQtyScale(), 1); //todo set FX rate
              if (executionReport != null && executionReport.getOrdStatus() == OrdStatus.FILLED) {
                availableBalance += executionReport.getNotional(); //todo check if notional value is calculated
              }

            }
            if (usdc > 0 && availableBalance < balanceRequired) {
              final double usdcAmountToConvert = Math.min(usdc, (balanceRequired - availableBalance));
              final Ticker ticker = subscription.getClient().getTicker(USDC, USD, TARDIS_SPOT);
              final double priceValue = ticker.getBid();
              final ExternalSymbol externalSymbol = ExternalInstrumentCache.getSymbolStatus(copyTradeOrder.getExchange(), USDC, USD, false);//todo check if spot or futures

              final Order externalOrder = OrderObjectPool.get();
              externalOrder.setUser(copyTradeOrder.getUser());
              externalOrder.setClOrdId(copyTradeOrder.getClOrdId()+ "S2");
              externalOrder.setSide(Side.SELL);
              externalOrder.setSymbol(externalSymbol.getSymbol());
              externalOrder.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
              externalOrder.setPrice(MbxMath.changeScaleWithRounding(priceValue, externalSymbol.getPriceScale()), (short) externalSymbol.getPriceScale());
              externalOrder.setQty(MbxMath.changeScaleWithRounding(usdcAmountToConvert, externalSymbol.getQtyScale()), (short) externalSymbol.getQtyScale());

              ExecutionReportMessage executionReport = subscription.getClient().sendOrder(externalOrder, subscription.isFuturesEnabled(), USD, USDT, externalSymbol.getPriceScale(), externalSymbol.getQtyScale(), 1); //todo set FX rate
              if (executionReport != null && executionReport.getOrdStatus() == OrdStatus.FILLED) {
                availableBalance += executionReport.getNotional(); //todo check if notional value is calculated
              }

            }
          } else if (USDC.equalsIgnoreCase(quotedSymbol)) {// best quote is in USDC. convert other stable coins to USDC
            availableBalance = usdc;
            if (usdt > 0) {
              final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDC, USDT);
              final org.knowm.xchange.instrument.Instrument conversionInstrument =
                  getInstrument(subscription.getExchange(), xExchange, conversionPair, subscription.isFuturesEnabled());
              final ExternalSymbol
                  metaData = ExternalInstrumentCache.getSymbolStatus(copyTradeOrder.getExchange(), USDC, USDT, subscription.isFuturesEnabled());
              final double priceValue =
                  ExternalExchangeHandler.getPrice(subscription, conversionPair, conversionInstrument, Side.BUY, xExchange);
              final double usdtAmountToConvert = Math.min(usdt, (balanceRequired - availableBalance));
              final double convertedAmount =
                  xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument, usdtAmountToConvert, priceValue,
                      Side.BUY, clOrdId + "B1", metaData);
              availableBalance += convertedAmount;
            }
            if (usd > 0 && availableBalance < balanceRequired) {
              final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDC, USD);
              final org.knowm.xchange.instrument.Instrument conversionInstrument =
                  getInstrument(subscription.getExchange(), xExchange, conversionPair, subscription.isFuturesEnabled());
              final ExternalSymbol metaData = ExternalInstrumentCache.getSymbolStatus(copyTradeOrder.getExchange(), USDC, USD, subscription.isFuturesEnabled());
              final double priceValue =
                  ExternalExchangeHandler.getPrice(subscription, conversionPair, conversionInstrument, Side.BUY, xExchange);
              final double usdAmountToConvert = Math.min(usd, (balanceRequired - availableBalance));
              final double convertedAmount =
                  xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument, usdAmountToConvert, priceValue, Side.BUY,
                      clOrdId + "B2", metaData);
              availableBalance += convertedAmount;
            }
          } else if (USDT.equalsIgnoreCase(quotedSymbol)) {// best quote is in USDT. convert other stable coins to USDT
            availableBalance = usdt;
            if (usdc > 0) {
              final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDC, USDT);
              final org.knowm.xchange.instrument.Instrument conversionInstrument =
                  getInstrument(subscription.getExchange(), xExchange, conversionPair, subscription.isFuturesEnabled());
              final ExternalSymbol
                  metaData = ExternalInstrumentCache.getSymbolStatus(copyTradeOrder.getExchange(), USDC, USDT, subscription.isFuturesEnabled());
              final double priceValue =
                  ExternalExchangeHandler.getPrice(subscription, conversionPair, conversionInstrument, Side.SELL, xExchange);
              final double usdcAmountToConvert = Math.min(usdc, (balanceRequired - availableBalance));

              final double convertedAmount =
                  xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument, usdcAmountToConvert, priceValue,
                      Side.SELL, clOrdId + "S1", metaData);
              availableBalance += convertedAmount;
            }
            if (availableBalance < balanceRequired && usd > 0) {
              final CurrencyPair conversionPair = ExternalCurrencyPairCache.get(USDT, USD);
              final org.knowm.xchange.instrument.Instrument conversionInstrument =
                  getInstrument(subscription.getExchange(), xExchange, conversionPair, subscription.isFuturesEnabled());
              final ExternalSymbol metaData = ExternalInstrumentCache.getSymbolStatus(copyTradeOrder.getExchange(), USDT, USD, subscription.isFuturesEnabled());
              final double priceValue =
                  ExternalExchangeHandler.getPrice(subscription, conversionPair, conversionInstrument, Side.BUY, xExchange);
              final double usdAmountToConvert = Math.min(usd, (balanceRequired - availableBalance));
              final double convertedAmount =
                  xExchange.placeConversionOrder(subscription.getExchange(), conversionInstrument, usdAmountToConvert, priceValue, Side.BUY,
                      clOrdId + "B2", metaData);
              availableBalance += convertedAmount;
            }
          }
          signalTradeValue = availableBalance;
        } catch (ExchangeException e) {
          LOGGER.error(ERROR_LOG, e);
          LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " failed to convert.");

          copyTradeOrder.setResult("REJECTED: " + e.getMessage());
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return false;
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, e);
          LOGGER.info(Constants.LOG_FMT_2, "Order rejected. clOrdId: ", clOrdId, " failed to convert.");

          copyTradeOrder.setResult("REJECTED: failed to convert stable currencies.");
          copyTradeOrder.setxExchange(null);
          matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

          return false;
        }
      } else if (subscription.isFuturesEnabled() && balance.getTotalStableCoinBalance() < signalTradeValue && side == Side.BUY) {//balance check for future orders
        LOGGER.info(Constants.LOG_FMT_2, "Order rejected. Insufficient funds. clOrdId: ", clOrdId, " totalBalance:", balance.getTotalStableCoinBalance(), " signalTradeValue: ", signalTradeValue);
        copyTradeOrder.setResult("REJECTED: insufficient funds.");
        copyTradeOrder.setxExchange(null);
        matcherToPublisherQueue.addGuaranteed(copyTradeOrder);

        return false;
      }

      return true;
    }*/
  }

  private static InstrumentMetaData getInstrumentMetadata(final XExchange xExchange,
      final org.knowm.xchange.instrument.Instrument instrument) {
    try {
      return xExchange.getExchangeMetaData().getInstruments().get(instrument);
/*      final Map<Instrument, InstrumentMetaData> map =
          (Map<Instrument, InstrumentMetaData>) (xExchange.getExchangeMetaData()).getInstruments();
      return map.get(instrument);*/
    } catch (Exception e) {
      LOGGER.error(Constants.LOG_FMT_2, "Instrument metadata not available. ", instrument.getBase(), "/", instrument.getCounter());
    }
    return defaultMetadata;
  }

}
