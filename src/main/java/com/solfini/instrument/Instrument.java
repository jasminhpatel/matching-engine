package com.solfini.instrument;

import com.solfini.common.Appendable;
import com.solfini.common.Constants;
import com.solfini.internal.admin.schema.Sector;

/**
 *
 * @author Chris Mack
 *
 */
public class Instrument implements Appendable, Constants {
  private final int id;
  private final String symbol;
  private final String name;
  private final short priceScale;
  private final short quantityScale;
  private final double quantityScaleFactor;
  private final double priceScaleFactor;
  private int quotedInstrumentId;
  private double indexFeedUsdMark;
  private int status;
  private int collateralMarginPercentDiscount; // in basis
  private double collateralPremiumFactor; // converted to double
  private final int priceMultiplier;
  private final int quantityMultiplier;
  private double withdrawFee;
  private boolean isWithdrawFeePercent;
  private int withdrawFeeInstrument;
  private Sector sector;

  public Instrument(final int id, final String symbol, final String name, final short priceScale, final short quantityScale,
      final double usdMark, final int collateralMarginPercentDiscount, final double withdrawFee, final boolean isWithdrawFeePercent,
      final int withdrawFeeInstrument, final Sector sector
  ) {
    this.id = id;
    this.symbol = symbol;
    this.name = name;
    this.priceScale = priceScale;
    this.quantityScale = quantityScale;

    int value = 1;
    for (int i = 0; i < priceScale; i++)
      value *= 10;
    priceMultiplier = value;

    value = 1;
    for (int i = 0; i < quantityScale; i++)
      value *= 10;
    quantityMultiplier = value;

    if (usdMark > 0)
      this.indexFeedUsdMark = usdMark;
    else if (USD.equals(symbol))
      this.indexFeedUsdMark = 1.0; // 1.0
    else if (USDT.equals(symbol))
      this.indexFeedUsdMark = 1.0; // 1.0
    else if (USDC.equals(symbol))
      this.indexFeedUsdMark = 1.0; // 1.0
    else
      this.indexFeedUsdMark = 0.01; // avoid 0 causing NAN

    double quantityScaleFactorTmp = 1;
    for (int i = 0; i < quantityScale; i++)
      quantityScaleFactorTmp = quantityScaleFactorTmp * 0.1;
    this.quantityScaleFactor = quantityScaleFactorTmp;

    double priceScaleFactorTmp = 1;
    for (int i = 0; i < priceScale; i++)
      priceScaleFactorTmp = priceScaleFactorTmp * 0.1;
    this.priceScaleFactor = priceScaleFactorTmp;

    this.collateralMarginPercentDiscount = collateralMarginPercentDiscount;
    this.collateralPremiumFactor = collateralMarginPercentDiscount * .0001;

    this.withdrawFee = withdrawFee;
    this.isWithdrawFeePercent = isWithdrawFeePercent;
    this.withdrawFeeInstrument = withdrawFeeInstrument;
    this.sector = sector;
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

  // 1=10, 2=100, 3=1000...
  public final int getPriceMultiplier() {
    return priceMultiplier;
  }

  // 1=10, 2=100, 3=1000...
  public final int getQuantityMultiplier() {
    return quantityMultiplier;
  }

  public final double getCollateralPremiumFactor() {
    return collateralPremiumFactor;
  }

  public final short getPriceScale() {
    return priceScale;
  }

  public final short getQuantityScale() {
    return quantityScale;
  }

  public final int getQuotedInstrumentId() {
    return quotedInstrumentId;
  }

  public final void setQuotedInstrumentId(final int quotedInstrumentId) {
    this.quotedInstrumentId = quotedInstrumentId;
  }

  public final double getIndexFeedUsdMark() {
    return indexFeedUsdMark;
  }

  public final void setIndexFeedUsdMark(final double indexFeedUsdMark) {
    if (indexFeedUsdMark > 0)
      this.indexFeedUsdMark = indexFeedUsdMark;
  }

  public final int getStatus() {
    return status;
  }

  public final void setStatus(final int status) {
    this.status = status;
  }

  public final double getQuantityScaleFactor() {
    return quantityScaleFactor;
  }

  public final double getPriceScaleFactor() {
    return priceScaleFactor;
  }

  public final int getCollateralMarginPercentDiscount() {
    return collateralMarginPercentDiscount;
  }

  public final void setCollateralMarginPercentDiscount(final int collateralMarginPercentDiscount) {
    this.collateralMarginPercentDiscount = collateralMarginPercentDiscount;
  }

  public final double getWithdrawFee() {
    return withdrawFee;
  }

  public final void setWithdrawFee(final double withdrawFee) {
    this.withdrawFee = withdrawFee;
  }

  public final boolean isWithdrawFeePercent() {
    return isWithdrawFeePercent;
  }

  public final void setWithdrawFeePercent(final boolean withdrawFeePercent) {
    isWithdrawFeePercent = withdrawFeePercent;
  }

  public int getWithdrawFeeInstrument() {
    return withdrawFeeInstrument;
  }

  public void setWithdrawFeeInstrument(final int withdrawFeeInstrument) {
    this.withdrawFeeInstrument = withdrawFeeInstrument;
  }

  public Sector getSector() {
    return sector;
  }

  public void setSector(Sector sector) {
    this.sector = sector;
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

  // given a quantity and scale adjust to this scale
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
    s.append("Instrument [id=").append(id).append(SYMBOL_EQ).append(symbol).append(NAME_EQ).append(name).append(STATUS_EQ).append(status)
        .append(PRICESCALE_EQ).append(priceScale).append(QUANTITYSCALE_EQ).append(quantityScale).append(", quotedInstrumentId=")
        .append(quotedInstrumentId).append(COLLATERALMARGINPERCENTDISCOUNT_EQ).append(collateralMarginPercentDiscount).append("]");
    return s;
  }

}
