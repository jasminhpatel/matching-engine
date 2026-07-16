package com.solfini.util.benchmark;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import java.util.Arrays;

public class LatencyDistributionBenchmark extends Benchmark {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LatencyDistributionBenchmark.class);

  private static final long DEFAULT_BATCH = 100_000;
  private static final int BUCKET_COUNT = 11;
  private static final int BUCKET_SIZE = 10_000_000; // 10 ms per bucket, in nanoseconds

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

      // Clamp on the long quotient BEFORE narrowing to int, so a huge latency
      // can never wrap during the cast and land inside a valid bucket index.
      final long bucketIndex = latency / BUCKET_SIZE;
      final int bucket;
      if (bucketIndex >= buckets.length) {
        bucket = buckets.length - 1;
      } else if (bucketIndex < 0) {
        bucket = 0;
      } else {
        bucket = (int) bucketIndex;
      }

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
      final long safeCount = count;
      if (safeCount > 0) {
        final StringBuilder builder = new StringBuilder();

        lastAverage = 0.000001 * total / safeCount;

        builder.append("average: ").append(format(lastAverage)).append(" ms ");
        builder.append("min: ").append(format(0.000001 * min)).append(" ");
        builder.append("max: ").append(format(0.000001 * max)).append(" ");
        builder.append("distribution ");

        // Cumulative distribution as plain text.
        // "up to Nms:X%"  -> percentage of samples with latency below N ms
        // "over Nms:X%"    -> percentage of samples in the final overflow bucket
        long cumulative = 0;
        boolean started = false;
        for (int i = 0; i < buckets.length; ++i) {
          cumulative += buckets[i];

          if (buckets[i] > 0) {
            started = true;
          }

          if (i < buckets.length - 1) {
            if (started) {
              final long upperMs = ((long) BUCKET_SIZE * (i + 1)) / 1_000_000;
              final double pct = 100.0 * cumulative / safeCount;
              builder.append("up to ").append(upperMs).append("ms:").append(format(pct)).append("% ");
            }
          } else {
            final long lowerMs = ((long) BUCKET_SIZE * i) / 1_000_000;
            final double pct = 100.0 * buckets[i] / safeCount;
            builder.append("over ").append(lowerMs).append("ms:").append(format(pct)).append("% ");
          }

          if (cumulative == safeCount) {
            break;
          }
        }

        Arrays.fill(buckets, 0);

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