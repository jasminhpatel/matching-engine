package com.solfini.matchengine.liquidity.direct.aiGenerated.prodtest;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.Sector;
import com.solfini.matchengine.LoggingThread;
import com.solfini.matchengine.liquidity.LastBalance;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.LiquiditySubscriptionCache;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.HtxFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.htx.HtxRestClient;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.UserCache;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;

public class HtxFastClientTest {

    final static String exchange = "HTX";
    private static final CustomLogger LOGGER = CustomLogger.getLogger(HtxFastClientTest.class);

    private static void init() throws IOException {
        long millis = System.currentTimeMillis();
        System.out.println("Test Start Time: " + millis);
        final LoggingThread loggingThread = Context.getLoggingThread();
        new Thread(loggingThread, "loggingThread").start();
        Properties properties = new Properties();
        PoolSize.minimize(properties);
        properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "100");
        properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "50");
        properties.setProperty("INITIAL_USER_CACHE_SIZE", "128");
        PropertyReader.initialize(null, properties);

        final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000, 0,
                false, 1, Sector.NOT_DEFINED);
        usdt.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usdt);

        final Instrument usd = new Instrument(2, "USD", "USD", (short) 2, (short) 6, 1, 1000, 0,
                false, 1, Sector.NOT_DEFINED);
        usd.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usd);
    }

    public static void main(String[] args) throws Exception {
        init();

        ExchangeSubscription spotAccount = getHtxSpotAccount();
//        LiquiditySubscription futureAccount = getHtxFutureAccount();
        LiquiditySubscriptionCache.onLoad(spotAccount);
//        LiquiditySubscriptionCache.onLoad(futureAccount);

        LiquiditySubscriptionCache.startAllSubscriptions();

        ExchangeSubscription spotSegmentSubscription = LiquiditySubscriptionCache.get(exchange, false);
        ExchangeSubscription futureSegmentSubscription = LiquiditySubscriptionCache.get(exchange, true);

//      testSpotTrade(spotSegmentSubscription);
//      testFutureTrade(futureSegmentSubscription);
//        testGetExchangeInstrumentsFull(futureSegmentSubscription);
//        testGetExchangeInstrumentsFull(spotSegmentSubscription);

        //  testGetTicker(futureSegmentSubscription);
        testGetTicker(spotSegmentSubscription);

//        if (futureSegmentSubscription != null) {
//            testOpenOrders(futureSegmentSubscription);
//        } else if (spotSegmentSubscription != null) {
//            testOpenOrders(spotSegmentSubscription);
//        }
//        testQuerySpotOrder(spotSegmentSubscription);
//        printCache(spotSegmentSubscription, futureSegmentSubscription);
    }

    public static void testGetExchangeInstrumentsFull(ExchangeSubscription subscription) {
        if (subscription == null) return;
        LOGGER.info("========== HTX getExchangeInstrumentsFull() TEST ==========");
        HtxFastClient client = (HtxFastClient) subscription.getClient();
        final List<ExternalSymbol> instruments = client.getExchangeInstrumentsFull();
        LOGGER.info("Total instruments: " + (instruments != null ? instruments.size() : 0));
        if (instruments != null && !instruments.isEmpty()) {
            int n = Math.min(10, instruments.size());
            for (int i = 0; i < n; i++) {
                ExternalSymbol s = instruments.get(i);
                LOGGER.info("  [" + i + "] " + s.getSymbol() + " base=" + s.getBase() + " quote=" + s.getQuote()
                        + " futures=" + s.isFutures() + " priceScale=" + s.getPriceScale() + " qtyScale=" + s.getQtyScale());
            }
        }
        LOGGER.info("========== HTX getExchangeInstrumentsFull() TEST COMPLETED ==========");
    }

    private static void testGetTicker(final ExchangeSubscription subscription) {
        if (subscription == null || subscription.getClient() == null) return;
        LOGGER.info("========== HTX getTicker() TEST ==========");
        final HtxFastClient client = (HtxFastClient) subscription.getClient();
        // instrumentType: 1 = Perps/Futures, 2 = Spot
        final int instrumentType = subscription.isFuturesEnabled() ? 1 : 2;
        final Ticker btcTicker = client.getTicker("BTC", "USDT", instrumentType);
        if (btcTicker != null) {
            LOGGER.info("BTC/USDT Ticker: symbol=" + btcTicker.getSymbol()
                    + " instrumentType=" + btcTicker.getInstrumentType()
                    + " last=" + btcTicker.getLast()
                    + " bid=" + btcTicker.getBid()
                    + " ask=" + btcTicker.getAsk()
                    + " high=" + btcTicker.getHigh()
                    + " low=" + btcTicker.getLow()
                    + " ts=" + btcTicker.getTimestamp());
        } else {
            LOGGER.warn("BTC/USDT Ticker returned null");
        }
        final Ticker solTicker = client.getTicker("SOL", "USDT", instrumentType);
        if (solTicker != null) {
            LOGGER.info("SOL/USDT Ticker: symbol=" + solTicker.getSymbol()
                    + " last=" + solTicker.getLast()
                    + " bid=" + solTicker.getBid()
                    + " ask=" + solTicker.getAsk()
                    + " ts=" + solTicker.getTimestamp());
        } else {
            LOGGER.warn("SOL/USDT Ticker returned null");
        }
        LOGGER.info("========== HTX getTicker() TEST COMPLETED ==========");
    }

    private static void testOpenOrders(ExchangeSubscription spotSegmentSubscription) {
        if (spotSegmentSubscription == null || spotSegmentSubscription.getClient() == null) {
            LOGGER.warn("HTX subscription or client is null, skipping open orders test");
            return;
        }
        LOGGER.info("========== HTX OPEN ORDERS TEST ==========");
        HtxFastClient spotClient = (HtxFastClient) spotSegmentSubscription.getClient();
        try {
            final String spotJson = spotClient.getAllOpenOrders();
            if (spotJson == null) {
                LOGGER.warn("HTX getAllOpenOrders returned null");
                return;
            }
            final String status = minExtract(spotJson, "status");
            if (!"ok".equalsIgnoreCase(status)) {
                final String errMsg = minExtract(spotJson, "err-msg");
                LOGGER.warn("HTX open orders not ok: status=" + status + " err=" + (errMsg != null ? errMsg : spotJson));
                return;
            }
            LOGGER.info("HTX Spot Open Orders response: status=ok, data length=" + (spotJson.length()) + " (use JsonHelper.extractJsonValue for 'data' array)");
        } catch (Exception e) {
            LOGGER.error("HTX getAllOpenOrders failed: " + e.getMessage(), e);
        }
    }

    public static void testQuerySpotOrder(ExchangeSubscription spotSegmentSubscription) throws Exception {
        LOGGER.info("========== STARTING HTX SPOT QUERY ORDER TEST ==========");

        HtxFastClient spotClient = (HtxFastClient) spotSegmentSubscription.getClient();

        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("ETHUSDT");
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(20000L, (short) 2);
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(25, (short) 3);
        order.setUser(UserCache.getTestUser());

        LOGGER.info("Placing HTX spot order for query test: " + order.getClOrdId());
        try {
            spotClient.sendOrder(order, false, null, null, 0, 0, 0);
        } catch (Exception e) {
            LOGGER.error("Send failed: " + e.getMessage(), e);
            LOGGER.info("========== HTX SPOT QUERY ORDER TEST COMPLETED (send failed) ==========");
            return;
        }

        if (order.getOcoClOrdId() == null || order.getOcoClOrdId().isEmpty()) {
            LOGGER.warn("No order-id returned (order may have been rejected); skip query");
            LOGGER.info("========== HTX SPOT QUERY ORDER TEST COMPLETED ==========");
            return;
        }

        LOGGER.info("Polling querySpotOrderStatus for order-id: " + order.getOcoClOrdId());
        final HtxRestClient restClient = new HtxRestClient(spotSegmentSubscription.getApiKey(),
                spotSegmentSubscription.getApiSecret(), spotSegmentSubscription);
        for (int i = 0; i < 10; i++) {
            restClient.querySpotOrderStatus(order, order.getClOrdId());
            spotSegmentSubscription.updateOrder(order.getClOrdId(), order);
            final ExecutionReportMessage report = spotSegmentSubscription.getExecutionReport(order.getClOrdId());
            if (report != null) {
                LOGGER.info("Query iteration " + i + " clientOrderId=" + report.getClOrdId() +
                        " ordStatus=" + report.getOrdStatus() + " cumQty=" + report.getCumQty());
            }
            if (order.isRejected() || order.isExecuted()) break;
            Thread.sleep(500);
        }

        LOGGER.info("Order after query loop: rejected=" + order.isRejected() + " executed=" + order.isExecuted());
        LOGGER.info("========== HTX SPOT QUERY ORDER TEST COMPLETED ==========");
    }

    public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription)
            throws Exception {
        LOGGER.info("========== STARTING HTX SPOT TRADE TEST (USDC/USDT mock) ==========");
        if (spotSegmentSubscription == null) {
            LOGGER.info("Spot subscription not loaded (futures-only?), skipping HTX spot trade test");
            return;
        }

        HtxFastClient spotClient = (HtxFastClient) spotSegmentSubscription.getClient();

        final String symbol = "USDCUSDT";
        final long limitPriceScaled = 99L;
        final short priceScale = 2;
        final long qtyScaled = 2L;
        final short qtyScale = 0;

        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol(symbol);
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(limitPriceScaled, priceScale);
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(qtyScaled, qtyScale);
        order.setUser(UserCache.getTestUser());

        LOGGER.info("Placing HTX spot mock order (USDC/USDT, non-executing): symbol=" + symbol
                + " BUY 2 USDC at 0.99 USDT (1.98 USDT notional, HTX min 1 USDT) clOrdId=" + order.getClOrdId());

        try {
            final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, false, null,
                    null, 0, 0, 0);

            if (executionReportMessage != null) {
                LOGGER.info("HTX spot order result: clOrdId=" + executionReportMessage.getClOrdId() +
                        " ordStatus=" + executionReportMessage.getOrdStatus() +
                        " execType=" + executionReportMessage.getExecType());
            } else {
                LOGGER.warn("HTX spot test: no execution report yet for clOrdId=" + order.getClOrdId() + " (WS may have disconnected before ack)");
            }
        } catch (Exception e) {
            LOGGER.error("Spot trade test failed: " + e.getMessage(), e);
        }

        if (!order.isRejected() && !order.isExecuted()) {
            try {
                Thread.sleep(1500);
                final boolean cancelled = spotClient.cancelOrder(order, true);
                if (cancelled) {
                    LOGGER.info("HTX spot order cancelled successfully: clOrdId=" + order.getClOrdId());
                } else {
                    LOGGER.warn("HTX spot order cancel failed or order already filled/cancelled: clOrdId=" + order.getClOrdId());
                }
            } catch (Exception e) {
                LOGGER.error("Cancel order failed: " + e.getMessage(), e);
            }
        } else {
            LOGGER.info("Order rejected or already executed, skip cancel. clOrdId=" + order.getClOrdId());
        }

        LOGGER.info("========== HTX SPOT TRADE TEST COMPLETED ==========");
    }

    public static void testFutureTrade(ExchangeSubscription futureSegmentSubscription) throws Exception {
        LOGGER.info("========== STARTING HTX FUTURES TRADE TEST ==========");
        if (futureSegmentSubscription == null) {
            LOGGER.info("Futures subscription not loaded, skipping HTX futures trade test");
            return;
        }
        if (!futureSegmentSubscription.isFuturesEnabled()) {
            LOGGER.info("Subscription is not futures-enabled, skipping HTX futures trade test");
            return;
        }

        HtxFastClient futureClient = (HtxFastClient) futureSegmentSubscription.getClient();

        final String symbol = "BTCUSDT";
        final long limitPriceScaled = 1000000L;
        final short priceScale = 2;
        final long qtyScaled = 1L;
        final short qtyScale = 3;

        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol(symbol);
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(limitPriceScaled, priceScale);
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(qtyScaled, qtyScale);
        order.setUser(UserCache.getTestUser());

        LOGGER.info("Placing HTX futures mock order: symbol=" + symbol + " BUY 1 contract at 10000 USDT clOrdId=" + order.getClOrdId());

        try {
            final ExecutionReportMessage executionReportMessage = futureClient.sendOrder(order, true, null,
                    null, 0, 0, 0);

            if (executionReportMessage != null) {
                LOGGER.info("HTX futures order result: clOrdId=" + executionReportMessage.getClOrdId() +
                        " ordStatus=" + executionReportMessage.getOrdStatus() +
                        " execType=" + executionReportMessage.getExecType());
            } else {
                LOGGER.warn("HTX futures test: no execution report for clOrdId=" + order.getClOrdId());
            }
        } catch (Exception e) {
            LOGGER.error("Futures trade test failed: " + e.getMessage(), e);
        }

        if (!order.isRejected() && !order.isExecuted()) {
            try {
                Thread.sleep(1500);
                final boolean cancelled = futureClient.cancelOrder(order, false);
                if (cancelled) {
                    LOGGER.info("HTX futures order cancelled successfully: clOrdId=" + order.getClOrdId() + " orderId=" + order.getOcoClOrdId());
                } else {
                    LOGGER.warn("HTX futures order cancel failed or order already filled/cancelled: clOrdId=" + order.getClOrdId());
                }
            } catch (Exception e) {
                LOGGER.error("Futures cancel order failed: " + e.getMessage(), e);
            }
        } else {
            LOGGER.info("Order rejected or already executed, skip cancel. clOrdId=" + order.getClOrdId());
        }

        LOGGER.info("========== HTX FUTURES TRADE TEST COMPLETED ==========");
    }

    static void printCache(ExchangeSubscription spotSegmentSubscription,
                           ExchangeSubscription futureSegmentSubscription) {
        while (true) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("\n========== HTX CACHE STATUS ==========");
            System.out.println("Press ENTER to refresh cache...");
            scanner.nextLine();

            if (spotSegmentSubscription != null) {
                printIt(spotSegmentSubscription);
            }
            if (futureSegmentSubscription != null) {
                printIt(futureSegmentSubscription);
            }
        }
    }

    private static void printIt(ExchangeSubscription subscription) {
        ConcurrentHashMap<String, LastBalance> balances = subscription.getBalanceCache();
        ConcurrentHashMap<String, LastBalance> positions = subscription.getPositionCache();
        ConcurrentHashMap<String, Order> orders = subscription.getOrders();
        ConcurrentHashMap<String, ExecutionReportMessage> executionReports = subscription.getEXECUTION_REPORT_CACHE();

        String segment = subscription.isFuturesEnabled() ? "HTX FUTURES SEGMENT" : "HTX SPOT SEGMENT";

        LOGGER.info("================================================");
        LOGGER.info(segment + " >>> Balances count=" + balances.size() + " | Positions count="
                + positions.size() + " | Orders count=" + orders.size() + " | Reports count=" + executionReports.size());
        LOGGER.info("USD/USDT Balance: " + subscription.getUsdtBalance());

        // Print all non-zero balances
        LOGGER.info("--- BALANCES ---");
        for (final LastBalance balance : balances.values()) {
            if (balance.getQuantity() > 0.0) {
                LOGGER.info("  " + balance);
            }
        }

        LOGGER.info("--- POSITIONS ---");
        for (final LastBalance position : positions.values()) {
            if (position.getQuantity() != 0.0) {
                LOGGER.info("  " + position);
            }
        }

        LOGGER.info("--- ORDERS ---");
        for (final Order order : orders.values()) {
            LOGGER.info("  " + order);
        }

        LOGGER.info("--- EXECUTION REPORTS ---");
        for (final ExecutionReportMessage report : executionReports.values()) {
            LOGGER.info("  ClientOrderId: " + report.getClOrdId() + " | Status: " + report.getOrdStatus() +
                    " | ExecType: " + report.getExecType() + " | LastQty: " + report.getLastQty());
            if (report.getError() != null && !report.getError().isEmpty()) {
                LOGGER.error("  Error: " + report.getError());
            }
        }
        LOGGER.info("================================================\n");
    }

    public static ExchangeSubscription getHtxSpotAccount() {
        final String apiKey = System.getenv("HTX_API_KEY");
        final String apiSecret = System.getenv("HTX_API_SECRET");
        if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            return null;
        }
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(1);
        subscription.setExchange(exchange);
        subscription.setApiUser("htxSpotUser");
        subscription.setApiKey(apiKey);
        subscription.setApiSecret(apiSecret);
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(false);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");

        LOGGER.info("HTX Spot Account Configuration Loaded");
        return subscription;
    }

    public static ExchangeSubscription getHtxFutureAccount() {
        final String apiKey = System.getenv("HTX_API_KEY");
        final String apiSecret = System.getenv("HTX_API_SECRET");
        if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            return null;
        }
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(2);
        subscription.setExchange(exchange);
        subscription.setApiUser("htxFutureUser");
        subscription.setApiKey(apiKey);
        subscription.setApiSecret(apiSecret);
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(true);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");

        LOGGER.info("HTX Futures Account Configuration Loaded");
        return subscription;
    }
}
