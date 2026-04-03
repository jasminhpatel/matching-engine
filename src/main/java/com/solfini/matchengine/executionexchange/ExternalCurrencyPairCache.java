package com.solfini.matchengine.executionexchange;

import org.knowm.xchange.currency.CurrencyPair;

import java.util.concurrent.ConcurrentHashMap;

public class ExternalCurrencyPairCache {
  private static final ConcurrentHashMap<String, CurrencyPair> CURRENCY_PAIR_MAP = new ConcurrentHashMap<>();

  public static CurrencyPair get(final String base, final String quoted) {
    final String key = (base + "/" + quoted).toLowerCase();
    return CURRENCY_PAIR_MAP.computeIfAbsent(key, v -> new CurrencyPair(base, quoted));
  }
}
