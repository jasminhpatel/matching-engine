package com.solfini.matchengine.model.orderbook;

import java.util.Random;
import org.junit.Assert;

import com.solfini.common.Constants;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBookPriceLevel;
import com.solfini.pool.OrderObjectPool;
import com.solfini.user.User;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;

public class TestPriceLevel {

  public void testPriceLevel() {
    createInstruments();
    OrderBookPriceLevel priceLevel = new OrderBookPriceLevel();
    User user = new User(18);
    Order order1 = makeOrder(user, InstrumentCache.getPair(3));
    Order order2 = makeOrder(user, InstrumentCache.getPair(3));
    Order order3 = makeOrder(user, InstrumentCache.getPair(3));

    System.out.println("priceLevel0=" + priceLevel);
    priceLevel.setHead(order1);
    System.out.println("priceLevel1=" + priceLevel);

    priceLevel.addToTail(order2);
    System.out.println("priceLevel2=" + priceLevel);

    priceLevel.addToTail(order3);
    System.out.println("priceLevel3=" + priceLevel);

    priceLevel.remove(order3);
    System.out.println("priceLevel4=" + priceLevel);


    // new order
    // System.out.println("test7 accepted=" + accepted + ", userOpenOrdersByPair=" + userOpenOrdersByPair);

  }

  protected static void createInstruments() {
    final Instrument base = new Instrument(1, "BTC", "BTC", (short) 6, (short) 6, 3500, 1000);
    final Instrument quoted = new Instrument(2, "USD", "USD", (short) 6, (short) 6, 1, 1000);
    InstrumentCache.addInstrument(base);
    InstrumentCache.addInstrument(quoted);

    final InstrumentPair instrument =
        new InstrumentPair(3, "BTC/USD", "BTC/USD", base, quoted, (short) 6, (short) 6, 0, AssetType.PAIR, 10, 20, 3500, 0);
    InstrumentCache.addPair(instrument);
  }

  private static long orderId = 1;

  protected Order makeOrder(final User user, final InstrumentPair instrument) {
    final Random random = new Random();
    int account = 18;
    orderId++;
    try {
      final int orderType = 0; // random.nextInt(2);
      final int side = orderType == Constants.BUY_LIMIT ? 1 : 2;
      final long quantity = 1 + random.nextInt(100_000);

      long price = 0;
      if (orderType == Constants.BUY_LIMIT) {
        price = 5000; // 1 + random.nextInt(1_050_000);
      } else {
        price = 1_000_000 + random.nextInt(1_000_000);
      }

      final Order order = OrderObjectPool.get();
      order.setSenderCompId("test");
      order.setAccount(account);
      order.setUser(user);
      order.setOrderId(orderId);
      order.setOrderPriority(1);
      order.setType(orderType);
      order.setPriceInt((int) price);
      order.setQuantityLong(quantity);
      order.setQuantityOrigLong(quantity);
      order.setSecurityId(instrument.getId());
      order.setClOrdId("clientOrder0");

      final OrdType ordType = OrdType.LIMIT;
      final Side sideType = side == 1 ? Side.BUY : Side.SELL;
      order.setPrice(price, instrument.getPriceScale());
      order.setQty(quantity, instrument.getQuantityScale());
      order.setOrdType(ordType);
      order.setSide(sideType);

      return order;
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    return null;
  }

  public static void main(String[] args) {
    TestPriceLevel testPriceLevel = new TestPriceLevel();
    testPriceLevel.testPriceLevel();
  }

}
