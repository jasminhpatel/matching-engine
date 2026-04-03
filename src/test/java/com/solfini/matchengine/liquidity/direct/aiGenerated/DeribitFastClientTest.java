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
import com.solfini.sbe.encoder.OrdType;
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

import static com.solfini.common.Constants.ADL_MAKER_ONLY;

public class DeribitFastClientTest {

    final static String exchange = "DERIBIT";
    private static final CustomLogger LOGGER = CustomLogger.getLogger(DeribitFastClientTest.class);

    private static void init() throws IOException {
        final long millis = System.currentTimeMillis();
        System.out.println(millis);
        final LoggingThread loggingThread = Context.getLoggingThread();
        new Thread(loggingThread, "loggingThread").start();
        final Properties properties = new Properties();
        PoolSize.minimize(properties);
        properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "100");
        properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "50");
        properties.setProperty("INITIAL_USER_CACHE_SIZE", "128");
        PropertyReader.initialize(null, properties);
        final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000, 0, false, 1, Sector.NOT_DEFINED);
        usdt.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usdt);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        init();
        LiquiditySubscriptionCache.onLoad(getDeribitAccount());

        LiquiditySubscriptionCache.startAllSubscriptions();

        final ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange, true);
        Thread.sleep(3000);
     //  testSpotTrade(subscription);
       //testFutureTrade(subscription);
        // testOpenOrders(spotSegmentSubscription, subscription);
        testGetTicker(subscription);
        testInstrumentInfo(subscription);
        printCache(subscription);

    }

    private static void testGetTicker(final ExchangeSubscription subscription) {
        final DeribitFastClient client = (DeribitFastClient) subscription.getClient();

        // instrumentType: 1 = Perps, 2 = Spot
        final Ticker btcTicker = client.getTicker("BTC", "USDT", 1);
        if (btcTicker != null) {
            LOGGER.info("BTC-PERPETUAL Ticker: symbol=" + btcTicker.getSymbol()
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
            LOGGER.warn("BTC-PERPETUAL Ticker returned null");
        }

        final Ticker solTicker = client.getTicker("SOL", "USDT", 1);
        if (solTicker != null) {
            LOGGER.info("SOL-PERPETUAL Ticker: symbol=" + solTicker.getSymbol()
                    + " last=" + solTicker.getLast()
                    + " bid=" + solTicker.getBid()
                    + " ask=" + solTicker.getAsk()
                    + " change%=" + solTicker.getPercentageChange()
                    + " ts=" + solTicker.getTimestamp());
        } else {
            LOGGER.warn("SOL-PERPETUAL Ticker returned null");
        }
    }

    private static void testInstrumentInfo(ExchangeSubscription subscription) {
        List<ExternalSymbol> data = subscription.getClient().getExchangeInstrumentsFull();
        for(ExternalSymbol externalSymbol : data){
            System.out.println("SymbolData ="+ externalSymbol.toString());
        }
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

    public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription)
            throws InterruptedException {
        DeribitFastClient spotClient = (DeribitFastClient) spotSegmentSubscription.getClient();
        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("BTC_USDT");
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setOrdType(OrdType.LIMIT); //TODO set every where we use to set execution report
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);  //TODO try with fill or kill
        order.setPrice(40_900_00L, (short) 2);
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(1L, (short) 3);
        order.setUser(UserCache.getTestUser());
        order.setTargetStrategy(ADL_MAKER_ONLY);  //TODO set every where we use to set execution report

        LOGGER.info("Placing New Order: " + order);

        // Submit the order
        final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, false, null,
                null, 0, 0, 0);

        // Print result
        LOGGER.info(
                "Execution report for clientOrderId    " + executionReportMessage.getClOrdId() + "   :"
                        + executionReportMessage.toJSON());
        if(!(order.isRejected()||order.isExecuted())){
            spotClient.cancelOrder(order,false);
        }
    }

    private static void testFutureTrade(final ExchangeSubscription futureSegmentSubscription) {
        final DeribitFastClient futureClient = (DeribitFastClient) futureSegmentSubscription.getClient();

        final Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("BTC_USDT");  //TODO we have specific format of symbol
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_MARKET);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(52302_00, (short) 2);
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(3L, (short) 2);
        order.setUser(UserCache.getTestUser());

        LOGGER.info("New Order: " + order);

        final ExecutionReportMessage executionReportMessage = futureClient.sendOrder(order, true, null, null, 0, 0, 0);
        LOGGER.info("Execution report for clientOrderId " + executionReportMessage.getClOrdId() + ": " + executionReportMessage);
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

    private static ExchangeSubscription getDeribitAccount() {
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(1);
        subscription.setExchange(exchange);
        subscription.setApiUser("dummyUser");
        subscription.setApiKey(System.getenv("DERIBIT_DEMO_API_KEY"));
        subscription.setApiSecret(System.getenv("DERIBIT_DEMO_API_SECRET"));
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(true);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");
        subscription.setStatus(1);
        return subscription;
    }

}
