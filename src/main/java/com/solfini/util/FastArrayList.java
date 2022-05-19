package com.solfini.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Consumer;

public class FastArrayList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
  private static final long serialVersionUID = -6100844634084609544L;
  private static final int INCREMENT_SIZE = 32;
  private static final Object[] EMPTY_ELEMENTDATA = {};
  private static final String NULL = "null";

  private int size;
  private Object[] elementData;

  public FastArrayList(final int initialCapacity) {
    if (initialCapacity > 0)
      this.elementData = new Object[initialCapacity];
    else if (initialCapacity == 0)
      this.elementData = EMPTY_ELEMENTDATA;
    else
      throw new IllegalArgumentException("Illegal Capacity: " + initialCapacity);
  }

  public FastArrayList() {
    this.elementData = EMPTY_ELEMENTDATA;
  }

  public FastArrayList(final Collection<? extends E> c) {
    elementData = c.toArray();
    if ((size = elementData.length) != 0) {
      // c.toArray might (incorrectly) not return Object[] (see 6260652)
      if (elementData.getClass() != Object[].class)
        elementData = Arrays.copyOf(elementData, size, Object[].class);
    } else {
      // replace with empty array.
      this.elementData = EMPTY_ELEMENTDATA;
    }
  }

  private void resize(final int newCapacity) {
    elementData = Arrays.copyOf(elementData, newCapacity);
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isEmpty() {
    return (size == 0);
  }

  @Override
  public boolean add(final E e) {
    if (size + 1 > elementData.length)
      resize(elementData.length + INCREMENT_SIZE);
    elementData[size++] = e;
    return true;
  }

  @Override
  public void clear() {
    size = 0;
  }

  @Override
  public E get(final int index) {
    return (E) elementData[index];
  }

  @Override
  public E set(final int index, final E element) {
    E temp = (E) elementData[index];
    elementData[index] = element;
    return temp;
  }

  @Override
  public Object[] toArray() {
    return Arrays.copyOf(elementData, size);
  }

  @Override
  public <T> T[] toArray(T[] a) {
    if (a.length < size)
      // Make a new array of a's runtime type, but my contents:
      return (T[]) Arrays.copyOf(elementData, size, a.getClass());
    System.arraycopy(elementData, 0, a, 0, size);
    if (a.length > size)
      a[size] = null;
    return a;
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    final Object[] a = c.toArray();
    final int numNew = a.length;
    if (size + 1 + numNew > elementData.length)
      resize(elementData.length + numNew + INCREMENT_SIZE);
    System.arraycopy(a, 0, elementData, size, numNew);
    size += numNew;
    return numNew != 0;
  }

  @Override
  public boolean contains(final Object o) {
    return indexOf(o) >= 0;
  }

  @Override
  public int indexOf(final Object o) {
    if (o == null) {
      for (int i = 0; i < size; i++)
        if (elementData[i] == null)
          return i;
    } else {
      for (int i = 0; i < size; i++)
        if (o.equals(elementData[i]))
          return i;
    }
    return -1;
  }

  @Override
  public boolean remove(final Object o) {
    if (o == null) {
      for (int index = 0; index < size; index++)
        if (elementData[index] == null) {
          fastRemove(index);
          return true;
        }
    } else {
      for (int index = 0; index < size; index++)
        if (o.equals(elementData[index])) {
          fastRemove(index);
          return true;
        }
    }
    return false;
  }

  /*
   * Private remove method that skips bounds checking and does not return the value removed.
   */
  private void fastRemove(final int index) {
    int numMoved = size - index - 1;
    if (numMoved > 0)
      System.arraycopy(elementData, index + 1, elementData, index, numMoved);
    elementData[--size] = null; // clear to let GC do its work
  }

  @Override
  public Iterator<E> iterator() {
    return new Itr();
  }

  /**
   * An optimized version of AbstractList.Itr
   */
  private class Itr implements Iterator<E> {
    int cursor; // index of next element to return
    int lastRet = -1; // index of last element returned; -1 if no such

    public boolean hasNext() {
      return cursor != size;
    }

    @SuppressWarnings("unchecked")
    public E next() {
      int i = cursor;
      if (i >= size)
        throw new NoSuchElementException();
      Object[] elementData = FastArrayList.this.elementData;
      if (i >= elementData.length)
        throw new ConcurrentModificationException();
      cursor = i + 1;
      return (E) elementData[lastRet = i];
    }

    @Override
    public void remove() {
      if (lastRet < 0)
        throw new IllegalStateException();

      try {
        FastArrayList.this.remove(lastRet);
        cursor = lastRet;
        lastRet = -1;
      } catch (IndexOutOfBoundsException ex) {
        throw new ConcurrentModificationException();
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void forEachRemaining(Consumer<? super E> consumer) {
      Objects.requireNonNull(consumer);
      final int size = FastArrayList.this.size;
      int i = cursor;
      if (i >= size) {
        return;
      }
      final Object[] elementData = FastArrayList.this.elementData;
      if (i >= elementData.length) {
        throw new ConcurrentModificationException();
      }
      while (i != size) {
        consumer.accept((E) elementData[i++]);
      }
      // update once at end of iteration to reduce heap write traffic
      cursor = i;
      lastRet = i - 1;
    }
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean addAll(int index, Collection<? extends E> c) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    // TODO Auto-generated method stub
    return false;
  }


  @Override
  public void add(int index, E element) {
    // TODO Auto-generated method stub

  }

  @Override
  public E remove(int index) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int lastIndexOf(Object o) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public ListIterator<E> listIterator() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ListIterator<E> listIterator(int index) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < size; ++i) {
      if (0 != i) {
        sb.append(", ");
      }
      sb.append(null == elementData[i] ? NULL : String.valueOf(elementData[i].toString()));
    }
    sb.append("]");

    return sb.toString();
  }

  @Override
  public FastArrayList<E> clone() {
    return new FastArrayList<>(this);
  }
}
