package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitmart;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketClientHandler;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketListenerInterface;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.*;
import com.solfini.util.HMAC;
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
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public final class BitmartFutureUserDataListener implements NettyWebSocketListenerInterface {
    private static final CustomLogger LOGGER = CustomLogger.getLogger(BitmartFutureUserDataListener.class);
    private static final long PING_INTERVAL_SECONDS = 25;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;
    private static final int PROXY_PORT = 8888;
    private final String apiKey;
    private final String apiSecret;
    private final String apiMemo;
    private final ExchangeSubscription subscription;
    private final EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final String WS_URL = "wss://openapi-ws-v2.bitmart.com/user?protocol=1.1";
    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();

    public BitmartFutureUserDataListener(final String apiKey, final String apiSecret,
                                         final String apiMemo, final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.apiMemo = apiMemo;
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

    public void connect() throws Exception {
        LOGGER.info("Connecting to BitMart FutureUserData WebSocket: " + WS_URL);
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();
        final String proxyHost = subscription.getLastUsedProxy();
        final int proxyPort = PROXY_PORT;

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker =
                WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "BITMART-FUTURE-USER-DATA-LISTENER");

        final Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                final ChannelPipeline p = ch.pipeline();
                if (proxyHost != null) {
                    p.addLast(new HttpProxyHandler(new InetSocketAddress(proxyHost, proxyPort)));
                }
                p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                p.addLast(new HttpClientCodec());
                p.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                p.addLast(handler);
            }
        });

        final Channel ch = b.connect(host, port).sync().channel();
        this.channel = ch;
        handler.handshakeFuture().sync();

        LOGGER.info("BitMart FutureUserData WebSocket connected");
        authenticate();
    }

    public void disconnect() {
        LOGGER.info("Disconnecting BitMart FutureUserData WebSocket");
        setConnected(false);
        setAuthenticated(false);
        stopPing();
        if (channel != null && channel.isOpen()) {
            channel.close();
            LOGGER.debug("Channel closed");
        }
        group.shutdownGracefully();
        LOGGER.info("BitMart FutureUserData disconnected");
    }

    @Override
    public boolean isConnected() {
        return connected.get() && authenticated.get();
    }

    public void setConnected(final boolean connected) {
        this.connected.set(connected);
    }

    private void authenticate() throws Exception {
        final long timestamp = System.currentTimeMillis();
        final String message = timestamp + "#" + apiMemo + "#bitmart.WebSocket";
        final String sign = HMAC.hmacSha256(message, apiSecret);
        final String loginMsg = "{\"action\":\"access\",\"args\":[\"" + apiKey + "\",\"" + timestamp + "\",\"" + sign + "\",\"web\"]}";
        LOGGER.info("Sending BitMart future trade authentication JSON: " + loginMsg);
        channel.writeAndFlush(new TextWebSocketFrame(loginMsg));
    }

    private void schedulePeriodicPing() {
        LOGGER.debug("Scheduling periodic ping with interval: " + PING_INTERVAL_SECONDS + " seconds");
        stopPing();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                channel.writeAndFlush(new PingWebSocketFrame());
                LOGGER.debug("Sending BitMart FutureUserData ping");
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopPing() {
        if (pingFuture != null) {
            LOGGER.debug("Stopping periodic ping");
            pingFuture.cancel(false);
            pingFuture = null;
        }
    }

    public void reconnect() {
        LOGGER.warn("Initiating reconnection for BitMart FutureUserData WebSocket");
        setConnected(false);
        setAuthenticated(false);
        stopPing();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        channel.eventLoop().schedule(() -> {
            try {
                LOGGER.info("BitMart FutureUserData WebSocket reconnecting...");
                connect();
            } catch (final Exception e) {
                LOGGER.error("Error during reconnect", e);
                reconnect();
            }
        }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
    }

    public void onMessage(final String json) {
        LOGGER.debug("BitMart FutureUserData Received message: " + json);

        if (json.contains("\"action\":\"access\"")) {
            handleAuthResponse(json);
        }
        else if (json.contains("\"action\":\"subscribe\"")) {
                handleSubscriptionConfirmation(json);
        } else if (json.contains("\"group\":\"futures/order\"")) {
            parseOrderTradeUpdate(json);
        } else if (json.contains("\"group\":\"futures/asset:USDT\"")) {
            applyAccountUpdate(json);
        } else if (json.contains("\"group\":\"futures/position\"")) {
            applyPositionUpdate(json);
        } else {
            LOGGER.info("Unhandled event type: " + json);
        }
    }

    private void handleAuthResponse(final String message) {
        LOGGER.debug("Processing authentication response");
        if (message.contains("\"success\":true")) {
            LOGGER.info("BitMart Future User Data Authentication successful!");
            setConnected(true);
            setAuthenticated(true);
            subscribeToUserDataStreams();
            schedulePeriodicPing();
        } else {
            LOGGER.error("BitMart Future User Data Authentication failed: " + message);
        }
    }

    private void handleSubscriptionConfirmation(final String message){
        final String topic = minExtract(message, "group");

        if (topic != null) {
            if (topic.contains("futures/asset")) {
                LOGGER.info("Successfully subscribed to BitMart futures/asset");
            } else if (topic.contains("futures/position")) {
                LOGGER.info("Successfully subscribed to BitMart futures/position");
            }else if (topic.contains("futures/order")) {
                LOGGER.info("Successfully subscribed to BitMart futures/order");
            }
            else {
                LOGGER.info("Successfully subscribed to topic: " + topic);
            }
        } else {
            LOGGER.warn("Subscription confirmation received but topic extraction failed: " + message);
        }
    }

    private void subscribeToUserDataStreams() {
        LOGGER.info("Subscribing to BitMart future user data streams");
        final String balanceSubJson = "{\"action\":\"subscribe\",\"args\":[\"futures/asset:USDT\"]}";
        final String positionSubJson = "{\"action\":\"subscribe\",\"args\":[\"futures/position\"]}";
        final String orderSubJson = "{\"action\":\"subscribe\",\"args\":[\"futures/order\"]}";
        
        LOGGER.debug("Subscribing to futures balance: " + balanceSubJson);
        channel.writeAndFlush(new TextWebSocketFrame(balanceSubJson));

        LOGGER.debug("Subscribing to futures position: " + positionSubJson);
        channel.writeAndFlush(new TextWebSocketFrame(positionSubJson));
        
        LOGGER.debug("Subscribing to futures order: " + orderSubJson);
        channel.writeAndFlush(new TextWebSocketFrame(orderSubJson));
    }

    private void parseOrderTradeUpdate(final String json) {
        LOGGER.debug("Parsing order trade update");
        // Extract action and order data
        final String actionStr = minExtract(json, "action");

        // Extract order fields from nested structure
        final String symbolStr = minExtract(json, "symbol");
        final String clientOrderIdStr = minExtract(json, "client_order_id");
        final String sideStr = minExtract(json, "side"); // 1=buy, 2=sell
        final String typeStr = minExtract(json, "type"); // limit, market, etc.
        final String dealAvgPriceStr = minExtract(json, "deal_avg_price");
        final String dealSizeStr = minExtract(json, "deal_size");
        final String priceStr = minExtract(json, "price");
        final String sizeStr = minExtract(json, "size");   //TODO this is contract size and not the quantity so we need to calculate it to find excat qty
        final String stateStr = minExtract(json, "state"); // order state

        // Check for special order types indicating ADL or liquidation
        final String orderSourceStr = minExtract(json, "order_source");

        // --- Parse last_trade if present ---
        String lastTradeIdStr = null;
        String lastFillQtyStr = null;
        String lastFillPriceStr = null;
        String lastFeeStr = null;
        String lastFeeCcyStr = null;
        int lastTradeIdx = json.indexOf("\"last_trade\":");
        if (lastTradeIdx != -1) {
            int objStart = json.indexOf('{', lastTradeIdx);
            int objEnd = json.indexOf('}', objStart);
            if (objStart != -1 && objEnd != -1) {
                String lastTradeJson = json.substring(objStart, objEnd + 1);
                lastTradeIdStr = minExtract(lastTradeJson, "lastTradeID");
                lastFillQtyStr = minExtract(lastTradeJson, "fillQty");
                lastFillPriceStr = minExtract(lastTradeJson, "fillPrice");
                lastFeeStr = minExtract(lastTradeJson, "fee");
                lastFeeCcyStr = minExtract(lastTradeJson, "feeCcy");
            }
        }

        if (symbolStr == null || stateStr == null ) {
            LOGGER.warn("Missing required fields in order update: symbolStr=" + symbolStr + ", stateStr=" + stateStr);
            return;
        }

        // Parse numeric values
        final int action = actionStr != null ? Integer.parseInt(actionStr) : 0;
        final int side = sideStr != null ? Integer.parseInt(sideStr) : 0;
        final int state = Integer.parseInt(stateStr);
        final double dealAvgPrice = parseDoubleSafe(dealAvgPriceStr);
        final double dealSize = parseDoubleSafe(dealSizeStr);
        final double price = parseDoubleSafe(priceStr);
        final double size = parseDoubleSafe(sizeStr);

        // Use last_trade values if present, otherwise fallback to dealAvgPrice/dealSize
        final long tradeId = parseLongSafe(lastTradeIdStr);
        final double lastFillQty = parseDoubleSafe(lastFillQtyStr);
        final double lastFillPrice = parseDoubleSafe(lastFillPriceStr);
        final double fee = parseDoubleSafe(lastFeeStr);
        final String feeCcy = lastFeeCcyStr;

        // --- Log ADL or Liquidation Events ---
        if (orderSourceStr != null) {
            if ("liquidation".equalsIgnoreCase(orderSourceStr)) {
                LOGGER.error("LIQUIDATION EVENT: Position liquidated due to insufficient margin balance. Symbol: " + symbolStr + 
                        ", ClientOrderId: " + clientOrderIdStr + ", Side: " + (side == 1 ? "BUY" : "SELL") + 
                        ", Quantity: " + size + ", Price: " + dealAvgPrice + ", DealSize: " + dealSize);
            } else if ("adl".equalsIgnoreCase(orderSourceStr)) {
                LOGGER.error("ADL EVENT: Position closed due to Auto-Deleveraging. Symbol: " + symbolStr + 
                        ", ClientOrderId: " + clientOrderIdStr + ", Side: " + (side == 1 ? "BUY" : "SELL") + 
                        ", Quantity: " + size + ", Price: " + dealAvgPrice + ", DealSize: " + dealSize);
            } else if ("delivery_adl".equalsIgnoreCase(orderSourceStr)) {
                LOGGER.error("SETTLEMENT ADL EVENT: Position closed due to Auto-Deleveraging at delivery/settlement. Symbol: " + symbolStr + 
                        ", ClientOrderId: " + clientOrderIdStr + ", Side: " + (side == 1 ? "BUY" : "SELL") + 
                        ", Quantity: " + size + ", Price: " + dealAvgPrice + ", DealSize: " + dealSize);
            }
        }

        // Map state to status string
        final String statusStr = mapOrderState(state);

        // Map to SBE enums
        final Side sideObj = side == 1 ? Side.BUY : Side.SELL;
        final OrdStatus orderStatus = mapToOrdStatus(state);
        final OrdType orderType = mapToOrdType(typeStr);
        final ExecType execType = mapToExecType(state, action);

        LOGGER.info("FUTURE USER DATA WS>> Trade Update - Symbol: " + symbolStr +
                ", ClientOrderId: " + clientOrderIdStr +
                ", Side: " + (side == 1 ? "BUY" : "SELL") +
                ", Type: " + typeStr +
                ", State: " + state + " (" + statusStr + ")" +
                ", Price: " + price +
                ", Size: " + size +
                ", DealSize: " + dealSize +
                ", DealAvgPrice: " + dealAvgPrice +
                ", Action: " + action +
                ", LastTradeId: " + tradeId +
                ", LastFillQty: " + lastFillQty +
                ", LastFillPrice: " + lastFillPrice +
                ", Fee: " + fee +
                ", FeeCcy: " + feeCcy);

        // Get order from cache
        final Order order = subscription.getOrder(clientOrderIdStr);

        // Convert values to scaled longs
        final long dealAvgPriceLong = MbxMath.changeScale(dealAvgPrice, order.getPriceScale());
        final long dealSizeLong = MbxMath.changeScale(dealSize, order.getQtyScale());
        final long orderQtyLong = MbxMath.changeScale(size, order.getQtyScale());
        final long orderPriceLong = MbxMath.changeScale(price, order.getPriceScale());
        final long lastFillQtyLong = MbxMath.changeScale(lastFillQty, order.getQtyScale());
        final long lastFillPriceLong = MbxMath.changeScale(lastFillPrice, order.getPriceScale());

        // Fee scaling (if fee currency is known)
        long feeLong = 0;
        if (feeCcy != null && !feeCcy.isEmpty()) {
            try {
                com.solfini.instrument.Instrument feeInstr = com.solfini.instrument.InstrumentCache.getBySymbol(feeCcy);
                if (feeInstr != null) {
                    feeLong = MbxMath.changeScale(fee, feeInstr.getQuantityScale());
                }
            } catch (Exception e) {
                LOGGER.warn("Unknown fee currency for scaling: " + feeCcy);
            }
        }


        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
            // Create execution report
            executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(),
                    order.getUser(),
                    0,
                    symbolStr,
                    lastFillPriceLong,
                    order.getPriceScale(),
                    lastFillQtyLong,
                    order.getQtyScale(),
                    tradeId,
                    0,
                    0,
                    0,
                    sideObj,
                    feeLong
            );
        }

        executionMessage.setExecId(tradeId);
        executionMessage.setPrice(lastFillPriceLong);
        executionMessage.setPriceScale(order.getPriceScale());
        executionMessage.setLastPx(lastFillPriceLong);
        executionMessage.setLastPxScale(order.getPriceScale());
        executionMessage.setOrderQty(lastFillQtyLong);
        executionMessage.setOrderQtyScale(order.getQtyScale());
        executionMessage.setLastQty(lastFillQtyLong);
        executionMessage.setLastQtyScale(order.getQtyScale());
        executionMessage.setFeeAccumulatedQuantity(feeLong);
        executionMessage.setCumQty(dealSizeLong);
        executionMessage.setCumQtyScale(order.getQtyScale());
        executionMessage.setClOrdId(clientOrderIdStr);
        executionMessage.setOrdStatus(orderStatus);
        executionMessage.setExecType(execType);
        executionMessage.setOrdType(orderType);


        LOGGER.info("Updated execution report for orderId: " + order.getOrderId() + ", clientOrderId: " + clientOrderIdStr);
        subscription.updateExecutionReport(executionMessage);

        subscription.updateOrder(clientOrderIdStr, statusStr);
    }

    private String mapOrderState(final int state) {
        // BitMart futures order states:
        // 1 = Submitting
        // 2 = Submitted (unfilled)
        // 4 = Canceled
        // 5 = Partially Filled
        // 6 = Filled
        return switch (state) {
            case 1 -> "SUBMITTING";
            case 2 -> "NEW";
            case 4 -> "CANCELED";
            case 5 -> "PARTIALLY_FILLED";
            case 6 -> "FILLED";
            default -> "UNKNOWN_" + state;
        };
    }

    private OrdStatus mapToOrdStatus(final int state) {
        return switch (state) {
            case 1 -> OrdStatus.PENDING_NEW;
            case 2 -> OrdStatus.NEW;
            case 4 -> OrdStatus.CANCELED;
            case 5 -> OrdStatus.PARTIALLY_FILLED;
            case 6 -> OrdStatus.FILLED;
            default -> OrdStatus.REJECTED;
        };
    }

    private OrdType mapToOrdType(final String typeStr) {
        if (typeStr == null) return OrdType.LIMIT;
        return switch (typeStr.toLowerCase()) {
            case "limit" -> OrdType.LIMIT;
            case "market" -> OrdType.MARKET;
            default -> OrdType.LIMIT;
        };
    }

    private ExecType mapToExecType(final int state, final int action) {
        // action: 1=create, 2=update, 3=finish
        if (action == 3) {
            return switch (state) {
                case 4 -> ExecType.CANCELED;
                case 6 -> ExecType.TRADE;
                default -> ExecType.ORDER_STATUS;
            };
        }
        return switch (state) {
            case 1 -> ExecType.PENDING_NEW;
            case 2 -> ExecType.NEW;
            case 5 -> ExecType.TRADE;
            default -> ExecType.ORDER_STATUS;
        };
    }

    public void applyAccountUpdate(final String json) {
        LOGGER.debug("Processing account update");
        final String asset = minExtract(json, "currency");
        final double walletBalance = parseDoubleSafe(minExtract(json, "available_balance"));

        if (asset != null) {
            LOGGER.debug("Updating balance for asset: " + asset + " = " + walletBalance);
            subscription.updateBalance(asset, walletBalance);
        } else {
            LOGGER.warn("Asset is null in account update");
        }
    }

    private void applyPositionUpdate(final String json) {
        LOGGER.debug("Processing position update");
        final String symbol = minExtract(json, "symbol");
        final double positionAmount = parseDoubleSafe(minExtract(json, "hold_volume"));

        if (symbol != null) {
            LOGGER.debug("Updating position for symbol: " + symbol + " = " + positionAmount);
            subscription.updatePosition(symbol, positionAmount);
        } else {
            LOGGER.warn("Symbol is null in position update");
        }
    }

    private TimeInForce getTimeInForce(final String timeInForceStr) {
        if (timeInForceStr == null) return null;
        return switch (timeInForceStr) {
            case "GTC" -> TimeInForce.GOOD_TILL_CANCEL;
            case "IOC" -> TimeInForce.IMMEDIATE_OR_CANCEL;
            case "FOK" -> TimeInForce.FILL_OR_KILL;
            default -> null;
        };
    }
}
