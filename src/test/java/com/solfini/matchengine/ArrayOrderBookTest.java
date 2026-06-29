package com.solfini.matchengine;

import com.solfini.matchengine.orderbook.LiquidityOrderBook;
import com.solfini.pool.LiquidationOrderObjectPool;
import com.solfini.pool.OrderMatchingThreadObjectPool;
import com.solfini.pool.PositionMatchThreadObjectPool;
import com.solfini.pool.UserOpenOrdersByPairMatchThreadObjectPool;
import java.text.NumberFormat;
import java.util.Properties;
import java.util.Random;
import java.util.SplittableRandom;

import com.solfini.internal.admin.schema.Sector;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.event.Level;
import org.junit.Assert;

import com.solfini.common.Constants;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.user.User;
import com.solfini.util.FastArrayList;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;

/**
 *
 * @author Chris Mack
 *
 */
public class ArrayOrderBookTest {
  private static int ORDER_COUNT = 0;
  private int clOrdIdCounter = 0;

  @BeforeClass
  public static void before() {
    LogLevel.setLevel(Level.ERROR);
    NumberFormat.getInstance().setGroupingUsed(true);

    try {
      int poolSize = ORDER_COUNT;

      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", String.valueOf(poolSize));
      properties.setProperty("ORDER_POOL_START_CAPACITY", String.valueOf(poolSize));
      properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", String.valueOf(2 * poolSize));
      properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", String.valueOf(2 * poolSize));
      properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "1000");
      properties.setProperty("POSITION_POOL_START_CAPACITY", "1000");
      properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "4");
      properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "4");
      properties.setProperty("LIQUIDATION_ORDER_POOL_QUEUE_CAPACITY", "4");
      properties.setProperty("LIQUIDATION_ORDER_POOL_START_CAPACITY", "4");
      properties.setProperty("MIN_TOP_N_ORDER_VALUE", "2000000000000.00");
      properties.setProperty("INITIAL_USER_CACHE_SIZE", "8");
      properties.setProperty("ACK_REJECT_MESSAGES", "FALSE");
      PropertyReader.initialize(null, properties);

      System.out.println("Warming up object pools");
      OrderObjectPool.init();
      OrderMatchingThreadObjectPool.init();
      ExecutionReportObjectPool.init();
      PositionMatchThreadObjectPool.init();
      UserOpenOrdersByPairMatchThreadObjectPool.init();
      LiquidationOrderObjectPool.getSize(); //static initializer
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void measureOrderBookPerformance_10ThousandOrders() {
    measureOrderBookPerformance(10_000);
  }

  @Test
  public void measureOrderBookPerformance_1MillionOrders() {
    measureOrderBookPerformance(1_000_000);
  }

  @Test
  public void measureOrderBookPerformance_5MillionOrders() {
    measureOrderBookPerformance(5_000_000);
  }

  private static String format(final long value) {
    return NumberFormat.getInstance().format(value);
  }

  private static String format(final double value) {
    return NumberFormat.getInstance().format(value);
  }

  public void measureOrderBookPerformance(final int orderCount) {
    System.out.println("Loading...");
    final FastArrayList<Order> orderList = makeOrders(orderCount);
    System.out.println("Loaded " + format(orderList.size()) + " orders");

    final Instrument base = new Instrument(1, "BTC", "BTC", (short) 2, (short) 6, 3500, 10000, 0,false, 1, Sector.NOT_DEFINED);
    final Instrument quoted = new Instrument(2, "USD", "USD", (short) 6, (short) 6, 1, 10000, 0,false, 2, Sector.NOT_DEFINED);
    final InstrumentPair instrumentPair =
        new InstrumentPair(3, "BTC/USD", "BTC/USD", base, quoted, (short) 6, (short) 6, 0, AssetType.PAIR, 10, 20, 3500, 0, Sector.NOT_DEFINED);

    final int DEFAULT_ARR_SIZE = 10_000_000;
    final int DEFAULT_CACHE_DEPTH = 64;

    final OrderBook orderBook = OrderBookFactory.create(OrderBookFactory.ARRAY_ORDER_BOOK, OrderBookFactory.NO_PREORDER_CHECK,
        instrumentPair, DEFAULT_ARR_SIZE, DEFAULT_CACHE_DEPTH);
    orderBook.setSettleCoinUsdMarkInstrument(quoted);
    ArrayOrderBook arrayOrderBook = ((ArrayOrderBook) orderBook);
    arrayOrderBook.setPublishAcks(false);

    System.out.println("Starting");
/*    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }*/
    long start = System.nanoTime();
    for (final Order order : orderList) {
      orderBook.addOrder(order);
    }

    long elapsed = (System.nanoTime() - start) / 1_000_000;
    System.out.println("Orders=" + format(orderBook.getOrderCount()) + ", Filled=" + format(orderBook.getFilledCount()));
    System.out.println("Done in " + format(elapsed) + " ms (" + format((1_000 * orderCount) / elapsed) + " orders/s)");
    System.out.println();
    System.out.println("OrderObjectPool -size: " + OrderObjectPool.getSize() + " capacity: " + OrderObjectPool.getCapacity());
    System.out.println("OrderMatchingThreadObjectPool -size: " + OrderMatchingThreadObjectPool.getSize() + " capacity: " + OrderMatchingThreadObjectPool.getCapacity());
    System.out.println("ExecutionReportObjectPool -size: " + ExecutionReportObjectPool.getSize() + " capacity: " + ExecutionReportObjectPool.getCapacity());
    System.out.println("PositionMatchThreadObjectPool -size: " + PositionMatchThreadObjectPool.getSize() + " capacity: " + PositionMatchThreadObjectPool.getCapacity());
    System.out.println("UserOpenOrdersByPairMatchThreadObjectPool -size: " + UserOpenOrdersByPairMatchThreadObjectPool.getSize() + " capacity: " + UserOpenOrdersByPairMatchThreadObjectPool.getCapacity());
    System.out.println("LiquidationOrderObjectPool -size: " + LiquidationOrderObjectPool.getSize() + " capacity: " + LiquidationOrderObjectPool.getCapacity());
/*    try {
      Thread.sleep(20000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }*/
  }

  private FastArrayList<Order> makeOrders(final int limit) {
    final Random random = new Random();
    final int account = 18;
    final String clientOrderId = "clOrdId";
    final User user = new User(18);
    final FastArrayList<Order> list = new FastArrayList<Order>(limit);

    long orderId = 0;
    try {
      for (int i = 0; i < limit; i++) {
        final int orderType = random.nextInt(2);
        final int side = orderType == Constants.BUY_LIMIT ? 1 : 2;
        final long quantity = 1 + random.nextInt(100_000);

        long price = 0;
        if (orderType == Constants.BUY_LIMIT) {
          price = 1 + random.nextInt(1_050_000);
        } else {
          price = 1_000_000 + random.nextInt(2_000_000);
        }

        final Order order = OrderObjectPool.get();
        order.setAccount(account);
        order.setUser(user);
        order.setOrderId(orderId++);
        order.setType(orderType);
        order.setPriceInt((int) price);
        order.setQuantityLong(quantity);
        order.setClOrdId(("ClOrdId" + ++clOrdIdCounter));
        order.setSecurityId(3);

        final OrdType ordType = OrdType.LIMIT;
        final Side sideType = side == 1 ? Side.BUY : Side.SELL;
        order.setPrice(price, (short) 2);
        order.setQty(quantity, (short) 2);
        order.setOrdType(ordType);
        order.setSide(sideType);

        list.add(order);
        if (orderId % 100_000 == 0) {
          System.out.println("... " + format(orderId));
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    return list;
  }

  public static void main(String args[]) throws Exception {
    LogLevel.setLevel(Level.ERROR);
    int orderCount = 1_000_000;
    if (args.length == 1) {
      orderCount = Integer.parseInt(args[0]);
    }
    ORDER_COUNT = orderCount;

    ArrayOrderBookTest test = new ArrayOrderBookTest();
    ArrayOrderBookTest.before();
    test.measureOrderBookPerformance(orderCount);
    System.exit(0);
  }
}
