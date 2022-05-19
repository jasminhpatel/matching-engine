package com.solfini.matchengine.kafka;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.matchengine.decoder.CancelReplaceRequestHandler;
import com.solfini.matchengine.decoder.CancelRequestHandler;
import com.solfini.matchengine.decoder.MarketDataFeedRequestHandler;
import com.solfini.matchengine.decoder.MassCancelRequestHandler;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.decoder.SessionHandler;
import com.solfini.sbe.encoder.CancelOrderDecoder;
import com.solfini.sbe.encoder.CancelReplaceOrderDecoder;
import com.solfini.sbe.encoder.HeartbeatDecoder;
import com.solfini.sbe.encoder.LogonDecoder;
import com.solfini.sbe.encoder.LogoutDecoder;
import com.solfini.sbe.encoder.MarketDataFeedDecoder;
import com.solfini.sbe.encoder.MassCancelOrderDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.NetworkStatusDecoder;
import com.solfini.sbe.encoder.NewOrderSingleDecoder;
import com.solfini.sbe.encoder.ResendRequestDecoder;
import com.solfini.sbe.encoder.SequenceResetDecoder;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class KafkaInputFixListener extends KafkaListener {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaInputFixListener.class);

  protected static final String API_KAFKA_TOPIC_IN = PropertyReader.getProperty("API_KAFKA_TOPIC_IN", "api1");
  protected static final String ME_KAFKA_TOPIC_PRIMARY = PropertyReader.getProperty("ME_KAFKA_TOPIC_PRIMARY", "me1");

  private final NewOrderSingleHandler newOrderSingleHandler = new NewOrderSingleHandler();
  private final CancelRequestHandler cancelRequestHandler = new CancelRequestHandler();
  private final CancelReplaceRequestHandler cancelReplaceRequestHandler = new CancelReplaceRequestHandler();
  private final MassCancelRequestHandler massCancelRequestHandler = new MassCancelRequestHandler();
  private final MarketDataFeedRequestHandler marketDataFeedRequestHandler = new MarketDataFeedRequestHandler();
  private final SessionHandler sessionHandler = new SessionHandler();

  private final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final NewOrderSingleDecoder newOrderSingleDecoder = new NewOrderSingleDecoder();
  private final CancelOrderDecoder cancelOrderDecoder = new CancelOrderDecoder();
  private final CancelReplaceOrderDecoder cancelReplaceOrderDecoder = new CancelReplaceOrderDecoder();
  private final MassCancelOrderDecoder massCancelOrderDecoder = new MassCancelOrderDecoder();
  private final MarketDataFeedDecoder marketDataFeedDecoder = new MarketDataFeedDecoder();

  private final LogonDecoder logonDecoder = new LogonDecoder();
  private final HeartbeatDecoder heartbeatDecoder = new HeartbeatDecoder();
  private final ResendRequestDecoder resendRequestDecoder = new ResendRequestDecoder();
  private final SequenceResetDecoder sequenceResetDecoder = new SequenceResetDecoder();

  private final ManyToOneConcurrentArrayQueueCustom<Message> receiverToMatcherQueue = Context.getReceiverToMatcherQueue();
  private final KafkaAdminInputFixListener kafkaAdminInputFixListener = new KafkaAdminInputFixListener(false);

  protected long lastSequenceNumber = 0;
  protected long lastIpcIndex = 0;
  protected static final long RESPONSE_CHANNEL_ID = 0;

  // process bytes with 17 offset
  private static final int KAFKA_OFFSET = 17;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;

  private final boolean replay; // replay from input queue starting from where we left off?

  public KafkaInputFixListener(boolean replay) {
    super(API_KAFKA_TOPIC_IN);
    this.replay = replay;

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, KAFKAINPUTFIXLISTENER_TOPIC_EQ, API_KAFKA_TOPIC_IN, LOADED_LASTSEQUENCENUMBER_EQ, lastSequenceNumber,
          LASTIPCINDEX_EQ, lastIpcIndex);
    }
  }

  @Override
  public void start() {
    // Seek to the correct position of the input topic
    final TopicPartition partition = new TopicPartition(getTopic(), 0);
    final List<TopicPartition> partitions = Arrays.asList(partition);
    getConsumer().assign(partitions);

    if (replay) {
      final long offset = getLastInputOffset();
      getConsumer().seek(partition, offset);
      if (LOGGER.isInfoEnabled()) {
        LOGGER.debug(LOG_FMT_2, "Moving input queue cursor to last processed offset: ", offset);
      }
    } else {
      getConsumer().seekToEnd(partitions);
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "Moving input queue cursor to the end of the queue");
      }
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info("");
      }
    }
  }

  protected long getLastInputOffset() {
    // Read the last message on the matching engine output queue
    final KafkaListener reader = new KafkaListener(ME_KAFKA_TOPIC_PRIMARY);
    final KafkaConsumer<String, byte[]> consumer = reader.getConsumer();
    final TopicPartition partition = new TopicPartition(reader.getTopic(), 0);
    final List<TopicPartition> partitions = Arrays.asList(partition);

    consumer.assign(partitions);
    consumer.seekToEnd(partitions);
    long current = consumer.position(partition);
    if (current == 0) {
      return 0;
    }

    final Duration timeout = Duration.ofMillis(200);

    while (current > 0) {
      --current;
      consumer.seek(partition, current);

      final ConsumerRecords<String, byte[]> records = consumer.poll(timeout);
      if (records.isEmpty()) {
        continue;
      }

      for (final ConsumerRecord<String, byte[]> record : records) {
        final byte[] data = record.value();

        // wrap bytes
        decoderUnsafeBuffer.wrap(data);
        headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);
        long offset = headerDecoder.kafkaRecordOffset();

        if (offset > 0) {
          return offset;
        } else {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_2, "Output message with no input offset: ", data);
          }
        }
      }
    }

    return 0;
  }

  @Override
  public void onMessage(final long seqNum, final long sendTime, final long recordOffset, final byte messageType, final byte[] data) {
    final int length = data.length - KAFKA_OFFSET;
    final long inputTime = TimeUtil.getTime();
    final long latency = (inputTime - sendTime) / 1000; // in microseconds
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(LOG_FMT_14, RECEIVED_SEQNUM_EQ, seqNum, SENDTIME_EQ, sendTime, RECORDOFFSET_EQ, recordOffset, MESSAGETYPE_EQ,
          messageType, LATENCY_EQ, latency, INPUTTIME_EQ, inputTime, DATA_EQ, StringUtil.fixToString(data));
    }

    // route admin messages
    if (KafkaPublisher.ADMIN_API == messageType) {
      kafkaAdminInputFixListener.onMessage(seqNum, sendTime, recordOffset, messageType, data);
      return;
    }

    try {
      Message message = null;
      try {
        message = decode(data, length);
      } catch (Exception e) {
        // decode error!!!
        LOGGER.error(
            "KafkaInputFixListener decode error, msgType=" + messageType + LENGTH_EQ + length + SB_EQ + StringUtil.fixToString(data), e);
        return;
      }

      // Decode transaction details
      try {
        if (message != null) {
          message.setTransactionId(headerDecoder.transactionId());
          message.setLastMessageInTransaction(headerDecoder.transactionEnd() == 1);
        }
      } catch (Exception e) {
        LOGGER.error(LOG_FMT_2, "Error decoding transaction. TargetLocationId:", headerDecoder.transactionId(), ", Message:", e);
        return;
      }

      // don't check seqNum

      if (message != null) {
        message.setSourceSeqNum(seqNum);
        message.setSourceSendTime(sendTime);
        message.setKafkaRecordOffset(recordOffset);
        message.setInputTime(inputTime);
        message.setDecodedTime(TimeUtil.getTime());

        // regular mode
        // log incoming message to be processed
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_14, HANDLEMESSAGE_MSGSEQNUM_EQ, seqNum, LATENCY_EQ, latency, INPUTTIME_EQ, inputTime, MSGTYPE_EQ,
              String.valueOf(messageType), LENGTH_EQ, length, MESSAGE_EQ, data, DECODED_MESSAGE_EQ, message);
        }

        receiverToMatcherQueue.addGuaranteed(message);
      }

    } catch (Exception e) {
      LOGGER.error("KafkaInputFixListener error ", e);
      if (e.getMessage().contains("Queue")) {
        LOGGER.error("KafkaInputFixListener Queue error sleeping");
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e1) {
          LOGGER.error(ERROR_LOG, e1);
        }
      }
    }
  }

  private final Message decode(final byte[] data, final int length) {
    // wrap bytes
    decoderUnsafeBuffer.wrap(data);
    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);

    try {
      switch (headerDecoder.templateId()) {
        case NewOrderSingleDecoder.TEMPLATE_ID:
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(NEW_ORDER_SINGLE_RECEIVED);
          }
          newOrderSingleDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          Message message = newOrderSingleHandler.decodeNewOrderSingle(headerDecoder, newOrderSingleDecoder);
          return message;
        case CancelOrderDecoder.TEMPLATE_ID:
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(ORDER_CANCEL_REQUEST);
          }
          cancelOrderDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          long cancelId = NewOrderSingleHandler.getNextOrderId();
          message = cancelRequestHandler.decodeCancelRequest(headerDecoder, cancelOrderDecoder, cancelId);
          return message;
        case CancelReplaceOrderDecoder.TEMPLATE_ID:
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(ORDER_CANCEL_REPLACE);
          }
          cancelReplaceOrderDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          cancelId = NewOrderSingleHandler.getNextOrderId();
          long newOrderId = NewOrderSingleHandler.getNextOrderId();
          message = cancelReplaceRequestHandler.decodeCancelReplaceRequest(headerDecoder, cancelReplaceOrderDecoder, cancelId, newOrderId);
          return message;
        case MassCancelOrderDecoder.TEMPLATE_ID:
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(ORDER_MASS_CANCEL_REPLACE);
          }
          massCancelOrderDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          cancelId = NewOrderSingleHandler.getNextOrderId();
          message = massCancelRequestHandler.decodeCancelRequest(headerDecoder, massCancelOrderDecoder, cancelId);
          return message;
        case MarketDataFeedDecoder.TEMPLATE_ID:
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(MARKET_DATA_FEED);
          }
          marketDataFeedDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          message = marketDataFeedRequestHandler.decodeMarketDataFeed(headerDecoder, marketDataFeedDecoder);
          return message;
        case LogonDecoder.TEMPLATE_ID:
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(LOG_FMT_2, LOGON_MESSAGE_RECEIVED);
          }
          logonDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          message = sessionHandler.decodeLogon(headerDecoder, logonDecoder);
          return message;
        case LogoutDecoder.TEMPLATE_ID:
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(LOGOUT_MESSAGE_RECEIVED);
          }
          return null;
        case HeartbeatDecoder.TEMPLATE_ID:
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(HEARTBEAT_MESSAGE_RECEIVED);
          }
          heartbeatDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          message = sessionHandler.decodeHeartbeat(headerDecoder, heartbeatDecoder);
          return message;
        case ResendRequestDecoder.TEMPLATE_ID:
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(RESEND_REQUEST_MESSAGE_RECEIVED);
          }
          resendRequestDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          message = sessionHandler.decodeResendRequest(headerDecoder, resendRequestDecoder);
          return message;
        case SequenceResetDecoder.TEMPLATE_ID:
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(SEQUENCE_RESET_MESSAGE_RECEIVED);
          }
          sequenceResetDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          message = sessionHandler.decodeSequenceReset(headerDecoder, sequenceResetDecoder);
          return message;
        case NetworkStatusDecoder.TEMPLATE_ID:
          // new snap available
          return null;
        default:
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(LOG_FMT_4, UNABLE_TO_DECODE_MSGTYPE, headerDecoder.templateId(), DATA_EQ, data);
          }
          return null;

      }

    } catch (Exception e) {
      LOGGER.error("Error in decode", e);
      throw e;
    }
  }



}
