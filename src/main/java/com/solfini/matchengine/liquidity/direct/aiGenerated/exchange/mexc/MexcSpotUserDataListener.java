package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc;


import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketClientHandler;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketListenerInterface;

import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc.protobuf.PrivateAccountV3Api;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc.protobuf.PrivateDealsV3Api;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc.protobuf.PrivateOrdersV3Api;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc.protobuf.PushDataV3ApiWrapper;
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
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.util.MbxMath;
import com.solfini.util.StringUtil;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;

public final class MexcSpotUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(MexcSpotUserDataListener.class);
    
    // WebSocket Configuration
    private static final String WS_BASE_URL = "wss://wbs-api.mexc.com/ws";
    private static final long LISTEN_KEY_REFRESH_INTERVAL_MINUTES = 50;
    private static final long LISTEN_KEY_MAX_AGE_HOURS = 23;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int DEFAULT_PORT = 443;
    private static final long PING_INTERVAL_SECONDS = 15;
    
    // Protobuf Channels
    private static final String CHANNEL_ACCOUNT = "spot@private.account.v3.api.pb";
    private static final String CHANNEL_DEALS = "spot@private.deals.v3.api.pb";
    private static final String CHANNEL_ORDERS = "spot@private.orders.v3.api.pb";
    final String SUBSCRIPTION_ACCOUNT_DATA = "{  \"method\" : \"SUBSCRIPTION\",  \"params\" : [ \"spot@private.account.v3.api.pb\" ]}";
    final String SUBSCRIPTION_DEALS_DATA = "{  \"method\" : \"SUBSCRIPTION\",  \"params\" : [ \"spot@private.deals.v3.api.pb\" ]}";
    final String SUBSCRIPTION_ORDERS_DATA = "{  \"method\" : \"SUBSCRIPTION\",  \"params\" : [ \"spot@private.orders.v3.api.pb\" ]}";


    // MEXC Order Status Constants
    private static final int MEXC_STATUS_OPEN = 1;
    private static final int MEXC_STATUS_CLOSED = 2;
    private static final int MEXC_STATUS_PARTIALLY_FILLED = 3;
    private static final int MEXC_STATUS_CANCELED = 4;
    private static final int MEXC_STATUS_PARTIALLY_CANCELED = 5;
    private static final int MEXC_STATUS_PARTIALLY_FILLED_CANCELED = 6;

    // Immutable configuration
    private final MexcRestClient restClient;
    private final ExchangeSubscription subscription;
    private final EventLoopGroup group = new NioEventLoopGroup();

    // Connection state management
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private Channel channel;
    private ScheduledFuture<?> listenKeyRefreshFuture;
    private ScheduledFuture<?> pingFuture;
    private String currentListenKey;
    private volatile long lastPongReceived = System.currentTimeMillis();
    private volatile long connectionStartTime = 0;
    private volatile long listenKeyCreatedTime = 0; // Track when listenKey was created
    private volatile int consecutiveReconnects = 0;

    public MexcSpotUserDataListener(final MexcRestClient restClient, final ExchangeSubscription subscription) {
        this.restClient = restClient;
        this.subscription = subscription;
    }

    public boolean getConnected() {
        return connected.get();
    }

    public void setLastPongReceived(final long lastPongReceived) {
        this.lastPongReceived = lastPongReceived;
    }

    @Override
    public boolean isConnected() {
        return connected.get(); // Simplified like Binance
    }

    public void setConnected(final boolean connected) {
        this.connected.set(connected);
    }

    public void setAuthenticated(final boolean authenticated) {
        // Keep it simple like Binance - no complex logic here
    }

    @Override
    public void onMessage(final String message) {
        LOGGER.debug("MEXC SPOT received text message: " + message);
        
        if (message.contains("ping")) {
            handlePingMessage(message);
        }
    }

    private void handlePingMessage(final String message) {
        try {
            final String pingValue = minExtract(message, "ping");
            if (pingValue != null && channel != null && channel.isActive()) {
                final String pongResponse = "{\"pong\":" + pingValue + "}";
                final WebSocketFrame pongFrame = new TextWebSocketFrame(pongResponse);
                channel.writeAndFlush(pongFrame);
                LOGGER.debug("MEXC SPOT sent pong response: " + pongResponse);
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling MEXC SPOT ping message", e);
        }
    }

    public void onBinaryMessage(final byte[] data) {
        LOGGER.trace("MEXC SPOT received binary message: " + (data != null ? data.length : 0) + " bytes");
        
        try {
            if (data == null || data.length == 0) {
                LOGGER.warn("MEXC SPOT received empty binary message");
                return;
            }

            final PushDataV3ApiWrapper pushDataV3ApiWrapper = PushDataV3ApiWrapper.parseFrom(data);
            final String channel = pushDataV3ApiWrapper.getChannel();
            
            LOGGER.debug("MEXC SPOT binary message channel: " + channel);
            
            switch (channel) {
                case CHANNEL_ACCOUNT:
                    LOGGER.info("MEXC SPOT Account Update received");
                    handleProtobufAccountUpdate(pushDataV3ApiWrapper.getPrivateAccount());
                    break;
                    
                case CHANNEL_DEALS:
                    LOGGER.info("MEXC SPOT Account Deals received");
                    handleProtobufDealsUpdate(pushDataV3ApiWrapper.getPrivateDeals());
                    break;
                    
                case CHANNEL_ORDERS:
                    LOGGER.info("MEXC SPOT Account Orders received");
                    handleProtobufOrdersUpdate(pushDataV3ApiWrapper.getPrivateOrders());
                    break;
                    
                default:
                    LOGGER.debug("MEXC SPOT unhandled binary channel: " + channel);
                    break;
            }
            
        } catch (final Exception e) {
            LOGGER.error("Error parsing MEXC SPOT protobuf message (length: " + (data != null ? data.length : 0) + " bytes)", e);
        }
    }

    private void handleProtobufAccountUpdate(final PrivateAccountV3Api accountData) {
        try {
            LOGGER.debug("Processing MEXC SPOT protobuf account update");
            
            if (accountData == null) {
                LOGGER.warn("MEXC SPOT account data is null");
                return;
            }
            
            final String asset = accountData.getVcoinName();
            final String coinId = accountData.getCoinId();
            final double balanceAmount = parseDoubleSafe(accountData.getBalanceAmount());
            final double frozenAmount = parseDoubleSafe(accountData.getFrozenAmount());
            final double totalBalance = balanceAmount + frozenAmount;
            final String type = accountData.getType();
            
            LOGGER.info("MEXC SPOT Account Update - Asset: " + asset + " CoinId: " + coinId + " Balance: " + balanceAmount + 
                       " Frozen: " + frozenAmount + " Total: " + totalBalance + " Type: " + type);
            
            if (asset != null && !asset.isEmpty()) {
                subscription.updateBalance(asset, totalBalance);
                LOGGER.debug("Updated balance for asset: " + asset + " to: " + totalBalance);
            }
            
        } catch (final Exception e) {
            LOGGER.error("Error handling MEXC SPOT protobuf account update", e);
        }
    }

    private void handleProtobufDealsUpdate(final PrivateDealsV3Api dealsData) {
        try {
            LOGGER.debug("Processing MEXC SPOT protobuf deals update");
            
            if (dealsData == null) {
                LOGGER.warn("MEXC SPOT deals data is null");
                return;
            }
            
            final String price = dealsData.getPrice();
            final String quantity = dealsData.getQuantity();
            final String amount = dealsData.getAmount();
            final String tradeId = dealsData.getTradeId();
            final String clientOrderId = dealsData.getClientOrderId();
            final String feeAmount = dealsData.getFeeAmount();
            final String feeCurrency = dealsData.getFeeCurrency();
            
            LOGGER.info("MEXC SPOT Deals Update - ClientOrderId: " + clientOrderId + "  Price: " + price +
                       " Quantity: " + quantity + " Amount: " + amount + " TradeId: " + tradeId + " Fee: " + feeAmount + " FeeCurrency: " + feeCurrency);
            
            if (clientOrderId == null || clientOrderId.isEmpty()) {
                LOGGER.debug("No clientOrderId found in deals update");
                return;
            }
            
            final Order order = subscription.getOrder(clientOrderId);
            if (order == null) {
                LOGGER.debug("Order not found for clientOrderId: " + clientOrderId);
                return;
            }

            final double execPrice = parseDoubleSafe(price);
            final double execQty = parseDoubleSafe(quantity);
            final double execAmount = parseDoubleSafe(amount);
            final double feeAmountDouble = parseDoubleSafe(feeAmount);
            final long tradeIdLong = StringUtil.toLong(tradeId != null ? tradeId : "0");
            
            final long execPriceLong = MbxMath.changeScale(execPrice, order.getPriceScale());
            final long execQtyLong = MbxMath.changeScale(execQty, order.getQtyScale());



            LOGGER.info("MEXC SPOT trade execution processed - ClientOrderId: " + clientOrderId + " ExecutionPrice: " + execPrice +
                       " ExecutionQty: " + execQty + " Fee: " + feeAmountDouble + " FeeCurrency: " + feeCurrency);

            ExecutionReportMessage execReport = subscription.getExecutionReport(order.getClOrdId());
            if (execReport == null) {
                execReport = ExecutionReportMessage.createExternalExecutionReport(
                       order.getOrderId(),
                        order.getUser(),
                        0,
                        order.getSymbol(),
                        execPriceLong,
                        order.getPriceScale(),
                        execQtyLong,
                        order.getQtyScale(),
                        tradeIdLong,
                        0, 0, 0,
                        order.getSide(),
                        0
                );
            }else{
                execReport.setPrice(execPriceLong);
                execReport.setPriceScale(order.getPriceScale());
                execReport.setOrderQty(execQtyLong);
                execReport.setOrderQtyScale(order.getQtyScale());
                execReport.setExecId(tradeIdLong);

            }


            if (feeCurrency != null && !feeCurrency.isEmpty() && feeAmountDouble > 0) {
                final Instrument feesInstrument = InstrumentCache.getBySymbol(feeCurrency);
                if (feesInstrument != null) {
                   final long feesLong = MbxMath.changeScale(feeAmountDouble, feesInstrument.getQuantityScale());
                    execReport.setFeeAccumulatedQuantity(feesLong);
                    execReport.setFeePositionId(feesInstrument.getId());
                }
            }
            execReport.setClOrdId(clientOrderId);
            execReport.setExecType(ExecType.TRADE);
            
            subscription.updateExecutionReport(execReport);
            
        } catch (final Exception e) {
            LOGGER.error("Error handling MEXC SPOT protobuf deals update for order: " + 
                        (dealsData != null ? dealsData.getClientOrderId() : "unknown"), e);
        }
    }

    private void handleProtobufOrdersUpdate(final PrivateOrdersV3Api ordersData) {
        try {
            LOGGER.debug("Processing MEXC SPOT protobuf orders update");
            
            if (ordersData == null) {
                LOGGER.warn("MEXC SPOT orders data is null");
                return;
            }
            
            // Extract order status data from protobuf - MEXC orders structure
            final String clientId = ordersData.getClientId();
            final String price = ordersData.getPrice();
            final String quantity = ordersData.getQuantity();
            final String amount = ordersData.getAmount();
            final String avgPrice = ordersData.getAvgPrice();
            final String remainAmount = ordersData.getRemainAmount();
            final String remainQuantity = ordersData.getRemainQuantity();
            final String lastDealQuantity = ordersData.getLastDealQuantity();
            final String cumulativeQuantity = ordersData.getCumulativeQuantity();
            final String cumulativeAmount = ordersData.getCumulativeAmount();
            final String market = ordersData.getMarket();
            final String triggerPrice = ordersData.getTriggerPrice();
            final String ocoId = ordersData.getOcoId();
            final String routeFactor = ordersData.getRouteFactor();
            final String symbolId = ordersData.getSymbolId();
            final String marketId = ordersData.getMarketId();
            final String marketCurrencyId = ordersData.getMarketCurrencyId();
            final String currencyId = ordersData.getCurrencyId();



            LOGGER.info("MEXC SPOT Orders Update >>> ClientId: " + clientId +
                       " Price=" + price + " Quantity=" + quantity + " Amount=" + amount +
                       " AvgPrice=" + avgPrice + " RemainQty=" + remainQuantity + " CumQty=" + cumulativeQuantity +
                       " Market=" + market + " Symbol=" + symbolId);

            if (clientId == null || clientId.isEmpty()) {
                LOGGER.debug("No clientId found in orders update");
                return;
            }
            
            // Get order from cache
            final Order order = subscription.getOrder(clientId);
            if (order == null) {
                LOGGER.debug("Order not found for clientId: " + clientId);
                return;
            }

            // Parse numeric values
            final double orderPrice = parseDoubleSafe(price);
            final double orderQty = parseDoubleSafe(quantity);
            final double orderAmount = parseDoubleSafe(amount);
            final double avgPriceDouble = parseDoubleSafe(avgPrice);
            final double remainQtyDouble = parseDoubleSafe(remainQuantity);
            final double cumQtyDouble = parseDoubleSafe(cumulativeQuantity);
            final double cumAmountDouble = parseDoubleSafe(cumulativeAmount);
            final double lastDealQtyDouble = parseDoubleSafe(lastDealQuantity);

            // Map MEXC status codes to OrdStatus
            final OrdStatus orderStatus = mapMexcStatusToOrdStatus(ordersData.getStatus(), order);
            
            // Convert to scaled longs using order scales
            final long orderPriceLong = MbxMath.changeScale(orderPrice, order.getPriceScale());
            final long orderQtyLong = MbxMath.changeScale(orderQty, order.getQtyScale());
            final long cumQtyLong = MbxMath.changeScale(cumQtyDouble, order.getQtyScale());
            final long avgPriceLong = avgPriceDouble > 0 ? MbxMath.changeScale(avgPriceDouble, order.getPriceScale()) : 0;
            
            LOGGER.info("MEXC SPOT order update - ClientId: " + clientId + " Status: " + orderStatus +
                       " Price: " + orderPrice + " Qty: " + orderQty + " CumQty: " + cumQtyDouble + 
                       " AvgPrice: " + avgPriceDouble + " Market: " + market);

            // Create or update execution report
            ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
            if (executionMessage == null) {

                // Create execution report for order status update
                final ExecutionReportMessage execReport = ExecutionReportMessage.createExternalExecutionReport(
                       order.getOrderId(),
                        order.getUser(),
                        0, // venue order id
                        order.getSymbol(),
                        avgPriceLong, // Use avg price if available, otherwise order price
                        order.getPriceScale(),
                        cumQtyLong, // No new execution quantity for status update
                        order.getQtyScale(),
                        0, // No trade ID for status updates
                        0, 0, 0, // additional IDs
                        order.getSide(),
                        0 // No fees for status update
                );

                execReport.setClOrdId(clientId);
                execReport.setExecType(ExecType.ORDER_STATUS); // Order status update, not a trade
                execReport.setOrdStatus(orderStatus);
                execReport.setOrderQty(orderQtyLong);
                execReport.setOrderQtyScale(order.getQtyScale());
                execReport.setCumQty(cumQtyLong);
                execReport.setCumQtyScale(order.getQtyScale());
                execReport.setPrice(orderPriceLong);
                execReport.setPriceScale(order.getPriceScale());

                // Update subscription with execution report
                subscription.updateExecutionReport(execReport);
            }

            // Update order status in cache
            subscription.updateOrder(clientId, order);

        } catch (final Exception e) {
            LOGGER.error("Error handling MEXC SPOT protobuf orders update", e);
        }
    }

    /**
     * Maps MEXC status codes to OrdStatus enum values
     * @param status MEXC status code as int
     * @param order order object to update execution status
     * @return corresponding OrdStatus enum value
     */
    private OrdStatus mapMexcStatusToOrdStatus(final int status, final Order order) {
        switch (status) {
            case MEXC_STATUS_OPEN:
                LOGGER.debug("Order status: NEW for order: " + order.getClOrdId());
                return OrdStatus.NEW;
            case MEXC_STATUS_CLOSED:
                LOGGER.debug("Order status: FILLED for order: " + order.getClOrdId());
                order.setExecuted(true);
                return OrdStatus.FILLED;
            case MEXC_STATUS_PARTIALLY_FILLED:
                LOGGER.debug("Order status: PARTIALLY_FILLED for order: " + order.getClOrdId());
                return OrdStatus.PARTIALLY_FILLED;
            case MEXC_STATUS_CANCELED:
            case MEXC_STATUS_PARTIALLY_CANCELED:
            case MEXC_STATUS_PARTIALLY_FILLED_CANCELED:
                LOGGER.debug("Order status: CANCELED for order: " + order.getClOrdId() + " (MEXC status: " + status + ")");
                return OrdStatus.CANCELED;
            default:
                LOGGER.warn("Unknown MEXC order status: " + status + " for order: " + order.getClOrdId() + ", defaulting to NEW");
                return OrdStatus.NEW;
        }
    }

    public void connect() throws Exception {
        if (shuttingDown.get()) {
            LOGGER.info("MEXC SPOT connection cancelled - shutting down");
            return;
        }

        if (currentListenKey == null || isListenKeyExpired()) {
            LOGGER.debug("Creating new MEXC listen key");
            currentListenKey = restClient.createListenKey();
            if (currentListenKey == null) {
                throw new Exception("Failed to create MEXC listen key");
            }
            listenKeyCreatedTime = System.currentTimeMillis();
            LOGGER.info("Created new MEXC listen key, valid for 24 hours");
        }

        final String wsUrl = WS_BASE_URL + "?listenKey=" + currentListenKey;
        final URI uri = new URI(wsUrl);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? DEFAULT_PORT : uri.getPort();

        LOGGER.debug("Connecting to MEXC SPOT WebSocket: " + wsUrl);

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "MEXC-SPOT-USER-DATA");

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

        final Channel ch = b.connect(host, port).sync().channel();
        this.channel = ch;
        handler.handshakeFuture().sync();

        LOGGER.info("MEXC SPOT UserData WebSocket connected: " + wsUrl);
        
        connected.set(true);
        reconnecting.set(false);
        connectionStartTime = System.currentTimeMillis();
        lastPongReceived = System.currentTimeMillis();
        
        scheduleListenKeyRefresh();
        schedulePingMessages();
        subscribeToUserDataEvents();
        
        LOGGER.info("MEXC SPOT WebSocket connection established successfully");
    }

    @Override
    public void reconnect() {
        if (shuttingDown.get()) {
            LOGGER.info("MEXC SPOT reconnect cancelled - shutting down");
            return;
        }

        // Stop scheduled tasks like Binance does
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
        if (listenKeyRefreshFuture != null) {
            listenKeyRefreshFuture.cancel(false);
            listenKeyRefreshFuture = null;
        }
        
        // Close existing channel like Binance does
        if (channel != null && channel.isOpen()) {
            channel.close();
        }

        // Schedule reconnect exactly like Binance does
        if (channel != null && channel.eventLoop() != null && !channel.eventLoop().isShutdown()) {
            channel.eventLoop().schedule(() -> {
                try {
                    LOGGER.info("MEXC SPOT reconnecting...");
                    connect();
                } catch (final Exception e) {
                    LOGGER.error("MEXC SPOT reconnect error", e);
                    reconnect();
                }
            }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
        } else {
            new Thread(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY_SEC * 1000L);
                    if (!shuttingDown.get()) {
                        connect();
                    }
                } catch (final Exception e) {
                    LOGGER.error("MEXC SPOT reconnect error", e);
                }
            }).start();
        }
    }

    @Override
    public void disconnect() {
        LOGGER.info("Disconnecting MEXC SPOT user data stream");
        shuttingDown.set(true);
        
        // Stop scheduled tasks like Binance does
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
        if (listenKeyRefreshFuture != null) {
            listenKeyRefreshFuture.cancel(false);
            listenKeyRefreshFuture = null;
        }
        
        // Clean up listen key
        if (currentListenKey != null) {
            restClient.deleteListenKey(currentListenKey);
            currentListenKey = null;
            listenKeyCreatedTime = 0;
        }
        
        // Close connection like Binance does
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        group.shutdownGracefully();
        
        // Update state
        connected.set(false);
    }

    private void subscribeToUserDataEvents() {
        try {
            if (channel != null && channel.isActive()) {
                LOGGER.info("MEXC SPOT subscribing to user data events...");

                // Subscribe to private account updates
                final WebSocketFrame accountFrame = new TextWebSocketFrame(SUBSCRIPTION_ACCOUNT_DATA);
                channel.writeAndFlush(accountFrame);
                LOGGER.info("MEXC SPOT sent account subscription: " + SUBSCRIPTION_ACCOUNT_DATA);

                // Subscribe to private deals (trade executions)
                final WebSocketFrame dealsFrame = new TextWebSocketFrame(SUBSCRIPTION_DEALS_DATA);
                channel.writeAndFlush(dealsFrame);
                LOGGER.info("MEXC SPOT sent deals subscription: " + SUBSCRIPTION_DEALS_DATA);

                // Subscribe to private orders
                final WebSocketFrame ordersFrame = new TextWebSocketFrame(SUBSCRIPTION_ORDERS_DATA);
                channel.writeAndFlush(ordersFrame);
                LOGGER.info("MEXC SPOT sent orders subscription: " + SUBSCRIPTION_ORDERS_DATA);

                LOGGER.info("MEXC SPOT successfully sent all user data subscriptions");
            } else {
                LOGGER.warn("MEXC SPOT cannot subscribe - channel is not active");
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to send MEXC SPOT user data subscriptions", e);
            // Don't fail the connection for subscription errors as listenKey should work without explicit subscription
        }
    }

    private boolean isListenKeyExpired() {
        if (listenKeyCreatedTime == 0) return true;
        final long ageHours = (System.currentTimeMillis() - listenKeyCreatedTime) / 3600000;
        return ageHours >= LISTEN_KEY_MAX_AGE_HOURS;
    }

    private void schedulePingMessages() {
        if (pingFuture != null) pingFuture.cancel(false);
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel != null && channel.isActive() && !shuttingDown.get()) {
                // Send ping frame to keep connection alive
                try {
                    final WebSocketFrame pingFrame = new PingWebSocketFrame();
                    channel.writeAndFlush(pingFrame);
                    LOGGER.debug("MEXC SPOT sent keepalive ping frame");
                } catch (final Exception e) {
                    LOGGER.error("Failed to send MEXC SPOT ping frame", e);
                    reconnect();
                }
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void scheduleListenKeyRefresh() {
        if (listenKeyRefreshFuture != null) listenKeyRefreshFuture.cancel(false);
        listenKeyRefreshFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (currentListenKey != null) {
                if (isListenKeyExpired()) {
                    LOGGER.info("MEXC listen key expired after 24 hours, will recreate on next reconnect");
                    // Don't recreate here, just let it expire and recreate on next connect
                } else {
                    final boolean success = restClient.extendListenKey(currentListenKey);
                    if (!success) {
                        LOGGER.warn("Failed to refresh MEXC listen key, will recreate on next reconnect");
                        currentListenKey = null; // Force recreation
                        listenKeyCreatedTime = 0;
                    } else {
                        LOGGER.debug("Successfully refreshed MEXC listen key");
                    }
                }
            }
        }, LISTEN_KEY_REFRESH_INTERVAL_MINUTES, LISTEN_KEY_REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }


}