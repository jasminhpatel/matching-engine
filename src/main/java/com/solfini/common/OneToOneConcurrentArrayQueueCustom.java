package com.solfini.common;

import com.solfini.util.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chris Mack
 *
 */
public class OneToOneConcurrentArrayQueueCustom<E> extends org.agrona.concurrent.OneToOneConcurrentArrayQueue<E> {
  private static final Logger LOGGER = LoggerFactory.getLogger(OneToOneConcurrentArrayQueueCustom.class);

  private final String name;

  public OneToOneConcurrentArrayQueueCustom(final int requestedCapacity, final String name) {
    super(requestedCapacity);
    this.name = name;
  }

  public final String getName() {
    return name;
  }

  // blockAdd to guarantee that the message is added to the queue
  public final boolean addGuaranteed(final E e) {
    final boolean rc = offer(e);
    if (rc)
      return rc;

    try {
      if (LogLevel.warn()) {
        LOGGER.warn("Blocking on queue: name={}, size={}, capacity={}, message={}", name, size(), capacity(), e);
      }
      while (!offer(e)) {
        Thread.sleep(0);
      }
    } catch (Exception e2) {
      LOGGER.error("Failed to add to queue: name={}, message={}", name, e, e2);
      return false;
    }
    return true;
  }

}
