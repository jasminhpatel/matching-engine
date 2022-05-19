package com.solfini.util.benchmark;

public class RateBenchmark extends Benchmark {

  private static final long DEFAULT_BATCH = 100_000;

  private final long batch;
  private long start;
  private long split;
  private long count;

  public RateBenchmark(final String name) {
    this(name, DEFAULT_BATCH);
  }

  public RateBenchmark(final String name, final long batch) {
    super(name);
    this.batch = batch;
  }

  public void sample() {
    if (count == 0) {
      start = System.currentTimeMillis();
      split = start;
      count = 0;
    }

    ++count;

    if (count % batch == 0) {
      long now = System.currentTimeMillis();
      log(format(count) + " records, " + format(now - start) + " ms, " + format((1_000 * count) / (now - start)) + " rec/s "
        + "split " + format(now - split) + " ms, " + format((1_000 * batch) / (now - split)) + " rec/s ");

      split = now;
    }
  }
}
