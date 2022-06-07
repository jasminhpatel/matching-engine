package com.solfini.matchengine.model.orderbook;

import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.*;

public class OrderBookCashPreOrderCheckMarketOrderTest extends OrderBookTest {

  @Override
  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 4, 3));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT, UpdateType.PUT, "BTC/USDT", BTC, USDT, 2, 2, CASH_PREORDER_CHECK));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT + ", symbol=BTC/USDT, updateType=PUT");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT + ", symbol=BTC/USDT");

    pair = InstrumentCache.getPair(BTC_USDT);

    orderBook = new OrderBookTest.OrderBookWrapper(OrderBookFactory.DEFAULT_TEST_ORDER_BOOK, OrderBookFactory.CASH_PREORDER_CHECK, pair);
    orderBook.orderBook().setSettleCoinUsdMarkInstrument(InstrumentCache.get(pair.getQuotedId()));

    pair.setOrderBook(orderBook.orderBook());
    pair.setIndexFeedUsdMark(100);
    pair.setFee(new Fee(pair.getId(), USDT, 1, FeeType.PERCENT, MakerTaker.ALL, 0, true));
  }

  @Override
  protected void createUsers() {
    super.createUsers();
    user.addPosition(BTC, 10_000_00, null);
    user2.addPosition(BTC, 10_000_00, null);
  }

  // Add buy order without enough funds, assert business reject
  @Test
  public void rejectBuyOrderWithoutFunds() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1000, 5000000, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=REJECTED");
    expectMessage("orderId=1, businessRejectReason=FAILED_PRE_CREDIT_CHECK");
    assertMessages();
  }

  // Add sell order without enough funds, assert business reject
  @Test
  public void rejectSellOrderWithoutFunds() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1000, 5000000, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=REJECTED");
    expectMessage("orderId=1, businessRejectReason=FAILED_PRE_CREDIT_CHECK");
    assertMessages();
  }

  // Add buy market order without enough funds, assert business reject
  @Test
  public void rejectBuyMarketOrderWithoutFundsWithoutLiquidity() {
    orderBook.addOrder(createMarketOrder(1, user, pair.getId(), 5000000, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=REJECTED");
    expectMessage("orderId=1, businessRejectReason=NO_LIQUIDITY_AVAILABLE");
    assertMessages();
  }

  // Add sell market order without enough funds, assert business reject
  @Test
  public void rejectSellMarketOrderWithoutFundsWithoutLiquidity() {
    orderBook.addOrder(createMarketOrder(1, user, pair.getId(), 5000000, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=REJECTED");
    expectMessage("orderId=1, businessRejectReason=NO_LIQUIDITY_AVAILABLE");
    assertMessages();
  }

  // Add buy market order without enough funds, assert business reject
  @Test
  public void rejectBuyMarketOrderWithoutFundsWithLiquidity() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1000, 5000, Side.SELL, DAY));
    expectMessage("orderId=1, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 5000000, Side.BUY, DAY));
    expectMessage("orderId=2, ordStatus=REJECTED");
    expectMessage("orderId=2, businessRejectReason=FAILED_PRE_CREDIT_CHECK");
    assertMessages();
  }

  // Add sell market order without enough funds, assert business reject
  @Test
  public void rejectSellMarketOrderWithoutFundsWithLiquidity() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1000, 5000, Side.BUY, DAY));
    expectMessage("orderId=1, ordStatus=NEW");

    orderBook.addOrder(createMarketOrder(2, user, pair.getId(), 5000000, Side.SELL, DAY));
    expectMessage("orderId=2, ordStatus=REJECTED");
    expectMessage("orderId=2, businessRejectReason=FAILED_PRE_CREDIT_CHECK");
    assertMessages();
  }
}
