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
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import static com.solfini.common.Constants.ADL_MAKER_ONLY;


public class BitmartFastClientTest {

  final static String exchange = "BITMART";
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BitmartFastClientTest.class);

  private static void init() throws IOException {
    long millis = System.currentTimeMillis();
    System.out.println(millis);
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

  public static void main(String[] args) throws Exception {

    init();
    LiquiditySubscriptionCache.onLoad(getBitmartFutureAccount());
    LiquiditySubscriptionCache.onLoad(getBitmartSpotAccount());

    LiquiditySubscriptionCache.startAllSubscriptions();

    Thread.sleep(1000);

    ExchangeSubscription futureSegmentSubscription = LiquiditySubscriptionCache.get(exchange, true);
    ExchangeSubscription spotSegmentSubscription = LiquiditySubscriptionCache.get(exchange, false);

    testGetTicker(spotSegmentSubscription);
    //testSpotTrade(spotSegmentSubscription);
    //testFutureTrade(futureSegmentSubscription);
    //testTransferBalance(spotSegmentSubscription, futureSegmentSubscription);
    //testOpenOrders(spotSegmentSubscription, futureSegmentSubscription);
    printCache(spotSegmentSubscription, futureSegmentSubscription);
  }

/*
  private static void testOpenOrders(LiquiditySubscription spotSegmentSubscription,
      LiquiditySubscription futureSegmentSubscription) {
    BitmartFastClient futureClient = (BitmartFastClient) futureSegmentSubscription.getClient();
    BitmartFastClient spotClient = (BitmartFastClient) spotSegmentSubscription.getClient();
    
    String futureJson = futureClient.getAllOpenOrders();
    String spotJson = spotClient.getAllOpenOrders();
    LOGGER.info("Future Open Orders :" + futureJson);
    LOGGER.info("SPOT Open Orders :" + spotJson);
  }
*/

  private static void testGetTicker(final ExchangeSubscription subscription) {
    final BitmartFastClient client = (BitmartFastClient) subscription.getClient();

    // instrumentType: 1 = Perps, 2 = Spot
    // Bitmart spot symbol format: BTC_USDT (underscore)
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
          + " ts=" + solTicker.getTimestamp());
    } else {
      LOGGER.warn("SOL/USDT Spot Ticker returned null");
    }
  }

  private static void testTransferBalance(ExchangeSubscription spotSegmentSubscription,
      ExchangeSubscription futureSegmentSubscription) {
    BitmartFastClient futureClient = (BitmartFastClient) futureSegmentSubscription.getClient();
    BitmartFastClient spotClient = (BitmartFastClient) spotSegmentSubscription.getClient();

    final boolean result = futureClient.transferBalance("USDT", "6.5", false);
    if (result) {
      LOGGER.info("Transfer completed successfully");
    } else {
      LOGGER.info("Something went wrong while Transfer balance");
    }
  }

  private static void printCache(ExchangeSubscription spotSegmentSubscription,
      ExchangeSubscription futureSegmentSubscription) {
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
      printIt(spotSegmentSubscription);
    }
  }


  private static void testFutureTrade(ExchangeSubscription futureSegmentSubscription) {
    BitmartFastClient futureClient = (BitmartFastClient) futureSegmentSubscription.getClient();

    Order order = new Order();
    order.setOrderId(TimeUtil.getTime());
    order.setSymbol("XRPUSDT");
    order.setSide(Side.BUY);
    order.setType(Constants.BUY_LIMIT);
    order.setOrdType(OrdType.LIMIT);
    order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
    order.setPrice(160, (short) 2);
    order.setClOrdId(String.valueOf(TimeUtil.getTime()));
    order.setQty(2L, (short) 0); //0.2
    order.setUser(UserCache.getTestUser());
    order.setTargetStrategy(ADL_MAKER_ONLY);

    LOGGER.info("New Order: " + order);

    // Submit the order
    final ExecutionReportMessage executionReportMessage = futureClient.sendOrder(order, true, null,
        null, 0, 0, 0);

    // Print result
    LOGGER.info(
        "Execution report for clientOrderId    " + executionReportMessage.getClOrdId() + "   :"
            + executionReportMessage.toJSON());


    if(!(order.isRejected()||order.isExecuted())){
      futureClient.cancelOrder(order,false);
    }
  }

  public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription) {
    BitmartFastClient spotClient = (BitmartFastClient) spotSegmentSubscription.getClient();
    Order order = new Order();
    order.setOrderId(TimeUtil.getTime());
    order.setSymbol("XRP_USDT");
    order.setSide(Side.BUY);
    order.setType(Constants.BUY_LIMIT);
    order.setOrdType(OrdType.LIMIT);
    order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
    order.setPrice(160L, (short) 2);
    order.setClOrdId(String.valueOf(TimeUtil.getTime()));
    order.setQty(5L, (short) 0);
    order.setUser(UserCache.getTestUser());
    order.setTargetStrategy(ADL_MAKER_ONLY);

    LOGGER.info("Placing New Order: " + order);

    // Submit the order
    final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, false, null,
        null, 0, 0, 0);

    // Print result
    LOGGER.info(
        "Execution report for clientOrderId    " + executionReportMessage.getClOrdId() + "   :"
            + executionReportMessage.toJSON());

    if(!(order.isRejected()||order.isExecuted())){
      spotClient.cancelOrder(order,true);
    }
  }

  private static void printIt(ExchangeSubscription subscription) {
    ConcurrentHashMap<String, LastBalance> balances = subscription.getBalanceCache();
    ConcurrentHashMap<String, LastBalance> positions = subscription.getPositionCache();
    ConcurrentHashMap<String, Order> orders = subscription.getOrders();
    ConcurrentHashMap<String, ExecutionReportMessage> executionReports = subscription.getEXECUTION_REPORT_CACHE();

    String segment = subscription.isFuturesEnabled() ? "FUTURE SEGMENT" : "SPOT SEGMENT";
    LOGGER.info("################################################");
    LOGGER.info(segment + ">>> Balances count=" + balances.size() + "  Positions count="
        + positions.size());
    LOGGER.info("USDT balance:>> " + subscription.getUsdtBalance());

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


  private static ExchangeSubscription getBitmartFutureAccount() {
    final ExchangeSubscription subscription = new ExchangeSubscription();
    subscription.setId(1);
    subscription.setExchange(exchange);
    subscription.setApiUser("dummyUser");
    subscription.setApiKey(System.getenv("BITMART_API_KEY"));
    subscription.setApiSecret(System.getenv("BITMART_API_SECRET"));
    subscription.setPassphrase("Daksh@2104");
    subscription.setStatus(1);
    subscription.setCreated(1700000000000L);
    subscription.setExpires(1709999999999L);
    subscription.setFuturesEnabled(true);
    subscription.setLeverage(false);
    subscription.setLastUsedProxy("localhost");
    return subscription;
  }

  public static ExchangeSubscription getBitmartSpotAccount() {
    final ExchangeSubscription subscription = new ExchangeSubscription();
    subscription.setId(2);
    subscription.setExchange(exchange);
    subscription.setApiUser("dummyUser");
    subscription.setApiKey(System.getenv("BITMART_API_KEY"));
    subscription.setApiSecret(System.getenv("BITMART_API_SECRET"));
    subscription.setPassphrase("Daksh@2104");
    subscription.setStatus(1);
    subscription.setCreated(1700000000000L);
    subscription.setExpires(1709999999999L);
    subscription.setFuturesEnabled(false);
    subscription.setLeverage(false);
    subscription.setLastUsedProxy("localhost");

    return subscription;
  }
}
