package com.solfini.matchengine.model.orderbook;

import java.util.List;

import com.solfini.common.Constants;
import com.solfini.instrument.AssetFundingRate;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.ExpireContractMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class ExpireContractMessageScale6Test extends OrderBookTest {

  private final int USER_START = 1;
  private final int USER_COUNT = 4;

  @Before
  @Override
  public void before() {
    configure();
    clearQueues();
    createInstruments();
    createUsers();
  }

  @Override
  protected void createUsers() {
    for (int i = 0; i < USER_COUNT; i++) {
      final int userId = USER_START + i;
      createUser(userId);
      expectMessage("userId=" + userId);
    }
    user = UserCache.get(1);
    user2 = UserCache.get(2);
  }

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 6));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 6));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PUT, "BTC/USD[F]", BTC, USDT, 2, 6));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT, UpdateType.PUT, "BTC/USDT", BTC, USDT, 2, 6));

    Instrument instrument = InstrumentCache.get(1);
    int quantityMultiplier = instrument.getQuantityMultiplier();

    InstrumentCache
        .updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_DF, UpdateType.PUT, "BTC/USD[DF]Jun32", BTC, USDT, 2, 6));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT_CALL_6000, UpdateType.PUT, "BTC/USDT[C]Apr32_6000", BTC, USDT, 2, 6));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT_PUT_6000, UpdateType.PUT, "BTC/USDT[P]Apr32_6000", BTC, USDT, 2, 6));

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
    callPair.setStrikePrice(60_00);

    putPair.setAssetType(AssetType.OPTION_PUT);
    putPair.setContractExpireTime(System.currentTimeMillis() + 5000);
    putPair.setUnderlyerId(BTC_USDT);
    putPair.setStrikePrice(60_00);

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

    spotOrderBook.setMark(80_00);
    futureOrderBook.setMark(80_00);
    callOrderBook.setMark(10_50);
  }

  private Order createOrder(final int orderId, final User user, final OrdType orderType, final int securityId, final long price,
      final long quantity, final Side side, final TimeInForce timeInForce) {
    Order order = OrderObjectPool.get();
    order.setOrderId(orderId);
    order.setUser(user);
    order.setAccount(user == null ? 0 : user.getId());
    order.setClOrdId("ClOrdId");
    order.setOrdType(orderType);
    order.setSecurityId(securityId);
    order.setSide(side);
    order.setPrice(price, (short) 2);
    order.setPriceInt((int) price);
    order.setQty(quantity, (short) 6);
    order.setQuantityLong(quantity);
    order.setQuantityOrigLong(quantity);
    order.setTimeInForce(timeInForce);

    return order;
  }

  protected Order createOrder(final int orderId, final User user, final int securityId, final long price, final long quantity,
      final Side side, final TimeInForce timeInForce) {
    Order order = createOrder(orderId, user, OrdType.LIMIT, securityId, price, quantity, side, timeInForce);
    if (side == Side.SELL)
      order.setType(Constants.SELL_LIMIT);
    else if (side == Side.BUY)
      order.setType(Constants.BUY_LIMIT);

    return order;
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

    Assert.assertEquals(now + ExpireContractMessage.ONE_DAY, pair.getContractExpireTime());
    Assert.assertTrue(!pair.getSymbol().equals(symbol));
  }


  @Test
  public void testExpireDatedFutureContractMessageWithOrders() {
    try {
      user.setPosition(USDT, 100_000000);
      user.setPosition(BTC_USDT_DF, 0);

      user2.setPosition(USDT, 100_000000);
      user2.setPosition(BTC_USDT_DF, 0);

      long now = System.currentTimeMillis();
      final InstrumentPair pair = InstrumentCache.getPair(BTC_USDT_DF);
      String symbol = pair.getSymbol();

      final OrderBook orderBook = pair.getOrderBook();
      orderBook.addOrder(createOrder(1, user, BTC_USDT_DF, 10_11, 3_000000, Side.BUY, DAY));
      orderBook.addOrder(createOrder(2, user, BTC_USDT_DF, 10_12, 2_000000, Side.BUY, DAY));
      orderBook.addOrder(createOrder(3, user2, BTC_USDT_DF, 10_20, 500000, Side.SELL, DAY));
      orderBook.addOrder(createOrder(4, user2, BTC_USDT_DF, 10_82, 1_500000, Side.SELL, DAY));
      orderBook.addOrder(createOrder(5, user2, BTC_USDT_DF, 10_85, 5_000000, Side.SELL, DAY));

      // add crossing buy to create a position
      orderBook.addOrder(createOrder(6, user, BTC_USDT_DF, 10_90, 1_500000, Side.BUY, DAY));

      // assert traded positions opened
      Assert.assertEquals(100_000000, user.getPositionArr()[USDT].getQuantity()); // $100
      Assert.assertEquals(100_000000, user.getPositionArr()[USDT].getQuantity()); // $100
      Assert.assertEquals(1_500000, user.getPositionArr()[BTC_USDT_DF].getQuantity());
      Assert.assertEquals(-1_500000, user2.getPositionArr()[BTC_USDT_DF].getQuantity());

      pair.setContractExpireTime(now);

      ExpireContractMessage message = new ExpireContractMessage();
      final List<AssetFundingRate> list = message.getAssetExpireList();
      final AssetFundingRate assetFundingRate = new AssetFundingRate();
      assetFundingRate.setAssetId(pair.getId());
      list.add(assetFundingRate);

      message.onMatcher();

      // positions should be closed
      Assert.assertEquals(0, user.getPositionArr()[BTC_USDT_DF].getQuantity());
      Assert.assertEquals(0, user2.getPositionArr()[BTC_USDT_DF].getQuantity());

      // when the positions closed, traders had a profit and loss
      Assert.assertEquals(204_080000L, user.getPositionArr()[USDT].getQuantity()); // usdRealized=104.08
      Assert.assertEquals(-4_080000L, user2.getPositionArr()[USDT].getQuantity()); // usdRealized=-104.08

      // available balance should also be updated
      Assert.assertEquals(204_080000L, user.getPositionArr()[USDT].getAvailableQuantity());
      Assert.assertEquals(-4_080000L, user2.getPositionArr()[USDT].getAvailableQuantity());

      // contract should be rolled
      Assert.assertEquals(now + ExpireContractMessage.ONE_DAY, pair.getContractExpireTime());
      Assert.assertTrue(!pair.getSymbol().equals(symbol));

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testExpireCallOptionContractMessageWithOrders() {
    try {
      user.setPosition(USDT, 100_000000L);
      user.setPosition(BTC_USDT_CALL_6000, 0);

      user2.setPosition(USDT, 100_000000L);
      user2.setPosition(BTC_USDT_CALL_6000, 0);

      long now = System.currentTimeMillis();
      final InstrumentPair pair = InstrumentCache.getPair(BTC_USDT_CALL_6000);
      String symbol = pair.getSymbol();

      final OrderBook orderBook = pair.getOrderBook();
      orderBook.addOrder(createOrder(1, user, BTC_USDT_CALL_6000, 10_11, 3_000000, Side.BUY, DAY));
      orderBook.addOrder(createOrder(2, user, BTC_USDT_CALL_6000, 10_12, 2_000000, Side.BUY, DAY));
      orderBook.addOrder(createOrder(3, user2, BTC_USDT_CALL_6000, 10_20, 500000, Side.SELL, DAY));
      orderBook.addOrder(createOrder(4, user2, BTC_USDT_CALL_6000, 10_82, 1_500000, Side.SELL, DAY));
      orderBook.addOrder(createOrder(5, user2, BTC_USDT_CALL_6000, 10_85, 5_000000, Side.SELL, DAY));

      // add crossing buy to create a position
      orderBook.addOrder(createOrder(6, user, BTC_USDT_CALL_6000, 10_90, 1_500000, Side.BUY, DAY)); // 5.1

      // assert traded positions opened
      // usdAvgCostBasis=$10.613333 x 1.5 quantity
      Assert.assertEquals(100_000000, user.getPositionArr()[USDT].getQuantity()); // $100
      Assert.assertEquals(100_000000, user.getPositionArr()[USDT].getQuantity()); // $100
      Assert.assertEquals(1_500000, user.getPositionArr()[BTC_USDT_CALL_6000].getQuantity()); // quantity=1.5
      Assert.assertEquals(-1_500000, user2.getPositionArr()[BTC_USDT_CALL_6000].getQuantity());

      pair.setContractExpireTime(now);

      ExpireContractMessage message = new ExpireContractMessage();
      final List<AssetFundingRate> list = message.getAssetExpireList();
      final AssetFundingRate assetFundingRate = new AssetFundingRate();
      assetFundingRate.setAssetId(pair.getId());
      list.add(assetFundingRate);

      // underlying mark=80.00, strike=60.00, callPrice=20.00
      // pnl = (20.00-10.613333) x 1.5 quantity = 14.0800005
      message.onMatcher();

      // positions should be closed
      Assert.assertEquals(0, user.getPositionArr()[BTC_USDT_CALL_6000].getQuantity());
      Assert.assertEquals(0, user2.getPositionArr()[BTC_USDT_CALL_6000].getQuantity());

      // when the positions closed, traders had a profit and loss
      // 14080000
      Assert.assertEquals(114_080000L, user.getPositionArr()[USDT].getQuantity()); // usdRealized=104.08
      Assert.assertEquals(85_920000L, user2.getPositionArr()[USDT].getQuantity()); // usdRealized=-104.08

      // available balance should also be updated
      Assert.assertEquals(114_080000L, user.getPositionArr()[USDT].getAvailableQuantity());
      Assert.assertEquals(85_920000L, user2.getPositionArr()[USDT].getAvailableQuantity());

      // contract should be rolled
      Assert.assertEquals(now + ExpireContractMessage.ONE_DAY, pair.getContractExpireTime());
      Assert.assertTrue(!pair.getSymbol().equals(symbol));
      // System.out.println("pair.getSymbol()=" + pair.getSymbol());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
