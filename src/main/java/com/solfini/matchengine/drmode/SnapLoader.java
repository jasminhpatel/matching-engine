package com.solfini.matchengine.drmode;

import java.io.File;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import com.solfini.matchengine.AssetGroupCache;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.drmode.message.DRBusinessRejectMessage;
import com.solfini.matchengine.drmode.message.DRCancelRejectMessage;
import com.solfini.matchengine.kafka.KafkaAdminInputFixListener;
import com.solfini.matchengine.kafka.KafkaListener;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.SnapResponseAdminMessage;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.matchengine.message.session.NetworkStatusMessage;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import com.solfini.sbe.encoder.AssetGroupDecoder;
import com.solfini.sbe.encoder.BusinessRejectDecoder;
import com.solfini.sbe.encoder.ExecutionReportDecoder;
import com.solfini.sbe.encoder.HeartbeatDecoder;
import com.solfini.sbe.encoder.LogonDecoder;
import com.solfini.sbe.encoder.LogoutDecoder;
import com.solfini.sbe.encoder.MarketDataFeedDecoder;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.NetworkStatusDecoder;
import com.solfini.sbe.encoder.OptionPricingFeedDecoder;
import com.solfini.sbe.encoder.OrderCancelRejectDecoder;
import com.solfini.sbe.encoder.PositionReportDecoder;
import com.solfini.util.LogLevel;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

public class SnapLoader implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(SnapLoader.class);

  private static final String ME_KAFKA_TOPIC_PRIMARY = PropertyReader.getProperty("ME_KAFKA_TOPIC_PRIMARY", "me1");
  public static final byte NORMAL_API = (byte) 2;
  public static final byte ADMIN_API = (byte) 4;

  private final ChronicleQueue queue;
  private final ExcerptTailer tailer;
  private final long snapId;
  private long lastSequenceNumber = 0;
  private long lastKafkaInputOffset = -1;
  private long lastKafkaOutputOffset = -1;

  // process bytes with 17 offset
  private static final int KAFKA_OFFSET = 17;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;


  private KafkaAdminInputFixListener kafkaAdminInputFixListener;
  private static volatile boolean snapLoaderMode = true;

  private final ManyToOneConcurrentArrayQueueCustom<com.solfini.common.Message> fixDRToMatcherQueue = Context.getReceiverToMatcherQueue();

  private final ExecutionReportParser executionReportParser = new ExecutionReportParser();
  private final PositionReportParser positionReportParser = new PositionReportParser();
  private final NetworkStatusResponseParser networkStatusResponseParser = new NetworkStatusResponseParser();



  private final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final ExecutionReportDecoder executionReportDecoder = new ExecutionReportDecoder();
  private final PositionReportDecoder positionReportDecoder = new PositionReportDecoder();
  private final AssetGroupDecoder assetGroupDecoder = new AssetGroupDecoder();
  private final NetworkStatusDecoder networkStatusDecoder = new NetworkStatusDecoder();

  public static final void setSnapLoaderMode(final boolean value) {
    snapLoaderMode = value;
  }

  public static final boolean isSnapLoaderMode() {
    return snapLoaderMode;
  }

  public SnapLoader(final long snapId) {
    this.snapId = snapId;
    String dir = Context.getChronicleEngineSnapQueueDirectory() + "/" + snapId;
    kafkaAdminInputFixListener = new KafkaAdminInputFixListener(snapLoaderMode);

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, ">>> SnapLoader snapId=", snapId, DIR_EQ, dir);
    }
    try {

      for (int i = 0; i < 1_000_000; i++) {
        File folder = new File(dir + "/done");
        if (folder.exists()) {
          break;
        }

        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn(LOG_FMT_4, ">>> SnapLoader waiting snapId=", snapId, DIR_EQ, dir);
        }
        Thread.sleep(1);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    queue = SingleChronicleQueueBuilder.single(dir).blockSize(1048576).rollCycle(RollCycles.DAILY).build();
    tailer = queue.createTailer(); // read queue from beginning
    if (LOGGER.isWarnEnabled()) {
      LOGGER.warn(LOG_FMT_4, "<<< SnapLoader snapId=", snapId, DIR_EQ, dir);
    }
  }

  public final long getSnapId() {
    return snapId;
  }

  public void close() {
    queue.close();
  }

  public long load() {
    long offset = loadSnapshot();
    snapLoaderMode = false;
    return offset;
  }

  public long loadPrimary(final boolean replay) {
    long offset = loadSnapshot();
    if (replay) {
      offset = replaySnapshot(0);
      // reload AssetGroups in compacted Kafka first, because AssetGroup messages are not in the output topic.
      AssetGroupCache.loadAssetGroupsFromKafka();
    }

    snapLoaderMode = false;
    return offset;
  }

  public long loadSecondary(final boolean replay) {
    long offset = loadSnapshot();
    if (replay) {
      offset = replaySnapshot(offset);
    }

    snapLoaderMode = false;
    return offset;
  }

  public long loadSnapshot() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "Loading snapshot: snapId=", snapId);
    }

    try {
      Bytes<ByteBuffer> bytes = Bytes.elasticHeapByteBuffer(32768);
      boolean read;
      while (true) {
        try {
          long ipcIndex = tailer.index();
          bytes.clear();
          read = tailer.readBytes(bytes);

          if (read) {
            byte[] data = bytes.underlyingObject().array();
            int len = (int) bytes.readRemaining();

            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(LOG_FMT_6, "read+", read, LEN_EQ, len, DATA_EQ, StringUtil.fixToString(data, len));
            }

            // seqNum
            long seqNum = 0;
            for (int i = 0; i < 8; i++) {
              seqNum <<= 8;
              seqNum |= (data[i] & 0xFF);
            }
            // sendTime
            long sendTime = 0;
            for (int i = 8; i < 16; i++) {
              sendTime <<= 8;
              sendTime |= (data[i] & 0xFF);
            }
            // messageType
            byte messageType = data[16];

            // skip already read messages
            if (lastSequenceNumber > seqNum && seqNum > 0) {
              if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(LOG_FMT_12, ">>> SKIPPING ipc read=", read, LEN_EQ, len, DATA_EQ, StringUtil.fixToString(data), SEQNUM_EQ,
                    seqNum, " lastSequenceNumber=", lastSequenceNumber, IPCINDEX_EQ, ipcIndex);
              }
              continue;
            }
            if (lastSequenceNumber == 0)
              lastSequenceNumber = seqNum;
            else if (seqNum != lastSequenceNumber + 1) {
              if (LOGGER.isDebugEnabled()) {
                LOGGER.warn(LOG_FMT_12, ">>> Unexcpected seq. ipc read=", read, LEN_EQ, len, DATA_EQ, StringUtil.fixToString(data),
                    SEQNUM_EQ, seqNum, ", lastSequenceNumber=", lastSequenceNumber, IPCINDEX_EQ, ipcIndex);
              }

              long diff = 1 + seqNum - lastSequenceNumber;
              tailer.moveToIndex(ipcIndex - diff);
              continue;
            } else
              lastSequenceNumber++;

            if (LOGGER.isDebugEnabled()) {
              LOGGER.warn(LOG_FMT_12, ">>> ipc read=", read, LEN_EQ, data.length, LEN_EQ, data.length, DATA_EQ,
                  StringUtil.fixToString(data, len), SEQNUM_EQ, seqNum, IPCINDEX_EQ, ipcIndex);
            }

            // route admin messages
            if (KafkaPublisher.ADMIN_API == messageType) {
              long recordOffset = 0;
              Message message = kafkaAdminInputFixListener.onMessageSnapLoader(seqNum, sendTime, recordOffset, messageType, data);
              if (message instanceof SnapResponseAdminMessage) {
                if (LOGGER.isInfoEnabled()) {
                  LOGGER.info(LOG_FMT_2, "Snapshot loading complete: lastMessage=", message);
                }

                SnapResponseAdminMessage snapResponseAdminMessage = (SnapResponseAdminMessage) message;
                NewOrderSingleHandler.setOrderIdIfGreater(snapResponseAdminMessage.getOrderId());
                GlobalOrderBook.setOrderIdIfGreater(1, snapResponseAdminMessage.getOrderId());
                GlobalOrderBook.setFilledCountGlobalIfGreater(snapResponseAdminMessage.getExecId());

                lastKafkaOutputOffset = ((SnapResponseAdminMessage) message).getOutputKafkaRecordOffset();
                return lastKafkaOutputOffset; // end loop
              }
            } else {
              SnapResponseAdminMessage message = handleMessage(data, len - KAFKA_OFFSET);
              if (message != null) {
                if (LOGGER.isInfoEnabled()) {
                  LOGGER.info(LOG_FMT_2, "Snapshot loading complete: lastMessage=", message);
                }
                return lastKafkaOutputOffset; // end loop
              }
            }

          }
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, e);
        }

      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return -1;
  }

  private long replaySnapshot(final long replayEndOffset) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "SnapLoader:Replay - Starting replay for snapshot ", snapId);
    }

    // We need to replay rest of the messages from the output queue
    final KafkaListener reader = new KafkaListener(ME_KAFKA_TOPIC_PRIMARY);
    final KafkaConsumer<String, byte[]> consumer = reader.getConsumer();
    final TopicPartition partition = new TopicPartition(reader.getTopic(), 0);
    final List<TopicPartition> partitions = Arrays.asList(partition);
    final Duration timeout = Duration.ofMillis(1000);

    // Seek the output queue to the correct position
    consumer.assign(partitions);
    consumer.seekToEnd(partitions);
    long lastOffset = consumer.position(partition);
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "SnapLoader:Replay - Last offset on the output queue is ", (lastOffset - 1), " replayEndOffset: ", replayEndOffset);
    }

    if (replayEndOffset > 0) {
      lastOffset = replayEndOffset + 1;
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_4, "SnapLoader:Replay - Setting replay end offset to ", (lastOffset - 1), " replayEndOffset: ", replayEndOffset);
      }
    }

    if (lastKafkaOutputOffset < 0) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(LOG_FMT_4, "Replay aborted as snapshot does not contain a valid output queue offset: SnapshotOffset=",
            lastKafkaOutputOffset, ", QueueLastOffset=", lastOffset);
      }
      return -1;
    }

    if ((lastKafkaOutputOffset >= lastOffset) || (lastOffset <= 0)) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(LOG_FMT_4,
            "Replay aborted as output queue does not have messages corresponding to the last message on snapshot: SnapshotOffset=",
            lastKafkaOutputOffset, ", QueueLastOffset=", lastOffset);
      }
      return -1;
    }

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "SnapLoader:Replay - Starting replay on the output queue from offset ", lastKafkaOutputOffset);
    }
    consumer.seek(partition, lastKafkaOutputOffset);

    lastSequenceNumber = 0;
    while (true) {
      ConsumerRecords<String, byte[]> records = consumer.poll(timeout);
      if (records == null || records.isEmpty()) {
        continue;
      }

      for (ConsumerRecord<String, byte[]> record : records) {
        byte[] data = record.value();

        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_4, "Replaying message at offset ", record.offset(), ": ", StringUtil.fixToString(data));
        }

        // seqNum
        long seqNum = 0;
        for (int i = 0; i < 8; i++) {
          seqNum <<= 8;
          seqNum |= (data[i] & 0xFF);
        }
        // sendTime
        long sendTime = 0;
        for (int i = 8; i < 16; i++) {
          sendTime <<= 8;
          sendTime |= (data[i] & 0xFF);
        }
        // messageType
        byte messageType = data[16];

        boolean skip = false;
        if (lastSequenceNumber > seqNum) {
          if (seqNum > 0) {
            if (LOGGER.isWarnEnabled()) {
              LOGGER.warn(LOG_FMT_6, ">>> SKIPPING replay: data=", StringUtil.fixToString(data), SEQNUM_EQ, seqNum, " lastSequenceNumber=",
                  lastSequenceNumber, ", kafkaOffset=", record.offset());
            }
            skip = true;
          }
        } else if (lastSequenceNumber == 0) {
          lastSequenceNumber = seqNum;
        } else if (seqNum != lastSequenceNumber + 1) {
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(LOG_FMT_6, ">>> Unexpected sequence on replay: data=", StringUtil.fixToString(data), SEQNUM_EQ, seqNum,
                ", lastSequenceNumber=", lastSequenceNumber, ", kafkaOffset=", record.offset());
          }
          skip = true;
        } else {
          lastSequenceNumber++;
        }

        if (!skip) {
          if (KafkaPublisher.ADMIN_API == messageType) {
            final long recordOffset = 0;
            kafkaAdminInputFixListener.onMessage(seqNum, sendTime, recordOffset, messageType, data);
          } else {
            handleMessage(data, data.length - KAFKA_OFFSET);
          }
        }

        if (record.offset() == lastOffset - 1) {
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info(LOG_FMT_2, "SnapLoader:Replay - Replay completed for snapshot", snapId);
          }
          return lastOffset;
        }
      }
    }
  }

  private SnapResponseAdminMessage handleMessage(final byte[] data, final int length) {
    try {
      Message message = null;
      try {
        message = decode(data, length);

        decoderUnsafeBuffer.wrap(data);

        // sourceSeqNum
        long sourceSeqNum = headerDecoder.sourceSeqNum();
        // kafkaRecordOffset
        long kafkaRecordOffset = headerDecoder.kafkaRecordOffset();

        final String senderCompId = headerDecoder.senderCompId();
        final long msgSeqNum = headerDecoder.msgSeqNum();


        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(">>> handleMessage msgSeqNum=", msgSeqNum, SENDERCOMPID_EQ, senderCompId, SOURCESEQNUM_EQ, sourceSeqNum,
              KAFKARECORDOFFSET_EQ, kafkaRecordOffset, LENGTH_EQ, length, MESSAGE_EQ, StringUtil.fixToString(data, length));
        }

        if (kafkaRecordOffset > 0)
          lastKafkaInputOffset = kafkaRecordOffset;
        if (message instanceof NetworkStatusMessage) // for backward compatibility
          return new SnapResponseAdminMessage(((NetworkStatusMessage) message).getRequestId(),
              ((NetworkStatusMessage) message).getResponseId(), ((NetworkStatusMessage) message).getOrderSequenceNumber(),
              ((NetworkStatusMessage) message).getSenderCompId());
      } catch (Exception e) {
        // decode error!!!
        LOGGER.error("SnapLoader decode error, length=", length, SB_EQ, StringUtil.fixToString(data, length), e);
        return null;
      }

      // Decode transaction details
      try {
        if ((message != null)) {
          message.setTransactionId(headerDecoder.transactionId());
          message.setLastMessageInTransaction(headerDecoder.transactionEnd() == 1);
        }
      } catch (Exception e) {
        LOGGER.error(LOG_FMT_2, "Error decoding transaction. TargetLocationId:", headerDecoder.transactionId(), ", Message:", e);
        return null;
      }

      // regular mode
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_4, "Receiving decoded message=", message, " origMessage=", StringUtil.fixToString(data, length));
      }

      if (message != null) {
        fixDRToMatcherQueue.addGuaranteed(message);
      }

    } catch (Exception e) {
      LOGGER.error("SnapLoader error ", e);
      if (e.getMessage().contains("Queue")) {
        LOGGER.error("SnapLoader Queue error sleeping");
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e1) {
          LOGGER.error(ERROR_LOG, e1);
        }
      }
    }
    return null;
  }


  private final Message decode(final byte[] data, final int length) {
    // wrap bytes
    decoderUnsafeBuffer.wrap(data);
    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);

    try {
      switch (headerDecoder.templateId()) {

        case ExecutionReportDecoder.TEMPLATE_ID:
          executionReportDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());

          Message message = executionReportParser.parse(headerDecoder, executionReportDecoder);
          if (message != null) {
            message.setSenderCompId(headerDecoder.senderCompId());
            message.setSequenceNumber(headerDecoder.msgSeqNum());
            message.setSourceSeqNum(headerDecoder.sourceSeqNum());
            message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
            //LOGGER.info("EXE Decoded: " + message.toJSON());
          }
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_2, DECODED_EXECUTIONREPORT_EQ, message);
          }
          return message;

        case PositionReportDecoder.TEMPLATE_ID:
          positionReportDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());

          message = positionReportParser.parse(headerDecoder, positionReportDecoder);
          if (message != null) {
            message.setSenderCompId(headerDecoder.senderCompId());
            message.setSequenceNumber(headerDecoder.msgSeqNum());
            message.setSourceSeqNum(headerDecoder.sourceSeqNum());
            message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
          }
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_2, DECODED_POSITIONREPORT_EQ, message);
          }
          return message;

        case AssetGroupDecoder.TEMPLATE_ID:
          assetGroupDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());

          final AssetGroup assetGroup = new AssetGroup();
          assetGroup.set(assetGroupDecoder);
          assetGroup.setAvailableQuantity(assetGroup.getQuantity());
          message = assetGroup;
          if (message != null) {
            message.setSenderCompId(headerDecoder.senderCompId());
            message.setSequenceNumber(headerDecoder.msgSeqNum());
            message.setSourceSeqNum(headerDecoder.sourceSeqNum());
            message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
          }
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_2, DECODED_ASSETGROUP_EQ, message);
          }
          LOGGER.info("AssetGroup loaded: " + assetGroup.toJSON());
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
        case LogonDecoder.TEMPLATE_ID:
        case LogoutDecoder.TEMPLATE_ID:
        case HeartbeatDecoder.TEMPLATE_ID:
        case MarketDataSnapshotFullRefreshEncoder.TEMPLATE_ID:
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

  public final long getLastKafkaInputOffset() {
    return lastKafkaInputOffset;
  }

  public final long getLastSequenceNumber() {
    return lastSequenceNumber;
  }

}
