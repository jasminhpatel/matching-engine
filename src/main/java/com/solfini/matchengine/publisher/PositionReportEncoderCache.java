package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Context;
import com.solfini.instrument.InstrumentCache;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.PositionReportEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class PositionReportEncoderCache extends DecimalFloatCache {
  private static PositionReportEncoderCache[] cache = PositionReportEncoderCache.build();
  private final PositionReportEncoder encoder = new PositionReportEncoder();
  private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(32768 * 4);
  private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final double[] usdMarkPricesToSet = new double[Math.max(InstrumentCache.getPairCapacity(), 128)];

  public PositionReportEncoderCache() {
    super();
  }

  private static final PositionReportEncoderCache[] build() {
    cache = new PositionReportEncoderCache[Math.max(Context.getEncoderThreads(), 1)];
    for (int i = 0; i < cache.length; ++i) {
      cache[i] = new PositionReportEncoderCache();
    }
    return cache;
  }

  public static final PositionReportEncoderCache get() {
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

  public final PositionReportEncoder getEncoder() {
    return encoder;
  }

  public final double[] getUsdMarkPricesToSet() {
    return usdMarkPricesToSet;
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
