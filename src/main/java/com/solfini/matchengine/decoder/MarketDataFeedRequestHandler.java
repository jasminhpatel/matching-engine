package com.solfini.matchengine.decoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solfini.common.Constants;
import com.solfini.common.Message;
import com.solfini.matchengine.message.internal.MarketDataFeed;
import com.solfini.pool.MarketDataFeedObjectPool;
import com.solfini.sbe.encoder.MarketDataFeedDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;

/**
 *
 * @author Chris Mack
 *
 */
public class MarketDataFeedRequestHandler implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(MarketDataFeedRequestHandler.class);

  public final Message decodeMarketDataFeed(final MessageHeaderDecoder headerDecoder, final MarketDataFeedDecoder marketDataFeedDecoder) {
    try {
      final MarketDataFeed marketDataFeed = MarketDataFeedObjectPool.get();
      marketDataFeed.set(marketDataFeedDecoder);
      marketDataFeed.setSenderCompId(headerDecoder.senderCompId());

      return marketDataFeed;
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }

}
