package com.solfini.matchengine.kafka;

import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.kafka.persist.KafkaPersistReplayLoader;
import com.solfini.report.ReportUtil;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.RateBenchmark;

public class KafkaListener implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaListener.class);

  private static final Duration TIMEOUT = Duration.ofMillis(200);
  public static long FIRST_MESSAGE_RECEIVED_AT = 0;
  public static long FIRST_MESSAGE_OFFSET = 0;

  private final KafkaConsumer<String, byte[]> consumer;
  private final String topic;
  private long expectedSeqNum = 0;
  private long lastSequenceNum = 0;

  public enum StopMode {
    NONE, NEXT_ITERATION, END_OF_QUEUE, AT_OFFSET
  }

  private volatile StopMode stopMode = StopMode.NONE;
  private volatile long stopOffset = -1;
  private final CountDownLatch stopLatch = new CountDownLatch(1);
  private final RateBenchmark benchmark;

  private boolean firstMessageReceived = false;
  private long expectedKafkaOffset = 0;
  private long lastKafkaOffset = 0;

  public KafkaListener(final String topic) {
    // set up the consumer
    this.topic = topic;
    final Properties properties = PropertyReader.getPropertyGroup("KAFKA.CONSUMER");
    if (properties.getProperty("group.id") == null) {
      properties.setProperty("group.id", "group-" + new Random().nextInt(2_000_000_000));
    }

    consumer = new KafkaConsumer<>(properties);
    benchmark = new RateBenchmark("KafkaListener(" + topic + ")");
  }

  public final String getTopic() {
    return topic;
  }

  public long getLastSequence() {
    return lastSequenceNum;
  }

  public final KafkaConsumer<String, byte[]> getConsumer() {
    return consumer;
  }

  public void start() {
    consumer.subscribe(Arrays.asList(this.topic));
  }

  public void shutdown(final StopMode stopMode) throws InterruptedException {
    if (StopMode.NONE == this.stopMode) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info("Stopping listener: mode=" + stopMode);
      }
      this.stopMode = stopMode;
      stopLatch.await();
    }
  }

  public void shutdownAtOffset(long offset) throws InterruptedException {
    this.stopOffset = offset;
    shutdown(StopMode.AT_OFFSET);
  }

  public void shutdown() throws InterruptedException {
    shutdown(StopMode.NEXT_ITERATION);
  }

  public void onShutdown() {
    // Used by derived classes to perform additional actions on shutdown
  }

  @Override
  public void run() {
    if (Context.isReplayFromFileEnabled()) {
      LOGGER.warn("ReplayFromFileEnabled");
      KafkaPersistReplayLoader loader = new KafkaPersistReplayLoader(this);
      loader.replay();
    }

    start();

    final TopicPartition partition = new TopicPartition(topic, 0);
    while (true) {
      if (StopMode.NEXT_ITERATION == stopMode) {
        onShutdown();
        stopLatch.countDown();
        return;
      }

      if ((StopMode.END_OF_QUEUE == stopMode) && (-1 == stopOffset)) {
        final List<TopicPartition> partitions = Arrays.asList(partition);
        stopOffset = consumer.endOffsets(partitions).get(partition);
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info("Stopping listener: stopOffset=" + stopOffset);
        }
      }

      if (-1 != stopOffset) {
        final long currentOffset = consumer.position(partition);
        if (currentOffset >= stopOffset) {
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Stopping listener: stopOffset=" + stopOffset + ", currentOffset=" + currentOffset);
          }

          onShutdown();
          stopLatch.countDown();
          return;
        }
      }

      // read records with a short timeout. If we time out, we don't really care.
      final ConsumerRecords<String, byte[]> records = consumer.poll(TIMEOUT);
      for (final ConsumerRecord<String, byte[]> record : records) {
        try {
          final byte[] readData = record.value();
          final long kafkaOffset = record.offset();

          long seqNum = 0;
          for (int i = 0; i < 8; i++) {
            seqNum <<= 8;
            seqNum |= (readData[i] & 0xFF);
          }

          long sendTime = 0;
          for (int i = 8; i < 16; i++) {
            sendTime <<= 8;
            sendTime |= (readData[i] & 0xFF);
          }
          // messageType
          final byte messageType = readData[16];

          // Ignore sequence validation when the sequence number is 0.
          if (seqNum != 0) {
            if (expectedSeqNum == 0)
              expectedSeqNum = seqNum;
            else if (expectedSeqNum != seqNum) {
              expectedSeqNum = seqNum;
            }
            expectedSeqNum++;
            lastSequenceNum = seqNum;
          }

          // check kafka offset
          if (!firstMessageReceived) {
            FIRST_MESSAGE_RECEIVED_AT = TimeUtil.getTime();
            FIRST_MESSAGE_OFFSET = kafkaOffset;
            LOGGER.info("Publisher BM Start: " + FIRST_MESSAGE_RECEIVED_AT + " offset: " + FIRST_MESSAGE_OFFSET);
            firstMessageReceived = true;
            final String content = StringUtil.fixToString(readData);
            LOGGER.info(LOG_FMT_6, "Start Processing new messages. kafkaOffset: ", kafkaOffset ,
                " lastOrderId: ", NewOrderSingleHandler.getOrderId(), " lastExecutionId: ",
                GlobalOrderBook.getFilledCountGlobal());
            LOGGER.warn(LOG_FMT_8, "firstMessageReceived kafkaOffset: kafkaOffset=", kafkaOffset, ", messageType=", (long) messageType,
                ", sendTime=", sendTime, ", readData=", content);
            ReportUtil.onFirstMessage(kafkaOffset, sendTime, content);
          } else if (kafkaOffset != expectedKafkaOffset) {
            LOGGER.warn(LOG_FMT_6, "Unexpected kafkaOffset: expectedKafkaOffset=", expectedKafkaOffset, ", kafkaOffset=", kafkaOffset,
                ", lastKafkaOffset=", lastKafkaOffset);
          }
          lastKafkaOffset = kafkaOffset;
          expectedKafkaOffset = kafkaOffset + 1;
          // process bytes with 17 offset
          onMessage(seqNum, sendTime, kafkaOffset, messageType, readData);
          benchmark.sample();
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, e);
        }

      }
    }
  }

  public final boolean isActive() {
    return (StopMode.NONE == stopMode);
  }

  // process bytes with 17 offset
  public void onMessage(final long seqNum, final long sendTime, final long recordOffset, final byte messageType, final byte[] data) {
    final long latency = (TimeUtil.getTime() - sendTime) / 1000; // in microseconds
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_10, RECEIVED_SEQNUM_EQ, seqNum, SEND_TIME_EQ, sendTime, MESSAGETYPE_EQ, messageType, LATENCY_EQ, latency,
          DATA_EQ, StringUtil.fixToString(data));
    }
  }

}
