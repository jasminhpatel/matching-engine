package com.solfini.matchengine.model.orderbook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import static com.solfini.sbe.encoder.TimeInForce.*;

public class PositionContractConsistencyTest4 extends OrderBookTest {

  private final int USER_START = 1;
  private final int USER_COUNT = 200;
  private final int ORDER_COUNT = 10_000;
  private final long USDT_BALANCE = 9_000_000;
  private final long USDT_SCALE_MULT = 100;

  @Override
  protected void createUsers() {
    for (int i = 0; i < USER_COUNT; i++) {
      final int userId = USER_START + i;
      final User user = createUser(userId);
      user.addPosition(USDT, USDT_BALANCE * USDT_SCALE_MULT, null);
      expectMessage("userId=" + userId);
    }
  }

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PUT, "BTC/USDT[F]", BTC, USDT, 2, 8));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PUT");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F]");

    pair = InstrumentCache.getPair(BTC_USDT_F);

    orderBook = new OrderBookTest.OrderBookWrapper(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
    (orderBook.orderBook()).setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));

    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(100);
  }

  private void assertPositionSum(final int securityId) {
    assertPositionSum(securityId, 0, 0);
  }

  private void assertPositionSum(final int securityId, final long expected, final long delta) {
    long total = 0;
    for (int i = 0; i < Math.min(UserCache.getCapacity() + 1, UserCache.getCacheSize()); i++) {
      final User user = UserCache.get(i);
      if (user != null && user.isActive()) {
        final Position position = user.getPosition(securityId);
        if (position != null) {
          total += position.getQuantity();
        }
      }
    }

    System.out.println("positions=" + Arrays.toString(getUserPositionsBySecurity(securityId)));
    if (Math.abs(total - expected) > delta) {
      System.out.println("---------------------");
    }

    Assert.assertTrue("Total=" + total + ", Expected=" + expected + ", Diff=" + (total - expected) + ", Delta=" + delta,
        Math.abs(total - expected) <= delta);
  }

  private long[] getUserPositionsBySecurity(final int securityId) {
    int arrLen = Math.min(UserCache.getCapacity() + 1, UserCache.getCacheSize());
    long[] arr = new long[arrLen];
    for (int i = 0; i < arrLen; i++) {
      final User user = UserCache.get(i);
      if (user != null && user.isActive()) {
        final Position position = user.getPosition(securityId);
        if (position != null) {
          arr[i] = position.getQuantity();
        }
      }
    }
    return arr;
  }

  private void drain() {
    ArrayList<Message> messages = new ArrayList<>();
    Context.getMatcherToPublisherQueue().drainTo(messages, 1_000);
    for (Message message : messages) {
      System.out.println(">> " + message);
    }
  }


  @Test
  public void contractPositionConsistency2() {
    assertPositionSum(BTC_USDT_F);

    Random random = new Random();
    List<Order> cancelList = new ArrayList<>();
    for (int i = 0; i < ORDER_COUNT; i++) {
      final User user = UserCache.get(USER_START + random.nextInt(USER_COUNT));
      final long price = 10000 + random.nextInt(5000);
      final long stopPrice = 10000 + random.nextInt(5000);
      final long quantity = 100 + random.nextInt(400);

      final Side side = (random.nextInt(Math.abs((int) System.nanoTime())) % 2 == 0) ? Side.BUY : Side.SELL;
      TimeInForce timeInForce;
      switch (random.nextInt(Math.abs((int) System.nanoTime())) % 4) {
        case 0:
          timeInForce = DAY;
          break;
        case 1:
          timeInForce = FILL_OR_KILL;
          break;
        case 2:
          timeInForce = IMMEDIATE_OR_CANCEL;
          break;
        case 3:
          timeInForce = GOOD_TILL_CANCEL; // POST_ONLY;
          break;
        default:
          timeInForce = GOOD_TILL_CANCEL;
          break;
      }
      // timeInForce = GOOD_TILL_CANCEL; // TODO
      Order order = null;
      switch (random.nextInt(Math.abs((int) System.nanoTime())) % 4) {
        case 0:
          order = createMarketOrder(i, user, BTC_USDT_F, quantity, side, DAY);
          break;
        case 1:
          order = createStopLimitOrder(i, user, BTC_USDT_F, price, stopPrice, quantity, side, timeInForce);
          cancelList.add(createStopLimitOrder(i, user, BTC_USDT_F, price, stopPrice, quantity, side, timeInForce)); // copy data
          break;
        case 2:
          if (cancelList.size() > 10) {
            Order toCancel = cancelList.get(Math.abs(random.nextInt(cancelList.size())));
            if (toCancel.getOrderId() > 0) {
              CancelOrder cancelOrder = createCancelOrder((int) toCancel.getOrderId(), (int) toCancel.getOrderId(), toCancel.getUser(),
                  toCancel.getSecurityId(), toCancel.getPrice(), toCancel.getQuantityLong(), toCancel.getSide(), toCancel.getTimeInForce());
              orderBook.cancelOrder(cancelOrder);
            }
            cancelList.remove(toCancel);
          }
          break;
        default:
          order = createOrder(i, user, BTC_USDT_F, price, quantity, side, timeInForce);
          cancelList.add(createOrder(i, user, BTC_USDT_F, price, quantity, side, timeInForce)); // copy order data for cancel
          break;
      }
      if (order != null) {
        System.out.println(">> " + order);
        orderBook.addOrder(order);
      }

      // assertPositionSum(BTC_USDT_F);
    }
    assertPositionSum(BTC_USDT_F);
  }
}
