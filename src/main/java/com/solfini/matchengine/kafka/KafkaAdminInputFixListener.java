package com.solfini.matchengine.kafka;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.AdminMessage;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.common.TransactionalInputManyToOneConcurrentArrayQueue;
import com.solfini.internal.admin.schema.AdminAcknowledgementDecoder;
import com.solfini.internal.admin.schema.BalanceAdminMessageDecoder;
import com.solfini.internal.admin.schema.FIXUserAdminMessageDecoder;
import com.solfini.internal.admin.schema.FeeAdminMessageDecoder;
import com.solfini.internal.admin.schema.FundingRateCalcAdminMessageDecoder;
import com.solfini.internal.admin.schema.GlobalStateAdminMessageDecoder;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.MessageHeaderDecoder;
import com.solfini.internal.admin.schema.SecurityDefinitionAdminMessageDecoder;
import com.solfini.internal.admin.schema.SnapResponseAdminMessageDecoder;
import com.solfini.internal.admin.schema.TradeStateAdminMessageDecoder;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.admin.schema.UserAdminMessageDecoder;
import com.solfini.matchengine.MessageValidator;
import com.solfini.matchengine.decoder.InboundAdminMessageHandler;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.SnapResponseAdminMessage;
import com.solfini.matchengine.message.admin.TradeStateAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.util.LogLevel;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

/**
 *
 * @author Chris Mack
 *
 */
public class KafkaAdminInputFixListener extends KafkaListener implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaAdminInputFixListener.class);

  public static final String API_ADMIN_KAFKA_TOPIC = PropertyReader.getProperty("API_ADMIN_KAFKA_TOPIC", "apiadmin1");

  private final InboundAdminMessageHandler inboundAdminMessageHandler = new InboundAdminMessageHandler();

  private final com.solfini.internal.schema.MessageHeaderDecoder messageHeaderDecoder =
      new com.solfini.internal.schema.MessageHeaderDecoder();

  private static final MessageHeaderDecoder ADMIN_MESSAGE_HEADER_DECODER = new MessageHeaderDecoder();
  private final ByteBuffer buffer = ByteBuffer.allocateDirect(32768);

  final ByteBuffer messageBuffer = ByteBuffer.allocateDirect(32768);
  final MutableAsciiBuffer mab = new MutableAsciiBuffer(messageBuffer);

  private final TransactionalInputManyToOneConcurrentArrayQueue receiverToMatcherQueue = Context.getReceiverToMatcherQueue();
  private final ManyToOneConcurrentArrayQueueCustom<Message> persisterQueue = Context.getPublisherToPersisterQueue();

  private boolean snapLoaderMode = false;

  private long lastSequenceNumber = 0;
  private long lastIpcIndex = 0;

  // process bytes with 17 offset+2
  private static final int OFFSET = 19;

  public static final KafkaAdminInputFixListener build() {
    try {
      return new KafkaAdminInputFixListener(false);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG);
    }
    return null;
  }

  public KafkaAdminInputFixListener(final boolean snapLoaderMode) {
    super(API_ADMIN_KAFKA_TOPIC);
    this.snapLoaderMode = snapLoaderMode;

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, ">>> KafkaAdminInputFixListener topic=", API_ADMIN_KAFKA_TOPIC, LOADED_LASTSEQUENCENUMBER_EQ,
          lastSequenceNumber, LASTIPCINDEX_EQ, lastIpcIndex);
    }
  }

  @Override
  public final void onMessage(final long seqNum, final long sendTime, final long recordOffset, final byte messageType, final byte[] data) {
    final long latency = (TimeUtil.getTime() - sendTime) / 1000; // in microseconds
    final int length = data.length - OFFSET;

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(LOG_FMT_14, RECEIVED_SEQNUM_EQ, seqNum, SENDTIME_EQ, sendTime, RECORDOFFSET_EQ, recordOffset, MESSAGETYPE_EQ,
          messageType, LENGTH_EQ, length, LATENCY_EQ, latency, DATA_EQ, StringUtil.fixToString(data));
    }

    if (KafkaPublisher.ADMIN_API != messageType) {
      if (LogLevel.warn()) {
        LOGGER.warn(LOG_FMT_14, "Skipping non admin message: seqNum=", seqNum, SENDTIME_EQ, sendTime, RECORDOFFSET_EQ, recordOffset,
            MESSAGETYPE_EQ, messageType, LENGTH_EQ, length, LATENCY_EQ, latency, DATA_EQ, StringUtil.fixToString(data));
      }
      return;
    }
    try {
      buffer.clear();
      buffer.put(data, OFFSET, length);
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_18, RECEIVED_BUFFER_EQ, StringUtil.fixToString(buffer, length), RECEIVED_SEQNUM_EQ, seqNum, SENDTIME_EQ,
            sendTime, RECORDOFFSET_EQ, recordOffset, MESSAGETYPE_EQ, messageType, LENGTH_EQ, length, LATENCY_EQ, latency, DATA_EQ, data);
      }
      handleMessage(buffer, length, seqNum, sendTime, recordOffset, messageType);

    } catch (Exception e) {
      LOGGER.error("KafkaAdminInputFixListener error ", e);
      if (e.getMessage().contains("Queue")) {
        LOGGER.error("KafkaAdminInputFixListener Queue error sleeping");
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e1) {
          LOGGER.error(ERROR_LOG, e1);
        }
      }
    }
  }

  public final Message onMessageSnapLoader(final long seqNum, final long sendTime, final long recordOffset, final byte messageType,
      final byte[] data) {
    final long latency = (TimeUtil.getTime() - sendTime) / 1000; // in microseconds
    final int length = data.length - OFFSET;

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_14, RECEIVED_SEQNUM_EQ, seqNum, SENDTIME_EQ, sendTime, RECORDOFFSET_EQ, recordOffset, MESSAGETYPE_EQ,
          messageType, LENGTH_EQ, length, LATENCY_EQ, latency, DATA_EQ, StringUtil.fixToString(data));
    }

    if (KafkaPublisher.ADMIN_API != messageType) {
      if (LogLevel.warn()) {
        LOGGER.warn(LOG_FMT_14, "Skipping non admin message: seqNum=", seqNum, SENDTIME_EQ, sendTime, RECORDOFFSET_EQ, recordOffset,
            MESSAGETYPE_EQ, messageType, LENGTH_EQ, length, LATENCY_EQ, latency, DATA_EQ, StringUtil.fixToString(data));
      }
      return null;
    }
    try {
      buffer.clear();
      buffer.put(data, OFFSET, length);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_2, RECEIVED_BUFFER_EQ, StringUtil.fixToString(buffer, length));
      }
      return handleMessage(buffer, length, seqNum, sendTime, recordOffset, messageType);

    } catch (Exception e) {
      LOGGER.error("KafkaAdminInputFixListener error ", e);
      if (e.getMessage().contains("Queue")) {
        LOGGER.error("KafkaAdminInputFixListener Queue error sleeping");
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e1) {
          LOGGER.error(ERROR_LOG, e1);
        }
      }
    }
    return null;
  }

  private final Message handleAdminMessage(final ByteBuffer buffer, final int length, final long seqNum, final long sendTime,
      final long recordOffset, final byte messageType) {
    AdminMessage message = null;

    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);
    ADMIN_MESSAGE_HEADER_DECODER.wrap(unsafeBuffer, 0);

    final int templateId = ADMIN_MESSAGE_HEADER_DECODER.templateId();
    final int connectionId = 0;
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(LOG_FMT_2, HANDLEADMINMESSAGE_TEMPLATEID_EQ, templateId);
    }

    switch (templateId) {
      case AdminAcknowledgementDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_1, ACK_ADMIN_MESSAGE_RECEIVED);
        }
        break;
      case UserAdminMessageDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_1, USER_ADMIN_MESSAGE_RECEIVED);
        }
        message = inboundAdminMessageHandler.decodeAdminUserUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case BalanceAdminMessageDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_1, BALANCE_ADMIN_MESSAGE_RECEIVED);
        }
        message = inboundAdminMessageHandler.decodeBalanceAdminUserUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case FeeAdminMessageDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_1, FEE_ADMIN_MESSAGE_RECEIVED);
        }
        message = inboundAdminMessageHandler.decodeFeeAdminUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        if (message instanceof FeeAdminMessage)
          ((FeeAdminMessage) message).setUpdateType(UpdateType.PUT);
        break;
      case SecurityDefinitionAdminMessageDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_1, SECURITY_DEFINITION_ADMIN_MESSAGE_RECEIVED);
        }
        final SecurityDefinitionAdminMessage securityDefinitionAdminMessage =
            inboundAdminMessageHandler.decodeSecurityDefinitionAdminUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        // if (securityDefinitionAdminMessage != null)
        // securityDefinitionAdminMessage.setUpdateType(UpdateType.PUT);
        message = securityDefinitionAdminMessage;
        break;
      case TradeStateAdminMessageDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_1, TRADE_STATE_MESSAGE_RECEIVED);
        }
        message = inboundAdminMessageHandler.decodeTradeStateAdminUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case FIXUserAdminMessageDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_1, FIX_USER_ADMIN_MESSAGE_RECEIVED);
        }
        message = inboundAdminMessageHandler.decodeFIXUserAdminUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case FundingRateCalcAdminMessageDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_1, FUNDINGRATECALC_MESSAGE_RECEIVED);
        }
        message = inboundAdminMessageHandler.decodeFundingRateCalcAdminUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case GlobalStateAdminMessageDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_1, GLOBALSTATEADMIN_MESSAGE_RECEIVED);
        }
        message = inboundAdminMessageHandler.decodeGlobalStateAdminUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        break;
      case SnapResponseAdminMessageDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(SNAPRESPONSEADMINMESSAGE_MESSAGE_RECEIVED);
        }
        SnapResponseAdminMessage snapResponseAdminMessage =
            inboundAdminMessageHandler.decodeSnapResponseAdminMessage(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        if (snapResponseAdminMessage != null) {
          // special case, set the publisher sequence number from the networkStatusMessage
          Context.getMessagePublisher().setSequenceNumber(snapResponseAdminMessage.getSequenceNumber());
          NewOrderSingleHandler.setOrderIdIfGreater(snapResponseAdminMessage.getOrderId());

          snapResponseAdminMessage.setSourceSeqNum(seqNum);
          snapResponseAdminMessage.setSourceSendTime(sendTime);
          snapResponseAdminMessage.setKafkaRecordOffset(recordOffset);
          snapResponseAdminMessage.setInputKafkaRecordOffset(recordOffset);

          Context.getReceiverToMatcherQueue().flush();

          return snapResponseAdminMessage; // special case for ending SnapResponseAdminMessage Message
        }
        break;
      default:
        LOGGER.error(LOG_FMT_4, "Kafka Unsupported templateId=", templateId, MESSAGE_EQ, message);
        throw new UnsupportedOperationException("templateId=" + templateId + MESSAGE_EQ + message);

    }

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(LOG_FMT_4, KAFKA_ADMIN_DECODED_ADMIN_MESSAGE_EQ, message, SNAPLOADERMODE_EQ, snapLoaderMode);
    }

    if (message != null) {
      if (Context.isPersistModeEnabled())
        persisterQueue.add(message);

      // Validate message
      final MessageValidator validator = Context.getMessageValidator();

      if (validator != null) {

        // Set the new instance id if the message is a trade state admin message with
        // failover flag set.
        if ((message.getMessageType() == MessageType.TRADE_STATE_ADMIN)
            && (((TradeStateAdminMessage) message).getMarketStatus() == MarketStatus.FAILOVER)) {

          final String newInstance = message.getSenderInstanceId();
          if (Context.getInstanceId().equals(newInstance)) {
            LOGGER.info(FAILOVER_ALL_MESSAGES_READ);
            return null;
          }

          LOGGER.info(LOG_FMT_5, FAILOVER_MESSAGE_RECEIVED, CURRENT_EQ, validator.getCurrentSender(), NEW_EQ, newInstance);

          validator.setCurrentSender(newInstance);
          return null;
        }

        // Check if the message is sent from the current master node. If not ignore the
        // message.
        if (!validator.validate(message.getSenderInstanceId())) {
          LOGGER.error(LOG_FMT_3, INVALID_SENDER_INSTANCE_ID, MESSAGE_EQ, message);
          return null;
        }
      }

      final long transactionId = ADMIN_MESSAGE_HEADER_DECODER.transactionId();
      final boolean transactionEnd = (ADMIN_MESSAGE_HEADER_DECODER.transactionEnd() == 1);

      message.setTransactionId(transactionId);
      message.setLastMessageInTransaction(transactionEnd);

      message.setSourceSeqNum(seqNum);
      message.setSourceSendTime(sendTime);
      message.setKafkaRecordOffset(recordOffset);

      if (message.getTriggerTimeMillis() > 0) {
        Context.getTimeTriggerThread().registerMessage(message);
      } else {
        if (snapLoaderMode && (message instanceof SecurityDefinitionAdminMessage || message instanceof FeeAdminMessage
            || message instanceof UserAdminMessage)) {
          // handle in current thread to avoid race conditions
          message.onMatcher();
        } else {
          if (snapLoaderMode && (message instanceof TradeStateAdminMessage)) {
            // ignore snapshot requests on replay
            TradeStateAdminMessage tradeStateAdminMessage = (TradeStateAdminMessage) message;
            if (tradeStateAdminMessage.getMarketStatus() == MarketStatus.RESTATE) {
              tradeStateAdminMessage.setRouteToDestination("<ignore>");
            }
          }
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_4, ">>>> Kafka admin publishing admin message=", message, SNAPLOADERMODE_EQ, snapLoaderMode);
          }
          receiverToMatcherQueue.addGuaranteed(message);
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(LOG_FMT_4, "<<<< Kafka admin publishing admin message=", message, SNAPLOADERMODE_EQ, snapLoaderMode);
          }
        }
      }
    } else {

      // Flush the transaction input queue as the un decoded message is the last
      // message.
      if (ADMIN_MESSAGE_HEADER_DECODER.transactionEnd() == 1) {
        receiverToMatcherQueue.flush();
      }

    }
    return null;
  }

  private Message handleMessage(final ByteBuffer buffer, final int length, final long seqNum, final long sendTime, final long recordOffset,
      final byte messageType) {
    int offset = 0;
    try {
      mab.wrap(buffer, 0, length);
      messageHeaderDecoder.wrap(mab, offset);

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(LOG_FMT_2, MESSAGEHEADERDECODER_EQ, messageHeaderDecoder.toString());
      }

      return handleAdminMessage(buffer, length, seqNum, sendTime, recordOffset, messageType);

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }
}
