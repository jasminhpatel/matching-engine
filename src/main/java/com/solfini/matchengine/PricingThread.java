package com.solfini.matchengine;

import java.util.TimeZone;
import org.agrona.concurrent.IdleStrategy;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.common.ReusableLog;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.outbound.OptionPricingMessage;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.matchengine.orderbook.TrailingStopContainer;
import com.solfini.matchengine.pricing.DeribitCache;
import com.solfini.matchengine.pricing.DeribitLastTrade;
import com.solfini.matchengine.stats.ChartStats;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.util.BlackScholes;
import com.solfini.util.BlackScholesVolatilityBrentCalc;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class PricingThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PricingThread.class);
  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
  public static final long ONE_YEAR = 31_536_000_000L;

  public static final double OPTION_RATE_INTEREST_RATE =
      StringUtil.toDouble(PropertyReader.getProperty("OPTION_RATE_INTEREST_RATE", "0.02"));

  private final ManyToOneConcurrentArrayQueueCustom<Message> riskToMatcherQueue;
  private final IdleStrategy idleStrategy;
  private long deribitLastLoadedTime = 0;

  public PricingThread(final IdleStrategy idleStrategy) {
    this.riskToMatcherQueue = Context.getRiskToMatcherQueue();
    this.idleStrategy = idleStrategy;


    LOGGER.info(LOG_FMT_1, "starting PricingThread Mode=", Context.getControllerMode(), ", Status=", Context.getMarketStatus());
  }

  public void run() {
    while (true) {
      try {
        if ((Mode.PRIMARY != Context.getControllerMode())) {
          Thread.sleep(0);
          continue;
        }
        if ((MarketStatus.OPEN != Context.getMarketStatus())) {
          Thread.sleep(0);
          continue;
        }

        calcOptionPricing();
        Thread.sleep(2000); // sleep for 2 second: TODO: remove this later

        // calc trailing stops 10 times for 1 second time
        for (int i = 0; i < 1; i++) {
          calcTrailingStops();
          Thread.sleep(1000);
        }

        idleStrategy.idle();
      } catch (Throwable e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }


  private final double calcUsdUnderlyerPrice(final int underlyerId) {
    final InstrumentPair pair = InstrumentCache.getPair(underlyerId);
    return pair.getIndexFeedUsdMark();
  }

  // use vwap of last 5 minutes, or last option price to brent solve for implied volatility
  // if that fails, attempt to use deribit data
  private final double calcImpliedVolatility(final InstrumentPair pair, final double usdUnderlyerPrice, final double strikePrice,
      final double rate, final double time, final double div) {
    try {
      final ChartStats chartStats = pair.getTradeHistory().getRolling5minsStats();
      final double vwap = StringUtil.toDouble(chartStats.getVWAP(), pair.getPriceScale());
      final double last = vwap > 0 ? vwap : pair.getOrderBook().getUsdMark();
      final double usdUnderlyerMark = chartStats.getUnderlyerMark() > 0 ? chartStats.getUnderlyerMark() : usdUnderlyerPrice;

      // attempt to solve
      if (last > 0) {
        final boolean isCall = (AssetType.OPTION_CALL == pair.getAssetType());
        final double exchangeIV = BlackScholesVolatilityBrentCalc.solve(last, isCall, usdUnderlyerMark, strikePrice, rate, time, div);
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_24, "calcImpliedVolatility pairId=", pair.getId(), ", symbol=", pair.getSymbol(), ", exchangeIV=",
              exchangeIV, ", usdUnderlyerMark=", usdUnderlyerMark, ", usdUnderlyerPrice=", usdUnderlyerPrice, ", isCall=", isCall,
              ", strikePrice=", strikePrice, ", rate=", rate, ", time=", time, ", last=", last, ", vwap=", vwap, ", getUsdMark=",
              pair.getOrderBook().getUsdMark());
        }
        if (exchangeIV > 0)
          return exchangeIV;
      }

      // load DeribitCache only if 10 seconds stale data
      final long now = System.currentTimeMillis();
      if (now - deribitLastLoadedTime > 10_000) {
        DeribitCache.load();
        deribitLastLoadedTime = now;
      }

      // attempt to use deribit data
      final DeribitLastTrade deribitLastTrade = DeribitCache.lookupLast(pair.getId());
      if (deribitLastTrade != null) {
        final double deribitIV = deribitLastTrade.getIv() * .01;
        if (deribitIV > 0) {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_22, "calcImpliedVolatility deribitIV pairId=", pair.getId(), ", symbol=", pair.getSymbol(), ", deribitIV=",
                deribitIV, ", usdUnderlyerPrice=", usdUnderlyerPrice, ", isCall=", (AssetType.OPTION_CALL == pair.getAssetType()),
                ", strikePrice=", strikePrice, ", rate=", rate, ", time=", time, ", last=", last, ", vwap=", vwap, ", getUsdMark=",
                pair.getOrderBook().getUsdMark());
          }
          return deribitIV;
        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      e.printStackTrace();
    }

    return 5.0; // default
  }

  private final void calcOptionPricing(final InstrumentPair pair, final long now, final int updateType) {
    final OrderBook orderBook = pair.getOrderBook();
    final long bid = orderBook != null ? orderBook.getBid() : 0;
    final long ask = orderBook != null ? orderBook.getAsk() : 0;
    final long last = orderBook != null ? orderBook.getLast() : 0;
    final long openQty = pair.getOpenQty();

    final double usdUnderlyerPrice = calcUsdUnderlyerPrice(pair.getUnderlyerId());
    final double strikePrice = pair.getUsdStrikePrice();
    final double interestRate = OPTION_RATE_INTEREST_RATE;
    final double timeToExpire = (pair.getContractExpireTime() - now) / 31_536_000_000D;
    final double div_yield = pair.getDividend(); // usually 0
    // example .65, 1=100%, implied volatility
    final double sigma = calcImpliedVolatility(pair, usdUnderlyerPrice, strikePrice, interestRate, timeToExpire, div_yield);

    // Black Scholes calc
    final double[] result = BlackScholes.calc(usdUnderlyerPrice, strikePrice, interestRate, sigma, timeToExpire, div_yield);

    pair.setSigma(sigma);
    pair.setTimeToExpire(timeToExpire);
    final OptionPricingMessage message = new OptionPricingMessage();
    if (AssetType.OPTION_CALL == pair.getAssetType()) {
      pair.setIndexFeedUsdMark(result[0]); // call option price
      message.set(now, pair.getId(), updateType, pair.getUnderlyerId(), pair.getStrikePrice(), pair.getContractExpireTime(), strikePrice,
          usdUnderlyerPrice, result[0], interestRate, timeToExpire, div_yield, result[1], result[2], result[3], result[4], result[10],
          result[11], sigma, bid, ask, last, openQty);
    } else if (AssetType.OPTION_PUT == pair.getAssetType()) {
      pair.setIndexFeedUsdMark(result[5]); // put option price
      message.set(now, pair.getId(), updateType, pair.getUnderlyerId(), pair.getStrikePrice(), pair.getContractExpireTime(), strikePrice,
          usdUnderlyerPrice, result[5], interestRate, timeToExpire, div_yield, result[6], result[7], result[8], result[9], result[10],
          result[11], sigma, bid, ask, last, openQty);
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_8, "calcOptionPricing, message=", message, ", pair", pair, ", last=", last, ", sigma=", sigma);
    }

    riskToMatcherQueue.addGuaranteed(message);
  }

  private final void calcOptionPricing() {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_1, ">> calcOptionPricing");
    }
    final long now = System.currentTimeMillis();
    final int updateType = 0;
    try {
      for (int i = 0; i < InstrumentCache.getPairCapacity(); i++) {
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair == null || (AssetType.OPTION_CALL != pair.getAssetType() && AssetType.OPTION_PUT != pair.getAssetType()))
          continue;

        calcOptionPricing(pair, now, updateType);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_1, "<< calcOptionPricing");
    }
  }

  private final void calcTrailingStops() {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_1, ">> calcTrailingStops");
    }
    try {
      for (int i = 0; i < InstrumentCache.getPairCapacity(); i++) {
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair == null)
          continue;

        final OrderBook orderBook = pair.getOrderBook();
        if (orderBook == null)
          continue;

        final TrailingStopContainer trailingStopContainer = orderBook.getTrailingStopContainer();
        if (trailingStopContainer == null)
          continue;

        final int last = orderBook.getLast();
        if (last > 0)
          trailingStopContainer.recalc(last);
      }
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_1, "<< calcTrailingStops");
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public static void main2(final String[] args) {
    final long now = System.currentTimeMillis();
    final int updateType = 0;
    PricingThread pricingThread = new PricingThread(null);

    final int id = 103;
    final String symbol = "BTC/USDC[C]Jun26_4000";
    final String name = "BTC/USDC[C]Jun26_4000";
    final Instrument base = null;
    final Instrument quoted = null;
    final short priceScale = 2;
    final short quantityScale = 2;
    final int settleType = 1;
    final AssetType assetType = AssetType.OPTION_PUT;
    final int maintMarginBasisPoints = 2;
    final int requiredMarginBasisPoints = 2;
    final double usdMark = 7741.38;
    final int marginCurveId = 1;
    final long expireTimeMillis = 1593216000000L;
    final int strikePrice = 400000;
    final int underlyerId = 3;
    final long minOrderQuantity = 0;
    final int auctionStartTimeHrGMT = 0;
    final long auctionDurationTime = 0;
    final int auctionFixingAttempts = 0;
    final long auctionFixingWaitTime = 0;
    final double circuitBreakerThreshold = 0;
    final long expireRollTimeMillis = 0;
    final int symbolRollCount = 0;
    final boolean physicalSettle = false;
    final boolean isLimitOnlyMode = false;

    final InstrumentPair pair = new InstrumentPair(id, symbol, name, base, quoted, priceScale, quantityScale, settleType, assetType,
        maintMarginBasisPoints, requiredMarginBasisPoints, usdMark, marginCurveId, expireTimeMillis, strikePrice, underlyerId,
        minOrderQuantity, auctionStartTimeHrGMT, auctionDurationTime, auctionFixingAttempts, auctionFixingWaitTime, circuitBreakerThreshold,
        expireRollTimeMillis, symbolRollCount, physicalSettle, isLimitOnlyMode);

    pricingThread.calcOptionPricing(pair, now, updateType);
  }

  public static void main(final String[] args) {
    ReusableLog.setTEST_OUTPUT_MODE(true);
    final long now = System.currentTimeMillis();
    final int updateType = 0;
    PricingThread pricingThread = new PricingThread(null);

    final int id = 180;
    final String symbol = "BTC/USD[Call]Jul31_8000";
    final String name = "BTC/USD[Call]Jul31_8000";
    final Instrument base = null;
    final Instrument quoted = null;
    final short priceScale = 2;
    final short quantityScale = 2;
    final int settleType = 1;
    final AssetType assetType = AssetType.OPTION_CALL;
    final int maintMarginBasisPoints = 2;
    final int requiredMarginBasisPoints = 2;
    final double usdMark = 0;
    final int marginCurveId = 1;
    final long expireTimeMillis = 1596240000000L;
    final int strikePrice = 800000;
    final int underlyerId = 3;
    final long minOrderQuantity = 0;
    final int auctionStartTimeHrGMT = 0;
    final long auctionDurationTime = 0;
    final int auctionFixingAttempts = 0;
    final long auctionFixingWaitTime = 0;
    final double circuitBreakerThreshold = 0;
    final long expireRollTimeMillis = 0;
    final int symbolRollCount = 0;
    final boolean physicalSettle = false;
    final boolean isLimitOnlyMode = false;

    final InstrumentPair pair = new InstrumentPair(id, symbol, name, base, quoted, priceScale, quantityScale, settleType, assetType,
        maintMarginBasisPoints, requiredMarginBasisPoints, usdMark, marginCurveId, expireTimeMillis, strikePrice, underlyerId,
        minOrderQuantity, auctionStartTimeHrGMT, auctionDurationTime, auctionFixingAttempts, auctionFixingWaitTime, circuitBreakerThreshold,
        expireRollTimeMillis, symbolRollCount, physicalSettle, isLimitOnlyMode);
    pair.setOrderBook(new ArrayOrderBook(pair, new MarginPreOrderCheckAndSettle(), OrderBookFactory.DEFAULT_TEST_ORDER_BOOK,
        OrderBookFactory.MARGIN_PREORDER_CHECK));

    final InstrumentPair pair2 = new InstrumentPair(underlyerId, symbol, name, base, quoted, priceScale, quantityScale, settleType,
        assetType, maintMarginBasisPoints, requiredMarginBasisPoints, usdMark, marginCurveId, expireTimeMillis, strikePrice, underlyerId,
        minOrderQuantity, auctionStartTimeHrGMT, auctionDurationTime, auctionFixingAttempts, auctionFixingWaitTime, circuitBreakerThreshold,
        expireRollTimeMillis, symbolRollCount, physicalSettle, isLimitOnlyMode);
    pair2.setIndexFeedUsdMark(9141.03);
    InstrumentCache.addPair(pair2);


    pricingThread.calcOptionPricing(pair, now, updateType);
  }
}
