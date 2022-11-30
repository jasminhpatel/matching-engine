package com.solfini.matchengine.orderbook;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.DisabledManyToOneConcurrentArrayQueueCustom;
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
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.pool.DRCancelOrderObjectPool;
import com.solfini.pool.DRExecutionReportObjectPool;
import com.solfini.pool.DROrderObjectPool;
import com.solfini.pool.OrderMatchingThreadObjectPool;
import com.solfini.preordercheck.NoPreOrderCheck;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.sbe.encoder.BusinessRejectReason;
import com.solfini.sbe.encoder.ExecRestatementReason;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.FastArrayList;

/**
 *
 * @author Chris Mack
 *
 */
public final class LinkedListOrderBook extends GlobalOrderBook implements OrderBook, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LinkedListOrderBook.class);

  private static final String ZERO_STR = "0";
  private static final String DEFAULT_SENDER_COMP = "1000000009";
  private Order bidPtr;
  private Order askPtr;

  private final int id;
  private final int priceScale;
  private final int quantityScale;
  private final InstrumentPair instrumentPair;
  private PreOrderCheck preOrderCheck;
  private final PreOrderCheck preOrderCheck_orig;
  private final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue;

  private int orderCount;
  private static long filledCountGlobal; // this works because there is a single matcher thread
  private long filledCount;
  private int bidDepth;
  private int askDepth;
  private Instrument settleCoinUsdMarkInstrument = null;
  private MarketStatus marketStatus = MarketStatus.OPEN;
  private final int orderBookStrategy;
  private final int preOrderCheckStrategy;
  private int last;
  private int mark;
  private double usdMark;
  private final FastArrayList<Order> triggeredOrders;


  public LinkedListOrderBook(final InstrumentPair instrumentPair, final PreOrderCheck preOrderCheck, final int orderBookStrategy,
      final int preOrderCheckStrategy) {
    this.id = instrumentPair.getId();
    this.priceScale = instrumentPair.getPriceScale();
    this.quantityScale = instrumentPair.getQuantityScale();
    this.instrumentPair = instrumentPair;
    this.preOrderCheck = preOrderCheck;
    this.preOrderCheck_orig = preOrderCheck;
    this.matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
    this.preOrderCheckStrategy = preOrderCheckStrategy;
    this.orderBookStrategy = orderBookStrategy;
    triggeredOrders = new FastArrayList<>();
  }

  public final int getId() {
    return id;
  }

  public final int getQuanityScale() {
    return quantityScale;
  }

  public final int getPriceScale() {
    return priceScale;
  }

  public final int getOrderCount() {
    return orderCount;
  }

  public final long getFilledCount() {
    return filledCount;
  }


  @Override
  public final int getBid() {
    return bidPtr == null ? 0 : bidPtr.getPriceInt();
  }

  @Override
  public final int getAsk() {
    return askPtr == null ? 0 : askPtr.getPriceInt();
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

  @Override
  public final void addOrder(final Order order) {
    // validate
    if (order == null || (order.getPriceInt() <= 0 && !(order.getType() == BUY_MARKET || order.getType() == SELL_MARKET))) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_4, REJECT_ORDER_EQ, PRICE_IS_MISSING, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order == null ? "" : order.getSenderCompId(),
          MsgType.ORDER_SINGLE, Long.toString(order == null ? 0 : order.getOrderId()), BusinessRejectReason.PRICE_IS_MISSING,
          PRICE_IS_MISSING, order == null ? 0 : order.getOrderId(), order == null ? 0 : order.getSourceSeqNum(),
          order == null ? 0 : order.getSecondaryOrderId(), order == null ? 0 : order.getSecurityId(), order.getSubmitterId()));
      return;
    } else if (order.getQuantityLong() <= 0) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_4, REJECT_ORDER_EQ, QUANTITY_IS_MISSING, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.QUANTITY_IS_MISSING, QUANTITY_IS_MISSING, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
      return;
    } else if (MarketStatus.CLOSE == marketStatus || MarketStatus.PAUSE == marketStatus) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_4, REJECT_ORDER_EQ, MARKET_IS_PAUSED_OR_CLOSED, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.MARKET_IS_PAUSED_OR_CLOSED, MARKET_IS_PAUSED_OR_CLOSED,
          order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
      return;
    }
    if (order.getSecurityId() != id) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_4, "adding invalid security in order. id=", id, ORDER_EQ, order);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.INVALID_ORDER_SECURITY, INVALID_ORDER_SECURITY, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
      return;
    }


    orderCount++;


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
      case STOP_BUY_LIMIT:
        addStopBuyLimit(order);
        break;
      case STOP_SELL_LIMIT:
        addStopSellLimit(order);
        break;
      default:
    }
  }

  @Override
  public final void cancelOrder(final CancelOrder cancelOrder) {
    final long orderId = cancelOrder.getOrigOrderId();
    final int type = cancelOrder.getType();
    if (type == CANCEL_BUY || type == BUY_LIMIT || type == 0) {
      Order tmpPtr = bidPtr;
      while (tmpPtr != null) {
        if (tmpPtr.getOrderId() == orderId) {
          removeBuyOrder(tmpPtr);
          return;
        }
        tmpPtr = tmpPtr.getNext();
      }
    }
    if (type == CANCEL_SELL || type == SELL_LIMIT || type == 0) {
      Order tmpPtr = askPtr;
      while (tmpPtr != null) {
        if (tmpPtr.getOrderId() == orderId) {
          removeSellOrder(tmpPtr);
          return;
        }
        tmpPtr = tmpPtr.getNext();
      }
    }
  }

  @Override
  public void uncross() {
    // TODO Auto-generated method stub
  }

  private final void processTriggeredOrders() {
    if (triggeredOrders.isEmpty())
      return;

    final Object[] tempArr = triggeredOrders.toArray();
    triggeredOrders.clear();

    for (final Object temp : tempArr) {
      final Order order = (Order) temp;
      // must ack the elimination of original order
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(order, instrumentPair));

      if (order.getType() == STOP_SELL_LIMIT)
        order.setType(SELL_LIMIT);
      else if (order.getType() == STOP_BUY_LIMIT)
        order.setType(BUY_LIMIT);

      order.setPriceInt(order.getStopPxInt());
      order.setOrdType(OrdType.LIMIT);
      addOrder(order);
    }
  }

  public final void matchOnAsks(final Order newPtr) {
    // match
    Order tmpPtr = askPtr;
    while (tmpPtr != null) {
      if (newPtr.getPriceInt() >= tmpPtr.getPriceInt()) {
        if (newPtr.getQuantityLong() < tmpPtr.getQuantityLong()) {
          long quantityFilled = newPtr.getQuantityLong();
          tmpPtr.setQuantityLong(tmpPtr.getQuantityLong() - quantityFilled);
          newPtr.setQuantityLong(newPtr.getQuantityLong() - quantityFilled);
          filled(quantityFilled, tmpPtr, newPtr, BUY_LIMIT);
          return;
        } else {
          long quantityFilled = tmpPtr.getQuantityLong();
          tmpPtr.setQuantityLong(tmpPtr.getQuantityLong() - quantityFilled);
          newPtr.setQuantityLong(newPtr.getQuantityLong() - quantityFilled);
          filled(quantityFilled, tmpPtr, newPtr, BUY_LIMIT);
          removeSellOrder(tmpPtr);

          if (newPtr.getQuantityLong() == 0)
            return;
        }
      } else
        break;
      tmpPtr = tmpPtr.getNext();
    }
  }

  public final void matchOnBids(final Order newPtr) {
    // match
    Order tmpPtr = bidPtr;
    while (tmpPtr != null) {
      if (newPtr.getPriceInt() <= tmpPtr.getPriceInt()) {
        if (newPtr.getQuantityLong() < tmpPtr.getQuantityLong()) {
          long quantityFilled = newPtr.getQuantityLong();
          tmpPtr.setQuantityLong(tmpPtr.getQuantityLong() - quantityFilled);
          newPtr.setQuantityLong(newPtr.getQuantityLong() - quantityFilled);
          filled(quantityFilled, tmpPtr, newPtr, SELL_LIMIT);
          return;
        } else {
          long quantityFilled = tmpPtr.getQuantityLong();
          tmpPtr.setQuantityLong(tmpPtr.getQuantityLong() - quantityFilled);
          newPtr.setQuantityLong(newPtr.getQuantityLong() - quantityFilled);
          filled(quantityFilled, tmpPtr, newPtr, SELL_LIMIT);
          removeBuyOrder(tmpPtr);

          if (newPtr.getQuantityLong() == 0)
            return;
        }
      } else
        break;
      tmpPtr = tmpPtr.getNext();
    }
  }

  public final void addBuyLimit(final Order newPtr) {

    if (!preOrderCheck.checkOrder(newPtr, newPtr.getPriceInt())) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_4, REJECT_ORDER_EQ, FAILED_PRE_CREDIT_CHECK, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId()));
      return;
    }

    // ack
    final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
    matcherToPublisherQueue.addGuaranteed(executionReportMessage);

    if (TimeInForce.FILL_OR_KILL == newPtr.getTimeInForce() && !isAsksFillOrKill(newPtr)) {
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      return;
    }

    if (TimeInForce.POST_ONLY == newPtr.getTimeInForce() && getAsk() != 0 && getAsk() <= newPtr.getPriceInt()) {
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      return;
    }

    if (MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) {
      matchOnAsks(newPtr);
      processTriggeredOrders();
    }

    if (newPtr.getQuantityLong() <= 0)
      return;
    if (TimeInForce.IMMEDIATE_OR_CANCEL == newPtr.getTimeInForce()) {
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      return;
    }



    // add
    if (bidPtr == null) {
      bidPtr = newPtr;
      return;
    }
    Order tmpPtr = bidPtr;
    while (true) {
      if (newPtr.getPriceInt() <= tmpPtr.getPriceInt()) {
        Order next = tmpPtr.getNext();
        if (next == null) {
          newPtr.setPrev(tmpPtr);
          tmpPtr.setNext(newPtr);
          return;
        } else {
          tmpPtr = next;
        }
      } else {
        Order prev = tmpPtr.getPrev();
        newPtr.setNext(tmpPtr);
        newPtr.setPrev(prev);
        if (prev != null) {
          prev.setNext(newPtr);
          tmpPtr.setPrev(newPtr);
          return;
        } else {
          tmpPtr.setPrev(newPtr);
          bidPtr = newPtr;
          return;
        }
      }
    }
  }

  public final void addSellLimit(final Order newPtr) {
    if (!preOrderCheck.checkOrder(newPtr, Math.max(newPtr.getPriceInt(), getBid()))) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_4, REJECT_ORDER_EQ, FAILED_PRE_CREDIT_CHECK, ORDER_EQ, newPtr);
      }
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId()));
      return;
    }

    // ack
    final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
    matcherToPublisherQueue.addGuaranteed(executionReportMessage);

    if (TimeInForce.FILL_OR_KILL == newPtr.getTimeInForce() && !isBidsFillOrKill(newPtr)) {
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      return;
    }

    if (TimeInForce.POST_ONLY == newPtr.getTimeInForce() && getBid() != 0 && getBid() >= newPtr.getPriceInt()) {
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      return;
    }

    if (MarketStatus.PREOPEN != marketStatus && MarketStatus.DR_MODE != marketStatus) {
      matchOnBids(newPtr);
      processTriggeredOrders();
    }

    if (newPtr.getQuantityLong() <= 0)
      return;
    if (TimeInForce.IMMEDIATE_OR_CANCEL == newPtr.getTimeInForce()) {
      matcherToPublisherQueue.addGuaranteed(ExecutionReportMessage.createOrderEliminationExecutionReport(newPtr, instrumentPair));
      return;
    }



    // add
    if (askPtr == null) {
      askPtr = newPtr;
      return;
    }
    Order tmpPtr = askPtr;
    while (true) {
      if (newPtr.getPriceInt() >= tmpPtr.getPriceInt()) {
        Order next = tmpPtr.getNext();
        if (next == null) {
          newPtr.setPrev(tmpPtr);
          tmpPtr.setNext(newPtr);
          return;
        } else {
          tmpPtr = next;
        }
      } else {
        Order prev = tmpPtr.getPrev();
        newPtr.setNext(tmpPtr);
        newPtr.setPrev(prev);
        if (prev != null) {
          prev.setNext(newPtr);
          tmpPtr.setPrev(newPtr);
          return;
        } else {
          tmpPtr.setPrev(newPtr);
          askPtr = newPtr;
          return;
        }
      }
    }
  }

  public final void addBuyMarket(final Order newPtr) {
    matchOnAsks(newPtr);
  }

  public final void addSellMarket(final Order newPtr) {
    matchOnBids(newPtr);
  }

  public final void removeBuyOrder(final Order tmpPtr) {
    Order prev = tmpPtr.getPrev();
    Order next = tmpPtr.getNext();
    if (prev == null) {
      if (next == null) {
        bidPtr = null;
      } else {
        next.setPrev(null);
        bidPtr = next;
      }
    } else if (next == null) {
      tmpPtr.getPrev().setNext(null);
    } else {
      next.setPrev(prev);
      prev.setNext(next);
    }
  }

  public final void removeSellOrder(final Order tmpPtr) {
    Order prev = tmpPtr.getPrev();
    Order next = tmpPtr.getNext();
    if (prev == null) {
      if (next == null) {
        askPtr = null;
      } else {
        next.setPrev(null);
        askPtr = next;
      }
    } else if (next == null) {
      tmpPtr.getPrev().setNext(null);
    } else {
      next.setPrev(prev);
      prev.setNext(next);
    }
  }



  public final void filled(final long quantityFilled, final Order makerOrder, final Order takerOrder, final int orderType) {
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

    preOrderCheck.updateFill(takerOrder, makerOrder.getPriceInt(), quantityFilled, execTaker, quotedUsdMark, settleCoinUsdMark,
        quotedCoinUsdMark, false, takerOrder, makerUserId);
    preOrderCheck.updateFill(makerOrder, makerOrder.getPriceInt(), quantityFilled, execMaker, quotedUsdMark, settleCoinUsdMark,
        quotedCoinUsdMark, true, takerOrder, takerUserId); // publish second so that any liquidation fees are transfered from taker first

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
        quotedCoinUsdMark, true, takerOrder, takerUserId); // publish second so that any liquidation fees are transfered from taker first

    last = makerOrder.getPriceInt();
    setMark(makerOrder.getPriceInt());
  }



  public final void addStopBuyLimit(final Order newPtr) {

    if (!preOrderCheck.checkOrder(newPtr, newPtr.getStopPxInt())) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId()));
      return;
    }

    // ack
    final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
    matcherToPublisherQueue.addGuaranteed(executionReportMessage);

    if (newPtr.getQuantityLong() <= 0)
      return;

    bidDepth++;

    // add
    if (bidPtr == null) {
      bidPtr = newPtr;
      return;
    }
    Order tmpPtr = bidPtr;
    while (true) {
      if (newPtr.getPriceInt() <= tmpPtr.getPriceInt()) {
        Order next = tmpPtr.getNext();
        if (next == null) {
          newPtr.setPrev(tmpPtr);
          tmpPtr.setNext(newPtr);
          return;
        } else {
          tmpPtr = next;
        }
      } else {
        Order prev = tmpPtr.getPrev();
        newPtr.setNext(tmpPtr);
        newPtr.setPrev(prev);
        if (prev != null) {
          prev.setNext(newPtr);
          tmpPtr.setPrev(newPtr);
          return;
        } else {
          tmpPtr.setPrev(newPtr);
          bidPtr = newPtr;
          return;
        }
      }
    }
  }

  public final void addStopSellLimit(final Order newPtr) {

    if (!preOrderCheck.checkOrder(newPtr, newPtr.getStopPxInt())) {
      // ack
      if (Context.isAckRejectMessages()) {
        final ExecutionReportMessage executionReportMessage =
            ExecutionReportMessage.createAckNewOrderRejectExecutionReport(newPtr, instrumentPair);
        matcherToPublisherQueue.addGuaranteed(executionReportMessage);
      }

      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(newPtr.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(newPtr.getOrderId()), BusinessRejectReason.FAILED_PRE_CREDIT_CHECK, FAILED_PRE_CREDIT_CHECK, newPtr.getOrderId(),
          newPtr.getSourceSeqNum(), newPtr.getSecondaryOrderId(), newPtr.getSecurityId(), newPtr.getSubmitterId()));
      return;
    }

    // ack
    final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createAckNewOrderExecutionReport(newPtr, instrumentPair);
    matcherToPublisherQueue.addGuaranteed(executionReportMessage);

    if (newPtr.getQuantityLong() <= 0)
      return;


    askDepth++;

    // add
    if (askPtr == null) {
      askPtr = newPtr;
      return;
    }
    Order tmpPtr = askPtr;
    while (true) {
      if (newPtr.getPriceInt() >= tmpPtr.getPriceInt()) {
        Order next = tmpPtr.getNext();
        if (next == null) {
          newPtr.setPrev(tmpPtr);
          tmpPtr.setNext(newPtr);
          return;
        } else {
          tmpPtr = next;
        }
      } else {
        Order prev = tmpPtr.getPrev();
        newPtr.setNext(tmpPtr);
        newPtr.setPrev(prev);
        if (prev != null) {
          prev.setNext(newPtr);
          tmpPtr.setPrev(newPtr);
          return;
        } else {
          tmpPtr.setPrev(newPtr);
          askPtr = newPtr;
          return;
        }
      }
    }
  }

  public final boolean isAsksFillOrKill(final Order newPtr) {
    long quantity = 0;

    // traverse depth
    Order tmpPtr = askPtr;
    if (tmpPtr == null || tmpPtr.getPriceInt() > newPtr.getPriceInt())
      return false;

    while (tmpPtr != null) {
      if (newPtr.getPriceInt() <= tmpPtr.getPriceInt()) {
        quantity += tmpPtr.getQuantityLong();
        if (quantity >= newPtr.getQuantityLong())
          return true;

      } else
        break;
      tmpPtr = tmpPtr.getNext();
    }

    return false;
  }

  public final boolean isBidsFillOrKill(final Order newPtr) {
    long quantity = 0;

    // traverse depth
    Order tmpPtr = bidPtr;
    if (tmpPtr == null || tmpPtr.getPriceInt() < newPtr.getPriceInt())
      return false;

    while (tmpPtr != null) {
      if (newPtr.getPriceInt() <= tmpPtr.getPriceInt()) {
        quantity += tmpPtr.getQuantityLong();
        if (quantity >= newPtr.getQuantityLong())
          return true;

      } else
        break;
      tmpPtr = tmpPtr.getNext();
    }

    return false;
  }

  public void print() {
    Order temp = bidPtr;
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("Bids");
    while (temp != null) {
      if (LOGGER.isDebugEnabled())
        LOGGER.debug(LOG_FMT_2, ORDER_EQ, temp);
      temp = temp.getNext();
    }
    temp = askPtr;
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("Asks");
    while (temp != null) {
      if (LOGGER.isDebugEnabled())
        LOGGER.debug(LOG_FMT_2, ORDER_EQ, temp);
      temp = temp.getNext();
    }
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
      case PAUSE:
        this.marketStatus = marketStatus;
        expireLiveSessionOrders();
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
        this.preOrderCheck = preOrderCheck_orig;
        uncross();
        restate(snapId, causingMessage);
        break;
      default:
        break;
    }
  }

  @Override
  public void expireLiveSessionOrders() {
    // TODO
  }

  @Override
  public void expireAllOrders() {
    // TODO
    throw new UnsupportedOperationException();
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

    // restate
    final ArrayList<Order> bids = new ArrayList<>();
    Order tmp = bidPtr;
    while (tmp != null) {
      bids.add(tmp);
      tmp = tmp.getNext();
    }
    for (int i = bids.size() - 1; i >= 0; i--) {
      matcherToPublisherQueue
          .add(ExecutionReportMessage.createRestateExecutionReport(bids.get(i), instrumentPair, reason, snapId, causingMessage));
    }
    tmp = askPtr;
    while (tmp != null) {
      matcherToPublisherQueue.add(ExecutionReportMessage.createRestateExecutionReport(tmp, instrumentPair, reason, snapId, causingMessage));
      tmp = tmp.getNext();
    }
  }


  @Override
  public void cancelReplaceOrder(final CancelReplaceOrder cancelReplaceOrder) {
    // TODO Auto-generated method stub

  }

  @Override
  public void massCancelOrder(final MassCancelOrder massCancelOrder) {
    // TODO Auto-generated method stub

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
  public final int getOrderBookStrategy() {
    return orderBookStrategy;
  }

  @Override
  public final int getPreOrderCheckStrategy() {
    return preOrderCheckStrategy;
  }

  public final void setSettleCoinUsdMarkInstrument(final Instrument settleCoinUsdMarkInstrument) {
    this.settleCoinUsdMarkInstrument = settleCoinUsdMarkInstrument;
  }



  /****
   * DR code below
   */

  // use hashmap to track open orders in DR
  private Map<Long, DROrder> drOrderBookMap = new HashMap<>();

  @Override
  public void addOrderDR(final DROrder order) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, "addOrderDR id=", order.getOrderId(), ORDER_EQ, order);
    }
    drOrderBookMap.put(order.getOrderId(), order);
  }

  @Override
  public void cancelOrderDR(final DRCancelOrder cancelOrder) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, "cancelOrderDR id=", cancelOrder.getOrigOrderId(), ", cancelOrder=", cancelOrder);
    }

    final DROrder order = drOrderBookMap.remove(cancelOrder.getOrigOrderId());
    DROrderObjectPool.returnObject(order);
    DRCancelOrderObjectPool.returnObject(cancelOrder);
  }

  private final void updateOrderFromExecReport(final DRExecutionReport executionReport) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_4, "updateOrderFromExecReport id=", executionReport.getOrderId(), ", executionReport=", executionReport);
    }

    if (OrdStatus.FILLED == executionReport.getOrdStatus() || OrdStatus.CANCELED == executionReport.getOrdStatus()) {
      final DROrder order = drOrderBookMap.remove(executionReport.getOrderId());
      DROrderObjectPool.returnObject(order);
    } else {
      final DROrder order = drOrderBookMap.get(executionReport.getOrderId());
      if (order == null) {
        LOGGER.error(LOG_FMT_2, "error updateOrderFromExecReport order not found: ", executionReport.getOrderId());
      } else {
        final long qty = executionReport.getQtyInOrderbook();
        order.setQuantityLong(qty);
      }
    }

    // special case because primary doesn't publish balanceAdmin for feeUser
    // we add the fee to the exchange user here
    if (executionReport.getFeeInstrumentId() > 0 && !executionReport.isPaidToInsurance() && executionReport.getFeeQty() != 0
        && UserCache.getExchangeUser() != null) {
      if (LOGGER.isInfoEnabled())
        LOGGER.info(LOG_FMT_4, "updateOrderFromExecReport, feeId=", (long) executionReport.getFeeInstrumentId(), VALUE_EQ,
            (-executionReport.getFeeQty()));
      UserCache.getExchangeUser().addPosition(executionReport.getFeeInstrumentId(), -executionReport.getFeeQty(),
          executionReport.getAssetId(), executionReport.getTokenId(), executionReport.getGroupAssetId());
    }

    DRExecutionReportObjectPool.returnObject(executionReport);
  }

  @Override
  public void execReportDR(final DRExecutionReport executionReport) {
    final OrdStatus ordStatus = executionReport.getOrdStatus();
    if (ordStatus != null && ordStatus == OrdStatus.REJECTED)
      return;

    if (ExecType.TRADE == executionReport.getExecType() || ExecType.CALCULATED == executionReport.getExecType()) {
      updateOrderFromExecReport(executionReport);
    }
  }

  public final void clearOrderBook() {
    bidPtr = null;
    askPtr = null;
  }

  private final void populateOrderBookFromDRMap() {
    clearOrderBook();

    final List<Order> list = new ArrayList<>(drOrderBookMap.values());
    Collections.sort(list, orderComparator);

    for (final Order order : list) {
      addOrder(order);
    }
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


  @Override
  public void copyTo(OrderBook target, Transform transform) {
    // TODO Auto-generated method stub

  }

  @Override
  public void reclaim() {
    // TODO Auto-generated method stub

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
      LOGGER.info(ERROR_LOG, e);
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
  public final void setFilledCountIfGreater(final long newValue) {
    // TODO Auto-generated method stub
  }

  @Override
  public MarketStatus getMarketStatus() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public PreOrderCheck getPreOrderCheck() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Instrument getSettleCoinUsdMarkInstrument() {
    // TODO Auto-generated method stub
    return null;
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
  public String toString(int priceLevel) {
    // TODO Auto-generated method stub
    return null;
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
