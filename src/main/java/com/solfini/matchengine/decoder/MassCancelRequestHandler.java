package com.solfini.matchengine.decoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solfini.common.Constants;
import com.solfini.common.Message;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.sbe.encoder.BusinessRejectReason;
import com.solfini.sbe.encoder.MassCancelOrderDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.MsgType;

/**
 *
 * @author Chris Mack
 *
 */
public class MassCancelRequestHandler implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(MassCancelRequestHandler.class);

  private long cancelOrderPriority = 0;

  public final Message decodeCancelRequest(final MessageHeaderDecoder headerDecoder, final MassCancelOrderDecoder massCancelOrderDecoder,
      final long newCancelOrderId) {
    try {
      // validate
      // if (massCancelOrderDecoder.securityId() == 0) {
      //   return BusinessRejectMessage.createBusinessReject(headerDecoder.senderCompId(), MsgType.ORDER_MASS_CANCEL_REQUEST,
      //           massCancelOrderDecoder.clOrdID(), BusinessRejectReason.SECURITY_ID_IS_MISSING, SECURITY_ID_IS_MISSING,
      //           massCancelOrderDecoder.cancelId(), 0, massCancelOrderDecoder.secondaryOrderId(), massCancelOrderDecoder.securityId());
      // }

      final MassCancelOrder massCancelOrder = new MassCancelOrder(massCancelOrderDecoder, newCancelOrderId, cancelOrderPriority++);
      massCancelOrder.setSenderCompId(headerDecoder.senderCompId());
      massCancelOrder.setSecondaryOrderId(NewOrderSingleHandler.getSecondaryOrderId(massCancelOrder.getSecurityId()));
      massCancelOrder.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());

      return massCancelOrder;
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    return BusinessRejectMessage.createBusinessReject(headerDecoder.senderCompId(),
        MsgType.ORDER_MASS_CANCEL_REQUEST, massCancelOrderDecoder.clOrdID(), BusinessRejectReason.UNABLE_TO_PARSE, UNABLE_TO_PARSE,
        massCancelOrderDecoder.cancelId(), 0, massCancelOrderDecoder.secondaryOrderId(), massCancelOrderDecoder.securityId(),
        massCancelOrderDecoder.submitterId());
  }

}
