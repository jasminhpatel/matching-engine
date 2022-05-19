package com.solfini.matchengine.kafka;

import org.apache.kafka.common.header.Headers;

import com.solfini.common.Constants;

public class PoolableProducerRecord<K, V> extends org.apache.kafka.clients.producer.ProducerRecord<K, V> implements Constants {
  private static String DEFAULT_TOPIC = "default";

  private String topic;
  private Integer partition;
  private Headers headers;
  private K key;
  private V value;
  private Long timestamp;

  public PoolableProducerRecord(final String topic, final V value) {
    super(topic, value); // we have to call this to extend kafka
    this.topic = topic;
    this.value = value;
  }

  public PoolableProducerRecord(final V value) {
    super(DEFAULT_TOPIC, value); // we have to call this to extend kafka
    this.topic = DEFAULT_TOPIC;
    this.value = value;
  }

  public static final void setDefaultTopic(final String topic) {
    DEFAULT_TOPIC = topic;
  }

  public final void setValue(final V value) {
    this.value = value;
  }

  public final V getValue() {
    return value;
  }

  /**
   * @return The topic this record is being sent to
   */
  @Override
  public final String topic() {
    return topic;
  }

  /**
   * @return The headers
   */
  @Override
  public final Headers headers() {
    return headers;
  }

  /**
   * @return The key (or null if no key is specified)
   */
  @Override
  public final K key() {
    return key;
  }

  /**
   * @return The value
   */
  @Override
  public final V value() {
    return value;
  }

  /**
   * @return The timestamp, which is in milliseconds since epoch.
   */
  @Override
  public final Long timestamp() {
    return timestamp;
  }

  /**
   * @return The partition to which the record will be sent (or null if no partition was specified)
   */
  @Override
  public final Integer partition() {
    return partition;
  }


  @Override
  public String toString() {
    return "ProducerRecord(topic=" + topic + ", partition=" + partition +
      ", headers=" + (headers == null ? NULL : headers.toString()) +
      ", key=" + (key == null ? NULL : key.toString()) +
      VALUE_EQ + (value == null ? NULL : value.toString()) +
      TIMESTAMP_EQ + (timestamp == null ? NULL : timestamp.toString()) + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    else if (!(o instanceof PoolableProducerRecord))
      return false;

    final PoolableProducerRecord<?, ?> that = (PoolableProducerRecord<?, ?>) o;

    return !((key != null ? !key.equals(that.key()) : that.key() != null) ||
        (partition != null ? !partition.equals(that.partition()) : that.partition() != null) ||
        (topic != null ? !topic.equals(that.topic()) : that.topic() != null) ||
        (headers != null ? !headers.equals(that.headers()) : that.headers() != null) ||
        (value != null ? !value.equals(that.value()) : that.value() != null) ||
        (timestamp != null ? !timestamp.equals(that.timestamp()) : that.timestamp() != null));
  }

  @Override
  public int hashCode() {
    int result = topic != null ? topic.hashCode() : 0;
    result = 31 * result + (partition != null ? partition.hashCode() : 0);
    result = 31 * result + (headers != null ? headers.hashCode() : 0);
    result = 31 * result + (key != null ? key.hashCode() : 0);
    result = 31 * result + (value != null ? value.hashCode() : 0);
    result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
    return result;
  }
}
