package com.solfini.risk;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.message.internal.Order;

import com.solfini.matchengine.publisher.SessionInfoCache;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.NewOrderSingleEncoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.QuoteType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.MbxMath;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

public class StableCoinAutoConvertThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(StableCoinAutoConvertThread.class);
  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;

  private static final String API_KAFKA_TOPIC_IN = PropertyReader.getProperty("API_KAFKA_TOPIC_IN", "api1");

  // encoder classes
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder =
      new com.solfini.sbe.encoder.MessageHeaderEncoder();
  private static final NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();

  private static StableCoinAutoConvertThread instance = null;

  private volatile boolean stopRequested = false;
  private final CountDownLatch stopLatch = new CountDownLatch(1);

  private final ManyToManyConcurrentArrayQueueCustom<User> riskToAutoConvertQueue = Context.getRiskToAutoConvertQueue();
  private final IdleStrategy idleStrategy;

  public StableCoinAutoConvertThread(final IdleStrategy idleStrategy) {
    instance = this;
    this.idleStrategy = idleStrategy;
  }

  public static StableCoinAutoConvertThread getInstance() {
    return instance;
  }

  @Override
  public void run() {
    if (!Context.isLiquidityDexEnabled() || !Context.isLiquidityImbalanceSettleEnabled()) {
      LOGGER.info("Stable coin auto conversion is not enabled for this environment. ");
      return;
    }
    LOGGER.info("StableCoinAutoConvertThread started.");
    User user = null;

    while (true) {
      try {
        if (stopRequested) {
          LOGGER.info("Shutting down StableCoinAutoConvertThread thread,");
          stopLatch.countDown();
          return;
        }

        user = riskToAutoConvertQueue.poll();
        if (user != null) {
          autoConvert(user);
        }
        idleStrategy.idle();
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }

  public final void stopThread() throws InterruptedException {
    stopRequested = true;
    stopLatch.await();
  }

  public final void autoConvert(final User user) {
    LOGGER.info(LOG_FMT_2, "Trigger auto-convert for user: ", user.getId());
    if (user.getPositionArr() == null || user.getPositionArr().length == 0) {
      LOGGER.info(LOG_FMT_3, "Trigger auto-convert for user: ", user.getId(), " no available positions.");
      return;
    }
    final Instrument usd = InstrumentCache.getBySymbol(USD);
    final boolean isTestnet = "TEST".equalsIgnoreCase(Context.getEnvironment());
    final Position usdPosition = user.getPositionArr()[usd.getId()];
    if (usdPosition == null) {
      LOGGER.info(LOG_FMT_3, "Trigger auto-convert for user: ", user.getId(), " no available USD positions.");
      return;
    }
    LOGGER.info("UserId: " + user.getId() + " USD: " + usdPosition.getUsdValue() + " qty: " + usdPosition.getQuantity() + " scale: "
        + usd.getQuantityScale());
    if (usdPosition.getUsdValue() < 0) {
      double usdPositionValue = (-usdPosition.getUsdValue() + 0.000001);// to fix rounding errors
      double amountToSettle = usdPositionValue;
      final Instrument usdc = InstrumentCache.getBySymbol(isTestnet? "T_USDC": USDC);
      final Instrument usdt = InstrumentCache.getBySymbol(isTestnet? "T_USDT": USDT);
      final Position usdcPosition = user.getPositionArr()[usdc.getId()];
      final Position usdtPosition = user.getPositionArr()[usdt.getId()];
      double usdcBalance = usdcPosition != null ? usdcPosition.getUsdValue() : 0;
      double usdtBalance = usdtPosition != null ? usdtPosition.getUsdValue() : 0;

      if (usdcBalance + usdtBalance <= 0) {
        return;
      }
      if (usdcPosition != null) {
        LOGGER.info("USDC: " + usdcPosition.toJSON());
      }

      if (usdtPosition != null) {
        LOGGER.info("USDT: " + usdtPosition.toJSON());
      }


      LOGGER.info(Constants.LOG_FMT_10, "Available usd: ", usdPosition.getUsdValue(), " scaled: ", usdPosition.getAvailableQuantity(),
          " Available USDC: ", usdcBalance, " Available USDT: ", usdtBalance, " amountToSettle: ", amountToSettle);

      if (usdcBalance >= amountToSettle) {
        final InstrumentPair pair = InstrumentCache.getPairBySymbol(isTestnet ? "T_USDC/USD" : USDC_USD);
        // DO NOT PAY MORE THAN $1 FOR A USDC
        double price =
            pair.getIndexFeedUsdMark() == 0 ? 1 : Math.min(
                MbxMath.roundDown(pair.getIndexFeedUsdMark(), usdc.getPriceScale()), 1D);
        double quantity = MbxMath.roundUp(usdPositionValue / price, usdc.getQuantityScale());
        long amount = Math.min(MbxMath.scaleUp(quantity, usdc.getQuantityScale()), usdcPosition.getQuantity());
        long unitPrice = MbxMath.scaleUp(price, usdc.getPriceScale());
        LOGGER.info("Auto convert USDC/USD userId: " + user.getId() + " USDC: " + usdcBalance + " USD: " + usdPositionValue + " price: " + price + " quantity: "
            + quantity + " amount: " + amount + " unitPrice: " + unitPrice);
        sendOrder(pair, user, amount, usdc.getQuantityScale(), unitPrice, usdc.getPriceScale());
      } else if (usdtBalance >= amountToSettle) {
        final InstrumentPair pair = InstrumentCache.getPairBySymbol(isTestnet ? "T_USDT/USD" : USDT_USD);
        // DO NOT PAY MORE THAN $1 FOR A USDT
        double price =
            pair.getIndexFeedUsdMark() == 0 ? 1 : Math.min(MbxMath.roundDown(pair.getIndexFeedUsdMark(), usdt.getPriceScale()), 1D);
        double quantity = MbxMath.roundUp(usdPositionValue / price, usdt.getQuantityScale());
        long amount = Math.min(MbxMath.scaleUp(quantity, usdt.getQuantityScale()), usdtPosition.getQuantity());
        long unitPrice = MbxMath.scaleUp(price, usdt.getPriceScale());
        LOGGER.info("Auto convert USDT/USD userId: " + user.getId() + " USDT: " + usdtBalance + " USD: " + usdPositionValue + " price: " + price + " quantity: "
            + quantity + " amount: " + amount + " unitPrice: " + unitPrice);
        sendOrder(pair, user, amount, usd.getQuantityScale(), unitPrice, usdt.getPriceScale());
      } else if (usdcBalance > 0 || usdtBalance > 0) {
        if (usdcBalance > 0) {
          InstrumentPair pair = InstrumentCache.getPairBySymbol(isTestnet ? "T_USDC/USD" : USDC_USD);
          // DO NOT PAY MORE THAN $1 FOR A USDC
          double price =
              pair.getIndexFeedUsdMark() == 0 ? 1 : Math.min(MbxMath.roundDown(pair.getIndexFeedUsdMark(), usdc.getPriceScale()), 1D);
          double usdQuantity = MbxMath.roundUp(usdcPosition.getUsdValue(), usdc.getQuantityScale());
          long amount = Math.min(MbxMath.scaleUp(usdcBalance, usdc.getQuantityScale()), usdcPosition.getQuantity());
          long unitPrice = MbxMath.scaleUp(price, usdc.getPriceScale());
          sendOrder(pair, user, usdcPosition.getQuantity(), usdc.getQuantityScale(), unitPrice, usdc.getPriceScale());
          amountToSettle -= usdQuantity;
          LOGGER.info("Auto convert USDC/USD userId: " + user.getId() + " USDC: " + usdcBalance + " USDT: " + usdtBalance + " USD: " + usdPositionValue
              + " price: " + price + " usdQuantity: " + usdQuantity + " amount: " + amount + " unitPrice: " + unitPrice);
        }
        if (usdtBalance > 0) {
          double settle = Math.min(amountToSettle, usdtBalance);
          amountToSettle -= settle;
          InstrumentPair pair = InstrumentCache.getPairBySymbol(isTestnet ? "T_USDT/USD" : USDT_USD);
          // DO NOT PAY MORE THAN $1 FOR A USDT
          double price =
              pair.getIndexFeedUsdMark() == 0 ? 1 : Math.min(MbxMath.roundDown(pair.getIndexFeedUsdMark(), usdt.getPriceScale()), 1D);
          double quantity = MbxMath.roundUp(settle / price, usdt.getQuantityScale());
          long amount = Math.min(MbxMath.scaleUp(quantity, usdt.getQuantityScale()), usdtPosition.getQuantity());
          long unitPrice = MbxMath.scaleUp(price, usdt.getPriceScale());

          sendOrder(pair, user, amount, usdt.getQuantityScale(), unitPrice, usdt.getPriceScale());
        }
        if (amountToSettle > 0) {
          LOGGER.info(LOG_FMT_8, "No enough funds to fully auto convert stable coins. userId: ", user.getId(), ", USD: ", usdPositionValue,
              ", USDC: ", usdcBalance, ", USDT: ", usdtBalance, " amountToSettle: ", amountToSettle);
        }
      } else {
        LOGGER.info(LOG_FMT_8, "No enough funds to auto convert stable coins. userId: ", user.getId(), ", USD: ", usdPositionValue,
            ", USDC: ", usdcBalance, ", USDT: ", usdtBalance);
      }
    }
  }

  public void sendOrder(final InstrumentPair pair, final User user, final long quantity, final short quantityScale,
      final long price, final short priceScale) {
    try {
      short encodedLength = HEADER_LENGTH;
      newOrderSingleEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, encodedLength, headerEncoder);
      populateHeader(headerEncoder);
      headerEncoder.sendingTime(System.currentTimeMillis());
      encodedLength += headerEncoder.encodedLength();

      // set
      newOrderSingleEncoder.userId(user.getId());
      newOrderSingleEncoder.submitterId(UserCache.getAdminUser().getId());
      newOrderSingleEncoder.clOrdID(
          String.valueOf(System.currentTimeMillis() * 1_000_000 + (System.nanoTime() % 1_000_000)));
      newOrderSingleEncoder.securityId(pair.getId());
      newOrderSingleEncoder.symbol(pair.getSymbol());
      newOrderSingleEncoder.side(Side.SELL);
      newOrderSingleEncoder.ordType(OrdType.LIMIT);
      newOrderSingleEncoder.price(price);
      newOrderSingleEncoder.priceScale(priceScale);
      newOrderSingleEncoder.qty(quantity);
      newOrderSingleEncoder.qtyScale(quantityScale);
      newOrderSingleEncoder.stopPx(0);
      newOrderSingleEncoder.stopPxScale((short) 0);
      newOrderSingleEncoder.targetStrategy(AUTO_CONVERT);
      newOrderSingleEncoder.isHidden(BooleanType.FALSE);
      newOrderSingleEncoder.isLiquidation(BooleanType.FALSE);
      newOrderSingleEncoder.timeInForce(TimeInForce.GOOD_TILL_CANCEL);
      newOrderSingleEncoder.quoteType(QuoteType.NULL_VAL);

      encodedLength += newOrderSingleEncoder.encodedLength();
      buffer.limit(encodedLength);
      encoderUnsafeBuffer.putShort(0, encodedLength);

      final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(encoderUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
      Context.getKafkaPublisher().sendDirect(bytesWithKafkaOffset, API_KAFKA_TOPIC_IN);
    } catch (Exception e) {
      LOGGER.error("error in sendUser", e);
    }
  }

  private void populateHeader(final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder) {
    final SessionInfo sessionInfo = SessionInfoCache.DEFAULT;

    headerEncoder.msgSeqNum(sessionInfo.incrementAndGetMessageSequenceNumber());
    headerEncoder.sendingTime(System.currentTimeMillis());
    headerEncoder.sourceSeqNum(0); // sourceSeqNum
    headerEncoder.kafkaRecordOffset(0); // kafkaRecordOffset
    headerEncoder.senderCompId(Context.getInstanceId()); // instance id
    headerEncoder.deliverToCompId(Context.getMESeqId()); // matching engine seq num

  }

  public final Order buildNewAutoConvertOrder(final InstrumentPair pair, final User user, final long quantity, final short quantityScale,
      final long price, final short priceScale, final long kafkaOffset) {
    final Order order = OrderObjectPool.get();
    order.setKafkaRecordOffset(kafkaOffset);
    order.setClOrdId(String.valueOf(TimeUtil.getTime()));
    order.setSecurityId(pair.getId());
    order.setSymbol(pair.getSymbol());
    order.setOrderId(NewOrderSingleHandler.getNextOrderId());
    order.setOrderPriority(0);
    order.setUser(user);
    order.setSubmitterId(UserCache.getAdminUser().getId());
    order.setTargetStrategy(AUTO_CONVERT);

    order.setHidden(false);
    order.setLiquidation(false);
    order.setLastLook(false);

    order.setAccount(user.getId());
    order.setPrice(price, priceScale);
    order.setQty(quantity, quantityScale);

    order.setSide(Side.SELL);
    order.setOrdType(OrdType.LIMIT);
    order.setToClose(false);

    order.setPrice2(0L,(short) 0);
    order.setMarginCheckReferencePrice(0);
    order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
    order.setExpireTime(0);
    order.setStopPx(0l, (short) 0);
    order.setQuoteType(QuoteType.NULL_VAL);

    NewOrderSingleHandler.parseOrder(order);

    return order;
  }
}
