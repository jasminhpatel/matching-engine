package com.solfini.pricefeed;

import java.util.Random;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;

/**
 *
 * @author Chris Mack
 *
 */
public class PriceListener implements Runnable {

  public static final int GDAX = 1;
  public static final int KRAKEN = 2;
  public static final int BITSTAMP = 3;

  public static final String BTC = "BTC";
  public static final String ETH = "ETH";

  public static final int[] sourceArr = {GDAX, KRAKEN, BITSTAMP};
  public static final TickerTrie<PriceHolder> trie = new TickerTrie<>();
  public static final OneToOneConcurrentArrayQueue<PriceHolder> calcQueue = new OneToOneConcurrentArrayQueue<PriceHolder>(1024);

  public PriceListener() {
    // setup
    for (int i : sourceArr)
      trie.add(BTC + i, new PriceHolder(BTC, i));
    for (int i : sourceArr)
      trie.add(ETH + i, new PriceHolder(ETH, i));
  }

  @Override
  public void run() {
    Random r = new Random();

    while (true) {
      try {
        double price = 3500 + ((1000) * r.nextDouble());
        double size = 1 + ((1000) * r.nextDouble());
        int sourceIndex = r.nextInt(3);
        onMessage(BTC, sourceArr[sourceIndex], price, size);

        price = 100 + ((100) * r.nextDouble());
        size = 1 + ((1000) * r.nextDouble());
        sourceIndex = r.nextInt(3);
        onMessage(ETH, sourceArr[sourceIndex], price, size);

        Thread.sleep(1);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public static final void onMessage(final String symbol, final int sourceId, final double price, final double size) {
    System.out.println(">> PriceListener.onMessage symbol=" + symbol + ", sourceId=" + sourceId + ", price=" + price + ", size=" + size);
    PriceHolder priceHolder = trie.get(symbol + sourceId);
    priceHolder.set(price, size);

    if (priceHolder.getInQueue().compareAndSet(false, true))
      calcQueue.add(priceHolder);
  }



}
