package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitmart;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseLongSafe;
import com.solfini.util.HMAC;
import io.netty.handler.proxy.HttpProxyHandler;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import com.solfini.sbe.encoder.TimeInForce;
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
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

public final class BitmartSpotUserDataListener implements NettyWebSocketListenerInterface {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(BitmartSpotUserDataListener.class);
  private static final String WS_URL = "wss://ws-manager-compress.bitmart.com/user?protocol=1.1";
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
  private Channel channel;
  private ScheduledFuture<?> pingFuture;
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final AtomicBoolean authenticated = new AtomicBoolean(false);
  private volatile long lastPongReceived = System.currentTimeMillis();

  public BitmartSpotUserDataListener(final String apiKey, final String apiSecret, final String apiMemo, final ExchangeSubscription subscription) {
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.apiMemo=apiMemo;
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

  @Override
  public void onBinaryMessage(byte[] bytes) {
  }

  public void connect() throws Exception {
        LOGGER.info("Initiating BitMart SpotUserData WebSocket connection to: " + WS_URL);
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();
        final String proxyHost = subscription.getLastUsedProxy();
        final int proxyPort = PROXY_PORT;

        LOGGER.debug("Connection parameters - Host: " + host + ", Port: " + port + ", Proxy: " + proxyHost);
        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker =
            WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "BITMART-SPOT-USER-DATA-LISTENER");

        final Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(final Channel ch) {
            final ChannelPipeline p = ch.pipeline();
            if (proxyHost != null) {
              LOGGER.debug("Adding proxy handler for: " + proxyHost + ":" + proxyPort);
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

        LOGGER.info("BitMart SpotUserData WebSocket connected");
        authenticate();
    }

    public void disconnect() {
        LOGGER.info("Disconnecting BitMart SpotUserData WebSocket");
        stopPing();
        if (channel != null && channel.isOpen()) {
            channel.close();
            LOGGER.debug("Channel closed");
        }
        group.shutdownGracefully();
        LOGGER.info("BitMart SpotUserData WebSocket disconnected");
    }

  @Override
  public boolean isConnected() {
    return connected.get() && authenticated.get();
  }

    private void authenticate() throws Exception {
        LOGGER.debug("Authenticating BitMart spot user data connection");
        final long timestamp = System.currentTimeMillis();
        final String message = timestamp + "#" + apiMemo + "#bitmart.WebSocket";
        final String sign = HMAC.hmacSha256(message, apiSecret);
        final String loginMsg = "{\"op\":\"login\",\"args\":[\"" + apiKey + "\",\"" + timestamp + "\",\"" + sign + "\"]}";
        LOGGER.info("Sending BitMart spot user data authentication JSON: " + loginMsg);
        channel.writeAndFlush(new TextWebSocketFrame(loginMsg));
    }

    private void schedulePeriodicPing() {
        LOGGER.debug("Scheduling periodic ping with interval: " + PING_INTERVAL_SECONDS + " seconds");
        stopPing();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                channel.writeAndFlush(new PingWebSocketFrame());
                LOGGER.debug("Sending BitMart SpotUserData ping");
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
        LOGGER.warn("Initiating reconnection for BitMart SpotUserData WebSocket");
        stopPing();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        channel.eventLoop().schedule(() -> {
            try {
                LOGGER.info("BitMart SpotUserData WebSocket reconnecting...");
                connect();
            } catch (final Exception e) {
                LOGGER.error("Error during reconnect", e);
                reconnect();
            }
        }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
    }

    public void onMessage(final String msg) {
        LOGGER.debug("BitMart SpotUserData Received message: " + msg);

        if (msg.contains("\"event\":\"login\"")) {
            handleAuthResponse(msg);
        } else if (msg.contains("\"event\":\"subscribe\"")) {
            handleSubscriptionConfirmation(msg);
        } else if (msg.contains("\"table\":\"spot/user/orders\"")) {
            handleOrderUpdate(msg);
        } else if (msg.contains("\"table\":\"spot/user/balance\"")) {
            handleBalanceUpdate(msg);
        } else {
            LOGGER.info("Unhandled message type: " + msg);
        }
    }

    private void handleAuthResponse(final String message) {
        LOGGER.debug("Processing authentication response");
        if (message.contains("\"event\":\"login\"")) {
            LOGGER.info("BitMart Spot User Data Authentication successful!");
            setAuthenticated(true);
            subscribeToUserDataStreams();
            schedulePeriodicPing();
        } else {
            LOGGER.error("BitMart Spot User Data Authentication failed: " + message);
        }
    }

    private void handleSubscriptionConfirmation(final String message) {
        LOGGER.debug("Processing subscription confirmation");
        final String topic = minExtract(message, "topic");
        
        if (topic != null) {
            if (topic.contains("spot/user/orders:ALL_SYMBOLS")) {
                LOGGER.info("Successfully subscribed to BitMart spot/user/orders:ALL_SYMBOLS");
            } else if (topic.contains("spot/user/balance:BALANCE_UPDATE")) {
                LOGGER.info("Successfully subscribed to BitMart spot/user/balance:BALANCE_UPDATE");
            } else {
                LOGGER.info("Successfully subscribed to topic: " + topic);
            }
        } else {
            LOGGER.warn("Subscription confirmation received but topic extraction failed: " + message);
        }
    }

    private void subscribeToUserDataStreams() {
        LOGGER.info("Subscribing to BitMart user data streams");
        final String balanceSub = "{\"op\":\"subscribe\",\"args\":[\"spot/user/balance:BALANCE_UPDATE\"]}";
        final String ordersSub = "{\"op\":\"subscribe\",\"args\":[\"spot/user/orders:ALL_SYMBOLS\"]}";
        
        LOGGER.debug("Subscribing to BitMart spot user balance stream: " + balanceSub);
        channel.writeAndFlush(new TextWebSocketFrame(balanceSub));
        
        LOGGER.debug("Subscribing to BitMart spot user orders stream: " + ordersSub);
        channel.writeAndFlush(new TextWebSocketFrame(ordersSub));
    }

    private void handleOrderUpdate(final String msg) {
        LOGGER.info("ORDER UPDATE RAW: " + msg);
        
        final int dataStart = msg.indexOf("\"data\":[");
        if (dataStart == -1) {
            LOGGER.warn("No data array found in order update message");
            return;
        }
        
        final int arrayStart = msg.indexOf("[", dataStart);
        final int arrayEnd = msg.lastIndexOf("]");
        if (arrayStart == -1 || arrayEnd == -1) {
            LOGGER.warn("Invalid data array format in order update message");
            return;
        }
        
        final String dataContent = msg.substring(arrayStart, arrayEnd + 1);
        LOGGER.debug("Extracted data content length: " + dataContent.length());
        
        // Parse each order object
        int pos = 0;
        int orderCount = 0;
        while (pos < dataContent.length()) {
            final int objStart = dataContent.indexOf("{", pos);
            if (objStart == -1) break;
            
            final int objEnd = dataContent.indexOf("}", objStart);
            if (objEnd == -1) break;
            
            final String orderObj = dataContent.substring(objStart, objEnd + 1);
            parseOrderUpdate(orderObj);
            orderCount++;
            
            pos = objEnd + 1;
        }
        LOGGER.info("Processed " + orderCount + " order updates");
    }

    private void parseOrderUpdate(final String json) {
        LOGGER.debug("Parsing order update");
        // Extract order fields
        final String symbolStr = minExtract(json, "symbol");
        final String clientOrderIdStr = minExtract(json, "client_order_id");
        final String sideStr = minExtract(json, "side");
        final String orderTypeStr = minExtract(json, "type");
        final String orderStateStr = minExtract(json, "order_state");
        final String priceStr = minExtract(json, "price");
        final String sizeStr = minExtract(json, "size");
        final String filledSizeStr = minExtract(json, "filled_size");
        final String filledNotionalStr = minExtract(json, "filled_notional");
        final String lastFillPriceStr = minExtract(json, "last_fill_price");
        final String lastFillCountStr = minExtract(json, "last_fill_count");
        final String execTypeStr = minExtract(json, "exec_type");
        final String detailIdStr = minExtract(json, "detail_id");
        final String dealFeeStr = minExtract(json, "dealFee");
        final String dealFeeCoinStr = minExtract(json, "deal_fee_coin_name");
        
        if (symbolStr == null || clientOrderIdStr == null || orderStateStr == null) {
            LOGGER.warn("Missing required fields in order update - Symbol: " + symbolStr + ", ClientOrderId: " + clientOrderIdStr + ", OrderState: " + orderStateStr);
            return;
        }

        LOGGER.info("ORDER EXECUTION REPORT >> Symbol: " + symbolStr + ", ClientOrderId: " + clientOrderIdStr + 
                    ", OrderState: " + orderStateStr + ", Side: " + sideStr +
                    ", FilledSize: " + filledSizeStr);

        final Order order = subscription.getOrder(clientOrderIdStr);
        if (order == null) {
            LOGGER.warn("Order not found in cache for clientOrderId: " + clientOrderIdStr);
            return;
        }

        LOGGER.debug("Order found in cache, updating status to: " + orderStateStr);


        // Parse numeric values
        final long tradeId = parseLongSafe(detailIdStr);
        final double lastFilledPriceDouble = parseDoubleSafe(lastFillPriceStr);
        final double lastFilledQtyDouble = parseDoubleSafe(lastFillCountStr);
        final double cumulativeFilledQtyDouble = parseDoubleSafe(filledSizeStr);
        final double orderQtyDouble = parseDoubleSafe(sizeStr);
        final double orderPriceDouble = parseDoubleSafe(priceStr);
        final double feesDouble = parseDoubleSafe(dealFeeStr);

        // Convert to Side
        final Side sideObj = "buy".equalsIgnoreCase(sideStr) ? Side.BUY : Side.SELL;

        // Map BitMart order state to OrdStatus
        final OrdStatus orderStatus = mapBitmartStateToOrdStatus(orderStateStr);
        
        // Map BitMart type to OrdType
        final OrdType orderType = mapBitmartTypeToOrdType(orderTypeStr);
        
        // Map BitMart exec type to ExecType
        final ExecType execType = mapBitmartExecType(execTypeStr, orderStateStr);

        // Convert prices and quantities to scaled long values
        final long lastFilledPriceLong = MbxMath.changeScale(lastFilledPriceDouble, order.getPriceScale());
        final long lastFilledQtyLong = MbxMath.changeScale(lastFilledQtyDouble, order.getQtyScale());
        final long orderQtyLong = MbxMath.changeScale(orderQtyDouble, order.getQtyScale());
        final long cumQtyLong = MbxMath.changeScale(cumulativeFilledQtyDouble, order.getQtyScale());
        final long orderPriceLong = MbxMath.changeScale(orderPriceDouble, order.getPriceScale());

        // Convert commission to long with instrument qty scale
        long feesLong = (long) feesDouble;
        if (dealFeeCoinStr != null) {

          final Instrument feesInstrument = InstrumentCache.getBySymbol(dealFeeCoinStr == null || dealFeeCoinStr.isBlank() ? getDefaultQuotedCurrency(order.getSymbol()) : dealFeeCoinStr);
          if (feesInstrument != null) {
            feesLong = MbxMath.changeScale(feesDouble, feesInstrument.getQuantityScale());
            LOGGER.debug("Commission calculated: " + feesLong + " for coin: " + dealFeeCoinStr);
          } else {
            LOGGER.warn("Fee instrument not found for: " + dealFeeCoinStr);
          }
        }

        LOGGER.debug("Creating execution report for orderId: " + order.getOrderId() + ", tradeId: " + tradeId);

        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
            // Create execution report message
            executionMessage =ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(),
                    order.getUser(),
                    0,
                    symbolStr,
                    lastFilledPriceLong,
                    order.getPriceScale(),
                    lastFilledQtyLong,
                    order.getQtyScale(),
                    tradeId,
                    0,
                    0,
                    0,
                    sideObj,
                    feesLong
            );
        }

        executionMessage.setExecId(tradeId);
        executionMessage.setFeeAccumulatedQuantity(feesLong);
        executionMessage.setPrice(lastFilledPriceLong);
        executionMessage.setPriceScale(order.getPriceScale());
        executionMessage.setLastPx(lastFilledPriceLong);
        executionMessage.setLastPxScale(order.getPriceScale());
        executionMessage.setOrderQty(lastFilledQtyLong);
        executionMessage.setOrderQtyScale(order.getQtyScale());
        executionMessage.setLastQty(lastFilledQtyLong);
        executionMessage.setLastQtyScale(order.getQtyScale());
        executionMessage.setCumQty(cumQtyLong);
        executionMessage.setCumQtyScale(order.getQtyScale());
        executionMessage.setClOrdId(clientOrderIdStr);
        executionMessage.setOrdStatus(orderStatus);
        executionMessage.setExecType(execType);
        executionMessage.setOrdType(orderType);
        executionMessage.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL); // BitMart default

        LOGGER.info("Updating execution report for clientOrderId: " + clientOrderIdStr + ", Status: " + orderStatus + ", ExecType: " + execType);
        subscription.updateExecutionReport(executionMessage);

        // Update order status in cache
        subscription.updateOrder(clientOrderIdStr, orderStateStr);
    }

    private static final OrdStatus mapBitmartStateToOrdStatus(final String orderState) {
    // state: 1=pending, 2=failed, 4=done, 5=pending cancel, 6=incomplete cancel, 7=complete cancel
    // order_state: new, partially_filled, filled, canceled, pending_cancel, failed
    
    if (orderState != null) {
      return switch (orderState.toLowerCase()) {
        case "new" -> OrdStatus.NEW;
        case "partially_filled" -> OrdStatus.PARTIALLY_FILLED;
        case "filled" -> OrdStatus.FILLED;
        case "canceled", "pending_cancel" -> OrdStatus.CANCELED;
        case "failed" -> OrdStatus.REJECTED;
        default -> OrdStatus.NEW;
      };
    }
    return OrdStatus.NULL_VAL;
  }

  private OrdType mapBitmartTypeToOrdType(final String type) {
    if (type == null) return OrdType.LIMIT;
    
    return switch (type.toLowerCase()) {
      case "limit" -> OrdType.LIMIT;
      case "market" -> OrdType.MARKET;
      default -> OrdType.LIMIT;
    };
  }

  private ExecType mapBitmartExecType(final String execType, final String orderState) {
    // exec_type: M=maker, T=taker
    // Use order state to determine the execution type
    
    if (orderState != null) {
      return switch (orderState.toLowerCase()) {
        case "new" -> ExecType.NEW;
        case "partially_filled" -> ExecType.TRADE;
        case "filled" -> ExecType.TRADE;
        case "canceled" -> ExecType.CANCELED;
        case "pending_cancel" -> ExecType.PENDING_CANCEL;
        case "failed" -> ExecType.REJECTED;
        default -> ExecType.NEW;
      };
    }
    
    return ExecType.NEW;
  }
    /**
     * Returns default quote currency based on symbol pattern
     * @param symbol Trading pair symbol
     * @return Default quote currency (USDC or USDT)
     */
    private String getDefaultQuotedCurrency(final String symbol) {
        LOGGER.debug("Determining default quote currency for symbol: " + symbol);
        if (symbol != null && symbol.contains("USDC")) {
            LOGGER.debug("Using USDC as quote currency");
            return "USDC";
        } else {
            LOGGER.debug("Using USDT as quote currency");
            return "USDT";
        }
    }

    private void handleBalanceUpdate(final String msg) {
        LOGGER.info("BALANCE UPDATE RAW: " + msg);
        
        final int dataStart = msg.indexOf("\"data\":[");
        if (dataStart == -1) {
            LOGGER.warn("No data array found in balance update message");
            return;
        }
        
        final int balanceDetailsStart = msg.indexOf("\"balance_details\":[", dataStart);
        if (balanceDetailsStart == -1) {
            LOGGER.warn("No balance_details array found in balance update message");
            return;
        }
        
        final int arrayStart = msg.indexOf("[", balanceDetailsStart + 18);
        final int arrayEnd = msg.indexOf("]", arrayStart);
        if (arrayStart == -1 || arrayEnd == -1) {
            LOGGER.warn("Invalid balance_details array format");
            return;
        }
        
        final String balanceDetailsContent = msg.substring(arrayStart, arrayEnd + 1);
        LOGGER.debug("Extracted balance details content length: " + balanceDetailsContent.length());
        
        // Parse each balance detail object
        int pos = 0;
        int balanceCount = 0;
        while (pos < balanceDetailsContent.length()) {
            final int objStart = balanceDetailsContent.indexOf("{", pos);
            if (objStart == -1) break;
            
            final int objEnd = balanceDetailsContent.indexOf("}", objStart);
            if (objEnd == -1) break;
            
            final String balanceObj = balanceDetailsContent.substring(objStart, objEnd + 1);
            
            final String asset = minExtract(balanceObj, "ccy");
            final String avBalStr = minExtract(balanceObj, "av_bal");
            final String fzBalStr = minExtract(balanceObj, "fz_bal");
            
            if (asset != null && avBalStr != null) {
                final double available = parseDoubleSafe(avBalStr);
                final double frozen = fzBalStr != null ? parseDoubleSafe(fzBalStr) : 0.0;
                
                LOGGER.info("SPOT USER DATA STREAM >>> Asset: " + asset + " Available=" + available + " Frozen=" + frozen);
                subscription.updateBalance(asset, available);
                balanceCount++;
            } else {
                LOGGER.warn("Missing asset or available balance in balance update: asset=" + asset + ", avBal=" + avBalStr);
            }
            
            pos = objEnd + 1;
        }
        LOGGER.info("Processed " + balanceCount + " balance updates");
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