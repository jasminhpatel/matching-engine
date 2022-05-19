package com.solfini.matchengine.orderbook;

import java.io.Serializable;

import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.matchengine.drmode.message.DRCancelOrder;
import com.solfini.matchengine.drmode.message.DRExecutionReport;
import com.solfini.matchengine.drmode.message.DROrder;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.LiquidationOrder;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.preordercheck.PreOrderCheck;


/**
 *
 * @author Chris Mack
 *
 */
public interface OrderBook extends Serializable {

  public void addOrder(final Order order);

  public void cancelOrder(final CancelOrder cancelOrder);

  public void cancelReplaceOrder(final CancelReplaceOrder cancelReplaceOrder);

  public void massCancelOrder(final MassCancelOrder massCancelOrder);

  public void onLiquidationOrder(final LiquidationOrder liquidationOrder);

  public void uncross();

  public void changeState(final MarketStatus marketStatus, final long snapId, final Message causingMessage);

  public void expireLiveSessionOrders();

  public void expireAllOrders();

  public void restate(final long snapId, final Message causingMessage);

  public int getLast();

  public int getBid();

  public int getAsk();

  public int getMark();

  public void setMark(final int mark);

  public double getUsdMark();

  public int getOrderBookStrategy();

  public int getPreOrderCheckStrategy();

  public InstrumentPair getInstrumentPair();

  public MarketDataSnapshotFullRefreshEncoder build(final MarketDataSnapshotFullRefreshEncoder marketDataSnapshotFullRefreshEncoder);

  public interface Transform {
    public Order transform(final Order order);
  }

  public void copyTo(final OrderBook target, final Transform transform);

  public void reclaim();

  public void addOrderDR(final DROrder order);

  public void cancelOrderDR(final DRCancelOrder cancelOrder);

  public void execReportDR(final DRExecutionReport executionReport);

  // switch output queue to a disabled queue so nothing is published
  public void disableOutputQueue();

  // restore output to the actual publishing queue
  public void restoreOutputQueue();

  public OrderBookValidator getOrderBookValidator();

  public StopLimitContainer getStopLimitContainer();

  public StopProfitContainer getStopProfitContainer();

  public void setFilledCount(final long newValue);

  public void setFilledCountIfGreater(final long newValue);

  public long getFilledCount();

  public void setSecondaryOrderIdIfGreater(final long newValue);

  public long getSecondaryOrderId();

  public MarketStatus getMarketStatus();

  public void setSettleCoinUsdMarkInstrument(final Instrument settleCoinUsdMarkInstrument);

  public PreOrderCheck getPreOrderCheck();

  public int getId();

  public int getQuanityScale();

  public int getPriceScale();

  public Instrument getSettleCoinUsdMarkInstrument();

  public String toString(final int priceLevel);

  public void clearOrderBook();

  public int getOrderCount();

  default public int getArrSize() {
    return 0;
  }

  public TrailingStopContainer getTrailingStopContainer();

  public void expireSettlePosition(final int markInSettleCoin);

  public Order onCollateralSwapOrder(final User user, final long price, final short price_scale, final long qty, final short qty_scale,
      final Side side);

  public void updateSecurityDefinition(final InstrumentPair instrumentPair);

  public Order buildAlgoOrder(final Order source);

  public Order onUnderlyerPhysicalSettle(final User user, final long price, final short price_scale, final long qty, final short qty_scale,
      final Side side);
}
