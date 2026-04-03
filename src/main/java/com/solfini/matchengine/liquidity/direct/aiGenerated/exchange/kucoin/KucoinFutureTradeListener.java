package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kucoin;

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
import io.netty.channel.*;
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

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.extractJsonValue;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;

public final class KucoinFutureTradeListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(KucoinFutureTradeListener.class);
    private static final long DEFAULT_PING_INTERVAL_MS = 18_000;
    private static final long DEFAULT_PING_TIMEOUT_MS = 10_000;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;
    private static final String WS_HOST = "wsapi.kucoin.com";
    private static final String WS_PATH = "/v1/private";

    private final ExchangeSubscription subscription;
    private final KucoinRestClient restClient;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();
    private long pingIntervalMs = DEFAULT_PING_INTERVAL_MS;
    private long pingTimeoutMs = DEFAULT_PING_TIMEOUT_MS;

    public KucoinFutureTradeListener(
            final ExchangeSubscription subscription,
            final KucoinRestClient restClient) {
        this.subscription = subscription;
        this.restClient = restClient;
    }

    /**
     * KuCoin futures contracts append M: BTCUSDT → BTCUSDTM
     */
    private static String toFuturesSymbol(final String symbol) {
        if (symbol == null) return null;
        if (symbol.contains("-") || symbol.endsWith("M")) return symbol;
        return symbol + "M";
    }

    private static String hmacSha256Base64(final String message, final String secret) {
        try {
            final String hex = HMAC.hmacSha256(message, secret);
            final byte[] bytes = hexToBytes(hex);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (final Exception e) {
            throw new RuntimeException("KuCoin HMAC-SHA256 failed", e);
        }
    }

    private static byte[] hexToBytes(final String hex) {
        final int len = hex.length();
        final byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Builds wss://wsapi.kucoin.com/v1/private?apikey=...&timestamp=...&sign=...&passphrase=...
     * sign = base64(HMAC-SHA256(apiKey + timestamp, apiSecret))
     */
    private String buildAuthWsUrl() {
        try {
            final String apiKey = restClient.getApiKey();
            final String timestamp = String.valueOf(restClient.syncedTimestamp());
            final String sign = hmacSha256Base64(apiKey + timestamp, restClient.getApiSecret());
            final String passphrase = restClient.getEncodedPassphrase();

            final StringBuilder url = new StringBuilder("wss://").append(WS_HOST).append(WS_PATH)
                    .append("?apikey=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8))
                    .append("&timestamp=").append(timestamp)
                    .append("&sign=").append(URLEncoder.encode(sign, StandardCharsets.UTF_8))
                    .append("&passphrase=").append(URLEncoder.encode(passphrase, StandardCharsets.UTF_8));

            final String brokerId = subscription.getBrokerId();
            final String brokerKey = subscription.getBrokerKey();
            if (brokerId != null && !brokerId.isBlank() && brokerKey != null && !brokerKey.isBlank()) {
                final String partnerSign = hmacSha256Base64(timestamp + brokerId + apiKey, brokerKey);
                url.append("&partner=").append(URLEncoder.encode(brokerId, StandardCharsets.UTF_8))
                   .append("&partner_sign=").append(URLEncoder.encode(partnerSign, StandardCharsets.UTF_8));
            }

            return url.toString();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to build KuCoin futures WS auth URL", e);
        }
    }

    @Override
    public void connect() throws Exception {
        reconnecting.set(false);
        LOGGER.info("KuCoin Future Trade connecting to " + WS_HOST + WS_PATH);

        final String wsUrl = buildAuthWsUrl();
        final URI uri = new URI(wsUrl);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker =
                WebSocketClientHandshakerFactory.newHandshaker(
                        uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler =
                new NettyWebSocketClientHandler(handshaker, this, "KUCOIN-FUTURE-TRADE");

        final Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                        p.addLast(new HttpClientCodec());
                        p.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                        p.addLast(handler);
                    }
                });

        final String outboundIp = Context.getOutboundIp();
        if (outboundIp != null && !outboundIp.isEmpty()) {
            bootstrap.localAddress(new InetSocketAddress(outboundIp, 0));
        }

        channel = bootstrap.connect(host, port).sync().channel();
        handler.handshakeFuture().sync();

        connected.set(true);
        authenticated.set(false);
        lastPongReceived = System.currentTimeMillis();

        LOGGER.info("KuCoin Future Trade WebSocket connected — awaiting session challenge");
    }

    @Override
    public void disconnect() {
        stopPing();
        connected.set(false);
        authenticated.set(false);
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        LOGGER.info("KuCoin Future Trade WebSocket disconnected");
    }

    @Override
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    @Override
    public void setConnected(final boolean connected) {
        this.connected.set(connected);
    }

    /* =========================================================
       RECONNECT
       ========================================================= */

    @Override
    public void setAuthenticated(final boolean authenticated) {
        this.authenticated.set(authenticated);
    }

    /* =========================================================
       PING / PONG
       ========================================================= */

    @Override
    public void setLastPongReceived(final long ts) {
        this.lastPongReceived = ts;
    }

    @Override
    public void onBinaryMessage(final byte[] bytes) {
    }

    /* =========================================================
       MESSAGE HANDLING
       ========================================================= */

    @Override
    public void reconnect() {
        if (!reconnecting.compareAndSet(false, true)) {
            return; // already reconnecting, skip duplicate calls
        }
        stopPing();
        connected.set(false);
        authenticated.set(false);
        LOGGER.info("KuCoin Future Trade scheduling reconnect in " + RECONNECT_DELAY_SEC + "s...");

        final Runnable task = () -> {
            LOGGER.info("KuCoin Future Trade attempting reconnection...");
            try {
                connect();
            } catch (final Exception e) {
                LOGGER.error("KuCoin Future Trade reconnect failed: " + e.getMessage(), e);
                reconnecting.set(false);
                reconnect();
            }
        };

        if (channel != null && channel.isOpen()) {
            channel.close().addListener((ChannelFutureListener) f ->
                    group.schedule(task, RECONNECT_DELAY_SEC, TimeUnit.SECONDS));
        } else {
            group.schedule(task, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
        }
    }

    /* =========================================================
       ORDER RESPONSE
       ========================================================= */

    private void startPing() {
        stopPing();
        if (channel == null) return;
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (!isConnected()) return;
            if (System.currentTimeMillis() - lastPongReceived > pingIntervalMs + pingTimeoutMs) {
                LOGGER.warn("KuCoin Future Trade pong timeout (no pong for >"
                        + (pingIntervalMs + pingTimeoutMs) + "ms), reconnecting");
                reconnect();
                return;
            }
            final long ts = System.currentTimeMillis();
            channel.writeAndFlush(new TextWebSocketFrame(
                    "{\"id\":\"" + ts + "\",\"op\":\"ping\"}"));
            LOGGER.debug("KuCoin Future Trade ping sent id=" + ts);
        }, pingIntervalMs, pingIntervalMs, TimeUnit.MILLISECONDS);
    }

    /* =========================================================
       PLACE ORDER
       ========================================================= */

    private void stopPing() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
    }

    @Override
    public void onMessage(final String message) {
        if (message == null) return;
        LOGGER.debug("KuCoin Future Trade received: " + message);

        final String op = minExtract(message, "op");

        // ── op-keyed messages (pong + order responses) ────────
        if (op != null) {
            switch (op) {
                case "pong":
                    lastPongReceived = System.currentTimeMillis();
                    LOGGER.debug("KuCoin Future Trade pong received (op format)");
                    return;

                case "futures.order":
                    // {"id":"...","op":"futures.order","code":"200000","data":{...}}
                    // {"id":"...","op":"futures.order","code":"200004","msg":"Balance insufficient!"}
                    final String orderCode = minExtract(message, "code");
                    if (orderCode != null) {
                        handleOrderResponse(message, orderCode);
                    } else {
                        LOGGER.warn("KuCoin Future Trade futures.order response missing code: " + message);
                    }
                    return;

                default:
                    LOGGER.debug("KuCoin Future Trade ignoring op=" + op);
                    return;
            }
        }

        // ── type-keyed pong (KuCoin standard {"type":"pong"} format) ────
        final String type = minExtract(message, "type");
        if ("pong".equals(type)) {
            lastPongReceived = System.currentTimeMillis();
            LOGGER.debug("KuCoin Future Trade pong received (type format)");
            return;
        }

        // ── No op/type field — welcome / challenge / auth errors ───
        // Post-auth only op-keyed messages arrive; skip all op-less parsing during normal operation
        if (authenticated.get()) {
            LOGGER.debug("KuCoin Future Trade unexpected message post-auth: " + message);
            return;
        }

        final String data = minExtract(message, "data");

        // Welcome: {"sessionId":"...","data":"welcome","pingInterval":...,"pingTimeout":...}
        if ("welcome".equals(data)) {
            authenticated.set(true);
            final String intervalStr = minExtract(message, "pingInterval");
            final String timeoutStr = minExtract(message, "pingTimeout");
            if (intervalStr != null) {
                try {
                    pingIntervalMs = Long.parseLong(intervalStr);
                } catch (final NumberFormatException ignored) {
                }
            }
            if (timeoutStr != null) {
                try {
                    pingTimeoutMs = Long.parseLong(timeoutStr);
                } catch (final NumberFormatException ignored) {
                }
            }
            lastPongReceived = System.currentTimeMillis();
            startPing();
            LOGGER.info("KuCoin Future Trade authenticated and ready"
                    + " — pingInterval=" + pingIntervalMs + "ms"
                    + " pingTimeout=" + pingTimeoutMs + "ms");
            return;
        }

        // Session challenge: {"timestamp":...,"sessionId":"..."}
        // Sign the raw challenge JSON with HMAC-SHA256(apiSecret) and
        // send the bare Base64 string (NOT wrapped in JSON)
        final String sessionId = minExtract(message, "sessionId");
        if (sessionId != null && !authenticated.get()) {
            final String sign = hmacSha256Base64(message, restClient.getApiSecret());
            channel.writeAndFlush(new TextWebSocketFrame(sign));
            LOGGER.info("KuCoin Future Trade session challenge received, auth response sent for sessionId=" + sessionId);
        }
    }

    /* =========================================================
       UTILITIES
       ========================================================= */

    /**
     * Handles futures.order WS response.
     * Success (code 200000): sets ExecType.NEW / OrdStatus.NEW, stores exchange orderId.
     * Any other code: sets ExecType.REJECTED / OrdStatus.REJECTED with msg as reason.
     */
    private void handleOrderResponse(final String message, final String code) {
        try {
            final String id = minExtract(message, "id");
            if (id == null) {
                LOGGER.warn("KuCoin Future Trade order response missing id field: " + message);
                return;
            }

            final Order order = subscription.getOrder(id);
            if (order == null) {
                LOGGER.warn("KuCoin Future Trade order not found in cache for id=" + id);
                return;
            }

            ExecutionReportMessage er = subscription.getExecutionReport(order.getClOrdId());
            if (er == null) {
                er = ExecutionReportMessage.createExternalExecutionReport(
                        order.getOrderId(), order.getUser(), 0, order.getSymbol(),
                        0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);
            }
            er.setClOrdId(id);

            if ("200000".equals(code)) {
                final String dataStr = extractJsonValue(message, "data");
                final String orderId = dataStr != null ? minExtract(dataStr, "orderId") : null;

                er.setExecType(ExecType.NEW);
                er.setOrdStatus(OrdStatus.NEW);

                subscription.updateExecutionReport(er);
                LOGGER.info("KuCoin Future Trade order accepted:"
                        + " id=" + id
                        + " orderId=" + orderId
                        + " symbol=" + order.getSymbol()
                        + " side=" + order.getSide());
            } else {
                final String reason = minExtract(message, "msg");
                er.setError(reason != null ? reason : code);
                er.setExecType(ExecType.REJECTED);
                er.setOrdStatus(OrdStatus.REJECTED);
                subscription.updateExecutionReport(er);
                order.setRejected(true);
                subscription.updateOrder(id, order);
                LOGGER.warn("KuCoin Future Trade order rejected:"
                        + " id=" + id
                        + " code=" + code
                        + " reason=" + reason
                        + " symbol=" + order.getSymbol()
                        + " side=" + order.getSide());
            }
        } catch (final Exception e) {
            LOGGER.error("KuCoin Future Trade error processing order response: " + e.getMessage(), e);
        }
    }

    public void placeOrder(final String symbol, final String side, final String type,
                           final String tif, final String qty, final String price,
                           final String clOrdId) {
        placeOrder(symbol, side, type, tif, qty, price, clOrdId, "1", false);
    }

    /**
     * Place futures order via WebSocket.
     * tradeType is FUTURES, op is futures.order.
     * symbol is normalised to KuCoin futures format (e.g. BTCUSDT → BTCUSDTM).
     */
    public void placeOrder(final String symbol, final String side, final String type,
                           final String tif, final String qty, final String price,
                           final String clOrdId, final String leverage, final boolean reduceOnly) {
        if (!isConnected() || !authenticated.get()) {
            LOGGER.warn("KuCoin Future Trade placeOrder skipped — not connected/authenticated"
                    + " (connected=" + isConnected() + " authenticated=" + authenticated.get() + ")");
            return;
        }

        final String sym = toFuturesSymbol(symbol);
        final String sideNorm = side.toLowerCase();
        final String orderType = type != null ? type.toLowerCase() : "limit";

        final StringBuilder args = new StringBuilder()
                .append("{\"clientOid\":\"").append(clOrdId).append("\"")
                .append(",\"symbol\":\"").append(sym).append("\"")
                .append(",\"side\":\"").append(sideNorm).append("\"")
                .append(",\"type\":\"").append(orderType).append("\"")
                .append(",\"leverage\":").append(leverage)
                .append(",\"marginMode\":\"CROSS\"")
                .append(",\"size\":\"").append(qty).append("\"")
                .append(",\"timeInForce\":\"").append(tif).append("\"")
                .append(",\"reduceOnly\":").append(reduceOnly);
        if ("limit".equals(orderType)) {
            args.append(",\"price\":\"").append(price).append("\"");
        }
        args.append("}");

        final String json = "{\"id\":\"" + clOrdId + "\",\"op\":\"futures.order\",\"args\":" + args + "}";

        LOGGER.info("KuCoin Future Trade placing order:"
                + " symbol=" + sym
                + " side=" + sideNorm
                + " type=" + orderType
                + " qty=" + qty
                + " price=" + price
                + " tif=" + tif
                + " leverage=" + leverage
                + " reduceOnly=" + reduceOnly
                + " clOrdId=" + clOrdId);
        LOGGER.debug("KuCoin Future Trade order JSON: " + json);
        channel.writeAndFlush(new TextWebSocketFrame(json));
    }
}
