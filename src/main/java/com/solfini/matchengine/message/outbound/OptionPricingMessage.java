package com.solfini.matchengine.message.outbound;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.internal.schema.PayloadType;

/**
 * @author Chris Mack
 */
public class OptionPricingMessage extends Message {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(OptionPricingMessage.class);

  private long messageSequenceNumber;
  private long sentTime;
  private int securityId;
  private int updateType;
  private int underlyerId;
  private int strikePrice;
  private long expireTimeMillis;
  private double usdStrikePrice;
  private double usdUnderlyerPrice;
  private double usdModelPrice;
  private double interestRate;
  private double timeToExpire;
  private double dividend;
  private double delta;
  private double theta;
  private double rho;
  private double normalCDF;
  private double gamma;
  private double vega;
  private double sigma;
  private long bid;
  private long ask;
  private long last;
  private long openQty;


  public OptionPricingMessage() {
    sentTime = System.currentTimeMillis();
  }

  public final void set(final long sentTime, final int securityId, final int updateType, final int underlyerId, final int strikePrice,
      final long expireTimeMillis, final double usdStrikePrice, final double usdUnderlyerPrice, final double usdModelPrice,
      final double interestRate, final double timeToExpire, final double dividend, final double delta, final double theta, final double rho,
      final double normalCDF, final double gamma, final double vega, final double sigma, final long bid, final long ask, final long last,
      final long openQty) {
    this.sentTime = sentTime;
    this.securityId = securityId;
    this.updateType = updateType;
    this.underlyerId = underlyerId;
    this.strikePrice = strikePrice;
    this.expireTimeMillis = expireTimeMillis;
    this.usdStrikePrice = usdStrikePrice;
    this.usdUnderlyerPrice = usdUnderlyerPrice;
    this.usdModelPrice = usdModelPrice;
    this.interestRate = interestRate;
    this.timeToExpire = timeToExpire;
    this.dividend = dividend;
    this.delta = delta;
    this.theta = theta;
    this.rho = rho;
    this.normalCDF = normalCDF;
    this.gamma = gamma;
    this.vega = vega;
    this.sigma = sigma;
    this.bid = bid;
    this.ask = ask;
    this.last = last;
    this.openQty = openQty;
  }

  @Override
  public void clear() {
    super.clear();

    securityId = 0;
  }

  private static long scale(final long value, final int scale1, final int scale2) {
    long result = value;
    for (int i = 0; i < Math.abs(scale1 - scale2); i++) {
      if (scale1 > scale2)
        result /= 10;
      else if (scale2 > scale1)
        result *= 10;
    }
    return result;
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public final MessageType getMessageType() {
    return MessageType.OPTION_PRICING;
  }

  public final long getMessageSequenceNumber() {
    return messageSequenceNumber;
  }

  public final void setMessageSequenceNumber(final long messageSequenceNumber) {
    this.messageSequenceNumber = messageSequenceNumber;
  }

  public final long getSentTime() {
    return sentTime;
  }

  public final void setSentTime(final long sentTime) {
    this.sentTime = sentTime;
  }

  public final int getSecurityId() {
    return securityId;
  }

  public final void setSecurityId(final int securityId) {
    this.securityId = securityId;
  }

  public final int getUpdateType() {
    return updateType;
  }

  public final void setUpdateType(final int updateType) {
    this.updateType = updateType;
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

  public final long getBid() {
    return bid;
  }

  public final void setBid(final long bid) {
    this.bid = bid;
  }

  public final long getAsk() {
    return ask;
  }

  public final void setAsk(final long ask) {
    this.ask = ask;
  }

  public final long getLast() {
    return last;
  }

  public final void setLast(final long last) {
    this.last = last;
  }

  public final long getOpenQty() {
    return openQty;
  }

  public final void setOpenQty(final long openQty) {
    this.openQty = openQty;
  }

  @Override
  public final void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public final void onPersist() {
    // Persister.onMessage(this);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    s.append(OPTIONPRICINGMESSAGE_MSGSEQ_EQ).append(messageSequenceNumber).append(SEND_TIME_EQ).append(sentTime).append(SECURITYID_EQ)
        .append(securityId).append(UPDATETYPE_EQ).append(updateType).append(UNDERLYERID_EQ).append(underlyerId).append(STRIKEPRICE_EQ)
        .append(strikePrice).append(EXPIRETIMEMILLIS_EQ).append(expireTimeMillis).append(USDSTRIKEPRICE_EQ).append(usdStrikePrice)
        .append(USDUNDERLYERPRICE_EQ).append(usdUnderlyerPrice).append(USDMODELPRICE_EQ).append(usdModelPrice).append(INTERESTRATE_EQ)
        .append(interestRate).append(TIMETOEXPIRE_EQ).append(timeToExpire).append(DIVIDEND_EQ).append(dividend).append(DELTA_EQ)
        .append(delta).append(THETA_EQ).append(theta).append(RHO_EQ).append(rho).append(NORMALCDF_EQ).append(normalCDF).append(GAMMA_EQ)
        .append(gamma).append(VEGA_EQ).append(vega).append(SIGMA_EQ).append(sigma).append(BID_EQ).append(bid).append(ASK_EQ).append(ask)
        .append(LAST_EQ).append(last).append(OPENQTY_EQ).append(openQty).append(']');
    return s;
  }

  @Override
  public final String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"OptionPricingMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"messageSequenceNumber\":").append(messageSequenceNumber).append(",\"sentTime\":").append(sentTime)
        .append(",\"securityId\":").append(securityId).append(",\"updateType\":").append("\"").append(updateType).append("\"")
        .append(",\"underlyerId\":").append(underlyerId).append(",\"strikePrice\":").append("\"").append(strikePrice).append("\"")
        .append(",\"expireTimeMillis\":").append(expireTimeMillis).append(",\"usdStrikePrice\":").append("\"").append(usdStrikePrice)
        .append("\"").append(",\"usdUnderlyerPrice\":").append(usdUnderlyerPrice).append(",\"usdModelPrice\":").append("\"")
        .append(usdModelPrice).append("\"").append(",\"interestRate\":").append(interestRate).append(",\"timeToExpire\":").append("\"")
        .append(timeToExpire).append("\"").append(",\"dividend\":").append(dividend).append(",\"delta\":").append("\"").append(delta)
        .append("\"").append(",\"theta\":").append(theta).append(",\"rho\":").append("\"").append(rho).append("\"")
        .append(",\"normalCDF\":").append(normalCDF).append(",\"gamma\":").append("\"").append(gamma).append("\"").append(",\"vega\":")
        .append(vega).append(",\"sigma\":").append("\"").append(sigma).append("\"").append(",\"bid\":").append(bid).append(",\"ask\":")
        .append("\"").append(ask).append("\"").append(",\"last\":").append(last).append(",\"openQty\":").append("\"").append(openQty);
    sb.append("}");
    return sb.toString();
  }

}
