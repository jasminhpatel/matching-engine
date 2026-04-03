package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kraken;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketClientHandler;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketListenerInterface;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
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
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.net.URI;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class KrakenSpotTradeListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(KrakenSpotTradeListener.class);
    private static final String WS_URL = "wss://ws-auth.kraken.com/v2";
    private static final long PING_INTERVAL_SECONDS = 25;
    private static final long PONG_TIMEOUT_SECONDS = 35;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;
    private static final int PROXY_PORT = 8888;

    private final String apiKey;
    private final String apiSecret;
    private final ExchangeSubscription subscription;
    private final KrakenRestClient restClient;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private volatile Channel channel;
    private ScheduledFuture<?> pingFuture;
    private ScheduledFuture<?> watchdogFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();
    private volatile String websocketToken = null;
    private volatile long tokenCreatedTime = 0L;
    private volatile long pingRequestId = 100L;

    public KrakenSpotTradeListener(final String apiKey, final String apiSecret, final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.subscription = subscription;
        this.restClient = new KrakenRestClient(apiKey, apiSecret, subscription);
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

    public long getLastPongReceived() {
        return lastPongReceived;
    }

    @Override
    public void onBinaryMessage(final byte[] bytes) {
        // Handle binary messages if any
    }

    public void connect() throws Exception {
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker =
                WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "KRAKEN-SPOT-TRADE-LISTENER");

        final Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                        p.addLast(new HttpClientCodec());
                        p.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                        p.addLast(handler);
                    }
                });

        this.channel = b.connect(host, port).sync().channel();
        handler.handshakeFuture().sync();

        LOGGER.info("Kraken SpotTrade WebSocket connected");
        authenticate();
    }

    public void disconnect() {
        setConnected(false);
        setAuthenticated(false);
        stopPing();
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

    public void setConnected(final boolean connected) {
        this.connected.set(connected);
    }

    private void authenticate() throws Exception {
        // Retrieve WebSocket token from REST API
        this.websocketToken = restClient.getWebsocketToken();
        this.tokenCreatedTime = System.currentTimeMillis();

        if (websocketToken == null || websocketToken.isEmpty()) {
            LOGGER.error("Failed to retrieve WebSocket token from REST API");
            reconnect();
            return;
        }
        LOGGER.info("Successfully retrieved WebSocket token, attempting authentication");
    }

    private void schedulePeriodicPing() {
        stopPing();
        lastPongReceived = System.currentTimeMillis();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                final long reqId = ++pingRequestId;
                final String pingJson = "{\"method\":\"ping\",\"req_id\":" + reqId + "}";
                channel.writeAndFlush(new TextWebSocketFrame(pingJson));
                LOGGER.debug("Sending Kraken Spot Trade ping with req_id: " + reqId);
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopPing() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
    }

    /**
     * Schedules a watchdog task to detect stale connections by monitoring pong responses
     * Reconnects if no pong is received within PONG_TIMEOUT_SECONDS
     */
    private void scheduleWatchdog() {
        stopWatchdog();
        watchdogFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            final long timeSinceLastPong = System.currentTimeMillis() - lastPongReceived;
            final long timeoutMillis = PONG_TIMEOUT_SECONDS * 1000;

            if (timeSinceLastPong > timeoutMillis) {
                LOGGER.warn("Pong timeout detected (no pong for " + (timeSinceLastPong / 1000) +
                        "s). Reconnecting to Kraken Spot Trade WebSocket");
                reconnect();
            }
        }, PONG_TIMEOUT_SECONDS, PONG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void stopWatchdog() {
        if (watchdogFuture != null) {
            watchdogFuture.cancel(false);
            watchdogFuture = null;
        }
    }

    public void reconnect() {
        LOGGER.info("Kraken SpotTrade WebSocket reconnecting in " + RECONNECT_DELAY_SEC + " seconds...");
        setConnected(false);
        setAuthenticated(false);
        stopPing();
        stopWatchdog();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }

        // Attempt to schedule reconnect using channel's event loop if available
        if (channel != null && channel.eventLoop() != null && !channel.eventLoop().isShuttingDown()) {
            channel.eventLoop().schedule(() -> {
                try {
                    LOGGER.info("Attempting reconnection to Kraken SpotTrade WebSocket");
                    connect();
                } catch (final Exception e) {
                    LOGGER.error("Error during reconnect: " + e.getMessage(), e);
                    reconnect();
                }
            }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
        } else {
            // Fallback reconnection mechanism using new thread when event loop is unavailable
            new Thread(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY_SEC * 1000L);
                    LOGGER.info("Attempting reconnection to Kraken SpotTrade WebSocket via fallback thread");
                    connect();
                } catch (final Exception e) {
                    LOGGER.error("Error during reconnect via fallback thread: " + e.getMessage(), e);
                    reconnect();
                }
            }).start();
        }
    }

    public void onMessage(final String json) {
        LOGGER.debug("Kraken SpotTrade Received message: " + json);

        final String method = minExtract(json, "method");
        final String channel = minExtract(json, "channel");

        if (method == null && channel == null) {
            return;
        }

        if ("status".equals(channel)) {
            initiatePingPong(json);
        } else if ("pong".equals(method)) {
            setLastPongReceived(System.currentTimeMillis());
            LOGGER.debug("Received pong from Kraken Spot Trade");
        } else if ("add_order".equals(method)) {
            handleOrderAcknowledgement(json);
        } else if ("error".equals(method)) {
            handleErrorMessage(json);
        }
    }

    private void initiatePingPong(final String message) {
        try {
            final String channel = minExtract(message, "channel");

            if (!"status".equals(channel)) {
                return;
            }

            final String dataArrayStr = extractJsonValue(message, "data");
            if (dataArrayStr == null || !dataArrayStr.startsWith("[")) {
                return;
            }

            // Parse the status data object
            final int objStart = dataArrayStr.indexOf('{');
            if (objStart < 0) {
                return;
            }

            final int objEnd = dataArrayStr.indexOf('}', objStart);
            if (objEnd < 0) {
                return;
            }

            final String statusObj = dataArrayStr.substring(objStart, objEnd + 1);
            final String system = minExtract(statusObj, "system");

            if ("online".equals(system)) {
                LOGGER.info("Kraken Spot Trade system online, initiating ping/pong");
                if (!isConnected()) {
                    setConnected(true);
                    setAuthenticated(true);
                    schedulePeriodicPing();
                    scheduleWatchdog();
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Error processing status message: " + e.getMessage(), e);
        }
    }

    /**
     * Handles order acknowledgement responses from add_order requests
     * Processes both success and failure scenarios
     */
    private void handleOrderAcknowledgement(final String message) {
        try {
            LOGGER.debug("Processing order acknowledgement: " + message);

            final String successStr = minExtract(message, "success");
            final boolean success = "true".equals(successStr);

            final String reqIdStr = minExtract(message, "req_id");
            if (reqIdStr == null) {
                LOGGER.warn("Missing req_id in order acknowledgement");
                return;
            }

            // Use req_id as clOrdId to match with original order
            final String clOrdId = reqIdStr;

            final Order order = subscription.getOrder(clOrdId);
            if (order == null) {
                LOGGER.warn("Order not found in cache for clOrdId: " + clOrdId);
                return;
            }

            if (success) {
                // Success scenario: Order was accepted
                final String orderId = minExtract(message, "order_id");
                LOGGER.info("Order acknowledged successfully for clOrdId: " + clOrdId + 
                        ", orderId: " + orderId);
                order.setOcoClOrdId(orderId);
                ExecutionReportMessage executionReportMessage = subscription.getExecutionReport(clOrdId);
                if (executionReportMessage == null) {
                    executionReportMessage = ExecutionReportMessage.createExternalExecutionReport(
                            order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                            0, 0, 0, 0, order.getSide(), 0);
                }

                executionReportMessage.setClOrdId(clOrdId);
                subscription.updateExecutionReport(executionReportMessage);
                subscription.updateOrder(clOrdId,order);
            } else {
                // Failure scenario: Order was rejected
                final String errorMsg = minExtract(message, "error");
                LOGGER.error("Order rejected for clOrdId: " + clOrdId + ", error: " + errorMsg);

                ExecutionReportMessage executionReportMessage = subscription.getExecutionReport(clOrdId);
                if (executionReportMessage == null) {
                    executionReportMessage = ExecutionReportMessage.createExternalExecutionReport(
                            order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                            0, 0, 0, 0, order.getSide(), 0);
                }

                executionReportMessage.setClOrdId(clOrdId);
                executionReportMessage.setError(errorMsg);
                executionReportMessage.setExecType(ExecType.REJECTED);
                executionReportMessage.setOrdStatus(OrdStatus.REJECTED);
                
                subscription.updateExecutionReport(executionReportMessage);
                subscription.updateOrder(clOrdId, "REJECTED");
            }
        } catch (final Exception e) {
            LOGGER.error("Error processing order acknowledgement: " + e.getMessage(), e);
        }
    }


    private void handleErrorMessage(final String message) {
        final String errorMsg = minExtract(message, "error");
        LOGGER.error("Kraken Spot Trade error: " + errorMsg + ", Full message: " + message);
    }

    /**
     * Places a spot order via WebSocket
     * Uses Kraken Spot v2 WebSocket API add_order method
     * Reference: https://docs.kraken.com/api/docs/websocket-v2/add_order
     *
     * @param order
     * @param symbol    Trading pair symbol (Kraken format, e.g., "BTC/USD")
     * @param side      buy or sell
     * @param orderType limit or market
     * @param quantity  Order quantity as string
     * @param price     Order price as string (required for limit orders, optional for market)
     * @param clOrdId   Client order ID for reference
     */
    public void placeOrder(final Order order, final String symbol, final String side, final String orderType, final String quantity, final String price, final String clOrdId) {
        if (!authenticated.get() || websocketToken == null || websocketToken.isEmpty()) {
            LOGGER.error("Cannot place order: not authenticated or no websocket token");
            return;
        }

        if (channel == null || !channel.isActive()) {
            LOGGER.error("Cannot place order: channel is not active");
            return;
        }

        // Validate required parameters
        if (symbol == null || symbol.isEmpty()) {
            LOGGER.error("Cannot place order: symbol is required");
            return;
        }

        if (side == null || (!side.equalsIgnoreCase("buy") && !side.equalsIgnoreCase("sell"))) {
            LOGGER.error("Cannot place order: side must be buy or sell, got: " + side);
            return;
        }

        if (orderType == null || (!orderType.equalsIgnoreCase("limit") && !orderType.equalsIgnoreCase("market"))) {
            LOGGER.error("Cannot place order: orderType must be limit or market, got: " + orderType);
            return;
        }

        if (quantity == null || quantity.isEmpty()) {
            LOGGER.error("Cannot place order: quantity is required");
            return;
        }

        if ("limit".equalsIgnoreCase(orderType) && (price == null || price.isEmpty())) {
            LOGGER.error("Cannot place order: price is required for limit orders");
            return;
        }

        final StringBuilder orderJson = new StringBuilder("{");
        orderJson.append("\"method\":\"add_order\",");
        orderJson.append("\"req_id\":").append(Long.valueOf(order.getClOrdId())).append(",");
        orderJson.append("\"params\":{");
        orderJson.append("\"order_userref\":").append(Long.valueOf(order.getClOrdId())).append(",");
        orderJson.append("\"symbol\":\"").append(symbol).append("\",");
        orderJson.append("\"side\":\"").append(side.toLowerCase()).append("\",");
        orderJson.append("\"order_type\":\"").append(orderType.toLowerCase()).append("\",");
        orderJson.append("\"order_qty\":").append(quantity);

        // Only add price for limit orders
        if ("limit".equalsIgnoreCase(orderType)) {
            orderJson.append(",\"limit_price\":").append(price);
        }

        orderJson.append(",\"token\":\"").append(websocketToken).append("\"");
        orderJson.append("}");
        orderJson.append("}");

        LOGGER.info("Sending Kraken Spot v2 add_order via WebSocket: symbol=" + symbol + ", side=" + side +
                ", order_type=" + orderType + ", order_qty=" + quantity +
                (price != null ? ", limit_price=" + price : "") + ", clientId=" + clOrdId);
        LOGGER.debug("Order JSON: " + orderJson.toString());
        channel.writeAndFlush(new TextWebSocketFrame(orderJson.toString()));
    }


}
