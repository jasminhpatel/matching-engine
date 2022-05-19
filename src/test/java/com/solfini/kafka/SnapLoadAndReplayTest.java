package com.solfini.kafka;

import com.solfini.common.Context;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.matchengine.kafka.KafkaInputFixListener;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SnapLoadAndReplayTest {

  //    private static final String SNAPSHOT_PATH = "1564993803007140239";
  private static final String SNAPSHOT_PATH = "1564993803010132403";
  private static final String KAFKA_QUEUE_DUMP_PATH = "replay0";


  private KafkaInputFixListener kafkaInputFixListener;
  private final ManyToOneConcurrentArrayQueueCustom<Message> receiverToMatcherQueue;


  public SnapLoadAndReplayTest() throws IOException {
    PropertyReader.initialize(null, configure());
    kafkaInputFixListener = new KafkaInputFixListener(false);
    receiverToMatcherQueue = Context.getReceiverToMatcherQueue();
  }

  @Ignore
  @Test
  public void loadAndMatch() {
    List<Message> messageList = loadSnapshot(SNAPSHOT_PATH);
    messageList.forEach(Message::onMatcher); //call onMatcher() per each message
  }

  @Ignore
  @Test
  public void replayKafkaQueueFromFile() {
    List<Message> messageList = loadKafkaQueue(KAFKA_QUEUE_DUMP_PATH);
    messageList.forEach(Message::onMatcher); //call onMatcher() per each message
  }

  private List<Message> loadKafkaQueue(String queueDumpId) {
    String filePath = "src/test/resources/queues/" + queueDumpId;
    final ChronicleQueue queue = SingleChronicleQueueBuilder.single(filePath).blockSize(1048576).rollCycle(RollCycles.DAILY).build();

    final ExcerptTailer tailer = queue.createTailer();
    final Bytes<ByteBuffer> bytes = Bytes.elasticHeapByteBuffer(32768);


    while (true) {
      bytes.clear();
      boolean read = tailer.readBytes(bytes);

      if (read) {
        byte[] data = bytes.underlyingObject().array();
        int length = (int) bytes.readRemaining();

        byte[] messageData = new byte[length - 8];
        System.arraycopy(data, 8, messageData, 0, length - 8);

        long sequence = 0;
        for (int i = 0; i < 8; i++) {
          sequence <<= 8;
          sequence |= (messageData[i] & 0xFF);
        }

        long sendTime = 0;
        for (int i = 8; i < 16; i++) {
          sendTime <<= 8;
          sendTime |= (messageData[i] & 0xFF);
        }

        final byte messageType = messageData[16];

        kafkaInputFixListener.onMessage(sequence, sendTime, 0, messageType, messageData);

      } else {
        break;
      }
    }
    List<Message> messages = new ArrayList<>();
    receiverToMatcherQueue.drainTo(messages, 100_000);
    return messages;
  }

  private List<Message> loadSnapshot(String snapshotId) {
    String filePath = "src/test/resources/snapshots/" + snapshotId;
    final ChronicleQueue queue = SingleChronicleQueueBuilder.single(filePath).blockSize(1048576).rollCycle(RollCycles.DAILY).build();

    final ExcerptTailer tailer = queue.createTailer();
    final Bytes<ByteBuffer> bytes = Bytes.elasticHeapByteBuffer(32768);


    while (true) {
      bytes.clear();
      boolean read = tailer.readBytes(bytes);

      if (read) {
        byte[] data = bytes.underlyingObject().array();
        int length = (int) bytes.readRemaining();

        long sequence = 0;
        for (int i = 0; i < 8; i++) {
          sequence <<= 8;
          sequence |= (data[i] & 0xFF);
        }

        long sendTime = 0;
        for (int i = 8; i < 16; i++) {
          sendTime <<= 8;
          sendTime |= (data[i] & 0xFF);
        }

        final byte messageType = data[16];

        kafkaInputFixListener.onMessage(sequence, sendTime, 0, messageType, data);

      } else {
        break;
      }
    }
    List<Message> messages = new ArrayList<>();
    receiverToMatcherQueue.drainTo(messages, 100_000);
    return messages;
  }

  private Properties configure() {
    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("QUEUE_CAPACITY", "100000");
    properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "40000");
    properties.setProperty("ORDER_POOL_START_CAPACITY", "40000");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

    return properties;
  }
}

