package com.solfini.matchengine;

import com.solfini.common.Constants;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.Sector;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.pool.BusinessRejectObjectPool;
import com.solfini.pool.CancelRejectObjectPool;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.util.FastArrayList;
import org.junit.Assert;
import uk.co.real_logic.artio.fields.DecimalFloat;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Random;

/**
 * @author Chris Mack
 */
public class ArrayOrderBookTest2 {


  private static String testOrdersPath = "../testOrderData/testGdaxOrders.data"; // "D:\\testOrderData\testGdaxOrders.data";
  private static String testOutputPath = "../testOrderData/fills_2_2.data";

  private static void saveTestOrders(final String filepath) {
    try {
      Path orderPath = FileSystems.getDefault().getPath(filepath);
      String symbol = "BTCUSD";
      int ARR_SIZE = 1_000_000;
      int orderId = 0;
      long time = System.currentTimeMillis();

      StringBuilder data = new StringBuilder();
      for (int i = 0; i < 500000; i++) {
        Random rand = new Random();
        int current = rand.nextInt(ARR_SIZE);
        int quantity = rand.nextInt(1000) + 1;
        data.append(symbol).append(",").append(orderId++).append(",").append(time).append(",").append(current - 1).append(",")
            .append(quantity).append(",").append(1).append("\n");

        rand = new Random();
        current = rand.nextInt(ARR_SIZE);
        quantity = rand.nextInt(1000) + 1;
        data.append(symbol).append(",").append(orderId++).append(",").append(time).append(",").append(Math.max(current - 10, 30))
            .append(",").append(quantity).append(",").append(2).append("\n");
      }

      Files.write(orderPath, data.toString().getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static ArrayList<Order> loadTestOrders(final String filename) {
    int orderId = 0;
    ArrayList<Order> list = new ArrayList<Order>();
    try {
      Path filePath = FileSystems.getDefault().getPath(filename);
      InputStream inputStream = Files.newInputStream(filePath);
      BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
      String line = null;
      while ((line = bufferedReader.readLine()) != null) {
        String[] orderData = line.split(",");
        int side = Integer.parseInt(orderData[5]);
        int orderType = side == 1 ? Constants.BUY_LIMIT : Constants.SELL_LIMIT;
        Order order = OrderObjectPool.get();
        order.setOrderId(orderId++);
        order.setType(orderType);
        order.setPriceInt(Integer.parseInt(orderData[3]));
        order.setQuantityLong(Integer.parseInt(orderData[4]));
        order.setClOrdId("clOrdId");

        list.add(order);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return list;
  }

  private static FastArrayList<Order> loadTestOrders3(final int limit) {
    Random random = new Random();
    int orderId = 0;
    String customerId = "5";
    char[] account = "18".toCharArray();
    char[] clorId = "clOrdId".toCharArray();
    User user = new User(18);
    FastArrayList<Order> list = new FastArrayList<Order>();
    try {
      // Path filePath = FileSystems.getDefault().getPath(filename);
      // InputStream inputStream = Files.newInputStream(filePath);
      // BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
      String line = null;
      for (int i = 0; i < 2_000_000; i++) {
        // String[] orderData = line.split(",");

        int orderType = random.nextInt(2);
        int side = orderType == Constants.BUY_LIMIT ? 1 : 2;
        long price = 0;
        if (orderType == Constants.BUY_LIMIT) {
          price = 1 + random.nextInt(1__000);
        } else {
          price = 1_000_000 + random.nextInt(2__000);
        }
        long quantity = 1 + random.nextInt(100_000);

        Order order = OrderObjectPool.get();
        order.setAccount(18);
        order.setUser(user);
        order.setOrderId(orderId++);
        order.setType(orderType);
        order.setPriceInt((int) price);
        order.setQuantityLong((int) quantity);
        order.setClOrdId("clOrdId");
        order.setSecurityId(3);

        DecimalFloat priceDecimal = new DecimalFloat(price, 2);
        DecimalFloat quantityDecimal = new DecimalFloat(quantity, 2);
        OrdType ordType = OrdType.LIMIT; // TODO: verify
        Side sideType = side == 1 ? Side.BUY : Side.SELL;
        order.setPrice(price, (short) 2);
        order.setQty(quantity, (short) 2);
        order.setOrdType(ordType);
        order.setSide(sideType);
        // System.out.println("loaded order=" + order);

        list.add(order);
        if (orderId % 100_000 == 0)
          System.out.println("loaded " + orderId);
        if (orderId > limit)
          break;

        Assert.fail(); // Fix the test
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("loaded " + list.size() + " orders");
    return list;
  }

  private static FastArrayList<Order> loadTestOrders2(final String filename) {
    int orderId = 0;
    FastArrayList<Order> list = new FastArrayList<Order>();
    try {
      Path filePath = FileSystems.getDefault().getPath(filename);
      InputStream inputStream = Files.newInputStream(filePath);
      BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
      String line = null;
      while ((line = bufferedReader.readLine()) != null) {
        String[] orderData = line.split(",");
        int side = Integer.parseInt(orderData[5]);
        int orderType = side == 1 ? Constants.BUY_LIMIT : Constants.SELL_LIMIT;
        double price = Double.parseDouble(orderData[3]) * 100;
        double quantity = Double.parseDouble(orderData[4]) * 100;

        Order order = OrderObjectPool.get();
        order.setAccount(1);
        order.setOrderId(orderId++);
        order.setType(orderType);
        order.setPriceInt((int) price);
        order.setQuantityLong((int) price);
        order.setClOrdId("clOrdId");

        DecimalFloat priceDecimal = new DecimalFloat((long) price, 2);
        DecimalFloat quantityDecimal = new DecimalFloat((long) quantity, 0);
        OrdType ordType = OrdType.MARKET; // TODO: Verify
        Side sideType = side == 1 ? Side.SELL : Side.BUY;
        order.setOrdType(ordType);
        order.setSide(sideType);

        list.add(order);
        if (orderId % 100_000 == 0)
          System.out.println("loaded " + orderId);
        if (orderId > 1_000_000)
          break;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("loaded " + list.size() + " orders");
    return list;
  }

  public static final class TestProducer implements Runnable {
    final String customerId = "2";
    char[] clorId = "clOrdId".toCharArray();
    long orderId = 0;
    OrderBook orderBook;
    FastArrayList<Order> list = new FastArrayList<Order>(10000);

    public TestProducer(final OrderBook orderBook) {
      this.orderBook = orderBook;
      for (int i = 0; i < 500000; i++) {
        Random rand = new Random();
        int current = rand.nextInt(ArrayOrderBook.DEFAULT_ARR_SIZE);
        int quantity = rand.nextInt(1000) + 1;

        Order order = OrderObjectPool.get();
        order.setOrderId(orderId++);
        order.setType(Constants.BUY_LIMIT);
        order.setPriceInt(current - 1);
        order.setQuantityLong(quantity);
        order.setClOrdId("clOrdId");
        list.add(order);

        rand = new Random();
        current = rand.nextInt(ArrayOrderBook.DEFAULT_ARR_SIZE);
        quantity = rand.nextInt(1000) + 1;

        Order order2 = OrderObjectPool.get();
        order2.setOrderId(orderId++);
        order2.setType(Constants.SELL_LIMIT);
        order2.setPriceInt(Math.max(current - 10, 30));
        order2.setQuantityLong(quantity);
        order2.setClOrdId("clOrdId");
        list.add(order);
      }
    }

    TestProducer(OrderBook orderBook, FastArrayList<Order> list) {
      this.orderBook = orderBook;
      this.list = list;
    }

    @Override
    public void run() {
      for (Order order : list)
        orderBook.addOrder(order);
    }
  }

  private void testSendOrders() {
    // create test data
    // saveTestOrders("C:\\testOrderData\\orders_1.data");
    // ArrayList<OrderLinkNode7> orderList = loadTestOrders("C:\\testOrderData\\orders_1.data");
    System.out.println("loading orders from loadTestOrders3 " + testOrdersPath);
    FastArrayList<Order> orderList = loadTestOrders3(1_000_000); // (testOrdersPath);
    System.out.println("loaded orders " + orderList.size());

    FastArrayList<Order> orderList2 = loadTestOrders3(1_0_000); // (testOrdersPath);
    System.out.println("loaded orders2 " + orderList.size());

    final Instrument base = new Instrument(1, "BTC", "BTC", (short) 6, (short) 6, 3500, 1000, 0,false, 1, Sector.NOT_DEFINED);
    final Instrument quoted = new Instrument(2, "USD", "USD", (short) 6, (short) 6, 1, 1000, 0,false, 2, Sector.NOT_DEFINED);
    // InstrumentCache.addInstrument(base);
    // InstrumentCache.addInstrument(quoted);

    InstrumentPair instrumentPair =
        new InstrumentPair(3, "BTC/USD", "BTC/USD", base, quoted, (short) 6, (short) 6, 0, AssetType.PAIR, 10, 20, 3500, 0, Sector.NOT_DEFINED);
    // InstrumentCache.addPair(instrumentPair);

    int DEFAULT_ARR_SIZE = 10_000_000;
    int DEFAULT_CACHE_DEPTH = 64;

    ArrayOrderBook orderBook = (ArrayOrderBook) OrderBookFactory.create(OrderBookFactory.ARRAY_ORDER_BOOK,
        OrderBookFactory.NO_PREORDER_CHECK, instrumentPair, DEFAULT_ARR_SIZE, DEFAULT_CACHE_DEPTH);
    orderBook.setSettleCoinUsdMarkInstrument(quoted);
    Thread producer = new Thread(new TestProducer(orderBook, orderList));

    ArrayOrderBook orderBook2 = (ArrayOrderBook) OrderBookFactory.create(OrderBookFactory.ARRAY_ORDER_BOOK,
        OrderBookFactory.NO_PREORDER_CHECK, instrumentPair, DEFAULT_ARR_SIZE, DEFAULT_CACHE_DEPTH);
    orderBook2.setSettleCoinUsdMarkInstrument(quoted);
    Thread producer2 = new Thread(new TestProducer(orderBook2, orderList2));

    System.out.println("starting producer...");
    long start = System.nanoTime();

    producer.start();

    try {
      producer.join();
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("done------" + orderBook.getFilledCount() + " filled " + orderBook.getOrderCount() + " orders ");
    System.out.println("done in " + ((System.nanoTime() - start)) / 1000000000F);

    // consumer.active=false;


    System.out.println("starting producer...");
    start = System.nanoTime();

    producer2.start();
    try {
      producer2.join();
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("done------" + orderBook2.getFilledCount() + " filled " + orderBook2.getOrderCount() + " orders ");
    System.out.println("done in " + ((System.nanoTime() - start)) / 1000000000F);
  }


  public static void main(String[] args) {
    if (args.length == 2) {
      testOrdersPath = args[0];
      testOutputPath = args[1];
    }
    ArrayOrderBookTest2 arrayOrderBookTest = new ArrayOrderBookTest2();

    System.out.println("loading object pools");
    BusinessRejectObjectPool.init();
    CancelRejectObjectPool.init();
    ExecutionReportObjectPool.init();
    System.out.println("calling testSendOrders");

    arrayOrderBookTest.testSendOrders();
  }

}
