import com.solfini.matchengine.copytrade.ExternalExchangeUtil;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.solfini.common.Constants.*;
import static org.knowm.xchange.Exchange.USE_SANDBOX;
import static org.knowm.xchange.okex.OkexExchange.PARAM_PASSPHRASE;
import static org.knowm.xchange.okex.OkexExchange.PARAM_SIMULATED;

public class OKXTest {
  public static void main(String[] args) throws IOException {
    boolean isFutures = false;
    ExchangeSpecification specification = new OkexExchange().getDefaultExchangeSpecification();
    //prod
    specification.setApiKey("4e0b287f-3517-4ee4-b1b4-d0eac3187446");
    specification.setSecretKey("A41A7077DB5A7807DB8A745168DCD642");
    //prod
    specification.setApiKey("478a613d-7542-4363-b467-0a2963686291");
    specification.setSecretKey("4EE27263C14F7478B669D92EC48FF9F0");

    //specification.setPassword();
    specification.setExchangeSpecificParametersItem(PARAM_PASSPHRASE, "Password@77");
    specification.setExchangeSpecificParametersItem(USE_SANDBOX, true);
    specification.setExchangeSpecificParametersItem(PARAM_SIMULATED, "1");

    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(specification);
    XExchange.Balance balance = getBalanceFromExchange(exchange, "ETH");
    System.out.println(balance.toJson());
    XExchange.Balance stableCoinBalance = getStableCoinBalanceFromExchange(exchange);
    System.out.println(stableCoinBalance.toJson());

    org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("ETH", "USDT"), isFutures);
    System.out.println("Instrument: " + instrument);
    System.out.println(getPriceFromExchange(exchange, instrument, Side.BUY));
    System.out.println(getPriceFromExchange(exchange, instrument, Side.SELL));

    String ret = sendOrder(instrument, exchange, "3518.0", isFutures);
    getOrder(exchange, ret);
    System.out.println("Done");
  }

  public static XExchange.Balance getBalanceFromExchange(final Exchange exchange, final String symbol) {
    final boolean futures = false;
    final XExchange.Balance balance = new XExchange.Balance();
    balance.setLastUpdated(System.currentTimeMillis());
    try {
      final AccountService accountService = exchange.getAccountService();
      if (accountService == null)
        return balance;
      if (accountService.getAccountInfo().getWallets() == null) {
        return balance;
      }
      Wallet wallet = null;
      if (futures) {
        wallet = accountService.getAccountInfo().getWallets().get("futures");
      } else {
        wallet = accountService.getAccountInfo().getWallets().get("trading");
      }
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

  public static XExchange.Balance getStableCoinBalanceFromExchange(final Exchange exchange) {
    final boolean futures = false;
    final XExchange.Balance balance = new XExchange.Balance();
    balance.setLastUpdated(System.currentTimeMillis());
    try {
      final AccountService accountService = exchange.getAccountService();
      if (accountService == null)
        return balance;
      if (accountService.getAccountInfo().getWallets() == null) {
        return balance;
      }
      Wallet wallet = null;
      if (futures) {
        wallet = accountService.getAccountInfo().getWallets().get("futures");
      } else {
        wallet = accountService.getAccountInfo().getWallets().get("trading");
      }
      if (wallet == null)
        return balance;
      final Map<Currency, org.knowm.xchange.dto.account.Balance> balances = wallet.getBalances();
      if (balances == null)
        return balance;

      org.knowm.xchange.dto.account.Balance usdBalance = balances.get(Currency.getInstance(USD));
      org.knowm.xchange.dto.account.Balance usdcBalance = balances.get(Currency.getInstance(USDC));
      org.knowm.xchange.dto.account.Balance usdtBalance = balances.get(Currency.getInstance(USDT));
      if (usdBalance != null && usdBalance.hasAvailable()) {
        balance.setUsdBalance(MbxMath.roundToBestPrecision(usdBalance.getAvailable().doubleValue()));
      }
      if (usdcBalance != null && usdcBalance.hasAvailable()) {
        balance.setUsdcBalance(MbxMath.roundToBestPrecision(usdcBalance.getAvailable().doubleValue()));
      }
      if (usdtBalance != null && usdtBalance.hasAvailable()) {
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

          if (isFuture && i instanceof FuturesContract && "SWAP".equalsIgnoreCase(((FuturesContract) i).getPrompt())) {
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

  public static String sendOrder(org.knowm.xchange.instrument.Instrument instrument, Exchange exchange, String limitPrice, boolean isFutures) throws IOException {
    Side side = Side.BUY;
    final TradeService tradeService = exchange.getTradeService();
    //final org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("ETH", "USDT"), isFutures);
    final Order.OrderType xOrderType = Side.BUY == side ? Order.OrderType.BID : Order.OrderType.ASK;
    final BigDecimal quantity = new BigDecimal("0.01");
    final BigDecimal price = new BigDecimal(limitPrice);

    final LimitOrder
        order = new LimitOrder(xOrderType, quantity, instrument, "ABC124", null, price);
    final String returnValue = tradeService.placeLimitOrder(order);

    System.out.println("Return value: " + returnValue);
    return returnValue;
  }

  public static void getOrder(Exchange exchange, String reference) {
    final boolean futures = false;
    final TradeService tradeService = exchange.getTradeService();
    final org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("ETH", "USDT"), futures);
    int retryCount = 0;
    while (retryCount < 5) {
      System.out.println("retryCount: " + retryCount);
      retryCount++;
      try {
        final OrderQueryParams orderQueryParams =
            ExternalExchangeUtil.createOrderQueryParams("MEXC", instrument, reference,
                true);
        final Collection<Order> orders = tradeService.getOrder(orderQueryParams);
        if (orders != null && !orders.isEmpty()) {
          Order summary = orders.iterator().next();

          if (summary.getAveragePrice() != null)
            System.out.println("Price: " + MbxMath.changeScale(summary.getAveragePrice().doubleValue(), 2));
          System.out.println(summary.getOriginalAmount().doubleValue());
          System.out.println(summary.getCumulativeAmount().doubleValue());
          System.out.println(summary.getStatus().name());
          System.out.println(summary.getId());
          //System.out.println(summary.g());
          if ("FILLED".equalsIgnoreCase(summary.getStatus().name())) {
            System.out.println(summary.getCumulativeAmount().doubleValue() * summary.getAveragePrice().doubleValue());
            break;
          }
        }
        Thread.sleep(1000);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static double getPriceFromExchange(Exchange exchange, final Instrument currencyPair, final Side side) {
    try {
      final MarketDataService marketDataService = exchange.getMarketDataService();
      if (marketDataService == null)
        return 0D;
      final Ticker ticker = marketDataService.getTicker(currencyPair);//cast because of deprecated method
      if (ticker == null)
        return 0D;
      double price = 0;
      if (side == Side.BUY) {
        if (ticker.getAsk() != null)
          price = ticker.getAsk().doubleValue();
      } else {
        if (ticker.getBid() != null)
          price = ticker.getBid().doubleValue();
      }
      if (price == 0) {
        if (ticker.getLast() != null)
          price = ticker.getLast().doubleValue();
      }
      if (price == 0) {
        if (ticker.getOpen() != null)
          price = ticker.getOpen().doubleValue();
      }
      return MbxMath.roundToBestPrecision(price);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return 0D;
  }

}
