package rnd;

import com.solfini.matchengine.stats.TradeHistory;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.dto.trade.BinanceQueryOrderParams;
import org.knowm.xchange.binance.dto.trade.BinanceTradeHistoryParams;
//import org.knowm.xchange.bitmex.BitmexExchange;
//import org.knowm.xchange.bitstamp.BitstampExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.trade.*;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParam;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;

public class OrderMain {
  public static void main(String[] args) throws IOException {
    //Exchange exchange = createExchange("BITMEX", null, "0C1YKYc88iUJh78a4a1FJJeJ", "TJ_yafVFzXLk6hsEaVws7C3IHMXWjrwKs6nqDsnxNUeEWmhi");
    Exchange exchange = createExchange("BINANCE", null, "L65Hq6Fcc6LdROvk32nwjzzvC4fbWQpUx1WG8cFJSMXiV55jpTe7a117RJ57hKOc",
        "P4AgUEQtLEzKfGe31C8y9nzjYUunwgbtpMXuqjmajHhkVa5vm3gmJn7I35WVJVFA");
    Object metadata = exchange.getExchangeMetaData();
    Object ins = exchange.getExchangeInstruments();
    Object md = exchange.getMarketDataService();

    AccountService accountService = exchange.getAccountService();

    AccountInfo accountInfo = accountService.getAccountInfo();

    TradeService tradeService = exchange.getTradeService();


/*      LimitOrder limitOrder = new LimitOrder(Order.OrderType.ASK,
          new BigDecimal("100"),
          CurrencyPair.XBT_USD,
          null,
          null,
          new BigDecimal("45000"));*/
   /* LimitOrder limitOrder =
        new LimitOrder(Order.OrderType.ASK, new BigDecimal("0.01"), CurrencyPair.BTC_USDT, null, null, new BigDecimal("45000"));
*/

/*
    MarketOrder marketOrder =
        new MarketOrder(Order.OrderType.ASK, new BigDecimal("0.001"), CurrencyPair.BTC_USDT, null, null);
*/

    //String returnValue = tradeService.placeLimitOrder(limitOrder);
    //String returnValue = tradeService.placeMarketOrder(marketOrder);
    //System.out.println("Order return value: " + returnValue);

    //final OpenOrdersParams openOrdersParamsAll = tradeService.createOpenOrdersParams();

    //printOpenOrders(tradeService, openOrdersParamsAll);

    //Collection<Order> order = tradeService.getOrder("16263924");

    //System.out.println(order.toString());

    CurrencyPair currencyPair = new CurrencyPair("BTC", "USDC");

    final TradeHistoryParams tradeHistoryParams = new BinanceTradeHistoryParams(currencyPair);

    UserTrades userTrades = tradeService.getTradeHistory(tradeHistoryParams);
    for(UserTrade userTrade: userTrades.getUserTrades()) {
      //if (returnValue.equalsIgnoreCase(userTrade.getOrderId())) {
        //System.out.println(userTrade.toString());
      //}
    }
    final OrderQueryParams orderQueryParams = new BinanceQueryOrderParams(currencyPair, "3981057");
    Collection<Order> orders = tradeService.getOrder(orderQueryParams);
    System.out.println(orders);

    //final TradeHistoryParams tradeHistoryParams = new BinanceTradeHistoryParams(CurrencyPair.ETH_USDT);

    //printTradeHistory(tradeService.getTradeHistory(tradeHistoryParams));

    //String limitOrderReturnValue = "65e19141-71da-4774-9b87-92154fe9927a";

    //  boolean cancelResult = tradeService.cancelOrder(limitOrderReturnValue);//
    //  System.out.println("Canceling returned " + cancelResult);
  }

  private static void printTradeHistory(UserTrades tradeHistory) {
    for (UserTrade trade : tradeHistory.getUserTrades()) {
      System.out.println(trade.toString());
    }
  }

  private static void printOpenOrders(TradeService tradeService, OpenOrdersParams openOrdersParams) throws IOException {
    OpenOrders openOrders = tradeService.getOpenOrders(openOrdersParams);
    System.out.printf("Open Orders for %s: %s%n", openOrdersParams, openOrders);
  }

  public static Exchange createExchange(final String exchange, final String username, final String apiKey, final String apiSecret) {
    ExchangeSpecification specification = null;
    switch (exchange.toUpperCase()) {
      case "BINANCE":
        specification = new BinanceExchange().getDefaultExchangeSpecification();
        specification.setExchangeSpecificParametersItem("Use_Sandbox", true);
        break;
/*      case "BITMEX":
        specification = new BitmexExchange().getDefaultExchangeSpecification();
        specification.setExchangeSpecificParametersItem("Use_Sandbox", true);
        break;*/
/*      case "BITSTAMP":
        specification = new BitstampExchange().getDefaultExchangeSpecification();
        break;*/

    }
    specification.setUserName(username);
    specification.setApiKey(apiKey);
    specification.setSecretKey(apiSecret);

    return ExchangeFactory.INSTANCE.createExchange(specification);
  }
}
