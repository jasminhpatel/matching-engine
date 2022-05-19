package com.solfini.matchengine.model.orderbook;

import java.io.IOException;
import java.util.Properties;
import org.slf4j.event.Level;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.matchengine.TimeTriggerThread;
import com.solfini.matchengine.message.admin.FundingRateCalcMessage;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

/**
 *
 * @author Chris Mack
 *
 */
public class TimeTriggerThreadTest extends ModelTest {

  protected void configure() {
    LogLevel.setLevel(Level.TRACE);

    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "4194304");
    properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "4028");
    properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "1024");
    properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_POOL_START_CAPACITY", "32768");
    properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "32768");
    properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "32768");
    properties.setProperty("NUM_ENCODER_THREADS", "0");
    properties.setProperty("NUM_DECODER_THREADS", "8");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    // onConfigure(properties);

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testTimeTriggerThread() {
    TimeTriggerThread timeTriggerThread = new TimeTriggerThread(new NoOpIdleStrategy());


    FundingRateCalcMessage message = new FundingRateCalcMessage();
    message.setTriggerTimeMillis(System.currentTimeMillis());
    boolean rc = timeTriggerThread.registerMessage(message);
    Assert.assertEquals(false, rc);

    message.setTriggerTimeMillis(30_000 + System.currentTimeMillis());
    rc = timeTriggerThread.registerMessage(message);
    Assert.assertEquals(true, rc);

    FundingRateCalcMessage message2 = new FundingRateCalcMessage();
    message2.setTriggerTimeMillis(32_000 + System.currentTimeMillis());
    rc = timeTriggerThread.registerMessage(message2);
    Assert.assertEquals(true, rc);

    assertMessages();
  }

}
