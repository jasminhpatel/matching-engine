package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.hyperliquid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.DelistedSymbolCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

public class HyperliquidRestClient {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(HyperliquidRestClient.class);
  private static final String REST_API_BASE_MAINNET = "https://api.hyperliquid.xyz";
  private static final String REST_API_BASE_TESTNET = "https://api.hyperliquid-testnet.xyz";
  private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";
  // Hyperliquid's builder-deployed (HIP-3) commodities dex (crude oil, gold, silver, etc.).
  public static final String COMMODITIES_DEX = "xyz";
  private static final int PROXY_PORT = 8888;
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  // Hyperliquid tracks nonce uniqueness per signer (wallet), not per HyperliquidRestClient
  // instance - a spot subscription and a futures subscription signing with the same private key
  // must draw from the same counter, or two clients can hand out the same nonce and one gets
  // rejected as a replay. Shared across every instance in this JVM, keyed by signer address.
  private static final ConcurrentHashMap<String, AtomicLong> NONCE_BY_SIGNER = new ConcurrentHashMap<>();

  private final ExchangeSubscription subscription;
  private final Credentials credentials;
  private final boolean mainnet;
  private final String restApiBase;

  // base/quote -> Hyperliquid "coin" name (the allMids key / spotMeta market name), from spotMeta.
  private final Map<String, String> coinNameByPair = new ConcurrentHashMap<>();
  // base/quote -> Hyperliquid spot asset id (10000 + universe index), from spotMeta.
  private final Map<String, Integer> assetIdByPair = new ConcurrentHashMap<>();
  // Hyperliquid "coin" name (e.g. "@107" or "PURR/USDC") -> spot metadata.
  private final Map<String, Integer> spotAssetIdByCoin = new ConcurrentHashMap<>();
  private final Map<String, Integer> spotQtyScaleByCoin = new ConcurrentHashMap<>();
  private volatile boolean spotMetaLoaded = false;

  // coin -> Hyperliquid perp asset id (its 0-based index in meta's universe array), from meta.
  private final Map<String, Integer> perpAssetIdByCoin = new ConcurrentHashMap<>();
  private final Map<String, Integer> perpQtyScaleByCoin = new ConcurrentHashMap<>();
  private volatile boolean perpMetaLoaded = false;

  // Builder-deployed (HIP-3) perp dexes, keyed by upper-cased "DEX:COIN" market name.
  private final Map<String, Integer> hip3DexIndexByName = new ConcurrentHashMap<>();
  private final Map<String, Integer> hip3PerpAssetIdByMarket = new ConcurrentHashMap<>();
  private final Map<String, Integer> hip3PerpQtyScaleByMarket = new ConcurrentHashMap<>();

  // symbols no longer listed, one per segment (a client serves one); first check starts from the saved pairs
  private final DelistedSymbolCache.ListedTracker futuresTracker = new DelistedSymbolCache.ListedTracker();
  private final DelistedSymbolCache.ListedTracker spotTracker = new DelistedSymbolCache.ListedTracker();

  public HyperliquidRestClient(final String privateKey, final ExchangeSubscription subscription,
      final boolean mainnet) {
    this.credentials = Credentials.create(privateKey);
    this.subscription = subscription;
    this.mainnet = mainnet;
    this.restApiBase = mainnet ? REST_API_BASE_MAINNET : REST_API_BASE_TESTNET;
  }

  private JsonNode getSpotMeta() throws IOException {
    final HttpUtils.Response response = postInfo("{\"type\":\"spotMeta\"}");
    if (response != null && response.getCode() == 200) {
      return JSON_MAPPER.readTree(response.getData());
    }
    throw new IOException("Hyperliquid getSpotMeta failed with status: "
        + (response != null ? response.getCode() : "null"));
  }

  private JsonNode getAllMids(final String dex) throws IOException {
    final String body = (dex == null || dex.isEmpty())
        ? "{\"type\":\"allMids\"}"
        : "{\"type\":\"allMids\",\"dex\":\"" + dex + "\"}";
    final HttpUtils.Response response = postInfo(body);
    if (response != null && response.getCode() == 200) {
      return JSON_MAPPER.readTree(response.getData());
    }
    throw new IOException("Hyperliquid getAllMids failed with status: "
        + (response != null ? response.getCode() : "null"));
  }

  /** Spot wallet balances: {@code {"type":"spotClearinghouseState","user":"0x..."}} -> {"balances":[{coin,hold,total,...}]}. */
  public JsonNode getSpotBalances(final String user) throws IOException {
    final Map<String, Object> request = new LinkedHashMap<>();
    request.put("type", "spotClearinghouseState");
    request.put("user", user);

    final HttpUtils.Response response = postInfo(JSON_MAPPER.writeValueAsString(request));
    if (response == null || response.getCode() != 200) {
      throw new IOException("Hyperliquid getSpotBalances failed with status: "
          + (response != null ? response.getCode() : "null"));
    }
    return JSON_MAPPER.readTree(response.getData());
  }

  /** Perp account state on the main dex: {@code {"type":"clearinghouseState","user":"0x...","dex":""}} -> {marginSummary, assetPositions}. */
  public JsonNode getPerpClearinghouseState(final String user) throws IOException {
    return getPerpClearinghouseState(user, "");
  }

  /** Perp account state on a specific dex (e.g. {@link #COMMODITIES_DEX}) - same shape as {@link #getPerpClearinghouseState(String)}. */
  public JsonNode getPerpClearinghouseState(final String user, final String dex) throws IOException {
    final Map<String, Object> request = new LinkedHashMap<>();
    request.put("type", "clearinghouseState");
    request.put("user", user);
    request.put("dex", dex.toLowerCase(Locale.ROOT));

    final HttpUtils.Response response = postInfo(JSON_MAPPER.writeValueAsString(request));
    if (response == null || response.getCode() != 200) {
      throw new IOException("Hyperliquid getPerpClearinghouseState failed for dex " + dex + " with status: "
          + (response != null ? response.getCode() : "null"));
    }
    return JSON_MAPPER.readTree(response.getData());
  }

  /** Perp metadata: {@code {"type":"meta","dex":""}} -> {"universe":[{name,szDecimals,maxLeverage},...]}. */
  private JsonNode getPerpMeta() throws IOException {
    final HttpUtils.Response response = postInfo("{\"type\":\"meta\",\"dex\":\"\"}");
    if (response != null && response.getCode() == 200) {
      return JSON_MAPPER.readTree(response.getData());
    }
    throw new IOException("Hyperliquid getPerpMeta failed with status: "
        + (response != null ? response.getCode() : "null"));
  }

  /** Resolves the Hyperliquid perp asset id for a coin: its 0-based index in meta's universe array. */
  public int ensurePerpAssetId(final String coin) throws IOException {
    if (coin != null && coin.contains(":")) {
      final String[] parts = coin.split(":", 2);
      return ensureHip3PerpAssetId(parts[0], parts[1]);
    }

    ensurePerpMetaLoaded();
    Integer assetId = perpAssetIdByCoin.get(coin.toUpperCase(Locale.ROOT));
    if (assetId == null) {
      refreshPerpMetaCache();
      assetId = perpAssetIdByCoin.get(coin.toUpperCase(Locale.ROOT));
    }
    if (assetId != null) {
      return assetId;
    }
    try {
      return ensureHip3PerpAssetId(COMMODITIES_DEX, coin);
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported Hyperliquid perp coin: " + coin
          + " (not on the main perp dex; commodities dex fallback also failed: " + e.getMessage() + ")", e);
    }
  }

  /** Resolves the perp asset id for a builder-deployed dex market (e.g. dex="xyz", coin="CL"). */
  private int ensureHip3PerpAssetId(final String dex, final String coin) throws IOException {
    final String marketKey = (dex + ":" + coin).toUpperCase(Locale.ROOT);
    Integer assetId = hip3PerpAssetIdByMarket.get(marketKey);
    if (assetId == null) {
      refreshHip3PerpMetaCache(dex);
      assetId = hip3PerpAssetIdByMarket.get(marketKey);
    }
    if (assetId == null) {
      throw new IllegalArgumentException("Unsupported Hyperliquid HIP-3 perp market: " + dex + ":" + coin);
    }
    return assetId;
  }

  /** Resolves the quantity scale for a builder-deployed dex market. */
  private int ensureHip3QuantityScale(final String dex, final String coin) throws IOException {
    final String marketKey = (dex + ":" + coin).toUpperCase(Locale.ROOT);
    Integer scale = hip3PerpQtyScaleByMarket.get(marketKey);
    if (scale == null) {
      refreshHip3PerpMetaCache(dex);
      scale = hip3PerpQtyScaleByMarket.get(marketKey);
    }
    if (scale == null) {
      throw new IllegalArgumentException("Unsupported Hyperliquid HIP-3 perp market: " + dex + ":" + coin);
    }
    return scale;
  }

  /** Resolves a builder-deployed dex's index among {@code perpDexs} (index 0 is the main dex). */
  private int ensureHip3DexIndex(final String dex) throws IOException {
    final Integer cached = hip3DexIndexByName.get(dex.toUpperCase(Locale.ROOT));
    if (cached != null) {
      return cached;
    }
    final HttpUtils.Response dexResponse = postInfo("{\"type\":\"perpDexs\"}");
    if (dexResponse == null || dexResponse.getCode() != 200) {
      throw new IOException("Hyperliquid perpDexs request failed with status: "
              + (dexResponse != null ? dexResponse.getCode() : "null"));
    }
    final JsonNode dexes = JSON_MAPPER.readTree(dexResponse.getData());
    for (int i = 1; i < dexes.size(); i++) {
      final JsonNode dexNode = dexes.get(i);
      if (dexNode != null && dex.equalsIgnoreCase(dexNode.path("name").asText())) {
        hip3DexIndexByName.put(dex.toUpperCase(Locale.ROOT), i);
        return i;
      }
    }
    throw new IllegalArgumentException("Unknown Hyperliquid builder-deployed dex: " + dex);
  }

  /** Perp metadata for a builder-deployed dex: {@code {"type":"meta","dex":dex}}. */
  private JsonNode getHip3PerpMeta(final String dex) throws IOException {
    final Map<String, Object> request = new LinkedHashMap<>();
    request.put("type", "meta");
    request.put("dex", dex.toLowerCase(Locale.ROOT));
    final HttpUtils.Response response = postInfo(JSON_MAPPER.writeValueAsString(request));
    if (response != null && response.getCode() == 200) {
      return JSON_MAPPER.readTree(response.getData());
    }
    throw new IOException("Hyperliquid HIP-3 meta request failed for dex " + dex + " with status: "
        + (response != null ? response.getCode() : "null"));
  }

  /**
   * Loads every market in a builder-deployed dex's universe, rebuilding this dex's cache entries
   * from scratch so a delisted market drops out instead of leaving a stale asset id behind.
   */
  private synchronized void refreshHip3PerpMetaCache(final String dex) throws IOException {
    final int dexIndex = ensureHip3DexIndex(dex);
    final JsonNode universe = getHip3PerpMeta(dex).path("universe");

    final Map<String, Integer> nextAssetIds = new HashMap<>();
    final Map<String, Integer> nextQtyScales = new HashMap<>();
    for (int i = 0; i < universe.size(); i++) {
      final JsonNode market = universe.get(i);
      final String marketName = market.path("name").asText();
      if (marketName.isEmpty() || market.path("isDelisted").asBoolean(false)) {
        continue;
      }
      final String key = marketName.toUpperCase(Locale.ROOT);
      nextAssetIds.put(key, 100000 + (dexIndex * 10000) + i);
      nextQtyScales.put(key, market.path("szDecimals").asInt());
    }

    final String dexPrefix = dex.toUpperCase(Locale.ROOT) + ":";
    hip3PerpAssetIdByMarket.keySet().removeIf(key -> key.startsWith(dexPrefix));
    hip3PerpAssetIdByMarket.putAll(nextAssetIds);
    hip3PerpQtyScaleByMarket.keySet().removeIf(key -> key.startsWith(dexPrefix));
    hip3PerpQtyScaleByMarket.putAll(nextQtyScales);
  }

  public synchronized void ensurePerpMetaLoaded() throws IOException {
    if (!perpMetaLoaded) {
      refreshPerpMetaCache();
    }
  }

  private synchronized void refreshPerpMetaCache() throws IOException {
    final JsonNode meta = getPerpMeta();
    final Map<String, Integer> nextAssetIds = new HashMap<>();
    final Map<String, Integer> nextQtyScales = new HashMap<>();
    int index = 0;
    for (final JsonNode market : meta.path("universe")) {
      final String coin = market.path("name").asText().toUpperCase(Locale.ROOT);
      nextAssetIds.put(coin, index);
      nextQtyScales.put(coin, market.path("szDecimals").asInt());
      index++;
    }
    perpAssetIdByCoin.clear();
    perpAssetIdByCoin.putAll(nextAssetIds);
    perpQtyScaleByCoin.clear();
    perpQtyScaleByCoin.putAll(nextQtyScales);
    perpMetaLoaded = true;
  }

  /** Raw open-orders JSON for this wallet: {@code {"type":"openOrders","user":"0x..."}}. */
  public String getOpenOrders(final String user) {
    final com.fasterxml.jackson.databind.node.ArrayNode combined = JSON_MAPPER.createArrayNode();
    for (final String dex : new String[] {"", COMMODITIES_DEX}) {
      if (!dex.isEmpty()) {
        try {
          ensureHip3DexIndex(dex);
        } catch (final IllegalArgumentException e) {
          LOGGER.info("Hyperliquid dex is unavailable on this network: " + dex);
          continue;
        } catch (final IOException e) {
          throw new IllegalStateException("Cannot discover Hyperliquid dex: " + dex, e);
        }
      }
      final String json = getOpenOrders(user, dex);
      if (json == null) {
        throw new IllegalStateException("Cannot recover Hyperliquid orders for dex: " + dex);
      }
      try {
        final JsonNode orders = JSON_MAPPER.readTree(json);
        if (!orders.isArray()) {
          throw new IOException("Expected open-orders array");
        }
        for (final JsonNode order : orders) {
          combined.add(order);
        }
      } catch (final IOException e) {
        throw new IllegalStateException("Invalid Hyperliquid open-orders response", e);
      }
    }
    return combined.toString();
  }

  public String getOpenOrders(final String user, final String dex) {
    final Map<String, Object> request = new LinkedHashMap<>();
    request.put("type", "frontendOpenOrders");
    request.put("user", user);
    request.put("dex", dex.toLowerCase(Locale.ROOT));
    try {
      final HttpUtils.Response response = postInfo(JSON_MAPPER.writeValueAsString(request));
      if (response != null && response.getCode() == 200) {
        return response.getData();
      }
      LOGGER.warn("Hyperliquid getOpenOrders failed with status: "
          + (response != null ? response.getCode() : "null"));
    } catch (final Exception e) {
      LOGGER.error("Hyperliquid getOpenOrders failed: " + e.getMessage(), e);
    }
    return null;
  }

  /** Single-order lookup by exchange oid: {@code {"type":"orderStatus","user":"0x...","oid":oid}}. */
  public JsonNode getOrderStatus(final String user, final long oid) throws IOException {
    return getOrderStatus(user, (Object) oid);
  }

  /**
   * Single-order lookup by client order id instead of exchange oid - per the API docs, {@code oid}
   * accepts "either u64 representing the order id or 16-byte hex string representing the client
   * order id". Lets callers poll order status even when the real exchange oid was never learned
   * (e.g. a WS order-placement response that never arrived).
   */
  public JsonNode getOrderStatusByCloid(final String user, final String cloid) throws IOException {
    return getOrderStatus(user, (Object) cloid);
  }

  private JsonNode getOrderStatus(final String user, final Object oidOrCloid) throws IOException {
    final Map<String, Object> request = new LinkedHashMap<>();
    request.put("type", "orderStatus");
    request.put("user", user);
    request.put("oid", oidOrCloid);

    final HttpUtils.Response response = postInfo(JSON_MAPPER.writeValueAsString(request));
    if (response == null || response.getCode() != 200) {
      throw new IOException("Hyperliquid getOrderStatus failed with status: "
          + (response != null ? response.getCode() : "null"));
    }
    return JSON_MAPPER.readTree(response.getData());
  }

  /** Resolves the Hyperliquid "coin" name (allMids key) for a spot base/quote pair. */
  public String ensureCoinName(final String base, final String quote) throws IOException {
    ensureSpotMetaLoaded();
    final String key = pairKey(base, quote);
    String coin = coinNameByPair.get(key);
    if (coin == null) {
      refreshSpotMetaCache();
      coin = coinNameByPair.get(key);
    }
    if (coin == null) {
      throw new IllegalArgumentException("Unsupported Hyperliquid spot pair: " + base + "/" + quote);
    }
    return coin;
  }

  /** Resolves the Hyperliquid spot asset id for a base/quote pair. */
  public int ensureAssetId(final String base, final String quote) throws IOException {
    ensureSpotMetaLoaded();
    final String key = pairKey(base, quote);
    Integer assetId = assetIdByPair.get(key);
    if (assetId == null) {
      refreshSpotMetaCache();
      assetId = assetIdByPair.get(key);
    }
    if (assetId == null) {
      throw new IllegalArgumentException("Unsupported Hyperliquid spot pair: " + base + "/" + quote);
    }
    return assetId;
  }

  public synchronized void ensureSpotMetaLoaded() throws IOException {
    if (!spotMetaLoaded) {
      refreshSpotMetaCache();
    }
  }

  /** Resolves the spot asset id directly from Hyperliquid's raw coin name (e.g. "@107" or "PURR/USDC"), unlike {@link #ensureAssetId} which needs a separately-known base/quote pair. Returns null, not an exception, when unknown - callers restoring open orders should skip/log rather than fail bootstrap entirely. */
  public Integer ensureAssetIdForCoin(final String coin) throws IOException {
    ensureSpotMetaLoaded();
    Integer assetId = spotAssetIdByCoin.get(coin);
    if (assetId == null) {
      refreshSpotMetaCache();
      assetId = spotAssetIdByCoin.get(coin);
    }
    return assetId;
  }

  public int ensureQuantityScaleForCoin(final String coin, final boolean spot) throws IOException {
    if (!spot && coin != null && coin.contains(":")) {
      final String[] parts = coin.split(":", 2);
      return ensureHip3QuantityScale(parts[0], parts[1]);
    }

    final String key = spot ? coin : coin.toUpperCase(Locale.ROOT);
    if (spot) {
      ensureSpotMetaLoaded();
    } else {
      ensurePerpMetaLoaded();
    }
    Map<String, Integer> scales = spot ? spotQtyScaleByCoin : perpQtyScaleByCoin;
    Integer scale = scales.get(key);
    if (scale == null) {
      if (spot) {
        refreshSpotMetaCache();
      } else {
        refreshPerpMetaCache();
      }
      scale = scales.get(key);
    }
    if (scale == null && !spot) {
      // Not on the main perp dex - same commodities-dex fallback as ensurePerpAssetId, for a bare
      // coin name like "GOLD" or "CL".
      try {
        return ensureHip3QuantityScale(COMMODITIES_DEX, coin);
      } catch (final IllegalArgumentException ignored) {
        // fall through to the standard error below
      }
    }
    if (scale == null) {
      throw new IllegalArgumentException("Unsupported Hyperliquid coin: " + coin);
    }
    return scale;
  }

  private synchronized void refreshSpotMetaCache() throws IOException {
    final JsonNode meta = getSpotMeta();

    final Map<Integer, String> tokenNames = new HashMap<>();
    final Map<Integer, Integer> tokenQtyScales = new HashMap<>();
    for (final JsonNode token : meta.path("tokens")) {
      tokenNames.put(token.path("index").asInt(), token.path("name").asText());
      tokenQtyScales.put(token.path("index").asInt(), token.path("szDecimals").asInt());
    }

    final Map<String, String> nextCoins = new HashMap<>();
    final Map<String, Integer> nextAssetIds = new HashMap<>();
    final Map<String, Integer> nextAssetIdByCoin = new HashMap<>();
    final Map<String, Integer> nextQtyScaleByCoin = new HashMap<>();
    for (final JsonNode market : meta.path("universe")) {
      final JsonNode tokens = market.path("tokens");
      if (tokens.size() != 2) {
        continue;
      }
      final String base = tokenNames.get(tokens.get(0).asInt());
      final String quote = tokenNames.get(tokens.get(1).asInt());
      if (base == null || quote == null) {
        continue;
      }
      final String key = pairKey(base, quote);
      final String coinName = market.path("name").asText();
      final int assetId = 10_000 + market.path("index").asInt();
      nextCoins.put(key, coinName);
      nextAssetIds.put(key, assetId);
      nextAssetIdByCoin.put(coinName, assetId);
      nextQtyScaleByCoin.put(coinName, tokenQtyScales.getOrDefault(tokens.get(0).asInt(), 0));
    }
    coinNameByPair.clear();
    coinNameByPair.putAll(nextCoins);
    assetIdByPair.clear();
    assetIdByPair.putAll(nextAssetIds);
    spotAssetIdByCoin.clear();
    spotAssetIdByCoin.putAll(nextAssetIdByCoin);
    spotQtyScaleByCoin.clear();
    spotQtyScaleByCoin.putAll(nextQtyScaleByCoin);
    spotMetaLoaded = true;
  }

  private static String pairKey(final String base, final String quote) {
    return (base + "/" + quote).toUpperCase(Locale.ROOT);
  }

  /**
   * Ticker lookup for both spot and perp - mirrors BybitRestClient.getTicker(symbol, category).
   * Pass the Hyperliquid "coin" identifier directly - for spot, resolve it first via
   * {@link #ensureCoinName}; for perp, it's just the raw coin (e.g. "BTC"). {@code dex} scopes a
   * perp lookup to a builder-deployed dex (HIP-3, e.g. "xyz" for commodity/equity perps like WTI
   * crude oil / Brent oil) - pass "" for spot or the main perp dex. The {@code allMids}/{@code
   * l2Book} keys are always prefixed with the dex name (e.g. {@code "xyz:CL"}), so the key is
   * built as {@code dex:coin} whenever a non-default dex is given. Last price comes from
   * {@code allMids}, bid/ask/timestamp from the L2 order book (same key for both calls).
   */
  public Ticker getTicker(final String coin, final String dex) {
    final String key = (dex == null || dex.isEmpty()) ? coin : dex + ":" + coin;
    try {
      final JsonNode mids = getAllMids(dex);
      final JsonNode priceNode = mids.path(key);
      if (priceNode.isMissingNode() || priceNode.isNull()) {
        LOGGER.warn("Hyperliquid getTicker: no mid price returned for " + key);
        return null;
      }
      final double last = priceNode.asDouble();
      final Ticker ticker = new Ticker(key, 0, 0.0, last, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0.0, 0.0, 0.0);
      applyBestBidAsk(ticker, key);
      return ticker;
    } catch (final Exception e) {
      LOGGER.error("Get ticker failed for " + key + " " + e.getMessage());
      return null;
    }
  }

  /**
   * Fills in {@code bid}/{@code ask}/{@code timestamp} from the L2 order book - {@code allMids}
   * only has a mid price, so a real best-bid/best-ask needs a separate {@code l2Book} lookup.
   * {@code l2Book} takes the same coin key as {@code allMids} (main dex: plain coin, e.g. "BTC" or
   * "PURR/USDC"; builder dex: "dex:coin", e.g. "xyz:CL") - no separate "dex" field. Falls back to
   * a wall-clock timestamp on failure so the ticker cache still expires normally.
   */
  private void applyBestBidAsk(final Ticker ticker, final String coinKey) {
    try {
      final Map<String, Object> request = new LinkedHashMap<>();
      request.put("type", "l2Book");
      request.put("coin", coinKey);
      final HttpUtils.Response response = postInfo(JSON_MAPPER.writeValueAsString(request));
      if (response == null || response.getCode() != 200) {
        throw new IOException("Hyperliquid getL2Book failed with status: "
            + (response != null ? response.getCode() : "null"));
      }
      final JsonNode book = JSON_MAPPER.readTree(response.getData());
      final JsonNode levels = book.path("levels");
      if (levels.isArray() && levels.size() == 2) {
        final JsonNode bids = levels.get(0);
        final JsonNode asks = levels.get(1);
        if (bids.size() > 0) {
          ticker.setBid(bids.get(0).path("px").asDouble());
        }
        if (asks.size() > 0) {
          ticker.setAsk(asks.get(0).path("px").asDouble());
        }
      }
      ticker.setTimestamp(book.path("time").asLong());
    } catch (final Exception e) {
      // Leave the timestamp at zero so callers can recognize the failed book lookup.
      LOGGER.warn("Hyperliquid l2Book lookup failed for " + coinKey + ": " + e.getMessage());
    }
  }

  /**
   * Fetches both spot and perp instruments, matching BybitRestClient.getExchangeInstrumentsFull()
   * (which hits both {@code category=spot} and {@code category=linear}) - Hyperliquid has no
   * combined instruments endpoint, so this is spotMeta + meta merged into one list, one call each.
   * Price/qty scale follow Hyperliquid's documented decimal rule: quantities are precise to a
   * coin's own {@code szDecimals}, and prices to (MAX_DECIMALS - szDecimals) - 8 for spot, 6 for
   * perp - confirmed empirically (e.g. PURR szDecimals=0 -> priceScale 8; ARB szDecimals=1 ->
   * priceScale 5, matching observed live ticker precision).
   */
  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    final List<ExternalSymbol> symbolStatuses = new ArrayList<>();
    final long updated = System.currentTimeMillis();

    try {
      final JsonNode meta = getSpotMeta();
      final Map<Integer, String> tokenNames = new HashMap<>();
      final Map<Integer, Integer> tokenSzDecimals = new HashMap<>();
      for (final JsonNode token : meta.path("tokens")) {
        tokenNames.put(token.path("index").asInt(), token.path("name").asText());
        tokenSzDecimals.put(token.path("index").asInt(), token.path("szDecimals").asInt());
      }

      for (final JsonNode market : meta.path("universe")) {
        final JsonNode tokens = market.path("tokens");
        if (tokens.size() != 2) {
          continue;
        }
        final int baseIndex = tokens.get(0).asInt();
        final String base = tokenNames.get(baseIndex);
        final String quote = tokenNames.get(tokens.get(1).asInt());
        if (base == null || quote == null) {
          continue;
        }
        final int qtyScale = tokenSzDecimals.getOrDefault(baseIndex, 0);
        final ExternalSymbol symbolStatus = new ExternalSymbol();
        symbolStatus.setExchange("hyperliquid");
        symbolStatus.setSymbol(base + quote);
        symbolStatus.setBase(base);
        symbolStatus.setQuote(quote);
        symbolStatus.setPrompt("");
        symbolStatus.setTradable(true);
        symbolStatus.setFutures(false);
        symbolStatus.setPriceScale(Math.max(0, 8 - qtyScale));
        symbolStatus.setQtyScale(qtyScale);
        symbolStatus.setUpdated(updated);
        symbolStatuses.add(symbolStatus);
      }
    } catch (final Exception e) {
      LOGGER.error("Get spot exchange instruments failed: " + e.getMessage(), e);
    }

    try {
      final JsonNode meta = getPerpMeta();
      for (final JsonNode market : meta.path("universe")) {
        final String coin = market.path("name").asText();
        if (coin.isEmpty() || market.path("isDelisted").asBoolean(false)) {
          continue;
        }
        final int qtyScale = market.path("szDecimals").asInt();
        final ExternalSymbol symbolStatus = new ExternalSymbol();
        symbolStatus.setExchange("hyperliquid");
        symbolStatus.setSymbol(coin);
        symbolStatus.setBase(coin);
        symbolStatus.setQuote("USDC"); // Hyperliquid perps are always USDC-margined
        symbolStatus.setPrompt("");
        symbolStatus.setTradable(true);
        symbolStatus.setFutures(true);
        symbolStatus.setPriceScale(Math.max(0, 6 - qtyScale));
        symbolStatus.setQtyScale(qtyScale);
        symbolStatus.setUpdated(updated);
        symbolStatuses.add(symbolStatus);
      }
    } catch (final Exception e) {
      LOGGER.error("Get perp exchange instruments failed: " + e.getMessage(), e);
    }

    appendCommodityInstruments(symbolStatuses, updated);

    return symbolStatuses;
  }

  /** Preserves HIP-3 market namespaces in instrument identity to avoid main-dex collisions. */
  private void appendCommodityInstruments(final List<ExternalSymbol> symbolStatuses, final long updated) {
    try {
      final JsonNode universe = getHip3PerpMeta(COMMODITIES_DEX).path("universe");
      for (final JsonNode market : universe) {
        final String marketName = market.path("name").asText(); // dex-prefixed, e.g. "xyz:CL"
        if (marketName.isEmpty() || market.path("isDelisted").asBoolean(false)) {
          continue;
        }
        final int qtyScale = market.path("szDecimals").asInt();
        final ExternalSymbol symbolStatus = new ExternalSymbol();
        symbolStatus.setExchange("hyperliquid");
        symbolStatus.setSymbol(marketName);
        symbolStatus.setBase(marketName);
        symbolStatus.setQuote("USDC");
        symbolStatus.setPrompt("");
        symbolStatus.setTradable(true);
        symbolStatus.setFutures(true);
        symbolStatus.setPriceScale(Math.max(0, 6 - qtyScale));
        symbolStatus.setQtyScale(qtyScale);
        symbolStatus.setUpdated(updated);
        symbolStatuses.add(symbolStatus);
      }
    } catch (final Exception e) {
      LOGGER.error("Get commodity (xyz dex) exchange instruments failed: " + e.getMessage(), e);
    }
  }

  /**
   * Places a spot limit order (GTC) and updates the subscription's execution-report cache with
   * the result, matching the other exchange RestClients' sendOrderREST convention.
   */
  public boolean sendSpotOrderREST(final Order order, final int assetId, final boolean isBuy,
      final String price, final String qty, final String cloid) {
    return submitOrderAction(order, assetId, isBuy, price, qty, cloid, false);
  }

  /**
   * Places a perp limit order (GTC). Same {@code order} action as spot per the API docs - only
   * the asset id source differs (perp: raw universe index vs spot: 10000+index) - plus an
   * explicit reduce-only flag, since perp orders can close an existing position.
   */
  public boolean sendPerpOrderREST(final Order order, final int assetId, final boolean isBuy,
      final String price, final String qty, final String cloid, final boolean reduceOnly) {
    return submitOrderAction(order, assetId, isBuy, price, qty, cloid, reduceOnly);
  }

  private boolean submitOrderAction(final Order order, final int assetId, final boolean isBuy,
      final String price, final String qty, final String cloid, final boolean reduceOnly) {
    final String clientOrderId = order.getClOrdId();
    try {
      final JsonNode response = submitExchangeAction(
          buildOrderAction(assetId, isBuy, price, qty, cloid, reduceOnly, order.getTimeInForce()));
      if (response == null) {
       LOGGER.warn("Hyperliquid submitOrderAction: no response for clOrdId " + clientOrderId + " - outcome unknown");
        return false;
      }
      final JsonNode statuses = response.path("data").path("statuses");
      final JsonNode status = statuses.isArray() && statuses.size() > 0 ? statuses.get(0) : response;
      applyOrderStatus(order, status);
      return !order.isRejected();
    } catch (final Exception e) {
      LOGGER.error("Hyperliquid submitOrderAction failed for clOrdId " + clientOrderId + ": " + e.getMessage(), e);
      markRejected(order, e.getMessage());
      return false;
    }
  }

  /** Cancels a resting order (spot or perp - same action either side) by client order id via REST. */
  public boolean cancelSpotOrderRest(final int assetId, final String cloid) {
    try {
      final JsonNode response = submitExchangeAction(buildCancelAction(assetId, cloid));
      return cancelWasConfirmedSuccess(response, cloid);
    } catch (final Exception e) {
      LOGGER.error("Hyperliquid cancelSpotOrderRest failed for cloid " + cloid + ": " + e.getMessage(), e);
      return false;
    }
  }

  /**
   * Builds the signed {@code cancelByCloid} action payload (same shape as {@link
   * #cancelSpotOrderRest}) as a JSON string, for the WS trade listener to send via the same
   * {@code post}/{@code action} envelope used for order placement.
   */
  public String buildSignedCancelPayloadJson(final int assetId, final String cloid) throws Exception {
    return JSON_MAPPER.writeValueAsString(buildSignedPayload(buildCancelAction(assetId, cloid)));
  }

  private static Map<String, Object> buildCancelAction(final int assetId, final String cloid) {
    final Map<String, Object> cancel = new LinkedHashMap<>();
    cancel.put("asset", assetId);
    cancel.put("cloid", cloid);

    final Map<String, Object> action = new LinkedHashMap<>();
    action.put("type", "cancelByCloid");
    action.put("cancels", List.of(cancel));
    return action;
  }

  /**
   * A batch action can come back with a top-level "ok" status while an individual cancel still
   * failed - the per-cancel outcome is nested under data.statuses[i], either the literal string
   * "success" or {"error": "..."}. Require exactly that shape and exactly one "success" entry -
   * a missing or malformed response must not be read as a confirmed cancel.
   */
  private boolean cancelWasConfirmedSuccess(final JsonNode response, final String cloid) {
    if (response == null) {
      LOGGER.warn("Hyperliquid cancel for cloid " + cloid + " has no response - outcome unknown, not confirmed");
      return false;
    }
    final JsonNode statuses = response.path("data").path("statuses");
    if (!statuses.isArray() || statuses.size() != 1) {
      LOGGER.warn("Hyperliquid cancel response for cloid " + cloid + " has unexpected shape: " + response);
      return false;
    }
    final JsonNode status = statuses.get(0);
    final boolean success = status.isTextual() && "success".equalsIgnoreCase(status.asText());
    if (!success) {
      LOGGER.warn("Hyperliquid cancel not confirmed for cloid " + cloid + ": " + status);
    }
    return success;
  }

  private void applyOrderStatus(final Order order, final JsonNode status) {
    final ExecutionReportMessage message = executionReport(order);
    if (status.has("filled")) {
      final JsonNode filled = status.path("filled");
      final long filledQty = MbxMath.changeScale(filled.path("totalSz").asDouble(), order.getQtyScale());
      final long leavesQty = Math.max(0, order.getQty() - filledQty);
      order.setOrderId(filled.path("oid").asLong());
      order.setExecuted(true); // terminal either way - IOC never leaves a resting remainder
      message.setExecType(ExecType.TRADE);
      message.setCumQty(filledQty);
      message.setCumQtyScale(order.getQtyScale());
      message.setAvgPx(MbxMath.changeScale(filled.path("avgPx").asDouble(), order.getPriceScale()));
      message.setAvgPxScale(order.getPriceScale());
      message.setLeavesQty(leavesQty);
      message.setLeavesQtyScale(order.getQtyScale());
      message.setOrdStatus(leavesQty <= 0 ? OrdStatus.FILLED : OrdStatus.PARTIALLY_FILLED);
    } else if (status.has("resting")) {
      order.setOrderId(status.path("resting").path("oid").asLong());
      message.setOrdStatus(OrdStatus.NEW);
      message.setExecType(ExecType.NEW);
    } else {
      final String error = status.has("error") ? status.path("error").asText() : status.toString();
      order.setRejected(true);
      message.setOrdStatus(OrdStatus.REJECTED);
      message.setExecType(ExecType.REJECTED);
      message.setError(error);
    }
    subscription.updateExecutionReport(message);
    subscription.updateOrder(order.getClOrdId(), order);
  }

  private void markRejected(final Order order, final String reason) {
    final ExecutionReportMessage message = executionReport(order);
    order.setRejected(true);
    message.setOrdStatus(OrdStatus.REJECTED);
    message.setExecType(ExecType.REJECTED);
    message.setError(reason);
    subscription.updateExecutionReport(message);
    subscription.updateOrder(order.getClOrdId(), order);
  }

  private ExecutionReportMessage executionReport(final Order order) {
    ExecutionReportMessage message = subscription.getExecutionReport(order.getClOrdId());
    if (message == null) {
      message = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0,
          order.getSymbol(), 0L, order.getPriceScale(), 0L, order.getQtyScale(), 0, 0, 0, 0,
          order.getSide(), 0L);
    }
    message.setClOrdId(order.getClOrdId());
    message.setOrderQty(order.getQty());
    message.setOrderQtyScale(order.getQtyScale());
    message.setPrice(order.getPrice());
    message.setPriceScale(order.getPriceScale());
    return message;
  }

  private JsonNode submitExchangeAction(final Map<String, Object> action) throws Exception {
    final Map<String, Object> payload = buildSignedPayload(action);
    final HttpUtils.Response response = post(restApiBase + "/exchange", JSON_MAPPER.writeValueAsString(payload));
    if (response == null) {
       return null;
    }
    final JsonNode root = JSON_MAPPER.readTree(response.getData());
    if (!"ok".equalsIgnoreCase(root.path("status").asText())) {
      // "response" isn't always a plain string (e.g. cancelByCloid's per-cancel errors come back
      // nested under response.data.statuses) - .asText() on a non-text node silently yields "", so
      // include the full raw body instead of guessing its shape.
      throw new IOException("Hyperliquid exchange action rejected: " + response.getData());
    }
    return root.path("response");
  }

  private long nextNonce() {
    final AtomicLong sequence = NONCE_BY_SIGNER.computeIfAbsent(credentials.getAddress(), key -> new AtomicLong());
    return sequence.updateAndGet(previous -> Math.max(System.currentTimeMillis(), previous + 1));
  }

  /** Signs an action and returns the {@code {action, nonce, signature}} payload (used for both REST and WS post). */
  private Map<String, Object> buildSignedPayload(final Map<String, Object> action) throws Exception {
    final long nonce = nextNonce();
    final byte[] digest = hashForSigning(action, nonce);
    final Sign.SignatureData signed = Sign.signMessage(digest, credentials.getEcKeyPair(), false);

    final Map<String, Object> signature = new LinkedHashMap<>();
    signature.put("r", Numeric.toHexString(signed.getR()));
    signature.put("s", Numeric.toHexString(signed.getS()));
    signature.put("v", Byte.toUnsignedInt(signed.getV()[0]));

    final Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("action", action);
    payload.put("nonce", nonce);
    payload.put("signature", signature);
    return payload;
  }

  /**
   * Builds the signed {@code order} action payload (same shape as {@link #sendSpotOrderREST}/
   * {@link #sendPerpOrderREST}) as a JSON string, for the WS trade listeners to send via the
   * {@code {"method":"post","request":{"type":"action","payload":...}}} envelope instead of REST.
   */
  public String buildSignedOrderPayloadJson(final int assetId, final boolean isBuy, final String price,
      final String qty, final String cloid, final boolean reduceOnly, final TimeInForce timeInForce) throws Exception {
    return JSON_MAPPER.writeValueAsString(buildSignedPayload(
        buildOrderAction(assetId, isBuy, price, qty, cloid, reduceOnly, timeInForce)));
  }

  private static Map<String, Object> buildOrderAction(final int assetId, final boolean isBuy,
      final String price, final String qty, final String cloid, final boolean reduceOnly,
      final TimeInForce timeInForce) {
    final Map<String, Object> orderMap = new LinkedHashMap<>();
    orderMap.put("a", assetId);
    orderMap.put("b", isBuy);
    orderMap.put("p", price);
    orderMap.put("s", qty);
    orderMap.put("r", reduceOnly);
    final Map<String, Object> limit = new LinkedHashMap<>();
    limit.put("tif", toHyperliquidTif(timeInForce));
    final Map<String, Object> type = new LinkedHashMap<>();
    type.put("limit", limit);
    orderMap.put("t", type);
    if (cloid != null) {
      orderMap.put("c", cloid);
    }

    final Map<String, Object> action = new LinkedHashMap<>();
    action.put("type", "order");
    action.put("orders", List.of(orderMap));
    action.put("grouping", "na");
    return action;
  }

  /**
   * Maps the engine's requested time-in-force to Hyperliquid's native limit-order TIF. Hyperliquid
   * has no FOK - callers must reject a FOK request before it ever reaches here (see
   * HyperliquidFastClient.sendOrder); this method only has to choose between the two TIFs
   * Hyperliquid actually accepts.
   */
  private static String toHyperliquidTif(final TimeInForce timeInForce) {
    return timeInForce == TimeInForce.IMMEDIATE_OR_CANCEL ? "Ioc" : "Gtc";
  }

  /** Hyperliquid's L1 action signature: msgpack(action) + nonce + vault byte, hashed and wrapped as EIP-712. */
  private byte[] hashForSigning(final Map<String, Object> action, final long nonce) throws Exception {
    final byte[] packedAction = packMsgpack(action);
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream(packedAction.length + 9);
    bytes.write(packedAction);
    bytes.write(ByteBuffer.allocate(Long.BYTES).putLong(nonce).array());
    bytes.write(0); // no vault address
    final byte[] connectionId = Hash.sha3(bytes.toByteArray());

    final Map<String, Object> domain = new LinkedHashMap<>();
    domain.put("name", "Exchange");
    domain.put("version", "1");
    domain.put("chainId", 1337);
    domain.put("verifyingContract", ZERO_ADDRESS);

    final Map<String, Object> message = new LinkedHashMap<>();
    message.put("source", mainnet ? "a" : "b");
    message.put("connectionId", Numeric.toHexString(connectionId));

    final Map<String, Object> types = new LinkedHashMap<>();
    types.put("Agent", List.of(field("source", "string"), field("connectionId", "bytes32")));
    types.put("EIP712Domain", List.of(field("name", "string"), field("version", "string"),
        field("chainId", "uint256"), field("verifyingContract", "address")));

    final Map<String, Object> typedData = new LinkedHashMap<>();
    typedData.put("domain", domain);
    typedData.put("types", types);
    typedData.put("primaryType", "Agent");
    typedData.put("message", message);

    final StructuredDataEncoder encoder = new StructuredDataEncoder(JSON_MAPPER.writeValueAsString(typedData));
    return encoder.hashStructuredData();
  }

  private static Map<String, String> field(final String name, final String type) {
    final Map<String, String> field = new LinkedHashMap<>();
    field.put("name", name);
    field.put("type", type);
    return field;
  }

  /** Encodes a Hyperliquid action to msgpack bytes using msgpack-core directly (no Jackson dataformat). */
  private static byte[] packMsgpack(final Map<String, Object> action) throws IOException {
    final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
    packValue(packer, action);
    packer.close();
    return packer.toByteArray();
  }

  @SuppressWarnings("unchecked")
  private static void packValue(final MessageBufferPacker packer, final Object value) throws IOException {
    if (value == null) {
      packer.packNil();
    } else if (value instanceof Map) {
      final Map<String, Object> map = (Map<String, Object>) value;
      packer.packMapHeader(map.size());
      for (final Map.Entry<String, Object> entry : map.entrySet()) {
        packer.packString(entry.getKey());
        packValue(packer, entry.getValue());
      }
    } else if (value instanceof List) {
      final List<Object> list = (List<Object>) value;
      packer.packArrayHeader(list.size());
      for (final Object item : list) {
        packValue(packer, item);
      }
    } else if (value instanceof String) {
      packer.packString((String) value);
    } else if (value instanceof Boolean) {
      packer.packBoolean((Boolean) value);
    } else if (value instanceof Integer) {
      packer.packInt((Integer) value);
    } else if (value instanceof Long) {
      packer.packLong((Long) value);
    } else {
      throw new IllegalArgumentException("Unsupported msgpack value type: " + value.getClass());
    }
  }

  /** Perps of the main dex and the commodities dex the loader uses: isDelisted, or no longer listed. Null on failure. */
  public List<DelistedSymbol> getFuturesDelistedSymbols() {
    final long now = System.currentTimeMillis();
    final List<DelistedSymbol> delisted = new ArrayList<>();
    final Map<String, String[]> listed = new HashMap<>();
    for (final String dex : new String[] {"", COMMODITIES_DEX}) {
      final JsonNode universe = readInfo("{\"type\":\"meta\",\"dex\":\"" + dex + "\"}", "universe");
      if (universe == null) { // keep the last state: a partial list would drop the other dex's entries
        return null;
      }
      for (final JsonNode market : universe) {
        final String name = market.path("name").asText(); // main dex "BTC", commodities dex "xyz:CL"
        if (name.isEmpty()) {
          continue;
        }
        listed.put(name, new String[] {name, "USDC"});
        if (market.path("isDelisted").asBoolean(false)) {
          delisted.add(toDelistedSymbol(name, name, "USDC", true, "isDelisted", now));
        }
      }
    }
    return withNoLongerListed(delisted, listed, true, now);
  }

  /** Spot pairs (base + quote, as the loader names them) no longer listed; spotMeta has no delisted flag. Null on failure. */
  public List<DelistedSymbol> getSpotDelistedSymbols() {
    final long now = System.currentTimeMillis();
    final JsonNode meta = readInfo("{\"type\":\"spotMeta\"}", null);
    if (meta == null || !meta.path("universe").isArray()) {
      return null;
    }
    final Map<Integer, String> tokenNames = new HashMap<>();
    meta.path("tokens").forEach(token -> tokenNames.put(token.path("index").asInt(), token.path("name").asText()));
    final Map<String, String[]> listed = new HashMap<>();
    for (final JsonNode market : meta.path("universe")) {
      final JsonNode tokens = market.path("tokens");
      final String base = tokens.size() == 2 ? tokenNames.get(tokens.get(0).asInt()) : null;
      final String quote = tokens.size() == 2 ? tokenNames.get(tokens.get(1).asInt()) : null;
      if (base != null && quote != null) {
        listed.put(base + quote, new String[] {base, quote});
      }
    }
    return withNoLongerListed(new ArrayList<>(), listed, false, now);
  }

  private List<DelistedSymbol> withNoLongerListed(final List<DelistedSymbol> delisted, final Map<String, String[]> listed,
      final boolean futures, final long now) {
    final List<DelistedSymbol> removed = (futures ? futuresTracker : spotTracker).update(subscription.getExchange(), futures, listed, now);
    if (removed == null) {
      return null;
    }
    delisted.addAll(removed);
    return delisted;
  }

  // POST /info; the given field of the answer (or the whole answer), null when the request failed
  private JsonNode readInfo(final String body, final String field) {
    final HttpUtils.Response response = postInfo(body);
    if (response == null || response.getCode() != 200) {
      LOGGER.warn("Hyperliquid info " + body + " failed with status: " + (response != null ? response.getCode() : "null"));
      return null;
    }
    try {
      final JsonNode node = JSON_MAPPER.readTree(response.getData());
      final JsonNode value = field == null ? node : node.path(field);
      return field == null || value.isArray() ? value : null;
    } catch (final Exception e) {
      LOGGER.error("Hyperliquid info " + body + " parse failed", e);
      return null;
    }
  }

  private DelistedSymbol toDelistedSymbol(final String symbol, final String base, final String quote, final boolean futures,
      final String status, final long now) {
    final DelistedSymbol state = new DelistedSymbol();
    state.setExchange(subscription.getExchange());
    state.setFutures(futures);
    state.setSymbol(symbol);
    state.setBase(base);
    state.setQuote(quote);
    state.setStatus(status);
    state.setTradingDisabled(true);
    state.setDetectedAt(now);
    return state;
  }

  HttpUtils.Response postInfo(final String body) {
    return post(restApiBase + "/info", body);
  }

  private HttpUtils.Response post(final String url, final String body) {
    final Map<String, Object> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    return HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8),
        subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
  }
}
