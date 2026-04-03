package com.solfini.matchengine.liquidity;

import java.util.concurrent.atomic.AtomicLong;

public final class BalanceLruCache {
  private static final class Entry {
    volatile long timestamp;
    volatile double balance;
  }

  private final Entry[] buffer;
  private final int mask;
  private final AtomicLong writeCounter = new AtomicLong();

  public BalanceLruCache(final int capacityPowerOfTwo) {
    if (Integer.bitCount(capacityPowerOfTwo) != 1) {
      throw new IllegalArgumentException("Capacity must be power of 2");
    }
    this.buffer = new Entry[capacityPowerOfTwo];
    for (int i = 0; i < capacityPowerOfTwo; i++) {
      buffer[i] = new Entry();
    }
    this.mask = capacityPowerOfTwo - 1;
  }

  public void add(final long timestamp, final double balance) {
    final long sequence = writeCounter.getAndIncrement();
    final Entry entry = buffer[(int)(sequence & mask)];
    entry.timestamp = timestamp;
    entry.balance = balance;
  }

  public boolean addIfNew(final long timestamp, final double balance) {
    final long sequence = writeCounter.get();
    if (sequence > 0) {
      final Entry latest = buffer[(int) ((sequence - 1) & mask)];
      if (timestamp <= latest.timestamp) {
        return false; // stale or duplicate
      }
    }
    add(timestamp, balance);

    return true;
  }

  public double getLatest() {
    final long sequence = writeCounter.get() - 1;
    if (sequence < 0) return 0;
    return buffer[(int)(sequence & mask)].balance;
  }
}
