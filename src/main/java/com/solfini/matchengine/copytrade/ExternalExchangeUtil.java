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

import static org.knowm.xchange.Exchange.USE_SANDBOX;
import static org.knowm.xchange.binance.dto.ExchangeType.FUTURES;
import static org.knowm.xchange.binance.dto.ExchangeType.PORTFOLIO_MARGIN;

public class ExternalExchangeUtil {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ExternalExchangeUtil.class);
  private static final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();

  public final static String[] EXCHANGES = {
      "BINANCE", "BYBIT", "MEXC",
/*      "BINANCE", "BITFINEX", "BITFLYER", "BITHUMB", "BITMEX",
      "BITSTAMP", "BYBIT", "COINBASE", "GATEIO", "GEMINI",
      "KRAKEN", "KUCOIN", "MEXC", "OKEX", "UPBIT",*/
  };
  public final static String[] EXCHANGE_SLUGS = {
      "binance",
      "bybit",
      "mexc",
  };
  private static String[] PROXIES = null;
  private static Random RANDOM = new Random();

  static {
    final String proxyIPs = Context.getCopyTradeProxyIps();
    if (proxyIPs != null && !proxyIPs.isEmpty()) {
      PROXIES = proxyIPs.split(",");
    }
  }

  public static XExchange createXExchangeReadOnly(final String exchange) {
    if (exchange == null) {
      LOGGER.info(Constants.LOG_FMT_2, "Invalid exchange: ", exchange);
      return null;
    }
    final String exchangeUpper = exchange.toUpperCase();
    XExchange xExchange = null;
    ExchangeSpecification specification = null;
    int retryCount = 0;
    while (retryCount < 3) {
      retryCount ++;
      try {
        switch (exchangeUpper) {
          case "BINANCE": {
            specification = new BinanceExchange().getDefaultExchangeSpecification();
            processSpecification(specification, null, retryCount);

            xExchange = new XBinanceExchange(ExchangeFactory.INSTANCE.createExchange(specification));
            break;
          }
          case "BYBIT": {
            specification = new BybitExchange().getDefaultExchangeSpecification();
            //below two keys are only for internal validations done by the XChange library. not sent to exchange
            specification.setApiKey("fqWsXvp4L53VvTkdX4");
            specification.setSecretKey("G1wcCfRwkhPulF2KbkXkMMLksUcE1cE0y9GI");
            processSpecification(specification, null, retryCount);

            xExchange = new XBybitExchange(ExchangeFactory.INSTANCE.createExchange(specification));
            break;
          }
          case "MEXC": {
            specification = new MEXCExchange().getDefaultExchangeSpecification();
            //specification.setUserName();
            //below two keys are only for internal validations done by the XChange library. not sent to exchange
            specification.setApiKey("mx0vglEiMdG2Rab34T");
            specification.setSecretKey("32dd98b573f3480c975df712c733f187");
            processSpecification(specification, null, retryCount);

            xExchange = new XMEXCExchange(ExchangeFactory.INSTANCE.createExchange(specification));
            break;
          }
/*      case "COINBASE": {
        specification = new CoinbaseProExchange().getDefaultExchangeSpecification();
        processSpecification(specification, null);

        return new XCoinbaseExchange(ExchangeFactory.INSTANCE.createExchange(specification));
      }*/
        }
        if (xExchange != null) {
          return xExchange;
        } else {
          Thread.sleep(1000);
        }
      } catch (Exception e) {
        LOGGER.info(Constants.LOG_FMT_2, "Failed to load exchange: ", exchangeUpper, " attempt: ", retryCount);
        LOGGER.error(Constants.ERROR_LOG, e);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ex) {}
      }
    }
    return null;
  }

  public static XExchange createXExchange(final InfluencerSubscription subscription) {
    if (subscription.getExchange() == null) {
      LOGGER.info(Constants.LOG_FMT_2, "Invalid exchange: ", subscription.getExchange());
      return null;
    }
    final String exchange = subscription.getExchange().toUpperCase();
    XExchange xExchange = null;
    ExchangeSpecification specification = null;
    int retryCount = 0;
    while (retryCount < 3) {
      try {
        retryCount++;
        switch (exchange) {
          case "BINANCE": {
            specification = new BinanceExchange().getDefaultExchangeSpecification();
            processSpecification(specification, subscription, retryCount);
            if (!"PRODUCTION".equalsIgnoreCase(Context.getEnvironment())) {
              if (subscription.isFuturesEnabled()) {
                specification.setExchangeSpecificParametersItem(USE_SANDBOX, true);
              }
            }
            if (subscription.isFuturesEnabled()) {
              specification.setExchangeSpecificParametersItem(BinanceExchange.EXCHANGE_TYPE, FUTURES);
            } else if (subscription.hasLeverage()) {
              specification.setExchangeSpecificParametersItem(BinanceExchange.EXCHANGE_TYPE, PORTFOLIO_MARGIN);
            }

            xExchange = new XBinanceExchange(createExchange(specification));
            break;
          }
          case "BYBIT": {
            specification = new BybitExchange().getDefaultExchangeSpecification();
            processSpecification(specification, subscription, retryCount);

            if (subscription.isFuturesEnabled()) {
              specification.setExchangeSpecificParametersItem(BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE, BybitAccountType.CONTRACT);
            }

            xExchange = new XBybitExchange(ExchangeFactory.INSTANCE.createExchange(specification));
            break;
          }
          case "MEXC": {
            specification = new MEXCExchange().getDefaultExchangeSpecification();
            processSpecification(specification, subscription, retryCount);

            if (subscription.isFuturesEnabled()) {
              specification.setExchangeSpecificParametersItem(MEXCExchange.SPECIFIC_PARAM_FUTURES_ENABLED, true);
            }

            xExchange = new XMEXCExchange(ExchangeFactory.INSTANCE.createExchange(specification));
            break;
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
        if (xExchange != null) {
          return xExchange;
        } else {
          Thread.sleep(1000);
        }
      } catch (Exception e) {
        LOGGER.info(Constants.LOG_FMT_2, "Failed to load exchange: ", subscription.getExchange(), " attempt: ", retryCount);
        LOGGER.error(Constants.ERROR_LOG, e);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ex) {}
      }
    }

    return null;
  }

  public static OrderQueryParams createOrderQueryParams(final String exchange, final Instrument instrument, final String reference,
      final boolean futuresEnabled) {
    switch (exchange.toUpperCase()) {
      case "BINANCE": {
        return new BinanceQueryOrderParams(instrument, reference);
      }
      case "BYBIT": {
        String category = "spot";
        if (futuresEnabled) {
          category = "linear";
        }
        return new ByBitQueryOrderParams(category, reference);
      }
      case "MEXC": {
        return new MEXCQueryOrderParams(instrument, reference);
      }
      default:
        return new DefaultQueryOrderParam(reference);
    }
  }

  private static void processSpecification(final ExchangeSpecification specification, final InfluencerSubscription subscription,
      final int retryCount) {
    if (!"PRODUCTION".equalsIgnoreCase(Context.getEnvironment())) {
      specification.setExchangeSpecificParametersItem("Use_Sandbox", true);
    }

    if (subscription != null) {
      specification.setUserName(subscription.getApiUser());
      specification.setApiKey(subscription.getApiKey());
      specification.setSecretKey(subscription.getApiSecret());

      if (PROXIES != null ) {
        if (subscription.getLastUsedProxy() == null) {
          //assign a sticky proxy per user because an exchange account can be shared among multiple subscriptions by the same user
          subscription.setLastUsedProxy(getStickyProxy(subscription.getUserId()));
          //to persist lastUsedProxy
          matcherToPublisherQueue.addGuaranteed(subscription);
        }
        specification.setProxyHost(subscription.getLastUsedProxy());
        if (retryCount > 0) {//use a different proxy in the next attempt
          specification.setProxyHost(getRandomProxy(subscription));
        }
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

  private static String getRandomProxy(final InfluencerSubscription subscription) {
    if (PROXIES != null) {
      int randomIndex = RANDOM.nextInt(PROXIES.length + 1);
      String proxy = PROXIES[randomIndex % PROXIES.length];
      // if random proxy is the same proxy try next one
      if (proxy.equalsIgnoreCase(subscription.getLastUsedProxy())) {
        randomIndex = RANDOM.nextInt(randomIndex + 1);
        proxy = PROXIES[randomIndex % PROXIES.length];
      }
      return proxy;
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
