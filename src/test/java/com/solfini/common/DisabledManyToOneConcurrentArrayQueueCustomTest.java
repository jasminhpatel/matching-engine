package com.solfini.common;

import org.junit.Assert;
import org.junit.Test;

import com.solfini.common.DisabledManyToOneConcurrentArrayQueueCustom;

public class DisabledManyToOneConcurrentArrayQueueCustomTest {

  @Test
  public void offerAlwaysSucceedsButDoesNotAdd() {
    DisabledManyToOneConcurrentArrayQueueCustom<Integer> queue = new DisabledManyToOneConcurrentArrayQueueCustom<Integer>(1000, "test");

    Assert.assertNotNull(queue);
    Assert.assertTrue(queue.offer(null));
    Assert.assertTrue(queue.offer(100));
    Assert.assertTrue(queue.offer(new Integer(100)));
    Assert.assertEquals(0, queue.size());
  }

  @Test
  public void addGuaranteedAlwaysSucceedsButDoesNotAdd() {
    DisabledManyToOneConcurrentArrayQueueCustom<Integer> queue = new DisabledManyToOneConcurrentArrayQueueCustom<Integer>(1000, "test");

    Assert.assertNotNull(queue);
    Assert.assertTrue(queue.addGuaranteed(null));
    Assert.assertTrue(queue.addGuaranteed(100));
    Assert.assertTrue(queue.addGuaranteed(new Integer(100)));
    Assert.assertEquals(0, queue.size());
  }
}