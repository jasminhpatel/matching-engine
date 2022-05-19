package com.solfini.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.junit.Test;

public class DateTest {
  public static final int N_ITERATIONS = 1_000_000;
  public static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");

  public static final String testSimpleDate() {
    final String s = simpleDateFormat.format(new Date());
    return s;
  }

  public static final String testStringUtilDate() {
    final String s = StringUtil.getCurrentDateYYYYMMDDHHMMSSsss();
    return s;
  }

  @Test
  public void measureSimpleDate() {
    long start = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++) {
      testSimpleDate();
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("SimpleDateFormat.format(Date) took " + elapsed + " ms");
  }

  @Test
  public void measureStringUtilDate() {
    long start = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++) {
      testStringUtilDate();
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("StringUtil.getCurrentDateYYYYMMDDHHMMSSsss took " + elapsed + " ms");
  }

  public static void main(String[] args) {
    final DateTest test = new DateTest();
    test.measureSimpleDate();
    test.measureStringUtilDate();
  }
}
