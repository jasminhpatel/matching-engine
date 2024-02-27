package rnd;

import com.solfini.common.*;
import com.solfini.internal.schema.PayloadType;
import com.solfini.util.benchmark.RateBenchmark;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestThreadPool {
  private static final ManyToManyConcurrentArrayQueueCustom<Message> COPY_TRADE_QUEUE = new ManyToManyConcurrentArrayQueueCustom<>(1100000, "copyTradeQueue");
  private static final ManyToManyConcurrentArrayQueueCustom<Message> MATCHER_TO_PUBLISHER_QUEUE = new ManyToManyConcurrentArrayQueueCustom<>(1100000, "matcherToPublisherQueue");
  private static final int NO_OF_THREADS = 512 * 2 * 2 * 2 * 2 * 2 * 2 * 2;
  //private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(NO_OF_THREADS);
  private static final ExecutorService EXECUTOR_SERVICE = Executors.newVirtualThreadPerTaskExecutor();//.newFixedThreadPool(NO_OF_THREADS);

  public static void main(String[] args) {
    new Thread(() -> {
      RateBenchmark rateBenchmark = new RateBenchmark("Summary: " , 100000 *3);
      while (true) {
        try {
          final Message message = MATCHER_TO_PUBLISHER_QUEUE.poll();
          if (message instanceof CopyTrade) {
            rateBenchmark.sample();
          }
        } catch (Exception e) {
        }
      }
    }).start();
    for(int i = 1; i <= 1000000; i++) {
      //Order order = new Order();
      //order.setClOrdId(String.valueOf(i));

      final CopyTrade copyTrade = new CopyTrade();
      COPY_TRADE_QUEUE.addGuaranteed(copyTrade);
    }
    for(int i = 1; i <= NO_OF_THREADS; i++) {
      EXECUTOR_SERVICE.submit(new Router());
    }


  }

  public static class Router implements Runnable {
    @Override
    public void run() {
      while (true) {
        try {
          final Message message = COPY_TRADE_QUEUE.poll();

          if (message == null)
            continue;

          if (message instanceof CopyTrade) {
            final CopyTrade copyTrade = (CopyTrade) message;
            try {
              Thread.sleep(200);
              MATCHER_TO_PUBLISHER_QUEUE.addGuaranteed(copyTrade);
            } catch (Exception e) {}
          }

        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static class CopyTrade extends Message  {

    @Override
    public StringBuilder appendTo(StringBuilder s) {
      return null;
    }

    @Override
    public PayloadType getPayloadType() {
      return null;
    }

    @Override
    public MessageType getMessageType() {
      return null;
    }

    @Override
    public String toJSON() {
      return null;
    }
  }
}
