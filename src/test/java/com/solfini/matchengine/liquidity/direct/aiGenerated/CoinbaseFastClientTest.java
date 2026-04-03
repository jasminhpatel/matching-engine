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
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.solfini.common.Constants.ADL_MAKER_ONLY;

public class CoinbaseFastClientTest {

    final static String exchange = "COINBASE";
    private static final CustomLogger LOGGER = CustomLogger.getLogger(CoinbaseFastClientTest.class);

    private static void init() throws IOException {
        final LoggingThread loggingThread = Context.getLoggingThread();
        new Thread(loggingThread, "loggingThread").start();
        Properties properties = new Properties();
        PoolSize.minimize(properties);
        properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "100");
        properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "50");
        properties.setProperty("INITIAL_USER_CACHE_SIZE", "128");
        PropertyReader.initialize(null, properties);

        final Instrument usd = new Instrument(1, "USD", "USD", (short) 2, (short) 6, 1, 1000, 0,
                false, 1, Sector.NOT_DEFINED);
        usd.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usd);

        final Instrument usdc = new Instrument(2, "USDC", "USDC", (short) 2, (short) 6, 1, 1000, 0,
                false, 1, Sector.NOT_DEFINED);
        usdc.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usdc);

        final User user = new User(100);
        UserCache.setTestUser(user);
    }

    public static void main(String[] args) throws Exception {
      /*  if (args.length == 0) {
            throw new IllegalArgumentException("Missing trade argument string");
        }
        Map<String, String> params = parseArgString(args[0]);*/

        init();

        // Determine if futures or spot based on params
        //boolean isFutures = "true".equalsIgnoreCase(params.get("futures"));

 /*       if (isFutures) {
            LiquiditySubscriptionCache.onLoad(getFutureAccount());
        } else {
            LiquiditySubscriptionCache.onLoad(getSpotAccount());
        }*/
        LiquiditySubscriptionCache.onLoad(getSpotAccount());
        LiquiditySubscriptionCache.onLoad(getFutureAccount());

        LiquiditySubscriptionCache.startAllSubscriptions();

        Thread.sleep(2000);
        ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange, false);

       // LiquiditySubscription spotSubscription = LiquiditySubscriptionCache.get(exchange, false);

   /*     if (isFutures) {
            testFutureTradeFromParams(subscription, params);
        } else {
            testSpotTradeFromParams(subscription, params);
        }
*/
     //   testSpotTrade(subscription);
      //  testFutureTrade(subscription);
      //  testOpenOrders(spotSubscription, subscription);
        //testTransferBalance(spotSubscription);
        Thread.sleep(2000);
        testGetTicker(subscription);
        testInstrumentInfo(subscription);
        //testSpotTrade(subscription);
        //testSpotTradeFromParams(subscription, params);

        printCache(subscription);
    }
    private static void testGetTicker(final ExchangeSubscription subscription) {
        final CoinbaseFastClient client = (CoinbaseFastClient) subscription.getClient();

        // instrumentType: 1 = Perps, 2 = Spot
        final Ticker btcTicker = client.getTicker("BTC", "USDT", 2);
        if (btcTicker != null) {
            LOGGER.info("BTC-USDT Spot Ticker: symbol=" + btcTicker.getSymbol()
                    + " instrumentType=" + btcTicker.getInstrumentType()
                    + " last=" + btcTicker.getLast()
                    + " volume=" + btcTicker.getVolume()
                    + " change%=" + btcTicker.getPercentageChange()
                    + " ts=" + btcTicker.getTimestamp());
        } else {
            LOGGER.warn("BTC-USDT Spot Ticker returned null");
        }

        final Ticker solTicker = client.getTicker("SOL", "USDT", 2);
        if (solTicker != null) {
            LOGGER.info("SOL-USDT Spot Ticker: symbol=" + solTicker.getSymbol()
                    + " last=" + solTicker.getLast()
                    + " volume=" + solTicker.getVolume()
                    + " change%=" + solTicker.getPercentageChange()
                    + " ts=" + solTicker.getTimestamp());
        } else {
            LOGGER.warn("SOL-USDT Spot Ticker returned null");
        }
    }

    private static void testInstrumentInfo(ExchangeSubscription subscription) {
        List<ExternalSymbol> data = subscription.getClient().getExchangeInstrumentsFull();
        for(ExternalSymbol externalSymbol : data){
            System.out.println("SymbolData ="+ externalSymbol.toString());
        }
    }
    private static void testSpotTradeFromParams(ExchangeSubscription sub, Map<String, String> p) throws InterruptedException {
        String symbol = p.get("s");
        String price = p.get("p");
        String priceScale = p.get("ps");
        String qty = p.get("q");
        String qtyScale = p.get("qs");
        String side = p.get("side");
        String type = p.get("t");
        String timeInForce = p.get("tf");

        testSpotTrade(sub, symbol, price, priceScale, qty, qtyScale, side, type, timeInForce);
    }

    private static void testFutureTradeFromParams(ExchangeSubscription sub, Map<String, String> p) throws InterruptedException {
        String symbol = p.get("s");
        String price = p.get("p");
        String priceScale = p.get("ps");
        String qty = p.get("q");
        String qtyScale = p.get("qs");
        String side = p.get("side");
        String type = p.get("t");
        String timeInForce = p.get("tf");

        testFutureTrade(sub, symbol, price, priceScale, qty, qtyScale, side, type, timeInForce);
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

    private static void testOpenOrders(ExchangeSubscription spotSegmentSubscription,
                                       ExchangeSubscription futureSegmentSubscription) {
        CoinbaseFastClient futureClient = (CoinbaseFastClient) futureSegmentSubscription.getClient();
        CoinbaseFastClient spotClient = (CoinbaseFastClient) spotSegmentSubscription.getClient();

        String futureJson = futureClient.getAllOpenOrders();
        String spotJson = spotClient.getAllOpenOrders();

        LOGGER.info("Coinbase Future Open Orders: " + futureJson);
        LOGGER.info("Coinbase SPOT Open Orders: " + spotJson);
    }

    private static void testTransferBalance(ExchangeSubscription subscription) throws Exception {
        CoinbaseFastClient client = (CoinbaseFastClient) subscription.getClient();

        // Note: Coinbase uses portfolio IDs for transfers
        // These should be obtained from the account details
        final String fromPortfolioId = "source-portfolio-uuid";
        final String toPortfolioId = "target-portfolio-uuid";

        final boolean result = client.transferBalance( "USDC", "0.5", true);
        if (result) {
            LOGGER.info("Coinbase transfer completed successfully");
        } else {
            LOGGER.info("Coinbase transfer failed");
        }
    }

    static void printCache(ExchangeSubscription subscription) {
        while (true) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Do you want to print the cache again?");
            System.out.println();
            System.out.println();
            System.out.println();
            System.out.println();
            System.out.println("Press ENTER to continue...");
            scanner.nextLine();
            printIt(subscription);
        }
    }

    private static void testFutureTrade(ExchangeSubscription futureSegmentSubscription)
            throws InterruptedException {
        CoinbaseFastClient futureClient = (CoinbaseFastClient) futureSegmentSubscription.getClient();

        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("XRP-PERP-INTX"); // Coinbase futures format
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setOrdType(OrdType.LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(178, (short) 2); // $90,000.00
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(7L, (short) 0); // 1 contract
        order.setUser(UserCache.getTestUser());
        order.setTargetStrategy(ADL_MAKER_ONLY);

        LOGGER.info("Placing Coinbase Future Order: " + order);

        final ExecutionReportMessage executionReportMessage = futureClient.sendOrder(order, true, null,
                null, 0, 0, 0);

        Thread.sleep(1000);

/*        if (!(order.isRejected() || order.isExecuted())) {
            futureClient.cancelOrder(order, false);
        }*/
        LOGGER.info("Coinbase Execution report for clientOrderId " + executionReportMessage.getClOrdId() + ": "
                + executionReportMessage.toJSON());
    }

    private static void testFutureTrade(ExchangeSubscription futureSegmentSubscription,
                                        String symbol, String price, String priceScale,
                                        String qty, String qtyScale, String side,
                                        String type, String timeInforce) throws InterruptedException {
        CoinbaseFastClient futureClient = (CoinbaseFastClient) futureSegmentSubscription.getClient();

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
            order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        } else {
            order.setTimeInForce(TimeInForce.FILL_OR_KILL);
        }

        order.setPrice(Long.parseLong(price), (short) Long.parseLong(priceScale));
        order.setQty(Long.parseLong(qty), (short) Long.parseLong(qtyScale));
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setUser(UserCache.getTestUser());
        order.setTargetStrategy(ADL_MAKER_ONLY);

        LOGGER.info("Placing Coinbase Future Order: " + order);

        final ExecutionReportMessage executionReportMessage = futureClient.sendOrder(order, true, null,
                null, 0, 0, 0);

        LOGGER.info("Coinbase Execution report for clientOrderId " + executionReportMessage.getClOrdId() + ": "
                + executionReportMessage.toJSON());
    }

    public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription,
                                     String symbol, String price, String priceScale,
                                     String qty, String qtyScale, String side,
                                     String type, String timeInforce) throws InterruptedException {
        CoinbaseFastClient spotClient = (CoinbaseFastClient) spotSegmentSubscription.getClient();

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
            order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        } else {
            order.setTimeInForce(TimeInForce.FILL_OR_KILL);
        }

        order.setPrice(Long.parseLong(price), (short) Long.parseLong(priceScale));
        order.setQty(Long.parseLong(qty), (short) Long.parseLong(qtyScale));
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setUser(UserCache.getTestUser());
        order.setTargetStrategy(ADL_MAKER_ONLY);

        LOGGER.info("Placing Coinbase Spot Order: " + order);

        final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, false, null,
                null, 0, 0, 0);

        LOGGER.info("Coinbase Execution report for clientOrderId " + executionReportMessage.getClOrdId() + ": "
                + executionReportMessage.toJSON());
    }

    public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription)
            throws InterruptedException {
        CoinbaseFastClient spotClient = (CoinbaseFastClient) spotSegmentSubscription.getClient();

        Order order = new Order();
        order.setOrderId(TimeUtil.getTime());
        order.setSymbol("XRP-USDT"); // Coinbase spot format
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setOrdType(OrdType.LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(170, (short) 2); // $90,000.00
        order.setClOrdId(String.valueOf(TimeUtil.getTime()));
        order.setQty(1, (short) 0); // 0.0001 BTC
        order.setUser(UserCache.getTestUser());
        order.setTargetStrategy(ADL_MAKER_ONLY);

        LOGGER.info("Placing Coinbase Spot Order: " + order);

        final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, false, null,
                null, 0, 0, 0);

        LOGGER.info("Coinbase Execution report for clientOrderId " + executionReportMessage.getClOrdId() + ": "
                + executionReportMessage.toJSON());

     /*   if (!(order.isRejected() || order.isExecuted())) {
            spotClient.cancelOrder(order, true);
        }*/

    }

    private static void printIt(ExchangeSubscription subscription) {
        ConcurrentHashMap<String, LastBalance> balances = subscription.getBalanceCache();
        ConcurrentHashMap<String, LastBalance> positions = subscription.getPositionCache();
        ConcurrentHashMap<String, Order> orders = subscription.getOrders();
        ConcurrentHashMap<String, ExecutionReportMessage> executionReports = subscription.getEXECUTION_REPORT_CACHE();

        LOGGER.info("################################################");
        LOGGER.info("Coinbase - Futures Enabled: " + subscription.isFuturesEnabled() +
                " | Balances count=" + balances.size() + " | Positions count=" + positions.size());

        // Coinbase uses USD/USDC instead of USDT
        final double usdBalance = subscription.getBalance("USDT");
        final double usdcBalance = subscription.getBalance("USDC");
        LOGGER.info("USDT balance: " + usdBalance);
        LOGGER.info("USDC balance: " + usdcBalance);

        for (final LastBalance balance : balances.values()) {
            if (balance.getQuantity() <= 0.0) {
                continue;
            }
            LOGGER.info("Balance: " + balance);
        }

        for (final LastBalance position : positions.values()) {
            if (position.getQuantity() == 0.0) {
                continue;
            }
            LOGGER.info("Position: " + position);
        }

        for (final Order order : orders.values()) {
            LOGGER.info("Order: " + order);
        }

        for (final ExecutionReportMessage report : executionReports.values()) {
            LOGGER.info("ExecutionReportMessage: " + report.toJSON());
        }
        LOGGER.info("################################################");
    }

    static ExchangeSubscription getFutureAccount() {
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(1);
        subscription.setExchange(exchange);
        subscription.setApiUser("dummyUser");
        subscription.setApiKey(System.getenv("COINBASE_FUTURE_API_KEY"));
        subscription.setApiSecret(System.getenv("COINBASE_FUTURE_API_SECRET")); // EC private key in PEM format
       /* subscription.setApiKey(System.getenv("COINBASE_API_KEY"));
        subscription.setApiSecret(System.getenv("COINBASE_API_SECRET"));*/
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(true);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");
        return subscription;
    }

    public static ExchangeSubscription getSpotAccount() {
        final ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(1);
        subscription.setExchange(exchange);
        subscription.setApiUser("dummyUser");
        subscription.setApiKey(System.getenv("COINBASE_API_KEY"));
        subscription.setApiSecret(System.getenv("COINBASE_API_SECRET")); // EC private key in PEM format
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(false);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy("localhost");
        return subscription;
    }
}