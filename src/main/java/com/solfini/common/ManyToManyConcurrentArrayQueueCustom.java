package com.solfini.common;

import com.solfini.util.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chris Mack
 *
 */
public class ManyToManyConcurrentArrayQueueCustom<E> extends org.agrona.concurrent.ManyToManyConcurrentArrayQueue<E> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ManyToManyConcurrentArrayQueueCustom.class);

  private final String name;

  public ManyToManyConcurrentArrayQueueCustom(final int requestedCapacity, final String name) {
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

  public static void main(String[] args) {
    ManyToManyConcurrentArrayQueueCustom queue = new ManyToManyConcurrentArrayQueueCustom(4096, "test");
    System.out.println("s1=" + queue.size());
    queue.addGuaranteed("test1");
    System.out.println("s2=" + queue.size());
    queue.addGuaranteed("test2");
    System.out.println("s3=" + queue.size());
    queue.poll();
    System.out.println("s4=" + queue.size());
    queue.poll();
    System.out.println("s5=" + queue.size());


  }
}
