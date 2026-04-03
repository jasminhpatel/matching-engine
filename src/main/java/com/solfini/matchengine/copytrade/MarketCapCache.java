package com.solfini.matchengine.copytrade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.HttpUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solfini.common.Constants.*;

public class MarketCapCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MarketCapCache.class);
  private static final String COIN_MARKET_CAP_URL = "https://pro-api.coinmarketcap.com";
  private static final String COIN_MARKET_CAP_API_HEADER = "X-CMC_PRO_API_KEY";
  private static final String COIN_MARKET_CAP_API_KEY = Context.getCoinMarketCapApiKey();
  private static final String MARKET_CAP_API_URL = COIN_MARKET_CAP_URL + "/v1/cryptocurrency/listings/latest?cryptocurrency_type=all&sort=market_cap&sort_dir=asc";
  private static final ConcurrentHashMap<String, MarketCap> MARKET_CAP = new ConcurrentHashMap<>();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static MarketCap getMarketCap(final String symbol) {
    if (symbol == null) return null;

    return MARKET_CAP.get(symbol.toUpperCase());
  }

  private static void onLoad(final MarketCap marketCap) {
    MARKET_CAP.put(marketCap.getSymbol().toUpperCase(), marketCap);
  }

  public static void loadFromCoinMarketCap(final AtomicInteger loaderCounter) throws JsonProcessingException {
    LOGGER.info("CoinMarketCapCache loading. ");
    final long t0 = System.currentTimeMillis();
    final Map<String, Object> headers = new HashMap<>();
    headers.put(COIN_MARKET_CAP_API_HEADER, COIN_MARKET_CAP_API_KEY);

    int total = 0, loaded = 0;
    int limit = 500;
    int skip = 1;
    try {
      do {
        String url = MARKET_CAP_API_URL + "&start=" + skip + "&limit=" + limit;
        HttpUtils.Response response = HttpUtils.get(url, headers);
        skip += limit;
        if (response != null && (response.getCode() == 200 || response.getCode() == 201)) {
          final String returnValue = response.getData();
          ListingsResponse listingsResponse = OBJECT_MAPPER.readValue(returnValue, ListingsResponse.class);
          if (listingsResponse.data != null) {
            total = listingsResponse.status.totalCount;
            for (ListingsData data : listingsResponse.data) {
              final MarketCap marketCap = new MarketCap(data.getSymbol(), data.getSlug(), data.quote.getUsd().marketCap, data.quote.getUsd().marketCapDominance);
              onLoad(marketCap);
              loaded++;
            }
          }
        } else {
          String returnValue = null;
          if (response != null) {
            returnValue = response.getData();

            final CMCError error = OBJECT_MAPPER.readValue(returnValue, CMCError.class);
            if (error.getStatus() != null && error.getStatus().getErrorCode() == 1008) {
              LOGGER.error(Constants.ERROR_LOG, "Failed to load market Cap from CoinMarketCap. ", returnValue);
              LOGGER.info("Waiting for one minute and retry");
              Thread.sleep(ONE_MINUTE);
            } else {
              LOGGER.error(Constants.ERROR_LOG, "Failed to load market Cap from CoinMarketCap. ", returnValue);
              break;
            }
          } else {
            LOGGER.error(Constants.ERROR_LOG, "Failed to load market Cap from CoinMarketCap. ", returnValue);
            break;
          }
        }
      } while (total > loaded);

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      e.printStackTrace();
    }

    LOGGER.info(LOG_FMT_4, "MarketCapCache.loadFromCoinMarketCap=", (long) loaded, ", time=", System.currentTimeMillis() - t0);

    if (loaderCounter != null) {
      int id = loaderCounter.decrementAndGet();
      LOGGER.info("CoinMarketCapCache loaded. " + id);
    }
  }

  public static class MarketCap {
    private final String symbol;
    private final String slug;
    private final double marketCap;
    private final double marketCapDominance;

    public MarketCap(String symbol, String slug, double marketCap, double marketCapDominance) {
      this.symbol = symbol;
      this.slug = slug;
      this.marketCap = marketCap;
      this.marketCapDominance = marketCapDominance;
    }

    public String getSymbol() {
      return symbol;
    }

    public String getSlug() {
      return slug;
    }

    public double getMarketCap() {
      return marketCap;
    }

    public double getMarketCapDominance() {
      return marketCapDominance;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class ListingsResponse {
    private ListingsStatus status;
    private ListingsData[] data;

    public ListingsStatus getStatus() {
      return status;
    }

    public void setStatus(ListingsStatus status) {
      this.status = status;
    }

    public ListingsData[] getData() {
      return data;
    }

    public void setData(ListingsData[] data) {
      this.data = data;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class ListingsStatus{
    @JsonProperty("total_count")
    private int totalCount;

    public int getTotalCount() {
      return totalCount;
    }

    public void setTotalCount(int totalCount) {
      this.totalCount = totalCount;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class ListingsData {
    private String symbol;
    private String slug;
    private Quote quote;

    public String getSymbol() {
      return symbol;
    }

    public void setSymbol(String symbol) {
      this.symbol = symbol;
    }

    public String getSlug() {
      return slug;
    }

    public void setSlug(String slug) {
      this.slug = slug;
    }

    public Quote getQuote() {
      return quote;
    }

    public void setQuote(Quote quote) {
      this.quote = quote;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class Quote {
    @JsonProperty("USD")
    private USDQuote usd;

    public USDQuote getUsd() {
      return usd;
    }

    public void setUsd(USDQuote usd) {
      this.usd = usd;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class USDQuote {
    @JsonProperty("market_cap")
    private double marketCap;
    @JsonProperty("market_cap_dominance")
    private double marketCapDominance;

    public double getMarketCap() {
      return marketCap;
    }

    public void setMarketCap(double marketCap) {
      this.marketCap = marketCap;
    }

    public double getMarketCapDominance() {
      return marketCapDominance;
    }

    public void setMarketCapDominance(double marketCapDominance) {
      this.marketCapDominance = marketCapDominance;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class CMCError {
    private CMCStatus status;

    public CMCStatus getStatus() {
      return status;
    }

    public void setStatus(CMCStatus status) {
      this.status = status;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class CMCStatus {
    @JsonProperty("error_code")
    private int errorCode;

    public int getErrorCode() {
      return errorCode;
    }

    public void setErrorCode(int errorCode) {
      this.errorCode = errorCode;
    }
  }

  public static void main(String[] args) throws JsonProcessingException {
    loadFromCoinMarketCap(new AtomicInteger());
    System.out.println("done");
  }
}
