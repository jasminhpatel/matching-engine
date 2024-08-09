package com.solfini.matchengine.copytrade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.HttpUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solfini.common.Constants.*;
import static com.solfini.matchengine.copytrade.ExternalExchangeUtil.EXCHANGE_SLUGS;

public class MarketDepthCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MarketDepthCache.class);
  private static final String COIN_MARKET_CAP_URL = "https://api.coinmarketcap.com";
  private static final String MARKET_DEPTH_API_URL = COIN_MARKET_CAP_URL + "/data-api/v3/exchange/market-pairs/latest";
  private static final String COIN_MARKET_CAP_API_HEADER = "X-CMC_PRO_API_KEY";
  private static final String COIN_MARKET_CAP_API_KEY = Context.getCoinMarketCapApiKey();
  private static final ConcurrentHashMap<String, MarketDepth> MARKET_DEPTH = new ConcurrentHashMap<>();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static void onLoad(final MarketDepth marketDepth) {
    final String key = (marketDepth.exchangeName + "_" + marketDepth.baseSymbol + "_" + marketDepth.quoteSymbol + "_" + marketDepth.category).toLowerCase();

    MARKET_DEPTH.put(key, marketDepth);
  }

  public static MarketDepth get(final String exchange, final String base, final String quote, final boolean futuresEnabled) {
    final String key = (exchange + "_" + base + "_" + quote+ "_" + (futuresEnabled ? "futures" : "spot")).toLowerCase();
    return MARKET_DEPTH.get(key);
  }

  public static String getBestQuoteCurrency(final String exchange, final String base, final Side side, final boolean futuresEnabled) {
    String keyUSD = (exchange + "_" + base + "_usd_" + (futuresEnabled ? "futures" : "spot")).toLowerCase();
    String keyUSDC = (exchange + "_" + base + "_usdc_" + (futuresEnabled ? "futures" : "spot")).toLowerCase();
    String keyUSDT = (exchange + "_" + base + "_usdt_" + (futuresEnabled ? "futures" : "spot")).toLowerCase();
    MarketDepth depth = null, tmp;
    String bestQuoteCurrency = null;
    tmp = MARKET_DEPTH.get(keyUSD);
    if (tmp != null) {
      depth = tmp;
      bestQuoteCurrency = USD;
    }
    tmp = MARKET_DEPTH.get(keyUSDC);
    if (depth == null && tmp != null) {
      depth = tmp;
      bestQuoteCurrency = USDC;
    } else if (tmp != null) {
      if (side == Side.SELL && tmp.depthUsdPositiveTwo > depth.depthUsdPositiveTwo) {
        depth = tmp;
        bestQuoteCurrency = USDC;
      } else if (side == Side.BUY && tmp.depthUsdNegativeTwo > depth.depthUsdNegativeTwo) {
        depth = tmp;
        bestQuoteCurrency = USDC;
      }
    }
    tmp = MARKET_DEPTH.get(keyUSDT);
    if (depth == null && tmp != null) {
      //depth = tmp;
      bestQuoteCurrency = USDT;
    } else if (tmp != null) {
      if (side == Side.SELL && tmp.depthUsdPositiveTwo > depth.depthUsdPositiveTwo) {
       // depth = tmp;
        bestQuoteCurrency = USDT;
      } else if (side == Side.BUY && tmp.depthUsdNegativeTwo > depth.depthUsdNegativeTwo) {
        //depth = tmp;
        bestQuoteCurrency = USDT;
      }
    }

    return bestQuoteCurrency;
  }

  public static void loadFromCoinMarketCap(final AtomicInteger loaderCounter) throws JsonProcessingException {
    final long t0 = System.currentTimeMillis();
    final Map<String, Object> headers = new HashMap<>();
    headers.put(COIN_MARKET_CAP_API_HEADER, COIN_MARKET_CAP_API_KEY);

    for (String exchangeSlug : EXCHANGE_SLUGS) {
      int total = 0, loaded = 0;
      int limit = 500;
      int skip = 1;
      try {
        do {
          String url = MARKET_DEPTH_API_URL + "?sort=marketPair&sort_dir=asc&slug=" + exchangeSlug + "&start=" + skip + "&limit=" + limit;
          HttpUtils.Response response = HttpUtils.get(url, headers);
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
      }
      LOGGER.info(LOG_FMT_5, "MarketDepthCache.loadFromCoinMarketCap Exchange: ", exchangeSlug, (long) loaded, ", time=", System.currentTimeMillis() - t0);
    }

    if (loaderCounter != null) {
      loaderCounter.decrementAndGet();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class MarketDepthResponse {
    private MarketDepths data;

    public MarketDepths getData() {
      return data;
    }

    public void setData(MarketDepths data) {
      this.data = data;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class MarketDepths {
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
  public static class MarketDepth {
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
