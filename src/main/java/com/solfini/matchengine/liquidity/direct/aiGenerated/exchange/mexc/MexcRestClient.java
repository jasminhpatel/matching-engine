package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.liquidity.DelistedSymbolCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.util.HMAC;
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class MexcRestClient {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(MexcRestClient.class);
    private static final String MAINNET_BASE_URL = "https://api.mexc.com";
    private static final int PROXY_PORT = 8888;
  private static final ObjectMapper MAPPER = new ObjectMapper(); // todo use string parsing

    // Configuration and credentials - immutable after construction
    private final String apiKey;
    private final byte[] secretUtf8;
    private final ExchangeSubscription subscription;
    private final DelistedSymbolCache.ListedTracker listedTracker = new DelistedSymbolCache.ListedTracker(); // pairs no longer listed

    /**
     * Initializes MEXC REST client with API credentials
     * @param apiKey MEXC API key for authentication
     * @param secretUtf8 MEXC API secret in UTF-8 bytes for HMAC signature
     * @param subscription Liquidity subscription for order and balance management
     */
    public MexcRestClient(final String apiKey, final byte[] secretUtf8, final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.secretUtf8 = secretUtf8;
        this.subscription = subscription;
    }

    /**
     * Retrieves spot account balance snapshot from MEXC
     * @return JSON response string containing balance data, or null if failed
     */
    public String getBalanceSnapshot() {
        try {
            final long timestamp = System.currentTimeMillis();
            final String queryString = "timestamp=" + timestamp;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, queryString);

            final String url = MAINNET_BASE_URL + "/api/v3/account?" + queryString + "&signature=" + signature;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved MEXC spot balance snapshot");
                return response.getData();
            } else {
                LOGGER.warn("MEXC Spot Balance Bootstrap failed with status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("MEXC Spot Balance Bootstrap failed: " + e.getMessage());
        }
        return null;
    }

/*
    *//**
     * Transfers funds from spot account to futures account
     * @param asset Asset to transfer (e.g., "USDT")
     * @param amount Amount to transfer as string
     * @return true if transfer was successful, false otherwise
     *//*
    public boolean transferSpotToFutures(final String asset, final String amount) {
        try {
            final long timestamp = System.currentTimeMillis();
            final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("asset=").append(asset);
            queryBuilder.append("&amount=").append(amount);
            queryBuilder.append("&type=1"); // 1 = spot to futures
            queryBuilder.append("&timestamp=").append(timestamp);

            final String queryString = queryBuilder.toString();
            final String signature = HMAC.hmacSha256Hex(secretUtf8, queryString);
            final String requestBody = queryString + "&signature=" + signature;

            final String url = MAINNET_BASE_URL + "/fapi/v1/futures/transfer";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            LOGGER.debug("Transferring from spot to futures: asset=" + asset + " amount=" + amount);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT);
            
            if (resp != null && resp.getCode() == 200) {
                final String json = resp.getData();
                // Check for successful transfer response
                final String tranId = minExtract(json, "tranId");
                if (tranId != null) {
                    LOGGER.info("Successfully transferred from spot to futures: tranId=" + tranId);
                    return true;
                } else {
                    final String msg = minExtract(json, "msg");
                    LOGGER.error("MEXC spot to futures transfer failed: " + msg);
                }
            } else {
                LOGGER.warn("MEXC spot to futures transfer failed with status: " + (resp != null ? resp.getCode() : "null") + 
                           " body=" + (resp != null ? resp.getData() : "null"));
            }
            return false;
        } catch (final Exception e) {
            LOGGER.error("MEXC spot to futures transfer failed: " + e.getMessage());
            return false;
        }
    }

    *//**
     * Transfers funds from futures account to spot account
     * @param asset Asset to transfer (e.g., "USDT")
     * @param amount Amount to transfer as string
     * @return true if transfer was successful, false otherwise
     *//*
    public boolean transferFuturesToSpot(final String asset, final String amount) {
        try {
            final long timestamp = System.currentTimeMillis();
            final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("asset=").append(asset);
            queryBuilder.append("&amount=").append(amount);
            queryBuilder.append("&type=2"); // 2 = futures to spot
            queryBuilder.append("&timestamp=").append(timestamp);

            final String queryString = queryBuilder.toString();
            final String signature = HMAC.hmacSha256Hex(secretUtf8, queryString);
            final String requestBody = queryString + "&signature=" + signature;

            final String url = MAINNET_BASE_URL + "/fapi/v1/futures/transfer";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            LOGGER.debug("Transferring from futures to spot: asset=" + asset + " amount=" + amount);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT);
            
            if (resp != null && resp.getCode() == 200) {
                final String json = resp.getData();
                // Check for successful transfer response
                final String tranId = minExtract(json, "tranId");
                if (tranId != null) {
                    LOGGER.info("Successfully transferred from futures to spot: tranId=" + tranId);
                    return true;
                } else {
                    final String msg = minExtract(json, "msg");
                    LOGGER.error("MEXC futures to spot transfer failed: " + msg);
                }
            } else {
                LOGGER.warn("MEXC futures to spot transfer failed with status: " + (resp != null ? resp.getCode() : "null") + 
                           " body=" + (resp != null ? resp.getData() : "null"));
            }
            return false;
        } catch (final Exception e) {
            LOGGER.error("MEXC futures to spot transfer failed: " + e.getMessage());
            return false;
        }
    }

    *//**
     * Retrieves all open positions for USDT-M futures
     * @return JSON response string containing position data, or null if failed
     *//*
    public String getAllPositions() {
        try {
            final long timestamp = System.currentTimeMillis();
            final String queryString = "timestamp=" + timestamp;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, queryString);

            final String url = MAINNET_BASE_URL + "/fapi/v1/positionRisk?" + queryString + "&signature=" + signature;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT);
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved MEXC positions");
                return response.getData();
            } else {
                LOGGER.warn("MEXC positions retrieval failed with status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("MEXC positions retrieval failed: " + e.getMessage());
        }
        return null;
    }*/

/*    *//**
     * Sends a futures order via REST API
     * @param order The order object to update with response data
     * @param symbol Trading pair symbol
     * @param side Order side (BUY/SELL)
     * @param type Order type (LIMIT/MARKET)
     * @param timeInForce Time in force (GTC/IOC)
     * @param quantity Order quantity as string
     * @param price Order price as string
     * @return true if request was sent successfully, false otherwise
     *//*
    public boolean sendFutureOrderREST(final Order order, final String symbol, final String side, final String type, 
                                       final String timeInForce, final String quantity, final String price) {
        try {
            final long timestamp = System.currentTimeMillis();
            final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("symbol=").append(symbol);
            queryBuilder.append("&side=").append(side);
            queryBuilder.append("&type=").append(type);
            queryBuilder.append("&quantity=").append(quantity);
            
            if (!"MARKET".equals(type)) {
                queryBuilder.append("&price=").append(price);
            }
            
            queryBuilder.append("&timeInForce=").append(timeInForce);
            queryBuilder.append("&newClientOrderId=").append(order.getClOrdId());
            queryBuilder.append("&timestamp=").append(timestamp);

            final String queryString = queryBuilder.toString();
            final String signature = HMAC.hmacSha256Hex(secretUtf8, queryString);
            final String requestBody = queryString + "&signature=" + signature;

            final String url = MAINNET_BASE_URL + "/fapi/v1/order";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            LOGGER.debug("Sending MEXC future order REST for symbol: " + symbol + " side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT);
            
            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("REST MEXC future order status=" + resp.getCode() + " body=" + resp.getData());
            }
            
            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                // Check for error response
                final String code = minExtract(json, "code");
                if (code != null && !"200".equals(code)) {
                    final String msg = minExtract(json, "msg");
                    LOGGER.error("MEXC future order failed: " + msg);
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);
                } else {
                    // Extract order ID from successful response
                    final String orderId = minExtract(json, "orderId");
                    if (orderId != null) {
                        order.setOrderId(StringUtil.toLong(orderId));
                        LOGGER.info("MEXC future order created with orderId: " + orderId);
                    }
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST MEXC future order failed: " + e.getMessage());
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }*/

    /**
     * Sends a spot order via REST API
     * @param order The order object to update with response data
     * @param symbol Trading pair symbol
     * @param side Order side (BUY/SELL)
     * @param type Order type (LIMIT/MARKET)
     * @param timeInForce Time in force (GTC/IOC)
     * @param quantity Order quantity as string
     * @param price Order price as string
     * @param clientOrderId Client-specified order ID
     * @return true if request was sent successfully, false otherwise
     */
    public boolean sendSpotOrderREST(final Order order, final String symbol, final String side, final String type, 
                                     final String timeInForce, final String quantity, final String price, final String clientOrderId) {
        try {
            /*List of Supported Types
            LIMIT
            MARKET
            LIMIT_MAKER
            IMMEDIATE_OR_CANCEL
            FILL_OR_KILL
            STOP_MARKET_ORDER (Query only)
             */
            final long timestamp = System.currentTimeMillis();
            final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("symbol=").append(symbol);
            queryBuilder.append("&side=").append(side);
           if(timeInForce.equalsIgnoreCase("FOK")){
                queryBuilder.append("&type=").append("FILL_OR_KILL");
            }else{
               //Type value is hardcoded to limit because it doesn't support timeInForce
                queryBuilder.append("&type=").append(type);
            }
            queryBuilder.append("&quantity=").append(quantity);
            
            if (!"MARKET".equals(type)) {
                queryBuilder.append("&price=").append(price);
            }
            
            queryBuilder.append("&newClientOrderId=").append(clientOrderId);
            queryBuilder.append("&timestamp=").append(timestamp);

            final String queryString = queryBuilder.toString();
            final String signature = HMAC.hmacSha256Hex(secretUtf8, queryString);

            System.out.println("Request  Body: "+queryString);
            // Use GET format like other MEXC spot API calls
            final String url = MAINNET_BASE_URL + "/api/v3/order?" + queryString + "&signature=" + signature;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);

            LOGGER.debug("Sending MEXC spot order REST for symbol: " + symbol + ") side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, null, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            
            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("REST MEXC spot order status=" + resp.getCode() + " body=" + resp.getData());
            }

            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                // Create execution report for rejected order
                final ExecutionReportMessage executionReportMessage =
                        ExecutionReportMessage.createExternalExecutionReport(0L, order.getUser(), 0, "", 0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, null, 0);

                // Check for error response
                final String code = minExtract(json, "code");
                if (code != null && !code.isEmpty()) {
                    final String msg = minExtract(json, "msg");
                    LOGGER.error("MEXC spot order failed: " + msg);
                    order.setRejected(true);

                    executionReportMessage.setClOrdId(clientOrderId);
                    executionReportMessage.setError(msg);
                    executionReportMessage.setExecType(ExecType.REJECTED);

                } else {
                    executionReportMessage.setClOrdId(clientOrderId);
                }

                subscription.updateExecutionReport(executionReportMessage);
                subscription.updateOrder(order.getClOrdId(), order);

            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST MEXC spot order failed: " + e.getMessage());
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }


    /**
     * Queries the status of a spot order by client-specified order ID
     * @param order The order object to update with response data
     * @param symbol Trading pair symbol
     * @param clientOrderId Client-specified order ID
     * @return true if query was successful, false otherwise
     */
    public boolean querySpotOrderStatus(final Order order, final String symbol, final String clientOrderId) {
        try {
            final long timestamp = System.currentTimeMillis();
            final String queryString = "symbol=" + symbol + "&origClientOrderId=" + clientOrderId + "&timestamp=" + timestamp;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, queryString);

            final String url = MAINNET_BASE_URL + "/api/v3/order?" + queryString + "&signature=" + signature;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);

            LOGGER.debug("Querying MEXC spot order status for clientOrderId: " + clientOrderId + " symbol: " + symbol);
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("REST MEXC spot order status query failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }
            
            final String json = resp.getData();
            LOGGER.debug("Full response: " + json);
            
            // Parse all order parameters and update order/execution report
            populateOrderStatusJson(order, json, clientOrderId);

            return true;
        } catch (final Exception e) {
            LOGGER.error("REST MEXC spot order status query failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Populates order object and execution report with all fields extracted from MEXC order JSON
     * @param order The order object to populate
     * @param orderJson The complete order JSON response as string
     * @param clientOrderId The client order ID
     */
    private void populateOrderStatusJson(final Order order, final String orderJson, final String clientOrderId) {

        // Extract all order fields from MEXC response
        final String origQty = minExtract(orderJson, "origQty");
        final String executedQty = minExtract(orderJson, "executedQty");
        final String cummulativeQuoteQty = minExtract(orderJson, "cummulativeQuoteQty");
        final String status = minExtract(orderJson, "status");
        final String type = minExtract(orderJson, "type");
        final String side = minExtract(orderJson, "side");

        final String updateTime = minExtract(orderJson, "updateTime");

        final String cancelReason = minExtract(orderJson, "cancelReason");


        LOGGER.debug("Extracted MEXC order details - orderId: " + order.getOrderId() + ", symbol: " + order.getSymbol() +
                ", status: " + status + ", executedQty: " + executedQty +
                ", cummulativeQuoteQty: " + cummulativeQuoteQty + ", side: " + side + ", type: " + type);

        // Parse numeric values safely
        final double origQtyDouble = parseDoubleSafe(origQty);
        final double executedQtyDouble = parseDoubleSafe(executedQty);
        final long updateTimeLong = parseLongSafe(updateTime);

        // Convert to internal scales
        final long executedQtyLong = MbxMath.changeScale(executedQtyDouble, order.getQtyScale());
        final long leavesQtyLong = MbxMath.changeScale(origQtyDouble - executedQtyDouble, order.getQtyScale());

        // Create or update execution report
        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                    0, 0, 0, 0, order.getSide(), 0);
        }

        
        if ("FILLED".equalsIgnoreCase(status)) {
            LOGGER.debug("MEXC spot order status - FILLED for clientOrderId: " + clientOrderId + 
                    ", executedQty: " + executedQty +
                    ", cummulativeQuoteQty: " + cummulativeQuoteQty);
            order.setExecuted(true);
        } else if ("CANCELED".equalsIgnoreCase(status)) {
            LOGGER.debug("MEXC spot order status - CANCELED for clientOrderId: " + clientOrderId + 
                    ", cancelReason: " + cancelReason);
            order.setRejected(true);
            order.setError(cancelReason);
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            LOGGER.debug("MEXC spot order status - REJECTED for clientOrderId: " + clientOrderId + 
                    ", cancelReason: " + cancelReason);
            order.setRejected(true);
            order.setError(cancelReason);
        } else if ("EXPIRED".equalsIgnoreCase(status)) {
            LOGGER.debug("MEXC spot order status - EXPIRED for clientOrderId: " + clientOrderId);
            order.setRejected(true);
            order.setError("Order expired");
        }

        // Update execution report with all extracted fields
        executionMessage.setClOrdId(clientOrderId);
        executionMessage.setPrice(order.getPrice());
        executionMessage.setPriceScale(order.getPriceScale());
        executionMessage.setOrderQty(order.getQty());
        executionMessage.setOrderQtyScale(order.getQtyScale());
        executionMessage.setCumQty(executedQtyLong);
        executionMessage.setLeavesQty(leavesQtyLong);
        executionMessage.setAvgPx(order.getPrice());
        executionMessage.setTimeInForce(order.getTimeInForce());
        executionMessage.setInputTime(updateTimeLong);

        LOGGER.debug("Updating execution report for clientOrderId: " + clientOrderId + " with status: " + status);
        subscription.updateExecutionReport(executionMessage);
        subscription.updateOrder(clientOrderId, order);

    }


    /**
     * Creates a new listen key for user data stream
     * @return listen key string for websocket connection, or null if failed
     */
    public String createListenKey() {
        try {
            final long timestamp = System.currentTimeMillis();
            final String queryString = "timestamp=" + timestamp;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, queryString);
            
            final String url = MAINNET_BASE_URL + "/api/v3/userDataStream?" + queryString + "&signature=" + signature;
            
            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);

            LOGGER.debug("Creating MEXC listen key with URL: " + url);
            final HttpUtils.Response response = HttpUtils.post(url, headers, null, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            
            if (response != null) {
                LOGGER.debug("MEXC listen key response - status: " + response.getCode() + ", body: " + response.getData());
                
                if (response.getCode() == 200) {
                    final String responseData = response.getData();
                    if (responseData != null && !responseData.trim().isEmpty()) {
                        final String listenKey = minExtract(responseData, "listenKey");
                        if (listenKey != null && !listenKey.trim().isEmpty()) {
                            LOGGER.info("Successfully created MEXC listen key");
                            return listenKey;
                        } else {
                            LOGGER.warn("MEXC listen key not found in response: " + responseData);
                        }
                    } else {
                        LOGGER.warn("MEXC listen key response body is empty");
                    }
                } else {
                    LOGGER.warn("MEXC listen key creation failed with status: " + response.getCode() + ", body: " + response.getData());
                }
            } else {
                LOGGER.warn("MEXC listen key creation - null response from HTTP request");
            }
        } catch (final Exception e) {
            LOGGER.error("MEXC listen key creation failed: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Extends the validity of an existing listen key
     * @param listenKey The listen key to extend
     * @return true if extension was successful, false otherwise
     */
    public boolean extendListenKey(final String listenKey) {
        try {
            final String url = MAINNET_BASE_URL + "/api/v3/userDataStream";
            final String requestBody = "listenKey=" + listenKey;
            
            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            final HttpUtils.Response response = HttpUtils.put(url, headers, requestBody.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully extended MEXC listen key");
                return true;
            } else {
                LOGGER.warn("MEXC listen key extension failed with status: " + (response != null ? response.getCode() : "null"));
            }
        } catch (final Exception e) {
            LOGGER.error("MEXC listen key extension failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Deletes and invalidates a listen key
     * @param listenKey The listen key to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteListenKey(final String listenKey) {
        try {
            final String url = MAINNET_BASE_URL + "/api/v3/userDataStream?listenKey=" + listenKey;
            
            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);

            final HttpUtils.Response response = HttpUtils.delete(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.info("Successfully deleted MEXC listen key");
                return true;
            } else {
                LOGGER.warn("MEXC listen key deletion failed with status: " + (response != null ? response.getCode() : "null"));
            }
        } catch (final Exception e) {
            LOGGER.error("MEXC listen key deletion failed: " + e.getMessage());
        }
        return false;
    }

  /*  *//**
     * Places a futures order using the contract API
     * @param order The order object to update with response data
     * @param symbol Trading pair symbol
     * @param side Order side (1=open long, 2=close short, 3=open short, 4=close long)
     * @param type Order type (1=limit, 2=post only, 3=IOC, 4=FOK, 5=market)
     * @param vol Order volume as string
     * @param price Order price as string (required for non-market orders)
     * @return true if request was sent successfully, false otherwise
     *//*
    public boolean placeFutureOrder(final Order order, final String symbol, final int side, final int type, 
                                    final String vol, final String price) {
        try {
            final long timestamp = System.currentTimeMillis();
            
            // Build request parameters for contract API
            final StringBuilder paramBuilder = new StringBuilder();
            paramBuilder.append("symbol=").append(symbol);
            paramBuilder.append("&side=").append(side);
            paramBuilder.append("&type=").append(type);
            paramBuilder.append("&vol=").append(vol);
            
            if (type != 5 && price != null) { // Not market order
                paramBuilder.append("&price=").append(price);
            }
            
            paramBuilder.append("&externalOid=").append(order.getClOrdId());
            
            final String requestParam = paramBuilder.toString();
            
            // Contract API signature: accessKey + timestamp + requestParam
            final String signatureInput = apiKey + timestamp + requestParam;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, signatureInput);

            final String url = "https://contract.mexc.com/api/v1/private/order/submit";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("ApiKey", apiKey);
            headers.put("Request-Time", String.valueOf(timestamp));
            headers.put("Signature", signature);
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            LOGGER.debug("Placing MEXC future order via contract API for symbol: " + symbol + " side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestParam.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT);
            
            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("Contract API MEXC future order status=" + resp.getCode() + " body=" + resp.getData());
            }
            
            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                // Check for successful response
                final String success = minExtract(json, "success");
                if ("true".equals(success)) {
                    final String orderId = minExtract(json, "data");
                    if (orderId != null) {
                        order.setOrderId(StringUtil.toLong(orderId));
                        LOGGER.info("MEXC future order placed successfully with orderId: " + orderId);
                    }
                } else {
                    final String code = minExtract(json, "code");
                    final String msg = minExtract(json, "msg");
                    LOGGER.error("MEXC future order failed: code=" + code + " msg=" + msg);
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("Contract API MEXC future order failed: " + e.getMessage());
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }*/

    /**
     * Cancels a spot order via REST API
     * @param order The order object to update with cancellation status
     * @param symbol Trading pair symbol
     * @param clientOrderId Client-specified order ID
     * @return true if cancellation was successful, false otherwise
     */
    public boolean cancelSpotOrderRest(final Order order, final String symbol, final String clientOrderId) {
        try {
            final long timestamp = System.currentTimeMillis();
            final String queryString = "symbol=" + symbol + "&origClientOrderId=" + clientOrderId + "&timestamp=" + timestamp;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, queryString);

            final String url = MAINNET_BASE_URL + "/api/v3/order?" + queryString + "&signature=" + signature;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);

            LOGGER.debug("Cancelling MEXC spot order for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.delete(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Cancel MEXC spot order failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            LOGGER.debug("Cancel order response: " + json);

            // Check for successful cancellation
            final String status = minExtract(json, "status");
            if (!StringUtils.isEmpty(status)|| "CANCELED".equalsIgnoreCase(status) ) {  //Added this condition of is not empy because, getting status non cancel and order is already cancelled
                LOGGER.info("Successfully cancelled MEXC spot order for clientOrderId: " + clientOrderId);
                order.setRejected(true);
                
                ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionMessage == null) {
                    executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                            order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                            0, 0, 0, 0, order.getSide(), 0);
                    executionMessage.setClOrdId(clientOrderId);
                }
                executionMessage.setExecType(ExecType.CANCELED);
                executionMessage.setOrdStatus(OrdStatus.CANCELED);
                subscription.updateExecutionReport(executionMessage);
                subscription.updateOrder(clientOrderId, order);
                return true;
            } else {
                final String msg = minExtract(json, "msg");
                LOGGER.warn("Cancel MEXC spot order failed: " + msg);
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("Cancel MEXC spot order failed: " + e.getMessage(), e);
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
            final String queryString = "timestamp=" + timestamp;
            final String signature = HMAC.hmacSha256Hex(secretUtf8, queryString);

            final String url = MAINNET_BASE_URL + "/api/v3/openOrders?" + queryString + "&signature=" + signature;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MEXC-APIKEY", apiKey);

            LOGGER.debug("Getting all open MEXC spot orders");
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Get all open MEXC spot orders failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return null;
            }
            LOGGER.debug("Successfully retrieved all open MEXC spot orders");
            return resp.getData();
        } catch (final Exception e) {
            LOGGER.error("Get all open MEXC spot orders failed: " + e.getMessage());
            return null;
        }
    }

  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    HttpUtils.Response response = HttpUtils.get(MAINNET_BASE_URL + "/api/v3/exchangeInfo", new HashMap<>()
        , ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);
    if (response != null && response.getCode() == 200) {
      try {
        final MEXCExchangeInfoFull info = MAPPER.readValue(response.getData(), MEXCExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          List<ExternalSymbol> symbolStatuses = new ArrayList<>(info.getSymbols().size());
          for (MEXCSymbol mexcSymbol : info.getSymbols()) {
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("mexc");
            symbolStatus.setSymbol(mexcSymbol.getSymbol());
            symbolStatus.setBase(mexcSymbol.getBaseAsset());
            symbolStatus.setQuote(mexcSymbol.getQuoteAsset());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(mexcSymbol.getPermissions() != null && mexcSymbol.getPermissions().contains("FUTURES"));
            symbolStatus.setTradable("1".equals(mexcSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            // symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(mexcSymbol.getQuotePrecision());
            symbolStatus.setQtyScale(mexcSymbol.getBaseAssetPrecision());
            symbolStatuses.add(symbolStatus);
          }
          return symbolStatuses;
        }
      } catch (JsonProcessingException e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }
    return null;
  }

  /**
   * Spot pairs paused/offline (status != 1), restricted (tradeSideType != 1) or no longer listed by MEXC; null on failure.
   * MEXC gives no delisting date: no API for it, and its announcement pages refuse server requests (403).
   */
  public List<DelistedSymbol> getSpotDelistedSymbols() {
    final HttpUtils.Response response = HttpUtils.get(MAINNET_BASE_URL + "/api/v3/exchangeInfo", new HashMap<>(),
        subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
    if (response == null || response.getCode() != 200) {
      LOGGER.warn("MEXC exchangeInfo failed with status: " + (response != null ? response.getCode() : "null"));
      return null;
    }
    final long now = System.currentTimeMillis();
    final List<DelistedSymbol> delisted = new ArrayList<>();
    final Map<String, String[]> listed = new HashMap<>();
    try {
      for (final JsonNode item : MAPPER.readTree(response.getData()).path("symbols")) {
        final String symbol = item.path("symbol").asText();
        final String[] baseQuote = {item.path("baseAsset").asText(), item.path("quoteAsset").asText()};
        listed.put(symbol, baseQuote);
        final String stopped = stoppedStatus(item);
        if (stopped != null) {
          delisted.add(toDelisted(symbol, baseQuote, stopped, now));
        }
      }
    } catch (final Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
      return null;
    }
    final List<DelistedSymbol> removed = listedTracker.update(subscription.getExchange(), false, listed, now);
    if (removed == null) {
      return null;
    }
    delisted.addAll(removed);
    return delisted;
  }

  // null when the pair trades normally, else its status; status 2 paused, 3 offline; tradeSideType 2 buy only, 3 sell only, 4 closed
  private static String stoppedStatus(final JsonNode item) {
    final String status = item.path("status").asText();
    final String side = item.path("tradeSideType").asText("1");
    return "1".equals(status) && "1".equals(side) ? null : "status=" + status + " tradeSideType=" + side;
  }

  private DelistedSymbol toDelisted(final String symbol, final String[] baseQuote, final String status, final long now) {
    final DelistedSymbol delisted = new DelistedSymbol();
    delisted.setExchange(subscription.getExchange());
    delisted.setFutures(false);
    delisted.setSymbol(symbol);
    delisted.setBase(baseQuote[0]);
    delisted.setQuote(baseQuote[1]);
    delisted.setStatus(status);
    delisted.setTradingDisabled(true);
    delisted.setDetectedAt(now);
    return delisted;
  }

  public Ticker getTicker(final String symbol) {
    try {
        final String url = MAINNET_BASE_URL + "/api/v3/ticker/24hr?symbol=" + symbol;
        final Map<String, Object> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
        if (response == null || response.getCode() != 200) {
            LOGGER.warn("Get ticker failed for symbol: " + symbol + " status: " + (response != null ? response.getCode() : "null"));
            return null;
        }
        final String json = response.getData();
        final double last             = parseDoubleSafe(minExtract(json, "lastPrice"));
        final double bid              = parseDoubleSafe(minExtract(json, "bidPrice"));
        final double ask              = parseDoubleSafe(minExtract(json, "askPrice"));
        final double high             = parseDoubleSafe(minExtract(json, "highPrice"));
        final double low              = parseDoubleSafe(minExtract(json, "lowPrice"));
        final double open             = parseDoubleSafe(minExtract(json, "openPrice"));
        final double baseVolume       = parseDoubleSafe(minExtract(json, "volume"));
        final double quoteVolume      = parseDoubleSafe(minExtract(json, "quoteVolume"));
        final double bidSize          = parseDoubleSafe(minExtract(json, "bidQty"));
        final double askSize          = parseDoubleSafe(minExtract(json, "askQty"));
        final long timestamp          = parseLongSafe(minExtract(json, "closeTime"));
        final double percentageChange = parseDoubleSafe(minExtract(json, "priceChangePercent"));
        LOGGER.debug("Retrieved ticker for symbol: " + symbol + " last=" + last + " bid=" + bid + " ask=" + ask);
        return new Ticker(symbol, 0, open, last, bid, ask, high, low, baseVolume, quoteVolume, timestamp, bidSize, askSize, percentageChange);
    } catch (final Exception e) {
        LOGGER.error("Get ticker failed for symbol: " + symbol + " " + e.getMessage());
        return null;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MEXCExchangeInfoFull {
    private List<MEXCSymbol> symbols;

    public List<MEXCSymbol> getSymbols() {
      return symbols;
    }

    public void setSymbols(List<MEXCSymbol> symbols) {
      this.symbols = symbols;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MEXCSymbol {
    private String symbol;
    private String baseAsset;
    private String quoteAsset;
    private String status;
    private int baseAssetPrecision;
    private int quotePrecision;
    private List<String> permissions;

    public MEXCSymbol() {}

    public final String getSymbol() {
      return symbol;
    }

    public void setSymbol(final String symbol) {
      this.symbol = symbol;
    }

    public final String getBaseAsset() {
      return baseAsset;
    }

    public void setBaseAsset(final String baseAsset) {
      this.baseAsset = baseAsset;
    }

    public final String getQuoteAsset() {
      return quoteAsset;
    }

    public void setQuoteAsset(final String quoteAsset) {
      this.quoteAsset = quoteAsset;
    }

    public final String getStatus() {
      return status;
    }

    public void setStatus(final String status) {
      this.status = status;
    }

    public final int getBaseAssetPrecision() {
      return baseAssetPrecision;
    }

    public final void setBaseAssetPrecision(final int baseAssetPrecision) {
      this.baseAssetPrecision = baseAssetPrecision;
    }

    public final int getQuotePrecision() {
      return quotePrecision;
    }

    public final void setQuotePrecision(final int quotePrecision) {
      this.quotePrecision = quotePrecision;
    }

    public final List<String> getPermissions() {
      return permissions;
    }

    public final void setPermissions(final List<String> permissions) {
      this.permissions = permissions;
    }
  }
}

