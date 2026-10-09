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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
  // a refresh waits this long for the exchange (HttpUtils has no timeout); includes the up to 120 s saved-pairs wait
  private static final long REFRESH_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);
  // key <segment prefix, number of the latest refresh>, so a late result of an abandoned refresh is not applied
  private static final ConcurrentHashMap<String, Long> REFRESH_GENERATION = new ConcurrentHashMap<>();
  private static final ExecutorService REFRESH_WORKERS = Executors.newCachedThreadPool(r -> {
    final Thread thread = new Thread(r, "delistedSymbolRefreshWorker");
    thread.setDaemon(true);
    return thread;
  });

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
    scheduler.scheduleWithFixedDelay(() -> refreshWithTimeout(subscription, REFRESH_TIMEOUT_MILLIS), 0, REFRESH_MINUTES,
        TimeUnit.MINUTES);
  }

  private static String getSegmentPrefix(final ExchangeSubscription subscription) {
    return (subscription.getExchange() + "_" + (subscription.isFuturesEnabled() ? "1" : "0") + "_").toLowerCase();
  }

  public static void refresh(final ExchangeSubscription subscription) {
    refresh(subscription, REFRESH_GENERATION.merge(getSegmentPrefix(subscription), 1L, Long::sum));
  }

  // applies the result only if no newer refresh of the segment started meanwhile (a late answer of a timed-out one)
  private static void refresh(final ExchangeSubscription subscription, final long generation) {
    final ExternalExchangeClient client = subscription.getClient();
    if (client == null) {
      return;
    }
    final List<DelistedSymbol> symbols = client.getDelistedSymbols();
    if (symbols == null) { // request failed, keep the last known state
      return;
    }
    final String segmentPrefix = getSegmentPrefix(subscription);
    synchronized (REFRESH_GENERATION) {
      if (REFRESH_GENERATION.get(segmentPrefix) != generation) {
        LOGGER.warn("Late delisting result of " + segmentPrefix + " dropped, a newer refresh started meanwhile");
        return;
      }
      apply(segmentPrefix, symbols, System.currentTimeMillis());
    }
  }

  /** Runs a refresh on a worker and stops waiting after timeoutMillis, so a request that never answers (no HTTP timeout)
   * cannot freeze the segment: the next cycle runs normally and a late result is dropped. */
  static void refreshWithTimeout(final ExchangeSubscription subscription, final long timeoutMillis) {
    final long generation;
    synchronized (REFRESH_GENERATION) {
      generation = REFRESH_GENERATION.merge(getSegmentPrefix(subscription), 1L, Long::sum);
    }
    final Future<?> future = REFRESH_WORKERS.submit(() -> refresh(subscription, generation));
    try {
      future.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (final TimeoutException e) {
      LOGGER.warn("Delisting refresh of " + getSegmentPrefix(subscription) + " got no answer within " + timeoutMillis / 1000
          + " s, the last known state is kept and the next cycle retries");
    } catch (final ExecutionException e) {
      LOGGER.error(ERROR_LOG, e.getCause());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Replaces the segment with a REST snapshot; a symbol pushed by WebSocket is kept for PUSH_GRACE_MILLIS. */
  static void apply(final String segmentPrefix, final List<DelistedSymbol> symbols, final long now) {
    // on the first load every entry is new: one summary line instead of a warning per symbol
    final boolean firstLoad = !LOADED.contains(segmentPrefix);
    final Set<String> current = new HashSet<>();
    for (final DelistedSymbol symbol : symbols) {
      current.add(symbol.getKey());
      final Long pushedAt = PUSHED_AT.get(symbol.getKey());
      final DelistedSymbol pushed = DELISTED.get(symbol.getKey());
      if (pushedAt != null && now - pushedAt < PUSH_GRACE_MILLIS && pushed != null && pushed.isTradingDisabled()
          && !symbol.isTradingDisabled()) {
        continue; // a recent push says stopped and REST can lag: keep the push until the grace period ends
      }
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
    if (symbol.isRestricted() && (previous == null ? logNew : !previous.isRestricted())) {
      LOGGER.warn("Trading restricted to one side, still routed: " + symbol);
    }
  }

  /** One line for a segment's first load: how many are stopped, and which delistings are scheduled. */
  private static void logFirstLoad(final String segmentPrefix, final List<DelistedSymbol> symbols, final long now) {
    int stopped = 0;
    int restricted = 0;
    final StringBuilder scheduled = new StringBuilder();
    int scheduledCount = 0;
    for (final DelistedSymbol symbol : symbols) {
      if (symbol.isRestricted()) {
        restricted++;
      }
      if (symbol.isTradingDisabled()) {
        stopped++;
      } else if (symbol.isUpcoming() && symbol.getDelistTime() > now) {
        scheduledCount++;
        scheduled.append(scheduledCount == 1 ? "" : ", ").append(symbol.getSymbol()).append('@')
            .append(Instant.ofEpochMilli(symbol.getDelistTime()));
      }
    }
    LOGGER.warn("Delisted symbols loaded for " + segmentPrefix + " trading stopped: " + stopped + ", restricted: " + restricted
        + ", delisting scheduled: " + scheduledCount + (scheduledCount > 0 ? " [" + scheduled + "]" : ""));
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
    final List<ExternalSymbol> instruments = findExternalSymbols(symbol);
    // compute() runs one at a time per key with restoreTradable(); skip when the key was removed in the meantime
    DISABLED.compute(symbol.getKey(), (key, disabled) -> {
      final DelistedSymbol current = DELISTED.get(key);
      if (current == null || !current.isTradingDisabled()) {
        return disabled;
      }
      final List<ExternalSymbol> list = disabled != null ? disabled : new CopyOnWriteArrayList<>();
      for (final ExternalSymbol external : instruments) {
        if (external.isTradable()) {
          external.setTradable(false);
          list.add(external);
          LOGGER.info("Not tradable while delisted: " + key);
        }
      }
      return list.isEmpty() ? null : list;
    });
  }

  /** Sets back tradable the instruments syncTradable() disabled for this key, unless it is stopped again meanwhile. */
  private static void restoreTradable(final String key) {
    DISABLED.compute(key, (k, disabled) -> {
      final DelistedSymbol current = DELISTED.get(k);
      if (current != null && current.isTradingDisabled()) {
        return disabled;
      }
      if (disabled != null) {
        disabled.forEach(external -> external.setTradable(true));
        LOGGER.info("Tradable again: " + k);
      }
      return null;
    });
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
      if (symbol.getSymbol().equalsIgnoreCase(exchangeSymbol(external))) {
        found.add(external);
      }
    }
    return found;
  }

  /** The instrument's exchange symbol: symbol, else prompt (Deribit instrument name), else base + quote (Bitget rows). */
  public static String exchangeSymbol(final ExternalSymbol external) {
    if (external.getSymbol() != null) {
      return external.getSymbol();
    }
    return external.getPrompt() != null && !external.getPrompt().isEmpty() ? external.getPrompt()
        : (external.getBase() + external.getQuote()).toUpperCase();
  }

  /** Saved instruments of a segment; waits up to 120 s for the startup load (until the exchange's count stops changing). */
  public static List<ExternalSymbol> waitForSavedSymbols(final String exchange, final boolean futures) {
    final long waitUntil = System.currentTimeMillis() + 120_000;
    int count = savedBaseCount(exchange);
    int previous = -1;
    while ((count == 0 || count != previous) && System.currentTimeMillis() < waitUntil) {
      previous = count;
      try {
        Thread.sleep(2000);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      count = savedBaseCount(exchange);
    }
    // the instrument load can still be adding on another thread (HashSet copy may throw): retry instead of using 0 pairs
    for (int attempt = 1; attempt <= 5; attempt++) {
      try {
        final List<ExternalSymbol> saved = new ArrayList<>();
        for (final String base : ExternalInstrumentCache.getAvailableSymbols(exchange)) {
          for (final ExternalSymbol external : getExternalSymbols(exchange, futures, base)) {
            if (saved.stream().noneMatch(s -> s == external)) {
              saved.add(external);
            }
          }
        }
        LOGGER.info(exchange + (futures ? " futures" : " spot") + " delisting check starts from " + saved.size() + " saved pairs");
        return saved;
      } catch (final RuntimeException e) { // NullPointerException: nothing loaded; ConcurrentModificationException: still loading
        if (count == 0) {
          break;
        }
        try {
          Thread.sleep(1000);
        } catch (final InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    LOGGER.warn(exchange + (futures ? " futures" : " spot") + " saved pairs could not be read, pairs delisted while the engine"
        + " was down are not detected this run");
    return new ArrayList<>();
  }

  /** Finds pairs an exchange no longer lists (delisted pairs disappear from its list); one per exchange segment. */
  public static final class ListedTracker {
    private final Map<String, String[]> known = new HashMap<>(); // symbol -> base, quote
    private final Map<String, DelistedSymbol> removed = new HashMap<>();
    private final boolean seedFromSaved;
    private int lastCount;

    public ListedTracker() {
      this(true);
    }

    /** seedFromSaved false when saved rows can't give the exchange symbol (Deribit names aren't stored). */
    public ListedTracker(final boolean seedFromSaved) {
      this.seedFromSaved = seedFromSaved;
    }

    /**
     * Known pairs missing from listed (symbol -> base, quote), as stopped symbols; null when listed shrank under half the
     * previous response. The first call starts from the saved pairs, so delistings while the engine was down are caught.
     */
    public synchronized List<DelistedSymbol> update(final String exchange, final boolean futures, final Map<String, String[]> listed,
        final long now) {
      if (listed.isEmpty() || (lastCount > 0 && listed.size() < lastCount / 2)) {
        LOGGER.warn(exchange + " list shrank from " + lastCount + " to " + listed.size() + " symbols, ignoring this response");
        return null;
      }
      final boolean firstCall = lastCount == 0;
      if (firstCall && seedFromSaved) {
        // only pairs we could trade, with a real exchange symbol (base + quote is unreliable for futures, e.g. Bitget ...PERP)
        for (final ExternalSymbol saved : waitForSavedSymbols(exchange, futures)) {
          final boolean named = saved.getSymbol() != null || (saved.getPrompt() != null && !saved.getPrompt().isEmpty());
          if (saved.isTradable() && (named || !futures)) {
            known.put(exchangeSymbol(saved), new String[] {saved.getBase(), saved.getQuote()});
          }
        }
      }
      for (final Map.Entry<String, String[]> entry : known.entrySet()) {
        if (!listed.containsKey(entry.getKey()) && !removed.containsKey(entry.getKey())) {
          final DelistedSymbol symbol = new DelistedSymbol();
          symbol.setExchange(exchange);
          symbol.setFutures(futures);
          symbol.setSymbol(entry.getKey());
          symbol.setBase(entry.getValue()[0]);
          symbol.setQuote(entry.getValue()[1]);
          symbol.setStatus("REMOVED");
          symbol.setTradingDisabled(true);
          symbol.setDetectedAt(now);
          removed.put(entry.getKey(), symbol);
        }
      }
      removed.keySet().removeAll(listed.keySet()); // listed again
      if (firstCall && seedFromSaved) {
        LOGGER.warn(exchange + (futures ? " futures" : " spot") + " first check: " + removed.size()
            + " saved pairs are no longer listed (includes old delistings kept in the saved data)");
      }
      known.clear();
      known.putAll(listed); // the no longer listed ones are kept in removed
      lastCount = listed.size();
      return new ArrayList<>(removed.values());
    }
  }

  private static int savedBaseCount(final String exchange) {
    try {
      return ExternalInstrumentCache.getAvailableSymbols(exchange).size();
    } catch (final RuntimeException e) { // not loaded yet, or still loading
      return 0;
    }
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
        if (external.isFutures() == futures && exchange.equalsIgnoreCase(external.getExchange())
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
