package com.solfini.matchengine.copytrade.xchangewrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.copytrade.CopyTrade;
import com.solfini.matchengine.copytrade.ExternalExchangeUtil;
import com.solfini.matchengine.copytrade.InfluencerSubscription;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.HttpUtils;
import com.solfini.util.StringUtil;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class XBinanceExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XBinanceExchange.class);
  private final ObjectMapper mapper = new ObjectMapper();

  public XBinanceExchange(final Exchange exchange) {
    super(exchange);
  }

  public Balance getBalanceFromExchange(final String quoteCurrency, final InfluencerSubscription subscription) {
    //todo margin balance logic
    return super.getBalanceFromExchange(quoteCurrency, subscription);
  }

  public double getPriceFromExchange(final Instrument currencyPair, final Side side) {
    //todo price in futures
    return super.getPriceFromExchange(currencyPair, side);
  }

  public void placeOrder(final CopyTrade copyTrade) throws Exception {
/*    if (copyTrade.getSubscription().hasLeverage()) {
      processMarginOrder(copyTrade);
    } else {
      super.placeOrder(copyTrade);
    }*/
    super.placeOrder(copyTrade);
  }

  public void updateOrderStatus(final CopyTrade copyTrade) throws Exception {
/*    if (copyTrade.getSubscription().hasLeverage()) {
      updateMarginOrderStatus(copyTrade);
    } else {
      super.updateOrderStatus(copyTrade);
    }*/
    super.updateOrderStatus(copyTrade);
  }

  @Override
  public List<SymbolStatus> getExchangeInstrumentsFull() {
    final List<SymbolStatus> symbolStatuses = new ArrayList<>();
    final String apiUrl = exchange.getExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/api/v3/exchangeInfo", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final BinanceExchangeInfoFull info = mapper.readValue(response.getData(), BinanceExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          for (BinanceSymbol binanceSymbol : info.getSymbols()){
            final SymbolStatus symbolStatus = new SymbolStatus();
            symbolStatus.setExchange("binance");
            symbolStatus.setBase(binanceSymbol.getBaseAsset());
            symbolStatus.setQuote(binanceSymbol.getQuoteAsset());
            symbolStatus.setPrompt("");
            symbolStatus.setTradable("TRADING".equals(binanceSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            //symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(binanceSymbol.getQuotePrecision());
            symbolStatus.setQtyScale(binanceSymbol.getBaseAssetPrecision());
            for (Filter filter : binanceSymbol.getFilters()) {
              if ("PRICE_FILTER".equals(filter.getFilterType())) {
                int priceScale = (int) -Math.log10(StringUtil.toDouble(filter.tickSize));
                if (priceScale >= 0) {
                  symbolStatus.setPriceScale(priceScale);
                }
              } else if ("LOT_SIZE".equals(filter.getFilterType())) {
                int qtyScale = (int) -Math.log10(StringUtil.toDouble(filter.stepSize));
                if (qtyScale >= 0) {
                  symbolStatus.setQtyScale(qtyScale);
                }
              }
            }
            symbolStatuses.add(symbolStatus);
          }
        }
      } catch (Exception e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }
    response = HttpUtils.get(BinanceExchange.FUTURES_URL + "/fapi/v1/exchangeInfo", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final BinanceExchangeInfoFull info = mapper.readValue(response.getData(), BinanceExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          for (BinanceSymbol binanceSymbol : info.getSymbols()){
            final SymbolStatus symbolStatus = new SymbolStatus();
            symbolStatus.setExchange("binance");
            symbolStatus.setBase(binanceSymbol.getBaseAsset());
            symbolStatus.setQuote(binanceSymbol.getQuoteAsset());
            symbolStatus.setPrompt(binanceSymbol.getPrompt());
            symbolStatus.setTradable(true);
            symbolStatus.setFutures(true);
            symbolStatus.setUpdated(updated);
            //symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(binanceSymbol.getPricePrecision());
            symbolStatus.setQtyScale(binanceSymbol.getQuantityPrecision());
            symbolStatuses.add(symbolStatus);
          }
        }
      } catch (Exception e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }
    return symbolStatuses;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BinanceExchangeInfoFull {
    private List<BinanceSymbol> symbols;

    public List<BinanceSymbol> getSymbols() {
      return symbols;
    }

    public void setSymbols(List<BinanceSymbol> symbols) {
      this.symbols = symbols;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BinanceSymbol {
    private String symbol;
    private String baseAsset;
    private String quoteAsset;
    private String status;
    private String prompt;
    private int baseAssetPrecision;
    private int quotePrecision;
    private int pricePrecision;
    private int quantityPrecision;
    //private List<List<String>> permissionSets;
    private List<Filter> filters;

    public BinanceSymbol() {
    }

    public String getSymbol() {
      return symbol;
    }

    public void setSymbol(String symbol) {
      this.symbol = symbol;
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

    public String getPrompt() {
      return prompt;
    }

    public void setPrompt(String prompt) {
      this.prompt = prompt;
    }

    public int getBaseAssetPrecision() {
      return baseAssetPrecision;
    }

    public void setBaseAssetPrecision(int baseAssetPrecision) {
      this.baseAssetPrecision = baseAssetPrecision;
    }

    public int getQuotePrecision() {
      return quotePrecision;
    }

    public void setQuotePrecision(int quotePrecision) {
      this.quotePrecision = quotePrecision;
    }

    //public List<List<String>> getPermissionSets() {
    //  return permissionSets;
    //}

    //public void setPermissionSets(List<List<String>> permissionSets) {
    //  this.permissionSets = permissionSets;
    //}

    public int getPricePrecision() {
      return pricePrecision;
    }

    public void setPricePrecision(int pricePrecision) {
      this.pricePrecision = pricePrecision;
    }

    public int getQuantityPrecision() {
      return quantityPrecision;
    }

    public void setQuantityPrecision(int quantityPrecision) {
      this.quantityPrecision = quantityPrecision;
    }

    public List<Filter> getFilters() {
      return filters;
    }

    public void setFilters(List<Filter> filters) {
      this.filters = filters;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Filter {
    private String filterType;
    private String minPrice;
    private String tickSize;
    private String minQty;
    private String stepSize;

    public String getFilterType() {
      return filterType;
    }

    public void setFilterType(String filterType) {
      this.filterType = filterType;
    }

    public String getMinPrice() {
      return minPrice;
    }

    public void setMinPrice(String minPrice) {
      this.minPrice = minPrice;
    }

    public String getTickSize() {
      return tickSize;
    }

    public void setTickSize(String tickSize) {
      this.tickSize = tickSize;
    }

    public String getMinQty() {
      return minQty;
    }

    public void setMinQty(String minQty) {
      this.minQty = minQty;
    }

    public String getStepSize() {
      return stepSize;
    }

    public void setStepSize(String stepSize) {
      this.stepSize = stepSize;
    }
  }

  public static void main(String[] args) {
    final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly("BINANCE");
    List<SymbolStatus> symbols = exchange.getExchangeInstrumentsFull();
    System.out.println("Done");
  }

}
