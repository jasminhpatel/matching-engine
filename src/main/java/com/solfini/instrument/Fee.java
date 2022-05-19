package com.solfini.instrument;

import com.solfini.common.Appendable;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.PropertyReader;

/**
 *
 * @author Chris Mack
 *
 */
public class Fee implements Appendable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(Fee.class);

  private int instrumentPairId;
  private int feeInstrumentId;
  private int feeAmount;
  private FeeType feeType;
  private MakerTaker makerTaker;
  private int tier;
  private FeeCalc feeCalc;
  private User collectingUser;
  private boolean isPaidToInsurance; // internal use only
  public static final Fee EMPTY_FEE = new Fee();
  public static final int LIQUIDATION_FEE_ID = 999_999;
  public static final int LIQUIDATION_FEE_RATE = PropertyReader.getProperty("LIQUIDATION_FEE_RATE", 37_50); // was 50_00 aka 50bps
  public static final int LIQUIDATION_USDC_ID = 1;
  public static final Fee LIQUIDATION_FEE = new Fee(0, LIQUIDATION_USDC_ID, LIQUIDATION_FEE_RATE, FeeType.PERCENT, MakerTaker.ALL, 0, true); // 50bps

  public Fee() {
    feeType = FeeType.ABSOLUTE;
    feeCalc = new FeeCalcAbsolute(0);
    makerTaker = MakerTaker.ALL;
  }

  public Fee(final int instrumentPairId, final int feeInstrumentId, final int feeAmount, final FeeType feeType, final MakerTaker makerTaker,
      final int tier, boolean isPaidToInsurance) {
    this.instrumentPairId = instrumentPairId;
    this.feeInstrumentId = feeInstrumentId > 0 ? feeInstrumentId : 1;
    this.feeAmount = feeAmount;
    this.feeType = feeType;
    this.makerTaker = makerTaker;
    this.tier = tier;
    this.isPaidToInsurance = isPaidToInsurance;
    if (FeeType.PERCENT == feeType)
      feeCalc = new FeeCalcPercent(feeAmount);
    else
      feeCalc = new FeeCalcAbsolute(feeAmount);
  }

  public Fee(final FeeAdminMessage feeAdminMessage) {
    this.instrumentPairId = feeAdminMessage.getAssetId();
    this.feeInstrumentId = feeAdminMessage.getFeeInstrumentId() > 0 ? feeAdminMessage.getFeeInstrumentId() : 1;
    this.feeAmount = feeAdminMessage.getFee();
    this.feeType = feeAdminMessage.getFeeType();
    this.makerTaker = feeAdminMessage.getMakerTaker();
    this.tier = feeAdminMessage.getTier();
    if (FeeType.PERCENT == feeType)
      feeCalc = new FeeCalcPercent(feeAmount);
    else
      feeCalc = new FeeCalcAbsolute(feeAmount);
  }

  public final int getInstrumentPairId() {
    return instrumentPairId;
  }

  public final void setInstrumentPairId(final int instrumentPairId) {
    this.instrumentPairId = instrumentPairId;
  }

  public final int getFee() {
    return feeAmount;
  }

  public final void setFee(final int fee) {
    this.feeAmount = fee;
  }

  public final int getFeeInstrumentId() {
    return feeInstrumentId;
  }

  public final void setFeeInstrumentId(final int feeInstrumentId) {
    this.feeInstrumentId = feeInstrumentId;
  }

  public final FeeType getFeeType() {
    return feeType;
  }

  public final void setFeeType(final FeeType feeType) {
    this.feeType = feeType;
  }

  public final MakerTaker getMakerTaker() {
    return makerTaker;
  }

  public final void setMakerTaker(final MakerTaker makerTaker) {
    this.makerTaker = makerTaker;
  }

  public final int getTier() {
    return tier;
  }

  public final void setTier(final int tier) {
    this.tier = tier;
  }

  public final boolean isPaidToInsurance() {
    return isPaidToInsurance;
  }

  public final void setPaidToInsurance(final boolean isPaidToInsurance) {
    this.isPaidToInsurance = isPaidToInsurance;
  }

  public final User getCollectingUser() {
    return collectingUser;
  }

  public final void setCollectingUser(final User collectingUser) {
    this.collectingUser = collectingUser;
  }

  public final long calc(final long quantity) {
    return feeCalc.calc(quantity);
  }

  public final double calcUsdFee(final double notionalUsd) {
    return feeCalc.calcUsdFee(notionalUsd);
  }

  public final FeeCalc getFeeCalc() {
    return feeCalc;
  }

  public final void setFeeCalc(final FeeCalc feeCalc) {
    this.feeCalc = feeCalc;
  }

  public final void setFeeCalc(final int type) {
    if (FeeType.PERCENT.value() == type) {
      feeCalc = new FeeCalcPercent(feeAmount);
    } else if (FeeType.ABSOLUTE.value() == type) {
      feeCalc = new FeeCalcAbsolute(feeAmount);
    }
  }

  // must be called from the matching engine thread
  public final void transferToExchange(final long feeQuantity) {
    if (collectingUser == null)
      collectingUser = UserCache.getExchangeUser();
    collectingUser.addPosition(feeInstrumentId, feeQuantity);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_8, TRANSFERTOEXCHANGE_FEEID_EQ, feeInstrumentId, VALUE_EQ, feeQuantity, ISPAIDTOINSURANCE_EQ, isPaidToInsurance,
          COLLECTINGUSER_EQ, collectingUser.getId());
    }
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("Fee [instrumentPairId=").append(instrumentPairId).append(FEE_EQ).append(feeAmount).append(FEEINSTRUMENTID_EQ)
        .append(feeInstrumentId).append(FEETYPE_EQ).append(feeType).append(MAKERTAKER_EQ).append(makerTaker).append(TIER_EQ).append(tier)
        .append("]");
  }
}


