package com.solfini.matchengine.copytrade;

import com.solfini.common.*;
import com.solfini.matchengine.copytrade.xchangewrappers.XBinanceExchange;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.dto.trade.BinanceQueryOrderParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;
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

          return new XBinanceExchange(ExchangeFactory.INSTANCE.createExchange(specification));
        }
      }
    } catch (Exception e) {
      LOGGER.info(Constants.LOG_FMT_2, "Failed to load exchange: ", subscription.getExchange());
    }

    return null;
  }

  public static OrderQueryParams createOrderQueryParams(final CopyTrade copyTrade, final Instrument instrument) {
    switch (copyTrade.getExchange().toUpperCase()) {
      case "BINANCE": {
        LOGGER.info("QUERY instrument: " + instrument.getBase().getSymbol() + "-" + instrument.getCounter().getSymbol() + " " + copyTrade.getExternalId());
        return new BinanceQueryOrderParams(instrument, copyTrade.getExternalId());
      }
      default:
        return new DefaultQueryOrderParam(copyTrade.getExternalId());
    }
  }

  /*private static XExchange getXExchange(final InfluenceSubscription subscription) {

    switch (exchange.toUpperCase()) {
      case "BINANCE": {
        specification = new BinanceExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/BINANCE");
        }
        processSpecification(specification, subscription);

        return new XBinanceExchange(ExchangeFactory.INSTANCE.createExchange(specification));
      }
      case "BITFINEX":
        specification = new BitfinexExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/BITFINEX");
        }
        break;
      case "BITFLYER":
        specification = new BitflyerExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/BITFLYER");
        }
        break;
      case "BITHUMB":
        specification = new BithumbExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/BITHUMB");
        }
        break;
      case "BITMEX":
        specification = new BitmexExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/BITMEX");
        }
        break;
      case "BITSTAMP":
        specification = new BitstampExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/BITSTAMP");
        }
        break;
      case "BYBIT":
        specification = new BybitExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/BYBIT");
        }
        break;
      case "COINBASE":
        specification = new CoinbaseExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/COINBASE");
        }
        break;
      case "GATEIO":
        specification = new GateioExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/GATEIO");
        }
        break;
      case "GEMINI":
        specification = new GeminiExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/GEMINI");
        }
        break;
      case "KRAKEN":
        specification = new KrakenExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/KRAKEN");
        }
        break;
      case "KUCOIN":
        specification = new KucoinExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/KUCOIN");
        }
        break;
      case "MEXC":
        specification = new MEXCExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/MEXC");
        }
        break;
      case "OKEX":
        specification = new OkexExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/OKEX");
        }
        break;
      case "UPBIT":
        specification = new UpbitExchange().getDefaultExchangeSpecification();
        if (PROXIES != null) {
          specification.setSslUri("https://" + getRandomProxy() + "/UPBIT");
        }
        break;
      default:
        return null;
    }



    return null;
  }*/

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

}
