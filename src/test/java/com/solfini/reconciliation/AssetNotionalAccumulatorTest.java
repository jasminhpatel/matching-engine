package com.solfini.reconciliation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AssetNotionalAccumulatorTest {

  private static final int USDC_ID = 1;
  private static final int USDT_ID = 2;
  private static final int XUSDC_ID = 3;

  @Test
  public void addPosition_regularUser_addsToUsdcBucketAndCombinedTotal() {
    AssetNotionalAccumulator accumulator = new AssetNotionalAccumulator(USDC_ID, USDT_ID, XUSDC_ID);

    accumulator.addPosition(false, USDC_ID, 1500.0);

    assertEquals(1500.0, accumulator.getUsdcRequiredBacking(), 0.0001);
    assertEquals(1500.0, accumulator.getTotalUserValue(), 0.0001);
    assertEquals(0.0, accumulator.getTotalMarketMakerValue(), 0.0001);
    assertEquals(0.0, accumulator.getUsdtRequiredBacking(), 0.0001);
  }

  @Test
  public void addPosition_marketMaker_addsToMmBucketAndAssetBucket() {
    AssetNotionalAccumulator accumulator = new AssetNotionalAccumulator(USDC_ID, USDT_ID, XUSDC_ID);

    accumulator.addPosition(true, USDC_ID, 200.0);

    assertEquals(200.0, accumulator.getUsdcRequiredBacking(), 0.0001);
    assertEquals(200.0, accumulator.getTotalMarketMakerValue(), 0.0001);
    assertEquals(0.0, accumulator.getTotalUserValue(), 0.0001);
  }

  @Test
  public void addPosition_mixedAssets_keepsBucketsIndependent() {
    AssetNotionalAccumulator accumulator = new AssetNotionalAccumulator(USDC_ID, USDT_ID, XUSDC_ID);

    accumulator.addPosition(false, USDC_ID, 1000.0);
    accumulator.addPosition(false, USDT_ID, 500.0);
    accumulator.addPosition(true, USDC_ID, 50.0);

    assertEquals(1050.0, accumulator.getUsdcRequiredBacking(), 0.0001);
    assertEquals(500.0, accumulator.getUsdtRequiredBacking(), 0.0001);
    assertEquals(1550.0, accumulator.getTotalUserValue() + accumulator.getTotalMarketMakerValue(), 0.0001);
  }

  @Test
  public void addPosition_unknownInstrumentId_onlyAffectsCombinedTotal() {
    AssetNotionalAccumulator accumulator = new AssetNotionalAccumulator(USDC_ID, USDT_ID, XUSDC_ID);

    accumulator.addPosition(false, 999, 75.0);

    assertEquals(75.0, accumulator.getTotalUserValue(), 0.0001);
    assertEquals(0.0, accumulator.getUsdcRequiredBacking(), 0.0001);
    assertEquals(0.0, accumulator.getUsdtRequiredBacking(), 0.0001);
    assertEquals(0.0, accumulator.getXusdcRequiredBacking(), 0.0001);
  }

  @Test
  public void addPosition_xusdc_addsToXusdcBucket() {
    AssetNotionalAccumulator accumulator = new AssetNotionalAccumulator(USDC_ID, USDT_ID, XUSDC_ID);

    accumulator.addPosition(false, XUSDC_ID, 300.0);

    assertEquals(300.0, accumulator.getXusdcRequiredBacking(), 0.0001);
  }

  @Test
  public void getUsdcUserValueAndMmValue_reportSeparatedBucketsThatSumToRequiredBacking() {
    AssetNotionalAccumulator accumulator = new AssetNotionalAccumulator(USDC_ID, USDT_ID, XUSDC_ID);

    accumulator.addPosition(false, USDC_ID, 1450.0);
    accumulator.addPosition(true, USDC_ID, 50.0);

    assertEquals(1450.0, accumulator.getUsdcUserValue(), 0.0001);
    assertEquals(50.0, accumulator.getUsdcMmValue(), 0.0001);
    assertEquals(1500.0, accumulator.getUsdcRequiredBacking(), 0.0001);
  }
}
