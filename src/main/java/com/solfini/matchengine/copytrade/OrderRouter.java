package com.solfini.matchengine.copytrade;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.orderbook.CopyTradeOrderBook;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;

public class OrderRouter {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(OrderRouter.class);

  /*public static void placeMarketOrder(final String clOrdId, final Side side, final BigDecimal xQuantity,
      final CurrencyPair currencyPair, final CopyTrade copyTrade) throws IOException {
    final Exchange exchange = ExternalExchangeUtil.createExchange(copyTrade.getSubscription());
    if (exchange == null) {
      copyTrade.setResult("FAILED");
      return;
    }
    final TradeService tradeService = exchange.getTradeService();

    final Order.OrderType xOrderType = Side.BUY == side ? Order.OrderType.BID : Order.OrderType.ASK;
    LOGGER.info("Copy trade order, orderType: " + xOrderType + " quantity: " + xQuantity + " clOrdId: " + clOrdId);
    final MarketOrder order = new MarketOrder(xOrderType, xQuantity, currencyPair, clOrdId, null);

    final String returnValue = tradeService.placeMarketOrder(order);

    copyTrade.setExternalId(returnValue);

    final OrderQueryParams orderQueryParams = ExternalExchangeUtil.createOrderQueryParams(copyTrade, currencyPair);
    final Collection<Order> orders = tradeService.getOrder(orderQueryParams);
    if (orders != null && !orders.isEmpty()) {
      Order summary = orders.iterator().next();
      copyTrade.setPriceScale((short) 4);
      copyTrade.setPrice(MbxMath.changeScale(summary.getAveragePrice().doubleValue(), copyTrade.getPriceScale()));
      copyTrade.setOriginalAmount(summary.getOriginalAmount().doubleValue());
      copyTrade.setCumulativeAmount(summary.getCumulativeAmount().doubleValue());
      copyTrade.setStatus(summary.getStatus().name());
    }
  }

  public static void placeLimitOrder(final String clOrdId, final Side side, final BigDecimal xQuantity, final BigDecimal xPrice,
      final CurrencyPair currencyPair, final CopyTrade copyTrade) throws IOException {
    final Exchange exchange = ExternalExchangeUtil.createExchange(copyTrade.getSubscription());
    if (exchange == null) {
      copyTrade.setResult("FAILED");
      return;
    }
    final TradeService tradeService = exchange.getTradeService();

    final Order.OrderType xOrderType = Side.BUY == side ? Order.OrderType.BID : Order.OrderType.ASK;
    LOGGER.info("Copy trade order, orderType: " + xOrderType + " quantity: " + xQuantity + " price: " + xPrice +  " clOrdId: " + clOrdId);
    final LimitOrder order = new LimitOrder(xOrderType, xQuantity, currencyPair, clOrdId, null, xPrice);

    final String returnValue = tradeService.placeLimitOrder(order);
    copyTrade.setExternalId(returnValue);

    try {
      final OrderQueryParams orderQueryParams = ExternalExchangeUtil.createOrderQueryParams(copyTrade, currencyPair);
      final Collection<Order> orders = tradeService.getOrder(orderQueryParams);
      if (orders != null && !orders.isEmpty()) {
        Order summary = orders.iterator().next();
        copyTrade.setPriceScale((short) 4);
        copyTrade.setPrice(MbxMath.changeScale(summary.getAveragePrice().doubleValue(), copyTrade.getPriceScale()));
        copyTrade.setOriginalAmount(summary.getOriginalAmount().doubleValue());
        copyTrade.setCumulativeAmount(summary.getCumulativeAmount().doubleValue());
        copyTrade.setStatus(summary.getStatus().name());
      }
    } catch (Exception e) {
      copyTrade.setResult("ERROR");
    }
  }*/

}
