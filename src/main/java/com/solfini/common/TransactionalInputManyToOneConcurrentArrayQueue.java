package com.solfini.common;

import com.solfini.util.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;

// Queue implementation which lets only complete transactions to pass through. A transaction is marked with a new transaction
// id and the last message is marked as end of transaction.
// Messages without a transaction is passed through only if there is no active transaction is present.
public class TransactionalInputManyToOneConcurrentArrayQueue extends ManyToOneConcurrentArrayQueueCustom<Message> {

  private final ArrayList<Message> transactionQueue = new ArrayList<>();
  private long id = 0;
  private static final Logger LOGGER = LoggerFactory.getLogger(TransactionalInputManyToOneConcurrentArrayQueue.class);

  public TransactionalInputManyToOneConcurrentArrayQueue(final int requestedCapacity, final String name) {
    super(requestedCapacity, name);
  }

  @Override
  public final boolean addGuaranteed(final Message e) {
    return queue(e);
  }

  @Override
  public final boolean offer(final Message e) {
    return queue(e);
  }

  @Override
  public final boolean add(final Message e) {
    return queue(e);
  }

  @Override
  public final void clear() {
    transactionQueue.clear();
    id = 0;

    super.clear();
  }

  // Flushes all transactions
  public final void flush() {
    if (LogLevel.debug()) {
      LOGGER.debug("Transaction ended. ID:{} ", id);
    }

    Iterator<Message> it = transactionQueue.iterator();

    while (it.hasNext()) {
      Message elem = it.next();
      addGuaranteedImpl(elem);
    }

    transactionQueue.clear();
    id = 0;
  }

  private final boolean queue(final Message e) {

    // Messages with transaction id zero is passed through only if there is no
    // active transaction present.
    // When the ME is primary all input messages goes through this path.
    // When ME is secondary, all input messages should have a transaction id.
    if (e.getTransactionId() == 0) {
      if (transactionQueue.isEmpty()) {
        return addGuaranteedImpl(e);
      }

      LOGGER.error(
          "Message with transaction id 0 received while in an active transaction. Ignoring the message. MessageType: {}, PendingMessageCount: {}",
          e.getMessageType(), transactionQueue.size());
      return true;
    }

    // Control messages have the special transaction id 1. They are passed through.
    if (e.getTransactionId() == 1) {
      LOGGER.warn("Message with transaction id 1 received. Passing through. MessageType: {}, PendingMessageCount: {}",
          e.getMessageType(), transactionQueue.size());
      return addGuaranteedImpl(e);
    }

    // If a new transaction id received, while previous transaction has not ended,
    // Drop current transaction.
    if ((id != 0) && (e.getTransactionId() != id)) {
      Iterator<Message> it = transactionQueue.iterator();

      while (it.hasNext()) {
        Message elem = it.next();
        LOGGER.error("Incomplete transaction. Dropping messages. TransactionId: {} MessageType: {}",
            elem.getTransactionId(), elem.getMessageType());
      }

      transactionQueue.clear();
    }

    // Add this message
    id = e.getTransactionId();
    transactionQueue.add(e);

    // Flush queue if the message is last one in the transaction.
    if (e.isLastMessageInTransaction()) {
      if (LogLevel.debug()) {
        LOGGER.debug("Last message in transaction. ID:{} ", id);
      }
      flush();
    }

    return true;
  }

  private final boolean addGuaranteedImpl(final Message e) {
    final boolean rc = super.offer(e);
    if (rc)
      return rc;

    try {
      if (LogLevel.warn()) {
        LOGGER.warn("Blocking on queue: name={}, size={}, capacity={}, message={}", getName(), size(), capacity(), e);
      }
      while (!super.offer(e)) {
        Thread.sleep(0);
      }
    } catch (Exception e2) {
      LOGGER.error("Failed to add to queue: name={}, message={}", getName(), e, e2);
      return false;
    }
    return true;
  }
}
