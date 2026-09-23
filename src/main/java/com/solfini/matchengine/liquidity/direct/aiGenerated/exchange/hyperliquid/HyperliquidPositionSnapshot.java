package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.hyperliquid;

import com.fasterxml.jackson.databind.JsonNode;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/** Applies complete position snapshots without clearing another DEX's positions. */
public final class HyperliquidPositionSnapshot {
  private HyperliquidPositionSnapshot() {
  }

  public static void apply(final ExchangeSubscription subscription, final JsonNode state, final String dex) {
    synchronized (subscription.getPositionCache()) {
      apply(subscription.getPositionCache().keySet(), subscription::updatePosition, state, dex);
    }
  }

  static void apply(final Iterable<String> cachedKeys, final BiConsumer<String, Double> update,
      final JsonNode state, final String dex) {
    if (state == null || !state.path("assetPositions").isArray()) {
      throw new IllegalArgumentException("Missing Hyperliquid assetPositions snapshot");
    }
    final String prefix = dex.isEmpty() ? "" : dex.toUpperCase(Locale.ROOT) + ":";
    final Map<String, Double> next = new HashMap<>();
    for (final JsonNode entry : state.path("assetPositions")) {
      final JsonNode position = entry.path("position");
      final String coin = position.path("coin").asText().toUpperCase(Locale.ROOT);
      if (coin.isEmpty() || !belongsToDex(coin, prefix) || !position.hasNonNull("szi")) {
        throw new IllegalArgumentException("Invalid Hyperliquid position snapshot entry");
      }
      final double size = Double.parseDouble(position.path("szi").asText());
      if (!Double.isFinite(size)) {
        throw new IllegalArgumentException("Non-finite Hyperliquid position size");
      }
      next.put(coin, size);
      // LiquidityOrderRouter reads positions using ExternalSymbol.base + quote.
      next.put(coin + "USDC", size);
    }
    for (final String key : cachedKeys) {
      if (belongsToDex(key.toUpperCase(Locale.ROOT), prefix) && !next.containsKey(key)) {
        update.accept(key, 0.0);
      }
    }
    next.forEach(update);
  }

  private static boolean belongsToDex(final String key, final String prefix) {
    return prefix.isEmpty() ? !key.contains(":") : key.startsWith(prefix);
  }
}
