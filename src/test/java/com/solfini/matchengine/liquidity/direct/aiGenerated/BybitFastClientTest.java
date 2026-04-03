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
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import static com.solfini.common.Constants.ADL_MAKER_ONLY;


public class BybitFastClientTest {

  final static String exchange = "BYBIT";
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BybitFastClientTest.class);

  private static void init() throws IOException {

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
    final User user = new User(100);
    UserCache.setTestUser(user);
  }

    public static void main(String[] args) throws InterruptedException, IOException {
/*
        if (args.length == 0) {
            throw new IllegalArgumentException("Missing trade argument string");
        }
        Map<String, String> params = parseArgString(args[0]);
*/

        init();
        LiquiditySubscriptionCache.onLoad(getFutureAccount());
        //LiquiditySubscriptionCache.onLoad(getSpotAccount());

        LiquiditySubscriptionCache.startAllSubscriptions();

        Thread.sleep(1000);
        ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange, true);

        //testSpotTrade(subscription);
        //testFutureTrade(subscription);
        //testOpenOrders(spotSegmentSubscription, subscription);

        Thread.sleep(2000);

        //testSpotTrade(subscription);
      //  testSpotTradeFromParams(subscription, params);

        testGetTicker(subscription);

        printCache(subscription);


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

  /*public static void main(String[] args) throws InterruptedException, IOException {

    init();
    LiquiditySubscriptionCache.onLoad(getFutureAccount());
    //LiquiditySubscriptionCache.onLoad(getSpotAccount());

    LiquiditySubscriptionCache.startAllSubscriptions();

    Thread.sleep(1000);
    LiquiditySubscription subscription = LiquiditySubscriptionCache.get(exchange, true);

    //testSpotTrade(subscription);
    testFutureTrade(subscription);
    //testOpenOrders(spotSegmentSubscription, subscription);
    printCache(subscription);
  }*/

  private static void testOpenOrders(ExchangeSubscription spotSegmentSubscription,
      ExchangeSubscription futureSegmentSubscription) {
    BinanceFastClient futureClient = (BinanceFastClient) futureSegmentSubscription.getClient();
    BinanceFastClient spotClient = (BinanceFastClient) spotSegmentSubscription.getClient();
    futureSegmentSubscription.getUsdtBalance();
    String futureJson = futureClient.getAllOpenOrders();
    String spotJson = spotClient.getAllOpenOrders();
    LOGGER.info("Future Open Orders :" + futureJson);
    LOGGER.info("SPOT Open Orders :" + spotJson);
  }

  private static void testTransferBalance(ExchangeSubscription spotSegmentSubscription,
      ExchangeSubscription futureSegmentSubscription) throws Exception {
    BinanceFastClient futureClient = (BinanceFastClient) futureSegmentSubscription.getClient();
    BinanceFastClient spotClient = (BinanceFastClient) spotSegmentSubscription.getClient();

    final boolean result = futureClient.transferBalance("USDT", "6.5", false);
    if (result) {
      LOGGER.info("Transfer completed successfully");
    } else {
      LOGGER.info("Something went wrong while Transfer balance");
    }


  }

    private static void testGetTicker(final ExchangeSubscription subscription) {
        final BybitFastClient client = (BybitFastClient) subscription.getClient();

        // instrumentType: 1 = Linear (Perps), 2 = Spot
        final Ticker btcSpotTicker = client.getTicker("BTC", "USDT", 2);
        if (btcSpotTicker != null) {
            LOGGER.info("BTC/USDT Spot Ticker: symbol=" + btcSpotTicker.getSymbol()
                    + " instrumentType=" + btcSpotTicker.getInstrumentType()
                    + " last=" + btcSpotTicker.getLast()
                    + " bid=" + btcSpotTicker.getBid()
                    + " ask=" + btcSpotTicker.getAsk()
                    + " high=" + btcSpotTicker.getHigh()
                    + " low=" + btcSpotTicker.getLow()
                    + " volume=" + btcSpotTicker.getVolume()
                    + " change%=" + btcSpotTicker.getPercentageChange()
                    + " ts=" + btcSpotTicker.getTimestamp());
        } else {
            LOGGER.warn("BTC/USDT Spot Ticker returned null");
        }

        final Ticker solSpotTicker = client.getTicker("SOL", "USDT", 2);
        if (solSpotTicker != null) {
            LOGGER.info("SOL/USDT Spot Ticker: symbol=" + solSpotTicker.getSymbol()
                    + " instrumentType=" + solSpotTicker.getInstrumentType()
                    + " last=" + solSpotTicker.getLast()
                    + " bid=" + solSpotTicker.getBid()
                    + " ask=" + solSpotTicker.getAsk()
                    + " high=" + solSpotTicker.getHigh()
                    + " low=" + solSpotTicker.getLow()
                    + " volume=" + solSpotTicker.getVolume()
                    + " change%=" + solSpotTicker.getPercentageChange()
                    + " ts=" + solSpotTicker.getTimestamp());
        } else {
            LOGGER.warn("SOL/USDT Spot Ticker returned null");
        }

        final Ticker btcLinearTicker = client.getTicker("BTC", "USDT", 1);
        if (btcLinearTicker != null) {
            LOGGER.info("BTC/USDT Linear Ticker: symbol=" + btcLinearTicker.getSymbol()
                    + " instrumentType=" + btcLinearTicker.getInstrumentType()
                    + " last=" + btcLinearTicker.getLast()
                    + " bid=" + btcLinearTicker.getBid()
                    + " ask=" + btcLinearTicker.getAsk()
                    + " high=" + btcLinearTicker.getHigh()
                    + " low=" + btcLinearTicker.getLow()
                    + " volume=" + btcLinearTicker.getVolume()
                    + " change%=" + btcLinearTicker.getPercentageChange()
                    + " ts=" + btcLinearTicker.getTimestamp());
        } else {
            LOGGER.warn("BTC/USDT Linear Ticker returned null");
        }
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


  private static void testFutureTrade(ExchangeSubscription futureSegmentSubscription)
      throws InterruptedException {
    BybitFastClient futureClient = (BybitFastClient) futureSegmentSubscription.getClient();

    Order order = new Order();
    order.setOrderId(TimeUtil.getTime());
    order.setSymbol("XRPUSDT");
    order.setSide(Side.BUY);
    order.setType(Constants.BUY_LIMIT);
    order.setOrdType(OrdType.LIMIT); //TODO set every where we use to set execution report
    order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);  //TODO try with fill or kill
    order.setPrice(218, (short) 2);
    order.setClOrdId(String.valueOf(TimeUtil.getTime()));
    order.setQty(3L, (short) 0); //0.2
    order.setUser(UserCache.getTestUser());
    order.setTargetStrategy(ADL_MAKER_ONLY);  //TODO set every where we use to set execution report

    LOGGER.info("New Order: " + order);

    // Submit the order
    final ExecutionReportMessage executionReportMessage = futureClient.sendOrder(order, true, null,
        null, 0, 0, 0);
    Thread.sleep(1000);
    // Print result
    LOGGER.info(
        "Execution report for clientOrderId    " + executionReportMessage.getClOrdId() + "   :"
            + executionReportMessage.toJSON());

  }

    public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription,String symbol, String price, String priceScale, String qty, String qtyScale, String side, String type, String timeInforce) throws InterruptedException {
        BybitFastClient spotClient = (BybitFastClient) spotSegmentSubscription.getClient();
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


        LOGGER.info("Placing New Order: " + order);


        // Submit the order
        final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, false, null,
                null, 0, 0, 0);

        // Print result
        LOGGER.info(
                "Execution report for clientOrderId    " + executionReportMessage.getClOrdId() + "   :"
                        + executionReportMessage.toJSON());
    }


    public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription)
      throws InterruptedException {
    BybitFastClient spotClient = (BybitFastClient) spotSegmentSubscription.getClient();
    Order order = new Order();
    order.setOrderId(TimeUtil.getTime());
    order.setSymbol("XRPUSDT");
    order.setSide(Side.BUY);
    order.setType(Constants.BUY_LIMIT);
    order.setOrdType(OrdType.LIMIT); //TODO set every where we use to set execution report
    order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);  //TODO try with fill or kill
    order.setPrice(22100, (short) 4);
    order.setClOrdId(String.valueOf(TimeUtil.getTime()));
    order.setQty(3L, (short) 0);
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
  }

  private static void printIt(ExchangeSubscription spotSegmentSubscription) {
    ConcurrentHashMap<String, LastBalance> balances = spotSegmentSubscription.getBalanceCache();
    ConcurrentHashMap<String, LastBalance> positions = spotSegmentSubscription.getPositionCache();
    ConcurrentHashMap<String, Order> orders = spotSegmentSubscription.getOrders();
    ConcurrentHashMap<String, ExecutionReportMessage> executionReports = spotSegmentSubscription.getEXECUTION_REPORT_CACHE();

    LOGGER.info("################################################");
    LOGGER.info("Future Enable >>> Balances count=" + balances.size() + "  Positions count="
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


  static ExchangeSubscription getFutureAccount() {
    final ExchangeSubscription subscription = new ExchangeSubscription();
    subscription.setId(1);
    subscription.setExchange(exchange);
    subscription.setApiUser("dummyUser");
    subscription.setApiKey(System.getenv("BYBIT_API_KEY"));
    subscription.setApiSecret(System.getenv("BYBIT_API_SECRET"));
    subscription.setStatus(1);
    subscription.setCreated(1700000000000L);
    subscription.setExpires(1709999999999L);
    subscription.setFuturesEnabled(true);
    subscription.setLeverage(false);
    subscription.setLastUsedProxy("localhost");
    subscription.setBrokerId("dummy-broker-id");
    return subscription;
  }

  public static ExchangeSubscription getSpotAccount() {
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
    subscription.setBrokerId("dummy-broker-id");
    return subscription;
  }


}
