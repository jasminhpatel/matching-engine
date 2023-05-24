package com.solfini.matchengine.model.orderbook;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.util.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;
import uk.co.real_logic.artio.*;
import uk.co.real_logic.artio.fields.DecimalFloat;
import static com.solfini.common.Constants.*;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Created by robin.wu on 2019-07-18.
 */
public class ArrayOrderBookDataTest {

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

    quoted = new Instrument(1, "USDT", "USDT", (short) 6, (short) 6, 3500, 1000, 0,false, 1);
    base = new Instrument(2, "BTC", "BTC", (short) 6, (short) 6, 1, 1000, 0,false, 2);
    instrumentPair = new InstrumentPair(3, "BTC/USDT", "BTC/USDT", base, quoted, (short) 2, (short) 2, 0, AssetType.PAIR, 10, 20, 3500, 0);

    orderBook = OrderBookFactory.create(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, instrumentPair,
        DEFAULT_ARR_SIZE, DEFAULT_CACHE_DEPTH);
    orderBook.setSettleCoinUsdMarkInstrument(quoted);

    InstrumentCache.addInstrument(quoted);
    InstrumentCache.addInstrument(base);
    InstrumentCache.addPair(instrumentPair);


    user1.addPosition(1, 1000000000, null, 0, TokenType.ERC20);
    user2.addPosition(1, 1000000000, null, 0, TokenType.ERC20);

    user2.addPosition(3, 20, null, 0, TokenType.ERC20);
  }


  private void placeOrders() {

    Order order1 = makeOrder(user1, account1, OrdType.LIMIT, 1058098, 0, 862, Side.BUY);
    orderBook.addOrder(order1);

    order1 = makeOrder(user1, account1, OrdType.LIMIT, 1057040, 0, 614, Side.BUY);
    orderBook.addOrder(order1);


    Order order2 = makeOrder(user2, account2, OrdType.MARKET, 0, 0, 700, Side.SELL);
    orderBook.addOrder(order2);

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


  private Order makeOrder(User user, int account, OrdType orderType, long price, long stopPrice, long qty, Side sideType) {
    Order order = OrderObjectPool.get();
    order.setAccount(account);
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
    order.setSide(sideType);

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
      }
    } else if (Side.SELL == side) {
      if (OrdType.LIMIT == ordType)
        order.setType(SELL_LIMIT);
      else if (OrdType.MARKET == ordType)
        order.setType(SELL_MARKET);
      else if (OrdType.STOP_LIMIT == ordType || OrdType.STOP == ordType) {
        order.setType(STOP_SELL_LIMIT);
        order = parseStop(order);
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
