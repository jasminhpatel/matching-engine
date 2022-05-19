package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Context;
import com.solfini.sbe.encoder.LogonEncoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class LogonEncoderCache {
  private static LogonEncoderCache[] cache = LogonEncoderCache.build();
  private final LogonEncoder encoder = new LogonEncoder();
  private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
  private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  public LogonEncoderCache() {
    super();
  }

  private static final LogonEncoderCache[] build() {
    cache = new LogonEncoderCache[Math.max(Context.getEncoderThreads(), 1)];
    for (int i = 0; i < cache.length; ++i) {
      cache[i] = new LogonEncoderCache();
    }
    return cache;
  }

  public static final LogonEncoderCache get() {
    if (cache.length > 1) {
      final String name = Thread.currentThread().getName();
      return cache[((int) name.charAt(name.length() - 1)) - 48];
    } else {
      return cache[0];
    }
  }

  public final void reset() {
    // reset
  }

  public final LogonEncoder getEncoder() {
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
