import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.binance.BinanceRestClient.BinanceExchangeInfoFull;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.binance.BinanceRestClient.BinanceSymbol;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.dto.trade.BinanceCancelOrderParams;
import org.knowm.xchange.binance.dto.trade.BinanceTradeHistoryParams;
import org.knowm.xchange.binance.dto.trade.TransferResponse;
import org.knowm.xchange.binance.service.BinanceAccountService;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static com.solfini.common.Constants.*;
import static org.knowm.xchange.binance.dto.ExchangeType.FUTURES;
import static org.knowm.xchange.binance.dto.ExchangeType.SPOT;

public class BinanceTest {
  private static ObjectMapper mapper = new ObjectMapper();

  public static void main(String[] args) throws IOException {
    //System.out.println(Math.log10(new BigDecimal("0.01000000").doubleValue()));
    boolean isFutures = false;
    //List<XExchange.SymbolStatus> symbolStatuses = getExchangeInstrumentsFull();
    //System.out.println("One");
    ExchangeSpecification specification = new BinanceExchange().getDefaultExchangeSpecification();
    //specification.setUserName();
    specification.setApiKey("V0o4NPeKU9DoV4ZKmVTUWVXMLycjMi3MLHFbGqvWY4ZkZHmLRQrC945oeBymAjLX");
    specification.setSecretKey("sRlLWo49i0lk0Tnm1BJa1vjJW4lzAZe47vKCaBidl1kNb6yqA2vfsIcdB28RI6in");
    specification.setExchangeSpecificParametersItem(BinanceExchange.EXCHANGE_TYPE, isFutures ? FUTURES : SPOT);
    specification.setProxyHost("185.229.119.238");
    specification.setProxyPort(8888);
    //specification.setExchangeSpecificParametersItem(USE_SANDBOX, true);
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(specification);

    //System.out.println(getStableCoinBalanceFromExchange(exchange).toJson());
    //AccountService accountService = exchange.getAccountService();
    //AccountInfo accountInfo = accountService.getAccountInfo();

    //org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("BTC", "USDT"), isFutures);
    //MarketDataService marketDataService = exchange.getMarketDataService();
    //Ticker ticker = marketDataService.getTicker(instrument);

    //getOrders(exchange, instrument);

    String ret = null;
    //ret = sendOrder(exchange, "103405.0", isFutures, Side.SELL);
    //ret = "30913166773";
    //ret = "30913383038";
    ret = "11838802";
    System.out.println(ret);
    getOrder(exchange, ret, isFutures);
    //getOrder(exchange, "410941678891");
    //getOrder(exchange, "410960650169");
    //getOrder(exchange, "411861921081");
    //cancelOrder(exchange, "410941678891");
    //System.out.println(getStableCoinBalanceFromExchange(exchange).toJson());
    //transferFunds(exchange);
    //System.out.println(getStableCoinBalanceFromExchange(exchange).toJson());
    System.out.println("Done");
  }

  public static XExchange.Balance getStableCoinBalanceFromExchange(Exchange exchange) {
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

      for (Map.Entry<Currency, Balance> b : balances.entrySet()) {
        if (b.getValue().getTotal().doubleValue() > 0) {
          System.out.println(b.getKey().getSymbol() + " - " + MbxMath.roundToBestPrecision(b.getValue().getAvailable().doubleValue()));
        }
      }
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
      final List<org.knowm.xchange.instrument.Instrument> instruments = exchange.getExchangeInstruments();
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

  public static List<ExternalSymbol> getExchangeInstrumentsFull() {
    List<ExternalSymbol> symbolStatuses = new ArrayList<>();
    final String apiUrl = new BinanceExchange().getDefaultExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/api/v3/exchangeInfo", new HashMap<>());
/*    if (response != null && response.getCode() == 200) {
      try {
        final XBinanceExchange.BinanceExchangeInfoFull info = mapper.readValue(response.getData(), XBinanceExchange.BinanceExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          for (XBinanceExchange.BinanceSymbol binanceSymbol : info.getSymbols()){
            final XExchange.SymbolStatus symbolStatus = new XExchange.SymbolStatus();
            symbolStatus.setExchange("binance");
            symbolStatus.setBase(binanceSymbol.getBaseAsset());
            symbolStatus.setQuote(binanceSymbol.getQuoteAsset());
            symbolStatus.setPrompt("");
            symbolStatus.setTradable("TRADING".equals(binanceSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            //symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(binanceSymbol.getQuotePrecision());
            symbolStatus.setQtyScale(binanceSymbol.getBaseAssetPrecision());
            symbolStatuses.add(symbolStatus);
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }*/
    response = HttpUtils.get(BinanceExchange.FUTURES_URL + "/fapi/v1/exchangeInfo", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final BinanceExchangeInfoFull info = mapper.readValue(response.getData(), BinanceExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          for (BinanceSymbol binanceSymbol : info.getSymbols()){
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("binance");
            symbolStatus.setBase(binanceSymbol.getBaseAsset());
            symbolStatus.setQuote(binanceSymbol.getQuoteAsset());
            symbolStatus.setPrompt(binanceSymbol.getPrompt());
            symbolStatus.setTradable(true);
            symbolStatus.setFutures(true);
            symbolStatus.setUpdated(updated);
            //symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(binanceSymbol.getQuotePrecision());
            symbolStatus.setQtyScale(binanceSymbol.getBaseAssetPrecision());
            symbolStatuses.add(symbolStatus);
          }
          return symbolStatuses;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  public static void getOrders(Exchange exchange, Instrument instrument) {
    final TradeService tradeService = exchange.getTradeService();
    int retryCount = 0;
    while (retryCount < 5) {
      retryCount++;
      try {
        //final OrderQueryParams orderQueryParams = new BinanceQueryOrderParams(instrument, null);
        //final Collection<Order> orders = tradeService.getOrder(orderQueryParams);
        //final TradeHistoryParams tradeHistoryParams = new BinanceTradeHistoryParams();
        final TradeHistoryParamInstrument tradeHistoryParamInstrument = new BinanceTradeHistoryParams(instrument);
        tradeHistoryParamInstrument.setInstrument(instrument);
        final UserTrades orders = tradeService.getTradeHistory(tradeHistoryParamInstrument);
        if (orders != null && !orders.getUserTrades().isEmpty()) {
          for (Trade summary : orders.getUserTrades()) {
            System.out.println(summary.toString());
          }
          return;
        }
        Thread.sleep(250);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static String sendOrder(Exchange exchange, String limitPrice, boolean isFutures, Side side) throws IOException {
    final TradeService tradeService = exchange.getTradeService();
    final org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("BTC", "USDT"), isFutures);
    final Order.OrderType xOrderType = Side.BUY == side ? Order.OrderType.BID : Order.OrderType.ASK;
    final BigDecimal quantity = new BigDecimal("0.001");
    final BigDecimal price = new BigDecimal(limitPrice);

    final LimitOrder
        order = new LimitOrder(xOrderType, quantity, instrument, "ABC124", null, price);
    final String returnValue = tradeService.placeLimitOrder(order);

    System.out.println("Return value: " + returnValue);
    return returnValue;
  }

  public static void getOrder(Exchange exchange, String reference, final boolean isFutures) {
    final TradeService tradeService = exchange.getTradeService();
    final org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("BTC", "USDT"), isFutures);
    int retryCount = 0;
    while (retryCount < 5) {
      retryCount++;
      try {
        final OrderQueryParams orderQueryParams =
            ExternalExchangeUtil.createOrderQueryParams("BINANCE", instrument, reference,
                isFutures);
        final Collection<Order> orders = tradeService.getOrder(orderQueryParams);
        if (orders != null && !orders.isEmpty()) {
          Order summary = orders.iterator().next();

          if (summary.getAveragePrice() != null)
            System.out.println("Price: " + MbxMath.changeScale(summary.getAveragePrice().doubleValue(), 2));
          System.out.println("OriginalAmount: " + summary.getOriginalAmount().doubleValue());
          System.out.println("CumulativeAmount: " + summary.getCumulativeAmount().doubleValue());
          System.out.println("Status: " + summary.getStatus().name());
          System.out.println("Id: " + summary.getId());
          //System.out.println(summary.g());
          if ("FILLED".equalsIgnoreCase(summary.getStatus().name())) {
            System.out.println(summary.getCumulativeAmount().doubleValue() * summary.getAveragePrice().doubleValue());
            break;
          }
        }
        Thread.sleep(250);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static void cancelOrder(Exchange exchange, String reference) throws IOException {
    final TradeService tradeService = exchange.getTradeService();
    final org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("BTC", "USDT"), true);

    CancelOrderByIdParams cancelOrderByIdParams = new BinanceCancelOrderParams(instrument, reference);
    tradeService.cancelOrder(cancelOrderByIdParams);
  }

  public XExchange.Balance getBalanceFromExchange(Exchange exchange, String symbol) {
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

  public static void transferFunds(Exchange exchange) throws IOException {
    final BinanceAccountService accountService = (BinanceAccountService) exchange.getAccountService();
    TransferResponse response = accountService.transferFunds("USDT", new BigDecimal("1"), 2);
    System.out.println("Done");
  }
}
