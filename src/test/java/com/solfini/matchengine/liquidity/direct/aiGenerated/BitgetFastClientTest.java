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
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class BitgetFastClientTest {
    final static String exchange = "BITGET";
    private static final CustomLogger LOGGER = CustomLogger.getLogger(BitgetFastClientTest.class);

    static {
        final Properties properties = new Properties();
        PoolSize.minimize(properties);
        properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "100");
        properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "50");
        properties.setProperty("INITIAL_USER_CACHE_SIZE", "128");
        properties.setProperty("BITGET_EXCHANGE_DEMO_TRADING_ENABLE", "true");
        try {
            PropertyReader.initialize(null, properties);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void init() throws IOException {
        final long millis = System.currentTimeMillis();
        System.out.println(millis);
        final LoggingThread loggingThread = Context.getLoggingThread();
        new Thread(loggingThread, "loggingThread").start();

        final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000, 0, false, 1, Sector.NOT_DEFINED);
        usdt.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usdt);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        init();
        LiquiditySubscriptionCache.onLoad(getBitgetFutureAccount());

        LiquiditySubscriptionCache.startAllSubscriptions();

        final ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange, true);
        Thread.sleep(1000);
        testGetTicker(subscription);
        //testSpotTrade(subscription);
       // testFutureTrade(subscription);
        //testOpenOrders(spotSegmentSubscription, subscription);
        printCache(subscription);

    }

/*    private static void testOpenOrders(final LiquiditySubscription spotSegmentSubscription, final LiquiditySubscription futureSegmentSubscription) {
        final BitgetFastClient futureClient = (BitgetFastClient) futureSegmentSubscription.getClient();
        final BitgetFastClient spotClient = (BitgetFastClient) spotSegmentSubscription.getClient();
        final String futureJson = futureClient.getAllOpenOrders();
        final String spotJson = spotClient.getAllOpenOrders();
        LOGGER.info("Future Open Orders: " + futureJson);
        LOGGER.info("SPOT Open Orders: " + spotJson);
    }*/

    private static void printCache(final ExchangeSubscription subscription) {
        while (true) {
            final Scanner scanner = new Scanner(System.in);
            System.out.println("Do you want print the cache again?");
            System.out.println();
            System.out.println();
            System.out.println();
            System.out.println();
            System.out.println("Press ENTER to continue...");
            scanner.nextLine();
            printIt(subscription);
        }
    }

    private static void testGetTicker(final ExchangeSubscription subscription) {
        final BitgetFastClient client = (BitgetFastClient) subscription.getClient();

        // instrumentType: 1 = Perps, 2 = Spot
        final Ticker spotTicker = client.getTicker("BTC", "USDT", 2);
        if (spotTicker != null) {
            LOGGER.info("BTC/USDT Spot Ticker: symbol=" + spotTicker.getSymbol()
                    + " instrumentType=" + spotTicker.getInstrumentType()
                    + " last=" + spotTicker.getLast()
                    + " bid=" + spotTicker.getBid()
                    + " ask=" + spotTicker.getAsk()
                    + " high=" + spotTicker.getHigh()
                    + " low=" + spotTicker.getLow()
                    + " volume=" + spotTicker.getVolume()
                    + " change%=" + spotTicker.getPercentageChange()
                    + " ts=" + spotTicker.getTimestamp());
        } else {
            LOGGER.warn("BTC/USDT Spot Ticker returned null");
        }

        final Ticker solTicker = client.getTicker("SOL", "USDT", 2);
        if (solTicker != null) {
            LOGGER.info("SOL/USDT Spot Ticker: symbol=" + solTicker.getSymbol()
                    + " last=" + solTicker.getLast()
                    + " bid=" + solTicker.getBid()
                    + " ask=" + solTicker.getAsk()
                    + " ts=" + solTicker.getTimestamp());
        } else {
            LOGGER.warn("SOL/USDT Spot Ticker returned null");
        }
    }

    private static void testFutureTrade(final ExchangeSubscription futureSegmentSubscription) {
        final BitgetFastClient futureClient = (BitgetFastClient) futureSegmentSubscription.getClient();

        final Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("SOLUSDT");
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(125_00, (short) 2);
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(3L, (short) 1);
        order.setUser(UserCache.getTestUser());

        LOGGER.info("New Order: " + order);

        final ExecutionReportMessage executionReportMessage = futureClient.sendOrder(order, true, null, null, 0, 0, 0);
        //LOGGER.info("Execution report for clientOrderId " + executionReportMessage.getClOrdId() + ": " + executionReportMessage);
        if(!(order.isRejected()||order.isExecuted())){
            futureClient.cancelOrder(order,false);
        }
    }

    public static void testSpotTrade(final ExchangeSubscription spotSegmentSubscription) throws InterruptedException {
        final BitgetFastClient spotClient = (BitgetFastClient) spotSegmentSubscription.getClient();
        final Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("BTCUSDT");
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(82_200_00L, (short) 2);
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(7L, (short) 5);
        order.setUser(UserCache.getTestUser());

        LOGGER.info("Placing New Order: " + order);

        final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, false, null, null, 0, 0, 0);
        LOGGER.info("Execution report for clientOrderId " + executionReportMessage.getClOrdId() + ": " + executionReportMessage);
        if(!(order.isRejected()||order.isExecuted())){
            spotClient.cancelOrder(order,true);
        }
    }

    private static void printIt(final ExchangeSubscription segmentSubscription) {
        final ConcurrentHashMap<String, LastBalance> balances = segmentSubscription.getBalanceCache();
        final ConcurrentHashMap<String, LastBalance> positions = segmentSubscription.getPositionCache();
        final ConcurrentHashMap<String, Order> orders = segmentSubscription.getOrders();
        final ConcurrentHashMap<String, ExecutionReportMessage> executionReports = segmentSubscription.getEXECUTION_REPORT_CACHE();

        final String segment = "FUTURE SEGMENT & SPOT SEGMENT";
        LOGGER.info("################################################");
        LOGGER.info(segment + ">>> Balances count=" + balances.size() + " Positions count=" + positions.size());
        LOGGER.info("USDT balance:>> " + segmentSubscription.getUsdtBalance());

        for (final LastBalance balance : balances.values()) {
            if (balance.getQuantity() <= 0.0)
                continue;
            LOGGER.info("Balance: " + balance);
        }

        for (final LastBalance position : positions.values()) {
            if (position.getQuantity() <= 0.0)
                continue;
            LOGGER.info("Position: " + position);
        }

        for (final Order order : orders.values()) {
            LOGGER.info("Order: " + order);
        }

        for (final ExecutionReportMessage report : executionReports.values()) {
            LOGGER.info("ExecutionReportMessage: " + report);
        }
        LOGGER.info("################################################");
    }

    private static ExchangeSubscription getBitgetFutureAccount() {
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(1);
        subscription.setExchange("BITGET");
        subscription.setApiUser("dummyUser");
        subscription.setApiKey(System.getenv("BITGET_DEMO_API_KEY"));
        subscription.setApiSecret(System.getenv("BITGET_DEMO_API_SECRET"));
        //subscription.setApiKey2(System.getenv("BITGET_DEMO_API_KEY"));
        //subscription.setApiSecret2(System.getenv("BITGET_DEMO_API_SECRET"));
        subscription.setStatus(0);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(true);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");
        subscription.setStatus(1);
        subscription.setPassphrase("Daksh2104");
        return subscription;
    }

    public static ExchangeSubscription getBitgetSpotAccount() {
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(1);
        subscription.setExchange("BITGET");
        subscription.setApiUser("dummyUser");
        subscription.setApiKey(System.getenv("BITGET_DEMO_API_KEY"));
        subscription.setApiSecret(System.getenv("BITGET_DEMO_API_SECRET"));
        //subscription.setApiKey2(System.getenv("BITGET_DEMO_API_KEY"));
        //subscription.setApiSecret2(System.getenv("BITGET_DEMO_API_SECRET"));
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(false);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");
        subscription.setPassphrase("Daksh2104");
        subscription.setStatus(1);
        return subscription;
    }
}
