package com.solfini.matchengine.copytrade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.solfini.common.Constants;
import com.solfini.util.HttpUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MarketDepthCacheTest {
  private static final String COIN_MARKET_CAP_URL = "https://api.coinmarketcap.com";
  private static final String MARKET_DEPTH_API_URL = COIN_MARKET_CAP_URL + "/data-api/v3/exchange/market-pairs/latest";
  private static final String COIN_MARKET_CAP_API_HEADER = "X-CMC_PRO_API_KEY";
  private static final String COIN_MARKET_CAP_API_KEY = "5d89f95b-4f21-4909-8b97-746fb1892fc3";

  public static void main(String[] args) throws JsonProcessingException {
    loadFromCoinMarketCap(new AtomicInteger());
  }

  public static void loadFromCoinMarketCap(final AtomicInteger loaderCounter) throws JsonProcessingException {
    final long t0 = System.currentTimeMillis();
    final Map<String, Object> headers = new HashMap<>();
    headers.put(COIN_MARKET_CAP_API_HEADER, COIN_MARKET_CAP_API_KEY);

    try {
      String url = MARKET_DEPTH_API_URL + "?slug=mexc&category=all&start=50&limit=50";
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
  }
}
