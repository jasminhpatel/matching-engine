package com.solfini.matchengine.copytrade;

import com.solfini.common.*;
import com.solfini.matchengine.copytrade.xchangewrappers.XBinanceExchange;
import com.solfini.matchengine.copytrade.xchangewrappers.XBybitExchange;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import com.solfini.matchengine.copytrade.xchangewrappers.XMEXCExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.dto.trade.BinanceQueryOrderParams;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType;
import org.knowm.xchange.bybit.dto.trade.ByBitQueryOrderParams;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.mexc.MEXCExchange;
import org.knowm.xchange.mexc.dto.trade.MEXCQueryOrderParams;
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParam;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.util.Random;

public class ExternalExchangeUtil {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalExchangeUtil.class);
  private static final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();

  public final static String[] EXCHANGES = {
      "BINANCE", "BITFINEX", "BITFLYER", "BITHUMB", "BITMEX",
      "BITSTAMP", "BYBIT", "COINBASE", "GATEIO", "GEMINI",
      "KRAKEN", "KUCOIN", "MEXC", "OKEX", "UPBIT",
  };
  private static String[] PROXIES = null;
  private static Random RANDOM = new Random();

  static {
    final String proxyIPs = Context.getCopyTradeProxyIps();
    if (proxyIPs != null && !proxyIPs.isEmpty()) {
      PROXIES = proxyIPs.split(",");
    }
  }

  public static XExchange createXExchange(final String exchange) {
    if (exchange == null) {
      LOGGER.info(Constants.LOG_FMT_2, "Invalid exchange: ", exchange);
      return null;
    }
    final String exchangeUpper = exchange.toUpperCase();

    ExchangeSpecification specification = null;
    switch (exchangeUpper) {
      case "BINANCE": {
        specification = new BinanceExchange().getDefaultExchangeSpecification();
        processSpecification(specification, null);

        return new XBinanceExchange(ExchangeFactory.INSTANCE.createExchange(specification));
      }
      case "BYBIT": {
        specification = new BybitExchange().getDefaultExchangeSpecification();
        processSpecification(specification, null);

        return new XBybitExchange(ExchangeFactory.INSTANCE.createExchange(specification));
      }
      case "MEXC": {
        specification = new MEXCExchange().getDefaultExchangeSpecification();
        processSpecification(specification, null);

        return new XMEXCExchange(ExchangeFactory.INSTANCE.createExchange(specification));
      }
/*      case "COINBASE": {
        specification = new CoinbaseProExchange().getDefaultExchangeSpecification();
        processSpecification(specification, null);

        return new XCoinbaseExchange(ExchangeFactory.INSTANCE.createExchange(specification));
      }*/
    }

    LOGGER.info(Constants.LOG_FMT_2, "Failed to load exchange: ", exchangeUpper);
    return null;
  }

  public static XExchange createXExchange(final InfluencerSubscription subscription) {
    if (subscription.getExchange() == null) {
      LOGGER.info(Constants.LOG_FMT_2, "Invalid exchange: ", subscription.getExchange());
      return null;
    }
    try {
      final String exchange = subscription.getExchange().toUpperCase();
      ExchangeSpecification specification = null;
      switch (exchange) {
        case "BINANCE": {
          specification = new BinanceExchange().getDefaultExchangeSpecification();
          processSpecification(specification, subscription);
          if (!"PRODUCTION".equalsIgnoreCase(Context.getEnvironment())) {
            if (subscription.isFuturesEnabled()) {
              specification.setExchangeSpecificParametersItem(BinanceExchange.SPECIFIC_PARAM_USE_FUTURES_SANDBOX, true);
            }
          }
          if (subscription.hasLeverage()) {
            specification.setExchangeSpecificParametersItem(BinanceExchange.SPECIFIC_PARAM_PORTFOLIO_MARGIN_ENABLED, true);
          }
          if (subscription.isFuturesEnabled()) {
            specification.setExchangeSpecificParametersItem(BinanceExchange.SPECIFIC_PARAM_FUTURES_ENABLED, true);
          }

          return new XBinanceExchange(createExchange(specification));
        }
        case "BYBIT": {
          specification = new BybitExchange().getDefaultExchangeSpecification();
          processSpecification(specification, subscription);

          if (subscription.isFuturesEnabled()) {
            specification.setExchangeSpecificParametersItem(BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE, BybitAccountType.CONTRACT);
          }

          return new XBybitExchange(ExchangeFactory.INSTANCE.createExchange(specification));
        }
        case "MEXC": {
          specification = new MEXCExchange().getDefaultExchangeSpecification();
          processSpecification(specification, subscription);

          if (subscription.isFuturesEnabled()) {
            specification.setExchangeSpecificParametersItem(MEXCExchange.SPECIFIC_PARAM_FUTURES_ENABLED, true);
          }

          return new XMEXCExchange(ExchangeFactory.INSTANCE.createExchange(specification));
        }
/*        case "COINBASE": {
          specification = new CoinbaseProExchange().getDefaultExchangeSpecification();
          processSpecification(specification, subscription);
          if (!"PRODUCTION".equalsIgnoreCase(Context.getEnvironment())) {
            if (subscription.isFuturesEnabled()) {
              //specification.setExchangeSpecificParametersItem(BinanceExchange.SPECIFIC_PARAM_USE_FUTURES_SANDBOX, true);
            }
          }
          if (subscription.hasLeverage()) {
            //specification.setExchangeSpecificParametersItem(BinanceExchange.SPECIFIC_PARAM_PORTFOLIO_MARGIN_ENABLED, true);
          }
          if (subscription.isFuturesEnabled()) {
            //specification.setExchangeSpecificParametersItem(BinanceExchange.SPECIFIC_PARAM_FUTURES_ENABLED, true);
          }

          return new XCoinbaseExchange(ExchangeFactory.INSTANCE.createExchange(specification));
        }*/
      }
    } catch (Exception e) {
      LOGGER.info(Constants.LOG_FMT_2, "Failed to load exchange: ", subscription.getExchange());
    }

    return null;
  }

  public static OrderQueryParams createOrderQueryParams(final CopyTrade copyTrade, final Instrument instrument) {
    switch (copyTrade.getExchange().toUpperCase()) {
      case "BINANCE": {
        return new BinanceQueryOrderParams(instrument, copyTrade.getExternalId());
      }
      case "BYBIT": {
        String category = "spot";
        if (copyTrade.isFuturesEnabled()) {
          category = "linear";
        }
        return new ByBitQueryOrderParams(category, copyTrade.getExternalId());
      }
      case "MEXC": {
        return new MEXCQueryOrderParams(instrument, copyTrade.getExternalId());
      }
      default:
        return new DefaultQueryOrderParam(copyTrade.getExternalId());
    }
  }

  private static void processSpecification(final ExchangeSpecification specification, final InfluencerSubscription subscription) {
    if (!"PRODUCTION".equalsIgnoreCase(Context.getEnvironment())) {
      specification.setExchangeSpecificParametersItem("Use_Sandbox", true);
    }

    if (subscription != null) {
      specification.setUserName(subscription.getApiUser());
      specification.setApiKey(subscription.getApiKey());
      specification.setSecretKey(subscription.getApiSecret());

      if (PROXIES != null ) {
        if (subscription.getLastUsedProxy() == null) {
          subscription.setLastUsedProxy(getStickyProxy(subscription.getId()));
          //to persist lastUsedProxy
          matcherToPublisherQueue.addGuaranteed(subscription);
        }
        specification.setProxyHost(subscription.getLastUsedProxy());
        specification.setProxyPort(8888);
      }
    } else {
      if (PROXIES != null) {// always go through a proxy if exists
        specification.setProxyHost(getStickyProxy(System.currentTimeMillis()));
        specification.setProxyPort(8888);
      }
    }
  }

  private static String getStickyProxy(final long subscriptionId) {
    if (PROXIES != null) {
      int index = (int) (subscriptionId % PROXIES.length);
      return PROXIES[index];
    }
    return null;
  }

  private static Exchange createExchange(final ExchangeSpecification specification) {
    Exchange exchange = null;
    int randomIndex = RANDOM.nextInt(PROXIES.length);
    int count = 0;
    String proxyServer = specification.getProxyHost();
    while (exchange == null && count < PROXIES.length) {
      try {
        count++;
        exchange = ExchangeFactory.INSTANCE.createExchange(specification);
      } catch (Exception e) {
        randomIndex++;
        String proxy = PROXIES[randomIndex % PROXIES.length];
        if (proxy.equalsIgnoreCase(proxyServer)) {// if random proxy is the same proxy try next one
          randomIndex++;
          proxy = PROXIES[randomIndex % PROXIES.length];
        }
        proxyServer = proxy;
        specification.setProxyHost(proxyServer);
      }
    }
    return exchange;
  }

}
