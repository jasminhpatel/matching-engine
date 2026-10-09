package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
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
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class CoinbaseRestClient {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(CoinbaseRestClient.class);
    private static final int PROXY_PORT = 8888;
    private static final String BASE_URL = "https://api.coinbase.com/api/v3/brokerage";
    private static final String HOST = "api.coinbase.com";
    private static final String SPOT_PORTFOLIO_NAME = "Default";
    private static final String FUTURE_PORTFOLIO_NAME = "Perpetuals";
    //private static final String BASE_URL = "https://api-sandbox.coinbase.com/api/v3/brokerage";
    // Configuration and credentials - immutable after construction
    private final String apiKey;
    private final String apiSecret;
    private final ExchangeSubscription subscription;
    private String futurePortfolioId; // Change portfolioId to non-final so it can be set
    private String spotPortfolioId; // Change portfolioId to non-final so it can be set

    /**
     * Initializes Coinbase REST client with API credentials
     *
     * @param apiKey       Coinbase API key for authentication
     * @param apiSecret    Coinbase API secret (EC private key in PEM format)
     * @param subscription Liquidity subscription for order and balance management
     */
    public CoinbaseRestClient(final String apiKey, final String apiSecret, final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.subscription = subscription;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // products no longer listed; first check starts from the saved pairs
    private final DelistedSymbolCache.ListedTracker listedTracker = new DelistedSymbolCache.ListedTracker();
    // product ids of the latest successful check, for the WebSocket status stream
    private volatile List<String> listedProductIds = new ArrayList<>();

    /** Spot products not online / trading disabled / cancel or view only, post only (restricted) or no longer listed. */
    public List<DelistedSymbol> getSpotDelistedSymbols() {
        return getDelistedSymbols("product_type=SPOT", false);
    }

    /** Perpetuals cancel / view only, disabled, delisted / offline, post only (restricted) or no longer listed. */
    public List<DelistedSymbol> getFuturesDelistedSymbols() {
        return getDelistedSymbols("product_type=FUTURE&contract_expiry_type=PERPETUAL", true);
    }

    public List<String> getListedProductIds() {
        return listedProductIds;
    }

    // public /market/products (no key needed), all pages; null when a request failed
    private List<DelistedSymbol> getDelistedSymbols(final String filter, final boolean futures) {
        final List<JsonNode> products = new ArrayList<>();
        String cursor = "";
        int pages = 0;
        do {
            final HttpUtils.Response response = HttpUtils.get(BASE_URL + "/market/products?get_all_products=true&limit=10000&" + filter
                    + (cursor.isEmpty() ? "" : "&cursor=" + cursor), new HashMap<>(), subscription.getLastUsedProxy(), PROXY_PORT,
                subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Coinbase market/products failed with status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
            try {
                final JsonNode root = MAPPER.readTree(response.getData());
                if (!root.path("products").isArray()) {
                    LOGGER.warn("Coinbase market/products returned no products: " + response.getData());
                    return null;
                }
                root.path("products").forEach(products::add);
                cursor = root.path("pagination").path("has_next").asBoolean(false) ? root.path("pagination").path("next_cursor").asText("") : "";
            } catch (final Exception e) {
                LOGGER.error("Coinbase market/products parse failed", e);
                return null;
            }
        } while (!cursor.isEmpty() && ++pages < 20);
        return toDelistedSymbols(products, futures, System.currentTimeMillis());
    }

    List<DelistedSymbol> toDelistedSymbols(final List<JsonNode> products, final boolean futures, final long now) {
        final List<DelistedSymbol> delisted = new ArrayList<>();
        final Map<String, String[]> listed = new HashMap<>();
        for (final JsonNode product : products) {
            final DelistedSymbol state = toState(product, subscription.getExchange(), futures, now);
            listed.put(state.getSymbol(), new String[] {state.getBase(), state.getQuote()});
            if (state.isTradingDisabled() || state.isRestricted()) {
                delisted.add(state);
            }
        }
        final List<DelistedSymbol> removed = listedTracker.update(subscription.getExchange(), futures, listed, now);
        if (removed == null) {
            return null;
        }
        listedProductIds = new ArrayList<>(listed.keySet());
        delisted.addAll(removed);
        return delisted;
    }

    // spot: stopped unless status online and no trading_disabled / cancel_only / view_only / is_disabled flag.
    // futures: trading_disabled is set by region (INTX), so only explicit flags or a delisted / offline status stop it.
    // post_only (maker orders only) = restricted, still routed; limit_only doesn't affect limit orders
    static DelistedSymbol toState(final JsonNode product, final String exchange, final boolean futures, final long now) {
        final String id = product.path("product_id").asText();
        final String status = product.path("status").asText();
        final boolean flags = product.path("cancel_only").asBoolean(false) || product.path("view_only").asBoolean(false)
            || product.path("is_disabled").asBoolean(false);
        final boolean stopped = futures ? flags || "delisted".equalsIgnoreCase(status) || "offline".equalsIgnoreCase(status)
            : flags || !"online".equalsIgnoreCase(status) || product.path("trading_disabled").asBoolean(false);
        final String base = product.path("base_currency_id").asText();
        final DelistedSymbol state = new DelistedSymbol();
        state.setExchange(exchange);
        state.setFutures(futures);
        state.setSymbol(id);
        state.setBase(base.isEmpty() ? id.split("-")[0] : base); // perpetuals have no base_currency_id (BTC-PERP-INTX)
        state.setQuote(product.path("quote_currency_id").asText());
        state.setStatus(status + (product.path("trading_disabled").asBoolean(false) ? " trading_disabled" : "")
            + (product.path("cancel_only").asBoolean(false) ? " cancel_only" : "") + (product.path("post_only").asBoolean(false) ? " post_only" : ""));
        state.setTradingDisabled(stopped);
        state.setRestricted(!stopped && product.path("post_only").asBoolean(false));
        state.setDetectedAt(now);
        return state;
    }

    // Helper to extract nth object from a JSON array string (assumes array of objects, not robust)
    private static String extractNthOrderFromArray(final String arrayJson, final int n) {
        if (arrayJson == null) {
            return null;
        }
        int count = 0;
        int depth = 0;
        int start = -1;
        for (int i = 0; i < arrayJson.length(); i++) {
            final char c = arrayJson.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && count == n) {
                    return arrayJson.substring(start, i + 1);
                }
                if (depth == 0) {
                    count++;
                }
            }
        }
        return null;
    }

    public String generateJWT(final String path, final String requestMethod) {
        Security.addProvider(new BouncyCastleProvider());

        final Map<String, Object> header = new HashMap<>();
        header.put("alg", "ES256");
        header.put("typ", "JWT");
        header.put("kid", apiKey);
        header.put("nonce", String.valueOf(Instant.now().getEpochSecond()));

        final String uri = requestMethod + " " + HOST + path;

        final Map<String, Object> data = new HashMap<>();
        data.put("iss", "cdp");
        data.put("nbf", Instant.now().getEpochSecond());
        data.put("exp", Instant.now().getEpochSecond() + 120);
        data.put("sub", apiKey);
        data.put("uri", uri);

        String formattedKey = apiSecret;
        if (formattedKey.contains("\\n")) {
            formattedKey = formattedKey.replace("\\n", "\n");
        }

        // Ensure proper PEM format with headers if missing
        if (!formattedKey.contains("-----BEGIN")) {
            LOGGER.error("Invalid PEM format - missing BEGIN header");
            return null;
        }

        LOGGER.debug("Parsing PEM key with length: " + formattedKey.length());
        try (final PEMParser pemParser = new PEMParser(new StringReader(formattedKey))) {
            final Object object = pemParser.readObject();

            if (object == null) {
                LOGGER.error("PEMParser returned null - key format not recognized. Key preview: " +
                        formattedKey.substring(0, Math.min(100, formattedKey.length())));
                return null;
            }

            final JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            final PrivateKey privateKey;

            if (object instanceof PrivateKey) {
                LOGGER.debug("PEM object is already a PrivateKey");
                privateKey = (PrivateKey) object;
            } else if (object instanceof org.bouncycastle.openssl.PEMKeyPair) {
                LOGGER.debug("PEM object is a PEMKeyPair");
                privateKey = converter
                        .getPrivateKey(((org.bouncycastle.openssl.PEMKeyPair) object).getPrivateKeyInfo());
            } else if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                LOGGER.debug("PEM object is a PrivateKeyInfo");
                privateKey = converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) object);
            } else {
                LOGGER.error("Unexpected PEM object type: " + object.getClass().getName());
                return null;
            }

            final KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");
            final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
            final ECPrivateKey ecPrivateKey = (ECPrivateKey) keyFactory.generatePrivate(keySpec);

            final JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder();
            for (final Map.Entry<String, Object> entry : data.entrySet()) {
                claimsSetBuilder.claim(entry.getKey(), entry.getValue());
            }
            final JWTClaimsSet claimsSet = claimsSetBuilder.build();

            final JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.ES256).customParams(header).build();
            final SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);

            final JWSSigner signer = new ECDSASigner(ecPrivateKey);
            signedJWT.sign(signer);

            LOGGER.info("Successfully generated JWT token");
            return signedJWT.serialize();
        } catch (final Exception e) {
            LOGGER.error("Failed to generate JWT: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retrieves all account balances
     *
     * @return JSON response string containing balance data, or null if failed
     */
    public String getBalanceSnapshot() {
        try {
            final String path = "/api/v3/brokerage/accounts";
            final String jwt = generateJWT(path, "GET");

            if (jwt == null || jwt.isEmpty()) {
                LOGGER.error("Failed to generate JWT for balance snapshot");
                return null;
            }

            final String url = BASE_URL + "/accounts";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(),
                    PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved Coinbase balance snapshot");
                return response.getData();
            } else {
                LOGGER.warn("Coinbase Balance Bootstrap failed with status: "
                        + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Coinbase Balance Bootstrap failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves futures balance summary from Coinbase CFM API
     *
     * @return JSON response string containing balance summary, or null if failed
     */
    public String getFutureBalanceSummary() {
        try {
            if (null == futurePortfolioId) {
                getPortfolioId();
            }

            final String path = "/api/v3/brokerage/intx/balances/" + futurePortfolioId;
            final String jwt = generateJWT(path, "GET");

            if (jwt == null) {
                LOGGER.error("Failed to generate JWT for futures balance summary");
                return null;
            }
            final String url = BASE_URL + "/intx/balances/" + futurePortfolioId;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(),
                    PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved Coinbase futures balance summary");
                return response.getData();
            } else {
                LOGGER.warn("Coinbase futures balance summary failed with status: "
                        + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Coinbase futures balance summary failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves all open positions (Coinbase uses perpetual futures)
     *
     * @return JSON response string containing position data, or null if failed
     */
    public String getAllPositions() {
        try {
            if (null == futurePortfolioId) {
                getPortfolioId();
            }
            final String path = "/api/v3/brokerage/intx/positions/" + futurePortfolioId;
            final String jwt = generateJWT(path, "GET");

            if (jwt == null) {
                LOGGER.error("Failed to generate JWT for positions");
                return null;
            }

            final String url = BASE_URL + "/intx/positions/" + futurePortfolioId;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(),
                    PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved Coinbase positions");
                return response.getData();
            } else {
                LOGGER.warn("Coinbase positions retrieval failed with status: "
                        + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Coinbase positions retrieval failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Sends a spot order via REST API
     *
     * @param order         The order object to update with response data
     * @param productId     Trading pair (e.g., BTC-USD)
     * @param side          Order side (BUY/SELL)
     * @param orderType     Order type (MARKET/LIMIT/STOP/STOP_LIMIT)
     * @param baseSize      Order size in base currency
     * @param limitPrice    Limit price (for limit orders)
     * @param clientOrderId Client-specified order ID
     * @return true if request was sent successfully, false otherwise
     */
    public boolean sendSpotOrderREST(final Order order, final String productId, final String side,
                                     final String orderType,
                                     final String baseSize, final String limitPrice, final String clientOrderId) {
        final String path = "/api/v3/brokerage/orders";
        final String jwt = generateJWT(path, "POST");

        if (jwt == null) {
            LOGGER.error("Failed to generate JWT for spot order");
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }

        // Build JSON request body
        final StringBuilder requestBodyBuilder = new StringBuilder();
        requestBodyBuilder.append("{");
        requestBodyBuilder.append("\"client_order_id\":\"").append(clientOrderId).append("\",");
        requestBodyBuilder.append("\"product_id\":\"").append(productId).append("\",");
        requestBodyBuilder.append("\"side\":\"").append(side).append("\",");

        // Order configuration based on type
        requestBodyBuilder.append("\"order_configuration\":{");
        if ("MARKET".equals(orderType)) {
            requestBodyBuilder.append("\"market_market_ioc\":{");
            requestBodyBuilder.append("\"base_size\":\"").append(baseSize).append("\"");
            requestBodyBuilder.append("}");
        } else if ("LIMIT".equals(orderType)) {
            requestBodyBuilder.append("\"limit_limit_gtc\":{");
            requestBodyBuilder.append("\"base_size\":\"").append(baseSize).append("\",");
            requestBodyBuilder.append("\"limit_price\":\"").append(limitPrice).append("\",");
            requestBodyBuilder.append("\"post_only\":false");
            requestBodyBuilder.append("}");
        }
        requestBodyBuilder.append("}");
        requestBodyBuilder.append("}");

        final String requestBody = requestBodyBuilder.toString();

        try {
            final String url = BASE_URL + "/orders";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Sending Coinbase spot order for product: " + productId + " side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(StandardCharsets.UTF_8),
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("REST Coinbase spot order status=" + resp.getCode() + " body=" + resp.getData());
            }
            //final String json = "{\"success\":true,\"success_response\":{\"order_id\":\"93e7024a-b237-400a-8262-6141addeb1d4\",\"product_id\":\"XRP-USDT\",\"side\":\"BUY\",\"client_order_id\":\"1768930475348326645\",\"attached_order_id\":\"\"},\"order_configuration\":{\"limit_limit_gtc\":{\"base_size\":\"1\",\"limit_price\":\"1.80\",\"post_only\":false,\"rfq_disabled\":false,\"reduce_only\":false}}}"; //resp != null ? resp.getData() : null;
            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                LOGGER.info("Coinbase order response: " + json);
                final String success = minExtract(json, "success");
                if ("true".equals(success)) {
                    final String successResponse = extractJsonValue(json, "success_response");
                    if (successResponse != null) {
                        final String orderId = minExtract(successResponse, "order_id");
                        order.setOcoClOrdId(orderId); //TO use it later for query order info
                        LOGGER.info("Coinbase spot order created with orderId: " + orderId);

                        // Synthesize Execution Report immediately
                        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                        if (executionMessage == null) {
                            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L,
                                    (short) 0,
                                    0, 0, 0, 0, order.getSide(), 0);
                        }
                        executionMessage.setClOrdId(order.getClOrdId());
                        executionMessage.setOrdStatus(OrdStatus.NEW);
                        executionMessage.setExecType(ExecType.NEW);
                        subscription.updateExecutionReport(executionMessage);
                        subscription.updateOrder(order.getClOrdId(), order);
                    }
                } else {
                    final String errorResponse = extractJsonValue(json, "error_response");
                    final String errorMessage = minExtract(errorResponse != null ? errorResponse : json, "message");
                    LOGGER.error("Coinbase spot order failed: " + errorMessage);

                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }

                    executionMessage.setClOrdId(order.getClOrdId());
                    executionMessage.setError(errorMessage);
                    executionMessage.setExecType(ExecType.REJECTED);
                    subscription.updateExecutionReport(executionMessage);
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST Coinbase spot order failed: " + e.getMessage());
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }

    /**
     * Sends a futures (perpetual) order via REST API
     *
     * @param order         The order object to update with response data
     * @param productId     Trading pair (e.g., BTC-PERP-INTX)
     * @param side          Order side (BUY/SELL)
     * @param orderType     Order type (MARKET/LIMIT)
     * @param baseSize      Order baseSize
     * @param limitPrice    Order limitPrice (for limit orders)
     * @param clientOrderId Client-specified order ID
     * @return true if request was sent successfully, false otherwise
     */
    public boolean sendFutureOrderREST(final Order order, final String productId, final String side,
                                       final String orderType,
                                       final String baseSize, final String limitPrice, final String clientOrderId) {
        final String path = "/api/v3/brokerage/orders";
        final String jwt = generateJWT(path, "POST");

        if (jwt == null) {
            LOGGER.error("Failed to generate JWT for future order");
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }

        // Build JSON request body for futures order
        final StringBuilder requestBodyBuilder = new StringBuilder();
        requestBodyBuilder.append("{");
        requestBodyBuilder.append("\"client_order_id\":\"").append(clientOrderId).append("\",");
        requestBodyBuilder.append("\"product_id\":\"").append(productId).append("\",");
        requestBodyBuilder.append("\"side\":\"").append(side).append("\",");

        // Order configuration based on type
        requestBodyBuilder.append("\"order_configuration\":{");
        if ("MARKET".equals(orderType)) {
            requestBodyBuilder.append("\"market_market_ioc\":{");
            requestBodyBuilder.append("\"base_size\":\"").append(baseSize).append("\"");
            requestBodyBuilder.append("}");
        } else if ("LIMIT".equals(orderType)) {
            requestBodyBuilder.append("\"limit_limit_gtc\":{");
            requestBodyBuilder.append("\"base_size\":\"").append(baseSize).append("\",");
            requestBodyBuilder.append("\"limit_price\":\"").append(limitPrice).append("\",");
            requestBodyBuilder.append("\"post_only\":false");
            requestBodyBuilder.append("}");
        }
        requestBodyBuilder.append("}");
        requestBodyBuilder.append(",\"leverage\":\"10.0\"");

        requestBodyBuilder.append("}");


        final String requestBody = requestBodyBuilder.toString();

        try {
            final String url = BASE_URL + "/orders";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Sending Coinbase future order for product: " + productId + " side: " + side);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(StandardCharsets.UTF_8),
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("REST Coinbase future order status=" + resp.getCode() + " body=" + resp.getData());
            }

            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                final String success = minExtract(json, "success");
                if ("true".equals(success)) {
                    final String orderId = minExtract(json, "order_id");
                    LOGGER.info("Coinbase future order created with orderId: " + orderId);
                    order.setOcoClOrdId(orderId); // Store UUID for later use
                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }
                    executionMessage.setClOrdId(order.getClOrdId());
                    executionMessage.setOrdStatus(OrdStatus.NEW);
                    executionMessage.setExecType(ExecType.NEW);
                    subscription.updateExecutionReport(executionMessage);
                    subscription.updateOrder(order.getClOrdId(), order);

                } else {
                    final String errorResponse = extractJsonValue(json, "error_response");
                    final String errorMessage = minExtract(errorResponse != null ? errorResponse : json, "preview_failure_reason");
                    LOGGER.error("Coinbase future order failed: " + errorMessage);

                    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                    if (executionMessage == null) {
                        executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                                0, 0, 0, 0, order.getSide(), 0);
                    }

                    executionMessage.setClOrdId(order.getClOrdId());
                    executionMessage.setError(errorMessage);
                    executionMessage.setExecType(ExecType.REJECTED);
                    subscription.updateExecutionReport(executionMessage);
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("REST Coinbase future order failed: " + e.getMessage());
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }

    /**
     * Queries the status of a spot order
     *
     * @param order         The order object to update with response data
     * @param orderId       Coinbase order ID
     * @param clientOrderId Client-specified order ID
     * @param iteration     Iteration count for logging
     * @return true if query was successful, false otherwise
     */
    public boolean querySpotOrderStatus(final Order order, final String orderId, final String clientOrderId,
                                        final int iteration) {
        try {
            final String path = "/api/v3/brokerage/orders/historical/" + order.getOcoClOrdId();
            final String jwt = generateJWT(path, "GET");

            if (jwt == null) {
                LOGGER.error("Failed to generate JWT for spot order query");
                return false;
            }

            final String url = BASE_URL + "/orders/historical/" + order.getOcoClOrdId();

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Querying Coinbase spot order status for orderId: " + orderId);
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("REST Coinbase spot order status query failed with status=" +
                        (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();

            LOGGER.debug("Iteration: " + iteration + " REST Coinbase spot order status query Full response: " + json);

            final String orderData = extractJsonValue(json, "order");
            if (orderData != null) {
                populateSpotOrderFromJson(order, orderData, clientOrderId);
            } else {
                LOGGER.warn("Coinbase spot order query returned no order data for clientOrderId=" + clientOrderId);
            }

            return true;
        } catch (final Exception e) {
            LOGGER.error("REST Coinbase spot order status query failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Queries the status of a future order
     *
     * @param order         The order object to update with response data
     * @param orderId       Coinbase order ID
     * @param clientOrderId Client-specified order ID
     * @param iteration     Iteration count for logging
     * @return true if query was successful, false otherwise
     */
    public boolean queryFutureOrderStatus(final Order order, final String orderId, final String clientOrderId,
                                          final int iteration) {
        try {
            final String path = "/api/v3/brokerage/orders/historical/" + order.getOcoClOrdId();
            final String jwt = generateJWT(path, "GET");

            if (jwt == null) {
                LOGGER.error("Failed to generate JWT for future order query");
                return false;
            }

            final String url = BASE_URL + "/orders/historical/" + order.getOcoClOrdId();

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Querying Coinbase future order status for orderId: " + orderId);
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("REST Coinbase future order status query failed with status=" +
                        (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();

            LOGGER.debug("Iteration: " + iteration + " REST Coinbase future order status query Full response: " + json);

            final String orderData = extractJsonValue(json, "order");
            if (orderData != null) {
                populateFutureOrderFromJson(order, orderData, clientOrderId);
            } else {
                LOGGER.warn("Coinbase future order query returned no order data for clientOrderId=" + clientOrderId);
            }

            return true;
        } catch (final Exception e) {
            LOGGER.error("REST Coinbase future order status query failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Populates order object from Coinbase spot order JSON
     */
    private void populateSpotOrderFromJson(final Order order, final String orderJson, final String clientOrderId) {
        final String status = minExtract(orderJson, "status");
        final String filledSize = minExtract(orderJson, "filled_size");
        final String averageFilledPrice = minExtract(orderJson, "average_filled_price");
        final String totalFees = minExtract(orderJson, "total_fees");
        final String filledValue = minExtract(orderJson, "filled_value");
        final String rejectReason = minExtract(orderJson, "reject_reason");
        final String createdTime = minExtract(orderJson, "created_time");

        final OrdStatus orderStatus = getCoinbaseOrderStatus(status);
        final double filledSizeDouble = parseDoubleSafe(filledSize);
        final double avgPriceDouble = parseDoubleSafe(averageFilledPrice);
        final double totalFeesDouble = parseDoubleSafe(totalFees);
        final double filledValueDouble = parseDoubleSafe(filledValue);

        final long filledSizeLong = MbxMath.changeScale(filledSizeDouble, order.getQtyScale());
        final long avgPriceLong = MbxMath.changeScale(avgPriceDouble, order.getPriceScale());

        LOGGER.debug("Extracted order details - orderId: " + order.getOrderId() + ", status: " + status +
                ", filledSize: " + filledSize + ", avgPrice: " + averageFilledPrice + ", totalFees: " + totalFees +
                ", filledValue: " + filledValue + ", rejectReason: " + rejectReason);

        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                    0, 0, 0, 0, order.getSide(), 0);
        }

        if ("FILLED".equalsIgnoreCase(status)) {
            LOGGER.debug("Coinbase order status - FILLED for clientOrderId: " + clientOrderId +
                    ", filledSize: " + filledSize + ", avgPrice: " + averageFilledPrice +
                    ", filledValue: " + filledValue + ", totalFees: " + totalFees);
            order.setExecuted(true);

            // Extract quote currency from product_id (e.g., BTC-USD -> USD)
            final String productId = minExtract(orderJson, "product_id");
            final String quoteCurrency = (productId != null && productId.contains("-"))
                    ? productId.substring(productId.lastIndexOf("-") + 1) : "USD";

            final Instrument feesInstrument = InstrumentCache.getBySymbol(quoteCurrency);
            if (feesInstrument != null) {
                final long feesLong = MbxMath.changeScale(totalFeesDouble, feesInstrument.getQuantityScale());
                executionMessage.setFeeAccumulatedQuantity(feesLong);
                executionMessage.setFeePositionId(feesInstrument.getId());
            }
        } else if ("CANCELLED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
            LOGGER.debug("Coinbase order status - " + status + " for clientOrderId: " + clientOrderId +
                    ", rejectReason: " + rejectReason);
            order.setRejected(true);
            order.setError(rejectReason);
        }

        executionMessage.setClOrdId(clientOrderId);
        executionMessage.setOrdStatus(orderStatus);
        executionMessage.setTimeInForce(order.getTimeInForce());
        executionMessage.setPrice(avgPriceLong);
        executionMessage.setPriceScale(order.getPriceScale());
        executionMessage.setOrderQty(order.getQty());
        executionMessage.setOrderQtyScale(order.getQtyScale());
        executionMessage.setCumQty(filledSizeLong);
        executionMessage.setAvgPx(avgPriceLong);

        subscription.updateExecutionReport(executionMessage);
        subscription.updateOrder(clientOrderId, order);
    }

    /**
     * Populates order object from Coinbase futures order JSON
     */
    private void populateFutureOrderFromJson(final Order order, final String orderJson, final String clientOrderId) {
        final String status = minExtract(orderJson, "status");
        final String filledSize = minExtract(orderJson, "filled_size");
        final String averageFilledPrice = minExtract(orderJson, "average_filled_price");
        final String totalFees = minExtract(orderJson, "total_fees");
        final String filledValue = minExtract(orderJson, "filled_value");
        final String rejectReason = minExtract(orderJson, "reject_reason");

        final OrdStatus orderStatus = getCoinbaseOrderStatus(status);
        final double filledSizeDouble = parseDoubleSafe(filledSize);
        final double avgPriceDouble = parseDoubleSafe(averageFilledPrice);
        final double totalFeesDouble = parseDoubleSafe(totalFees);

        final long filledSizeLong = MbxMath.changeScale(filledSizeDouble, order.getQtyScale());
        final long avgPriceLong = MbxMath.changeScale(avgPriceDouble, order.getPriceScale());

        LOGGER.debug("Extracted order details - orderId: " + order.getOrderId() + ", status: " + status +
                ", filledSize: " + filledSize + ", avgPrice: " + averageFilledPrice + ", totalFees: " + totalFees +
                ", filledValue: " + filledValue + ", rejectReason: " + rejectReason);

        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                    0, 0, 0, 0, order.getSide(), 0);
        }

        if ("FILLED".equalsIgnoreCase(status)) {
            LOGGER.debug("Coinbase order status - FILLED for clientOrderId: " + clientOrderId +
                    ", filledSize: " + filledSize + ", avgPrice: " + averageFilledPrice +
                    ", filledValue: " + filledValue + ", totalFees: " + totalFees);
            order.setExecuted(true);

            // Extract quote currency from product_id (e.g., BTC-USD -> USD)
            final String productId = minExtract(orderJson, "product_id");
            final String quoteCurrency = (productId != null && productId.contains("-"))
                    ? productId.substring(productId.lastIndexOf("-") + 1) : "USD";

            final Instrument feesInstrument = InstrumentCache.getBySymbol(quoteCurrency);
            if (feesInstrument != null) {
                final long feesLong = MbxMath.changeScale(totalFeesDouble, feesInstrument.getQuantityScale());
                executionMessage.setFeeAccumulatedQuantity(feesLong);
                executionMessage.setFeePositionId(feesInstrument.getId());
            }
        } else if ("CANCELLED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
            LOGGER.debug("Coinbase order status - " + status + " for clientOrderId: " + clientOrderId +
                    ", rejectReason: " + rejectReason);
            order.setRejected(true);
            order.setError(rejectReason);
        }

        executionMessage.setClOrdId(clientOrderId);
        executionMessage.setOrdStatus(orderStatus);
        executionMessage.setTimeInForce(order.getTimeInForce());
        executionMessage.setPrice(avgPriceLong);
        executionMessage.setPriceScale(order.getPriceScale());
        executionMessage.setOrderQty(order.getQty());
        executionMessage.setOrderQtyScale(order.getQtyScale());
        executionMessage.setCumQty(filledSizeLong);
        executionMessage.setAvgPx(avgPriceLong);

        subscription.updateExecutionReport(executionMessage);
        subscription.updateOrder(clientOrderId, order);
    }

    /**
     * Cancels a spot order via REST API
     *
     * @param order         The order object to update with cancellation status
     * @param orderId       Coinbase order ID
     * @param clientOrderId Client-specified order ID
     * @return true if cancellation was successful, false otherwise
     */
    public boolean cancelSpotOrderRest(final Order order, final String orderId, final String clientOrderId) {
        try {
            final String path = "/api/v3/brokerage/orders/batch_cancel";
            final String jwt = generateJWT(path, "POST");

            if (jwt == null) {
                LOGGER.error("Failed to generate JWT for spot order cancellation");
                return false;
            }

            final String requestBody = "{\"order_ids\":[\"" + orderId + "\"]}";
            final String url = BASE_URL + "/orders/batch_cancel";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Cancelling Coinbase spot order for orderId: " + orderId);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(StandardCharsets.UTF_8),
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Cancel Coinbase spot order failed with status=" +
                        (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            boolean cancelSuccess = false;
            // Parse the JSON: { "results": [ { "success": true, ... } ] }
            try {
                String resultJson = extractJsonValue(json, "results");
                String resultsArr = extractFirstOrderFromArray(resultJson);
                String successVal = minExtract(resultsArr, "success");
                cancelSuccess = "true".equalsIgnoreCase(successVal);
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse cancel spot order response: " + ex.getMessage());
            }

            if (cancelSuccess) {
                LOGGER.info("Successfully cancelled Coinbase spot order for orderId: " + orderId);
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
                LOGGER.warn("Cancel Coinbase spot order failed: success=false in response for orderId: " + orderId + ", body=" + json);
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("Cancel Coinbase spot order failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Cancels a spot order via REST API
     *
     * @param order         The order object to update with cancellation status
     * @param orderId       Coinbase order ID
     * @param clientOrderId Client-specified order ID
     * @return true if cancellation was successful, false otherwise
     */
    public boolean cancelFutureOrderRest(final Order order, final String orderId, final String clientOrderId) {
        try {
            final String path = "/api/v3/brokerage/orders/batch_cancel";
            final String jwt = generateJWT(path, "POST");

            if (jwt == null) {
                LOGGER.error("Failed to generate JWT for spot order cancellation");
                return false;
            }

            final String requestBody = "{\"order_ids\":[\"" + orderId + "\"]}";
            final String url = BASE_URL + "/orders/batch_cancel";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Cancelling Coinbase spot order for orderId: " + orderId);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(StandardCharsets.UTF_8),
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Cancel Coinbase spot order failed with status=" +
                        (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }

            final String json = resp.getData();
            boolean cancelSuccess = false;
            // Parse the JSON: { "results": [ { "success": true, ... } ] }
            try {
                String resultJson = extractJsonValue(json, "results");
                String resultsArr = extractFirstOrderFromArray(resultJson);
                String successVal = minExtract(resultsArr, "success");
                cancelSuccess = "true".equalsIgnoreCase(successVal);
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse cancel spot order response: " + ex.getMessage());
            }

            if (cancelSuccess) {
                LOGGER.info("Successfully cancelled Coinbase spot order for orderId: " + orderId);
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
                LOGGER.warn("Cancel Coinbase spot order failed: success=false in response for orderId: " + orderId + ", body=" + json);
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("Cancel Coinbase spot order failed: " + e.getMessage(), e);
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
            final String path = "/api/v3/brokerage/orders/historical/batch";
            final String jwt = generateJWT(path, "GET");

            if (jwt == null) {
                LOGGER.error("Failed to generate JWT for open spot orders");
                return null;
            }

            final String url = BASE_URL + "/orders/historical/batch";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Getting all open Coinbase spot orders");
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Get all open Coinbase spot orders failed with status=" +
                        (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return null;
            }

            LOGGER.debug("Successfully retrieved all open Coinbase spot orders");
            return resp.getData();
        } catch (final Exception e) {
            LOGGER.error("Get all open Coinbase spot orders failed: " + e.getMessage());
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
            final String path = "/api/v3/brokerage/cfm/orders";
            final String jwt = generateJWT(path, "GET");

            if (jwt == null) {
                LOGGER.error("Failed to generate JWT for open futures orders");
                return null;
            }

            final String url = BASE_URL + "/cfm/orders?order_status=OPEN";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Getting all open Coinbase futures orders");
            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Get all open Coinbase futures orders failed with status=" +
                        (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return null;
            }

            LOGGER.debug("Successfully retrieved all open Coinbase futures orders");
            return resp.getData();
        } catch (final Exception e) {
            LOGGER.error("Get all open Coinbase futures orders failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Transfers balance between portfolios (Coinbase doesn't have direct
     * spot-to-futures transfer)
     * This method handles transfers between different portfolio types
     *
     * @param currency   Currency to transfer
     * @param amount     Amount to transfer
     * @param isFromSpot transfer from spot
     * @return true if transfer was successful, false otherwise
     */
    public boolean transferBalance(final String currency, final String amount, final boolean isFromSpot) {
        try {

            if (null == futurePortfolioId) {
                getPortfolioId();
            }
            if (null == spotPortfolioId) {
                getPortfolioId();
            }

            final String path = "/api/v3/brokerage/portfolios/move_funds";
            final String jwt = generateJWT(path, "POST");

            if (jwt == null) {
                LOGGER.error("Failed to generate JWT for balance transfer");
                return false;
            }

            final String fromPortfolioId;
            final String toPortfolioId;
            if (isFromSpot) {
                fromPortfolioId = spotPortfolioId;
                toPortfolioId = futurePortfolioId;
            } else {
                fromPortfolioId = futurePortfolioId;
                toPortfolioId = spotPortfolioId;
            }

            final String requestBody = "{" +
                    "\"funds\":{" +
                    "\"value\":\"" + amount + "\"," +
                    "\"currency\":\"" + currency + "\"" +
                    "}," +
                    "\"source_portfolio_uuid\":\"" + fromPortfolioId + "\"," +
                    "\"target_portfolio_uuid\":\"" + toPortfolioId + "\"" +
                    "}";

            final String url = BASE_URL + "/portfolios/move_funds";

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            LOGGER.debug("Transferring balance: currency=" + currency + " amount=" + amount);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, requestBody.getBytes(StandardCharsets.UTF_8),
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

            if (resp != null && resp.getCode() == 200) {
                LOGGER.info("Coinbase balance transfer successful: currency=" + currency + " amount=" + amount);
                return true;
            } else {
                LOGGER.warn("Coinbase balance transfer failed with status=" +
                        (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("Coinbase balance transfer failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves a page of products from Coinbase Advanced Trade API.
     * Endpoint: GET /api/v3/brokerage/products?get_all_products=true
     *
     * @param cursor pagination cursor (null or empty for first page)
     * @return JSON response string containing products array and pagination, or null if failed
     */
    public String getProducts(final String cursor) {
        try {
            final String path = "/api/v3/brokerage/products";
            final String jwt = generateJWT(path, "GET");

            if (jwt == null) {
                LOGGER.error("Failed to generate JWT for getProducts");
                return null;
            }

            String url = BASE_URL + "/products?get_all_products=true&limit=10000";
            if (cursor != null && !cursor.isEmpty()) {
                url += "&cursor=" + cursor;
            }

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                LOGGER.debug("Successfully retrieved Coinbase products page");
                return response.getData();
            } else {
                LOGGER.warn("Coinbase getProducts failed with status: "
                        + (response != null ? response.getCode() : "null"));
                return null;
            }
        } catch (final Exception e) {
            LOGGER.error("Coinbase getProducts failed: " + e.getMessage());
            return null;
        }
    }

    public Ticker getTicker(final String productId) {
        try {
            final String path = "/api/v3/brokerage/products/" + productId;
            final String jwt = generateJWT(path, "GET");
            if (jwt == null || jwt.isEmpty()) {
                LOGGER.error("Failed to generate JWT for ticker: " + productId);
                return null;
            }
            final String url = BASE_URL + "/products/" + productId;
            final Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer " + jwt);
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get ticker failed for productId: " + productId + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
            final String json = response.getData();
            final String product = extractJsonValue(json, "product");
            final String data = (product != null) ? product : json;
            final double last             = parseDoubleSafe(minExtract(data, "price"));
            final double percentageChange = parseDoubleSafe(minExtract(data, "price_percentage_change_24h"));
            final double baseVolume       = parseDoubleSafe(minExtract(data, "volume_24h"));
            final double quoteVolume      = parseDoubleSafe(minExtract(data, "approximate_quote_24h_volume"));
            final long timestamp          = System.currentTimeMillis();
            LOGGER.debug("Retrieved ticker for productId: " + productId + " last=" + last + " change%=" + percentageChange);
            return new Ticker(productId, 0, last, last, 0.0, 0.0, 0.0, 0.0, baseVolume, quoteVolume, timestamp, 0.0, 0.0, percentageChange);
        } catch (final Exception e) {
            LOGGER.error("Get ticker failed for productId: " + productId + " " + e.getMessage());
            return null;
        }
    }

    /**
     * Converts Coinbase order status string to internal OrdStatus enum
     *
     * @param orderStatusStr Coinbase order status string
     * @return Corresponding OrdStatus enum value
     */
    private OrdStatus getCoinbaseOrderStatus(final String orderStatusStr) {
        if (orderStatusStr == null)
            return OrdStatus.NEW;

        return switch (orderStatusStr) {
            case "OPEN", "PENDING" -> OrdStatus.NEW;
            case "PARTIALLY_FILLED" -> OrdStatus.PARTIALLY_FILLED;
            case "FILLED" -> OrdStatus.FILLED;
            case "CANCELLED", "EXPIRED" -> OrdStatus.CANCELED;
            case "FAILED", "REJECTED" -> OrdStatus.REJECTED;
            default -> {
                LOGGER.warn("Unknown Coinbase order status: " + orderStatusStr + ", defaulting to NEW");
                yield OrdStatus.NEW;
            }
        };
    }

    /**
     * Fetches the portfolio UUID from Coinbase and sets it to portfolioId.
     * Returns the UUID if found, null otherwise.
     */
    public void getPortfolioId() {
        final String path = "/api/v3/brokerage/portfolios";
        final String url = BASE_URL + "/portfolios";
        try {
            final String jwt = generateJWT(path, "GET");
            if (jwt == null) {
                LOGGER.error("Failed to generate JWT for getPerpatualPortfolioId");
                return;
            }
            final Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Content-Type", "application/json");

            final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("Failed to fetch portfolios: status=" +
                        (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
                return;
            }
            final String json = resp.getData();
            // Parse the JSON to find the perpetual portfolio UUID
            // Example structure: { "portfolios": [ { "type": "PERPETUAL", "uuid": "..." }, ... ] }
            String portfoliosArr = extractJsonValue(json, "portfolios");
            if (portfoliosArr == null) {
                LOGGER.warn("No portfolios array found in response");
                return;
            }
            // Find the first portfolio with type "PERPETUAL"
            int idx = 0;
            while (true) {
                final String portfolioJson = extractNthOrderFromArray(portfoliosArr, idx);
                if (portfolioJson == null) {
                    break;
                }
                final String type = minExtract(portfolioJson, "name");
                if (FUTURE_PORTFOLIO_NAME.equalsIgnoreCase(type)) {
                    final String uuid = minExtract(portfolioJson, "uuid");
                    if (uuid != null) {
                        this.futurePortfolioId = uuid;
                        LOGGER.info("Found perpetual portfolio UUID: " + uuid);
                    }
                }
                if (SPOT_PORTFOLIO_NAME.equalsIgnoreCase(type)) {
                    final String uuid = minExtract(portfolioJson, "uuid");
                    if (uuid != null) {
                        this.spotPortfolioId = uuid;
                        LOGGER.info("Found spot portfolio UUID: " + uuid);
                    }
                }
                idx++;
            }
            LOGGER.warn("No perpetual portfolio found in portfolios list");
        } catch (final Exception e) {
            LOGGER.error("Error in getPortfolioId: " + e.getMessage(), e);
        }
    }

}
