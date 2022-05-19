package com.solfini.matchengine.orderbook;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.message.internal.Order;

/**
 *
 * @author Chris Mack
 *
 */
public class OrderBookPriceLevel implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(OrderBookPriceLevel.class);

  private Order head;
  private Order tail;

  public final Order getHead() {
    return head;
  }

  public final void setHead(final Order value) {
    if (head != null) {

      if (head.getPriceInt() != value.getPriceInt()) {
        LOGGER.error(LOG_FMT_4, "priceLevel setHead called, invalid price, head=", head, VALUE_EQ, value);
      }

      value.setNext(head);
      value.setPrev(null);
      head.setPrev(value);
      head = value;
    } else {
      // if head is null, tail must also be null
      head = value;
      tail = value;
      value.setPrev(null);
      value.setNext(null);
    }
  }

  public final Order getTail() {
    return tail;
  }

  public final void addToTail(final Order value) {

    if (tail != null) {

      if (tail.getPriceInt() != value.getPriceInt()) {
        LOGGER.error(LOG_FMT_4, "priceLevel addToTail called, invalid price, tail=", tail, VALUE_EQ, value);
      }

      tail.setNext(value);
      value.setPrev(tail);
      value.setNext(null);
      tail = value;
    } else {
      // if tail is null, head must also be null
      head = value;
      tail = value;
      value.setPrev(null);
      value.setNext(null);
    }
  }

  public final void addToLmmTail(final Order value) {
    if (head == null || !head.isLmm()) {
      setHead(value);
      return;
    }

    if (tail != null && tail.isLmm()) {
      addToTail(value);
      return;
    }

    Order next = head;
    while (next.isLmm()) {
      next = next.getNext();
    }

    Order prev = next.getPrev();
    prev.setNext(value);
    value.setNext(next);
    next.setPrev(value);
    value.setPrev(prev);
  }

  public final void remove(final Order tmpPtr) {
    if (head == null) {
      LOGGER.error(LOG_FMT_2, "priceLevel remove called, head=null, tmpPtr=", tmpPtr);
    } else if (head.getPriceInt() != tmpPtr.getPriceInt()) {
      LOGGER.error(LOG_FMT_4, "priceLevel remove called, invalid price, head=", head, VALUE_EQ, tmpPtr);
    }

    // remove from bookArr
    final Order prev = tmpPtr.getPrev();
    final Order next = tmpPtr.getNext();

    if (prev == null && next == null) { // remove only node
      head = null;
      tail = null;
      tmpPtr.setPrev(null);
      tmpPtr.setNext(null);
      return;
    }
    if (prev == null) { // node is head
      next.setPrev(null);
      head = next;
      tmpPtr.setPrev(null);
      tmpPtr.setNext(null);
      return;
    }
    if (next == null) { // node is tail
      prev.setNext(null);
      tail = prev;
      tmpPtr.setPrev(null);
      tmpPtr.setNext(null);
      return;
    }

    // node is in middle
    next.setPrev(prev);
    prev.setNext(next);
    tmpPtr.setPrev(null);
    tmpPtr.setNext(null);
  }

  public final void clear() {
    head = null;
    tail = null;
  }

  public String toString() {
    final StringBuilder sb = new StringBuilder("[priceLevel]\n");
    Order tmp = head;
    for (int i = 0; i < 10_000; i++) {
      if (tmp == null)
        break;
      sb.append("priceLevel").append(i).append("->").append(tmp).append("\n");
      tmp = tmp.getNext();
    }
    return sb.toString();
  }
}
