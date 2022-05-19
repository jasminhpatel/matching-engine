package com.solfini.util;

import static com.solfini.util.benchmark.TestEncoder.encodeCancelReplace;
import static com.solfini.util.benchmark.TestEncoder.encodeNewOrder;
import static com.solfini.util.benchmark.TestEncoder.encodeOrderCancel;
import static com.solfini.util.benchmark.TestEncoder.encodeOrderMassCancel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.util.snapshot.CancelOrderJsonDeserializer;
import com.solfini.util.snapshot.CancelReplaceOrderJsonDeserializer;
import com.solfini.util.snapshot.MassCancelOrderJsonDeserializer;
import com.solfini.util.snapshot.OrderJsonDeserializer;

public class Feeder {

  public static final String API_KAFKA_TOPIC_IN = "API_KAFKA_TOPIC_IN";

  private long lastDelayTime = 0;
  private long delayTimeNs;

  private List<Message> messageList = new ArrayList<>();


  Feeder(long msgsPerSecond) {
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

  private void addOrder(final User user, final InstrumentPair instrument, Side side, OrdType orderType, long price, long qty,
      String orderId) {
    byte[] bytes = encodeNewOrder(user, instrument, side, orderType, price, qty, orderId, TimeInForce.GOOD_TILL_CANCEL);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);
  }

  private void addCancelOrder(String orderId, User user, int instrumentPairId, Side side, OrdType orderType, long qty) {
    byte[] bytes = encodeOrderCancel(orderId, user, InstrumentCache.getPair(instrumentPairId), side, orderType, qty);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);
  }

  private void addCancelReplaceOrder(final long orderId, long originalOrderId, final User user, final int securityId, final long price,
      final long quantity, Side side, TimeInForce timeInForce, Order order) {
    byte[] bytes = encodeCancelReplace(String.valueOf(orderId), String.valueOf(originalOrderId), user, InstrumentCache.getPair(securityId),
        side, price, order.getPrice(), quantity, order.getQty());
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);
  }

  private void addMassCancelOrder(String orderId, User user, InstrumentPair instrument) {
    byte[] bytes = encodeOrderMassCancel(orderId, user, instrument, Side.SELL);
    Context.getKafkaPublisher().sendDirect(PropertyReader.getProperty(API_KAFKA_TOPIC_IN, ""), bytes, KafkaPublisher.NORMAL_API);
  }


  void feedDataFromJsonSnap(final BufferedReader reader) throws IOException {

    System.out.println("XX Loading messages from file");
    while (reader.ready()) {
      String line = reader.readLine();
      Message message = importMessage(line);
      if (message != null) {
        messageList.add(message);
      }
    }

    System.out.println("XX Loaded message count: " + messageList.size());
    System.out.println("XX Feeding");

    messageList.forEach(message -> {

      if (message instanceof Order) {
        Order order = (Order) message;
        addOrder(order.getUser(), InstrumentCache.getPair(order.getSecurityId()), order.getSide(), order.getOrdType(), order.getPrice(),
            order.getQty(), String.valueOf(order.getOrderId()));

      } else if (message instanceof CancelOrder) {
        CancelOrder cancelOrder = (CancelOrder) message;
        addCancelOrder(String.valueOf(cancelOrder.getOrigOrderId()), cancelOrder.getUser(), cancelOrder.getSecurityId(),
            cancelOrder.getSide(), cancelOrder.getOrdType(), cancelOrder.getQty());

      } else if (message instanceof CancelReplaceOrder) {
        CancelReplaceOrder cancelReplaceOrder = (CancelReplaceOrder) message;
        addCancelReplaceOrder(cancelReplaceOrder.getOrder().getOrderId(), cancelReplaceOrder.getOrigOrderId(), cancelReplaceOrder.getUser(),
            cancelReplaceOrder.getSecurityId(), cancelReplaceOrder.getPrice(), cancelReplaceOrder.getQty(),
            cancelReplaceOrder.getOrder().getSide(), cancelReplaceOrder.getOrder().getTimeInForce(), cancelReplaceOrder.getOrder());
      } else if (message instanceof MassCancelOrder) {
        MassCancelOrder massCancelOrder = (MassCancelOrder) message;
        addMassCancelOrder(String.valueOf(massCancelOrder.getOrigOrderId()), massCancelOrder.getUser(),
            InstrumentCache.getPair(massCancelOrder.getSecurityId()));
      }
      delay();
    });
  }

  private Message importMessage(String json) {

    JsonObject obj = (JsonObject) new JsonParser().parse(json);
    String messageType = obj.get("class").getAsString();

    final GsonBuilder builder = new GsonBuilder();
    Message message = null;
    switch (messageType) {
      case "Order":
        builder.registerTypeAdapter(Order.class, new OrderJsonDeserializer());
        message = builder.create().fromJson(json, Order.class);
        break;

      case "CancelOrder":
        builder.registerTypeAdapter(CancelOrder.class, new CancelOrderJsonDeserializer());
        message = builder.create().fromJson(json, CancelOrder.class);
        break;

      case "CancelReplaceOrder":
        builder.registerTypeAdapter(CancelReplaceOrder.class, new CancelReplaceOrderJsonDeserializer());
        message = builder.create().fromJson(json, CancelReplaceOrder.class);
        break;

      case "MassCancelOrder":
        builder.registerTypeAdapter(MassCancelOrder.class, new MassCancelOrderJsonDeserializer());
        message = builder.create().fromJson(json, MassCancelOrder.class);
        break;

      default:
        System.err.println("XX ERROR Unhandled message type: " + messageType);
    }

    return message;
  }

  public static void main(String[] args) {

    Options options = new Options();

    options.addOption(Option.builder("h").longOpt("help").desc("feeder help").required(false).build());
    options.addOption(Option.builder("c").longOpt("conf").desc("configuration file").hasArg().argName("file").required().build());
    options.addOption(Option.builder("s").longOpt("snap").desc("snap json file").hasArg().argName("file").required().build());
    options.addOption(
        Option.builder("r").longOpt("rate").desc("number of messages per second to send").hasArg().argName("number").required().build());

    for (final String arg : args) {
      if (arg.equals("-h") || arg.equals("--help")) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Order Feeder", options);
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
      System.err.println("Run order feeder test injector with --help option for usage information");
      System.exit(1);
      return;
    }

    Properties properties = new Properties();
    PoolSize.minimize(properties);

    try {
      PropertyReader.initialize(new FileInputStream(new File(cmd.getOptionValue("c"))), properties);
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

      System.out.println("XX Starting");

      final long rate = Long.parseLong(cmd.getOptionValue("r"));
      final String file = cmd.getOptionValue("s");

      Feeder feeder = new Feeder(rate);
      feeder.feedDataFromJsonSnap(new BufferedReader(new InputStreamReader(new FileInputStream(new File(file)))));

      System.out.println("XX Done");
      System.exit(0);

    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }


  }
}
