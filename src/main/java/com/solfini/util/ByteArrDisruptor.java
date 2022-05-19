package com.solfini.util;

import org.slf4j.LoggerFactory;

import com.solfini.common.Constants;

import org.slf4j.Logger;

/**
 * supports only 1 producer and 1 consumer
 */
public class ByteArrDisruptor implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(ByteArrDisruptor.class);

  private final byte[][] ring;
  private final int size;
  private volatile long sequenceBarrier;
  private volatile long commitBarrier;
  private volatile long readBarrier;

  private ByteArrDisruptor(int size) {
    this.size = size;
    ring = new byte[size][];
  }

  public int size() {
    return size;
  }

  public static final ByteArrDisruptor buildDisruptor(final int size) {
    if ((size & -size) != size)
      throw new IllegalArgumentException("size must be a power of 2");
    return new ByteArrDisruptor(size);
  }

  public final synchronized boolean synchronizedOffer(final byte[] o) {
    return offer(o);
  }

  public final boolean offer(final byte[] o) {
    if (sequenceBarrier - readBarrier >= size)
      return false;

    final int index = (int) (sequenceBarrier % size);
    sequenceBarrier++;
    ring[index] = o;
    commitBarrier++;
    return true;
  }

  public final synchronized void synchronizedAdd(final byte[] o) {
    add(o);
  }

  public final void add(final byte[] o) {
    while (sequenceBarrier - readBarrier >= size) {
      // busy wait if nothing is available
      try {
        Thread.sleep(0);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        LOGGER.error(ERROR_LOG, e);
      }
    }
    final int index = (int) (sequenceBarrier % size);
    sequenceBarrier++;
    ring[index] = o;
    commitBarrier++;
  }

  public final byte[] get() {
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
    final byte[] o = ring[index];
    readBarrier++;

    return o;
  }

  public final byte[][] batchGet() {
    while (commitBarrier == readBarrier) {
      // busy wait if nothing is available
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        LOGGER.error(ERROR_LOG, e);
      }
    }
    final byte[][] result = new byte[(int) (commitBarrier - readBarrier)][];
    for (int i = 0; i < result.length; i++) {
      int index = (int) ((readBarrier + i) % size);
      result[i] = ring[index];
    }
    readBarrier += result.length;
    return result;
  }

  public final byte[][] tryBatchGet() {
    if (commitBarrier == readBarrier)
      return null;

    final byte[][] result = new byte[(int) (commitBarrier - readBarrier)][];
    for (int i = 0; i < result.length; i++) {
      int index = (int) ((readBarrier + i) % size);
      result[i] = ring[index];
    }
    readBarrier += result.length;
    return result;
  }

  public final byte[] tryGet() {
    if (commitBarrier == readBarrier)
      return null;

    int index = (int) (readBarrier % size);
    final byte[] o = ring[index];
    readBarrier++;

    return o;
  }

  public static final class Producer implements Runnable {
    private ByteArrDisruptor disruptor;

    public Producer(ByteArrDisruptor disruptor) {
      this.disruptor = disruptor;
    }

    public void run() {
      for (int i = 0; i < 1_000_000; i++) {
        disruptor.synchronizedAdd(new byte[] {});
      }
    }
  }

  public static final class Consumer implements Runnable {
    private ByteArrDisruptor disruptor;

    public Consumer(final ByteArrDisruptor disruptor) {
      this.disruptor = disruptor;
    }

    public void run() {
      for (int i = 0; i < 1000000; i++) {
        disruptor.batchGet();
      }
    }
  }
}
