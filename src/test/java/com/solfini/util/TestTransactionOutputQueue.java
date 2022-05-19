package com.solfini.util;

import java.util.ArrayList;

import com.solfini.common.Message;
import com.solfini.common.TransactionalOutputManyToOneConcurrentArrayQueue;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import org.junit.Assert;
import org.junit.Test;

public class TestTransactionOutputQueue {
  @Test
  public void testAddWithoutTransaction() {
    TransactionalOutputManyToOneConcurrentArrayQueue queue = new TransactionalOutputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);

    queue.add(message);

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(1, list.size());
    Assert.assertEquals(message, list.get(0));
  }

  @Test
  public void testOfferWithoutTransaction() {
    TransactionalOutputManyToOneConcurrentArrayQueue queue = new TransactionalOutputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);

    queue.offer(message);

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(1, list.size());
    Assert.assertEquals(message, list.get(0));
  }

  @Test
  public void testAddGuaranteedWithoutTransaction() {
    TransactionalOutputManyToOneConcurrentArrayQueue queue = new TransactionalOutputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);

    queue.offer(message);

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(1, list.size());
    Assert.assertEquals(message, list.get(0));
  }

  @Test
  public void testAddWithTransaction() {
    TransactionalOutputManyToOneConcurrentArrayQueue queue = new TransactionalOutputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);

    queue.beginTransaction();
    queue.add(message);
    queue.endTransaction();

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(1, list.size());
    Assert.assertEquals(message, list.get(0));
  }

  @Test
  public void testClearWithIncompleteTransaction() {
    TransactionalOutputManyToOneConcurrentArrayQueue queue = new TransactionalOutputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);

    queue.beginTransaction();
    queue.offer(message);
    queue.clear();

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(0, list.size());
  }

  @Test
  public void testAddTransactionSingle() {
    TransactionalOutputManyToOneConcurrentArrayQueue queue = new TransactionalOutputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);

    queue.beginTransaction();
    queue.add(message);
    queue.endTransaction();

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(1, list.size());
    Assert.assertEquals(((ExecutionReportMessage) list.get(0)).getOrderId(), message.getOrderId());
    Assert.assertTrue(((ExecutionReportMessage) list.get(0)).isLastMessageInTransaction());
    Assert.assertNotEquals(((ExecutionReportMessage) list.get(0)).getTransactionId(), 0);
  }

  @Test
  public void testAddTransactionMultiple() {
    TransactionalOutputManyToOneConcurrentArrayQueue queue = new TransactionalOutputManyToOneConcurrentArrayQueue(1000, "A");

    ExecutionReportMessage message = new ExecutionReportMessage();
    message.setOrderId(1);
    ExecutionReportMessage message2 = new ExecutionReportMessage();
    message2.setOrderId(2);

    queue.beginTransaction();
    queue.add(message);
    queue.endTransaction();

    queue.beginTransaction();
    queue.add(message2);
    queue.endTransaction();

    ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    Assert.assertEquals(2, list.size());
    Assert.assertEquals(((ExecutionReportMessage) list.get(0)).getOrderId(), message.getOrderId());
    Assert.assertTrue(((ExecutionReportMessage) list.get(0)).isLastMessageInTransaction());
    Assert.assertNotEquals(((ExecutionReportMessage) list.get(0)).getTransactionId(), 0);
    Assert.assertEquals(((ExecutionReportMessage) list.get(1)).getOrderId(), message2.getOrderId());
    Assert.assertTrue(((ExecutionReportMessage) list.get(1)).isLastMessageInTransaction());
    Assert.assertNotEquals(((ExecutionReportMessage) list.get(1)).getTransactionId(), 0);

    Assert.assertNotEquals(((ExecutionReportMessage) list.get(1)).getTransactionId(),
            ((ExecutionReportMessage) list.get(0)).getTransactionId());
  }
}
