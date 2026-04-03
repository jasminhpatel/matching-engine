package com.solfini.matchengine.liquidity.direct.aiGenerated;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;

import java.util.List;
import java.util.Objects;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.deribit.DeribitRestClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.deribit.DeribitTradeListener;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.deribit.DeribitUserDataListener;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.StringUtil;

public final class DeribitFastClient implements ExternalExchangeClient {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(DeribitFastClient.class);
    private static final long TEN_MINUTES = 600_000;
    private static final long ONE_MINUTE = 60_000;

    private final ExchangeSubscription subscription;
    private final String clientId;
    private final String clientSecret;

    private final boolean restOnly;
    private final DeribitRestClient deribitRestClient;
    private final DeribitTradeListener tradeListener;
    private volatile DeribitUserDataListener userDataListener;
    private volatile Thread accountRefreshThread;
    private volatile boolean isAccountRefreshRunning = false;

    public DeribitFastClient(final String clientId, final String clientSecret, final ExchangeSubscription subscription) {
        this(clientId, clientSecret, subscription, false);
    }

    public DeribitFastClient(final String clientId, final String clientSecret, final ExchangeSubscription subscription, final boolean restOnly) {
        this.restOnly = restOnly;
        this.clientId = Objects.requireNonNull(clientId);
        this.clientSecret = Objects.requireNonNull(clientSecret);
        this.subscription = subscription;
        this.deribitRestClient = new DeribitRestClient(clientId, clientSecret, subscription);
        this.tradeListener = restOnly ? null : new DeribitTradeListener(clientId, clientSecret, subscription);
    }

    @Override
    public void start() {
        try {
            // Start periodic REST API polling for account state synchronization
            startPeriodicAccountRefresh();


            if(!restOnly) {
                // Initialize real-time data streams
                startUserDataStream();
                startTradeDataStream();
            }

            LOGGER.info("DeribitFastClient started successfully");
        } catch (final Exception e) {
            throw new RuntimeException("Failed to start DeribitFastClient", e);
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

        LOGGER.info("DeribitFastClient stopped");
    }

    @Override
    public ExecutionReportMessage sendOrder(final Order order, final boolean futuresEnabled, final String bestQuoteSymbol,
                                            final String baseSymbol, final int priceScale, final int qtyScale, final double fxRate) {
        if (!futuresEnabled) {
            LOGGER.debug("Sending spot order for symbol: " + bestQuoteSymbol);
            sendSpotOrder(order, bestQuoteSymbol, baseSymbol, priceScale, qtyScale, fxRate);
        } else {
            LOGGER.debug("Sending derivative order for symbol: " + bestQuoteSymbol);
            sendDerivativeOrder(order, bestQuoteSymbol, baseSymbol, priceScale, qtyScale, fxRate);
        }

        return subscription.getExecutionReport(order.getClOrdId());
    }

    @Override
    public boolean cancelOrder(Order order, boolean isSpotOrder) {
        return deribitRestClient.cancelOrderByLabel(order.getClOrdId(), order);
    }

    @Override
    public List<ExternalSymbol> getExchangeInstrumentsFull() {
        return deribitRestClient.getExchangeInstrumentsFull();
    }

    @Override
    public Ticker getTicker(final String base, final String quote, final int instrumentType) {
        // instrumentType 1 = Perps, 2 = Spot
        final String instrumentName = (instrumentType == 1) ? base + "-PERPETUAL" : base + "-" + quote;
        final Ticker ticker = deribitRestClient.getTicker(instrumentName);
        if (ticker != null) {
            ticker.setInstrumentType(instrumentType);
        }
        return ticker;
    }

    public String getAllOpenOrders() {
        final String jsonResp;
        jsonResp = deribitRestClient.getAllOpenOrders();
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
                    LOGGER.info("Running periodic Deribit account snapshot refresh...");
                    bootstrapBalanceSnapshot();
                    LOGGER.info("Periodic Deribit account snapshot refresh completed");
                    Thread.sleep(TEN_MINUTES);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final Exception e) {
                    LOGGER.error("Periodic Deribit account snapshot failed: " + e.getMessage());
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
        LOGGER.info("Stopping periodic Deribit account refresh");

        if (accountRefreshThread != null) {
            accountRefreshThread.interrupt();
        }
    }

    private void bootstrapBalanceSnapshot() {
        // Get account summary
        final String balanceInfo = deribitRestClient.getBalanceSnapshot();
        applyAccountSnapshot(balanceInfo);

        // Fetch positions
        final String positionInfo = deribitRestClient.getPositionInfo();
        updateOpenPosition(positionInfo);

        LOGGER.info("Deribit bootstrap snapshot applied");
    }

    /**
     * Updates open positions from Deribit position info JSON response
     * @param positionInfo JSON response containing position data
     */
    private void updateOpenPosition(final String positionInfo) {
        if (positionInfo == null) {
            LOGGER.warn("Deribit position info response is null");
            return;
        }

        try {
            LOGGER.debug("Full position info JSON length: " + positionInfo.length());

            // Direct parsing: find result array in the full JSON response
            String resultJson = null;

            final int resultStart = positionInfo.indexOf("\"result\"");
            if (resultStart != -1) {
                final int valueStart = positionInfo.indexOf(":", resultStart) + 1;
                if (valueStart > 0) {
                    // Find the opening bracket of the array
                    int arrayStart = positionInfo.indexOf("[", valueStart);
                    if (arrayStart != -1) {
                        // Find the matching closing bracket
                        int bracketCount = 0;
                        int arrayEnd = -1;
                        boolean inString = false;
                        boolean escaped = false;

                        for (int i = arrayStart; i < positionInfo.length(); i++) {
                            char c = positionInfo.charAt(i);

                            if (escaped) {
                                escaped = false;
                                continue;
                            }

                            if (c == '\\') {
                                escaped = true;
                                continue;
                            }

                            if (c == '"') {
                                inString = !inString;
                                continue;
                            }

                            if (!inString) {
                                if (c == '[') {
                                    bracketCount++;
                                } else if (c == ']') {
                                    bracketCount--;
                                    if (bracketCount == 0) {
                                        arrayEnd = i;
                                        break;
                                    }
                                }
                            }
                        }

                        if (arrayEnd > arrayStart) {
                            resultJson = positionInfo.substring(arrayStart, arrayEnd + 1);
                            LOGGER.debug("Direct extracted result JSON length: " + resultJson.length());
                        }
                    }
                }
            }

            if (resultJson == null) {
                LOGGER.warn("No result section in Deribit position response");
                return;
            }

            // Deribit returns an array of positions directly in result
            if (!resultJson.trim().startsWith("[")) {
                LOGGER.warn("Expected array in Deribit position result");
                return;
            }

            // Parse each position object in the result array
            final String arrayContent = resultJson.trim().substring(1, resultJson.trim().length() - 1);
            int objStart = 0;
            while (objStart < arrayContent.length()) {
                objStart = arrayContent.indexOf('{', objStart);
                if (objStart < 0) break;

                // Find matching closing brace
                int braceCount = 0;
                int objEnd = objStart;
                boolean inString = false;
                boolean escaped = false;

                for (int i = objStart; i < arrayContent.length(); i++) {
                    char c = arrayContent.charAt(i);

                    if (escaped) {
                        escaped = false;
                        continue;
                    }

                    if (c == '\\') {
                        escaped = true;
                        continue;
                    }

                    if (c == '"') {
                        inString = !inString;
                        continue;
                    }

                    if (!inString) {
                        if (c == '{') {
                            braceCount++;
                        } else if (c == '}') {
                            braceCount--;
                            if (braceCount == 0) {
                                objEnd = i;
                                break;
                            }
                        }
                    }
                }

                if (objEnd <= objStart) break;

                final String positionObj = arrayContent.substring(objStart, objEnd + 1);
                LOGGER.debug("Processing Deribit position object of length: " + positionObj.length());

                // Parse Deribit position data
                final String instrumentName = minExtract(positionObj, "instrument_name");
                final double size = parseDoubleSafe(minExtract(positionObj, "size"));
                final String direction = minExtract(positionObj, "direction");
                final double averagePrice = parseDoubleSafe(minExtract(positionObj, "average_price"));
                final double floatingProfitLoss = parseDoubleSafe(minExtract(positionObj, "floating_profit_loss"));
                final double leverage = parseDoubleSafe(minExtract(positionObj, "leverage"));

                if (instrumentName != null && size != 0.0) {
                    subscription.updatePosition(instrumentName, size);
                    LOGGER.debug("Updated Deribit position: " + instrumentName + " direction=" + direction +
                            " size=" + size + " avgPrice=" + averagePrice + " pnl=" + floatingProfitLoss +
                            " leverage=" + leverage);
                }

                objStart = objEnd + 1;
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to parse Deribit position info: " + e.getMessage());
        }
    }
    private void startUserDataStream() throws Exception {
        userDataListener = new DeribitUserDataListener(clientId, clientSecret, subscription);
        userDataListener.connect();
    }

    private void startTradeDataStream() throws Exception {
        tradeListener.connect();
    }

    private Order sendSpotOrder(final Order order, final String bestQuoteSymbol, final String baseSymbol,
                                final int priceScale, final int qtyScale, final double fxRate) {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "limit" : "market";
        final String timeInForce = convertTimeInForce(order.getTimeInForce());
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order);

        if (tradeListener != null && tradeListener.isConnected()) {
            LOGGER.debug("Sending Deribit spot order via WebSocket for symbol: " + order.getSymbol());
            tradeListener.placeSpotOrder(order.getSymbol(), order.getSide().toString(),
                    type, timeInForce, qtyStr, priceStr, order.getClOrdId());
        } else {
            LOGGER.debug("Sending Deribit spot order via REST for symbol: " + order.getSymbol());
            deribitRestClient.sendSpotOrderREST(order, order.getSymbol(), order.getSide().toString(),
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
            deribitRestClient.queryOrderStatusByLabel(order);
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

    private Order sendDerivativeOrder(final Order order, final String bestQuoteSymbol, final String baseSymbol,
                                      final int priceScale, final int qtyScale, final double fxRate) {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "limit" : "market";
        final String timeInForce = convertTimeInForce(order.getTimeInForce());
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order);

        if (tradeListener != null && tradeListener.isConnected()) {
            LOGGER.debug("Sending Deribit derivative order via WebSocket for symbol: " + order.getSymbol());
            tradeListener.placeFuturesOrder(order.getSymbol(), order.getSide().toString(),
                    type, timeInForce, qtyStr, priceStr, order.getClOrdId());
        } else {
            LOGGER.debug("Sending Deribit derivative order via REST for symbol: " + order.getSymbol());
            deribitRestClient.sendFutureOrderREST(order, order.getSymbol(), order.getSide().toString(),
                    type, timeInForce, qtyStr, priceStr, order.getClOrdId());
        }

        // Poll order cache for status updates (WebSocket response) — skip in REST-only mode
        if (!restOnly) {
            LOGGER.debug("DERIVATIVE: Start of Periodic check on Order Cache for status update.");
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
        LOGGER.debug("DERIVATIVE: Start of Periodic REST API check for status update.");
        for (int i = 0; i < 100; i++) {
            deribitRestClient.queryOrderStatusByLabel(order);
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

        LOGGER.warn("DERIVATIVE: Could not process the order for clientOrderId: " + order.getClOrdId());
        return order;
    }

    private String convertTimeInForce(final TimeInForce timeInForce) {
        if (timeInForce == null) return "gtc";

        switch (timeInForce) {
            case FILL_OR_KILL:
                return "fok";
            case IMMEDIATE_OR_CANCEL:
                return "ioc";
            case GOOD_TILL_CANCEL:
            default:
                return "gtc";
        }
    }

    private void applyAccountSnapshot(final String json) {
        if (json == null) return;

        try {
            LOGGER.debug("Full account snapshot JSON length: " + json.length());

            // Skip minExtract for result and go directly to summaries parsing
            // since minExtract is truncating the large JSON response
            String summariesJson = null;

            // Direct parsing: find summaries array in the full JSON response
            final int summariesStart = json.indexOf("\"summaries\"");
            if (summariesStart != -1) {
                final int valueStart = json.indexOf(":", summariesStart) + 1;
                if (valueStart > 0) {
                    // Find the opening bracket of the array
                    int arrayStart = json.indexOf("[", valueStart);
                    if (arrayStart != -1) {
                        // Find the matching closing bracket
                        int bracketCount = 0;
                        int arrayEnd = -1;
                        boolean inString = false;
                        boolean escaped = false;

                        for (int i = arrayStart; i < json.length(); i++) {
                            char c = json.charAt(i);

                            if (escaped) {
                                escaped = false;
                                continue;
                            }

                            if (c == '\\') {
                                escaped = true;
                                continue;
                            }

                            if (c == '"') {
                                inString = !inString;
                                continue;
                            }

                            if (!inString) {
                                if (c == '[') {
                                    bracketCount++;
                                } else if (c == ']') {
                                    bracketCount--;
                                    if (bracketCount == 0) {
                                        arrayEnd = i;
                                        break;
                                    }
                                }
                            }
                        }

                        if (arrayEnd > arrayStart) {
                            summariesJson = json.substring(arrayStart, arrayEnd + 1);
                            LOGGER.debug("Direct extracted summaries JSON length: " + summariesJson.length());
                        }
                    }
                }
            }

            if (summariesJson == null) {
                LOGGER.warn("No summaries section found in Deribit account response");
                return;
            }

            LOGGER.debug("Processing Deribit summaries of length: " + summariesJson.length());

            // Parse the summaries array
            if (!summariesJson.trim().startsWith("[")) {
                LOGGER.warn("Expected array in Deribit summaries");
                return;
            }

            // Remove array brackets and process each summary object
            final String arrayContent = summariesJson.trim().substring(1, summariesJson.trim().length() - 1);
            int objStart = 0;

            while (objStart < arrayContent.length()) {
                objStart = arrayContent.indexOf('{', objStart);
                if (objStart < 0) break;

                // Find matching closing brace
                int braceCount = 0;
                int objEnd = objStart;
                boolean inString = false;
                boolean escaped = false;

                for (int i = objStart; i < arrayContent.length(); i++) {
                    char c = arrayContent.charAt(i);

                    if (escaped) {
                        escaped = false;
                        continue;
                    }

                    if (c == '\\') {
                        escaped = true;
                        continue;
                    }

                    if (c == '"') {
                        inString = !inString;
                        continue;
                    }

                    if (!inString) {
                        if (c == '{') {
                            braceCount++;
                        } else if (c == '}') {
                            braceCount--;
                            if (braceCount == 0) {
                                objEnd = i;
                                break;
                            }
                        }
                    }
                }

                if (objEnd <= objStart) break;

                final String summaryObj = arrayContent.substring(objStart, objEnd + 1);
                processCurrencySummary(summaryObj);

                objStart = objEnd + 1;
            }

        } catch (final Exception e) {
            LOGGER.error("Failed to parse Deribit account snapshot: " + e.getMessage());
        }
    }

    /**
     * Process a single currency summary object from the summaries array
     */
    private void processCurrencySummary(final String summaryObj) {
        try {
            // Extract currency from the summary object
            final String currencyCode = minExtract(summaryObj, "currency");
            if (currencyCode == null) {
                LOGGER.warn("No currency found in summary object");
                return;
            }

            final String cleanCurrency = currencyCode.replace("\"", "");

            // Parse Deribit account summary fields
            final double balance = parseDoubleSafe(minExtract(summaryObj, "balance"));
            final double equity = parseDoubleSafe(minExtract(summaryObj, "equity"));
            final double availableFunds = parseDoubleSafe(minExtract(summaryObj, "available_funds"));
            final double availableWithdrawalFunds = parseDoubleSafe(minExtract(summaryObj, "available_withdrawal_funds"));
            final double maintenanceMargin = parseDoubleSafe(minExtract(summaryObj, "maintenance_margin"));
            final double initialMargin = parseDoubleSafe(minExtract(summaryObj, "initial_margin"));
            final double marginBalance = parseDoubleSafe(minExtract(summaryObj, "margin_balance"));
            final double totalPl = parseDoubleSafe(minExtract(summaryObj, "total_pl"));
            final double sessionUpl = parseDoubleSafe(minExtract(summaryObj, "session_upl"));

            // Use available_funds as the primary balance, fallback to balance if available_funds is 0
            double effectiveBalance = availableFunds > 0 ? availableFunds : balance;

            if (effectiveBalance >= 0) { // Allow zero balances
                subscription.updateBalance(cleanCurrency, effectiveBalance);
                LOGGER.debug("Updated Deribit balance for " + cleanCurrency + ": available=" + availableFunds +
                        ", balance=" + balance + ", equity=" + equity + ", margin=" + marginBalance +
                        ", totalPL=" + totalPl + ", sessionUPL=" + sessionUpl);
            } else {
                LOGGER.debug("Skipping negative balance for " + cleanCurrency + ": " + effectiveBalance);
            }

        } catch (final Exception e) {
            LOGGER.error("Failed to process currency summary: " + e.getMessage());
        }
    }
}
