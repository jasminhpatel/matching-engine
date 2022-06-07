package com.solfini.binance.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.Position;
import com.solfini.binance.orderbook.BinanceOrderBookTest;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.risk.RiskAutoLiquidationThread;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;

public class AutoLiquidationStateConsistencyTest extends BinanceOrderBookTest {

  private final int USER_START = 1;
  private final int USER_COUNT = 4000;
  private final int ORDER_COUNT = 10000;
  private final long USDC_BALANCE = 1_000_000;
  private final long PX_SCALE_MULT = 100;
  private final long QTY_SCALE_MULT = 1_000_000;
  private final int BASE_BTC_PX = 10000_00; // 10,000.00
  private long totalWithdrawals = 0;

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

      user.addPosition(USDC, USDC_BALANCE * QTY_SCALE_MULT, null);
      expectMessage("userId=" + userId);
    }
  }

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDC, UpdateType.PUT, "USDC", 2, 6));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 6));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDC_F, UpdateType.PUT, "BTC/USDC[F]", BTC, USDC, 2, 6));

    expectMessage("securityId=" + USDC + ", symbol=USDC");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDC_F + ", symbol=BTC/USDC[F], updateType=PUT");

    pair = InstrumentCache.getPair(BTC_USDC_F);
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 500, FeeType.PERCENT, MakerTaker.ALL, 0, true));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 400, FeeType.PERCENT, MakerTaker.ALL, 1, false));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 300, FeeType.PERCENT, MakerTaker.ALL, 2, false));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 200, FeeType.PERCENT, MakerTaker.ALL, 3, false));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 100, FeeType.PERCENT, MakerTaker.ALL, 4, false));
    pair.setFee(new Fee(pair.getId(), pair.getQuotedId(), 0, FeeType.PERCENT, MakerTaker.ALL, 5, false));

    orderBook =
        new BinanceOrderBookTest.OrderBookWrapper(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
    (orderBook.orderBook()).setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));
    preOrderCheck = (orderBook.orderBook()).getPreOrderCheck();
    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(10_000);
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
    double totalUsd = 0;
    double totalUsdUnrealized = 0;
    double totalUsdRealized = 0;

    for (int i = 0; i < Math.min(UserCache.getCapacity() + 1, UserCache.getCacheSize()); i++) {
      final User user = UserCache.get(i);
      if (user != null && user.isActive()) {
        preOrderCheck.updateRisk(user, null);
        final Position contract = user.getPosition(BTC_USDC_F);
        if (contract != null && (contract.getUsdUnrealized() != 0 || contract.getUsdRealizedDouble() != 0)) {
          totalUsdUnrealized += contract.getUsdUnrealized();
          totalUsdRealized += contract.getUsdRealizedDouble();
          preOrderCheck.updateRisk(user, null);
          preOrderCheck.updateRisk(user, null);
        }
        final Position asset = user.getPosition(USDC);
        if (asset != null) {
          totalUsd += asset.getQuantity() / (double) QTY_SCALE_MULT;
        }
      }
    }

    System.out.println("PnlSum securityId=" + BTC_USDC_F + ", totalUsdUnrealized=" + totalUsdUnrealized + ", totalUsdRealized="
        + totalUsdRealized + ", sum=" + (totalUsdUnrealized + totalUsdRealized));
    Assert.assertTrue("TotalUnrealized=" + totalUsdUnrealized + ", TotalRealized=" + totalUsdRealized + ", Sum="
        + (totalUsdUnrealized + totalUsdRealized), Math.abs(totalUsdUnrealized + totalUsdRealized) < 0.1);

    final double totalUsdWithdrawals = totalWithdrawals / (double) QTY_SCALE_MULT;
    System.out.println(
        "PnlSum expected=" + (expected / (double) QTY_SCALE_MULT) + ", totalUsd=" + totalUsd + ", totalUsdUnrealized=" + totalUsdUnrealized
            + ", totalUsdWithdrawals=" + totalUsdWithdrawals + ", sum=" + (totalUsd + totalUsdUnrealized + totalUsdWithdrawals) + ", diff="
            + (expected / (double) QTY_SCALE_MULT - totalUsd - totalUsdUnrealized - totalUsdWithdrawals));
    Assert.assertTrue(
        "Total=" + (totalUsd + totalUsdUnrealized + totalUsdWithdrawals) + ", Expected=" + (expected / (double) QTY_SCALE_MULT) + ", Diff="
            + (expected / (double) QTY_SCALE_MULT - totalUsd - totalUsdUnrealized - totalUsdWithdrawals),
        Math.abs(expected / (double) QTY_SCALE_MULT - totalUsd - totalUsdUnrealized - totalUsdWithdrawals) < 0.1);
  }

  private void drain() {
    ArrayList<Message> messages = new ArrayList<>();
    Context.getMatcherToPublisherQueue().drainTo(messages, 1_000);
  }

  @Test
  public void marketConsistency() {
    assertPositionSum(USDC, USDC_BALANCE * QTY_SCALE_MULT * USER_COUNT);
    assertPositionSum(BTC_USDC_F, 0);
    assertPnlSum(USDC_BALANCE * QTY_SCALE_MULT * USER_COUNT);

    RiskAutoLiquidationThread autoLiquidationThread = new RiskAutoLiquidationThread(new NoOpIdleStrategy());

    Random random = new Random();
    TimeInForce[] timeInForceOptions = {DAY, GOOD_TILL_CANCEL, FILL_OR_KILL, IMMEDIATE_OR_CANCEL, POST_ONLY};
    long totalNotional = 0;

    Order[] orders = new Order[ORDER_COUNT];
    for (int i = 0; i < ORDER_COUNT; i++) {
      final User user = UserCache.get(USER_START + random.nextInt(USER_COUNT));
      final long price = BASE_BTC_PX + random.nextInt(BASE_BTC_PX / 2);
      final long stopPrice = BASE_BTC_PX + random.nextInt(BASE_BTC_PX / 2);
      final long quantity = 1000 + random.nextInt(500);
      final Side side = (random.nextInt(Math.abs((int) System.nanoTime())) % 2 == 0) ? Side.BUY : Side.SELL;
      final TimeInForce timeInForce = timeInForceOptions[random.nextInt(Math.abs((int) System.nanoTime())) % 5];
      totalNotional += quantity * price;
      switch (random.nextInt(Math.abs((int) System.nanoTime())) % 10) {
        case 1:
          orderBook.addOrder(createMarketOrder(i, user, BTC_USDC_F, quantity, side, timeInForce));
          break;
        case 2:
          orderBook.addOrder(createStopLimitOrder(i, user, BTC_USDC_F, price, stopPrice, quantity, side, timeInForce));
          break;
        case 3:
          if (orders[i] != null) {
            Order order = orders[i];
            orders[i] = null;
            orderBook.cancelOrder(createCancelOrder(i, (int) order.getOrderId(), order.getUser(), order.getOrdType(), order.getSecurityId(),
                order.getPrice(), order.getQuantityLong(), order.getSide(), order.getTimeInForce()));
          } else {
            orders[i] = createOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce);
            orderBook.addOrder(createOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce));
          }
          break;
        case 4:
          if (orders[i] != null) {
            Order order = orders[i];
            orders[i] = null;
            orders[i] = createOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce);
            orderBook.cancelReplaceOrder(createCancelReplaceOrder(i, (int) order.getOrderId(), order.getUser(), order.getSecurityId(),
                order.getPrice(), order.getQuantityLong(), order.getSide(), order.getTimeInForce(),
                createOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce)));
          } else {
            orders[i] = createOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce);
            orderBook.addOrder(createOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce));
          }
          break;
        case 5:
          totalWithdrawals += user.getPosition(USDC).getQuantity();
          BalanceAdminMessage balanceAdminMessage = new BalanceAdminMessage();
          balanceAdminMessage.setUpdateType(UpdateType.PUT);
          balanceAdminMessage.setUserId(user.getId());
          balanceAdminMessage.setTxType(TX_ADJUSTMENT);
          balanceAdminMessage.addBalance(new Balance(USDC, 0, 0, 0, 0, null));
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
          orders[i] = createOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce);
          orderBook.addOrder(createOrder(i, user, BTC_USDC_F, price, quantity, side, timeInForce));

          break;
      }

      if (i % 100 == 0) {
        drain();
      }
    }

    System.out.println("totalUsdFees=" + (UserCache.getExchangeUser().getPosition(USDC).getQuantity() / (double) QTY_SCALE_MULT));
    System.out.println("totalUsdNotional=" + (totalNotional / (PX_SCALE_MULT * PX_SCALE_MULT)));
    System.out.println("totalUsdWithdrawals=" + (totalWithdrawals / (double) QTY_SCALE_MULT));

    assertPositionSum(BTC_USDC_F, 0);
    assertPnlSum(USDC_BALANCE * QTY_SCALE_MULT * USER_COUNT);
  }
}
