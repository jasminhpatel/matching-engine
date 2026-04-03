package com.solfini.matchengine.executionexchange;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.db.ExternalDBManager;
import com.solfini.matchengine.ExecutionExchangeConfig;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange.Balance;
import com.solfini.matchengine.liquidity.LastBalance;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.instrument.Instrument;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.solfini.common.Constants.*;

public class  ExternalExchangeHandler {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalExchangeHandler.class);

  private static final ConcurrentHashMap<String, Price> PRICE_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Double> COIN_MARKET_CAP_PRICE_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, XExchange.Balance> BALANCE_CACHE = new ConcurrentHashMap<>();

  private static final String SELECT =
      "SELECT ticker, price FROM PricingData_CoinMarketCap WHERE ts > DATE_SUB(UTC_TIMESTAMP, INTERVAL 1 MINUTE) ORDER BY ticker, ts ASC;";

  public static void onLoadCoinMarketCapPrice(final String ticker, final double price) {
    String key = ticker.toUpperCase();
    COIN_MARKET_CAP_PRICE_CACHE.put(key, price);
  }

  public static double getPrice(final ExecutionExchangeConfig subscription, final CurrencyPair pair, final Instrument instrument,
      final Side side, final XExchange xExchange) {
    final String key = (subscription.getExchange() + "_" + instrument.toString() + "_" + side.toString()).toLowerCase();
    final Price price = PRICE_CACHE.computeIfAbsent(key, v -> new Price());

    if (price.lastUpdated <= (System.currentTimeMillis() - TWO_MINUTE)) {
      synchronized (pair) {
        if (price.lastUpdated < (System.currentTimeMillis() - TWO_MINUTE)) {
          final Price exchangePrice = getPriceFromExchange(subscription, instrument, side, xExchange);

          LOGGER.info(LOG_FMT_6, "Exchange price updated: key: ", key, " side: ", side.name(), " price: ", exchangePrice.getPrice());
          price.setPrice(exchangePrice.getPrice());
          price.setLastUpdated(exchangePrice.getLastUpdated());
        }
      }
    }
    if (price.getPrice() == 0) {
      // use coinmarketcap price
      final ExternalSymbol symbolStatus = ExternalInstrumentCache.getSymbolStatus(subscription.getExchange(),
          pair.getBase().getSymbol(), pair.getCounter().getSymbol(), instrument instanceof FuturesContract);
      if (symbolStatus != null) {
        double cmcPrice = COIN_MARKET_CAP_PRICE_CACHE.getOrDefault(pair.base.getSymbol().toUpperCase(), 0D);
        cmcPrice = MbxMath.roundUp(cmcPrice, symbolStatus.getPriceScale());
        price.setPrice(cmcPrice);
        price.setLastUpdated(System.currentTimeMillis());
        LOGGER.info(LOG_FMT_4, "Using CoinMarketCap price. ticker key: ", key, " price: ", price.getPrice());
      } else {
        LOGGER.info(LOG_FMT_4, "Fail to fetch symbol metadata. ticker key: ", key, " price: ", price.getPrice());
      }
    }
    if (price.getPrice() == 0) {
      LOGGER.info(LOG_FMT_4, "Failed to get price for ticker key: ", key, " price: ", price.getPrice());
    }

    return price.getPrice();
  }

  public static XExchange.Balance getStableCoinBalance(final ExecutionExchangeConfig subscription, final XExchange xExchange) {
    String key = (subscription.getId() + "_buy").toLowerCase();

    XExchange.Balance balance = BALANCE_CACHE.get(key);
    if (balance == null || balance.getLastUpdated() <= (System.currentTimeMillis() - Context.getExternalExchangeBalanceCacheDurationMs())) {
      synchronized (subscription) {
        if (balance == null
            || balance.getLastUpdated() < (System.currentTimeMillis() - Context.getExternalExchangeBalanceCacheDurationMs())) {
          balance = getStableCoinBalanceFromExchange(xExchange);
          BALANCE_CACHE.put(key, balance);
          if (subscription instanceof ExchangeSubscription exchangeSubscription) {
            final LastBalance usd = exchangeSubscription.getBalanceCache().computeIfAbsent(USD, v -> new LastBalance(USD, 0));
            usd.setQuantity(balance.getUsdBalance());
            final LastBalance usdc = exchangeSubscription.getBalanceCache().computeIfAbsent(USDC, v -> new LastBalance(USDC, 0));
            usdc.setQuantity(balance.getUsdcBalance());
            final LastBalance usdt = exchangeSubscription.getBalanceCache().computeIfAbsent(USDT, v -> new LastBalance(USDT, 0));
            usdt.setQuantity(balance.getUsdtBalance());
          }
          if (balance.hasBalance()) {
            LOGGER.info(LOG_FMT_4, "Exchange balance updated: subscription: ", subscription.getId(), " balance: ", balance.toJson());
          }
        }
      }
    }
    if (balance.hasBalance()) {
      LOGGER.info(LOG_FMT_6, "Balance key: ", key, " subscriptionId: ", subscription.getId(), " balance: ", balance.toJson());
    }
    return balance;
  }

  public static XExchange.Balance getStableCoinBalanceFromExchange(final ExecutionExchangeConfig subscription, final XExchange xExchange) {
    String key = (subscription.getId() + "_buy").toLowerCase();

    XExchange.Balance balance = getStableCoinBalanceFromExchange(xExchange);
    BALANCE_CACHE.put(key, balance);
    if (balance.hasBalance()) {
      LOGGER.info(LOG_FMT_4, "Exchange balance updated: subscription: ", subscription.getId(), " balance: ", balance.toJson());
    }

    return balance;
  }

  public static XExchange.Balance getBalance(final ExecutionExchangeConfig subscription, final XExchange xExchange, final String symbol) {
    String key = (subscription.getId() + "_sell_" + symbol).toLowerCase();
    XExchange.Balance balance = BALANCE_CACHE.get(key);
    if (balance == null || balance.getLastUpdated() <= (System.currentTimeMillis() - Context.getExternalExchangeBalanceCacheDurationMs())) {
      synchronized (subscription) {
        if (balance == null
            || balance.getLastUpdated() < (System.currentTimeMillis() - Context.getExternalExchangeBalanceCacheDurationMs())) {
          balance = getBalanceFromExchange(xExchange, symbol, subscription);
          BALANCE_CACHE.put(key, balance);
          if (subscription instanceof ExchangeSubscription exchangeSubscription) {
            if (exchangeSubscription.isFuturesEnabled()) {
              final LastBalance coin = exchangeSubscription.getPositionCache().computeIfAbsent(symbol.toUpperCase(), v -> new LastBalance(symbol.toUpperCase(), 0));
              coin.setQuantity(balance.getLastBalance(symbol));
            } else {
              final LastBalance coin = exchangeSubscription.getBalanceCache().computeIfAbsent(symbol.toUpperCase(), v -> new LastBalance(symbol.toUpperCase(), 0));
              coin.setQuantity(balance.getLastBalance(symbol));
            }
          }
          if (balance.hasBalance()) {
            LOGGER.info(LOG_FMT_6, "Exchange balance updated: subscription: ", subscription.getId(), " symbol: ", symbol,  " balance: ", balance.toJson());
          }
        }
      }
    }
    if (balance.hasBalance()) {
      LOGGER.info(LOG_FMT_6, "Balance key: ", key, " subscriptionId: ", subscription.getId(), " balance: ", balance.toJson());
    }

    return balance;
  }

  public static XExchange.Balance getBalanceFromExchange(final ExecutionExchangeConfig subscription, final XExchange xExchange,
      final String symbol) {
    String key = (subscription.getId() + "_sell_" + symbol).toLowerCase();
    XExchange.Balance balance = getBalanceFromExchange(xExchange, symbol, subscription);
    BALANCE_CACHE.put(key, balance);
    //LOGGER.info(LOG_FMT_4, "Exchange balance updated: subscription: ", subscription.getId(), " balance: ", balance.toJson());

    return balance;
  }

  public static void getBalancesFromExchange(final ExecutionExchangeConfig subscription, final XExchange xExchange, String exchange) {
    // List<String> symbols = new ArrayList<>();
    final List<String> symbols = ExternalInstrumentCache.getAvailableSymbols(exchange);
    final Map<String, XExchange.Balance> balanceMap = getBalancesFromExchange(xExchange, symbols, subscription);

    for (Entry<String, Balance> entry : balanceMap.entrySet()) {
      String key = (subscription.getId() + "_sell_" + entry.getKey()).toLowerCase();
      BALANCE_CACHE.put(key, entry.getValue());
      if (entry.getValue().hasBalance()) {
        LOGGER.info(LOG_FMT_4, "Exchange balance updated: subscription: ", subscription.getId(), " balance: ", entry.getValue().toJson());
      }
    }
  }

  //                  Todo use ExternalTickerCache
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

        onLoadCoinMarketCapPrice(ticker, price);
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

  private static Price getPriceFromExchange(final ExecutionExchangeConfig subscription, final Instrument instrument, final Side side,
      final XExchange xExchange) {
    return new Price(xExchange.getPriceFromExchange(instrument, side), System.currentTimeMillis());
  }

  private static XExchange.Balance getStableCoinBalanceFromExchange(final XExchange xExchange) {
    return xExchange.getStableCoinBalanceFromExchange();
  }

  private static XExchange.Balance getBalanceFromExchange(final XExchange xExchange, final String symbol, final ExecutionExchangeConfig subscription) {
    return xExchange.getBalanceFromExchange(symbol, subscription);
  }

  private static Map<String, XExchange.Balance> getBalancesFromExchange(final XExchange xExchange, final List<String> symbols, final ExecutionExchangeConfig subscription) {
    return xExchange.getBalancesFromExchange(symbols, subscription);
  }

  private static class Price {
    private double price;
    private long lastUpdated;

    public Price() {}

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
