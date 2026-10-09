package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.liquidity.DelistedSymbolCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kraken.*;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.StringUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;

public final class KrakenFastClient implements ExternalExchangeClient {

    // ======= Configuration (Kraken Spot & Futures) =======
    private static final CustomLogger LOGGER = CustomLogger.getLogger(KrakenFastClient.class);
    private static final String WS_SPOT_USERDATA = "wss://ws-auth.kraken.com/v2";
    private static final String WS_FUTURES_USERDATA = "wss://futures.kraken.com/ws/v1";
    private static final long TEN_MINUTES = 600_000;
    private static final long ONE_MINUTE = 60_000;
    private final ExchangeSubscription subscription;

    // ======= API credentials =======
    private final String apiKey;
    private final String apiSecret;

    // ======= REST Client =======
    private final KrakenRestClient krakenRestClient;

    // ======= WebSocket Listeners =======
    private final KrakenSpotTradeListener spotTradeListener;
    private volatile KrakenSpotUserDataListener spotUserDataListener;
    private volatile KrakenFutureUserDataListener futureUserDataListener;

    // ======= Scheduled Tasks =======
    private volatile Thread spotAccountRefreshThread;
    private volatile Thread futureAccountRefreshThread;
    private volatile boolean isSpotAccountRefreshRunning = false;
    private volatile boolean isFutureAccountRefreshRunning = false;

    private final boolean restOnly;
    private static final String INSTRUMENT_WS_URL = "wss://ws.kraken.com/v2";
    private static final long INSTRUMENT_RECONNECT_MILLIS = 10_000;
    private volatile boolean instrumentStreamRunning;

    // ======= Constructor =======
    public KrakenFastClient(final ExchangeSubscription subscription) {
        this(subscription, false);
    }

    public KrakenFastClient(final ExchangeSubscription subscription, final boolean restOnly) {
        this.restOnly = restOnly;
        this.subscription = subscription;
        this.apiKey = Objects.requireNonNull(subscription.getApiKey());
        this.apiSecret = Objects.requireNonNull(subscription.getApiSecret());

        // Initialize REST client
        this.krakenRestClient = new KrakenRestClient(apiKey, apiSecret, subscription);

        // Initialize WebSocket trade listeners
        if (restOnly || subscription.isFuturesEnabled()) {
            this.spotTradeListener = null;
        } else {
            this.spotTradeListener = new KrakenSpotTradeListener(apiKey, apiSecret, subscription);
        }
    }

    @Override
    public void start() {
        // first, so a failing account login below does not stop it; delisted symbols now, then every 10 minutes
        DelistedSymbolCache.start(subscription);
        if (!restOnly && !subscription.isFuturesEnabled()) {
            startInstrumentStream();
        }
        if (subscription.isFuturesEnabled()) {
            startFutureClient();
        } else {
            startSpotClient();
        }
    }

    @Override
    public List<DelistedSymbol> getDelistedSymbols() {
        return subscription.isFuturesEnabled()
                ? krakenRestClient.getFuturesDelistedSymbols()
                : krakenRestClient.getSpotDelistedSymbols();
    }

    // Kraken v2 "instrument" channel (public): pair status changes, classified like the REST check, so a stop is
    // applied at once instead of at the next 10 minute check
    void startInstrumentStream() {
        instrumentStreamRunning = true;
        final Thread thread = new Thread(() -> {
            while (instrumentStreamRunning) {
                try {
                    runInstrumentStream(Long.MAX_VALUE);
                } catch (final Exception e) {
                    LOGGER.warn("Kraken instrument stream reconnects after error: " + e);
                }
                try {
                    Thread.sleep(INSTRUMENT_RECONNECT_MILLIS);
                } catch (final InterruptedException e) {
                    return;
                }
            }
        }, "krakenInstrumentStream");
        thread.setDaemon(true);
        thread.start();
    }

    // one connection, until it closes or maxMillis pass
    void runInstrumentStream(final long maxMillis) throws Exception {
        final CompletableFuture<Void> closed = new CompletableFuture<>();
        final WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(INSTRUMENT_WS_URL),
                new WebSocket.Listener() {
            private final StringBuilder text = new StringBuilder();

            @Override
            public CompletionStage<?> onText(final WebSocket webSocket, final CharSequence data, final boolean last) {
                text.append(data);
                if (last) {
                    final String json = text.toString();
                    text.setLength(0);
                    if (json.contains("\"success\":false")) {
                        LOGGER.error("Kraken instrument subscribe rejected, delisting updates come from the REST check only: " + json);
                    } else {
                        krakenRestClient.applyInstrumentMessage(json);
                    }
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(final WebSocket webSocket, final int statusCode, final String reason) {
                closed.complete(null);
                return null;
            }

            @Override
            public void onError(final WebSocket webSocket, final Throwable error) {
                closed.completeExceptionally(error);
            }
        }).get(15, TimeUnit.SECONDS);
        socket.sendText("{\"method\":\"subscribe\",\"params\":{\"channel\":\"instrument\",\"snapshot\":true}}", true);
        try {
            closed.get(maxMillis, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            // caller limit reached
        } finally {
            socket.abort();
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping KrakenFastClient...");
        instrumentStreamRunning = false;
        stopPeriodicFutureAccountRefresh();
        stopPeriodicSpotAccountRefresh();
        disconnectWebSockets();
    }

    @Override
    public ExecutionReportMessage sendOrder(final Order order, final boolean futuresEnabled, final String bestQuoteSymbol,
                                            final String baseSymbol, final int priceScale, final int qtyScale, final double fxRate)
            throws Exception {


        if (futuresEnabled) {
            sendFutureOrder(order, bestQuoteSymbol, baseSymbol, priceScale, qtyScale, fxRate);
        } else {
            sendSpotOrder(order);
        }
        return subscription.getExecutionReport(order.getClOrdId());
    }

    @Override
    public boolean cancelOrder(Order order, boolean isSpotOrder) {
        boolean result;
        if (isSpotOrder) {
            result = krakenRestClient.cancelSpotOrderRest(order, order.getClOrdId());
        } else {
            result = krakenRestClient.cancelFuturesOrderRest(order, order.getClOrdId());
        }
        return result;
    }

    // ======= Balance Transfer Methods =======
    private boolean transferBalanceFromSpotToFutures(final String asset, final String amount) throws Exception {
        LOGGER.info("Transferring balance from Spot to Futures: asset=" + asset + " amount=" + amount);
        return krakenRestClient.transferSpotToFutures(asset, amount);
    }

    public boolean transferBalance(final String asset, final String amount, final boolean fromSpotToFutures) throws Exception {
        if (fromSpotToFutures) {
            return transferBalanceFromSpotToFutures(asset, amount);
        } else {
            LOGGER.warn("Kraken does not support transfer from Futures to Spot via this method");
            return false;
        }
    }

    public String getAllOpenOrders() {
        if (subscription.isFuturesEnabled()) {
            return krakenRestClient.getAllOpenFuturesOrders();
        } else {
            return krakenRestClient.getAllOpenSpotOrders();
        }
    }


    @Override
    public List<ExternalSymbol> getExchangeInstrumentsFull() {
        return krakenRestClient.getExchangeInstrumentsFull();
    }

    // ======= Internal Methods =======
    private void startSpotClient() {
        try {
            LOGGER.info("Starting Kraken Spot Client...");

            // Get balance snapshot via REST
            bootstrapSpotBalanceSnapshot();

            // Start periodic balance refresh
            startPeriodicSpotAccountRefresh();

            if (restOnly) {
                LOGGER.info("KrakenFastClient started in REST-only mode — WebSocket connections skipped");
                return;
            }

            // Start WebSocket connections
            startUserSpotBalanceStream();
            startUserSpotTradeStream();

            LOGGER.info("Kraken Spot Client started successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to start Kraken Spot Client: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void startFutureClient() {
        try {
            LOGGER.info("Starting Kraken Futures Client...");

            // Get balance and positions snapshot via REST
            bootstrapFutureBalanceSnapshot();

            // Start periodic refresh
            startPeriodicFutureAccountRefresh();

            if (restOnly) {
                LOGGER.info("KrakenFastClient started in REST-only mode — WebSocket connections skipped");
                return;
            }

            // Start WebSocket connections
            startUserFutureDataStream();

            LOGGER.info("Kraken Futures Client started successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to start Kraken Futures Client: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    // ======= Bootstrap Methods =======
    private void bootstrapSpotBalanceSnapshot() {
        try {
            LOGGER.info("Bootstrapping Kraken Spot balance snapshot...");
            final String json = krakenRestClient.getBalanceSnapshot();
            if (json != null) {
                applySpotAccountSnapshot(json);
                LOGGER.info("Kraken Spot bootstrap snapshot applied successfully");
            } else {
                LOGGER.warn("Failed to retrieve Kraken Spot balance snapshot");
            }
        } catch (Exception e) {
            LOGGER.error("Error bootstrapping Kraken Spot balance: " + e.getMessage());
        }
    }

    private void bootstrapFutureBalanceSnapshot() {
        try {
            LOGGER.info("Bootstrapping Kraken Futures balance snapshot...");

            // Fetch futures account snapshot
            final String json = krakenRestClient.getAccountsSnapshot();
            if (json != null) {
                applyFutureAccountSnapshot(json);
                LOGGER.info("Kraken Futures bootstrap snapshot applied successfully");
            } else {
                LOGGER.warn("Failed to retrieve Kraken Futures balance snapshot");
            }

            // Fetch positions
            final String positionInfo = krakenRestClient.getAllPositions();
            updateOpenPosition(positionInfo);
        } catch (Exception e) {
            LOGGER.error("Error bootstrapping Kraken Futures balance: " + e.getMessage());
        }
    }

    private void updateOpenPosition(String positionInfo) {
        if (positionInfo == null) {
            LOGGER.warn("Kraken position info response is null");
            return;
        }

        // Parse Kraken position info JSON response
        final int dataStart = positionInfo.indexOf("\"openPositions\":");
        if (dataStart < 0) {
            LOGGER.warn("No \"openPositions\" section in Kraken position response");
            return;
        }

        final int listStart = positionInfo.indexOf("[", dataStart);
        if (listStart < 0) {
            LOGGER.warn("No array in Kraken position response data");
            return;
        }

        // Find the matching closing bracket for the array
        int bracketCount = 0;
        int arrayEnd = listStart;
        for (int i = listStart; i < positionInfo.length(); i++) {
            if (positionInfo.charAt(i) == '[') {
                bracketCount++;
            } else if (positionInfo.charAt(i) == ']') {
                bracketCount--;
                if (bracketCount == 0) {
                    arrayEnd = i;
                    break;
                }
            }
        }

        if (arrayEnd <= listStart) {
            LOGGER.warn("Cannot find closing ] for position array");
            return;
        }

        final String dataArray = positionInfo.substring(listStart + 1, arrayEnd);
        LOGGER.debug("Position data array content: " + dataArray.substring(0, Math.min(200, dataArray.length())) + "...");

        // Parse each position object in the data array
        int objStart = 0;
        while (objStart < dataArray.length()) {
            objStart = dataArray.indexOf('{', objStart);
            if (objStart < 0) break;

            // Find matching closing brace
            int braceCount = 0;
            int objEnd = objStart;
            for (int i = objStart; i < dataArray.length(); i++) {
                if (dataArray.charAt(i) == '{') {
                    braceCount++;
                } else if (dataArray.charAt(i) == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        objEnd = i;
                        break;
                    }
                }
            }

            if (objEnd <= objStart) break;

            final String positionObj = dataArray.substring(objStart, objEnd + 1);
            LOGGER.debug("Processing Kraken position object of length: " + positionObj.length());

            // Parse position data
            final String symbol = minExtract(positionObj, "symbol");
            final String side = minExtract(positionObj, "side");
            final double size = parseDoubleSafe(minExtract(positionObj, "size"));
            final double price = parseDoubleSafe(minExtract(positionObj, "price"));
            final double unrealizedFunding = parseDoubleSafe(minExtract(positionObj, "unrealizedFunding"));
            final String fillTime = minExtract(positionObj, "fillTime");
            final String pnlCurrency = minExtract(positionObj, "pnlCurrency");

            if (symbol != null && size != 0.0) {
                subscription.updatePosition(symbol, size);
                LOGGER.debug("Updated Kraken position: " + symbol + " side=" + side + " size=" + size +
                        " price=" + price + " unrealizedFunding=" + unrealizedFunding +
                        " fillTime=" + fillTime + " pnlCurrency=" + pnlCurrency);
            }

            objStart = objEnd + 1;
        }
    }

    // ======= Periodic Refresh Methods =======
    private void startPeriodicSpotAccountRefresh() {
        if (isSpotAccountRefreshRunning) {
            LOGGER.warn("Periodic spot account refresh already running");
            return;
        }

        isSpotAccountRefreshRunning = true;
        spotAccountRefreshThread = Thread.ofVirtual().start(() -> {
            while (isSpotAccountRefreshRunning) {
                try {
                    LOGGER.debug("Running periodic spot account snapshot refresh...");
                    bootstrapSpotBalanceSnapshot();
                    Thread.sleep(TEN_MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Periodic spot account snapshot refresh failed: " + e.getMessage());
                    try {
                        Thread.sleep(ONE_MINUTE);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    private void startPeriodicFutureAccountRefresh() {
        if (isFutureAccountRefreshRunning) {
            LOGGER.warn("Periodic future account refresh already running");
            return;
        }

        isFutureAccountRefreshRunning = true;
        futureAccountRefreshThread = Thread.ofVirtual().start(() -> {
            while (isFutureAccountRefreshRunning) {
                try {
                    LOGGER.debug("Running periodic future account snapshot refresh...");
                    bootstrapFutureBalanceSnapshot();
                    Thread.sleep(TEN_MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Periodic future account snapshot refresh failed: " + e.getMessage());
                    try {
                        Thread.sleep(ONE_MINUTE);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    private void stopPeriodicSpotAccountRefresh() {
        if (!isSpotAccountRefreshRunning) {
            return;
        }

        isSpotAccountRefreshRunning = false;
        LOGGER.info("Stopping periodic spot account refresh");

        if (spotAccountRefreshThread != null) {
            spotAccountRefreshThread.interrupt();
        }
    }

    private void stopPeriodicFutureAccountRefresh() {
        if (!isFutureAccountRefreshRunning) {
            return;
        }

        isFutureAccountRefreshRunning = false;
        LOGGER.info("Stopping periodic future account refresh");

        if (futureAccountRefreshThread != null) {
            futureAccountRefreshThread.interrupt();
        }
    }

    // ======= WebSocket Connection Methods =======
    private void startUserSpotBalanceStream() throws Exception {
        try {
            LOGGER.info("Connecting Kraken Spot UserData WS...");
            spotUserDataListener = new KrakenSpotUserDataListener(WS_SPOT_USERDATA, apiKey, apiSecret, subscription);
            spotUserDataListener.connect();
            LOGGER.info("Kraken Spot UserData WS connected");
        } catch (final Exception e) {
            LOGGER.error("Failed to connect Kraken Spot UserData WS: " + e.getMessage(), e);
            throw e;
        }
    }

    private void startUserSpotTradeStream() throws Exception {
        try {
            LOGGER.info("Connecting Kraken Spot Trade WS...");
            spotTradeListener.connect();
            LOGGER.info("Kraken Spot Trade WS connected");
        } catch (final Exception e) {
            LOGGER.error("Failed to connect Kraken Spot Trade WS: " + e.getMessage(), e);
            throw e;
        }
    }

    private void startUserFutureDataStream() throws Exception {
        try {
            LOGGER.info("Connecting Kraken Futures UserData WS...");
            futureUserDataListener = new KrakenFutureUserDataListener(WS_FUTURES_USERDATA, apiKey, apiSecret, subscription);
            futureUserDataListener.connect();
            LOGGER.info("Kraken Futures UserData WS connected");
        } catch (final Exception e) {
            LOGGER.error("Failed to connect Kraken Futures UserData WS: " + e.getMessage(), e);
            throw e;
        }
    }



    private void disconnectWebSockets() {
        if (spotUserDataListener != null && spotUserDataListener.isConnected()) {
            spotUserDataListener.disconnect();
        }
        if (spotTradeListener != null && spotTradeListener.isConnected()) {
            spotTradeListener.disconnect();
        }
        if (futureUserDataListener != null && futureUserDataListener.isConnected()) {
            futureUserDataListener.disconnect();
        }

    }
    /**
     * Safely converts a long value to an integer with overflow handling
     * If the long value exceeds Integer.MAX_VALUE, uses modulo operation to wrap around
     * This ensures the value stays within valid integer range [Integer.MIN_VALUE, Integer.MAX_VALUE]
     *
     * @param value The long value to convert
     * @return The converted integer value, wrapped using modulo if overflow occurs
     */
    private int safeConvertLongToInt(final long value) {
        if (value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE) {
            // Value fits within integer range, return as-is
            return (int) value;
        } else {
            // Value overflows, use modulo to wrap around
            // This maps the value to the range [Integer.MIN_VALUE, Integer.MAX_VALUE]
            final long range = (long) Integer.MAX_VALUE - Integer.MIN_VALUE + 1;
            final long wrapped = ((value - Integer.MIN_VALUE) % range + range) % range + Integer.MIN_VALUE;
            LOGGER.debug("Long value " + value + " overflowed, wrapped to integer: " + wrapped);
            return (int) wrapped;
        }
    }

    // ======= Order Methods =======
    private void sendSpotOrder(final Order order) throws Exception {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "Limit" : "Market";
        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? "FOK" : "GTC";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        //converting ClOrdId to 32 bit integer to support API requirement
        order.setClOrdId(String.valueOf(safeConvertLongToInt((Long.parseLong(order.getClOrdId())))));

        subscription.cacheNewOrder(order);

        if (spotTradeListener.isConnected()) {
            LOGGER.info("SPOT: Sending order via WebSocket for symbol: " + order.getSymbol());
            spotTradeListener.placeOrder(order, order.getSymbol(), order.getSide().toString(), type, qtyStr, priceStr, order.getClOrdId());
        } else {
            LOGGER.info("SPOT: Sending order via REST for symbol: " + order.getSymbol());
            sendSpotOrderREST(order, order.getSymbol(), order.getSide().toString(), type, timeInForce, qtyStr, priceStr, order.getClOrdId());
        }

        // Poll order cache for status updates (WebSocket response) — skip in REST-only mode
        if (!restOnly) {
            LOGGER.debug("SPOT: Polling order cache for status update...");
            for (int i = 0; i < 2000; i++) {
                final Order existingOrder = subscription.getOrder(order.getClOrdId());
                if (existingOrder != null && (existingOrder.isRejected() || existingOrder.isExecuted())) {
                    return;
                }
                try {
                    Thread.sleep(1);
                } catch (final InterruptedException e) {
                    LOGGER.error("Thread interrupted during order status check: " + e.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        LOGGER.debug("SPOT: Polling REST API for status update...");

        // Query via REST API
        for (int i = 0; i < 100; i++) {
            krakenRestClient.querySpotOrderStatus(order, order.getClOrdId(), i);
            subscription.updateOrder(order.getClOrdId(), order);

            final Order existingOrder = subscription.getOrder(order.getClOrdId());
            if (existingOrder != null && (existingOrder.isRejected() || existingOrder.isExecuted())) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (final InterruptedException e) {
                LOGGER.error("Thread interrupted during REST API status check: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }

        LOGGER.warn("SPOT: Order status could not be determined for order: " + order.getClOrdId());
    }

    private void sendFutureOrder(final Order order, final String bestQuoteSymbol, final String baseSymbol, final int priceScale,
                                 final int qtyScale, final double fxRate) throws Exception {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "Limit" : "Market";
        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? "FOK" : "GTC";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order);

        LOGGER.info("FUTURE: Sending order via REST for symbol: " + order.getSymbol());
        sendFutureOrderREST(order, order.getSymbol(), order.getSide().toString(), type, timeInForce, qtyStr, priceStr, false);

        // Poll order cache for status updates (WebSocket response) — skip in REST-only mode
        if (!restOnly) {
            LOGGER.debug("FUTURE: Polling order cache for status update...");
            for (int i = 0; i < 2000; i++) {
                final Order existingOrder = subscription.getOrder(order.getClOrdId());
                if (existingOrder != null && (existingOrder.isRejected() || existingOrder.isExecuted())) {
                    return;
                }
                try {
                    Thread.sleep(1);
                } catch (final InterruptedException e) {
                    LOGGER.error("Thread interrupted during order status check: " + e.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        LOGGER.debug("FUTURE: Polling REST API for status update...");

        // Query via REST API
        for (int i = 0; i < 100; i++) {
            krakenRestClient.queryFuturesOrderStatus(order, order.getClOrdId(), i);
            subscription.updateOrder(order.getClOrdId(), order);

            final Order existingOrder = subscription.getOrder(order.getClOrdId());
            if (existingOrder != null && (existingOrder.isRejected() || existingOrder.isExecuted())) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (final InterruptedException e) {
                LOGGER.error("Thread interrupted during REST API status check: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }

        LOGGER.warn("FUTURE: Order status could not be determined for order: " + order.getClOrdId());
    }

    private void sendSpotOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce,
                                   final String quantityStr, final String priceStr, final String clientOrderId) throws Exception {
        final boolean result = krakenRestClient.sendSpotOrderREST(order, symbol, side, type, timeInForce, quantityStr, priceStr, clientOrderId);
        LOGGER.info("Result of sendFutureOrderREST: " + result);
    }

    private void sendFutureOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce,
                                     final String quantityStr, final String priceStr, final boolean reduceOnly) throws Exception {
        final boolean result = krakenRestClient.sendFutureOrderREST(order, symbol, side, type, timeInForce, quantityStr, priceStr, reduceOnly);
        LOGGER.info("Result of sendFutureOrderREST: " + result);
    }

    // ======= Account Snapshot Application Methods =======
    private void applySpotAccountSnapshot(final String json) {
        try {
            // Extract the result object from Kraken response
            final String resultObj = JsonHelper.extractJsonValue(json, "result");
            if (resultObj == null || resultObj.isEmpty()) {
                LOGGER.warn("No 'result' object found in Kraken spot balance snapshot");
                return;
            }

            // Parse each asset-balance pair from the result object
            String remaining = resultObj.trim();
            if (remaining.startsWith("{")) {
                remaining = remaining.substring(1);
            }
            if (remaining.endsWith("}")) {
                remaining = remaining.substring(0, remaining.length() - 1);
            }

            final String[] pairs = remaining.split(",");
            for (final String pair : pairs) {
                final int colonIdx = pair.indexOf(':');
                if (colonIdx < 0) continue;

                final String assetPart = pair.substring(0, colonIdx).trim();
                final String valuePart = pair.substring(colonIdx + 1).trim();

                final String asset = assetPart.replaceAll("\"", "");
                final String balanceValue = valuePart.replaceAll("\"", "");
                final double balance = parseDoubleSafe(balanceValue);

                LOGGER.debug("SPOT BOOTSTRAP: Asset: " + asset + " Balance: " + balance);
                subscription.updateBalance(asset, balance);
            }

            LOGGER.info("Spot account snapshot applied successfully");
        } catch (Exception e) {
            LOGGER.error("Error applying spot account snapshot: " + e.getMessage());
        }
    }

    private void applyFutureAccountSnapshot(final String json) {
        try {
            if (json == null || json.isEmpty()) {
                LOGGER.warn("Futures account snapshot JSON is null or empty");
                return;
            }

            LOGGER.debug("Parsing Kraken Futures account snapshot...");

            // Extract the accounts object using JsonHelper
            final String accountsObj = JsonHelper.extractJsonValue(json, "accounts");
            if (accountsObj == null || accountsObj.isEmpty()) {
                LOGGER.warn("No 'accounts' object found in Kraken futures account snapshot");
                return;
            }

            // Extract the flex account object
            final String flexObj = JsonHelper.extractJsonValue(accountsObj, "flex");
            if (flexObj == null || flexObj.isEmpty()) {
                LOGGER.warn("No 'flex' account found in accounts object");
                return;
            }

            // Extract the currencies object from flex
            final String currenciesObj = JsonHelper.extractJsonValue(flexObj, "currencies");
            if (currenciesObj == null || currenciesObj.isEmpty()) {
                LOGGER.warn("No 'currencies' object found in flex account");
                return;
            }

            LOGGER.debug("Extracted currencies object, length: " + currenciesObj.length());

            // Parse each currency object within the currencies object
            // Format: {"USDT": {...}, "USD": {...}, ...}
            int currencyObjStart = 0;
            while (currencyObjStart < currenciesObj.length()) {
                currencyObjStart = currenciesObj.indexOf('"', currencyObjStart);
                if (currencyObjStart < 0) break;

                // Extract currency name (e.g., "USDT")
                int currencyNameEnd = currenciesObj.indexOf('"', currencyObjStart + 1);
                if (currencyNameEnd < 0) break;

                final String currencyName = currenciesObj.substring(currencyObjStart + 1, currencyNameEnd);

                // Find the opening brace for this currency's data
                final int currencyDataStart = currenciesObj.indexOf('{', currencyNameEnd);
                if (currencyDataStart < 0) break;

                // Extract the complete currency data object using JsonHelper logic
                final String currencyData = JsonHelper.extractJsonObject(currenciesObj, currencyDataStart);
                if (currencyData == null) break;

                LOGGER.debug("Processing currency: " + currencyName + " with data length: " + currencyData.length());

                // Extract balance using quantity field (with fallback to available)
                final String quantityStr = minExtract(currencyData, "quantity");
                final double balance = parseDoubleSafe(quantityStr);

                if (balance > 0) {
                    subscription.updateBalance(currencyName, balance);
                    LOGGER.debug("FUTURES BOOTSTRAP: Currency: " + currencyName + " Balance: " + balance);
                }

                currencyObjStart = currenciesObj.indexOf('}', currencyDataStart) + 1;
            }

            LOGGER.info("Futures account snapshot applied successfully");
        } catch (Exception e) {
            LOGGER.error("Error applying futures account snapshot: " + e.getMessage(), e);
        }
    }
}
