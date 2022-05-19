package com.solfini.matchengine.orderbook;

import com.solfini.common.Context;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
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
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;

/**
 *
 * @author Chris Mack
 *
 */
public class TreeOrderBook extends GlobalOrderBook implements OrderBook {

  private static final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();

  public TreeOrderBook(final String symbol, final PreOrderCheck preOrderCheck, final int orderBookStrategy,
      final int preOrderCheckStrategy) {
    // TODO Implement
  }

  @Override
  public void addOrder(Order order) {
    // Check for matches
    ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(order, null);
    addPublishableMessage(executionReportMessage);
  }

  @Override
  public void cancelOrder(final CancelOrder cancelOrder) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException();
  }

  @Override
  public void uncross() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException();
  }

  private void addPublishableMessage(Message message) {
    matcherToPublisherQueue.addGuaranteed(message);
    throw new UnsupportedOperationException();
  }

  @Override
  public void changeState(final MarketStatus marketStatus, final long snapId, final Message causingMessage) {
    // TODO
    throw new UnsupportedOperationException();
  }

  @Override
  public void expireLiveSessionOrders() {
    // TODO
    throw new UnsupportedOperationException();
  }

  @Override
  public void expireAllOrders() {
    // TODO
    throw new UnsupportedOperationException();
  }

  @Override
  public void restate(final long snapId, final Message causingMessage) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException();
  }

  @Override
  public void cancelReplaceOrder(final CancelReplaceOrder cancelReplaceOrder) {
    // TODO Auto-generated method stub

  }

  @Override
  public void massCancelOrder(MassCancelOrder massCancelOrder) {
    // TODO Auto-generated method stub

  }

  @Override
  public int getLast() {
    // TODO Auto-generated method stub
    return 0;
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
  public int getMark() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public void setMark(int mark) {
    // TODO Auto-generated method stub

  }

  @Override
  public double getUsdMark() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public MarketDataSnapshotFullRefreshEncoder build(MarketDataSnapshotFullRefreshEncoder marketDataSnapshotFullRefreshEncoder) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void onLiquidationOrder(LiquidationOrder liquidationOrder) {
    // TODO Auto-generated method stub

  }

  @Override
  public int getOrderBookStrategy() {
    // TODO Auto-generated method stub
    return 0;
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
  public int getPreOrderCheckStrategy() {
    // TODO Auto-generated method stub
    return 0;
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
  public void disableOutputQueue() {
    // TODO Auto-generated method stub

  }

  @Override
  public void restoreOutputQueue() {
    // TODO Auto-generated method stub

  }

  @Override
  public OrderBookValidator getOrderBookValidator() {
    return null;
  }

  @Override
  public StopLimitContainer getStopLimitContainer() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public InstrumentPair getInstrumentPair() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setFilledCount(long newValue) {
    // TODO Auto-generated method stub
  }

  @Override
  public void setFilledCountIfGreater(long newValue) {
    // TODO Auto-generated method stub
  }

  @Override
  public long getFilledCount() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public MarketStatus getMarketStatus() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setSettleCoinUsdMarkInstrument(Instrument settleCoinUsdMarkInstrument) {
    // TODO Auto-generated method stub

  }

  @Override
  public PreOrderCheck getPreOrderCheck() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int getId() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getQuanityScale() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getPriceScale() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public Instrument getSettleCoinUsdMarkInstrument() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String toString(int priceLevel) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void clearOrderBook() {
    // TODO Auto-generated method stub

  }

  @Override
  public int getOrderCount() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public void setSecondaryOrderIdIfGreater(final long newValue) {
    // TODO Auto-generated method stub
  }

  @Override
  public long getSecondaryOrderId() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public StopProfitContainer getStopProfitContainer() {
    // TODO Auto-generated method stub
    return null;
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
