package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketClientHandler;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketListenerInterface;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
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
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;

public final class BitgetTradeListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(BitgetTradeListener.class);
    private static final String WS_URL = "wss://wspap.bitget.com/v3/ws/private";
    private static final long PING_INTERVAL_SECONDS = 25;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;

    private final String apiKey;
    private final String secretKey;
    private final String passphrase;
    private final ExchangeSubscription subscription;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();

    public BitgetTradeListener(final String apiKey, final String secretKey, final String passphrase,
                               final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.passphrase = passphrase;
        this.subscription = subscription;
    }

    public boolean getConnected() {
        return connected.get();
    }

    public boolean getAuthenticated() {
        return authenticated.get();
    }

    public void setAuthenticated(final boolean authenticated) {
        this.authenticated.set(authenticated);
    }

    public void setLastPongReceived(final long lastPongReceived) {
        this.lastPongReceived = lastPongReceived;
    }

    @Override
    public void onBinaryMessage(byte[] bytes) {
        // Not used for Bitget
    }

    public void connect() throws Exception {
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker =
                WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "BITGET-TRADE-LISTENER");

        final Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                final ChannelPipeline p = ch.pipeline();
                p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                p.addLast(new HttpClientCodec());
                p.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                p.addLast(handler);
            }
        });

        final Channel ch = b.connect(host, port).sync().channel();
        this.channel = ch;
        handler.handshakeFuture().sync();

        LOGGER.info("Bitget Trade WebSocket connected");
        setConnected(true);
        setAuthenticated(false);
        authenticate();
    }

    public void disconnect() {
        stopPing();
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

    public void setConnected(final boolean connected) {
        this.connected.set(connected);
    }

    private void authenticate() throws Exception {
        // Stop any existing ping task before authentication
        stopPing();

        final String timestamp = String.valueOf(Instant.now().toEpochMilli());
        final String preHashString = timestamp + "GET" + "/user/verify";
        final String hmacHex = HMAC.hmacSha256(preHashString, secretKey);
        final byte[] hmacBytes = hexStringToByteArray(hmacHex);
        final String sign = Base64.getEncoder().encodeToString(hmacBytes);

        final String loginMsg = "{\"op\":\"login\",\"args\":[{" +
                "\"apiKey\":\"" + apiKey + "\"," +
                "\"passphrase\":\"" + passphrase + "\"," +
                "\"timestamp\":\"" + timestamp + "\"," +
                "\"sign\":\"" + sign + "\"" +
                "}]}";

        LOGGER.info("Sending spot authentication: " + loginMsg);
        channel.writeAndFlush(new TextWebSocketFrame(loginMsg));
    }

    public void onMessage(final String message) {
        LOGGER.debug("Bitget Trade Received message: " + message);

        // Check for login response
        if (message.contains("\"event\":\"login\"")) {
            if (message.contains("\"code\":0")) {
                LOGGER.info("Authentication successful!");
                setAuthenticated(true);
                // Only schedule ping after successful authentication
                schedulePeriodicPing();
            } else {
                LOGGER.error("Authentication failed: " + message);
                setAuthenticated(false);
            }
        }
        // Handle trade responses (order placement)
        else if (message.contains("\"event\":\"trade\"") && message.contains("\"topic\":\"place-order\"")) {
            handleOrderResponse(message);
        }
        // Handle error responses (order rejection)
        else if (message.contains("\"event\":\"error\"")) {
            handleOrderResponse(message);
        }
    }

    private void handleOrderResponse(final String message) {
        final String code = minExtract(message, "code");
        String clientOrderId = null;

        // For error events, we need to extract the ID to match with client order
        if (message.contains("\"event\":\"error\"")) {
            clientOrderId = minExtract(message, "id");
        } else {
            clientOrderId = minExtract(message, "clientOid");
        }

        if (clientOrderId == null) return;

        if ("0".equals(code)) {
            final Order order = subscription.getOrder(clientOrderId);
            if (order != null) {
                ExecutionReportMessage executionReportMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionReportMessage == null) {
                    executionReportMessage =
                            ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, "", 0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0L);
                }
                subscription.updateExecutionReport(executionReportMessage);
            }
            LOGGER.info("Order placed successfully for clientOrderId: " + clientOrderId + " orderId: " + order.getOrderId());
        } else {
            final String msg = minExtract(message, "msg");
            LOGGER.info("Order rejected for clientOrderId: " + clientOrderId + " reason: " + msg);

            final Order order = subscription.getOrder(clientOrderId);
            if (order != null) {
                ExecutionReportMessage executionReportMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionReportMessage == null) {
                    executionReportMessage =
                            ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, "", 0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, null, 0L);
                }
                executionReportMessage.setClOrdId(clientOrderId);
                executionReportMessage.setError(msg);
                executionReportMessage.setExecType(ExecType.REJECTED);
                subscription.updateExecutionReport(executionReportMessage);
                subscription.updateOrder(clientOrderId, "REJECTED");


            }
        }
    }

    public void placeSpotOrder(final String symbol, final String side, final String orderType,
                               final String timeInForce, final String qty, final String price,
                               final String clientOrderId) {
        final StringBuilder orderBuilder = new StringBuilder();

        orderBuilder.append("{");
        orderBuilder.append("\"op\":\"trade\",");
        orderBuilder.append("\"id\":\"").append(clientOrderId).append("\",");
        orderBuilder.append("\"category\":\"spot\",");
        orderBuilder.append("\"topic\":\"place-order\",");
        orderBuilder.append("\"args\":[{");
        orderBuilder.append("\"orderType\":\"").append(orderType.toLowerCase()).append("\",");
        orderBuilder.append("\"side\":\"").append(side.toLowerCase()).append("\",");
        orderBuilder.append("\"symbol\":\"").append(symbol).append("\",");
        orderBuilder.append("\"qty\":\"").append(qty).append("\"");
        orderBuilder.append(",\"price\":\"").append(price).append("\"");

        if (timeInForce != null) {
            orderBuilder.append(",\"timeInForce\":\"").append(timeInForce.toLowerCase()).append("\"");
        }

        if (clientOrderId != null) {
            orderBuilder.append(",\"clientOid\":\"").append(clientOrderId).append("\"");
        }

        orderBuilder.append("}]}");

        final String orderMsg = orderBuilder.toString();
        LOGGER.info("Sending spot order via WebSocket: " + orderMsg);
        channel.writeAndFlush(new TextWebSocketFrame(orderMsg));
    }

    public void placeFuturesOrder(final String symbol, final String side, final String orderType,
                                  final String timeInForce, final String qty, final String price,
                                  final String clientOrderId) {
        final String id = String.valueOf(System.currentTimeMillis());
        final StringBuilder orderBuilder = new StringBuilder();

        orderBuilder.append("{");
        orderBuilder.append("\"op\":\"trade\",");
        orderBuilder.append("\"id\":\"").append(id).append("\",");
        orderBuilder.append("\"topic\":\"place-order\",");
        if (symbol.toLowerCase().contains("usdt")) {
            orderBuilder.append("\"category\":\"usdt-futures\",");
        } else {
            orderBuilder.append("\"category\":\"usdc-futures\",");
        }
        orderBuilder.append("\"args\":[{");
        orderBuilder.append("\"orderType\":\"").append(orderType.toLowerCase()).append("\",");
        orderBuilder.append("\"side\":\"").append(side.toLowerCase()).append("\",");
        orderBuilder.append("\"symbol\":\"").append(symbol).append("\",");
        orderBuilder.append("\"qty\":\"").append(qty).append("\"");
        if (price != null && !"market".equalsIgnoreCase(orderType)) {
            orderBuilder.append(",\"price\":\"").append(price).append("\"");
        }

        if (timeInForce != null) {
            orderBuilder.append(",\"timeInForce\":\"").append(timeInForce.toLowerCase()).append("\"");
        }

        if (clientOrderId != null) {
            orderBuilder.append(",\"clientOid\":\"").append(clientOrderId).append("\"");
        }

        orderBuilder.append("}]}");

        final String orderMsg = orderBuilder.toString();
        LOGGER.info("Sending futures order via WebSocket: " + orderMsg);
        channel.writeAndFlush(new TextWebSocketFrame(orderMsg));
    }
    private byte[] hexStringToByteArray(final String hex) {
        final int len = hex.length();
        final byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private void schedulePeriodicPing() {
        // Ensure we stop any existing ping task first
        stopPing();

        // Only schedule if we're connected and authenticated
        if (!channel.isActive() || !authenticated.get()) {
            return;
        }

        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive() && authenticated.get()) {
                // Send ping to keep connection alive
                channel.writeAndFlush(new TextWebSocketFrame("ping"));

                LOGGER.debug("Sending Bitget TradeListener ping");
            } else {
                // Stop ping if connection is lost
                stopPing();
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopPing() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
    }

    public void reconnect() {
        LOGGER.info("Bitget Trade WebSocket reconnecting...");
        // Stop ping immediately to prevent multiple tasks
        stopPing();
        setConnected(false);
        setAuthenticated(false);
        if (channel != null && channel.isOpen()) {
            channel.close();
        }

        // Use the existing event loop if available, otherwise use a new thread
        if (channel != null && channel.eventLoop() != null && !channel.eventLoop().isShuttingDown()) {
            channel.eventLoop().schedule(() -> {
                try {
                    connect();
                } catch (Exception e) {
                    LOGGER.error("Error during reconnect", e);
                    // Schedule another reconnect attempt
                    reconnect();
                }
            }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
        } else {
            // Fallback to new thread if event loop is not available
            new Thread(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY_SEC * 1000);
                    connect();
                } catch (Exception e) {
                    LOGGER.error("Error during reconnect", e);
                    reconnect();
                }
            }).start();
        }
    }

}