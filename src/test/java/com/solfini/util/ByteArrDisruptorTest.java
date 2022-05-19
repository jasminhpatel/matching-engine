package com.solfini.util;

public class ByteArrDisruptorTest {

  public static void main(String[] args) throws Exception {
    ByteArrDisruptor disruptor = ByteArrDisruptor.buildDisruptor(1024 * 64);
    Thread producer = new Thread(new ByteArrDisruptor.Producer(disruptor));
    // Thread producer2 = new Thread(new Producer(disruptor));
    Thread consumer = new Thread(new ByteArrDisruptor.Consumer(disruptor));
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
