package com.solfini.matchengine.model;

import org.junit.Assert;

import com.solfini.common.Message;

/**
 * The ExpectedMessage class is used for verifying a message against a set of expectations.
 */
public class ExpectedMessage {
  private final String type;
  private final KeyValue[] keyValues;
  private final String[] strings;

  public ExpectedMessage(final String type, final KeyValue[] keyValues) {
    this.type = type;
    this.keyValues = keyValues;
    this.strings = null;
  }

  public ExpectedMessage(final String type, final String[] strings) {
    this.type = type;
    this.keyValues = null;
    this.strings = strings;
  }

  public ExpectedMessage(final KeyValue[] keyValueArray) {
    this(null, keyValueArray);
  }

  public static ExpectedMessage make(final String type, final String message) {
    final String[] fields = message.split(",");
    final KeyValue[] values = new KeyValue[fields.length];

    for (int i = 0; i < fields.length; i++) {
      final String[] tokens = fields[i].split("=");
      values[i] = new KeyValue(tokens[0].trim(), tokens[1].trim());
    }

    return new ExpectedMessage(type, values);
  }

  public void verify(final Message verifiableMessage) {
    try {
      System.out.println("VERIFY: " + verifiableMessage.toString());
      verify(verifiableMessage.toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void verify(final String verifiableMessage) {
    if (null != type) {
      Assert.assertTrue("Expected to be of type: " + type + " message:" + verifiableMessage, verifiableMessage.startsWith(type));
    }

    if (null != keyValues) {
      for (int i = 0; i < keyValues.length; i++) {
        Assert.assertTrue("Expected to contain: " + keyValues[i].toString() + " message:" + verifiableMessage,
            verifiableMessage.contains(keyValues[i].toString()));
      }
    }

    if (null != strings) {
      for (int i = 0; i < strings.length; i++) {
        Assert.assertTrue("Expected to contain: " + strings[i].toString() + " message:" + verifiableMessage,
            verifiableMessage.contains(strings[i]));
      }
    }
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (strings != null) {
      for (int i = 0; i < strings.length; i++) {
        sb.append(strings[i].toString()).append(" | ");
      }
    }
    if (keyValues != null) {
      for (int i = 0; i < keyValues.length; i++) {
        sb.append(keyValues[i].toString()).append(" | ");
      }
    }

    return sb.toString();
  }
}
