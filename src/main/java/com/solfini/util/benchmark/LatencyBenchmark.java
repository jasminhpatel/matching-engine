package com.solfini.util.benchmark;

public class LatencyBenchmark extends Benchmark {

  private static final int COUNT = 256;

  private class BenchmarkEntry {
    String label;
    long start;
    long duration;
    long count;
  }

  private final BenchmarkEntry[] entries = new BenchmarkEntry[COUNT];

  public LatencyBenchmark(final String name) {
    super(name);
  }

  public void start(final int index) {
    if (null == entries[index]) {
      entries[index] = new BenchmarkEntry();
    }

    entries[index].start = System.nanoTime();
  }

  public void label(final int index, final String label) {
    if (null == entries[index]) {
      entries[index] = new BenchmarkEntry();
    }

    entries[index].label = label;
  }

  public final void sample(final int index) {
    final long now = System.nanoTime();
    final BenchmarkEntry entry = entries[index];
    if (now >= entry.start) {
      entry.duration += now - entry.start;
      entry.count++;
    }
  }

  public final void report() {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < entries.length; ++i) {
      final BenchmarkEntry entry = entries[i];
      if (null != entry && 0 != entry.count && entry.duration > 0) {
        builder.append("\n").append(entry.label).append(" :").append(format((entry.duration / 1000.0) / entry.count))
                .append("ms, count:").append(format(entry.count));
        entry.duration = 0;
        entry.count = 0;
      }
    }

    log(builder.toString());
  }
}
