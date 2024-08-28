import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.matchengine.copytrade.xchangewrappers.XBinanceExchange;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import com.solfini.util.HttpUtils;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.knowm.xchange.binance.dto.ExchangeType.FUTURES;
import static org.knowm.xchange.binance.dto.ExchangeType.SPOT;

public class Test {
  private static ObjectMapper mapper = new ObjectMapper();
  public static void main(String[] args) throws IOException {
    List<XExchange.SymbolStatus> symbolStatuses = getExchangeInstrumentsFull();
    System.out.println("One");
    ExchangeSpecification specification = new BinanceExchange().getDefaultExchangeSpecification();
    //specification.setUserName();
    //specification.setApiKey();
    //specification.setSecretKey();
    specification.setExchangeSpecificParametersItem(BinanceExchange.EXCHANGE_TYPE, SPOT);
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(specification);

    AccountService accountService = exchange.getAccountService();
    //accountService.getAccountInfo();
    MarketDataService marketDataService = exchange.getMarketDataService();
    org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("BTC", "USDT"), false);
    Ticker ticker = marketDataService.getTicker(instrument);

    System.out.println("Done");
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

  public static List<XExchange.SymbolStatus> getExchangeInstrumentsFull() {
    List<XExchange.SymbolStatus> symbolStatuses = new ArrayList<>();
    final String apiUrl = new BinanceExchange().getDefaultExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/api/v3/exchangeInfo", new HashMap<>());
    if (response != null && response.getCode() == 200) {
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
    }
    response = HttpUtils.get(BinanceExchange.FUTURES_URL + "/fapi/v1/exchangeInfo", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final XBinanceExchange.BinanceExchangeInfoFull info = mapper.readValue(response.getData(), XBinanceExchange.BinanceExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          for (XBinanceExchange.BinanceSymbol binanceSymbol : info.getSymbols()){
            final XExchange.SymbolStatus symbolStatus = new XExchange.SymbolStatus();
            symbolStatus.setExchange("binance");
            symbolStatus.setBase(binanceSymbol.getBaseAsset());
            symbolStatus.setQuote(binanceSymbol.getQuoteAsset());
            symbolStatus.setPrompt(binanceSymbol.getContractType());
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
}
