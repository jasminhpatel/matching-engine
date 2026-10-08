package com.solfini.matchengine.liquidity;

import static com.solfini.common.Constants.ERROR_LOG;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.executionexchange.ExternalInstrumentCache;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.direct.aiGenerated.ExternalExchangeClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Delisted, suspended and to-be-delisted symbols of all external exchanges, refreshed from each subscription's client.
 */
public class DelistedSymbolCache {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(DelistedSymbolCache.class);
  private static final long REFRESH_MINUTES = 10;
  // a pushed symbol the REST snapshot doesn't report yet is kept this long before the REST state wins
  static final long PUSH_GRACE_MILLIS = TimeUnit.MINUTES.toMillis(60);
  // key <lower(exchange_segment_symbol), DelistedSymbol>
  private static final ConcurrentHashMap<String, DelistedSymbol> DELISTED = new ConcurrentHashMap<>();
  // key <lower(exchange_segment_symbol), push time> of symbols added by a WebSocket push and not yet seen in a REST snapshot
  private static final ConcurrentHashMap<String, Long> PUSHED_AT = new ConcurrentHashMap<>();
  // segments (exchange_spot/futures) already scheduled
  private static final Set<String> STARTED = ConcurrentHashMap.newKeySet();
  // segments that had at least one successful refresh
  private static final Set<String> LOADED = ConcurrentHashMap.newKeySet();
  // key <lower(exchange_segment_symbol), instruments this cache set not tradable>, restored when trading resumes
  private static final ConcurrentHashMap<String, List<ExternalSymbol>> DISABLED = new ConcurrentHashMap<>();
  // quotes the router routes to, used to find the base of a symbol that comes without one
  private static final String[] ROUTED_QUOTES = {"USDT", "USDC", "USD"};

  /** Loads the delisted symbols now and every REFRESH_MINUTES, one thread per segment; repeat calls do nothing. */
  public static void start(final ExchangeSubscription subscription) {
    final String segmentPrefix = getSegmentPrefix(subscription);
    if (!STARTED.add(segmentPrefix)) {
      return;
    }
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      final Thread thread = new Thread(r, "delistedSymbolRefresh-" + segmentPrefix.substring(0, segmentPrefix.length() - 1));
      thread.setDaemon(true);
      return thread;
    });
    scheduler.scheduleWithFixedDelay(() -> {
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
    apply(getSegmentPrefix(subscription), symbols, System.currentTimeMillis());
  }

  /** Replaces the segment with a REST snapshot; a symbol pushed by WebSocket is kept for PUSH_GRACE_MILLIS. */
  static void apply(final String segmentPrefix, final List<DelistedSymbol> symbols, final long now) {
    // on the first load every entry is new: one summary line instead of a warning per symbol
    final boolean firstLoad = !LOADED.contains(segmentPrefix);
    final Set<String> current = new HashSet<>();
    for (final DelistedSymbol symbol : symbols) {
      current.add(symbol.getKey());
      PUSHED_AT.remove(symbol.getKey()); // REST reports it now, the REST state is used from here on
      store(symbol, now, !firstLoad);
    }
    if (firstLoad) {
      logFirstLoad(segmentPrefix, symbols, now);
    }
    for (final Map.Entry<String, DelistedSymbol> entry : DELISTED.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(segmentPrefix) || current.contains(key)) {
        continue;
      }
      final Long pushedAt = PUSHED_AT.get(key);
      if (pushedAt != null && now - pushedAt < PUSH_GRACE_MILLIS) {
        continue;
      }
      PUSHED_AT.remove(key);
      if (DELISTED.remove(key, entry.getValue())) {
        restoreTradable(key);
        LOGGER.warn("No longer delisted or suspended: " + entry.getValue());
      }
    }
    LOADED.add(segmentPrefix);
  }

  /** Adds or replaces one symbol from a WebSocket push and logs the change. */
  public static void update(final DelistedSymbol symbol) {
    final long now = System.currentTimeMillis();
    PUSHED_AT.put(symbol.getKey(), now);
    store(symbol, now, true);
  }

  /** Stores one symbol and logs the change, for both REST and WebSocket; logNew=false skips new-entry logs. */
  private static void store(final DelistedSymbol symbol, final long now, final boolean logNew) {
    final DelistedSymbol previous = DELISTED.get(symbol.getKey());
    if (previous != null) {
      symbol.setDetectedAt(previous.getDetectedAt()); // before the put, so readers never see the new detection time
    }
    DELISTED.put(symbol.getKey(), symbol);
    syncTradable(symbol);
    if (symbol.isUpcoming() && symbol.getDelistTime() > now
        && (previous == null ? logNew : previous.getDelistTime() != symbol.getDelistTime())) {
      LOGGER.warn("Delisting scheduled: " + symbol);
    }
    if (symbol.isTradingDisabled() && (previous == null ? logNew : !previous.isTradingDisabled())) {
      LOGGER.warn("Trading stopped: " + symbol);
    }
  }

  /** One line for a segment's first load: how many are stopped, and which delistings are scheduled. */
  private static void logFirstLoad(final String segmentPrefix, final List<DelistedSymbol> symbols, final long now) {
    int stopped = 0;
    final StringBuilder scheduled = new StringBuilder();
    int scheduledCount = 0;
    for (final DelistedSymbol symbol : symbols) {
      if (symbol.isTradingDisabled()) {
        stopped++;
      } else if (symbol.isUpcoming() && symbol.getDelistTime() > now) {
        scheduledCount++;
        scheduled.append(scheduledCount == 1 ? "" : ", ").append(symbol.getSymbol()).append('@')
            .append(Instant.ofEpochMilli(symbol.getDelistTime()));
      }
    }
    LOGGER.warn("Delisted symbols loaded for " + segmentPrefix + " trading stopped: " + stopped + ", delisting scheduled: "
        + scheduledCount + (scheduledCount > 0 ? " [" + scheduled + "]" : ""));
  }

  /** Removes one symbol that is trading normally again, e.g. from a WebSocket push. */
  public static void remove(final String exchange, final boolean futures, final String symbol) {
    final String key = DelistedSymbol.getKey(exchange, futures, symbol);
    PUSHED_AT.remove(key);
    final DelistedSymbol removed = DELISTED.remove(key);
    restoreTradable(key);
    if (removed != null) {
      LOGGER.warn("No longer delisted or suspended: " + removed);
    }
  }

  /** While trading is stopped, sets the instrument not tradable so LiquidityOrderRouter skips it; runs every refresh. */
  private static void syncTradable(final DelistedSymbol symbol) {
    if (!symbol.isTradingDisabled()) {
      restoreTradable(symbol.getKey());
      return;
    }
    for (final ExternalSymbol external : findExternalSymbols(symbol)) {
      if (external.isTradable()) {
        external.setTradable(false);
        DISABLED.computeIfAbsent(symbol.getKey(), k -> new CopyOnWriteArrayList<>()).add(external);
        LOGGER.info("Not tradable while delisted: " + symbol.getKey());
      }
    }
  }

  /** Sets back tradable the instruments syncTradable() disabled for this key. */
  private static void restoreTradable(final String key) {
    final List<ExternalSymbol> disabled = DISABLED.remove(key);
    if (disabled != null) {
      disabled.forEach(external -> external.setTradable(true));
      LOGGER.info("Tradable again: " + key);
    }
  }

  /** The ExternalInstrumentCache instruments of a delisted symbol (same exchange, segment and exchange symbol). */
  static List<ExternalSymbol> findExternalSymbols(final DelistedSymbol symbol) {
    String base = symbol.getBase();
    if (base == null || base.isEmpty()) {
      final String name = symbol.getSymbol().toUpperCase();
      for (final String quote : ROUTED_QUOTES) {
        if (name.endsWith(quote) && name.length() > quote.length()) {
          base = name.substring(0, name.length() - quote.length());
          break;
        }
      }
    }
    final List<ExternalSymbol> found = new ArrayList<>();
    if (base == null) {
      return found;
    }
    for (final ExternalSymbol external : getExternalSymbols(symbol.getExchange(), symbol.isFutures(), base)) {
      if (symbol.getSymbol().equalsIgnoreCase(external.getSymbol())) {
        found.add(external);
      }
    }
    return found;
  }

  /** Instruments of one exchange segment and base; also tries the plain base of multiplier contracts (1000PEPE). */
  public static List<ExternalSymbol> getExternalSymbols(final String exchange, final boolean futures, final String base) {
    final Set<String> bases = new LinkedHashSet<>();
    bases.add(base);
    bases.add(base.replaceFirst("^\\d+", ""));
    bases.add(base.replaceFirst("\\d+$", ""));
    final List<ExternalSymbol> found = new ArrayList<>();
    for (final String candidate : bases) {
      final List<ExternalSymbol> listed = candidate.isEmpty() ? null : ExternalInstrumentCache.getAvailableExchanges(candidate);
      if (listed == null) {
        continue;
      }
      for (final ExternalSymbol external : listed) {
        if (external.isFutures() == futures && exchange.equalsIgnoreCase(external.getExchange()) && external.getSymbol() != null
            && found.stream().noneMatch(f -> f == external)) {
          found.add(external);
        }
      }
    }
    return found;
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
