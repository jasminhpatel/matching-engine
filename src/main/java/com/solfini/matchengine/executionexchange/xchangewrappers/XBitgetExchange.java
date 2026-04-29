package com.solfini.matchengine.executionexchange.xchangewrappers;

import static com.solfini.common.Constants.TARDIS_PERPS;
import static com.solfini.common.Constants.TARDIS_SPOT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget.BitgetRestClient.BitgetContractInfo;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget.BitgetRestClient.BitgetContractInfoFull;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget.BitgetRestClient.BitgetExchangeInfoFull;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget.BitgetRestClient.BitgetSymbolInfo;
import com.solfini.util.HttpUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.Exchange;

public class XBitgetExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XBitgetExchange.class);
  private final ObjectMapper mapper = new ObjectMapper();

  public XBitgetExchange(final Exchange exchange) {
    super(exchange);
  }

  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    final List<ExternalSymbol> symbolStatuses = new ArrayList<>();
    final String apiUrl = exchange.getExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/api/v2/spot/public/symbols", new HashMap<>()
        , ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);
    if (response != null && response.getCode() == 200) {
      try {
        final BitgetExchangeInfoFull info = mapper.readValue(response.getData(), BitgetExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {

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
        }
      } catch (JsonProcessingException e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
    }

    // ── Perps (USDT + USDC) ───────────────────────────────────────────────────
    for (String productType : List.of("usdt-futures", "usdc-futures")) {
      HttpUtils.Response perpResponse = HttpUtils.get(
          apiUrl + "/api/v2/mix/market/contracts?productType=" + productType,
          new HashMap<>(), ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);

      if (perpResponse != null && perpResponse.getCode() == 200) {
        final long updated = System.currentTimeMillis();
        try {
          final BitgetContractInfoFull contractInfo = mapper.readValue(perpResponse.getData(),
              BitgetContractInfoFull.class);
          if (contractInfo.getContracts() != null) {
            for (BitgetContractInfo bitgetContractInfo : contractInfo.getContracts()) {
              final ExternalSymbol symbolStatus = new ExternalSymbol();
              symbolStatus.setExchange("bitget");
              symbolStatus.setSymbol(bitgetContractInfo.getSymbol());
              symbolStatus.setBase(bitgetContractInfo.getBaseCoin());
              symbolStatus.setQuote(bitgetContractInfo.getQuoteCoin());
              symbolStatus.setPrompt(bitgetContractInfo.getSymbol());
              symbolStatus.setFutures(true);
              symbolStatus.setInstrumentType(TARDIS_PERPS);
              symbolStatus.setTradable(
                  "normal".equalsIgnoreCase(bitgetContractInfo.getSymbolStatus()));
              try {
                symbolStatus.setPriceScale(Integer.parseInt(bitgetContractInfo.getPricePlace()));
              } catch (NumberFormatException e) {
                symbolStatus.setPriceScale(8);
              }
              try {
                symbolStatus.setQtyScale(Integer.parseInt(bitgetContractInfo.getVolumePlace()));
              } catch (NumberFormatException e) {
                symbolStatus.setQtyScale(8);
              }
              symbolStatus.setUpdated(updated);
              symbolStatuses.add(symbolStatus);
            }
          }
        } catch (JsonProcessingException e) {
          LOGGER.error(Constants.ERROR_LOG, e);
        }
      }
    }
    return symbolStatuses;
  }

  public static void main(final String[] args) {
    final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly("BITGET");
    List<ExternalSymbol> symbols = exchange.getExchangeInstrumentsFull();
    System.out.println("Done");
  }
}
