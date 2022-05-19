package com.solfini.util;

import org.slf4j.LoggerFactory;

import com.solfini.common.Constants;

import org.slf4j.Logger;

/**
 * supports only 1 producer and 1 consumer
 */
public class ResizingDisruptor implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(ResizingDisruptor.class);
  private volatile int readState = 1;
  private volatile int writeState = 1;
  private Disruptor disruptor1;
  private Disruptor disruptor2;


  public ResizingDisruptor(int size) {
    if ((size & -size) != size) { // if size isn't power of 2, make it
      int temp = 4;
      while (temp < size && temp > 0) {
        temp *= 2;
      }
      size = temp;
    }
    try {
      disruptor1 = Disruptor.buildDisruptor(size);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public final synchronized boolean synchronizedOffer(final Object o) {
    return offer(o);
  }

  public final boolean offer(final Object o) {
    try {
      if (writeState == 1) {
        boolean rc = disruptor1.offer(o);
        if (rc)
          return true;
        else {
          while (readState == 2) {
            // block wait
          }

          disruptor2 = Disruptor.buildDisruptor(disruptor1.size() * 2);
          writeState = 2;
          return disruptor2.offer(o);
        }
      } else {
        boolean rc = disruptor2.offer(o);
        if (rc)
          return true;
        else {
          while (readState == 1) {
            // block wait
          }

          disruptor1 = Disruptor.buildDisruptor(disruptor2.size() * 2);
          writeState = 1;
          return disruptor1.offer(o);
        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return false;
  }

  public final Object[] tryBatchGet() {
    try {
      if (readState == 1) {
        Object[] rc = disruptor1.tryBatchGet();
        if ((rc == null || rc.length == 0) && writeState == 2) {
          readState = 2;
          return disruptor2.tryBatchGet();
        }
        return rc;
      } else {
        Object[] rc = disruptor2.tryBatchGet();
        if ((rc == null || rc.length == 0) && writeState == 1) {
          readState = 1;
          return disruptor1.tryBatchGet();
        }
        return rc;
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }

  public final Object tryGet() {
    try {
      if (readState == 1) {
        Object rc = disruptor1.tryGet();
        if (rc == null && writeState == 2) {
          readState = 2;
          return disruptor2.tryGet();
        }
        return rc;
      } else {
        Object rc = disruptor2.tryGet();
        if (rc == null && writeState == 1) {
          readState = 1;
          return disruptor1.tryGet();
        }
        return rc;
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }

  public static final class Producer implements Runnable {
    private ResizingDisruptor disruptor;

    public Producer(ResizingDisruptor disruptor) {
      this.disruptor = disruptor;
    }

    public void run() {
      for (int i = 0; i < 1_000_000; i++) {
        disruptor.offer(i); // autoboxed int
      }
    }
  }

  public static final class Consumer implements Runnable {
    private ResizingDisruptor disruptor;

    public Consumer(final ResizingDisruptor disruptor) {
      this.disruptor = disruptor;
    }

    public void run() {
      int expected = 0;
      for (int i = 0; i < 1000000; i++) {
        Object result = disruptor.tryGet();
        if (result != null) {
          int last = (Integer) result;
          if (last == 999999)
            break;
          if (expected != last)
            LOGGER.debug(LOG_FMT_4, "expected=", expected, ", last=", last);
          else
            expected = last + 1;
        }
      }
    }
  }
}
