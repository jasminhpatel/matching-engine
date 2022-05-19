package com.solfini.util;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 *
 * @author Chris Mack
 *
 */
public final class TickerTrie<V> {
  private final TickerTrieNode<V> root;

  public TickerTrie(final Map<String, V> map) {
    root = new TickerTrieNode<>();
    final Set<Entry<String, V>> set = map.entrySet();
    for (final Entry<String, V> entry : set) {
      root.add(entry.getKey(), entry);
    }
  }

  public TickerTrie() {
    root = new TickerTrieNode<>();
  }

  public final void add(final String key, final V v) {
    root.add(key, v);
  }

  public final V get(final String key) {
    return root.get(key);
  }

  private static final class TickerTrieNode<V> {
    private final char SPACE = ' ';
    private final TickerTrieNode<V>[] arr;
    private V value;

    public TickerTrieNode() {
      arr = new TickerTrieNode[100];
    }

    public final void add(final String subs, final V v) {
      if (subs == null || subs.length() == 0)
        return;
      final int index = subs.charAt(0) - SPACE;
      if (arr[index] == null)
        arr[index] = new TickerTrieNode<>();
      if (subs.length() == 1) {
        arr[index].value = v;
      } else {
        arr[index].add(subs, 1, v);
      }
    }

    private final void add(final String subs, final int indx, final V v) {
      if (subs.length() == indx) {
        value = v;
      } else {
        final int index = subs.charAt(indx) - SPACE;
        if (arr[index] == null)
          arr[index] = new TickerTrieNode<>();
        arr[index].add(subs, indx + 1, v);
      }
    }

    public final void add(final String subs, Map.Entry<String, V> entry) {
      if (subs == null || subs.length() == 0)
        return;
      final int index = subs.charAt(0) - SPACE;
      if (arr[index] == null)
        arr[index] = new TickerTrieNode<>();
      if (subs.length() == 1) {
        arr[index].value = entry.getValue();
      } else {
        arr[index].add(subs, 1, entry);
      }
    }

    private final void add(final String subs, final int indx, Map.Entry<String, V> entry) {
      if (subs.length() == indx) {
        value = entry.getValue();
      } else {
        final int index = subs.charAt(indx) - SPACE;
        if (arr[index] == null)
          arr[index] = new TickerTrieNode<>();
        arr[index].add(subs, indx + 1, entry);
      }
    }

    public final V get(final String subs) {
      if (subs == null || subs.length() == 0)
        return null;
      final int index = subs.charAt(0) - SPACE;
      if (subs.length() == 1)
        return arr[index].value;
      else {
        if (arr[index] == null)
          return null;
        else
          return arr[index].get(subs, 1);
      }
    }

    private final V get(final String subs, final int indx) {
      if (subs.length() == indx)
        return value;
      else {
        final int index = subs.charAt(indx) - SPACE;
        if (arr[index] == null)
          return null;
        else
          return arr[index].get(subs, indx + 1);
      }
    }

    public final Object getValue() {
      return value;
    }

    public final void setValue(V value) {
      this.value = value;
    }
  }
}


