package com.solfini.util;

public class ResizingDisruptorTest {

  public static void main(String[] args) throws Exception {
    ResizingDisruptor disruptor = new ResizingDisruptor(64000);
    Thread producer = new Thread(new ResizingDisruptor.Producer(disruptor));
    // Thread producer2 = new Thread(new Producer(disruptor));
    Thread consumer = new Thread(new ResizingDisruptor.Consumer(disruptor));
    producer.start();
    // producer2.start();
    // Thread.sleep(1);
    consumer.start();

    long start = TimeUtil.getTime();
    producer.join();
    consumer.join();

    System.out.println("done in " + ((TimeUtil.getTime() - start)) / 1000000000F);
  }
}
