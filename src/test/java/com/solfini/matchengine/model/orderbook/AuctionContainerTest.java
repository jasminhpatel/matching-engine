package com.solfini.matchengine.model.orderbook;

import static com.solfini.sbe.encoder.TimeInForce.DAY;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.TreeSet;
import org.junit.Test;
import org.slf4j.event.Level;

import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.AuctionContainer;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

/**
 *
 * @author Chris Mack
 *
 */
public class AuctionContainerTest extends OrderBookTest {

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

  /*
   * Create 2 instruments. Create an instrument pair quoted on them. Patch an instrument. Patch the instrument pair.
   */
  @Test
  public void testTree() {
    final InstrumentPair pair = InstrumentCache.getPair(BTC_USDT);
    TreeSet<Order> treeSet = new TreeSet<>(orderComparatorHigh);
    Random random = new Random();
    AuctionContainer auctionContainer = new AuctionContainer(pair);

    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 1__00; i++) {
      final User user = UserCache.get(1 + random.nextInt(100));
      final long price = 100000 + random.nextInt(100);
      final long quantity = 1000 + random.nextInt(400);
      final Side side = (random.nextInt(100) % 2 == 0) ? Side.BUY : Side.SELL;
      final Order order = createOrder(i, user, BTC_USDT_F, price, quantity, side, DAY);
      if (random.nextInt(2) == 1)
        order.setOrdType(OrdType.MARKET);

      // orderBook.addOrder(order);
      treeSet.add(order);
      System.out.println(">> " + order);
      auctionContainer.addBuyLimit(order);
      auctionContainer.addSellLimit(order);
    }

    List<Order> list = new ArrayList<>();
    List<Order> list2 = new ArrayList<>();

    auctionContainer.getTriggeredBuyLimitList(0, list);
    auctionContainer.getTriggeredSellLimitList(200050, list2);

    System.out.println("t0=" + (System.currentTimeMillis() - t0));
    for (Order order : list) {
      System.out.println("<<1 " + order);
    }
    for (Order order : list2) {
      System.out.println("<<2 " + order);
    }

  }

  @Test
  public void testPriceCalc() {
    // Asset scale is 8.
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 8, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 8, 8));
    // Symbol price & quantity scale both 2
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 2));

    final InstrumentPair pair = InstrumentCache.getPair(BTC_USDT);
    final AuctionContainer auctionContainer = new AuctionContainer(pair);
    int optimalAskPrice = 0;

    auctionContainer.addBuyLimit(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    auctionContainer.addSellLimit(createOrder(3, user2, pair.getId(), 1011, 1500, Side.SELL, DAY));
    auctionContainer.addBuyLimit(createOrder(1, user, pair.getId(), 1005, 600, Side.BUY, DAY));

    auctionContainer.calcAuctionPrice(pair.getOrderBook(), 1000);
    optimalAskPrice = auctionContainer.getOptimalAskPrice();
    long volume = auctionContainer.getMaxQtyMatchedAtLevel();
    System.out.println("<<optimalAskPrice " + optimalAskPrice);
    System.out.println("<<volume " + volume);
    assert (optimalAskPrice == 1011);
    assert (volume == 500);

    auctionContainer.addBuyLimit(createOrder(4, user, pair.getId(), 1015, 500, Side.BUY, DAY));
    auctionContainer.addSellLimit(createOrder(5, user2, pair.getId(), 1011, 1500, Side.SELL, DAY));
    auctionContainer.calcAuctionPrice(pair.getOrderBook(), 0);
    optimalAskPrice = auctionContainer.getOptimalAskPrice();
    volume = auctionContainer.getMaxQtyMatchedAtLevel();
    System.out.println("<<optimalAskPrice " + optimalAskPrice);
    System.out.println("<<volume " + volume);
    assert (optimalAskPrice == 1011);
    assert (volume == 1000);

    auctionContainer.closeAuction((ArrayOrderBook) pair.getOrderBook());

    assert (auctionContainer.getBuyTreeSet().size() == 0);
    assert (auctionContainer.getSellTreeSet().size() == 0);

    long t0 = System.currentTimeMillis();
  }
}
