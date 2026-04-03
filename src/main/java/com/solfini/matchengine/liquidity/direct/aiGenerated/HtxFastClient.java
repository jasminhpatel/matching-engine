package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.htx.HtxFutureTradeListener;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.htx.HtxRestClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.htx.HtxSpotTradeListener;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.htx.HtxSpotUserDataListener;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.util.StringUtil;

import java.util.List;
import java.util.Objects;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;

public final class HtxFastClient implements ExternalExchangeClient {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(HtxFastClient.class);
    private static final long TEN_MINUTES = 600_000;
    private static final long ONE_MINUTE = 60_000;
    private static final int SNAPSHOT_RETRY_DELAY_MS = 2_000;
    private static final int SNAPSHOT_MAX_ATTEMPTS = 2;

    private final ExchangeSubscription subscription;
    private final HtxRestClient htxRestClient;
    private final HtxSpotTradeListener spotTradeListener;
    private final HtxFutureTradeListener futureTradeListener;
    private final boolean restOnly;
    private volatile HtxSpotUserDataListener spotUserDataListener;
    private volatile Thread spotAccountRefreshThread;
    private volatile Thread futureAccountRefreshThread;
    private volatile boolean isSpotAccountRefreshRunning = false;
    private volatile boolean isFutureAccountRefreshRunning = false;

    public HtxFastClient(final ExchangeSubscription subscription) {
        this(subscription, false);
    }

    public HtxFastClient(final ExchangeSubscription subscription, final boolean restOnly) {
        this.restOnly = restOnly;
        this.subscription = Objects.requireNonNull(subscription);
        final String apiKey = Objects.requireNonNull(subscription.getApiKey(), "HTX API key required");
        final String apiSecret = Objects.requireNonNull(subscription.getApiSecret(), "HTX API secret required");
        this.htxRestClient = new HtxRestClient(apiKey, apiSecret, subscription);
        if (restOnly) {
            this.spotTradeListener = null;
            this.futureTradeListener = null;
        } else if (subscription.isFuturesEnabled()) {
            this.futureTradeListener = new HtxFutureTradeListener(htxRestClient, subscription);
            this.spotTradeListener = null;
        } else {
            this.spotTradeListener = new HtxSpotTradeListener(htxRestClient, subscription);
            this.futureTradeListener = null;
        }
    }

    private static String deriveLinearSwapContractCode(final String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return "BTC-USDT";
        }
        final String s = symbol.toUpperCase(java.util.Locale.ROOT);
        if (s.endsWith("USDT")) {
            final String base = s.substring(0, s.length() - 4);
            if (!base.isEmpty()) {
                return base + "-USDT";
            }
        }
        return s + "-USDT";
    }

    private static boolean isTransientConnectionError(final Throwable t) {
        if (t instanceof java.net.SocketException) return true;
        if (t.getMessage() != null && t.getMessage().contains("Unexpected end of file")) return true;
        Throwable c = t.getCause();
        return c != null && c != t && isTransientConnectionError(c);
    }

    @Override
    public void start() {
        if (subscription.isFuturesEnabled()) {
            startFutureClient();
        } else {
            startSpotClient();
        }
    }

    @Override
    public void stop() {
        stopPeriodicSpotAccountRefresh();
        if (subscription.isFuturesEnabled()) {
            stopPeriodicFutureAccountRefresh();
        }
    }

    @Override
    public ExecutionReportMessage sendOrder(final Order order, final boolean futuresEnabled, final String bestQuoteSymbol,
                                            final String baseSymbol, final int priceScale, final int qtyScale, final double fxRate)
            throws Exception {
        if (futuresEnabled) {
            sendFutureOrder(order, bestQuoteSymbol, baseSymbol, priceScale, qtyScale, fxRate);
            return subscription.getExecutionReport(order.getClOrdId());
        } else {
            sendSpotOrder(order);
            return subscription.getExecutionReport(order.getClOrdId());
        }
    }

    private Order sendFutureOrder(final Order order, final String bestQuoteSymbol, final String baseSymbol, final int priceScale,
                                  final int qtyScale, final double fxRate) throws Exception {
        subscription.cacheNewOrder(order);

        String contractCode = (baseSymbol != null && !baseSymbol.isEmpty() ? baseSymbol : "") + "-" + (bestQuoteSymbol != null && !bestQuoteSymbol.isEmpty() ? bestQuoteSymbol : "USDT");
        if (contractCode.startsWith("-") || contractCode.equals("-USDT")) {
            contractCode = deriveLinearSwapContractCode(order.getSymbol());
        }
        final String side = order.getSide() != null ? order.getSide().toString().toLowerCase(java.util.Locale.ROOT) : "buy";
        final boolean isLimit = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT;
        final String orderPriceType = isLimit ? "limit" : "opponent";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final double baseQty = order.getQty() / Math.pow(10, order.getQtyScale());
        final double contractSize = (contractCode == null) ? 0.01
                : (contractCode.toUpperCase(java.util.Locale.ROOT).startsWith("BTC-") ? 0.001 : 0.01);
        final long volumeContracts = Math.max(1, Math.round(baseQty / contractSize));
        final String qtyStr = String.valueOf(volumeContracts);
        final double lev = subscription.getLeverage();
        final int leverRate = (lev >= 1 && lev <= 125) ? (int) Math.round(lev) : 10;

        if (futureTradeListener != null && futureTradeListener.isConnected()) {
            futureTradeListener.placeOrder(contractCode, side, "open", orderPriceType, qtyStr, priceStr, order.getClOrdId(), leverRate);
        } else {
            LOGGER.debug("Sending FUTURE order via REST for symbol: " + order.getSymbol());
            sendFutureOrderRESTWithParams(order, contractCode, side, orderPriceType, qtyStr, priceStr, leverRate);
        }

        // Poll order cache for status updates (WebSocket response) — skip in REST-only mode
        if (!restOnly) {
            LOGGER.debug("FUTURE: Start of Periodic check on Order Cache for status update.");
            for (int i = 0; i < 2000; i++) {
                final Order existingOrder = subscription.getOrder(order.getClOrdId());
                if (existingOrder.isRejected() || existingOrder.isExecuted())
                    return existingOrder;
                try {
                    Thread.sleep(1);
                } catch (final InterruptedException e) {
                    LOGGER.error("Thread interrupted during order status check: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }
        }

        LOGGER.debug("FUTURE: Start of Periodic REST API check for status update.");
        for (int i = 0; i < 100; i++) {
            final Order existingOrder = subscription.getOrder(order.getClOrdId());
            if (existingOrder.isRejected() || existingOrder.isExecuted())
                return existingOrder;
            try {
                Thread.sleep(50);
            } catch (final InterruptedException e) {
                LOGGER.error("Thread interrupted during REST API status check: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }

        LOGGER.warn("FUTURE: we couldn't process the order for order: " + order.getClOrdId());
        return order;
    }

    private Order sendSpotOrder(final Order order) throws Exception {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "Limit" : "Market";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order);

        if (spotTradeListener != null && spotTradeListener.isConnected()) {
            LOGGER.debug("Sending SPOT order via websocket for symbol: " + order.getSymbol());
            spotTradeListener.placeOrder(order, order.getSymbol(), order.getSide().toString(), type, qtyStr, priceStr, order.getClOrdId());
        } else {
            LOGGER.debug("Sending SPOT order via REST for symbol: " + order.getSymbol());
            sendSpotOrderREST(order, order.getSymbol(), order.getSide().toString(), type, qtyStr, priceStr, order.getClOrdId());
        }

        // Poll order cache for status updates (WebSocket response) — skip in REST-only mode
        if (!restOnly) {
            LOGGER.debug("SPOT: Start of Periodic check on Order Cache for status update.");
            for (int i = 0; i < 2000; i++) {
                final Order existingOrder = subscription.getOrder(order.getClOrdId());
                if (existingOrder.isRejected() || existingOrder.isExecuted())
                    return existingOrder;
                try {
                    Thread.sleep(1);
                } catch (final InterruptedException e) {
                    LOGGER.error("Thread interrupted during order status check: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }
        }

        LOGGER.debug("SPOT: Start of Periodic REST API check for status update.");
        for (int i = 0; i < 100; i++) {
            htxRestClient.querySpotOrderStatus(order, order.getClOrdId());
            subscription.updateOrder(order.getClOrdId(), order);

            final Order existingOrder = subscription.getOrder(order.getClOrdId());
            if (existingOrder.isRejected() || existingOrder.isExecuted())
                return existingOrder;
            try {
                Thread.sleep(50);
            } catch (final InterruptedException e) {
                LOGGER.error("Thread interrupted during REST API status check: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }

        LOGGER.warn("SPOT: we couldn't process the order for order: " + order.getClOrdId());
        return order;
    }

    private void sendFutureOrderRESTWithParams(final Order order, final String contractCode, final String side,
                                               final String orderPriceType, final String qtyStr, final String priceStr,
                                               final int leverRate) throws Exception {
        LOGGER.info("HTX futures: sending order contract=" + contractCode + " side=" + side + " volume=" + qtyStr + " contracts lever_rate=" + leverRate);
        final boolean result = htxRestClient.sendLinearSwapOrderREST(order, contractCode, side, orderPriceType, qtyStr, priceStr, order.getClOrdId(), leverRate,
                null, null, null, null, null, null);
        LOGGER.info("Result of sendFutureOrderREST: " + result);
    }

    private Order sendSpotOrderREST(final Order order, final String symbol, final String side, final String type,
                                    final String quantityStr, final String priceStr, final String clientOrderId) throws Exception {
        final boolean result = htxRestClient.sendSpotOrderREST(order, symbol, side, type, quantityStr, priceStr, clientOrderId);
        LOGGER.info("Result of sendSpotOrderREST: " + result);
        return order;
    }

    @Override
    public boolean cancelOrder(final Order order, final boolean isSpotOrder) {
        if (isSpotOrder) {
            return htxRestClient.cancelSpotOrderByClientOrderId(order, order.getClOrdId());
        }
        final String orderId = order.getOcoClOrdId();
        if (orderId == null || orderId.isEmpty()) {
            LOGGER.warn("HTX cancel futures: no exchange order id");
            return false;
        }
        final String contractCode = deriveLinearSwapContractCode(order.getSymbol());
        return htxRestClient.cancelLinearSwapOrder(orderId, contractCode != null ? contractCode.toLowerCase(java.util.Locale.ROOT) : "btc-usdt");
    }

    public String getAllOpenOrders() throws Exception {
        if (subscription.isFuturesEnabled()) {
            return htxRestClient.getLinearSwapOpenOrders(null);
        }
        return htxRestClient.getOpenOrders();
    }

    public String getBalance() throws Exception {
        if (subscription.isFuturesEnabled()) {
            return htxRestClient.getFutureAccountSnapshot();
        } else {
            return htxRestClient.getSpotBalanceSnapshot();
        }
    }

    public boolean transferBalance(final String asset, final String amount, final boolean fromSpotToFutures) throws Exception {
        if (fromSpotToFutures) {
            LOGGER.info("Transferring balance from Spot to Futures: asset=" + asset + " amount=" + amount);
            return htxRestClient.transferSpotToFutures(asset, amount);
        } else {
            LOGGER.info("Transferring balance from Futures to Spot: asset=" + asset + " amount=" + amount);
            return htxRestClient.transferFuturesToSpot(asset, amount);
        }
    }

    private void startSpotClient() {
        try {
            bootstrapSpotBalanceSnapshot();
            startPeriodicSpotAccountRefresh();
            startUserSpotBalanceStream();
            startUserSpotTradeStream();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void startUserSpotBalanceStream() {
        try {
            spotUserDataListener = new HtxSpotUserDataListener(htxRestClient, subscription);
            spotUserDataListener.connect();
            LOGGER.info("HTX SpotUserDataListener (subscribe + all response handling) started");
        } catch (final Exception e) {
            LOGGER.error("HTX startUserSpotBalanceStream error", e);
            throw new RuntimeException(e);
        }
    }

    private void startUserSpotTradeStream() {
        try {
            spotTradeListener.connect();
            LOGGER.info("HTX SpotTradeListener (place order) started");
        } catch (final Exception e) {
            LOGGER.error("HTX startUserSpotTradeStream error", e);
            throw new RuntimeException(e);
        }
    }

    private void startFutureClient() {
        try {
            LOGGER.info("Starting HTX Futures Client (linear-swap)...");

            bootstrapFutureBalanceSnapshot();
            startFutureTradeStream();
            if (futureTradeListener != null) {
                final long deadline = System.currentTimeMillis() + 5_000L;
                while (System.currentTimeMillis() < deadline) {
                    if (futureTradeListener.isConnected()) break;
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            startPeriodicFutureAccountRefresh();

            LOGGER.info("HTX Futures Client started");
        } catch (final Exception e) {
            LOGGER.error("Failed to start HTX Futures Client", e);
            throw new RuntimeException(e);
        }
    }

    private void startFutureTradeStream() {
        try {
            futureTradeListener.connect();
            if (futureTradeListener.isConnected()) {
                LOGGER.info("HTX Future Trade WS connected");
            } else {
                LOGGER.warn("HTX Future Trade WS not connected (auth pending or failed); orders will use REST");
            }
        } catch (final Exception e) {
            LOGGER.error("HTX startFutureTradeStream error", e);
            throw new RuntimeException(e);
        }
    }

    private void bootstrapFutureBalanceSnapshot() {
        try {
            String body = null;
            for (int attempt = 1; attempt <= SNAPSHOT_MAX_ATTEMPTS; attempt++) {
                body = htxRestClient.getFutureAccountSnapshot();
                if (body != null) break;
                if (attempt < SNAPSHOT_MAX_ATTEMPTS) {
                    LOGGER.warn("HTX future account snapshot attempt {} returned null (will retry)", attempt);
                    Thread.sleep(SNAPSHOT_RETRY_DELAY_MS);
                }
            }
            if (body != null) {
                applyFutureAccountSnapshot(body);
            }
            for (int attempt = 1; attempt <= SNAPSHOT_MAX_ATTEMPTS; attempt++) {
                try {
                    final String positionJson = htxRestClient.getLinearSwapPositionInfo("");
                    applyFuturePositionSnapshot(positionJson);
                    break;
                } catch (final Exception e) {
                    if (isTransientConnectionError(e) && attempt < SNAPSHOT_MAX_ATTEMPTS) {
                        LOGGER.warn("HTX future position snapshot attempt {} failed (will retry): {}", attempt, e.getMessage());
                        Thread.sleep(SNAPSHOT_RETRY_DELAY_MS);
                    } else {
                        LOGGER.warn("HTX future position snapshot failed: {}", e.getMessage());
                        break;
                    }
                }
            }
            LOGGER.info("Future balance and position snapshot applied");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("HTX future snapshot interrupted");
        } catch (final Exception e) {
            LOGGER.warn("HTX future snapshot failed: {}", e.getMessage());
        }
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
                    LOGGER.info("Running periodic future account snapshot refresh...");
                    bootstrapFutureBalanceSnapshot();
                    LOGGER.info("Periodic future account snapshot refresh completed");
                    Thread.sleep(TEN_MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Periodic future account snapshot failed: " + e.getMessage());
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

    private void bootstrapSpotBalanceSnapshot() throws Exception {

        final String body = htxRestClient.getSpotBalanceSnapshot();
        applySpotAccountSnapshot(body);
        LOGGER.info("Bootstrap snapshot applied");
    }

    private void startPeriodicSpotAccountRefresh() {
        if (isSpotAccountRefreshRunning) {
            LOGGER.warn("Periodic spot account refresh already running");
            return;
        }

        isSpotAccountRefreshRunning = true;
        spotAccountRefreshThread = Thread.ofVirtual().start(() -> {
            while (isSpotAccountRefreshRunning) {
                try {
                    LOGGER.info("Running periodic spot account snapshot refresh...");
                    bootstrapSpotBalanceSnapshot();
                    LOGGER.info("Periodic spot account snapshot refresh completed");
                    Thread.sleep(TEN_MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Periodic spot account snapshot failed: " + e.getMessage());
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

    private void applySpotAccountSnapshot(final String json) {
        final int bStart = json.indexOf("\"list\":[");
        if (bStart >= 0) {
            final int bEnd = json.indexOf(']', bStart);
            final String bArr = json.substring(bStart + 8, bEnd);
            int p = 0;
            while (p >= 0 && p < bArr.length()) {
                final int objStart = bArr.indexOf('{', p);
                if (objStart < 0)
                    break;
                final int objEnd = bArr.indexOf('}', objStart);
                final String obj = bArr.substring(objStart, objEnd + 1);
                final String asset = minExtract(obj, "currency");
                final double balance = parseDoubleSafe(minExtract(obj, "balance"));

                if (asset != null) {
                    subscription.updateBalance(asset.toUpperCase(java.util.Locale.ROOT), balance);
                }
                p = objEnd + 1;
            }
        }
    }

    private void applyFutureAccountSnapshot(final String json) {
        if (json == null) {
            return;
        }
        // Array format: "data":[{ "symbol" or "margin_account", "margin_balance" }] (Binance / HTX linear-swap)
        final int aStart = json.indexOf("\"data\":[");
        if (aStart >= 0) {
            final int aEnd = json.indexOf(']', aStart);
            if (aEnd < 0) return;
            final String aArr = json.substring(aStart + 8, aEnd);
            int p = 0;
            while (p >= 0 && p < aArr.length()) {
                final int objStart = aArr.indexOf('{', p);
                if (objStart < 0) break;
                final int objEnd = aArr.indexOf('}', objStart);
                if (objEnd < 0) break;
                final String obj = aArr.substring(objStart, objEnd + 1);
                String asset = minExtract(obj, "symbol");
                if (asset == null) {
                    asset = minExtract(obj, "margin_account");
                }
                final double walletBalance = parseDoubleSafe(minExtract(obj, "margin_balance"));
                if (asset != null) {
                    subscription.updateBalance(asset.toUpperCase(), walletBalance);
                }
                p = objEnd + 1;
            }
            return;
        }
        // Single object: "data":{ "margin_balance", ... } (unified/cross-margin)
        final int objStart = json.indexOf("\"data\":{");
        if (objStart >= 0) {
            final int objEnd = json.indexOf('}', objStart + 8);
            final String obj = json.substring(objStart + 8, objEnd + 1);
            final double walletBalance = parseDoubleSafe(minExtract(obj, "margin_balance"));
            subscription.updateBalance("USDT", walletBalance);
        }
    }

    private void applyFuturePositionSnapshot(final String json) {
        if (json == null) {
            return;
        }
        final int pStart = json.indexOf("\"data\":[");
        if (pStart >= 0) {
            final int pEnd = json.indexOf(']', pStart);
            if (pEnd < 0) return;
            final String pArr = json.substring(pStart + 8, pEnd);
            int q = 0;
            while (q >= 0 && q < pArr.length()) {
                final int objStart = pArr.indexOf('{', q);
                if (objStart < 0) break;
                final int objEnd = pArr.indexOf('}', objStart);
                if (objEnd < 0) break; // avoid infinite loop on malformed or unexpected format
                final String obj = pArr.substring(objStart, objEnd + 1);
                final String symbol = minExtract(obj, "contract_code");
                final double volume = parseDoubleSafe(minExtract(obj, "volume"));
                final String direction = minExtract(obj, "direction");
                final double positionAmt = "buy".equalsIgnoreCase(direction) ? volume : -volume;
                if (symbol != null)
                    subscription.updatePosition(symbol, positionAmt);
                q = objEnd + 1;
            }
        }
    }

    @Override
    public List<ExternalSymbol> getExchangeInstrumentsFull() {
        return htxRestClient.getExchangeInstrumentsFull();
    }

    @Override
    public Ticker getTicker(final String base, final String quote, final int instrumentType) {
        if (instrumentType == 1) {
            final String contractCode = (base != null ? base : "BTC") + "-" + (quote != null ? quote : "USDT");
            final Ticker ticker = htxRestClient.getFutureTicker(contractCode);
            if (ticker != null) {
                ticker.setInstrumentType(instrumentType);
            }
            return ticker;
        } else {
            final String symbol = (base != null ? base : "BTC") + (quote != null ? quote : "USDT");
            final Ticker ticker = htxRestClient.getSpotTicker(symbol);
            if (ticker != null) {
                ticker.setInstrumentType(instrumentType);
            }
            return ticker;
        }
    }
}
