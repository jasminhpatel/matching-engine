package com.solfini.matchengine.model.risk;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.model.orderbook.OrderBookTest;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.risk.RiskAutoLiquidationThread;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.solfini.sbe.encoder.TimeInForce.*;

public class StateConsistencyTest extends OrderBookTest {

  private final int USER_START = 1;
  private final int USER_COUNT = 4000;
  private final int ORDER_COUNT = 100000;
  private final long USDT_BALANCE = 1_000_000;
  private final long USDT_SCALE_MULT = 100;
  private long totalWithdrawals = 0;

  @Override protected void createUsers() {
    Random random = new Random();
    for (int i = 0; i < USER_COUNT; i++) {
      final int userId = USER_START + i;
      final User user = createUser(userId);
      user.setFeeTier(random.nextInt(5));
      user.addPosition(USDT, USDT_BALANCE * USDT_SCALE_MULT);
      expectMessage("userId=" + userId);
    }
  }

  @Override protected void createInstruments() {
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
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 0, FeeType.PERCENT, MakerTaker.ALL, 0, true));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 100, FeeType.PERCENT, MakerTaker.ALL, 1, true));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 200, FeeType.PERCENT, MakerTaker.ALL, 2, true));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 300, FeeType.PERCENT, MakerTaker.ALL, 3, true));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 400, FeeType.PERCENT, MakerTaker.ALL, 4, true));

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

    Assert.assertTrue("Total=" + total + ", Expected=" + expected + ", Diff=" + Math.abs(total - expected) + ", Delta=" + delta,
      Math.abs(total - expected) <= delta);
  }

  private void assertPnlSum(final int securityId, final double expected, final double delta) {
    double total = 0;
    for (int i = 0; i < Math.min(UserCache.getCapacity() + 1, UserCache.getCacheSize()); i++) {
      final User user = UserCache.get(i);
      if (user != null && user.isActive()) {
        final Position position = user.getPosition(securityId);
        if (position != null) {
          total += position.getUsdUnrealized();
          total += position.getUsdRealizedDouble();
        }
      }
    }

    Assert.assertTrue("Total=" + total + ", Expected=" + expected + ", Diff=" + (total - expected) + ", Delta=" + delta,
      Math.abs(total - expected) <= delta);
  }

  private void drain() {
    ArrayList<Message> messages = new ArrayList<>();
    Context.getMatcherToPublisherQueue().drainTo(messages, 1_000);
  }

  @Test public void marketConsistency() {
    assertPositionSum(USDT, USDT_BALANCE * USDT_SCALE_MULT * USER_COUNT, 0);
    assertPositionSum(BTC_USDT_F);
    assertPnlSum(BTC_USDT_F, 0, 0);

    RiskAutoLiquidationThread autoLiquidationThread = new RiskAutoLiquidationThread(new NoOpIdleStrategy());

    Random random = new Random();
    TimeInForce[] timeInForceOptions = {DAY, GOOD_TILL_CANCEL, FILL_OR_KILL, IMMEDIATE_OR_CANCEL, POST_ONLY};

    Order[] orders = new Order[ORDER_COUNT];
    for (int i = 0; i < ORDER_COUNT; i++) {
      final User user = UserCache.get(USER_START + random.nextInt(USER_COUNT));
      final long price = 1000000 + random.nextInt(1000000 / 2);
      final long stopPrice = 1000000 + random.nextInt(1000000 / 2);
      final long quantity = 1000 + random.nextInt(500);
      final Side side = (random.nextInt(Math.abs((int) System.nanoTime())) % 2 == 0) ? Side.BUY : Side.SELL;
      final TimeInForce timeInForce = timeInForceOptions[random.nextInt(Math.abs((int) System.nanoTime())) % 5];
      switch (random.nextInt(Math.abs((int) System.nanoTime())) % 10) {
        case 1:
          orderBook.addOrder(createMarketOrder(i, user, BTC_USDT_F, quantity, side, timeInForce));
          break;
        case 2:
          orderBook.addOrder(createStopLimitOrder(i, user, BTC_USDT_F, price, stopPrice, quantity, side, timeInForce));
          break;
        case 3:
          if (orders[i] != null) {
            Order order = orders[i];
            orders[i] = null;
            orderBook.cancelOrder(
              createCancelOrder(i, (int) order.getOrderId(), order.getUser(), order.getOrdType(), order.getSecurityId(), order.getPrice(),
                order.getQuantityLong(), order.getSide(), order.getTimeInForce()));
          } else {
            orders[i] = createOrder(i, user, BTC_USDT_F, price, quantity, side, timeInForce);
            orderBook.addOrder(createOrder(i, user, BTC_USDT_F, price, quantity, side, timeInForce));
          }
          break;
        case 4:
          if (orders[i] != null) {
            Order order = orders[i];
            orders[i] = null;
            orders[i] = createOrder(i, user, BTC_USDT_F, price, quantity, side, timeInForce);
            orderBook.cancelReplaceOrder(
              createCancelReplaceOrder(i, (int) order.getOrderId(), order.getUser(), order.getSecurityId(), order.getPrice(),
                order.getQuantityLong(), order.getSide(), order.getTimeInForce(),
                createOrder(i, user, BTC_USDT_F, price, quantity, side, timeInForce)));
          } else {
            orders[i] = createOrder(i, user, BTC_USDT_F, price, quantity, side, timeInForce);
            orderBook.addOrder(createOrder(i, user, BTC_USDT_F, price, quantity, side, timeInForce));
          }
          break;
        case 5:
          totalWithdrawals += user.getPosition(USDT).getQuantity();
          BalanceAdminMessage balanceAdminMessage = new BalanceAdminMessage();
          balanceAdminMessage.setUpdateType(UpdateType.PUT);
          balanceAdminMessage.setUserId(user.getId());
          balanceAdminMessage.setTxType(TX_ADJUSTMENT);
          balanceAdminMessage.addBalance(new Balance(USDT, 0, 0, 0, 0));
          UserCache.addBalance(balanceAdminMessage);

          final List<Order> liquidationOrders = new ArrayList<Order>();
          autoLiquidationThread.autoLiquidate(user, liquidationOrders);

          final List<Message> messages = new ArrayList<Message>();
          Context.getRiskToMatcherQueue().drainTo(messages, 1000);
          for (final Message message : messages) {
            message.onMatcher();
          }
          break;
        default:
          orders[i] = createOrder(i, user, BTC_USDT_F, price, quantity, side, timeInForce);
          orderBook.addOrder(createOrder(i, user, BTC_USDT_F, price, quantity, side, timeInForce));

          break;
      }

      if (i % 100 == 0) {
        drain();
      }
    }

    assertPositionSum(USDT, USDT_BALANCE * USDT_SCALE_MULT * USER_COUNT - totalWithdrawals, USDT_SCALE_MULT);
    assertPositionSum(BTC_USDT_F);
    assertPnlSum(BTC_USDT_F, 0, 0.1);
  }
}
