package com.solfini.matchengine.drmode;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.drmode.message.DRExecutionReport;
import com.solfini.matchengine.drmode.message.DROrder;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.pool.CancelOrderObjectPool;
import com.solfini.pool.DRCancelOrderObjectPool;
import com.solfini.pool.DRExecutionReportObjectPool;
import com.solfini.pool.DROrderObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.ExecutionReportDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;

/**
 *
 * @author Chris Mack
 *
 */
public class ExecutionReportParser implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExecutionReportParser.class);

  private int securityId = 0;
  private String clOrdId = null;
  private Side side = null;
  private OrdType ordType = null;
  private int account = 0;
  private int submitterId = 0;
  private long cancelId = 0;
  private long orderId = 0;
  private long secondaryOrderId = 0;
  private long execId = 0;
  private long secondaryExecId = 0;

  private long orderQtyLong = 0;
  private short orderQtyScaleRaw = 0;
  private long leavesQty = 0;
  private short leavesQtyScale = 0;
  private long priceLong = 0;
  private short priceScaleRaw = 0;

  private long stopPx = 0;
  private short stopPxScale = 0;

  private long price2Long = 0;
  private short price2ScaleRaw = 0;

  private long fee = 0;
  private short feeScale = 0;
  private int feeInstrumentId = 0;
  private boolean isPaidToInsurance = false;
  private long feeEstimatedQuantity;
  private long feeAccumulatedQuantity;
  private long availableEstimatedQuantity;
  private long availableAccumulatedQuantity;
  private short cancelType;

  private TimeInForce timeInForce;
  private long expireTime = 0;
  private int targetStrategy = 0;
  private long assetId = 0;
  private int tokenId = 0;
  private long selectId = 0;
  private long groupAssetId = 0;

  private ExecType execType = null;
  private InstrumentPair instrumentPair = null;

  public ExecutionReportParser() {
    // default constructor
  }

  public final Message parse(final MessageHeaderDecoder headerDecoder, final ExecutionReportDecoder executionReportDecoder) {
    try {
      // clOrdId
      clOrdId = executionReportDecoder.clOrdID();
      // orderId
      orderId = executionReportDecoder.orderId();

      execType = executionReportDecoder.execType();
      if (execType == null)
        return null;

      // ordType
      ordType = executionReportDecoder.ordType();
      if (ordType == null)
        return null;

      // secondaryOrderId
      secondaryOrderId = executionReportDecoder.secondaryOrderId();

      // securityId, instrumentPair
      try {
        securityId = executionReportDecoder.securityId();
        instrumentPair = InstrumentCache.getPair(securityId);
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
        securityId = 0;
        instrumentPair = null;
      }

      // side
      side = executionReportDecoder.side();

      account = executionReportDecoder.userId();
      submitterId = executionReportDecoder.submitterId();
      cancelId = executionReportDecoder.cancelId();

      // price
      priceLong = executionReportDecoder.price();
      priceScaleRaw = executionReportDecoder.priceScale();

      // price2
      price2Long = executionReportDecoder.price2();
      price2ScaleRaw = executionReportDecoder.price2Scale();

      // qty
      orderQtyLong = executionReportDecoder.orderQty();
      orderQtyScaleRaw = executionReportDecoder.orderQtyScale();


      timeInForce = executionReportDecoder.timeInForce();
      expireTime = executionReportDecoder.expireTime();
      targetStrategy = executionReportDecoder.targetStrategy();
      feeEstimatedQuantity = executionReportDecoder.feeEstimatedQuantity();
      feeAccumulatedQuantity = executionReportDecoder.feeAccumulatedQuantity();
      availableEstimatedQuantity = executionReportDecoder.availableEstimatedQuantity();
      availableAccumulatedQuantity = executionReportDecoder.availableAccumulatedQuantity();
      cancelType = executionReportDecoder.cancelType();

      assetId = executionReportDecoder.assetId();
      tokenId = executionReportDecoder.tokenId();
      groupAssetId = executionReportDecoder.groupAssetId();
      selectId = executionReportDecoder.selectId();

      if (orderId == 0 | orderId < NewOrderSingleHandler.getOrderId()) {
        LOGGER.info("Suspicious ER: " + clOrdId + " orderId: " + orderId + " securityId: "
        + securityId + " side: " + side + " execType: " + execType + " account: " + account
        + " orderQtyLong: " + orderQtyLong + " priceLong: " + priceLong + " timeInForce: " + timeInForce
        );
      }

      // special case, set orderId seqNum if greater
      NewOrderSingleHandler.setOrderIdIfGreater(orderId);
      NewOrderSingleHandler.setSecondaryOrderIdIfGreater(securityId, secondaryOrderId);

      switch (execType) {
        case NEW:
        case RESTATED:
          return newOrder(executionReportDecoder);
        case PENDING_CANCEL:
          return null;
        case CANCELED:
        case EXPIRED:
          return cancelOrder(executionReportDecoder);
        case TRADE:
          return trade(executionReportDecoder);
        case CALCULATED:
          return calculatedTrade(executionReportDecoder);
        default:
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(LOG_FMT_2, ">>> unable to parse executionReport execType=", execType.toString());
          }
          break;
      }


    } catch (Exception e) {
      LOGGER.error(LOG_FMT_2, "Error in decode, executionReportDecoder=", executionReportDecoder.toString(), e);
      throw e;
    }

    return null;
  }


  public final Order newOrder(final ExecutionReportDecoder executionReportDecoder) {

    OrdStatus ordStatus = null;
    try {
      ordStatus = executionReportDecoder.ordStatus();
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      ordStatus = null;
    }
    if (ordStatus != null && ordStatus == OrdStatus.REJECTED)
      return null;

    stopPx = executionReportDecoder.stopPx();
    stopPxScale = executionReportDecoder.stopPxScale();

    final Order order = MarketStatus.DR_MODE == Context.getMarketStatus() ? DROrderObjectPool.get() : OrderObjectPool.get();
    order.setSecurityId(securityId);
    order.setPrice(priceLong, priceScaleRaw);
    order.setPrice2(price2Long, price2ScaleRaw);
    order.setQty(orderQtyLong, orderQtyScaleRaw);
    order.setSide(side);
    order.setOrderId(orderId);
    order.setSecondaryOrderId(secondaryOrderId);
    order.setClOrdId(clOrdId);
    order.setAccount(account);
    order.setSubmitterId(submitterId);
    order.setOrdType(ordType);
    order.setStopPx(stopPx, stopPxScale);
    order.setTimeInForce(timeInForce);
    order.setExpireTime(expireTime);
    order.setTargetStrategy(targetStrategy);
    order.setFeeEstimatedQuantity(feeEstimatedQuantity);
    order.setFeeAccumulatedQuantity(feeAccumulatedQuantity);
    order.setAvailableEstimatedQuantity(availableEstimatedQuantity);
    order.setAvailableAccumulatedQuantity(availableAccumulatedQuantity);
    order.setAssetId(assetId);
    order.setTokenId(tokenId);
    order.setGroupAssetId(groupAssetId);
    order.setSelectId(selectId);

    if (LOGGER.isDebugEnabled() && expireTime == 0) {
      LOGGER.debug(LOG_FMT_2, ">>> parse order with expireTime==null msg=", order);
    }
    if (LOGGER.isDebugEnabled() && (OrdType.STOP == ordType || OrdType.STOP_LIMIT == ordType || stopPx > 0)) {
      LOGGER.debug(LOG_FMT_2, ">>> parse order with stop=", order);
    }

    NewOrderSingleHandler.parseOrder(order);

    if (executionReportDecoder.execType() == ExecType.RESTATED) {
      long leavesQuantityLong = executionReportDecoder.leavesQty();
      final int leavesQuantityScale = executionReportDecoder.leavesQtyScale();
      if (instrumentPair.getQuantityScale() > leavesQuantityScale) {
        for (int i = 0; i < (instrumentPair.getQuantityScale() - leavesQuantityScale); i++)
          leavesQuantityLong = leavesQuantityLong * 10;
      } else if (instrumentPair.getQuantityScale() < leavesQuantityScale) {
        for (int i = 0; i < (leavesQuantityScale - instrumentPair.getQuantityScale()); i++)
          leavesQuantityLong = leavesQuantityLong / 10;
      }

      if (leavesQuantityLong > 0 && leavesQuantityLong <= order.getQuantityLong()) {
        order.setQuantityLong(leavesQuantityLong);
      }
    }

    if (LOGGER.isDebugEnabled() && (OrdType.STOP == ordType || OrdType.STOP_LIMIT == ordType || stopPx > 0)) {
      LOGGER.debug(LOG_FMT_2, "<<< parse order with stop=", order);
    }

    return order;
  }


  public final CancelOrder cancelOrder(final ExecutionReportDecoder executionReportDecoder) {

    stopPx = executionReportDecoder.stopPx();
    stopPxScale = executionReportDecoder.stopPxScale();

    final Order order = MarketStatus.DR_MODE == Context.getMarketStatus() ? DROrderObjectPool.get() : OrderObjectPool.get();
    order.setSecurityId(securityId);
    order.setPrice(priceLong, priceScaleRaw);
    order.setPrice2(price2Long, price2ScaleRaw);
    order.setQty(orderQtyLong, orderQtyScaleRaw);
    order.setSide(side);
    order.setOrderId(orderId);
    order.setSecondaryOrderId(secondaryOrderId);
    order.setClOrdId(clOrdId);
    order.setAccount(account);
    order.setSubmitterId(submitterId);
    order.setOrdType(ordType);
    order.setStopPx(stopPx, stopPxScale);
    order.setTimeInForce(timeInForce);
    order.setExpireTime(expireTime);
    order.setFeeEstimatedQuantity(feeEstimatedQuantity);
    order.setFeeAccumulatedQuantity(feeAccumulatedQuantity);
    order.setAvailableEstimatedQuantity(availableEstimatedQuantity);
    order.setAvailableAccumulatedQuantity(availableAccumulatedQuantity);
    order.setAssetId(assetId);
    order.setTokenId(tokenId);
    order.setGroupAssetId(groupAssetId);
    order.setSelectId(selectId);

    NewOrderSingleHandler.parseOrder(order);
    // final long cancelId = orderId; /// TODO: is this ok?
    final long cancelPriority = 0;

    final CancelOrder cancelOrder =
        MarketStatus.DR_MODE == Context.getMarketStatus() ? DRCancelOrderObjectPool.get() : CancelOrderObjectPool.get();
    cancelOrder.set(order, cancelId, cancelPriority);
    // cancelOrder.setSecondaryOrderId(secondaryOrderId);
    cancelOrder.setCancelType(cancelType);

    if (order instanceof DROrder)
      DROrderObjectPool.returnObject((DROrder) order);
    else
      OrderObjectPool.returnObject((Order) order);

    return cancelOrder;
  }

  public final DRExecutionReport trade(final ExecutionReportDecoder executionReportDecoder) {

    try {
      leavesQty = executionReportDecoder.leavesQty();
      leavesQtyScale = executionReportDecoder.leavesQtyScale();

      if (null != instrumentPair) {
        if (instrumentPair.getQuantityScale() > leavesQtyScale) {
          for (int i = 0; i < (instrumentPair.getQuantityScale() - leavesQtyScale); i++)
            leavesQty = leavesQty * 10;
        } else if (instrumentPair.getQuantityScale() < leavesQtyScale) {
          for (int i = 0; i < (leavesQtyScale - instrumentPair.getQuantityScale()); i++)
            leavesQty = leavesQty / 10;
        }
        leavesQtyScale = instrumentPair.getQuantityScale();
      }
    } catch (Exception e) {
      leavesQty = 0;
      leavesQtyScale = 0;
    }

    fee = executionReportDecoder.feePositionQuantityChange();
    feeScale = executionReportDecoder.feePositionQuantityChangeScale();
    feeInstrumentId = executionReportDecoder.feePositionId();
    isPaidToInsurance = (BooleanType.TRUE == executionReportDecoder.isPaidToInsurance());
    final Instrument instrument = InstrumentCache.get(feeInstrumentId);
    if (null != instrument) {
      if (instrument.getQuantityScale() > feeScale) {
        for (int i = 0; i < (instrument.getQuantityScale() - feeScale); i++)
          fee = fee * 10;
      } else if (instrument.getQuantityScale() < feeScale) {
        for (int i = 0; i < (feeScale - instrument.getQuantityScale()); i++)
          fee = fee / 10;
      }
      feeScale = instrument.getQuantityScale();
    }

    execId = executionReportDecoder.execId();
    secondaryExecId = executionReportDecoder.secondaryExecId();

    final DRExecutionReport executionReport = DRExecutionReportObjectPool.get();
    executionReport.setSecurityId(securityId);
    executionReport.setSide(side);
    executionReport.setOrderId(orderId);
    executionReport.setSecondaryOrderId(secondaryOrderId);
    executionReport.setQtyInOrderbook(leavesQty);

    executionReport.setClOrdId(clOrdId);
    executionReport.setAccount(account);
    executionReport.setSubmitterId(submitterId);
    executionReport.setOrdType(ordType);
    executionReport.setTimeInForce(timeInForce);
    executionReport.setExecId(execId);
    executionReport.setSecondaryExecId(secondaryExecId);
    executionReport.setExecType(execType);

    executionReport.setFeeInstrumentId(feeInstrumentId);
    executionReport.setFeeQty(fee, feeScale);
    executionReport.setPaidToInsurance(isPaidToInsurance);
    executionReport.setTargetStrategy(targetStrategy);
    executionReport.setFeeEstimatedQuantity(feeEstimatedQuantity);
    executionReport.setFeeAccumulatedQuantity(feeAccumulatedQuantity);
    executionReport.setAvailableEstimatedQuantity(availableEstimatedQuantity);
    executionReport.setAvailableAccumulatedQuantity(availableAccumulatedQuantity);
    executionReport.setAssetId(assetId);
    executionReport.setTokenId(tokenId);
    executionReport.setGroupAssetId(groupAssetId);
    executionReport.setSelectId(selectId);

    return executionReport;
  }

  public final DRExecutionReport calculatedTrade(final ExecutionReportDecoder executionReportDecoder) {

    try {
      leavesQty = executionReportDecoder.leavesQty();
      leavesQtyScale = executionReportDecoder.leavesQtyScale();

      if (null != instrumentPair) {
        if (instrumentPair.getQuantityScale() > leavesQtyScale) {
          for (int i = 0; i < (instrumentPair.getQuantityScale() - leavesQtyScale); i++)
            leavesQty = leavesQty * 10;
        } else if (instrumentPair.getQuantityScale() < leavesQtyScale) {
          for (int i = 0; i < (leavesQtyScale - instrumentPair.getQuantityScale()); i++)
            leavesQty = leavesQty / 10;
        }
        leavesQtyScale = instrumentPair.getQuantityScale();
      }
    } catch (Exception e) {
      leavesQty = 0;
      leavesQtyScale = 0;
    }

    fee = executionReportDecoder.feePositionQuantityChange();
    feeScale = executionReportDecoder.feePositionQuantityChangeScale();
    feeInstrumentId = executionReportDecoder.feePositionId();
    isPaidToInsurance = (BooleanType.TRUE == executionReportDecoder.isPaidToInsurance());
    final Instrument instrument = InstrumentCache.get(feeInstrumentId);
    if (null != instrument) {
      if (instrument.getQuantityScale() > feeScale) {
        for (int i = 0; i < (instrument.getQuantityScale() - feeScale); i++)
          fee = fee * 10;
      } else if (instrument.getQuantityScale() < feeScale) {
        for (int i = 0; i < (feeScale - instrument.getQuantityScale()); i++)
          fee = fee / 10;
      }
      feeScale = instrument.getQuantityScale();
    }

    execId = executionReportDecoder.execId();
    secondaryExecId = executionReportDecoder.secondaryExecId();

    final DRExecutionReport executionReport = DRExecutionReportObjectPool.get();
    executionReport.setSecurityId(securityId);
    executionReport.setSide(side);
    executionReport.setOrderId(orderId);
    executionReport.setSecondaryOrderId(secondaryOrderId);
    executionReport.setClOrdId(clOrdId);
    executionReport.setAccount(account);
    executionReport.setSubmitterId(submitterId);
    executionReport.setOrdType(ordType);
    executionReport.setTimeInForce(timeInForce);
    executionReport.setExecId(execId);
    executionReport.setSecondaryExecId(secondaryExecId);
    executionReport.setExecType(execType);

    executionReport.setFeeInstrumentId(feeInstrumentId);
    executionReport.setFeeQty(fee, feeScale);
    executionReport.setPaidToInsurance(isPaidToInsurance);
    executionReport.setQtyInOrderbook(leavesQty);
    executionReport.setTargetStrategy(targetStrategy);
    executionReport.setFeeEstimatedQuantity(feeEstimatedQuantity);
    executionReport.setFeeAccumulatedQuantity(feeAccumulatedQuantity);
    executionReport.setAvailableEstimatedQuantity(availableEstimatedQuantity);
    executionReport.setAvailableAccumulatedQuantity(availableAccumulatedQuantity);
    executionReport.setAssetId(assetId);
    executionReport.setTokenId(tokenId);
    executionReport.setGroupAssetId(groupAssetId);
    executionReport.setSelectId(selectId);

    return executionReport;
  }

}
