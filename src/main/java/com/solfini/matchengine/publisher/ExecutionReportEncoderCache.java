package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Context;
import com.solfini.sbe.encoder.ExecutionReportEncoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class ExecutionReportEncoderCache extends DecimalFloatCache {
  private static ExecutionReportEncoderCache[] cache = ExecutionReportEncoderCache.build();
  private final ExecutionReportEncoder encoder = new ExecutionReportEncoder();
  private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
  private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  public ExecutionReportEncoderCache() {
    super();
  }

  private static final ExecutionReportEncoderCache[] build() {
    cache = new ExecutionReportEncoderCache[Math.max(Context.getEncoderThreads(), 1)];
    for (int i = 0; i < cache.length; ++i) {
      cache[i] = new ExecutionReportEncoderCache();
    }
    return cache;
  }

  public static final ExecutionReportEncoderCache get() {
    if (Context.getEncoderThreads() > 0) {
      final String name = Thread.currentThread().getName();
      return cache[((int) name.charAt(name.length() - 1)) - 48];
    } else {
      return cache[0];
    }
  }

  @Override
  public final void reset() {
    super.reset();
    // reset
  }

  public final ExecutionReportEncoder getEncoder() {
    return encoder;
  }

  public final ByteBuffer getDirectBuffer() {
    return directBuffer;
  }

  public final UnsafeBuffer getUnsafeBuffer() {
    return unsafeBuffer;
  }

  public final MessageHeaderEncoder getHeaderEncoder() {
    return headerEncoder;
  }

}
