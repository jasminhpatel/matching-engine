package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Context;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.NetworkStatusEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class NetworkStatusEncoderCache {
  private static NetworkStatusEncoderCache[] cache = NetworkStatusEncoderCache.build();
  private final NetworkStatusEncoder encoder = new NetworkStatusEncoder();
  private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
  private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  public NetworkStatusEncoderCache() {
    super();
  }

  private static final NetworkStatusEncoderCache[] build() {
    cache = new NetworkStatusEncoderCache[Math.max(Context.getEncoderThreads(), 1)];
    for (int i = 0; i < cache.length; ++i) {
      cache[i] = new NetworkStatusEncoderCache();
    }
    return cache;
  }

  public static final NetworkStatusEncoderCache get() {
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

  public final NetworkStatusEncoder getEncoder() {
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
