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
          for (ByBitSymbol byBitSymbol : info.getResult().getList()) {
            final SymbolStatus symbolStatus = new SymbolStatus();
            symbolStatus.setExchange("bybit");
            symbolStatus.setBase(byBitSymbol.getBaseCoin());
            symbolStatus.setQuote(byBitSymbol.getQuoteCoin());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(false);
            symbolStatus.setTradable("Trading".equals(byBitSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            // symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(getPrecision(byBitSymbol.getPriceFilter().getTickSize()));
            symbolStatus.setQtyScale(getPrecision(byBitSymbol.getLotSizeFilter().getBasePrecision()));
            symbolStatuses.add(symbolStatus);
          }
        }
      } catch (JsonProcessingException e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }
    response = HttpUtils.get(apiUrl + "/v5/market/instruments-info?category=linear&limit=1000", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final ByBitExchangeInfoFull info = mapper.readValue(response.getData(), ByBitExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getResult() != null && info.getResult().getList() != null) {
          for (ByBitSymbol byBitSymbol : info.getResult().getList()) {
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
            // symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(getPrecision(byBitSymbol.getPriceFilter().getTickSize()));
            symbolStatus.setQtyScale(getPrecision(byBitSymbol.getLotSizeFilter().getQtyStep()));
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

    public final int getRetCode() {
      return retCode;
    }

    public final void setRetCode(final int retCode) {
      this.retCode = retCode;
    }

    public final ByBitResult getResult() {
      return result;
    }

    public final void setResult(final ByBitResult result) {
      this.result = result;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitResult {
    private List<ByBitSymbol> list;
    private String category;

    public final List<ByBitSymbol> getList() {
      return list;
    }

    public final void setList(final List<ByBitSymbol> list) {
      this.list = list;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(final String category) {
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
    private ByBitPriceFilter priceFilter;

    public final String getSymbol() {
      return symbol;
    }

    public final void setSymbol(final String symbol) {
      this.symbol = symbol;
    }

    public final String getBaseCoin() {
      return baseCoin;
    }

    public final void setBaseCoin(final String baseCoin) {
      this.baseCoin = baseCoin;
    }

    public final String getQuoteCoin() {
      return quoteCoin;
    }

    public final void setQuoteCoin(final String quoteCoin) {
      this.quoteCoin = quoteCoin;
    }

    public final String getStatus() {
      return status;
    }

    public final void setStatus(final String status) {
      this.status = status;
    }

    public final String getContractType() {
      return contractType;
    }

    public final void setContractType(final String contractType) {
      this.contractType = contractType;
    }

    public final String getMarginTrading() {
      return marginTrading;
    }

    public final void setMarginTrading(final String marginTrading) {
      this.marginTrading = marginTrading;
    }

    public final ByBitSymbolLotSizeFilter getLotSizeFilter() {
      return lotSizeFilter;
    }

    public final void setLotSizeFilter(final ByBitSymbolLotSizeFilter lotSizeFilter) {
      this.lotSizeFilter = lotSizeFilter;
    }

    public final ByBitPriceFilter getPriceFilter() {
      return priceFilter;
    }

    public final void setPriceFilter(final ByBitPriceFilter priceFilter) {
      this.priceFilter = priceFilter;
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
    private String qtyStep;

    public final String getBasePrecision() {
      return basePrecision;
    }

    public final void setBasePrecision(final String basePrecision) {
      this.basePrecision = basePrecision;
    }

    public final String getQuotePrecision() {
      return quotePrecision;
    }

    public final void setQuotePrecision(final String quotePrecision) {
      this.quotePrecision = quotePrecision;
    }

    public final String getMarginTrading() {
      return marginTrading;
    }

    public final void setMarginTrading(final String marginTrading) {
      this.marginTrading = marginTrading;
    }

    public final String getMinOrderQty() {
      return minOrderQty;
    }

    public final void setMinOrderQty(final String minOrderQty) {
      this.minOrderQty = minOrderQty;
    }

    public final String getMaxOrderQty() {
      return maxOrderQty;
    }

    public final void setMaxOrderQty(final String maxOrderQty) {
      this.maxOrderQty = maxOrderQty;
    }

    public final String getMinOrderAmt() {
      return minOrderAmt;
    }

    public final void setMinOrderAmt(final String minOrderAmt) {
      this.minOrderAmt = minOrderAmt;
    }

    public final String getMaxOrderAmt() {
      return maxOrderAmt;
    }

    public final void setMaxOrderAmt(final String maxOrderAmt) {
      this.maxOrderAmt = maxOrderAmt;
    }

    public final String getQtyStep() {
      return qtyStep;
    }

    public final void setQtyStep(final String qtyStep) {
      this.qtyStep = qtyStep;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitPriceFilter {
    private String minPrice;
    private String maxPrice;
    private String tickSize;

    public final String getMinPrice() {
      return minPrice;
    }

    public final void setMinPrice(final String minPrice) {
      this.minPrice = minPrice;
    }

    public final String getMaxPrice() {
      return maxPrice;
    }

    public final void setMaxPrice(final String maxPrice) {
      this.maxPrice = maxPrice;
    }

    public final String getTickSize() {
      return tickSize;
    }

    public final void setTickSize(final String tickSize) {
      this.tickSize = tickSize;
    }
  }

  // ---------- Position POJOs ----------
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BybitPositionsResponse {
    private int retCode;
    private String retMsg;
    private BybitPositionsResult result;
    private long time;

    public final int getRetCode() {
      return retCode;
    }

    public final void setRetCode(int retCode) {
      this.retCode = retCode;
    }

    public final String getRetMsg() {
      return retMsg;
    }

    public final void setRetMsg(String retMsg) {
      this.retMsg = retMsg;
    }

    public final BybitPositionsResult getResult() {
      return result;
    }

    public final void setResult(BybitPositionsResult result) {
      this.result = result;
    }

    public final long getTime() {
      return time;
    }

    public final void setTime(long time) {
      this.time = time;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BybitPositionsResult {
    private String category;
    private List<BybitPosition> list;

    public final String getCategory() {
      return category;
    }

    public final void setCategory(String category) {
      this.category = category;
    }

    public final List<BybitPosition> getList() {
      return list;
    }

    public final void setList(List<BybitPosition> list) {
      this.list = list;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BybitPosition {
    private String symbol;
    private String side;
    private String size;
    private String entryPrice;
    private String markPrice;
    private String leverage;
    private String positionValue;
    private String positionStatus;
    private String positionIdx;
    private String positionIM;
    private String positionMM;
    private String takeProfit;
    private String stopLoss;

    public final String getSymbol() {
      return symbol;
    }

    public final void setSymbol(String symbol) {
      this.symbol = symbol;
    }

    public final String getSide() {
      return side;
    }

    public final void setSide(String side) {
      this.side = side;
    }

    public final String getSize() {
      return size;
    }

    public final void setSize(String size) {
      this.size = size;
    }

    public final String getEntryPrice() {
      return entryPrice;
    }

    public final void setEntryPrice(String entryPrice) {
      this.entryPrice = entryPrice;
    }

    public final String getMarkPrice() {
      return markPrice;
    }

    public final void setMarkPrice(String markPrice) {
      this.markPrice = markPrice;
    }

    public final String getLeverage() {
      return leverage;
    }

    public final void setLeverage(String leverage) {
      this.leverage = leverage;
    }

    public final String getPositionValue() {
      return positionValue;
    }

    public final void setPositionValue(String positionValue) {
      this.positionValue = positionValue;
    }

    public final String getPositionStatus() {
      return positionStatus;
    }

    public final void setPositionStatus(String positionStatus) {
      this.positionStatus = positionStatus;
    }

    public final String getPositionIdx() {
      return positionIdx;
    }

    public final void setPositionIdx(String positionIdx) {
      this.positionIdx = positionIdx;
    }

    public final String getPositionIM() {
      return positionIM;
    }

    public final void setPositionIM(String positionIM) {
      this.positionIM = positionIM;
    }

    public final String getPositionMM() {
      return positionMM;
    }

    public final void setPositionMM(String positionMM) {
      this.positionMM = positionMM;
    }

    public final String getTakeProfit() {
      return takeProfit;
    }

    public final void setTakeProfit(String takeProfit) {
      this.takeProfit = takeProfit;
    }

    public final String getStopLoss() {
      return stopLoss;
    }

    public final void setStopLoss(String stopLoss) {
      this.stopLoss = stopLoss;
    }
  }

  public static void main(String[] args) {
    final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly("BYBIT");
    List<SymbolStatus> symbols = exchange.getExchangeInstrumentsFull();
    System.out.println("Done");
  }
}
