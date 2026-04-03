package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget.BitgetRestClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget.BitgetTradeListener;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget.BitgetUserDataListener;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.StringUtil;

import java.util.List;
import java.util.Objects;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;

public final class BitgetFastClient implements ExternalExchangeClient {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(BitgetFastClient.class);
    private static final long TEN_MINUTES = 600_000;
    private static final long ONE_MINUTE = 60_000;

    private static final String STABLE_COIN_USDC = "USDC";
    private static final String STABLE_COIN_USDT = "USDT";

    private final ExchangeSubscription subscription;
    private final String apiKey;
    private final String secretKey;
    private final String passphrase;

    private final boolean restOnly;
    private final BitgetRestClient bitgetRestClient;
    private final BitgetTradeListener tradeListener;
    private volatile BitgetUserDataListener userDataListener;
    private volatile Thread accountRefreshThread;
    private volatile boolean isAccountRefreshRunning = false;

    public BitgetFastClient(final String apiKey, final String secretKey, final String passphrase, final ExchangeSubscription subscription) {
        this(apiKey, secretKey, passphrase, subscription, false);
    }

    public BitgetFastClient(final String apiKey, final String secretKey, final String passphrase,
                            final ExchangeSubscription subscription, final boolean restOnly) {
        this.restOnly = restOnly;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.passphrase = passphrase;
        this.subscription = subscription;
        this.bitgetRestClient = new BitgetRestClient(apiKey, secretKey, passphrase, subscription);
        this.tradeListener = restOnly ? null : new BitgetTradeListener(apiKey, secretKey, passphrase, subscription);
    }

    @Override
    public Ticker getTicker(final String base, final String quote, final int instrumentType) {
        final String symbol = base + quote;
        final Ticker ticker = BitgetRestClient.getTicker(symbol); //static method call using ClassName
        if (ticker != null) {
            ticker.setInstrumentType(instrumentType);
        }
        return ticker;
    }

    @Override
    public void start() {
        try {
            Objects.requireNonNull(apiKey);
            Objects.requireNonNull(secretKey);
            Objects.requireNonNull(passphrase);

            startPeriodicAccountRefresh();

            if (restOnly) {
                LOGGER.info("BitgetFastClient started in REST-only mode — WebSocket connections skipped");
                return;
            }
            // Initialize real-time data streams
            startUserDataStream();
            startTradeDataStream();

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        stopPeriodicAccountRefresh();
        if (userDataListener != null) {
            userDataListener.disconnect();
        }
        if (tradeListener != null) {
            tradeListener.disconnect();
        }
    }

    @Override
    public ExecutionReportMessage sendOrder(final Order order, final boolean futuresEnabled, final String bestQuoteSymbol,
                                            final String baseSymbol, final int priceScale, final int qtyScale, final double fxRate) {

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
            result = bitgetRestClient.cancelSpotOrderRest(order, order.getSymbol(), order.getClOrdId());
        } else {
            result = bitgetRestClient.cancelFuturesOrderRest(order, order.getSymbol(), order.getClOrdId());
        }
        return result;
    }

    @Override
    public List<ExternalSymbol> getExchangeInstrumentsFull() {
        return bitgetRestClient.getExchangeInstrumentsFull();
    }

    public boolean transferBalance(final String asset, final String amount, final boolean fromSpotToFutures) {
        // Bitget doesn't have direct transfer API, would need to be implemented differently
        LOGGER.warn("Bitget balance transfer not implemented yet");
        return false;
    }

    public String getAllOpenOrders() {
        final String jsonResp;
        if (subscription.isFuturesEnabled()) {
            jsonResp = bitgetRestClient.getAllOpenFuturesOrders();
        } else {
            jsonResp = bitgetRestClient.getAllOpenSpotOrders();
        }
        return jsonResp;
    }

    private void startPeriodicAccountRefresh() {
        if (isAccountRefreshRunning) {
            LOGGER.warn("Periodic account refresh already running");
            return;
        }

        isAccountRefreshRunning = true;
        accountRefreshThread = Thread.ofVirtual().start(() -> {
            while (isAccountRefreshRunning) {
                try {
                    LOGGER.info("Running periodic account snapshot refresh...");
                    bootstrapBalanceSnapshot();
                    LOGGER.info("Periodic account snapshot refresh completed");
                    Thread.sleep(TEN_MINUTES);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final Exception e) {
                    LOGGER.error("Periodic account snapshot failed: " + e.getMessage());
                    try {
                        Thread.sleep(ONE_MINUTE); // Wait 1 minute before retrying
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    private void stopPeriodicAccountRefresh() {
        if (!isAccountRefreshRunning) {
            return;
        }

        isAccountRefreshRunning = false;
        LOGGER.info("Stopping periodic account refresh");

        if (accountRefreshThread != null) {
            accountRefreshThread.interrupt();
        }
    }

    private void startUserDataStream() throws Exception {
        userDataListener = new BitgetUserDataListener(apiKey, secretKey, passphrase, subscription);
        userDataListener.connect();
    }

    private void startTradeDataStream() throws Exception {
        tradeListener.connect();
    }

    private void bootstrapBalanceSnapshot() {
        final String body = bitgetRestClient.getBalanceSnapshot();
        applyAccountSnapshot(body);

        // Fetch positions for both USDT and USDC settled contracts (similar to BybitFastClient pattern)
        if (subscription.isFuturesEnabled()) {
            final String openUSDTBasedPosition = bitgetRestClient.getPositionInfo(STABLE_COIN_USDT);
            updateOpenPosition(openUSDTBasedPosition);
            final String openUSDCBasedPosition = bitgetRestClient.getPositionInfo(STABLE_COIN_USDC);
            updateOpenPosition(openUSDCBasedPosition);
        }

        LOGGER.info("Bootstrap snapshot applied");
    }

    /**
     * Updates open positions from Bitget position info JSON response
     *
     * @param positionInfo JSON response containing position data
     */
    private void updateOpenPosition(final String positionInfo) {
        if (positionInfo == null) {
            LOGGER.warn("Position info response is null");
            return;
        }

        // Parse Bitget position info JSON response
        final int dataStart = positionInfo.indexOf("\"data\":");
        if (dataStart < 0) {
            LOGGER.warn("No \"data\" section in Bitget position response");
            return;
        }

        final int listStart = positionInfo.indexOf("\"list\":[", dataStart);
        if (listStart < 0) {
            LOGGER.warn("No \"list\" array in Bitget position response data");
            return;
        }

        // Find the matching closing bracket for the list array
        int bracketCount = 0;
        int arrayEnd = listStart + 8; // Start after "list":[
        for (int i = arrayEnd; i < positionInfo.length(); i++) {
            if (positionInfo.charAt(i) == '[') {
                bracketCount++;
            } else if (positionInfo.charAt(i) == ']') {
                bracketCount--;
                if (bracketCount == -1) {
                    arrayEnd = i;
                    break;
                }
            }
        }

        if (arrayEnd <= listStart + 8) {
            LOGGER.warn("Cannot find closing ] for position list array");
            return;
        }

        final String dataArray = positionInfo.substring(listStart + 8, arrayEnd);
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
            LOGGER.debug("Processing position object of length: " + positionObj.length());

            // Parse position data using the new JSON structure
            final String symbol = minExtract(positionObj, "symbol");
            final String posSide = minExtract(positionObj, "posSide");
            final double total = parseDoubleSafe(minExtract(positionObj, "total"));
            final double avgPrice = parseDoubleSafe(minExtract(positionObj, "avgPrice"));
            final double unrealisedPnl = parseDoubleSafe(minExtract(positionObj, "unrealisedPnl"));
            final String leverage = minExtract(positionObj, "leverage");
            final String marginCoin = minExtract(positionObj, "marginCoin");

            if (symbol != null && total != 0.0) {
                subscription.updatePosition(symbol, total);
                LOGGER.debug("Updated Bitget position: " + symbol + " posSide=" + posSide + " total=" + total +
                        " avgPrice=" + avgPrice + " unrealisedPnl=" + unrealisedPnl +
                        " leverage=" + leverage + " marginCoin=" + marginCoin);
            }

            objStart = objEnd + 1;
        }
    }

    private Order sendFutureOrder(final Order order, final String bestQuoteSymbol, final String baseSymbol,
                                  final int priceScale, final int qtyScale, final double fxRate) {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "limit" : "market";
        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? "fok" : "gtc";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order);

        if (tradeListener != null && tradeListener.isConnected()) {
            LOGGER.debug("Sending FUTURE order via websocket for symbol: " + order.getSymbol());
            tradeListener.placeFuturesOrder(order.getSymbol(), order.getSide().toString(), type,
                    timeInForce, qtyStr, priceStr, order.getClOrdId());
        } else {
            LOGGER.debug("Sending FUTURE order via REST for symbol: " + order.getSymbol());
            bitgetRestClient.sendFutureOrderREST(order, order.getSymbol(), order.getSide().toString(),
                    type, timeInForce, qtyStr, priceStr, order.getClOrdId());
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

        // Fallback to REST API polling if WebSocket doesn't provide updates
        LOGGER.debug("FUTURE: Start of Periodic REST API check for status update.");
        for (int i = 0; i < 100; i++) {
            bitgetRestClient.queryFuturesOrderStatus(order, order.getSymbol(), order.getClOrdId());
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

        /* //cancel order when status not updated
        if (!(order.isRejected() || order.isExecuted())) {
            boolean result = bitgetRestClient.cancelFuturesOrderRest(order, order.getSymbol(), order.getClOrdId());
            if (result) {
                LOGGER.info("SPOT: Successfully cancelled  order:{}", order);
            }
        }*/
        LOGGER.warn("FUTURE: Could not process the order for clientOrderId: " + order.getClOrdId());
        return order;
    }

    private Order sendSpotOrder(final Order order) {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "limit" : "market";
        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? "fok" : "gtc";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order);

        if (tradeListener != null && tradeListener.isConnected()) {
            LOGGER.debug("Sending SPOT order via websocket for symbol: " + order.getSymbol());
            tradeListener.placeSpotOrder(order.getSymbol(), order.getSide().toString(), type,
                    timeInForce, qtyStr, priceStr, order.getClOrdId());
        } else {
            LOGGER.debug("Sending SPOT order via REST for symbol: " + order.getSymbol());
            bitgetRestClient.sendSpotOrderREST(order, order.getSymbol(), order.getSide().toString(),
                    type, timeInForce, qtyStr, priceStr, order.getClOrdId());
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

        // Fallback to REST API polling if WebSocket doesn't provide updates
        LOGGER.debug("SPOT: Start of Periodic REST API check for status update.");
        for (int i = 0; i < 100; i++) {
            final boolean result = bitgetRestClient.querySpotOrderStatus(order, order.getSymbol(), order.getClOrdId());
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


        /*//cancel order when status not updated
        if (!(order.isRejected() || order.isExecuted())) {
            boolean result = bitgetRestClient.cancelSpotOrderRest(order, order.getSymbol(), order.getClOrdId());
            if (result) {
                LOGGER.info("SPOT: Successfully cancelled  order:{}", order);
            }
        }*/

        LOGGER.warn("SPOT: Could not process the order for clientOrderId: " + order.getClOrdId());
        return order;
    }

    private void applyAccountSnapshot(final String json) {
        if (json == null) return;

        final int assetsIdx = json.indexOf("\"assets\":[");
        if (assetsIdx < 0) return;

        final int arrayStart = json.indexOf('[', assetsIdx);
        if (arrayStart < 0) return;

        // Find the matching closing bracket for the assets array
        int bracketCount = 0;
        int arrayEnd = arrayStart;
        for (int i = arrayStart; i < json.length(); i++) {
            if (json.charAt(i) == '[') {
                bracketCount++;
            } else if (json.charAt(i) == ']') {
                bracketCount--;
                if (bracketCount == 0) {
                    arrayEnd = i;
                    break;
                }
            }
        }

        if (arrayEnd <= arrayStart) return;

        final String dataArray = json.substring(arrayStart + 1, arrayEnd);

        // Parse each asset object in the assets array
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

            final String obj = dataArray.substring(objStart, objEnd + 1);
            final String coin = minExtract(obj, "coin");
            final double available = parseDoubleSafe(minExtract(obj, "available"));

            if (coin != null && available > 0) {
                subscription.updateBalance(coin, available);
                LOGGER.debug("Updated balance: " + coin + " = " + available);
            }

            objStart = objEnd + 1;
        }
    }
}