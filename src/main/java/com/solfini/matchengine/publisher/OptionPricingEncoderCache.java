package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Context;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.OptionPricingFeedEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class OptionPricingEncoderCache extends DecimalFloatCache {
  private static OptionPricingEncoderCache[] cache = OptionPricingEncoderCache.build();
  private final OptionPricingFeedEncoder encoder = new OptionPricingFeedEncoder();
  private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
  private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  public OptionPricingEncoderCache() {
    super();
  }

  private static final OptionPricingEncoderCache[] build() {
    cache = new OptionPricingEncoderCache[Math.max(Context.getEncoderThreads(), 1)];
    for (int i = 0; i < cache.length; ++i) {
      cache[i] = new OptionPricingEncoderCache();
    }
    return cache;
  }

  public static final OptionPricingEncoderCache get() {
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

  public final OptionPricingFeedEncoder getEncoder() {
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
