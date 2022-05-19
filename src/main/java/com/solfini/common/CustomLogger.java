package com.solfini.common;

import org.slf4j.Logger;
import org.slf4j.Marker;
import com.solfini.matchengine.LoggingThread;
import com.solfini.util.LogLevel;

public class CustomLogger implements Logger, Constants {
  private static final ManyToOneConcurrentArrayQueueCustom<ReusableLog> loggingQueue = LoggingThread.getLoggingQueue();
  private final String className;

  public static final CustomLogger getLogger(final Class<?> clazz) {
    return new CustomLogger(clazz.getName());
  }

  private CustomLogger(final String className) {
    this.className = className;
  }

  @Override
  public String getName() {
    return className;
  }

  @Override
  public boolean isTraceEnabled() {
    return LogLevel.trace();
  }

  @Override
  public void trace(final String msg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(msg));

  }

  @Override
  public void trace(final String format, final Object arg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg == null ? NULL : arg.toString()));

  }

  @Override
  public void trace(final String format, final Object arg1, final Object arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1 == null ? NULL : arg1.toString())
        .append(arg2 == null ? NULL : arg2.toString()));
  }

  @Override
  public void trace(final String format, final Object... arguments) {
    final ReusableLog log = ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE);
    StringBuilder sb = log.getSb();
    if (arguments != null) {
      for (final Object o : arguments) {
        if (o == null)
          sb.append(NULL);
        else
          sb.append(o.toString());
      }
    }
    loggingQueue.offer(log);
  }

  @Override
  public void trace(final String msg, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(msg).append(t));
  }

  @Override
  public boolean isTraceEnabled(final Marker marker) {
    return LogLevel.trace();
  }

  @Override
  public void trace(final Marker marker, final String msg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE));
  }

  @Override
  public void trace(final Marker marker, final String format, final Object arg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg == null ? NULL : arg.toString()));
  }

  @Override
  public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1 == null ? NULL : arg1.toString())
        .append(arg2 == null ? NULL : arg2.toString()));
  }

  @Override
  public void trace(final Marker marker, final String format, final Object... argArray) {
    final ReusableLog log = ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG);
    StringBuilder sb = log.getSb();
    if (argArray != null) {
      for (final Object o : argArray) {
        if (o == null)
          sb.append(NULL);
        else
          sb.append(o.toString());
      }
    }
    loggingQueue.offer(log);
  }

  @Override
  public void trace(final Marker marker, final String msg, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(msg).append(t));

  }

  @Override
  public boolean isDebugEnabled() {
    return LogLevel.debug();
  }

  @Override
  public void debug(final String msg) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(msg));
  }

  @Override
  public void debug(final String format, final Object arg) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg == null ? NULL : arg.toString()));
  }

  @Override
  public void debug(final String format, final Object arg1, final Object arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1 == null ? NULL : arg1.toString())
        .append(arg2 == null ? NULL : arg2.toString()));
  }

  public void error(final String format, final CharSequence arg1, final boolean arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.ERROR).append(arg1).append(arg2));
  }

  public void error(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6, final Throwable arg7) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7));
  }

  public void trace(final String format, final CharSequence arg1) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1));
  }

  public void trace(final String format, final CharSequence arg1, final boolean arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1).append(arg2));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1).append(arg2));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1).append(arg2));
  }

  public void trace(final String format, final CharSequence arg1, final double arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1).append(arg2));
  }

  public void trace(final String format, final CharSequence arg1, final byte[] arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2));
  }

  public void trace(final String format, final CharSequence arg1, final CharSequence arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2));
  }

  public void trace(final String format, final Appendable arg1, final Appendable arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3));
  }

  public void trace(final String format, final int arg1, final int arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3));
  }

  public void trace(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final boolean arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3,
      final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final Appendable arg1, final Appendable arg2, final Appendable arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final boolean arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final Appendable arg1, final Appendable arg2, final Appendable arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final long arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }


  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final int arg1, final char arg2, final int arg3, final CharSequence arg4,
      final CharSequence arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void trace(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void trace(final String format, final Appendable arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }



  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9));
  }


  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final boolean arg6, final CharSequence arg7, final int arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void trace(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }


  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final int arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final Appendable arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void trace(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final byte[] arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void trace(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final int arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void trace(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final Appendable arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final byte arg6, final CharSequence arg7, final long arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void trace(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final boolean arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void trace(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final boolean arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }


  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6, final CharSequence arg7, final long arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void trace(final String format, final CharSequence arg1, final byte arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }


  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void trace(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final long arg7, final CharSequence arg8, final long arg9, final CharSequence arg10,
      final int arg11, final CharSequence arg12, final double arg13) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13));
  }


  public void trace(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final int arg10, final CharSequence arg11, final CharSequence arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final byte arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final byte[] arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final Appendable arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final int arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final byte[] arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void trace(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final double arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final int arg10, final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void trace(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final byte arg8, final CharSequence arg9, final int arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final byte arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final Appendable arg8, final CharSequence arg9,
      final Appendable arg10, final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14, final CharSequence arg15, final long arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final Appendable arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final byte[] arg12, final CharSequence arg13, final int arg14, final CharSequence arg15,
      final CharSequence arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void trace(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final int arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final int arg8, final CharSequence arg9, final int arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final int arg14, final CharSequence arg15, final int arg16,
      final CharSequence arg17, final int arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10, final CharSequence arg11, final CharSequence arg12, final CharSequence arg13, final int arg14,
      final CharSequence arg15, final CharSequence arg16, final CharSequence arg17, final CharSequence arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void trace(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final int arg16, final CharSequence arg17, final Appendable arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final long arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void trace(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final int arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final Appendable arg22) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final double arg22, final CharSequence arg23, final Appendable arg24) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final long arg24, final CharSequence arg25, final long arg26,
      final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final long arg24, final CharSequence arg25, final long arg26,
      final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final Appendable arg22, final CharSequence arg23, final long arg24, final CharSequence arg25,
      final long arg26, final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30, final CharSequence arg31,
      final long arg32) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14)
            .append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22).append(arg23)
            .append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30).append(arg31).append(arg32));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final long arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final double arg24, final CharSequence arg25,
      final double arg26, final CharSequence arg27, final double arg28, final CharSequence arg29, final long arg30,
      final CharSequence arg31, final long arg32) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14)
            .append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22).append(arg23)
            .append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30).append(arg31).append(arg32));
  }

  public void trace(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final long arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final double arg24, final CharSequence arg25,
      final double arg26, final CharSequence arg27, final double arg28) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28));
  }

  public void info(final String format, final CharSequence arg1) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1));
  }

  public void info(final String format, final CharSequence arg1, final boolean arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2));
  }

  public void info(final String format, final CharSequence arg1, final int arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2));
  }

  public void info(final String format, final CharSequence arg1, final long arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2));
  }

  public void info(final String format, final CharSequence arg1, final double arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2));
  }

  public void info(final String format, final CharSequence arg1, final byte[] arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2));
  }

  public void info(final String format, final Appendable arg1, final Appendable arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final boolean arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }


  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3));
  }

  public void info(final String format, final int arg1, final int arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final boolean arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final boolean arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3,
      final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final Appendable arg1, final Appendable arg2, final Appendable arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final boolean arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final Appendable arg1, final Appendable arg2, final Appendable arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final long arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final int arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final Appendable arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final boolean arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final boolean arg6, final CharSequence arg7, final boolean arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final int arg1, final char arg2, final int arg3, final CharSequence arg4, final CharSequence arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void info(final String format, final Appendable arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final boolean arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final double arg22) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final boolean arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final double arg22, final CharSequence arg23, final double arg24) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24));
  }


  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final double arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9));
  }


  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final Appendable arg6, final CharSequence arg7, final Appendable arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final boolean arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final boolean arg6, final CharSequence arg7, final int arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }


  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final int arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final Appendable arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final byte[] arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final CharSequence arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final int arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final Appendable arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final Appendable arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final byte arg6, final CharSequence arg7, final long arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final boolean arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final boolean arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final boolean arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final boolean arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final int arg10,
      final CharSequence arg11, final double arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }


  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6, final CharSequence arg7, final long arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final byte arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final byte[] arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void info(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final long arg7, final CharSequence arg8, final long arg9, final CharSequence arg10,
      final int arg11, final CharSequence arg12, final double arg13) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13));
  }


  public void info(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final byte arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final byte[] arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final Appendable arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final int arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final double arg14, final CharSequence arg15, final long arg16,
      final CharSequence arg17, final long arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void info(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final double arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final int arg10, final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void info(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final byte arg8, final CharSequence arg9, final int arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final byte arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final Appendable arg8, final CharSequence arg9,
      final Appendable arg10, final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14, final CharSequence arg15, final long arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final Appendable arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final byte[] arg12, final CharSequence arg13, final int arg14, final CharSequence arg15,
      final CharSequence arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final int arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final int arg8, final CharSequence arg9, final int arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final int arg14, final CharSequence arg15, final int arg16,
      final CharSequence arg17, final int arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10, final CharSequence arg11, final CharSequence arg12, final CharSequence arg13, final int arg14,
      final CharSequence arg15, final CharSequence arg16, final CharSequence arg17, final CharSequence arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void info(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final int arg16, final CharSequence arg17, final Appendable arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final long arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void info(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final int arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final Appendable arg22) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22));
  }

  public void info(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final double arg22, final CharSequence arg23, final Appendable arg24) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final long arg24, final CharSequence arg25, final long arg26,
      final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final long arg24, final CharSequence arg25, final long arg26,
      final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final Appendable arg22, final CharSequence arg23, final long arg24, final CharSequence arg25,
      final long arg26, final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30, final CharSequence arg31,
      final long arg32) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14)
            .append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22).append(arg23)
            .append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30).append(arg31).append(arg32));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final long arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final double arg24, final CharSequence arg25,
      final double arg26, final CharSequence arg27, final double arg28, final CharSequence arg29, final long arg30,
      final CharSequence arg31, final long arg32) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14)
            .append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22).append(arg23)
            .append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30).append(arg31).append(arg32));
  }

  public void info(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final long arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final double arg24, final CharSequence arg25,
      final double arg26, final CharSequence arg27, final double arg28) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28));
  }



  public void warn(final String format, final CharSequence arg1, final Appendable arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2));
  }

  public void error(final String format, final int arg1, final char arg2, final int arg3, final CharSequence arg4,
      final CharSequence arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_128, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void error(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void error(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }


  public void error(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }


  public void error(final String format, final CharSequence arg1, final long arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.ERROR).append(arg1).append(arg2));
  }


  public void error(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void error(final String format, final CharSequence arg1, final Appendable arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1).append(arg2));
  }

  public void trace(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }


  public void warn(final String format, final CharSequence arg1) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1));
  }

  public void warn(final String format, final CharSequence arg1, final boolean arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1).append(arg2));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1).append(arg2));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1).append(arg2));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1).append(arg2));
  }

  public void warn(final String format, final CharSequence arg1, final byte[] arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2));
  }

  public void warn(final String format, final CharSequence arg1, final CharSequence arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2));
  }

  public void warn(final String format, final Appendable arg1, final Appendable arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3));
  }

  public void warn(final String format, final int arg1, final int arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3));
  }

  public void warn(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final boolean arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3,
      final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final Appendable arg1, final Appendable arg2, final Appendable arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final boolean arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final Appendable arg1, final Appendable arg2, final Appendable arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final long arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }



  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final int arg1, final char arg2, final int arg3, final CharSequence arg4, final CharSequence arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void warn(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void warn(final String format, final Appendable arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final CharSequence arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6, final Throwable arg7) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7));
  }

  public void warn(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9));
  }


  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final boolean arg6, final CharSequence arg7, final int arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }


  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final int arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final Appendable arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void warn(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void warn(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final byte[] arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final int arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final Appendable arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final byte arg6, final CharSequence arg7, final long arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10, final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final boolean arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final boolean arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }


  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6, final CharSequence arg7, final long arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }



  public void warn(final String format, final CharSequence arg1, final byte arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }


  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void warn(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final long arg7, final CharSequence arg8, final long arg9, final CharSequence arg10,
      final int arg11, final CharSequence arg12, final double arg13) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13));
  }


  public void warn(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final byte arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final byte[] arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final Appendable arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final int arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final int arg10, final CharSequence arg11, final CharSequence arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final int arg10, final CharSequence arg11, final int arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }



  public void warn(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final double arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final int arg10, final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void warn(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final byte arg8, final CharSequence arg9, final int arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final byte arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final Appendable arg8, final CharSequence arg9,
      final Appendable arg10, final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14, final CharSequence arg15, final long arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final Appendable arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final byte[] arg12, final CharSequence arg13, final int arg14, final CharSequence arg15,
      final CharSequence arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final int arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final int arg8, final CharSequence arg9, final int arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final int arg14, final CharSequence arg15, final int arg16,
      final CharSequence arg17, final int arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10, final CharSequence arg11, final CharSequence arg12, final CharSequence arg13, final int arg14,
      final CharSequence arg15, final CharSequence arg16, final CharSequence arg17, final CharSequence arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void warn(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final int arg16, final CharSequence arg17, final Appendable arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final long arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void warn(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final int arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final Appendable arg22) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22));
  }

  public void warn(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final double arg22, final CharSequence arg23, final Appendable arg24) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final long arg24, final CharSequence arg25, final long arg26,
      final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final long arg24, final CharSequence arg25, final long arg26,
      final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final Appendable arg22, final CharSequence arg23, final long arg24, final CharSequence arg25,
      final long arg26, final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30, final CharSequence arg31,
      final long arg32) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14)
            .append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22).append(arg23)
            .append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30).append(arg31).append(arg32));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final long arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final double arg24, final CharSequence arg25,
      final double arg26, final CharSequence arg27, final double arg28, final CharSequence arg29, final long arg30,
      final CharSequence arg31, final long arg32) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14)
            .append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22).append(arg23)
            .append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30).append(arg31).append(arg32));
  }

  public void warn(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final long arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final double arg24, final CharSequence arg25,
      final double arg26, final CharSequence arg27, final double arg28) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28));
  }


  public void debug(final String format, final CharSequence arg1) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1));
  }

  public void debug(final String format, final CharSequence arg1, final boolean arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1).append(arg2));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1).append(arg2));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1).append(arg2));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1).append(arg2));
  }

  public void debug(final String format, final CharSequence arg1, final byte[] arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2));
  }

  public void debug(final String format, final Appendable arg1, final Appendable arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3));
  }

  public void debug(final String format, final int arg1, final int arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final boolean arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3,
      final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final Appendable arg1, final Appendable arg2, final Appendable arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final boolean arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final Appendable arg1, final Appendable arg2, final Appendable arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final long arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final int arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final int arg1, final char arg2, final int arg3, final CharSequence arg4,
      final CharSequence arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final long arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void debug(final String format, final Appendable arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6, final CharSequence arg7, final Appendable arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final Appendable arg5) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6, final Throwable arg7) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9));
  }


  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final boolean arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }


  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void error(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final boolean arg6, final CharSequence arg7, final int arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final CharSequence arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final int arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final Appendable arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final byte[] arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final byte[] arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final byte[] arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final byte[] arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final int arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final Appendable arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final byte arg6, final CharSequence arg7, final long arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final boolean arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final boolean arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }


  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final byte[] arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }


  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final Appendable arg6, final CharSequence arg7, final long arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }



  public void debug(final String format, final CharSequence arg1, final byte arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final int arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void debug(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final long arg7, final CharSequence arg8, final long arg9, final CharSequence arg10,
      final int arg11, final CharSequence arg12, final double arg13) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13));
  }


  public void debug(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final byte arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final byte[] arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final Appendable arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final int arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void debug(final String format, final String arg1, final long arg2, final String arg3, final long arg4, final String arg5,
      final long arg6, final String arg7, final byte arg8, final String arg9, final long arg10, final String arg11, final byte[] arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void debug(final String format, final String arg1, final byte arg2, final String arg3, final long arg4, final String arg5,
      final long arg6, final String arg7, final byte arg8, final String arg9, final byte[] arg10, final Throwable arg11) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11));
  }


  public void debug(final String format, final String arg1, final byte arg2, final String arg3, final long arg4, final String arg5,
      final long arg6, final String arg7, final long arg8, final String arg9, final byte[] arg10, final Throwable arg11) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11));
  }


  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final int arg10, final CharSequence arg11, final CharSequence arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final int arg10, final CharSequence arg11, final int arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }


  public void debug(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, CharSequence arg9, final byte arg10,
      CharSequence arg11, final int arg12, CharSequence arg13, final long arg14, CharSequence arg15, final byte[] arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }


  public void debug(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final double arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final int arg10, final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void debug(final String format, final CharSequence arg1, final boolean arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final boolean arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.INFO).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final byte arg8, final CharSequence arg9, final int arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final byte arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final CharSequence arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final Appendable arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final Appendable arg8, final CharSequence arg9,
      final Appendable arg10, final CharSequence arg11, final Appendable arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9, final int arg10,
      final CharSequence arg11, final byte[] arg12, final CharSequence arg13, final Appendable arg14) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final long arg14, final CharSequence arg15, final long arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final Appendable arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final byte[] arg12, final CharSequence arg13, final int arg14, final CharSequence arg15,
      final CharSequence arg16) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final double arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_512, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final int arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final int arg8, final CharSequence arg9, final int arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final int arg14, final CharSequence arg15, final int arg16,
      final CharSequence arg17, final int arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10, final CharSequence arg11, final CharSequence arg12, final CharSequence arg13, final int arg14,
      final CharSequence arg15, final CharSequence arg16, final CharSequence arg17, final CharSequence arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final int arg16, final CharSequence arg17, final Appendable arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10, final CharSequence arg11, final CharSequence arg12, final CharSequence arg13, final int arg14,
      final CharSequence arg15, final byte[] arg16, final CharSequence arg17, final Appendable arg18) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final int arg18, final CharSequence arg19, final double arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final int arg18, final CharSequence arg19, final int arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final long arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10, final CharSequence arg11, final CharSequence arg12, final CharSequence arg13, final int arg14,
      final CharSequence arg15, final CharSequence arg16, final CharSequence arg17, final CharSequence arg18, final CharSequence arg19,
      final Appendable arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final CharSequence arg8, final CharSequence arg9,
      final CharSequence arg10, final CharSequence arg11, final CharSequence arg12, final CharSequence arg13, final int arg14,
      final CharSequence arg15, final byte[] arg16, final CharSequence arg17, final CharSequence arg18, final CharSequence arg19,
      final Appendable arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void debug(final String format, final CharSequence arg1, final long arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final long arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final int arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final Appendable arg22) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22));
  }

  public void debug(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final double arg22, final CharSequence arg23, final Appendable arg24) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final Appendable arg22, final CharSequence arg23, final long arg24) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final Appendable arg22, final CharSequence arg23, final long arg24, final CharSequence arg25,
      final long arg26) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final long arg24, final CharSequence arg25, final long arg26,
      final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final Appendable arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final long arg24, final CharSequence arg25, final long arg26,
      final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final long arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final double arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final Appendable arg22, final CharSequence arg23, final long arg24, final CharSequence arg25,
      final long arg26, final CharSequence arg27, final long arg28, final CharSequence arg29, final long arg30, final CharSequence arg31,
      final long arg32) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4).append(arg5)
            .append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13).append(arg14)
            .append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22).append(arg23)
            .append(arg24).append(arg25).append(arg26).append(arg27).append(arg28).append(arg29).append(arg30).append(arg31).append(arg32));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final double arg20,
      final CharSequence arg21, final double arg22, final CharSequence arg23, final double arg24, final CharSequence arg25,
      final long arg26) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26));
  }

  public void debug(final String format, final CharSequence arg1, final Appendable arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final long arg6, final CharSequence arg7, final long arg8, final CharSequence arg9, final double arg10,
      final CharSequence arg11, final double arg12, final CharSequence arg13, final double arg14, final CharSequence arg15,
      final double arg16, final CharSequence arg17, final Appendable arg18, final CharSequence arg19, final long arg20,
      final CharSequence arg21, final long arg22, final CharSequence arg23, final double arg24, final CharSequence arg25,
      final double arg26, final CharSequence arg27, final double arg28) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22)
        .append(arg23).append(arg24).append(arg25).append(arg26).append(arg27).append(arg28));
  }

  @Override
  public void debug(final String format, final Object... arguments) {
    final ReusableLog log = ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG);
    StringBuilder sb = log.getSb();
    if (arguments != null) {
      for (final Object o : arguments) {
        if (o == null)
          sb.append(NULL);
        else
          sb.append(o.toString());
      }
    }
    loggingQueue.offer(log);
  }

  @Override
  public void debug(final String msg, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(msg).append(t));

  }

  @Override
  public boolean isDebugEnabled(final Marker marker) {
    return LogLevel.debug();
  }

  @Override
  public void debug(final Marker marker, final String msg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(msg));

  }

  @Override
  public void debug(final Marker marker, final String format, final Object arg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg == null ? NULL : arg.toString()));
  }

  @Override
  public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1 == null ? NULL : arg1.toString())
        .append(arg2 == null ? NULL : arg2.toString()));
  }

  @Override
  public void debug(final Marker marker, final String format, final Object... arguments) {
    final ReusableLog log = ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG);
    StringBuilder sb = log.getSb();
    if (arguments != null) {
      for (final Object o : arguments) {
        if (o == null)
          sb.append(NULL);
        else
          sb.append(o.toString());
      }
    }
    loggingQueue.offer(log);
  }

  @Override
  public void debug(final Marker marker, final String msg, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(msg).append(t));

  }

  public void error(final String format, final String arg1, final byte arg2, final String arg3, final long arg4, final String arg5,
      final long arg6, final String arg7, final byte arg8, final String arg9, final byte[] arg10, final Throwable arg11) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11));
  }

  @Override
  public boolean isInfoEnabled() {
    return LogLevel.info();
  }

  @Override
  public void info(final String msg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(msg));

  }

  @Override
  public void info(final String format, final Object arg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg == null ? NULL : arg.toString()));
  }

  @Override
  public void info(final String format, final Object arg1, final Object arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1 == null ? NULL : arg1.toString())
        .append(arg2 == null ? NULL : arg2.toString()));
  }


  public void error(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3) {
    loggingQueue.offer(ReusableLog.get(POOL_128, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3));
  }

  public void error(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final CharSequence arg5, final Appendable arg6) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6));
  }

  public void error(final String format, final String arg1, final byte arg2, final String arg3, final long arg4, final String arg5,
      final long arg6, final String arg7, final long arg8, final String arg9, final byte[] arg10, final Throwable arg11) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11));
  }

  public void debug(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final int arg18, final CharSequence arg19, final Appendable arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void trace(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final int arg18, final CharSequence arg19, final Appendable arg20) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20));
  }

  public void trace(final String format, final CharSequence arg1, final double arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final int arg6, final CharSequence arg7, final double arg8, final CharSequence arg9, final Appendable arg10,
      final CharSequence arg11, final int arg12, final CharSequence arg13, final Appendable arg14, final CharSequence arg15,
      final Appendable arg16, final CharSequence arg17, final int arg18, final CharSequence arg19, final Appendable arg20,
      final CharSequence arg21, final double arg22) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.DEBUG).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11).append(arg12).append(arg13)
        .append(arg14).append(arg15).append(arg16).append(arg17).append(arg18).append(arg19).append(arg20).append(arg21).append(arg22));
  }

  @Override
  public void info(final String format, final Object... arguments) {
    final ReusableLog log = ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO);
    StringBuilder sb = log.getSb();
    if (arguments != null) {
      for (final Object o : arguments) {
        if (o == null)
          sb.append(NULL);
        else
          sb.append(o.toString());
      }
    }
    loggingQueue.offer(log);
  }

  @Override
  public void info(final String msg, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(msg).append(t));

  }

  @Override
  public boolean isInfoEnabled(final Marker marker) {
    return LogLevel.info();
  }

  @Override
  public void info(final Marker marker, final String msg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(msg));

  }

  @Override
  public void info(final Marker marker, final String format, final Object arg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg == null ? NULL : arg.toString()));

  }

  @Override
  public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(arg1 == null ? NULL : arg1.toString())
        .append(arg2 == null ? NULL : arg2.toString()));
  }

  @Override
  public void info(final Marker marker, final String format, final Object... arguments) {
    final ReusableLog log = ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO);
    StringBuilder sb = log.getSb();
    if (arguments != null) {
      for (final Object o : arguments) {
        if (o == null)
          sb.append(NULL);
        else
          sb.append(o.toString());
      }
    }
    loggingQueue.offer(log);
  }

  @Override
  public void info(final Marker marker, final String msg, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.INFO).append(msg).append(t));

  }

  public void error(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final CharSequence arg4,
      final Throwable t) {
    loggingQueue
        .offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4).append(t));
  }

  public void error(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(t));
  }

  public void error(final String format, final CharSequence arg1, final CharSequence arg2, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1).append(arg2).append(t));
  }


  @Override
  public boolean isWarnEnabled() {
    return LogLevel.warn();
  }

  @Override
  public void warn(final String msg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(msg));
  }

  @Override
  public void warn(final String format, final Object arg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg == null ? NULL : arg.toString()));

  }

  @Override
  public void warn(final String format, final Object... arguments) {
    final ReusableLog log = ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN);
    StringBuilder sb = log.getSb();
    if (arguments != null) {
      for (final Object o : arguments) {
        if (o == null)
          sb.append(NULL);
        else
          sb.append(o.toString());
      }
    }
    loggingQueue.offer(log);
  }

  @Override
  public void warn(final String format, final Object arg1, final Object arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1 == null ? NULL : arg1.toString())
        .append(arg2 == null ? NULL : arg2.toString()));
  }

  @Override
  public void warn(final String msg, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(msg).append(t));

  }

  @Override
  public boolean isWarnEnabled(final Marker marker) {
    return LogLevel.warn();
  }

  @Override
  public void warn(final Marker marker, final String msg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(msg));

  }

  @Override
  public void warn(final Marker marker, final String format, final Object arg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg == null ? NULL : arg.toString()));

  }

  @Override
  public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(arg1 == null ? NULL : arg1.toString())
        .append(arg2 == null ? NULL : arg2.toString()));
  }

  @Override
  public void warn(final Marker marker, final String format, final Object... arguments) {
    final ReusableLog log = ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN);
    StringBuilder sb = log.getSb();
    if (arguments != null) {
      for (final Object o : arguments) {
        if (o == null)
          sb.append(NULL);
        else
          sb.append(o.toString());
      }
    }
    loggingQueue.offer(log);
  }

  @Override
  public void warn(final Marker marker, final String msg, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.WARN).append(msg).append(t));

  }

  @Override
  public boolean isErrorEnabled() {
    return LogLevel.error();
  }

  @Override
  public void error(final String msg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(msg));
  }

  @Override
  public void error(final String format, final Object arg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg == null ? "" : arg.toString()));
  }

  @Override
  public void error(final String format, final Object arg1, final Object arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1 == null ? "" : arg1.toString())
        .append(arg2 == null ? "" : arg2.toString()));
  }

  public void error(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3, final int arg4,
      final CharSequence arg5, final CharSequence arg6, final CharSequence arg7, final int arg8, final CharSequence arg9,
      final CharSequence arg10, Throwable arg11) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10).append(arg11));
  }

  @Override
  public void error(final String format, final Object... arguments) {
    final ReusableLog log = ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR);
    StringBuilder sb = log.getSb();
    if (arguments != null) {
      for (final Object o : arguments) {
        if (o == null)
          sb.append(NULL);
        else
          sb.append(o.toString());
      }
    }
    loggingQueue.offer(log);
  }

  @Override
  public void error(final String msg, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(msg).append(t));
  }

  @Override
  public boolean isErrorEnabled(final Marker marker) {
    return LogLevel.error();
  }

  @Override
  public void error(final Marker marker, final String msg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(msg));

  }

  @Override
  public void error(final Marker marker, final String format, final Object arg) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.TRACE).append(arg == null ? NULL : arg.toString()));

  }

  @Override
  public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(arg1 == null ? NULL : arg1.toString())
        .append(arg2 == null ? NULL : arg2.toString()));
  }

  @Override
  public void error(final Marker marker, final String format, final Object... arguments) {
    final ReusableLog log = ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR);
    StringBuilder sb = log.getSb();
    if (arguments != null) {
      for (final Object o : arguments) {
        if (o == null)
          sb.append(NULL);
        else
          sb.append(o.toString());
      }
    }
    loggingQueue.offer(log);
  }

  public void error(final String format, final CharSequence arg1, final CharSequence arg2, final CharSequence arg3,
      final CharSequence arg4) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4));
  }

  public void error(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8));
  }

  public void error(final String format, final CharSequence arg1, final int arg2, final CharSequence arg3, final long arg4,
      final CharSequence arg5, final double arg6, final CharSequence arg7, final double arg8, final CharSequence arg9,
      final Appendable arg10) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.ERROR).append(arg1).append(arg2).append(arg3).append(arg4)
        .append(arg5).append(arg6).append(arg7).append(arg8).append(arg9).append(arg10));
  }

  public void error(final String format, final CharSequence arg1, final CharSequence arg2) {
    loggingQueue.offer(ReusableLog.get(POOL_1024, className, ReusableLog.ERROR).append(arg1).append(arg2));
  }

  @Override
  public void error(final Marker marker, final String msg, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(msg).append(t));

  }

  public void error(final String format, final String msg, final Appendable arg1, final Throwable t) {
    loggingQueue.offer(ReusableLog.get(POOL_LARGE, className, ReusableLog.ERROR).append(msg).append(arg1).append(t));
  }
}
