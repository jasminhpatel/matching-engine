package com.solfini.matchengine.executionexchange;

import static com.solfini.common.Constants.LOG_FMT_4;
import static com.solfini.common.Constants.TWO_MINUTE;

import com.solfini.common.CustomLogger;
import com.solfini.db.ExternalDBManager;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.liquidity.direct.aiGenerated.ExternalExchangeClient;
import com.solfini.util.MbxMath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ExternalTickerCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalTickerCache.class);
  private static final ConcurrentHashMap<String, Ticker> TICKER_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Double> COIN_MARKET_CAP_PRICE_CACHE = new ConcurrentHashMap<>();
  private static final long TICKER_REFRESH_TIME_MS = TWO_MINUTE;
  private static final String SELECT =
      "SELECT ticker, price FROM PricingData_CoinMarketCap WHERE ts > DATE_SUB(UTC_TIMESTAMP, INTERVAL 1 MINUTE) ORDER BY ticker, ts ASC;";

  public static Ticker getTicker(final ExternalSymbol symbol, final ExternalExchangeClient client) {
    final String key = symbol.getKey();
    // try cache
    final Ticker ticker = TICKER_CACHE.computeIfAbsent(key, v -> new Ticker());
    if (ticker.getTimestamp() <= (System.currentTimeMillis() - TICKER_REFRESH_TIME_MS)) {
      //reload if stale
      synchronized (ticker) {
        if (ticker.getTimestamp() <= (System.currentTimeMillis() - TICKER_REFRESH_TIME_MS)) {
          final Ticker latestTicker = loadFronExchange(symbol, client);
          ticker.copyFrom(latestTicker);
        }
      }
    }
    // load from coin-market-cap if failed from exchange
    if (ticker.getAsk() == 0 || ticker.getBid() == 0) {
      double cmcPrice = COIN_MARKET_CAP_PRICE_CACHE.getOrDefault(symbol.getBase().toUpperCase(), 0D);
      cmcPrice = MbxMath.roundUp(cmcPrice, symbol.getPriceScale());
      ticker.setAsk(cmcPrice);
      ticker.setBid(cmcPrice);
      LOGGER.info(LOG_FMT_4, "Using CoinMarketCap price. ticker key: ", key, " price: ", cmcPrice);
    }

    return ticker;
  }

  public static void loadCoinMarketCapPriceFromMPDB(final AtomicInteger loaderCounter) {
    LOGGER.info("CoinMarketCapPrice loading. ");
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = ExternalDBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery();) {
      while (rs.next()) {
        String ticker = rs.getString(1);
        double price = rs.getDouble(2);

        COIN_MARKET_CAP_PRICE_CACHE.put(ticker.toUpperCase(), price);
        count++;
      }
      LOGGER.info(LOG_FMT_4, "CoinMarketCapPrice.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0);
      if (loaderCounter != null) {
        int id = loaderCounter.decrementAndGet();
        LOGGER.info("CoinMarketCapPrice loaded. " + id);
      }
    } catch (final Exception e) {
      LOGGER.error("error", e);
      e.printStackTrace();
    }
  }

  private static Ticker loadFronExchange(final ExternalSymbol symbol, final ExternalExchangeClient client) {
    return client.getTicker(symbol.getBase(), symbol.getQuote(), symbol.getInstrumentType());
  }
}
