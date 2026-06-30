package com.solfini.util.benchmark;

/**
 * Tracks min, max, and average latency from pairs of System.nanoTime() timestamps.
 * Reports values in microseconds (μs).
 *
 * Usage:
 *   MinMaxAvgLatency lat = new MinMaxAvgLatency("order-roundtrip");
 *   long start = System.nanoTime();
 *   // ... work ...
 *   lat.record(start, System.nanoTime());
 *   lat.report();
 */
public class MinMaxAvgLatency extends Benchmark {

  private long min = Long.MAX_VALUE;
  private long max = Long.MIN_VALUE;
  private long total = 0;
  private long count = 0;

  public MinMaxAvgLatency(final String name) {
    super(name);
  }

  /**
   * Records one latency sample from a start/end pair of System.nanoTime() values.
   * Negative or zero durations are ignored.
   */
  public void record(final long startNano, final long endNano) {
    final long durationNano = endNano - startNano;
    if (durationNano <= 0) {
      return;
    }

    if (durationNano < min) min = durationNano;
    if (durationNano > max) max = durationNano;
    total += durationNano;
    count++;
  }

  public long getMinNanos()  { return min == Long.MAX_VALUE ? 0 : min; }
  public long getMaxNanos()  { return max == Long.MIN_VALUE ? 0 : max; }
  public double getAvgNanos() { return count == 0 ? 0 : (double) total / count; }

  /** Min latency in microseconds. */
  public double getMinUs()  { return getMinNanos()  / 1_000.0; }
  /** Max latency in microseconds. */
  public double getMaxUs()  { return getMaxNanos()  / 1_000.0; }
  /** Average latency in microseconds. */
  public double getAvgUs()  { return getAvgNanos()  / 1_000.0; }

  public long getCount() { return count; }

  /** Logs min / avg / max in microseconds and resets all accumulators. */
  public void report() {
    if (count == 0) {
      log("no samples");
      return;
    }

    final String msg = "count:" + format(count)
        + "  min:" + format(getMinUs()) + " us"
        + "  avg:" + format(getAvgUs()) + " us"
        + "  max:" + format(getMaxUs()) + " us";

    log(msg);
    reset();
  }

  /** Returns a formatted summary string without logging or resetting. */
  public String summary() {
    if (count == 0) return "no samples";
    return "count:" + format(count)
        + "  min:" + format(getMinUs()) + " us"
        + "  avg:" + format(getAvgUs()) + " us"
        + "  max:" + format(getMaxUs()) + " us";
  }

  /** Clears all accumulated data. */
  public void reset() {
    min = Long.MAX_VALUE;
    max = Long.MIN_VALUE;
    total = 0;
    count = 0;
  }
}