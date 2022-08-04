package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import com.solfini.common.Context;
import com.solfini.sbe.encoder.AssetGroupEncoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class AssetGroupEncoderCache {
  private static AssetGroupEncoderCache[] cache = AssetGroupEncoderCache.build();
  private final AssetGroupEncoder encoder = new AssetGroupEncoder();
  private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(524_288);
  private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  public AssetGroupEncoderCache() {
    super();
  }

  private static final AssetGroupEncoderCache[] build() {
    cache = new AssetGroupEncoderCache[Math.max(Context.getEncoderThreads(), 1)];
    for (int i = 0; i < cache.length; ++i) {
      cache[i] = new AssetGroupEncoderCache();
    }
    return cache;
  }

  public static final AssetGroupEncoderCache get() {
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

  public final AssetGroupEncoder getEncoder() {
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
