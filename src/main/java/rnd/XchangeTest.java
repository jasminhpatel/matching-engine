package rnd;

import com.solfini.matchengine.copytrade.CopyTrade;
import com.solfini.matchengine.copytrade.ExternalExchangeUtil;
import com.solfini.matchengine.copytrade.InfluencerSubscription;
import com.solfini.matchengine.copytrade.xchangewrappers.XBinanceExchange;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceAdapters;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.dto.trade.BinanceQueryOrderParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.util.Collection;
import java.util.List;

public class XchangeTest {
  public static void main(String[] args) throws Exception {
    InfluencerSubscription subscription = new InfluencerSubscription();
    subscription.setExchange("BINANCE");
    subscription.setPreferredQuoteCurrency("USDT");

    subscription.setApiKey("1d773b4c6f1361614091fc5e9e48de1fd4e0b8dbd8a6c917844b69dec4def0f0");
    subscription.setApiSecret("4d9b0470fec2f70ecb58a13c417af5e5cd3011e68088cf37934e67f45b343b19");
    subscription.setFuturesEnabled(true);

/*    subscription.setApiKey("L65Hq6Fcc6LdROvk32nwjzzvC4fbWQpUx1WG8cFJSMXiV55jpTe7a117RJ57hKOc");
    subscription.setApiSecret("P4AgUEQtLEzKfGe31C8y9nzjYUunwgbtpMXuqjmajHhkVa5vm3gmJn7I35WVJVFA");
    subscription.setFuturesEnabled(false);*/

    String base = "BTC";
    String quoted = "USDT";
    Side side = Side.BUY;

    XExchange xExchange = createXExchange(subscription);
    List<Instrument> instruments = xExchange.getExchange().getExchangeInstruments();
    Instrument instrument = null;
    for (Instrument i : instruments) {
      if (i.getBase().getSymbol().equalsIgnoreCase(base) && i.getCounter().getSymbol().equalsIgnoreCase(quoted)) {
        instrument = i;
      }
    }
    InstrumentMetaData instrumentMetaData = xExchange.getExchangeMetaData().getInstruments().get(instrument);


    //double availableBalance = getBalanceFromExchange(subscription, xExchange).getBalance();
    //System.out.println("Available Balance: " + availableBalance);

    //double price = getPriceFromExchange(subscription, instrument, side, xExchange).getPrice();
    //System.out.println("Price: " + price);

    updateOrderStatus(xExchange, instrument, "3720691744");

  }

  public static void updateOrderStatus(final XExchange xExchange, final Instrument instrument, final String orderId) throws Exception {
    final TradeService tradeService = xExchange.getTradeService();
    final OrderQueryParams orderQueryParams = new BinanceQueryOrderParams(instrument, orderId);
    final Collection<Order> orders = tradeService.getOrder(orderQueryParams);
    if (orders != null && !orders.isEmpty()) {
      Order summary = orders.iterator().next();
      System.out.println(summary.getId());
      System.out.println(summary.getAveragePrice());
      System.out.println(summary.getLeverage());
      System.out.println(summary.getCumulativeAmount());
      System.out.println("Done");
    }
  }

  private static Price getPriceFromExchange(final InfluencerSubscription subscription, final Instrument currencyPair, final Side side, final XExchange xExchange) {
    return new Price(xExchange.getPriceFromExchange(currencyPair, side), System.currentTimeMillis());
  }

  private static Balance getBalanceFromExchange(final InfluencerSubscription subscription, final XExchange xExchange) {
    return new Balance(xExchange.getBalanceFromExchange(subscription.getPreferredQuoteCurrency()), System.currentTimeMillis());
  }

  public static XExchange createXExchange(final InfluencerSubscription subscription) {
    final String exchange = subscription.getExchange().toUpperCase();
    ExchangeSpecification specification = null;
    switch (exchange) {
      case "BINANCE": {
        specification = new BinanceExchange().getDefaultExchangeSpecification();
        processSpecification(specification, subscription, exchange);
        if (!"PRODUCTION".equalsIgnoreCase("TEST")) {
          if (subscription.isFuturesEnabled()) {
            specification.setExchangeSpecificParametersItem(BinanceExchange.SPECIFIC_PARAM_USE_FUTURES_SANDBOX, true);
          }
        }
        if (subscription.hasLeverage()) {
          specification.setExchangeSpecificParametersItem(BinanceExchange.SPECIFIC_PARAM_PORTFOLIO_MARGIN_ENABLED, true);
        }
        if (subscription.isFuturesEnabled()) {
          specification.setExchangeSpecificParametersItem(BinanceExchange.SPECIFIC_PARAM_FUTURES_ENABLED, true);
        }

        return new XBinanceExchange(ExchangeFactory.INSTANCE.createExchange(specification));
      }
    }

    return null;
  }

  private static void processSpecification(final ExchangeSpecification specification, final InfluencerSubscription subscription, final String exchange) {
    if (!"PRODUCTION".equalsIgnoreCase("TEST")) {
      specification.setExchangeSpecificParametersItem("Use_Sandbox", true);
    }
    if (subscription != null) {
      specification.setUserName(subscription.getApiUser());
      specification.setApiKey(subscription.getApiKey());
      specification.setSecretKey(subscription.getApiSecret());
    }
  }

  private static class Price {
    private double price;
    private long lastUpdated;

    public Price() {
    }

    public Price(double price, long lastUpdated) {
      this.price = price;
      this.lastUpdated = lastUpdated;
    }

    public double getPrice() {
      return price;
    }

    public void setPrice(double price) {
      this.price = price;
    }

    public long getLastUpdated() {
      return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
      this.lastUpdated = lastUpdated;
    }
  }

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
