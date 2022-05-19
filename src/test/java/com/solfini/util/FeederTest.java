package com.solfini.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.event.Level;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.model.orderbook.OrderBookTest;
import com.solfini.sbe.encoder.OrdType;

public class FeederTest extends OrderBookTest {

  private static final String JSON_SNAP_PATH = "FeederTest.json";
  private Path path = null;

  @Before
  public void before() {
    super.before();
    LogLevel.setLevel(Level.TRACE);

    try {
      path = Files.createTempDirectory(System.getProperty("java.io.tmp"));
      Files.copy(getClass().getResourceAsStream("/" + JSON_SNAP_PATH), path.resolve(JSON_SNAP_PATH));
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Ignore
  @Test
  public void loadDataFromSnap() throws IOException {

    String absolutePath = path.toString() + "/" + JSON_SNAP_PATH;
    BufferedReader snapFile = new BufferedReader(new InputStreamReader(new FileInputStream(new File(absolutePath))));

    Feeder feeder = new Feeder(100);
    feeder.feedDataFromJsonSnap(snapFile);

    Assert.assertTrue(true);
  }

  @Ignore
  @Test
  public void generateOrderJsonStrings() {
    for (int i = 0; i < 20; i++) {
      // CancelOrder cancelOrder = createCancelOrder(101 + i, 1 + i, user, pair.getId(), 1011 + i, 500, Side.BUY, DAY);
      // Order replacementOrder = createOrder(1 + i, user, pair.getId(), 2301, 1000, Side.BUY, DAY);
      // CancelReplaceOrder cancelReplaceOrder = createCancelReplaceOrder(301 + i, 101 + i, user2, pair.getId(), 0, 500, Side.BUY, DAY,
      // replacementOrder);
      MassCancelOrder massCancelOrder = new MassCancelOrder();
      massCancelOrder.setUser(user);
      massCancelOrder.setSecurityId(pair.getId());
      massCancelOrder.setOrigOrderId(1 + i);
      massCancelOrder.setCancelId(101 + i);
      massCancelOrder.setClOrdId("clrOrdId");
      massCancelOrder.setAccount(user.getId());
      massCancelOrder.setOrdType(OrdType.LIMIT);

      System.out.println(massCancelOrder.toJSON());

    }
    Assert.assertTrue(true);
  }

  protected void configure() {
    LogLevel.setLevel(Level.TRACE);

    Properties properties = new Properties();
    properties.setProperty("PUBLISH_MARKET_DATA", "false");
    properties.setProperty("POSITION_REPORT_PARSER_START_CAPACITY", "64");
    properties.setProperty("QUEUE_CAPACITY", "100000");
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "100000");
    properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "4028");
    properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "1024");
    properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "1000");
    properties.setProperty("POSITION_POOL_START_CAPACITY", "1000");
    properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "10000");
    properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "5000");
    properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "10000");
    properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "5000");
    properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "10000");
    properties.setProperty("ORDER_POOL_START_CAPACITY", "5000");
    properties.setProperty("NUM_ENCODER_THREADS", "0");
    properties.setProperty("NUM_DECODER_THREADS", "0");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    onConfigure(properties);

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
