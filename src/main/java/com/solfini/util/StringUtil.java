package com.solfini.util;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.solfini.common.Constants;
import com.solfini.internal.admin.schema.DecimalFloatDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import uk.co.real_logic.artio.fields.DecimalFloat;

/**
 *
 * @author Chris Mack
 *
 */
public final class StringUtil implements Constants {
  // use regular logger here because this is called by the custom logger
  private static final Logger LOGGER = LoggerFactory.getLogger(StringUtil.class);

  public static final int ZERO_CHAR = (int) '0';
  public static final int UNDERSCORE = (int) '_';
  public static final char SPACE = ' ';
  public static final char BBRACE = '}';

  private StringUtil() {
    // hidden default constructor
  }

  public static final short toShort(final String s) {
    try {
      return Short.parseShort(s);
    } catch (Exception e) {
      return 0;
    }
  }

  public static final int toInt(final String s) {
    try {
      return Integer.parseInt(s);
    } catch (Exception e) {
      return 0;
    }
  }

  public static final long toLong(final String s) {
    try {
      return Long.parseLong(s);
    } catch (Exception e) {
      return 0;
    }
  }

  public static final double toDouble(final String s) {
    try {
      return Double.parseDouble(s);
    } catch (Exception e) {
      return 0;
    }
  }

  public static final double toDouble(final BigDecimal s) {
    try {
      if (s == null)
        return 0;
      return s.doubleValue();
    } catch (Exception e) {
      return 0;
    }
  }

  public static final int charArrayToInt(final char[] data) {
    int result = 0;
    for (int i = 0; i < data.length; i++) {
      int digit = (int) data[i] - ZERO_CHAR;
      if ((digit < 0) || (digit > 9))
        throw new NumberFormatException();
      result *= 10;
      result += digit;
    }
    return result;
  }

  public static final int charArrayToInt(final char[] data, final int length) {
    int result = 0;
    for (int i = 0; i < length; i++) {
      int digit = (int) data[i] - ZERO_CHAR;
      if ((digit < 0) || (digit > 9))
        continue;
      result *= 10;
      result += digit;
    }
    return result;
  }

  public static final long charArrayToLong(final char[] data) {
    long result = 0;
    for (int i = 0; i < data.length; i++) {
      int digit = (int) data[i] - ZERO_CHAR;
      if ((digit < 0) || (digit > 9))
        throw new NumberFormatException();
      result *= 10;
      result += digit;
    }
    return result;
  }

  public static final long charArrayToLong(final char[] data, final int length) {
    long result = 0;
    for (int i = 0; i < data.length; i++) {
      int digit = (int) data[i] - ZERO_CHAR;
      if ((digit < 0) || (digit > 9))
        continue;
      result *= 10;
      result += digit;
    }
    return result;
  }

  public static final char[] copyWithBuffer(final char[] data) {
    final char[] charbuffer = new char[] {SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE};
    final int len = Math.min(data.length, charbuffer.length);
    for (int i = 0; i < len; i++)
      charbuffer[i] = data[i];

    return charbuffer;
  }

  public static final char[] copyWithBuffer32(final char[] data) {
    final char[] charbuffer = new char[] {SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE,
        SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE};
    final int len = Math.min(data.length, charbuffer.length);
    for (int i = 0; i < len; i++)
      charbuffer[i] = data[i];

    return charbuffer;
  }

  public static final char[] copyWithBuffer36(final char[] data) {
    final char[] charbuffer = new char[] {SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE,
        SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE, SPACE,
        SPACE, SPACE, SPACE};
    final int len = Math.min(data.length, charbuffer.length);
    for (int i = 0; i < len; i++)
      charbuffer[i] = data[i];

    return charbuffer;
  }

  public static final String fixToString(final byte[] bytes) {
    final StringBuilder sb = new StringBuilder();
    try {
      for (int i = 0; i < bytes.length; i++) {
        final byte b = bytes[i];
        final char c = (char) b;
        if (c >= SPACE && c <= BBRACE)
          sb.append(c);
        else
          sb.append(SPACE);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return sb.toString().trim();
  }

  public static final StringBuilder appendBytes(final byte[] bytes, final int length, final StringBuilder sb) {
    try {
      for (int i = 0; i < length; i++) {
        final byte b = bytes[i];
        final char c = (char) b;
        if (c >= SPACE && c <= BBRACE)
          sb.append(c);
        else
          sb.append(SPACE);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return sb;
  }

  public static final String fixToString(final ByteBuffer buffer, final int length) {
    final StringBuilder sb = new StringBuilder();
    try {
      for (int i = 0; i < length; i++) {
        final byte b = buffer.get(i);
        final char c = (char) b;
        if (c >= SPACE && c <= BBRACE)
          sb.append(c);
        else
          sb.append(SPACE);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return sb.toString().trim();
  }

  public static final StringBuilder trim(final StringBuilder sb) {
    if (sb == null || sb.length() == 0)
      return sb;

    int i = sb.length() - 1;
    for (; i >= 0; i--) {
      if (sb.charAt(i) != SPACE)
        break;
    }
    sb.setLength(i + 1);

    return sb;
  }

  public static final StringBuilder appendTo(final StringBuilder sb, final MessageHeaderDecoder headerDecoder) {
    sb.append("messageHeaderDecoder{\"MsgType\":\"").append(headerDecoder.templateId()).append("\",\"MsgSeqNum\":")
        .append(headerDecoder.msgSeqNum()).append("\"SendingTime\":\"").append(headerDecoder.sendingTime()).append("\"}");
    return sb;
  }


  public static final byte[] bufferToArray(final ByteBuffer buffer, final int length) {
    final byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = buffer.get(i);
    }
    return bytes;
  }

  public static final byte[] bufferToArray(final ByteBuffer buffer, final int length, final int offset) {
    final byte[] bytes = new byte[length + offset];
    for (int i = 0; i < length; i++) {
      bytes[i + offset] = buffer.get(i);
    }
    return bytes;
  }

  // calls a native bulk copy of memory
  public static final byte[] bufferToArrayBulk(final ByteBuffer buffer, final int length, final int offset) {
    final byte[] bytes = new byte[length + offset];
    buffer.position(0);
    buffer.get(bytes, offset, length);
    return bytes;
  }

  public static final byte[] bufferToArray(final ByteBuffer buffer, final int length, final int offset, final byte[] targetBytes) {
    for (int i = 0; i < length; i++) {
      targetBytes[i + offset] = buffer.get(i);
    }
    return targetBytes;
  }

  public static final String fixToString(final byte[] bytes, final int len) {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < bytes.length; i++) {
      if (i > len)
        break;

      final byte b = bytes[i];
      final char c = (char) b;
      if (c >= SPACE && c <= BBRACE)
        sb.append(c);
      else
        sb.append(SPACE);
    }
    return sb.toString();
  }

  public static final double toDouble(final DecimalFloat decimal) {
    if (decimal == null)
      return 0;

    double value = decimal.value();
    for (int i = 0; i < decimal.scale(); i++)
      value *= .1;
    return value;
  }

  public static final double toDouble(final DecimalFloatDecoder decimal) {
    if (decimal == null)
      return 0;

    double value = decimal.value();
    for (int i = 0; i < decimal.scale(); i++)
      value *= .1;
    return value;
  }

  public static final double toDouble(final long valueLong, final short scale) {
    double value = valueLong;
    for (int i = 0; i < scale; i++)
      value *= .1;
    return value;
  }

  public static final double round(final double decimal, final int multiplier) {
    long l = (long) (decimal * multiplier);
    return ((double) l) / multiplier;
  }

  public static final void charArrayToInt(final char[] data, final int posTypeLength, final int[] rc) {
    int rcCount = 0;
    int result = 0;
    for (int i = 0; i < posTypeLength; i++) { // changed to use posTypeLength instead of data.length
      if (data[i] == UNDERSCORE) {
        rc[rcCount] = result;
        rcCount++;
        result = 0;
        continue;
      }

      int digit = (int) data[i] - ZERO_CHAR;
      if ((digit < 0) || (digit > 9))
        throw new NumberFormatException("i=" + i + ", digit=" + digit + ", data=" + new String(data));
      result *= 10;
      result += digit;
    }
    rc[rcCount] = result;
  }

  public static String testFinal() {
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");
    return simpleDateFormat.format(new Date());
  }

  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
  private static final char CHAR_ZERO = '0';

  public static final long getMillisFromDateYYYMMDDHHMMSSsss(final String date) {
    final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");
    simpleDateFormat.setTimeZone(GMT);
    try {
      return simpleDateFormat.parse(date).getTime();
    } catch (ParseException e) {
      LOGGER.error(LOG_FMT_2, "Error", e);
    }
    return 0;
  }

  // returns the same as current simpleDateFormat yyyyMMdd-HH:mm:ss.SSS in GMT
  // with 4-5x performance and thread safe
  public static final String getCurrentDateYYYMMDDHHMMSSsss(final long timeInMillis) {
    final Calendar calendar = new GregorianCalendar(GMT);
    calendar.setTimeInMillis(timeInMillis);
    final int mYear = calendar.get(Calendar.YEAR);
    final int mMonth = calendar.get(Calendar.MONTH) + 1;
    final int mDay = calendar.get(Calendar.DAY_OF_MONTH);
    final int mHour = calendar.get(Calendar.HOUR_OF_DAY);
    final int mMin = calendar.get(Calendar.MINUTE);
    final int mSecond = calendar.get(Calendar.SECOND);
    final int mMillis = calendar.get(Calendar.MILLISECOND);

    final StringBuilder sb = new StringBuilder(22);
    sb.append(mYear);
    if (mMonth < 10)
      sb.append(CHAR_ZERO);
    sb.append(mMonth);
    if (mDay < 10)
      sb.append(CHAR_ZERO);
    sb.append(mDay);
    sb.append("-");
    if (mHour < 10)
      sb.append(CHAR_ZERO);
    sb.append(mHour);
    sb.append(":");
    if (mMin < 10)
      sb.append(CHAR_ZERO);
    sb.append(mMin);
    sb.append(":");
    if (mSecond < 10)
      sb.append(CHAR_ZERO);
    sb.append(mSecond);
    sb.append(".");
    if (mMillis < 10)
      sb.append(CHAR_ZERO);
    if (mMillis < 100)
      sb.append(CHAR_ZERO);
    sb.append(mMillis);
    return sb.toString();
  }

  public static final String intMonthToMMM(final int month) {
    switch (month) {
      case Calendar.JANUARY:
        return "Jan";
      case Calendar.FEBRUARY:
        return "Feb";
      case Calendar.MARCH:
        return "Mar";
      case Calendar.APRIL:
        return "Apr";
      case Calendar.MAY:
        return "May";
      case Calendar.JUNE:
        return "Jun";
      case Calendar.JULY:
        return "Jul";
      case Calendar.AUGUST:
        return "Aug";
      case Calendar.SEPTEMBER:
        return "Sep";
      case Calendar.OCTOBER:
        return "Oct";
      case Calendar.NOVEMBER:
        return "Nov";
      case Calendar.DECEMBER:
        return "Dec";
    }
    return "";
  }

  // returns the same as current simpleDateFormat yyyyMMdd-HH:mm:ss.SSS in GMT
  // with 4-5x performance and thread safe
  public static final String getCurrentDateYYYYMMDD() {
    final Calendar calendar = new GregorianCalendar(GMT);
    final int mYear = calendar.get(Calendar.YEAR);
    final int mMonth = calendar.get(Calendar.MONTH) + 1;
    final int mDay = calendar.get(Calendar.DAY_OF_MONTH);

    final StringBuilder sb = new StringBuilder(8);
    sb.append(mYear);
    if (mMonth < 10)
      sb.append(CHAR_ZERO);
    sb.append(mMonth);
    if (mDay < 10)
      sb.append(CHAR_ZERO);
    sb.append(mDay);
    return sb.toString();
  }


  // returns the same as current simpleDateFormat yyyyMMdd-HH:mm:ss.SSS in GMT
  // with 4-5x performance and thread safe
  public static final String getCurrentDateYYYYMMDDHHMMSSsss() {
    final Calendar calendar = new GregorianCalendar(GMT);
    final int mYear = calendar.get(Calendar.YEAR);
    final int mMonth = calendar.get(Calendar.MONTH) + 1;
    final int mDay = calendar.get(Calendar.DAY_OF_MONTH);
    final int mHour = calendar.get(Calendar.HOUR_OF_DAY);
    final int mMin = calendar.get(Calendar.MINUTE);
    final int mSecond = calendar.get(Calendar.SECOND);
    final int mMillis = calendar.get(Calendar.MILLISECOND);

    final StringBuilder sb = new StringBuilder(22);
    sb.append(mYear);
    if (mMonth < 10)
      sb.append(CHAR_ZERO);
    sb.append(mMonth);
    if (mDay < 10)
      sb.append(CHAR_ZERO);
    sb.append(mDay);
    sb.append("-");
    if (mHour < 10)
      sb.append(CHAR_ZERO);
    sb.append(mHour);
    sb.append(":");
    if (mMin < 10)
      sb.append(CHAR_ZERO);
    sb.append(mMin);
    sb.append(":");
    if (mSecond < 10)
      sb.append(CHAR_ZERO);
    sb.append(mSecond);
    sb.append(".");
    if (mMillis < 10)
      sb.append(CHAR_ZERO);
    if (mMillis < 100)
      sb.append(CHAR_ZERO);
    sb.append(mMillis);
    return sb.toString();
  }

  private static final byte[][][] arr = buildArr();

  private static final byte[][][] buildArr() {
    byte[][][] arr = new byte[256][16][];
    for (int i = 0; i < 256; i++) {
      for (int j = 0; j < 16; j++) {
        arr[i][j] = (String.valueOf(i) + "_" + j).getBytes();
      }
    }
    return arr;
  }

  // returns the byte array equal to (""+i+"_"+j).getBytes
  // 20x faster with no new memory
  // see StringTest example
  public static final byte[] lookupConcatIdByteArr(final int i, final int j) {
    return arr[i][j];
  }


  // returns the same as current simpleDateFormat yyyyMMdd-HH:mm:ss.SSS in GMT
  // with 4-5x performance and thread safe
  public static final String getCurrentDateYYYMMDDHHMMSSsss() {
    final Calendar calendar = new GregorianCalendar(GMT);
    final int mYear = calendar.get(Calendar.YEAR);
    final int mMonth = calendar.get(Calendar.MONTH) + 1;
    final int mDay = calendar.get(Calendar.DAY_OF_MONTH);
    final int mHour = calendar.get(Calendar.HOUR_OF_DAY);
    final int mMin = calendar.get(Calendar.MINUTE);
    final int mSecond = calendar.get(Calendar.SECOND);
    final int mMillis = calendar.get(Calendar.MILLISECOND);

    final StringBuilder sb = new StringBuilder(22);
    sb.append(mYear);
    if (mMonth < 10)
      sb.append(CHAR_ZERO);
    sb.append(mMonth);
    if (mDay < 10)
      sb.append(CHAR_ZERO);
    sb.append(mDay);
    sb.append("-");
    if (mHour < 10)
      sb.append(CHAR_ZERO);
    sb.append(mHour);
    sb.append(":");
    if (mMin < 10)
      sb.append(CHAR_ZERO);
    sb.append(mMin);
    sb.append(":");
    if (mSecond < 10)
      sb.append(CHAR_ZERO);
    sb.append(mSecond);
    sb.append(".");
    if (mMillis < 10)
      sb.append(CHAR_ZERO);
    if (mMillis < 100)
      sb.append(CHAR_ZERO);
    sb.append(mMillis);
    return sb.toString();
  }

  // returns the same as current simpleDateFormat yyyyMMdd-HH:mm:ss.SSS in GMT
  // with 4-5x performance and thread safe
  public static final String getCurrentDateYYYMMDDHHMMSSsss(final TimeZone timeZone) {
    final Calendar calendar = new GregorianCalendar(timeZone);
    final int mYear = calendar.get(Calendar.YEAR);
    final int mMonth = calendar.get(Calendar.MONTH) + 1;
    final int mDay = calendar.get(Calendar.DAY_OF_MONTH);
    final int mHour = calendar.get(Calendar.HOUR_OF_DAY);
    final int mMin = calendar.get(Calendar.MINUTE);
    final int mSecond = calendar.get(Calendar.SECOND);
    final int mMillis = calendar.get(Calendar.MILLISECOND);

    final StringBuilder sb = new StringBuilder(22);
    sb.append(mYear);
    if (mMonth < 10)
      sb.append(CHAR_ZERO);
    sb.append(mMonth);
    if (mDay < 10)
      sb.append(CHAR_ZERO);
    sb.append(mDay);
    sb.append("-");
    if (mHour < 10)
      sb.append(CHAR_ZERO);
    sb.append(mHour);
    sb.append(":");
    if (mMin < 10)
      sb.append(CHAR_ZERO);
    sb.append(mMin);
    sb.append(":");
    if (mSecond < 10)
      sb.append(CHAR_ZERO);
    sb.append(mSecond);
    sb.append(".");
    if (mMillis < 10)
      sb.append(CHAR_ZERO);
    if (mMillis < 100)
      sb.append(CHAR_ZERO);
    sb.append(mMillis);
    return sb.toString();
  }

  public static String toNumericString(final double v) {
    // render with US decimal point, plenty of precision, no grouping
    final String s = String.format(Locale.US, "%.20f", v);
    // trim trailing zeros and an optional trailing dot
    int end = s.length() - 1;
    while (end > 0 && s.charAt(end) == '0')
      end--;
    if (s.charAt(end) == '.')
      end--;
    return s.substring(0, end + 1);
  }

  public static final String toNumericString(final long amount, final int scale) {
    if (scale == 0) {
      return Long.toString(amount);
    }

    if (amount >= 0) {
      // Fast path: positive numbers
      final String value = Long.toString(amount);
      final int len = value.length();

      if (len <= scale) {
        // Pad with leading zeros: "0.00...value"
        final StringBuilder sb = new StringBuilder(scale + 2);
        sb.append("0.");
        for (int i = len; i < scale; i++) {
          sb.append('0');
        }
        sb.append(value);
        return sb.toString();
      } else {
        // Insert decimal point
        final int pointIndex = len - scale;
        final StringBuilder sb = new StringBuilder(len + 1);
        sb.append(value, 0, pointIndex);
        sb.append('.');
        sb.append(value, pointIndex, len);
        return sb.toString();
      }
    } else {
      // Negative branch (slower, rare)
      final long absAmount = -amount;
      final String value = Long.toString(absAmount);
      final int len = value.length();

      if (len <= scale) {
        final StringBuilder sb = new StringBuilder(scale + 3);
        sb.append("-0.");
        for (int i = len; i < scale; i++) {
          sb.append('0');
        }
        sb.append(value);
        return sb.toString();
      } else {
        final int pointIndex = len - scale;
        final StringBuilder sb = new StringBuilder(len + 2);
        sb.append('-');
        sb.append(value, 0, pointIndex);
        sb.append('.');
        sb.append(value, pointIndex, len);
        return sb.toString();
      }
    }
  }

  public static String processRSA(final String pem) {
    return pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
        .replaceAll("-----END [A-Z ]+-----", "")
        .replaceAll("\\s", "");
  }

  public static double roundUp(final double decimal, final int multiplier) {
    final long l = (long) Math.ceil(decimal * multiplier);
    return ((double) l) / multiplier;
  }

  public static double roundDown(final double decimal, final int multiplier) {
    final long l = (long) Math.floor(decimal * multiplier);
    return ((double) l) / multiplier;
  }

  public static void main(String[] args) {
    long t0 = System.nanoTime();
    for (int i = 0; i < 50_000_000; i++) {
      toNumericString(123456, 2);
    }
    System.out.println((System.nanoTime() - t0) / 1_000_000);

    System.out.println(toNumericString(123456, 2)); // "1234.56"
    System.out.println(toNumericString(123, 5)); // "0.00123"
    System.out.println(toNumericString(123, 12)); // "0.00123"
    System.out.println(toNumericString(-98765, 3)); // "-98.765"
    System.out.println(toNumericString(1000, 0)); // "1000"
  }
}
