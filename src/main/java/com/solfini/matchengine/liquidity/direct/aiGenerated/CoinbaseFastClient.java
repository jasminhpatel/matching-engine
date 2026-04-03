package com.solfini.matchengine.liquidity.direct.aiGenerated;

import java.util.*;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.coinbase.CoinbaseRestClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.coinbase.CoinbaseUserDataListener;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.util.StringUtil;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public final class CoinbaseFastClient implements ExternalExchangeClient {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(CoinbaseFastClient.class);
    private static final long TEN_MINUTES = 600_000;
    private static final long ONE_MINUTE = 60_000;

    private final ExchangeSubscription subscription;

    private final String apiKey;
    private final String apiSecret;

    private final boolean restOnly;
    private final CoinbaseRestClient coinbaseRestClient;
    private volatile CoinbaseUserDataListener userDataListener;

    private volatile Thread accountRefreshThread;
    private volatile boolean isAccountRefreshRunning = false;

    public CoinbaseFastClient(final String apiKey, final String apiSecret, final ExchangeSubscription subscription) {
        this(apiKey, apiSecret, subscription, false);
    }

    public CoinbaseFastClient(final String apiKey, final String apiSecret, final ExchangeSubscription subscription, final boolean restOnly) {
        this.restOnly = restOnly;
        this.apiKey = Objects.requireNonNull(apiKey);
        this.apiSecret = Objects.requireNonNull(apiSecret);
        this.subscription = subscription;
        this.coinbaseRestClient = new CoinbaseRestClient(apiKey, apiSecret, subscription);
    }

    @Override
    public void start() {
        LOGGER.info("Starting Coinbase client");
        if (subscription.isFuturesEnabled()) {
            startFutureClient();
        } else {
            startSpotClient();
        }
    }

    // ======= Internal API =======

    private void startSpotClient() {
        try {
            LOGGER.info("Starting Coinbase Spot Client");

            // Get snapshot via REST
            bootstrapSpotBalanceSnapshot();

            // Start periodic polling using REST
            startPeriodicSpotAccountRefresh();

            if (!restOnly) {
                // Start real-time WebSocket streams for spot
                startUserDataStream();
            }

        } catch (final Exception e) {
            LOGGER.error("Failed to start Coinbase Spot Client", e);
            throw new RuntimeException(e);
        }
    }

    private void startFutureClient() {
        try {
            LOGGER.info("Starting Coinbase Futures Client");

            // Get snapshot via REST
            bootstrapFutureBalanceSnapshot();

            // Start periodic polling using REST
            startPeriodicFutureAccountRefresh();

            if (!restOnly) {
                // Start real-time WebSocket streams for futures
                startUserDataStream();
            }



        } catch (final Exception e) {
            LOGGER.error("Failed to start Coinbase Futures Client", e);
            throw new RuntimeException(e);
        }
    }

    // Spot snapshot and periodic refresh
    private void bootstrapSpotBalanceSnapshot() {
        final String body = coinbaseRestClient.getBalanceSnapshot();
        applyAccountSnapshot(body);
        LOGGER.info("Coinbase spot balance snapshot applied");
    }

    private void startPeriodicSpotAccountRefresh() {
        if (isAccountRefreshRunning) {
            LOGGER.warn("Periodic spot account refresh already running");
            return;
        }

        isAccountRefreshRunning = true;
        accountRefreshThread = Thread.ofVirtual().start(() -> {
            while (isAccountRefreshRunning) {
                try {
                    LOGGER.info("Running periodic Coinbase spot account snapshot refresh...");
                    bootstrapSpotBalanceSnapshot();
                    LOGGER.info("Periodic Coinbase spot account snapshot refresh completed");
                    Thread.sleep(TEN_MINUTES);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final Exception e) {
                    LOGGER.error("Periodic Coinbase spot account snapshot failed: " + e.getMessage());
                    try {
                        Thread.sleep(ONE_MINUTE);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    // Futures snapshot and periodic refresh
    private void bootstrapFutureBalanceSnapshot() {
        final String body = coinbaseRestClient.getFutureBalanceSummary();
        parseFutureBalanceSummary(body);

        // Fetch positions for futures contracts if enabled
        final String openPosition = coinbaseRestClient.getAllPositions();
        updateOpenPosition(openPosition);

        LOGGER.info("Coinbase futures balance snapshot applied");
    }

    private void startPeriodicFutureAccountRefresh() {
        if (isAccountRefreshRunning) {
            LOGGER.warn("Periodic futures account refresh already running");
            return;
        }

        isAccountRefreshRunning = true;
        accountRefreshThread = Thread.ofVirtual().start(() -> {
            while (isAccountRefreshRunning) {
                try {
                    LOGGER.info("Running periodic Coinbase futures account snapshot refresh...");
                    bootstrapFutureBalanceSnapshot();
                    LOGGER.info("Periodic Coinbase futures account snapshot refresh completed");
                    Thread.sleep(TEN_MINUTES);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final Exception e) {
                    LOGGER.error("Periodic Coinbase futures account snapshot failed: " + e.getMessage());
                    try {
                        Thread.sleep(ONE_MINUTE);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping Coinbase client");
        stopPeriodicAccountRefresh();

        if (userDataListener != null) {
            userDataListener.disconnect();
        }
    }

    @Override
    public ExecutionReportMessage sendOrder(final Order order, final boolean futuresEnabled,
                                            final String bestQuoteSymbol,
                                            final String baseSymbol, final int priceScale, final int qtyScale, final double fxRate) {
        if (futuresEnabled) {
            sendFutureOrder(order, bestQuoteSymbol, baseSymbol, priceScale, qtyScale, fxRate);
        } else {
            sendSpotOrder(order);
        }
        return subscription.getExecutionReport(order.getClOrdId());
    }

    @Override
    public boolean cancelOrder(final Order order, final boolean isSpotOrder) {
        // Get Coinbase order ID from order (assuming it's stored in the order object)
        final String orderId = order.getOcoClOrdId() != null ? order.getOcoClOrdId()
                : (order.getOrderId() != 0 ? String.valueOf(order.getOrderId()) : order.getClOrdId());

        if (isSpotOrder) {
            return coinbaseRestClient.cancelSpotOrderRest(order, orderId, order.getClOrdId());
        } else {
            return coinbaseRestClient.cancelFutureOrderRest(order, orderId, order.getClOrdId());
        }
    }

    public boolean transferBalance(final String currency, final String amount, final boolean isFromSpot) {
        return coinbaseRestClient.transferBalance(currency, amount,isFromSpot);
    }

    public String getAllOpenOrders() {
        final String jsonResp;
        if (subscription.isFuturesEnabled()) {
            jsonResp = coinbaseRestClient.getAllOpenFuturesOrders();
        } else {
            jsonResp = coinbaseRestClient.getAllOpenSpotOrders();
        }
        return jsonResp;
    }

    public List<ExternalSymbol> getExchangeInstrumentsFull() {
        LOGGER.debug("Fetching Coinbase exchange instruments (spot and futures)");

        final List<ExternalSymbol> combinedInstruments = new ArrayList<>();
        final long updated = System.currentTimeMillis();
        String cursor = null;

        // Paginate through all products
        do {
            final String json = coinbaseRestClient.getProducts(cursor);
            if (json == null) {
                LOGGER.warn("Coinbase getProducts returned null" + (cursor != null ? " at cursor: " + cursor : ""));
                break;
            }

            // Parse "products" array from response
            final String productsArray = extractJsonValue(json, "products");
            if (productsArray == null) {
                LOGGER.warn("No 'products' array in Coinbase products response");
                break;
            }

            // Parse each product object in the array
            parseProductsPage(productsArray, combinedInstruments, updated);

            // Check pagination for next page
            cursor = null;
            final String paginationObj = extractJsonValue(json, "pagination");
            if (paginationObj != null) {
                final String hasNext = minExtract(paginationObj, "has_next");
                if ("true".equalsIgnoreCase(hasNext)) {
                    final String nextCursor = minExtract(paginationObj, "next_cursor");
                    if (nextCursor != null && !nextCursor.isEmpty()) {
                        cursor = nextCursor;
                        LOGGER.debug("Coinbase products pagination: fetching next page with cursor: " + cursor);
                    }
                }
            }
        } while (cursor != null);

        LOGGER.info("Total Coinbase instruments loaded: " + combinedInstruments.size());
        return combinedInstruments;
    }

    @Override
    public Ticker getTicker(final String base, final String quote, final int instrumentType) {
        // instrumentType 1 = Perps: BTC-PERP-INTX, 2 = Spot: BTC-USDT
        final String productId = (instrumentType == 1) ? base + "-PERP-INTX" : base + "-" + quote;
        final Ticker ticker = coinbaseRestClient.getTicker(productId);
        if (ticker != null) {
            ticker.setInstrumentType(instrumentType);
        }
        return ticker;
    }

    private void parseProductsPage(final String productsArray, final List<ExternalSymbol> instruments, final long updated) {
        int objStart = 0;
        while (objStart < productsArray.length()) {
            objStart = productsArray.indexOf('{', objStart);
            if (objStart < 0) break;

            // Find matching closing brace
            int braceCount = 0;
            int objEnd = objStart;
            for (int i = objStart; i < productsArray.length(); i++) {
                if (productsArray.charAt(i) == '{') {
                    braceCount++;
                } else if (productsArray.charAt(i) == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        objEnd = i;
                        break;
                    }
                }
            }
            if (objEnd <= objStart) break;

            final String productObj = productsArray.substring(objStart, objEnd + 1);

            final String productId = minExtract(productObj, "product_id");
            final String baseCurrencyId = minExtract(productObj, "base_currency_id");
            final String quoteCurrencyId = minExtract(productObj, "quote_currency_id");
            final String status = minExtract(productObj, "status");
            final String tradingDisabled = minExtract(productObj, "trading_disabled");
            final String productType = minExtract(productObj, "product_type");
            final String baseIncrement = minExtract(productObj, "base_increment");
            final String quoteIncrement = minExtract(productObj, "quote_increment");
            final String baseMinSize = minExtract(productObj, "base_min_size");
            final String baseMaxSize = minExtract(productObj, "base_max_size");

            if (productId != null && baseCurrencyId != null && quoteCurrencyId != null) {
                final ExternalSymbol externalSymbol = new ExternalSymbol();
                externalSymbol.setExchange("coinbase");
                externalSymbol.setBase(baseCurrencyId);
                externalSymbol.setQuote(quoteCurrencyId);
                externalSymbol.setSymbol(productId);
                externalSymbol.setPrompt(productId);
                externalSymbol.setFutures("FUTURE".equalsIgnoreCase(productType));
                externalSymbol.setTradable("online".equalsIgnoreCase(status) && !"true".equalsIgnoreCase(tradingDisabled));
                externalSymbol.setPriceScale(incrementToScale(quoteIncrement));
                externalSymbol.setQtyScale(incrementToScale(baseIncrement));
                externalSymbol.setMinimumAmount(parseDoubleSafe(baseMinSize));
                externalSymbol.setMaximumAmount(parseDoubleSafe(baseMaxSize));
                externalSymbol.setAmountStepSize(parseDoubleSafe(baseIncrement));
                externalSymbol.setUpdated(updated);

                // For futures, extract contract_size as multiplierContract
                if (externalSymbol.isFutures()) {
                    final String futureDetails = extractJsonValue(productObj, "future_product_details");
                    if (futureDetails != null) {
                        final String contractSize = minExtract(futureDetails, "contract_size");
                        if (contractSize != null) {
                            externalSymbol.setMultiplierContract((int) parseDoubleSafe(contractSize));
                        }
                    }
                }

                instruments.add(externalSymbol);
            }

            objStart = objEnd + 1;
        }
    }

    /**
     * Converts an increment string (e.g. "0.001") to a decimal scale (e.g. 3).
     * Returns 8 as default if parsing fails.
     */
    private int incrementToScale(final String increment) {
        if (increment == null || increment.isEmpty()) return 8;
        try {
            final int dotIdx = increment.indexOf('.');
            if (dotIdx < 0) return 0;
            // Count digits after the decimal point (trailing zeros matter)
            return increment.length() - dotIdx - 1;
        } catch (final Exception e) {
            return 8;
        }
    }

    // ======= Account Management Methods =======

    private void stopPeriodicAccountRefresh() {
        if (!isAccountRefreshRunning) {
            return;
        }

        isAccountRefreshRunning = false;
        LOGGER.info("Stopping periodic Coinbase account refresh");

        if (accountRefreshThread != null) {
            accountRefreshThread.interrupt();
        }
    }

    private void startUserDataStream() throws Exception {
        LOGGER.info("Starting Coinbase User Data Stream");
        userDataListener = new CoinbaseUserDataListener(apiKey, apiSecret, subscription);
        userDataListener.connect();
    }

    // ======= Order Sending Methods =======

    private Order sendSpotOrder(final Order order) {
        // Determine order type
        final String orderType = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT
                ? "LIMIT"
                : "MARKET";
        final String side = order.getSide().toString(); // BUY or SELL
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        // Convert symbol to Coinbase product_id format (e.g., BTC-USD)
        // final String productId = convertSymbolToProductId(order.getSymbol());

        subscription.cacheNewOrder(order);

        //  LOGGER.debug("Sending Coinbase SPOT order via REST for product: " + productId);
        coinbaseRestClient.sendSpotOrderREST(order, order.getSymbol(), side, orderType, qtyStr, priceStr, order.getClOrdId());

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
        final String orderId = order.getOcoClOrdId() != null ? order.getOcoClOrdId()
                : (order.getOrderId() != 0 ? String.valueOf(order.getOrderId()) : order.getClOrdId());
        for (int i = 0; i < 100; i++) {
            coinbaseRestClient.querySpotOrderStatus(order, orderId, order.getClOrdId(), i);
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

        LOGGER.warn("SPOT: Could not process the order for clientOrderId: " + order.getClOrdId());
        return order;
    }

    private Order sendFutureOrder(final Order order, final String bestQuoteSymbol, final String baseSymbol,
                                  final int priceScale, final int qtyScale, final double fxRate) {
        // Determine order type
        final String orderType = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT
                ? "LIMIT"
                : "MARKET";
        final String side = order.getSide().toString(); // BUY or SELL
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        // Convert symbol to Coinbase futures product_id format (e.g., BTC-PERP-INTX)
        final String productId = convertSymbolToFuturesProductId(order.getSymbol());

        subscription.cacheNewOrder(order);

        LOGGER.debug("Sending Coinbase FUTURE order via REST for product: " + productId);
        coinbaseRestClient.sendFutureOrderREST(order, order.getSymbol(), side, orderType, qtyStr, priceStr, order.getClOrdId());

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
        final String orderId = order.getOcoClOrdId() != null ? order.getOcoClOrdId()
                : (order.getOrderId() != 0 ? String.valueOf(order.getOrderId()) : order.getClOrdId());
        for (int i = 0; i < 100; i++) {
            coinbaseRestClient.queryFutureOrderStatus(order, orderId, order.getClOrdId(), i);
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

        LOGGER.warn("FUTURE: Could not process the order for clientOrderId: " + order.getClOrdId());
        return order;
    }

    // ======= Helper Methods =======

    private void updateOpenPosition(final String positionInfo) {
        if (positionInfo == null) {
            LOGGER.warn("Coinbase position info response is null");
            return;
        }

        // Parse Coinbase position info JSON response
        // Expected format: {"positions":[{...position objects...}]}
        final int positionsStart = positionInfo.indexOf("\"positions\":");
        if (positionsStart < 0) {
            LOGGER.warn("No \"positions\" section in Coinbase position response");
            return;
        }

        // Find the positions array
        int arrayStart = -1;
        for (int i = positionsStart + 12; i < positionInfo.length(); i++) {
            final char c = positionInfo.charAt(i);
            if (c == '[') {
                arrayStart = i;
                break;
            } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t' && c != ':') {
                break;
            }
        }

        if (arrayStart < 0) {
            LOGGER.warn("No array found in Coinbase position response data");
            return;
        }

        // Find the matching closing bracket for the positions array
        int bracketCount = 0;
        int arrayEnd = arrayStart;
        for (int i = arrayStart; i < positionInfo.length(); i++) {
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

        if (arrayEnd <= arrayStart) {
            LOGGER.warn("Cannot find closing ] for position data array");
            return;
        }

        final String dataArray = positionInfo.substring(arrayStart + 1, arrayEnd);
        LOGGER.debug(
                "Position data array content: " + dataArray.substring(0, Math.min(200, dataArray.length())) + "...");

        // Parse each position object in the data array
        int objStart = 0;
        int positionCount = 0;
        while (objStart < dataArray.length()) {
            objStart = dataArray.indexOf('{', objStart);
            if (objStart < 0)
                break;

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

            if (objEnd <= objStart)
                break;

            final String positionObj = dataArray.substring(objStart, objEnd + 1);
            LOGGER.debug("Processing position object of length: " + positionObj.length());

            // Parse position data using Coinbase JSON structure
            final String productId = minExtract(positionObj, "product_id");
            final String symbol = minExtract(positionObj, "symbol");
            final String netSize = minExtract(positionObj, "net_size");
            final String markPriceObj = extractJsonValue(positionObj, "mark_price");
            final String markPrice = extractJsonValue(markPriceObj, "value");

            if (productId != null && netSize != null) {
                final double size = parseDoubleSafe(netSize);
                final double markPriceDouble = parseDoubleSafe(markPrice);

                if (size != 0.0) {
                    subscription.updatePosition(symbol, size, markPriceDouble);
                    positionCount++;
                    LOGGER.debug("Updated Coinbase position: " + productId + " netSize=" + netSize +
                            " markPrice=" + markPrice);
                }
            }

            objStart = objEnd + 1;
        }
        LOGGER.info("Updated " + positionCount + " Coinbase positions");
    }

    private void applyAccountSnapshot(final String json) {
        if (json == null) {
            LOGGER.warn("Coinbase account snapshot JSON is null");
            return;
        }

        // Coinbase wallet response format: {"accounts":[...]}
        final int accountsIdx = json.indexOf("\"accounts\":");
        if (accountsIdx < 0) {
            LOGGER.warn("No accounts field in Coinbase account snapshot");
            return;
        }

        int arrayStart = -1;
        for (int i = accountsIdx + 11; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == '[') {
                arrayStart = i;
                break;
            } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t' && c != ':') {
                break;
            }
        }

        if (arrayStart < 0)
            return;

        // Find the matching closing bracket for the accounts array
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

        if (arrayEnd <= arrayStart)
            return;

        final String dataArray = json.substring(arrayStart + 1, arrayEnd);

        // Parse each account object in the array
        int objStart = 0;
        int balanceCount = 0;
        while (objStart < dataArray.length()) {
            objStart = dataArray.indexOf('{', objStart);
            if (objStart < 0)
                break;

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

            if (objEnd <= objStart)
                break;

            final String obj = dataArray.substring(objStart, objEnd + 1);
            final String currency = minExtract(obj, "currency");

            // Extract available_balance nested object
            final String availableBalanceObj = extractJsonValue(obj, "available_balance");
            final String availableStr = availableBalanceObj != null
                    ? minExtract(availableBalanceObj, "value")
                    : "0";
            final double available = parseDoubleSafe(availableStr);

            if (currency != null) {
                subscription.updateBalance(currency, available);
                balanceCount++;
                LOGGER.debug("Updated Coinbase balance: " + currency + " = " + available);
            }

            objStart = objEnd + 1;
        }
        LOGGER.info("Updated " + balanceCount + " Coinbase balances");
    }

    private void parseFutureBalanceSummary(final String json) {
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            // Find "portfolio_balances" array
            final int pbIdx = json.indexOf("\"portfolio_balances\"");
            if (pbIdx < 0) {
                LOGGER.warn("No portfolio_balances found in response");
                return;
            }
            int arrayStart = json.indexOf('[', pbIdx);
            if (arrayStart < 0) return;
            int bracketCount = 0, arrayEnd = arrayStart;
            for (int i = arrayStart; i < json.length(); i++) {
                if (json.charAt(i) == '[') bracketCount++;
                else if (json.charAt(i) == ']') {
                    bracketCount--;
                    if (bracketCount == 0) {
                        arrayEnd = i;
                        break;
                    }
                }
            }
            if (arrayEnd <= arrayStart) return;
            final String pbArray = json.substring(arrayStart + 1, arrayEnd);

            // For each portfolio object
            int pIdx = 0;
            while (pIdx < pbArray.length()) {
                int objStart = pbArray.indexOf('{', pIdx);
                if (objStart < 0) break;
                int braceCount = 0, objEnd = objStart;
                for (int i = objStart; i < pbArray.length(); i++) {
                    if (pbArray.charAt(i) == '{') braceCount++;
                    else if (pbArray.charAt(i) == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }
                if (objEnd <= objStart) break;
                final String portfolioObj = pbArray.substring(objStart, objEnd + 1);

                // Find "balances" array in portfolioObj
                int balIdx = portfolioObj.indexOf("\"balances\"");
                if (balIdx >= 0) {
                    int balArrStart = portfolioObj.indexOf('[', balIdx);
                    if (balArrStart >= 0) {
                        int balBracketCount = 0, balArrEnd = balArrStart;
                        for (int i = balArrStart; i < portfolioObj.length(); i++) {
                            if (portfolioObj.charAt(i) == '[') balBracketCount++;
                            else if (portfolioObj.charAt(i) == ']') {
                                balBracketCount--;
                                if (balBracketCount == 0) {
                                    balArrEnd = i;
                                    break;
                                }
                            }
                        }
                        if (balArrEnd > balArrStart) {
                            final String balancesArray = portfolioObj.substring(balArrStart + 1, balArrEnd);
                            // For each balance object
                            int bIdx = 0;
                            while (bIdx < balancesArray.length()) {
                                int balObjStart = balancesArray.indexOf('{', bIdx);
                                if (balObjStart < 0) break;
                                int balBraceCount = 0, balObjEnd = balObjStart;
                                for (int i = balObjStart; i < balancesArray.length(); i++) {
                                    if (balancesArray.charAt(i) == '{') balBraceCount++;
                                    else if (balancesArray.charAt(i) == '}') {
                                        balBraceCount--;
                                        if (balBraceCount == 0) {
                                            balObjEnd = i;
                                            break;
                                        }
                                    }
                                }
                                if (balObjEnd <= balObjStart) break;
                                final String balanceObj = balancesArray.substring(balObjStart, balObjEnd + 1);

                                // Extract asset_name and quantity
                                final String assetObj = extractJsonValue(balanceObj, "asset");
                                final String assetName = assetObj != null ? minExtract(assetObj, "asset_name") : null;
                                final String quantityStr = minExtract(balanceObj, "quantity");
                                final double quantity = parseDoubleSafe(quantityStr);

                                if (assetName != null) {
                                    subscription.updateBalance(assetName, quantity);
                                    LOGGER.debug("Updated Coinbase futures balance: " + assetName + " = " + quantity);
                                }
                                bIdx = balObjEnd + 1;
                            }
                        }
                    }
                }
                pIdx = objEnd + 1;
            }
        } catch (final Exception e) {
            LOGGER.error("Error parsing futures balance summary: " + e.getMessage());
        }
    }
    private String convertSymbolToProductId(final String symbol) {
        if (symbol == null) {
            return null;
        }

        // Common quote currencies
        if (symbol.endsWith("-USD")) {
            return symbol; // Already in correct format
        } else if (symbol.endsWith("USDT")) {
            return symbol.substring(0, symbol.length() - 4) + "-USD";
        } else if (symbol.endsWith("USDC")) {
            return symbol.substring(0, symbol.length() - 4) + "-USDC";
        } else if (symbol.endsWith("USD")) {
            return symbol.substring(0, symbol.length() - 3) + "-USD";
        }

        // Default: assume last part is quote currency
        return symbol;
    }


    private String convertSymbolToFuturesProductId(final String symbol) {
        if (symbol == null) {
            return null;
        }

        // Extract base currency
        final String baseCurrency;
        if (symbol.endsWith("USDT") || symbol.endsWith("USDC")) {
            baseCurrency = symbol.substring(0, symbol.length() - 4);
        } else if (symbol.endsWith("USD")) {
            baseCurrency = symbol.substring(0, symbol.length() - 3);
        } else {
            baseCurrency = symbol;
        }

        // Coinbase perpetual futures format
        return baseCurrency + "-PERP-INTX";
    }

    private String extractJsonValue(final String json, final String key) {
        if (json == null || key == null) {
            LOGGER.debug("extractJsonValue called with null json or key");
            return null;
        }

        final String searchPattern = "\"" + key + "\"";
        final int keyStart = json.indexOf(searchPattern);
        if (keyStart < 0) {
            LOGGER.debug("Key '" + key + "' not found in JSON");
            return null;
        }

        int colonPos = -1;
        for (int i = keyStart + searchPattern.length(); i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == ':') {
                colonPos = i;
                break;
            } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                LOGGER.debug("Invalid character before colon: " + c);
                return null;
            }
        }

        if (colonPos < 0) {
            LOGGER.debug("No colon found after key '" + key + "'");
            return null;
        }

        int valueStart = -1;
        for (int i = colonPos + 1; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                valueStart = i;
                break;
            }
        }

        if (valueStart < 0) {
            LOGGER.debug("No value found after colon for key '" + key + "'");
            return null;
        }

        final char firstChar = json.charAt(valueStart);

        if (firstChar == '{') {
            return extractJsonObject(json, valueStart);
        } else if (firstChar == '[') {
            return extractJsonArray(json, valueStart);
        } else if (firstChar == '"') {
            return extractJsonString(json, valueStart);
        } else {
            return extractJsonPrimitive(json, valueStart);
        }
    }

    private String extractJsonObject(final String json, final int start) {
        int braceDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = start; i < json.length(); i++) {
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
                if (c == '{') {
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private String extractJsonArray(final String json, final int start) {
        int bracketDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = start; i < json.length(); i++) {
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
                    bracketDepth--;
                    if (bracketDepth == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private String extractJsonString(final String json, final int start) {
        boolean escapeNext = false;

        for (int i = start + 1; i < json.length(); i++) {
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
                return json.substring(start + 1, i);
            }
        }
        return null;
    }

    private String extractJsonPrimitive(final String json, final int start) {
        for (int i = start; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']' || c == '\n' || c == '\r') {
                return json.substring(start, i).trim();
            }
        }
        return json.substring(start).trim();
    }
}
