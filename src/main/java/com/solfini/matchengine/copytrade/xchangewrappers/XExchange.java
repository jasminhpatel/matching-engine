package com.solfini.matchengine.copytrade.xchangewrappers;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.copytrade.CopyTrade;
import com.solfini.matchengine.copytrade.ExternalExchangeUtil;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceAdapters;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;
import si.mazi.rescu.SynchronizedValueFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class XExchange implements Exchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XExchange.class);
  protected final Exchange exchange;
  protected final XAccountService xAccountService;

  public XExchange(Exchange exchange) {
    this.exchange = exchange;
    this.xAccountService = new XAccountService(exchange.getAccountService());
  }

  public Exchange getExchange() {
    return exchange;
  }

  @Override
  public ExchangeSpecification getExchangeSpecification() {
    return this.exchange.getExchangeSpecification();
  }

  @Override
  public ExchangeMetaData getExchangeMetaData() {
    return this.exchange.getExchangeMetaData();
  }

  @Override
  public List<Instrument> getExchangeInstruments() {
    return exchange.getExchangeInstruments();
  }

  @Override
  public SynchronizedValueFactory<Long> getNonceFactory() {
    return this.exchange.getNonceFactory();
  }

  @Override
  public ExchangeSpecification getDefaultExchangeSpecification() {
    return this.exchange.getDefaultExchangeSpecification();
  }

  @Override
  public void applySpecification(ExchangeSpecification exchangeSpecification) {
    this.exchange.applySpecification(exchangeSpecification);
  }

  @Override
  public MarketDataService getMarketDataService() {
    return this.exchange.getMarketDataService();
  }

  @Override
  public TradeService getTradeService() {
    return this.exchange.getTradeService();
  }

  @Override
  public AccountService getAccountService() {
    return null;
  }

  @Override
  public void remoteInit() throws IOException, ExchangeException {
    this.exchange.remoteInit();
  }

  public double getBalanceFromExchange(final String quoteCurrency) {
    try {
      final AccountService accountService = this.exchange.getAccountService();
      if (accountService == null)
        return 0D;
      final Wallet wallet = accountService.getAccountInfo().getWallet();
      if (wallet == null)
        return 0D;
      final Map<Currency, Balance> balances = wallet.getBalances();
      if (balances == null)
        return 0D;
      Currency currency = Currency.getInstance(quoteCurrency);
      if (currency == null) {
        return 0D;
      }
      org.knowm.xchange.dto.account.Balance balance = balances.get(currency);
      if (balance == null) {
        if ("USDC".equalsIgnoreCase(quoteCurrency) || "USDT".equalsIgnoreCase(quoteCurrency)) {
          currency = Currency.getInstance("USD");
          if (currency == null) {
            return 0D;
          }
          balance = balances.get(currency);
          if (balance == null) {
            return 0D;
          }
        }
      }

      double available = balance.getAvailable().doubleValue();

      return MbxMath.roundToBestPrecision(available);

    } catch (Exception e) {
      LOGGER.error("Error occurred wile fetching balance. ", e);
    }
    return 0D;
  }

  public double getPriceFromExchange(final Instrument currencyPair, final Side side) {
    try {
      final MarketDataService marketDataService = getMarketDataService();
      if (marketDataService == null)
        return 0D;
      final Ticker ticker = marketDataService.getTicker(currencyPair);//cast because of deprecated method
      if (ticker == null)
        return 0D;
      double price = 0;
      if (side == Side.BUY) {
        if (ticker.getAsk() != null)
          price = ticker.getAsk().doubleValue();
      } else {
        if (ticker.getBid() != null)
          price = ticker.getBid().doubleValue();
      }
      if (price == 0) {
        if (ticker.getLast() != null)
          price = ticker.getLast().doubleValue();
      }
      if (price == 0) {
        if (ticker.getOpen() != null)
          price = ticker.getOpen().doubleValue();
      }
      return MbxMath.roundToBestPrecision(price);
    } catch (Exception e) {
      LOGGER.error("Error occurred wile fetching price. ", e);
    }
    return 0D;
  }

  public void placeOrder(final CopyTrade copyTrade) throws Exception {
    final XExchange xExchange = copyTrade.getxExchange();
    final TradeService tradeService = xExchange.getTradeService();

    final Order.OrderType xOrderType = Side.BUY == copyTrade.getSide() ? Order.OrderType.BID : Order.OrderType.ASK;
    LOGGER.info(Constants.LOG_FMT_6, "Copy trade order, orderType: ", copyTrade.getOrdType(), " side: ", xOrderType, " quantity: ",
        copyTrade.getxQuantity(), " price: ", copyTrade.getxPrice(), " clOrdId: ", copyTrade.getClOrdId());
    if (copyTrade.getOrdType() == OrdType.MARKET) {
      final MarketOrder
          order = new MarketOrder(xOrderType, copyTrade.getxQuantity(), copyTrade.getInstrument(), copyTrade.getClOrdId(), null);
      if (copyTrade.getSubscription().hasLeverage()) {
        order.setLeverage("1");
      }
      final String returnValue = tradeService.placeMarketOrder(order);
      copyTrade.setExternalId(returnValue);
    } else {
      final LimitOrder
          order = new LimitOrder(xOrderType, copyTrade.getxQuantity(), copyTrade.getInstrument(), copyTrade.getClOrdId(), null, copyTrade.getxPrice());
      if (copyTrade.getSubscription().hasLeverage()) {
        order.setLeverage("1");
      }
      final String returnValue = tradeService.placeLimitOrder(order);
      copyTrade.setExternalId(returnValue);
    }

    updateOrderStatus(copyTrade);
  }

  public void updateOrderStatus(final CopyTrade copyTrade) throws Exception {
    final TradeService tradeService = getTradeService();
    final OrderQueryParams orderQueryParams = ExternalExchangeUtil.createOrderQueryParams(copyTrade, copyTrade.getInstrument());
    final Collection<Order> orders = tradeService.getOrder(orderQueryParams);
    if (orders != null && !orders.isEmpty()) {
      Order summary = orders.iterator().next();
      copyTrade.setPriceScale((short) 4);
      if (summary.getAveragePrice() != null)
        copyTrade.setPrice(MbxMath.changeScale(summary.getAveragePrice().doubleValue(), copyTrade.getPriceScale()));
      copyTrade.setOriginalAmount(summary.getOriginalAmount().doubleValue());
      copyTrade.setCumulativeAmount(summary.getCumulativeAmount().doubleValue());
      copyTrade.setStatus(summary.getStatus().name());
    }
  }
}
