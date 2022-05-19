package com.solfini.matchengine;

import com.solfini.common.Constants;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.internal.admin.schema.*;
import com.solfini.matchengine.decoder.InboundAdminMessageHandler;
import com.solfini.user.User;
import com.solfini.util.LogLevel;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;
import java.nio.ByteBuffer;

public class AdminMessageDecoderTest implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(AdminMessageDecoderTest.class);
  private static final InboundAdminMessageHandler inboundAdminMessageHandler = new InboundAdminMessageHandler();

  private static final com.solfini.internal.schema.MessageHeaderDecoder messageHeaderDecoder =
      new com.solfini.internal.schema.MessageHeaderDecoder();

  private static final MessageHeaderDecoder ADMIN_MESSAGE_HEADER_DECODER = new MessageHeaderDecoder();
  private static final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(8192);
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
  private static final int OFFSET = 18;
  private static ByteBuffer messageBuffer = ByteBuffer.allocateDirect(16384);
  private static MutableAsciiBuffer mab = new MutableAsciiBuffer(messageBuffer);

  private static void onMessage(final long seqNum, final long sendTime, final byte[] data) {
    long latency = (TimeUtil.getTime() - sendTime) / 1000; // in microseconds
    String fixString = StringUtil.fixToString(data);
    int length = data.length - OFFSET;

    if (LogLevel.debug())
      LOGGER.debug(
          "received: seqNum=" + seqNum + ",length=" + length + ", sendTime=" + sendTime + ", latency=" + latency + ", data=" + fixString);
    try {
      mab.wrap(data, OFFSET, length);

      buffer.clear();
      buffer.put(data, OFFSET, length);
      if (LogLevel.debug())
        LOGGER.debug("received buffer=" + StringUtil.fixToString(buffer, length));
      handleMessage(buffer, length);

    } catch (Exception e) {
      LOGGER.error("KafkaAdminInputFixListener error ", e);
      if (e.getMessage().contains("Queue")) {
        LOGGER.error("KafkaAdminInputFixListener Queue error sleeping");
        try {
          Thread.sleep(5000);
        } catch (Exception e1) {
          LOGGER.error(ERROR_LOG, e1);
        }
      }
    }
  }

  private static void handleAdminMessage(final ByteBuffer buffer, final int length) {
    Message message = null;

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      sb.append((int) buffer.get(i)).append(",");
    }
    if (LogLevel.debug())
      LOGGER.debug(
          ">>> IPC Admin handleAdminMessage adminBodyLength=" + length + ", sb=" + sb.toString() + ", adminBytes=" + buffer.toString());


    UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);
    ADMIN_MESSAGE_HEADER_DECODER.wrap(unsafeBuffer, 0); // ????

    int templateId = ADMIN_MESSAGE_HEADER_DECODER.templateId();
    int connectionId = 0;
    if (LogLevel.debug())
      LOGGER.debug(">>> handleAdminMessage, templateId=" + templateId);

    switch (templateId) {
      case AdminAcknowledgementDecoder.TEMPLATE_ID:
        if (LogLevel.debug())
          LOGGER.debug("Ack Admin Message Received");
        break;
      case UserAdminMessageDecoder.TEMPLATE_ID:
        if (LogLevel.debug())
          LOGGER.debug("User Admin Message Received");
        message = inboundAdminMessageHandler.decodeAdminUserUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case BalanceAdminMessageDecoder.TEMPLATE_ID:
        if (LogLevel.debug())
          LOGGER.debug("Balance Admin Message Received");
        message = inboundAdminMessageHandler.decodeBalanceAdminUserUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case FeeAdminMessageDecoder.TEMPLATE_ID:
        if (LogLevel.debug())
          LOGGER.debug("Fee Admin Message Received");
        message = inboundAdminMessageHandler.decodeFeeAdminUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case SecurityDefinitionAdminMessageDecoder.TEMPLATE_ID:
        if (LogLevel.debug())
          LOGGER.debug("Security Definition Admin Message Received");
        message = inboundAdminMessageHandler.decodeSecurityDefinitionAdminUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case TradeStateAdminMessageDecoder.TEMPLATE_ID:
        if (LogLevel.debug())
          LOGGER.debug("Trade State Message Received");
        message = inboundAdminMessageHandler.decodeTradeStateAdminUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case FIXUserAdminMessageDecoder.TEMPLATE_ID:
        if (LogLevel.debug())
          LOGGER.debug("FIX User Admin Message Received");
        message = inboundAdminMessageHandler.decodeFIXUserAdminUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case FundingRateCalcAdminMessageDecoder.TEMPLATE_ID:
        if (LogLevel.debug())
          LOGGER.debug("FundingRateCalc Message Received");
        message = inboundAdminMessageHandler.decodeFundingRateCalcAdminUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      default:
        LOGGER.error("Ipc Unsupported templateId=" + templateId + ", message=" + message);
        throw new UnsupportedOperationException("templateId=" + templateId + ", message=" + message);

    }
    if (LogLevel.debug())
      LOGGER.debug("Ipc admin decoded admin message=" + message);

    if (message != null) {
      final long transactionId = ADMIN_MESSAGE_HEADER_DECODER.transactionId();
      final boolean transactionEnd = (ADMIN_MESSAGE_HEADER_DECODER.transactionEnd() == 1) ? true : false;

      message.setTransactionId(transactionId);
      message.setLastMessageInTransaction(transactionEnd);
    }

    if (message != null) {
      Assert.assertEquals(MessageType.BALANCE_ADMIN, message.getMessageType());
      Assert.assertEquals(18, message.getUser().getId());
      // System.exit(0);
    }
  }

  private static void handleMessage(final ByteBuffer buffer, final int length) {
    int offset = 0;
    try {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < length; i++) {
        sb.append((int) buffer.get(i)).append(",");
      }
      if (LogLevel.debug())
        LOGGER.debug(">>> handleAdminMessage length=" + length + ", sb=" + sb.toString() + ", buffer=" + buffer);

      ByteBuffer messageBuffer = ByteBuffer.allocateDirect(8192);
      MutableAsciiBuffer mab = new MutableAsciiBuffer(messageBuffer);

      mab.wrap(buffer, 0, length);
      messageHeaderDecoder.wrap(mab, offset);



      if (LogLevel.debug())
        LOGGER.debug("messageHeaderDecoder=" + messageHeaderDecoder.toString());
      handleAdminMessage(buffer, length);


    } catch (Exception e) {
      LOGGER.error("onFragment error", e);
    }
  }

  public static boolean sendUserBalance() {
    final User user = new User(18);
    final int securityId = 3;
    final long quantity = 10000;
    final int quantity_scale = 2;
    final int txType = 1;
    final int txId = 333;
    return sendUserBalance(user, securityId, quantity, quantity_scale, txType, txId);
  }

  private static final short ENCODED_LENGTH_SIZE = 2;

  private static boolean sendUserBalance(final User user, final int securityId, final long quantity, final int quantity_scale,
      final int txType, final int txId) {
    try {
      short encodedLength = ENCODED_LENGTH_SIZE;
      ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
      UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);


      BalanceAdminMessageEncoder balanceAdminMessageEncoder = new BalanceAdminMessageEncoder();
      com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
          new com.solfini.internal.admin.schema.MessageHeaderEncoder();
      balanceAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
      encodedLength += headerEncoder.encodedLength();

      balanceAdminMessageEncoder.updateType(UpdateType.PATCH);
      balanceAdminMessageEncoder.userId(user.getId());
      balanceAdminMessageEncoder.feeTier(user.getFeeTier());
      balanceAdminMessageEncoder.txType(txType);
      balanceAdminMessageEncoder.txId(txId);

      com.solfini.internal.admin.schema.BalanceAdminMessageEncoder.BalanceGroupEncoder balanceGroupEncoder =
          balanceAdminMessageEncoder.balanceGroupCount(1);
      balanceGroupEncoder.next().assetId(securityId).balance().value(quantity).scale(quantity_scale);

      encodedLength += balanceAdminMessageEncoder.encodedLength();
      adminMessageBuffer.limit(encodedLength);
      adminMessageUnsafeBuffer.putShort(0, encodedLength);

      if (LogLevel.debug())
        LOGGER.debug(">>> sendUserBalance securityId=" + securityId + ", txType=" + txType + ", txId=" + txId + ", quantity=" + quantity
            + ", quantity_scale=" + quantity_scale + ", user=" + user);

      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < encodedLength; i++) {
        sb.append((int) adminMessageBuffer.get(i)).append(",");
      }


      byte[] bytes = StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, 16);
      if (LogLevel.debug())
        LOGGER.debug(">>> sendUserBalance encodedLength=" + encodedLength + ", sb=" + sb.toString() + ", adminMessageBuffer="
            + adminMessageBuffer + ", bytes=" + StringUtil.fixToString(bytes) + ", len=" + bytes.length);


    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return true;
  }

  @Test
  public void decodeBalanceAdminMessage() {

    byte[] data = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 52, 0, 22, 0, 2, 0, 1, 0, 0, 0, 2, 18, 0, 0, 0, 0, 0, 0, 0, 1,
        0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 16, 0, 1, 0, 3, 0, 0, 0, 4, -121, 1, 0, 0, 0, 0, 0, 6, 0, 0, 0};
    if (LogLevel.debug())
      LOGGER.debug("data=" + data.length);
    final long seqNum = 123;
    final long sendTime = 123;
    onMessage(seqNum, sendTime, data);
  }
}
