package com.solfini.matchengine.copytrade.xchangewrappers;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.copytrade.CopyTrade;
import com.solfini.matchengine.copytrade.ExternalCurrencyPairCache;
import com.solfini.matchengine.copytrade.ExternalExchangeUtil;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceAdapters;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
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

import static com.solfini.common.Constants.*;

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
      if (usdBalance != null) {
        balance.setUsdBalance(MbxMath.roundToBestPrecision(usdBalance.getAvailable().doubleValue()));
      }
      if (usdcBalance != null) {
        balance.setUsdcBalance(MbxMath.roundToBestPrecision(usdcBalance.getAvailable().doubleValue()));
      }
      if (usdtBalance != null) {
        balance.setUsdtBalance(MbxMath.roundToBestPrecision(usdtBalance.getAvailable().doubleValue()));
      }

    } catch (Exception e) {
      LOGGER.error("Error occurred wile fetching balance. ", e);
    }
    return balance;
  }

  public Balance getBalanceFromExchange(final String symbol) {
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

      if (bal != null) {
        balance.setCoinBalance(MbxMath.roundToBestPrecision(bal.getAvailable().doubleValue()));
      }

    } catch (Exception e) {
      LOGGER.error("Error occurred wile fetching balance. ", e);
    }
    return balance;
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
        copyTrade.getxQuantity(), " price: ", copyTrade.getxPrice(), " clOrdId: ", copyTrade.getClOrdId(), " instrument: ", copyTrade.getInstrument().getBase(),
        "/", copyTrade.getInstrument().getCounter());
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

  public double placeConversionOrder(final String exchange, final Instrument instrument, final double quantity, final double price, final Side side,
      final String clOrdId, final SymbolStatus metadata) throws Exception {
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
    LOGGER.info(Constants.LOG_FMT_6, "Convert order, orderType: LIMIT", " side: ", side.name(), " quantity: ",
        quantity, " price: ", price, " clOrdId: ", clOrdId);

    final LimitOrder
        order = new LimitOrder(xOrderType, quantityValue, instrument, clOrdId, null, priceValue);
    final String returnValue = tradeService.placeLimitOrder(order);
    LOGGER.info(Constants.LOG_FMT_6, "Convert order, orderType: LIMIT", " side: ", side.name(), " quantity: ",
        quantity, " price: ", price, " clOrdId: ", clOrdId, " returnValue: ", returnValue);
    return getFilledQuantity(exchange, instrument, returnValue, false, clOrdId);

  }

  public void updateOrderStatus(final CopyTrade copyTrade) throws Exception {
    final TradeService tradeService = getTradeService();
    final OrderQueryParams orderQueryParams = ExternalExchangeUtil.createOrderQueryParams(copyTrade.getExchange(), copyTrade.getInstrument(),
        copyTrade.getExternalId(), copyTrade.isFuturesEnabled());
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

  public double getFilledQuantity(final String exchange, Instrument instrument, final String reference, final boolean futuresEnabled, final String clOrdId) {
    Order summary = null;
    int count = 0;
    while (count < 5) {
      try {
        Thread.sleep(50);
        count++;
        Order.OrderStatus status = null;
        double filledQty = 0;
        final TradeService tradeService = getTradeService();
        final OrderQueryParams orderQueryParams = ExternalExchangeUtil.createOrderQueryParams(exchange, instrument, reference, futuresEnabled);
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
        LOGGER.info(Constants.LOG_FMT_6, "Convert order status. clOrdId: ", clOrdId, " status: " + status + "count: " + count
        + " filledQty:" + filledQty);
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
      LOGGER.info(Constants.LOG_FMT_2, "Failed to load instrument. ", currencyPair.toString());
    }
    return null;
  }

  //default getExchangeInstruments() does not return full data set.
  public abstract List<SymbolStatus> getExchangeInstrumentsFull();

  public static class Balance {
    private double usdBalance;
    private double usdcBalance;
    private double usdtBalance;
    private double coinBalance;
    private long lastUpdated;

    public double getTotalStableCoinBalance() {
      return usdBalance + usdcBalance +  usdtBalance;
    }

    public double getBalance(final String currency) {
      if (currency == null) return 0D;
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

    public double getUsdBalance() {
      return usdBalance;
    }

    public void setUsdBalance(double usdBalance) {
      this.usdBalance = usdBalance;
    }

    public double getUsdcBalance() {
      return usdcBalance;
    }

    public void setUsdcBalance(double usdcBalance) {
      this.usdcBalance = usdcBalance;
    }

    public double getUsdtBalance() {
      return usdtBalance;
    }

    public void setUsdtBalance(double usdtBalance) {
      this.usdtBalance = usdtBalance;
    }

    public long getLastUpdated() {
      return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
      this.lastUpdated = lastUpdated;
    }

    public double getCoinBalance() {
      return coinBalance;
    }

    public void setCoinBalance(double coinBalance) {
      this.coinBalance = coinBalance;
    }

    public String toJson() {
      final StringBuilder sb = new StringBuilder("{");
      sb.append("\"usdBalance\":").append(usdBalance);
      sb.append(",\"usdcBalance\":").append(usdcBalance);
      sb.append(",\"usdtBalance\":").append(usdtBalance);
      sb.append(",\"lastUpdated\":").append(lastUpdated);
      sb.append('}');
      return sb.toString();
    }
  }

  public static class SymbolStatus {
    private String exchange;
    private String base;
    private String quote;
    private String prompt;
    private boolean futures;
    private boolean tradable;
    private long updated;
    private long closePricePercentage = 2000;
    private int priceScale;
    private int qtyScale;

    public String getKey() {
      return (this.getExchange() + "_" + this.getBase() + "/" + this.getQuote() + "_" + (this.isFutures() ? "1" :"0")).toLowerCase();
    }

    public String getExchange() {
      return exchange;
    }

    public void setExchange(String exchange) {
      this.exchange = exchange;
    }

    public String getBase() {
      return base;
    }

    public void setBase(String base) {
      this.base = base;
    }

    public String getQuote() {
      return quote;
    }

    public void setQuote(String quote) {
      this.quote = quote;
    }

    public String getPrompt() {
      return prompt;
    }

    public void setPrompt(String prompt) {
      this.prompt = prompt;
    }

    public boolean isFutures() {
      return futures;
    }

    public void setFutures(boolean futures) {
      this.futures = futures;
    }

    public boolean isTradable() {
      return tradable;
    }

    public void setTradable(boolean tradable) {
      this.tradable = tradable;
    }

    public long getUpdated() {
      return updated;
    }

    public void setUpdated(long updated) {
      this.updated = updated;
    }

    public long getClosePricePercentage() {
      return closePricePercentage;
    }

    public void setClosePricePercentage(long closePricePercentage) {
      this.closePricePercentage = closePricePercentage;
    }

    public int getPriceScale() {
      return priceScale;
    }

    public void setPriceScale(int priceScale) {
      this.priceScale = priceScale;
    }

    public int getQtyScale() {
      return qtyScale;
    }

    public void setQtyScale(int qtyScale) {
      this.qtyScale = qtyScale;
    }
  }
}
