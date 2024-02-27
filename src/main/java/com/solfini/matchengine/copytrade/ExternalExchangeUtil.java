package com.solfini.matchengine.copytrade;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.copytrade.xchangewrappers.XBinanceExchange;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.dto.trade.BinanceQueryOrderParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParam;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.util.Random;

public class ExternalExchangeUtil {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalExchangeUtil.class);

  public final static String[] EXCHANGES = {
      "BINANCE", "BITFINEX", "BITFLYER", "BITHUMB", "BITMEX",
      "BITSTAMP", "BYBIT", "COINBASE", "GATEIO", "GEMINI",
      "KRAKEN", "KUCOIN", "MEXC", "OKEX", "UPBIT",
  };
  private static String[] PROXIES = null;
  private static final Random RANDOM = new Random();

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
        processSpecification(specification, null, exchangeUpper);

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
    final String exchange = subscription.getExchange().toUpperCase();
    ExchangeSpecification specification = null;
    switch (exchange) {
      case "BINANCE": {
        specification = new BinanceExchange().getDefaultExchangeSpecification();
        processSpecification(specification, subscription, exchange);

        return new XBinanceExchange(ExchangeFactory.INSTANCE.createExchange(specification));
      }
    }

    LOGGER.info(Constants.LOG_FMT_2, "Failed to load exchange: ", exchange);
    return null;
  }

  public static OrderQueryParams createOrderQueryParams(final CopyTrade copyTrade, final CurrencyPair currencyPair) {
    switch (copyTrade.getExchange().toUpperCase()) {
      case "BINANCE": {
        return new BinanceQueryOrderParams(currencyPair, copyTrade.getExternalId());
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

  private static void processSpecification(final ExchangeSpecification specification, final InfluencerSubscription subscription, final String exchange) {
    if (!"PRODUCTION".equalsIgnoreCase(Context.getEnvironment())) {
      specification.setExchangeSpecificParametersItem("Use_Sandbox", true);
    }
    if (subscription != null) {
      specification.setUserName(subscription.getApiUser());
      specification.setApiKey(subscription.getApiKey());
      specification.setSecretKey(subscription.getApiSecret());
    }

    if (PROXIES != null) {
      specification.setSslUri("https://" + getRandomProxy() + "/" + exchange);
    }
  }

  private static String getRandomProxy() {
    if (PROXIES != null) {
      return PROXIES[RANDOM.nextInt(PROXIES.length)];
    }
    return null;
  }

}
