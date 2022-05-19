package com.solfini.matchengine.message;

import java.util.Properties;

import com.solfini.common.Context;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.matchengine.drmode.DecoderThreadCache;
import com.solfini.matchengine.drmode.KafkaDRFixListener;
import com.solfini.matchengine.drmode.message.DRExecutionReport;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

import org.junit.*;

public class ExecutionReportDecodeTest extends MessageTest {

  @BeforeClass
  public static void before() {
    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
      properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
      properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
      PropertyReader.initialize(null, properties);

      synchronized (properties) {
        createInstruments();
      }

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    DecoderThreadCache.start();
  }

  @AfterClass
  public static void after() {
    DecoderThreadCache.shutdown();
  }

  @Ignore
  @Test
  public void decodeExecutionReportForTradeWithFees() {
    byte[] data = new byte[] { 0, 0, 0, 0, 0, 0, 7, 100, 21, -76, -22, -38, -30, 25, -83, 3, 2, 56, 61, 70, 73, 88, 46, 52, 46, 52, 1, 57, 61, 52, 49, 48, 1,
      51, 53, 61, 56, 1, 52, 57, 61, 109, 101, 48, 49, 1, 53, 54, 61, 49, 48, 48, 48, 48, 48, 48, 48, 48, 57, 1, 49, 50, 56, 61, 49, 1, 51, 52, 61, 49, 54, 51,
      53, 1, 53, 48, 61, 49, 48, 1, 49, 52, 50, 61, 50, 57, 55, 53, 53, 48, 51, 49, 57, 1, 53, 50, 61, 50, 48, 49, 57, 48, 55, 50, 54, 45, 48, 57, 58, 50, 54,
      58, 51, 54, 46, 52, 50, 49, 1, 51, 55, 61, 49, 48, 48, 49, 53, 53, 51, 50, 56, 56, 55, 54, 51, 1, 49, 57, 56, 61, 49, 48, 48, 48, 48, 48, 48, 48, 54, 50,
      52, 49, 55, 1, 53, 50, 55, 61, 49, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 49, 1, 49, 49, 61, 51, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 1, 51,
      56, 50, 61, 49, 1, 51, 51, 55, 61, 56, 1, 49, 55, 61, 49, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 49, 1, 49, 53, 48, 61, 70, 1, 51, 57, 61, 49, 1, 49,
      61, 49, 48, 48, 48, 48, 48, 48, 48, 49, 56, 1, 53, 53, 61, 83, 80, 89, 47, 85, 83, 68, 84, 91, 70, 93, 1, 52, 56, 61, 49, 48, 48, 48, 48, 48, 48, 48, 49,
      52, 1, 53, 52, 61, 49, 1, 51, 56, 61, 50, 1, 52, 48, 61, 49, 1, 52, 52, 61, 48, 1, 53, 57, 61, 49, 1, 51, 50, 61, 49, 46, 56, 48, 48, 48, 48, 48, 1, 51,
      49, 61, 50, 56, 57, 46, 50, 1, 49, 53, 49, 61, 46, 50, 48, 48, 48, 48, 48, 1, 49, 52, 61, 49, 46, 56, 48, 48, 48, 48, 48, 1, 54, 61, 50, 56, 46, 57, 50,
      1, 52, 50, 55, 61, 48, 1, 54, 48, 61, 50, 48, 49, 57, 48, 55, 50, 54, 45, 48, 57, 58, 50, 54, 58, 51, 54, 46, 52, 48, 48, 1, 49, 50, 61, 45, 46, 55, 56,
      1, 49, 51, 61, 50, 1, 52, 55, 57, 61, 49, 1, 57, 50, 49, 61, 50, 50, 50, 51, 48, 48, 53, 46, 55, 51, 1, 57, 50, 50, 61, 52, 48, 48, 48, 46, 48, 48, 1, 49,
      49, 57, 61, 48, 1, 49, 50, 48, 61, 49, 1, 52, 57, 52, 61, 49, 1, 49, 48, 61, 48, 48, 48, 1};

    KafkaDRFixListener listener = null;
    try {
      listener = new KafkaDRFixListener(KafkaDRFixListener.LOAD_STRATEGY_NONE);
      listener.onMessage(0, 0, 0, KafkaPublisher.NORMAL_API, data);
      Thread.sleep(1000);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
      return;
    }

    final ManyToOneConcurrentArrayQueueCustom<Message> queue = Context.getReceiverToMatcherQueue();
    Message message = queue.remove();
    Assert.assertNotNull(message);
    Assert.assertTrue(message instanceof DRExecutionReport);

    DRExecutionReport executionReport = (DRExecutionReport) message;
    Assert.assertNotNull(executionReport);
    System.out.println(executionReport);
    Assert.assertEquals("DRExecutionReport [securityId=14, clOrdId=[3], symbol=null, side=BUY, ordType=MARKET, account=[1, 8], " +
      "orderId=1553288763, secondaryOrderId=62417, execId=1, secondaryExecId=1, feeQty=-780000, timeInForce=1, expireTime=null, " +
      "qtyInOrderbook=200000, feeInstrumentId=1, isPaidToInsurance=false, execType=TRADE, ordStatus=null, sourceSeqNum=0, " +
      "sourceSendTime=0]", executionReport.toString());
  }
}
