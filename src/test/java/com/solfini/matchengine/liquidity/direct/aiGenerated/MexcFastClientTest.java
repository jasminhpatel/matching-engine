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
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import com.solfini.matchengine.liquidity.Ticker;
import static com.solfini.common.Constants.ADL_MAKER_ONLY;


public class MexcFastClientTest {
    final static String exchange = "MEXC";
    private static final CustomLogger LOGGER = CustomLogger.getLogger(MexcFastClientTest.class);

    private static void init() throws IOException {

        final LoggingThread loggingThread = Context.getLoggingThread();
        new Thread(loggingThread, "loggingThread").start();
        Properties properties = new Properties();
        PoolSize.minimize(properties);
        properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "100");
        properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "50");
        properties.setProperty("INITIAL_USER_CACHE_SIZE", "128");
        PropertyReader.initialize(null, properties);

        final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000, 0,false, 1, Sector.NOT_DEFINED);
        usdt.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usdt);
        final User user = new User(100);
        UserCache.setTestUser(user);
    }

    public static void main(String[] args) throws InterruptedException, IOException {



        init();
        LiquiditySubscriptionCache.onLoad(getSpotAccount());

        LiquiditySubscriptionCache.startAllSubscriptions();

        Thread.sleep(1000);
        ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange, false);


        testGetTicker(subscription);
        //testSpotTrade(subscription);
        //testOpenOrders(spotSegmentSubscription, subscription);
        printCache(subscription);
    }

/*
    private static void testOpenOrders(LiquiditySubscription spotSegmentSubscription, LiquiditySubscription futureSegmentSubscription) {
        MexcFastClient futureClient = (MexcFastClient) futureSegmentSubscription.getClient();
        MexcFastClient spotClient = (MexcFastClient) spotSegmentSubscription.getClient();
        futureSegmentSubscription.getUsdtBalance();
        String futureJson = futureClient.getAllOpenOrders();
        String spotJson = spotClient.getAllOpenOrders();
        LOGGER.info("Future Open Orders :"+futureJson);
        LOGGER.info("SPOT Open Orders :"+spotJson);
    }*/

/*
    private static void testTransferBalance(LiquiditySubscription spotSegmentSubscription, LiquiditySubscription futureSegmentSubscription) {
        MexcFastClient futureClient = (MexcFastClient) futureSegmentSubscription.getClient();
        MexcFastClient spotClient = (MexcFastClient) spotSegmentSubscription.getClient();

        final boolean result = futureClient.transferBalance("USDT", "6.5", false);
        if(result){
            LOGGER.info("Transfer completed successfully");
        }else{
            LOGGER.info("Something went wrong while Transfer balance");
        }


    }
*/

    static void printCache(ExchangeSubscription subscription) {
        while (true) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Do you want print the cache again?");
            System.out.println();
            System.out.println();
            System.out.println();
            System.out.println();
            System.out.println("Press ENTER to continue...");
            scanner.nextLine();  // Waits until user presses enter
            printIt(subscription);
        }
    }


    private static void testFutureTrade(ExchangeSubscription futureSegmentSubscription) throws InterruptedException {
        MexcFastClient futureClient = (MexcFastClient) futureSegmentSubscription.getClient();

        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("SOLUSDT");
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setOrdType(OrdType.LIMIT); //TODO set every where we use to set execution report
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);  //TODO try with fill or kill
        order.setPrice(180_00, (short) 2);
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(2L, (short) 1); //0.2
        order.setUser(UserCache.getTestUser());
        order.setTargetStrategy(ADL_MAKER_ONLY);  //TODO set every where we use to set execution report

        LOGGER.info("New Order: " + order);


        // Submit the order
        final ExecutionReportMessage executionReportMessage = futureClient.sendOrder(order, true, null, null, 0, 0, 0);
        Thread.sleep(1000);
        // Print result
        LOGGER.info("Execution report for clientOrderId    " + executionReportMessage.getClOrdId() + "   :" + executionReportMessage.toJSON());

    }



    private static void testGetTicker(final ExchangeSubscription subscription) {
        final MexcFastClient client = (MexcFastClient) subscription.getClient();

        // instrumentType: 1 = Perps, 2 = Spot
        final Ticker btcTicker = client.getTicker("BTC", "USDT", 2);
        if (btcTicker != null) {
            LOGGER.info("BTC/USDT Spot Ticker: symbol=" + btcTicker.getSymbol()
                    + " instrumentType=" + btcTicker.getInstrumentType()
                    + " last=" + btcTicker.getLast()
                    + " bid=" + btcTicker.getBid()
                    + " ask=" + btcTicker.getAsk()
                    + " high=" + btcTicker.getHigh()
                    + " low=" + btcTicker.getLow()
                    + " volume=" + btcTicker.getVolume()
                    + " change%=" + btcTicker.getPercentageChange()
                    + " ts=" + btcTicker.getTimestamp());
        } else {
            LOGGER.warn("BTC/USDT Spot Ticker returned null");
        }

        final Ticker solTicker = client.getTicker("SOL", "USDT", 2);
        if (solTicker != null) {
            LOGGER.info("SOL/USDT Spot Ticker: symbol=" + solTicker.getSymbol()
                    + " last=" + solTicker.getLast()
                    + " bid=" + solTicker.getBid()
                    + " ask=" + solTicker.getAsk()
                    + " change%=" + solTicker.getPercentageChange()
                    + " ts=" + solTicker.getTimestamp());
        } else {
            LOGGER.warn("SOL/USDT Spot Ticker returned null");
        }
    }

    public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription) throws InterruptedException {
        MexcFastClient spotClient = (MexcFastClient) spotSegmentSubscription.getClient();

        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("SOLUSDT");
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setOrdType(OrdType.LIMIT); //TODO set every where we use to set execution report
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);  //TODO try with fill or kill
        order.setPrice(125_25, (short) 2);
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(75L, (short)3); //0.2 0.059
        order.setUser(UserCache.getTestUser());
        order.setTargetStrategy(ADL_MAKER_ONLY);  //TODO set every where we use to set execution report


        LOGGER.info("Placing New Order: " + order);

        // Submit the order
        final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, false, null, null, 0, 0, 0);

        spotClient.cancelOrder(order,true);
        // Print result
        LOGGER.info("Execution report for clientOrderId    " + executionReportMessage.getClOrdId() + "   :" + executionReportMessage.toJSON());
    }

    private static void printIt(ExchangeSubscription spotSegmentSubscription) {
        ConcurrentHashMap<String, LastBalance> balances = spotSegmentSubscription.getBalanceCache();
        ConcurrentHashMap<String, LastBalance> positions = spotSegmentSubscription.getPositionCache();
        ConcurrentHashMap<String, Order> orders = spotSegmentSubscription.getOrders();
        ConcurrentHashMap<String, ExecutionReportMessage> executionReports = spotSegmentSubscription.getEXECUTION_REPORT_CACHE();

        LOGGER.info("################################################");
        LOGGER.info( "Future Enable >>> Balances count=" + balances.size() + "  Positions count=" + positions.size());
        LOGGER.info("USDT balance:>> " + spotSegmentSubscription.getUsdtBalance());

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
            LOGGER.info("ExecutionReportMessage: " + report.toJSON());
            //LOGGER.info("Error Reason: "+report.getError());
        }
        LOGGER.info("################################################");


    }


    public static ExchangeSubscription getSpotAccount() {
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(2);
        subscription.setExchange(exchange);
        subscription.setApiUser("dummyUser");
        subscription.setApiKey(System.getenv("MEXC_API_KEY"));
        subscription.setApiSecret(System.getenv("MEXC_API_SECRET"));
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(false);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");

        return subscription;
    }

    //-Dcom.google.protobuf.use_unsafe_pre22_gencode=true

}
