package com.solfini.matchengine.orderbook;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.agrona.collections.Long2ObjectHashMap;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.DisabledManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Fee;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.AssetGroupCache;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.drmode.message.DRCancelOrder;
import com.solfini.matchengine.drmode.message.DRExecutionReport;
import com.solfini.matchengine.drmode.message.DROrder;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.CollateralSwapMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.LiquidationOrder;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.matchengine.message.outbound.CancelRejectMessage;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.matchengine.orderbook.validator.OrderBookValidatorFactory;
import com.solfini.pool.CancelOrderMatchThreadObjectPool;
import com.solfini.pool.CancelOrderObjectPool;
import com.solfini.pool.DRCancelOrderObjectPool;
import com.solfini.pool.DRExecutionReportObjectPool;
import com.solfini.pool.DROrderObjectPool;
import com.solfini.pool.OrderMatchingThreadObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.preordercheck.NoPreOrderCheck;
import com.solfini.preordercheck.NotionalMarginCalc;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.report.StateValidator;
import com.solfini.risk.InsuranceState;
import com.solfini.risk.UserRiskCache;
import com.solfini.sbe.encoder.BusinessRejectReason;
import com.solfini.sbe.encoder.CxlRejReason;
import com.solfini.sbe.encoder.ExecRestatementReason;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder.MdEntrieGroupEncoder;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.user.UserOpenOrdersByPair;
import com.solfini.util.FastArrayList;
import com.solfini.util.LogLevel;
import com.solfini.util.MbxMath;

/**
 *
 * @author Chris Mack
 *
 */


public class SelectArrayOrderBook extends GlobalOrderBook implements OrderBook, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(SelectArrayOrderBook.class);

  public static final int DEFAULT_ARR_SIZE = Context.getInitialOrderBookSize();
  public static final int DEFAULT_CACHE_DEPTH = 512; // test with 4
  private static final String ZERO_STR = "0";
  private static final String AUTOCLOSE_STR = "autoclose";
  private static final String AUTOCLOSE_MAKER_STR = "autoclose_maker";

  private static final String EXPIRESETTLE_STR = "expiresettle";
  private static final String DEFAULT_SENDER_COMP = "1000000009";
  private static final boolean PREVENT_SELF_TRADE = false;

  public final int ARR_SIZE;
  public final int CACHE_DEPTH;

  private final int id;
  private final int priceScale;
  private final int quanityScale;
  private final int orderBookStrategy;
  private final int preOrderCheckStrategy;
  private final InstrumentPair instrumentPair;
  private final Map<Long, Order> outOfBoundsOrderMap; // key is orderId
  private final Map<Long, Order> outOfBoundsOrderMapBySecondaryOrderId; // key is secondaryOrderId
  private final Long2ObjectHashMap<Order> idToOrderMap; // cache by orderId to support select
  private final FastArrayList<Order> triggeredOrders;
  private final StopLimitContainer stopLimitContainer;
  private final StopProfitContainer stopProfitContainer;
  private final TrailingStopContainer trailingStopContainer;
  private final ADLMakerContainer adlMakerContainer;
  private final SelectAuctionContainer auctionContainer;

  private PreOrderCheck preOrderCheck;
  private final PreOrderCheck preOrderCheckOrig;
  private final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue;

  private MarketStatus marketStatus;
  private int[] bidLevelCachePtrArr;
  private int[] askLevelCachePtrArr;
  private final OrderBookPriceLevel[] bookArr;

  private int orderCount;
  private long filledCount;
  private int bidDepth;
  private int askDepth;
  private int bidDepthLevelCacheCount;
  private int askDepthLevelCacheCount;
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

  public SelectArrayOrderBook(final InstrumentPair pair, final PreOrderCheck preOrderCheck, final int orderBookStrategy,
      final int preOrderCheckStrategy) {
    this(pair, preOrderCheck, DEFAULT_ARR_SIZE, DEFAULT_CACHE_DEPTH, orderBookStrategy, preOrderCheckStrategy);
  }

  public SelectArrayOrderBook(final InstrumentPair pair, final PreOrderCheck preOrderCheck, final int arrSize, final int cacheDepth,
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
    this.triggeredOrders = new FastArrayList<>();
    this.stopLimitContainer = new StopLimitContainer(id);
    this.stopProfitContainer = new StopProfitContainer(id);
    this.trailingStopContainer = new TrailingStopContainer(pair);
    this.adlMakerContainer = new ADLMakerContainer(id);
    this.auctionContainer = new SelectAuctionContainer(pair);

    this.ARR_SIZE = arrSize > 0 ? arrSize : DEFAULT_ARR_SIZE;
    this.CACHE_DEPTH = cacheDepth > 0 ? cacheDepth : DEFAULT_CACHE_DEPTH;
    this.bidLevelCachePtrArr = new int[CACHE_DEPTH];
    this.askLevelCachePtrArr = new int[CACHE_DEPTH];
    this.bookArr = new OrderBookPriceLevel[ARR_SIZE];
    this.outOfBoundsOrderMap = new HashMap<>();
    this.outOfBoundsOrderMapBySecondaryOrderId = new HashMap<>();
    this.idToOrderMap = new Long2ObjectHashMap<>();
    this.settleCoinUsdMarkInstrument = InstrumentCache.getBySymbol(USDC);
    this.usdMark = pair.getIndexFeedUsdMark();
    this.orderBookStrategy = orderBookStrategy;
    this.preOrderCheckStrategy = preOrderCheckStrategy;
    this.validator = OrderBookValidatorFactory.newOrderBookValidator(this);
    this.circuitBreakerThreshold = pair.getCircuitBreakerThreshold();

    // init orderbook
    for (int i = 0; i < bookArr.length; i++) {
      bookArr[i] = new OrderBookPriceLevel();
    }

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_18, "init orderbook: id=", id, PRICESCALE_EQ, priceScale, QUANTITYSCALE_EQ, quanityScale, ", ARR_SIZE=", ARR_SIZE,
          ", CACHE_DEPTH=", CACHE_DEPTH, ORDERBOOKSTRATEGY_EQ, orderBookStrategy, PREORDERCHECKSTRATEGY_EQ, preOrderCheckStrategy,
          ", DEFAULT_ARR_SIZE=", DEFAULT_ARR_SIZE, ", DEFAULT_CACHE_DEPTH=", DEFAULT_CACHE_DEPTH, ", circuitBreakerThreshold=",
          circuitBreakerThreshold, ", usdMark=", usdMark);
    }
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

  public final OrderBookPriceLevel[] getBookArr() {
    return bookArr;
  }

  public final int[] getBidLevelCachePtrArr() {
    return bidLevelCachePtrArr;
  }

  public final int[] getAskLevelCachePtrArr() {
    return askLevelCachePtrArr;
  }

  public final int getBidDepth() {
    return bidDepth;
  }

  public final int getAskDepth() {
    return askDepth;
  }

  @Override
  public final StopLimitContainer getStopLimitContainer() {
    return stopLimitContainer;
  }

  @Override
  public final StopProfitContainer getStopProfitContainer() {
    return stopProfitContainer;
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
    return trailingStopContainer;
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

    // update ids
    if ((order != null) && (order.getSecurityId() == id)) {
      GlobalOrderBook.setOrderIdIfGreater(15, order.getOrderId());
      setSecondaryOrderIdIfGreater(order.getSecondaryOrderId());
    }
    // validate
    if (order == null
        || (order.getPriceInt() <= 0 && !(order.getType() == BUY_MARKET || order.getType() == SELL_MARKET || order.isMarket()))) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, REJECT_ORDER_EQ, PRICE_IS_MISSING, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order == null ? "" : order.getSenderCompId(),
          MsgType.ORDER_SINGLE, Long.toString(order == null ? 0 : order.getOrderId()), BusinessRejectReason.PRICE_IS_MISSING,
          PRICE_IS_MISSING, order == null ? 0 : order.getOrderId(), order == null ? 0 : order.getSourceSeqNum(),
          order == null ? 0 : order.getSecondaryOrderId(), order == null ? 0 : order.getSecurityId()));
      return;
    } else if (order.getQuantityLong() <= 0) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, QUANTITY_IS_MISSING, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.QUANTITY_IS_MISSING, QUANTITY_IS_MISSING, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId()));
      return;
    } else if (MarketStatus.CLOSE == marketStatus || MarketStatus.PAUSE == marketStatus) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, MARKET_IS_PAUSED_OR_CLOSED, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.MARKET_IS_PAUSED_OR_CLOSED, MARKET_IS_PAUSED_OR_CLOSED,
          order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId()));
      return;
    }
    if (order.getSecurityId() != id) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, "adding invalid security in order. id=", id, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.INVALID_ORDER_SECURITY, INVALID_ORDER_SECURITY, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId()));
      return;
    }
    if (instrumentPair.isLimitOnlyMode() && order.getOrdType() != OrdType.LIMIT) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, "only limit orders are allowed id=", id, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.ONLY_LIMIT_ORDERS_ALLOWED, ONLY_LIMIT_ORDERS_ALLOWED, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId()));
      return;
    }
    if (order.getOrdType() == OrdType.SELECT && order.getSelectId() <= 0) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, "adding invalid selectId in order. id=", id, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.INVALID_ORDER_SECURITY, INVALID_ORDER_SELECT, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId()));
      return;
    }
    if (order.getSide() == Side.SELL && order.getGroupAssetId() > 0) {
      // there should be a group with the same number of assets as sell quantity
      // if there is an exsess, transfer extra to new asset group
      final AssetGroup assetGroup = AssetGroupCache.get(order.getGroupAssetId());
      if (assetGroup == null) {
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.ASSET_GROUP_NOT_FOUND, ASSET_GROUP_NOT_FOUND, order.getOrderId(),
            order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId()));
        return;
      }

      final ConcurrentSkipListSet<long[]> assetGroupSet = assetGroup.getAssetIdGroupTreeSet();
      if (assetGroupSet.size() < order.getQuantityLong()) {
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.ASSET_GROUP_NOT_ENOUGH, ASSET_GROUP_NOT_ENOUGH, order.getOrderId(),
            order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId()));
        return;
      }

      if (assetGroupSet.size() > order.getQuantityLong()) {
        // split group moving quantityFilled assets to taker group
        final AssetGroup newAssetGroup = new AssetGroup();
        newAssetGroup.copySet(assetGroup);
        newAssetGroup.setUpdateType(com.solfini.sbe.encoder.UpdateType.POST);
        newAssetGroup.setId(0);
        newAssetGroup.setGroupAssetId(0);

        final long reallocCount = assetGroupSet.size() - order.getQuantityLong();
        final long[][] reallocatedAssets = new long[(int) reallocCount][];
        for (int i = 0; i < reallocCount; i++) {
          final long[] value = assetGroupSet.pollFirst();
          reallocatedAssets[i] = value;
          newAssetGroup.addAssetId(value[0], (int) value[1]);
        }

        // publish changes
        AssetGroupCache.onModel(newAssetGroup);
        //update groupId in positions with new group id
        final Position position = order.getUser().getPosition((int) assetGroup.getSecurityId());
        for (long[] reallocated: reallocatedAssets) {
          for (final long[] assetTokenInPositions : position.getAssetIdtreeSet()) {
            if (reallocated[0] == assetTokenInPositions[0] && reallocated[1] == assetTokenInPositions[1]) {
              assetTokenInPositions[2] = newAssetGroup.getId();
              break;
            }
          }
        }

        matcherToPublisherQueue.add(assetGroup);
      }
    }

    // ack - moved to after preordercheck

    orderCount++;

    // TWAP special case
    if (order.isTWAP() && !rebuildInProgress) {
      if (order.getPrice2() < 1000) {
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.UNABLE_TO_PARSE_ORDER, INVALID_ALGO_ORDER_INTERVAL, order.getOrderId(),
            order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId()));
        return;
      }
      Context.getTimeTriggerThread().registerMessage(order);
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(order, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      return;
    } else if (order.isADLMaker()) {
      if (Side.BUY == order.getSide())
        adlMakerContainer.addBuyLimit(order);
      else
        adlMakerContainer.addSellLimit(order);
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(order, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      return;
    }

    switch (order.getType()) {
      case BUY_LIMIT:
        addBuyLimit(order);
        break;
      case SELL_LIMIT:
        addSellLimit(order);
        break;
      case BUY_MARKET:
        addBuyMarket(order);
        break;
      case SELL_MARKET:
        addSellMarket(order);
        break;
      case BUY_SELECT:
        addBuySelect(order);
        break;
      case SELL_SELECT:
        addSellSelect(order);
        break;
      case STOP_BUY_LIMIT:
        addStopBuyLimit(order);
        break;
      case STOP_SELL_LIMIT:
        addStopSellLimit(order);
        break;
      default:
    }
    processTriggeredOrders();
    checkCircuitBreaker();
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
    final ExecutionReportMessage executionReportMessage =
        ExecutionReportMessage.createAckCancelOrderExecutionReport(cancelOrder, instrumentPair);
    matcherToPublisherQueue.addGuaranteed(executionReportMessage);

    if (MarketStatus.CLOSE == marketStatus) {
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithCancelId(cancelOrder.getSenderCompId(),
          MsgType.ORDER_CANCEL_REQUEST, Long.toString(cancelOrder.getCancelId()), BusinessRejectReason.MARKET_IS_CLOSED, MARKET_IS_CLOSED,
          cancelOrder.getOrigOrderId(), cancelOrder.getSourceSeqNum(), cancelOrder.getSecondaryOrderId(), cancelOrder.getSecurityId(),
          cancelOrder.getCancelId()));
      return;
    }


    if (cancelOrder.getPriceInt() >= ARR_SIZE || cancelOrder.getPriceInt() < 0) {
      final Order tmpPtr = outOfBoundsOrderMap.get(cancelOrder.getOrigOrderId());
      if ((tmpPtr != null) && (tmpPtr.getUser().getId() == cancelOrder.getUser().getId())) {
        preOrderCheck.updateCancel(tmpPtr);
        matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelOrder.getCancelId(), tmpPtr,
            instrumentPair, cancelOrder, cancelOrder.getCancelType()));
        OrderObjectPool.returnObject(tmpPtr);
        CancelOrderObjectPool.returnObject(cancelOrder);
      } else {
        matcherToPublisherQueue
            .addGuaranteed(CancelRejectMessage.createCancelReject(cancelOrder, instrumentPair, CxlRejReason.UNKNOWN_ORDER));
        CancelOrderObjectPool.returnObject(cancelOrder);
      }

      return;
    }

    // try to use open orders cache to lookup order
    Order order = (cancelOrder.getOrigOrderId() > 0)
        ? cancelOrder.getUser().lookupOrder(cancelOrder.getOrigOrderId(), cancelOrder.getSecurityId(), cancelOrder.getSide())
        : cancelOrder.getUser().lookupOrderBySecondaryOrderId(cancelOrder.getSecondaryOrderId(), cancelOrder.getSecurityId(),
            cancelOrder.getSide());


    // if unable to lookup order, lookup TWAP special case
    if (order == null) {
      order = Context.getTimeTriggerThread().removeOrder(cancelOrder);

      if (order != null) {
        matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelOrder.getCancelId(), order,
            instrumentPair, cancelOrder, cancelOrder.getCancelType()));
        OrderObjectPool.returnObject(order);
        CancelOrderObjectPool.returnObject(cancelOrder);
        return;
      }
    }

    // if unable to lookup order, lookup ADL Maker special case
    if (order == null) {
      order = adlMakerContainer.removeOrder(cancelOrder);

      if (order != null) {
        matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelOrder.getCancelId(), order,
            instrumentPair, cancelOrder, cancelOrder.getCancelType()));
        OrderObjectPool.returnObject(order);
        CancelOrderObjectPool.returnObject(cancelOrder);
        return;
      }
    }

    // if unable to lookup order, attempt to scan for it
    if (order == null && Context.isScanForOrderEnabled())
      order = scanForOrder(cancelOrder);

    // if still unable to lookup order, look in out of bounds
    if (order == null) {
      order = outOfBoundsOrderMapBySecondaryOrderId.get(cancelOrder.getSecondaryOrderId());
    }

    if (order != null) {
      // check that cancel user must be the same user
      if ((order.getUser() != null) && (order.getUser().getId() != cancelOrder.getAccount())) {
        LOGGER.warn(LOG_FMT_4, "user does not match, cancelOrder=", cancelOrder, ORDER_EQ, order);

        matcherToPublisherQueue
            .addGuaranteed(CancelRejectMessage.createCancelReject(cancelOrder, instrumentPair, CxlRejReason.UNKNOWN_ORDER));
        CancelOrderObjectPool.returnObject(cancelOrder);
        return;
      }

      if (cancelOrder.getOrigOrderId() == 0)
        cancelOrder.setOrigOrderId(order.getOrderId());

      // auction
      if (MarketStatus.OPEN_AUCTION == marketStatus) {
        boolean rc = false;
        if (order.getSide() == Side.BUY)
          rc = auctionContainer.removeBuyLimit(order);
        else if (order.getSide() == Side.SELL)
          rc = auctionContainer.removeSellLimit(order);

        preOrderCheck.updateCancel(order);
        matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelOrder.getCancelId(), order,
            instrumentPair, cancelOrder, cancelOrder.getCancelType()));
        OrderObjectPool.returnObject(order);
        CancelOrderObjectPool.returnObject(cancelOrder);
        return;
      }

      if (order.getSide() == Side.BUY) {
        if (BUY_LIMIT == order.getType())
          removeBuyOrder(order);
        else if (STOP_BUY_LIMIT == order.getType()) {
          if (order.isStopTakeProfit())
            stopProfitContainer.removeBuyLimit(order);
          else if (order.isTrailingStop())
            trailingStopContainer.removeBuyLimit(order);
          else if (order.isADLMaker())
            adlMakerContainer.removeBuyLimit(order);
          else
            stopLimitContainer.removeBuyLimit(order);
        }
        // if BUY_MARKET, we don't remove from orderbook
      } else {
        if (SELL_LIMIT == order.getType())
          removeSellOrder(order);
        else if (STOP_SELL_LIMIT == order.getType()) {
          if (order.isStopTakeProfit())
            stopProfitContainer.removeSellLimit(order);
          else if (order.isTrailingStop())
            trailingStopContainer.removeSellLimit(order);
          else if (order.isADLMaker())
            adlMakerContainer.removeSellLimit(order);
          else
            stopLimitContainer.removeSellLimit(order);
        }
        // if SELL_MARKET, we don't remove from orderbook
      }
      preOrderCheck.updateCancel(order);
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelOrder.getCancelId(), order,
          instrumentPair, cancelOrder, cancelOrder.getCancelType()));
      OrderObjectPool.returnObject(order);
      CancelOrderObjectPool.returnObject(cancelOrder);

      return;
    }

    if (LogLevel.warn()) {
      LOGGER.warn(LOG_FMT_4, UNKNOWN_ORDER_CANCELORDER_EQ, cancelOrder);
    }
    matcherToPublisherQueue.addGuaranteed(CancelRejectMessage.createCancelReject(cancelOrder, instrumentPair, CxlRejReason.UNKNOWN_ORDER));
    CancelOrderObjectPool.returnObject(cancelOrder);
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

  private final void triggerStopLimitOrders(final int price) {
    if (price == 0)
      return;
    stopLimitContainer.getTriggeredBuyLimitList(price, triggeredOrders);
    stopLimitContainer.getTriggeredSellLimitList(price, triggeredOrders);
    stopProfitContainer.getTriggeredBuyLimitList(price, triggeredOrders);
    stopProfitContainer.getTriggeredSellLimitList(price, triggeredOrders);
    trailingStopContainer.getTriggeredOrderList(triggeredOrders);
  }

  private final void processTriggeredOrders() {
    if (rebuildInProgress || triggeredOrders.isEmpty())
      return;

    final Object[] tempArr = triggeredOrders.toArray();
    triggeredOrders.clear();

    for (final Object temp : tempArr) {
      final Order origOrder = (Order) temp;

      // copy set new order
      final Order order = OrderObjectPool.get();
      order.set(origOrder, origOrder.getOrderId(), origOrder.getSecondaryOrderId());

      // cancel old order from risk so UserOpenOrders is updated
      preOrderCheck.updateCancel(origOrder);

      // must ack the elimination of original order
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(origOrder, instrumentPair));

      // set new orderId to triggered order
      order.setOrigOrderId(origOrder.getOrderId());

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_2, STOPTRIGGEREDORDER_EQ, order);
      }

      // special logic for stop take profit orders
      if (order.isStopTakeProfit()) {
        if (order.getType() == STOP_SELL_LIMIT) {
          if (PROFIT_LIMIT == order.getTargetStrategy() || PROFIT_LIMIT_REDUCE_ONLY == order.getTargetStrategy()) {
            order.setType(SELL_LIMIT);
            order.setOrdType(OrdType.LIMIT);
          } else if (PROFIT_MARKET == order.getTargetStrategy() || PROFIT_MARKET_REDUCE_ONLY == order.getTargetStrategy()) {
            order.setType(SELL_MARKET);
            order.setOrdType(OrdType.MARKET);
          }
        } else if (order.getType() == STOP_BUY_LIMIT) {
          if (PROFIT_LIMIT == order.getTargetStrategy() || PROFIT_LIMIT_REDUCE_ONLY == order.getTargetStrategy()) {
            order.setType(BUY_LIMIT);
            order.setOrdType(OrdType.LIMIT);
          } else if (PROFIT_MARKET == order.getTargetStrategy() || PROFIT_MARKET_REDUCE_ONLY == order.getTargetStrategy()) {
            order.setType(BUY_MARKET);
            order.setOrdType(OrdType.MARKET);
          }
        }
      } else if (order.isTrailingStop()) { // trailing stop
        if (order.getType() == STOP_SELL_LIMIT || order.getType() == TRAILING_STOP_SELL_LIMIT) {
          if (TRAILING_STOP_MARKET == order.getTargetStrategy() || TRAILING_STOP_MARKET_REDUCE_ONLY == order.getTargetStrategy()) {
            order.setType(SELL_MARKET);
            order.setOrdType(OrdType.MARKET);
          } else {
            order.setType(SELL_LIMIT);
            order.setOrdType(OrdType.LIMIT);
          }
        } else if (order.getType() == STOP_BUY_LIMIT || order.getType() == TRAILING_STOP_BUY_LIMIT) {
          if (TRAILING_STOP_MARKET == order.getTargetStrategy() || TRAILING_STOP_MARKET_REDUCE_ONLY == order.getTargetStrategy()) {
            order.setType(BUY_MARKET);
            order.setOrdType(OrdType.MARKET);
          } else {
            order.setType(BUY_LIMIT);
            order.setOrdType(OrdType.LIMIT);
          }
        }
        if (order.isReduceOnly())
          order.setTargetStrategy(REDUCE_ONLY);
        else
          order.setTargetStrategy(0);
      } else { // regular stop limit
        if (order.getType() == STOP_SELL_LIMIT) {
          if (STOP_MARKET == order.getTargetStrategy() || STOP_MARKET_REDUCE_ONLY == order.getTargetStrategy()) {
            order.setType(SELL_MARKET);
            order.setOrdType(OrdType.MARKET);
          } else {
            order.setType(SELL_LIMIT);
            order.setOrdType(OrdType.LIMIT);
          }
        } else if (order.getType() == STOP_BUY_LIMIT) {
          if (STOP_MARKET == order.getTargetStrategy() || STOP_MARKET_REDUCE_ONLY == order.getTargetStrategy()) {
            order.setType(BUY_MARKET);
            order.setOrdType(OrdType.MARKET);
          } else {
            order.setType(BUY_LIMIT);
            order.setOrdType(OrdType.LIMIT);
          }
        }
      }

      order.setPriceInt(order.getStopPxInt());

      addOrder(order);
    }
  }

  public final int matchOnAsks(final Order takerOrder) {
    int lastPriceLevelVisited = 0;

    // TODO: Remove this check for crossing, used for debugging
    if (bidLevelCachePtrArr[0] > askLevelCachePtrArr[0] && bidLevelCachePtrArr[0] != 0 && askLevelCachePtrArr[0] != 0) {
      LOGGER.error(LOG_FMT_2, MATCHONASKS_CROSS_EQ, bidLevelCachePtrArr[0] + ASKLEVELCACHEPTRARR0_EQ, askLevelCachePtrArr[0]);
      throw new NullPointerException(MATCHONASKS_CROSS_EQ + bidLevelCachePtrArr[0] + ASKLEVELCACHEPTRARR0_EQ + askLevelCachePtrArr[0]);
    }

    // match
    while (askLevelCachePtrArr[0] != 0 && askLevelCachePtrArr[0] <= takerOrder.getPriceInt()) {
      final OrderBookPriceLevel priceLevel = bookArr[askLevelCachePtrArr[0]];
      Order tmpPtr = priceLevel.getHead();

      if (tmpPtr == null) {
        LOGGER.warn(LOG_FMT_2, "matchOnAsks head is null, askLevelCachePtrArr[0]=", askLevelCachePtrArr[0]);
        updateAskLevelCacheAfterOrderRemoval(askLevelCachePtrArr[0]);
      }

      while (tmpPtr != null) {
        lastPriceLevelVisited = tmpPtr.getPriceInt();
        boolean returnObject = false;
        if (takerOrder.getPriceInt() >= tmpPtr.getPriceInt()) {
          // cancel if self-trading
          if (PREVENT_SELF_TRADE && !takerOrder.isLmm() && (takerOrder.getAccount() != 8)
              && takerOrder.getAccount() == tmpPtr.getAccount()) {
            LOGGER.warn(LOG_FMT_4, "matchOnAsks cancel self-trading, takerOrder=", takerOrder, ", tmpPtr=", tmpPtr);
            cancelOrder(takerOrder);
            return lastPriceLevelVisited;
          } else if (tmpPtr.getType() == STOP_SELL_LIMIT) {
            LOGGER.warn(LOG_FMT_2, "matchOnAsks sell stop, tmpPtr=", tmpPtr);
          } else if (tmpPtr.getType() == STOP_BUY_LIMIT) {
            LOGGER.warn(LOG_FMT_2, "matchOnAsks buy stop, tmpPtr=", tmpPtr);
          } else if (takerOrder.getMatchQuantityLong(tmpPtr) < tmpPtr.getMatchQuantityLong(takerOrder)) {
            final long quantityFilled = takerOrder.getMatchQuantityLong(tmpPtr);
            tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
            takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled);
            filled(quantityFilled, tmpPtr, takerOrder, BUY_LIMIT);
            return lastPriceLevelVisited;
          } else {
            final long quantityFilled = tmpPtr.getMatchQuantityLong(takerOrder);
            tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
            takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled);
            filled(quantityFilled, tmpPtr, takerOrder, BUY_LIMIT);
            removeSellOrder(tmpPtr);

            if (takerOrder.getMatchQuantityLong(tmpPtr) == 0) {
              OrderObjectPool.returnObject(tmpPtr);
              return lastPriceLevelVisited;
            } else
              returnObject = true;
          }
        } else
          break;

        if (returnObject) {
          final Order next = tmpPtr.getNext();
          OrderObjectPool.returnObject(tmpPtr);
          tmpPtr = next;
        } else
          tmpPtr = tmpPtr.getNext();

        // if Liquidation order, recalc MarginRatio, if below 1 stop liquidating and eliminate order
        // this may have a performance hit
        if ((takerOrder.isLiquidation() && takerOrder instanceof LiquidationOrder)
            && isMarginLiquidationSatisfied(takerOrder.getUser(), (LiquidationOrder) takerOrder)) {
          return lastPriceLevelVisited;
        }

      }
    }

    return lastPriceLevelVisited;
  }

  public int matchOnAsksMarket(final Order takerOrder) {
    int lastPriceLevelVisited = 0;

    // match
    while (askLevelCachePtrArr[0] != 0) {
      final OrderBookPriceLevel priceLevel = bookArr[askLevelCachePtrArr[0]];
      Order tmpPtr = priceLevel.getHead();

      if (tmpPtr == null) {
        LOGGER.warn(LOG_FMT_2, "matchOnAsksMarket head is null, askLevelCachePtrArr[0]=", askLevelCachePtrArr[0]);
        updateAskLevelCacheAfterOrderRemoval(askLevelCachePtrArr[0]);
      }

      while (tmpPtr != null) {
        lastPriceLevelVisited = tmpPtr.getPriceInt();
        boolean returnObject = false;
        // cancel if self-trading
        if (PREVENT_SELF_TRADE && !takerOrder.isLmm() && (takerOrder.getAccount() != 8) && takerOrder.getAccount() == tmpPtr.getAccount()) {
          LOGGER.warn(LOG_FMT_4, "matchOnAsksMarket cancel self-trading, takerOrder=", takerOrder, ", tmpPtr=", tmpPtr);
          cancelOrder(takerOrder);
          return lastPriceLevelVisited;
        } else if (tmpPtr.getType() == STOP_SELL_LIMIT) {
          LOGGER.warn(LOG_FMT_2, "matchOnAsksMarket sell stop, tmpPtr=", tmpPtr);
        } else if (tmpPtr.getType() == STOP_BUY_LIMIT) {
          LOGGER.warn(LOG_FMT_2, "matchOnAsksMarket buy stop, tmpPtr=", tmpPtr);
        } else if (takerOrder.getMatchQuantityLong(tmpPtr) < tmpPtr.getMatchQuantityLong(takerOrder)) {
          final long quantityFilled = takerOrder.getMatchQuantityLong(tmpPtr);
          tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
          takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled);
          filled(quantityFilled, tmpPtr, takerOrder, BUY_MARKET);
          return lastPriceLevelVisited;
        } else {
          final long quantityFilled = tmpPtr.getMatchQuantityLong(takerOrder);
          tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
          takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled);
          filled(quantityFilled, tmpPtr, takerOrder, BUY_MARKET);
          removeSellOrder(tmpPtr);

          if (takerOrder.getMatchQuantityLong(tmpPtr) == 0) {
            OrderObjectPool.returnObject(tmpPtr);
            return lastPriceLevelVisited;
          } else
            returnObject = true;
        }

        if (returnObject) {
          final Order next = tmpPtr.getNext();
          OrderObjectPool.returnObject(tmpPtr);
          tmpPtr = next;
        } else
          tmpPtr = tmpPtr.getNext();
      }
    }
    // market sweeped, no liquidity remains
    if (takerOrder.getQty() > 0) {
      cancelOrder(takerOrder);
    }
    return lastPriceLevelVisited;
  }

  public int matchOnAskOnSelect(final Order takerOrder, final Order makerOrder) {
    final int lastPriceLevelVisited = makerOrder.getPriceInt();

    // match
    // cancel if self-trading
    if (PREVENT_SELF_TRADE && !takerOrder.isLmm() && (takerOrder.getAccount() != 8) && takerOrder.getAccount() == makerOrder.getAccount()) {
      LOGGER.warn(LOG_FMT_4, "matchOnAsksMarket cancel self-trading, takerOrder=", takerOrder, ", tmpPtr=", makerOrder);
      cancelOrder(takerOrder);
      return lastPriceLevelVisited;
    } else if (takerOrder.getMatchQuantityLong(makerOrder) < makerOrder.getMatchQuantityLong(takerOrder)) {
      final long quantityFilled = takerOrder.getMatchQuantityLong(makerOrder);
      makerOrder.setQuantityLong(makerOrder.getMatchQuantityLong(takerOrder) - quantityFilled);
      takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(makerOrder) - quantityFilled);
      filled(quantityFilled, makerOrder, takerOrder, BUY_SELECT);
      return lastPriceLevelVisited;
    } else {
      final long quantityFilled = makerOrder.getMatchQuantityLong(takerOrder);
      makerOrder.setQuantityLong(makerOrder.getMatchQuantityLong(takerOrder) - quantityFilled);
      takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(makerOrder) - quantityFilled);
      filled(quantityFilled, makerOrder, takerOrder, BUY_SELECT);
      removeSellOrder(makerOrder);

      if (takerOrder.getMatchQuantityLong(makerOrder) == 0) {
        OrderObjectPool.returnObject(makerOrder);
        return lastPriceLevelVisited;
      }
    }

    // market sweeped, no liquidity remains
    if (takerOrder.getQty() > 0) {
      cancelOrder(takerOrder);
    }
    return lastPriceLevelVisited;
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
    int lastPriceLevelVisited = 0;

    // match
    while (bidLevelCachePtrArr[0] != 0 && bidLevelCachePtrArr[0] >= takerOrder.getPriceInt()) {
      final OrderBookPriceLevel priceLevel = bookArr[bidLevelCachePtrArr[0]];
      Order tmpPtr = priceLevel.getHead();

      if (tmpPtr == null) {
        LOGGER.warn(LOG_FMT_2, "matchOnBids head is null, bidLevelCachePtrArr[0]=", bidLevelCachePtrArr[0]);
        updateBidLevelCacheAfterOrderRemoval(bidLevelCachePtrArr[0]);
      }

      while (tmpPtr != null) {
        lastPriceLevelVisited = tmpPtr.getPriceInt();
        boolean returnObject = false;
        if (takerOrder.getPriceInt() <= tmpPtr.getPriceInt()) {
          // cancel if self-trading
          if (PREVENT_SELF_TRADE && !takerOrder.isLmm() && (takerOrder.getAccount() != 8)
              && takerOrder.getAccount() == tmpPtr.getAccount()) {
            LOGGER.warn(LOG_FMT_4, "matchOnBids cancel self-trading, takerOrder=", takerOrder, ", tmpPtr=", tmpPtr);
            cancelOrder(takerOrder);
            return lastPriceLevelVisited;
          } else if (tmpPtr.getType() == STOP_BUY_LIMIT) {
            LOGGER.debug(LOG_FMT_2, "matchOnBids buy stop, tmpPtr=", tmpPtr);
          } else if (tmpPtr.getType() == STOP_SELL_LIMIT) {
            LOGGER.debug(LOG_FMT_2, "matchOnBids sell stop, tmpPtr=", tmpPtr);
          } else if (takerOrder.getMatchQuantityLong(tmpPtr) < tmpPtr.getMatchQuantityLong(takerOrder)) {
            final long quantityFilled = takerOrder.getMatchQuantityLong(tmpPtr);
            tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
            takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled);
            filled(quantityFilled, tmpPtr, takerOrder, SELL_LIMIT);
            return lastPriceLevelVisited;
          } else {
            final long quantityFilled = tmpPtr.getMatchQuantityLong(takerOrder);
            tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
            takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled);
            filled(quantityFilled, tmpPtr, takerOrder, SELL_LIMIT);
            removeBuyOrder(tmpPtr);

            if (takerOrder.getMatchQuantityLong(tmpPtr) == 0) {
              OrderObjectPool.returnObject(tmpPtr);
              return lastPriceLevelVisited;
            } else
              returnObject = true;
          }
        } else
          break;

        if (returnObject) {
          final Order next = tmpPtr.getNext();
          OrderObjectPool.returnObject(tmpPtr);
          tmpPtr = next;
        } else
          tmpPtr = tmpPtr.getNext();


        // if Liquidation order, recalc MarginRatio, if below 1 stop liquidating and eliminate order
        // this may have a performance hit
        if ((takerOrder.isLiquidation() && takerOrder instanceof LiquidationOrder)
            && isMarginLiquidationSatisfied(takerOrder.getUser(), (LiquidationOrder) takerOrder)) {
          return lastPriceLevelVisited;
        }

      }
    }

    return lastPriceLevelVisited;
  }

  public final int matchOnBidsMarket(final Order takerOrder) {
    int lastPriceLevelVisited = 0;

    // match
    while (bidLevelCachePtrArr[0] != 0) {
      final OrderBookPriceLevel priceLevel = bookArr[bidLevelCachePtrArr[0]];
      Order tmpPtr = priceLevel.getHead();

      if (tmpPtr == null) {
        LOGGER.warn(LOG_FMT_2, "matchOnBidsMarket head is null, bidLevelCachePtrArr[0]=", bidLevelCachePtrArr[0]);
        updateBidLevelCacheAfterOrderRemoval(bidLevelCachePtrArr[0]);
      }

      while (tmpPtr != null) {
        lastPriceLevelVisited = tmpPtr.getPriceInt();
        boolean returnObject = false;
        // cancel if self-trading
        if (PREVENT_SELF_TRADE && !takerOrder.isLmm() && (takerOrder.getAccount() != 8) && takerOrder.getAccount() == tmpPtr.getAccount()) {
          LOGGER.warn(LOG_FMT_4, "matchOnBidsMarket cancel self-trading, takerOrder=", takerOrder, ", tmpPtr=", tmpPtr);
          cancelOrder(takerOrder);
          return lastPriceLevelVisited;
        } else if (tmpPtr.getType() == STOP_BUY_LIMIT) {
          LOGGER.debug(LOG_FMT_2, "matchOnBidsMarket buy stop, tmpPtr=", tmpPtr);
        } else if (tmpPtr.getType() == STOP_SELL_LIMIT) {
          LOGGER.debug(LOG_FMT_2, "matchOnBidsMarket sell stop, tmpPtr=", tmpPtr);
        } else if (takerOrder.getMatchQuantityLong(tmpPtr) < tmpPtr.getMatchQuantityLong(takerOrder)) {
          final long quantityFilled = takerOrder.getMatchQuantityLong(tmpPtr);
          tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
          takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled);
          filled(quantityFilled, tmpPtr, takerOrder, SELL_MARKET);
          return lastPriceLevelVisited;
        } else {
          final long quantityFilled = tmpPtr.getMatchQuantityLong(takerOrder);
          tmpPtr.setQuantityLong(tmpPtr.getMatchQuantityLong(takerOrder) - quantityFilled);
          takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(tmpPtr) - quantityFilled);
          filled(quantityFilled, tmpPtr, takerOrder, SELL_MARKET);
          removeBuyOrder(tmpPtr);

          if (takerOrder.getMatchQuantityLong(tmpPtr) == 0) {
            OrderObjectPool.returnObject(tmpPtr);
            return lastPriceLevelVisited;
          } else
            returnObject = true;
        }

        if (returnObject) {
          final Order next = tmpPtr.getNext();
          OrderObjectPool.returnObject(tmpPtr);
          tmpPtr = next;
        } else
          tmpPtr = tmpPtr.getNext();
      }
    }
    // market sweeped, no liquidity remains
    if (takerOrder.getQty() > 0) {
      cancelOrder(takerOrder);
    }
    return lastPriceLevelVisited;
  }


  public final int matchOnBidOnSelect(final Order takerOrder, final Order makerOrder) {
    final int lastPriceLevelVisited = makerOrder.getPriceInt();

    // match
    // cancel if self-trading
    if (PREVENT_SELF_TRADE && !takerOrder.isLmm() && (takerOrder.getAccount() != 8) && takerOrder.getAccount() == makerOrder.getAccount()) {
      LOGGER.warn(LOG_FMT_4, "matchOnBidsMarket cancel self-trading, takerOrder=", takerOrder, ", tmpPtr=", makerOrder);
      cancelOrder(takerOrder);
      return lastPriceLevelVisited;
    } else if (takerOrder.getMatchQuantityLong(makerOrder) < makerOrder.getMatchQuantityLong(takerOrder)) {
      final long quantityFilled = takerOrder.getMatchQuantityLong(makerOrder);
      makerOrder.setQuantityLong(makerOrder.getMatchQuantityLong(takerOrder) - quantityFilled);
      takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(makerOrder) - quantityFilled);
      filled(quantityFilled, makerOrder, takerOrder, SELL_SELECT);
      return lastPriceLevelVisited;
    } else {
      final long quantityFilled = makerOrder.getMatchQuantityLong(takerOrder);
      makerOrder.setQuantityLong(makerOrder.getMatchQuantityLong(takerOrder) - quantityFilled);
      takerOrder.setQuantityLong(takerOrder.getMatchQuantityLong(makerOrder) - quantityFilled);
      filled(quantityFilled, makerOrder, takerOrder, SELL_SELECT);
      removeBuyOrder(makerOrder);

      if (takerOrder.getMatchQuantityLong(makerOrder) == 0) {
        OrderObjectPool.returnObject(makerOrder);
        return lastPriceLevelVisited;
      }
    }

    // market sweeped, no liquidity remains
    if (takerOrder.getQty() > 0) {
      cancelOrder(takerOrder);
    }
    return lastPriceLevelVisited;
  }

  public final boolean isBidsFillOrKill(final Order newPtr) {
    long quantity = 0;

    // traverse cached depth
    int i = 0;
    for (; i < CACHE_DEPTH; i++) {
      if (bidLevelCachePtrArr[i] == 0)
        return false;
      Order tmpPtr = bookArr[bidLevelCachePtrArr[i]].getHead();
      if (tmpPtr == null || (newPtr.getOrdType() != OrdType.MARKET && tmpPtr.getPriceInt() < newPtr.getPriceInt()))
        return false;

      while (tmpPtr != null) {
        if (newPtr.getOrdType() == OrdType.MARKET || newPtr.getPriceInt() <= tmpPtr.getPriceInt()) {
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

  private final void addBuyLimit(final Order newPtr) {
    // out of bound buy orders are rejected as they can lead to a crossed order book when resizing
    if (newPtr.getPriceInt() >= ARR_SIZE) {
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.PRICE_IS_OUT_OF_BOUNDS, PRICE_IS_OUT_OF_BOUNDS, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

      OrderObjectPool.returnObject(newPtr);
      return;
    }

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
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // ack
    if (!rebuildInProgress && publishAcks) {
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
    }

    // used for select id orders
    // todo write cleanup logic
    newPtr.setSelectId(newPtr.getOrderId());
    idToOrderMap.put(newPtr.getOrderId(), newPtr);

    // auction
    if (MarketStatus.OPEN_AUCTION == marketStatus) {
      auctionContainer.addBuyLimit(newPtr);
      return;
    }

    if (TimeInForce.FILL_OR_KILL == newPtr.getTimeInForce() && !isAsksFillOrKill(newPtr)) {
      // cancel old order from risk so UserOpenOrders is updated
      preOrderCheck.updateCancel(newPtr);
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // post only, aka limit maker, or CIRCUIT_BREAKER
    if ((TimeInForce.POST_ONLY == newPtr.getTimeInForce() || isCircuitBreaker()) && askLevelCachePtrArr[0] != 0
        && askLevelCachePtrArr[0] <= newPtr.getPriceInt()) {
      // cancel old order from risk so UserOpenOrders is updated
      preOrderCheck.updateCancel(newPtr);
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // match orders
    if (!rebuildInProgress && MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) {
      final int lastPriceLevelVisited = matchOnAsks(newPtr);
      triggerStopLimitOrders(lastPriceLevelVisited);
    }

    if (newPtr.getQuantityLong() <= 0) {
      OrderObjectPool.returnObject(newPtr);
      return;
    }
    if (TimeInForce.IMMEDIATE_OR_CANCEL == newPtr.getTimeInForce()) {
      // cancel old order from risk so UserOpenOrders is updated
      preOrderCheck.updateCancel(newPtr);
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // set best bid
    for (int i = 0; i < CACHE_DEPTH; i++) {
      if (bidLevelCachePtrArr[i] == 0) { // newPtr gets default index, since its null
        bidLevelCachePtrArr[i] = newPtr.getPriceInt();
        bidDepthLevelCacheCount++;
        break;
      } else if (newPtr.getPriceInt() == bidLevelCachePtrArr[i]) {
        break; // already have a node at this price
      } else if (newPtr.getPriceInt() > bidLevelCachePtrArr[i]) { // newPtr is in the middle
        for (int j = CACHE_DEPTH - 1; j > i; j--) {
          bidLevelCachePtrArr[j] = bidLevelCachePtrArr[j - 1];
        }
        bidLevelCachePtrArr[i] = newPtr.getPriceInt();
        bidDepthLevelCacheCount++;
        break;
      }
    }

    // max bidDepthLevelCacheCount if cache is full
    if (bidDepthLevelCacheCount > CACHE_DEPTH)
      bidDepthLevelCacheCount = CACHE_DEPTH;

    // set to array if no price for that index exists
    if (bookArr[newPtr.getPriceInt()].getHead() == null) {
      bookArr[newPtr.getPriceInt()].setHead(newPtr);
      bidDepth++;
      return;
    }

    // put at tail
    if (newPtr.isLmm()) {
      bookArr[newPtr.getPriceInt()].addToLmmTail(newPtr);
    } else {
      bookArr[newPtr.getPriceInt()].addToTail(newPtr);
    }
    bidDepth++;
  }

  private final void addSellLimit(final Order newPtr) {
    // out of bound buy orders are rejected as they can lead to a crossed order book when resizing
    if (newPtr.getPriceInt() >= ARR_SIZE) {
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.PRICE_IS_OUT_OF_BOUNDS, PRICE_IS_OUT_OF_BOUNDS, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

      OrderObjectPool.returnObject(newPtr);
      return;
    }

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
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // ack
    if (!rebuildInProgress && publishAcks) {
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
    }

    // used for select id orders
    // todo write cleanup logic
    newPtr.setSelectId(newPtr.getOrderId());
    idToOrderMap.put(newPtr.getOrderId(), newPtr);

    // auction
    if (MarketStatus.OPEN_AUCTION == marketStatus) {
      auctionContainer.addSellLimit(newPtr);
      return;
    }

    if (TimeInForce.FILL_OR_KILL == newPtr.getTimeInForce() && !isBidsFillOrKill(newPtr)) {
      // cancel old order from risk so UserOpenOrders is updated
      preOrderCheck.updateCancel(newPtr);
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      OrderObjectPool.returnObject(newPtr);
      return;
    }

    if ((TimeInForce.POST_ONLY == newPtr.getTimeInForce() || isCircuitBreaker()) && bidLevelCachePtrArr[0] != 0
        && bidLevelCachePtrArr[0] >= newPtr.getPriceInt()) {
      // cancel old order from risk so UserOpenOrders is updated
      preOrderCheck.updateCancel(newPtr);
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // match orders
    if (!rebuildInProgress && MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) {
      final int lastPriceLevelVisited = matchOnBids(newPtr);
      triggerStopLimitOrders(lastPriceLevelVisited);
    }

    if (newPtr.getQuantityLong() <= 0) {
      OrderObjectPool.returnObject(newPtr);
      return;
    }
    if (TimeInForce.IMMEDIATE_OR_CANCEL == newPtr.getTimeInForce()) {
      // cancel old order from risk so UserOpenOrders is updated
      preOrderCheck.updateCancel(newPtr);
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // set best ask
    for (int i = 0; i < CACHE_DEPTH; i++) {
      if (askLevelCachePtrArr[i] == 0) { // newPtr gets default index, since its null
        askLevelCachePtrArr[i] = newPtr.getPriceInt();
        askDepthLevelCacheCount++;
        break;
      } else if (newPtr.getPriceInt() == askLevelCachePtrArr[i]) {
        break; // already have a node at this price
      } else if (newPtr.getPriceInt() < askLevelCachePtrArr[i]) { // newPtr is in the middle
        for (int j = CACHE_DEPTH - 1; j > i; j--) {
          askLevelCachePtrArr[j] = askLevelCachePtrArr[j - 1];
        }
        askLevelCachePtrArr[i] = newPtr.getPriceInt();
        askDepthLevelCacheCount++;
        break;
      }
    }

    // max askDepthLevelCacheCount if cache is full
    if (askDepthLevelCacheCount > CACHE_DEPTH)
      askDepthLevelCacheCount = CACHE_DEPTH;

    // set to array if no price for that index exists
    if (bookArr[newPtr.getPriceInt()].getHead() == null) {
      bookArr[newPtr.getPriceInt()].setHead(newPtr);
      askDepth++;
      return;
    }

    // put at tail
    if (newPtr.isLmm()) {
      bookArr[newPtr.getPriceInt()].addToLmmTail(newPtr);
    } else {
      bookArr[newPtr.getPriceInt()].addToTail(newPtr);
    }

    askDepth++;
  }

  public final void addBuyMarket(final Order newPtr) {
    if (isCircuitBreaker()) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, MARKET_CIRCUIT_BREAKER, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.CIRCUIT_BREAKER, MARKET_CIRCUIT_BREAKER, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
      return;
    }

    final int marketOrderRiskPrice = newPtr.getPrice2Int() > 0 ? newPtr.getPrice2Int() : (int) (askLevelCachePtrArr[0] * 1.05);

    if ((askLevelCachePtrArr[0] == 0 || !preOrderCheck.checkOrder(newPtr, marketOrderRiskPrice))
        && marketStatus != MarketStatus.OPEN_AUCTION) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      matcherToPublisherQueue.addGuaranteed(
          BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE, Long.toString(newPtr.getOrderId()),
              askLevelCachePtrArr[0] == 0 ? BusinessRejectReason.NO_LIQUIDITY_AVAILABLE : BusinessRejectReason.FAILED_PRE_CREDIT_CHECK,
              askLevelCachePtrArr[0] == 0 ? NO_LIQUIDITY_AVAILABLE : FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(), newPtr.getSourceSeqNum(),
              newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // ack
    if (!rebuildInProgress && publishAcks) {
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
    }

    // auction
    if (MarketStatus.OPEN_AUCTION == marketStatus) {
      auctionContainer.addBuyLimit(newPtr);
      return;
    }

    if (TimeInForce.FILL_OR_KILL == newPtr.getTimeInForce() && !isAsksFillOrKill(newPtr)) {
      // cancel old order from risk so UserOpenOrders is updated
      preOrderCheck.updateCancel(newPtr);
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // match orders
    if (MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) {
      final int lastPriceLevelVisited = matchOnAsksMarket(newPtr);
      triggerStopLimitOrders(lastPriceLevelVisited);
    }
    OrderObjectPool.returnObject(newPtr);
  }

  public final void addSellMarket(final Order newPtr) {
    if (isCircuitBreaker()) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, MARKET_CIRCUIT_BREAKER, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.CIRCUIT_BREAKER, MARKET_CIRCUIT_BREAKER, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
      return;
    }

    final int marketOrderRiskPrice = newPtr.getPrice2Int() > 0 ? newPtr.getPrice2Int() : bidLevelCachePtrArr[0];

    if ((bidLevelCachePtrArr[0] == 0 || !preOrderCheck.checkOrder(newPtr, marketOrderRiskPrice))
        && marketStatus != MarketStatus.OPEN_AUCTION) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      matcherToPublisherQueue.addGuaranteed(
          BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE, Long.toString(newPtr.getOrderId()),
              bidLevelCachePtrArr[0] == 0 ? BusinessRejectReason.NO_LIQUIDITY_AVAILABLE : BusinessRejectReason.FAILED_PRE_CREDIT_CHECK,
              bidLevelCachePtrArr[0] == 0 ? NO_LIQUIDITY_AVAILABLE : FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(), newPtr.getSourceSeqNum(),
              newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

      return;
    }

    // ack
    if (!rebuildInProgress && publishAcks) {
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
    }

    // auction
    if (MarketStatus.OPEN_AUCTION == marketStatus) {
      auctionContainer.addSellLimit(newPtr);
      return;
    }

    if (TimeInForce.FILL_OR_KILL == newPtr.getTimeInForce() && !isBidsFillOrKill(newPtr)) {
      // cancel old order from risk so UserOpenOrders is updated
      preOrderCheck.updateCancel(newPtr);
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // match orders
    if (MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) {
      final int lastPriceLevelVisited = matchOnBidsMarket(newPtr);
      triggerStopLimitOrders(lastPriceLevelVisited);
    }
    OrderObjectPool.returnObject(newPtr);
  }


  public final void addBuySelect(final Order newPtr) {
    if (isCircuitBreaker()) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, MARKET_CIRCUIT_BREAKER, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.CIRCUIT_BREAKER, MARKET_CIRCUIT_BREAKER, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
      return;
    }

    final Order counterOrder = idToOrderMap.get(newPtr.getSelectId());
    if (counterOrder == null || counterOrder.getQty() <= 0) {
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.SELECT_ORDER_NOT_FOUND, INVALID_ORDER_SELECT, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
      return;
    }

    final int marketOrderRiskPrice = counterOrder.getPrice2Int();
    if ((askLevelCachePtrArr[0] == 0 || !preOrderCheck.checkOrder(newPtr, marketOrderRiskPrice))
        && marketStatus != MarketStatus.OPEN_AUCTION) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      matcherToPublisherQueue.addGuaranteed(
          BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE, Long.toString(newPtr.getOrderId()),
              askLevelCachePtrArr[0] == 0 ? BusinessRejectReason.NO_LIQUIDITY_AVAILABLE : BusinessRejectReason.FAILED_PRE_CREDIT_CHECK,
              askLevelCachePtrArr[0] == 0 ? NO_LIQUIDITY_AVAILABLE : FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(), newPtr.getSourceSeqNum(),
              newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // ack
    if (!rebuildInProgress && publishAcks) {
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
    }

    // auction
    if (MarketStatus.OPEN_AUCTION == marketStatus) {
      auctionContainer.addBuyLimit(newPtr);
      return;
    }

    // match orders
    if (MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) {
      final int lastPriceLevelVisited = matchOnAskOnSelect(newPtr, counterOrder);
      triggerStopLimitOrders(lastPriceLevelVisited);
    }
    OrderObjectPool.returnObject(newPtr);
  }

  public final void addSellSelect(final Order newPtr) {
    if (isCircuitBreaker()) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, MARKET_CIRCUIT_BREAKER, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.CIRCUIT_BREAKER, MARKET_CIRCUIT_BREAKER, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
      return;
    }

    final Order counterOrder = idToOrderMap.get(newPtr.getSelectId());

    if (counterOrder == null || counterOrder.getQty() <= 0) {
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.SELECT_ORDER_NOT_FOUND, INVALID_ORDER_SELECT, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
      return;
    }

    final int marketOrderRiskPrice = counterOrder.getPrice2Int();
    if ((bidLevelCachePtrArr[0] == 0 || !preOrderCheck.checkOrder(newPtr, marketOrderRiskPrice))
        && marketStatus != MarketStatus.OPEN_AUCTION) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      matcherToPublisherQueue.addGuaranteed(
          BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE, Long.toString(newPtr.getOrderId()),
              bidLevelCachePtrArr[0] == 0 ? BusinessRejectReason.NO_LIQUIDITY_AVAILABLE : BusinessRejectReason.FAILED_PRE_CREDIT_CHECK,
              bidLevelCachePtrArr[0] == 0 ? NO_LIQUIDITY_AVAILABLE : FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(), newPtr.getSourceSeqNum(),
              newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

      return;
    }

    // ack
    if (!rebuildInProgress && publishAcks) {
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
    }

    // auction
    if (MarketStatus.OPEN_AUCTION == marketStatus) {
      auctionContainer.addSellLimit(newPtr);
      return;
    }

    // match orders
    if (MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) {
      final int lastPriceLevelVisited = matchOnBidOnSelect(newPtr, counterOrder);
      triggerStopLimitOrders(lastPriceLevelVisited);
    }
    OrderObjectPool.returnObject(newPtr);
  }

  public final void addStopBuyLimit(final Order newPtr) {
    if (isCircuitBreaker()) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, MARKET_CIRCUIT_BREAKER, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.CIRCUIT_BREAKER, MARKET_CIRCUIT_BREAKER, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
      return;
    }

    if (newPtr.getStopPxInt() <= 0 && !newPtr.isStopTakeProfitMarket() && !newPtr.isTrailingStopMarket()) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_4, REJECT_ORDER_EQ, STOP_PRICE_IS_MISSING, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.STOP_PRICE_IS_MISSING, STOP_PRICE_IS_MISSING, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
      return;
    }

    if (!rebuildInProgress) {

      // reject orders that would be triggered immediately
      if (Context.isRejectImmediateTriggeredEnabled()) {
        if (newPtr.isStopTakeProfit()) { // stop profit
          if (last <= newPtr.getPriceInt()) {
            // ack
            if (Context.isAckRejectMessages()) {
              final ExecutionReportMessage executionReportMessage =
                  ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
              matcherToPublisherQueue.addGuaranteed(executionReportMessage);
            }

            matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
                Long.toString(newPtr.getOrderId()), BusinessRejectReason.NOT_AUTHORIZED, ORDER_WOULD_IMMEDIATELY_TRIGGER,
                newPtr.getOrderId(), newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

            return;
          }
        } else if (newPtr.isTrailingStop()) { // stop profit

        } else { // regular stop
          if (last >= newPtr.getPriceInt()) {
            // ack
            if (Context.isAckRejectMessages()) {
              final ExecutionReportMessage executionReportMessage =
                  ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
              matcherToPublisherQueue.addGuaranteed(executionReportMessage);
            }

            matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
                Long.toString(newPtr.getOrderId()), BusinessRejectReason.NOT_AUTHORIZED, ORDER_WOULD_IMMEDIATELY_TRIGGER,
                newPtr.getOrderId(), newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
            return;
          }
        }
      }


      if (!preOrderCheck.checkOrder(newPtr, newPtr.getStopPxInt())) {
        // ack
        if (Context.isAckRejectMessages()) {
          final ExecutionReportMessage executionReportMessage =
              ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
          matcherToPublisherQueue.addGuaranteed(executionReportMessage);
        }

        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(newPtr.getOrderId()), BusinessRejectReason.NOT_AUTHORIZED, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
            newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

        OrderObjectPool.returnObject(newPtr);
        return;
      }
    }

    // out of bound buy orders are rejected as they can lead to a crossed order book when resizing
    if (newPtr.getPriceInt() >= ARR_SIZE) {
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.PRICE_IS_OUT_OF_BOUNDS, PRICE_IS_OUT_OF_BOUNDS, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // ack
    if (!rebuildInProgress) {
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
    }

    if (newPtr.getQuantityLong() <= 0) {
      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // add to container
    if (newPtr.isStopTakeProfit())
      stopProfitContainer.addBuyLimit(newPtr);
    else if (newPtr.isTrailingStop())
      trailingStopContainer.addBuyLimit(newPtr);
    else
      stopLimitContainer.addBuyLimit(newPtr);
  }

  public final void addStopSellLimit(final Order newPtr) {
    if (isCircuitBreaker()) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_3, REJECT_ORDER_EQ, MARKET_CIRCUIT_BREAKER, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.CIRCUIT_BREAKER, MARKET_CIRCUIT_BREAKER, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
      return;
    }

    if (newPtr.getStopPxInt() <= 0 && !newPtr.isStopTakeProfitMarket() && !newPtr.isTrailingStopMarket()) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_4, REJECT_ORDER_EQ, STOP_PRICE_IS_MISSING, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.STOP_PRICE_IS_MISSING, STOP_PRICE_IS_MISSING, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

      return;
    }

    if (!rebuildInProgress) {

      // reject orders that would be triggered immediately
      if (Context.isRejectImmediateTriggeredEnabled()) {
        if (newPtr.isStopTakeProfit()) { // stop profit
          if (last >= newPtr.getPriceInt()) {
            // ack
            if (Context.isAckRejectMessages()) {
              final ExecutionReportMessage executionReportMessage =
                  ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
              matcherToPublisherQueue.addGuaranteed(executionReportMessage);
            }

            matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
                Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, ORDER_WOULD_IMMEDIATELY_TRIGGER,
                newPtr.getOrderId(), newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
            return;
          }
        } else if (newPtr.isTrailingStop()) { // stop profit

        } else { // regular stop
          if ((last != 0) && (last <= newPtr.getPriceInt())) {
            // ack
            if (Context.isAckRejectMessages()) {
              final ExecutionReportMessage executionReportMessage =
                  ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
              matcherToPublisherQueue.addGuaranteed(executionReportMessage);
            }

            matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
                Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, ORDER_WOULD_IMMEDIATELY_TRIGGER,
                newPtr.getOrderId(), newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));
            return;
          }
        }
      }


      if (!preOrderCheck.checkOrder(newPtr, newPtr.getStopPxInt())) {
        // ack
        if (Context.isAckRejectMessages()) {
          final ExecutionReportMessage executionReportMessage =
              ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
          matcherToPublisherQueue.addGuaranteed(executionReportMessage);
        }

        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
            newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId()));

        OrderObjectPool.returnObject(newPtr);
        return;
      }
    }

    // if out of bounds we keep in separate data structure and ignore it
    if (newPtr.getPriceInt() >= ARR_SIZE) {
      outOfBoundsOrderMap.put(newPtr.getOrderId(), newPtr);
      outOfBoundsOrderMapBySecondaryOrderId.put(newPtr.getSecondaryOrderId(), newPtr);
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      return;
    }

    // ack
    if (!rebuildInProgress) {
      final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
      matcherToPublisherQueue.addGuaranteed(executionReportMessage);
    }

    if (newPtr.getQuantityLong() <= 0) {
      OrderObjectPool.returnObject(newPtr);
      return;
    }

    // add to container
    if (newPtr.isStopTakeProfit())
      stopProfitContainer.addSellLimit(newPtr);
    else if (newPtr.isTrailingStop())
      trailingStopContainer.addSellLimit(newPtr);
    else
      stopLimitContainer.addSellLimit(newPtr);
  }

  private final void updateBidLevelCacheAfterOrderRemoval(final int priceLevel) {
    // if best bid/ask, find and set next bidPtr/askPtr
    for (int i = 0; i < CACHE_DEPTH; i++) {
      if (bidLevelCachePtrArr[i] == 0)
        break;
      if (bidLevelCachePtrArr[i] == priceLevel) {
        // if price level is cached, and there are no more orders at that level remove it from
        // cache
        if (bookArr[priceLevel].getHead() == null) {
          bidDepthLevelCacheCount--;

          for (int j = i; j < CACHE_DEPTH - 1; j++) {
            bidLevelCachePtrArr[j] = bidLevelCachePtrArr[j + 1];
            if (bidLevelCachePtrArr[j] == 0)
              break;
          }
          bidLevelCachePtrArr[CACHE_DEPTH - 1] = 0;
          // find the next element that will be the last node of the cache
          if (bidDepthLevelCacheCount >= CACHE_DEPTH - 1 && bidLevelCachePtrArr[CACHE_DEPTH - 2] != 0) {
            for (int j = bidLevelCachePtrArr[CACHE_DEPTH - 2] - 1; j >= 0; j--) {
              if (bookArr[j].getHead() != null) {
                bidLevelCachePtrArr[CACHE_DEPTH - 1] = bookArr[j].getHead().getPriceInt();
                bidDepthLevelCacheCount = CACHE_DEPTH;
                break;
              }
            }
          }
        }
        break;
      }
    }
    // if bidLevelCachePtrArr is empty, attempt to set bidPtr/askPtr
    if (bidLevelCachePtrArr[0] == 0 && bidDepth > 0) {
      for (int i = priceLevel - 1; i >= 0; i--) {
        if (bookArr[i].getHead() != null) {
          bidLevelCachePtrArr[0] = bookArr[i].getHead().getPriceInt();
          bidDepthLevelCacheCount++;
          break;
        }
      }
    }
  }

  private final void removeBuyOrder(final Order tmpPtr) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug(LOG_FMT_2, REMOVE_BUY_ORDER_EQ, tmpPtr);

    // remove from outOfBoundsOrderMap
    if (tmpPtr.getPriceInt() >= bookArr.length) {
      outOfBoundsOrderMap.remove(tmpPtr.getOrderId());
      outOfBoundsOrderMapBySecondaryOrderId.remove(tmpPtr.getSecondaryOrderId());
      return;
    }

    // remove from bookArr
    bookArr[tmpPtr.getPriceInt()].remove(tmpPtr);

    if (OrdType.LIMIT == tmpPtr.getOrdType())
      bidDepth--;

    // update cache
    updateBidLevelCacheAfterOrderRemoval(tmpPtr.getPriceInt());
  }

  private final void updateAskLevelCacheAfterOrderRemoval(final int priceLevel) {
    for (int i = 0; i < CACHE_DEPTH; i++) {
      if (askLevelCachePtrArr[i] == 0)
        break;
      if (askLevelCachePtrArr[i] == priceLevel) {
        // if price level is cached, and there are no more orders at that level remove it from
        // cache
        if (bookArr[priceLevel].getHead() == null) {
          askDepthLevelCacheCount--;
          for (int j = i; j < CACHE_DEPTH - 1; j++) {
            askLevelCachePtrArr[j] = askLevelCachePtrArr[j + 1];
            if (askLevelCachePtrArr[j] == 0)
              break;
          }
          askLevelCachePtrArr[CACHE_DEPTH - 1] = 0;
          // find the next element that will be the last node of the cache
          if (askDepthLevelCacheCount >= CACHE_DEPTH - 1 && askLevelCachePtrArr[CACHE_DEPTH - 2] != 0) {
            for (int j = askLevelCachePtrArr[CACHE_DEPTH - 2] + 1; j < bookArr.length; j++) {
              if (bookArr[j].getHead() != null) {
                askLevelCachePtrArr[CACHE_DEPTH - 1] = bookArr[j].getHead().getPriceInt();
                askDepthLevelCacheCount = CACHE_DEPTH;
                break;
              }
            }
          }
        }
        break;
      }
    }

    // if askLevelCachePtrArr is empty, attempt to set bidPtr/askPtr
    if (askLevelCachePtrArr[0] == 0 && askDepth > 0) {
      for (int i = priceLevel + 1; i < bookArr.length; i++) {
        if (bookArr[i].getHead() != null) {
          askLevelCachePtrArr[0] = bookArr[i].getHead().getPriceInt();
          askDepthLevelCacheCount++;
          break;
        }
      }
    }
  }

  private final void removeSellOrder(final Order tmpPtr) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug(LOG_FMT_2, REMOVE_SELL_ORDER_EQ, tmpPtr);

    // remove from outOfBoundsOrderMap
    if (tmpPtr.getPriceInt() >= bookArr.length) {
      outOfBoundsOrderMap.remove(tmpPtr.getOrderId());
      outOfBoundsOrderMapBySecondaryOrderId.remove(tmpPtr.getSecondaryOrderId());
      return;
    }

    // remove from bookArr
    bookArr[tmpPtr.getPriceInt()].remove(tmpPtr);

    if (OrdType.LIMIT == tmpPtr.getOrdType())
      askDepth--;

    // update cache
    updateAskLevelCacheAfterOrderRemoval(tmpPtr.getPriceInt());
  }

  public final AssetGroup allocateGroupAssets(final long quantityFilled, final Order makerOrder, final Order takerOrder) {
    final AssetGroup sellGroup = AssetGroupCache.get(makerOrder.getGroupAssetId());
    if (sellGroup == null) {
      LOGGER.error(LOG_FMT_2, "allocateGroupAssets AssetGroup not found=", makerOrder);
      return null;
    }

    final int takerUserId = takerOrder.getUser().getId();
    final int makerUserId = makerOrder.getUser().getId();

    if (makerOrder.getQuantityLong() == 0) { // taker filled all of group
      sellGroup.setOwnerUserId(takerUserId);
      final Position makerPosition = makerOrder.getUser().getPosition((int) sellGroup.getSecurityId());
      final Position takerPosition = takerOrder.getUser().getPosition((int) sellGroup.getSecurityId());
      //update groupId in positions
      for (long[] assetTaken : sellGroup.getAssetIdGroupTreeSet()) {
        long[] asset = {assetTaken[0], assetTaken[1], sellGroup.getId()};
        makerPosition.getAssetIdtreeSet().remove(asset);
        takerPosition.addAssetId(assetTaken[0], (int) assetTaken[1], sellGroup.getId());
      }

      matcherToPublisherQueue.add(sellGroup);
      return sellGroup;
    }

    // split group moving quantityFilled assets to taker group
    final AssetGroup takerAssetGroup = new AssetGroup();
    takerAssetGroup.copySet(sellGroup);
    takerAssetGroup.setUpdateType(com.solfini.sbe.encoder.UpdateType.POST);
    takerAssetGroup.setId(0);
    takerAssetGroup.setGroupAssetId(0);
    takerAssetGroup.setOwnerUserId(takerUserId);

    final long[][] assetsTaken = new long[(int) quantityFilled][];

    final ConcurrentSkipListSet<long[]> set = sellGroup.getAssetIdGroupTreeSet();
    long allocated = 0;
    for (; allocated < quantityFilled; allocated++) {
      long[] value = set.pollFirst();
      if (value == null) {
        LOGGER.error(LOG_FMT_2, "allocateGroupAssets Asset not found=", sellGroup);
        break;
      }
      takerAssetGroup.addAssetId(value[0], (int) value[1]);
      long[] asset = {value[0], value[1], sellGroup.getId()};
      assetsTaken[(int) allocated] = asset;
    }
    //todo is this needed
    if (allocated < quantityFilled) { // try using otherGroups with the same seller and securityId
      LOGGER.error(LOG_FMT_2, "allocateGroupAssets allocated=", allocated, ", quantityFilled=", quantityFilled, ", sellGroup=", sellGroup);
      final Collection<AssetGroup> otherGroups = AssetGroupCache.getByUserId(makerUserId, id);
      for (final AssetGroup otherGroup : otherGroups) {
        final ConcurrentSkipListSet<long[]> set2 = otherGroup.getAssetIdGroupTreeSet();
        for (; allocated < quantityFilled; allocated++) {
          long[] value = set2.pollFirst();
          if (value == null) {
            LOGGER.error(LOG_FMT_2, "allocateGroupAssets Asset not found=", otherGroup);
            break;
          }
          takerAssetGroup.addAssetId(value[0], (int) value[1]);
          long[] asset = {value[0], value[1], otherGroup.getId()};
          assetsTaken[(int) allocated] = asset;
        }
        matcherToPublisherQueue.add(otherGroup);

        if (allocated == quantityFilled)
          break;
      }
    }

    // publish changes
    AssetGroupCache.onModel(takerAssetGroup);
    matcherToPublisherQueue.add(sellGroup);
    //update groupId in positions with new group id
    if (!takerAssetGroup.getAssetIdGroupTreeSet().isEmpty()) {
      //update groupId in positions with new group id
      final Position makerPosition = makerOrder.getUser().getPosition((int) sellGroup.getSecurityId());
      for (long[] takenAsset : assetsTaken) {
        makerPosition.getAssetIdtreeSet().remove(takenAsset);
      }
      final Position takerPosition = makerOrder.getUser().getPosition((int) sellGroup.getSecurityId());
      for (long[] takenAsset : assetsTaken) {
        takenAsset[2] = takerAssetGroup.getId();
        takerPosition.getAssetIdtreeSet().add(takenAsset);
      }
    }

    return takerAssetGroup;
  }

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

    long takerGroupAssetId = makerOrder.getGroupAssetId();
    if (makerOrder.getGroupAssetId() > 0) {
      final AssetGroup newAssetGroup = allocateGroupAssets(quantityFilled, makerOrder, takerOrder);
      if (newAssetGroup != null)
        takerGroupAssetId = newAssetGroup.getGroupAssetId();
    }

    final ExecutionReportMessage execMaker =
        ExecutionReportMessage.createTradeExecutionReport(makerOrder, instrumentPair, makerOrder.getPrice(), makerOrder.getPriceScale(),
            quantityFilled, instrumentPair.getQuantityScale(), filledCountGlobal, filledCount, takerOrder, takerUserId, false,
            makerOrder.getAssetId(), makerOrder.getTokenId(), makerOrder.getGroupAssetId(), takerOrder.getSelectId());
    final ExecutionReportMessage execTaker =
        ExecutionReportMessage.createTradeExecutionReport(takerOrder, instrumentPair, makerOrder.getPrice(), makerOrder.getPriceScale(),
            quantityFilled, instrumentPair.getQuantityScale(), filledCountGlobal, filledCount, takerOrder, makerUserId, false,
            makerOrder.getAssetId(), makerOrder.getTokenId(), takerGroupAssetId, takerOrder.getSelectId());

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

    long takerGroupAssetId = makerOrder.getGroupAssetId();
    if (makerOrder.getGroupAssetId() > 0) {
      final AssetGroup newAssetGroup = allocateGroupAssets(quantityFilled, makerOrder, takerOrder);
      if (newAssetGroup != null)
        takerGroupAssetId = newAssetGroup.getGroupAssetId();
    }

    final ExecutionReportMessage execMaker =
        ExecutionReportMessage.createTradeExecutionReport(makerOrder, instrumentPair, makerOrder.getPrice(), makerOrder.getPriceScale(),
            quantityFilled, instrumentPair.getQuantityScale(), filledCountGlobal, filledCount, takerOrder, takerUserId, false,
            makerOrder.getAssetId(), makerOrder.getTokenId(), makerOrder.getGroupAssetId(), takerOrder.getSelectId());
    final ExecutionReportMessage execTaker =
        ExecutionReportMessage.createTradeExecutionReport(takerOrder, instrumentPair, makerOrder.getPrice(), makerOrder.getPriceScale(),
            quantityFilled, instrumentPair.getQuantityScale(), filledCountGlobal, filledCount, takerOrder, makerUserId, false,
            makerOrder.getAssetId(), makerOrder.getTokenId(), takerGroupAssetId, takerOrder.getSelectId());
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
      case OPEN_AUCTION:
        this.marketStatus = marketStatus;
        if (MarketStatus.DR_MODE != Context.getMarketStatus()) {
          auctionContainer.openAuction(this);
        }
        break;
      case CLOSE_AUCTION:
        this.marketStatus = marketStatus;
        if (MarketStatus.DR_MODE != Context.getMarketStatus()) {
          auctionContainer.closeAuction(this);
        }
        break;
      case CANCEL_AUCTION:
        this.marketStatus = marketStatus;
        if (MarketStatus.DR_MODE != Context.getMarketStatus()) {
          auctionContainer.cancelAuction(this);
        }
        this.marketStatus = MarketStatus.OPEN;
        instrumentPair.setMarketStatus(MarketStatus.OPEN);
        break;
      default:
        break;
    }
    instrumentPair.setMarketStatus(marketStatus);
  }

  @Override
  public final void expireLiveSessionOrders() {
    final long cancelPriority = 0;
    for (int i = 0; i < bookArr.length; i++) { // this will take a while...
      Order tmp = null;
      Order next = null;
      if (bookArr[i].getHead() != null) {
        tmp = bookArr[i].getHead();
        while (tmp != null) {
          if (TimeInForce.DAY == tmp.getTimeInForce()) {
            next = tmp.getNext();
            final long cancelId = tmp.getOrderId();
            final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
            cancelOrder.set(tmp, cancelId, cancelPriority);
            cancelOrder.setUser(tmp.getUser());
            cancelOrder(cancelOrder);
            tmp = next;
          } else
            tmp = tmp.getNext();
        }
      }
    }
  }

  @Override
  public final void restate(final long snapId, final Message causingMessage) {
    final ExecRestatementReason reason = ExecRestatementReason.OTHER;

    // send initial to indicate reset
    final Order order = OrderMatchingThreadObjectPool.get();
    order.setSenderCompId(DEFAULT_SENDER_COMP);
    order.setClOrdId(ZERO_STR);
    order.setSecurityId(instrumentPair.getId());
    order.setPrice(0, (short) 0);
    order.setQty(0, (short) 0);
    order.setPriceInt(0);
    order.setOrdType(OrdType.PREVIOUSLY_INDICATED);
    order.setSide(Side.BUY);
    order.setAccount(UserCache.getExchangeUser().getId());
    order.setUser(UserCache.getExchangeUser());
    order.setSnapId(snapId);
    matcherToPublisherQueue
        .addGuaranteed(ExecutionReportMessage.createRestateExecutionReport(order, instrumentPair, reason, snapId, causingMessage));

    if (bidLevelCachePtrArr.length > 0 && bidLevelCachePtrArr[0] == 0 && askLevelCachePtrArr.length > 0 && askLevelCachePtrArr[0] == 0) {
      // if order book is empty don't do anything
    } else if (bidLevelCachePtrArr.length > 0 && bidLevelCachePtrArr[bidLevelCachePtrArr.length - 1] == 0 && askLevelCachePtrArr.length > 0
        && askLevelCachePtrArr[askLevelCachePtrArr.length - 1] == 0) {
      // if order book is completely cached, use cache only
      for (int i = bidLevelCachePtrArr.length - 1; i >= 0; i--) {
        final int price = bidLevelCachePtrArr[i];
        if (price > 0) {
          Order tmp = bookArr[price].getHead();
          if (tmp != null) {
            while (tmp != null) {
              matcherToPublisherQueue
                  .add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
              tmp = tmp.getNext();
            }
          }
        }
      }
      for (int i = 0; i < askLevelCachePtrArr.length; i++) {
        final int price = askLevelCachePtrArr[i];
        if (price > 0) {
          Order tmp = bookArr[price].getHead();
          if (tmp != null) {
            while (tmp != null) {
              matcherToPublisherQueue
                  .add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
              tmp = tmp.getNext();
            }
          }
        }
      }
    } else {
      // restate scan all
      for (int i = 0; i < bookArr.length; i++) { // this will take a while...
        Order tmp = bookArr[i].getHead();
        if (tmp != null) {
          while (tmp != null) {
            matcherToPublisherQueue
                .add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
            tmp = tmp.getNext();
          }
        }
      }
    }

    // restate stop limit orders
    final TreeSet<Order> buyTreeSet = stopLimitContainer.getBuyTreeSet();
    for (final Order tmp : buyTreeSet) {
      matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
    }

    final TreeSet<Order> sellTreeSet = stopLimitContainer.getSellTreeSet();
    for (final Order tmp : sellTreeSet) {
      matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
    }

    // restate stop profit limit orders
    final TreeSet<Order> buyTreeSet2 = stopProfitContainer.getBuyTreeSet();
    for (final Order tmp : buyTreeSet2) {
      matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
    }

    final TreeSet<Order> sellTreeSet2 = stopProfitContainer.getSellTreeSet();
    for (final Order tmp : sellTreeSet2) {
      matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
    }

    // restate auction limit orders
    final ConcurrentSkipListSet<Order> buyTreeSet3 = auctionContainer.getBuyTreeSet();
    for (final Order tmp : buyTreeSet3) {
      matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
    }

    final ConcurrentSkipListSet<Order> sellTreeSet3 = auctionContainer.getSellTreeSet();
    for (final Order tmp : sellTreeSet3) {
      matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
    }

    // restate auction limit orders
    final TreeSet<Order> buyTreeSet4 = trailingStopContainer.getBuyTreeSet();
    for (final Order tmp : buyTreeSet4) {
      matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
    }

    final TreeSet<Order> sellTreeSet4 = trailingStopContainer.getSellTreeSet();
    for (final Order tmp : sellTreeSet4) {
      matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
    }


    // restate outOfBounds orders
    final List<Order> outOfBoundsList = new ArrayList<>(outOfBoundsOrderMap.values());
    Collections.sort(outOfBoundsList, orderComparator);
    for (final Order tmp : outOfBoundsList) {
      if (tmp == null)
        continue;
      matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
    }
  }

  private void cancelOrder(final CancelReplaceOrder cancelReplaceOrder) {
    final ExecutionReportMessage executionReportMessage =
        ExecutionReportMessage.createAckCancelOrderExecutionReport(cancelReplaceOrder, instrumentPair);
    matcherToPublisherQueue.addGuaranteed(executionReportMessage);

    if (MarketStatus.CLOSE == marketStatus) {
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithCancelReplacelId(
          cancelReplaceOrder.getSenderCompId(), MsgType.ORDER_CANCEL_REPLACE_REQUEST, Long.toString(cancelReplaceOrder.getCancelId()),
          BusinessRejectReason.MARKET_IS_CLOSED, MARKET_IS_CLOSED, cancelReplaceOrder.getOrigOrderId(),
          cancelReplaceOrder.getSourceSeqNum(), cancelReplaceOrder.getSecondaryOrderId(), cancelReplaceOrder.getSecurityId(),
          cancelReplaceOrder.getCancelId(), cancelReplaceOrder.getNewOrderId()));

      return;
    }

    if (cancelReplaceOrder.getPriceInt() >= ARR_SIZE) {
      final Order tmpPtr = outOfBoundsOrderMap.get(cancelReplaceOrder.getOrigOrderId());
      if ((tmpPtr != null) && (tmpPtr.getUser().getId() == cancelReplaceOrder.getUser().getId())) {
        preOrderCheck.updateCancel(tmpPtr);
        matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelReplaceOrder.getCancelId(), tmpPtr,
            instrumentPair, cancelReplaceOrder, CANCEL_ON_REQUEST));
        outOfBoundsOrderMap.remove(cancelReplaceOrder.getOrigOrderId());
        OrderObjectPool.returnObject(tmpPtr);
      } else {
        matcherToPublisherQueue
            .addGuaranteed(CancelRejectMessage.createCancelReject(cancelReplaceOrder, instrumentPair, CxlRejReason.UNKNOWN_ORDER));

        if (!cancelReplaceOrder.isForceAddOrder()) {
          OrderObjectPool.returnObject(cancelReplaceOrder.getOrder());
          cancelReplaceOrder.setOrder(null);
        }
      }
      return;
    }

    final OrderBookPriceLevel priceLevel = bookArr[cancelReplaceOrder.getPriceInt()];
    Order tmpPtr = priceLevel.getHead();

    while (tmpPtr != null) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_10, "cancelReplaceOrder.getCancelId(), >>>> cancel priceInt=", cancelReplaceOrder.getPriceInt(),
            ", tmpPtr.getOrderId()=", tmpPtr.getOrderId(), ", cancelReplaceOrder.getOrigOrderId()=", cancelReplaceOrder.getOrigOrderId(),
            USER_EQ, tmpPtr.getUser().getId(), ", user2=", cancelReplaceOrder.getUser().getId());
      }
      if (((tmpPtr.getOrderId() == cancelReplaceOrder.getOrigOrderId()) || (cancelReplaceOrder.getOrigOrderId() == 0))
          // if ((tmpPtr.getOrderId() == cancelReplaceOrder.getOrigOrderId())
          && (tmpPtr.getUser().getId() == cancelReplaceOrder.getUser().getId())) {
        if (tmpPtr.getSide() == Side.BUY)
          removeBuyOrder(tmpPtr);
        else
          removeSellOrder(tmpPtr);
        preOrderCheck.updateCancel(tmpPtr);
        matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createCancelExecutionReport(cancelReplaceOrder.getCancelId(), tmpPtr,
            instrumentPair, cancelReplaceOrder, CANCEL_ON_REQUEST));
        OrderObjectPool.returnObject(tmpPtr);

        return;
      }
      tmpPtr = tmpPtr.getNext();
    }

    matcherToPublisherQueue
        .addGuaranteed(CancelRejectMessage.createCancelReject(cancelReplaceOrder, instrumentPair, CxlRejReason.UNKNOWN_ORDER));
    // if cancelreplace is rejected, remove order
    if (!cancelReplaceOrder.isForceAddOrder() && cancelReplaceOrder.getOrigOrderId() != 0) {
      OrderObjectPool.returnObject(cancelReplaceOrder.getOrder());
      cancelReplaceOrder.setOrder(null);
    }
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
    if (MarketStatus.CLOSE == marketStatus) {
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithCancelId(massCancelOrder.getSenderCompId(),
          MsgType.ORDER_CANCEL_REPLACE_REQUEST, Long.toString(massCancelOrder.getCancelId()), BusinessRejectReason.MARKET_IS_CLOSED,
          MARKET_IS_CLOSED, massCancelOrder.getOrigOrderId(), massCancelOrder.getSourceSeqNum(), massCancelOrder.getSecondaryOrderId(),
          massCancelOrder.getSecurityId(), massCancelOrder.getCancelId()));

      return;
    }

    matcherToPublisherQueue.addGuaranteed(massCancelOrder);

    final long cancelPriority = 0;
    int userId = 0;

    // for mass cancel for a user, use open order cache instead of scanning orderbooks
    if (massCancelOrder.getUser() != null) {
      final Position[] positionArr = massCancelOrder.getUser().getPositionArr();
      if (positionArr.length <= id) // if position doesn't exist return
        return;

      final Position position = positionArr[id];
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_4, ">>orderbook.massCancelOrder1 id=", id, ", position=", position);
      }

      if (position != null) {
        final UserOpenOrdersByPair userOpenOrdersByPair = position.getUserOpenOrdersByPair();
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_4, ">>orderbook.massCancelOrder2 id=", id, USEROPENORDERSBYPAIR_EQ, userOpenOrdersByPair);
        }
        if (userOpenOrdersByPair != null) {
          Order[] arr = userOpenOrdersByPair.getBids();
          for (int i = arr.length - 1; i >= 0; i--) {
            final Order order = arr[i];
            if (order == null)
              continue;
            // ET-1973
            // if ((massCancelOrder.getType() == CANCEL_ON_DISCONNECT) || (massCancelOrder.getType() == CANCEL_ON_LOGOUT)) {
            // if (order.getTimeInForce() == TimeInForce.GOOD_TILL_CANCEL) {
            // continue;
            // }
            // }
            final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
            cancelOrder.set(order, order.getOrderId(), cancelPriority);
            cancelOrder.setUser(massCancelOrder.getUser());
            cancelOrder.setType(massCancelOrder.getType());
            cancelOrder.setKafkaRecordOffset(massCancelOrder.getKafkaRecordOffset());
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(LOG_FMT_6, ">>orderbook.massCancelOrder3 id=", id, ", order.getOrderId()=", order.getOrderId(), CANCELORDER_EQ,
                  cancelOrder);
            }

            cancelOrder(cancelOrder);
          }
          arr = userOpenOrdersByPair.getAsks();
          for (int i = arr.length - 1; i >= 0; i--) {
            final Order order = arr[i];
            if (order == null)
              continue;
            // ET-1973
            // if ((massCancelOrder.getType() == CANCEL_ON_DISCONNECT) || (massCancelOrder.getType() == CANCEL_ON_LOGOUT)) {
            // if (order.getTimeInForce() == TimeInForce.GOOD_TILL_CANCEL) {
            // continue;
            // }
            // }
            final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
            cancelOrder.set(order, order.getOrderId(), cancelPriority);
            cancelOrder.setUser(massCancelOrder.getUser());
            cancelOrder.setType(massCancelOrder.getType());
            cancelOrder.setKafkaRecordOffset(massCancelOrder.getKafkaRecordOffset());
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(LOG_FMT_6, ">>orderbook.massCancelOrder4 id=", id, ", order.getOrderId()=", order.getOrderId(), CANCELORDER_EQ,
                  cancelOrder);
            }
            cancelOrder(cancelOrder);
          }
        }
      }
    }

    // for mass cancel by submitter, scan the orderbook
    // only if account wasn't specified
    if (massCancelOrder.getSubmitterId() != 0 && massCancelOrder.getAccount() == 0) {
      final List<Order> orders = new ArrayList<>();

      // order book
      for (int i = 0; i < bookArr.length; i++) {
        if (bookArr[i] != null) {
          Order order = bookArr[i].getHead();
          while (order != null) {
            if (order.getSubmitterId() == massCancelOrder.getSubmitterId()) {
              orders.add(order);
            }
            order = order.getNext();
          }
        }
      }

      // out of bound orders
      for (final Order order : outOfBoundsOrderMap.values()) {
        if (order.getSubmitterId() == massCancelOrder.getSubmitterId()) {
          orders.add(order);
        }
      }

      // stop limit orders
      for (final Order order : stopLimitContainer.getBuyTreeSet()) {
        if (order.getSubmitterId() == massCancelOrder.getSubmitterId()) {
          orders.add(order);
        }
      }
      for (final Order order : stopLimitContainer.getSellTreeSet()) {
        if (order.getSubmitterId() == massCancelOrder.getSubmitterId()) {
          orders.add(order);
        }
      }

      // stop profit orders
      for (final Order order : stopProfitContainer.getBuyTreeSet()) {
        if (order.getSubmitterId() == massCancelOrder.getSubmitterId()) {
          orders.add(order);
        }
      }
      for (final Order order : stopProfitContainer.getSellTreeSet()) {
        if (order.getSubmitterId() == massCancelOrder.getSubmitterId()) {
          orders.add(order);
        }
      }

      // auction orders
      for (final Order order : auctionContainer.getBuyTreeSet()) {
        if (order.getSubmitterId() == massCancelOrder.getSubmitterId()) {
          orders.add(order);
        }
      }
      for (final Order order : auctionContainer.getSellTreeSet()) {
        if (order.getSubmitterId() == massCancelOrder.getSubmitterId()) {
          orders.add(order);
        }
      }

      // trailing stop orders
      for (final Order order : trailingStopContainer.getBuyTreeSet()) {
        if (order.getSubmitterId() == massCancelOrder.getSubmitterId()) {
          orders.add(order);
        }
      }
      for (final Order order : trailingStopContainer.getSellTreeSet()) {
        if (order.getSubmitterId() == massCancelOrder.getSubmitterId()) {
          orders.add(order);
        }
      }

      // cancel
      for (final Order order : orders) {
        // ET-1973: Stop skipping GTC orders on cancel on disconnect and cancel on logout
        // if ((massCancelOrder.getType() == CANCEL_ON_DISCONNECT) || (massCancelOrder.getType() == CANCEL_ON_LOGOUT)) {
        // if (order.getTimeInForce() == TimeInForce.GOOD_TILL_CANCEL) {
        // continue;
        // }
        // }

        final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
        cancelOrder.set(order, order.getOrderId(), cancelPriority);
        cancelOrder.setUser(order.getUser());
        cancelOrder.setType(massCancelOrder.getType());
        cancelOrder.setKafkaRecordOffset(massCancelOrder.getKafkaRecordOffset());
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_6, "Mass cancel orders by submitter submitterId=", massCancelOrder.getSubmitterId(), ", orderId=",
              order.getOrderId(), CANCELORDER_EQ, cancelOrder);
        }
        cancelOrder(cancelOrder);
      }
    }
  }

  // called from a separate MarketDataOutputBuilderThread thread
  @Override
  public MarketDataSnapshotFullRefreshEncoder build(final MarketDataSnapshotFullRefreshEncoder marketDataSnapshotFullRefreshEncoder) {
    final int[] bidPricesArr = new int[CACHE_DEPTH];
    final long[] bidQuantityArr = new long[CACHE_DEPTH];
    final long[] bidAssetIdArr = new long[CACHE_DEPTH];
    final int[] bidTokenIdArr = new int[CACHE_DEPTH];
    final long[] bidGroupAssetIdArr = new long[CACHE_DEPTH];
    final long[] bidSelectIdArr = new long[CACHE_DEPTH];

    final int[] askPricesArr = new int[CACHE_DEPTH];
    final long[] askQuantityArr = new long[CACHE_DEPTH];
    final long[] askAssetIdArr = new long[CACHE_DEPTH];
    final int[] askTokenIdArr = new int[CACHE_DEPTH];
    final long[] askGroupAssetIdArr = new long[CACHE_DEPTH];
    final long[] askSelectIdArr = new long[CACHE_DEPTH];

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
      bidAssetIdArr[bidIndex] = temp.getAssetId();
      bidTokenIdArr[bidIndex] = temp.getTokenId();
      bidGroupAssetIdArr[bidIndex] = temp.getGroupAssetId();
      bidSelectIdArr[bidIndex] = temp.getSelectId();


      if (!temp.isHidden())
        bidQuantityArr[bidIndex] = temp.getQuantityLong();
      temp = temp.getNext();
      while (temp != null) {
        if (!temp.isHidden()) {
          if (temp.getAssetId() != bidAssetIdArr[bidIndex] || temp.getTokenId() != bidTokenIdArr[bidIndex]
              || temp.getGroupAssetId() != bidGroupAssetIdArr[bidIndex]) {
            bidIndex++;
            if (bidIndex >= CACHE_DEPTH)
              break;
            bidPricesArr[bidIndex] = temp.getPriceInt();
            bidAssetIdArr[bidIndex] = temp.getAssetId();
            bidTokenIdArr[bidIndex] = temp.getTokenId();
            bidGroupAssetIdArr[bidIndex] = temp.getGroupAssetId();
            bidSelectIdArr[bidIndex] = temp.getSelectId();
          }
          bidQuantityArr[bidIndex] += temp.getQuantityLong();
        }
        temp = temp.getNext();
      }
      if (bidQuantityArr[bidIndex] > 0) {
        bidIndex++;
        if (bidIndex >= CACHE_DEPTH)
          break;
      }
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
      askAssetIdArr[askIndex] = temp.getAssetId();
      askTokenIdArr[askIndex] = temp.getTokenId();
      askGroupAssetIdArr[askIndex] = temp.getGroupAssetId();
      askSelectIdArr[askIndex] = temp.getSelectId();

      if (!temp.isHidden())
        askQuantityArr[askIndex] = temp.getQuantityLong();
      temp = temp.getNext();
      while (temp != null) {
        if (!temp.isHidden()) {
          if (temp.getAssetId() != askAssetIdArr[askIndex] || temp.getTokenId() != askTokenIdArr[askIndex]
              || temp.getGroupAssetId() != askGroupAssetIdArr[askIndex]) {
            askIndex++;
            if (askIndex >= CACHE_DEPTH)
              break;
            askPricesArr[askIndex] = temp.getPriceInt();
            askAssetIdArr[askIndex] = temp.getAssetId();
            askTokenIdArr[askIndex] = temp.getTokenId();
            askGroupAssetIdArr[askIndex] = temp.getGroupAssetId();
            askSelectIdArr[askIndex] = temp.getSelectId();
          }
          askQuantityArr[askIndex] += temp.getQuantityLong();
        }
        temp = temp.getNext();
      }
      if (askQuantityArr[askIndex] > 0) {
        askIndex++;
        if (askIndex >= CACHE_DEPTH)
          break;
      }
    }
    if (marketStatus != null)
      marketDataSnapshotFullRefreshEncoder.marketStatus(marketStatus.value());
    marketDataSnapshotFullRefreshEncoder.usdMark(instrumentPair.getIndexFeedUsdMark());
    marketDataSnapshotFullRefreshEncoder.fundingRateTime(instrumentPair.getFundingRateTime());
    marketDataSnapshotFullRefreshEncoder.estFundingRate(instrumentPair.getEstFundingRate());
    if (auctionContainer != null) {
      if (MarketStatus.OPEN_AUCTION == marketStatus) {
        auctionContainer.calcAuctionPrice(this, Context.getAuctionRecalcTimeInterval());
        marketDataSnapshotFullRefreshEncoder
            .fundingRateTime(instrumentPair.getAuctionLastStartedTime() + instrumentPair.getAuctionDurationTime());
      }
      marketDataSnapshotFullRefreshEncoder.auctionPrice(auctionContainer.getOptimalAskPrice());
      marketDataSnapshotFullRefreshEncoder.auctionVolume(auctionContainer.getMaxQtyMatchedAtLevel());
    }

    MdEntrieGroupEncoder entry = marketDataSnapshotFullRefreshEncoder.mdEntrieGroupCount(bidIndex + askIndex);
    for (int i = 0; i < bidIndex; i++) {
      entry = entry.next();
      entry.side(Side.BUY);
      entry.price(bidPricesArr[i]);
      entry.priceScale(instrumentPair.getPriceScale());
      entry.quantity(bidQuantityArr[i]);
      entry.quantityScale(instrumentPair.getQuantityScale());
      entry.assetId(bidAssetIdArr[i]);
      entry.tokenId(bidTokenIdArr[i]);
      entry.groupAssetId(bidGroupAssetIdArr[i]);
      entry.selectId(bidSelectIdArr[i]);
      /*
       * LOGGER.info("Side: " + Side.BUY + " price: " + bidPricesArr[i] + " quantity: " + bidQuantityArr[i] + " assetId: " +
       * bidAssetIdArr[i] + " tokenId: " + bidSelectIdArr[i]);
       */
    }
    for (int i = 0; i < askIndex; i++) {
      entry = entry.next();
      entry.side(Side.SELL);
      entry.price(askPricesArr[i]);
      entry.priceScale(instrumentPair.getPriceScale());
      entry.quantity(askQuantityArr[i]);
      entry.quantityScale(instrumentPair.getQuantityScale());
      entry.assetId(askAssetIdArr[i]);
      entry.tokenId(askTokenIdArr[i]);
      entry.groupAssetId(askGroupAssetIdArr[i]);
      entry.selectId(askSelectIdArr[i]);
      /*
       * LOGGER.info("Side: " + Side.SELL + " price: " + askSelectIdArr[i] + " quantity: " + askSelectIdArr[i] + " assetId: " +
       * askSelectIdArr[i] + " tokenId: " + askSelectIdArr[i]);
       */
    }

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
    try {
      final ArrayList<Order> triggeredOrdersList = new ArrayList<>();
      int priceInt = liquidationOrder.getPriceInt();
      if (priceInt == 0)
        priceInt = (int) instrumentPair.adjustPriceToScale(liquidationOrder.getPrice(), liquidationOrder.getPriceScale());
      if (Side.BUY == liquidationOrder.getSide())
        adlMakerContainer.getTriggeredSellLimitList(priceInt, triggeredOrdersList);
      else
        adlMakerContainer.getTriggeredBuyLimitList(priceInt, triggeredOrdersList);

      for (final Order orderMaker : triggeredOrdersList) {
        // prevent self-trade
        if (orderMaker.getAccount() == liquidationOrder.getAccount())
          continue;

        final long quantityFilled = Math.min(liquidationOrder.getQuantityLong(), orderMaker.getQuantityLong());

        if (quantityFilled > 0) {
          // match without order book
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info(LOG_FMT_6, "liquidationToCloseUsingADLMaker calculated quantityFilled=", quantityFilled, ORDERMAKER_EQ, orderMaker,
                ORDERTAKER_EQ, liquidationOrder);
          }
          liquidationOrder.setQuantityLong(liquidationOrder.getQuantityLong() - quantityFilled);
          orderMaker.setQuantityLong(orderMaker.getQuantityLong() - quantityFilled);
          filled(quantityFilled, orderMaker, liquidationOrder, BUY_LIMIT, ExecType.CALCULATED);
          // liquidationOrder.setQuantityLong(liquidationOrder.getQuantityLong() - quantityFilled);
        }

        // remove counterOrder if fully filled
        if (orderMaker.getQuantityLong() <= 0) {
          if (Side.BUY == liquidationOrder.getSide())
            adlMakerContainer.removeSellLimit(orderMaker);
          else
            adlMakerContainer.removeBuyLimit(orderMaker);
        }

        // if liquidated, done
        if (liquidationOrder.getQuantityLong() <= 0) {
          isMarginLiquidationSatisfied(user, liquidationOrder); // this needs to be called to reset the userstate
          return true;
        }
      }
    } catch (Throwable e) {
      LOGGER.error(ERROR_LOG, e);
    }
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "liquidationToCloseUsingADLMaker sweeped=", liquidationOrder, USER_EQ, user);
    }
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


      if (Side.BUY == liquidationOrder.getSide()) { // buying to cover short
        liquidationBuyToCloseOrder(liquidationOrder, user, positionArr);
      } else { // sell to close
        liquidationSellToCloseOrder(liquidationOrder, user, positionArr);
      }

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
        from.addBalance(
            new Balance(assetId, 0, 0, -settlePosition.getQuantity(), instrument.getQuantityScale(), settlePosition.getAssetIdtreeSet()));

        final BalanceAdminMessage to = new BalanceAdminMessage();
        to.setUpdateType(UpdateType.PATCH);
        to.setUserId(insuranceUser.getId());
        to.setUser(insuranceUser);
        to.setFirmId(insuranceUser.getFirmId());
        to.setFeeTier(insuranceUser.getFeeTierOrig());
        to.setSenderInstanceId(Context.getInstanceId());
        to.setTxType(Constants.TX_TO_BANKRUPT_REMAINDER);
        to.setTxId(instrumentPair.getId());
        to.addBalance(
            new Balance(assetId, 0, 0, settlePosition.getQuantity(), instrument.getQuantityScale(), settlePosition.getAssetIdtreeSet()));

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
    target.disableOutputQueue();

    // restate
    List<Order> orders = new ArrayList<>();
    for (int i = 0; i < bookArr.length; i++) { // this will take a while...

      // we need to copy the orders to a separate list ad adding the orders to the target book invalidates
      // the prev/next references in the orders
      orders.clear();
      Order tmp = bookArr[i].getHead();
      while (tmp != null) {
        orders.add(tmp);
        tmp = tmp.getNext();
      }

      for (final Order order : orders) {
        target.addOrder(transform == null ? order : transform.transform(order));
      }
    }

    // restate outOfBounds orders
    final List<Order> outOfBoundsList = new ArrayList<>(outOfBoundsOrderMap.values());
    Collections.sort(outOfBoundsList, orderComparator);
    for (final Order outOfBoundsOrder : outOfBoundsList) {
      if (outOfBoundsOrder == null)
        continue;
      target.addOrder(transform == null ? outOfBoundsOrder : transform.transform(outOfBoundsOrder));
    }

    // restate stop limit orders
    final TreeSet<Order> buyTreeSet = stopLimitContainer.getBuyTreeSet();
    while (!buyTreeSet.isEmpty()) {
      final Order order = buyTreeSet.pollFirst();
      if (order != null) {
        target.addOrder(transform == null ? order : transform.transform(order));
      }
    }
    final TreeSet<Order> sellTreeSet = stopLimitContainer.getSellTreeSet();
    while (!sellTreeSet.isEmpty()) {
      final Order order = sellTreeSet.pollFirst();
      if (order != null) {
        target.addOrder(transform == null ? order : transform.transform(order));
      }
    }

    // restate profit stop limit orders
    final TreeSet<Order> buyTreeSet2 = stopProfitContainer.getBuyTreeSet();
    while (!buyTreeSet2.isEmpty()) {
      final Order order = buyTreeSet2.pollFirst();
      if (order != null) {
        target.addOrder(transform == null ? order : transform.transform(order));
      }
    }
    final TreeSet<Order> sellTreeSet2 = stopProfitContainer.getSellTreeSet();
    while (!sellTreeSet2.isEmpty()) {
      final Order order = sellTreeSet2.pollFirst();
      if (order != null) {
        target.addOrder(transform == null ? order : transform.transform(order));
      }
    }

    // restate auction orders
    final ConcurrentSkipListSet<Order> buyTreeSet3 = auctionContainer.getBuyTreeSet();
    while (!buyTreeSet3.isEmpty()) {
      final Order order = buyTreeSet3.pollFirst();
      if (order != null) {
        target.addOrder(transform == null ? order : transform.transform(order));
      }
    }
    final ConcurrentSkipListSet<Order> sellTreeSet3 = auctionContainer.getSellTreeSet();
    while (!sellTreeSet3.isEmpty()) {
      final Order order = sellTreeSet3.pollFirst();
      if (order != null) {
        target.addOrder(transform == null ? order : transform.transform(order));
      }
    }

    // restate trailing stop limit orders
    final TreeSet<Order> buyTreeSet4 = trailingStopContainer.getBuyTreeSet();
    while (!buyTreeSet4.isEmpty()) {
      final Order order = buyTreeSet4.pollFirst();
      if (order != null) {
        target.addOrder(transform == null ? order : transform.transform(order));
      }
    }
    final TreeSet<Order> sellTreeSet4 = trailingStopContainer.getSellTreeSet();
    while (!sellTreeSet4.isEmpty()) {
      final Order order = sellTreeSet4.pollFirst();
      if (order != null) {
        target.addOrder(transform == null ? order : transform.transform(order));
      }
    }

    // restore publishing to output
    target.restoreOutputQueue();
  }

  // clears and removes references of cached data
  @Override
  public final void reclaim() {
    for (int i = 0; i < bidLevelCachePtrArr.length; i++)
      bidLevelCachePtrArr[i] = 0;
    for (int i = 0; i < askLevelCachePtrArr.length; i++)
      askLevelCachePtrArr[i] = 0;
    for (int i = 0; i < bookArr.length; i++) {
      bookArr[i].clear();
      bookArr[i] = null;
    }
    bidDepthLevelCacheCount = 0;
    askDepthLevelCacheCount = 0;
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

    if (OrdStatus.FILLED == executionReport.getOrdStatus() || OrdStatus.CANCELED == executionReport.getOrdStatus()) {
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
    for (int i = 0; i < bidLevelCachePtrArr.length; i++)
      bidLevelCachePtrArr[i] = 0;
    for (int i = 0; i < askLevelCachePtrArr.length; i++)
      askLevelCachePtrArr[i] = 0;
    for (int i = 0; i < bookArr.length; i++)
      bookArr[i].clear();
    bidDepthLevelCacheCount = 0;
    askDepthLevelCacheCount = 0;

    stopLimitContainer.clear();
    stopProfitContainer.clear();
    outOfBoundsOrderMap.clear();
    outOfBoundsOrderMapBySecondaryOrderId.clear();
  }

  private final void populateOrderBookFromDRMap() {
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

  // sort by smallest to largest
  private static final Comparator<Order> orderComparator = new Comparator<Order>() {
    @Override
    public int compare(final Order order1, final Order order2) {
      try {
        if (order1.getOrderId() == order2.getOrderId())
          return 0;
        else if (order1.getOrderId() > order2.getOrderId())
          return 1;
        else
          return -1;
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
      return 0;
    }
  };

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

  public Map<Long, Order> getOutOfBoundsOrderMap() {
    return outOfBoundsOrderMap;
  }

  @Override
  public final int getArrSize() {
    return this.ARR_SIZE;
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
  public final void expireAllOrders() {
    for (int i = 0; i < bookArr.length; i++) { // this will take a while...
      Order tmp = null;
      Order next = null;
      if (bookArr[i].getHead() != null) {
        tmp = bookArr[i].getHead();
        while (tmp != null) {
          next = tmp.getNext();

          // expireOrder(tmp);
          preOrderCheck.updateCancel(tmp);
          final ExecutionReportMessage executionReportMessage =
              ExecutionReportMessage.createOrderEliminationExecutionReport(tmp, instrumentPair);
          matcherToPublisherQueue.addGuaranteed(executionReportMessage);

          tmp = next;
        }
      }
    }

    FastArrayList<Order> list = new FastArrayList<>(stopLimitContainer.getSellTreeSet());
    for (final Order order : list) {
      final long cancelId = order.getOrderId();
      final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
      cancelOrder.set(order, cancelId, cancelId);
      cancelOrder.setUser(order.getUser());
      cancelOrder(cancelOrder);
    }

    list = new FastArrayList<>(stopLimitContainer.getBuyTreeSet());
    for (final Order order : list) {
      final long cancelId = order.getOrderId();
      final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
      cancelOrder.set(order, cancelId, cancelId);
      cancelOrder.setUser(order.getUser());
      cancelOrder(cancelOrder);
    }

    list = new FastArrayList<>(stopProfitContainer.getSellTreeSet());
    for (final Order order : list) {
      final long cancelId = order.getOrderId();
      final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
      cancelOrder.set(order, cancelId, cancelId);
      cancelOrder.setUser(order.getUser());
      cancelOrder(cancelOrder);
    }

    list = new FastArrayList<>(stopProfitContainer.getBuyTreeSet());
    for (final Order order : list) {
      final long cancelId = order.getOrderId();
      final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
      cancelOrder.set(order, cancelId, cancelId);
      cancelOrder.setUser(order.getUser());
      cancelOrder(cancelOrder);
    }

    list = new FastArrayList<>(trailingStopContainer.getSellTreeSet());
    for (final Order order : list) {
      final long cancelId = order.getOrderId();
      final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
      cancelOrder.set(order, cancelId, cancelId);
      cancelOrder.setUser(order.getUser());
      cancelOrder(cancelOrder);
    }

    list = new FastArrayList<>(trailingStopContainer.getBuyTreeSet());
    for (final Order order : list) {
      final long cancelId = order.getOrderId();
      final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
      cancelOrder.set(order, cancelId, cancelId);
      cancelOrder.setUser(order.getUser());
      cancelOrder(cancelOrder);
    }

    list = new FastArrayList<>(auctionContainer.getSellTreeSet());
    for (final Order order : list) {
      final long cancelId = order.getOrderId();
      final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
      cancelOrder.set(order, cancelId, cancelId);
      cancelOrder.setUser(order.getUser());
      cancelOrder(cancelOrder);
    }

    list = new FastArrayList<>(auctionContainer.getBuyTreeSet());
    for (final Order order : list) {
      final long cancelId = order.getOrderId();
      final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
      cancelOrder.set(order, cancelId, cancelId);
      cancelOrder.setUser(order.getUser());
      cancelOrder(cancelOrder);
    }

    list = new FastArrayList<>(outOfBoundsOrderMap.values());
    for (final Order order : list) {
      final long cancelId = order.getOrderId();
      final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
      cancelOrder.set(order, cancelId, cancelId);
      cancelOrder.setUser(order.getUser());
      cancelOrder(cancelOrder);
    }

    clearOrderBook();
  }

  public void setPublishAcks(final boolean publishAcks) {
    this.publishAcks = publishAcks;
  }
}
