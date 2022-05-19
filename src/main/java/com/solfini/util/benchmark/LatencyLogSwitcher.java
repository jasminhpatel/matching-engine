package com.solfini.util.benchmark;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.util.LogLevel;
import com.solfini.util.PropertyReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

// Disables log level at runtime if the latency exceeds a set value.
public class LatencyLogSwitcher implements Constants {
    private static final Logger LOGGER = LoggerFactory.getLogger(LatencyLogSwitcher.class);
    private final long maxLatencyForLogDisable = Long
            .parseLong(PropertyReader.getProperty("MAX_LATENCY_FOR_LOG_DISABLE_MS", "1000"));
    private final long minLatencyForLogEnable = Long
            .parseLong(PropertyReader.getProperty("MIN_LATENCY_FOR_LOG_ENABLE_MS", "500"));

    private Level logLevel = LogLevel.getLevel();
    private boolean levelSwitched = false;
    private boolean autoChangeEnabled = false;

    public LatencyLogSwitcher() {
        // constructor
    }

    public void update(double latency) {

        // Load the setting and check if it is switched at runtime
        final boolean setting = Context.isEnableAutoChangeLogLevel();
        if (autoChangeEnabled != setting) {

            // Log setting changed.
            if (setting) {
                LOGGER.info("LatencyLogSwitcher is enabled.  LogLevel: {}", logLevel);
            } else {
                LOGGER.info("LatencyLogSwitcher is disabled.  LogLevel: {}", logLevel);
            }

            autoChangeEnabled = setting;
        }

        // Ignore if the setting is disabled
        if (!autoChangeEnabled) {
            return;
        }

        if (!levelSwitched) {
            // Check if the latency exceeds threshold level
            if (latency > maxLatencyForLogDisable) {
                // Store the logging level
                logLevel = LogLevel.getLevel();

                // If the log level is already not debug or trace, there is no need to change
                // automatically.
                if (logLevel != Level.DEBUG && logLevel != Level.TRACE) {
                    return;
                }

                LOGGER.info(
                        "Automatically switching log level to INFO based on latency. Latency: {} ms Threshold: {} ms PrevLogLevel: {}",
                        latency, maxLatencyForLogDisable, logLevel);

                // Change the level to INFO
                LogLevel.changeLevelAfterStart(Level.INFO);

                levelSwitched = true;
            }
        } else {
            // Check if the latency has reverted back to normal. If so, enable logging.
            if (latency <= minLatencyForLogEnable) {
                LOGGER.info(
                        "Automatically enabling log level based on latency. Latency: {} ms Threshold: {} ms LogLevel: {}",
                        latency, minLatencyForLogEnable, logLevel);

                // Restore
                LogLevel.changeLevelAfterStart(logLevel);
                levelSwitched = false;
            }
        }
    }
}
