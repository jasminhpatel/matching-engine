package com.solfini.util;

import static com.solfini.util.benchmark.TestEncoder.encodeBalance;
import static com.solfini.util.benchmark.TestEncoder.encodeCancelReplace;
import static com.solfini.util.benchmark.TestEncoder.encodeFeeAdminMessage;
import static com.solfini.util.benchmark.TestEncoder.encodeLogon;
import static com.solfini.util.benchmark.TestEncoder.encodeNewOrder;
import static com.solfini.util.benchmark.TestEncoder.encodeNewUser;
import static com.solfini.util.benchmark.TestEncoder.encodeOrderCancel;
import static com.solfini.util.benchmark.TestEncoder.encodeStopLimitOrder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;

public class ConsistencyTestInjector {

  public static final String API_KAFKA_TOPIC_IN = "API_KAFKA_TOPIC_IN";
  private static final int USDC = 1;
  private static final int DEFAULT_ORDER_COUNT = 100000;
  private static final int DEFAULT_SEED = 16180;
  private static final int DEFAULT_MATCH_PERCENTAGE = 40;
  private static final long USDC_BALANCE = 1_000_000;
  private static final long USDC_PX_SCALE_MULT = 100; // factor of 2
  private static final long BTC_USDC_PX_SCALE_MULT = 1000; // factor of 3
  private static final int USDC_QTY_SCALE_MULT = 100_000_000; // factor of 8
  private static final int BASE_BTC_PX = 10000_00; // 10,000.00
  private final Random random = new Random();

  private static List<Integer> instrumentIds = new ArrayList<>();
  private static List<User> users = new ArrayList<>();

  private static final int BTC_USDC_F = 25;
  private List<OrdType> orderTypes = new ArrayList<>();

  private long lastDelayTime = 0;
  private long delayTimeNs;

  public ConsistencyTestInjector(long msgsPerSecond) {
    orderTypes.add(OrdType.LIMIT);
    orderTypes.add(OrdType.MARKET);
    orderTypes.add(OrdType.STOP_LIMIT);
    this.delayTimeNs = 1000_000_000 / msgsPerSecond;
    System.out.println("XX Delay time(ns) : " + this.delayTimeNs);
  }

  private void delay() {
    if (lastDelayTime == 0) {
      lastDelayTime = System.nanoTime();
    }

    long now = System.nanoTime();
    while (this.delayTimeNs > now - lastDelayTime) {
      now = System.nanoTime();
    }
    lastDelayTime = now;
  }

  private void createUser(User user) {
    byte[] bytes = encodeNewUser(user);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.ADMIN_API);
  }

  private void login(final User user) {
    byte[] bytes = encodeLogon(user);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);
  }

  private void balance(final User user, final long quantity, final int quantityScale, final int securityId) {
    byte[] bytes = encodeBalance(user, quantity, quantityScale, securityId);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.ADMIN_API);
  }

  private void fee(FeeAdminMessage feeAdminMessage) {
    byte[] bytes = encodeFeeAdminMessage(feeAdminMessage);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.ADMIN_API);

  }

  private void order(String orderId, final User user, int instrumentPairId, long price, long qty, Side side, TimeInForce timeInForce,
      OrdType orderType) {
    byte[] bytes = encodeNewOrder(user, InstrumentCache.getPair(instrumentPairId), side, orderType, price, qty, orderId, timeInForce);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);
  }

  private void marketOrder(String orderId, final User user, int instrumentPairId, long qty, Side side, TimeInForce timeInForce) {
    order(orderId, user, instrumentPairId, 0, qty, side, timeInForce, OrdType.MARKET);
  }

  private void stopLimitOrder(String orderId, final User user, int instrumentPairId, long price, long stopPrice, long qty, Side side,
      TimeInForce timeInForce) {
    byte[] bytes = encodeStopLimitOrder(user, InstrumentCache.getPair(instrumentPairId), side, price, stopPrice, qty, orderId, timeInForce);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);
  }

  private void cancelOrder(String orderId, User user, int instrumentPairId, Side side, OrdType orderType, long qty) {
    byte[] bytes = encodeOrderCancel(orderId, user, InstrumentCache.getPair(instrumentPairId), side, orderType, qty);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);

  }

  private Order newOrder(int i, User user, int securityId, long price, long quantity, Side side, TimeInForce timeInForce) {
    Order order = new Order();
    order.setOrderId(i);
    order.setUser(user);
    order.setSecurityId(securityId);
    order.setPrice(price, (short) 2);
    order.setSide(side);
    order.setTimeInForce(timeInForce);
    order.setOrdType(OrdType.LIMIT);

    order(String.valueOf(i), order.getUser(), order.getSecurityId(), order.getPrice(), order.getQty(), order.getSide(),
        order.getTimeInForce(), order.getOrdType());

    return order;
  }

  private void cancelReplaceOrder(int cancelId, final long orderId, final User user, final int securityId, final long price,
      final long quantity, Side side, TimeInForce timeInForce, Order order) {
    byte[] bytes = encodeCancelReplace(String.valueOf(orderId), String.valueOf(cancelId), user, InstrumentCache.getPair(securityId), side,
        price, order.getPrice(), quantity, order.getQty());

    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);
  }

  private void createUsers(int userCount, int startUid) throws InterruptedException {

    System.out.println("XX Creating users");

    ArrayList<User> userList = new ArrayList<>();
    for (int i = 0; i < userCount; i++) {
      final int userId = startUid + i;

      User user = new User(userId);
      user.setPassword("pass_" + userId);
      user.setLogin("user_" + userId);
      user.setFeeTier(random.nextInt(5));
      userList.add(user);

      createUser(user);
      delay();
    }

    Thread.sleep(1_000);

    System.out.println("XX Sending login and balance");

    for (User user : userList) {
      login(user);
      delay();

      balance(user, USDC_BALANCE, USDC_QTY_SCALE_MULT, USDC);
      delay();
    }
  }

  private void updateInstruments() {

    System.out.println("XX Updating instruments");

    InstrumentPair pair = InstrumentCache.getPair(BTC_USDC_F);
    FeeAdminMessage feeAdminMessage =
        new FeeAdminMessage(new Fee(pair.getId(), pair.getQuotedId(), 0, FeeType.PERCENT, MakerTaker.ALL, 0, true));
    fee(feeAdminMessage);
    delay();
    feeAdminMessage = new FeeAdminMessage(new Fee(pair.getId(), pair.getQuotedId(), 100, FeeType.PERCENT, MakerTaker.ALL, 0, true));
    fee(feeAdminMessage);
    delay();
    feeAdminMessage = new FeeAdminMessage(new Fee(pair.getId(), pair.getQuotedId(), 200, FeeType.PERCENT, MakerTaker.ALL, 0, true));
    fee(feeAdminMessage);
    delay();
    feeAdminMessage = new FeeAdminMessage(new Fee(pair.getId(), pair.getQuotedId(), 300, FeeType.PERCENT, MakerTaker.ALL, 0, true));
    fee(feeAdminMessage);
    delay();
    feeAdminMessage = new FeeAdminMessage(new Fee(pair.getId(), pair.getQuotedId(), 400, FeeType.PERCENT, MakerTaker.ALL, 0, true));
    fee(feeAdminMessage);
    delay();

    // TODO: Set settle coin usd mark instrument

    pair.setIndexFeedUsdMark(100);
  }

  private void drain() {
    ArrayList<Message> messages = new ArrayList<>();
    Context.getMatcherToPublisherQueue().drainTo(messages, 1_000);
  }

  private void runMarketConsistency(int userCount, int userStart, int orderCount) {
    System.out.println("XX Injecting");
    TimeInForce[] timeInForceOptions =
        {TimeInForce.DAY, TimeInForce.GOOD_TILL_CANCEL, TimeInForce.FILL_OR_KILL, TimeInForce.IMMEDIATE_OR_CANCEL, TimeInForce.POST_ONLY};
    long totalNotional = 0;

    Order[] orders = new Order[orderCount];
    for (int i = 0; i < orderCount; i++) {
      final User user = UserCache.get(userStart + random.nextInt(userCount));
      final long price = (long) BASE_BTC_PX + random.nextInt(BASE_BTC_PX / 2);
      final long stopPrice = (long) BASE_BTC_PX + random.nextInt(BASE_BTC_PX / 2);
      final long quantity = 1000L + random.nextInt(500);
      final Side side = (random.nextInt(Math.abs((int) System.nanoTime())) % 2 == 0) ? Side.BUY : Side.SELL;
      final TimeInForce timeInForce = timeInForceOptions[random.nextInt(Math.abs((int) System.nanoTime())) % 5];
      totalNotional += quantity * price;
      switch (random.nextInt(Math.abs((int) System.nanoTime())) % 5) {
        case 1:
          marketOrder(String.valueOf(i), user, BTC_USDC_F, quantity, side, timeInForce);
          delay();
          break;
        case 2:
          stopLimitOrder(String.valueOf(i), user, BTC_USDC_F, price, stopPrice, quantity, side, timeInForce);
          delay();
          break;
        case 3:
          if (orders[i] != null) {
            Order order = orders[i];
            orders[i] = null;
            cancelOrder(String.valueOf(order.getOrderId()), order.getUser(), order.getSecurityId(), order.getSide(), order.getOrdType(),
                order.getPrice());
          } else {

            orders[i] = newOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce);
          }
          break;
        case 4:
          if (orders[i] != null) {
            Order order = orders[i];
            orders[i] = newOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce);
            cancelReplaceOrder(i, (int) order.getOrderId(), order.getUser(), order.getSecurityId(), order.getPrice(),
                order.getQuantityLong(), order.getSide(), order.getTimeInForce(), orders[i]);
          } else {
            orders[i] = newOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce);
          }
          break;
        default:
          orders[i] = newOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce);
          break;
      }

      if (i % 100 == 0) {
        drain();
      }
    }

    User exchangeUser = UserCache.getExchangeUser();
    Position usdcFeePosition = exchangeUser.getPosition(1);


    double usdcTotalNotional = (totalNotional * 1.0 / (USDC_PX_SCALE_MULT * BTC_USDC_PX_SCALE_MULT));
    System.out.println("XX Post Test: " + "fees=" + usdcFeePosition.getQuantity() + ", usdFees="
        + (usdcFeePosition.getQuantity() / (double) USDC_QTY_SCALE_MULT) + ", usdtotalNotional=" + usdcTotalNotional);

  }

  private void sendLogInForExistingUsers() {
    System.out.println("XX Sending Logon");
    for (User user : users) {
      login(user);
      delay();
    }
  }

  private void sendOrdersFromExistingUsers(int orderCount, int randomSeed, int matchPercentage) {
    System.out.println("XX Sending Orders");

    Random seededRandom = new Random(randomSeed);

    for (int i = 0; i < orderCount; i++) {
      User user = users.get(seededRandom.nextInt(users.size()));
      int instrumentPairId = instrumentIds.get(seededRandom.nextInt(instrumentIds.size()));
      Side side;
      OrdType orderType;
      long price;
      if (i % 2 == 0) {
        side = Side.BUY;
        orderType = orderTypes.get(seededRandom.nextInt(orderTypes.size()));
        if (orderType == OrdType.MARKET) {
          price = 0;
        } else {
          price = 1L + seededRandom.nextInt(100);
        }
      } else {
        side = Side.SELL;
        orderType = orderTypes.get(seededRandom.nextInt(orderTypes.size()));
        if (orderType == OrdType.MARKET) {
          price = 0;
        } else {
          price = 101L + seededRandom.nextInt(100) - matchPercentage;
        }
      }

      order("ConsistencyInjector_" + i, user, instrumentPairId, price, 1L + seededRandom.nextInt(100), side, TimeInForce.GOOD_TILL_CANCEL,
          orderType);
      delay();
    }
    System.out.println("XX Done");
  }

  private void runWithExistingData(int orderCount, int randomSeed, int matchPercentage) throws InterruptedException {
    sendLogInForExistingUsers();
    Thread.sleep(5000);
    sendOrdersFromExistingUsers(orderCount, randomSeed, matchPercentage);

  }

  private static void loadTestData(final BufferedReader reader) throws IOException {
    while (reader.ready()) {
      String line = reader.readLine();
      JsonObject obj = (JsonObject) new JsonParser().parse(line);
      if (obj.get("class").getAsString().equals("SecurityDefinitionAdminMessage") && obj.get("assetType").getAsString().equals("1")) {
        instrumentIds.add(obj.get("securityId").getAsInt());
      } else if (obj.get("class").getAsString().equals("UserAdminMessage") && obj.get("userType").getAsString().equals("0")) {
        User user = new User(obj.get("userId").getAsInt());
        user.setLogin(obj.get("username").getAsString());
        users.add(user);
      }
    }
  }

  public static void main(String[] args) {

    Options options = new Options();
    options.addOption(Option.builder("h").longOpt("help").desc("show help").required(false).build());
    options.addOption(Option.builder("c").longOpt("conf").desc("configuration file").hasArg().argName("file").required().build());
    options.addOption(Option.builder("s").longOpt("snap").desc("snap json file").hasArg().argName("file").required().build());
    options.addOption(Option.builder("uid").longOpt("start-uid").desc("starting user id for creating users").hasArg().argName("uid")
        .required(false).build());
    options.addOption(
        Option.builder("u").longOpt("users").desc("number of users to create").hasArg().argName("count").required(false).build());
    options.addOption(
        Option.builder("sd").longOpt("seed").desc("seed to generate random number").hasArg().argName("count").required(false).build());
    options.addOption(Option.builder("o").longOpt("orders").desc("number of orders to send").hasArg().argName("count").required().build());
    options.addOption(
        Option.builder("r").longOpt("rate").desc("number of messages per second to send").hasArg().argName("number").required().build());
    options.addOption(Option.builder("m").longOpt("match").desc("percentage or orders that match").hasArg().argName("percentage")
        .required(false).build());

    for (final String arg : args) {
      if (arg.equals("-h") || arg.equals("--help")) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Consistency Test Injector", options);
        System.out.println();
        return;
      }
    }

    System.out.println("XX Initializing");
    CommandLine cmd;
    try {
      CommandLineParser parser = new DefaultParser();
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      System.err.println("Run consistency test injector with --help option for usage information");
      return;
    }

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      PropertyReader.initialize(new FileInputStream(new File(cmd.getOptionValue("c"))), properties);
      loadTestData(new BufferedReader(new InputStreamReader(new FileInputStream(new File(cmd.getOptionValue("s"))))));
      System.out.println("XX Loading test data from snap");
      SnapLoader loader = new SnapLoader(Long.parseLong(PropertyReader.getProperty("LOAD_FROM_SNAP", "")));
      loader.load();

      int count;
      do {
        List<Message> list = new ArrayList<>(4096);
        count = Context.getReceiverToMatcherQueue().drainTo(list, 4096);
        for (int i = 0; i < count; i++) {
          list.get(i).onMatcher();
        }
      } while (count != 0);

      final long rate = Long.parseLong(cmd.getOptionValue("r"));
      final int orderCount = Integer.parseInt(cmd.getOptionValue("o", String.valueOf(DEFAULT_ORDER_COUNT)));
      final int seed = Integer.parseInt(cmd.getOptionValue("sd", String.valueOf(DEFAULT_SEED)));
      final int match = Integer.parseInt(cmd.getOptionValue("m", String.valueOf(DEFAULT_MATCH_PERCENTAGE)));

      System.out.println("XX Rate: " + rate);
      System.out.println("XX Orders: " + orderCount);
      System.out.println("XX Seed: " + seed);

      System.out.println("XX Running");
      ConsistencyTestInjector injector = new ConsistencyTestInjector(rate);
      injector.runWithExistingData(orderCount, seed, match);

    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("ERROR: " + e.getMessage());
      System.exit(1);
    }

    System.exit(0);
  }

}
