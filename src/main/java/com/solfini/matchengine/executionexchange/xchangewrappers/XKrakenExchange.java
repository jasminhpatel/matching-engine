package com.solfini.matchengine.executionexchange.xchangewrappers;

import static com.solfini.common.Constants.TARDIS_SPOT;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.util.HttpUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.Exchange;

public class XKrakenExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XKrakenExchange.class);
  private final ObjectMapper mapper = new ObjectMapper();

  public XKrakenExchange(final Exchange exchange) {
    super(exchange);
  }

  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    final String apiUrl = exchange.getExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/0/public/AssetPairs", new HashMap<>()
        , ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);
    if (response != null && response.getCode() == 200) {
      try {
        final KrakenExchangeInfoFull info = mapper.readValue(response.getData(), KrakenExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getResult() != null) {
          List<ExternalSymbol> symbolStatuses = new ArrayList<>();
          for (Map.Entry<String, KrakenTradingPair> entry : info.getResult().entrySet()) {
            String pairName = entry.getKey();
            KrakenTradingPair krakenPair = entry.getValue();

            ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("kraken");
            symbolStatus.setFutures(false);
            symbolStatus.setInstrumentType(TARDIS_SPOT);
            symbolStatus.setUpdated(updated);
            symbolStatus.setSymbol(krakenPair.getAltname());
            symbolStatus.setBase(normalizeBaseCurrency(krakenPair.getBase()));
            symbolStatus.setQuote(normalizeQuoteCurrency(krakenPair.getQuote()));
            symbolStatus.setPrompt(pairName);
            symbolStatus.setTradable("online".equalsIgnoreCase(krakenPair.getStatus()));
            symbolStatus.setPriceScale(krakenPair.getPairDecimals() != null ? krakenPair.getPairDecimals() : 4);
            symbolStatus.setQtyScale(krakenPair.getLotDecimals() != null ? krakenPair.getLotDecimals() : 8);

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

  private static final String normalizeQuoteCurrency(final String krakenQuote) {
    if (krakenQuote == null)
      return null;

    switch (krakenQuote.toLowerCase()) {
      case "zusd":
        return "USD";
      case "zeur":
        return "EUR";
      case "zgbp":
        return "GBP";
      case "zcad":
        return "CAD";
      case "zjpy":
        return "JPY";
      case "zaud":
        return "AUD";
      case "xeth":
        return "ETH";
      case "xbt":
        return "BTC";
      case "xxbt":
        return "BTC";
      default:
        if (krakenQuote.startsWith("Z") || krakenQuote.startsWith("X")) {
          return krakenQuote.substring(1);
        }
        return krakenQuote;
    }
  }

  private static final String normalizeBaseCurrency(final String krakenBase) {
    if (krakenBase == null)
      return null;

    switch (krakenBase.toLowerCase()) {
      case "xbt":
        return "BTC";
      case "xxbt":
        return "BTC";
      default:
        // Remove X prefix if present (for crypto currencies)
        if (krakenBase.startsWith("X") && krakenBase.length() > 1) {
          return krakenBase.substring(1);
        }
        return krakenBase;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class KrakenExchangeInfoFull {
    @JsonProperty("error")
    private List<String> error;

    @JsonProperty("result")
    private Map<String, KrakenTradingPair> result;

    // Constructors
    public KrakenExchangeInfoFull() {}

    // Getters and Setters
    public List<String> getError() {
      return error;
    }

    public void setError(List<String> error) {
      this.error = error;
    }

    public Map<String, KrakenTradingPair> getResult() {
      return result;
    }

    public void setResult(Map<String, KrakenTradingPair> result) {
      this.result = result;
    }

    @Override
    public String toString() {
      return "KrakenExchangeInfoFull{" + "error=" + error + ", result=" + result + '}';
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class KrakenTradingPair {
    @JsonProperty("altname")
    private String altname;

    @JsonProperty("wsname")
    private String wsname;

    @JsonProperty("aclass_base")
    private String aclassBase;

    @JsonProperty("base")
    private String base;

    @JsonProperty("aclass_quote")
    private String aclassQuote;

    @JsonProperty("quote")
    private String quote;

    @JsonProperty("lot")
    private String lot;

    @JsonProperty("cost_decimals")
    private Integer costDecimals;

    @JsonProperty("pair_decimals")
    private Integer pairDecimals;

    @JsonProperty("lot_decimals")
    private Integer lotDecimals;

    @JsonProperty("lot_multiplier")
    private Integer lotMultiplier;

    @JsonProperty("leverage_buy")
    private List<Object> leverageBuy;

    @JsonProperty("leverage_sell")
    private List<Object> leverageSell;

    @JsonProperty("fees")
    private List<List<Double>> fees;

    @JsonProperty("fees_maker")
    private List<List<Double>> feesMaker;

    @JsonProperty("fee_volume_currency")
    private String feeVolumeCurrency;

    @JsonProperty("margin_call")
    private Integer marginCall;

    @JsonProperty("margin_stop")
    private Integer marginStop;

    @JsonProperty("ordermin")
    private String ordermin;

    @JsonProperty("costmin")
    private String costmin;

    @JsonProperty("tick_size")
    private String tickSize;

    @JsonProperty("status")
    private String status;

    // Constructors
    public KrakenTradingPair() {}

    // Getters and Setters
    public String getAltname() {
      return altname;
    }

    public void setAltname(String altname) {
      this.altname = altname;
    }

    public String getWsname() {
      return wsname;
    }

    public void setWsname(String wsname) {
      this.wsname = wsname;
    }

    public String getAclassBase() {
      return aclassBase;
    }

    public void setAclassBase(String aclassBase) {
      this.aclassBase = aclassBase;
    }

    public String getBase() {
      return base;
    }

    public void setBase(String base) {
      this.base = base;
    }

    public String getAclassQuote() {
      return aclassQuote;
    }

    public void setAclassQuote(String aclassQuote) {
      this.aclassQuote = aclassQuote;
    }

    public String getQuote() {
      return quote;
    }

    public void setQuote(String quote) {
      this.quote = quote;
    }

    public String getLot() {
      return lot;
    }

    public void setLot(String lot) {
      this.lot = lot;
    }

    public Integer getCostDecimals() {
      return costDecimals;
    }

    public void setCostDecimals(Integer costDecimals) {
      this.costDecimals = costDecimals;
    }

    public Integer getPairDecimals() {
      return pairDecimals;
    }

    public void setPairDecimals(Integer pairDecimals) {
      this.pairDecimals = pairDecimals;
    }

    public Integer getLotDecimals() {
      return lotDecimals;
    }

    public void setLotDecimals(Integer lotDecimals) {
      this.lotDecimals = lotDecimals;
    }

    public Integer getLotMultiplier() {
      return lotMultiplier;
    }

    public void setLotMultiplier(Integer lotMultiplier) {
      this.lotMultiplier = lotMultiplier;
    }

    public List<Object> getLeverageBuy() {
      return leverageBuy;
    }

    public void setLeverageBuy(List<Object> leverageBuy) {
      this.leverageBuy = leverageBuy;
    }

    public List<Object> getLeverageSell() {
      return leverageSell;
    }

    public void setLeverageSell(List<Object> leverageSell) {
      this.leverageSell = leverageSell;
    }

    public List<List<Double>> getFees() {
      return fees;
    }

    public void setFees(List<List<Double>> fees) {
      this.fees = fees;
    }

    public List<List<Double>> getFeesMaker() {
      return feesMaker;
    }

    public void setFeesMaker(List<List<Double>> feesMaker) {
      this.feesMaker = feesMaker;
    }

    public String getFeeVolumeCurrency() {
      return feeVolumeCurrency;
    }

    public void setFeeVolumeCurrency(String feeVolumeCurrency) {
      this.feeVolumeCurrency = feeVolumeCurrency;
    }

    public Integer getMarginCall() {
      return marginCall;
    }

    public void setMarginCall(Integer marginCall) {
      this.marginCall = marginCall;
    }

    public Integer getMarginStop() {
      return marginStop;
    }

    public void setMarginStop(Integer marginStop) {
      this.marginStop = marginStop;
    }

    public String getOrdermin() {
      return ordermin;
    }

    public void setOrdermin(String ordermin) {
      this.ordermin = ordermin;
    }

    public String getCostmin() {
      return costmin;
    }

    public void setCostmin(String costmin) {
      this.costmin = costmin;
    }

    public String getTickSize() {
      return tickSize;
    }

    public void setTickSize(String tickSize) {
      this.tickSize = tickSize;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    @Override
    public String toString() {
      return "KrakenTradingPair{" + "altname='" + altname + '\'' + ", wsname='" + wsname + '\'' + ", base='" + base + '\'' + ", quote='"
          + quote + '\'' + ", status='" + status + '\'' + ", ordermin='" + ordermin + '\'' + ", costmin='" + costmin + '\'' + '}';
    }
  }

  public static void main(String[] args) {
    final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly("KRAKEN");
    List<ExternalSymbol> symbols = exchange.getExchangeInstrumentsFull();
    System.out.println("Done");
  }
}
