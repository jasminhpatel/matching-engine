package com.solfini.util;

import org.junit.Test;

public class FinalTest {
  public static final int N_ITERATIONS = 1_000_000;

  public static final String testFinal() {
    final String a = "a";
    final String b = "b";
    return a + b;
  }

  public static final String testNonFinal() {
    String a = "a";
    String b = "b";
    return a + b;
  }

  @Test
  public void measureFinal() {
    long start = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++) {
      testFinal();
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Method with final took " + elapsed + " ms");
  }

  @Test
  public void measureNonFinal() {
    long start = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++) {
      testNonFinal();
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Method without final took " + elapsed + " ms");
  }

  public static void main(String[] args) {
    final FinalTest test = new FinalTest();
    test.measureFinal();
    test.measureNonFinal();
  }
}
