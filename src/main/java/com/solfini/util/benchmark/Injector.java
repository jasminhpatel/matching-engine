package com.solfini.util.benchmark;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.user.User;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import org.apache.commons.cli.*;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import static com.solfini.util.benchmark.TestEncoder.*;

public class Injector {
  public static final String API_KAFKA_TOPIC_IN = "API_KAFKA_TOPIC_IN";

  private final Random random = new Random();
  private final RateBenchmark rate = new RateBenchmark("Injector", 100000);
  private long delayTimeNs;
  private long lastDelayTime = 0;

  public Injector(long msgsPerSec) {
    this.delayTimeNs = 1000_000_000 / msgsPerSec;
    System.out.println("XX Delay time(ns) : " + this.delayTimeNs);
  }

  void delay() {
    if (lastDelayTime == 0) {
      lastDelayTime = System.nanoTime();
    }

    // busy wait for delay time
    long now = System.nanoTime();
    while (this.delayTimeNs > now - lastDelayTime) {
      now = System.nanoTime();
    }

    lastDelayTime = now;
    rate.sample();
  }

  private void createUser(User user) {
    byte[] bytes = encodeNewUser(user);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.ADMIN_API);
  }

  private void login(final User user) {
    byte[] bytes = encodeLogon(user);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);
  }

  public void balance(final User user, final long quantity, final int quantityScale, final int securityId) {
    byte[] bytes = encodeBalance(user, quantity, quantityScale, securityId);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.ADMIN_API);

  }

  public void order(final User user, final InstrumentPair instrument, Side side, OrdType orderType, long price, long qty, String orderId) {
    byte[] bytes = encodeNewOrder(user, instrument, side, orderType, price, qty, orderId, TimeInForce.GOOD_TILL_CANCEL);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);
  }

  public void massCancel(final User user, final InstrumentPair instrument, Side side) {
    byte[] bytes = encodeOrderMassCancel(null, user, instrument, side);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);
  }

  public void run(final long usersCount, final long ordersCount, final int matchPercentage, final int startUID, final long pauseCount,
      final long pauseTime, final long burstCount, final long burstAfter, final ArrayList<String> instruments, boolean massCancel) throws InterruptedException {

    ArrayList<User> users = new ArrayList<>();

    final ArrayList<InstrumentPair> instrumentPairs = new ArrayList<>();

    System.out.println("XX Users:" + usersCount + " Orders:" + ordersCount + " StartUID:" + startUID);

    for (final String symbol : instruments) {
      InstrumentPair instrument = InstrumentCache.getPairBySymbol(symbol);
      if (instrument != null) {
        instrumentPairs.add(instrument);
        System.out.println("XX InstrumentID:" + instrument.getId() + " QuotedInstrumentID:" + instrument.getQuotedId());
      } else {
        System.out.println("XX ERROR: Instrument not found for symbol: " + symbol + ". Will be ignored");
      }
    }

    // Create user objects
    for (int i = 0; i < usersCount; ++i) {
      final int id = i + startUID;
      User user = new User(id);
      user.setLogin("stress_test_user_" + id);
      user.setPassword("solfini123");
      user.setFirmId(0);
      user.setFeeTier(2);
      user.setLmm(false);

      users.add(user);
    }

    System.out.println("XX Creating users");
    for (int i = 0; i < usersCount; ++i) {
      createUser(users.get(i));
      delay();
    }

    Thread.sleep(1_000);

    System.out.println("XX Sending login and balance");

    for (int i = 0; i < usersCount; ++i) {
      User user = users.get(i);

      login(user);
      delay();

      for (final InstrumentPair instrument: instrumentPairs) {
        balance(user, 500000000, 0, instrument.getQuotedId());
        delay();
      }
    }

    boolean bursting = false;

    // Send orders
    System.out.println("XX Sending orders");
    for (long i = 0; i < ordersCount; ++i) {
      if (pauseCount == i) {
        System.out.println("XX Waiting for ME to warm up");
        Thread.sleep(pauseTime * 1_000);
        System.out.println("XX Resuming order submission");
      }

      User user = users.get(random.nextInt(users.size()));
      Side side;
      OrdType orderType;
      long price;
      if (i % 2 == 0) {
        side = Side.BUY;
        orderType = OrdType.LIMIT;
        price = 1L + random.nextInt(100);
      } else {
        side = Side.SELL;
        orderType = OrdType.LIMIT;
        price = 101L + random.nextInt(100) - matchPercentage;
      }

      InstrumentPair instrument = instrumentPairs.get(random.nextInt(instrumentPairs.size()));
      order(user, instrument, side, orderType, price, 1L + random.nextInt(100), "Injector_" + i);

      if (i < burstAfter || i >= burstAfter + burstCount) {
        if (bursting) {
          System.out.println("XX Stopping burst");
          bursting = false;
        }

        delay();
      } else {
        if (!bursting) {
          System.out.println("XX Bursting " + burstCount + " messages");
          bursting = true;
        }
      }
    }

    if (massCancel) {
      for (final InstrumentPair instrument: instrumentPairs) {
        System.out.println("XX Mass Cancel for InstrumentID: " + instrument.getId());
        for (User user : users) {
          massCancel(user, instrument, Side.BUY);
          massCancel(user, instrument, Side.SELL);
          delay();
        }
      }
    } else {
      System.out.println("Mass Cancel: False");
    }

    System.out.println("XX Done");
  }

  public static void main(String[] args) {
    Options options = new Options();
    options.addOption(Option.builder("h").longOpt("help").desc("show help").required(false).build());
    options.addOption(Option.builder("c").longOpt("conf").desc("configuration file").hasArg().argName("file").required().build());
    options
        .addOption(Option.builder("o").longOpt("orders").desc("number of orders to submit").hasArg().argName("count").required().build());
    options.addOption(Option.builder("pc").longOpt("pause-count").desc("pause order submission after this many orders").hasArg()
        .argName("count").required(false).build());
    options.addOption(Option.builder("pt").longOpt("pause-time").desc("pause order submission for this many seconds").hasArg()
        .argName("seconds").required(false).build());
    options.addOption(Option.builder("uid").longOpt("start-uid").desc("starting user id for created users").hasArg().argName("uid")
        .required(false).build());
    options.addOption(Option.builder("u").longOpt("users").desc("number of users to create").hasArg().argName("count").required().build());
    options.addOption(
        Option.builder("r").longOpt("rate").desc("number of messages per second to send").hasArg().argName("count").required().build());
    options.addOption(Option.builder("m").longOpt("match").desc("percentage or orders that match").hasArg().argName("percentage")
        .required(false).build());
    options.addOption(Option.builder("b").longOpt("burst").desc("number of messages to send as burst").hasArg().argName("count").build());
    options.addOption(
        Option.builder("ba").longOpt("burst after").desc("start bursting after this number of messages").hasArg().argName("count").build());
    options.addOption(
        Option.builder("mc").longOpt("mass-cancel").desc("Initiates a mass cancel of open orders").required(false).build());
    options.addOption(
        Option.builder("i").longOpt("instrument").desc("Symbols of instruments used for testing").required(false).hasArgs().build());
    for (final String arg : args) {
      if (arg.equals("-h") || arg.equals("--help")) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Injector", options);
        System.out.println();
        return;
      }
    }

    CommandLine cmd;
    try {
      CommandLineParser parser = new DefaultParser();
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      System.err.println("Run with --help option for usage information");
      return;
    }

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("INSTANCE_ID", "injector");
      properties.setProperty("NUM_ENCODER_THREADS", "0");
      PropertyReader.initialize(new FileInputStream(new File(cmd.getOptionValue("c"))), properties);

      SnapLoader loader = new SnapLoader(Long.parseLong(PropertyReader.getProperty("LOAD_FROM_SNAP", "")));
      loader.load();

      int count = 0;
      do {
        List<Message> list = new ArrayList<>(4096);
        count = Context.getReceiverToMatcherQueue().drainTo(list, 4096);
        for (int i = 0; i < count; i++) {
          Message message = list.get(i);
          if (!(message instanceof Order)) {
            message.onMatcher();
          }
        }
      } while (count != 0);

      final long users = Long.parseLong(cmd.getOptionValue("u"));
      final long orders = Long.parseLong(cmd.getOptionValue("o"));
      final long pauseCount = Long.parseLong(cmd.getOptionValue("pc", "1"));
      final long pauseTime = Long.parseLong(cmd.getOptionValue("pt", "20"));
      final long rate = Long.parseLong(cmd.getOptionValue("r"));
      final int uid = Integer.parseInt(cmd.getOptionValue("uid", "100"));
      final int match = Integer.parseInt(cmd.getOptionValue("m", "0"));
      final int burstCount = Integer.parseInt(cmd.getOptionValue("b", "0"));
      final int burstAfter = Integer.parseInt(cmd.getOptionValue("ba", "0"));
      final boolean massCancel = cmd.hasOption("mc");

      final ArrayList<String> instruments = new ArrayList<>();

      if (cmd.hasOption("i")) {
        for (final String symbol : cmd.getOptionValues("i")) {
          instruments.add(symbol);
        }
      } else {
        instruments.add("BTC/USDC");
      }

      Injector injector = new Injector(rate);
      injector.run(users, orders, match, uid, pauseCount, pauseTime, burstCount, burstAfter, instruments, massCancel);
    } catch (Exception e) {
      System.err.println("ERROR: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }

    System.exit(0);
  }
}
