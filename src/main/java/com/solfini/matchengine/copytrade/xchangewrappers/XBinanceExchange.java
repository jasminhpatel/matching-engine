package com.solfini.matchengine.copytrade.xchangewrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.copytrade.CopyTrade;
import com.solfini.matchengine.copytrade.ExternalExchangeUtil;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.HttpUtils;
import com.solfini.util.MbxMath;
import com.solfini.util.StringUtil;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class XBinanceExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XBinanceExchange.class);
  private static String HMAC_SHA256_ALGORITHM = "HmacSHA256";
  private static String ACCESS_KEY_HEADER = "X-MBX-APIKEY";
  private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static String BORROW = "BORROW";
  private static String REPAY = "REPAY";

  public XBinanceExchange(final Exchange exchange) {
    super(exchange);
  }

  @Override
  public AccountService getAccountService() {
    return this.getAccountService();
  }

  public double getBalanceFromExchange(final String quoteCurrency) {
    //todo margin balance logic
    return super.getBalanceFromExchange(quoteCurrency);
  }

  public double getPriceFromExchange(final Instrument currencyPair, final Side side) {
    //todo price in futures
    return super.getPriceFromExchange(currencyPair, side);
  }

  public void placeOrder(final CopyTrade copyTrade) throws Exception {
    if (copyTrade.getSubscription().hasLeverage()) {
      processMarginOrder(copyTrade);
    } else {
      super.placeOrder(copyTrade);
    }
  }

  public void updateOrderStatus(final CopyTrade copyTrade) throws Exception {
    if (copyTrade.getSubscription().hasLeverage()) {
      updateMarginOrderStatus(copyTrade);
    } else {
      super.updateOrderStatus(copyTrade);
    }
  }

  private void processMarginOrder(final CopyTrade copyTrade) throws Exception {
    //using sideEffectType MARGIN_BUY and AUTO_REPAY
    placeMarginOrder(copyTrade);
    /*if (copyTrade.isToClose()) {
      //close positions
      placeMarginOrder(copyTrade);
      if (copyTrade.getBorrowedAmount() > 0) {
        String borrowedAmount = String.valueOf(copyTrade.getBorrowedAmount());
        String asset;
        String type = REPAY;
        if (Side.BUY == copyTrade.getSide()) {
          asset = copyTrade.getBaseSymbol();
        } else {
          asset = copyTrade.getQuotedSymbol();
        }
        //repay margin
        boolean repaid = borrowRepay(asset, borrowedAmount, type);
        if (!repaid) {
          copyTrade.setResult("FAILED to repay margin");
        } else {
          copyTrade.setRepaid(true);
        }
      }
    } else {
      double amountToBorrow ;
      String asset;
      String type = BORROW;
      if (Side.BUY == copyTrade.getSide()) {
        asset = copyTrade.getQuotedSymbol();
        double marketPrice = copyTrade.getxPrice().doubleValue() * (1.05); // 5% safety factor
        //borrow based on the percentage and market price
        amountToBorrow = MbxMath.roundToBestPrecision(marketPrice * copyTrade.getxQuantity().doubleValue() *
            ((copyTrade.getSubscription().getPercentage() - 10_000) /10_000D));
      } else {
        asset = copyTrade.getBaseSymbol();
        //borrow based on the percentage
        amountToBorrow = MbxMath.roundToBestPrecision(copyTrade.getxQuantity().doubleValue() *
            ((copyTrade.getSubscription().getPercentage() - 10_000) /10_000D));
      }

      //borrow margin
      boolean borrowed = borrowRepay(asset, String.valueOf(amountToBorrow), type);
      if (borrowed) {
        copyTrade.setBorrowedAmount(amountToBorrow);
        placeMarginOrder(copyTrade);
      }
    }*/
  }

  private void placeMarginOrder(final CopyTrade copyTrade) throws Exception {
    StringBuilder url = new StringBuilder();
    url.append("symbol=").append(copyTrade.getBaseSymbol().toUpperCase()).append(copyTrade.getQuotedSymbol().toUpperCase());
    url.append("&isolated=").append("TRUE");
    url.append("&side=").append(copyTrade.getSide() == Side.BUY ? "BUY": "SELL");
    url.append("&type=").append(copyTrade.getOrdType() == OrdType.MARKET ? "MARKET" : "LIMIT");//LIMIT, MARKET, STOP_LOSS, STOP_LOSS_LIMIT, TAKE_PROFIT, TAKE_PROFIT_LIMIT, LIMIT_MAKER
    url.append("&quantity=").append(copyTrade.getxQuantity());
   // url.append("&quoteOrderQty=").append();
    if (copyTrade.getOrdType() != OrdType.MARKET && copyTrade.getxPrice() != null)
      url.append("&price=").append(copyTrade.getxPrice());
    //url.append("&stopPrice=").append();//Used with STOP_LOSS, STOP_LOSS_LIMIT, TAKE_PROFIT, and TAKE_PROFIT_LIMIT orders.
    url.append("&newClientOrderId=").append(copyTrade.getClOrdId());
    //url.append("&icebergQty=").append();//Used with LIMIT, STOP_LOSS_LIMIT, and TAKE_PROFIT_LIMIT to create an iceberg order.
    url.append("&newOrderRespType=").append("FULL");//JSON. ACK, RESULT, or FULL, MARKET and LIMIT order types default to FULL, all other orders default to ACK.
    //url.append("&sideEffectType=").append("NO_SIDE_EFFECT");//NO_SIDE_EFFECT, MARGIN_BUY, AUTO_REPAY,AUTO_BORROW_REPAY; default NO_SIDE_EFFECT.
    if (copyTrade.isToClose()) {
      url.append("&sideEffectType=").append("AUTO_REPAY");
    } else {
      url.append("&sideEffectType=").append("MARGIN_BUY");
    }
    if (copyTrade.getOrdType() != OrdType.MARKET) {
      url.append("&timeInForce=").append("GTC");//GTC,IOC,FOK
    }
    //url.append("&selfTradePreventionMode=").append("EXPIRE_BOTH");//EXPIRE_TAKER, EXPIRE_MAKER, EXPIRE_BOTH, NONE
    //url.append("&autoRepayAtCancel=").append(true);//default true
    url.append("&recvWindow=").append(60000);
    url.append("&timestamp=").append(System.currentTimeMillis());

    String signature = getSignature(url.toString().getBytes(), exchange.getExchangeSpecification().getSecretKey().getBytes());
    url.append("&signature=").append(signature);

    url.insert(0, "/sapi/v1/margin/order?");
    url.insert(0, exchange.getDefaultExchangeSpecification().getSslUri());

    final String fullUrl = url.toString();

    LOGGER.info(Constants.LOG_FMT_2, "Sending margin order to exchange. URL: ", fullUrl);

    final Map<String, Object> headers = new HashMap<>();
    headers.put(ACCESS_KEY_HEADER, exchange.getExchangeSpecification().getApiKey());

    HttpUtils.Response response = HttpUtils.post(fullUrl, headers, null);
    if (response != null && (response.getCode() == 200 || response.getCode() == 201)) {
      final String returnValue = response.getData();
      OrderResponse orderResponse = OBJECT_MAPPER.readValue(returnValue, OrderResponse.class);
      copyTrade.setExternalId(String.valueOf(orderResponse.getOrderId()));
    }

    this.updateOrderStatus(copyTrade);
  }

  private boolean borrowRepay(final String asset, final String amount, final String type) throws Exception {
    boolean success = false;
    StringBuilder url = new StringBuilder();
    url.append("asset=").append(asset.toUpperCase());
    url.append("&isolated=").append("TRUE");
    url.append("&symbol=").append(asset.toUpperCase());//only if isolated=TRUE
    url.append("&amount=").append(amount);
    url.append("&type=").append(type);//BORROW, REPAY
    url.append("&recvWindow=").append(60000);
    url.append("&timestamp=").append(System.currentTimeMillis());

    String signature = getSignature(url.toString().getBytes(), exchange.getExchangeSpecification().getSecretKey().getBytes());
    url.append("&signature=").append(signature);

    url.insert(0, "/sapi/v1/margin/borrow-repay?");
    url.insert(0, exchange.getDefaultExchangeSpecification().getSslUri());

    final String fullUrl = url.toString();

    LOGGER.info(Constants.LOG_FMT_2, "Sending borrow/repay to exchange. URL: ", fullUrl);

    final Map<String, Object> headers = new HashMap<>();
    headers.put(ACCESS_KEY_HEADER, exchange.getExchangeSpecification().getApiKey());

    final HttpUtils.Response response = HttpUtils.post(fullUrl, headers, null);
    if (response != null && (response.getCode() == 200 || response.getCode() == 201)) {
      final String returnValue = response.getData();
      BorrowRepayResponse borrowRepayResponse = OBJECT_MAPPER.readValue(returnValue, BorrowRepayResponse.class);
      if (borrowRepayResponse.getTranId() > 0) {
        success = true;
        LOGGER.info(Constants.LOG_FMT_2, "Borrow/Repay successful. tranId: ", borrowRepayResponse.getTranId());
      }
    }
    success = true;
    return success;
  }

  private void updateMarginOrderStatus(final CopyTrade copyTrade) throws Exception {
    StringBuilder url = new StringBuilder();
    url.append("symbol=").append(copyTrade.getBaseSymbol().toUpperCase()).append(copyTrade.getQuotedSymbol().toUpperCase());
    url.append("&isolated=").append("TRUE");
    url.append("&orderId=").append(copyTrade.getExternalId());
    //url.append("&origClientOrderId=").append(copyTrade.getClOrdId());
    url.append("&recvWindow=").append(60000);
    url.append("&timestamp=").append(System.currentTimeMillis());

    String signature = getSignature(url.toString().getBytes(), exchange.getExchangeSpecification().getSecretKey().getBytes());
    url.append("&signature=").append(signature);

    url.insert(0, "/sapi/v1/margin/order?");
    url.insert(0, exchange.getDefaultExchangeSpecification().getSslUri());

    final String fullUrl = url.toString();

    LOGGER.info(Constants.LOG_FMT_2, "Sending get margin order status exchange. URL: ", fullUrl);

    Map<String, Object> headers = new HashMap<>();
    headers.put(ACCESS_KEY_HEADER, exchange.getExchangeSpecification().getApiKey());

    HttpUtils.Response response = HttpUtils.get(fullUrl, headers);
    if (response != null && (response.getCode() == 200 || response.getCode() == 201)) {
      final String returnValue = response.getData();
      MarginOrderStatus summary = OBJECT_MAPPER.readValue(returnValue, MarginOrderStatus.class);
      copyTrade.setPriceScale((short) 4);
      copyTrade.setPrice(MbxMath.changeScale(StringUtil.toDouble(summary.getPrice()), copyTrade.getPriceScale()));
      copyTrade.setOriginalAmount(StringUtil.toDouble(summary.getOrigQty()));
      copyTrade.setCumulativeAmount(StringUtil.toDouble(summary.getExecutedQty()));
      copyTrade.setStatus(summary.getStatus());
    }
  }

  private static String getSignature(final byte[] message, final byte[] keyData) throws Exception {
    final SecretKey key = new SecretKeySpec(keyData, HMAC_SHA256_ALGORITHM);
    final Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
    mac.init(key);
    mac.update(message);
    byte[] hmac = mac.doFinal();
    String hd;
    final BigInteger hash = new BigInteger(1, hmac);
    hd = hash.toString(16);
    while (hd.length() < 32) {
      hd = "0" + hd;
    }
    return hd;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class OrderResponse {
    private long orderId;

    public long getOrderId() {
      return orderId;
    }

    public void setOrderId(long orderId) {
      this.orderId = orderId;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BorrowRepayResponse {
    private long tranId;

    public long getTranId() {
      return tranId;
    }

    public void setTranId(long tranId) {
      this.tranId = tranId;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MarginOrderStatus {
    private String price;
    private String origQty;
    private String executedQty;
    private String status;

    public String getPrice() {
      return price;
    }

    public void setPrice(String price) {
      this.price = price;
    }

    public String getOrigQty() {
      return origQty;
    }

    public void setOrigQty(String origQty) {
      this.origQty = origQty;
    }

    public String getExecutedQty() {
      return executedQty;
    }

    public void setExecutedQty(String executedQty) {
      this.executedQty = executedQty;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }
  }
}
