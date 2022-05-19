package com.solfini.matchengine.message.internal;

import java.util.Arrays;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.controller.Mode;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.sbe.encoder.MarketDataFeedDecoder;
import com.solfini.sbe.encoder.MarketDataFeedDecoder.MdEntrieGroupDecoder;

/**
 *
 * @author Chris Mack
 *
 */
public class MarketDataFeed extends Message implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(CancelReplaceOrder.class);

  private long sentTime;
  private double[] usdMarkArr;
  private double[] usdSpotIndexArr;
  private int entryCount;

  public MarketDataFeed() {
    // default constructor
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public final MessageType getMessageType() {
    return MessageType.MARKET_DATA_FEED;
  }

  public void set(final MarketDataFeedDecoder marketDataFeedDecoder) {
    sentTime = marketDataFeedDecoder.sentTime();

    int len = Math.max(32, InstrumentCache.getPairCapacity());
    usdMarkArr = new double[len];
    usdSpotIndexArr = new double[len];

    MdEntrieGroupDecoder mdEntrieGroupDecoder = marketDataFeedDecoder.mdEntrieGroup(); // positions
    while (mdEntrieGroupDecoder != null) {
      if (mdEntrieGroupDecoder.hasNext())
        mdEntrieGroupDecoder = mdEntrieGroupDecoder.next();
      else
        break;

      final int securityId = mdEntrieGroupDecoder.securityId();
      if (securityId < len) {
        usdMarkArr[securityId] = mdEntrieGroupDecoder.usdMark();
        usdSpotIndexArr[securityId] = mdEntrieGroupDecoder.usdSpotIndex();
      }
    }
  }

  public final long getSentTime() {
    return sentTime;
  }

  public final void setSentTime(final long sentTime) {
    this.sentTime = sentTime;
  }

  public final double[] getUsdMarkArr() {
    return usdMarkArr;
  }

  public final void setUsdMarkArr(final double[] usdMarkArr) {
    this.usdMarkArr = usdMarkArr;
  }

  public final double[] getUsdSpotIndexArr() {
    return usdSpotIndexArr;
  }

  public final void setUsdSpotIndexArr(final double[] usdSpotIndexArr) {
    this.usdSpotIndexArr = usdSpotIndexArr;
  }

  public final int getEntryCount() {
    return entryCount;
  }

  public final void setEntryCount(int entryCount) {
    this.entryCount = entryCount;
  }

  private final void setBTCDerivatives(final double price) {
    try {
      // set dated futures mark, && AssetType.DATED_FUTURE == pair.getAssetType()
      for (int i = 0; i < InstrumentCache.getPairCapacity(); i++) {
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair != null && pair.getSymbol() != null
            && ((pair.getSymbol().indexOf("BTC/USD[DF]") >= 0) || pair.getSymbol().indexOf("BTC/USD[A]") >= 0)) {
          pair.setIndexFeedUsdMark(price);
        }
      }
    } catch (Exception e) {
      LOGGER.error("", e);
    }
  }

  @Override
  public void onMatcher() {

    for (int i = 0; i < usdMarkArr.length; i++) {
      if (usdMarkArr[i] > 0) {
        entryCount++;
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair != null) {
          pair.setIndexFeedUsdMark(usdMarkArr[i]);
        } else {
          final Instrument instrument = InstrumentCache.get(i);
          if (instrument != null) {
            instrument.setIndexFeedUsdMark(usdMarkArr[i]);
            if (BTC.equals(instrument.getSymbol())) {
              setBTCDerivatives(usdMarkArr[i]);
            }
          }
        }
      }
    }

    if (Context.getControllerMode() == Mode.PRIMARY) {
      Context.getMatcherToPublisherQueue().addGuaranteed(this);

      try {
        // set LIQUIDATON_MODE if we have price data
        if (!MarginPreOrderCheckAndSettle.isLIQUIDATON_MODE()) {
          InstrumentPair pair = InstrumentCache.getPairBySymbol(BTC_USDC);
          if (pair == null)
            pair = InstrumentCache.getPairBySymbol(BTC_USD);

          if (!SnapLoader.isSnapLoaderMode() && pair != null && pair.getIndexFeedUsdMark() > 0)
            MarginPreOrderCheckAndSettle.setLIQUIDATON_MODE(true);
        }
      } catch (Exception e) {
        LOGGER.error(LOG_FMT_1, "error", e);
      }
    }

  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append(MARKET_DATA_FEED_EQ).append(sentTime).append(MARKETDATAFEED_EQ).append(Arrays.toString(usdMarkArr)).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"MarketDataFeed\"").append(",\"sentTime\":").append(sentTime).append(",\"priceArr\":")
        .append(Arrays.toString(usdMarkArr));
    sb.append("}");
    return sb.toString();
  }

  @Override
  public final void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

}
