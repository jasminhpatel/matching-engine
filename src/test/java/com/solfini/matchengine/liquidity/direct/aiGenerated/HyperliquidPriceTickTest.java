package com.solfini.matchengine.liquidity.direct.aiGenerated;

import org.junit.Assert;
import org.junit.Test;

/**
 * Hyperliquid accepts a price only when it has at most 5 significant figures and at most
 * (6 - szDecimals) decimal places for perps, or (8 - szDecimals) for spot. Prices above 100000
 * must be integers. Buys floor onto that tick and sells ceil.
 */
public class HyperliquidPriceTickTest {

  @Test
  public void silverPerpFloorsBuyAndCeilsSell() {
    // xyz:SILVER szDecimals=2, priceScale=4. 64.3405 has 6 significant figures; tick is 0.001.
    assertWire(643405L, 4, 2, true, true, "64.34");
    assertWire(643405L, 4, 2, true, false, "64.341");
  }

  @Test
  public void priceAlreadyOnTickIsUnchanged() {
    assertWire(643410L, 4, 2, true, true, "64.341");
    assertWire(643410L, 4, 2, true, false, "64.341");
    assertWire(643400L, 4, 2, true, false, "64.34");
    assertWire(123450L, 2, 0, true, true, "1234.5");
  }

  @Test
  public void buyFloorsAndSellCeilsWhenOffTick() {
    assertWire(643409L, 4, 2, true, true, "64.34");
    assertWire(643401L, 4, 2, true, false, "64.341");
  }

  @Test
  public void coarserStoredScaleIsAlreadyOnAFinerTick() {
    // 64 has no fractional digits, so a 0.001 tick cannot move it.
    assertWire(64L, 0, 2, true, true, "64");
    assertWire(64L, 0, 2, true, false, "64");
  }

  @Test
  public void significantFiguresTightenAroundPowersOfTen() {
    // 9.9999 has 5 significant figures. 10.0001 has 6, so the tick widens to 0.001.
    assertWire(99999L, 4, 0, true, true, "9.9999");
    assertWire(100001L, 4, 0, true, true, "10");
    assertWire(100001L, 4, 0, true, false, "10.001");
  }

  @Test
  public void documentedPerpExamples() {
    // 1234.5 is valid. 1234.56 has too many significant figures.
    assertWire(123456L, 2, 0, true, true, "1234.5");
    assertWire(123456L, 2, 0, true, false, "1234.6");

    // szDecimals=1: 0.01234 is valid, 0.012345 has more than 5 decimal places.
    assertWire(1234L, 5, 1, true, true, "0.01234");
    assertWire(12345L, 6, 1, true, true, "0.01234");
    assertWire(12345L, 6, 1, true, false, "0.01235");

    // szDecimals=0: 0.001234 is valid, 0.0012345 has more than 6 decimal places.
    assertWire(1234L, 6, 0, true, true, "0.001234");
    assertWire(12345L, 7, 0, true, true, "0.001234");
    assertWire(12345L, 7, 0, true, false, "0.001235");
  }

  @Test
  public void ethPerpUsesTenthTick() {
    // szDecimals=4 leaves at most 2 price decimals, and 5 significant figures makes the tick 0.1.
    assertWire(256735L, 2, 4, true, true, "2567.3");
    assertWire(256735L, 2, 4, true, false, "2567.4");
  }

  @Test
  public void btcPerpRoundsFractionalPricesToIntegers() {
    // szDecimals=5. 97000.5 has 6 significant figures.
    assertWire(970005L, 1, 5, true, true, "97000");
    assertWire(970005L, 1, 5, true, false, "97001");
  }

  @Test
  public void pricesAboveOneHundredThousandMustBeIntegers() {
    assertWire(1000004L, 1, 5, true, true, "100000");
    assertWire(1000004L, 1, 5, true, false, "100001");
    assertWire(100000L, 0, 5, true, true, "100000");
    assertWire(123456L, 0, 5, true, true, "123456");
    assertWire(123456L, 0, 5, true, false, "123456");
    assertWire(1234567L, 1, 5, true, true, "123456");
    assertWire(1234567L, 1, 5, true, false, "123457");
  }

  @Test
  public void spotAllowsTwoMorePriceDecimalsThanPerps() {
    // 0.00012345 fits 8 spot decimals and 5 significant figures, but not 6 perp decimals.
    assertWire(12345L, 8, 0, false, true, "0.00012345");
    assertWire(12345L, 8, 0, true, true, "0.000123");
    assertWire(12345L, 8, 0, true, false, "0.000124");

    // 1.23456789 has 8 significant figures. Spot still keeps only 5.
    assertWire(123456789L, 8, 0, false, true, "1.2345");
    assertWire(123456789L, 8, 0, false, false, "1.2346");
  }

  @Test
  public void sizeScaleAboveThePriceCapForcesAnIntegerTick() {
    // Perp max decimals are 6 - 8 = 0, so 1.2 can only be sent as an integer.
    assertWire(12L, 1, 8, true, true, "1");
    assertWire(12L, 1, 8, true, false, "2");
  }

  @Test
  public void nonPositivePriceIsLeftUnchanged() {
    Assert.assertEquals(0L, HyperliquidFastClient.toHyperliquidPrice(0L, 4, 2, true, true));
    Assert.assertEquals(-643405L, HyperliquidFastClient.toHyperliquidPrice(-643405L, 4, 2, true, true));
    Assert.assertEquals(643405L, HyperliquidFastClient.toHyperliquidPrice(643405L, -1, 2, true, true));
  }

  private static void assertWire(final long amount, final int priceScale, final int szDecimals, final boolean futures,
      final boolean isBuy, final String expectedWire) {
    final long snapped = HyperliquidFastClient.toHyperliquidPrice(amount, priceScale, szDecimals, futures, isBuy);
    Assert.assertEquals(expectedWire, HyperliquidFastClient.toWireNumber(snapped, priceScale));
  }
}
