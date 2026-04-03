package com.solfini.matchengine.orderbook;

import com.solfini.common.*;
import com.solfini.instrument.*;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.drmode.message.DRCancelOrder;
import com.solfini.matchengine.drmode.message.DRExecutionReport;
import com.solfini.matchengine.drmode.message.DROrder;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.internal.*;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.pool.OrderObjectPool;
import com.solfini.pool.PositionMatchThreadObjectPool;
import com.solfini.preordercheck.*;
import com.solfini.risk.InsuranceState;
import com.solfini.sbe.encoder.*;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.user.UserOpenOrdersByPair;
import com.solfini.util.MbxMath;
import com.solfini.util.PropertyReader;
import org.agrona.collections.Long2LongHashMap;

import static com.solfini.preordercheck.MarginPreOrderCheckAndSettle.isLIQUIDATON_MODE;

public class LiquidityOrderBookOld extends GlobalOrderBook implements OrderBook, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LiquidityOrderBookOld.class);
  private static final String AUTOCLOSE_MAKER_STR = "autoclose_maker";
  private static final ManyToOneConcurrentArrayQueueCustom<Message> MATCHER_TO_PUBLISHER_QUEUE = Context.getMatcherToPublisherQueue();
  private static final ManyToManyConcurrentArrayQueueCustom<User> riskToAutoLiquidatorQueue = Context.getRiskToAutoLiquidatorQueue();
  private static final int LIQUIDITY_DEPTH_LEVELS = Context.getLiquidityDepthLevels();
  private static final long ADL_COOLOFF_TIME = PropertyReader.getProperty("ADL_COOLOFF_TIME", 2_000); // default to 2 seconds
  public static final double NAV_FEE_OFFSET = 1 - (0.000001 * Fee.LIQUIDATION_FEE_RATE); // .995
  //private static boolean LIQUIDATON_MODE = false; // don't auto-liquidate until fully started up

  private static final int SETTLE_INSTRUMENT_ID = 1;
  private final int id;
  private final int depth;
  private final short priceScale;
  private final short quantityScale;
  private final InstrumentPair liquidityPair;
  private PreOrderCheck cashPreOrderCheck;
  private PreOrderCheck marginPreOrderCheck;
  private final Long2LongHashMap bids;
  private final Long2LongHashMap asks;
  private long lastUpdated;
  private long bestBid;
  private long bestAsk;
  private final int orderBookStrategy;
  private final int preOrderCheckStrategy;
  private MarketStatus marketStatus;
  private final NewOrderSingleHandler newOrderSingleHandler = new NewOrderSingleHandler();
  private long filledCount;
  private int last;

  public LiquidityOrderBookOld(final InstrumentPair pair, final PreOrderCheck preOrderCheck, final int orderBookStrategy,
      final int preOrderCheckStrategy) {
    this.id = pair.getId();
    this.priceScale = pair.getPriceScale();
    this.quantityScale = pair.getQuantityScale();
    this.liquidityPair = pair;
    this.cashPreOrderCheck = new CashPreOrderCheck();
    this.marginPreOrderCheck = new MarginPreOrderCheckAndSettle();
    this.depth = LIQUIDITY_DEPTH_LEVELS;

    this.orderBookStrategy = orderBookStrategy;
    this.preOrderCheckStrategy = preOrderCheckStrategy;
    if (MarketStatus.DR_MODE == Context.getMarketStatus()) {
      this.cashPreOrderCheck = new NoPreOrderCheck();
      this.marginPreOrderCheck = new NoPreOrderCheck();
    } else if (pair.getMarketStatus() != null) {
      this.marketStatus = pair.getMarketStatus();
    }
    this.bids = new Long2LongHashMap(depth, 0.8f, 0);
    this.asks = new Long2LongHashMap(depth, 0.8f, 0);
  }

  @Override
  public void addOrder(final Order order) {
    LOGGER.info("Liquidity Order Received: " + order.toJSON());
    final InstrumentPair pair = InstrumentCache.getPairBySymbol(order.getSymbol());
    if (pair == null) {
      LOGGER.error("Invalid instrument pair. symbol: " + order.getSymbol() + " clOrdId: " + order.getClOrdId());
      MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.INVALID_ORDER_SECURITY, INVALID_ORDER_SECURITY, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
      return;
    }
    long priceLong = order.getPrice();
    final int priceScale = order.getPriceScale();
    if (pair.getPriceScale() > priceScale) {
      for (int i = 0; i < (pair.getPriceScale() - priceScale); i++)
        priceLong = priceLong * 10;
    } else if (pair.getPriceScale() < priceScale) {
      for (int i = 0; i < (priceScale - pair.getPriceScale()); i++)
        priceLong = priceLong / 10;
    }
    order.setPriceInt((int) priceLong);

    long quantityLong = order.getQty();
    final int quantityScale = order.getQtyScale();
    if (pair.getQuantityScale() > quantityScale) {
      for (int i = 0; i < (pair.getQuantityScale() - quantityScale); i++)
        quantityLong = quantityLong * 10;
    } else if (pair.getQuantityScale() < quantityScale) {
      for (int i = 0; i < (quantityScale - pair.getQuantityScale()); i++)
        quantityLong = quantityLong / 10;
    }
    order.setQuantityLong(quantityLong);
    order.setQuantityOrigLong(quantityLong);

    User counterpartyUser = UserCache.getMarketMakerUser();
    if (STAKING == order.getTargetStrategy() || VIRTUAL_TOKEN_SWAP == order.getTargetStrategy()) {
      counterpartyUser = UserCache.getTokenManager();
    }

    if (Side.BUY == order.getSide()) {
      matchAutoBuyWithoutOrderBook(order, order.getUser(), counterpartyUser, order.getQuantityLong(), pair);
    } else {
      matchAutoSellWithoutOrderBook(order, order.getUser(), counterpartyUser, order.getQuantityLong(), pair);
    }
  }

  @Override
  public void cancelOrder(CancelOrder cancelOrder) {

  }

  @Override
  public void cancelReplaceOrder(CancelReplaceOrder cancelReplaceOrder) {

  }

  @Override
  public void massCancelOrder(MassCancelOrder massCancelOrder) {

  }

  @Override
  public void onLiquidationOrder(LiquidationOrder liquidationOrder) {

  }

  @Override
  public void uncross() {

  }

  @Override
  public void changeState(MarketStatus marketStatus, long snapId, Message causingMessage) {
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
        this.cashPreOrderCheck = new NoPreOrderCheck();
        this.marginPreOrderCheck = new NoPreOrderCheck();
        restate(snapId, causingMessage);
        break;
      case DR_TO_OPEN:
        this.marketStatus = MarketStatus.OPEN;
        this.cashPreOrderCheck = new CashPreOrderCheck();
        this.marginPreOrderCheck = new MarginPreOrderCheckAndSettle();
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
        this.liquidityPair.setMarketStatus(MarketStatus.OPEN);
        break;
      default:
        break;
    }
    this.liquidityPair.setMarketStatus(marketStatus);
  }

  @Override
  public void expireLiveSessionOrders() {

  }

  @Override
  public void expireAllOrders() {

  }

  @Override
  public void restate(long snapId, Message causingMessage) {

  }

  @Override
  public int getLast() {
    return 0;
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
  public int getMark() {
    return 0;
  }

  @Override
  public void setMark(int mark) {

  }

  @Override
  public double getUsdMark() {
    return 0;
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
  public InstrumentPair getInstrumentPair() {
    return this.liquidityPair;
  }

  @Override
  public MarketDataSnapshotFullRefreshEncoder build(final MarketDataSnapshotFullRefreshEncoder marketDataSnapshotFullRefreshEncoder) {
    if (marketStatus != null)
      marketDataSnapshotFullRefreshEncoder.marketStatus(marketStatus.value());
    marketDataSnapshotFullRefreshEncoder.usdMark(0/*liquidityPair.getIndexFeedUsdMark()*/);
    marketDataSnapshotFullRefreshEncoder.fundingRateTime(liquidityPair.getFundingRateTime());
    marketDataSnapshotFullRefreshEncoder.estFundingRate(liquidityPair.getEstFundingRate());

    MarketDataSnapshotFullRefreshEncoder.MdEntrieGroupEncoder entry = marketDataSnapshotFullRefreshEncoder.mdEntrieGroupCount(0);

    return marketDataSnapshotFullRefreshEncoder;
  }

  @Override
  public void copyTo(OrderBook target, Transform transform) {

  }

  @Override
  public void reclaim() {

  }

  @Override
  public void addOrderDR(DROrder order) {

  }

  @Override
  public void cancelOrderDR(DRCancelOrder cancelOrder) {

  }

  @Override
  public void execReportDR(DRExecutionReport executionReport) {

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
  public StopLimitContainer getStopLimitContainer() {
    return null;
  }

  @Override
  public StopProfitContainer getStopProfitContainer() {
    return null;
  }

  @Override
  public void setFilledCount(long newValue) {

  }

  @Override
  public void setFilledCountIfGreater(long newValue) {

  }

  @Override
  public long getFilledCount() {
    return 0;
  }

  @Override
  public void setSecondaryOrderIdIfGreater(long newValue) {

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
  public void setSettleCoinUsdMarkInstrument(Instrument settleCoinUsdMarkInstrument) {

  }

  @Override
  public PreOrderCheck getPreOrderCheck() {
    return null;
  }

  @Override
  public int getId() {
    return this.id;
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
  public Instrument getSettleCoinUsdMarkInstrument() {
    return null;
  }

  @Override
  public String toString(int priceLevel) {
    return null;
  }

  @Override
  public void clearOrderBook() {

  }

  @Override
  public int getOrderCount() {
    return 0;
  }

  @Override
  public TrailingStopContainer getTrailingStopContainer() {
    return null;
  }

  @Override
  public void expireSettlePosition(int markInSettleCoin) {

  }

  @Override
  public Order onCollateralSwapOrder(User user, long price, short price_scale, long qty, short qty_scale, Side side) {
    return null;
  }

  @Override
  public void updateSecurityDefinition(InstrumentPair instrumentPair) {
    final SecurityDefinitionAdminMessage message = new SecurityDefinitionAdminMessage(instrumentPair);
    message.setUpdateType(UpdateType.PATCH);
    MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(message);
  }

  @Override
  public Order buildAlgoOrder(final Order source) {
    return null;
  }

  @Override
  public Order onUnderlyerPhysicalSettle(User user, long price, short price_scale, long qty, short qty_scale, Side side) {
    return null;
  }

  // sets ExecType.CALCULATED
  // forced trade
  private void matchAutoBuyWithoutOrderBook(final Order order, final User user, final User counterpartyUser, final long quantityFilled, final InstrumentPair instrumentPair) {
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

    final Order orderMaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(counterpartyUser, "" + 1_000_000_000 + counterpartyUser.getId(),
        instrumentPair.getId(), AUTOCLOSE_MAKER_STR, order.getPrice(), order.getPriceScale(), quantityFilled,
        instrumentPair.getQuantityScale(), Side.SELL, OrdType.LIMIT, true);
    final PreOrderCheck preOrderCheck = instrumentPair.getPreOrderCheckStrategy() == MARGIN_PREORDER_CHECK ? marginPreOrderCheck : cashPreOrderCheck;

    if (!preOrderCheck.checkOrder(orderTaker, orderTaker.getPriceInt())) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(orderTaker, instrumentPair);
        MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(executionReportMessage);
      }

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, REJECT_ORDER_EQ, FAILED_PRE_CREDIT_CHECK, ORDER_EQ, orderTaker);
      }
      MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(BusinessRejectMessage.createBusinessReject(orderTaker.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(orderTaker.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, orderTaker.getOrderId(),
          orderTaker.getSourceSeqNum(), orderTaker.getSecondaryOrderId(), orderTaker.getSecurityId(), orderTaker.getSubmitterId()));
      OrderObjectPool.returnObject(orderTaker);
      return;
    }

    // ack
    final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(orderTaker, instrumentPair);
    executionReportMessage.setSecurityId(liquidityPair.getId());
    MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(executionReportMessage);

    // ack
    final ExecutionReportMessage executionReportMessage2 = ExecutionReportMessage.createAckNewOrderExecutionReport(orderMaker, instrumentPair);
    executionReportMessage.setSecurityId(liquidityPair.getId());
    MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(executionReportMessage2);

    // match without order book
    LOGGER.info(LOG_FMT_6, "Liquidity order generated quantityFilled=", quantityFilled, ORDERMAKER_EQ, orderMaker,
          ORDERTAKER_EQ, orderTaker);

    orderTaker.setQuantityLong(orderTaker.getQuantityLong() - quantityFilled);
    orderMaker.setQuantityLong(orderMaker.getQuantityLong() - quantityFilled);
    filled(quantityFilled, orderMaker, orderTaker, BUY_LIMIT, ExecType.CALCULATED, instrumentPair, instrumentPair.getBase(), preOrderCheck);

  }

  // sets ExecType.CALCULATED
  // forced trade
  private void matchAutoSellWithoutOrderBook(final Order order, final User user, final User counterpartyUser, final long quantityFilled, final InstrumentPair instrumentPair) {
    // create new orders
    final Order orderTaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(user, "" + 1_000_000_000 + user.getId(),
        instrumentPair.getId(), order.getClOrdId(), order.getPrice(), order.getPriceScale(), quantityFilled, instrumentPair.getQuantityScale(),
        Side.SELL, OrdType.LIMIT, true);
    // preOrderCheck.checkOrderNoValidation(orderTaker, orderTaker.getPriceInt());
    orderTaker.setSourceSeqNum(order.getSourceSeqNum());
    orderTaker.setSourceSendTime(order.getSourceSendTime());
    orderTaker.setKafkaRecordOffset(order.getKafkaRecordOffset());
    orderTaker.setInputTime(order.getInputTime());
    orderTaker.setDecodedTime(order.getDecodedTime());

    final Order orderMaker = (Order) newOrderSingleHandler.buildNewLiquidationOrder(counterpartyUser, "" + 1_000_000_000 + counterpartyUser.getId(),
        instrumentPair.getId(), AUTOCLOSE_MAKER_STR, order.getPrice(), order.getPriceScale(), quantityFilled, instrumentPair.getQuantityScale(),
        Side.BUY, OrdType.LIMIT, true);
    final PreOrderCheck preOrderCheck = instrumentPair.getPreOrderCheckStrategy() == MARGIN_PREORDER_CHECK ? marginPreOrderCheck : cashPreOrderCheck;

    if (!preOrderCheck.checkOrder(orderTaker, getRiskPriceForSell(instrumentPair, orderTaker.getPriceInt()))) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(orderTaker, instrumentPair);
        MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(executionReportMessage);
      }

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_4, REJECT_ORDER_EQ, FAILED_PRE_CREDIT_CHECK, ORDER_EQ, orderTaker);
      }
      MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(BusinessRejectMessage.createBusinessReject(orderTaker.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(orderTaker.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, orderTaker.getOrderId(),
          orderTaker.getSourceSeqNum(), orderTaker.getSecondaryOrderId(), orderTaker.getSecurityId(), orderTaker.getSubmitterId()));

      OrderObjectPool.returnObject(orderTaker);
      return;
    }

    // ack
    final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(orderTaker, instrumentPair);
    executionReportMessage.setSecurityId(liquidityPair.getId());
    MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(executionReportMessage);

    // ack
    final ExecutionReportMessage executionReportMessage2 = ExecutionReportMessage.createAckNewOrderExecutionReport(orderMaker, instrumentPair);
    executionReportMessage.setSecurityId(liquidityPair.getId());
    MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(executionReportMessage2);

    LOGGER.info(LOG_FMT_6, "Liquidity order generated quantityFilled=", quantityFilled, ORDERMAKER_EQ, orderMaker,
        ORDERTAKER_EQ, orderTaker);

    // match without order book
    orderTaker.setQuantityLong(orderTaker.getQuantityLong() - quantityFilled);
    orderMaker.setQuantityLong(orderMaker.getQuantityLong() - quantityFilled);
    filled(quantityFilled, orderMaker, orderTaker, SELL_LIMIT, ExecType.CALCULATED, instrumentPair, instrumentPair.getBase(), preOrderCheck);

    // preOrderCheck.updateCancelNoValidation(orderTaker);
  }

  public final void filled(final long quantityFilled, final Order makerOrder, final Order takerOrder, final int orderType,
      final ExecType execType, final InstrumentPair instrumentPair, final Instrument settleCoinUsdMarkInstrument, final PreOrderCheck preOrderCheck) {
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

  public boolean updateFillOld(final Order order, final int referencePrice, final long referenceQuantity,
      final ExecutionReportMessage execReport, final double quotedUsdMark, final double settleCoinUsdMark, final double quotedCoinUsdMark,
      final boolean isMaker, final Order causingMessage, final int counterpartyId) {

    try {
      final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());
      final User user = order.getUser();
      final Position[] positionArr = user.getPositionArr();
      final Fee fee = liquidityPair.getFee(user.getFeeTier(), isMaker, null);

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
      }

      // System.out.println(" >>> FEE from " + order.getFeeEstimatedQuantity() + " " + order.getFeeAccumulatedQuantity());
      order.incrementFeeAccumulatedQuantity(feeQuantity);
      // System.out.println(" >>> FEE to " + order.getFeeEstimatedQuantity() + " " + order.getFeeAccumulatedQuantity());

      Position feePosition = positionArr[fee.getFeeInstrumentId()];
      Position basePosition = positionArr[instrumentPair.getBaseId()];
      Position quotedPosition = positionArr[instrumentPair.getQuotedId()];

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

      final long normalizedQuantityLong = normalizeQuantity(instrumentPair, referenceQuantity);
      long amount = normalizedQuantityLong * referencePrice;
      for (int i = 0; i < instrumentPair.getBase().getQuantityScale(); i++)
        amount = amount / 10;

      final long normalizedAmountLong = normalizePrice(instrumentPair, amount);

      StringBuilder log = new StringBuilder();
      log.append("\nUser: ").append(user.getId()).append("\n");
      log.append("feePosition: ").append(feePosition.getAvailableQuantity()).append(" ");
      log.append("basePosition: ").append(basePosition.getAvailableQuantity()).append(" ");
      log.append("quotedPosition: ").append(quotedPosition.getAvailableQuantity()).append(" ");
      log.append("referencePrice: ").append(referencePrice).append(" ");
      log.append("referenceQuantity: ").append(referenceQuantity).append(" ");
      log.append("normalizedQuantityLong: ").append(normalizedQuantityLong).append(" ");
      log.append("amountb4: ").append(normalizedQuantityLong * referencePrice).append(" ");
      log.append("amount: ").append(amount).append(" ");
      log.append("normalizedAmountLong: ").append(normalizedAmountLong).append(" ");
      log.append("feeQuantity: ").append(feeQuantity).append(" \n");
      //final Position position = user.getPosition(instrumentPair.getId());
      //final UserOpenOrdersByPair userOpenOrdersByPair = position.getUserOpenOrdersByPair();

      switch (order.getType()) {
        case SELL_LIMIT:
        case SELL_MARKET:
        case SELL_SELECT:
        case STOP_SELL_LIMIT:
          basePosition.addQuantity(-normalizedQuantityLong); // fill
          if (execReport.getAssetId() > 0) { // groups are handled separately
            basePosition.removeAssetId(execReport.getAssetId(), execReport.getTokenId(), execReport.getGroupAssetId());
          }

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
            //userOpenOrdersByPair.remove(order);
            if (instrumentPair.getQuotedId() == fee.getFeeInstrumentId()) {
              quotedPosition.addAvailableQuantity(order.getFeeUncollectedQuantity());
            }
            if (basePosition != null)
              basePosition.addAvailableQuantity(order.getAvailableUncollectedQuantity(), order.getUser().getId(), order.getGroupAssetId());
          }

          // copy positions to execReport after updateFill
          user.copySetPositionArr(execReport);

          execReport.setFeeEstimatedQuantity(order.getFeeEstimatedQuantity());
          execReport.setFeeAccumulatedQuantity(order.getFeeAccumulatedQuantity());
          execReport.setAvailableEstimatedQuantity(order.getAvailableEstimatedQuantity());
          execReport.setAvailableAccumulatedQuantity(order.getAvailableAccumulatedQuantity());


          log.append("feePosition: ").append(feePosition.getAvailableQuantity()).append(" ");
          log.append("basePosition: ").append(basePosition.getAvailableQuantity()).append(" ");
          log.append("quotedPosition: ").append(quotedPosition.getAvailableQuantity()).append(" \n");
          LOGGER.info(log.toString());
          execReport.setSecurityId(liquidityPair.getId()); //override securityId
          MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(execReport);
          return true;
        case BUY_LIMIT:
        case BUY_MARKET:
        case BUY_SELECT:
        case STOP_BUY_LIMIT:
          basePosition.addQuantity(normalizedQuantityLong); // fill
          basePosition.addAvailableQuantity(normalizedQuantityLong, order.getUser().getId(), order.getGroupAssetId()); // fill
          // (assetId, tokenId > 0 && groupId == 0) or (assetId, tokenId == 0 && groupId > 0)
          basePosition.addAssetId(execReport.getAssetId(), execReport.getTokenId(), execReport.getGroupAssetId());

          quotedPosition.addQuantity(-normalizedAmountLong); // fill

          long adjustment = normalizedQuantityLong * order.getMarginCheckReferencePrice();
          for (int i = 0; i < instrumentPair.getBase().getQuantityScale(); i++)
            adjustment = adjustment / 10;
          quotedPosition.addAvailableQuantity(normalizePrice(instrumentPair, adjustment - amount));

          feePosition.addQuantity(-feeQuantity);
          order.incrementAvailableAccumulatedQuantity(normalizedAmountLong);
          order.incrementAvailableAccumulatedQuantity(normalizePrice(instrumentPair, adjustment - amount));

          fee.transferToExchange(feeQuantity);

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

          if (order.getQuantityLong() == 0) {
            user.decrementOpenOrderCount();
            //userOpenOrdersByPair.remove(order);
            if (feeInstrument != null)
              feePosition.addAvailableQuantity(order.getFeeUncollectedQuantity(), 0, 0);
            if (quotedPosition != null)
              quotedPosition.addAvailableQuantity(order.getAvailableUncollectedQuantity(), order.getUser().getId(),order.getGroupAssetId());
          }

          // copy positions to execReport after updateFill
          user.copySetPositionArr(execReport);

          execReport.setFeeEstimatedQuantity(order.getFeeEstimatedQuantity());
          execReport.setFeeAccumulatedQuantity(order.getFeeAccumulatedQuantity());
          execReport.setAvailableEstimatedQuantity(order.getAvailableEstimatedQuantity());
          execReport.setAvailableAccumulatedQuantity(order.getAvailableAccumulatedQuantity());

          log.append("feePosition: ").append(feePosition.getAvailableQuantity()).append(" ");
          log.append("basePosition: ").append(basePosition.getAvailableQuantity()).append(" ");
          log.append("quotedPosition: ").append(quotedPosition.getAvailableQuantity()).append(" \n");
          LOGGER.info(log.toString());

          execReport.setSecurityId(liquidityPair.getId()); //override securityId
          MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(execReport);

/*          // add trade to chart for stats
          try {
            instrumentPair.getTradeHistory().addToChart(System.currentTimeMillis(), referencePrice, referenceQuantity);
          } catch (Exception e) {
            LOGGER.error("error", e);
          }*/

          return true;
        default:
      }

      execReport.buildBalanceAdminMessage(); // sets the current balances from the matching thread

    } catch (Exception e) {
      LOGGER.error("error in updateFill " + order, e);
    }

    return false;
  }

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
                execReport.getTokenId(), execReport.getGroupAssetId(), execReport.getSelectId());
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
                execReport.getTokenId(), execReport.getGroupAssetId(), execReport.getSelectId());

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
        settlePosition.addAvailableQuantity(settleQuantityChange, user.getId(), order.getGroupAssetId());
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
      MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(execReport);
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
        settlePosition.addAvailableQuantity(settleQuantityChange, user.getId(), order.getGroupAssetId());
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
      MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(execReport);

      // add trade to chart for stats
      instrumentPair.getTradeHistory().addToChart(System.currentTimeMillis(), referencePrice, referenceQuantity);
      return true;
    } catch (Exception e) {
      LOGGER.error("error in updateFill " + order, e);
    }
    return false;
  }

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
          if (com.solfini.internal.admin.schema.AssetType.ASSET == position.getAssetType()) {
            // collateral coin assets
            final Instrument instrument = InstrumentCache.get(position.getInstrumentId());
            if (instrument != null) {
              final boolean stakeSymbol = Context.getStakeSymbolIdMap().contains(instrument.getId());
              final boolean vToken = Context.getVtokenSymbolIdMap().contains(instrument.getId());

              if (stakeSymbol) {
                continue;
              }

              if (vToken) {
                continue;
              }
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
            // skip the pair created to hold liquidity orderbook
/*            if (Context.getLiquidityInstrumentPairId() == position.getInstrumentId()) {
              continue;
            }*/
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
                if (instrumentPair.getAssetType() != com.solfini.internal.admin.schema.AssetType.PAIR) { // don't include spot open orders in exposure
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

/*              if (Context.getEnableDetailLogsForUserId() == user.getId()) {
                StringBuilder sb = new StringBuilder();
                sb.append("symbol=").append(instrumentPair.getSymbol()).append(" symbolId=").append(instrumentPair.getId())
                    .append(", quantity=").append(quantity).append(", usdMark=").append(usdMark).append(", notional=")
                    .append(notional).append(", usdNotional=").append(usdNotional)
                    .append(", unrealized=").append(unrealized)
                    .append(", absNotional=").append(absNotional)
                    .append(", requiredMargin=").append(requiredMargin).append(", maintMargin=")
                    .append(maintMargin);
                LOGGER.info(sb.toString());
              }*/

/*              if (user.getId() == 2 && instrumentPair.getSymbol() != null && instrumentPair.getSymbol().contains("BTC"))
                LOGGER.info("UR: updateRisk instrument: " + instrumentPair.getSymbol() + " unrealized: " + unrealized + " usdMark: " + usdMark
                    + " quantity: " + quantity + " usdNotional: " + usdNotional + " UsdAvgCostBasis: " + position.getUsdAvgCostBasisDouble());*/

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
        if (Context.getEnableDetailLogsForUserId() == user.getId()) {
          StringBuilder sb = new StringBuilder();
          sb.append("usdValue=").append(usdValue).append(" usdMarginableValue=").append(usdMarginableValue)
              .append(", usdNotionalPositionValue=").append(usdNotionalPositionValue).append(", usdOpenOrdersRequiredValue=").append(usdOpenOrdersRequiredValue).append(", usdMaxExposurePositionAndOpenOrdersValue=")
              .append(usdMaxExposurePositionAndOpenOrdersValue).append(", usdMarginRequiredValue=").append(usdMarginRequiredValue)
              .append(", usdMarginMaintValue=").append(usdMarginMaintValue)
              .append(", usdUnrealized=").append(usdUnrealized)
              .append(", usdCollateralValue=").append(usdCollateralValue).append(", otherCoinCollateralValue=")
              .append(otherCoinCollateralValue);
          LOGGER.info(sb.toString());
        }
      }
/*      if (user.getId() == 35) {
        LOGGER.info("usdMarginableValue: " + usdMarginableValue + " usdMarginMaintValue: " + usdMarginMaintValue + " usdMarginRequiredValue: " + usdMarginRequiredValue
            + " usdOpenOrdersRequiredValue: " + usdOpenOrdersRequiredValue + " usdValue: " + usdValue + " usdNotionalPositionValue: " + usdNotionalPositionValue +
            " usdUnrealized: " + usdUnrealized + " usdCollateralValue: " + usdCollateralValue + " otherCoinCollateralValue: " + otherCoinCollateralValue);
      }*/
      adjustAndSetRiskValues(user, usdMarginableValue, usdMarginMaintValue, usdMarginRequiredValue, usdOpenOrdersRequiredValue, positionArr,
          usdValue, usdMaxExposurePositionAndOpenOrdersValue, leverageRatio, usdNotionalPositionValue, usdUnrealized, usdCollateralValue,
          otherCoinCollateralValue);

    } catch (Exception e) {
      e.printStackTrace();
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
    if (isLIQUIDATON_MODE()
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


  // convert pair quantity scale to base instrument quantity scale
  private static long normalizeQuantity(final InstrumentPair instrumentPair, long quantity) {
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
  private static long normalizePrice(final InstrumentPair instrumentPair, long price) {
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

  private int getRiskPriceForSell(final InstrumentPair instrumentPair, int orderPrice) {
/*    long markPrice = MbxMath.changeScale(instrumentPair.getUsdMark(), 2);
    int bidPrice = bidLevelCachePtrArr[0] == 0 ? 0 : bidLevelCachePtrArr[0];

    return (int) Math.max(Math.max(orderPrice, markPrice), bidPrice);*/

    return (int) MbxMath.changeScale(instrumentPair.getUsdMark(), 2);
  }

  private static double getUsdMark(final InstrumentPair pair) {
    final double usdMark = pair.getIndexFeedUsdMark();
    if (usdMark > 0)
      return usdMark;
    else
      return pair.getOrderBook().getUsdMark();
  }
}
