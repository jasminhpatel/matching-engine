package com.solfini.pricefeed;

public class Starter {
  public static void main(String args[]) throws InterruptedException {
    new Thread(new PriceListener()).start();
    // new Thread(new CalcThread()).start();

    Thread.sleep(10_000);
  }
}
