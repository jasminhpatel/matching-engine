package com.solfini.matchengine.model.orderbook;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.TreeSet;
import com.solfini.sbe.encoder.Side;
import org.junit.Test;
import org.slf4j.event.Level;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.StopLimitContainer;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

/**
 *
 * @author Chris Mack
 *
 */
public class StopLimitContainerTest extends OrderBookTest {

  protected void configure() {
    LogLevel.setLevel(Level.TRACE);

    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "4194304");
    properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "4028");
    properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "1024");
    properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_POOL_START_CAPACITY", "32768");
    properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "32768");
    properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "32768");
    properties.setProperty("NUM_ENCODER_THREADS", "0");
    properties.setProperty("NUM_DECODER_THREADS", "8");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    // onConfigure(properties);

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /*
   * Create 2 instruments. Create an instrument pair quoted on them. Patch an instrument. Patch the instrument pair.
   */
  @Test
  public void testTree() {
    TreeSet<Order> treeSet = new TreeSet<>(orderComparatorHigh);
    Random random = new Random();
    StopLimitContainer stopLimitContainer = new StopLimitContainer(12);

    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 1__000; i++) {
      final User user = UserCache.get(1 + random.nextInt(100));
      final long price = 100000 + random.nextInt(100);
      final long quantity = 1000 + random.nextInt(400);
      final Side side = (random.nextInt(100) % 2 == 0) ? Side.BUY : Side.SELL;
      final Order order = createOrder(i, user, BTC_USDT_F, price, quantity, side, DAY);

      // orderBook.addOrder(order);
      treeSet.add(order);
      System.out.println(">> " + order);
      stopLimitContainer.addBuyLimit(order);
      stopLimitContainer.addSellLimit(order);
    }

    List<Order> list = new ArrayList<>();
    List<Order> list2 = new ArrayList<>();

    stopLimitContainer.getTriggeredBuyLimitList(100050, list);
    stopLimitContainer.getTriggeredSellLimitList(100050, list2);

    System.out.println("t0=" + (System.currentTimeMillis() - t0));
    for (Order order : list) {
      System.out.println("<<1 " + order);
    }
    for (Order order : list2) {
      System.out.println("<<2 " + order);
    }

  }

  // sort by smallest to largest
  private static final Comparator<Order> orderComparatorLow = new Comparator<Order>() {
    @Override
    public int compare(final Order order1, final Order order2) {
      try {
        if (order1.getPriceInt() == order2.getPriceInt()) {
          if (order1.getOrderId() == order2.getOrderId()) {
            return 0;
          } else if (order1.getOrderId() > order2.getOrderId())
            return 1;
          else
            return -1;
        } else if (order1.getPriceInt() > order2.getPriceInt())
          return 1;
        else
          return -1;
      } catch (Exception e) {
        // LOGGER.error(ERROR_LOG, e);
      }
      return 0;
    }
  };


  // sort by smallest to largest
  private static final Comparator<Order> orderComparatorHigh = new Comparator<Order>() {
    @Override
    public int compare(final Order order1, final Order order2) {
      try {
        if (order1.getPriceInt() == order2.getPriceInt()) {
          if (order1.getOrderId() == order2.getOrderId()) {
            return 0;
          } else if (order1.getOrderId() > order2.getOrderId())
            return 1;
          else
            return -1;
        } else if (order1.getPriceInt() > order2.getPriceInt())
          return -1;
        else
          return 1;
      } catch (Exception e) {
        // LOGGER.error(ERROR_LOG, e);
      }
      return 0;
    }
  };
}
