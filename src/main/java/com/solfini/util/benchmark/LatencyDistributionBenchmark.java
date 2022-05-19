package com.solfini.util.benchmark;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

public class LatencyDistributionBenchmark extends Benchmark {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LatencyDistributionBenchmark.class);

  private static final long DEFAULT_BATCH = 100_000;
  private static final int BUCKET_COUNT = 11;
  private static final int BUCKET_SIZE = 10_000_000;

  private final long batch;
  private final long[] buckets = new long[BUCKET_COUNT];

  private long min = Long.MAX_VALUE;
  private long max = Long.MIN_VALUE;
  private long total = 0;
  private long count = 0;
  private volatile double lastAverage = 0;
  private volatile long totalSampleCount = 0;

  public LatencyDistributionBenchmark(final String name) {
    this(name, DEFAULT_BATCH);
  }

  public LatencyDistributionBenchmark(final String name, final long batch) {
    super(name);
    this.batch = batch;
  }

  public void sample(final long latency) {
    if (latency > 0) {
      if (latency < min) {
        min = latency;
      }
      if (latency > max) {
        max = latency;
      }

      total += latency;
      ++count;
      ++totalSampleCount;

      int bucket = (int) (latency / BUCKET_SIZE);
      if (bucket >= buckets.length) {
        bucket = buckets.length - 1;
      }
      if (bucket < 0)
        bucket = 0;

      ++buckets[bucket];

      if (count >= batch) {
        report();
      }
    }
  }

  // Returns the average latency value
  public final double getAverage() {
    return lastAverage;
  }

  // Returns the total number of samples collected
  public final long getTotalSampleCount() {
    return totalSampleCount;
  }

  // Resets the sample count
  public final void resetTotalSamplesCount() {
    totalSampleCount = 0;
  }

  public void report() {
    try {
      long safecount = count;
      if (safecount > 0) {
        final StringBuilder builder = new StringBuilder();

        lastAverage = 0.000001 * total / safecount;

        builder.append("average: ").append(format(lastAverage)).append(" ms ");
        builder.append("min: ").append(format(0.000001 * min)).append(" ");
        builder.append("max: ").append(format(0.000001 * max)).append(" ");
        builder.append("distribution ");
        int sum = 0;
        boolean start = false;
        for (int i = 0; i < buckets.length; ++i) {
          if (buckets[i] > 0) {
            start = true;
          }

          sum += buckets[i];
          if (i < buckets.length - 1) {
            if (start) {
              builder.append("<").append((BUCKET_SIZE * (i + 1)) / 1_000_000).append("ms:").append(format((100 * sum) / safecount))
                  .append("% ");
            }
          } else {
            builder.append(">").append((BUCKET_SIZE * i) / 1_000_000).append("ms:").append(format((100 * buckets[i]) / safecount))
                .append("% ");
          }

          if (sum == safecount) {
            break;
          }
        }

        for (int i = 0; i < buckets.length; ++i) {
          buckets[i] = 0;
        }

        min = Long.MAX_VALUE;
        max = Long.MIN_VALUE;
        total = 0;
        count = 0;

        log(builder.toString());
      }
    } catch (Exception e) {
      LOGGER.error(Constants.ERROR_LOG, e);
    }
  }
}
