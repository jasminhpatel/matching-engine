package com.solfini.matchengine.executionexchange.xchangewrappers;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.ExecutionExchangeConfig;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.ExternalOrder;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;

import java.util.HashMap;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.solfini.common.Constants.*;

public abstract class XExchange implements Exchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XExchange.class);
  protected final Exchange exchange;
  protected final XAccountService xAccountService;

  public XExchange(final Exchange exchange) {
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
  public void applySpecification(final ExchangeSpecification exchangeSpecification) {
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
    return this.exchange.getAccountService();
  }

  @Override
  public void remoteInit() throws IOException, ExchangeException {
    this.exchange.remoteInit();
  }

  public Balance getStableCoinBalanceFromExchange() {
    final Balance balance = new Balance();
    balance.setLastUpdated(System.currentTimeMillis());
    try {
      final AccountService accountService = this.exchange.getAccountService();
      if (accountService == null)
        return balance;
      final Wallet wallet = accountService.getAccountInfo().getWallet();
      if (wallet == null)
        return balance;
      final Map<Currency, org.knowm.xchange.dto.account.Balance> balances = wallet.getBalances();
      if (balances == null)
        return balance;

      org.knowm.xchange.dto.account.Balance usdBalance = balances.get(Currency.getInstance(USD));
      org.knowm.xchange.dto.account.Balance usdcBalance = balances.get(Currency.getInstance(USDC));
      org.knowm.xchange.dto.account.Balance usdtBalance = balances.get(Currency.getInstance(USDT));
      if (usdBalance != null && usdBalance.hasAvailable()) {
        balance.setUsdBalance(MbxMath.roundToBestPrecision(usdBalance.getAvailable().doubleValue()));
      }
      if (usdcBalance != null && usdcBalance.hasAvailable()) {
        balance.setUsdcBalance(MbxMath.roundToBestPrecision(usdcBalance.getAvailable().doubleValue()));
      }
      if (usdtBalance != null && usdtBalance.hasAvailable()) {
        balance.setUsdtBalance(MbxMath.roundToBestPrecision(usdtBalance.getAvailable().doubleValue()));
      }

    } catch (Exception e) {
      LOGGER.error("Error occurred wile fetching balance. ", e);
    }
    LOGGER.info("Balance summary: " + balance.toJson());
    return balance;
  }

  public Balance getBalanceFromExchange(final String symbol, final ExecutionExchangeConfig subscription) {
    final Balance balance = new Balance();
    balance.setLastUpdated(System.currentTimeMillis());
    try {
      final AccountService accountService = this.exchange.getAccountService();
      if (accountService == null)
        return balance;
      final Wallet wallet = accountService.getAccountInfo().getWallet();
      if (wallet == null)
        return balance;
      final Map<Currency, org.knowm.xchange.dto.account.Balance> balances = wallet.getBalances();
      if (balances == null)
        return balance;

      final Currency currency = Currency.getInstance(symbol);
      if (currency == null) {
        return balance;
      }

      org.knowm.xchange.dto.account.Balance bal = balances.get(currency);

      if (bal != null && bal.hasAvailable()) {
        if ("Bybit".equalsIgnoreCase(exchange.getExchangeSpecification().getExchangeName())) {
          if (bal.hasAvailable()) {
            balance.setCoinBalance(MbxMath.roundToBestPrecision(bal.getAvailable().doubleValue() + bal.getFrozen().doubleValue()));
          }
        } else {
          if (bal.hasAvailable()) {
            balance.setCoinBalance(MbxMath.roundToBestPrecision(bal.getAvailable().doubleValue()));
          }
        }
      }

    } catch (Exception e) {
      LOGGER.error("Error occurred wile fetching balance. ", e);
    }
    return balance;
  }

  public Map<String, Balance> getBalancesFromExchange(final List<String> symbols, final ExecutionExchangeConfig subscription) {
    final Map<String, Balance> balanceMap = new HashMap<>();

    try {
      final AccountService accountService = this.exchange.getAccountService();
      if (accountService == null)
        return balanceMap;
      final Wallet wallet = accountService.getAccountInfo().getWallet();
      if (wallet == null)
        return balanceMap;
      final Map<Currency, org.knowm.xchange.dto.account.Balance> balances = wallet.getBalances();
      if (balances == null)
        return balanceMap;

      for (String symbol : symbols) {
        final Balance balance = new Balance();
        balance.setLastUpdated(System.currentTimeMillis());
        final Currency currency = Currency.getInstance(symbol);
        if (currency == null) {
          balanceMap.put(symbol, balance);
          continue;
        }
        org.knowm.xchange.dto.account.Balance bal = balances.get(currency);
        if (bal != null && bal.hasAvailable()) {
          if ("Bybit".equalsIgnoreCase(exchange.getExchangeSpecification().getExchangeName())) {
            if (bal.hasAvailable()) {
              balance.setCoinBalance(MbxMath.roundToBestPrecision(bal.getAvailable().doubleValue() + bal.getFrozen().doubleValue()));
            }
          } else {
            if (bal.hasAvailable()) {
              balance.setCoinBalance(MbxMath.roundToBestPrecision(bal.getAvailable().doubleValue()));
            }
          }
        }
        balanceMap.put(symbol, balance);
      }
    } catch (Exception e) {
      LOGGER.error("Error occurred wile fetching balance. ", e);
    }
    return balanceMap;
  }

  public double getPriceFromExchange(final Instrument currencyPair, final Side side) {
    try {
      final MarketDataService marketDataService = getMarketDataService();
      if (marketDataService == null)
        return 0D;
      final Ticker ticker = marketDataService.getTicker(currencyPair);// cast because of deprecated method
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

  public void placeOrder(final ExternalOrder externalOrder, final Set<Order.IOrderFlags> orderFlags) throws Exception {
    final XExchange xExchange = externalOrder.getxExchange();
    final TradeService tradeService = xExchange.getTradeService();

    final Order.OrderType xOrderType = Side.BUY == externalOrder.getSide() ? Order.OrderType.BID : Order.OrderType.ASK;
    LOGGER.info(Constants.LOG_FMT_6, "External execution order, orderType: ", externalOrder.getOrdType(), " side: ", xOrderType,
        " quantity: ", externalOrder.getxQuantity(), " price: ", externalOrder.getxPrice(), " clOrdId: ", externalOrder.getClOrdId(),
        " instrument: ", externalOrder.getInstrument().getBase(), "/", externalOrder.getInstrument().getCounter());
    if (externalOrder.getOrdType() == OrdType.MARKET) {
      final MarketOrder order =
          new MarketOrder(xOrderType, externalOrder.getxQuantity(), externalOrder.getInstrument(), externalOrder.getClOrdId(), null);
      if (externalOrder.getSubscription().hasLeverage()) {
        order.setLeverage("1");
      }
      final String returnValue = tradeService.placeMarketOrder(order);
      externalOrder.setExternalId(returnValue);
    } else {
      final LimitOrder order =
          new LimitOrder.Builder(xOrderType, externalOrder.getInstrument()).originalAmount(externalOrder.getxQuantity())
              .limitPrice(externalOrder.getxPrice()).userReference(externalOrder.getClOrdId()).build();
      // final LimitOrder
      // order = new LimitOrder(xOrderType, externalOrder.getxQuantity(), externalOrder.getInstrument(), externalOrder.getClOrdId(), null,
      // externalOrder.getxPrice());

      if (externalOrder.getSubscription().hasLeverage()) {
        order.setLeverage("1");
      }
      if (orderFlags != null) {
        order.setOrderFlags(orderFlags);
      }
      final String returnValue = tradeService.placeLimitOrder(order);
      externalOrder.setExternalId(returnValue);
    }

    requestGetOrderStatus(externalOrder);
  }

  public double placeConversionOrder(final String exchange, final Instrument instrument, final double quantity, final double price,
      final Side side, final String clOrdId, final ExternalSymbol metadata) throws Exception {
    final TradeService tradeService = this.getTradeService();
    int priceScale = 2, quantityScale = 2;
    if (metadata != null) {
      priceScale = metadata.getPriceScale();
      quantityScale = metadata.getQtyScale();
    }

    BigDecimal priceValue = new BigDecimal(price);
    priceValue = priceValue.setScale(priceScale, side == Side.BUY ? RoundingMode.HALF_UP : RoundingMode.HALF_DOWN);
    BigDecimal quantityValue = new BigDecimal(quantity);
    quantityValue = quantityValue.setScale(quantityScale, RoundingMode.HALF_UP);

    final Order.OrderType xOrderType = Side.BUY == side ? Order.OrderType.BID : Order.OrderType.ASK;
    LOGGER.info(Constants.LOG_FMT_6, "Convert order, orderType: LIMIT", " side: ", side.name(), " quantity: ", quantity, " price: ", price,
        " clOrdId: ", clOrdId);

    final LimitOrder order = new LimitOrder(xOrderType, quantityValue, instrument, clOrdId, null, priceValue);
    final String returnValue = tradeService.placeLimitOrder(order);
    LOGGER.info(Constants.LOG_FMT_6, "Convert order, orderType: LIMIT", " side: ", side.name(), " quantity: ", quantity, " price: ", price,
        " clOrdId: ", clOrdId, " returnValue: ", returnValue);
    return getFilledQuantity(exchange, instrument, returnValue, false, clOrdId);

  }

  public void requestGetOrderStatus(final ExternalOrder externalOrder) throws Exception {
    final TradeService tradeService = getTradeService();
    int retryCount = 0;
    Thread.sleep(50);
    while (retryCount < 2400) {// wait upto 10 mins
      LOGGER.info(Constants.LOG_FMT_6, "Check external order status, clOrdId: ", externalOrder.getClOrdId(), " externalId: ",
          externalOrder.getExternalId());
      retryCount++;
      try {
        final OrderQueryParams orderQueryParams = ExternalExchangeUtil.createOrderQueryParams(externalOrder.getExchange(),
            externalOrder.getInstrument(), externalOrder.getExternalId(), externalOrder.isFuturesEnabled());
        final Collection<Order> orders = tradeService.getOrder(orderQueryParams);
        if (orders != null && !orders.isEmpty()) {
          final Order summary = orders.iterator().next();
          externalOrder.setPriceScale((short) 4);
          if (summary.getAveragePrice() != null) {
            externalOrder.setPrice(MbxMath.changeScale(summary.getAveragePrice().doubleValue(), externalOrder.getPriceScale()));
            externalOrder.setAveragePrice(summary.getAveragePrice().doubleValue());
          }
          externalOrder.setOriginalAmount(summary.getOriginalAmount().doubleValue());
          externalOrder.setCumulativeAmount(summary.getCumulativeAmount().doubleValue());
          externalOrder.setStatus(summary.getStatus().name());
          LOGGER.info(Constants.LOG_FMT_6, "Check external order status, clOrdId: ", externalOrder.getClOrdId(), " externalId: ",
              externalOrder.getExternalId(), " status: ", externalOrder.getStatus());
          if (ORDER_STATUS_FILLED.equalsIgnoreCase(externalOrder.getStatus())) {
            if (summary.getFee() != null) {
              externalOrder.setFee(summary.getFee().doubleValue());
            }
            externalOrder.setTradeValue(summary.getCumulativeAmount().doubleValue() * summary.getAveragePrice().doubleValue());
            externalOrder.setResult(SUCCESS);
            break;
          } else if (ORDER_STATUS_CANCELED.equalsIgnoreCase(externalOrder.getStatus())
              || ORDER_STATUS_REJECTED.equalsIgnoreCase(externalOrder.getStatus())
              || ORDER_STATUS_EXPIRED.equalsIgnoreCase(externalOrder.getStatus())) {
            externalOrder.setResult(FAILURE);
            break;
          }
        }
        Thread.sleep(250);
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG + " in updateOrderStatus clOrdId: " + externalOrder.getClOrdId(), e);
      }
    }
  }

  public double getFilledQuantity(final String exchange, final Instrument instrument, final String reference, final boolean futuresEnabled,
      final String clOrdId) {
    Order summary = null;
    int retryCount = 0;
    while (retryCount < 5) {
      try {
        Thread.sleep(50);
        retryCount++;
        Order.OrderStatus status = null;
        double filledQty = 0;
        final TradeService tradeService = getTradeService();
        final OrderQueryParams orderQueryParams =
            ExternalExchangeUtil.createOrderQueryParams(exchange, instrument, reference, futuresEnabled);
        final Collection<Order> orders = tradeService.getOrder(orderQueryParams);
        if (orders != null && !orders.isEmpty()) {
          summary = orders.iterator().next();
          if (summary != null) {
            status = summary.getStatus();
            filledQty = summary.getCumulativeAmount().doubleValue();
            if (summary.getStatus() == Order.OrderStatus.FILLED) {
              break;
            }
          }
        }
        LOGGER.info(Constants.LOG_FMT_6, "Convert order status. clOrdId: ", clOrdId,
            " status: " + status + "count: " + retryCount + " filledQty:" + filledQty);
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }

    if (summary != null) {
      return summary.getCumulativeAmount().doubleValue();
    }
    return 0D;
  }

  public org.knowm.xchange.instrument.Instrument getInstrument(final CurrencyPair currencyPair, boolean isFuture) {
    try {
      final List<org.knowm.xchange.instrument.Instrument> instruments = getExchange().getExchangeInstruments();
      org.knowm.xchange.instrument.Instrument instrument = null;
      for (org.knowm.xchange.instrument.Instrument i : instruments) {
        if (i.getBase().getSymbol().equalsIgnoreCase(currencyPair.getBase().getSymbol())
            && i.getCounter().getSymbol().equalsIgnoreCase(currencyPair.getCounter().getSymbol())) {
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
      LOGGER.info(Constants.LOG_FMT_2, "Failed to load instrument. ", currencyPair.toString());
    }
    return null;
  }

  // default getExchangeInstruments() does not return full data set.
  public abstract List<ExternalSymbol> getExchangeInstrumentsFull();

  public static class Balance {
    private double usdBalance;
    private double usdcBalance;
    private double usdtBalance;
    private double coinBalance;
    private long lastUpdated;

    public double getTotalStableCoinBalance() {
      return usdBalance + usdcBalance + usdtBalance;
    }

    public double getBalance(final String currency) {
      if (currency == null)
        return 0D;
      switch (currency.toUpperCase()) {
        case USD:
          return usdBalance;
        case USDC:
          return usdcBalance;
        case USDT:
          return usdtBalance;
        default:
          return 0D;
      }
    }

    public final double getUsdBalance() {
      return usdBalance;
    }

    public final void setUsdBalance(final double usdBalance) {
      this.usdBalance = usdBalance;
    }

    public final double getUsdcBalance() {
      return usdcBalance;
    }

    public final void setUsdcBalance(final double usdcBalance) {
      this.usdcBalance = usdcBalance;
    }

    public final double getUsdtBalance() {
      return usdtBalance;
    }

    public final void setUsdtBalance(final double usdtBalance) {
      this.usdtBalance = usdtBalance;
    }

    public final long getLastUpdated() {
      return lastUpdated;
    }

    public final void setLastUpdated(final long lastUpdated) {
      this.lastUpdated = lastUpdated;
    }

    public final double getCoinBalance() {
      return coinBalance;
    }

    public final void setCoinBalance(final double coinBalance) {
      this.coinBalance = coinBalance;
    }

    public String getBestQuoteCurrency(final double orderValue, final String bestQuoteInExchange) {
      if (getBalance(bestQuoteInExchange) > orderValue) {
        return bestQuoteInExchange;
      }
      if (usdBalance > orderValue) {
        return USD;
      } else if (usdcBalance > orderValue) {
        return USDC;
      } else if (usdtBalance > orderValue) {
        return USDT;
      } else {
        return bestQuoteInExchange;
      }
    }

    public boolean hasBalance() {
      return usdBalance != 0 || usdcBalance != 0 || usdtBalance != 0 || coinBalance != 0;
    }

    public String toJson() {
      final StringBuilder sb = new StringBuilder("{");
      sb.append("\"lastUpdated\":").append(lastUpdated);
      if (usdBalance != 0) sb.append(",\"usdBalance\":").append(usdBalance);
      if (usdcBalance != 0) sb.append(",\"usdcBalance\":").append(usdcBalance);
      if (usdtBalance != 0) sb.append(",\"usdtBalance\":").append(usdtBalance);
      if (coinBalance != 0) sb.append(",\"coinBalance\":").append(coinBalance);

      sb.append('}');
      return sb.toString();
    }

    public double getLastBalance(final String symbol) {
      double balance = 0;
      if (symbol == null) {
        balance = 0;
      } else {
        switch (symbol.toUpperCase()) {
          case USD:
            balance = usdBalance;
            break;
          case USDC:
            balance = usdcBalance;
            break;
          case USDT:
            balance = usdtBalance;
            break;
          default:
            balance = coinBalance;
        }
      }
      return balance;
    }
  }
}
