package com.solfini.matchengine.message.internal;

import java.io.PrintWriter;
import java.io.StringWriter;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.NewOrderSingleDecoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.QuoteType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;

/**
 * @author Chris Mack
 */
public class Order extends Message implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(Order.class);

  protected int securityId;
  private int submitterId;
  private int account;
  private long price;
  private short priceScale;
  private long qty;
  private short qtyScale;
  private Side side;
  private long orderId;
  private long orderPriority;
  private long secondaryOrderId; // orderId grouped by instrumentPair
  private String clOrdId;
  private OrdType ordType;
  private Order prev;
  private Order next;
  private int type;
  private int priceInt;
  private long quantityLong;
  private long quantityOrigLong;
  private short quantityOrigScale;
  private TimeInForce timeInForce = TimeInForce.GOOD_TILL_CANCEL;
  private long expireTime;
  private long stopPx;
  private short stopPxScale;
  private int stopPxInt;
  private boolean toClose;
  private long price2;
  private short price2Scale;
  private int price2Int;
  private int marginCheckReferencePrice;
  private long fillCumNotional;
  private long origOrderId;
  private int targetStrategy; // used for special order types such as Reduce-Only
  private boolean isHidden;
  private boolean isLiquidation;
  private boolean isLastLook;
  private long feeEstimatedQuantity;
  private long feeAccumulatedQuantity;
  private long availableEstimatedQuantity;
  private long availableAccumulatedQuantity;
  private int minMaxPrice; // used for trailing stops to track the min or max price for the order
  private long assetId;
  private int tokenId;
  private long groupAssetId;
  private long selectId;
  private QuoteType quoteType;
  private int quoteTargetUserId;

  //for inmemory use only
  private boolean orderModified;


  public Order() {
    // default constructor
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.NEW_ORDER;
  }

  public void set(final NewOrderSingleDecoder newOrderSingleDecoder, final long orderId, final long orderPriority) {
    this.clOrdId = newOrderSingleDecoder.clOrdID();
    this.securityId = newOrderSingleDecoder.securityId();
    this.orderId = orderId;
    this.orderPriority = orderPriority;
    this.submitterId = newOrderSingleDecoder.submitterId();
    targetStrategy = newOrderSingleDecoder.targetStrategy();
    isHidden = BooleanType.TRUE == newOrderSingleDecoder.isHidden();
    isLiquidation = BooleanType.TRUE == newOrderSingleDecoder.isLiquidation();
    isLastLook = BooleanType.TRUE == newOrderSingleDecoder.isLastLook();

    account = newOrderSingleDecoder.userId();
    price = newOrderSingleDecoder.price();
    priceScale = newOrderSingleDecoder.priceScale();
    qty = newOrderSingleDecoder.qty();
    qtyScale = newOrderSingleDecoder.qtyScale();
    side = newOrderSingleDecoder.side();

    ordType = newOrderSingleDecoder.ordType();
    toClose = false;

    price2 = newOrderSingleDecoder.price2();
    price2Scale = newOrderSingleDecoder.price2Scale();
    marginCheckReferencePrice = 0;
    timeInForce = newOrderSingleDecoder.timeInForce(); // default to TimeInForce.GOOD_TILL_CANCEL
    expireTime = newOrderSingleDecoder.expireTime();

    stopPx = newOrderSingleDecoder.stopPx();
    stopPxScale = newOrderSingleDecoder.stopPxScale();

    assetId = newOrderSingleDecoder.assetId();
    tokenId = newOrderSingleDecoder.tokenId();
    groupAssetId = newOrderSingleDecoder.groupAssetId();
    selectId = newOrderSingleDecoder.selectId();

    fillCumNotional = 0;
    feeEstimatedQuantity = 0;
    feeAccumulatedQuantity = 0;
    availableEstimatedQuantity = 0;
    availableAccumulatedQuantity = 0;

    quoteType = newOrderSingleDecoder.quoteType();
    quoteTargetUserId = newOrderSingleDecoder.quoteTargetUserId();
    if (quoteType != null && quoteType != QuoteType.NULL_VAL) {
      this.origOrderId = newOrderSingleDecoder.orderId();//for RFQ
      if (this.origOrderId > 0) this.orderId = origOrderId;
    }
  }

  // copy set order, used for stop limit orders
  public void set(final Order source, final long orderId, final long secondaryOrderId) {
    this.orderId = orderId;
    this.secondaryOrderId = secondaryOrderId;
    this.orderPriority = source.orderPriority;
    this.submitterId = source.getSubmitterId();
    securityId = source.securityId;
    clOrdId = source.clOrdId;
    price = source.price;
    priceScale = source.priceScale;
    priceInt = source.priceInt;
    qty = source.qty;
    qtyScale = source.qtyScale;
    quantityLong = source.quantityLong;
    quantityOrigLong = source.quantityOrigLong;
    quantityOrigScale = source.quantityOrigScale;
    side = source.side;
    ordType = source.ordType;
    senderCompId = source.senderCompId;
    toClose = source.toClose;

    price2 = source.price2;
    price2Scale = source.price2Scale;
    price2Int = source.price2Int;

    marginCheckReferencePrice = source.marginCheckReferencePrice;
    timeInForce = source.timeInForce;
    expireTime = source.expireTime;
    account = source.account;
    targetStrategy = source.targetStrategy;
    isHidden = source.isHidden;
    isLiquidation = source.isLiquidation;
    isLastLook = source.isLastLook;
    user = source.user;
    senderCompId = source.senderCompId;
    sourceSeqNum = source.sourceSeqNum;
    sourceSendTime = source.sourceSendTime;
    sequenceNumber = source.sequenceNumber;
    snapId = source.snapId;
    kafkaRecordOffset = source.kafkaRecordOffset;
    inputTime = source.inputTime;
    decodedTime = source.decodedTime;
    matchTime = source.matchTime;
    publishTime = source.publishTime;

    stopPx = source.stopPx;
    stopPxScale = source.stopPxScale;
    stopPxInt = source.stopPxInt;

    assetId = source.assetId;
    tokenId = source.tokenId;
    groupAssetId = source.groupAssetId;

    selectId = source.selectId;
    quoteType = source.quoteType;
    quoteTargetUserId = source.quoteTargetUserId;

    type = source.type;
    ordType = source.ordType;
    prev = null;
    next = null;

    origOrderId = source.orderId;
    fillCumNotional = 0;
    feeEstimatedQuantity = source.feeEstimatedQuantity;
    feeAccumulatedQuantity = source.feeAccumulatedQuantity;
    availableEstimatedQuantity = source.availableEstimatedQuantity;
    availableAccumulatedQuantity = source.availableAccumulatedQuantity;
    orderModified = source.orderModified;
  }

  public final int getSecurityId() {
    return securityId;
  }

  public final void setSecurityId(final int securityId) {
    this.securityId = securityId;
  }

  public final long getPrice() {
    return price;
  }

  public final short getPriceScale() {
    return priceScale;
  }

  public final void setPrice(final long price, final short price_scale) {
    this.price = price;
    this.priceScale = price_scale;
  }


  public final long getPrice2() {
    return price2;
  }

  public final short getPrice2Scale() {
    return price2Scale;
  }

  public final void setPrice2(final long price2, final short price2_scale) {
    this.price2 = price2;
    this.price2Scale = price2_scale;
  }

  public final long getQty() {
    return qty;
  }

  public final short getQtyScale() {
    return qtyScale;
  }

  public final void setQty(final long qty, final short qty_scale) {
    this.qty = qty;
    this.qtyScale = qty_scale;
  }

  public final Side getSide() {
    return side;
  }

  public final void setSide(final Side side) {
    this.side = side;
  }

  public final long getOrderId() {
    return orderId;
  }

  public final void setOrderId(final long orderId) {
    this.orderId = orderId;
  }

  public final long getOrigOrderId() {
    return origOrderId;
  }

  public final void setOrigOrderId(final long origOrderId) {
    this.origOrderId = origOrderId;
  }

  public final long getOrderPriority() {
    return orderPriority;
  }

  public final void setOrderPriority(final long orderPriority) {
    this.orderPriority = orderPriority;
  }

  public final String getClOrdId() {
    return clOrdId;
  }

  public final void setClOrdId(final String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public final int getAccount() {
    return account;
  }

  public final void setAccount(final int account) {
    this.account = account;
  }

  public final OrdType getOrdType() {
    return ordType;
  }

  public final void setOrdType(final OrdType ordType) {
    this.ordType = ordType;
  }

  public final Order getPrev() {
    return prev;
  }

  public final void setPrev(final Order prev) {
    this.prev = prev;
  }

  public final Order getNext() {
    return next;
  }

  public final void setNext(final Order next) {
    this.next = next;
  }

  public final int getType() {
    return type;
  }

  public final void setType(final int type) {
    this.type = type;
  }

  public final int getPriceInt() {
    visit();
    return priceInt;
  }

  public final void setPriceInt(final int priceInt) {
    visit();
    this.priceInt = priceInt;
  }

  public final int getPrice2Int() {
    return price2Int;
  }

  public final void setPrice2Int(final int price2Int) {
    this.price2Int = price2Int;
  }

  public final boolean isHidden() {
    return isHidden;
  }

  public final void setHidden(final boolean isHidden) {
    this.isHidden = isHidden;
  }

  public final int getSubmitterId() {
    return submitterId;
  }

  public final void setSubmitterId(final int submitterId) {
    this.submitterId = submitterId;
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

  // is reduce only order
  public boolean isReduceOnly() {
    return ((targetStrategy != 0)
        && (REDUCE_ONLY == targetStrategy || PROFIT_LIMIT_REDUCE_ONLY == targetStrategy || STOP_MARKET_REDUCE_ONLY == targetStrategy
            || TRAILING_STOP_LIMIT_REDUCE_ONLY == targetStrategy || TRAILING_STOP_MARKET_REDUCE_ONLY == targetStrategy));
  }

  // is a stop profit order, limit or market
  public boolean isStopTakeProfit() {
    return ((targetStrategy != 0) && (PROFIT_LIMIT == targetStrategy || PROFIT_LIMIT_REDUCE_ONLY == targetStrategy
        || PROFIT_MARKET == targetStrategy || PROFIT_MARKET_REDUCE_ONLY == targetStrategy));
  }

  // is a stop profit order, market only
  public boolean isStopTakeProfitMarket() {
    return ((targetStrategy != 0) && (PROFIT_MARKET == targetStrategy || PROFIT_MARKET_REDUCE_ONLY == targetStrategy));
  }

  // is Trailing Stop
  public boolean isTrailingStop() {
    return ((targetStrategy != 0) && (TRAILING_STOP_LIMIT == targetStrategy || TRAILING_STOP_LIMIT_REDUCE_ONLY == targetStrategy
        || TRAILING_STOP_MARKET == targetStrategy || TRAILING_STOP_MARKET_REDUCE_ONLY == targetStrategy));
  }

  // is Trailing Stop Market
  public boolean isTrailingStopMarket() {
    return ((targetStrategy != 0) && (TRAILING_STOP_MARKET == targetStrategy || TRAILING_STOP_MARKET_REDUCE_ONLY == targetStrategy));
  }

  // is Market
  public boolean isMarket() {
    return ((targetStrategy != 0) && (STOP_MARKET == targetStrategy || STOP_MARKET_REDUCE_ONLY == targetStrategy
        || PROFIT_MARKET == targetStrategy || PROFIT_MARKET_REDUCE_ONLY == targetStrategy || TRAILING_STOP_MARKET == targetStrategy
        || TRAILING_STOP_MARKET_REDUCE_ONLY == targetStrategy));
  }

  public boolean isTWAP() {
    return ((targetStrategy != 0) && (TWAP == targetStrategy || TWAP_REDUCE_ONLY == targetStrategy));
  }

  public boolean isADLMaker() {
    return ((targetStrategy != 0) && (ADL_MAKER_ONLY == targetStrategy));
  }

  public final boolean isLmm() {
    if (user == null)
      return false;

    return user.isLmm();
  }

  // get QuantityLong used in Matching
  public final long getMatchQuantityLong(final Order counterOrder) {
    if (isLastLook) {
      final User counterpartyUser = counterOrder.getUser();

      if (counterpartyUser == null || user == null) {
        quantityLong = 0;
        return quantityLong;
      }

      // if self-trade continue
      // else do more checks
      if (counterpartyUser.getId() != user.getId()) {
        final InstrumentPair pair = InstrumentCache.getPair(securityId);
        int feedMark = (int) (pair.getIndexFeedUsdMark() * pair.getPriceScaleMultiplier());
        if (Side.BUY == side && priceInt >= feedMark) {
          quantityLong = 0;
          return quantityLong;
        }
        if (Side.SELL == side && priceInt <= feedMark) {
          quantityLong = 0;
          return quantityLong;
        }
      }
    }

    return getQuantityLong();
  }

  public final long getQuantityLong() {
    visit();

    // for reduce only, we only allow the max orderQty = position qty
    if (isReduceOnly()) {
      try {
        // calc open orders required
        Position position = user.getPositionArr()[securityId];
        if (position == null)
          position = user.setPosition(securityId, 0, null);

        if (Side.BUY == side) {
          if (position.getQuantity() >= 0) {
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(LOG_FMT_6, REDUCE_ONLY_EQ, this, POSITION_EQ, position, ABS_POSITION_EQ, 0);
            }
            return 0;
          } else {
            final long absPosition = Math.abs(position.getQuantity());
            if (absPosition < quantityLong) {
              if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(LOG_FMT_6, REDUCE_ONLY_EQ, this, POSITION_EQ, position, ABS_POSITION_EQ, absPosition);
              }
              return absPosition;
            }
          }
        } else if (Side.SELL == side) {
          if (position.getQuantity() <= 0) {
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(LOG_FMT_6, REDUCE_ONLY_EQ, this, POSITION_EQ, position, ABS_POSITION_EQ, 0);
            }
            return 0;
          } else {
            final long absPosition = Math.abs(position.getQuantity());
            if (absPosition < quantityLong) {
              if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(LOG_FMT_6, REDUCE_ONLY_EQ, this, POSITION_EQ, position, ABS_POSITION_EQ, absPosition);
              }
              return absPosition;
            }
          }
        }

      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }

    return quantityLong;
  }

  public final void setQuantityLong(final long quantityLong) {
    visit();
    this.quantityLong = quantityLong;
  }

  public final long getQuantityOrigLong() {
    return quantityOrigLong;
  }

  public final void setQuantityOrigLong(final long quantityOrigLong) {
    this.quantityOrigLong = quantityOrigLong;
  }

  public TimeInForce getTimeInForce() {
    return timeInForce;
  }

  public void setTimeInForce(final TimeInForce timeInForce) {
    this.timeInForce = timeInForce;
  }

  public long getExpireTime() {
    return expireTime;
  }

  public void setExpireTime(final long expireTime) {
    this.expireTime = expireTime;
  }

  public final long getStopPx() {
    return stopPx;
  }

  public final short getStopPxScale() {
    return stopPxScale;
  }

  public final void setStopPx(final long stopPx, final short stopPx_scale) {
    this.stopPx = stopPx;
    this.stopPxScale = stopPx_scale;
  }

  public final int getStopPxInt() {
    return stopPxInt;
  }

  public final void setStopPxInt(final int stopPxInt) {
    this.stopPxInt = stopPxInt;
  }

  public final boolean isToClose() {
    return toClose;
  }

  public final void setToClose(final boolean toClose) {
    this.toClose = toClose;
  }

  public final int getTargetStrategy() {
    return targetStrategy;
  }

  public final void setTargetStrategy(final int targetStrategy) {
    this.targetStrategy = targetStrategy;
  }

  public final long getSecondaryOrderId() {
    return secondaryOrderId;
  }

  public final void setSecondaryOrderId(final long secondaryOrderId) {
    this.secondaryOrderId = secondaryOrderId;
  }

  public final int getMarginCheckReferencePrice() {
    return marginCheckReferencePrice;
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

  public long getGroupAssetId() {
    return groupAssetId;
  }

  public void setGroupAssetId(final long groupAssetId) {
    this.groupAssetId = groupAssetId;
  }

  public final long getSelectId() {
    return selectId;
  }

  public final void setSelectId(final long selectId) {
    this.selectId = selectId;
  }

  public final void setMarginCheckReferencePrice(final int marginCheckReferencePrice) {
    this.marginCheckReferencePrice = marginCheckReferencePrice;
  }

  public final int getMinMaxPrice() {
    return minMaxPrice;
  }

  public final int setMinTrailingPrice(final int minPrice) {
    if ((minPrice > 0 && minPrice < this.minMaxPrice) || minMaxPrice <= 0)
      this.minMaxPrice = minPrice;
    return this.minMaxPrice;
  }

  public final int setMaxTrailingPrice(final int maxPrice) {
    if (maxPrice > this.minMaxPrice)
      this.minMaxPrice = maxPrice;
    return this.minMaxPrice;
  }

  public final boolean isRFQ() {
    if (quoteType == null || quoteType == quoteType.NULL_VAL)
      return false;
    return true;
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

  public final void incrementFeeAccumulatedQuantity(final long feeQuantity) {
    this.feeAccumulatedQuantity += feeQuantity;
  }

  public final long getFeeUncollectedQuantity() {
    return (feeEstimatedQuantity > feeAccumulatedQuantity) ? feeEstimatedQuantity - feeAccumulatedQuantity : 0;
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

  public boolean isOrderModified() {
    return orderModified;
  }

  public void setOrderModified(final boolean orderModified) {
    this.orderModified = orderModified;
  }

  public final void incrementAvailableAccumulatedQuantity(final long quantity) {
    this.availableAccumulatedQuantity += quantity;
  }

  public final long getAvailableUncollectedQuantity() {
    return availableEstimatedQuantity - availableAccumulatedQuantity;
  }

  public final long getFillCumNotional() {
    return fillCumNotional;
  }

  public final void incrementFillNotional(final long fillNotional) {
    this.fillCumNotional += fillNotional;
  }

  public final double calcNotional() {
    InstrumentPair pair = InstrumentCache.getPair(securityId);
    return (priceInt * pair.getPriceScaleFactor()) * (quantityLong * pair.getQuantityScaleFactor());
  }

  // TODO: remove this check used for debugging
  public void visit() {
    if (markAsReturned) {
      try {
        throw new NullPointerException();
      } catch (Exception e) {
        LOGGER.error("visit marked AsReturned" + toString() + ", returnedStack=" + returnedStack, e);
      }
    }
  }

  private String returnedStack = "";

  @Override
  public void markAsReturned() {
    markAsReturned = true;

    try {
      throw new NullPointerException();
    } catch (Exception e) {
      final StringWriter sw = new StringWriter();
      final PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      returnedStack = sw.toString(); // stack trace as a string
    }
  }

  @Override
  public void onMatcher() {
    if (getUser() == null) {
      final Message reject = NewOrderSingleHandler.lookupUser(this);
      if (reject != null) {
        reject.onMatcher();
        return;
      }
    }
    //todo remove after testing
    if (account != Context.getMarketMakerUserid())
      LOGGER.info("Order received: " + this.toJSON());
    final InstrumentPair instrument = InstrumentCache.getPair(securityId);
    if (null != instrument) {
      final OrderBook orderbook = instrument.getOrderBook();
      orderbook.addOrder(this);
    }
  }

  // use expireTime aka triggerTimeMillis, for TWAP
  public final void setTriggerTimeMillis(final long triggerTimeMillis) {
    this.expireTime = triggerTimeMillis;
  }

  // use price2 aka interval, for TWAP
  public final void incrementTriggerTimeMillis() {
    this.expireTime = this.expireTime + this.price2;
  }

  // use price2Scale aka intervalCount, for TWAP
  public final int intervalCountDecrement() {
    price2Scale--;
    return price2Scale;
  }

  @Override
  public void clear() {
    super.clear();
    prev = null;
    next = null;
    marginCheckReferencePrice = 0;
    price2 = 0;
    price2Int = 0;
    stopPx = 0;
    stopPxInt = 0;
    price = 0;
    priceInt = 0;
    orderId = 0;
    quantityLong = 0;
    securityId = 0;
    type = 0;
    fillCumNotional = 0;
    origOrderId = 0;
    targetStrategy = 0;
    isHidden = false;
    isLiquidation = false;
    isLastLook = false;
    markAsReturned = false;
    feeEstimatedQuantity = 0;
    feeAccumulatedQuantity = 0;
    availableEstimatedQuantity = 0;
    availableAccumulatedQuantity = 0;
    minMaxPrice = 0;
    expireTime = 0;
    assetId = 0;
    tokenId = 0;
    selectId = 0;
    groupAssetId = 0;
    quoteType = null;
    quoteTargetUserId = 0;
  }

  @Override
  public boolean equals(final Object order) {
    if (this == order)
      return true;
    if (order == null || order.getClass() != this.getClass())
      return false;

    return orderId == ((Order) order).getOrderId();
  }

  @Override
  public int hashCode() {
    return (int) orderId;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append(ORDER_SECURITYID_EQ).append(securityId).append(ORDERID_EQ).append(orderId).append(ORIGORDERID_EQ).append(origOrderId)
        .append(PRICE_EQ).append(price).append(PRICE_SCALE_EQ).append(priceScale).append(PRICE2_EQ).append(price2).append(PRICE2_SCALE_EQ)
        .append(price2Scale).append(QTY_EQ).append(qty).append(QTY_SCALE_EQ).append(qtyScale).append(SIDE_EQ).append(side)
        .append(ORDERPRIORITY_EQ).append(orderPriority).append(SECONDARYORDERID_EQ).append(secondaryOrderId)
        .append(MARGINCHECKREFERENCEPRICE_EQ).append(marginCheckReferencePrice).append(CLORID_EQ)
        .append(clOrdId == null ? "" : String.valueOf(clOrdId)).append(SENDERCOMPIDCHARARR_EQ).append(senderCompId)
        .append(SENDERCOMPIDASSTRING_EQ).append(senderCompId).append(ACCOUNT_EQ).append(account).append(SUBMITTERID_EQ).append(submitterId)
        .append(ORDTYPE_EQ).append(ordType).append(TYPE_EQ).append(type).append(PRICEINT_EQ).append(priceInt).append(QUANTITYLONG_EQ)
        .append(quantityLong).append(QUANTITYORIGLONG_EQ).append(quantityOrigLong).append(QUANTITYORIGLONGSCALE_EQ)
        .append(quantityOrigScale).append(TIMEINFORCE_EQ).append(timeInForce).append(EXPIRETIME_EQ).append(expireTime).append(STOPPX_EQ)
        .append(stopPx).append(STOPPX_SCALE_EQ).append(stopPxScale).append(STOPPX_INT_EQ).append(stopPxInt).append(TO_CLOSE_EQ)
        .append(toClose).append(INPUTTIME_EQ).append(inputTime).append(DECODEDTIME_EQ).append(decodedTime).append(SOURCESEQNUM_EQ)
        .append(sourceSeqNum).append(SOURCESENDTIME_EQ).append(sourceSendTime).append(TARGETSTRATEGY_EQ).append(targetStrategy)
        .append(ISHIDDEN_EQ).append(isHidden).append(ISLIQUIDATION_EQ).append(isLiquidation).append(ISLASTLOOK_EQ).append(isLastLook)
        .append(FEEESTIMATEDQUANTITY_EQ).append(feeEstimatedQuantity).append(FEEACCUMULATEDQUANTITY_EQ).append(feeAccumulatedQuantity)
        .append(AVAILABLEESTIMATEDQUANTITY_EQ).append(availableEstimatedQuantity).append(AVAILABLEACCUMULATEDQUANTITY_EQ)
        .append(availableAccumulatedQuantity).append(ASSETID_EQ).append(assetId).append(TOKENID_EQ).append(tokenId).append(GROUPASSETID_EQ)
        .append(groupAssetId).append(SELECTID_EQ).append(selectId).append(QUOTETYPE_EQ).append(quoteType).append(QUOTE_TARGET_USERID_EQ)
        .append(quoteTargetUserId).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"Order\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":").append(persistTime)
        .append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime).append(",\"snapId\":")
        .append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"securityId\":").append(securityId).append(",\"price\":").append(price).append(",\"price_scale\":").append(priceScale)
        .append(",\"price2\":").append(price2).append(",\"price2_scale\":").append(price2Scale).append(",\"marginCheckReferencePrice\":")
        .append(marginCheckReferencePrice).append(",\"qty\":").append(qty).append(",\"qty_scale\":").append(qtyScale).append(",\"side\":")
        .append("\"").append(side).append("\"").append(",\"secondaryOrderId\":").append(secondaryOrderId).append(",\"orderId\":")
        .append(orderId).append(",\"origOrderId\":").append(origOrderId).append(",\"orderPriority\":").append(orderPriority)
        .append(",\"clOrdId\":").append("\"").append(clOrdId == null ? "" : clOrdId).append("\"").append(",\"senderCompIdCharArr\":")
        .append("\"").append(senderCompId == null ? "" : senderCompId).append("\"").append(",\"senderCompAsString\":").append("\"")
        .append(senderCompId).append("\"").append(",\"account\":").append("\"").append(account).append("\"").append(",\"submitterId\":")
        .append(submitterId).append(",\"ordType\":").append("\"").append(ordType).append("\"").append(",\"type\":").append(type)
        .append(",\"priceInt\":").append(priceInt).append(",\"quantityLong\":").append(quantityLong).append(",\"quantityOrigLong\":")
        .append(quantityOrigLong).append(",\"quantityOrig_scale\":").append(quantityOrigScale).append(",\"timeInForce\":").append("\"")
        .append(timeInForce).append("\"").append(",\"expireTime\":").append(expireTime).append(",\"stopPx\":").append(stopPx)
        .append(",\"stopPx_scale\":").append(stopPxScale).append(",\"stopPxInt\":").append(stopPxInt).append(",\"toClose\":")
        .append(toClose).append(",\"targetStrategy\":").append(targetStrategy).append(",\"isHidden\":").append(isHidden)
        .append(",\"isLiquidation\":").append(isLiquidation).append(",\"isLastLook\":").append(isLastLook)
        .append(",\"feeEstimatedQuantity\":").append(feeEstimatedQuantity).append(",\"feeAccumulatedQuantity\":")
        .append(feeAccumulatedQuantity).append(",\"availableEstimatedQuantity\":").append(availableEstimatedQuantity)
        .append(",\"availableAccumulatedQuantity\":").append(availableAccumulatedQuantity).append(",\"assetId\":").append(assetId)
        .append(",\"tokenId\":").append(tokenId).append(",\"groupAssetId\":").append(groupAssetId).append(",\"selectId\":")
        .append(selectId);
    if (quoteType != null) {
      sb.append(",\"quoteType\":").append(quoteType);
    }

    sb.append("}");
    return sb.toString();
  }
}
