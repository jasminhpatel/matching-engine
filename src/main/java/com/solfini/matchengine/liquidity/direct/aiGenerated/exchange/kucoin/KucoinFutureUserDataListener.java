package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kucoin;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketClientHandler;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketListenerInterface;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.*;
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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.extractJsonValue;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseLongSafe;

/**
 * KuCoin Futures user-data WebSocket: balance (/contractAccount/wallet), position (/contract/positionAll), orders (/contractMarket/tradeOrders).
 * Same as BinanceFutureUserDataListener: updateBalance, updatePosition, updateOrder.
 * See: https://www.kucoin.com/docs-new/3470092w0 (Balance), https://www.kucoin.com/docs-new/3470093w0 (Positions), https://www.kucoin.com/docs-new/3470090w0 (Orders).
 */
public final class KucoinFutureUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(KucoinFutureUserDataListener.class);
    private static final String BALANCE_TOPIC = "/contractAccount/wallet";
    private static final String POSITION_TOPIC = "/contract/positionAll";
    /** KuCoin Futures order updates. Topic can be /contractMarket/tradeOrders or /contractMarket/tradeOrders:SYMBOL */
    private static final String ORDER_TOPIC = "/contractMarket/tradeOrders";
    private static final long PING_INTERVAL_MS = 18_000;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;

    private final ExchangeSubscription subscription;
    private volatile String token;
    private volatile String endpoint;
    private final Supplier<KucoinRestClient.PrivateTokenResult> tokenSupplier;
    private final String outboundIp;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();

    public KucoinFutureUserDataListener(final ExchangeSubscription subscription,
                                        final String token,
                                        final String endpoint,
                                        final Supplier<KucoinRestClient.PrivateTokenResult> tokenSupplier,
                                        final String outboundIp) {
        this.subscription = subscription;
        this.token = token;
        this.endpoint = endpoint;
        this.tokenSupplier = tokenSupplier;
        this.outboundIp = outboundIp;
    }

    @Override
    public void setLastPongReceived(final long lastPongReceived) {
        this.lastPongReceived = lastPongReceived;
    }

    @Override
    public void onBinaryMessage(final byte[] bytes) {
    }

    @Override
    public void connect() throws Exception {
        final String wsUrl = endpoint.contains("?") ? endpoint + "&token=" + token : endpoint + "?token=" + token;
        final URI uri = new URI(wsUrl);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();

        final SslContext sslCtx = SslContextBuilder.forClient().build();
        final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());
        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "KUCOIN-FUTURE-USER-DATA-LISTENER");

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

        if (outboundIp != null && !outboundIp.isEmpty()) {
            b.localAddress(new InetSocketAddress(outboundIp, 0));
        }
        this.channel = b.connect(host, port).sync().channel();
        handler.handshakeFuture().sync();

        setConnected(true);
        setAuthenticated(true);
        sendSubscribeBalance();
        sendSubscribePosition();
        sendSubscribeOrder();
        schedulePing();
        LOGGER.info("KuCoin FutureUserData WebSocket connected");
    }

    private void sendSubscribe(final String topic, final long id) {
        if (channel == null || !channel.isActive()) return;
        final String subscribe = "{\"id\":\"" + id + "\",\"type\":\"subscribe\",\"topic\":\"" + topic + "\",\"response\":true,\"privateChannel\":\"true\"}";
        channel.writeAndFlush(new TextWebSocketFrame(subscribe));
        LOGGER.debug("KuCoin FutureUserData subscribed to " + topic);
    }

    private void sendSubscribeBalance() {
        sendSubscribe(BALANCE_TOPIC, System.currentTimeMillis());
    }

    private void sendSubscribePosition() {
        sendSubscribe(POSITION_TOPIC, System.currentTimeMillis() + 1);
    }

    private void sendSubscribeOrder() {
        sendSubscribe(ORDER_TOPIC, System.currentTimeMillis() + 2);
    }

    private void schedulePing() {
        stopPing();
        if (channel == null) return;
        final long id = System.currentTimeMillis();
        final String subscribe = "{\"id\":\"" + id + "\",\"type\":\"ping\"}";
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(subscribe));
                LOGGER.debug("Sending KuCoin FutureUserData ping");
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopPing() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
    }

    @Override
    public void disconnect() {
        stopPing();
        setConnected(false);
        setAuthenticated(false);
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        group.shutdownGracefully();
        LOGGER.info("KuCoin FutureUserData WebSocket disconnected");
    }

    @Override
    public boolean isConnected() {
        return connected.get() && authenticated.get();
    }

    @Override
    public void setConnected(final boolean connected) {
        this.connected.set(connected);
    }

    @Override
    public void setAuthenticated(final boolean authenticated) {
        this.authenticated.set(authenticated);
    }

    @Override
    public void reconnect() {
        stopPing();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (channel != null && channel.eventLoop() != null) {
            channel.eventLoop().schedule(() -> {
                try {
                    if (tokenSupplier != null) {
                        final KucoinRestClient.PrivateTokenResult fresh = tokenSupplier.get();
                        if (fresh != null) {
                            token = fresh.getToken();
                            endpoint = fresh.getEndpoint();
                        }
                    }
                    LOGGER.info("KuCoin FutureUserData WebSocket reconnecting...");
                    connect();
                } catch (final Exception e) {
                    LOGGER.error("KuCoin FutureUserData reconnect failed: " + e.getMessage(), e);
                    reconnect();
                }
            }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onMessage(final String msg) {
        if (msg == null) return;
        LOGGER.debug("KuCoin FutureUserData received: " + msg);
        final String type = minExtract(msg, "type");
        if ("welcome".equals(type) || "ack".equals(type)) {
            return;
        }
        final String topic = minExtract(msg, "topic");
        if ("message".equals(type) && BALANCE_TOPIC.equals(topic)) {
            handleBalanceMessage(msg);
            return;
        }
        if ("message".equals(type) && topic != null && (POSITION_TOPIC.equals(topic) || topic.startsWith("/contract/position:"))) {
            handlePositionMessage(msg);
            return;
        }
        if ("message".equals(type) && topic != null && (ORDER_TOPIC.equals(topic) || topic.startsWith(ORDER_TOPIC + ":"))) {
            handleOrderMessage(msg);
        }
    }

    /**
     * Handle order updates from /contractMarket/tradeOrders (orderChange / symbolOrderChange).
     * Setting procedure is split by data.type: open, update, match, filled, canceled.
     */
    private void handleOrderMessage(final String msg) {
        final String dataStr = extractJsonValue(msg, "data");
        if (dataStr == null) return;

        final String clientOid = minExtract(dataStr, "clientOid");
        final String orderId = minExtract(dataStr, "orderId");
        final String key = clientOid != null ? clientOid : orderId;
        if (key == null) {
            LOGGER.debug("KuCoin Futures order update skipped: missing clientOid/orderId");
            return;
        }

        final Order order = subscription.getOrder(key);
        if (order == null) {
            LOGGER.warn("KuCoin Futures UserData order not found in cache: key=" + key);
            return;
        }

        final String changeType = minExtract(dataStr, "type");
        if (changeType == null) return;

        final Side sideObj = "sell".equalsIgnoreCase(minExtract(dataStr, "side")) ? Side.SELL : Side.BUY;
        final String orderTypeStr = minExtract(dataStr, "orderType");
        final OrdType orderType = "market".equalsIgnoreCase(orderTypeStr) ? OrdType.MARKET : OrdType.LIMIT;
        final double size = parseDoubleSafe(minExtract(dataStr, "size"));
        final double priceDouble = parseDoubleSafe(minExtract(dataStr, "price"));
        final long orderQtyLong = MbxMath.changeScale(size, order.getQtyScale());
        final long orderPriceLong = MbxMath.changeScale(priceDouble, order.getPriceScale());

        ExecutionReportMessage executionReportMessage = getOrCreateExecutionReport(order, sideObj, orderPriceLong, orderQtyLong);
        executionReportMessage.setClOrdId(order.getClOrdId());
        executionReportMessage.setOrderQty(orderQtyLong);
        executionReportMessage.setOrderQtyScale(order.getQtyScale());
        executionReportMessage.setPrice(orderPriceLong);
        executionReportMessage.setPriceScale(order.getPriceScale());
        executionReportMessage.setOrdType(orderType);
        executionReportMessage.setTimeInForce(order.getTimeInForce());

        final String orderStatusStr;
        switch (changeType) {
            case "open":
                // Order in book: size, filledSize=0, price, remainSize
                executionReportMessage.setCumQty(0);
                executionReportMessage.setCumQtyScale(order.getQtyScale());
                executionReportMessage.setExecType(ExecType.NEW);
                executionReportMessage.setOrdStatus(OrdStatus.NEW);
                orderStatusStr = "NEW";
                break;
            case "update":
                // Partial cancel / STP: size, filledSize, price, remainSize, clientOid
                final double filledUpdate = parseDoubleSafe(minExtract(dataStr, "filledSize"));
                executionReportMessage.setCumQty(MbxMath.changeScale(filledUpdate, order.getQtyScale()));
                executionReportMessage.setCumQtyScale(order.getQtyScale());
                executionReportMessage.setExecType(ExecType.CANCELED);
                executionReportMessage.setOrdStatus(OrdStatus.CANCELED);
                orderStatusStr = "CANCELED";
                break;
            case "match":
                // Fill: matchPrice, matchSize, filledSize, tradeId
                final double matchPrice = parseDoubleSafe(minExtract(dataStr, "matchPrice"));
                final double matchSize = parseDoubleSafe(minExtract(dataStr, "matchSize"));
                final double filledMatch = parseDoubleSafe(minExtract(dataStr, "filledSize"));
                final long tradeId = parseLongSafe(minExtract(dataStr, "tradeId"));
                executionReportMessage.setLastPx(MbxMath.changeScale(matchPrice > 0 ? matchPrice : priceDouble, order.getPriceScale()));
                executionReportMessage.setLastPxScale(order.getPriceScale());
                executionReportMessage.setLastQty(MbxMath.changeScale(matchSize > 0 ? matchSize : filledMatch, order.getQtyScale()));
                executionReportMessage.setLastQtyScale(order.getQtyScale());
                executionReportMessage.setCumQty(MbxMath.changeScale(filledMatch, order.getQtyScale()));
                executionReportMessage.setCumQtyScale(order.getQtyScale());
                executionReportMessage.setExecId(tradeId);
                executionReportMessage.setExecType(ExecType.TRADE);
                final String statusMatch = minExtract(dataStr, "status");
                executionReportMessage.setOrdStatus("done".equals(statusMatch) ? OrdStatus.FILLED : OrdStatus.PARTIALLY_FILLED);
                orderStatusStr = "done".equals(statusMatch) ? "FILLED" : "PARTIALLY_FILLED";
                break;
            case "filled":
                // Order done (filled): size, filledSize, price, remainSize=0
                final double filledDone = parseDoubleSafe(minExtract(dataStr, "filledSize"));
                executionReportMessage.setLastPx(orderPriceLong);
                executionReportMessage.setLastPxScale(order.getPriceScale());
                executionReportMessage.setLastQty(MbxMath.changeScale(filledDone, order.getQtyScale()));
                executionReportMessage.setLastQtyScale(order.getQtyScale());
                executionReportMessage.setCumQty(MbxMath.changeScale(filledDone, order.getQtyScale()));
                executionReportMessage.setCumQtyScale(order.getQtyScale());
                executionReportMessage.setExecType(ExecType.TRADE);
                executionReportMessage.setOrdStatus(OrdStatus.FILLED);
                orderStatusStr = "FILLED";
                break;
            case "canceled":
                // Order done (canceled): size, filledSize, price, remainSize=0
                final double filledCancel = parseDoubleSafe(minExtract(dataStr, "filledSize"));
                executionReportMessage.setCumQty(MbxMath.changeScale(filledCancel, order.getQtyScale()));
                executionReportMessage.setCumQtyScale(order.getQtyScale());
                executionReportMessage.setExecType(ExecType.CANCELED);
                executionReportMessage.setOrdStatus(OrdStatus.CANCELED);
                orderStatusStr = "CANCELED";
                break;
            default:
                LOGGER.debug("KuCoin Futures order update: unknown type=" + changeType);
                return;
        }
        // todo : verify
//        if (changeType.equals(ExecType.EXPIRED)) {
//            executionReportMessage.setExecType(ExecType.REJECTED);
//            executionReportMessage.setOrdStatus(OrdStatus.REJECTED);
//        }
        subscription.updateExecutionReport(executionReportMessage);
        subscription.updateOrder(order.getClOrdId(), orderStatusStr);
        LOGGER.info("KuCoin Futures UserData order update: clOrdId=" + order.getClOrdId() + " type=" + changeType + " status=" + orderStatusStr);
    }

    private ExecutionReportMessage getOrCreateExecutionReport(final Order order, final Side side,
                                                              final long fillPrice, final long fillQty) {
        ExecutionReportMessage report = subscription.getExecutionReport(order.getClOrdId());
        if (report == null) {
            report = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(),
                    fillPrice, order.getPriceScale(), fillQty, order.getQtyScale(),
                    0, 0, 0, 0, side, 0);
        }
        return report;
    }

    /** Map KuCoin Futures order status + type to internal order status string. */
    private static String mapKuCoinFutureOrderStatus(final String status, final String changeType) {
        if (status == null) return null;
        switch (status) {
            case "open":
                return "NEW";
            case "match":
                return "PARTIALLY_FILLED";
            case "done":
                if ("filled".equals(changeType)) return "FILLED";
                if ("canceled".equals(changeType)) return "CANCELED";
                if ("update".equals(changeType)) return "CANCELED";
                return "FILLED";
            default:
                return null;
        }
    }

    private static OrdStatus toOrdStatus(final String orderStatusStr) {
        if (orderStatusStr == null) return null;
        try {
            return OrdStatus.valueOf(orderStatusStr);
        } catch (final Exception e) {
            return null;
        }
    }

    /** Map KuCoin Futures data.type (open, match, update, filled, canceled) to ExecType. */
    private static ExecType toExecType(final String changeType) {
        if (changeType == null) return null;
        return switch (changeType) {
            case "open" -> ExecType.NEW;
            case "match" -> ExecType.TRADE;
            case "filled" -> ExecType.TRADE;
            case "canceled" -> ExecType.CANCELED;
            case "update" -> ExecType.CANCELED;
            default -> null;
        };
    }

    /**
     * Handle position update from /contract/positionAll or /contract/position:SYMBOL (subject position.change).
     * Same as Binance applyAccountUpdate positions: subscription.updatePosition(symbol, positionAmount).
     */
    private void handlePositionMessage(final String msg) {
        final String dataStr = extractJsonValue(msg, "data");
        if (dataStr == null) return;
        final String symbol = minExtract(dataStr, "symbol");
        if (symbol == null) return;
        final double currentQty = parseDoubleSafe(minExtract(dataStr, "currentQty"));
        subscription.updatePosition(symbol, currentQty);
        LOGGER.info("KuCoin FutureUserData position update: " + symbol + "=" + currentQty);
    }

    /**
     * Handle balance update from /contractAccount/wallet (subject walletBalance.change or availableBalance.change).
     * Same as Binance applyAccountUpdate balance: push to subscription cache.
     */
    private void handleBalanceMessage(final String msg) {
        final String dataStr = extractJsonValue(msg, "data");
        if (dataStr == null) return;
        final String currency = minExtract(dataStr, "currency");
        if (currency == null) return;
        double balance = parseDoubleSafe(minExtract(dataStr, "walletBalance"));
        if (balance == 0.0) {
            balance = parseDoubleSafe(minExtract(dataStr, "availableBalance"));
        }
        subscription.updateBalance(currency, balance);
        LOGGER.info("KuCoin FutureUserData balance update: " + currency + "=" + balance);
    }
}
