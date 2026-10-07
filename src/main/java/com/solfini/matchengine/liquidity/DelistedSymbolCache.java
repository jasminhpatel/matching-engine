package com.solfini.matchengine.liquidity;

import static com.solfini.common.Constants.ERROR_LOG;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.liquidity.direct.aiGenerated.ExternalExchangeClient;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Delisted, suspended and to-be-delisted symbols of all external exchanges, refreshed from each subscription's client.
 */
public class DelistedSymbolCache {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(DelistedSymbolCache.class);
  private static final long REFRESH_MINUTES = 10;
  // key <lower(exchange_segment_symbol), DelistedSymbol>
  private static final ConcurrentHashMap<String, DelistedSymbol> DELISTED = new ConcurrentHashMap<>();
  // segments (exchange_spot/futures) already scheduled
  private static final Set<String> STARTED = ConcurrentHashMap.newKeySet();
  private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
    final Thread thread = new Thread(r, "delistedSymbolRefresh");
    thread.setDaemon(true);
    return thread;
  });

  /**
   * Called from the exchange client's start(): loads the delisted symbols now and then every REFRESH_MINUTES.
   * Calling it again for the same exchange segment does nothing.
   */
  public static void start(final ExchangeSubscription subscription) {
    if (!STARTED.add(getSegmentPrefix(subscription))) {
      return;
    }
    SCHEDULER.scheduleWithFixedDelay(() -> {
      try {
        refresh(subscription);
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }, 0, REFRESH_MINUTES, TimeUnit.MINUTES);
  }

  private static String getSegmentPrefix(final ExchangeSubscription subscription) {
    return (subscription.getExchange() + "_" + (subscription.isFuturesEnabled() ? "1" : "0") + "_").toLowerCase();
  }

  public static void refresh(final ExchangeSubscription subscription) {
    final ExternalExchangeClient client = subscription.getClient();
    if (client == null) {
      return;
    }
    final List<DelistedSymbol> symbols = client.getDelistedSymbols();
    if (symbols == null) { // request failed, keep the last known state
      return;
    }
    final String segmentPrefix = getSegmentPrefix(subscription);
    final long now = System.currentTimeMillis();
    final Set<String> current = new HashSet<>();
    for (final DelistedSymbol symbol : symbols) {
      final String key = symbol.getKey();
      current.add(key);
      final DelistedSymbol previous = DELISTED.put(key, symbol);
      if (previous != null) {
        symbol.setDetectedAt(previous.getDetectedAt());
      }
      if (symbol.isUpcoming() && symbol.getDelistTime() > now
          && (previous == null || previous.getDelistTime() != symbol.getDelistTime())) {
        LOGGER.warn("Delisting scheduled: " + symbol);
      }
      if (previous != null && symbol.isTradingDisabled() && !previous.isTradingDisabled()) {
        LOGGER.warn("Trading stopped: " + symbol);
      }
    }
    for (final Map.Entry<String, DelistedSymbol> entry : DELISTED.entrySet()) {
      if (entry.getKey().startsWith(segmentPrefix) && !current.contains(entry.getKey())) {
        DELISTED.remove(entry.getKey());
        LOGGER.warn("No longer delisted or suspended: " + entry.getValue());
      }
    }
  }

  /** Adds or replaces one symbol, e.g. from a WebSocket push, and logs the change. */
  public static void update(final DelistedSymbol symbol) {
    final DelistedSymbol previous = DELISTED.put(symbol.getKey(), symbol);
    if (previous != null) {
      symbol.setDetectedAt(previous.getDetectedAt());
    }
    if (symbol.isUpcoming() && symbol.getDelistTime() > System.currentTimeMillis()
        && (previous == null || previous.getDelistTime() != symbol.getDelistTime())) {
      LOGGER.warn("Delisting scheduled: " + symbol);
    }
    if (symbol.isTradingDisabled() && (previous == null || !previous.isTradingDisabled())) {
      LOGGER.warn("Trading stopped: " + symbol);
    }
  }

  /** Removes one symbol that is trading normally again, e.g. from a WebSocket push. */
  public static void remove(final String exchange, final boolean futures, final String symbol) {
    final DelistedSymbol removed = DELISTED.remove(DelistedSymbol.getKey(exchange, futures, symbol));
    if (removed != null) {
      LOGGER.warn("No longer delisted or suspended: " + removed);
    }
  }

  public static DelistedSymbol get(final String exchange, final boolean futures, final String symbol) {
    return DELISTED.get(DelistedSymbol.getKey(exchange, futures, symbol));
  }

  /** True when trading has stopped on the exchange for this symbol (delisted or suspended). */
  public static boolean isDelisted(final String exchange, final boolean futures, final String symbol) {
    final DelistedSymbol delisted = get(exchange, futures, symbol);
    return delisted != null && delisted.isTradingDisabled();
  }

  public static Collection<DelistedSymbol> getAll() {
    return DELISTED.values();
  }
}
