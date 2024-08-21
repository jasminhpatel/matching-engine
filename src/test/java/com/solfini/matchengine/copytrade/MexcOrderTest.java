package com.solfini.matchengine.copytrade;

import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;

public class MexcOrderTest {
  public static void main(String[] args) throws IOException {
    InfluencerSubscription subscription = new InfluencerSubscription();
    subscription.setExchange("MEXC");
    subscription.setApiKey("mx0vglEiMdG2Rab34T");
    subscription.setApiSecret("32dd98b573f3480c975df712c733f187");
    final XExchange xExchange = ExternalExchangeUtil.createXExchange(subscription);
    CurrencyPair pair = new CurrencyPair(Currency.ADA, Currency.USDC);
    Instrument instrument = xExchange.getInstrument(pair, false);
    final LimitOrder
        order = new LimitOrder(Order.OrderType.BID, new BigDecimal("20"), instrument, "1234", null, new BigDecimal("0.3193"));


    final TradeService tradeService = xExchange.getTradeService();
    //String returnValue = tradeService.placeLimitOrder(order);

    String returnValue = "C02__452500641248489472090";

    final OrderQueryParams
        orderQueryParams = ExternalExchangeUtil.createOrderQueryParams(subscription.getExchange(), instrument, returnValue, false);
    final Collection<Order> orders = tradeService.getOrder(orderQueryParams);
    Order summary = null;
    if (orders != null && !orders.isEmpty()) {
      summary = orders.iterator().next();
      if (summary != null) {
        System.out.println(summary.getStatus());
        System.out.println(summary.getCumulativeAmount().doubleValue());
      }
    }
    System.out.println(returnValue);
  }
}
