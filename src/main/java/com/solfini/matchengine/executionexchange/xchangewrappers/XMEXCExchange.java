package com.solfini.matchengine.executionexchange.xchangewrappers;

import static com.solfini.common.Constants.TARDIS_PERPS;
import static com.solfini.common.Constants.TARDIS_SPOT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc.MexcRestClient.MEXCExchangeInfoFull;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.mexc.MexcRestClient.MEXCSymbol;
import com.solfini.util.HttpUtils;
import org.knowm.xchange.Exchange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class XMEXCExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XMEXCExchange.class);
  private final ObjectMapper mapper = new ObjectMapper();

  public XMEXCExchange(final Exchange exchange) {
    super(exchange);
  }

  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    final String apiUrl = exchange.getExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/api/v3/exchangeInfo", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final MEXCExchangeInfoFull info = mapper.readValue(response.getData(), MEXCExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          List<ExternalSymbol> symbolStatuses = new ArrayList<>(info.getSymbols().size());
          for (MEXCSymbol mexcSymbol : info.getSymbols()) {
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("mexc");
            symbolStatus.setSymbol(mexcSymbol.getSymbol());
            symbolStatus.setBase(mexcSymbol.getBaseAsset());
            symbolStatus.setQuote(mexcSymbol.getQuoteAsset());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(mexcSymbol.getPermissions() != null && mexcSymbol.getPermissions().contains("FUTURES"));
            symbolStatus.setInstrumentType(symbolStatus.isFutures()? TARDIS_PERPS: TARDIS_SPOT);
            symbolStatus.setTradable("1".equals(mexcSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            // symbolStatus.setUpdated(2000);
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

  public static void main(final String[] args) {
    final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly("MEXC");
    List<ExternalSymbol> symbols = exchange.getExchangeInstrumentsFull();
    System.out.println("Done");
  }
}
