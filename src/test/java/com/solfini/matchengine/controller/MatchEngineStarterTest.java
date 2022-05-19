package com.solfini.matchengine.controller;

import com.solfini.matchengine.kafka.KafkaListener;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.util.PropertyReader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MatchEngineStarterTest {

  private static final String TOPIC = "test";
  private KafkaListener listener;
  private Thread thread;

  @Before
  public void before() {
    try {
      PropertyReader.initialize(getClass().getResourceAsStream("/KafkaControllerTest.properties"), null);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  private void publish() {
    KafkaPublisher publisher = new KafkaPublisher(0);
    byte[] data = new byte[200];
    for (int i = 0; i < 1000; ++i) {
      publisher.sendDirect(TOPIC, data, (byte)0);
    }
  }

  private void start() {
    listener = new KafkaListener(TOPIC);
    thread = new Thread(listener);
    thread.start();
  }

  private void stop() {
    try {
      Thread.sleep(100);
      listener.shutdown();
      thread.join();
    } catch (InterruptedException e) {
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void publishBeforeListening() {
    publish();
    start();
    stop();
  }

  @Test
  public void publishWhileListening() {
    start();
    publish();
    stop();
  }
}
