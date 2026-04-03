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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
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
import java.util.zip.GZIPInputStream;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public final class HtxFutureTradeListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(HtxFutureTradeListener.class);
    private static final String WS_URL = "wss://api.hbdm.com/linear-swap-trade";
    private static final String HANDLER_NAME = "HTX-FUTURE-TRADE-LISTENER";
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;
    private static final String HOST_FUTURES = "api.hbdm.com";
    private static final DateTimeFormatter HUOBI_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private final ExchangeSubscription subscription;
    private EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);

    private volatile Channel channel;

    public HtxFutureTradeListener(final HtxRestClient restClient, final ExchangeSubscription subscription) {
        this.subscription = subscription;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public void onBinaryMessage(final byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        final String text = decodeBinaryPayload(bytes);
        if (text != null && !text.isEmpty()) {
            onMessage(text);
        } else {
            LOGGER.warn("HTX Future Trade WS binary decode failed");
        }
    }

    @Override
    public void connect() throws Exception {
        if (group.isShuttingDown() || group.isTerminated()) {
            group = new NioEventLoopGroup();
        }
        final URI uri = new URI(WS_URL);
        final SslContext sslCtx = SslContextBuilder.forClient().build();
        final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory
                .newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());
        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, HANDLER_NAME);

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
                p.addLast(sslCtx.newHandler(ch.alloc(), HOST_FUTURES, SSL_PORT));
                p.addLast(new HttpClientCodec());
                p.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                p.addLast(handler);
            }
        });

        channel = b.connect(HOST_FUTURES, SSL_PORT).sync().channel();
        handler.handshakeFuture().sync();
        LOGGER.info("HTX Future Trade WS connected");
        authenticate();
    }

    private void authenticate() {
        try {
            final String timestamp = HUOBI_TIMESTAMP_FORMAT.format(Instant.now());
            final Map<String, String> authParams = new TreeMap<>();
            authParams.put("AccessKeyId", subscription.getApiKey());
            authParams.put("SignatureMethod", "HmacSHA256");
            authParams.put("SignatureVersion", "2");
            authParams.put("Timestamp", timestamp);
            final String signatureB64 = computeSignature("GET", "/linear-swap-trade", authParams);
            final String cid = "auth-" + System.currentTimeMillis();
            final StringBuilder sb = new StringBuilder();
            sb.append("{\"op\":\"auth\",\"type\":\"api\",\"cid\":\"").append(escapeJson(cid)).append("\"");
            sb.append(",\"AccessKeyId\":\"").append(escapeJson(subscription.getApiKey())).append("\"");
            sb.append(",\"SignatureMethod\":\"HmacSHA256\"");
            sb.append(",\"SignatureVersion\":\"2\"");
            sb.append(",\"Timestamp\":\"").append(escapeJson(timestamp)).append("\"");
            sb.append(",\"Signature\":\"").append(escapeJson(signatureB64)).append("\"");
            sb.append("}");
            LOGGER.info("HTX linear-swap-trade WS auth (root-level) sent");
            final Channel ch = channel;
            if (ch != null && ch.isActive()) {
                ch.writeAndFlush(new TextWebSocketFrame(sb.toString()));
            }
        } catch (final Exception e) {
            LOGGER.error("HTX Future Trade WS auth failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        setConnected(false);
        setAuthenticated(false);
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
    public void setLastPongReceived(final long lastPongReceived) {
    }

    @Override
    public void reconnect() {
        setConnected(false);
        setAuthenticated(false);
        final Channel ch = channel;
        if (ch != null && ch.isOpen()) {
            ch.close();
        }
        if (ch != null && ch.eventLoop() != null && !ch.eventLoop().isShuttingDown()) {
            ch.eventLoop().schedule(() -> {
                try {
                    LOGGER.info("HTX Future Trade WS reconnecting...");
                    connect();
                } catch (final Exception e) {
                    LOGGER.error("HTX Future Trade WS reconnect error", e);
                    reconnect();
                }
            }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onMessage(final String message) {
        if (message == null || message.isEmpty()) return;
        final String op = minExtract(message, "op");
        if ("ping".equals(op)) {
            handlePing(message);
            return;
        }
        final String action = minExtract(message, "action");
        final String ch = minExtract(message, "ch");
        if ("auth".equals(op) || ("req".equals(action) && "auth".equals(ch))) {
            handleAuthResponse(message);
            return;
        }
        if ("create_order".equals(op)) {
            handleCreateOrderResponse(message);
            return;
        }
        LOGGER.debug("HTX Future Trade WS unhandled op=" + op);
    }

    public boolean placeOrder(final String contractCode, final String direction, final String offset,
                              final String orderPriceType, final String volume, final String price,
                              final String clOrdId, final int leverRate) {
        final Channel ch = channel;
        if (ch == null || !ch.isActive() || !isConnected()) {
            LOGGER.warn("HTX Future Trade WS not ready (channel or auth); use REST");
            return false;
        }
        if (contractCode == null || contractCode.isEmpty() || clOrdId == null || clOrdId.isEmpty()) {
            LOGGER.warn("HTX Future Trade WS placeOrder: contractCode/clOrdId required");
            return false;
        }
        try {
            final String clientOrderId = clOrdId.matches("\\d+") ? clOrdId : null;
            final String dataJson = buildSwapOrderDataJson(
                    contractCode, direction, offset, price, leverRate, volume, orderPriceType,
                    clientOrderId, null, null, null, null, null, null);
            final String msg = "{\"op\":\"create_order\",\"cid\":\"" + escapeJson(clOrdId) + "\",\"data\":" + dataJson + "}";
            ch.writeAndFlush(new TextWebSocketFrame(msg));
            LOGGER.info("HTX Future Trade WS placeOrder contract=" + contractCode + " side=" + direction + " offset=" + offset + " clOrdId=" + clOrdId);
            return true;
        } catch (final Exception e) {
            LOGGER.error("HTX Future Trade WS placeOrder failed: " + e.getMessage(), e);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void handleAuthResponse(final String message) {
        final String errCode = minExtract(message, "err-code");
        final String errCodeAlt = minExtract(message, "err_code");
        final String status = minExtract(message, "status");
        final String codeStr = minExtract(message, "code");
        final int code = codeStr != null ? (int) parseLongSafe(codeStr) : -1;

        final boolean success = "ok".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)
                || code == 200
                || "0".equals(errCode) || "0".equals(errCodeAlt)
                || (errCode == null && errCodeAlt == null);

        if (success) {
            LOGGER.info("HTX Future Trade WS auth ok");
            setAuthenticated(true);
        } else {
            String errMsg = minExtract(message, "err-msg");
            if (errMsg == null) errMsg = minExtract(message, "err_msg");
            LOGGER.warn("HTX Future Trade WS auth failed: err-code=" + errCode + " err_code=" + errCodeAlt + " err-msg=" + errMsg);
        }
    }

    private void handlePing(final String message) {
        final Channel ch = channel;
        if (ch == null || !ch.isActive()) return;
        final String dataStr = extractJsonValue(message, "data");
        String ts = dataStr != null ? minExtract(dataStr, "ts") : null;
        if (ts == null) ts = minExtract(message, "ts");
        if (ts == null || ts.isEmpty()) ts = String.valueOf(System.currentTimeMillis());
        ch.writeAndFlush(new TextWebSocketFrame("{\"op\":\"pong\",\"ts\":" + ts + "}"));
        LOGGER.debug("HTX Future Trade WS pong ts=" + ts);
    }

    private void handleCreateOrderResponse(final String message) {
        final String status = minExtract(message, "status");
        final String cid = minExtract(message, "cid");
        if (cid == null || cid.isEmpty()) return;

        final Order order = subscription.getOrder(cid);
        if (order == null) {
            LOGGER.debug("HTX Future Trade WS order response for unknown cid=" + cid);
            return;
        }

        if ("ok".equalsIgnoreCase(status)) {
            final String dataStr = extractJsonValue(message, "data");
            String orderIdStr = null;
            if (dataStr != null && !dataStr.isEmpty()) {
                orderIdStr = minExtract(dataStr, "order_id_str");
                if (orderIdStr == null || orderIdStr.isEmpty()) orderIdStr = minExtract(dataStr, "order_id");
            }
            if (orderIdStr != null && !orderIdStr.isEmpty()) order.setOcoClOrdId(orderIdStr);
            LOGGER.info("HTX Future Trade WS order placed orderId=" + orderIdStr + " clOrdId=" + cid);
            final ExecutionReportMessage exec = getOrCreateExecutionReport(order);
            exec.setClOrdId(cid);
            exec.setOrdStatus(OrdStatus.NEW);
            exec.setExecType(ExecType.NEW);
            subscription.updateExecutionReport(exec);
            subscription.updateOrder(cid, "NEW");
        } else {
            String errMsg = minExtract(message, "err-msg");
            if (errMsg == null || errMsg.isEmpty()) errMsg = minExtract(message, "err_msg");
            if (errMsg == null || errMsg.isEmpty()) errMsg = message;
            LOGGER.error("HTX Future Trade WS order rejected clOrdId=" + cid + " err=" + errMsg);
            final ExecutionReportMessage exec = getOrCreateExecutionReport(order);
            exec.setClOrdId(cid);
            exec.setError(errMsg);
            exec.setOrdStatus(OrdStatus.REJECTED);
            exec.setExecType(ExecType.REJECTED);
            subscription.updateExecutionReport(exec);
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
        final String signatureBase = method + "\n" + HOST_FUTURES + "\n" + path + "\n" + q;
        final String hmacHex = HMAC.hmacSha256(signatureBase, subscription.getApiSecret());
        final byte[] hmacBytes = hexStringToByteArray(hmacHex);
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    private static String buildSwapOrderDataJson(final String contractCode, final String direction, final String offset,
                                                 final String price, final int leverRate, final String volume,
                                                 final String orderPriceType, final String clientOrderId,
                                                 final String tpTriggerPrice, final String tpOrderPrice, final String tpOrderPriceType,
                                                 final String slTriggerPrice, final String slOrderPrice, final String slOrderPriceType) {
        final String c = contractCode != null ? contractCode.toLowerCase(Locale.ROOT) : "btc-usdt";
        final String d = direction != null ? direction.toLowerCase(Locale.ROOT) : "buy";
        final String off = offset != null ? offset.toLowerCase(Locale.ROOT) : "open";
        final String p = price != null && !price.isEmpty() ? price : "0";
        final String v = volume != null && !volume.isEmpty() ? volume : "0";
        final String t = orderPriceType != null ? orderPriceType.toLowerCase(Locale.ROOT) : "limit";
        final int lev = leverRate <= 0 ? 10 : Math.min(125, Math.max(1, leverRate));
        final StringBuilder sb = new StringBuilder();
        sb.append("{\"contract_code\":\"").append(escapeJson(c)).append("\"");
        sb.append(",\"direction\":\"").append(escapeJson(d)).append("\"");
        sb.append(",\"offset\":\"").append(escapeJson(off)).append("\"");
        sb.append(",\"price\":\"").append(escapeJson(p)).append("\"");
        sb.append(",\"lever_rate\":").append(lev);
        sb.append(",\"volume\":").append(v);
        sb.append(",\"order_price_type\":\"").append(escapeJson(t)).append("\"");
        if (clientOrderId != null && !clientOrderId.isEmpty() && clientOrderId.matches("\\d+")) {
            sb.append(",\"client_order_id\":").append(clientOrderId);
        }
        sb.append("}");
        return sb.toString();
    }

    private ExecutionReportMessage getOrCreateExecutionReport(final Order order) {
        ExecutionReportMessage exec = subscription.getExecutionReport(order.getClOrdId());
        if (exec == null) {
            exec = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                    0, 0, 0, 0, order.getSide(), 0);
        }
        return exec;
    }

    private static String decodeBinaryPayload(final byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            if (bytes.length >= 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
                try (final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes));
                     final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    final byte[] buf = new byte[4096];
                    int n;
                    while ((n = gis.read(buf)) > 0) out.write(buf, 0, n);
                    return out.toString(StandardCharsets.UTF_8);
                }
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            return null;
        }
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
