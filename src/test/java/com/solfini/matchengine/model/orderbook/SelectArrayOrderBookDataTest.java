package com.solfini.matchengine.model.orderbook;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.util.FastArrayList;
import com.solfini.util.LogLevel;
import com.solfini.util.MbxMath;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

import static com.solfini.common.Constants.BUY_LIMIT;
import static com.solfini.common.Constants.BUY_MARKET;
import static com.solfini.common.Constants.BUY_SELECT;
import static com.solfini.common.Constants.SELL_LIMIT;
import static com.solfini.common.Constants.SELL_MARKET;
import static com.solfini.common.Constants.SELL_SELECT;
import static com.solfini.common.Constants.STOP_BUY_LIMIT;
import static com.solfini.common.Constants.STOP_SELL_LIMIT;
import static com.solfini.instrument.Position.assetIdComparator;

/**
 * Created by robin.wu on 2019-07-18.
 */
public class SelectArrayOrderBookDataTest {

  private Instrument base;
  private Instrument quoted;
  private InstrumentPair instrumentPair;
  private OrderBook orderBook;

  final int DEFAULT_ARR_SIZE = 10_000_000;
  final int DEFAULT_CACHE_DEPTH = 64;
  final FastArrayList<Message> list = new FastArrayList<>(4096);


  final char[] clientOrderId = "clOrdId".toCharArray();
  private int account1 = 1;
  private int account2 = 2;
  private int clOrdIdCounter = 0;

  private User user1 = new User(1);
  private User user2 = new User(2);

  private long orderId = 0;

  private CountDownLatch latch;


  private void initProperty() {
    LogLevel.setLevel(Level.TRACE);
    NumberFormat.getInstance().setGroupingUsed(true);

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "2");
      properties.setProperty("ORDER_POOL_START_CAPACITY", "5");
      properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "2");
      properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "10");
      properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "1024");
      properties.setProperty("POSITION_POOL_START_CAPACITY", "1024");
      PropertyReader.initialize(null, properties);

      System.out.println("Warming up object pools");
      OrderObjectPool.init();
      ExecutionReportObjectPool.init();
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Before
  public void init() {
    initProperty();

    quoted = new Instrument(0, "USD", "USD", (short) 6, (short) 6, 1, 0);
    base = new Instrument(228, "CARBON", "CARBON", (short) 6, (short) 0, 1, 0);
    instrumentPair = new InstrumentPair(229, "CARBON/USD", "CARBON/USD", base, quoted, (short) 2, (short) 0, 0, AssetType.PAIR, 0, 0, 1, 0);

    orderBook = OrderBookFactory.create(OrderBookFactory.SELECT_ARRAY_ORDER_BOOK, OrderBookFactory.CASH_PREORDER_CHECK, instrumentPair,
        DEFAULT_ARR_SIZE, DEFAULT_CACHE_DEPTH);
    orderBook.setSettleCoinUsdMarkInstrument(quoted);

    InstrumentCache.addInstrument(quoted);
    InstrumentCache.addInstrument(base);
    InstrumentCache.addPair(instrumentPair);


    user1.addPosition(0, 1000000000, null);
    user2.addPosition(0, 1000000000, null);

    TreeSet assetIdTreeSet = new TreeSet<>(assetIdComparator);
    assetIdTreeSet.add(new long[] {101, 1});

    user2.addPosition(228, 1, assetIdTreeSet);//deposit assetId 101, tokenId 1

    assetIdTreeSet = new TreeSet<>(assetIdComparator);
    assetIdTreeSet.add(new long[] {101, 2});

    user2.addPosition(228, 1, assetIdTreeSet);//deposit assetId 101, tokenId 2

    assetIdTreeSet = new TreeSet<>(assetIdComparator);
    assetIdTreeSet.add(new long[] {102, 5});

    user2.addPosition(228, 1, assetIdTreeSet);//deposit assetId 102, tokenId 5
  }


  private void placeOrders() {
    //sell assetId 101, tokenId 1
    Order sell = makeOrder(user2, account2, OrdType.LIMIT, 10000, 0, 1, Side.SELL, 101, 1, 0);
    orderBook.addOrder(sell);
    //sell assetId 102, tokenId 5
    Order sell2 = makeOrder(user2, account2, OrdType.LIMIT, 10200, 0, 1, Side.SELL, 102, 5, 0);
    orderBook.addOrder(sell2);
    //buy assetId 101, tokenId 1
    Order buy = makeOrder(user1, account1, OrdType.SELECT, 10000, 0, 1, Side.BUY,101, 1, sell.getOrderId());
    orderBook.addOrder(buy);
    //buy assetId 102, tokenId 5
    Order buy2 = makeOrder(user1, account1, OrdType.SELECT, 10200, 0, 1, Side.BUY,102, 5, sell2.getOrderId());
    orderBook.addOrder(buy2);

  }

  @Test
  public void testOrderMatch () throws InterruptedException {
    placeOrders();
    Thread.sleep(1000);
    int iterations = 0;
    int count = 1;
    while (iterations < 3) {
      count = Context.getMatcherToPublisherQueue().drainTo(list, 4096);

      for (int i = 0; i < count; i++) {
        Message message = list.get(i);
        System.out.println(message.toJSON());
      }
      iterations++;

      Thread.sleep(1000);
    }
  }

  @Test
  public void testAvgPrice() throws InterruptedException {
    placeOrders();

    Map<Long, Long> quoteMap = new HashMap<>();
    int count = 1;
    while (count > 0) {
      count = Context.getMatcherToPublisherQueue().drainTo(list, 4096);

      for (int i = 0; i < count; i++) {
        Message message = list.get(i);

        if (message instanceof ExecutionReportMessage) {
          System.out.println(message.toJSON());

          ExecutionReportMessage executionReport = (ExecutionReportMessage) message;
          if (executionReport.getExecType() == ExecType.TRADE) {
            long tmpCumQuote = executionReport.getLastPx() * executionReport.getLastQty();
            quoteMap.put(executionReport.getOrderId(), quoteMap.getOrDefault(executionReport.getOrderId(), 0L) + tmpCumQuote);

            long cumQty = executionReport.getCumQty();
            long cumQuote = quoteMap.get(executionReport.getOrderId());

            double avgPrice = cumQty == 0 ? 0d : Double.parseDouble(String.valueOf(cumQuote)) / cumQty;

            System.out.println(String.format("orderId:%s, cumQty: %s, cumQuote: %s, avgPrice: %s", executionReport.getOrderId(), cumQty,
                cumQuote, avgPrice));


          }
        }
      }

      Thread.sleep(1000);
    }
  }


  @Test
  public void testRealizedPnl() {
    placeOrders();

    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

  }


  private Order makeOrder(User user, int account, OrdType orderType, long price, long stopPrice, long qty, Side sideType,
      long assetId, int tokenId, long selectId) {
    Order order = OrderObjectPool.get();
    order.setAccount(account);
    order.setUser(user);
    order.setOrderId(++orderId);
    order.setPriceInt((int) price);
    order.setQuantityLong(qty);
    order.setQuantityOrigLong(qty);
    order.setClOrdId(("ClOrdId" + ++clOrdIdCounter));
    order.setSecurityId(229);

    order.setPrice(price, (short) 2);
    order.setQty(qty, (short) 0);
    order.setOrdType(orderType);
    order.setSide(sideType);

    order.setAssetId(assetId);
    order.setTokenId(tokenId);
    order.setSelectId(selectId);

    final OrdType ordType = order.getOrdType();
    final Side side = order.getSide();

    if (Side.BUY == side) {
      if (OrdType.LIMIT == ordType)
        order.setType(BUY_LIMIT);
      else if (OrdType.MARKET == ordType)
        order.setType(BUY_MARKET);
      else if (OrdType.STOP_LIMIT == ordType || OrdType.STOP == ordType) {
        order.setType(STOP_BUY_LIMIT);
        order = parseStop(order);
      } else if (order.getOrdType() == OrdType.SELECT) {
        order.setType(BUY_SELECT);
      }
    } else if (Side.SELL == side) {
      if (OrdType.LIMIT == ordType)
        order.setType(SELL_LIMIT);
      else if (OrdType.MARKET == ordType)
        order.setType(SELL_MARKET);
      else if (OrdType.STOP_LIMIT == ordType || OrdType.STOP == ordType) {
        order.setType(STOP_SELL_LIMIT);
        order = parseStop(order);
      } else if (order.getOrdType() == OrdType.SELECT) {
        order.setType(SELL_SELECT);
      }
    }

    return order;
  }

  private Order parseStop(Order order) {
    long stopPriceLong = order.getStopPx();

    // swap prices
    order.setStopPxInt(order.getPriceInt());
    order.setPriceInt((int) stopPriceLong);
    return order;
  }


  @Test
  public void testDivide() {
    double d1 = 10L / 3L;
    double d2 = 10D / 3L;
    double d3 = MbxMath.divide(10L, 3L, 100_000_000);

    System.out.println(d1);
    System.out.println(d2);
    System.out.println(d3);
  }
}
