package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kraken;

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

import java.net.URI;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class KrakenFutureUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(KrakenFutureUserDataListener.class);
    private static final long PING_INTERVAL_SECONDS = 25;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 32768;
    private static final int SSL_PORT = 443;
    private static final int PROXY_PORT = 8888;

    private final String apiKey;
    private final String apiSecret;
    private final ExchangeSubscription subscription;
    private final String WS_URL;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();
    private volatile long challengeToken = 0L;
    private volatile String websocketToken = null;
    private volatile long tokenCreatedTime = 0L;
    private volatile String signedChallenge = null;
    private volatile String originalChallenge = null;

    public KrakenFutureUserDataListener(final String wsUrl, final String apiKey, final String apiSecret, final ExchangeSubscription subscription) {
        this.WS_URL = wsUrl;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
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
    public void onBinaryMessage(final byte[] bytes) {
        // Handle binary messages if any
    }

    public void connect() throws Exception {
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker =
                WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "KRAKEN-FUTURE-USER-DATA-LISTENER");

        final Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                // .localAddress(new InetSocketAddress(Context.getOutboundIp(), 0))
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

        LOGGER.info("Kraken FutureUserData WebSocket connected");
        authenticate();
    }

    public void disconnect() {
        setConnected(false);
        setAuthenticated(false);
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
        // Send challenge request to initiate WebSocket authentication
        // Reference: https://docs.kraken.com/api/docs/futures-api/websocket/challenge
        final String challengeJson = "{" +
                "\"event\":\"challenge\"," +
                "\"api_key\":\"" + apiKey + "\"" +
                "}";

        LOGGER.info("Sending Kraken FutureUserData challenge request with API key");
        channel.writeAndFlush(new TextWebSocketFrame(challengeJson));
    }

    private void subscribeToAccountFeeds() throws Exception {
        // Subscribe to balance updates feed with authentication
        final String balanceSubscription = "{" +
                "\"event\":\"subscribe\"," +
                "\"feed\":\"balances\"," +
                "\"api_key\":\"" + apiKey + "\"," +
                "\"original_challenge\":\"" + originalChallenge + "\"," +
                "\"signed_challenge\":\"" + signedChallenge + "\"" +
                "}";

        // Subscribe to fills feed with authentication
        final String fillsSubscription = "{" +
                "\"event\":\"subscribe\"," +
                "\"feed\":\"fills\"," +
                "\"api_key\":\"" + apiKey + "\"," +
                "\"original_challenge\":\"" + originalChallenge + "\"," +
                "\"signed_challenge\":\"" + signedChallenge + "\"" +
                "}";

        // Subscribe to open postions feed with authentication
        final String positionSubscription = "{" +
                "\"event\":\"subscribe\"," +
                "\"feed\":\"open_positions\"," +
                "\"api_key\":\"" + apiKey + "\"," +
                "\"original_challenge\":\"" + originalChallenge + "\"," +
                "\"signed_challenge\":\"" + signedChallenge + "\"" +
                "}";

        // Subscribe to open orders feed with authentication
        final String  openOrdersSubscription = "{" +
                "\"event\":\"subscribe\"," +
                "\"feed\":\"open_orders\"," +
                "\"api_key\":\"" + apiKey + "\"," +
                "\"original_challenge\":\"" + originalChallenge + "\"," +
                "\"signed_challenge\":\"" + signedChallenge + "\"" +
                "}";
        LOGGER.debug("Subscribing to Kraken Futures account feeds");
        LOGGER.debug(balanceSubscription);
        channel.writeAndFlush(new TextWebSocketFrame(balanceSubscription));
        channel.writeAndFlush(new TextWebSocketFrame(fillsSubscription));
        channel.writeAndFlush(new TextWebSocketFrame(positionSubscription));
        channel.writeAndFlush(new TextWebSocketFrame(openOrdersSubscription));
    }

    private void schedulePeriodicPing() {
        stopPing();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                final String pingJson = "{\"event\":\"ping\"}";
                channel.writeAndFlush(new TextWebSocketFrame(pingJson));
                LOGGER.debug("Sending Kraken FutureUserData ping");
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
        setConnected(false);
        setAuthenticated(false);
        stopPing();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        channel.eventLoop().schedule(() -> {
            try {
                LOGGER.info("Kraken FutureUserData WebSocket reconnecting...");
                connect();
            } catch (final Exception e) {
                LOGGER.error("Error during reconnect", e);
                reconnect();
            }
        }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
    }

    public void onMessage(final String json) {
        LOGGER.debug("Kraken FutureUserData Received message: " + json);

        final String event = minExtract(json, "event");
        final String feed = minExtract(json, "feed");

        if (event == null && feed == null) {
            return;
        }

        if ("challenge".equals(event)) {
            handleChallenge(json);
        } else if ("pong".equals(event)) {
            setLastPongReceived(System.currentTimeMillis());
        } else if ("balances".equals(feed)) {
            handleBalanceUpdate(json);
        } else if ("fills".equals(feed)) {
            handleFillUpdate(json);
        } else if ("open_positions".equals(feed)) {
            handlePositionUpdate(json);
        } else if ("liquidation".equals(event)) {
            handleLiquidationEvent(json);
        } else if ("error".equals(event)) {
            handleErrorMessage(json);
        }
    }

    private void handleChallenge(final String message) {
        try {
            final String event = minExtract(message, "event");
            if ("challenge".equalsIgnoreCase(event)) {
                // Extract the challenge message from the response
                final String challengeMessage = minExtract(message, "message");

                if (challengeMessage == null || challengeMessage.isEmpty()) {
                    LOGGER.error("Challenge message is empty or missing");
                    reconnect();
                    return;
                }

                // Store the original challenge
                this.originalChallenge = challengeMessage;

                // Sign the challenge with the API secret using the correct formula:
                // 1. SHA256 hash of the challenge
                // 2. HMAC-SHA512 with base64-decoded secret and the hash
                // 3. Base64 encode the result
                this.signedChallenge = generateSignedChallenge(challengeMessage);

                LOGGER.info("Received challenge, signing and sending authentication response");

                subscribeToAccountFeeds();
                LOGGER.debug("Sent auth subscription with signed challenge");

            }
        } catch (final Exception e) {
            LOGGER.error("Error handling challenge: " + e.getMessage(), e);
        }
    }

    /**
     * Generates signed challenge using the Kraken Futures formula:
     * 1. SHA256 hash of the challenge string
     * 2. HMAC-SHA512 with base64-decoded API secret and the hash
     * 3. Base64 encode the result
     */
    private String generateSignedChallenge(final String challenge) throws Exception {
        final java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
        final byte[] challengeHash = sha256.digest(challenge.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        final byte[] secretBytes = java.util.Base64.getDecoder().decode(apiSecret);

        final javax.crypto.Mac hmac = javax.crypto.Mac.getInstance("HmacSHA512");
        hmac.init(new javax.crypto.spec.SecretKeySpec(secretBytes, "HmacSHA512"));
        final byte[] hmacResult = hmac.doFinal(challengeHash);

        return java.util.Base64.getEncoder().encodeToString(hmacResult);
    }



    /**
     * Handles balance updates from Kraken Futures
     * Parses the flex_futures.currencies structure with nested currency objects
     * Example: {"feed":"balances","flex_futures":{"currencies":{"USDT":{"quantity":12.02,"available":11.98},...}}}
     */
    private void handleBalanceUpdate(final String message) {
        try {
            LOGGER.debug("Processing Futures balance update: " + message);

            // Extract the flex_futures object
            final String flexFuturesStr = extractJsonValue(message, "flex_futures");
            if (flexFuturesStr == null || !flexFuturesStr.startsWith("{")) {
                return;
            }

            // Extract the currencies object from flex_futures
            final String currenciesStr = extractJsonValue(flexFuturesStr, "currencies");
            if (currenciesStr == null || !currenciesStr.startsWith("{")) {
                return;
            }

            // Parse each currency by finding key-value pairs
            int pos = 0;
            while (pos < currenciesStr.length()) {
                // Find next currency key (quoted string followed by colon and opening brace)
                final int keyStart = currenciesStr.indexOf('"', pos);
                if (keyStart < 0) break;

                final int keyEnd = currenciesStr.indexOf('"', keyStart + 1);
                if (keyEnd < 0) break;

                final String currency = currenciesStr.substring(keyStart + 1, keyEnd);

                // Find the opening brace of the currency object
                final int objStart = currenciesStr.indexOf('{', keyEnd);
                if (objStart < 0) break;

                // Find matching closing brace for currency object
                int braceDepth = 0;
                int objEnd = -1;
                for (int i = objStart; i < currenciesStr.length(); i++) {
                    final char c = currenciesStr.charAt(i);
                    if (c == '{') {
                        braceDepth++;
                    } else if (c == '}') {
                        braceDepth--;
                        if (braceDepth == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }

                if (objEnd < 0) break;

                final String currencyObj = currenciesStr.substring(objStart, objEnd + 1);

                final String quantity = minExtract(currencyObj, "quantity");
                final String available = minExtract(currencyObj, "available");
                final String value = minExtract(currencyObj, "value");

                if (currency != null && !currency.isEmpty() && quantity != null) {
                    final double quantityDouble = parseDoubleSafe(quantity);

                    // Only log and update if quantity is non-zero
                    if (quantityDouble > 0) {
                        LOGGER.debug("Futures balance update - currency: " + currency +
                                ", quantity: " + quantity + ", available: " + available + ", value: " + value);
                        subscription.updateBalance(currency, quantityDouble);
                    }
                }

                pos = objEnd + 1;
            }

            LOGGER.info("FUTURES USER DATA STREAM >>> Balance update processed");

        } catch (final Exception e) {
            LOGGER.error("Error processing balance update: " + e.getMessage(), e);
        }
    }

    /**
     * Handles fill updates from Kraken Futures (trade execution)
     * Parses fills array with instrument, price, qty, fee details
     * Example: {"feed":"fills_snapshot","fills":[{"instrument":"FI_XBTUSD_200925","price":10937.5,"qty":5000.0,...}]}
     */
    private void handleFillUpdate(final String message) {
        try {
            LOGGER.debug("Processing fill update: " + message);

            // Extract the fills array
            final String fillsArrayStr = extractJsonValue(message, "fills");
            if (fillsArrayStr == null || !fillsArrayStr.startsWith("[")) {
                return;
            }

            // Parse each fill object in the array
            int p = 0;
            while (p < fillsArrayStr.length()) {
                final int objStart = fillsArrayStr.indexOf('{', p);
                if (objStart < 0) break;

                // Find matching closing brace for fill object
                int braceDepth = 0;
                int objEnd = -1;
                for (int i = objStart; i < fillsArrayStr.length(); i++) {
                    final char c = fillsArrayStr.charAt(i);
                    if (c == '{') {
                        braceDepth++;
                    } else if (c == '}') {
                        braceDepth--;
                        if (braceDepth == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }

                if (objEnd < 0) break;

                final String fillObj = fillsArrayStr.substring(objStart, objEnd + 1);

                final String instrument = minExtract(fillObj, "instrument");
                final String orderId = minExtract(fillObj, "order_id");
                final String cliOrdId = minExtract(fillObj, "cli_ord_id");
                final String fillId = minExtract(fillObj, "fill_id");
                final String price = minExtract(fillObj, "price");
                final String qty = minExtract(fillObj, "qty");
                final String feePaid = minExtract(fillObj, "fee_paid");
                final String feeCurrency = minExtract(fillObj, "fee_currency");
                final String fillType = minExtract(fillObj, "fill_type");
                final String buyStr = minExtract(fillObj, "buy");
                final String orderType = minExtract(fillObj, "order_type");
                final String takerOrderType = minExtract(fillObj, "taker_order_type");
                final String time = minExtract(fillObj, "time");
                final String remainingOrderQty = minExtract(fillObj, "remaining_order_qty");

                if (instrument == null || cliOrdId == null || qty == null || price == null) {
                    LOGGER.warn("Missing required fields in fill update");
                    p = objEnd + 1;
                    continue;
                }

                // Try to get order from cache using orderId
                Order order = subscription.getOrder(cliOrdId);
                if (order == null) {
                    // If not found by orderId, log warning and skip
                    LOGGER.warn("Order not found in cache for cliOrdId: " + cliOrdId);
                    p = objEnd + 1;
                    continue;
                }

                final double qtyDouble = parseDoubleSafe(qty);
                final double priceDouble = parseDoubleSafe(price);
                final double feePaidDouble = parseDoubleSafe(feePaid);
                final double remainingQtyDouble = parseDoubleSafe(remainingOrderQty);

                final long qtyLong = MbxMath.changeScale(qtyDouble, order.getQtyScale());
                final long priceLong = MbxMath.changeScale(priceDouble, order.getPriceScale());

                LOGGER.info("Fill update for orderId: " + orderId + ", instrument: " + instrument +
                        ", qty: " + qty + ", price: " + price + ", fillType: " + fillType +
                        ", feePaid: " + feePaid + ", feeCurrency: " + feeCurrency);

                ExecutionReportMessage executionMessage = subscription.getExecutionReport(cliOrdId);
                if (executionMessage == null) {
                    executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                            order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L, (short) 0, 0L, (short) 0,
                            0, 0, 0, 0, order.getSide(), 0);
                }

                executionMessage.setClOrdId(cliOrdId);
                executionMessage.setLastQty(qtyLong);
                executionMessage.setLastQtyScale(order.getQtyScale());
                executionMessage.setLastPx(priceLong);
                executionMessage.setLastPxScale(order.getPriceScale());
                executionMessage.setExecType(ExecType.TRADE);

                // Set order status based on remaining quantity
                if (remainingQtyDouble <= 0) {
                    executionMessage.setOrdStatus(OrdStatus.FILLED);
                    order.setExecuted(true);
                } else {
                    executionMessage.setOrdStatus(OrdStatus.PARTIALLY_FILLED);
                }

                // Handle fees
                if (feeCurrency != null && !feeCurrency.isEmpty()) {
                    final Instrument feeInstrument = InstrumentCache.getBySymbol(feeCurrency);
                    if (feeInstrument != null) {
                        final long feesLong = MbxMath.changeScale(Math.abs(feePaidDouble), feeInstrument.getQuantityScale());
                        executionMessage.setFeeAccumulatedQuantity(feesLong);
                        executionMessage.setFeePositionId(feeInstrument.getId());
                    }
                }

                // Log fill type details
                if ("maker".equalsIgnoreCase(fillType)) {
                    LOGGER.debug("Maker fill - orderId: " + orderId);
                } else if ("taker".equalsIgnoreCase(fillType)) {
                    LOGGER.debug("Taker fill - orderId: " + orderId);
                }

                subscription.updateExecutionReport(executionMessage);
                subscription.updateOrder(cliOrdId,order);
                p = objEnd + 1;
            }
        } catch (final Exception e) {
            LOGGER.error("Error processing fill update: " + e.getMessage(), e);
        }
    }



    /**
     * Handles position updates from Kraken Futures
     * Parses the positions array with instrument, balance, entry_price, mark_price, and pnl
     * Example: {"feed":"open_positions","positions":[{"instrument":"PF_XRPUSD","balance":1.0,"entry_price":1.93507,"mark_price":2.00256}]}
     */
    private void handlePositionUpdate(final String message) {
        try {
            LOGGER.debug("Processing Futures position update: " + message);

            // Extract the positions array
            final String positionsArrayStr = extractJsonValue(message, "positions");
            if (positionsArrayStr == null || !positionsArrayStr.startsWith("[")) {
                return;
            }

            // Parse each position object in the array
            int p = 0;
            while (p < positionsArrayStr.length()) {
                final int objStart = positionsArrayStr.indexOf('{', p);
                if (objStart < 0) break;

                // Find matching closing brace for position object
                int braceDepth = 0;
                int objEnd = -1;
                for (int i = objStart; i < positionsArrayStr.length(); i++) {
                    final char c = positionsArrayStr.charAt(i);
                    if (c == '{') {
                        braceDepth++;
                    } else if (c == '}') {
                        braceDepth--;
                        if (braceDepth == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }

                if (objEnd < 0) break;

                final String positionObj = positionsArrayStr.substring(objStart, objEnd + 1);

                final String instrument = minExtract(positionObj, "instrument");
                final String balance = minExtract(positionObj, "balance");
                final String entryPrice = minExtract(positionObj, "entry_price");
                final String markPrice = minExtract(positionObj, "mark_price");
                final String indexPrice = minExtract(positionObj, "index_price");
                final String pnl = minExtract(positionObj, "pnl");
                final String unrealizedFunding = minExtract(positionObj, "unrealized_funding");
                final String effectiveLeverage = minExtract(positionObj, "effective_leverage");
                final String maintenanceMargin = minExtract(positionObj, "maintenance_margin");
                final String returnOnEquity = minExtract(positionObj, "return_on_equity");

                if (instrument != null && balance != null) {
                    final double balanceDouble = parseDoubleSafe(balance);
                    final double entryPriceDouble = parseDoubleSafe(entryPrice);
                    final double markPriceDouble = parseDoubleSafe(markPrice);
                    final double indexPriceDouble = parseDoubleSafe(indexPrice);
                    final double pnlDouble = parseDoubleSafe(pnl);
                    final double effectiveLeverageDouble = parseDoubleSafe(effectiveLeverage);
                    final double returnOnEquityDouble = parseDoubleSafe(returnOnEquity);

                    LOGGER.debug("Futures position update - instrument: " + instrument +
                            ", balance: " + balance + ", entry_price: " + entryPrice +
                            ", mark_price: " + markPrice + ", pnl: " + pnl +
                            ", effective_leverage: " + effectiveLeverage +
                            ", return_on_equity: " + returnOnEquity);

                    // Update position with mark price (similar to ByBit implementation)
                    if (markPriceDouble > 0) {
                        subscription.updatePosition(instrument, balanceDouble, markPriceDouble);
                    } else {
                        subscription.updatePosition(instrument, balanceDouble);
                    }

                    // Log liquidation warning if leverage is high
                    if (effectiveLeverageDouble > 5.0) {
                        LOGGER.warn("HIGH LEVERAGE POSITION - Instrument: " + instrument +
                                ", Balance: " + balance + ", Effective Leverage: " + effectiveLeverage +
                                ", Mark Price: " + markPrice + ", Entry Price: " + entryPrice);
                    }

                    // Log PnL warning if significant loss
                    if (pnlDouble < 0) {
                        LOGGER.warn("UNREALIZED LOSS - Instrument: " + instrument +
                                ", Balance: " + balance + ", PnL: " + pnl +
                                ", Return on Equity: " + returnOnEquity + "%");
                    }

                    // Log position details
                    LOGGER.info("FUTURES USER DATA STREAM >>> Position update - Instrument: " + instrument +
                            ", Balance: " + balance + ", Entry Price: " + entryPrice +
                            ", Mark Price: " + markPrice + ", Index Price: " + indexPrice +
                            ", PnL: " + pnl + ", Effective Leverage: " + effectiveLeverage +
                            ", Return on Equity: " + returnOnEquity + "%");
                }

                p = objEnd + 1;
            }
        } catch (final Exception e) {
            LOGGER.error("Error processing position update: " + e.getMessage(), e);
        }
    }

    /**
     * Handles liquidation events from Kraken Futures
     * Logs critical events: ADL (Auto-Deleveraging) and liquidations
     */
    private void handleLiquidationEvent(final String message) {
        try {
            LOGGER.error("========== LIQUIDATION EVENT ==========");
            LOGGER.error("Raw message: " + message);

            final String symbol = minExtract(message, "product_id");
            final String quantity = minExtract(message, "quantity");
            final String price = minExtract(message, "price");
            final String side = minExtract(message, "side");
            final String liquidationType = minExtract(message, "liquidationType");
            final String reason = minExtract(message, "reason");

            LOGGER.error("LIQUIDATION - Symbol: " + symbol + ", Type: " + liquidationType);
            LOGGER.error("LIQUIDATION - Quantity: " + quantity + ", Price: " + price + ", Side: " + side);
            LOGGER.error("LIQUIDATION - Reason: " + reason);

            // Determine if this is ADL or regular liquidation
            if ("adl".equalsIgnoreCase(liquidationType) || (reason != null && reason.toLowerCase().contains("auto-deleveraging"))) {
                LOGGER.error("ADL EVENT (Auto-Deleveraging): Position for symbol " + symbol +
                        " was involuntarily closed due to ADL." +
                        " Quantity: " + quantity + ", Price: " + price);
            } else {
                LOGGER.error("MARGIN LIQUIDATION EVENT: Position for symbol " + symbol +
                        " was liquidated due to insufficient margin balance." +
                        " Quantity: " + quantity + ", Price: " + price);
            }

            LOGGER.error("========== END LIQUIDATION EVENT ==========");
        } catch (final Exception e) {
            LOGGER.error("Error processing liquidation event: " + e.getMessage());
        }
    }

    private void handleErrorMessage(final String message) {
        final String errorMsg = minExtract(message, "message");
        LOGGER.error("Kraken Futures UserData error: " + errorMsg + ", Full message: " + message);
    }
}