package com.solfini.matchengine.liquidity.direct.aiGenerated;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;

import java.math.BigDecimal;
import java.util.*;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.liquidity.DelistedSymbolCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitmart.BitmartRestClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitmart.BitmartSpotUserDataListener;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitmart.BitmartFutureUserDataListener;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.StringUtil;

public final class BitmartFastClient implements ExternalExchangeClient {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(BitmartFastClient.class);
    private static final long TEN_MINUTES = 600_000;
    private static final long ONE_MINUTE = 60_000;

    private final ExchangeSubscription subscription;

    private final String apiKey;
    private final String secretKey;
    private final String memo;

    private final boolean restOnly;
    private final BitmartRestClient bitmartRestClient;
    private volatile BitmartSpotUserDataListener spotUserDataListener;
    private volatile BitmartFutureUserDataListener futureUserDataListener;

    private volatile Thread spotAccountRefreshThread;
    private volatile Thread futureAccountRefreshThread;
    private volatile boolean isSpotAccountRefreshRunning = false;
    private volatile boolean isFutureAccountRefreshRunning = false;

    public BitmartFastClient(final String apiKey, final String secretKey, final String memo, final ExchangeSubscription subscription) {
        this(apiKey, secretKey, memo, subscription, false);
    }

    public BitmartFastClient(final String apiKey, final String secretKey, final String memo, final ExchangeSubscription subscription, final boolean restOnly) {
        this.restOnly = restOnly;
        this.apiKey = Objects.requireNonNull(apiKey);
        this.secretKey = Objects.requireNonNull(secretKey);
        this.memo = Objects.requireNonNull(memo);
        this.subscription = subscription;
        this.bitmartRestClient = new BitmartRestClient(apiKey, secretKey, memo, subscription);
    }

    @Override
    public void start() {
        LOGGER.info("Starting BitMart client");
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
        LOGGER.info("Stopping BitMart client");
        stopPeriodicSpotAccountRefresh();
        stopPeriodicFutureAccountRefresh();

        if (spotUserDataListener != null) {
            spotUserDataListener.disconnect();
        }
        if (futureUserDataListener != null) {
            futureUserDataListener.disconnect();
        }
    }

    @Override
    public ExecutionReportMessage  sendOrder(final Order order, final boolean futuresEnabled, final String bestQuoteSymbol,
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
            result = bitmartRestClient.cancelSpotOrderRest(order, order.getSymbol(), order.getClOrdId());
        } else {
            result = bitmartRestClient.cancelFuturesOrderRest(order, order.getSymbol(), order.getClOrdId());
        }
        return result;
    }
    public boolean transferBalance(final String asset, final String amount, final boolean fromSpotToFutures) {
        if (fromSpotToFutures) {
            return bitmartRestClient.transferSpotToFutures(asset, amount);
        } else {
            return bitmartRestClient.transferFuturesToSpot(asset, amount);
        }
    }

    public String getAllOpenOrders() {
        final String jsonResp;
        if (subscription.isFuturesEnabled()) {
            jsonResp = bitmartRestClient.getAllOpenFuturesOrders();
        } else {
            jsonResp = bitmartRestClient.getAllOpenSpotOrders();
        }
        return jsonResp;
    }


    @Override
    public Ticker getTicker(final String base, final String quote, final int instrumentType) {
        final Ticker ticker;
        if (instrumentType == 1) {
            // Futures: symbol format BTCUSDT
            final String symbol = base + quote;
            ticker = bitmartRestClient.getFutureTicker(symbol);
        } else {
            // Spot: symbol format BTC_USDT
            final String symbol = base + "_" + quote;
            ticker = bitmartRestClient.getSpotTicker(symbol);
        }
        if (ticker != null) {
            ticker.setInstrumentType(instrumentType);
        }
        return ticker;
    }

    @Override
    public List<DelistedSymbol> getDelistedSymbols() {
        return subscription.isFuturesEnabled()
                ? bitmartRestClient.getFuturesDelistedSymbols()
                : bitmartRestClient.getSpotDelistedSymbols();
    }

    public List<ExternalSymbol> getExchangeInstrumentsFull() {
        LOGGER.debug("Fetching exchange instruments (spot and futures)");
        final String futureSymbolDetails = bitmartRestClient.getFutureSymbolDetails();
        final String spotSymbolDetails = bitmartRestClient.getSpotSymbolDetails();
        final List<ExternalSymbol> futureInstruments = getFutureSymbolInfo(futureSymbolDetails);
        final List<ExternalSymbol> spotInstruments = getSpotSymbolInfo(spotSymbolDetails);
        
        final List<ExternalSymbol> combinedInstruments = new ArrayList<>();
        
        // Add future instruments
        if (futureInstruments != null) {
            LOGGER.debug("Adding " + futureInstruments.size() + " future instruments");
            combinedInstruments.addAll(futureInstruments);
        }
        
        // Add spot instruments
        if (spotInstruments != null) {
            LOGGER.debug("Adding " + spotInstruments.size() + " spot instruments");
            combinedInstruments.addAll(spotInstruments);
        }
        
        LOGGER.info("Total instruments loaded: " + combinedInstruments.size());
        return combinedInstruments;
    }

    private List<ExternalSymbol> getFutureSymbolInfo(final String futureSymbolDetails) {
        LOGGER.debug("Parsing future symbol information");
        final List<ExternalSymbol> symbolStatuses = new ArrayList<>();
        try {
            final long updated = System.currentTimeMillis();
            // Find "symbols" array in the JSON
            final String dataJson = extractJsonValue(futureSymbolDetails, "data");
            if (dataJson == null) {
                LOGGER.warn("Future symbol data is null");
                return symbolStatuses;
            }
            final String symbolsArray = extractJsonValue(dataJson, "symbols");
            if (symbolsArray == null || !symbolsArray.startsWith("[")) {
                LOGGER.warn("Future symbols array not found or invalid");
                return symbolStatuses;
            }

            int idx = 0;
            while (idx < symbolsArray.length()) {
                idx = symbolsArray.indexOf('{', idx);
                if (idx < 0) break;
                int braceCount = 0;
                int objEnd = idx;
                for (int i = idx; i < symbolsArray.length(); i++) {
                    if (symbolsArray.charAt(i) == '{') braceCount++;
                    else if (symbolsArray.charAt(i) == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }
                if (objEnd <= idx) break;
                final String obj = symbolsArray.substring(idx, objEnd + 1);

                final String symbol = minExtract(obj, "symbol");
                final String status = minExtract(obj, "status");

                  /*
                    URL: https://api-cloud-v2.bitmart.com/contract/public/details
                    Sample Data:
                    {
                    "symbol": "BTCUSDT",
                        "product_type": 1,
                        "open_timestamp": 1645977600000,
                        "expire_timestamp": 0,
                        "settle_timestamp": 0,
                        "base_currency": "BTC",
                        "quote_currency": "USDT",
                        "last_price": "90645.3",
                        "volume_24h": "133630474",
                        "turnover_24h": "12151473327.3832",
                        "index_price": "90651.89673913",
                        "index_name": "BTCUSDT",
                        "contract_size": "0.001",
                        "min_leverage": "1",
                        "max_leverage": "200",
                        "price_precision": "0.1",
                        "vol_precision": "1",
                        "max_volume": "500000",
                        "min_volume": "1",
                        "funding_rate": "0.00015",
                        "expected_funding_rate": "0.0001305",
                        "open_interest": "727798",
                        "open_interest_value": "68783854.03819381",
                        "high_24h": "93130.8",
                        "low_24h": "88581.7",
                        "change_24h": "-0.0004245212282667",
                        "funding_time": 1763654400000,
                        "market_max_volume": "80000",
                        "funding_interval_hours": 8,
                        "status": "Trading",
                        "delist_time": 0
                }
                 */

                if (symbol != null && "Trading".equalsIgnoreCase(status)) {
                    final String baseCurrency = minExtract(obj, "base_currency");
                    final String quoteCurrency = minExtract(obj, "quote_currency");
                    final String contractSizeStr = minExtract(obj, "contract_size");
                    final String pricePrecisionStr = minExtract(obj, "price_precision");
                    final String volPrecisionStr = minExtract(obj, "vol_precision");

                    final BigDecimal contractSize = contractSizeStr != null ? new BigDecimal(contractSizeStr) : BigDecimal.ZERO;
                    final int pricePrecision = pricePrecisionStr != null ? (int) Double.parseDouble(pricePrecisionStr) : 0;
                    final int volumePrecision = volPrecisionStr != null ? (int) Double.parseDouble(volPrecisionStr) : 0;

                    final ExternalSymbol symbolStatus = new ExternalSymbol();
                    symbolStatus.setExchange("bitmart");
                    symbolStatus.setSymbol(symbol);
                    symbolStatus.setBase(baseCurrency);
                    symbolStatus.setQuote(quoteCurrency);
                    symbolStatus.setPrompt("");
                    symbolStatus.setTradable(true);
                    symbolStatus.setFutures(true);
                    symbolStatus.setUpdated(updated);

                    symbolStatus.setPriceScale(pricePrecision);
                    symbolStatus.setQtyScale(volumePrecision);
                    symbolStatuses.add(symbolStatus);
                    LOGGER.debug("Added future symbol: " + symbol);
                }
                idx = objEnd + 1;
            }
            LOGGER.info("Parsed " + symbolStatuses.size() + " future symbols");
        } catch (final Exception e) {
            LOGGER.error("getFutureSymbolInfo failed: " + e.getMessage(), e);
        }
        return symbolStatuses;
    }

    private List<ExternalSymbol> getSpotSymbolInfo(final String futureSymbolDetails) {
        LOGGER.debug("Parsing spot symbol information");
        final List<ExternalSymbol> symbolStatuses = new ArrayList<>();
        try {
            final long updated = System.currentTimeMillis();
            // Find "symbols" array in the JSON
            final String dataJson = extractJsonValue(futureSymbolDetails, "data");
            if (dataJson == null) {
                LOGGER.warn("Spot symbol data is null");
                return symbolStatuses;
            }
            final String symbolsArray = extractJsonValue(dataJson, "symbols");
            if (symbolsArray == null || !symbolsArray.startsWith("[")) {
                LOGGER.warn("Spot symbols array not found or invalid");
                return symbolStatuses;
            }

            int idx = 0;
            while (idx < symbolsArray.length()) {
                idx = symbolsArray.indexOf('{', idx);
                if (idx < 0) break;
                int braceCount = 0;
                int objEnd = idx;
                for (int i = idx; i < symbolsArray.length(); i++) {
                    if (symbolsArray.charAt(i) == '{') braceCount++;
                    else if (symbolsArray.charAt(i) == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }
                if (objEnd <= idx) break;
                final String obj = symbolsArray.substring(idx, objEnd + 1);

                final String symbol = minExtract(obj, "symbol");
                final String status = minExtract(obj, "trade_status");
              /*
              URL: https://api-cloud.bitmart.com/spot/v1/symbols/details
              Sample Data:
              {
                    "symbol": "$NUT_USDT",
                        "symbol_id": 5443,
                        "base_currency": "$NUT",
                        "quote_currency": "USDT",
                        "quote_increment": "1",
                        "base_min_size": "1",
                        "price_min_precision": 1,
                        "price_max_precision": 4,
                        "expiration": "NA",
                        "min_buy_amount": "5.000000000000000000000000000000",
                        "min_sell_amount": "5.000000000000000000000000000000",
                        "trade_status": "trading"
                }
                */

                if (symbol != null && "Trading".equalsIgnoreCase(status)) {
                    final String baseCurrency = minExtract(obj, "base_currency");
                    final String quoteCurrency = minExtract(obj, "quote_currency");
                    // ...existing code...

                    final ExternalSymbol symbolStatus = new ExternalSymbol();
                    symbolStatus.setExchange("bitmart");
                    symbolStatus.setSymbol(symbol);
                    symbolStatus.setBase(baseCurrency);
                    symbolStatus.setQuote(quoteCurrency);
                    symbolStatus.setPrompt("");
                    symbolStatus.setTradable(true);
                    symbolStatus.setFutures(false);
                    symbolStatus.setUpdated(updated);

                    symbolStatuses.add(symbolStatus);
                    LOGGER.debug("Added spot symbol: " + symbol);
                }
                idx = objEnd + 1;
            }
            LOGGER.info("Parsed " + symbolStatuses.size() + " spot symbols");
        } catch (final Exception e) {
            LOGGER.error("getSpotSymbolInfo failed: " + e.getMessage(), e);
        }
        return symbolStatuses;
    }

    // ======= Spot Client Methods =======

    private void startSpotClient() {
        try {
            LOGGER.info("Starting BitMart Spot Client");

            // Get snapshot via REST
             bootstrapSpotBalanceSnapshot();

            // Start periodic polling using REST
            startPeriodicSpotAccountRefresh();
            if(!restOnly) {
                // Start real-time WebSocket streams
                startUserSpotDataStream();
            }

        } catch (final Exception e) {
            LOGGER.error("Failed to start Spot Client", e);
            throw new RuntimeException(e);
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
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final Exception e) {
                    LOGGER.error("Periodic spot account snapshot failed: " + e.getMessage());
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

    private void startUserSpotDataStream() throws Exception {
        LOGGER.info("Starting BitMart Spot User Data Stream");
        spotUserDataListener = new BitmartSpotUserDataListener(apiKey, secretKey, memo, subscription);
        spotUserDataListener.connect();
    }

    private void bootstrapSpotBalanceSnapshot() {
        final String body = bitmartRestClient.getSpotBalanceSnapshot();
        applySpotAccountSnapshot(body);
        LOGGER.info("Spot balance snapshot applied");
    }

    private Order sendSpotOrder(final Order order) {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "limit" : "market";
        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? "fok" : "gtc";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order);

        LOGGER.debug("Sending SPOT order via REST for symbol: " + order.getSymbol());
        bitmartRestClient.sendSpotOrderREST(order, order.getSymbol(), order.getSide().toString(),
                type, timeInForce, qtyStr, priceStr, order.getClOrdId());

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
            bitmartRestClient.querySpotOrderStatus(order, order.getSymbol(), order.getClOrdId());
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

    // ======= Future Client Methods =======

    private void startFutureClient() {
        try {
            LOGGER.info("Starting BitMart Future Client");

            // Get snapshot via REST
            bootstrapFutureBalanceSnapshot();

            // Start periodic polling using REST
            startPeriodicFutureAccountRefresh();

            if(!restOnly){
                // Start real-time WebSocket streams
                startUserFutureDataStream();
            }

        } catch (final Exception e) {
            LOGGER.error("Failed to start Future Client", e);
            throw new RuntimeException(e);
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

    private void startUserFutureDataStream() throws Exception {
        LOGGER.info("Starting BitMart Future User Data Stream");
        futureUserDataListener = new BitmartFutureUserDataListener(apiKey, secretKey, memo, subscription);
        futureUserDataListener.connect();
    }


    private void bootstrapFutureBalanceSnapshot() {
        final String body = bitmartRestClient.getFutureBalanceSnapshot();
        applyFutureAccountSnapshot(body);

        // Fetch positions for futures contracts
        final String openPosition = bitmartRestClient.getPositionInfo();
        updateOpenPosition(openPosition);

        LOGGER.info("Future balance snapshot applied");
    }

    private Order sendFutureOrder(final Order order, final String bestQuoteSymbol, final String baseSymbol,
                                  final int priceScale, final int qtyScale, final double fxRate) {
        final String type = order.getType() == Constants.BUY_LIMIT || order.getType() == Constants.SELL_LIMIT ? "limit" : "market";
        final String timeInForce = order.getTimeInForce() == TimeInForce.FILL_OR_KILL ? "fok" : "gtc";
        final String priceStr = StringUtil.toNumericString(order.getPrice(), order.getPriceScale());
        final String qtyStr = StringUtil.toNumericString(order.getQty(), order.getQtyScale());

        subscription.cacheNewOrder(order);

        LOGGER.debug("Sending FUTURE order via REST for symbol: " + order.getSymbol());
        bitmartRestClient.sendFutureOrderREST(order, order.getSymbol(), order.getSide().toString(),
                type, timeInForce, qtyStr, priceStr, order.getClOrdId());

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
            bitmartRestClient.queryFuturesOrderStatus(order, order.getSymbol(), order.getClOrdId());
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

    // ======= Common Helper Methods =======

    private void updateOpenPosition(final String positionInfo) {
        if (positionInfo == null) {
            LOGGER.warn("Position info response is null");
            return;
        }

        // Parse Bitmart position info JSON response
        // Expected format: {"code":1000,"data":[{...position objects...}]}
        final String code = minExtract(positionInfo, "code");
        if (!"1000".equals(code)) {
            LOGGER.warn("Position info response has error code: " + code);
            return;
        }

        final int dataStart = positionInfo.indexOf("\"data\":");
        if (dataStart < 0) {
            LOGGER.warn("No \"data\" section in Bitmart position response");
            return;
        }

        // Find the data array
        int arrayStart = -1;
        for (int i = dataStart + 7; i < positionInfo.length(); i++) {
            final char c = positionInfo.charAt(i);
            if (c == '[') {
                arrayStart = i;
                break;
            } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t' && c != ':') {
                break;
            }
        }

        if (arrayStart < 0) {
            LOGGER.warn("No array found in Bitmart position response data");
            return;
        }

        // Find the matching closing bracket for the data array
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
        LOGGER.debug("Position data array content: " + dataArray.substring(0, Math.min(200, dataArray.length())) + "...");

        // Parse each position object in the data array
        int objStart = 0;
        int positionCount = 0;
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

            // Parse position data using Bitmart JSON structure
            final String symbol = minExtract(positionObj, "symbol");
            final double currentAmount = parseDoubleSafe(minExtract(positionObj, "current_amount"));
            final double holdAvgPrice = parseDoubleSafe(minExtract(positionObj, "hold_avg_price"));
            final double unrealised = parseDoubleSafe(minExtract(positionObj, "unrealised"));
            final String openType = minExtract(positionObj, "open_type");
            final String state = minExtract(positionObj, "state");
            final String leverage = minExtract(positionObj, "leverage");

            if (symbol != null && currentAmount != 0.0) {
                subscription.updatePosition(symbol, currentAmount);
                positionCount++;
                LOGGER.debug("Updated Bitmart position: " + symbol + " currentAmount=" + currentAmount +
                        " holdAvgPrice=" + holdAvgPrice + " unrealised=" + unrealised +
                        " openType=" + openType + " state=" + state + " leverage=" + leverage);
            }

            objStart = objEnd + 1;
        }
        LOGGER.info("Updated " + positionCount + " positions");
    }

    private void applySpotAccountSnapshot(final String json) {
        if (json == null) {
            LOGGER.warn("Spot account snapshot JSON is null");
            return;
        }

        // Bitmart wallet response format: {"code":1000,"message":"OK","data":{"wallet":[...]}}
        final String code = minExtract(json, "code");
        if (!"1000".equals(code)) {
            LOGGER.warn("Spot account snapshot response has error code: " + code);
            return;
        }

        final int dataIdx = json.indexOf("\"data\":");
        if (dataIdx < 0) {
            LOGGER.warn("No data field in spot account snapshot");
            return;
        }

        final int walletIdx = json.indexOf("\"wallet\":", dataIdx);
        if (walletIdx < 0) return;

        int arrayStart = -1;
        for (int i = walletIdx + 9; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == '[') {
                arrayStart = i;
                break;
            } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t' && c != ':') {
                break;
            }
        }

        if (arrayStart < 0) return;

        // Find the matching closing bracket for the wallet array
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

        // Parse each wallet object in the array
        int objStart = 0;
        int balanceCount = 0;
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
            final String currency = minExtract(obj, "currency");
            final double available = parseDoubleSafe(minExtract(obj, "available"));

            if (currency != null && available > 0) {
                subscription.updateBalance(currency, available);
                balanceCount++;
                LOGGER.debug("Updated spot balance: " + currency + " = " + available);
            }

            objStart = objEnd + 1;
        }
        LOGGER.info("Updated " + balanceCount + " spot balances");
    }

    private void applyFutureAccountSnapshot(final String json) {
        if (json == null) {
            LOGGER.warn("Future account snapshot JSON is null");
            return;
        }

        final String code = minExtract(json, "code");
        if (!"1000".equals(code)) {
            LOGGER.warn("Future account snapshot response has error code: " + code);
            return;
        }

        final int dataIdx = json.indexOf("\"data\":");
        if (dataIdx < 0) {
            LOGGER.warn("No data field in future account snapshot");
            return;
        }

        int arrayStart = -1;
        for (int i = dataIdx + 7; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == '[') {
                arrayStart = i;
                break;
            } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t' && c != ':') {
                break;
            }
        }

        if (arrayStart < 0) return;

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

        int objStart = 0;
        int balanceCount = 0;
        while (objStart < dataArray.length()) {
            objStart = dataArray.indexOf('{', objStart);
            if (objStart < 0) break;

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
            final String currency = minExtract(obj, "currency");
            final double availableBalance = parseDoubleSafe(minExtract(obj, "available_balance"));

            if (currency != null && availableBalance > 0) {
                subscription.updateBalance(currency, availableBalance);
                balanceCount++;
                LOGGER.debug("Updated future balance: " + currency + " = " + availableBalance);
            }

            objStart = objEnd + 1;
        }
        LOGGER.info("Updated " + balanceCount + " future balances");
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