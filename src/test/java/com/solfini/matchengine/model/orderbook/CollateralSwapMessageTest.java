package com.solfini.matchengine.model.orderbook;

import com.solfini.common.Constants;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.message.admin.CollateralSwapMessage;
import com.solfini.matchengine.message.admin.ExpireContractMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
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

public class CollateralSwapMessageTest extends OrderBookTest {

  private final int USER_START = 1;
  private final int USER_COUNT = 4;
  private final long USDC_BALANCE = 1_000_000;
  private final long USDC_SCALE_MULT = 100;
  private final int INSURANCE_FUND = 100;
  private User insuranceFund = null;

  @Before
  @Override
  public void before() {
    configure();
    clearQueues();
    createInstruments();
    createUsers();
    createInsuranceFund(INSURANCE_FUND);
    assertMessages();
  }

  @Override
  protected void createUsers() {
    for (int i = 0; i < USER_COUNT; i++) {
      final int userId = USER_START + i;
      final User user = createUser(userId);
      user.addPosition(USDC, USDC_BALANCE * USDC_SCALE_MULT, null, 0, TokenType.ERC20);
      expectMessage("userId=" + userId);
    }

    user = UserCache.get(1);
    user2 = UserCache.get(2);
  }

  protected void createInsuranceFund(final int userId) {
    UserAdminMessage message = new UserAdminMessage();
    message.setUpdateType(UpdateType.PUT);
    message.setUserId(userId);
    message.setUserType(User.INSURANCE_FUND);
    message.setUsername("insurance");
    message.setFeeTier(1);
    UserCache.add(message);

    insuranceFund = UserCache.get(INSURANCE_FUND);
    setPositions(insuranceFund, 1_0000_00000000L, 100_00000000L);
    expectMessage("userId=" + userId);
  }

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDC, UpdateType.PUT, "USDC", 2, 8));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 8));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDC, UpdateType.PUT, "BTC/USDC", BTC, USDC, 2, 8, CASH_PREORDER_CHECK));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDC_F, UpdateType.PUT, "BTC/USD[F]", BTC, USDC, 2, 8));
    InstrumentCache
        .updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDC_DF, UpdateType.PUT, "BTC/USD[DF]Jun28", BTC, USDC, 2, 8));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDC_CALL_6000, UpdateType.PUT, "BTC/USD[C]Apr28_6000", BTC, USDC, 2, 8));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDC_PUT_6000, UpdateType.PUT, "BTC/USD[P]Apr28_6000", BTC, USDC, 2, 8));

    expectMessage("securityId=" + USDC + ", symbol=USDC");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDC + ", symbol=BTC/USDC");
    expectMessage("securityId=" + BTC_USDC_F + ", symbol=BTC/USD[F]");
    expectMessage("securityId=" + BTC_USDC_DF + ", symbol=BTC/USD[DF]Jun28");
    expectMessage("securityId=" + BTC_USDC_CALL_6000 + ", symbol=BTC/USD[C]Apr28_6000");
    expectMessage("securityId=" + BTC_USDC_PUT_6000 + ", symbol=BTC/USD[P]Apr28_6000");

    InstrumentPair spotPair = InstrumentCache.getPair(BTC_USDC);
    InstrumentPair futurePair = InstrumentCache.getPair(BTC_USDC_DF);
    InstrumentPair callPair = InstrumentCache.getPair(BTC_USDC_CALL_6000);
    InstrumentPair putPair = InstrumentCache.getPair(BTC_USDC_PUT_6000);

    spotPair.setFee(new Fee(spotPair.getId(), USDC, 1, FeeType.PERCENT, MakerTaker.ALL, 0, true));
    spotPair.setFee(new Fee(spotPair.getId(), USDC, 0, FeeType.PERCENT, MakerTaker.ALL, 1, true));

    futurePair.setExpireRollTimeMillis(ExpireContractMessage.ONE_DAY);
    callPair.setExpireRollTimeMillis(ExpireContractMessage.ONE_DAY);
    putPair.setExpireRollTimeMillis(ExpireContractMessage.ONE_DAY);

    futurePair.setAssetType(AssetType.DATED_FUTURE);
    futurePair.setContractExpireTime(System.currentTimeMillis() + 5000);
    futurePair.setUnderlyerId(BTC_USDC);

    callPair.setAssetType(AssetType.OPTION_CALL);
    callPair.setContractExpireTime(System.currentTimeMillis() + 5000);
    callPair.setUnderlyerId(BTC_USDC);

    putPair.setAssetType(AssetType.OPTION_PUT);
    putPair.setContractExpireTime(System.currentTimeMillis() + 5000);
    putPair.setUnderlyerId(BTC_USDC);

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

    spotOrderBook.setMark(800);
    futureOrderBook.setMark(800);
    callOrderBook.setMark(1050);

    InstrumentCache.getBySymbol("USDC").setIndexFeedUsdMark(1);
    InstrumentCache.getBySymbol("BTC").setIndexFeedUsdMark(800);
  }

  private Order createOrder(final long orderId, final User user, final OrdType orderType, final int securityId, final long price,
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
    order.setQty(quantity, (short) 8);
    order.setQuantityLong(quantity);
    order.setQuantityOrigLong(quantity);
    order.setTimeInForce(timeInForce);

    return order;
  }

  protected Order createOrder(final long orderId, final User user, final int securityId, final long price, final long quantity,
      final Side side, final TimeInForce timeInForce) {
    Order order = createOrder(orderId, user, OrdType.LIMIT, securityId, price, quantity, side, timeInForce);
    if (side == Side.SELL)
      order.setType(Constants.SELL_LIMIT);
    else if (side == Side.BUY)
      order.setType(Constants.BUY_LIMIT);

    return order;
  }

  private static void setPositions(final User user, final long usdc, final long btc) {
    user.setPosition(USDC, usdc, null, 0, null, TokenType.ERC20);
    user.setPosition(BTC, btc, null, 0, null, TokenType.ERC20);
  }

  private static void assertPosition(final User user, final int instrumentId, final long quantity, final long available) {
    System.out.println("Positions: userId=" + user.getId() + ", instrumentId=" + instrumentId + ", quantity="
        + user.getPositionArr()[instrumentId].getQuantity() + ", available=" + user.getPositionArr()[instrumentId].getAvailableQuantity());

    Assert.assertEquals(quantity, user.getPositionArr()[instrumentId].getQuantity());
    Assert.assertEquals(available, user.getPositionArr()[instrumentId].getAvailableQuantity());
  }

  // Auto sell BTC to cover its USDC deficit against the market
  @Test
  public void autoSellBTCToCoverUSDCDeficit_AgainstMarket() {
    setPositions(user, -1_000_00000000L, 100_00000000L);
    setPositions(user2, 1_0000_00000000L, 100_00000000L);

    final long orderId = NewOrderSingleHandler.getOrderId();

    final InstrumentPair spotPair = InstrumentCache.getPair(BTC_USDC);
    final OrderBook spotOrderBook = spotPair.getOrderBook();
    spotOrderBook.addOrder(createOrder(NewOrderSingleHandler.getNextOrderId(), user2, BTC_USDC, 799_00, 3_00000000, Side.BUY, DAY));
    spotOrderBook.addOrder(createOrder(NewOrderSingleHandler.getNextOrderId(), user2, BTC_USDC, 798_00, 3_00000000, Side.BUY, DAY));

    expectMessage("orderId=" + (orderId + 1) + ", securityId=" + BTC_USDC + ", account=2, side=BUY, ordStatus=NEW");
    expectMessage("orderId=" + (orderId + 2) + ", securityId=" + BTC_USDC + ", account=2, side=BUY, ordStatus=NEW");
    assertMessages();

    Assert.assertEquals(-1_000_00000000L, user.getPositionArr()[USDC].getQuantity());
    Assert.assertEquals(100_00000000L, user.getPositionArr()[BTC].getQuantity());
    Assert.assertEquals(-1_000_00000000L, user.getPositionArr()[USDC].getAvailableQuantity());
    Assert.assertEquals(100_00000000L, user.getPositionArr()[BTC].getAvailableQuantity());

    final CollateralSwapMessage message = new CollateralSwapMessage(user);
    message.onMatcher();

    expectMessage("orderId=" + (orderId + 3) + ", securityId=" + BTC_USDC + ", account=1, side=SELL, ordType=LIMIT"
        + ", orderQty=132851525, price=77600, ordStatus=NEW, execType=NEW, timeInForce=IMMEDIATE_OR_CANCEL");
    expectMessage("orderId=" + (orderId + 3) + ", securityId=" + BTC_USDC + ", account=1, side=SELL"
        + ", orderQty=132851525, price=77600, lastPx=79900, ordStatus=FILLED, execType=TRADE");
    expectMessage("orderId=" + (orderId + 1) + ", securityId=" + BTC_USDC + ", account=2, side=BUY"
        + ", orderQty=3000000, price=79900, lastPx=79900, ordStatus=PARTIALLY_FILLED, execType=TRADE");
    assertMessages();

    assertPosition(user, USDC, 57_49943619L, 57_49943619L);
    assertPosition(user, BTC, 98_67148475L, 98_67148475L);
    assertPosition(user2, USDC, 8_938_51893852L, 5_208_99520901L);
    assertPosition(user2, BTC, 101_32851525L, 101_32851525L);
  }

  // Auto sell BTC to cover its USDC deficit against the market
  @Test
  public void autoSellBTCToCoverUSDCDeficit_AgainstMarketAtImpactPrice() {
    setPositions(user, -1_000_00000000L, 100_00000000L);
    setPositions(user2, 1_0000_00000000L, 100_00000000L);

    final long orderId = NewOrderSingleHandler.getOrderId();

    final InstrumentPair spotPair = InstrumentCache.getPair(BTC_USDC);
    final OrderBook spotOrderBook = spotPair.getOrderBook();
    spotOrderBook.addOrder(createOrder(NewOrderSingleHandler.getNextOrderId(), user2, BTC_USDC, 776_00, 3_00000000, Side.BUY, DAY));
    spotOrderBook.addOrder(createOrder(NewOrderSingleHandler.getNextOrderId(), user2, BTC_USDC, 776_00, 3_00000000, Side.BUY, DAY));

    expectMessage("orderId=" + (orderId + 1) + ", securityId=" + BTC_USDC + ", account=2, side=BUY, ordStatus=NEW");
    expectMessage("orderId=" + (orderId + 2) + ", securityId=" + BTC_USDC + ", account=2, side=BUY, ordStatus=NEW");
    assertMessages();

    Assert.assertEquals(-1_000_00000000L, user.getPositionArr()[USDC].getQuantity());
    Assert.assertEquals(100_00000000L, user.getPositionArr()[BTC].getQuantity());

    final CollateralSwapMessage message = new CollateralSwapMessage(user);
    message.onMatcher();

    expectMessage("orderId=" + (orderId + 3) + ", securityId=" + BTC_USDC + ", account=1, side=SELL, ordType=LIMIT"
        + ", orderQty=132851525, price=77600, ordStatus=NEW, execType=NEW, timeInForce=IMMEDIATE_OR_CANCEL");
    expectMessage("orderId=" + (orderId + 3) + ", securityId=" + BTC_USDC + ", account=1, side=SELL"
        + ", orderQty=132851525, price=77600, lastPx=77600, ordStatus=FILLED, execType=TRADE");
    expectMessage("orderId=" + (orderId + 1) + ", securityId=" + BTC_USDC + ", account=2, side=BUY"
        + ", orderQty=3000000, price=77600, lastPx=77600, ordStatus=PARTIALLY_FILLED, execType=TRADE");
    assertMessages();

    assertPosition(user, USDC, 27_05402063L, 27_05402063L);
    assertPosition(user, BTC, 98_67148475L, 98_67148475L);
    assertPosition(user2, USDC, 8_969_07896908L, 5_343_99534400L);
    assertPosition(user2, BTC, 101_32851525L, 101_32851525L);
  }

  // Auto sell BTC to cover its USDC deficit when there is no liquidity in the market
  @Test
  public void autoSellBTCToCoverUSDCDeficit_AgainstInsuranceFund() {
    setPositions(user, -1_000_00000000L, 100_00000000L);
    setPositions(insuranceFund, 1_0000_00000000L, 100_00000000L);

    final long orderId = NewOrderSingleHandler.getOrderId();

    Assert.assertEquals(-1_000_00000000L, user.getPositionArr()[USDC].getQuantity());
    Assert.assertEquals(100_00000000L, user.getPositionArr()[BTC].getQuantity());

    final CollateralSwapMessage message = new CollateralSwapMessage(user);
    message.onMatcher();

    expectMessage("orderId=" + (orderId + 1) + ", securityId=" + BTC_USDC + ", account=1, side=SELL, ordType=LIMIT"
        + ", orderQty=132851525, price=77600, ordStatus=NEW, execType=NEW, timeInForce=IMMEDIATE_OR_CANCEL");
    expectMessage("orderId=" + (orderId + 1) + ", securityId=" + BTC_USDC + ", account=1, side=SELL, ordType=LIMIT"
        + ", orderQty=132851525, price=77600, ordStatus=EXPIRED, execType=EXPIRED, timeInForce=IMMEDIATE_OR_CANCEL");
    expectMessage("orderId=" + (orderId + 2) + ", securityId=" + BTC_USDC + ", account=1, clOrdId=autoclose"
        + ", side=SELL, ordType=LIMIT, orderQty=132851525, price=77600, ordStatus=NEW, execType=NEW, timeInForce=IMMEDIATE_OR_CANCEL");
    expectMessage("orderId=" + (orderId + 3) + ", securityId=" + BTC_USDC + ", account=" + INSURANCE_FUND + ", clOrdId=autoclose"
        + ", side=BUY, ordType=LIMIT, orderQty=132851525, price=77600, ordStatus=NEW, execType=NEW, timeInForce=IMMEDIATE_OR_CANCEL");
    expectMessage("orderId=" + (orderId + 2) + ", securityId=" + BTC_USDC + ", account=1, clOrdId=autoclose"
        + ", side=SELL, ordType=LIMIT, orderQty=132851525, price=77600, lastPx=77600, lastQty=132851525"
        + ", ordStatus=FILLED, execType=CALCULATED, timeInForce=IMMEDIATE_OR_CANCEL");
    expectMessage("orderId=" + (orderId + 3) + ", securityId=" + BTC_USDC + ", account=" + INSURANCE_FUND + ", clOrdId=autoclose"
        + ", side=BUY, ordType=LIMIT, orderQty=132851525, price=77600, lastPx=77600, lastQty=132851525"
        + ", ordStatus=FILLED, execType=CALCULATED, timeInForce=IMMEDIATE_OR_CANCEL");
    assertMessages();

    assertPosition(user, USDC, 27_05402063L, 27_05402063L);
    assertPosition(user, BTC, 98_67148475L, 98_67148475L);
    assertPosition(insuranceFund, USDC, 8_972_94597937L, 8_972_94597937L);
    assertPosition(insuranceFund, BTC, 101_32851525L, 101_32851525L);
  }

  // Auto sell BTC to cover its USDC deficit against the market and insurance fund
  @Test
  public void autoSellBTCToCoverUSDCDeficit_AgainstMarketAndInsuranceFund() {
    setPositions(user, -1_000_00000000L, 100_00000000L);
    setPositions(user2, 1_0000_00000000L, 100_00000000L);
    setPositions(insuranceFund, 1_0000_00000000L, 100_00000000L);

    // set available to be different for test
    user.getPositionArr()[USDC].setAvailableQuantity(user.getPositionArr()[USDC].getAvailableQuantity() + 1);
    user.getPositionArr()[BTC].setAvailableQuantity(user.getPositionArr()[BTC].getAvailableQuantity() + 1);

    final long orderId = NewOrderSingleHandler.getOrderId();

    final InstrumentPair spotPair = InstrumentCache.getPair(BTC_USDC);
    final OrderBook spotOrderBook = spotPair.getOrderBook();
    spotOrderBook.addOrder(createOrder(NewOrderSingleHandler.getNextOrderId(), user2, BTC_USDC, 799_00, 1_00000000, Side.BUY, DAY));

    expectMessage("orderId=" + (orderId + 1) + ", securityId=" + BTC_USDC + ", account=2, side=BUY, ordStatus=NEW");
    assertMessages();

    Assert.assertEquals(-1_000_00000000L, user.getPositionArr()[USDC].getQuantity());
    Assert.assertEquals(100_00000000L, user.getPositionArr()[BTC].getQuantity());
    Assert.assertEquals(-1_000_00000000L + 1, user.getPositionArr()[USDC].getAvailableQuantity());
    Assert.assertEquals(100_00000000L + 1, user.getPositionArr()[BTC].getAvailableQuantity());

    final CollateralSwapMessage message = new CollateralSwapMessage(user);
    message.onMatcher();

    expectMessage("orderId=" + (orderId + 2) + ", securityId=" + BTC_USDC + ", account=1, side=SELL, ordType=LIMIT"
        + ", orderQty=132851525, price=77600, ordStatus=NEW, execType=NEW, timeInForce=IMMEDIATE_OR_CANCEL");
    expectMessage("orderId=" + (orderId + 2) + ", securityId=" + BTC_USDC + ", account=1, side=SELL"
        + ", orderQty=132851525, price=77600, lastPx=79900, lastQty=100000000, ordStatus=PARTIALLY_FILLED, execType=TRADE");
    expectMessage("orderId=" + (orderId + 1) + ", securityId=" + BTC_USDC + ", account=2, side=BUY"
        + ", orderQty=1000000, price=79900, lastPx=79900, lastQty=100000000, ordStatus=FILLED, execType=TRADE");
    expectMessage("orderId=" + (orderId + 2) + ", securityId=" + BTC_USDC + ", account=1, side=SELL, ordType=LIMIT"
        + ", orderQty=132851525, price=77600, ordStatus=EXPIRED, execType=EXPIRED, timeInForce=IMMEDIATE_OR_CANCEL");

    expectMessage("orderId=" + (orderId + 3) + ", securityId=" + BTC_USDC + ", account=1, clOrdId=autoclose"
        + ", side=SELL, ordType=LIMIT, orderQty=32851525, price=77600, ordStatus=NEW, execType=NEW, timeInForce=IMMEDIATE_OR_CANCEL");
    expectMessage("orderId=" + (orderId + 4) + ", securityId=" + BTC_USDC + ", account=" + INSURANCE_FUND + ", clOrdId=autoclose"
        + ", side=BUY, ordType=LIMIT, orderQty=32851525, price=77600, ordStatus=NEW, execType=NEW, timeInForce=IMMEDIATE_OR_CANCEL");
    expectMessage("orderId=" + (orderId + 3) + ", securityId=" + BTC_USDC + ", account=1, clOrdId=autoclose"
        + ", side=SELL, ordType=LIMIT, orderQty=32851525, price=77600, lastPx=77600, lastQty=32851525"
        + ", ordStatus=FILLED, execType=CALCULATED, timeInForce=IMMEDIATE_OR_CANCEL");
    expectMessage("orderId=" + (orderId + 4) + ", securityId=" + BTC_USDC + ", account=" + INSURANCE_FUND + ", clOrdId=autoclose"
        + ", side=BUY, ordType=LIMIT, orderQty=32851525, price=77600, lastPx=77600, lastQty=32851525"
        + ", ordStatus=FILLED, execType=CALCULATED, timeInForce=IMMEDIATE_OR_CANCEL");

    // 52_64536083 -> 3.995
    assertPosition(user, USDC, 49_96777063L, 49_96777063L + 1);
    assertPosition(user, BTC, 98_67148475L, 98_67148475L + 1);
    assertPosition(user2, USDC, 9_200_99920100L, 9_200_99920100L);
    assertPosition(user2, BTC, 101_00000000L, 101_00000000L);


    // 9_750_34963917 -> 0.00025492
    assertPosition(insuranceFund, USDC, 9_749_03222937L, 9_749_03222937L); // was 9750_34938425L
    assertPosition(insuranceFund, BTC, 100_32851525L, 100_32851525L);
  }
}
