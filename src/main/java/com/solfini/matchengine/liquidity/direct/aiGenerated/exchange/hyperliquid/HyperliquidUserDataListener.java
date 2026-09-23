package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.hyperliquid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.HyperliquidFastClient;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HyperliquidUserDataListener implements NettyWebSocketListenerInterface {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(HyperliquidUserDataListener.class);
  private static final String WS_URL_MAINNET = "wss://api.hyperliquid.xyz/ws";
  private static final String WS_URL_TESTNET = "wss://api.hyperliquid-testnet.xyz/ws";
  private static final long PING_INTERVAL_SECONDS = 30; // server closes the socket after 60s of silence
  private static final long PONG_TIMEOUT_MILLIS = 60_000;
  private static final int RECONNECT_DELAY_SEC = 5;
  private static final int MAX_CONTENT_LENGTH = 65536;
  private static final int SSL_PORT = 443;
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final String walletAddress;
  private final ExchangeSubscription subscription;
  private final String wsUrl;
  private final java.util.function.Consumer<String> terminalOrder;

  private final EventLoopGroup group = new NioEventLoopGroup();
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private Channel channel;
  private ScheduledFuture<?> pingFuture;
  private volatile long lastPongReceived = System.currentTimeMillis();

  // Reconnect snapshots can redeliver fills already applied.
  private final Set<String> processedFillIds = ConcurrentHashMap.newKeySet();

  public HyperliquidUserDataListener(final String walletAddress, final ExchangeSubscription subscription,
      final boolean mainnet) {
    this(walletAddress, subscription, mainnet, ignored -> { });
  }

  public HyperliquidUserDataListener(final String walletAddress, final ExchangeSubscription subscription,
      final boolean mainnet, final java.util.function.Consumer<String> terminalOrder) {
    this.terminalOrder = terminalOrder;
    this.walletAddress = walletAddress;
    this.subscription = subscription;
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
    final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "HYPERLIQUID-DATA-LISTENER");

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

    LOGGER.info("Hyperliquid UserData WebSocket connected for wallet: " + walletAddress);
    subscribe("spotState");
    subscribe("clearinghouseState", "");
    subscribe("clearinghouseState", HyperliquidRestClient.COMMODITIES_DEX);
    subscribe("orderUpdates");
    subscribe("userFills");
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
        LOGGER.info("Hyperliquid UserData WebSocket reconnecting...");
        connect();
      } catch (final Exception e) {
        LOGGER.error("Error during Hyperliquid UserData reconnect", e);
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
    // No authentication handshake for Hyperliquid's read subscriptions - unused.
  }

  @Override
  public void setLastPongReceived(final long timeStamp) {
    this.lastPongReceived = timeStamp;
  }

  @Override
  public void onBinaryMessage(final byte[] bytes) {
    // Hyperliquid only sends text frames for these subscriptions.
  }

  private void subscribe(final String type) {
    subscribe(type, null);
  }

  private void subscribe(final String type, final String dex) {
    final String dexField = dex == null ? "" : ",\"dex\":\"" + dex + "\"";
    final String aggregateByTime = "userFills".equals(type) ? ",\"aggregateByTime\":true" : "";
    final String subJson = "{\"method\":\"subscribe\",\"subscription\":{\"type\":\"" + type
        + "\",\"user\":\"" + walletAddress + "\"" + aggregateByTime + dexField + "}}";
    LOGGER.info("Subscribing to Hyperliquid " + type + ": " + subJson);
    channel.writeAndFlush(new TextWebSocketFrame(subJson));
  }

  private void schedulePeriodicPing() {
    stopPing();
    pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
      if (channel.isActive()) {
        if (System.currentTimeMillis() - lastPongReceived > PONG_TIMEOUT_MILLIS) {
          LOGGER.warn("Hyperliquid UserData WebSocket: no pong received, reconnecting");
          reconnect();
          return;
        }
        channel.writeAndFlush(new TextWebSocketFrame("{\"method\":\"ping\"}"));
        LOGGER.debug("Sending Hyperliquid UserData ping");
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
    LOGGER.debug("Hyperliquid UserData Received message: " + msg);
    final JsonNode root;
    try {
      root = JSON_MAPPER.readTree(msg);
    } catch (final Exception e) {
      LOGGER.error("Failed to parse Hyperliquid UserData message: " + msg, e);
      return;
    }

    final String channelName = root.path("channel").asText();
    switch (channelName) {
      case "subscriptionResponse":
        LOGGER.info("Hyperliquid subscription acknowledged: " + root.path("data").toString());
        break;
      case "pong":
        lastPongReceived = System.currentTimeMillis();
        break;
      case "spotState":
        handleSpotState(root.path("data"));
        break;
      case "clearinghouseState":
        handleClearinghouseState(root.path("data").path("clearinghouseState"),
            root.path("data").path("dex").asText());
        break;
      case "orderUpdates":
        handleOrderUpdates(root.path("data"));
        break;
      case "userFills":
        handleUserFills(root.path("data"));
        break;
      case "error":
        LOGGER.error("Hyperliquid UserData WebSocket error: " + root.path("data").asText());
        break;
      default:
        LOGGER.debug("Unhandled Hyperliquid UserData channel: " + channelName);
        break;
    }
  }

  /** {@code data: {user, spotState: {balances: [{coin, token, hold, total, entryNtl}, ...]}}}. */
  private void handleSpotState(final JsonNode data) {
    final JsonNode balances = data.path("spotState").path("balances");
    for (final JsonNode balance : balances) {
      final String coin = balance.path("coin").asText();
      final double total = balance.path("total").asDouble();
      final double hold = balance.path("hold").asDouble();
      LOGGER.debug("Hyperliquid SPOT DATA >>> Coin: " + coin + " Total=" + total + " Hold=" + hold);
      if (!coin.isEmpty()) {
        subscription.updateBalance(coin, total - hold);
      }
    }
  }

  /**
   * {@code {marginSummary:{accountValue,...}, assetPositions:[{position:{coin,szi,...}}, ...], ...}}.
   * Positions only - accountValue is deliberately NOT written to the subscription's USDC balance
   * here. It's a narrower figure (margin currently deployed + PnL), not the account's overall USDC
   * balance, and would stomp on the same "USDC" field the spotState handler above populates
   * (Unified Account - one balance, one source of truth).
   */
  private void handleClearinghouseState(final JsonNode state, final String dex) {
    HyperliquidPositionSnapshot.apply(subscription, state, dex);
  }

  /**
   * {@code data} carries the per-order updates, either as a bare array or wrapped as
   * {@code {user, orders: [...]}} - each entry is {@code {order: {...}, status, statusTimestamp}}.
   * Covers both spot and perp orders - Hyperliquid's orderUpdates has no market-type split.
   */
  private void handleOrderUpdates(final JsonNode data) {
    final JsonNode orders = data.isArray() ? data : data.path("orders");
    for (final JsonNode entry : orders) {
      final JsonNode orderNode = entry.path("order");
      final long oid = orderNode.path("oid").asLong();
      final String cloid = orderNode.has("cloid") && !orderNode.path("cloid").isNull()
          ? orderNode.path("cloid").asText() : null;
      final String hyperliquidStatus = entry.path("status").asText();

      final Order order = findCachedOrder(oid, cloid);
      if (order == null) {
        LOGGER.debug("Hyperliquid orderUpdates: no cached order for oid=" + oid + " cloid=" + cloid);
        continue;
      }
      order.setOrderId(oid);
      applyOrderStatus(order, hyperliquidStatus);
    }
  }

  /**
   * Correlates by cloid first when the update carries one - deterministic, and doesn't depend on
   * the exchange oid having been learned yet (an update can otherwise arrive before the
   * order-placement response does). Falls back to oid when no cloid is present.
   */
  private Order findCachedOrder(final long oid, final String cloid) {
    if (cloid != null) {
      for (final Order cachedOrder : subscription.getOrders().values()) {
        if (cloid.equalsIgnoreCase(HyperliquidFastClient.toCloid(cachedOrder.getClOrdId()))) {
          return cachedOrder;
        }
      }
    }
    for (final Order cachedOrder : subscription.getOrders().values()) {
      if (cachedOrder.getOrderId() == oid) {
        return cachedOrder;
      }
    }
    return null;
  }

  /** Maps Hyperliquid's documented terminal order statuses. */
  private void applyOrderStatus(final Order order, final String hyperliquidStatus) {
    final ExecutionReportMessage message = executionReport(order);
    if (HyperliquidFastClient.isOpenStatus(hyperliquidStatus)) {
      message.setOrdStatus(OrdStatus.NEW);
      message.setExecType(ExecType.NEW);
    } else if ("filled".equalsIgnoreCase(hyperliquidStatus)) {
      order.setExecuted(true);
      message.setOrdStatus(OrdStatus.FILLED);
      message.setExecType(ExecType.TRADE);
    } else if (HyperliquidFastClient.isCanceledStatus(hyperliquidStatus)) {
      message.setOrdStatus(OrdStatus.CANCELED);
      message.setExecType(ExecType.CANCELED);
      if (message.getCumQty() > 0) {
        order.setExecuted(true);
      } else {
        order.setRejected(true);
      }
    } else if (HyperliquidFastClient.isRejectedStatus(hyperliquidStatus)) {
      order.setRejected(true);
      message.setOrdStatus(OrdStatus.REJECTED);
      message.setExecType(ExecType.REJECTED);
    } else {
      LOGGER.warn("Hyperliquid orderUpdates: unrecognized status '" + hyperliquidStatus
          + "' for clOrdId=" + order.getClOrdId());
    }
    LOGGER.info("Hyperliquid ORDER UPDATE >>> clOrdId=" + order.getClOrdId() + " status=" + hyperliquidStatus);
    subscription.updateExecutionReport(message);
    subscription.updateOrder(order.getClOrdId(), order);
    if (message.getOrdStatus() == OrdStatus.FILLED || message.getOrdStatus() == OrdStatus.CANCELED
        || message.getOrdStatus() == OrdStatus.REJECTED) {
      terminalOrder.accept(order.getClOrdId());
    }
  }

  private void handleUserFills(final JsonNode data) {
    for (final JsonNode fill : data.path("fills")) {
      applyFill(fill);
    }
  }

  private void applyFill(final JsonNode fill) {
    final String coin = fill.path("coin").asText();
    final long time = fill.path("time").asLong();
    final long tid = fill.path("tid").asLong();
    final String fillId = time + ":" + coin + ":" + tid;

    final long oid = fill.path("oid").asLong();
    Order order = null;
    for (final Order cachedOrder : subscription.getOrders().values()) {
      if (cachedOrder.getOrderId() == oid) {
        order = cachedOrder;
        break;
      }
    }
    if (order == null) {
      LOGGER.debug("Hyperliquid userFills: no cached order for oid=" + oid + " coin=" + coin);
      return;
    }
    final ExecutionReportMessage message = executionReport(order);

    // Record the dedup key only after the fill can actually be applied. This permits a snapshot
    // replay to recover a fill that arrived before its order was restored into the local cache.
    if (!processedFillIds.add(fillId)) {
      return;
    }

    final long fillQty = MbxMath.changeScale(fill.path("sz").asDouble(), order.getQtyScale());
    final long fillPrice = MbxMath.changeScale(fill.path("px").asDouble(), order.getPriceScale());

    final long previousCumQty = message.getCumQty();
    final long previousAvgPx = message.getAvgPx();
    // An immediate execution is already represented by the signed order response. The fill event
    // is still needed for fee data, but must not add the same quantity twice.
    final boolean quantityAlreadyApplied = order.isExecuted() && previousCumQty > 0;
    final long newCumQty = quantityAlreadyApplied ? previousCumQty : previousCumQty + fillQty;
    final long newAvgPx = quantityAlreadyApplied ? previousAvgPx
        : newCumQty == 0 ? 0
        : Math.round((previousAvgPx * (double) previousCumQty + fillPrice * (double) fillQty) / newCumQty);
    final long leavesQty = Math.max(0, order.getQty() - newCumQty);

    message.setCumQty(newCumQty);
    message.setCumQtyScale(order.getQtyScale());
    message.setAvgPx(newAvgPx);
    message.setAvgPxScale(order.getPriceScale());
    message.setLastPx(fillPrice);
    message.setLastPxScale(order.getPriceScale());
    message.setLastQty(fillQty);
    message.setLastQtyScale(order.getQtyScale());
    message.setLeavesQty(leavesQty);
    message.setLeavesQtyScale(order.getQtyScale());

    final String feeToken = fill.path("feeToken").asText(null);
    if (feeToken != null && !feeToken.isEmpty()) {
      final Instrument feeInstrument = InstrumentCache.getBySymbol(feeToken);
      if (feeInstrument != null) {
        final long feeQty = MbxMath.changeScale(fill.path("fee").asDouble(0), feeInstrument.getQuantityScale());
        message.setFeeAccumulatedQuantity(message.getFeeAccumulatedQuantity() + feeQty);
        message.setFeePositionId(feeInstrument.getId());
      }
    }

    final boolean fullyFilled = leavesQty <= 0;
    message.setExecType(ExecType.TRADE);
    message.setOrdStatus(fullyFilled ? OrdStatus.FILLED : OrdStatus.PARTIALLY_FILLED);
    if (fullyFilled) {
      // Only a full fill is terminal here - a partial fill leaves a GTC order resting; its
      // eventual terminal state (further fills, or a cancel) arrives via later userFills/
      // orderUpdates pushes.
      order.setExecuted(true);
    }

    LOGGER.info("Hyperliquid FILL >>> clOrdId=" + order.getClOrdId() + " coin=" + coin
        + " fillQty=" + fill.path("sz").asText() + " fillPx=" + fill.path("px").asText()
        + " cumQty=" + newCumQty + " leavesQty=" + leavesQty);
    subscription.updateExecutionReport(message);
    subscription.updateOrder(order.getClOrdId(), order);
    if (message.getOrdStatus() == OrdStatus.FILLED || message.getOrdStatus() == OrdStatus.CANCELED
        || message.getOrdStatus() == OrdStatus.REJECTED) {
      terminalOrder.accept(order.getClOrdId());
    }
  }

  private ExecutionReportMessage executionReport(final Order order) {
    ExecutionReportMessage message = subscription.getExecutionReport(order.getClOrdId());
    if (message == null) {
      message = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0,
          order.getSymbol(), 0L, order.getPriceScale(), 0L, order.getQtyScale(), 0, 0, 0, 0,
          order.getSide(), 0L);
      message.setClOrdId(order.getClOrdId());
      message.setOrderQty(order.getQty());
      message.setOrderQtyScale(order.getQtyScale());
      message.setPrice(order.getPrice());
      message.setPriceScale(order.getPriceScale());
      message.setLeavesQty(order.getQty());
      message.setLeavesQtyScale(order.getQtyScale());
    }
    return message;
  }
}
