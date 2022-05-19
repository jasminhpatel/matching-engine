package com.solfini.matchengine.model.orderbook;

import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.*;

public class OrderBookBestBidAskTest extends OrderBookTest {

  // Add buy orders
  @Test
  public void addBuyOrders() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY));

    expectMessage("ExecutionReportMessage", "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=0, bestAskPx=0, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1011, bestAskPx=0, bestPxScale=2");

        assertMessages();
  }

  // Add sell orders
  @Test
  public void addSellOrders() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    expectMessage("ExecutionReportMessage", "orderId=1, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=0, bestAskPx=0, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=0, bestAskPx=1012, bestPxScale=2");
    assertMessages();
  }

  // Add buy and sell orders
  @Test
  public void addOrders1() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1012, 500, Side.SELL, DAY));

    expectMessage("ExecutionReportMessage", "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=0, bestAskPx=0, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1011, bestAskPx=0, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1011, bestAskPx=1012, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=4, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1011, bestAskPx=1012, bestPxScale=2");

    assertMessages();
  }

  // Add buy and sell orders
  @Test
  public void addOrders2() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1013, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(5, user, pair.getId(), 1015, 500, Side.SELL, DAY));

    expectMessage("ExecutionReportMessage", "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=0, bestAskPx=0, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=2, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1011, bestAskPx=0, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=3, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1011, bestAskPx=1012, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=4, ordType=LIMIT, side=SELL, price=1013, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1011, bestAskPx=1012, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=5, ordType=LIMIT, side=SELL, price=1015, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1011, bestAskPx=1012, bestPxScale=2");

    assertMessages();
  }

  // Add buy and sell orders
  @Test
  public void addOrders3() {
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1013, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(5, user, pair.getId(), 1015, 500, Side.SELL, DAY));

    expectMessage("ExecutionReportMessage", "orderId=1, ordType=LIMIT, side=BUY, price=1010, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=0, bestAskPx=0, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=2, ordType=LIMIT, side=SELL, price=1013, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1010, bestAskPx=0, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1010, bestAskPx=1013, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=4, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1011, bestAskPx=1013, bestPxScale=2");
    expectMessage("ExecutionReportMessage", "orderId=5, ordType=LIMIT, side=SELL, price=1015, orderQty=500, leavesQty=500, ordStatus=NEW, "
        + "bestBidPx=1011, bestAskPx=1012, bestPxScale=2");

    assertMessages();
  }
}
