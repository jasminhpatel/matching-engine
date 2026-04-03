package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.coinbase;

import com.solfini.common.Constants;
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
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;
import com.solfini.util.TimeUtil;
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

import java.security.KeyFactory;
import java.security.PrivateKey;

import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;
import java.security.SecureRandom;
import java.math.BigInteger;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.HashMap;
import java.util.Map;
import java.time.Instant;
import java.security.interfaces.ECPrivateKey;
import com.nimbusds.jose.JOSEObjectType;

public final class CoinbaseUserDataListener implements NettyWebSocketListenerInterface {

    private static final CustomLogger LOGGER = CustomLogger.getLogger(CoinbaseUserDataListener.class);
    private static final String WS_URL = "wss://advanced-trade-ws-user.coinbase.com";
    private static final long PING_INTERVAL_SECONDS = 30;
    private static final int RECONNECT_DELAY_SEC = 5;
    private static final int MAX_CONTENT_LENGTH = 65536;
    private static final int SSL_PORT = 443;

    // Immutable configuration - set once in constructor
    private final String apiKey;
    private final String apiSecret;
    private final PrivateKey privateKey;
    private final ExchangeSubscription subscription;
    private final EventLoopGroup group = new NioEventLoopGroup();

    // Connection state management - can change during runtime
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean subscribed = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();

    /**
     * Initializes Coinbase User Data WebSocket listener
     *
     * @param apiKey       Coinbase API key for authentication
     * @param apiSecret    Coinbase API secret (EC private key in PEM format)
     * @param subscription Liquidity subscription for balance and order updates
     */
    public CoinbaseUserDataListener(final String apiKey, final String apiSecret,
                                    final ExchangeSubscription subscription) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.subscription = subscription;
        this.privateKey = parsePrivateKey(apiSecret);
    }

    /**
     * Parses EC private key from PEM format string
     */
    private PrivateKey parsePrivateKey(final String privateKeyPEM) {
        try {
            // Ensure BouncyCastle provider is registered
            if (java.security.Security.getProvider("BC") == null) {
                java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            }

            // Properly format the PEM string - handle both literal \n and actual newlines
            String formattedKey = privateKeyPEM;
            if (formattedKey.contains("\\n")) {
                formattedKey = formattedKey.replace("\\n", "\n");
            }

            try (org.bouncycastle.openssl.PEMParser pemParser = new org.bouncycastle.openssl.PEMParser(
                    new java.io.StringReader(formattedKey))) {
                Object object = pemParser.readObject();

                if (object == null) {
                    LOGGER.error("PEMParser returned null - key format not recognized.");
                    return null;
                }

                org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter converter = new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter()
                        .setProvider("BC");

                if (object instanceof PrivateKey) {
                    return (PrivateKey) object;
                } else if (object instanceof org.bouncycastle.openssl.PEMKeyPair) {
                    return converter.getPrivateKey(((org.bouncycastle.openssl.PEMKeyPair) object).getPrivateKeyInfo());
                } else if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                    return converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) object);
                } else {
                    LOGGER.error("Unexpected PEM object type: " + object.getClass().getName());
                    return null;
                }
            } catch (Exception e) {
                LOGGER.error("Bouncy Castle parsing failed, trying fallback manual parsing");
                // Fallback to simple parsing if BC fails (legacy support)
                String privateKeyContent = formattedKey
                        .replace("-----BEGIN EC PRIVATE KEY-----", "")
                        .replace("-----END EC PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");

                byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("EC");
                return keyFactory.generatePrivate(keySpec);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse Coinbase private key: " + e.getMessage());
            return null;
        }
    }

    public boolean getConnected() {
        return connected.get();
    }

    public boolean getSubscribed() {
        return subscribed.get();
    }

    public void setSubscribed(final boolean subscribed) {
        this.subscribed.set(subscribed);
    }

    public void setLastPongReceived(final long lastPongReceived) {
        this.lastPongReceived = lastPongReceived;
    }

    public long getLastPongReceived() {
        return lastPongReceived;
    }

    @Override
    public void onBinaryMessage(byte[] bytes) {
        // Coinbase uses text messages, not binary
    }

    /**
     * Establishes WebSocket connection to Coinbase Advanced Trade API
     * Sets up SSL context, handshaker, and connection pipeline
     */
    public void connect() throws Exception {
        final URI uri = new URI(WS_URL);
        final String host = uri.getHost();
        final int port = uri.getPort() == -1 ? SSL_PORT : uri.getPort();

        final SslContext sslCtx = SslContextBuilder.forClient().build();

        final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this,
                "COINBASE-USER-DATA-LISTENER");

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

        LOGGER.info("Coinbase UserData WebSocket connected");
        subscribeToChannels();
    }

    /**
     * Gracefully disconnects from WebSocket and shuts down resources
     */
    public void disconnect() {
        stopPing();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        group.shutdownGracefully();
    }

    @Override
    public boolean isConnected() {
        return connected.get() && subscribed.get();
    }

    public void setConnected(final boolean connected) {
        this.connected.set(connected);
    }

    @Override
    public void setAuthenticated(final boolean authenticated) {
        this.authenticated.set(authenticated);
    }

    /**
     * Generates JWT token for Coinbase WebSocket authentication
     */
    private String generateJWT() {
        try {
            final long timestamp = System.currentTimeMillis() / 1000;
            // Generate random nonce (16 bytes -> 32 hex chars)
            final byte[] nonceBytes = new byte[16];
            new SecureRandom().nextBytes(nonceBytes);
            final StringBuilder nonceBuilder = new StringBuilder(new BigInteger(1, nonceBytes).toString(16));
            while (nonceBuilder.length() < 32) {
                nonceBuilder.insert(0, '0');
            }
            final String nonce = nonceBuilder.toString();

            // Prepare Header
            final Map<String, Object> headerParams = new HashMap<>();
            headerParams.put("nonce", nonce);

            final JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(apiKey)
                    .type(JOSEObjectType.JWT)
                    .customParams(headerParams)
                    .build();

            // Prepare Payload
            final JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(apiKey)
                    .issuer("cdp")
                    .notBeforeTime(java.util.Date.from(Instant.ofEpochSecond(timestamp)))
                    .expirationTime(java.util.Date.from(Instant.ofEpochSecond(timestamp + 120)))
                    .build();

            final SignedJWT signedJWT = new SignedJWT(header, claimsSet);

            // Sign
            final JWSSigner signer = new ECDSASigner((ECPrivateKey) privateKey);
            signedJWT.sign(signer);

            return signedJWT.serialize();

        } catch (final Exception e) {
            LOGGER.error("Failed to generate JWT for Coinbase WebSocket: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Subscribes to user data channels: user, futures_balance_summary
     * Coinbase requires JWT authentication in subscribe message
     */
    private void subscribeToChannels() {
        final String jwt = generateJWT();
        if (jwt == null) {
            LOGGER.error("Failed to generate JWT for Coinbase WebSocket subscription");
            return;
        }

        // Subscribe to user channel for order updates
        final String subscribeMessage = "{" +
                "\"type\":\"subscribe\"," +
             //   "\"product_ids\":[\"XRP-PERP-INTX\"]," +
                "\"channel\":\"user\"," +
                "\"jwt\":\"" + jwt + "\"" +
                "}";

        LOGGER.debug("Subscribing to Coinbase UserData channels");
        channel.writeAndFlush(new TextWebSocketFrame(subscribeMessage));

        // Subscribe to futures balance summary
        final String futuresBalanceSubscribe = "{" +
                "\"type\":\"subscribe\"," +
                "\"channel\":\"futures_balance_summary\"," +
                "\"jwt\":\"" + jwt + "\"" +
                "}";

        channel.writeAndFlush(new TextWebSocketFrame(futuresBalanceSubscribe));

        subscribed.set(true);
        schedulePeriodicPing();
    }

    /**
     * Starts periodic ping to maintain WebSocket connection
     */
    private void schedulePeriodicPing() {
        stopPing();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                channel.writeAndFlush(new PingWebSocketFrame());
                LOGGER.debug("Sending Coinbase UserData ping");
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Stops the periodic ping task
     */
    private void stopPing() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
    }

    /**
     * Reconnects to WebSocket after connection loss
     */
    public void reconnect() {
        stopPing();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        channel.eventLoop().schedule(() -> {
            try {
                LOGGER.info("Coinbase UserData WebSocket reconnecting...");
                connect();
            } catch (final Exception e) {
                LOGGER.error("Error during reconnect", e);
                reconnect();
            }
        }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
    }

    /**
     * Main message handler - routes messages to appropriate handlers
     *
     * @param message WebSocket message received from Coinbase
     */
    public void onMessage(final String message) {
        LOGGER.debug(Constants.LOG_FMT_4, "SubscriptionId: ", subscription.getId(), " Coinbase UserData received: ",
                message);

        try {
            final String channel = minExtract(message, "channel");

            if ("subscriptions".equals(channel)) {
                handleSubscriptionResponse(message);
            } else if ("user".equals(channel)) {
                handleUserChannelUpdate(message);
            } else if ("futures_balance_summary".equals(channel)) {
                handleFuturesBalanceUpdate(message);
            } else if ("heartbeats".equals(channel)) {
                handleHeartbeat(message);
            } else {
                LOGGER.debug("Unhandled Coinbase channel: " + channel);
            }
        } catch (final Exception e) {
            LOGGER.error("Error processing Coinbase UserData message", e);
        }
    }

    /**
     * Handles subscription confirmation responses
     */
    private void handleSubscriptionResponse(final String message) {
        final String subscriptionsData = minExtract(message, "subscriptions");
        if (subscriptionsData != null) {
            LOGGER.info("Coinbase WebSocket subscriptions confirmed: " + subscriptionsData);
        }
    }

    /**
     * Handles heartbeat messages from Coinbase WebSocket
     */
    private void handleHeartbeat(final String message) {
        final String timestamp = minExtract(message, "timestamp");
        LOGGER.debug("Coinbase heartbeat received at: " + timestamp);
        lastPongReceived = System.currentTimeMillis();
    }

    /**
     * Handles user channel updates (orders, matches, positions)
     */
    private void handleUserChannelUpdate(final String message) {
        try {
            final String eventsData = extractJsonValue(message, "events");
            if (eventsData == null || eventsData.trim().isEmpty() || "[]".equals(eventsData.trim())) {
                return;
            }

            // Parse events array
            final String[] events = parseJsonArray(eventsData);
            for (final String eventData : events) {
                final String type = minExtract(eventData, "type");

                if ("snapshot".equals(type)) {
                    handleOrderSnapshot(eventData);
                    handlePositionUpdate(eventData);
                } else if ("update".equals(type)) {
                    handleOrderUpdate(eventData);
                    handlePositionUpdate(eventData);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling Coinbase user channel update", e);
        }
    }

    /**
     * Handles order snapshot messages
     */
    private void handleOrderSnapshot(final String eventData) {
        try {
            final String ordersData = extractJsonValue(eventData, "orders");
            if (ordersData == null || ordersData.trim().isEmpty() || "[]".equals(ordersData.trim())) {
                return;
            }

            final String[] orders = parseJsonArray(ordersData);
            for (final String orderData : orders) {
                processOrderData(orderData);
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling Coinbase order snapshot", e);
        }
    }

    /**
     * Handles order update messages
     */
    private void handleOrderUpdate(final String eventData) {
        try {
            final String ordersData = extractJsonValue(eventData, "orders");
            if (ordersData == null || ordersData.trim().isEmpty() || "[]".equals(ordersData.trim())) {
                return;
            }

            final String[] orders = parseJsonArray(ordersData);
            for (final String orderData : orders) {
                processOrderData(orderData);
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling Coinbase order update", e);
        }
    }

    /**
     * Handles position updates from Coinbase user channel
     * Parses perpetual_futures_positions and expiring_futures_positions arrays
     * Updates local position cache with new size and mark price data
     * Only processes if futures trading is enabled on the subscription
     */
    private void handlePositionUpdate(final String eventData) {
        if (!subscription.isFuturesEnabled()) {
            return;
        }

        try {
            final String positionsData = extractJsonValue(eventData, "positions");
            if (positionsData == null || positionsData.trim().isEmpty() || "{}".equals(positionsData.trim())) {
                return;
            }

            // Parse perpetual futures positions
            final String perpetualPositionsData = extractJsonValue(positionsData, "perpetual_futures_positions");
            if (perpetualPositionsData != null && !perpetualPositionsData.trim().isEmpty()
                    && !"[]".equals(perpetualPositionsData.trim())) {
                processPositionsArray(perpetualPositionsData);
            }

        } catch (final Exception e) {
            LOGGER.error("Error handling Coinbase position update", e);
        }
    }

    /**
     * Processes an array of position objects from Coinbase
     */
    private void processPositionsArray(final String positionsArrayData) {
        try {
            final String[] positions = parseJsonArray(positionsArrayData);
            for (final String positionData : positions) {
                final String productId = minExtract(positionData, "product_id");
                final String positionSide = minExtract(positionData, "position_side");
                final String netSize = minExtract(positionData, "net_size");
                final String markPrice = minExtract(positionData, "mark_price");

                if (productId != null && netSize != null) {
                    final double parsedQty = parseDoubleSafe(netSize);
                    // Coinbase uses "Long"/"Short" for position_side - Short positions should be negative
                    final double qty = "Short".equalsIgnoreCase(positionSide) ? -Math.abs(parsedQty) : parsedQty;
                    final double markPriceDouble = parseDoubleSafe(markPrice);

                    // Update position in local cache
                    if (markPriceDouble == 0) {
                        subscription.updatePosition(productId, qty);
                    } else {
                        subscription.updatePosition(productId, qty, markPriceDouble);
                    }
                    LOGGER.debug("Updated Coinbase position - ProductId: " + productId +
                            ", Side: " + positionSide + ", Size: " + netSize + ", MarkPrice: " + markPrice);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Error processing Coinbase positions array", e);
        }
    }

    /**
     * Processes individual order data from Coinbase updates
     */
    private void processOrderData(final String orderData) {
        try {
            final String clientOrderId = minExtract(orderData, "client_order_id");
            final String orderId = minExtract(orderData, "order_id");
            final String productId = minExtract(orderData, "product_id");
            final String productType = minExtract(orderData, "product_type");
            final String status = minExtract(orderData, "status");
            final String side = minExtract(orderData, "order_side");
            final String orderType = minExtract(orderData, "order_type");
            final String avgPrice = minExtract(orderData, "avg_price");
            final String filledSize = minExtract(orderData, "cumulative_quantity");
            final String totalFees = minExtract(orderData, "total_fees");
            final String rejectReason = minExtract(orderData, "reject_Reason");
            final String createdTime = minExtract(orderData, "creation_time");

            if (clientOrderId == null || status == null) {
                return;
            }

            // Filter orders based on futures/spot mode
            // Process FUTURE orders only when futures is enabled
            // Process SPOT orders only when futures is disabled
            final boolean isFutureOrder = "FUTURE".equalsIgnoreCase(productType);
            final boolean isSpotOrder = "SPOT".equalsIgnoreCase(productType);
            if (subscription.isFuturesEnabled() && !isFutureOrder) {
                return;
            }
            if (!subscription.isFuturesEnabled() && !isSpotOrder) {
                return;
            }

            // Get order from cache
            final Order order = subscription.getOrder(clientOrderId);
            if (order == null) {
                LOGGER.warn(Constants.LOG_FMT_4, "Order not found in cache: ", clientOrderId, " SubscriptionId: ",
                        subscription.getId());
                return;
            }

            LOGGER.debug("Processing Coinbase order update - ClientOrderId: " + clientOrderId +
                    ", OrderId: " + orderId + ", Type: " + orderType +
                    ", Status: " + status + ", FilledSize: " + filledSize + ", AvgPrice: " + avgPrice +
                    ", CreatedTime: " + createdTime);

            final OrdStatus orderStatus = getCoinbaseOrderStatus(status);
            final double avgPriceDouble = parseDoubleSafe(avgPrice);
            final double filledSizeDouble = parseDoubleSafe(filledSize);
            final double totalFeesDouble = parseDoubleSafe(totalFees);

            final long avgPriceLong = MbxMath.changeScaleWithRounding(avgPriceDouble, order.getPriceScale());
            final long filledSizeLong = MbxMath.changeScaleWithRounding(filledSizeDouble, order.getQtyScale());

            // Extract quote currency from product_id (e.g., BTC-USD -> USD)
            String quoteCurrency = "USD";
            if (productId != null && productId.contains("-")) {
                quoteCurrency = productId.substring(productId.lastIndexOf("-") + 1);
            }

            final Instrument feesInstrument = InstrumentCache.getBySymbol(quoteCurrency);
            long totalFeesLong = 0;
            if (feesInstrument != null) {
                totalFeesLong = MbxMath.changeScale(totalFeesDouble, feesInstrument.getQuantityScale());
            }

            // Create or update execution report
            ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
            if (executionMessage == null) {
                final Side sideObj = "BUY".equalsIgnoreCase(side) ? Side.BUY : Side.SELL;
                executionMessage = ExecutionReportMessage.createExternalExecutionReport(
                        order.getOrderId(),
                        order.getUser(),
                        0,
                        order.getSymbol(),
                        avgPriceLong,
                        order.getPriceScale(),
                        filledSizeLong,
                        order.getQtyScale(),
                        0,
                        0,
                        0,
                        order.getTargetStrategy(),
                        sideObj,
                        totalFeesLong);
                executionMessage.setClOrdId(clientOrderId);
                executionMessage.setPrice(order.getPrice());
                executionMessage.setPriceScale(order.getPriceScale());
                executionMessage.setOrderQty(order.getQty());
                executionMessage.setOrderQtyScale(order.getQtyScale());
                executionMessage.setOrdType(order.getOrdType());
                executionMessage.setMatchTime(TimeUtil.getTime());
                if (feesInstrument != null) {
                    executionMessage.setFeePositionId(feesInstrument.getId());
                }
            } else {
                executionMessage.setAvgPx(avgPriceLong);
                executionMessage.setCumQty(filledSizeLong);
                executionMessage.setFeeAccumulatedQuantity(totalFeesLong);
                if (feesInstrument != null) {
                    executionMessage.setFeePositionId(feesInstrument.getId());
                }
                executionMessage.setMatchTime(TimeUtil.getTime());
            }

            executionMessage.setOrdStatus(orderStatus);

            // Handle filled orders
            if (OrdStatus.FILLED.equals(orderStatus)) {
                order.setExecuted(true);
                order.setAvailableAccumulatedQuantity(filledSizeLong);
                order.setPrice(avgPriceLong, order.getPriceScale());
                if (feesInstrument != null) {
                    order.setFeeAccumulatedQuantity(totalFeesLong);
                }
                LOGGER.info("Coinbase order FILLED - ClientOrderId: " + clientOrderId +
                        ", FilledSize: " + filledSize + ", AvgPrice: " + avgPrice);
            } else if (OrdStatus.CANCELED.equals(orderStatus) || OrdStatus.REJECTED.equals(orderStatus)) {
                order.setRejected(true);
                executionMessage.setExecType(ExecType.CANCELED);
                executionMessage.setError(rejectReason);
                LOGGER.debug("Coinbase order " + status + " - ClientOrderId: " + clientOrderId +
                        ", Reason: " + rejectReason);
            }

            subscription.updateExecutionReport(executionMessage);
            subscription.updateOrder(clientOrderId, order);

        } catch (final Exception e) {
            LOGGER.error("Error processing Coinbase order data", e);
        }
    }

    /**
     * Handles futures balance summary updates
     */
    private void handleFuturesBalanceUpdate(final String message) {
        try {
            LOGGER.info("Coinbase Futures Balance: " + message);

            final String eventsData = minExtract(message, "events");
            if (eventsData == null || eventsData.trim().isEmpty() || "[]".equals(eventsData.trim())) {
                return;
            }

            final String[] events = parseJsonArray(eventsData);
            for (final String eventData : events) {
                final String futuresBalanceData = extractJsonValue(eventData, "fcm_balance_summary");
                if (futuresBalanceData != null) {
                    final String cfmUsdBalance = minExtract(futuresBalanceData, "cfm_usd_balance");
                    final String futuresBuyingPower = minExtract(futuresBalanceData, "futures_buying_power");

                    final double cashBalance = parseDoubleSafe(cfmUsdBalance);
                    // final double buyingPower = parseDoubleSafe(futuresBuyingPower);

                    // Update USD balance for futures
                    subscription.updateBalance("USD", cashBalance);

                    LOGGER.debug("Updated Coinbase futures balance - Cash: " + cfmUsdBalance +
                            ", BuyingPower: " + futuresBuyingPower);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Error handling Coinbase futures balance update", e);
        }
    }

    /**
     * Parses JSON array into individual elements
     * Properly handles nested objects and arrays by tracking bracket depth
     */
    private String[] parseJsonArray(final String arrayData) {
        if (arrayData == null || arrayData.trim().isEmpty() || "[]".equals(arrayData.trim())) {
            return new String[0];
        }

        final String trimmed = arrayData.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return new String[0];
        }

        final java.util.List<String> elements = new java.util.ArrayList<>();
        int braceDepth = 0;
        int bracketDepth = 0;
        int elementStart = -1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 1; i < trimmed.length() - 1; i++) {
            final char c = trimmed.charAt(i);

            // Handle escape sequences inside strings
            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }

            // Handle string boundaries
            if (c == '"') {
                inString = !inString;
                continue;
            }

            // Skip content inside strings
            if (inString) {
                continue;
            }

            // Track bracket/brace depth
            if (c == '{') {
                if (braceDepth == 0 && bracketDepth == 0 && elementStart == -1) {
                    elementStart = i;
                }
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0 && bracketDepth == 0 && elementStart != -1) {
                    elements.add(trimmed.substring(elementStart, i + 1));
                    elementStart = -1;
                }
            } else if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                bracketDepth--;
            }
        }

        return elements.toArray(new String[0]);
    }

    /**
     * Converts Coinbase order status string to internal OrdStatus enum
     */
    private OrdStatus getCoinbaseOrderStatus(final String statusStr) {
        if (statusStr == null)
            return OrdStatus.NEW;

        return switch (statusStr) {
            case "OPEN", "PENDING" -> OrdStatus.NEW;
            case "FILLED" -> OrdStatus.FILLED;
            case "CANCELLED", "EXPIRED" -> OrdStatus.CANCELED;
            case "FAILED", "REJECTED" -> OrdStatus.REJECTED;
            default -> {
                LOGGER.warn("Unknown Coinbase order status: " + statusStr + ", defaulting to NEW");
                yield OrdStatus.NEW;
            }
        };
    }
}
