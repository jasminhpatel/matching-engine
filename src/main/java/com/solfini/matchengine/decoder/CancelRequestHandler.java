package com.solfini.matchengine.decoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.solfini.common.Constants;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.pool.CancelOrderObjectPool;
import com.solfini.sbe.encoder.BusinessRejectReason;
import com.solfini.sbe.encoder.CancelOrderDecoder;
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
public class CancelRequestHandler implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(CancelRequestHandler.class);

  private long cancelOrderPriority = 0;

  public final Message decodeCancelRequest(final MessageHeaderDecoder headerDecoder, final CancelOrderDecoder cancelOrderDecoder,
      final long newCancelOrderId) {
    try {
      // validate
      if (cancelOrderDecoder.securityId() == 0) {
        return BusinessRejectMessage.createBusinessRejectWithCancelId(headerDecoder.senderCompId(), MsgType.ORDER_CANCEL_REQUEST,
            cancelOrderDecoder.clOrdID(), BusinessRejectReason.SECURITY_ID_IS_MISSING, SECURITY_ID_IS_MISSING,
            cancelOrderDecoder.cancelId(), 0, cancelOrderDecoder.secondaryOrderId(), cancelOrderDecoder.securityId(),
            newCancelOrderId, cancelOrderDecoder.submitterId());
      }

      final CancelOrder cancelOrder = CancelOrderObjectPool.get();
      cancelOrder.set(cancelOrderDecoder, newCancelOrderId, cancelOrderPriority++);
      cancelOrder.setSenderCompId(headerDecoder.senderCompId());
      cancelOrder.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());

      return parseCancelOrder(cancelOrder);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    return BusinessRejectMessage.createBusinessRejectWithCancelId(headerDecoder.senderCompId(), MsgType.ORDER_CANCEL_REQUEST,
        headerDecoder.senderCompId(), BusinessRejectReason.UNABLE_TO_PARSE, UNABLE_TO_PARSE, cancelOrderDecoder.cancelId(), 0,
        cancelOrderDecoder.secondaryOrderId(), cancelOrderDecoder.securityId(), newCancelOrderId, cancelOrderDecoder.submitterId());
  }

  public static final Message parseCancelOrder(final CancelOrder cancelOrder) {
    try {
      final InstrumentPair instrumentPair = InstrumentCache.getPair(cancelOrder.getSecurityId());

      if (instrumentPair == null) {
        return BusinessRejectMessage.createBusinessRejectWithCancelId(cancelOrder.getSenderCompId(), MsgType.ORDER_SINGLE,
            Long.toString(cancelOrder.getOrigOrderId()), BusinessRejectReason.UNKNOWN_SECURITY, INSTRUMENT_NOT_FOUND,
            cancelOrder.getOrigOrderId(), 0, cancelOrder.getSecondaryOrderId(), cancelOrder.getSecurityId(),
            cancelOrder.getCancelId(), cancelOrder.getSubmitterId());
      }

      final long priceLong = cancelOrder.getPrice();

      cancelOrder.setPriceInt((int) priceLong);

      long quantityLong = cancelOrder.getQty();
      final int quantityScale = cancelOrder.getQtyScale();
      if (instrumentPair.getQuantityScale() > quantityScale) {
        for (int i = 0; i < (instrumentPair.getQuantityScale() - quantityScale); i++)
          quantityLong = quantityLong * 10;
      } else if (instrumentPair.getQuantityScale() < quantityScale) {
        for (int i = 0; i < (quantityScale - instrumentPair.getQuantityScale()); i++)
          quantityLong = quantityLong / 10;
      }
      cancelOrder.setQuantityLong(quantityLong);
      cancelOrder.setQuantityOrigLong(quantityLong);

      try {
        final User user = UserCache.get(cancelOrder.getAccount());

        if (user != null)
          cancelOrder.setUser(user);
        else {
          return BusinessRejectMessage.createBusinessReject(cancelOrder.getSenderCompId(), MsgType.ORDER_CANCEL_REQUEST,
              Long.toString(cancelOrder.getCancelId()), BusinessRejectReason.USER_NOT_FOUND, USER_NOT_FOUND, cancelOrder.getOrigOrderId(),
              0, cancelOrder.getSecondaryOrderId(), cancelOrder.getSecurityId(), cancelOrder.getSubmitterId());
        }
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
        return BusinessRejectMessage.createBusinessReject(cancelOrder.getSenderCompId(), MsgType.ORDER_CANCEL_REQUEST,
            Long.toString(cancelOrder.getCancelId()), BusinessRejectReason.UNABLE_TO_LOAD_USER, UNABLE_TO_LOAD_USER,
            cancelOrder.getOrigOrderId(), 0, cancelOrder.getSecondaryOrderId(), cancelOrder.getSecurityId(), cancelOrder.getSubmitterId());
      }


      final OrdType ordType = cancelOrder.getOrdType();
      final Side side = cancelOrder.getSide();
      int type = 0;
      if (Side.BUY == side) {
        if (OrdType.LIMIT == ordType)
          type = BUY_LIMIT;
        else if (OrdType.MARKET == ordType)
          type = BUY_MARKET;
        else if (OrdType.SELECT == ordType)
          type = BUY_SELECT;
      } else if (Side.SELL == side) {
        if (OrdType.LIMIT == ordType)
          type = SELL_LIMIT;
        else if (OrdType.MARKET == ordType)
          type = SELL_MARKET;
        else if (OrdType.SELECT == ordType)
          type = SELL_SELECT;
      }
      cancelOrder.setType(type);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return cancelOrder;
  }
}
