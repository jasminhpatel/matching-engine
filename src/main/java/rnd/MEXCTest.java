package rnd;

import com.solfini.matchengine.copytrade.ExternalExchangeHandler;
import com.solfini.matchengine.copytrade.InfluencerSubscription;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import com.solfini.matchengine.copytrade.xchangewrappers.XMEXCExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.mexc.MEXCExchange;
import org.knowm.xchange.mexc.dto.trade.MEXCQueryOrderParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class MEXCTest {
  private static String[] PROXIES = {"38.242.225.103","83.171.249.86","45.8.133.149"};
  private static Random RANDOM = new Random();

  public static void main(String[] args) throws IOException {
    Order.OrderType xOrderType = Order.OrderType.BID;
    BigDecimal xQuantity = new BigDecimal("0.01");

    CurrencyPair currencyPair = new CurrencyPair("BTC", "USDT");
    //FuturesContract pair = new FuturesContract(currencyPair, "PERP");
    String clOrdId = String.valueOf(System.currentTimeMillis());
    final MarketOrder
        marketOrder = new MarketOrder(xOrderType, xQuantity, currencyPair, clOrdId, null);

    InfluencerSubscription subscription = new InfluencerSubscription();
    subscription.setId(1);
    subscription.setUserId(2);
    subscription.setPlatform("YOUTUBE");
    subscription.setAccountId("wrohanc");
    subscription.setExchange("MEXC");
    subscription.setPercentage(100);
    subscription.setMaxAmount(1000);
    subscription.setStatus(0);
    subscription.setExpires(System.currentTimeMillis() + 10000000);
    subscription.setPreferredQuoteCurrency("USDT");
    subscription.setApiKey("mx0vglI6QHowBDJj3y");
    subscription.setApiSecret("2a7d5e1ca73a472480f5189636e17772");
    subscription.setFuturesEnabled(true);

    XExchange xExchange = createXExchange(subscription);
    XExchange.Balance exchangeBalance = ExternalExchangeHandler.getBalance(subscription, xExchange, "USDC");
    System.out.println(exchangeBalance);

    org.knowm.xchange.instrument.Instrument instrument = getInstrument(xExchange, currencyPair, subscription.isFuturesEnabled());
    System.out.println(instrument);
    //final LimitOrder limitOrder = new LimitOrder(xOrderType, xQuantity, instrument, clOrdId, null, new BigDecimal("61425.00"));
/*    System.out.println(xExchange.getExchangeMetaData().toJSONString());
    for (InstrumentMetaData i : xExchange.getExchangeMetaData().getInstruments().values()) {
      System.out.println(i.toString());
    }*/
    //xExchange.getExchangeMetaData().getInstruments();
    //String returnValue = xExchange.getTradeService().placeLimitOrder(limitOrder);
    //System.out.println(returnValue);
    String returnValue = "1";

    final OrderQueryParams orderQueryParams = new MEXCQueryOrderParams(instrument, returnValue);
    final Collection<Order> orders = xExchange.getTradeService().getOrder(orderQueryParams);
    if (orders != null && !orders.isEmpty()) {
      Order summary = orders.iterator().next();
      if (summary.getAveragePrice() != null)
        System.out.println("AveragePrice: " + summary.getAveragePrice().doubleValue());

      System.out.println("OriginalAmount: " + summary.getOriginalAmount().doubleValue());
      System.out.println("CumulativeAmount: " + summary.getCumulativeAmount().doubleValue());
      System.out.println("Status: " + summary.getStatus());
    }
  }

  public static XExchange createXExchange(final InfluencerSubscription subscription) {
    if (subscription.getExchange() == null) {
      return null;
    }
    try {
      final String exchange = subscription.getExchange().toUpperCase();
      ExchangeSpecification specification = null;
      switch (exchange) {
        case "MEXC": {
          specification = new MEXCExchange().getDefaultExchangeSpecification();
          //specification.setProxyHost("38.242.225.103");
          //specification.setProxyPort(8888);
          processSpecification(specification, subscription);
/*          if (!"PRODUCTION".equalsIgnoreCase(Context.getEnvironment())) {
            if (subscription.isFuturesEnabled()) {
              //specification.setExchangeSpecificParametersItem(BinanceExchange.SPECIFIC_PARAM_USE_FUTURES_SANDBOX, true);
            }
          }*/
          if (subscription.hasLeverage()) {
            //specification.setExchangeSpecificParametersItem(BinanceExchange.SPECIFIC_PARAM_PORTFOLIO_MARGIN_ENABLED, true);
          }
          if (subscription.isFuturesEnabled()) {
            specification.setExchangeSpecificParametersItem(MEXCExchange.SPECIFIC_PARAM_FUTURES_ENABLED, true);
          }

          return new XMEXCExchange(createExchange(specification));
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return null;
  }

  private static void processSpecification(final ExchangeSpecification specification, final InfluencerSubscription subscription) {
    specification.setExchangeSpecificParametersItem("Use_Sandbox", true);

    if (subscription != null) {
      specification.setUserName(subscription.getApiUser());
      specification.setApiKey(subscription.getApiKey());
      specification.setSecretKey(subscription.getApiSecret());
    }
  }

  private static Exchange createExchange(final ExchangeSpecification specification) {
    Exchange exchange = null;
    int randomIndex = RANDOM.nextInt(PROXIES.length);
    int count = 0;
    String proxyServer = specification.getProxyHost();
    while (exchange == null && count < PROXIES.length) {
      try {
        System.out.println("Proxy: " + specification.getProxyHost());
        count++;
        exchange = ExchangeFactory.INSTANCE.createExchange(specification);
      } catch (Exception e) {
        randomIndex++;
        String proxy = PROXIES[randomIndex % PROXIES.length];
        System.out.println("Selected: " + proxy);
        if (proxy.equalsIgnoreCase(proxyServer)) {// if random proxy is the same proxy try next one
          randomIndex++;
          proxy = PROXIES[randomIndex % PROXIES.length];
          System.out.println("Skip Selected: " + proxy);
        }
        proxyServer = proxy;
        specification.setProxyHost(proxyServer);
      }
    }
    return exchange;
  }

  private static org.knowm.xchange.instrument.Instrument getInstrument(final XExchange xExchange, final CurrencyPair currencyPair, boolean isFuture) {
    try {
      final List<Instrument> instruments = xExchange.getExchange()
          .getExchangeInstruments();
      org.knowm.xchange.instrument.Instrument instrument = null;
      for (org.knowm.xchange.instrument.Instrument i : instruments) {
        if (i.getBase().getSymbol().equalsIgnoreCase(currencyPair.getBase().getSymbol()) && i.getCounter().getSymbol()
            .equalsIgnoreCase(currencyPair.getCounter().getSymbol())) {
          if (isFuture && i instanceof FuturesContract && "PERP".equalsIgnoreCase(((FuturesContract) i).getPrompt())) {
            instrument = i;
            break;
          } else if (!isFuture && !(i instanceof FuturesContract)) {
            instrument = i;
            System.out.println(i);
            break;
          }
        }
      }
      return instrument;
    } catch (Exception e) {
      System.out.println("Failed to load instrument. " + currencyPair.toString());
    }
    return null;
  }

}
