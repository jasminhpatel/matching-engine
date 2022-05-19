package com.solfini.util.benchmark;

public class PoolBenchmark extends Benchmark {

  private static final long BATCH = 100_000;

  private long count = 0;
  private long startCount = 0;
  private long objectCount = 0;
  private long hitCount = 0;
  private long missCount = 0;
  private long remitCount = 0;

  public PoolBenchmark(final Class owner, final long capacity) {
    super(owner.getName());
    startCount = capacity;
    objectCount = startCount;
  }

  public void hit() {
    ++hitCount;
    if (++count % BATCH == 0) {
      print();
    }
  }

  public void miss() {
    ++missCount;
    ++objectCount;
    if (++count % BATCH == 0) {
      print();
    }
  }

  public void remit() {
    ++remitCount;
    if (++count % BATCH == 0) {
      print();
    }
  }

  private void print() {
    if ((100 * missCount) / count > 1) {
      log("start=" + format(startCount) + ", objects=" + format(objectCount) + ", hit=" + format(hitCount) + ", miss=" + format(missCount) + ", remit=" + format(remitCount));
    }
  }
}
