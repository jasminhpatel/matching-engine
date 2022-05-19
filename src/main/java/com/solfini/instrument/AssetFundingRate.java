package com.solfini.instrument;

import com.solfini.util.StringUtil;
import uk.co.real_logic.artio.fields.DecimalFloat;

/**
 *
 * @author Chris Mack
 *
 */
public class AssetFundingRate {

  private int assetId;
  private DecimalFloat rate;
  private DecimalFloat markInSettleCoin;

  private double vwap;
  private double last;
  private double usdMark;
  private double timePeriodInterest;

  public AssetFundingRate() {}

  public AssetFundingRate(final int assetId, final DecimalFloat rate, final DecimalFloat markInSettleCoin) {
    this.assetId = assetId;
    this.rate = rate;
    this.markInSettleCoin = markInSettleCoin;
  }

  public final int getAssetId() {
    return assetId;
  }

  public final void setAssetId(final int assetId) {
    this.assetId = assetId;
  }

  public final DecimalFloat getRate() {
    return rate;
  }

  public final void setRate(final DecimalFloat rate) {
    this.rate = rate;
  }

  public final DecimalFloat getMarkInSettleCoin() {
    return markInSettleCoin;
  }

  public final void setMarkInSettleCoin(final DecimalFloat markInSettleCoin) {
    this.markInSettleCoin = markInSettleCoin;
  }

  public final double getVwap() {
    return vwap;
  }

  public final void setVwap(final double vwap) {
    this.vwap = vwap;
  }

  public final double getLast() {
    return last;
  }

  public final void setLast(final double last) {
    this.last = last;
  }

  public final double getUsdMark() {
    return usdMark;
  }

  public final void setUsdMark(final double usdMark) {
    this.usdMark = usdMark;
  }

  public final double getTimePeriodInterest() {
    return timePeriodInterest;
  }

  public final void setTimePeriodInterest(final double timePeriodInterest) {
    this.timePeriodInterest = timePeriodInterest;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("AssetFundingRate [assetId=").append(assetId).append(", rate=").append((rate == null ? 0 : rate.value()))
        .append(", rate_scale=").append((rate == null ? 0 : rate.scale())).append(", markInSettleCoin=")
        .append((markInSettleCoin == null ? 0 : markInSettleCoin.value())).append(", markInSettleCoin_scale=")
        .append((markInSettleCoin == null ? 0 : markInSettleCoin.scale())).append("]");
  }

  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"AssetFundingRate\"").append(",\"assetId\":").append(assetId);
    if (rate != null) {
      sb.append(",\"rate\":[").append(rate.value()).append(",").append(rate.scale()).append("]");
    }
    if (markInSettleCoin != null) {
      sb.append(",\"markInSettleCoin\":[").append(markInSettleCoin.value()).append(",").append(markInSettleCoin.scale()).append("]");
    }
    sb.append("}");
    return sb.toString();
  }
}
