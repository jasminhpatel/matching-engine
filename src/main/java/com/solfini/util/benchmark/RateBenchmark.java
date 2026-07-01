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

/*  public void sample() {
    if (count == 0) {
      start = System.currentTimeMillis();
      split = start;
      count = 0;
    }

    ++count;

    if (count % batch == 0) {
      long now = System.currentTimeMillis();
      log(format(count) + " records, totalTime: " + format(now - start) + " ms, "
          + format((1_000 * count) / (now - start)) + " rec/s "
        + " timeForLast100000 " + format(now - split) + " ms, rateForLast100000"
          + format((1_000 * batch) / (now - split)) + " rec/s ");

      split = now;
    }
  }*/

  public void sample() {
    if (count == 0) {
      start = System.nanoTime();
      split = start;
    }

    ++count;

    if (count % batch == 0) {
      long now = System.nanoTime();
      long totalElapsedNs = now - start;
      long batchElapsedNs = now - split;
      long totalElapsedMs = totalElapsedNs / 1_000_000;
      long batchElapsedMs = batchElapsedNs / 1_000_000;
      long avgRate = totalElapsedNs > 0
          ? (1_000_000_000L * count) / totalElapsedNs
          : 0;
      long batchRate = batchElapsedNs > 0
          ? (1_000_000_000L * batch) / batchElapsedNs
          : 0;

      log(format(count) + " records, totalTime: " + format(totalElapsedMs) + " ms, "
          //+ "avgRate: " + format(avgRate) + " rec/s, "
          + "timeForLast100K: " + format(batchElapsedMs) + " ms, "
          + "rateForLast100K: " + format(batchRate) + " rec/s");
      split = now;
    }

  }
}
