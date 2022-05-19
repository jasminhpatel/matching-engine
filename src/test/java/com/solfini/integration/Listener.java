package com.solfini.integration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.solfini.common.Message;
import com.solfini.matchengine.kafka.KafkaListener;
import com.solfini.util.MessageDecoder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

public class Listener extends KafkaListener {
  private static final Duration TIMEOUT = Duration.ofMillis(200);
  private final List<TopicPartition> partitions = new ArrayList<>();
  private final Map<String, List<ConsumerRecord<String, byte[]>>> records = new HashMap<>();
  private final MessageDecoder decoder = new MessageDecoder();

  public Listener() {
    super("");
  }

  public TopicListener listen(final String topic) {
    Log.info("Listening to topic: "+ topic);
    partitions.add(new TopicPartition(topic, 0));


    return new TopicListener(topic, this);
  }

  @Override
  final public void start() {
    getConsumer().assign(partitions);
    getConsumer().seekToEnd(partitions);
    for(TopicPartition topicPartition: partitions){
      long endOffset = getConsumer().position(topicPartition);
      getConsumer().seek(topicPartition, endOffset);
    }
  }

  public Message receive(final String topic) throws Exception {
    List<ConsumerRecord<String, byte[]>> list = getRecords(topic);
    while (list.size() == 0) {
      for (final ConsumerRecord<String, byte[]> record : getConsumer().poll(TIMEOUT)) {
        getRecords(record.topic()).add(record);
      }
    }

    ConsumerRecord<String, byte[]> record = list.remove(0);
    try{

      Message message = decoder.decode(record.value(), record.offset(), null);
      Log.debug("RX (" + topic + "): " + message.toString());

      return message;
    } catch (Exception e){
      e.printStackTrace();
      return null;
    }
  }

  private List<ConsumerRecord<String, byte[]>> getRecords(final String topic) {
    List<ConsumerRecord<String, byte[]>> result = records.get(topic);
    if (null == result) {
      result = new ArrayList<>();
      records.put(topic, result);
    }

    return result;
  }
}
