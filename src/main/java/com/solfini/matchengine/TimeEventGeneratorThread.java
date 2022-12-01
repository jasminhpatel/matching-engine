package com.solfini.matchengine;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import org.agrona.concurrent.IdleStrategy;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.AssetFundingRate;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.message.admin.ExpireContractMessage;
import com.solfini.matchengine.message.admin.FundingRateCalcMessage;
import com.solfini.matchengine.message.admin.TradeStateAdminMessage;
import com.solfini.matchengine.stats.ChartStats;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import uk.co.real_logic.artio.fields.DecimalFloat;

/**
 *
 * @author Chris Mack
 *
 */
public class TimeEventGeneratorThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(TimeEventGeneratorThread.class);
  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  // default 8 hours
  // processed at 04:00, 12:00, and 20:00 UTC
  public static final long FUNDING_RATE_MILLIS_SPAN = StringUtil.toLong(PropertyReader.getProperty("FUNDING_RATE_MILLIS_SPAN", "360000")); // "288000000"
  public static final long FUNDING_RATE_MILLIS_START_OFFSET =
      StringUtil.toLong(PropertyReader.getProperty("FUNDING_RATE_MILLIS_START_OFFSET", "0")); // 144000000

  // public static double FUNDING_RATE_COLLAR = StringUtil.toDouble(PropertyReader.getProperty("FUNDING_RATE_COLLAR", ".00375"));
  public static double FUNDING_RATE_COLLAR = StringUtil.toDouble(PropertyReader.getProperty("FUNDING_RATE_COLLAR", "0"));
  public static double FUNDING_RATE_MIN = StringUtil.toDouble(PropertyReader.getProperty("FUNDING_RATE_MIN", "0")); // ".0000101"
  public static double FUNDING_RATE_INTEREST_RATE = StringUtil.toDouble(PropertyReader.getProperty("FUNDING_RATE_INTEREST_RATE", "0.02"));
  private static boolean USE_TWAP_FUNDING_RATE = TRUE.equalsIgnoreCase(PropertyReader.getProperty("USE_TWAP_FUNDING_RATE", TRUE));
  private static boolean GENERATE_FUNDING_RATE_EVENT =
      TRUE.equalsIgnoreCase(PropertyReader.getProperty("GENERATE_FUNDING_RATE_EVENT", TRUE));

  // default 24 hours, daily for dated futures and options
  public static final long CONTRACT_EXPIRE_MILLIS_SPAN =
      StringUtil.toLong(PropertyReader.getProperty("CONTRACT_EXPIRE_MILLIS_SPAN", "86400000")); // "86400000"
  public static final long CONTRACT_RATE_MILLIS_START_OFFSET =
      StringUtil.toLong(PropertyReader.getProperty("CONTRACT_RATE_MILLIS_START_OFFSET", "86400000")); // 144000000
  public static final long HOUR_23_MIN_59_SEC_55 = 86_395_000;
  public static final long ONE_DAY = 86_400_000;

  private final ManyToOneConcurrentArrayQueueCustom<Message> riskToMatcherQueue;
  private final IdleStrategy idleStrategy;
  private long nextFundingRateTime = 0;
  private long nextContractExpireTime = 0; // daily for dated futures and options


  public TimeEventGeneratorThread(final IdleStrategy idleStrategy) {
    this.riskToMatcherQueue = Context.getRiskToMatcherQueue();
    this.idleStrategy = idleStrategy;

    // calc prev funding time
    final GregorianCalendar calendar = new GregorianCalendar();
    calendar.setTimeZone(GMT);
    calendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
    nextFundingRateTime = calendar.getTimeInMillis() + FUNDING_RATE_MILLIS_START_OFFSET;
    nextContractExpireTime = calendar.getTimeInMillis() + CONTRACT_RATE_MILLIS_START_OFFSET;

    while (nextFundingRateTime <= System.currentTimeMillis()) {
      nextFundingRateTime += FUNDING_RATE_MILLIS_SPAN;
    }

    while (nextContractExpireTime <= System.currentTimeMillis()) {
      nextContractExpireTime += CONTRACT_EXPIRE_MILLIS_SPAN;
    }
    LOGGER.info(LOG_FMT_6, "starting TimeEventGeneratorThread, nextFundingRateTime=", nextFundingRateTime, ", nextContractExpireTime=",
        nextContractExpireTime, ", GENERATE_FUNDING_RATE_EVENT=", GENERATE_FUNDING_RATE_EVENT);
  }


  private final void initPerpetualFunding() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_1, ">> initPerpetualFunding");
    }
    try {
      for (int i = 0; i < InstrumentCache.getPairCapacity(); i++) {
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair == null)
          continue;

        if (pair.getAssetType() == AssetType.PERPETUAL_SWAP) {
          if (pair.getFundingRateTime() == 0 || pair.getFundingRateTime() < System.currentTimeMillis() - 600_000) {
            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_6, "initPerpetualFunding, pairId=", pair.getId(), ", currentTime=", System.currentTimeMillis(),
                  ", nextFundingRateTime=", (double) nextFundingRateTime);
            }
            // pair.setContractExpireTime(nextFundingRateTime);
            pair.setFundingRateTime(nextFundingRateTime);
          }
        } else if (pair.getAssetType() != AssetType.PAIR) { // set test contracts expire time
          if (pair.getSymbol() != null && pair.getSymbol().indexOf("Now") > 0
              && pair.getContractExpireTime() < System.currentTimeMillis()) {

            // for testing set to PhysicalSettle
            pair.setPhysicalSettle(true);

            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_6, "initPerpetualFunding2, pairId=", pair.getId(), ", physicalSettle=", pair.isPhysicalSettle(),
                  ", currentTime=", System.currentTimeMillis(), ", getContractExpireTime=", (double) pair.getContractExpireTime(),
                  ", nextContractExpireTime=", nextContractExpireTime, ", nextContractExpireTime=",
                  StringUtil.getCurrentDateYYYMMDDHHMMSSsss(nextContractExpireTime));
            }

            pair.setContractExpireTime(nextContractExpireTime); // + pair.getExpireRollTimeMillis()
            pair.setFundingRateTime(nextContractExpireTime);

            // send expire message to roll
            final ExpireContractMessage message = new ExpireContractMessage();
            final List<AssetFundingRate> list = message.getAssetExpireList();
            final AssetFundingRate assetFundingRate = new AssetFundingRate();
            assetFundingRate.setAssetId(pair.getId());
            list.add(assetFundingRate);
            if (!list.isEmpty()) {
              riskToMatcherQueue.addGuaranteed(message);
            }
          }
        }

      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public void run() {
    try {
      Thread.sleep(5000);
      while (InstrumentCache.getPairCapacity() < 128) {
        Thread.sleep(100);
      }
      Thread.sleep(5000);
      initPerpetualFunding();
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    while (true) {
      try {
        if ((Mode.PRIMARY != Context.getControllerMode())) {
          Thread.sleep(1000);
          continue;
        }

        if (Context.isAuctionsEnabled())
          processAuctions();

        if ((MarketStatus.OPEN != Context.getMarketStatus())) {
          Thread.sleep(0);
          continue;
        }

        // trigger funding rate
        if (System.currentTimeMillis() >= nextFundingRateTime) {
          nextFundingRateTime += FUNDING_RATE_MILLIS_SPAN;
          if (Context.isPerpetualsEnabled()) {
            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_4, "trigger funding rate, now=", StringUtil.getCurrentDateYYYMMDDHHMMSSsss(System.currentTimeMillis()),
                  ", currentTime=", System.currentTimeMillis(), ", nextFundingRateTime=", nextFundingRateTime,
                  ", FUNDING_RATE_MILLIS_SPAN=", FUNDING_RATE_MILLIS_SPAN);
            }
            buildFundingRate();
          }
        }

        // contract expiration daily for dated futures and options
        if (System.currentTimeMillis() >= nextContractExpireTime) {
          nextContractExpireTime += CONTRACT_EXPIRE_MILLIS_SPAN;
          if (Context.isContractExpiryEnabled()) {
            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_4, "trigger expireContracts, now=", StringUtil.getCurrentDateYYYMMDDHHMMSSsss(System.currentTimeMillis()),
                  "nextContractExpireTime=", StringUtil.getCurrentDateYYYMMDDHHMMSSsss(nextContractExpireTime), ", currentTime=",
                  System.currentTimeMillis(), ", nextContractExpireTime=", nextContractExpireTime, ", CONTRACT_EXPIRE_MILLIS_SPAN=",
                  CONTRACT_EXPIRE_MILLIS_SPAN);
            }
            expireContracts();
          }
        }

        idleStrategy.idle();
        Thread.sleep(1000);
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }

  private final void expireContracts() {
    final long now = System.currentTimeMillis();
    final long lastExpireTime = now - CONTRACT_EXPIRE_MILLIS_SPAN;
    final ExpireContractMessage message = new ExpireContractMessage();
    final List<AssetFundingRate> list = message.getAssetExpireList();
    // LOGGER.info(LOG_FMT_4, "expireContracts0, lastExpireTime=", lastExpireTime, ", now=", now);
    try {
      for (int i = 0; i < InstrumentCache.getPairCapacity(); i++) {
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair == null)
          continue;
        if (pair.getSymbol() == null)
          continue;
        if (pair.getSymbol().indexOf("[DF]") > 0 || pair.getSymbol().indexOf("[C]") > 0 || pair.getSymbol().indexOf("[P]") > 0) {
          final long expireTime = pair.getContractExpireTime();
          if (expireTime > 0 && expireTime <= now) {
            if (expireTime > lastExpireTime) {
              final AssetFundingRate assetFundingRate = new AssetFundingRate();
              assetFundingRate.setAssetId(pair.getId());
              list.add(assetFundingRate);
              LOGGER.info(LOG_FMT_4, "expireContracts1, now=", StringUtil.getCurrentDateYYYMMDDHHMMSSsss(System.currentTimeMillis()),
                  ", symbol=", pair.getSymbol(), " ,assetFundingRate=", assetFundingRate.toString(), ", expireTime=", expireTime,
                  ", lastExpireTime=", lastExpireTime, ", expireTime=", StringUtil.getCurrentDateYYYMMDDHHMMSSsss(expireTime),
                  ", lastExpireTime=", StringUtil.getCurrentDateYYYMMDDHHMMSSsss(lastExpireTime));
            } else {
              LOGGER.warn(LOG_FMT_4, "expireContracts2 stale, now=", StringUtil.getCurrentDateYYYMMDDHHMMSSsss(System.currentTimeMillis()),
                  ", symbol=", pair.getSymbol(), " ,pair.getId()=", pair.getId(), ", expireTime=", expireTime, ", lastExpireTime=",
                  lastExpireTime, ", expireTime=", StringUtil.getCurrentDateYYYMMDDHHMMSSsss(expireTime), ", lastExpireTime=",
                  StringUtil.getCurrentDateYYYMMDDHHMMSSsss(lastExpireTime), ", CONTRACT_EXPIRE_MILLIS_SPAN=", CONTRACT_EXPIRE_MILLIS_SPAN);
              if (pair.getSymbol().indexOf("Now") > 0) {
                pair.setContractExpireTime(lastExpireTime + CONTRACT_EXPIRE_MILLIS_SPAN);
                pair.setFundingRateTime(lastExpireTime + CONTRACT_EXPIRE_MILLIS_SPAN);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    if (!list.isEmpty()) {
      riskToMatcherQueue.addGuaranteed(message);
    }
  }

  private final void buildFundingRate() {
    final long currentFundingRateTime = nextFundingRateTime - FUNDING_RATE_MILLIS_SPAN;
    final FundingRateCalcMessage message = new FundingRateCalcMessage();
    message.setTriggerTimeMillis(currentFundingRateTime);
    final List<AssetFundingRate> list = message.getAssetFundingList();

    try {
      for (int i = 0; i < InstrumentCache.getPairCapacity(); i++) {
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair == null)
          continue;
        if (pair.getAssetType() != AssetType.PERPETUAL_SWAP)
          continue;

        final AssetFundingRate assetFundingRate = new AssetFundingRate();

        double fundingRate = 0;
        if (!GENERATE_FUNDING_RATE_EVENT) {
          fundingRate = pair.getExternalFundingRate();
          LOGGER.info("using externalFundingRate for pair" + i + ", fundingRate=" + fundingRate);
        } else if (USE_TWAP_FUNDING_RATE && pair.getSymbol() != null && pair.getSymbol().indexOf("BTC/USD") == 0)
          fundingRate = calcFundingRateUsingTWAPDiff(pair, assetFundingRate);
        else
          fundingRate = calcFundingRate(pair, assetFundingRate);

        // collar
        if (fundingRate > FUNDING_RATE_COLLAR && FUNDING_RATE_COLLAR != 0) // upper collar
          fundingRate = FUNDING_RATE_COLLAR;
        else if (fundingRate < -FUNDING_RATE_COLLAR && FUNDING_RATE_COLLAR != 0) // lower collar
          fundingRate = -FUNDING_RATE_COLLAR;
        else if (Math.abs(fundingRate) < FUNDING_RATE_MIN) // minimum funding rate 0.400000
          fundingRate = 0;

        assetFundingRate.setAssetId(pair.getId());
        assetFundingRate.setRate(new DecimalFloat((long) (fundingRate * 100000000), 8));
        // markInSettleCoin

        list.add(assetFundingRate);

        pair.setEstFundingRate(fundingRate);
        pair.setFundingRateTime(nextFundingRateTime);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    if (!list.isEmpty()) {
      riskToMatcherQueue.addGuaranteed(message);
    }
    // start separate thread to persist the funding rate
    new Thread() {
      @Override
      public void run() {
        message.onPersist();
      }
    }.start();
  }

  private final double calcFundingRate(final InstrumentPair pair, final AssetFundingRate assetFundingRate) {
    final ChartStats chartStats = pair.getTradeHistory().getRolling5minsStats();
    final double vwap = StringUtil.toDouble(chartStats.getVWAP(), pair.getPriceScale());
    final double last = vwap > 0 ? vwap : pair.getOrderBook().getUsdMark();
    final double usdMark = pair.getIndexFeedUsdMark();
    final double timePeriodInterest = FUNDING_RATE_INTEREST_RATE / (365 * 3); // 0.001826484% = 0.00001826484

    // can be positive or negative
    double premium = 0;
    if (usdMark > 0) {
      premium = (usdMark - last) / usdMark;
    }
    double fundingRate = premium + timePeriodInterest;

    LOGGER.info(LOG_FMT_8, "buildFundingRate, calcFundingRate=", premium, ", pair", pair, ", vwap=", vwap, ", last=", last,
        ", timePeriodInterest=", timePeriodInterest, ", fundingRate=", fundingRate);

    assetFundingRate.setVwap(vwap);
    assetFundingRate.setLast(last);
    assetFundingRate.setUsdMark(usdMark);
    assetFundingRate.setTimePeriodInterest(timePeriodInterest);
    return fundingRate;
  }

  private final double calcFundingRateUsingTWAPDiff(final InstrumentPair pair, final AssetFundingRate assetFundingRate) {
    try {
      final int perpTWAP = pair.getTradeHistory().getRolling8HrTWAP();
      final String spotSymbol = pair.getSymbol().split("/")[0] + "/USDC";
      final InstrumentPair spotPair = InstrumentCache.getPairBySymbol(spotSymbol);
      if (spotPair == null) {
        LOGGER.warn(LOG_FMT_8, "calcFundingRateUsingTWAPDiff, getRolling8HrTWAP spotPair=", spotPair, ", spotSymbol=", spotSymbol);
        return 0;
      }

      final int spotTWAP = spotPair.getTradeHistory().getRolling8HrTWAP();

      final double usdMark = StringUtil.toDouble(spotTWAP, spotPair.getPriceScale());
      if (usdMark == 0) {
        LOGGER.warn(LOG_FMT_6, "calcFundingRateUsingTWAPDiff, getRolling8HrTWAP usdMark=", usdMark, ", spotPair=", spotPair.getId(),
            ", spotPair.getPriceScale()=", spotPair.getPriceScale());
      }

      final double last = StringUtil.toDouble(perpTWAP, pair.getPriceScale());

      // can be positive or negative
      double premium = 0;
      if (usdMark > 0) {
        premium = (usdMark - last) / usdMark;
      }
      double fundingRate = premium;

      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_8, "calcFundingRateUsingTWAPDiff, calcFundingRate=", premium, ", usdMark=", usdMark, ", last=", last,
            ", fundingRate=", fundingRate, ", spotTWAP=", spotTWAP, ", perpTWAP=", perpTWAP, ", pair=", pair, ", spotPair=", spotPair);
      }

      assetFundingRate.setVwap(usdMark);
      assetFundingRate.setLast(last);
      assetFundingRate.setUsdMark(usdMark);
      assetFundingRate.setTimePeriodInterest(0);
      return fundingRate;
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return 0;
  }

  public final void processAuctions() {
    final long now = System.currentTimeMillis();

    try {
      Thread.sleep(2000); // TODO: remove this later

      for (int i = 0; i < InstrumentCache.getPairCapacity(); i++) {
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair == null || pair.getAuctionDurationTime() <= 0)
          continue;

        final MarketStatus currentMarketStatus = pair.getOrderBook().getMarketStatus();
        if (MarketStatus.OPEN_AUCTION == currentMarketStatus // auction already open
            && now - pair.getAuctionLastStartedTime() >= pair.getAuctionDurationTime()) { // auction lasted duration
          LOGGER.info(LOG_FMT_1, "closing auction ", pair, ", duration=", pair.getAuctionDurationTime(), ", lastStarted=",
              pair.getAuctionLastStartedTime(), ", lastStopped=", pair.getAuctionLastStoppedTime(), ", FixingWaitTime=",
              pair.getAuctionFixingWaitTime());
          pair.setAuctionLastStoppedTime(now);
          pair.setAuctionFixingAttempts(0);
          final TradeStateAdminMessage tradeStateAdminMessage = new TradeStateAdminMessage(MarketStatus.CLOSE_AUCTION, pair.getId());
          riskToMatcherQueue.addGuaranteed(tradeStateAdminMessage);
          continue;
        } else if (MarketStatus.CLOSE_AUCTION == currentMarketStatus) { // auction in closed/fixing state
          if (now - pair.getAuctionLastStoppedTime() >= pair.getAuctionFixingWaitTime()) { // auction lasted duration
            pair.setAuctionLastStoppedTime(now);
            pair.setAuctionFixingAttempts(pair.getAuctionFixingAttempts() + 1);
            if (pair.getAuctionFixingAttempts() > Context.getAuctionFixMaxAttempts()) { // too many attempts
              LOGGER.info(LOG_FMT_1, "cancel auction ", pair, ", duration=", pair.getAuctionDurationTime(), ", lastStarted=",
                  pair.getAuctionLastStartedTime(), ", lastStopped=", pair.getAuctionLastStoppedTime(), ", FixingWaitTime=",
                  pair.getAuctionFixingWaitTime());
              final TradeStateAdminMessage tradeStateAdminMessage = new TradeStateAdminMessage(MarketStatus.CANCEL_AUCTION, pair.getId());
              pair.setFundingRateTime(calcNextAuctionStartTime(pair));
              riskToMatcherQueue.addGuaranteed(tradeStateAdminMessage);
            } else { // retry fixing
              LOGGER.info(LOG_FMT_1, "retry closing auction ", pair, ", duration=", pair.getAuctionDurationTime(), ", lastStarted=",
                  pair.getAuctionLastStartedTime(), ", lastStopped=", pair.getAuctionLastStoppedTime(), ", FixingWaitTime=",
                  pair.getAuctionFixingWaitTime());
              final TradeStateAdminMessage tradeStateAdminMessage = new TradeStateAdminMessage(MarketStatus.CLOSE_AUCTION, pair.getId());
              pair.setFundingRateTime(calcNextAuctionStartTime(pair));
              riskToMatcherQueue.addGuaranteed(tradeStateAdminMessage);
            }
          }
          continue;
        }

        if (pair.getAuctionLastStartedTime() > 0 && now - pair.getAuctionLastStartedTime() < HOUR_23_MIN_59_SEC_55) // filter for efficiency
          continue;

        final Calendar calendar = Calendar.getInstance(GMT);
        calendar.set(Calendar.HOUR_OF_DAY, pair.getAuctionStartTimeHrGMT());
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        final long startTime = calendar.getTimeInMillis();

        if (now < startTime)
          continue;

        LOGGER.info(LOG_FMT_8, "starting auction ", pair, ", startTime", startTime, ", now", now, ", duration=",
            pair.getAuctionDurationTime(), ", lastStarted=", pair.getAuctionLastStartedTime(), ", lastStopped=",
            pair.getAuctionLastStoppedTime(), ", FixingWaitTime=", pair.getAuctionFixingWaitTime());

        pair.setAuctionLastStartedTime(startTime);
        final TradeStateAdminMessage tradeStateAdminMessage = new TradeStateAdminMessage(MarketStatus.OPEN_AUCTION, pair.getId());
        riskToMatcherQueue.addGuaranteed(tradeStateAdminMessage);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  private long calcNextAuctionStartTime(final InstrumentPair pair) {
    final Calendar calendar = Calendar.getInstance(GMT);
    calendar.set(Calendar.HOUR_OF_DAY, pair.getAuctionStartTimeHrGMT());
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);

    long startTime = calendar.getTimeInMillis();
    if (startTime < System.currentTimeMillis())
      startTime = startTime + ONE_DAY;
    return startTime;
  }

  public static void main(String[] args) {
    String s = "BTC/USD[A]Apr24_5000";
    int i = s.indexOf("[A]");
    System.out.println("i=" + i);
  }
}
