package com.solfini.matchengine.model.orderbook;

import java.util.Random;

import com.solfini.internal.admin.schema.TokenType;
import com.solfini.sbe.encoder.Side;
import org.junit.Test;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookStopLimitOrderInfiniteTest extends OrderBookTest {

  @Test
  public void infiniteLoopOnTrigger() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    Random random = new Random();
    for (int i = 0; i < 1000; i++) {
      orderBook.addOrder(
          createOrder(i, user, BTC_USDT_F, 8000 + random.nextInt(5000), random.nextInt(10000), i % 2 == 0 ? Side.BUY : Side.SELL, DAY));
    }
    for (int i = 0; i < 1000; i++) {
      orderBook.addOrder(
          createStopLimitOrder(1000 + i, user, BTC_USDT_F, 8000, 8000, random.nextInt(10000), i % 2 == 0 ? Side.BUY : Side.SELL, DAY));
    }
    orderBook.addOrder(createOrder(2000, user, BTC_USDT_F, 8000, 10000, Side.BUY, DAY));
    clearMessages();
  }

  @Test
  public void infiniteLoop_BS_TriggerByBuyOrder() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    orderBook.addOrder(createStopLimitOrder(100, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(101, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createOrder(201, user, BTC_USDT_F, 8000, 10000, Side.SELL, DAY));

    expectMessage("orderId=100, ordType=STOP_LIMIT, side=BUY, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, ordType=STOP_LIMIT, side=SELL, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=201, ordType=LIMIT, side=SELL, price=8000, orderQty=10000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(200, user, BTC_USDT_F, 8000, 10000, Side.BUY, DAY));
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=10000, ordStatus=NEW");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=10000, ordStatus=FILLED");
    expectMessage("orderId=201, ordType=LIMIT, side=SELL, price=8000, orderQty=10000, ordStatus=FILLED");

    expectMessage("orderId=100, ordType=STOP_LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=100, origOrderId=100, ordType=LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, ordType=STOP_LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=101, origOrderId=101, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, origOrderId=101, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=FILLED");
    expectMessage("orderId=100, ordType=LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=FILLED");
    assertMessages();
  }

  @Test
  public void infiniteLoop_BSB_TriggerByBuyOrder() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    orderBook.addOrder(createStopLimitOrder(100, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(101, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createStopLimitOrder(102, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(200, user, BTC_USDT_F, 8000, 11000, Side.BUY, DAY));

    expectMessage("orderId=100, ordType=STOP_LIMIT, side=BUY, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, ordType=STOP_LIMIT, side=SELL, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=102, ordType=STOP_LIMIT, side=BUY, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(201, user, BTC_USDT_F, 8000, 1000, Side.SELL, DAY));
    expectMessage("orderId=201, ordType=LIMIT, side=SELL, price=8000, orderQty=1000, ordStatus=NEW");
    expectMessage("orderId=201, ordType=LIMIT, side=SELL, price=8000, orderQty=1000, ordStatus=FILLED");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=100, ordType=STOP_LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=100, origOrderId=100, ordType=LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=102, ordType=STOP_LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=102, origOrderId=102, ordType=LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, origOrderId=0, ordType=STOP_LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=101, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=NEW");

    expectMessage("orderId=101, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=FILLED");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  @Test
  public void infiniteLoop_BSS_TriggerByBuyOrder() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    orderBook.addOrder(createStopLimitOrder(100, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(101, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createStopLimitOrder(102, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createOrder(200, user, BTC_USDT_F, 8000, 11000, Side.BUY, DAY));

    expectMessage("orderId=100, ordType=STOP_LIMIT, side=BUY, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, ordType=STOP_LIMIT, side=SELL, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=102, ordType=STOP_LIMIT, side=SELL, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(201, user, BTC_USDT_F, 8000, 1000, Side.SELL, DAY));
    expectMessage("orderId=201, ordType=LIMIT, side=SELL, price=8000, orderQty=1000, ordStatus=NEW");
    expectMessage("orderId=201, ordType=LIMIT, side=SELL, price=8000, orderQty=1000, ordStatus=FILLED");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=100, ordType=STOP_LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=100, origOrderId=100, ordType=LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, ordType=STOP_LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=101, origOrderId=101, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=FILLED");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=102, ordType=STOP_LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=102, origOrderId=102, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=102, origOrderId=102, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=FILLED");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=FILLED");
    assertMessages();
  }

  @Test
  public void infiniteLoop_SB_TriggerByBuyOrder() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    orderBook.addOrder(createStopLimitOrder(101, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createStopLimitOrder(100, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(102, user, BTC_USDT_F, 8000, 11000, Side.BUY, DAY));

    expectMessage("orderId=101, ordType=STOP_LIMIT, side=SELL, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=100, ordType=STOP_LIMIT, side=BUY, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=102, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(103, user, BTC_USDT_F, 8000, 1000, Side.SELL, DAY));
    expectMessage("orderId=103, ordType=LIMIT, side=SELL, price=8000, orderQty=1000, ordStatus=NEW");
    expectMessage("orderId=103, ordType=LIMIT, side=SELL, price=8000, orderQty=1000, ordStatus=FILLED");
    expectMessage("orderId=102, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=100, ordType=STOP_LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=100, origOrderId=100, ordType=LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, ordType=STOP_LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=101, origOrderId=101, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, origOrderId=101, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=FILLED");
    expectMessage("orderId=102, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  @Test
  public void infiniteLoop_SBB_TriggerByBuyOrder() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    orderBook.addOrder(createStopLimitOrder(100, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createStopLimitOrder(101, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(102, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(200, user, BTC_USDT_F, 8000, 11000, Side.BUY, DAY));

    expectMessage("orderId=100, ordType=STOP_LIMIT, side=SELL, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, ordType=STOP_LIMIT, side=BUY, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=102, ordType=STOP_LIMIT, side=BUY, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(201, user, BTC_USDT_F, 8000, 1000, Side.SELL, DAY));
    expectMessage("orderId=201, ordType=LIMIT, side=SELL, price=8000, orderQty=1000, ordStatus=NEW");
    expectMessage("orderId=201, ordType=LIMIT, side=SELL, price=8000, orderQty=1000, ordStatus=FILLED");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=101, ordType=STOP_LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=101, origOrderId=101, ordType=LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=102, ordType=STOP_LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=102, origOrderId=102, ordType=LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=100, ordType=STOP_LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=100, origOrderId=100, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=100, origOrderId=100, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=FILLED");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=PARTIALLY_FILLED");
    assertMessages();
  }

  @Test
  public void infiniteLoop_SBS_TriggerByBuyOrder() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    orderBook.addOrder(createStopLimitOrder(100, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createStopLimitOrder(101, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(102, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createOrder(200, user, BTC_USDT_F, 8000, 11000, Side.BUY, DAY));

    expectMessage("orderId=100, ordType=STOP_LIMIT, side=SELL, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=101, ordType=STOP_LIMIT, side=BUY, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=102, ordType=STOP_LIMIT, side=SELL, price=8000, stopPx=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(201, user, BTC_USDT_F, 8000, 1000, Side.SELL, DAY));
    expectMessage("orderId=201, ordType=LIMIT, side=SELL, price=8000, orderQty=1000, ordStatus=NEW");
    expectMessage("orderId=201, ordType=LIMIT, side=SELL, price=8000, orderQty=1000, ordStatus=FILLED");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=PARTIALLY_FILLED");

    expectMessage("orderId=101, ordType=STOP_LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=101, origOrderId=101, ordType=LIMIT, side=BUY, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=100, ordType=STOP_LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=100, origOrderId=100, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=100, origOrderId=100, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=FILLED");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=PARTIALLY_FILLED");
    expectMessage("orderId=102, ordType=STOP_LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=EXPIRED");
    expectMessage("orderId=102, origOrderId=102, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=NEW");
    expectMessage("orderId=102, origOrderId=102, ordType=LIMIT, side=SELL, price=8000, orderQty=5000, ordStatus=FILLED");
    expectMessage("orderId=200, ordType=LIMIT, side=BUY, price=8000, orderQty=11000, ordStatus=FILLED");
    assertMessages();
  }

  @Test
  public void infiniteLoopOnTriggerBySellOrder() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    orderBook.addOrder(createStopLimitOrder(1, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(2, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user, BTC_USDT_F, 8000, 10000, Side.SELL, DAY));
  }

  @Test
  public void infiniteLoopOnTriggerByBuyMarketOrder() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    orderBook.addOrder(createStopLimitOrder(1, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(2, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createMarketOrder(3, user, BTC_USDT_F, 10000, Side.BUY, DAY));
  }

  @Test
  public void infiniteLoopOnTriggerBySellMarketOrder() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    orderBook.addOrder(createStopLimitOrder(1, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(2, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createMarketOrder(3, user, BTC_USDT_F, 10000, Side.SELL, DAY));
  }

  @Test
  public void infiniteLoopOnTriggerByBuyMultipleOrder() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    orderBook.addOrder(createStopLimitOrder(1, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(2, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createStopLimitOrder(3, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(4, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));

    orderBook.addOrder(createOrder(5, user, BTC_USDT_F, 700000, 10000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(6, user, BTC_USDT_F, 900000, 10000, Side.SELL, DAY));

    orderBook.addOrder(createOrder(7, user, BTC_USDT_F, 8000, 10000, Side.BUY, DAY));
  }

  @Test
  public void infiniteLoopOnTriggerBySellMultipleOrder() {
    orderBook.clearOrderBook();

    user.setPosition(USDT, 100_000_000_00L, null, 0, null, TokenType.ERC20);
    orderBook.addOrder(createStopLimitOrder(1, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(2, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));
    orderBook.addOrder(createStopLimitOrder(3, user, BTC_USDT_F, 8000, 8000, 5000, Side.BUY, DAY));
    orderBook.addOrder(createStopLimitOrder(4, user, BTC_USDT_F, 8000, 8000, 5000, Side.SELL, DAY));

    orderBook.addOrder(createOrder(5, user, BTC_USDT_F, 700000, 10000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(6, user, BTC_USDT_F, 900000, 10000, Side.SELL, DAY));

    orderBook.addOrder(createOrder(7, user, BTC_USDT_F, 8000, 10000, Side.SELL, DAY));
  }



  public static void main(String[] args) {
    OrderBookStopLimitOrderInfiniteTest test = new OrderBookStopLimitOrderInfiniteTest();
    System.out.println("before");
    test.before();
    System.out.println("after");
    System.out.println("testStopLimit done");
  }
}
