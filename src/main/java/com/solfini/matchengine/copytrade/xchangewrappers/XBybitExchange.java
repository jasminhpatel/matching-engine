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
    final String apiUrl = exchange.getExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/spot/v3/public/symbols", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final ByBitExchangeInfoFull info = mapper.readValue(response.getData(), ByBitExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getResult() != null && info.getResult().getList() != null) {
          List<SymbolStatus> symbolStatuses = new ArrayList<>(info.getResult().getList().size());
          for (ByBitSymbol byBitSymbol : info.getResult().getList()){
            final SymbolStatus symbolStatus = new SymbolStatus();
            symbolStatus.setExchange("bybit");
            symbolStatus.setBase(byBitSymbol.getBaseCoin());
            symbolStatus.setQuote(byBitSymbol.getQuoteCoin());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(!"1".equals(byBitSymbol.getCategory()));
            symbolStatus.setTradable("1".equals(byBitSymbol.getShowStatus()));
            symbolStatus.setUpdated(updated);
            //symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(getPrecision(byBitSymbol.getMinPricePrecision()));
            symbolStatus.setQtyScale(getPrecision(byBitSymbol.getBasePrecision()));
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

  private static int getPrecision(String minValue) {
    if (minValue.contains(".")) {
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

    public List<ByBitSymbol> getList() {
      return list;
    }

    public void setList(List<ByBitSymbol> list) {
      this.list = list;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ByBitSymbol {
    private String name;
    private String baseCoin;
    private String quoteCoin;
    private String showStatus;
    private String basePrecision;
    private String minPricePrecision;
    private String category;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
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

    public String getShowStatus() {
      return showStatus;
    }

    public void setShowStatus(String showStatus) {
      this.showStatus = showStatus;
    }

    public String getBasePrecision() {
      return basePrecision;
    }

    public void setBasePrecision(String basePrecision) {
      this.basePrecision = basePrecision;
    }

    public String getMinPricePrecision() {
      return minPricePrecision;
    }

    public void setMinPricePrecision(String minPricePrecision) {
      this.minPricePrecision = minPricePrecision;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }
  }

  public static void main(String[] args) {
    final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly("BYBIT");
    List<SymbolStatus> symbols = exchange.getExchangeInstrumentsFull();
    System.out.println("Done");
  }
}
