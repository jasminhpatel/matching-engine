package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Context;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class MarketDataSnapshotFullRefreshEncoderCache {
  private static MarketDataSnapshotFullRefreshEncoderCache[] cache = MarketDataSnapshotFullRefreshEncoderCache.build();
  private final MarketDataSnapshotFullRefreshEncoder encoder = new MarketDataSnapshotFullRefreshEncoder();
  private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(32768);
  private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  public MarketDataSnapshotFullRefreshEncoderCache() {
    super();
  }

  private static final MarketDataSnapshotFullRefreshEncoderCache[] build() {
    cache = new MarketDataSnapshotFullRefreshEncoderCache[Math.max(Context.getEncoderThreads(), 1)];
    for (int i = 0; i < cache.length; ++i) {
      cache[i] = new MarketDataSnapshotFullRefreshEncoderCache();
    }
    return cache;
  }

  public static final MarketDataSnapshotFullRefreshEncoderCache get() {
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

  public final MarketDataSnapshotFullRefreshEncoder getEncoder() {
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
