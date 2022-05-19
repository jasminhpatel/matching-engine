package com.solfini.matchengine.model.transaction;

import java.util.ArrayList;
import org.junit.After;
import org.junit.Test;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.common.TransactionalInputManyToOneConcurrentArrayQueue;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.model.orderbook.OrderBookTest;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;

public class TransactionQueueTest extends OrderBookTest {

  // Posts a message to the input queue of the matching engine.
  private void postMessage(final Message message) {
    TransactionalInputManyToOneConcurrentArrayQueue queue = Context.getReceiverToMatcherQueue();

    // Post to input
    queue.addGuaranteed(message);

    // Read from output
    final ArrayList<Message> list = new ArrayList<>();
    queue.drainTo(list, 1000);

    for (final Message m : list) {
      m.onMatcher();
    }
  }

  @After
  public void after() {
    clearMessages();
    Context.getReceiverToMatcherQueue().clear();
  }

  void post(int id, int transactionId, boolean end) {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, TimeInForce.DAY);
    order.setTransactionId(transactionId);
    order.setLastMessageInTransaction(end);

    postMessage(order);
  }

  void expect(int id) {
    expectMessage("ExecutionReportMessage", "securityId=12");

    expectOutput("BalanceAdminMessage", "senderCompId=me01");
    expectOutput("Order", "securityId=12");
  }

  @Test
  public void validTransactionIdAndEnd() {
    post(1, 100, true);
    expect(1);

    assertMessages();
    assertOutputMessages();
  }

  @Test
  public void validTransactionIdInvalidEndShouldBlock() {
    post(1, 100, false);

    // should not receive any messages as the transaction is not ended.

    assertMessages();
    assertOutputMessages();
  }

  @Test
  public void multiplevalidTransactionIdWithEachEnding() {
    post(1, 100, true);
    expect(1);
    post(2, 101, true);
    expect(2);

    assertMessages();
    assertOutputMessages();
  }

  @Test
  public void singleTransactionIdMulipleMessagesWithLastOneEnding() {
    post(1, 100, false);
    post(2, 100, true);

    expect(1);
    expect(2);

    assertMessages();
    assertOutputMessages();
  }

  @Test
  public void singleTransactionIdMultipleMessagesWithNoneEndingShouldBlock() {
    post(1, 100, false);
    post(2, 100, false);

    assertMessages();
    assertOutputMessages();
  }

  @Test
  public void startingANewTransactionWhenPreviousUnendedShouldDropPrevious() {
    post(1, 100, false);
    post(2, 101, true);

    expect(2);

    assertMessages();
    assertOutputMessages();
  }
}
