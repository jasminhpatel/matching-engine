package com.solfini.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import com.solfini.matchengine.kafka.PoolableProducerRecord;
import org.junit.Assert;
import org.junit.Test;

public class PoolableProducerRecordTest {

  @Test
  public void comparePoolableProducerRecords() {
    PoolableProducerRecord.setDefaultTopic("test1");
    ProducerRecord<String, String> producerRecord = new PoolableProducerRecord<>("test3","test4");
    ProducerRecord<String, String> producerRecord2 = new PoolableProducerRecord<>("test1", "test4");
    ProducerRecord<String, String> producerRecord3 = new PoolableProducerRecord<>("test3", "test4");
    Assert.assertFalse(producerRecord2.equals(producerRecord));
    Assert.assertFalse(producerRecord.equals(producerRecord2));
    Assert.assertTrue(producerRecord3.equals(producerRecord));
    Assert.assertTrue(producerRecord.equals(producerRecord3));
  }
}
