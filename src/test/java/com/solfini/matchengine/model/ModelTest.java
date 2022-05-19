package com.solfini.matchengine.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.slf4j.event.Level;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.util.LogLevel;
import com.solfini.util.MessageDecoder;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

public class ModelTest extends ModelTestScaffold {

  private final List<ExpectedMessage> expectedMessageList = new ArrayList<ExpectedMessage>();
  private final List<ExpectedMessage> expectedOutputMessageList = new ArrayList<ExpectedMessage>();
  private final List<Message> publisherInputMessageList = new ArrayList<>();

  @Before
  public void before() {
    LogLevel.setLevel(Level.TRACE);

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      PropertyReader.initialize(null, properties);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @After
  public void after() {
    clearMessages();
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

  protected void expectMessage(final String type, final KeyValue... keyValueArray) {
    expectedMessageList.add(new ExpectedMessage(type, keyValueArray));
  }

  protected void expectMessage(final KeyValue... keyValueArray) {
    expectMessage(null, keyValueArray);
  }

  protected void expectMessage(final String type, final String message) {
    expectedMessageList.add(makeExpectedMessage(type, message));
  }

  protected void expectMessage(final String message) {
    expectMessage(null, message);
  }

  protected void expectMessage(final String type, final String... strings) {
    expectedMessageList.add(new ExpectedMessage(type, strings));
  }

  protected void clearMessages() {
    ArrayList<Message> messages = new ArrayList<>();
    do {
      messages.clear();
      Context.getMatcherToPublisherQueue().drainTo(messages, 1_000);
    } while (!messages.isEmpty());
  }

  protected void assertMessages() {
    ArrayList<Message> resultList = new ArrayList<>();
    Context.getMatcherToPublisherQueue().drainTo(resultList, 4096);

    if (expectedMessageList.size() != resultList.size()) {
      for (int i = 0; i < resultList.size(); i++) {
        System.out.println("Message (" + i + "): " + resultList.get(i));
        if (expectedMessageList.size() > i)
          System.out.println("expected Message (" + i + "): " + expectedMessageList.get(i));
      }
    }

    Assert.assertEquals(expectedMessageList.size(), resultList.size());

    for (int i = 0; i < expectedMessageList.size(); i++) {
      System.out.println("Message (" + i + "): " + resultList.get(i));
      expectedMessageList.get(i).verify(resultList.get(i).toString());
    }

    expectedMessageList.clear();
    publisherInputMessageList.addAll(resultList);
  }

  protected void assertOutputBaseMessage(final Message message, final String type, final String expected) {
    makeExpectedMessage(type, expected).verify(message.toString());
  }

  protected void expectOutput(final String type, final String message) {
    expectedOutputMessageList.add(makeExpectedMessage(type, message));
  }

  protected void expectOutput(final String message) {
    expectOutput(null, message);
  }

  protected void expectOutput(final String type, final String... strings) {
    expectedOutputMessageList.add(new ExpectedMessage(type, strings));
  }

  protected void clearExpectedOutputMessages() {
    expectedOutputMessageList.clear();
  }

  protected List<Message> assertOutputMessages(boolean forcePublish) {
    // Setup publisher
    Context.setKafkaPublisher(new KafkaPublisher());

    // Publish all messages if prompted

    if (forcePublish) {
      for (Message msg : publisherInputMessageList) {
        try {
          msg.onPublish();
        } catch (Exception e) {
          System.err.println("Exception thrown while publishing message. message: " + msg + " error: " + e);
        }
      }
    }

    // Read from the publisher output queue
    final ArrayList<byte[]> outputList = new ArrayList<>();
    Context.getPublisherToKafkaPublisherQueue().drainTo(outputList, 1_000);

    final ArrayList<Message> resultList = new ArrayList<>();
    final ArrayList<Message> baseMessages = new ArrayList<>();
    final MessageDecoder decoder = new MessageDecoder();
    for (byte[] data : outputList) {
      try {
        Message msg = decoder.decode(data, 0, baseMessages);
        System.out.println("ModelTest decoded: " + msg);
        if (msg == null) {
          System.err.println("Decoder returned null while decoding");
          continue;
        }

        resultList.add(msg);
      } catch (Exception e) {
        System.err.println("Exception thrown while decoding message. error: " + e);
        continue;
      }
    }


    // Validate expectations
    if (expectedOutputMessageList.size() != resultList.size()) {
      for (int i = 0; i < resultList.size(); i++) {
        System.out.println("[PUBLISHER] Message (" + i + "): " + resultList.get(i));
        if (expectedOutputMessageList.size() > i)
          System.out.println("[PUBLISHER] expected Message (" + i + "): " + expectedOutputMessageList.get(i));
      }
    }

    Assert.assertEquals(expectedOutputMessageList.size(), resultList.size());

    for (int i = 0; i < expectedOutputMessageList.size(); i++) {
      System.out.println("[PUBLISHER]  Message (" + i + "): " + resultList.get(i));
      expectedOutputMessageList.get(i).verify(resultList.get(i).toString());
    }

    expectedOutputMessageList.clear();
    publisherInputMessageList.clear();

    return baseMessages;
  }

  // Verifies all messages from the publisher
  protected List<Message> assertOutputMessages() {
    return assertOutputMessages(true);
  }

  protected void clearQueues() {
    expectedMessageList.clear();
    expectedOutputMessageList.clear();
    publisherInputMessageList.clear();

    final ArrayList<byte[]> outputList = new ArrayList<>();
    Context.getPublisherToKafkaPublisherQueue().drainTo(outputList, 1_000);

    ArrayList<Message> resultList = new ArrayList<>();
    Context.getMatcherToPublisherQueue().drainTo(resultList, 1_000);
  }



}
