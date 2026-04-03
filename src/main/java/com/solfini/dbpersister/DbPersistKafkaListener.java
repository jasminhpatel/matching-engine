package com.solfini.dbpersister;

import com.solfini.common.Message;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;

import static com.solfini.common.Constants.*;

public class DbPersistKafkaListener implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(DbPersistKafkaListener.class);
  private static final Duration TIMEOUT = Duration.ofMillis(200);
  private static final int OFFSET = 19;
  private final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final String topic;
  private final KafkaConsumer<String, byte[]> consumer;
  private final MessageDecoder messageDecoder;

  public DbPersistKafkaListener(final String topic, final long startKafkaOffset) {
    this.messageDecoder = new MessageDecoder();
    this.topic = topic;
    final Properties properties = new Properties();
    try (final InputStream props = new FileInputStream("kafka-consumer.properties")) {
      properties.load(props);
    } catch (IOException e) {
      e.printStackTrace();
      LOGGER.error("error loading DbPersistKafkaListener", e);
    }
    consumer = new KafkaConsumer<>(properties);

    // Seek to the specified position
    final TopicPartition partition = new TopicPartition(topic, 0);
    final List<TopicPartition> partitions = Arrays.asList(partition);
    consumer.assign(partitions);
    if (startKafkaOffset == 0) {
      consumer.seekToBeginning(partitions);
      LOGGER.info(LOG_FMT_2, "Starting from the beginning. startOfOutQueue: ", startKafkaOffset);
      return;
    }
    consumer.seekToEnd(partitions);
    long startOfOutQueue = seekStartingPosition(partition, startKafkaOffset);
    LOGGER.info(LOG_FMT_2, "Seek response: ", startOfOutQueue);
    if (startOfOutQueue > 0) {
      LOGGER.info(LOG_FMT_2, "Starting from the startOfOutQueue. startOfOutQueue: ", startKafkaOffset);
      consumer.seek(partition, startOfOutQueue);
    } else {
      LOGGER.info(LOG_FMT_2, "Starting from the end. startOfOutQueue: ", startKafkaOffset);
      consumer.seekToEnd(partitions);
    }

    if (startOfOutQueue < startKafkaOffset) {
      LOGGER.info(LOG_FMT_4, "End offset is less than the snapshot kafka offset. startOfOutQueue: ", startOfOutQueue, " startKafkaOffset: ",
          startKafkaOffset);
    }
  }

  public void run() {
    LOGGER.info(LOG_FMT_2, "Started listening to topic: ", topic);

    while (true) {
      final ConsumerRecords<String, byte[]> records = consumer.poll(TIMEOUT);
      for (final ConsumerRecord<String, byte[]> record : records) {
        try {
          final byte[] readData = record.value();
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
          final byte messageType = readData[16];
          // process bytes with 17 offset
          final Message message = messageDecoder.onMessage(seqNum, sendTime, record.offset(), messageType, readData);
/*          if (message != null) {
            LOGGER.info(message.toJSON());
          }*/
          if (message instanceof PositionReportMessage positionReportMessage) {
            //LOGGER.info(message.toJSON());
            DbPersistPersister.persist(positionReportMessage);
          }
        } catch (Exception e) {
          e.printStackTrace();
          LOGGER.error("error", e);
        }
      }
    }
  }

  private long seekStartingPosition(final TopicPartition partition, final long expectedInputOffset) {
    long endOffset = consumer.endOffsets(Collections.singleton(partition)).get(partition);
    long currentOutputOffset = endOffset - 1; // last record
    long stopInputOffset = expectedInputOffset + 1;

    while (currentOutputOffset >= 0) {
      consumer.seek(partition, currentOutputOffset);
      final ConsumerRecords<String, byte[]> records = consumer.poll(TIMEOUT);

      if (!records.isEmpty()) {
        for (final ConsumerRecord<String, byte[]> record : records) {
          try {
            final byte[] readData = record.value();
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
            final byte messageType = readData[16];
            // process bytes with 17 offset
            final Message message = messageDecoder.onMessage(seqNum, sendTime, record.offset(), messageType, readData);
            if (message != null) {
              long inputOffset = headerDecoder.kafkaRecordOffset();
              if (inputOffset == 0)
                continue;

              LOGGER.info(LOG_FMT_8, "Reverse lookup. outputOffset: ", currentOutputOffset, " inputOffset: ", inputOffset, " expectedInputOffset: ",
                  expectedInputOffset, " diff: ", (inputOffset - expectedInputOffset));
              if (inputOffset < expectedInputOffset) {
                LOGGER.info(LOG_FMT_4, "Input offset is less than the expected. inputOffset: ", inputOffset, " expectedInputOffset: ", expectedInputOffset);
                return 0;
              }

              if (stopInputOffset == inputOffset) {
                LOGGER.info(LOG_FMT_4, "Found the starting point. startOfOutQueue. ", currentOutputOffset, " expectedInputOffset: ", expectedInputOffset);
                return currentOutputOffset;
              }
            } else {
              continue;
            }

          } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("error", e);
          }
        }
/*        for (final ConsumerRecord<String, byte[]> record : records) {
          decoderUnsafeBuffer.wrap(record.value());
          headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);
          long inputOffset = headerDecoder.kafkaRecordOffset();

          if (inputOffset == 0)
            continue;

          LOGGER.info(LOG_FMT_8, "Reverse lookup. outputOffset: ", currentOutputOffset, " inputOffset: ", inputOffset, " expectedInputOffset: ",
              expectedInputOffset, " diff: ", (inputOffset - expectedInputOffset));
          if (inputOffset < expectedInputOffset) {
            LOGGER.info(LOG_FMT_4, "Input offset is less than the expected. inputOffset: ", inputOffset, " expectedInputOffset: ", expectedInputOffset);
            return 0;
          }

          if (stopInputOffset == inputOffset) {
            LOGGER.info(LOG_FMT_4, "Found the starting point. startOfOutQueue. ", currentOutputOffset, " expectedInputOffset: ", expectedInputOffset);
            return currentOutputOffset;
          }
        }*/
      }

      currentOutputOffset--; // always move back
    }

    LOGGER.info("Could not find the starting point. Returning 0.");
    return 0;
  }
}
