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
import com.solfini.matchengine.liquidity.direct.aiGenerated.BybitFastClient;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.solfini.common.Constants.ADL_MAKER_ONLY;
/*
Export them before running this script:
  export BYBIT_API_KEY='your_api_key'
  export BYBIT_API_SECRET='your_api_secret'
  export OUTBOUND_IP='your_outbound_ip'

USAGE : ./test-bybit-client.sh 's=XRPUSDT,p=20,ps=1,q=3,qs=0,side=Buy,t=LIMIT,tf=FOK,i=1' 'OUTBOUND_IP=192.168.1.1'

*/

public class BybitFastClientProdTest {

    final static String exchange = "BYBIT";
    private static final CustomLogger LOGGER = CustomLogger.getLogger(BybitFastClientProdTest.class);
    private static final List<Long> latencyMeasurements = new ArrayList<>();

    static {
        try {
            Properties properties = new Properties();
            properties.setProperty("OUTBOUND_IP", System.getenv("OUTBOUND_IP"));
            properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "100");
            properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "50");
            properties.setProperty("INITIAL_USER_CACHE_SIZE", "128");
            PoolSize.minimize(properties);
            PropertyReader.initialize(null, properties);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static void init() {

        final LoggingThread loggingThread = Context.getLoggingThread();
        new Thread(loggingThread, "loggingThread").start();


        final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000, 0,
                false, 1, Sector.NOT_DEFINED);
        usdt.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usdt);
        final User user = new User(100);
        UserCache.setTestUser(user);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Missing trade argument string");
        }

        Map<String, String> params = parseArgString(args[0]);

        init();
        LiquiditySubscriptionCache.onLoad(getSpotAccount());

        LiquiditySubscriptionCache.startAllSubscriptions();

        Thread.sleep(1000);
        ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange, false);

        Thread.sleep(2000);

        testSpotTrade(subscription, params);

        // printCache(subscription);
        printLatencyStats();


    }


    static void printCache(ExchangeSubscription futureSegmentSubscription) {
        while (true) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Do you want print the cache again?");
            System.out.println();
            System.out.println();
            System.out.println();
            System.out.println();
            System.out.println("Press ENTER to continue...");
            scanner.nextLine();  // Waits until user presses enter
            printIt(futureSegmentSubscription);
        }
    }

    public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription, Map<String, String> p) throws InterruptedException {
        BybitFastClient spotClient = (BybitFastClient) spotSegmentSubscription.getClient();

        String symbol = p.get("s");
        String price = p.get("p");
        String priceScale = p.get("ps");
        String qty = p.get("q");
        String qtyScale = p.get("qs");
        String side = p.get("side");
        String type = p.get("t");
        String timeInforce = p.get("tf");
        String iteration = p.get("i");

        int totalOrders = 1;
        try {
            totalOrders = Integer.parseInt(iteration);
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid value passed as parameter for iteration. i=" + iteration);
        }


        for (int i = 0; i < totalOrders; i++) {
            Order order = new Order();
            order.setSymbol(symbol);
            if (side.equalsIgnoreCase("Buy")) {
                order.setSide(Side.BUY);
            } else {
                order.setSide(Side.SELL);
            }

            if (side.equalsIgnoreCase("Buy") && type.equalsIgnoreCase("LIMIT")) {
                order.setType(Constants.BUY_LIMIT);
                order.setOrdType(OrdType.LIMIT);
            } else if (side.equalsIgnoreCase("Buy") && type.equalsIgnoreCase("MARKET")) {
                order.setType(Constants.BUY_MARKET);
                order.setOrdType(OrdType.MARKET);
            } else if (side.equalsIgnoreCase("Sell") && type.equalsIgnoreCase("LIMIT")) {
                order.setType(Constants.SELL_LIMIT);
                order.setOrdType(OrdType.LIMIT);
            } else if (side.equalsIgnoreCase("Sell") && type.equalsIgnoreCase("MARKET")) {
                order.setType(Constants.SELL_MARKET);
                order.setOrdType(OrdType.MARKET);
            }

            if (timeInforce.equalsIgnoreCase("GTC")) {
                order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);  //TODO try with fill or kill

            } else {
                order.setTimeInForce(TimeInForce.FILL_OR_KILL);  //TODO try with fill or kill
            }


            order.setPrice(Long.parseLong(price), (short) Long.parseLong(priceScale));
            order.setQty(Long.parseLong(qty), (short) Long.parseLong(qtyScale));

            order.setClOrdId(String.valueOf(TimeUtil.getTime()));
            order.setUser(UserCache.getTestUser());
            order.setTargetStrategy(ADL_MAKER_ONLY);  //TODO set every where we use to set execution report

            LOGGER.info("Iteration no:" + (i + 1) + " Placing New Order: " + order);
            order.setClOrdId(String.valueOf(TimeUtil.getTime()));
            // Submit the order with latency measurement
            long startTime = System.nanoTime();
            final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, false, null,
                    null, 0, 0, 0);
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            latencyMeasurements.add(latencyNanos);

            // Print result
            LOGGER.info(
                    "Execution report for clientOrderId    " + executionReportMessage.getClOrdId() + "   :"
                            + executionReportMessage.toJSON());

            if (!order.isRejected()) {
                boolean isCancelled = spotClient.cancelOrder(order,true);
                if (!isCancelled) {
                    LOGGER.info("Last order is not cancelled. Order:" + order);
                    break;
                }
            }
        }
    }

    private static void printIt(ExchangeSubscription spotSegmentSubscription) {
        ConcurrentHashMap<String, LastBalance> balances = spotSegmentSubscription.getBalanceCache();
        ConcurrentHashMap<String, LastBalance> positions = spotSegmentSubscription.getPositionCache();
        ConcurrentHashMap<String, Order> orders = spotSegmentSubscription.getOrders();
        ConcurrentHashMap<String, ExecutionReportMessage> executionReports = spotSegmentSubscription.getEXECUTION_REPORT_CACHE();

        LOGGER.info("################################################");
        LOGGER.info("Unified Account >>> Balances count=" + balances.size() + "  Positions count="
                + positions.size());
        LOGGER.info("USDT balance:>> " + spotSegmentSubscription.getUsdtBalance());

        for (final LastBalance balance : balances.values()) {
            if (balance.getQuantity() <= 0.0) {
                continue;
            }
            LOGGER.info("Balance: " + balance);
        }

        for (final LastBalance position : positions.values()) {
            if (position.getQuantity() <= 0.0) {
                continue;
            }
            LOGGER.info("Position: " + position);
        }

        for (final Order order : orders.values()) {
            LOGGER.info("Order: " + order);
        }

        for (final ExecutionReportMessage report : executionReports.values()) {
            LOGGER.info("ExecutionReportMessage: " + report.toJSON());
            //LOGGER.info("Error Reason: "+report.getError());
        }
        LOGGER.info("################################################");


    }


    static ExchangeSubscription getSpotAccount() {
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(1);
        subscription.setExchange(exchange);
        subscription.setApiUser("dummyUser");
        subscription.setApiKey(System.getenv("BYBIT_API_KEY"));
        subscription.setApiSecret(System.getenv("BYBIT_API_SECRET"));
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(false);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");
        return subscription;
    }


    /**
     * Calculates and logs latency statistics from all measurements collected.
     * Provides average, minimum, maximum, and other useful metrics in nanoseconds and milliseconds.
     */
    public static void printLatencyStats() {
        if (latencyMeasurements.isEmpty()) {
            LOGGER.info("No latency measurements available");
            return;
        }

        long count = latencyMeasurements.size();
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (Long latency : latencyMeasurements) {
            sum += latency;
            if (latency < min) {
                min = latency;
            }
            if (latency > max) {
                max = latency;
            }
        }

        long avgNanos = sum / count;
        double avgMillis = avgNanos / 1_000_000.0;
        double minMillis = min / 1_000_000.0;
        double maxMillis = max / 1_000_000.0;

        LOGGER.info("========== LATENCY STATISTICS ==========");
        LOGGER.info("Total Samples: " + count);
        LOGGER.info("Average Latency: " + avgNanos + " ns (" + String.format("%.3f", avgMillis) + " ms)");
        LOGGER.info("Minimum Latency: " + min + " ns (" + String.format("%.3f", minMillis) + " ms)");
        LOGGER.info("Maximum Latency: " + max + " ns (" + String.format("%.3f", maxMillis) + " ms)");
        LOGGER.info("Total Time: " + sum + " ns (" + String.format("%.3f", sum / 1_000_000.0) + " ms)");
        LOGGER.info("========================================");
    }

    /**
     * Resets latency measurements for a new test run.
     */
    public static void resetLatencyMeasurements() {
        latencyMeasurements.clear();
        LOGGER.info("Latency measurements reset");
    }

    private static Map<String, String> parseArgString(String arg) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = arg.split(",");

        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }


}
