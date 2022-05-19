package com.solfini.util.controller;

import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.util.PropertyReader;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaControllerPublisher extends KafkaPublisher {

  private final String topic;

  public KafkaControllerPublisher() {
    super(0);
    this.topic = PropertyReader.getProperty("CONTROLLER_KAFKA_TOPIC", "matching-engine-control");
  }

  public void primary(final String instance) {
    send(instance + ":primary");
  }

  public void priority(final String instance, final long instancePriority) {
    send(instance + ":priority:" + instancePriority);
  }

  public void shutdown(final String instance) {
    send(instance + ":shutdown");
  }

  public void shutdownAndPromote(final String instance, final String promoteInstance) {
    send(instance + ":shutdown:promote:" + promoteInstance);
  }

  public void status(final String instance) {
    send(instance + ":status");
  }

  public void send(final String message) {
    getProducer().send(new ProducerRecord<String, byte[]>(topic, message.getBytes()));
    getProducer().flush();
  }
}
