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

public class XMEXCExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XMEXCExchange.class);
  private final ObjectMapper mapper = new ObjectMapper();

  public XMEXCExchange(Exchange exchange) {
    super(exchange);
  }

  public List<SymbolStatus> getExchangeInstrumentsFull() {
    final String apiUrl = exchange.getExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/api/v3/exchangeInfo", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final MEXCExchangeInfoFull info = mapper.readValue(response.getData(), MEXCExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          List<SymbolStatus> symbolStatuses = new ArrayList<>(info.getSymbols().size());
          for (MEXCSymbol mexcSymbol : info.getSymbols()){
            final SymbolStatus symbolStatus = new SymbolStatus();
            symbolStatus.setExchange("mexc");
            symbolStatus.setBase(mexcSymbol.getBaseAsset());
            symbolStatus.setQuote(mexcSymbol.getQuoteAsset());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(mexcSymbol.getPermissions() != null && mexcSymbol.getPermissions().contains("FUTURES"));
            symbolStatus.setTradable("1".equals(mexcSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            //symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(mexcSymbol.getQuotePrecision());
            symbolStatus.setQtyScale(mexcSymbol.getBaseAssetPrecision());
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

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MEXCExchangeInfoFull {
    private List<MEXCSymbol> symbols;

    public List<MEXCSymbol> getSymbols() {
      return symbols;
    }

    public void setSymbols(List<MEXCSymbol> symbols) {
      this.symbols = symbols;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MEXCSymbol {
    private String symbol;
    private String baseAsset;
    private String quoteAsset;
    private String status;
    private int baseAssetPrecision;
    private int quotePrecision;
    private List<String> permissions;

    public MEXCSymbol() {
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

    public List<String> getPermissions() {
      return permissions;
    }

    public void setPermissions(List<String> permissions) {
      this.permissions = permissions;
    }
  }

  public static void main(String[] args) {
    final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly("MEXC");
    List<SymbolStatus> symbols = exchange.getExchangeInstrumentsFull();
    System.out.println("Done");
  }
}
