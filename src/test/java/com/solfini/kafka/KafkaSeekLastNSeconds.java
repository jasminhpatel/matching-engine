package com.solfini.kafka;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import com.google.common.io.Resources;
import java.util.List;
import java.util.Map;

public class KafkaSeekLastNSeconds {

  private static final String INPUT_QUEUE = "matching-engine-input";

  private String id;
  private long n;

  public KafkaSeekLastNSeconds(String id, long n) {
    this.id = id;
    this.n = n;
  }

  private void run() {
    System.out.println(String.format("matching-engine-%s: starting consumer", id));
    KafkaConsumer<String, byte[]> consumer = null;
    try (InputStream stream = Resources.getResource("consumer.properties").openStream()) {
      Properties properties = new Properties();
      properties.load(stream);
      consumer = new KafkaConsumer<>(properties);
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    TopicPartition topicPartition = new TopicPartition(INPUT_QUEUE, 0);

    List<TopicPartition> topics = Arrays.asList(topicPartition);
    consumer.assign(topics);

    // Subscribe to n seconds from the current time and find the offset
    Map<TopicPartition, Long> query = new HashMap<>();
    query.put(topicPartition, Instant.now().minus(this.n, ChronoUnit.SECONDS).toEpochMilli());

    Map<TopicPartition, OffsetAndTimestamp> positions = consumer.offsetsForTimes(query);
    for (Map.Entry<TopicPartition, OffsetAndTimestamp> entry : positions.entrySet()) {
      consumer.seek(topicPartition, entry.getValue().offset());
    }

    while (true) {
      ConsumerRecords<String, byte[]> records = consumer.poll(1000);
      if (records.count() == 0) {
        continue;
      }

      for (ConsumerRecord<String, byte[]> record : records) {
        String data = new String(record.value());
        System.out.println(String.format("matching-engine-%s << %s", id, data));
      }

      consumer.commitSync();
    }
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("  Usage: KafkaSeekLastNSeconds <id> [n]");
      System.out.println("Options: <id>    - Server identifier");
      System.out.println("         <n> - Start reading from the n seconds before the last message");
      return;
    }

    String id = args[0];
    long n = args.length > 1 ? Long.parseLong(args[1]) : 0;

    KafkaSeekLastNSeconds engine = new KafkaSeekLastNSeconds(id, n);
    engine.run();
  }
}
