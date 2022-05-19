package com.solfini.matchengine.kafka;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestKafkaPublisher extends KafkaPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(TestKafkaPublisher.class);
  public static final byte NORMAL_API = (byte) 2;
  public static final byte ADMIN_API = (byte) 4;
  public static int counter = 0;
  public static long t0;
  public static AtomicInteger lastSeq = new AtomicInteger();


  public TestKafkaPublisher(final long startSeqNum) {
    super();
  }

  // offset=17
  @Override
  public void enqueueToSend(final String topic, final byte[] bytes, final byte messageType) {
    if (counter == 0) {
      t0 = System.currentTimeMillis();
    }
    counter++;
    if (counter == 299999) {
      System.out.println("published in " + (System.currentTimeMillis() - t0));
    }
    if (counter % 50_000 == 0)
      System.out.println("counter=" + counter + ", published in " + (System.currentTimeMillis() - t0));

  }

  @Override
  public long getLastSendOffset() {
    return 0;
  }

  public static void main(String args[]) {
    TestKafkaPublisher publisher = new TestKafkaPublisher(0);
    publisher.enqueueToSend("test1", new String("aaa").getBytes(), NORMAL_API);

    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 1_000_000; i++) {
      ProducerRecord<String, byte[]> record = new ProducerRecord<>("test1", new String("" + i).getBytes());
    }
    System.out.println("t2=" + (System.currentTimeMillis() - t0));

    t0 = System.currentTimeMillis();
    for (int i = 0; i < 1_000_000; i++) {
      ProducerRecord<String, byte[]> record = new ProducerRecord<>("test1", new String("" + i).getBytes());
    }
    System.out.println("t2=" + (System.currentTimeMillis() - t0));


    t0 = System.currentTimeMillis();
    for (int i = 0; i < 1_000_000; i++) {
      ProducerRecord<String, byte[]> record = new ProducerRecord<>("test1", new String("" + i).getBytes());
    }
    System.out.println("t2=" + (System.currentTimeMillis() - t0));
  }
}
