package rnd;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SecurityDefinitionAdminMessageDto {

  @JsonProperty("class")
  private String clazz;

  @JsonProperty("sequenceNumber")
  private long sequenceNumber;

  @JsonProperty("persistTime")
  private long persistTime;

  @JsonProperty("sourceSeqNum")
  private long sourceSeqNum;

  @JsonProperty("sourceSendTime")
  private long sourceSendTime;

  @JsonProperty("snapId")
  private long snapId;

  @JsonProperty("kafkaRecordOffset")
  private long kafkaRecordOffset;

  @JsonProperty("connectionId")
  private long connectionId;

  @JsonProperty("triggerTimeMillis")
  private long triggerTimeMillis;

  @JsonProperty("externalId")
  private long externalId;

  @JsonProperty("updateType")
  private int updateType;

  @JsonProperty("routeToDestination")
  private String routeToDestination;

  @JsonProperty("securityId")
  private int securityId;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("name")
  private String name;

  @JsonProperty("assetType")
  private int assetType;

  @JsonProperty("marketStatus")
  private int marketStatus;

  @JsonProperty("marketType")
  private int marketType;

  @JsonProperty("baseId")
  private int baseId;

  @JsonProperty("quotedId")
  private int quotedId;

  @JsonProperty("priceScale")
  private int priceScale;

  @JsonProperty("quantityScale")
  private int quantityScale;

  @JsonProperty("underlyerId")
  private int underlyerId;

  @JsonProperty("strikePrice")
  private int strikePrice;

  @JsonProperty("expireTimeMillis")
  private long expireTimeMillis;

  @JsonProperty("orderBookStrategy")
  private int orderBookStrategy;

  @JsonProperty("preOrderCheckStrategy")
  private int preOrderCheckStrategy;

  @JsonProperty("estimatedUserCount")
  private int estimatedUserCount;

  @JsonProperty("daysFeedIsActive")
  private int daysFeedIsActive;

  @JsonProperty("estimatedVolatility")
  private double estimatedVolatility;

  @JsonProperty("estimatedVAR")
  private double estimatedVAR;

  @JsonProperty("settleType")
  private int settleType;

  @JsonProperty("maintMarginPercent")
  private int maintMarginPercent;

  @JsonProperty("requiredMarginPercent")
  private int requiredMarginPercent;

  @JsonProperty("minQty")
  private long minQty;

  @JsonProperty("maxQty")
  private long maxQty;

  @JsonProperty("maxPrice")
  private long maxPrice;

  @JsonProperty("supportOrderType")
  private int supportOrderType;

  @JsonProperty("marginCurveId")
  private int marginCurveId;

  @JsonProperty("commissionType")
  private int commissionType;

  @JsonProperty("indexFeedUsdMark")
  private double indexFeedUsdMark;

  @JsonProperty("collateralMarginPercentDiscount")
  private int collateralMarginPercentDiscount;

  @JsonProperty("arrSize")
  private int arrSize;

  @JsonProperty("cacheDepth")
  private int cacheDepth;

  @JsonProperty("textData")
  private String textData;

  @JsonProperty("secondaryOrderId")
  private long secondaryOrderId;

  @JsonProperty("secondaryExecId")
  private long secondaryExecId;

  @JsonProperty("auctionStartTimeHrGMT")
  private int auctionStartTimeHrGMT;

  @JsonProperty("auctionDurationTime")
  private int auctionDurationTime;

  @JsonProperty("auctionFixingAttempts")
  private int auctionFixingAttempts;

  @JsonProperty("auctionFixingWaitTime")
  private int auctionFixingWaitTime;

  @JsonProperty("expireRollTimeMillis")
  private long expireRollTimeMillis;

  @JsonProperty("symbolRollCount")
  private int symbolRollCount;

  @JsonProperty("physicalSettle")
  private boolean physicalSettle;

  @JsonProperty("withdrawFee")
  private double withdrawFee;

  @JsonProperty("isWithdrawFeePercent")
  private boolean isWithdrawFeePercent;

  @JsonProperty("withdrawFeeInstrument")
  private int withdrawFeeInstrument;

  @JsonProperty("sector")
  private int sector;

  public String toJson() {
    return "{"
        + "\"class\":\"" + clazz + "\""
        + ",\"sequenceNumber\":" + sequenceNumber
        + ",\"persistTime\":" + persistTime
        + ",\"sourceSeqNum\":" + sourceSeqNum
        + ",\"sourceSendTime\":" + sourceSendTime
        + ",\"snapId\":" + snapId
        + ",\"kafkaRecordOffset\":" + kafkaRecordOffset
        + ",\"connectionId\":" + connectionId
        + ",\"triggerTimeMillis\":" + triggerTimeMillis
        + ",\"externalId\":" + externalId
        + ",\"updateType\":" + updateType
        + ",\"routeToDestination\":\"" + routeToDestination + "\""
        + ",\"securityId\":" + securityId
        + ",\"symbol\":\"" + symbol + "\""
        + ",\"name\":\"" + name + "\""
        + ",\"assetType\":" + assetType
        + ",\"marketStatus\":" + marketStatus
        + ",\"marketType\":" + marketType
        + ",\"baseId\":" + baseId
        + ",\"quotedId\":" + quotedId
        + ",\"priceScale\":" + priceScale
        + ",\"quantityScale\":" + quantityScale
        + ",\"underlyerId\":" + underlyerId
        + ",\"strikePrice\":" + strikePrice
        + ",\"expireTimeMillis\":" + expireTimeMillis
        + ",\"orderBookStrategy\":" + orderBookStrategy
        + ",\"preOrderCheckStrategy\":" + preOrderCheckStrategy
        + ",\"estimatedUserCount\":" + estimatedUserCount
        + ",\"daysFeedIsActive\":" + daysFeedIsActive
        + ",\"estimatedVolatility\":" + estimatedVolatility
        + ",\"estimatedVAR\":" + estimatedVAR
        + ",\"settleType\":" + settleType
        + ",\"maintMarginPercent\":" + maintMarginPercent
        + ",\"requiredMarginPercent\":" + requiredMarginPercent
        + ",\"minQty\":" + minQty
        + ",\"maxQty\":" + maxQty
        + ",\"maxPrice\":" + maxPrice
        + ",\"supportOrderType\":" + supportOrderType
        + ",\"marginCurveId\":" + marginCurveId
        + ",\"commissionType\":" + commissionType
        + ",\"indexFeedUsdMark\":" + indexFeedUsdMark
        + ",\"collateralMarginPercentDiscount\":" + collateralMarginPercentDiscount
        + ",\"arrSize\":" + arrSize
        + ",\"cacheDepth\":" + cacheDepth
        + ",\"textData\":\"" + textData + "\""
        + ",\"secondaryOrderId\":" + secondaryOrderId
        + ",\"secondaryExecId\":" + secondaryExecId
        + ",\"auctionStartTimeHrGMT\":" + auctionStartTimeHrGMT
        + ",\"auctionDurationTime\":" + auctionDurationTime
        + ",\"auctionFixingAttempts\":" + auctionFixingAttempts
        + ",\"auctionFixingWaitTime\":" + auctionFixingWaitTime
        + ",\"expireRollTimeMillis\":" + expireRollTimeMillis
        + ",\"symbolRollCount\":" + symbolRollCount
        + ",\"physicalSettle\":" + physicalSettle
        + ",\"withdrawFee\":" + withdrawFee
        + ",\"isWithdrawFeePercent\":" + isWithdrawFeePercent
        + ",\"withdrawFeeInstrument\":" + withdrawFeeInstrument
        + ",\"sector\":" + sector
        + "}";
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public String getClazz() { return clazz; }
  public long getSequenceNumber() { return sequenceNumber; }
  public long getPersistTime() { return persistTime; }
  public long getSourceSeqNum() { return sourceSeqNum; }
  public long getSourceSendTime() { return sourceSendTime; }
  public long getSnapId() { return snapId; }
  public long getKafkaRecordOffset() { return kafkaRecordOffset; }
  public long getConnectionId() { return connectionId; }
  public long getTriggerTimeMillis() { return triggerTimeMillis; }
  public long getExternalId() { return externalId; }
  public int getUpdateType() { return updateType; }
  public String getRouteToDestination() { return routeToDestination; }
  public int getSecurityId() { return securityId; }
  public String getSymbol() { return symbol; }
  public String getName() { return name; }
  public int getAssetType() { return assetType; }
  public int getMarketStatus() { return marketStatus; }
  public int getMarketType() { return marketType; }
  public int getBaseId() { return baseId; }
  public int getQuotedId() { return quotedId; }
  public int getPriceScale() { return priceScale; }
  public int getQuantityScale() { return quantityScale; }
  public int getUnderlyerId() { return underlyerId; }
  public int getStrikePrice() { return strikePrice; }
  public long getExpireTimeMillis() { return expireTimeMillis; }
  public int getOrderBookStrategy() { return orderBookStrategy; }
  public int getPreOrderCheckStrategy() { return preOrderCheckStrategy; }
  public int getEstimatedUserCount() { return estimatedUserCount; }
  public int getDaysFeedIsActive() { return daysFeedIsActive; }
  public double getEstimatedVolatility() { return estimatedVolatility; }
  public double getEstimatedVAR() { return estimatedVAR; }
  public int getSettleType() { return settleType; }
  public int getMaintMarginPercent() { return maintMarginPercent; }
  public int getRequiredMarginPercent() { return requiredMarginPercent; }
  public long getMinQty() { return minQty; }
  public long getMaxQty() { return maxQty; }
  public long getMaxPrice() { return maxPrice; }
  public int getSupportOrderType() { return supportOrderType; }
  public int getMarginCurveId() { return marginCurveId; }
  public int getCommissionType() { return commissionType; }
  public double getIndexFeedUsdMark() { return indexFeedUsdMark; }
  public int getCollateralMarginPercentDiscount() { return collateralMarginPercentDiscount; }
  public int getArrSize() { return arrSize; }
  public int getCacheDepth() { return cacheDepth; }
  public String getTextData() { return textData; }
  public long getSecondaryOrderId() { return secondaryOrderId; }
  public long getSecondaryExecId() { return secondaryExecId; }
  public int getAuctionStartTimeHrGMT() { return auctionStartTimeHrGMT; }
  public int getAuctionDurationTime() { return auctionDurationTime; }
  public int getAuctionFixingAttempts() { return auctionFixingAttempts; }
  public int getAuctionFixingWaitTime() { return auctionFixingWaitTime; }
  public long getExpireRollTimeMillis() { return expireRollTimeMillis; }
  public int getSymbolRollCount() { return symbolRollCount; }
  public boolean isPhysicalSettle() { return physicalSettle; }
  public double getWithdrawFee() { return withdrawFee; }
  public boolean isWithdrawFeePercent() { return isWithdrawFeePercent; }
  public int getWithdrawFeeInstrument() { return withdrawFeeInstrument; }
  public int getSector() { return sector; }


  // ── Setters ────────────────────────────────────────────────────────────────

  public void setClazz(String clazz) { this.clazz = clazz; }
  public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
  public void setPersistTime(long persistTime) { this.persistTime = persistTime; }
  public void setSourceSeqNum(long sourceSeqNum) { this.sourceSeqNum = sourceSeqNum; }
  public void setSourceSendTime(long sourceSendTime) { this.sourceSendTime = sourceSendTime; }
  public void setSnapId(long snapId) { this.snapId = snapId; }
  public void setKafkaRecordOffset(long kafkaRecordOffset) { this.kafkaRecordOffset = kafkaRecordOffset; }
  public void setConnectionId(long connectionId) { this.connectionId = connectionId; }
  public void setTriggerTimeMillis(long triggerTimeMillis) { this.triggerTimeMillis = triggerTimeMillis; }
  public void setExternalId(long externalId) { this.externalId = externalId; }
  public void setUpdateType(int updateType) { this.updateType = updateType; }
  public void setRouteToDestination(String routeToDestination) { this.routeToDestination = routeToDestination; }
  public void setSecurityId(int securityId) { this.securityId = securityId; }
  public void setSymbol(String symbol) { this.symbol = symbol; }
  public void setName(String name) { this.name = name; }
  public void setAssetType(int assetType) { this.assetType = assetType; }
  public void setMarketStatus(int marketStatus) { this.marketStatus = marketStatus; }
  public void setMarketType(int marketType) { this.marketType = marketType; }
  public void setBaseId(int baseId) { this.baseId = baseId; }
  public void setQuotedId(int quotedId) { this.quotedId = quotedId; }
  public void setPriceScale(int priceScale) { this.priceScale = priceScale; }
  public void setQuantityScale(int quantityScale) { this.quantityScale = quantityScale; }
  public void setUnderlyerId(int underlyerId) { this.underlyerId = underlyerId; }
  public void setStrikePrice(int strikePrice) { this.strikePrice = strikePrice; }
  public void setExpireTimeMillis(long expireTimeMillis) { this.expireTimeMillis = expireTimeMillis; }
  public void setOrderBookStrategy(int orderBookStrategy) { this.orderBookStrategy = orderBookStrategy; }
  public void setPreOrderCheckStrategy(int preOrderCheckStrategy) { this.preOrderCheckStrategy = preOrderCheckStrategy; }
  public void setEstimatedUserCount(int estimatedUserCount) { this.estimatedUserCount = estimatedUserCount; }
  public void setDaysFeedIsActive(int daysFeedIsActive) { this.daysFeedIsActive = daysFeedIsActive; }
  public void setEstimatedVolatility(double estimatedVolatility) { this.estimatedVolatility = estimatedVolatility; }
  public void setEstimatedVAR(double estimatedVAR) { this.estimatedVAR = estimatedVAR; }
  public void setSettleType(int settleType) { this.settleType = settleType; }
  public void setMaintMarginPercent(int maintMarginPercent) { this.maintMarginPercent = maintMarginPercent; }
  public void setRequiredMarginPercent(int requiredMarginPercent) { this.requiredMarginPercent = requiredMarginPercent; }
  public void setMinQty(long minQty) { this.minQty = minQty; }
  public void setMaxQty(long maxQty) { this.maxQty = maxQty; }
  public void setMaxPrice(long maxPrice) { this.maxPrice = maxPrice; }
  public void setSupportOrderType(int supportOrderType) { this.supportOrderType = supportOrderType; }
  public void setMarginCurveId(int marginCurveId) { this.marginCurveId = marginCurveId; }
  public void setCommissionType(int commissionType) { this.commissionType = commissionType; }
  public void setIndexFeedUsdMark(double indexFeedUsdMark) { this.indexFeedUsdMark = indexFeedUsdMark; }
  public void setCollateralMarginPercentDiscount(int collateralMarginPercentDiscount) { this.collateralMarginPercentDiscount = collateralMarginPercentDiscount; }
  public void setArrSize(int arrSize) { this.arrSize = arrSize; }
  public void setCacheDepth(int cacheDepth) { this.cacheDepth = cacheDepth; }
  public void setTextData(String textData) { this.textData = textData; }
  public void setSecondaryOrderId(long secondaryOrderId) { this.secondaryOrderId = secondaryOrderId; }
  public void setSecondaryExecId(long secondaryExecId) { this.secondaryExecId = secondaryExecId; }
  public void setAuctionStartTimeHrGMT(int auctionStartTimeHrGMT) { this.auctionStartTimeHrGMT = auctionStartTimeHrGMT; }
  public void setAuctionDurationTime(int auctionDurationTime) { this.auctionDurationTime = auctionDurationTime; }
  public void setAuctionFixingAttempts(int auctionFixingAttempts) { this.auctionFixingAttempts = auctionFixingAttempts; }
  public void setAuctionFixingWaitTime(int auctionFixingWaitTime) { this.auctionFixingWaitTime = auctionFixingWaitTime; }
  public void setExpireRollTimeMillis(long expireRollTimeMillis) { this.expireRollTimeMillis = expireRollTimeMillis; }
  public void setSymbolRollCount(int symbolRollCount) { this.symbolRollCount = symbolRollCount; }
  public void setPhysicalSettle(boolean physicalSettle) { this.physicalSettle = physicalSettle; }
  public void setWithdrawFee(double withdrawFee) { this.withdrawFee = withdrawFee; }
  public void setWithdrawFeePercent(boolean withdrawFeePercent) { this.isWithdrawFeePercent = withdrawFeePercent; }
  public void setWithdrawFeeInstrument(int withdrawFeeInstrument) { this.withdrawFeeInstrument = withdrawFeeInstrument; }
  public void setSector(int sector) { this.sector = sector; }
}
