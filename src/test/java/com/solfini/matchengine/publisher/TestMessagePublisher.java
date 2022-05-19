package com.solfini.matchengine.publisher;

import com.solfini.common.*;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.PublisherThread;
import com.solfini.matchengine.kafka.TestKafkaPublisher;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.util.TimeUtil;

public class TestMessagePublisher {

  private static Order createOrder(int orderType, int price, int quantity) {
    int side = orderType == Constants.BUY_LIMIT ? 1 : 2;
    Order order = OrderObjectPool.get();
    order.setAccount(18);
    order.setUser(new User(18));
    order.setOrderId(10001);
    order.setType(orderType);
    order.setPriceInt(price);
    order.setQuantityLong(quantity);
    order.setClOrdId("clOrdId");
    order.setSecurityId(3);

    OrdType ordType = OrdType.LIMIT;
    Side sideType = side == 1 ? Side.BUY : Side.SELL;
    order.setPrice(price, (short) 0);
    order.setQty(quantity, (short) 0);
    order.setOrdType(ordType);
    order.setSide(sideType);
    return order;
  }

  public static void main(String[] args) throws InterruptedException {
    Context.setPublishMarketData(false);
    Context.setEncoderThreads(2);

    InstrumentPair instrumentPair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, null, (short) 2, (short) 2, 2, AssetType.PAIR, 250, 500, 260, 0);
    Order order = createOrder(Constants.BUY_LIMIT, 5000_00, 100);

    Message[] arr = new Message[500_000];
    for (int i = 0; i < 500_000; i++) {
      arr[i] = ExecutionReportMessage.createAckNewOrderExecutionReport(order, instrumentPair);
      arr[i].setSourceSeqNum(i);
    }

    MessagePublisher messagePublisher = new MessagePublisher();
    Context.setKafkaPublisher(new TestKafkaPublisher(1));

    PublisherThread publisherThread = new PublisherThread(IdleStrategyFactory.create(Context.getPublisherThreadIdle()));
    new Thread(publisherThread, "publisherThread").start();
    ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();

    System.out.println("starting...");

    long t0 = TimeUtil.getTime();
    for (int i = 0; i < 10000; i++)
      matcherToPublisherQueue.addGuaranteed(arr[i]);
    System.out.println("t0=" + (TimeUtil.getTime() - t0));

    t0 = TimeUtil.getTime();
    for (int i = 0; i < 500_000; i++)
      matcherToPublisherQueue.addGuaranteed(arr[i]);
    System.out.println("t0=" + (TimeUtil.getTime() - t0));


    Thread.sleep(1_000_000);
  }
}
