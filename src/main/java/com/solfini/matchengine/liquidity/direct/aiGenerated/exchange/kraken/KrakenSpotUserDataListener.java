package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kraken;

import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
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
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.net.URI;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class KrakenSpotUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(KrakenSpotUserDataListener.class);
    private static final long PING_INTERVAL_SECONDS = 25;
    private static final long PONG_TIMEOUT_SECONDS = 35;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;
    private static final int PROXY_PORT = 8888;

    private final String apiKey;
    private final String apiSecret;
    private final ExchangeSubscription subscription;
    private final String WS_URL;
    private final KrakenRestClient restClient;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private volatile Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();
    private volatile long challengeToken = 0L;
    private volatile String websocketToken = null;
    private volatile long tokenCreatedTime = 0L;
    private volatile long lastHeartbeatReceived = System.currentTimeMillis();
    private ScheduledFuture<?> watchdogFuture;
    private volatile long pingRequestId = 100L;

    public KrakenSpotUserDataListener(final String wsUrl, final String apiKey, final String apiSecret, final ExchangeSubscription subscription) {
        this.WS_URL = wsUrl;
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

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "KRAKEN-SPOT-USER-DATA-LISTENER");

        final Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                //  .localAddress(new InetSocketAddress(Context.getOutboundIp(), 0))
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

        LOGGER.info("Kraken SpotUserData WebSocket connected");
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
        subscribeToPrivateFeeds();
    }

    private void subscribeToPrivateFeeds() throws Exception {
        // Subscribe to executions feed
        final String executionsSubscription = "{" +
                "\"method\":\"subscribe\"," +
                "\"params\":{" +
                "\"channel\":\"executions\"," +
                "\"token\":\"" + websocketToken + "\"" +
                "}" +
                "}";

        // Subscribe to balances feed
        final String balancesSubscription = "{" +
                "\"method\":\"subscribe\"," +
                "\"params\":{" +
                "\"channel\":\"balances\"," +
                "\"token\":\"" + websocketToken + "\"" +
                "}" +
                "}";

        LOGGER.debug("Subscribing to Kraken Spot v2 private feeds with WebSocket token");
        channel.writeAndFlush(new TextWebSocketFrame(executionsSubscription));
        channel.writeAndFlush(new TextWebSocketFrame(balancesSubscription));
    }

    private void schedulePeriodicPing() {
        stopPing();
        lastPongReceived = System.currentTimeMillis();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                final long reqId = ++pingRequestId;
                final String pingJson = "{\"method\":\"ping\",\"req_id\":" + reqId + "}";
                channel.writeAndFlush(new TextWebSocketFrame(pingJson));
                LOGGER.debug("Sending Kraken Spot v2 UserData ping with req_id: " + reqId);
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
                        "s). Reconnecting to Kraken Spot UserData WebSocket");
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
        LOGGER.info("Kraken SpotUserData WebSocket reconnecting in " + RECONNECT_DELAY_SEC + " seconds...");
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
                    LOGGER.info("Attempting reconnection to Kraken SpotUserData WebSocket");
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
                    LOGGER.info("Attempting reconnection to Kraken SpotUserData WebSocket via fallback thread");
                    connect();
                } catch (final Exception e) {
                    LOGGER.error("Error during reconnect via fallback thread: " + e.getMessage(), e);
                    reconnect();
                }
            }).start();
        }
    }

    public void onMessage(final String json) {
        LOGGER.debug("Kraken SpotUserData Received message: " + json);

        final String method = minExtract(json, "method");
        final String channel = minExtract(json, "channel");

        if (method == null && channel == null) {
            return;
        }

        if ("subscribe".equals(method)) {
            handleSubscriptionStatus(json);
        } else if ("pong".equals(method)) {
            setLastPongReceived(System.currentTimeMillis());
            LOGGER.debug("Received pong from Kraken Spot UserData");
        } else if ("heartbeat".equals(channel)) {
            handleHeartbeat(json);
        } else if ("executions".equals(channel)) {
            handleExecutionsUpdate(json);
        } else if ("balances".equals(channel)) {
            handleBalancesUpdate(json);
        } else if ("error".equals(method)) {
            handleErrorMessage(json);
        }
    }

    private void handleSubscriptionStatus(final String message) {
        final String channel = minExtract(message, "channel");
        final String result = minExtract(message, "success");

        if ("true".equals(result)) {
            LOGGER.info("Successfully subscribed to Kraken Spot v2 UserData feed: " + channel);
            if (!isConnected()) {
                setConnected(true);
                setAuthenticated(true);
                schedulePeriodicPing();
                scheduleWatchdog();
            }

        } else {
            final String errorMsg = minExtract(message, "error");
            LOGGER.warn("Subscription status for " + channel + ": " + result + (errorMsg != null ? ", error: " + errorMsg : ""));
        }
    }

    /**
     * Handles heartbeat messages from Kraken Spot v2
     * Heartbeats are sent periodically by the server to maintain connection activity
     */
    private void handleHeartbeat(final String message) {
        setLastHeartbeatReceived(System.currentTimeMillis());
        // LOGGER.debug("Received heartbeat from Kraken Spot UserData");
    }

    public void setLastHeartbeatReceived(final long lastHeartbeatReceived) {
        this.lastHeartbeatReceived = lastHeartbeatReceived;
    }

    public long getLastHeartbeatReceived() {
        return lastHeartbeatReceived;
    }

    public long getLastPongReceived() {
        return lastPongReceived;
    }

    /**
     * Handles executions updates from Kraken Spot v2 (unified feed for trades and order updates)
     * https://docs.kraken.com/api/docs/websocket-v2/executions
     */
    private void handleExecutionsUpdate(final String message) {
        try {
            LOGGER.debug("Processing executions update: " + message);

            final String dataArrayStr = extractJsonValue(message, "data");
            if (dataArrayStr == null || !dataArrayStr.startsWith("[")) {
                return;
            }

            // Parse each execution object in the array
            int p = 0;
            while (p < dataArrayStr.length()) {
                final int objStart = dataArrayStr.indexOf('{', p);
                if (objStart < 0) break;

                // Find matching closing brace for execution object
                int braceDepth = 0;
                int objEnd = -1;
                for (int i = objStart; i < dataArrayStr.length(); i++) {
                    final char c = dataArrayStr.charAt(i);
                    if (c == '{') {
                        braceDepth++;
                    } else if (c == '}') {
                        braceDepth--;
                        if (braceDepth == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }

                if (objEnd < 0) break;

                final String execObj = dataArrayStr.substring(objStart, objEnd + 1);

                final String orderId = minExtract(execObj, "order_id");
                final String symbol = minExtract(execObj, "symbol");
                final String side = minExtract(execObj, "side");
                final String orderQty = minExtract(execObj, "order_qty");
                final String cumQty = minExtract(execObj, "cum_qty");
                final String cumCost = minExtract(execObj, "cum_cost");
                final String limitPrice = minExtract(execObj, "limit_price");
                final String lastQty = minExtract(execObj, "last_qty");
                final String lastPrice = minExtract(execObj, "last_price");
                final String avgPrice = minExtract(execObj, "avg_price");
                final String orderStatus = minExtract(execObj, "order_status");
                final String execType = minExtract(execObj, "exec_type");
                final String clOrId = minExtract(execObj, "order_userref");
                final String feeUsdEquiv = minExtract(execObj, "fee_usd_equiv");

                if (orderId == null || symbol == null) {
                    LOGGER.warn("Missing required fields in executions update");
                    p = objEnd + 1;
                    continue;
                }

                final Order order = subscription.getOrder(clOrId);
                if (order == null) {
                    LOGGER.warn("Order not found in cache for orderId: " + orderId);
                    p = objEnd + 1;
                    continue;
                }

                final double orderQtyDouble = parseDoubleSafe(orderQty);
                final double cumQtyDouble = parseDoubleSafe(cumQty);
                final double cumCostDouble = parseDoubleSafe(cumCost);
                final double limitPriceDouble = parseDoubleSafe(limitPrice);
                final double lastQtyDouble = parseDoubleSafe(lastQty);
                final double lastPriceDouble = parseDoubleSafe(lastPrice);
                final double avgPriceDouble = parseDoubleSafe(avgPrice);

                final long qtyLong = MbxMath.changeScale(orderQtyDouble, order.getQtyScale());
                final long cumQtyLong = MbxMath.changeScale(cumQtyDouble, order.getQtyScale());
                final long limitPriceLong = MbxMath.changeScale(limitPriceDouble, order.getPriceScale());
                final long lastQtyLong = MbxMath.changeScale(lastQtyDouble, order.getQtyScale());
                final long lastPriceLong = MbxMath.changeScale(lastPriceDouble, order.getPriceScale());
                final long avgPriceLong = MbxMath.changeScale(avgPriceDouble, order.getPriceScale());

                LOGGER.info("SPOT USER DATA STREAM >>> Executions update for orderId: " + orderId +
                        ", status: " + orderStatus + ", cumQty: " + cumQty + ", lastQty: " + lastQty +
                        ", execType: " + execType);

                ExecutionReportMessage executionMessage = subscription.getExecutionReport(clOrId);
                if (executionMessage == null) {
                    executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                            order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                            0, 0, 0, 0, order.getSide(), 0);
                }

                executionMessage.setClOrdId(order.getClOrdId());
                executionMessage.setOrderQty(qtyLong);
                executionMessage.setOrderQtyScale(order.getQtyScale());
                executionMessage.setPrice(limitPriceLong);
                executionMessage.setPriceScale(order.getPriceScale());
                executionMessage.setCumQty(cumQtyLong);
                executionMessage.setLeavesQty(qtyLong - cumQtyLong);

                // Set average price (cumulative average)
                executionMessage.setAvgPx(avgPriceLong);

                // Set last execution price and quantity if this is a trade execution
                if (lastQtyLong > 0) {
                    executionMessage.setLastQty(lastQtyLong);
                    executionMessage.setLastQtyScale(order.getQtyScale());
                    executionMessage.setLastPx(lastPriceLong);
                    executionMessage.setLastPxScale(order.getPriceScale());
                }

                // Handle order status transitions
                if ("pending_new".equalsIgnoreCase(orderStatus)) {
                    executionMessage.setOrdStatus(OrdStatus.PENDING_NEW);
                    executionMessage.setExecType(ExecType.PENDING_NEW);
                    LOGGER.info("Order PENDING_NEW - orderId: " + orderId);
                } else if ("accepted".equalsIgnoreCase(orderStatus) || "open".equalsIgnoreCase(orderStatus)) {
                    executionMessage.setOrdStatus(OrdStatus.NEW);
                    if ("trade".equalsIgnoreCase(execType)) {
                        executionMessage.setExecType(ExecType.TRADE);
                    } else {
                        executionMessage.setExecType(ExecType.NEW);
                    }
                    LOGGER.info("Order NEW/ACCEPTED - orderId: " + orderId);
                } else if ("filled".equalsIgnoreCase(orderStatus) || "done".equalsIgnoreCase(orderStatus)) {
                    executionMessage.setOrdStatus(OrdStatus.FILLED);
                    executionMessage.setExecType(ExecType.TRADE);
                    order.setExecuted(true);
                    LOGGER.info("Order FILLED - orderId: " + orderId);
                } else if ("partially_filled".equalsIgnoreCase(orderStatus)) {
                    executionMessage.setOrdStatus(OrdStatus.PARTIALLY_FILLED);
                    if ("trade".equalsIgnoreCase(execType)) {
                        executionMessage.setExecType(ExecType.TRADE);
                    } else {
                        executionMessage.setExecType(ExecType.PARTIAL_FILL);
                    }
                    LOGGER.debug("Order PARTIALLY FILLED - orderId: " + orderId);
                } else if ("cancelled".equalsIgnoreCase(orderStatus) || "rejected".equalsIgnoreCase(orderStatus)) {
                    executionMessage.setOrdStatus(OrdStatus.CANCELED);
                    executionMessage.setExecType(ExecType.CANCELED);
                    order.setRejected(true);
                    LOGGER.warn("Order CANCELLED - clOrId: " + clOrId);
                }

                // Handle fees array
                final String feesArrayStr = extractJsonValue(execObj, "fees");
                if (feesArrayStr != null && feesArrayStr.startsWith("[")) {
                    parseFees(feesArrayStr, executionMessage);
                }

                subscription.updateExecutionReport(executionMessage);
                subscription.updateOrder(clOrId, order);

                p = objEnd + 1;
            }
        } catch (final Exception e) {
            LOGGER.error("Error processing executions update: " + e.getMessage(), e);
        }
    }

    /**
     * Parses fees array from executions update
     * Format: [{"asset":"USDT","qty":0.01604936}]
     */
    private void parseFees(final String feesArrayStr, final ExecutionReportMessage executionMessage) {
        try {
            int p = 0;
            while (p < feesArrayStr.length()) {
                final int objStart = feesArrayStr.indexOf('{', p);
                if (objStart < 0) break;

                // Find matching closing brace for fee object
                int braceDepth = 0;
                int objEnd = -1;
                for (int i = objStart; i < feesArrayStr.length(); i++) {
                    final char c = feesArrayStr.charAt(i);
                    if (c == '{') {
                        braceDepth++;
                    } else if (c == '}') {
                        braceDepth--;
                        if (braceDepth == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }

                if (objEnd < 0) break;

                final String feeObj = feesArrayStr.substring(objStart, objEnd + 1);
                final String asset = minExtract(feeObj, "asset");
                final String qty = minExtract(feeObj, "qty");

                if (asset != null && qty != null) {
                    final double feeQtyDouble = parseDoubleSafe(qty);
                    final Instrument feeInstrument = InstrumentCache.getBySymbol(asset);

                    if (feeInstrument != null) {
                        final long feeQtyLong = MbxMath.changeScale(feeQtyDouble, feeInstrument.getQuantityScale());
                        executionMessage.setFeeAccumulatedQuantity(feeQtyLong);
                        executionMessage.setFeePositionId(feeInstrument.getId());
                        LOGGER.debug("Fee extracted: asset=" + asset + ", qty=" + qty);
                    }
                }

                p = objEnd + 1;
            }
        } catch (final Exception e) {
            LOGGER.error("Error parsing fees array: " + e.getMessage(), e);
        }
    }

    /**
     * Handles balances updates from Kraken Spot v2
     * https://docs.kraken.com/api/docs/websocket-v2/balances
     */
    private void handleBalancesUpdate(final String message) {
        try {
            LOGGER.debug("Processing balances update: " + message);

            final String dataArrayStr = extractJsonValue(message, "data");
            if (dataArrayStr == null || !dataArrayStr.startsWith("[")) {
                return;
            }

            // Parse each balance object in the array
            int p = 0;
            while (p < dataArrayStr.length()) {
                final int objStart = dataArrayStr.indexOf('{', p);
                if (objStart < 0) break;

                // Find matching closing brace for balance object
                int braceDepth = 0;
                int objEnd = -1;
                for (int i = objStart; i < dataArrayStr.length(); i++) {
                    final char c = dataArrayStr.charAt(i);
                    if (c == '{') {
                        braceDepth++;
                    } else if (c == '}') {
                        braceDepth--;
                        if (braceDepth == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }

                if (objEnd < 0) break;

                final String balanceObj = dataArrayStr.substring(objStart, objEnd + 1);

                final String asset = minExtract(balanceObj, "asset");
                final String balanceStr = minExtract(balanceObj, "balance");

                if (asset != null && balanceStr != null) {
                    final double balance = parseDoubleSafe(balanceStr);
                    subscription.updateBalance(asset, balance);

                    LOGGER.info("SPOT USER DATA STREAM >>> Balance update for asset: " + asset +
                            ", balance: " + balanceStr);
                }

                p = objEnd + 1;
            }
        } catch (final Exception e) {
            LOGGER.error("Error processing balances update: " + e.getMessage());
        }
    }

    private void handleErrorMessage(final String message) {
        final String errorMsg = minExtract(message, "error");
        LOGGER.error("Kraken Spot UserData error: " + errorMsg + ", Full message: " + message);
    }
}
