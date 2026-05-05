package com.solfini.matchengine.message.outbound;

import com.solfini.matchengine.orderbook.GlobalOrderBook;
import java.util.Arrays;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.persist.Persister;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.PersistExecutionReportObjectPool;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.ExecRestatementReason;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.ExecutionReportDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.QuoteType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserStats;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Chris Mack
 */
public class ExecutionReportMessage extends Message {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExecutionReportMessage.class);

  private int securityId;
  private String clOrdId;
  private String symbol;
  private Side side;
  private OrdType ordType;
  private int account;
  private int submitterId;
  private long orderId;
  private long secondaryOrderId; // orderId grouped by instrumentPair
  private long origOrderId; // set when stop order triggered
  private long execId;
  private long secondaryExecId;
  private long cancelId; // set when order is cancelled
  private int counterpartyId;
  private int targetStrategy; // used for special order types such as Reduce-Only
  private boolean isHidden;
  private boolean isLiquidation;
  private boolean isLastLook;
  private long orderQty;
  private short orderQtyScale;
  private long leavesQty;
  private short leavesQtyScale;
  private long cumQty;
  private short cumQtyScale;
  private long price;
  private short priceScale;
  private long avgPx;
  private short avgPxScale;
  private long lastPx;
  private short lastPxScale;
  private long lastQty;
  private short lastQtyScale;
  private long stopPx;
  private short stopPxScale;
  private TimeInForce timeInForce;
  private long expireTime;
  private long timestamp;
  private Side aggressorSide; // taker side
  private long price2;
  private short price2Scale;
  private long expireTimeMillis;
  private boolean isPositionSideCrossed; // flag indicating if this is the second execution, crossing position sides

  private long assetId;
  private int tokenId;
  private long groupAssetId;

  // selectId is used for
  // 1. selected order id in SelectArrayOrderBook,
  // 2. influencer subscription id in LiquidityOrderBook
  // 3. execId of the last copy trade when targetStrategy is 198 or 199 (earnings and commission)
  private long selectId;
  private QuoteType quoteType;
  private int quoteTargetUserId;

  private ExecType execType;
  private ExecRestatementReason execRestatementReason;
  private OrdStatus ordStatus;
  private BalanceAdminMessage balanceAdminMessage; // set after a trade, get from persister

  // balance update
  private int basePositionId;
  private long basePositionQuantity;
  private long basePositionQuantityChange;
  private int quotedPositionId;
  private long quotedPositionQuantity;
  private long quotedPositionQuantityChange;
  private int feePositionId;
  private long feePositionQuantity;
  private long feePositionQuantityChange;
  private int settlePositionId;
  private long settlePositionQuantity;
  private long settlePositionQuantityChange;
  private double unrealizedUsd;
  private double realizedUsd;
  private double avgCostBasisUsd;
  private double quotedUsdMark;
  private double settleCoinUsdMark;
  private double settleCoinUnrealized;
  private double settleCoinRealized;

  private long feeEstimatedQuantity;
  private long feeAccumulatedQuantity;
  private long availableEstimatedQuantity;
  private long availableAccumulatedQuantity;

  private boolean isPaidToInsurance; // internal use only

  private Position[] positionArr = new Position[Math.max(InstrumentCache.getPairCapacity(), 64)]; // copied from user
  private int positionsLength = 0;

  private long openOrderCount = -1;
  private long bestBidPx = 0;
  private long bestAskPx = 0;
  private short bestPxScale = 0;
  private double notional;
  private short cancelType;
  //# of concurrents threads i.e. PersistThread, EncoderThread
  private final AtomicInteger concurrentUsageCount = new AtomicInteger(2);

  public ExecutionReportMessage() {
    timestamp = System.currentTimeMillis();
  }

  @Override
  public void clear() {
    super.clear();

    securityId = 0;
    clOrdId = null;
    symbol = null;
    side = null;
    ordType = null;
    account = 0;
    submitterId = 0;
    orderId = 0;
    secondaryOrderId = 0;
    origOrderId = 0;
    execId = 0;
    secondaryExecId = 0;
    cancelId = 0;
    counterpartyId = 0;
    targetStrategy = 0;
    isHidden = false;
    isLiquidation = false;
    isLastLook = false;
    orderQty = 0;
    orderQtyScale = 0;
    leavesQty = 0;
    leavesQtyScale = 0;
    cumQty = 0;
    cumQtyScale = 0;
    price = 0;
    priceScale = 0;
    avgPx = 0;
    avgPxScale = 0;
    lastPx = 0;
    lastPxScale = 0;
    lastQty = 0;
    lastQtyScale = 0;
    stopPx = 0;
    stopPxScale = 0;
    timeInForce = null;
    expireTime = 0;
    timestamp = 0;
    aggressorSide = null;
    price2 = 0;
    price2Scale = 0;
    expireTimeMillis = 0;
    isPositionSideCrossed = false;

    execType = null;
    execRestatementReason = null;
    ordStatus = null;
    balanceAdminMessage = null;

    basePositionId = 0;
    basePositionQuantity = 0;
    basePositionQuantityChange = 0;
    quotedPositionId = 0;
    quotedPositionQuantity = 0;
    quotedPositionQuantityChange = 0;
    feePositionId = 0;
    feePositionQuantity = 0;
    feePositionQuantityChange = 0;
    settlePositionId = 0;
    settlePositionQuantity = 0;
    settlePositionQuantityChange = 0;
    unrealizedUsd = 0;
    realizedUsd = 0;
    avgCostBasisUsd = 0;
    quotedUsdMark = 0;
    settleCoinUsdMark = 0;
    settleCoinUnrealized = 0;
    settleCoinRealized = 0;

    isPaidToInsurance = false;

    positionArr = new Position[Math.max(InstrumentCache.getPairCapacity(), 64)];
    positionsLength = 0;

    openOrderCount = 0;
    bestBidPx = 0;
    bestAskPx = 0;
    bestPxScale = 0;
    notional = 0;
    feeEstimatedQuantity = 0;
    feeAccumulatedQuantity = 0;
    availableEstimatedQuantity = 0;
    availableAccumulatedQuantity = 0;
    cancelType = 0;

    assetId = 0;
    tokenId = 0;
    groupAssetId = 0;

    selectId = 0;
    quoteType = null;
    quoteTargetUserId = 0;
    concurrentUsageCount.set(2);
  }

  public static final ExecutionReportMessage createAckNewOrderExecutionReport(final Order order, final InstrumentPair pair) {
    final ExecutionReportMessage executionReportMessage = ExecutionReportObjectPool.get();
    executionReportMessage.securityId = order.getSecurityId();
    executionReportMessage.user = order.getUser();
    executionReportMessage.clOrdId = order.getClOrdId();
    executionReportMessage.symbol = pair.getSymbol();
    executionReportMessage.side = order.getSide();
    executionReportMessage.ordType = order.getOrdType();
    executionReportMessage.account = order.getAccount();
    executionReportMessage.submitterId = order.getSubmitterId();
    executionReportMessage.cancelId = 0;
    executionReportMessage.orderId = order.getOrderId();
    executionReportMessage.secondaryOrderId = order.getSecondaryOrderId();
    executionReportMessage.origOrderId = order.getOrigOrderId();
    executionReportMessage.orderQty = order.getQty();
    executionReportMessage.orderQtyScale = order.getQtyScale();
    executionReportMessage.leavesQty = order.getQty();
    executionReportMessage.leavesQtyScale = order.getQtyScale();
    executionReportMessage.price = order.getPrice();
    executionReportMessage.priceScale = order.getPriceScale();
    executionReportMessage.timeInForce = order.getTimeInForce();
    executionReportMessage.expireTime = order.getExpireTime();
    executionReportMessage.cumQty = 0;
    executionReportMessage.cumQtyScale = 0;
    executionReportMessage.avgPx = 0;
    executionReportMessage.avgPxScale = 0;
    executionReportMessage.lastQty = 0;
    executionReportMessage.lastQtyScale = 0;
    executionReportMessage.execType = ExecType.NEW;
    executionReportMessage.ordStatus = OrdStatus.NEW;
    executionReportMessage.stopPx = order.getStopPx();
    executionReportMessage.stopPxScale = order.getStopPxScale();
    executionReportMessage.timestamp = System.currentTimeMillis();
    executionReportMessage.sourceSeqNum = order.getSourceSeqNum();
    executionReportMessage.sourceSendTime = order.getSourceSendTime();
    executionReportMessage.kafkaRecordOffset = order.getKafkaRecordOffset();
    executionReportMessage.price2 = order.getPrice2();
    executionReportMessage.price2Scale = order.getPrice2Scale();
    executionReportMessage.isPositionSideCrossed = false;
    executionReportMessage.setSenderCompId(order.getSenderCompId());
    executionReportMessage.targetStrategy = order.getTargetStrategy();
    executionReportMessage.isHidden = order.isHidden();
    executionReportMessage.isLiquidation = order.isLiquidation();
    executionReportMessage.isLastLook = order.isLastLook();
    executionReportMessage.inputTime = order.getInputTime();
    executionReportMessage.decodedTime = order.getDecodedTime();
    executionReportMessage.matchTime = TimeUtil.getTime();
    executionReportMessage.feeEstimatedQuantity = order.getFeeEstimatedQuantity();
    executionReportMessage.feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
    executionReportMessage.availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
    executionReportMessage.availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();

    executionReportMessage.assetId = order.getAssetId();
    executionReportMessage.tokenId = order.getTokenId();
    executionReportMessage.groupAssetId = order.getGroupAssetId();
    executionReportMessage.selectId = order.getSelectId();
    executionReportMessage.quoteType = order.getQuoteType();
    executionReportMessage.quoteTargetUserId = order.getQuoteTargetUserId();


    executionReportMessage.openOrderCount = order.getUser().getOpenOrderCount();
    if (order.getOrdType() == OrdType.LIMIT) {
      final OrderBook orderBook = pair.getOrderBook();
      if (orderBook != null) {
        executionReportMessage.bestBidPx = orderBook.getBid();
        executionReportMessage.bestAskPx = orderBook.getAsk();
      }
      executionReportMessage.bestPxScale = pair.getPriceScale();
    }

    order.getUser().copySetPositionArr(executionReportMessage);

    return executionReportMessage;
  }

  public static final ExecutionReportMessage createAckNewOrderRejectExecutionReport(final Order order, final InstrumentPair instrument) {
    final ExecutionReportMessage executionReportMessage = ExecutionReportObjectPool.get();
    executionReportMessage.securityId = order.getSecurityId();
    executionReportMessage.user = order.getUser();
    executionReportMessage.clOrdId = order.getClOrdId();
    executionReportMessage.symbol = instrument.getSymbol();
    executionReportMessage.side = order.getSide();
    executionReportMessage.ordType = order.getOrdType();
    executionReportMessage.account = order.getAccount();
    executionReportMessage.submitterId = order.getSubmitterId();
    executionReportMessage.cancelId = 0;
    executionReportMessage.orderId = order.getOrderId();
    executionReportMessage.secondaryOrderId = order.getSecondaryOrderId();
    executionReportMessage.origOrderId = order.getOrigOrderId();
    executionReportMessage.orderQty = order.getQty();
    executionReportMessage.orderQtyScale = order.getQtyScale();
    executionReportMessage.leavesQty = 0;
    executionReportMessage.leavesQtyScale = 0;
    executionReportMessage.price = order.getPrice();
    executionReportMessage.priceScale = order.getPriceScale();
    executionReportMessage.timeInForce = order.getTimeInForce();
    executionReportMessage.expireTime = order.getExpireTime();
    executionReportMessage.targetStrategy = order.getTargetStrategy();
    executionReportMessage.isHidden = order.isHidden();
    executionReportMessage.isLiquidation = order.isLiquidation();
    executionReportMessage.isLastLook = order.isLastLook();
    executionReportMessage.cumQty = 0;
    executionReportMessage.cumQtyScale = 0;
    executionReportMessage.avgPx = 0;
    executionReportMessage.avgPxScale = 0;
    executionReportMessage.lastQty = 0;
    executionReportMessage.lastQtyScale = 0;
    executionReportMessage.execType = ExecType.NEW;
    executionReportMessage.ordStatus = OrdStatus.REJECTED;
    executionReportMessage.stopPx = order.getStopPx();
    executionReportMessage.stopPxScale = order.getStopPxScale();
    executionReportMessage.timestamp = System.currentTimeMillis();
    executionReportMessage.sourceSeqNum = order.getSourceSeqNum();
    executionReportMessage.sourceSendTime = order.getSourceSendTime();
    executionReportMessage.kafkaRecordOffset = order.getKafkaRecordOffset();
    executionReportMessage.price2 = order.getPrice2();
    executionReportMessage.price2Scale = order.getPrice2Scale();
    executionReportMessage.isPositionSideCrossed = false;
    executionReportMessage.setSenderCompId(order.getSenderCompId());
    executionReportMessage.inputTime = order.getInputTime();
    executionReportMessage.decodedTime = order.getDecodedTime();
    executionReportMessage.matchTime = TimeUtil.getTime();
    executionReportMessage.feeEstimatedQuantity = order.getFeeEstimatedQuantity();
    executionReportMessage.feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
    executionReportMessage.availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
    executionReportMessage.availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();
    executionReportMessage.assetId = order.getAssetId();
    executionReportMessage.tokenId = order.getTokenId();
    executionReportMessage.groupAssetId = order.getGroupAssetId();
    executionReportMessage.selectId = order.getSelectId();
    executionReportMessage.quoteType = order.getQuoteType();
    executionReportMessage.quoteTargetUserId = order.getQuoteTargetUserId();
    order.getUser().copySetPositionArr(executionReportMessage);

    return executionReportMessage;
  }

  public static final ExecutionReportMessage createAckCancelOrderExecutionReport(final CancelOrder cancelOrder,
      final InstrumentPair instrument) {
    final ExecutionReportMessage executionReportMessage = ExecutionReportObjectPool.get();
    executionReportMessage.securityId = cancelOrder.getSecurityId();
    executionReportMessage.user = cancelOrder.getUser();
    executionReportMessage.clOrdId = cancelOrder.getClOrdId();
    executionReportMessage.symbol = instrument.getSymbol();
    executionReportMessage.side = cancelOrder.getSide();
    executionReportMessage.ordType = cancelOrder.getOrdType();
    executionReportMessage.account = cancelOrder.getAccount();
    executionReportMessage.submitterId = cancelOrder.getSubmitterId();
    executionReportMessage.cancelId = cancelOrder.getCancelId();
    executionReportMessage.orderId = cancelOrder.getOrigOrderId();
    executionReportMessage.secondaryOrderId = cancelOrder.getSecondaryOrderId();
    executionReportMessage.origOrderId = cancelOrder.getOrigOrderId();
    executionReportMessage.orderQty = cancelOrder.getQty();
    executionReportMessage.orderQtyScale = cancelOrder.getQtyScale();
    executionReportMessage.leavesQty = cancelOrder.getQty();
    executionReportMessage.leavesQtyScale = cancelOrder.getQtyScale();
    executionReportMessage.price = cancelOrder.getPrice();
    executionReportMessage.priceScale = instrument.getPriceScale();
    executionReportMessage.cumQty = 0;
    executionReportMessage.cumQtyScale = 0;
    executionReportMessage.avgPx = 0;
    executionReportMessage.avgPxScale = 0;
    executionReportMessage.lastQty = 0;
    executionReportMessage.lastQtyScale = 0;
    executionReportMessage.isPositionSideCrossed = false;
    executionReportMessage.execType = ExecType.PENDING_CANCEL;
    executionReportMessage.ordStatus = OrdStatus.PENDING_CANCEL;
    executionReportMessage.openOrderCount = cancelOrder.getUser().getOpenOrderCount();

    executionReportMessage.timestamp = System.currentTimeMillis();
    executionReportMessage.sourceSeqNum = cancelOrder.getSourceSeqNum();
    executionReportMessage.sourceSendTime = cancelOrder.getSourceSendTime();
    executionReportMessage.kafkaRecordOffset = cancelOrder.getKafkaRecordOffset();

    executionReportMessage.setSenderCompId(cancelOrder.getSenderCompId());
    executionReportMessage.inputTime = cancelOrder.getInputTime();
    executionReportMessage.decodedTime = cancelOrder.getDecodedTime();
    executionReportMessage.matchTime = TimeUtil.getTime();
    executionReportMessage.feeEstimatedQuantity = cancelOrder.getFeeEstimatedQuantity();
    executionReportMessage.feeAccumulatedQuantity = cancelOrder.getFeeAccumulatedQuantity();
    executionReportMessage.availableEstimatedQuantity = cancelOrder.getAvailableEstimatedQuantity();
    executionReportMessage.availableAccumulatedQuantity = cancelOrder.getAvailableAccumulatedQuantity();

    cancelOrder.getUser().copySetPositionArr(executionReportMessage);

    return executionReportMessage;
  }

  public static final ExecutionReportMessage createAckCancelOrderExecutionReport(final CancelReplaceOrder cancelOrder,
      final InstrumentPair instrument) {
    final ExecutionReportMessage executionReportMessage = ExecutionReportObjectPool.get();
    executionReportMessage.securityId = cancelOrder.getSecurityId();
    executionReportMessage.user = cancelOrder.getUser();
    executionReportMessage.clOrdId = cancelOrder.getClOrdId();
    executionReportMessage.symbol = instrument.getSymbol();
    executionReportMessage.side = cancelOrder.getSide();
    executionReportMessage.ordType = cancelOrder.getOrdType();
    executionReportMessage.account = cancelOrder.getAccount();
    executionReportMessage.submitterId = cancelOrder.getSubmitterId();
    executionReportMessage.cancelId = cancelOrder.getCancelId();
    executionReportMessage.orderId = cancelOrder.getOrigOrderId();
    executionReportMessage.secondaryOrderId = cancelOrder.getSecondaryOrderId();
    executionReportMessage.origOrderId = cancelOrder.getOrigOrderId();
    executionReportMessage.orderQty = cancelOrder.getQty();
    executionReportMessage.orderQtyScale = cancelOrder.getQtyScale();
    executionReportMessage.leavesQty = cancelOrder.getQty();
    executionReportMessage.leavesQtyScale = cancelOrder.getQtyScale();
    executionReportMessage.price = cancelOrder.getPrice();
    executionReportMessage.priceScale = instrument.getPriceScale();
    executionReportMessage.cumQty = 0;
    executionReportMessage.cumQtyScale = 0;
    executionReportMessage.avgPx = 0;
    executionReportMessage.avgPxScale = 0;
    executionReportMessage.lastQty = 0;
    executionReportMessage.lastQtyScale = 0;
    executionReportMessage.isPositionSideCrossed = false;
    executionReportMessage.execType = ExecType.PENDING_CANCEL;
    executionReportMessage.ordStatus = OrdStatus.PENDING_CANCEL;
    executionReportMessage.timestamp = System.currentTimeMillis();
    executionReportMessage.sourceSeqNum = cancelOrder.getSourceSeqNum();
    executionReportMessage.sourceSendTime = cancelOrder.getSourceSendTime();
    executionReportMessage.kafkaRecordOffset = cancelOrder.getKafkaRecordOffset();

    executionReportMessage.assetId = cancelOrder.getAssetId();
    executionReportMessage.tokenId = cancelOrder.getTokenId();
    executionReportMessage.groupAssetId = cancelOrder.getGroupAssetId();

    executionReportMessage.selectId = cancelOrder.getSelectId();
    executionReportMessage.setSenderCompId(cancelOrder.getSenderCompId());
    executionReportMessage.inputTime = cancelOrder.getInputTime();
    executionReportMessage.decodedTime = cancelOrder.getDecodedTime();
    executionReportMessage.matchTime = TimeUtil.getTime();
    final Order order = cancelOrder.getOrder();
    if (order != null) {
      executionReportMessage.feeEstimatedQuantity = order.getFeeEstimatedQuantity();
      executionReportMessage.feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
      executionReportMessage.availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
      executionReportMessage.availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();
    }

    cancelOrder.getUser().copySetPositionArr(executionReportMessage);

    return executionReportMessage;
  }

  public static final ExecutionReportMessage createCancelExecutionReport(final long cancelId, final Order order, final InstrumentPair pair,
      final Message causingMessage, final int cancelType) {
    final ExecutionReportMessage executionReportMessage = ExecutionReportObjectPool.get();
    executionReportMessage.securityId = order.getSecurityId();
    executionReportMessage.user = order.getUser();
    executionReportMessage.clOrdId = order.getClOrdId();
    executionReportMessage.symbol = pair.getSymbol();
    executionReportMessage.side = order.getSide();
    executionReportMessage.ordType = order.getOrdType();
    executionReportMessage.account = order.getAccount();
    executionReportMessage.submitterId = order.getSubmitterId();
    executionReportMessage.cancelId = cancelId;
    executionReportMessage.orderId = order.getOrderId();
    executionReportMessage.secondaryOrderId = order.getSecondaryOrderId();
    executionReportMessage.origOrderId = order.getOrigOrderId();
    executionReportMessage.cancelType = (short) cancelType;

    executionReportMessage.orderQty = order.getQty();
    executionReportMessage.orderQtyScale = order.getQtyScale();
    executionReportMessage.leavesQty = 0;
    executionReportMessage.leavesQtyScale = 0;
    executionReportMessage.price = order.getPrice();
    executionReportMessage.priceScale = order.getPriceScale();
    executionReportMessage.isPositionSideCrossed = false;
    executionReportMessage.cumQty = order.getQuantityOrigLong() - order.getQuantityLong();
    executionReportMessage.cumQtyScale = pair.getQuantityScale();
    executionReportMessage.lastQty = 0;
    executionReportMessage.lastQtyScale = 0;

    executionReportMessage.execType = ExecType.CANCELED;
    executionReportMessage.ordStatus = OrdStatus.CANCELED;
    executionReportMessage.timeInForce = order.getTimeInForce();
    executionReportMessage.expireTime = order.getExpireTime();
    executionReportMessage.timestamp = System.currentTimeMillis();
    executionReportMessage.targetStrategy = order.getTargetStrategy();
    executionReportMessage.isHidden = order.isHidden();
    executionReportMessage.isLiquidation = order.isLiquidation();
    executionReportMessage.isLastLook = order.isLastLook();
    executionReportMessage.sourceSeqNum = causingMessage.getSourceSeqNum();
    executionReportMessage.sourceSendTime = causingMessage.getSourceSendTime();
    executionReportMessage.kafkaRecordOffset = causingMessage.getKafkaRecordOffset();
    executionReportMessage.price2 = order.getPrice2();
    executionReportMessage.price2Scale = order.getPrice2Scale();
    executionReportMessage.openOrderCount = order.getUser().getOpenOrderCount();

    executionReportMessage.setSenderCompId(order.getSenderCompId());
    executionReportMessage.setSenderCompId(order.getSenderCompId());
    executionReportMessage.inputTime = causingMessage.getInputTime();
    executionReportMessage.decodedTime = causingMessage.getDecodedTime();
    executionReportMessage.matchTime = TimeUtil.getTime();
    executionReportMessage.feeEstimatedQuantity = order.getFeeEstimatedQuantity();
    executionReportMessage.feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
    executionReportMessage.availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
    executionReportMessage.availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();
    executionReportMessage.assetId = order.getAssetId();
    executionReportMessage.tokenId = order.getTokenId();
    executionReportMessage.groupAssetId = order.getGroupAssetId();
    executionReportMessage.selectId = order.getSelectId();
    executionReportMessage.quoteType = order.getQuoteType();
    executionReportMessage.quoteTargetUserId = order.getQuoteTargetUserId();

    // cumNotional, avgPx
    executionReportMessage.avgPx = executionReportMessage.cumQty == 0 ? 0 : (order.getFillCumNotional() / executionReportMessage.cumQty);
    executionReportMessage.avgPxScale = pair.getPriceScale();

    order.getUser().copySetPositionArr(executionReportMessage);

    return executionReportMessage;
  }

  public static final ExecutionReportMessage createTradeExecutionReport(final Order order, final InstrumentPair pair, final long fillPrice,
      final short fillPriceScale, final long lastQty, final short lastQtyScale, final long execId, final long secondaryExecId,
      final Order causingMessage, final int counterpartyId, final boolean isPositionSideCrossed, final long assetId, final int tokenId,
      final long groupAssetId, final long selectId) {
    final ExecutionReportMessage executionReportMessage = ExecutionReportObjectPool.get();

    executionReportMessage.securityId = order.getSecurityId();
    executionReportMessage.user = order.getUser();
    executionReportMessage.clOrdId = order.getClOrdId();
    executionReportMessage.symbol = pair.getSymbol();
    executionReportMessage.side = order.getSide();
    executionReportMessage.ordType = order.getOrdType();
    executionReportMessage.account = order.getAccount();
    executionReportMessage.submitterId = order.getSubmitterId();
    executionReportMessage.cancelId = 0;
    executionReportMessage.orderId = order.getOrderId();
    executionReportMessage.secondaryOrderId = order.getSecondaryOrderId();
    executionReportMessage.origOrderId = order.getOrigOrderId();

    executionReportMessage.counterpartyId = counterpartyId;

    executionReportMessage.orderQty = order.getQty();
    executionReportMessage.orderQtyScale = order.getQtyScale();
    executionReportMessage.lastQty = lastQty;
    executionReportMessage.lastQtyScale = lastQtyScale;

    executionReportMessage.price = order.getPrice();
    executionReportMessage.priceScale = order.getPriceScale();
    executionReportMessage.lastPx = fillPrice;
    executionReportMessage.lastPxScale = fillPriceScale;
    executionReportMessage.isPositionSideCrossed = isPositionSideCrossed;
    executionReportMessage.execId = execId;
    executionReportMessage.secondaryExecId = secondaryExecId;
    executionReportMessage.timeInForce = order.getTimeInForce();
    executionReportMessage.expireTime = order.getExpireTime();
    executionReportMessage.timestamp = System.currentTimeMillis();
    executionReportMessage.targetStrategy = order.getTargetStrategy();
    executionReportMessage.isHidden = order.isHidden();
    executionReportMessage.isLiquidation = order.isLiquidation();
    executionReportMessage.isLastLook = order.isLastLook();
    executionReportMessage.price2 = order.getPrice2();
    executionReportMessage.price2Scale = order.getPrice2Scale();
    executionReportMessage.avgPx = 0;
    executionReportMessage.avgPxScale = 0;
    executionReportMessage.execType = ExecType.TRADE;

    if (order.getQuantityLong() > 0) {
      executionReportMessage.ordStatus = OrdStatus.PARTIALLY_FILLED;
      executionReportMessage.leavesQty = order.getQuantityLong();
      executionReportMessage.leavesQtyScale = pair.getQuantityScale();
      executionReportMessage.cumQty = order.getQuantityOrigLong() - order.getQuantityLong();
      executionReportMessage.cumQtyScale = pair.getQuantityScale();
    } else {
      executionReportMessage.ordStatus = OrdStatus.FILLED;
      executionReportMessage.leavesQty = 0;
      executionReportMessage.leavesQtyScale = 0;
      executionReportMessage.cumQty = order.getQuantityOrigLong();
      executionReportMessage.cumQtyScale = pair.getQuantityScale();
    }

    executionReportMessage.openOrderCount = order.getUser().getOpenOrderCount();
    executionReportMessage.setSenderCompId(order.getSenderCompId());
    if (causingMessage != null) {
      executionReportMessage.sourceSeqNum = causingMessage.getSourceSeqNum();
      executionReportMessage.sourceSendTime = causingMessage.getSourceSendTime();
      executionReportMessage.kafkaRecordOffset = causingMessage.getKafkaRecordOffset();
      executionReportMessage.setAggressorSide(causingMessage.getSide());
      executionReportMessage.inputTime = causingMessage.getInputTime();
      executionReportMessage.decodedTime = causingMessage.getDecodedTime();
    }
    executionReportMessage.matchTime = TimeUtil.getTime();
    executionReportMessage.feeEstimatedQuantity = order.getFeeEstimatedQuantity();
    executionReportMessage.feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
    executionReportMessage.availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
    executionReportMessage.availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();


    executionReportMessage.assetId = assetId;
    executionReportMessage.tokenId = tokenId;
    executionReportMessage.groupAssetId = groupAssetId;

    executionReportMessage.selectId = selectId;
    executionReportMessage.quoteType = order.getQuoteType();
    executionReportMessage.quoteTargetUserId = order.getQuoteTargetUserId();

    // cumNotional, avgPx
    final long fillNotional =
        scale(fillPrice, fillPriceScale, pair.getPriceScale()) * scale(lastQty, lastQtyScale, pair.getQuantityScale());
    order.incrementFillNotional(fillNotional);

    executionReportMessage.avgPx = executionReportMessage.cumQty == 0 ? 0 : (order.getFillCumNotional() / executionReportMessage.cumQty);
    executionReportMessage.avgPxScale = pair.getPriceScale();

    if (executionReportMessage.lastQty == 0) {
      executionReportMessage.notional = 0;
    } else {
      executionReportMessage.notional = StringUtil.toDouble(executionReportMessage.lastQty, executionReportMessage.lastQtyScale)
          * StringUtil.toDouble(executionReportMessage.lastPx, executionReportMessage.lastPxScale);
    }

    return executionReportMessage;
  }

  public static final ExecutionReportMessage createFundingExecutionReport(final long orderId, final User user, final int securityId, final String symbol,
      final long fillPrice, final short fillPriceScale, final long quantity, final short quantityScale, final long execId, final long secondaryExecId,
      final int counterpartyId, final int targetStrategy) {
    final ExecutionReportMessage executionReportMessage = ExecutionReportObjectPool.get();

    GlobalOrderBook.setOrderIdIfGreater(user.getId(), orderId);

    executionReportMessage.securityId = securityId;
    executionReportMessage.user = user;
    executionReportMessage.symbol = symbol;
    executionReportMessage.side = Side.SELL;
    executionReportMessage.ordType = OrdType.LIMIT;
    executionReportMessage.account = user.getId();
    executionReportMessage.submitterId = user.getId();
    executionReportMessage.cancelId = 0;
    executionReportMessage.orderId = orderId;
    executionReportMessage.secondaryOrderId = 0;
    executionReportMessage.origOrderId = 0;

    executionReportMessage.counterpartyId = counterpartyId;

    executionReportMessage.orderQty = quantity;
    executionReportMessage.orderQtyScale = quantityScale;
    executionReportMessage.lastQty = quantity;
    executionReportMessage.lastQtyScale = quantityScale;

    executionReportMessage.price = fillPrice;
    executionReportMessage.priceScale = fillPriceScale;
    executionReportMessage.lastPx = fillPrice;
    executionReportMessage.lastPxScale = fillPriceScale;
    executionReportMessage.isPositionSideCrossed = false;
    executionReportMessage.execId = execId;
    executionReportMessage.secondaryExecId = secondaryExecId;
    executionReportMessage.timeInForce = TimeInForce.GOOD_TILL_CANCEL;
    executionReportMessage.expireTime = 0;
    executionReportMessage.timestamp = System.currentTimeMillis();
    executionReportMessage.targetStrategy = targetStrategy;
    executionReportMessage.isHidden = false;
    executionReportMessage.isLiquidation = false;
    executionReportMessage.isLastLook = false;
    executionReportMessage.price2 = 0;
    executionReportMessage.price2Scale = 0;
    executionReportMessage.avgPx = fillPrice;
    executionReportMessage.avgPxScale = fillPriceScale;
    executionReportMessage.execType = ExecType.TRADE;

    executionReportMessage.ordStatus = OrdStatus.CALCULATED;
    executionReportMessage.leavesQty = 0;
    executionReportMessage.leavesQtyScale = 0;
    executionReportMessage.cumQty = quantity;
    executionReportMessage.cumQtyScale = quantityScale;

    executionReportMessage.matchTime = TimeUtil.getTime();
    executionReportMessage.clOrdId = String.valueOf(executionReportMessage.matchTime);
    executionReportMessage.aggressorSide = Side.SELL;
    //executionReportMessage.feeEstimatedQuantity = order.getFeeEstimatedQuantity();
    //executionReportMessage.feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
    //executionReportMessage.availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
    //executionReportMessage.availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();


    executionReportMessage.assetId = 0;
    executionReportMessage.tokenId = 0;
    executionReportMessage.groupAssetId = 0;

    executionReportMessage.selectId = 0;
    executionReportMessage.quoteType = QuoteType.NULL_VAL;
    executionReportMessage.quoteTargetUserId = 0;

    if (executionReportMessage.lastQty == 0) {
      executionReportMessage.notional = 0;
    } else {
      executionReportMessage.notional = StringUtil.toDouble(executionReportMessage.lastQty, executionReportMessage.lastQtyScale)
          * StringUtil.toDouble(executionReportMessage.lastPx, executionReportMessage.lastPxScale);
    }

    return executionReportMessage;
  }

  public static final ExecutionReportMessage createExternalExecutionReport(final long orderId, final User user, final int securityId, final String symbol,
      final long fillPrice, final short fillPriceScale, final long quantity, final short quantityScale, final long execId, final long secondaryExecId,
      final int counterpartyId, final int targetStrategy, final Side side, final long fee) {
    final ExecutionReportMessage executionReportMessage = ExecutionReportObjectPool.get();

    executionReportMessage.securityId = securityId;
    executionReportMessage.user = user;
    executionReportMessage.symbol = symbol;
    executionReportMessage.side = side;
    executionReportMessage.ordType = OrdType.LIMIT;
    if (user != null) {
      executionReportMessage.account = user.getId();
      executionReportMessage.submitterId = user.getId();
    }
    executionReportMessage.cancelId = 0;
    executionReportMessage.orderId = orderId;
    executionReportMessage.secondaryOrderId = 0;
    executionReportMessage.origOrderId = 0;

    executionReportMessage.counterpartyId = counterpartyId;

    executionReportMessage.orderQty = quantity;
    executionReportMessage.orderQtyScale = quantityScale;
    executionReportMessage.lastQty = quantity;
    executionReportMessage.lastQtyScale = quantityScale;

    executionReportMessage.price = fillPrice;
    executionReportMessage.priceScale = fillPriceScale;
    executionReportMessage.lastPx = fillPrice;
    executionReportMessage.lastPxScale = fillPriceScale;
    executionReportMessage.isPositionSideCrossed = false;
    executionReportMessage.execId = execId;
    executionReportMessage.secondaryExecId = secondaryExecId;
    executionReportMessage.timeInForce = TimeInForce.FILL_OR_KILL;
    executionReportMessage.expireTime = 0;
    executionReportMessage.timestamp = System.currentTimeMillis();
    executionReportMessage.targetStrategy = targetStrategy;
    executionReportMessage.isHidden = false;
    executionReportMessage.isLiquidation = false;
    executionReportMessage.isLastLook = false;
    executionReportMessage.price2 = 0;
    executionReportMessage.price2Scale = 0;
    executionReportMessage.avgPx = fillPrice;
    executionReportMessage.avgPxScale = fillPriceScale;
    executionReportMessage.execType = ExecType.TRADE;

    executionReportMessage.ordStatus = OrdStatus.CALCULATED;
    executionReportMessage.leavesQty = 0;
    executionReportMessage.leavesQtyScale = 0;
    executionReportMessage.cumQty = quantity;
    executionReportMessage.cumQtyScale = quantityScale;

    executionReportMessage.matchTime = TimeUtil.getTime();
    executionReportMessage.clOrdId = String.valueOf(executionReportMessage.matchTime);
    executionReportMessage.aggressorSide = Side.SELL;
    //executionReportMessage.feeEstimatedQuantity = order.getFeeEstimatedQuantity();
    executionReportMessage.feeAccumulatedQuantity = fee;
    //executionReportMessage.availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
    //executionReportMessage.availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();

    executionReportMessage.assetId = 0;
    executionReportMessage.tokenId = 0;
    executionReportMessage.groupAssetId = 0;

    executionReportMessage.selectId = 0;
    executionReportMessage.quoteType = QuoteType.NULL_VAL;
    executionReportMessage.quoteTargetUserId = 0;

    if (executionReportMessage.lastQty == 0) {
      executionReportMessage.notional = 0;
    } else {
      executionReportMessage.notional = StringUtil.toDouble(executionReportMessage.lastQty, executionReportMessage.lastQtyScale)
          * StringUtil.toDouble(executionReportMessage.lastPx, executionReportMessage.lastPxScale);
    }

    return executionReportMessage;
  }

  private static long scale(final long value, final int scale1, final int scale2) {
    long result = value;
    for (int i = 0; i < Math.abs(scale1 - scale2); i++) {
      if (scale1 > scale2)
        result /= 10;
      else if (scale2 > scale1)
        result *= 10;
    }
    return result;
  }

  public static final ExecutionReportMessage createOrderEliminationExecutionReport(final Order order, final InstrumentPair pair) {
    final ExecutionReportMessage executionReportMessage = ExecutionReportObjectPool.get();
    executionReportMessage.securityId = order.getSecurityId();
    executionReportMessage.user = order.getUser();
    executionReportMessage.clOrdId = order.getClOrdId();
    executionReportMessage.symbol = pair.getSymbol();
    executionReportMessage.side = order.getSide();
    executionReportMessage.ordType = order.getOrdType();
    executionReportMessage.account = order.getAccount();
    executionReportMessage.submitterId = order.getSubmitterId();
    executionReportMessage.cancelId = 0;
    executionReportMessage.orderId = order.getOrderId();
    executionReportMessage.secondaryOrderId = order.getSecondaryOrderId();
    executionReportMessage.origOrderId = order.getOrigOrderId();

    executionReportMessage.orderQty = order.getQty();
    executionReportMessage.orderQtyScale = order.getQtyScale();
    executionReportMessage.price = order.getPrice();
    executionReportMessage.priceScale = order.getPriceScale();
    executionReportMessage.isPositionSideCrossed = false;
    executionReportMessage.timeInForce = order.getTimeInForce();
    executionReportMessage.expireTime = order.getExpireTime();
    executionReportMessage.targetStrategy = order.getTargetStrategy();
    executionReportMessage.isHidden = order.isHidden();
    executionReportMessage.isLiquidation = order.isLiquidation();
    executionReportMessage.isLastLook = order.isLastLook();
    executionReportMessage.leavesQty = 0;
    executionReportMessage.leavesQtyScale = 0;
    executionReportMessage.cumQty = order.getQuantityOrigLong() - order.getQuantityLong();
    executionReportMessage.cumQtyScale = pair.getQuantityScale();
    executionReportMessage.lastQty = 0;
    executionReportMessage.lastQtyScale = 0;
    executionReportMessage.price2 = order.getPrice2();
    executionReportMessage.price2Scale = order.getPrice2Scale();
    executionReportMessage.stopPx = order.getStopPx();
    executionReportMessage.stopPxScale = order.getStopPxScale();

    executionReportMessage.execType = ExecType.EXPIRED;
    executionReportMessage.ordStatus = OrdStatus.EXPIRED;
    executionReportMessage.openOrderCount = order.getUser().getOpenOrderCount();
    executionReportMessage.timestamp = System.currentTimeMillis();

    executionReportMessage.setSenderCompId(order.getSenderCompId());
    executionReportMessage.sourceSeqNum = order.getSourceSeqNum();
    executionReportMessage.sourceSendTime = order.getSourceSendTime();
    executionReportMessage.kafkaRecordOffset = order.getKafkaRecordOffset();

    executionReportMessage.inputTime = order.getInputTime();
    executionReportMessage.decodedTime = order.getDecodedTime();
    executionReportMessage.matchTime = TimeUtil.getTime();
    executionReportMessage.feeEstimatedQuantity = order.getFeeEstimatedQuantity();
    executionReportMessage.feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
    executionReportMessage.availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
    executionReportMessage.availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();
    executionReportMessage.assetId = order.getAssetId();
    executionReportMessage.tokenId = order.getTokenId();
    executionReportMessage.groupAssetId = order.getGroupAssetId();

    executionReportMessage.selectId = order.getSelectId();
    executionReportMessage.quoteType = order.getQuoteType();
    executionReportMessage.quoteTargetUserId = order.getQuoteTargetUserId();

    // cumNotional, avgPx
    executionReportMessage.avgPx = executionReportMessage.cumQty == 0 ? 0 : (order.getFillCumNotional() / executionReportMessage.cumQty);
    executionReportMessage.avgPxScale = pair.getPriceScale();

    order.getUser().copySetPositionArr(executionReportMessage);

    return executionReportMessage;
  }

  public static final ExecutionReportMessage createRestateExecutionReport(final Order order, final InstrumentPair pair,
      final ExecRestatementReason reason, final long snapId, final Message causingMessage) {
    final ExecutionReportMessage executionReportMessage = ExecutionReportObjectPool.get();
    executionReportMessage.snapId = snapId;
    executionReportMessage.securityId = order.getSecurityId();
    executionReportMessage.user = order.getUser();
    executionReportMessage.clOrdId = order.getClOrdId();
    executionReportMessage.symbol = pair.getSymbol();
    executionReportMessage.side = order.getSide();
    executionReportMessage.ordType = order.getOrdType();
    executionReportMessage.account = order.getAccount();
    executionReportMessage.submitterId = order.getSubmitterId();
    executionReportMessage.cancelId = 0;
    executionReportMessage.orderId = order.getOrderId();
    executionReportMessage.secondaryOrderId = order.getSecondaryOrderId();
    executionReportMessage.origOrderId = order.getOrigOrderId();

    executionReportMessage.orderQty = order.getQty();
    executionReportMessage.orderQtyScale = order.getQtyScale();
    executionReportMessage.leavesQty = order.getQty();
    executionReportMessage.leavesQtyScale = order.getQtyScale();
    executionReportMessage.price = order.getPrice();
    executionReportMessage.priceScale = order.getPriceScale();
    executionReportMessage.isPositionSideCrossed = false;
    executionReportMessage.timeInForce = order.getTimeInForce();
    executionReportMessage.expireTime = order.getExpireTime();
    executionReportMessage.timestamp = System.currentTimeMillis();
    executionReportMessage.targetStrategy = order.getTargetStrategy();
    executionReportMessage.isHidden = order.isHidden();
    executionReportMessage.isLiquidation = order.isLiquidation();
    executionReportMessage.isLastLook = order.isLastLook();
    executionReportMessage.cumQty = 0;
    executionReportMessage.cumQtyScale = 0;
    executionReportMessage.lastQty = 0;
    executionReportMessage.lastQtyScale = 0;
    executionReportMessage.price2 = order.getPrice2();
    executionReportMessage.price2Scale = order.getPrice2Scale();

    executionReportMessage.stopPx = order.getStopPx();
    executionReportMessage.stopPxScale = order.getStopPxScale();

    executionReportMessage.execType = ExecType.RESTATED;
    executionReportMessage.execRestatementReason = reason;
    executionReportMessage.openOrderCount = order.getUser().getOpenOrderCount();
    executionReportMessage.feeEstimatedQuantity = order.getFeeEstimatedQuantity();
    executionReportMessage.feeAccumulatedQuantity = order.getFeeAccumulatedQuantity();
    executionReportMessage.availableEstimatedQuantity = order.getAvailableEstimatedQuantity();
    executionReportMessage.availableAccumulatedQuantity = order.getAvailableAccumulatedQuantity();
    executionReportMessage.assetId = order.getAssetId();
    executionReportMessage.tokenId = order.getTokenId();
    executionReportMessage.groupAssetId = order.getGroupAssetId();
    executionReportMessage.selectId = order.getSelectId();

    executionReportMessage.quoteType = order.getQuoteType();
    executionReportMessage.quoteTargetUserId = order.getQuoteTargetUserId();

    if (order.getQuantityLong() > 0) {
      if (order.getQuantityLong() == order.getQuantityOrigLong())
        executionReportMessage.ordStatus = OrdStatus.NEW;
      else
        executionReportMessage.ordStatus = OrdStatus.PARTIALLY_FILLED;

      executionReportMessage.leavesQty = order.getQuantityLong();
      executionReportMessage.leavesQtyScale = pair.getQuantityScale();

      executionReportMessage.cumQty = order.getQuantityOrigLong() - order.getQuantityLong();
      executionReportMessage.cumQtyScale = pair.getQuantityScale();
    } else {
      executionReportMessage.ordStatus = OrdStatus.FILLED;
      executionReportMessage.leavesQty = 0;
      executionReportMessage.leavesQtyScale = 0;
      executionReportMessage.cumQty = order.getQty();
      executionReportMessage.cumQtyScale = order.getQtyScale();
    }

    executionReportMessage.setSenderCompId(order.getSenderCompId());
    if (null != causingMessage) {
      executionReportMessage.sourceSeqNum = causingMessage.getSourceSeqNum();
      executionReportMessage.sourceSendTime = causingMessage.getSourceSendTime();
      executionReportMessage.kafkaRecordOffset = causingMessage.getKafkaRecordOffset();

      executionReportMessage.inputTime = causingMessage.getInputTime();
      executionReportMessage.decodedTime = causingMessage.getDecodedTime();
    }
    executionReportMessage.matchTime = TimeUtil.getTime();


    // cumNotional, avgPx
    executionReportMessage.avgPx = executionReportMessage.cumQty == 0 ? 0 : (order.getFillCumNotional() / executionReportMessage.cumQty);
    executionReportMessage.avgPxScale = pair.getPriceScale();


    order.getUser().copySetPositionArr(executionReportMessage);

    if (LOGGER.isTraceEnabled() && executionReportMessage.expireTime == 0) {
      LOGGER.trace(LOG_FMT_2, "executionReportMessage expireTime==0, msg=", executionReportMessage);
    }
    if (LOGGER.isTraceEnabled() && executionReportMessage.stopPx != 0) {
      LOGGER.trace(LOG_FMT_2, "executionReportMessage stopPx != 0, msg=", executionReportMessage);
    }

    return executionReportMessage;
  }

  public static final ExecutionReportMessage createFromDecoder(final MessageHeaderDecoder headerDecoder,
      final ExecutionReportDecoder decoder) {
    final ExecutionReportMessage message = PersistExecutionReportObjectPool.get();

    message.setSecurityId(decoder.securityId());
    message.setAccount(decoder.userId());
    message.setSubmitterId(decoder.userId());
    message.setCancelId(decoder.cancelId());
    message.setClOrdId(decoder.clOrdID());
    message.setSymbol(decoder.symbol());
    message.setSide(decoder.side());
    message.setOrdType(decoder.ordType());
    message.setExecType(decoder.execType());
    message.setOrdStatus(decoder.ordStatus());
    message.setOrderId(decoder.orderId());
    message.setSecondaryOrderId(decoder.secondaryOrderId());
    message.setExecId(decoder.execId());
    message.setSecondaryExecId(decoder.secondaryExecId());
    message.setCounterpartyId(decoder.counterPartyId());
    message.setPositionSideCrossed(decoder.isPositionSideCrossed() == BooleanType.TRUE);
    message.setTargetStrategy(decoder.targetStrategy());
    message.setOrderQty(decoder.orderQty());
    message.setOrderQtyScale(decoder.orderQtyScale());
    message.setLeavesQty(decoder.leavesQty());
    message.setLeavesQtyScale(decoder.leavesQtyScale());
    message.setCumQty(decoder.cumQty());
    message.setCumQtyScale(decoder.cumQtyScale());
    message.setPrice(decoder.price());
    message.setPriceScale(decoder.priceScale());
    message.setAvgPx(decoder.avgPx());
    message.setAvgPxScale(decoder.avgPxScale());
    message.setLastPx(decoder.lastPx());
    message.setLastPxScale(decoder.lastPxScale());
    message.setLastQty(decoder.lastQty());
    message.setLastQtyScale(decoder.lastQtyScale());
    message.setStopPx(decoder.stopPx());
    message.setStopPxScale(decoder.stopPxScale());
    message.setTimeInForce(decoder.timeInForce());
    message.setExpireTime(decoder.expireTime());
    message.setTimestamp(decoder.transactTime());
    message.setAggressorSide(decoder.aggresorSide());
    message.setPrice2(decoder.price2());
    message.setPrice2Scale(decoder.price2Scale());
    message.setExecRestatementReason(decoder.execRestatementReason());

    message.setFeePositionId(decoder.feePositionId());
    message.setFeePositionQuantityChange(decoder.feePositionQuantityChange());
    message.setSettlePositionId(decoder.settlePositionId());
    message.setSettlePositionQuantity(decoder.settlePositionQuantityChange());
    message.setPaidToInsurance(BooleanType.TRUE == decoder.isPaidToInsurance());
    message.setHidden(BooleanType.TRUE == decoder.isHidden());
    message.setLiquidation(BooleanType.TRUE == decoder.isLiquidation());
    message.setSubmitterId(decoder.submitterId());

    message.setOpenOrderCount(decoder.openOrderCount());
    message.setBestBidPx(decoder.bestBidPx());
    message.setBestAskPx(decoder.bestAskPx());
    message.setBestPxScale(decoder.bestPxScale());
    message.setNotional(decoder.notional());
    message.setFeeEstimatedQuantity(decoder.feeEstimatedQuantity());
    message.setFeeAccumulatedQuantity(decoder.feeAccumulatedQuantity());
    message.setAvailableEstimatedQuantity(decoder.availableEstimatedQuantity());
    message.setAvailableAccumulatedQuantity(decoder.availableAccumulatedQuantity());
    message.setAssetId(decoder.assetId());
    message.setTokenId(decoder.tokenId());
    message.setGroupAssetId(decoder.groupAssetId());
    message.setSenderCompId(headerDecoder.senderCompId());
    message.setSequenceNumber(headerDecoder.msgSeqNum());
    message.setSourceSeqNum(headerDecoder.sourceSeqNum());
    message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
    message.setTransactionId(headerDecoder.transactionId());
    message.setLastMessageInTransaction(headerDecoder.transactionEnd() != 0);

    return message;
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public final MessageType getMessageType() {
    return MessageType.EXECUTION_REPORT;
  }

  public final String getClOrdId() {
    return clOrdId;
  }

  public final void setClOrdId(final String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public final String getSymbol() {
    return symbol;
  }

  public final void setSymbol(final String symbol) {
    this.symbol = symbol;
  }

  public final Side getSide() {
    return side;
  }

  public final void setSide(final Side side) {
    this.side = side;
  }

  public final OrdType getOrdType() {
    return ordType;
  }

  public final void setOrdType(final OrdType ordType) {
    this.ordType = ordType;
  }

  public final int getAccount() {
    return account;
  }

  public final void setAccount(final int account) {
    this.account = account;
  }

  public final long getOrderId() {
    return orderId;
  }

  public final void setOrderId(final long orderId) {
    this.orderId = orderId;
  }

  public final long getExecId() {
    return execId;
  }

  public final void setExecId(final long execId) {
    this.execId = execId;
  }

  public final long getSecondaryExecId() {
    return secondaryExecId;
  }

  public final void setSecondaryExecId(final long secondaryExecId) {
    this.secondaryExecId = secondaryExecId;
  }

  public final ExecType getExecType() {
    return execType;
  }

  public final void setExecType(final ExecType execType) {
    this.execType = execType;
  }

  public final OrdStatus getOrdStatus() {
    return ordStatus;
  }

  public final void setOrdStatus(final OrdStatus ordStatus) {
    this.ordStatus = ordStatus;
  }

  public final boolean isPositionSideCrossed() {
    return isPositionSideCrossed;
  }

  public final void setPositionSideCrossed(final boolean isPositionSideCrossed) {
    this.isPositionSideCrossed = isPositionSideCrossed;
  }

  public final TimeInForce getTimeInForce() {
    return timeInForce;
  }

  public final void setTimeInForce(final TimeInForce timeInForce) {
    this.timeInForce = timeInForce;
  }

  public final int getTargetStrategy() {
    return targetStrategy;
  }

  public final void setTargetStrategy(final int targetStrategy) {
    this.targetStrategy = targetStrategy;
  }

  public final boolean isHidden() {
    return isHidden;
  }

  public final void setHidden(final boolean isHidden) {
    this.isHidden = isHidden;
  }

  public final boolean isLiquidation() {
    return isLiquidation;
  }

  public final void setLiquidation(final boolean isLiquidation) {
    this.isLiquidation = isLiquidation;
  }

  public final boolean isLastLook() {
    return isLastLook;
  }

  public final void setLastLook(final boolean isLastLook) {
    this.isLastLook = isLastLook;
  }

  public final long getExpireTime() {
    return expireTime;
  }

  public final void setExpireTime(final long expireTime) {
    this.expireTime = expireTime;
  }

  public final long getTimestamp() {
    return timestamp;
  }

  public final void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  public final int getSecurityId() {
    return securityId;
  }

  public final void setSecurityId(final int securityId) {
    this.securityId = securityId;
  }

  public final ExecRestatementReason getExecRestatementReason() {
    return execRestatementReason;
  }

  public final void setExecRestatementReason(final ExecRestatementReason execRestatementReason) {
    this.execRestatementReason = execRestatementReason;
  }

  public final long getOrderQty() {
    return orderQty;
  }

  public final void setOrderQty(final long orderQty) {
    this.orderQty = orderQty;
  }

  public final short getOrderQtyScale() {
    return orderQtyScale;
  }

  public final void setOrderQtyScale(final short orderQtyScale) {
    this.orderQtyScale = orderQtyScale;
  }

  public final long getLeavesQty() {
    return leavesQty;
  }

  public final void setLeavesQty(final long leavesQty) {
    this.leavesQty = leavesQty;
  }

  public final short getLeavesQtyScale() {
    return leavesQtyScale;
  }

  public final void setLeavesQtyScale(final short leavesQtyScale) {
    this.leavesQtyScale = leavesQtyScale;
  }

  public final long getCumQty() {
    return cumQty;
  }

  public final void setCumQty(final long cumQty) {
    this.cumQty = cumQty;
  }

  public final short getCumQtyScale() {
    return cumQtyScale;
  }

  public final void setCumQtyScale(final short cumQtyScale) {
    this.cumQtyScale = cumQtyScale;
  }

  public final long getPrice() {
    return price;
  }

  public final void setPrice(final long price) {
    this.price = price;
  }

  public final short getPriceScale() {
    return priceScale;
  }

  public final void setPriceScale(final short priceScale) {
    this.priceScale = priceScale;
  }

  public final long getAvgPx() {
    return avgPx;
  }

  public final void setAvgPx(final long avgPx) {
    this.avgPx = avgPx;
  }

  public final short getAvgPxScale() {
    return avgPxScale;
  }

  public final void setAvgPxScale(final short avgPxScale) {
    this.avgPxScale = avgPxScale;
  }

  public final long getLastPx() {
    return lastPx;
  }

  public final void setLastPx(final long lastPx) {
    this.lastPx = lastPx;
  }

  public final short getLastPxScale() {
    return lastPxScale;
  }

  public final void setLastPxScale(final short lastPxScale) {
    this.lastPxScale = lastPxScale;
  }

  public final long getLastQty() {
    return lastQty;
  }

  public final void setLastQty(final long lastQty) {
    this.lastQty = lastQty;
  }

  public final short getLastQtyScale() {
    return lastQtyScale;
  }

  public final void setLastQtyScale(final short lastQtyScale) {
    this.lastQtyScale = lastQtyScale;
  }

  public final long getStopPx() {
    return stopPx;
  }

  public final void setStopPx(final long stopPx) {
    this.stopPx = stopPx;
  }

  public final short getStopPxScale() {
    return stopPxScale;
  }

  public final void setStopPxScale(final short stopPxScale) {
    this.stopPxScale = stopPxScale;
  }

  public final int getBasePositionId() {
    return basePositionId;
  }

  public final void setBasePositionId(final int basePositionId) {
    this.basePositionId = basePositionId;
  }

  public final long getBasePositionQuantity() {
    return basePositionQuantity;
  }

  public final void setBasePositionQuantity(final long basePositionQuantity) {
    this.basePositionQuantity = basePositionQuantity;
  }

  public final long getBasePositionQuantityChange() {
    return basePositionQuantityChange;
  }

  public final void setBasePositionQuantityChange(final long basePositionQuantityChange) {
    this.basePositionQuantityChange = basePositionQuantityChange;
  }

  public final int getQuotedPositionId() {
    return quotedPositionId;
  }

  public final void setQuotedPositionId(final int quotedPositionId) {
    this.quotedPositionId = quotedPositionId;
  }

  public final long getQuotedPositionQuantity() {
    return quotedPositionQuantity;
  }

  public final void setQuotedPositionQuantity(final long quotedPositionQuantity) {
    this.quotedPositionQuantity = quotedPositionQuantity;
  }

  public final long getQuotedPositionQuantityChange() {
    return quotedPositionQuantityChange;
  }

  public final void setQuotedPositionQuantityChange(final long quotedPositionQuantityChange) {
    this.quotedPositionQuantityChange = quotedPositionQuantityChange;
  }

  public final int getFeePositionId() {
    return feePositionId;
  }

  public final void setFeePositionId(final int feePositionId) {
    this.feePositionId = feePositionId;
  }

  public final long getFeePositionQuantity() {
    return feePositionQuantity;
  }

  public final void setFeePositionQuantity(final long feePositionQuantity) {
    this.feePositionQuantity = feePositionQuantity;
  }

  public final long getFeePositionQuantityChange() {
    return feePositionQuantityChange;
  }

  public final void setFeePositionQuantityChange(final long feePositionQuantityChange) {
    this.feePositionQuantityChange = feePositionQuantityChange;
  }

  public final int getSettlePositionId() {
    return settlePositionId;
  }

  public final void setSettlePositionId(final int settlePositionId) {
    this.settlePositionId = settlePositionId;
  }

  public final long getSettlePositionQuantity() {
    return settlePositionQuantity;
  }

  public final void setSettlePositionQuantity(final long settlePositionQuantity) {
    this.settlePositionQuantity = settlePositionQuantity;
  }

  public final long getSettlePositionQuantityChange() {
    return settlePositionQuantityChange;
  }

  public final void setSettlePositionQuantityChange(final long settlePositionQuantityChange) {
    this.settlePositionQuantityChange = settlePositionQuantityChange;
  }

  public final double getUnrealizedUsd() {
    return unrealizedUsd;
  }

  public final void setUnrealizedUsd(final double unrealizedUsd) {
    this.unrealizedUsd = unrealizedUsd;
  }

  public final double getRealizedUsd() {
    return realizedUsd;
  }

  public final void setRealizedUsd(final double realizedUsd) {
    this.realizedUsd = realizedUsd;
  }

  public final double getAvgCostBasisUsd() {
    return avgCostBasisUsd;
  }

  public final void setAvgCostBasisUsd(final double avgCostBasisUsd) {
    this.avgCostBasisUsd = avgCostBasisUsd;
  }

  public final double getQuotedUsdMark() {
    return quotedUsdMark;
  }

  public final void setQuotedUsdMark(final double quotedUsdMark) {
    this.quotedUsdMark = quotedUsdMark;
  }

  public final double getSettleCoinUsdMark() {
    return settleCoinUsdMark;
  }

  public final void setSettleCoinUsdMark(final double settleCoinUsdMark) {
    this.settleCoinUsdMark = settleCoinUsdMark;
  }

  public final double getSettleCoinUnrealized() {
    return settleCoinUnrealized;
  }

  public final void setSettleCoinUnrealized(double settleCoinUnrealized) {
    this.settleCoinUnrealized = settleCoinUnrealized;
  }

  public final double getSettleCoinRealized() {
    return settleCoinRealized;
  }

  public final void setSettleCoinRealized(double settleCoinRealized) {
    this.settleCoinRealized = settleCoinRealized;
  }

  public final BalanceAdminMessage getBalanceAdminMessage() {
    return balanceAdminMessage;
  }

  public final void buildBalanceAdminMessage() {
    // Not required
  }

  public final Side getAggressorSide() {
    return aggressorSide;
  }

  public final void setAggressorSide(final Side aggressorSide) {
    this.aggressorSide = aggressorSide;
  }

  public final long getSecondaryOrderId() {
    return secondaryOrderId;
  }

  public final void setSecondaryOrderId(final long secondaryOrderId) {
    this.secondaryOrderId = secondaryOrderId;
  }

  public final long getOrigOrderId() {
    return origOrderId;
  }

  public final void setOrigOrderId(final long origOrderId) {
    this.origOrderId = origOrderId;
  }

  public final int getCounterpartyId() {
    return counterpartyId;
  }

  public final void setCounterpartyId(final int counterpartyId) {
    this.counterpartyId = counterpartyId;
  }

  public final long getPrice2() {
    return price2;
  }

  public final void setPrice2(final long price2) {
    this.price2 = price2;
  }

  public final short getPrice2Scale() {
    return price2Scale;
  }

  public final void setPrice2Scale(final short price2Scale) {
    this.price2Scale = price2Scale;
  }

  public final boolean isPaidToInsurance() {
    return isPaidToInsurance;
  }

  public final void setPaidToInsurance(final boolean isPaidToInsurance) {
    this.isPaidToInsurance = isPaidToInsurance;
  }

  public final Position[] getPositionArr() {
    return positionArr;
  }

  public final int getPositionsLength() {
    return positionsLength;
  }

  public final Position[] reservePositionArrSize(final int length) {
    if (this.positionArr.length < length) {
      setPositionArr(Arrays.copyOf(this.positionArr, length));
    }

    return positionArr;
  }

  private final void setPositionArr(final Position[] positionArr) {
    this.positionArr = positionArr;
    this.positionsLength = positionArr.length;
  }


  public final void setPositionsLength(final int positionsLength) {
    this.positionsLength = positionsLength;
  }

  public final long getOpenOrderCount() {
    return openOrderCount;
  }

  public final void setOpenOrderCount(final long openOrderCount) {
    this.openOrderCount = openOrderCount;
  }

  public final long getBestBidPx() {
    return bestBidPx;
  }

  public final void setBestBidPx(final long bestBidPx) {
    this.bestBidPx = bestBidPx;
  }

  public final long getBestAskPx() {
    return bestAskPx;
  }

  public final void setBestAskPx(final long bestAskPx) {
    this.bestAskPx = bestAskPx;
  }

  public final short getBestPxScale() {
    return bestPxScale;
  }

  public final void setBestPxScale(final short bestPxScale) {
    this.bestPxScale = bestPxScale;
  }

  public final long getExpireTimeMillis() {
    return expireTimeMillis;
  }

  public final void setExpireTimeMillis(final long expireTimeMillis) {
    this.expireTimeMillis = expireTimeMillis;
  }

  public final double getNotional() {
    return notional;
  }

  public final void setNotional(final double notional) {
    this.notional = notional;
  }

  public final int getSubmitterId() {
    return submitterId;
  }

  public final void setSubmitterId(final int submitterId) {
    this.submitterId = submitterId;
  }

  public final long getCancelId() {
    return cancelId;
  }

  public final void setCancelId(final long cancelId) {
    this.cancelId = cancelId;
  }

  public final long getFeeEstimatedQuantity() {
    return feeEstimatedQuantity;
  }

  public final void setFeeEstimatedQuantity(final long feeEstimatedQuantity) {
    this.feeEstimatedQuantity = feeEstimatedQuantity;
  }

  public final long getFeeAccumulatedQuantity() {
    return feeAccumulatedQuantity;
  }

  public final void setFeeAccumulatedQuantity(final long feeAccumulatedQuantity) {
    this.feeAccumulatedQuantity = feeAccumulatedQuantity;
  }

  public final long getAvailableEstimatedQuantity() {
    return availableEstimatedQuantity;
  }

  public final void setAvailableEstimatedQuantity(final long availableEstimatedQuantity) {
    this.availableEstimatedQuantity = availableEstimatedQuantity;
  }

  public final long getAvailableAccumulatedQuantity() {
    return availableAccumulatedQuantity;
  }

  public final void setAvailableAccumulatedQuantity(final long availableAccumulatedQuantity) {
    this.availableAccumulatedQuantity = availableAccumulatedQuantity;
  }

  public final long getAssetId() {
    return assetId;
  }

  public final void setAssetId(final long assetId) {
    this.assetId = assetId;
  }

  public final int getTokenId() {
    return tokenId;
  }

  public final void setTokenId(final int tokenId) {
    this.tokenId = tokenId;
  }

  public final long getSelectId() {
    return selectId;
  }

  public final void setSelectId(final long selectId) {
    this.selectId = selectId;
  }

  public long getGroupAssetId() {
    return groupAssetId;
  }

  public void setGroupAssetId(final long groupAssetId) {
    this.groupAssetId = groupAssetId;
  }

  public final QuoteType getQuoteType() {
    return quoteType;
  }

  public final void setQuoteType(final QuoteType quoteType) {
    this.quoteType = quoteType;
  }

  public final int getQuoteTargetUserId() {
    return quoteTargetUserId;
  }

  public final void setQuoteTargetUserId(final int quoteTargetUserId) {
    this.quoteTargetUserId = quoteTargetUserId;
  }

  public final short getCancelType() {
    return cancelType;
  }

  public final void setCancelType(short cancelType) {
    this.cancelType = cancelType;
  }

  public AtomicInteger getConcurrentUsageCount() {
    return concurrentUsageCount;
  }

  @Override
  public final void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public final void onPersist() {
    if (!Context.isPersistMarketMakerOrders() && Context.getMarketMakerUserid() == this.account) {
      InstrumentPair pair = InstrumentCache.getPair(securityId);
      if (targetStrategy == EXTERNAL) {
        //persist
      } else if (pair != null && (USDC.equals(pair.getBase().getSymbol()) || USDT.equals(pair.getBase().getSymbol()))) {
        //persist
      } else {
        return;
      }
    }
    if (Context.isUserStatsEnabled()) {
      UserStats.onMessage(this);
    }

    Persister.onMessage(this);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    s.append(EXECUTIONREPORTMESSAGE_SECURITYID_EQ).append(securityId).append(CLORID_EQ)
        .append(clOrdId == null ? "" : String.valueOf(clOrdId)).append(SYMBOL_EQ).append(symbol).append(SIDE_EQ).append(side)
        .append(ORDTYPE_EQ).append(ordType).append(ACCOUNT_EQ).append(account).append(SUBMITTERID_EQ).append(submitterId).append(ORDERID_EQ)
        .append(orderId).append(ORIGORDERID_EQ).append(origOrderId).append(EXECID_EQ).append(execId).append(ORDERQTY_EQ).append(orderQty)
        .append(LEAVESQTY_EQ).append(leavesQty).append(CUMQTY_EQ).append(cumQty).append(PRICE_EQ).append(price).append(PRICE2_EQ)
        .append(price2).append(AVGPX_EQ).append(avgPx).append(AVGPXSCALE_EQ).append(avgPxScale).append(LASTPX_EQ).append(lastPx)
        .append(LASTQTY_EQ).append(lastQty).append(STOPPX_EQ).append(stopPx).append(TIMEINFORCE_EQ).append(timeInForce).append(EXECTYPE_EQ)
        .append(execType).append(EXECRESTATEMENTREASON_EQ).append(execRestatementReason).append(ORDSTATUS_EQ).append(ordStatus)
        .append(BASEPOSITIONID_EQ).append(basePositionId).append(BASEPOSITIONQUANTITY_EQ).append(basePositionQuantity)
        .append(BASEPOSITIONQUANTITYCHANGE_EQ).append(basePositionQuantityChange).append(QUOTEDPOSITIONID_EQ).append(quotedPositionId)
        .append(QUOTEDPOSITIONQUANTITY_EQ).append(quotedPositionQuantity).append(QUOTEDPOSITIONQUANTITYCHANGE_EQ)
        .append(quotedPositionQuantityChange).append(FEEPOSITIONID_EQ).append(feePositionId).append(FEEPOSITIONQUANTITY_EQ)
        .append(feePositionQuantity).append(FEEPOSITIONQUANTITYCHANGE_EQ).append(feePositionQuantityChange).append(UNREALIZEDUSD_EQ)
        .append(unrealizedUsd).append(REALIZEDUSD_EQ).append(realizedUsd).append(AVGCOSTBASISUSD_EQ).append(avgCostBasisUsd)
        .append(QUOTEDUSDMARK_EQ).append(quotedUsdMark).append(SETTLECOINUSDMARK_EQ).append(settleCoinUsdMark)
        .append(SETTLECOINUNREALIZED_EQ).append(settleCoinUnrealized).append(SETTLECOINREALIZED_EQ).append(settleCoinRealized)
        .append(SECONDARYORDERID_EQ).append(secondaryOrderId).append(TARGETSTRATEGY_EQ).append(targetStrategy).append(ISHIDDEN_EQ)
        .append(isHidden).append(ISLIQUIDATION_EQ).append(isLiquidation).append(ISLASTLOOK_EQ).append(isLastLook).append(", bestBidPx=")
        .append(bestBidPx).append(", bestAskPx=").append(bestAskPx).append(", bestPxScale=").append(bestPxScale).append(", notional=")
        .append(notional).append(INPUTTIME_EQ).append(inputTime).append(DECODEDTIME_EQ).append(decodedTime).append(MATCHTIME_EQ)
        .append(matchTime).append(KAFKA_OFFSET_EQ).append(kafkaRecordOffset).append(FEEESTIMATEDQUANTITY_EQ).append(feeEstimatedQuantity)
        .append(FEEACCUMULATEDQUANTITY_EQ).append(feeAccumulatedQuantity).append(AVAILABLEESTIMATEDQUANTITY_EQ)
        .append(availableEstimatedQuantity).append(AVAILABLEACCUMULATEDQUANTITY_EQ).append(availableAccumulatedQuantity)
        .append(", cancelType=").append(cancelType).append(ASSETID_EQ).append(assetId).append(TOKENID_EQ).append(tokenId)
        .append(GROUPASSETID_EQ).append(groupAssetId).append(SELECTID_EQ).append(selectId).append(QUOTETYPE_EQ).append(quoteType)
        .append(QUOTE_TARGET_USERID_EQ).append(quoteTargetUserId).append(']');
    return s;
  }

  @Override
  public final String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"ExecutionReportMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"securityId\":").append(securityId).append(",\"price\":").append(price).append(",\"price2\":").append(price2)
        .append(",\"side\":").append("\"").append(side).append("\"").append(",\"secondaryOrderId\":").append(secondaryOrderId)
        .append(",\"clOrdId\":").append("\"").append(clOrdId == null ? "" : String.valueOf(clOrdId)).append("\"").append("\"")
        .append(",\"account\":").append("\"").append(account).append("\"").append(",\"submitterId\":").append(submitterId)
        .append(",\"userId\":").append(user == null ? 0 : user.getId()).append(",\"ordType\":").append("\"").append(ordType).append("\"")
        .append(",\"symbol\":").append("\"").append(symbol).append("\"").append(",\"orderId\":").append(orderId)
        .append(",\"secondaryOrderId\":").append(secondaryOrderId).append(",\"execId\":").append(execId).append(",\"secondaryExecId\":")
        .append(secondaryExecId);
    sb.append(",\"timeInForce\":").append("\"").append(timeInForce).append("\"");
    sb.append(",\"expireTime\":").append(expireTime);
    sb.append(",\"timestamp\":").append("\"").append(timestamp).append("\"");
    sb.append(",\"targetStrategy\":").append(targetStrategy);
    sb.append(",\"execType\":").append("\"").append(execType).append("\"");
    sb.append(",\"execRestatementReason\":").append("\"").append(execRestatementReason).append("\"");
    sb.append(",\"ordStatus\":").append("\"").append(ordStatus).append("\"");
    sb.append(",\"counterpartyId\":").append(counterpartyId);
    sb.append(",\"orderQty\":").append(orderQty);
    sb.append(",\"orderQty_scale\":").append(orderQtyScale);
    sb.append(",\"leavesQty\":").append(leavesQty);
    sb.append(",\"leavesQty_scale\":").append(leavesQtyScale);
    sb.append(",\"cumQty\":").append(cumQty);
    sb.append(",\"cumQty_scale\":").append(cumQtyScale);
    sb.append(",\"price\":").append(price);
    sb.append(",\"price_scale\":").append(priceScale);
    sb.append(",\"avgPx\":").append(avgPx);
    sb.append(",\"avgPx_scale\":").append(avgPxScale);
    sb.append(",\"lastQty\":").append(lastQty);
    sb.append(",\"lastQty_scale\":").append(lastQtyScale);
    sb.append(",\"stopPx\":").append(stopPx);
    sb.append(",\"stopPx_scale\":").append(stopPxScale);
    sb.append(",\"aggressorSide\":").append("\"").append(aggressorSide).append("\"");
    sb.append(",\"basePositionId\":").append(basePositionId);
    sb.append(",\"basePositionQuantity\":").append(basePositionQuantity);
    sb.append(",\"basePositionQuantityChange\":").append(basePositionQuantityChange);
    sb.append(",\"quotedPositionId\":").append(quotedPositionId);
    sb.append(",\"quotedPositionQuantity\":").append(quotedPositionQuantity);
    sb.append(",\"quotedPositionQuantityChange\":").append(quotedPositionQuantityChange);
    sb.append(",\"feePositionId\":").append(feePositionId);
    sb.append(",\"feePositionQuantity\":").append(feePositionQuantity);
    sb.append(",\"feePositionQuantityChange\":").append(feePositionQuantityChange);
    sb.append(",\"settlePositionId\":").append(settlePositionId);
    sb.append(",\"settlePositionQuantity\":").append(settlePositionQuantity);
    sb.append(",\"settlePositionQuantityChange\":").append(settlePositionQuantityChange);
    sb.append(",\"unrealizedUsd\":").append(unrealizedUsd);
    sb.append(",\"realizedUsd\":").append(realizedUsd);
    sb.append(",\"avgCostBasisUsd\":").append(avgCostBasisUsd);
    sb.append(",\"quotedUsdMark\":").append(quotedUsdMark);
    sb.append(",\"settleCoinUsdMark\":").append(settleCoinUsdMark);
    sb.append(",\"basePositionId\":").append(basePositionId);
    sb.append(",\"settleCoinUnrealized\":").append(settleCoinUnrealized);
    sb.append(",\"settleCoinRealized\":").append(settleCoinRealized);
    sb.append(",\"isHidden\":").append(isHidden);
    sb.append(",\"isLiquidation\":").append(isLiquidation);
    sb.append(",\"isLastLook\":").append(isLastLook);
    sb.append(",\"feeEstimatedQuantity\":").append(feeEstimatedQuantity);
    sb.append(",\"feeAccumulatedQuantity\":").append(feeAccumulatedQuantity);
    sb.append(",\"availableEstimatedQuantity\":").append(availableEstimatedQuantity);
    sb.append(",\"availableAccumulatedQuantity\":").append(availableAccumulatedQuantity);
    sb.append(",\"assetId\":").append(assetId);
    sb.append(",\"tokenId\":").append(tokenId);
    sb.append(",\"groupAssetId\":").append(groupAssetId);
    sb.append(",\"selectId\":").append(selectId);
    sb.append("}");
    return sb.toString();
  }

}
