package com.solfini.binance.consistency;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;

import com.solfini.internal.admin.schema.TokenType;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.binance.orderbook.BinanceOrderBookTest;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;

public class MultiPairStateConsistencyTest extends BinanceOrderBookTest {

  private final int USER_START = 1;
  private final int USER_COUNT = 4000;
  private final int ORDER_COUNT = 10000;
  private final int PAIR_START = 3;
  private final int PAIR_COUNT = 10;
  private final long USDC_BALANCE = 1_000_000;
  private final long PX_SCALE_MULT = 100;
  private final long QTY_SCALE_MULT = 1_000_000;
  private final int BASE_BTC_PX = 10000_00; // 10,000.00
  private final BinanceOrderBookTest.OrderBookWrapper[] orderBooks = new BinanceOrderBookTest.OrderBookWrapper[PAIR_COUNT];

  @Override
  protected void onConfigure(final Properties properties) {
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "2000000");
  }

  @Override
  protected void createUsers() {
    Random random = new Random();
    for (int i = 0; i < USER_COUNT; i++) {
      final int userId = USER_START + i;
      User user;
      if (i == 0) {
        user = createUser(userId, User.EXCHANGE);
        user.setFeeTier(5);
      } else if (i == 1) {
        user = createUser(userId, User.INSURANCE_FUND);
        user.setFeeTier(5);
      } else {
        user = createUser(userId);
        user.setFeeTier(random.nextInt(5));
      }

      user.addPosition(USDC, USDC_BALANCE * QTY_SCALE_MULT, null, 0, TokenType.ERC20);
      expectMessage("userId=" + userId);
    }
  }

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDC, UpdateType.PUT, "USDC", 2, 6));
    expectMessage("securityId=" + USDC + ", symbol=USDC");
    expectOutput("securityId=" + USDC + ", symbol=USDC");

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 6));
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC + ", symbol=BTC");

    for (int i = PAIR_START; i < PAIR_START + PAIR_COUNT; i++) {
      InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(i, UpdateType.PUT, "BTC/USDC[" + i + "]", BTC, USDC, 2, 6));
      expectMessage("securityId=" + i + ", symbol=BTC/USDC[" + i + "], updateType=PUT");
      expectOutput("securityId=" + i + ", symbol=BTC/USDC[" + i + "]");

      InstrumentPair pair = InstrumentCache.getPair(i);
      pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 500, FeeType.PERCENT, MakerTaker.ALL, 0, true));
      pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 400, FeeType.PERCENT, MakerTaker.ALL, 1, false));
      pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 300, FeeType.PERCENT, MakerTaker.ALL, 2, false));
      pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 200, FeeType.PERCENT, MakerTaker.ALL, 3, false));
      pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 100, FeeType.PERCENT, MakerTaker.ALL, 4, false));
      pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 0, FeeType.PERCENT, MakerTaker.ALL, 5, false));

      orderBook =
          new BinanceOrderBookTest.OrderBookWrapper(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
      orderBooks[i - PAIR_START] = orderBook;
      orderBook.orderBook().setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));
      preOrderCheck = orderBook.orderBook().getPreOrderCheck();
      pair.setOrderBook(orderBook.orderBook());
      pair.setIndexFeedUsdMark(10_000);
    }
  }

  private void assertPositionSum(final int securityId, final long expected) {
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

    System.out.println("PositionSum securityId=" + securityId + ", expected=" + expected + ", actual=" + total + ", diff="
        + Math.abs(total - expected) + ", usddiff=" + (Math.abs(total - expected) / (double) QTY_SCALE_MULT));
    Assert.assertTrue("Total=" + total + ", Expected=" + expected + ", Diff=" + Math.abs(total - expected),
        Math.abs(total - expected) / (double) QTY_SCALE_MULT < 0.01);
  }

  private void assertPnlSum(final long expected) {
    double totalUnrealized = 0;

    for (int j = PAIR_START; j < PAIR_START + PAIR_COUNT; j++) {
      double totalUsdUnrealized = 0;
      double totalUsdRealized = 0;
      for (int i = 0; i < Math.min(UserCache.getCapacity() + 1, UserCache.getCacheSize()); i++) {
        final User user = UserCache.get(i);
        if (user != null && user.isActive()) {
          preOrderCheck.updateRisk(user, null);
          final Position contract = user.getPosition(j);
          if (contract != null && (contract.getUsdUnrealized() != 0 || contract.getUsdRealizedDouble() != 0)) {
            totalUsdUnrealized += contract.getUsdUnrealized();
            totalUsdRealized += contract.getUsdRealizedDouble();
            preOrderCheck.updateRisk(user, null);
            preOrderCheck.updateRisk(user, null);
          }
        }
      }

      totalUnrealized += totalUsdUnrealized;

      System.out.println("PnlSum securityId=" + j + ", totalUsdUnrealized=" + totalUsdUnrealized + ", totalUsdRealized=" + totalUsdRealized
          + ", sum=" + (totalUsdUnrealized + totalUsdRealized));
      Assert.assertTrue("TotalUnrealized=" + totalUsdUnrealized + ", TotalUnrealized=" + totalUsdRealized + ", Sum="
          + (totalUsdUnrealized + totalUsdRealized), Math.abs(totalUsdUnrealized + totalUsdRealized) < 0.01);
    }

    double totalUsd = 0;
    for (int i = 0; i < Math.min(UserCache.getCapacity() + 1, UserCache.getCacheSize()); i++) {
      final User user = UserCache.get(i);
      if (user != null && user.isActive()) {
        final Position asset = user.getPosition(USDC);
        if (asset != null) {
          totalUsd += asset.getQuantity() / (double) QTY_SCALE_MULT;
        }
      }
    }

    System.out.println(
        "PnlSum expected=" + (expected / (double) QTY_SCALE_MULT) + ", totalUsd=" + totalUsd + ", totalUsdUnrealized=" + totalUnrealized
            + ", sum=" + (totalUsd + totalUnrealized) + ", diff=" + (expected / (double) QTY_SCALE_MULT - totalUsd - totalUnrealized));
    Assert.assertTrue(
        "Total=" + (totalUsd + totalUnrealized) + ", Expected=" + (expected / (double) QTY_SCALE_MULT) + ", Diff="
            + (expected / (double) QTY_SCALE_MULT - totalUsd - totalUnrealized),
        Math.abs(expected / (double) QTY_SCALE_MULT - totalUsd - totalUnrealized) < 0.01);
  }

  private void drain() {
    ArrayList<Message> messages = new ArrayList<>();
    Context.getMatcherToPublisherQueue().drainTo(messages, 1_000);
  }

  @Test
  public void marketConsistency() {
    assertPositionSum(USDC, USDC_BALANCE * QTY_SCALE_MULT * USER_COUNT);
    assertPnlSum(USDC_BALANCE * QTY_SCALE_MULT * USER_COUNT);
    for (int i = PAIR_START; i < PAIR_START + PAIR_COUNT; i++) {
      assertPositionSum(i, 0);
    }

    Random random = new Random();
    TimeInForce[] timeInForceOptions = {DAY, GOOD_TILL_CANCEL, FILL_OR_KILL, IMMEDIATE_OR_CANCEL, POST_ONLY};
    long totalNotional = 0;

    Order[] orders = new Order[ORDER_COUNT];
    for (int i = 0; i < ORDER_COUNT; i++) {
      final int securityId = PAIR_START + random.nextInt(PAIR_COUNT);
      final User user = UserCache.get(USER_START + random.nextInt(USER_COUNT));
      final long price = BASE_BTC_PX + random.nextInt(BASE_BTC_PX / 2);
      final long stopPrice = BASE_BTC_PX + random.nextInt(BASE_BTC_PX / 2);
      final long quantity = 1000 + random.nextInt(500);
      final Side side = (random.nextInt(Math.abs((int) System.nanoTime())) % 2 == 0) ? Side.BUY : Side.SELL;
      final TimeInForce timeInForce = timeInForceOptions[random.nextInt(Math.abs((int) System.nanoTime())) % 5];

      orderBook = orderBooks[securityId - PAIR_START];
      totalNotional += quantity * price;
      switch (random.nextInt(Math.abs((int) System.nanoTime())) % 5) {
        case 1:
          orderBook.addOrder(createMarketOrder(i, user, securityId, quantity, side, timeInForce));
          break;
        case 2:
          orderBook.addOrder(createStopLimitOrder(i, user, securityId, price, stopPrice, quantity, side, timeInForce));
          break;
        case 3:
          if (orders[i] != null) {
            Order order = orders[i];
            orders[i] = null;
            orderBook.cancelOrder(createCancelOrder(i, (int) order.getOrderId(), order.getUser(), order.getOrdType(), order.getSecurityId(),
                order.getPrice(), order.getQuantityLong(), order.getSide(), order.getTimeInForce()));
          } else {
            orders[i] = createOrder(i, user, securityId, price, quantity, side, timeInForce);
            orderBook.addOrder(createOrder(i, user, securityId, price, quantity, side, timeInForce));
          }
          break;
        case 4:
          if (orders[i] != null) {
            Order order = orders[i];
            orders[i] = null;
            orders[i] = createOrder(i, user, order.getSecurityId(), price, quantity, side, timeInForce);
            orderBook.cancelReplaceOrder(createCancelReplaceOrder(i, (int) order.getOrderId(), order.getUser(), order.getSecurityId(),
                order.getPrice(), order.getQuantityLong(), order.getSide(), order.getTimeInForce(),
                createOrder(i, user, order.getSecurityId(), price, quantity, side, timeInForce)));
          } else {
            orders[i] = createOrder(i, user, securityId, price, quantity, side, timeInForce);
            orderBook.addOrder(createOrder(i, user, securityId, price, quantity, side, timeInForce));
          }
          break;
        default:
          orders[i] = createOrder(i, user, securityId, price, quantity, side, timeInForce);
          orderBook.addOrder(createOrder(i, user, securityId, price, quantity, side, timeInForce));

          break;
      }

      if (i % 100 == 0) {
        drain();
      }
    }

    System.out.println("totalUsdFees=" + (UserCache.getExchangeUser().getPosition(USDC).getQuantity() / (double) QTY_SCALE_MULT));
    System.out.println("totalUsdNotional=" + (totalNotional / (PX_SCALE_MULT * PX_SCALE_MULT)));

    assertPositionSum(BTC_USDC_F, 0);
    assertPnlSum(USDC_BALANCE * QTY_SCALE_MULT * USER_COUNT);
    for (int i = PAIR_START; i < PAIR_START + PAIR_COUNT; i++) {
      assertPositionSum(i, 0);
    }
  }
}
