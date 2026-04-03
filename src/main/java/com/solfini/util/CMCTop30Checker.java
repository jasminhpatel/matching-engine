package com.solfini.util;

import com.solfini.common.Context;
import java.util.Set;

public class CMCTop30Checker {

  private static final Set<String> TOP_30_CMC = Set.of(
      "BTC", "ETH", "USDT", "BNB", "SOL", "XRP", "USDC", "DOGE", "ADA", "TRX",
      "TON", "AVAX", "SHIB", "DOT", "WBTC", "BCH", "UNI", "LTC", "LINK", "NEAR",
      "DAI", "MATIC", "XLM", "APT", "HBAR", "OKB", "FIL", "CRO", "ARB", "ICP",
      // test tokens
      "T_BTC", "T_ETH", "T_USDT", "T_BNB", "T_SOL", "T_XRP", "T_USDC", "T_DOGE", "T_ADA", "T_TRX",
      "T_TON", "T_AVAX", "T_SHIB", "T_DOT", "T_WBTC", "T_BCH", "T_UNI", "T_LTC", "T_LINK", "T_NEAR",
      "T_DAI", "T_MATIC", "T_XLM", "T_APT", "T_HBAR", "T_OKB", "T_FIL", "T_CRO", "T_ARB", "T_ICP"
  );

  public static boolean isTop30(String symbol) {
    return TOP_30_CMC.contains(symbol.toUpperCase());
  }

  public static boolean withinSmallOrderValue(final String symbol, final double orderValue) {
    if (isTop30(symbol) && orderValue < Context.getMinTopThirtyOrderValue()) {
      return true;
    } else {
      return orderValue < Context.getMinOrderValue();
    }
  }

  public static double getBPS(final String symbol) {
    if (isTop30(symbol)) {
      return 0.002D;
    } else {
      return 0.003D;
    }
  }

  public static void main(String[] args) {
    for (String symbol : TOP_30_CMC.stream().toList()) {
      System.out.println(symbol + "\t\t:" +getBPS(symbol));
    }
    String symbol = "BRETT";
    System.out.println(symbol + "\t\t:" +getBPS(symbol));
  }
}
