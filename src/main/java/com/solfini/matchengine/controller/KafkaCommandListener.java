package com.solfini.matchengine.controller;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.kafka.KafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

public class KafkaCommandListener extends KafkaListener {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaCommandListener.class);
  private static final Duration TIMEOUT = Duration.ofMillis(100);

  private final Controller controller;

  public KafkaCommandListener(final String topic, final Controller controller) {
    super(topic);
    this.controller = controller;
  }

  @Override
  public void start() {
    final List<TopicPartition> partitions = Arrays.asList(new TopicPartition(getTopic(), 0));
    getConsumer().assign(partitions);
    getConsumer().seekToEnd(partitions);
  }

  public final void process() {
    final ConsumerRecords<String, byte[]> records = getConsumer().poll(TIMEOUT);
    if (!records.isEmpty()) {
      for (final ConsumerRecord<String, byte[]> record : records) {
        String message = new String(record.value(), StandardCharsets.UTF_8);
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_2, "Control message received: ", message);
        }

        if (message.equals(controller.getInstanceId() + ":shutdown")) {
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Shutting down");
          }
          controller.shutdown();
        } else if (message.startsWith(controller.getInstanceId() + ":shutdown:promote:")) {
          final String promoteInstance = message.substring(message.lastIndexOf(':') + 1);
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Adding shutdown action - " + promoteInstance + ":primary");
            LOGGER.info("Shutting down");
          }
          controller.onTerminate(promoteInstance + ":primary");
          controller.shutdown();
        } else if (message.equals(controller.getInstanceId() + ":status")) {
          controller.status();
        } else if (message.startsWith(controller.getInstanceId() + ":")) {
          controller.execute(message.substring(message.indexOf(':') + 1));
        }
      }
    }
  }
}
