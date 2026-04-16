package com.solfini.matchengine.liquidity.direct.aiGenerated.prodtest;

import static com.solfini.common.Constants.ADL_MAKER_ONLY;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.Sector;
import com.solfini.matchengine.LoggingThread;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.executionexchange.ExternalTickerCache;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.LastBalance;
import com.solfini.matchengine.liquidity.LiquiditySubscriptionCache;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.FastClientFactory;
import com.solfini.matchengine.liquidity.direct.aiGenerated.BitgetFastClient;
import com.solfini.matchengine.liquidity.direct.aiGenerated.ExternalExchangeClient;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class BitgetFastClientRestOnlyTest {
  final static String exchange = "BITGET";
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BitgetFastClientProdTest.class);
  private static final List<Long> latencyMeasurements = new ArrayList<>();

  static {
    try {
      Properties properties = new Properties();
      //properties.setProperty("OUTBOUND_IP", System.getenv("OUTBOUND_IP"));
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

    init();
    ExternalSymbol externalSymbol = getExchangeSymbol();

    ExchangeSubscription subscription = getBitgetSpotAccount();
    final ExternalExchangeClient fastClient = FastClientFactory.createRestOnlyClient(subscription);
    final Ticker ticker = ExternalTickerCache.getTicker(externalSymbol, fastClient);
    System.out.println(ticker.toString());


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
      scanner.nextLine();
      printIt(futureSegmentSubscription);
    }
  }

  public static void testSpotTrade(ExchangeSubscription spotSegmentSubscription, Map<String, String> p) throws InterruptedException {
    BitgetFastClient spotClient = (BitgetFastClient) spotSegmentSubscription.getClient();

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
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
      } else {
        order.setTimeInForce(TimeInForce.FILL_OR_KILL);
      }

      order.setPrice(Long.parseLong(price), (short) Long.parseLong(priceScale));
      order.setQty(Long.parseLong(qty), (short) Long.parseLong(qtyScale));

      order.setClOrdId(String.valueOf(TimeUtil.getTime()));
      order.setUser(UserCache.getTestUser());
      order.setTargetStrategy(ADL_MAKER_ONLY);

      LOGGER.info("Iteration no:" + (i + 1) + " Placing New Order: " + order);
      order.setClOrdId(String.valueOf(TimeUtil.getTime()));

      long startTime = System.nanoTime();
      final ExecutionReportMessage executionReportMessage = spotClient.sendOrder(order, true, null,
          null, 0, 0, 0);
      long endTime = System.nanoTime();
      long latencyNanos = endTime - startTime;
      latencyMeasurements.add(latencyNanos);

      LOGGER.info(
          "Execution report for clientOrderId    " + executionReportMessage.getClOrdId() + "   :"
              + executionReportMessage.toJSON());

      if (!order.isRejected()) {
        boolean isCancelled = spotClient.cancelOrder(order, true);
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
    LOGGER.info("Future Account >>> Balances count=" + balances.size() + "  Positions count="
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
    }
    LOGGER.info("################################################");
  }


  static ExchangeSubscription getBitgetSpotAccount() {
    final ExchangeSubscription subscription = new ExchangeSubscription();
    subscription.setId(1);
    subscription.setExchange(exchange);
    subscription.setApiUser("dummyUser");
    subscription.setApiKey(System.getenv("BITGET_DEMO_API_KEY"));
    subscription.setApiSecret(System.getenv("BITGET_DEMO_API_SECRET"));
    subscription.setPassphrase(System.getenv("BITGET_PASSPHRASE"));
    subscription.setStatus(1);
    subscription.setCreated(1700000000000L);
    subscription.setExpires(1709999999999L);
    subscription.setFuturesEnabled(false);
    subscription.setLeverage(false);
    subscription.setLastUsedProxy("38.242.225.103");
    subscription.setRestOnly(true);
    subscription.setForceToUseProxy(true);
    subscription.setConnectionType(1);
    return subscription;
  }

  static ExternalSymbol getExchangeSymbol() {
    ExternalSymbol externalSymbol = new ExternalSymbol();
    externalSymbol.setId(1);
    externalSymbol.setExchange("bitget");
    externalSymbol.setSymbol("BTCUSDT");
    externalSymbol.setBase("BTC");
    externalSymbol.setQuote("USDT");
    //externalSymbol.setPrompt("btc");
    externalSymbol.setFutures(false);
    externalSymbol.setTradable(true);

    return externalSymbol;
  }
}
