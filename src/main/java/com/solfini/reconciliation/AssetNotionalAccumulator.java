package com.solfini.reconciliation;

final class AssetNotionalAccumulator {

  private final int usdcId;
  private final int usdtId;
  private final int xusdcId;

  private double totalUserValue;
  private double totalMarketMakerValue;
  private double usdcUserValue;
  private double usdcMmValue;
  private double usdtUserValue;
  private double usdtMmValue;
  private double xusdcUserValue;
  private double xusdcMmValue;

  AssetNotionalAccumulator(final int usdcId, final int usdtId, final int xusdcId) {
    this.usdcId = usdcId;
    this.usdtId = usdtId;
    this.xusdcId = xusdcId;
  }

  void addPosition(final boolean isMarketMaker, final int instrumentId, final double usdValue) {
    if (isMarketMaker) {
      totalMarketMakerValue += usdValue;
    } else {
      totalUserValue += usdValue;
    }
    if (instrumentId == usdcId) {
      if (isMarketMaker) {
        usdcMmValue += usdValue;
      } else {
        usdcUserValue += usdValue;
      }
    } else if (instrumentId == usdtId) {
      if (isMarketMaker) {
        usdtMmValue += usdValue;
      } else {
        usdtUserValue += usdValue;
      }
    } else if (instrumentId == xusdcId) {
      if (isMarketMaker) {
        xusdcMmValue += usdValue;
      } else {
        xusdcUserValue += usdValue;
      }
    }
  }

  double getTotalUserValue() {
    return totalUserValue;
  }

  double getTotalMarketMakerValue() {
    return totalMarketMakerValue;
  }

  double getUsdcRequiredBacking() {
    return usdcUserValue + usdcMmValue;
  }

  double getUsdtRequiredBacking() {
    return usdtUserValue + usdtMmValue;
  }

  double getXusdcRequiredBacking() {
    return xusdcUserValue + xusdcMmValue;
  }

  double getUsdcUserValue() {
    return usdcUserValue;
  }

  double getUsdcMmValue() {
    return usdcMmValue;
  }

  double getUsdtUserValue() {
    return usdtUserValue;
  }

  double getUsdtMmValue() {
    return usdtMmValue;
  }

  double getXusdcUserValue() {
    return xusdcUserValue;
  }

  double getXusdcMmValue() {
    return xusdcMmValue;
  }
}
