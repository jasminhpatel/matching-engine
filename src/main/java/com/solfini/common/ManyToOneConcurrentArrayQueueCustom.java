package com.solfini.common;

import com.solfini.util.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chris Mack
 *
 */
public class ManyToOneConcurrentArrayQueueCustom<E> extends org.agrona.concurrent.ManyToOneConcurrentArrayQueue<E> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ManyToOneConcurrentArrayQueueCustom.class);

  private final String name;

  public ManyToOneConcurrentArrayQueueCustom(final int requestedCapacity, final String name) {
    super(requestedCapacity);
    this.name = name;
  }

  public final String getName() {
    return name;
  }

  // blockAdd to guarantee that the message is added to the queue
  public boolean addGuaranteed(final E e) {
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
