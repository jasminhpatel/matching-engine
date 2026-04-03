package com.solfini.matchengine.liquidity.direct.aiGenerated;

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
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.UserCache;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.extractJsonValue;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.minExtract;

public class KucoinFastClientTest {

    static final String EXCHANGE = "KUCOIN";
    private static final CustomLogger LOGGER = CustomLogger.getLogger(KucoinFastClientTest.class);

    private static KucoinFastClient kucoinFastClient;

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

        LiquiditySubscriptionCache.onLoad(getKuCoinFutureAccount());
       // LiquiditySubscriptionCache.onLoad(getKuCoinSpotAccount());

        LiquiditySubscriptionCache.startAllSubscriptions();

        ExchangeSubscription futureSegmentSubscription = LiquiditySubscriptionCache.get(EXCHANGE, true);
       // LiquiditySubscription spotSegmentSubscription = LiquiditySubscriptionCache.get(EXCHANGE, false);
        //kucoinFastClient = (KucoinFastClient) futureSegmentSubscription.getClient();
        Thread.sleep(10000);
     //   testSpotTrade(spotSegmentSubscription);
       // testGetAllOpenOrders(kucoinFastClient, true);
        //testGetTicker(futureSegmentSubscription);
        testFutureTrade(futureSegmentSubscription);
       /* testQueryOrderAndCancel(futureSegmentSubscription);*/

        printCache(futureSegmentSubscription, null);
    }


    private static void testGetTicker(final ExchangeSubscription subscription) {
        final KucoinFastClient client = (KucoinFastClient) subscription.getClient();

        // instrumentType: 1 = Perps (futures), 2 = Spot
        final Ticker btcFutureTicker = client.getTicker("BTC", "USDT", 1);
        if (btcFutureTicker != null) {
            LOGGER.info("BTC/USDT Futures Ticker: symbol=" + btcFutureTicker.getSymbol()
                    + " instrumentType=" + btcFutureTicker.getInstrumentType()
                    + " last=" + btcFutureTicker.getLast()
                    + " bid=" + btcFutureTicker.getBid()
                    + " ask=" + btcFutureTicker.getAsk()
                    + " bidSize=" + btcFutureTicker.getBidSize()
                    + " askSize=" + btcFutureTicker.getAskSize()
                    + " ts=" + btcFutureTicker.getTimestamp());
        } else {
            LOGGER.warn("BTC/USDT Futures Ticker returned null");
        }

        final Ticker solFutureTicker = client.getTicker("SOL", "USDT", 1);
        if (solFutureTicker != null) {
            LOGGER.info("SOL/USDT Futures Ticker: symbol=" + solFutureTicker.getSymbol()
                    + " last=" + solFutureTicker.getLast()
                    + " bid=" + solFutureTicker.getBid()
                    + " ask=" + solFutureTicker.getAsk()
                    + " ts=" + solFutureTicker.getTimestamp());
        } else {
            LOGGER.warn("SOL/USDT Futures Ticker returned null");
        }
    }

    public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription) throws Exception {
        LOGGER.info("========== STARTING KUCOIN SPOT TRADE TEST ==========");

        KucoinFastClient spotClient = (KucoinFastClient) spotSegmentSubscription.getClient();

        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("XRP-USDC");
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(122, (short) 2);   // 500.00 USDT
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        // KuCoin min order value: 0.1 USDT. 0.0002 BTC @ 500 = 0.1 USDT; use 0.0003 BTC for small test
        order.setQty(1, (short) 0);        // 0.000003 BTC -> ~0.15 USDT notional
        order.setUser(UserCache.getTestUser());

        LOGGER.info("Placing New KuCoin Spot Order: " + order);

        try {
            final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, false, null,
                    null, 0, 0, 0);

            if (executionReportMessage != null) {
                LOGGER.info("Execution report for clientOrderId: " + executionReportMessage.getClOrdId() +
                        " Status: " + executionReportMessage.getOrdStatus() +
                        " ExecType: " + executionReportMessage.getExecType());
                if (executionReportMessage.getError() != null && !executionReportMessage.getError().isEmpty()) {
                    LOGGER.info("Report error: " + executionReportMessage.getError());
                }
            } else {
                LOGGER.warn("No execution report returned (order may have been rejected before report was set)");
            }

            LOGGER.info("Order state after sendOrder: rejected=" + order.isRejected() + ", executed=" + order.isExecuted());
        } catch (Exception e) {
            LOGGER.error("Spot trade test failed: " + e.getMessage(), e);
        }

        if (!(order.isRejected() || order.isExecuted())) {
            final boolean cancelled = spotClient.cancelOrder(order, true);
            LOGGER.info("cancelOrder(spot) result: " + cancelled);
        }
        LOGGER.info("========== KUCOIN SPOT TRADE TEST COMPLETED ==========");
    }

    public static void testQueryOrderAndCancel(ExchangeSubscription spotSegmentSubscription) throws Exception {
        LOGGER.info("========== KUCOIN SEND + CANCEL TEST ==========");

        KucoinFastClient spotClient = (KucoinFastClient) spotSegmentSubscription.getClient();

        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("BTCUSDT");
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(50000L, (short) 2);
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(300, (short) 6);
        order.setUser(UserCache.getTestUser());

        LOGGER.info("Placing spot order: " + order.getClOrdId());
        try {
            spotClient.sendOrder(order, false, null, null, 0, 0, 0);
        } catch (Exception e) {
            LOGGER.error("Send failed: " + e.getMessage(), e);
            return;
        }

        LOGGER.info("Order state after sendOrder: rejected=" + order.isRejected() + " executed=" + order.isExecuted());

        if (!(order.isRejected() || order.isExecuted())) {
            final boolean cancelled = spotClient.cancelOrder(order, true);
            LOGGER.info("cancelOrder(spot) ok=" + cancelled);
        }
        LOGGER.info("========== KUCOIN SEND + CANCEL TEST COMPLETED ==========");
    }

    /**
     * Test Get Open Orders API (GET /api/v1/hf/orders/active for spot, or futures order list).
     * Call this after startAllSubscriptions() with the client from the subscription.
     * Logs response code, order count, and a sample of the JSON.
     */
    public static void testGetAllOpenOrders(KucoinFastClient client, boolean futures) {
        if (client == null) {
            LOGGER.error("testGetAllOpenOrders: client is null (subscription not started or wrong exchange?)");
            return;
        }
        LOGGER.info("========== KUCOIN GET ALL OPEN ORDERS TEST (" + (futures ? "FUTURES" : "SPOT") + ") ==========");
        try {
            String json = client.getAllOpenOrders();
            if (json == null || json.isEmpty()) {
                LOGGER.warn("getAllOpenOrders() returned null or empty");
                return;
            }
            String code = minExtract(json, "code");
            String dataStr = extractJsonValue(json, "data");
            int orderCount = 0;
            if (dataStr != null && dataStr.startsWith("[")) {
                int idx = 0;
                while ((idx = dataStr.indexOf('{', idx)) >= 0) {
                    orderCount++;
                    idx++;
                }
            }
            LOGGER.info("getAllOpenOrders response: code=" + code + ", orderCount=" + orderCount);
            if (dataStr != null && !dataStr.startsWith("[")) {
                LOGGER.warn("Expected data to be an array per Get Open Orders doc, but got: " + (dataStr.length() > 120 ? dataStr.substring(0, 120) + "..." : dataStr) + " (Did you get Get Symbols response by mistake?)");
            }
            if (orderCount > 0) {
                LOGGER.info("First 500 chars of data: " + (dataStr != null && dataStr.length() > 500 ? dataStr.substring(0, 500) + "..." : dataStr));
            }
            if (!"200000".equals(code)) {
                LOGGER.warn("API code was not 200000: " + code + " msg=" + minExtract(json, "msg"));
            }
            LOGGER.info("========== KUCOIN GET ALL OPEN ORDERS TEST COMPLETED ==========");
        } catch (Exception e) {
            LOGGER.error("testGetAllOpenOrders failed: " + e.getMessage(), e);
        }
    }

    private static void testFutureTrade(ExchangeSubscription futureSegmentSubscription) throws Exception {
        LOGGER.info("========== STARTING KUCOIN FUTURES TRADE TEST ==========");

        KucoinFastClient futureClient = (KucoinFastClient) futureSegmentSubscription.getClient();

        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("XRPUSDCM");
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(111, (short) 2);
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(1, (short) 0);
        order.setUser(UserCache.getTestUser());

        LOGGER.info("Placing New KuCoin Futures Order: " + order);

        try {
            final ExecutionReportMessage executionReportMessage = futureClient.sendOrder(order, true, null,
                    null, 0, 0, 0);

            LOGGER.info("Execution report for clientOrderId: " + executionReportMessage.getClOrdId() +
                    " Status: " + executionReportMessage.getOrdStatus() +
                    " ExecType: " + executionReportMessage.getExecType());
        } catch (Exception e) {
            LOGGER.error("Futures trade test failed: " + e.getMessage(), e);
        }
/*        if (!(order.isRejected() || order.isExecuted())) {
            futureClient.cancelOrder(order, false);
        }*/
        LOGGER.info("========== KUCOIN FUTURES TRADE TEST COMPLETED ==========");
    }

    private static void printCache(ExchangeSubscription futureSegmentSubscription,
                                   ExchangeSubscription spotSegmentSubscription) {
        while (true) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("\n========== KUCOIN CACHE STATUS ==========");
            System.out.println("Press ENTER to refresh cache...");
            try {
                scanner.nextLine();
            } catch (NoSuchElementException e) {
                LOGGER.info("No input available, exiting printCache loop");
                break;
            }

            if (futureSegmentSubscription != null) {
                printIt(futureSegmentSubscription);
            }
            if (spotSegmentSubscription != null) {
                printIt(spotSegmentSubscription);
            }
        }
    }

    private static void printIt(ExchangeSubscription subscription) {
        ConcurrentHashMap<String, LastBalance> balances = subscription.getBalanceCache();
        ConcurrentHashMap<String, LastBalance> positions = subscription.getPositionCache();
        ConcurrentHashMap<String, Order> orders = subscription.getOrders();
        ConcurrentHashMap<String, ExecutionReportMessage> executionReports = subscription.getEXECUTION_REPORT_CACHE();

        String segment = subscription.isFuturesEnabled() ? "KUCOIN FUTURES SEGMENT" : "KUCOIN SPOT SEGMENT";

        LOGGER.info("================================================");
        LOGGER.info(segment + " >>> Balances count=" + balances.size() + " | Positions count="
                + positions.size() + " | Orders count=" + orders.size() + " | Reports count=" + executionReports.size());
        LOGGER.info("USD/USDT Balance: " + subscription.getUsdtBalance());
        LOGGER.info("USDC Balance: " + subscription.getUsdcBalance());

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

    private static ExchangeSubscription getKuCoinFutureAccount() {
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(1);
        subscription.setExchange("KUCOIN");
        subscription.setApiUser("kucoinFuturesUser");
        subscription.setApiKey(System.getenv("KUCOIN_API_KEY"));
        subscription.setApiSecret(System.getenv("KUCOIN_API_SECRET"));
        subscription.setPassphrase(System.getenv("KUCOIN_API_PASSPHRASE"));
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(true);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");

        LOGGER.info("KuCoin Futures Account Configuration Loaded");
        return subscription;
    }

    public static ExchangeSubscription getKuCoinSpotAccount() {
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(2);
        subscription.setExchange("KUCOIN");
        subscription.setApiUser("kucoinSpotUser");
        subscription.setApiKey(System.getenv("KUCOIN_API_KEY"));
        subscription.setApiSecret(System.getenv("KUCOIN_API_SECRET"));
        subscription.setPassphrase(System.getenv("KUCOIN_API_PASSPHRASE"));
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(false);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");

        LOGGER.info("KuCoin Spot Account Configuration Loaded");
        return subscription;
    }
}
