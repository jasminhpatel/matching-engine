package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.DelistedSymbolCache;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.util.HMAC;
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;

import java.net.URLEncoder;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class BybitRestClient {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(BybitRestClient.class);
    private static final String MAINNET_BASE_URL = Context.getBybitUnifiedRest();
    private static final int PROXY_PORT = 8888;
    private static final ObjectMapper MAPPER = new ObjectMapper(); // todo use string parsing
    private static final int MAX_INSTRUMENT_PAGES = 20; // safety stop for the cursor paging
    private final DelistedSymbolCache.ListedTracker listedTracker = new DelistedSymbolCache.ListedTracker(); // spot pairs no longer listed
    private long lastAnnouncementTime; // publishTime of the newest delisting announcement already logged

    // Configuration and credentials - immutable after construction
    private final String apiKey;
    private final byte[] secretUtf8;
    private final ExchangeSubscription subscription;

    /**
     * Initializes ByBit REST client with API credentials
     *
     * @param apiKey       ByBit API key for authentication
     * @param secretUtf8   ByBit API secret in UTF-8 bytes for HMAC signature
     * @param subscription Liquidity subscription for order and balance management
     */
    public BybitRestClient(final String apiKey, final byte[] secretUtf8, final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.subscription = subscription;
        this.secretUtf8 = secretUtf8;
    }

    /**
     * Retrieves unified account wallet balance snapshot from ByBit
     *
     * @return JSON response string containing balance data, or null if failed
     */
    public String getBalanceSnapshot() {
        try {
            final long timestamp = System.currentTimeMillis();
            final String recv_window = "5000";
            final String queryString = "accountType=UNIFIED&timestamp=" + timestamp;
            final String signaturePayload = timestamp + apiKey + recv_window + queryString;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signaturePayload);

            final String url = MAINNET_BASE_URL + "/v5/account/wallet-balance?" + queryString;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BAPI-API-KEY", apiKey);
            headers.put("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.put("X-BAPI-RECV-WINDOW", recv_window);
            headers.put("X-BAPI-SIGN", signature);
            headers.put("Referer", subscription.getBrokerId());

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved ByBit spot balance snapshot");
                return response.getData();
            } else {
                LOGGER.warn("ByBit Spot Balance Bootstrap failed with status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("ByBit Spot Balance Bootstrap failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves all open positions for a specific settle coin (USDT/USDC)
     *
     * @param stableCoinSym The settle coin symbol (USDT or USDC)
     * @return JSON response string containing position data, or null if failed
     */
    public String getAllPositions(final String stableCoinSym) {
        try {
            final long timestamp = System.currentTimeMillis();
            final String recv_window = "5000";
            final String queryString = "category=linear&limit=200&settleCoin=" + stableCoinSym + "&timestamp=" + timestamp;
            final String signaturePayload = timestamp + apiKey + recv_window + queryString;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signaturePayload);

            final String url = MAINNET_BASE_URL + "/v5/position/list?" + queryString;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BAPI-API-KEY", apiKey);
            headers.put("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.put("X-BAPI-RECV-WINDOW", recv_window);
            headers.put("X-BAPI-SIGN", signature);
            headers.put("Referer", subscription.getBrokerId());

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved ByBit positions for " + stableCoinSym);
                return response.getData();
            } else {
                LOGGER.warn("ByBit positions retrieval failed with status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("ByBit positions retrieval failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Sends a futures order via REST API
     *
     * @param order        The order object to update with response data
     * @param symbol       Trading pair symbol
     * @param side         Order side (Buy/Sell)
     * @param type         Order type (Limit/Market)
     * @param timeInForce  Time in force (GTC/FOK/IOC)
     * @param quantityStr  Order quantity as string
     * @param priceStr     Order price as string
     * @param positionSide Position index (0 for one-way mode)
     * @param reduceOnly   Whether this is a reduce-only order
     * @return true if request was sent successfully, false otherwise
     */
    public boolean sendFutureOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce, final String quantityStr,
                                       final String priceStr, final String positionSide, final boolean reduceOnly) {
        final long timestamp = System.currentTimeMillis();
        final String recv_window = "5000";

        // Build JSON request body for futures order
        final StringBuilder requestBodyBuilder = new StringBuilder();
        requestBodyBuilder.append("{");
        requestBodyBuilder.append("\"category\":\"linear\",");
        requestBodyBuilder.append("\"symbol\":\"").append(symbol).append("\",");
        requestBodyBuilder.append("\"side\":\"").append(side).append("\",");
        requestBodyBuilder.append("\"orderType\":\"").append(type).append("\",");
        requestBodyBuilder.append("\"qty\":\"").append(quantityStr).append("\"");

        // Add price for limit orders only
        if (!"Market".equals(type)) {
            requestBodyBuilder.append(",\"price\":\"").append(priceStr).append("\"");
        }

        requestBodyBuilder.append(",\"timeInForce\":\"").append(timeInForce).append("\"");
        requestBodyBuilder.append(",\"orderLinkId\":\"").append(order.getClOrdId()).append("\"");
        // requestBodyBuilder.append(",\"positionIdx\":0"); //this require to set when using hedge mode

        if (reduceOnly) {
            requestBodyBuilder.append(",\"reduceOnly\":true");
        }
        requestBodyBuilder.append("}");

        final String requestBody = requestBodyBuilder.toString();
        final String signaturePayload = timestamp + apiKey + recv_window + requestBody;

        try {
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signaturePayload);
            final String url = MAINNET_BASE_URL + "/v5/order/create";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BAPI-API-KEY", apiKey);
            headers.put("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.put("X-BAPI-RECV-WINDOW", recv_window);
            headers.put("X-BAPI-SIGN", signature);
            headers.put("Content-Type", "application/json");
            headers.put("Referer", subscription.getBrokerId());

            LOGGER.debug("Sending ByBit future order REST for symbol: " + symbol + " side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("REST ByBit future order status=" + resp.getCode() + " body=" + resp.getData());
            }

            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                final String retCode = minExtract(json, "retCode");
                if ("0".equals(retCode)) {
                    // Extract order ID from successful response
                    final String resultSection = minExtract(json, "result");
                    if (resultSection != null) {
                        LOGGER.info("ByBit future order created with orderId: " + order.getOrderId());
                    }
                } else {
                    // Handle order rejection
                    final String retMsg = minExtract(json, "retMsg");
                    LOGGER.error("ByBit future order failed: " + retMsg);
                    // Create execution report for rejected order
                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }

                    executionMessage.setClOrdId(order.getClOrdId());
                    executionMessage.setError(retMsg);
                    executionMessage.setExecType(ExecType.REJECTED);
                    subscription.updateExecutionReport(executionMessage);
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST ByBit future order failed: " + e.getMessage());
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }

    /**
     * Sends a spot order via REST API
     *
     * @param order         The order object to update with response data
     * @param symbol        Trading pair symbol
     * @param side          Order side (Buy/Sell)
     * @param type          Order type (Limit/Market)
     * @param timeInForce   Time in force (GTC/FOK/IOC)
     * @param quantityStr   Order quantity as string
     * @param priceStr      Order price as string
     * @param clientOrderId Client-specified order ID
     * @return true if request was sent successfully, false otherwise
     */
    public boolean sendSpotOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce,
                                     final String quantityStr, final String priceStr, final String clientOrderId) {
        final long timestamp = System.currentTimeMillis();
        final String recv_window = "5000";

        // Build JSON request body for spot order
        final StringBuilder requestBodyBuilder = new StringBuilder();
        requestBodyBuilder.append("{");
        requestBodyBuilder.append("\"category\":\"spot\",");
        requestBodyBuilder.append("\"symbol\":\"").append(symbol).append("\",");
        requestBodyBuilder.append("\"side\":\"").append(side).append("\",");
        requestBodyBuilder.append("\"orderType\":\"").append(type).append("\",");
        requestBodyBuilder.append("\"qty\":\"").append(quantityStr).append("\"");

        // Add price for limit orders only
        if (!"Market".equals(type)) {
            requestBodyBuilder.append(",\"price\":\"").append(priceStr).append("\"");
        }

        requestBodyBuilder.append(",\"timeInForce\":\"").append(timeInForce).append("\"");
        requestBodyBuilder.append(",\"orderLinkId\":\"").append(clientOrderId).append("\"");
        requestBodyBuilder.append("}");

        final String requestBody = requestBodyBuilder.toString();
        final String signaturePayload = timestamp + apiKey + recv_window + requestBody;

        try {
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signaturePayload);
            final String url = MAINNET_BASE_URL + "/v5/order/create";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BAPI-API-KEY", apiKey);
            headers.put("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.put("X-BAPI-RECV-WINDOW", recv_window);
            headers.put("X-BAPI-SIGN", signature);
            headers.put("Content-Type", "application/json");
            headers.put("Referer", subscription.getBrokerId());

            LOGGER.debug("Sending ByBit spot order REST for symbol: " + symbol + " side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("REST ByBit spot order status=" + resp.getCode() + " body=" + resp.getData());
                return true;
            }

            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                final String retCode = minExtract(json, "retCode");
                if ("0".equals(retCode)) {
                    // Extract order ID from successful response
                    final String resultSection = minExtract(json, "result");
                    if (resultSection != null) {
                        LOGGER.info("ByBit spot order created with orderId: " + order.getOrderId());
                    }
                } else {
                    // Handle order rejection
                    final String retMsg = minExtract(json, "retMsg");
                    LOGGER.error("ByBit spot order failed: " + retMsg);
                    // Create execution report for rejected order
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
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST ByBit spot order failed: " + e.getMessage());
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }

    /**
     * Queries the status of a spot order by client-specified order ID
     *
     * @param order         The order object to update with response data
     * @param symbol        Trading pair symbol
     * @param clientOrderId Client-specified order ID
     * @param iteration
     * @return true if query was successful, false otherwise
     */
    public boolean querySpotOrderStatus(final Order order, final String symbol, final String clientOrderId, int iteration) {
        try {
            final long timestamp = System.currentTimeMillis();
            final String recv_window = "5000";
            final String queryString = "category=spot&symbol=" + symbol + "&orderLinkId=" + clientOrderId + "&timestamp=" + timestamp;
            final String signaturePayload = timestamp + apiKey + recv_window + queryString;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signaturePayload);

            final String url = MAINNET_BASE_URL + "/v5/order/realtime?" + queryString;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BAPI-API-KEY", apiKey);
            headers.put("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.put("X-BAPI-RECV-WINDOW", recv_window);
            headers.put("X-BAPI-SIGN", signature);
            headers.put("Referer", subscription.getBrokerId());

            LOGGER.debug("Querying ByBit spot order status for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("REST ByBit spot order status query failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }
            final String json = resp.getData();
            LOGGER.debug("Iteration: " + iteration + " REST ByBit spot order status query Full response: " + json);

            final String retCode = minExtract(json, "retCode");
            if ("0".equals(retCode)) {
                final String resultSection = extractJsonValue(json, "result");
                if (resultSection != null) {
                    LOGGER.debug("Extracted result section: " + resultSection);
                    final String orderData = extractFirstOrderFromResult(resultSection);
                    if (orderData != null) {
                        populateOrderFromJson(order, orderData, clientOrderId);
                    }
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST ByBit spot order status query failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Populates order object and execution report with all fields extracted from ByBit order JSON
     *
     * @param order         The order object to populate
     * @param orderJson     The complete order JSON object as string
     * @param clientOrderId The client order ID
     */
    private void populateOrderFromJson(final Order order, final String orderJson, final String clientOrderId) {

        // Extract all order fields
        final String status = minExtract(orderJson, "orderStatus");
        final String cumExecQty = minExtract(orderJson, "cumExecQty");
        final String leavesQty = minExtract(orderJson, "leavesQty");
        final String avgPrice = minExtract(orderJson, "avgPrice");
        final String cumExecValue = minExtract(orderJson, "cumExecValue");
        final String cumExecFee = minExtract(orderJson, "cumExecFee");
        final String cancelType = minExtract(orderJson, "cancelType");
        final String rejectReason = minExtract(orderJson, "rejectReason");
        final String reduceOnly = minExtract(orderJson, "reduceOnly");

        // Extract filled-specific fields
        final String basePrice = minExtract(orderJson, "basePrice");
        final String leavesValue = minExtract(orderJson, "leavesValue");
        final String cumFeeDetailSection = extractJsonValue(orderJson, "cumFeeDetail");
        final String updatedTime = extractJsonValue(orderJson, "updatedTime");

        // Extract fee currency from cumFeeDetail - get the key name (e.g., "XRP")
        final String feeCurrency = extractFeeCurrencyFromDetail(cumFeeDetailSection);

        final OrdStatus orderStatus = getByBitOrderStatus(status);
        final double cumExecQtyDouble = parseDoubleSafe(cumExecQty);
        final double leavesQtyDouble = parseDoubleSafe(leavesQty);
        final double avgPriceDouble = parseDoubleSafe(avgPrice);
        final double cumExecValueDouble = parseDoubleSafe(cumExecValue);
        final double cumExecFeeDouble = parseDoubleSafe(cumExecFee);
        final long updatedTimeLong = parseLongSafe(updatedTime);

        final long cumExecQtyLong = MbxMath.changeScale(cumExecQtyDouble, order.getQtyScale());
        final long leavesQtyLong = MbxMath.changeScale(leavesQtyDouble, order.getQtyScale());
        final long avgPriceLong = MbxMath.changeScale(avgPriceDouble, order.getPriceScale());
        final long cumExecValueLong = MbxMath.changeScale(cumExecValueDouble, order.getPriceScale());


        LOGGER.debug("Extracted order details - orderId: " + order.getOrderId() + ", status: " + status +
                ", cumExecQty: " + cumExecQty + ", leavesQty: " + leavesQty + ", avgPrice: " + avgPrice + ", cumExecValue: " + cumExecValue +
                ", cumExecFee: " + cumExecFee + ", feeCurrency: " + feeCurrency);

        // Create or update execution report with all extracted fields
        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                    0, 0, 0, 0, order.getSide(), 0);
        }

        // Update order object
        if ("Filled".equalsIgnoreCase(status)) {
            LOGGER.debug("ByBit order status - FILLED for clientOrderId: " + clientOrderId +
                    ", cumExecQty: " + cumExecQty + ", avgPrice: " + avgPrice +
                    ", cumExecValue: " + cumExecValue + ", cumExecFee: " + cumExecFee +
                    ", leavesValue: " + leavesValue + ", basePrice: " + basePrice);
            order.setExecuted(true);
            // Convert commission with proper instrument scale
            final Instrument feesInstrument = InstrumentCache.getBySymbol(feeCurrency == null || feeCurrency.isBlank() ? getDefaultQuotedCurrency(order.getSymbol()) : feeCurrency);

            if (feesInstrument != null) {
                final long feesLong = MbxMath.changeScale(cumExecFeeDouble, feesInstrument.getQuantityScale());
                executionMessage.setFeeAccumulatedQuantity(feesLong);
                executionMessage.setFeePositionId(feesInstrument.getId());
            }

        } else if ("Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
            LOGGER.debug("ByBit order status - " + status + " for clientOrderId: " + clientOrderId);
            order.setRejected(true);
            order.setError(rejectReason);
        }


        executionMessage.setClOrdId(clientOrderId);
        executionMessage.setOrdStatus(orderStatus);
        executionMessage.setTimeInForce(order.getTimeInForce());
        executionMessage.setInputTime(updatedTimeLong);
        executionMessage.setPrice(avgPriceLong);
        executionMessage.setPriceScale(order.getPriceScale());
        executionMessage.setOrderQty(order.getQty());
        executionMessage.setOrderQtyScale(order.getQtyScale());
        executionMessage.setCumQty(cumExecQtyLong);
        executionMessage.setLeavesQty(leavesQtyLong);
        executionMessage.setAvgPx(avgPriceLong);


        LOGGER.debug("Updating execution report for clientOrderId: " + clientOrderId + " with status: " + status);
        subscription.updateOrder(clientOrderId, order);
        subscription.updateExecutionReport(executionMessage);
    }

    /**
     * Queries the status of a futures order by client-specified order ID
     *
     * @param order         The order object to update with response data
     * @param symbol        Trading pair symbol
     * @param clientOrderId Client-specified order ID
     * @param iteration
     * @return true if query was successful, false otherwise
     */
    public boolean queryFuturesOrderStatus(final Order order, final String symbol, final String clientOrderId, int iteration) {
        try {
            final long timestamp = System.currentTimeMillis();
            final String recv_window = "5000";
            final String queryString = "category=linear&symbol=" + symbol + "&orderLinkId=" + clientOrderId + "&timestamp=" + timestamp;
            final String signaturePayload = timestamp + apiKey + recv_window + queryString;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signaturePayload);

            final String url = MAINNET_BASE_URL + "/v5/order/realtime?" + queryString;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BAPI-API-KEY", apiKey);
            headers.put("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.put("X-BAPI-RECV-WINDOW", recv_window);
            headers.put("X-BAPI-SIGN", signature);
            headers.put("Referer", subscription.getBrokerId());

            LOGGER.debug("Querying ByBit futures order status for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("ByBit FUTURES order status query failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }
            final String json = resp.getData();
            LOGGER.debug("Iteration:" + iteration + " Querying ByBit futures order Full response: " + json);

            final String retCode = minExtract(json, "retCode");
            if ("0".equals(retCode)) {
                final String resultSection = extractJsonValue(json, "result");
                if (resultSection != null) {
                    LOGGER.debug("Extracted result section: " + resultSection);
                    final String orderData = extractFirstOrderFromResult(resultSection);
                    if (orderData != null) {
                        populateOrderFromJson(order, orderData, clientOrderId);
                    }
                }
            } else {
                final String retMsg = minExtract(json, "retMsg");
                LOGGER.warn("ByBit futures order status query failed with retCode: " + retCode + ", retMsg: " + retMsg);
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("ByBit FUTURES order status query failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Transfers balance between spot and futures accounts
     *
     * @param asset  The asset to transfer (e.g., USDT, BTC)
     * @param amount The amount to transfer as string
     * @return true if transfer was successful, false otherwise
     */
    public boolean transferSpotToFutures(final String asset, final String amount) {
        final long timestamp = System.currentTimeMillis();
        final String recv_window = "5000";

        // Build transfer request body

        final String requestBody = "{" +
                "\"transferId\":\"" + java.util.UUID.randomUUID() + "\"," +
                "\"coin\":\"" + asset + "\"," +
                "\"amount\":\"" + amount + "\"," +
                "\"fromAccountType\":\"SPOT\"," +
                "\"toAccountType\":\"CONTRACT\"" +
                "}";
        final String signaturePayload = timestamp + apiKey + recv_window + requestBody;

        try {
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signaturePayload);
            final String url = MAINNET_BASE_URL + "/v5/asset/transfer/inter-transfer";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BAPI-API-KEY", apiKey);
            headers.put("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.put("X-BAPI-RECV-WINDOW", recv_window);
            headers.put("X-BAPI-SIGN", signature);
            headers.put("Content-Type", "application/json");
            headers.put("Referer", subscription.getBrokerId());

            LOGGER.debug("Transferring balance from Spot to Futures: asset=" + asset + " amount=" + amount);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp != null && resp.getCode() == 200) {
                final String json = resp.getData();
                final String retCode = minExtract(json, "retCode");
                if ("0".equals(retCode)) {
                    LOGGER.info("ByBit balance transfer from spot to futures successful: asset=" + asset + " amount=" + amount);
                    return true;
                } else {
                    final String retMsg = minExtract(json, "retMsg");
                    LOGGER.warn("ByBit balance transfer failed: " + retMsg);
                    return false;
                }
            } else {
                LOGGER.warn("ByBit balance transfer failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("ByBit balance transfer failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves all open spot orders for the account
     *
     * @return JSON response string containing open order data, or null if failed
     */
    public String getAllOpenSpotOrders() {
        try {
            final long timestamp = System.currentTimeMillis();
            final String recv_window = "5000";
            final String queryString = "category=spot&timestamp=" + timestamp;
            final String signaturePayload = timestamp + apiKey + recv_window + queryString;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signaturePayload);

            final String url = MAINNET_BASE_URL + "/v5/order/realtime?" + queryString;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BAPI-API-KEY", apiKey);
            headers.put("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.put("X-BAPI-RECV-WINDOW", recv_window);
            headers.put("X-BAPI-SIGN", signature);
            headers.put("Referer", subscription.getBrokerId());

            LOGGER.debug("Getting all open ByBit spot orders");
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Get all open ByBit spot orders failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return null;
            }
            LOGGER.debug("Successfully retrieved all open ByBit spot orders");
            return resp.getData();
        } catch (final Exception e) {
            LOGGER.error("Get all open ByBit spot orders failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves all open futures orders for the account
     *
     * @return JSON response string containing open order data, or null if failed
     */
    public String getAllOpenFuturesOrders() {
        try {
            final long timestamp = System.currentTimeMillis();
            final String recv_window = "5000";
            final String queryString = "category=linear&timestamp=" + timestamp;
            final String signaturePayload = timestamp + apiKey + recv_window + queryString;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signaturePayload);

            final String url = MAINNET_BASE_URL + "/v5/order/realtime?" + queryString;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BAPI-API-KEY", apiKey);
            headers.put("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.put("X-BAPI-RECV-WINDOW", recv_window);
            headers.put("X-BAPI-SIGN", signature);
            headers.put("Referer", subscription.getBrokerId());

            LOGGER.debug("Getting all open ByBit futures orders");
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Get all open ByBit futures orders failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return null;
            }
            LOGGER.debug("Successfully retrieved all open ByBit futures orders");
            return resp.getData();
        } catch (final Exception e) {
            LOGGER.error("Get all open ByBit futures orders failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Cancels a spot order via REST API
     *
     * @param order         The order object to update with cancellation status
     * @param symbol        Trading pair symbol
     * @param clientOrderId Client-specified order ID
     * @return true if cancellation was successful, false otherwise
     */
    public boolean cancelSpotOrderRest(final Order order, final String symbol, final String clientOrderId) {
        try {
            final long timestamp = System.currentTimeMillis();
            final String recv_window = "5000";

            final String requestBody = "{" +
                    "\"category\":\"spot\"," +
                    "\"symbol\":\"" + symbol + "\"," +
                    "\"orderLinkId\":\"" + clientOrderId + "\"" +
                    "}";
            final String signaturePayload = timestamp + apiKey + recv_window + requestBody;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signaturePayload);
            final String url = MAINNET_BASE_URL + "/v5/order/cancel";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BAPI-API-KEY", apiKey);
            headers.put("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.put("X-BAPI-RECV-WINDOW", recv_window);
            headers.put("X-BAPI-SIGN", signature);
            headers.put("Content-Type", "application/json");
            headers.put("Referer", subscription.getBrokerId());

            LOGGER.debug("Cancelling ByBit spot order for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Cancel ByBit spot order failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            final String retCode = minExtract(json, "retCode");

            if ("0".equals(retCode)) {
                LOGGER.info("Successfully cancelled ByBit spot order for clientOrderId: " + clientOrderId);
                order.setRejected(true);
                ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionMessage == null) {
                    executionMessage =
                            ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);
                    executionMessage.setClOrdId(clientOrderId);
                }
                executionMessage.setExecType(ExecType.CANCELED);
                executionMessage.setOrdStatus(OrdStatus.CANCELED);
                subscription.updateExecutionReport(executionMessage);
                subscription.updateOrder(clientOrderId, order);
                return true;
            } else {
                final String retMsg = minExtract(json, "retMsg");
                LOGGER.warn("Cancel ByBit spot order failed with retCode: " + retCode + ", retMsg: " + retMsg);
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("Cancel ByBit spot order failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Cancels a futures order via REST API
     *
     * @param order         The order object to update with cancellation status
     * @param symbol        Trading pair symbol
     * @param clientOrderId Client-specified order ID
     * @return true if cancellation was successful, false otherwise
     */
    public boolean cancelFuturesOrderRest(final Order order, final String symbol, final String clientOrderId) {
        try {
            final long timestamp = System.currentTimeMillis();
            final String recv_window = "5000";

            final String requestBody = "{" +
                    "\"category\":\"linear\"," +
                    "\"symbol\":\"" + symbol + "\"," +
                    "\"orderLinkId\":\"" + clientOrderId + "\"" +
                    "}";
            final String signaturePayload = timestamp + apiKey + recv_window + requestBody;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signaturePayload);
            final String url = MAINNET_BASE_URL + "/v5/order/cancel";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-BAPI-API-KEY", apiKey);
            headers.put("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.put("X-BAPI-RECV-WINDOW", recv_window);
            headers.put("X-BAPI-SIGN", signature);
            headers.put("Content-Type", "application/json");
            headers.put("Referer", subscription.getBrokerId());

            LOGGER.debug("Cancelling ByBit futures order for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Cancel ByBit futures order failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            final String retCode = minExtract(json, "retCode");

            if ("0".equals(retCode)) {
                LOGGER.info("Successfully cancelled ByBit futures order for clientOrderId: " + clientOrderId);
                order.setRejected(true);
                ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionMessage == null) {
                    executionMessage =
                            ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);
                    executionMessage.setClOrdId(clientOrderId);
                }
                executionMessage.setExecType(ExecType.CANCELED);
                executionMessage.setOrdStatus(OrdStatus.CANCELED);
                subscription.updateExecutionReport(executionMessage);
                subscription.updateOrder(clientOrderId, order);
                return true;
            } else {
                final String retMsg = minExtract(json, "retMsg");
                LOGGER.warn("Cancel ByBit futures order failed with retCode: " + retCode + ", retMsg: " + retMsg);
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("Cancel ByBit futures order failed: " + e.getMessage(), e);
            return false;
        }
    }

    public List<ExternalSymbol> getExchangeInstrumentsFull() {
    List<ExternalSymbol> symbolStatuses = new ArrayList<>();
    HttpUtils.Response response = HttpUtils.get(MAINNET_BASE_URL + "/v5/market/instruments-info?category=spot", new HashMap<>()
        , ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);
    if (response != null && response.getCode() == 200) {
      try {
        final ByBitExchangeInfoFull info = MAPPER.readValue(response.getData(), ByBitExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getResult() != null && info.getResult().getList() != null) {
          for (ByBitSymbol byBitSymbol : info.getResult().getList()) {
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("bybit");
            symbolStatus.setSymbol(byBitSymbol.getSymbol());
            symbolStatus.setBase(byBitSymbol.getBaseCoin());
            symbolStatus.setQuote(byBitSymbol.getQuoteCoin());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(false);
            symbolStatus.setTradable("Trading".equals(byBitSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            // symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(getPrecision(byBitSymbol.getPriceFilter().getTickSize()));
            symbolStatus.setQtyScale(getPrecision(byBitSymbol.getLotSizeFilter().getBasePrecision()));
            symbolStatuses.add(symbolStatus);
          }
        }
      } catch (JsonProcessingException e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }
    response = HttpUtils.get(MAINNET_BASE_URL + "/v5/market/instruments-info?category=linear&limit=1000", new HashMap<>()
        , ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);
    if (response != null && response.getCode() == 200) {
      try {
        final ByBitExchangeInfoFull info = MAPPER.readValue(response.getData(), ByBitExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getResult() != null && info.getResult().getList() != null) {
          for (ByBitSymbol byBitSymbol : info.getResult().getList()) {
            if (!"LinearPerpetual".equalsIgnoreCase(byBitSymbol.getContractType())) {
              continue;
            }
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("bybit");
            symbolStatus.setSymbol(byBitSymbol.getSymbol());
            symbolStatus.setBase(byBitSymbol.getBaseCoin());
            symbolStatus.setQuote(byBitSymbol.getQuoteCoin());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(true);
            symbolStatus.setTradable("Trading".equals(byBitSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            // symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(getPrecision(byBitSymbol.getPriceFilter().getTickSize()));
            symbolStatus.setQtyScale(getPrecision(byBitSymbol.getLotSizeFilter().getQtyStep()));
            symbolStatuses.add(symbolStatus);
          }
        }
      } catch (JsonProcessingException e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }

    return symbolStatuses;
  }

  /**
   * Linear perpetuals that are delisted or scheduled for delisting, from GET /v5/market/instruments-info.
   * Trading perpetual with deliveryTime != "0" = delisting scheduled; status Delivering or Closed = trading stopped.
   *
   * @return the delisted symbols, or null when a request failed
   */
  public List<DelistedSymbol> getFuturesDelistedSymbols() {
    final List<ByBitSymbol> live = getInstruments("category=linear&limit=1000"); // Trading only
    final List<ByBitSymbol> delivering = getInstruments("category=linear&status=Delivering&limit=1000");
    final List<ByBitSymbol> closed = getInstruments("category=linear&status=Closed&limit=1000");
    if (live == null || delivering == null || closed == null) {
      return null;
    }
    closed.addAll(delivering); // both stopped trading, checked the same way below
    final long now = System.currentTimeMillis();
    final List<DelistedSymbol> delisted = new ArrayList<>();
    for (final ByBitSymbol byBitSymbol : live) {
      final long deliveryTime = parseDeliveryTime(byBitSymbol.getDeliveryTime());
      if ("LinearPerpetual".equals(byBitSymbol.getContractType()) && deliveryTime > 0) {
        delisted.add(toDelistedSymbol(byBitSymbol, true, deliveryTime, now));
      }
    }
    for (final ByBitSymbol byBitSymbol : closed) { // results of the status=Closed and status=Delivering queries
      // dated LinearFutures close on their normal expiry, that is not a delisting. The status=Closed query also returns old
      // renamed perpetuals (e.g. DATAOLD01USDT) as PendingOpen with a past deliveryTime: stopped too
      final long deliveryTime = parseDeliveryTime(byBitSymbol.getDeliveryTime());
      if ("LinearPerpetual".equals(byBitSymbol.getContractType()) && ("Closed".equals(byBitSymbol.getStatus())
          || "Delivering".equals(byBitSymbol.getStatus()) || (deliveryTime > 0 && deliveryTime <= now))) {
        delisted.add(toDelistedSymbol(byBitSymbol, true, parseDeliveryTime(byBitSymbol.getDeliveryTime()), now));
      }
    }
    return delisted;
  }

  /**
   * Spot pairs Bybit stopped listing. Bybit spot has no delisted status or date: a delisted pair disappears from
   * GET /v5/market/instruments-info?category=spot, so pairs missing compared to the previous call are reported.
   * The first call starts from the saved pairs, so pairs delisted while the engine was down are caught too.
   *
   * @return the removed pairs, or null when the request failed
   */
  public List<DelistedSymbol> getSpotDelistedSymbols() {
    checkDelistingAnnouncements();
    final List<ByBitSymbol> live = getInstruments("category=spot&limit=1000");
    if (live == null) {
      return null;
    }
    final Map<String, String[]> listed = new HashMap<>();
    for (final ByBitSymbol byBitSymbol : live) {
      if (DelistedSymbol.TRADING.equalsIgnoreCase(byBitSymbol.getStatus())) {
        listed.put(byBitSymbol.getSymbol(), new String[] {byBitSymbol.getBaseCoin(), byBitSymbol.getQuoteCoin()});
      }
    }
    return listedTracker.update(subscription.getExchange(), false, listed, System.currentTimeMillis());
  }

  // Bybit names no spot pairs or dates, so new spot delisting announcements are logged (title, date, link)
  private void checkDelistingAnnouncements() {
    try {
      final HttpUtils.Response response = HttpUtils.get(MAINNET_BASE_URL + "/v5/announcements/index?locale=en-US&type=delistings&limit=20",
          new HashMap<>(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
      if (response == null || response.getCode() != 200) {
        return;
      }
      final long since = lastAnnouncementTime > 0 ? lastAnnouncementTime : System.currentTimeMillis() - 7L * 24 * 3600_000;
      long latest = since;
      for (final JsonNode item : MAPPER.readTree(response.getData()).path("result").path("list")) {
        final long publishTime = item.path("publishTime").asLong(0);
        final String tags = item.path("tags").toString(); // perpetuals come with a deliveryTime, WEB3 = Bybit Alpha
        if (publishTime > since && !tags.contains("\"Derivatives\"") && !tags.contains("\"WEB3\"")) {
          LOGGER.warn("Bybit spot delisting announced: " + item.path("title").asText() + " (published "
              + Instant.ofEpochMilli(publishTime) + ", " + item.path("url").asText() + ")");
        }
        latest = Math.max(latest, publishTime);
      }
      lastAnnouncementTime = latest;
    } catch (final Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
    }
  }

  // GET /v5/market/instruments-info following nextPageCursor; null when a request failed
  private List<ByBitSymbol> getInstruments(final String query) {
    final List<ByBitSymbol> symbols = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      if (++pages > MAX_INSTRUMENT_PAGES) {
        LOGGER.warn("Bybit instruments-info " + query + " stopped after " + MAX_INSTRUMENT_PAGES + " pages");
        break;
      }
      final String url = MAINNET_BASE_URL + "/v5/market/instruments-info?" + query
          + (cursor == null ? "" : "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
      final HttpUtils.Response response = HttpUtils.get(url, new HashMap<>(), subscription.getLastUsedProxy(),
          PROXY_PORT, subscription.isForceToUseProxy());
      if (response == null || response.getCode() != 200) {
        LOGGER.warn("Bybit instruments-info " + query + " failed with status: "
            + (response != null ? response.getCode() : "null"));
        return null;
      }
      try {
        final ByBitExchangeInfoFull info = MAPPER.readValue(response.getData(), ByBitExchangeInfoFull.class);
        if (info.getRetCode() != 0 || info.getResult() == null) {
          LOGGER.warn("Bybit instruments-info " + query + " retCode: " + info.getRetCode());
          return null;
        }
        final List<ByBitSymbol> page = info.getResult().getList();
        if (page == null || page.isEmpty()) {
          break;
        }
        symbols.addAll(page);
        final String next = info.getResult().getNextPageCursor();
        cursor = next == null || next.equals(cursor) ? null : next; // stop when the cursor does not move
      } catch (final Exception e) {
        LOGGER.error(Constants.ERROR_LOG, e);
        return null;
      }
    } while (cursor != null && !cursor.isEmpty());
    return symbols;
  }

  private static long parseDeliveryTime(final String deliveryTime) {
    try {
      return deliveryTime == null || deliveryTime.isEmpty() ? 0 : Long.parseLong(deliveryTime);
    } catch (final NumberFormatException e) {
      return 0;
    }
  }

  private DelistedSymbol toDelistedSymbol(final ByBitSymbol byBitSymbol, final boolean futures,
      final long delistTime, final long now) {
    final DelistedSymbol delisted = new DelistedSymbol();
    delisted.setExchange(subscription.getExchange());
    delisted.setTradingDisabled(!"Trading".equals(byBitSymbol.getStatus()));
    delisted.setFutures(futures);
    delisted.setSymbol(byBitSymbol.getSymbol());
    delisted.setBase(byBitSymbol.getBaseCoin());
    delisted.setQuote(byBitSymbol.getQuoteCoin());
    delisted.setStatus("Trading".equals(byBitSymbol.getStatus()) ? DelistedSymbol.TRADING : byBitSymbol.getStatus());
    delisted.setDelistTime(delistTime);
    delisted.setDetectedAt(now);
    return delisted;
  }

    /**
     * Extracts the fee currency key from cumFeeDetail JSON object
     * Expected format: {"XRP": "0.003"} -> returns "XRP"
     *
     * @param cumFeeDetailJson The cumFeeDetail JSON object as string
     * @return The currency key (e.g., "XRP"), or null if not found
     */
    private String extractFeeCurrencyFromDetail(final String cumFeeDetailJson) {
        if (cumFeeDetailJson == null || cumFeeDetailJson.trim().isEmpty() || "{}".equals(cumFeeDetailJson.trim())) {
            return null;
        }

        // Find the first quoted key
        final int firstQuote = cumFeeDetailJson.indexOf('"');
        if (firstQuote < 0) {
            return null;
        }

        final int secondQuote = cumFeeDetailJson.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }

        return cumFeeDetailJson.substring(firstQuote + 1, secondQuote);
    }

    /**
     * Converts ByBit order status string to internal OrdStatus enum
     *
     * @param orderStatusStr ByBit order status string
     * @return Corresponding OrdStatus enum value
     */
    private OrdStatus getByBitOrderStatus(final String orderStatusStr) {
        if (orderStatusStr == null) return OrdStatus.NEW;

        return switch (orderStatusStr) {
            case "New" -> OrdStatus.NEW;
            case "PartiallyFilled" -> OrdStatus.PARTIALLY_FILLED;
            case "Filled" -> OrdStatus.FILLED;
            case "Cancelled" -> OrdStatus.CANCELED;
            case "Rejected" -> OrdStatus.REJECTED;
            case "PartiallyFilledCanceled" -> OrdStatus.CANCELED;
            case "Deactivated" -> OrdStatus.CANCELED;
            default -> {
                LOGGER.warn("Unknown ByBit order status: " + orderStatusStr + ", defaulting to NEW");
                yield OrdStatus.NEW;
            }
        };
    }

    /**
     * Properly extracts a JSON value by finding the complete object/value for a given key
     * Handles nested JSON structures with proper bracket/brace matching
     *
     * @param json The JSON string to parse
     * @param key  The key to extract value for
     * @return The extracted value as string, or null if not found
     */
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

        // Find the colon after the key
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

        // Skip whitespace after colon to find value start
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

        // Determine value type and extract accordingly
        if (firstChar == '{') {
            // Extract object
            return extractJsonObject(json, valueStart);
        } else if (firstChar == '[') {
            // Extract array
            return extractJsonArray(json, valueStart);
        } else if (firstChar == '"') {
            // Extract string
            return extractJsonString(json, valueStart);
        } else {
            // Extract primitive (number, boolean, null)
            return extractJsonPrimitive(json, valueStart);
        }
    }

    /**
     * Extracts a complete JSON object starting from the given position
     * Uses proper brace matching to handle nested objects
     */
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

    /**
     * Extracts a complete JSON array starting from the given position
     * Uses proper bracket matching to handle nested arrays
     */
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

    /**
     * Extracts a JSON string value (without quotes)
     */
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
                return json.substring(start + 1, i); // Return without quotes
            }
        }
        return null;
    }

    /**
     * Extracts a JSON primitive value (number, boolean, null)
     */
    private String extractJsonPrimitive(final String json, final int start) {
        for (int i = start; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']' || c == '\n' || c == '\r') {
                return json.substring(start, i).trim();
            }
        }
        return json.substring(start).trim();
    }

    /**
     * Extracts the first order object from the result section by finding the "list" array
     * and returning the first order object within it
     *
     * @param resultSection The result section from ByBit API response
     * @return The first order object as JSON string, or null if not found
     */
    private String extractFirstOrderFromResult(final String resultSection) {
        if (resultSection == null || resultSection.trim().isEmpty()) {
            return null;
        }

        LOGGER.debug("Parsing result section: " + resultSection);

        // Find the "list" field - look for exact pattern "list"
        final int listFieldStart = resultSection.indexOf("\"list\"");
        if (listFieldStart < 0) {
            LOGGER.debug("No 'list' field found in result section");
            return null;
        }

        // Find the colon after "list"
        int colonPos = -1;
        for (int i = listFieldStart + 6; i < resultSection.length(); i++) {
            final char c = resultSection.charAt(i);
            if (c == ':') {
                colonPos = i;
                break;
            } else if (c != ' ' && c != '"') {
                // Found non-whitespace, non-quote character before ':', invalid
                LOGGER.debug("Invalid character found before ':' in list field");
                return null;
            }
        }

        if (colonPos < 0) {
            LOGGER.debug("No colon found after 'list' field");
            return null;
        }

        // Find the opening bracket of the list array after the colon
        int arrayStart = -1;
        for (int i = colonPos + 1; i < resultSection.length(); i++) {
            final char c = resultSection.charAt(i);
            if (c == '[') {
                arrayStart = i;
                break;
            } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                // Found non-whitespace character before '[', not an array
                LOGGER.debug("Non-whitespace character found before '[': " + c);
                return null;
            }
        }

        if (arrayStart < 0) {
            LOGGER.debug("No array start '[' found for 'list' field");
            return null;
        }

        LOGGER.debug("Found list array start at position: " + arrayStart);

        // Find the matching closing bracket for the list array
        int arrayEnd = -1;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = arrayStart; i < resultSection.length(); i++) {
            final char c = resultSection.charAt(i);

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

        if (arrayEnd <= arrayStart) {
            LOGGER.debug("No matching ']' found for list array");
            return null;
        }

        // Extract the content of the list array (without the brackets)
        final String listContent = resultSection.substring(arrayStart + 1, arrayEnd);
        LOGGER.debug("Extracted list content: " + listContent);

        // Now extract the first order object from the list
        return extractFirstOrderFromList(listContent.trim());
    }

    /**
     * Extracts the first order object from a JSON list array content
     * Handles proper bracket matching to extract complete JSON objects
     *
     * @param listContent The content inside the list array (without brackets)
     * @return The first complete order object, or null if not found
     */
    private String extractFirstOrderFromList(final String listContent) {
        if (listContent == null || listContent.trim().isEmpty()) {
            LOGGER.debug("List content is empty or null");
            return null;
        }

        final String trimmedContent = listContent.trim();
        LOGGER.debug("Processing trimmed list content: " + trimmedContent);

        // Find the start of the first object in the array
        final int objStart = trimmedContent.indexOf('{');
        if (objStart < 0) {
            LOGGER.debug("No object start '{' found in list content");
            return null;
        }

        // Find the matching closing brace for the first object
        int braceDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = objStart; i < trimmedContent.length(); i++) {
            final char c = trimmedContent.charAt(i);

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
                        // Found the end of the first complete object
                        final String orderObject = trimmedContent.substring(objStart, i + 1);
                        LOGGER.debug("Successfully extracted first order object");
                        return orderObject;
                    }
                }
            }
        }

        LOGGER.debug("No matching '}' found for first order object");
        return null;
    }


    /**
     * Returns default quote currency based on symbol pattern
     *
     * @param symbol Trading pair symbol
     * @return Default quote currency (USDC or USDT)
     */
    private String getDefaultQuotedCurrency(final String symbol) {
        if (symbol != null && symbol.contains("USDC")) {
            return "USDC";
        } else {
            return "USDT";
        }
    }

  private int getPrecision(String value) {
    if (value == null || !value.contains(".")) {
      return 0; // No decimal point means 0 precision
    }
    // Split the string on the decimal point
    String[] parts = value.split("\\.");
    if (parts.length < 2) {
      return 0;
    }
    // Trim trailing zeros for robustness
    String decimalPart = parts[1].replaceAll("0*$", "");

    return decimalPart.length();
  }

    /**
     * Retrieves ticker data for a given symbol and category from Bybit v5 market API.
     *
     * @param symbol   Symbol name, e.g. "BTCUSDT"
     * @param category Product type: "spot" or "linear"
     * @return Populated {@link Ticker}, or null on failure
     */
    public Ticker getTicker(final String symbol, final String category) {
        try {
            final String url = MAINNET_BASE_URL + "/v5/market/tickers?category=" + category + "&symbol=" + symbol;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Referer", subscription.getBrokerId());

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get ticker failed for symbol: " + symbol + " category: " + category
                        + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }

            final String json = response.getData();
            final String retCodeStr = minExtract(json, "retCode");
            if (!"0".equals(retCodeStr)) {
                final String retMsg = minExtract(json, "retMsg");
                LOGGER.warn("Get ticker failed for symbol: " + symbol + " retCode: " + retCodeStr + " retMsg: " + retMsg);
                return null;
            }

            final String result = extractJsonValue(json, "result");
            if (result == null) {
                LOGGER.warn("No result in ticker response for symbol: " + symbol);
                return null;
            }

            final String listArray = extractJsonValue(result, "list");
            if (listArray == null) {
                LOGGER.warn("No list in ticker result for symbol: " + symbol);
                return null;
            }

            final String tickerJson = extractFirstOrderFromArray(listArray);
            if (tickerJson == null) {
                LOGGER.warn("Empty list in ticker response for symbol: " + symbol);
                return null;
            }

            final double last        = parseDoubleSafe(minExtract(tickerJson, "lastPrice"));
            final double bid         = parseDoubleSafe(minExtract(tickerJson, "bid1Price"));
            final double ask         = parseDoubleSafe(minExtract(tickerJson, "ask1Price"));
            final double bidSize     = parseDoubleSafe(minExtract(tickerJson, "bid1Size"));
            final double askSize     = parseDoubleSafe(minExtract(tickerJson, "ask1Size"));
            final double high        = parseDoubleSafe(minExtract(tickerJson, "highPrice24h"));
            final double low         = parseDoubleSafe(minExtract(tickerJson, "lowPrice24h"));
            final double open        = parseDoubleSafe(minExtract(tickerJson, "prevPrice24h"));
            final double volume      = parseDoubleSafe(minExtract(tickerJson, "volume24h"));
            final double quoteVolume = parseDoubleSafe(minExtract(tickerJson, "turnover24h"));
            // price24hPcnt is a decimal fraction (e.g. 0.0145 = 1.45%) — convert to percentage
            final double percentageChange = parseDoubleSafe(minExtract(tickerJson, "price24hPcnt")) * 100.0;
            final long timestamp     = parseLongSafe(minExtract(json, "time"));

            LOGGER.debug("Retrieved ticker for symbol: " + symbol + " category: " + category
                    + " last=" + last + " bid=" + bid + " ask=" + ask);

            return new Ticker(symbol, 0, open, last, bid, ask, high, low,
                    volume, quoteVolume, timestamp, bidSize, askSize, percentageChange);

        } catch (final Exception e) {
            LOGGER.error("Get ticker failed for symbol: " + symbol + " " + e.getMessage());
            return null;
        }
    }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitExchangeInfoFull {
    private int retCode;
    private ByBitResult result;

    public final int getRetCode() {
      return retCode;
    }

    public final void setRetCode(final int retCode) {
      this.retCode = retCode;
    }

    public final ByBitResult getResult() {
      return result;
    }

    public final void setResult(final ByBitResult result) {
      this.result = result;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitResult {
    private List<ByBitSymbol> list;
    private String category;
    private String nextPageCursor;

    public final String getNextPageCursor() {
      return nextPageCursor;
    }

    public final void setNextPageCursor(final String nextPageCursor) {
      this.nextPageCursor = nextPageCursor;
    }

    public final List<ByBitSymbol> getList() {
      return list;
    }

    public final void setList(final List<ByBitSymbol> list) {
      this.list = list;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(final String category) {
      this.category = category;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitSymbol {
    private String symbol;
    private String baseCoin;
    private String quoteCoin;
    private String status;
    private String contractType;
    // linear: "0" = no end date; for a LinearPerpetual it is the delisting time (ms)
    private String deliveryTime;

    private String marginTrading;

    public final String getDeliveryTime() {
      return deliveryTime;
    }

    public final void setDeliveryTime(final String deliveryTime) {
      this.deliveryTime = deliveryTime;
    }
    private ByBitSymbolLotSizeFilter lotSizeFilter;
    private ByBitPriceFilter priceFilter;

    public final String getSymbol() {
      return symbol;
    }

    public final void setSymbol(final String symbol) {
      this.symbol = symbol;
    }

    public final String getBaseCoin() {
      return baseCoin;
    }

    public final void setBaseCoin(final String baseCoin) {
      this.baseCoin = baseCoin;
    }

    public final String getQuoteCoin() {
      return quoteCoin;
    }

    public final void setQuoteCoin(final String quoteCoin) {
      this.quoteCoin = quoteCoin;
    }

    public final String getStatus() {
      return status;
    }

    public final void setStatus(final String status) {
      this.status = status;
    }

    public final String getContractType() {
      return contractType;
    }

    public final void setContractType(final String contractType) {
      this.contractType = contractType;
    }

    public final String getMarginTrading() {
      return marginTrading;
    }

    public final void setMarginTrading(final String marginTrading) {
      this.marginTrading = marginTrading;
    }

    public final ByBitSymbolLotSizeFilter getLotSizeFilter() {
      return lotSizeFilter;
    }

    public final void setLotSizeFilter(final ByBitSymbolLotSizeFilter lotSizeFilter) {
      this.lotSizeFilter = lotSizeFilter;
    }

    public final ByBitPriceFilter getPriceFilter() {
      return priceFilter;
    }

    public final void setPriceFilter(final ByBitPriceFilter priceFilter) {
      this.priceFilter = priceFilter;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitSymbolLotSizeFilter {
    private String basePrecision;
    private String quotePrecision;
    private String marginTrading;
    private String minOrderQty;
    private String maxOrderQty;
    private String minOrderAmt;
    private String maxOrderAmt;
    private String qtyStep;

    public final String getBasePrecision() {
      return basePrecision;
    }

    public final void setBasePrecision(final String basePrecision) {
      this.basePrecision = basePrecision;
    }

    public final String getQuotePrecision() {
      return quotePrecision;
    }

    public final void setQuotePrecision(final String quotePrecision) {
      this.quotePrecision = quotePrecision;
    }

    public final String getMarginTrading() {
      return marginTrading;
    }

    public final void setMarginTrading(final String marginTrading) {
      this.marginTrading = marginTrading;
    }

    public final String getMinOrderQty() {
      return minOrderQty;
    }

    public final void setMinOrderQty(final String minOrderQty) {
      this.minOrderQty = minOrderQty;
    }

    public final String getMaxOrderQty() {
      return maxOrderQty;
    }

    public final void setMaxOrderQty(final String maxOrderQty) {
      this.maxOrderQty = maxOrderQty;
    }

    public final String getMinOrderAmt() {
      return minOrderAmt;
    }

    public final void setMinOrderAmt(final String minOrderAmt) {
      this.minOrderAmt = minOrderAmt;
    }

    public final String getMaxOrderAmt() {
      return maxOrderAmt;
    }

    public final void setMaxOrderAmt(final String maxOrderAmt) {
      this.maxOrderAmt = maxOrderAmt;
    }

    public final String getQtyStep() {
      return qtyStep;
    }

    public final void setQtyStep(final String qtyStep) {
      this.qtyStep = qtyStep;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitPriceFilter {
    private String minPrice;
    private String maxPrice;
    private String tickSize;

    public final String getMinPrice() {
      return minPrice;
    }

    public final void setMinPrice(final String minPrice) {
      this.minPrice = minPrice;
    }

    public final String getMaxPrice() {
      return maxPrice;
    }

    public final void setMaxPrice(final String maxPrice) {
      this.maxPrice = maxPrice;
    }

    public final String getTickSize() {
      return tickSize;
    }

    public final void setTickSize(final String tickSize) {
      this.tickSize = tickSize;
    }
  }

  // ---------- Position POJOs ----------
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BybitPositionsResponse {
    private int retCode;
    private String retMsg;
    private BybitPositionsResult result;
    private long time;

    public final int getRetCode() {
      return retCode;
    }

    public final void setRetCode(int retCode) {
      this.retCode = retCode;
    }

    public final String getRetMsg() {
      return retMsg;
    }

    public final void setRetMsg(String retMsg) {
      this.retMsg = retMsg;
    }

    public final BybitPositionsResult getResult() {
      return result;
    }

    public final void setResult(BybitPositionsResult result) {
      this.result = result;
    }

    public final long getTime() {
      return time;
    }

    public final void setTime(long time) {
      this.time = time;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BybitPositionsResult {
    private String category;
    private List<BybitPosition> list;

    public final String getCategory() {
      return category;
    }

    public final void setCategory(String category) {
      this.category = category;
    }

    public final List<BybitPosition> getList() {
      return list;
    }

    public final void setList(List<BybitPosition> list) {
      this.list = list;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BybitPosition {
    private String symbol;
    private String side;
    private String size;
    private String entryPrice;
    private String markPrice;
    private String leverage;
    private String positionValue;
    private String positionStatus;
    private String positionIdx;
    private String positionIM;
    private String positionMM;
    private String takeProfit;
    private String stopLoss;

    public final String getSymbol() {
      return symbol;
    }

    public final void setSymbol(String symbol) {
      this.symbol = symbol;
    }

    public final String getSide() {
      return side;
    }

    public final void setSide(String side) {
      this.side = side;
    }

    public final String getSize() {
      return size;
    }

    public final void setSize(String size) {
      this.size = size;
    }

    public final String getEntryPrice() {
      return entryPrice;
    }

    public final void setEntryPrice(String entryPrice) {
      this.entryPrice = entryPrice;
    }

    public final String getMarkPrice() {
      return markPrice;
    }

    public final void setMarkPrice(String markPrice) {
      this.markPrice = markPrice;
    }

    public final String getLeverage() {
      return leverage;
    }

    public final void setLeverage(String leverage) {
      this.leverage = leverage;
    }

    public final String getPositionValue() {
      return positionValue;
    }

    public final void setPositionValue(String positionValue) {
      this.positionValue = positionValue;
    }

    public final String getPositionStatus() {
      return positionStatus;
    }

    public final void setPositionStatus(String positionStatus) {
      this.positionStatus = positionStatus;
    }

    public final String getPositionIdx() {
      return positionIdx;
    }

    public final void setPositionIdx(String positionIdx) {
      this.positionIdx = positionIdx;
    }

    public final String getPositionIM() {
      return positionIM;
    }

    public final void setPositionIM(String positionIM) {
      this.positionIM = positionIM;
    }

    public final String getPositionMM() {
      return positionMM;
    }

    public final void setPositionMM(String positionMM) {
      this.positionMM = positionMM;
    }

    public final String getTakeProfit() {
      return takeProfit;
    }

    public final void setTakeProfit(String takeProfit) {
      this.takeProfit = takeProfit;
    }

    public final String getStopLoss() {
      return stopLoss;
    }

    public final void setStopLoss(String stopLoss) {
      this.stopLoss = stopLoss;
    }
  }

}