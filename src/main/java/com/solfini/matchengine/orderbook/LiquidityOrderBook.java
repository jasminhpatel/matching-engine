package com.solfini.matchengine.orderbook;

import static com.solfini.instrument.Position.assetIdComparator;

import com.solfini.common.*;
import com.solfini.instrument.*;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.drmode.message.DRCancelOrder;
import com.solfini.matchengine.drmode.message.DRExecutionReport;
import com.solfini.matchengine.drmode.message.DROrder;
import com.solfini.matchengine.liquidity.ExternalExchangeCache;
import com.solfini.matchengine.liquidity.LiquiditySubscriptionCache;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.CollateralSwapMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.*;
import com.solfini.matchengine.message.internal.LiquidityResponse.Liquidity;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.matchengine.message.outbound.CancelRejectMessage;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.matchengine.orderbook.validator.OrderBookValidatorFactory;
import com.solfini.pool.*;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.preordercheck.NoPreOrderCheck;
import com.solfini.preordercheck.NotionalMarginCalc;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.report.StateValidator;
import com.solfini.risk.InsuranceState;
import com.solfini.risk.UserRiskCache;
import com.solfini.sbe.encoder.*;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder.MdEntrieGroupEncoder;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.CMCTop30Checker;
import com.solfini.util.MbxMath;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;

public class LiquidityOrderBook extends GlobalOrderBook implements OrderBook, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LiquidityOrderBook.class);

  private static final ConcurrentHashMap<Integer, DoubleAdder> MARKET_MAKER_POSITION = new ConcurrentHashMap<>();
  private static final String AUTOCLOSE_STR = "autoclose";
  private static final String AUTOCLOSE_MAKER_STR = "autoclose_maker";
  private static final String EXPIRESETTLE_STR = "expiresettle";

  private static boolean initialized = false;

  public final int ARR_SIZE = 0;
  public final int CACHE_DEPTH = 0;

  private final int id;
  private final int priceScale;
  private final int quanityScale;
  private final int orderBookStrategy;
  private final int preOrderCheckStrategy;
  private final InstrumentPair instrumentPair;
  // private final Map<Long, Order> outOfBoundsOrderMap; // key is orderId

  private PreOrderCheck preOrderCheck;
  private final PreOrderCheck preOrderCheckOrig;
  private final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue;

  // <tardisExchangeId, <tardisSymbolId>> Note: tardisExchangeId, tardisSymbolId combination is unique across all exchanges, symbols and
  // instrument types
  private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, LiquidityResponse.Liquidity>> exchangeSymbolLiquidity = new ConcurrentHashMap<>();

  private MarketStatus marketStatus;
  private int[] bidLevelCachePtrArr = new int[1];
  private int[] askLevelCachePtrArr = new int[1];
  private final OrderBookPriceLevel[] bookArr = new OrderBookPriceLevel[1];

  private int orderCount;
  private long filledCount;
  private int last;
  private int mark;
  private double usdMark;
  private double circuitBreakerThreshold;
  private Instrument settleCoinUsdMarkInstrument;
  private final NewOrderSingleHandler newOrderSingleHandler = new NewOrderSingleHandler();
  private OrderBookValidator validator;
  private long secondaryOrderId = 0;
  private boolean rebuildInProgress = false;
  private long circuitBreakerEndTime = 0;
  private boolean publishAcks = true;
  private boolean usdAutoConvertEnabled = true;
  private boolean publishPositions = true;

  public static synchronized void initialize() {
    if (!initialized) {
      Position[] marketMakerPositions = UserCache.getMarketMakerUser().getPositionArr();
      for (int i = 1; i < marketMakerPositions.length; i++) {// skip 0 because there is no instrument with id 0
        Position position = marketMakerPositions[i];
        if (position != null) {
          short quantityScale = 0;
          Instrument instrument = InstrumentCache.get(i);
          if (instrument != null) {
            quantityScale = instrument.getQuantityScale();
          } else {
            InstrumentPair pair = InstrumentCache.getPair(i);
            if (pair != null) {
              quantityScale = pair.getQuantityScale();
            }
          }
          double qty = MbxMath.scaleDown(position.getQuantity(), quantityScale);
          final DoubleAdder positionQty = MARKET_MAKER_POSITION.computeIfAbsent(i, k -> new DoubleAdder());
          positionQty.add(qty);
        }
      }
    }
    initialized = true;
  }

  public void setPublishAcks(final boolean publishAcks) {
    this.publishAcks = publishAcks;
  }

  public void setUsdAutoConvertEnabled(final boolean usdAutoConvertEnabled) {
    this.usdAutoConvertEnabled = usdAutoConvertEnabled;
  }

  public void setPublishPositions(final boolean publishPositions) {
    this.publishPositions = publishPositions;
  }

  public static DoubleAdder getMarketMakerPositionQty(int instrumentId) {
    return MARKET_MAKER_POSITION.computeIfAbsent(instrumentId, k -> new DoubleAdder());
  }

  public LiquidityOrderBook(final InstrumentPair pair, final PreOrderCheck preOrderCheck, final int orderBookStrategy,
      final int preOrderCheckStrategy) {
    this(pair, preOrderCheck, 0, 0, orderBookStrategy, preOrderCheckStrategy);
  }

  public LiquidityOrderBook(final InstrumentPair pair, final PreOrderCheck preOrderCheck, final int arrSize, final int cacheDepth,
      final int orderBookStrategy, final int preOrderCheckStrategy) {
    this.id = pair.getId();
    this.priceScale = pair.getPriceScale();
    this.quanityScale = pair.getQuantityScale();
    this.instrumentPair = pair;
    this.preOrderCheck = preOrderCheck;
    this.preOrderCheckOrig = preOrderCheck;
    if (MarketStatus.DR_MODE == Context.getMarketStatus()) {
      this.preOrderCheck = new NoPreOrderCheck();
    } else if (pair.getMarketStatus() != null) {
      this.marketStatus = pair.getMarketStatus();
    }

    this.matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
    /*
     * this.triggeredOrders = new FastArrayList<>(); this.stopLimitContainer = new StopLimitContainer(id); this.stopProfitContainer = new
     * StopProfitContainer(id); this.trailingStopContainer = new TrailingStopContainer(pair); this.adlMakerContainer = new
     * ADLMakerContainer(id); this.auctionContainer = new AuctionContainer(pair);
     */

    // this.ARR_SIZE = arrSize > 0 ? arrSize : DEFAULT_ARR_SIZE;
    // this.CACHE_DEPTH = cacheDepth > 0 ? cacheDepth : DEFAULT_CACHE_DEPTH;
    // this.bidLevelCachePtrArr = new int[CACHE_DEPTH];
    // this.askLevelCachePtrArr = new int[CACHE_DEPTH];
    // this.bookArr = new OrderBookPriceLevel[ARR_SIZE];
    // this.outOfBoundsOrderMap = new HashMap<>();
    // this.outOfBoundsOrderMapBySecondaryOrderId = new HashMap<>();
    this.settleCoinUsdMarkInstrument = InstrumentCache.getBySymbol(USD);
    this.usdMark = pair.getIndexFeedUsdMark();
    this.orderBookStrategy = orderBookStrategy;
    this.preOrderCheckStrategy = preOrderCheckStrategy;
    this.validator = OrderBookValidatorFactory.newOrderBookValidator(this);
    this.circuitBreakerThreshold = pair.getCircuitBreakerThreshold();

    /*
     * if (LOGGER.isInfoEnabled()) { LOGGER.info(LOG_FMT_22, "init orderbook: id=", id, PRICESCALE_EQ, priceScale, QUANTITYSCALE_EQ,
     * quanityScale, ", ARR_SIZE=", 0, ", CACHE_DEPTH=", 0, ORDERBOOKSTRATEGY_EQ, orderBookStrategy, PREORDERCHECKSTRATEGY_EQ,
     * preOrderCheckStrategy, ", DEFAULT_ARR_SIZE=", 0, ", DEFAULT_CACHE_DEPTH=", 0, ", circuitBreakerThreshold=", circuitBreakerThreshold,
     * ", usdMark=", usdMark); }
     */
  }

  public final int getId() {
    return id;
  }

  public final int getQuanityScale() {
    return quanityScale;
  }

  public final int getPriceScale() {
    return priceScale;
  }

  public final int getOrderCount() {
    return orderCount;
  }

  public final void setFilledCount(final long newValue) {
    this.filledCount = newValue;
  }

  public final void setFilledCountIfGreater(final long newValue) {
    if (newValue > filledCount) {
      this.filledCount = newValue;
    }
  }

  public final long getFilledCount() {
    return filledCount;
  }

  public void setSecondaryOrderIdIfGreater(final long newValue) {
    if (newValue > secondaryOrderId) {
      secondaryOrderId = newValue;
    }
  }

  public long getSecondaryOrderId() {
    return secondaryOrderId;
  }

  public final MarketStatus getMarketStatus() {
    return marketStatus;
  }

  public final Instrument getSettleCoinUsdMarkInstrument() {
    return settleCoinUsdMarkInstrument;
  }

  @Override
  public final InstrumentPair getInstrumentPair() {
    return instrumentPair;
  }

  public final void setSettleCoinUsdMarkInstrument(final Instrument settleCoinUsdMarkInstrument) {
    this.settleCoinUsdMarkInstrument = settleCoinUsdMarkInstrument;
  }

  public final int[] getBidLevelCachePtrArr() {
    return bidLevelCachePtrArr;
  }

  public final int[] getAskLevelCachePtrArr() {
    return askLevelCachePtrArr;
  }


  @Override
  public final StopLimitContainer getStopLimitContainer() {
    return null;
  }

  @Override
  public final StopProfitContainer getStopProfitContainer() {
    return null;
  }

  @Override
  public final int getOrderBookStrategy() {
    return orderBookStrategy;
  }

  @Override
  public final int getPreOrderCheckStrategy() {
    return preOrderCheckStrategy;
  }

  @Override
  public final int getBid() {
    return bidLevelCachePtrArr[0];
  }

  @Override
  public final int getAsk() {
    return askLevelCachePtrArr[0];
  }

  @Override
  public final int getLast() {
    return last;
  }

  @Override
  public final int getMark() {
    return mark;
  }

  @Override
  public final TrailingStopContainer getTrailingStopContainer() {
    return null;
  }

  @Override
  public final void setMark(final int mark) {
    this.mark = mark;
    double usdMarkTemp = mark;
    for (int i = 0; i < instrumentPair.getPriceScale(); i++)
      usdMarkTemp = usdMarkTemp * 0.1;
    this.usdMark = usdMarkTemp;
  }

  @Override
  public final double getUsdMark() {
    int countSide = 0;
    if (this.usdMark == 0) {
      // calc a mid
      double usdMarkTemp = 0;
      final int bid = bidLevelCachePtrArr[0];
      final int ask = askLevelCachePtrArr[0];
      if (bid > 0) {
        usdMarkTemp += bid;
        countSide++;
      }
      if (ask > 0) {
        usdMarkTemp += ask;
        countSide++;
      }
      for (int i = 0; i < instrumentPair.getPriceScale(); i++)
        usdMarkTemp = usdMarkTemp * 0.1;
      if (countSide > 0)
        usdMarkTemp = usdMarkTemp / countSide;
      return usdMarkTemp;
    }
    if ((bidLevelCachePtrArr[0] > 0 || askLevelCachePtrArr[0] > 0) && AssetType.OPTION_CALL != instrumentPair.getAssetType()
        && AssetType.OPTION_PUT != instrumentPair.getAssetType())
      return this.usdMark;

    return 0; // if we have no quotes return 0, options pricing needs a real price
  }

  @Override
  public final PreOrderCheck getPreOrderCheck() {
    return preOrderCheck;
  }

  @Override
  public final void addOrder(final Order order) {
    if (!initialized) {
      initialize();
    }
    // handle external orders separately
    if (order.getTargetStrategy() == EXTERNAL || order.getTargetStrategy() == LIQUIDATION) {
      //LOGGER.info(LOG_FMT_2, "EXTERNAL Order Received: ", order.toJSON());
      addExternalOrder(order);
      return;
    }
    //LOGGER.info(LOG_FMT_2, "Liquidity Order Received: ", order.toJSON());
    // validate
    if ((order.getPriceInt() <= 0 && !(order.getType() == BUY_MARKET || order.getType() == SELL_MARKET || order.isMarket()))) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, REJECT_ORDER_EQ, PRICE_IS_MISSING, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order == null ? "" : order.getSenderCompId(),
          MsgType.ORDER_SINGLE, Long.toString(order == null ? 0 : order.getOrderId()), BusinessRejectReason.PRICE_IS_MISSING,
          PRICE_IS_MISSING, order == null ? 0 : order.getOrderId(), order == null ? 0 : order.getSourceSeqNum(),
          order == null ? 0 : order.getSecondaryOrderId(), order == null ? 0 : order.getSecurityId(),
          order.getClOrdId(), order.getAccount()));
      OrderObjectPool.returnObject(order);

      return;
    } else if (order.getQuantityLong() <= 0) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, QUANTITY_IS_MISSING, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.QUANTITY_IS_MISSING, QUANTITY_IS_MISSING, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(),
          order.getClOrdId(), order.getAccount()));
      OrderObjectPool.returnObject(order);

      return;
    } else if (MarketStatus.CLOSE == marketStatus || MarketStatus.PAUSE == marketStatus) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, MARKET_IS_PAUSED_OR_CLOSED, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.MARKET_IS_PAUSED_OR_CLOSED, MARKET_IS_PAUSED_OR_CLOSED,
          order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(),
          order.getClOrdId(), order.getAccount()));
      OrderObjectPool.returnObject(order);

      return;
    }

    if (order.getSecurityId() != id) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, "adding invalid security in order. id=", id, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.INVALID_ORDER_SECURITY, INVALID_ORDER_SECURITY, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(),
          order.getClOrdId(), order.getAccount()));
      OrderObjectPool.returnObject(order);

      return;
    }
    if (instrumentPair.isLimitOnlyMode() && order.getOrdType() != OrdType.LIMIT) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, "only limit orders are allowed id=", id, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.ONLY_LIMIT_ORDERS_ALLOWED, ONLY_LIMIT_ORDERS_ALLOWED, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(),
          order.getClOrdId(), order.getAccount()));
      OrderObjectPool.returnObject(order);

      return;
    }

    final User user = order.getUser();
    final int targetStrategy = order.getTargetStrategy();
    final long kafkaRecordOffset = order.getKafkaRecordOffset();

    if (targetStrategy == AUTO_CONVERT) {
      final Instrument usd = InstrumentCache.getBySymbol(USD);
      final Position usdPosition = user.getPositionArr()[usd.getId()];
      if (usdPosition == null) {
        LOGGER.info(LOG_FMT_3, "Reject auto-convert for user: ", user.getId(), " no available USD positions.");
        return;
      }
      LOGGER.info(LOG_FMT_4, "Processing auto conversion. User Id: ", user.getId(), " usdBalance:", usdPosition.getUsdValue());
      if (usdPosition.getQuantity() >= -MbxMath.changeScale(Context.getAutoConvertMinAmount(), usd.getQuantityScale())) {
        LOGGER.info(LOG_FMT_2, "Processing auto conversion. User Id: ", user.getId());
        return;
      }
    }

    // update ids
    if (order.getSecurityId() == id) {
      GlobalOrderBook.setOrderIdIfGreater(15, order.getOrderId());
      setSecondaryOrderIdIfGreater(order.getSecondaryOrderId());
    } else {
      LOGGER.info(Constants.LOG_FMT_6, "Invalid securityId. order.getSecurityId(): ", order.getSecurityId(), " bookSecurityId: ", id,
          " clOrdId: ", order.getClOrdId());
    }

    // ack - moved to after preordercheck

    if (!order.isOrderModified())
      orderCount++;

    switch (order.getType()) {
      case BUY_LIMIT:
        addBuyLimitCheck(order);
        if (order.isRejected()) {
            return;
        }
        addBuyLimit(order);
        break;
      case SELL_LIMIT:
        addSellLimitCheck(order);
        if (order.isRejected()) {
            return;
        }
        addSellLimit(order);
        break;
      /*
       * case BUY_MARKET: addBuyMarket(order); break; case SELL_MARKET: addSellMarket(order); break; case STOP_BUY_LIMIT:
       * addStopBuyLimit(order); break; case STOP_SELL_LIMIT: addStopSellLimit(order); break;
       */
      default: {
        LOGGER.info(Constants.LOG_FMT_2, "Unhandled order type: ", order.getType());
        // return;
      }
    }

    // convert stable coins to settle USD balance
    if (usdAutoConvertEnabled && targetStrategy != AUTO_CONVERT) {
      autoConvertStableCoinsToSettle(user, kafkaRecordOffset);
    }
    // processTriggeredOrders();
    // checkCircuitBreaker();
    OrderObjectPool.returnObject(order);
  }

  public final void addExternalOrder(final Order order) {
    // order rejected and reject message was sent from router, call cancelOrder and exit
    if (order.isRejected()) {
      // business reject is already sent
      LOGGER.info("Liquidity Order Cancelled: " + order.toJSON());
      preOrderCheck.updateCancel(order);
      OrderObjectPool.returnObject(order);

      return;
    }
    // new external order, validate and execute
    if ((order.getTargetStrategy() == EXTERNAL || order.getTargetStrategy() == LIQUIDATION) && !order.isExecuted()) {
      //LOGGER.info("Liquidity Order Received: " + order.toJSON());
      // validate
      if ((order.getPriceInt() <= 0 && !(order.getType() == BUY_MARKET || order.getType() == SELL_MARKET || order.isMarket()))) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_4, REJECT_ORDER_EQ, PRICE_IS_MISSING, ORDER_EQ, order);
        }
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order == null ? "" : order.getSenderCompId(),
            MsgType.ORDER_SINGLE, Long.toString(order == null ? 0 : order.getOrderId()), BusinessRejectReason.PRICE_IS_MISSING,
            PRICE_IS_MISSING, order == null ? 0 : order.getOrderId(), order == null ? 0 : order.getSourceSeqNum(),
            order == null ? 0 : order.getSecondaryOrderId(), order == null ? 0 : order.getSecurityId(),
            order.getClOrdId(), order.getAccount()));
        OrderObjectPool.returnObject(order);

        return;
      } else if (order.getQuantityLong() <= 0) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, QUANTITY_IS_MISSING, ORDER_EQ, order);
        }
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.QUANTITY_IS_MISSING, QUANTITY_IS_MISSING, order.getOrderId(),
            order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(),
            order.getClOrdId(), order.getAccount()));
        OrderObjectPool.returnObject(order);

        return;
      } else if (MarketStatus.CLOSE == marketStatus || MarketStatus.PAUSE == marketStatus) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, MARKET_IS_PAUSED_OR_CLOSED, ORDER_EQ, order);
        }
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.MARKET_IS_PAUSED_OR_CLOSED, MARKET_IS_PAUSED_OR_CLOSED,
            order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(),
            order.getClOrdId(), order.getAccount()));
        OrderObjectPool.returnObject(order);

        return;
      }

      if (order.getSecurityId() != id) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_4, "adding invalid security in order. id=", id, ORDER_EQ, order);
        }
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.INVALID_ORDER_SECURITY, INVALID_ORDER_SECURITY, order.getOrderId(),
            order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(),
            order.getClOrdId(), order.getAccount()));
        OrderObjectPool.returnObject(order);

        return;
      }
      if (instrumentPair.isLimitOnlyMode() && order.getOrdType() != OrdType.LIMIT) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_4, "only limit orders are allowed id=", id, ORDER_EQ, order);
        }
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.ONLY_LIMIT_ORDERS_ALLOWED, ONLY_LIMIT_ORDERS_ALLOWED,
            order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(),
            order.getClOrdId(), order.getAccount()));
        OrderObjectPool.returnObject(order);

        return;
      }

      // update ids
      if (order.getSecurityId() == id) {
        GlobalOrderBook.setOrderIdIfGreater(15, order.getOrderId());
        setSecondaryOrderIdIfGreater(order.getSecondaryOrderId());
      } else {
        LOGGER.info(Constants.LOG_FMT_6, "Invalid securityId. order.getSecurityId(): ", order.getSecurityId(), " bookSecurityId: ", id,
            " clOrdId: ", order.getClOrdId());
      }

      switch (order.getType()) {
        case BUY_LIMIT:
          addBuyLimitCheck(order);
          break;
        case SELL_LIMIT:
          addSellLimitCheck(order);
          break;
        default: {
          order.setRejected(true);
          LOGGER.info(Constants.LOG_FMT_2, "Unhandled order type: ", order.getType());
          // return;
        }
      }

      if (order.isRejected()) {
        // business reject is already sent
        OrderObjectPool.returnObject(order);

        return;
      }

      order.setQuantityOrigLong(order.getQuantityLong()); // use this qty when triggering external order
      order.setQuantityOrigScale(order.getQtyScale());

      double orderQty = MbxMath.scaleDown(order.getQty(), order.getQtyScale());
      // marketMaker Position - total external exchange positions.
      if (Context.isLiquidityImbalanceSettleEnabled()) {
        final double imbalance = LiquiditySubscriptionCache.processImbalance(instrumentPair);
        if (Context.getTestUsers().contains(order.getSubmitterId())) { //only for test users.
          long quantityOrigLong = order.getQuantityOrigLong();
          if (order.getSide() == Side.BUY) {
            double newQty = MbxMath.scaleDown(order.getQty(), order.getQtyScale()) + imbalance;
            LOGGER.info(LOG_FMT_6, "orderQty: ", orderQty, " newQty: ", newQty, " imbalance: ", imbalance);
            if (newQty <= 0) {
              LOGGER.info(LOG_FMT_8, "Imbalance created a negative qty, triggered by ",
                  order.getClOrdId(),
                  " imbalance: ", imbalance, " newQty: ", newQty, " side: ", order.getSide());
              newQty = 0;
            }
            orderQty = newQty;
            quantityOrigLong = MbxMath.changeScale(orderQty, order.getQuantityOrigScale());
          } else {
            double newQty = MbxMath.scaleDown(order.getQty(), order.getQtyScale()) - imbalance;
            LOGGER.info(LOG_FMT_6, "orderQty: ", orderQty, " newQty: ", newQty, " imbalance: ", imbalance);
            if (newQty <= 0) {
              LOGGER.info(LOG_FMT_8, "Imbalance created a negative qty, triggered by ",
                  order.getClOrdId(),
                  " imbalance: ", imbalance, " newQty: ", newQty, " side: ", order.getSide());
              newQty = 0;
            }
            orderQty = newQty;
            quantityOrigLong = MbxMath.changeScale(orderQty, order.getQuantityOrigScale());
          }
          order.setQuantityOrigLong(quantityOrigLong);
        }
      }

      final double orderPrice = MbxMath.scaleDown(order.getPrice(), order.getPriceScale());
      final double orderValue = orderQty * orderPrice;
      if (CMCTop30Checker.withinSmallOrderValue(this.getInstrumentPair().getBase().getSymbol(), orderValue)) {
        LOGGER.info(Constants.LOG_FMT_2, "Submit for small order execution. clOrdId: ", order.getClOrdId());
        matchSmallOrder(order);
        // don't return here. the internal matching logic should continue in the same thread.
      } else {
        LOGGER.info(Constants.LOG_FMT_2, "Submit for external execution. clOrdId: ", order.getClOrdId());
        ExternalExchangeCache.addOrder(order);

        return;
      }
    }

    // executed in the external exchange, match/fill locally
    if ((order.getTargetStrategy() == EXTERNAL || order.getTargetStrategy() == LIQUIDATION) && order.isExecuted()) {
      final long price = order.getPrice2();
      final short priceScale = order.getPrice2Scale();
      final long qty = order.getQuantityOrigLong();
      final short qtyScale = order.getQuantityOrigScale();
      LOGGER.info(LOG_FMT_20, "Liquidity trade executed. clOrdId: ", order.getClOrdId(), " symbol: ", order.getSymbol(), " exPrice: ",
          price, " exPriceScale: ", priceScale, " exQty: ", qty, " exQtyScale: ", qtyScale, " origPrice: ", order.getPrice(),
          " origPriceScale: ", order.getPriceScale(), " origQty: ", order.getQty(), " origQtyScale: ", order.getQty());
      final User marketMaker = UserCache.getMarketMakerUser();
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createExternalExecutionReport(
          NewOrderSingleHandler.getNextOrderId(), marketMaker, order.getSecurityId(), order.getSymbol(), price, priceScale, qty, qtyScale,
          incrementAndGetFilledCountGlobal(), 0, order.getUser().getId(), order.getTargetStrategy(), order.getSide(), order.getFeeAccumulatedQuantity());
      executionReportMessage.setKafkaRecordOffset(order.getKafkaRecordOffset());
      executionReportMessage.setClOrdId(order.getClOrdId());
      executionReportMessage.setFeePositionId((int) order.getAssetId());
      executionReportMessage.setFeePositionQuantity(order.getFeeAccumulatedQuantity());
      executionReportMessage.setSelectId(order.getSelectId());
      if (publishPositions) {
        marketMaker.copySetPositionArr(executionReportMessage);
      }

      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      //reset reused values of the order object
      order.setPrice2(0L,(short) priceScale);
      order.setQuantityOrigLong(order.getQty());
      order.setFeeAccumulatedQuantity(0);
      order.setAssetId(0);
      //order.setSelectId(0);
      // ack - moved to after preordercheck
      if (!order.isOrderModified())
        orderCount++;

      switch (order.getType()) {
        case BUY_LIMIT:
          addBuyLimit(order);
          break;
        case SELL_LIMIT:
          addSellLimit(order);
          break;
        /*
         * case BUY_MARKET: addBuyMarket(order); break; case SELL_MARKET: addSellMarket(order); break; case STOP_BUY_LIMIT:
         * addStopBuyLimit(order); break; case STOP_SELL_LIMIT: addStopSellLimit(order); break;
         */
        default: {
          LOGGER.info(Constants.LOG_FMT_2, "Unhandled order type: ", order.getType());
          // return;
        }
      }

      // convert stable coins to settle USD balance
      if (usdAutoConvertEnabled && order.getTargetStrategy() != AUTO_CONVERT) {
        autoConvertStableCoinsToSettle(order.getUser(), order.getKafkaRecordOffset());
      }
      OrderObjectPool.returnObject(order);

      return;
    }
  }

  private void matchSmallOrder(final Order order) {
    order.setExecuted(true);
    order.setPrice2(order.getPrice2(), order.getPriceScale());
    //order.setQuantityOrigLong(order.getQuantityLong());// already set
    //order.setQuantityOrigScale(order.getQtyScale()); // already set
  }

  // activate CircuitBreaker if price moved
  // during CircuitBreaker, only post only and cancel orders are accepted
  private final void checkCircuitBreaker() {
    try {
      final double indexFeedUsdMark = instrumentPair.getIndexFeedUsdMark();

      // LOGGER.info(LOG_FMT_12, "checkCircuitBreaker1 id=", id, ", indexFeedUsdMark=", indexFeedUsdMark, ", circuitBreakerThreshold=",
      // circuitBreakerThreshold, ", circuitBreakerEndTime=", circuitBreakerEndTime, ", time=",
      // (circuitBreakerEndTime + Context.getCircuitBreakerTimeInterval()), ", now=", System.currentTimeMillis(), ", marketStatus=",
      // marketStatus, ", enabled=", Context.isEnableCircuitBreaker());

      if (MarketStatus.OPEN != marketStatus || !Context.isEnableCircuitBreaker())
        return;

      if (indexFeedUsdMark <= 0 || circuitBreakerThreshold <= 0)
        return;

      // if circuit breaker was recently activated, wait
      if (circuitBreakerEndTime + Context.getCircuitBreakerTimeInterval() > System.currentTimeMillis())
        return;

      final double usdMark = Math.abs(getUsdMark());

      // LOGGER.info(LOG_FMT_2, "checkCircuitBreaker2 id=", id, ", usdMark=", usdMark, ", indexFeedUsdMark=", indexFeedUsdMark);

      if (usdMark > 0 && Math.abs(usdMark - indexFeedUsdMark) > indexFeedUsdMark * circuitBreakerThreshold) {
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn(LOG_FMT_4, "EnableCircuitBreaker3 used, pair=", id, ", getUsdMark()=", usdMark, ", indexFeedUsdMark=",
              indexFeedUsdMark, ", circuitBreakerThreshold=", circuitBreakerThreshold);
        }
        changeState(MarketStatus.CIRCUIT_BREAKER, 0, null);
        circuitBreakerEndTime = System.currentTimeMillis() + Context.getCircuitBreakerTimeInterval();
      } else {
        // LOGGER.info(LOG_FMT_4, "EnableCircuitBreaker4 not used, pair=", id, ", getUsdMark()=", usdMark, ", indexFeedUsdMark=",
        // indexFeedUsdMark, ", circuitBreakerThreshold=", circuitBreakerThreshold, ", circuitBreakerEndTime=", circuitBreakerEndTime);
      }

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  // end CircuitBreaker if time expired
  private final boolean isCircuitBreaker() {
    if (MarketStatus.CIRCUIT_BREAKER != marketStatus)
      return false;

    if (System.currentTimeMillis() > circuitBreakerEndTime) {
      changeState(MarketStatus.OPEN, 0, null);
      return false;
    }
    return true;
  }

  // scan for order given a price level
  private final Order scanForOrder(final CancelOrder cancelOrder, final int priceInt) {
    Order tmpPtr = bookArr[priceInt].getHead();
    while (tmpPtr != null) {
      if (tmpPtr.getUser().getId() == cancelOrder.getUser().getId()) { // same user required

        if (tmpPtr.getSecondaryOrderId() == cancelOrder.getSecondaryOrderId() && cancelOrder.getSecondaryOrderId() > 0
            && cancelOrder.getOrigOrderId() == 0) { // SecondaryOrderId
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_4, "scanForOrder1 used, cancelOrder=", cancelOrder, "tmpPtr=", tmpPtr, "priceInt=", priceInt);
          }
          return tmpPtr;
        }

        if (tmpPtr.getOrderId() == cancelOrder.getOrigOrderId() && cancelOrder.getOrigOrderId() > 0) { // OrderId
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_4, "scanForOrder2 used, cancelOrder=", cancelOrder, "tmpPtr=", tmpPtr, "priceInt=", priceInt);
          }
          return tmpPtr;
        }

      }

      tmpPtr = tmpPtr.getNext();
    }
    return null;
  }

  // scan for order given a price level
  private final Order scanForOrder(final CancelOrder cancelOrder) {
    try {
      // old way of searching for order in orderbook
      final Order order2 = scanForOrder(cancelOrder, 0);
      if (order2 != null)
        return order2;

      int lastPrice = 0;
      if (Side.BUY == cancelOrder.getSide()) {
        for (int i = 0; i < bidLevelCachePtrArr.length; i++) {
          if (bidLevelCachePtrArr[i] == 0)
            break;
          lastPrice = bidLevelCachePtrArr[i];
          final Order order = scanForOrder(cancelOrder, bidLevelCachePtrArr[i]);
          if (order != null)
            return order;
        }
        for (int i = lastPrice; i > 0; i--) {
          final Order order = scanForOrder(cancelOrder, i);
          if (order != null)
            return order;
        }
      } else if (Side.SELL == cancelOrder.getSide()) {
        for (int i = 0; i < askLevelCachePtrArr.length; i++) {
          if (askLevelCachePtrArr[i] == 0)
            break;
          lastPrice = askLevelCachePtrArr[i];
          final Order order = scanForOrder(cancelOrder, askLevelCachePtrArr[i]);
          if (order != null)
            return order;
        }
        for (int i = lastPrice; i < ARR_SIZE; i++) {
          final Order order = scanForOrder(cancelOrder, i);
          if (order != null)
            return order;
        }
      }
    } catch (Exception e) {
      LOGGER.error(LOG_FMT_2, "Error in scanForOrder, cancelOrder=", cancelOrder, e);
    }

    return null;
  }

  @Override
  public final void cancelOrder(final CancelOrder cancelOrder) {
    /*
     * final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckCancelOrderExecutionReport(cancelOrder,
     * instrumentPair); matcherToPublisherQueue.addGuaranteed(executionReportMessage);
     * 
     * if (MarketStatus.CLOSE == marketStatus) {
     * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithCancelId(cancelOrder.getSenderCompId(),
     * MsgType.ORDER_CANCEL_REQUEST, Long.toString(cancelOrder.getCancelId()), BusinessRejectReason.MARKET_IS_CLOSED, MARKET_IS_CLOSED,
     * cancelOrder.getOrigOrderId(), cancelOrder.getSourceSeqNum(), cancelOrder.getSecondaryOrderId(), cancelOrder.getSecurityId(),
     * cancelOrder.getCancelId(), cancelOrder.getSubmitterId())); return; }
     * 
     * 
     * if (cancelOrder.getPriceInt() < 0) { final Order tmpPtr = outOfBoundsOrderMap.get(cancelOrder.getOrigOrderId()); if ((tmpPtr != null)
     * && (tmpPtr.getUser().getId() == cancelOrder.getUser().getId())) { preOrderCheck.updateCancel(tmpPtr);
     * matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelOrder.getCancelId(), tmpPtr,
     * instrumentPair, cancelOrder, cancelOrder.getCancelType())); OrderObjectPool.returnObject(tmpPtr);
     * CancelOrderObjectPool.returnObject(cancelOrder); } else { matcherToPublisherQueue
     * .addGuaranteed(CancelRejectMessage.createCancelReject(cancelOrder, instrumentPair, CxlRejReason.UNKNOWN_ORDER));
     * CancelOrderObjectPool.returnObject(cancelOrder); }
     * 
     * return; }
     * 
     * // try to use open orders cache to lookup order Order order = (cancelOrder.getOrigOrderId() > 0) ?
     * cancelOrder.getUser().lookupOrder(cancelOrder.getOrigOrderId(), cancelOrder.getSecurityId(), cancelOrder.getSide()) :
     * cancelOrder.getUser().lookupOrderBySecondaryOrderId(cancelOrder.getSecondaryOrderId(), cancelOrder.getSecurityId(),
     * cancelOrder.getSide());
     * 
     * 
     * // if unable to lookup order, lookup TWAP special case if (order == null) { order =
     * Context.getTimeTriggerThread().removeOrder(cancelOrder);
     * 
     * if (order != null) {
     * matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelOrder.getCancelId(), order,
     * instrumentPair, cancelOrder, cancelOrder.getCancelType())); OrderObjectPool.returnObject(order);
     * CancelOrderObjectPool.returnObject(cancelOrder); return; } }
     * 
     * // if unable to lookup order, lookup ADL Maker special case if (order == null) { order = adlMakerContainer.removeOrder(cancelOrder);
     * 
     * if (order != null) {
     * matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelOrder.getCancelId(), order,
     * instrumentPair, cancelOrder, cancelOrder.getCancelType())); OrderObjectPool.returnObject(order);
     * CancelOrderObjectPool.returnObject(cancelOrder); return; } }
     * 
     * // if unable to lookup order, attempt to scan for it if (order == null && Context.isScanForOrderEnabled()) order =
     * scanForOrder(cancelOrder);
     * 
     * // if still unable to lookup order, look in out of bounds if (order == null) { order =
     * outOfBoundsOrderMapBySecondaryOrderId.get(cancelOrder.getSecondaryOrderId()); }
     * 
     * if (order != null) { // check that cancel user must be the same user if ((order.getUser() != null) && (order.getUser().getId() !=
     * cancelOrder.getAccount())) { LOGGER.warn(LOG_FMT_4, "user does not match, cancelOrder=", cancelOrder, ORDER_EQ, order);
     * 
     * matcherToPublisherQueue .addGuaranteed(CancelRejectMessage.createCancelReject(cancelOrder, instrumentPair,
     * CxlRejReason.UNKNOWN_ORDER)); CancelOrderObjectPool.returnObject(cancelOrder); return; }
     * 
     * if (cancelOrder.getOrigOrderId() == 0) cancelOrder.setOrigOrderId(order.getOrderId());
     * 
     * // auction if (MarketStatus.OPEN_AUCTION == marketStatus) { boolean rc = false; if (order.getSide() == Side.BUY) rc =
     * auctionContainer.removeBuyLimit(order); else if (order.getSide() == Side.SELL) rc = auctionContainer.removeSellLimit(order);
     * 
     * preOrderCheck.updateCancel(order);
     * matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelOrder.getCancelId(), order,
     * instrumentPair, cancelOrder, cancelOrder.getCancelType())); OrderObjectPool.returnObject(order);
     * CancelOrderObjectPool.returnObject(cancelOrder); return; }
     * 
     * if (order.getSide() == Side.BUY) { if (BUY_LIMIT == order.getType()) removeBuyOrder(order); else if (STOP_BUY_LIMIT ==
     * order.getType()) { if (order.isStopTakeProfit()) stopProfitContainer.removeBuyLimit(order); else if (order.isTrailingStop())
     * trailingStopContainer.removeBuyLimit(order); else if (order.isADLMaker()) adlMakerContainer.removeBuyLimit(order); else
     * stopLimitContainer.removeBuyLimit(order); } // if BUY_MARKET, we don't remove from orderbook } else { if (SELL_LIMIT ==
     * order.getType()) removeSellOrder(order); else if (STOP_SELL_LIMIT == order.getType()) { if (order.isStopTakeProfit())
     * stopProfitContainer.removeSellLimit(order); else if (order.isTrailingStop()) trailingStopContainer.removeSellLimit(order); else if
     * (order.isADLMaker()) adlMakerContainer.removeSellLimit(order); else stopLimitContainer.removeSellLimit(order); } // if SELL_MARKET,
     * we don't remove from orderbook } preOrderCheck.updateCancel(order);
     * matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelOrder.getCancelId(), order,
     * instrumentPair, cancelOrder, cancelOrder.getCancelType())); OrderObjectPool.returnObject(order);
     * CancelOrderObjectPool.returnObject(cancelOrder);
     * 
     * return; }
     * 
     * if (LogLevel.warn()) { LOGGER.warn(LOG_FMT_4, UNKNOWN_ORDER_CANCELORDER_EQ, cancelOrder); }
     * matcherToPublisherQueue.addGuaranteed(CancelRejectMessage.createCancelReject(cancelOrder, instrumentPair,
     * CxlRejReason.UNKNOWN_ORDER)); CancelOrderObjectPool.returnObject(cancelOrder);
     */
  }

  // used to cancel an order from the order itself
  private final void cancelOrder(final Order order) {
    final long cancelPriority = 0;
    final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
    cancelOrder.set(order, order.getOrderId(), cancelPriority);
    cancelOrder(cancelOrder);
  }

  @Override
  public final void uncross() {
    // TODO Auto-generated method stub
  }

  public final void updateLiquidity(final LiquidityResponse.Liquidity liquidity) {
    final int exchangeId = liquidity.getExchangeId();
    final int symbolId = liquidity.getSymbolId();
    ConcurrentHashMap<Integer, LiquidityResponse.Liquidity> symbolLiquidity =
        exchangeSymbolLiquidity.computeIfAbsent(exchangeId, v -> new ConcurrentHashMap<>());
    final LiquidityResponse.Liquidity old = symbolLiquidity.get(symbolId);
    symbolLiquidity.put(symbolId, liquidity);
    if (old != liquidity && old != null) {
      LiquidityObjectPool.returnObject(old);
    }
    // LOGGER.info("Liquidity updated. exchangeId: " + exchangeId + " symbolId: " + symbolId + " pairSymbol: "
    // + instrumentPair.getSymbol() + " liquidity: " + liquidity.toJson(null));
  }

  private final void triggerStopLimitOrders(final int price) {}

  private final void processTriggeredOrders() {

  }

  public final int matchOnAsks(final Order takerOrder) {
    //LOGGER.info(Constants.LOG_FMT_2, "Liquidity matchOnAsks started: ", takerOrder.toJSON());
    User counterpartyUser = UserCache.getMarketMakerUser();
    if (STAKING == takerOrder.getTargetStrategy() || VIRTUAL_TOKEN_SWAP == takerOrder.getTargetStrategy()) {
      counterpartyUser = UserCache.getTokenManager();
    }
    final Order tmpPtr = (Order) newOrderSingleHandler.buildNewLiquidationOrder(counterpartyUser,
        "" + 1_000_000_000 + counterpartyUser.getId(), instrumentPair.getId(), takerOrder.getClOrdId(), takerOrder.getPrice(),
        takerOrder.getPriceScale(), takerOrder.getQuantityLong(), instrumentPair.getQuantityScale(), Side.SELL, OrdType.LIMIT, true);
    tmpPtr.setTargetStrategy(takerOrder.getTargetStrategy());
    final long quantityFilled = tmpPtr.getMatchQuantityLong(takerOrder);
    tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
    takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled);
    filled(quantityFilled, tmpPtr, takerOrder, BUY_LIMIT);
    //LOGGER.info(Constants.LOG_FMT_2, "Liquidity matchOnAsks successful: ", takerOrder.toJSON());

    applyPromoOffer(takerOrder);

    OrderObjectPool.returnObject(tmpPtr);

    return 0;
  }

  public int matchOnAsksMarket(final Order takerOrder) {
    return -1;
    /*
     * int lastPriceLevelVisited = 0;
     * 
     * // match while (askLevelCachePtrArr[0] != 0) { final OrderBookPriceLevel priceLevel = bookArr[askLevelCachePtrArr[0]]; Order tmpPtr =
     * priceLevel.getHead();
     * 
     * if (tmpPtr == null) { LOGGER.warn(LOG_FMT_2, "matchOnAsksMarket head is null, askLevelCachePtrArr[0]=", askLevelCachePtrArr[0]);
     * updateAskLevelCacheAfterOrderRemoval(askLevelCachePtrArr[0]); }
     * 
     * while (tmpPtr != null) { lastPriceLevelVisited = tmpPtr.getPriceInt(); boolean returnObject = false; // cancel if self-trading if
     * (PREVENT_SELF_TRADE && !takerOrder.isLmm() && (takerOrder.getAccount() != 8) && takerOrder.getAccount() == tmpPtr.getAccount()) {
     * LOGGER.warn(LOG_FMT_4, "matchOnAsksMarket cancel self-trading, takerOrder=", takerOrder, ", tmpPtr=", tmpPtr);
     * cancelOrder(takerOrder); return lastPriceLevelVisited; } else if (tmpPtr.getType() == STOP_SELL_LIMIT) { LOGGER.warn(LOG_FMT_2,
     * "matchOnAsksMarket sell stop, tmpPtr=", tmpPtr); } else if (tmpPtr.getType() == STOP_BUY_LIMIT) { LOGGER.warn(LOG_FMT_2,
     * "matchOnAsksMarket buy stop, tmpPtr=", tmpPtr); } else if (takerOrder.getMatchQuantityLong(tmpPtr) <
     * tmpPtr.getMatchQuantityLong(takerOrder)) { final long quantityFilled = takerOrder.getMatchQuantityLong(tmpPtr);
     * tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
     * takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled); filled(quantityFilled, tmpPtr, takerOrder,
     * BUY_MARKET); return lastPriceLevelVisited; } else { final long quantityFilled = tmpPtr.getMatchQuantityLong(takerOrder);
     * tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
     * takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled); filled(quantityFilled, tmpPtr, takerOrder,
     * BUY_MARKET); removeSellOrder(tmpPtr);
     * 
     * if (takerOrder.getMatchQuantityLong(tmpPtr) == 0) { OrderObjectPool.returnObject(tmpPtr); return lastPriceLevelVisited; } else
     * returnObject = true; }
     * 
     * if (returnObject) { final Order next = tmpPtr.getNext(); OrderObjectPool.returnObject(tmpPtr); tmpPtr = next; } else tmpPtr =
     * tmpPtr.getNext(); } } // market sweeped, no liquidity remains if (takerOrder.getQty() > 0) { cancelOrder(takerOrder); } return
     * lastPriceLevelVisited;
     */
  }

  public final boolean isAsksFillOrKill(final Order newPtr) {
    long quantity = 0;

    // traverse cached depth
    int i = 0;
    for (; i < CACHE_DEPTH; i++) {
      if (askLevelCachePtrArr[i] == 0)
        return false;
      Order tmpPtr = bookArr[askLevelCachePtrArr[i]].getHead();
      if (tmpPtr == null || (newPtr.getOrdType() != OrdType.MARKET && tmpPtr.getPriceInt() > newPtr.getPriceInt()))
        return false;

      while (tmpPtr != null) {
        if (newPtr.getOrdType() == OrdType.MARKET || newPtr.getPriceInt() >= tmpPtr.getPriceInt()) {
          quantity += tmpPtr.getQuantityLong();
          if (quantity >= newPtr.getQuantityLong())
            return true;

        } else
          break;
        tmpPtr = tmpPtr.getNext();
      }
    }
    return false;
  }

  public final int matchOnBids(final Order takerOrder) {
    User counterpartyUser = UserCache.getMarketMakerUser();
    if (STAKING == takerOrder.getTargetStrategy() || VIRTUAL_TOKEN_SWAP == takerOrder.getTargetStrategy()) {
      counterpartyUser = UserCache.getTokenManager();
    }
    final Order tmpPtr = (Order) newOrderSingleHandler.buildNewLiquidationOrder(counterpartyUser,
        "" + 1_000_000_000 + counterpartyUser.getId(), instrumentPair.getId(), takerOrder.getClOrdId(), takerOrder.getPrice(),
        takerOrder.getPriceScale(), takerOrder.getQuantityLong(), instrumentPair.getQuantityScale(), Side.BUY, OrdType.LIMIT, true);
    tmpPtr.setTargetStrategy(takerOrder.getTargetStrategy());
    final long quantityFilled = tmpPtr.getMatchQuantityLong(takerOrder);
    tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
    takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled);
    filled(quantityFilled, tmpPtr, takerOrder, SELL_LIMIT);
//    LOGGER.info("Liquidity matchOnBids successful: " + takerOrder.toJSON());
    applyPromoOffer(takerOrder);
    OrderObjectPool.returnObject(tmpPtr);

    return 0;
  }

  /*
   * public final int matchOnBidsMarket(final Order takerOrder) { int lastPriceLevelVisited = 0;
   * 
   * // match while (bidLevelCachePtrArr[0] != 0) { final OrderBookPriceLevel priceLevel = bookArr[bidLevelCachePtrArr[0]]; Order tmpPtr =
   * priceLevel.getHead();
   * 
   * if (tmpPtr == null) { LOGGER.warn(LOG_FMT_2, "matchOnBidsMarket head is null, bidLevelCachePtrArr[0]=", bidLevelCachePtrArr[0]);
   * updateBidLevelCacheAfterOrderRemoval(bidLevelCachePtrArr[0]); }
   * 
   * while (tmpPtr != null) { lastPriceLevelVisited = tmpPtr.getPriceInt(); boolean returnObject = false; // cancel if self-trading if
   * (PREVENT_SELF_TRADE && !takerOrder.isLmm() && (takerOrder.getAccount() != 8) && takerOrder.getAccount() == tmpPtr.getAccount()) {
   * LOGGER.warn(LOG_FMT_4, "matchOnBidsMarket cancel self-trading, takerOrder=", takerOrder, ", tmpPtr=", tmpPtr); cancelOrder(takerOrder);
   * return lastPriceLevelVisited; } else if (tmpPtr.getType() == STOP_BUY_LIMIT) { LOGGER.debug(LOG_FMT_2,
   * "matchOnBidsMarket buy stop, tmpPtr=", tmpPtr); } else if (tmpPtr.getType() == STOP_SELL_LIMIT) { LOGGER.debug(LOG_FMT_2,
   * "matchOnBidsMarket sell stop, tmpPtr=", tmpPtr); } else if (takerOrder.getMatchQuantityLong(tmpPtr) <
   * tmpPtr.getMatchQuantityLong(takerOrder)) { final long quantityFilled = takerOrder.getMatchQuantityLong(tmpPtr);
   * tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
   * takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled); filled(quantityFilled, tmpPtr, takerOrder,
   * SELL_MARKET); return lastPriceLevelVisited; } else { final long quantityFilled = tmpPtr.getMatchQuantityLong(takerOrder);
   * tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
   * takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled); filled(quantityFilled, tmpPtr, takerOrder,
   * SELL_MARKET); removeBuyOrder(tmpPtr);
   * 
   * if (takerOrder.getMatchQuantityLong(tmpPtr) == 0) { OrderObjectPool.returnObject(tmpPtr); return lastPriceLevelVisited; } else
   * returnObject = true; }
   * 
   * if (returnObject) { final Order next = tmpPtr.getNext(); OrderObjectPool.returnObject(tmpPtr); tmpPtr = next; } else tmpPtr =
   * tmpPtr.getNext(); } } // market sweeped, no liquidity remains if (takerOrder.getQty() > 0) { cancelOrder(takerOrder); } return
   * lastPriceLevelVisited; }
   * 
   * public final boolean isBidsFillOrKill(final Order newPtr) { long quantity = 0;
   * 
   * // traverse cached depth int i = 0; for (; i < CACHE_DEPTH; i++) { if (bidLevelCachePtrArr[i] == 0) return false; Order tmpPtr =
   * bookArr[bidLevelCachePtrArr[i]].getHead(); if (tmpPtr == null || (newPtr.getOrdType() != OrdType.MARKET && tmpPtr.getPriceInt() <
   * newPtr.getPriceInt())) return false;
   * 
   * while (tmpPtr != null) { if (newPtr.getOrdType() == OrdType.MARKET || newPtr.getPriceInt() <= tmpPtr.getPriceInt()) { quantity +=
   * tmpPtr.getQuantityLong(); if (quantity >= newPtr.getQuantityLong()) return true;
   * 
   * } else break; tmpPtr = tmpPtr.getNext(); } } return false; }
   */

  private void addBuyLimitCheck(final Order newPtr) {
    if (rebuildInProgress) {
      preOrderCheckOrig.addOrderDuringRebuild(newPtr, newPtr.getPriceInt());
    } else if (!preOrderCheck.checkOrder(newPtr, newPtr.getPriceInt())) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, REJECT_ORDER_EQ, FAILED_PRE_CREDIT_CHECK, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getClOrdId(), newPtr.getAccount()));

      newPtr.setRejected(true);
      return;
    }

  }

  private void addBuyLimit(final Order newPtr) {
    // ack
    if (!rebuildInProgress && publishAcks) {
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
    }

    // match orders
    if (!rebuildInProgress && MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) {
      matchOnAsks(newPtr);
    } else {
      LOGGER.warn(Constants.LOG_FMT_4, "Order not processed. rebuildInProgress: ", rebuildInProgress, " marketStatus: ", marketStatus,
          " order: ", newPtr.toJSON());
    }
  }

  private void addSellLimitCheck(final Order newPtr) {
    if (rebuildInProgress) {
      preOrderCheckOrig.addOrderDuringRebuild(newPtr, newPtr.getPriceInt());
    } else if (!preOrderCheck.checkOrder(newPtr, getRiskPriceForSell(newPtr.getPriceInt()))) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, REJECT_ORDER_EQ, FAILED_PRE_CREDIT_CHECK, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getClOrdId(), newPtr.getAccount()));

      newPtr.setRejected(true);
    }
  }

  private void addSellLimit(final Order newPtr) {
    // ack
    if (!rebuildInProgress && publishAcks) {
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
    }

    // match orders
    if (!rebuildInProgress && MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) {
      matchOnBids(newPtr);
    } else {
      LOGGER.warn(Constants.LOG_FMT_4, "Order not processed. rebuildInProgress: ", rebuildInProgress, " marketStatus: ", marketStatus,
          " order: ", newPtr.toJSON());
    }
  }

  /*
   * public final void addBuyMarket(final Order newPtr) { if (isCircuitBreaker()) { if (LOGGER.isTraceEnabled()) { LOGGER.trace(LOG_FMT_3,
   * REJECT_ORDER_EQ, MARKET_CIRCUIT_BREAKER, ORDER_EQ, newPtr); }
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.CIRCUIT_BREAKER, MARKET_CIRCUIT_BREAKER, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId())); return; }
   * 
   * final int marketOrderRiskPrice = newPtr.getPrice2Int() > 0 ? newPtr.getPrice2Int() : (int) (askLevelCachePtrArr[0] * 1.05);
   * 
   * if ((askLevelCachePtrArr[0] == 0 || !preOrderCheck.checkOrder(newPtr, marketOrderRiskPrice)) && marketStatus !=
   * MarketStatus.OPEN_AUCTION) { // ack if (Context.isAckRejectMessages()) { final ExecutionReportMessage executionReportMessage =
   * ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * matcherToPublisherQueue.addGuaranteed( BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), askLevelCachePtrArr[0] == 0 ? BusinessRejectReason.NO_LIQUIDITY_AVAILABLE :
   * BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, askLevelCachePtrArr[0] == 0 ? NO_LIQUIDITY_AVAILABLE : FAILED_PRE_CREDIT_CHECK,
   * newPtr.getOrderId(), newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId()));
   * 
   * OrderObjectPool.returnObject(newPtr); return; }
   * 
   * // ack if (!rebuildInProgress && publishAcks) { final ExecutionReportMessage executionReportMessage =
   * ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * // auction if (MarketStatus.OPEN_AUCTION == marketStatus) { auctionContainer.addBuyLimit(newPtr); return; }
   * 
   * if (TimeInForce.FILL_OR_KILL == newPtr.getTimeInForce() && !isAsksFillOrKill(newPtr)) { // cancel old order from risk so UserOpenOrders
   * is updated preOrderCheck.updateCancel(newPtr);
   * matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
   * OrderObjectPool.returnObject(newPtr); return; }
   * 
   * // match orders if (MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) { final int lastPriceLevelVisited =
   * matchOnAsksMarket(newPtr); triggerStopLimitOrders(lastPriceLevelVisited); } OrderObjectPool.returnObject(newPtr); }
   * 
   * public final void addSellMarket(final Order newPtr) { if (isCircuitBreaker()) { if (LOGGER.isTraceEnabled()) { LOGGER.trace(LOG_FMT_3,
   * REJECT_ORDER_EQ, MARKET_CIRCUIT_BREAKER, ORDER_EQ, newPtr); }
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.CIRCUIT_BREAKER, MARKET_CIRCUIT_BREAKER, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId())); return; }
   * 
   * final int marketOrderRiskPrice = newPtr.getPrice2Int() > 0 ? newPtr.getPrice2Int() : bidLevelCachePtrArr[0];
   * 
   * if ((bidLevelCachePtrArr[0] == 0 || !preOrderCheck.checkOrder(newPtr, marketOrderRiskPrice)) && marketStatus !=
   * MarketStatus.OPEN_AUCTION) { // ack if (Context.isAckRejectMessages()) { final ExecutionReportMessage executionReportMessage =
   * ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * matcherToPublisherQueue.addGuaranteed( BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), bidLevelCachePtrArr[0] == 0 ? BusinessRejectReason.NO_LIQUIDITY_AVAILABLE :
   * BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, bidLevelCachePtrArr[0] == 0 ? NO_LIQUIDITY_AVAILABLE : FAILED_PRE_CREDIT_CHECK,
   * newPtr.getOrderId(), newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId()));
   * 
   * return; }
   * 
   * // ack if (!rebuildInProgress && publishAcks) { final ExecutionReportMessage executionReportMessage =
   * ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * // auction if (MarketStatus.OPEN_AUCTION == marketStatus) { auctionContainer.addSellLimit(newPtr); return; }
   * 
   * if (TimeInForce.FILL_OR_KILL == newPtr.getTimeInForce() && !isBidsFillOrKill(newPtr)) { // cancel old order from risk so UserOpenOrders
   * is updated preOrderCheck.updateCancel(newPtr);
   * matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
   * OrderObjectPool.returnObject(newPtr); return; }
   * 
   * // match orders if (MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) { final int lastPriceLevelVisited =
   * matchOnBidsMarket(newPtr); triggerStopLimitOrders(lastPriceLevelVisited); } OrderObjectPool.returnObject(newPtr); }
   * 
   * 
   * public final void addStopBuyLimit(final Order newPtr) { if (isCircuitBreaker()) { if (LOGGER.isTraceEnabled()) {
   * LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, MARKET_CIRCUIT_BREAKER, ORDER_EQ, newPtr); }
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.CIRCUIT_BREAKER, MARKET_CIRCUIT_BREAKER, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId())); return; }
   * 
   * if (newPtr.getStopPxInt() <= 0 && !newPtr.isStopTakeProfitMarket() && !newPtr.isTrailingStopMarket()) { if (LOGGER.isDebugEnabled()) {
   * LOGGER.debug(LOG_FMT_4, REJECT_ORDER_EQ, STOP_PRICE_IS_MISSING, ORDER_EQ, newPtr); }
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.STOP_PRICE_IS_MISSING, STOP_PRICE_IS_MISSING, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId())); return; }
   * 
   * if (!rebuildInProgress) {
   * 
   * // reject orders that would be triggered immediately if (Context.isRejectImmediateTriggeredEnabled()) { if (newPtr.isStopTakeProfit())
   * { // stop profit if (last <= newPtr.getPriceInt()) { // ack if (Context.isAckRejectMessages()) { final ExecutionReportMessage
   * executionReportMessage = ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.NOT_AUTHORIZED, ORDER_WOULD_IMMEDIATELY_TRIGGER, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId()));
   * 
   * return; } } else if (newPtr.isTrailingStop()) { // stop profit
   * 
   * } else { // regular stop if (last >= newPtr.getPriceInt()) { // ack if (Context.isAckRejectMessages()) { final ExecutionReportMessage
   * executionReportMessage = ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.NOT_AUTHORIZED, ORDER_WOULD_IMMEDIATELY_TRIGGER, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId())); return; } } }
   * 
   * 
   * if (!preOrderCheck.checkOrder(newPtr, newPtr.getStopPxInt())) { // ack if (Context.isAckRejectMessages()) { final
   * ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.NOT_AUTHORIZED, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId()));
   * 
   * OrderObjectPool.returnObject(newPtr); return; } }
   * 
   * // ack if (!rebuildInProgress) { final ExecutionReportMessage executionReportMessage =
   * ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * if (newPtr.getQuantityLong() <= 0) { OrderObjectPool.returnObject(newPtr); return; }
   * 
   * // add to container if (newPtr.isStopTakeProfit()) stopProfitContainer.addBuyLimit(newPtr); else if (newPtr.isTrailingStop())
   * trailingStopContainer.addBuyLimit(newPtr); else stopLimitContainer.addBuyLimit(newPtr); }
   * 
   * public final void addStopSellLimit(final Order newPtr) { if (isCircuitBreaker()) { if (LOGGER.isTraceEnabled()) {
   * LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, MARKET_CIRCUIT_BREAKER, ORDER_EQ, newPtr); }
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.CIRCUIT_BREAKER, MARKET_CIRCUIT_BREAKER, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId())); return; }
   * 
   * if (newPtr.getStopPxInt() <= 0 && !newPtr.isStopTakeProfitMarket() && !newPtr.isTrailingStopMarket()) { if (LOGGER.isDebugEnabled()) {
   * LOGGER.debug(LOG_FMT_4, REJECT_ORDER_EQ, STOP_PRICE_IS_MISSING, ORDER_EQ, newPtr); }
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.STOP_PRICE_IS_MISSING, STOP_PRICE_IS_MISSING, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId()));
   * 
   * return; }
   * 
   * if (!rebuildInProgress) {
   * 
   * // reject orders that would be triggered immediately if (Context.isRejectImmediateTriggeredEnabled()) { if (newPtr.isStopTakeProfit())
   * { // stop profit if (last >= newPtr.getPriceInt()) { // ack if (Context.isAckRejectMessages()) { final ExecutionReportMessage
   * executionReportMessage = ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, ORDER_WOULD_IMMEDIATELY_TRIGGER, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId())); return; } } else if
   * (newPtr.isTrailingStop()) { // stop profit
   * 
   * } else { // regular stop if ((last != 0) && (last <= newPtr.getPriceInt())) { // ack if (Context.isAckRejectMessages()) { final
   * ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, ORDER_WOULD_IMMEDIATELY_TRIGGER, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId())); return; } } }
   * 
   * 
   * if (!preOrderCheck.checkOrder(newPtr, newPtr.getStopPxInt())) { // ack if (Context.isAckRejectMessages()) { final
   * ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
   * Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
   * newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId()));
   * 
   * OrderObjectPool.returnObject(newPtr); return; } }
   * 
   * // ack if (!rebuildInProgress) { final ExecutionReportMessage executionReportMessage =
   * ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
   * matcherToPublisherQueue.addGuaranteed(executionReportMessage); }
   * 
   * if (newPtr.getQuantityLong() <= 0) { OrderObjectPool.returnObject(newPtr); return; }
   * 
   * // add to container if (newPtr.isStopTakeProfit()) stopProfitContainer.addSellLimit(newPtr); else if (newPtr.isTrailingStop())
   * trailingStopContainer.addSellLimit(newPtr); else stopLimitContainer.addSellLimit(newPtr); }
   */

  /*
   * private final void updateBidLevelCacheAfterOrderRemoval(final int priceLevel) { // if best bid/ask, find and set next bidPtr/askPtr for
   * (int i = 0; i < CACHE_DEPTH; i++) { if (bidLevelCachePtrArr[i] == 0) break; if (bidLevelCachePtrArr[i] == priceLevel) { // if price
   * level is cached, and there are no more orders at that level remove it from // cache if (bookArr[priceLevel].getHead() == null) {
   * bidDepthLevelCacheCount--;
   * 
   * for (int j = i; j < CACHE_DEPTH - 1; j++) { bidLevelCachePtrArr[j] = bidLevelCachePtrArr[j + 1]; if (bidLevelCachePtrArr[j] == 0)
   * break; } bidLevelCachePtrArr[CACHE_DEPTH - 1] = 0; // find the next element that will be the last node of the cache if
   * (bidDepthLevelCacheCount >= CACHE_DEPTH - 1 && bidLevelCachePtrArr[CACHE_DEPTH - 2] != 0) { for (int j =
   * bidLevelCachePtrArr[CACHE_DEPTH - 2] - 1; j >= 0; j--) { if (bookArr[j].getHead() != null) { bidLevelCachePtrArr[CACHE_DEPTH - 1] =
   * bookArr[j].getHead().getPriceInt(); bidDepthLevelCacheCount = CACHE_DEPTH; break; } } } } break; } } // if bidLevelCachePtrArr is
   * empty, attempt to set bidPtr/askPtr if (bidLevelCachePtrArr[0] == 0 && bidDepth > 0) { for (int i = priceLevel - 1; i >= 0; i--) { if
   * (bookArr[i].getHead() != null) { bidLevelCachePtrArr[0] = bookArr[i].getHead().getPriceInt(); bidDepthLevelCacheCount++; break; } } } }
   */

  /*
   * private final void removeBuyOrder(final Order tmpPtr) { if (LOGGER.isDebugEnabled()) LOGGER.debug(LOG_FMT_2, REMOVE_BUY_ORDER_EQ,
   * tmpPtr);
   * 
   * // remove from outOfBoundsOrderMap if (tmpPtr.getPriceInt() >= bookArr.length) { outOfBoundsOrderMap.remove(tmpPtr.getOrderId());
   * outOfBoundsOrderMapBySecondaryOrderId.remove(tmpPtr.getSecondaryOrderId()); return; }
   * 
   * // remove from bookArr bookArr[tmpPtr.getPriceInt()].remove(tmpPtr);
   * 
   * if (OrdType.LIMIT == tmpPtr.getOrdType()) bidDepth--;
   * 
   * // update cache updateBidLevelCacheAfterOrderRemoval(tmpPtr.getPriceInt()); }
   */

  /*
   * private final void updateAskLevelCacheAfterOrderRemoval(final int priceLevel) { for (int i = 0; i < CACHE_DEPTH; i++) { if
   * (askLevelCachePtrArr[i] == 0) break; if (askLevelCachePtrArr[i] == priceLevel) { // if price level is cached, and there are no more
   * orders at that level remove it from // cache if (bookArr[priceLevel].getHead() == null) { askDepthLevelCacheCount--; for (int j = i; j
   * < CACHE_DEPTH - 1; j++) { askLevelCachePtrArr[j] = askLevelCachePtrArr[j + 1]; if (askLevelCachePtrArr[j] == 0) break; }
   * askLevelCachePtrArr[CACHE_DEPTH - 1] = 0; // find the next element that will be the last node of the cache if (askDepthLevelCacheCount
   * >= CACHE_DEPTH - 1 && askLevelCachePtrArr[CACHE_DEPTH - 2] != 0) { for (int j = askLevelCachePtrArr[CACHE_DEPTH - 2] + 1; j <
   * bookArr.length; j++) { if (bookArr[j].getHead() != null) { askLevelCachePtrArr[CACHE_DEPTH - 1] = bookArr[j].getHead().getPriceInt();
   * askDepthLevelCacheCount = CACHE_DEPTH; break; } } } } break; } }
   * 
   * // if askLevelCachePtrArr is empty, attempt to set bidPtr/askPtr if (askLevelCachePtrArr[0] == 0 && askDepth > 0) { for (int i =
   * priceLevel + 1; i < bookArr.length; i++) { if (bookArr[i].getHead() != null) { askLevelCachePtrArr[0] =
   * bookArr[i].getHead().getPriceInt(); askDepthLevelCacheCount++; break; } } } }
   */

  /*
   * private final void removeSellOrder(final Order tmpPtr) { if (LOGGER.isDebugEnabled()) LOGGER.debug(LOG_FMT_2, REMOVE_SELL_ORDER_EQ,
   * tmpPtr);
   * 
   * // remove from outOfBoundsOrderMap if (tmpPtr.getPriceInt() >= bookArr.length) { outOfBoundsOrderMap.remove(tmpPtr.getOrderId());
   * outOfBoundsOrderMapBySecondaryOrderId.remove(tmpPtr.getSecondaryOrderId()); return; }
   * 
   * // remove from bookArr bookArr[tmpPtr.getPriceInt()].remove(tmpPtr);
   * 
   * if (OrdType.LIMIT == tmpPtr.getOrdType()) askDepth--;
   * 
   * // update cache updateAskLevelCacheAfterOrderRemoval(tmpPtr.getPriceInt()); }
   */

  public final void filled(final long quantityFilled, final Order makerOrder, final Order takerOrder, final int orderType) {
    filledCountGlobal++;
    filledCount++;
    final double quotedUsdMark = instrumentPair.getIndexFeedUsdMark();
    final double quotedCoinUsdMark = instrumentPair.getQuoted().getIndexFeedUsdMark();
    final double settleCoinUsdMark = settleCoinUsdMarkInstrument == null ? 1 : settleCoinUsdMarkInstrument.getIndexFeedUsdMark();

    // for debugging, this should never happen
    if (takerOrder == null || takerOrder.getUser() == null || makerOrder == null || makerOrder.getUser() == null) {
      LOGGER.warn(NULL_FOUND, takerOrder, MAKERORDER_EQ, makerOrder, TAKERORDERUSER_EQ, (takerOrder == null ? null : takerOrder.getUser()),
          MAKERORDERUSER_EQ, (makerOrder == null ? null : makerOrder.getUser()));
      throw new NullPointerException(NULL_FOUND + takerOrder + MAKERORDER_EQ + makerOrder + TAKERORDERUSER_EQ
          + (takerOrder == null ? null : takerOrder.getUser()) + MAKERORDERUSER_EQ + (makerOrder == null ? null : makerOrder.getUser()));
    }

    final int takerUserId = takerOrder.getUser().getId();
    final int makerUserId = makerOrder.getUser().getId();

    final ExecutionReportMessage execMaker =
        ExecutionReportMessage.createTradeExecutionReport(makerOrder, instrumentPair, makerOrder.getPrice(), makerOrder.getPriceScale(),
            quantityFilled, instrumentPair.getQuantityScale(), filledCountGlobal, filledCount, takerOrder, takerUserId, false,
            makerOrder.getAssetId(), makerOrder.getTokenId(), makerOrder.getGroupAssetId(), takerOrder.getSelectId());
    final ExecutionReportMessage execTaker =
        ExecutionReportMessage.createTradeExecutionReport(takerOrder, instrumentPair, makerOrder.getPrice(), makerOrder.getPriceScale(),
            quantityFilled, instrumentPair.getQuantityScale(), filledCountGlobal, filledCount, takerOrder, makerUserId, false,
            makerOrder.getAssetId(), makerOrder.getTokenId(), makerOrder.getGroupAssetId(), takerOrder.getSelectId());

    preOrderCheck.updateFill(takerOrder, makerOrder.getPriceInt(), quantityFilled, execTaker, quotedUsdMark, settleCoinUsdMark,
        quotedCoinUsdMark, false, takerOrder, makerUserId);
    preOrderCheck.updateFill(makerOrder, makerOrder.getPriceInt(), quantityFilled, execMaker, quotedUsdMark, settleCoinUsdMark,
        quotedCoinUsdMark, true, takerOrder, takerUserId); // publish second so that any liquidation fees are transfered from taker
    // first

    last = makerOrder.getPriceInt();
    setMark(makerOrder.getPriceInt());
  }

  public final void filled(final long quantityFilled, final Order makerOrder, final Order takerOrder, final int orderType,
      final ExecType execType) {
    filledCountGlobal++;
    filledCount++;
    final double quotedUsdMark = instrumentPair.getIndexFeedUsdMark();
    final double quotedCoinUsdMark = instrumentPair.getQuoted().getIndexFeedUsdMark();
    final double settleCoinUsdMark = settleCoinUsdMarkInstrument.getIndexFeedUsdMark();

    final int takerUserId = takerOrder.getUser().getId();
    final int makerUserId = makerOrder.getUser().getId();

    final ExecutionReportMessage execMaker =
        ExecutionReportMessage.createTradeExecutionReport(makerOrder, instrumentPair, makerOrder.getPrice(), makerOrder.getPriceScale(),
            quantityFilled, instrumentPair.getQuantityScale(), filledCountGlobal, filledCount, takerOrder, takerUserId, false,
            makerOrder.getAssetId(), makerOrder.getTokenId(), makerOrder.getGroupAssetId(), takerOrder.getSelectId());
    final ExecutionReportMessage execTaker =
        ExecutionReportMessage.createTradeExecutionReport(takerOrder, instrumentPair, makerOrder.getPrice(), makerOrder.getPriceScale(),
            quantityFilled, instrumentPair.getQuantityScale(), filledCountGlobal, filledCount, takerOrder, makerUserId, false,
            makerOrder.getAssetId(), makerOrder.getTokenId(), makerOrder.getGroupAssetId(), takerOrder.getSelectId());
    execMaker.setExecType(execType);
    execTaker.setExecType(execType);

    preOrderCheck.updateFill(takerOrder, makerOrder.getPriceInt(), quantityFilled, execTaker, quotedUsdMark, settleCoinUsdMark,
        quotedCoinUsdMark, false, takerOrder, makerUserId);
    preOrderCheck.updateFill(makerOrder, makerOrder.getPriceInt(), quantityFilled, execMaker, quotedUsdMark, settleCoinUsdMark,
        quotedCoinUsdMark, true, takerOrder, takerUserId); // publish second so that any liquidation fees are transfered from taker
    // first

    last = makerOrder.getPriceInt();
    setMark(makerOrder.getPriceInt());
  }

  public final String toString(final int priceLevel) {
    final StringBuilder sb = new StringBuilder();
    Order tmp = bookArr[priceLevel].getHead();
    while (tmp != null) {
      sb.append("-> " + tmp);
      tmp = tmp.getNext();
    }
    return sb.toString();
  }

  @Override
  public final void changeState(final MarketStatus marketStatus, final long snapId, final Message causingMessage) {

    if (MarketStatus.DR_MODE == Context.getMarketStatus()) {
      populateOrderBookFromDRMap();
    }

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
      /*
       * case OPEN_AUCTION: this.marketStatus = marketStatus; if (MarketStatus.DR_MODE != Context.getMarketStatus()) {
       * auctionContainer.openAuction(this); } break;
       */
      /*
       * case CLOSE_AUCTION: this.marketStatus = marketStatus; if (MarketStatus.DR_MODE != Context.getMarketStatus()) {
       * auctionContainer.closeAuction(this); } break;
       */
      /*
       * case CANCEL_AUCTION: this.marketStatus = marketStatus; if (MarketStatus.DR_MODE != Context.getMarketStatus()) {
       * auctionContainer.cancelAuction(this); } this.marketStatus = MarketStatus.OPEN; instrumentPair.setMarketStatus(MarketStatus.OPEN);
       * break;
       */
      default:
        break;
    }
    instrumentPair.setMarketStatus(marketStatus);
  }

  @Override
  public final void expireLiveSessionOrders() {
    /*
     * final long cancelPriority = 0; for (int i = 0; i < bookArr.length; i++) { // this will take a while... Order tmp = null; Order next =
     * null; if (bookArr[i].getHead() != null) { tmp = bookArr[i].getHead(); while (tmp != null) { if (TimeInForce.DAY ==
     * tmp.getTimeInForce()) { next = tmp.getNext(); final long cancelId = tmp.getOrderId(); final CancelOrder cancelOrder =
     * CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(tmp, cancelId, cancelPriority); cancelOrder.setUser(tmp.getUser());
     * cancelOrder(cancelOrder); tmp = next; } else tmp = tmp.getNext(); } } }
     */
  }

  @Override
  public final void restate(final long snapId, final Message causingMessage) {
    /*
     * final ExecRestatementReason reason = ExecRestatementReason.OTHER;
     * 
     * // send initial to indicate reset final Order order = OrderMatchingThreadObjectPool.get();
     * order.setSenderCompId(DEFAULT_SENDER_COMP); order.setClOrdId(ZERO_STR); order.setSecurityId(instrumentPair.getId());
     * order.setPrice(0, (short) 0); order.setQty(0, (short) 0); order.setPriceInt(0); order.setOrdType(OrdType.PREVIOUSLY_INDICATED);
     * order.setSide(Side.BUY); order.setAccount(UserCache.getExchangeUser().getId()); order.setUser(UserCache.getExchangeUser());
     * order.setSnapId(snapId); matcherToPublisherQueue .addGuaranteed(ExecutionReportMessage.createRestateExecutionReport(order,
     * instrumentPair, reason, snapId, causingMessage));
     * 
     * if (bidLevelCachePtrArr.length > 0 && bidLevelCachePtrArr[0] == 0 && askLevelCachePtrArr.length > 0 && askLevelCachePtrArr[0] == 0) {
     * // if order book is empty don't do anything } else if (bidLevelCachePtrArr.length > 0 &&
     * bidLevelCachePtrArr[bidLevelCachePtrArr.length - 1] == 0 && askLevelCachePtrArr.length > 0 &&
     * askLevelCachePtrArr[askLevelCachePtrArr.length - 1] == 0) { // if order book is completely cached, use cache only for (int i =
     * bidLevelCachePtrArr.length - 1; i >= 0; i--) { final int price = bidLevelCachePtrArr[i]; if (price > 0) { Order tmp =
     * bookArr[price].getHead(); if (tmp != null) { while (tmp != null) { matcherToPublisherQueue
     * .add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage)); tmp = tmp.getNext();
     * } } } } for (int i = 0; i < askLevelCachePtrArr.length; i++) { final int price = askLevelCachePtrArr[i]; if (price > 0) { Order tmp =
     * bookArr[price].getHead(); if (tmp != null) { while (tmp != null) { matcherToPublisherQueue
     * .add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage)); tmp = tmp.getNext();
     * } } } } } else { // restate scan all for (int i = 0; i < bookArr.length; i++) { // this will take a while... Order tmp =
     * bookArr[i].getHead(); if (tmp != null) { while (tmp != null) { matcherToPublisherQueue
     * .add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage)); tmp = tmp.getNext();
     * } } } }
     * 
     * // restate stop limit orders final TreeSet<Order> buyTreeSet = stopLimitContainer.getBuyTreeSet(); for (final Order tmp : buyTreeSet)
     * { matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId,
     * causingMessage)); }
     * 
     * final TreeSet<Order> sellTreeSet = stopLimitContainer.getSellTreeSet(); for (final Order tmp : sellTreeSet) {
     * matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId,
     * causingMessage)); }
     * 
     * // restate stop profit limit orders final TreeSet<Order> buyTreeSet2 = stopProfitContainer.getBuyTreeSet(); for (final Order tmp :
     * buyTreeSet2) { matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId,
     * causingMessage)); }
     * 
     * final TreeSet<Order> sellTreeSet2 = stopProfitContainer.getSellTreeSet(); for (final Order tmp : sellTreeSet2) {
     * matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId,
     * causingMessage)); }
     * 
     * // restate auction limit orders final ConcurrentSkipListSet<Order> buyTreeSet3 = auctionContainer.getBuyTreeSet(); for (final Order
     * tmp : buyTreeSet3) { matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason,
     * snapId, causingMessage)); }
     * 
     * final ConcurrentSkipListSet<Order> sellTreeSet3 = auctionContainer.getSellTreeSet(); for (final Order tmp : sellTreeSet3) {
     * matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId,
     * causingMessage)); }
     * 
     * // restate trailingStop orders final TreeSet<Order> buyTreeSet4 = trailingStopContainer.getBuyTreeSet(); for (final Order tmp :
     * buyTreeSet4) { matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId,
     * causingMessage)); }
     * 
     * final TreeSet<Order> sellTreeSet4 = trailingStopContainer.getSellTreeSet(); for (final Order tmp : sellTreeSet4) {
     * matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId,
     * causingMessage)); }
     * 
     * // restate outOfBounds orders final List<Order> outOfBoundsList = new ArrayList<>(outOfBoundsOrderMap.values());
     * Collections.sort(outOfBoundsList, orderComparator); for (final Order tmp : outOfBoundsList) { if (tmp == null) continue;
     * matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId,
     * causingMessage)); }
     */
  }

  private void cancelOrder(final CancelReplaceOrder cancelReplaceOrder) {
    /*
     * final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckCancelOrderExecutionReport(cancelReplaceOrder,
     * instrumentPair); matcherToPublisherQueue.addGuaranteed(executionReportMessage);
     * 
     * if (MarketStatus.CLOSE == marketStatus) {
     * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithCancelReplacelId(
     * cancelReplaceOrder.getSenderCompId(), MsgType.ORDER_CANCEL_REPLACE_REQUEST, Long.toString(cancelReplaceOrder.getCancelId()),
     * BusinessRejectReason.MARKET_IS_CLOSED, MARKET_IS_CLOSED, cancelReplaceOrder.getOrigOrderId(), cancelReplaceOrder.getSourceSeqNum(),
     * cancelReplaceOrder.getSecondaryOrderId(), cancelReplaceOrder.getSecurityId(), cancelReplaceOrder.getCancelId(),
     * cancelReplaceOrder.getNewOrderId(), cancelReplaceOrder.getSubmitterId()));
     * 
     * return; }
     * 
     * if (cancelReplaceOrder.getPriceInt() >= ARR_SIZE) { final Order tmpPtr =
     * outOfBoundsOrderMap.get(cancelReplaceOrder.getOrigOrderId()); if ((tmpPtr != null) && (tmpPtr.getUser().getId() ==
     * cancelReplaceOrder.getUser().getId())) { preOrderCheck.updateCancel(tmpPtr);
     * matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelReplaceOrder.getCancelId(), tmpPtr,
     * instrumentPair, cancelReplaceOrder, CANCEL_ON_REQUEST)); outOfBoundsOrderMap.remove(cancelReplaceOrder.getOrigOrderId());
     * OrderObjectPool.returnObject(tmpPtr); } else { matcherToPublisherQueue
     * .addGuaranteed(CancelRejectMessage.createCancelReject(cancelReplaceOrder, instrumentPair, CxlRejReason.UNKNOWN_ORDER));
     * 
     * if (!cancelReplaceOrder.isForceAddOrder()) { OrderObjectPool.returnObject(cancelReplaceOrder.getOrder());
     * cancelReplaceOrder.setOrder(null); } } return; }
     * 
     * final OrderBookPriceLevel priceLevel = bookArr[cancelReplaceOrder.getPriceInt()]; Order tmpPtr = priceLevel.getHead();
     * 
     * while (tmpPtr != null) { if (LOGGER.isTraceEnabled()) { LOGGER.trace(LOG_FMT_10,
     * "cancelReplaceOrder.getCancelId(), >>>> cancel priceInt=", cancelReplaceOrder.getPriceInt(), ", tmpPtr.getOrderId()=",
     * tmpPtr.getOrderId(), ", cancelReplaceOrder.getOrigOrderId()=", cancelReplaceOrder.getOrigOrderId(), USER_EQ,
     * tmpPtr.getUser().getId(), ", user2=", cancelReplaceOrder.getUser().getId()); } if (((tmpPtr.getOrderId() ==
     * cancelReplaceOrder.getOrigOrderId()) || (cancelReplaceOrder.getOrigOrderId() == 0)) // if ((tmpPtr.getOrderId() ==
     * cancelReplaceOrder.getOrigOrderId()) && (tmpPtr.getUser().getId() == cancelReplaceOrder.getUser().getId())) { if (tmpPtr.getSide() ==
     * Side.BUY) removeBuyOrder(tmpPtr); else removeSellOrder(tmpPtr); preOrderCheck.updateCancel(tmpPtr);
     * matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelReplaceOrder.getCancelId(), tmpPtr,
     * instrumentPair, cancelReplaceOrder, CANCEL_ON_REQUEST)); OrderObjectPool.returnObject(tmpPtr);
     * 
     * return; } tmpPtr = tmpPtr.getNext(); }
     * 
     * matcherToPublisherQueue .addGuaranteed(CancelRejectMessage.createCancelReject(cancelReplaceOrder, instrumentPair,
     * CxlRejReason.UNKNOWN_ORDER)); // if cancelreplace is rejected, remove order if (!cancelReplaceOrder.isForceAddOrder() &&
     * cancelReplaceOrder.getOrigOrderId() != 0) { OrderObjectPool.returnObject(cancelReplaceOrder.getOrder());
     * cancelReplaceOrder.setOrder(null); }
     */
  }

  @Override
  public void cancelReplaceOrder(final CancelReplaceOrder cancelReplaceOrder) {
    if ((cancelReplaceOrder.getOrder() != null)
        && (cancelReplaceOrder.getUser().getId() != cancelReplaceOrder.getOrder().getUser().getId())) {
      matcherToPublisherQueue
          .addGuaranteed(CancelRejectMessage.createCancelReject(cancelReplaceOrder, instrumentPair, CxlRejReason.UNKNOWN_ORDER));
      OrderObjectPool.returnObject(cancelReplaceOrder.getOrder());
      return;
    }

    cancelOrder(cancelReplaceOrder);

    if (cancelReplaceOrder.getOrder() != null)
      addOrder(cancelReplaceOrder.getOrder());
  }

  @Override
  public void massCancelOrder(final MassCancelOrder massCancelOrder) {
    /*
     * if (MarketStatus.CLOSE == marketStatus) {
     * matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithCancelId(massCancelOrder.getSenderCompId(),
     * MsgType.ORDER_CANCEL_REPLACE_REQUEST, Long.toString(massCancelOrder.getCancelId()), BusinessRejectReason.MARKET_IS_CLOSED,
     * MARKET_IS_CLOSED, massCancelOrder.getOrigOrderId(), massCancelOrder.getSourceSeqNum(), massCancelOrder.getSecondaryOrderId(),
     * massCancelOrder.getSecurityId(), massCancelOrder.getCancelId(), massCancelOrder.getSubmitterId()));
     * 
     * return; }
     * 
     * matcherToPublisherQueue.addGuaranteed(massCancelOrder);
     * 
     * final long cancelPriority = 0; int userId = 0;
     * 
     * // for mass cancel for a user, use open order cache instead of scanning orderbooks if (massCancelOrder.getUser() != null) { final
     * Position[] positionArr = massCancelOrder.getUser().getPositionArr(); if (positionArr.length <= id) // if position doesn't exist
     * return return;
     * 
     * final Position position = positionArr[id]; if (LOGGER.isDebugEnabled()) { LOGGER.debug(LOG_FMT_4, ">>orderbook.massCancelOrder1 id=",
     * id, ", position=", position); }
     * 
     * if (position != null) { final UserOpenOrdersByPair userOpenOrdersByPair = position.getUserOpenOrdersByPair(); if
     * (LOGGER.isDebugEnabled()) { LOGGER.debug(LOG_FMT_4, ">>orderbook.massCancelOrder2 id=", id, USEROPENORDERSBYPAIR_EQ,
     * userOpenOrdersByPair); } if (userOpenOrdersByPair != null) { Order[] arr = userOpenOrdersByPair.getBids(); for (int i = arr.length -
     * 1; i >= 0; i--) { final Order order = arr[i]; if (order == null) continue; // ET-1973 // if ((massCancelOrder.getType() ==
     * CANCEL_ON_DISCONNECT) || (massCancelOrder.getType() == CANCEL_ON_LOGOUT)) { // if (order.getTimeInForce() ==
     * TimeInForce.GOOD_TILL_CANCEL) { // continue; // } // } final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
     * cancelOrder.set(order, order.getOrderId(), cancelPriority); cancelOrder.setUser(massCancelOrder.getUser());
     * cancelOrder.setType(massCancelOrder.getType()); cancelOrder.setKafkaRecordOffset(massCancelOrder.getKafkaRecordOffset()); if
     * (LOGGER.isDebugEnabled()) { LOGGER.debug(LOG_FMT_6, ">>orderbook.massCancelOrder3 id=", id, ", order.getOrderId()=",
     * order.getOrderId(), CANCELORDER_EQ, cancelOrder); }
     * 
     * cancelOrder(cancelOrder); } arr = userOpenOrdersByPair.getAsks(); for (int i = arr.length - 1; i >= 0; i--) { final Order order =
     * arr[i]; if (order == null) continue; // ET-1973 // if ((massCancelOrder.getType() == CANCEL_ON_DISCONNECT) ||
     * (massCancelOrder.getType() == CANCEL_ON_LOGOUT)) { // if (order.getTimeInForce() == TimeInForce.GOOD_TILL_CANCEL) { // continue; // }
     * // } final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(order, order.getOrderId(),
     * cancelPriority); cancelOrder.setUser(massCancelOrder.getUser()); cancelOrder.setType(massCancelOrder.getType());
     * cancelOrder.setKafkaRecordOffset(massCancelOrder.getKafkaRecordOffset()); if (LOGGER.isDebugEnabled()) { LOGGER.debug(LOG_FMT_6,
     * ">>orderbook.massCancelOrder4 id=", id, ", order.getOrderId()=", order.getOrderId(), CANCELORDER_EQ, cancelOrder); }
     * cancelOrder(cancelOrder); } } } }
     * 
     * // for mass cancel by submitter, scan the orderbook // only if account wasn't specified if (massCancelOrder.getSubmitterId() != 0 &&
     * massCancelOrder.getAccount() == 0) { final List<Order> orders = new ArrayList<>();
     * 
     * // order book for (int i = 0; i < bookArr.length; i++) { if (bookArr[i] != null) { Order order = bookArr[i].getHead(); while (order
     * != null) { if (order.getSubmitterId() == massCancelOrder.getSubmitterId()) { orders.add(order); } order = order.getNext(); } } }
     * 
     * // out of bound orders for (final Order order : outOfBoundsOrderMap.values()) { if (order.getSubmitterId() ==
     * massCancelOrder.getSubmitterId()) { orders.add(order); } }
     * 
     * // stop limit orders for (final Order order : stopLimitContainer.getBuyTreeSet()) { if (order.getSubmitterId() ==
     * massCancelOrder.getSubmitterId()) { orders.add(order); } } for (final Order order : stopLimitContainer.getSellTreeSet()) { if
     * (order.getSubmitterId() == massCancelOrder.getSubmitterId()) { orders.add(order); } }
     * 
     * // stop profit orders for (final Order order : stopProfitContainer.getBuyTreeSet()) { if (order.getSubmitterId() ==
     * massCancelOrder.getSubmitterId()) { orders.add(order); } } for (final Order order : stopProfitContainer.getSellTreeSet()) { if
     * (order.getSubmitterId() == massCancelOrder.getSubmitterId()) { orders.add(order); } }
     * 
     * // auction orders for (final Order order : auctionContainer.getBuyTreeSet()) { if (order.getSubmitterId() ==
     * massCancelOrder.getSubmitterId()) { orders.add(order); } } for (final Order order : auctionContainer.getSellTreeSet()) { if
     * (order.getSubmitterId() == massCancelOrder.getSubmitterId()) { orders.add(order); } }
     * 
     * // trailing stop orders for (final Order order : trailingStopContainer.getBuyTreeSet()) { if (order.getSubmitterId() ==
     * massCancelOrder.getSubmitterId()) { orders.add(order); } } for (final Order order : trailingStopContainer.getSellTreeSet()) { if
     * (order.getSubmitterId() == massCancelOrder.getSubmitterId()) { orders.add(order); } }
     * 
     * // cancel for (final Order order : orders) { // ET-1973: Stop skipping GTC orders on cancel on disconnect and cancel on logout // if
     * ((massCancelOrder.getType() == CANCEL_ON_DISCONNECT) || (massCancelOrder.getType() == CANCEL_ON_LOGOUT)) { // if
     * (order.getTimeInForce() == TimeInForce.GOOD_TILL_CANCEL) { // continue; // } // }
     * 
     * final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(order, order.getOrderId(), cancelPriority);
     * cancelOrder.setUser(order.getUser()); cancelOrder.setType(massCancelOrder.getType());
     * cancelOrder.setKafkaRecordOffset(massCancelOrder.getKafkaRecordOffset()); if (LOGGER.isInfoEnabled()) { LOGGER.info(LOG_FMT_6,
     * "Mass cancel orders by submitter submitterId=", massCancelOrder.getSubmitterId(), ", orderId=", order.getOrderId(), CANCELORDER_EQ,
     * cancelOrder); } cancelOrder(cancelOrder); } }
     */
  }

  // called from a separate MarketDataOutputBuilderThread thread
  @Override
  public MarketDataSnapshotFullRefreshEncoder build(final MarketDataSnapshotFullRefreshEncoder marketDataSnapshotFullRefreshEncoder) {
    final int[] bidPricesArr = new int[CACHE_DEPTH];
    final long[] bidQuantityArr = new long[CACHE_DEPTH];
    final int[] askPricesArr = new int[CACHE_DEPTH];
    final long[] askQuantityArr = new long[CACHE_DEPTH];
    int bidIndex = 0;
    int askIndex = 0;

    Order temp;
    for (int i = 0; i < CACHE_DEPTH; i++) {
      final int index = bidLevelCachePtrArr[i];
      if (index == 0)
        break;

      temp = bookArr[index].getHead();
      if (temp == null)
        continue;

      if (temp.getType() == STOP_BUY_LIMIT) {
        while (temp != null) {
          temp = temp.getNext();
          if (temp != null && temp.getType() != STOP_BUY_LIMIT)
            break;
        }
        if (temp == null)
          continue;
      }

      bidPricesArr[bidIndex] = temp.getPriceInt();
      if (!temp.isHidden())
        bidQuantityArr[bidIndex] = temp.getQuantityLong();
      temp = temp.getNext();
      while (temp != null) {
        if (!temp.isHidden())
          bidQuantityArr[bidIndex] += temp.getQuantityLong();
        temp = temp.getNext();
      }
      if (bidQuantityArr[bidIndex] > 0)
        bidIndex++;
    }

    for (int i = 0; i < CACHE_DEPTH; i++) {
      final int index = askLevelCachePtrArr[i];
      if (index == 0)
        break;

      temp = bookArr[index].getHead();
      if (temp == null)
        continue;


      if (temp.getType() == STOP_SELL_LIMIT) {
        while (temp != null) {
          temp = temp.getNext();
          if (temp != null && temp.getType() != STOP_SELL_LIMIT)
            break;
        }
        if (temp == null)
          continue;
      }

      askPricesArr[askIndex] = temp.getPriceInt();
      if (!temp.isHidden())
        askQuantityArr[askIndex] = temp.getQuantityLong();
      temp = temp.getNext();
      while (temp != null) {
        if (!temp.isHidden())
          askQuantityArr[askIndex] += temp.getQuantityLong();
        temp = temp.getNext();
      }
      if (askQuantityArr[askIndex] > 0)
        askIndex++;
    }
    if (marketStatus != null)
      marketDataSnapshotFullRefreshEncoder.marketStatus(marketStatus.value());
    marketDataSnapshotFullRefreshEncoder.usdMark(instrumentPair.getIndexFeedUsdMark());
    marketDataSnapshotFullRefreshEncoder.fundingRateTime(instrumentPair.getFundingRateTime());
    marketDataSnapshotFullRefreshEncoder.estFundingRate(instrumentPair.getEstFundingRate());
    /*
     * if (auctionContainer != null) { if (MarketStatus.OPEN_AUCTION == marketStatus) { auctionContainer.calcAuctionPrice(this,
     * Context.getAuctionRecalcTimeInterval()); marketDataSnapshotFullRefreshEncoder
     * .fundingRateTime(instrumentPair.getAuctionLastStartedTime() + instrumentPair.getAuctionDurationTime()); }
     * marketDataSnapshotFullRefreshEncoder.auctionPrice(auctionContainer.getOptimalAskPrice());
     * marketDataSnapshotFullRefreshEncoder.auctionVolume(auctionContainer.getMaxQtyMatchedAtLevel()); }
     */
    // LOGGER.info("Publish MD bidIndex: " + bidIndex + " askIndex: " + askIndex + "==========================================");
    MdEntrieGroupEncoder entry = marketDataSnapshotFullRefreshEncoder.mdEntrieGroupCount(bidIndex + askIndex);
    for (int i = 0; i < bidIndex; i++) {
      entry = entry.next();
      entry.side(Side.BUY);
      entry.price(bidPricesArr[i]);
      entry.priceScale(instrumentPair.getPriceScale());
      entry.quantity(bidQuantityArr[i]);
      entry.quantityScale(instrumentPair.getQuantityScale());
      // LOGGER.info("Side: " + Side.BUY + " price: " + bidPricesArr[i] + " quantity: " + bidQuantityArr[i]);
    }
    for (int i = 0; i < askIndex; i++) {
      entry = entry.next();
      entry.side(Side.SELL);
      entry.price(askPricesArr[i]);
      entry.priceScale(instrumentPair.getPriceScale());
      entry.quantity(askQuantityArr[i]);
      entry.quantityScale(instrumentPair.getQuantityScale());
      // LOGGER.info("Side: " + Side.SELL + " price: " + bidPricesArr[i] + " quantity: " + bidQuantityArr[i]);
    }
    // LOGGER.info("End===========================================================");
    return marketDataSnapshotFullRefreshEncoder;
  }

  @Override
  public void expireSettlePosition(final int markInSettleCoin) {
    final double quotedUsdMark = instrumentPair.getIndexFeedUsdMark();
    final double quotedCoinUsdMark = instrumentPair.getQuoted().getIndexFeedUsdMark();
    final double underlyerCoinUsdMark = instrumentPair.getBase() == null ? 0 : instrumentPair.getBase().getIndexFeedUsdMark();

    final double settleCoinUsdMark = settleCoinUsdMarkInstrument != null ? settleCoinUsdMarkInstrument.getIndexFeedUsdMark() : 1;

    // expire existing orders
    expireAllOrders();

    // for all users close current positions
    for (int userId = 0; userId <= UserCache.getCapacity(); userId++) {
      final User user = UserCache.get(userId);
      if (user == null || !user.isActive() || user.getPositionArr() == null)
        continue;

      final Position position = user.getPositionArr()[id];
      if (position == null || position.getQuantity() == 0)
        continue;

      final int takerUserId = user.getId();
      final long quantityFilled = Math.abs(position.getQuantity());

      int adjMarkInSettleCoin = markInSettleCoin;
      // only in the money options and futures can be physically settled
      if (instrumentPair.isPhysicalSettle() && underlyerCoinUsdMark > 0 && markInSettleCoin > 0) {
        if (AssetType.OPTION_CALL == instrumentPair.getAssetType() || AssetType.OPTION_CALL == instrumentPair.getAssetType()) {
          adjMarkInSettleCoin = 0;
        } else {
          final Position[] positionArr = user.getPositionArr();
          final Position pairPosition = positionArr[id];
          final double usdAvgCostBasisDouble = pairPosition.getUsdAvgCostBasisDouble();
          adjMarkInSettleCoin = (int) (usdAvgCostBasisDouble * instrumentPair.getPriceScaleMultiplier()); // default if not option
        }
      }

      // build order and ack. if long then sell, if short then buy
      final Order orderTaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(user, "" + 1_000_000_000 + user.getId(), id,
          EXPIRESETTLE_STR, adjMarkInSettleCoin, instrumentPair.getPriceScale(), quantityFilled, instrumentPair.getQuantityScale(),
          position.getQuantity() > 0 ? Side.SELL : Side.BUY, OrdType.LIMIT, true);

      // ack order
      ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(orderTaker, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);

      // fill order
      filledCountGlobal++;
      filledCount++;
      orderTaker.setQuantityLong(0); // full quantity is filled
      final ExecutionReportMessage execTaker = ExecutionReportMessage.createTradeExecutionReport(orderTaker, instrumentPair,
          adjMarkInSettleCoin, instrumentPair.getPriceScale(), quantityFilled, instrumentPair.getQuantityScale(), filledCountGlobal,
          filledCount, orderTaker, takerUserId, false, 0, 0, 0, orderTaker.getSelectId());
      execTaker.setExecType(ExecType.CALCULATED); // TODO: should we change to another execType?

      // only in the money options and futures can be physically settled
      if (instrumentPair.isPhysicalSettle() && underlyerCoinUsdMark > 0 && markInSettleCoin > 0)
        preOrderCheck.updateFillPhysicalSettle(orderTaker, adjMarkInSettleCoin, quantityFilled, execTaker, quotedUsdMark, settleCoinUsdMark,
            quotedCoinUsdMark, false, orderTaker, takerUserId, underlyerCoinUsdMark);
      else
        preOrderCheck.updateFill(orderTaker, markInSettleCoin, quantityFilled, execTaker, quotedUsdMark, settleCoinUsdMark,
            quotedCoinUsdMark, false, orderTaker, takerUserId);

      last = markInSettleCoin;
      setMark(markInSettleCoin);
    }
  }

  @Override
  public void updateSecurityDefinition(final InstrumentPair instrumentPair) {
    final SecurityDefinitionAdminMessage message = new SecurityDefinitionAdminMessage(instrumentPair);
    message.setUpdateType(UpdateType.PATCH);
    matcherToPublisherQueue.addGuaranteed(message);
  }

  // sets ExecType.CALCULATED
  // forced trade
  private void matchAutoBuyWithoutOrderBook(final Order order, final User user, final User counterpartyUser, final long quantityFilled,
      final InstrumentPair instrumentPair) {
    // create new orders
    final Order orderTaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(user, "" + 1_000_000_000 + user.getId(),
        instrumentPair.getId(), order.getClOrdId(), order.getPrice(), order.getPriceScale(), quantityFilled,
        instrumentPair.getQuantityScale(), Side.BUY, OrdType.LIMIT, true);
    // preOrderCheck.checkOrderNoValidation(orderTaker, orderTaker.getPriceInt());
    orderTaker.setSourceSeqNum(order.getSourceSeqNum());
    orderTaker.setSourceSendTime(order.getSourceSendTime());
    orderTaker.setKafkaRecordOffset(order.getKafkaRecordOffset());
    orderTaker.setInputTime(order.getInputTime());
    orderTaker.setDecodedTime(order.getDecodedTime());

    final Order orderMaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(counterpartyUser,
        "" + 1_000_000_000 + counterpartyUser.getId(), instrumentPair.getId(), AUTOCLOSE_MAKER_STR, order.getPrice(), order.getPriceScale(),
        quantityFilled, instrumentPair.getQuantityScale(), Side.SELL, OrdType.LIMIT, true);

    if (!preOrderCheck.checkOrder(orderTaker, orderTaker.getPriceInt())) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(orderTaker, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, REJECT_ORDER_EQ, FAILED_PRE_CREDIT_CHECK, ORDER_EQ, orderTaker);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(orderTaker.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(orderTaker.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK,
          orderTaker.getOrderId(), orderTaker.getSourceSeqNum(), orderTaker.getSecondaryOrderId(), orderTaker.getSecurityId(),
          order.getClOrdId(), orderTaker.getAccount()));
      OrderObjectPool.returnObject(orderTaker);
      return;
    }

    // ack
    final ExecutionReportMessage executionReportMessage =
        ExecutionReportMessage.createAckNewOrderExecutionReport(orderTaker, instrumentPair);
    executionReportMessage.setSecurityId(instrumentPair.getId());
    matcherToPublisherQueue.addGuaranteed(executionReportMessage);

    // ack
    final ExecutionReportMessage executionReportMessage2 =
        ExecutionReportMessage.createAckNewOrderExecutionReport(orderMaker, instrumentPair);
    executionReportMessage.setSecurityId(instrumentPair.getId());
    matcherToPublisherQueue.addGuaranteed(executionReportMessage2);

    // match without order book
    LOGGER.info(LOG_FMT_6, "Liquidity order generated quantityFilled=", quantityFilled, ORDERMAKER_EQ, orderMaker, ORDERTAKER_EQ,
        orderTaker);

    orderTaker.setQuantityLong(orderTaker.getQuantityLong() - quantityFilled);
    orderMaker.setQuantityLong(orderMaker.getQuantityLong() - quantityFilled);
    filled(quantityFilled, orderMaker, orderTaker, BUY_LIMIT, ExecType.CALCULATED);

  }

  // sets ExecType.CALCULATED
  // forced trade
  private void matchAutoSellWithoutOrderBook(final Order order, final User user, final User counterpartyUser, final long quantityFilled,
      final InstrumentPair instrumentPair) {
    // create new orders
    final Order orderTaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(user, "" + 1_000_000_000 + user.getId(),
        instrumentPair.getId(), order.getClOrdId(), order.getPrice(), order.getPriceScale(), quantityFilled,
        instrumentPair.getQuantityScale(), Side.SELL, OrdType.LIMIT, true);
    // preOrderCheck.checkOrderNoValidation(orderTaker, orderTaker.getPriceInt());
    orderTaker.setSourceSeqNum(order.getSourceSeqNum());
    orderTaker.setSourceSendTime(order.getSourceSendTime());
    orderTaker.setKafkaRecordOffset(order.getKafkaRecordOffset());
    orderTaker.setInputTime(order.getInputTime());
    orderTaker.setDecodedTime(order.getDecodedTime());

    final Order orderMaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(counterpartyUser,
        "" + 1_000_000_000 + counterpartyUser.getId(), instrumentPair.getId(), AUTOCLOSE_MAKER_STR, order.getPrice(), order.getPriceScale(),
        quantityFilled, instrumentPair.getQuantityScale(), Side.BUY, OrdType.LIMIT, true);

    if (!preOrderCheck.checkOrder(orderTaker, getRiskPriceForSell(orderTaker.getPriceInt()))) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(orderTaker, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, REJECT_ORDER_EQ, FAILED_PRE_CREDIT_CHECK, ORDER_EQ, orderTaker);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(orderTaker.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(orderTaker.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK,
          orderTaker.getOrderId(), orderTaker.getSourceSeqNum(), orderTaker.getSecondaryOrderId(), orderTaker.getSecurityId(),
          orderTaker.getClOrdId(), orderTaker.getAccount()));

      OrderObjectPool.returnObject(orderTaker);
      return;
    }

    // ack
    final ExecutionReportMessage executionReportMessage =
        ExecutionReportMessage.createAckNewOrderExecutionReport(orderTaker, instrumentPair);
    executionReportMessage.setSecurityId(instrumentPair.getId());
    matcherToPublisherQueue.addGuaranteed(executionReportMessage);

    // ack
    final ExecutionReportMessage executionReportMessage2 =
        ExecutionReportMessage.createAckNewOrderExecutionReport(orderMaker, instrumentPair);
    executionReportMessage.setSecurityId(instrumentPair.getId());
    matcherToPublisherQueue.addGuaranteed(executionReportMessage2);

    LOGGER.info(LOG_FMT_6, "Liquidity order generated quantityFilled=", quantityFilled, ORDERMAKER_EQ, orderMaker, ORDERTAKER_EQ,
        orderTaker);

    // match without order book
    orderTaker.setQuantityLong(orderTaker.getQuantityLong() - quantityFilled);
    orderMaker.setQuantityLong(orderMaker.getQuantityLong() - quantityFilled);
    filled(quantityFilled, orderMaker, orderTaker, SELL_LIMIT, ExecType.CALCULATED);

    // preOrderCheck.updateCancelNoValidation(orderTaker);
  }


  /****
   * Autodeleveraging, Margin call code below
   */

  // sets ExecType.CALCULATED
  // forced trade
  private Order matchAutoBuyWithoutOrderBook(final LiquidationOrder liquidationOrder, final User user, final User counterpartyUser,
      final Position[] positionArr, final long quantityFilled) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, "liquidationBuyToCloseOrder matchAutoBuyWithoutOrderBook, liquidationOrder=", liquidationOrder,
          QUANTITYFILLED_EQ, counterpartyUser, QUANTITYFILLED_EQ, quantityFilled);
    }

    // create new orders
    Order orderTaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(user, "" + 1_000_000_000 + user.getId(), id, AUTOCLOSE_STR,
        liquidationOrder.getPrice(), liquidationOrder.getPriceScale(), quantityFilled, instrumentPair.getQuantityScale(), Side.BUY,
        OrdType.LIMIT, true);
    // preOrderCheck.checkOrderNoValidation(orderTaker, orderTaker.getPriceInt());

    Order orderMaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(counterpartyUser,
        "" + 1_000_000_000 + counterpartyUser.getId(), id, AUTOCLOSE_MAKER_STR, liquidationOrder.getPrice(),
        liquidationOrder.getPriceScale(), quantityFilled, instrumentPair.getQuantityScale(), Side.SELL, OrdType.LIMIT, true);

    // ack
    ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(orderTaker, instrumentPair);
    matcherToPublisherQueue.addGuaranteed(executionReportMessage);

    // ack
    ExecutionReportMessage executionReportMessage2 = ExecutionReportMessage.createAckNewOrderExecutionReport(orderMaker, instrumentPair);
    matcherToPublisherQueue.addGuaranteed(executionReportMessage2);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_14, "liquidationBuyToCloseOrder matchAutoBuyWithoutOrderBook, liquidationOrder=", liquidationOrder,
          QUANTITYFILLED_EQ, counterpartyUser, QUANTITYFILLED_EQ, quantityFilled, ORDERTAKER_EQ, orderTaker, ORDERMAKER_EQ, orderMaker,
          ", ack1=", executionReportMessage, ", ack2=", executionReportMessage2);
    }

    // match without order book
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, "liquidationBuyToCloseOrder calculated quantityFilled=", quantityFilled, ORDERMAKER_EQ, orderMaker,
          ORDERTAKER_EQ, orderTaker);
    }
    orderTaker.setQuantityLong(orderTaker.getQuantityLong() - quantityFilled);
    orderMaker.setQuantityLong(orderMaker.getQuantityLong() - quantityFilled);
    filled(quantityFilled, orderMaker, orderTaker, BUY_LIMIT, ExecType.CALCULATED);

    // preOrderCheck.updateCancelNoValidation(orderTaker);
    return orderTaker;
  }

  // sets ExecType.CALCULATED
  // forced trade
  private Order matchAutoSellWithoutOrderBook(final LiquidationOrder liquidationOrder, final User user, final User counterpartyUser,
      final Position[] positionArr, final long quantityFilled) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, "liquidationSellToCloseOrder matchAutoSellWithoutOrderBook, liquidationOrder=", liquidationOrder,
          QUANTITYFILLED_EQ, counterpartyUser, QUANTITYFILLED_EQ, quantityFilled);
    }

    // create new orders
    Order orderTaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(user, "" + 1_000_000_000 + user.getId(), id, AUTOCLOSE_STR,
        liquidationOrder.getPrice(), liquidationOrder.getPriceScale(), quantityFilled, instrumentPair.getQuantityScale(), Side.SELL,
        OrdType.LIMIT, true);
    // preOrderCheck.checkOrderNoValidation(orderTaker, orderTaker.getPriceInt());

    Order orderMaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(counterpartyUser,
        "" + 1_000_000_000 + counterpartyUser.getId(), id, AUTOCLOSE_MAKER_STR, liquidationOrder.getPrice(),
        liquidationOrder.getPriceScale(), quantityFilled, instrumentPair.getQuantityScale(), Side.BUY, OrdType.LIMIT, true);

    // ack
    ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(orderTaker, instrumentPair);
    matcherToPublisherQueue.addGuaranteed(executionReportMessage);

    // ack
    ExecutionReportMessage executionReportMessage2 = ExecutionReportMessage.createAckNewOrderExecutionReport(orderMaker, instrumentPair);
    matcherToPublisherQueue.addGuaranteed(executionReportMessage2);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, "liquidationSellToCloseOrder matchAutoSellWithoutOrderBook, liquidationOrder=", liquidationOrder,
          QUANTITYFILLED_EQ, counterpartyUser, QUANTITYFILLED_EQ, quantityFilled, ORDERTAKER_EQ, orderTaker, ORDERMAKER_EQ, orderMaker,
          ", ack1=", executionReportMessage, ", ack2=", executionReportMessage2);
    }

    // match without order book
    orderTaker.setQuantityLong(orderTaker.getQuantityLong() - quantityFilled);
    orderMaker.setQuantityLong(orderMaker.getQuantityLong() - quantityFilled);
    filled(quantityFilled, orderMaker, orderTaker, SELL_LIMIT, ExecType.CALCULATED);

    // preOrderCheck.updateCancelNoValidation(orderTaker);
    return orderTaker;
  }

  // calc max new notional position that the insurance can take
  private final long calcMaxNewInsuranceQty(final LiquidationOrder liquidationOrder) {
    try {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_10, "calcMaxNewInsuranceQty InsuranceState.isUseInsurance()=", InsuranceState.isUseInsurance(),
            LIQUIDATIONORDER_EQ, liquidationOrder);
      }
      if (!InsuranceState.isUseInsurance())
        return 0;

      final User insuranceUser = InsuranceState.getUser();
      if (insuranceUser == null)
        return 0;

      preOrderCheck.updateRisk(insuranceUser, null);
      final double notionalPositionValue = insuranceUser.getUsdNotionalPositionValue();
      final double usdValue = insuranceUser.getUsdValue();
      final double maxPosition = InsuranceState.getInsurancePositonPercentLimit() * .01 * usdValue;
      final double maxNotionalNewInsurancePosition = maxPosition - notionalPositionValue;
      if (maxNotionalNewInsurancePosition <= 0)
        return 0;

      // calc qty by dividing by notional
      final double priceUsd = liquidationOrder.getPriceInt() * instrumentPair.getPriceScaleFactor();
      if (priceUsd > 0) {
        double insuranceMaxQty = maxNotionalNewInsurancePosition / priceUsd;
        if (instrumentPair.getQuantityScaleFactor() > 0)
          insuranceMaxQty = insuranceMaxQty / instrumentPair.getQuantityScaleFactor();
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_10, "calcMaxNewInsuranceQty insuranceMaxQty=", insuranceMaxQty, ", notionalPositionValue+",
              notionalPositionValue, ", maxNotionalNewInsurancePosition+", maxNotionalNewInsurancePosition, ", priceUsd=", priceUsd,
              LIQUIDATIONORDER_EQ, liquidationOrder);
        }
        // min of insuranceMaxQty and liquidationOrder.getQuantityLong()
        return Math.min((long) insuranceMaxQty, liquidationOrder.getQuantityLong());
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return 0;
  }

  // returns true if closed, false if position remains
  // will matchAutoBuyWithoutOrderBook
  public final boolean liquidationToCloseUsingADLMaker(final LiquidationOrder liquidationOrder, final User user) {
    /*
     * try { final ArrayList<Order> triggeredOrdersList = new ArrayList<>(); int priceInt = liquidationOrder.getPriceInt(); if (priceInt ==
     * 0) priceInt = (int) instrumentPair.adjustPriceToScale(liquidationOrder.getPrice(), liquidationOrder.getPriceScale()); if (Side.BUY ==
     * liquidationOrder.getSide()) adlMakerContainer.getTriggeredSellLimitList(priceInt, triggeredOrdersList); else
     * adlMakerContainer.getTriggeredBuyLimitList(priceInt, triggeredOrdersList);
     * 
     * for (final Order orderMaker : triggeredOrdersList) { // prevent self-trade if (orderMaker.getAccount() ==
     * liquidationOrder.getAccount()) continue;
     * 
     * final long quantityFilled = Math.min(liquidationOrder.getQuantityLong(), orderMaker.getQuantityLong());
     * 
     * if (quantityFilled > 0) { // match without order book if (LOGGER.isInfoEnabled()) { LOGGER.info(LOG_FMT_6,
     * "liquidationToCloseUsingADLMaker calculated quantityFilled=", quantityFilled, ORDERMAKER_EQ, orderMaker, ORDERTAKER_EQ,
     * liquidationOrder); } liquidationOrder.setQuantityLong(liquidationOrder.getQuantityLong() - quantityFilled);
     * orderMaker.setQuantityLong(orderMaker.getQuantityLong() - quantityFilled); filled(quantityFilled, orderMaker, liquidationOrder,
     * BUY_LIMIT, ExecType.CALCULATED); // liquidationOrder.setQuantityLong(liquidationOrder.getQuantityLong() - quantityFilled); }
     * 
     * // remove counterOrder if fully filled if (orderMaker.getQuantityLong() <= 0) { if (Side.BUY == liquidationOrder.getSide())
     * adlMakerContainer.removeSellLimit(orderMaker); else adlMakerContainer.removeBuyLimit(orderMaker); }
     * 
     * // if liquidated, done if (liquidationOrder.getQuantityLong() <= 0) { isMarginLiquidationSatisfied(user, liquidationOrder); // this
     * needs to be called to reset the userstate return true; } } } catch (Throwable e) { LOGGER.error(ERROR_LOG, e); } if
     * (LOGGER.isInfoEnabled()) { LOGGER.info(LOG_FMT_2, "liquidationToCloseUsingADLMaker sweeped=", liquidationOrder, USER_EQ, user); }
     */
    return false;
  }

  // returns true if closed, false if position remains
  private final boolean liquidationBuyToCloseUsingInsurance(final LiquidationOrder liquidationOrder, final User user,
      final Position[] positionArr) {
    // insurance may take position transfers
    long insuranceQty = calcMaxNewInsuranceQty(liquidationOrder);
    if (insuranceQty > 0) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_2, "liquidationBuyToCloseOrder close counterparties=", liquidationOrder);
      }

      if (insuranceQty >= liquidationOrder.getQuantityLong()) { // counter party has enough of a position
        long quantityFilled = liquidationOrder.getQuantityLong();
        liquidationOrder.setQuantityLong(0);

        matchAutoBuyWithoutOrderBook(liquidationOrder, user, InsuranceState.getUser(), positionArr, quantityFilled);

        // position is closed
        return true;
      } else { // user position larger than insurance
        liquidationOrder.setQuantityLong(liquidationOrder.getQuantityLong() - insuranceQty);

        matchAutoBuyWithoutOrderBook(liquidationOrder, user, InsuranceState.getUser(), positionArr, insuranceQty);
      }
    }
    return false;
  }

  private final void liquidationBuyToCloseOrder(final LiquidationOrder liquidationOrder, final User user, final Position[] positionArr) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "liquidationBuyToCloseOrder=", liquidationOrder, USER_EQ, user);
    }

    // set liquidation fee tier if usd value below tier threshold
    if (user.getUsdValue() < NotionalMarginCalc.LOWEST_TIER_THRESHOLD)
      user.setFeeTier(Fee.LIQUIDATION_FEE_ID);

    // use adlMakerContainer to take position transfers
    if (liquidationToCloseUsingADLMaker(liquidationOrder, user)) {
      // balanceTransferRemainingCollateral(user);
      return;
    }
    // recalc MarginRatio, if below 1 stop liquidating and eliminate order
    if (isMarginLiquidationSatisfied(user, liquidationOrder))
      return;

    // sweep to breakeven price
    final int lastPriceLevelVisited = matchOnAsks(liquidationOrder);
    triggerStopLimitOrders(lastPriceLevelVisited);
    processTriggeredOrders();

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "liquidationBuyToCloseOrder sweeped=", liquidationOrder, USER_EQ, user);
    }

    // recalc MarginRatio, if below 1 stop liquidating and eliminate order
    if (isMarginLiquidationSatisfied(user, liquidationOrder))
      return;

    // if quantity remaining close counterparties
    // user considered bankrupt, liquidation fee tier is set
    if (liquidationOrder.getQuantityLong() > 0) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_4, "liquidationBuyToCloseOrder close counterparties=", liquidationOrder, USER_EQ, user);
      }

      // liquidation fee tier
      user.setFeeTier(Fee.LIQUIDATION_FEE_ID);

      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(liquidationOrder, instrumentPair));

      // insurance may take position transfers
      if (liquidationBuyToCloseUsingInsurance(liquidationOrder, user, positionArr)) {
        balanceTransferRemainingCollateral(user);
        return;
      }

      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_4, "liquidationBuyToCloseOrder insurance not remaining, closing counterparties=", liquidationOrder, USER_EQ,
            user);
      }

      // close counterparties
      for (int bucket = UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_10; bucket >= UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1; bucket--) {
        ConcurrentHashMap<Integer, User> map = UserRiskCache.getIndex(instrumentPair.getId(), 0, bucket);
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_6, "liquidationBuyToCloseOrder map bucket=", bucket, INSTR_EQ, instrumentPair.getId(), MAP_EQ,
              map != null ? map.toString() : "");
        }
        if (map != null) {
          for (User counterpartyUser : map.values()) {
            if (counterpartyUser == null || user.getId() == counterpartyUser.getId())
              continue;
            Position[] counterpartyPositionArr = counterpartyUser.getPositionArr();
            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_4, "liquidationBuyToCloseOrder counterpartyPositionArr=", Arrays.toString(counterpartyPositionArr),
                  USER_EQ, user);
            }

            if (counterpartyPositionArr == null)
              continue;
            Position counterpartyPosition = counterpartyPositionArr[liquidationOrder.getSecurityId()];
            if (counterpartyPosition == null || counterpartyPosition.getQuantity() <= 0) // looking for long positions of counterparty
              continue;

            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_4, "liquidationBuyToCloseOrder bucket=", bucket, FOUND_COUNTERPARTYPOSITION_EQ, counterpartyPosition,
                  USER_EQ, user);
            }

            long counterpartyQuantityAbs = Math.abs(counterpartyPosition.getQuantity());
            if (counterpartyQuantityAbs >= liquidationOrder.getQuantityLong()) { // counter party has enough of a position
              long quantityFilled = liquidationOrder.getQuantityLong();
              liquidationOrder.setQuantityLong(0);

              matchAutoBuyWithoutOrderBook(liquidationOrder, user, counterpartyUser, positionArr, quantityFilled);

              // position is closed
              // user.getAutoLiquidationState().set(0); // need to wait for other positions to be closed!
              balanceTransferRemainingCollateral(user);
              return;
            } else { // user position larger than counterparty
              long quantityFilled = counterpartyQuantityAbs;
              liquidationOrder.setQuantityLong(liquidationOrder.getQuantityLong() - quantityFilled);

              matchAutoBuyWithoutOrderBook(liquidationOrder, user, counterpartyUser, positionArr, quantityFilled);
            }

          }
        }
      }

      // position still remains after iterating through counterparties
      if (liquidationOrder.getQuantityLong() > 0) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_4, "liquidationBuyToCloseOrder, no counterparty remainder=", liquidationOrder, USER_EQ, user);
        }

        long quantityFilled = liquidationOrder.getQuantityLong();
        liquidationOrder.setQuantityLong(0);

        User counterpartyUser = UserCache.getInsuranceFundUser(); // force bot to be counterparty
        matchAutoBuyWithoutOrderBook(liquidationOrder, user, counterpartyUser, positionArr, quantityFilled);

        balanceTransferRemainingCollateral(user);
      }
    }

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "liquidationBuyToCloseOrder, returning remainder=", liquidationOrder, USER_EQ, user);
    }
  }

  // returns true if closed, false if position remains
  private final boolean liquidationSellToCloseUsingInsurance(final LiquidationOrder liquidationOrder, final User user,
      final Position[] positionArr) {
    long insuranceQty = calcMaxNewInsuranceQty(liquidationOrder);
    if (insuranceQty > 0) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_6, "liquidationSellToCloseOrder insuranceQty=", insuranceQty, LIQUIDATIONORDER_EQ, liquidationOrder, USER_EQ,
            user);
      }

      if (insuranceQty >= liquidationOrder.getQuantityLong()) { // counter party has enough of a position
        long quantityFilled = liquidationOrder.getQuantityLong();
        liquidationOrder.setQuantityLong(0);

        matchAutoSellWithoutOrderBook(liquidationOrder, user, InsuranceState.getUser(), positionArr, quantityFilled);

        // position is closed
        return true;
      } else { // user position larger than insurance
        liquidationOrder.setQuantityLong(liquidationOrder.getQuantityLong() - insuranceQty);

        matchAutoSellWithoutOrderBook(liquidationOrder, user, InsuranceState.getUser(), positionArr, insuranceQty);
      }
    }
    return false;
  }

  private void liquidationSellToCloseOrder(final LiquidationOrder liquidationOrder, final User user, final Position[] positionArr) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "liquidationSellToCloseOrder=", liquidationOrder, USER_EQ, user);
    }

    // set liquidation fee tier if usd value below tier threshold
    if (user.getUsdValue() < NotionalMarginCalc.LOWEST_TIER_THRESHOLD)
      user.setFeeTier(Fee.LIQUIDATION_FEE_ID);

    // use adlMakerContainer to take position transfers
    if (liquidationToCloseUsingADLMaker(liquidationOrder, user)) {
      // balanceTransferRemainingCollateral(user);
      return;
    }

    // recalc MarginRatio, if below 1 stop liquidating and eliminate order
    if (isMarginLiquidationSatisfied(user, liquidationOrder))
      return;

    // sweep to breakeven price
    final int lastPriceLevelVisited = matchOnBids(liquidationOrder);
    triggerStopLimitOrders(lastPriceLevelVisited);
    processTriggeredOrders();

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "liquidationSellToCloseOrder sweeped=", liquidationOrder, USER_EQ, user);
    }

    // recalc MarginRatio, if below 1 stop liquidating and eliminate order
    if (isMarginLiquidationSatisfied(user, liquidationOrder))
      return;

    // if quantity remaining close counterparties
    // user considered bankrupt, liquidation fee tier is set
    if (liquidationOrder.getQuantityLong() > 0) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_4, "liquidationSellToCloseOrder close counterparties=", liquidationOrder, USER_EQ, user);
      }

      // liquidation fee tier
      user.setFeeTier(Fee.LIQUIDATION_FEE_ID);

      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(liquidationOrder, instrumentPair));

      // insurance may take position transfers
      if (liquidationSellToCloseUsingInsurance(liquidationOrder, user, positionArr)) {
        balanceTransferRemainingCollateral(user);
        return;
      }

      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_4, "liquidationSellToCloseOrder insurance not remaining, closing counterparties=", liquidationOrder, USER_EQ,
            user);
      }

      // close counterparties
      for (int bucket = UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_10; bucket >= UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1; bucket--) {
        ConcurrentHashMap<Integer, User> map = UserRiskCache.getIndex(instrumentPair.getId(), 1, bucket);
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_6, "liquidationSellToCloseOrder map bucket=", bucket, INSTR_EQ, instrumentPair.getId(), MAP_EQ,
              map != null ? map.toString() : "");
        }
        if (map != null) {
          for (User counterpartyUser : map.values()) {
            if (counterpartyUser == null || user.getId() == counterpartyUser.getId())
              continue;
            Position[] counterpartyPositionArr = counterpartyUser.getPositionArr();
            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_8, "liquidationSellToCloseOrder counterpartyPositionArr=", Arrays.toString(counterpartyPositionArr),
                  QUANTITYFILLED_EQ, counterpartyUser, "counterparties=", liquidationOrder, USER_EQ, user);
            }

            if (counterpartyPositionArr == null)
              continue;
            Position counterpartyPosition = counterpartyPositionArr[liquidationOrder.getSecurityId()];
            if (counterpartyPosition == null || counterpartyPosition.getQuantity() >= 0) // looking for short positions of counterparty
              continue;

            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_6, "liquidationSellToCloseOrder bucket=", bucket, FOUND_COUNTERPARTYPOSITION_EQ, counterpartyPosition,
                  QUANTITYFILLED_EQ, counterpartyUser);
            }

            long counterpartyQuantityAbs = Math.abs(counterpartyPosition.getQuantity());
            if (counterpartyQuantityAbs >= liquidationOrder.getQuantityLong()) { // counter party has enough of a position
              long quantityFilled = liquidationOrder.getQuantityLong();
              liquidationOrder.setQuantityLong(0);

              matchAutoSellWithoutOrderBook(liquidationOrder, user, counterpartyUser, positionArr, quantityFilled);

              // position is closed
              balanceTransferRemainingCollateral(user);
              return;
            } else { // user position larger than counterparty
              long quantityFilled = counterpartyQuantityAbs;
              liquidationOrder.setQuantityLong(liquidationOrder.getQuantityLong() - quantityFilled);

              matchAutoSellWithoutOrderBook(liquidationOrder, user, counterpartyUser, positionArr, quantityFilled);
            }


          }
        }
      }

      // position still remains after iterating through counterparties
      if (liquidationOrder.getQuantityLong() > 0) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_4, "liquidationSellToCloseOrder, no counterparty remainder=", liquidationOrder, USER_EQ, user);
        }
        long quantityFilled = liquidationOrder.getQuantityLong();
        liquidationOrder.setQuantityLong(0);

        User counterpartyUser = UserCache.getInsuranceFundUser(); // force bot to be counterparty
        matchAutoSellWithoutOrderBook(liquidationOrder, user, counterpartyUser, positionArr, quantityFilled);

        balanceTransferRemainingCollateral(user);
      }

    }
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "liquidationSellToCloseOrder, returning remainder=", liquidationOrder, USER_EQ, user);
    }
  }

  // recalc MarginRatio, if below 1 stop liquidating and eliminate order
  private final boolean isMarginLiquidationSatisfied(final User user, final LiquidationOrder liquidationOrder) {
    preOrderCheck.updateRisk(user, null);
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_8, ISMARGINLIQUIDATIONSATISFIED_MARGINRATIO_EQ, user.getMarginRatio(), USER_GETUSDVALUE_EQ, user.getUsdValue(),
          LIQUIDATIONORDER_EQ, liquidationOrder, USER_EQ, user);
    }

    // MarginLiquidationSatisfiedThreshold defaults to .95
    if (user.getMarginRatio() < Context.getMarginLiquidationSatisfiedThreshold() && user.getUsdValue() >= 0
        && (user.getUsdMarginableValue() >= user.getUsdMarginMaintValue() * Context.getMarginLiquidationSatisfiedThreshold())) {

      if (liquidationOrder != null && liquidationOrder.getQuantityLong() > 0)
        matcherToPublisherQueue
            .addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(liquidationOrder, instrumentPair));

      user.setFeeTier(user.getFeeTierOrig());
      user.getAutoLiquidationCounter().set(0);
      user.getAutoLiquidationState().set(0);

      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_8, ISMARGINLIQUIDATIONSATISFIED2_MARGINRATIO_EQ, user.getMarginRatio(), USER_GETUSDVALUE_EQ, user.getUsdValue(),
            LIQUIDATIONORDER_EQ, liquidationOrder, USER_EQ, user);
      }
      return true;
    }
    return false;
  }

  // onCollateralSwapOrder
  // is always a SELL, reduce only, IOC order
  @Override
  public LiquidationOrder onCollateralSwapOrder(final User user, final long price, final short price_scale, final long qty,
      final short qty_scale, final Side side) {
    final Message message =
        newOrderSingleHandler.buildNewCollateralSwapLiquidationOrder(user, id, price, price_scale, qty, qty_scale, side);
    final LiquidationOrder order = (LiquidationOrder) message;

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "onCollateralSwapOrder=", order, USER_EQ, user);
    }

    // set liquidation fee tier if usd value below tier threshold
    // if (user.getUsdValue() < NotionalMarginCalc.LOWEST_TIER_THRESHOLD)
    user.setFeeTier(Fee.LIQUIDATION_FEE_ID);

    final Position settlePosition = user.getPosition((CollateralSwapMessage.getSettleCoinUsdMarkInstrument().getId())); // USD/USDC
    final Position btcPosition = user.getPosition((CollateralSwapMessage.getBtcCoinUsdMarkInstrument().getId()));

    final long settleAvailableQuantity = settlePosition.getAvailableQuantity();
    final long settleQuantity = settlePosition.getQuantity();
    final long btcAvailableQuantity = btcPosition.getAvailableQuantity();
    final long btcQuantity = btcPosition.getQuantity();

    // ack order
    final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(order, instrumentPair);
    matcherToPublisherQueue.addGuaranteed(executionReportMessage);

    // update available fee
    preOrderCheck.checkOrderNoValidation(order, (int) instrumentPair.adjustPriceToScale(price, price_scale));

    // sweep to breakeven price
    final int lastPriceLevelVisited = matchOnBids(order);
    triggerStopLimitOrders(lastPriceLevelVisited);
    processTriggeredOrders();

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "onCollateralSwapOrder2=", order, USER_EQ, user);
    }

    // immediate or cancel
    if (order.getQuantityLong() > 0) {
      // preOrderCheck.updateCancelNoValidation(order);
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(order, instrumentPair));

      // use insuranceUser as counterparty
      if (InsuranceState.getUser() != null) {
        // force trade
        order.setQty(order.getQuantityLong(), instrumentPair.getQuantityScale());
        Order trade = null;
        if (Side.BUY == side) {
          trade = matchAutoBuyWithoutOrderBook(order, user, InsuranceState.getUser(), user.getPositionArr(), order.getQuantityLong());
        } else if (Side.SELL == side) {
          trade = matchAutoSellWithoutOrderBook(order, user, InsuranceState.getUser(), user.getPositionArr(), order.getQuantityLong());
        }
        if (trade != null)
          order.setQuantityLong(trade.getQuantityLong());
        if (order.getQuantityLong() > 0) {
          matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(order, instrumentPair));
        }

        // fix available quantity
        final long diff = settleQuantity - settleAvailableQuantity;
        settlePosition.setAvailableQuantity(settlePosition.getQuantity() - diff);
        final long diff2 = btcQuantity - btcAvailableQuantity;
        btcPosition.setAvailableQuantity(btcPosition.getQuantity() - diff2);
      }
    }

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "onCollateralSwapOrder3=", order, USER_EQ, user);
    }

    user.setFeeTier(user.getFeeTierOrig());

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "onCollateralSwapOrder sweeped=", order, USER_EQ, user);
    }
    return order;
  }

  /*
   * onUnderlyerPhysicalSettle force trade at the strike price for options example:
   *
   * user is long 1 call option, 6000 strike, mark = 9000 during settlement: buy 1 BTC @6000 -6000 usd +1 BTC @ 9000
   *
   * for future buy 6000 avg cost, mark = 9000 -6000 usd +1 BTC @ 9000
   */

  @Override
  public Order onUnderlyerPhysicalSettle(final User user, final long price, final short price_scale, final long qty, final short qty_scale,
      final Side side) {
    final Message message = newOrderSingleHandler.buildNewLiquidationOrder(user, "" + 1_000_000_000 + user.getId(), id, AUTOCLOSE_STR,
        price, price_scale, qty, qty_scale, side, OrdType.LIMIT, true);
    final LiquidationOrder order = (LiquidationOrder) message;

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "onUnderlyerPhysicalSettle id=", id, ", price=", price, ", price_scale=", price_scale, ", qty=", qty,
          ", qty_scale=", qty_scale, ", side=", side, ", order=", order, USER_EQ, user);
    }

    // set liquidation fee tier if usd value below tier threshold
    // if (user.getUsdValue() < NotionalMarginCalc.LOWEST_TIER_THRESHOLD)
    user.setFeeTier(Fee.LIQUIDATION_FEE_ID);


    // update available fee
    preOrderCheck.checkOrderNoValidation(order, (int) instrumentPair.adjustPriceToScale(price, price_scale));

    // force trade
    if (Side.BUY == side)
      matchAutoBuyWithoutOrderBook(order, user, InsuranceState.getUser(), user.getPositionArr(), qty);
    else if (Side.SELL == side)
      matchAutoSellWithoutOrderBook(order, user, InsuranceState.getUser(), user.getPositionArr(), qty);



    user.setFeeTier(user.getFeeTierOrig());

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_2, "onUnderlyerPhysicalSettle sweeped=", order, USER_EQ, user);
    }
    return order;
  }

  @Override
  public void onLiquidationOrder(final LiquidationOrder liquidationOrder) {
    final User user = liquidationOrder.getUser();
    if (user.getAutoLiquidationState().get() == 0) {
      return;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, ">>> liquidationOrder=", liquidationOrder, USER_EQ, user);
    }
    try {
      Position[] positionArr = user.getPositionArr();
      Position position = positionArr[liquidationOrder.getSecurityId()];

      /// -- ported
      if (position.getQuantity() == 0) {
        LOGGER.warn(LOG_FMT_4, "Current position is 0, liquidationOrder qty=", liquidationOrder.getQty(), ", side=",
            liquidationOrder.getSide());
        return;
      }

      Side expectedSide = position.getQuantity() > 0 ? Side.SELL : Side.BUY;
      if (Math.abs(position.getQuantity()) != Math.abs(liquidationOrder.getQty()) || !expectedSide.equals(liquidationOrder.getSide())) {
        LOGGER.warn(LOG_FMT_8, "LiquidationOrder qty=", liquidationOrder.getQty(), ", side=", liquidationOrder.getSide(),
            ", current position=", position.getQuantity(), ", expected side=", expectedSide);
      }

      liquidationOrder.setQty(Math.abs(position.getQuantity()), liquidationOrder.getQtyScale());
      liquidationOrder.setQuantityLong(Math.abs(position.getQuantity()));
      liquidationOrder.setSide(expectedSide);
      /// -- end of ported change

      // ack
      ExecutionReportMessage executionReportMessage =
          ExecutionReportMessage.createAckNewOrderExecutionReport(liquidationOrder, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_2, "liquidationOrder ack executionReportMessage=", executionReportMessage);
      }

      // recalc MarginRatio, if below 1 stop liquidating and eliminate order
      if (isMarginLiquidationSatisfied(user, liquidationOrder))
        return;

      // process external order
      // then, match internally
      addOrder(liquidationOrder);

/*      if (Side.BUY == liquidationOrder.getSide()) { // buying to cover short
        liquidationBuyToCloseOrder(liquidationOrder, user, positionArr);
      } else { // sell to close
        liquidationSellToCloseOrder(liquidationOrder, user, positionArr);
      }*/

      //autoConvertStableCoinsToSettle(user, liquidationOrder.getKafkaRecordOffset());

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    } finally {
      user.getAutoLiquidationCounter().decrementAndGet();
      isMarginLiquidationSatisfied(user, null);
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, "<<< liquidationOrder=", liquidationOrder, USER_EQ, user);
    }
  }

  private final void changeFinalMatcherToPublisherQueueReference(final ManyToOneConcurrentArrayQueueCustom<Message> newValue) {
    try {
      final Field field = this.getClass().getDeclaredField("matcherToPublisherQueue");
      field.setAccessible(true);
      int modifiers = field.getModifiers();
      final Field modifierField = field.getClass().getDeclaredField("modifiers");
      modifiers = modifiers & ~Modifier.FINAL;
      modifierField.setAccessible(true);
      modifierField.setInt(field, modifiers);
      // set newValue
      field.set(this, newValue);
      // set back to final
      modifiers = Modifier.PRIVATE + Modifier.FINAL;
      modifierField.setInt(field, modifiers);
      modifierField.setAccessible(false);
      field.setAccessible(false);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  // take bankrupt user remaining funds
  // generate balance admin messages
  private final void balanceTransferRemainingCollateral(final User user) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, "liquidation balanceTransferRemainingCollateral=", USER_EQ, user);
      return; // TODO: return for testing
    }

    try {
      final int assetId = MarginPreOrderCheckAndSettle.SETTLE_INSTRUMENT_ID;
      final Position settlePosition = user.getPosition(assetId);
      final User insuranceUser = InsuranceState.getUser();

      if (settlePosition.getQuantity() > 0 && insuranceUser != null) {
        final Instrument instrument = InstrumentCache.get(assetId);

        final BalanceAdminMessage from = new BalanceAdminMessage();
        from.setUpdateType(UpdateType.PATCH);
        from.setUserId(user.getId());
        from.setUser(user);
        from.setFirmId(user.getFirmId());
        from.setFeeTier(user.getFeeTierOrig());
        from.setSenderInstanceId(Context.getInstanceId());
        from.setTxType(Constants.TX_FROM_BANKRUPT_REMAINDER);
        from.setTxId(instrumentPair.getId());
        from.addBalance(new Balance(assetId, 0, 0, -settlePosition.getQuantity(), instrument.getQuantityScale(),
            settlePosition.getAssetIdtreeSet(), 0, null));

        final BalanceAdminMessage to = new BalanceAdminMessage();
        to.setUpdateType(UpdateType.PATCH);
        to.setUserId(insuranceUser.getId());
        to.setUser(insuranceUser);
        to.setFirmId(insuranceUser.getFirmId());
        to.setFeeTier(insuranceUser.getFeeTierOrig());
        to.setSenderInstanceId(Context.getInstanceId());
        to.setTxType(Constants.TX_TO_BANKRUPT_REMAINDER);
        to.setTxId(instrumentPair.getId());
        to.addBalance(new Balance(assetId, 0, 0, settlePosition.getQuantity(), instrument.getQuantityScale(),
            settlePosition.getAssetIdtreeSet(), 0, null));

        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_4, "liquidation2 balanceTransferRemainingCollateral=", USER_EQ, user, ", from=", from, ", to=", to);
        }

        from.onMatcher();
        to.onMatcher();
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  // switch output queue to a disabled queue so nothing is published
  public final void disableOutputQueue() {
    changeFinalMatcherToPublisherQueueReference(new DisabledManyToOneConcurrentArrayQueueCustom<>(4, "disabledQueue"));
  }

  // restore output to the actual publishing queue
  public final void restoreOutputQueue() {
    changeFinalMatcherToPublisherQueueReference(Context.getMatcherToPublisherQueue());
  }

  @Override
  public OrderBookValidator getOrderBookValidator() {
    return validator;
  }

  @Override
  public final void copyTo(final OrderBook target, final Transform transform) {
    // disable publishing to output
    //target.disableOutputQueue();

    // restate
    /*
     * List<Order> orders = new ArrayList<>(); for (int i = 0; i < bookArr.length; i++) { // this will take a while...
     * 
     * // we need to copy the orders to a separate list ad adding the orders to the target book invalidates // the prev/next references in
     * the orders orders.clear(); Order tmp = bookArr[i].getHead(); while (tmp != null) { orders.add(tmp); tmp = tmp.getNext(); }
     * 
     * for (final Order order : orders) { target.addOrder(transform == null ? order : transform.transform(order)); } }
     * 
     * // restate outOfBounds orders final List<Order> outOfBoundsList = new ArrayList<>(outOfBoundsOrderMap.values());
     * Collections.sort(outOfBoundsList, orderComparator); for (final Order outOfBoundsOrder : outOfBoundsList) { if (outOfBoundsOrder ==
     * null) continue; target.addOrder(transform == null ? outOfBoundsOrder : transform.transform(outOfBoundsOrder)); }
     * 
     * // restate stop limit orders final TreeSet<Order> buyTreeSet = stopLimitContainer.getBuyTreeSet(); while (!buyTreeSet.isEmpty()) {
     * final Order order = buyTreeSet.pollFirst(); if (order != null) { target.addOrder(transform == null ? order :
     * transform.transform(order)); } } final TreeSet<Order> sellTreeSet = stopLimitContainer.getSellTreeSet(); while
     * (!sellTreeSet.isEmpty()) { final Order order = sellTreeSet.pollFirst(); if (order != null) { target.addOrder(transform == null ?
     * order : transform.transform(order)); } }
     * 
     * // restate profit stop limit orders final TreeSet<Order> buyTreeSet2 = stopProfitContainer.getBuyTreeSet(); while
     * (!buyTreeSet2.isEmpty()) { final Order order = buyTreeSet2.pollFirst(); if (order != null) { target.addOrder(transform == null ?
     * order : transform.transform(order)); } } final TreeSet<Order> sellTreeSet2 = stopProfitContainer.getSellTreeSet(); while
     * (!sellTreeSet2.isEmpty()) { final Order order = sellTreeSet2.pollFirst(); if (order != null) { target.addOrder(transform == null ?
     * order : transform.transform(order)); } }
     * 
     * // restate auction orders final ConcurrentSkipListSet<Order> buyTreeSet3 = auctionContainer.getBuyTreeSet(); while
     * (!buyTreeSet3.isEmpty()) { final Order order = buyTreeSet3.pollFirst(); if (order != null) { target.addOrder(transform == null ?
     * order : transform.transform(order)); } } final ConcurrentSkipListSet<Order> sellTreeSet3 = auctionContainer.getSellTreeSet(); while
     * (!sellTreeSet3.isEmpty()) { final Order order = sellTreeSet3.pollFirst(); if (order != null) { target.addOrder(transform == null ?
     * order : transform.transform(order)); } }
     * 
     * // restate trailing stop limit orders final TreeSet<Order> buyTreeSet4 = trailingStopContainer.getBuyTreeSet(); while
     * (!buyTreeSet4.isEmpty()) { final Order order = buyTreeSet4.pollFirst(); if (order != null) { target.addOrder(transform == null ?
     * order : transform.transform(order)); } } final TreeSet<Order> sellTreeSet4 = trailingStopContainer.getSellTreeSet(); while
     * (!sellTreeSet4.isEmpty()) { final Order order = sellTreeSet4.pollFirst(); if (order != null) { target.addOrder(transform == null ?
     * order : transform.transform(order)); } }
     * 
     * // restate rfq orders final ConcurrentSkipListSet<Order> buyTreeSet5 = auctionContainer.getBuyTreeSet(); while
     * (!buyTreeSet5.isEmpty()) { final Order order = buyTreeSet5.pollFirst(); if (order != null) { target.addOrder(transform == null ?
     * order : transform.transform(order)); } } final ConcurrentSkipListSet<Order> sellTreeSet5 = auctionContainer.getSellTreeSet(); while
     * (!sellTreeSet5.isEmpty()) { final Order order = sellTreeSet5.pollFirst(); if (order != null) { target.addOrder(transform == null ?
     * order : transform.transform(order)); } }
     */

    // restore publishing to output
    //target.restoreOutputQueue();
  }

  // clears and removes references of cached data
  @Override
  public final void reclaim() {
    /*
     * for (int i = 0; i < bidLevelCachePtrArr.length; i++) bidLevelCachePtrArr[i] = 0; for (int i = 0; i < askLevelCachePtrArr.length; i++)
     * askLevelCachePtrArr[i] = 0; for (int i = 0; i < bookArr.length; i++) { bookArr[i].clear(); bookArr[i] = null; }
     * bidDepthLevelCacheCount = 0; askDepthLevelCacheCount = 0;
     */
  }

  /****
   * DR code below
   */

  // use hashmap to track open orders in DR
  private final Map<Long, DROrder> drOrderBookMap = new HashMap<>();
  private long drOrderIndex = 0;

  @Override
  public void addOrderDR(final DROrder order) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, ADDORDERDR_ID_EQ, order.getOrderId(), ORDER_EQ, order);
    }

    // update ids
    if (order.getSecurityId() == id) {
      GlobalOrderBook.setOrderIdIfGreater(16, order.getOrderId());
      setSecondaryOrderIdIfGreater(order.getSecondaryOrderId());
    }

    order.setOrderIndex(drOrderIndex++);
    drOrderBookMap.put(order.getOrderId(), order);
  }

  @Override
  public void cancelOrderDR(final DRCancelOrder cancelOrder) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, CANCELORDERDR_ID_EQ, cancelOrder.getOrigOrderId(), CANCELORDER_EQ, cancelOrder);
    }

    GlobalOrderBook.setOrderIdIfGreater(17, cancelOrder.getCancelId());
    GlobalOrderBook.setOrderIdIfGreater(18, cancelOrder.getOrigOrderId());

    final DROrder order = drOrderBookMap.remove(cancelOrder.getOrigOrderId());
    DROrderObjectPool.returnObject(order);
    DRCancelOrderObjectPool.returnObject(cancelOrder);
  }

  private final void updateOrderFromExecReport(final DRExecutionReport executionReport) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, UPDATEORDERFROMEXECREPORT_ID_EQ, executionReport.getOrderId(), EXECUTIONREPORT_EQ, executionReport);
    }

    GlobalOrderBook.setOrderIdIfGreater(19, executionReport.getOrderId());

    if (OrdStatus.CALCULATED == executionReport.getOrdStatus() || OrdStatus.FILLED == executionReport.getOrdStatus()
        || OrdStatus.CANCELED == executionReport.getOrdStatus()) {
      final DROrder order = drOrderBookMap.remove(executionReport.getOrderId());
      DROrderObjectPool.returnObject(order);
    } else {
      final DROrder order = drOrderBookMap.get(executionReport.getOrderId());
      if (order == null) {
        LOGGER.error("error updateOrderFromExecReport order not found: " + executionReport.getOrderId());
      } else {
        final long qty = executionReport.getQtyInOrderbook();
        order.setQuantityLong(qty);
        order.setFeeEstimatedQuantity(executionReport.getFeeEstimatedQuantity());
        order.setFeeAccumulatedQuantity(executionReport.getFeeAccumulatedQuantity());
        order.setAvailableEstimatedQuantity(executionReport.getAvailableEstimatedQuantity());
        order.setAvailableAccumulatedQuantity(executionReport.getAvailableAccumulatedQuantity());
      }
    }

    // special case because primary doesn't publish balanceAdmin for feeUser
    // we add the fee to the exchange user here
    if (executionReport.getFeeInstrumentId() > 0 && executionReport.getFeeQty() != 0) {

      if (!executionReport.isPaidToInsurance() && UserCache.getExchangeUser() != null) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.debug(LOG_FMT_4, UPDATEORDERFROMEXECREPORT_FEEID_EQ, (long) executionReport.getFeeInstrumentId(), VALUE_EQ,
              (-executionReport.getFeeQty()));
        }
        UserCache.getExchangeUser().addPosition(executionReport.getFeeInstrumentId(), -executionReport.getFeeQty(),
            executionReport.getAssetId(), executionReport.getTokenId(), executionReport.getGroupAssetId());
      } else if (executionReport.getFeeInstrumentId() > 0 && executionReport.isPaidToInsurance() && executionReport.getFeeQty() != 0
          && UserCache.getInsuranceFundUser() != null) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.debug(LOG_FMT_4, UPDATEORDERFROMEXECREPORT_FEEID_EQ, (long) executionReport.getFeeInstrumentId(), VALUE_EQ,
              (-executionReport.getFeeQty()));
        }
        UserCache.getInsuranceFundUser().addPosition(executionReport.getFeeInstrumentId(), -executionReport.getFeeQty(),
            executionReport.getAssetId(), executionReport.getTokenId(), executionReport.getGroupAssetId());
      }
    }

    DRExecutionReportObjectPool.returnObject(executionReport);
  }

  private long lastExecId = 0;

  @Override
  public void execReportDR(final DRExecutionReport executionReport) {

    // update ids
    GlobalOrderBook.setFilledCountGlobalIfGreater(executionReport.getExecId());
    instrumentPair.getOrderBook().setFilledCountIfGreater(executionReport.getSecondaryExecId());

    final OrdStatus ordStatus = executionReport.getOrdStatus();
    if (ordStatus != null && ordStatus == OrdStatus.REJECTED)
      return;

    if (ExecType.TRADE == executionReport.getExecType() || ExecType.CALCULATED == executionReport.getExecType()) {
      updateOrderFromExecReport(executionReport);

      // logic to validate DR, ONLY when the 2nd ExecId is recieved
      if (Context.isStateValidatorEnabled() && (lastExecId > 0) && (lastExecId == executionReport.getExecId())
          && !StateValidator.validateDR(executionReport)) {
        LOGGER.error(LOG_FMT_2, "DR validate error ", executionReport);
      }
      lastExecId = executionReport.getExecId();
    }
  }

  // should take 30-50 ms for 100M orderbook
  public final void clearOrderBook() {
    /*
     * for (int i = 0; i < bidLevelCachePtrArr.length; i++) bidLevelCachePtrArr[i] = 0; for (int i = 0; i < askLevelCachePtrArr.length; i++)
     * askLevelCachePtrArr[i] = 0; for (int i = 0; i < bookArr.length; i++) bookArr[i].clear(); bidDepthLevelCacheCount = 0;
     * askDepthLevelCacheCount = 0;
     * 
     * stopLimitContainer.clear(); stopProfitContainer.clear(); outOfBoundsOrderMap.clear(); outOfBoundsOrderMapBySecondaryOrderId.clear();
     */
  }

  private void populateOrderBookFromDRMap() {
    clearOrderBook();

    final List<DROrder> list = new ArrayList<>(drOrderBookMap.values());
    Collections.sort(list, orderIndexComparator);

    rebuildInProgress = true;
    for (final DROrder order : list) {
      addOrder(order);
    }

    rebuildInProgress = false;
  }

  public int getRiskPriceForSell(int orderPrice) {
    long markPrice = MbxMath.changeScale(instrumentPair.getUsdMark(), 2);
    int bidPrice = bidLevelCachePtrArr[0] == 0 ? 0 : bidLevelCachePtrArr[0];

    return (int) Math.max(Math.max(orderPrice, markPrice), bidPrice);
  }

  private static final Comparator<DROrder> orderIndexComparator = new Comparator<DROrder>() {
    @Override
    public int compare(final DROrder order1, final DROrder order2) {
      try {
        if (order1.getOrderIndex() == order2.getOrderIndex())
          return 0;
        else if (order1.getOrderIndex() > order2.getOrderIndex())
          return 1;
        else
          return -1;
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
      return 0;
    }
  };


  @Override
  public final int getArrSize() {
    return 0;
  }

  @Override
  public final Order buildAlgoOrder(final Order source) {
    final Order order = OrderObjectPool.get();
    order.set(source, NewOrderSingleHandler.getNextOrderId(), 0); // secondaryOrderId++
    if (order.isReduceOnly())
      order.setTargetStrategy(REDUCE_ONLY);
    else
      order.setTargetStrategy(0);
    order.setExpireTime(0);
    order.setPrice2(0, (short) 0);
    NewOrderSingleHandler.parseOrder(order);
    return order;
  }

  @Override
  public final void expireAllOrders() {/*
                                        * for (int i = 0; i < bookArr.length; i++) { // this will take a while... Order tmp = null; Order
                                        * next = null; if (bookArr[i].getHead() != null) { tmp = bookArr[i].getHead(); while (tmp != null) {
                                        * next = tmp.getNext();
                                        * 
                                        * // expireOrder(tmp); preOrderCheck.updateCancel(tmp); final ExecutionReportMessage
                                        * executionReportMessage = ExecutionReportMessage.createOrderEliminationExecutionReport(tmp,
                                        * instrumentPair); matcherToPublisherQueue.addGuaranteed(executionReportMessage);
                                        * 
                                        * tmp = next; } } }
                                        * 
                                        * FastArrayList<Order> list = new FastArrayList<>(stopLimitContainer.getSellTreeSet()); for (final
                                        * Order order : list) { final long cancelId = order.getOrderId(); final CancelOrder cancelOrder =
                                        * CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(order, cancelId, cancelId);
                                        * cancelOrder.setUser(order.getUser()); cancelOrder(cancelOrder); }
                                        * 
                                        * list = new FastArrayList<>(stopLimitContainer.getBuyTreeSet()); for (final Order order : list) {
                                        * final long cancelId = order.getOrderId(); final CancelOrder cancelOrder =
                                        * CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(order, cancelId, cancelId);
                                        * cancelOrder.setUser(order.getUser()); cancelOrder(cancelOrder); }
                                        * 
                                        * list = new FastArrayList<>(stopProfitContainer.getSellTreeSet()); for (final Order order : list) {
                                        * final long cancelId = order.getOrderId(); final CancelOrder cancelOrder =
                                        * CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(order, cancelId, cancelId);
                                        * cancelOrder.setUser(order.getUser()); cancelOrder(cancelOrder); }
                                        * 
                                        * list = new FastArrayList<>(stopProfitContainer.getBuyTreeSet()); for (final Order order : list) {
                                        * final long cancelId = order.getOrderId(); final CancelOrder cancelOrder =
                                        * CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(order, cancelId, cancelId);
                                        * cancelOrder.setUser(order.getUser()); cancelOrder(cancelOrder); }
                                        * 
                                        * list = new FastArrayList<>(trailingStopContainer.getSellTreeSet()); for (final Order order : list)
                                        * { final long cancelId = order.getOrderId(); final CancelOrder cancelOrder =
                                        * CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(order, cancelId, cancelId);
                                        * cancelOrder.setUser(order.getUser()); cancelOrder(cancelOrder); }
                                        * 
                                        * list = new FastArrayList<>(trailingStopContainer.getBuyTreeSet()); for (final Order order : list)
                                        * { final long cancelId = order.getOrderId(); final CancelOrder cancelOrder =
                                        * CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(order, cancelId, cancelId);
                                        * cancelOrder.setUser(order.getUser()); cancelOrder(cancelOrder); }
                                        * 
                                        * list = new FastArrayList<>(auctionContainer.getSellTreeSet()); for (final Order order : list) {
                                        * final long cancelId = order.getOrderId(); final CancelOrder cancelOrder =
                                        * CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(order, cancelId, cancelId);
                                        * cancelOrder.setUser(order.getUser()); cancelOrder(cancelOrder); }
                                        * 
                                        * list = new FastArrayList<>(auctionContainer.getBuyTreeSet()); for (final Order order : list) {
                                        * final long cancelId = order.getOrderId(); final CancelOrder cancelOrder =
                                        * CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(order, cancelId, cancelId);
                                        * cancelOrder.setUser(order.getUser()); cancelOrder(cancelOrder); }
                                        * 
                                        * list = new FastArrayList<>(outOfBoundsOrderMap.values()); for (final Order order : list) { final
                                        * long cancelId = order.getOrderId(); final CancelOrder cancelOrder =
                                        * CancelOrderMatchThreadObjectPool.get(); cancelOrder.set(order, cancelId, cancelId);
                                        * cancelOrder.setUser(order.getUser()); cancelOrder(cancelOrder); }
                                        * 
                                        * clearOrderBook();
                                        */
  }

  // should be called from matching thread
  // Use an order to convert stable coins to USD
  public void autoConvertStableCoinsToSettle(final User user, final long kafkaRecordOffset) {
    LOGGER.info(LOG_FMT_2, "Trigger auto-convert for user: ", user.getId());
    if (user.getPositionArr() == null || user.getPositionArr().length == 0) {
      LOGGER.info(LOG_FMT_3, "Trigger auto-convert for user: ", user.getId(), " no available positions.");
      return;
    }
    final Instrument usd = InstrumentCache.getBySymbol(USD);
    final Position usdPosition = user.getPositionArr()[usd.getId()];
    if (usdPosition == null) {
      LOGGER.info(LOG_FMT_3, "Trigger auto-convert for user: ", user.getId(), " no available USD positions.");
      return;
    }
    LOGGER.info("UserId: " + user.getId() + " USD: " + usdPosition.getUsdValue() + " qty: " + usdPosition.getQuantity() + " scale: "
        + usd.getQuantityScale());
    if (usdPosition.getQuantity() < -MbxMath.changeScale(Context.getAutoConvertMinAmount(), usd.getQuantityScale())) {
      double usdPositionValue = (-usdPosition.getUsdValue() + 0.000001);// to fix rounding errors
      double amountToSettle = usdPositionValue;
      final Instrument usdc = InstrumentCache.getBySymbol(USDC);
      final Instrument usdt = InstrumentCache.getBySymbol(USDT);
      final Position usdcPosition = user.getPositionArr()[usdc.getId()];
      final Position usdtPosition = user.getPositionArr()[usdt.getId()];
      double usdcBalance = usdcPosition != null ? usdcPosition.getUsdValue() : 0;
      double usdtBalance = usdtPosition != null ? usdtPosition.getUsdValue() : 0;

      if (usdcBalance >= amountToSettle) {
        final InstrumentPair pair = InstrumentCache.getPairBySymbol(USDC_USD);
        // DO NOT PAY MORE THAN $1 FOR A USDC
        double price =
            pair.getIndexFeedUsdMark() == 0 ? 1 : Math.min(MbxMath.roundDown(pair.getIndexFeedUsdMark(), usdc.getPriceScale()), 1D);
        double quantity = MbxMath.roundUp(usdPositionValue / price, usdc.getQuantityScale());
        long amount = MbxMath.scaleUp(quantity, usdc.getQuantityScale());
        long unitPrice = MbxMath.scaleUp(price, usdc.getPriceScale());
        LOGGER.info("Auto convert USDC/USD userId: " + user.getId() + " USDC: " + usdcBalance + " USD: " + usdPositionValue + " price: " + price + " quantity: "
            + quantity + " amount: " + amount + " unitPrice: " + unitPrice);
        generateAutoConvertOrder(pair, user, amount, usdc.getQuantityScale(), unitPrice, usdc.getPriceScale(), kafkaRecordOffset);
      } else if (usdtBalance >= amountToSettle) {
        final InstrumentPair pair = InstrumentCache.getPairBySymbol(USDT_USD);
        // DO NOT PAY MORE THAN $1 FOR A USDT
        double price =
            pair.getIndexFeedUsdMark() == 0 ? 1 : Math.min(MbxMath.roundDown(pair.getIndexFeedUsdMark(), usdt.getPriceScale()), 1D);
        double quantity = MbxMath.roundUp(usdPositionValue / price, usdt.getQuantityScale());
        long amount = MbxMath.scaleUp(quantity, usdt.getQuantityScale());
        long unitPrice = MbxMath.scaleUp(price, usdt.getPriceScale());
        LOGGER.info("Auto convert USDT/USD userId: " + user.getId() + " USDT: " + usdtBalance + " USD: " + usdPositionValue + " price: " + price + " quantity: "
            + quantity + " amount: " + amount + " unitPrice: " + unitPrice);
        generateAutoConvertOrder(pair, user, amount, usd.getQuantityScale(), unitPrice, usdt.getPriceScale(), kafkaRecordOffset);
      } else if (usdcBalance > 0 || usdtBalance > 0) {
        if (usdcBalance > 0) {
          InstrumentPair pair = InstrumentCache.getPairBySymbol(USDC_USD);
          // DO NOT PAY MORE THAN $1 FOR A USDC
          double price =
              pair.getIndexFeedUsdMark() == 0 ? 1 : Math.min(MbxMath.roundDown(pair.getIndexFeedUsdMark(), usdc.getPriceScale()), 1D);
          double usdQuantity = MbxMath.roundUp(usdcPosition.getUsdValue(), usdc.getQuantityScale());
          long amount = MbxMath.scaleUp(usdcBalance, usdc.getQuantityScale());
          long unitPrice = MbxMath.scaleUp(price, usdc.getPriceScale());
          generateAutoConvertOrder(pair, user, usdcPosition.getQuantity(), usdc.getQuantityScale(), unitPrice, usdc.getPriceScale(),
              kafkaRecordOffset);
          amountToSettle -= usdQuantity;
          LOGGER.info("Auto convert USDC/USD userId: " + user.getId() + " USDC: " + usdcBalance + " USDT: " + usdtBalance + " USD: " + usdPositionValue
              + " price: " + price + " usdQuantity: " + usdQuantity + " amount: " + amount + " unitPrice: " + unitPrice);
        }
        if (usdtBalance > 0) {
          double settle = Math.min(amountToSettle, usdtBalance);
          amountToSettle -= settle;
          InstrumentPair pair = InstrumentCache.getPairBySymbol(USDT_USD);
          // DO NOT PAY MORE THAN $1 FOR A USDT
          double price =
              pair.getIndexFeedUsdMark() == 0 ? 1 : Math.min(MbxMath.roundDown(pair.getIndexFeedUsdMark(), usdt.getPriceScale()), 1D);
          double quantity = MbxMath.roundUp(settle / price, usdt.getQuantityScale());
          long amount = MbxMath.scaleUp(quantity, usdt.getQuantityScale());
          long unitPrice = MbxMath.scaleUp(price, usdt.getPriceScale());

          generateAutoConvertOrder(pair, user, amount, usdt.getQuantityScale(), unitPrice, usdt.getPriceScale(), kafkaRecordOffset);
        }
        if (amountToSettle > 0) {
          LOGGER.info(LOG_FMT_8, "No enough funds to fully auto convert stable coins. userId: ", user.getId(), ", USD: ", usdPositionValue,
              ", USDC: ", usdcBalance, ", USDT: ", usdtBalance);
        }
      } else {
        LOGGER.info(LOG_FMT_8, "No enough funds to auto convert stable coins. userId: ", user.getId(), ", USD: ", usdPositionValue,
            ", USDC: ", usdcBalance, ", USDT: ", usdtBalance);
      }
    }
  }

  public ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Liquidity>> getExchangeSymbolLiquidity() {
    return exchangeSymbolLiquidity;
  }

  private void generateAutoConvertOrder(final InstrumentPair pair, final User user, final long quantity, final short quantityScale,
      final long price, final short priceScale, final long kafkaRecordOffset) {
    final OrderBook orderBook = pair.getOrderBook();
    if (orderBook instanceof LiquidityOrderBook) {
      final LiquidityOrderBook liquidityOrderBook = (LiquidityOrderBook) orderBook;
      final Order order = (Order) liquidityOrderBook.newOrderSingleHandler.buildNewAutoConvertOrder(pair, user, quantity, quantityScale,
          price, priceScale, kafkaRecordOffset);
      LOGGER.info("Auto Convert order: " + order.toJSON());
      liquidityOrderBook.addOrder(order);
      LOGGER.info("Auto Convert order completed. ");
    } else {
      LOGGER.info("Pair is not an instance of Liquidity orderbook: " + pair.getSymbol());
    }
  }

  private void applyPromoOffer(final Order order) {
    if (order.getTargetStrategy() == EXTERNAL && Context.getPromoDepositThreshold() > 0 && !order.getUser().isRewardClaimed()) {
      final User user = order.getUser();
      double value = MbxMath.scaleDown(order.getPrice2(), order.getPrice2Scale())
          * MbxMath.scaleDown(order.getQuantityOrigLong(), order.getQuantityOrigScale());

      Instrument instrument = instrumentPair.getQuoted();

      long timestamp = System.currentTimeMillis();
      long triggerTimeMillis = System.currentTimeMillis();

      long change = MbxMath.changeScale(Context.getPromoDepositValue(), instrument.getQuantityScale());

      if (value >= Context.getPromoDepositThreshold()) {
        final BalanceAdminMessage message = BalanceAdminMessageObjectPool.get();
        message.setUpdateType(UpdateType.PATCH);
        message.setRequestStatus(RequestStatus.SUCCESS);
        message.setChecksum(0);
        message.setPersistTime(timestamp);
        message.setUserId(user.getId());
        message.setUser(user);
        message.setFirmId(user.getFirmId());
        message.setFeeTier(user.getFeeTier());
        message.setTxType(Constants.TX_ADMIN_DEPOSIT);
        message.setTxId(order.getSecurityId());
        message.setTriggerTimeMillis(triggerTimeMillis);
        message.setMatchTime(triggerTimeMillis);

        final Balance balance = new Balance();
        message.getBalanceList().add(balance);
        balance.setAssetId(instrument.getId());
        balance.setBalanceChange(change, instrument.getQuantityScale());
        balance.setTokenType(TokenType.ERC20);
        TreeSet<long[]> assetIdtreeSet = new TreeSet<>(assetIdComparator);
        long[] arrValue = new long[] {0, 0, 0};
        assetIdtreeSet.add(arrValue);
        balance.setAssetIdtreeSet(assetIdtreeSet);

        UserCache.addBalance(message);

        final User marketMakerUser = UserCache.getMarketMakerUser();
        final BalanceAdminMessage counterMessage = BalanceAdminMessageObjectPool.get();
        counterMessage.setUpdateType(UpdateType.PATCH);
        counterMessage.setRequestStatus(RequestStatus.SUCCESS);
        counterMessage.setChecksum(0);
        counterMessage.setPersistTime(timestamp);
        counterMessage.setUserId(marketMakerUser.getId());
        counterMessage.setUser(marketMakerUser);
        counterMessage.setFirmId(marketMakerUser.getFirmId());
        counterMessage.setFeeTier(marketMakerUser.getFeeTier());
        counterMessage.setTxType(Constants.TX_ADMIN_DEPOSIT);
        counterMessage.setTxId(order.getSecurityId());
        counterMessage.setTriggerTimeMillis(triggerTimeMillis);
        counterMessage.setMatchTime(triggerTimeMillis);

        final Balance counterBalance = new Balance();
        counterMessage.getBalanceList().add(counterBalance);
        counterBalance.setAssetId(instrument.getId());
        counterBalance.setBalanceChange(-change, instrument.getQuantityScale());
        counterBalance.setTokenType(TokenType.ERC20);
        assetIdtreeSet = new TreeSet<>(assetIdComparator);
        arrValue = new long[] {0, 0, 0};
        assetIdtreeSet.add(arrValue);
        counterBalance.setAssetIdtreeSet(assetIdtreeSet);

        UserCache.addBalance(counterMessage);

        final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createFundingExecutionReport(
            NewOrderSingleHandler.getNextOrderId(), user, instrumentPair.getId(), instrument.getSymbol(), 1L, (short) 0, change,
            (short) instrument.getQuantityScale(), incrementAndGetFilledCountGlobal(), 0, marketMakerUser.getId(), Constants.INTEREST);
        executionReportMessage.setKafkaRecordOffset(order.getKafkaRecordOffset());
        if (publishPositions) {
          user.copySetPositionArr(executionReportMessage);
        }

        matcherToPublisherQueue.addGuaranteed(executionReportMessage);

        final ExecutionReportMessage counterExecutionReportMessage = ExecutionReportMessage.createFundingExecutionReport(
            NewOrderSingleHandler.getNextOrderId(), marketMakerUser, instrumentPair.getId(), instrument.getSymbol(), 1L, (short) 0, -change,
            (short) instrument.getQuantityScale(), incrementAndGetFilledCountGlobal(), 0, user.getId(), Constants.INTEREST);
        counterExecutionReportMessage.setKafkaRecordOffset(order.getKafkaRecordOffset());
        if (publishPositions) {
          user.copySetPositionArr(counterExecutionReportMessage);
        }

        matcherToPublisherQueue.addGuaranteed(counterExecutionReportMessage);

        // UserCache.setRewardClaimed(user.getId());
        // user.setRewardClaimed(true);
        UserAdminMessage message1 = user.buildUserAdminMessage();
        if (publishPositions) {
          user.copySetPositionArr(message1);
        }
        message1.setRewardClaimed(true);
        UserCache.addToCache(message1);

        matcherToPublisherQueue.addGuaranteed(message1);

        LOGGER.info(Constants.LOG_FMT_2, "message1: ", message1.toJSON());
      }
    }
  }
}
