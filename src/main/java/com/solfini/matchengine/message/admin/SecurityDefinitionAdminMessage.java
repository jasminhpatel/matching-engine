package com.solfini.matchengine.message.admin;

import com.solfini.common.AdminMessage;
import com.solfini.common.Context;
import com.solfini.common.MessageType;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.MarketType;
import com.solfini.internal.admin.schema.SecurityDefinitionAdminMessageDecoder;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.util.MbxMath;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class SecurityDefinitionAdminMessage extends AdminMessage {
  private UpdateType updateType;
  private int securityId;
  private String symbol;
  private String name;
  private AssetType assetType;
  private MarketStatus marketStatus;
  private MarketType marketType;
  private int baseId;
  private int quotedId;
  private short priceScale;
  private short quantityScale;
  private Instrument base;
  private Instrument quoted;
  private int orderBookStrategy;
  private int preOrderCheckStrategy;
  private int estimatedUserCount;
  private int daysFeedIsActive;
  private double estimatedVolatility;
  private double estimatedVAR;
  private int settleType = 0;
  private int maintMarginBasisPoints = 40; // default
  private int requiredMarginBasisPoints = 80; // default
  private int collateralMarginPercentDiscount = 0;
  private long minQty;
  private long maxQty;
  private long maxPrice;
  private int supportOrderType;
  private int commissionType;
  private double indexFeedUsdMark;
  private int marginCurveId = 0;
  private int arrSize = 0;
  private int cacheDepth = 0;
  private String textData;
  private long expireTimeMillis = 0;
  private long secondaryOrderId;
  private long secondaryExecId;
  private double circuitBreakerThreshold;

  private long contractExpireTime; // millis, for options, dated futures
  private long expireRollTimeMillis;
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
  private double sigma;

  public int auctionStartTimeHrGMT; // auctions
  public long auctionDurationTime;
  public int auctionFixingAttempts;
  public long auctionFixingWaitTime;
  public boolean snapConverterMode = false;
  public boolean physicalSettle = false;
  public boolean limitOnlyMode = false;


  public SecurityDefinitionAdminMessage() {}

  public SecurityDefinitionAdminMessage(final SecurityDefinitionAdminMessageDecoder SECURITY_DEFINITION_DECODER, final long connectionId) {
    this.connectionId = connectionId;
    this.updateType = SECURITY_DEFINITION_DECODER.updateType();
    this.securityId = SECURITY_DEFINITION_DECODER.securityId();
    this.symbol = SECURITY_DEFINITION_DECODER.symbol();
    this.name = SECURITY_DEFINITION_DECODER.name();
    this.assetType = SECURITY_DEFINITION_DECODER.assetType();
    this.marketStatus = SECURITY_DEFINITION_DECODER.marketStatus();
    this.marketType = SECURITY_DEFINITION_DECODER.marketType();
    this.baseId = SECURITY_DEFINITION_DECODER.baseId();
    this.quotedId = SECURITY_DEFINITION_DECODER.quotedId();
    this.priceScale = SECURITY_DEFINITION_DECODER.priceScale();
    this.quantityScale = SECURITY_DEFINITION_DECODER.quantityScale();
    this.orderBookStrategy = SECURITY_DEFINITION_DECODER.orderBookStrategy();
    this.preOrderCheckStrategy = SECURITY_DEFINITION_DECODER.preOrderCheckStrategy();
    this.settleType = SECURITY_DEFINITION_DECODER.settleType();
    this.maintMarginBasisPoints = SECURITY_DEFINITION_DECODER.maintMarginPercent();
    this.requiredMarginBasisPoints = SECURITY_DEFINITION_DECODER.requiredMarginPercent();
    this.collateralMarginPercentDiscount = SECURITY_DEFINITION_DECODER.collateralMarginPercentDiscount();
    this.marginCurveId = SECURITY_DEFINITION_DECODER.marginCurveId();
    this.minQty = SECURITY_DEFINITION_DECODER.minQty();
    this.maxQty = SECURITY_DEFINITION_DECODER.maxQty();
    this.maxPrice = SECURITY_DEFINITION_DECODER.maxPrice();
    this.supportOrderType = SECURITY_DEFINITION_DECODER.supportOrderType();
    this.commissionType = SECURITY_DEFINITION_DECODER.commissionType();
    this.triggerTimeMillis = SECURITY_DEFINITION_DECODER.triggerTimeMillis();
    this.routeToDestination = SECURITY_DEFINITION_DECODER.routeToDestination();
    this.arrSize = SECURITY_DEFINITION_DECODER.arrSize();
    this.cacheDepth = SECURITY_DEFINITION_DECODER.cacheDepth();
    this.textData = SECURITY_DEFINITION_DECODER.textData();
    this.senderInstanceId = SECURITY_DEFINITION_DECODER.senderInstanceId();
    this.underlyerId = SECURITY_DEFINITION_DECODER.underlyerId();
    this.strikePrice = SECURITY_DEFINITION_DECODER.strikePrice();
    this.expireTimeMillis = SECURITY_DEFINITION_DECODER.expireTimeMillis();
    this.secondaryOrderId = SECURITY_DEFINITION_DECODER.secondaryOrderId();
    this.secondaryExecId = SECURITY_DEFINITION_DECODER.secondaryExecId();
    this.circuitBreakerThreshold = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.circuitBreakerThreshold());
    this.auctionStartTimeHrGMT = SECURITY_DEFINITION_DECODER.auctionStartTimeHrGMT();
    this.auctionDurationTime = SECURITY_DEFINITION_DECODER.auctionDurationTime();
    this.auctionFixingAttempts = SECURITY_DEFINITION_DECODER.auctionFixingAttempts();
    this.auctionFixingWaitTime = SECURITY_DEFINITION_DECODER.auctionFixingWaitTime();
    this.physicalSettle = SECURITY_DEFINITION_DECODER.physicalSettle() == 1;
    this.limitOnlyMode = SECURITY_DEFINITION_DECODER.limitOnlyMode() == 1;

    this.usdStrikePrice = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.usdStrikePrice()); // for options, calculated from strikePrice
    if (usdStrikePrice == 0 && strikePrice > 0) {
      usdStrikePrice = MbxMath.roundToBestPrecision(strikePrice / (double) MbxMath.multiplier(priceScale));
    } else if (strikePrice == 0 && usdStrikePrice > 0) {
      strikePrice = (int) (usdStrikePrice * MbxMath.multiplier(priceScale));
    }

    this.usdUnderlyerPrice = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.usdUnderlyerPrice()); // stock price
    this.usdModelPrice = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.usdModelPrice()); // model option price
    this.interestRate = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.interestRate()); // interestRate used for option calc
    this.timeToExpire = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.timeToExpire()); // option time to expire, in annual format of
                                                                                         // contractExpireTime
    this.expireRollTimeMillis = SECURITY_DEFINITION_DECODER.expireRollTimeMillis();
    this.symbolRollCount = SECURITY_DEFINITION_DECODER.symbolRollCount();
    this.dividend = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.dividend()); // dividend for option calc
    this.delta = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.delta());
    this.theta = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.theta());
    this.rho = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.rho());
    this.normalCDF = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.normalCDF());
    this.gamma = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.gamma());
    this.vega = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.vega());
    this.sigma = StringUtil.toDouble(SECURITY_DEFINITION_DECODER.sigma());

    indexFeedUsdMark = SECURITY_DEFINITION_DECODER.indexFeedUsdMark().value();
    for (int i = 0; i < SECURITY_DEFINITION_DECODER.indexFeedUsdMark().scale(); i++) {
      indexFeedUsdMark *= 0.1;
    }

    // default
    if (maintMarginBasisPoints == 0)
      maintMarginBasisPoints = 40;
    if (requiredMarginBasisPoints == 0)
      requiredMarginBasisPoints = 80;
  }

  public SecurityDefinitionAdminMessage(final Instrument instrument) {
    this.updateType = UpdateType.PUT;
    this.securityId = instrument.getId();
    this.symbol = instrument.getSymbol();
    this.name = instrument.getName();
    this.assetType = AssetType.ASSET;
    this.marketStatus = MarketStatus.OPEN;
    this.baseId = 0; // ?
    this.quotedId = instrument.getQuotedInstrumentId();
    this.priceScale = instrument.getPriceScale();
    this.quantityScale = instrument.getQuantityScale();
    this.orderBookStrategy = 0; // ?
    this.preOrderCheckStrategy = 0; // ?
    this.settleType = 0;
    this.maintMarginBasisPoints = 0;
    this.requiredMarginBasisPoints = 0;
    this.marginCurveId = 0;
    this.indexFeedUsdMark = instrument.getIndexFeedUsdMark();
    this.collateralMarginPercentDiscount = instrument.getCollateralMarginPercentDiscount();
  }

  public SecurityDefinitionAdminMessage(final InstrumentPair instrumentPair) {
    this.updateType = UpdateType.PUT;
    this.securityId = instrumentPair.getId();
    this.symbol = instrumentPair.getSymbol();
    this.name = instrumentPair.getName();
    this.assetType = instrumentPair.getAssetType(); // was pair
    this.marketStatus = instrumentPair.getMarketStatus();
    this.marketType = instrumentPair.getMarketType();
    this.baseId = instrumentPair.getBaseId();
    this.quotedId = instrumentPair.getQuotedId();
    this.priceScale = instrumentPair.getPriceScale();
    this.quantityScale = instrumentPair.getQuantityScale();
    this.estimatedUserCount = instrumentPair.getEstimatedUserCount();
    this.daysFeedIsActive = instrumentPair.getDaysFeedIsActive();
    this.estimatedVAR = instrumentPair.getEstimatedVAR();
    this.estimatedVolatility = instrumentPair.getEstimatedVolatility();
    this.settleType = instrumentPair.getSettleType();
    this.maintMarginBasisPoints = instrumentPair.getMaintMarginBasisPoints();
    this.requiredMarginBasisPoints = instrumentPair.getRequiredMarginBasisPoints();
    this.indexFeedUsdMark = instrumentPair.getIndexFeedUsdMark();
    this.marginCurveId = instrumentPair.getMarginCurveId();
    this.minQty = instrumentPair.getMinOrderQuantity();
    this.circuitBreakerThreshold = instrumentPair.getCircuitBreakerThreshold();

    this.strikePrice = instrumentPair.getStrikePrice();

    this.underlyerId = instrumentPair.getUnderlyerId();
    this.expireTimeMillis = instrumentPair.getContractExpireTime();
    this.contractExpireTime = instrumentPair.getContractExpireTime();
    this.symbolRollCount = instrumentPair.getSymbolRollCount();
    this.physicalSettle = instrumentPair.isPhysicalSettle();

    this.auctionStartTimeHrGMT = instrumentPair.getAuctionStartTimeHrGMT();
    this.auctionDurationTime = instrumentPair.getAuctionDurationTime();
    this.auctionFixingAttempts = instrumentPair.getAuctionFixingAttempts();
    this.auctionFixingWaitTime = instrumentPair.getAuctionFixingWaitTime();

    this.usdStrikePrice = instrumentPair.getUsdStrikePrice(); // for options, calculated from strikePrice
    if (usdStrikePrice == 0 && strikePrice > 0) {
      usdStrikePrice = MbxMath.roundToBestPrecision(strikePrice / (double) MbxMath.multiplier(priceScale));
    } else if (strikePrice == 0 && usdStrikePrice > 0) {
      strikePrice = (int) (usdStrikePrice * MbxMath.multiplier(priceScale));
    }
    this.usdUnderlyerPrice = instrumentPair.getUsdUnderlyerPrice(); // stock price
    this.usdModelPrice = instrumentPair.getUsdModelPrice(); // model option price
    this.interestRate = instrumentPair.getInterestRate(); // interestRate used for option calc
    this.timeToExpire = instrumentPair.getTimeToExpire(); // option time to expire, in annual format of
                                                          // contractExpireTime
    this.expireRollTimeMillis = instrumentPair.getExpireRollTimeMillis();

    this.dividend = instrumentPair.getDividend(); // dividend for option calc
    this.delta = instrumentPair.getDelta();
    this.theta = instrumentPair.getTheta();
    this.rho = instrumentPair.getRho();
    this.normalCDF = instrumentPair.getNormalCDF();
    this.gamma = instrumentPair.getGamma();
    this.vega = instrumentPair.getVega();
    this.sigma = instrumentPair.getSigma();

    OrderBook orderBook = instrumentPair.getOrderBook();
    if (orderBook != null) {
      this.orderBookStrategy = orderBook.getOrderBookStrategy();
      this.preOrderCheckStrategy = orderBook.getPreOrderCheckStrategy();
      this.arrSize = orderBook.getArrSize();
    }
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  public final MessageType getMessageType() {
    return MessageType.SECURITY_DEFINITION;
  }

  public final Instrument getBase() {
    return base;
  }

  public final void setBase(final Instrument base) {
    this.base = base;
  }

  public final Instrument getQuoted() {
    return quoted;
  }

  public final void setQuoted(final Instrument quoted) {
    this.quoted = quoted;
  }

  public final int getSecurityId() {
    return securityId;
  }

  public final String getSymbol() {
    return symbol;
  }

  public final String getName() {
    return name;
  }

  public final AssetType getAssetType() {
    return assetType;
  }

  public final MarketStatus getMarketStatus() {
    return marketStatus;
  }

  public final int getBaseId() {
    return baseId;
  }

  public final int getQuotedId() {
    return quotedId;
  }

  public final short getPriceScale() {
    return priceScale;
  }

  public final short getQuantityScale() {
    return quantityScale;
  }

  public final UpdateType getUpdateType() {
    return updateType;
  }

  public final int getOrderBookStrategy() {
    return orderBookStrategy;
  }

  public final int getPreOrderCheckStrategy() {
    return preOrderCheckStrategy;
  }

  public final MarketType getMarketType() {
    return marketType;
  }

  public final void setMarketType(final MarketType marketType) {
    this.marketType = marketType;
  }

  public final void setUpdateType(final UpdateType updateType) {
    this.updateType = updateType;
  }

  public final void setSecurityId(final int securityId) {
    this.securityId = securityId;
  }

  public final void setSymbol(final String symbol) {
    this.symbol = symbol;
  }

  public final void setName(final String name) {
    this.name = name;
  }

  public final void setAssetType(final AssetType assetType) {
    this.assetType = assetType;
  }

  public final void setMarketStatus(final MarketStatus marketStatus) {
    this.marketStatus = marketStatus;
  }

  public final void setBaseId(final int baseId) {
    this.baseId = baseId;
  }

  public final void setQuotedId(final int quotedId) {
    this.quotedId = quotedId;
  }

  public final void setPriceScale(final short priceScale) {
    this.priceScale = priceScale;
  }

  public final void setQuantityScale(final short quantityScale) {
    this.quantityScale = quantityScale;
  }

  public final void setOrderBookStrategy(final int orderBookStrategy) {
    this.orderBookStrategy = orderBookStrategy;
  }

  public final void setPreOrderCheckStrategy(final int preOrderCheckStrategy) {
    this.preOrderCheckStrategy = preOrderCheckStrategy;
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

  public final long getMinQty() {
    return minQty;
  }

  public final void setMinQty(final long minQty) {
    this.minQty = minQty;
  }

  public final long getMaxQty() {
    return maxQty;
  }

  public final void setMaxQty(final long maxQty) {
    this.maxQty = maxQty;
  }

  public final long getMaxPrice() {
    return maxPrice;
  }

  public final void setMaxPrice(final long maxPrice) {
    this.maxPrice = maxPrice;
  }

  public final int getSupportOrderType() {
    return supportOrderType;
  }

  public final void setSupportOrderType(final int supportOrderType) {
    this.supportOrderType = supportOrderType;
  }

  public final int getCommissionType() {
    return commissionType;
  }

  public final void setCommissionType(final int commissionType) {
    this.commissionType = commissionType;
  }

  public final double getIndexFeedUsdMark() {
    return indexFeedUsdMark;
  }

  public final void setIndexFeedUsdMark(final double indexFeedUsdMark) {
    this.indexFeedUsdMark = indexFeedUsdMark;
  }

  public final int getCollateralMarginPercentDiscount() {
    return collateralMarginPercentDiscount;
  }

  public final void setCollateralMarginPercentDiscount(final int collateralMarginPercentDiscount) {
    this.collateralMarginPercentDiscount = collateralMarginPercentDiscount;
  }

  public final int getMarginCurveId() {
    return marginCurveId;
  }

  public final void setMarginCurveId(final int marginCurveId) {
    this.marginCurveId = marginCurveId;
  }

  public final int getArrSize() {
    return arrSize;
  }

  public final void setArrSize(final int arrSize) {
    this.arrSize = arrSize;
  }

  public final int getCacheDepth() {
    return cacheDepth;
  }

  public final void setCacheDepth(final int cacheDepth) {
    this.cacheDepth = cacheDepth;
  }

  public final String getTextData() {
    return textData;
  }

  public final void setTextData(final String textData) {
    this.textData = textData;
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

  public final long getExpireTimeMillis() {
    return expireTimeMillis;
  }

  public final void setExpireTimeMillis(final long expireTimeMillis) {
    this.expireTimeMillis = expireTimeMillis;
  }

  public final long getSecondaryOrderId() {
    return secondaryOrderId;
  }

  public final void setSecondaryOrderId(final long secondaryOrderId) {
    this.secondaryOrderId = secondaryOrderId;
  }

  public final long getSecondaryExecId() {
    return secondaryExecId;
  }

  public final void setSecondaryExecId(final long secondaryExecId) {
    this.secondaryExecId = secondaryExecId;
  }

  public final double getCircuitBreakerThreshold() {
    return circuitBreakerThreshold;
  }

  public final void setCircuitBreakerThreshold(final double circuitBreakerThreshold) {
    this.circuitBreakerThreshold = circuitBreakerThreshold;
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

  public final double getUsdStrikePrice() {
    return usdStrikePrice;
  }

  public final void setUsdStrikePrice(final double usdStrikePrice) {
    this.usdStrikePrice = usdStrikePrice;
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

  public final void setSnapConverterMode(boolean snapConverterMode) {
    this.snapConverterMode = snapConverterMode;
  }

  public final boolean isSnapConverterMode() {
    return snapConverterMode;
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

  @Override
  public final void onMatcher() {
    InstrumentCache.updateSecurityDefinition(this);
  }

  @Override
  public final void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append(SECURITYDEFINITIONADMINMESSAGE_UPDATETYPE_EQ).append(updateType).append(SECURITYID_EQ).append(securityId).append(SYMBOL_EQ)
        .append(symbol).append(NAME_EQ).append(name).append(ASSETTYPE_EQ).append(assetType).append(MARKETSTATUS_EQ).append(marketStatus)
        .append(MARKETTYPE_EQ).append(marketType).append(TRIGGERTIMEMILLIS_EQ).append(triggerTimeMillis).append(BASEID_EQ).append(baseId)
        .append(QUOTEID_EQ).append(quotedId).append(PRICESCALE_EQ).append(priceScale).append(QUANTITYSCALE_EQ).append(quantityScale)
        .append(BASE_EQ).append(base).append(QUOTED_EQ).append(quoted).append(ORDERBOOKSTRATEGY_EQ).append(orderBookStrategy)
        .append(PREORDERCHECKSTRATEGY_EQ).append(preOrderCheckStrategy).append(ESTIMATEDUSERCOUNT_EQ).append(estimatedUserCount)
        .append(DAYSFEEDISACTIVE_EQ).append(daysFeedIsActive).append(ESTIMATEDVOLATILITY_EQ).append(estimatedVolatility)
        .append(ESTIMATEDVAR_EQ).append(estimatedVAR).append(SETTLETYPE_EQ).append(settleType).append(MAINTMARGINPERCENT_EQ)
        .append(maintMarginBasisPoints).append(REQUIREDMARGINPERCENT_EQ).append(requiredMarginBasisPoints).append(MINQTY_EQ).append(minQty)
        .append(MAXQTY_EQ).append(maxQty).append(MAXPRICE_EQ).append(maxPrice).append(SUPPORTORDERTYPE_EQ).append(supportOrderType)
        .append(UNDERLYERID_EQ).append(underlyerId).append(STRIKEPRICE_EQ).append(strikePrice).append(EXPIRETIMEMILLIS_EQ)
        .append(expireTimeMillis).append(MARGINCURVEID_EQ).append(marginCurveId).append(COMISSIONTYPE_EQ).append(commissionType)
        .append(ROUTETODESTINATION_EQ).append(routeToDestination).append(INDEXFEEDUSDMARK_EQ).append(indexFeedUsdMark)
        .append(COLLATERALMARGINPERCENTDISCOUNT_EQ).append(collateralMarginPercentDiscount).append(", secondaryOrderId=")
        .append(secondaryOrderId).append(", secondaryExecId=").append(secondaryExecId).append(SOURCESEQNUM_EQ).append(sourceSeqNum)
        .append(SOURCESENDTIME_EQ).append(sourceSendTime).append(ARRSIZE_EQ).append(arrSize).append(CACHEDEPTH_EQ).append(cacheDepth)
        .append(TEXTDATA_EQ).append(textData).append(AUCTION_START_TIME_HR_GMT_EQ).append(auctionStartTimeHrGMT)
        .append(AUCTION_DURATION_TIME_EQ).append(auctionDurationTime).append(AUCTION_FIXING_ATTEMPTS_EQ).append(auctionFixingAttempts)
        .append(AUCTION_FIXING_WAIT_TIME_EQ).append(auctionFixingWaitTime).append(PYSICALSETTLE_EQ).append(physicalSettle).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"SecurityDefinitionAdminMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber)
        .append(",\"persistTime\":").append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":")
        .append(sourceSendTime).append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset)
        .append(",\"connectionId\":").append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis)
        .append(",\"externalId\":").append(externalId).append(",\"updateType\":").append(updateType.value());
    sb.append(",\"routeToDestination\":").append("\"").append(routeToDestination).append("\"");
    sb.append(",\"securityId\":").append(securityId).append(",\"symbol\":").append("\"").append(symbol).append("\"").append(",\"name\":")
        .append("\"").append(name).append("\"").append(",\"assetType\":").append(assetType.value()).append(",\"marketStatus\":")
        .append(marketStatus.value()).append(",\"marketType\":").append(marketType.value()).append(",\"baseId\":").append(baseId)
        .append(",\"quotedId\":").append(quotedId).append(",\"priceScale\":").append(priceScale).append(",\"quantityScale\":")
        .append(quantityScale).append(",\"underlyerId\":").append(underlyerId).append(",\"strikePrice\":").append(strikePrice)
        .append(",\"expireTimeMillis\":").append(expireTimeMillis).append(",\"orderBookStrategy\":").append(orderBookStrategy)
        .append(",\"preOrderCheckStrategy\":").append(preOrderCheckStrategy).append(",\"estimatedUserCount\":").append(estimatedUserCount)
        .append(",\"daysFeedIsActive\":").append(daysFeedIsActive).append(",\"estimatedVolatility\":").append(estimatedVolatility)
        .append(",\"estimatedVAR\":").append(estimatedVAR).append(",\"settleType\":").append(settleType).append(",\"maintMarginPercent\":")
        .append(maintMarginBasisPoints).append(",\"requiredMarginPercent\":").append(requiredMarginBasisPoints).append(",\"minQty\":")
        .append(minQty).append(",\"maxQty\":").append(maxQty).append(",\"maxPrice\":").append(maxPrice).append(",\"supportOrderType\":")
        .append(supportOrderType).append(",\"marginCurveId\":").append(marginCurveId).append(",\"commissionType\":").append(commissionType)
        .append(",\"indexFeedUsdMark\":").append(indexFeedUsdMark).append(",\"collateralMarginPercentDiscount\":")
        .append(collateralMarginPercentDiscount).append(",\"arrSize\":").append(arrSize).append(",\"cacheDepth\":").append(cacheDepth)
        .append(",\"textData\":").append("\"").append(textData).append("\"").append(",\"secondaryOrderId\":").append(secondaryOrderId)
        .append(",\"secondaryExecId\":").append(secondaryExecId).append(",\"auctionStartTimeHrGMT\":").append(auctionStartTimeHrGMT)
        .append(",\"auctionDurationTime\":").append(auctionDurationTime).append(",\"auctionFixingAttempts\":").append(auctionFixingAttempts)
        .append(",\"auctionFixingWaitTime\":").append(auctionFixingWaitTime).append(",\"expireRollTimeMillis\":")
        .append(expireRollTimeMillis).append(",\"symbolRollCount\":").append(symbolRollCount).append(",\"physicalSettle\":")
        .append(physicalSettle);
    sb.append("}");
    return sb.toString();
  }

}
