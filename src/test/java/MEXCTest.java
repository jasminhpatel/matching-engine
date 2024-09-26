import com.solfini.matchengine.copytrade.ExternalExchangeUtil;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;

import org.knowm.xchange.binance.dto.trade.BinanceTradeHistoryParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.mexc.MEXCExchange;
import org.knowm.xchange.mexc.dto.trade.MEXCQueryOrderParams;
import org.knowm.xchange.mexc.service.MEXCTradeService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public class MEXCTest {
  public static void main(String[] args) throws Exception {
    boolean isFutures = true;
    ExchangeSpecification specification = new MEXCExchange().getDefaultExchangeSpecification();
    specification.setApiKey("mx0vglxj0LtnCs5A3W");
    specification.setSecretKey("acc41e982f5e4d3aab87344cc56092d3");
    if (isFutures)
      specification.setExchangeSpecificParametersItem(MEXCExchange.SPECIFIC_PARAM_FUTURES_ENABLED, true);

    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(specification);
    //org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("ETH", "USDT"), isFutures);

    String ret = sendOrder(exchange, "2600.0");
    System.out.println(ret);
    getOrder(exchange, ret);
    //getOrder(exchange, "411861921081");
    //getOrders(exchange, instrument);

    System.out.println("Done");
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

  public static String sendOrder(Exchange exchange, String limitPrice) throws IOException {
    Side side = Side.SELL;
    final TradeService tradeService = exchange.getTradeService();
    final org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("ETH", "USDT"), true);
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
    final TradeService tradeService = exchange.getTradeService();
    final org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("BTC", "USDT"), true);
    int retryCount = 0;
    while (retryCount < 5) {
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
        Thread.sleep(250);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static void getOrders(Exchange exchange, Instrument instrument) {
    final MEXCTradeService tradeService = (MEXCTradeService) exchange.getTradeService();
    int retryCount = 0;
    while (retryCount < 5) {
      retryCount++;
      try {
        final OrderQueryParams orderQueryParams = new MEXCQueryOrderParams(instrument, null);
        final Collection<Order> orders = tradeService.getAllOrders(orderQueryParams);
        if (orders != null) {
          for (Order summary : orders) {
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

}
