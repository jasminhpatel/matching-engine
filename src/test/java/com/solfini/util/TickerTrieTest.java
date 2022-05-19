package com.solfini.util;

import org.junit.Test;

public class TickerTrieTest {

  @Test
  public void failsOnHyphen() {
    TickerTrie<Integer> tickerTrie = new TickerTrie<>();
    tickerTrie.add("user-name", 100);
  }

  public static void main(String[] args) {
    long start2 = TimeUtil.getTime();

    TickerTrie<Integer> tickerTrie = new TickerTrie<>();
    for (int i = 0; i < 1_000_000; i++) {
      tickerTrie.add("" + i, i);

    }
    System.out.println("done in " + ((TimeUtil.getTime() - start2)) / 1000000000F);

  }
}
