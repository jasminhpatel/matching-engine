package com.solfini.matchengine.decoder;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.sbe.encoder.BusinessRejectReason;
import com.solfini.sbe.encoder.CancelReplaceOrderDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserCache;

/**
 *
 * @author Chris Mack
 *
 */
public class CancelReplaceRequestHandler implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(CancelReplaceRequestHandler.class);

  private long cancelOrderPriority = 0;

  public final Message decodeCancelReplaceRequest(final MessageHeaderDecoder headerDecoder,
      final CancelReplaceOrderDecoder cancelReplaceOrderDecoder, final long newCancelOrderId, final long newOrderId) {
    try {
      // validate
      if (cancelReplaceOrderDecoder.securityId() == 0) {
        return BusinessRejectMessage.createBusinessRejectWithCancelReplacelId(headerDecoder.senderCompId(),
            MsgType.ORDER_CANCEL_REPLACE_REQUEST, headerDecoder.senderCompId(), BusinessRejectReason.SECURITY_ID_IS_MISSING,
            SECURITY_ID_IS_MISSING, cancelReplaceOrderDecoder.cancelId(), 0, cancelReplaceOrderDecoder.secondaryOrderId(),
            cancelReplaceOrderDecoder.securityId(), newCancelOrderId, newOrderId);
      }

      final CancelReplaceOrder cancelReplaceOrder =
          new CancelReplaceOrder(cancelReplaceOrderDecoder, newCancelOrderId, cancelOrderPriority++, newOrderId, headerDecoder.kafkaRecordOffset());
      cancelReplaceOrder.setSenderCompId(headerDecoder.senderCompId());
      cancelReplaceOrder.setSecondaryOrderId(NewOrderSingleHandler.getSecondaryOrderId(cancelReplaceOrder.getSecurityId()));


      return parseCancelOrder(cancelReplaceOrder);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    return BusinessRejectMessage.createBusinessRejectWithCancelReplacelId(headerDecoder.senderCompId(),
        MsgType.ORDER_CANCEL_REPLACE_REQUEST, headerDecoder.senderCompId(), BusinessRejectReason.UNABLE_TO_PARSE,
        UNABLE_TO_PARSE, cancelReplaceOrderDecoder.cancelId(), 0, cancelReplaceOrderDecoder.secondaryOrderId(),
        cancelReplaceOrderDecoder.securityId(), newCancelOrderId, newOrderId);
  }

  public static final Message parseCancelOrder(final CancelReplaceOrder cancelReplaceOrder) {
    try {
      final InstrumentPair instrumentPair = InstrumentCache.getPair(cancelReplaceOrder.getSecurityId());

      long priceLong = cancelReplaceOrder.getPrice();
      final int priceScale = cancelReplaceOrder.getPriceScale();
      if (instrumentPair.getPriceScale() > priceScale) {
        for (int i = 0; i < (instrumentPair.getPriceScale() - priceScale); i++)
          priceLong = priceLong * 10;
      } else if (instrumentPair.getPriceScale() < priceScale) {
        for (int i = 0; i < (priceScale - instrumentPair.getPriceScale()); i++)
          priceLong = priceLong / 10;
      }
      cancelReplaceOrder.setPriceInt((int) priceLong);

      long quantityLong = cancelReplaceOrder.getQty();
      final int quantityScale = cancelReplaceOrder.getQtyScale();
      if (instrumentPair.getQuantityScale() > quantityScale) {
        for (int i = 0; i < (instrumentPair.getQuantityScale() - quantityScale); i++)
          quantityLong = quantityLong * 10;
      } else if (instrumentPair.getQuantityScale() < quantityScale) {
        for (int i = 0; i < (quantityScale - instrumentPair.getQuantityScale()); i++)
          quantityLong = quantityLong / 10;
      }
      cancelReplaceOrder.setQuantityLong(quantityLong);
      cancelReplaceOrder.setQuantityOrigLong(quantityLong);


      long price2Long = cancelReplaceOrder.getPrice2();
      final int price2Scale = cancelReplaceOrder.getPrice2Scale();
      if (instrumentPair.getPriceScale() > price2Scale) {
        for (int i = 0; i < (instrumentPair.getPriceScale() - price2Scale); i++)
          price2Long = price2Long * 10;
      } else if (instrumentPair.getPriceScale() < price2Scale) {
        for (int i = 0; i < (price2Scale - instrumentPair.getPriceScale()); i++)
          price2Long = price2Long / 10;
      }
      cancelReplaceOrder.setPrice2Int((int) price2Long);

      long quantity2Long = cancelReplaceOrder.getQty2();
      final int quantity2Scale = cancelReplaceOrder.getQty2Scale();
      if (instrumentPair.getQuantityScale() > quantity2Scale) {
        for (int i = 0; i < (instrumentPair.getQuantityScale() - quantity2Scale); i++)
          quantity2Long = quantity2Long * 10;
      } else if (instrumentPair.getQuantityScale() < quantity2Scale) {
        for (int i = 0; i < (quantity2Scale - instrumentPair.getQuantityScale()); i++)
          quantity2Long = quantity2Long / 10;
      }
      cancelReplaceOrder.setQuantityLong(quantity2Long);
      cancelReplaceOrder.setQuantityOrigLong(quantityLong);

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_2, ">>> parse CancelReplaceOrder", cancelReplaceOrder);
      }
      try {
        final User user = UserCache.get(cancelReplaceOrder.getAccount());
        if (user != null)
          cancelReplaceOrder.setUser(user);
        else {
          return BusinessRejectMessage.createBusinessRejectWithCancelReplacelId(cancelReplaceOrder.getSenderCompId(),
              MsgType.ORDER_CANCEL_REPLACE_REQUEST, Long.toString(cancelReplaceOrder.getCancelId()),
              BusinessRejectReason.USER_NOT_FOUND, USER_NOT_FOUND, cancelReplaceOrder.getOrigOrderId(), 0,
              cancelReplaceOrder.getSecondaryOrderId(), cancelReplaceOrder.getSecurityId(),
              cancelReplaceOrder.getCancelId(), cancelReplaceOrder.getNewOrderId());
        }
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
        return BusinessRejectMessage.createBusinessRejectWithCancelReplacelId(cancelReplaceOrder.getSenderCompId(),
            MsgType.ORDER_CANCEL_REPLACE_REQUEST, Long.toString(cancelReplaceOrder.getCancelId()),
            BusinessRejectReason.UNABLE_TO_LOAD_USER, UNABLE_TO_LOAD_USER, cancelReplaceOrder.getOrigOrderId(), 0,
            cancelReplaceOrder.getSecondaryOrderId(), cancelReplaceOrder.getSecurityId(),
            cancelReplaceOrder.getCancelId(), cancelReplaceOrder.getNewOrderId());
      }


      final OrdType ordType = cancelReplaceOrder.getOrdType();
      final Side side = cancelReplaceOrder.getSide();
      int type = 0;
      if (Side.BUY == side) {
        if (OrdType.LIMIT == ordType)
          type = BUY_LIMIT;
        else if (OrdType.MARKET == ordType)
          type = BUY_MARKET;
      } else if (Side.SELL == side) {
        if (OrdType.LIMIT == ordType)
          type = SELL_LIMIT;
        else if (OrdType.MARKET == ordType)
          type = SELL_MARKET;
      }
      cancelReplaceOrder.setType(type); // TODO: this isn't being used

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return cancelReplaceOrder;
  }
}
