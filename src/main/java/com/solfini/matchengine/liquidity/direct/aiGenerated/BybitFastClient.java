package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.DelistedSymbolCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit.BybitRestClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit.BybitTradeDataListener;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit.BybitUserDataListener;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.StringUtil;

import java.util.List;
import java.util.Objects;

import static com.solfini.common.Constants.*;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;

public final class BybitFastClient implements ExternalExchangeClient {

    // ======= Configuration (USDT-M Futures & Spot) =======
    private static final CustomLogger LOGGER = CustomLogger.getLogger(BybitFastClient.class);

    private static final long TEN_MINUTES = 600_000;
    private static final long ONE_MINUTE = 60_000;
    private static final String STABLE_COIN_USDC = "USDC";
    private static final String STABLE_COIN_USDT = "USDT";
    private static final String TIME_IN_FORCE_GTC = "GTC";
    private static final String TIME_IN_FORCE_FOK = "FOK";
    private static final String SIDE_BUY = "Buy";
    private static final String SIDE_SELL = "Sell";
    private static final String TYPE_LIMIT = "Limit";
    private static final String TYPE_MARKET = "Market";

    // Core subscription and configuration objects - never reassigned after construction
    private final ExchangeSubscription subscription;
    private final String apiKey;
    private final String secretKey; // Change from byte[] to String for HMAC
    private final boolean restOnly;
    private final BybitRestClient bybitRestClient;
    private final BybitTradeDataListener bybitTradeDataListener;

    // WebSocket connections and thread management - can be reassigned during reconnections
    private volatile Thread spotAccountRefreshThread;
    private volatile boolean isAccountRefreshRunning = false;

    // ======= Constructor =======

    /**
     * Initializes ByBit client with API credentials and subscription management
     *
     * @param apiKey       ByBit API key for authentication
     * @param apiSecret    ByBit API secret for HMAC signature generation
     * @param subscription Liquidity subscription manager for order and balance tracking
     */
    public BybitFastClient(final String apiKey, final String apiSecret, final ExchangeSubscription subscription) {
        this(apiKey, apiSecret, subscription, false);
    }

    public BybitFastClient(final String apiKey, final String apiSecret, final ExchangeSubscription subscription, final boolean restOnly) {
        this.restOnly = restOnly;
        this.apiKey = Objects.requireNonNull(apiKey);
        this.subscription = subscription;
        this.secretKey = apiSecret; // Use apiSecret directly as string for HMAC-SHA256
        this.bybitTradeDataListener = restOnly ? null : new BybitTradeDataListener(apiKey, secretKey, subscription);
        this.bybitRestClient = new BybitRestClient(apiKey, secretKey.getBytes(), subscription);
    }

    @Override
    public void start() {
        // first, so a failing account login below does not stop it; delisted symbols now, then every 10 minutes
        DelistedSymbolCache.start(subscription);
        try {
            // Start periodic REST API polling for account state synchronization
            startPeriodicAccountRefresh();

            if (restOnly) {
                LOGGER.info("BybitFastClient started in REST-only mode — WebSocket connections skipped");
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
  public List<DelistedSymbol> getDelistedSymbols() {
    return subscription.isFuturesEnabled()
        ? bybitRestClient.getFuturesDelistedSymbols()
        : bybitRestClient.getSpotDelistedSymbols();
  }

  @Override
  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    return bybitRestClient.getExchangeInstrumentsFull();
  }

    @Override
    public Ticker getTicker(final String base, final String quote, final int instrumentType) {
        final String symbol = base + quote;
        final String category = (instrumentType == 1) ? "linear" : "spot";
        final Ticker ticker = bybitRestClient.getTicker(symbol, category);
        if (ticker != null) {
            ticker.setInstrumentType(instrumentType);
        }
        return ticker;
    }

  /*  public String getAllOpenOrders() {
        final String jsonResp;
        if (subscription.isFuturesEnabled()) {
            jsonResp = bybitRestClient.getAllOpenFuturesOrders();
        } else {
            jsonResp = bybitRestClient.getAllOpenSpotOrders();
        }
        return jsonResp;
    }
*/
    // ======= Internal API ======= //

    /**
     * Starts periodic account balance and position refresh using REST API
     * Runs every 10 minutes to ensure data consistency with exchange
     */
    private void startPeriodicAccountRefresh() {
        if (isAccountRefreshRunning) {
            LOGGER.warn("Periodic spot account refresh already running");
            return;
        }

        isAccountRefreshRunning = true;
        spotAccountRefreshThread = Thread.ofVirtual().start(() -> {
            while (isAccountRefreshRunning) {
                try {
                    LOGGER.info("Running periodic spot account snapshot refresh...");
                    bootstrapBalanceSnapshot();
                    LOGGER.info("Periodic spot account snapshot refresh completed");
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

    /**
     * Stops the periodic account refresh thread gracefully
     */
    private void stopPeriodicAccountRefresh() {
        if (!isAccountRefreshRunning) {
            return;
        }

        isAccountRefreshRunning = false;
        LOGGER.info("Stopping periodic spot account refresh");

        if (spotAccountRefreshThread != null) {
            spotAccountRefreshThread.interrupt();
        }
    }

    /**
     * Initializes user data WebSocket stream for real-time account updates
     */
    private void startUserDataStream() {
        try {
            final BybitUserDataListener userDataListener = new BybitUserDataListener(apiKey, secretKey, subscription);
            userDataListener.connect();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Initializes trade data WebSocket stream for order management
     */
    private void startTradeDataStream() throws Exception {
        bybitTradeDataListener.connect();
    }

    /**
     * Bootstraps initial account state by fetching balances and positions via REST API
     */
    private void bootstrapBalanceSnapshot() {
        final String body = bybitRestClient.getBalanceSnapshot();
        applySpotAccountSnapshot(body);

        // Fetch positions for both USDT and USDC settled contracts
        final String openUSDTBasedPosition = bybitRestClient.getAllPositions(STABLE_COIN_USDT);
        updateOpenPosition(openUSDTBasedPosition);
        final String openUSDCBasedPosition = bybitRestClient.getAllPositions(STABLE_COIN_USDC);
        updateOpenPosition(openUSDCBasedPosition);

        LOGGER.info("Bootstrap snapshot applied");
    }

    /**
     * Updates open positions from ByBit position list JSON response
     *
     * @param openPosition JSON response containing position data
     */
    private void updateOpenPosition(final String openPosition) {
        if (openPosition == null) {
            LOGGER.warn("Open position response is null");
            return;
        }

        // Parse ByBit position list JSON response
        final int resultStart = openPosition.indexOf("\"result\":");
        if (resultStart < 0) {
            LOGGER.warn("No \"result\" section in ByBit position response");
            return;
        }

        final int listStart = openPosition.indexOf("\"list\":[", resultStart);
        if (listStart < 0) {
            LOGGER.warn("No \"list\" array in ByBit position response");
            return;
        }

        // Find the matching closing bracket for the "list" array
        int listEnd = -1;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        // Start from after "list":[
        for (int i = listStart + 8; i < openPosition.length(); i++) {
            final char c = openPosition.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '[') {
                    bracketDepth++;
                } else if (c == ']') {
                    if (bracketDepth == 0) {
                        listEnd = i;
                        break;
                    } else {
                        bracketDepth--;
                    }
                }
            }
        }

        if (listEnd == -1) {
            LOGGER.warn("Cannot find closing ] for \"list\" array in position response");
            return;
        }

        final String listArr = openPosition.substring(listStart + 8, listEnd);
        LOGGER.debug("Position list array content: " + listArr.substring(0, Math.min(200, listArr.length())) + "...");

        // Parse each position object in the array
        int p = 0;
        while (p >= 0 && p < listArr.length()) {
            final int objStart = listArr.indexOf('{', p);
            if (objStart < 0) break;

            // Find matching closing brace for this position object
            int objEnd = -1;
            int braceDepth = 0;
            boolean objInString = false;
            boolean objEscapeNext = false;

            for (int i = objStart; i < listArr.length(); i++) {
                final char c = listArr.charAt(i);

                if (objEscapeNext) {
                    objEscapeNext = false;
                    continue;
                }

                if (c == '\\') {
                    objEscapeNext = true;
                    continue;
                }

                if (c == '"') {
                    objInString = !objInString;
                    continue;
                }

                if (!objInString) {
                    if (c == '{') {
                        braceDepth++;
                    } else if (c == '}') {
                        braceDepth--;
                        if (braceDepth == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }
            }

            if (objEnd <= objStart) break;

            final String positionObj = listArr.substring(objStart, objEnd + 1);
            LOGGER.debug("Processing position object of length: " + positionObj.length());

            // Parse position data
            final String symbol = minExtract(positionObj, "symbol");
            final String side = minExtract(positionObj, "side");
            double size = parseDoubleSafe(minExtract(positionObj, "size"));
            final double avgPrice = parseDoubleSafe(minExtract(positionObj, "avgPrice"));
            final double unrealisedPnl = parseDoubleSafe(minExtract(positionObj, "unrealisedPnl"));
            final double positionValue = parseDoubleSafe(minExtract(positionObj, "positionValue"));
            final String leverage = minExtract(positionObj, "leverage");

            if ("Sell".equalsIgnoreCase(side)) {// -ve for short
                size = -size;
            }

            if (symbol != null /*&& size != 0.0*/) {
                subscription.updatePosition(symbol, size);
                LOGGER.debug("Updated ByBit position: " + symbol + " side=" + side + " size=" + size +
                        " avgPrice=" + avgPrice + " unrealisedPnl=" + unrealisedPnl + " positionValue=" + positionValue +
                        " leverage=" + leverage);
            }

            p = objEnd + 1;
        }
    }

    /**
     * Sends futures order via REST API as fallback method
     */
    private Order sendFutureOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce, final String quantityStr,
                                      final String priceStr, final String positionSide, final boolean reduceOnly) {
        final boolean result = bybitRestClient.sendFutureOrderREST(order, symbol, side, type, timeInForce, quantityStr,
                priceStr, positionSide, reduceOnly);
        LOGGER.info("Result of sendFutureOrderREST: " + result);
        return order;
    }

    /**
     * Sends spot order via REST API as fallback method
     */
    private Order sendSpotOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce, final String quantityStr,
                                    final String priceStr, final String clientOrderId) {
        final boolean result = bybitRestClient.sendSpotOrderREST(order, symbol, side, type, timeInForce, quantityStr,
                priceStr, clientOrderId);
        LOGGER.info("Result of sendSpotOrderREST: " + result);
        return order;
    }

    /**
     * Converts internal order type to ByBit's required format
     *
     * @param orderType Internal order type (integer)
     * @return ByBit-compatible order type string
     */
    private String convertOrderTypeToBybit(final int orderType) {
        switch (orderType) {
            case BUY_LIMIT:
            case SELL_LIMIT:
                return TYPE_LIMIT;
            case BUY_MARKET:
            case SELL_MARKET:
                return TYPE_MARKET;
            default:
                LOGGER.warn("Unknown order type: " + orderType + ", defaulting to LIMIT");
                return TYPE_LIMIT;
        }
    }

    /**
     * Sends futures order with WebSocket fallback to REST API
     *
     * @param order           The order object containing order details
     * @param bestQuoteSymbol Best quote symbol for the order
     * @param baseSymbol      Base symbol for the order
     * @param priceScale      Price decimal places
     * @param qtyScale        Quantity decimal places
     * @param fxRate          Foreign exchange rate for conversion
     * @return Updated order object with execution status
     */
    private Order sendFutureOrder(final Order order, final String bestQuoteSymbol, final String baseSymbol, final int priceScale, final int qtyScale, final double fxRate) {

        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? TIME_IN_FORCE_FOK : TIME_IN_FORCE_GTC;
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());
        final String side = order.getSide().equals(Side.BUY) ? SIDE_BUY : SIDE_SELL;
        final String bybitOrderType = convertOrderTypeToBybit(order.getType());

        // Cache order for tracking
        subscription.cacheNewOrder(order);

        // Try WebSocket first, fallback to REST if not available
        if (bybitTradeDataListener != null && bybitTradeDataListener.isConnected()) {
            LOGGER.debug("Sending FUTURE order via websocket for symbol: " + order.getSymbol());
            bybitTradeDataListener.placeLinearOrder(order.getSymbol(), side, bybitOrderType, timeInForce, qtyStr, priceStr,
                    order.getClOrdId());
        } else {
            LOGGER.debug("Sending FUTURE order via REST (WebSocket not available) for symbol: " + order.getSymbol());
            sendFutureOrderREST(order, order.getSymbol(), side, bybitOrderType, timeInForce, qtyStr, priceStr, "0", order.isReduceOnly());
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
            bybitRestClient.queryFuturesOrderStatus(order, order.getSymbol(), order.getClOrdId(), i + 1);
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

/*        // Cancel order when status not updated
        if (!(order.isRejected() || order.isExecuted())) {
            boolean result = bybitRestClient.cancelFuturesOrderRest(order, order.getSymbol(), order.getClOrdId());
            if (result) {
                LOGGER.info("FUTURE: Successfully cancelled order: " + order.getClOrdId());
            }
        }*/

        return order;
    }

    /**
     * Sends spot order with WebSocket fallback to REST API
     *
     * @param order The order object containing order details
     * @return Updated order object with execution status
     */
    private Order sendSpotOrder(final Order order) {

        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? TIME_IN_FORCE_FOK : TIME_IN_FORCE_GTC;
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());
        final String side = order.getSide().equals(Side.BUY) ? SIDE_BUY : SIDE_SELL;
        final String bybitOrderType = convertOrderTypeToBybit(order.getType());

        // Cache order for tracking
        subscription.cacheNewOrder(order);

        // Try WebSocket first, fallback to REST if not available
        if (bybitTradeDataListener != null && bybitTradeDataListener.isConnected()) {
            LOGGER.debug("Sending SPOT order via websocket for symbol: " + order.getSymbol());
            bybitTradeDataListener.placeSpotOrder(order.getSymbol(), side, bybitOrderType, timeInForce, qtyStr, priceStr,
                    order.getClOrdId());
        } else {
            LOGGER.debug("Sending SPOT order via REST (WebSocket not available) for symbol: " + order.getSymbol());
            sendSpotOrderREST(order, order.getSymbol(), side, bybitOrderType, timeInForce, qtyStr, priceStr, order.getClOrdId());
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
            final boolean result = bybitRestClient.querySpotOrderStatus(order, order.getSymbol(), order.getClOrdId(), i + 1);
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

      /*  // Cancel order when status not updated
        if (!(order.isRejected() || order.isExecuted())) {
            boolean result = bybitRestClient.cancelSpotOrderRest(order, order.getSymbol(), order.getClOrdId());
            if (result) {
                LOGGER.info("SPOT: Successfully cancelled order: " + order.getClOrdId());
            }
        }*/

        return order;
    }


    @Override
    public boolean cancelOrder(Order order, boolean isSpotOrder) {
        boolean result;
        if (isSpotOrder) {
            result = bybitRestClient.cancelSpotOrderRest(order, order.getSymbol(), order.getClOrdId());
        } else {
            result = bybitRestClient.cancelFuturesOrderRest(order, order.getSymbol(), order.getClOrdId());
        }
        return result;
    }

    /**
     * Applies spot account snapshot from ByBit UNIFIED account JSON response
     * Parses wallet balances and updates local subscription cache
     *
     * @param json JSON response from ByBit balance endpoint
     */
    private void applySpotAccountSnapshot(final String json) {
        // Parse ByBit UNIFIED account snapshot JSON - Fixed for actual v5 API structure
        final int resultStart = json.indexOf("\"result\":");
        if (resultStart < 0) {
            LOGGER.warn("No \"result\" section in ByBit balance response");
            return;
        }

        final int listStart = json.indexOf("\"list\":[", resultStart);
        if (listStart < 0) {
            LOGGER.warn("No \"list\" array in ByBit balance response");
            return;
        }

        // Find the matching closing bracket for the "list" array using proper JSON parsing
        int listEnd = -1;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        // Start from after "list":[
        for (int i = listStart + 8; i < json.length(); i++) {
            final char c = json.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '[') {
                    bracketDepth++;
                } else if (c == ']') {
                    if (bracketDepth == 0) {
                        listEnd = i;
                        break;
                    } else {
                        bracketDepth--;
                    }
                }
            }
        }

        if (listEnd == -1) {
            LOGGER.warn("Cannot find closing ] for \"list\" array");
            return;
        }

        final String listArr = json.substring(listStart + 8, listEnd);
        LOGGER.debug("List array content: " + listArr.substring(0, Math.min(200, listArr.length())) + "...");

        // Parse each account object in the list
        int p = 0;
        while (p >= 0 && p < listArr.length()) {
            final int objStart = listArr.indexOf('{', p);
            if (objStart < 0) break;

            // Find matching closing brace for this account object
            int objEnd = -1;
            int braceDepth = 0;
            boolean objInString = false;
            boolean objEscapeNext = false;

            for (int i = objStart; i < listArr.length(); i++) {
                final char c = listArr.charAt(i);

                if (objEscapeNext) {
                    objEscapeNext = false;
                    continue;
                }

                if (c == '\\') {
                    objEscapeNext = true;
                    continue;
                }

                if (c == '"') {
                    objInString = !objInString;
                    continue;
                }

                if (!objInString) {
                    if (c == '{') {
                        braceDepth++;
                    } else if (c == '}') {
                        braceDepth--;
                        if (braceDepth == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }
            }

            if (objEnd <= objStart) break;

            final String obj = listArr.substring(objStart, objEnd + 1);
            LOGGER.debug("Processing account object of length: " + obj.length());

            // Parse coin array within each account
            final int coinStart = obj.indexOf("\"coin\":[");
            if (coinStart >= 0) {
                // Find the closing bracket for the coin array
                int coinArrEnd = -1;
                int coinBracketDepth = 0;
                boolean coinInString = false;
                boolean coinEscapeNext = false;

                for (int i = coinStart + 8; i < obj.length(); i++) {
                    final char c = obj.charAt(i);

                    if (coinEscapeNext) {
                        coinEscapeNext = false;
                        continue;
                    }

                    if (c == '\\') {
                        coinEscapeNext = true;
                        continue;
                    }

                    if (c == '"') {
                        coinInString = !coinInString;
                        continue;
                    }

                    if (!coinInString) {
                        if (c == '[') {
                            coinBracketDepth++;
                        } else if (c == ']') {
                            if (coinBracketDepth == 0) {
                                coinArrEnd = i;
                                break;
                            } else {
                                coinBracketDepth--;
                            }
                        }
                    }
                }

                if (coinArrEnd > coinStart) {
                    final String coinArr = obj.substring(coinStart + 8, coinArrEnd);
                    LOGGER.debug("Coin array: " + coinArr);

                    // Parse each coin object in the array
                    int q = 0;
                    while (q >= 0 && q < coinArr.length()) {
                        final int coinObjStart = coinArr.indexOf('{', q);
                        if (coinObjStart < 0) break;

                        // Find matching closing brace for coin object
                        int coinObjEnd = -1;
                        int coinBraceDepth = 0;
                        boolean coinObjInString = false;
                        boolean coinObjEscapeNext = false;

                        for (int i = coinObjStart; i < coinArr.length(); i++) {
                            final char c = coinArr.charAt(i);

                            if (coinObjEscapeNext) {
                                coinObjEscapeNext = false;
                                continue;
                            }

                            if (c == '\\') {
                                coinObjEscapeNext = true;
                                continue;
                            }

                            if (c == '"') {
                                coinObjInString = !coinObjInString;
                                continue;
                            }

                            if (!coinObjInString) {
                                if (c == '{') {
                                    coinBraceDepth++;
                                } else if (c == '}') {
                                    coinBraceDepth--;
                                    if (coinBraceDepth == 0) {
                                        coinObjEnd = i;
                                        break;
                                    }
                                }
                            }
                        }

                        if (coinObjEnd > coinObjStart) {
                            final String coinObj = coinArr.substring(coinObjStart, coinObjEnd + 1);
                            final String coinName = minExtract(coinObj, "coin");
                            final double walletBalance = parseDoubleSafe(minExtract(coinObj, "walletBalance"));
                            final double equity = parseDoubleSafe(minExtract(coinObj, "equity"));

                            if (coinName != null && walletBalance > 0) {
                                subscription.updateBalance(coinName, walletBalance);
                                LOGGER.debug("Updated ByBit balance: " + coinName + " walletBalance=" + walletBalance + " equity=" + equity);
                            }
                            q = coinObjEnd + 1;
                        } else {
                            break;
                        }
                    }
                } else {
                    LOGGER.warn("Could not find closing bracket for coin array");
                }
            } else {
                LOGGER.warn("No coin array found in account object");
            }
            p = objEnd + 1;
        }
    }
}