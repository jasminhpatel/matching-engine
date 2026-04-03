package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.deribit;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseLongSafe;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketClientHandler;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketListenerInterface;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
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

public final class DeribitTradeListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(DeribitTradeListener.class);
    private static final String WS_URL = "wss://test.deribit.com/ws/api/v2"; //TODO update it with prod url
    private static final long PING_INTERVAL_SECONDS = 60;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;

    private final String clientId;
    private final String clientSecret;
    private final ExchangeSubscription subscription;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicLong requestId = new AtomicLong(1);
    private volatile long lastPongReceived = System.currentTimeMillis();
    private volatile String accessToken;

    public DeribitTradeListener(final String clientId, final String clientSecret,
                               final ExchangeSubscription subscription) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.subscription = subscription;
    }

    public boolean getConnected() {
        return connected.get();
    }

    public void setConnected(final boolean connected) {
        this.connected.set(connected);
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

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(final String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public void onBinaryMessage(final byte[] bytes) {
        // Not used for Deribit
    }

    public void connect() throws Exception {
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();

        LOGGER.info("Connecting to Deribit Trade WebSocket at: " + host + ":" + port);

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker =
                WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "DERIBIT-TRADE-LISTENER");

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

        LOGGER.info("Deribit Trade WebSocket connected successfully");
        setConnected(true);
        setAuthenticated(false);
        authenticate();
    }

    public void disconnect() {
        LOGGER.info("Disconnecting Deribit Trade WebSocket");
        stopPing();
        setConnected(false);
        setAuthenticated(false);
        setAccessToken(null);
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        group.shutdownGracefully();
    }

    @Override
    public boolean isConnected() {
        return connected.get() && authenticated.get() && accessToken != null;
    }

    private void authenticate() throws Exception {
        stopPing();
        setAuthenticated(false);
        setAccessToken(null);

        final long timestamp = Instant.now().toEpochMilli();
        final String nonce = String.valueOf(timestamp);
        
        // Construct authentication request using client credentials grant type
        final String authMsg = "{\"jsonrpc\":\"2.0\",\"id\":" + requestId.getAndIncrement() + 
                ",\"method\":\"public/auth\",\"params\":{" +
                "\"grant_type\":\"client_credentials\"," +
                "\"client_id\":\"" + clientId + "\"," +
                "\"client_secret\":\"" + clientSecret + "\"," +
                "\"timestamp\":" + timestamp + "," +
                "\"nonce\":\"" + nonce + "\"" +
                "}}";

        LOGGER.info("Sending Deribit trade authentication request with timestamp: " + timestamp);
        channel.writeAndFlush(new TextWebSocketFrame(authMsg));
    }

    private void schedulePeriodicPing() {
        stopPing();
        
        if (!channel.isActive() || !authenticated.get() || accessToken == null) {
            LOGGER.warn("Cannot schedule ping - channel active: " + channel.isActive() + ", authenticated: " + authenticated.get() + ", token present: " + (accessToken != null));
            return;
        }
        
        // Schedule periodic ping to keep connection alive and detect disconnections
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive() && authenticated.get() && accessToken != null) {
                final String pingMsg = "{\"jsonrpc\":\"2.0\",\"id\":" + requestId.getAndIncrement() + 
                        ",\"method\":\"public/ping\"}";
                channel.writeAndFlush(new TextWebSocketFrame(pingMsg));
                LOGGER.debug("Sending Deribit TradeListener ping");
            } else {
                LOGGER.warn("Stopping ping - connection or authentication lost. Active: " + channel.isActive() + ", Authenticated: " + authenticated.get());
                stopPing();
                if (!authenticated.get() || accessToken == null) {
                    reconnect();
                }
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        LOGGER.info("Scheduled periodic ping every " + PING_INTERVAL_SECONDS + " seconds");
    }

    private void stopPing() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
    }

    public void reconnect() {
        LOGGER.info("Deribit Trade WebSocket reconnecting in " + RECONNECT_DELAY_SEC + " seconds...");
        stopPing();
        setConnected(false);
        setAuthenticated(false);
        setAccessToken(null);
        if (channel != null && channel.isOpen()) {
            channel.close();
        }

        if (channel != null && channel.eventLoop() != null && !channel.eventLoop().isShuttingDown()) {
            channel.eventLoop().schedule(() -> {
                try {
                    LOGGER.info("Attempting reconnection to Deribit Trade WebSocket");
                    connect();
                } catch (final Exception e) {
                    LOGGER.error("Error during reconnect: " + e.getMessage(), e);
                    reconnect();
                }
            }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
        } else {
            // Fallback reconnection mechanism using new thread
            new Thread(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY_SEC * 1000);
                    LOGGER.info("Attempting reconnection to Deribit Trade WebSocket via fallback thread");
                    connect();
                } catch (final Exception e) {
                    LOGGER.error("Error during reconnect via fallback thread: " + e.getMessage(), e);
                    reconnect();
                }
            }).start();
        }
    }

    public void onMessage(final String message) {
        LOGGER.debug("Deribit Trade Received message: " + message);

        // Route message to appropriate handler based on content
        if (message.contains("\"method\":\"public/auth\"") || (message.contains("\"result\"") && message.contains("access_token"))) {
            handleAuthResponse(message);
        } 
        // Handle successful order responses
        else if (message.contains("\"result\":{") && message.contains("\"order\":{")) {
            handleOrderResponse(message);
        }
        // Handle error responses
        else if (message.contains("\"error\":{")) {
            handleErrorResponse(message);
        }
    }

    /**
     * Handles successful order response from Deribit WebSocket
     */
    private void handleOrderResponse(final String message) {
        try {
            final String id = minExtract(message, "id");
            
            if (id == null) {
                LOGGER.warn("Deribit order response - Received id null. Message: " + message);
                return;
            }
            
            // Extract order_id from result.order
            final int resultStart = message.indexOf("\"result\":{");
            if (resultStart < 0) {
                LOGGER.warn("No result section found in order response message");
                return;
            }
            
            final String resultSection = message.substring(resultStart);
            final String orderId = minExtract(resultSection, "order_id");
            final String orderState = minExtract(resultSection, "order_state");
            
            LOGGER.info("Deribit order placed successfully - Label: " + id + ", OrderId: " + orderId + ", State: " + orderState);
            
            final Order order = subscription.getOrder(id);
            if (order != null && orderId != null) {
                // Remove prefix like "BTC_USDT-" to get numeric part
                final int dashIndex = orderId.indexOf('-');
                if (dashIndex >= 0) {
                    final String numericOrderId = orderId.substring(dashIndex + 1);
                    final long orderIdLong = parseLongSafe(numericOrderId);
                    order.setOrderId(orderIdLong);
                    LOGGER.debug("Set order ID to: " + orderIdLong + " from: " + orderId);
                } else {
                    final long orderIdLong = parseLongSafe(orderId);
                    order.setOrderId(orderIdLong);
                    LOGGER.debug("Set order ID to: " + orderIdLong);
                }
            } else {
                LOGGER.warn("Order not found for id: " + id + " or orderId is null");
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling Deribit order response: " + e.getMessage(), e);
        }
    }

    /**
     * Handles error response from Deribit WebSocket
     */
    private void handleErrorResponse(final String message) {
        final String id = minExtract(message, "id");
        final String code = minExtract(message, "code");
        
        // Extract nested error.data.reason
        String reason = null;
        final int errorDataStart = message.indexOf("\"data\":{");
        if (errorDataStart >= 0) {
            final String afterData = message.substring(errorDataStart);
            reason = minExtract(afterData, "reason");
        }
        
        // Fallback to error.message if reason not found
        if (reason == null) {
            reason = minExtract(message, "message");
        }
        
        LOGGER.error("Error response - ID: " + id + ", Code: " + code + ", Reason: " + reason);
        
        if (id != null) {
            final Order order = subscription.getOrder(id);
            if (order != null) {
                subscription.updateOrder(id, "REJECTED");
                
                ExecutionReportMessage executionReportMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionReportMessage == null) {
                    LOGGER.debug("Creating new execution report for rejected order: " + id);
                    executionReportMessage = 
                            ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0L);
                }
                executionReportMessage.setClOrdId(id);
                executionReportMessage.setError(reason != null ? reason : "Unknown error");
                executionReportMessage.setExecType(ExecType.REJECTED);
                
                subscription.updateExecutionReport(executionReportMessage);
                LOGGER.info("Updated execution report for rejected order: " + id + ", reason: " + reason);
            } else {
                LOGGER.warn("Order not found in subscription for error response id: " + id);
            }
        } else {
            LOGGER.warn("Error response missing id field");
        }
    }

    private void handleAuthResponse(final String message) {
        if (message.contains("\"result\"") && !message.contains("\"error\"")) {
            final String token = minExtract(message, "access_token");
            if (token != null && !token.trim().isEmpty()) {
                setAccessToken(token);
                setAuthenticated(true);
                LOGGER.info("Deribit Trade authentication successful! Token length: " + token.length());
                schedulePeriodicPing();
            } else {
                LOGGER.error("Authentication response missing or empty access token");
                setAuthenticated(false);
                setAccessToken(null);
                reconnect();
            }
        } else {
            LOGGER.error("Deribit Trade authentication failed: " + message);
            setAuthenticated(false);
            setAccessToken(null);
            reconnect();
        }
    }

    private void handlePingResponse(final String message) {
        if (message.contains("\"result\"") && !message.contains("\"error\"")) {
            final long currentTime = System.currentTimeMillis();
            setLastPongReceived(currentTime);
            LOGGER.debug("Received pong response at: " + currentTime);
        } else {
            LOGGER.warn("Ping failed with response: " + message);
            if (message.contains("\"error\"")) {
                final String error = minExtract(message, "error");
                final String errorCode = minExtract(error, "code");
                LOGGER.error("Ping error code: " + errorCode);
                if ("13009".equals(errorCode) || "13004".equals(errorCode)) {
                    LOGGER.error("Authentication expired or invalid, reconnecting...");
                    setAuthenticated(false);
                    setAccessToken(null);
                    reconnect();
                }
            }
        }
    }

    public void placeOrder(final String instrument, final String side, final String orderType,
                          final String qty, final String price, final String clientOrderId,
                          final String timeInForce) {
        if (!authenticated.get() || accessToken == null || accessToken.trim().isEmpty()) {
            LOGGER.error("Cannot place order: not authenticated or no access token available");
            return;
        }

        if (!channel.isActive()) {
            LOGGER.error("Cannot place order: channel is not active");
            return;
        }

        // Determine method based on side
        final String method = "buy".equalsIgnoreCase(side) ? "private/buy" : "private/sell";
        final StringBuilder orderBuilder = new StringBuilder();

        // Build order JSON request
        orderBuilder.append("{\"jsonrpc\":\"2.0\",\"id\":").append(clientOrderId);
        orderBuilder.append(",\"method\":\"").append(method).append("\",\"params\":{");
        orderBuilder.append("\"instrument_name\":\"").append(instrument).append("\"");
        orderBuilder.append(",\"amount\":").append(qty);
        orderBuilder.append(",\"type\":\"").append(orderType.toLowerCase()).append("\"");
        
        // Add price for non-market orders
        if (price != null && !"market".equalsIgnoreCase(orderType)) {
            orderBuilder.append(",\"price\":").append(price);
        }
        
        // Add time in force if specified
        if (timeInForce != null) {
            final String convertedTif = convertTimeInForce(timeInForce);
            orderBuilder.append(",\"time_in_force\":\"").append(convertedTif).append("\"");
        }
        
        // Add label (client order ID)
        if (clientOrderId != null) {
            orderBuilder.append(",\"label\":\"").append(clientOrderId).append("\"");
        }

        orderBuilder.append("}");
        orderBuilder.append(",\"auth\":{\"access_token\":\"").append(accessToken).append("\"}");
        orderBuilder.append("}");

        final String orderMsg = orderBuilder.toString();
        LOGGER.info("Sending Deribit order via WebSocket - instrument: " + instrument + ", side: " + side + ", type: " + orderType + ", qty: " + qty + ", price: " + price);
        channel.writeAndFlush(new TextWebSocketFrame(orderMsg));
    }

    public void placeSpotOrder(final String symbol, final String side, final String orderType,
                               final String timeInForce, final String qty, final String price,
                               final String clientOrderId) {
        LOGGER.debug("Placing spot order - symbol: " + symbol + ", side: " + side + ", clOrdId: " + clientOrderId);
        placeOrder(symbol, side, orderType, qty, price, clientOrderId, timeInForce);
    }

    public void placeFuturesOrder(final String symbol, final String side, final String orderType,
                                  final String timeInForce, final String qty, final String price,
                                  final String clientOrderId) {
        LOGGER.debug("Placing futures order - symbol: " + symbol + ", side: " + side + ", clOrdId: " + clientOrderId);
        placeOrder(symbol, side, orderType, qty, price, clientOrderId, timeInForce);
    }


    private String convertTimeInForce(final String timeInForce) {
        if (timeInForce == null) {
            LOGGER.debug("TimeInForce is null, returning null");
            return null;
        }

        final String upperTif = timeInForce.toUpperCase();
        LOGGER.debug("Converting TimeInForce: " + timeInForce);
        
        switch (upperTif) {
            case "GTC":
                return "good_til_cancelled";
            case "IOC":
                return "immediate_or_cancel";
            case "FOK":
                return "fill_or_kill";
            default:
                LOGGER.warn("Unknown TimeInForce: " + timeInForce + ", defaulting to GTC");
                return "good_til_cancelled";
        }
    }

}
