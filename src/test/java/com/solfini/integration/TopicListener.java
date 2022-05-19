package com.solfini.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.matchengine.model.ExpectedMessage;
import com.solfini.matchengine.model.KeyValue;
import org.junit.Assert;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TopicListener {
  private final String topic;
  private final Listener listener;

  private List<Message> actualFinalMessageList = new ArrayList<>();
  private List<String> expectedFinalMessageList = new ArrayList<>();

  private List<MessageType> expectedMessageTypeList = new ArrayList<>();
  private List<MessageType> actualMessageTypeList = new ArrayList<>();
  private List<String> expectedFinalMessageTypeList = new ArrayList<>();

  public TopicListener(final String topic, final Listener listener) {
    this.topic = topic;
    this.listener = listener;
    this.listener.start();
  }

  public Message receive() throws Exception {
    return listener.receive(topic);
  }

  public Message expect(final String expected) throws Exception {
    Log.debug("Current " + Thread.currentThread().getStackTrace()[1].getClassName() + " Line: " + Thread.currentThread().getStackTrace()[2]
      .getLineNumber());
    final Message message = receive();
    Assert.assertNotNull(message);
    Assert.assertEquals(expected, message.toString());

    return message;
  }

  public Message expect(final Message expected) throws Exception {
    JsonObject jsonExpected = new JsonParser().parse(expected.toJSON()).getAsJsonObject();

    final Message actual = receive();
    JsonObject jsonActual = new JsonParser().parse(actual.toJSON()).getAsJsonObject();

    validate(jsonExpected, jsonActual);

    return actual;
  }

  private boolean validate(JsonObject actual, JsonObject expected) {
    boolean isValid = true;
    expected.entrySet().forEach((entry) -> {
      if (entry.getValue().isJsonObject()) {
        validate(actual.get(entry.getKey()).getAsJsonObject(), entry.getValue().getAsJsonObject());
      } else {
        Assert.assertEquals(entry.getValue(), actual.get(entry.getKey()));
      }
    });
    return isValid;
  }

  public void clearMessageQueues() {
    expectedMessageTypeList = new ArrayList<>();
    expectedFinalMessageList = new ArrayList<>();
    expectedFinalMessageTypeList = new ArrayList<>();

    actualMessageTypeList = new ArrayList<>();
    actualFinalMessageList = new ArrayList<>();
  }

  private void printAssertionStats() {
    System.out.println("\nXX List sizes ");
    System.out.println("XX Expected Message Type List: " + expectedMessageTypeList.size());
    System.out.println("XX Expected Message List: " + expectedFinalMessageList.size());
    System.out.println("XX Expected Final Message List: " + expectedFinalMessageTypeList.size());

    System.out.println("\nXX Actual Message List: " + actualFinalMessageList.size());
    System.out.println("XX Actual Message Type List: " + actualMessageTypeList.size());

    System.out.println("\nXX Actual Final Messages: ");

    AtomicInteger j = new AtomicInteger(1);
    actualFinalMessageList.forEach(message -> {
      System.out.println("\nXX Message " + j + ": " + message.toString());
      j.getAndIncrement();
    });

    System.out.println("\nXX Actual Message Types: ");

    AtomicInteger k = new AtomicInteger(1);
    actualMessageTypeList.forEach(messageType -> {
      System.out.println("\nXX Message Type " + k + ": " + messageType.toString());
      k.getAndIncrement();
    });

/*    System.out.println(
      "\nXX Verifying Final Message. Expected Type: " + expectedFinalMessageTypeList.get(expectedFinalMessageList.size() - 1)
        + " Expected Message: " + expectedFinalMessageList.get(expectedFinalMessageList.size() - 1));
    System.out.println("XX Actual Message Comparing: " + actualFinalMessageList.get(expectedFinalMessageList.size() - 1).toString());
    System.out.println("\n");*/
  }

  public void assertMessages() {
    Map<MessageType, Integer> expectedCounts = new HashMap<>();
    Map<MessageType, Integer> actualCounts = new HashMap<>();

    for (MessageType messageType : expectedMessageTypeList) {
      expectedCounts.put(messageType, 0);
    }

    for (MessageType messageType : actualMessageTypeList) {
      actualCounts.put(messageType, 0);
    }

    for (MessageType messageType : expectedMessageTypeList) {
      expectedCounts.put(messageType, expectedCounts.get(messageType) + 1);
    }

    for (MessageType messageType : actualMessageTypeList) {
      actualCounts.put(messageType, actualCounts.get(messageType) + 1);
    }

    Assert.assertEquals(expectedCounts, actualCounts);

    //printAssertionStats();

    // Compare Final Message
   /* makeExpectedMessage(expectedFinalMessageTypeList.get(expectedFinalMessageList.size() - 1),
      expectedFinalMessageList.get(expectedFinalMessageList.size() - 1))
      .verify(actualFinalMessageList.get(expectedFinalMessageList.size() - 1).toString());*/
  }

  protected void expectFinalMessage(final String type, final String expectedMessage) throws Exception {

    expectedFinalMessageList.add(expectedMessage);
    expectedFinalMessageTypeList.add(type);

    final Message message = receive();
    if (message != null) {
      actualFinalMessageList.add(message);
    } else {
      Log.info("Unable to receive message for the test");
    }
  }

  public Message expectMessage(MessageType expectedMessageType) throws Exception {
    Log.debug("Current " + Thread.currentThread().getStackTrace()[1].getClassName() + " Line: " + Thread.currentThread().getStackTrace()[2]
      .getLineNumber());

    if (expectedMessageType != MessageType.EXECUTION_REPORT) {  //TODO: Find an alternative to decode pending cancel execution reports
      expectedMessageTypeList.add(expectedMessageType);
    }

    final Message message = receive();
    if (message != null && message.getMessageType() != MessageType.EXECUTION_REPORT) {
      actualMessageTypeList.add(message.getMessageType());
    } else {
      Log.info("Unable to receive message for the test");
    }
    return message;
  }


  public Message expect(final String expected, Boolean validate) throws Exception {
    final Message message = receive();
    if (validate) {
      Assert.assertNotNull(message);
      Assert.assertEquals(expected, message.toString());
    }

    return message;
  }

  private ExpectedMessage makeExpectedMessage(final String type, final String message) {
    final String[] fields = message.split(",");
    final KeyValue[] keyValueArray = new KeyValue[fields.length];

    for (int i = 0; i < fields.length; i++) {
      final String[] tokens = fields[i].split("=");
      keyValueArray[i] = new KeyValue(tokens[0].trim(), tokens[1].trim());
    }

    return new ExpectedMessage(type, keyValueArray);
  }

  protected Message expectMessage(final String type, final KeyValue... keyValueArray) throws Exception {
    final Message message = receive();
    new ExpectedMessage(type, keyValueArray).verify(message.toString());
    return message;
  }

  protected void expectMessage(final KeyValue... keyValueArray) throws Exception {
    expectMessage(null, keyValueArray);
  }

  protected Message expectMessage(final String type, final String expectedMessage) throws Exception {
    final Message message = receive();
    makeExpectedMessage(type, expectedMessage).verify(message.toString());
    return message;
  }

  protected void expectMessage(final String type, final String... strings) throws Exception {
    final Message message = receive();
    new ExpectedMessage(type, strings).verify(message);
  }

}
