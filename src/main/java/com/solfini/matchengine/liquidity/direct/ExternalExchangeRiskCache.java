package com.solfini.matchengine.liquidity.direct;

import java.util.concurrent.ConcurrentHashMap;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.message.internal.Order;

public class ExternalExchangeRiskCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalExchangeRiskCache.class);

  private static final ConcurrentHashMap<String, ExchangeSubscription> SUBSCRIPTIONS = new ConcurrentHashMap<>();

  public static final String BINANCE = "binance";
  public static final String BYBIT = "bybit";

  public static void onLoad(final ExchangeSubscription subscription) {
    final String key = (subscription.getExchange() + "_" + (subscription.isFuturesEnabled() ? "1" : "0")).toLowerCase();
    if (subscription.getLastUsedProxy() == null || subscription.getLastUsedProxy().isEmpty()) {
      subscription.setLastUsedProxy(ExternalExchangeUtil.getStickyProxy(subscription.getId()));
    }
    SUBSCRIPTIONS.put(key, subscription);
  }

  public static ExchangeSubscription get(final String exchange, final boolean futuresEnabled) {
    final String key = (exchange + "_" + (futuresEnabled ? "1" : "0")).toLowerCase();
    return SUBSCRIPTIONS.get(key);
  }

  public static void main(final String[] args) {
    final Order order = new Order();
    order.setSymbol("BTC/USD");
    final int symbolSeparatorIndex = order.getSymbol().indexOf("/");
    final String baseSymbol = order.getSymbol().substring(0, symbolSeparatorIndex);
    final String quoteSymbol = order.getSymbol().substring(symbolSeparatorIndex + 1);
    System.out.printf("baseSymbol: %s, quoteSymbol: %s%n", baseSymbol, quoteSymbol);
  }

}
