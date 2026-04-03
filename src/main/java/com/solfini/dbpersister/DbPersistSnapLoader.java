package com.solfini.dbpersister;

import com.solfini.common.*;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.*;
import com.solfini.matchengine.MessageValidator;
import com.solfini.matchengine.decoder.InboundAdminMessageHandler;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.SnapResponseAdminMessage;
import com.solfini.matchengine.message.admin.TradeStateAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import com.solfini.util.LogLevel;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

import java.io.File;
import java.nio.ByteBuffer;

import static com.solfini.common.Constants.*;

public class DbPersistSnapLoader {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(DbPersistSnapLoader.class);
  private static final byte ADMIN_API = (byte) 4;
  private static final int OFFSET = 19;
  private static final MessageHeaderDecoder ADMIN_MESSAGE_HEADER_DECODER = new MessageHeaderDecoder();
  private final com.solfini.internal.schema.MessageHeaderDecoder messageHeaderDecoder =
      new com.solfini.internal.schema.MessageHeaderDecoder();
  private final ByteBuffer buffer = ByteBuffer.allocateDirect(32768);
  final ByteBuffer messageBuffer = ByteBuffer.allocateDirect(32768);
  final MutableAsciiBuffer mab = new MutableAsciiBuffer(messageBuffer);
  private final InboundAdminMessageHandler inboundAdminMessageHandler = new InboundAdminMessageHandler();
  private final ChronicleQueue queue;
  private final ExcerptTailer tailer;

  private long lastSequenceNumber = 0;

  public DbPersistSnapLoader(final long snapId) {
    final String dir = Context.getChronicleEngineSnapQueueDirectory() + "/" + snapId;
    System.out.println("Loading snap from: " + dir);
    LOGGER.info(LOG_FMT_2, "Loading snap from: ", dir);
    try {
      for (int i = 0; i < 1_000_000; i++) {
        File folder = new File(dir + "/done");
        if (folder.exists()) {
          break;
        }
        Thread.sleep(1);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    queue = SingleChronicleQueueBuilder.single(dir).blockSize(1048576).rollCycle(RollCycles.DAILY).build();
    tailer = queue.createTailer(); // read queue from beginning
  }

  public long loadSnapshot() {
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
            if (ADMIN_API == messageType) {
              Message message = onMessageSnapLoader(seqNum, sendTime, 0, messageType, data);
              if (message instanceof SnapResponseAdminMessage) {
                LOGGER.info(LOG_FMT_2, "Snapshot loading complete: lastMessage=", message);

                return 1; // end loop
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

  public final Message onMessageSnapLoader(final long seqNum, final long sendTime, final long recordOffset, final byte messageType,
      final byte[] data) {
    final long latency = (TimeUtil.getTime() - sendTime) / 1000; // in microseconds
    final int length = data.length - OFFSET;


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

  private Message handleAdminMessage(final ByteBuffer buffer, final int length, final long seqNum, final long sendTime,
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
      case UserAdminMessageDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(LOG_FMT_1, USER_ADMIN_MESSAGE_RECEIVED);
        }
        message = inboundAdminMessageHandler.decodeAdminUserUpdate(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
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
      case SnapResponseAdminMessageDecoder.TEMPLATE_ID:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(SNAPRESPONSEADMINMESSAGE_MESSAGE_RECEIVED);
        }
        SnapResponseAdminMessage snapResponseAdminMessage =
            inboundAdminMessageHandler.decodeSnapResponseAdminMessage(unsafeBuffer, ADMIN_MESSAGE_HEADER_DECODER, connectionId);
        if (snapResponseAdminMessage != null) {
          snapResponseAdminMessage.setSourceSeqNum(seqNum);
          snapResponseAdminMessage.setSourceSendTime(sendTime);
          snapResponseAdminMessage.setKafkaRecordOffset(recordOffset);
          snapResponseAdminMessage.setInputKafkaRecordOffset(recordOffset);

          return snapResponseAdminMessage; // special case for ending SnapResponseAdminMessage Message
        }
        break;
      default:
        LOGGER.error(LOG_FMT_4, "Kafka Unsupported templateId=", templateId, MESSAGE_EQ, message);
        throw new UnsupportedOperationException("templateId=" + templateId + MESSAGE_EQ + message);
    }

    if (message != null) {
      // Validate message
      final MessageValidator validator = Context.getMessageValidator();

      if (validator != null) {
        // Set the new instance id if the message is a trade state admin message with
        // failover flag set.
        if ((message.getMessageType() == MessageType.TRADE_STATE_ADMIN) && (((TradeStateAdminMessage) message).getMarketStatus() == MarketStatus.FAILOVER)) {

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
        //Context.getTimeTriggerThread().registerMessage(message);
      } else {
        if (message instanceof SecurityDefinitionAdminMessage securityDefinitionAdminMessage) {
          securityDefinitionAdminMessage.setSnapConverterMode(true);
          InstrumentCache.updateSecurityDefinitionAsCacheOnly(securityDefinitionAdminMessage);

        } else if (message instanceof UserAdminMessage userAdminMessage){
          //System.out.println(message.toJSON());
          message.onMatcher();
        }
      }
    }
    return null;
  }
}
