package com.solfini.util.benchmark;

import java.text.NumberFormat;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

public class Benchmark {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(Benchmark.class);

  private final String name;
  private final NumberFormat format = NumberFormat.getInstance();

  protected Benchmark(final String name) {
    this.name = name;
    format.setGroupingUsed(true);
  }

  protected String format(final long value) {
    return format.format(value);
  }

  protected String format(final double value) {
    return format.format(value);
  }

  protected void log(final String message) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(Constants.LOG_FMT_4, "BENCHMARK ", name, ": ", message);
    }
  }
}
