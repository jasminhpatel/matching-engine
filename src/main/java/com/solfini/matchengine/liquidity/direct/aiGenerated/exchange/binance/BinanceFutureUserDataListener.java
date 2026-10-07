package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.binance;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.liquidity.DelistedSymbolCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper;
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
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
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

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public final class BinanceFutureUserDataListener implements NettyWebSocketListenerInterface {
    private static final CustomLogger LOGGER = CustomLogger.getLogger(BinanceFutureUserDataListener.class);
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
    private final String WS_URL; // = "wss://fstream.binance.com/ws/"; // + listenKey
    private Channel channel;
    private ScheduledFuture<?> pingFuture;
    private volatile long lastPongReceived = System.currentTimeMillis();

    public BinanceFutureUserDataListener(final String wsUrl, final String apiKey, final byte[] ed25519PrivateKey,
                                         final ExchangeSubscription subscription) {
        this.WS_URL = wsUrl;
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

        final NettyWebSocketClientHandler handler = new NettyWebSocketClientHandler(handshaker, this, "BINANCE-FUTURE-USER-DATA-LISTENER");

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
        // public contract status / delist date stream on the same connection, see applyContractInfo()
        channel.writeAndFlush(new TextWebSocketFrame("{\"method\":\"SUBSCRIBE\",\"params\":[\"!contractInfo\"],\"id\":1}"));
        schedulePeriodicPing();
        LOGGER.info("Binance FutureUserData WebSocket connected");
        setConnected(true);
        setAuthenticated(true);
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

    private void schedulePeriodicPing() {
        stopPing();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                channel.writeAndFlush(new PingWebSocketFrame());
                LOGGER.debug("Sending Binance FutureUserData ping");
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
                LOGGER.info("Binance FutureUserData WebSocket reconnecting...");
                connect();
            } catch (Exception e) {
                LOGGER.error("Error during reconnect", e);
                reconnect();
            }
        }, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
    }

    public void onMessage(final String json) {
        LOGGER.debug("Binance FutureUserData Received message: " + json);

        // Two main event types: "e":"ACCOUNT_UPDATE" and "e":"ORDER_TRADE_UPDATE"
        final String et = minExtract(json, "e");
        if (et == null)
            return;
        if (et.indexOf("ACCOUNT_UPDATE") >= 0) {
            applyAccountUpdate(json);
        } else if (et.indexOf("ORDER_TRADE_UPDATE") >= 0) {
            parseOrderTradeUpdate(json);
        } else if ("contractInfo".equals(et)) {
            applyContractInfo(json);
        } else
            LOGGER.info("Unhandled event type: " + json);
    }

    /**
     * !contractInfo push, sent on contract listing / settlement / bracket changes:
     * {"e":"contractInfo","E":1669356423908,"s":"IOTAUSDT","ps":"IOTAUSDT","ct":"PERPETUAL","dt":4133404800000,
     * "ot":1569398400000,"cs":"TRADING","bks":[...]}
     */
    private void applyContractInfo(final String json) {
        final String symbol = minExtract(json, "s");
        final String contractType = minExtract(json, "ct");
        final String status = minExtract(json, "cs");
        final long deliveryDate = parseLongSafe(minExtract(json, "dt"));
        // quarterly contracts expire normally on dt, PENDING_TRADING is a new listing
        if (symbol == null || status == null || "PENDING_TRADING".equals(status)
                || (!"PERPETUAL".equals(contractType) && !"TRADIFI_PERPETUAL".equals(contractType))) {
            return;
        }
        final boolean delistScheduled = deliveryDate > 0 && deliveryDate != BinanceRestClient.PERPETUAL_NO_DELIVERY_DATE;
        if (DelistedSymbol.TRADING.equals(status) && !delistScheduled) {
            DelistedSymbolCache.remove("BINANCE", true, symbol);
            return;
        }
        final DelistedSymbol delisted = new DelistedSymbol();
        delisted.setExchange("BINANCE");
        delisted.setFutures(true);
        delisted.setSymbol(symbol);
        delisted.setStatus(status);
        delisted.setDelistTime(delistScheduled ? deliveryDate : 0);
        delisted.setDetectedAt(System.currentTimeMillis());
        DelistedSymbolCache.update(delisted);
    }


    private void parseOrderTradeUpdate(final String json) {
        final int oi = json.indexOf("\"o\":");
        if (oi < 0)
            return;
        final int start = json.indexOf('{', oi);
        final int end = json.indexOf('}', start);
        final String o = json.substring(start, end + 1);

        final String symbolStr = minExtract(o, "s");
        final String clientOrderIdStr = minExtract(o, "c");
        final String sideStr = minExtract(o, "S");
        final String orderTypeStr = minExtract(o, "o");
        final String timeInForceStr = minExtract(o, "f");
        final String quantityStr = minExtract(o, "q");
        final String priceStr = minExtract(o, "p");

        final String executionTypeStr = minExtract(o, "x");
        final String orderStatusStr = minExtract(o, "X");
        final String lastFilledQtyStr = minExtract(o, "l");
        final String cumulativeFilledQtyStr = minExtract(o, "z");
        final String lastFilledPriceStr = minExtract(o, "L");
        final String commissionStr = minExtract(o, "n");
        final String tradeIdStr = minExtract(o, "t");
        final String commissionAssetStr = minExtract(o, "N");
        /*
         * final String avgPriceStr = minExtract(o, "ap"); final String stopPriceStr = minExtract(o, "sp"); final String commissionAssetStr =
         * minExtract(o, "N"); final String transactionTimeStr = minExtract(o, "T"); final String bidNotionalStr = minExtract(o, "b"); final
         * String askNotionalStr = minExtract(o, "a"); final String isMakerStr = minExtract(o, "m"); final String reduceOnlyStr = minExtract(o,
         * "R"); final String workingTypeStr = minExtract(o, "wt"); final String origTypeStr = minExtract(o, "ot"); final String positionSideStr
         * = minExtract(o, "ps"); final String closePositionStr = minExtract(o, "cp"); final String realizedProfitStr = minExtract(o, "rp");
         * final String priceProtectStr = minExtract(o, "pP"); final String strategyIdStr = minExtract(o, "si"); final String strategyTypeStr =
         * minExtract(o, "ss"); final String selfTradePreventModeStr = minExtract(o, "V"); final String priceMatchStr = minExtract(o, "pm");
         * final String goodTillDateStr = minExtract(o, "gtd");
         */


        if (symbolStr == null || orderStatusStr == null)
            return;

        // Check for special client order IDs and log appropriate errors
        if (clientOrderIdStr != null) {
            if (clientOrderIdStr.startsWith("autoclose-")) {
                LOGGER.error("LIQUIDATION EVENT: Position liquidated due to insufficient margin balance. Symbol: " + symbolStr + ", ClientOrderId: "
                        + clientOrderIdStr + ", ExecutionType: " + executionTypeStr + ", Quantity: " + quantityStr + ", Price: " + priceStr);
            } else if ("adl_autoclose".equals(clientOrderIdStr)) {
                LOGGER.error("ADL EVENT: Position closed due to Auto-Deleveraging. Symbol: " + symbolStr + ", ClientOrderId: " + clientOrderIdStr
                        + ", ExecutionType: " + executionTypeStr + ", Quantity: " + quantityStr + ", Price: " + priceStr);
            } else if (clientOrderIdStr.startsWith("settlement_autoclose-")) {
                LOGGER.error(
                        "SETTLEMENT EVENT: Position closed due to settlement for delisting or delivery. Symbol: " + symbolStr + ", ClientOrderId: "
                                + clientOrderIdStr + ", ExecutionType: " + executionTypeStr + ", Quantity: " + quantityStr + ", Price: " + priceStr);
            }
        }

        final Side sideObj = "BUY".equalsIgnoreCase(sideStr) ? Side.BUY : Side.SELL;
        final OrdStatus orderStatus = OrdStatus.valueOf(orderStatusStr);
        final OrdType orderType = OrdType.valueOf(orderTypeStr);
        final ExecType execType = ExecType.valueOf(executionTypeStr);

        final TimeInForce timeInForce = getTimeInForce(timeInForceStr);

        final long tradeId = parseLongSafe(tradeIdStr);
        final double lastFilledPriceDouble = JsonHelper.parseDoubleSafe(lastFilledPriceStr);
        final double cumulativeFilledQtyDouble = JsonHelper.parseDoubleSafe(cumulativeFilledQtyStr);
        final double lastFilledQtyDouble = JsonHelper.parseDoubleSafe(lastFilledQtyStr);
        final double orderQtyDouble = JsonHelper.parseDoubleSafe(quantityStr);
        final double orderPriceDouble = JsonHelper.parseDoubleSafe(priceStr);
        final double feesDouble = JsonHelper.parseDoubleSafe(commissionStr);

        // get order from cache
        final Order order = subscription.getOrder(clientOrderIdStr);

        final long lastFilledPriceLong = MbxMath.changeScale(lastFilledPriceDouble, order.getPriceScale());
        final long lastFilledQtyLong = MbxMath.changeScale(lastFilledQtyDouble, order.getQtyScale());
        final long orderQtyLong = MbxMath.changeScale(orderQtyDouble, order.getQtyScale());
        final long cumQtyLong = MbxMath.changeScale(cumulativeFilledQtyDouble, order.getQtyScale());
        final long orderPriceLong = MbxMath.changeScale(orderPriceDouble, order.getPriceScale());

        LOGGER.info("FUTURE USER DATA WS>> Trade Update for clOrId:" + clientOrderIdStr + " and status: " + orderStatusStr
                + " and message JSON: " + json);


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
            executionReportMessage.setFeeAccumulatedQuantity(feesLong);
            executionReportMessage.setExecId(tradeId);
        }
        if(feesInstrument!=null) {
            executionReportMessage.setFeePositionId(feesInstrument.getId());
        }

        executionReportMessage.setOrderQty(lastFilledQtyLong);
        executionReportMessage.setOrderQtyScale(order.getQtyScale());
        executionReportMessage.setCumQty(cumQtyLong);
        executionReportMessage.setCumQtyScale(order.getQtyScale());
        executionReportMessage.setPrice(lastFilledPriceLong);
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

        subscription.updateOrder(clientOrderIdStr, orderStatusStr);
    }

    public void applyAccountUpdate(final String json) {
        // ACCOUNT_UPDATE payload (UM Futures):
        // {"e":"ACCOUNT_UPDATE","a":{"B":[{"a":"USDT","wb":"100.0","cw":"100.0"},...],
        // "P":[{"s":"BTCUSDT","pa":"0.01","ep":"70000.0","up":"-2.3","ps":"BOTH"},...]}}
        // Parse B array
        final int idxA = json.indexOf("\"a\":");
        if (idxA < 0)
            return;
        // Balances
        final int bStart = json.indexOf("\"B\":[", idxA);
        if (bStart >= 0) {
            final int bEnd = json.indexOf(']', bStart);
            final String bArr = json.substring(bStart + 4, bEnd);
            int p = 0;
            while (p >= 0 && p < bArr.length()) {
                final int objStart = bArr.indexOf('{', p);
                if (objStart < 0)
                    break;
                final int objEnd = bArr.indexOf('}', objStart);
                final String obj = bArr.substring(objStart, objEnd + 1);
                final String asset = minExtract(obj, "a");
                final double walletBalance = parseDoubleSafe(minExtract(obj, "wb"));
                // final double crossWallet = parseDoubleSafe(minExtract(obj, "cw"));
                if (asset != null) {
                    subscription.updateBalance(asset, walletBalance);
                }
                p = objEnd + 1;
            }
        }
        // Positions
        final int pStart = json.indexOf("\"P\":[", idxA);
        if (pStart >= 0) {
            final int pEnd = json.indexOf(']', pStart);
            final String pArr = json.substring(pStart + 4, pEnd);
            int q = 0;
            while (q >= 0 && q < pArr.length()) {
                final int objStart = pArr.indexOf('{', q);
                if (objStart < 0)
                    break;
                final int objEnd = pArr.indexOf('}', objStart);
                final String obj = pArr.substring(objStart, objEnd + 1);
                final String symbol = minExtract(obj, "s");
                final double positionAmount = parseDoubleSafe(minExtract(obj, "pa"));
                /*
                 * final double ep = parseDoubleSafe(minExtract(obj, "ep")); final double up = parseDoubleSafe(minExtract(obj, "up")); final double
                 * bep = parseDoubleSafe(minExtract(obj, "bep")); // break even price final String ps = minExtract(obj, "ps");
                 */
                if (symbol != null) {
                    subscription.updatePosition(symbol, positionAmount);
                }
                q = objEnd + 1;
            }
        }
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

}
