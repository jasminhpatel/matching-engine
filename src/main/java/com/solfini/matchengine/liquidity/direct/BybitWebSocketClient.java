package com.solfini.matchengine.liquidity.direct;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.ExecutionExchangeConfig;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.liquidity.LiquidityOrderRouter;
import com.solfini.matchengine.message.internal.WsOrderUpdate;
import com.solfini.util.HMAC;
import com.solfini.util.StringUtil;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.Proxy;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.solfini.common.Constants.*;

public class BybitWebSocketClient extends WebSocketClient {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BybitWebSocketClient.class);
  private static final String BYBIT_WEBSOCKET_ENDPOINT = Context.getBybitUnifiedWs() + "/private";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final String apiKey;
  private final String apiSecret;
  private final Proxy proxy;

  public static void startListener(final ExecutionExchangeConfig subscription) {
    if (subscription == null) {
      LOGGER.info(LOG_FMT_1, "No ByBit subscriptions available.");
      return;
    }
    final String proxyIp = ExternalExchangeUtil.getStickyProxy(subscription.getId());
    final Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, Context.getExternalExchangeProxyPort())); // OR Proxy.Type.SOCKS
    final Runnable wsTask = () -> {
      try {
        final BybitWebSocketClient client =
            new BybitWebSocketClient(new URI(BYBIT_WEBSOCKET_ENDPOINT), proxy, subscription.getApiKey(), subscription.getApiSecret());
        client.connectBlocking(); // Wait for connection
      } catch (Exception e) {
        e.printStackTrace();
      }
    };

    final Thread wsThread = new Thread(wsTask);
    wsThread.setName("ByBit-WS-Thread");
    wsThread.setDaemon(true);
    wsThread.start();
  }

  public BybitWebSocketClient(final URI serverUri, final Proxy proxy, final String apiKey, final String apiSecret) {
    super(serverUri);
    this.proxy = proxy;
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.setProxy(proxy);
  }

  @Override
  public void onOpen(final ServerHandshake handshake) {
    LOGGER.info(LOG_FMT_1, "Websocket connected to ByBit.");
    try {
      sendAuth();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  // TODO: change to direct JSON parsing
  public void onMessage(final String message) {
    LOGGER.info(LOG_FMT_2, "ByBit websocket message: ", message);

    // Subscribe only after auth success
    if (message.contains("\"success\":true") && message.contains("\"op\":\"auth\"")) {
      send("{\"op\":\"subscribe\",\"args\":[\"order\"]}");
    } else if (message.contains("\"topic\":\"order\"")) {
      OrderMessage msg = null;
      try {
        LOGGER.info(LOG_FMT_2, "ByBit order response message: ", message);
        msg = MAPPER.readValue(message, OrderMessage.class);
        for (final OrderData e : msg.getData()) {
          final WsOrderUpdate order = new WsOrderUpdate();
          order.setClOrdId(e.getOrderLinkId());
          order.setSymbol(e.getSymbol());
          order.setQuantity(StringUtil.toDouble(e.getCumExecQty()));
          order.setPrice(StringUtil.toDouble(e.getAvgPrice()));
          order.setFees(StringUtil.toDouble(e.getCumExecFee()));
          order.setFilled(OrderMessage.FILLED_TEXT.equalsIgnoreCase(e.getOrderStatus()));
          LiquidityOrderRouter.addWsOrder(order);
          LOGGER.info(LOG_FMT_2, "ByBit order response. clOrdId: ", order.getClOrdId());
        }
      } catch (JsonProcessingException e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }

  @Override
  public void onClose(int code, String reason, boolean remote) {
    LOGGER.info(LOG_FMT_2, "ByBit Websocket closed. :", reason);
    reconnectWithBackoff();
  }

  @Override
  public void onError(Exception ex) {
    LOGGER.info(LOG_FMT_2, "ByBit Websocket error. :", ex.getMessage());
  }

  private void sendAuth() throws Exception {
    final long expires = Instant.now().toEpochMilli() + 10000;
    final String signaturePayload = "GET/realtime" + expires;
    final String signature = HMAC.hmacSha256(signaturePayload, apiSecret);

    final String authPayload = String.format("{\"op\":\"auth\",\"args\":[\"%s\",\"%d\",\"%s\"]}", apiKey, expires, signature);

    send(authPayload);
  }

  private void reconnectWithBackoff() {
    new Timer().schedule(new TimerTask() {
      @Override
      public void run() {
        try {
          LOGGER.info(LOG_FMT_1, "[WebSocket] Attempting reconnect...");
          reconnect();
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, e);
        }
      }
    }, 5000); // Wait 5 seconds before retry
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class OrderMessage { // DTO for the response JSON
    public static String FILLED_TEXT = "Filled";
    private String topic;
    private String id;
    private long creationTime;
    private List<OrderData> data;

    public final String getTopic() {
      return topic;
    }

    public final void setTopic(final String topic) {
      this.topic = topic;
    }

    public final String getId() {
      return id;
    }

    public final void setId(final String id) {
      this.id = id;
    }

    public final long getCreationTime() {
      return creationTime;
    }

    public final void setCreationTime(final long creationTime) {
      this.creationTime = creationTime;
    }

    public final List<OrderData> getData() {
      return data;
    }

    public final void setData(final List<OrderData> data) {
      this.data = data;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class OrderData { // DTO for the response JSON
    private String orderLinkId;
    private String symbol;
    private String avgPrice;
    private String cumExecQty;
    private String cumExecValue;
    private String cumExecFee;
    private String orderStatus;
    private String feeCurrency;

    public final String getOrderLinkId() {
      return orderLinkId;
    }

    public final void setOrderLinkId(final String orderLinkId) {
      this.orderLinkId = orderLinkId;
    }

    public String getSymbol() {
      return symbol;
    }

    public void setSymbol(String symbol) {
      this.symbol = symbol;
    }

    public final String getAvgPrice() {
      return avgPrice;
    }

    public final void setAvgPrice(final String avgPrice) {
      this.avgPrice = avgPrice;
    }

    public final String getCumExecQty() {
      return cumExecQty;
    }

    public final void setCumExecQty(final String cumExecQty) {
      this.cumExecQty = cumExecQty;
    }

    public final String getCumExecValue() {
      return cumExecValue;
    }

    public final void setCumExecValue(final String cumExecValue) {
      this.cumExecValue = cumExecValue;
    }

    public final String getCumExecFee() {
      return cumExecFee;
    }

    public final void setCumExecFee(final String cumExecFee) {
      this.cumExecFee = cumExecFee;
    }

    public final String getOrderStatus() {
      return orderStatus;
    }

    public final void setOrderStatus(final String orderStatus) {
      this.orderStatus = orderStatus;
    }

    public String getFeeCurrency() {
      return feeCurrency;
    }

    public void setFeeCurrency(String feeCurrency) {
      this.feeCurrency = feeCurrency;
    }
  }
}
