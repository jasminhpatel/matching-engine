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
import com.solfini.matchengine.executionexchange.ExternalSymbol;
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
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class KrakenFastClientTest {

    final static String exchange = "KRAKEN";
    private static final CustomLogger LOGGER = CustomLogger.getLogger(KrakenFastClientTest.class);

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

        // Initialize USDT instrument
        final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000, 0,
                false, 1, Sector.NOT_DEFINED);
        usdt.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usdt);

        // Initialize USD instrument for Kraken Spot
        final Instrument usd = new Instrument(2, "USD", "USD", (short) 2, (short) 6, 1, 1000, 0,
                false, 1, Sector.NOT_DEFINED);
        usd.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usd);
    }

    public static void main(String[] args) throws Exception {
        init();

        // Load Kraken accounts (Futures and Spot)
        LiquiditySubscriptionCache.onLoad(getKrakenFutureAccount());
        //LiquiditySubscriptionCache.onLoad(getKrakenSpotAccount());

        // Start all subscriptions
        LiquiditySubscriptionCache.startAllSubscriptions();

        // Get subscription references
        ExchangeSubscription futureSegmentSubscription = LiquiditySubscriptionCache.get(exchange, true);
        //LiquiditySubscription spotSegmentSubscription = LiquiditySubscriptionCache.get(exchange, false);

        // Uncomment one of the test methods to run specific tests
        //testSpotTrade(spotSegmentSubscription);
        //testFutureTrade(futureSegmentSubscription);
        // testTransferBalance(spotSegmentSubscription, futureSegmentSubscription);
        // testOpenOrders(spotSegmentSubscription, futureSegmentSubscription);
        testGetTicker(futureSegmentSubscription);
        testInstrumentInfo(futureSegmentSubscription);
        // Print cache status periodically
        printCache(futureSegmentSubscription, null);
    }

    private static void testGetTicker(final ExchangeSubscription subscription) {
        final KrakenFastClient client = (KrakenFastClient) subscription.getClient();

        // instrumentType: 1 = Perps (futures), 2 = Spot
        final Ticker btcFutureTicker = client.getTicker("BTC", "USD", 1);
        if (btcFutureTicker != null) {
            LOGGER.info("BTC/USD Futures Ticker: symbol=" + btcFutureTicker.getSymbol()
                    + " instrumentType=" + btcFutureTicker.getInstrumentType()
                    + " last=" + btcFutureTicker.getLast()
                    + " bid=" + btcFutureTicker.getBid()
                    + " ask=" + btcFutureTicker.getAsk()
                    + " high=" + btcFutureTicker.getHigh()
                    + " low=" + btcFutureTicker.getLow()
                    + " volume=" + btcFutureTicker.getVolume()
                    + " change%=" + btcFutureTicker.getPercentageChange()
                    + " ts=" + btcFutureTicker.getTimestamp());
        } else {
            LOGGER.warn("BTC/USD Futures Ticker returned null");
        }

        final Ticker solFutureTicker = client.getTicker("SOL", "USD", 1);
        if (solFutureTicker != null) {
            LOGGER.info("SOL/USD Futures Ticker: symbol=" + solFutureTicker.getSymbol()
                    + " last=" + solFutureTicker.getLast()
                    + " bid=" + solFutureTicker.getBid()
                    + " ask=" + solFutureTicker.getAsk()
                    + " change%=" + solFutureTicker.getPercentageChange()
                    + " ts=" + solFutureTicker.getTimestamp());
        } else {
            LOGGER.warn("SOL/USD Futures Ticker returned null");
        }
    }

    private static void testInstrumentInfo(ExchangeSubscription subscription) {
        List<ExternalSymbol> data = subscription.getClient().getExchangeInstrumentsFull();
        for(ExternalSymbol externalSymbol : data){
            System.out.println("SymbolData ="+ externalSymbol.toString());
        }
    }
    /**
     * Test Kraken Spot Trading
     */
    public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription)
            throws Exception {
        LOGGER.info("========== STARTING KRAKEN SPOT TRADE TEST ==========");

        KrakenFastClient spotClient = (KrakenFastClient) spotSegmentSubscription.getClient();

        // Create a new spot order
        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("XRP/USDT");
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(20060L, (short) 4); // Price: 25.00
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(2, (short) 0); // Qty: 1000
        order.setUser(UserCache.getTestUser());

        LOGGER.info("Placing New Kraken Spot Order: " + order);

        // Submit the order
        try {
            final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, false, null,
                    null, 0, 0, 0);

            // Print result
            LOGGER.info("Execution report for clientOrderId: " + executionReportMessage.getClOrdId() +
                    " Status: " + executionReportMessage.getOrdStatus() +
                    " ExecType: " + executionReportMessage.getExecType());
        } catch (Exception e) {
            LOGGER.error("Spot trade test failed: " + e.getMessage(), e);
        }

        if (!(order.isRejected() || order.isExecuted())) {
            spotClient.cancelOrder(order, true);
        }
        LOGGER.info("========== KRAKEN SPOT TRADE TEST COMPLETED ==========");
    }

    /**
     * Test Kraken Futures Trading
     */
    private static void testFutureTrade(ExchangeSubscription futureSegmentSubscription)
            throws Exception {
        LOGGER.info("========== STARTING KRAKEN FUTURES TRADE TEST ==========");

        KrakenFastClient futureClient = (KrakenFastClient) futureSegmentSubscription.getClient();

        // Create a new futures order
        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("PF_XRPUSD");
        order.setSide(Side.SELL);
        order.setType(Constants.SELL_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(22100L, (short) 4); // Price: 42000.00
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(1L, (short) 0); // Qty: 0.001 BTC
        order.setUser(UserCache.getTestUser());

        LOGGER.info("Placing New Kraken Futures Order: " + order);

        // Submit the order
        try {
            final ExecutionReportMessage executionReportMessage = futureClient.sendOrder(order, true, null,
                    null, 0, 0, 0);

            // Print result
            LOGGER.info("Execution report for clientOrderId: " + executionReportMessage.getClOrdId() +
                    " Status: " + executionReportMessage.getOrdStatus() +
                    " ExecType: " + executionReportMessage.getExecType());
        } catch (Exception e) {
            LOGGER.error("Futures trade test failed: " + e.getMessage(), e);
        }
      /*  if (!(order.isRejected() || order.isExecuted())) {
            futureClient.cancelOrder(order, false);
        }*/
        LOGGER.info("========== KRAKEN FUTURES TRADE TEST COMPLETED ==========");
    }



    /**
     * Continuously Print Cache Status
     */
    static void printCache(ExchangeSubscription spotSegmentSubscription,
                           ExchangeSubscription futureSegmentSubscription) {
        while (true) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("\n========== KRAKEN CACHE STATUS ==========");
            System.out.println("Press ENTER to refresh cache...");
            scanner.nextLine();

            //printIt(futureSegmentSubscription);
            printIt(spotSegmentSubscription);
        }
    }

    /**
     * Print detailed cache information for a subscription
     */
    private static void printIt(ExchangeSubscription subscription) {
        ConcurrentHashMap<String, LastBalance> balances = subscription.getBalanceCache();
        ConcurrentHashMap<String, LastBalance> positions = subscription.getPositionCache();
        ConcurrentHashMap<String, Order> orders = subscription.getOrders();
        ConcurrentHashMap<String, ExecutionReportMessage> executionReports = subscription.getEXECUTION_REPORT_CACHE();

        String segment = subscription.isFuturesEnabled() ? "KRAKEN FUTURES SEGMENT" : "KRAKEN SPOT SEGMENT";

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

        // Print all non-zero positions
        LOGGER.info("--- POSITIONS ---");
        for (final LastBalance position : positions.values()) {
            if (position.getQuantity() != 0.0) {
                LOGGER.info("  " + position);
            }
        }

        // Print all orders
        LOGGER.info("--- ORDERS ---");
        for (final Order order : orders.values()) {
            LOGGER.info("  " + order);
        }

        // Print all execution reports
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

    /**
     * Create Kraken Futures Account Configuration
     */
    static ExchangeSubscription getKrakenFutureAccount() {
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(1);
        subscription.setExchange("KRAKEN");
        subscription.setApiUser("krakenFuturesUser");
        subscription.setApiKey(System.getenv("KRAKEN_FUTURE_API_KEY"));
        subscription.setApiSecret(System.getenv("KRAKEN_FUTURE_API_SECRET"));
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(true);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");

        LOGGER.info("Kraken Futures Account Configuration Loaded");
        return subscription;
    }

    /**
     * Create Kraken Spot Account Configuration
     */
    public static ExchangeSubscription getKrakenSpotAccount() {
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(2);
        subscription.setExchange("KRAKEN");
        subscription.setApiUser("krakenSpotUser");
        subscription.setApiKey(System.getenv("KRAKEN_API_KEY"));
        subscription.setApiSecret(System.getenv("KRAKEN_API_SECRET"));
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(false);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");

        LOGGER.info("Kraken Spot Account Configuration Loaded");
        return subscription;
    }
}
