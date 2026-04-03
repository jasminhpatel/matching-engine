package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.deribit;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

public final class DeribitUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(DeribitUserDataListener.class);
    private static final String WS_URL = "wss://test.deribit.com/ws/api/v2";  //TODO update it with prod url
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

    public DeribitUserDataListener(final String clientId, final String clientSecret, 
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
    public void onBinaryMessage(byte[] bytes) {
        // Not used for Deribit
    }

    public void connect() throws Exception {
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();

        LOGGER.info("Connecting to Deribit UserData WebSocket at: " + host + ":" + port);

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker =
            WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "DERIBIT-USER-DATA-LISTENER");

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

        LOGGER.info("Deribit UserData WebSocket connected successfully");
        setConnected(true);
        setAuthenticated(false);
        authenticate();
    }

    public void disconnect() {
        LOGGER.info("Disconnecting Deribit UserData WebSocket");
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

    private void authenticate() {
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

        LOGGER.info("Sending Deribit authentication request with timestamp: " + timestamp);
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
                LOGGER.debug("Sending Deribit UserData ping");
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
        LOGGER.info("Deribit UserData WebSocket reconnecting in " + RECONNECT_DELAY_SEC + " seconds...");
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
                    LOGGER.info("Attempting reconnection to Deribit UserData WebSocket");
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
                    LOGGER.info("Attempting reconnection to Deribit UserData WebSocket via fallback thread");
                    connect();
                } catch (final Exception e) {
                    LOGGER.error("Error during reconnect via fallback thread: " + e.getMessage(), e);
                    reconnect();
                }
            }).start();
        }
    }

    public void onMessage(final String msg) {
        LOGGER.debug("Deribit UserData Received message: " + msg);

        // Route message to appropriate handler based on content
        if (msg.contains("\"method\":\"public/auth\"") || (msg.contains("\"result\"") && msg.contains("access_token"))) {
            handleAuthResponse(msg);
        } else if (msg.contains("\"method\":\"public/ping\"")) {
            handlePingResponse(msg);
        } else if (msg.contains("\"error\"")) {
            handleErrorResponse(msg);
        } else if (msg.contains("\"params\"") && msg.contains("\"channel\"")) {
            handleSubscriptionData(msg);
        }
    }

    private void handleAuthResponse(final String msg) {
        if (msg.contains("\"result\"") && !msg.contains("\"error\"")) {
            final String token = minExtract(msg, "access_token");
            if (token != null && !token.trim().isEmpty()) {
                setAccessToken(token);
                setAuthenticated(true);
                LOGGER.info("Deribit UserData authentication successful! Token length: " + token.length());
                subscribeToUserDataStreams();
                schedulePeriodicPing();
            } else {
                LOGGER.error("Authentication response missing or empty access token");
                setAuthenticated(false);
                setAccessToken(null);
                reconnect();
            }
        } else {
            LOGGER.error("Deribit UserData authentication failed: " + msg);
            setAuthenticated(false);
            setAccessToken(null);
            reconnect();
        }
    }

    private void handlePingResponse(final String msg) {
        if (msg.contains("\"result\"") && !msg.contains("\"error\"")) {
            final long currentTime = System.currentTimeMillis();
            setLastPongReceived(currentTime);
            LOGGER.debug("Received pong response at: " + currentTime);
        } else {
            LOGGER.warn("Ping failed with response: " + msg);
            if (msg.contains("\"error\"")) {
                final String error = minExtract(msg, "error");
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

    private void handleErrorResponse(final String msg) {
        final String error = minExtract(msg, "error");
        final String errorMessage = minExtract(error, "message");
        final String errorCode = minExtract(error, "code");
        final String id = minExtract(msg, "id");
        
        LOGGER.error("Error response - ID: " + id + ", Code: " + errorCode + ", Message: " + errorMessage);
        
        // Check if authentication error and reconnect if needed
        if ("13009".equals(errorCode) || "13004".equals(errorCode) || "13003".equals(errorCode)) {
            LOGGER.error("Authentication error detected (code: " + errorCode + "), clearing auth state and reconnecting");
            setAuthenticated(false);
            setAccessToken(null);
            reconnect();
        }
    }

    private void subscribeToUserDataStreams() {
        // Subscribe to account balance changes
        String subMsg = "{\"jsonrpc\":\"2.0\",\"id\":" + requestId.getAndIncrement() + 
                ",\"method\":\"private/subscribe\",\"params\":{\"channels\":[\"user.portfolio.any\"]}}";
        channel.writeAndFlush(new TextWebSocketFrame(subMsg));
        LOGGER.info("Subscribed to portfolio updates on channel: user.portfolio.any");

        // Subscribe to order changes
        subMsg = "{\"jsonrpc\":\"2.0\",\"id\":" + requestId.getAndIncrement() + 
                ",\"method\":\"private/subscribe\",\"params\":{\"channels\":[\"user.orders.any.any.raw\"]}}";
        channel.writeAndFlush(new TextWebSocketFrame(subMsg));
        LOGGER.info("Subscribed to order updates on channel: user.orders.any.any.raw");

        // Subscribe to trade/fill updates
        subMsg = "{\"jsonrpc\":\"2.0\",\"id\":" + requestId.getAndIncrement() + 
                ",\"method\":\"private/subscribe\",\"params\":{\"channels\":[\"user.trades.any.any.raw\"]}}";
        channel.writeAndFlush(new TextWebSocketFrame(subMsg));
        LOGGER.info("Subscribed to trade updates on channel: user.trades.any.any.raw");
    }

    private void handleSubscriptionData(final String msg) {
        // Manually extract the entire params object using brace matching
        final int paramsStart = msg.indexOf("\"params\":");
        if (paramsStart == -1) {
            LOGGER.warn("No params found in subscription data");
            return;
        }
        
        final int paramsJsonStart = msg.indexOf("{", paramsStart);
        if (paramsJsonStart == -1) {
            LOGGER.warn("No opening brace found for params");
            return;
        }
        
        // Find matching closing brace for params object
        int braceCount = 1;
        int paramsJsonEnd = paramsJsonStart + 1;
        while (paramsJsonEnd < msg.length() && braceCount > 0) {
            final char c = msg.charAt(paramsJsonEnd);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
            paramsJsonEnd++;
        }
        
        if (braceCount != 0) {
            LOGGER.warn("Malformed JSON in params - unmatched braces");
            return;
        }
        
        final String params = msg.substring(paramsJsonStart, paramsJsonEnd);
        
        // Extract channel and data from params
        final String channel = minExtract(params, "channel");
        if (channel == null) {
            LOGGER.warn("No channel found in params");
            return;
        }
        
        LOGGER.debug("Processing subscription data for channel: " + channel);
        
        // Extract the data object
        final int dataStart = params.indexOf("\"data\":");
        if (dataStart == -1) {
            LOGGER.warn("No data field found in params for channel: " + channel);
            return;
        }
        
        final int dataJsonStart = params.indexOf("{", dataStart);
        if (dataJsonStart == -1) {
            LOGGER.warn("No opening brace found for data in channel: " + channel);
            return;
        }
        
        // Find matching closing brace for data object
        braceCount = 1;
        int dataJsonEnd = dataJsonStart + 1;
        while (dataJsonEnd < params.length() && braceCount > 0) {
            final char c = params.charAt(dataJsonEnd);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
            dataJsonEnd++;
        }
        
        if (braceCount != 0) {
            LOGGER.warn("Malformed JSON in data for channel: " + channel);
            return;
        }
        
        final String data = params.substring(dataJsonStart, dataJsonEnd);

        // Route to specific handler based on channel type
        if (channel.startsWith("user.portfolio")) {
            handlePortfolioUpdate(data);
        } else if (channel.startsWith("user.orders")) {
            handleOrderUpdate(data);
        } else if (channel.startsWith("user.trades")) {
            handleTradeUpdate(data);
        } else {
            LOGGER.debug("Unhandled channel type: " + channel);
        }
    }

    private void handlePortfolioUpdate(final String data) {
        // Parse balance data from nested structure
        final String currency = minExtract(data, "currency");
        final String balance = minExtract(data, "balance");
        final String availableFunds = minExtract(data, "available_funds");
        final String equity = minExtract(data, "equity");
        final String marginBalance = minExtract(data, "margin_balance");
        final String availableWithdrawalFunds = minExtract(data, "available_withdrawal_funds");
        
        if (currency != null && availableFunds != null) {
            final double available = parseDoubleSafe(availableFunds);
            final double balanceAmount = parseDoubleSafe(balance);
            final double equityAmount = parseDoubleSafe(equity);
            
            LOGGER.info("DERIBIT USER DATA STREAM >>> Portfolio Update - Currency: " + currency + 
                       ", Balance: " + balanceAmount + 
                       ", Equity: " + equityAmount +
                       ", Available: " + available);
            
            // Update subscription with available funds (what can be used for trading)
            subscription.updateBalance(currency, available);
        } else {
            LOGGER.warn("Portfolio update missing required fields - currency: " + currency + ", available_funds: " + availableFunds);
        }
    }

    private void handleOrderUpdate(final String data) {
        final String orderState = minExtract(data, "order_state");
        final String instrument = minExtract(data, "instrument_name");
        final String direction = minExtract(data, "direction");
        final String orderType = minExtract(data, "order_type");
        final String amount = minExtract(data, "amount");
        final String price = minExtract(data, "price");
        final String filledAmount = minExtract(data, "filled_amount");
        final String avgPrice = minExtract(data, "average_price");
        final String label = minExtract(data, "label");

        if (instrument == null || label == null || orderState == null) {
            LOGGER.warn("Order update missing required fields - instrument: " + instrument + ", label: " + label + ", state: " + orderState);
            return;
        }

        LOGGER.info("DERIBIT ORDER UPDATE >> Instrument: " + instrument + ", Label: " + label +
                   ", State: " + orderState + ", Direction: " + direction + ", FilledAmount: " + filledAmount);

        final Order order = subscription.getOrder(label);
        if (order == null) {
            LOGGER.warn("Order not found in subscription for label: " + label);
            return;
        }



        final Side sideObj = "buy".equalsIgnoreCase(direction) ? Side.BUY : Side.SELL;
        final OrdStatus orderStatus = mapOrderStatus(orderState);
        final OrdType ordType = mapOrderType(orderType);
        final ExecType execType = mapExecType(orderState);

        final double avgPriceDouble = parseDoubleSafe(avgPrice);
        final double filledAmountDouble = parseDoubleSafe(filledAmount);
        final double orderAmountDouble = parseDoubleSafe(amount);
        final double orderPriceDouble = parseDoubleSafe(price);

        final long avgPriceLong = MbxMath.changeScale(avgPriceDouble, order.getPriceScale());
        final long filledAmountLong = MbxMath.changeScale(filledAmountDouble, order.getQtyScale());
        final long orderAmountLong = MbxMath.changeScale(orderAmountDouble, order.getQtyScale());
        final long orderPriceLong = MbxMath.changeScale(orderPriceDouble, order.getPriceScale());


        if (OrdStatus.FILLED.equals(orderStatus)) {
            order.setExecuted(true);
        } else if (OrdStatus.REJECTED.equals(orderStatus) ||  OrdStatus.CANCELED.equals(orderStatus)) {
            order.setRejected(true);
        }

        ExecutionReportMessage message = subscription.getExecutionReport(order.getClOrdId());
        if (message == null) {
            LOGGER.debug("Creating new execution report for order: " + order.getClOrdId());
            message = ExecutionReportMessage.createExternalExecutionReport(
                order.getOrderId(), order.getUser(), 0, order.getSymbol(), avgPriceLong, order.getPriceScale(),
                filledAmountLong, order.getQtyScale(), 0, 0, 0, 0, sideObj, 0L);
        }
        message.setOrderQty(orderAmountLong);
        message.setOrderQtyScale(order.getQtyScale());
        message.setCumQty(filledAmountLong);
        message.setCumQtyScale(order.getQtyScale());
        message.setPrice(orderPriceLong);
        message.setPriceScale(order.getPriceScale());
        message.setClOrdId(label);
        message.setOrdStatus(orderStatus);
        message.setExecType(execType);
        message.setOrdType(ordType);
        message.setTimeInForce(order.getTimeInForce());

        subscription.updateExecutionReport(message);
        subscription.updateOrder(label, orderState);
        LOGGER.debug("Updated execution report for order: " + label + ", status: " + orderStatus);
    }

    private void handleTradeUpdate(final String data) {
        final String instrument = minExtract(data, "instrument_name");
        final String direction = minExtract(data, "direction");
        final String amount = minExtract(data, "amount");
        final String price = minExtract(data, "price");
        final String tradeId = minExtract(data, "trade_id");
        final String timestamp = minExtract(data, "timestamp");
        final String orderType = minExtract(data, "order_type");
        final String label = minExtract(data, "label");
        final String state = minExtract(data, "state");
        final String feeCurrency = minExtract(data, "fee_currency");
        final String fee = minExtract(data, "fee");

        if (instrument == null || label == null || amount == null || price == null) {
            LOGGER.warn("Trade update missing required fields - instrument: " + instrument + ", label: " + label + ", amount: " + amount + ", price: " + price);
            return;
        }

        final double priceDouble = parseDoubleSafe(price);
        final double amountDouble = parseDoubleSafe(amount);
        final double feeDouble = parseDoubleSafe(fee);

        LOGGER.info("DERIBIT FILL UPDATE >> Instrument: " + instrument + ", Label: " + label +
                ", Price: " + priceDouble + ", Amount: " + amountDouble +
                ", Direction: " + direction + ", TradeId: " + tradeId + ", Fee: " + feeDouble);

        final Order order = subscription.getOrder(label);
        if (order == null) {
            LOGGER.warn("Order not found in subscription for label: " + label);
            return;
        }

        final Side sideObj = "buy".equalsIgnoreCase(direction) ? Side.BUY : Side.SELL;
        final OrdType ordType = mapOrderType(orderType);

        final long priceLong = MbxMath.changeScale(priceDouble, order.getPriceScale());
        final long amountLong = MbxMath.changeScale(amountDouble, order.getQtyScale());
        final OrdStatus orderStatus = mapOrderStatus(state);

        ExecutionReportMessage message = subscription.getExecutionReport(order.getClOrdId());
        if (message == null) {
            LOGGER.debug("Creating new execution report for trade: " + tradeId);
            message = ExecutionReportMessage.createExternalExecutionReport(
                    0L, order.getUser(), 0, instrument, priceLong, order.getPriceScale(),
                    amountLong, order.getQtyScale(), 0, 0, 0, 0, sideObj, 0L);
        }
        message.setOrderQty(order.getQty());
        message.setOrderQtyScale(order.getQtyScale());
        message.setPrice(priceLong);
        message.setPriceScale(order.getPriceScale());
        message.setClOrdId(label);
        message.setExecType(ExecType.TRADE);
        message.setOrdType(ordType);
        message.setOrdStatus(orderStatus);

        if (OrdStatus.FILLED.equals(orderStatus)) {
            order.setExecuted(true);
        } else if (OrdStatus.REJECTED.equals(orderStatus) ||  OrdStatus.CANCELED.equals(orderStatus)  ) {
            order.setRejected(true);
        }

        // Convert commission with proper instrument scale
        final Instrument feesInstrument = InstrumentCache.getBySymbol(feeCurrency == null || feeCurrency.isBlank() ? getDefaultQuotedCurrency(order.getSymbol()) : feeCurrency);
        if (feesInstrument != null){
        final long feesLong = MbxMath.changeScale(feeDouble, feesInstrument.getQuantityScale());
            message.setFeeAccumulatedQuantity(feesLong);
            message.setFeePositionId(feesInstrument.getId());
            LOGGER.debug("Converted fee: " + feeDouble + " to scaled value: " + feesLong + " for currency: " + (feeCurrency != null ? feeCurrency : "default"));

        }

        subscription.updateExecutionReport(message);
        subscription.updateOrder(order.getClOrdId(), order);
        LOGGER.debug("Updated execution report for trade: " + tradeId + ", order: " + label);
    }

    private OrdStatus mapOrderStatus(final String state) {
        final String lowerState = state.toLowerCase();
        LOGGER.debug("Mapping order state: " + state + " to OrdStatus");
        return switch (lowerState) {
            case "open" -> OrdStatus.NEW;
            case "filled" -> OrdStatus.FILLED;
            case "partially_filled" -> OrdStatus.PARTIALLY_FILLED;
            case "rejected" -> OrdStatus.REJECTED;
            case "cancelled" -> OrdStatus.CANCELED;
            case "untriggered" -> OrdStatus.NEW;
            default -> {
                LOGGER.warn("Unknown order state: " + state + ", mapping to REJECTED");
                yield OrdStatus.REJECTED;
            }
        };
    }

    private OrdType mapOrderType(final String orderType) {
        final String lowerType = orderType.toLowerCase();
        LOGGER.debug("Mapping order type: " + orderType + " to OrdType");
        return switch (lowerType) {
            case "limit" -> OrdType.LIMIT;
            case "market" -> OrdType.MARKET;
            case "stop_limit" -> OrdType.STOP_LIMIT;
            case "stop_market" -> OrdType.STOP;
            default -> {
                LOGGER.warn("Unknown order type: " + orderType + ", mapping to LIMIT");
                yield OrdType.LIMIT;
            }
        };
    }

    private ExecType mapExecType(final String state) {
        final String lowerState = state.toLowerCase();
        LOGGER.debug("Mapping execution state: " + state + " to ExecType");
        return switch (lowerState) {
            case "open" -> ExecType.NEW;
            case "filled" -> ExecType.TRADE;
            case "rejected" -> ExecType.REJECTED;
            case "cancelled" -> ExecType.CANCELED;
            case "untriggered" -> ExecType.NEW;
            default -> {
                LOGGER.warn("Unknown execution state: " + state + ", mapping to REJECTED");
                yield ExecType.REJECTED;
            }
        };
    }


    private String getDefaultQuotedCurrency(final String symbol) {
        // Determine default quote currency based on symbol
        if (symbol != null && symbol.contains("USDC")) {
            LOGGER.debug("Using USDC as default quote currency for symbol: " + symbol);
            return "USDC";
        } else {
            LOGGER.debug("Using USDT as default quote currency for symbol: " + symbol);
            return "USDT";
        }
    }
}
