package com.solfini.matchengine.model.risk;

import static com.solfini.common.Constants.BUY_LIMIT;
import static com.solfini.common.Constants.BUY_MARKET;
import static com.solfini.common.Constants.SELL_LIMIT;
import static com.solfini.common.Constants.SELL_MARKET;
import static com.solfini.common.Constants.STOP_BUY_LIMIT;
import static com.solfini.common.Constants.STOP_SELL_LIMIT;

import java.text.NumberFormat;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;

import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import uk.co.real_logic.artio.fields.DecimalFloat;

/**
 * Created by robin.wu on 2019-07-24.
 */
public class MarginTest {

  private Instrument base;
  private Instrument quote;
  private InstrumentPair pair;
  private OrderBook orderBook;
  private PreOrderCheck preOrderCheck;
  private long orderId = 0;
  private char[] clientOrderId = "clOrdId".toCharArray();
  private int account1 = 1;
  private int account2 = 2;
  private int clOrdIdCounter = 0;

  private User user1 = new User(1);
  private User user2 = new User(2);

  private double useMark = 10000D;

  private void initProp() {
    LogLevel.setLevel(Level.TRACE);
    NumberFormat.getInstance().setGroupingUsed(true);

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "10");
      properties.setProperty("ORDER_POOL_START_CAPACITY", "10");
      properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "10");
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
    initProp();

    quote = new Instrument(1, "USDT", "USDT", (short) 2, (short) 8, 1, 1000);
    base = new Instrument(2, "BTC", "BTC", (short) 2, (short) 8, 1, 1000);

    pair = new InstrumentPair(3, "BTCUSDT", "BTCUSDT", base, quote, (short) 2, (short) 3, 1, AssetType.PAIR, 250, 500, 9880, 0);

    preOrderCheck = new MarginPreOrderCheckAndSettle();
    orderBook = new ArrayOrderBook(pair, preOrderCheck, OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK);
    pair.setOrderBook(orderBook);
    InstrumentCache.addInstrument(quote);
    InstrumentCache.addInstrument(base);
    InstrumentCache.addPair(pair);

    Position position1 = user1.setPosition(1, 999999_00000000L);
    Position position2 = user2.setPosition(1, 160_000_000_000L);
  }

  @Test
  public void openOrderCountAfterOrderEntry() {
    pair.setIndexFeedUsdMark(useMark);

    Order order1 = makeOrder(user1, account1, OrdType.LIMIT, 980012, 0, 10123, Side.BUY);
    System.out.println("BEFORE: user=" + printUserRisk(user1));
    orderBook.addOrder(order1);
    System.out.println("AFTER: user=" + printUserRisk(user1));
    Assert.assertEquals(1, user1.getOpenOrderCount());

    Order order2 = makeOrder(user1, account1, OrdType.LIMIT, 980012, 0, 10123, Side.BUY);
    System.out.println("BEFORE: user=" + printUserRisk(user1));
    orderBook.addOrder(order2);
    System.out.println("AFTER: user=" + printUserRisk(user1));
    Assert.assertEquals(2, user1.getOpenOrderCount());
  }

  @Test
  public void openOrderCountAfterOrderEntry2() {
    pair.setIndexFeedUsdMark(useMark);

    Order order1 = makeOrder(user1, account1, OrdType.LIMIT, 980012, 0, 10123 * 2, Side.BUY);
    System.out.println("BEFORE: user=" + printUserRisk(user1));
    orderBook.addOrder(order1);
    Assert.assertEquals(1, user1.getOpenOrderCount());
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

  private String printUserRisk(User u) {
    StringBuilder sb = new StringBuilder();
    sb.append("{usdValue: ").append(u.getUsdValue()).append(", ");
    sb.append("usdNotionalPositionValue: ").append(u.getUsdNotionalPositionValue()).append(", ");
    sb.append("usdMarginMaintValue: ").append(u.getUsdMarginMaintValue()).append(", ");
    sb.append("usdMarginRequiredValue: ").append(u.getUsdMarginRequiredValue()).append(", ");
    sb.append("leverageRatio: ").append(u.getLeverageRatio()).append(", ");
    sb.append("usdUnrealized: ").append(u.getUsdUnrealized()).append(", ");
    sb.append("marginRatio: ").append(u.getMarginRatio()).append(", ");
    sb.append("usdOpenOrdersRequiredValue: ").append(u.getUsdOpenOrdersRequiredValue()).append(", ");
    sb.append("usdMaxExposurePositionAndOpenOrdersValue: ").append(u.getUsdMaxExposurePositionAndOpenOrdersValue()).append("}");

    return sb.toString();
  }
}
