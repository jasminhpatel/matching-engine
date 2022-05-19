package com.solfini.common;

import java.util.ArrayList;
import com.solfini.util.LogLevel;
import com.solfini.util.TimeUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionalOutputManyToOneConcurrentArrayQueue extends ManyToOneConcurrentArrayQueueCustom<Message> {

  private boolean transactionStarted = false;
  private final ArrayList<Message> transactionQueue = new ArrayList<>();
  private static final Logger LOGGER = LoggerFactory.getLogger(TransactionalOutputManyToOneConcurrentArrayQueue.class);

  public TransactionalOutputManyToOneConcurrentArrayQueue(final int requestedCapacity, final String name) {
    super(requestedCapacity, name);
  }

  // Starts the transaction. If the transaction is already started and attempting
  // to start again will end previous transaction.
  public final void beginTransaction() {
    if (transactionStarted) {
      LOGGER.error("Already started transaction present. Restarting a new transaction.");
      endTransaction();
    }

    transactionStarted = true;
  }

  // Ends the transaction. This stamps a unique transaction id to all messages.
  // The last message of the transaction is also marked.
  public final void endTransaction() {
    if (!transactionStarted) {
      throw new IllegalStateException("Transaction not started.");
    }

    final long id = TimeUtil.getTime();

    if (LogLevel.debug()) {
      LOGGER.debug("New transaction created. Id: {}, MessageCount: {}", id, transactionQueue.size());
    }

    for (int i = 0; i < transactionQueue.size(); ++i) {
      final Message e = transactionQueue.get(i);
      e.setTransactionId(id);
      e.setLastMessageInTransaction(i == (transactionQueue.size() - 1));

      addGuaranteedImpl(e);
    }

    this.transactionQueue.clear();

    transactionStarted = false;
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
    transactionStarted = false;
    transactionQueue.clear();
    super.clear();
  }

  private final boolean queue(final Message e) {
    if (e == null) {
      LOGGER.error("Ignoring null message");
      return true;
    }

    if (!transactionStarted) {
      return addGuaranteedImpl(e);
    }

    transactionQueue.add(e);
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
