package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kraken;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.util.HMAC;
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class KrakenRestClient {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(KrakenRestClient.class);
    private static final String MAINNET_SPOT_BASE_URL = "https://api.kraken.com";
    private static final String MAINNET_FUTURE_BASE_URL = "https://futures.kraken.com/derivatives";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PROXY_PORT = 8888;

    // Configuration and credentials - immutable after construction
    private final String apiKey;
    private final String apiSecret;
    private final ExchangeSubscription subscription;
    // ======= Kraken Futures Status Constants =======
    private static final java.util.Set<String> VALID_KRAKEN_FUTURES_STATUSES = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "marketSuspended",
                    "marketInactive",
                    "invalidPrice",
                    "invalidSize",
                    "tooManySmallOrders",
                    "insufficientAvailableFunds",
                    "wouldCauseLiquidation",
                    "clientOrderIdAlreadyExist",
                    "clientOrderIdTooBig",
                    "maxPositionViolation",
                    "outsidePriceCollar",
                    "wouldIncreasePriceDislocation",
                    "notFound",
                    "orderForEditNotAStop",
                    "orderForEditNotFound",
                    "postWouldExecute",
                    "iocWouldNotExecute",
                    "selfFill",
                    "wouldNotReducePosition",
                    "marketIsPostOnly",
                    "tooManyOrders",
                    "fixedLeverageTooHigh",
                    "clientOrderIdInvalid",
                    "cannotEditTriggerPriceOfTrailingStop",
                    "cannotEditLimitPriceOfTrailingStop",
                    "wouldProcessAfterSpecifiedTime"
            ))
    );

    /**
     * Initializes Kraken REST client with API credentials
     *
     * @param apiKey       Kraken API key for authentication
     * @param apiSecret    Kraken API secret for HMAC signature
     * @param subscription Liquidity subscription for order and balance management
     */
    public KrakenRestClient(final String apiKey, final String apiSecret, final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.subscription = subscription;
    }

    /**
     * Generates Kraken API-Sign header value
     * Kraken Signature Algorithm (matches Python reference):
     * 1. Prepend nonce to POST data: nonce + postdata
     * 2. SHA256 hash of (nonce + postdata)
     * 3. Concatenate urlpath + SHA256 digest (as bytes)
     * 4. HMAC-SHA512 with base64-decoded secret
     * 5. Base64 encode the result
     *
     * @param urlPath       The request path (e.g., /0/private/Balance)
     * @param postData      The POST data as string (e.g., "nonce=1616492376594&pair=XBTUSD...")
     * @param nonce         Nonce value (milliseconds timestamp)
     * @return Base64 encoded signature
     */
    private String generateKrakenSignature(final String urlPath, final String postData, final long nonce) throws Exception {
        LOGGER.debug("=== Kraken Signature Generation ===");
        LOGGER.debug("URL Path: " + urlPath);
        LOGGER.debug("Post Data: " + postData);
        LOGGER.debug("Nonce: " + nonce);

        // Step 1: Decode base64 secret
        final byte[] decodedSecret = Base64.getDecoder().decode(apiSecret);
        LOGGER.debug("Decoded secret length: " + decodedSecret.length);

        // Step 2: Prepend nonce to POST data
        // For POST data like "nonce=1616492376594&pair=XBTUSD", the nonce is already in the data
        // But Kraken's algorithm requires: String(nonce) + postdata concatenated before hashing
        final String nonceStr = String.valueOf(nonce);
        final String dataToHash = nonceStr + postData;
        LOGGER.debug("Data to hash (nonce + postData): " + dataToHash);

        // Step 3: SHA256 hash of (nonce + postdata)
        final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        final byte[] sha256Hash = sha256.digest(dataToHash.getBytes(StandardCharsets.UTF_8));
        LOGGER.debug("SHA256 hash (hex): " + bytesToHex(sha256Hash));
        LOGGER.debug("SHA256 hash length: " + sha256Hash.length);

        // Step 4: Concatenate urlPath + SHA256 digest (as bytes, not hex)
        final byte[] urlPathBytes = urlPath.getBytes(StandardCharsets.UTF_8);
        final byte[] messageBytes = new byte[urlPathBytes.length + sha256Hash.length];
        System.arraycopy(urlPathBytes, 0, messageBytes, 0, urlPathBytes.length);
        System.arraycopy(sha256Hash, 0, messageBytes, urlPathBytes.length, sha256Hash.length);
        LOGGER.debug("Message bytes length: " + messageBytes.length + " (urlPath: " + urlPathBytes.length + " + sha256: " + sha256Hash.length + ")");

        // Step 5: HMAC-SHA512 with the decoded secret
        final byte[] signature = HMAC.hmacSha512(decodedSecret, messageBytes);
        LOGGER.debug("HMAC-SHA512 signature length: " + signature.length);

        // Step 6: Base64 encode
        final String signatureBase64 = Base64.getEncoder().encodeToString(signature);
        LOGGER.debug("Base64 encoded signature: " + signatureBase64);
        LOGGER.debug("=== End Signature Generation ===");

        return signatureBase64;
    }

    /**
     * Helper method to convert byte array to hex string for debugging
     */
    private String bytesToHex(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder();
        for (final byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Retrieves account balance snapshot from Kraken
     *
     * @return JSON response string containing balance data, or null if failed
     */
    public String getBalanceSnapshot() {
        try {
            final long nonce = System.currentTimeMillis();
            final String urlPath = "/0/private/Balance";

            // Build POST data with nonce FIRST (this is what gets sent in the body)
            final String postData = "nonce=" + nonce;
            final String signature = generateKrakenSignature(urlPath, postData, nonce);

            final String url = MAINNET_SPOT_BASE_URL + urlPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("API-Key", apiKey);
            headers.put("API-Sign", signature);

            LOGGER.info("Kraken Balance Request:");
            LOGGER.info("  URL: " + url);
            LOGGER.info("  Headers: API-Key=" + apiKey.substring(0, Math.min(10, apiKey.length())) + "..., API-Sign=" + signature.substring(0, Math.min(20, signature.length())) + "...");
            LOGGER.info("  Body: " + postData);

            final HttpUtils.Response response = HttpUtils.post(url, headers, postData.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved Kraken balance snapshot");
                return response.getData();
            } else {
                LOGGER.warn("Kraken Balance Bootstrap failed with status: " + (response != null ? response.getCode() : "null") +
                        " body: " + (response != null ? response.getData() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Kraken Balance Bootstrap failed: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Generates Kraken Futures API authentication header value
     * Kraken Futures uses a specific signature algorithm:
     * 1. Concatenate postData + Nonce + endpointPath
     * 2. Hash the result with SHA-256
     * 3. Base64-decode the api_secret
     * 4. Use the decoded secret to hash the SHA-256 result with HMAC-SHA-512
     * 5. Base64-encode the result
     * Reference: https://docs.kraken.com/api/docs/guides/futures-rest
     *
     * @param endpointPath The request path (e.g., /api/v3/openpositions)
     * @param nonce        Nonce value (milliseconds timestamp)
     * @return Base64 encoded authentication signature
     */
    private String generateKrakenFuturesAuthent(final String endpointPath, final String postData,  final long nonce) throws Exception {
        LOGGER.debug("=== Kraken Futures Authentication Generation ===");
        LOGGER.debug("Endpoint Path: " + endpointPath);
        LOGGER.debug("Nonce: " + nonce);

        // Step 1: Concatenate postData + Nonce + endpointPath (NO URL encoding)
        final String nonceStr = String.valueOf(nonce);
        final String message = postData + nonceStr + endpointPath;
        LOGGER.debug("Message to hash (postData + nonce + endpointPath): " + message);

        // Step 2: Hash with SHA-256
        final byte[] hash = MessageDigest.getInstance("SHA-256").digest(message.getBytes(StandardCharsets.UTF_8));
        LOGGER.debug("SHA-256 hash (hex): " + bytesToHex(hash));

        // Step 3: Base64-decode the api_secret
        final byte[] secretDecoded = Base64.getDecoder().decode(apiSecret);
        LOGGER.debug("Decoded secret length: " + secretDecoded.length);

        // Step 4: HMAC-SHA-512 with the decoded secret on the SHA-256 hash
        final javax.crypto.Mac hmacsha512 = javax.crypto.Mac.getInstance("HmacSHA512");
        hmacsha512.init(new javax.crypto.spec.SecretKeySpec(secretDecoded, "HmacSHA512"));
        final byte[] hash2 = hmacsha512.doFinal(hash);
        LOGGER.debug("HMAC-SHA-512 result length: " + hash2.length);

        // Step 5: Base64-encode the result
        final String signatureBase64 = Base64.getEncoder().encodeToString(hash2);
        LOGGER.debug("Base64 encoded authentication: " + signatureBase64.substring(0, Math.min(30, signatureBase64.length())) + "...");
        LOGGER.debug("=== End Futures Authentication Generation ===");

        return signatureBase64;
    }

    /**
     * Retrieves all open positions for the account
     *
     * @return JSON response string containing position data, or null if failed
     */
    public String getAllPositions() {
        try {
            final long nonce = System.currentTimeMillis();
            //final String nonce = createNonce();
            final String endpointPath = "/api/v3/openpositions";
            final String authen = generateKrakenFuturesAuthent(endpointPath, "", nonce);

            final String url = MAINNET_FUTURE_BASE_URL + endpointPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("APIKey", apiKey);
            headers.put("Authent", authen);
            headers.put("Nonce",nonce);

            LOGGER.info("Getting Kraken Futures OpenPositions with nonce: " + nonce);
            LOGGER.info("  URL: " + url);
            LOGGER.info("  Headers: API-Key=" + apiKey.substring(0, Math.min(10, apiKey.length())) + "..., Authent=" + authen.substring(0, Math.min(20, authen.length())) + "...");
            
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved Kraken Futures positions");
                return response.getData();
            } else {
                LOGGER.warn("Kraken Futures positions retrieval failed with status: " + (response != null ? response.getCode() : "null") +
                        " body: " + (response != null ? response.getData() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Kraken Futures positions retrieval failed: " + e.getMessage(), e);
        }
        return null;
    }

  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    HttpUtils.Response response = HttpUtils.get(MAINNET_SPOT_BASE_URL + "/0/public/AssetPairs", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final KrakenExchangeInfoFull info = MAPPER.readValue(response.getData(), KrakenExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getResult() != null) {
          List<ExternalSymbol> symbolStatuses = new ArrayList<>();
          for (Map.Entry<String, KrakenTradingPair> entry : info.getResult().entrySet()) {
            String pairName = entry.getKey();
            KrakenTradingPair krakenPair = entry.getValue();

            ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("kraken");
            symbolStatus.setFutures(false);
            symbolStatus.setUpdated(updated);
            symbolStatus.setBase(normalizeBaseCurrency(krakenPair.getBase()));
            symbolStatus.setQuote(normalizeQuoteCurrency(krakenPair.getQuote()));
            symbolStatus.setPrompt(pairName);
            symbolStatus.setTradable("online".equalsIgnoreCase(krakenPair.getStatus()));
            symbolStatus.setPriceScale(krakenPair.getPairDecimals() != null ? krakenPair.getPairDecimals() : 4);
            symbolStatus.setQtyScale(krakenPair.getLotDecimals() != null ? krakenPair.getLotDecimals() : 8);

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
     * Sends a futures order via REST API
     * Endpoint: POST /api/v3/sendorder
     * Reference: https://docs.kraken.com/api/docs/futures-api/trading/send-order
     *
     * @param order         The order object to update with response data
     * @param symbol        Trading pair symbol (Kraken Futures format, e.g., "PF_XRPUSD")
     * @param side          Order side (Buy/Sell)
     * @param type          Order type (Limit/Market)
     * @param timeInForce   Time in force (GTC/IOC/FOK)
     * @param quantityStr   Order quantity as string
     * @param priceStr      Order price as string
     * @param reduceOnly    Whether this is a reduce-only order
     * @return true if request was sent successfully, false otherwise
     */
    public boolean sendFutureOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce, final String quantityStr,
                                       final String priceStr, final boolean reduceOnly) {
        final long nonce = System.currentTimeMillis();
        final String endpointPath = "/api/v3/sendorder";

        try {
            // Convert order type to Kraken Futures format
            final String krakenOrderType = convertToKrakenFuturesOrderType(type, timeInForce);

            // Build POST data for futures order according to Kraken Futures API spec
            final StringBuilder postDataBuilder = new StringBuilder();
            postDataBuilder.append("cliOrdId=").append(order.getClOrdId());
            postDataBuilder.append("&symbol=").append(symbol);
            postDataBuilder.append("&side=").append(side.toLowerCase());
            postDataBuilder.append("&orderType=").append(krakenOrderType);
            postDataBuilder.append("&size=").append(quantityStr);

            // Add limitPrice only for non-market orders
            if (!"mkt".equals(krakenOrderType)) {
                postDataBuilder.append("&limitPrice=").append(priceStr);
            }

            if (reduceOnly) {
                postDataBuilder.append("&reduceOnly=true");
            }

            final String postData = postDataBuilder.toString();
            final String authen = generateKrakenFuturesAuthent(endpointPath, postData, nonce);

            final String url = MAINNET_FUTURE_BASE_URL + endpointPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("APIKey", apiKey);
            headers.put("Authent", authen);
            headers.put("Nonce", nonce);

            LOGGER.debug("Sending Kraken Futures order via REST for symbol: " + symbol + " side: " + side + " orderType: " + krakenOrderType);
            LOGGER.debug("  URL: " + url);
            LOGGER.debug("  Body: " + postData);

            final HttpUtils.Response resp = HttpUtils.post(url, headers, postData.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("REST Kraken Futures order status=" + resp.getCode() + " body=" + resp.getData());
            }

            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                LOGGER.debug("Kraken Futures order response: " + json);

                // Check for success in response
                final String result = JsonHelper.extractJsonValue(json, "result");
                if (result != null && !result.isEmpty() && !"error".equalsIgnoreCase(result)) {
                    // Extract order details from response
                    final String resultData = JsonHelper.extractJsonValue(json, "orderEvents");
                    final String status = JsonHelper.extractJsonValue(json,"status");

                    if (resultData != null && !isUnFulfilledOrderStatus(status)) {
                        // orderEvents is an array, extract the first event
                        final String firstEvent = JsonHelper.extractFirstOrderFromArray(resultData);
                        if (firstEvent != null) {
                            final String eventType = minExtract(firstEvent, "type");

                            
                            LOGGER.debug("Processing orderEvent with type: " + eventType);

                            ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                            if (executionMessage == null) {
                                executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                        order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                        0, 0, 0, 0, order.getSide(), 0);
                            }

                            executionMessage.setClOrdId(order.getClOrdId());

                            if ("PLACE".equalsIgnoreCase(eventType)) {
                                // Order was placed successfully
                                LOGGER.info("Kraken Futures order placed successfully. ClientOrderId: " + order.getClOrdId());
                                executionMessage.setOrdStatus(OrdStatus.NEW);
                                executionMessage.setExecType(ExecType.NEW);

                            } else if ("EXECUTION".equalsIgnoreCase(eventType)) {
                                // Order was executed - extract execution details from orderPriorExecution
                                final String orderPriorExecution = JsonHelper.extractJsonValue(firstEvent, "orderPriorExecution");
                                if (orderPriorExecution != null) {
                                    final String orderId = minExtract(orderPriorExecution, "orderId");
                                    final double executionPrice = parseDoubleSafe(minExtract(firstEvent, "price"));
                                    final double executedAmount = parseDoubleSafe(minExtract(firstEvent, "amount"));
                                    final double orderQuantity = parseDoubleSafe(minExtract(orderPriorExecution, "quantity"));
                                    final double filled = parseDoubleSafe(minExtract(orderPriorExecution, "filled"));

                                    order.setOcoClOrdId(orderId);
                                    LOGGER.info("Kraken Futures order executed. OrderId: " + orderId + ", ExecutionPrice: " + executionPrice + 
                                            ", ExecutedAmount: " + executedAmount);

                                    // Set execution details in message
                                    final long executionPriceLong = MbxMath.changeScale(executionPrice, order.getPriceScale());
                                    final long executedQtyLong = MbxMath.changeScale(executedAmount, order.getQtyScale());
                                    final long totalQtyLong = MbxMath.changeScale(orderQuantity, order.getQtyScale());
                                    final long filledLong = MbxMath.changeScale(filled, order.getQtyScale());

                                    executionMessage.setOrdStatus(OrdStatus.FILLED);
                                    executionMessage.setExecType(ExecType.TRADE);
                                    executionMessage.setPrice(executionPriceLong);
                                    executionMessage.setPriceScale(order.getPriceScale());
                                    executionMessage.setOrderQty(totalQtyLong);
                                    executionMessage.setOrderQtyScale(order.getQtyScale());
                                    executionMessage.setCumQty(filledLong);
                                    executionMessage.setLeavesQty(totalQtyLong - filledLong);
                                    executionMessage.setAvgPx(executionPriceLong);
                                    order.setExecuted(true);
                                }

                            } else if ("REJECT".equalsIgnoreCase(eventType) || "CANCEL".equalsIgnoreCase(eventType)) {
                                // Order was rejected
                                final String errorReason = minExtract(firstEvent, "reason");
                                LOGGER.error("Kraken Futures order rejected. ClientOrderId: " + order.getClOrdId() + ", Reason: " + errorReason);
                                
                                executionMessage.setOrdStatus(OrdStatus.REJECTED);
                                executionMessage.setExecType(ExecType.REJECTED);
                                executionMessage.setError(errorReason);
                                order.setRejected(true);
                            }
                            subscription.updateExecutionReport(executionMessage);
                            subscription.updateOrder(order.getClOrdId(), order);
                            return true;
                        }
                    }
                    else if(isUnFulfilledOrderStatus(status)){
                            ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                            if (executionMessage == null) {
                                executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                        order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                        0, 0, 0, 0, order.getSide(), 0);
                            }

                            executionMessage.setClOrdId(order.getClOrdId());
                            executionMessage.setError(status);
                            executionMessage.setExecType(ExecType.REJECTED);
                            executionMessage.setOrdStatus(OrdStatus.REJECTED);
                            subscription.updateExecutionReport(executionMessage);
                            order.setRejected(true);
                            subscription.updateOrder(order.getClOrdId(), order);
                        }
                } else {
                    // Handle error response
                    final String error = JsonHelper.extractJsonValue(json, "error");
                    if (error != null && !error.isEmpty()) {
                        LOGGER.error("Kraken Futures order failed: " + error);
                        
                        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                        if (executionMessage == null) {
                            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                    0, 0, 0, 0, order.getSide(), 0);
                        }

                        executionMessage.setClOrdId(order.getClOrdId());
                        executionMessage.setError(error);
                        executionMessage.setExecType(ExecType.REJECTED);
                        executionMessage.setOrdStatus(OrdStatus.REJECTED);
                        subscription.updateExecutionReport(executionMessage);
                        order.setRejected(true);
                        subscription.updateOrder(order.getClOrdId(), order);
                    }
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST Kraken Futures order failed: " + e.getMessage(), e);
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }

    /**
     * Converts order type to Kraken Futures API format
     * Maps internal order type strings to Kraken Futures orderType values
     *
     * @param type    The order type (e.g., "Limit", "Market")
     * @param timeInForce The time in force (e.g., "GTC", "IOC", "FOK")
     * @return The Kraken Futures orderType value (e.g., "lmt", "mkt", "ioc", "fok")
     */
    private String convertToKrakenFuturesOrderType(final String type, final String timeInForce) {
        if (type == null) {
            LOGGER.warn("Order type is null, defaulting to 'lmt'");
            return "lmt";
        }

        final String lowerType = type.toLowerCase();

        // Handle Limit orders
        if ("limit".equals(lowerType)) {
            if ("FOK".equalsIgnoreCase(timeInForce)) {
                return "fok"; // Fill or Kill
            } else if ("IOC".equalsIgnoreCase(timeInForce)) {
                return "ioc"; // Immediate or Cancel
            } else {
                return "lmt"; // Default: Limit order (GTC)
            }
        }
        // Handle Market orders
        else if ("market".equals(lowerType)) {
            // Market orders map to mkt (immediate-or-cancel with 1% price protection)
            return "mkt";
        }
        // Fallback
        else {
            LOGGER.warn("Unknown order type: " + type + ", defaulting to 'lmt'");
            return "lmt";
        }
    }

    /**
     * Sends a spot order via REST API
     *
     * @param order         The order object to update with response data
     * @param symbol        Trading pair symbol
     * @param side          Order side (Buy/Sell)
     * @param type          Order type (Limit/Market)
     * @param timeInForce   Time in force (GTC/IOC/PO)
     * @param quantityStr   Order quantity as string
     * @param priceStr      Order price as string
     * @param clientOrderId Client-specified order ID
     * @return true if request was sent successfully, false otherwise
     */
    public boolean sendSpotOrderREST(final Order order, final String symbol, final String side, final String type, final String timeInForce,
                                     final String quantityStr, final String priceStr, final String clientOrderId) {
        final long nonce = System.currentTimeMillis();
        final String urlPath = "/0/private/AddOrder";

        try {
            // Build POST data for spot order
            final StringBuilder postDataBuilder = new StringBuilder();
            postDataBuilder.append("nonce=").append(nonce);
            postDataBuilder.append("&userref=").append(Long.valueOf(order.getClOrdId()));
            postDataBuilder.append("&pair=").append(symbol);
            postDataBuilder.append("&type=").append(side.toLowerCase());
            postDataBuilder.append("&ordertype=").append(type.toLowerCase());
            postDataBuilder.append("&volume=").append(quantityStr);

            if (!"Market".equalsIgnoreCase(type)) {
                postDataBuilder.append("&price=").append(priceStr);
            }

            if ("GTC".equals(timeInForce)) {
                postDataBuilder.append("&expiretm=0");
            } else if ("IOC".equals(timeInForce)) {
                postDataBuilder.append("&timeinforce=IOC");
            }

            final String postData = postDataBuilder.toString();
            final String signature = generateKrakenSignature(urlPath, postData, nonce);

            final String url = MAINNET_SPOT_BASE_URL + urlPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("API-Key", apiKey);
            headers.put("API-Sign", signature);

            LOGGER.debug("Sending Kraken spot order REST for symbol: " + symbol + " side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, postData.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("REST Kraken spot order status=" + resp.getCode() + " body=" + resp.getData());
            }

            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                final String errorSection = JsonHelper.extractJsonValue(json, "error");
                if (errorSection == null || errorSection.isEmpty() || "[]".equals(errorSection)) {
                    // Extract result object
                    final String resultSection = extractJsonValue(json, "result");
                    if (resultSection != null && resultSection.contains("txid")) {
                        // Extract transaction ID
                        final String txIds = extractJsonValue(resultSection, "txid");
                        final String txId = extractFirstOrderFromArray(txIds);
                        if (txId != null && !txId.isEmpty()) {
                            LOGGER.info("SPOT: Order created successfully via REST. TXID: " + txId + ", Symbol: " + symbol);
                            order.setOcoClOrdId(txId); //setting up txId to OcoClordId so we can use it later while calling order status or delete api
                            // Create and cache execution report with PENDING status
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
                            subscription.updateOrder(clientOrderId,order);

                        } else {
                            LOGGER.warn("SPOT: Failed to extract TXID from successful response");
                        }
                    } else {
                        LOGGER.error("SPOT: Invalid response format - no txid found in result");
                    }
                } else {
                    // Handle order rejection
                    LOGGER.error("Kraken spot order failed: " + errorSection);
                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }

                    executionMessage.setClOrdId(clientOrderId);
                    executionMessage.setError(errorSection);
                    executionMessage.setOrdStatus(OrdStatus.REJECTED);
                    executionMessage.setExecType(ExecType.REJECTED);
                    subscription.updateExecutionReport(executionMessage);
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST Kraken spot order failed: " + e.getMessage());
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }

    /**
     * Queries the status of a spot order by user reference
     *
     * @param order         The order object to update with response data
     * @param clientOrderId Client-specified order ID
     * @param iteration     Iteration count for logging
     * @return true if query was successful, false otherwise
     */
    public boolean querySpotOrderStatus(final Order order, final String clientOrderId, int iteration) {
        try {
            final long nonce = System.currentTimeMillis();
            final String urlPath = "/0/private/QueryOrders";

            final StringBuilder postDataBuilder = new StringBuilder();
            postDataBuilder.append("nonce=").append(nonce);
            postDataBuilder.append("&txid=").append(order.getOcoClOrdId());

            final String postData = postDataBuilder.toString();
            final String signature = generateKrakenSignature(urlPath, postData, nonce);

            final String url = MAINNET_SPOT_BASE_URL + urlPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("API-Key", apiKey);
            headers.put("API-Sign", signature);

            LOGGER.debug("Querying Kraken spot order status for clientOrderId: " + clientOrderId + " with txid: " + order.getOcoClOrdId());
            final HttpUtils.Response resp = HttpUtils.post(url, headers, postData.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("REST Kraken spot order status query failed with status=" + (resp != null ? resp.getCode() : "null"));
                return false;
            }
            final String json = resp.getData();
            LOGGER.debug("Iteration: " + iteration + " REST Kraken spot order status query Full response: " + json);

            final String errorSection = extractJsonValue(json, "error");
            if (errorSection == null || errorSection.isEmpty() || "[]".equals(errorSection)) {
                final String resultSection = extractJsonValue(json, "result");
                if (resultSection != null && !resultSection.isEmpty()) {
                    LOGGER.debug("Extracted result section: " + resultSection);
                    // Parse the result object which contains order IDs as keys
                    final String orderJson = extractJsonValue(resultSection, order.getOcoClOrdId());
                    if (orderJson != null) {
                        populateOrderFromJson(order, orderJson, clientOrderId);
                    } else {
                        LOGGER.debug("Order not found in result section for clientOrderId: " + clientOrderId);
                    }
                }
            } else {
                LOGGER.warn("Error querying orders: " + errorSection);
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST Kraken spot order status query failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Populates order object and execution report with all fields extracted from Kraken order JSON
     *
     * @param order         The order object to populate
     * @param orderJson     The order result JSON
     * @param clientOrderId The client order ID
     */
    private void populateOrderFromJson(final Order order, final String orderJson, final String clientOrderId) {
        try {
            final String status = minExtract(orderJson, "status");
            final String vol = minExtract(orderJson, "vol");
            final String volExec = minExtract(orderJson, "vol_exec");
            final String avgPrice = minExtract(orderJson, "price");
            final String cost = minExtract(orderJson, "cost");
            final String fee = minExtract(orderJson, "fee");
            //final String desc = extractJsonValue(orderJson,"descr");

            // Parse values
            final OrdStatus orderStatus = getKrakenOrderStatus(status);
            final double volDouble = parseDoubleSafe(vol);
            final double volExecDouble = parseDoubleSafe(volExec);
            final double avgPriceDouble = parseDoubleSafe(avgPrice);
            final double costDouble = parseDoubleSafe(cost);
            final double feeDouble = parseDoubleSafe(fee);

            final long volLong = MbxMath.changeScale(volDouble, order.getQtyScale());
            final long volExecLong = MbxMath.changeScale(volExecDouble, order.getQtyScale());
            final long avgPriceLong = MbxMath.changeScale(avgPriceDouble, order.getPriceScale());
            final long costLong = MbxMath.changeScale(costDouble, order.getPriceScale());

            LOGGER.debug("Extracted order details - status: " + status + ", vol: " + vol + ", vol_exec: " + volExec + ", avg_price: " + avgPrice);

            ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
            if (executionMessage == null) {
                executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                        order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                        0, 0, 0, 0, order.getSide(), 0);
            }

            // Update order object based on status
            if ("closed".equalsIgnoreCase(status)) {
                LOGGER.debug("Kraken order status - FILLED for clientOrderId: " + clientOrderId);
                order.setExecuted(true);

                final String feeCurrency = getDefaultQuotedCurrency(order.getSymbol());
                final Instrument feesInstrument = InstrumentCache.getBySymbol(feeCurrency);
                if (feesInstrument != null) {
                    final long feesLong = MbxMath.changeScale(feeDouble, feesInstrument.getQuantityScale());
                    executionMessage.setFeeAccumulatedQuantity(feesLong);
                    executionMessage.setFeePositionId(feesInstrument.getId());
                }
            } else if ("canceled".equalsIgnoreCase(status)) {
                LOGGER.debug("Kraken order status - CANCELLED for clientOrderId: " + clientOrderId);
                order.setRejected(true);
            }

            executionMessage.setClOrdId(clientOrderId);
            executionMessage.setOrdStatus(orderStatus);
            executionMessage.setTimeInForce(order.getTimeInForce());
            executionMessage.setPrice(avgPriceLong);
            executionMessage.setPriceScale(order.getPriceScale());
            executionMessage.setOrderQty(order.getQty());
            executionMessage.setOrderQtyScale(order.getQtyScale());
            executionMessage.setCumQty(volExecLong);
            executionMessage.setLeavesQty(volLong - volExecLong);
            executionMessage.setAvgPx(avgPriceLong);

            LOGGER.debug("Updating execution report for clientOrderId: " + clientOrderId);
            subscription.updateExecutionReport(executionMessage);
            subscription.updateOrder(clientOrderId, order);
        } catch (final Exception e) {
            LOGGER.error("Error populating order from JSON: " + e.getMessage());
        }
    }

    /**
     * Queries the status of a futures order by order ID or client order ID
     * Endpoint: POST /api/v3/orders/status
     * Reference: https://docs.kraken.com/api/docs/futures-api/trading/get-order-status
     *
     * @param order         The order object to update with response data
     * @param clientOrderId Client-specified order ID
     * @param iteration     Iteration count for logging
     * @return true if query was successful, false otherwise
     */
    public boolean queryFuturesOrderStatus(final Order order, final String clientOrderId, int iteration) {
        final long nonce = System.currentTimeMillis();
        final String endpointPath = "/api/v3/orders/status";
        final String postUrl = "cliOrdIds=" + clientOrderId;

        try {
            // For URL parameters, post data includes the query string
            final String postData = postUrl;
            final String authen = generateKrakenFuturesAuthent(endpointPath, postData, nonce);

            final String url = MAINNET_FUTURE_BASE_URL + endpointPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("APIKey", apiKey);
            headers.put("Authent", authen);
            headers.put("Nonce", nonce);

            LOGGER.debug("Querying Kraken Futures order status for clientOrderId: " + clientOrderId + " iteration: " + iteration);
            LOGGER.debug("  URL: " + url + "?" + postUrl);

            final HttpUtils.Response resp = HttpUtils.post(url + "?" + postUrl, headers, postData.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Iteration " + iteration + ": REST Kraken Futures order status query failed with status=" + 
                        (resp != null ? resp.getCode() : "null"));
                return false;
            }

            final String json = resp.getData();
            LOGGER.debug("Iteration " + iteration + ": REST Kraken Futures order status response: " + 
                    json.substring(0, Math.min(300, json.length())) + "...");

            // Check for success in response
            final String result = JsonHelper.extractJsonValue(json, "result");
            if (result != null && "success".equalsIgnoreCase(result)) {
                // Extract orders array from response
                final String ordersArray = JsonHelper.extractJsonValue(json, "orders");
                if (ordersArray != null && ordersArray.startsWith("[")) {
                    // Extract the first order from the array
                    final String firstOrderEntry = JsonHelper.extractFirstOrderFromArray(ordersArray);
                    if (firstOrderEntry != null) {
                        // Parse the order entry which has "order" and "status" fields
                        final String orderObj = JsonHelper.extractJsonValue(firstOrderEntry, "order");
                        final String orderStatus = minExtract(firstOrderEntry, "status");
                        
                        if (orderObj != null) {
                            final String orderId = minExtract(orderObj, "orderId");
                            final double filled = parseDoubleSafe(minExtract(orderObj, "filled"));
                            final double quantity = parseDoubleSafe(minExtract(orderObj, "quantity"));
                            final double limitPrice = parseDoubleSafe(minExtract(orderObj, "limitPrice"));

                            LOGGER.debug("Iteration " + iteration + ": Kraken Futures order status retrieved. OrderId: " + orderId + 
                                    ", Status: " + orderStatus + ", Filled: " + filled + "/" + quantity);


                            ExecutionReportMessage executionMessage = subscription.getExecutionReport(clientOrderId);
                            if (executionMessage == null) {
                                executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                        order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                        0, 0, 0, 0, order.getSide(), 0);
                            }

                            executionMessage.setClOrdId(clientOrderId);

                            // Map order status to OrdStatus
                            if ("ENTERED_BOOK".equalsIgnoreCase(orderStatus)) {
                                // Order is placed and waiting in the order book
                                executionMessage.setOrdStatus(OrdStatus.NEW);
                                executionMessage.setExecType(ExecType.NEW);
                            } else if ("PARTIALLY_FILLED".equalsIgnoreCase(orderStatus)) {
                                // Order has been partially filled
                                final long filledQtyLong = MbxMath.changeScale(filled, order.getQtyScale());
                                final long totalQtyLong = MbxMath.changeScale(quantity, order.getQtyScale());
                                final long priceLong = MbxMath.changeScale(limitPrice, order.getPriceScale());

                                executionMessage.setOrdStatus(OrdStatus.PARTIALLY_FILLED);
                                executionMessage.setExecType(ExecType.TRADE);
                                executionMessage.setCumQty(filledQtyLong);
                                executionMessage.setLeavesQty(totalQtyLong - filledQtyLong);
                                executionMessage.setPrice(priceLong);
                                executionMessage.setPriceScale(order.getPriceScale());
                                order.setExecuted(false); // Still active
                            } else if ("FILLED".equalsIgnoreCase(orderStatus)) {
                                // Order is completely filled
                                final long filledQtyLong = MbxMath.changeScale(filled, order.getQtyScale());
                                final long totalQtyLong = MbxMath.changeScale(quantity, order.getQtyScale());
                                final long priceLong = MbxMath.changeScale(limitPrice, order.getPriceScale());

                                executionMessage.setOrdStatus(OrdStatus.FILLED);
                                executionMessage.setExecType(ExecType.TRADE);
                                executionMessage.setCumQty(filledQtyLong);
                                executionMessage.setLeavesQty(0);
                                executionMessage.setPrice(priceLong);
                                executionMessage.setPriceScale(order.getPriceScale());
                                order.setExecuted(true);
                            } else if ("CANCELLED".equalsIgnoreCase(orderStatus)) {
                                // Order has been cancelled
                                executionMessage.setOrdStatus(OrdStatus.CANCELED);
                                executionMessage.setExecType(ExecType.CANCELED);
                                order.setRejected(true);
                            } else if ("REJECTED".equalsIgnoreCase(orderStatus) || isUnFulfilledOrderStatus(orderStatus)) {
                                // Order has been rejected or has an error status
                                final String errorMsg = minExtract(firstOrderEntry, "error");
                                executionMessage.setOrdStatus(OrdStatus.REJECTED);
                                executionMessage.setExecType(ExecType.REJECTED);
                                executionMessage.setError(errorMsg != null ? errorMsg : orderStatus);
                                order.setRejected(true);
                                LOGGER.warn("Iteration " + iteration + ": Order rejected with status: " + orderStatus);
                            } else {
                                // Unknown status - default to NEW
                                LOGGER.warn("Iteration " + iteration + ": Unknown order status: " + orderStatus);
                                executionMessage.setOrdStatus(OrdStatus.NEW);
                                executionMessage.setExecType(ExecType.NEW);
                            }

                            subscription.updateExecutionReport(executionMessage);
                            subscription.updateOrder(clientOrderId, order);
                            return true;
                        }
                    }
                }
            } else {
                // Handle error response
                final String error = JsonHelper.extractJsonValue(json, "error");
                if (error != null && !error.isEmpty()) {
                    LOGGER.warn("Iteration " + iteration + ": Kraken Futures order status query error: " + error);
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("Iteration " + iteration + ": REST Kraken Futures order status query failed: " + e.getMessage(), e);
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
        final long nonce = System.currentTimeMillis();
        final String urlPath = "/0/private/Transfer";

        try {
            final StringBuilder postDataBuilder = new StringBuilder();
            postDataBuilder.append("nonce=").append(nonce);
            postDataBuilder.append("&asset=").append(asset);
            postDataBuilder.append("&amount=").append(amount);
            postDataBuilder.append("&from=").append("Spot");
            postDataBuilder.append("&to=").append("Futures");

            final String postData = postDataBuilder.toString();
            final String signature = generateKrakenSignature(urlPath, postData, nonce);

            final String url = MAINNET_SPOT_BASE_URL + urlPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("API-Key", apiKey);
            headers.put("API-Sign", signature);

            LOGGER.debug("Transferring balance from Spot to Futures: asset=" + asset + " amount=" + amount);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, postData.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp != null && resp.getCode() == 200) {
                final String json = resp.getData();
                final String errorSection = minExtract(json, "error");
                if (errorSection == null || errorSection.isEmpty() || "[]".equals(errorSection)) {
                    LOGGER.info("Kraken balance transfer from spot to futures successful: asset=" + asset + " amount=" + amount);
                    return true;
                } else {
                    LOGGER.warn("Kraken balance transfer failed: " + errorSection);
                    return false;
                }
            } else {
                LOGGER.warn("Kraken balance transfer failed with status=" + (resp != null ? resp.getCode() : "null"));
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("Kraken balance transfer failed: " + e.getMessage());
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
            final long nonce = System.currentTimeMillis();
            final String urlPath = "/0/private/OpenOrders";

            final String postData = "nonce=" + nonce;
            final String signature = generateKrakenSignature(urlPath, postData, nonce);

            final String url = MAINNET_SPOT_BASE_URL + urlPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("API-Key", apiKey);
            headers.put("API-Sign", signature);

            LOGGER.debug("Getting all open Kraken spot orders");
            final HttpUtils.Response resp = HttpUtils.post(url, headers, postData.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Get all open Kraken spot orders failed with status=" + (resp != null ? resp.getCode() : "null"));
                return null;
            }
            LOGGER.debug("Successfully retrieved all open Kraken spot orders");
            return resp.getData();
        } catch (final Exception e) {
            LOGGER.error("Get all open Kraken spot orders failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves all open futures orders for the account
     * Endpoint: GET /api/v3/openorders
     * Reference: https://docs.kraken.com/api/docs/futures-api/trading/get-open-orders
     *
     * @return JSON response string containing open order data, or null if failed
     */
    public String getAllOpenFuturesOrders() {
        try {
            final long nonce = System.currentTimeMillis();
            final String endpointPath = "/api/v3/openorders";
            final String authen = generateKrakenFuturesAuthent(endpointPath, "", nonce);

            final String url = MAINNET_FUTURE_BASE_URL + endpointPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("APIKey", apiKey);
            headers.put("Authent", authen);
            headers.put("Nonce", nonce);

            LOGGER.debug("Getting all open Kraken Futures orders");
            LOGGER.info("  URL: " + url);
            LOGGER.info("  Headers: APIKey=" + apiKey.substring(0, Math.min(10, apiKey.length())) + "..., Authent=" + authen.substring(0, Math.min(20, authen.length())) + "...");

            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Get all open Kraken Futures orders failed with status=" + (resp != null ? resp.getCode() : "null"));
                return null;
            }
            LOGGER.debug("Successfully retrieved all open Kraken Futures orders");
            return resp.getData();
        } catch (final Exception e) {
            LOGGER.error("Get all open Kraken Futures orders failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Cancels a spot order via REST API
     *
     * @param order         The order object to update with cancellation status
     * @param clientOrderId Client-specified order ID
     * @return true if cancellation was successful, false otherwise
     */
    public boolean cancelSpotOrderRest(final Order order, final String clientOrderId) {
        try {
            final long nonce = System.currentTimeMillis();
            final String urlPath = "/0/private/CancelOrder";

            final StringBuilder postDataBuilder = new StringBuilder();
            postDataBuilder.append("nonce=").append(nonce);
            postDataBuilder.append("&txid=").append(order.getOcoClOrdId());

            final String postData = postDataBuilder.toString();
            final String signature = generateKrakenSignature(urlPath, postData, nonce);
            final String url = MAINNET_SPOT_BASE_URL + urlPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("API-Key", apiKey);
            headers.put("API-Sign", signature);

            LOGGER.debug("Cancelling Kraken spot order for txId: " + order.getOcoClOrdId());
            final HttpUtils.Response resp = HttpUtils.post(url, headers, postData.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Cancel Kraken spot order failed with status=" + (resp != null ? resp.getCode() : "null"));
                return false;
            }

            final String json = resp.getData();
            final String errorSection = extractJsonValue(json, "error");

            if (errorSection == null || errorSection.isEmpty() || "[]".equals(errorSection)) {
                LOGGER.info("Successfully cancelled Kraken spot order for txId: " + order.getOcoClOrdId());
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
                LOGGER.warn("Cancel Kraken spot order failed with error: " + errorSection);
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("Cancel Kraken spot order failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Cancels a futures order via REST API
     * Endpoint: POST /api/v3/cancelorder
     * Reference: https://docs.kraken.com/api/docs/futures-api/trading/cancel-order
     *
     * @param order         The order object to update with cancellation status
     * @param clientOrderId Client-specified order ID
     * @return true if cancellation was successful, false otherwise
     */
    public boolean cancelFuturesOrderRest(final Order order, final String clientOrderId) {
        final long nonce = System.currentTimeMillis();
        final String endpointPath = "/api/v3/cancelorder";

        try {
            // Build POST data with client order ID
            final String postData = "cliOrdId=" + clientOrderId;
            final String authen = generateKrakenFuturesAuthent(endpointPath, postData, nonce);

            final String url = MAINNET_FUTURE_BASE_URL + endpointPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("APIKey", apiKey);
            headers.put("Authent", authen);
            headers.put("Nonce", nonce);

            LOGGER.debug("Cancelling Kraken Futures order for clientOrderId: " + clientOrderId);
            LOGGER.debug("  URL: " + url);
            LOGGER.debug("  Body: " + postData);

            final HttpUtils.Response resp = HttpUtils.post(url, headers, postData.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Cancel Kraken Futures order failed with status=" + (resp != null ? resp.getCode() : "null"));
                return false;
            }

            final String json = resp.getData();
            LOGGER.debug("Cancel Futures order response: " + json);

            // Check for success in response
            final String result = JsonHelper.extractJsonValue(json, "result");
            if (result != null && "success".equalsIgnoreCase(result)) {
                LOGGER.info("Successfully cancelled Kraken Futures order for clientOrderId: " + clientOrderId);
                order.setRejected(true);

                ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionMessage == null) {
                    executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                            order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                            0, 0, 0, 0, order.getSide(), 0);
                }

                executionMessage.setClOrdId(clientOrderId);
                executionMessage.setExecType(ExecType.CANCELED);
                executionMessage.setOrdStatus(OrdStatus.CANCELED);
                subscription.updateExecutionReport(executionMessage);
                subscription.updateOrder(clientOrderId, order);
                return true;
            } else {
                // Handle error response
                final String error = JsonHelper.extractJsonValue(json, "error");
                if (error != null && !error.isEmpty()) {
                    LOGGER.warn("Cancel Kraken Futures order failed: " + error);
                    
                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }

                    executionMessage.setClOrdId(clientOrderId);
                    executionMessage.setError(error);
                    subscription.updateExecutionReport(executionMessage);
                }
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("Cancel Kraken Futures order failed: " + e.getMessage(), e);
            return false;
        }
    }

    public Ticker getSpotTicker(final String pair) {
        try {
            final String url = MAINNET_SPOT_BASE_URL + "/0/public/Ticker?pair=" + pair;
            final HttpUtils.Response response = HttpUtils.get(url, new HashMap<>(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get spot ticker failed for pair: " + pair + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
            final String json = response.getData();
            final String result = extractJsonValue(json, "result");
            if (result == null) {
                LOGGER.warn("No result in spot ticker response for pair: " + pair);
                return null;
            }
            final String tickerData = krakenExtractFirstValue(result);
            if (tickerData == null) {
                LOGGER.warn("Could not extract ticker data for pair: " + pair);
                return null;
            }
            final String aArr = extractJsonValue(tickerData, "a");
            final String bArr = extractJsonValue(tickerData, "b");
            final String cArr = extractJsonValue(tickerData, "c");
            final String vArr = extractJsonValue(tickerData, "v");
            final String hArr = extractJsonValue(tickerData, "h");
            final String lArr = extractJsonValue(tickerData, "l");
            final double ask        = krakenFirstArrayStr(aArr);
            final double bid        = krakenFirstArrayStr(bArr);
            final double last       = krakenFirstArrayStr(cArr);
            final double baseVolume = krakenSecondArrayStr(vArr);
            final double high       = krakenSecondArrayStr(hArr);
            final double low        = krakenSecondArrayStr(lArr);
            final double open       = parseDoubleSafe(minExtract(tickerData, "o"));
            final double percentageChange = (open > 0) ? (last - open) / open * 100.0 : 0.0;
            final long timestamp = System.currentTimeMillis();
            LOGGER.debug("Retrieved Kraken spot ticker for pair: " + pair + " last=" + last + " bid=" + bid + " ask=" + ask);
            return new Ticker(pair, 0, open, last, bid, ask, high, low, baseVolume, 0.0, timestamp, 0.0, 0.0, percentageChange);
        } catch (final Exception e) {
            LOGGER.error("Get spot ticker failed for pair: " + pair + " " + e.getMessage());
            return null;
        }
    }

    public Ticker getFutureTicker(final String symbol) {
        try {
            final String url = MAINNET_FUTURE_BASE_URL + "/api/v3/tickers/" + symbol;
            final HttpUtils.Response response = HttpUtils.get(url, new HashMap<>(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get future ticker failed for symbol: " + symbol + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
            final String json = response.getData();
            final String ticker = extractJsonValue(json, "ticker");
            if (ticker == null) {
                LOGGER.warn("No ticker in future ticker response for: " + symbol);
                return null;
            }
            final double bid         = parseDoubleSafe(minExtract(ticker, "bid"));
            final double ask         = parseDoubleSafe(minExtract(ticker, "ask"));
            final double last        = parseDoubleSafe(minExtract(ticker, "last"));
            final double high        = parseDoubleSafe(minExtract(ticker, "high24h"));
            final double low         = parseDoubleSafe(minExtract(ticker, "low24h"));
            final double open        = parseDoubleSafe(minExtract(ticker, "open24h"));
            final double baseVolume  = parseDoubleSafe(minExtract(ticker, "vol24h"));
            final double quoteVolume = parseDoubleSafe(minExtract(ticker, "volumeQuote"));
            final double bidSize     = parseDoubleSafe(minExtract(ticker, "bidSize"));
            final double askSize     = parseDoubleSafe(minExtract(ticker, "askSize"));
            final double percentageChange = (open > 0) ? (last - open) / open * 100.0 : 0.0;
            final long timestamp = System.currentTimeMillis();
            LOGGER.debug("Retrieved Kraken future ticker for symbol: " + symbol + " last=" + last + " bid=" + bid + " ask=" + ask);
            return new Ticker(symbol, 0, open, last, bid, ask, high, low, baseVolume, quoteVolume, timestamp, bidSize, askSize, percentageChange);
        } catch (final Exception e) {
            LOGGER.error("Get future ticker failed for symbol: " + symbol + " " + e.getMessage());
            return null;
        }
    }

    private String krakenExtractFirstValue(final String json) {
        if (json == null) return null;
        final int colonPos = json.indexOf(':');
        if (colonPos < 0) return null;
        final int objStart = json.indexOf('{', colonPos);
        if (objStart < 0) return null;
        int braceCount = 0;
        for (int i = objStart; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') {
                braceCount--;
                if (braceCount == 0) return json.substring(objStart, i + 1);
            }
        }
        return null;
    }

    private double krakenFirstArrayStr(final String array) {
        if (array == null || array.isEmpty()) return 0.0;
        final int start = array.indexOf('"');
        if (start < 0) return 0.0;
        final int end = array.indexOf('"', start + 1);
        if (end < 0) return 0.0;
        return parseDoubleSafe(array.substring(start + 1, end));
    }

    private double krakenSecondArrayStr(final String array) {
        if (array == null || array.isEmpty()) return 0.0;
        int pos = array.indexOf('"');
        if (pos < 0) return 0.0;
        pos = array.indexOf('"', pos + 1);
        if (pos < 0) return 0.0;
        pos = array.indexOf('"', pos + 1);
        if (pos < 0) return 0.0;
        final int end = array.indexOf('"', pos + 1);
        if (end < 0) return 0.0;
        return parseDoubleSafe(array.substring(pos + 1, end));
    }

    /**
     * Converts Kraken order status string to internal OrdStatus enum
     *
     * @param orderStatusStr Kraken order status string
     * @return Corresponding OrdStatus enum value
     */
    private OrdStatus getKrakenOrderStatus(final String orderStatusStr) {
        if (orderStatusStr == null) return OrdStatus.NEW;

        return switch (orderStatusStr) {
            case "pending" -> OrdStatus.NEW;
            case "open" -> OrdStatus.NEW;
            case "active" -> OrdStatus.NEW;
            case "closed" -> OrdStatus.FILLED;
            case "canceled" -> OrdStatus.CANCELED;
            case "expired" -> OrdStatus.CANCELED;
            default -> {
                LOGGER.warn("Unknown Kraken order status: " + orderStatusStr + ", defaulting to NEW");
                yield OrdStatus.NEW;
            }
        };
    }


    /**
     * Returns default quote currency based on symbol pattern
     *
     * @param symbol Trading pair symbol
     * @return Default quote currency
     */
    private String getDefaultQuotedCurrency(final String symbol) {
        if (symbol != null) {
            if (symbol.contains("USDT")) {
                return "USDT";
            } else if (symbol.contains("USDC")) {
                return "USDC";
            }
        }
        return "USDT";
    }

    /**
     * Retrieves a specific open order by clientOrderId
     * Queries the OpenOrders endpoint and searches for matching order
     *
     * @param clientOrderId The client order ID to search for
     * @return Order JSON object if found, null otherwise
     */
    public String getOpenOrderByClientId(final String clientOrderId) {
        try {
            final long nonce = System.currentTimeMillis();
            final String urlPath = "/0/private/OpenOrders";

            final String postData = "nonce=" + nonce;
            final String signature = generateKrakenSignature(urlPath, postData, nonce);

            final String url = MAINNET_SPOT_BASE_URL + urlPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("API-Key", apiKey);
            headers.put("API-Sign", signature);

            LOGGER.debug("Querying Kraken open orders to find clientOrderId: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, postData.getBytes(), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("REST Kraken open orders query failed with status=" + (resp != null ? resp.getCode() : "null"));
                return null;
            }

            final String json = resp.getData();
            LOGGER.debug("REST Kraken open orders response: " + json);

            final String errorSection = minExtract(json, "error");
            if (errorSection == null || errorSection.isEmpty() || "[]".equals(errorSection)) {
                final String resultSection = extractJsonValue(json, "result");
                if (resultSection != null && !resultSection.isEmpty()) {
                    LOGGER.debug("Extracted result section with open orders");
                    // Search through all open orders for matching clientOrderId
                    return findOrderByClientIdInResult(resultSection, clientOrderId);
                }
            } else {
                LOGGER.warn("Error querying open orders: " + errorSection);
            }
            return null;
        } catch (final Exception e) {
            LOGGER.error("REST Kraken open orders query failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Searches through open orders result to find order matching clientOrderId
     * Result format: {"orderId1": {"refid": "clientId", ...}, "orderId2": {...}, ...}
     *
     * @param resultSection The result section containing all open orders
     * @param clientOrderId The client order ID to search for
     * @return Order JSON object if found, null otherwise
     */
    private String findOrderByClientIdInResult(final String resultSection, final String clientOrderId) {
        if (resultSection == null || resultSection.trim().isEmpty() || !resultSection.startsWith("{")) {
            return null;
        }

        // Remove outer braces
        String content = resultSection.trim();
        if (content.startsWith("{")) {
            content = content.substring(1);
        }
        if (content.endsWith("}")) {
            content = content.substring(0, content.length() - 1);
        }

        // Extract each order object and check for matching refid
        int braceDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;
        int objectStart = -1;

        for (int i = 0; i < content.length(); i++) {
            final char c = content.charAt(i);

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
                    if (braceDepth == 0) {
                        objectStart = i;
                    }
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0 && objectStart >= 0) {
                        // Found a complete order object
                        final String orderJson = "{" + content.substring(objectStart, i + 1);

                        // Check if this order matches the clientOrderId
                        final String refid = minExtract(orderJson, "refid");
                        if (refid != null && refid.equals(clientOrderId)) {
                            LOGGER.debug("Found matching open order with clientOrderId: " + clientOrderId);
                            return orderJson;
                        }
                    }
                }
            }
        }

        LOGGER.debug("No matching open order found for clientOrderId: " + clientOrderId);
        return null;
    }



    /**
     * Extracts the first element from a JSON array string
     * Handles format like ["OFO5KC-U223R-UJSFGC"] and returns OFO5KC-U223R-UJSFGC
     *
     * @param arrayJson The JSON array string (e.g., "["OFO5KC-U223R-UJSFGC"]")
     * @return The first element without quotes, or null if not found or invalid format
     */
    private String extractFirstOrderFromArray(final String arrayJson) {
        if (arrayJson == null || arrayJson.trim().isEmpty()) {
            return null;
        }

        final String trimmed = arrayJson.trim();

        // Check if it starts with [ and ends with ]
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            LOGGER.warn("Invalid array format: " + trimmed);
            return null;
        }

        // Extract content between brackets
        final String content = trimmed.substring(1, trimmed.length() - 1).trim();

        if (content.isEmpty()) {
            return null;
        }

        // Find the first quoted string
        final int firstQuote = content.indexOf('"');
        if (firstQuote < 0) {
            LOGGER.warn("No quoted string found in array: " + trimmed);
            return null;
        }

        final int secondQuote = content.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            LOGGER.warn("Unclosed quoted string in array: " + trimmed);
            return null;
        }

        final String element = content.substring(firstQuote + 1, secondQuote);
        LOGGER.debug("Extracted element from array: " + element);
        return element;
    }

    /**
     * Retrieves a WebSocket authentication token from Kraken REST API
     * The token should be used within 15 minutes of creation for WebSocket authentication
     *
     * @return WebSocket authentication token, or null if failed
     */
    public String getWebsocketToken() {
        try {
            final long nonce = System.currentTimeMillis();
            final String urlPath = "/0/private/GetWebSocketsToken";

            // Build POST data with nonce (JSON body)
            final String postData = "nonce=" + nonce;
            final String signature = generateKrakenSignature(urlPath, postData, nonce);

            final String url = MAINNET_SPOT_BASE_URL + urlPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("API-Key", apiKey);
            headers.put("API-Sign", signature);

            LOGGER.info("Kraken WebSocket Token Request:");
            LOGGER.info("  URL: " + url);
            LOGGER.info("  Headers: API-Key=" + apiKey.substring(0, Math.min(10, apiKey.length())) + "..., API-Sign=" + signature.substring(0, Math.min(20, signature.length())) + "...");
            LOGGER.info("  Body: " + postData);

            final HttpUtils.Response response = HttpUtils.post(url, headers, postData.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (response != null && response.getCode() == 200) {
                final String json = response.getData();
                LOGGER.debug("WebSocket Token Response: " + json);

                // Extract token from result section
                final String resultSection = extractJsonValue(json, "result");
                if (resultSection != null) {
                    final String token = extractJsonValue(resultSection, "token");
                    if (token != null && !token.isEmpty()) {
                        LOGGER.info("Successfully retrieved WebSocket authentication token");
                        return token;
                    } else {
                        LOGGER.error("Token field not found in result section: " + resultSection);
                    }
                }

                // Check for error section
                final String errorSection = extractJsonValue(json, "error");
                if (errorSection != null && !errorSection.isEmpty() && !"[]".equals(errorSection)) {
                    LOGGER.error("Kraken WebSocket Token Error: " + errorSection);
                }
            } else {
                final int code = response != null ? response.getCode() : -1;
                final String data = response != null ? response.getData() : "No response";
                LOGGER.error("Kraken WebSocket Token request failed with code " + code + ": " + data);
            }
        } catch (final Exception e) {
            LOGGER.error("Kraken WebSocket Token retrieval failed: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Retrieves futures account snapshot from Kraken Futures API
     * Endpoint: GET /api/v3/accounts
     *
     * @return JSON response string containing account data, or null if failed
     */
    public String getAccountsSnapshot() {
        try {
            final long nonce = System.currentTimeMillis();
            final String endpointPath = "/api/v3/accounts";
            final String authen = generateKrakenFuturesAuthent(endpointPath, "", nonce);

            final String url = MAINNET_FUTURE_BASE_URL + endpointPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("APIKey", apiKey);
            headers.put("Authent", authen);
            headers.put("Nonce", nonce);

            LOGGER.info("Kraken Futures Accounts Request with nonce: " + nonce);
            LOGGER.info("  URL: " + url);
            LOGGER.info("  Headers: APIKey=" + apiKey.substring(0, Math.min(10, apiKey.length())) + "..., Authent=" + authen.substring(0, Math.min(20, authen.length())) + "...");

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved Kraken Futures accounts snapshot");
                return response.getData();
            } else {
                LOGGER.warn("Kraken Futures accounts retrieval failed with status: " + (response != null ? response.getCode() : "null") +
                        " body: " + (response != null ? response.getData() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Kraken Futures accounts retrieval failed: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Validates if the given status string is a valid Kraken Futures order status
     * Compares against the complete list of known Kraken Futures order statuses
     *
     * @param status The status string to validate
     * @return true if the status is valid, false otherwise
     */
    private boolean isUnFulfilledOrderStatus(final String status) {
        if (status == null || status.isEmpty()) {
            return false;
        }
        return VALID_KRAKEN_FUTURES_STATUSES.contains(status.trim());
    }

  private static final String normalizeQuoteCurrency(final String krakenQuote) {
    if (krakenQuote == null)
      return null;

    switch (krakenQuote.toLowerCase()) {
      case "zusd":
        return "USD";
      case "zeur":
        return "EUR";
      case "zgbp":
        return "GBP";
      case "zcad":
        return "CAD";
      case "zjpy":
        return "JPY";
      case "zaud":
        return "AUD";
      case "xeth":
        return "ETH";
      case "xbt":
        return "BTC";
      case "xxbt":
        return "BTC";
      default:
        if (krakenQuote.startsWith("Z") || krakenQuote.startsWith("X")) {
          return krakenQuote.substring(1);
        }
        return krakenQuote;
    }
  }

  private static final String normalizeBaseCurrency(final String krakenBase) {
    if (krakenBase == null)
      return null;

    switch (krakenBase.toLowerCase()) {
      case "xbt":
        return "BTC";
      case "xxbt":
        return "BTC";
      default:
        // Remove X prefix if present (for crypto currencies)
        if (krakenBase.startsWith("X") && krakenBase.length() > 1) {
          return krakenBase.substring(1);
        }
        return krakenBase;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class KrakenExchangeInfoFull {
    @JsonProperty("error")
    private List<String> error;

    @JsonProperty("result")
    private Map<String, KrakenTradingPair> result;

    // Constructors
    public KrakenExchangeInfoFull() {}

    // Getters and Setters
    public List<String> getError() {
      return error;
    }

    public void setError(List<String> error) {
      this.error = error;
    }

    public Map<String, KrakenTradingPair> getResult() {
      return result;
    }

    public void setResult(Map<String, KrakenTradingPair> result) {
      this.result = result;
    }

    @Override
    public String toString() {
      return "KrakenExchangeInfoFull{" + "error=" + error + ", result=" + result + '}';
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class KrakenTradingPair {
    @JsonProperty("altname")
    private String altname;

    @JsonProperty("wsname")
    private String wsname;

    @JsonProperty("aclass_base")
    private String aclassBase;

    @JsonProperty("base")
    private String base;

    @JsonProperty("aclass_quote")
    private String aclassQuote;

    @JsonProperty("quote")
    private String quote;

    @JsonProperty("lot")
    private String lot;

    @JsonProperty("cost_decimals")
    private Integer costDecimals;

    @JsonProperty("pair_decimals")
    private Integer pairDecimals;

    @JsonProperty("lot_decimals")
    private Integer lotDecimals;

    @JsonProperty("lot_multiplier")
    private Integer lotMultiplier;

    @JsonProperty("leverage_buy")
    private List<Object> leverageBuy;

    @JsonProperty("leverage_sell")
    private List<Object> leverageSell;

    @JsonProperty("fees")
    private List<List<Double>> fees;

    @JsonProperty("fees_maker")
    private List<List<Double>> feesMaker;

    @JsonProperty("fee_volume_currency")
    private String feeVolumeCurrency;

    @JsonProperty("margin_call")
    private Integer marginCall;

    @JsonProperty("margin_stop")
    private Integer marginStop;

    @JsonProperty("ordermin")
    private String ordermin;

    @JsonProperty("costmin")
    private String costmin;

    @JsonProperty("tick_size")
    private String tickSize;

    @JsonProperty("status")
    private String status;

    // Constructors
    public KrakenTradingPair() {}

    // Getters and Setters
    public String getAltname() {
      return altname;
    }

    public void setAltname(String altname) {
      this.altname = altname;
    }

    public String getWsname() {
      return wsname;
    }

    public void setWsname(String wsname) {
      this.wsname = wsname;
    }

    public String getAclassBase() {
      return aclassBase;
    }

    public void setAclassBase(String aclassBase) {
      this.aclassBase = aclassBase;
    }

    public String getBase() {
      return base;
    }

    public void setBase(String base) {
      this.base = base;
    }

    public String getAclassQuote() {
      return aclassQuote;
    }

    public void setAclassQuote(String aclassQuote) {
      this.aclassQuote = aclassQuote;
    }

    public String getQuote() {
      return quote;
    }

    public void setQuote(String quote) {
      this.quote = quote;
    }

    public String getLot() {
      return lot;
    }

    public void setLot(String lot) {
      this.lot = lot;
    }

    public Integer getCostDecimals() {
      return costDecimals;
    }

    public void setCostDecimals(Integer costDecimals) {
      this.costDecimals = costDecimals;
    }

    public Integer getPairDecimals() {
      return pairDecimals;
    }

    public void setPairDecimals(Integer pairDecimals) {
      this.pairDecimals = pairDecimals;
    }

    public Integer getLotDecimals() {
      return lotDecimals;
    }

    public void setLotDecimals(Integer lotDecimals) {
      this.lotDecimals = lotDecimals;
    }

    public Integer getLotMultiplier() {
      return lotMultiplier;
    }

    public void setLotMultiplier(Integer lotMultiplier) {
      this.lotMultiplier = lotMultiplier;
    }

    public List<Object> getLeverageBuy() {
      return leverageBuy;
    }

    public void setLeverageBuy(List<Object> leverageBuy) {
      this.leverageBuy = leverageBuy;
    }

    public List<Object> getLeverageSell() {
      return leverageSell;
    }

    public void setLeverageSell(List<Object> leverageSell) {
      this.leverageSell = leverageSell;
    }

    public List<List<Double>> getFees() {
      return fees;
    }

    public void setFees(List<List<Double>> fees) {
      this.fees = fees;
    }

    public List<List<Double>> getFeesMaker() {
      return feesMaker;
    }

    public void setFeesMaker(List<List<Double>> feesMaker) {
      this.feesMaker = feesMaker;
    }

    public String getFeeVolumeCurrency() {
      return feeVolumeCurrency;
    }

    public void setFeeVolumeCurrency(String feeVolumeCurrency) {
      this.feeVolumeCurrency = feeVolumeCurrency;
    }

    public Integer getMarginCall() {
      return marginCall;
    }

    public void setMarginCall(Integer marginCall) {
      this.marginCall = marginCall;
    }

    public Integer getMarginStop() {
      return marginStop;
    }

    public void setMarginStop(Integer marginStop) {
      this.marginStop = marginStop;
    }

    public String getOrdermin() {
      return ordermin;
    }

    public void setOrdermin(String ordermin) {
      this.ordermin = ordermin;
    }

    public String getCostmin() {
      return costmin;
    }

    public void setCostmin(String costmin) {
      this.costmin = costmin;
    }

    public String getTickSize() {
      return tickSize;
    }

    public void setTickSize(String tickSize) {
      this.tickSize = tickSize;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    @Override
    public String toString() {
      return "KrakenTradingPair{" + "altname='" + altname + '\'' + ", wsname='" + wsname + '\'' + ", base='" + base + '\'' + ", quote='"
          + quote + '\'' + ", status='" + status + '\'' + ", ordermin='" + ordermin + '\'' + ", costmin='" + costmin + '\'' + '}';
    }
  }

}
