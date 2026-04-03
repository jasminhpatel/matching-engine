package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.htx;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketClientHandler;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketListenerInterface;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.util.MbxMath;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import com.solfini.util.HMAC;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class HtxSpotUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(HtxSpotUserDataListener.class);
    private static final String WS_URL = "wss://api.huobi.pro/ws/v2";
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;
    private static final String HOST = "api.huobi.pro";
    private static final DateTimeFormatter HUOBI_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private static final long WATCHDOG_TIMEOUT_SECONDS = 45;

    private final HtxRestClient restClient;
    private final ExchangeSubscription subscription;

    private EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private volatile Channel channel;
    private volatile long lastMessageReceived = System.currentTimeMillis();
    private ScheduledFuture<?> watchdogFuture;

    public HtxSpotUserDataListener(final HtxRestClient restClient, final ExchangeSubscription subscription) {
        this.restClient = restClient;
        this.subscription = subscription;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public void onBinaryMessage(final byte[] bytes) {
        // HTX spot WS v2 uses text frames only
    }

    @Override
    public void connect() throws Exception {
        if (group.isShuttingDown() || group.isTerminated()) {
            group = new NioEventLoopGroup();
        }
        final URI uri = new URI(WS_URL);
        final SslContext sslCtx = SslContextBuilder.forClient().build();
        final WebSocketClientHandshaker handshaker =
                WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());
        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "HTX-SPOT-USER-DATA-LISTENER");

        final Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class);
        final String outboundIp = Context.getOutboundIp();
        if (outboundIp != null && !outboundIp.isEmpty()) {
            b.localAddress(new InetSocketAddress(outboundIp, 0));
        }
        b.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                final ChannelPipeline p = ch.pipeline();
                p.addLast(sslCtx.newHandler(ch.alloc(), HOST, SSL_PORT));
                p.addLast(new HttpClientCodec());
                p.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                p.addLast(handler);
            }
        });

        this.channel = b.connect(HOST, SSL_PORT).sync().channel();
        handler.handshakeFuture().sync();

        LOGGER.info("HTX SpotUserData WebSocket connected");
        authenticate();
    }

    @Override
    public void disconnect() {
        setConnected(false);
        setAuthenticated(false);
        stopWatchdog();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        group.shutdownGracefully();
    }

    @Override
    public boolean isConnected() {
        return connected.get() && authenticated.get();
    }

    @Override
    public void setConnected(final boolean connected) {
        this.connected.set(connected);
    }

    @Override
    public void setAuthenticated(final boolean authenticated) {
        this.authenticated.set(authenticated);
    }

    @Override
    public void setLastPongReceived(final long timeStamp) {
        lastMessageReceived = timeStamp;
    }

    @Override
    public void reconnect() {
        LOGGER.info("HTX SpotUserData WebSocket reconnecting in " + RECONNECT_DELAY_SEC + " seconds...");
        setConnected(false);
        setAuthenticated(false);
        stopWatchdog();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }

        if (channel != null && channel.eventLoop() != null && !channel.eventLoop().isShuttingDown()) {
            channel.eventLoop().schedule(() -> {
                try {
                    LOGGER.info("Attempting reconnection to HTX SpotUserData WebSocket");
                    connect();
                } catch (final Exception e) {
                    LOGGER.error("Error during reconnect: " + e.getMessage(), e);
                    reconnect();
                }
            }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void authenticate() throws Exception {
        final Map<String, String> params = buildWsAuthParams("/ws/v2", "2.1");
        if (params == null || params.isEmpty()) {
            LOGGER.error("HTX WS v2 auth params failed");
            reconnect();
            return;
        }
        // Build auth request: {"action":"req","ch":"auth","params":{...}}
        final StringBuilder sb = new StringBuilder();
        sb.append("{\"action\":\"req\",\"ch\":\"auth\",\"params\":{");
        sb.append("\"authType\":\"").append(escapeJson(params.get("authType"))).append("\"");
        sb.append(",\"accessKey\":\"").append(escapeJson(params.get("accessKey"))).append("\"");
        sb.append(",\"signatureMethod\":\"").append(escapeJson(params.get("signatureMethod"))).append("\"");
        sb.append(",\"signatureVersion\":\"").append(escapeJson(params.get("signatureVersion"))).append("\"");
        sb.append(",\"timestamp\":\"").append(escapeJson(params.get("timestamp"))).append("\"");
        sb.append(",\"signature\":\"").append(escapeJson(params.get("signature"))).append("\"");
        sb.append("}}");
        final String authMsg = sb.toString();
        LOGGER.debug("HTX WS v2 sending auth request");
        channel.writeAndFlush(new TextWebSocketFrame(authMsg));
    }

    private Map<String, String> buildWsAuthParams(final String requestPath, final String signatureVersion) throws Exception {
        final String timestamp = HUOBI_TIMESTAMP_FORMAT.format(Instant.now());
        final Map<String, String> params = new TreeMap<>();
        params.put("accessKey", subscription.getApiKey());
        params.put("signatureMethod", "HmacSHA256");
        params.put("signatureVersion", signatureVersion);
        params.put("timestamp", timestamp);
        final StringBuilder q = new StringBuilder();
        for (final Map.Entry<String, String> e : params.entrySet()) {
            if (q.length() > 0) q.append("&");
            q.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            q.append("=");
            q.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        final String queryString = q.toString();
        final String signatureBase = "GET\n" + HOST + "\n" + requestPath + "\n" + queryString;
        final String hmacHex = HMAC.hmacSha256(signatureBase, subscription.getApiSecret());
        final byte[] hmacBytes = hexStringToByteArray(hmacHex);
        final String signatureB64 = Base64.getEncoder().encodeToString(hmacBytes);
        params.put("authType", "api");
        params.put("signature", signatureB64);
        return params;
    }

    private ExecutionReportMessage getOrCreateExecutionReport(final Order order) {
        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                    0, 0, 0, 0, order.getSide(), 0);
        }
        return executionMessage;
    }

    private static byte[] hexStringToByteArray(final String hex) {
        final int len = hex.length();
        final byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private void subscribeToPrivateFeeds() {
        if (channel == null || !channel.isActive()) {
            return;
        }
        final String accountsSub = "{\"action\":\"sub\",\"ch\":\"accounts.update#0\"}";
        LOGGER.debug("HTX WS v2 subscribing to accounts.update#0 (balances)");
        channel.writeAndFlush(new TextWebSocketFrame(accountsSub));
        final String ordersSub = "{\"action\":\"sub\",\"ch\":\"orders#*\"}";
        LOGGER.debug("HTX WS v2 subscribing to orders#* (order updates)");
        channel.writeAndFlush(new TextWebSocketFrame(ordersSub));
    }

    private void scheduleWatchdog() {
        stopWatchdog();
        if (channel == null || channel.eventLoop() == null || channel.eventLoop().isShuttingDown()) {
            return;
        }
        watchdogFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            final long elapsed = System.currentTimeMillis() - lastMessageReceived;
            if (elapsed > WATCHDOG_TIMEOUT_SECONDS * 1000L) {
                LOGGER.warn("HTX SpotUserData no message for " + (elapsed / 1000) + "s, reconnecting");
                reconnect();
            }
        }, WATCHDOG_TIMEOUT_SECONDS, WATCHDOG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void stopWatchdog() {
        if (watchdogFuture != null) {
            watchdogFuture.cancel(false);
            watchdogFuture = null;
        }
    }

    @Override
    public void onMessage(final String json) {
        lastMessageReceived = System.currentTimeMillis();
        LOGGER.debug("HTX SpotUserData received: " + json);

        final String action = minExtract(json, "action");
        final String ch = minExtract(json, "ch");

        if ("ping".equals(action)) {
            handlePing(json);
        } else if ("pong".equals(action)) {
            LOGGER.debug("HTX SpotUserData pong");
        } else if ("req".equals(action)) {
            handleAuthResponse(json);
        } else if ("sub".equals(action)) {
            handleSubResponse(json);
        } else if ("push".equals(action)) {
            if (ch != null && ch.startsWith("accounts.update")) {
                handleAccountsUpdate(json);
            } else if (ch != null && ch.startsWith("orders")) {
                handleOrderUpdate(json);
            }
        } else if ("error".equals(action)) {
            final String errMsg = minExtract(json, "message");
            LOGGER.error("HTX SpotUserData error: " + errMsg + ", " + json);
        }
    }

    private void handlePing(final String message) {
        final String dataStr = extractJsonValue(message, "data");
        if (dataStr != null) {
            final String ts = minExtract(dataStr, "ts");
            if (ts != null) {
                final String pong = "{\"action\":\"pong\",\"data\":{\"ts\":" + ts + "}}";
                if (channel != null && channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(pong));
                }
            }
        }
    }

    private void handleAuthResponse(final String message) {
        final String codeStr = minExtract(message, "code");
        final int code = codeStr != null ? (int) parseLongSafe(codeStr) : -1;
        if (code == 200) {
            LOGGER.info("HTX WS v2 auth success");
            setAuthenticated(true);
            subscribeToPrivateFeeds();
        } else {
            final String errMsg = minExtract(message, "message");
            LOGGER.error("HTX WS v2 auth failed: code=" + code + " message=" + errMsg);
            reconnect();
        }
    }

    private void handleSubResponse(final String message) {
        final String codeStr = minExtract(message, "code");
        final int code = codeStr != null ? (int) parseLongSafe(codeStr) : -1;
        final String ch = minExtract(message, "ch");
        if (code == 200) {
            LOGGER.info("HTX SpotUserData subscribed to " + ch);
            if (!isConnected()) {
                setConnected(true);
                scheduleWatchdog();
            }
        } else {
            final String errMsg = minExtract(message, "message");
            LOGGER.warn("HTX SpotUserData sub failed for " + ch + ": code=" + code + " " + errMsg);
        }
    }

    private void handleAccountsUpdate(final String message) {
        try {
            LOGGER.debug("HTX SpotUserData accounts update: " + message);

            String listStr = extractJsonValue(message, "list");
            if (listStr == null) {
                final String dataStr = extractJsonValue(message, "data");
                if (dataStr != null && dataStr.startsWith("{")) {
                    listStr = extractJsonValue(dataStr, "list");
                } else {
                    listStr = dataStr;
                }
            }
            if (listStr == null || (!listStr.startsWith("[") && !listStr.startsWith("{"))) {
                return;
            }
            if (listStr.startsWith("{")) {
                listStr = "[" + listStr + "]";
            }

            int p = 0;
            while (p < listStr.length()) {
                final int objStart = listStr.indexOf('{', p);
                if (objStart < 0) break;

                int braceDepth = 0;
                int objEnd = -1;
                for (int i = objStart; i < listStr.length(); i++) {
                    final char c = listStr.charAt(i);
                    if (c == '{') braceDepth++;
                    else if (c == '}') {
                        braceDepth--;
                        if (braceDepth == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }
                if (objEnd < 0) break;

                final String balanceObj = listStr.substring(objStart, objEnd + 1);
                final String currency = minExtract(balanceObj, "currency");
                final String balanceStr = minExtract(balanceObj, "balance");
                if (currency != null && balanceStr != null) {
                    final double balance = parseDoubleSafe(balanceStr);
                    final String asset = currency.toUpperCase();
                    subscription.updateBalance(asset, balance);
                    LOGGER.info("HTX SPOT USER DATA >>> Balance update asset: " + asset + ", balance: " + balanceStr);
                }
                p = objEnd + 1;
            }
        } catch (final Exception e) {
            LOGGER.error("HTX SpotUserData handleAccountsUpdate error: " + e.getMessage());
        }
    }

    private void handleOrderUpdate(final String message) {
        try {
            LOGGER.debug("HTX SpotUserData order update: " + message);

            String dataStr = extractJsonValue(message, "data");
            if (dataStr == null || !dataStr.startsWith("{")) {
                return;
            }

            final String clientOrderId = minExtract(dataStr, "client-order-id");
            final String clientOrderIdAlt = minExtract(dataStr, "clientOrderId");
            final String clOrdId = clientOrderId != null && !clientOrderId.isEmpty() ? clientOrderId : clientOrderIdAlt;
            final String orderId = minExtract(dataStr, "order-id");
            final String orderIdAlt = minExtract(dataStr, "orderId");
            final String exchangeOrderId = orderId != null && !orderId.isEmpty() ? orderId : orderIdAlt;
            final String orderStatus = minExtract(dataStr, "orderStatus");
            final String eventType = minExtract(dataStr, "eventType");
            final String orderPriceStr = minExtract(dataStr, "orderPrice");
            final String orderSizeStr = minExtract(dataStr, "orderSize");
            final String filledAmountStr = minExtract(dataStr, "filled-amount");
            final String filledAmountAlt = minExtract(dataStr, "filledAmount");
            final String matchPriceStr = minExtract(dataStr, "matchPrice");
            final String matchAmountStr = minExtract(dataStr, "matchAmount");

            if (exchangeOrderId == null && clOrdId == null) {
                LOGGER.warn("HTX order update missing order-id and client-order-id");
                return;
            }

            Order order = clOrdId != null ? subscription.getOrder(clOrdId) : null;
            if (order == null && exchangeOrderId != null) {
                for (Order o : subscription.getOrders().values()) {
                    if (exchangeOrderId.equals(o.getOcoClOrdId())) {
                        order = o;
                        break;
                    }
                }
            }
            if (order == null) {
                LOGGER.warn("HTX order update: order not found in cache for clientOrderId=" + clOrdId + " orderId=" + exchangeOrderId);
                return;
            }

            final String effectiveClOrdId = order.getClOrdId();
            final double orderQtyDouble = parseDoubleSafe(orderSizeStr);
            final double orderPriceDouble = parseDoubleSafe(orderPriceStr);
            final double filledQtyDouble = parseDoubleSafe(filledAmountStr != null ? filledAmountStr : filledAmountAlt);
            final double matchPriceDouble = parseDoubleSafe(matchPriceStr);
            final double matchQtyDouble = parseDoubleSafe(matchAmountStr);

            final long qtyLong = MbxMath.changeScale(orderQtyDouble, order.getQtyScale());
            final long filledLong = MbxMath.changeScale(filledQtyDouble, order.getQtyScale());
            final long priceLong = MbxMath.changeScale(orderPriceDouble, order.getPriceScale());
            final long matchPriceLong = MbxMath.changeScale(matchPriceDouble, order.getPriceScale());
            final long matchQtyLong = MbxMath.changeScale(matchQtyDouble, order.getQtyScale());

            LOGGER.info("HTX SPOT USER DATA >>> Order update orderId=" + exchangeOrderId + " clOrdId=" + effectiveClOrdId
                    + " orderStatus=" + orderStatus + " eventType=" + eventType + " filled=" + filledAmountStr);

            final ExecutionReportMessage executionMessage = getOrCreateExecutionReport(order);
            executionMessage.setClOrdId(effectiveClOrdId);
            executionMessage.setOrderQty(qtyLong);
            executionMessage.setOrderQtyScale(order.getQtyScale());
            executionMessage.setPrice(priceLong);
            executionMessage.setPriceScale(order.getPriceScale());
            executionMessage.setCumQty(filledLong);
            executionMessage.setLeavesQty(qtyLong - filledLong);
            if (exchangeOrderId != null) {
                order.setOcoClOrdId(exchangeOrderId);
            }
            if (matchQtyLong > 0) {
                executionMessage.setLastQty(matchQtyLong);
                executionMessage.setLastQtyScale(order.getQtyScale());
                executionMessage.setLastPx(matchPriceLong);
                executionMessage.setLastPxScale(order.getPriceScale());
            }

            if ("submitted".equalsIgnoreCase(orderStatus)) {
                executionMessage.setOrdStatus(OrdStatus.NEW);
                executionMessage.setExecType("creation".equalsIgnoreCase(eventType) ? ExecType.NEW : ExecType.PENDING_NEW);
            } else if ("partial-filled".equalsIgnoreCase(orderStatus)) {
                executionMessage.setOrdStatus(OrdStatus.PARTIALLY_FILLED);
                executionMessage.setExecType("trade".equalsIgnoreCase(eventType) ? ExecType.TRADE : ExecType.PARTIAL_FILL);
            } else if ("filled".equalsIgnoreCase(orderStatus)) {
                executionMessage.setOrdStatus(OrdStatus.FILLED);
                executionMessage.setExecType(ExecType.TRADE);
                order.setExecuted(true);
            } else if ("canceled".equalsIgnoreCase(orderStatus) || "cancellation".equalsIgnoreCase(eventType)) {
                executionMessage.setOrdStatus(OrdStatus.CANCELED);
                executionMessage.setExecType(ExecType.CANCELED);
                order.setRejected(true);
            }

            subscription.updateExecutionReport(executionMessage);
            subscription.updateOrder(effectiveClOrdId, order);
        } catch (final Exception e) {
            LOGGER.error("HTX SpotUserData handleOrderUpdate error: " + e.getMessage(), e);
        }
    }
}
