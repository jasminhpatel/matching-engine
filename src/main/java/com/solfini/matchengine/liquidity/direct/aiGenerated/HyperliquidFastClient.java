package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.hyperliquid.HyperliquidPositionSnapshot;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.hyperliquid.HyperliquidRestClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.hyperliquid.HyperliquidTradeListener;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.hyperliquid.HyperliquidUserDataListener;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.MbxMath;
import com.solfini.util.StringUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Hyperliquid integration: spot and perp order lifecycle (send/cancel/query) plus market
 * data/balance/position, via REST and WebSocket calls per the Hyperliquid API docs
 * (https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api).
 */
public class HyperliquidFastClient implements ExternalExchangeClient {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(HyperliquidFastClient.class);
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final long TEN_MINUTES = 600_000;
  private static final long ONE_MINUTE = 60_000;
  private static final int ORDER_WS_WAIT_MILLIS = 2_000;
  /** Above this, Hyperliquid allows only integer prices. */
  private static final long HL_INTEGER_PRICE_THRESHOLD = 100_000L;

  private final ExchangeSubscription subscription;
  private final HyperliquidRestClient restClient;
  private final HyperliquidUserDataListener dataListener;
  private final HyperliquidTradeListener tradeListener;
  private final String walletAddress;
  private final boolean restOnly;

  private volatile Thread spotAccountRefreshThread;
  private volatile Thread futureAccountRefreshThread;
  private volatile boolean isSpotAccountRefreshRunning = false;
  private volatile boolean isFutureAccountRefreshRunning = false;

  // cancelOrder() only receives the Order, so remember which asset each open clOrdId belongs to.
  private final ConcurrentHashMap<String, Integer> assetIdByClOrdId = new ConcurrentHashMap<>();

  public HyperliquidFastClient(final ExchangeSubscription subscription) {
    this(subscription, Context.isHyperliquidMainnet(), false);
  }

  public HyperliquidFastClient(final ExchangeSubscription subscription, final boolean mainnet) {
    this(subscription, mainnet, false);
  }

  public HyperliquidFastClient(final ExchangeSubscription subscription, final boolean mainnet, final boolean restOnly) {
    this.restOnly = restOnly;
    this.subscription = subscription;
    this.walletAddress = subscription.getApiKey();
    this.restClient = new HyperliquidRestClient(subscription.getApiSecret(), subscription, mainnet);

    if (restOnly) {
      this.dataListener = null;
      this.tradeListener = null;
    } else {
      this.dataListener = new HyperliquidUserDataListener(walletAddress, subscription, mainnet,
          assetIdByClOrdId::remove);
      this.tradeListener = new HyperliquidTradeListener(subscription, restClient, mainnet);
    }
  }

  @Override
  public void start() {
    start(false);
  }

  private void start(final boolean parallel) {
    try {
      if (parallel) {
        //final Long startNanos = ExternalExchangeClientBootstrapper.START_NANOS.get(subscription.getId());
        final Executor executor = command -> Thread.ofVirtual().start(command);
        final CompletableFuture<Void> balance = startupTask("balance", this::bootstrapSpotBalanceSnapshot, executor);
        final CompletableFuture<Void> positions = startupTask("position", this::bootstrapAccountSnapshot, executor);
        final CompletableFuture<Void> orders = startupTask("order", this::bootstrapOpenOrders, executor);
        final CompletableFuture<Void> websocket = restOnly ? CompletableFuture.completedFuture(null)
            : startupTask("websocket", this::connectStreams, executor);
        CompletableFuture.allOf(balance, positions, orders, websocket).join();
/*        if (startNanos != null) {
          final long elapsed = (System.nanoTime() - startNanos) / 1_000_000L;
          ExternalExchangeClientBootstrapper.STARTUP_ELAPSED_MILLIS.put(subscription.getId(), elapsed);
          LOGGER.info("startParallel completed: " + elapsed + "ms");
        }*/
      } else {
        restClient.ensureSpotMetaLoaded();
        bootstrapOpenOrders();
        bootstrapSpotBalanceSnapshot();
        bootstrapAccountSnapshot();
        if (!restOnly) {
          connectStreams();
        }
      }
      if (restOnly) {
        LOGGER.info("HyperliquidFastClient started in REST-only mode");
      } else {
        startPeriodicSpotAccountRefresh();
        startPeriodicFutureAccountRefresh();
        LOGGER.info("HyperliquidFastClient started for wallet: " + walletAddress);
      }
    } catch (final Exception e) {
      try {
        stop();
      } catch (final Exception cleanupFailure) {
        e.addSuppressed(cleanupFailure);
      }
      throw new RuntimeException("Failed to start HyperliquidFastClient", e);
    }
  }

  @FunctionalInterface
  private interface StartupTask {
    void run() throws Exception;
  }

  private CompletableFuture<Void> startupTask(final String name, final StartupTask task, final Executor executor) {
    return CompletableFuture.runAsync(() -> {
      final long startNanos = System.nanoTime();
      try {
        task.run();
      } catch (final Exception e) {
        throw new RuntimeException("Hyperliquid startup task failed: " + name, e);
      }
      logMethodTiming(name, startNanos);
    }, executor);
  }

  private void connectStreams() throws Exception {
    dataListener.connect();
    tradeListener.connect();
  }

  private void logMethodTiming(final String methodName, final long methodStartNanos) {
    final long durationMillis = (System.nanoTime() - methodStartNanos) / 1_000_000L;
    LOGGER.info(methodName + ": " + durationMillis + "ms");
  }

  @Override
  public void stop() {
    stopPeriodicSpotAccountRefresh();
    stopPeriodicFutureAccountRefresh();
    if (!restOnly) {
      try {
        dataListener.disconnect();
      } finally {
        tradeListener.disconnect();
      }
    }
    assetIdByClOrdId.clear();
    LOGGER.info("HyperliquidFastClient stopped");
  }

  // Start periodic account snapshot refresh (spot)
  private void startPeriodicSpotAccountRefresh() {
    if (isSpotAccountRefreshRunning) {
      LOGGER.warn("Periodic Hyperliquid spot account refresh already running");
      return;
    }

    isSpotAccountRefreshRunning = true;
    spotAccountRefreshThread = startAccountRefresh("spot", this::bootstrapSpotBalanceSnapshot,
        () -> isSpotAccountRefreshRunning);
  }

  private void startPeriodicFutureAccountRefresh() {
    if (isFutureAccountRefreshRunning) {
      LOGGER.warn("Periodic Hyperliquid future account refresh already running");
      return;
    }
    isFutureAccountRefreshRunning = true;
    futureAccountRefreshThread = startAccountRefresh("future", this::bootstrapAccountSnapshot,
        () -> isFutureAccountRefreshRunning);
  }

  private Thread startAccountRefresh(final String name, final StartupTask refresh,
      final java.util.function.BooleanSupplier running) {
    return Thread.ofVirtual().start(() -> {
      while (running.getAsBoolean()) {
        long delay = TEN_MINUTES;
        try {
          refresh.run();
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        } catch (final Exception e) {
          LOGGER.error("Periodic Hyperliquid " + name + " account snapshot failed", e);
          delay = ONE_MINUTE;
        }
        try {
          Thread.sleep(delay);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    });
  }

  // Stop periodic refresh and cleanup (spot)
  private void stopPeriodicSpotAccountRefresh() {
    if (!isSpotAccountRefreshRunning) {
      return;
    }

    isSpotAccountRefreshRunning = false;
    LOGGER.info("Stopping periodic Hyperliquid spot account refresh");

    if (spotAccountRefreshThread != null) {
      spotAccountRefreshThread.interrupt();
    }
  }

  // Stop periodic refresh and cleanup (futures)
  private void stopPeriodicFutureAccountRefresh() {
    if (!isFutureAccountRefreshRunning) {
      return;
    }

    isFutureAccountRefreshRunning = false;
    LOGGER.info("Stopping periodic Hyperliquid future account refresh");

    if (futureAccountRefreshThread != null) {
      futureAccountRefreshThread.interrupt();
    }
  }

  // REST merges main and xyz DEX orders. Cache only the subscription's spot or perp segment.
  private void bootstrapOpenOrders() {
    final String openOrdersJson = getAllOpenOrders();
    if (openOrdersJson == null) {
      LOGGER.warn("Bootstrap open orders: REST call returned null");
      return;
    }
    final Map<String, Order> orders = extractOpenOrders(openOrdersJson);
    int applied = 0;
    for (final Order order : orders.values()) {
      // Spot coins are named either "@{index}" or "BASE/QUOTE".
      final boolean isSpotOrder = order.getSymbol() != null
          && (order.getSymbol().contains("/") || order.getSymbol().startsWith("@"));
      if (isSpotOrder == subscription.isFuturesEnabled()) {
        continue;
      }
      subscription.cacheNewOrder(order);
      resolveAndCacheAssetId(order, isSpotOrder);
      applied++;
    }
    LOGGER.info("Bootstrap open orders applied: " + applied + " order(s) (of " + orders.size() + " total on wallet)");
  }

  /**
   * Restored orders (from a restart) previously had no entry in {@link #assetIdByClOrdId}, so
   * cancelOrder() always failed for them with "unknown assetId" - resolve and cache it here too,
   * keyed by the same clOrdId the order was restored under
   */
  private void resolveAndCacheAssetId(final Order order, final boolean isSpotOrder) {
    try {
      final Integer assetId = isSpotOrder
          ? restClient.ensureAssetIdForCoin(order.getSymbol())
          : restClient.ensurePerpAssetId(order.getSymbol());
      if (assetId != null) {
        assetIdByClOrdId.put(order.getClOrdId(), assetId);
      } else {
        LOGGER.warn("Hyperliquid bootstrap: no assetId found for restored order coin=" + order.getSymbol());
      }
    } catch (final Exception e) {
      LOGGER.warn("Hyperliquid bootstrap: failed to resolve assetId for restored order coin=" + order.getSymbol());
    }
  }

  private Map<String, Order> extractOpenOrders(final String json) {
    final Map<String, Order> orders = new HashMap<>();
    try {
      for (final JsonNode obj : JSON_MAPPER.readTree(json)) {
        final long oid = obj.path("oid").asLong();
        final String cloid = obj.has("cloid") && !obj.path("cloid").isNull() ? obj.path("cloid").asText() : null;
        final String key = cloid != null ? cloid : ("oid:" + oid);

        final String coin = obj.path("coin").asText();
        final String sideStr = obj.path("side").asText(); // "A" = ask/sell, "B" = bid/buy
        final double limitPx = obj.path("limitPx").asDouble();
        final double sz = obj.path("sz").asDouble();

        final boolean isSpotOrder = coin.contains("/") || coin.startsWith("@");
        final short qtyScale = (short) restClient.ensureQuantityScaleForCoin(coin, isSpotOrder);
        final short priceScale = (short) Math.max(0, (isSpotOrder ? 8 : 6) - qtyScale);

        final Order order = new Order();
        order.setClOrdId(key);
        order.setOrderId(oid);
        if (!coin.isEmpty()) {
          order.setSymbol(coin);
        }
        order.setSide("A".equalsIgnoreCase(sideStr) ? Side.SELL : Side.BUY);
        order.setPrice(MbxMath.changeScale(limitPx, priceScale), priceScale);
        order.setQty(MbxMath.changeScale(sz, qtyScale), qtyScale);
        order.setExecuted(false);
        order.setRejected(false);

        orders.put(key, order);
      }
    } catch (final Exception e) {
      LOGGER.error("Failed to parse Hyperliquid open orders: " + e.getMessage(), e);
    }
    return orders;
  }

  @Override
  public ExecutionReportMessage sendOrder(final Order order, final boolean futuresEnabled,
      final String bestQuoteSymbol, final String baseSymbol, final int priceScale,
      final int qtyScale, final double fxRate) throws Exception {

    // Hyperliquid does not support FOK orders, so convert them to IOC orders.
    if (order.getTimeInForce() == TimeInForce.FILL_OR_KILL) {
      order.setTimeInForce(TimeInForce.IMMEDIATE_OR_CANCEL);
    }
    submitOrder(order, futuresEnabled, baseSymbol, bestQuoteSymbol);
    return subscription.getExecutionReport(order.getClOrdId());
  }

  /**
   * Rejects FOK locally because Hyperliquid only supports Alo, Ioc, and Gtc. IOC is not an
   * equivalent fallback because it may partially fill before canceling the remainder.
   */
  private void rejectUnsupportedFok(final Order order) {
    order.setRejected(true);
    final ExecutionReportMessage message = getOrCreateExecutionReport(order);
    message.setOrdStatus(OrdStatus.REJECTED);
    message.setExecType(ExecType.REJECTED);
    message.setError("Hyperliquid does not support FOK time-in-force (only Alo/Ioc/Gtc are supported)");
    subscription.updateExecutionReport(message);
    LOGGER.warn("Rejected Hyperliquid order clOrdId=" + order.getClOrdId()
        + " because FOK is not supported");
  }

  private void queryOrder(final Order order, final String cloid) {
    try {
      final JsonNode response = restClient.getOrderStatusByCloid(walletAddress, cloid);
      if ("order".equals(response.path("status").asText())) {
        applyOrderStatusDetails(order, response.path("order"));
      }
    } catch (final Exception e) {
      LOGGER.error("Hyperliquid REST order-status poll failed for clOrdId: " + order.getClOrdId(), e);
    }
    subscription.updateOrder(order.getClOrdId(), order);
  }

  private void submitOrder(final Order order, final boolean futures, final String base, final String quote)
      throws Exception {
    final String market = order.getSymbol() != null && order.getSymbol().contains(":") ? order.getSymbol() : base;
    final int assetId = futures ? restClient.ensurePerpAssetId(market) : restClient.ensureAssetId(base, quote);
    final boolean isBuy = order.getSide() == Side.BUY;
    final long roundedPrice =
        toHyperliquidPrice(order.getPrice(), order.getPriceScale(), order.getQtyScale(), futures, isBuy);
    final String price = toWireNumber(roundedPrice, order.getPriceScale());
    if (roundedPrice != order.getPrice()) {
      LOGGER.info("Hyperliquid price snapped to tick. clOrdId: " + order.getClOrdId() + " from: "
          + toWireNumber(order.getPrice(), order.getPriceScale()) + " to: " + price);
      order.setPrice(roundedPrice, order.getPriceScale());
    }
    final String quantity = toWireNumber(order.getQty(), order.getQtyScale());
    final String cloid = toCloid(order.getClOrdId());
    subscription.cacheNewOrder(order);
    assetIdByClOrdId.put(order.getClOrdId(), assetId);
    if (tradeListener != null && tradeListener.isConnected()) {
      if (futures) {
        tradeListener.sendPerpOrder(order, assetId, isBuy, price, quantity, cloid, order.isReduceOnly());
      } else {
        tradeListener.sendSpotOrder(order, assetId, isBuy, price, quantity, cloid);
      }
    } else if (futures) {
      restClient.sendPerpOrderREST(order, assetId, isBuy, price, quantity, cloid, order.isReduceOnly());
    } else {
      restClient.sendSpotOrderREST(order, assetId, isBuy, price, quantity, cloid);
    }
    awaitOrderCompletion(order, cloid, futures ? "PERP" : "SPOT");
  }

  private void awaitOrderCompletion(final Order order, final String cloid, final String market)
      throws InterruptedException {
    if (!restOnly) {
      for (int i = 0; i < ORDER_WS_WAIT_MILLIS; i++) {
        if (isOrderComplete(order)) {
          assetIdByClOrdId.remove(order.getClOrdId());
          return;
        }
        Thread.sleep(1);
      }
    }

    for (int i = 0; i < 100; i++) {
      queryOrder(order, cloid);
      if (isOrderComplete(order)) {
        assetIdByClOrdId.remove(order.getClOrdId());
        return;
      }
      Thread.sleep(50);
    }
    LOGGER.warn(market + ": couldn't obtain a terminal order status for: " + order.getClOrdId());
  }

  @Override
  public boolean cancelOrder(final Order order, final boolean isSpotOrder) {
    final Integer assetId = assetIdByClOrdId.get(order.getClOrdId());
    if (assetId == null) {
      LOGGER.warn("Cannot cancel Hyperliquid order, unknown assetId for clOrdId: " + order.getClOrdId());
      return false;
    }
    final String cloid = toCloid(order.getClOrdId());
    if (tradeListener != null && tradeListener.isConnected()) {
      LOGGER.debug("Cancelling Hyperliquid order via WebSocket for clOrdId: " + order.getClOrdId());
      return recordCancelResult(order, tradeListener.cancelOrder(assetId, cloid));
    }
    LOGGER.debug("Cancelling Hyperliquid order via REST for clOrdId: " + order.getClOrdId());
    return recordCancelResult(order, restClient.cancelSpotOrderRest(assetId, cloid));
  }

  private boolean recordCancelResult(final Order order, final boolean confirmed) {
    if (confirmed) {
      assetIdByClOrdId.remove(order.getClOrdId());
    }
    return confirmed;
  }

  @Override
  public double getBalance(final String asset) {
    final double cached = subscription.getBalance(asset);
    if (cached != 0.0) {
      return cached;
    }
    try {
      bootstrapSpotBalanceSnapshot();
    } catch (final Exception e) {
      LOGGER.error("Failed to refresh Hyperliquid balance for asset: " + asset, e);
    }
    return subscription.getBalance(asset);
  }

  /** Spot balances: sum of every coin's free (total - hold) amount from spotClearinghouseState. */
  private void bootstrapSpotBalanceSnapshot() throws IOException {
    final JsonNode balances = restClient.getSpotBalances(walletAddress).path("balances");
    for (final JsonNode balance : balances) {
      final String coin = balance.path("coin").asText();
      final double total = balance.path("total").asDouble();
      final double hold = balance.path("hold").asDouble();
      subscription.updateBalance(coin, total - hold);
    }
  }


  private void bootstrapAccountSnapshot() throws IOException {
    applyPerpPositions(restClient.getPerpClearinghouseState(walletAddress), "");
    try {
      applyPerpPositions(restClient.getPerpClearinghouseState(walletAddress, HyperliquidRestClient.COMMODITIES_DEX),
          HyperliquidRestClient.COMMODITIES_DEX);
    } catch (final Exception e) {
      LOGGER.error("Failed to refresh Hyperliquid commodities (xyz dex) positions: " + e.getMessage(), e);
    }
  }

  private void applyPerpPositions(final JsonNode state, final String dex) {
    HyperliquidPositionSnapshot.apply(subscription, state, dex);
  }

  @Override
  public double getPosition(final String symbol) {
    final double cached = subscription.getPosition(symbol);
    if (cached != 0.0) {
      return cached;
    }
    try {
      bootstrapAccountSnapshot();
    } catch (final Exception e) {
      LOGGER.error("Failed to refresh Hyperliquid position for symbol: " + symbol, e);
    }
    return subscription.getPosition(symbol);
  }

  /** Raw open-orders JSON for this wallet, matching every other exchange's getAllOpenOrders(). */
  public String getAllOpenOrders() {
    return restClient.getOpenOrders(walletAddress);
  }

  @Override
  public ExecutionReportMessage getOrder(final String clOrdId, final String orderId) {
    final ExecutionReportMessage cached = subscription.getExecutionReport(clOrdId);
    if (cached != null) {
      return cached;
    }
    final Order order = subscription.getOrder(clOrdId);
    if (order == null) {
      return null;
    }
    queryOrder(order, toCloid(clOrdId));
    return subscription.getExecutionReport(clOrdId);
  }

  private void applyOrderStatusDetails(final Order order, final JsonNode orderDetails) {
    final String hyperliquidStatus = orderDetails.path("status").asText();
    final JsonNode orderSnapshot = orderDetails.path("order");
    if (orderSnapshot.has("oid")) {
      order.setOrderId(orderSnapshot.path("oid").asLong());
    }
    final ExecutionReportMessage message = getOrCreateExecutionReport(order);
    if (isOpenStatus(hyperliquidStatus)) {
      message.setOrdStatus(OrdStatus.NEW);
      message.setExecType(ExecType.NEW);
    } else if ("filled".equalsIgnoreCase(hyperliquidStatus)) {
      applyTerminalQuantities(order, message, orderSnapshot);
      order.setExecuted(true);
      message.setOrdStatus(OrdStatus.FILLED);
      message.setExecType(ExecType.TRADE);
    } else if (isCanceledStatus(hyperliquidStatus)) {
      applyTerminalQuantities(order, message, orderSnapshot);
      message.setOrdStatus(OrdStatus.CANCELED);
      message.setExecType(ExecType.CANCELED);
      if (message.getCumQty() > 0) {
        order.setExecuted(true);
      } else {
        order.setRejected(true);
      }
    } else if (isRejectedStatus(hyperliquidStatus)) {
      order.setRejected(true);
      message.setOrdStatus(OrdStatus.REJECTED);
      message.setExecType(ExecType.REJECTED);
    } else {
      LOGGER.warn("Hyperliquid orderStatus: unrecognized status '" + hyperliquidStatus
          + "' for clOrdId=" + order.getClOrdId());
    }
    subscription.updateExecutionReport(message);
    subscription.updateOrder(order.getClOrdId(), order);
    if (isOrderComplete(order)) {
      assetIdByClOrdId.remove(order.getClOrdId());
    }
  }

  private void applyTerminalQuantities(final Order order, final ExecutionReportMessage message,
      final JsonNode orderSnapshot) {
    if (!orderSnapshot.has("origSz") || !orderSnapshot.has("sz")) {
      return;
    }
    final long originalQty = MbxMath.changeScale(orderSnapshot.path("origSz").asDouble(), order.getQtyScale());
    final long leavesQty = MbxMath.changeScale(orderSnapshot.path("sz").asDouble(), order.getQtyScale());
    final long cumulativeQty = Math.max(0, originalQty - leavesQty);
    message.setCumQty(cumulativeQty);
    message.setCumQtyScale(order.getQtyScale());
    message.setLeavesQty(leavesQty);
    message.setLeavesQtyScale(order.getQtyScale());
    if (cumulativeQty > 0 && message.getAvgPx() == 0 && orderSnapshot.has("limitPx")) {
      message.setAvgPx(MbxMath.changeScale(orderSnapshot.path("limitPx").asDouble(), order.getPriceScale()));
      message.setAvgPxScale(order.getPriceScale());
    }
  }

  @Override
  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    return restClient.getExchangeInstrumentsFull();
  }

  @Override
  public Ticker getTicker(final String base, final String quote, final int instrumentType) {
    try {
      final String coin = instrumentType == 1 ? base : restClient.ensureCoinName(base, quote);
      final int separator = instrumentType == 1 && coin != null ? coin.indexOf(':') : -1;
      final String dex = separator >= 0 ? coin.substring(0, separator) : "";
      final String market = separator >= 0 ? coin.substring(separator + 1) : coin;
      Ticker ticker = restClient.getTicker(market, dex);
      if (ticker == null && instrumentType == 1 && separator < 0) {
        ticker = restClient.getTicker(coin, HyperliquidRestClient.COMMODITIES_DEX);
      }
      if (ticker != null) {
        ticker.setInstrumentType(instrumentType);
      }
      return ticker;
    } catch (final Exception e) {
      LOGGER.error("Get ticker failed for " + base + "/" + quote + " " + e.getMessage());
      return null;
    }
  }

  /**
   * Perp ticker on a builder-deployed dex (HIP-3, e.g. Hyperliquid's "xyz" dex for commodity/
   * equity perps like WTI crude oil ("CL") / Brent oil ("BRENTOIL")). Not part of the
   * {@code ExternalExchangeClient} interface since dex-scoping isn't part of that contract -
   * call directly when you need a symbol outside the main perp dex.
   */
  public Ticker getPerpTicker(final String coin, final String dex) {
    return restClient.getTicker(coin, dex);
  }

  /**
   * Hyperliquid client order ids must be "0x" followed by exactly 32 hex chars; derive one from
   * our clOrdId. Public so HyperliquidUserDataListener can correlate an orderUpdates push by
   * recomputing this from a cached order's clOrdId, rather than only by exchange oid.
   */
  public static String toCloid(final String clOrdId) {
    if (clOrdId != null && clOrdId.matches("(?i)0x[0-9a-f]{32}")) {
      return clOrdId.toLowerCase(java.util.Locale.ROOT);
    }
    try {
      final MessageDigest md5 = MessageDigest.getInstance("MD5");
      final byte[] digest = md5.digest(clOrdId.getBytes(StandardCharsets.UTF_8));
      final StringBuilder sb = new StringBuilder(34).append("0x");
      for (final byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 not available", e);
    }
  }

  public static boolean isOpenStatus(final String status) {
    return "open".equalsIgnoreCase(status) || "triggered".equalsIgnoreCase(status);
  }

  public static boolean isCanceledStatus(final String status) {
    if (status == null) {
      return false;
    }
    final String normalized = status.toLowerCase(java.util.Locale.ROOT);
    return normalized.endsWith("canceled") || "scheduledcancel".equals(normalized);
  }

  public static boolean isRejectedStatus(final String status) {
    return status != null && status.toLowerCase(java.util.Locale.ROOT).endsWith("rejected");
  }

  private ExecutionReportMessage getOrCreateExecutionReport(final Order order) {
    ExecutionReportMessage message = subscription.getExecutionReport(order.getClOrdId());
    if (message == null) {
      message = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0,
          order.getSymbol(), 0L, order.getPriceScale(), 0L, order.getQtyScale(), 0, 0, 0, 0,
          order.getSide(), 0L);
      message.setClOrdId(order.getClOrdId());
      message.setOrderQty(order.getQty());
      message.setOrderQtyScale(order.getQtyScale());
      message.setPrice(order.getPrice());
      message.setPriceScale(order.getPriceScale());
    }
    return message;
  }

  private static boolean isOrderComplete(final Order order) {
    return order.isExecuted() || order.isRejected();
  }

  /**
   * Snaps a scaled price onto Hyperliquid's tick and returns it in the same scale. Non-integer
   * prices allow at most 5 significant figures and at most (6 - szDecimals) decimal places for
   * perps, or (8 - szDecimals) for spot. Buys floor and sells ceil.
   */
  static long toHyperliquidPrice(final long amount, final int priceScale, final int szDecimals,
      final boolean futures, final boolean isBuy) {
    if (amount <= 0 || priceScale < 0) {
      return amount;
    }
    final int maxPriceDecimals = Math.max(0, (futures ? 6 : 8) - Math.max(0, szDecimals));
    final int tickDecimals;
    if (amount > HL_INTEGER_PRICE_THRESHOLD * MbxMath.multiplier((short) priceScale)) {
      tickDecimals = 0;
    } else {
      int magnitude = -priceScale;
      long rest = amount;
      while (rest >= 10) {
        rest /= 10;
        magnitude++;
      }
      tickDecimals = Math.min(4 - magnitude, maxPriceDecimals);
    }
    if (tickDecimals >= priceScale) {
      return amount;
    }
    final long step = MbxMath.multiplier((short) (priceScale - tickDecimals));
    if (isBuy || amount % step == 0) {
      return (amount / step) * step;
    }
    return ((amount / step) + 1) * step;
  }

  /** Renders a scaled long as a plain decimal string with no trailing zeros, as Hyperliquid requires. */
  static String toWireNumber(final long amount, final int scale) {
    final String value = StringUtil.toNumericString(amount, scale);
    if (value.indexOf('.') < 0) {
      return value;
    }
    int end = value.length();
    while (value.charAt(end - 1) == '0') {
      end--;
    }
    if (value.charAt(end - 1) == '.') {
      end--;
    }
    return value.substring(0, end);
  }
}
