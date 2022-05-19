package com.solfini.common;

/**
 * 
 * @author Chris Mack
 *
 */
public class DisabledManyToOneConcurrentArrayQueueCustom<E> extends ManyToOneConcurrentArrayQueueCustom<E> {

  public DisabledManyToOneConcurrentArrayQueueCustom(final int requestedCapacity, final String name) {
    super(requestedCapacity, name);
  }

  // don't do anything
  @Override
  public boolean offer(final E e) {
    return true;
  }

  // don't do anything
  @Override
  public boolean addGuaranteed(final E e) {
    return true;
  }

}
