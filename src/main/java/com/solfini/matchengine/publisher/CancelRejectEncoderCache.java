package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Context;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.OrderCancelRejectEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class CancelRejectEncoderCache {
  private static CancelRejectEncoderCache[] cache = CancelRejectEncoderCache.build();
  private final OrderCancelRejectEncoder encoder = new OrderCancelRejectEncoder();
  private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
  private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  public CancelRejectEncoderCache() {
    super();
  }

  private static final CancelRejectEncoderCache[] build() {
    cache = new CancelRejectEncoderCache[Math.max(Context.getEncoderThreads(), 1)];
    for (int i = 0; i < cache.length; ++i) {
      cache[i] = new CancelRejectEncoderCache();
    }
    return cache;
  }

  public static final CancelRejectEncoderCache get() {
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

  public final OrderCancelRejectEncoder getEncoder() {
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
