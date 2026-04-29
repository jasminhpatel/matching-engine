package com.solfini.matchengine.executionexchange.xchangewrappers;

import static com.solfini.common.Constants.TARDIS_PERPS;
import static com.solfini.common.Constants.TARDIS_SPOT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.ExecutionExchangeConfig;
import com.solfini.matchengine.copytrade.CopyTradeOrder;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.binance.BinanceRestClient.BinanceExchangeInfoFull;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.binance.BinanceRestClient.BinanceSymbol;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.binance.BinanceRestClient.Filter;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.HttpUtils;
import com.solfini.util.StringUtil;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.instrument.Instrument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class XBinanceExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XBinanceExchange.class);
  private final ObjectMapper mapper = new ObjectMapper();

  public XBinanceExchange(final Exchange exchange) {
    super(exchange);
  }

  public Balance getBalanceFromExchange(final String quoteCurrency, final ExecutionExchangeConfig subscription) {
    // todo margin balance logic
    return super.getBalanceFromExchange(quoteCurrency, subscription);
  }

  public double getPriceFromExchange(final Instrument currencyPair, final Side side) {
    // todo price in futures
    return super.getPriceFromExchange(currencyPair, side);
  }

  public void updateOrderStatus(final CopyTradeOrder copyTradeOrder) throws Exception {
    /*
     * if (copyTrade.getSubscription().hasLeverage()) { updateMarginOrderStatus(copyTrade); } else { super.updateOrderStatus(copyTrade); }
     */
    super.requestGetOrderStatus(copyTradeOrder);
  }

  @Override
  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    final List<ExternalSymbol> symbolStatuses = new ArrayList<>();
    final String apiUrl = exchange.getExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/api/v3/exchangeInfo", new HashMap<>()
        , ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);
    if (response != null && response.getCode() == 200) {
      try {
        final BinanceExchangeInfoFull info = mapper.readValue(response.getData(), BinanceExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          for (BinanceSymbol binanceSymbol : info.getSymbols()) {
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("binance");
            symbolStatus.setSymbol(binanceSymbol.getSymbol());
            symbolStatus.setBase(binanceSymbol.getBaseAsset());
            symbolStatus.setQuote(binanceSymbol.getQuoteAsset());
            symbolStatus.setPrompt("");
            symbolStatus.setTradable("TRADING".equals(binanceSymbol.getStatus()));
            symbolStatus.setUpdated(updated);
            symbolStatus.setInstrumentType(TARDIS_SPOT);
            // symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(binanceSymbol.getQuotePrecision());
            symbolStatus.setQtyScale(binanceSymbol.getBaseAssetPrecision());
            for (Filter filter : binanceSymbol.getFilters()) {
              if ("PRICE_FILTER".equals(filter.getFilterType())) {
                int priceScale = (int) -Math.log10(StringUtil.toDouble(filter.getTickSize()));
                if (priceScale >= 0) {
                  symbolStatus.setPriceScale(priceScale);
                }
              } else if ("LOT_SIZE".equals(filter.getFilterType())) {
                int qtyScale = (int) -Math.log10(StringUtil.toDouble(filter.getStepSize()));
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
    response = HttpUtils.get(BinanceExchange.FUTURES_URL + "/fapi/v1/exchangeInfo", new HashMap<>()
        , ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);
    if (response != null && response.getCode() == 200) {
      try {
        final BinanceExchangeInfoFull info = mapper.readValue(response.getData(), BinanceExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          for (BinanceSymbol binanceSymbol : info.getSymbols()) {
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("binance");
            symbolStatus.setSymbol(binanceSymbol.getSymbol());
            symbolStatus.setBase(binanceSymbol.getBaseAsset());
            symbolStatus.setQuote(binanceSymbol.getQuoteAsset());
            symbolStatus.setPrompt(binanceSymbol.getPrompt());
            symbolStatus.setTradable(true);
            symbolStatus.setFutures(true);
            symbolStatus.setInstrumentType(TARDIS_PERPS);
            symbolStatus.setUpdated(updated);
            // symbolStatus.setUpdated(2000);
            symbolStatus.setPriceScale(binanceSymbol.getPricePrecision());
            symbolStatus.setQtyScale(binanceSymbol.getQuantityPrecision());
            for (Filter filter : binanceSymbol.getFilters()) {
              if ("PRICE_FILTER".equals(filter.getFilterType())) {
                int priceScale = (int) -Math.log10(StringUtil.toDouble(filter.getTickSize()));
                if (priceScale >= 0) {
                  symbolStatus.setPriceScale(priceScale);
                }
              } else if ("LOT_SIZE".equals(filter.getFilterType())) {
                int qtyScale = (int) -Math.log10(StringUtil.toDouble(filter.getStepSize()));
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
    return symbolStatuses;
  }

  public static void main(final String[] args) {
    final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly("BINANCE");
    final List<ExternalSymbol> symbols = exchange.getExchangeInstrumentsFull();
    System.out.println("Done");
  }

}
