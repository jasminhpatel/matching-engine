package com.solfini.matchengine.copytrade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.ExternalInstrumentCache;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.HttpUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solfini.common.Constants.*;
import static com.solfini.matchengine.executionexchange.ExternalExchangeUtil.EXCHANGE_SLUGS;

public class MarketDepthCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MarketDepthCache.class);
  private static final String COIN_MARKET_CAP_URL = "https://api.coinmarketcap.com";
  private static final String MARKET_DEPTH_API_URL = COIN_MARKET_CAP_URL + "/data-api/v3/exchange/market-pairs/latest";
  private static final String COIN_MARKET_CAP_API_HEADER = "X-CMC_PRO_API_KEY";
  private static final String COIN_MARKET_CAP_API_KEY = Context.getCoinMarketCapApiKey();
  private static final ConcurrentHashMap<String, MarketDepth> MARKET_DEPTH = new ConcurrentHashMap<>();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String UPSERT_SQL = """
        INSERT INTO coin_market_cap_depth (exchangeName, baseSymbol, quoteSymbol, category, depthUsdNegativeTwo, depthUsdPositiveTwo, updated)
        VALUES (?, ?, ?, ?, ?, ?, now())
        ON CONFLICT (exchangeName, baseSymbol, quoteSymbol, category)
        DO UPDATE SET
            depthUsdNegativeTwo = EXCLUDED.depthUsdNegativeTwo,
            depthUsdPositiveTwo = EXCLUDED.depthUsdPositiveTwo,
            updated = now()
        """;

  public static void onLoad(final MarketDepth marketDepth) {
    final String key = (marketDepth.exchangeName + "_" + marketDepth.baseSymbol + "_" + marketDepth.quoteSymbol + "_" + marketDepth.category).toLowerCase();

    MARKET_DEPTH.put(key, marketDepth);
  }

  public static MarketDepth get(final String exchange, final String base, final String quote, final boolean futuresEnabled) {
    final String key = (exchange + "_" + base + "_" + quote+ "_" + (futuresEnabled ? "perpetual" : "spot")).toLowerCase();
    return MARKET_DEPTH.get(key);
  }

  public static String getBestQuoteCurrency(final String exchange, final String base, final Side side, final boolean futuresEnabled) {
    LOGGER.info(LOG_FMT_8, " exchange: ", exchange, " base: ", base, " side: ", side.name(), " futuresEnabled: ", futuresEnabled);
    MarketDepth usdDepth = null, usdcDepth = null, usdtDepth = null;
    if (ExternalInstrumentCache.isTradeableOnExchange(exchange, base, USD, futuresEnabled)) {
      final String keyUSD = (exchange + "_" + base + "_usd_" + (futuresEnabled ? "perpetual" : "spot")).toLowerCase();
      usdDepth = MARKET_DEPTH.get(keyUSD);
      LOGGER.info(LOG_FMT_1, "keyUSD: " + keyUSD + " depth: " + usdDepth);
    }
    if (ExternalInstrumentCache.isTradeableOnExchange(exchange, base, USDC, futuresEnabled)) {
      final String keyUSDC = (exchange + "_" + base + "_usdc_" + (futuresEnabled ? "perpetual" : "spot")).toLowerCase();
      usdcDepth = MARKET_DEPTH.get(keyUSDC);
      LOGGER.info(LOG_FMT_1, "keyUSDC: " + keyUSDC + " depth: " + usdcDepth);
    }
    if (ExternalInstrumentCache.isTradeableOnExchange(exchange, base, USDT, futuresEnabled)) {
      final String keyUSDT = (exchange + "_" + base + "_usdt_" + (futuresEnabled ? "perpetual" : "spot")).toLowerCase();
      usdtDepth = MARKET_DEPTH.get(keyUSDT);
      LOGGER.info(LOG_FMT_1, "keyUSDT: " + keyUSDT + " depth: " + usdtDepth);
    }

    MarketDepth bestDepth = getBestDepth(usdDepth, usdcDepth, side);
    bestDepth = getBestDepth(bestDepth, usdtDepth, side);

    if (bestDepth != null) {
      return bestDepth.quoteSymbol;
    }
    return null;
  }

  private static MarketDepth getBestDepth(final MarketDepth d1, final MarketDepth d2, final Side side) {
    if (d1 == null) return d2;
    if (d2 == null) return d1;

    if (side == Side.SELL) {
      if (d1.depthUsdPositiveTwo > d2.depthUsdPositiveTwo) {
        return d1;
      } else {
        return d2;
      }
    } else {
      if (d1.depthUsdNegativeTwo > d2.depthUsdNegativeTwo) {
        return d1;
      } else {
        return d2;
      }
    }
  }

  public static void loadFromCoinMarketCap(final AtomicInteger loaderCounter) throws JsonProcessingException {
    LOGGER.info("MarketDepthCache loading. ");
    final long t0 = System.currentTimeMillis();
    final Map<String, Object> headers = new HashMap<>();
    headers.put(COIN_MARKET_CAP_API_HEADER, COIN_MARKET_CAP_API_KEY);

    for (String exchangeSlug : EXCHANGE_SLUGS) {
      final List<MarketDepth> marketDepths = new ArrayList<>();
      int total = 0, loaded = 0;
      int limit = 500;
      int skip = 1;
      try {
        do {
          String url = MARKET_DEPTH_API_URL + "?sort=marketPair&sort_dir=asc&slug=" + exchangeSlug + "&start=" + skip + "&limit=" + limit;
          HttpUtils.Response response = HttpUtils.get(url, headers, ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);
          skip += limit;
          if (response != null && (response.getCode() == 200 || response.getCode() == 201)) {
            final String returnValue = response.getData();
            MarketDepthResponse depthResponse = OBJECT_MAPPER.readValue(returnValue, MarketDepthResponse.class);
            if (depthResponse.data != null) {
              if (depthResponse.data.marketPairs.length == 0) {
                break;
              }
              total = depthResponse.data.numMarketPairs;
              for (MarketDepth data : depthResponse.data.marketPairs) {
                onLoad(data);
                marketDepths.add(data);
                loaded++;
              }
            }
          } else {
            String returnValue = null;
            if (response != null) {
              returnValue = response.getData();
            }
            LOGGER.error(Constants.ERROR_LOG, "Failed to load market Cap from CoinMarketCap. ", returnValue);
            break;
          }

        } while (true);
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
        e.printStackTrace();
      }
      if (!marketDepths.isEmpty()) {
        try {
          upsertBatch(marketDepths);
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, e);
        }
      }
      LOGGER.info(LOG_FMT_5, "MarketDepthCache.loadFromCoinMarketCap Exchange: ", exchangeSlug, (long) loaded, ", time=", System.currentTimeMillis() - t0);
    }

    if (loaderCounter != null) {
      int id = loaderCounter.decrementAndGet();
      LOGGER.info("MarketDepthCache loaded. " + id);
    }
  }

  public static void upsertBatch(List<MarketDepth> marketDepths) {
    final long t0 = System.currentTimeMillis();

    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {

      conn.setAutoCommit(false);

      try {
        int count = 0;
        for (final MarketDepth depth : marketDepths) {
          ps.setString(1, depth.getExchangeName());
          ps.setString(2, depth.getBaseSymbol());
          ps.setString(3, depth.getQuoteSymbol());
          ps.setString(4, depth.getCategory());
          ps.setDouble(5, depth.getDepthUsdNegativeTwo());
          ps.setDouble(6, depth.getDepthUsdPositiveTwo());
          ps.addBatch();
          count++;

          if (count % 500 == 0) {
            ps.executeBatch();
            conn.commit();
          }
        }

        ps.executeBatch();
        conn.commit();

        LOGGER.info("MarketDepth upsertBatch complete, count={}, time={}ms", count, System.currentTimeMillis() - t0);

      } catch (final Exception e) {
        conn.rollback();  // undo any uncommitted changes
        LOGGER.error("error during upsertBatch, rolling back", e);
        e.printStackTrace();
      } finally {
        conn.setAutoCommit(true);  // always restore before returning to pool
      }

    } catch (final Exception e) {
      LOGGER.error("error acquiring connection", e);
      e.printStackTrace();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class MarketDepthResponse {
    private MarketDepths data;

    public MarketDepths getData() {
      return data;
    }

    public void setData(MarketDepths data) {
      this.data = data;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class MarketDepths {
    private MarketDepth[] marketPairs;
    private int numMarketPairs;

    public MarketDepth[] getMarketPairs() {
      return marketPairs;
    }

    public void setMarketPairs(MarketDepth[] marketPairs) {
      this.marketPairs = marketPairs;
    }

    public int getNumMarketPairs() {
      return numMarketPairs;
    }

    public void setNumMarketPairs(int numMarketPairs) {
      this.numMarketPairs = numMarketPairs;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class MarketDepth {
    private String exchangeName;
    private String baseSymbol;
    private String quoteSymbol;
    private String category;
    private double depthUsdNegativeTwo;
    private double depthUsdPositiveTwo;

    public String getExchangeName() {
      return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
      this.exchangeName = exchangeName;
    }

    public String getBaseSymbol() {
      return baseSymbol;
    }

    public void setBaseSymbol(String baseSymbol) {
      this.baseSymbol = baseSymbol;
    }

    public String getQuoteSymbol() {
      return quoteSymbol;
    }

    public void setQuoteSymbol(String quoteSymbol) {
      this.quoteSymbol = quoteSymbol;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public double getDepthUsdNegativeTwo() {
      return depthUsdNegativeTwo;
    }

    public void setDepthUsdNegativeTwo(double depthUsdNegativeTwo) {
      this.depthUsdNegativeTwo = depthUsdNegativeTwo;
    }

    public double getDepthUsdPositiveTwo() {
      return depthUsdPositiveTwo;
    }

    public void setDepthUsdPositiveTwo(double depthUsdPositiveTwo) {
      this.depthUsdPositiveTwo = depthUsdPositiveTwo;
    }
  }
}
