package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitmart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.liquidity.DelistedSymbolCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.HMAC;
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class BitmartRestClient {
    private static final CustomLogger LOGGER = CustomLogger.getLogger(BitmartRestClient.class);
    private static final String REST_API_BASE = "https://api-cloud.bitmart.com";
    private static final String REST_API_V2_BASE = "https://api-cloud-v2.bitmart.com";


    private static final int PROXY_PORT = 8888;
    private final String apiKey;
    private final String secretKey;
    private final String memo;
    private final ExchangeSubscription subscription;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // symbols no longer listed (delisted pairs disappear from both lists); first check starts from the saved pairs
    private final DelistedSymbolCache.ListedTracker listedTracker = new DelistedSymbolCache.ListedTracker();

    public BitmartRestClient(final String apiKey, final String secretKey, final String memo, final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.memo = memo;
        this.subscription = subscription;
    }

    public String getSpotBalanceSnapshot() {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "GET";
            final String requestPath = "/account/v1/wallet";
            final String queryString = "";
            final String body = "";

            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved spot balance snapshot");
                return response.getData();
            } else {
                LOGGER.warn("Spot Balance Bootstrap failed with status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Spot Balance Bootstrap failed: " + e.getMessage());
        }
        return null;
    }

    public String getFutureBalanceSnapshot() {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "GET";
            final String requestPath = "/contract/private/assets-detail";
            final String queryString = "";
            final String body = "";

            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_V2_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved futures balance snapshot");
                return response.getData();
            } else {
                LOGGER.warn("Futures Balance Bootstrap failed with status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Futures Balance Bootstrap failed: " + e.getMessage());
        }
        return null;
    }

    public boolean sendSpotOrderREST(final Order order, final String symbol, final String side, final String orderType,
                                     final String timeInForce, final String size, final String price, final String clientOrderId) {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "POST";
            final String requestPath = "/spot/v2/submit_order";
            final String queryString = "";

            final StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append("{");
            bodyBuilder.append("\"symbol\":\"").append(symbol).append("\",");
            bodyBuilder.append("\"side\":\"").append(side.toLowerCase()).append("\",");
            bodyBuilder.append("\"type\":\"").append(orderType.toLowerCase()).append("\",");
            bodyBuilder.append("\"size\":\"").append(size).append("\"");
            if (price != null && !"market".equalsIgnoreCase(orderType)) {
                bodyBuilder.append(",\"price\":\"").append(price).append("\"");
            }
            if (clientOrderId != null) {
                bodyBuilder.append(",\"client_order_id\":\"").append(clientOrderId).append("\"");
            }
            bodyBuilder.append("}");

            final String body = bodyBuilder.toString();
            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Sending spot order REST for symbol: " + symbol + " side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                if (resp != null && resp.getCode() == 400) {
                    final String json = resp.getData();
                    final String retMsg = minExtract(json, "message");
                    LOGGER.error("Bitmart spot order failed: " + retMsg);

                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }
                    executionMessage.setClOrdId(clientOrderId);
                    executionMessage.setError(retMsg);
                    executionMessage.setExecType(ExecType.REJECTED);
                    subscription.updateExecutionReport(executionMessage);
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);
                }
                LOGGER.warn("REST spot order status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            final String code = minExtract(json, "code");

            if (!"1000".equals(code)) {
                final String message = minExtract(json, "message");
                LOGGER.warn("Spot order failed with code: " + code + ", message: " + message);
                return false;
            }

            final String dataJson = minExtract(json, "data");
            if (dataJson == null) {
                LOGGER.warn("No data object found in response: " + json);
                return false;
            }

            LOGGER.info("Spot order created with orderId: " + order.getOrderId() + " clientOrderId: " + clientOrderId);
            return true;

        } catch (final Exception e) {
            LOGGER.error("REST spot order failed: " + e.getMessage());
            return false;
        }
    }

    public boolean sendFutureOrderREST(final Order order, final String symbol, final String side,
                                       final String orderType, final String timeInForce, final String size, final String price,
                                       final String clientOrderId) {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "POST";
            final String requestPath = "/contract/private/submit-order";
            final String queryString = "";

            final StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append("{");
            bodyBuilder.append("\"symbol\":\"").append(symbol).append("\",");
            bodyBuilder.append("\"side\":").append(getFutureSideValue(side, order.isReduceOnly())).append(",");
            bodyBuilder.append("\"type\":\"").append(orderType.toLowerCase()).append("\",");
            bodyBuilder.append("\"mode\":").append(getOrderMode(timeInForce)).append(",");
            bodyBuilder.append("\"size\":").append(order.getQty());
            if (price != null && !"market".equalsIgnoreCase(orderType)) {
                bodyBuilder.append(",\"price\":\"").append(price).append("\"");
            }
            if (clientOrderId != null) {
                bodyBuilder.append(",\"client_order_id\":\"").append(clientOrderId).append("\"");
            }
            bodyBuilder.append("}");

            final String body = bodyBuilder.toString();
            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_V2_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Sending future order REST for symbol: " + symbol + " side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                if (resp != null && resp.getCode() == 400) {
                    final String json = resp.getData();
                    final String retMsg = minExtract(json, "message");
                    LOGGER.error("Bitmart future order failed: " + retMsg);

                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }
                    executionMessage.setClOrdId(clientOrderId);
                    executionMessage.setError(retMsg);
                    executionMessage.setExecType(ExecType.REJECTED);
                    subscription.updateExecutionReport(executionMessage);
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);
                }
                LOGGER.warn("REST future order status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            final String code = minExtract(json, "code");

            if (!"1000".equals(code)) {
                final String message = minExtract(json, "message");
                LOGGER.warn("Future order failed with code: " + code + ", message: " + message);
                return false;
            }

            final String dataJson = minExtract(json, "data");
            final String orderId = minExtract(dataJson, "order_id");
            final long orderIdLong = parseLongSafe(orderId);
            order.setGroupAssetId(orderIdLong); //TODO it is require to have orderId to fetch order details so for now, we set orderid to the setGroupAssetId
            subscription.updateOrder(clientOrderId,order);
            if (dataJson == null) {
                LOGGER.warn("No data object found in response: " + json);
                return false;
            }

            LOGGER.info("Future order created with orderId: " + order.getOrderId() + " clientOrderId: " + clientOrderId);
            return true;

        } catch (final Exception e) {
            LOGGER.error("REST future order failed: " + e.getMessage());
            return false;
        }
    }

    public boolean querySpotOrderStatus(final Order order, final String symbol, final String clientOrderId) {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "POST";
            final String requestPath = "/spot/v4/query/client-order";
            final String queryString = "";

            final String body = "{\"clientOrderId\":\"" + clientOrderId + "\"}";

            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Querying Bitmart spot order status for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Bitmart spot order status query failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            LOGGER.debug("Full response: " + json);

            final String code = minExtract(json, "code");
            if ("1000".equals(code)) {
                final String dataJson = extractJsonValue(json, "data");
                if (dataJson != null) {
                    populateSpotOrderFromJson(order, dataJson, clientOrderId);
                }
            } else {
                final String msg = minExtract(json, "message");
                LOGGER.warn("Bitmart spot order status query failed with code: " + code + ", msg: " + msg);
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("Bitmart spot order status query failed: " + e.getMessage());
            return false;
        }
    }

    public boolean queryFuturesOrderStatus(final Order order, final String symbol, final String clientOrderId) {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "GET";
            final String requestPath = "/contract/private/order";

            final String queryString = "symbol=" + order.getSymbol() + "&order_id=" + order.getGroupAssetId();
            final String body = "";

            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_V2_BASE + requestPath + "?" + queryString;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Querying Bitmart futures order status for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Bitmart futures order status query failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            LOGGER.debug("Full response: " + json);

            final String code = minExtract(json, "code");
            if ("1000".equals(code)) {
                final String dataJson = extractJsonValue(json, "data");
                if (dataJson != null) {
                    populateFuturesOrderFromJson(order, dataJson, clientOrderId);
                }
            } else {
                final String msg = minExtract(json, "message");
                LOGGER.warn("Bitmart futures order status query failed with code: " + code + ", msg: " + msg);
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("Bitmart futures order status query failed: " + e.getMessage());
            return false;
        }
    }

    public String getPositionInfo() {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "GET";
            final String requestPath = "/contract/private/position";
            final String queryString = "";
            final String body = "";

            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_V2_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved position info");
                return response.getData();
            } else {
                LOGGER.warn("Get position info failed with status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Get position info failed: " + e.getMessage(), e);
        }
        return null;
    }

    public boolean transferSpotToFutures(final String currency, final String amount) {
        return transferBalance(currency, amount, "spot", "contract");
    }

    public boolean transferFuturesToSpot(final String currency, final String amount) {
        return transferBalance(currency, amount, "contract", "spot");
    }

    private boolean transferBalance(final String currency, final String amount, final String from, final String to) {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "POST";
            final String requestPath = "/account/v1/transfer-contract";
            final String queryString = "";

            final String body = "{\"currency\":\"" + currency + "\",\"amount\":\"" + amount + "\",\"type\":\"" + from + "_to_" + to + "\"}";

            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_V2_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Transferring balance: currency=" + currency + " amount=" + amount + " from=" + from + " to=" + to);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp != null && resp.getCode() == 200) {
                final String json = resp.getData();
                final String code = minExtract(json, "code");

                if ("1000".equals(code)) {
                    final String trace = minExtract(json, "trace");
                    LOGGER.info("Balance transfer successful: trace=" + trace + " currency=" + currency + " amount=" + amount);
                    return true;
                } else {
                    final String message = minExtract(json, "message");
                    LOGGER.warn("Balance transfer failed with code: " + code + ", message: " + message);
                    return false;
                }
            } else {
                LOGGER.warn("Balance transfer status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("Balance transfer failed: " + e.getMessage());
            return false;
        }
    }

    public boolean cancelSpotOrderRest(final Order order, final String symbol, final String clientOrderId) {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "POST";
            final String requestPath = "/spot/v3/cancel_order";
            final String queryString = "";

            final String body = "{\"symbol\":\"" + symbol + "\",\"client_order_id\":\"" + clientOrderId + "\"}";

            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Cancelling spot order for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("REST cancel spot order failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            final String code = minExtract(json, "code");
            if ("1000".equals(code)) {
                LOGGER.info("Successfully cancelled spot order for clientOrderId: " + clientOrderId);
                order.setRejected(true);
                return true;
            } else {
                final String message = minExtract(json, "message");
                LOGGER.warn("Cancel spot order failed with code: " + code + ", message: " + message);
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("REST cancel spot order failed: " + e.getMessage());
            return false;
        }
    }

    public boolean cancelFuturesOrderRest(final Order order, final String symbol, final String clientOrderId) {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "POST";
            final String requestPath = "/contract/private/cancel-order";
            final String queryString = "";

            final String body = "{\"symbol\":\"" + symbol + "\",\"client_order_id\":\"" + clientOrderId + "\"}";

            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_V2_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Cancelling futures order for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("FUTURES cancel order failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            final String code = minExtract(json, "code");
            if ("1000".equals(code)) {
                LOGGER.info("Successfully cancelled futures order for clientOrderId: " + clientOrderId);
                order.setRejected(true);
                return true;
            } else {
                final String message = minExtract(json, "message");
                LOGGER.warn("Cancel futures order failed with code: " + code + ", message: " + message);
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("FUTURES cancel order failed: " + e.getMessage());
            return false;
        }
    }

    public String getAllOpenSpotOrders() {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "GET";
            final String requestPath = "/spot/v2/orders";
            final String queryString = "";
            final String body = "";

            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Get all open spot orders failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return null;
            }
            LOGGER.debug("Successfully retrieved all open spot orders");
            return resp.getData();
        } catch (final Exception e) {
            LOGGER.error("Get all open spot orders failed: " + e.getMessage());
            return null;
        }
    }

    public String getAllOpenFuturesOrders() {
        try {
            final String timestamp = String.valueOf(Instant.now().toEpochMilli());
            final String method = "GET";
            final String requestPath = "/contract/private/current-plan-order";
            final String queryString = "";
            final String body = "";

            final String signature = generateSignature(timestamp, method, requestPath, queryString, body);
            final String url = REST_API_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BM-KEY", apiKey);
            headers.put("X-BM-SIGN", signature);
            headers.put("X-BM-TIMESTAMP", timestamp);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Get all open futures orders failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return null;
            }
            LOGGER.debug("Successfully retrieved all open futures orders");
            return resp.getData();
        } catch (final Exception e) {
            LOGGER.error("Get all open futures orders failed: " + e.getMessage());
            return null;
        }
    }

    private String generateSignature(final String timestamp, final String method, final String requestPath,
                                     final String queryString, final String body) {
        try {
            final String preHashString = timestamp + "#" + memo + "#" + body;
            return HMAC.hmacSha256(preHashString, secretKey);
        } catch (Exception e) {
            LOGGER.error("Failed to generate signature: " + e.getMessage());
            throw new RuntimeException("Signature generation failed", e);
        }
    }

    public String getFutureSymbolDetails() {
        try {
            final String requestPath = "/contract/public/details";
            final String url = REST_API_V2_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Fetching future symbol details");
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved future symbol details");
                return response.getData();
            } else {
                LOGGER.warn("getFutureSymbolDetails: HTTP error: " + (response != null ? response.getCode() : "null"));
            }
        } catch (final Exception e) {
            LOGGER.error("getFutureSymbolDetails failed: " + e.getMessage(), e);
        }
        return null;
    }

    public String getSpotSymbolDetails() {
        try {
            final String url = REST_API_BASE + "/spot/v1/symbols/details";
            final Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Fetching spot symbol details");
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved spot symbol details");
                return response.getData();
            } else {
                LOGGER.warn("getSpotSymbolDetails: HTTP error: " + (response != null ? response.getCode() : "null"));
            }
        } catch (final Exception e) {
            LOGGER.error("getSpotSymbolDetails failed: " + e.getMessage());
        }
        return null;
    }

    /** Perpetuals Delisted, past or upcoming delist_time, or no longer listed; dated futures are skipped. Null on failure. */
    public List<DelistedSymbol> getFuturesDelistedSymbols() {
        final String json = getFutureSymbolDetails();
        return json == null ? null : toDelistedSymbols(json, true, System.currentTimeMillis());
    }

    /** Spot pairs not trading, past or upcoming planned_down_time, or no longer listed; null when the request failed. */
    public List<DelistedSymbol> getSpotDelistedSymbols() {
        final String json = getSpotSymbolDetails();
        return json == null ? null : toDelistedSymbols(json, false, System.currentTimeMillis());
    }

    // parses a /contract/public/details or /spot/v1/symbols/details response; null when it is not a valid list
    List<DelistedSymbol> toDelistedSymbols(final String json, final boolean futures, final long now) {
        final List<DelistedSymbol> delisted = new ArrayList<>();
        final Map<String, String[]> listed = new HashMap<>();
        try {
            final JsonNode root = MAPPER.readTree(json);
            final JsonNode symbols = root.path("data").path("symbols");
            if (root.path("code").asInt(-1) != 1000 || !symbols.isArray()) {
                LOGGER.warn("Bitmart " + (futures ? "contract" : "spot") + " details returned no symbols: " + json);
                return null;
            }
            for (final JsonNode item : symbols) {
                final String symbol = item.path("symbol").asText();
                listed.put(symbol, new String[] {item.path("base_currency").asText(), item.path("quote_currency").asText()});
                if (futures && item.path("product_type").asInt(1) != 1) {
                    continue; // product_type 2 = futures with a fixed expiry, not a delisting; still listed so a saved row is not marked removed
                }
                final String status = futures ? item.path("status").asText() : item.path("trade_status").asText();
                final long delistTime = toMillis(item.path(futures ? "delist_time" : "planned_down_time").asLong(0));
                // futures: Trading / Delisted; spot: trading, pre-trade = new listing
                final boolean trading = futures ? "Trading".equals(status) : "trading".equals(status) || "pre-trade".equals(status);
                final boolean stopped = !trading || (delistTime > 0 && delistTime <= now);
                if (stopped || delistTime > 0) {
                    final DelistedSymbol entry = new DelistedSymbol();
                    entry.setExchange(subscription.getExchange());
                    entry.setFutures(futures);
                    entry.setSymbol(symbol);
                    entry.setBase(item.path("base_currency").asText());
                    entry.setQuote(item.path("quote_currency").asText());
                    entry.setStatus(status);
                    entry.setTradingDisabled(stopped);
                    entry.setDelistTime(delistTime);
                    entry.setDetectedAt(now);
                    delisted.add(entry);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Bitmart " + (futures ? "contract" : "spot") + " details parse failed", e);
            return null;
        }
        final List<DelistedSymbol> removed = listedTracker.update(subscription.getExchange(), futures, listed, now);
        if (removed == null) {
            return null;
        }
        delisted.addAll(removed);
        return delisted;
    }

    // delist_time is documented as Unix time without unit; accept seconds as well as milliseconds
    static long toMillis(final long time) {
        return time > 0 && time < 100_000_000_000L ? time * 1000 : Math.max(time, 0);
    }

    public Ticker getSpotTicker(final String symbol) {
        try {
            // symbol format for spot: BTC_USDT
            final String url = REST_API_BASE + "/spot/quotation/v3/ticker?symbol=" + symbol;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get spot ticker failed for symbol: " + symbol + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }

            final String json = response.getData();
            final String code = minExtract(json, "code");
            if (!"1000".equals(code)) {
                LOGGER.warn("Get spot ticker failed for symbol: " + symbol + " code: " + code);
                return null;
            }

            final String dataJson = extractJsonValue(json, "data");
            if (dataJson == null) {
                LOGGER.warn("No data in spot ticker response for symbol: " + symbol);
                return null;
            }

            final double last         = parseDoubleSafe(minExtract(dataJson, "last"));
            final double bid          = parseDoubleSafe(minExtract(dataJson, "bid_px"));
            final double ask          = parseDoubleSafe(minExtract(dataJson, "ask_px"));
            final double high         = parseDoubleSafe(minExtract(dataJson, "high_24h"));
            final double low          = parseDoubleSafe(minExtract(dataJson, "low_24h"));
            final double open         = parseDoubleSafe(minExtract(dataJson, "open_24h"));
            final double baseVolume   = parseDoubleSafe(minExtract(dataJson, "v_24h"));
            final double quoteVolume  = parseDoubleSafe(minExtract(dataJson, "qv_24h"));
            final double bidSize      = parseDoubleSafe(minExtract(dataJson, "bid_sz"));
            final double askSize      = parseDoubleSafe(minExtract(dataJson, "ask_sz"));
            final long timestamp      = parseLongSafe(minExtract(dataJson, "ts"));
            // fluctuation is a fraction (e.g. -0.0093 = -0.93%); Ticker expects percentage units
            final double percentageChange = parseDoubleSafe(minExtract(dataJson, "fluctuation")) * 100.0;

            LOGGER.debug("Retrieved spot ticker for symbol: " + symbol + " last=" + last + " bid=" + bid + " ask=" + ask);

            return new Ticker(symbol, 0, open, last, bid, ask, high, low, baseVolume, quoteVolume, timestamp, bidSize, askSize, percentageChange);
        } catch (final Exception e) {
            LOGGER.error("Get spot ticker failed for symbol: " + symbol + " " + e.getMessage());
            return null;
        }
    }

    public Ticker getFutureTicker(final String symbol) {
        try {
            // symbol format for futures: BTCUSDT
            final String url = REST_API_V2_BASE + "/contract/public/details?symbol=" + symbol;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get future ticker failed for symbol: " + symbol + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }

            final String json = response.getData();
            final String code = minExtract(json, "code");
            if (!"1000".equals(code)) {
                LOGGER.warn("Get future ticker failed for symbol: " + symbol + " code: " + code);
                return null;
            }

            final String dataJson = extractJsonValue(json, "data");
            if (dataJson == null) {
                LOGGER.warn("No data in future ticker response for symbol: " + symbol);
                return null;
            }

            final String symbolsArray = extractJsonValue(dataJson, "symbols");
            if (symbolsArray == null) {
                LOGGER.warn("No symbols array in future ticker response for symbol: " + symbol);
                return null;
            }

            final String tickerJson = extractFirstOrderFromArray(symbolsArray);
            if (tickerJson == null) {
                LOGGER.warn("Empty symbols array in future ticker response for symbol: " + symbol);
                return null;
            }

            final double last        = parseDoubleSafe(minExtract(tickerJson, "last_price"));
            final double high        = parseDoubleSafe(minExtract(tickerJson, "high_24h"));
            final double low         = parseDoubleSafe(minExtract(tickerJson, "low_24h"));
            final double open        = 0.0; // not provided by this endpoint
            final double baseVolume  = parseDoubleSafe(minExtract(tickerJson, "volume_24h"));
            final double quoteVolume = parseDoubleSafe(minExtract(tickerJson, "turnover_24h"));
            final long timestamp     = System.currentTimeMillis();
            // change_24h is a fraction (e.g. -0.0004 = -0.04%); Ticker expects percentage units
            final double percentageChange = parseDoubleSafe(minExtract(tickerJson, "change_24h")) * 100.0;

            LOGGER.debug("Retrieved future ticker for symbol: " + symbol + " last=" + last);

            // Futures contract details endpoint does not provide bid/ask
            return new Ticker(symbol, 0, open, last, 0.0, 0.0, high, low, baseVolume, quoteVolume, timestamp, 0.0, 0.0, percentageChange);
        } catch (final Exception e) {
            LOGGER.error("Get future ticker failed for symbol: " + symbol + " " + e.getMessage());
            return null;
        }
    }

    private String extractJsonValue(final String json, final String key) {
        if (json == null || key == null) {
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

    private int getFutureSideValue(final String side, final boolean reduceOnly) {
        final boolean isBuy = "buy".equalsIgnoreCase(side);

        if (isBuy && !reduceOnly) {
            return 1; // Buy open
        } else if (isBuy && reduceOnly) {
            return 2; // Buy close
        } else if (!isBuy && !reduceOnly) {
            return 3; // Sell open
        } else {
            return 4; // Sell close
        }
    }

    //TBD We need to verify this logic
    public OrdStatus getOrderStatus(int stateCode, int dealSize, int size) {
        if (stateCode == 1) {
            return OrdStatus.NEW;
        } else if (stateCode == 2) {
            return OrdStatus.ACCEPTED_FOR_BIDDING;
        } else if (stateCode == 3 && dealSize == size) {
            return OrdStatus.FILLED;
        } else if (stateCode == 4 && dealSize < size && dealSize != 0) {
            return OrdStatus.PARTIALLY_FILLED;
        } else if (stateCode == 4 && dealSize == 0) {
            return OrdStatus.REJECTED;
        } else {
            return OrdStatus.NULL_VAL;
        }
    }

    private OrdStatus getOrdStatus(String orderStatus) {
        if (OrdStatus.NEW.toString().equalsIgnoreCase(orderStatus)) {
            return OrdStatus.NEW;
        } else if (OrdStatus.PARTIALLY_FILLED.toString().equalsIgnoreCase(orderStatus)) {
            return OrdStatus.PARTIALLY_FILLED; // FOK
        } else if (OrdStatus.FILLED.toString().equalsIgnoreCase(orderStatus)) {
            return OrdStatus.FILLED; // IOC
        } else if (OrdStatus.CANCELED.toString().equalsIgnoreCase(orderStatus)) {
            return OrdStatus.CANCELED; // IOC
        } else {
            return OrdStatus.NULL_VAL;
        }
    }

    private int getOrderMode(String timeInForce) {

        if (TimeInForce.GOOD_TILL_CANCEL.toString().equalsIgnoreCase(timeInForce)) {
            return 1;
        } else if (TimeInForce.FILL_OR_KILL.toString().equalsIgnoreCase(timeInForce)) {
            return 2; // FOK
        } else if (TimeInForce.IMMEDIATE_OR_CANCEL.toString().equalsIgnoreCase(timeInForce)) {
            return 3; // IOC
        } else {
            return 4; // Maker Only
        }
    }

    private void populateSpotOrderFromJson(final Order order, final String orderJson, final String clientOrderId) {
        // Extract all order fields from Bitmart response
        final String orderStatus = minExtract(orderJson, "state");
        final String filledSize = minExtract(orderJson, "filledSize");
        final String priceAvg = minExtract(orderJson, "priceAvg");
        final String cancelSource = minExtract(orderJson, "cancelSource");
        final String updateTime = minExtract(orderJson, "updateTime");
        final String filledNotional = minExtract(orderJson, "filledNotional");

        // Parse numeric values safely
        final OrdStatus mappedOrderStatus = mapSpotOrderStatus(orderStatus);
        final double filledSizeDouble = parseDoubleSafe(filledSize);
        final double priceAvgDouble = parseDoubleSafe(priceAvg);
        final long updateTimeLong = parseLongSafe(updateTime);

        // Convert to order's scale
        final long filledSizeLong = MbxMath.changeScale(filledSizeDouble, order.getQtyScale());
        final long priceAvgLong = MbxMath.changeScale(priceAvgDouble, order.getPriceScale());
        final long leavesQtyLong = order.getQty() - filledSizeLong;

        LOGGER.debug("Extracted Bitmart spot order details - orderId: " + order.getOrderId() + ", status: " + orderStatus +
                ", filledSize: " + filledSize + ", priceAvg: " + priceAvg + ", filledNotional: " + filledNotional +
                ", cancelSource: " + cancelSource + ", side: " + order.getSide());

        // Create or update execution report with all extracted fields
        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                    0, 0, 0, 0, order.getSide(), 0);
        }

        // Update order object based on status
        if ("filled".equalsIgnoreCase(orderStatus)) {
            LOGGER.debug("Bitmart spot order status - FILLED for clientOrderId: " + clientOrderId +
                    ", filledSize: " + filledSize + ", priceAvg: " + priceAvg +
                    ", filledNotional: " + filledNotional);
            order.setExecuted(true);
        } else if ("cancelled".equalsIgnoreCase(orderStatus)) {
            LOGGER.debug("Bitmart spot order status - CANCELLED for clientOrderId: " + clientOrderId + 
                    (cancelSource != null && !cancelSource.isBlank() ? ", reason: " + cancelSource : ""));
            order.setRejected(true);
            if (cancelSource != null && !cancelSource.isBlank()) {
                order.setError(cancelSource);
            }
        } else if ("rejected".equalsIgnoreCase(orderStatus)) {
            LOGGER.debug("Bitmart spot order status - REJECTED for clientOrderId: " + clientOrderId);
            order.setRejected(true);
        }

        // Set execution report fields
        executionMessage.setClOrdId(clientOrderId);
        executionMessage.setOrdStatus(mappedOrderStatus);
        executionMessage.setInputTime(updateTimeLong);
        executionMessage.setPrice(order.getPrice());
        executionMessage.setPriceScale(order.getPriceScale());
        executionMessage.setOrderQty(order.getQty());
        executionMessage.setOrderQtyScale(order.getQtyScale());
        executionMessage.setCumQty(filledSizeLong);
        executionMessage.setCumQtyScale(order.getQtyScale());
        executionMessage.setLeavesQty(leavesQtyLong);
        executionMessage.setLeavesQtyScale(order.getQtyScale());
        executionMessage.setAvgPx(priceAvgLong);
        executionMessage.setError(cancelSource);

        LOGGER.debug("Updating execution report for clientOrderId: " + clientOrderId + " with status: " + orderStatus);
        subscription.updateExecutionReport(executionMessage);

        subscription.updateOrder(clientOrderId, order);
    }

    private OrdStatus mapSpotOrderStatus(final String state) {
        if (state == null || state.isEmpty())
            return OrdStatus.NULL_VAL;
        return switch (state.toLowerCase()) {
            case "new" -> OrdStatus.NEW;
            case "partially_filled" -> OrdStatus.PARTIALLY_FILLED;
            case "filled" -> OrdStatus.FILLED;
            case "cancelled" -> OrdStatus.CANCELED;
            default -> OrdStatus.REJECTED;
        };
    }


    private void populateFuturesOrderFromJson(final Order order, final String orderJson, final String clientOrderId) {
        // Extract all order fields from Bitmart response
        final String orderStatus = minExtract(orderJson, "state");
        final String dealSize = minExtract(orderJson, "deal_size");
        final String dealAvgPrice = minExtract(orderJson, "deal_avg_price");
        final String price = minExtract(orderJson, "price");
        final String size = minExtract(orderJson, "size");
        final String orderType = minExtract(orderJson, "type");
        final String side = minExtract(orderJson, "side"); // 1=buy, 2=sell
        final String leverage = minExtract(orderJson, "leverage");
        final String openType = minExtract(orderJson, "open_type");
        final String positionMode = minExtract(orderJson, "position_mode");
        final String createTime = minExtract(orderJson, "create_time");

        // Parse numeric values safely
        final OrdStatus mappedOrderStatus = mapFuturesOrderStatus(orderStatus);
        final double dealSizeDouble = parseDoubleSafe(dealSize);
        final double dealAvgPriceDouble = parseDoubleSafe(dealAvgPrice);
        final double priceDouble = parseDoubleSafe(price);
        final double sizeDouble = parseDoubleSafe(size);
        final int sideInt = Integer.parseInt(side != null ? side : "0");
        final long createTimeLong = parseLongSafe(createTime);

        // Convert to order's scale
        final long dealSizeLong = MbxMath.changeScale(dealSizeDouble, order.getQtyScale());
        final long dealAvgPriceLong = MbxMath.changeScale(dealAvgPriceDouble, order.getPriceScale());
        final long priceLong = MbxMath.changeScale(priceDouble, order.getPriceScale());
        final long sizeLong = MbxMath.changeScale(sizeDouble, order.getQtyScale());
        final long leavesQtyLong = sizeLong - dealSizeLong;

        final Side sideObj = sideInt == 1 ? Side.BUY : Side.SELL;

        LOGGER.debug("Extracted Bitmart futures order details - orderId: , status: " + orderStatus +
                ", dealSize: " + dealSize + ", dealAvgPrice: " + dealAvgPrice + ", price: " + price +
                ", size: " + size + ", orderType: " + orderType + ", side: " + (sideInt == 1 ? "BUY" : "SELL") +
                ", leverage: " + leverage + ", openType: " + openType + ", positionMode: " + positionMode);

        // Create or update execution report with all extracted fields
        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                    0, 0, 0, 0, sideObj, 0);
        }

        // Update order object based on status
        if ("6".equals(orderStatus)) {
            LOGGER.debug("Bitmart futures order status - FILLED for clientOrderId: " + clientOrderId +
                    ", dealSize: " + dealSize + ", dealAvgPrice: " + dealAvgPrice);
            order.setExecuted(true);
        } else if ("4".equals(orderStatus)) {
            LOGGER.debug("Bitmart futures order status - CANCELLED for clientOrderId: " + clientOrderId);
            order.setRejected(true);
        } else if ("1".equals(orderStatus)) {
            LOGGER.debug("Bitmart futures order status - PENDING_NEW for clientOrderId: " + clientOrderId);
        }

        // Set execution report fields
        executionMessage.setClOrdId(clientOrderId);
        executionMessage.setOrdStatus(mappedOrderStatus);
        executionMessage.setInputTime(createTimeLong);
        executionMessage.setPrice(priceLong);
        executionMessage.setPriceScale(order.getPriceScale());
        executionMessage.setOrderQty(sizeLong);
        executionMessage.setOrderQtyScale(order.getQtyScale());
        executionMessage.setCumQty(dealSizeLong);
        executionMessage.setCumQtyScale(order.getQtyScale());
        executionMessage.setLeavesQty(leavesQtyLong);
        executionMessage.setLeavesQtyScale(order.getQtyScale());
        executionMessage.setAvgPx(dealAvgPriceLong);
        executionMessage.setAvgPxScale(order.getPriceScale());

        LOGGER.debug("Updating execution report for clientOrderId: " + clientOrderId + " with status: " + orderStatus +
                ", dealSize: " + dealSize + ", leavesQty: " + leavesQtyLong);
        subscription.updateExecutionReport(executionMessage);

        subscription.updateOrder(clientOrderId, order);
    }

    private OrdStatus mapFuturesOrderStatus(final String state) {
        if (state == null || state.isEmpty())
            return OrdStatus.NULL_VAL;
        return switch (state) {
            case "1" -> OrdStatus.PENDING_NEW;
            case "2" -> OrdStatus.NEW;
            case "4" -> OrdStatus.CANCELED;
            case "5" -> OrdStatus.PARTIALLY_FILLED;
            case "6" -> OrdStatus.FILLED;
            default -> OrdStatus.REJECTED;
        };
    }
}