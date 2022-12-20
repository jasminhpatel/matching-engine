package com.solfini.matchengine.model.orderbook;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.common.ReusableLog;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.risk.InsuranceState;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.message.admin.ExpireContractMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.internal.LiquidationOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;
import static com.solfini.sbe.encoder.TimeInForce.DAY;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OrderBookMarginCalcWithSpotTest5 extends OrderBookTest {

  private final int USER_START = 1;
  private final int USER_COUNT = 4;
  private final int ORDER_COUNT = 100;
  private final long USDT_BALANCE = 1_000_000;
  private final long USDT_SCALE_MULT = 100;

  @Before
  @Override
  public void before() {
    configure();
    clearQueues();
    createInstruments();
    createUsers();
    // assertMessages();
  }

  @Override
  protected void createUsers() {
    for (int i = 0; i < USER_COUNT; i++) {
      final int userId = USER_START + i;
      final User user = createUser(userId);
      user.addPosition(USDT, USDT_BALANCE * USDT_SCALE_MULT, null, 0, TokenType.ERC20);
      expectMessage("userId=" + userId);
    }
  }

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDC", 2, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PUT, "BTC/USD[F]", BTC, USDT, 2, 8));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT, UpdateType.PUT, "BTC/USDC", BTC, USDT, 2, 8, CASH_PREORDER_CHECK));

    InstrumentCache
        .updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_DF, UpdateType.PUT, "BTC/USD[DF]Jun26", BTC, USDT, 2, 8));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT_CALL_6000, UpdateType.PUT, "BTC/USDT[C]Apr24_6000", BTC, USDT, 2, 8));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT_PUT_6000, UpdateType.PUT, "BTC/USDT[P]Apr24_6000", BTC, USDT, 2, 8));

    InstrumentPair spotPair = InstrumentCache.getPair(BTC_USDT);
    InstrumentPair futurePair = InstrumentCache.getPair(BTC_USDT_DF);
    InstrumentPair callPair = InstrumentCache.getPair(BTC_USDT_CALL_6000);
    InstrumentPair putPair = InstrumentCache.getPair(BTC_USDT_PUT_6000);

    spotPair.setFee(Fee.LIQUIDATION_FEE);

    futurePair.setAssetType(AssetType.DATED_FUTURE);
    futurePair.setContractExpireTime(System.currentTimeMillis() + 5000);
    futurePair.setUnderlyerId(BTC_USDT);
    futurePair.setPhysicalSettle(true);
    futurePair.setExpireRollTimeMillis(ExpireContractMessage.ONE_DAY);

    callPair.setAssetType(AssetType.OPTION_CALL);
    callPair.setContractExpireTime(System.currentTimeMillis() + 5000);
    callPair.setUnderlyerId(BTC_USDT);
    callPair.setPhysicalSettle(true);
    callPair.setStrikePrice(6000);
    callPair.setExpireRollTimeMillis(ExpireContractMessage.ONE_DAY);

    putPair.setAssetType(AssetType.OPTION_PUT);
    putPair.setContractExpireTime(System.currentTimeMillis() + 5000);
    putPair.setUnderlyerId(BTC_USDT);
    putPair.setPhysicalSettle(true);
    putPair.setStrikePrice(6000);
    putPair.setExpireRollTimeMillis(ExpireContractMessage.ONE_DAY);

    OrderBook spotOrderBook =
        OrderBookFactory.create(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.CASH_PREORDER_CHECK, spotPair);
    OrderBook futureOrderBook =
        OrderBookFactory.create(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, futurePair);
    OrderBook callOrderBook =
        OrderBookFactory.create(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, callPair);
    OrderBook putOrderBook =
        OrderBookFactory.create(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, putPair);

    spotPair.setOrderBook(spotOrderBook);
    futurePair.setOrderBook(futureOrderBook);
    callPair.setOrderBook(callOrderBook);
    putPair.setOrderBook(putOrderBook);

    spotOrderBook.setMark(8000);
    spotPair.setIndexFeedUsdMark(8000);
    callPair.setIndexFeedUsdMark(1200);
    putPair.setIndexFeedUsdMark(1400);
    futurePair.setIndexFeedUsdMark(1600);
    spotPair.getBase().setIndexFeedUsdMark(8000);
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
  public void testADLMaker() {
    try {
      ReusableLog.setTEST_OUTPUT_MODE(true);
      Context.setControllerMode(Mode.PRIMARY);

      InstrumentPair spotPair = InstrumentCache.getPair(BTC_USDT);
      user = createUser(24);
      user.setPosition(spotPair.getQuotedId(), 100000_00000000L, null, 0, null, TokenType.ERC20); // $100,000
      user.setPosition(BTC_USDT_F, 0, null, 0, null, TokenType.ERC20); // 0
      user.setPosition(BTC_USDT_F, 0, null, 0, null, TokenType.ERC20); // 0

      expectMessage("userId=18");
      expectOutput("userId=18");

      user2 = createUser(25);
      user2.setPosition(USDT, 200000000_00000000L, null, 0, null, TokenType.ERC20); // $200,000
      user2.setPosition(BTC, 200000_00000000L, null, 0, null, TokenType.ERC20); // $200,000
      user2.setPosition(BTC_USDT_F, 0, null, 0, null, TokenType.ERC20); // 0
      expectMessage("userId=19");
      expectOutput("userId=19");

      user3 = createUser(26);
      user3.setPosition(spotPair.getQuotedId(), 300000_00000000L, null, 0, null, TokenType.ERC20); // $300,000
      user3.setPosition(BTC_USDT_F, 0, null, 0, null, TokenType.ERC20); // 0
      InsuranceState.setUser(user3);

      long now = System.currentTimeMillis();
      final InstrumentPair putPair = InstrumentCache.getPair(BTC_USDT_PUT_6000);
      final InstrumentPair futurePair = InstrumentCache.getPair(BTC_USDT_DF);

      final OrderBook orderBook = futurePair.getOrderBook();
      final OrderBook spotOrderBook = spotPair.getOrderBook();


      System.out.println(">> " + now);

      // short notional=1133.60
      // 1133.60 x 1.3 = 1473.68
      orderBook.addOrder(createOrder(101, user2, futurePair.getId(), 100_50, 104_00, Side.BUY, DAY));
      orderBook.getPreOrderCheck().updateRisk(user2, null);

      System.out.println(">> user1=" + user2);

      spotOrderBook.addOrder(createOrder(105, user2, spotPair.getId(), 100_50, 104_00, Side.BUY, DAY));
      spotOrderBook.getPreOrderCheck().updateRisk(user2, null);

      System.out.println(">> user2=" + user2);


      orderBook.getPreOrderCheck().updateRisk(user2, null);


      System.out.println(">> user3=" + user2);



    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
