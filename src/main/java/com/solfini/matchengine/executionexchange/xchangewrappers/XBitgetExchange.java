package com.solfini.matchengine.executionexchange.xchangewrappers;

import static com.solfini.common.Constants.TARDIS_SPOT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget.BitgetRestClient.BitgetExchangeInfoFull;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget.BitgetRestClient.BitgetSymbolInfo;
import com.solfini.util.HttpUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.knowm.xchange.Exchange;

public class XBitgetExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XBitgetExchange.class);
  private final ObjectMapper mapper = new ObjectMapper();

  public XBitgetExchange(final Exchange exchange) {
    super(exchange);
  }

  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    final String apiUrl = exchange.getExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/api/v2/spot/public/symbols", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final BitgetExchangeInfoFull info = mapper.readValue(response.getData(), BitgetExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          List<ExternalSymbol> symbolStatuses = new ArrayList<>();
          for (BitgetSymbolInfo bitgetSymbolInfo : info.getSymbols()) {
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("bitget");
            symbolStatus.setSymbol(bitgetSymbolInfo.getSymbol());
            symbolStatus.setBase(bitgetSymbolInfo.getBaseCoin());
            symbolStatus.setQuote(bitgetSymbolInfo.getQuoteCoin());
            symbolStatus.setPrompt(bitgetSymbolInfo.getSymbol());
            symbolStatus.setFutures(false);
            symbolStatus.setInstrumentType(TARDIS_SPOT);
            symbolStatus.setTradable("online".equalsIgnoreCase(bitgetSymbolInfo.getStatus()));
            try {
              symbolStatus.setPriceScale(Integer.parseInt(bitgetSymbolInfo.getPricePrecision()));
            } catch (NumberFormatException e) {
              symbolStatus.setPriceScale(8);
            }
            try {
              symbolStatus.setQtyScale(Integer.parseInt(bitgetSymbolInfo.getQuantityPrecision()));
            } catch (NumberFormatException e) {
              symbolStatus.setQtyScale(8);
            }
            symbolStatus.setUpdated(updated);
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

  public static void main(final String[] args) {
    final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly("BITGET");
    List<ExternalSymbol> symbols = exchange.getExchangeInstrumentsFull();
    System.out.println("Done");
  }
}
