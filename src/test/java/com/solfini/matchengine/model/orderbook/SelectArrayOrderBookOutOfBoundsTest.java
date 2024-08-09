package com.solfini.matchengine.model.orderbook;

import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.Sector;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.matchengine.orderbook.OrderBookPriceLevel;
import com.solfini.matchengine.orderbook.OutOfBoundsContainer;
import com.solfini.matchengine.orderbook.SelectArrayOrderBook;
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

public class SelectArrayOrderBookOutOfBoundsTest {
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

    buyer.addPosition(quoted.getId(), 200_000_000, null, 0, TokenType.ERC20);
    seller.addPosition(base.getId(), 100, null, 1, TokenType.ERC20_GROUP);

  }

  @Test
  public void testOutOfBoundsSellOrder() {
    Order sell1 = makeOrder(seller, Side.SELL, OrdType.LIMIT, 100_000_000, 10, 1);
    orderBook.addOrder(sell1);
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(sell1.getOrderId(), 10));
    CancelOrder cancelOrder = new CancelOrder();
    cancelOrder.set(sell1, orderId++, 1);
    orderBook.cancelOrder(cancelOrder);
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(sell1.getOrderId(), 0));
    System.out.println("Done");
  }

  @Test
  public void testOutOfBoundsBuyOrder() {
    Order buy1 = makeOrder(buyer, Side.BUY, OrdType.LIMIT, 100_000_000, 1, 0);
    orderBook.addOrder(buy1);
    Assert.assertTrue(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 1));
    CancelOrder cancelOrder = new CancelOrder();
    cancelOrder.set(buy1, orderId++, 1);
    orderBook.cancelOrder(cancelOrder);
    Assert.assertFalse(checkIfOrderExistsOnTheOrderBook(buy1.getOrderId(), 0));
    System.out.println("Done");
  }

  private Order makeOrder(User user, Side side, OrdType orderType, long price, long qty, long assetId) {
    Order order = OrderObjectPool.get();
    order.setAccount(user.getId());
    order.setUser(user);
    order.setOrderId(++orderId);
    order.setSecondaryOrderId(orderId);
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
    OutOfBoundsContainer outOfBoundsContainer = orderBook.getOutOfBoundsContainer();
    Order order = outOfBoundsContainer.get(orderId);
    if (order != null) {
      return remainingQty == 0 | remainingQty == order.getQuantityLong();
    }
    return false;
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
