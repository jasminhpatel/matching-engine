package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit;

import com.solfini.common.Context;
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
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;

public final class BybitTradeDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(BybitTradeDataListener.class);
    private static final String WS_URL = Context.getBybitUnifiedWs() + "/trade";
    private static final long PING_INTERVAL_SECONDS = 20;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final long PONG_TIMEOUT_MILLIS = 60000; // 60 seconds
    private static final int SSL_PORT = 443;
    private static final int PROXY_PORT = 8888;
    private static final int MAX_CONTENT_LENGTH = 8192;

    // Immutable configuration - set once in constructor
    private final String apiKey;
    private final String secretKey;
    private final ExchangeSubscription subscription;
    private final EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicLong requestId = new AtomicLong(1);
    
    // Connection state - can change during runtime
    private volatile long lastPongReceived = System.currentTimeMillis();
    private Channel channel;
    private ScheduledFuture<?> pingFuture;

    /**
     * Initializes ByBit Trade Data WebSocket listener for order management
     * @param apiKey ByBit API key for authentication
     * @param secretKey ByBit API secret for HMAC signature generation
     * @param subscription Liquidity subscription for order management
     */
    public BybitTradeDataListener(final String apiKey, final String secretKey, final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
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

    }

    @Override
    public boolean isConnected() {
        return connected.get() && authenticated.get();
    }

    public void setConnected(final boolean connected) {
        this.connected.set(connected);
    }

    /**
     * Establishes WebSocket connection to ByBit trade stream for order management
     */
    public void connect() throws Exception {
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();
        final String proxyHost = subscription.getLastUsedProxy();
        final int proxyPort = PROXY_PORT;
        LOGGER.info("Connecting via proxy host: " + proxyHost + " proxy port: " + proxyPort);
      
        final SslContext sslCtx = SslContextBuilder.forClient().build();
        final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "BYBIT-TRADE-LISTENER");

        final Bootstrap b = new Bootstrap();
        b.group(group)
          .channel(NioSocketChannel.class)
            .localAddress(new InetSocketAddress(Context.getOutboundIp(), 0))
            .handler(new ChannelInitializer<Channel>() {
              @Override
              protected void initChannel(final Channel ch) {
              final ChannelPipeline p = ch.pipeline();
/*              if (proxyHost != null) {
                p.addLast(new HttpProxyHandler(new InetSocketAddress(proxyHost, proxyPort)));
              }*/
              p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
              p.addLast(new HttpClientCodec());
              p.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
              p.addLast(handler);
              }
          });

/*        if (Context.getOutboundIp() != null && !Context.getOutboundIp().isEmpty()) {
          // Bind explicitly to outbound ip
          this.channel = b.connect(
              new InetSocketAddress(host, port),
              new InetSocketAddress(Context.getOutboundIp(), 0)
          ).sync().channel();
        } else {*/
          this.channel = b.connect(host, port).sync().channel();
        //}
        handler.handshakeFuture().sync();

        LOGGER.info("ByBit Trade WebSocket connected");
        setConnected(true);
        setAuthenticated(false);
        authenticate();
        startPingThread();
    }

    /**
     * Gracefully disconnects from WebSocket and shuts down resources
     */
    public void disconnect() {
        stopPingThread();
        setConnected(false);
        setAuthenticated(false);
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        group.shutdownGracefully();
    }

    /**
     * Places a spot order via WebSocket
     * @param symbol Trading pair symbol
     * @param side Order side (Buy/Sell)
     * @param type Order type (Limit/Market)
     * @param timeInForce Time in force (GTC/FOK/IOC)
     * @param quantity Order quantity as string
     * @param price Order price as string
     * @param clientOrderId Client-specified order ID
     */
    public void placeSpotOrder(final String symbol, final String side, final String type, final String timeInForce,
                               final String quantity, final String price, final String clientOrderId) {
        if (!isConnected()) {
            LOGGER.warn("Cannot place spot order - WebSocket not connected or not authenticated");
            return;
        }

        final long reqId = requestId.getAndIncrement();
        final long timestamp = System.currentTimeMillis();

        // Limit order for spot (market orders commented out for now)
        final String orderRequest = String.format("""
                {
                    "reqId": "%s",
                    "header": {
                        "X-BAPI-TIMESTAMP": "%d",
                        "X-BAPI-RECV-WINDOW": "5000",
                        "X-BAPI-API-KEY": "%s",
                        "Referer": "%s"
                    },
                    "op": "order.create",
                    "args": [{
                        "category": "spot",
                        "symbol": "%s",
                        "side": "%s",
                        "orderType": "%s",
                        "qty": "%s",
                        "price": "%s",
                        "timeInForce": "%s",
                        "orderLinkId": "%s"
                    }]
                }
                """, clientOrderId, timestamp, apiKey, subscription.getBrokerId(), symbol, side, type, quantity, price, timeInForce, clientOrderId);

        LOGGER.debug("Sending ByBit Spot order: " + orderRequest);
        channel.writeAndFlush(new TextWebSocketFrame(orderRequest));
    }

    /**
     * Places a linear (futures) order via WebSocket
     * @param symbol Trading pair symbol
     * @param side Order side (Buy/Sell)
     * @param type Order type (Limit/Market)
     * @param timeInForce Time in force (GTC/FOK/IOC)
     * @param quantity Order quantity as string
     * @param price Order price as string
     * @param clientOrderId Client-specified order ID
     */
    public void placeLinearOrder(final String symbol, final String side, final String type, final String timeInForce,
                                 final String quantity, final String price, final String clientOrderId) {
        if (!isConnected()) {
            LOGGER.warn("Cannot place linear order - WebSocket not connected or not authenticated");
            return;
        }

        final long timestamp = System.currentTimeMillis();

        final String orderRequest = String.format("""
                {
                    "reqId": "%s",
                    "header": {
                        "X-BAPI-TIMESTAMP": "%d",
                        "X-BAPI-RECV-WINDOW": "5000",
                        "X-BAPI-API-KEY": "%s",
                        "Referer": "%s"
                    },
                    "op": "order.create",
                    "args": [{
                        "category": "linear",
                        "symbol": "%s",
                        "side": "%s",
                        "orderType": "%s",
                        "qty": "%s",
                        "price": "%s",
                        "timeInForce": "%s",
                        "orderLinkId": "%s"
                    }]
                }
                """, clientOrderId, timestamp, apiKey, subscription.getBrokerId(), symbol, side, type, quantity, price, timeInForce, clientOrderId);

        LOGGER.debug("Sending ByBit Linear order: " + orderRequest);
        channel.writeAndFlush(new TextWebSocketFrame(orderRequest));
    }

    /**
     * Reconnects to WebSocket after connection loss
     */
    public void reconnect() {
        stopPingThread();
        setConnected(false);
        setAuthenticated(false);
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        channel.eventLoop().schedule(() -> {
            try {
                LOGGER.info("ByBit Trade WebSocket reconnecting...");
                connect();
            } catch (final Exception e) {
                LOGGER.error("Error during reconnect", e);
                reconnect();
            }
        }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
    }

    /**
     * Authenticates with ByBit using API credentials
     */
    private void authenticate() throws Exception {
        final long timestamp = System.currentTimeMillis();
        final String expires = String.valueOf(timestamp + 10000);
        final String payload = "GET/realtime" + expires;
        final String signature = HMAC.hmacSha256(payload, secretKey);

        final String authMessage = String.format("""
                {
                    "op": "auth",
                    "args": ["%s", "%s", "%s"]
                }
                """, apiKey, expires, signature);

        LOGGER.info("Authenticating ByBit Trade WebSocket...");
        channel.writeAndFlush(new TextWebSocketFrame(authMessage));
    }

    /**
     * Starts periodic ping to maintain WebSocket connection
     */
    private void startPingThread() {
        stopPingThread();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (authenticated.get() && channel.isActive()) {
                sendPing();

                // Check if we received pong within reasonable time
                if (System.currentTimeMillis() - lastPongReceived > PONG_TIMEOUT_MILLIS) {
                    LOGGER.warn("ByBit Trade WebSocket: No pong received for " + (PONG_TIMEOUT_MILLIS / 1000) + " seconds, connection may be dead");
                    connected.set(false);
                    reconnect();
                }
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Stops the periodic ping task
     */
    private void stopPingThread() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
    }

    /**
     * Sends ping frame to maintain connection
     */
    private void sendPing() {
        try {
            LOGGER.debug("Sending ByBit Trade ping");
            channel.writeAndFlush(new PingWebSocketFrame());
        } catch (final Exception e) {
            LOGGER.error("Failed to send ByBit Trade ping: " + e.getMessage());
        }
    }

    /**
     * Main message handler for trade WebSocket messages
     * @param message WebSocket message received from ByBit
     */
    public void onMessage(final String message) {
        LOGGER.debug("Message received: " + message);
        try {
            if (message.contains("\"op\":\"auth\"")) {
                handleAuthResponse(message);
            } else if (message.contains("\"op\":\"order.create\"")) {
                handleOrderResponse(message);
            }
        } catch (final Exception e) {
            LOGGER.error("Error processing ByBit Trade message", e);
        }
    }

    /**
     * Handles authentication response from ByBit WebSocket
     */
    private void handleAuthResponse(final String message) {
        final String op = minExtract(message, "op");

        if ("auth".equals(op)) {
            // Check for new format with retCode
            final String retCode = minExtract(message, "retCode");
            if (retCode != null) {
                if ("0".equals(retCode)) {
                    final String connId = minExtract(message, "connId");
                    LOGGER.info("ByBit Trade WebSocket authenticated successfully - Connection ID: " + connId);
                    setAuthenticated(true);
                } else {
                    final String retMsg = minExtract(message, "retMsg");
                    LOGGER.error("ByBit Trade WebSocket authentication failed - Code: " + retCode + ", Message: " + retMsg);
                    setConnected(false);
                    setAuthenticated(false);
                }
            } else {
                // Check for old format with success
                final String success = minExtract(message, "success");
                if ("true".equals(success)) {
                    final String connId = minExtract(message, "conn_id");
                    LOGGER.info("ByBit Trade WebSocket authenticated successfully - Connection ID: " + connId);
                    setAuthenticated(true);
                } else {
                    final String retMsg = minExtract(message, "ret_msg");
                    LOGGER.error("ByBit Trade WebSocket authentication failed - Message: " + retMsg);
                    setConnected(false);
                    setAuthenticated(false);
                }
            }
        }
    }

    /**
     * Handles order creation response from ByBit WebSocket
     */
    private void handleOrderResponse(final String message) {
        try {
            // Try to extract retCode and retMsg from the root level first
            String retCode = minExtract(message, "retCode");
            String retMsg = minExtract(message, "retMsg");
            final String reqId = minExtract(message, "reqId");  // We set reqId same value as orderLinkId

            if (reqId == null) {
                LOGGER.info("ByBit order - Received reqId null. Message:" + message);
                return;
            }

            final Order order = subscription.getOrder(reqId);

            // If not found at root, check if it's in the success/ret_msg format
            if (retCode == null) {
                final String success = minExtract(message, "success");
                if ("false".equals(success)) {
                    retCode = "1"; // Indicate failure
                    retMsg = minExtract(message, "ret_msg");
                } else if ("true".equals(success)) {
                    retCode = "0"; // Indicate success
                }
            }

            if ("0".equals(retCode) || "true".equals(minExtract(message, "success"))) {
                LOGGER.debug("ByBit order placed successfully");
                final int resultStart = message.indexOf("\"result\":");
                if (resultStart >= 0) {
                    final int resultEnd = message.lastIndexOf('}');
                    final String resultSection = message.substring(resultStart + 9, resultEnd);

                    final String orderLinkId = minExtract(resultSection, "orderLinkId");
                    LOGGER.info("ByBit order created - OrderId: " + order.getOrderId() + ", ClientOrderId: " + orderLinkId);

                }
            } else {
                LOGGER.error("ByBit order failed - Code: " + retCode + ", Message: " + retMsg);

                if (order != null) {

                // Create or update execution report with all extracted fields
                ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionMessage == null) {
                    executionMessage = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, order.getQty(), order.getQtyScale(), 0, 0, 0, 0, order.getSide(), 0);

                }
                executionMessage.setClOrdId(reqId);
                executionMessage.setError(retMsg);
                executionMessage.setExecType(ExecType.REJECTED);
                subscription.updateExecutionReport(executionMessage);

                order.setRejected(true);
                subscription.updateOrder(reqId, "REJECTED");
            }
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling ByBit order response", e);
        }
    }
}
