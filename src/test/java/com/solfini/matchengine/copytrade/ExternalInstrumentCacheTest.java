package com.solfini.matchengine.copytrade;

import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.instrument.Instrument;

import java.util.Date;
import java.util.List;

public class ExternalInstrumentCacheTest {
  public final static String[] EXCHANGES = {
      "MEXC",
      //"BYBIT"
  };
  public static void main(String[] args) {
/*    InfluencerSubscription subscription = new InfluencerSubscription();
    subscription.setExchange("MEXC");
    subscription.setApiKey("mx0vglEiMdG2Rab34T");
    subscription.setApiSecret("32dd98b573f3480c975df712c733f187");
    subscription.setPreferredQuoteCurrency("usdt");
    subscription.setFuturesEnabled(true);

    final XExchange xExchange = ExternalExchangeUtil.createXExchange(subscription);
    Balance balance = getBalanceFromExchange(subscription, xExchange);
    System.out.println(balance);*/
    System.out.println(new Date(1722844193711L));
    loadFromExchange();
  }

  public static void loadFromExchange() {
    for (String exchangeCode : EXCHANGES) {
      try {
        final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly(exchangeCode);
        if (exchange == null) {
          continue;
        }

        final List<Instrument> instruments = exchange.getExchangeInstruments();
        for (final Instrument instrument : instruments) {
          if (instrument instanceof FuturesContract) {
            System.out.println("EXCHANGE: " + exchangeCode + " FUTURE " + instrument.getBase().getCurrencyCode() + "-" + instrument.getCounter()
                .getCurrencyCode() + ((FuturesContract) instrument).getPrompt());
          } else {
            System.out.println("EXCHANGE: " + exchangeCode + " " + instrument.getBase().getCurrencyCode() + "-" + instrument.getCounter()
                .getCurrencyCode());
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        System.err.println("FAILED to load exchange: " + exchangeCode);
      }
    }
  }


/*  private static Balance getBalanceFromExchange(final InfluencerSubscription subscription, final XExchange xExchange) {
    return new Balance(xExchange.getBalanceFromExchange(subscription.getPreferredQuoteCurrency()), System.currentTimeMillis());
  }*/

  private static class Balance {
    private double balance;
    private long lastUpdated;

    public Balance() {
    }

    public Balance(double balance, long lastUpdated) {
      this.balance = balance;
      this.lastUpdated = lastUpdated;
    }

    public double getBalance() {
      return balance;
    }

    public void setBalance(double balance) {
      this.balance = balance;
    }

    public long getLastUpdated() {
      return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
      this.lastUpdated = lastUpdated;
    }
  }
}
