package com.solfini.instrument;

import uk.co.real_logic.artio.fields.DecimalFloat;

public class StakeInterestRate {
  private final int assetId;
  private final int securityId;
  private final DecimalFloat rate;

  public StakeInterestRate(int assetId, int securityId, DecimalFloat rate) {
    this.assetId = assetId;
    this.securityId = securityId;
    this.rate = rate;
  }

  public int getAssetId() {
    return assetId;
  }

  public int getSecurityId() {
    return securityId;
  }

  public DecimalFloat getRate() {
    return rate;
  }
}
