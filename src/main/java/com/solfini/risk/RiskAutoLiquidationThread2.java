package com.solfini.risk;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.message.internal.LiquidationOrder;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.publisher.SessionInfoCache;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.NewOrderSingleEncoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class RiskAutoLiquidationThread2 implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(RiskAutoLiquidationThread2.class);
  private static RiskAutoLiquidationThread2 instance = null;

  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;

  private final ManyToManyConcurrentArrayQueueCustom<User> riskToAutoLiquidatorQueue;
  private final ManyToOneConcurrentArrayQueueCustom<Message> riskToMatcherQueue;
  private static final NewOrderSingleHandler newOrderSingleHandler = new NewOrderSingleHandler();
  private static final PreOrderCheck preOrderCheck = new MarginPreOrderCheckAndSettle();
  private static final boolean TO_CLOSE = true;
  private final double[] usdMarkPricesToSet = new double[Math.max(InstrumentCache.getPairCapacity(), 128)];

  private final IdleStrategy idleStrategy;
  private static final String clOrdId = "autoclose";
  public static final String API_KAFKA_TOPIC_IN = PropertyReader.getProperty("API_KAFKA_TOPIC_IN", "api1");

  // encoder classes
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder =
      new com.solfini.sbe.encoder.MessageHeaderEncoder();
  private static final NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();

  private volatile boolean stopRequested = false;
  private final CountDownLatch stopLatch = new CountDownLatch(1);
  public static final double SLIPPAGE_PREMIUM = PropertyReader.getProperty("SLIPPAGE_PREMIUM", 1.005d); // FEE is 37.5 bps
  public static final double SLIPPAGE_DISCOUNT = PropertyReader.getProperty("SLIPPAGE_DISCOUNT", 0.995d); // FEE is 37.5 bps

  public RiskAutoLiquidationThread2(final IdleStrategy idleStrategy) {
    instance = this;
    this.riskToAutoLiquidatorQueue = Context.getRiskToAutoLiquidatorQueue();
    this.riskToMatcherQueue = Context.getRiskToMatcherQueue();
    this.idleStrategy = idleStrategy;
  }

  public static RiskAutoLiquidationThread2 getInstance() {
    return instance;
  }

  public void run() {
    User user = null;
    final List<Order> list = new ArrayList<>();

    while (true) {
      try {
        if (stopRequested) {
          LOGGER.info("Shutting down auto liquidation thread");
          stopLatch.countDown();
          return;
        }

        // regular messages
        user = riskToAutoLiquidatorQueue.poll();
        if (user != null) {
          list.clear();
          autoLiquidate(user, list);
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

  public final void autoLiquidate(final User user, final List<Order> list) {
    try {
      LOGGER.info(LOG_FMT_2, ">>> verbose autoLiquidate user=", user);
      final String senderCompId = "" + 1_000_000_000 + user.getId();

      // skip if user is insuranceUser
      final User insuranceUser = InsuranceState.getUser();
      if (insuranceUser != null && user.getId() == insuranceUser.getId())
        return;
      // skip if user is exchangeUser
      final User exchangeUser = UserCache.getExchangeUser();
      if (exchangeUser != null && user.getId() == exchangeUser.getId())
        return;

      // sends mass cancel for every position
      final MassCancelOrder massCancelOrder = new MassCancelOrder();
      massCancelOrder.setUser(user);
      massCancelOrder.setAccount(user.getId());
      massCancelOrder.setSenderCompId(senderCompId);

      LOGGER.info(LOG_FMT_6, ">>> verbose autoLiquidate", user.getId(), ", massCancelOrder=", massCancelOrder, ", riskToMatcherQueue=",
          riskToMatcherQueue.toString());

      riskToMatcherQueue.addGuaranteed(massCancelOrder);

      calcBankruptcyPrices(user, list, usdMarkPricesToSet);

      // add LiquidationOrders
      Collections.sort(list, liquidationComparator);
      user.getAutoLiquidationCounter().set(list.size());


      if (Context.isLiquidationOrderExternalRouting()) {
        LOGGER.info(LOG_FMT_2, "Sending liquidation orders via external routing to input Kafka topic: count=", list.size());
        for (Order order : list) {
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(LOG_FMT_2, "Sending liquidation order: route=external, order=", order);
          }
          sendOrder(order, true);
        }
      } else {
        LOGGER.info("LOG_FMT_2, Sending liquidation orders via internal routing to matching thread: count=", list.size());
        for (Order order : list) {
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("LOG_FMT_2, Sending liquidation order: route=internal, order=", order);
          }
          riskToMatcherQueue.addGuaranteed(order);
        }
      }

    } catch (Exception e) {
      LOGGER.error("error in autoliquidator " + user, e);
    }
  }

  public void sendOrder(final Order order, final boolean isLiquidation) {
    try {
      short encodedLength = HEADER_LENGTH;
      newOrderSingleEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, encodedLength, headerEncoder);
      populateHeader(headerEncoder, order);
      headerEncoder.sendingTime(System.currentTimeMillis());
      encodedLength += headerEncoder.encodedLength();

      // set
      newOrderSingleEncoder.userId(order.getUser().getId());
      newOrderSingleEncoder.clOrdID(order.getClOrdId());
      newOrderSingleEncoder.securityId(order.getSecurityId());
      newOrderSingleEncoder.side(order.getSide());
      newOrderSingleEncoder.ordType(order.getOrdType());
      newOrderSingleEncoder.price(order.getPrice());
      newOrderSingleEncoder.priceScale(order.getPriceScale());
      newOrderSingleEncoder.qty(order.getQty());
      newOrderSingleEncoder.qtyScale(order.getQtyScale());
      newOrderSingleEncoder.stopPx((int) order.getStopPx());
      newOrderSingleEncoder.stopPxScale(order.getStopPxScale());
      newOrderSingleEncoder.targetStrategy(order.getTargetStrategy());
      newOrderSingleEncoder.isHidden(order.isHidden() ? BooleanType.TRUE : BooleanType.FALSE);
      newOrderSingleEncoder.isLiquidation(isLiquidation ? BooleanType.TRUE : BooleanType.FALSE);

      encodedLength += newOrderSingleEncoder.encodedLength();
      buffer.limit(encodedLength);
      encoderUnsafeBuffer.putShort(0, encodedLength);

      final byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(encoderUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
      Context.getKafkaPublisher().sendDirect(bytesWithKafkaOffset, API_KAFKA_TOPIC_IN);
    } catch (Exception e) {
      LOGGER.error("error in sendUser", e);
    }
  }

  public static final int getMarkIntPrice(final InstrumentPair instrumentPair, final double[] usdMarkPricesToSet, final Side side) {
    // get price
    double usdMark = usdMarkPricesToSet[instrumentPair.getId()];
    for (int i = 0; i < instrumentPair.getPriceScale(); i++)
      usdMark = usdMark * 10;
    int bankruptPriceInt = (int) usdMark;

    if (bankruptPriceInt > 0)
      return bankruptPriceInt;

    final OrderBook orderBook = instrumentPair.getOrderBook();
    if (Side.SELL == side) {
      bankruptPriceInt = orderBook.getAsk();
      if (bankruptPriceInt > 0)
        return bankruptPriceInt;

      bankruptPriceInt = orderBook.getBid();
      if (bankruptPriceInt > 0)
        return bankruptPriceInt;
    } else {
      bankruptPriceInt = orderBook.getBid();
      if (bankruptPriceInt > 0)
        return bankruptPriceInt;

      bankruptPriceInt = orderBook.getAsk();
      if (bankruptPriceInt > 0)
        return bankruptPriceInt;
    }

    bankruptPriceInt = orderBook.getMark();
    if (bankruptPriceInt > 0)
      return bankruptPriceInt;


    if (bankruptPriceInt == 0) {
      usdMark = instrumentPair.getIndexFeedUsdMark();
      for (int i = 0; i < instrumentPair.getPriceScale(); i++)
        usdMark = usdMark * 10;
      bankruptPriceInt = (int) usdMark;
      return bankruptPriceInt;
    }

    return 1;
  }

  // calcBankruptcyPrices AND generate liquidation orders, must be called by this thread so its private
  private static final void calcBankruptcyPrices(final User user, final List<Order> list, final double[] usdMarkPricesToSet) {
    // update risk and get arr of mark prices used
    preOrderCheck.updateRisk(user, usdMarkPricesToSet);

    final double usdValue = user.getUsdValue();
    final double positionValue = user.getUsdNotionalPositionValue();
    double lossMargin = 0;
    boolean hasEquity = false;

    if (usdValue > 0 && positionValue > 0) {
      lossMargin = Math.abs(usdValue / (positionValue));
      hasEquity = true;
    } else if (positionValue != 0) {
      lossMargin = Math.abs(usdValue / (positionValue));
    }
    LOGGER.info(LOG_FMT_8, ">>> calcBankruptcyPrices, lossMargin=", lossMargin, ", hasEquity=", hasEquity, USDVALUE_EQ, usdValue,
        ", positionValue=", positionValue, USER_EQ, user);
    if (lossMargin > 1)
      lossMargin = SLIPPAGE_DISCOUNT;


    final Position[] positionArr = user.getPositionArr();
    for (final Position position : positionArr) {
      if ((position != null) && (position.getQuantity() != 0) && (AssetType.ASSET != position.getAssetType())) {
        final InstrumentPair instrumentPair = InstrumentCache.getPair(position.getInstrumentId());
        if (instrumentPair != null) {
          final short price_scale = instrumentPair.getPriceScale();

          // get price
          int bankruptPriceInt = 0;

          Side side = Side.SELL;
          if (position.getQuantity() > 0) {
            bankruptPriceInt = getMarkIntPrice(instrumentPair, usdMarkPricesToSet, side);
            LOGGER.info(LOG_FMT_2, ">>> calcBankruptcyPrices1, SELL bankruptPriceInt=", bankruptPriceInt);

            bankruptPriceInt = hasEquity ? (int) (bankruptPriceInt * (SLIPPAGE_PREMIUM - lossMargin))
                : (int) (bankruptPriceInt * (SLIPPAGE_PREMIUM + lossMargin)); // sell
            // to close
            LOGGER.info(LOG_FMT_10, ">>> calcBankruptcyPrices2, SELL lossMargin=", lossMargin, ", hasEquity=", hasEquity, USDVALUE_EQ,
                usdValue, ", positionValue=", positionValue, ", bankruptPriceInt=", bankruptPriceInt);
          } else {
            side = Side.BUY;
            bankruptPriceInt = getMarkIntPrice(instrumentPair, usdMarkPricesToSet, side);
            LOGGER.info(LOG_FMT_2, ">>> calcBankruptcyPrices3, BUY bankruptPriceInt=", bankruptPriceInt);

            bankruptPriceInt = hasEquity ? (int) (bankruptPriceInt * (SLIPPAGE_DISCOUNT + lossMargin))
                : (int) (bankruptPriceInt * (SLIPPAGE_DISCOUNT - lossMargin));
            // to buy
            LOGGER.info(LOG_FMT_10, ">>> calcBankruptcyPrices4, BUY lossMargin=", lossMargin, ", hasEquity=", hasEquity, USDVALUE_EQ,
                usdValue, ", positionValue=", positionValue, ", bankruptPriceInt=", bankruptPriceInt); // close
          }
          position.setBankruptPriceInt(bankruptPriceInt);


          final String senderCompId = "" + 1_000_000_000 + user.getId();
          final Message message =
              newOrderSingleHandler.buildNewLiquidationOrder(user, senderCompId, position.getInstrumentId(), clOrdId, bankruptPriceInt,
                  price_scale, Math.abs(position.getQuantity()), instrumentPair.getQuantityScale(), side, OrdType.LIMIT, TO_CLOSE);
          LOGGER.debug(LOG_FMT_6, ">>> verbose autoLiquidate", user.getId(), MESSAGE_EQ, message, INSTRUMENT_PAIR_EQ, instrumentPair);
          if (message instanceof LiquidationOrder)
            list.add((LiquidationOrder) message);
        }
      }
    }
  }

  private void populateHeader(final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder, final Message message) {
    final SessionInfo sessionInfo = SessionInfoCache.DEFAULT;

    headerEncoder.msgSeqNum(sessionInfo.incrementAndGetMessageSequenceNumber());
    headerEncoder.sendingTime(System.currentTimeMillis());
    headerEncoder.sourceSeqNum(message.getSourceSeqNum()); // sourceSeqNum
    headerEncoder.kafkaRecordOffset(message.getKafkaRecordOffset()); // kafkaRecordOffset
    headerEncoder.senderCompId(Context.getInstanceId()); // instance id
    headerEncoder.deliverToCompId(Context.getMESeqId()); // matching engine seq num
  }


  // sort by largest to smallest
  private static final Comparator<Order> liquidationComparator = new Comparator<Order>() {
    @Override
    public int compare(final Order order1, final Order order2) {
      try {
        final double notional1 = order1.calcNotional();
        final double notional2 = order2.calcNotional();
        if (notional1 == notional2)
          return 0;
        else if (notional1 > notional2)
          return -1;
        else
          return 1;
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
      return 0;
    }
  };
}
