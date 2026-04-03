package com.solfini.matchengine.copytrade;

import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.sbe.encoder.Side;
import org.knowm.xchange.currency.CurrencyPair;

import java.util.Date;

public class ExternalPriceTest {
  public static void main(String[] args) {
    System.out.println(new Date(1723894573537L));
    System.out.println(System.currentTimeMillis());
    System.out.println(new Date(1738348199000L));

    final XExchange xExchange = ExternalExchangeUtil.createXExchangeReadOnly("MEXC");
    CurrencyPair instrument = CurrencyPair.BTC_USDT;
    System.out.println(xExchange.getPriceFromExchange(instrument, Side.BUY));
    System.out.println(xExchange.getPriceFromExchange(instrument, Side.SELL));
  }
}
