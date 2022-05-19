package com.solfini.matchengine.model.orderbook;

import com.solfini.instrument.AssetFundingRate;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.ExpireContractMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;

import static com.solfini.sbe.encoder.TimeInForce.*;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class ExpiryTest extends OrderBookTest {

  final long now = System.currentTimeMillis();

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDC, UpdateType.PUT, "USDC", 4, 1));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 3, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDC, UpdateType.PUT, "BTC/USDC", BTC, USDC, 2, 3));
    expectMessage("securityId=" + USDC + ", symbol=USDC");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDC + ", symbol=BTC/USDC");
    assertMessages();

    final SecurityDefinitionAdminMessage message = createInstrumentPairDefinition(BTC_USDC_DF, UpdateType.PUT, "BTC/USDC[DF]", BTC, USDC, 2, 3);
    message.setAssetType(AssetType.DATED_FUTURE);
    message.setUnderlyerId(BTC_USDC);
    InstrumentCache.updateSecurityDefinition(message);
    expectMessage("securityId=" + BTC_USDC_DF + ", symbol=BTC/USDC[DF], updateType=PUT");
    assertMessages();

    InstrumentPair underlyer = InstrumentCache.getPair(BTC_USDC);
    orderBook = new OrderBookTest.OrderBookWrapper(underlyer);
    underlyer.setOrderBook(orderBook.orderBook());

    pair = InstrumentCache.getPair(BTC_USDC_DF);
    pair.setContractExpireTime(now + 3600000);
    orderBook = new OrderBookTest.OrderBookWrapper(pair);
    orderBook.orderBook().setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));
    pair.setOrderBook(orderBook.orderBook());
    // pair.setFee(new Fee(pair.getId(), USDC, 1000, FeeType.PERCENT, MakerTaker.ALL, 0, true));

    setUnderlyerMark(10);
  }

  private void setUnderlyerMark(final double mark) {
    InstrumentCache.getPair(BTC_USDC).setIndexFeedUsdMark(mark);
    InstrumentCache.getPair(BTC_USDC).getOrderBook().setMark((int) (mark * InstrumentCache.getPair(BTC_USDC).getPriceScaleMultiplier()));
  }

  @Override
  protected void createUsers() {
    user1 = createUser(18, new Balance(USDC, 1000, 0, 0, 0), new Balance(BTC, 1000, 0, 0, 0), new Balance(BTC_USDC_DF, 0, 0, 0, 0));
    user1.setOpenOrderCount(0);
    expectMessage("userId=18");

    user2 = createUser(19, new Balance(USDC, 0, 0, 0, 0), new Balance(BTC, 0, 0, 0, 0), new Balance(BTC_USDC_DF, 0, 0, 0, 0));
    user2.setOpenOrderCount(0);
    expectMessage("userId=19");

    user3 = createUser(20, new Balance(USDC, 1000, 0, 0, 0), new Balance(BTC, 1000, 0, 0, 0), new Balance(BTC_USDC_DF, 0, 0, 0, 0));
    user3.setOpenOrderCount(0);
    expectMessage("userId=20");

    user1.getPosition(BTC_USDC_DF).getUserOpenOrdersByPair().set(user1, InstrumentCache.getPair(BTC_USDC_DF));
    user2.getPosition(BTC_USDC_DF).getUserOpenOrdersByPair().set(user2, InstrumentCache.getPair(BTC_USDC_DF));
    user3.getPosition(BTC_USDC_DF).getUserOpenOrdersByPair().set(user3, InstrumentCache.getPair(BTC_USDC_DF));
  }

  private void assertPosition(final User user1, final int instrumentId, final long quantity, final long availableQuantity) {
    Assert.assertEquals(
        "Expected quantity: " + quantity + ", Received quantity: " + user1.getPosition(instrumentId).getQuantity(),
        quantity, user1.getPosition(instrumentId).getQuantity());
    Assert.assertEquals(
        "Expected available quantity: " + availableQuantity + ", Received available quantity: "
            + user1.getPosition(instrumentId).getAvailableQuantity(),
        availableQuantity, user1.getPosition(instrumentId).getAvailableQuantity());
  }

  // Should get rejected
  @Test
  public void orderInitializationWithZeroBalanceShouldGetRejected() {
    assertPosition(user2, USDC, 0_0, 0_0);
    assertPosition(user2, BTC, 0_0, 0_0);
    assertPosition(user2, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user2, BTC_USDC_DF, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=REJECTED");
    expectMessage("orderId=1, businessRejectReason=FAILED_PRE_CREDIT_CHECK");
    assertMessages();

    assertPosition(user2, USDC, 0_0, 0_0);
    assertPosition(user2, BTC, 0_0, 0_0);
    assertPosition(user2, BTC_USDC_DF, 0_0, 0_0);
  }

  @Test
  public void orderInitializationWithPositiveBalanceApproved() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);
  }

  @Test
  public void buyFirstEqualOrdersShouldBeMatched() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 10_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user3, BTC_USDC_DF, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 50_000, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, -50_000, 0_0);
  }

  @Test
  public void buyFirstDifferentValuedOrdersShouldBeMatched() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 30_00, 50_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user3, BTC_USDC_DF, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 50_000, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, -50_000, 0_0);
  }

  @Test
  public void sellFirstEqualOrdersShouldBeMatched() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user3, BTC_USDC_DF, 10_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, -50_000, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 50_000, 0_0);
  }

  @Test
  public void sellFirstDifferentValuedOrdersShouldBeMatched() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user3, BTC_USDC_DF, 20_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, -50_000, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 50_000, 0_0);
  }

  private void expireContract(final int instrumentId) {
    final ExpireContractMessage message = new ExpireContractMessage();
    final List<AssetFundingRate> list = message.getAssetExpireList();
    final AssetFundingRate assetFundingRate = new AssetFundingRate();
    assetFundingRate.setAssetId(instrumentId);
    list.add(assetFundingRate);
    message.onMatcher();
  }

  @Test
  public void openBuyOrdersShouldGetExpired() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 20_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    assertMessages();

    expireContract(BTC_USDC_DF);
    expectMessage("orderId=1, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);
    assertEquals(0, user1.getOpenOrderCount());
  }

  @Test
  public void openSellOrdersShouldGetExpired() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 20_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    assertMessages();

    expireContract(BTC_USDC_DF);
    expectMessage("orderId=1, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);
    assertEquals(0, user1.getOpenOrderCount());
  }

  @Test
  public void openBuyOrdersShouldGetExpired_MarkPriceChanged() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 20_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    assertMessages();

    setUnderlyerMark(30);
    expireContract(BTC_USDC_DF);
    expectMessage("orderId=1, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);
    assertEquals(0, user1.getOpenOrderCount());
  }

  @Test
  public void openSellOrdersShouldGetExpired_MarkPriceChanged() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 20_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    assertMessages();

    setUnderlyerMark(30);
    expireContract(BTC_USDC_DF);
    expectMessage("orderId=1, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);
    assertEquals(0, user1.getOpenOrderCount());
  }

  @Test
  public void multipleOpenSellAndBuyOrdersShouldGetExpired_MarkPriceChanged() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 30_00, 10_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user1, BTC_USDC_DF, 20_00, 10_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user1, BTC_USDC_DF, 15_00, 10_00, Side.BUY, DAY));
    orderBook.addOrder(createOrder(4, user1, BTC_USDC_DF, 10_00, 10_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=3, ordStatus=NEW");
    expectMessage("orderId=4, ordStatus=NEW");
    assertMessages();

    setUnderlyerMark(30);
    expireContract(BTC_USDC_DF);
    expectMessage("orderId=4, ordStatus=EXPIRED");
    expectMessage("orderId=3, ordStatus=EXPIRED");
    expectMessage("orderId=2, ordStatus=EXPIRED");
    expectMessage("orderId=1, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertEquals(0, user1.getOpenOrderCount());
  }

  @Test
  public void ordersShouldBeSettledAfterExpiryTriggered_NoMarkPriceChange() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 10_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user3, BTC_USDC_DF, 10_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, -50_000, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 50_000, 0_0);

    expireContract(BTC_USDC_DF);
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=BUY,account=18,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=NEW,ordStatus=NEW");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=BUY,account=18,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=CALCULATED,ordStatus=FILLED");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=SELL,account=20,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=NEW,ordStatus=NEW");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=SELL,account=20,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=CALCULATED,ordStatus=FILLED");
    assertMessages();

    final long diff = (20 - 20) * 50_0;
    assertPosition(user1, USDC, 1000_0 + diff, 1000_0 + diff);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0 - diff, 1000_0 - diff);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);
  }

  @Test
  public void ordersShouldBeSettledAfterExpiryTriggered_MarkPriceChangedAfterMatching() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 20_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user3, BTC_USDC_DF, 20_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC_USDC_DF, -50_000, 0_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC_USDC_DF, 50_000, 0_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    setUnderlyerMark(30);
    expireContract(BTC_USDC_DF);
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=BUY,account=18,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=NEW,ordStatus=NEW");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=BUY,account=18,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=CALCULATED,ordStatus=FILLED");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=SELL,account=20,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=NEW,ordStatus=NEW");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=SELL,account=20,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=CALCULATED,ordStatus=FILLED");
    assertMessages();

    final long diff = (20 - 30) * 50_0;
    assertPosition(user1, USDC, 1000_0 + diff, 1000_0 + diff);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0 - diff, 1000_0 - diff);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);
  }

  @Test
  public void ordersShouldBeSettledAfterExpiryTriggered_MarkPriceChangedBeforeMatching() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);

    setUnderlyerMark(30);
    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 20_00, 50_00, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user3, BTC_USDC_DF, 20_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, -50_000, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 50_000, 0_0);

    expireContract(BTC_USDC_DF);
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=BUY,account=18,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=NEW,ordStatus=NEW");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=BUY,account=18,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=CALCULATED,ordStatus=FILLED");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=SELL,account=20,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=NEW,ordStatus=NEW");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=SELL,account=20,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=CALCULATED,ordStatus=FILLED");
    assertMessages();

    final long diff = (20 - 30) * 50_0;
    assertPosition(user1, USDC, 1000_0 + diff, 1000_0 + diff);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0 - diff, 1000_0 - diff);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);
  }

  @Test
  public void ordersShouldBeSettledAfterExpiryTriggered_MarkPriceChangedMultipleTimes() {

    setUnderlyerMark(20);

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 20_00, 50_00, Side.SELL, DAY));
    setUnderlyerMark(23);
    orderBook.addOrder(createOrder(2, user3, BTC_USDC_DF, 20_00, 50_00, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=NEW");
    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC_USDC_DF, -50_000, 0_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC_USDC_DF, 50_000, 0_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);

    setUnderlyerMark(29);
    setUnderlyerMark(30);

    expireContract(BTC_USDC_DF);
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=BUY,account=18,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=NEW,ordStatus=NEW");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=BUY,account=18,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=CALCULATED,ordStatus=FILLED");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=SELL,account=20,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=NEW,ordStatus=NEW");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=SELL,account=20,orderQty=50000,timeInForce=IMMEDIATE_OR_CANCEL,execType=CALCULATED,ordStatus=FILLED");
    assertMessages();

    final long diff = (20 - 30) * 50_0;
    assertPosition(user1, USDC, 1000_0 + diff, 1000_0 + diff);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0 - diff, 1000_0 - diff);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);
  }

  @Test
  public void cancelOrderAfterExpireTimeShouldGetRejected() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);

    expireContract(BTC_USDC_DF);
    expectMessage("orderId=1, ordStatus=EXPIRED");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);

    orderBook.cancelOrder(createCancelOrder(1, 1, user1, BTC_USDC_DF, 10_00, 50_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=PENDING_CANCEL");
    expectMessage("CancelRejectMessage", "orderId=1", "cxlRejReason=UNKNOWN_ORDER");
    assertMessages();
  }

  // Open order should not have any impact on balances
  @Test
  public void openAndMatchedOrdersShouldBeSettledAfterExpiry() {
    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);

    orderBook.addOrder(createOrder(1, user1, BTC_USDC_DF, 10_00, 10_00, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");
    orderBook.addOrder(createOrder(2, user3, BTC_USDC_DF, 10_00, 10_00, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=NEW");

    expectMessage("orderId=2, ordStatus=FILLED");
    expectMessage("orderId=1, ordStatus=FILLED");

    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, -10_000, 0_0);

    assertPosition(user3, USDC, 1000_0, 1000_0);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 10_000, 0_0);

    orderBook.addOrder(createOrder(3, user1, BTC_USDC_DF, 10_00, 01_00, Side.SELL, DAY));
    expectMessage("orderId=3, ordStatus=NEW");
    assertMessages();

    assertPosition(user1, USDC, 1000_0, 1000_0);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, -10_000, 0_0);

    expireContract(BTC_USDC_DF);
    expectMessage("orderId=3, ordStatus=EXPIRED");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=BUY,account=18,orderQty=10000,timeInForce=IMMEDIATE_OR_CANCEL,execType=NEW,ordStatus=NEW");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=BUY,account=18,orderQty=10000,timeInForce=IMMEDIATE_OR_CANCEL,execType=CALCULATED,ordStatus=FILLED");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=SELL,account=20,orderQty=10000,timeInForce=IMMEDIATE_OR_CANCEL,execType=NEW,ordStatus=NEW");
    expectMessage("ExecutionReportMessage", "symbol=BTC/USDC[DF],ordType=LIMIT,side=SELL,account=20,orderQty=10000,timeInForce=IMMEDIATE_OR_CANCEL,execType=CALCULATED,ordStatus=FILLED");
    assertMessages();

    final long diff = (20 - 20) * 50_0;
    assertPosition(user1, USDC, 1000_0 + diff, 1000_0 + diff);
    assertPosition(user1, BTC, 1000_00, 1000_00);
    assertPosition(user1, BTC_USDC_DF, 0_0, 0_0);
    assertEquals(0, user1.getOpenOrderCount());

    assertPosition(user3, USDC, 1000_0 - diff, 1000_0 - diff);
    assertPosition(user3, BTC, 1000_00, 1000_00);
    assertPosition(user3, BTC_USDC_DF, 0_0, 0_0);
  }
}
