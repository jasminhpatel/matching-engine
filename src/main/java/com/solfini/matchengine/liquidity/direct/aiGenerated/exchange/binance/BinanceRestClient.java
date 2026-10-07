package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;
import com.solfini.util.PrivateKeyBasedSigner;
import com.solfini.util.StringUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.solfini.matchengine.liquidity.direct.aiGenerated.BinanceFastClient;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class BinanceRestClient {
    private static final CustomLogger LOGGER = CustomLogger.getLogger(BinanceRestClient.class);
    private static final String REST_FUTURE_BASE = Context.getBinanceFuturesRest();
    private static final String REST_SPOT_BASE = Context.getBinanceSpotRest();
    private static final ObjectMapper MAPPER = new ObjectMapper(); // todo use string parsing
    private static final int PROXY_PORT = 8888;
    private static final String BINANCE = "BINANCE";
    // deliveryDate Binance uses for perpetuals with no delisting scheduled (2100-12-25)
    static final long PERPETUAL_NO_DELIVERY_DATE = 4133404800000L;
    private final String apiKey;
    private final byte[] secretUtf8;
    private final ExchangeSubscription subscription;

    public BinanceRestClient(final String apiKey, final byte[] apiSecret, final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.secretUtf8 = apiSecret;
        this.subscription = subscription;
    }

    public String createListenKey() {
        try {
            final String url = REST_FUTURE_BASE + "/fapi/v1/listenKey";
            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);

            final HttpUtils.Response resp = HttpUtils.post(url, headers, new byte[0], subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                final String errorMsg = "listenKey http " + (resp != null ? resp.getCode() : "null") + " " + (resp != null ? resp.getData() : "null");
                LOGGER.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            final String lk = minExtract(resp.getData(), "listenKey");
            if (lk == null) {
                LOGGER.error("listenKey missing from response");
                throw new IllegalStateException("listenKey missing");
            }
            LOGGER.info("Successfully created listenKey");
            return lk;
        } catch (final Exception e) {
            LOGGER.error("createListenKey failed: " + e.getMessage());
            throw new RuntimeException("createListenKey failed: " + e.getMessage(), e);
        }
    }

    public void keepAliveListenKey(final String listenKey) {
        try {
            final String url = REST_FUTURE_BASE + "/fapi/v1/listenKey";
            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            final String requestData = "listenKey=" + listenKey;
            final HttpUtils.Response resp = HttpUtils.put(url, headers, requestData.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() == 200)
                LOGGER.debug("listenKey keepalive ok");
            else
                LOGGER.warn("listenKey keepalive status " + (resp != null ? resp.getCode() : "null"));
        } catch (final Exception e) {
            LOGGER.error("keepAlive failed: " + e.getMessage());
        }
    }

    public void deleteListenKey(final String listenKey) {
        try {
            final String url = REST_FUTURE_BASE + "/fapi/v1/listenKey?listenKey=" + listenKey;
            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);

            HttpUtils.delete(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            LOGGER.debug("Successfully deleted listenKey");
        } catch (final Exception e) {
            LOGGER.error("Failed to delete listenKey: " + e.getMessage());
        }
    }

    public String getSpotBalanceSnapshot() throws Exception {
        final long timeStamp = System.currentTimeMillis();
        final String signature = PrivateKeyBasedSigner.generateSignature(secretUtf8,
                "timestamp=" + timeStamp);
        final String encodedSignature = URLEncoder.encode(signature, StandardCharsets.UTF_8);
        final String queryString = "timestamp=" + timeStamp + "&signature=" + encodedSignature;
        final String url = REST_SPOT_BASE + "/api/v3/account?" + queryString;
        try {
            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);

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

    public String getFutureAccountSnapshot() {

        try {
            final long timeStamp = System.currentTimeMillis();
            final String signature = PrivateKeyBasedSigner.generateSignature(secretUtf8, "timestamp=" + timeStamp);
            final String encodedSignature = URLEncoder.encode(signature, StandardCharsets.UTF_8);
            final String queryString = "timestamp=" + timeStamp + "&signature=" + encodedSignature;

            final String url = REST_FUTURE_BASE + "/fapi/v2/account?" + queryString;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);

            LOGGER.debug("Future account snapshot URL: " + url);
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved future account snapshot");
                return response.getData();
            } else {
                LOGGER.warn("Future account bootstrap failed with status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Future account bootstrap failed: " + e.getMessage());
        }
        return null;
    }

    public boolean sendFutureOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce, final String quantityStr,
                                       final String priceStr, final String positionSide, final boolean reduceOnly)
            throws Exception {
        final long ts = System.currentTimeMillis();
        final StringBuilder queryBuilder = new StringBuilder(192);
        // use DECIMAL as strings per spec; keep order of params conventional then signature
        append(queryBuilder, "symbol", symbol);
        append(queryBuilder, "side", side);
        append(queryBuilder, "type", type);
        if (timeInForce != null)
            append(queryBuilder, "timeInForce", timeInForce);
        if (quantityStr != null)
            append(queryBuilder, "quantity", quantityStr);
        if (priceStr != null)
            append(queryBuilder, "price", priceStr);
        if (positionSide != null)
            append(queryBuilder, "positionSide", positionSide);
        if (reduceOnly)
            append(queryBuilder, "reduceOnly", "true");
        append(queryBuilder, "newClientOrderId", BinanceFastClient.withBrokerId(subscription.getBrokerId(),order.getClOrdId()));
        append(queryBuilder, "timestamp", Long.toString(ts));
        final String sig = PrivateKeyBasedSigner.generateSignature(secretUtf8, queryBuilder.toString());
        final String encodedSignature = URLEncoder.encode(sig, StandardCharsets.UTF_8);

        queryBuilder.append("&signature=").append(encodedSignature);

        final String url = REST_FUTURE_BASE + "/fapi/v1/order";

        try {
            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            LOGGER.debug("Sending future order REST for symbol: " + symbol + " side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, queryBuilder.toString().getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("REST future order status=" + resp.getCode() + " body=" + resp.getData());
            }
            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                final String status = minExtract(json, "status");
                final String clientOrderId = minExtract(json, "clientOrderId");

                ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionMessage == null) {
                    executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                            order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                            0, 0, 0, 0, order.getSide(), 0);
                    executionMessage.setExecType(ExecType.NEW);
                }

                executionMessage.setClOrdId(clientOrderId);

                if ("FILLED".equalsIgnoreCase(status)) {
                    LOGGER.info("Future order FILLED for clientOrderId: " + clientOrderId);
                    order.setExecuted(true);
                    executionMessage.setExecType(ExecType.TRADE);
                } else if ("CANCELED".equalsIgnoreCase(status)) {
                    LOGGER.info("Future order CANCELED for clientOrderId: " + clientOrderId);
                    order.setRejected(true);
                    executionMessage.setExecType(ExecType.CANCELED);
                } else {
                    executionMessage.setExecType(ExecType.NEW);
                }

                subscription.updateExecutionReport(executionMessage);
                subscription.updateOrder(order.getClOrdId(), order);
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST future order failed: " + e.getMessage());
            return false;
        }
    }

    public boolean sendSpotOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce, final String quantityStr,
                                     final String priceStr, final String clientOrderId)
            throws Exception {
        final long ts = System.currentTimeMillis();
        final StringBuilder queryBuilder = new StringBuilder(192);
        append(queryBuilder, "symbol", symbol);         // e.g. "BTCUSDT"
        append(queryBuilder, "side", side);             // "BUY" or "SELL"
        append(queryBuilder, "type", type);             // "LIMIT", "MARKET", etc.
        if (timeInForce != null)
            append(queryBuilder, "timeInForce", timeInForce); // e.g. "GTC"
        if (quantityStr != null)
            append(queryBuilder, "quantity", quantityStr);    // as string, e.g. "0.001"
        if (priceStr != null)
            append(queryBuilder, "price", priceStr);          // for LIMIT, e.g. "64000.00"
        if (clientOrderId != null)
            append(queryBuilder, "newClientOrderId", BinanceFastClient.withBrokerId(subscription.getBrokerId(),clientOrderId));
        append(queryBuilder, "timestamp", Long.toString(ts));

        final String sig = PrivateKeyBasedSigner.generateSignature(secretUtf8, queryBuilder.toString());
        final String encodedSignature = URLEncoder.encode(sig, StandardCharsets.UTF_8);

        queryBuilder.append("&signature=").append(encodedSignature);

        final String url = REST_SPOT_BASE + "/api/v3/order";

        try {
            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            LOGGER.debug("Sending spot order REST for symbol: " + symbol + " side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, queryBuilder.toString().getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("REST spot order status=" + resp.getCode() + " body=" + resp.getData());
                return true;
            }
            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                final String status = minExtract(json, "status");
                final String clientOrderIdResp = minExtract(json, "clientOrderId");


                ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionMessage == null) {
                    executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                            order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                            0, 0, 0, 0, order.getSide(), 0);
                    executionMessage.setExecType(ExecType.NEW);
                }

                if ("FILLED".equalsIgnoreCase(status)) {
                    LOGGER.info("Spot order FILLED for clientOrderId: " + clientOrderIdResp);
                    order.setExecuted(true);
                    executionMessage.setExecType(ExecType.TRADE);
                } else if ("CANCELED".equalsIgnoreCase(status)) {
                    LOGGER.info("Spot order CANCELED for clientOrderId: " + clientOrderIdResp);
                    order.setRejected(true);
                    executionMessage.setExecType(ExecType.CANCELED);
                }
                subscription.updateExecutionReport(executionMessage);
                subscription.updateOrder(order.getClOrdId(), order);
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST spot order failed: " + e.getMessage());
            return false;
        }

    }

    public boolean querySpotOrderStatus(final Order order, final String symbol, final String clientOrderId) {
        try {
            final long timestamp = System.currentTimeMillis();
            final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("symbol=").append(symbol)
                    .append("&origClientOrderId=").append(BinanceFastClient.withBrokerId(subscription.getBrokerId(),clientOrderId))
                    .append("&timestamp=").append(timestamp);
            final String signature = PrivateKeyBasedSigner.generateSignature(secretUtf8, queryBuilder.toString());
            final String encodedSignature = URLEncoder.encode(signature, StandardCharsets.UTF_8);

            queryBuilder.append("&signature=").append(encodedSignature);
            final String url = REST_SPOT_BASE + "/api/v3/order?" + queryBuilder;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);

            LOGGER.debug("Querying spot order status for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("REST spot order status query failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }
            final String json = resp.getData();


            final String status = minExtract(json, "status");
            final String executedQty = minExtract(json, "executedQty");
            final String cumulativeQuoteQty = minExtract(json, "cummulativeQuoteQty");
            final String updateTime = minExtract(json, "updateTime");

            LOGGER.debug("Extracted spot order details - orderId: " + order.getOrderId() + ", status: " + status +
                    ", executedQty: " + executedQty +
                    ", cumulativeQuoteQty: " + cumulativeQuoteQty);

            // Parse numeric values
            final double executedQtyDouble = parseDoubleSafe(executedQty);
            final long updateTimeLong = parseLongSafe(updateTime);

            final long executedQtyLong = MbxMath.changeScale(executedQtyDouble, order.getQtyScale());
            final OrdStatus orderStatus = getBinanceOrderStatus(status);

            // Create or update execution report
            ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
            if (executionMessage == null) {
                executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                        order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                        0, 0, 0, 0, order.getSide(), 0);
                executionMessage.setExecType(ExecType.NEW);
            }

            // Update order based on status
            if (OrdStatus.FILLED.equals(orderStatus)) {
                LOGGER.debug("Spot order status query - FILLED for clientOrderId: " + clientOrderId +
                        ", executedQty: " + executedQty + ", cumulativeQuoteQty: " + cumulativeQuoteQty);
                order.setExecuted(true);
                executionMessage.setExecType(ExecType.TRADE);
            } else if (OrdStatus.CANCELED.equals(orderStatus)) {
                LOGGER.debug("Spot order status query - CANCELED for clientOrderId: " + clientOrderId);
                order.setRejected(true);
                executionMessage.setExecType(ExecType.CANCELED);
            } else if (OrdStatus.REJECTED.equals(orderStatus)) {
                LOGGER.debug("Spot order status query - REJECTED for clientOrderId: " + clientOrderId);
                executionMessage.setExecType(ExecType.REJECTED);
                order.setRejected(true);
            }

            // Populate execution message with all extracted fields
            executionMessage.setClOrdId(clientOrderId);
            executionMessage.setOrdStatus(orderStatus);
            executionMessage.setTimeInForce(order.getTimeInForce());
            executionMessage.setInputTime(updateTimeLong);
            executionMessage.setPrice(order.getPrice());
            executionMessage.setPriceScale(order.getPriceScale());
            executionMessage.setOrderQty(order.getQty());
            executionMessage.setOrderQtyScale(order.getQtyScale());
            executionMessage.setCumQty(executedQtyLong);
            executionMessage.setLeavesQty(order.getQty() - executedQtyLong);

            LOGGER.debug("Updating execution report for clientOrderId: " + clientOrderId + " with status: " + orderStatus);
            subscription.updateOrder(clientOrderId, order);
            subscription.updateExecutionReport(executionMessage);

            return true;
        } catch (final Exception e) {
            LOGGER.error("REST spot order status query failed: " + e.getMessage());
            return false;
        }
    }

    public boolean queryFuturesOrderStatus(final Order order, final String symbol, final String clientOrderId) {
        try {
            final long timestamp = System.currentTimeMillis();
            final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("symbol=").append(symbol)
                    .append("&origClientOrderId=").append(clientOrderId)
                    .append("&timestamp=").append(timestamp);
            final String signature = PrivateKeyBasedSigner.generateSignature(secretUtf8, queryBuilder.toString());
            final String encodedSignature = URLEncoder.encode(signature, StandardCharsets.UTF_8);

            queryBuilder.append("&signature=").append(encodedSignature);

            final String url = REST_FUTURE_BASE + "/fapi/v1/order?" + queryBuilder;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);

            LOGGER.debug("Querying futures order status for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("FUTURES order status query failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }
            final String json = resp.getData();

            // Extract all order fields from response
            final String status = minExtract(json, "status");
            final String executedQty = minExtract(json, "executedQty");
            final String avgPrice = minExtract(json, "avgPrice");
            final String cumQuote = minExtract(json, "cumQuote");
            final String positionSide = minExtract(json, "positionSide");
            final String stopPrice = minExtract(json, "stopPrice");
            final String updateTime = minExtract(json, "updateTime");
            final String reduceOnly = minExtract(json, "reduceOnly");

            LOGGER.debug("Extracted futures order details - orderId: " + order.getOrderId() + ", status: " + status +
                    ", executedQty: " + executedQty +
                    ", avgPrice: " + avgPrice + ", cumQuote: " + cumQuote + ", type: , positionSide: " + positionSide);

            // Create or update execution report
            ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
            if (executionMessage == null) {
                executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                        order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                        0, 0, 0, 0, order.getSide(), 0);
                executionMessage.setExecType(ExecType.NEW);
            }

            // Parse numeric values
            final double executedQtyDouble = parseDoubleSafe(executedQty);
            final double avgPriceDouble = parseDoubleSafe(avgPrice);
            final long updateTimeLong = parseLongSafe(updateTime);
            final OrdStatus orderStatus = getBinanceOrderStatus(status);

            // Convert to order scales
            final long executedQtyLong = MbxMath.changeScale(executedQtyDouble, order.getQtyScale());
            final long avgPriceLong = MbxMath.changeScale(avgPriceDouble, order.getPriceScale());
            final long leavesQtyLong = order.getQty() - executedQtyLong;

            // Update order based on status
            if (OrdStatus.FILLED.equals(orderStatus)) {
                LOGGER.debug("Futures order status query - FILLED for clientOrderId: " + clientOrderId +
                        ", executedQty: " + executedQty + ", avgPrice: " + avgPrice + ", cumQuote: " + cumQuote);
                order.setExecuted(true);
            } else if (OrdStatus.CANCELED.equals(orderStatus)) {
                LOGGER.debug("Futures order status query - CANCELED for clientOrderId: " + clientOrderId);
                order.setRejected(true);
                executionMessage.setExecType(ExecType.CANCELED);
            } else if (OrdStatus.REJECTED.equals(orderStatus)) {
                LOGGER.debug("Futures order status query - REJECTED for clientOrderId: " + clientOrderId);
                order.setRejected(true);
                executionMessage.setExecType(ExecType.REJECTED);
            } else if (OrdStatus.EXPIRED.equals(orderStatus)) {
                LOGGER.debug("Futures order status query - EXPIRED for clientOrderId: " + clientOrderId);
                order.setRejected(true);
                executionMessage.setExecType(ExecType.EXPIRED);
            }

            // Populate execution message with all extracted fields
            executionMessage.setClOrdId(clientOrderId);
            executionMessage.setOrdStatus(orderStatus);
            executionMessage.setTimeInForce(order.getTimeInForce());
            executionMessage.setInputTime(updateTimeLong);
            executionMessage.setPrice(order.getPrice());
            executionMessage.setPriceScale(order.getPriceScale());
            executionMessage.setOrderQty(order.getQty());
            executionMessage.setOrderQtyScale(order.getQtyScale());
            executionMessage.setCumQty(executedQtyLong);
            executionMessage.setLeavesQty(leavesQtyLong);
            executionMessage.setAvgPx(avgPriceLong);

            LOGGER.debug("Updating execution report for clientOrderId: " + clientOrderId + " with status: " + status +
                    ", orderQty: " + order.getQty() + ", cumQty: " + executedQtyLong + ", leavesQty: " + leavesQtyLong +
                    ", avgPx: " + avgPriceLong + ", positionSide: " + positionSide + ", reduceOnly: " + reduceOnly);
            subscription.updateOrder(clientOrderId, order);
            subscription.updateExecutionReport(executionMessage);

            return true;
        } catch (final Exception e) {
            LOGGER.error("FUTURES order status query failed: " + e.getMessage());
            return false;
        }
    }

    public boolean cancelSpotOrderRest(final Order order, final String symbol, final String clientOrderId) {
        try {
            final long timestamp = System.currentTimeMillis();
            final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("symbol=").append(symbol)
                    .append("&origClientOrderId=").append(BinanceFastClient.withBrokerId(subscription.getBrokerId(),clientOrderId))
                    .append("&timestamp=").append(timestamp);

            final String signature = PrivateKeyBasedSigner.generateSignature(secretUtf8, queryBuilder.toString());
            final String encodedSignature = URLEncoder.encode(signature, StandardCharsets.UTF_8);
            queryBuilder.append("&signature=").append(encodedSignature);

            final String url = REST_SPOT_BASE + "/api/v3/order?" + queryBuilder;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);

            LOGGER.debug("Cancelling spot order for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.delete(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("REST cancel spot order failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }
            final String json = resp.getData();
            final String status = minExtract(json, "status");
            if ("CANCELED".equalsIgnoreCase(status)) {
                LOGGER.info("Successfully cancelled spot order for clientOrderId: " + clientOrderId);
                order.setRejected(true);
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST cancel spot order failed: " + e.getMessage());
            return true;
        }
    }

    public boolean cancelFuturesOrderRest(final Order order, final String symbol, final String clientOrderId) {
        try {
            final long timestamp = System.currentTimeMillis();
            final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("symbol=").append(symbol)
                    .append("&origClientOrderId=").append(BinanceFastClient.withBrokerId(subscription.getBrokerId(),clientOrderId))
                    .append("&timestamp=").append(timestamp);

            final String signature = PrivateKeyBasedSigner.generateSignature(secretUtf8, queryBuilder.toString());
            final String encodedSignature = URLEncoder.encode(signature, StandardCharsets.UTF_8);

            queryBuilder.append("&signature=").append(encodedSignature);

            final String url = REST_FUTURE_BASE + "/fapi/v1/order?" + queryBuilder; // Futures endpoint

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);

            LOGGER.debug("Cancelling futures order for clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.delete(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("FUTURES cancel order failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }
            final String json = resp.getData();
            final String status = minExtract(json, "status");
            if ("CANCELED".equalsIgnoreCase(status)) {
                LOGGER.info("Successfully cancelled futures order for clientOrderId: " + clientOrderId);
                order.setRejected(true);
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("FUTURES cancel order failed: " + e.getMessage());
            return false;
        }
    }

    public List<ExternalSymbol> getExchangeInstrumentsFull() {
    // todo optimize Json handling
    final List<ExternalSymbol> symbolStatuses = new ArrayList<>();
    HttpUtils.Response response = HttpUtils.get(REST_SPOT_BASE + "/api/v3/exchangeInfo", new HashMap<>()
        , ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);
    if (response != null && response.getCode() == 200) {
      try {
        final BinanceExchangeInfoFull info = MAPPER.readValue(response.getData(), BinanceExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          for (BinanceSymbol binanceSymbol : info.getSymbols()) {
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("binance");
            symbolStatus.setSymbol(binanceSymbol.getSymbol());
            symbolStatus.setBase(binanceSymbol.getBaseAsset());
            symbolStatus.setQuote(binanceSymbol.getQuoteAsset());
            symbolStatus.setPrompt("");
            symbolStatus.setTradable("TRADING".equals(binanceSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            // symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(binanceSymbol.getQuotePrecision());
            symbolStatus.setQtyScale(binanceSymbol.getBaseAssetPrecision());
            for (Filter filter : binanceSymbol.getFilters()) {
              if ("PRICE_FILTER".equals(filter.getFilterType())) {
                int priceScale = (int) -Math.log10(StringUtil.toDouble(filter.getTickSize()));
                if (priceScale >= 0) {
                  symbolStatus.setPriceScale(priceScale);
                }
              } else if ("LOT_SIZE".equals(filter.getFilterType())) {
                int qtyScale = (int) -Math.log10(StringUtil.toDouble(filter.getStepSize()));
                if (qtyScale >= 0) {
                  symbolStatus.setQtyScale(qtyScale);
                }
              }
            }
            symbolStatuses.add(symbolStatus);
          }
        }
      } catch (Exception e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }
    response = HttpUtils.get(REST_FUTURE_BASE + "/fapi/v1/exchangeInfo", new HashMap<>()
        , ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);
    if (response != null && response.getCode() == 200) {
      try {
        final BinanceExchangeInfoFull info = MAPPER.readValue(response.getData(), BinanceExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          for (BinanceSymbol binanceSymbol : info.getSymbols()) {
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("binance");
            symbolStatus.setSymbol(binanceSymbol.getSymbol());
            symbolStatus.setBase(binanceSymbol.getBaseAsset());
            symbolStatus.setQuote(binanceSymbol.getQuoteAsset());
            symbolStatus.setPrompt(binanceSymbol.getPrompt());
            symbolStatus.setTradable(true);
            symbolStatus.setFutures(true);
            symbolStatus.setUpdated(updated);
            // symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(binanceSymbol.getPricePrecision());
            symbolStatus.setQtyScale(binanceSymbol.getQuantityPrecision());
            for (Filter filter : binanceSymbol.getFilters()) {
              if ("PRICE_FILTER".equals(filter.getFilterType())) {
                int priceScale = (int) -Math.log10(StringUtil.toDouble(filter.getTickSize()));
                if (priceScale >= 0) {
                  symbolStatus.setPriceScale(priceScale);
                }
              } else if ("LOT_SIZE".equals(filter.getFilterType())) {
                int qtyScale = (int) -Math.log10(StringUtil.toDouble(filter.getStepSize()));
                if (qtyScale >= 0) {
                  symbolStatus.setQtyScale(qtyScale);
                }
              }
            }
            symbolStatuses.add(symbolStatus);
          }
        }
      } catch (Exception e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }
    return symbolStatuses;
  }

  /**
   * Perpetual futures that are delisted, suspended or scheduled for delisting, from GET /fapi/v1/exchangeInfo.
   * Binance sets deliveryDate of a perpetual to the delisting time once the delisting is announced.
   *
   * @return the delisted symbols, or null when the request failed
   */
  public List<DelistedSymbol> getFuturesDelistedSymbols() {
    final HttpUtils.Response response = HttpUtils.get(REST_FUTURE_BASE + "/fapi/v1/exchangeInfo", new HashMap<>(),
        subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
    if (response == null || response.getCode() != 200) {
      LOGGER.warn("Futures exchangeInfo failed with status: " + (response != null ? response.getCode() : "null"));
      return null;
    }
    try {
      final BinanceExchangeInfoFull info = MAPPER.readValue(response.getData(), BinanceExchangeInfoFull.class);
      final List<DelistedSymbol> delisted = new ArrayList<>();
      final long now = System.currentTimeMillis();
      if (info.getSymbols() != null) {
        for (final BinanceSymbol binanceSymbol : info.getSymbols()) {
          final String contractType = binanceSymbol.getContractType();
          // quarterly contracts expire normally on deliveryDate, that is not a delisting
          if (!"PERPETUAL".equals(contractType) && !"TRADIFI_PERPETUAL".equals(contractType)) {
            continue;
          }
          final boolean delistScheduled = binanceSymbol.getDeliveryDate() != PERPETUAL_NO_DELIVERY_DATE;
          final boolean tradingStopped = !DelistedSymbol.TRADING.equals(binanceSymbol.getStatus())
              && !"PENDING_TRADING".equals(binanceSymbol.getStatus()); // PENDING_TRADING = new listing
          if (delistScheduled || tradingStopped) {
            final DelistedSymbol symbol = new DelistedSymbol();
            symbol.setExchange(BINANCE);
            symbol.setFutures(true);
            symbol.setSymbol(binanceSymbol.getSymbol());
            symbol.setBase(binanceSymbol.getBaseAsset());
            symbol.setQuote(binanceSymbol.getQuoteAsset());
            symbol.setStatus(binanceSymbol.getStatus());
            symbol.setDelistTime(delistScheduled ? binanceSymbol.getDeliveryDate() : 0);
            symbol.setDetectedAt(now);
            delisted.add(symbol);
          }
        }
      }
      return delisted;
    } catch (final Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
      return null;
    }
  }

  /**
   * Spot pairs that are suspended or delisted (GET /api/v3/exchangeInfo with symbolStatus HALT and BREAK) plus the
   * pairs scheduled for delisting (GET /sapi/v1/spot/delist-schedule, needs the API key).
   *
   * @return the delisted symbols, or null when a request failed
   */
  public List<DelistedSymbol> getSpotDelistedSymbols() {
    final Map<String, DelistedSymbol> delisted = new HashMap<>();
    final long now = System.currentTimeMillis();
    for (final String symbolStatus : new String[] {"HALT", "BREAK"}) {
      final HttpUtils.Response response = HttpUtils.get(REST_SPOT_BASE + "/api/v3/exchangeInfo?symbolStatus=" + symbolStatus,
          new HashMap<>(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
      if (response == null || response.getCode() != 200) {
        LOGGER.warn("Spot exchangeInfo " + symbolStatus + " failed with status: " + (response != null ? response.getCode() : "null"));
        return null;
      }
      try {
        final BinanceExchangeInfoFull info = MAPPER.readValue(response.getData(), BinanceExchangeInfoFull.class);
        if (info.getSymbols() != null) {
          for (final BinanceSymbol binanceSymbol : info.getSymbols()) {
            final DelistedSymbol symbol = new DelistedSymbol();
            symbol.setExchange(BINANCE);
            symbol.setFutures(false);
            symbol.setSymbol(binanceSymbol.getSymbol());
            symbol.setBase(binanceSymbol.getBaseAsset());
            symbol.setQuote(binanceSymbol.getQuoteAsset());
            symbol.setStatus(binanceSymbol.getStatus());
            symbol.setDetectedAt(now);
            delisted.put(symbol.getSymbol(), symbol);
          }
        }
      } catch (final Exception e) {
        LOGGER.error(Constants.ERROR_LOG, e);
        return null;
      }
    }

    if (apiKey == null || secretUtf8 == null) {
      LOGGER.warn("No API key, skipping /sapi/v1/spot/delist-schedule; upcoming spot delistings are not included");
      return new ArrayList<>(delisted.values());
    }
    try {
      final long timeStamp = System.currentTimeMillis();
      final String signature = PrivateKeyBasedSigner.generateSignature(secretUtf8, "timestamp=" + timeStamp);
      final String queryString = "timestamp=" + timeStamp + "&signature=" + URLEncoder.encode(signature, StandardCharsets.UTF_8);
      final Map<String, Object> headers = new HashMap<>();
      headers.put("X-MBX-APIKEY", apiKey);
      final HttpUtils.Response response = HttpUtils.get(REST_SPOT_BASE + "/sapi/v1/spot/delist-schedule?" + queryString,
          headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
      if (response == null || response.getCode() != 200) {
        LOGGER.warn("Spot delist-schedule failed with status: " + (response != null ? response.getCode() + " " + response.getData() : "null"));
        return null;
      }
      final BinanceDelistSchedule[] schedules = MAPPER.readValue(response.getData(), BinanceDelistSchedule[].class);
      for (final BinanceDelistSchedule schedule : schedules) {
        if (schedule.getSymbols() == null) {
          continue;
        }
        for (final String name : schedule.getSymbols()) {
          DelistedSymbol symbol = delisted.get(name);
          if (symbol == null) { // still trading until delistTime
            symbol = new DelistedSymbol();
            symbol.setExchange(BINANCE);
            symbol.setFutures(false);
            symbol.setSymbol(name);
            symbol.setStatus(DelistedSymbol.TRADING);
            symbol.setDetectedAt(now);
            delisted.put(name, symbol);
          }
          symbol.setDelistTime(schedule.getDelistTime());
        }
      }
    } catch (final Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
      return null;
    }
    return new ArrayList<>(delisted.values());
  }

    private boolean transferBalance(final String asset, final String amount, final int type)
            throws Exception {
        // type: 1 - transfer from spot account to USDT-M futures account
        // type: 2 - transfer from USDT-M futures account to spot account
        // type: 3 - transfer from spot account to COIN-M futures account
        // type: 4 - transfer from COIN-M futures account to spot account

        final long ts = System.currentTimeMillis();
        final StringBuilder queryBuilder = new StringBuilder(192);

        append(queryBuilder, "asset", asset);
        append(queryBuilder, "amount", amount);
        append(queryBuilder, "type", String.valueOf(type));
        append(queryBuilder, "timestamp", Long.toString(ts));

        final String sig = PrivateKeyBasedSigner.generateSignature(secretUtf8, queryBuilder.toString());
        final String encodedSignature = URLEncoder.encode(sig, StandardCharsets.UTF_8);

        queryBuilder.append("&signature=").append(encodedSignature);

        final String url = REST_SPOT_BASE + "/sapi/v1/futures/transfer";

        try {
            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            LOGGER.debug("Transferring balance: asset=" + asset + " amount=" + amount + " type=" + type);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, queryBuilder.toString().getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp != null && resp.getCode() == 200) {
                final String json = resp.getData();
                final String tranId = minExtract(json, "tranId");
                LOGGER.info("Balance transfer successful: tranId=" + tranId + " asset=" + asset + " amount=" + amount);
                return true;
            } else {
                LOGGER.warn("Balance transfer failed with status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("Balance transfer failed: " + e.getMessage());
            return false;
        }
    }

    public boolean transferSpotToFutures(final String asset, final String amount) throws Exception {
        return transferBalance(asset, amount, 1);
    }

    public boolean transferFuturesToSpot(final String asset, final String amount) throws Exception {
        return transferBalance(asset, amount, 2);
    }


    public static void append(final StringBuilder q, final String k, final String v) {
        if (q.length() > 0)
            q.append('&');
        q.append(k).append('=').append(v);
    }

    public String getAllOpenSpotOrders() {
        try {
            final long timestamp = System.currentTimeMillis();
            final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("timestamp=").append(timestamp);

            final String signature = PrivateKeyBasedSigner.generateSignature(secretUtf8, queryBuilder.toString());
            final String encodedSignature = URLEncoder.encode(signature, StandardCharsets.UTF_8);

            queryBuilder.append("&signature=").append(encodedSignature);
            final String url = REST_SPOT_BASE + "/api/v3/openOrders?" + queryBuilder;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);

            LOGGER.debug("Getting all open spot orders");
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
            final long timestamp = System.currentTimeMillis();
            final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("timestamp=").append(timestamp);

            final String signature = PrivateKeyBasedSigner.generateSignature(secretUtf8, queryBuilder.toString());
            final String encodedSignature = URLEncoder.encode(signature, StandardCharsets.UTF_8);

            queryBuilder.append("&signature=").append(encodedSignature);

            final String url = REST_FUTURE_BASE + "/fapi/v1/openOrders?" + queryBuilder;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("X-MBX-APIKEY", apiKey);

            LOGGER.debug("Getting all open futures orders");
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

    public Ticker getTicker(final String symbol, final boolean isFutures) {
        try {
            final String path = isFutures
                    ? "/fapi/v1/ticker/24hr?symbol=" + symbol
                    : "/api/v3/ticker/24hr?symbol=" + symbol;
            final String url = (isFutures ? REST_FUTURE_BASE : REST_SPOT_BASE) + path;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get ticker failed for symbol: " + symbol + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }

            final String json = response.getData();
            final double last            = parseDoubleSafe(minExtract(json, "lastPrice"));
            final double bid             = parseDoubleSafe(minExtract(json, "bidPrice"));
            final double ask             = parseDoubleSafe(minExtract(json, "askPrice"));
            final double high            = parseDoubleSafe(minExtract(json, "highPrice"));
            final double low             = parseDoubleSafe(minExtract(json, "lowPrice"));
            final double open            = parseDoubleSafe(minExtract(json, "openPrice"));
            final double baseVolume      = parseDoubleSafe(minExtract(json, "volume"));
            final double quoteVolume     = parseDoubleSafe(minExtract(json, "quoteVolume"));
            final double bidSize         = parseDoubleSafe(minExtract(json, "bidQty"));
            final double askSize         = parseDoubleSafe(minExtract(json, "askQty"));
            final long timestamp         = parseLongSafe(minExtract(json, "closeTime"));
            // Binance returns priceChangePercent already as percentage (e.g. -2.34 means -2.34%)
            final double percentageChange = parseDoubleSafe(minExtract(json, "priceChangePercent"));

            LOGGER.debug("Retrieved ticker for symbol: " + symbol + " last=" + last + " bid=" + bid + " ask=" + ask);

            return new Ticker(symbol, 0, open, last, bid, ask, high, low, baseVolume, quoteVolume, timestamp, bidSize, askSize, percentageChange);
        } catch (final Exception e) {
            LOGGER.error("Get ticker failed for symbol: " + symbol + " " + e.getMessage());
            return null;
        }
    }

    private OrdStatus getBinanceOrderStatus(final String statusStr) {
        if (statusStr == null) return OrdStatus.NEW;

        return switch (statusStr) {
            case "NEW" -> OrdStatus.NEW;
            case "PARTIALLY_FILLED" -> OrdStatus.PARTIALLY_FILLED;
            case "FILLED" -> OrdStatus.FILLED;
            case "CANCELED" -> OrdStatus.CANCELED;
            case "REJECTED" -> OrdStatus.REJECTED;
            case "EXPIRED" -> OrdStatus.REJECTED;
            default -> {
                LOGGER.warn("Unknown Binance order status: " + statusStr + ", defaulting to NEW");
                yield OrdStatus.NEW;
            }
        };
    }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public final static class BinanceExchangeInfoFull {
    private List<BinanceSymbol> symbols;

    public final List<BinanceSymbol> getSymbols() {
      return symbols;
    }

    public final void setSymbols(final List<BinanceSymbol> symbols) {
      this.symbols = symbols;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BinanceSymbol {
    private String symbol;
    private String baseAsset;
    private String quoteAsset;
    private String status;
    private String prompt;
    private int baseAssetPrecision;
    private int quotePrecision;
    private int pricePrecision;
    private int quantityPrecision;
    private long deliveryDate; // futures only
    private String contractType; // futures only: PERPETUAL, TRADIFI_PERPETUAL, CURRENT_QUARTER, NEXT_QUARTER
    // private List<List<String>> permissionSets;
    private List<Filter> filters;

    public BinanceSymbol() {}

    public final String getSymbol() {
      return symbol;
    }

    public final void setSymbol(final String symbol) {
      this.symbol = symbol;
    }

    public final String getBaseAsset() {
      return baseAsset;
    }

    public final void setBaseAsset(final String baseAsset) {
      this.baseAsset = baseAsset;
    }

    public final String getQuoteAsset() {
      return quoteAsset;
    }

    public final void setQuoteAsset(final String quoteAsset) {
      this.quoteAsset = quoteAsset;
    }

    public final String getStatus() {
      return status;
    }

    public final void setStatus(final String status) {
      this.status = status;
    }

    public final String getPrompt() {
      return prompt;
    }

    public final void setPrompt(final String prompt) {
      this.prompt = prompt;
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

    // public List<List<String>> getPermissionSets() {
    // return permissionSets;
    // }

    // public void setPermissionSets(List<List<String>> permissionSets) {
    // this.permissionSets = permissionSets;
    // }

    public final int getPricePrecision() {
      return pricePrecision;
    }

    public final void setPricePrecision(final int pricePrecision) {
      this.pricePrecision = pricePrecision;
    }

    public final int getQuantityPrecision() {
      return quantityPrecision;
    }

    public final void setQuantityPrecision(final int quantityPrecision) {
      this.quantityPrecision = quantityPrecision;
    }

    public final long getDeliveryDate() {
      return deliveryDate;
    }

    public final String getContractType() {
      return contractType;
    }

    public final void setContractType(final String contractType) {
      this.contractType = contractType;
    }

    public final void setDeliveryDate(final long deliveryDate) {
      this.deliveryDate = deliveryDate;
    }

    public final List<Filter> getFilters() {
      return filters;
    }

    public final void setFilters(final List<Filter> filters) {
      this.filters = filters;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Filter {
    private String filterType;
    private String minPrice;
    private String tickSize;
    private String minQty;
    private String stepSize;

    public final String getFilterType() {
      return filterType;
    }

    public void setFilterType(final String filterType) {
      this.filterType = filterType;
    }

    public final String getMinPrice() {
      return minPrice;
    }

    public final void setMinPrice(final String minPrice) {
      this.minPrice = minPrice;
    }

    public final String getTickSize() {
      return tickSize;
    }

    public final void setTickSize(final String tickSize) {
      this.tickSize = tickSize;
    }

    public final String getMinQty() {
      return minQty;
    }

    public final void setMinQty(final String minQty) {
      this.minQty = minQty;
    }

    public final String getStepSize() {
      return stepSize;
    }

    public final void setStepSize(final String stepSize) {
      this.stepSize = stepSize;
    }
  }
}
