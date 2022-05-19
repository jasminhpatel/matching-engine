package com.solfini.matchengine.orderbook;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.matchengine.drmode.message.DRCancelOrder;
import com.solfini.matchengine.drmode.message.DRExecutionReport;
import com.solfini.matchengine.drmode.message.DROrder;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.LiquidationOrder;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.preordercheck.NoPreOrderCheck;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;

/**
 *
 * @author Chris Mack
 *
 */
public class TreeOrderBook2 extends GlobalOrderBook implements OrderBook, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(TreeOrderBook2.class);

  public static final int DEFAULT_ARR_SIZE = Context.getInitialOrderBookSize();

  private final int id;
  private final int priceScale;
  private final int quanityScale;
  private final int orderBookStrategy;
  private final int preOrderCheckStrategy;
  private final InstrumentPair instrumentPair;
  private final StopLimitContainer stopLimitContainer;
  private final StopProfitContainer stopProfitContainer;

  private PreOrderCheck preOrderCheck;
  private MarketStatus marketStatus;
  private int orderCount;
  private long filledCount;
  private int last;
  private int mark;
  private double usdMark;
  private Instrument settleCoinUsdMarkInstrument;
  private long secondaryOrderId = 0;

  public TreeOrderBook2(final InstrumentPair pair, final PreOrderCheck preOrderCheck, final int orderBookStrategy,
      final int preOrderCheckStrategy) {
    this(pair, preOrderCheck, DEFAULT_ARR_SIZE, 0, orderBookStrategy, preOrderCheckStrategy);
  }

  public TreeOrderBook2(final InstrumentPair pair, final PreOrderCheck preOrderCheck, final int arrSize, final int cacheDepth,
      final int orderBookStrategy, final int preOrderCheckStrategy) {
    this.id = pair.getId();
    this.priceScale = pair.getPriceScale();
    this.quanityScale = pair.getQuantityScale();
    this.instrumentPair = pair;
    this.preOrderCheck = preOrderCheck;
    if (MarketStatus.DR_MODE == Context.getMarketStatus()) {
      this.preOrderCheck = new NoPreOrderCheck();
    }
    this.stopLimitContainer = new StopLimitContainer(id);
    this.stopProfitContainer = new StopProfitContainer(id);
    this.settleCoinUsdMarkInstrument = InstrumentCache.getBySymbol("USDC");
    this.usdMark = pair.getIndexFeedUsdMark();
    this.orderBookStrategy = orderBookStrategy;
    this.preOrderCheckStrategy = preOrderCheckStrategy;

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_18, "init orderbook: id=", id, PRICESCALE_EQ, (long) priceScale, QUANTITYSCALE_EQ, quanityScale,
          ORDERBOOKSTRATEGY_EQ, orderBookStrategy, PREORDERCHECKSTRATEGY_EQ, preOrderCheckStrategy);
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


  @Override
  public final StopLimitContainer getStopLimitContainer() {
    return stopLimitContainer;
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
  public final int getLast() {
    return last;
  }

  @Override
  public final int getMark() {
    return mark;
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
    if (this.usdMark == 0) {
      // calc a mid
      double usdMarkTemp = 0;
      final int bid = getBid();
      final int ask = getAsk();
      if (bid > 0) {
        usdMarkTemp += bid;
      }
      if (ask > 0) {
        usdMarkTemp += ask;
      }
      for (int i = 0; i < instrumentPair.getPriceScale(); i++)
        usdMarkTemp = usdMarkTemp * 0.1;
      return usdMarkTemp;
    }
    return this.usdMark;
  }

  public final PreOrderCheck getPreOrderCheck() {
    return preOrderCheck;
  }



  @Override
  public String toString(int priceLevel) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void addOrder(Order order) {
    // TODO Auto-generated method stub

  }

  @Override
  public void cancelOrder(CancelOrder cancelOrder) {
    // TODO Auto-generated method stub

  }

  @Override
  public void cancelReplaceOrder(CancelReplaceOrder cancelReplaceOrder) {
    // TODO Auto-generated method stub

  }

  @Override
  public void massCancelOrder(MassCancelOrder massCancelOrder) {
    // TODO Auto-generated method stub

  }

  @Override
  public void onLiquidationOrder(LiquidationOrder liquidationOrder) {
    // TODO Auto-generated method stub

  }

  @Override
  public void uncross() {
    // TODO Auto-generated method stub

  }

  @Override
  public void changeState(MarketStatus marketStatus, long snapId, Message causingMessage) {
    // TODO Auto-generated method stub

  }

  @Override
  public void expireLiveSessionOrders() {
    // TODO Auto-generated method stub

  }

  @Override
  public void expireAllOrders() {
    // TODO
    throw new UnsupportedOperationException();
  }

  @Override
  public void restate(long snapId, Message causingMessage) {
    // TODO Auto-generated method stub

  }

  @Override
  public int getBid() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getAsk() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder build(
      com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder marketDataSnapshotFullRefreshEncoder) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void copyTo(OrderBook target, Transform transform) {
    // TODO Auto-generated method stub

  }

  @Override
  public void reclaim() {
    // TODO Auto-generated method stub

  }

  @Override
  public void addOrderDR(DROrder order) {
    // TODO Auto-generated method stub

  }

  @Override
  public void cancelOrderDR(DRCancelOrder cancelOrder) {
    // TODO Auto-generated method stub

  }

  @Override
  public void execReportDR(DRExecutionReport executionReport) {
    // TODO Auto-generated method stub

  }

  @Override
  public void disableOutputQueue() {
    // TODO Auto-generated method stub

  }

  @Override
  public void restoreOutputQueue() {
    // TODO Auto-generated method stub

  }

  @Override
  public OrderBookValidator getOrderBookValidator() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void clearOrderBook() {
    // TODO Auto-generated method stub

  }

  @Override
  public StopProfitContainer getStopProfitContainer() {
    return stopProfitContainer;
  }

  @Override
  public TrailingStopContainer getTrailingStopContainer() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void expireSettlePosition(int markInSettleCoin) {
    // TODO Auto-generated method stub

  }

  @Override
  public Order onCollateralSwapOrder(User user, long price, short price_scale, long qty, short qty_scale, final Side side) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void updateSecurityDefinition(InstrumentPair instrumentPair) {
    // TODO Auto-generated method stub

  }

  @Override
  public Order buildAlgoOrder(Order source) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Order onUnderlyerPhysicalSettle(User user, long price, short price_scale, long qty, short qty_scale, Side side) {
    // TODO Auto-generated method stub
    return null;
  }
}
