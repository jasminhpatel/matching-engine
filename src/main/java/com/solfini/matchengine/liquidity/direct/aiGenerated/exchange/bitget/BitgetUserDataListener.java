package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget;

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
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.parseDoubleSafe;

public final class BitgetUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(BitgetUserDataListener.class);
    private static final String WS_URL = "wss://wspap.bitget.com/v3/ws/private";


    private static final long PING_INTERVAL_SECONDS = 25;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;

    private final String apiKey;
    private final String secretKey;
    private final String passphrase;
    private final ExchangeSubscription subscription;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();

    public BitgetUserDataListener(final String apiKey, final String secretKey, final String passphrase, final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.passphrase = passphrase;
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
        // Not used for Bitget
    }

    public void connect() throws Exception {
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker =
                WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "BITGET-USER-DATA-LISTENER");

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

        LOGGER.info("Bitget SpotUserData WebSocket connected");
        setConnected(true);
        setAuthenticated(false);
        authenticate();
    }

    public void disconnect() {
        stopPing();
        setConnected(false);
        setAuthenticated(false);
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
        // Stop any existing ping task before authentication
        stopPing();

        final String timestamp = String.valueOf(Instant.now().toEpochMilli());
        final String preHashString = timestamp + "GET" + "/user/verify";
        final String hmacHex = HMAC.hmacSha256(preHashString, secretKey);
        final byte[] hmacBytes = hexStringToByteArray(hmacHex);
        final String sign = Base64.getEncoder().encodeToString(hmacBytes);

        final String loginMsg = "{\"op\":\"login\",\"args\":[{" +
                "\"apiKey\":\"" + apiKey + "\"," +
                "\"passphrase\":\"" + passphrase + "\"," +
                "\"timestamp\":\"" + timestamp + "\"," +
                "\"sign\":\"" + sign + "\"" +
                "}]}";

        LOGGER.info("Sending spot authentication: " + loginMsg);
        channel.writeAndFlush(new TextWebSocketFrame(loginMsg));
    }

    public void onMessage(final String msg) {
        LOGGER.debug("Bitget SpotUserData Received message: " + msg);

        // Check for login response
        if (msg.contains("\"event\":\"login\"")) {
            if (msg.contains("\"code\":0")) {
                LOGGER.info("Authentication successful!");
                setAuthenticated(true);
                subscribeToUserDataStreams();
                // Only schedule ping after successful authentication and subscription
                schedulePeriodicPing();
            } else {
                LOGGER.error("Authentication failed: " + msg);
                setAuthenticated(false);
            }
        }
        // Handle subscription confirmations
        else if (msg.contains("\"event\":\"subscribe\"")) {
            LOGGER.debug("Subscription confirmed: " + msg);
        }
        // Handle data messages
        else if (msg.contains("\"action\":\"update\"") || msg.contains("\"action\":\"snapshot\"")) {
            handleUserDataUpdate(msg);
        }
    }

    private void subscribeToUserDataStreams() {
        // Subscribe to account updates using UTA format
        final String accountSubMsg = "{\"op\":\"subscribe\",\"args\":[{\"instType\":\"UTA\",\"topic\":\"account\"}]}";
        channel.writeAndFlush(new TextWebSocketFrame(accountSubMsg));
        LOGGER.info("Subscribed to spot account updates");

        // Subscribe to fill updates using UTA format
        final String fillSubMsg = "{\"op\":\"subscribe\",\"args\":[{\"instType\":\"UTA\",\"topic\":\"fill\"}]}";
        channel.writeAndFlush(new TextWebSocketFrame(fillSubMsg));
        LOGGER.info("Subscribed to spot fill updates");

        // Subscribe to position updates using UTA format
        final String positionSubMsg = "{\"op\":\"subscribe\",\"args\":[{\"instType\":\"UTA\",\"topic\":\"position\"}]}";
        channel.writeAndFlush(new TextWebSocketFrame(positionSubMsg));
        LOGGER.info("Subscribed to position updates");

        // Subscribe to order updates using UTA format
        final String orderSubMsg = "{\"op\":\"subscribe\",\"args\":[{\"instType\":\"UTA\",\"topic\":\"order\"}]}";
        channel.writeAndFlush(new TextWebSocketFrame(orderSubMsg));
        LOGGER.info("Subscribed to orderSubMsg updates");
    }

    private void handleUserDataUpdate(final String msg) {
        // Find the arg object in the message
        final int argIdx = msg.indexOf("\"arg\":");
        if (argIdx < 0) return;

        // Find the start of the arg object
        final int argObjStart = msg.indexOf('{', argIdx);
        if (argObjStart < 0) return;

        // Find the end of the arg object
        final int argObjEnd = findMatchingBrace(msg, argObjStart);
        if (argObjEnd < 0) return;

        // Extract the arg object
        final String argObj = msg.substring(argObjStart, argObjEnd + 1);

        // Extract topic from the arg object
        final String topic = minExtract(argObj, "topic");
        if (topic != null) {
            if ("account".equals(topic)) {
                handleAccountUpdate(msg);
            } else if ("order".equals(topic)) {
                handleOrderUpdate(msg);
            } else if ("fill".equals(topic)) {
                handleFillUpdate(msg);
            } else if ("position".equals(topic)) {
                handlePositionUpdate(msg);
            }
        }
    }

    private void handleAccountUpdate(final String msg) {
        final int dataIdx = msg.indexOf("\"data\":");
        if (dataIdx < 0) return;

        // Find the opening bracket of the data array
        final int arrayStart = msg.indexOf('[', dataIdx);
        if (arrayStart < 0) return;

        // Find the closing bracket of the data array
        final int arrayEnd = findMatchingBracket(msg, arrayStart);
        if (arrayEnd < 0) return;

        // Extract the content inside the data array (without the brackets)
        final String dataContent = msg.substring(arrayStart + 1, arrayEnd);

        // Find the first object in the data array
        final int objStart = dataContent.indexOf('{');
        if (objStart < 0) return;

        // Find the matching closing brace for the entire account object
        final int objEnd = findMatchingBrace(dataContent, objStart);
        if (objEnd < 0) return;

        final String accountObj = dataContent.substring(objStart, objEnd + 1);

        // Look for "coin" field (handle whitespace around colon and bracket)
        final int coinKeyIdx = accountObj.indexOf("\"coin\"");
        if (coinKeyIdx >= 0) {
            // Find the colon after "coin"
            int colonIdx = accountObj.indexOf(':', coinKeyIdx);
            if (colonIdx >= 0) {
                // Find the opening bracket for the array (skip whitespace)
                int coinArrayStart = -1;
                for (int i = colonIdx + 1; i < accountObj.length(); i++) {
                    char c = accountObj.charAt(i);
                    if (c == '[') {
                        coinArrayStart = i;
                        break;
                    } else if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                        break; // Non-whitespace that's not '[' means no array
                    }
                }

                if (coinArrayStart >= 0) {
                    final int coinArrayEnd = findMatchingBracket(accountObj, coinArrayStart);
                    if (coinArrayEnd >= 0) {
                        final String coinArray = accountObj.substring(coinArrayStart + 1, coinArrayEnd);
                        parseCoinArray(coinArray);
                    }
                }
            }
        }
    }
    private void handleOrderUpdate(final String msg) {
        final int dataIdx = msg.indexOf("\"data\"");
        if (dataIdx < 0) return;

        // Find the colon after "data"
        final int colonIdx = msg.indexOf(':', dataIdx);
        if (colonIdx < 0) return;

        // Find the opening bracket for the data array (skip whitespace)
        int arrayStart = -1;
        for (int i = colonIdx + 1; i < msg.length(); i++) {
            char c = msg.charAt(i);
            if (c == '[') {
                arrayStart = i;
                break;
            } else if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                break; // Non-whitespace that's not '[' means no array
            }
        }

        if (arrayStart < 0) return;

        final int arrayEnd = findMatchingBracket(msg, arrayStart);
        if (arrayEnd < 0) return;

        final String dataArray = msg.substring(arrayStart + 1, arrayEnd);
        int p = 0;
        while (p >= 0 && p < dataArray.length()) {
            final int objStart = dataArray.indexOf('{', p);
            if (objStart < 0) break;
            final int objEnd = findMatchingBrace(dataArray, objStart);
            if (objEnd < 0) break;

            final String orderObj = dataArray.substring(objStart, objEnd + 1);
            parseOrderUpdate(orderObj);
            p = objEnd + 1;
        }
    }

    private void parseOrderUpdate(final String orderObj) {
        final String symbol = minExtract(orderObj, "symbol");

        // Try new format first (clientOid), then fallback to old format (clientOrderId)
        String clientOrderId = minExtract(orderObj, "clientOid");
        if (clientOrderId == null) {
            clientOrderId = minExtract(orderObj, "clientOrderId");
        }
        final String side = minExtract(orderObj, "side");
        final String orderType = minExtract(orderObj, "orderType");

        // Try new format first (orderStatus), then fallback to old format (state)
        String state = minExtract(orderObj, "orderStatus");
        if (state == null) {
            state = minExtract(orderObj, "state");
        }

        // Try new format first (qty), then fallback to old format (size)
        String size = minExtract(orderObj, "qty");
        if (size == null) {
            size = minExtract(orderObj, "size");
        }

        final String price = minExtract(orderObj, "price");

        // Try new format first (avgPrice), then fallback to old format (fillPrice)
        String fillPrice = minExtract(orderObj, "avgPrice");
        if (fillPrice == null) {
            fillPrice = minExtract(orderObj, "fillPrice");
        }

        final String fillQuantity = minExtract(orderObj, "fillQuantity");

        // Try new format first (cumExecQty), then fallback to old format (accFillSize)
        String accFillSize = minExtract(orderObj, "cumExecQty");
        if (accFillSize == null) {
            accFillSize = minExtract(orderObj, "accFillSize");
        }

        // Extract additional fields for exchange event detection
        final String reduceOnly = minExtract(orderObj, "reduceOnly");
        final String cancelReason = minExtract(orderObj, "cancelReason");
        final String execType = minExtract(orderObj, "execType");

        if (symbol == null || clientOrderId == null || state == null) return;

        // Check for exchange-triggered events (ADL, Liquidation, Risk Management)
        final boolean isExchangeTriggered = detectBitgetExchangeTriggeredEvent(clientOrderId, state, cancelReason, reduceOnly, execType, orderObj);
        if (isExchangeTriggered) {
            LOGGER.warn("EXCHANGE-TRIGGERED ORDER UPDATE DETECTED - OrderLinkId: " + clientOrderId +
                    ", Symbol: " + symbol + ", Status: " + state + ", CancelReason: " + cancelReason +
                    ", ReduceOnly: " + reduceOnly + ", ExecType: " + execType + ", Full Message: " + orderObj);
        }

        LOGGER.info("ORDER EXECUTION REPORT >> Symbol: " + symbol + ", ClientOrderId: " + clientOrderId +
                ", State: " + state + ", Side: " + side + ", FillQuantity: " + accFillSize);

        final Order order = subscription.getOrder(clientOrderId);
        if (order == null) return;

        final Side sideObj = "buy".equalsIgnoreCase(side) ? Side.BUY : Side.SELL;
        final OrdStatus orderStatus = mapOrderStatus(state);
        final OrdType ordType = mapOrderType(orderType);
        final ExecType execTypeEnum = mapExecType(state);

        final double fillPriceDouble = parseDoubleSafe(fillPrice);
        final double fillQuantityDouble = parseDoubleSafe(fillQuantity);
        final double accFillSizeDouble = parseDoubleSafe(accFillSize);
        final double orderSizeDouble = parseDoubleSafe(size);
        final double orderPriceDouble = parseDoubleSafe(price);

        final long fillPriceLong = MbxMath.changeScale(fillPriceDouble, order.getPriceScale());
        final long fillQuantityLong = MbxMath.changeScale(fillQuantityDouble, order.getQtyScale());
        final long accFillSizeLong = MbxMath.changeScale(accFillSizeDouble, order.getQtyScale());
        final long orderSizeLong = MbxMath.changeScale(orderSizeDouble, order.getQtyScale());
        final long orderPriceLong = MbxMath.changeScale(orderPriceDouble, order.getPriceScale());

        ExecutionReportMessage message = subscription.getExecutionReport(order.getClOrdId());
        if (message == null) {
            message = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrderId(), order.getUser(), 0, order.getSymbol(), fillPriceLong, order.getPriceScale(),
                    fillQuantityLong, order.getQtyScale(), 0, 0, 0, 0, sideObj, 0L);
        }
        message.setOrderQty(orderSizeLong);
        message.setOrderQtyScale(order.getQtyScale());
        message.setCumQty(accFillSizeLong);
        message.setCumQtyScale(order.getQtyScale());
        message.setPrice(orderPriceLong);
        message.setPriceScale(order.getPriceScale());
        message.setClOrdId(clientOrderId);
        message.setOrdStatus(orderStatus);
        message.setExecType(execTypeEnum);
        message.setOrdType(ordType);
        message.setTimeInForce(order.getTimeInForce());

        subscription.updateExecutionReport(message);

        subscription.updateOrder(clientOrderId, state);
    }

    private void handleFillUpdate(final String msg) {
        final int dataIdx = msg.indexOf("\"data\"");
        if (dataIdx < 0) return;

        // Find the colon after "data"
        final int colonIdx = msg.indexOf(':', dataIdx);
        if (colonIdx < 0) return;

        // Find the opening bracket for the data array (skip whitespace)
        int arrayStart = -1;
        for (int i = colonIdx + 1; i < msg.length(); i++) {
            char c = msg.charAt(i);
            if (c == '[') {
                arrayStart = i;
                break;
            } else if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                break; // Non-whitespace that's not '[' means no array
            }
        }

        if (arrayStart < 0) return;

        final int arrayEnd = findMatchingBracket(msg, arrayStart);
        if (arrayEnd < 0) return;

        final String dataArray = msg.substring(arrayStart + 1, arrayEnd);
        int p = 0;
        while (p >= 0 && p < dataArray.length()) {
            final int objStart = dataArray.indexOf('{', p);
            if (objStart < 0) break;
            final int objEnd = findMatchingBrace(dataArray, objStart);
            if (objEnd < 0) break;

            final String fillObj = dataArray.substring(objStart, objEnd + 1);
            parseFillUpdate(fillObj);
            p = objEnd + 1;
        }
    }

    private void parseFillUpdate(final String fillObj) {
        final String symbol = minExtract(fillObj, "symbol");
        final String clientOrderId = minExtract(fillObj, "clientOid");
        final String side = minExtract(fillObj, "side");
        final String orderType = minExtract(fillObj, "orderType");
        final String execPrice = minExtract(fillObj, "execPrice");
        final String execQty = minExtract(fillObj, "execQty");
        final String execValue = minExtract(fillObj, "execValue");
        final String execTime = minExtract(fillObj, "execTime");
        final String execId = minExtract(fillObj, "execId");
        final String tradeScope = minExtract(fillObj, "tradeScope");
        final String cumFeeDetailSection = extractJsonValue(fillObj, "feeDetail");
        
        // Extract additional fields for exchange event detection
        final String reduceOnly = minExtract(fillObj, "reduceOnly");
        final String isMaker = minExtract(fillObj, "isMaker");
        
        // Extract fee information from feeDetail array
        final String feeCoin = extractFeeDetailsFromArray(cumFeeDetailSection, "feeCoin");
        final String feeAmount = extractFeeDetailsFromArray(cumFeeDetailSection, "fee");

        if (symbol == null || clientOrderId == null || execPrice == null || execQty == null) return;

        // Check for exchange-triggered fill events (ADL, Liquidation)
        final boolean isExchangeTriggered = detectBitgetExchangeTriggeredFill(clientOrderId, tradeScope, reduceOnly, fillObj);
        if (isExchangeTriggered) {
            LOGGER.warn("EXCHANGE-TRIGGERED FILL DETECTED - Symbol: " + symbol +
                    ", ClientOrderId: " + clientOrderId + ", ExecQty: " + execQty +
                    ", ExecPrice: " + execPrice + ", TradeScope: " + tradeScope +
                    ", ReduceOnly: " + reduceOnly + ", Full Message: " + fillObj);
        }

        final double execPriceDouble = parseDoubleSafe(execPrice);
        final double execQtyDouble = parseDoubleSafe(execQty);
        final double execValueDouble = parseDoubleSafe(execValue);
        final double feeDouble = parseDoubleSafe(feeAmount);

        LOGGER.info("FILL UPDATE >> Symbol: " + symbol + ", ClientOrderId: " + clientOrderId +
                ", ExecPrice: " + execPriceDouble + ", ExecQty: " + execQtyDouble +
                ", Side: " + side + ", TradeScope: " + tradeScope);

        final Order order = subscription.getOrder(clientOrderId);
        if (order == null) return;

        final Side sideObj = "buy".equalsIgnoreCase(side) ? Side.BUY : Side.SELL;
        final OrdType ordType = mapOrderType(orderType);

        final long execPriceLong = MbxMath.changeScale(execPriceDouble, order.getPriceScale());
        final long execQtyLong = MbxMath.changeScale(execQtyDouble, order.getQtyScale());

        ExecutionReportMessage message = subscription.getExecutionReport(order.getClOrdId());
        if (message == null) {
            message = ExecutionReportMessage.createExternalExecutionReport(
                    order.getOrigOrderId(), order.getUser(), 0, symbol, execPriceLong, order.getPriceScale(),
                    execQtyLong, order.getQtyScale(), 0, 0, 0, 0, sideObj, 0L);
        }
        message.setOrderQty(order.getQty());
        message.setOrderQtyScale(order.getQtyScale());
        message.setPrice(execPriceLong);
        message.setPriceScale(order.getPriceScale());
        message.setClOrdId(clientOrderId);
        message.setExecType(ExecType.TRADE);
        message.setOrdType(ordType);
        
        // Set fee information if available
        if (feeDouble > 0 && feeCoin != null && !feeCoin.isBlank()) {
            // Convert commission with proper instrument scale
            final Instrument feesInstrument = InstrumentCache.getBySymbol(feeCoin.isBlank() ? getDefaultQuotedCurrency(order.getSymbol()) : feeCoin);
            if(feesInstrument!=null){
                final long feesLong = MbxMath.changeScale(feeDouble, feesInstrument.getQuantityScale());
                message.setFeeAccumulatedQuantity(feesLong);
                message.setFeePositionId(feesInstrument.getId());
            }
        }
        subscription.updateExecutionReport(message);
    }

    private void handlePositionUpdate(final String msg) {
        final int dataIdx = msg.indexOf("\"data\":[");
        if (dataIdx < 0) return;

        final int arrayStart = msg.indexOf('[', dataIdx);
        final int arrayEnd = msg.indexOf(']', arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) return;

        final String dataArray = msg.substring(arrayStart + 1, arrayEnd);
        int p = 0;
        while (p >= 0 && p < dataArray.length()) {
            final int objStart = dataArray.indexOf('{', p);
            if (objStart < 0) break;
            final int objEnd = findMatchingBrace(dataArray, objStart);
            if (objEnd < 0) break;

            final String positionObj = dataArray.substring(objStart, objEnd + 1);
            parsePositionUpdate(positionObj);
            p = objEnd + 1;
        }
    }

    private void parsePositionUpdate(final String positionObj) {
        final String symbol = minExtract(positionObj, "symbol");
        final String leverage = minExtract(positionObj, "leverage");
        final String size = minExtract(positionObj, "size");
        final String avgPrice = minExtract(positionObj, "avgPrice");
        final String markPrice = minExtract(positionObj, "markPrice");
        final String unrealisedPnl = minExtract(positionObj, "unrealisedPnl");
        final String marginCoin = minExtract(positionObj, "marginCoin");
        final String positionStatus = minExtract(positionObj, "positionStatus");
        final String posSide = minExtract(positionObj, "posSide");
        final String marginMode = minExtract(positionObj, "marginMode");
        final String available = minExtract(positionObj, "available");

        if (symbol == null) return;

        final double sizeDouble = parseDoubleSafe(size);
        final double avgPriceDouble = parseDoubleSafe(avgPrice);
        final double markPriceDouble = parseDoubleSafe(markPrice);
        final double unrealisedPnlDouble = parseDoubleSafe(unrealisedPnl);
        final double availableDouble = parseDoubleSafe(available);

        LOGGER.info("POSITION UPDATE >> Symbol: " + symbol + ", Size: " + sizeDouble +
                ", AvgPrice: " + avgPriceDouble + ", MarkPrice: " + markPriceDouble +
                ", UnrealisedPnl: " + unrealisedPnlDouble + ", Status: " + positionStatus +
                ", Side: " + posSide + ", MarginCoin: " + marginCoin);

        // Update position in local cache
        if (markPriceDouble == 0) {
            subscription.updatePosition(symbol, sizeDouble);
        } else {
            subscription.updatePosition(symbol, sizeDouble, markPriceDouble);
        }
    }

    private byte[] hexStringToByteArray(final String hex) {
        final int len = hex.length();
        final byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private void schedulePeriodicPing() {
        // Ensure we stop any existing ping task first
        stopPing();

        // Only schedule if we're connected and authenticated
        if (!channel.isActive() || !authenticated.get()) {
            return;
        }

        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive() && authenticated.get()) {
                // Send ping to keep connection alive
                // channel.writeAndFlush(new PingWebSocketFrame());
                channel.writeAndFlush(new TextWebSocketFrame("ping"));

                LOGGER.debug("Sending Bitget SpotUserData ping");
            } else {
                // Stop ping if connection is lost
                stopPing();
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
        LOGGER.info("Bitget SpotUserData WebSocket reconnecting...");
        // Stop ping immediately to prevent multiple tasks
        stopPing();
        setConnected(false);
        setAuthenticated(false);
        if (channel != null && channel.isOpen()) {
            channel.close();
        }

        // Use the existing event loop if available, otherwise use a new thread
        if (channel != null && channel.eventLoop() != null && !channel.eventLoop().isShuttingDown()) {
            channel.eventLoop().schedule(() -> {
                try {
                    connect();
                } catch (Exception e) {
                    LOGGER.error("Error during reconnect", e);
                    // Schedule another reconnect attempt
                    reconnect();
                }
            }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
        } else {
            // Fallback to new thread if event loop is not available
            new Thread(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY_SEC * 1000);
                    connect();
                } catch (Exception e) {
                    LOGGER.error("Error during reconnect", e);
                    reconnect();
                }
            }).start();
        }
    }

    private void parseCoinArray(final String coinArray) {
        int p = 0;
        while (p >= 0 && p < coinArray.length()) {
            final int objStart = coinArray.indexOf('{', p);
            if (objStart < 0) break;
            final int objEnd = findMatchingBrace(coinArray, objStart);
            if (objEnd < 0) break;

            final String coinObj = coinArray.substring(objStart, objEnd + 1);
            final String coin = minExtract(coinObj, "coin");
            final double available = parseDoubleSafe(minExtract(coinObj, "available"));

            if (coin != null) {
                LOGGER.info("SPOT USER DATA STREAM >>> Asset: " + coin + " Available=" + available);
                subscription.updateBalance(coin, available);
            }
            p = objEnd + 1;
        }
    }

    private int findMatchingBrace(final String str, final int startIdx) {
        int braceCount = 1;
        for (int i = startIdx + 1; i < str.length(); i++) {
            if (str.charAt(i) == '{') {
                braceCount++;
            } else if (str.charAt(i) == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findMatchingBracket(final String str, final int startIdx) {
        int bracketCount = 1;
        for (int i = startIdx + 1; i < str.length(); i++) {
            if (str.charAt(i) == '[') {
                bracketCount++;
            } else if (str.charAt(i) == ']') {
                bracketCount--;
                if (bracketCount == 0) {
                    return i;
                }
            }
        }
        return -1;
    }


    private OrdStatus mapOrderStatus(final String state) {
        if (state == null || state.isEmpty())
            return OrdStatus.NULL_VAL;
        return switch (state.toLowerCase()) {
            case "new" -> OrdStatus.NEW;
            case "partial_filled" -> OrdStatus.PARTIALLY_FILLED;
            case "filled" -> OrdStatus.FILLED;
            case "cancelled" -> OrdStatus.CANCELED;
            default -> OrdStatus.REJECTED;
        };
    }

    private OrdType mapOrderType(final String orderType) {
        return switch (orderType.toLowerCase()) {
            case "limit" -> OrdType.LIMIT;
            case "market" -> OrdType.MARKET;
            default -> OrdType.LIMIT;
        };
    }

    private ExecType mapExecType(final String state) {
        return switch (state.toLowerCase()) {
            case "new" -> ExecType.NEW;
            case "partial_filled" -> ExecType.TRADE;
            case "filled" -> ExecType.TRADE;
            case "cancelled" -> ExecType.CANCELED;
            default -> ExecType.REJECTED;
        };
    }

    /**
     * Properly extracts a JSON value by finding the complete object/value for a given key
     * Handles nested JSON structures with proper bracket/brace matching
     *
     * @param json The JSON string to parse
     * @param key  The key to extract value for
     * @return The extracted value as string, or null if not found
     */
    private String extractJsonValue(final String json, final String key) {
        if (json == null || key == null) {
            return null;
        }

        final String searchPattern = "\"" + key + "\"";
        final int keyStart = json.indexOf(searchPattern);
        if (keyStart < 0) {
            LOGGER.debug("Key '" + key + "' not found in JSON");
            return null;
        }

        // Find the colon after the key
        int colonPos = -1;
        for (int i = keyStart + searchPattern.length(); i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == ':') {
                colonPos = i;
                break;
            } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                LOGGER.debug("Invalid character before colon: " + c);
                return null;
            }
        }

        if (colonPos < 0) {
            LOGGER.debug("No colon found after key '" + key + "'");
            return null;
        }

        // Skip whitespace after colon to find value start
        int valueStart = -1;
        for (int i = colonPos + 1; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                valueStart = i;
                break;
            }
        }

        if (valueStart < 0) {
            LOGGER.debug("No value found after colon for key '" + key + "'");
            return null;
        }

        final char firstChar = json.charAt(valueStart);

        // Determine value type and extract accordingly
        if (firstChar == '{') {
            // Extract object
            return extractJsonObject(json, valueStart);
        } else if (firstChar == '[') {
            // Extract array
            return extractJsonArray(json, valueStart);
        } else if (firstChar == '"') {
            // Extract string
            return extractJsonString(json, valueStart);
        } else {
            // Extract primitive (number, boolean, null)
            return extractJsonPrimitive(json, valueStart);
        }
    }

    /**
     * Extracts a complete JSON object starting from the given position
     * Uses proper brace matching to handle nested objects
     */
    private String extractJsonObject(final String json, final int start) {
        int braceDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = start; i < json.length(); i++) {
            final char c = json.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') {
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extracts a complete JSON array starting from the given position
     * Uses proper bracket matching to handle nested arrays
     */
    private String extractJsonArray(final String json, final int start) {
        int bracketDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = start; i < json.length(); i++) {
            final char c = json.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '[') {
                    bracketDepth++;
                } else if (c == ']') {
                    bracketDepth--;
                    if (bracketDepth == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extracts a JSON string value (without quotes)
     */
    private String extractJsonString(final String json, final int start) {
        boolean escapeNext = false;

        for (int i = start + 1; i < json.length(); i++) {
            final char c = json.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                return json.substring(start + 1, i); // Return without quotes
            }
        }
        return null;
    }

    /**
     * Extracts a JSON primitive value (number, boolean, null)
     */
    private String extractJsonPrimitive(final String json, final int start) {
        for (int i = start; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']' || c == '\n' || c == '\r') {
                return json.substring(start, i).trim();
            }
        }
        return json.substring(start).trim();
    }



    /**
     * Returns default quote currency based on symbol pattern
     * @param symbol Trading pair symbol
     * @return Default quote currency (USDC or USDT)
     */
    private String getDefaultQuotedCurrency(final String symbol) {
        if (symbol != null && symbol.contains("USDC")) {
            return "USDC";
        } else {
            return "USDT";
        }
    }
    /**
     * Extracts a specific field value from the feeDetail array
     * Expected format: [{"feeCoin":"BTC","fee":"0.001"}]
     *
     * @param feeDetailJson The feeDetail array as JSON string
     * @param fieldName The field to extract (e.g., "feeCoin", "fee")
     * @return The field value, or null if not found
     */
    private String extractFeeDetailsFromArray(final String feeDetailJson, final String fieldName) {
        if (feeDetailJson == null || feeDetailJson.trim().isEmpty() || "[]".equals(feeDetailJson.trim())) {
            return null;
        }

        // Find first object in array
        final int objStart = feeDetailJson.indexOf('{');
        if (objStart < 0) {
            return null;
        }

        // Find matching closing brace
        int braceDepth = 0;
        int objEnd = -1;
        for (int i = objStart; i < feeDetailJson.length(); i++) {
            if (feeDetailJson.charAt(i) == '{') {
                braceDepth++;
            } else if (feeDetailJson.charAt(i) == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    objEnd = i + 1;
                    break;
                }
            }
        }

        if (objEnd <= objStart) {
            return null;
        }

        // Extract first object and get the field
        final String firstObjectJson = feeDetailJson.substring(objStart, objEnd);
        return minExtract(firstObjectJson, fieldName);
    }

    /**
     * Detects if an order update is triggered by exchange events like ADL, Liquidation, or Risk Management
     * Based on Bitget WebSocket documentation patterns:
     * - ADL: Auto-Deleveraging triggered by high risk exposure
     * - Liquidation: Position liquidation due to insufficient margin
     * - Risk Management: Emergency position closing due to risk thresholds
     *
     * @param clientOrderId The client order ID
     * @param state The order state (new, partial_filled, filled, cancelled, rejected)
     * @param cancelReason Cancel reason if order was cancelled
     * @param reduceOnly Whether this is a reduce-only order (common in ADL/liquidation)
     * @param execType Execution type (normal, post_only, fill_or_kill, immediate_or_cancel)
     * @param fullOrderData The complete order object for detailed analysis
     * @return true if this appears to be an exchange-triggered event
     */
    private boolean detectBitgetExchangeTriggeredEvent(final String clientOrderId, final String state,
                                                       final String cancelReason, final String reduceOnly,
                                                       final String execType, final String fullOrderData) {
        if (clientOrderId == null) return false;

        // ADL Detection Pattern 1: clientOid contains ADL indicator
        if (clientOrderId.toLowerCase().contains("adl") ||
                clientOrderId.toLowerCase().contains("auto-deleveraging") ||
                clientOrderId.toLowerCase().startsWith("adl_")) {
            LOGGER.warn("ADL (Auto-Deleveraging) detected for OrderLinkId: " + clientOrderId);
            return true;
        }

        // Liquidation Detection Pattern 1: clientOid contains liquidation indicator
        if (clientOrderId.toLowerCase().contains("liquidation") ||
                clientOrderId.toLowerCase().contains("liq") ||
                clientOrderId.toLowerCase().startsWith("liq_")) {
            LOGGER.warn("LIQUIDATION detected for OrderLinkId: " + clientOrderId);
            return true;
        }

        // Cancel Reason Analysis - Key indicator for exchange-triggered events
        if (cancelReason != null) {
            final String lowerCancelReason = cancelReason.toLowerCase();

            // ADL Detection via cancel reason
            if (lowerCancelReason.contains("adl") ||
                    lowerCancelReason.contains("auto-deleverage") ||
                    lowerCancelReason.contains("auto deleverage") ||
                    lowerCancelReason.contains("deleveraging")) {
                LOGGER.warn("ADL (Auto-Deleveraging) detected - CancelReason: " + cancelReason +
                        ", OrderLinkId: " + clientOrderId);
                return true;
            }

            // Liquidation Detection via cancel reason
            if (lowerCancelReason.contains("liquidation") ||
                    lowerCancelReason.contains("liquidated") ||
                    lowerCancelReason.contains("insufficient margin") ||
                    lowerCancelReason.contains("margin call")) {
                LOGGER.warn("LIQUIDATION detected - CancelReason: " + cancelReason +
                        ", OrderLinkId: " + clientOrderId);
                return true;
            }

            // Risk Management / Emergency Closure Detection
            if (lowerCancelReason.contains("risk") ||
                    lowerCancelReason.contains("emergency") ||
                    lowerCancelReason.contains("force") ||
                    lowerCancelReason.contains("system") ||
                    lowerCancelReason.contains("risk control") ||
                    lowerCancelReason.contains("position close")) {
                LOGGER.warn("RISK MANAGEMENT EVENT detected - CancelReason: " + cancelReason +
                        ", OrderLinkId: " + clientOrderId);
                return true;
            }
        }

        // Reduce-Only + Cancelled = likely ADL or liquidation
        if ("true".equalsIgnoreCase(reduceOnly) && "cancelled".equalsIgnoreCase(state)) {
            // Check if this is a market order (typical for ADL/liquidation)
            final String orderType = minExtract(fullOrderData, "orderType");
            if ("market".equalsIgnoreCase(orderType)) {
                LOGGER.warn("FORCED POSITION REDUCTION detected (reduceOnly=true, state=cancelled) - " +
                        "OrderLinkId: " + clientOrderId + ", OrderType: " + orderType);
                return true;
            }
        }

        // System-generated order pattern detection
        if (clientOrderId.matches(".*[0-9]{13,}.*") &&
                (clientOrderId.toLowerCase().contains("sys") ||
                        clientOrderId.toLowerCase().contains("auto") ||
                        clientOrderId.toLowerCase().contains("risk") ||
                        clientOrderId.toLowerCase().contains("emergency"))) {
            LOGGER.warn("SYSTEM-GENERATED ORDER detected - OrderLinkId: " + clientOrderId);
            return true;
        }

        // Check for multiple fills at unfavorable prices (ADL signature)
        // This would typically indicate forced liquidation fills
        if ("partial_filled".equalsIgnoreCase(state) && "true".equalsIgnoreCase(reduceOnly)) {
            final String cumExecQty = minExtract(fullOrderData, "cumExecQty");
            final String avgPrice = minExtract(fullOrderData, "avgPrice");
            if (cumExecQty != null && avgPrice != null) {
                final double cumQty = parseDoubleSafe(cumExecQty);
                // If significant quantity already filled and still reducing, likely ADL
                if (cumQty > 0) {
                    LOGGER.warn("POTENTIAL ADL/LIQUIDATION - Partial fill with reduce-only: " +
                            "OrderLinkId: " + clientOrderId + ", CumExecQty: " + cumExecQty +
                            ", AvgPrice: " + avgPrice);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Detects if a fill event is triggered by exchange events like ADL or Liquidation
     * Based on Bitget fill event patterns:
     * - tradeScope = "liquidation" or "adl" indicates system-triggered execution
     * - reduceOnly = true suggests position reduction
     *
     * @param clientOrderId The client order ID
     * @param tradeScope The trade scope (e.g., "normal", "liquidation", "adl")
     * @param reduceOnly Whether this fill is reduce-only
     * @param fullFillData The complete fill object for detailed analysis
     * @return true if this appears to be an exchange-triggered fill
     */
    private boolean detectBitgetExchangeTriggeredFill(final String clientOrderId, final String tradeScope,
                                                      final String reduceOnly, final String fullFillData) {
        if (clientOrderId == null) return false;

        // tradeScope is the primary indicator in Bitget for exchange-triggered events
        if (tradeScope != null) {
            final String lowerTradeScope = tradeScope.toLowerCase();

            // ADL Detection via tradeScope
            if (lowerTradeScope.contains("adl") ||
                    lowerTradeScope.contains("auto-deleveraging") ||
                    lowerTradeScope.contains("deleveraging")) {
                LOGGER.warn("ADL (Auto-Deleveraging) FILL detected - TradeScope: " + tradeScope +
                        ", OrderLinkId: " + clientOrderId + ", Full Fill: " + fullFillData);
                return true;
            }

            // Liquidation Detection via tradeScope
            if (lowerTradeScope.contains("liquidation") ||
                    lowerTradeScope.contains("liquidate") ||
                    lowerTradeScope.contains("liq")) {
                LOGGER.warn("LIQUIDATION FILL detected - TradeScope: " + tradeScope +
                        ", OrderLinkId: " + clientOrderId + ", ReduceOnly: " + reduceOnly +
                        ", Full Fill: " + fullFillData);
                return true;
            }
        }

        // Liquidation Detection Pattern: reduceOnly without explicit tradeScope
        // Combined with market order indicates forced liquidation
        if ("true".equalsIgnoreCase(reduceOnly)) {
            final String orderType = minExtract(fullFillData, "orderType");
            final String isMaker = minExtract(fullFillData, "isMaker");

            // Market orders that are reduce-only (especially as maker) suggest forced liquidation
            if ("market".equalsIgnoreCase(orderType)) {
                LOGGER.warn("FORCED LIQUIDATION FILL DETECTED - OrderLinkId: " + clientOrderId +
                        ", ReduceOnly: " + reduceOnly + ", OrderType: " + orderType +
                        ", IsMaker: " + isMaker);
                return true;
            }
        }

        // ADL Detection Pattern: clientOid contains ADL indicator
        if (clientOrderId.toLowerCase().contains("adl")) {
            LOGGER.warn("ADL FILL detected from clientOid - OrderLinkId: " + clientOrderId);
            return true;
        }

        // Liquidation Detection Pattern: clientOid contains liquidation indicator
        if (clientOrderId.toLowerCase().contains("liquidation") ||
                clientOrderId.toLowerCase().contains("liq")) {
            LOGGER.warn("LIQUIDATION FILL detected from clientOid - OrderLinkId: " + clientOrderId);
            return true;
        }

        return false;
    }

}