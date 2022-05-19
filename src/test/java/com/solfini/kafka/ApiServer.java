package com.solfini.kafka;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import com.google.common.io.Resources;

public class ApiServer {

  private static final String OUTPUT_QUEUE = "matching-engine-input";

  private String id;
  private long sequence = 0;

  public ApiServer(String id, long start) {
    this.id = id;
    this.sequence = start;
  }

  private void run() {
    System.out.println(String.format("api-server-%s: starting producer with sequence %d", id, sequence));
    KafkaProducer<String, byte[]> producer = null;
    try (InputStream stream = Resources.getResource("producer.properties").openStream()) {
      Properties properties = new Properties();
      properties.load(stream);
      producer = new KafkaProducer<>(properties);
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    try {
      while (true) {
        String message = String.format("api-server:%s, sequence:%d, timestamp:%d", id, sequence, System.nanoTime());

        producer.send(new ProducerRecord<String, byte[]>(OUTPUT_QUEUE, message.getBytes("UTF-8")));
        System.out.println(String.format("api-server-%s >> %s", id, message));
        ++sequence;

        Thread.sleep(1000);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      producer.close();
    }
  }

  public static void main(String[] args) {

    if (args.length == 0) {
      System.out.println("  Usage: ApiServer <id> [start]");
      System.out.println("Options: <id>    - Server identifier");
      System.out.println("         <start> - Startup input sequence number (default is 0)");
      return;
    }

    String id = args[0];
    long start = args.length > 1 ? Long.parseLong(args[1]) : 0;

    ApiServer server = new ApiServer(id, start);
    server.run();
  }
}
