package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kucoin;

import com.solfini.common.CustomLogger;
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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public final class KucoinSpotUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(KucoinSpotUserDataListener.class);
    private static final String BALANCE_TOPIC = "/account/balance";
    private static final String ORDER_TOPIC = "/spotMarket/tradeOrdersV2";

    private static final long PING_INTERVAL_MS = 18_000;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;

    private final ExchangeSubscription subscription;
    private final Supplier<KucoinRestClient.PrivateTokenResult> tokenSupplier;
    private final String outboundIp;
    private final EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private volatile String token;
    private volatile String endpoint;
    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();

    public KucoinSpotUserDataListener(final ExchangeSubscription subscription,
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
        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "KUCOIN-SPOT-USER-DATA-LISTENER");

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
        LOGGER.info("KuCoin Spot UserData WebSocket connected");

        sendSubscribeBalanceInfo();
        sendSubscribeOrderInfo();
        schedulePing();
    }

    private void sendSubscribeBalanceInfo() {
        if (channel == null || !channel.isActive()) return;
        final long id = System.currentTimeMillis();
        final String subscribe = "{\"id\":\"" + id + "\",\"type\":\"subscribe\",\"topic\":\"" + BALANCE_TOPIC + "\",\"response\":true,\"privateChannel\":\"true\"}";
        channel.writeAndFlush(new TextWebSocketFrame(subscribe));
        LOGGER.debug("KuCoin Spot UserData subscribed to " + BALANCE_TOPIC);
    }

    private void sendSubscribeOrderInfo() {
        if (channel == null || !channel.isActive()) return;
        final long id = System.currentTimeMillis() + 1;
        final String subscribe = "{\"id\":\"" + id + "\",\"type\":\"subscribe\",\"topic\":\"" + ORDER_TOPIC + "\",\"response\":true,\"privateChannel\":\"true\"}";
        channel.writeAndFlush(new TextWebSocketFrame(subscribe));
        LOGGER.debug("KuCoin Spot UserData subscribed to " + ORDER_TOPIC);
    }


    private void schedulePing() {
        stopPing();
        if (channel == null) return;
        final long id = System.currentTimeMillis();
        final String subscribe = "{\"id\":\"" + id + "\",\"type\":\"ping\"}";

        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(subscribe));
                LOGGER.debug("Sending KuCoin Spot UserData ping");
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
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        group.shutdownGracefully();
        setConnected(false);
        setAuthenticated(false);
        LOGGER.info("KuCoin Spot UserData WebSocket disconnected");
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
                    LOGGER.info("KuCoin Spot UserData WebSocket reconnecting...");
                    connect();
                } catch (final Exception e) {
                    LOGGER.error("KuCoin Spot UserData reconnect failed: " + e.getMessage(), e);
                    reconnect();
                }
            }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onMessage(final String msg) {
        if (msg == null) return;
        LOGGER.debug("KuCoin Spot UserData received: " + msg);

        final String type = minExtract(msg, "type");
        if ("welcome".equals(type) || "ack".equals(type)) {
            return;
        }
        final String topic = minExtract(msg, "topic");
        if ("message".equals(type) && BALANCE_TOPIC.equals(topic)) {
            handleBalanceMessage(msg);
            return;
        }
        if ("message".equals(type) && ORDER_TOPIC.equals(topic)) {
            handleOrderMessage(msg);
        }
    }

    /**
     * Handle order updates from /spotMarket/tradeOrdersV2 (orderChange).
     * Setting procedure is split by data.type: received, open, update, match, filled, canceled.
     */
    private void handleOrderMessage(final String msg) {
        final String dataStr = extractJsonValue(msg, "data");
        if (dataStr == null) return;

        final String clientOid = minExtract(dataStr, "clientOid");
        if (clientOid == null) return;

        final Order order = subscription.getOrder(clientOid);
        if (order == null) {
            LOGGER.warn("KuCoin Spot UserData order not found in cache: clientOid=" + clientOid);
            return;
        }

        final String changeType = minExtract(dataStr, "type");
        if (changeType == null) return;

        final Side sideObj = "buy".equalsIgnoreCase(minExtract(dataStr, "side")) ? Side.BUY : Side.SELL;
        final String orderTypeStr = minExtract(dataStr, "orderType");
        final OrdType orderType = "market".equalsIgnoreCase(orderTypeStr) ? OrdType.MARKET : OrdType.LIMIT;
        final double originSize = parseDoubleSafe(minExtract(dataStr, "originSize"));
        final double size = parseDoubleSafe(minExtract(dataStr, "size"));
        final double priceDouble = parseDoubleSafe(minExtract(dataStr, "price"));
        final long orderQtyLong = MbxMath.changeScale(originSize > 0 ? originSize : size, order.getQtyScale());
        final long orderPriceLong = MbxMath.changeScale(priceDouble, order.getPriceScale());

        ExecutionReportMessage executionReportMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionReportMessage == null) {
            executionReportMessage = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(),
                    orderPriceLong, order.getPriceScale(), orderPriceLong, order.getQtyScale(),
                    0, 0, 0, 0, order.getSide(), 0);
        }

        executionReportMessage.setClOrdId(order.getClOrdId());
        executionReportMessage.setOrderQty(orderQtyLong);
        executionReportMessage.setOrderQtyScale(order.getQtyScale());
        executionReportMessage.setPrice(orderPriceLong);
        executionReportMessage.setPriceScale(order.getPriceScale());
        executionReportMessage.setOrdType(orderType);
        executionReportMessage.setTimeInForce(order.getTimeInForce());

        final String orderStatusStr;
        switch (changeType) {
            case "received":
                // Order ack: no fill fields
                executionReportMessage.setCumQty(0);
                executionReportMessage.setCumQtyScale(order.getQtyScale());
                executionReportMessage.setExecType(ExecType.NEW);
                executionReportMessage.setOrdStatus(OrdStatus.NEW);
                orderStatusStr = "NEW";
                break;
            case "open":
                // Order in book: size, filledSize=0, price
                executionReportMessage.setCumQty(0);
                executionReportMessage.setCumQtyScale(order.getQtyScale());
                executionReportMessage.setExecType(ExecType.NEW);
                executionReportMessage.setOrdStatus(OrdStatus.NEW);
                orderStatusStr = "NEW";
                break;
            case "update":
                // Partial cancel / size change: filledSize, remainSize
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
                LOGGER.debug("KuCoin Spot UserData order update: unknown type=" + changeType);
                return;
        }
        subscription.updateExecutionReport(executionReportMessage);
        subscription.updateOrder(order.getClOrdId(), orderStatusStr);
        LOGGER.info("KuCoin Spot UserData order update: clOrdId=" + order.getClOrdId() + " type=" + changeType + " status=" + orderStatusStr);
    }


    /**
     * Same as Binance handleOutboundAccountPosition / balance update: push balance to subscription cache.
     */
    private void handleBalanceMessage(final String msg) {
        final String dataStr = extractJsonValue(msg, "data");
        if (dataStr == null) return;
        final String currency = minExtract(dataStr, "currency");
        final double available = parseDoubleSafe(minExtract(dataStr, "available"));
        final double total = parseDoubleSafe(minExtract(dataStr, "total"));
        final double balance = available > 0.0 ? available : total;
        if (currency != null) {
            subscription.updateBalance(currency, balance);
            LOGGER.info("KuCoin Spot UserData balance update: " + currency + "=" + balance);
        }
    }
}
