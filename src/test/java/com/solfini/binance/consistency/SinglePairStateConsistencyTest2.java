package com.solfini.binance.consistency;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
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

public class SinglePairStateConsistencyTest2 extends BinanceOrderBookTest {

  private final int USER_START = 1;
  private final int USER_COUNT = 4;
  private final int ORDER_COUNT = 10;
  private final long USDT_BALANCE = 1_000_000;
  private final long USDT_PX_SCALE_MULT = 100; // factor of 2
  private final long BTC_USDT_PX_SCALE_MULT = 100; // factor of 2
  private final long USDT_QTY_SCALE_MULT = 1_000_000; // factor of 6
  private final int BASE_BTC_PX = 10000_00; // 10,000.00
  private static final Random random = new Random(25214903916L);

  @Override
  protected void onConfigure(final Properties properties) {
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "2000000");
  }

  @Override
  protected void createUsers() {
    for (int i = 0; i < USER_COUNT; i++) {
      final int userId = USER_START + i;
      final User user = createUser(userId);
      // user.setFeeTier(5);
      user.setFeeTier(random.nextInt(5));
      user.addPosition(USDT, USDT_BALANCE * USDT_QTY_SCALE_MULT, null);
      expectMessage("userId=" + userId);
    }
  }

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 6));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 6));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PUT, "BTC/USDT[F]", BTC, USDT, 2, 6));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PUT");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F]");

    pair = InstrumentCache.getPair(BTC_USDT_F);
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 500, FeeType.PERCENT, MakerTaker.ALL, 0, true));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 400, FeeType.PERCENT, MakerTaker.ALL, 1, true));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 300, FeeType.PERCENT, MakerTaker.ALL, 2, true));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 200, FeeType.PERCENT, MakerTaker.ALL, 3, true));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 100, FeeType.PERCENT, MakerTaker.ALL, 4, true));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 0, FeeType.PERCENT, MakerTaker.ALL, 5, true));

    orderBook =
        new BinanceOrderBookTest.OrderBookWrapper(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
    (orderBook.orderBook()).setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));
    preOrderCheck = (orderBook.orderBook()).getPreOrderCheck();
    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(100);
  }

  private void assertPositionSum(final int securityId) {
    assertPositionSum(securityId, 0, 0, 0);
  }

  private void assertPositionSum(final int securityId, final long expected, final long fees, final long delta) {
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
    total += fees;

    System.out.println("assertPositionSum securityId=" + securityId + ", expected=" + expected + ", total=" + total + ", diff="
        + Math.abs(total - expected) + ", delta=" + delta + ", usddiff=" + (Math.abs(total - expected) / (double) USDT_QTY_SCALE_MULT));
    Assert.assertTrue("Total=" + total + ", Expected=" + expected + ", Diff=" + Math.abs(total - expected) + ", Delta=" + delta,
        Math.abs(total - expected) <= delta);
  }

  private void assertPnlSum(final int securityId, final double expected, final double delta) {
    double totalUsdUnrealized = 0;
    double totalUsdRealized = 0;

    for (int i = 0; i < Math.min(UserCache.getCapacity() + 1, UserCache.getCacheSize()); i++) {
      final User user = UserCache.get(i);
      if (user != null && user.isActive()) {
        preOrderCheck.updateRisk(user, null);
        final Position position = user.getPosition(securityId);
        if (position != null && (position.getUsdUnrealized() != 0 || position.getUsdRealizedDouble() != 0)) {
          totalUsdUnrealized += position.getUsdUnrealized();
          totalUsdRealized += position.getUsdRealizedDouble();
          preOrderCheck.updateRisk(user, null);
          preOrderCheck.updateRisk(user, null);
        }
      }
    }
    double total = totalUsdUnrealized + totalUsdRealized;
    System.out.println("assertPnlSum securityId=" + securityId + ", expected=" + expected + ", total=" + total + ", totalUsdUnrealized="
        + totalUsdUnrealized + ", totalUsdRealized=" + totalUsdRealized + ", diff=" + Math.abs(total - expected) + ", delta=" + delta
        + ", usddiff=" + (Math.abs(total - expected) / (double) USDT_QTY_SCALE_MULT / USDT_PX_SCALE_MULT));
    Assert.assertTrue("Total=" + total + ", Expected=" + expected + ", Diff=" + (total - expected) + ", Delta=" + delta,
        Math.abs(total - expected) <= delta);
  }

  private void drain() {
    ArrayList<Message> messages = new ArrayList<>();
    while (messages.isEmpty()) {
      Context.getMatcherToPublisherQueue().drainTo(messages, 1_000);
      if (!messages.isEmpty()) {
        for (final Message message : messages) {
          System.out.println("OUTPUT " + message);
        }
      }
    }
  }

  @Test
  public void marketConsistency() {
    random.setSeed(25214903916L);
    assertPositionSum(USDT, USDT_BALANCE * USDT_QTY_SCALE_MULT * USER_COUNT, 0, 0);
    assertPositionSum(BTC_USDT_F);
    assertPnlSum(BTC_USDT_F, 0, 0);

    TimeInForce[] timeInForceOptions =
        {TimeInForce.DAY, TimeInForce.GOOD_TILL_CANCEL, TimeInForce.FILL_OR_KILL, TimeInForce.IMMEDIATE_OR_CANCEL, TimeInForce.POST_ONLY};
    long totalNotional = 0;

    Order[] orders = new Order[ORDER_COUNT];
    for (int i = 0; i < ORDER_COUNT; i++) {
      final User user = UserCache.get(USER_START + random.nextInt(USER_COUNT));
      final long price = BASE_BTC_PX + random.nextInt(BASE_BTC_PX / 2);
      final long stopPrice = BASE_BTC_PX + random.nextInt(BASE_BTC_PX / 2);
      final long quantity = 1000 + random.nextInt(500);
      final Side side = (random.nextInt() % 2 == 0) ? Side.BUY : Side.SELL;
      final TimeInForce timeInForce = timeInForceOptions[random.nextInt(5)];
      totalNotional += quantity * price;

      System.out.println("RUN " + i);
      switch (random.nextInt(5)) {
        default:
          orders[i] = createOrder(i, user, BTC_USDT_F, price, quantity, side, TimeInForce.GOOD_TILL_CANCEL);
          orderBook.addOrder(createOrder(i, user, BTC_USDT_F, price, quantity, side, TimeInForce.GOOD_TILL_CANCEL));
          System.out.println("AddOrder user=" + user.getId() + ", order=" + orders[i]);
          break;
      }

      // if (i % 100 == 0) {
      drain();
      // }

      long total = 0;
      for (int j = 0; j < USER_COUNT; j++) {
        final Position usdt = UserCache.get(USER_START + j).getPosition(USDT);
        final Position btcusdt = UserCache.get(USER_START + j).getPosition(BTC_USDT_F);
        System.out.println("POSITIONS user=" + (USER_START + j) + ", USDT quantity=" + usdt.getQuantity() + ", available="
            + usdt.getAvailableQuantity() + ", unrealized=" + usdt.getUsdUnrealized() + ", realized=" + usdt.getUsdRealizedDouble()
            + ", BTC/USDT[F] quantity=" + btcusdt.getQuantity() + ", available=" + btcusdt.getAvailableQuantity() + ", unrealized="
            + btcusdt.getUsdUnrealized() + ", realized=" + btcusdt.getUsdRealizedDouble());
        total += usdt.getQuantity();
      }

      System.out.println("USDT total=" + total);

    }

    User exchangeUser = UserCache.getExchangeUser();
    Position usdtFeePosition = exchangeUser.getPosition(1);

    System.out
        .println("fees=" + usdtFeePosition.getQuantity() + ", usdFees=" + (usdtFeePosition.getQuantity() / (double) USDT_QTY_SCALE_MULT));


    double usdtotalNotional = (totalNotional / (USDT_PX_SCALE_MULT * BTC_USDT_PX_SCALE_MULT));
    System.out.println("usdtotalNotional=" + usdtotalNotional);
    // assertPositionSum(USDT, USDT_BALANCE * USDT_QTY_SCALE_MULT * USER_COUNT, usdtFeePosition.getQuantity(), 2 * USDT_QTY_SCALE_MULT);
    // assertPositionSum(USDT, USDT_BALANCE * USDT_QTY_SCALE_MULT * USER_COUNT, 0, 2 * USDT_QTY_SCALE_MULT);
    assertPositionSum(BTC_USDT_F);
    assertPnlSum(BTC_USDT_F, 0, USDT_QTY_SCALE_MULT);
  }

  public static void main(String args[]) {
    Random random = new Random(25214903916L);
    int i = random.nextInt();
    System.out.println("i=" + i);
  }
}
