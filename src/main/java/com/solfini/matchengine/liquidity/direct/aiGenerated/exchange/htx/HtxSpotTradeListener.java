package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.htx;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketClientHandler;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketListenerInterface;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.util.HMAC;
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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class HtxSpotTradeListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(HtxSpotTradeListener.class);
    private static final String WS_URL = "wss://api.huobi.pro/ws/trade";
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;
    private static final String HOST = "api.huobi.pro";
    private static final DateTimeFormatter HUOBI_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private final HtxRestClient restClient;
    private final ExchangeSubscription subscription;

    private EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicBoolean authSent = new AtomicBoolean(false);
    private Channel channel;

    public HtxSpotTradeListener(final HtxRestClient restClient, final ExchangeSubscription subscription) {
        this.restClient = restClient;
        this.subscription = subscription;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public void onBinaryMessage(final byte[] bytes) {
        // HTX ws/trade uses text frames only
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
        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "HTX-SPOT-TRADE-LISTENER");

        final Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
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
        LOGGER.info("HTX SpotTrade WebSocket connected (ws/trade)");

        try {
            final String timestamp = HUOBI_TIMESTAMP_FORMAT.format(Instant.now());
            final Map<String, String> authParams = new TreeMap<>();
            authParams.put("accessKey", subscription.getApiKey());
            authParams.put("signatureMethod", "HmacSHA256");
            authParams.put("signatureVersion", "2.1");
            authParams.put("timestamp", timestamp);
            final String signatureB64 = computeSignature("GET", "/ws/trade", authParams);
            final StringBuilder sb = new StringBuilder();
            sb.append("{\"action\":\"req\",\"ch\":\"auth\",\"params\":{");
            sb.append("\"authType\":\"api\"");
            sb.append(",\"accessKey\":\"").append(escapeJson(subscription.getApiKey())).append("\"");
            sb.append(",\"signatureMethod\":\"HmacSHA256\"");
            sb.append(",\"signatureVersion\":\"2.1\"");
            sb.append(",\"timestamp\":\"").append(escapeJson(timestamp)).append("\"");
            sb.append(",\"signature\":\"").append(escapeJson(signatureB64)).append("\"");
            sb.append("}}");
            LOGGER.debug("HTX ws/trade sending auth request");
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(sb.toString()));
                authSent.set(true);
            }
        } catch (final Exception e) {
            LOGGER.error("HTX ws/trade auth request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
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
    }

    @Override
    public void reconnect() {
        authSent.set(false);
        if (channel == null) return;
        final var loop = channel.eventLoop();
        if (loop == null || loop.isShuttingDown()) return;
        if (channel.isOpen()) channel.close();
        loop.schedule(() -> {
            try {
                LOGGER.info("HTX SpotTrade WebSocket reconnecting...");
                connect();
            } catch (Exception e) {
                LOGGER.error("HTX SpotTrade reconnect error", e);
                reconnect();
            }
        }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
    }

    @Override
    public void onMessage(final String message) {
        LOGGER.debug("HTX SpotTrade WS message: " + message);
        final String action = minExtract(message, "action");
        if ("ping".equals(action)) {
            handlePing(message);
            return;
        }
        if ("req".equals(action)) {
            handleAuthResponse(message);
            return;
        }
        final String cid = minExtract(message, "cid");
        if (cid == null || cid.isEmpty()) {
            LOGGER.warn("HTX SpotTrade WS: ignoring message without cid (action=" + action + ")");
            return;
        }
        handleCreateOrderResponse(message);
    }

    public void placeOrder(final Order order, final String symbol, final String side, final String type,
                           final String quantity, final String price, final String clOrdId) {
        try {
            final String accountId = restClient.getSpotAccountId();
            if (accountId == null || accountId.isEmpty()) {
                LOGGER.error("HTX spot order failed: no spot account id");
                restClient.sendSpotOrderREST(order, symbol, side, type, quantity, price, clOrdId);
                return;
            }
            final String symbolNorm = (symbol != null ? symbol : "").toLowerCase(Locale.ROOT).replace("/", "");
            final String s = (side != null ? side : "").toLowerCase(Locale.ROOT);
            final String t = (type != null ? type : "").toLowerCase(Locale.ROOT);
            final String orderType = "market".equals(t)
                    ? ("buy".equals(s) ? "buy-market" : "sell-market")
                    : ("buy".equals(s) ? "buy-limit" : "sell-limit");
            final String timestamp = HUOBI_TIMESTAMP_FORMAT.format(Instant.now());
            final Map<String, String> params = new TreeMap<>();
            params.put("AccessKeyId", subscription.getApiKey());
            params.put("SignatureMethod", "HmacSHA256");
            params.put("SignatureVersion", "2");
            params.put("Timestamp", timestamp);
            params.put("account-id", accountId);
            params.put("amount", quantity);
            params.put("client-order-id", clOrdId);
            params.put("price", price);
            params.put("source", "spot-api");
            params.put("symbol", symbolNorm);
            params.put("type", orderType);
            final String signatureB64 = computeSignature("POST", "/ws/trade", params);
            params.put("Signature", signatureB64);
            final StringBuilder paramsJson = new StringBuilder();
            paramsJson.append("{");
            boolean first = true;
            for (final Map.Entry<String, String> e : params.entrySet()) {
                if (!first) paramsJson.append(",");
                first = false;
                paramsJson.append("\"").append(escapeJson(e.getKey())).append("\":");
                final String v = e.getValue();
                if ("account-id".equals(e.getKey()) && v != null && v.matches("\\d+")) {
                    paramsJson.append(v);
                } else {
                    paramsJson.append("\"").append(escapeJson(v)).append("\"");
                }
            }
            paramsJson.append("}");
            final String createOrderJson = "{\"ch\":\"create-order\",\"params\":" + paramsJson
                    + ",\"cid\":\"" + escapeJson(clOrdId) + "\"}";
            LOGGER.info("HTX spot place order via WS: symbol=" + symbol + " side=" + side + " clOrdId=" + clOrdId);
            channel.writeAndFlush(new TextWebSocketFrame(createOrderJson));
        } catch (final Exception e) {
            LOGGER.error("HTX spot place order WS failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void handleAuthResponse(final String message) {
        final String ch = minExtract(message, "ch");
        if (!"auth".equals(ch)) return;
        final String codeStr = minExtract(message, "code");
        final int code = codeStr != null ? (int) parseLongSafe(codeStr) : -1;
        if (code == 200) {
            LOGGER.info("HTX ws/trade auth success");
            setAuthenticated(true);
        } else {
            final String errMsg = minExtract(message, "message");
            LOGGER.warn("HTX ws/trade auth failed: code=" + code + " message=" + errMsg);
        }
    }

    private void handlePing(final String message) {
        if (authSent.get() && !authenticated.get()) {
            setAuthenticated(true);
            LOGGER.debug("HTX ws/trade: no auth response; treating first ping as connection accepted");
        }
        final String dataStr = extractJsonValue(message, "data");
        if (dataStr == null) {
            LOGGER.debug("HTX SpotTrade WS: ping without data, skipping pong");
            return;
        }
        final String ts = minExtract(dataStr, "ts");
        if (ts == null || channel == null || !channel.isActive()) {
            if (ts == null) LOGGER.debug("HTX SpotTrade WS: ping data has no ts");
            return;
        }
        channel.writeAndFlush(new TextWebSocketFrame("{\"action\":\"pong\",\"data\":{\"ts\":" + ts + "}}"));
        LOGGER.debug("HTX SpotTrade WS: pong sent (ts=" + ts + ")");
    }

    private void handleCreateOrderResponse(final String message) {
        final String status = minExtract(message, "status");
        final String cid = minExtract(message, "cid");
        if (cid == null || cid.isEmpty()) return;

        if ("ok".equalsIgnoreCase(status)) {
            final String orderId = minExtract(message, "data");
            if (orderId == null || orderId.isEmpty()) {
                LOGGER.warn("HTX SpotTrade WS: create-order ok but no data (orderId), cid=" + cid);
                return;
            }
            LOGGER.info("HTX SpotTrade WS: order placed orderId=" + orderId + " clOrdId=" + cid);
            final Order order = subscription.getOrder(cid);
            if (order == null) {
                LOGGER.warn("HTX SpotTrade WS: order not in cache for clOrdId=" + cid + ", orderId=" + orderId);
                return;
            }
            order.setOcoClOrdId(orderId);
            final ExecutionReportMessage executionMessage = getOrCreateExecutionReport(order);
            executionMessage.setClOrdId(cid);
            executionMessage.setOrdStatus(OrdStatus.NEW);
            executionMessage.setExecType(ExecType.NEW);
            subscription.updateExecutionReport(executionMessage);
            subscription.updateOrder(cid, order);
        } else {
            final String errMsg = minExtract(message, "err-msg");
            final String errorSection = errMsg != null && !errMsg.isEmpty() ? errMsg : message;
            LOGGER.error("HTX SpotTrade WS: order rejected clOrdId=" + cid + " err=" + errorSection);
            final Order order = subscription.getOrder(cid);
            if (order == null) {
                LOGGER.warn("HTX SpotTrade WS: rejected order not in cache clOrdId=" + cid);
                return;
            }
            final ExecutionReportMessage executionMessage = getOrCreateExecutionReport(order);
            executionMessage.setClOrdId(cid);
            executionMessage.setError(errorSection);
            executionMessage.setOrdStatus(OrdStatus.REJECTED);
            executionMessage.setExecType(ExecType.REJECTED);
            subscription.updateExecutionReport(executionMessage);
            subscription.updateOrder(cid, "REJECTED");
        }
    }

    private String computeSignature(final String method, final String path,
                                    final Map<String, String> params) throws Exception {
        final StringBuilder q = new StringBuilder();
        for (final Map.Entry<String, String> e : params.entrySet()) {
            if (!q.isEmpty()) q.append("&");
            q.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            q.append("=");
            q.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        final String signatureBase = method + "\n" + HOST + "\n" + path + "\n" + q;
        final String hmacHex = HMAC.hmacSha256(signatureBase, subscription.getApiSecret());
        final byte[] hmacBytes = hexStringToByteArray(hmacHex);
        return Base64.getEncoder().encodeToString(hmacBytes);
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
}
