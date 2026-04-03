package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper;
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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public final class BybitUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(BybitUserDataListener.class);
    private static final String WS_URL = Context.getBybitUnifiedWs() + "/private";
    private static final long PING_INTERVAL_SECONDS = 25;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;
    private static final int PROXY_PORT = 8888;
    
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

    /**
     * Initializes ByBit User Data WebSocket listener
     * @param apiKey ByBit API key for authentication
     * @param secretKey ByBit API secret for HMAC signature generation
     * @param subscription Liquidity subscription for balance and order updates
     */
    public BybitUserDataListener(final String apiKey, final String secretKey, final ExchangeSubscription subscription) {
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

    /**
     * Establishes WebSocket connection to ByBit private stream
     * Sets up SSL context, handshaker, and connection pipeline
     */
    public void connect() throws Exception {
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();
      final String proxyHost = subscription.getLastUsedProxy();
      final int proxyPort = PROXY_PORT;

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "BYBIT-USER-DATA-LISTENER");

        final Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .localAddress(new InetSocketAddress(Context.getOutboundIp(), 0))
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
/*                      if (proxyHost != null) {
                        p.addLast(new HttpProxyHandler(new InetSocketAddress(proxyHost, proxyPort)));
                      }*/
                        p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                        p.addLast(new HttpClientCodec());
                        p.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                        p.addLast(handler);
                    }
                });

          this.channel = b.connect(host, port).sync().channel();
        handler.handshakeFuture().sync();

        LOGGER.info("ByBit UserData WebSocket connected");
        authenticate();
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
     * Authenticates with ByBit using API credentials and HMAC signature
     * Required before subscribing to private data streams
     */
    private void authenticate() throws Exception {
        final long timestamp = System.currentTimeMillis();
        final String expires = String.valueOf(timestamp + 10000);
        final String payload = "GET/realtime" + expires;
        final String signature = HMAC.hmacSha256(payload, secretKey);

        final String authMessage = String.format("""
                {
                    "op": "auth",
                    "args": ["%s", %s, "%s"]
                }
                """, apiKey, expires, signature);

        LOGGER.debug("Sending ByBit UserData auth: " + authMessage);
        channel.writeAndFlush(new TextWebSocketFrame(authMessage));
    }

    /**
     * Called after successful authentication to set up data subscriptions
     */
    private void onAuthSuccess() {
        LOGGER.info("ByBit UserData WebSocket authenticated successfully");
        subscribeToStreams();
        schedulePeriodicPing();
    }

    /**
     * Subscribes to private data streams: wallet, order, execution, position
     */
    private void subscribeToStreams() {
        final String subscribeMessage = """
                {
                    "op": "subscribe",
                    "args": ["wallet", "order", "execution", "position"]
                }
                """;

        LOGGER.debug("Subscribing to ByBit UserData streams");
        channel.writeAndFlush(new TextWebSocketFrame(subscribeMessage));
    }

    /**
     * Starts periodic ping to maintain WebSocket connection
     */
    private void schedulePeriodicPing() {
        stopPing();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                channel.writeAndFlush(new PingWebSocketFrame());
                LOGGER.debug("Sending ByBit UserData ping");
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
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
                LOGGER.info("ByBit UserData WebSocket reconnecting...");
                connect();
            } catch (final Exception e) {
                LOGGER.error("Error during reconnect", e);
                reconnect();
            }
        }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
    }

    /**
     * Main message handler - routes messages to appropriate handlers
     * @param message WebSocket message received from ByBit
     */
    public void onMessage(final String message) {
        LOGGER.debug(Constants.LOG_FMT_4, "SubscriptionId: ", subscription.getId(), " ByBit UserData received: ", message);

        try {
            if (message.contains("\"op\":\"auth\"")) {
                handleAuthResponse(message);
            } else if (message.contains("\"topic\":\"wallet\"")) {
                handleWalletUpdate(message);
            } else if (message.contains("\"topic\":\"order\"")) {
                handleOrderUpdate(message);
            } else if (message.contains("\"topic\":\"execution\"")) {
                handleExecutionUpdate(message);
            } else if (message.contains("\"topic\":\"position\"")) {
                handlePositionUpdate(message);
            } else {
                LOGGER.debug("Unhandled message: " + message);
            }
        } catch (final Exception e) {
            LOGGER.error("Error processing ByBit UserData message", e);
        }
    }

    /**
     * Handles real-time position updates from ByBit
     * Updates local position cache with new size and mark price data
     */
    private void handlePositionUpdate(final String message) {
        try {
            final int dataStart = message.indexOf("\"data\":");
            if (dataStart >= 0) {
                final int dataArrayStart = message.indexOf('[', dataStart);
                final int dataArrayEnd = message.indexOf(']', dataArrayStart);
                if (dataArrayStart >= 0 && dataArrayEnd >= 0) {
                    final String dataArray = message.substring(dataArrayStart + 1, dataArrayEnd);

                    // Handle multiple positions in the array
                    final String[] positions = dataArray.split("\\},\\{");
                    for (String positionData : positions) {
                        // Clean up the position data
                        positionData = positionData.replace("{", "").replace("}", "");

                        final String symbol = minExtract(positionData, "symbol");
                        final String side = minExtract(positionData, "side");
                        final String size = minExtract(positionData, "size");
                        final String markPrice = minExtract(positionData, "markPrice");


                        if (symbol != null && size != null) {
                            double qty = parseDoubleSafe(size);
                            if ("Sell".equalsIgnoreCase(side)) {
                              qty = -Math.abs(qty);
                            }
                            final double markPriceDouble = parseDoubleSafe(markPrice);

                            // Update position in local cache
                            if (markPriceDouble == 0) {
                                subscription.updatePosition(symbol, qty);
                            } else {
                                subscription.updatePosition(symbol, qty, markPriceDouble);
                            }

                            LOGGER.debug("Updated ByBit position - Symbol: " + symbol + ", Size: " + size);
                        }
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling ByBit position update", e);
        }
    }

    /**
     * Handles authentication response from ByBit WebSocket
     */
    private void handleAuthResponse(final String message) {
        final String success = minExtract(message, "success");
        final String op = minExtract(message, "op");

        if ("true".equals(success) && "auth".equals(op)) {
            final String connId = minExtract(message, "conn_id");
            LOGGER.info("ByBit UserData WebSocket authenticated successfully - Connection ID: " + connId);
            onAuthSuccess();
        } else {
            final String retMsg = minExtract(message, "ret_msg");
            LOGGER.error("ByBit UserData WebSocket authentication failed - Message: " + retMsg);
        }
    }

    /**
     * Handles real-time wallet balance updates from ByBit
     * Parses coin arrays and updates local balance cache
     */
    private void handleWalletUpdate(final String message) {
      LOGGER.info("ByBit Balance: " + message);
        try {
            final int dataStart = message.indexOf("\"data\":[");
            if (dataStart >= 0) {
                // Find the data array using proper JSON parsing
                int dataArrayEnd = -1;
                int bracketDepth = 0;
                boolean inString = false;
                boolean escapeNext = false;

                for (int i = dataStart + 8; i < message.length(); i++) {
                    final char c = message.charAt(i);

                    if (escapeNext) {
                        escapeNext = false;
                        continue;
                    }

                    if (c == '\\') {
                        escapeNext = true;
                        continue;
                    }

                    if (c == '"') {
                        inString = !inString;
                        continue;
                    }

                    if (!inString) {
                        if (c == '[') {
                            bracketDepth++;
                        } else if (c == ']') {
                            if (bracketDepth == 0) {
                                dataArrayEnd = i;
                                break;
                            } else {
                                bracketDepth--;
                            }
                        }
                    }
                }

                if (dataArrayEnd > dataStart) {
                    final String dataArray = message.substring(dataStart + 8, dataArrayEnd);

                    // Find the first object in data array
                    final int objStart = dataArray.indexOf('{');
                    if (objStart >= 0) {
                        // Find matching closing brace for the data object
                        int objEnd = -1;
                        int braceDepth = 0;
                        boolean objInString = false;
                        boolean objEscapeNext = false;

                        for (int i = objStart; i < dataArray.length(); i++) {
                            final char c = dataArray.charAt(i);

                            if (objEscapeNext) {
                                objEscapeNext = false;
                                continue;
                            }

                            if (c == '\\') {
                                objEscapeNext = true;
                                continue;
                            }

                            if (c == '"') {
                                objInString = !objInString;
                                continue;
                            }

                            if (!objInString) {
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
                        }

                        if (objEnd > objStart) {
                            final String dataObj = dataArray.substring(objStart, objEnd + 1);

                            // Parse coin array within the data object
                            final int coinStart = dataObj.indexOf("\"coin\":[");
                            if (coinStart >= 0) {
                                // Find the closing bracket for the coin array
                                int coinArrEnd = -1;
                                int coinBracketDepth = 0;
                                boolean coinInString = false;
                                boolean coinEscapeNext = false;

                                for (int i = coinStart + 8; i < dataObj.length(); i++) {
                                    final char c = dataObj.charAt(i);

                                    if (coinEscapeNext) {
                                        coinEscapeNext = false;
                                        continue;
                                    }

                                    if (c == '\\') {
                                        coinEscapeNext = true;
                                        continue;
                                    }

                                    if (c == '"') {
                                        coinInString = !coinInString;
                                        continue;
                                    }

                                    if (!coinInString) {
                                        if (c == '[') {
                                            coinBracketDepth++;
                                        } else if (c == ']') {
                                            if (coinBracketDepth == 0) {
                                                coinArrEnd = i;
                                                break;
                                            } else {
                                                coinBracketDepth--;
                                            }
                                        }
                                    }
                                }

                                if (coinArrEnd > coinStart) {
                                    final String coinArr = dataObj.substring(coinStart + 8, coinArrEnd);
                                    LOGGER.debug("Processing coin array from wallet update");

                                    // Parse each coin object
                                    int p = 0;
                                    while (p >= 0 && p < coinArr.length()) {
                                        final int coinObjStart = coinArr.indexOf('{', p);
                                        if (coinObjStart < 0) break;

                                        // Find matching closing brace for coin object
                                        int coinObjEnd = -1;
                                        int coinBraceDepth = 0;
                                        boolean coinObjInString = false;
                                        boolean coinObjEscapeNext = false;

                                        for (int i = coinObjStart; i < coinArr.length(); i++) {
                                            final char c = coinArr.charAt(i);

                                            if (coinObjEscapeNext) {
                                                coinObjEscapeNext = false;
                                                continue;
                                            }

                                            if (c == '\\') {
                                                coinObjEscapeNext = true;
                                                continue;
                                            }

                                            if (c == '"') {
                                                coinObjInString = !coinObjInString;
                                                continue;
                                            }

                                            if (!coinObjInString) {
                                                if (c == '{') {
                                                    coinBraceDepth++;
                                                } else if (c == '}') {
                                                    coinBraceDepth--;
                                                    if (coinBraceDepth == 0) {
                                                        coinObjEnd = i;
                                                        break;
                                                    }
                                                }
                                            }
                                        }

                                        if (coinObjEnd > coinObjStart) {
                                            final String coinObj = coinArr.substring(coinObjStart, coinObjEnd + 1);
                                            final String coinName = minExtract(coinObj, "coin");
                                            final double walletBalance = parseDoubleSafe(minExtract(coinObj, "walletBalance"));
                                            final double equity = parseDoubleSafe(minExtract(coinObj, "equity"));
                                            final double unrealisedPnl = parseDoubleSafe(minExtract(coinObj, "unrealisedPnl"));

                                            if (coinName != null /*&& walletBalance > 0*/) {// negative balances for shorting
                                                subscription.updateBalance(coinName, walletBalance);
/*                                                LOGGER.debug("Updated ByBit wallet balance via stream: " + coinName +
                                                        " walletBalance=" + walletBalance + " equity=" + equity +
                                                        " unrealisedPnl=" + unrealisedPnl);*/
                                            }
                                            p = coinObjEnd + 1;
                                        } else {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling ByBit wallet update", e);
        }
    }

    /**
     * Handles real-time order status updates from ByBit
     * Updates order status and creates execution reports for status changes
     */
    private void handleOrderUpdate(final String message) {
        try {
            final int dataStart = message.indexOf("\"data\":");
            if (dataStart >= 0) {
                final int dataEnd = message.indexOf('}', dataStart);
                final String data = message.substring(dataStart + 7, dataEnd + 1);

                final String orderLinkId = minExtract(data, "orderLinkId");
                final String orderStatusStr = minExtract(data, "orderStatus");
                final String cumExecQtyStr = minExtract(data, "cumExecQty");
                final String avgPriceStr = minExtract(data, "avgPrice");
                final String cumExecFeeStr = minExtract(data, "cumExecFee");
                final String feeCurrency = minExtract(data, "feeCurrency");
                final String rejectReason = minExtract(data, "rejectReason");
                final String reduceOnly = minExtract(data, "reduceOnly");
                final String closedPnl = minExtract(data, "closedPnl");

                // Check for exchange-triggered events (ADL, Liquidation, Margin Call)
                final boolean isExchangeTriggered = detectExchangeTriggeredEvent(orderLinkId, rejectReason, reduceOnly, data);
                if (isExchangeTriggered) {
                    LOGGER.warn("EXCHANGE-TRIGGERED ORDER UPDATE DETECTED - OrderLinkId: " + orderLinkId +
                               ", Status: " + orderStatusStr + ", RejectReason: " + rejectReason + 
                               ", ReduceOnly: " + reduceOnly + ", Full Message: " + message);
                }

                // Validate required fields
                if (orderLinkId == null || orderStatusStr == null)
                    return;

                // Get order from cache
                final Order order = subscription.getOrder(orderLinkId);
                if (order == null) {
                    LOGGER.warn(Constants.LOG_FMT_4, "Order not found in cache: ", orderLinkId, " SubscriptionId: ", subscription.getId());
                    for(Order o : subscription.getOrders().values()) {
                      LOGGER.info(o.toJSON());
                    }
                    return;
                }
                LOGGER.warn(Constants.LOG_FMT_4, "Order found in cache: ", orderLinkId, " SubscriptionId: ", subscription.getId());
                final OrdStatus orderStatus = getByBitOrderStatus(orderStatusStr);
                final double cumExecQtyDouble = JsonHelper.parseDoubleSafe(cumExecQtyStr);
                final double avgPriceDouble = JsonHelper.parseDoubleSafe(avgPriceStr);
                final double cumExecFeeDouble = JsonHelper.parseDoubleSafe(cumExecFeeStr);

                final long cumExecQtyLong = MbxMath.changeScaleWithRounding(cumExecQtyDouble, order.getQtyScale());
                final long avgPriceLong = MbxMath.changeScaleWithRounding(avgPriceDouble, order.getPriceScale());

                final Instrument fee = InstrumentCache.getBySymbol(feeCurrency == null || feeCurrency.isBlank() ? getDefaultQuotedCurrency(order.getSymbol()) : feeCurrency);
                long cumExecFeeLong = 0;

                // Update order data for filled orders
                if (OrdStatus.FILLED.equals(orderStatus)) {
                    order.setAvailableAccumulatedQuantity(cumExecQtyLong);
                    order.setPrice(avgPriceLong, order.getPriceScale());
                    if (fee != null) {
                        cumExecFeeLong = MbxMath.changeScale(cumExecFeeDouble, fee.getQuantityScale());
                        order.setFeeAccumulatedQuantity(cumExecFeeLong);
                    }
                }

                // Create or update execution report
                ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionMessage == null) {
                    // Create execution report message
                    executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                            order.getOrderId(),
                            order.getUser(),
                            0,
                            order.getSymbol(),
                            avgPriceLong,
                            order.getPriceScale(),
                            cumExecQtyLong,
                            order.getQtyScale(),
                            0,
                            0,
                            0,
                            order.getTargetStrategy(),
                            order.getSide(),
                            cumExecFeeLong
                    );
                    executionMessage.setClOrdId(order.getClOrdId());
                    executionMessage.setPrice(order.getPrice());
                    executionMessage.setPriceScale(order.getPriceScale());
                    executionMessage.setOrderQty(order.getQty());
                    executionMessage.setOrderQtyScale(order.getQtyScale());
                    executionMessage.setOrdStatus(orderStatus);
                    executionMessage.setOrdType(order.getOrdType());
                    executionMessage.setMatchTime(TimeUtil.getTime());
                    if (fee != null) {
                        executionMessage.setFeePositionId(fee.getId());
                    }
                } else {
                    // Update existing execution report
                    executionMessage.setAvgPx(avgPriceLong);
                    executionMessage.setCumQty(cumExecQtyLong);
                    executionMessage.setFeeAccumulatedQuantity(cumExecFeeLong);
                    if (fee != null) {
                        executionMessage.setFeePositionId(fee.getId());
                    }
                    executionMessage.setOrdStatus(orderStatus);
                    executionMessage.setTargetStrategy(order.getTargetStrategy());
                    executionMessage.setOrdType(order.getOrdType());
                    executionMessage.setMatchTime(TimeUtil.getTime());
                }

                // Handle canceled/rejected orders
                if (OrdStatus.CANCELED.equals(orderStatus)) {
                    executionMessage.setOrderQty(order.getQty());
                    executionMessage.setOrderQtyScale(order.getQtyScale());
                    executionMessage.setPrice(order.getPrice());
                    executionMessage.setPriceScale(order.getPriceScale());
                    executionMessage.setClOrdId(orderLinkId);
                    executionMessage.setExecType(ExecType.TRADE);
                    executionMessage.setOrdType(order.getOrdType());
                    executionMessage.setTimeInForce(order.getTimeInForce());
                    executionMessage.setOrdStatus(orderStatus);
                    executionMessage.setError(rejectReason);

                    LOGGER.debug("ByBit execution processed for Failed Order: " + orderLinkId +
                            " order Status: " + orderStatusStr +
                            " reason: " + rejectReason);
                }

                // Update execution report and order status
                subscription.updateExecutionReport(executionMessage);
                subscription.updateOrder(orderLinkId, orderStatusStr);
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling ByBit Spot order update", e);
        }
    }

    /**
     * Handles real-time execution reports from ByBit
     * Processes individual trade executions and updates execution reports
     */
    private void handleExecutionUpdate(final String message) {
        try {
            final int dataStart = message.indexOf("\"data\":");
            if (dataStart >= 0) {
                final int dataArrayStart = message.indexOf('[', dataStart);
                final int dataArrayEnd = message.indexOf(']', dataArrayStart);
                if (dataArrayStart >= 0 && dataArrayEnd >= 0) {
                    final String dataArray = message.substring(dataArrayStart + 1, dataArrayEnd);

                    // Handle multiple executions in the array
                    final String[] executions = dataArray.split("\\},\\{");
                    for (String executionData : executions) {
                        // Clean up the execution data
                        executionData = executionData.replace("{", "").replace("}", "");

                        final String orderLinkId = minExtract(executionData, "orderLinkId");
                        final String symbolStr = minExtract(executionData, "symbol");
                        final String sideStr = minExtract(executionData, "side");
                        final String orderTypeStr = minExtract(executionData, "orderType");
                        final String execQtyStr = minExtract(executionData, "execQty");
                        final String execPriceStr = minExtract(executionData, "execPrice");
                        final String execIdStr = minExtract(executionData, "execId");
                        final String execFeeStr = minExtract(executionData, "execFee");
                        final String feeCurrencyStr = minExtract(executionData, "feeCurrency");
                        final String isMaker = minExtract(executionData, "isMaker");
                        final String execTime = minExtract(executionData, "execTime");

                        // Check for exchange-triggered executions (ADL, Liquidation)
                        final boolean isExchangeTriggered = detectExchangeTriggeredEvent(orderLinkId, null, null, executionData);
                        if (isExchangeTriggered) {
                            LOGGER.warn("EXCHANGE-TRIGGERED EXECUTION DETECTED- Symbol: " + symbolStr +
                                       ", OrderLinkId: " + orderLinkId + ", ExecQty: " + execQtyStr + 
                                       ", ExecPrice: " + execPriceStr + ", Full Message: " + message);
                        }

                        // Validate required fields
                        if (orderLinkId == null || symbolStr == null || execQtyStr == null || execPriceStr == null) {
                            continue;
                        }

                        LOGGER.debug("BYBIT EXECUTION REPORT >> Symbol: " + symbolStr +
                                ", OrderLinkId: " + orderLinkId +
                                ", ExecQty: " + execQtyStr +
                                ", ExecPrice: " + execPriceStr);

                        // Get order from cache
                        final Order order = subscription.getOrder(orderLinkId);
                        if (order == null) {
                            LOGGER.warn("Order not found in cache: " + orderLinkId);
                          for(Order o : subscription.getOrders().values()) {
                            LOGGER.info(o.toJSON());
                          }
                            continue;
                        }

                        final long execId = parseLongSafe(execIdStr);
                        final long execTimeLong = parseLongSafe(execTime);

                        final Side sideObj = "Buy".equalsIgnoreCase(sideStr) ? Side.BUY : Side.SELL;
                        final OrdType orderType = getBybitOrderType(orderTypeStr);
                        final double execPriceDouble = parseDoubleSafe(execPriceStr);
                        final double execQtyDouble = parseDoubleSafe(execQtyStr);
                        final double execFeeDouble = parseDoubleSafe(execFeeStr);

                        final long execPriceLong = MbxMath.changeScale(execPriceDouble, order.getPriceScale());
                        final long execQtyLong = MbxMath.changeScale(execQtyDouble, order.getQtyScale());

                        // Convert commission with proper instrument scale
                        final Instrument feesInstrument = InstrumentCache.getBySymbol(feeCurrencyStr == null || feeCurrencyStr.isBlank() ? getDefaultQuotedCurrency(order.getSymbol()) : feeCurrencyStr);
                        final long feesLong = MbxMath.changeScale(execFeeDouble, feesInstrument.getQuantityScale());

                        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
                        if (executionMessage == null) {
                            // Create execution report message
                            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                                    order.getOrderId(),
                                    order.getUser(),
                                    0,
                                    symbolStr,
                                    execPriceLong,
                                    order.getPriceScale(),
                                    execQtyLong,
                                    order.getQtyScale(),
                                    execId,
                                    0,
                                    0,
                                    0,
                                    sideObj,
                                    feesLong
                            );
                        }

                        // Update execution report with trade details
                        executionMessage.setOrderQty(order.getQty());
                        executionMessage.setOrderQtyScale(order.getQtyScale());
                        executionMessage.setPrice(order.getPrice());
                        executionMessage.setPriceScale(order.getPriceScale());
                        executionMessage.setClOrdId(orderLinkId);
                        executionMessage.setExecType(ExecType.TRADE);
                        executionMessage.setOrdType(orderType);
                        executionMessage.setTimeInForce(order.getTimeInForce());
                        executionMessage.setInputTime(execTimeLong);
                        
                        // Set aggressor side based on maker/taker flag
                        if ("true".equalsIgnoreCase(isMaker)) {
                            // If this order is maker, the other side was aggressor
                            if (Side.BUY.equals(order.getSide())) {
                                executionMessage.setAggressorSide(Side.SELL);
                            } else {
                                executionMessage.setAggressorSide(Side.BUY);
                            }
                        } else {
                            // If this order is taker, it was the aggressor
                            executionMessage.setAggressorSide(order.getSide());
                        }

                        subscription.updateExecutionReport(executionMessage);

                        LOGGER.debug("ByBit execution processed: " + orderLinkId +
                                " qty: " + execQtyStr +
                                " price: " + execPriceStr);
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling ByBit execution update", e);
        }
    }

    /**
     * Detects if an order/execution update is triggered by exchange events like ADL, Liquidation, or Margin Call
     * Based on ByBit WebSocket documentation patterns
     */
    private boolean detectExchangeTriggeredEvent(final String orderLinkId, final String rejectReason, 
                                               final String reduceOnly, final String fullData) {
        if (orderLinkId == null) return false;

        // ADL Detection: ByBit uses specific patterns for ADL orders
        if (orderLinkId.toLowerCase().contains("adl") || 
            orderLinkId.toLowerCase().contains("auto-deleverage") ||
            orderLinkId.toLowerCase().startsWith("adl_")) {
            LOGGER.warn("ADL (Auto-Deleveraging) detected for OrderLinkId: " + orderLinkId);
            return true;
        }

        // Liquidation Detection: Check for liquidation-related patterns
        if (orderLinkId.toLowerCase().contains("liquidation") ||
            orderLinkId.toLowerCase().contains("liq") ||
            orderLinkId.toLowerCase().startsWith("liq_")) {
            LOGGER.warn("LIQUIDATION detected for OrderLinkId: " + orderLinkId);
            return true;
        }

        // Margin Call Detection: Check reject reasons
        if (rejectReason != null) {
            final String lowerRejectReason = rejectReason.toLowerCase();
            if (lowerRejectReason.contains("insufficient margin") ||
                lowerRejectReason.contains("margin call") ||
                lowerRejectReason.contains("liquidation") ||
                lowerRejectReason.contains("adl") ||
                lowerRejectReason.contains("auto-deleverage") ||
                lowerRejectReason.contains("risk management")) {
                LOGGER.warn("MARGIN/RISK EVENT detected - Reason: " + rejectReason + ", OrderLinkId: " + orderLinkId);
                return true;
            }
        }

        // Force Reduce Position Detection
        if ("true".equals(reduceOnly)) {
            // Check if this is a forced reduce (not user-initiated)
            final String orderType = minExtract(fullData, "orderType");
            final String triggerBy = minExtract(fullData, "triggerBy");
            
            if ("Market".equals(orderType) && (triggerBy == null || "Unknown".equals(triggerBy))) {
                LOGGER.warn("FORCED POSITION REDUCTION detected for OrderLinkId: " + orderLinkId);
                return true;
            }
        }

        // Check for system-generated order patterns
        if (orderLinkId.matches(".*[0-9]{13,}.*") && 
            (orderLinkId.toLowerCase().contains("sys") || 
             orderLinkId.toLowerCase().contains("auto") ||
             orderLinkId.toLowerCase().contains("risk"))) {
            LOGGER.warn("SYSTEM-GENERATED ORDER detected for OrderLinkId: " + orderLinkId);
            return true;
        }

        return false;
    }

    /**
     * Returns default quote currency based on symbol pattern
     * @param symbol Trading pair symbol
     * @return Default quote currency (USDC or USDT)
     */
    private String getDefaultQuotedCurrency(final String symbol) {
        if (symbol != null && symbol.contains("USDC")) {
            return "USDC";
        } else {
            return "USDT";
        }
    }

    /**
     * Converts ByBit order status string to internal OrdStatus enum
     * @param orderStatusStr ByBit order status string
     * @return Corresponding OrdStatus enum value
     */
    private OrdStatus getByBitOrderStatus(final String orderStatusStr) {
        if (orderStatusStr == null) return OrdStatus.NEW;

        return switch (orderStatusStr) {
            case "New" -> OrdStatus.NEW;
            case "PartiallyFilled" -> OrdStatus.PARTIALLY_FILLED;
            case "Filled" -> OrdStatus.FILLED;
            case "Cancelled" -> OrdStatus.CANCELED;
            case "Rejected" -> OrdStatus.REJECTED;
            case "PartiallyFilledCanceled" -> OrdStatus.CANCELED;
            case "Deactivated" -> OrdStatus.CANCELED;
            default -> {
                LOGGER.warn("Unknown ByBit order status: " + orderStatusStr + ", defaulting to NEW");
                yield OrdStatus.NEW;
            }
        };
    }

    /**
     * Converts ByBit order type string to internal OrdType enum
     * @param orderTypeStr ByBit order type string
     * @return Corresponding OrdType enum value
     */
    private OrdType getBybitOrderType(final String orderTypeStr) {
        if (orderTypeStr == null) return OrdType.LIMIT;

        return switch (orderTypeStr) {
            case "Market" -> OrdType.MARKET;
            case "Limit" -> OrdType.LIMIT;
            default -> OrdType.LIMIT;
        };
    }


}
