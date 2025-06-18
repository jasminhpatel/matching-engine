package com.solfini.matchengine.copytrade.xchangewrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.copytrade.ExternalExchangeUtil;
import com.solfini.util.HttpUtils;
import org.knowm.xchange.Exchange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class XBybitExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XBybitExchange.class);
  private final ObjectMapper mapper = new ObjectMapper();

  public XBybitExchange(Exchange exchange) {
    super(exchange);
  }

  @Override
  public List<SymbolStatus> getExchangeInstrumentsFull() {
    List<SymbolStatus> symbolStatuses = new ArrayList<>();
    final String apiUrl = exchange.getExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/v5/market/instruments-info?category=spot", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final ByBitExchangeInfoFull info = mapper.readValue(response.getData(), ByBitExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getResult() != null && info.getResult().getList() != null) {
          for (ByBitSymbol byBitSymbol : info.getResult().getList()){
            final SymbolStatus symbolStatus = new SymbolStatus();
            symbolStatus.setExchange("bybit");
            symbolStatus.setBase(byBitSymbol.getBaseCoin());
            symbolStatus.setQuote(byBitSymbol.getQuoteCoin());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(false);
            symbolStatus.setTradable("Trading".equals(byBitSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            //symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(getPrecision(byBitSymbol.getLotSizeFilter().getQuotePrecision()));
            symbolStatus.setQtyScale(getPrecision(byBitSymbol.getLotSizeFilter().getBasePrecision()));
            symbolStatuses.add(symbolStatus);
          }
        }
      } catch (JsonProcessingException e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }
    response = HttpUtils.get(apiUrl + "/v5/market/instruments-info?category=linear", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final ByBitExchangeInfoFull info = mapper.readValue(response.getData(), ByBitExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getResult() != null && info.getResult().getList() != null) {
          for (ByBitSymbol byBitSymbol : info.getResult().getList()){
            if (!"LinearPerpetual".equalsIgnoreCase(byBitSymbol.getContractType())) {
              continue;
            }
            final SymbolStatus symbolStatus = new SymbolStatus();
            symbolStatus.setExchange("bybit");
            symbolStatus.setBase(byBitSymbol.getBaseCoin());
            symbolStatus.setQuote(byBitSymbol.getQuoteCoin());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(true);
            symbolStatus.setTradable("Trading".equals(byBitSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            //symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(getPrecision(byBitSymbol.getLotSizeFilter().getQuotePrecision()));
            symbolStatus.setQtyScale(getPrecision(byBitSymbol.getLotSizeFilter().getBasePrecision()));
            symbolStatuses.add(symbolStatus);
          }
        }
      } catch (JsonProcessingException e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }
    /*response = HttpUtils.get(apiUrl + "/v5/market/instruments-info?category=inverse", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final ByBitExchangeInfoFull info = mapper.readValue(response.getData(), ByBitExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getResult() != null && info.getResult().getList() != null) {
          for (ByBitSymbol byBitSymbol : info.getResult().getList()){
            if (!"InversePerpetual".equalsIgnoreCase(byBitSymbol.getContractType())) {
              continue;
            }
            final SymbolStatus symbolStatus = new SymbolStatus();
            symbolStatus.setExchange("bybit");
            symbolStatus.setBase(byBitSymbol.getBaseCoin());
            symbolStatus.setQuote(byBitSymbol.getQuoteCoin());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(true);
            symbolStatus.setTradable("Trading".equals(byBitSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            //symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(getPrecision(byBitSymbol.getLotSizeFilter().getQuotePrecision()));
            symbolStatus.setQtyScale(getPrecision(byBitSymbol.getLotSizeFilter().getBasePrecision()));
            symbolStatuses.add(symbolStatus);
          }
        }
      } catch (JsonProcessingException e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }*/
    return symbolStatuses;
  }

  private static int getPrecision(String minValue) {
    if (minValue != null && minValue.contains(".")) {
      return minValue.substring(minValue.indexOf(".") + 1).length();
    } else {
      return 0;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitExchangeInfoFull {
    private int retCode;
    private ByBitResult result;

    public int getRetCode() {
      return retCode;
    }

    public void setRetCode(int retCode) {
      this.retCode = retCode;
    }

    public ByBitResult getResult() {
      return result;
    }

    public void setResult(ByBitResult result) {
      this.result = result;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitResult {
    private List<ByBitSymbol> list;
    private String category;

    public List<ByBitSymbol> getList() {
      return list;
    }

    public void setList(List<ByBitSymbol> list) {
      this.list = list;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitSymbol {
    private String symbol;
    private String baseCoin;
    private String quoteCoin;
    private String status;
    private String contractType;

    private String marginTrading;
    private ByBitSymbolLotSizeFilter lotSizeFilter;

    public String getSymbol() {
      return symbol;
    }

    public void setSymbol(String symbol) {
      this.symbol = symbol;
    }

    public String getBaseCoin() {
      return baseCoin;
    }

    public void setBaseCoin(String baseCoin) {
      this.baseCoin = baseCoin;
    }

    public String getQuoteCoin() {
      return quoteCoin;
    }

    public void setQuoteCoin(String quoteCoin) {
      this.quoteCoin = quoteCoin;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getContractType() {
      return contractType;
    }

    public void setContractType(String contractType) {
      this.contractType = contractType;
    }

    public String getMarginTrading() {
      return marginTrading;
    }

    public void setMarginTrading(String marginTrading) {
      this.marginTrading = marginTrading;
    }

    public ByBitSymbolLotSizeFilter getLotSizeFilter() {
      return lotSizeFilter;
    }

    public void setLotSizeFilter(ByBitSymbolLotSizeFilter lotSizeFilter) {
      this.lotSizeFilter = lotSizeFilter;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitSymbolLotSizeFilter {
    private String basePrecision;
    private String quotePrecision;
    private String marginTrading;
    private String minOrderQty;
    private String maxOrderQty;
    private String minOrderAmt;
    private String maxOrderAmt;

    public String getBasePrecision() {
      return basePrecision;
    }

    public void setBasePrecision(String basePrecision) {
      this.basePrecision = basePrecision;
    }

    public String getQuotePrecision() {
      return quotePrecision;
    }

    public void setQuotePrecision(String quotePrecision) {
      this.quotePrecision = quotePrecision;
    }

    public String getMarginTrading() {
      return marginTrading;
    }

    public void setMarginTrading(String marginTrading) {
      this.marginTrading = marginTrading;
    }

    public String getMinOrderQty() {
      return minOrderQty;
    }

    public void setMinOrderQty(String minOrderQty) {
      this.minOrderQty = minOrderQty;
    }

    public String getMaxOrderQty() {
      return maxOrderQty;
    }

    public void setMaxOrderQty(String maxOrderQty) {
      this.maxOrderQty = maxOrderQty;
    }

    public String getMinOrderAmt() {
      return minOrderAmt;
    }

    public void setMinOrderAmt(String minOrderAmt) {
      this.minOrderAmt = minOrderAmt;
    }

    public String getMaxOrderAmt() {
      return maxOrderAmt;
    }

    public void setMaxOrderAmt(String maxOrderAmt) {
      this.maxOrderAmt = maxOrderAmt;
    }
  }

  public static void main(String[] args) {
    final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly("BYBIT");
    List<SymbolStatus> symbols = exchange.getExchangeInstrumentsFull();
    System.out.println("Done");
  }
}
