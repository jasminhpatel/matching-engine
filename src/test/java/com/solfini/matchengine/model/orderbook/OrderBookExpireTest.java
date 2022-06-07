package com.solfini.matchengine.model.orderbook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.AssetFundingRate;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.ExpireContractMessage;
import com.solfini.matchengine.model.orderbook.OrderBookTest.OrderBookWrapper;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import uk.co.real_logic.artio.fields.DecimalFloat;

public class OrderBookExpireTest extends OrderBookTest {

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
      user.addPosition(USDT, USDT_BALANCE * USDT_SCALE_MULT, null);
      expectMessage("userId=" + userId);
    }
  }

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PUT, "BTC/USD[F]", BTC, USDT, 2, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT, UpdateType.PUT, "BTC/USDT", BTC, USDT, 2, 8));

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

    futurePair.setExpireRollTimeMillis(ExpireContractMessage.ONE_DAY);
    callPair.setExpireRollTimeMillis(ExpireContractMessage.ONE_DAY);
    putPair.setExpireRollTimeMillis(ExpireContractMessage.ONE_DAY);

    futurePair.setAssetType(AssetType.DATED_FUTURE);
    futurePair.setContractExpireTime(System.currentTimeMillis() + 5000);
    futurePair.setUnderlyerId(BTC_USDT);

    callPair.setAssetType(AssetType.OPTION_CALL);
    callPair.setContractExpireTime(System.currentTimeMillis() + 5000);
    callPair.setUnderlyerId(BTC_USDT);

    putPair.setAssetType(AssetType.OPTION_PUT);
    putPair.setContractExpireTime(System.currentTimeMillis() + 5000);
    putPair.setUnderlyerId(BTC_USDT);

    OrderBook spotOrderBook =
        OrderBookFactory.create(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, spotPair);
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
  public void testExpireContractMessage() {
    long now = System.currentTimeMillis();
    final InstrumentPair pair = InstrumentCache.getPair(BTC_USDT_DF);
    String symbol = pair.getSymbol();
    pair.setContractExpireTime(now);

    ExpireContractMessage message = new ExpireContractMessage();
    final List<AssetFundingRate> list = message.getAssetExpireList();
    final AssetFundingRate assetFundingRate = new AssetFundingRate();
    assetFundingRate.setAssetId(pair.getId());
    list.add(assetFundingRate);

    message.onMatcher();

    Assert.assertTrue(pair.getContractExpireTime() == now + ExpireContractMessage.ONE_DAY);
    Assert.assertTrue(!pair.getSymbol().equals(symbol));

  }
}
