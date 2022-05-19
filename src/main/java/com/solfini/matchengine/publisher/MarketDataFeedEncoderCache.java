package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Context;
import com.solfini.sbe.encoder.MarketDataFeedEncoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class MarketDataFeedEncoderCache {
  private static MarketDataFeedEncoderCache[] cache = MarketDataFeedEncoderCache.build();
  private final MarketDataFeedEncoder encoder = new MarketDataFeedEncoder();
  private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
  private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  public MarketDataFeedEncoderCache() {
    super();
  }

  private static final MarketDataFeedEncoderCache[] build() {
    cache = new MarketDataFeedEncoderCache[Math.max(Context.getEncoderThreads(), 1)];
    for (int i = 0; i < cache.length; ++i) {
      cache[i] = new MarketDataFeedEncoderCache();
    }
    return cache;
  }

  public static final MarketDataFeedEncoderCache get() {
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

  public final MarketDataFeedEncoder getEncoder() {
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
