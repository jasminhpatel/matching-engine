package com.solfini.util.affiliate;

import java.util.Arrays;
import java.util.Date;

class Record {

  public static final int MAX_INSTRUMENTS = 1024;

  private final int userId;
  private final String username;
  private final Date startDate;
  private final int feeTier;
  private final String referralCode;
  private final String referredByCode;
  private final int minimumFeeTier;
  private final int upgradeFeeTier;
  private final int lmm;
  private final int individual;

  private double volume = 0;
  private long[] fees = new long[MAX_INSTRUMENTS];

  private final static double[] volumes = {0.2, 2.5, 7.5, 22.5, 50.0, 100.0, 200.0, 400.0, 750.0, 1000.0};

  public Record(final int userId, final String username, final Date startDate, final int feeTier,
      final String referralCode, final String referredByCode, final int minimumFeeTier, final int upgradeFeeTier,
      final int lmm, final int individual) {
    this.userId = userId;
    this.username = username;
    this.startDate = startDate;
    this.feeTier = feeTier;
    this.referralCode = referralCode;
    this.referredByCode = referredByCode;
    this.minimumFeeTier = minimumFeeTier;
    this.upgradeFeeTier = upgradeFeeTier;
    this.lmm = lmm;
    this.individual = individual;
  }

  public final int getUserId() {
    return userId;
  }

  public final String getUsername() {
    return username;
  }

  public final Date getStartDate() {
    return startDate;
  }

  public final int getFeeTier() {
    return feeTier;
  }

  public final String getReferralCode() {
    return referralCode;
  }

  public final String getReferredByCode() {
    return referredByCode;
  }

  public final int getMinimumFeeTier() {
    return minimumFeeTier;
  }

  public final int getUpgradeFeeTier() {
    return upgradeFeeTier;
  }

  private final boolean isLmm() {
    return lmm != 0;
  }

  public final boolean isIndividual() {
    return individual == 1;
  }

  public final double getVolume() {
    return volume;
  }

  public final int getNewFeeTier() {
    if (isLmm()) {
      return volumes.length + 1;
    }

    for (int i = 0; i < volumes.length; ++i) {
      if (volume < volumes[i] * 1_000_000d) {
        return ((i + upgradeFeeTier) < minimumFeeTier) ? minimumFeeTier : (i + upgradeFeeTier);
      }
    }

    return Math.max(volumes.length, minimumFeeTier);
  }

  public final void addVolume(final double volume) {
    this.volume += volume;
  }

  public final void addFees(final int instrumentId, final long amount) {
    if (instrumentId >= fees.length) {
        fees = Arrays.copyOf(fees, instrumentId + 16);
      }

    fees[instrumentId] += amount;
  }

  @Override
  public String toString() {
    return "Record(userId=" + userId + ", username=" + username + ", feeTier=" + feeTier
        + ", refferalCode=" + referralCode + ", referredByCode=" + referredByCode
        + ", lmm=" + lmm + ", volume=" + volume + ", newFeeTier=" + getNewFeeTier() + ")";
    }
}
