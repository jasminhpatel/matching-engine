package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.liquidity.DelistedSymbolCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc.MexcRestClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc.MexcSpotUserDataListener;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.StringUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;

public final class MexcFastClient implements ExternalExchangeClient {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(MexcFastClient.class);

    // Time Constants
    private static final long TEN_MINUTES = 600_000;
    private static final long ONE_MINUTE = 60_000;

    private static final String TIME_IN_FORCE_GTC = "GTC";
    private static final String TIME_IN_FORCE_FOK = "FOK";
    private static final String SIDE_BUY = "BUY";
    private static final String SIDE_SELL = "SELL";
    private static final String TYPE_LIMIT = "LIMIT";

    // Core subscription and configuration objects - never reassigned after construction
    private final ExchangeSubscription subscription;
    private final String apiKey;
    private final String secretKey;
    private final boolean restOnly;
    private final MexcRestClient mexcRestClient;

    // WebSocket connections and thread management - can be reassigned during reconnections
    private volatile Thread spotAccountRefreshThread;
    private volatile boolean isAccountRefreshRunning = false;
    private volatile boolean isFutureAccountRefreshRunning = false;

    // WebSocket connections - use appropriate listener based on mode
    private volatile MexcSpotUserDataListener spotUserDataListener;

    // ======= Constructor =======
    /**
     * Initializes MEXC client with API credentials and subscription management
     * @param apiKey MEXC API key for authentication
     * @param apiSecret MEXC API secret for HMAC signature generation
     * @param subscription Liquidity subscription manager for order and balance tracking
     */
    public MexcFastClient(final String apiKey, final String apiSecret, final ExchangeSubscription subscription) {
        this(apiKey, apiSecret, subscription, false);
    }

    public MexcFastClient(final String apiKey, final String apiSecret, final ExchangeSubscription subscription, final boolean restOnly) {
        this.restOnly = restOnly;
        this.apiKey = Objects.requireNonNull(apiKey);
        this.subscription = subscription;
        this.secretKey = apiSecret;
        this.mexcRestClient = new MexcRestClient(apiKey, secretKey.getBytes(), subscription);
    }

    /**
     * Initializes user data WebSocket stream for real-time account updates
     * Note: MEXC futures doesn't support private WebSocket streams
     */
    private void startUserDataStream() {
        try {

            LOGGER.info("Attempting to connect to MEXC SPOT WebSocket...");

            final MexcSpotUserDataListener spotUserDataListener = new MexcSpotUserDataListener(mexcRestClient,subscription);
            spotUserDataListener.connect();
            
            // Wait for connection to establish
            Thread.sleep(2000);
            
            if (spotUserDataListener.isConnected()) {
                LOGGER.info("MEXC SPOT UserData WebSocket connected successfully");
            } else {
                LOGGER.warn("MEXC SPOT UserData WebSocket connection may not be fully established");
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to start MEXC user data stream", e);
            
            // Log specific error details for debugging
            if (e.getMessage().contains("404")) {
                LOGGER.error("MEXC WebSocket endpoint not found - may need to use REST API only");
            } else if (e.getMessage().contains("403") || e.getMessage().contains("401")) {
                LOGGER.error("MEXC WebSocket authentication failed - check API credentials");
            }
            
            // Don't throw exception to allow REST API fallback
            LOGGER.warn("Continuing without WebSocket - will rely on REST API polling");
        }
    }

    @Override
    public void start() {
        // first, so a failing account login below does not stop it; delisted symbols now, then every 10 minutes
        DelistedSymbolCache.start(subscription);
        try {
            LOGGER.info("Starting MEXC SPOT client with WebSocket + REST API");
            startSpotClient();

        if (!restOnly) {
            // Initialize real-time data streams (spot only)
            startUserDataStream();
        }
        } catch (final Exception e) {
            LOGGER.error("Failed to start MEXC client", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        stopPeriodicAccountRefresh();
        ///stopPeriodicFutureAccountRefresh();
        
        // Disconnect appropriate listener
        if (spotUserDataListener != null) {
            spotUserDataListener.disconnect();
        }
    }

    @Override
    public ExecutionReportMessage sendOrder(final Order order, final boolean futuresEnabled, final String bestQuoteSymbol,
                                            final String baseSymbol, final int priceScale, final int qtyScale, final double fxRate) {
            final Order rc = sendSpotOrder(order);
            return subscription.getExecutionReport(order.getClOrdId());
    }

    @Override
    public boolean cancelOrder(Order order, boolean isSpotOrder) {
        boolean result = false;
        if (isSpotOrder) {
            result = mexcRestClient.cancelSpotOrderRest(order, order.getSymbol(), order.getClOrdId());
        } 
        return result;
    }

  @Override
  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    return mexcRestClient.getExchangeInstrumentsFull();
  }

  @Override
  public List<DelistedSymbol> getDelistedSymbols() {
    // this client trades MEXC spot only
    return subscription.isFuturesEnabled() ? Collections.emptyList() : mexcRestClient.getSpotDelistedSymbols();
  }

    @Override
    public Ticker getTicker(final String base, final String quote, final int instrumentType) {
        final String symbol = base + quote;
        final Ticker ticker = mexcRestClient.getTicker(symbol);
        if (ticker != null) {
            ticker.setInstrumentType(instrumentType);
        }
        return ticker;
    }

/*    private boolean transferBalanceFromSpotToFutures(final String asset, final String amount) {
        LOGGER.info("Transferring balance from Spot to Futures: asset=" + asset + " amount=" + amount);
        return mexcRestClient.transferSpotToFutures(asset, amount);
    }

    private boolean transferBalanceFromFuturesToSpot(final String asset, final String amount) {
        LOGGER.info("Transferring balance from Futures to Spot: asset=" + asset + " amount=" + amount);
        return mexcRestClient.transferFuturesToSpot(asset, amount);
    }*/

/*
    public boolean transferBalance(final String asset, final String amount, final boolean fromSpotToFutures) {
        if (fromSpotToFutures) {
            return transferBalanceFromSpotToFutures(asset, amount);
        } else {
            return transferBalanceFromFuturesToSpot(asset, amount);
        }
    }
*/

    // ======= Internal API ======= //
    /**
     * Starts spot client with periodic REST API polling for account state synchronization
     */
    private void startSpotClient() {
        try {
            LOGGER.debug("Starting MEXC spot client initialization");
            bootstrapSpotBalanceSnapshot();
            startPeriodicAccountRefresh();
            LOGGER.info("MEXC spot client initialized successfully");
        } catch (final Exception e) {
            LOGGER.error("Failed to start MEXC spot client", e);
            throw new RuntimeException(e);
        }
    }

/*
    */
/**
     * Starts futures client with periodic REST API polling for account state synchronization
     *//*

    private void startFutureClient() {
        startPeriodicFutureAccountRefresh();
    }
*/

    /**
     * Starts periodic account balance and position refresh using REST API
     * Runs every 10 minutes to ensure data consistency with exchange
     */
    private void startPeriodicAccountRefresh() {
        if (isAccountRefreshRunning) {
            LOGGER.warn("Periodic spot account refresh already running");
            return;
        }

        LOGGER.info("Starting periodic spot account refresh (every " + (TEN_MINUTES / 60000) + " minutes)");
        isAccountRefreshRunning = true;
        spotAccountRefreshThread = Thread.ofVirtual().start(() -> {
            while (isAccountRefreshRunning) {
                try {
                    LOGGER.debug("Running periodic spot account snapshot refresh...");
                    bootstrapBalanceSnapshot();
                    LOGGER.debug("Periodic spot account snapshot refresh completed");
                    Thread.sleep(TEN_MINUTES);
                } catch (final InterruptedException e) {
                    LOGGER.info("Spot account refresh thread interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (final Exception e) {
                    LOGGER.error("Periodic account snapshot failed", e);
                    try {
                        Thread.sleep(ONE_MINUTE);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            LOGGER.info("Periodic spot account refresh thread terminated");
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

/*
    */
/**
     * Starts periodic futures account balance and position refresh using REST API
     * Runs every 10 minutes to ensure data consistency with exchange
     *//*

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
                    bootstrapFutureAccountSnapshot();
                    LOGGER.info("Periodic future account snapshot refresh completed");
                    Thread.sleep(TEN_MINUTES);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final Exception e) {
                    LOGGER.error("Periodic future account snapshot failed: " + e.getMessage());
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

    */
/**
     * Stops the periodic futures account refresh thread gracefully
     *//*

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
*/


    /**
     * Sends spot order with REST API and polling
     */
    private Order sendSpotOrder(final Order order) {
        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? TIME_IN_FORCE_FOK : TIME_IN_FORCE_GTC;
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());
        final String side = order.getSide().equals(Side.BUY) ? SIDE_BUY : SIDE_SELL;

        subscription.cacheNewOrder(order);

        LOGGER.debug("Sending SPOT order via REST for symbol: " + order.getSymbol() + " side: " + side + 
                    " qty: " + qtyStr + " price: " + priceStr);

        sendSpotOrderREST(order, order.getSymbol(), side, TYPE_LIMIT, timeInForce, qtyStr, priceStr, order.getClOrdId());

        // Poll order cache for status updates (WebSocket response) — skip in REST-only mode
        if (!restOnly) {
            LOGGER.debug("SPOT: Starting periodic check on Order Cache for status update");
            for (int i = 0; i < 2000; i++) {
                final Order existingOrder = subscription.getOrder(order.getClOrdId());
                if (existingOrder.isRejected() || existingOrder.isExecuted()) {
                    LOGGER.info("SPOT order status resolved from cache: " + order.getClOrdId() +
                               " - rejected: " + existingOrder.isRejected() + " executed: " + existingOrder.isExecuted());
                    return existingOrder;
                }
                try {
                    Thread.sleep(1);
                } catch (final InterruptedException e) {
                    LOGGER.error("Thread interrupted during order status check", e);
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }

        LOGGER.debug("SPOT: Starting periodic REST API check for status update");
        for (int i = 0; i < 100; i++) {
            mexcRestClient.querySpotOrderStatus(order, order.getSymbol(), order.getClOrdId());
            subscription.updateOrder(order.getClOrdId(), order);

            final Order existingOrder = subscription.getOrder(order.getClOrdId());
            if (existingOrder.isRejected() || existingOrder.isExecuted()) {
                LOGGER.info("SPOT order status resolved from REST API: " + order.getClOrdId() + 
                           " - rejected: " + existingOrder.isRejected() + " executed: " + existingOrder.isExecuted());
                return existingOrder;
            }
            try {
                Thread.sleep(50);
            } catch (final InterruptedException e) {
                LOGGER.error("Thread interrupted during REST API status check", e);
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        LOGGER.warn("SPOT: Could not determine final order status for order: " + order.getClOrdId());
        return order;
    }


    /**
     * Sends spot order via REST API as fallback method
     */
    private Order sendSpotOrderREST(final Order order, final String symbol, final String side, final String type, 
                                    final String timeInForce, final String quantity, final String price, final String clientOrderId) {
        final boolean result = mexcRestClient.sendSpotOrderREST(order, symbol, side, type, timeInForce, 
                quantity, price, clientOrderId);
        LOGGER.info("Result of sendSpotOrderREST: " + result);
        return order;
    }

    /**
     * Bootstraps initial spot account state by fetching balances via REST API
     */
    private void bootstrapSpotBalanceSnapshot() {
        final String body = mexcRestClient.getBalanceSnapshot();
        applySpotAccountSnapshot(body);
        LOGGER.info("Bootstrap spot snapshot applied");
    }

/*    *//**
     * Bootstraps initial futures account state by fetching balances and positions via REST API
     *//*
    private void bootstrapFutureAccountSnapshot() {
        final String body = mexcRestClient.getFutureAccountSnapshot();
        applyFutureAccountSnapshot(body);
        
        // Fetch positions for USDT settled contracts
        final String openUSDTBasedPosition = mexcRestClient.getAllPositions();
        updateOpenPosition(openUSDTBasedPosition);

        LOGGER.info("Bootstrap future snapshot applied");
    }*/

    /**
     * Bootstraps initial account state by fetching balances and positions via REST API
     */
    private void bootstrapBalanceSnapshot() {

        bootstrapSpotBalanceSnapshot();
/*        if (subscription.isFuturesEnabled()) {
            bootstrapFutureAccountSnapshot();
        } else {
            bootstrapSpotBalanceSnapshot();
        }*/
    }

   /* *//**
     * Updates open positions from MEXC position list JSON response
     *//*
    private void updateOpenPosition(final String openPosition) {
        if (openPosition == null) {
            LOGGER.warn("Open position response is null");
            return;
        }

        // Parse MEXC position JSON response - typically returns array directly
        if (!openPosition.trim().startsWith("[")) {
            LOGGER.warn("Invalid MEXC position response format");
            return;
        }

        final String positionsArray = openPosition.trim();
        
        // Parse each position object in the array
        int p = 1; // Skip opening bracket
        while (p >= 0 && p < positionsArray.length() - 1) {
            final int objStart = positionsArray.indexOf('{', p);
            if (objStart < 0) break;

            // Find matching closing brace
            int objEnd = -1;
            int braceDepth = 0;
            boolean inString = false;
            boolean escapeNext = false;

            for (int i = objStart; i < positionsArray.length(); i++) {
                final char c = positionsArray.charAt(i);

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
                            objEnd = i;
                            break;
                        }
                    }
                }
            }

            if (objEnd <= objStart) break;

            final String positionObj = positionsArray.substring(objStart, objEnd + 1);
            
            // Parse position data
            final String symbol = minExtract(positionObj, "symbol");
            final String positionSide = minExtract(positionObj, "positionSide");
            final double positionAmt = parseDoubleSafe(minExtract(positionObj, "positionAmt"));
            final double entryPrice = parseDoubleSafe(minExtract(positionObj, "entryPrice"));
            final double unrealizedPnl = parseDoubleSafe(minExtract(positionObj, "unrealizedPnl"));
            final String leverage = minExtract(positionObj, "leverage");

            if (symbol != null && positionAmt != 0.0) {
                subscription.updatePosition(symbol, positionAmt);
                LOGGER.debug("Updated MEXC position: " + symbol + " side=" + positionSide + " amount=" + positionAmt +
                           " entryPrice=" + entryPrice + " unrealizedPnl=" + unrealizedPnl + " leverage=" + leverage);
            }

            p = objEnd + 1;
        }
    }
*/

    /**
     * Applies spot account snapshot from MEXC account JSON response
     */
    private void applySpotAccountSnapshot(final String json) {
        if (json == null || json.trim().isEmpty()) {
            LOGGER.warn("Empty account snapshot response");
            return;
        }

        // MEXC returns balances array directly or in a "balances" field
        String balancesArray = json;
        
        // Check if response has "balances" field
        final int balancesStart = json.indexOf("\"balances\":");
        if (balancesStart >= 0) {
            final int arrayStart = json.indexOf('[', balancesStart);
            if (arrayStart >= 0) {
                // Find matching closing bracket
                int arrayEnd = -1;
                int bracketDepth = 0;
                boolean inString = false;
                boolean escapeNext = false;

                for (int i = arrayStart; i < json.length(); i++) {
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
                                arrayEnd = i;
                                break;
                            }
                        }
                    }
                }

                if (arrayEnd > arrayStart) {
                    balancesArray = json.substring(arrayStart, arrayEnd + 1);
                }
            }
        }

        if (!balancesArray.trim().startsWith("[")) {
            LOGGER.warn("Invalid balance response format");
            return;
        }

        // Parse each balance object in the array
        int p = 1; // Skip opening bracket
        while (p >= 0 && p < balancesArray.length() - 1) {
            final int objStart = balancesArray.indexOf('{', p);
            if (objStart < 0) break;

            // Find matching closing brace
            int objEnd = -1;
            int braceDepth = 0;
            boolean inString = false;
            boolean escapeNext = false;

            for (int i = objStart; i < balancesArray.length(); i++) {
                final char c = balancesArray.charAt(i);

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
                            objEnd = i;
                            break;
                        }
                    }
                }
            }

            if (objEnd > objStart) {
                final String balanceObj = balancesArray.substring(objStart, objEnd + 1);
                final String asset = minExtract(balanceObj, "asset");
                final double free = parseDoubleSafe(minExtract(balanceObj, "free"));
                final double locked = parseDoubleSafe(minExtract(balanceObj, "locked"));
                final double total = free + locked;

                if (asset != null && total > 0) {
                    subscription.updateBalance(asset, total);
                    LOGGER.debug("Updated MEXC balance: " + asset + " free=" + free + " locked=" + locked + " total=" + total);
                }
                p = objEnd + 1;
            } else {
                break;
            }
        }
    }

   /* *//**
     * Applies futures account snapshot from MEXC futures account JSON response
     *//*
    private void applyFutureAccountSnapshot(final String json) {
        if (json == null || json.trim().isEmpty()) {
            LOGGER.warn("Empty futures account snapshot response");
            return;
        }

        // MEXC futures returns data array in a wrapper object with success/code/data structure
        String dataArray = json;
        
        // Check if response has "data" field (new format)
        final int dataStart = json.indexOf("\"data\":");
        if (dataStart >= 0) {
            final int arrayStart = json.indexOf('[', dataStart);
            if (arrayStart >= 0) {
                // Find matching closing bracket
                int arrayEnd = -1;
                int bracketDepth = 0;
                boolean inString = false;
                boolean escapeNext = false;

                for (int i = arrayStart; i < json.length(); i++) {
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
                                arrayEnd = i;
                                break;
                            }
                        }
                    }
                }

                if (arrayEnd > arrayStart) {
                    dataArray = json.substring(arrayStart, arrayEnd + 1);
                }
            }
        } else {
            // Fallback: Check if response has "assets" field (old format)
            final int assetsStart = json.indexOf("\"assets\":");
            if (assetsStart >= 0) {
                final int arrayStart = json.indexOf('[', assetsStart);
                if (arrayStart >= 0) {
                    // Find matching closing bracket for assets array
                    int arrayEnd = -1;
                    int bracketDepth = 0;
                    boolean inString = false;
                    boolean escapeNext = false;

                    for (int i = arrayStart; i < json.length(); i++) {
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
                                    arrayEnd = i;
                                    break;
                                }
                            }
                        }
                    }

                    if (arrayEnd > arrayStart) {
                        dataArray = json.substring(arrayStart, arrayEnd + 1);
                    }
                }
            }
        }

        if (!dataArray.trim().startsWith("[")) {
            LOGGER.warn("Invalid futures account response format");
            return;
        }

        // Parse each account object in the array
        int p = 1; // Skip opening bracket
        while (p >= 0 && p < dataArray.length() - 1) {
            final int objStart = dataArray.indexOf('{', p);
            if (objStart < 0) break;

            // Find matching closing brace
            int objEnd = -1;
            int braceDepth = 0;
            boolean inString = false;
            boolean escapeNext = false;

            for (int i = objStart; i < dataArray.length(); i++) {
                final char c = dataArray.charAt(i);

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
                            objEnd = i;
                            break;
                        }
                    }
                }
            }

            if (objEnd > objStart) {
                final String accountObj = dataArray.substring(objStart, objEnd + 1);
                
                // Try new format fields first (currency, availableBalance, etc.)
                String currency = minExtract(accountObj, "currency");
                double availableBalance = parseDoubleSafe(minExtract(accountObj, "availableBalance"));
                double cashBalance = parseDoubleSafe(minExtract(accountObj, "cashBalance"));
                double equity = parseDoubleSafe(minExtract(accountObj, "equity"));
                
                // If new format fields are found, use them
                if (currency != null) {
                    // Use equity as the primary balance, fallback to availableBalance or cashBalance
                    double balance = equity > 0 ? equity : (availableBalance > 0 ? availableBalance : cashBalance);
                    
                    if (balance > 0) {
                        subscription.updateBalance(currency, balance);
                        LOGGER.debug("Updated MEXC futures balance (new format): " + currency + 
                                   " availableBalance=" + availableBalance + " cashBalance=" + cashBalance + 
                                   " equity=" + equity + " balance=" + balance);
                    }
                } else {
                    // Fallback to old format fields (asset, walletBalance, etc.)
                    final String asset = minExtract(accountObj, "asset");
                    final double walletBalance = parseDoubleSafe(minExtract(accountObj, "walletBalance"));
                    final double crossWalletBalance = parseDoubleSafe(minExtract(accountObj, "crossWalletBalance"));
                    final double totalMarginBalance = parseDoubleSafe(minExtract(accountObj, "totalMarginBalance"));

                    if (asset != null && walletBalance > 0) {
                        subscription.updateBalance(asset, walletBalance);
                        LOGGER.debug("Updated MEXC futures balance (old format): " + asset + " walletBalance=" + walletBalance + 
                                   " crossWalletBalance=" + crossWalletBalance + " totalMarginBalance=" + totalMarginBalance);
                    }
                }
                
                p = objEnd + 1;
            } else {
                break;
            }
        }
    }*/
}