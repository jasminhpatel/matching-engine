import com.solfini.common.Constants;
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
import java.util.List;

import static org.knowm.xchange.binance.dto.ExchangeType.FUTURES;

public class Test {
  public static void main(String[] args) throws IOException {
    ExchangeSpecification specification = new BinanceExchange().getDefaultExchangeSpecification();
    //specification.setUserName();
    //specification.setApiKey();
    //specification.setSecretKey();
    specification.setExchangeSpecificParametersItem(BinanceExchange.EXCHANGE_TYPE, FUTURES);
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(specification);

    AccountService accountService = exchange.getAccountService();
    accountService.getAccountInfo();
    MarketDataService marketDataService = exchange.getMarketDataService();
    org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("BTC", "USDT"), true);
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
}
