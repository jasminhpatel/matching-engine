package rnd;

import com.solfini.common.Context;
import com.solfini.matchengine.executionexchange.xchangewrappers.XBinanceExchange;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.solfini.common.Constants.*;
import static org.knowm.xchange.Exchange.USE_SANDBOX;
import static org.knowm.xchange.binance.dto.ExchangeType.FUTURES;
import static org.knowm.xchange.binance.dto.ExchangeType.PORTFOLIO_MARGIN;

public class BinanceTest {
  private static String[] PROXIES = {"38.242.225.103","83.171.249.86","45.8.133.149"};
  private static Random RANDOM = new Random();

  public static void main(String[] args) {
    boolean isFutures = false;
    ExchangeSubscription subscription = new ExchangeSubscription();
    subscription.setId(1);
    subscription.setUserId(2);
    subscription.setPlatform("TWITTER");
    subscription.setAccountIds(new String[] {"wrohanc"});
    subscription.setExchange("BINANCE");
    subscription.setPercentage(100);
    subscription.setMaxAmount(1000);
    subscription.setStatus(0);
    subscription.setExpires(System.currentTimeMillis() + 10000000);
    subscription.setPreferredQuoteCurrency("USDC");
    subscription.setApiKey("064c3e04e2200c4e1774531a0eaaa315d9a887e93bd1254b81a070d525ff2445");
    subscription.setApiSecret("cb22609e7a8047e3b54a47581f8556966f8e153c276f599b52da7141b9840175");
    subscription.setFuturesEnabled(isFutures);

    XExchange xExchange = createXExchange(subscription);
    //XExchange.Balance balance = getBalanceFromExchange(xExchange.getExchange(), "ETH");
    //System.out.println(balance.toJson());
    XExchange.Balance balance1 = getStableCoinBalanceFromExchange(xExchange.getExchange());
    System.out.println(balance1.toJson());
    //org.knowm.xchange.instrument.Instrument instrument = getInstrument(xExchange.getExchange(), new CurrencyPair("ETH", "USDT"), isFutures);

  }

  public static XExchange.Balance getStableCoinBalanceFromExchange(final Exchange exchange) {
    final XExchange.Balance balance = new XExchange.Balance();
    balance.setLastUpdated(System.currentTimeMillis());
    try {
      final AccountService accountService = exchange.getAccountService();
      if (accountService == null)
        return balance;
      final Wallet wallet = accountService.getAccountInfo().getWallet();
      if (wallet == null)
        return balance;
      final Map<Currency, org.knowm.xchange.dto.account.Balance> balances = wallet.getBalances();
      if (balances == null)
        return balance;

      org.knowm.xchange.dto.account.Balance usdBalance = balances.get(Currency.getInstance(USD));
      org.knowm.xchange.dto.account.Balance usdcBalance = balances.get(Currency.getInstance(USDC));
      org.knowm.xchange.dto.account.Balance usdtBalance = balances.get(Currency.getInstance(USDT));
      if (usdBalance != null) {
        balance.setUsdBalance(MbxMath.roundToBestPrecision(usdBalance.getAvailable().doubleValue()));
      }
      if (usdcBalance != null) {
        balance.setUsdcBalance(MbxMath.roundToBestPrecision(usdcBalance.getAvailable().doubleValue()));
      }
      if (usdtBalance != null) {
        balance.setUsdtBalance(MbxMath.roundToBestPrecision(usdtBalance.getAvailable().doubleValue()));
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return balance;
  }


  public static org.knowm.xchange.instrument.Instrument getInstrument(final Exchange exchange, final CurrencyPair currencyPair, boolean isFuture) {
    try {
      final List<Instrument> instruments = exchange.getExchangeInstruments();
      org.knowm.xchange.instrument.Instrument instrument = null;
      for (org.knowm.xchange.instrument.Instrument i : instruments) {
        if (i.getBase().getSymbol().equalsIgnoreCase(currencyPair.getBase().getSymbol()) && i.getCounter().getSymbol()
            .equalsIgnoreCase(currencyPair.getCounter().getSymbol())) {
          if (isFuture && i instanceof FuturesContract && "PERP".equalsIgnoreCase(((FuturesContract) i).getPrompt())) {
            instrument = i;
            break;
          } else if (!isFuture && !(i instanceof FuturesContract)) {
            instrument = i;
            break;
          }
        }
      }
      return instrument;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public static XExchange createXExchange(final ExchangeSubscription subscription) {
    if (subscription.getExchange() == null) {
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
              specification.setExchangeSpecificParametersItem(USE_SANDBOX, true);
            }
          }
          if (subscription.hasLeverage()) {
            specification.setExchangeSpecificParametersItem(BinanceExchange.EXCHANGE_TYPE, PORTFOLIO_MARGIN);
          }
          if (subscription.isFuturesEnabled()) {
            specification.setExchangeSpecificParametersItem(BinanceExchange.EXCHANGE_TYPE, FUTURES);
          }

          return new XBinanceExchange(createExchange(specification));
        }
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

  private static Exchange createExchange(final ExchangeSpecification specification) {
    Exchange exchange = null;
    int randomIndex = RANDOM.nextInt(PROXIES.length);
    int count = 0;
    String proxyServer = specification.getProxyHost();
    while (exchange == null && count < PROXIES.length) {
      try {
        //System.out.println("Proxy: " + specification.getProxyHost());
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

  public static XExchange.Balance getBalanceFromExchange(final Exchange exchange, final String symbol) {
    final XExchange.Balance balance = new XExchange.Balance();
    balance.setLastUpdated(System.currentTimeMillis());
    try {
      final AccountService accountService = exchange.getAccountService();
      if (accountService == null)
        return balance;
      final Wallet wallet = accountService.getAccountInfo().getWallet();
      if (wallet == null)
        return balance;
      final Map<Currency, Balance> balances = wallet.getBalances();
      if (balances == null)
        return balance;

      final Currency currency = Currency.getInstance(symbol);
      if (currency == null) {
        return balance;
      }

      org.knowm.xchange.dto.account.Balance bal = balances.get(currency);

      if (bal != null) {
        balance.setCoinBalance(MbxMath.roundToBestPrecision(bal.getAvailable().doubleValue()));
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return balance;
  }
}
