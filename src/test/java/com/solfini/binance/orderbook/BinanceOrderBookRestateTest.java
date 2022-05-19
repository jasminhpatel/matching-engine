package com.solfini.binance.orderbook;

import org.junit.Test;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.sbe.encoder.Side;

public class BinanceOrderBookRestateTest extends BinanceOrderBookTest {

  @Test
  public void restateOrderBook() {

    // limit orders
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1016, 50, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1014, 400, Side.BUY, DAY));
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1015, 300, Side.SELL, GOOD_TILL_CANCEL));
    orderBook.addOrder(createOrder(5, user, pair.getId(), 1012, 600, Side.BUY, DAY));
    orderBook.addOrder(createOrder(6, user, pair.getId(), 1016, 400, Side.SELL, DAY));
    orderBook.addOrder(createOrder(7, user, pair.getId(), 1017, 900, Side.SELL, DAY));
    orderBook.addOrder(createOrder(8, user, pair.getId(), 1012, 1500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(9, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    expectMessage("ExecutionReportMessage",
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=2, ordType=LIMIT, side=SELL, price=1016, orderQty=50, leavesQty=50, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=3, ordType=LIMIT, side=BUY, price=1014, orderQty=400, leavesQty=400, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=4, ordType=LIMIT, side=SELL, price=1015, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=5, ordType=LIMIT, side=BUY, price=1012, orderQty=600, leavesQty=600, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=6, ordType=LIMIT, side=SELL, price=1016, orderQty=400, leavesQty=400, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=7, ordType=LIMIT, side=SELL, price=1017, orderQty=900, leavesQty=900, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=8, ordType=LIMIT, side=BUY, price=1012, orderQty=1500, leavesQty=1500, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=9, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");

    // stop limit orders
    orderBook.addOrder(createStopLimitOrder(10, user, pair.getId(), 1013, 1014, 500, Side.SELL, DAY));
    orderBook.addOrder(createStopLimitOrder(11, user, pair.getId(), 1010, 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=10, ordType=STOP_LIMIT, side=SELL, price=1013, stopPx=1014, orderQty=500, ordStatus=NEW");
    expectMessage("orderId=11, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");

    // trade
    orderBook.addOrder(createOrder(12, user, pair.getId(), 1014, 900, Side.SELL, DAY));
    expectMessage("ExecutionReportMessage",
        "orderId=12, ordType=LIMIT, side=SELL, price=1014, orderQty=900, leavesQty=900, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=12, ordType=LIMIT, side=SELL, price=1014, orderQty=900, leavesQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("ExecutionReportMessage",
        "orderId=3, ordType=LIMIT, side=BUY, price=1014, orderQty=400, leavesQty=0, ordStatus=FILLED");

    // stop limit trigger
    expectMessage("orderId=10, ordType=STOP_LIMIT, side=SELL, price=1013, orderQty=500, ordStatus=EXPIRED");
    expectMessage("origOrderId=10, ordType=LIMIT, side=SELL, price=1013, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();

    // cancel
    orderBook.cancelOrder(createCancelOrder(13, 8, user, pair.getId(), 1012, 1500, Side.BUY, DAY));
    expectMessage("ExecutionReportMessage",
        "orderId=8, ordType=LIMIT, side=BUY, price=1012, orderQty=1500, leavesQty=1500, ordStatus=PENDING_CANCEL");
    expectMessage("ExecutionReportMessage",
        "orderId=8, ordType=LIMIT, side=BUY, price=1012, orderQty=1500, leavesQty=0, ordStatus=CANCELED");

    // restate
    orderBook.orderBook().changeState(MarketStatus.RESTATE, 0, null);
    expectMessage("ExecutionReportMessage", "orderId=0, ordType=PREVIOUSLY_INDICATED, execRestatementReason=OTHER");

    // buy orders
    expectMessage("ExecutionReportMessage",
        "orderId=9, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=5, ordType=LIMIT, side=BUY, price=1012, orderQty=600, leavesQty=600, ordStatus=NEW");

    // sell orders
    expectMessage("ExecutionReportMessage",
        "origOrderId=10, ordType=LIMIT, side=SELL, price=1013, orderQty=500, leavesQty=500, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=12, ordType=LIMIT, side=SELL, price=1014, orderQty=900, leavesQty=500, ordStatus=PARTIALLY_FILLED");
    expectMessage("ExecutionReportMessage",
        "orderId=4, ordType=LIMIT, side=SELL, price=1015, orderQty=300, leavesQty=300, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=2, ordType=LIMIT, side=SELL, price=1016, orderQty=50, leavesQty=50, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=6, ordType=LIMIT, side=SELL, price=1016, orderQty=400, leavesQty=400, ordStatus=NEW");
    expectMessage("ExecutionReportMessage",
        "orderId=7, ordType=LIMIT, side=SELL, price=1017, orderQty=900, leavesQty=900, ordStatus=NEW");

    // stop limits
    expectMessage("orderId=11, ordType=STOP_LIMIT, side=SELL, price=1010, stopPx=1012, orderQty=500, ordStatus=NEW");
    assertMessages();
  }
}
