package com.solfini.matchengine.copytrade;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import com.solfini.sbe.encoder.Side;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;

import java.util.concurrent.ConcurrentHashMap;

import static com.solfini.common.Constants.*;

public class ExternalExchangeHandler {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalExchangeHandler.class);
  private static final Object LOCK = new Object();
  private static final ConcurrentHashMap<String, Price> PRICE_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, XExchange.Balance> BALANCE_CACHE = new ConcurrentHashMap<>();

  public static double getPrice(final InfluencerSubscription subscription,final CurrencyPair pair, final Instrument instrument, final Side side, final XExchange xExchange) {
    final String key = (subscription.getExchange() + "_" + instrument.toString() + "_" + side.toString()).toLowerCase();
    final Price price = PRICE_CACHE.computeIfAbsent(key, v -> new Price());

    if (price.lastUpdated <= (System.currentTimeMillis() - TWO_MINUTE)) {
      synchronized (pair) {
        if (price.lastUpdated < (System.currentTimeMillis() - TWO_MINUTE)) {
          final Price exchangePrice = getPriceFromExchange(subscription, instrument, side, xExchange);

          LOGGER.info(LOG_FMT_4, "Exchange price updated: key: ", key, " side: ", side.name(),  " price: ", exchangePrice.getPrice());
          price.setPrice(exchangePrice.getPrice());
          price.setLastUpdated(exchangePrice.getLastUpdated());
        }
      }
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
