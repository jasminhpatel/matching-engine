package com.solfini.matchengine.model.risk;

import com.solfini.instrument.Balance;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.model.orderbook.OrderBookTest;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.preordercheck.CashPreOrderCheck;
import com.solfini.user.User;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.solfini.sbe.encoder.Side;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static com.solfini.sbe.encoder.TimeInForce.DAY;

@RunWith(JUnitParamsRunner.class)
public class CashPreOrderCheckTest extends OrderBookTest {
  int userId = 3000;
  CashPreOrderCheck preOrderCheck = null;

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(ETH, UpdateType.PUT, "ETH", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 2));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + ETH + ", symbol=ETH");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + ETH + ", symbol=ETH");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F]");

    pair = InstrumentCache.getPair(BTC_USDT_F);

    orderBook = new OrderBookTest.OrderBookWrapper(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.CASH_PREORDER_CHECK, pair);
    (orderBook.orderBook()).setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));

    pair.setOrderBook(orderBook.orderBook());

    Field field = null;
    try {
      field = orderBook.orderBook().getClass().getDeclaredField("preOrderCheck");
    } catch (NoSuchFieldException e) {
      e.printStackTrace();
    }

    try {
      if (Modifier.isPrivate(field.getModifiers())) {
        field.setAccessible(true);
        preOrderCheck = (CashPreOrderCheck) field.get(orderBook.orderBook());
      }
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }

    preOrderCheck.updateRiskAndCalcBankruptcyPrices(null, null);
    preOrderCheck.updateRisk(null, null);
    Assert.assertNotNull(preOrderCheck);
  }

  // required quote positions 1011 * 1000
  // required fee 10
  long minBalanceToBuy = 1011 * 10000;
  long lessThanMinBalanceToBuy = 1011 * (10000 - 1);
  long minBalanceToSell = 1000000;
  long lessThanMinBalanceToSell = 1000000 - 1;
  long requiredfeeQty = 10110;
  long lessThanRequiredFeeQty = 10110 - 3;

  private Object[] checkOrderTestValues() {
    return new Object[] {
        // fee deducted from base position
        new Object[] {Side.SELL, BTC, true, requiredfeeQty + minBalanceToSell, 0, 0, false}, // Enough balance to deduct fee and amount
        new Object[] {Side.SELL, BTC, true, minBalanceToSell, 0, 0, true}, // Enough balance to deduct amount but not fee
        new Object[] {Side.SELL, BTC, true, requiredfeeQty, 0, 0, true}, // Enough balance to deduct fee but not amount
        new Object[] {Side.SELL, BTC, true, lessThanMinBalanceToSell, 0, 0, true}, // Not enough balance to deduct amount
        new Object[] {Side.SELL, BTC, true, lessThanRequiredFeeQty, 0, 0, true}, // Not enough balance to deduct fee
        new Object[] {Side.SELL, BTC, true, lessThanMinBalanceToSell + lessThanRequiredFeeQty, 0, 0, true}, // Not enough balance to deduct
                                                                                                          // fee

        // fee deducted from quote position
        new Object[] {Side.BUY, USDT, true, 0, minBalanceToBuy + requiredfeeQty, 0, false}, // Enough balance to deduct amount amount
        new Object[] {Side.BUY, USDT, true, 0, lessThanMinBalanceToBuy, 0, true}, // not enough balance to deduct amount

        // fee deducted from neither from base or quote position
        new Object[] {Side.SELL, ETH, true, minBalanceToSell, 0, requiredfeeQty, false}, // Enough balance to deduct fee and amount
        new Object[] {Side.SELL, ETH, true, minBalanceToSell, 0, lessThanRequiredFeeQty, true}, // Enough balance to deduct amount but not
                                                                                               // fee
        new Object[] {Side.SELL, ETH, true, lessThanMinBalanceToSell, 0, requiredfeeQty, true}, // Enough balance to deduct amount
        new Object[] {Side.SELL, ETH, true, minBalanceToSell, 0, lessThanRequiredFeeQty, true}, // Not enough balance to deduct fee // Not
                                                                                               // enough balance to deduct amount
        new Object[] {Side.SELL, ETH, true, lessThanRequiredFeeQty, 0, 0, true}, // Not enough balance to deduct fee

        // fee deducted from neither from base or quote position
        new Object[] {Side.BUY, ETH, true, 0, minBalanceToBuy, requiredfeeQty, false}, // Enough balance to deduct amount amount and fee
        new Object[] {Side.BUY, ETH, true, 0, lessThanMinBalanceToBuy, requiredfeeQty, true}, // not enough balance to deduct amount
        new Object[] {Side.BUY, ETH, true, 0, lessThanMinBalanceToBuy, lessThanRequiredFeeQty, true}, // not enough balance to deduct amount
                                                                                                     // or fee
        ///
        // fee deducted from base position
        new Object[] {Side.SELL, BTC, false, requiredfeeQty + minBalanceToSell, 0, 0, false}, // Enough balance to deduct fee and amount
        new Object[] {Side.SELL, BTC, false, minBalanceToSell, 0, 0, false}, // Enough balance to deduct amount but not fee -
        new Object[] {Side.SELL, BTC, false, requiredfeeQty, 0, 0, true}, // Enough balance to deduct fee but not amount
        new Object[] {Side.SELL, BTC, false, lessThanMinBalanceToSell, 0, 0, true}, // Not enough balance to deduct amount
        new Object[] {Side.SELL, BTC, false, lessThanRequiredFeeQty, 0, 0, true}, // Not enough balance to deduct fee
        new Object[] {Side.SELL, BTC, false, lessThanMinBalanceToSell + lessThanRequiredFeeQty, 0, 0, false}, // Not enough balance to deduct
                                                                                                            // fee -

        // fee deducted from quote position
        new Object[] {Side.BUY, USDT, false, 0, minBalanceToBuy, 0, true}, // Enough balance to deduct amount amount -
        new Object[] {Side.BUY, USDT, false, 0, lessThanMinBalanceToBuy, 0, true}, // not enough balance to deduct amount -

        // fee deducted from neither from base or quote position
        new Object[] {Side.SELL, ETH, false, minBalanceToSell, 0, requiredfeeQty, false}, // Enough balance to deduct fee and amount
        new Object[] {Side.SELL, ETH, false, minBalanceToSell, 0, lessThanRequiredFeeQty, false}, // Enough balance to deduct amount but not
                                                                                                 // fee
        new Object[] {Side.SELL, ETH, false, lessThanMinBalanceToSell, 0, requiredfeeQty, true}, // Enough balance to deduct amount
        new Object[] {Side.SELL, ETH, false, minBalanceToSell, 0, lessThanRequiredFeeQty, false}, // Not enough balance to deduct fee // Not
                                                                                                 // enough balance to deduct amount
        new Object[] {Side.SELL, ETH, false, lessThanRequiredFeeQty, 0, 0, true}, // Not enough balance to deduct fee -

        // fee deducted from neither from base or quote position
        new Object[] {Side.BUY, ETH, false, 0, minBalanceToBuy, requiredfeeQty, true}, // Enough balance to deduct amount amount and fee -
        new Object[] {Side.BUY, ETH, false, 0, lessThanMinBalanceToBuy, requiredfeeQty, true}, // not enough balance to deduct amount
        new Object[] {Side.BUY, ETH, false, 0, lessThanMinBalanceToBuy, lessThanRequiredFeeQty, true}, // not enough balance to deduct amount
                                                                                                      // or fee
    };
  }

  private void setOrderSubmitExpectation(Side side, boolean expectReject) {
    if (expectReject) {
      expectMessage("ExecutionReportMessage", "securityId=12, clOrdId=ClOrdId, side=" + side.name() + ", ordType=LIMIT, account=" + userId
          + ", orderId=1, execId=0, orderQty=100000, leavesQty=0, cumQty=0, price=1011, price2=0, avgPx=0, lastPx=0, lastQty=0, stopPx=0, timeInForce=DAY, execType=NEW, execRestatementReason=null, ordStatus=REJECTED, basePositionId=0, basePositionQuantity=0, basePositionQuantityChange=0, quotedPositionId=0, quotedPositionQuantity=0, quotedPositionQuantityChange=0, feePositionId=0, feePositionQuantity=0, feePositionQuantityChange=0, unrealizedUsd=0.0, realizedUsd=0.0, avgCostBasisUsd=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0, secondaryOrderId=0");
      expectMessage("BusinessRejectMessage",
          "businessRejectReason=FAILED_PRE_CREDIT_CHECK, text=Failed pre-credit check, refMsgType=ORDER_SINGLE, businessRejectRefID=1, orderId=1");

    } else {
      expectMessage("ExecutionReportMessage",
          "orderId=1, ordType=LIMIT, side=" + side.name() + ", price=1011, orderQty=100000, leavesQty=100000, ordStatus=NEW");
    }
    assertMessages();
  }

  private void setFee(boolean setFee, User user, int feeInstrument) {
    if (setFee) {
      pair.setFee(new Fee(pair.getId(), feeInstrument, 10, FeeType.PERCENT, MakerTaker.ALL, 0, true));
      user.setFeeTier(0);
    }
  }

  private int setFeeInstrument(Side side, int feeInstrumentId) {
    if (side.name().equals("SELL") && (feeInstrumentId == pair.getBaseId())) {
      return pair.getBaseId();
    } else if (side.name().equals("BUY") && feeInstrumentId == pair.getQuotedId()) {
      return pair.getQuotedId();
    } else {
      return feeInstrumentId;
    }
  }

  @Test
  @Parameters(method = "checkOrderTestValues")
  public void checkOrderTest(Side side, int feeInstrumentId, boolean setFee, long btcPosition, long usdtPosition, long btcusdtPosition,
      boolean expectReject) {
    int feeInstrument = setFeeInstrument(side, feeInstrumentId);

    User user = createUser(userId, new Balance(BTC, btcPosition, 2, 0, 0), new Balance(USDT, usdtPosition, 2, 0, 0),
        new Balance(BTC_USDT_F, btcusdtPosition, 2, 0, 0), new Balance(ETH, btcusdtPosition, 2, 0, 0));
    user.getPosition(BTC_USDT_F).getUserOpenOrdersByPair().set(user, InstrumentCache.getPair(BTC_USDT_F));
    expectMessage("UserAdminMessage", "userId=" + userId);
    assertMessages();

    setFee(setFee, user, feeInstrument);

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 1000000, side, DAY));
    setOrderSubmitExpectation(side, expectReject);
  }

}
