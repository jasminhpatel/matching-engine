package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.deribit;

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
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;
import com.solfini.util.StringUtil;

import com.solfini.matchengine.executionexchange.ExternalSymbol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class DeribitRestClient {
    private static final CustomLogger LOGGER = CustomLogger.getLogger(DeribitRestClient.class);
    private static final String REST_API_BASE = "https://test.deribit.com"; //TODO update it with prod url Use https://www.deribit.com for production
    private static final int PROXY_PORT = 8888;

    private final String clientId;
    private final String clientSecret;
    private final ExchangeSubscription subscription;
    private String accessToken;
    private long tokenExpiryTime;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long NO_EXPIRY = 32503708800000L; // expiration_timestamp of a perpetual with no delisting (year 3000)
    // instruments no longer listed; saved rows don't hold Deribit instrument names, so no seeding from them
    private final DelistedSymbolCache.ListedTracker listedTracker = new DelistedSymbolCache.ListedTracker(false);

    public DeribitRestClient(final String clientId, final String clientSecret, final ExchangeSubscription subscription) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.subscription = subscription;
    }

    private boolean authenticate() {
        try {
            // Check if existing token is still valid
            if (accessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
                LOGGER.debug("Using existing access token, expires at: " + tokenExpiryTime);
                return true; // Token is still valid
            }

            LOGGER.info("Authenticating with Deribit REST API");
            final String url = REST_API_BASE + "/api/v2/public/auth";
            final String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"public/auth\",\"params\":{\"grant_type\":\"client_credentials\",\"client_id\":\"%s\",\"client_secret\":\"%s\"}}",
                    clientId, clientSecret);

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (response != null && response.getCode() == 200) {
                final String json = response.getData();
                LOGGER.debug("Authentication response received - length: " + (json != null ? json.length() : 0));

                // First extract the result object
                final String resultJson = minExtract(json, "result");
                LOGGER.debug("Extracted result JSON length: " + (resultJson != null ? resultJson.length() : "null"));

                if (resultJson != null) {
                    // Check if the resultJson is complete (should end with '}')
                    if (!resultJson.trim().endsWith("}")) {
                        LOGGER.warn("Result JSON appears to be truncated, attempting fallback parsing");
                        // Try alternative parsing - look for access_token directly in the full response
                        final int tokenStart = json.indexOf("\"access_token\"");
                        if (tokenStart != -1) {
                            final int valueStart = json.indexOf(":", tokenStart) + 1;
                            final int valueEnd = json.indexOf(",", valueStart);
                            if (valueStart > 0 && valueEnd > valueStart) {
                                accessToken = json.substring(valueStart, valueEnd).trim().replace("\"", "").replace(" ", "");
                                LOGGER.debug("Fallback extracted access_token length: " + accessToken.length());
                            }
                        }

                        // Extract expires_in similarly
                        final int expiresStart = json.indexOf("\"expires_in\"");
                        String expiresIn = null;
                        if (expiresStart != -1) {
                            final int expValueStart = json.indexOf(":", expiresStart) + 1;
                            final int expValueEnd = json.indexOf(",", expValueStart);
                            if (expValueStart > 0 && expValueEnd > expValueStart) {
                                expiresIn = json.substring(expValueStart, expValueEnd).trim().replace("\"", "").replace(" ", "");
                                LOGGER.debug("Fallback extracted expires_in: " + expiresIn);
                            }
                        }

                        if (accessToken != null && expiresIn != null) {
                            tokenExpiryTime = System.currentTimeMillis() + (StringUtil.toLong(expiresIn) * 1000) - 60000; // 1 minute buffer
                            LOGGER.info("Successfully authenticated with Deribit using fallback parsing, token expires at: " + tokenExpiryTime);
                            return true;
                        }
                    } else {
                        // Normal parsing path
                        accessToken = minExtract(resultJson, "access_token");
                        final String expiresIn = minExtract(resultJson, "expires_in");

                        LOGGER.debug("Extracted access_token length: " + (accessToken != null ? accessToken.length() : "null"));
                        LOGGER.debug("Extracted expires_in: " + expiresIn);

                        if (accessToken != null && expiresIn != null) {
                            // Remove quotes if present in the extracted values
                            accessToken = accessToken.replace("\"", "");
                            final String cleanExpiresIn = expiresIn.replace("\"", "");

                            tokenExpiryTime = System.currentTimeMillis() + (StringUtil.toLong(cleanExpiresIn) * 1000) - 60000; // 1 minute buffer
                            LOGGER.info("Successfully authenticated with Deribit, token expires at: " + tokenExpiryTime);
                            return true;
                        } else {
                            LOGGER.warn("Failed to extract access_token or expires_in from result JSON");
                        }
                    }
                } else {
                    LOGGER.warn("Failed to extract result from response JSON");
                }
            }
            LOGGER.warn("Authentication failed with status: " + (response != null ? response.getCode() : "null"));
            return false;
        } catch (final Exception e) {
            LOGGER.error("Authentication failed: " + e.getMessage(), e);
            return false;
        }
    }

    public String getBalanceSnapshot() {
        try {
            if (!authenticate()) {
                LOGGER.error("Cannot get balance snapshot - authentication failed");
                return null;
            }

            LOGGER.debug("Fetching balance snapshot from Deribit");
            final String url = REST_API_BASE + "/api/v2/private/get_account_summaries";
            final String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"private/get_account_summaries\",\"params\":{}}";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (response != null && response.getCode() == 200) {
                LOGGER.info("Successfully retrieved balance snapshot");
                return response.getData();
            } else {
                LOGGER.warn("Balance snapshot failed with status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Balance snapshot failed: " + e.getMessage(), e);
        }
        return null;
    }

    public boolean sendSpotOrderREST(final Order order, final String symbol, final String side, final String orderType,
                                     final String timeInForce, final String size, final String price, final String clientOrderId) {
        try {
            if (!authenticate()) {
                LOGGER.error("Cannot send spot order - authentication failed");
                return false;
            }

            LOGGER.info("Sending spot order via REST - symbol: " + symbol + ", side: " + side + ", type: " + orderType + ", size: " + size + ", price: " + price);

            final String url = REST_API_BASE + "/api/v2/private/" + (side.equalsIgnoreCase("buy") ? "buy" : "sell");

            // Build order parameters
            final StringBuilder paramsBuilder = new StringBuilder();
            paramsBuilder.append("{");
            paramsBuilder.append("\"instrument_name\":\"").append("BTC_USDT").append("\",");
            paramsBuilder.append("\"amount\":").append(size).append(",");
            paramsBuilder.append("\"type\":\"").append(orderType.toLowerCase()).append("\"");

            if (price != null && !"market".equalsIgnoreCase(orderType)) {
                paramsBuilder.append(",\"price\":").append(price);
            }

            if (clientOrderId != null) {
                paramsBuilder.append(",\"label\":\"").append(clientOrderId).append("\"");
            }

            if (order.isReduceOnly()) {
                paramsBuilder.append(",\"reduce_only\":").append(order.isReduceOnly());
            }

            if (timeInForce != null) {
                final String deribitTif = convertTimeInForce(timeInForce);
                if (deribitTif != null) {
                    paramsBuilder.append(",\"time_in_force\":\"").append(deribitTif).append("\"");
                }
            }

            paramsBuilder.append("}");

            final String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"private/%s\",\"params\":%s}",
                    side.equalsIgnoreCase("buy") ? "buy" : "sell", paramsBuilder);

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Sending spot order REST request");
            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp != null) {
                final String json = resp.getData();

                // Check for error response (can be in 200 or 400 response)
                if (resp.getCode() != 200 || (json != null && json.contains("\"error\""))) {
                    // Extract error reason
                    final String errorReason =  extractJsonValue(json, "message");

                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }

                    executionMessage.setClOrdId(order.getClOrdId());
                    executionMessage.setError(errorReason);
                    executionMessage.setExecType(ExecType.REJECTED);
                    subscription.updateExecutionReport(executionMessage);
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);

                    LOGGER.warn("Spot order rejected (status: " + resp.getCode() + "): " + errorReason + " - Full response: " + json);
                    return false;
                }

                final String resultJson =  extractJsonValue(json, "result");
                if (resultJson != null) {
                    final String orderJson = extractJsonValue(resultJson, "order");
                    if (orderJson != null) {

                        final String filledAmount = minExtract(orderJson, "filled_amount");
                        final String averagePrice = minExtract(orderJson, "average_price");
                        final String orderState = minExtract(orderJson, "order_state");

                        final double filledAmountDouble = parseDoubleSafe(filledAmount);
                        final double averagePriceDouble = parseDoubleSafe(averagePrice);
                        // Convert Deribit order status
                        final String convertedStatus = convertDeribitOrderStatus(orderState);

                        // Convert to order's scale
                        final long filledAmountLong = MbxMath.changeScale(filledAmountDouble, order.getQtyScale());
                        final long averagePriceLong = MbxMath.changeScale(averagePriceDouble, order.getPriceScale());


                        // Create or update execution report
                        ExecutionReportMessage executionMessage = subscription.getExecutionReport(clientOrderId);
                        if (executionMessage == null) {
                            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                    0, 0, 0, 0, order.getSide(), 0);
                        }

                        executionMessage.setOrdStatus(convertDeribitOrdStatus(convertedStatus));
                        executionMessage.setCumQty(filledAmountLong);
                        executionMessage.setAvgPx(averagePriceLong);

                        subscription.updateExecutionReport(executionMessage);
                        LOGGER.info("Spot order created successfully - orderId: " + order.getOrderId() + ", clOrdId: " + order.getClOrdId());
                        return true;
                    }
                }

                LOGGER.warn("Failed to parse order from response: " + json);
                return false;
            } else {
                LOGGER.warn("REST spot order failed - null response");
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("REST spot order failed: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean sendFutureOrderREST(final Order order, final String symbol, final String side,
                                       final String orderType, final String timeInForce, final String size, final String price,
                                       final String clientOrderId) {
        try {
            if (!authenticate()) {
                LOGGER.error("Cannot send future order - authentication failed");
                return false;
            }

            LOGGER.info("Sending future order via REST - symbol: " + symbol + ", side: " + side + ", type: " + orderType + ", size: " + size + ", price: " + price);

            final String url = REST_API_BASE + "/api/v2/private/" + (side.equalsIgnoreCase("buy") ? "buy" : "sell");

            // Build order parameters
            final StringBuilder paramsBuilder = new StringBuilder();
            paramsBuilder.append("{");
            paramsBuilder.append("\"instrument_name\":\"").append(symbol).append("\",");
            paramsBuilder.append("\"amount\":").append(size).append(",");
            paramsBuilder.append("\"type\":\"").append(orderType.toLowerCase()).append("\"");

            if (price != null && !"market".equalsIgnoreCase(orderType)) {
                paramsBuilder.append(",\"price\":").append(price);
            }

            if (clientOrderId != null) {
                paramsBuilder.append(",\"label\":\"").append(clientOrderId).append("\"");
            }

            if (order.isReduceOnly()) {
                paramsBuilder.append(",\"reduce_only\":").append(order.isReduceOnly());
            }

            if (timeInForce != null) {
                final String deribitTif = convertTimeInForce(timeInForce);
                if (deribitTif != null) {
                    paramsBuilder.append(",\"time_in_force\":\"").append(deribitTif).append("\"");
                }
            }

            paramsBuilder.append("}");

            final String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"private/%s\",\"params\":%s}",
                    side.equalsIgnoreCase("buy") ? "buy" : "sell", paramsBuilder);

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Sending future order REST request");
            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp != null) {
                final String json = resp.getData();

                // Check for error response (can be in 200 or 400 response)
                if (resp.getCode() != 200 || (json != null && json.contains("\"error\""))) {
                    // Extract error reason
                    final String errorReason =  extractJsonValue(json, "message");

                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }

                    executionMessage.setClOrdId(order.getClOrdId());
                    executionMessage.setError(errorReason);
                    executionMessage.setExecType(ExecType.REJECTED);
                    subscription.updateExecutionReport(executionMessage);
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);

                    LOGGER.warn("future order rejected (status: " + resp.getCode() + "): " + errorReason + " - Full response: " + json);
                    return false;
                }

                final String resultJson =  extractJsonValue(json, "result");
                if (resultJson != null) {
                    final String orderJson = extractJsonValue(resultJson, "order");
                    if (orderJson != null) {

                        final String filledAmount = minExtract(orderJson, "filled_amount");
                        final String averagePrice = minExtract(orderJson, "average_price");
                        final String orderState = minExtract(orderJson, "order_state");

                        final double filledAmountDouble = parseDoubleSafe(filledAmount);
                        final double averagePriceDouble = parseDoubleSafe(averagePrice);
                        // Convert Deribit order status
                        final String convertedStatus = convertDeribitOrderStatus(orderState);

                        // Convert to order's scale
                        final long filledAmountLong = MbxMath.changeScale(filledAmountDouble, order.getQtyScale());
                        final long averagePriceLong = MbxMath.changeScale(averagePriceDouble, order.getPriceScale());


                        // Create or update execution report
                        ExecutionReportMessage executionMessage = subscription.getExecutionReport(clientOrderId);
                        if (executionMessage == null) {
                            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                    0, 0, 0, 0, order.getSide(), 0);
                        }

                        executionMessage.setOrdStatus(convertDeribitOrdStatus(convertedStatus));
                        executionMessage.setCumQty(filledAmountLong);
                        executionMessage.setAvgPx(averagePriceLong);

                        subscription.updateExecutionReport(executionMessage);
                        LOGGER.info("Spot order created successfully - orderId: " + order.getOrderId() + ", clOrdId: " + order.getClOrdId());
                        return true;
                    }
                }

                LOGGER.warn("Failed to parse order from response: " + json);
                return false;
            } else {
                LOGGER.warn("REST future order failed - null response");
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("REST future order failed: " + e.getMessage(), e);
            return false;
        }
    }


    public String getPositionInfo() {
        try {
            if (!authenticate()) {
                LOGGER.error("Cannot get position info - authentication failed");
                return null;
            }

            LOGGER.debug("Fetching position info from Deribit");
            final String url = REST_API_BASE + "/api/v2/private/get_positions";
            final String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"private/get_positions\",\"params\":{}}";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (response != null && response.getCode() == 200) {
                LOGGER.info("Successfully retrieved position info");
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

    public String getAllOpenOrders() {
        try {
            if (!authenticate()) {
                LOGGER.error("Cannot get open futures orders - authentication failed");
                return null;
            }

            LOGGER.debug("Fetching all open orders from Deribit");
            final String url = REST_API_BASE + "/api/v2/private/get_open_orders";
            final String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"private/get_open_orders\",\"params\":{}}";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Get all open futures orders failed with status: " + (resp != null ? resp.getCode() : "null") + ", body: " + (resp != null ? resp.getData() : "null"));
                return null;
            }
            LOGGER.info("Successfully retrieved all open futures orders");
            return resp.getData();
        } catch (final Exception e) {
            LOGGER.error("Get all open futures orders failed: " + e.getMessage(), e);
            return null;
        }
    }

    public boolean querySpotOrderStatus(final Order order) {

        try {
            if (!authenticate()) {
                LOGGER.error("Cannot query spot order status - authentication failed");
                return false;
            }

            LOGGER.debug("Querying Deribit spot order status for orderId: " + order.getOrderId());
            final String url = REST_API_BASE + "/api/v2/private/get_order_state";
            final String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"private/get_order_state\",\"params\":{\"order_id\":\"%s\"}}",
                    order.getOrderId());

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Deribit spot order status query failed with status: " + (resp != null ? resp.getCode() : "null") + ", body: " + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            LOGGER.debug("Order status response: " + json);

            final String resultJson = minExtract(json, "result");
            if (resultJson != null) {
                final String orderState = minExtract(resultJson, "order_state");
                final String label = minExtract(resultJson, "label");

                LOGGER.info("Extracted orderState: " + orderState + " for orderId: " + order.getOrderId());

                if (orderState != null) {
                    final String convertedStatus = convertDeribitOrderStatus(orderState);
                    subscription.updateOrder(order.getClOrdId(), convertedStatus);
                }
            } else {
                final String error = minExtract(json, "error");
                if (error != null) {
                    final String message = minExtract(error, "message");
                    LOGGER.warn("Deribit spot order status query failed with error: " + message);
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("Deribit spot order status query failed: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean queryFuturesOrderStatus(final Order order, final String symbol, final String clientOrderId) {
        try {
            if (!authenticate()) {
                LOGGER.error("Cannot query futures order status - authentication failed");
                return false;
            }

            LOGGER.debug("Querying Deribit futures order status for orderId: " + order.getOrderId());
            final String url = REST_API_BASE + "/api/v2/private/get_order_state";
            final String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"private/get_order_state\",\"params\":{\"order_id\":\"%s\"}}",
                    order.getOrderId());

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Deribit futures order status query failed with status: " + (resp != null ? resp.getCode() : "null") + ", body: " + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            LOGGER.debug("Order status response: " + json);

            final String resultJson = minExtract(json, "result");
            if (resultJson != null) {
                final String orderState = minExtract(resultJson, "order_state");
                final String label = minExtract(resultJson, "label");

                LOGGER.info("Extracted orderState: " + orderState + " for orderId: " + order.getOrderId());

                if (orderState != null) {
                    final String convertedStatus = convertDeribitOrderStatus(orderState);
                    subscription.updateOrder(clientOrderId, convertedStatus);
                }
            } else {
                final String error = minExtract(json, "error");
                if (error != null) {
                    final String message = minExtract(error, "message");
                    LOGGER.warn("Deribit futures order status query failed with error: " + message);
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("Deribit futures order status query failed: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean queryOrderStatusByLabel(final Order order) {
        try {
            if (!authenticate()) {
                LOGGER.error("Cannot query order status by label - authentication failed");
                return false;
            }

            final String currency = order.getSymbol();
            final String label = order.getClOrdId();

            LOGGER. debug("Querying Deribit order status by label: " + label + ", currency: " + currency);
            final String url = REST_API_BASE + "/api/v2/private/get_order_state_by_label";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response resp = HttpUtils.get(url + "?currency=" + currency + "&label=" + label,
                    headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Deribit order status by label query failed with status: " + (resp != null ? resp.getCode() : "null") + ", body: " + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            LOGGER.debug("Order status by label response: " + json);

            final String resultJson = extractJsonValue(json, "result");
            if (resultJson != null && resultJson.trim().startsWith("[")) {
                // Result is an array - extract first order from it
                final String firstOrder = extractFirstOrderFromArray(resultJson);
                if (firstOrder != null) {
                    populateDeribitOrderFromJson(order, firstOrder, label);
                    LOGGER.info("Successfully queried order status by label: " + label);
                    return true;
                }
            } else {
                final String error = minExtract(json, "error");
                if (error != null) {
                    final String message = minExtract(error, "message");
                    LOGGER.warn("Deribit order status by label query failed with error: " + message);
                }
            }
            return false;
        } catch (final Exception e) {
            LOGGER.error("Deribit order status by label query failed: " + e.getMessage(), e);
            return false;
        }
    }



    private String convertTimeInForce(final String timeInForce) {
        if (timeInForce == null) {
            LOGGER.debug("TimeInForce is null, returning null");
            return null;
        }

        final String upperTif = timeInForce.toUpperCase();
        LOGGER.debug("Converting TimeInForce: " + timeInForce);

        switch (upperTif) {
            case "GTC":
                return "good_til_cancelled";
            case "IOC":
                return "immediate_or_cancel";
            case "FOK":
                return "fill_or_kill";
            default:
                LOGGER.warn("Unknown TimeInForce: " + timeInForce + ", defaulting to GTC");
                return "good_til_cancelled";
        }
    }

    private String convertDeribitOrderStatus(final String deribitStatus) {
        if (deribitStatus == null) {
            LOGGER.warn("Deribit order status is null, returning unknown");
            return "unknown";
        }

        final String lowerStatus = deribitStatus.toLowerCase();
        LOGGER.debug("Converting Deribit order status: " + deribitStatus);

        switch (lowerStatus) {
            case "open":
                return "open";
            case "filled":
                return "filled";
            case "partially_filled":
                return "partially_filled";
            case "rejected":
                return "rejected";
            case "cancelled":
                return "cancelled";
            case "untriggered":
                return "pending";
            default:
                LOGGER.warn("Unknown Deribit order status: " + deribitStatus + ", returning as-is");
                return deribitStatus;
        }
    }

    /**
     * Get instruments for a specific currency
     */
    public String getInstruments(final String currency, final String kind) {
        try {
            LOGGER.debug("Fetching instruments for currency: " + currency + ", kind: " + kind);
            final String url = REST_API_BASE + "/api/v2/public/get_instruments";
            final String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"public/get_instruments\",\"params\":{\"currency\":\"%s\",\"kind\":\"%s\"}}",
                    currency, kind != null ? kind : "future");

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.get(url + "?currency=" + currency + "&kind=" + (kind != null ? kind : "future"),
                    headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (response != null && response.getCode() == 200) {
                LOGGER.info("Successfully retrieved instruments for currency: " + currency);
                return response.getData();
            } else {
                LOGGER.warn("Get instruments failed with status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Get instruments failed: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Fetches all available trading instruments from Deribit for spot and future kinds
     * across major currencies (BTC, ETH, USDC, USDT) and returns them as SymbolData list.
     *
     * Deribit API: GET /api/v2/public/get_instruments?currency={currency}&kind={kind}
     * Response: {"jsonrpc":"2.0","id":1,"result":[{instrument_name, base_currency, quote_currency,
     *           tick_size, min_trade_amount, contract_size, is_active, kind, ...}]}
     */
    public List<ExternalSymbol> getExchangeInstrumentsFull() {
        final List<ExternalSymbol> externalSymbolList = new ArrayList<>();
        final String[] currencies = {"BTC", "ETH", "USDC", "USDT"};
        final String[] kinds = {"spot", "future", "option"};
        final long updated = System.currentTimeMillis();

        for (final String currency : currencies) {
            for (final String kind : kinds) {
                try {
                    final String json = getInstruments(currency, kind);
                    if (json == null) {
                        continue;
                    }

                    // Extract the result array from JSON-RPC response
                    final String resultArray = extractResultArray(json);
                    if (resultArray == null) {
                        LOGGER.warn("No result array in get_instruments response for currency=" + currency + ", kind=" + kind);
                        continue;
                    }

                    // Parse each instrument object from the result array
                    int objStart = 0;
                    while (objStart < resultArray.length()) {
                        objStart = resultArray.indexOf('{', objStart);
                        if (objStart < 0) break;

                        // Find matching closing brace
                        int braceCount = 0;
                        int objEnd = objStart;
                        boolean inString = false;
                        boolean escaped = false;

                        for (int i = objStart; i < resultArray.length(); i++) {
                            final char c = resultArray.charAt(i);
                            if (escaped) { escaped = false; continue; }
                            if (c == '\\') { escaped = true; continue; }
                            if (c == '"') { inString = !inString; continue; }
                            if (!inString) {
                                if (c == '{') braceCount++;
                                else if (c == '}') {
                                    braceCount--;
                                    if (braceCount == 0) { objEnd = i; break; }
                                }
                            }
                        }

                        if (objEnd <= objStart) break;

                        final String instrumentObj = resultArray.substring(objStart, objEnd + 1);
                        final ExternalSymbol externalSymbol = parseInstrumentToSymbolData(instrumentObj, kind, updated);
                        if (externalSymbol != null) {
                            externalSymbolList.add(externalSymbol);
                        }

                        objStart = objEnd + 1;
                    }

                    LOGGER.debug("Fetched " + externalSymbolList.size() + " instruments so far (after currency=" + currency + ", kind=" + kind + ")");
                } catch (final Exception e) {
                    LOGGER.error("Failed to fetch instruments for currency=" + currency + ", kind=" + kind + ": " + e.getMessage());
                }
            }
        }

        LOGGER.info("Total Deribit instruments fetched: " + externalSymbolList.size());
        return externalSymbolList.isEmpty() ? null : externalSymbolList;
    }

    /** Perpetuals not open, scheduled (real expiration) or no longer listed; dated futures expire normally. Null on failure. */
    public List<DelistedSymbol> getFuturesDelistedSymbols() {
        return getDelistedSymbols("future", true);
    }

    /** Spot pairs not open (locked, halted, ...) or no longer listed; null when the request failed. */
    public List<DelistedSymbol> getSpotDelistedSymbols() {
        return getDelistedSymbols("spot", false);
    }

    private List<DelistedSymbol> getDelistedSymbols(final String kind, final boolean futures) {
        final HttpUtils.Response response = HttpUtils.get(REST_API_BASE + "/api/v2/public/get_instruments?currency=any&kind=" + kind,
                new HashMap<>(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
        if (response == null || response.getCode() != 200) {
            LOGGER.warn("Deribit get_instruments " + kind + " failed with status: " + (response != null ? response.getCode() : "null"));
            return null;
        }
        final long now = System.currentTimeMillis();
        final List<DelistedSymbol> delisted = new ArrayList<>();
        final Map<String, String[]> listed = new HashMap<>();
        final Set<String> openBaseQuote = new HashSet<>(); // base + quote with at least one open instrument of this kind
        try {
            final JsonNode result = MAPPER.readTree(response.getData()).path("result");
            if (!result.isArray()) {
                LOGGER.warn("Deribit get_instruments " + kind + " returned no result: " + response.getData());
                return null;
            }
            for (final JsonNode item : result) {
                if (item.path("is_active").asBoolean(false) && "open".equals(item.path("state").asText())) {
                    openBaseQuote.add((item.path("base_currency").asText() + item.path("quote_currency").asText()).toUpperCase());
                }
                if (futures && !"perpetual".equals(item.path("settlement_period").asText())) {
                    continue; // dated futures expire normally, that is not a delisting
                }
                final DelistedSymbol symbol = toDelistedSymbol(item, subscription.getExchange(), futures, now);
                listed.put(symbol.getSymbol(), new String[] {symbol.getBase(), symbol.getQuote()});
                if (symbol.isTradingDisabled() || symbol.isUpcoming()) {
                    delisted.add(symbol);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Deribit get_instruments " + kind + " parse failed", e);
            return null;
        }
        final List<DelistedSymbol> removed = listedTracker.update(subscription.getExchange(), futures, listed, now);
        if (removed == null) {
            return null;
        }
        delisted.addAll(removed);
        delisted.addAll(savedRowEntries(delisted, openBaseQuote, now));
        return delisted;
    }

    // Saved (DB) rows have no instrument name, only base + quote: block them too, but only when no instrument of that
    // base + quote is open in this kind, so a coin that still trades is never blocked
    private List<DelistedSymbol> savedRowEntries(final List<DelistedSymbol> delisted, final Set<String> openBaseQuote, final long now) {
        final Map<String, DelistedSymbol> entries = new HashMap<>();
        for (final DelistedSymbol symbol : delisted) {
            final String baseQuote = (symbol.getBase() + symbol.getQuote()).toUpperCase();
            if (symbol.isTradingDisabled() && !openBaseQuote.contains(baseQuote) && !entries.containsKey(baseQuote)) {
                final DelistedSymbol entry = new DelistedSymbol();
                entry.setExchange(symbol.getExchange());
                entry.setFutures(symbol.isFutures());
                entry.setSymbol(baseQuote); // what a saved row without a name is matched by
                entry.setBase(symbol.getBase());
                entry.setQuote(symbol.getQuote());
                entry.setStatus("NO_OPEN_INSTRUMENT (" + symbol.getSymbol() + ")");
                entry.setTradingDisabled(true);
                entry.setDetectedAt(now);
                entries.put(baseQuote, entry);
            }
        }
        return new ArrayList<>(entries.values());
    }

    // stopped when not active or state is not "open" (locked, inactive, halted); a real expiration on a perpetual = delisting date
    static DelistedSymbol toDelistedSymbol(final JsonNode item, final String exchange, final boolean futures, final long now) {
        final long expiration = futures ? item.path("expiration_timestamp").asLong(0) : 0;
        final long delistTime = expiration > 0 && expiration < NO_EXPIRY ? expiration : 0;
        final DelistedSymbol symbol = new DelistedSymbol();
        symbol.setExchange(exchange);
        symbol.setFutures(futures);
        symbol.setSymbol(item.path("instrument_name").asText());
        symbol.setBase(item.path("base_currency").asText());
        symbol.setQuote(item.path("quote_currency").asText());
        symbol.setStatus(item.path("state").asText());
        symbol.setTradingDisabled(!item.path("is_active").asBoolean(false) || !"open".equals(item.path("state").asText())
                || (delistTime > 0 && delistTime <= now));
        symbol.setDelistTime(delistTime);
        symbol.setDetectedAt(now);
        return symbol;
    }

    /**
     * Extracts the JSON array from "result" field in a JSON-RPC response.
     */
    private String extractResultArray(final String json) {
        final int resultStart = json.indexOf("\"result\"");
        if (resultStart == -1) return null;

        final int valueStart = json.indexOf(":", resultStart) + 1;
        if (valueStart <= 0) return null;

        final int arrayStart = json.indexOf("[", valueStart);
        if (arrayStart == -1) return null;

        int bracketCount = 0;
        int arrayEnd = -1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = arrayStart; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString) {
                if (c == '[') bracketCount++;
                else if (c == ']') {
                    bracketCount--;
                    if (bracketCount == 0) { arrayEnd = i; break; }
                }
            }
        }

        if (arrayEnd > arrayStart) {
            return json.substring(arrayStart + 1, arrayEnd);
        }
        return null;
    }
    
    /**
     * Parses a single Deribit instrument JSON object into a SymbolData.
     *
     * Deribit instrument fields used:
     * - instrument_name: trading symbol (e.g. "BTC-PERPETUAL", "BTC_USDT")
     * - base_currency: base asset (e.g. "BTC")
     * - quote_currency: quote asset (e.g. "USD", "USDT")
     * - tick_size: minimum price increment
     * - min_trade_amount: minimum order size
     * - contract_size: contract multiplier
     * - is_active: whether instrument is tradable
     * - kind: "spot" or "future"
     */
    private ExternalSymbol parseInstrumentToSymbolData(final String instrumentObj, final String kind, final long updated) {
        final String instrumentName = minExtract(instrumentObj, "instrument_name");
        if (instrumentName == null) return null;

        final String baseCurrency = minExtract(instrumentObj, "base_currency");
        final String quoteCurrency = minExtract(instrumentObj, "quote_currency");
        final String isActiveStr = minExtract(instrumentObj, "is_active");
        final double tickSize = parseDoubleSafe(minExtract(instrumentObj, "tick_size"));
        final double minTradeAmount = parseDoubleSafe(minExtract(instrumentObj, "min_trade_amount"));
        final double contractSize = parseDoubleSafe(minExtract(instrumentObj, "contract_size"));

        final ExternalSymbol externalSymbol = new ExternalSymbol();
        externalSymbol.setExchange("deribit");
        externalSymbol.setBase(baseCurrency);
        externalSymbol.setQuote(quoteCurrency);
        externalSymbol.setPrompt(instrumentName);
        externalSymbol.setKind(kind);
        externalSymbol.setFutures("future".equalsIgnoreCase(kind));
        externalSymbol.setTradable("true".equalsIgnoreCase(isActiveStr));
        externalSymbol.setPriceScale(deriveScale(tickSize));
        externalSymbol.setQtyScale(deriveScale(minTradeAmount));
        externalSymbol.setMinimumAmount(minTradeAmount);
        externalSymbol.setPriceStepSize(tickSize);
        externalSymbol.setAmountStepSize(minTradeAmount);
        externalSymbol.setUpdated(updated);

        if (contractSize > 0) {
            externalSymbol.setMultiplierContract((int) contractSize);
        }

        // Populate option-specific fields
        if ("option".equalsIgnoreCase(kind)) {
            final String optionType = minExtract(instrumentObj, "option_type");
            final double strike = parseDoubleSafe(minExtract(instrumentObj, "strike"));
            final long expirationTimestamp = parseLongSafe(minExtract(instrumentObj, "expiration_timestamp"));

            externalSymbol.setPut("put".equalsIgnoreCase(optionType));
            externalSymbol.setCall("call".equalsIgnoreCase(optionType));
            externalSymbol.setStrike(strike);
            externalSymbol.setExpiryTime(expirationTimestamp);
        }

        return externalSymbol;
    }

    /**
     * Derives the decimal scale (number of decimal places) from a step size value.
     * e.g. 0.01 -> 2, 0.0005 -> 4, 1.0 -> 0, 10 -> 0
     */
    private int deriveScale(final double stepSize) {
        if (stepSize <= 0 || stepSize >= 1.0) return 0;

        int scale = 0;
        double val = stepSize;
        while (val < 1.0 && scale < 12) {
            val *= 10;
            scale++;
        }
        return scale;
    }

    /**
     * Cancel an order
     */
    public boolean cancelOrder(final String orderId) {
        try {
            if (!authenticate()) {
                LOGGER.error("Cannot cancel order - authentication failed");
                return false;
            }

            LOGGER.info("Cancelling order: " + orderId);
            final String url = REST_API_BASE + "/api/v2/private/cancel";
            final String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"private/cancel\",\"params\":{\"order_id\":\"%s\"}}", orderId);

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp != null && resp.getCode() == 200) {
                final String json = resp.getData();
                final String resultJson = minExtract(json, "result");

                if (resultJson != null) {
                    LOGGER.info("Successfully cancelled order: " + orderId);
                    return true;
                }
            }
            LOGGER.warn("Cancel order failed with status: " + (resp != null ? resp.getCode() : "null") + ", body: " + (resp != null ? resp.getData() : "null"));
            return false;
        } catch (final Exception e) {
            LOGGER.error("Cancel order failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Cancel an order by label (client order ID)
     * Deribit API: POST /api/v2/private/cancel_by_label
     *
     * @param label The label/client order ID to cancel
     * @return true if cancellation was successful, false otherwise
     */
    public boolean cancelOrderByLabel(final String label, final Order order) {
        try {
            if (!authenticate()) {
                LOGGER.error("Cannot cancel order by label - authentication failed");
                return false;
            }

            LOGGER.info("Cancelling order by label: " + label);
            final String url = REST_API_BASE + "/api/v2/private/cancel_by_label";
            final String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"private/cancel_by_label\",\"params\":{\"label\":\"%s\"}}", label);

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response resp = HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp != null && resp.getCode() == 200) {
                final String json = resp.getData();
                final String resultJson = minExtract(json, "result");

                if (resultJson != null && resultJson.contains("1")) {
                    order.setRejected(true);
                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage =
                                ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);
                        executionMessage.setClOrdId(order.getClOrdId());
                    }
                    executionMessage.setExecType(ExecType.CANCELED);
                    executionMessage.setOrdStatus(OrdStatus.CANCELED);
                    subscription.updateExecutionReport(executionMessage);
                    subscription.updateOrder(order.getClOrdId(), order);

                    LOGGER.info("Successfully cancelled order by label: " + label);
                    return true;
                }
            }
            LOGGER.warn("Cancel order by label failed with status: " + (resp != null ? resp.getCode() : "null") + ", body: " + (resp != null ? resp.getData() : "null"));
            return false;
        } catch (final Exception e) {
            LOGGER.error("Cancel order by label failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Populates order object and execution report from Deribit order JSON response
     * Extracts filled_amount, average_price, order_state and other relevant fields
     *
     * @param order         The order object to populate
     * @param orderJson     The complete order JSON object as string
     * @param clientOrderId The client order ID
     */
    private void populateDeribitOrderFromJson(final Order order, final String orderJson, final String clientOrderId) {
        // Extract all order fields from response
        final String filledAmount = minExtract(orderJson, "filled_amount");
        final String averagePrice = minExtract(orderJson, "average_price");
        final String orderState = minExtract(orderJson, "order_state");
        final String lastUpdateTimestamp = minExtract(orderJson, "last_update_timestamp");

        LOGGER.debug("Extracted order details - orderId: " + order.getOrderId() + ", filledAmount: " + filledAmount +
                ", averagePrice: " + averagePrice + ", orderState: " + orderState + ", orderType: " + order.getOrdType());

        // Parse numeric values
        final double filledAmountDouble = parseDoubleSafe(filledAmount);
        final double averagePriceDouble = parseDoubleSafe(averagePrice);
        final long lastUpdateTimestampLong = parseLongSafe(lastUpdateTimestamp);

        // Convert to order's scale
        final long filledAmountLong = MbxMath.changeScale(filledAmountDouble, order.getQtyScale());
        final long averagePriceLong = MbxMath.changeScale(averagePriceDouble, order.getPriceScale());

        // Convert Deribit order status
        final String convertedStatus = convertDeribitOrderStatus(orderState);

        // Create or update execution report
        ExecutionReportMessage executionMessage = subscription.getExecutionReport(clientOrderId);
        if (executionMessage == null) {
            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                    0, 0, 0, 0, order.getSide(), 0);
        }

        // Update order object
        if ("filled".equalsIgnoreCase(orderState)) {
            order.setExecuted(true);
            LOGGER.debug("Deribit order FILLED - filledAmount: " + filledAmount + ", averagePrice: " + averagePrice);
        } else if ("cancelled".equalsIgnoreCase(orderState) || "rejected".equalsIgnoreCase(orderState)) {
            order.setRejected(true);
            LOGGER.debug("Deribit order " + orderState.toUpperCase());
        }

        // Update execution report with all extracted fields
        executionMessage.setClOrdId(clientOrderId);
        executionMessage.setOrdStatus(convertDeribitOrdStatus(convertedStatus));
        executionMessage.setTimeInForce(order.getTimeInForce());
        executionMessage.setInputTime(lastUpdateTimestampLong);
        executionMessage.setPrice(averagePriceLong);
        executionMessage.setPriceScale(order.getPriceScale());
        executionMessage.setOrderQty(order.getQty());
        executionMessage.setOrderQtyScale(order.getQtyScale());
        executionMessage.setCumQty(filledAmountLong);
        executionMessage.setAvgPx(averagePriceLong);

        LOGGER.info("Updated execution report for spot order - orderId: " + order.getOrderId() + " with status: " + orderState);
        subscription.updateExecutionReport(executionMessage);
        subscription.updateOrder(clientOrderId, order);
    }

    public Ticker getTicker(final String instrumentName) {
        try {
            final String url = REST_API_BASE + "/api/v2/public/get_ticker?instrument_name=" + instrumentName;
            final Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get ticker failed for instrument: " + instrumentName + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
            final String json = response.getData();
            final String result = extractJsonValue(json, "result");
            if (result == null) {
                LOGGER.warn("No result in ticker response for: " + instrumentName);
                return null;
            }
            final String stats = extractJsonValue(result, "stats");
            final double last             = parseDoubleSafe(minExtract(result, "last_price"));
            final double bid              = parseDoubleSafe(minExtract(result, "bid_price"));
            final double ask              = parseDoubleSafe(minExtract(result, "ask_price"));
            final long timestamp          = parseLongSafe(minExtract(result, "timestamp"));
            final double high             = stats != null ? parseDoubleSafe(minExtract(stats, "high")) : 0.0;
            final double low              = stats != null ? parseDoubleSafe(minExtract(stats, "low")) : 0.0;
            final double baseVolume       = stats != null ? parseDoubleSafe(minExtract(stats, "volume")) : 0.0;
            final double quoteVolume      = stats != null ? parseDoubleSafe(minExtract(stats, "volume_usd")) : 0.0;
            final double percentageChange = stats != null ? parseDoubleSafe(minExtract(stats, "price_change")) : 0.0;
            LOGGER.debug("Retrieved ticker for instrument: " + instrumentName + " last=" + last + " bid=" + bid + " ask=" + ask);
            return new Ticker(instrumentName, 0, last, last, bid, ask, high, low, baseVolume, quoteVolume, timestamp, 0.0, 0.0, percentageChange);
        } catch (final Exception e) {
            LOGGER.error("Get ticker failed for instrument: " + instrumentName + " " + e.getMessage());
            return null;
        }
    }

    /**
     * Converts Deribit order status string to OrdStatus enum
     *
     * @param statusStr Deribit status string (open, filled, cancelled, rejected, etc.)
     * @return Corresponding OrdStatus enum
     */
    private OrdStatus convertDeribitOrdStatus(final String statusStr) {
        if (statusStr == null) {
            return OrdStatus.NEW;
        }

        return switch (statusStr.toLowerCase()) {
            case "open" -> OrdStatus.NEW;
            case "filled" -> OrdStatus.FILLED;
            case "partially_filled" -> OrdStatus.PARTIALLY_FILLED;
            case "cancelled" -> OrdStatus.CANCELED;
            case "rejected" -> OrdStatus.REJECTED;
            case "pending" -> OrdStatus.NEW;
            default -> {
                LOGGER.warn("Unknown Deribit order status: " + statusStr + ", defaulting to NEW");
                yield OrdStatus.NEW;
            }
        };
    }


}
