package com.solfini.matchengine.decoder;

import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.internal.admin.schema.AdminAcknowledgementDecoder;
import com.solfini.internal.admin.schema.BalanceAdminMessageDecoder;
import com.solfini.internal.admin.schema.FIXUserAdminMessageDecoder;
import com.solfini.internal.admin.schema.FeeAdminMessageDecoder;
import com.solfini.internal.admin.schema.FundingRateCalcAdminMessageDecoder;
import com.solfini.internal.admin.schema.GlobalStateAdminMessageDecoder;
import com.solfini.internal.admin.schema.MessageHeaderDecoder;
import com.solfini.internal.admin.schema.SecurityDefinitionAdminMessageDecoder;
import com.solfini.internal.admin.schema.SnapResponseAdminMessageDecoder;
import com.solfini.internal.admin.schema.TradeStateAdminMessageDecoder;
import com.solfini.internal.admin.schema.UserAdminMessageDecoder;
import com.solfini.matchengine.message.admin.AckAdminMessage;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.FIXUserAdminMessage;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.admin.FundingRateCalcMessage;
import com.solfini.matchengine.message.admin.GlobalStateAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.SnapResponseAdminMessage;
import com.solfini.matchengine.message.admin.TradeStateAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;

public class OutboundAdminMessageHandler implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(OutboundAdminMessageHandler.class);
  private static final UserAdminMessageDecoder USER_DECODER = new UserAdminMessageDecoder();
  private static final BalanceAdminMessageDecoder BALANCE_DECODER = new BalanceAdminMessageDecoder();
  private static final FeeAdminMessageDecoder FEE_DECODER = new FeeAdminMessageDecoder();
  private static final TradeStateAdminMessageDecoder TRADE_STATE_DECODER = new TradeStateAdminMessageDecoder();
  private static final FIXUserAdminMessageDecoder FIX_USER_DECODER = new FIXUserAdminMessageDecoder();
  private static final SecurityDefinitionAdminMessageDecoder SECURITY_DEFINITION_DECODER = new SecurityDefinitionAdminMessageDecoder();
  private static final AdminAcknowledgementDecoder ADMIN_ACK_DECODER = new AdminAcknowledgementDecoder();
  private static final FundingRateCalcAdminMessageDecoder FUNDING_RATE_DECODER = new FundingRateCalcAdminMessageDecoder();
  private static final GlobalStateAdminMessageDecoder GLOBAL_STATE_ADMIN_DECODER = new GlobalStateAdminMessageDecoder();
  private static final SnapResponseAdminMessageDecoder SNAP_RESPONSE_DECODER = new SnapResponseAdminMessageDecoder();

  private OutboundAdminMessageHandler() {
    // default constructor
  }

  public final AckAdminMessage decodeAdminAck(final UnsafeBuffer unsafeBuffer, final MessageHeaderDecoder messageHeaderDecoder,
      final int connectionId) {

    ADMIN_ACK_DECODER.wrap(unsafeBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    return new AckAdminMessage(ADMIN_ACK_DECODER, connectionId);
  }

  public final UserAdminMessage decodeAdminUserUpdate(final UnsafeBuffer unsafeBuffer, final MessageHeaderDecoder messageHeaderDecoder,
      final int connectionId) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(DECODEADMINUSERUPDATE_EQ);
    }
    USER_DECODER.wrap(unsafeBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    final UserAdminMessage userAdminMessage = new UserAdminMessage(USER_DECODER, connectionId);
    userAdminMessage.setRequestStatus(USER_DECODER.requestStatus());
    return userAdminMessage;
  }

  public final BalanceAdminMessage decodeBalanceAdminUserUpdate(final UnsafeBuffer unsafeBuffer,
      final MessageHeaderDecoder messageHeaderDecoder, final int connectionId) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(DECODEBALANCEADMINUSERUPDATE_EQ);
    }
    BALANCE_DECODER.wrap(unsafeBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    return new BalanceAdminMessage(BALANCE_DECODER, connectionId);
  }

  public final FeeAdminMessage decodeFeeAdminUpdate(final UnsafeBuffer unsafeBuffer, final MessageHeaderDecoder messageHeaderDecoder,
      final int connectionId) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(DECODEFEEADMINUPDATE_EQ);
    }
    FEE_DECODER.wrap(unsafeBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    return new FeeAdminMessage(FEE_DECODER, connectionId);
  }

  public final SecurityDefinitionAdminMessage decodeSecurityDefinitionAdminUpdate(final UnsafeBuffer unsafeBuffer,
      final MessageHeaderDecoder messageHeaderDecoder, final int connectionId) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(DECODESECURITYDEFINITIONADMINUPDATE_EQ);
    }
    SECURITY_DEFINITION_DECODER.wrap(unsafeBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());
    return new SecurityDefinitionAdminMessage(SECURITY_DEFINITION_DECODER, connectionId);
  }

  public final TradeStateAdminMessage decodeTradeStateAdminUpdate(final UnsafeBuffer unsafeBuffer,
      final MessageHeaderDecoder messageHeaderDecoder, final int connectionId) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.info(DECODETRADESTATEADMINUPDATE_EQ);
    }
    TRADE_STATE_DECODER.wrap(unsafeBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    return new TradeStateAdminMessage(TRADE_STATE_DECODER, connectionId);
  }

  public final FundingRateCalcMessage decodeFundingRateCalcAdminUpdate(final UnsafeBuffer unsafeBuffer,
      final MessageHeaderDecoder messageHeaderDecoder, final int connectionId) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.info(DECODEFUNDINGRATECALCADMINUPDATE_EQ);
    }
    FUNDING_RATE_DECODER.wrap(unsafeBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    return new FundingRateCalcMessage(FUNDING_RATE_DECODER, connectionId);
  }

  public final GlobalStateAdminMessage decodeGlobalStateAdminUpdate(final UnsafeBuffer unsafeBuffer,
      final MessageHeaderDecoder messageHeaderDecoder, final int connectionId) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.info(DECODEGLOBALSTATEADMINUPDATE_EQ);
    }
    GLOBAL_STATE_ADMIN_DECODER.wrap(unsafeBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    return new GlobalStateAdminMessage(GLOBAL_STATE_ADMIN_DECODER, connectionId);
  }

  public final SnapResponseAdminMessage decodeSnapResponseAdminMessage(final UnsafeBuffer unsafeBuffer,
      final MessageHeaderDecoder messageHeaderDecoder, final int connectionId) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(DECODESNAPRESPONSEADMIN_EQ);
    }
    SNAP_RESPONSE_DECODER.wrap(unsafeBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    return new SnapResponseAdminMessage(SNAP_RESPONSE_DECODER, connectionId);
  }

  public final FIXUserAdminMessage decodeFIXUserAdminUpdate(final UnsafeBuffer unsafeBuffer,
      final MessageHeaderDecoder messageHeaderDecoder, final int connectionId) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(DECODEFIXUSERADMINUPDATE_EQ);
    }
    FIX_USER_DECODER.wrap(unsafeBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    return new FIXUserAdminMessage(FIX_USER_DECODER, connectionId);
  }

}
