package com.solfini.marketdata;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.outbound.MarketDataSnapMessage;
import com.solfini.matchengine.publisher.SessionInfoCache;
import com.solfini.matchengine.session.SessionInfo;

/**
 *
 * @author Chris Mack
 *
 */

// used to build outgoing marketdata snap
// done in separate thread as its an expensive calc to traverse order books
// uses conflation atomic swap to guarantee no back pressure on calcQueue
// its use can be configured
public class MarketDataOutputBuilderThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MarketDataOutputBuilderThread.class);

  private static final ManyToOneConcurrentArrayQueueCustom<InstrumentPair> calcQueue = Context.getMarketDataBuilderQueue();
  private static final ManyToOneConcurrentArrayQueueCustom<Message> receiverToMatcherQueue = Context.getReceiverToMatcherQueue();

  public MarketDataOutputBuilderThread() {
    // default constructor
  }

  @Override
  public void run() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_1, "Starting MarketDataBuilderThread");
    }

    while (true) {
      try {
        Thread.sleep(0);
        final InstrumentPair instrumentPair = calcQueue.poll();
        if (instrumentPair != null)
          onMessage(instrumentPair);
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }

  private static final void onMessage(final InstrumentPair instrumentPair) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_2, MARKETDATABUILDERTHREAD, instrumentPair);
    }

    try {
      final SessionInfo sessionInfo = SessionInfoCache.getTradeApiArr()[0];

      // super expensive calc here
      final MarketDataSnapMessage message = MarketDataSnapMessage.create((instrumentPair), sessionInfo);

      receiverToMatcherQueue.addGuaranteed(message);


      // get the usdMark which should be the mid
      final double usdMark = instrumentPair.getOrderBook().getUsdMark();
      final int midPrice = (int) instrumentPair.adjustPriceToScale((long) (usdMark * instrumentPair.getPriceScaleMultiplier()),
          instrumentPair.getPriceScale());

      instrumentPair.getMidHistory().addToChart(System.currentTimeMillis(), midPrice, 1 * instrumentPair.getQuantityScaleMultiplier());

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    instrumentPair.isInMarketDataQueue().set(0);
  }

}
