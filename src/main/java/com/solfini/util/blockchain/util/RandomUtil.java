package com.solfini.util.blockchain.util;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RandomUtil {
  private final static SecureRandom random = new SecureRandom();
  private final static AtomicInteger atomicIndex = new AtomicInteger();
  private final static ArrayList<String> randomCache = new ArrayList<String>();
  private final static AtomicBoolean loading = new AtomicBoolean(false);

  private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final SecureRandom RANDOM = new SecureRandom();

  static {
    for (int i = 0; i < 100; i++) {
      randomCache.add(nextSessionId());
    }
  }

  public static final String generateString(final int length) {
    final StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++)
      sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
    return sb.toString();
  }

  public static int generateInt(final int limit) {
    return RANDOM.nextInt(limit);
  }

  public static String getRandomString() {
    return nextSessionId();
    /*
     * int index = atomicIndex.getAndIncrement(); if(randomCache.size()<=index) { if(loading.compareAndSet(false, true)) { new Thread(new
     * GenerateThread(100)).start(); } return nextSessionId(); } return randomCache.get(index);
     */
  }

  public static String nextSessionId() {
    return new BigInteger(130, random).toString(32);
  }

  public static class GenerateThread implements Runnable {
    int len;

    public GenerateThread(int len) {
      this.len = len;
    }

    @Override
    public void run() {
      for (int i = 0; i < len; i++) {
        randomCache.add(nextSessionId());
      }
      loading.set(false);
    }
  }
}
