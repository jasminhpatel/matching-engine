package com.solfini.matchengine.executionexchange.xchangewrappers;

import static com.solfini.common.Constants.TARDIS_PERPS;
import static com.solfini.common.Constants.TARDIS_SPOT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.ExecutionExchangeConfig;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit.BybitRestClient.ByBitExchangeInfoFull;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit.BybitRestClient.ByBitSymbol;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit.BybitRestClient.BybitPosition;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit.BybitRestClient.BybitPositionsResponse;
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.knowm.xchange.Exchange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.service.account.AccountService;

public class XBybitExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XBybitExchange.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private static final int PROXY_PORT = 8888;

  public XBybitExchange(Exchange exchange) {
    super(exchange);
  }

  @Override
  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    List<ExternalSymbol> symbolStatuses = new ArrayList<>();
    final String apiUrl = exchange.getExchangeSpecification().getSslUri();
    HttpUtils.Response response = HttpUtils.get(apiUrl + "/v5/market/instruments-info?category=spot", new HashMap<>());
    if (response != null && response.getCode() == 200) {
      try {
        final ByBitExchangeInfoFull info = mapper.readValue(response.getData(), ByBitExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getResult() != null && info.getResult().getList() != null) {
          for (ByBitSymbol byBitSymbol : info.getResult().getList()) {
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("bybit");
            symbolStatus.setSymbol(byBitSymbol.getSymbol());
            symbolStatus.setBase(byBitSymbol.getBaseCoin());
            symbolStatus.setQuote(byBitSymbol.getQuoteCoin());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(false);
            symbolStatus.setInstrumentType(TARDIS_SPOT);
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
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("bybit");
            symbolStatus.setSymbol(byBitSymbol.getSymbol());
            symbolStatus.setBase(byBitSymbol.getBaseCoin());
            symbolStatus.setQuote(byBitSymbol.getQuoteCoin());
            symbolStatus.setPrompt("");
            symbolStatus.setFutures(true);
            symbolStatus.setInstrumentType(TARDIS_PERPS);
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
    /*
     * response = HttpUtils.get(apiUrl + "/v5/market/instruments-info?category=inverse", new HashMap<>()); if (response != null &&
     * response.getCode() == 200) { try { final ByBitExchangeInfoFull info = mapper.readValue(response.getData(),
     * ByBitExchangeInfoFull.class); final long updated = System.currentTimeMillis(); if (info.getResult() != null &&
     * info.getResult().getList() != null) { for (ByBitSymbol byBitSymbol : info.getResult().getList()){ if
     * (!"InversePerpetual".equalsIgnoreCase(byBitSymbol.getContractType())) { continue; } final SymbolStatus symbolStatus = new
     * SymbolStatus(); symbolStatus.setExchange("bybit"); symbolStatus.setBase(byBitSymbol.getBaseCoin());
     * symbolStatus.setQuote(byBitSymbol.getQuoteCoin()); symbolStatus.setPrompt(""); symbolStatus.setFutures(true);
     * symbolStatus.setTradable("Trading".equals(byBitSymbol.getStatus())); symbolStatus.setUpdated(updated);
     * //symbolStatus.setUpdated(2000); symbolStatus.setPriceScale(getPrecision(byBitSymbol.getLotSizeFilter().getQuotePrecision()));
     * symbolStatus.setQtyScale(getPrecision(byBitSymbol.getLotSizeFilter().getBasePrecision())); symbolStatuses.add(symbolStatus); } } }
     * catch (JsonProcessingException e) { LOGGER.error(Constants.ERROR_LOG, e); } }
     */
    return symbolStatuses;
  }

  public Balance getBalanceFromExchange(final String symbol, final ExecutionExchangeConfig subscription) {
    final Balance balance = new Balance();
    balance.setLastUpdated(System.currentTimeMillis());
    try {
      if (subscription.isFuturesEnabled()) {
//        final String apiKey = exchange.getExchangeSpecification().getApiKey();
//        final String secretKey = exchange.getExchangeSpecification().getSecretKey();
//        final String apiUrl = exchange.getExchangeSpecification().getSslUri();
        final String apiKey = subscription.getApiKey();
        final String secretKey = subscription.getApiSecret();
        final String apiUrl = Context.getBybitExchangeBaseUrl();


        final long ts = System.currentTimeMillis();
        final String recvWindow = "15000";
        final StringBuilder query = new StringBuilder();
        query.append("category=linear").append("&symbol=").append(symbol.toUpperCase() + "USDT");

        final String preSign = ts + apiKey + recvWindow + query;
        final String sign = hmacSha256(preSign, secretKey);
        final String url = apiUrl + "/v5/position/list" + "?" + query;

        final Map<String, Object> headers = new HashMap<>(5);
        headers.put("X-BAPI-API-KEY", apiKey);
        headers.put("X-BAPI-TIMESTAMP", String.valueOf(ts));
        headers.put("X-BAPI-RECV-WINDOW", recvWindow);
        headers.put("X-BAPI-SIGN", sign);

        final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
        if (response != null && response.getCode() == 200) {
          final BybitPositionsResponse parsed = mapper.readValue(response.getData(), BybitPositionsResponse.class);

          if (parsed.getRetCode() == 0 && "OK".equalsIgnoreCase(parsed.getRetMsg()) && parsed.getResult() != null
            && parsed.getResult().getList() != null && !parsed.getResult().getList().isEmpty()) {
            final BybitPosition bybitPosition = parsed.getResult().getList().get(0);
            if (bybitPosition != null) {
              balance.setCoinBalance("SELL".equalsIgnoreCase(bybitPosition.getSide()) ?
                  MbxMath.roundToBestPrecision(Double.parseDouble(bybitPosition.getSize())) * -1 :
                  MbxMath.roundToBestPrecision(Double.parseDouble(bybitPosition.getSize())));
            }
          }
        }
      } else {
        final AccountService accountService = this.exchange.getAccountService();
        if (accountService == null)
          return balance;
        final Wallet wallet = accountService.getAccountInfo().getWallet();
        if (wallet == null)
          return balance;
        final Map<Currency, org.knowm.xchange.dto.account.Balance> balances = wallet.getBalances();
        if (balances == null)
          return balance;

        final Currency currency = Currency.getInstance(symbol);
        if (currency == null) {
          return balance;
        }

        org.knowm.xchange.dto.account.Balance bal = balances.get(currency);

        if (bal != null && bal.hasAvailable()) {
          balance.setCoinBalance(MbxMath.roundToBestPrecision(bal.getAvailable().doubleValue() + bal.getFrozen().doubleValue()));
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error occurred wile fetching balance. ", e);
    }
    return balance;
  }

  public Map<String, Balance> getBalancesFromExchange(final List<String> symbols, final ExecutionExchangeConfig subscription) {
    final Map<String, Balance> balanceMap = new HashMap<>();

    try {
      if (subscription.isFuturesEnabled()) {
//        final String apiKey = exchange.getExchangeSpecification().getApiKey();
//        final String secretKey = exchange.getExchangeSpecification().getSecretKey();
//        final String apiUrl = exchange.getExchangeSpecification().getSslUri();
        final String apiKey = subscription.getApiKey();
        final String secretKey = subscription.getApiSecret();
        final String apiUrl = Context.getBybitExchangeBaseUrl();


        final long ts = System.currentTimeMillis();
        final String recvWindow = "15000";
        final StringBuilder query = new StringBuilder();
        query.append("category=linear").append("&settleCoin=").append("USDT");

        final String preSign = ts + apiKey + recvWindow + query;
        final String sign = hmacSha256(preSign, secretKey);
        final String url = apiUrl + "/v5/position/list" + "?" + query;

        final Map<String, Object> headers = new HashMap<>(5);
        headers.put("X-BAPI-API-KEY", apiKey);
        headers.put("X-BAPI-TIMESTAMP", String.valueOf(ts));
        headers.put("X-BAPI-RECV-WINDOW", recvWindow);
        headers.put("X-BAPI-SIGN", sign);

        final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
        if (response != null && response.getCode() == 200) {
          final BybitPositionsResponse parsed = mapper.readValue(response.getData(), BybitPositionsResponse.class);

          if (parsed.getRetCode() == 0 && "OK".equalsIgnoreCase(parsed.getRetMsg()) && parsed.getResult() != null
              && parsed.getResult().getList() != null && !parsed.getResult().getList().isEmpty()) {
            final Map<String, BybitPosition> positionMap = new HashMap<>();
            for (BybitPosition pos : parsed.getResult().getList()) {
              if (pos != null && pos.getSymbol() != null) {
                positionMap.put(pos.getSymbol().toUpperCase(), pos);
              }
            }

            for (String symbol : symbols) {
              final Balance balance = new Balance();
              balance.setLastUpdated(System.currentTimeMillis());
              final Currency currency = Currency.getInstance(symbol);
              if (currency == null) {
                balanceMap.put(symbol, balance);
                continue;
              }
              final BybitPosition bybitPosition = positionMap.get(symbol.toUpperCase());

              if (bybitPosition != null) {
                balance.setCoinBalance("SELL".equalsIgnoreCase(bybitPosition.getSide()) ?
                    MbxMath.roundToBestPrecision(Double.parseDouble(bybitPosition.getSize())) * -1 :
                    MbxMath.roundToBestPrecision(Double.parseDouble(bybitPosition.getSize())));
              }
              balanceMap.put(symbol, balance);
            }

          }
        }
      } else {
        final AccountService accountService = this.exchange.getAccountService();
        if (accountService == null)
          return balanceMap;
        final Wallet wallet = accountService.getAccountInfo().getWallet();
        if (wallet == null)
          return balanceMap;
        final Map<Currency, org.knowm.xchange.dto.account.Balance> balances = wallet.getBalances();
        if (balances == null)
          return balanceMap;

        for (String symbol : symbols) {
          final Balance balance = new Balance();
          balance.setLastUpdated(System.currentTimeMillis());
          final Currency currency = Currency.getInstance(symbol);
          if (currency == null) {
            balanceMap.put(symbol, balance);
            continue;
          }
          org.knowm.xchange.dto.account.Balance bal = balances.get(currency);
          if (bal != null && bal.hasAvailable()) {
            balance.setCoinBalance(MbxMath.roundToBestPrecision(bal.getAvailable().doubleValue() + bal.getFrozen().doubleValue()));
          }
          balanceMap.put(symbol, balance);
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error occurred wile fetching balance. ", e);
    }
    return balanceMap;
  }

  private int getPrecision(String value) {
    if (value == null || !value.contains(".")) {
      return 0; // No decimal point means 0 precision
    }
    // Split the string on the decimal point
    String[] parts = value.split("\\.");
    if (parts.length < 2) {
      return 0;
    }
    // Trim trailing zeros for robustness
    String decimalPart = parts[1].replaceAll("0*$", "");

    return decimalPart.length();
  }

  private static String hmacSha256(String data, String key) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] h = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(h.length * 2);
    for (byte b : h) sb.append(String.format("%02x", b));
    return sb.toString();
  }




  public static void main(final String[] args) {
    final XExchange exchange = ExternalExchangeUtil.createXExchangeReadOnly("BYBIT");
    List<ExternalSymbol> symbols = exchange.getExchangeInstrumentsFull();
    System.out.println("Done");
  }
}
