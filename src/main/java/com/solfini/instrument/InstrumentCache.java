package com.solfini.instrument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.MarketType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.sbe.encoder.BusinessRejectReason;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.util.LogLevel;
import com.solfini.util.PropertyReader;
import com.solfini.util.TickerTrie;

/**
 *
 * @author Chris Mack
 *
 */
public class InstrumentCache implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(InstrumentCache.class);
  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  private static final int INITIAL_SIZE = 64;
  private static final int INCREASE_SIZE = 2;
  private static int maxSecurityId = 0;
  private static final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
  private static final TickerTrie<Instrument> symbolToInstrument = new TickerTrie<>();
  private static Instrument[] instrumentArr = new Instrument[INITIAL_SIZE];
  private static final TickerTrie<InstrumentPair> symbolToInstrumentPair = new TickerTrie<>();
  private static final List<InstrumentPair> instrumentPairList = new ArrayList<>();
  private static InstrumentPair[] instrumentPairArr = new InstrumentPair[INITIAL_SIZE];
  private static Instrument[] altCollateralInstrumentArr = new Instrument[0];
  private static int minSpotPairIndex = 0;
  private static int maxSpotPairIndex = 0;

  private InstrumentCache() {}

  public static final int getInstrumentCapacity() {
    return instrumentArr.length;
  }

  public static final int getPairCapacity() {
    return instrumentPairArr.length;
  }

  public static final void addInstrument(final Instrument instrument) {
    if (instrument.getId() >= instrumentArr.length) {
      final int newSize = instrument.getId() + INCREASE_SIZE;
      instrumentArr = Arrays.copyOf(instrumentArr, newSize);
      instrumentPairArr = Arrays.copyOf(instrumentPairArr, newSize);
    }

    if (instrument.getId() > maxSecurityId)
      maxSecurityId = instrument.getId();


    instrumentArr[instrument.getId()] = instrument;
    final String symbol = instrument.getSymbol() == null ? null : instrument.getSymbol().trim();
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info("add symbol=" + symbol + ", instrument=" + instrument);
    }
    symbolToInstrument.add(instrument.getSymbol(), instrument);

    if ("BTC".equals(instrument.getSymbol()) || "ETH".equals(instrument.getSymbol()) || "BNB".equals(instrument.getSymbol())
        || "USDT".equals(instrument.getSymbol()) || "DAI".equals(instrument.getSymbol()) || "USD".equals(instrument.getSymbol())) {
      Instrument[] temp = Arrays.copyOf(altCollateralInstrumentArr, altCollateralInstrumentArr.length + 1);
      temp[temp.length - 1] = instrument;
      altCollateralInstrumentArr = temp;
    }
  }

  public static final Instrument get(final int id) {
    if (id >= instrumentArr.length) {
      return null;
    }

    return instrumentArr[id];
  }

  public static final Instrument getBySymbol(final String symbol) {
    return symbolToInstrument.get(symbol);
  }

  public static final void addPair(final InstrumentPair instrumentPair) {
    if (instrumentPair.getId() >= instrumentPairArr.length) {
      final int newSize = instrumentPair.getId() + INCREASE_SIZE;
      instrumentArr = Arrays.copyOf(instrumentArr, newSize);
      instrumentPairArr = Arrays.copyOf(instrumentPairArr, newSize);
    }

    if (instrumentPair.getId() > maxSecurityId)
      maxSecurityId = instrumentPair.getId();


    instrumentPairArr[instrumentPair.getId()] = instrumentPair;
    symbolToInstrumentPair.add(instrumentPair.getSymbol(), instrumentPair);
    instrumentPairList.add(instrumentPair);

    if (AssetType.PAIR == instrumentPair.getAssetType()) {
      if (minSpotPairIndex <= 0 || instrumentPair.getId() < minSpotPairIndex)
        minSpotPairIndex = instrumentPair.getId();
      if (instrumentPair.getId() > maxSpotPairIndex)
        maxSpotPairIndex = instrumentPair.getId();
    }


    if (instrumentPair.getSymbol() != null && instrumentPair.getSymbol().indexOf("[A]") > 0)
      setupAuction(instrumentPair);
  }

  public static final void setupAuction(final InstrumentPair instrumentPair) {
    if (instrumentPair.getAuctionDurationTime() == 0)
      instrumentPair.setAuctionDurationTime(36_000_000); // default to 36_000_000
    if (instrumentPair.getAuctionStartTimeHrGMT() == 0)
      instrumentPair.setAuctionStartTimeHrGMT(2); // default to 2
    if (instrumentPair.getAuctionFixingAttempts() == 0)
      instrumentPair.setAuctionFixingAttempts(3); // default to 3
    if (instrumentPair.getAuctionFixingWaitTime() == 0)
      instrumentPair.setAuctionFixingWaitTime(60_000); // default to 60_000

    instrumentPair.setFundingRateTime(calcNextAuctionStartTime(instrumentPair));
  }

  private static final long calcNextAuctionStartTime(final InstrumentPair pair) {
    final Calendar calendar = Calendar.getInstance(GMT);
    calendar.set(Calendar.HOUR_OF_DAY, pair.getAuctionStartTimeHrGMT());
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    long startTime = calendar.getTimeInMillis();
    if (startTime < System.currentTimeMillis())
      startTime = startTime + ONE_DAY;
    return startTime;
  }


  public static final void resizePairCache(final int size) {
    if (size >= instrumentPairArr.length) {
      instrumentPairArr = Arrays.copyOf(instrumentPairArr, size + INCREASE_SIZE); // resize
      instrumentArr = Arrays.copyOf(instrumentArr, size + INCREASE_SIZE); // resize
    }
  }

  public static final void resizeInstrumentCache(final int size) {
    if (size >= instrumentArr.length) {
      instrumentPairArr = Arrays.copyOf(instrumentPairArr, size + INCREASE_SIZE); // resize
      instrumentArr = Arrays.copyOf(instrumentArr, size + INCREASE_SIZE); // resize
    }
  }


  public static final InstrumentPair getPair(final int id) {
    if (id >= instrumentPairArr.length) {
      return null;
    }

    return instrumentPairArr[id];
  }

  public static final InstrumentPair getPairBySymbol(final String symbol) {
    return symbolToInstrumentPair.get(symbol);
  }

  public static final void updatePairSymbol(final String symbol, final InstrumentPair pair) {
    symbolToInstrumentPair.add(symbol, pair);
  }

  public static final Iterator<InstrumentPair> getPairIterator() {
    return instrumentPairList.iterator();
  }

  public static final void setFee(final FeeAdminMessage feeAdminMessage) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("setFee: PUT feeAdminMessage={}", feeAdminMessage);
    }

    final InstrumentPair instrumentPair = getPair(feeAdminMessage.getAssetId());
    if (instrumentPair == null) {
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(feeAdminMessage.getSenderCompId(),
          MsgType.SECURITY_STATUS, Long.toString(feeAdminMessage.getAssetId()), BusinessRejectReason.INSTRUMENT_NOT_FOUND,
          INSTRUMENT_NOT_FOUND, 0, feeAdminMessage.getSourceSeqNum(), 0, feeAdminMessage.getAssetId()));
    } else {
      final Fee fee = new Fee(feeAdminMessage);
      instrumentPair.setFee(fee);
      matcherToPublisherQueue.addGuaranteed(feeAdminMessage);
    }
  }

  // called from matching thread
  private static final void updateSecurityDefinitionPUT(final SecurityDefinitionAdminMessage securityDefinitionAdminMessage) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("updateSecurityDefinition: PUT securityDefinitionAdminMessage={}", securityDefinitionAdminMessage);
    }
    if (AssetType.ASSET == securityDefinitionAdminMessage.getAssetType()) {
      final Instrument instrument = new Instrument(securityDefinitionAdminMessage.getSecurityId(),
          securityDefinitionAdminMessage.getSymbol(), securityDefinitionAdminMessage.getName(),
          (short) securityDefinitionAdminMessage.getPriceScale(), (short) securityDefinitionAdminMessage.getQuantityScale(), 0,
          securityDefinitionAdminMessage.getCollateralMarginPercentDiscount());
      if (securityDefinitionAdminMessage.getIndexFeedUsdMark() > 0)
        instrument.setIndexFeedUsdMark(securityDefinitionAdminMessage.getIndexFeedUsdMark());
      InstrumentCache.addInstrument(instrument);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("updateSecurityDefinition: added instrument={}, InstrumentCache.get={}", instrument,
            InstrumentCache.get(instrument.getId()));
      }
    } else {
      final String symbol = securityDefinitionAdminMessage.getSymbol();
      final String name = securityDefinitionAdminMessage.getName();
      final int priceScale = securityDefinitionAdminMessage.getPriceScale();
      final int quantityScale = securityDefinitionAdminMessage.getQuantityScale();
      final int orderBookStrategy = securityDefinitionAdminMessage.getOrderBookStrategy();
      final int preOrderCheckStrategy = securityDefinitionAdminMessage.getPreOrderCheckStrategy();
      final int settleType = securityDefinitionAdminMessage.getSettleType();
      final AssetType assetType = securityDefinitionAdminMessage.getAssetType();
      final MarketType marketType = securityDefinitionAdminMessage.getMarketType();
      final int maintMarginPercent = securityDefinitionAdminMessage.getMaintMarginBasisPoints();
      final int requiredMarginPercent = securityDefinitionAdminMessage.getRequiredMarginBasisPoints();
      final int marginCurveId = securityDefinitionAdminMessage.getMarginCurveId();
      final long expireTimeMillis = securityDefinitionAdminMessage.getExpireTimeMillis();
      final int strikePrice = securityDefinitionAdminMessage.getStrikePrice();
      final int underlyerId = securityDefinitionAdminMessage.getUnderlyerId();

      final double usdStrikePrice = securityDefinitionAdminMessage.getUsdStrikePrice(); // for options, calculated from strikePrice
      final double usdUnderlyerPrice = securityDefinitionAdminMessage.getUsdUnderlyerPrice(); // stock price
      final double usdModelPrice = securityDefinitionAdminMessage.getUsdModelPrice(); // model option price
      final double interestRate = securityDefinitionAdminMessage.getInterestRate(); // interestRate used for option calc
      final double timeToExpire = securityDefinitionAdminMessage.getTimeToExpire(); // option time to expire, in annual format of
                                                                                    // contractExpireTime
      final long expireRollTimeMillis = securityDefinitionAdminMessage.getExpireRollTimeMillis();
      final int symbolRollCount = securityDefinitionAdminMessage.getSymbolRollCount();
      final double dividend = securityDefinitionAdminMessage.getDividend(); // dividend for option calc
      final double delta = securityDefinitionAdminMessage.getDelta();
      final double theta = securityDefinitionAdminMessage.getTheta();
      final double rho = securityDefinitionAdminMessage.getRho();
      final double normalCDF = securityDefinitionAdminMessage.getNormalCDF();
      final double gamma = securityDefinitionAdminMessage.getGamma();
      final double vega = securityDefinitionAdminMessage.getVega();
      final double sigma = securityDefinitionAdminMessage.getSigma();
      final double circuitBreakerThreshold = securityDefinitionAdminMessage.getCircuitBreakerThreshold();

      final long minOrderQuantity = securityDefinitionAdminMessage.getMinQty();
      final int auctionStartTimeHrGMT = securityDefinitionAdminMessage.auctionStartTimeHrGMT;
      final long auctionDurationTime = securityDefinitionAdminMessage.auctionDurationTime;
      final int auctionFixingAttempts = securityDefinitionAdminMessage.auctionFixingAttempts;
      final long auctionFixingWaitTime = securityDefinitionAdminMessage.auctionFixingWaitTime;
      final boolean physicalSettle = securityDefinitionAdminMessage.isPhysicalSettle();
      final boolean isLimitOnlyMode = securityDefinitionAdminMessage.isLimitOnlyMode();

      final Instrument base = InstrumentCache.get(securityDefinitionAdminMessage.getBaseId());
      final Instrument quoted = InstrumentCache.get(securityDefinitionAdminMessage.getQuotedId());
      if (base == null || quoted == null) {
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(securityDefinitionAdminMessage.getSenderCompId(),
            MsgType.SECURITY_STATUS, Long.toString(securityDefinitionAdminMessage.getSecurityId()),
            BusinessRejectReason.BASE_OR_QUOTED_INSTRUMENT_NOT_FOUND, BASE_OR_QUOTED_INSTRUMENT_NOT_FOUND, 0,
            securityDefinitionAdminMessage.getSourceSeqNum(), 0, securityDefinitionAdminMessage.getSecurityId()));
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("updateSecurityDefinition: pair error base={}, quoted={}, message={}", base, quoted, securityDefinitionAdminMessage);
        }
        return;
      }
      int arrSize = securityDefinitionAdminMessage.getArrSize();
      final int cacheDepth = securityDefinitionAdminMessage.getCacheDepth();


      // check if we need to rebuild the orderbook or just update the fields
      final InstrumentPair currentPair = getPair(securityDefinitionAdminMessage.getSecurityId());
      if (null != currentPair) {
        final OrderBook orderBook = currentPair.getOrderBook();
        if (priceScale == currentPair.getPriceScale() && quantityScale == currentPair.getQuantityScale()
            && assetType == currentPair.getAssetType() && orderBook != null && arrSize == orderBook.getArrSize()
            && securityDefinitionAdminMessage.getBaseId() == currentPair.getBaseId()
            && securityDefinitionAdminMessage.getQuotedId() == currentPair.getQuotedId()
            && !securityDefinitionAdminMessage.isSnapConverterMode()) {
          currentPair.setMarginCurveId(marginCurveId);
          currentPair.setMaintMarginBasisPoints(maintMarginPercent);
          currentPair.setRequiredMarginBasisPoints(requiredMarginPercent);
          currentPair.setSymbol(symbol);
          currentPair.setName(name);
          currentPair.setContractExpireTime(expireTimeMillis);
          currentPair.setExpireRollTimeMillis(expireRollTimeMillis);
          currentPair.setSymbolRollCount(symbolRollCount);
          currentPair.setCircuitBreakerThreshold(circuitBreakerThreshold);
          currentPair.setAuctionStartTimeHrGMT(auctionStartTimeHrGMT);
          currentPair.setAuctionDurationTime(auctionDurationTime);
          currentPair.setAuctionFixingAttempts(auctionFixingAttempts);
          currentPair.setAuctionFixingWaitTime(auctionFixingWaitTime);
          currentPair.setStrikePrice(strikePrice);
          currentPair.setInterestRate(interestRate);
          currentPair.setDividend(dividend);
          currentPair.setDelta(delta);
          currentPair.setUnderlyerId(underlyerId);
          currentPair.setMinOrderQuantity(minOrderQuantity);
          currentPair.setPhysicalSettle(physicalSettle);
          currentPair.setLimitOnlyMode(isLimitOnlyMode);
          return;
        }
      }


      final InstrumentPair instrumentPair = new InstrumentPair(securityDefinitionAdminMessage.getSecurityId(),
          securityDefinitionAdminMessage.getSymbol(), securityDefinitionAdminMessage.getName(), base, quoted, (short) priceScale,
          (short) quantityScale, settleType, assetType, maintMarginPercent, requiredMarginPercent, 0, marginCurveId, expireTimeMillis,
          strikePrice, underlyerId, minOrderQuantity, auctionStartTimeHrGMT, auctionDurationTime, auctionFixingAttempts,
          auctionFixingWaitTime, circuitBreakerThreshold, expireRollTimeMillis, symbolRollCount, physicalSettle, isLimitOnlyMode, marketType);
      if (securityDefinitionAdminMessage.getIndexFeedUsdMark() > 0)
        instrumentPair.setIndexFeedUsdMark(securityDefinitionAdminMessage.getIndexFeedUsdMark());



      // Set the array size to 0 on snap converter mode
      if (securityDefinitionAdminMessage.isSnapConverterMode()) {
        arrSize = 0;
      }

      instrumentPair.setOrderBook(OrderBookFactory.create(orderBookStrategy, preOrderCheckStrategy, instrumentPair, arrSize, cacheDepth));
      InstrumentCache.addPair(instrumentPair);

      NewOrderSingleHandler.setSecondaryOrderIdIfGreater(instrumentPair.getId(), securityDefinitionAdminMessage.getSecondaryOrderId());
      instrumentPair.getOrderBook().setSecondaryOrderIdIfGreater(securityDefinitionAdminMessage.getSecondaryOrderId());
      instrumentPair.getOrderBook().setFilledCountIfGreater(securityDefinitionAdminMessage.getSecondaryExecId());

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("updateSecurityDefinition: added pair={}, InstrumentCache.get={}", instrumentPair,
            InstrumentCache.getPair(instrumentPair.getId()));
      }
    }
  }

  // called from matching thread
  private static final void updateSecurityDefinitionPATCH(final SecurityDefinitionAdminMessage securityDefinitionAdminMessage) {
    if (AssetType.ASSET == securityDefinitionAdminMessage.getAssetType()) {
      final Instrument instrument = InstrumentCache.get(securityDefinitionAdminMessage.getSecurityId());
      if (instrument == null) {
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(securityDefinitionAdminMessage.getSenderCompId(),
            MsgType.SECURITY_STATUS, Long.toString(securityDefinitionAdminMessage.getSecurityId()),
            BusinessRejectReason.INSTRUMENT_NOT_FOUND, INSTRUMENT_NOT_FOUND, 0, securityDefinitionAdminMessage.getSourceSeqNum(), 0,
            securityDefinitionAdminMessage.getSecurityId()));
        return;
      }

      instrumentArr[instrument.getId()] = instrument;
      symbolToInstrument.add(instrument.getSymbol(), instrument);

    } else {
      final String symbol = securityDefinitionAdminMessage.getSymbol();
      final String name = securityDefinitionAdminMessage.getName();
      final int priceScale = securityDefinitionAdminMessage.getPriceScale();
      final int quantityScale = securityDefinitionAdminMessage.getQuantityScale();
      final int orderBookStrategy = securityDefinitionAdminMessage.getOrderBookStrategy();
      final int preOrderCheckStrategy = securityDefinitionAdminMessage.getPreOrderCheckStrategy();
      final int marginCurveId = securityDefinitionAdminMessage.getMarginCurveId();

      final int settleType = securityDefinitionAdminMessage.getSettleType();
      final AssetType assetType = securityDefinitionAdminMessage.getAssetType();
      final MarketType marketType = securityDefinitionAdminMessage.getMarketType();
      final int maintMarginPercent = securityDefinitionAdminMessage.getMaintMarginBasisPoints();
      final int requiredMarginPercent = securityDefinitionAdminMessage.getRequiredMarginBasisPoints();
      final int arrSize = securityDefinitionAdminMessage.getArrSize();
      final int cacheDepth = securityDefinitionAdminMessage.getCacheDepth();

      final long expireTimeMillis = securityDefinitionAdminMessage.getExpireTimeMillis();
      final long expireRollTimeMillis = securityDefinitionAdminMessage.getExpireRollTimeMillis();
      final int symbolRollCount = securityDefinitionAdminMessage.getSymbolRollCount();
      final boolean physicalSettle = securityDefinitionAdminMessage.isPhysicalSettle();
      final boolean isLimitOnlyMode = securityDefinitionAdminMessage.isLimitOnlyMode();

      final int strikePrice = securityDefinitionAdminMessage.getStrikePrice();
      final int underlyerId = securityDefinitionAdminMessage.getUnderlyerId();

      final long minOrderQuantity = securityDefinitionAdminMessage.getMinQty();
      final int auctionStartTimeHrGMT = securityDefinitionAdminMessage.getAuctionStartTimeHrGMT();
      final long auctionDurationTime = securityDefinitionAdminMessage.getAuctionDurationTime();
      final int auctionFixingAttempts = securityDefinitionAdminMessage.getAuctionFixingAttempts();
      final long auctionFixingWaitTime = securityDefinitionAdminMessage.getAuctionFixingWaitTime();
      final double circuitBreakerThreshold = securityDefinitionAdminMessage.getCircuitBreakerThreshold();

      final double usdStrikePrice = securityDefinitionAdminMessage.getUsdStrikePrice(); // for options, calculated from strikePrice
      final double usdUnderlyerPrice = securityDefinitionAdminMessage.getUsdUnderlyerPrice(); // stock price
      final double usdModelPrice = securityDefinitionAdminMessage.getUsdModelPrice(); // model option price
      final double interestRate = securityDefinitionAdminMessage.getInterestRate(); // interestRate used for option calc
      final double timeToExpire = securityDefinitionAdminMessage.getTimeToExpire(); // option time to expire, in annual format of
                                                                                    // contractExpireTime
      final double dividend = securityDefinitionAdminMessage.getDividend(); // dividend for option calc
      final double delta = securityDefinitionAdminMessage.getDelta();
      final double theta = securityDefinitionAdminMessage.getTheta();
      final double rho = securityDefinitionAdminMessage.getRho();
      final double normalCDF = securityDefinitionAdminMessage.getNormalCDF();
      final double gamma = securityDefinitionAdminMessage.getGamma();
      final double vega = securityDefinitionAdminMessage.getVega();
      final double sigma = securityDefinitionAdminMessage.getSigma();

      final Instrument base = InstrumentCache.get(securityDefinitionAdminMessage.getBaseId());
      final Instrument quoted = InstrumentCache.get(securityDefinitionAdminMessage.getQuotedId());
      if (base == null || quoted == null) {
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(securityDefinitionAdminMessage.getSenderCompId(),
            MsgType.SECURITY_STATUS, Long.toString(securityDefinitionAdminMessage.getSecurityId()),
            BusinessRejectReason.BASE_OR_QUOTED_INSTRUMENT_NOT_FOUND, BASE_OR_QUOTED_INSTRUMENT_NOT_FOUND, 0,
            securityDefinitionAdminMessage.getSourceSeqNum(), 0, securityDefinitionAdminMessage.getSecurityId()));
        return;
      }

      // we are overwriting the existing pair and orderbook here, do we need to do something to the existing orderbook?
      if (LogLevel.warn()) {
        LOGGER.warn(LOG_FMT_2, "Calling recreateReplace on orderbook pair, message=", securityDefinitionAdminMessage);
      }

      // check if we need to rebuild the orderbook or just update the fields
      final InstrumentPair currentPair = getPair(securityDefinitionAdminMessage.getSecurityId());
      if (null != currentPair) {
        final OrderBook orderBook = currentPair.getOrderBook();
        if (priceScale == currentPair.getPriceScale() && quantityScale == currentPair.getQuantityScale()
            && assetType == currentPair.getAssetType() && orderBook != null && arrSize == orderBook.getArrSize()
            && securityDefinitionAdminMessage.getBaseId() == currentPair.getBaseId()
            && securityDefinitionAdminMessage.getQuotedId() == currentPair.getQuotedId()
            && !securityDefinitionAdminMessage.isSnapConverterMode()) {
          currentPair.setMarginCurveId(marginCurveId);
          currentPair.setMaintMarginBasisPoints(maintMarginPercent);
          currentPair.setRequiredMarginBasisPoints(requiredMarginPercent);
          currentPair.setSymbol(symbol);
          currentPair.setName(name);
          currentPair.setContractExpireTime(expireTimeMillis);
          currentPair.setExpireRollTimeMillis(expireRollTimeMillis);
          currentPair.setSymbolRollCount(symbolRollCount);
          currentPair.setCircuitBreakerThreshold(circuitBreakerThreshold);
          currentPair.setAuctionStartTimeHrGMT(auctionStartTimeHrGMT);
          currentPair.setAuctionDurationTime(auctionDurationTime);
          currentPair.setAuctionFixingAttempts(auctionFixingAttempts);
          currentPair.setAuctionFixingWaitTime(auctionFixingWaitTime);
          currentPair.setStrikePrice(strikePrice);
          currentPair.setInterestRate(interestRate);
          currentPair.setDividend(dividend);
          currentPair.setDelta(delta);
          currentPair.setUnderlyerId(underlyerId);
          currentPair.setMinOrderQuantity(minOrderQuantity);
          currentPair.setPhysicalSettle(physicalSettle);
          currentPair.setLimitOnlyMode(isLimitOnlyMode);
          return;
        }
      }

      if (null != currentPair) {
        instrumentPairList.remove(currentPair);
      }

      final InstrumentPair instrumentPair = new InstrumentPair(securityDefinitionAdminMessage.getSecurityId(),
          securityDefinitionAdminMessage.getSymbol(), securityDefinitionAdminMessage.getName(), base, quoted, (short) priceScale,
          (short) quantityScale, settleType, assetType, maintMarginPercent, requiredMarginPercent, 0, marginCurveId, expireTimeMillis,
          strikePrice, underlyerId, minOrderQuantity, auctionStartTimeHrGMT, auctionDurationTime, auctionFixingAttempts,
          auctionFixingWaitTime, circuitBreakerThreshold, expireRollTimeMillis, symbolRollCount, physicalSettle, isLimitOnlyMode, marketType);

      OrderBookFactory.recreateReplace(orderBookStrategy, preOrderCheckStrategy, currentPair, instrumentPair, arrSize, cacheDepth);

      instrumentPairArr[instrumentPair.getId()] = instrumentPair;
      symbolToInstrumentPair.add(instrumentPair.getSymbol(), instrumentPair);
      instrumentPairList.add(instrumentPair);
    }
  }

  // called from matching thread
  private static final void updateSecurityDefinitionDELETE(final SecurityDefinitionAdminMessage securityDefinitionAdminMessage) {
    if (AssetType.ASSET == securityDefinitionAdminMessage.getAssetType()) {
      final Instrument instrument = InstrumentCache.get(securityDefinitionAdminMessage.getSecurityId());
      if (instrument == null) {
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(securityDefinitionAdminMessage.getSenderCompId(),
            MsgType.SECURITY_STATUS, Long.toString(securityDefinitionAdminMessage.getSecurityId()),
            BusinessRejectReason.INSTRUMENT_NOT_FOUND, INSTRUMENT_NOT_FOUND, 0, securityDefinitionAdminMessage.getSourceSeqNum(), 0,
            securityDefinitionAdminMessage.getSecurityId()));
        return;
      }

      instrumentArr[instrument.getId()] = null;
      symbolToInstrument.add(instrument.getSymbol(), null);

    } else {
      // we are overwriting the existing pair and orderbook here, do we need to do something to the existing orderbook?
      instrumentPairList.remove(getPair(securityDefinitionAdminMessage.getSecurityId()));

      instrumentPairArr[securityDefinitionAdminMessage.getSecurityId()] = null;
      symbolToInstrumentPair.add(securityDefinitionAdminMessage.getSymbol(), null);
    }
  }

  // called from matching thread
  public static final void updateSecurityDefinition(final SecurityDefinitionAdminMessage securityDefinitionAdminMessage) {
    if (securityDefinitionAdminMessage.getSecurityId() == 0) { // SecurityId
      int securityId = ++maxSecurityId;
      securityDefinitionAdminMessage.setSecurityId(securityId);
    } else if (securityDefinitionAdminMessage.getSecurityId() > maxSecurityId) // maxSecurityId
      maxSecurityId = securityDefinitionAdminMessage.getSecurityId();

    switch (securityDefinitionAdminMessage.getUpdateType()) {
      case PUT:
        updateSecurityDefinitionPUT(securityDefinitionAdminMessage);
        break;
      case PATCH:
        updateSecurityDefinitionPATCH(securityDefinitionAdminMessage);
        break;
      case DELETE:
        updateSecurityDefinitionDELETE(securityDefinitionAdminMessage);
        break;
      default:
    }

    matcherToPublisherQueue.addGuaranteed(securityDefinitionAdminMessage);
  }

  // restate all instruments and pairs, and fees
  // must be called from matching thread
  public static final void restateAllInstrumentsPairsAndFees(final long snapId) {
    for (int i = 0; i < InstrumentCache.getInstrumentCapacity(); i++) {
      final Instrument instrument = InstrumentCache.get(i);
      if (instrument == null)
        continue;
      final SecurityDefinitionAdminMessage security = new SecurityDefinitionAdminMessage(instrument);
      security.setSnapId(snapId);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("restateAllInstruments security={}, snapId={}", security, snapId);
      }
      matcherToPublisherQueue.addGuaranteed(security);
    }

    for (int i = 0; i < InstrumentCache.getPairCapacity(); i++) {
      final InstrumentPair instrumentPair = InstrumentCache.getPair(i);
      if (instrumentPair == null)
        continue;
      final SecurityDefinitionAdminMessage security = new SecurityDefinitionAdminMessage(instrumentPair);
      LOGGER.info(Constants.LOG_FMT_2, "Sec Def from instrument pair: getSecurityId: ", security.getSecurityId(), " getUnderlyerId: ", security.getUnderlyerId(),
          " getContractExpireTime: ", security.getContractExpireTime()," getArrSize: ", security.getArrSize());
      security.setSnapId(snapId);
      if (instrumentPair.getOrderBook() != null) {
        security.setSecondaryOrderId(instrumentPair.getOrderBook().getSecondaryOrderId());
        security.setSecondaryExecId(instrumentPair.getOrderBook().getFilledCount());
      }

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("restateAllInstruments security={}, snapId={}", security, snapId);
      }
      matcherToPublisherQueue.addGuaranteed(security);

      final List<FeeAdminMessage> list = instrumentPair.getFeeAdminRestateList();
      for (final FeeAdminMessage fee : list) {
        fee.setSnapId(snapId);
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_6, "restateAllInstruments fee=", fee, ", security=", security, ", snapId=", snapId);
        }
        matcherToPublisherQueue.addGuaranteed(fee);
      }
    }
  }

  public static final Instrument[] getAltCollateralInstrumentArr() {
    return altCollateralInstrumentArr;
  }

  public static final int getSettleInstrumentPriceScaleMult() {
    if (instrumentArr == null)
      return PropertyReader.getProperty("DEFAULT_SETTLE_INSTRUMENT_PRICE_SCALE_MULT", 100);

    final Instrument instrument = instrumentArr[1];
    if (instrument != null)
      return instrument.getPriceMultiplier();
    return PropertyReader.getProperty("DEFAULT_SETTLE_INSTRUMENT_PRICE_SCALE_MULT", 100);
  }

  public static final int getSettleInstrumentQuantityScaleMult() {
    final Instrument instrument = instrumentArr[1];
    if (instrument != null)
      return instrument.getQuantityMultiplier();
    return PropertyReader.getProperty("DEFAULT_SETTLE_INSTRUMENT_QUANTITY_SCALE_MULT", 100);
  }

  public static final int getSettleInstrumentQuantityScale() {
    final Instrument instrument = instrumentArr[1];
    if (instrument != null)
      return instrument.getQuantityScale();
    return PropertyReader.getProperty("DEFAULT_SETTLE_INSTRUMENT_QUANTITY_SCALE", 2);
  }

  public static final int getMinSpotPairIndex() {
    return minSpotPairIndex;
  }

  public static final int getMaxSpotPairIndex() {
    return maxSpotPairIndex;
  }

}
