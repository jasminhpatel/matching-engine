package rnd;

import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.bitstamp.BitstampExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;

import java.io.IOException;
import java.util.List;
import java.util.Random;

public class Rnd {
    public static void main(String[] args) throws IOException {
        Random random = new Random();
        for(int i = 0; i < 100; i++) {
            System.out.println(random.nextInt(2));
        }
/*        long ordQty = 10000;
        short ordQtyScale = 4;
        double maxTradeValue = MbxMath.scaleDown(1000000, 2);;
        double marketPrice = 45249.97;
        double tradePercentage = MbxMath.scaleDown(ordQty, ordQtyScale)/100D;
        double tradeValue = tradePercentage * maxTradeValue;
        double quantity = MbxMath.roundToBestPrecision(tradeValue / marketPrice);

        System.out.println(quantity);*/


/*        Exchange bitstamp = ExchangeFactory.INSTANCE.createExchange(BitstampExchange.class);
        List<Instrument> instruments = bitstamp.getExchangeInstruments();
        for (Instrument instrument : instruments) {
            System.out.println(instrument.getBase().getSymbol() + "/" + instrument.getCounter().getSymbol());
        }*/
        //MarketDataService marketDataService = bitstamp.getMarketDataService();
        //Ticker ticker = marketDataService.getTicker(CurrencyPair.BTC_USD);
        //System.out.println(ticker.toString());
    }
}
