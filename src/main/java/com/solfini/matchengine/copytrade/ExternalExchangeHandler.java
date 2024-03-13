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
  private static final ConcurrentHashMap<String, Balance> BALANCE_CACHE = new ConcurrentHashMap<>();

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

  public static double getBalance(final InfluencerSubscription subscription, final XExchange xExchange) {
    String key = (subscription.getId() + "_" + subscription.getPreferredQuoteCurrency()).toLowerCase();
    LOGGER.info("Balance key: " + key + " subscriptionId: " + subscription.getId());
    final Balance
        balance = BALANCE_CACHE.computeIfAbsent(key, v-> new Balance());
    if (balance.lastUpdated <= (System.currentTimeMillis() - TWO_MINUTE)) {
      synchronized (subscription) {
        if (balance.lastUpdated < (System.currentTimeMillis() - TWO_MINUTE)) {
          final Balance exchangeBalance = getBalanceFromExchange(subscription, xExchange);

          LOGGER.info(LOG_FMT_4, "Exchange balance updated: subscription: ", subscription.getId(), " balance: ", exchangeBalance.getBalance());
          balance.setBalance(exchangeBalance.getBalance());
          balance.setLastUpdated(exchangeBalance.getLastUpdated());
        }
      }
    }

    return balance.getBalance();
  }

  private static Price getPriceFromExchange(final InfluencerSubscription subscription, final Instrument instrument, final Side side, final XExchange xExchange) {
    return new Price(xExchange.getPriceFromExchange(instrument, side), System.currentTimeMillis());
  }

  private static Balance getBalanceFromExchange(final InfluencerSubscription subscription, final XExchange xExchange) {
    return new Balance(xExchange.getBalanceFromExchange(subscription.getPreferredQuoteCurrency()), System.currentTimeMillis());
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

  private static class Balance {
    private double balance;
    private long lastUpdated;

    public Balance() {
    }

    public Balance(double balance, long lastUpdated) {
      this.balance = balance;
      this.lastUpdated = lastUpdated;
    }

    public double getBalance() {
      return balance;
    }

    public void setBalance(double balance) {
      this.balance = balance;
    }

    public long getLastUpdated() {
      return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
      this.lastUpdated = lastUpdated;
    }
  }
}
