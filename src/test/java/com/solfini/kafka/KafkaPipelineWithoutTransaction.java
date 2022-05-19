package com.solfini.kafka;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import com.google.common.io.Resources;
import java.util.List;
import java.util.ArrayList;

public class KafkaPipelineWithoutTransaction {

  private static final String INPUT_QUEUE = "matching-engine-input";
  private static final String OUTPUT_QUEUE = "matching-engine-output";

  class Message {
    public byte[] data;
    long recvTime;
    long offset;
    int partition;
  }

  class Forwarder {
    List<Message> incoming;
    List<Message> outgoing;

    Forwarder(List<Message> incoming, List<Message> outgoing) {
      this.incoming = incoming;
      this.outgoing = outgoing;
    }

    public void run() {
      new Thread(() -> {

        try {
          while (true) {
            if (incoming.isEmpty()) {
              Thread.yield();
              continue;
            }

            Message record = incoming.remove(0);
            outgoing.add(record);
          }
        } catch (Exception e) {
        }

      }).start();
    }
  };

  class Producer {
    List<Message> list;

    Producer(List<Message> list) {
      this.list = list;
    }

    public void run() {
      new Thread(() -> {
        // Create a consumer to read from INPUT_QUEUE
        KafkaConsumer<String, byte[]> consumer = null;
        try (InputStream stream = Resources.getResource("consumer.properties").openStream()) {
          Properties properties = new Properties();
          properties.load(stream);
          consumer = new KafkaConsumer<>(properties);
        } catch (IOException e) {
          e.printStackTrace();
          return;
        }

        consumer.subscribe(Arrays.asList(INPUT_QUEUE));

        while (true) {
          ConsumerRecords<String, byte[]> records = consumer.poll(1000);
          if (records.count() == 0) {
            Thread.yield();
            continue;
          }

          for (ConsumerRecord<String, byte[]> record : records) {
            String data = new String(record.value());
            // System.out.println(String.format("Read %s << %s", record.offset(), data));

            // Add the read record to the list
            Message msg = new Message();
            msg.data = record.value();
            msg.offset = record.offset();
            msg.partition = record.partition();
            msg.recvTime = System.nanoTime();
            this.list.add(msg);
          }

          consumer.commitSync();
        }
      }).start();
    }
  };

  class Consumer {
    List<Message> list;

    Consumer(List<Message> list) {
      this.list = list;
    }

    public void run() {
      new Thread(() -> {

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
            // Avoid busy wait
            if (this.list.isEmpty()) {
              Thread.yield();
              continue;
            }

            // Take one record from the queue and push it to the OUTPUT_QUEUE
            Message record = this.list.remove(0);

            producer.send(new ProducerRecord<>(OUTPUT_QUEUE, record.data));


            long now = System.nanoTime();

            System.out.println(String.format("Committed %s << Latency %d us", record.offset, (now - record.recvTime) / 1000));
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          producer.close();
        }

      }).start();
    }
  };

  private String id;
  private Producer producer;
  private Consumer consumer;
  private ArrayList<Forwarder> forwarders = new ArrayList<>();

  public KafkaPipelineWithoutTransaction(String id, Integer n) {
    this.id = id;

    List<Message> list = Collections.synchronizedList(new ArrayList<>());

    this.producer = new Producer(list);

    for (int i = 0; i < n - 1; ++i) {
      List<Message> list2 = Collections.synchronizedList(new ArrayList<>());

      this.forwarders.add(new Forwarder(list, list2));

      list = list2;
    }

    this.consumer = new Consumer(list);
  }

  private void run() {
    consumer.run();
    for (Forwarder f : this.forwarders) {
      f.run();
    }

    producer.run();
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("  Usage: KafkaPipelineWithoutTransaction <id> [n]");
      System.out.println("Options: <id>    - Server identifier");
      System.out.println("         <forwarders>    - Number of forwarders to run in the pipeline");
      return;
    }

    String id = args[0];
    int n = Integer.parseInt(args[1]) <= 1 ? 1 : Integer.parseInt(args[1]);

    KafkaPipelineWithoutTransaction engine = new KafkaPipelineWithoutTransaction(id, n);
    engine.run();
  }
}
