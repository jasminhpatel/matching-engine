package com.solfini.matchengine.message;

import java.util.Properties;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.solfini.common.Context;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.drmode.DecoderThreadCache;
import com.solfini.matchengine.kafka.KafkaInputFixListener;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;

public class CancelOrderDecodeTest extends MessageTest {

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

  protected static void createInstruments() {
    final Instrument base = new Instrument(1, "BTC", "BTC", (short) 6, (short) 6, 3500, 1000, 0,false, 1);
    final Instrument quoted = new Instrument(2, "USD", "USD", (short) 6, (short) 6, 1, 1000, 0,false, 2);
    InstrumentCache.addInstrument(base);
    InstrumentCache.addInstrument(quoted);

    InstrumentCache
        .addPair(new InstrumentPair(3, "BTC/USD", "BTC/USD", base, quoted, (short) 6, (short) 6, 0, AssetType.PAIR, 10, 20, 3500, 0));
    InstrumentCache
        .addPair(new InstrumentPair(4, "BTC/USD", "BTC/USD", base, quoted, (short) 6, (short) 6, 0, AssetType.PAIR, 10, 20, 3500, 0));

    InstrumentCache
        .addPair(new InstrumentPair(14, "BTC/USD", "BTC/USD", base, quoted, (short) 6, (short) 6, 0, AssetType.PAIR, 10, 20, 3500, 0));
  }

  @Ignore
  @Test
  public void decodeCancelOrderWithSecondaryOrderId() {
    /*
     * byte[] data = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 56, 61, 70, 73, 88, 46, 52, 46, 52, 1, 57, 61, 49, 57,
     * 57, 1, 51, 53, 61, 70, 1, 52, 57, 61, 49, 48, 48, 48, 48, 48, 48, 48, 48, 57, 1, 53, 54, 61, 116, 101, 115, 116, 1, 51, 52, 61, 50,
     * 53, 1, 53, 50, 61, 50, 48, 49, 57, 48, 55, 50, 55, 45, 48, 52, 58, 50, 50, 58, 52, 54, 46, 54, 52, 57, 1, 52, 49, 61, 48, 32, 32, 32,
     * 32, 32, 32, 32, 32, 32, 32, 32, 1, 51, 55, 61, 49, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 1, 49, 49, 61, 49, 55, 53, 32, 32,
     * 32, 32, 32, 32, 32, 32, 32, 1, 53, 50, 54, 61, 49, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 54, 1, 49, 61, 49, 48, 48, 48, 48, 48, 48,
     * 48, 49, 48, 1, 53, 53, 61, 66, 84, 67, 85, 83, 68, 84, 1, 52, 56, 61, 52, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 1, 53, 52, 61,
     * 50, 1, 54, 48, 61, 50, 48, 49, 57, 48, 55, 50, 55, 45, 48, 52, 58, 50, 50, 58, 52, 54, 46, 54, 52, 57, 1, 51, 56, 61, 48, 1, 49, 48,
     * 61, 49, 57, 57, 1};
     */
    byte[] data = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 56, 61, 70, 73, 88, 46, 52, 46, 52, 1, 57, 61, 50, 48, 48, 1, 51, 53,
        61, 70, 1, 52, 57, 61, 49, 48, 48, 48, 48, 48, 48, 48, 48, 57, 1, 53, 54, 61, 116, 101, 115, 116, 1, 51, 52, 61, 50, 55, 1, 53, 50,
        61, 50, 48, 49, 57, 48, 55, 50, 55, 45, 48, 54, 58, 48, 51, 58, 49, 56, 46, 54, 55, 50, 1, 52, 49, 61, 48, 32, 32, 32, 32, 32, 32,
        32, 32, 32, 32, 32, 1, 51, 55, 61, 49, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 1, 49, 49, 61, 49, 55, 55, 32, 32, 32, 32,
        32, 32, 32, 32, 32, 1, 53, 50, 54, 61, 49, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 54, 50, 1, 49, 61, 49, 48, 48, 48, 48, 48, 48,
        48, 49, 48, 1, 53, 53, 61, 66, 84, 67, 85, 83, 68, 84, 1, 52, 56, 61, 52, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 1, 53, 52, 61,
        50, 1, 54, 48, 61, 50, 48, 49, 57, 48, 55, 50, 55, 45, 48, 54, 58, 48, 51, 58, 49, 56, 46, 54, 55, 50, 1, 51, 56, 61, 48, 1, 49, 48,
        61, 50, 50, 56, 1};

    System.out.println("decodeCancelOrderWithSecondaryOrderId fix=" + StringUtil.fixToString(data));

    KafkaInputFixListener listener = null;
    try {
      listener = new KafkaInputFixListener(false);
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
    Assert.assertTrue(message instanceof CancelOrder);

    CancelOrder cancelOrder = (CancelOrder) message;
    Assert.assertNotNull(cancelOrder);
    System.out.println(cancelOrder);
    cancelOrder.setInputTime(1564209769093366200L);
    cancelOrder.setDecodedTime(1564209774731036600L);
    Assert.assertEquals(
        "CancelOrder [securityId=4, price=0, side=SELL, qty=0, origOrderId=0, cancelId=1, cancelPriority=0, clOrdId=177, senderCompId=1000000009, account=10, ordType=LIMIT, type=1, priceInt=0, quantityLong=0, quantityOrigLong=0, secondaryOrderId=62, inTime=1564209769093366200, decodedTime=1564209774731036600, sourceSeqNum=0, sourceSendTime=0]",
        cancelOrder.toString());
  }
}
