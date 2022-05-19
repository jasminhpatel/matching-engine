package com.solfini.matchengine.message.admin;

import com.solfini.common.AdminMessage;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.MessageType;
import com.solfini.internal.admin.schema.GlobalStateAdminMessageDecoder;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.TimeEventGeneratorThread;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.risk.InsuranceState;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class GlobalStateAdminMessage extends AdminMessage {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(GlobalStateAdminMessage.class);

  private int snapLoaderMode;
  private int liquidationMode;
  private int useInsurance;
  private int insurancePositonPercentLimit;
  private int insuranceLossPercentLimit;
  private int insuranceAutoCloseMode;
  private long publishSequenceNumber;
  private long orderSequenceNumber;
  private long execSequenceNumber;
  private int discountFeesInstrumentId;
  private int discountFeesCoinBasisPts;
  private int changeLogLevel;
  private int enableAutoChangeLogLevel;
  private int enableStateValidator;
  private double fundingRateCollar;
  private double fundingRateMin;
  private double fundingRateInterestRate;

  public GlobalStateAdminMessage() {}

  public GlobalStateAdminMessage(final GlobalStateAdminMessageDecoder GLOBAL_STATE_ADMIN_DECODER, final long connectionId) {
    this.connectionId = connectionId;
    this.snapLoaderMode = GLOBAL_STATE_ADMIN_DECODER.snapLoaderMode();
    this.liquidationMode = GLOBAL_STATE_ADMIN_DECODER.liquidationMode();
    this.useInsurance = GLOBAL_STATE_ADMIN_DECODER.useInsurance();
    this.insurancePositonPercentLimit = GLOBAL_STATE_ADMIN_DECODER.insurancePositonPercentLimit();
    this.insuranceLossPercentLimit = GLOBAL_STATE_ADMIN_DECODER.insuranceLossPercentLimit();
    this.insuranceAutoCloseMode = GLOBAL_STATE_ADMIN_DECODER.insuranceAutoCloseMode();
    this.publishSequenceNumber = GLOBAL_STATE_ADMIN_DECODER.publishSequenceNumber();
    this.orderSequenceNumber = GLOBAL_STATE_ADMIN_DECODER.orderSequenceNumber();
    this.execSequenceNumber = GLOBAL_STATE_ADMIN_DECODER.execSequenceNumber();
    this.externalId = GLOBAL_STATE_ADMIN_DECODER.externalId();
    this.routeToDestination = GLOBAL_STATE_ADMIN_DECODER.routeToDestination();
    this.triggerTimeMillis = GLOBAL_STATE_ADMIN_DECODER.triggerTimeMillis();
    this.discountFeesInstrumentId = GLOBAL_STATE_ADMIN_DECODER.discountFeesInstrumentId();
    this.discountFeesCoinBasisPts = GLOBAL_STATE_ADMIN_DECODER.discountFeesCoinBasisPts();
    this.routeToDestination = GLOBAL_STATE_ADMIN_DECODER.routeToDestination();
    this.changeLogLevel = GLOBAL_STATE_ADMIN_DECODER.changeLogLevel();
    this.enableAutoChangeLogLevel = GLOBAL_STATE_ADMIN_DECODER.enableAutoChangeLogLevel();
    this.enableStateValidator = GLOBAL_STATE_ADMIN_DECODER.enableStateValidator();
    this.fundingRateCollar = StringUtil.toDouble(GLOBAL_STATE_ADMIN_DECODER.fundingRateCollar());
    this.fundingRateMin = StringUtil.toDouble(GLOBAL_STATE_ADMIN_DECODER.fundingRateMin());
    this.fundingRateInterestRate = StringUtil.toDouble(GLOBAL_STATE_ADMIN_DECODER.fundingRateInterestRate());
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  public final MessageType getMessageType() {
    return MessageType.GLOBAL_STATE_ADMIN;
  }

  public final int getSnapLoaderMode() {
    return snapLoaderMode;
  }

  public final void setSnapLoaderMode(final int snapLoaderMode) {
    this.snapLoaderMode = snapLoaderMode;
  }

  public final int getLiquidationMode() {
    return liquidationMode;
  }

  public final void setLiquidationMode(final int liquidationMode) {
    this.liquidationMode = liquidationMode;
  }

  public final int getUseInsurance() {
    return useInsurance;
  }

  public final void setUseInsurance(final int useInsurance) {
    this.useInsurance = useInsurance;
  }

  public final int getInsurancePositonPercentLimit() {
    return insurancePositonPercentLimit;
  }

  public final void setInsurancePositonPercentLimit(final int insurancePositonPercentLimit) {
    this.insurancePositonPercentLimit = insurancePositonPercentLimit;
  }

  public final int getInsuranceAutoCloseMode() {
    return insuranceAutoCloseMode;
  }

  public final void setInsuranceAutoCloseMode(final int insuranceAutoCloseMode) {
    this.insuranceAutoCloseMode = insuranceAutoCloseMode;
  }

  public final long getPublishSequenceNumber() {
    return publishSequenceNumber;
  }

  public final void setPublishSequenceNumber(final long publishSequenceNumber) {
    this.publishSequenceNumber = publishSequenceNumber;
  }

  public final long getOrderSequenceNumber() {
    return orderSequenceNumber;
  }

  public final void setOrderSequenceNumber(final long orderSequenceNumber) {
    this.orderSequenceNumber = orderSequenceNumber;
  }

  public final long getExecSequenceNumber() {
    return execSequenceNumber;
  }

  public final void setExecSequenceNumber(final long execSequenceNumber) {
    this.execSequenceNumber = execSequenceNumber;
  }

  public final int getInsuranceLossPercentLimit() {
    return insuranceLossPercentLimit;
  }

  public final void setInsuranceLossPercentLimit(final int insuranceLossPercentLimit) {
    this.insuranceLossPercentLimit = insuranceLossPercentLimit;
  }

  public final int getDiscountFeesCoinBasisPts() {
    return discountFeesCoinBasisPts;
  }

  public final void setDiscountFeesCoinBasisPts(final int discountFeesCoinBasisPts) {
    this.discountFeesCoinBasisPts = discountFeesCoinBasisPts;
  }

  public final int getDiscountFeesInstrumentId() {
    return discountFeesInstrumentId;
  }

  public final void setDiscountFeesInstrumentId(final int discountFeesInstrumentId) {
    this.discountFeesInstrumentId = discountFeesInstrumentId;
  }

  public final int getChangeLogLevel() {
    return changeLogLevel;
  }

  public final void setChangeLogLevel(final int changeLogLevel) {
    this.changeLogLevel = changeLogLevel;
  }

  public final int getEnableAutoChangeLogLevel() {
    return enableAutoChangeLogLevel;
  }

  public final void setEnableAutoChangeLogLevel(final int enableAutoChangeLogLevel) {
    this.enableAutoChangeLogLevel = enableAutoChangeLogLevel;
  }

  public final double getFundingRateCollar() {
    return fundingRateCollar;
  }

  public final void setFundingRateCollar(final double fundingRateCollar) {
    this.fundingRateCollar = fundingRateCollar;
  }

  public final double getFundingRateMin() {
    return fundingRateMin;
  }

  public final void setFundingRateMin(final double fundingRateMin) {
    this.fundingRateMin = fundingRateMin;
  }

  public final double getFundingRateInterestRate() {
    return fundingRateInterestRate;
  }

  public final void setFundingRateInterestRate(final double fundingRateInterestRate) {
    this.fundingRateInterestRate = fundingRateInterestRate;
  }

  @Override
  public void onMatcher() {
    // set global values here
    if (snapLoaderMode == 1)
      SnapLoader.setSnapLoaderMode(true);
    else if (snapLoaderMode == -1)
      SnapLoader.setSnapLoaderMode(false);

    if (liquidationMode == 1)
      MarginPreOrderCheckAndSettle.setLIQUIDATON_MODE(true);
    else if (liquidationMode == -1)
      MarginPreOrderCheckAndSettle.setLIQUIDATON_MODE(false);

    if (useInsurance == 1)
      InsuranceState.setUseInsurance(true);
    else if (useInsurance == -1)
      InsuranceState.setUseInsurance(false);

    if (insurancePositonPercentLimit > 0)
      InsuranceState.setInsurancePositonPercentLimit(insurancePositonPercentLimit);
    else if (insurancePositonPercentLimit < 0)
      InsuranceState.setInsurancePositonPercentLimit(0);

    if (insuranceLossPercentLimit > 0)
      InsuranceState.setInsuranceLossPercentLimit(insuranceLossPercentLimit);
    else if (insuranceLossPercentLimit < 0)
      InsuranceState.setInsuranceLossPercentLimit(0);

    if (insuranceAutoCloseMode > 0)
      InsuranceState.setInsuranceAutoCloseMode(insuranceAutoCloseMode);
    else if (insuranceAutoCloseMode < 0)
      InsuranceState.setInsuranceAutoCloseMode(0);

    if (publishSequenceNumber > 0)
      Context.getMessagePublisher().setSequenceNumber(publishSequenceNumber);
    else if (publishSequenceNumber < 0)
      Context.getMessagePublisher().setSequenceNumber(0);

    if (discountFeesInstrumentId > 0)
      Context.setDiscountFeesInstrumentId(discountFeesInstrumentId);
    else if (discountFeesInstrumentId < 0)
      Context.setDiscountFeesInstrumentId(0);

    if (discountFeesCoinBasisPts > 0)
      Context.setDiscountFeesCoinBasisPoints(discountFeesCoinBasisPts);
    else if (discountFeesCoinBasisPts < 0)
      Context.setDiscountFeesCoinBasisPoints(0);

    if (orderSequenceNumber > 0)
      NewOrderSingleHandler.setOrderId(orderSequenceNumber);
    else if (orderSequenceNumber < 0)
      NewOrderSingleHandler.setOrderId(0);

    if (execSequenceNumber > 0)
      ArrayOrderBook.setFilledCountGlobal(execSequenceNumber);
    else if (execSequenceNumber < 0)
      ArrayOrderBook.setFilledCountGlobal(0);

    if (enableAutoChangeLogLevel > 0)
      Context.setEnableAutoChangeLogLevel(true);
    else if (changeLogLevel < 0)
      Context.setEnableAutoChangeLogLevel(false);

    if (enableStateValidator > 0)
      Context.setStateValidatorEnabled(true);
    else if (enableStateValidator < 0)
      Context.setStateValidatorEnabled(false);

    if (fundingRateCollar > 0)
      TimeEventGeneratorThread.FUNDING_RATE_COLLAR = fundingRateCollar;
    else if (fundingRateCollar < 0)
      TimeEventGeneratorThread.FUNDING_RATE_COLLAR = 0;

    if (fundingRateMin > 0)
      TimeEventGeneratorThread.FUNDING_RATE_MIN = fundingRateMin;
    else if (fundingRateMin < 0)
      TimeEventGeneratorThread.FUNDING_RATE_MIN = 0;

    if (fundingRateInterestRate > 0)
      TimeEventGeneratorThread.FUNDING_RATE_INTEREST_RATE = fundingRateInterestRate;
    else if (fundingRateInterestRate < 0)
      TimeEventGeneratorThread.FUNDING_RATE_INTEREST_RATE = 0;

    LOGGER.info(LOG_FMT_1, " GlobalStateAdminMessage=", this);
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
    s.append("GlobalStateAdminMessage [snapLoaderMode=").append(snapLoaderMode).append(LIQUIDATIONMODE_EQ).append(liquidationMode)
        .append(USEINSURANCE_EQ).append(useInsurance).append(INSURANCEPOSITIONPERCENTLIMIT_EQ).append(insurancePositonPercentLimit)
        .append(INSURANCELOSSPERCENTLIMIT_EQ).append(insuranceLossPercentLimit).append(INSURANCEAUTOCLOSEMODE_EQ)
        .append(insuranceAutoCloseMode).append(", publishSequenceNumber=").append(publishSequenceNumber).append(ORDERSEQUENCENUMBER_EQ)
        .append(orderSequenceNumber).append(EXECSEQUENCENUMBER_EQ).append(execSequenceNumber).append(EXTERNALID_EQ).append(externalId)
        .append(ROUTETODESTINATION_EQ).append(routeToDestination).append(DISCOUNTFEESCOINBASISPTS_EQ).append(discountFeesCoinBasisPts)
        .append(SOURCESEQNUM_EQ).append(sourceSeqNum).append(SOURCESENDTIME_EQ).append(sourceSendTime).append(ENABLESTATEVALIDATOR_EQ)
        .append(enableStateValidator).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"FundingRateCalcMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis).append(",\"externalId\":").append(externalId)
        .append(",\"snapLoaderMode\":").append(snapLoaderMode).append(",\"liquidationMode\":").append(liquidationMode)
        .append(",\"routeToDestination\":").append("\"").append(routeToDestination).append("\"").append(",\"useInsurance\":")
        .append(useInsurance).append(",\"insurancePositonPercentLimit\":").append(insurancePositonPercentLimit)
        .append(",\"insuranceAutoCloseMode\":").append(insuranceAutoCloseMode).append(",\"discountFeesCoinBasisPts\":")
        .append(discountFeesCoinBasisPts).append(",\"fundingRateCollar\":").append(fundingRateCollar).append(",\"fundingRateMin\":")
        .append(fundingRateMin).append(",\"fundingRateInterestRate\":").append(fundingRateInterestRate)
        .append(",\"publishSequenceNumber\":").append(publishSequenceNumber).append(",\"orderSequenceNumber\":").append(orderSequenceNumber)
        .append(",\"enableStateValidator\":").append(enableStateValidator).append(",\"routeToDestination\":").append("\"")
        .append(routeToDestination).append("\"");
    sb.append("}");
    return sb.toString();
  }

}
