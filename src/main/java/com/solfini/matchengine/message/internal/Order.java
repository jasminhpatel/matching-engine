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
import com.solfini.internal.admin.schema.TokenType;
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
  private String platform;
  private String accountId;
  private String symbol; // for copy trades
  private long filterId;
  private String ocoClOrdId;
  private Order ocoOrder;

  // for inmemory use only
  private boolean orderModified;
  private boolean executed;
  private boolean rejected;

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
    this.symbol = newOrderSingleDecoder.symbol();
    this.orderId = orderId;
    this.orderPriority = orderPriority;
    this.submitterId = newOrderSingleDecoder.submitterId();
    this.targetStrategy = newOrderSingleDecoder.targetStrategy();
    this.isHidden = BooleanType.TRUE == newOrderSingleDecoder.isHidden();
    this.isLiquidation = BooleanType.TRUE == newOrderSingleDecoder.isLiquidation();
    this.isLastLook = BooleanType.TRUE == newOrderSingleDecoder.isLastLook();

    this.account = newOrderSingleDecoder.userId();
    this.price = newOrderSingleDecoder.price();
    this.priceScale = newOrderSingleDecoder.priceScale();
    this.qty = newOrderSingleDecoder.qty();
    this.qtyScale = newOrderSingleDecoder.qtyScale();
    this.side = newOrderSingleDecoder.side();

    this.ordType = newOrderSingleDecoder.ordType();
    // this.toClose = false;
    this.toClose = BooleanType.TRUE == newOrderSingleDecoder.isToClose();

    this.price2 = newOrderSingleDecoder.price2();
    this.price2Scale = newOrderSingleDecoder.price2Scale();
    this.marginCheckReferencePrice = 0;
    this.timeInForce = newOrderSingleDecoder.timeInForce(); // default to TimeInForce.GOOD_TILL_CANCEL
    this.expireTime = newOrderSingleDecoder.expireTime();

    this.stopPx = newOrderSingleDecoder.stopPx();
    this.stopPxScale = newOrderSingleDecoder.stopPxScale();

    this.assetId = newOrderSingleDecoder.assetId();
    this.tokenId = newOrderSingleDecoder.tokenId();
    this.groupAssetId = newOrderSingleDecoder.groupAssetId();
    this.selectId = newOrderSingleDecoder.selectId();

    this.fillCumNotional = 0;
    this.feeEstimatedQuantity = 0;
    this.feeAccumulatedQuantity = 0;
    this.availableEstimatedQuantity = 0;
    this.availableAccumulatedQuantity = 0;

    this.quoteType = newOrderSingleDecoder.quoteType();
    this.quoteTargetUserId = newOrderSingleDecoder.quoteTargetUserId();
    if (this.quoteType != null && this.quoteType != QuoteType.NULL_VAL) {
      this.origOrderId = newOrderSingleDecoder.orderId();// for RFQ
      if (this.origOrderId > 0)
        this.orderId = this.origOrderId;
    }
    this.platform = newOrderSingleDecoder.platform();
    this.accountId = newOrderSingleDecoder.accountId();
    // reuse secondaryOrderId field to get filterId from API. secondaryOrderId is empty until the engine assigns a value for it.
    this.filterId = newOrderSingleDecoder.secondaryOrderId();
    //todo update SBE version and deloy the below change
    //this.ocoClOrdId = newOrderSingleDecoder.ocoClOrdID();
  }

  // copy set order, used for stop limit orders
  public void set(final Order source, final long orderId, final long secondaryOrderId) {
    this.orderId = orderId;
    this.secondaryOrderId = secondaryOrderId;
    this.orderPriority = source.orderPriority;
    this.submitterId = source.getSubmitterId();
    this.securityId = source.securityId;
    this.symbol = source.symbol;
    this.clOrdId = source.clOrdId;
    this.price = source.price;
    this.priceScale = source.priceScale;
    this.priceInt = source.priceInt;
    this.qty = source.qty;
    this.qtyScale = source.qtyScale;
    this.quantityLong = source.quantityLong;
    this.quantityOrigLong = source.quantityOrigLong;
    this.quantityOrigScale = source.quantityOrigScale;
    this.side = source.side;
    this.ordType = source.ordType;
    this.senderCompId = source.senderCompId;
    this.toClose = source.toClose;

    this.price2 = source.price2;
    this.price2Scale = source.price2Scale;
    this.price2Int = source.price2Int;

    this.marginCheckReferencePrice = source.marginCheckReferencePrice;
    this.timeInForce = source.timeInForce;
    this.expireTime = source.expireTime;
    this.account = source.account;
    this.targetStrategy = source.targetStrategy;
    this.isHidden = source.isHidden;
    this.isLiquidation = source.isLiquidation;
    this.isLastLook = source.isLastLook;
    this.user = source.user;
    this.senderCompId = source.senderCompId;
    this.sourceSeqNum = source.sourceSeqNum;
    this.sourceSendTime = source.sourceSendTime;
    this.sequenceNumber = source.sequenceNumber;
    this.snapId = source.snapId;
    this.kafkaRecordOffset = source.kafkaRecordOffset;
    this.inputTime = source.inputTime;
    this.decodedTime = source.decodedTime;
    this.matchTime = source.matchTime;
    this.publishTime = source.publishTime;

    this.stopPx = source.stopPx;
    this.stopPxScale = source.stopPxScale;
    this.stopPxInt = source.stopPxInt;

    this.assetId = source.assetId;
    this.tokenId = source.tokenId;
    this.groupAssetId = source.groupAssetId;

    this.selectId = source.selectId;
    this.quoteType = source.quoteType;
    this.quoteTargetUserId = source.quoteTargetUserId;

    this.type = source.type;
    this.prev = null;
    this.next = null;

    this.origOrderId = source.orderId;
    this.fillCumNotional = 0;
    this.feeEstimatedQuantity = source.feeEstimatedQuantity;
    this.feeAccumulatedQuantity = source.feeAccumulatedQuantity;
    this.availableEstimatedQuantity = source.availableEstimatedQuantity;
    this.availableAccumulatedQuantity = source.availableAccumulatedQuantity;
    this.orderModified = source.orderModified;
    this.platform = source.platform;
    this.accountId = source.accountId;
    this.ocoClOrdId = source.ocoClOrdId;
    this.ocoOrder = source.ocoOrder;
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

  public final String getOcoClOrdId() {
    return ocoClOrdId;
  }

  public final void setOcoClOrdId(final String ocoClOrdId) {
    this.ocoClOrdId = ocoClOrdId;
  }

  public final Order getOcoOrder() {
    return ocoOrder;
  }

  public final void setOcoOrder(final Order ocoOrder) {
    this.ocoOrder = ocoOrder;
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
          position = user.setPosition(securityId, 0, null, 0, null, TokenType.ERC20);

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

  public short getQuantityOrigScale() {
    return quantityOrigScale;
  }

  public void setQuantityOrigScale(short quantityOrigScale) {
    this.quantityOrigScale = quantityOrigScale;
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

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public long getFilterId() {
    return filterId;
  }

  public void setFilterId(final long filterId) {
    this.filterId = filterId;
  }

  public boolean isExecuted() {
    return executed;
  }

  public void setExecuted(boolean executed) {
    this.executed = executed;
  }

  public boolean isRejected() {
    return rejected;
  }

  public void setRejected(boolean rejected) {
    this.rejected = rejected;
  }

  // TODO: remove this check used for debugging
  public void visit() {
    /*
     * if (markAsReturned) { try { throw new NullPointerException(); } catch (Exception e) { LOGGER.error("visit marked AsReturned" +
     * toString() + ", returnedStack=" + returnedStack, e); } }
     */
  }

  @Override
  public void markAsReturned() {
    markAsReturned = true;

/*    try {
      throw new NullPointerException();
    } catch (Exception e) {
      final StringWriter sw = new StringWriter();
      final PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      // returnedStack = sw.toString(); // stack trace as a string
    }*/
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

    if (account != Context.getMarketMakerUserid()) {
      LOGGER.info("Order received: " + this.toJSON());
    }

    final InstrumentPair instrument = InstrumentCache.getPair(securityId);
    if (null != instrument) {
      final OrderBook orderbook = instrument.getOrderBook();
      orderbook.addOrder(this);
    } else {
      LOGGER.error("Invalid instrument pair. id: " + securityId);
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
    this.price2Scale--;
    return price2Scale;
  }

  @Override
  public void clear() {
    super.clear();
    this.prev = null;
    this.next = null;
    this.marginCheckReferencePrice = 0;
    this.price2 = 0;
    this.price2Int = 0;
    this.stopPx = 0;
    this.stopPxInt = 0;
    this.price = 0;
    this.priceInt = 0;
    this.orderId = 0;
    this.quantityLong = 0;
    this.securityId = 0;
    this.type = 0;
    this.fillCumNotional = 0;
    this.origOrderId = 0;
    this.targetStrategy = 0;
    this.isHidden = false;
    this.isLiquidation = false;
    this.isLastLook = false;
    this.markAsReturned = false;
    this.feeEstimatedQuantity = 0;
    this.feeAccumulatedQuantity = 0;
    this.availableEstimatedQuantity = 0;
    this.availableAccumulatedQuantity = 0;
    this.minMaxPrice = 0;
    this.expireTime = 0;
    this.assetId = 0;
    this.tokenId = 0;
    this.selectId = 0;
    this.groupAssetId = 0;
    this.quoteType = null;
    this.quoteTargetUserId = 0;
    this.platform = null;
    this.accountId = null;
    this.symbol = null;
    this.filterId = 0;
    this.timeInForce = null;
    this.orderModified = false;
    this.toClose = false;
    this.secondaryOrderId = 0;
    this.orderPriority = 0;
    this.side = null;
    this.ordType = null;
    this.clOrdId = null;
    this.executed = false;
    this.rejected = false;
    this.inputTime = 0;
    this.ocoClOrdId = null;
    this.ocoOrder = null;
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
        .append(",\"tokenId\":").append(tokenId).append(",\"groupAssetId\":").append(groupAssetId).append(",\"selectId\":").append(selectId)
        .append(",\"platform\":\"").append(platform).append("\",\"accountId\":\"").append(accountId).append("\",\"symbol\":")
        .append(symbol != null ? "\"" + symbol + "\"" : "null").append(",\"executed\":").append(executed).append(",\"rejected\":")
        .append(rejected);
    if (quoteType != null) {
      sb.append(",\"quoteType\":\"").append(quoteType).append("\"");
    }

    sb.append("}");
    return sb.toString();
  }
}
