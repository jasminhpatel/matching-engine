package com.solfini.matchengine.model.orderbook;

import com.solfini.common.Context;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import org.junit.Assert;
import org.junit.Test;

public class OrderBookMarginCalcTest5 extends OrderBookTest {

  private static SecurityDefinitionAdminMessage discount(final SecurityDefinitionAdminMessage message, final int value) {
    message.setCollateralMarginPercentDiscount(value * 100);
    return message;
  }

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(discount(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2), 0));
    InstrumentCache.updateSecurityDefinition(discount(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 4, 3), 100));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PUT, "BTC/USDT[F]", BTC, USDT, 2, 2));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PUT");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F]");

    pair = InstrumentCache.getPair(BTC_USDT_F);

    orderBook = new OrderBookTest.OrderBookWrapper(OrderBookFactory.ARRAY_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
    ((ArrayOrderBook) orderBook.orderBook()).setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));
    this.validator = orderBook.getOrderBookValidator();

    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(5);
    pair.setMarginCurveId(5);
    pair.setMaintMarginBasisPoints(40);
    pair.setRequiredMarginBasisPoints(80);

  }

  private void setPositions(final long usdt, final long btc, final long btc_usdt_f) {
    user.setPosition(USDT, usdt * 100, null);
    user.setPosition(BTC, btc * 1000, null);
    user.setPosition(BTC_USDT_F, btc_usdt_f * 100, null);

    InstrumentCache.get(USDT).setIndexFeedUsdMark(1.0);
    InstrumentCache.get(BTC).setIndexFeedUsdMark(10000.0);
    InstrumentCache.getPair(BTC_USDT_F).setIndexFeedUsdMark(9000.0);
    orderBook.setUsdMark(900000);
  }

  @Test
  public void marginWithoutPositions_WithCrossCollateral() {
    Context.setCrossCollateralEnabled(true);
    setPositions(0, 0, 0);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(0, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(0, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWithoutPositions_WithoutCrossCollateral() {
    Context.setCrossCollateralEnabled(false);
    setPositions(0, 0, 0);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(0, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(0, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWithPositions_USDT_WithCrossCollateral() {
    Context.setCrossCollateralEnabled(true);
    setPositions(1000, 0, 0);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(0, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(0, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWithPositions_USDT_WithoutCrossCollateral() {
    Context.setCrossCollateralEnabled(false);
    setPositions(1000, 0, 0);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(0, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(0, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWithPositions_BTC_WithCrossCollateral() {
    Context.setCrossCollateralEnabled(true);
    setPositions(0, 10, 0);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(0, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(0, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWithPositions_BTC_WithoutCrossCollateral() {
    Context.setCrossCollateralEnabled(false);
    setPositions(0, 10, 0);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(0, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(0, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWihPositions_USDT_BTC_WithCrossCollateral() {
    Context.setCrossCollateralEnabled(true);
    setPositions(1000, 10, 0);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(0, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(0, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWithPositions_USDT_BTC_WithoutCrossCollateral() {
    Context.setCrossCollateralEnabled(false);
    setPositions(1000, 10, 0);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(0, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(0, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWihPositions_F_WithCrossCollateral() {
    Context.setCrossCollateralEnabled(true);
    setPositions(0, 0, 10);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(681.25, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(1362.5, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWithPositions_F_WithoutCrossCollateral() {
    Context.setCrossCollateralEnabled(false);
    setPositions(0, 0, 10);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(681.25, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(1362.5, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWihPositions_USDT_F_WithCrossCollateral() {
    Context.setCrossCollateralEnabled(true);
    setPositions(1000, 0, 10);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(681.25, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(1362.5, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWithPositions_USDT_F_WithoutCrossCollateral() {
    Context.setCrossCollateralEnabled(false);
    setPositions(1000, 0, 10);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(681.25, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(1362.5, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWihPositions_BTC_F_WithCrossCollateral() {
    Context.setCrossCollateralEnabled(true);
    setPositions(0, 10, 10);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(681.25, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(1362.5, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWithPositions_BTC_F_WithoutCrossCollateral() {
    Context.setCrossCollateralEnabled(false);
    setPositions(0, 10, 10);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(681.25, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(1362.5, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWihPositions_USDT_BTC_F_WithCrossCollateral() {
    Context.setCrossCollateralEnabled(true);
    setPositions(1000, 10, 10);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(681.25, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(1362.5, user.getUsdMarginRequiredValue(), 0.01);
  }

  @Test
  public void marginWithPositions_USDT_BTC_F_WithoutCrossCollateral() {
    Context.setCrossCollateralEnabled(false);
    setPositions(1000, 10, 10);
    orderBook.getPreOrderCheck().updateRisk(user, null);
    Assert.assertEquals(681.25, user.getUsdMarginMaintValue(), 0.01);
    Assert.assertEquals(1362.5, user.getUsdMarginRequiredValue(), 0.01);
  }
}
