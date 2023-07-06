package com.solfini.marketdata;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;

import static com.solfini.common.Constants.ERROR_LOG;

public class InactiveMarketDataPublisherThread implements Runnable {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(InactiveMarketDataPublisherThread.class);
  private static final ManyToOneConcurrentArrayQueueCustom<InstrumentPair> calcQueue = Context.getMarketDataBuilderQueue();

  @Override
  public void run() {
    //if (Context.isPublishMarketData()) {
      while (true) {
        try {
          for (int i = 1; i <= InstrumentCache.getPairCapacity(); i++) {
            final InstrumentPair instrumentPair = InstrumentCache.getPair(i);
            if (instrumentPair != null) {
              if (instrumentPair.isInMarketDataQueue().compareAndSet(0, 1))
                calcQueue.add(instrumentPair);
              else if (Context.getMarketDataBuilderQueue().isEmpty()) {
                instrumentPair.isInMarketDataQueue().set(0);
              }
            }
          }
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, e);
        } finally {
          try {
            Thread.sleep(Context.getInactiveMarketDataPublishTime());
          } catch (InterruptedException e) {
            LOGGER.error(ERROR_LOG, e);
          }
        }
      }
/*    } else {
      LOGGER.info("Thread is inactive because Context.isPublishMarketData() = " + Context.isPublishMarketData());
    }*/
  }
}
