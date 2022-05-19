package com.solfini.util;

public class DisruptorTest {
  public static void main(String args[]) throws Exception {
    Disruptor disruptor = Disruptor.buildDisruptor(1024 * 64);
    Thread producer = new Thread(new Disruptor.Producer(disruptor));
    // Thread producer2 = new Thread(new Producer(disruptor));
    Thread consumer = new Thread(new Disruptor.Consumer(disruptor));
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
