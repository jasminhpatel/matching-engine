package com.solfini.matchengine.copytrade.xchangewrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.copytrade.InfluencerSubscription;
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.service.account.AccountService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XOkexExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XOkexExchange.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private boolean futures;

  public XOkexExchange(final Exchange exchange, final boolean futures) {
    super(exchange);
    this.futures = futures;
  }

  public List<SymbolStatus> getExchangeInstrumentsFull() {
    final String apiUrl = exchange.getExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/api/v5/public/instruments?instType=" + (futures ? "SWAP" : "SPOT"), new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final XOkexExchange.OkexExchangeInfoFull info = mapper.readValue(response.getData(), XOkexExchange.OkexExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          List<SymbolStatus> symbolStatuses = new ArrayList<>(info.getSymbols().size());
          for (XOkexExchange.OkexSymbol okexSymbol : info.getSymbols()){
            final SymbolStatus symbolStatus = new SymbolStatus();
            symbolStatus.setExchange("okex");
            symbolStatus.setBase(okexSymbol.getBaseAsset());
            symbolStatus.setQuote(okexSymbol.getQuoteAsset());
            symbolStatus.setPrompt("");
            if (futures) {
              symbolStatus.setBase(okexSymbol.getSettleCurrency());
              symbolStatus.setQuote(okexSymbol.getValueCurrency());
            } else {
              symbolStatus.setBase(okexSymbol.getBaseAsset());
              symbolStatus.setQuote(okexSymbol.getQuoteAsset());
            }
            symbolStatus.setFutures(futures);
            symbolStatus.setTradable("1".equals(okexSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            //symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(getPrecision(okexSymbol.getQuotePrecision()));
            symbolStatus.setQtyScale(getPrecision(okexSymbol.getBaseAssetPrecision()));
            symbolStatuses.add(symbolStatus);
          }
          return symbolStatuses;
        }
      } catch (JsonProcessingException e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }
    return null;
  }

  public org.knowm.xchange.instrument.Instrument getInstrument(final CurrencyPair currencyPair, boolean isFuture) {
    try {
      final List<org.knowm.xchange.instrument.Instrument> instruments = getExchange().getExchangeInstruments();
      org.knowm.xchange.instrument.Instrument instrument = null;
      for (org.knowm.xchange.instrument.Instrument i : instruments) {
        if (i.getBase().getSymbol().equalsIgnoreCase(currencyPair.getBase().getSymbol()) && i.getCounter().getSymbol()
            .equalsIgnoreCase(currencyPair.getCounter().getSymbol())) {
          if (isFuture && i instanceof FuturesContract && "SWAP".equalsIgnoreCase(((FuturesContract) i).getPrompt())) {
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

  public Balance getBalanceFromExchange(final String symbol, final InfluencerSubscription subscription) {
    final Balance balance = new Balance();
    balance.setLastUpdated(System.currentTimeMillis());
    try {
      final AccountService accountService = this.exchange.getAccountService();
      if (accountService == null)
        return balance;
      if (accountService.getAccountInfo().getWallets() == null) {
        return balance;
      }
      Wallet wallet = null;
      if (futures) {
        wallet = accountService.getAccountInfo().getWallets().get("futures");
      } else {
        wallet = accountService.getAccountInfo().getWallets().get("trading");
      }
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

  private static int getPrecision(String minValue) {
    if (minValue.contains(".")) {
      return minValue.substring(minValue.indexOf(".") + 1).length();
    } else {
      return 0;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class OkexExchangeInfoFull {
    @JsonProperty("data")
    private List<OkexSymbol> symbols;

    public List<OkexSymbol> getSymbols() {
      return symbols;
    }

    public void setSymbols(List<OkexSymbol> symbols) {
      this.symbols = symbols;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class OkexSymbol {
    @JsonProperty("instType")
    private String instrumentType;
    @JsonProperty("baseCcy")
    private String baseAsset;
    @JsonProperty("quoteCcy")
    private String quoteAsset;
    @JsonProperty("state")
    private String status;
    @JsonProperty("minSz")
    private String baseAssetPrecision;
    @JsonProperty("tickSz")
    private String quotePrecision;
    @JsonProperty("ctValCcy")
    private String valueCurrency;
    @JsonProperty("settleCcy")
    private String settleCurrency;

    public String getInstrumentType() {
      return instrumentType;
    }

    public void setInstrumentType(String instrumentType) {
      this.instrumentType = instrumentType;
    }

    public String getBaseAsset() {
      return baseAsset;
    }

    public void setBaseAsset(String baseAsset) {
      this.baseAsset = baseAsset;
    }

    public String getQuoteAsset() {
      return quoteAsset;
    }

    public void setQuoteAsset(String quoteAsset) {
      this.quoteAsset = quoteAsset;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getBaseAssetPrecision() {
      return baseAssetPrecision;
    }

    public void setBaseAssetPrecision(String baseAssetPrecision) {
      this.baseAssetPrecision = baseAssetPrecision;
    }

    public String getQuotePrecision() {
      return quotePrecision;
    }

    public void setQuotePrecision(String quotePrecision) {
      this.quotePrecision = quotePrecision;
    }

    public String getValueCurrency() {
      return valueCurrency;
    }

    public void setValueCurrency(String valueCurrency) {
      this.valueCurrency = valueCurrency;
    }

    public String getSettleCurrency() {
      return settleCurrency;
    }

    public void setSettleCurrency(String settleCurrency) {
      this.settleCurrency = settleCurrency;
    }
  }

}
