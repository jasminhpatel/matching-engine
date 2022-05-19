package com.solfini.matchengine.model;

/**
 * The KeyValue class contains a key/value pair.
 */
public class KeyValue {

  private final String key;
  private final String value;

  public static KeyValue validatePair(final String key, final String value) {
    return new KeyValue(key, value);
  }

  public KeyValue(final String key, final String value) {
    this.key = key;
    this.value = value;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return key + "=" + value;
  }
}
