package com.solfini.matchengine.message.admin;

import com.solfini.common.AdminMessage;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.preordercheck.NotionalMarginCalc;
import com.solfini.risk.InsuranceState;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class CollateralSwapMessage extends AdminMessage implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(CollateralSwapMessage.class);

  private static final ManyToOneConcurrentArrayQueueCustom<Message> riskToMatcherQueue = Context.getRiskToMatcherQueue();
  private static Instrument settleCoinUsdMarkInstrument;
  private static Instrument btcCoinUsdMarkInstrument;


  private UpdateType updateType;
  private long timestamp;

  public CollateralSwapMessage(final User user) {
    timestamp = System.currentTimeMillis();
    this.user = user;
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  public final MessageType getMessageType() {
    return MessageType.COLLATERAL_SWAP;
  }

  public final UpdateType getUpdateType() {
    return updateType;
  }

  public final void setUpdateType(final UpdateType updateType) {
    this.updateType = updateType;
  }

  public static final Instrument getSettleCoinUsdMarkInstrument() {
    return settleCoinUsdMarkInstrument;
  }

  public static final Instrument getBtcCoinUsdMarkInstrument() {
    return btcCoinUsdMarkInstrument;
  }

  public static final void checkCollateralBalance(final User user) {
    try {
      // skip if already in collateral swap state
      if (user == null || user.getCollateralSwapState().get() != 0)
        return;

      // skip if user is insuranceUser
      final User insuranceUser = InsuranceState.getUser();
      if (insuranceUser != null && user.getId() == insuranceUser.getId())
        return;
      // skip if user is exchangeUser
      final User exchangeUser = UserCache.getExchangeUser();
      if (exchangeUser != null && user.getId() == exchangeUser.getId())
        return;

      final Position[] positionArr = user.getPositionArr();
      if (positionArr == null)
        return;

      if (settleCoinUsdMarkInstrument == null)
        settleCoinUsdMarkInstrument = InstrumentCache.getBySymbol("USDC");
      if (btcCoinUsdMarkInstrument == null)
        btcCoinUsdMarkInstrument = InstrumentCache.getBySymbol("BTC");
      if (settleCoinUsdMarkInstrument == null || btcCoinUsdMarkInstrument == null)
        return;

      final Position usdPosition = positionArr[settleCoinUsdMarkInstrument.getId()];
      final Position btcPosition = positionArr[btcCoinUsdMarkInstrument.getId()];
      if (usdPosition == null || user.getUsdValue() < 0)
        return;

      if (usdPosition.getQuantity() >= 0 && (btcPosition == null || btcPosition.getQuantity() >= 0))
        return;

      LOGGER.warn(LOG_FMT_2, ">>> checkCollateralBalance user=", user.getId(), ", usdPosition=", usdPosition, ", btcPosition=",
          btcPosition);

      if (user.getCollateralSwapState().compareAndSet(0, 1)) {
        final CollateralSwapMessage message = new CollateralSwapMessage(user);
        riskToMatcherQueue.addGuaranteed(message);
      }
    } catch (Exception e) {
      LOGGER.info(ERROR_LOG, e);
    }
  }

  // called from matching thread
  // if USD > 0 and BTC < 0 then buy BTC to balance
  private final void applyCollateralSwapSellUSD(final Position settlePosition, final Position btcPosition,
      final Instrument settleInstrument, final Instrument buyInstrument) {
    LOGGER.warn(LOG_FMT_2, ">>> applyCollateralSwapSellUSD user=", user.getId(), ", settlePosition=", settlePosition, ", btcPosition=",
        btcPosition);
    InstrumentPair instrumentPair = InstrumentCache.getPairBySymbol(buyInstrument.getSymbol() + "/USD");
    if (instrumentPair == null)
      instrumentPair = InstrumentCache.getPairBySymbol(buyInstrument.getSymbol() + "/USDC");

    if (instrumentPair == null || instrumentPair.getMarketStatus() == MarketStatus.CLOSE)
      return;

    final OrderBook orderBook = instrumentPair.getOrderBook();
    double usdMarkTradePrice = orderBook.getUsdMark();

    double usdQty = StringUtil.toDouble(settlePosition.getQuantity(), settleInstrument.getQuantityScale());
    double btcQty = StringUtil.toDouble(Math.abs(btcPosition.getQuantity()), buyInstrument.getQuantityScale());
    double discountMultiplier = 1 / NotionalMarginCalc.calcImpactDiscount(buyInstrument, btcQty);
    usdMarkTradePrice = usdMarkTradePrice * discountMultiplier;
    double notional = btcQty * usdMarkTradePrice;

    if (usdQty > notional) {
      long qty = instrumentPair.adjustQuantityToScale(Math.abs(btcPosition.getQuantity()), buyInstrument.getQuantityScale());
      final Order order = orderBook.onCollateralSwapOrder(user, (long) (usdMarkTradePrice * 100), (short) 2, qty,
          instrumentPair.getQuantityScale(), Side.BUY);
      LOGGER.info(LOG_FMT_2, ">>> applyCollateralSwapSellUSD1 traded=", order);
    } else if (notional > 0) {
      double percentFill = usdQty / notional;
      long qty = (long) (percentFill
          * (instrumentPair.adjustQuantityToScale(Math.abs(btcPosition.getQuantity()), buyInstrument.getQuantityScale())));
      final Order order = orderBook.onCollateralSwapOrder(user, (long) (usdMarkTradePrice * 100), (short) 2, qty,
          instrumentPair.getQuantityScale(), Side.BUY);
      LOGGER.info(LOG_FMT_2, ">>> applyCollateralSwapSellUSD2 traded=", order);
    }
  }

  // called from matching thread
  // If the USDC balance falls below 0. we will create an immediate synthetic coin swap marked at our latest spot price. The balance swap
  // will be published in the position report with txType=TX_COLLATERAL_SWAP
  private final void applyCollateralSwap() {
    LOGGER.warn(LOG_FMT_2, ">>> applyCollateralSwap user=", user.getId());
    try {
      final Position[] positionArr = user.getPositionArr();
      if (positionArr == null)
        return;

      if (settleCoinUsdMarkInstrument == null)
        settleCoinUsdMarkInstrument = InstrumentCache.getBySymbol("USDC");
      if (settleCoinUsdMarkInstrument == null)
        settleCoinUsdMarkInstrument = InstrumentCache.getBySymbol("USDT");
      if (btcCoinUsdMarkInstrument == null)
        btcCoinUsdMarkInstrument = InstrumentCache.getBySymbol("BTC");
      if (settleCoinUsdMarkInstrument == null || btcCoinUsdMarkInstrument == null)
        return;

      final Position settlePosition = positionArr[settleCoinUsdMarkInstrument.getId()];
      final Position btcPosition = positionArr[btcCoinUsdMarkInstrument.getId()];
      if (settlePosition == null || user.getUsdValue() < 0)
        return;

      // special case sell USD for BTC if BTC is negative
      if (settlePosition.getQuantity() >= 0 && btcPosition != null && btcPosition.getQuantity() < 0) {
        applyCollateralSwapSellUSD(settlePosition, btcPosition, settleCoinUsdMarkInstrument, btcCoinUsdMarkInstrument);
        return;
      }

      if (settlePosition.getQuantity() >= 0)
        return;

      final Instrument settleInstrument = InstrumentCache.get(settleCoinUsdMarkInstrument.getId());
      double settleNotional =
          settleInstrument.getQuantityScaleFactor() * settlePosition.getQuantity() * settleInstrument.getIndexFeedUsdMark();
      if (settleNotional > 0)
        return;


      for (final Position position : positionArr) { // TODO: limit loop using user.maxActivePositionIndexHint
        if (position != null && position.getQuantity() > 0) {
          final Instrument instrument = InstrumentCache.get(position.getInstrumentId());
          if (instrument == null)
            continue;
          double usdMarkTradePrice = instrument.getIndexFeedUsdMark();
          if (usdMarkTradePrice == 0)
            continue;

          long settlePositionLong = settlePosition.getQuantity();
          settleNotional = settleInstrument.getQuantityScaleFactor() * settlePositionLong * settleInstrument.getIndexFeedUsdMark();

          double qtyRequiredEstimated = Math.abs(settleNotional / usdMarkTradePrice);
          double discountMultiplier = NotionalMarginCalc.calcImpactDiscount(instrument, qtyRequiredEstimated);
          usdMarkTradePrice = usdMarkTradePrice * discountMultiplier;

          double qtyRequired = Math.abs(settleNotional / usdMarkTradePrice) / discountMultiplier;
          double coinPositionQuantity = instrument.getQuantityScaleFactor() * position.getQuantity();
          // instrument.getQuantityMultiplier() *
          // attempt to sell into the spot market
          InstrumentPair instrumentPair = InstrumentCache.getPairBySymbol(instrument.getSymbol() + "/USD");
          if (instrumentPair == null)
            instrumentPair = InstrumentCache.getPairBySymbol(instrument.getSymbol() + "/USDC");

          if (instrumentPair != null && instrumentPair.getMarketStatus() != MarketStatus.CLOSE) {
            final OrderBook orderBook = instrumentPair.getOrderBook();
            final long positionQuantityLong =
                position.getQuantity() * instrumentPair.getQuantityScaleMultiplier() / instrument.getQuantityMultiplier();
            final long qtyRequiredLong = (long) (qtyRequired * instrumentPair.getQuantityScaleMultiplier());

            // sell full position to counterparty OR sell just enough to qtyRequired
            final long qty = qtyRequiredLong < positionQuantityLong ? qtyRequiredLong : positionQuantityLong;

            LOGGER.info(LOG_FMT_2, ">>> applyCollateralSwap1 settlePosition=", settlePosition, ", coinposition=", position);

            final Order order = orderBook.onCollateralSwapOrder(user, (long) (usdMarkTradePrice * 100), (short) 2, qty,
                instrumentPair.getQuantityScale(), Side.SELL);

            LOGGER.info(LOG_FMT_2, ">>> applyCollateralSwap2 traded=", order);
            LOGGER.info(LOG_FMT_2, ">>> applyCollateralSwap3 settlePosition=", settlePosition, ", coinposition=", position);

            if (order.getQuantityLong() == 0) {
              // success selling into market
              continue;
            }
          }

          LOGGER.info(LOG_FMT_2, ">>> applyCollateralSwap4 settleNotional=", settleNotional, ", qtyRequiredEstimated=",
              qtyRequiredEstimated, ", discountMultiplier=", discountMultiplier, ", usdMarkTradePrice=", usdMarkTradePrice,
              ", coinPositionQuantity=", coinPositionQuantity, ", qtyRequired=", qtyRequired, ", settlePositionLong=", settlePositionLong);


          if (settlePositionLong >= 0) // if settlePositionLong is back above 0, stop
            return;
        }
      }
    } catch (Throwable e) {
      LOGGER.info(ERROR_LOG, e);
      e.printStackTrace();
    } finally {
      user.getCollateralSwapState().set(0);
    }
  }

  @Override
  public final void onMatcher() {
    LOGGER.info(LOG_FMT_2, ">>> applyCollateralSwap timestamp=", timestamp);

    applyCollateralSwap();

    LOGGER.info(LOG_FMT_2, "<<< applyCollateralSwap timestamp=", timestamp, ", now=", System.currentTimeMillis());
  }

  @Override
  public final void onPublish() {
    // do nothing
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append(COLLATERALSWAPMESSAGE_UPDATETYPE_EQ).append(updateType).append(TIMESTAMP_EQ).append(timestamp).append(ROUTETODESTINATION_EQ)
        .append(routeToDestination).append(TRIGGERTIMEMILLIS_EQ).append(triggerTimeMillis).append(USER_EQ).append(user)
        .append(SOURCESEQNUM_EQ).append(sourceSeqNum).append(SOURCESENDTIME_EQ).append(sourceSendTime).append(']');
    return s;
  }

  @Override
  public final String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"CollateralSwapMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis).append(",\"externalId\":").append(externalId)
        .append(",\"timestamp\":").append(timestamp).append(",\"userId\":").append(user.getId()).append(",\"updateType\":")
        .append(updateType.value());
    sb.append(",\"routeToDestination\":").append("\"").append(routeToDestination).append("\"");
    sb.append("}");
    return sb.toString();
  }

}
