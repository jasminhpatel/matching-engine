package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.DelistedSymbolCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.binance.*;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.StringUtil;

import java.util.List;
import java.util.Objects;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;
import static com.solfini.util.PrivateKeyBasedSigner.getEd25519PrivateKey;

public final class BinanceFastClient implements ExternalExchangeClient {

    // ======= Configuration (USDT-M Futures & Spot) =======
    private static final CustomLogger LOGGER = CustomLogger.getLogger(BinanceFastClient.class);
    private static final String WS_USERDATA_BASE = Context.getBinanceFuturesUserdataWs(); // + listenKey
    private static final long TEN_MINUTES = 600_000;
    private static final long ONE_MINUTE = 60_000;
    private final ExchangeSubscription subscription;
    // ======= API credentials =======
    private final String ed25519ApiKey;
    private final byte[] ed25519SecretKey;
    // ======= Scheduled Tasks =======
    private final BinanceRestClient binanceRestClient;
    private final BinanceSpotTradeListener spotTradeListener;
    private final BinanceFutureTradeListener futureTradeListener;
    private final boolean restOnly;
    private volatile BinanceFutureUserDataListener futureUserDataListener;
    private volatile BinanceSpotUserDataListener spotUserDataListener;
    private volatile String listenKey;
    private volatile Thread spotAccountRefreshThread;
    private volatile Thread futureAccountRefreshThread;
    private volatile boolean isSpotAccountRefreshRunning = false;
    private volatile boolean isFutureAccountRefreshRunning = false;

    // ======= Constructor =======
    public BinanceFastClient(final ExchangeSubscription subscription) {
        this(subscription, false);
    }

    public BinanceFastClient(final ExchangeSubscription subscription, final boolean restOnly) {
        this.restOnly = restOnly;
        this.subscription = subscription;
        this.ed25519ApiKey = Objects.requireNonNull(subscription.getApiKey2());
        this.ed25519SecretKey = getEd25519PrivateKey(StringUtil.processRSA(subscription.getApiSecret2()));
        this.binanceRestClient = new BinanceRestClient(ed25519ApiKey, this.ed25519SecretKey, subscription);

        if (restOnly) {
            spotTradeListener = null;
            futureTradeListener = null;
        } else {
            if (subscription.isFuturesEnabled()) {
                this.futureTradeListener = new BinanceFutureTradeListener(ed25519ApiKey, this.ed25519SecretKey, subscription);
                this.spotTradeListener = null;
            } else {
                this.spotTradeListener = new BinanceSpotTradeListener(ed25519ApiKey, this.ed25519SecretKey, subscription);
                this.futureTradeListener = null;
            }
        }

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
        // TODO need to implement
        stopPeriodicFutureAccountRefresh();
        stopPeriodicSpotAccountRefresh();
    }

    @Override
    public ExecutionReportMessage sendOrder(final Order order, final boolean futuresEnabled, final String bestQuoteSymbol,
                                            final String baseSymbol, final int priceScale, final int qtyScale, final double fxRate)
            throws Exception {

        if (futuresEnabled) {
            final Order rc = sendFutureOrder(order, bestQuoteSymbol, baseSymbol, priceScale, qtyScale, fxRate);
            return subscription.getExecutionReport(order.getClOrdId());
        } else {
            final Order rc = sendSpotOrder(order);
            return subscription.getExecutionReport(order.getClOrdId());
        }
    }

    @Override
    public boolean cancelOrder(Order order, boolean isSpotOrder) {
        boolean result;
        if (isSpotOrder) {
            result = binanceRestClient.cancelSpotOrderRest(order, order.getSymbol(), order.getClOrdId());
        } else {
            result = binanceRestClient.cancelFuturesOrderRest(order, order.getSymbol(), order.getClOrdId());
        }
        return result;
    }

    @Override
    public List<ExternalSymbol> getExchangeInstrumentsFull() {
        return binanceRestClient.getExchangeInstrumentsFull();
    }

    @Override
    public List<DelistedSymbol> getDelistedSymbols() {
        return subscription.isFuturesEnabled()
                ? binanceRestClient.getFuturesDelistedSymbols()
                : binanceRestClient.getSpotDelistedSymbols();
    }

    @Override
    public Ticker getTicker(final String base, final String quote, final int instrumentType) {
        final String symbol = base + quote;
        final Ticker ticker = binanceRestClient.getTicker(symbol, instrumentType == 1);
        if (ticker != null) {
            ticker.setInstrumentType(instrumentType);
        }
        return ticker;
    }

    private boolean transferBalanceFromSpotToFutures(final String asset, final String amount)
            throws Exception {
        LOGGER.info("Transferring balance from Spot to Futures: asset=" + asset + " amount=" + amount);
        return binanceRestClient.transferSpotToFutures(asset, amount);
    }

    private boolean transferBalanceFromFuturesToSpot(final String asset, final String amount)
            throws Exception {
        LOGGER.info("Transferring balance from Futures to Spot: asset=" + asset + " amount=" + amount);
        return binanceRestClient.transferFuturesToSpot(asset, amount);
    }

    public boolean transferBalance(final String asset, final String amount, final boolean fromSpotToFutures)
            throws Exception {
        if (fromSpotToFutures) {
            return transferBalanceFromSpotToFutures(asset, amount);
        } else {
            return transferBalanceFromFuturesToSpot(asset, amount);
        }
    }

    public String getAllOpenOrders() {
        // final String[] clientOrderIds;
        final String jsonResp;
        if (subscription.isFuturesEnabled()) {
            jsonResp = binanceRestClient.getAllOpenFuturesOrders();
        } else {
            jsonResp = binanceRestClient.getAllOpenSpotOrders();
        }

        return jsonResp;
    }

    // ======= Internal API ======= //
    private void startSpotClient() {

        try {
            // get snapshot via REST
            bootstrapSpotBalanceSnapshot();

            // poll using REST
            startPeriodicSpotAccountRefresh();

            if (!restOnly) {
                startUserSpotBalanceStream();
                startUserSpotTradeStream();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void startFutureClient() {
        startPeriodicFutureAccountRefresh();

        if (!restOnly) {
            startUserFutureDataStream();
            startUserFutureTradeStream();
        }
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

    // Start periodic account snapshot refresh
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
                    bootstrapAccountSnapshot();
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

    // Stop periodic refresh and cleanup
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

    private void startUserFutureDataStream() {
        final String lk = binanceRestClient.createListenKey();
        this.listenKey = lk;
        final String wsUrl = WS_USERDATA_BASE + "/" + lk;
        LOGGER.info("Connecting UserData Future WS: " + wsUrl);
        try {
            futureUserDataListener = new BinanceFutureUserDataListener(wsUrl, ed25519ApiKey, ed25519SecretKey, subscription);
            futureUserDataListener.connect();
        } catch (final Exception e) {
            LOGGER.error("Error", e);
            throw new RuntimeException(e);
        }
    }

    private void startUserSpotBalanceStream() throws Exception {
        try {
            spotUserDataListener = new BinanceSpotUserDataListener(ed25519ApiKey, ed25519SecretKey, subscription);
            spotUserDataListener.connect();
        } catch (final Exception e) {
            LOGGER.error("Error", e);
            throw new RuntimeException(e);
        }
    }

    private void startUserSpotTradeStream() throws Exception {
        try {
            spotTradeListener.connect();
        } catch (final Exception e) {
            LOGGER.error("Error", e);
            throw new RuntimeException(e);
        }
    }

    private void startUserFutureTradeStream() {
        try {
            futureTradeListener.connect();
        } catch (final Exception e) {
            LOGGER.error("Error", e);
            throw new RuntimeException(e);
        }
    }

    private void bootstrapAccountSnapshot() {
        final String body = binanceRestClient.getFutureAccountSnapshot();
        applyFutureAccountSnapshot(body);
        LOGGER.info("Bootstrap snapshot applied");
    }

    private void bootstrapSpotBalanceSnapshot() throws Exception {

        final String body = binanceRestClient.getSpotBalanceSnapshot();
        applySpotAccountSnapshot(body);
        LOGGER.info("Bootstrap snapshot applied");
    }

    private Order sendFutureOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce,
                                      final String quantityStr, final String priceStr, final String positionSide, final boolean reduceOnly)
            throws Exception {

        final boolean result =
                binanceRestClient.sendFutureOrderREST(order, symbol, side, type, timeInForce, quantityStr, priceStr, positionSide, reduceOnly);
        LOGGER.info("Result of sendFutureOrderREST: " + result);
        return order;
    }

    private Order sendSpotOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce,
                                    final String quantityStr, final String priceStr, final String clientOrderId)
            throws Exception {

        final boolean result =
                binanceRestClient.sendSpotOrderREST(order, symbol, side, type, timeInForce, quantityStr, priceStr, clientOrderId);
        LOGGER.info("Result of sendSpotOrderREST: " + result);
        return order;
    }

    private Order sendFutureOrder(final Order order, final String bestQuoteSymbol, final String baseSymbol, final int priceScale,
                                  final int qtyScale, final double fxRate) throws Exception {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "LIMIT" : "MARKET";
        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? "FOK" : "GTC";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order); // TBD if we set newOrder before sending then how to catch when some error happen related to account

        if (futureTradeListener != null && futureTradeListener.isConnected()) { // sending via websocket
            LOGGER.debug("Sending FUTURE order via websocket for symbol: " + order.getSymbol());
            futureTradeListener.placeOrder(order.getSymbol(), order.getSide().toString(), type, timeInForce, qtyStr, priceStr,
                    order.getClOrdId());
        } else {
            LOGGER.debug("Sending FUTURE order via REST for symbol: " + order.getSymbol());
            sendFutureOrderREST(order, order.getSymbol(), order.getSide().toString(), type, timeInForce, qtyStr, priceStr, "BOTH", false);
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

            binanceRestClient.queryFuturesOrderStatus(order, order.getSymbol(), order.getClOrdId());
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

        LOGGER.warn("FUTURE: we couldn't process the order for order: " + order.getClOrdId());

        /*
         * cancel order when status not updated if (!(order.isRejected() || order.isExecuted())) { boolean result =
         * binanceRestClient.cancelFuturesOrderRest(order, order.getSymbol(), order.getClOrdId()); if (result) {
         * LOGGER.info("SPOT: Successfully cancelled  order:{}", order); } }
         */

        return order;
    }

    private Order sendSpotOrder(final Order order) throws Exception {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "LIMIT" : "MARKET";
        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? "FOK" : "GTC";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order);

        if (spotTradeListener != null && spotTradeListener.isConnected()) {
            LOGGER.debug("Sending SPOT order via websocket for symbol: " + order.getSymbol());
            spotTradeListener.placeOrder(order.getSymbol(), order.getSide().toString(), type, timeInForce, qtyStr, priceStr, order.getClOrdId());
        } else {
            LOGGER.debug("Sending SPOT order via REST for symbol: " + order.getSymbol());
            sendSpotOrderREST(order, order.getSymbol(), order.getSide().toString(), type, timeInForce, qtyStr, priceStr,
                    order.getClOrdId());
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

            final boolean result = binanceRestClient.querySpotOrderStatus(order, order.getSymbol(), order.getClOrdId());
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

        /*
         * cancel order when status not updated if (!(order.isRejected() || order.isExecuted())) { boolean result =
         * binanceRestClient.cancelSpotOrderRest(order, order.getSymbol(), order.getClOrdId()); if (result) {
         * LOGGER.info("FUTURE: Successfully cancelled  order:{}", order); }
         *
         * }
         */
        return order;
    }

    public static String withBrokerId(final String brokerId, final String clOrdId) {
        return (brokerId != null && !brokerId.isEmpty()) ? "x-" + brokerId + clOrdId : clOrdId;
    }

    private void applyFutureAccountSnapshot(final String json) {

        final int aStart = json.indexOf("\"assets\":[");
        if (aStart >= 0) {
            final int aEnd = json.indexOf(']', aStart);
            final String aArr = json.substring(aStart + 10, aEnd);
            int p = 0;
            while (p >= 0 && p < aArr.length()) {
                final int objStart = aArr.indexOf('{', p);
                if (objStart < 0)
                    break;
                final int objEnd = aArr.indexOf('}', objStart);
                final String obj = aArr.substring(objStart, objEnd + 1);
                final String asset = minExtract(obj, "asset");
                final double walletBalance = parseDoubleSafe(minExtract(obj, "walletBalance"));
                final double crossWalletBalance = parseDoubleSafe(minExtract(obj, "crossWalletBalance"));
                final double totalInitialMargin = parseDoubleSafe(minExtract(obj, "totalInitialMargin"));
                final double totalMaintMargin = parseDoubleSafe(minExtract(obj, "totalMaintMargin"));
                final double totalMarginBalance = parseDoubleSafe(minExtract(obj, "totalMarginBalance"));
                if (asset != null)
                    subscription.updateBalance(asset, walletBalance); // its wallet balance (not cross wallet)
                p = objEnd + 1;
            }
        }
        // Positions:
        final int pStart = json.indexOf("\"positions\":[");
        if (pStart >= 0) {
            final int pEnd = json.indexOf(']', pStart);
            final String pArr = json.substring(pStart + 12, pEnd);
            int q = 0;
            while (q >= 0 && q < pArr.length()) {
                final int objStart = pArr.indexOf('{', q);
                if (objStart < 0)
                    break;
                final int objEnd = pArr.indexOf('}', objStart);
                final String obj = pArr.substring(objStart, objEnd + 1);
                final String symbol = minExtract(obj, "symbol");
                final double positionAmt = parseDoubleSafe(minExtract(obj, "positionAmt"));
                final double entryPrice = parseDoubleSafe(minExtract(obj, "entryPrice"));
                final double unrealizedProfit = parseDoubleSafe(minExtract(obj, "unrealizedProfit"));
                final String positionSide = minExtract(obj, "positionSide");
                if (symbol != null)
                    subscription.updatePosition(symbol, positionAmt); // position amount

                q = objEnd + 1;
            }
        }
    }

    private void applySpotAccountSnapshot(final String json) {

        final int bStart = json.indexOf("\"balances\":[");
        if (bStart >= 0) {
            final int bEnd = json.indexOf(']', bStart);
            final String bArr = json.substring(bStart + 11, bEnd); // +11 to move past "balances":[
            int p = 0;
            while (p >= 0 && p < bArr.length()) {
                final int objStart = bArr.indexOf('{', p);
                if (objStart < 0)
                    break;
                final int objEnd = bArr.indexOf('}', objStart);
                final String obj = bArr.substring(objStart, objEnd + 1);
                final String asset = minExtract(obj, "asset");
                final double freeAsset = parseDoubleSafe(minExtract(obj, "free"));
                final double lockedAsset = parseDoubleSafe(minExtract(obj, "locked"));

                // For spot, you may want to use free+locked for total or keep them separate.
                if (asset != null) {
                    subscription.updateBalance(asset, freeAsset); // Or update both free and locked as needed
                }
                p = objEnd + 1;
            }
        }
    }


}