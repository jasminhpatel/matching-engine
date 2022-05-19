package com.solfini.matchengine.message;

import org.junit.Assert;
import org.junit.Test;

// Thread.sleep isn't guarenteed to sync memory, yet it does
// https://stackoverflow.com/questions/42417636/what-is-the-relationship-between-thread-sleep-and-happens-
// https://docs.oracle.com/javase/specs/jls/se7/html/jls-17.html#jls-17.4.5
public class SleepTest implements Runnable {

  private boolean active = true;

  @Override public void run() {
    while (active) {
      try {
        Thread.sleep(0);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    Assert.assertFalse(active);
  }

  @Test public void interThreadVariableRead() throws Exception {
    SleepTest test = new SleepTest();
    Thread thread = new Thread(test);
    thread.start();

    Thread.sleep(10000);
    Assert.assertTrue(test.active);
    test.active = false;
    Thread.sleep(1000);
    Assert.assertFalse(test.active);
  }
}
