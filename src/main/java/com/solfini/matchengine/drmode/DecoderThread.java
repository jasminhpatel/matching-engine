package com.solfini.matchengine.drmode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.common.TransactionalInputManyToOneConcurrentArrayQueue;
import com.solfini.internal.admin.schema.TradeStateAdminMessageDecoder;
import com.solfini.matchengine.MessageValidator;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.drmode.message.DRBusinessRejectMessage;
import com.solfini.matchengine.drmode.message.DRCancelRejectMessage;
import com.solfini.matchengine.kafka.KafkaAdminInputFixListener;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.SnapResponseAdminMessage;
import com.solfini.matchengine.message.admin.TradeStateAdminMessage;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.matchengine.message.internal.MarketDataFeed;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.matchengine.message.session.NetworkStatusMessage;
import com.solfini.pool.DRRecieverDataObjectPool;
import com.solfini.sbe.encoder.AssetGroupDecoder;
import com.solfini.sbe.encoder.BusinessRejectDecoder;
import com.solfini.sbe.encoder.ExecutionReportDecoder;
import com.solfini.sbe.encoder.HeartbeatDecoder;
import com.solfini.sbe.encoder.LogonDecoder;
import com.solfini.sbe.encoder.LogoutDecoder;
import com.solfini.sbe.encoder.MarketDataFeedDecoder;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshDecoder;
import com.solfini.sbe.encoder.MassCancelOrderDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.NetworkStatusDecoder;
import com.solfini.sbe.encoder.OptionPricingFeedDecoder;
import com.solfini.sbe.encoder.OrderCancelRejectDecoder;
import com.solfini.sbe.encoder.PositionReportDecoder;
import com.solfini.util.LogLevel;
import com.solfini.util.StringUtil;


/**
 *
 * @author Chris Mack
 *
 */
public class DecoderThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(DecoderThread.class);

  // process bytes with 17 offset
  private static final int KAFKA_OFFSET = 17;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;

  public static final int LOAD_STRATEGY_NONE = 0;
  public static final int LOAD_STRATEGY_WAIT_FOR_SNAP = 1;
  public static final int LOAD_STRATEGY_REPLAY_FROM_KAFKA_OFFSET = 2;

  private final NetworkStatusResponseParser networkStatusResponseParser = new NetworkStatusResponseParser();
  private final ExecutionReportParser executionReportParser = new ExecutionReportParser();
  private final PositionReportParser positionReportParser = new PositionReportParser();
  private final KafkaAdminInputFixListener kafkaAdminInputFixListener;

  private final int lockId;
  private final int nextId;
  private final AtomicInteger roundRobinLock;
  private final int NUM_DECODER_THREADS = Context.getDecoderThreads();


  private final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final ExecutionReportDecoder executionReportDecoder = new ExecutionReportDecoder();
  private final PositionReportDecoder positionReportDecoder = new PositionReportDecoder();
  private final NetworkStatusDecoder networkStatusDecoder = new NetworkStatusDecoder();
  private final MarketDataFeedDecoder marketDataFeedDecoder = new MarketDataFeedDecoder();
  private final MassCancelOrderDecoder massCancelOrderDecoder = new MassCancelOrderDecoder();
  private final AssetGroupDecoder assetGroupDecoder = new AssetGroupDecoder();

  private final TransactionalInputManyToOneConcurrentArrayQueue fixDRToMatcherQueue = Context.getReceiverToMatcherQueue();
  private final OneToOneConcurrentArrayQueueCustom<RecieverData> decoderQueue;
  private final ManyToOneConcurrentArrayQueueCustom<com.solfini.common.Message> persisterQueue = Context.getPublisherToPersisterQueue();
  private final ManyToOneConcurrentArrayQueueCustom<PositionReportMessage> persisterPositionQueue =
      Context.getPublisherToPersisterPositionQueue();

  private volatile boolean stopRequested = false;
  private volatile CountDownLatch stopLatch = null;

  public DecoderThread(final IdleStrategy idleStrategy, final int lockId, final AtomicInteger roundRobinLock) {
    this.decoderQueue = new OneToOneConcurrentArrayQueueCustom<>(Context.getQueueCapacity(), "DecoderThread" + lockId);
    this.lockId = lockId;
    this.roundRobinLock = roundRobinLock;
    this.nextId = (lockId + 1 >= NUM_DECODER_THREADS) ? 0 : lockId + 1;
    kafkaAdminInputFixListener = KafkaAdminInputFixListener.build();
  }

  public final int getLockId() {
    return this.lockId;
  }

  public final int getNextId() {
    return this.nextId;
  }

  public final AtomicInteger getRoundRobinLock() {
    return this.roundRobinLock;
  }

  // called by listener thread
  public boolean add(final RecieverData message) {
    return decoderQueue.addGuaranteed(message);
  }

  private final Message decode(final byte[] data, final int length) {

    // wrap bytes
    decoderUnsafeBuffer.wrap(data);
    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);
    LOGGER.info(LOG_FMT_2, "DecoderThread ", headerDecoder.templateId());
    try {
      switch (headerDecoder.templateId()) {
        case ExecutionReportDecoder.TEMPLATE_ID:
          executionReportDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());

          Message message = executionReportParser.parse(headerDecoder, executionReportDecoder);
          LOGGER.info(LOG_FMT_2, "executionReport message=", message);
          if (message != null) {
            message.setSenderCompId(headerDecoder.senderCompId());
            message.setSequenceNumber(headerDecoder.msgSeqNum());
            message.setSourceSeqNum(headerDecoder.sourceSeqNum());
            message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
            if (Context.isPersistModeEnabled()) {

              // Acquire lock to prevent orders being out of order in the database
              acquireLock();
              persisterQueue.add(ExecutionReportMessage.createFromDecoder(headerDecoder, executionReportDecoder));
            }
          }
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_2, DECODED_EXECUTIONREPORT_EQ, message);
          }
          return message;
        case PositionReportDecoder.TEMPLATE_ID:
          positionReportDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());

          message = positionReportParser.parse(headerDecoder, positionReportDecoder);
          LOGGER.info(LOG_FMT_2, "positionReport message=", message);
          if (message != null) {
            message.setSenderCompId(headerDecoder.senderCompId());
            message.setSequenceNumber(headerDecoder.msgSeqNum());
            message.setSourceSeqNum(headerDecoder.sourceSeqNum());
            message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
            if (Context.isPersistModeEnabled()) {
              // Acquire lock to prevent reports being out of order in the database
              acquireLock();
              persisterPositionQueue
                  .add(PositionReportMessage.createFromDecoder(headerDecoder, positionReportDecoder, (BalanceAdminMessage) message));
            }
          }
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_2, DECODED_POSITIONREPORT_EQ, message);
          }
          return message;
        case NetworkStatusDecoder.TEMPLATE_ID:
          // new snap available
          networkStatusDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());

          message = networkStatusResponseParser.parse(headerDecoder, networkStatusDecoder);
          if (message != null) {
            message.setSenderCompId(headerDecoder.senderCompId());
            message.setSequenceNumber(headerDecoder.msgSeqNum());
            message.setSourceSeqNum(headerDecoder.sourceSeqNum());
            message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
          }
          return message;

        // Business Reject messages are decoded to update the last order id in case of a failover right after.
        case BusinessRejectDecoder.TEMPLATE_ID:
          BusinessRejectDecoder businessRejectDecoder = new BusinessRejectDecoder();
          businessRejectDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          DRBusinessRejectMessage businessRejectMessage = new DRBusinessRejectMessage(businessRejectDecoder);

          // Update from orderId
          NewOrderSingleHandler.setOrderIdIfGreater(businessRejectMessage.getOrderId());

          // Update from cancelId
          NewOrderSingleHandler.setOrderIdIfGreater(businessRejectMessage.getCancelId());

          // Update from cancelReplaceId
          NewOrderSingleHandler.setOrderIdIfGreater(businessRejectMessage.getCancelReplaceId());

          // Update secondary order id of the instrument
          NewOrderSingleHandler.setSecondaryOrderIdIfGreater(businessRejectMessage.getPairId(),
              businessRejectMessage.getSecondaryOrderId());

          message = businessRejectMessage;
          return message;

        // Cancel Reject messages are decoded to update the last order id in case of a failover right after.
        case OrderCancelRejectDecoder.TEMPLATE_ID:
          OrderCancelRejectDecoder cancelRejectDecoder = new OrderCancelRejectDecoder();
          cancelRejectDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());

          DRCancelRejectMessage cancelRejectMessage = new DRCancelRejectMessage(cancelRejectDecoder);

          // Update from cancel id
          NewOrderSingleHandler.setOrderIdIfGreater(cancelRejectMessage.getCancelId());

          // Update from cancel replace id
          NewOrderSingleHandler.setOrderIdIfGreater(cancelRejectMessage.getCancelReplaceId());

          message = cancelRejectMessage;
          return message;

        case MarketDataFeedDecoder.TEMPLATE_ID:
          marketDataFeedDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          MarketDataFeed marketDataFeed = new MarketDataFeed();
          marketDataFeed.set(marketDataFeedDecoder);

          message = marketDataFeed;
          return message;

        case AssetGroupDecoder.TEMPLATE_ID:
          assetGroupDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());
          AssetGroup assetGroup = new AssetGroup();
          assetGroup.set(assetGroupDecoder);

          message = assetGroup;
          return message;

        case MassCancelOrderDecoder.TEMPLATE_ID:
          massCancelOrderDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());

          MassCancelOrder massCancelOrder = new MassCancelOrder(massCancelOrderDecoder);

          // Update from cancel id
          NewOrderSingleHandler.setOrderIdIfGreater(massCancelOrder.getCancelId());

          message = massCancelOrder;
          return message;

        case HeartbeatDecoder.TEMPLATE_ID:
        case LogonDecoder.TEMPLATE_ID:
        case LogoutDecoder.TEMPLATE_ID:
        case MarketDataSnapshotFullRefreshDecoder.TEMPLATE_ID:
        case OptionPricingFeedDecoder.TEMPLATE_ID:
          return null;

        default:
          if (LogLevel.warn()) {
            LOGGER.warn(LOG_FMT_3, "Unable to decode message: data=", data);
          }
          return null;
      }
    } catch (Exception e) {
      LOGGER.error("Error in decode", e);
      throw e;
    }
  }

  public void stopThread() throws InterruptedException {
    if (!stopRequested) {
      stopLatch = new CountDownLatch(1);
      stopRequested = true;

      stopLatch.await();
    }
  }

  private void handleAdminMessages(final RecieverData recieverData) {
    final long seqNum = recieverData.getSeqNum();
    final long sendTime = recieverData.getSendTime();
    final long recordOffset = recieverData.getRecordOffset();
    final byte messageType = recieverData.getMessageType();
    final byte[] data = recieverData.getData();
    final int length = data.length - KAFKA_OFFSET;


    // block wait until thread obtains lock
    acquireLock();

    try {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_3, "decoder publishing admin roundRobinLock=", roundRobinLock.get(), LOCKID_EQ, lockId, MESSAGETYPE_EQ,
            messageType, ", seqNum=", seqNum, SOURCESENDTIME_EQ, sendTime, " message=", data);
      }
      // check if valid
      if (roundRobinLock.get() != lockId) {
        LOGGER.error("WTF, roundRobinLock=" + roundRobinLock.get() + LOCKID_EQ + lockId);
      }

      try {
        if (LOAD_STRATEGY_WAIT_FOR_SNAP == Context.getLoadStrategy()) {
          final Message message = kafkaAdminInputFixListener.onMessageSnapLoader(seqNum, sendTime, recordOffset, messageType, data);
          if (message instanceof SnapResponseAdminMessage) {
            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_2, "WarmStart: Received new snapshot: ", message);
            }
            final long snapId = ((SnapResponseAdminMessage) (message)).getSnapId();

            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(LOG_FMT_2, "calling SnapLoader ", snapId);
            }
            final SnapLoader snapLoader = new SnapLoader(snapId);
            snapLoader.load(); // block until loaded

            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_2, "WarmStart: Snapshot loading completed and state recovered: snapId=", snapId);
            }
            Context.setLoadStrategy(LOAD_STRATEGY_NONE);
          }
        } else {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_10, ">>>>DecoderThread: Received new adminMessage: msgType=", messageType, LENGTH_EQ, length, SENDTIME_EQ,
                sendTime, MSGSEQNUM_EQ, seqNum, LOCKID_EQ, lockId);
          }
          kafkaAdminInputFixListener.onMessage(seqNum, sendTime, recordOffset, messageType, data);
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_10, "<<<<DecoderThread: Received new adminMessage: msgType=", messageType, LENGTH_EQ, length, SENDTIME_EQ,
                sendTime, MSGSEQNUM_EQ, seqNum, LOCKID_EQ, lockId);
          }
        }

      } catch (Exception e) {
        // decode error!!!
        LOGGER.error(LOG_FMT_10, DECODE_ERROR_MSGTYPE_EQ, messageType, LENGTH_EQ, length, SENDTIME_EQ, sendTime, MSGSEQNUM_EQ, seqNum,
            SB_EQ, StringUtil.fixToString(data), e);
      }

      // release lock to next thread
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_12, ">>>>DecoderThread: release lock new adminMessage: msgType=", messageType, LENGTH_EQ, length, SENDTIME_EQ,
            sendTime, MSGSEQNUM_EQ, seqNum, LOCKID_EQ, lockId, ROUNDROBINLOCKID_EQ, roundRobinLock.get());
      }
    } catch (Exception e) {
      LOGGER.error(LOG_FMT_10, DECODE_ERROR_MSGTYPE_EQ, messageType, LENGTH_EQ, length, SENDTIME_EQ, sendTime, MSGSEQNUM_EQ, seqNum, SB_EQ,
          StringUtil.fixToString(data), e);
    }

    // Add RecieverData back to pool
    DRRecieverDataObjectPool.returnObject(recieverData);

    releaseLock();

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_12, "<<<<DecoderThread: release lock new adminMessage: msgType=", messageType, LENGTH_EQ, length, SENDTIME_EQ,
          sendTime, MSGSEQNUM_EQ, seqNum, LOCKID_EQ, lockId, ROUNDROBINLOCKID_EQ, roundRobinLock.get());
    }

  }

  // block wait until thread obtains lock
  private void acquireLock() {
    DecoderThreadCache.blockWaitGetLock();
  }

  // Releases the lock
  private void releaseLock() {
    boolean rc = roundRobinLock.compareAndSet(this.lockId, nextId); // release lock to next thread
    if (!rc)
      LOGGER.error(LOG_FMT_2, "error, lock release failed. lockId=", this.lockId, NEXTID_EQ, nextId, ROUNDROBINLOCKID_EQ, roundRobinLock);
  }


  public void run() {
    while (true) {
      try {
        final RecieverData recieverData = decoderQueue.poll();
        if (recieverData == null) {
          if (stopRequested) {
            stopLatch.countDown();
            return;
          }

          continue;
        }

        final byte messageType = recieverData.getMessageType();

        // handle admin messages
        if (KafkaPublisher.ADMIN_API == messageType) {
          handleAdminMessages(recieverData);
          continue; // end of handle admin
        }

        // handle regular messages
        final long seqNum = recieverData.getSeqNum();
        final long sendTime = recieverData.getSendTime();
        final byte[] data = recieverData.getData();
        final int length = data.length - OFFSET;

        Message message = null;
        try {
          message = decode(data, length);

        } catch (Exception e) {
          // decode error!!!
          LOGGER.error(DECODE_ERROR_MSGTYPE_EQ + messageType + LENGTH_EQ + length + SB_EQ + StringUtil.fixToString(data), e);

          // block wait until thread obtains lock so that we dont break the message ordering
          acquireLock();
          releaseLock();
          continue;
        }

        // Decode transaction details
        try {
          if ((message != null)) {
            message.setTransactionId(headerDecoder.transactionId());
            message.setLastMessageInTransaction(headerDecoder.transactionEnd() == 1);
          }
        } catch (Exception e) {
          LOGGER.error(LOG_FMT_2, "Error decoding transaction. TargetLocationId:", headerDecoder.transactionId(), ", Message:", e);
          acquireLock();
          releaseLock();
          continue;
        }

        acquireLock();

        try {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_10, "decoder publishing roundRobinLock=", roundRobinLock.get(), LOCKID_EQ, lockId, SEQNUM_EQ, seqNum,
                SOURCESENDTIME_EQ, sendTime, MESSAGE_EQ, message);
          }
          // check if valid
          if (roundRobinLock.get() != lockId) {
            LOGGER.error("WTF, roundRobinLock=" + roundRobinLock.get() + LOCKID_EQ + lockId);
          }

          // Validate message
          final MessageValidator validator = Context.getMessageValidator();
          if ((message != null) && (validator != null) && !validator.validate(message.getSenderCompId())) {
            LOGGER.warn(LOG_FMT_3, INVALID_SENDER_INSTANCE_ID, MESSAGE_EQ, message);
            message = null; // setting to null so that the message will no longer be processed.
          }

          if (message != null) {
            message.setSourceSeqNum(seqNum);
            message.setSourceSendTime(sendTime);
            final int loadStrategy = Context.getLoadStrategy();

            try {
              if (loadStrategy == LOAD_STRATEGY_NONE) {
                fixDRToMatcherQueue.addGuaranteed(message);
                DecoderThreadCache.counter.incrementAndGet();
              } else if (loadStrategy == LOAD_STRATEGY_WAIT_FOR_SNAP) {
                if (message instanceof NetworkStatusMessage) {
                  if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(LOG_FMT_2, "WarmStart: Received new snapshot: ", message);
                  }
                  final long snapId = ((NetworkStatusMessage) (message)).getRequestId();

                  if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(LOG_FMT_2, "calling SnapLoader ", snapId);
                  }
                  final SnapLoader snapLoader = new SnapLoader(snapId);
                  snapLoader.load(); // block until loaded

                  if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(LOG_FMT_2, "WarmStart: Snapshot loading completed and state recovered: snapId=", snapId);
                  }
                  Context.setLoadStrategy(LOAD_STRATEGY_NONE);
                } else {
                  if (LogLevel.warn()) {
                    LOGGER.warn("Skipping snapshot loading");
                  }
                }
              }
            } catch (Exception e) {
              LOGGER.error(ERROR_LOG, e);
            }
          } else {

            // Flush the transaction input queue as the un decoded message is the last
            // message.
            if (headerDecoder.transactionEnd() == 1) {
              fixDRToMatcherQueue.flush();
            }

          }

        } catch (Exception e) {
          LOGGER.error("error 2", e);
        } finally {
          // Add RecieverData back to pool
          DRRecieverDataObjectPool.returnObject(recieverData);

          // release
          releaseLock();
        }

      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }

    }
  }

}
