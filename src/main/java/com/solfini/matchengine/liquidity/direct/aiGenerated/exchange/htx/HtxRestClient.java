package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.htx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class HtxRestClient {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(HtxRestClient.class);
    private static final String HOST = "api.huobi.pro";
    private static final String HOST_FUTURES = "api.hbdm.com";
    private static final String REST_SPOT_BASE = "https://" + HOST;
    private static final String REST_FUTURES_BASE = "https://" + HOST_FUTURES;
    private static final int PROXY_PORT = 8888;
    private static final DateTimeFormatter HUOBI_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private volatile String SPOT_ACCOUNT_ID = null;

    private final String accessKey;
    private final String secretKey;
    private final ExchangeSubscription subscription;

    public HtxRestClient(final String accessKey, final String secretKey,
                         final ExchangeSubscription subscription) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.subscription = subscription;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // symbols no longer listed; first check starts from the saved pairs
    private final DelistedSymbolCache.ListedTracker listedTracker = new DelistedSymbolCache.ListedTracker();
    // contract codes of the latest futures check: the contract_info WebSocket also sends old hidden contracts, ignored
    private volatile Set<String> listedContracts = Collections.emptySet();

    public Set<String> getListedContracts() {
        return listedContracts;
    }

    /** Spot pairs not online (offline, suspend; pre-online = new listing), API trading disabled, or no longer listed. */
    public List<DelistedSymbol> getSpotDelistedSymbols() {
        final String json = getSpotSymbolDetails();
        return json == null ? null : toDelistedSymbols(json, false, System.currentTimeMillis());
    }

    /** USDT swaps not listing (status other than 1 / 2), past or upcoming delivery_time, or no longer listed. */
    public List<DelistedSymbol> getFuturesDelistedSymbols() {
        final String json = getFutureSymbolDetails();
        return json == null ? null : toDelistedSymbols(json, true, System.currentTimeMillis());
    }

    // parses /v1/common/symbols or /linear-swap-api/v1/swap_contract_info; null when it is not a valid list
    List<DelistedSymbol> toDelistedSymbols(final String json, final boolean futures, final long now) {
        final List<DelistedSymbol> delisted = new ArrayList<>();
        final Map<String, String[]> listed = new HashMap<>();
        try {
            final JsonNode root = MAPPER.readTree(json);
            if (!"ok".equals(root.path("status").asText()) || !root.path("data").isArray()) {
                LOGGER.warn("HTX " + (futures ? "contract" : "symbol") + " list not ok: " + json);
                return null;
            }
            for (final JsonNode item : root.path("data")) {
                final DelistedSymbol state = futures ? toFuturesState(item, subscription.getExchange(), now)
                    : toSpotState(item, subscription.getExchange(), now);
                listed.put(state.getSymbol(), new String[] {state.getBase(), state.getQuote()});
                if (futures && !"swap".equals(item.path("business_type").asText("swap"))) {
                    continue; // dated futures expire normally, not a delisting; still listed so a saved row is not marked removed
                }
                if (state.isTradingDisabled() || state.isUpcoming()) {
                    delisted.add(state);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("HTX " + (futures ? "contract" : "symbol") + " list parse failed", e);
            return null;
        }
        final List<DelistedSymbol> removed = listedTracker.update(subscription.getExchange(), futures, listed, now);
        if (removed == null) {
            return null;
        }
        if (futures) {
            listedContracts = new HashSet<>(listed.keySet());
        }
        delisted.addAll(removed);
        return delisted;
    }

    // stopped unless state is online or pre-online (new listing) and api-trading is enabled
    static DelistedSymbol toSpotState(final JsonNode item, final String exchange, final long now) {
        final String state = item.path("state").asText();
        final boolean stopped = !("online".equals(state) || "pre-online".equals(state)) || "disabled".equals(item.path("api-trading").asText());
        return toState(item.path("symbol").asText(), item.path("base-currency").asText().toUpperCase(Locale.ROOT),
            item.path("quote-currency").asText().toUpperCase(Locale.ROOT), false, state + " api-trading " + item.path("api-trading").asText(),
            stopped, 0, now, exchange);
    }

    /** Used by the REST check and the contract_info WebSocket: status 1 listing, 2 pending listing; delivery_time = delisting. */
    public static DelistedSymbol toFuturesState(final JsonNode item, final String exchange, final long now) {
        final String code = item.path("contract_code").asText();
        final int status = item.path("contract_status").asInt(-1);
        final long deliveryTime = item.path("delivery_time").asLong(0); // "" when no delisting is scheduled
        final boolean stopped = (status != 1 && status != 2) || (deliveryTime > 0 && deliveryTime <= now);
        final String[] baseQuote = code.split("-", 2);
        return toState(code, baseQuote[0], baseQuote.length > 1 ? baseQuote[1] : "USDT", true, "contract_status " + status, stopped,
            Math.max(deliveryTime, 0), now, exchange);
    }

    private static DelistedSymbol toState(final String symbol, final String base, final String quote, final boolean futures,
        final String status, final boolean stopped, final long delistTime, final long now, final String exchange) {
        final DelistedSymbol state = new DelistedSymbol();
        state.setExchange(exchange);
        state.setFutures(futures);
        state.setSymbol(symbol);
        state.setBase(base);
        state.setQuote(quote);
        state.setStatus(status);
        state.setTradingDisabled(stopped);
        state.setDelistTime(delistTime);
        state.setDetectedAt(now);
        return state;
    }


    // -------------------------------------------------------------------------
    // Public API — Spot
    // -------------------------------------------------------------------------

    public String getSpotAccountId() throws Exception {
        if (SPOT_ACCOUNT_ID != null) return SPOT_ACCOUNT_ID;
        final String requestPath = "/v1/account/accounts";
        final String query = buildSignedQueryString(HOST, "GET", requestPath);
        final String url = REST_SPOT_BASE + requestPath + "?" + query;
        final HttpUtils.Response response = HttpUtils.get(url, new HashMap<>(),
                subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
        if (response == null || response.getCode() != 200) {
            LOGGER.warn("HTX getAccounts failed: code=" + (response != null ? response.getCode() : "null")
                    + " body=" + (response != null ? response.getData() : "null"));
            return null;
        }
        final String accountsJson = response.getData();
        final String status = minExtract(accountsJson, "status");
        if (!"ok".equalsIgnoreCase(status)) return null;
        final String dataArray = extractJsonValue(accountsJson, "data");
        if (dataArray == null || !dataArray.startsWith("[")) return null;
        int i = 0;
        while (i < dataArray.length()) {
            final int objStart = dataArray.indexOf('{', i);
            if (objStart < 0) break;
            final int objEnd = dataArray.indexOf('}', objStart);
            if (objEnd < 0) break;
            final String accountObj = dataArray.substring(objStart, objEnd + 1);
            final String type = minExtract(accountObj, "type");
            if ("spot".equalsIgnoreCase(type)) {
                SPOT_ACCOUNT_ID = minExtract(accountObj, "id");
                return SPOT_ACCOUNT_ID;
            }
            i = objEnd + 1;
        }
        LOGGER.debug("HTX getSpotAccountId: no spot account in data array");
        return null;
    }

    public String getSpotBalanceSnapshot() {
        try {
            final String accountId = getSpotAccountId();
            if (accountId == null || accountId.isEmpty()) {
                LOGGER.warn("HTX: no spot account found");
                return null;
            }
            final String balancePath = "/v1/account/accounts/" + accountId + "/balance";
            final String balanceQuery = buildSignedQueryString(HOST, "GET", balancePath);
            final String balanceUrl = REST_SPOT_BASE + balancePath + "?" + balanceQuery;
            final HttpUtils.Response balanceResp = HttpUtils.get(balanceUrl, new HashMap<>(),
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (balanceResp != null && balanceResp.getCode() == 200) {
                LOGGER.debug("Successfully retrieved spot balance snapshot");
                return balanceResp.getData();
            }
            LOGGER.warn("Spot Balance Bootstrap failed: account=" + accountId
                    + " code=" + (balanceResp != null ? balanceResp.getCode() : "null")
                    + " body=" + (balanceResp != null ? balanceResp.getData() : "null"));
            return null;
        } catch (final Exception e) {
            LOGGER.error("Spot Balance Bootstrap failed: " + e.getMessage());
            return null;
        }
    }

    public boolean sendSpotOrderREST(final Order order, final String symbol, final String side, final String type,
                                     final String quantityStr, final String priceStr,
                                     final String clientOrderId) {
        try {
            final String accountId = getSpotAccountId();
            if (accountId == null || accountId.isEmpty()) {
                LOGGER.error("HTX spot order failed: no spot account id");
                order.setRejected(true);
                subscription.updateOrder(clientOrderId, order);
                return false;
            }
            final String symbolNorm = (symbol != null ? symbol : "").toLowerCase(Locale.ROOT).replace("/", "");
            final String orderType = toHtxOrderType(side, type);
            String body = "{\"account-id\":\"" + accountId + "\"" +
                    ",\"amount\":\"" + quantityStr + "\"" +
                    ",\"price\":\"" + priceStr + "\"" +
                    ",\"source\":\"spot-api\"" +
                    ",\"symbol\":\"" + symbolNorm + "\"" +
                    ",\"type\":\"" + orderType + "\"" +
                    ",\"client-order-id\":\"" + clientOrderId + "\"}";

            final String requestPath = "/v1/order/orders/place";
            final String query = buildSignedQueryString(HOST, "POST", requestPath);
            final String url = REST_SPOT_BASE + requestPath + "?" + query;
            final Map<String, Object> headers = acceptJsonHeaders();
            final byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

            LOGGER.debug("HTX spot order: symbol=" + symbolNorm + " side=" + side + " type=" + orderType);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, bodyBytes,
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp != null && resp.getCode() != 200) {
                LOGGER.warn("HTX spot order failed with status: " + resp.getCode() + " body=" + resp.getData());
            }
            final String json = resp != null ? resp.getData() : null;
            if (json != null) {
                final String status = minExtract(json, "status");
                if ("ok".equalsIgnoreCase(status)) {
                    final String orderId = minExtract(json, "data");
                    if (orderId != null && !orderId.isEmpty()) {
                        LOGGER.info("HTX REST spot order placed: orderId=" + orderId + " symbol=" + symbolNorm + " clOrdId=" + clientOrderId);
                        order.setOcoClOrdId(orderId);
                        final ExecutionReportMessage execNew = getOrCreateExecutionReport(order, clientOrderId);
                        execNew.setOrdStatus(OrdStatus.NEW);
                        execNew.setExecType(ExecType.NEW);
                        subscription.updateExecutionReport(execNew);
                        subscription.updateOrder(clientOrderId, order);
                        return true;
                    }
                } else {
                    final String errMsg = minExtract(json, "err-msg");
                    final String errorSection = errMsg != null && !errMsg.isEmpty() ? errMsg : json;
                    LOGGER.error("HTX REST spot order rejected: clOrdId=" + clientOrderId + " err=" + errorSection);
                    final ExecutionReportMessage execRej = getOrCreateExecutionReport(order, clientOrderId);
                    execRej.setError(errorSection);
                    execRej.setOrdStatus(OrdStatus.REJECTED);
                    execRej.setExecType(ExecType.REJECTED);
                    subscription.updateExecutionReport(execRej);
                    order.setRejected(true);
                    subscription.updateOrder(order.getClOrdId(), order);
                }
            }
            return false;
        } catch (final Exception e) {
            LOGGER.error("HTX spot order failed: " + e.getMessage());
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
            return false;
        }
    }

    public boolean cancelSpotOrderByClientOrderId(final Order order, final String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isEmpty()) {
            LOGGER.warn("HTX cancel: client order ID is empty");
            return false;
        }
        try {
            final String body = "{\"client-order-id\":\"" + escapeJson(clientOrderId) + "\"}";
            final String requestPath = "/v1/order/orders/submitCancelClientOrder";
            final String query = buildSignedQueryString(HOST, "POST", requestPath);
            final String url = REST_SPOT_BASE + requestPath + "?" + query;
            final Map<String, Object> headers = acceptJsonHeaders();
            final byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

            LOGGER.debug("HTX cancel spot order by client-order-id: " + clientOrderId);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, bodyBytes,
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("HTX cancel spot order failed with status=" + (resp != null ? resp.getCode() : "null"));
                return false;
            }
            final String json = resp.getData();
            final String status = minExtract(json, "status");
            if ("ok".equalsIgnoreCase(status)) {
                LOGGER.info("HTX REST spot order cancelled: client-order-id=" + clientOrderId);
                order.setRejected(true);
                final ExecutionReportMessage executionMessage = getOrCreateExecutionReport(order, clientOrderId);
                executionMessage.setExecType(ExecType.CANCELED);
                executionMessage.setOrdStatus(OrdStatus.CANCELED);
                subscription.updateExecutionReport(executionMessage);
                subscription.updateOrder(clientOrderId, order);
                return true;
            }
            final String errMsg = minExtract(json, "err-msg");
            LOGGER.warn("HTX REST cancel spot order failed: client-order-id=" + clientOrderId + " err=" + (errMsg != null && !errMsg.isEmpty() ? errMsg : json));
            return false;
        } catch (final Exception e) {
            LOGGER.error("HTX cancel spot order failed: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean querySpotOrderStatus(final Order order, final String clientOrderId) {
        try {
            final String orderId = order.getOcoClOrdId();
            if (orderId == null || orderId.isEmpty()) {
                LOGGER.debug("HTX querySpotOrderStatus: no order-id (OcoClOrdId) for clientOrderId=" + clientOrderId);
                return false;
            }
            LOGGER.debug("Querying HTX spot order status for clientOrderId=" + clientOrderId + " order-id=" + orderId);
            final String orderDetailPath = "/v1/order/orders/" + orderId;
            final String orderDetailQuery = buildSignedQueryString(HOST, "GET", orderDetailPath);
            final String orderDetailUrl = REST_SPOT_BASE + orderDetailPath + "?" + orderDetailQuery;
            final HttpUtils.Response orderDetailResp = HttpUtils.get(orderDetailUrl, new HashMap<>(),
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (orderDetailResp == null || orderDetailResp.getCode() != 200) {
                LOGGER.warn("HTX getOrderDetail failed: orderId=" + orderId
                        + " code=" + (orderDetailResp != null ? orderDetailResp.getCode() : "null"));
                return false;
            }
            final String json = orderDetailResp.getData();
            if (json == null) return false;
            final String status = minExtract(json, "status");
            if (!"ok".equalsIgnoreCase(status)) {
                LOGGER.warn("HTX order detail not ok: " + status);
                return true;
            }
            final String dataSection = extractJsonValue(json, "data");
            if (dataSection == null || !dataSection.startsWith("{")) return true;
            final String state = minExtract(dataSection, "state");
            if (state == null) return true;
            final String fieldAmount = minExtract(dataSection, "field-amount");
            final String amount = minExtract(dataSection, "amount");
            final String priceStr = minExtract(dataSection, "price");
            final double filledQty = parseDoubleSafe(fieldAmount);
            final double orderQty = parseDoubleSafe(amount);
            final double avgPrice = parseDoubleSafe(priceStr);
            final OrdStatus ordStatus = toOrdStatus(state);
            final long filledLong = MbxMath.changeScale(filledQty, order.getQtyScale());
            final long totalQtyLong = MbxMath.changeScale(orderQty, order.getQtyScale());
            final long avgPriceLong = MbxMath.changeScale(avgPrice, order.getPriceScale());
            final ExecutionReportMessage executionMessage = getOrCreateExecutionReport(order, clientOrderId);
            executionMessage.setClOrdId(clientOrderId);
            executionMessage.setOrdStatus(ordStatus);
            executionMessage.setCumQty(filledLong);
            executionMessage.setLeavesQty(totalQtyLong - filledLong);
            executionMessage.setPrice(avgPriceLong);
            executionMessage.setPriceScale(order.getPriceScale());
            executionMessage.setOrderQty(order.getQty());
            executionMessage.setOrderQtyScale(order.getQtyScale());
            subscription.updateExecutionReport(executionMessage);
            if ("filled".equalsIgnoreCase(state)) {
                order.setExecuted(true);
            } else if ("canceled".equalsIgnoreCase(state) || "partial-canceled".equalsIgnoreCase(state)) {
                order.setRejected(true);
            }
            subscription.updateOrder(order.getClOrdId(), order);
            return true;
        } catch (final Exception e) {
            LOGGER.error("HTX querySpotOrderStatus failed: " + e.getMessage());
            return false;
        }
    }

    public String getOpenOrders() throws Exception {
        final String requestPath = "/v1/order/openOrders";
        final String query = buildSignedQueryString(HOST, "GET", requestPath);
        final String url = REST_SPOT_BASE + requestPath + "?" + query;
        final HttpUtils.Response response = HttpUtils.get(url, new HashMap<>(),
                subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
        if (response != null && response.getCode() == 200) {
            LOGGER.debug("HTX getOpenOrders success");
            return response.getData();
        }
        LOGGER.warn("HTX getOpenOrders failed: code=" + (response != null ? response.getCode() : "null"));
        return null;
    }

    // -------------------------------------------------------------------------
    // Public API — Futures
    // -------------------------------------------------------------------------

    public String getFutureAccountSnapshot() {
        try {
            final String data = postFuturesJson("/linear-swap-api/v3/unified_account_info", "{}");
            if (data != null) {
                LOGGER.debug("Successfully retrieved future account snapshot");
                return data;
            }
            LOGGER.warn("Future account bootstrap failed with status: null");
            return null;
        } catch (final Exception e) {
            LOGGER.error("Future account bootstrap failed: " + e.getMessage());
            return null;
        }
    }

    public String getLinearSwapPositionInfo(final String contractCode) throws Exception {
        final String body = (contractCode == null || contractCode.isEmpty())
                ? "{}"
                : "{\"contract_code\":\"" + escapeJson(contractCode) + "\"}";
        return postFuturesJson("/linear-swap-api/v1/swap_position_info", body);
    }

    public String getLinearSwapOpenOrders(final String contractCode) throws Exception {
        final String c = (contractCode != null && !contractCode.isEmpty())
                ? contractCode.toLowerCase(Locale.ROOT)
                : "btc-usdt";
        final String body = "{\"contract_code\":\"" + escapeJson(c) + "\",\"page_index\":1,\"page_size\":50}";
        return postFuturesJson("/linear-swap-api/v1/swap_openorders", body);
    }

    public boolean sendLinearSwapOrderREST(final Order order, final String contractCode, final String direction,
                                           final String orderPriceType, final String volume, final String price,
                                           final String clientOrderId, final int leverRate,
                                           final String tpTriggerPrice, final String tpOrderPrice, final String tpOrderPriceType,
                                           final String slTriggerPrice, final String slOrderPrice, final String slOrderPriceType) throws Exception {
        final String requestPath = "/linear-swap-api/v1/swap_order";
        final String authQuery = buildSignedQueryString(HOST_FUTURES, "POST", requestPath);
        final String url = REST_FUTURES_BASE + requestPath + "?" + authQuery;
        final String c = escapeJson(contractCode != null ? contractCode.toLowerCase(Locale.ROOT) : "btc-usdt");
        final String d = escapeJson(direction != null ? direction.toLowerCase(Locale.ROOT) : "buy");
        final String t = escapeJson(orderPriceType != null ? orderPriceType.toLowerCase(Locale.ROOT) : "limit");
        final String v = volume != null && !volume.isEmpty() ? volume : "0";
        final String p = price != null && !price.isEmpty() ? price : "0";
        final String cid = escapeJson(clientOrderId);
        final int lev = leverRate <= 0 ? 10 : Math.min(125, Math.max(1, leverRate));
        final StringBuilder sb = new StringBuilder();
        sb.append("{\"contract_code\":\"").append(c).append("\"");
        if (cid != null && !cid.isEmpty()) {
            sb.append(",\"client_order_id\":\"").append(cid).append("\"");
        }
        sb.append(",\"direction\":\"").append(d).append("\",\"offset\":\"open\"");
        sb.append(",\"price\":\"").append(p).append("\"");
        sb.append(",\"lever_rate\":").append(lev);
        sb.append(",\"volume\":").append(v);
        sb.append(",\"order_price_type\":\"").append(t).append("\"");
        if (tpTriggerPrice != null && !tpTriggerPrice.isEmpty() && tpOrderPrice != null && !tpOrderPrice.isEmpty()) {
            sb.append(",\"tp_trigger_price\":").append(tpTriggerPrice);
            sb.append(",\"tp_order_price\":").append(tpOrderPrice);
            sb.append(",\"tp_order_price_type\":\"").append(escapeJson(tpOrderPriceType != null ? tpOrderPriceType : "optimal_5")).append("\"");
        }
        if (slTriggerPrice != null && !slTriggerPrice.isEmpty() && slOrderPrice != null && !slOrderPrice.isEmpty()) {
            sb.append(",\"sl_trigger_price\":\"").append(escapeJson(slTriggerPrice)).append("\"");
            sb.append(",\"sl_order_price\":\"").append(escapeJson(slOrderPrice)).append("\"");
            sb.append(",\"sl_order_price_type\":\"").append(escapeJson(slOrderPriceType != null ? slOrderPriceType : "optimal_5")).append("\"");
        }
        sb.append("}");
        final String jsonBody = sb.toString();
        final Map<String, Object> headers = contentTypeHeaders();
        final byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        LOGGER.debug("HTX linear-swap order: contract=" + contractCode + " direction=" + direction + " type=" + orderPriceType);
        final HttpUtils.Response resp = HttpUtils.post(url, headers, bodyBytes,
                subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
        final String json = resp != null ? resp.getData() : null;
        if (resp != null && resp.getCode() != 200) {
            LOGGER.warn("HTX linear-swap order failed: status=" + resp.getCode() + " body=" + json);
        }
        if (json == null) return false;
        final String status = minExtract(json, "status");
        if ("ok".equalsIgnoreCase(status)) {
            final String dataSection = extractJsonValue(json, "data");
            final String orderIdStr = dataSection != null ? minExtract(dataSection, "order_id_str") : null;
            if (orderIdStr != null && !orderIdStr.isEmpty()) {
                LOGGER.info("HTX REST linear-swap order placed: orderId=" + orderIdStr + " contract=" + contractCode + " clOrdId=" + clientOrderId);
                order.setOcoClOrdId(orderIdStr);
                final ExecutionReportMessage execNew = getOrCreateExecutionReport(order, clientOrderId);
                execNew.setOrdStatus(OrdStatus.NEW);
                execNew.setExecType(ExecType.NEW);
                subscription.updateExecutionReport(execNew);
                subscription.updateOrder(clientOrderId, order);
                return true;
            }
        } else {
            final String errMsg = minExtract(json, "err_msg");
            final String errSection = errMsg != null && !errMsg.isEmpty() ? errMsg : json;
            LOGGER.error("HTX REST linear-swap order rejected: clOrdId=" + clientOrderId + " err=" + errSection);
            final ExecutionReportMessage execRej = getOrCreateExecutionReport(order, clientOrderId);
            execRej.setError(errSection);
            execRej.setOrdStatus(OrdStatus.REJECTED);
            execRej.setExecType(ExecType.REJECTED);
            subscription.updateExecutionReport(execRej);
            order.setRejected(true);
            subscription.updateOrder(order.getClOrdId(), order);
        }
        return false;
    }

    public boolean cancelLinearSwapOrder(final String orderId, final String contractCode) {
        if (orderId == null || orderId.isEmpty()) {
            LOGGER.warn("HTX cancel linear-swap: order id is empty");
            return false;
        }
        try {
            final String c = (contractCode != null && !contractCode.isEmpty())
                    ? contractCode.toLowerCase(Locale.ROOT) : "btc-usdt";
            final String body = "{\"order_id\":\"" + escapeJson(orderId) + "\",\"contract_code\":\"" + escapeJson(c) + "\"}";
            final String requestPath = "/linear-swap-api/v1/swap_cancel";
            final String authQuery = buildSignedQueryString(HOST_FUTURES, "POST", requestPath);
            final String url = REST_FUTURES_BASE + requestPath + "?" + authQuery;
            final Map<String, Object> headers = contentTypeHeaders();
            final byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            final HttpUtils.Response resp = HttpUtils.post(url, headers, bodyBytes,
                    subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (resp == null || resp.getCode() != 200) {
                LOGGER.warn("HTX cancel linear-swap failed: status=" + (resp != null ? resp.getCode() : "null"));
                return false;
            }
            final String json = resp.getData();
            final String status = minExtract(json, "status");
            if ("ok".equalsIgnoreCase(status)) {
                LOGGER.info("HTX REST linear-swap order cancelled: orderId=" + orderId + " contract=" + c);
                return true;
            }
            final String errMsg = minExtract(json, "err_msg");
            LOGGER.warn("HTX cancel linear-swap rejected: orderId=" + orderId + " err=" + (errMsg != null ? errMsg : json));
            return false;
        } catch (final Exception e) {
            LOGGER.error("HTX cancel linear-swap failed: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean transferSpotToFutures(final String currency, final String amount) throws Exception {
        return transferBetweenSpotAndSwap(currency, amount, true);
    }

    public boolean transferFuturesToSpot(final String currency, final String amount) throws Exception {
        return transferBetweenSpotAndSwap(currency, amount, false);
    }

    // -------------------------------------------------------------------------
    // Public API — Market data
    // -------------------------------------------------------------------------

    public Ticker getSpotTicker(final String symbol) {
        try {
            final String url = REST_SPOT_BASE + "/market/detail/merged?symbol=" + (symbol != null ? symbol.toLowerCase(Locale.ROOT) : "");
            final Map<String, Object> headers = contentTypeHeaders();
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get spot ticker failed for symbol: " + symbol + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
            final String json = response.getData();
            final String status = minExtract(json, "status");
            if (!"ok".equalsIgnoreCase(status)) {
                LOGGER.warn("HTX spot ticker response not ok: " + status);
                return null;
            }
            final String tickStr = extractJsonValue(json, "tick");
            if (tickStr == null || !tickStr.startsWith("{")) {
                LOGGER.warn("No tick in spot ticker response for: " + symbol);
                return null;
            }
            final double open = parseDoubleSafe(minExtract(tickStr, "open"));
            final double close = parseDoubleSafe(minExtract(tickStr, "close"));
            final double high = parseDoubleSafe(minExtract(tickStr, "high"));
            final double low = parseDoubleSafe(minExtract(tickStr, "low"));
            final double vol = parseDoubleSafe(minExtract(tickStr, "vol"));
            final long ts = parseLongSafe(minExtract(tickStr, "id"));
            final double bid = parseFirstArrayElementDouble(minExtract(tickStr, "bid"));
            final double ask = parseFirstArrayElementDouble(minExtract(tickStr, "ask"));
            final double bidSize = parseSecondArrayElementDouble(minExtract(tickStr, "bid"));
            final double askSize = parseSecondArrayElementDouble(minExtract(tickStr, "ask"));
            LOGGER.debug("Retrieved HTX spot ticker for symbol: " + symbol + " last=" + close + " bid=" + bid + " ask=" + ask);
            return new Ticker(symbol, 0, open, close, bid, ask, high, low, vol, 0.0, ts, bidSize, askSize, 0.0);
        } catch (final Exception e) {
            LOGGER.error("Get spot ticker failed for symbol: " + symbol + " " + e.getMessage());
            return null;
        }
    }

    public Ticker getFutureTicker(final String contractCode) {
        try {
            final String code = (contractCode != null ? contractCode : "").toLowerCase(Locale.ROOT);
            final String url = REST_FUTURES_BASE + "/linear-swap-ex/market/detail/merged?contract_code=" + code;
            final Map<String, Object> headers = contentTypeHeaders();
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get future ticker failed for contract: " + contractCode + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }
            final String json = response.getData();
            final String status = minExtract(json, "status");
            if (!"ok".equalsIgnoreCase(status)) {
                LOGGER.warn("HTX future ticker response not ok: " + status);
                return null;
            }
            final String tickStr = extractJsonValue(json, "tick");
            if (tickStr == null || !tickStr.startsWith("{")) {
                LOGGER.warn("No tick in future ticker response for: " + contractCode);
                return null;
            }
            final double open = parseDoubleSafe(minExtract(tickStr, "open"));
            final double close = parseDoubleSafe(minExtract(tickStr, "close"));
            final double high = parseDoubleSafe(minExtract(tickStr, "high"));
            final double low = parseDoubleSafe(minExtract(tickStr, "low"));
            final double vol = parseDoubleSafe(minExtract(tickStr, "vol"));
            final long ts = parseLongSafe(minExtract(tickStr, "ts"));
            final double bid = parseFirstArrayElementDouble(minExtract(tickStr, "bid"));
            final double ask = parseFirstArrayElementDouble(minExtract(tickStr, "ask"));
            final double bidSize = parseSecondArrayElementDouble(minExtract(tickStr, "bid"));
            final double askSize = parseSecondArrayElementDouble(minExtract(tickStr, "ask"));
            LOGGER.debug("Retrieved HTX future ticker for contract: " + contractCode + " last=" + close + " bid=" + bid + " ask=" + ask);
            return new Ticker(contractCode, 0, open, close, bid, ask, high, low, vol, 0.0, ts, bidSize, askSize, 0.0);
        } catch (final Exception e) {
            LOGGER.error("Get future ticker failed for contract: " + contractCode + " " + e.getMessage());
            return null;
        }
    }

    public List<ExternalSymbol> getExchangeInstrumentsFull() {
        final List<ExternalSymbol> combined = new ArrayList<>();
        final String spotJson = getSpotSymbolDetails();
        final String futureJson = getFutureSymbolDetails();
        if (futureJson != null) {
            final long updated = System.currentTimeMillis();
            for (final String obj : extractJsonObjects(extractJsonValue(futureJson, "data"))) {
                final String contractCode = minExtract(obj, "contract_code");
                if (contractCode != null && contractCode.contains("-")) {
                    final String[] parts = contractCode.split("-", 2);
                    final String base = parts[0];
                    final String quote = parts.length > 1 ? parts[1] : "USDT";
                    final double priceTick = parseDoubleSafe(minExtract(obj, "price_tick"));
                    final int priceScale = priceTick > 0 && priceTick < 1 ? Math.max(0, (int) Math.round(-Math.log10(priceTick))) : 0;
                    final String volPrecisionStr = minExtract(obj, "volume_precision");
                    final int qtyScale = volPrecisionStr != null ? (int) parseDoubleSafe(volPrecisionStr) : 0;
                    final ExternalSymbol sd = new ExternalSymbol();
                    sd.setExchange("htx");
                    sd.setSymbol(contractCode);
                    sd.setBase(base);
                    sd.setQuote(quote);
                    sd.setFutures(true);
                    sd.setTradable(true);
                    sd.setUpdated(updated);
                    sd.setPriceScale(priceScale);
                    sd.setQtyScale(qtyScale);
                    combined.add(sd);
                }
            }
        }
        if (spotJson != null) {
            final long updated = System.currentTimeMillis();
            for (final String obj : extractJsonObjects(extractJsonValue(spotJson, "data"))) {
                final String symbol = minExtract(obj, "symbol");
                final String base = minExtract(obj, "base-currency");
                final String quote = minExtract(obj, "quote-currency");
                final int pricePrecision = (int) parseDoubleSafe(minExtract(obj, "price-precision"));
                final int amountPrecision = (int) parseDoubleSafe(minExtract(obj, "amount-precision"));
                if (symbol != null && base != null && quote != null) {
                    final ExternalSymbol sd = new ExternalSymbol();
                    sd.setExchange("htx");
                    sd.setSymbol(symbol);
                    sd.setBase(base.toUpperCase(Locale.ROOT));
                    sd.setQuote(quote.toUpperCase(Locale.ROOT));
                    sd.setFutures(false);
                    sd.setTradable(true);
                    sd.setUpdated(updated);
                    sd.setPriceScale(pricePrecision);
                    sd.setQtyScale(amountPrecision);
                    combined.add(sd);
                }
            }
        }
        LOGGER.info("HTX instruments loaded: " + combined.size());
        return combined;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String getSpotSymbolDetails() {
        try {
            final String url = REST_SPOT_BASE + "/v1/common/symbols";
            final Map<String, Object> headers = contentTypeHeaders();
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                return response.getData();
            }
        } catch (final Exception e) {
            LOGGER.error("getSpotSymbolDetails failed: " + e.getMessage());
        }
        return null;
    }

    private String getFutureSymbolDetails() {
        try {
            final String url = REST_FUTURES_BASE + "/linear-swap-api/v1/swap_contract_info";
            final Map<String, Object> headers = contentTypeHeaders();
            final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
            if (response != null && response.getCode() == 200) {
                return response.getData();
            }
        } catch (final Exception e) {
            LOGGER.error("getFutureSymbolDetails failed: " + e.getMessage());
        }
        return null;
    }

    private boolean transferBetweenSpotAndSwap(final String currency, final String amount, final boolean fromSpotToFutures) throws Exception {
        if (currency == null || currency.trim().isEmpty() || amount == null || amount.trim().isEmpty()) {
            LOGGER.warn("HTX transfer: currency and amount are required");
            return false;
        }
        final String requestPath = "/v1/account/transfer";
        final String fromAccountType = fromSpotToFutures ? "spot" : "linear-swap";
        final String toAccountType = fromSpotToFutures ? "linear-swap" : "spot";
        final String spotAccountId = getSpotAccountId();
        if (spotAccountId == null || spotAccountId.isEmpty()) {
            LOGGER.warn("HTX transfer: could not get spot account id");
            return false;
        }
        final String body = "{\"from-user\":" + spotAccountId + ",\"from-account-type\":\"" + fromAccountType
                + "\",\"from-account\":" + spotAccountId + ",\"to-user\":" + spotAccountId
                + ",\"to-account-type\":\"" + toAccountType + "\",\"to-account\":" + spotAccountId
                + ",\"currency\":\"" + escapeJson(currency.trim()) + "\",\"amount\":\"" + escapeJson(amount.trim()) + "\"}";
        final String query = buildSignedQueryString(HOST, "POST", requestPath);
        final String url = REST_SPOT_BASE + requestPath + "?" + query;
        final Map<String, Object> headers = contentTypeHeaders();
        final byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        final String logDir = fromSpotToFutures ? "Spot -> Futures" : "Futures -> Spot";
        LOGGER.debug("HTX transfer " + logDir + ": currency=" + currency + " amount=" + amount);
        final HttpUtils.Response resp = HttpUtils.post(url, headers, bodyBytes,
                subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
        final String json = resp != null ? resp.getData() : null;
        if (resp == null || resp.getCode() != 200) {
            LOGGER.warn("HTX transfer " + logDir + " failed: status=" + (resp != null ? resp.getCode() : "null") + " body=" + json);
            return false;
        }
        final String status = minExtract(json != null ? json : "", "status");
        if ("ok".equalsIgnoreCase(status)) {
            LOGGER.info("HTX transfer " + logDir + " success: " + currency + " " + amount);
            return true;
        }
        final String errMsg = json != null ? minExtract(json, "err-msg") : null;
        LOGGER.warn("HTX transfer " + logDir + " rejected: " + (errMsg != null ? errMsg : json));
        return false;
    }

    private String postFuturesJson(final String requestPath, final String jsonBody) throws Exception {
        final String authQuery = buildSignedQueryString(HOST_FUTURES, "POST", requestPath);
        final String url = REST_FUTURES_BASE + requestPath + "?" + authQuery;
        final Map<String, Object> headers = contentTypeHeaders();
        final byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        final HttpUtils.Response response = HttpUtils.post(url, headers, bodyBytes,
                subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
        if (response != null && response.getCode() == 200) {
            return response.getData();
        }
        return null;
    }

    private ExecutionReportMessage getOrCreateExecutionReport(final Order order, final String clientOrderId) {
        final ExecutionReportMessage existing = subscription.getExecutionReport(order.getClOrdId());
        if (existing != null) {
            return existing;
        }
        final ExecutionReportMessage executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                0, 0, 0, 0, order.getSide(), 0);
        executionMessage.setClOrdId(clientOrderId);
        return executionMessage;
    }

    private String buildSignedQueryString(final String host, final String method, final String requestPath) throws Exception {
        final String timestamp = HUOBI_TIMESTAMP_FORMAT.format(Instant.now());
        final Map<String, String> params = new TreeMap<>();
        params.put("AccessKeyId", accessKey);
        params.put("SignatureMethod", "HmacSHA256");
        params.put("SignatureVersion", "2");
        params.put("Timestamp", timestamp);
        final StringBuilder q = new StringBuilder();
        for (final Map.Entry<String, String> e : params.entrySet()) {
            if (q.length() > 0) q.append("&");
            q.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            q.append("=");
            q.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        final String queryString = q.toString();
        final String signatureBase = method + "\n" + host + "\n" + requestPath + "\n" + queryString;
        final String hmacHex = HMAC.hmacSha256(signatureBase, secretKey);
        final byte[] hmacBytes = hexStringToByteArray(hmacHex);
        final String signatureB64 = Base64.getEncoder().encodeToString(hmacBytes);
        final String signatureEncoded = URLEncoder.encode(signatureB64, StandardCharsets.UTF_8);
        return queryString + "&Signature=" + signatureEncoded;
    }

    private static String toHtxOrderType(final String side, final String type) {
        final String s = (side != null ? side : "").toLowerCase(Locale.ROOT);
        final String t = (type != null ? type : "").toLowerCase(Locale.ROOT);
        if ("market".equals(t)) return "buy".equals(s) ? "buy-market" : "sell-market";
        return "buy".equals(s) ? "buy-limit" : "sell-limit";
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

    private static Map<String, Object> contentTypeHeaders() {
        final Map<String, Object> h = new HashMap<>();
        h.put("Content-Type", "application/json");
        return h;
    }

    private static Map<String, Object> acceptJsonHeaders() {
        final Map<String, Object> h = new HashMap<>();
        h.put("Accept", "application/json");
        h.put("Content-Type", "application/json");
        return h;
    }

    private static OrdStatus toOrdStatus(final String state) {
        if (state == null) return OrdStatus.NEW;
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "filled" -> OrdStatus.FILLED;
            case "canceled", "partial-canceled" -> OrdStatus.CANCELED;
            case "partial-filled" -> OrdStatus.PARTIALLY_FILLED;
            default -> OrdStatus.NEW;
        };
    }
}
