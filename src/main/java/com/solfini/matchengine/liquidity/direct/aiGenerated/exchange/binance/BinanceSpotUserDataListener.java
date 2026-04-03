package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.binance;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
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

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;
import static com.solfini.util.PrivateKeyBasedSigner.signWithEd25519;

public final class BinanceSpotUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(BinanceSpotUserDataListener.class);
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

    public BinanceSpotUserDataListener(final String apiKey, final byte[] ed25519PrivateKey, final ExchangeSubscription subscription) {
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

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "BINANCE-SPOT-USER-DATA-LISTENER");

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

        LOGGER.info("Binance SpotUserData WebSocket connected");
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
                LOGGER.debug("Sending Binance SpotUserData ping");
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
                LOGGER.info("Binance SpotUserData WebSocket reconnecting...");
                connect();
            } catch (Exception e) {
                LOGGER.error("Error during reconnect", e);
                reconnect();
            }
        }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
    }

    public void onMessage(final String msg) {
        LOGGER.debug("Binance SpotUserData Received message: " + msg);

        // Check for session.logon authentication result
        if (msg.contains("\"status\":200") && msg.contains("\"id\":\"1\"")) {
            LOGGER.info("Authentication successful!");
            // Subscribe to user data stream
            final String subJson = "{" + "\"id\":\"2\"," + "\"method\":\"userDataStream.subscribe\"," + "\"params\":{}" + "}";
            LOGGER.info("Subscribing to user data stream: " + subJson);
            channel.writeAndFlush(new TextWebSocketFrame(subJson));
        }
        // Check for authentication error
        else if (msg.contains("\"id\":\"1\"") && msg.contains("\"error\":")) {
            LOGGER.error("Authentication failed: " + msg);
        }
        // Check for userDataStream.subscribe response
        else if (msg.contains("\"result\":") && msg.contains("\"id\":\"2\"")) {
            LOGGER.info("User data stream subscription successful");
            // Schedule periodic ping to keep connection alive
            schedulePeriodicPing();
        }
        // Check for userDataStream.subscribe error
        else if (msg.contains("\"id\":\"2\"") && msg.contains("\"error\":")) {
            LOGGER.error("User data stream subscription failed: " + msg);
        }
        // Handle user data stream events
        else if (msg.contains("\"e\":")) {
            handleUserDataStreamEvent(msg);
        }
    }


    private void handleUserDataStreamEvent(final String msg) {

        // Extract event type
        final String eventType = extractJsonValue(msg, "e");
        if (eventType != null) {

            switch (eventType) {
                case "outboundAccountPosition":
                    handleOutboundAccountPosition(msg);
                    break;
                case "balanceUpdate":
                    // handleBalanceUpdate(msg);
                    break;
                case "executionReport":
                    parseOrderTradeUpdate(msg);
                    break;
                default:
                    LOGGER.info("Unhandled event type: " + eventType);
                    break;
            }
        }
    }

    private void parseOrderTradeUpdate(final String json) {
        // Parse JSON structure: {"subscriptionId":0,"event":{"e":"executionReport","E":1758203365143,"s":"BTCUSDT",...}}
        final int eventIdx = json.indexOf("\"event\":");
        if (eventIdx < 0)
            return; // No event found, cannot parse

        final int start = json.indexOf('{', eventIdx);
        int brace = 1;
        int idx = start + 1;
        while (idx < json.length() && brace > 0) {
            final char c = json.charAt(idx++);
            if (c == '{')
                brace++;
            if (c == '}')
                brace--;
        }
        final int end = idx - 1;
        final String eventObj = json.substring(start, end);

        // Extract key order fields from the event object
        final String symbolStr = minExtract(eventObj, "s");
        final String clientOrderIdStr = minExtract(eventObj, "c");
        final String sideStr = minExtract(eventObj, "S");
        final String orderTypeStr = minExtract(eventObj, "o");
        final String timeInForceStr = minExtract(eventObj, "f");
        final String quantityStr = minExtract(eventObj, "q");
        final String priceStr = minExtract(eventObj, "p");

        final String executionTypeStr = minExtract(eventObj, "x");
        final String orderStatusStr = minExtract(eventObj, "X");
        final String lastFilledQtyStr = minExtract(eventObj, "l");
        final String cumulativeFilledQtyStr = minExtract(eventObj, "z");
        final String lastFilledPriceStr = minExtract(eventObj, "L");
        final String commissionStr = minExtract(eventObj, "n");
        final String tradeIdStr = minExtract(eventObj, "t");
        final String commissionAssetStr = minExtract(eventObj, "N");

        /*
         * final String ignoreIdStr = minExtract(eventObj, "I"); final String stopPriceStr = minExtract(eventObj, "P"); final String
         * icebergQtyStr = minExtract(eventObj, "F"); final String orderListIdStr = minExtract(eventObj, "g"); final String orderRejectReasonStr
         * = minExtract(eventObj, "r"); final String origClientOrderIdStr = minExtract(eventObj, "C"); final String commissionAssetStr =
         * minExtract(eventObj, "N"); final String transactionTimeStr = minExtract(eventObj, "T"); final String isOnOrderBookStr =
         * minExtract(eventObj, "w"); final String isMakerStr = minExtract(eventObj, "m"); final String isReduceOnlyStr = minExtract(eventObj,
         * "M"); final String orderCreationTimeStr = minExtract(eventObj, "O"); final String cumulativeQuoteQtyStr = minExtract(eventObj, "Z");
         * final String lastQuoteQtyStr = minExtract(eventObj, "Y"); final String quoteOrderQtyStr = minExtract(eventObj, "Q"); final String
         * workingTimeStr = minExtract(eventObj, "W"); final String selfTradePreventionModeStr = minExtract(eventObj, "V");
         */

        // Validate required fields
        if (symbolStr == null || orderStatusStr == null || clientOrderIdStr == null)
            return;

        LOGGER.info("ORDER EXECUTION REPORT >> Symbol: " + symbolStr + ", ClientOrderId: " + clientOrderIdStr + ", Status: " + orderStatusStr
                + ", ExecutionType: " + executionTypeStr + ", Side: " + sideStr + ", FilledQty: " + cumulativeFilledQtyStr);


        final Side sideObj = "BUY".equalsIgnoreCase(sideStr) ? Side.BUY : Side.SELL;
        final OrdStatus orderStatus = OrdStatus.valueOf(orderStatusStr);
        final OrdType orderType = OrdType.valueOf(orderTypeStr);
        final ExecType execType = ExecType.valueOf(executionTypeStr);

        final TimeInForce timeInForce = getTimeInForce(timeInForceStr);

        final long tradeId = parseLongSafe(tradeIdStr);
        final double lastFilledPriceDouble = parseDoubleSafe(lastFilledPriceStr);
        final double cumulativeFilledQtyDouble = parseDoubleSafe(cumulativeFilledQtyStr);
        final double lastFilledQtyDouble = parseDoubleSafe(lastFilledQtyStr);
        final double orderQtyDouble = parseDoubleSafe(quantityStr);
        final double orderPriceDouble = parseDoubleSafe(priceStr);
        final double feesDouble = parseDoubleSafe(commissionStr);

        // get order from cache
        final Order order = subscription.getOrder(clientOrderIdStr);

        final long lastFilledPriceLong = MbxMath.changeScale(lastFilledPriceDouble, order.getPriceScale());
        final long lastFilledQtyLong = MbxMath.changeScale(lastFilledQtyDouble, order.getQtyScale());
        final long orderQtyLong = MbxMath.changeScale(orderQtyDouble, order.getQtyScale());
        final long cumQtyLong = MbxMath.changeScale(cumulativeFilledQtyDouble, order.getQtyScale());
        final long orderPriceLong = MbxMath.changeScale(orderPriceDouble, order.getPriceScale());

        // converting commission string to long with instrument qty scale
        long feesLong = 0;
        final Instrument feesInstrument = InstrumentCache.getBySymbol(commissionAssetStr);
        if (feesInstrument != null) {
            feesLong = MbxMath.changeScale(feesDouble, feesInstrument.getQuantityScale());
        }
        ExecutionReportMessage executionReportMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionReportMessage == null) {
            executionReportMessage = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, order.getSymbol(),
                    lastFilledPriceLong, order.getPriceScale(), lastFilledQtyLong, order.getQtyScale(), tradeId, 0, 0, 0, sideObj, feesLong);
        }else{
            executionReportMessage.setLastPx(lastFilledPriceLong);
            executionReportMessage.setLastPxScale(order.getPriceScale());
            executionReportMessage.setLastQty(lastFilledQtyLong);
            executionReportMessage.setLastQtyScale(order.getQtyScale());
            executionReportMessage.setExecId(tradeId);
            executionReportMessage.setFeeAccumulatedQuantity(feesLong);
        }
        
        if(feesInstrument!=null) {
            executionReportMessage.setFeePositionId(feesInstrument.getId());
        }
        executionReportMessage.setOrderQty(orderQtyLong);
        executionReportMessage.setOrderQtyScale(order.getQtyScale());
        executionReportMessage.setCumQty(cumQtyLong);
        executionReportMessage.setCumQtyScale(order.getQtyScale());
        executionReportMessage.setPrice(orderPriceLong);
        executionReportMessage.setPriceScale(order.getPriceScale());
        executionReportMessage.setClOrdId(clientOrderIdStr);
        executionReportMessage.setOrdStatus(orderStatus);


        if (execType.equals(ExecType.EXPIRED)) {
            executionReportMessage.setExecType(ExecType.REJECTED);
            executionReportMessage.setOrdStatus(OrdStatus.REJECTED);
        }
        executionReportMessage.setOrdType(orderType);
        executionReportMessage.setTimeInForce(timeInForce);
        subscription.updateExecutionReport(executionReportMessage);

        // setting up order status in the cache
        subscription.updateOrder(clientOrderIdStr, orderStatusStr);
    }

    private TimeInForce getTimeInForce(final String timeInForceStr) {
        if (timeInForceStr == null)
            return null;
        return switch (timeInForceStr) {
            case "GTC" -> TimeInForce.GOOD_TILL_CANCEL;
            case "IOC" -> TimeInForce.IMMEDIATE_OR_CANCEL;
            case "FOK" -> TimeInForce.FILL_OR_KILL;
            default -> null;
        };
    }

    private void handleOutboundAccountPosition(final String msg) {
        // Find the 'event' object
        final int idxEvent = msg.indexOf("\"event\":");
        if (idxEvent < 0) {
            LOGGER.error("No 'event' found in message!");
            return;
        }
        final int objStart = msg.indexOf('{', idxEvent);
        final int objEnd = msg.lastIndexOf('}'); // safer than first, as event could be last
        if (objStart < 0 || objEnd < 0 || objEnd <= objStart) {
            LOGGER.error("Could not extract event object boundaries!");
            return;
        }
        final String eventObj = msg.substring(objStart, objEnd + 1);

        // Parse balance array 'B'
        final int bArrStart = eventObj.indexOf("\"B\":[");
        if (bArrStart < 0) {
            LOGGER.warn("No Balance array found in event!");
            return;
        }
        final int arrayStart = eventObj.indexOf('[', bArrStart); // position after [
        final int bArrEnd = eventObj.indexOf(']', arrayStart);
        if (arrayStart < 0 || bArrEnd < 0 || bArrEnd <= arrayStart) {
            LOGGER.error("Could not extract balance array boundaries!");
            return;
        }
        final String bArr = eventObj.substring(arrayStart + 1, bArrEnd); // get content inside []

        // Split and parse every balance object in the array (works for 1 or multiple items)
        int p = 0;
        while (p >= 0 && p < bArr.length()) {
            final int balObjStart = bArr.indexOf('{', p);
            if (balObjStart < 0)
                break;
            final int balObjEnd = bArr.indexOf('}', balObjStart);
            if (balObjEnd < 0)
                break;
            final String balObj = bArr.substring(balObjStart, balObjEnd + 1);

            final String asset = minExtract(balObj, "a");
            final double free = parseDoubleSafe(minExtract(balObj, "f"));
            final double locked = parseDoubleSafe(minExtract(balObj, "l"));

            LOGGER.info("SPOT USER DATA STREAM >>> Asset: " + asset + " Free=" + free + " Locked=" + locked);
            if (asset != null) {
                subscription.updateBalance(asset, free);
            }
            p = balObjEnd + 1;
        }
    }

    private void handleBalanceUpdate(final String msg) {
        final String asset = extractJsonValue(msg, "a");
        final String clearTime = extractJsonValue(msg, "T");
        final double balanceDelta = parseDoubleSafe(minExtract(msg, "d"));
        LOGGER.info("SPOT USER DATA STREAM >>> Updating Balance: Asset: " + asset + " Balance=" + balanceDelta);
        subscription.updateBalance(asset, balanceDelta);
    }

    private String extractJsonValue(final String json, final String key) {
        final String searchKey = "\"" + key + "\":";
        final int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1)
            return null;

        int valueStart = keyIndex + searchKey.length();
        if (valueStart >= json.length())
            return null;

        // Skip whitespace
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length())
            return null;

        final char firstChar = json.charAt(valueStart);
        final int valueEnd;

        if (firstChar == '"') {
            // String value
            valueStart++; // Skip opening quote
            valueEnd = json.indexOf('"', valueStart);
            if (valueEnd == -1)
                return null;
            return json.substring(valueStart, valueEnd);
        } else {
            // Number or boolean value
            int tempEnd = valueStart;
            while (tempEnd < json.length() && !Character.isWhitespace(json.charAt(tempEnd)) && json.charAt(tempEnd) != ','
                    && json.charAt(tempEnd) != '}' && json.charAt(tempEnd) != ']') {
                tempEnd++;
            }
            return json.substring(valueStart, tempEnd);
        }
    }


}
