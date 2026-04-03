package com.solfini.matchengine.liquidity.direct;

import static com.solfini.common.Constants.ERROR_PLEASE_TRY_AGAIN_LATER;
import static com.solfini.common.Constants.LOG_FMT_10;
import static com.solfini.common.Constants.LOG_FMT_2;
import static com.solfini.common.Constants.LOG_FMT_4;
import static com.solfini.common.Constants.LOG_FMT_8;
import static com.solfini.common.Constants.MARKET_IS_CLOSED;
import static com.solfini.common.Constants.SUCCESS;
import static com.solfini.common.Constants.SYMBOL_NOT_FOUND;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.solfini.matchengine.liquidity.SymbolBalance;
import com.solfini.matchengine.message.internal.WsOrderUpdate;
import com.solfini.util.*;
import org.knowm.xchange.currency.CurrencyPair;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.matchengine.executionexchange.ExternalCurrencyPairCache;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.ExternalInstrumentCache;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.matchengine.liquidity.LiquidityOrder;
import com.solfini.matchengine.liquidity.LiquidityOrderRouter;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.sbe.encoder.BusinessRejectReason;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.UserCache;
import com.solfini.util.HttpUtils.Response;

import static com.solfini.common.Constants.*;

public class Bybit {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LiquidityOrderRouter.class);
  public static final String BINANCE = "binance";
  public static final String BYBIT = "bybit";
  private static final int PROXY_PORT = 8888;
  private static final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
  private static final ManyToOneConcurrentArrayQueueCustom<Message> receiverToMatcherQueue = Context.getReceiverToMatcherQueue();
  /*
  private static final Map<String, MultiplierContractDetails> SYMBOL_TO_MULTIPLIER_CONTRACT_MAP = new HashMap<>();

  static {
    init();
  }

   */

  public static final Order sendOrderWithDirectHttp(final Side side, final boolean futuresEnabled, final Order order,
      final String bestQuoteSymbol, final String baseSymbol, final ExchangeSubscription subscription, final String exchange,
      final SymbolBalance selectedExchange, final double fxRate, final double orderValue, final Instrument bestQuoteInstrument) {
    try {
      long start = System.currentTimeMillis();
      final int priceScale = selectedExchange.getSymbolData().getPriceScale();
      final int qtyScale = selectedExchange.getSymbolData().getQtyScale();
      double xPrice = 0;
      double xQuantity = 0;
      long qtyUnits = 0;
      long priceUnits = 0;
      final double priceMul = Math.pow(10, priceScale);
      final double qtyMul = Math.pow(10, qtyScale);
      double rawQty = MbxMath.scaleDown(order.getQty(), order.getQtyScale());
      double safetyFactorBps = Context.getSafetyFactorBpsForLiquidity();
      if (baseSymbol != null) {
        safetyFactorBps = CMCTop30Checker.getBPS(baseSymbol);
      }
      double totalQtyToCoverFee = rawQty;
      if (side == Side.BUY) {
        xPrice = MbxMath
            .scaleDown((long) (order.getPrice() * fxRate * (1 + safetyFactorBps - Context.getProfitMarginBpsForLiquidity())
                / (1 + safetyFactorBps)), order.getPriceScale());
        if (!futuresEnabled) {
          // increase qty to cover the fee.
          totalQtyToCoverFee = rawQty * (1 + Context.getExternalExchangeTransactionFee());
          qtyUnits = Math.round(totalQtyToCoverFee * qtyMul);
          xQuantity = qtyUnits / qtyMul;
        } else {
          if (selectedExchange.getSymbolData().getMultiplierContract() > 0) {
            qtyUnits = (long) ((rawQty / selectedExchange.getSymbolData().getMultiplierContract()) * qtyMul);
          } else {
            qtyUnits = (long) (rawQty * qtyMul);
          }
          xQuantity = qtyUnits / qtyMul;
        }
        if (selectedExchange.getSymbolData().getMultiplierContract() > 0) {
          priceUnits = (long) Math.floor(xPrice * selectedExchange.getSymbolData().getMultiplierContract() * priceMul);
        } else {
          priceUnits = (long) Math.floor(xPrice * priceMul);
        }
      } else {
        xPrice = MbxMath.scaleDown(
            (long) (order.getPrice() * fxRate * (1 - (safetyFactorBps - Context.getProfitMarginBpsForLiquidity()))
                / (1 - safetyFactorBps)),
            order.getPriceScale());

        if (selectedExchange.getSymbolData().getMultiplierContract() > 0) {
          qtyUnits = (long) ((rawQty / selectedExchange.getSymbolData().getMultiplierContract()) * qtyMul);
          priceUnits = (long) Math.ceil(xPrice * selectedExchange.getSymbolData().getMultiplierContract() * priceMul);
        } else {
          qtyUnits = (long) (rawQty * qtyMul);
          priceUnits = (long) Math.ceil(xPrice * priceMul);
        }
        xQuantity = qtyUnits / qtyMul;
        // even if exchange have 1% lower continue.
        if (selectedExchange.getAvailableBalance() < xQuantity) {
          LOGGER.info(LOG_FMT_4, "Exchange doesn't have the full amount.  availableBalance:", selectedExchange.getAvailableBalance() , " xQuantity: ", xQuantity);
        }
        if (selectedExchange.getAvailableBalance() > 0) {
          xQuantity = Math.min(xQuantity, selectedExchange.getAvailableBalance());
        }
      }
      xPrice = priceUnits / priceMul;

      final String sPrice = StringUtil.toNumericString(xPrice);
      final String sQuantity = StringUtil.toNumericString(xQuantity);
      LOGGER.info(LOG_FMT_8, "xPrice ", xPrice, " sPrice: ", sPrice,  " xQuantity ", xQuantity, " sQuantity: ", sQuantity);
      String orderId = "";

      String multiplierContractBaseSymbol;
      if (selectedExchange.getSymbolData().getMultiplierContract() > 0) {
        multiplierContractBaseSymbol = selectedExchange.getSymbolData().getBase();
      } else {
        multiplierContractBaseSymbol = baseSymbol;
      }

      LOGGER.info(LOG_FMT_4, "Update pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
      final boolean reserved = subscription.reserve(orderValue, bestQuoteInstrument);
      LOGGER.info(LOG_FMT_6, "Pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue, " reserved: ", reserved);

      if (BYBIT.equalsIgnoreCase(exchange)) {
        if (!futuresEnabled) {
          final long timestamp = System.currentTimeMillis();
          final String tsString = String.valueOf(timestamp);
          final String symbol = baseSymbol + bestQuoteSymbol;
          final String sideStr = (side == Side.BUY) ? "Buy" : "Sell";
          final StringBuilder sb = new StringBuilder();
          sb.append('{').append("\"category\":\"spot\",").append("\"symbol\":\"").append(symbol).append("\",").append("\"side\":\"")
              .append(sideStr).append("\",").append("\"orderType\":\"Limit\",").append("\"qty\":\"").append(sQuantity)
              .append("\",").append("\"price\":\"").append(sPrice).append("\",").append("\"timeInForce\":\"FOK\",")
              .append("\"orderLinkId\":\"").append(order.getClOrdId()).append('"').append('}');
          final String body = sb.toString();

          final String RECV_WINDOW = Context.getBybitExchangeRecvWindow();

          final Map<String, Object> headers = new HashMap<>(5);
          headers.put("X-BAPI-API-KEY", subscription.getApiKey());
          headers.put("X-BAPI-TIMESTAMP", tsString);
          headers.put("X-BAPI-RECV-WINDOW", RECV_WINDOW);

          final String preSign = tsString + subscription.getApiKey() + RECV_WINDOW + body;
          final String signature = HMAC.hmacSha256(preSign, subscription.getApiSecret());
          headers.put("X-BAPI-SIGN", signature);
          headers.put("Content-Type", "application/json");

          final String url = Context.getBybitExchangeBaseUrl() + "/v5/order/create";
          final Response resp =
              HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
          final long endTime = System.currentTimeMillis();
          final boolean outOfWindow = endTime > (timestamp + StringUtil.toLong(RECV_WINDOW));
          LOGGER.info(LOG_FMT_10, "Time taken for bybit: ", (endTime - timestamp), " recWindow: ", RECV_WINDOW, " outOfRecWindow: ",
              outOfWindow, " timestamp: ", timestamp, " endTime: ", endTime);
          if (resp != null) {
            if (resp.getCode() == 200) {
              final JsonObject root = JsonParser.parseString(resp.getData()).getAsJsonObject();
              final String retMsg = root.get("retMsg").getAsString();
              if ("OK".equalsIgnoreCase(retMsg)) {
                final JsonObject result = root.getAsJsonObject("result");
                orderId = result.get("orderId").getAsString();
              } else {
                LOGGER.error("Exchange Exception, Liquidity trade failed. clOrdId: " + order.getClOrdId() + " " + retMsg);
                matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(),
                    MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()), BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE,
                    ERROR_PLEASE_TRY_AGAIN_LATER, order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(),
                    order.getSecurityId(), order.getSubmitterId()));
                order.setRejected(true);
                LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
                subscription.release(orderValue, bestQuoteInstrument);
                LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
                return order;
              }
            } else {
              LOGGER.error("Exchange Exception, Liquidity trade failed. clOrdId: " + order.getClOrdId());
              matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(),
                  MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()), BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE,
                  ERROR_PLEASE_TRY_AGAIN_LATER, order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(),
                  order.getSecurityId(), order.getSubmitterId()));
              order.setRejected(true);
              LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
              subscription.release(orderValue, bestQuoteInstrument);
              LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
              return order;
            }
          } else {
            LOGGER.error("Exchange Exception, Liquidity trade failed. clOrdId: " + order.getClOrdId());
            matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
                Long.toString(order.getOrderId()), BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE, ERROR_PLEASE_TRY_AGAIN_LATER,
                order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
            order.setRejected(true);
            LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
            subscription.release(orderValue, bestQuoteInstrument);
            LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
            return order;
          }
        } else {
          String symbol;

          if ("USDC".equalsIgnoreCase(bestQuoteSymbol)) {
            symbol = multiplierContractBaseSymbol + "PERP";
          } else {
            symbol = multiplierContractBaseSymbol + bestQuoteSymbol;
          }
          final long timestamp = System.currentTimeMillis();
          final String tsString = String.valueOf(timestamp);
          final String sideStr = (side == Side.BUY) ? "Buy" : "Sell";
          final StringBuilder sb = new StringBuilder();
          sb.append('{').append("\"category\":\"linear\",").append("\"symbol\":\"").append(symbol.toUpperCase()).append("\",").append("\"side\":\"")
              .append(sideStr).append("\",").append("\"orderType\":\"Limit\",").append("\"qty\":\"").append(sQuantity)
              .append("\",").append("\"price\":\"").append(sPrice).append("\",").append("\"timeInForce\":\"FOK\",")
              .append("\"orderLinkId\":\"").append(order.getClOrdId()).append('"').append('}');
          final String body = sb.toString();

          LOGGER.info(LOG_FMT_2, "Request Body: ", body);

          final String RECV_WINDOW = Context.getBybitExchangeRecvWindow();

          final Map<String, Object> headers = new HashMap<>();
          headers.put("X-BAPI-API-KEY", subscription.getApiKey());
          headers.put("X-BAPI-TIMESTAMP", tsString);
          headers.put("X-BAPI-RECV-WINDOW", RECV_WINDOW);

          final String preSign = tsString + subscription.getApiKey() + RECV_WINDOW + body;
          final String signature = HMAC.hmacSha256(preSign, subscription.getApiSecret());
          headers.put("X-BAPI-SIGN", signature);
          headers.put("Content-Type", "application/json");

          final String url = Context.getBybitExchangeBaseUrl() + "/v5/order/create";
          final Response resp =
              HttpUtils.post(url, headers, body.getBytes(StandardCharsets.UTF_8), subscription.getLastUsedProxy(), PROXY_PORT, subscription.isForceToUseProxy());
          final long endTime = System.currentTimeMillis();
          final boolean outOfWindow = endTime > (timestamp + StringUtil.toLong(RECV_WINDOW));
          LOGGER.info(LOG_FMT_10, "Time taken for bybit: ", (endTime - timestamp), " recWindow: ", RECV_WINDOW, " outOfRecWindow: ",
              outOfWindow, " timestamp: ", timestamp, " endTime: ", endTime);
          if (resp != null) {
            if (resp.getCode() == 200) {
              JsonObject root = JsonParser.parseString(resp.getData()).getAsJsonObject();
              final String retMsg = root.get("retMsg").getAsString();
              if ("OK".equalsIgnoreCase(retMsg)) {
                JsonObject result = root.getAsJsonObject("result");
                orderId = result.get("orderId").getAsString();
              } else {
                LOGGER.error("Exchange Exception, Liquidity trade failed. clOrdId: " + order.getClOrdId() + " " + retMsg);
                matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(),
                    MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()), BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE,
                    ERROR_PLEASE_TRY_AGAIN_LATER, order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(),
                    order.getSecurityId(), order.getSubmitterId()));
                order.setRejected(true);
                LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
                subscription.release(orderValue, bestQuoteInstrument);
                LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
                return order;
              }
            } else {
              LOGGER.error("Exchange Exception, Liquidity trade failed. clOrdId: " + order.getClOrdId());
              matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(),
                  MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()), BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE,
                  ERROR_PLEASE_TRY_AGAIN_LATER, order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(),
                  order.getSecurityId(), order.getSubmitterId()));
              order.setRejected(true);
              LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
              subscription.release(orderValue, bestQuoteInstrument);
              LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
              return order;
            }
          } else {
            LOGGER.error("Exchange Exception, Liquidity trade failed. clOrdId: " + order.getClOrdId());
            matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
                Long.toString(order.getOrderId()), BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE, ERROR_PLEASE_TRY_AGAIN_LATER,
                order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
            order.setRejected(true);
            LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
            subscription.release(orderValue, bestQuoteInstrument);
            LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
            return order;
          }
        }
      }
      long end = System.currentTimeMillis();
      LOGGER.info(LOG_FMT_2, "External order - direct http request to exchange time ", (end - start));
      start = end;
      // check websocket order update
      long wsStatusPollingStart = System.currentTimeMillis();
      do {
        LOGGER.info(LOG_FMT_2, "External order - get order status ", order.getClOrdId());
        final WsOrderUpdate wsOrder = LiquidityOrderRouter.getWsOrder(order.getClOrdId());
        if (wsOrder != null && wsOrder.isFilled()) {
          order.setExecuted(true);
          //don't override the qty
          order.setQuantityOrigLong(MbxMath.changeScale(wsOrder.getQuantity(), order.getQtyScale()));
          order.setQuantityOrigScale(order.getQtyScale());
          order.setPrice2(MbxMath.changeScale(wsOrder.getPrice(), order.getPriceScale()), order.getPriceScale());
          order.setSymbol(baseSymbol + bestQuoteSymbol);// todo check if _perps is required
          final Instrument fee = (wsOrder.getFeeCurrency() != null && !wsOrder.getFeeCurrency().isEmpty() && wsOrder.getSymbol().startsWith(wsOrder.getFeeCurrency()))  ?
              InstrumentCache.getBySymbol(wsOrder.getFeeCurrency()) : InstrumentCache.getBySymbol(bestQuoteSymbol);

          order.setFeeAccumulatedQuantity(MbxMath.changeScale(wsOrder.getFees(), fee.getQuantityScale()));
          order.setAssetId(fee.getId());

          receiverToMatcherQueue.addGuaranteed(order);
          LOGGER.info(Constants.LOG_FMT_20, "Liquidity trade successful. clOrdId: ", order.getClOrdId(), " symbol: ", baseSymbol.toUpperCase(), "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled,
              " side: ", order.getSide().name(), " quantity: ", wsOrder.getQuantity(), " averagePrice:", wsOrder.getPrice()
              , " xPrice: ", xPrice, " xQuantity: ", xQuantity, " result: ", "success");
          LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
          subscription.release(orderValue, bestQuoteInstrument);
          LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
          return order;
        }

        Thread.sleep(50);
      } while (wsStatusPollingStart < System.currentTimeMillis() - TWO_SECOND);

      final XExchange xExchange = ExternalExchangeUtil.createXExchange(subscription);

      if (xExchange == null) {
        LOGGER.info(Constants.LOG_FMT_6, "Order rejected. clOrdId: ", order.getClOrdId(), " invalid exchange: ", exchange,
            " futuresEnabled: ", futuresEnabled);

        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.MARKET_IS_CLOSED, MARKET_IS_CLOSED, order.getOrderId(),
            order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
        order.setRejected(true);
        return order;
      }

      final CurrencyPair currencyPair = ExternalCurrencyPairCache.get(multiplierContractBaseSymbol, bestQuoteSymbol);
      LOGGER.info(LOG_FMT_8, " Exchange: ", exchange, " baseSymbol: ", baseSymbol, " bestQuoteSymbol: ", bestQuoteSymbol,
          " futuresEnabled: ", futuresEnabled);

      final org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, xExchange, currencyPair, futuresEnabled);
      if (instrument == null) {
        LOGGER.info(Constants.LOG_FMT_8, "Order rejected. clOrdId: ", order.getClOrdId(), " invalid instrument: ", baseSymbol, " ",
            bestQuoteSymbol, " futuresEnabled: ", futuresEnabled);

        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.SYMBOL_NOT_FOUND, SYMBOL_NOT_FOUND, order.getOrderId(),
            order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
        order.setRejected(true);
        return order;
      }

      final LiquidityOrder liquidityOrder = new LiquidityOrder();
      liquidityOrder.setUserId(UserCache.getMarketMakerUser().getId());
      liquidityOrder.setSecurityId(order.getSecurityId());
      // liquidityOrder.setSubscriptionId(subscription.getId());
      liquidityOrder.setBaseSymbol(baseSymbol);
      liquidityOrder.setQuotedSymbol(bestQuoteSymbol);
      // liquidityOrder.setOrigClOrdId();
      liquidityOrder.setClOrdId(order.getClOrdId());
      liquidityOrder.setExchange(exchange);
      liquidityOrder.setSide(side);
      liquidityOrder.setOrdType(order.getOrdType());
      liquidityOrder.setTimeInForce(order.getTimeInForce());

      liquidityOrder.setxExchange(xExchange);
      liquidityOrder.setFuturesEnabled(futuresEnabled);
      liquidityOrder.setInstrument(instrument);
      liquidityOrder.setxPrice(BigDecimal.valueOf(xPrice));
      liquidityOrder.setxQuantity(BigDecimal.valueOf(xQuantity));
      liquidityOrder.setSubscription(subscription);
      liquidityOrder.setExternalId(orderId);

      xExchange.requestGetOrderStatus(liquidityOrder);

      end = System.currentTimeMillis();
      LOGGER.info(LOG_FMT_2, "External order - get order time ", (end - start));
      start = end;

      if (SUCCESS.equals(liquidityOrder.getResult())) {
        order.setExecuted(true);
        // don't override the qty
        order.setQuantityOrigLong(MbxMath.changeScale(liquidityOrder.getCumulativeAmount(), order.getQtyScale()));
        order.setQuantityOrigScale(order.getQtyScale());
        order.setPrice2(MbxMath.changeScale(liquidityOrder.getAveragePrice(), order.getPriceScale()), order.getPriceScale());
        order.setSymbol(instrument.toString());
        Instrument fee = InstrumentCache.getBySymbol(bestQuoteSymbol);
        order.setFeeAccumulatedQuantity(MbxMath.changeScale(liquidityOrder.getFee(), fee.getQuantityScale()));
        order.setAssetId(fee.getId());
        // if (futuresEnabled) {
        // perpPositions.add(order.getSide() == Side.BUY ? liquidityOrder.getCumulativeAmount() : -liquidityOrder.getCumulativeAmount());
        // } else {
        // spotPositions.add(order.getSide() == Side.BUY ? liquidityOrder.getCumulativeAmount() : -liquidityOrder.getCumulativeAmount());
        // }
        // sb.append("\nAfter order: spotPositions: ").append(spotPositions.doubleValue()).append(", perpPositions:
        // ").append(perpPositions.doubleValue());
        // send back to matching thread
        // LOGGER.info(sb.toString());
        receiverToMatcherQueue.addGuaranteed(order);
        LOGGER.info(Constants.LOG_FMT_20, "Liquidity trade successful. clOrdId: ", liquidityOrder.getClOrdId(), " symbol: ",
            baseSymbol, "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ", liquidityOrder.getSide().name(),
            " quantity: ", liquidityOrder.getCumulativeAmount(), " averagePrice:", liquidityOrder.getAveragePrice(), " xPrice: ", xPrice,
            " xQuantity: ", xQuantity, " result: ", liquidityOrder.getResult());
        LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
        subscription.release(orderValue, bestQuoteInstrument);
        LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
        return order;
      } else {
        LOGGER.info(Constants.LOG_FMT_18, "Liquidity trade rejected. clOrdId: ", liquidityOrder.getClOrdId(), " symbol: ",
            baseSymbol, "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ", liquidityOrder.getSide().name(),
            " quantity: ", liquidityOrder.getCumulativeAmount(), " price: ", xPrice, " quantity: ", xQuantity, " result: ",
            liquidityOrder.getResult());
        matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(order.getOrderId()), BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE, ERROR_PLEASE_TRY_AGAIN_LATER,
            order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
        order.setRejected(true);
        LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
        subscription.release(orderValue, bestQuoteInstrument);
        LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
        return order;
      }
    } catch (Exception e) {
      LOGGER.error("Error, Liquidity trade failed. clOrdId: " + order.getClOrdId(), e);
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE, ERROR_PLEASE_TRY_AGAIN_LATER,
          order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
      order.setRejected(true);
      LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
      subscription.release(orderValue, bestQuoteInstrument);
      LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
      return order;
    }
  }

  private static final org.knowm.xchange.instrument.Instrument getInstrument(final String exchange, final XExchange xExchange,
      final CurrencyPair currencyPair, final boolean isFuture) {
    final String key = (exchange + "_" + currencyPair.toString() + "_" + (isFuture ? "1" : "0")).toLowerCase();
    org.knowm.xchange.instrument.Instrument instrument = ExternalInstrumentCache.getInstrument(key);
    if (instrument != null) {
      return instrument;
    } else {
      instrument = xExchange.getInstrument(currencyPair, isFuture);
      ExternalInstrumentCache.addInstrument(key, instrument);
      return instrument;
    }
  }

  /*
  private static final void init() {
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BABYDOGEUSDT", new MultiplierContractDetails("1000000BABYDOGEUSDT", 1000000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("CHEEMSUSDT", new MultiplierContractDetails("1000000CHEEMSUSDT", 1000000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("MOGUSDT", new MultiplierContractDetails("1000000MOGUSDT", 1000000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("PEIPEIUSDT", new MultiplierContractDetails("1000000PEIPEIUSDT", 1000000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("COQUSDT", new MultiplierContractDetails("10000COQUSDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("ELONUSDT", new MultiplierContractDetails("10000ELONUSDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("LADYSUSDT", new MultiplierContractDetails("10000LADYSUSDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("QUBICUSDT", new MultiplierContractDetails("10000QUBICUSDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("SATSUSDT", new MultiplierContractDetails("10000SATSUSDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("WENUSDT", new MultiplierContractDetails("10000WENUSDT", 10000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BONKUSDC", new MultiplierContractDetails("1000BONKPERP", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BONKUSDT", new MultiplierContractDetails("1000BONKUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("BTTUSDT", new MultiplierContractDetails("1000BTTUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("CATUSDT", new MultiplierContractDetails("1000CATUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("FLOKIUSDT", new MultiplierContractDetails("1000FLOKIUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("LUNCUSDT", new MultiplierContractDetails("1000LUNCUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("NEIROCTOUSDC", new MultiplierContractDetails("1000NEIROCTOPERP", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("NEIROCTOUSDT", new MultiplierContractDetails("1000NEIROCTOUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("PEPEUSDC", new MultiplierContractDetails("1000PEPEPERP", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("PEPEUSDT", new MultiplierContractDetails("1000PEPEUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("RATSUSDT", new MultiplierContractDetails("1000RATSUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("TAGUSDT", new MultiplierContractDetails("1000TAGUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("TOSHIUSDT", new MultiplierContractDetails("1000TOSHIUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("TURBOUSDT", new MultiplierContractDetails("1000TURBOUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("XECUSDT", new MultiplierContractDetails("1000XECUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("XUSDT", new MultiplierContractDetails("1000XUSDT", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("SHIBUSDC", new MultiplierContractDetails("SHIB1000PERP", 1000));
    SYMBOL_TO_MULTIPLIER_CONTRACT_MAP.put("SHIBUSDT", new MultiplierContractDetails("SHIB1000USDT", 1000));
  }

  public static class MultiplierContractDetails {
    private String symbol;
    private int multiplier;

    public MultiplierContractDetails(String symbol, int multiplier) {
      this.symbol = symbol;
      this.multiplier = multiplier;
    }

    public String getSymbol() {
      return symbol;
    }

    public void setSymbol(String symbol) {
      this.symbol = symbol;
    }

    public int getMultiplier() {
      return multiplier;
    }

    public void setMultiplier(int multiplier) {
      this.multiplier = multiplier;
    }
  }

   */
}
