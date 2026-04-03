package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kucoin;

import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.util.HttpUtils;
import com.solfini.util.HMAC;
import com.solfini.util.MbxMath;

import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.extractJsonValue;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseLongSafe;

public class KucoinRestClient {
    private static final CustomLogger LOGGER = CustomLogger.getLogger(KucoinRestClient.class);
    private static final int PROXY_PORT = 8888;
    private static final long TIMESTAMP_BUFFER_MS = 1000;
    private static final int SYNC_SERVER_TIME_RETRIES = 3;
    private static final long SYNC_RETRY_DELAY_MS = 1_500;
    private static final int BULLET_PRIVATE_RETRIES = 5;
    private static final long BULLET_PRIVATE_RETRY_DELAY_MS = 2_000;

    private final String restFutureBase;
    private final String restSpotBase;
    private final String apiKey;
    private final String apiSecret;
    private final ExchangeSubscription subscription;
    private final String encodedPassphrase;
    private long timeDelta = 0;
    private volatile long lastSyncedServerTimeMs = 0;

    public KucoinRestClient(final String apiKey, final String apiSecret, final ExchangeSubscription subscription,
                            final String restSpotBase, final String restFutureBase) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.subscription = subscription;
        this.restSpotBase = restSpotBase;
        this.restFutureBase = restFutureBase;
        final String passphrase = subscription.getPassphrase() != null ? subscription.getPassphrase() : "";
        this.encodedPassphrase = computeEncodedPassphrase(passphrase);
        syncServerTime();
    }

    // ==============================================================================================
    // Public API — Balance & Positions
    // ==============================================================================================

    public String getApiKey() { return apiKey; }
    public String getApiSecret() { return apiSecret; }
    public String getEncodedPassphrase() { return encodedPassphrase; }

    public String getSpotBalanceSnapshot() {
        final String requestPath = "/api/v1/accounts?type=trade";
        final String method = "GET";
        final String body = "";
        final String url = restSpotBase + requestPath;
        try {
            syncServerTime();
            final String timestamp = String.valueOf(getAdjustedTimestamp());
            final Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, body);
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved KuCoin spot accounts snapshot");
                return response.getData();
            }
            final String errorMsg = response != null ? response.getData() : "null response";
            LOGGER.warn("KuCoin spot snapshot failed: " + (response != null ? response.getCode() : "null") + " " + errorMsg);
            handleKuCoinError(errorMsg);
            return null;
        } catch (final Exception e) {
            LOGGER.error("KuCoin spot snapshot failed: " + e.getMessage());
            return null;
        }
    }

    public String getFutureAccountSnapshot() {
        final String requestPath = "/api/v1/account-overview";
        final String method = "GET";
        final String body = "";
        final String url = restFutureBase + requestPath;
        try {
            syncServerTime(true);
            final String timestamp = String.valueOf(getAdjustedTimestamp());
            final Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, body);
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved KuCoin futures account snapshot");
                return response.getData();
            }
            handleKuCoinError(response != null ? response.getData() : null);
            return null;
        } catch (final Exception e) {
            LOGGER.error("KuCoin futures account snapshot failed: " + e.getMessage());
            return null;
        }
    }

    public String getAllPositions() {
        final String requestPath = "/api/v1/positions";
        final String method = "GET";
        final String body = "";
        final String url = restFutureBase + requestPath;
        try {
            syncServerTime(true);
            final String timestamp = String.valueOf(getAdjustedTimestamp());
            final Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, body);
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                return response.getData();
            }
            return null;
        } catch (final Exception e) {
            LOGGER.error("KuCoin get positions failed: " + e.getMessage());
            return null;
        }
    }

    // ==============================================================================================
    // Public API — Balance Transfer
    // ==============================================================================================

    public boolean transferSpotToFutures(final String asset, final String amount) {
        return transfer(asset, amount, "TRADE", "CONTRACT", "Spot -> Futures");
    }

    public boolean transferFuturesToSpot(final String asset, final String amount) {
        return transfer(asset, amount, "CONTRACT", "TRADE", "Futures -> Spot");
    }

    // ==============================================================================================
    // Public API — Send Orders
    // ==============================================================================================

    public boolean sendSpotOrderREST(final Order order, final String symbol, final String side, final String type,
                                     final String timeInForce, final String quantityStr, final String priceStr,
                                     final String clientOrderId) {
        final String kucoinSymbol = symbol.contains("-") ? symbol : toKuCoinSymbol(symbol);
        final String requestPath = "/api/v1/hf/orders";
        final String method = "POST";
        final String typeLower = type == null ? "limit" : type.toLowerCase();
        final String sideLower = side == null ? "buy" : side.toLowerCase();
        final StringBuilder body = new StringBuilder();
        body.append("{\"clientOid\":\"").append(clientOrderId).append("\"");
        body.append(",\"symbol\":\"").append(kucoinSymbol).append("\"");
        body.append(",\"side\":\"").append(sideLower).append("\"");
        body.append(",\"type\":\"").append(typeLower).append("\"");
        body.append(",\"size\":").append(quantityStr);
        if ("limit".equals(typeLower)) {
            body.append(",\"price\":\"").append(priceStr).append("\"");
            body.append(",\"timeInForce\":\"").append(timeInForce != null ? timeInForce : "GTC").append("\"");
        }
        body.append("}");
        final String bodyStr = body.toString();
        final String url = restSpotBase + requestPath;
        try {
            syncServerTime();
            String timestamp = String.valueOf(getAdjustedTimestamp());
            Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, bodyStr);
            headers.put("Content-Type", "application/json");
            HttpUtils.Response resp = HttpUtils.post(url, headers, bodyStr.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            String json = resp != null ? resp.getData() : null;
            if (resp != null && resp.getCode() != 200 && json != null && "400002".equals(JsonHelper.minExtract(json, "code"))) {
                LOGGER.warn("KuCoin timestamp rejected (400002), re-syncing and retrying once");
                syncServerTime();
                timestamp = String.valueOf(getAdjustedTimestamp());
                headers = kucoinHeaders(timestamp, method, requestPath, bodyStr);
                headers.put("Content-Type", "application/json");
                resp = HttpUtils.post(url, headers, bodyStr.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
                json = resp != null ? resp.getData() : null;
            }
            if (resp != null && resp.getCode() == 200 && json != null && "200000".equals(JsonHelper.minExtract(json, "code"))) {
                final String dataSection = extractJsonValue(json, "data");
                if (dataSection != null) {
                    final String orderId = JsonHelper.minExtract(dataSection, "orderId");
                    if (orderId != null && !orderId.isEmpty()) {
                        order.setOcoClOrdId(orderId);
                        LOGGER.info("SPOT: Order created via REST. orderId: " + orderId);
                        return true;
                    }
                }
            }
            if (json != null) {
                handleKuCoinError(json);
                final String code = JsonHelper.minExtract(json, "code");
                if (code != null && !"200000".equals(code)) {
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);
                    final String errorMsg = JsonHelper.minExtract(json, "msg");
                    ExecutionReportMessage rejectReport = subscription.getExecutionReport(order.getClOrdId());
                    if (rejectReport == null) {
                        rejectReport = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }
                    rejectReport.setClOrdId(clientOrderId);
                    rejectReport.setError(errorMsg != null && !errorMsg.isEmpty() ? errorMsg : code);
                    rejectReport.setOrdStatus(OrdStatus.REJECTED);
                    rejectReport.setExecType(ExecType.REJECTED);
                    subscription.updateExecutionReport(rejectReport);
                }
            }
            return false;
        } catch (final Exception e) {
            LOGGER.error("REST KuCoin spot order failed: " + e.getMessage());
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }

    public boolean sendFutureOrderREST(final Order order, final String symbol, final String side, final String type,
                                       final String timeInForce, final String quantityStr, final String priceStr,
                                       final String clientOrderId, final boolean reduceOnly) {
        final String requestPath = "/api/v1/orders";
        final String method = "POST";
        final String kucoinSymbol = toKuCoinFuturesSymbol(symbol);
        final String typeLower = type == null ? "limit" : type.toLowerCase();
        final String sideLower = side == null ? "buy" : side.toLowerCase();
        final String tif = (timeInForce != null && !timeInForce.isEmpty()) ? timeInForce : "GTC";
        final StringBuilder body = new StringBuilder();
        body.append("{\"clientOid\":\"").append(clientOrderId).append("\"");
        body.append(",\"symbol\":\"").append(kucoinSymbol).append("\"");
        body.append(",\"marginMode\":\"CROSS\"");
        body.append(",\"leverage\":1");
        body.append(",\"positionSide\":\"BOTH\"");
        body.append(",\"side\":\"").append(sideLower).append("\"");
        body.append(",\"type\":\"").append(typeLower).append("\"");
        body.append(",\"size\":").append(quantityStr);
        if ("limit".equals(typeLower)) {
            body.append(",\"price\":\"").append(priceStr).append("\"");
            body.append(",\"timeInForce\":\"").append(tif).append("\"");
        }
        body.append(",\"reduceOnly\":").append(reduceOnly);
        body.append("}");
        final String bodyStr = body.toString();
        final String url = restFutureBase + requestPath;
        try {
            syncServerTime(true);
            String timestamp = String.valueOf(getAdjustedTimestamp());
            Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, bodyStr);
            headers.put("Content-Type", "application/json");
            LOGGER.debug("Sending KuCoin futures order REST for symbol: " + kucoinSymbol + " side: " + side);
            HttpUtils.Response resp = HttpUtils.post(url, headers, bodyStr.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            String json = resp != null ? resp.getData() : null;
            if (resp != null && resp.getCode() != 200 && json != null && "400002".equals(JsonHelper.minExtract(json, "code"))) {
                LOGGER.warn("KuCoin futures timestamp rejected (400002), re-syncing from futures and retrying once");
                syncServerTime(true);
                timestamp = String.valueOf(getAdjustedTimestamp());
                headers = kucoinHeaders(timestamp, method, requestPath, bodyStr);
                headers.put("Content-Type", "application/json");
                resp = HttpUtils.post(url, headers, bodyStr.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
                json = resp != null ? resp.getData() : null;
            }
            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("REST KuCoin futures order status=" + resp.getCode() + " body=" + json);
            }
            if (json != null && "200000".equals(JsonHelper.minExtract(json, "code"))) {
                final String dataSection = extractJsonValue(json, "data");
                if (dataSection != null) {
                    final String orderId = JsonHelper.minExtract(dataSection, "orderId");
                    if (orderId != null && !orderId.isEmpty()) {
                        LOGGER.info("FUTURE: Order created via REST. orderId: " + orderId + ", Symbol: " + kucoinSymbol);
                        order.setOcoClOrdId(orderId);
                        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                        if (executionMessage == null) {
                            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                    0, 0, 0, 0, order.getSide(), 0);
                        }
                        executionMessage.setClOrdId(clientOrderId);
                        executionMessage.setOrdStatus(OrdStatus.NEW);
                        executionMessage.setExecType(ExecType.NEW);
                        subscription.updateExecutionReport(executionMessage);
                        subscription.updateOrder(clientOrderId, order);
                        return true;
                    }
                }
            }
            if (json != null) {
                final String code = JsonHelper.minExtract(json, "code");
                if (!"200000".equals(code)) {
                    final String msg = JsonHelper.minExtract(json, "msg");
                    LOGGER.error("KuCoin futures order failed: code=" + code + ", msg=" + msg);
                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }
                    executionMessage.setClOrdId(clientOrderId);
                    executionMessage.setError(msg != null ? msg : code);
                    executionMessage.setOrdStatus(OrdStatus.REJECTED);
                    executionMessage.setExecType(ExecType.REJECTED);
                    subscription.updateExecutionReport(executionMessage);
                    order.setRejected(true);
                    subscription.updateOrder(clientOrderId, order);
                }
            }
            return false;
        } catch (final Exception e) {
            LOGGER.error("REST KuCoin futures order failed: " + e.getMessage());
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }

    // ==============================================================================================
    // Public API — Query Order Status
    // ==============================================================================================

    public boolean querySpotOrderStatus(final Order order, final String clientOrderId, final String symbol, final int iteration) {
        final String kucoinSymbol = symbol.contains("-") ? symbol : toKuCoinSymbol(symbol);
        final String requestPath = "/api/v1/hf/orders/client-order/" + clientOrderId + "?symbol=" + kucoinSymbol;
        final String method = "GET";
        final String body = "";
        final String url = restSpotBase + requestPath;
        try {
            syncServerTime();
            String timestamp = String.valueOf(getAdjustedTimestamp());
            Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, body);
            LOGGER.debug("Querying KuCoin spot order status for clientOid: " + clientOrderId);
            HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() != 200) {
                final String errJson = resp.getData();
                if (errJson != null && "400002".equals(JsonHelper.minExtract(errJson, "code"))) {
                    LOGGER.warn("KuCoin spot order status timestamp rejected (400002), re-syncing and retrying once");
                    syncServerTime();
                    timestamp = String.valueOf(getAdjustedTimestamp());
                    headers = kucoinHeaders(timestamp, method, requestPath, body);
                    resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
                }
            }
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("REST KuCoin spot order status query failed: " + (resp != null ? resp.getCode() : "null"));
                return false;
            }
            final String json = resp.getData();
            final String code = JsonHelper.minExtract(json, "code");
            if ("200000".equals(code)) {
                final String dataSection = extractJsonValue(json, "data");
                if (dataSection != null) {
                    populateOrderFromKuCoinJson(order, dataSection, clientOrderId);
                }
            } else {
                final String msg = JsonHelper.minExtract(json, "msg");
                LOGGER.warn("KuCoin order status query error: code=" + code + ", msg=" + msg);
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST KuCoin spot order status query failed: " + e.getMessage());
            return false;
        }
    }

    public boolean queryFuturesOrderStatus(final Order order, final String clientOrderId, final String symbol, final int iteration) {
        final String kucoinSymbol = toKuCoinFuturesSymbol(symbol);
        final String requestPath;
        if (order.getOcoClOrdId() != null && !order.getOcoClOrdId().isEmpty()) {
            requestPath = "/api/v1/orders/" + order.getOcoClOrdId();
        } else {
            requestPath = "/api/v1/orders/byClientOid?clientOid=" + clientOrderId + "&symbol=" + kucoinSymbol;
        }
        final String method = "GET";
        final String body = "";
        final String url = restFutureBase + requestPath;
        try {
            syncServerTime(true);
            String timestamp = String.valueOf(getAdjustedTimestamp());
            Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, body);
            LOGGER.debug("Querying KuCoin futures order status for clientOid: " + clientOrderId + " iteration: " + iteration);
            HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() != 200) {
                final String errJson = resp.getData();
                if (errJson != null && "400002".equals(JsonHelper.minExtract(errJson, "code"))) {
                    LOGGER.warn("KuCoin futures order status timestamp rejected (400002), re-syncing and retrying once");
                    syncServerTime(true);
                    timestamp = String.valueOf(getAdjustedTimestamp());
                    headers = kucoinHeaders(timestamp, method, requestPath, body);
                    resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
                }
            }
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("REST KuCoin futures order status query failed: " + (resp != null ? resp.getCode() : "null"));
                return false;
            }
            final String json = resp.getData();
            final String code = JsonHelper.minExtract(json, "code");
            if ("200000".equals(code)) {
                final String dataSection = extractJsonValue(json, "data");
                if (dataSection != null) {
                    populateOrderFromKuCoinFuturesJson(order, dataSection, clientOrderId);
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST KuCoin futures order status query failed: " + e.getMessage());
            return false;
        }
    }

    // ==============================================================================================
    // Public API — Cancel Orders
    // ==============================================================================================

    public boolean cancelSpotOrderRest(final String clientOrderId, final String symbol) {
        final String kucoinSymbol = symbol.contains("-") ? symbol : toKuCoinSymbol(symbol);
        final String requestPath = "/api/v1/hf/orders/client-order/" + clientOrderId + "?symbol=" + kucoinSymbol;
        final String method = "DELETE";
        final String body = "";
        final String url = restSpotBase + requestPath;
        try {
            syncServerTime();
            final String timestamp = String.valueOf(getAdjustedTimestamp());
            final Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, body);
            final HttpUtils.Response resp = HttpUtils.delete(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() == 200) {
                final String json = resp.getData();
                final String code = JsonHelper.minExtract(json, "code");
                if ("200000".equals(code)) {
                    LOGGER.info("KuCoin spot order cancelled: clientOid=" + clientOrderId);
                    return true;
                }
            }
            LOGGER.warn("KuCoin cancel spot order failed: " + (resp != null ? resp.getData() : "null"));
            return false;
        } catch (final Exception e) {
            LOGGER.error("KuCoin cancel spot order failed: " + e.getMessage());
            return false;
        }
    }

    public boolean cancelAllSpotOrdersRest() {
        final String requestPath = "/api/v1/hf/orders/cancelAll";
        final String method = "DELETE";
        final String body = "";
        final String url = restSpotBase + requestPath;
        try {
            syncServerTime();
            final String timestamp = String.valueOf(getAdjustedTimestamp());
            final Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, body);
            final HttpUtils.Response resp = HttpUtils.delete(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() == 200) {
                final String code = JsonHelper.minExtract(resp.getData(), "code");
                if ("200000".equals(code)) {
                    LOGGER.info("KuCoin cancel all spot orders ok");
                    return true;
                }
            }
            return false;
        } catch (final Exception e) {
            LOGGER.error("KuCoin cancel all spot orders failed: " + e.getMessage());
            return false;
        }
    }

    public boolean cancelFuturesOrderRest(final String clientOrderId, final String symbol) {
        final String kucoinSymbol = toKuCoinFuturesSymbol(symbol);
        final String requestPath = "/api/v1/orders/client-order/" + clientOrderId + "?symbol=" + kucoinSymbol;
        final String method = "DELETE";
        final String body = "";
        final String url = restFutureBase + requestPath;
        try {
            syncServerTime(true);
            final String timestamp = String.valueOf(getAdjustedTimestamp());
            final Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, body);
            final HttpUtils.Response resp = HttpUtils.delete(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() == 200) {
                final String json = resp.getData();
                final String code = JsonHelper.minExtract(json, "code");
                if ("200000".equals(code)) {
                    LOGGER.info("KuCoin futures order cancelled: clientOid=" + clientOrderId);
                    return true;
                }
            }
            return false;
        } catch (final Exception e) {
            LOGGER.error("KuCoin cancel futures order failed: " + e.getMessage());
            return false;
        }
    }

    // ==============================================================================================
    // Public API — Open Orders
    // ==============================================================================================

    /**
     * Get all open spot orders across all symbols.
     * Step 1: GET /api/v1/hf/orders/active/symbols → list of symbols with open orders
     * Step 2: GET /api/v1/hf/orders/active?symbol=X for each symbol
     * Aggregates all orders into a single response.
     */
    public String getAllOpenSpotOrders() {
        try {
            final List<String> symbols = getSymbolsWithOpenOrders();
            if (symbols == null || symbols.isEmpty()) {
                LOGGER.debug("No symbols with open spot orders");
                return "{\"code\":\"200000\",\"data\":null}";
            }
            LOGGER.debug("Found " + symbols.size() + " symbol(s) with open orders: " + symbols);

            final StringBuilder aggregated = new StringBuilder("[");
            boolean first = true;
            for (final String sym : symbols) {
                final String result = fetchOpenSpotOrdersForSymbol(sym);
                if (result == null) continue;
                final String dataArray = extractJsonValue(result, "data");
                if (dataArray == null || "null".equals(dataArray) || "[]".equals(dataArray.trim())) continue;

                // Strip outer brackets and append individual order objects
                final String inner = dataArray.trim();
                final String orders = inner.startsWith("[") ? inner.substring(1, inner.length() - 1).trim() : inner;
                if (orders.isEmpty()) continue;

                if (!first) aggregated.append(",");
                aggregated.append(orders);
                first = false;
            }
            aggregated.append("]");

            final String allOrders = aggregated.toString();
            if ("[]".equals(allOrders)) {
                return "{\"code\":\"200000\",\"data\":null}";
            }
            return "{\"code\":\"200000\",\"data\":" + allOrders + "}";
        } catch (final Exception e) {
            LOGGER.error("Get all open spot orders failed: " + e.getMessage());
            return null;
        }
    }

    private List<String> getSymbolsWithOpenOrders() {
        final String requestPath = "/api/v1/hf/orders/active/symbols";
        final String method = "GET";
        final String body = "";
        final String url = restSpotBase + requestPath;
        try {
            syncServerTime();
            String timestamp = String.valueOf(getAdjustedTimestamp());
            Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, body);
            HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() != 200) {
                final String json = response.getData();
                if (json != null && "400002".equals(JsonHelper.minExtract(json, "code"))) {
                    syncServerTime();
                    timestamp = String.valueOf(getAdjustedTimestamp());
                    headers = kucoinHeaders(timestamp, method, requestPath, body);
                    response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
                }
            }
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get symbols with open orders failed: status=" + (response != null ? response.getCode() : "null"));
                return null;
            }
            final String json = response.getData();
            if (!"200000".equals(JsonHelper.minExtract(json, "code"))) {
                handleKuCoinError(json);
                return null;
            }
            final String dataObj = extractJsonValue(json, "data");
            if (dataObj == null) return null;
            final String symbolsArray = extractJsonValue(dataObj, "symbols");
            if (symbolsArray == null || !symbolsArray.startsWith("[")) return null;

            // Parse simple string array: ["ETH-USDT","BTC-USDT"]
            final List<String> symbols = new ArrayList<>();
            int idx = 0;
            while (idx < symbolsArray.length()) {
                final int qStart = symbolsArray.indexOf('"', idx);
                if (qStart < 0) break;
                final int qEnd = symbolsArray.indexOf('"', qStart + 1);
                if (qEnd < 0) break;
                symbols.add(symbolsArray.substring(qStart + 1, qEnd));
                idx = qEnd + 1;
            }
            return symbols;
        } catch (final Exception e) {
            LOGGER.error("Get symbols with open orders failed: " + e.getMessage());
            return null;
        }
    }

    private String fetchOpenSpotOrdersForSymbol(final String kucoinSymbol) {
        final String requestPath = "/api/v1/hf/orders/active?symbol=" + kucoinSymbol;
        final String method = "GET";
        final String body = "";
        final String url = restSpotBase + requestPath;
        try {
            syncServerTime();
            String timestamp = String.valueOf(getAdjustedTimestamp());
            Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, body);
            LOGGER.debug("Getting open spot orders: GET /api/v1/hf/orders/active?symbol=" + kucoinSymbol);
            HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() != 200) {
                final String json = response.getData();
                if (json != null && "400002".equals(JsonHelper.minExtract(json, "code"))) {
                    LOGGER.warn("KuCoin get open orders timestamp rejected (400002), re-syncing and retrying once");
                    syncServerTime();
                    timestamp = String.valueOf(getAdjustedTimestamp());
                    headers = kucoinHeaders(timestamp, method, requestPath, body);
                    response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
                }
            }
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get open spot orders failed: status=" + (response != null ? response.getCode() : "null"));
                if (response != null && response.getData() != null) {
                    handleKuCoinError(response.getData());
                }
                return null;
            }
            String data = response.getData();
            if (!"200000".equals(JsonHelper.minExtract(data, "code"))) {
                LOGGER.warn("KuCoin get open orders API code=" + JsonHelper.minExtract(data, "code"));
                handleKuCoinError(data);
                return null;
            }
            if (data != null && data.contains("\"data\":[]")) {
                data = data.replace("\"data\":[]", "\"data\":null");
            }
            LOGGER.debug("Successfully retrieved open spot orders for " + kucoinSymbol);
            return data;
        } catch (final Exception e) {
            LOGGER.error("Get open spot orders failed: " + e.getMessage());
            return null;
        }
    }

    public String getAllOpenFuturesOrders() {
        final String requestPath = "/api/v1/orders?status=active";
        final String method = "GET";
        final String body = "";
        final String url = restFutureBase + requestPath;
        try {
            syncServerTime(true);
            String timestamp = String.valueOf(getAdjustedTimestamp());
            Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, body);
            LOGGER.debug("Getting all open futures orders (KuCoin GET /api/v1/orders?status=active)");
            HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT,
                subscription.isForceToUseProxy());
            if (response != null && response.getCode() != 200) {
                final String json = response.getData();
                if (json != null && "400002".equals(JsonHelper.minExtract(json, "code"))) {
                    LOGGER.warn("KuCoin get open futures orders timestamp rejected (400002), re-syncing and retrying once");
                    syncServerTime(true);
                    timestamp = String.valueOf(getAdjustedTimestamp());
                    headers = kucoinHeaders(timestamp, method, requestPath, body);
                    response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
                }
            }
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get all open futures orders failed: status=" + (response != null ? response.getCode() : "null"));
                if (response != null && response.getData() != null) {
                    handleKuCoinError(response.getData());
                }
                return null;
            }
            final String raw = response.getData();
            final String code = JsonHelper.minExtract(raw, "code");
            if (!"200000".equals(code)) {
                LOGGER.warn("KuCoin get open futures orders API code=" + code);
                handleKuCoinError(raw);
                return null;
            }
            final String dataObj = extractJsonValue(raw, "data");
            final String itemsStr = dataObj != null ? extractJsonValue(dataObj, "items") : null;
            final boolean empty = itemsStr == null || itemsStr.trim().isEmpty() || "[]".equals(itemsStr.trim());
            final String normalized = empty
                    ? "{\"code\":\"200000\",\"data\":null}"
                    : "{\"code\":\"200000\",\"data\":" + itemsStr + "}";
            LOGGER.debug("Successfully retrieved all open futures orders");
            return normalized;
        } catch (final Exception e) {
            LOGGER.error("Get all open futures orders failed: " + e.getMessage());
            return null;
        }
    }

    // ==============================================================================================
    // Public API — Instruments
    // ==============================================================================================

    /**
     * Fetches all available trading instruments from KuCoin (spot + futures) and returns them as SymbolData list.
     * Spot API:    GET /api/v2/symbols (public, no auth)
     * Futures API: GET /api/v1/contracts/active (public, no auth)
     */
    public List<ExternalSymbol> getExchangeInstrumentsFull() {
        final List<ExternalSymbol> externalSymbolList = new ArrayList<>();
        final long updated = System.currentTimeMillis();

        // Fetch spot symbols
        try {
            final String spotJson = getSpotSymbols();
            if (spotJson != null) {
                parseSpotSymbols(spotJson, externalSymbolList, updated);
                LOGGER.debug("Fetched " + externalSymbolList.size() + " spot instruments");
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to fetch KuCoin spot instruments: " + e.getMessage());
        }

        // Fetch futures symbols
        try {
            final int spotCount = externalSymbolList.size();
            final String futuresJson = getFuturesSymbols();
            if (futuresJson != null) {
                parseFuturesSymbols(futuresJson, externalSymbolList, updated);
                LOGGER.debug("Fetched " + (externalSymbolList.size() - spotCount) + " futures instruments");
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to fetch KuCoin futures instruments: " + e.getMessage());
        }

        LOGGER.info("Total KuCoin instruments fetched: " + externalSymbolList.size());
        return externalSymbolList.isEmpty() ? null : externalSymbolList;
    }

    private String getSpotSymbols() {
        final String url = restSpotBase + "/api/v2/symbols";
        try {
            final HttpUtils.Response response = HttpUtils.get(url, new HashMap<>(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                return response.getData();
            }
            LOGGER.warn("KuCoin get spot symbols failed: status=" + (response != null ? response.getCode() : "null"));
        } catch (final Exception e) {
            LOGGER.error("KuCoin get spot symbols failed: " + e.getMessage());
        }
        return null;
    }

    private String getFuturesSymbols() {
        final String url = restFutureBase + "/api/v1/contracts/active";
        try {
            final HttpUtils.Response response = HttpUtils.get(url, new HashMap<>(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                return response.getData();
            }
            LOGGER.warn("KuCoin get futures symbols failed: status=" + (response != null ? response.getCode() : "null"));
        } catch (final Exception e) {
            LOGGER.error("KuCoin get futures symbols failed: " + e.getMessage());
        }
        return null;
    }

    public Ticker getSpotTicker(final String symbol) {
        try {
            final String url = restSpotBase + "/api/v1/market/stats?symbol=" + symbol;
            final HttpUtils.Response response = HttpUtils.get(url, new HashMap<>(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get spot ticker failed for symbol: " + symbol + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
            final String json = response.getData();
            final String data = extractJsonValue(json, "data");
            if (data == null) {
                LOGGER.warn("No data in spot ticker response for: " + symbol);
                return null;
            }
            final double bid              = parseDoubleSafe(minExtract(data, "buy"));
            final double ask              = parseDoubleSafe(minExtract(data, "sell"));
            final double high             = parseDoubleSafe(minExtract(data, "high"));
            final double low              = parseDoubleSafe(minExtract(data, "low"));
            final double baseVolume       = parseDoubleSafe(minExtract(data, "vol"));
            final double quoteVolume      = parseDoubleSafe(minExtract(data, "volValue"));
            final double last             = parseDoubleSafe(minExtract(data, "last"));
            final double percentageChange = parseDoubleSafe(minExtract(data, "changeRate")) * 100.0;
            final long timestamp          = parseLongSafe(minExtract(data, "time"));
            LOGGER.debug("Retrieved KuCoin spot ticker for symbol: " + symbol + " last=" + last + " bid=" + bid + " ask=" + ask);
            return new Ticker(symbol, 0, last, last, bid, ask, high, low, baseVolume, quoteVolume, timestamp, 0.0, 0.0, percentageChange);
        } catch (final Exception e) {
            LOGGER.error("Get spot ticker failed for symbol: " + symbol + " " + e.getMessage());
            return null;
        }
    }

    public Ticker getFutureTicker(final String symbol) {
        try {
            final String url = restFutureBase + "/api/v1/ticker?symbol=" + symbol;
            final HttpUtils.Response response = HttpUtils.get(url, new HashMap<>(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get future ticker failed for symbol: " + symbol + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
            final String json = response.getData();
            final String data = extractJsonValue(json, "data");
            if (data == null) {
                LOGGER.warn("No data in future ticker response for: " + symbol);
                return null;
            }
            final double last    = parseDoubleSafe(minExtract(data, "price"));
            final double bid     = parseDoubleSafe(minExtract(data, "bestBidPrice"));
            final double ask     = parseDoubleSafe(minExtract(data, "bestAskPrice"));
            final double bidSize = parseDoubleSafe(minExtract(data, "bestBidSize"));
            final double askSize = parseDoubleSafe(minExtract(data, "bestAskSize"));
            final long timestamp = parseLongSafe(minExtract(data, "ts")) / 1_000_000L;
            LOGGER.debug("Retrieved KuCoin future ticker for symbol: " + symbol + " last=" + last + " bid=" + bid + " ask=" + ask);
            return new Ticker(symbol, 0, last, last, bid, ask, 0.0, 0.0, 0.0, 0.0, timestamp, bidSize, askSize, 0.0);
        } catch (final Exception e) {
            LOGGER.error("Get future ticker failed for symbol: " + symbol + " " + e.getMessage());
            return null;
        }
    }

    private void parseSpotSymbols(final String json, final List<ExternalSymbol> list, final long updated) {
        final String dataArray = extractJsonValue(json, "data");
        if (dataArray == null || !dataArray.startsWith("[")) return;

        int objStart = 0;
        while (objStart < dataArray.length()) {
            objStart = dataArray.indexOf('{', objStart);
            if (objStart < 0) break;
            int braceCount = 0;
            int objEnd = objStart;
            for (int i = objStart; i < dataArray.length(); i++) {
                if (dataArray.charAt(i) == '{') braceCount++;
                else if (dataArray.charAt(i) == '}') {
                    braceCount--;
                    if (braceCount == 0) { objEnd = i; break; }
                }
            }
            if (objEnd <= objStart) break;

            final String obj = dataArray.substring(objStart, objEnd + 1);
            final String symbol = minExtract(obj, "symbol");
            final String baseCurrency = minExtract(obj, "baseCurrency");
            final String quoteCurrency = minExtract(obj, "quoteCurrency");
            final String enableTrading = minExtract(obj, "enableTrading");
            final double baseMinSize = parseDoubleSafe(minExtract(obj, "baseMinSize"));
            final double baseMaxSize = parseDoubleSafe(minExtract(obj, "baseMaxSize"));
            final double baseIncrement = parseDoubleSafe(minExtract(obj, "baseIncrement"));
            final double priceIncrement = parseDoubleSafe(minExtract(obj, "priceIncrement"));

            if (symbol != null && baseCurrency != null && quoteCurrency != null) {
                final ExternalSymbol sd = new ExternalSymbol();
                sd.setExchange("kucoin");
                sd.setSymbol(symbol);
                sd.setBase(baseCurrency);
                sd.setQuote(quoteCurrency);
                sd.setFutures(false);
                sd.setTradable("true".equalsIgnoreCase(enableTrading));
                sd.setPriceScale(deriveScale(priceIncrement));
                sd.setQtyScale(deriveScale(baseIncrement));
                sd.setMinimumAmount(baseMinSize);
                sd.setMaximumAmount(baseMaxSize);
                sd.setAmountStepSize(baseIncrement);
                sd.setPriceStepSize(priceIncrement);
                sd.setUpdated(updated);
                list.add(sd);
            }
            objStart = objEnd + 1;
        }
    }

    private void parseFuturesSymbols(final String json, final List<ExternalSymbol> list, final long updated) {
        final String dataArray = extractJsonValue(json, "data");
        if (dataArray == null || !dataArray.startsWith("[")) return;

        int objStart = 0;
        while (objStart < dataArray.length()) {
            objStart = dataArray.indexOf('{', objStart);
            if (objStart < 0) break;
            int braceCount = 0;
            int objEnd = objStart;
            for (int i = objStart; i < dataArray.length(); i++) {
                if (dataArray.charAt(i) == '{') braceCount++;
                else if (dataArray.charAt(i) == '}') {
                    braceCount--;
                    if (braceCount == 0) { objEnd = i; break; }
                }
            }
            if (objEnd <= objStart) break;

            final String obj = dataArray.substring(objStart, objEnd + 1);
            final String symbol = minExtract(obj, "symbol");
            final String baseCurrency = minExtract(obj, "baseCurrency");
            final String quoteCurrency = minExtract(obj, "quoteCurrency");
            final String status = minExtract(obj, "status");
            final double lotSize = parseDoubleSafe(minExtract(obj, "lotSize"));
            final double tickSize = parseDoubleSafe(minExtract(obj, "tickSize"));
            final double multiplier = parseDoubleSafe(minExtract(obj, "multiplier"));
            final double maxOrderQty = parseDoubleSafe(minExtract(obj, "maxOrderQty"));

            if (symbol != null && baseCurrency != null) {
                final ExternalSymbol sd = new ExternalSymbol();
                sd.setExchange("kucoin");
                sd.setSymbol(symbol);
                sd.setBase(baseCurrency);
                sd.setQuote(quoteCurrency != null ? quoteCurrency : "USDT");
                sd.setFutures(true);
                sd.setTradable("Open".equalsIgnoreCase(status));
                sd.setPriceScale(deriveScale(tickSize));
                sd.setQtyScale(deriveScale(lotSize));
                sd.setMinimumAmount(lotSize);
                sd.setMaximumAmount(maxOrderQty);
                sd.setAmountStepSize(lotSize);
                sd.setPriceStepSize(tickSize);
                sd.setUpdated(updated);
                if (multiplier > 0) {
                    sd.setMultiplierContract((int) multiplier);
                }
                list.add(sd);
            }
            objStart = objEnd + 1;
        }
    }

    private static int deriveScale(final double stepSize) {
        if (stepSize <= 0 || stepSize >= 1.0) return 0;
        int scale = 0;
        double val = stepSize;
        while (val < 1.0 && scale < 12) {
            val *= 10;
            scale++;
        }
        return scale;
    }

    // ==============================================================================================
    // Public API — WebSocket Token
    // ==============================================================================================

    public PrivateTokenResult getSpotPrivateToken() {
        return getPrivateToken(false);
    }

    public PrivateTokenResult getFuturesPrivateToken() {
        return getPrivateToken(true);
    }

    public static final class PrivateTokenResult {
        private final String token;
        private final String endpoint;

        public PrivateTokenResult(final String token, final String endpoint) {
            this.token = token;
            this.endpoint = endpoint;
        }

        public String getToken() {
            return token;
        }

        public String getEndpoint() {
            return endpoint;
        }
    }

    // ==============================================================================================
    // Private — Transfer Helper
    // ==============================================================================================

    private boolean transfer(final String currency, final String amount,
                             final String fromAccountType, final String toAccountType,
                             final String logPrefix) {
        if (currency == null || currency.trim().isEmpty() || amount == null || amount.trim().isEmpty()) {
            LOGGER.warn("KuCoin transfer: currency and amount are required");
            return false;
        }
        final String requestPath = "/api/v3/accounts/universal-transfer";
        final String method = "POST";
        final String bodyStr = "{\"currency\":\"" + currency.trim() + "\",\"amount\":\"" + amount.trim()
                + "\",\"fromAccountType\":\"" + fromAccountType
                + "\",\"toAccountType\":\"" + toAccountType + "\"}";
        final String url = restSpotBase + requestPath;
        try {
            syncServerTime();
            String timestamp = String.valueOf(getAdjustedTimestamp());
            Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, bodyStr);
            headers.put("Content-Type", "application/json");
            LOGGER.debug("KuCoin " + logPrefix + ": " + currency + " amount=" + amount);
            HttpUtils.Response resp = HttpUtils.post(url, headers, bodyStr.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            String json = resp != null ? resp.getData() : null;
            if (resp != null && resp.getCode() != 200 && json != null && "400002".equals(JsonHelper.minExtract(json, "code"))) {
                LOGGER.warn("KuCoin " + logPrefix + " timestamp rejected (400002), re-syncing and retrying once");
                syncServerTime();
                timestamp = String.valueOf(getAdjustedTimestamp());
                headers = kucoinHeaders(timestamp, method, requestPath, bodyStr);
                headers.put("Content-Type", "application/json");
                resp = HttpUtils.post(url, headers, bodyStr.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
                json = resp != null ? resp.getData() : null;
            }
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("KuCoin " + logPrefix + " failed: status=" + (resp != null ? resp.getCode() : "null"));
                if (json != null) handleKuCoinError(json);
                return false;
            }
            if (json == null || !"200000".equals(JsonHelper.minExtract(json, "code"))) {
                if (json != null) handleKuCoinError(json);
                return false;
            }
            LOGGER.info("KuCoin " + logPrefix + " success: " + currency + " " + amount);
            return true;
        } catch (final Exception e) {
            LOGGER.error("KuCoin " + logPrefix + " failed: " + e.getMessage());
            return false;
        }
    }

    // ==============================================================================================
    // Private — Order Response Parsing
    // ==============================================================================================

    private void populateOrderFromKuCoinJson(final Order order, final String orderJson, final String clientOrderId) {
        try {
            final String activeStr = JsonHelper.minExtract(orderJson, "active");
            final String dealSizeStr = JsonHelper.minExtract(orderJson, "dealSize");
            final String sizeStr = JsonHelper.minExtract(orderJson, "size");
            final String priceStr = JsonHelper.minExtract(orderJson, "price");
            final String feeStr = JsonHelper.minExtract(orderJson, "fee");
            final String remainSizeStr = JsonHelper.minExtract(orderJson, "remainSize");
            final String cancelExistStr = JsonHelper.minExtract(orderJson, "cancelExist");

            final boolean active = "true".equalsIgnoreCase(activeStr);
            final double dealSize = parseDoubleSafe(dealSizeStr);
            final double size = parseDoubleSafe(sizeStr);
            final double priceVal = parseDoubleSafe(priceStr);
            final double feeVal = parseDoubleSafe(feeStr);
            final double remainSize = parseDoubleSafe(remainSizeStr);
            final boolean cancelExist = "true".equalsIgnoreCase(cancelExistStr);

            final long dealSizeLong = MbxMath.changeScale(dealSize, order.getQtyScale());
            final long remainSizeLong = MbxMath.changeScale(remainSize, order.getQtyScale());
            final long avgPriceLong = MbxMath.changeScale(priceVal, order.getPriceScale());

            ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
            if (executionMessage == null) {
                executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                        order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                        0, 0, 0, 0, order.getSide(), 0);
            }
            executionMessage.setClOrdId(clientOrderId);
            executionMessage.setPrice(avgPriceLong);
            executionMessage.setPriceScale(order.getPriceScale());
            executionMessage.setOrderQty(order.getQty());
            executionMessage.setOrderQtyScale(order.getQtyScale());
            executionMessage.setCumQty(dealSizeLong);
            executionMessage.setLeavesQty(remainSizeLong);
            executionMessage.setAvgPx(avgPriceLong);

            if (cancelExist || (!active && remainSize > 0 && dealSize < size)) {
                executionMessage.setOrdStatus(OrdStatus.CANCELED);
                executionMessage.setExecType(ExecType.CANCELED);
                order.setRejected(true);
            } else if (!active && dealSize >= size) {
                executionMessage.setOrdStatus(OrdStatus.FILLED);
                executionMessage.setExecType(ExecType.TRADE);
                order.setExecuted(true);
                final String feeCurrency = JsonHelper.minExtract(orderJson, "feeCurrency");
                if (feeCurrency != null) {
                    final Instrument feesInstrument = InstrumentCache.getBySymbol(feeCurrency);
                    if (feesInstrument != null) {
                        final long feesLong = MbxMath.changeScale(feeVal, feesInstrument.getQuantityScale());
                        executionMessage.setFeeAccumulatedQuantity(feesLong);
                        executionMessage.setFeePositionId(feesInstrument.getId());
                    }
                }
            } else {
                executionMessage.setOrdStatus(OrdStatus.NEW);
                executionMessage.setExecType(ExecType.NEW);
                if (dealSize > 0) {
                    executionMessage.setOrdStatus(OrdStatus.PARTIALLY_FILLED);
                    executionMessage.setExecType(ExecType.TRADE);
                }
            }
            subscription.updateExecutionReport(executionMessage);
            subscription.updateOrder(clientOrderId, order);
        } catch (final Exception e) {
            LOGGER.error("Error populating order from KuCoin JSON: " + e.getMessage());
        }
    }

    private void populateOrderFromKuCoinFuturesJson(final Order order, final String orderJson, final String clientOrderId) {
        try {
            final String orderId = minExtract(orderJson, "id");
            if (orderId != null && !orderId.isEmpty()) {
                order.setOcoClOrdId(orderId);
            }
            final String statusStr = minExtract(orderJson, "status");
            final String dealSizeStr = minExtract(orderJson, "dealSize");
            final String sizeStr = minExtract(orderJson, "size");
            final String priceStr = minExtract(orderJson, "price");
            final String filledSizeStr = minExtract(orderJson, "filledSize");
            final String cancelExistStr = minExtract(orderJson, "cancelExist");
            final String isActiveStr = minExtract(orderJson, "isActive");

            final double size = parseDoubleSafe(sizeStr);
            final double dealSize = parseDoubleSafe(dealSizeStr);
            final double filledSize = parseDoubleSafe(filledSizeStr);
            final double remainSize = size - (filledSize > 0 ? filledSize : dealSize);
            final double priceVal = parseDoubleSafe(priceStr);
            final boolean cancelExist = "true".equalsIgnoreCase(cancelExistStr);
            final boolean isActive = "true".equalsIgnoreCase(isActiveStr);

            final long dealSizeLong = MbxMath.changeScale(dealSize, order.getQtyScale());
            final long remainSizeLong = MbxMath.changeScale(remainSize, order.getQtyScale());
            final long totalSizeLong = MbxMath.changeScale(size, order.getQtyScale());
            final long avgPriceLong = MbxMath.changeScale(priceVal, order.getPriceScale());

            ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
            if (executionMessage == null) {
                executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                        order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                        0, 0, 0, 0, order.getSide(), 0);
            }
            executionMessage.setClOrdId(clientOrderId);
            executionMessage.setPrice(avgPriceLong);
            executionMessage.setPriceScale(order.getPriceScale());
            executionMessage.setOrderQty(totalSizeLong);
            executionMessage.setOrderQtyScale(order.getQtyScale());
            executionMessage.setCumQty(dealSizeLong);
            executionMessage.setLeavesQty(remainSizeLong);
            executionMessage.setAvgPx(avgPriceLong);

            if (cancelExist || "cancel".equalsIgnoreCase(statusStr) || (!isActive && remainSize > 0 && dealSize < size)) {
                executionMessage.setOrdStatus(OrdStatus.CANCELED);
                executionMessage.setExecType(ExecType.CANCELED);
                order.setRejected(true);
            } else if ("done".equalsIgnoreCase(statusStr) || (!isActive && dealSize >= size)) {
                executionMessage.setOrdStatus(OrdStatus.FILLED);
                executionMessage.setExecType(ExecType.TRADE);
                order.setExecuted(true);
            } else {
                executionMessage.setOrdStatus(OrdStatus.NEW);
                executionMessage.setExecType(ExecType.NEW);
                if (dealSize > 0) {
                    executionMessage.setOrdStatus(OrdStatus.PARTIALLY_FILLED);
                    executionMessage.setExecType(ExecType.TRADE);
                }
            }
            subscription.updateExecutionReport(executionMessage);
            subscription.updateOrder(clientOrderId, order);
        } catch (final Exception e) {
            LOGGER.error("Error populating order from KuCoin futures JSON: " + e.getMessage());
        }
    }

    // ==============================================================================================
    // Private — WebSocket Token Helper
    // ==============================================================================================

    private PrivateTokenResult getPrivateToken(final boolean forFutures) {
        final String label = forFutures ? "futures" : "spot";
        final String requestPath = "/api/v1/bullet-private";
        final String method = "POST";
        final String bodyForSignature = "";
        final String base = forFutures ? restFutureBase : restSpotBase;
        final String url = base + requestPath;

        syncServerTime(forFutures);
        Exception lastEx = null;

        for (int attempt = 1; attempt <= BULLET_PRIVATE_RETRIES; attempt++) {
            try {
                final String timestamp = String.valueOf(getAdjustedTimestamp());
                final Map<String, Object> headers = kucoinHeaders(timestamp, method, requestPath, bodyForSignature);
                final HttpUtils.Response resp = HttpUtils.post(url, headers, null, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

                if (resp == null) {
                    LOGGER.warn("getPrivateToken(" + label + "): attempt " + attempt + "/" + BULLET_PRIVATE_RETRIES + " — HTTP response is null");
                    lastEx = new IllegalStateException("HTTP response is null");
                    sleepBeforeRetry(attempt);
                    continue;
                }

                final int httpCode = resp.getCode();
                final String json = resp.getData();

                if (httpCode != 200) {
                    LOGGER.warn("getPrivateToken(" + label + "): attempt " + attempt + "/" + BULLET_PRIVATE_RETRIES + " — HTTP " + httpCode);
                    if (json != null) handleKuCoinError(json);
                    lastEx = new IllegalStateException("HTTP " + httpCode);
                    sleepBeforeRetry(attempt);
                    continue;
                }

                if (json == null || json.isEmpty()) {
                    LOGGER.warn("getPrivateToken(" + label + "): attempt " + attempt + "/" + BULLET_PRIVATE_RETRIES + " — response body is null or empty");
                    lastEx = new IllegalStateException("Response body is null or empty");
                    sleepBeforeRetry(attempt);
                    continue;
                }

                final String codeStr = minExtract(json, "code");
                if (codeStr == null || !"200000".equals(codeStr.trim())) {
                    LOGGER.warn("getPrivateToken(" + label + "): attempt " + attempt + "/" + BULLET_PRIVATE_RETRIES + " — API code=" + codeStr);
                    handleKuCoinError(json);
                    lastEx = new IllegalStateException("API code=" + codeStr);
                    sleepBeforeRetry(attempt);
                    continue;
                }

                final PrivateTokenResult result = parseBulletPrivateResponse(json);
                if (result != null) {
                    LOGGER.info("getPrivateToken(" + label + "): success endpoint=" + result.getEndpoint());
                    return result;
                }

                LOGGER.warn("getPrivateToken(" + label + "): attempt " + attempt + "/" + BULLET_PRIVATE_RETRIES + " — parse failed (missing token or endpoint)");
                lastEx = new IllegalStateException("Parse failed: missing token or endpoint");
                sleepBeforeRetry(attempt);
            } catch (final Exception e) {
                lastEx = e;
                LOGGER.warn("getPrivateToken(" + label + "): attempt " + attempt + "/" + BULLET_PRIVATE_RETRIES + " — error: " + e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }

        LOGGER.error("getPrivateToken(" + label + "): failed after " + BULLET_PRIVATE_RETRIES + " attempts. Last error: " + (lastEx != null ? lastEx.getMessage() : "unknown"));
        return null;
    }

    private static String parseFirstInstanceServerEndpoint(final String json) {
        if (json == null) return null;
        final String dataStr = extractJsonValue(json, "data");
        if (dataStr == null) return null;
        final String instanceServers = extractJsonValue(dataStr, "instanceServers");
        if (instanceServers == null || !instanceServers.startsWith("[")) return null;
        final int objStart = instanceServers.indexOf('{');
        if (objStart < 0) return null;
        final String endpoint = minExtract(instanceServers.substring(objStart), "endpoint");
        if (endpoint == null || endpoint.isEmpty()) return null;
        return endpoint.startsWith("wss://") ? endpoint : "wss://" + endpoint;
    }

    private static PrivateTokenResult parseBulletPrivateResponse(final String json) {
        if (json == null || json.isEmpty()) return null;
        final String dataStr = extractJsonValue(json, "data");
        if (dataStr == null) return null;
        final String token = minExtract(dataStr, "token");
        if (token == null || token.isEmpty()) return null;
        final String endpoint = parseFirstInstanceServerEndpoint(json);
        if (endpoint == null || endpoint.isEmpty()) return null;
        return new PrivateTokenResult(token, endpoint);
    }

    // ==============================================================================================
    // Private — Auth, Signing & Time Sync
    // ==============================================================================================

    /** Syncs with the KuCoin spot server clock and returns the adjusted timestamp (ms). */
    public long syncedTimestamp() {
        syncServerTime(false);
        return getAdjustedTimestamp();
    }


    private void syncServerTime() {
        syncServerTime(false);
    }

    private void syncServerTime(final boolean forFutures) {
        final String base = forFutures ? restFutureBase : restSpotBase;
        final String url = base + "/api/v1/timestamp";
        Exception lastEx = null;
        for (int attempt = 0; attempt < SYNC_SERVER_TIME_RETRIES; attempt++) {
            try {
                final HttpUtils.Response response = HttpUtils.get(url, new HashMap<>(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
                if (response != null && response.getCode() == 200) {
                    final String data = response.getData();
                    final String serverTimeStr = JsonHelper.minExtract(data, "data");
                    if (serverTimeStr != null && !serverTimeStr.isEmpty()) {
                        final long serverTime = Long.parseLong(serverTimeStr);
                        final long localTime = System.currentTimeMillis();
                        timeDelta = serverTime - localTime;
                        lastSyncedServerTimeMs = serverTime;
                        LOGGER.debug("KuCoin server time sync (" + (forFutures ? "futures" : "spot") + "): delta=" + timeDelta + "ms");
                        return;
                    }
                }
            } catch (final Exception e) {
                lastEx = e;
                LOGGER.debug("Could not sync server time (attempt " + (attempt + 1) + "/" + SYNC_SERVER_TIME_RETRIES + "): " + e.getMessage());
                if (attempt < SYNC_SERVER_TIME_RETRIES - 1) {
                    try {
                        Thread.sleep(SYNC_RETRY_DELAY_MS);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (!forFutures) {
            timeDelta = 0;
        }
        if (lastEx != null) {
            LOGGER.debug("KuCoin server time sync failed after " + SYNC_SERVER_TIME_RETRIES + " attempts: " + lastEx.getMessage());
        }
    }

    private long getAdjustedTimestamp() {
        final long base = lastSyncedServerTimeMs > 0
                ? lastSyncedServerTimeMs + (System.currentTimeMillis() - (lastSyncedServerTimeMs - timeDelta))
                : System.currentTimeMillis() + timeDelta;
        return base - TIMESTAMP_BUFFER_MS;
    }

    private Map<String, Object> kucoinHeaders(final String timestamp, final String method, final String requestPath, final String body) {
        final Map<String, Object> headers = new HashMap<>();
        headers.put("KC-API-KEY", apiKey);
        headers.put("KC-API-SIGN", generateSignature(timestamp, method, requestPath, body));
        headers.put("KC-API-TIMESTAMP", timestamp);
        headers.put("KC-API-PASSPHRASE", encodedPassphrase);
        headers.put("KC-API-KEY-VERSION", "2");
        final String brokerId = subscription.getBrokerId();
        final String brokerKey = subscription.getBrokerKey();
        final String brokerName = subscription.getBrokerName();
        if (brokerId != null && !brokerId.isBlank() && brokerKey != null && !brokerKey.isBlank()) {
            try {
                final String partnerSignHex = HMAC.hmacSha256(timestamp + brokerId + apiKey, brokerKey);
                final String partnerSign = Base64.getEncoder().encodeToString(hexStringToByteArray(partnerSignHex));
                headers.put("KC-API-PARTNER", brokerId);
                headers.put("KC-API-PARTNER-SIGN", partnerSign);
                headers.put("KC-BROKER-NAME", brokerName != null ? brokerName : brokerId);
                headers.put("KC-API-PARTNER-VERIFY", "true");
            } catch (final Exception e) {
                LOGGER.error("Failed to compute KC-API-PARTNER-SIGN: " + e.getMessage());
            }
        }
        return headers;
    }

    private String generateSignature(final String timestamp, final String method, final String requestPath, final String body) {
        return hmacSha256Base64(timestamp + method + requestPath + body);
    }

    private String computeEncodedPassphrase(final String passphrase) {
        return hmacSha256Base64(passphrase);
    }

    private String hmacSha256Base64(final String message) {
        try {
            final String hex = HMAC.hmacSha256(message, apiSecret);
            final byte[] bytes = hexStringToByteArray(hex);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (final Exception e) {
            LOGGER.error("KuCoin HMAC-SHA256 failed: " + e.getMessage());
            throw new RuntimeException("KuCoin HMAC-SHA256 failed", e);
        }
    }

    private static byte[] hexStringToByteArray(final String hex) {
        final int len = hex.length();
        final byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    // ==============================================================================================
    // Private — Utility
    // ==============================================================================================

    private void handleKuCoinError(final String responseBody) {
        if (responseBody == null) return;
        final String code = JsonHelper.minExtract(responseBody, "code");
        final String msg = JsonHelper.minExtract(responseBody, "msg");
        if ("400002".equals(code)) {
            LOGGER.error("KuCoin timestamp error - resyncing server time");
            syncServerTime(false);
        } else {
            LOGGER.error("KuCoin API error: code=" + code + ", msg=" + msg);
        }
    }

    private static String toKuCoinSymbol(final String symbol) {
        if (symbol == null || symbol.length() < 4) return symbol;
        return symbol.substring(0, symbol.length() - 4) + "-" + symbol.substring(symbol.length() - 4);
    }

    private static String toKuCoinFuturesSymbol(final String symbol) {
        if (symbol == null) return symbol;
        if (symbol.endsWith("USDT")) return symbol + "M";
        return symbol;
    }

    private void sleepBeforeRetry(final int attempt) {
        if (attempt < BULLET_PRIVATE_RETRIES) {
            try {
                Thread.sleep(BULLET_PRIVATE_RETRY_DELAY_MS);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
