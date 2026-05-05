package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitget;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.util.HMAC;
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;
import com.solfini.util.StringUtil;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.solfini.matchengine.liquidity.direct.aiGenerated.JsonHelper.*;

public class BitgetRestClient {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BitgetRestClient.class);
  private static final String REST_API_BASE = "https://api.bitget.com";
  private static final int PROXY_PORT = 8888;
  private static final ObjectMapper MAPPER = new ObjectMapper(); // todo use string parsing
  private final String apiKey;
  private final String secretKey;
  private final String passphrase;
  private final ExchangeSubscription subscription;
  private final boolean DEMO_TRADING_ENABLE = Context.getBitgetExchangeDemoTradingEnable();
  private static final String[] QUOTES = {"USDT", "USDC", "USD"};

  public BitgetRestClient(final String apiKey, final String secretKey, final String passphrase, final ExchangeSubscription subscription) {
    this.apiKey = apiKey;
    this.secretKey = secretKey;
    this.passphrase = passphrase;
    this.subscription = subscription;
  }

  public static AccountMode getAccountMode(final String apiKey, final String secretKey, final String passphrase,
      final String lastUsedProxy, final boolean forceToUseProxy) {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "GET";
      final String requestPath = "/api/v3/account/settings";
      final String body = "";

      // Pre-hash string
      final String preHash = timestamp + method + requestPath + body;

      // Signature
      String signature = HMAC.signHmacSHA256(preHash, secretKey);

      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      headers.put("locale", "en-US");

      final HttpUtils.Response response = HttpUtils.get(url, headers, lastUsedProxy, PROXY_PORT, forceToUseProxy);
      if (response != null) {
        if (response.getCode() == 200 && response.getData().contains("00000")) {
          return AccountMode.UNIFIED;
        } else {
          return AccountMode.CLASSIC;
        }
      }
    } catch (final Exception e) {
      LOGGER.error("Balance Bootstrap failed: " + e.getMessage());
    }
    return AccountMode.UNIFIED;
  }

  public String getBalanceSnapshot() {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "GET";
      final String requestPath = "/api/v3/account/assets";
      final String body = "";

      final String signature = generateSignature(timestamp, method, requestPath, body);
      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      headers.put("locale", "en-US");
      if (DEMO_TRADING_ENABLE) {
        headers.put("PAPTRADING", "1");
      }

      final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
      if (response != null && response.getCode() == 200) {
        LOGGER.debug("Successfully retrieved balance snapshot");
        return response.getData();
      } else {
        LOGGER.warn("Balance Bootstrap failed with status: " + (response != null ? response.getCode() : "null"));
        return null;
      }
    } catch (final Exception e) {
      LOGGER.error("Balance Bootstrap failed: " + e.getMessage());
    }
    return null;
  }


  public boolean sendSpotOrderREST(final Order order, final String symbol, final String side, final String orderType,
      final String timeInForce, final String size, final String price, final String clientOrderId) {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "POST";
      final String requestPath = "/api/v3/trade/place-order";

      final StringBuilder bodyBuilder = new StringBuilder();
      bodyBuilder.append("{");
      bodyBuilder.append("\"symbol\":\"").append(symbol).append("\",");
      bodyBuilder.append("\"category\":\"SPOT\",");
      bodyBuilder.append("\"side\":\"").append(side.toLowerCase()).append("\",");
      bodyBuilder.append("\"orderType\":\"").append(orderType.toLowerCase()).append("\",");
      bodyBuilder.append("\"timeInForce\":\"").append(timeInForce.toLowerCase()).append("\",");
      bodyBuilder.append("\"qty\":\"").append(size).append("\"");
      if (price != null && !"market".equalsIgnoreCase(orderType)) {
        bodyBuilder.append(",\"price\":\"").append(price).append("\"");
      }
      if (clientOrderId != null) {
        bodyBuilder.append(",\"clientOid\":\"").append(clientOrderId).append("\"");
      }
      bodyBuilder.append("}");

      final String body = bodyBuilder.toString();
      final String signature = generateSignature(timestamp, method, requestPath, body);
      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      headers.put("X-CHANNEL-API-CODE", subscription.getBrokerId());
      if (DEMO_TRADING_ENABLE) {
        headers.put("PAPTRADING", "1");
      }

      LOGGER.debug("Sending spot order REST for symbol: " + symbol + " side: " + side);
      final HttpUtils.Response resp =
          HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

      if (resp != null && resp.getCode() == 200) {
        final String json = resp.getData();
        final String dataJson = minExtract(json, "data");

        if (dataJson != null) {
          ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
          if (executionMessage == null) {
            executionMessage = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0,
                order.getSymbol(), 0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);

            executionMessage.setClOrdId(clientOrderId);
          }
          subscription.updateExecutionReport(executionMessage);
          LOGGER.info("Spot order created with orderId: " + order.getOrderId() + " clientOid: " + clientOrderId);
          return true;
        } else {
          LOGGER.warn("No data object found in response: " + json);
          return false;
        }
      } else {

        final String msg = minExtract(resp.getData(), "msg");
        LOGGER.warn(
            "REST spot order status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
        // Create execution report for rejected order
        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
          executionMessage = ExecutionReportMessage.createExternalExecutionReport(0L, order.getUser(), 0, order.getSymbol(), 0L, (short) 0,
              0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);
        }

        executionMessage.setClOrdId(clientOrderId);
        executionMessage.setError(msg);
        executionMessage.setExecType(ExecType.REJECTED);
        subscription.updateExecutionReport(executionMessage);

        order.setRejected(true);
        subscription.updateOrder(order.getClOrdId(), order);
        return false;
      }
    } catch (final Exception e) {
      LOGGER.error("REST spot order failed: " + e.getMessage());
      return false;
    }
  }

  public boolean sendFutureOrderREST(final Order order, final String symbol, final String side, final String orderType,
      final String timeInForce, final String size, final String price, final String clientOrderId) {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "POST";
      final String requestPath = "/api/v3/trade/place-order";
      boolean hedgeMode = false;
      String category = "USDT-FUTURES";
      if (symbol.toLowerCase().endsWith("USDC")) {
        category = "USDC-FUTURES";
      }

      final StringBuilder bodyBuilder = new StringBuilder();
      bodyBuilder.append("{");
      bodyBuilder.append("\"symbol\":\"").append(symbol).append("\",");
      bodyBuilder.append("\"category\":\"").append(category).append("\",");
      bodyBuilder.append("\"side\":\"").append(side.toLowerCase()).append("\",");
      bodyBuilder.append("\"orderType\":\"").append(orderType.toLowerCase()).append("\",");
      //bodyBuilder.append("\"oneWayMode\":true,"); // TODO do we use hedge mode account or one way mode?
      bodyBuilder.append("\"timeInForce\":\"").append(timeInForce.toLowerCase()).append("\",");
      bodyBuilder.append("\"qty\":\"").append(size).append("\"");
      if (hedgeMode) {
        if (side.equalsIgnoreCase("buy")) {
          if (order.isToClose()) {
            bodyBuilder.append(",\"posSide\":\"").append("short").append("\"");
          } else {
            bodyBuilder.append(",\"posSide\":\"").append("long").append("\"");
          }
        } else {
          if (order.isToClose()) {
            bodyBuilder.append(",\"posSide\":\"").append("long").append("\"");
          } else {
            bodyBuilder.append(",\"posSide\":\"").append("short").append("\"");
          }
        }
      } else {
        if (order.isToClose()) {
          bodyBuilder.append(",\"reduceOnly\":").append(order.isToClose());
        }
      }
      if (price != null && !"market".equalsIgnoreCase(orderType)) {
        bodyBuilder.append(",\"price\":\"").append(price).append("\"");
      }
      if (clientOrderId != null) {
        bodyBuilder.append(",\"clientOid\":\"").append(clientOrderId).append("\"");
      }
      bodyBuilder.append("}");

      final String body = bodyBuilder.toString();
      final String signature = generateSignature(timestamp, method, requestPath, body);
      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      headers.put("X-CHANNEL-API-CODE", subscription.getBrokerId());
      if (DEMO_TRADING_ENABLE) {
        headers.put("PAPTRADING", "1");
      }

      LOGGER.debug("Sending future order REST for symbol: " + symbol + " side: " + side);
      final HttpUtils.Response resp =
          HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

      if (resp != null && resp.getCode() == 200) {
        final String json = resp.getData();
        final String dataJson = minExtract(json, "data");

        if (dataJson != null) {
          final String clientOid = minExtract(dataJson, "clientOid");
          ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
          if (executionMessage == null) {
            executionMessage = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0,
                order.getSymbol(), 0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);

            executionMessage.setClOrdId(clientOrderId);
          }
          subscription.updateExecutionReport(executionMessage);

          LOGGER.info("Future order created with orderId: " + order.getOrderId() + " clientOid: " + clientOid);
          return true;

        } else {
          LOGGER.warn("No data object found in response: " + json);
          return false;
        }
      } else {

        final String msg = minExtract(resp.getData(), "msg");
        LOGGER.warn(
            "REST future order status=" + (resp != null ? resp.getCode() : "null") + " body=" + (resp != null ? resp.getData() : "null"));
        // Create execution report for rejected order
        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
          executionMessage = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, order.getSymbol(),
              0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);
        }

        executionMessage.setClOrdId(clientOrderId);
        executionMessage.setError(msg);
        executionMessage.setExecType(ExecType.REJECTED);
        subscription.updateExecutionReport(executionMessage);

        order.setRejected(true);
        subscription.updateOrder(order.getClOrdId(), order);
        return false;
      }
    } catch (final Exception e) {
      LOGGER.error("REST future order failed: " + e.getMessage());
      return false;
    }
  }

  public String getPositionInfo(final String settleCoin) {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "GET";
      final String requestPath = "/api/v3/position/current-position?category=" + settleCoin + "-FUTURES";
      final String body = "";

      final String signature = generateSignature(timestamp, method, requestPath, body);
      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      headers.put("locale", "en-US");
      if (DEMO_TRADING_ENABLE) {
        headers.put("PAPTRADING", "1");
      }

      final HttpUtils.Response response = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
      if (response != null && response.getCode() == 200) {
        LOGGER.debug("Successfully retrieved " + settleCoin + " position info");
        return response.getData();
      } else {
        LOGGER.warn("Get " + settleCoin + " position info failed with status: " + (response != null ? response.getCode() : "null"));
        return null;
      }
    } catch (final Exception e) {
      LOGGER.error("Get " + settleCoin + " position info failed: " + e.getMessage());
    }
    return null;
  }

  public String getAllOpenSpotOrders() {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "GET";
      final String requestPath = "/api/v3/trade/unfilled-orders";
      final String body = "";

      final String signature = generateSignature(timestamp, method, requestPath, body);
      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      if (DEMO_TRADING_ENABLE) {
        headers.put("PAPTRADING", "1");
      }

      final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
      if (resp == null || resp.getCode() != 200) {
        LOGGER.warn("Get all open spot orders failed with status=" + (resp != null ? resp.getCode() : "null") + " body="
            + (resp != null ? resp.getData() : "null"));
        return null;
      }
      LOGGER.debug("Successfully retrieved all open spot orders");
      return resp.getData();
    } catch (final Exception e) {
      LOGGER.error("Get all open spot orders failed: " + e.getMessage());
      return null;
    }
  }

  public String getAllOpenFuturesOrders() {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "GET";
      final String requestPath = "/api/v3/trade/unfilled-orders?productType=USDT-FUTURES";
      final String body = "";

      final String signature = generateSignature(timestamp, method, requestPath, body);
      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      if (DEMO_TRADING_ENABLE) {
        headers.put("PAPTRADING", "1");
      }
      final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
      if (resp == null || resp.getCode() != 200) {
        LOGGER.warn("Get all open futures orders failed with status=" + (resp != null ? resp.getCode() : "null") + " body="
            + (resp != null ? resp.getData() : "null"));
        return null;
      }
      LOGGER.debug("Successfully retrieved all open futures orders");
      return resp.getData();
    } catch (final Exception e) {
      LOGGER.error("Get all open futures orders failed: " + e.getMessage());
      return null;
    }
  }

  public boolean querySpotOrderStatus(final Order order, final String symbol, final String clientOrderId) {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "GET";
      final String requestPath = "/api/v3/trade/order-info?symbol=" + symbol + "&clientOid=" + clientOrderId;
      final String body = "";

      final String signature = generateSignature(timestamp, method, requestPath, body);
      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      if (DEMO_TRADING_ENABLE) {
        headers.put("PAPTRADING", "1");
      }

      LOGGER.debug("Querying Bitget spot order status for clientOrderId: " + clientOrderId);
      final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
      if (resp == null || resp.getCode() != 200) {
        LOGGER.warn("Bitget spot order status query failed with status=" + (resp != null ? resp.getCode() : "null") + " body="
            + (resp != null ? resp.getData() : "null"));
        return false;
      }

      final String json = resp.getData();
      LOGGER.debug("Full response: " + json);

      final String code = minExtract(json, "code");
      if ("00000".equals(code)) {
        final String dataJson = extractJsonValue(json, "data");
        if (dataJson != null) {
          populateSpotOrderFromJson(order, dataJson, clientOrderId);
        }
      } else {
        final String msg = minExtract(json, "msg");
        LOGGER.warn("Bitget spot order status query failed with code: " + code + ", msg: " + msg);
      }
      return true;
    } catch (final Exception e) {
      LOGGER.error("Bitget spot order status query failed: " + e.getMessage());
      return false;
    }
  }

  public boolean queryFuturesOrderStatus(final Order order, final String symbol, final String clientOrderId) {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "GET";
      final String requestPath = "/api/v3/trade/order-info?symbol=" + symbol + "&clientOid=" + clientOrderId + "&productType=USDT-FUTURES";
      final String body = "";

      final String signature = generateSignature(timestamp, method, requestPath, body);
      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      if (DEMO_TRADING_ENABLE) {
        headers.put("PAPTRADING", "1");
      }

      LOGGER.debug("Querying Bitget futures order status for clientOrderId: " + clientOrderId);
      final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
      if (resp == null || resp.getCode() != 200) {
        LOGGER.warn("Bitget futures order status query failed with status=" + (resp != null ? resp.getCode() : "null") + " body="
            + (resp != null ? resp.getData() : "null"));
        return false;
      }

      final String json = resp.getData();
      LOGGER.debug("Full response: " + json);

      final String code = minExtract(json, "code");
      if ("00000".equals(code)) {
        final String dataJson = extractJsonValue(json, "data");
        if (dataJson != null) {
          populateFuturesOrderFromJson(order, dataJson, clientOrderId);
        }
      } else {
        final String msg = minExtract(json, "msg");
        LOGGER.warn("Bitget futures order status query failed with code: " + code + ", msg: " + msg);
      }
      return true;
    } catch (final Exception e) {
      LOGGER.error("Bitget futures order status query failed: " + e.getMessage());
      return false;
    }
  }

  public ExecutionReportMessage queryUTAOrderStatus(final String clOrdId, final String orderId) {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "GET";
      final String requestPath = clOrdId != null ? ("/api/v3/trade/order-info?clientOid=" + clOrdId)
          : ("/api/v3/trade/order-info?orderId=" + orderId);
      final String body = "";

      final String signature = generateSignature(timestamp, method, requestPath, body);
      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      if (DEMO_TRADING_ENABLE) {
        headers.put("PAPTRADING", "1");
      }

      LOGGER.debug(Constants.LOG_FMT_4, "Querying Bitget futures order status for clOrdId: ", clOrdId, " orderId: ", orderId);
      final HttpUtils.Response resp = HttpUtils.get(url, headers, subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
      if (resp == null || resp.getCode() != 200) {
        LOGGER.warn("Bitget futures order status query failed with status=" + (resp != null ? resp.getCode() : "null") + " body="
            + (resp != null ? resp.getData() : "null"));
        return null;
      }

      final String json = resp.getData();
      LOGGER.debug("Full response: " + json);

      final String code = minExtract(json, "code");
      if ("00000".equals(code)) {
        final String dataJson = extractJsonValue(json, "data");
        if (dataJson != null) {
          generateUTAOrderFromJson(dataJson, clOrdId, orderId);
        }
      } else {
        final String msg = minExtract(json, "msg");
        LOGGER.warn("Bitget futures order status query failed with code: " + code + ", msg: " + msg);
      }
      return null;
    } catch (final Exception e) {
      LOGGER.error("Bitget futures order status query failed: " + e.getMessage());
      return null;
    }
  }

  public boolean cancelSpotOrderRest(final Order order, final String symbol, final String clientOrderId) {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "POST";
      final String requestPath = "/api/v3/trade/cancel-order";

      final String body = "{\"symbol\":\"" + symbol + "\",\"clientOid\":\"" + clientOrderId + "\"}";

      final String signature = generateSignature(timestamp, method, requestPath, body);
      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      if (DEMO_TRADING_ENABLE) {
        headers.put("PAPTRADING", "1");
      }

      LOGGER.debug("Cancelling spot order for clientOrderId: " + clientOrderId);
      final HttpUtils.Response resp =
          HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

      if (resp == null || resp.getCode() != 200) {
        LOGGER.warn("Cancel spot order failed with status=" + (resp != null ? resp.getCode() : "null") + " body="
            + (resp != null ? resp.getData() : "null"));
        return false;
      }

      final String json = resp.getData();
      final String code = minExtract(json, "code");

      if ("00000".equals(code)) {
        LOGGER.info("Successfully cancelled spot order for clientOrderId: " + clientOrderId);
        order.setRejected(true);
        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
          executionMessage = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, order.getSymbol(),
              0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);
          executionMessage.setClOrdId(clientOrderId);
        }
        executionMessage.setOrdStatus(OrdStatus.CANCELED);
        subscription.updateExecutionReport(executionMessage);
        subscription.updateOrder(clientOrderId, order);
        return true;
      } else {
        final String message = minExtract(json, "msg");
        LOGGER.warn("Cancel spot order failed with code: " + code + ", message: " + message);
        return false;
      }
    } catch (final Exception e) {
      LOGGER.error("Cancel spot order failed: " + e.getMessage(), e);
      return false;
    }
  }

  public boolean cancelFuturesOrderRest(final Order order, final String symbol, final String clientOrderId) {
    try {
      final String timestamp = String.valueOf(Instant.now().toEpochMilli());
      final String method = "POST";
      final String requestPath = "/api/v3/trade/cancel-order";

      final String body = "{\"symbol\":\"" + symbol + "\",\"clientOid\":\"" + clientOrderId + "\",\"productType\":\"USDT-FUTURES\"}";

      final String signature = generateSignature(timestamp, method, requestPath, body);
      final String url = REST_API_BASE + requestPath;

      final Map<String, Object> headers = new HashMap<>();
      headers.put("ACCESS-KEY", apiKey);
      headers.put("ACCESS-SIGN", signature);
      headers.put("ACCESS-TIMESTAMP", timestamp);
      headers.put("ACCESS-PASSPHRASE", passphrase);
      headers.put("Content-Type", "application/json");
      if (DEMO_TRADING_ENABLE) {
        headers.put("PAPTRADING", "1");
      }

      LOGGER.debug("Cancelling futures order for clientOrderId: " + clientOrderId);
      final HttpUtils.Response resp =
          HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());

      if (resp == null || resp.getCode() != 200) {
        LOGGER.warn("Cancel futures order failed with status=" + (resp != null ? resp.getCode() : "null") + " body="
            + (resp != null ? resp.getData() : "null"));
        return false;
      }

      final String json = resp.getData();
      final String code = minExtract(json, "code");

      if ("00000".equals(code)) {
        LOGGER.info("Successfully cancelled futures order for clientOrderId: " + clientOrderId);
        order.setRejected(true);
        // Create execution report and set OrderId
        ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
        if (executionMessage == null) {
          executionMessage = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, order.getSymbol(),
              0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);
          executionMessage.setClOrdId(clientOrderId);
        }
        executionMessage.setOrdStatus(OrdStatus.CANCELED);
        subscription.updateExecutionReport(executionMessage);
        subscription.updateOrder(clientOrderId, order);
        return true;
      } else {
        final String message = minExtract(json, "msg");
        LOGGER.warn("Cancel futures order failed with code: " + code + ", message: " + message);
        return false;
      }
    } catch (final Exception e) {
      LOGGER.error("Cancel futures order failed: " + e.getMessage(), e);
      return false;
    }
  }

  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    HttpUtils.Response response = HttpUtils.get(REST_API_BASE + "/api/v2/spot/public/symbols", new HashMap<>()
        , ExternalExchangeUtil.getStickyProxy(0), Context.getExternalExchangeProxyPort(), true);
    if (response != null && response.getCode() == 200) {
      try {
        final BitgetExchangeInfoFull info = MAPPER.readValue(response.getData(), BitgetExchangeInfoFull.class);
        final long updated = System.currentTimeMillis();
        if (info.getSymbols() != null) {
          List<ExternalSymbol> symbolStatuses = new ArrayList<>();
          for (BitgetSymbolInfo bitgetSymbolInfo : info.getSymbols()) {
            final ExternalSymbol symbolStatus = new ExternalSymbol();
            symbolStatus.setExchange("bitget");
            symbolStatus.setBase(bitgetSymbolInfo.getBaseCoin());
            symbolStatus.setQuote(bitgetSymbolInfo.getQuoteCoin());
            symbolStatus.setPrompt(bitgetSymbolInfo.getSymbol());
            symbolStatus.setFutures(false);
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

  public static Ticker getTicker(final String symbol) {
        try {
            final String requestPath = "/api/v2/spot/market/tickers?symbol=" + symbol;
            final String url = REST_API_BASE + requestPath;

            final Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("locale", "en-US");

            final HttpUtils.Response response = HttpUtils.get(url, headers, ExternalExchangeUtil.getStickyProxy(0),
                Context.getExternalExchangeProxyPort(), true);
            if (response == null || response.getCode() != 200) {
                LOGGER.warn("Get ticker failed for symbol: " + symbol + " status: " + (response != null ? response.getCode() : "null"));
                return null;
            }

            final String json = response.getData();
            final String code = minExtract(json, "code");
            if (!"00000".equals(code)) {
                final String msg = minExtract(json, "msg");
                LOGGER.warn("Get ticker failed for symbol: " + symbol + " code: " + code + " msg: " + msg);
                return null;
            }

            final String dataArray = extractJsonValue(json, "data");
            if (dataArray == null) {
                LOGGER.warn("No data in ticker response for symbol: " + symbol);
                return null;
            }

            final String tickerJson = extractFirstOrderFromArray(dataArray);
            if (tickerJson == null) {
                LOGGER.warn("Empty data array in ticker response for symbol: " + symbol);
                return null;
            }

            final double last       = parseDoubleSafe(minExtract(tickerJson, "lastPr"));
            final double bid        = parseDoubleSafe(minExtract(tickerJson, "bidPr"));
            final double ask        = parseDoubleSafe(minExtract(tickerJson, "askPr"));
            final double high       = parseDoubleSafe(minExtract(tickerJson, "high24h"));
            final double low        = parseDoubleSafe(minExtract(tickerJson, "low24h"));
            final double open       = parseDoubleSafe(minExtract(tickerJson, "openUtc"));
            final double baseVolume    = parseDoubleSafe(minExtract(tickerJson, "baseVolume"));
            final double quoteVolume   = parseDoubleSafe(minExtract(tickerJson, "quoteVolume"));
            final double bidSize      = parseDoubleSafe(minExtract(tickerJson, "bidSz"));
            final double askSize      = parseDoubleSafe(minExtract(tickerJson, "askSz"));
            final long timestamp         = parseLongSafe(minExtract(tickerJson, "ts"));
            final double change24h  = parseDoubleSafe(minExtract(tickerJson, "change24h"));
            
            final int instrumentType = 0;

            // Bitget returns change24h as a fraction (e.g. 0.0175 = 1.75%); Ticker expects percentage units
            final double percentageChange = change24h * 100.0;

            LOGGER.debug("Retrieved ticker for symbol: " + symbol + " last=" + last + " bid=" + bid + " ask=" + ask);

            final Ticker ticker = new Ticker(symbol, instrumentType,  open, last,  bid, ask,
               high,  low, baseVolume,  quoteVolume, timestamp,
                bidSize, askSize, percentageChange);
                
            return ticker;
        } catch (final Exception e) {
            LOGGER.error("Get ticker failed for symbol: " + symbol + " " + e.getMessage());
            return null;
        }
    }

  private String generateSignature(final String timestamp, final String method, final String requestPath, final String body) {
    final String preHashString = timestamp + method.toUpperCase() + requestPath + body;
    try {
      final String hmacHex = HMAC.hmacSha256(preHashString, secretKey);
      // Convert hex string to bytes and then encode as Base64
      final byte[] hmacBytes = hexStringToByteArray(hmacHex);
      return Base64.getEncoder().encodeToString(hmacBytes);
    } catch (Exception e) {
      LOGGER.error("Failed to generate signature: " + e.getMessage());
      throw new RuntimeException("Signature generation failed", e);
    }
  }

  private byte[] hexStringToByteArray(final String hex) {
    final int len = hex.length();
    final byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
    }
    return data;
  }

  /**
   * Populates order object and execution report with all fields extracted from Bitget spot order JSON
   *
   * @param order The order object to populate
   * @param orderJson The complete order JSON object as string
   * @param clientOrderId The client order ID
   */
  private void populateSpotOrderFromJson(final Order order, final String orderJson, final String clientOrderId) {
    // Extract all order fields from Bitget response
    final String orderStatus = minExtract(orderJson, "orderStatus");
    final String cumExecQty = minExtract(orderJson, "cumExecQty");
    final String cumExecValue = minExtract(orderJson, "cumExecValue");
    final String avgPrice = minExtract(orderJson, "avgPrice");
    final String orderType = minExtract(orderJson, "orderType");
    final String cancelReason = minExtract(orderJson, "cancelReason");
    final String updatedTime = minExtract(orderJson, "updatedTime");
    final String feeDetailSection = extractJsonValue(orderJson, "feeDetail");
    final String reduceOnly = minExtract(orderJson, "reduceOnly");

    // Extract fee information from feeDetail array
    final String feeCoin = extractFeeDetailsFromArray(feeDetailSection, "feeCoin");
    final String feeAmount = extractFeeDetailsFromArray(feeDetailSection, "fee");

    // Parse numeric values safely
    final OrdStatus mappedOrderStatus = mapOrderStatus(orderStatus);
    final double cumExecQtyDouble = parseDoubleSafe(cumExecQty);
    final double avgPriceDouble = parseDoubleSafe(avgPrice);
    final double feeDouble = parseDoubleSafe(feeAmount);
    final long updatedTimeLong = parseLongSafe(updatedTime);

    // Convert to order's scale
    final long cumExecQtyLong = MbxMath.changeScale(cumExecQtyDouble, order.getQtyScale());
    final long avgPriceLong = MbxMath.changeScale(avgPriceDouble, order.getPriceScale());
    final long leavesQtyLong = order.getQty() - cumExecQtyLong;

    LOGGER.debug("Extracted Bitget spot order details - orderId: " + order.getOrderId() + ", status: " + orderStatus + ", cumExecQty: "
        + cumExecQty + ", avgPrice: " + avgPrice + ", cumExecValue: " + cumExecValue + ", feeCoin: " + feeCoin + ", fee: " + feeAmount
        + ", orderType: " + orderType + ", side: " + order.getSide());

    // Create or update execution report with all extracted fields
    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
    if (executionMessage == null) {
      executionMessage = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L,
          (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);
    }

    // Update order object
    if ("filled".equalsIgnoreCase(orderStatus)) {
      LOGGER.debug("Bitget spot order status - FILLED for clientOrderId: " + clientOrderId + ", cumExecQty: " + cumExecQty + ", avgPrice: "
          + avgPrice + ", cumExecValue: " + cumExecValue + ", fee: " + feeAmount);
      order.setExecuted(true);


      // Set fee information if available
      if (feeDouble > 0 && feeCoin != null && !feeCoin.isBlank()) {
        // Convert commission with proper instrument scale
        final Instrument feesInstrument =
            InstrumentCache.getBySymbol(feeCoin.isBlank() ? getDefaultQuotedCurrency(order.getSymbol()) : feeCoin);
        if (feesInstrument != null) {
          final long feesLong = MbxMath.changeScale(feeDouble, feesInstrument.getQuantityScale());
          executionMessage.setFeeAccumulatedQuantity(feesLong);
          executionMessage.setFeePositionId(feesInstrument.getId());
        }
      }
    } else if ("cancelled".equalsIgnoreCase(orderStatus) || "rejected".equalsIgnoreCase(orderStatus)) {
      LOGGER.debug("Bitget spot order status - " + orderStatus + " for clientOrderId: " + clientOrderId
          + (cancelReason != null ? ", reason: " + cancelReason : ""));
      order.setRejected(true);
      if (cancelReason != null && !cancelReason.isBlank()) {
        order.setError(cancelReason);
      }
    }

    // Set execution report fields
    executionMessage.setClOrdId(clientOrderId);
    executionMessage.setOrdStatus(mappedOrderStatus);
    executionMessage.setTimeInForce(order.getTimeInForce());
    executionMessage.setInputTime(updatedTimeLong);
    executionMessage.setPrice(order.getPrice());
    executionMessage.setPriceScale(order.getPriceScale());
    executionMessage.setOrderQty(order.getQty());
    executionMessage.setOrderQtyScale(order.getQtyScale());
    executionMessage.setCumQty(cumExecQtyLong);
    executionMessage.setLeavesQty(leavesQtyLong);
    executionMessage.setAvgPx(avgPriceLong);

    LOGGER.debug("Updating execution report for clientOrderId: " + clientOrderId + " with status: " + orderStatus);
    subscription.updateExecutionReport(executionMessage);
    subscription.updateOrder(clientOrderId, order);
  }

  /**
   * Populates order object and execution report with all fields extracted from Bitget futures order JSON
   *
   * @param order The order object to populate
   * @param orderJson The complete order JSON object as string
   * @param clientOrderId The client order ID
   */
  private void populateFuturesOrderFromJson(final Order order, final String orderJson, final String clientOrderId) {
    // Extract all order fields from Bitget response
    final String orderStatus = minExtract(orderJson, "orderStatus");
    final String cumExecQty = minExtract(orderJson, "cumExecQty");
    final String cumExecValue = minExtract(orderJson, "cumExecValue");
    final String avgPrice = minExtract(orderJson, "avgPrice");
    final String orderType = minExtract(orderJson, "orderType");
    final String posSide = minExtract(orderJson, "posSide");
    final String holdMode = minExtract(orderJson, "holdMode");
    final String cancelReason = minExtract(orderJson, "cancelReason");
    final String updatedTime = minExtract(orderJson, "updatedTime");
    final String feeDetailSection = extractJsonValue(orderJson, "feeDetail");
    final String reduceOnly = minExtract(orderJson, "reduceOnly");
    final String marginMode = minExtract(orderJson, "marginMode");

    // Extract fee information from feeDetail array
    final String feeCoin = extractFeeDetailsFromArray(feeDetailSection, "feeCoin");
    final String feeAmount = extractFeeDetailsFromArray(feeDetailSection, "fee");

    // Parse numeric values safely
    final OrdStatus mappedOrderStatus = mapOrderStatus(orderStatus);
    final double cumExecQtyDouble = parseDoubleSafe(cumExecQty);
    final double avgPriceDouble = parseDoubleSafe(avgPrice);
    final double feeDouble = parseDoubleSafe(feeAmount);
    final long updatedTimeLong = parseLongSafe(updatedTime);

    // Convert to order's scale
    final long cumExecQtyLong = MbxMath.changeScale(cumExecQtyDouble, order.getQtyScale());
    final long avgPriceLong = MbxMath.changeScale(avgPriceDouble, order.getPriceScale());
    final long leavesQtyLong = order.getQty() - cumExecQtyLong;

    LOGGER.debug("Extracted Bitget futures order details - orderId: " + order.getOrderId() + ", status: " + orderStatus + ", cumExecQty: "
        + cumExecQty + ", avgPrice: " + avgPrice + ", cumExecValue: " + cumExecValue + ", feeCoin: " + feeCoin + ", fee: " + feeAmount
        + ", orderType: " + orderType + ", side: " + order.getSide() + ", posSide: " + posSide + ", holdMode: " + holdMode
        + ", marginMode: " + marginMode);

    // Create or update execution report with all extracted fields
    ExecutionReportMessage executionMessage = subscription.getExecutionReport(order.getClOrdId());
    if (executionMessage == null) {
      executionMessage = ExecutionReportMessage.createExternalExecutionReport(order.getOrderId(), order.getUser(), 0, order.getSymbol(), 0L,
          (short) 0, 0L, (short) 0, 0, 0, 0, 0, order.getSide(), 0);
    }

    if ("filled".equalsIgnoreCase(orderStatus)) {
      LOGGER.debug("Bitget futures order status - FILLED for clientOrderId: " + clientOrderId + ", cumExecQty: " + cumExecQty
          + ", avgPrice: " + avgPrice + ", cumExecValue: " + cumExecValue + ", fee: " + feeAmount + ", posSide: " + posSide);
      order.setExecuted(true);

      // Set fee information if available
      if (feeDouble > 0 && feeCoin != null && !feeCoin.isBlank()) {
        // Convert commission with proper instrument scale
        final Instrument feesInstrument =
            InstrumentCache.getBySymbol(feeCoin.isBlank() ? getDefaultQuotedCurrency(order.getSymbol()) : feeCoin);
        if (feesInstrument != null) {
          final long feesLong = MbxMath.changeScale(feeDouble, feesInstrument.getQuantityScale());
          executionMessage.setFeeAccumulatedQuantity(feesLong);
          executionMessage.setFeePositionId(feesInstrument.getId());
        }
      }
    } else if ("cancelled".equalsIgnoreCase(orderStatus) || "rejected".equalsIgnoreCase(orderStatus)) {
      LOGGER.debug("Bitget futures order status - " + orderStatus + " for clientOrderId: " + clientOrderId
          + (cancelReason != null ? ", reason: " + cancelReason : ""));
      order.setRejected(true);
      if (cancelReason != null && !cancelReason.isBlank()) {
        order.setError(cancelReason);
      }
    }

    // Set execution report fields
    executionMessage.setClOrdId(clientOrderId);
    executionMessage.setOrderId(order.getOrderId());
    executionMessage.setOrdStatus(mappedOrderStatus);
    executionMessage.setTimeInForce(order.getTimeInForce());
    executionMessage.setInputTime(updatedTimeLong);
    executionMessage.setPrice(order.getPrice());
    executionMessage.setPriceScale(order.getPriceScale());
    executionMessage.setOrderQty(order.getQty());
    executionMessage.setOrderQtyScale(order.getQtyScale());
    executionMessage.setCumQty(cumExecQtyLong);
    executionMessage.setLeavesQty(leavesQtyLong);
    executionMessage.setAvgPx(avgPriceLong);

    LOGGER.debug("Updating execution report for clientOrderId: " + clientOrderId + " with status: " + orderStatus + ", posSide: " + posSide
        + ", holdMode: " + holdMode);
    subscription.updateExecutionReport(executionMessage);
    subscription.updateOrder(clientOrderId, order);
  }

  private ExecutionReportMessage generateUTAOrderFromJson(final String orderJson, final String clientOrderId,
      final String orderId) {
    // Extract all order fields from Bitget response
    final String clientOid = minExtract(orderJson, "clientOid");
    final String category = minExtract(orderJson, "category");
    final String symbol = minExtract(orderJson, "symbol");
    final String orderType = minExtract(orderJson, "orderType");
    final String side = minExtract(orderJson, "side");
    final String price = minExtract(orderJson, "price");
    final String qty = minExtract(orderJson, "qty");
    final String amount = minExtract(orderJson, "amount");
    final String cumExecQty = minExtract(orderJson, "cumExecQty");
    final String cumExecValue = minExtract(orderJson, "cumExecValue");
    final String avgPrice = minExtract(orderJson, "avgPrice");
    final String timeInForce = minExtract(orderJson, "timeInForce");
    final String orderStatus = minExtract(orderJson, "orderStatus");
    final String posSide = minExtract(orderJson, "posSide");
    final String holdMode = minExtract(orderJson, "holdMode");
    final String tradeSide = minExtract(orderJson, "tradeSide");
    final String reduceOnly = minExtract(orderJson, "reduceOnly");
    final String feeDetailSection = extractJsonValue(orderJson, "feeDetail");
    final String cancelReason = minExtract(orderJson, "cancelReason");
    final String execType = minExtract(orderJson, "execType");
    final String updatedTime = minExtract(orderJson, "updatedTime");
    //final String marginMode = minExtract(orderJson, "marginMode");

    // Extract fee information from feeDetail array
    final String feeCoin = extractFeeDetailsFromArray(feeDetailSection, "feeCoin");
    final String feeAmount = extractFeeDetailsFromArray(feeDetailSection, "fee");

    // Parse numeric values safely
    final OrdStatus mappedOrderStatus = mapOrderStatus(orderStatus);
    final double priceDouble = parseDoubleSafe(price);
    final double qtyDouble = parseDoubleSafe(qty);
    final double cumExecQtyDouble = parseDoubleSafe(cumExecQty);
    final double avgPriceDouble = parseDoubleSafe(avgPrice);
    final double feeDouble = parseDoubleSafe(feeAmount);
    final long updatedTimeLong = parseLongSafe(updatedTime);
    final Side sideValue = parseSide(side);
    final String[] baseQuoteSymbols = parseSymbol(symbol);
    final TimeInForce tif = parseTif(timeInForce);

    LOGGER.debug("Extracted Bitget UTA order details - orderId: " + clientOrderId + ", status: " + orderStatus + ", cumExecQty: "
        + cumExecQty + ", avgPrice: " + avgPrice + ", cumExecValue: " + cumExecValue + ", feeCoin: " + feeCoin + ", fee: " + feeAmount
        + ", orderType: " + orderType + ", side: " + side + ", posSide: " + posSide + ", holdMode: " + holdMode);

    // Create or update execution report with all extracted fields
    ExecutionReportMessage executionMessage = subscription.getExecutionReport(clientOrderId);
    if (executionMessage == null) {
      executionMessage = ExecutionReportMessage.createExternalExecutionReport(StringUtil.toInt(orderId),
          null, 0, baseQuoteSymbols[0], 0L, (short) 0, 0L, (short) 0,
          0, 0, 0, 0, sideValue, 0);
    }

    if ("filled".equalsIgnoreCase(orderStatus)) {
      LOGGER.debug("Bitget futures order status - FILLED for clientOrderId: " + clientOrderId + ", cumExecQty: " + cumExecQty
          + ", avgPrice: " + avgPrice + ", cumExecValue: " + cumExecValue + ", fee: " + feeAmount + ", posSide: " + posSide);

      // Set fee information if available
      if (feeDouble > 0 && feeCoin != null && !feeCoin.isBlank()) {
        // Convert commission with proper instrument scale
        final Instrument feesInstrument =
            InstrumentCache.getBySymbol(feeCoin.isBlank() ? getDefaultQuotedCurrency(baseQuoteSymbols[0]) : feeCoin);
        if (feesInstrument != null) {
          final long feesLong = MbxMath.changeScale(feeDouble, feesInstrument.getQuantityScale());
          executionMessage.setFeeAccumulatedQuantity(feesLong);
          executionMessage.setFeePositionId(feesInstrument.getId());
        }
      }
    } else if ("cancelled".equalsIgnoreCase(orderStatus) || "rejected".equalsIgnoreCase(orderStatus)) {
      LOGGER.debug("Bitget futures order status - " + orderStatus + " for clientOrderId: " + clientOrderId
          + (cancelReason != null ? ", reason: " + cancelReason : ""));
    }

    // Set execution report fields
    executionMessage.setClOrdId(clientOrderId);
    //executionMessage.setOrderId(order.getOrderId());
    executionMessage.setOrdStatus(mappedOrderStatus);
    executionMessage.setTimeInForce(tif);
    executionMessage.setInputTime(updatedTimeLong);
    executionMessage.setPrice(MbxMath.changeScale(priceDouble, 8));
    executionMessage.setPriceScale((short) 8);
    executionMessage.setOrderQty(MbxMath.changeScale(qtyDouble, 6));
    executionMessage.setOrderQtyScale((short) 6);
    executionMessage.setCumQty(MbxMath.changeScale(cumExecQtyDouble, 6));
    executionMessage.setCumQtyScale((short) 6);
    executionMessage.setLeavesQty(executionMessage.getOrderQty() - executionMessage.getCumQty());
    executionMessage.setLeavesQtyScale((short) 6);
    executionMessage.setAvgPx(MbxMath.changeScale(avgPriceDouble, 8));
    executionMessage.setAvgPxScale((short) 8);


    LOGGER.debug("Updating execution report for clientOrderId: " + clientOrderId + " with status: " + orderStatus + ", posSide: " + posSide
        + ", holdMode: " + holdMode);
    subscription.updateExecutionReport(executionMessage);
    subscription.updateOrder(clientOrderId, mappedOrderStatus.name());

    return executionMessage;
  }

  /**
   * Extracts a specific field value from the feeDetail array Expected format: [{"feeCoin":"BTC","fee":"0.001"}]
   *
   * @param feeDetailJson The feeDetail array as JSON string
   * @param fieldName The field to extract (e.g., "feeCoin", "fee")
   * @return The field value, or null if not found
   */
  private String extractFeeDetailsFromArray(final String feeDetailJson, final String fieldName) {
    if (feeDetailJson == null || feeDetailJson.trim().isEmpty() || "[]".equals(feeDetailJson.trim())) {
      return null;
    }

    // Find first object in array
    final int objStart = feeDetailJson.indexOf('{');
    if (objStart < 0) {
      return null;
    }

    // Find matching closing brace
    int braceDepth = 0;
    int objEnd = -1;
    for (int i = objStart; i < feeDetailJson.length(); i++) {
      if (feeDetailJson.charAt(i) == '{') {
        braceDepth++;
      } else if (feeDetailJson.charAt(i) == '}') {
        braceDepth--;
        if (braceDepth == 0) {
          objEnd = i + 1;
          break;
        }
      }
    }

    if (objEnd <= objStart) {
      return null;
    }

    // Extract first object and get the field
    final String firstObjectJson = feeDetailJson.substring(objStart, objEnd);
    return minExtract(firstObjectJson, fieldName);
  }


  private OrdStatus mapOrderStatus(final String state) {
    if (state == null || state.isEmpty())
      return OrdStatus.NULL_VAL;
    return switch (state.toLowerCase()) {
      case "new" -> OrdStatus.NEW;
      case "partial_filled" -> OrdStatus.PARTIALLY_FILLED;
      case "filled" -> OrdStatus.FILLED;
      case "cancelled" -> OrdStatus.CANCELED;
      default -> OrdStatus.REJECTED;
    };
  }

  public static Side parseSide(final String side) {
    if (side == null) {
      return Side.NULL_VAL;
    }
    switch (side) {
      case "buy":
        return Side.BUY;
      case "sell":
        return Side.SELL;
      default:
        return Side.NULL_VAL;
    }
  }

  public static String[] parseSymbol(final String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("Invalid symbol");
    }

    // Step 1: remove suffix (futures like BTCUSDT_UMCBL)
    String clean = stripSuffix(symbol);

    // Step 2: find matching quote
    for (String quote : QUOTES) {
      if (clean.endsWith(quote)) {
        String base = clean.substring(0, clean.length() - quote.length());
        return new String[]{base, quote};
      }
    }

    throw new IllegalArgumentException("Unknown quote currency for symbol: " + symbol);
  }

  public static TimeInForce parseTif(final String tif) {
    if (tif == null) {
      return TimeInForce.NULL_VAL;
    }
    switch (tif) {
      case "ioc":
        return TimeInForce.IMMEDIATE_OR_CANCEL;
      case "fok":
        return TimeInForce.FILL_OR_KILL;
      case "gtc":
        return TimeInForce.GOOD_TILL_DATE;
      default:
        return TimeInForce.NULL_VAL;
    }
  }

  private static String stripSuffix(String symbol) {
    int idx = symbol.indexOf('_');
    if (idx > 0) {
      return symbol.substring(0, idx);
    }
    return symbol;
  }

  /**
   * Returns default quote currency based on symbol pattern
   * 
   * @param symbol Trading pair symbol
   * @return Default quote currency (USDC or USDT)
   */
  private String getDefaultQuotedCurrency(final String symbol) {
    if (symbol != null && symbol.contains("USDC")) {
      return "USDC";
    } else {
      return "USDT";
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BitgetExchangeInfoFull {
    private String code;
    private String msg;
    private long requestTime;
    @JsonProperty("data")
    private List<BitgetSymbolInfo> symbols;

    public List<BitgetSymbolInfo> getSymbols() {
      return symbols;
    }

    public void setSymbols(final List<BitgetSymbolInfo> symbols) {
      this.symbols = symbols;
    }

    // Getters and setters
    public String getCode() {
      return code;
    }

    public void setCode(final String code) {
      this.code = code;
    }

    public String getMsg() {
      return msg;
    }

    public void setMsg(final String msg) {
      this.msg = msg;
    }

    public long getRequestTime() {
      return requestTime;
    }

    public void setRequestTime(final long requestTime) {
      this.requestTime = requestTime;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BitgetSymbolInfo {
    private String symbol;
    private String baseCoin;
    private String quoteCoin;
    private String minTradeAmount;
    private String maxTradeAmount;
    private String takerFeeRate;
    private String makerFeeRate;
    private String pricePrecision;
    private String quantityPrecision;
    private String quotePrecision;
    private String status;
    private String minTradeUSDT;
    private String buyLimitPriceRatio;
    private String sellLimitPriceRatio;
    private String areaSymbol;
    private String orderQuantity;
    private String openTime;
    private String offTime;

    // Getters and setters
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

    public final String getMinTradeAmount() {
      return minTradeAmount;
    }

    public final void setMinTradeAmount(final String minTradeAmount) {
      this.minTradeAmount = minTradeAmount;
    }

    public final String getMaxTradeAmount() {
      return maxTradeAmount;
    }

    public final void setMaxTradeAmount(final String maxTradeAmount) {
      this.maxTradeAmount = maxTradeAmount;
    }

    public final String getTakerFeeRate() {
      return takerFeeRate;
    }

    public final void setTakerFeeRate(final String takerFeeRate) {
      this.takerFeeRate = takerFeeRate;
    }

    public final String getMakerFeeRate() {
      return makerFeeRate;
    }

    public final void setMakerFeeRate(final String makerFeeRate) {
      this.makerFeeRate = makerFeeRate;
    }

    public final String getPricePrecision() {
      return pricePrecision;
    }

    public final void setPricePrecision(final String pricePrecision) {
      this.pricePrecision = pricePrecision;
    }

    public final String getQuantityPrecision() {
      return quantityPrecision;
    }

    public final void setQuantityPrecision(final String quantityPrecision) {
      this.quantityPrecision = quantityPrecision;
    }

    public final String getQuotePrecision() {
      return quotePrecision;
    }

    public final void setQuotePrecision(final String quotePrecision) {
      this.quotePrecision = quotePrecision;
    }

    public final String getStatus() {
      return status;
    }

    public final void setStatus(final String status) {
      this.status = status;
    }

    public final String getMinTradeUSDT() {
      return minTradeUSDT;
    }

    public final void setMinTradeUSDT(final String minTradeUSDT) {
      this.minTradeUSDT = minTradeUSDT;
    }

    public final String getBuyLimitPriceRatio() {
      return buyLimitPriceRatio;
    }

    public final void setBuyLimitPriceRatio(final String buyLimitPriceRatio) {
      this.buyLimitPriceRatio = buyLimitPriceRatio;
    }

    public final String getSellLimitPriceRatio() {
      return sellLimitPriceRatio;
    }

    public final void setSellLimitPriceRatio(final String sellLimitPriceRatio) {
      this.sellLimitPriceRatio = sellLimitPriceRatio;
    }

    public final String getAreaSymbol() {
      return areaSymbol;
    }

    public final void setAreaSymbol(final String areaSymbol) {
      this.areaSymbol = areaSymbol;
    }

    public final String getOrderQuantity() {
      return orderQuantity;
    }

    public final void setOrderQuantity(final String orderQuantity) {
      this.orderQuantity = orderQuantity;
    }

    public final String getOpenTime() {
      return openTime;
    }

    public final void setOpenTime(final String openTime) {
      this.openTime = openTime;
    }

    public final String getOffTime() {
      return offTime;
    }

    public final void setOffTime(final String offTime) {
      this.offTime = offTime;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BitgetContractInfoFull {

    @JsonProperty("data")
    private List<BitgetContractInfo> contracts;

    public List<BitgetContractInfo> getContracts() {
      return contracts;
    }

    public void setContracts(List<BitgetContractInfo> contracts) {
      this.contracts = contracts;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BitgetContractInfo {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("baseCoin")
    private String baseCoin;

    @JsonProperty("quoteCoin")
    private String quoteCoin;

    @JsonProperty("symbolStatus")
    private String symbolStatus;

    @JsonProperty("pricePlace")
    private String pricePlace;

    @JsonProperty("volumePlace")
    private String volumePlace;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getBaseCoin() { return baseCoin; }
    public void setBaseCoin(String baseCoin) { this.baseCoin = baseCoin; }

    public String getQuoteCoin() { return quoteCoin; }
    public void setQuoteCoin(String quoteCoin) { this.quoteCoin = quoteCoin; }

    public String getSymbolStatus() { return symbolStatus; }
    public void setSymbolStatus(String symbolStatus) { this.symbolStatus = symbolStatus; }

    public String getPricePlace() { return pricePlace; }
    public void setPricePlace(String pricePlace) { this.pricePlace = pricePlace; }

    public String getVolumePlace() { return volumePlace; }
    public void setVolumePlace(String volumePlace) { this.volumePlace = volumePlace; }
  }

  public enum AccountMode {
    CLASSIC,
    UNIFIED
  }
}
