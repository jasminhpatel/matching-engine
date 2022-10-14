package com.solfini.instrument;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.solfini.common.Appendable;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.MarketType;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.stats.TradeHistory;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.util.FastArrayList;
import com.solfini.util.MbxMath;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class InstrumentPair implements Appendable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(InstrumentPair.class);

  private final int id;
  private String symbol;
  private String name;
  private final Instrument base;
  private final Instrument quoted;
  private final int baseId;
  private final int quotedId;
  private final short priceScale;
  private final short quantityScale;
  private OrderBook orderBook;
  private MarketStatus marketStatus;
  private MarketType marketType;
  private int settleType;
  private AssetType assetType;
  private int maintMarginBasisPoints;
  private int requiredMarginBasisPoints;
  private double indexFeedUsdMark;
  private long fundingRateTime;
  private double estFundingRate;
  private long minOrderQuantity;
  private double circuitBreakerThreshold;
  private double externalFundingRate; // set from external fundingRateCalc message

  private int marginCurveId;
  private long contractExpireTime; // millis, for options, dated futures
  private long expireRollTimeMillis; // time to roll contract
  private int symbolRollCount;

  private int underlyerId; // for options
  private int strikePrice; // for options
  private double usdStrikePrice; // for options, calculated from strikePrice
  private double usdUnderlyerPrice; // stock price
  private double usdModelPrice; // model option price
  private double interestRate; // interestRate used for option calc
  private double timeToExpire; // option time to expire, in annual format of contractExpireTime
  private double dividend; // dividend for option calc
  private double delta;
  private double theta;
  private double rho;
  private double normalCDF;
  private double gamma;
  private double vega;
  private double sigma; // impliedVolatility
  private long openQty;

  private int estimatedUserCount;
  private int daysFeedIsActive;
  private double estimatedVolatility;
  private double estimatedVAR;
  private boolean physicalSettle;
  private boolean limitOnlyMode;

  private final double quantityScaleFactor;
  private final double priceScaleFactor;
  private final int quantityScaleMultiplier;
  private final int priceScaleMultiplier;

  // auctions
  private int auctionStartTimeHrGMT; // default to 2
  private long auctionDurationTime; // default to 36_000_000
  private int auctionFixingAttempts; // default to 3
  private long auctionFixingWaitTime; // default to 60_000
  private long auctionLastStartedTime; // used internally only
  private long auctionLastStoppedTime; // used internally only

  private static final int FEE_TIER_CAPACITY = 16;
  private final Fee[] makerFeeArr;
  private final Fee[] takerFeeArr;
  private final Fee[] makerFeeDiscountedArr;
  private final Fee[] takerFeeDiscountedArr;
  private final AtomicInteger inMarketDataQueue = new AtomicInteger(0);

  // for price feed protection
  private double prevIndexFeedUsdMark;
  private long prevIndexFeedUsdMarkTime;

  private double effectiveVolumeScale;

  private final TradeHistory tradeHistory;
  private final TradeHistory midHistory;

  public InstrumentPair(final int id, final String symbol, final String name, final Instrument base, final Instrument quoted,
      final short priceScale, final short quantityScale, final int settleType, final AssetType assetType, final int maintMarginBasisPoints,
      final int requiredMarginBasisPoints, final double usdMark, final int marginCurveId) {
    this(id, symbol, name, base, quoted, priceScale, quantityScale, settleType, assetType, maintMarginBasisPoints,
        requiredMarginBasisPoints, usdMark, marginCurveId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false);
  }

  public InstrumentPair(final int id, final String symbol, final String name, final Instrument base, final Instrument quoted,
      final short priceScale, final short quantityScale, final int settleType, final AssetType assetType, final int maintMarginBasisPoints,
      final int requiredMarginBasisPoints, final double usdMark, final int marginCurveId, final long expireTimeMillis,
      final int strikePrice, final int underlyerId, final long minOrderQuantity, final int auctionStartTimeHrGMT,
      final long auctionDurationTime, final int auctionFixingAttempts, final long auctionFixingWaitTime,
      final double circuitBreakerThreshold, final long expireRollTimeMillis, final int symbolRollCount, final boolean physicalSettle,
      final boolean limitOnlyMode) {
    this.id = id;
    this.symbol = symbol;
    this.name = name;
    this.base = base;
    this.baseId = base == null ? 0 : base.getId();
    this.quoted = quoted;
    this.quotedId = quoted == null ? 0 : quoted.getId();
    this.priceScale = priceScale;
    this.quantityScale = quantityScale;
    this.makerFeeArr = new Fee[FEE_TIER_CAPACITY];
    this.takerFeeArr = new Fee[FEE_TIER_CAPACITY];
    this.makerFeeDiscountedArr = new Fee[FEE_TIER_CAPACITY];
    this.takerFeeDiscountedArr = new Fee[FEE_TIER_CAPACITY];
    this.settleType = settleType;
    this.assetType = assetType;
    this.maintMarginBasisPoints = maintMarginBasisPoints;
    this.requiredMarginBasisPoints = requiredMarginBasisPoints;
    this.indexFeedUsdMark = usdMark;
    this.marginCurveId = marginCurveId;
    this.quantityScaleFactor = scaleFactor(quantityScale);
    this.quantityScaleMultiplier = scaleMultiplier(quantityScale);
    this.priceScaleFactor = scaleFactor(priceScale);
    this.priceScaleMultiplier = scaleMultiplier(priceScale);
    this.contractExpireTime = expireTimeMillis;
    this.expireRollTimeMillis = expireRollTimeMillis; // time to roll contract
    this.symbolRollCount = symbolRollCount;
    this.strikePrice = strikePrice;
    this.underlyerId = underlyerId;
    this.usdStrikePrice = MbxMath.roundToBestPrecision(priceScaleFactor * strikePrice);
    this.minOrderQuantity = minOrderQuantity;
    this.auctionStartTimeHrGMT = auctionStartTimeHrGMT;
    this.auctionDurationTime = auctionDurationTime;
    this.auctionFixingAttempts = auctionFixingAttempts;
    this.auctionFixingWaitTime = auctionFixingWaitTime;
    this.circuitBreakerThreshold = circuitBreakerThreshold;
    this.fundingRateTime = expireTimeMillis;
    this.physicalSettle = physicalSettle;
    this.limitOnlyMode = limitOnlyMode;


    if (assetType == AssetType.PAIR) {
      this.effectiveVolumeScale = 1.0;
    } else {
      this.effectiveVolumeScale = 0.1;
    }

    if (circuitBreakerThreshold == 0) {
      if (assetType == AssetType.PAIR || assetType == AssetType.PERPETUAL_SWAP || assetType == AssetType.DATED_FUTURE)
        this.circuitBreakerThreshold = 0.30; // default to 30%
    }

    this.tradeHistory = new TradeHistory(id, underlyerId);
    this.midHistory = new TradeHistory(id, underlyerId);
  }

  public final double getEffectiveVolumeScale() {
    return effectiveVolumeScale;
  }

  private static double scaleFactor(final int scale) {
    double factor = 1;
    for (int i = 0; i < scale; i++) {
      factor = factor * 0.1;
    }
    return factor;
  }

  private static int scaleMultiplier(final int scale) {
    int multiplier = 1;
    for (int i = 0; i < scale; i++) {
      multiplier = multiplier * 10;
    }
    return multiplier;
  }

  public final int getId() {
    return id;
  }

  public final String getSymbol() {
    return symbol;
  }

  public final String getName() {
    return name;
  }

  public final Instrument getBase() {
    return base;
  }

  public final Instrument getQuoted() {
    return quoted;
  }

  public final short getPriceScale() {
    return priceScale;
  }

  public final short getQuantityScale() {
    return quantityScale;
  }

  public final int getQuantityScaleMultiplier() {
    return quantityScaleMultiplier;
  }

  public final int getPriceScaleMultiplier() {
    return priceScaleMultiplier;
  }

  public final void setOrderBook(final OrderBook orderBook) {
    this.orderBook = orderBook;
  }

  public final OrderBook getOrderBook() {
    return orderBook;
  }

  public final int getBaseId() {
    return baseId;
  }

  public final int getQuotedId() {
    return quotedId;
  }

  public final MarketStatus getMarketStatus() {
    return marketStatus;
  }

  public final void setMarketStatus(final MarketStatus marketStatus) {
    this.marketStatus = marketStatus;
  }

  public MarketType getMarketType() {
    return marketType;
  }

  public void setMarketType(final MarketType marketType) {
    this.marketType = marketType;
  }

  public final int getSettleType() {
    return settleType;
  }

  public final void setSettleType(final int settleType) {
    this.settleType = settleType;
  }

  public final int getMaintMarginBasisPoints() {
    return maintMarginBasisPoints;
  }

  public final void setMaintMarginBasisPoints(final int maintMarginBasisPoints) {
    this.maintMarginBasisPoints = maintMarginBasisPoints;
  }

  public final int getRequiredMarginBasisPoints() {
    return requiredMarginBasisPoints;
  }

  public final void setRequiredMarginBasisPoints(final int requiredMarginBasisPoints) {
    this.requiredMarginBasisPoints = requiredMarginBasisPoints;
  }

  public final double getIndexFeedUsdMark() {
    return indexFeedUsdMark;
  }

  public final long getFundingRateTime() {
    return fundingRateTime;
  }

  public final void setFundingRateTime(final long fundingRateTime) {
    this.fundingRateTime = fundingRateTime;
  }

  public final double getEstFundingRate() {
    return estFundingRate;
  }

  public final void setEstFundingRate(final double estFundingRate) {
    this.estFundingRate = estFundingRate;
  }

  public final void setIndexFeedUsdMark(double indexFeedUsdMark) {
    if (Context.isUseSpotMarketIndexPriceEnabled() && ((assetType == AssetType.PERPETUAL_SWAP) || (assetType == AssetType.DATED_FUTURE))
        && symbol.startsWith("BTC")) {
      try {
        final String spotSymbol = symbol.split("/")[0] + "/USDC";
        // use this pair instead of the spot market for index
        final InstrumentPair pair = this; // InstrumentCache.getPairBySymbol(spotSymbol);
        if (pair != null) {
          int spotTWAP = pair.getMidHistory().getRollingNSecondTWAP(Context.getSpotMarketIndexTwapSeconds());
          if (spotTWAP <= 0)
            spotTWAP = pair.getTradeHistory().getRollingNSecondTWAP(Context.getSpotMarketIndexTwapSeconds());

          final double usdSpotMark = StringUtil.toDouble(spotTWAP, pair.getPriceScale());
          if (usdSpotMark != 0) {
            final double diff = Math.abs((usdSpotMark - indexFeedUsdMark) / indexFeedUsdMark);
            if (diff < Context.getSpotMarketIndexThreshold()) { // set if within bounds of index
              if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(LOG_FMT_8, "setIndexFeedUsdMark1, UseSpotMarketIndexPriceEnabled usdSpotMark=", usdSpotMark, ", spotPair=",
                    pair.getId(), ", spotPair.getPriceScale()=", pair.getPriceScale(), ", indexFeedUsdMark=", indexFeedUsdMark);
              }

              indexFeedUsdMark = usdSpotMark;
            } else {
              // if spot is off more than the bounds, use the index adjusted by bounds
              final double boundsDiff = indexFeedUsdMark * Context.getSpotMarketIndexThreshold();
              if (indexFeedUsdMark > usdSpotMark)
                indexFeedUsdMark -= boundsDiff;
              else
                indexFeedUsdMark += boundsDiff;
            }
          }
        }
        if (pair != null && LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_8, "setIndexFeedUsdMark2, UseSpotMarketIndexPriceEnabled spotPair=", pair.getId(),
              ", spotPair.getPriceScale()=", pair.getPriceScale(), ", indexFeedUsdMark=", indexFeedUsdMark);
        }
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }

    // for price feed protection
    if (assetType != assetType.OPTION_CALL && assetType != assetType.OPTION_PUT) {
      if ((indexFeedUsdMark <= 0)
          || (prevIndexFeedUsdMark > 0 && (Math.abs(indexFeedUsdMark - prevIndexFeedUsdMark) / prevIndexFeedUsdMark) > 1.5)) {
        // if feed is stale turn off liquidation
        if (prevIndexFeedUsdMarkTime > 0 && System.currentTimeMillis() - prevIndexFeedUsdMarkTime > 600_000) {
          MarginPreOrderCheckAndSettle.setLIQUIDATON_MODE(false);
        }
        // LOGGER.warn("price feed protection indexFeedUsdMark=" + indexFeedUsdMark + ", prevIndexFeedUsdMark=" + prevIndexFeedUsdMark
        // + ", pair=" + this);
        return;
      }
    }

    this.prevIndexFeedUsdMark = this.indexFeedUsdMark;
    this.prevIndexFeedUsdMarkTime = System.currentTimeMillis();
    this.indexFeedUsdMark = indexFeedUsdMark;
  }

  public final double getCircuitBreakerThreshold() {
    return circuitBreakerThreshold;
  }

  public final void setCircuitBreakerThreshold(final double circuitBreakerThreshold) {
    this.circuitBreakerThreshold = circuitBreakerThreshold;
  }

  public final int getEstimatedUserCount() {
    return estimatedUserCount;
  }

  public final void setEstimatedUserCount(final int estimatedUserCount) {
    this.estimatedUserCount = estimatedUserCount;
  }

  public final int getDaysFeedIsActive() {
    return daysFeedIsActive;
  }

  public final void setDaysFeedIsActive(final int daysFeedIsActive) {
    this.daysFeedIsActive = daysFeedIsActive;
  }

  public final double getEstimatedVolatility() {
    return estimatedVolatility;
  }

  public final void setEstimatedVolatility(final double estimatedVolatility) {
    this.estimatedVolatility = estimatedVolatility;
  }

  public final double getEstimatedVAR() {
    return estimatedVAR;
  }

  public final void setEstimatedVAR(final double estimatedVAR) {
    this.estimatedVAR = estimatedVAR;
  }

  public final double getQuantityScaleFactor() {
    return quantityScaleFactor;
  }

  public final double getPriceScaleFactor() {
    return priceScaleFactor;
  }

  public final AtomicInteger isInMarketDataQueue() {
    return inMarketDataQueue;
  }

  public final AssetType getAssetType() {
    return assetType;
  }

  public final void setAssetType(final AssetType assetType) {
    this.assetType = assetType;
  }

  public final int getMarginCurveId() {
    return marginCurveId;
  }

  public final void setMarginCurveId(final int marginCurveId) {
    this.marginCurveId = marginCurveId;
  }

  public final long getContractExpireTime() {
    return contractExpireTime;
  }

  public final void setContractExpireTime(final long contractExpireTime) {
    this.contractExpireTime = contractExpireTime;
  }

  public final long getExpireRollTimeMillis() {
    return expireRollTimeMillis;
  }

  public final void setExpireRollTimeMillis(final long expireRollTimeMillis) {
    this.expireRollTimeMillis = expireRollTimeMillis;
  }

  public final int getSymbolRollCount() {
    return symbolRollCount;
  }

  public final void setSymbolRollCount(final int symbolRollCount) {
    this.symbolRollCount = symbolRollCount;
  }

  public final int getUnderlyerId() {
    return underlyerId;
  }

  public final void setUnderlyerId(final int underlyerId) {
    this.underlyerId = underlyerId;
  }

  public final int getStrikePrice() {
    return strikePrice;
  }

  public final void setStrikePrice(final int strikePrice) {
    this.strikePrice = strikePrice;
  }

  public final double getUsdStrikePrice() {
    return usdStrikePrice;
  }

  public final double getUsdUnderlyerPrice() {
    return usdUnderlyerPrice;
  }

  public final void setUsdUnderlyerPrice(final double usdUnderlyerPrice) {
    this.usdUnderlyerPrice = usdUnderlyerPrice;
  }

  public final double getUsdModelPrice() {
    return usdModelPrice;
  }

  public final void setUsdModelPrice(final double usdModelPrice) {
    this.usdModelPrice = usdModelPrice;
  }

  public final double getInterestRate() {
    return interestRate;
  }

  public final void setInterestRate(final double interestRate) {
    this.interestRate = interestRate;
  }

  public final double getTimeToExpire() {
    return timeToExpire;
  }

  public final void setTimeToExpire(final double timeToExpire) {
    this.timeToExpire = timeToExpire;
  }

  public final double getDividend() {
    return dividend;
  }

  public final void setDividend(final double dividend) {
    this.dividend = dividend;
  }

  public final double getDelta() {
    return delta;
  }

  public final void setDelta(final double delta) {
    this.delta = delta;
  }

  public final double getTheta() {
    return theta;
  }

  public final void setTheta(final double theta) {
    this.theta = theta;
  }

  public final double getRho() {
    return rho;
  }

  public final void setRho(final double rho) {
    this.rho = rho;
  }

  public final double getNormalCDF() {
    return normalCDF;
  }

  public final void setNormalCDF(final double normalCDF) {
    this.normalCDF = normalCDF;
  }

  public final double getGamma() {
    return gamma;
  }

  public final void setGamma(final double gamma) {
    this.gamma = gamma;
  }

  public final double getVega() {
    return vega;
  }

  public final void setVega(final double vega) {
    this.vega = vega;
  }

  public final double getSigma() {
    return sigma;
  }

  public final void setSigma(final double sigma) {
    this.sigma = sigma;
  }

  public final int getAuctionStartTimeHrGMT() {
    return auctionStartTimeHrGMT;
  }

  public final void setAuctionStartTimeHrGMT(final int auctionStartTimeHrGMT) {
    this.auctionStartTimeHrGMT = auctionStartTimeHrGMT;
  }

  public final long getAuctionDurationTime() {
    return auctionDurationTime;
  }

  public final void setAuctionDurationTime(final long auctionDurationTime) {
    this.auctionDurationTime = auctionDurationTime;
  }

  public final int getAuctionFixingAttempts() {
    return auctionFixingAttempts;
  }

  public final void setAuctionFixingAttempts(final int auctionFixingAttempts) {
    this.auctionFixingAttempts = auctionFixingAttempts;
  }

  public final long getAuctionFixingWaitTime() {
    return auctionFixingWaitTime;
  }

  public final void setAuctionFixingWaitTime(final long auctionFixingWaitTime) {
    this.auctionFixingWaitTime = auctionFixingWaitTime;
  }

  public final long getAuctionLastStartedTime() {
    return auctionLastStartedTime;
  }

  public final void setAuctionLastStartedTime(final long auctionLastStartedTime) {
    this.auctionLastStartedTime = auctionLastStartedTime;
  }

  public final long getAuctionLastStoppedTime() {
    return auctionLastStoppedTime;
  }

  public final void setAuctionLastStoppedTime(final long auctionLastStoppedTime) {
    this.auctionLastStoppedTime = auctionLastStoppedTime;
  }

  public final long getMinOrderQuantity() {
    return minOrderQuantity;
  }

  public final void setMinOrderQuantity(final long minOrderQuantity) {
    this.minOrderQuantity = minOrderQuantity;
  }

  public final double getPrevIndexFeedUsdMark() {
    return prevIndexFeedUsdMark;
  }

  public final void setPrevIndexFeedUsdMark(final double prevIndexFeedUsdMark) {
    this.prevIndexFeedUsdMark = prevIndexFeedUsdMark;
  }

  public final long getPrevIndexFeedUsdMarkTime() {
    return prevIndexFeedUsdMarkTime;
  }

  public final void setPrevIndexFeedUsdMarkTime(final long prevIndexFeedUsdMarkTime) {
    this.prevIndexFeedUsdMarkTime = prevIndexFeedUsdMarkTime;
  }

  public final boolean isPhysicalSettle() {
    return physicalSettle;
  }

  public final void setPhysicalSettle(final boolean physicalSettle) {
    this.physicalSettle = physicalSettle;
  }

  public final boolean isLimitOnlyMode() {
    return limitOnlyMode;
  }

  public final void setLimitOnlyMode(final boolean limitOnlyMode) {
    this.limitOnlyMode = limitOnlyMode;
  }

  public final long getOpenQty() {
    return openQty;
  }

  public final void setOpenQty(final long openQty) {
    this.openQty = openQty;
  }

  public final void setSymbol(final String symbol) {
    this.symbol = symbol;
  }

  public final void setName(final String name) {
    this.name = name;
  }

  public final Fee[] getMakerFeeArr() {
    return makerFeeArr;
  }

  public final Fee[] getTakerFeeArr() {
    return takerFeeArr;
  }

  public final Fee[] getMakerFeeDiscountedArr() {
    return makerFeeDiscountedArr;
  }

  public final Fee[] getTakerFeeDiscountedArr() {
    return takerFeeDiscountedArr;
  }

  public final TradeHistory getTradeHistory() {
    return tradeHistory;
  }

  public final TradeHistory getMidHistory() {
    return midHistory;
  }

  public final AtomicInteger getInMarketDataQueue() {
    return inMarketDataQueue;
  }

  public final void setUsdStrikePrice(final double usdStrikePrice) {
    this.usdStrikePrice = usdStrikePrice;
  }

  public final double getExternalFundingRate() {
    return externalFundingRate;
  }

  public final void setExternalFundingRate(final double externalFundingRate) {
    this.externalFundingRate = externalFundingRate;
  }

  public final double getUsdMark() {
    if (indexFeedUsdMark > 0) {
      return indexFeedUsdMark;
    } else if (orderBook != null) {
      return orderBook.getUsdMark();
    }
    return 0;
  }

  public final void setFee(final Fee fee) {
    final int discountFee = fee.getFee() <= 0 ? fee.getFee() : fee.getFee() * (1 - (Context.getDiscountFeesCoinBasisPoints() / 10_000));

    switch (fee.getMakerTaker()) {
      case MAKER:
        if (fee.getTier() >= makerFeeArr.length)
          throw new UnsupportedOperationException();
        makerFeeArr[fee.getTier()] = fee;
        makerFeeDiscountedArr[fee.getTier()] = new Fee(fee.getInstrumentPairId(), Context.getDiscountFeesInstrumentId(), discountFee,
            fee.getFeeType(), fee.getMakerTaker(), fee.getTier(), fee.isPaidToInsurance());
        break;
      case TAKER:
        if (fee.getTier() >= takerFeeArr.length)
          throw new UnsupportedOperationException();
        takerFeeArr[fee.getTier()] = fee;
        takerFeeDiscountedArr[fee.getTier()] = new Fee(fee.getInstrumentPairId(), Context.getDiscountFeesInstrumentId(), discountFee,
            fee.getFeeType(), fee.getMakerTaker(), fee.getTier(), fee.isPaidToInsurance());
        break;
      case ALL:
        if (fee.getTier() >= takerFeeArr.length || fee.getTier() > makerFeeArr.length)
          throw new UnsupportedOperationException();
        makerFeeArr[fee.getTier()] = fee;
        takerFeeArr[fee.getTier()] = fee;
        makerFeeDiscountedArr[fee.getTier()] = new Fee(fee.getInstrumentPairId(), Context.getDiscountFeesInstrumentId(), discountFee,
            fee.getFeeType(), fee.getMakerTaker(), fee.getTier(), fee.isPaidToInsurance());
        takerFeeDiscountedArr[fee.getTier()] = new Fee(fee.getInstrumentPairId(), Context.getDiscountFeesInstrumentId(), discountFee,
            fee.getFeeType(), fee.getMakerTaker(), fee.getTier(), fee.isPaidToInsurance());
        break;
      default:
        throw new UnsupportedOperationException();
    }
  }

  public final Fee getFee(final int tier, final boolean isMaker, final Order causingMessage) {
    if (Fee.LIQUIDATION_FEE_ID == tier)
      return Fee.LIQUIDATION_FEE;

    if (isMaker) {
      if (tier >= makerFeeArr.length)
        throw new UnsupportedOperationException();
      if (makerFeeArr[tier] == null) {
        makerFeeArr[tier] = Fee.EMPTY_FEE;
        return makerFeeArr[tier];
      }

      // guarantee taker fee - maker fee > 0, with negative fees
      if (makerFeeArr[tier].getFee() < 0 && causingMessage != null && causingMessage.getUser() != null) {
        int takerTier = causingMessage.getUser().getFeeTier();
        if ((takerTier < takerFeeArr.length && takerFeeArr[takerTier] != null)
            && (-makerFeeArr[tier].getFee() > takerFeeArr[takerTier].getFee())) {
          int maxFee = makerFeeArr[tier].getFee() + takerFeeArr[takerTier].getFee();
          return new Fee(makerFeeArr[tier].getInstrumentPairId(), makerFeeArr[tier].getFeeInstrumentId(), maxFee, FeeType.PERCENT,
              MakerTaker.MAKER, tier, false);
        }
      }

      return makerFeeArr[tier];
    } else {
      if (tier >= takerFeeArr.length)
        throw new UnsupportedOperationException();
      if (takerFeeArr[tier] == null)
        takerFeeArr[tier] = Fee.EMPTY_FEE;

      return takerFeeArr[tier];
    }
  }

  public final Fee getDiscountFee(final int tier, final boolean isMaker, final Order causingMessage) {
    if (Fee.LIQUIDATION_FEE_ID == tier)
      return Fee.LIQUIDATION_FEE;

    if (isMaker) {
      if (tier >= makerFeeArr.length)
        throw new UnsupportedOperationException();
      if (makerFeeDiscountedArr[tier] == null) {
        makerFeeDiscountedArr[tier] = Fee.EMPTY_FEE;
        return makerFeeDiscountedArr[tier];
      }

      // guarantee taker fee - maker fee > 0, with negative fees
      if (makerFeeDiscountedArr[tier].getFee() < 0 && causingMessage != null && causingMessage.getUser() != null) {
        int takerTier = causingMessage.getUser().getFeeTier();
        if ((takerTier < takerFeeDiscountedArr.length && takerFeeDiscountedArr[takerTier] != null)
            && (-makerFeeDiscountedArr[tier].getFee() > takerFeeDiscountedArr[takerTier].getFee())) {
          int maxFee = makerFeeDiscountedArr[tier].getFee() + takerFeeDiscountedArr[takerTier].getFee();
          return new Fee(makerFeeDiscountedArr[tier].getInstrumentPairId(), makerFeeDiscountedArr[tier].getFeeInstrumentId(), maxFee,
              FeeType.PERCENT, MakerTaker.MAKER, tier, false);
        }
      }

      return makerFeeDiscountedArr[tier];
    } else {
      if (tier >= takerFeeDiscountedArr.length)
        throw new UnsupportedOperationException();
      if (takerFeeDiscountedArr[tier] == null)
        takerFeeDiscountedArr[tier] = Fee.EMPTY_FEE;

      return takerFeeDiscountedArr[tier];
    }
  }

  public final void changeState(final MarketStatus marketStatus, final long snapId, final Message causingMessage) {
    orderBook.changeState(marketStatus, snapId, causingMessage);
  }

  public List<FeeAdminMessage> getFeeAdminRestateList() {
    final List<FeeAdminMessage> list = new FastArrayList<>();
    if (takerFeeArr != null) {
      for (Fee fee : takerFeeArr) {
        if (fee != null && fee.getInstrumentPairId() > 0)
          list.add(new FeeAdminMessage(fee));
      }
    }
    if (makerFeeArr != null) {
      for (Fee fee : makerFeeArr) {
        if (fee != null && fee.getInstrumentPairId() > 0)
          list.add(new FeeAdminMessage(fee));
      }
    }
    return list;
  }

  // given a quantity and scale adjust to this scale
  public final long adjustQuantityToScale(long quantityLong, final int quantityScale) {
    if (this.quantityScale > quantityScale) {
      for (int i = 0; i < (this.quantityScale - quantityScale); i++)
        quantityLong = quantityLong * 10;
    } else if (this.quantityScale < quantityScale) {
      for (int i = 0; i < (quantityScale - this.quantityScale); i++)
        quantityLong = quantityLong / 10;
    }
    return quantityLong;
  }

  // given a price and scale adjust to this scale
  public final long adjustPriceToScale(long priceLong, final int priceScale) {
    if (this.priceScale > priceScale) {
      for (int i = 0; i < (this.priceScale - priceScale); i++)
        priceLong = priceLong * 10;
    } else if (this.priceScale < priceScale) {
      for (int i = 0; i < (priceScale - this.priceScale); i++)
        priceLong = priceLong / 10;
    }
    return priceLong;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append("InstrumentPair [id=").append(id).append(SYMBOL_EQ).append(symbol).append(NAME_EQ).append(name).append(BASE_EQ).append(base)
        .append(QUOTED_EQ).append(quoted).append(BASEID_EQ).append(baseId).append(QUOTEID_EQ).append(quotedId)
        .append(", contractExpireTime=").append(contractExpireTime).append(", strikePrice=").append(strikePrice).append(", underlyerId=")
        .append(underlyerId).append(QUOTEID_EQ).append(quotedId).append(MARGINCURVEID_EQ).append(marginCurveId).append(PRICESCALE_EQ)
        .append(priceScale).append(QUANTITYSCALE_EQ).append(quantityScale).append(", orderBook=").append(orderBook)
        .append(", marketStatus=").append(marketStatus).append(SETTLETYPE_EQ).append(", indexFeedUsdMark=").append(indexFeedUsdMark)
        .append(", estFundingRate=").append(estFundingRate).append(", fundingRateTime=").append(fundingRateTime).append(settleType)
        .append(", assetType=").append(assetType).append(MAINTMARGINPERCENT_EQ).append(maintMarginBasisPoints)
        .append(REQUIREDMARGINPERCENT_EQ).append(requiredMarginBasisPoints).append(", externalFundingRate=").append(externalFundingRate)
        .append(", makerFeeArr=").append(Arrays.toString(makerFeeArr)).append(", takerFeeArr=").append(Arrays.toString(takerFeeArr))
        .append("]");
    return s;
  }

}
