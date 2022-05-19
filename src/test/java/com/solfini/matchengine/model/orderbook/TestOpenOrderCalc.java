package com.solfini.matchengine.model.orderbook;

import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.matchengine.orderbook.OrderBookFactory;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserOpenOrdersByPair;
import org.junit.Test;

public class TestOpenOrderCalc extends OrderBookTest {

  @Test
  public void testAddOrderToBookBidSide() {
    final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000);
    usdt.setIndexFeedUsdMark(1);
    InstrumentCache.addInstrument(usdt);

    InstrumentPair pair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, usdt, (short) 2, (short) 6, 2, AssetType.PAIR, 5_00, 10_00, 260, 0);

    OrderBook orderBook = OrderBookFactory.create(OrderBookFactory.ARRAY_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
    pair.setOrderBook(orderBook);
    InstrumentCache.addPair(pair);

    User user = new User(18);
    Position position = user.setPosition(1, 10_000_000_000_000L);
    Position position2 = user.setPosition(14, 20_000_000);
    UserOpenOrdersByPair userOpenOrdersByPair = position2.getUserOpenOrdersByPair();
    MarginPreOrderCheckAndSettle marginPreOrderCheck = new MarginPreOrderCheckAndSettle();
    // position.getUserOpenOrdersByPair()

    marginPreOrderCheck.updateRisk(user, null);

    // position
    System.out.println("test0 userOpenOrdersByPair=" + userOpenOrdersByPair);

    Order buyOrder = new Order();
    buyOrder.setUser(user);
    buyOrder.setOrderId(1011);
    buyOrder.setClOrdId("1");
    buyOrder.setSecurityId(14);
    buyOrder.setSide(Side.BUY);
    buyOrder.setPriceInt(5000_00);
    buyOrder.setPrice(5000, (short) 2);
    buyOrder.setQuantityLong(10_000_000);
    buyOrder.setQuantityOrigLong(10_000_000);
    buyOrder.setQty(10_000_000, (short) 6);

    boolean accepted = marginPreOrderCheck.checkOrder(buyOrder, buyOrder.getPriceInt());

    // new order
    System.out.println("test1 accepted=" + accepted + ", userOpenOrdersByPair=" + userOpenOrdersByPair);

    // cancel order
    marginPreOrderCheck.updateCancel(buyOrder);

    System.out.println("test2 accepted=" + accepted + ", userOpenOrdersByPair=" + userOpenOrdersByPair);

    Order buyOrder2 = new Order();
    buyOrder2.setUser(user);
    buyOrder2.setOrderId(1011);
    buyOrder2.setClOrdId("1");
    buyOrder2.setSecurityId(14);
    buyOrder2.setSide(Side.BUY);
    buyOrder2.setPriceInt(5000_00);
    buyOrder2.setPrice(5000, (short) 2);
    buyOrder2.setQuantityLong(10_000_000);
    buyOrder2.setQuantityOrigLong(10_000_000);
    buyOrder2.setQty(10_000_000, (short) 6);

    boolean accepted2 = marginPreOrderCheck.checkOrder(buyOrder2, buyOrder2.getPriceInt());

    // new order
    System.out.println("test3 accepted=" + accepted2 + ", userOpenOrdersByPair=" + userOpenOrdersByPair);

    // partial fill order
    int referencePrice = 5000_00;
    long referenceQuantity = 2_000_000;
    ExecutionReportMessage execReport = new ExecutionReportMessage();
    execReport.setUser(user);
    double quotedUsdMark = 1;
    double settleCoinUsdMark = 1;
    double quotedCoinUsdMark = 1;
    boolean isMaker = false;
    Order causingMessage = buyOrder2;
    int counterpartyId = 27;

    buyOrder2.setQuantityLong(buyOrder2.getQuantityLong() - referenceQuantity);
    marginPreOrderCheck.updateFill(buyOrder2, referencePrice, referenceQuantity, execReport, quotedUsdMark, settleCoinUsdMark,
        quotedCoinUsdMark, isMaker, causingMessage, counterpartyId);

    System.out.println("test4 accepted=" + accepted + ", userOpenOrdersByPair=" + userOpenOrdersByPair);

    Order buyOrder3 = new Order();
    buyOrder3.setUser(user);
    buyOrder3.setOrderId(1011);
    buyOrder3.setClOrdId("1");
    buyOrder3.setSecurityId(14);
    buyOrder3.setSide(Side.BUY);
    buyOrder3.setPriceInt(5000_00);
    buyOrder3.setPrice(5000, (short) 2);
    buyOrder3.setQuantityLong(1_000_000_000);
    buyOrder3.setQuantityOrigLong(1_000_000_000);
    buyOrder3.setQty(1_000_000_000, (short) 6);

    accepted = marginPreOrderCheck.checkOrder(buyOrder3, buyOrder3.getPriceInt());

    // new order
    System.out.println("test5 accepted=" + accepted + ", userOpenOrdersByPair=" + userOpenOrdersByPair);



    Order buyOrder4 = new Order();
    buyOrder4.setUser(user);
    buyOrder4.setOrderId(1011);
    buyOrder4.setClOrdId("1");
    buyOrder4.setSecurityId(14);
    buyOrder4.setSide(Side.BUY);
    buyOrder4.setPriceInt(5000_00);
    buyOrder4.setPrice(5000, (short) 2);
    buyOrder4.setQuantityLong(1_000_000_000);
    buyOrder4.setQuantityOrigLong(1_000_000_000);
    buyOrder4.setQty(1_000_000_000, (short) 6);

    accepted = marginPreOrderCheck.checkOrder(buyOrder4, buyOrder4.getPriceInt());

    // new order
    System.out.println("test6 accepted=" + accepted + ", userOpenOrdersByPair=" + userOpenOrdersByPair);

    Order buyOrder5 = new Order();
    buyOrder5.setUser(user);
    buyOrder5.setOrderId(1011);
    buyOrder5.setClOrdId("1");
    buyOrder5.setSecurityId(14);
    buyOrder5.setSide(Side.BUY);
    buyOrder5.setPriceInt(5000_00);
    buyOrder5.setPrice(5000, (short) 2);
    buyOrder5.setQuantityLong(1_000_000_000);
    buyOrder5.setQuantityOrigLong(1_000_000_000);
    buyOrder5.setQty(1_000_000_000, (short) 6);

    accepted = marginPreOrderCheck.checkOrder(buyOrder5, buyOrder5.getPriceInt());

    // new order
    System.out.println("test7 accepted=" + accepted + ", userOpenOrdersByPair=" + userOpenOrdersByPair);

  }


}
