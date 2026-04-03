package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc;

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
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.HMAC;
import com.solfini.util.MbxMath;
import com.solfini.util.TimeUtil;
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

//This class in not in USE
public final class MexcFutureUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(MexcFutureUserDataListener.class);
    // MEXC Futures WebSocket endpoint - updated to correct native endpoint
    private static final String WS_URL_FUTURES = "wss://contract.mexc.com/edge";

    private static final long PING_INTERVAL_SECONDS = 30;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;

    // Immutable configuration - set once in constructor
    private final String apiKey;
    private final String secretKey;
    private final ExchangeSubscription subscription;
    private final EventLoopGroup group = new NioEventLoopGroup();

    // Connection state management - can change during runtime
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();

    // Add protobuf support flag
    private boolean useProtobuf = false; // Start with JSON by default
    private boolean compressionEnabled = false;

    /**
     * Initializes MEXC Futures User Data WebSocket listener
     * @param apiKey MEXC API key for authentication
     * @param secretKey MEXC API secret for HMAC signature generation
     * @param subscription Liquidity subscription for balance and order updates
     */
    public MexcFutureUserDataListener(final String apiKey, final String secretKey, final ExchangeSubscription subscription) {
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

    /**
     * Establishes WebSocket connection to MEXC futures user data stream using native endpoint
     */
    public void connect() throws Exception {
        final URI uri = new URI(WS_URL_FUTURES);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "MEXC-FUTURES-NATIVE-LISTENER");

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

        try {
            final Channel ch = b.connect(host, port).sync().channel();
            this.channel = ch;
            handler.handshakeFuture().sync();

            LOGGER.info("MEXC Futures Native WebSocket connected to endpoint: " + WS_URL_FUTURES);
            authenticate();
        } catch (final Exception e) {
            LOGGER.error("Failed to connect to MEXC Futures Native WebSocket endpoint: " + WS_URL_FUTURES, e);
            throw e;
        }
    }

    /**
     * Gracefully disconnects from WebSocket and shuts down resources
     */
    public void disconnect() {
        stopPing();
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

    /**
     * Authenticates with MEXC Futures native endpoint using correct format
     */
    private void authenticate() throws Exception {
        final long timestamp = System.currentTimeMillis();

        // MEXC Futures native authentication for contract.mexc.com/edge
        // Uses different signature format than REST API
        final String signature = HMAC.hmacSha256(apiKey + timestamp, secretKey);

        final String authMessage = String.format("""
                {
                    "method": "login",
                    "param": {
                        "apiKey": "%s",
                        "reqTime": %d,
                        "signature": "%s"
                    }
                }
                """, apiKey, timestamp, signature);

        LOGGER.debug("Sending MEXC Futures Native auth message");
        channel.writeAndFlush(new TextWebSocketFrame(authMessage));
    }


    /**
     * Called after successful authentication to set up data subscriptions
     */
    private void onAuthSuccess() {
        LOGGER.info("MEXC Futures Native WebSocket authenticated successfully");
        subscribeToStreams();
        schedulePeriodicPing();
    }

    /**
     * Subscribes to futures private data streams using native format
     */
    private void subscribeToStreams() {
        // Subscribe to user data streams for futures
        final String[] streams = {
                "sub.personal.order",
                "sub.personal.asset",
                "sub.personal.position",
                "sub.personal.risk"
        };

        for (String stream : streams) {
            final String subscribeMessage = String.format("""
                    {
                        "method": "%s",
                        "param": {}
                    }
                    """, stream);

            LOGGER.debug("Subscribing to MEXC Futures stream: " + stream);
            channel.writeAndFlush(new TextWebSocketFrame(subscribeMessage));
        }
    }

    /**
     * Starts periodic ping to maintain WebSocket connection
     */
    private void schedulePeriodicPing() {
        stopPing();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                sendPing();
                LOGGER.debug("Sending MEXC Futures Native ping");
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Sends ping using native format
     */
    private void sendPing() {
        try {
            final String pingMessage = """
                    {
                        "method": "ping"
                    }
                    """;
            channel.writeAndFlush(new TextWebSocketFrame(pingMessage));
            LOGGER.debug("Sending MEXC Futures Native ping");
        } catch (final Exception e) {
            LOGGER.error("Failed to send MEXC Futures Native ping: " + e.getMessage());
        }
    }

    /**
     * Stops the periodic ping task
     */
    private void stopPing() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
    }

    /**
     * Reconnects to WebSocket after connection loss
     */
    public void reconnect() {
        stopPing();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        channel.eventLoop().schedule(() -> {
            try {
                LOGGER.info("MEXC UserData WebSocket reconnecting...");
                connect();
            } catch (final Exception e) {
                LOGGER.error("Error during reconnect", e);
                reconnect();
            }
        }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
    }

    /**
     * Main message handler - handles JSON messages only (MEXC doesn't support protobuf)
     * @param message WebSocket message received from MEXC
     */
    public void onMessage(final String message) {
        handleJsonMessage(message);
    }

    /**
     * Handle binary protobuf messages - not supported by MEXC, log warning
     */
    public void onBinaryMessage(final byte[] data) {
        LOGGER.warn("Received unexpected binary message from MEXC (protobuf not supported)");
    }

    /**
     * Handles JSON messages with proper method detection for native futures endpoint
     */
    private void handleJsonMessage(final String message) {
        LOGGER.debug("MEXC Futures Native received JSON: " + message);

        try {
            // Handle authentication responses
            if (message.contains("\"method\":\"login\"") || message.contains("login")) {
                handleAuthResponse(message);
            }
            // Handle subscription confirmations
            else if (message.contains("\"method\":\"sub.") || message.contains("\"channel\":\"")) {
                //todo handle balance update
                //[1760025670401]: [nioEventLoopGroup-2-1][com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc.MexcUserDataListener] MEXC Futures Native subscription confirmed: {"channel":"push.personal.asset","data":{"availableBalance":11.8461,"bonus":0,"currency":"USDT","frozenBalance":0,"positionMargin":0},"ts":1760025670378}
                LOGGER.info("MEXC Futures Native subscription confirmed: " + message);
            }
            // Handle native futures-specific messages
            else if (message.contains("\"channel\":\"push.personal.asset\"") ||
                    message.contains("\"method\":\"push.personal.asset\"")) {
                handleBalanceUpdate(message);
            }
            else if (message.contains("\"channel\":\"push.personal.order\"") ||
                    message.contains("\"method\":\"push.personal.order\"")) {
                handleOrderUpdate(message);
            }
            else if (message.contains("\"channel\":\"push.personal.order.deal\"") ||
                    message.contains("\"method\":\"push.personal.order.deal\"")) {
                handleTradeUpdate(message);
            }
            else if (message.contains("\"channel\":\"push.personal.position\"") ||
                    message.contains("\"method\":\"push.personal.position\"")) {
                handlePositionUpdate(message);
            }
            else if (message.contains("\"method\":\"pong\"") || message.contains("pong")) {
                lastPongReceived = System.currentTimeMillis();
                LOGGER.debug("MEXC Futures Native pong received");
            }
            // Handle error messages
            else if (message.contains("\"code\":") && !message.contains("\"code\":200")) {
                handleErrorResponse(message);
            }
            else {
                LOGGER.debug("Unhandled native JSON message: " + message);
            }
        } catch (final Exception e) {
            LOGGER.error("Error processing MEXC Futures Native JSON message", e);
        }
    }

    /**
     * Handles JSON authentication response for native endpoint
     */
    private void handleAuthResponse(final String message) {
        final String code = minExtract(message, "code");
        final String msg = minExtract(message, "msg");
        final String channel = minExtract(message, "channel");
        final String data = minExtract(message, "data");

        // Check for native MEXC response format: {"channel":"rs.login","data":"success","ts":1760019610895}
        if ("rs.login".equals(channel) && "success".equals(data)) {
            setAuthenticated(true);
            onAuthSuccess();
            LOGGER.info("MEXC Futures Native authentication successful (native format)");
        }
        // Also check for traditional code-based response
        else if ("200".equals(code) || message.contains("\"code\":200")) {
            setAuthenticated(true);
            onAuthSuccess();
            LOGGER.info("MEXC Futures Native authentication successful (code format)");
        } else {
            LOGGER.error("MEXC Futures Native authentication failed - Channel: " + channel +
                    ", Data: " + data + ", Code: " + code + ", Message: " + msg);
            setAuthenticated(false);
        }
    }

    /**
     * Handles error responses from MEXC native endpoint
     */
    private void handleErrorResponse(final String message) {
        final String code = minExtract(message, "code");
        final String msg = minExtract(message, "msg");
        final String errorMsg = minExtract(message, "error");

        LOGGER.error("MEXC Futures Native error - Code: " + code +
                ", Message: " + (msg != null ? msg : errorMsg));

        if ("401".equals(code) || "403".equals(code)) {
            LOGGER.error("MEXC authentication failed - check API credentials");
            setAuthenticated(false);
        }
    }

    /**
     * Handles real-time futures balance updates from native endpoint
     */
    private void handleBalanceUpdate(final String message) {
        try {
            // Native endpoint structure - data is in "data" field
            final String data = minExtract(message, "data");
            if (data != null) {
                // Check if data is an array
                if (data.startsWith("[")) {
                    // Handle array of balance updates
                    final String[] balances = data.substring(1, data.length() - 1).split("\\},\\{");
                    for (String balanceStr : balances) {
                        if (!balanceStr.startsWith("{")) balanceStr = "{" + balanceStr;
                        if (!balanceStr.endsWith("}")) balanceStr = balanceStr + "}";
                        processBalanceData(balanceStr);
                    }
                } else {
                    // Handle single balance update
                    processBalanceData(data);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling MEXC native balance update", e);
        }
    }

    private void processBalanceData(final String balanceData) {
        final String currency = minExtract(balanceData, "currency");
        final String available = minExtract(balanceData, "available");
        final String frozen = minExtract(balanceData, "frozen");
        final String positionMargin = minExtract(balanceData, "positionMargin");
        final String orderMargin = minExtract(balanceData, "orderMargin");

        if (currency != null && available != null) {
            final double availableAmount = parseDoubleSafe(available);
            final double frozenAmount = parseDoubleSafe(frozen);
            final double posMargin = parseDoubleSafe(positionMargin);
            final double ordMargin = parseDoubleSafe(orderMargin);

            final double totalBalance = availableAmount + frozenAmount + posMargin + ordMargin;
            subscription.updatePosition(currency, totalBalance);

            LOGGER.debug("Updated MEXC Native balance: " + currency +
                    " total=" + totalBalance +
                    " available=" + availableAmount +
                    " frozen=" + frozenAmount +
                    " posMargin=" + posMargin +
                    " orderMargin=" + ordMargin);
        }
    }

    /**
     * Handles real-time futures order status updates from native endpoint
     */
    private void handleOrderUpdate(final String message) {
        try {
            final String data = minExtract(message, "data");
            if (data != null) {
                final String clientOrderId = minExtract(data, "clientOrderId");
                final String symbol = minExtract(data, "symbol");
                final String side = minExtract(data, "side");
                final String orderType = minExtract(data, "orderType");
                final String state = minExtract(data, "state");
                final String vol = minExtract(data, "vol");
                final String price = minExtract(data, "price");
                final String dealVol = minExtract(data, "dealVol");
                final String avgPrice = minExtract(data, "avgPrice");
                final String fee = minExtract(data, "fee");
                final String feeCurrency = minExtract(data, "feeCurrency");
                final String positionMode = minExtract(data, "positionMode");
                final String positionSide = minExtract(data, "positionSide");
                final String reduceOnly = minExtract(data, "reduceOnly");

                // Process native order update
                processOrderUpdate(clientOrderId, symbol, state, parseDoubleSafe(dealVol),
                        parseDoubleSafe(avgPrice), parseDoubleSafe(fee), feeCurrency,
                        positionSide, "true".equalsIgnoreCase(reduceOnly));
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling MEXC native order update", e);
        }
    }

    /**
     * Handles real-time futures trade/execution updates from native endpoint
     */
    private void handleTradeUpdate(final String message) {
        try {
            final String data = minExtract(message, "data");
            if (data != null) {
                final String symbol = minExtract(data, "symbol");
                final String orderId = minExtract(data, "orderId");
                final String clientOrderId = minExtract(data, "clientOrderId");
                final String side = minExtract(data, "side");
                final String orderType = minExtract(data, "orderType");
                final String vol = minExtract(data, "vol");
                final String price = minExtract(data, "price");
                final String fee = minExtract(data, "fee");
                final String feeCurrency = minExtract(data, "feeCurrency");
                final String dealId = minExtract(data, "dealId");
                final String isMaker = minExtract(data, "isMaker");
                final String dealTime = minExtract(data, "dealTime");
                final String positionSide = minExtract(data, "positionSide");
                final String reduceOnly = minExtract(data, "reduceOnly");
                final String realizedPnl = minExtract(data, "realizedPnl");

                // Process native trade update
                processTradeUpdate(symbol, orderId, clientOrderId, side, orderType, parseDoubleSafe(vol),
                        parseDoubleSafe(price), parseDoubleSafe(fee), feeCurrency, dealId,
                        "true".equalsIgnoreCase(isMaker), parseLongSafe(dealTime), positionSide,
                        "true".equalsIgnoreCase(reduceOnly), parseDoubleSafe(realizedPnl));
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling MEXC native trade update", e);
        }
    }

    /**
     * Handles real-time position updates from native endpoint
     */
    private void handlePositionUpdate(final String message) {
        try {
            final String data = minExtract(message, "data");
            if (data != null) {
                // Check if data is an array of positions
                if (data.startsWith("[")) {
                    final String[] positions = data.substring(1, data.length() - 1).split("\\},\\{");
                    for (String posStr : positions) {
                        if (!posStr.startsWith("{")) posStr = "{" + posStr;
                        if (!posStr.endsWith("}")) posStr = posStr + "}";
                        processPositionData(posStr);
                    }
                } else {
                    processPositionData(data);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling MEXC native position update", e);
        }
    }

    private void processPositionData(final String positionData) {
        final String symbol = minExtract(positionData, "symbol");
        final String positionType = minExtract(positionData, "positionType");
        final String holdVol = minExtract(positionData, "holdVol");
        final String holdAvgPrice = minExtract(positionData, "holdAvgPrice");
        final String positionValue = minExtract(positionData, "positionValue");
        final String unrealizedPnl = minExtract(positionData, "unrealizedPnl");
        final String realizedPnl = minExtract(positionData, "realizedPnl");
        final String leverage = minExtract(positionData, "leverage");
        final String marginMode = minExtract(positionData, "marginMode");

        if (symbol != null) {
            final double holdVolume = parseDoubleSafe(holdVol);
            final double avgPrice = parseDoubleSafe(holdAvgPrice);
            final double posValue = parseDoubleSafe(positionValue);
            final double unrealizedPnlDouble = parseDoubleSafe(unrealizedPnl);
            final double leverageDouble = parseDoubleSafe(leverage);

            // Update subscription with native position data
            subscription.updatePosition(symbol, holdVolume);

            LOGGER.debug("Updated MEXC Native position: " + symbol +
                    " holdVol=" + holdVolume +
                    " avgPrice=" + avgPrice +
                    " posValue=" + posValue +
                    " unrealizedPnl=" + unrealizedPnlDouble +
                    " positionType=" + positionType +
                    " leverage=" + leverageDouble +
                    " marginMode=" + marginMode);

            // Remove position if volume is zero
            if (holdVolume == 0) {
                subscription.updatePosition(symbol, holdVolume);
                LOGGER.debug("Removed closed MEXC Native position: " + symbol);
            }
        }
    }

    /**
     * Fallback method - not needed since we start with JSON
     */
    public void fallbackToJson() {
        // Already using JSON, no fallback needed
        LOGGER.info("Already using JSON protocol for MEXC Futures WebSocket");
    }

    // Extract common futures order processing logic
    private void processOrderUpdate(String clientOrderId, String symbol, String status, double executedQty,
                                    double avgPrice, double commission, String commissionAsset,
                                    String positionSide, boolean reduceOnly) {
        try {
            // Check for exchange-triggered events
            final boolean isExchangeTriggered = detectExchangeTriggeredEvent(clientOrderId, status, "order_data");
            if (isExchangeTriggered) {
                LOGGER.warn("EXCHANGE-TRIGGERED ORDER UPDATE DETECTED [FUTURES] - Symbol: " + symbol +
                        ", ClientOrderId: " + clientOrderId + ", Status: " + status +
                        ", PositionSide: " + positionSide + ", ReduceOnly: " + reduceOnly);
            }

            if (clientOrderId == null || status == null) return;

            final Order order = subscription.getOrder(clientOrderId);
            if (order == null) {
                LOGGER.warn("Order not found in cache: " + clientOrderId);
                return;
            }

            final OrdStatus orderStatus = getMexcOrderStatus(status);
            final long executedQtyLong = MbxMath.changeScale(executedQty, order.getQtyScale());
            final long avgPriceLong = MbxMath.changeScale(avgPrice, order.getPriceScale());

            final Instrument fee = InstrumentCache.getBySymbol(commissionAsset == null || commissionAsset.isBlank() ?
                    getDefaultQuotedCurrency(symbol) : commissionAsset);
            long commissionLong = 0;

            // Update order data for filled orders
            if (OrdStatus.FILLED.equals(orderStatus)) {
                order.setAvailableAccumulatedQuantity(executedQtyLong);
                order.setPrice(avgPriceLong, order.getPriceScale());
                if (fee != null) {
                    commissionLong = MbxMath.changeScale(commission, fee.getQuantityScale());
                    order.setFeeAccumulatedQuantity(commissionLong);
                    order.setAssetId(fee.getId());
                }
            }

            // Create or update execution report
            ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
            if (executionMessage == null) {
                executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                        order.getOrderId(),
                        order.getUser(),
                        0,
                        symbol,
                        avgPriceLong,
                        order.getPriceScale(),
                        executedQtyLong,
                        order.getQtyScale(),
                        0,
                        0,
                        0,
                        order.getTargetStrategy(),
                        order.getSide(),
                        commissionLong
                );
                executionMessage.setClOrdId(order.getClOrdId());
                executionMessage.setPrice(order.getPrice());
                executionMessage.setPriceScale(order.getPriceScale());
                executionMessage.setOrderQty(order.getQty());
                executionMessage.setOrderQtyScale(order.getQtyScale());
                executionMessage.setOrdStatus(orderStatus);
                executionMessage.setOrdType(order.getOrdType());
                executionMessage.setMatchTime(TimeUtil.getTime());
            } else {
                // Update existing execution report
                executionMessage.setAvgPx(avgPriceLong);
                executionMessage.setCumQty(executedQtyLong);
                executionMessage.setFeeAccumulatedQuantity(commissionLong);
                if (fee != null) {
                    executionMessage.setFeePositionId(fee.getId());
                }
                executionMessage.setOrdStatus(orderStatus);
                executionMessage.setTargetStrategy(order.getTargetStrategy());
                executionMessage.setOrdType(order.getOrdType());
                executionMessage.setMatchTime(TimeUtil.getTime());
            }

            // Handle canceled/rejected orders
            if (OrdStatus.CANCELED.equals(orderStatus) || OrdStatus.REJECTED.equals(orderStatus)) {
                executionMessage.setOrderQty(order.getQty());
                executionMessage.setOrderQtyScale(order.getQtyScale());
                executionMessage.setPrice(order.getPrice());
                executionMessage.setPriceScale(order.getPriceScale());
                executionMessage.setClOrdId(clientOrderId);
                executionMessage.setExecType(ExecType.TRADE);
                executionMessage.setOrdType(order.getOrdType());
                executionMessage.setTimeInForce(order.getTimeInForce());
                executionMessage.setOrdStatus(orderStatus);
            }

            subscription.updateExecutionReport(executionMessage);
            subscription.updateOrder(clientOrderId, status);
        } catch (final Exception e) {
            LOGGER.error("Error processing futures order update", e);
        }
    }

    // Extract common futures trade processing logic
    private void processTradeUpdate(String symbol, String orderId, String clientOrderId, String side,
                                    String type, double quantity, double price, double commission,
                                    String commissionAsset, String tradeId, boolean isMaker, long time,
                                    String positionSide, boolean reduceOnly, double realizedPnl) {
        try {
            // Check for exchange-triggered executions
            final boolean isExchangeTriggered = detectExchangeTriggeredEvent(clientOrderId, null, "trade_data");
            if (isExchangeTriggered) {
                LOGGER.warn("EXCHANGE-TRIGGERED EXECUTION DETECTED [FUTURES] - Symbol: " + symbol +
                        ", ClientOrderId: " + clientOrderId + ", Quantity: " + quantity +
                        ", Price: " + price + ", PositionSide: " + positionSide +
                        ", RealizedPnl: " + realizedPnl);
            }

            if (clientOrderId == null || symbol == null) {
                return;
            }

            LOGGER.debug("MEXC FUTURES EXECUTION REPORT >> Symbol: " + symbol +
                    ", ClientOrderId: " + clientOrderId +
                    ", Quantity: " + quantity +
                    ", Price: " + price +
                    ", PositionSide: " + positionSide +
                    ", ReduceOnly: " + reduceOnly +
                    ", RealizedPnl: " + realizedPnl);

            final Order order = subscription.getOrder(clientOrderId);
            if (order == null) {
                LOGGER.warn("Order not found in cache: " + clientOrderId);
                return;
            }

            final long orderIdLong = parseLongSafe(orderId);
            final long tradeIdLong = parseLongSafe(tradeId);

            final Side sideObj = "BUY".equalsIgnoreCase(side) ? Side.BUY : Side.SELL;
            final OrdType orderType = getMexcOrderType(type);

            final long priceLong = MbxMath.changeScale(price, order.getPriceScale());
            final long quantityLong = MbxMath.changeScale(quantity, order.getQtyScale());

            final Instrument feesInstrument = InstrumentCache.getBySymbol(commissionAsset == null || commissionAsset.isBlank() ?
                    getDefaultQuotedCurrency(symbol) : commissionAsset);
            final long feesLong = feesInstrument != null ? MbxMath.changeScale(commission, feesInstrument.getQuantityScale()) : 0;

            ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
            if (executionMessage == null) {
                executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                        orderIdLong,
                        order.getUser(),
                        0,
                        symbol,
                        priceLong,
                        order.getPriceScale(),
                        quantityLong,
                        order.getQtyScale(),
                        tradeIdLong,
                        0,
                        0,
                        0,
                        sideObj,
                        feesLong
                );
            }

            executionMessage.setOrderQty(order.getQty());
            executionMessage.setOrderQtyScale(order.getQtyScale());
            executionMessage.setPrice(order.getPrice());
            executionMessage.setPriceScale(order.getPriceScale());
            executionMessage.setClOrdId(clientOrderId);
            executionMessage.setExecType(ExecType.TRADE);
            executionMessage.setOrdType(orderType);
            executionMessage.setTimeInForce(order.getTimeInForce());
            executionMessage.setInputTime(time);

            // Set aggressor side based on maker/taker flag
            if (isMaker) {
                if (Side.BUY.equals(order.getSide())) {
                    executionMessage.setAggressorSide(Side.SELL);
                } else {
                    executionMessage.setAggressorSide(Side.BUY);
                }
            } else {
                executionMessage.setAggressorSide(order.getSide());
            }

            subscription.updateExecutionReport(executionMessage);

            LOGGER.debug("MEXC FUTURES execution processed: " + clientOrderId +
                    " qty: " + quantity +
                    " price: " + price);
        } catch (final Exception e) {
            LOGGER.error("Error processing futures trade update", e);
        }
    }

    // Helper method to determine if symbol is futures
    private boolean isFuturesSymbol(final String symbol) {
        if (symbol == null) return false;
        return symbol.contains("_PERP") ||
                symbol.contains("USDT-SWAP") ||
                symbol.contains("USD-SWAP") ||
                symbol.matches(".*\\d{6}$");
    }


    /**
     * Detects if an order/execution is triggered by exchange events like liquidation or margin call
     */
    private boolean detectExchangeTriggeredEvent(final String clientOrderId, final String status, final String fullData) {
        if (clientOrderId == null) return false;

        // Liquidation Detection
        if (clientOrderId.toLowerCase().contains("liquidation") ||
                clientOrderId.toLowerCase().contains("liq") ||
                clientOrderId.toLowerCase().startsWith("liq_")) {
            return true;
        }

        // Margin Call Detection
        if (clientOrderId.toLowerCase().contains("margin") ||
                clientOrderId.toLowerCase().contains("forced") ||
                clientOrderId.toLowerCase().contains("auto")) {
            return true;
        }

        // Check status for forced closures
        if ("EXPIRED".equals(status) || "FORCE_LIQUIDATED".equals(status)) {
            return true;
        }

        return false;
    }

    /**
     * Returns default quote currency for futures
     */
    private String getDefaultQuotedCurrency(final String symbol) {
        if (symbol != null && symbol.contains("USDC")) {
            return "USDC";
        } else {
            return "USDT";
        }
    }

    /**
     * Converts MEXC native order status to internal OrdStatus enum
     */
    private OrdStatus getMexcOrderStatus(final String status) {
        if (status == null) return OrdStatus.NEW;

        return switch (status) {
            case "1" -> OrdStatus.NEW;              // New
            case "2" -> OrdStatus.PARTIALLY_FILLED; // Partially filled
            case "3" -> OrdStatus.FILLED;           // Fully filled
            case "4" -> OrdStatus.CANCELED;         // Canceled
            case "5" -> OrdStatus.REJECTED;         // Rejected
            case "6" -> OrdStatus.CANCELED;         // Expired
            default -> {
                LOGGER.warn("Unknown MEXC native order status: " + status + ", defaulting to NEW");
                yield OrdStatus.NEW;
            }
        };
    }

    /**
     * Converts MEXC native order type to internal OrdType enum
     */
    private OrdType getMexcOrderType(final String type) {
        if (type == null) return OrdType.LIMIT;

        return switch (type) {
            case "1" -> OrdType.LIMIT;   // Limit order
            case "2" -> OrdType.MARKET;  // Market order
            case "3" -> OrdType.LIMIT;   // Stop limit
            case "4" -> OrdType.MARKET;  // Stop market
            default -> OrdType.LIMIT;
        };
    }

    // Add missing helper method
    private long parseLongSafe(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException e) {
            LOGGER.warn("Failed to parse long value: " + value);
            return 0L;
        }
    }
}

