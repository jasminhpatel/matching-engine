package com.solfini.matchengine.kafka;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.matchengine.controller.Controller;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import com.solfini.util.benchmark.RateBenchmark;

public class KafkaPublisher implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaPublisher.class);
  public static final byte NORMAL_API = (byte) 2;
  public static final byte ADMIN_API = (byte) 4;

  public static final byte COMMAND_SHUTDOWN = (byte) 0;
  public static final byte COMMAND_TOPIC_CHANGE = (byte) 1;
  public static final byte COMMAND_FLUSH = (byte) 2;

  private final OneToOneConcurrentArrayQueueCustom<byte[]> publisherToKafkaPublisherQueue;
  private final KafkaProducer<String, byte[]> producer;
  private final RateBenchmark benchmark = new RateBenchmark("KafkaPublisher");
  private long seqNum = 0;
  private Future<RecordMetadata> lastRecordMetadata = null;
  private String topic;
  private volatile Controller controller;
  private volatile String newTopic;
  private volatile long newOutputSequence;
  private CountDownLatch latch = null;

  public KafkaPublisher() {
    // set up the producer
    this.seqNum = 0;
    producer = null;
    this.publisherToKafkaPublisherQueue = Context.getPublisherToKafkaPublisherQueue();
  }

  public KafkaPublisher(final long startSeqNum) {
    // set up the producer
    this.seqNum = startSeqNum;
    Properties properties = PropertyReader.getPropertyGroup("KAFKA.PRODUCER");
    producer = new KafkaProducer<>(properties);
    this.publisherToKafkaPublisherQueue = Context.getPublisherToKafkaPublisherQueue();
  }

  public final String getTopic() {
    return topic;
  }

  public final void setTopic(final String topic) {
    this.topic = topic;
  }

  // offset=17
  // called by publisher thread
  public void enqueueToSend(final String topic, final byte[] bytes, final byte messageType) {

    // seqNum
    seqNum++;
    long tempSeqNum = seqNum;
    for (int i = 7; i >= 0; i--) {
      bytes[i] = (byte) (tempSeqNum & 0xFF);
      tempSeqNum >>= 8;
    }

    // time
    long now = TimeUtil.getTime();
    for (int i = 15; i >= 8; i--) {
      bytes[i] = (byte) (now & 0xFF);
      now >>= 8;
    }

    // messageType
    bytes[16] = messageType;
    publisherToKafkaPublisherQueue.addGuaranteed(bytes);
  }

  public final void enqueueShutdown(final Controller controller) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_2, "Shutdown: Shutdown command enqueueing");
    }
    this.controller = controller;
    byte[] buffer = new byte[] {COMMAND_SHUTDOWN};
    publisherToKafkaPublisherQueue.addGuaranteed(buffer);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_2, "Shutdown: Shutdown command enqueueing completed");
    }
  }

  public final void enqueueTopicChange(final String newTopic, final long newOutputSequence) {
    this.newTopic = newTopic;
    this.newOutputSequence = newOutputSequence;
    byte[] buffer = new byte[] {COMMAND_TOPIC_CHANGE};
    publisherToKafkaPublisherQueue.addGuaranteed(buffer);
  }

  public final void enqueueFlushAndWait() throws InterruptedException {
    latch = new CountDownLatch(1);
    byte[] buffer = new byte[] {COMMAND_FLUSH};
    publisherToKafkaPublisherQueue.addGuaranteed(buffer);
    latch.await();
  }

  // called by kafka publisher thread
  public final void send(final byte[] bytes) {
    if (bytes.length >= 32_768) {
      LOGGER.error(LOG_FMT_2, "error 1, kafka bytes send length=", bytes.length, StringUtil.fixToString(bytes));
    }

    if (bytes.length == 1) { // special case to handle commands
      switch (bytes[0]) {
        case COMMAND_SHUTDOWN:
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_2, "Shutdown: Shutdown command executing");
          }
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Executing command: COMMAND_SHUTDOWN");
          }
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_2, "Shutdown: Kafka producer flushing");
          }
          producer.flush();
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_2, "Shutdown: Kafka producer closing");
          }
          producer.close();
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_2, "Shutdown: Controller terminating");
          }
          controller.terminate();
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_2, "Shutdown: Shutdown command enqueueing completed");
          }
          break;

        case COMMAND_TOPIC_CHANGE:
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Executing command: COMMAND_TOPIC_CHANGE");
          }
          this.topic = this.newTopic;
          if (this.newOutputSequence != -1) {
            this.seqNum = this.newOutputSequence;
          }
          break;

        case COMMAND_FLUSH:
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Executing command: COMMAND_FLUSH");
          }
          latch.countDown();
          break;
        default:
      }
      return;
    }

    if (null != topic) {
      lastRecordMetadata = producer.send(new ProducerRecord<String, byte[]>(topic, bytes));
      benchmark.sample();
    } else if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_2, "SKIPPING output message as topic has not been set", StringUtil.fixToString(bytes));
    }
  }

  // send to custom topic, different than defined topic
  // returns the metaData
  public final Future<RecordMetadata> sendDirect(final byte[] bytes, final String customTopic) {
    if (bytes.length >= 32_768) {
      LOGGER.error(LOG_FMT_2, "error 2, kafka bytes send length=", bytes.length, StringUtil.fixToString(bytes));
    }

    return producer.send(new ProducerRecord<String, byte[]>(customTopic, bytes));
  }

  // offset=17
  // called by external thread with custom topic
  public final Future<RecordMetadata> sendDirect(final String customTopic, final byte[] bytes, final byte messageType) {

    // seqNum
    seqNum++;
    long tempSeqNum = seqNum;
    for (int i = 7; i >= 0; i--) {
      bytes[i] = (byte) (tempSeqNum & 0xFF);
      tempSeqNum >>= 8;
    }

    // time
    long now = TimeUtil.getTime();
    for (int i = 15; i >= 8; i--) {
      bytes[i] = (byte) (now & 0xFF);
      now >>= 8;
    }

    // messageType
    bytes[16] = messageType;
    Future<RecordMetadata> future = sendDirect(bytes, customTopic);
    benchmark.sample();
    return future;
  }

  public void close() {
    producer.flush();
    producer.close();
  }

  public void flush() {
    producer.flush();
  }

  public long getLastSendOffset() {
    try {
      if (lastRecordMetadata != null) {
        RecordMetadata recordMetadata = lastRecordMetadata.get();
        return recordMetadata.hasOffset() ? recordMetadata.offset() : -1;
      }
    } catch (Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
    }

    return -1;
  }

  protected KafkaProducer<String, byte[]> getProducer() {
    return producer;
  }
}
