package com.solfini.util;

import com.solfini.pool.ByteArrayPublisherThreadObjectPool;

public class PoolTest {
  public static final int N_ITERATIONS = 1_000;

  public static String testFinal() {
    for (int i = 128; i < 1024; i++) {
      final byte[] bytes = new byte[i];
    }
    return "";
  }

  public static String testNonFinal() {
    for (int i = 128; i < 1024; i++) {
      final byte[] bytes = ByteArrayPublisherThreadObjectPool.get(i);
      // ByteArrayPublisherThreadObjectPool.returnObject(bytes);
    }
    return "";
  }

  public static void main(String[] args) {
    ByteArrayPublisherThreadObjectPool.get(1);

    long tStart, tElapsed;
    tStart = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++)
      testFinal();
    tElapsed = System.currentTimeMillis() - tStart;
    System.out.println("Method with finals took " + tElapsed + " ms");
    tStart = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++)
      testNonFinal();
    tElapsed = System.currentTimeMillis() - tStart;
    System.out.println("Method with pool took " + tElapsed + " ms");


    // long tStart, tElapsed;
    tStart = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++)
      testFinal();
    tElapsed = System.currentTimeMillis() - tStart;
    System.out.println("Method with finals took " + tElapsed + " ms");
    tStart = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++)
      testNonFinal();
    tElapsed = System.currentTimeMillis() - tStart;
    System.out.println("Method with pool took " + tElapsed + " ms");

    // long tStart, tElapsed;
    tStart = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++)
      testFinal();
    tElapsed = System.currentTimeMillis() - tStart;
    System.out.println("Method with finals took " + tElapsed + " ms");
    tStart = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++)
      testNonFinal();
    tElapsed = System.currentTimeMillis() - tStart;
    System.out.println("Method with pool took " + tElapsed + " ms");

    // long tStart, tElapsed;
    tStart = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++)
      testFinal();
    tElapsed = System.currentTimeMillis() - tStart;
    System.out.println("Method with finals took " + tElapsed + " ms");
    tStart = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++)
      testNonFinal();
    tElapsed = System.currentTimeMillis() - tStart;
    System.out.println("Method with pool took " + tElapsed + " ms");

    // long tStart, tElapsed;
    tStart = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++)
      testFinal();
    tElapsed = System.currentTimeMillis() - tStart;
    System.out.println("Method with finals took " + tElapsed + " ms");
    tStart = System.currentTimeMillis();
    for (int i = 0; i < N_ITERATIONS; i++)
      testNonFinal();
    tElapsed = System.currentTimeMillis() - tStart;
    System.out.println("Method with pool took " + tElapsed + " ms");
  }

}
