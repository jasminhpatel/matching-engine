package rnd;

import com.solfini.common.Context;
import com.solfini.matchengine.executionexchange.xchangewrappers.XCoinbaseExchange;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitExchange;
//import org.knowm.xchange.coinbasepro.CoinbaseProExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.MarketOrder;

import java.io.IOException;
import java.math.BigDecimal;

public class CoinbaseTest {
  public static void main(String[] args) throws IOException {
    Order.OrderType xOrderType = Order.OrderType.BID;
    BigDecimal xQuantity = new BigDecimal("0.01");
/*    Instrument instrument = new Instrument() {
      @Override
      public Currency getBase() {
        return Currency.BTC;
      }

      @Override
      public Currency getCounter() {
        return Currency.USD;
      }
    };*/
    CurrencyPair instrument = new CurrencyPair("BTC", "USD");
    String clOrdId = String.valueOf(System.currentTimeMillis());
    final MarketOrder
        order = new MarketOrder(xOrderType, xQuantity, instrument, clOrdId, null);
    ExchangeSubscription subscription = new ExchangeSubscription();
    subscription.setId(1);
    subscription.setUserId(2);
    subscription.setPlatform("YOUTUBE");
    subscription.setAccountIds(new String[] {"wrohanc"});
    subscription.setExchange("COINBASE");
    subscription.setPercentage(100);
    subscription.setMaxAmount(1000);
    subscription.setStatus(0);
    subscription.setExpires(System.currentTimeMillis() + 10000000);
    subscription.setPreferredQuoteCurrency("USDC");
    subscription.setApiUser("7zanjvvc20a");
    subscription.setApiKey("6eecfe79eaa9047ead46308e077e63d0");
    subscription.setApiSecret("RmipJFludUw5bfBom35GSqc039KacRm+tOSJkhWd8WY7MZd8q2F8SNO13Ui5tH7p0KhwuRviK8M0iEan71tp1Q==");
    //subscription.setApiKey("RmipJFludUw5bfBom35GSqc039KacRm+tOSJkhWd8WY7MZd8q2F8SNO13Ui5tH7p0KhwuRviK8M0iEan71tp1Q==");


    XExchange xExchange = createXExchange(subscription);
    final String returnValue = xExchange.getTradeService().placeMarketOrder(order);
    System.out.println(returnValue);
  }

  public static XExchange createXExchange(final ExchangeSubscription subscription) {
    if (subscription.getExchange() == null) {
      return null;
    }
    try {
      final String exchange = subscription.getExchange().toUpperCase();
      ExchangeSpecification specification = null;
      switch (exchange) {
        case "BYBIT": {
          specification = new BybitExchange().getDefaultExchangeSpecification();
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
        }
/*        case "COINBASE": {
          specification = new CoinbaseProExchange().getDefaultExchangeSpecification();
          processSpecification(specification, subscription);
          specification.setExchangeSpecificParametersItem("passphrase", subscription.getApiUser());
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
      e.printStackTrace();
    }

    return null;
  }

  private static void processSpecification(final ExchangeSpecification specification, final ExchangeSubscription subscription) {
    specification.setExchangeSpecificParametersItem("Use_Sandbox", true);

    if (subscription != null) {
      specification.setUserName(subscription.getApiUser());
      specification.setApiKey(subscription.getApiKey());
      specification.setSecretKey(subscription.getApiSecret());

    }
  }
}
