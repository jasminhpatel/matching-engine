package com.solfini.common;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;

public class ManyToManyConcurrentArrayQueueTest {

  class Validator implements Runnable {

    final ManyToManyConcurrentArrayQueueCustom<Integer> queue;
    volatile boolean done;

    public Validator(final ManyToManyConcurrentArrayQueueCustom<Integer> queue) {
      this.queue = queue;
      this.done = false;
    }

    public boolean done() {
      return done;
    }

    @Override
    public void run() {
      try {
        Thread.sleep(1000);

        List<Integer> list = new ArrayList<Integer>(10000);
        int count = 0;
        while (!done) {
          list.clear();
          queue.drainTo(list, 10000);

          for (int i = 0; i < list.size(); i++) {
            if (count != list.get(i).intValue()) {
              return;
            }
            count++;
          }

          if (count == 10000) {
            done = true;
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Test
  public void blockingAdd() {
    ManyToManyConcurrentArrayQueueCustom<Integer> queue = new ManyToManyConcurrentArrayQueueCustom<Integer>(1000, "Queue");
    Assert.assertEquals("Queue", queue.getName());

    Validator validator = new Validator(queue);
    Thread thread = new Thread(validator);
    thread.start();
    for (int i = 0; i < 10000; i++) {
      queue.addGuaranteed(i);
    }

    try {
      thread.join();
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail();
    }

    Assert.assertTrue(validator.done());
  }
}
