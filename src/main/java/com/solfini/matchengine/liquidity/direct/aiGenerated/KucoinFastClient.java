package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.liquidity.DelistedSymbolCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kucoin.KucoinFutureTradeListener;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kucoin.KucoinFutureUserDataListener;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kucoin.KucoinRestClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kucoin.KucoinSpotTradeListener;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kucoin.KucoinSpotUserDataListener;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.StringUtil;

import com.solfini.matchengine.executionexchange.ExternalSymbol;

import java.util.List;
import java.util.Objects;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.extractJsonValue;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;

public final class KucoinFastClient implements ExternalExchangeClient {

    // ======= Configuration (KuCoin Spot & Futures) =======
    private static final CustomLogger LOGGER = CustomLogger.getLogger(KucoinFastClient.class);
    private static final long TEN_MINUTES = 600_000;
    private static final long ONE_MINUTE = 60_000;
    private final ExchangeSubscription subscription;

    // ======= API credentials =======
    private final String apiKey;
    private final String apiSecret;

    // ======= REST Client =======
    private final KucoinRestClient kuCoinRestClient;

    // ======= WebSocket: getBalance + spot trade (same as Binance SpotUserDataListener / SpotTradeListener) =======
    private volatile KucoinSpotUserDataListener spotUserDataListener;
    private volatile KucoinFutureUserDataListener futureUserDataListener;
    private volatile KucoinSpotTradeListener spotTradeListener;
    private volatile KucoinFutureTradeListener futureTradeListener;

    // ======= Scheduled Tasks =======
    private volatile Thread spotAccountRefreshThread;
    private volatile Thread futureAccountRefreshThread;
    private volatile boolean isSpotAccountRefreshRunning = false;
    private volatile boolean isFutureAccountRefreshRunning = false;

    private final boolean restOnly;

    // ======= Constructor =======
    public KucoinFastClient(final ExchangeSubscription subscription) {
        this(subscription, false);
    }

    public KucoinFastClient(final ExchangeSubscription subscription, final boolean restOnly) {
        this.restOnly = restOnly;
        this.subscription = subscription;
        this.apiKey = Objects.requireNonNull(subscription.getApiKey(), "KUCOIN_API_KEY required");
        this.apiSecret = Objects.requireNonNull(subscription.getApiSecret(), "KUCOIN_API_SECRET required");

        this.kuCoinRestClient = new KucoinRestClient(apiKey, apiSecret, subscription,
                Context.getKuCoinSpotRest(), Context.getKuCoinFuturesRest());
        this.spotTradeListener = (!restOnly && !subscription.isFuturesEnabled())
                ? new KucoinSpotTradeListener(subscription, kuCoinRestClient) : null;
        this.futureTradeListener = (!restOnly && subscription.isFuturesEnabled())
                ? new KucoinFutureTradeListener(subscription, kuCoinRestClient) : null;
    }

    @Override
    public void start() {
        // first, so a failing account login below does not stop it; delisted symbols now, then every 10 minutes
        DelistedSymbolCache.start(subscription);
        if (subscription.isFuturesEnabled()) {
            startFutureClient();
        } else {
            startSpotClient();
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping KuCoinFastClient...");
        stopSpotUserDataStream();
        stopSpotTradeStream();
        stopFutureUserDataStream();
        stopFutureTradeStream();
        stopPeriodicSpotAccountRefresh();
        stopPeriodicFutureAccountRefresh();
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

    private void sendSpotOrder(final Order order) throws Exception {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "limit" : "market";
        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? "FOK" : "GTC";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order);
        if (spotTradeListener != null && spotTradeListener.isConnected()) {
            LOGGER.debug("Sending SPOT order via WebSocket for symbol: " + order.getSymbol());
            spotTradeListener.placeOrder(order.getSymbol(), order.getSide().toString(), type, timeInForce, qtyStr, priceStr, order.getClOrdId());
        } else {
            LOGGER.debug("Sending SPOT order via REST for symbol: " + order.getSymbol());
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
        for (int i = 0; i < 100; i++) {
            kuCoinRestClient.querySpotOrderStatus(order, order.getClOrdId(), order.getSymbol(), i);
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

    private void sendSpotOrderREST(final Order order, final String symbol, final String side, final String type,
                                   final String timeInForce, final String quantityStr, final String priceStr,
                                   final String clientOrderId) throws Exception {
        final boolean result = kuCoinRestClient.sendSpotOrderREST(order, symbol, side, type, timeInForce, quantityStr, priceStr, clientOrderId);
        LOGGER.info("Result of sendSpotOrderREST: " + result);
    }

    private void sendFutureOrder(final Order order, final String bestQuoteSymbol, final String baseSymbol, final int priceScale,
                                 final int qtyScale, final double fxRate) throws Exception {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "limit" : "market";
        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? "FOK" : "GTC";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order);

        if (futureTradeListener != null && futureTradeListener.isConnected()) {
            LOGGER.debug("Sending FUTURE order via WebSocket for symbol: " + order.getSymbol());
            futureTradeListener.placeOrder(order.getSymbol(), order.getSide().toString(), type, timeInForce, qtyStr, priceStr, order.getClOrdId());
        } else {
            LOGGER.info("FUTURE: Sending order via REST for symbol: " + order.getSymbol());
            sendFutureOrderREST(order, order.getSymbol(), order.getSide().toString(), type, timeInForce, qtyStr, priceStr, false);
        }

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
        for (int i = 0; i < 100; i++) {
            kuCoinRestClient.queryFuturesOrderStatus(order, order.getClOrdId(), order.getSymbol(), i);
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

    private void sendFutureOrderREST(final Order order, final String symbol, final String side, final String type,
                                     final String timeInForce, final String quantityStr, final String priceStr,
                                     final boolean reduceOnly) throws Exception {
        final boolean result = kuCoinRestClient.sendFutureOrderREST(order, symbol, side, type, timeInForce, quantityStr, priceStr, order.getClOrdId(), reduceOnly);
        LOGGER.info("Result of sendFutureOrderREST: " + result);
    }

    @Override
    public boolean cancelOrder(final Order order, final boolean isSpotOrder) {
        if (isSpotOrder) {
            return kuCoinRestClient.cancelSpotOrderRest(order.getClOrdId(), order.getSymbol());
        } else {
            return kuCoinRestClient.cancelFuturesOrderRest(order.getClOrdId(), order.getSymbol());
        }
    }

    /** Get all open orders across all symbols (spot or futures depending on config). */
    public String getAllOpenOrders() {
        if (subscription.isFuturesEnabled()) {
            return kuCoinRestClient.getAllOpenFuturesOrders();
        }
        return kuCoinRestClient.getAllOpenSpotOrders();
    }

    @Override
    public List<DelistedSymbol> getDelistedSymbols() {
        return subscription.isFuturesEnabled()
                ? kuCoinRestClient.getFuturesDelistedSymbols()
                : kuCoinRestClient.getSpotDelistedSymbols();
    }

    @Override
    public List<ExternalSymbol> getExchangeInstrumentsFull() {
        return kuCoinRestClient.getExchangeInstrumentsFull();
    }

    @Override
    public Ticker getTicker(final String base, final String quote, final int instrumentType) {
        // instrumentType 1 = Perps, 2 = Spot
        if (instrumentType == 1) {
            final String kucoinBase = "BTC".equals(base) ? "XBT" : base;
            final String symbol = kucoinBase + quote + "M";
            final Ticker ticker = kuCoinRestClient.getFutureTicker(symbol);
            if (ticker != null) {
                ticker.setInstrumentType(instrumentType);
            }
            return ticker;
        } else {
            final String symbol = base + "-" + quote;
            final Ticker ticker = kuCoinRestClient.getSpotTicker(symbol);
            if (ticker != null) {
                ticker.setInstrumentType(instrumentType);
            }
            return ticker;
        }
    }

    // ======= Balance Transfer Methods =======
    public boolean transferBalance(final String asset, final String amount, final boolean fromSpotToFutures) {
        if (fromSpotToFutures) {
            LOGGER.info("Transferring balance from Spot to Futures: asset=" + asset + " amount=" + amount);
            return kuCoinRestClient.transferSpotToFutures(asset, amount);
        } else {
            LOGGER.info("Transferring balance from Futures to Spot: asset=" + asset + " amount=" + amount);
            return kuCoinRestClient.transferFuturesToSpot(asset, amount);
        }
    }

    public boolean cancelAllSpotOrders() {
        return kuCoinRestClient.cancelAllSpotOrdersRest();
    }

    private void startSpotClient() {
        try {
            if (restOnly) {
                LOGGER.info("KucoinFastClient started in REST-only mode — WebSocket connections skipped");
                bootstrapSpotBalanceSnapshot();
                startPeriodicSpotAccountRefresh();
                return;
            }
            // bootstrapSpotBalanceSnapshot();
            startUserSpotDataStream();
            startUserSpotTradeStream();
            //  startPeriodicSpotAccountRefresh();
            LOGGER.info("KuCoin Spot Client started successfully");
        } catch (final Exception e) {
            LOGGER.error("Failed to start KuCoin Spot Client: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void startFutureClient() {
        try {
            LOGGER.info("Starting KuCoin Futures Client...");

            bootstrapFutureBalanceSnapshot();
            startPeriodicFutureAccountRefresh();

            if (restOnly) {
                LOGGER.info("KucoinFastClient started in REST-only mode — WebSocket connections skipped");
                return;
            }

            startFutureUserDataStream();
            startFutureTradeStream();

            LOGGER.info("KuCoin Futures Client started successfully");
        } catch (final Exception e) {
            LOGGER.error("Failed to start KuCoin Futures Client: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void startUserSpotDataStream() throws Exception {
        try {
            final KucoinRestClient.PrivateTokenResult result = kuCoinRestClient.getSpotPrivateToken();
            if (result == null) {
                throw new IllegalStateException("KuCoin spot private token failed");
            }
            spotUserDataListener = new KucoinSpotUserDataListener(subscription, result.getToken(), result.getEndpoint(), () -> kuCoinRestClient.getSpotPrivateToken(), Context.getOutboundIp());
            spotUserDataListener.connect();
        } catch (final Exception e) {
            LOGGER.error("Error", e);
            throw new RuntimeException(e);
        }
    }

    private void stopSpotUserDataStream() {
        if (spotUserDataListener != null) {
            spotUserDataListener.disconnect();
            spotUserDataListener = null;
        }
    }

    private void startUserSpotTradeStream() {
        if (spotTradeListener == null) return;
        try {
            spotTradeListener.connect();
        } catch (final Exception e) {
            LOGGER.error("Failed to start KuCoin SpotTrade stream: " + e.getMessage(), e);
        }
    }

    private void stopSpotTradeStream() {
        if (spotTradeListener != null) {
            spotTradeListener.disconnect();
        }
    }

    private void startFutureUserDataStream() throws Exception {
        try {
            final KucoinRestClient.PrivateTokenResult result = kuCoinRestClient.getFuturesPrivateToken();
            if (result == null) {
                throw new IllegalStateException("KuCoin futures private token failed");
            }
            futureUserDataListener = new KucoinFutureUserDataListener(subscription, result.getToken(), result.getEndpoint(),
                    kuCoinRestClient::getFuturesPrivateToken, Context.getOutboundIp());
            futureUserDataListener.connect();
        } catch (final Exception e) {
            LOGGER.error("Failed to start KuCoin FutureUserData stream: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void stopFutureUserDataStream() {
        if (futureUserDataListener != null) {
            futureUserDataListener.disconnect();
            futureUserDataListener = null;
        }
    }

    private void startFutureTradeStream() {
        if (futureTradeListener == null) return;
        try {
            futureTradeListener.connect();
        } catch (final Exception e) {
            LOGGER.error("Failed to start KuCoin FutureTrade stream: " + e.getMessage(), e);
        }
    }

    private void stopFutureTradeStream() {
        if (futureTradeListener != null) {
            futureTradeListener.disconnect();
        }
    }

    // ======= Bootstrap Methods =======
    private void bootstrapSpotBalanceSnapshot() {
        try {
            LOGGER.info("Bootstrapping KuCoin Spot balance snapshot...");
            final String json = kuCoinRestClient.getSpotBalanceSnapshot();
            if (json != null) {
                applySpotAccountSnapshot(json);
                LOGGER.info("KuCoin Spot bootstrap snapshot applied successfully");
            } else {
                LOGGER.warn("Failed to retrieve KuCoin Spot balance snapshot");
            }
        } catch (final Exception e) {
            LOGGER.error("Error bootstrapping KuCoin Spot balance: " + e.getMessage());
        }
    }

    private void bootstrapFutureBalanceSnapshot() {
        try {
            LOGGER.info("Bootstrapping KuCoin Futures balance snapshot...");
            final String json = kuCoinRestClient.getFutureAccountSnapshot();
            if (json != null) {
                applyFutureAccountSnapshot(json);
                LOGGER.info("KuCoin Futures bootstrap snapshot applied successfully");
            } else {
                LOGGER.warn("Failed to retrieve KuCoin Futures balance snapshot");
            }
            final String positionInfo = kuCoinRestClient.getAllPositions();
            updateOpenPosition(positionInfo);
        } catch (final Exception e) {
            LOGGER.error("Error bootstrapping KuCoin Futures balance: " + e.getMessage());
        }
    }

    private void updateOpenPosition(final String positionInfo) {
        if (positionInfo == null) {
            LOGGER.warn("KuCoin position info response is null");
            return;
        }
        final String dataArray = extractJsonValue(positionInfo, "data");
        if (dataArray == null || !dataArray.startsWith("[")) {
            LOGGER.warn("No 'data' array in KuCoin position response");
            return;
        }
        int objStart = 0;
        int positionCount = 0;
        while (objStart < dataArray.length()) {
            objStart = dataArray.indexOf('{', objStart);
            if (objStart < 0) break;
            int braceCount = 0;
            int objEnd = objStart;
            for (int i = objStart; i < dataArray.length(); i++) {
                if (dataArray.charAt(i) == '{') braceCount++;
                else if (dataArray.charAt(i) == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        objEnd = i;
                        break;
                    }
                }
            }
            if (objEnd <= objStart) break;
            final String positionObj = dataArray.substring(objStart, objEnd + 1);
            final String symbol = minExtract(positionObj, "symbol");
            final String positionSide = minExtract(positionObj, "positionSide");
            final double currentQty = parseDoubleSafe(minExtract(positionObj, "currentQty"));
            if (symbol != null && currentQty != 0.0) {
                final boolean isShort = positionSide != null && "short".equalsIgnoreCase(positionSide.trim());
                final double signedSize = isShort ? -Math.abs(currentQty) : Math.abs(currentQty);
                subscription.updatePosition(symbol, signedSize);
                positionCount++;
                LOGGER.debug("Updated KuCoin position: symbol=" + symbol + " positionSide=" + positionSide + " currentQty=" + currentQty + " signedSize=" + signedSize);
            }
            objStart = objEnd + 1;
        }
        if (positionCount > 0) {
            LOGGER.info("KuCoin positions applied: " + positionCount + " position(s)");
        }
    }

    // ======= Periodic Refresh Methods =======
    // Same as Binance: startPeriodicSpotAccountRefresh
    private void startPeriodicSpotAccountRefresh() {
        if (isSpotAccountRefreshRunning) {
            LOGGER.warn("Periodic spot account refresh already running");
            return;
        }
        isSpotAccountRefreshRunning = true;
        spotAccountRefreshThread = Thread.ofVirtual().start(() -> {
            while (isSpotAccountRefreshRunning) {
                try {
                    // Delay first run to avoid racing with startup (reduces connection resets and speeds startup)
                    Thread.sleep(ONE_MINUTE);
                    LOGGER.info("Running periodic spot account snapshot refresh...");
                    bootstrapSpotBalanceSnapshot();
                    LOGGER.info("Periodic spot account snapshot refresh completed");
                    Thread.sleep(TEN_MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Periodic account snapshot failed: " + e.getMessage());
                    try {
                        Thread.sleep(ONE_MINUTE); // Wait 1 minute before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    // Same as Binance: startPeriodicFutureAccountRefresh
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
                    LOGGER.error("Periodic account snapshot failed ", e);
                    try {
                        Thread.sleep(ONE_MINUTE); // Wait 1 minute before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    private void stopPeriodicSpotAccountRefresh() {
        if (!isSpotAccountRefreshRunning) return;
        isSpotAccountRefreshRunning = false;
        LOGGER.info("Stopping periodic spot account refresh");
        if (spotAccountRefreshThread != null) {
            spotAccountRefreshThread.interrupt();
        }
    }

    private void stopPeriodicFutureAccountRefresh() {
        if (!isFutureAccountRefreshRunning) return;
        isFutureAccountRefreshRunning = false;
        LOGGER.info("Stopping periodic future account refresh");
        if (futureAccountRefreshThread != null) {
            futureAccountRefreshThread.interrupt();
        }
    }

    // ======= Account Snapshot Application Methods =======
    private void applySpotAccountSnapshot(final String json) {
        try {
            if (json == null || json.isEmpty()) {
                LOGGER.warn("Spot account snapshot JSON is null or empty");
                return;
            }
            LOGGER.debug("Parsing KuCoin Spot account snapshot, JSON length: " + json.length());
            final String dataArray = extractJsonValue(json, "data");
            if (dataArray == null || !dataArray.startsWith("[")) {
                LOGGER.warn("No 'data' array found in KuCoin spot accounts snapshot. JSON: " + (json.length() > 200 ? json.substring(0, 200) : json));
                return;
            }
            LOGGER.debug("Extracted data array, length: " + dataArray.length());
            int objStart = 0;
            int balanceCount = 0;
            while (objStart < dataArray.length()) {
                objStart = dataArray.indexOf('{', objStart);
                if (objStart < 0) break;
                int braceCount = 0;
                int objEnd = objStart;
                for (int i = objStart; i < dataArray.length(); i++) {
                    if (dataArray.charAt(i) == '{') braceCount++;
                    else if (dataArray.charAt(i) == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }
                if (objEnd <= objStart) break;
                final String accountObj = dataArray.substring(objStart, objEnd + 1);
                final String currency = minExtract(accountObj, "currency");
                final String type = minExtract(accountObj, "type");
                final double available = parseDoubleSafe(minExtract(accountObj, "available"));
                final double balance = parseDoubleSafe(minExtract(accountObj, "balance"));
                final double amount = available > 0.0 ? available : balance;
                LOGGER.debug("Parsed account: currency=" + currency + " type=" + type + " available=" + available + " balance=" + balance + " amount=" + amount);
                if (currency != null && amount >= 0.0) {
                    subscription.updateBalance(currency, amount);
                    balanceCount++;
                    LOGGER.info("SPOT BOOTSTRAP: Currency: " + currency + " available: " + available + " balance: " + balance + " -> updated: " + amount);
                } else {
                    LOGGER.debug("Skipping account: currency=" + currency + " amount=" + amount);
                }
                objStart = objEnd + 1;
            }
            LOGGER.info("Spot account snapshot applied successfully. Updated " + balanceCount + " balances");
        } catch (Exception e) {
            LOGGER.error("Error applying spot account snapshot: " + e.getMessage(), e);
        }
    }

    private void applyFutureAccountSnapshot(final String json) {
        try {
            if (json == null || json.isEmpty()) {
                LOGGER.warn("Futures account snapshot JSON is null or empty");
                return;
            }
            LOGGER.debug("Parsing KuCoin Futures account snapshot, JSON length: " + json.length());
            final String dataObj = extractJsonValue(json, "data");
            if (dataObj == null) {
                LOGGER.warn("No 'data' object found in KuCoin futures account snapshot. JSON: " + (json.length() > 200 ? json.substring(0, 200) : json));
                return;
            }
            LOGGER.debug("Extracted data object, length: " + dataObj.length());
            final String currency = minExtract(dataObj, "currency");
            final double accountEquity = parseDoubleSafe(minExtract(dataObj, "accountEquity"));
            final double availableBalance = parseDoubleSafe(minExtract(dataObj, "availableBalance"));
            final double balance = availableBalance > 0.0 ? availableBalance : accountEquity;
            LOGGER.debug("Parsed futures account: currency=" + currency + " accountEquity=" + accountEquity + " availableBalance=" + availableBalance + " balance=" + balance);
            if (currency != null && balance >= 0.0) {
                subscription.updateBalance(currency, balance);
                LOGGER.info("FUTURES BOOTSTRAP: Currency: " + currency + " accountEquity: " + accountEquity + " availableBalance: " + availableBalance + " -> updated: " + balance);
            } else {
                LOGGER.debug("Skipping futures account: currency=" + currency + " balance=" + balance);
            }
            LOGGER.info("Futures account snapshot applied successfully");
        } catch (Exception e) {
            LOGGER.error("Error applying futures account snapshot: " + e.getMessage(), e);
        }
    }
}
