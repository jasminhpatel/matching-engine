package com.solfini.matchengine.liquidity.direct.aiGenerated;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared utilities for direct exchange clients. */
public final class ExternalExchangeUtils {
  private ExternalExchangeUtils() {}

  /** Returns the scaled average fill price, or zero for invalid or unrepresentable amounts. */
  public static long averageFillPrice(final String cumulativeQuote, final String cumulativeBase, final int priceScale) {
    if (priceScale < 0 || priceScale > 18 || cumulativeQuote == null || cumulativeBase == null) {
      return 0;
    }
    try {
      final BigDecimal quote = new BigDecimal(cumulativeQuote);
      final BigDecimal base = new BigDecimal(cumulativeBase);
      if (quote.signum() <= 0 || base.signum() <= 0) {
        return 0;
      }
      return quote.divide(base, priceScale, RoundingMode.HALF_UP).movePointRight(priceScale).longValueExact();
    } catch (ArithmeticException | NumberFormatException ignored) {
      return 0;
    }
  }
}
