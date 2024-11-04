package com.solfini.matchengine.copytrade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.HttpUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solfini.common.Constants.ERROR_LOG;
import static com.solfini.common.Constants.LOG_FMT_5;

public class MarketDepthCacheTest {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MarketDepthCacheTest.class);
  private static final String COIN_MARKET_CAP_URL = "https://api.coinmarketcap.com";
  private static final String MARKET_DEPTH_API_URL = COIN_MARKET_CAP_URL + "/data-api/v3/exchange/market-pairs/latest";
  private static final String COIN_MARKET_CAP_API_HEADER = "X-CMC_PRO_API_KEY";
  private static final String COIN_MARKET_CAP_API_KEY = "5d89f95b-4f21-4909-8b97-746fb1892fc3";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  public final static String[] EXCHANGE_SLUGS = {
      "binance"
  };

  public static void main(String[] args) throws JsonProcessingException {
    loadFromCoinMarketCap(new AtomicInteger());
    String quotedSymbol = null;
    //quotedSymbol = MarketDepthCache.getBestQuoteCurrency("MEXC", "BTC", Side.BUY,true);
    System.out.println(quotedSymbol);
    //quotedSymbol = MarketDepthCache.getBestQuoteCurrency("MEXC", "BTC", Side.BUY,false);
    //System.out.println(quotedSymbol);
    quotedSymbol = MarketDepthCache.getBestQuoteCurrency("BINANCE", "BTC", Side.BUY, true);
    System.out.println(quotedSymbol);
    quotedSymbol = MarketDepthCache.getBestQuoteCurrency("BINANCE", "BTC", Side.BUY, false);
    System.out.println(quotedSymbol);
    System.out.println("Done.");
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
            System.out.println(returnValue);
            MarketDepthCache.MarketDepthResponse depthResponse = OBJECT_MAPPER.readValue(returnValue, MarketDepthCache.MarketDepthResponse.class);
            if (depthResponse.getData() != null) {
              if (depthResponse.getData().getMarketPairs().length == 0) {
                break;
              }
              total = depthResponse.getData().getNumMarketPairs();
              for (MarketDepthCache.MarketDepth data : depthResponse.getData().getMarketPairs()) {
                MarketDepthCache.onLoad(data);
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

/*  public static void loadFromCoinMarketCap(final AtomicInteger loaderCounter) throws JsonProcessingException {
    final long t0 = System.currentTimeMillis();
    final Map<String, Object> headers = new HashMap<>();
    headers.put(COIN_MARKET_CAP_API_HEADER, COIN_MARKET_CAP_API_KEY);

    try {
      String url = MARKET_DEPTH_API_URL + "?slug=binance&category=all&start=50&limit=50";
      HttpUtils.Response response = HttpUtils.get(url, headers);
      if (response != null && (response.getCode() == 200 || response.getCode() == 201)) {
        final String returnValue = response.getData();
        System.out.println(returnValue);
      }  else {
        String returnValue = null;
        if (response != null) {
          returnValue = response.getData();
        }
        System.out.println(returnValue);
      }
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    if (loaderCounter != null) {
      loaderCounter.decrementAndGet();
    }
  }*/
}
