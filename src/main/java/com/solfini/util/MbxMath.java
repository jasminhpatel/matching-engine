package com.solfini.util;

import java.math.BigInteger;
import net.openhft.chronicle.core.Maths;

/**
 *
 * @author Ninj0r
 */

public final class MbxMath {

  private MbxMath() {}

  public static final long multiplyQtyAndPrice2(final long qty, final long qtyPrecDiv, final long price, final long pricePrecDiv) {
    if (qtyPrecDiv >= 100) {
      return 100 * multiplyQtyAndPrice(qty, qtyPrecDiv / 100, price, pricePrecDiv);
    }
    return multiplyQtyAndPrice(qty, qtyPrecDiv, price, pricePrecDiv);
  }

  public static final long divide(final long numerator, final long denominator, final long scale_multiplier) {
    final long div = numerator / denominator;
    final long mod = numerator % denominator;
    final long temp1 = div * scale_multiplier;
    final long temp2 = (mod * scale_multiplier) / denominator;
    return temp1 + temp2;
  }

  public static final int digitsAfterDecimal(double d) {
    if (d < 0)
      d = -d;
    if (d < 1)
      return 0;
    for (int i = 1; i < 32; i++) {
      d = d * .1;
      if (d < 1)
        return i;
    }
    return 32;
  }


  public static final double roundToBestPrecision(final double d) {
    try {
      final int digits = 15 - digitsAfterDecimal(d);
      if (digits <= 1)
        return d;
      return Maths.roundN(d, digits - 2);
    } catch (Exception e) {
      return d;
    }
  }

  public static final long multiplier(final short s) {
    long value = 1;
    for (int i = 0; i < s; i++) {
      value = value * 10;
    }
    return value;
  }

  public static long multiplyQtyAndPrice(final long qty, final long qtyPrecDiv, final long price, final long pricePrecDiv) {

    if (qty == 0 || price == 0) {
      return 0;
    }

    final long qtyS = qty / qtyPrecDiv;
    final long qtyD = qty % qtyPrecDiv;
    final long priceS = price / pricePrecDiv;
    final long priceD = price % pricePrecDiv;

    final long s = qtyS * priceS * pricePrecDiv;
    final long d;

    if (pricePrecDiv > qtyPrecDiv) {
      final long d1 = (qtyD * priceS) * (pricePrecDiv / qtyPrecDiv);
      final long d2 = qtyS * priceD;
      final long d3 = (qtyD * priceD) / qtyPrecDiv;
      d = d1 + d2 + d3;
    } else if (pricePrecDiv < qtyPrecDiv) {
      final long precDiff = (qtyPrecDiv / pricePrecDiv);
      final long d1 = qtyD * priceS;
      final long d2 = (qtyS * priceD) * precDiff;
      final long d3 = (qtyD * priceD) / pricePrecDiv;
      d = (d1 + d2 + d3) / precDiff;
    } else {
      final long d1 = qtyD * priceS;
      final long d2 = qtyS * priceD;
      final long d3 = (qtyD * priceD) / qtyPrecDiv;
      d = d1 + d2 + d3;
    }

    return s + d;
  }

  public static long stringQtyToFixedPointLong(final String value, final int decimalPlaces, final MutableBoolean truncatedIndicator) {

    return stringQtyToFixedPointLong(value, decimalPlaces, (long) Math.pow(10, decimalPlaces), truncatedIndicator);
  }

  public static long stringQtyToFixedPointLong(final String value, final int decimalPlaces, final long assetDivisor,
      final MutableBoolean truncatedIndicator) {

    final int decimalPointIndex = value.indexOf((int) '.');

    if (decimalPointIndex != -1) {
      final int fractionalPartBeginIndex = decimalPointIndex + 1;
      final int fractionalPartLength = value.length() - fractionalPartBeginIndex;
      final String wholePart = value.substring(0, decimalPointIndex);
      final String fractionalPart = value.substring(fractionalPartBeginIndex, fractionalPartBeginIndex + fractionalPartLength);

      if (fractionalPartLength > decimalPlaces) {
        truncatedIndicator.setValue(true);
        return Long.parseLong(wholePart + fractionalPart.substring(0, decimalPlaces));
      } else if (fractionalPartLength < decimalPlaces) {
        truncatedIndicator.setValue(false);
        return Math.multiplyExact(Long.parseLong(wholePart + fractionalPart), (long) Math.pow(10.0, (double) (decimalPlaces - fractionalPartLength)));
      } else {
        truncatedIndicator.setValue(false);
        return Long.parseLong(wholePart + fractionalPart);
      }
    } else {
      truncatedIndicator.setValue(false);
      return Math.multiplyExact(Long.parseLong(value), assetDivisor);
    }
  }

  public static long stringQtyToFixedPointLong(final String value, final int decimalPlaces) {
    return stringQtyToFixedPointLong(value, decimalPlaces, (long) Math.pow(10, decimalPlaces));
  }

  public static long stringQtyToFixedPointLong(final String value, final int decimalPlaces, final long assetDivisor) {

    final int decimalPointIndex = value.indexOf((int) '.');
    final long multiplyBy;

    if (decimalPointIndex != -1) {
      final int fractionalPartBeginIndex = decimalPointIndex + 1;
      final int fractionalPartLength = value.length() - fractionalPartBeginIndex;
      final String wholePart = value.substring(0, decimalPointIndex);
      final String fractionalPart = value.substring(fractionalPartBeginIndex, fractionalPartBeginIndex + fractionalPartLength);

      if (fractionalPartLength > decimalPlaces) {
        return Long.parseLong(wholePart + fractionalPart.substring(0, decimalPlaces));
      } else if (fractionalPartLength < decimalPlaces) {
        multiplyBy = (long) Math.pow(10.0, (double) (decimalPlaces - fractionalPartLength));
        return Math.multiplyExact(Long.parseLong(wholePart + fractionalPart), multiplyBy);
      } else {
        return Long.parseLong(wholePart + fractionalPart);
      }
    } else {
      return Math.multiplyExact(Long.parseLong(value), assetDivisor);
    }
  }

  public static String fixedPointLongToStringQty(final long value, final int decimalPlaces) {
    return fixedPointLongToStringQty(value, decimalPlaces, (long) Math.pow(10, decimalPlaces));
  }

  public static String fixedPointLongToStringQty(final long origValue, final int decimalPlaces, final long assetDivisor) {

    final boolean negative;
    final long value;

    if (origValue < 0) {
      negative = true;
      value = origValue * -1;
    } else {
      negative = false;
      value = origValue;
    }

    final long wholePart = value / assetDivisor;
    final long fractionalPart = value - wholePart * assetDivisor;

    final String ret;
    if (fractionalPart > 0) {
      ret = (negative ? "-" : "") + wholePart + "." + String.format("%0" + decimalPlaces + "d", fractionalPart);
    } else {
      if (wholePart == 0 || !negative) {
        ret = Long.toString(wholePart);
      } else {
        ret = "-" + wholePart;
      }
    }

    return ret;
  }

  public static String fixedPointBigIntegerToStringQty(final BigInteger origValue, final int decimalPlaces, final long assetDivisor) {

    final boolean negative;
    final BigInteger value;

    if (origValue.compareTo(BigInteger.ZERO) < 0) {
      negative = true;
      value = origValue.negate();
    } else {
      negative = false;
      value = origValue;
    }

    final BigInteger[] div = value.divideAndRemainder(BigInteger.valueOf(assetDivisor));
    final BigInteger wholePart = div[0];
    final BigInteger fractionalPart = div[1];

    final String ret;
    if (fractionalPart.compareTo(BigInteger.ZERO) > 0) {
      ret = (negative ? "-" : "") + wholePart + "." + String.format("%0" + decimalPlaces + "d", fractionalPart);
    } else {
      if (wholePart.compareTo(BigInteger.ZERO) == 0 || !negative) {
        ret = wholePart.toString();
      } else {
        ret = "-" + wholePart;
      }
    }

    return ret;
  }

  public static long changeScale(long value, int oldScale, int newScale) {
    if (value == 0L) {
      return value;
    } else {
      int i;
      if (newScale > oldScale) {
        for(i = 0; i < newScale - oldScale; ++i) {
          value *= 10L;
        }
      } else if (newScale < oldScale) {
        for(i = 0; i < oldScale - newScale; ++i) {
          value /= 10L;
        }
      }
      return value;
    }
  }

  public static long changeScale(double value, int newScale) {
    if (value == 0) {
      return (long) value;
    } else {
      int i;
      if (newScale > 0) {
        for(i = 0; i < newScale; ++i) {
          value *= 10L;
        }
      } else if (newScale < 0) {
        for(i = 0; i < - newScale; ++i) {
          value /= 10L;
        }
      }
      return (long) value;
    }
  }

  public static final class MutableBoolean {

    private boolean value;

    public MutableBoolean() {}

    public MutableBoolean(boolean value) {
      this.value = value;
    }

    public boolean getValue() {
      return value;
    }

    public void setValue(boolean value) {
      this.value = value;
    }
  }

}

