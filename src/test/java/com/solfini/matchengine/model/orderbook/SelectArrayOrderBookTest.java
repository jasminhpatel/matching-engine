package com.solfini.matchengine.model.orderbook;

import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.Sector;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.internal.OrderFilter;
import com.solfini.matchengine.orderbook.*;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.util.FastArrayList;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;

import java.text.NumberFormat;
import java.util.Properties;

import static com.solfini.common.Constants.*;
import static com.solfini.common.Constants.STOP_SELL_LIMIT;

public class SelectArrayOrderBookTest {
  private Instrument base;
  private Instrument quoted;
  private InstrumentPair instrumentPair;
  private SelectArrayOrderBook orderBook;

  final int DEFAULT_ARR_SIZE = 10_000;
  final int DEFAULT_CACHE_DEPTH = 4;
  final FastArrayList<Message> list = new FastArrayList<>(4096);

  private long assetId1 = 1;
  private long assetId2 = 2;
  private int clOrdIdCounter = 0;

  private User seller = new User(1);
  private User buyer = new User(2);

  private long orderId = 0;

  @Before
  public void init() {
    initProperty();

    quoted = new Instrument(1, "USD", "USD", (short) 2, (short) 2, 1, 1000, 0,false, 1, Sector.NOT_DEFINED);
    base = new Instrument(2, "CARBON", "CARBON", (short) 2, (short) 0, 1, 1000, 0,false, 2, Sector.NOT_DEFINED);
    instrumentPair = new InstrumentPair(3, "CARBON/USD", "CARBON/USD", base, quoted, (short) 2, (short) 0, 0, AssetType.PAIR, 10, 20, 3500, 0, Sector.NOT_DEFINED);

    orderBook = (SelectArrayOrderBook) OrderBookFactory.create(OrderBookFactory.SELECT_ARRAY_ORDER_BOOK, OrderBookFactory.CASH_PREORDER_CHECK, instrumentPair,
        DEFAULT_ARR_SIZE, DEFAULT_CACHE_DEPTH);
    orderBook.setSettleCoinUsdMarkInstrument(quoted);

    InstrumentCache.addInstrument(quoted);
    InstrumentCache.addInstrument(base);
    InstrumentCache.addPair(instrumentPair);

    buyer.addPosition(quoted.getId(), 100_000, null, 0, TokenType.ERC20);
    seller.addPosition(base.getId(), 100, null, 1, TokenType.ERC20_GROUP);

  }

  @Test
  public void testMarketOrderOnEmptyBook() {
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.MARKET, 0, 5, 0);
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(buy1.getFilterId());
    orderFilter.setOrderIdGroup(new long[] {1});
    orderFilter.setPriceIdGroup(new int[] {1});
    OrderFilterCache.onModel(orderFilter);
    orderBook.addOrder(buy1);
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
  }

  @Test
  public void testMarketOrderPartialMatchFirstOrder() {
    createSellOrders();
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.MARKET, 0, 3, 0);
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(buy1.getFilterId());
    orderFilter.setOrderIdGroup(new long[] {1});
    orderFilter.setPriceIdGroup(new int[] {1});
    OrderFilterCache.onModel(orderFilter);
    orderBook.addOrder(buy1);
    //buy market order gets cancelled
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
    //order 1 partially filled
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(1, 7));
  }

  @Test
  public void testMarketOrderPartialMatchSecondOrder() {
    createSellOrders();
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.MARKET, 0, 3, 0);
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(buy1.getFilterId());
    orderFilter.setOrderIdGroup(new long[] {2});
    orderFilter.setPriceIdGroup(new int[] {2});
    OrderFilterCache.onModel(orderFilter);
    orderBook.addOrder(buy1);
    //buy market order gets cancelled
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
    //order 1 not matched because of filters
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(1, 10));
    //order 1 partially filled
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(2, 7));
  }

  @Test
  public void testMarketOrderMatchEvenOrderIds() {
    createSellOrders();
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.MARKET, 0, 50, 0);
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(buy1.getFilterId());
    orderFilter.setOrderIdGroup(new long[] {2,4,6,8,10});
    orderFilter.setPriceIdGroup(new int[] {2,2,6,8,10});
    OrderFilterCache.onModel(orderFilter);
    orderBook.addOrder(buy1);
    //buy market order gets filled
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(1, 10));
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(2, 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(3, 10));
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(4, 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(5, 10));
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(6, 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(7, 10));
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(8, 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(9, 10));
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(10, 0));
  }

  @Test
  public void testLimitOrderOnEmptyBook() {
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.LIMIT, 1, 5, 0);
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(buy1.getFilterId());
    orderFilter.setOrderIdGroup(new long[] {1});
    orderFilter.setPriceIdGroup(new int[] {1});
    OrderFilterCache.onModel(orderFilter);
    orderBook.addOrder(buy1);
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
  }

  @Test
  public void testLimitOrderNoMatchCrossed() {
    createSellOrders2();
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.LIMIT, 10, 15, 0);
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(buy1.getFilterId());
    orderFilter.setOrderIdGroup(new long[] {2});//adjust filter to prevent matching
    orderFilter.setPriceIdGroup(new int[] {20});
    OrderFilterCache.onModel(orderFilter);
    orderBook.addOrder(buy1);
    //cancel because crossed
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
    //order 1 partially filled
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(1, 10));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(2, 10));
  }

  @Test
  public void testLimitOrderNoMatchNotCrossed() {
    createSellOrders2();
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.LIMIT, 9, 15, 0);
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(buy1.getFilterId());
    orderFilter.setOrderIdGroup(new long[] {2});//adjust filter to prevent matching
    orderFilter.setPriceIdGroup(new int[] {20});
    OrderFilterCache.onModel(orderFilter);
    orderBook.addOrder(buy1);
    //rest on the order book because not crossed
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
    //order 1 partially filled
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(1, 10));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(2, 10));
  }

  @Test
  public void testLimitOrderPartialMatchFirstOrderNotCrossed() {
    createSellOrders2();
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.LIMIT, 10, 15, 0);
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(buy1.getFilterId());
    orderFilter.setOrderIdGroup(new long[] {1});
    orderFilter.setPriceIdGroup(new int[] {10});
    OrderFilterCache.onModel(orderFilter);
    orderBook.addOrder(buy1);
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
    //order 1 partially filled
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(1, 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(2, 10));
  }

  @Test
  public void testLimitOrderPartialMatchFirstOrderCrossed() {
    createSellOrders2();
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.LIMIT, 21, 15, 0);
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(buy1.getFilterId());
    orderFilter.setOrderIdGroup(new long[] {1});
    orderFilter.setPriceIdGroup(new int[] {10});
    OrderFilterCache.onModel(orderFilter);
    orderBook.addOrder(buy1);
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
    //order 1 partially filled
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(1, 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(2, 10));
  }

  @Test
  public void testLimitOrderPartialMatchSecondOrderCrossed() {
    createSellOrders2();
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.LIMIT, 20, 3, 0);
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(buy1.getFilterId());
    orderFilter.setOrderIdGroup(new long[] {2});
    orderFilter.setPriceIdGroup(new int[] {20});
    OrderFilterCache.onModel(orderFilter);
    orderBook.addOrder(buy1);
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
    //order 1 not matched because of filters
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(1, 10));
    //order 1 partially filled
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(2, 7));
  }

  @Test
  public void testLimitOrderMatchEvenOrderIds() {
    createSellOrders();
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.LIMIT, 10, 50, 0);
    OrderFilter orderFilter = new OrderFilter();
    orderFilter.setFilterId(buy1.getFilterId());
    orderFilter.setOrderIdGroup(new long[] {2,4,6,8,10});
    orderFilter.setPriceIdGroup(new int[] {2,2,6,8,10});
    OrderFilterCache.onModel(orderFilter);
    orderBook.addOrder(buy1);

    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(1, 10));
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(2, 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(3, 10));
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(4, 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(5, 10));
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(6, 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(7, 10));
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(8, 0));
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(9, 10));
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(10, 0));
  }


  private boolean checkIfOrderExistsOnTheOrderBook(long orderId, long remainingQty) {
    OrderBookPriceLevel[] priceLevels = orderBook.getBookArr();
    for (OrderBookPriceLevel priceLevel : priceLevels) {
      Order orderPtr = priceLevel.getHead();
      if (orderPtr == null) continue;

      while (orderPtr != null) {
        System.out.println("Order id: " + orderPtr.getOrderId() + " price: " + orderPtr.getPriceInt() + " qty: " + orderPtr.getQuantityLong());
        if ((orderPtr.getOrderId() == orderId)) {
          System.out.println("Order Exists:");
          return remainingQty == 0 | remainingQty == orderPtr.getQuantityLong();
        }
        orderPtr = orderPtr.getNext();
      }
    }
    return false;
  }

  private void createSellOrders() {
    Order sell1 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 1, 10, 1);
    Order sell2 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 2, 10, 1);
    Order sell3 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 2, 10, 1);
    Order sell4 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 2, 10, 1);
    Order sell5 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 5, 10, 1);
    Order sell6 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 6, 10, 1);
    Order sell7 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 7, 10, 1);
    Order sell8 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 8, 10, 1);
    Order sell9 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 9, 10, 1);
    Order sell10 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 10, 10, 1);
    orderBook.addOrder(sell1);
    orderBook.addOrder(sell2);
    orderBook.addOrder(sell3);
    orderBook.addOrder(sell4);
    orderBook.addOrder(sell5);
    orderBook.addOrder(sell6);
    orderBook.addOrder(sell7);
    orderBook.addOrder(sell8);
    orderBook.addOrder(sell9);
    orderBook.addOrder(sell10);
  }

  private void createSellOrders2() {
    Order sell1 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 10, 10, 1);
    Order sell2 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 20, 10, 1);
    orderBook.addOrder(sell1);
    orderBook.addOrder(sell2);
  }

  private Order makeOrder(User user, Side side, OrdType orderType, long price, long qty, long assetId) {
    Order order = OrderObjectPool.get();
    order.setAccount(user.getId());
    order.setUser(user);
    order.setOrderId(++orderId);
    order.setPriceInt((int) price);
    order.setQuantityLong(qty);
    order.setQuantityOrigLong(qty);
    order.setClOrdId(("ClOrdId" + ++clOrdIdCounter));
    order.setSecurityId(3);

    order.setPrice(price, (short) 2);
    order.setQty(qty, (short) 2);
    order.setOrdType(orderType);
    order.setSide(side);
    order.setAssetId(assetId);

    final OrdType ordType = order.getOrdType();
    //final Side side = order.getSide();

    if (Side.BUY == side) {
      if (OrdType.LIMIT == ordType)
        order.setType(BUY_LIMIT);
      else if (OrdType.MARKET == ordType)
        order.setType(BUY_MARKET);
      else if (OrdType.STOP_LIMIT == ordType || OrdType.STOP == ordType) {
        order.setType(STOP_BUY_LIMIT);
        //order = parseStop(order);
      }
    } else if (Side.SELL == side) {
      if (OrdType.LIMIT == ordType)
        order.setType(SELL_LIMIT);
      else if (OrdType.MARKET == ordType)
        order.setType(SELL_MARKET);
      else if (OrdType.STOP_LIMIT == ordType || OrdType.STOP == ordType) {
        order.setType(STOP_SELL_LIMIT);
        //order = parseStop(order);
      }
    }

    order.setFilterId(orderId);
    order.setTargetStrategy(FILTERED);
    return order;
  }

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
      properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "100");

      PropertyReader.initialize(null, properties);

      System.out.println("Warming up object pools");
      OrderObjectPool.init();
      ExecutionReportObjectPool.init();
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }
}
