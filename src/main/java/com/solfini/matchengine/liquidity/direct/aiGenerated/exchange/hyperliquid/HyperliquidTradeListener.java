package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.hyperliquid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.CustomLogger;
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
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class HyperliquidTradeListener implements NettyWebSocketListenerInterface {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(HyperliquidTradeListener.class);
  private static final String WS_URL_MAINNET = "wss://api.hyperliquid.xyz/ws";
  private static final String WS_URL_TESTNET = "wss://api.hyperliquid-testnet.xyz/ws";
  private static final long PING_INTERVAL_SECONDS = 30; // server closes the socket after 60s of silence
  private static final long PONG_TIMEOUT_MILLIS = 60_000;
  private static final int RECONNECT_DELAY_SEC = 5;
  private static final int MAX_CONTENT_LENGTH = 65536;
  private static final int SSL_PORT = 443;
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final ExchangeSubscription subscription;
  private final HyperliquidRestClient restClient;
  private final String wsUrl;

  private final EventLoopGroup group = new NioEventLoopGroup();
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final AtomicLong requestId = new AtomicLong(1);
  private final ConcurrentHashMap<Long, Order> pendingOrdersByRequestId = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, CompletableFuture<Boolean>> pendingCancelsByRequestId = new ConcurrentHashMap<>();
  private static final long CANCEL_RESPONSE_TIMEOUT_SECONDS = 5;
  private static final long ORDER_RESPONSE_TIMEOUT_SECONDS = 30;
  private Channel channel;
  private ScheduledFuture<?> pingFuture;
  private volatile long lastPongReceived = System.currentTimeMillis();

  public HyperliquidTradeListener(final ExchangeSubscription subscription, final HyperliquidRestClient restClient,
      final boolean mainnet) {
    this.subscription = subscription;
    this.restClient = restClient;
    this.wsUrl = mainnet ? WS_URL_MAINNET : WS_URL_TESTNET;
  }

  @Override
  public void connect() throws Exception {
    final URI uri = new URI(wsUrl);
    final String host = uri.getHost();
    final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();

    final SslContext sslCtx = SslContextBuilder.forClient().build();
    final WebSocketClientHandshaker handshaker =
        WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());
    final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "HYPERLIQUID-TRADE-LISTENER");

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

    this.channel = b.connect(host, port).sync().channel();
    handler.handshakeFuture().sync();
    connected.set(true);

    LOGGER.info("Hyperliquid Trade WebSocket connected");
    schedulePeriodicPing();
  }

  @Override
  public void disconnect() {
    stopped.set(true);
    stopPing();
    connected.set(false);
    if (channel != null && channel.isOpen()) {
      channel.close();
    }
    group.shutdownGracefully();
  }

  @Override
  public void reconnect() {
    if (stopped.get() || channel == null || group.isShuttingDown()) {
      return;
    }
    stopPing();
    connected.set(false);
    if (channel != null && channel.isOpen()) {
      channel.close();
    }
    channel.eventLoop().schedule(() -> {
      if (stopped.get()) {
        return;
      }
      try {
        LOGGER.info("Hyperliquid Trade WebSocket reconnecting...");
        connect();
      } catch (final Exception e) {
        LOGGER.error("Error during Hyperliquid Trade reconnect", e);
        reconnect();
      }
    }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
  }

  @Override
  public boolean isConnected() {
    return connected.get();
  }

  @Override
  public void setConnected(final boolean connected) {
    this.connected.set(connected);
  }

  @Override
  public void setAuthenticated(final boolean authenticated) {
    // No authentication handshake for Hyperliquid - the signed action payload is itself the auth.
  }

  @Override
  public void setLastPongReceived(final long timeStamp) {
    this.lastPongReceived = timeStamp;
  }

  @Override
  public void onBinaryMessage(final byte[] bytes) {
    // Hyperliquid only sends text frames.
  }

  /** Places a spot limit order (GTC) over the WebSocket instead of REST. Spot has no reduceOnly concept. */
  public void sendSpotOrder(final Order order, final int assetId, final boolean isBuy, final String price,
      final String qty, final String cloid) {
    sendSignedOrder(order, assetId, isBuy, price, qty, cloid, false);
  }

  /** Places a perp limit order (GTC or IOC, per {@code order.getTimeInForce()}) over the WebSocket instead of REST. */
  public void sendPerpOrder(final Order order, final int assetId, final boolean isBuy, final String price,
      final String qty, final String cloid, final boolean reduceOnly) {
    sendSignedOrder(order, assetId, isBuy, price, qty, cloid, reduceOnly);
  }

  private void sendSignedOrder(final Order order, final int assetId, final boolean isBuy, final String price,
      final String qty, final String cloid, final boolean reduceOnly) {
    try {
      final String signedPayloadJson = restClient.buildSignedOrderPayloadJson(assetId, isBuy, price, qty, cloid, reduceOnly,
          order.getTimeInForce());
      final long id = requestId.getAndIncrement();
      pendingOrdersByRequestId.put(id, order);
      channel.eventLoop().schedule(() -> {
        if (pendingOrdersByRequestId.remove(id) != null) {
          LOGGER.warn("Hyperliquid order response timed out for request id=" + id);
        }
      }, ORDER_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

      final String postJson = "{\"method\":\"post\",\"id\":" + id
          + ",\"request\":{\"type\":\"action\",\"payload\":" + signedPayloadJson + "}}";
      LOGGER.debug("Sending Hyperliquid order via WebSocket, id=" + id);
      channel.writeAndFlush(new TextWebSocketFrame(postJson));
    } catch (final Exception e) {
      LOGGER.error("Hyperliquid WS sendOrder failed for clOrdId " + order.getClOrdId() + ": " + e.getMessage(), e);
      markRejected(order, e.getMessage());
    }
  }

  /**
   * Cancels a resting order (spot or perp - same "cancelByCloid" action either side) over this
   * same WebSocket connection instead of REST. Unlike {@link #sendSpotOrder}/{@link #sendPerpOrder}
   * (fire-and-forget - the actual fill/reject state always arrives separately via the user-data
   * WS's orderUpdates push), cancel has no other channel telling the caller whether the cancel
   * itself was accepted, so this blocks for the response (bounded by
   * {@value #CANCEL_RESPONSE_TIMEOUT_SECONDS}s) to give the same synchronous true/false contract
   * {@code HyperliquidRestClient.cancelSpotOrderRest} already has.
   */
  public boolean cancelOrder(final int assetId, final String cloid) {
    final long id = requestId.getAndIncrement();
    try {
      final String signedPayloadJson = restClient.buildSignedCancelPayloadJson(assetId, cloid);
      final CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
      pendingCancelsByRequestId.put(id, resultFuture);

      final String postJson = "{\"method\":\"post\",\"id\":" + id
          + ",\"request\":{\"type\":\"action\",\"payload\":" + signedPayloadJson + "}}";
      LOGGER.debug("Sending Hyperliquid cancel via WebSocket, id=" + id);
      channel.writeAndFlush(new TextWebSocketFrame(postJson));

      return resultFuture.get(CANCEL_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (final Exception e) {
      // Timeout or disconnect after the write is ambiguous, not a confirmed failure.
      LOGGER.error("Hyperliquid WS cancelOrder failed for cloid " + cloid + ": " + e.getMessage(), e);
      return false;
    } finally {
      pendingCancelsByRequestId.remove(id);
    }
  }

  private void schedulePeriodicPing() {
    stopPing();
    pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
      if (channel.isActive()) {
        if (System.currentTimeMillis() - lastPongReceived > PONG_TIMEOUT_MILLIS) {
          LOGGER.warn("Hyperliquid Trade WebSocket: no pong received, reconnecting");
          reconnect();
          return;
        }
        channel.writeAndFlush(new TextWebSocketFrame("{\"method\":\"ping\"}"));
        LOGGER.debug("Sending Hyperliquid Trade ping");
      }
    }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  private void stopPing() {
    if (pingFuture != null) {
      pingFuture.cancel(false);
      pingFuture = null;
    }
  }

  @Override
  public void onMessage(final String msg) {
    LOGGER.debug("Hyperliquid Trade Received message: " + msg);
    final JsonNode root;
    try {
      root = JSON_MAPPER.readTree(msg);
    } catch (final Exception e) {
      LOGGER.error("Failed to parse Hyperliquid Trade message: " + msg, e);
      return;
    }

    final String channelName = root.path("channel").asText();
    if ("pong".equals(channelName)) {
      lastPongReceived = System.currentTimeMillis();
      return;
    }
    if ("error".equals(channelName)) {
      LOGGER.error("Hyperliquid Trade WebSocket error: " + root.path("data").asText());
      return;
    }
    if (!"post".equals(channelName)) {
      LOGGER.debug("Unhandled Hyperliquid Trade channel: " + channelName);
      return;
    }

    final JsonNode data = root.path("data");
    final long id = data.path("id").asLong();

    // data.response = {type:"action", payload:{status, response}} - same shape as the REST
    // /exchange response body, just nested one level deeper under "response.payload".
    final JsonNode payload = data.path("response").path("payload");

    final CompletableFuture<Boolean> pendingCancel = pendingCancelsByRequestId.remove(id);
    if (pendingCancel != null) {
      pendingCancel.complete(isCancelSuccess(payload));
      return;
    }

    final Order order = pendingOrdersByRequestId.remove(id);
    if (order == null) {
      LOGGER.warn("Hyperliquid Trade: no pending order/cancel for request id=" + id);
      return;
    }
    if (!"ok".equalsIgnoreCase(payload.path("status").asText())) {
      markRejected(order, payload.path("response").asText());
      return;
    }
    final JsonNode statuses = payload.path("response").path("data").path("statuses");
    final JsonNode status = statuses.isArray() && statuses.size() > 0 ? statuses.get(0) : payload;
    applyOrderStatus(order, status);
  }

  /** Same per-cancel outcome shape as the REST path: statuses[0] is "success" or {"error": "..."}. */
  private boolean isCancelSuccess(final JsonNode payload) {
    if (!"ok".equalsIgnoreCase(payload.path("status").asText())) {
      LOGGER.warn("Hyperliquid WS cancel rejected: " + payload.path("response").asText());
      return false;
    }
    final JsonNode statuses = payload.path("response").path("data").path("statuses");
    if (!statuses.isArray() || statuses.size() != 1) {
      LOGGER.warn("Hyperliquid WS cancel response has unexpected shape: " + payload);
      return false;
    }
    final JsonNode status = statuses.get(0);
    final boolean success = status.isTextual() && "success".equalsIgnoreCase(status.asText());
    if (!success) {
      LOGGER.warn("Hyperliquid WS cancel not confirmed: " + status);
    }
    return success;
  }

  private void applyOrderStatus(final Order order, final JsonNode status) {
    final ExecutionReportMessage message = executionReport(order);
    if (status.has("filled")) {
      final JsonNode filled = status.path("filled");
      final long filledQty = MbxMath.changeScale(filled.path("totalSz").asDouble(), order.getQtyScale());
      final long leavesQty = Math.max(0, order.getQty() - filledQty);
      order.setOrderId(filled.path("oid").asLong());
      order.setExecuted(true); // IOC is terminal even when only part of the requested size filled.
      message.setOrdStatus(leavesQty == 0 ? OrdStatus.FILLED : OrdStatus.PARTIALLY_FILLED);
      message.setExecType(ExecType.TRADE);
      message.setCumQty(filledQty);
      message.setCumQtyScale(order.getQtyScale());
      message.setAvgPx(MbxMath.changeScale(filled.path("avgPx").asDouble(), order.getPriceScale()));
      message.setAvgPxScale(order.getPriceScale());
      message.setLeavesQty(leavesQty);
      message.setLeavesQtyScale(order.getQtyScale());
    } else if (status.has("resting")) {
      order.setOrderId(status.path("resting").path("oid").asLong());
      message.setOrdStatus(OrdStatus.NEW);
      message.setExecType(ExecType.NEW);
    } else {
      final String error = status.has("error") ? status.path("error").asText() : status.toString();
      order.setRejected(true);
      message.setOrdStatus(OrdStatus.REJECTED);
      message.setExecType(ExecType.REJECTED);
      message.setError(error);
    }
    subscription.updateExecutionReport(message);
    subscription.updateOrder(order.getClOrdId(), order);
  }

  private void markRejected(final Order order, final String reason) {
    final ExecutionReportMessage message = executionReport(order);
    order.setRejected(true);
    message.setOrdStatus(OrdStatus.REJECTED);
    message.setExecType(ExecType.REJECTED);
    message.setError(reason);
    subscription.updateExecutionReport(message);
    subscription.updateOrder(order.getClOrdId(), order);
  }

  private ExecutionReportMessage executionReport(final Order order) {
    ExecutionReportMessage message = subscription.getExecutionReport(order.getClOrdId());
    if (message == null) {
      message = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0,
          order.getSymbol(), 0L, order.getPriceScale(), 0L, order.getQtyScale(), 0, 0, 0, 0,
          order.getSide(), 0L);
    }
    message.setClOrdId(order.getClOrdId());
    message.setOrderQty(order.getQty());
    message.setOrderQtyScale(order.getQtyScale());
    message.setPrice(order.getPrice());
    message.setPriceScale(order.getPriceScale());
    return message;
  }
}
