package com.solfini.matchengine.kafka;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import com.solfini.common.*;
import com.solfini.matchengine.decoder.*;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.session.HeartbeatMessage;
import com.solfini.sbe.encoder.*;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
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
  private final AssetGroupRequestHandler assetGroupRequestHandler = new AssetGroupRequestHandler();
  private final OrderFilterHandler orderFilterHandler = new OrderFilterHandler();
  private final LiquidityHandler liquidityHandler = new LiquidityHandler();

  private final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final NewOrderSingleDecoder newOrderSingleDecoder = new NewOrderSingleDecoder();
  private final CancelOrderDecoder cancelOrderDecoder = new CancelOrderDecoder();
  private final CancelReplaceOrderDecoder cancelReplaceOrderDecoder = new CancelReplaceOrderDecoder();
  private final MassCancelOrderDecoder massCancelOrderDecoder = new MassCancelOrderDecoder();
  private final MarketDataFeedDecoder marketDataFeedDecoder = new MarketDataFeedDecoder();
  private final AssetGroupDecoder assetGroupDecoder = new AssetGroupDecoder();
  private final OrderFilterDecoder orderFilterDecoder = new OrderFilterDecoder();
  private final LiquidityResponseDecoder liquidityResponseDecoder = new LiquidityResponseDecoder();

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
  private final boolean replaySelected; // replay only selected messages (User, Deposit) from input queue starting from where we left off?
  private long startPointOffset = 0;

  public KafkaInputFixListener(boolean replay) {
    super(API_KAFKA_TOPIC_IN);
    this.replay = replay;
    this.replaySelected = false;
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, KAFKAINPUTFIXLISTENER_TOPIC_EQ, API_KAFKA_TOPIC_IN, LOADED_LASTSEQUENCENUMBER_EQ, lastSequenceNumber,
          LASTIPCINDEX_EQ, lastIpcIndex);
    }
  }

  public KafkaInputFixListener(boolean replay, boolean replaySelected) {
    super(API_KAFKA_TOPIC_IN);
    this.replay = replay;
    this.replaySelected = replaySelected;

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
      final long lastAppliedOffset = getLastInputOffset();
      final long resumeOffset = resumeInputOffset(lastAppliedOffset);
      getConsumer().seek(partition, resumeOffset);
      LOGGER.info(LOG_FMT_4, "Last processed input offset: ", lastAppliedOffset, ", resuming input queue at: ", resumeOffset);

      if (replaySelected) {
        startPointOffset = getStartPointOffsetOffset();
        LOGGER.info(LOG_FMT_4, "Start point offset of the input queue: ", startPointOffset, " pendingMessages: ",
            (startPointOffset - resumeOffset));
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

  /**
   * Where to resume reading the input queue, given the input offset of the last message this engine is
   * known to have applied -- see {@link #getLastInputOffset()} for how that is established.
   * <p>
   * That message has already been applied and its effect is already in the state restored from the
   * snapshot and the output replay, so reading it again applies it twice. Balance changes arrive as
   * increments and the engine deduplicates nothing, so for a deposit or a withdrawal "twice" means
   * twice the money. Resume from the message after it.
   * <p>
   * Zero means "nothing known", not "offset 0 was applied": {@link #getLastInputOffset()} returns 0
   * for an empty output topic and for an output topic where nothing carries an input offset. It is
   * passed through unchanged so the seek lands on 0, which is what this did before. Be aware what that
   * actually does at runtime -- if retention has moved the input topic's log start past 0 then offset
   * 0 no longer exists, the next poll takes an OffsetOutOfRange and the consumer falls back to its
   * auto.offset.reset policy, which is unset throughout this repository and therefore defaults to
   * latest. So the fallback resumes at the END of the input topic and replays nothing. That is
   * long-standing behaviour rather than something introduced here, but it is not the "start from the
   * beginning" it looks like, and setting KAFKA.CONSUMER.auto.offset.reset=earliest is what would make
   * it so.
   * <p>
   * One case stays imprecise: an engine whose entire history is the single record at offset 0 cannot be
   * told apart from an empty topic through this signal, and re-applies that record.
   */
  static long resumeInputOffset(final long lastAppliedInputOffset) {
    if (lastAppliedInputOffset <= 0) {
      return 0;
    }
    return lastAppliedInputOffset + 1;
  }

  /**
   * The input offset of the newest message whose effects are genuinely present in the state rebuilt by
   * the snapshot load and the output replay. Zero when that cannot be determined.
   * <p>
   * Found by scanning the output topic backwards for the newest record that carries an input offset.
   * A record only counts if its output transaction is complete, which is what
   * {@link #isAppliedBoundary} decides -- see there for why an incomplete one must be skipped.
   */
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
        final long transactionId = headerDecoder.transactionId();
        final int transactionEnd = headerDecoder.transactionEnd();
        long offset = headerDecoder.kafkaRecordOffset();

        if (!isAppliedBoundary(transactionId, transactionEnd)) {
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info(LOG_FMT_4, "Ignoring output record from an unterminated transaction. transactionId: ", transactionId,
                ", inputOffset: ", offset);
          }
          continue;
        }

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

  /**
   * Whether an output record proves the input message that produced it was applied, and can therefore
   * be used as the resume boundary.
   * <p>
   * The matcher wraps each input message's output records in one transaction, stamping every record
   * with the transaction id and marking only the last one. Publishing is fire-and-forget with
   * retries=0, so a crash or a single failed send can leave the earlier records of a set on the topic
   * without the closing one.
   * <p>
   * That matters because of how the replay consumes them. Records with a transaction id of 0 or 1 are
   * passed straight through, but anything higher is buffered by the receiver queue until the record
   * marked last arrives, and the snapshot replay never flushes what is left over. So an unterminated
   * transaction's records are stamped with an input offset and yet were never applied -- and taking
   * one as the boundary would resume past an input message that never took effect. For a deposit that
   * loses the money outright, which is worse than the duplicate the resume offset exists to prevent.
   */
  static boolean isAppliedBoundary(final long transactionId, final int transactionEnd) {
    return transactionEnd == 1 || transactionId <= 1;
  }

  public long getStartPointOffsetOffset() {
    final KafkaListener reader = new KafkaListener(getTopic());
    final KafkaConsumer<String, byte[]> consumer = reader.getConsumer();
    final TopicPartition partition = new TopicPartition(getTopic(), 0);
    final List<TopicPartition> partitions = Arrays.asList(partition);

    consumer.assign(partitions);
    consumer.seekToEnd(partitions);

    return consumer.position(partition);
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
      // startPointOffset is the END offset of the input topic as it stood at startup -- the offset the
      // next record produced will receive, not the offset of the last existing one. So the replay
      // window is everything strictly below it. Using <= here pulled in the first record produced
      // after startup, and since this branch drops anything that is not a user registration or a
      // deposit, that discarded one live message per restart.
      if (replaySelected && recordOffset < startPointOffset) {
        if (message instanceof UserAdminMessage userAdminMessage) { // new user registrations
          LOGGER.info(Constants.LOG_FMT_2, "Replaying input: ", userAdminMessage.toJSON());
        } else if (message instanceof BalanceAdminMessage balanceAdminMessage) { // deposits
          if (balanceAdminMessage.getTxType() == 3) {
            LOGGER.info(Constants.LOG_FMT_2, "Replaying input: ", balanceAdminMessage.toJSON());
          } else {
            return;
          }
        } else {
          return;
        }
      }

      if (Context.isCopyTradeOnly()) {
        //ignore unsupported messages in copy trade mode
        boolean ignore = true;
        if (message instanceof Order || message instanceof HeartbeatMessage) {
          ignore = false;
        }

        if (ignore) {
          return;
        }
      }

      // Decode transaction details
      try {
        if (message != null) {
          message.setTransactionId(headerDecoder.transactionId());
          message.setLastMessageInTransaction(headerDecoder.transactionEnd() == 1);
        }
      } catch (Exception e) {
        LOGGER.error(LOG_FMT_3, "Error decoding transaction. TargetLocationId:", headerDecoder.transactionId(), ", Message:", e);
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
          //LOGGER.info("Order message decoded: " + message.toJSON());
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
        case AssetGroupDecoder.TEMPLATE_ID:
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(ASSET_GROUP_REQUEST);
          }
          assetGroupDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          message = assetGroupRequestHandler.decodeAssetGroupRequest(headerDecoder, assetGroupDecoder);
          return message;
        case OrderFilterDecoder.TEMPLATE_ID:
          orderFilterDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          message = orderFilterHandler.decodeOrderFilterRequest(headerDecoder, orderFilterDecoder);
          return message;
        case LiquidityResponseDecoder.TEMPLATE_ID:
          liquidityResponseDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          message = liquidityHandler.decodeLiquidityMessage(headerDecoder, liquidityResponseDecoder);
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
