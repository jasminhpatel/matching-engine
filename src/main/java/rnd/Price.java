package rnd;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
//import org.knowm.xchange.bitstamp.BitstampExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NonceException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.CurrencyPairsParam;
import org.knowm.xchange.service.marketdata.params.Params;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;


public class Price {
  public static void main(String[] args) throws IOException {
    //Exchange bitstampExchange = ExchangeFactory.INSTANCE.createExchange(BitstampExchange.class.getName());

    // Interested in the public market data feed (no authentication)
    //MarketDataService marketDataService = bitstampExchange.getMarketDataService();

    Params params = new CurrencyPairsParam() {
      @Override
      public Collection<CurrencyPair> getCurrencyPairs() {
        return Arrays.asList(CurrencyPair.BTC_USD);
      }
    };

/*    List<Ticker> tickers = marketDataService.getTickers(params);
    for (Ticker ticker : tickers) {
      System.out.println(ticker.toString());
    }*/
  }

  public static Map<Instrument, InstrumentMetaData> getExchangeCurrencyPairs(String Classname)
  {
    Map<Instrument, InstrumentMetaData> currencyPairList = null;

    Exchange exch = null;

    try {
/*      exch = ExchangeFactory.INSTANCE.createExchange(BitstampExchange.class);
      currencyPairList = exch.getExchangeMetaData().getInstruments();*/
    }
    catch (NonceException e) {
      System.out.println("Nonce exception...");
    }
    catch (ExchangeException e) {
      System.out.println("Exchange exception...");
    }
    catch (Exception e) {
      System.out.println("ok we have an issue...");
    }
    return currencyPairList;
  }
}
