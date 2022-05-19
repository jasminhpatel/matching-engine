package com.solfini.util;

import java.util.ArrayList;

import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.common.TransactionalInputManyToOneConcurrentArrayQueue;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import org.junit.Assert;
import org.junit.Test;

public class TestTransactionInputQueue {
  @Test
  public void testAdd() {
    ManyToOneConcurrentArrayQueueCustom<Message> queue = new TransactionalInputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);

    queue.add(message);

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(1, list.size());
    Assert.assertEquals(message, list.get(0));
  }

  @Test
  public void testOffer() {
    ManyToOneConcurrentArrayQueueCustom<Message> queue = new TransactionalInputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);

    queue.offer(message);

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(1, list.size());
    Assert.assertEquals(message, list.get(0));
  }

  @Test
  public void testAddGuaranteed() {
    ManyToOneConcurrentArrayQueueCustom<Message> queue = new TransactionalInputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);

    queue.offer(message);

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(1, list.size());
    Assert.assertEquals(message, list.get(0));
  }

  @Test
  public void testClear() {
    ManyToOneConcurrentArrayQueueCustom<Message> queue = new TransactionalInputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);

    queue.offer(message);
    queue.clear();

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(0, list.size());
  }

  @Test
  public void testAddTransactionSingle() {
    ManyToOneConcurrentArrayQueueCustom<Message> queue = new TransactionalInputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);
    message.setTransactionId(10);
    message.setLastMessageInTransaction(true);

    queue.add(message);

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(1, list.size());
    Assert.assertEquals(message, list.get(0));
  }

  @Test
  public void testAddTransactionMultiple() {
    ManyToOneConcurrentArrayQueueCustom<Message> queue = new TransactionalInputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);
    message.setTransactionId(10);
    message.setLastMessageInTransaction(false);

    ExecutionReportMessage message2 = new ExecutionReportMessage();
    message2.setOrderId(2);
    message2.setTransactionId(10);
    message2.setLastMessageInTransaction(true);

    queue.add(message);
    queue.add(message2);

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(2, list.size());
    Assert.assertEquals(message, list.get(0));
    Assert.assertEquals(list.get(1), message2);
  }

  @Test
  public void testDropInvalidTransactions() {
    ManyToOneConcurrentArrayQueueCustom<Message> queue = new TransactionalInputManyToOneConcurrentArrayQueue(1000, "A");

    // This message should be dropped as transaction will not be closed
    ExecutionReportMessage invalid = new ExecutionReportMessage();
    invalid.setOrderId(1);
    invalid.setTransactionId(9);
    invalid.setLastMessageInTransaction(false);

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(2);
    message.setTransactionId(10);
    message.setLastMessageInTransaction(false);
    ExecutionReportMessage message2 = new ExecutionReportMessage();
    message2.setOrderId(3);
    message2.setTransactionId(10);
    message2.setLastMessageInTransaction(true);

    queue.add(invalid);
    queue.add(message);
    queue.add(message2);

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(2, list.size());
    Assert.assertEquals(message, list.get(0));
    Assert.assertEquals(list.get(1), message2);
  }
}
