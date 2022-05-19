package com.solfini.util;

import org.junit.Assert;
import org.junit.Test;

public class StringTest {
  public static final int N_ITERATIONS = 1_000_000;
  public static final byte[][][] arr = buildArr();

  private static final byte[][][] buildArr() {
    byte[][][] arr = new byte[256][16][];
    for (int i = 0; i < 256; i++) {
      for (int j = 0; j < 16; j++) {
        arr[i][j] = (String.valueOf(i) + "_" + String.valueOf(j)).getBytes();
      }
    }
    return arr;
  }

  public static final byte[] lookupByteArr(final int i, final int j) {
    return arr[i][j];
  }

  public static byte[] testDirect() {
    final String a = "1";
    final String b = "2";
    return (a + b).getBytes();
  }

  public static byte[] testLookup() {
    return lookupByteArr(1, 2);
  }

  @Test
  public void measureWithLookup() {
    long start = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++) {
      testLookup();
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("With lookup took " + elapsed + " ms");
  }

  @Test
  public void measureWithoutLookup() {
    long start = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++) {
      testDirect();
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Without lookup took " + elapsed + " ms");
  }

  @Test
  public void correctness() {
    Assert.assertEquals("1_2", new String(lookupByteArr(1, 2)));
    Assert.assertEquals("10_15", new String(lookupByteArr(10, 15)));
  }

  public static void main(String[] args) {
    final StringTest test = new StringTest();
    test.measureWithLookup();
    test.measureWithoutLookup();
  }
}
