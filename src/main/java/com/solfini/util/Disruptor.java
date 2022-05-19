package com.solfini.util;

import org.slf4j.LoggerFactory;

import com.solfini.common.Constants;

import org.slf4j.Logger;

/**
 * supports only 1 producer and 1 consumer
 */
public class Disruptor implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(Disruptor.class);

  private final Object[] ring;
  private final int size;
  private volatile long sequenceBarrier;
  private volatile long commitBarrier;
  private volatile long readBarrier;

  public Disruptor(int size) {
    this.size = size;
    ring = new Object[size];
  }

  public int size() {
    return size;
  }

  public static final Disruptor buildDisruptor(final int size) {
    if ((size & -size) != size)
      throw new IllegalArgumentException("size must be a power of 2");
    return new Disruptor(size);
  }

  public final synchronized boolean synchronizedOffer(final Object o) {
    return offer(o);
  }

  public final boolean offer(final Object o) {
    if (sequenceBarrier - readBarrier >= size)
      return false;

    final int index = (int) (sequenceBarrier % size);
    sequenceBarrier++;
    ring[index] = o;
    commitBarrier++;
    return true;
  }

  public final synchronized void synchronizedAdd(final Object o) {
    add(o);
  }

  public final void add(final Object o) {
    while (sequenceBarrier - readBarrier >= size) {
      // busy wait if nothing is available
      try {
        Thread.sleep(0);
      } catch (InterruptedException e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
    final int index = (int) (sequenceBarrier % size);
    sequenceBarrier++;
    ring[index] = o;
    commitBarrier++;
  }

  public final Object get() {
    while (commitBarrier == readBarrier) {
      // busy wait if nothing is available
      try {
        Thread.sleep(0);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        LOGGER.error(ERROR_LOG, e);
      }
    }
    int index = (int) (readBarrier % size);
    final Object o = ring[index];
    readBarrier++;

    return o;
  }

  public final Object[] batchGet() {
    while (commitBarrier == readBarrier) {
      // busy wait if nothing is available
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        LOGGER.error(ERROR_LOG, e);
      }
    }
    final Object[] result = new Object[(int) (commitBarrier - readBarrier)];
    for (int i = 0; i < result.length; i++) {
      int index = (int) ((readBarrier + i) % size);
      result[i] = ring[index];
    }
    readBarrier += result.length;
    return result;
  }

  public final Object[] tryBatchGet() {
    if (commitBarrier == readBarrier)
      return null;

    final Object[] result = new Object[(int) (commitBarrier - readBarrier)];
    for (int i = 0; i < result.length; i++) {
      int index = (int) ((readBarrier + i) % size);
      result[i] = ring[index];
    }
    readBarrier += result.length;
    return result;
  }

  public final Object tryGet() {
    if (commitBarrier == readBarrier)
      return null;

    int index = (int) (readBarrier % size);
    final Object o = ring[index];
    readBarrier++;

    return o;
  }

  public static final class Producer implements Runnable {
    private Disruptor disruptor;

    public Producer(Disruptor disruptor) {
      this.disruptor = disruptor;
    }

    public void run() {
      for (int i = 0; i < 1_000_000; i++) {
        disruptor.synchronizedAdd(i); // autoboxed int
      }
    }
  }

  public static final class Consumer implements Runnable {
    private Disruptor disruptor;

    public Consumer(final Disruptor disruptor) {
      this.disruptor = disruptor;
    }

    public void run() {
      for (int i = 0; i < 1000000; i++) {
        Object[] result = disruptor.batchGet();
        int last = (Integer) result[result.length - 1];
        if (last == 999999)
          break;
      }
    }
  }
}
