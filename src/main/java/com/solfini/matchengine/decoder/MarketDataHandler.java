package com.solfini.matchengine.decoder;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshDecoder;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshDecoder.MdEntrieGroupDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class MarketDataHandler implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MarketDataHandler.class);

  private InstrumentPair BTC_USDC = null;
  private InstrumentPair BTC_USDC_F = null;
  private InstrumentPair ETH_USDC = null;
  private InstrumentPair ETH_USDC_F = null;

  private boolean isBTCReceived = false;
  private boolean isBTCUSDCSpotReceived = false;
  private boolean isBTCUSDCFutureReceived = false;

  // MarketDataSnapshotFullRefreshDecoder
  public MarketDataHandler() {
    BTC_USDC = InstrumentCache.getPairBySymbol(Context.getBtcUsdcSpotSymbol());
    BTC_USDC_F = InstrumentCache.getPairBySymbol(Context.getBtcUsdcFutureSymbol());
    ETH_USDC = InstrumentCache.getPairBySymbol(Context.getEthUsdcSpotSymbol());
    ETH_USDC_F = InstrumentCache.getPairBySymbol(Context.getEthUsdcFutureSymbol());
  }

  public Message decodeMarketData(final MessageHeaderDecoder headerDecoder,
      MarketDataSnapshotFullRefreshDecoder marketDataSnapshotFullRefreshDecoder) {
    try {
      final String symbol = marketDataSnapshotFullRefreshDecoder.symbol();

      int assetId = marketDataSnapshotFullRefreshDecoder.securityId();
      if (assetId == 0) {
        final InstrumentPair pair = InstrumentCache.getPairBySymbol(symbol);
        if (pair == null) {
          LOGGER.warn(LOG_FMT_2, "unable to lookup pair: ", symbol);
          return null;
        } else {
          assetId = pair.getId();
        }
      }

      final MdEntrieGroupDecoder mdEntriesGroupDecoder = marketDataSnapshotFullRefreshDecoder.mdEntrieGroup();
      final long price = mdEntriesGroupDecoder.price();
      final short priceScale = mdEntriesGroupDecoder.priceScale();
      final double priceDouble = StringUtil.toDouble(price, priceScale);

      final InstrumentPair pair = InstrumentCache.getPair(assetId);
      if (pair != null) {
        pair.setIndexFeedUsdMark(priceDouble);
        if (!isBTCUSDCFutureReceived && Context.getBtcUsdcFutureSymbol().equals(pair.getSymbol())) {
          isBTCUSDCFutureReceived = true;
        }
        if (!isBTCUSDCSpotReceived && Context.getBtcUsdcSpotSymbol().equals(pair.getSymbol())) {
          isBTCUSDCSpotReceived = true;
        }
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_8, PAIR_SETINDEXFEEDUSDMARK_ASSETID, assetId, PRICE_EQ, priceDouble, LIQUIDATON_MODE_EQ,
              MarginPreOrderCheckAndSettle.isLIQUIDATON_MODE(), ISBTCUSDCFUTURERECEIVED_EQ, isBTCUSDCFutureReceived);
        }
      } else {
        final Instrument instrument = InstrumentCache.get(assetId);
        instrument.setIndexFeedUsdMark(priceDouble);
        if (!isBTCReceived && Context.getBtcSymbol().equals(instrument.getSymbol())) {
          isBTCReceived = true;
        }
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_8, INSTRUMENT_SETINDEXFEEDUSDMARK_ASSETID, assetId, PRICE_EQ, priceDouble, LIQUIDATON_MODE_EQ,
              MarginPreOrderCheckAndSettle.isLIQUIDATON_MODE(), ISBTCUSDCFUTURERECEIVED_EQ, isBTCUSDCFutureReceived);
        }

        setIndexFeedUsdMark(instrument.getSymbol(), priceDouble);
      }

      // set LIQUIDATON_MODE
      if (!MarginPreOrderCheckAndSettle.isLIQUIDATON_MODE() && !SnapLoader.isSnapLoaderMode() && isBTCUSDCFutureReceived) {
        MarginPreOrderCheckAndSettle.setLIQUIDATON_MODE(true);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }

  private final void setIndexFeedUsdMark(final String symbol, final double price) {
    if (Context.getBtcSymbol().equals(symbol)) {
      if (BTC_USDC == null)
        BTC_USDC = InstrumentCache.getPairBySymbol(Context.getBtcUsdcSpotSymbol());
      if (BTC_USDC != null) {
        BTC_USDC.setIndexFeedUsdMark(price);
        isBTCUSDCSpotReceived = true;
      }

      if (BTC_USDC_F == null)
        BTC_USDC_F = InstrumentCache.getPairBySymbol(Context.getBtcUsdcFutureSymbol());
      if (BTC_USDC_F != null) {
        BTC_USDC_F.setIndexFeedUsdMark(price);
        isBTCUSDCFutureReceived = true;
      }

      // set dated futures mark, && AssetType.DATED_FUTURE == pair.getAssetType()
      for (int i = 0; i < InstrumentCache.getPairCapacity(); i++) {
        final InstrumentPair pair = InstrumentCache.getPair(i);
        if (pair != null && pair.getSymbol() != null && pair.getSymbol().indexOf("BTC/USD[DF]") >= 0) {
          pair.setIndexFeedUsdMark(price);
        }
      }
    } else if (Context.getEthSymbol().equals(symbol)) {
      if (ETH_USDC == null)
        ETH_USDC = InstrumentCache.getPairBySymbol(Context.getEthUsdcSpotSymbol());
      if (ETH_USDC != null)
        ETH_USDC.setIndexFeedUsdMark(price);

      if (ETH_USDC_F == null)
        ETH_USDC_F = InstrumentCache.getPairBySymbol(Context.getEthUsdcFutureSymbol());
      if (ETH_USDC_F != null)
        ETH_USDC_F.setIndexFeedUsdMark(price);
    }
  }

}
