package com.solfini.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.solfini.pool.ReusableLogPool;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.sbe.encoder.PositionReportEncoder;
import com.solfini.sbe.encoder.PositionReportEncoder.PositionsGroupEncoder;
import com.solfini.user.User;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public final class ReusableLog {
  public static final int OFF = 0;
  public static final int FATAL = 1;
  public static final int ERROR = 2;
  public static final int WARN = 3;
  public static final int INFO = 4;
  public static final int DEBUG = 5;
  public static final int TRACE = 6;

  private final StringBuilder sb = new StringBuilder();
  private Logger LOGGER = LogManager.getLogger(ReusableLog.class);
  private int level = 0;
  private int poolId = 0;
  private static boolean TEST_OUTPUT_MODE = false;

  // pooled object
  public ReusableLog(final int poolId) {
    this.poolId = poolId;
  }

  public static final ReusableLog get(final int poolId, final String CLASS_NAME, final int level) {
    final ReusableLog log = ReusableLogPool.get(poolId);
    log.alloc(CLASS_NAME, level);

    return log;
  }

  public final ReusableLog append(final boolean value) {
    sb.append(value);
    return this;
  }

  public final ReusableLog append(final int value) {
    sb.append(value);
    return this;
  }

  public final ReusableLog append(final long value) {
    sb.append(value);
    return this;
  }

  public final ReusableLog append(final double value) {
    sb.append(value);
    return this;
  }

  public final ReusableLog append(final float value) {
    sb.append(value);
    return this;
  }

  public final ReusableLog append(final short value) {
    sb.append(value);
    return this;
  }

  public final ReusableLog append(final char value) {
    sb.append(value);
    return this;
  }

  public final ReusableLog append(final byte value) {
    sb.append(value);
    return this;
  }

  public final ReusableLog append(final CharSequence value) {
    sb.append(value);
    return this;
  }

  public final ReusableLog append(final Appendable value) {
    if (value != null) {
      value.appendTo(sb);
    }
    return this;
  }

  public final ReusableLog append(final MsgType value) {
    if (value != null) {
      sb.append(value.toString());
    }
    return this;
  }

  public final ReusableLog append(final byte[] bytes) {
    if (bytes != null) {
      StringUtil.appendBytes(bytes, bytes.length, sb);
    }
    return this;
  }

  public final ReusableLog append(final byte[] bytes, final int length) {
    if (bytes != null) {
      StringUtil.appendBytes(bytes, length, sb);
    }
    return this;
  }

  public final ReusableLog append(final Throwable value) {
    if (value != null) {
      final StringWriter sw = new StringWriter();
      final PrintWriter pw = new PrintWriter(sw);
      value.printStackTrace(pw);
      final String sStackTrace = sw.toString(); // stack trace as a string
      sb.append(sStackTrace);
    }
    return this;
  }


  public final ReusableLog append(final PositionsGroupEncoder positionsGroupEncoder) {
    if (positionsGroupEncoder != null) {
      sb.append(positionsGroupEncoder.toString());
    }
    return this;
  }

  public final ReusableLog append(final PositionReportEncoder positionReportEncoder) {
    if (positionReportEncoder != null) {
      sb.append(positionReportEncoder.toString());
    }
    return this;
  }

  public final ReusableLog append(final MessageHeaderDecoder headerDecoder) {
    if (headerDecoder != null) {
      StringUtil.appendTo(sb, headerDecoder);
    }
    return this;
  }


  public ReusableLog append(final Map<Integer, User> map) {
    if (map != null) {
      sb.append(map.toString());
    }
    return this;
  }

  public final StringBuilder alloc(final String classname, final int level) {
    this.level = level;
    sb.append("[").append(Thread.currentThread().getName()).append("][").append(classname).append("] ");
    return sb;
  }

  public final Logger getLOGGER() {
    return LOGGER;
  }

  public final void setLOGGER(final Logger lOGGER) {
    LOGGER = lOGGER;
  }

  public final int getLevel() {
    return level;
  }

  public final void setLevel(final int level) {
    this.level = level;
  }

  public final StringBuilder getSb() {
    return sb;
  }

  public final int getPoolId() {
    return poolId;
  }

  public final void setPoolId(final int poolId) {
    this.poolId = poolId;
  }

  public static final boolean isTEST_OUTPUT_MODE() {
    return TEST_OUTPUT_MODE;
  }

  public static final void setTEST_OUTPUT_MODE(final boolean tEST_OUTPUT_MODE) {
    TEST_OUTPUT_MODE = tEST_OUTPUT_MODE;
  }

  public final void log() {
    if (TEST_OUTPUT_MODE)
      System.out.println("[" + System.currentTimeMillis() + "]: " + sb);

    switch (level) {
      case FATAL:
        LOGGER.fatal(sb);
        break;
      case ERROR:
        LOGGER.error(sb);
        break;
      case WARN:
        LOGGER.warn(sb);
        break;
      case INFO:
        LOGGER.info(sb);
        break;
      case DEBUG:
        LOGGER.debug(sb);
        break;
      case TRACE:
        LOGGER.trace(sb);
        break;
      default:
    }
    sb.setLength(0);
  }
}
