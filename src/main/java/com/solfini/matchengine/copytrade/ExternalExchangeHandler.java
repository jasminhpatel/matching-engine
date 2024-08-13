package com.solfini.matchengine.copytrade;

import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;
import com.solfini.db.ExternalDBManager;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import com.solfini.sbe.encoder.Side;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solfini.common.Constants.*;

public class ExternalExchangeHandler {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalExchangeHandler.class);
  private static final Object LOCK = new Object();
  private static final ConcurrentHashMap<String, Price> PRICE_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Double> COIN_MARKET_CAP_PRICE_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, XExchange.Balance> BALANCE_CACHE = new ConcurrentHashMap<>();

  private static final String SELECT = "SELECT ticker, price FROM PricingData_CoinMarketCap WHERE ts > DATE_SUB(UTC_TIMESTAMP, INTERVAL 1 MINUTE) ORDER BY ticker, ts ASC;";

  public static void onLoadCoinMarketCapPrice(final String ticker, final double price) {
    String key = ticker.toUpperCase();
    COIN_MARKET_CAP_PRICE_CACHE.put(key, price);
  }

  public static double getPrice(final InfluencerSubscription subscription,final CurrencyPair pair, final Instrument instrument, final Side side, final XExchange xExchange) {
    final String key = (subscription.getExchange() + "_" + instrument.toString() + "_" + side.toString()).toLowerCase();
    final Price price = PRICE_CACHE.computeIfAbsent(key, v -> new Price());

    if (price.lastUpdated <= (System.currentTimeMillis() - TWO_MINUTE)) {
      synchronized (pair) {
        if (price.lastUpdated < (System.currentTimeMillis() - TWO_MINUTE)) {
          final Price exchangePrice = getPriceFromExchange(subscription, instrument, side, xExchange);

          LOGGER.info(LOG_FMT_6, "Exchange price updated: key: ", key, " side: ", side.name(),  " price: ", exchangePrice.getPrice());
          price.setPrice(exchangePrice.getPrice());
          price.setLastUpdated(exchangePrice.getLastUpdated());
        }
      }
    }
    if (price.getPrice() == 0) {
      price.setPrice(COIN_MARKET_CAP_PRICE_CACHE.getOrDefault(pair.base.getSymbol().toUpperCase(), 0D));
      price.setLastUpdated(System.currentTimeMillis());
      LOGGER.info(LOG_FMT_4, "Using CoinMarketCap price. ticker key: ", key, " price: ", price.getPrice());
    }
    if (price.getPrice() == 0) {
      LOGGER.info(LOG_FMT_4, "Failed to get price for ticker key: ", key, " price: ", price.getPrice());
    }

    return price.getPrice();
  }

  public static XExchange.Balance getStableCoinBalance(final InfluencerSubscription subscription, final XExchange xExchange) {
    String key = (subscription.getId() + "_buy").toLowerCase();
    LOGGER.info("Balance key: " + key + " subscriptionId: " + subscription.getId());
    XExchange.Balance balance = BALANCE_CACHE.get(key);
    if (balance == null || balance.getLastUpdated() <= (System.currentTimeMillis() - TWO_MINUTE)) {
      synchronized (subscription) {
        if (balance == null || balance.getLastUpdated() < (System.currentTimeMillis() - TWO_MINUTE)) {
          balance = getStableCoinBalanceFromExchange(xExchange);
          BALANCE_CACHE.put(key, balance);
          LOGGER.info(LOG_FMT_4, "Exchange balance updated: subscription: ", subscription.getId(), " balance: ", balance.toJson());
        }
      }
    }

    return balance;
  }

  public static XExchange.Balance getBalance(final InfluencerSubscription subscription, final XExchange xExchange, final String symbol) {
    String key = (subscription.getId() + "_sell_" + symbol).toLowerCase();
    LOGGER.info("Balance key: " + key + " subscriptionId: " + subscription.getId());
    XExchange.Balance balance = BALANCE_CACHE.get(key);
    if (balance == null || balance.getLastUpdated() <= (System.currentTimeMillis() - TWO_MINUTE)) {
      synchronized (subscription) {
        if (balance == null || balance.getLastUpdated() < (System.currentTimeMillis() - TWO_MINUTE)) {
          balance = getBalanceFromExchange(xExchange, symbol);
          BALANCE_CACHE.put(key, balance);
          LOGGER.info(LOG_FMT_4, "Exchange balance updated: subscription: ", subscription.getId(), " balance: ", balance.toJson());
        }
      }
    }

    return balance;
  }

  public static void loadCoinMarketCapPriceFromMPDB(final AtomicInteger loaderCounter) {
    int count = 0;
    final long t0 = System.currentTimeMillis();
    try (final Connection conn = ExternalDBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery();) {
      while (rs.next()) {
        String ticker = rs.getString(1);
        double price = rs.getDouble(2);

        onLoadCoinMarketCapPrice(ticker, price);
        count++;
      }
      LOGGER.info(LOG_FMT_4, "CoinMarketCapPrice.loadFromDB=", (long) count, ", time=", System.currentTimeMillis() - t0);
      if (loaderCounter != null) {
        loaderCounter.decrementAndGet();
      }
    } catch (final Exception e) {
      LOGGER.error("error", e);
    }
  }

  private static Price getPriceFromExchange(final InfluencerSubscription subscription, final Instrument instrument, final Side side, final XExchange xExchange) {
    return new Price(xExchange.getPriceFromExchange(instrument, side), System.currentTimeMillis());
  }

  private static XExchange.Balance getStableCoinBalanceFromExchange(final XExchange xExchange) {
    return xExchange.getStableCoinBalanceFromExchange();
  }

  private static XExchange.Balance getBalanceFromExchange(final XExchange xExchange, final String symbol) {
    return xExchange.getBalanceFromExchange(symbol);
  }

  private static class Price {
    private double price;
    private long lastUpdated;

    public Price() {
    }

    public Price(double price, long lastUpdated) {
      this.price = price;
      this.lastUpdated = lastUpdated;
    }

    public double getPrice() {
      return price;
    }

    public void setPrice(double price) {
      this.price = price;
    }

    public long getLastUpdated() {
      return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
      this.lastUpdated = lastUpdated;
    }
  }
}
