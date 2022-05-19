package com.solfini.matchengine.publisher;

import uk.co.real_logic.artio.fields.DecimalFloat;

/**
 *
 * @author Chris Mack
 *
 */
public class DecimalFloatCache {
  private static final int FLOAT_INITIAL_SIZE = 2048;
  private static final int FLOAT_INCREASE_SIZE = 256;

  private DecimalFloat[] floats;
  private int index;

  public DecimalFloatCache() {
    floats = new DecimalFloat[FLOAT_INITIAL_SIZE];
    for (int i = 0; i < floats.length; ++i) {
      floats[i] = new DecimalFloat();
    }

    index = 0;
  }

  public void reset() {
    index = 0;
  }

  public final DecimalFloat nextDecimalFloat(final long value, final int scale) {
    final DecimalFloat result = floats[index];
    result.value(value);
    result.scale(scale);
    ++index;

    if (floats.length == index) {
      DecimalFloat[] copy = new DecimalFloat[floats.length + FLOAT_INCREASE_SIZE];
      for (int i = 0; i < floats.length; ++i) {
        copy[i] = floats[i];
      }
      for (int i = floats.length; i < copy.length; ++i) {
        copy[i] = new DecimalFloat();
      }
      floats = copy;
    }

    return result;
  }
}
