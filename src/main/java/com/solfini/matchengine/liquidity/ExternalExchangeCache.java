package com.solfini.matchengine.liquidity;

import static com.solfini.common.Constants.ERROR_LOG;
import static com.solfini.common.Constants.TWO_SECOND;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.knowm.xchange.binance.dto.trade.TimeInForce;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.IdleStrategyFactory;
import com.solfini.common.ManyToManyConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.matchengine.executionexchange.ExternalExchangeHandler;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.matchengine.message.internal.Order;

public class ExternalExchangeCache {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalExchangeCache.class);
  private static final ManyToManyConcurrentArrayQueueCustom<Message> LIQUIDITY_ORDER_QUEUE = Context.getLiquidityRouterQueue();
  private static final ConcurrentHashMap<String, Set<org.knowm.xchange.dto.Order.IOrderFlags>> EXCHANGE_ORDER_FLAGS =
      new ConcurrentHashMap<>();
  private static final int NO_OF_THREADS = 4;
  private static final ExecutorService EXECUTOR_SERVICE = Executors.newVirtualThreadPerTaskExecutor();
  private static final InstrumentMetaData DEFAULT_META_DATA = buildDEFAULT_META_DATA();
  private static final List<String> EXCHANGE_PREFERENCE = buildEXCHANGE_PREFERENCE();
  private static final ConcurrentHashMap<String, XExchange> X_EXCHANGE_CACHE = new ConcurrentHashMap<>();

  public static final String BINANCE = "binance";
  public static final String BYBIT = "bybit";

  public static final InstrumentMetaData buildDEFAULT_META_DATA() {
    return new InstrumentMetaData.Builder().marketOrderEnabled(false).minimumAmount(new BigDecimal("50"))
        .maximumAmount(new BigDecimal("5000000")).priceScale(2).volumeScale(2).build();
  }

  public static final List<String> buildEXCHANGE_PREFERENCE() {
    final List<String> exchangePreference = new ArrayList<>();
    exchangePreference.addAll(Arrays.asList(Context.getLiquidityExchangePreference()));
    if (!exchangePreference.isEmpty() && !exchangePreference.get(0).equals(BYBIT)) {
      exchangePreference.remove(BYBIT);
      exchangePreference.add(0, BYBIT);
    }
    return exchangePreference;
  }

  static {
    if (Context.isLiquidityDexEnabled()) {
      for (int i = 1; i <= NO_OF_THREADS; i++) {
        EXECUTOR_SERVICE.submit(new LiquidityOrderRouter(IdleStrategyFactory.create(Context.getLiquidityDexThreadIdle())));
      }
    }
    // initialize exchange specific flags for order
    final Set<org.knowm.xchange.dto.Order.IOrderFlags> binanceFlags =
        EXCHANGE_ORDER_FLAGS.computeIfAbsent(BINANCE, k -> ConcurrentHashMap.newKeySet());
    binanceFlags.add(TimeInForce.IOC);
    // todo set for other exchanges
  }

  public static void initialize() {
    final StringBuilder sb = new StringBuilder();
    sb.append("\nStart initialising subscriptions. time: ").append(new Date());

    long start = System.currentTimeMillis();
    for (final String exchange : EXCHANGE_PREFERENCE) {
      // spot market
      try {
        final boolean futures = false;
        final String key = (exchange + "_" + futures).toLowerCase();
        final ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange.toLowerCase(), futures);
        if (subscription != null) {
          final long s = System.currentTimeMillis();
          final XExchange xExchange = ExternalExchangeUtil.createXExchange(subscription);
          if (xExchange != null) {
            X_EXCHANGE_CACHE.put(key, xExchange);
          }
          sb.append("\n key: ").append(key).append(" time taken: ").append((System.currentTimeMillis() - s) / 1000).append(" seconds.");
        }
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
      // futures market
      try {
        final boolean futures = true;
        final String key = (exchange + "_" + futures).toLowerCase();
        final ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange.toLowerCase(), futures);
        if (subscription != null) {
          final long s = System.currentTimeMillis();
          final XExchange xExchange = ExternalExchangeUtil.createXExchange(subscription);
          if (xExchange != null) {
            X_EXCHANGE_CACHE.put(key, xExchange);
          }
          sb.append("\n key: ").append(key).append(" time taken: ").append((System.currentTimeMillis() - s) / 1000).append(" seconds.");
        }
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
    long end = System.currentTimeMillis();
    sb.append("\nEnd initialising subscriptions. time: ").append(new Date());
    sb.append("\nTotal time taken: ").append((end - start) / 1000).append(" seconds.");

    LOGGER.info(sb.toString());
  }

  public static void refreshBalances() {
    final StringBuilder sb = new StringBuilder();
    sb.append("\nStart refreshing balances. time: ").append(new Date());
    long start = System.currentTimeMillis();

    for (final String exchange : EXCHANGE_PREFERENCE) {
      // spot market
      try {
        boolean futures = false;
        final String key = (exchange + "_" + futures).toLowerCase();
        final ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange.toLowerCase(), futures);
        if (subscription != null) {
          final long s = System.currentTimeMillis();
          final XExchange xExchange = X_EXCHANGE_CACHE.get(key);
          if (xExchange != null) {
            // todo log balances
            ExternalExchangeHandler.getStableCoinBalanceFromExchange(subscription, xExchange);
            Thread.sleep(TWO_SECOND);
            // todo for all symbols
            ExternalExchangeHandler.getBalancesFromExchange(subscription, xExchange, exchange); // todo for all symbols
            Thread.sleep(TWO_SECOND);
          }
          sb.append("\n key: ").append(key).append(" time taken: ").append((System.currentTimeMillis() - s) / 1000).append(" seconds.");
        }
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
      // futures market
      try {
        final boolean futures = true;
        final String key = (exchange + "_" + futures).toLowerCase();
        final ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange.toLowerCase(), futures);
        if (subscription != null) {
          final long s = System.currentTimeMillis();
          final XExchange xExchange = X_EXCHANGE_CACHE.get(key);
          if (xExchange != null) {
            // todo log balances
            XExchange.Balance stableCoinBalance = ExternalExchangeHandler.getStableCoinBalanceFromExchange(subscription, xExchange);
            // todo for all symbols
            // XExchange.Balance balance = ExternalExchangeHandler.getBalanceFromExchange(subscription, xExchange, baseSymbol);
          }
          sb.append("\n key: ").append(key).append(" time taken: ").append((System.currentTimeMillis() - s) / 1000).append(" seconds.");
        }
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }

    long end = System.currentTimeMillis();
    sb.append("\nEnd initialising subscriptions. time: ").append(new Date());
    sb.append("\nTotal time taken: ").append((end - start) / 1000).append(" seconds.");
    LOGGER.info(sb.toString());
  }

  public static void addOrder(final Order order) {
    LIQUIDITY_ORDER_QUEUE.addGuaranteed(order);
  }



  public static void main(final String[] args) {
    final Order order = new Order();
    order.setSymbol("BTC/USD");
    final int symbolSeparatorIndex = order.getSymbol().indexOf("/");
    final String baseSymbol = order.getSymbol().substring(0, symbolSeparatorIndex);
    final String quoteSymbol = order.getSymbol().substring(symbolSeparatorIndex + 1);
    System.out.printf("baseSymbol: %s, quoteSymbol: %s%n", baseSymbol, quoteSymbol);
  }

}
