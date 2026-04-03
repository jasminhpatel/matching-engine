package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.binance;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.BinanceFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketClientHandler;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.NettyWebSocketListenerInterface;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
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

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.util.PrivateKeyBasedSigner.signWithEd25519;

public class BinanceSpotTradeListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(BinanceSpotTradeListener.class);
    private static final String WS_URL = Context.getBinanceSpotWs();
    private static final long PING_INTERVAL_SECONDS = 25;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;
    private static final int PROXY_PORT = 8888;
    private final String apiKey;
    private final byte[] ed25519PrivateKey;
    private final ExchangeSubscription subscription;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();

    public BinanceSpotTradeListener(final String apiKey, final byte[] ed25519PrivateKey, final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.ed25519PrivateKey = ed25519PrivateKey;
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
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();
        final String proxyHost = subscription.getLastUsedProxy();
        final int proxyPort = PROXY_PORT;

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker =
                WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "BINANCE-SPOT-TRADE-LISTENER");

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

        LOGGER.info("Binance SpotTradeData WebSocket connected");
        authenticate();
    }

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

    private void authenticate() throws Exception {
        final long timestamp = System.currentTimeMillis();
        final String payload = "apiKey=" + apiKey + "&timestamp=" + timestamp;
        final String signature = signWithEd25519(payload, ed25519PrivateKey);

        final String authJson = "{" + "\"id\":\"1\"," + "\"method\":\"session.logon\"," + "\"params\":{" + "\"apiKey\":\"" + apiKey + "\","
                + "\"signature\":\"" + signature + "\"," + "\"timestamp\":" + timestamp + "}" + "}";

        LOGGER.info("Sending spot authentication JSON: " + authJson);

        channel.writeAndFlush(new TextWebSocketFrame(authJson));
    }

    private void schedulePeriodicPing() {
        stopPing();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                channel.writeAndFlush(new PingWebSocketFrame());
                LOGGER.debug("Sending Binance SpotTradeData ping");
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopPing() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
    }

    public void reconnect() {
        stopPing();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        channel.eventLoop().schedule(() -> {
            try {
                LOGGER.info("Binance SpotTradeData WebSocket reconnecting...");
                connect();
            } catch (Exception e) {
                LOGGER.error("Error during reconnect", e);
                reconnect();
            }
        }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
    }

    public void onMessage(final String message) {
        LOGGER.debug("Binance SpotTradeData Received message: " + message);
        // Only handle order placement responses - both success and error
        if (message.contains("\"status\":200") && message.contains("\"id\":\"1\"")) {
            LOGGER.info("Authentication successful!");
            setAuthenticated(true);
        } else if (message.contains("\"status\":") && message.contains("\"id\":")) {
            handleOrderResponse(message);
        } else {
            LOGGER.warn("Ignoring unhandled message on Spot Trade WS: " + message);
        }
    }


    // ----- Order Placement -----
    public void placeOrder(final String symbol, final String side, final String type, final String timeInForce, final String quantity,
                           final String price, final String clOrId) {
        final long timestamp = System.currentTimeMillis();

        final String orderJson = "{" + "\"id\":\"" + clOrId + "\"," + "\"method\":\"order.place\"," + "\"params\":{" + "\"symbol\":\"" + symbol
                + "\"," + "\"side\":\"" + side + "\"," + "\"type\":\"" + type + "\"," + "\"timeInForce\":\"" + timeInForce + "\"," //
                + "\"quantity\":\"" + quantity + "\"," + "\"price\":\"" + price + "\"," + "\"newClientOrderId\":\"" + BinanceFastClient.withBrokerId(subscription.getBrokerId(), clOrId) + "\","
                + "\"timestamp\":" + timestamp + "}" + "}";

        LOGGER.info("Sending trade order" + orderJson);
        channel.writeAndFlush(new TextWebSocketFrame(orderJson));
    }

    private void handleOrderResponse(final String message) {
        // --- Extract status ---
        final String statusKey = "\"status\":";
        final int statusIdx = message.indexOf(statusKey);
        int status = -1;
        if (statusIdx >= 0) {
            final int commaIdx = message.indexOf(',', statusIdx);
            final int endIdx = commaIdx < 0 ? message.length() : commaIdx;
            try {
                status = Integer.parseInt(message.substring(statusIdx + statusKey.length(), endIdx).trim());
            } catch (final Exception ignore) {
            }
        }

        // Extract client order ID from the message
        final String clientOrderId = minExtract(message, "id");
        if ("1".equalsIgnoreCase(clientOrderId)) // since it's auth response we can skip it
        {
            schedulePeriodicPing();
            return;
        }
        /*
         * // --- Handle SUCCESS (status == 200) --- if (status == 200) { String resultKey = "\"result\":"; int resultIdx =
         * message.indexOf(resultKey); if (resultIdx >= 0) { int resObjStart = message.indexOf('{', resultIdx); int resObjEnd =
         * message.indexOf('}', resObjStart); if (resObjStart >= 0 && resObjEnd > resObjStart) { String resultObj =
         * message.substring(resObjStart, resObjEnd + 1); String orderId = minExtract(resultObj, "orderId"); String orderClientId =
         * minExtract(resultObj, "clientOrderId"); String symbol = minExtract(resultObj, "symbol"); String price = minExtract(resultObj,
         * "price"); String origQty = minExtract(resultObj, "origQty"); String executedQty = minExtract(resultObj, "executedQty"); String
         * orderStatus = minExtract(resultObj, "status"); String type = minExtract(resultObj, "type"); String side = minExtract(resultObj,
         * "side");
         *
         * LOGGER.info("✅ Order Placed: orderId=" + orderId + ", clientOrderId=" + orderClientId + ", symbol=" + symbol + ", price=" + price +
         * ", quantity=" + origQty + ", executedQty=" + executedQty + ", orderStatus=" + orderStatus + ", type=" + type + ", side=" + side );
         *
         * // Update local object with success subscription.updateOrder(orderClientId, orderStatus); return; } } return; }
         */

        // --- Handle ERROR (status != 200) ---
        final int errorIdx = message.indexOf("\"error\":");
        if (errorIdx >= 0) {
            // Find error object boundaries
            final int errObjStart = message.indexOf('{', errorIdx);
            final int errObjEnd = message.indexOf('}', errObjStart);
            if (errObjStart >= 0 && errObjEnd > errObjStart) {
                final String errObj = message.substring(errObjStart, errObjEnd + 1);
                final String errMsg = minExtract(errObj, "msg");

                LOGGER.info("Order rejected by exchange for clOrId:" + clientOrderId + " for reason: " + errMsg);

                final Order order = subscription.getOrder(clientOrderId);

                // Create execution report for rejected order
                ExecutionReportMessage executionReportMessage = subscription.getExecutionReport(order.getClOrdId());
                if (executionReportMessage == null) {
                    executionReportMessage = ExecutionReportMessage.createExternalExecutionReport(
                            order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                            0, 0, 0, 0, order.getSide(), 0);
                }
                executionReportMessage.setClOrdId(clientOrderId);
                executionReportMessage.setError(errMsg);
                executionReportMessage.setExecType(ExecType.REJECTED);
                subscription.updateExecutionReport(executionReportMessage);
                //update order status after adding the execution report cache, otherwise cache lookup could return null
                subscription.updateOrder(clientOrderId, "REJECTED");
            }
        }
    }

}


