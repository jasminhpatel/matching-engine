package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.common.Context;
import com.solfini.sbe.encoder.BusinessRejectEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class BusinessRejectEncoderCache {
  private static BusinessRejectEncoderCache[] cache = BusinessRejectEncoderCache.build();
  private final BusinessRejectEncoder encoder = new BusinessRejectEncoder();
  private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
  private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  public BusinessRejectEncoderCache() {
    super();
  }

  private static final BusinessRejectEncoderCache[] build() {
    cache = new BusinessRejectEncoderCache[Math.max(Context.getEncoderThreads(), 1)];
    for (int i = 0; i < cache.length; ++i) {
      cache[i] = new BusinessRejectEncoderCache();
    }
    return cache;
  }

  public static final BusinessRejectEncoderCache get() {
    if (Context.getEncoderThreads() > 0) {
      final String name = Thread.currentThread().getName();
      return cache[((int) name.charAt(name.length() - 1)) - 48];
    } else {
      return cache[0];
    }
  }

  public final void reset() {
    // reset
  }

  public final BusinessRejectEncoder getEncoder() {
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
