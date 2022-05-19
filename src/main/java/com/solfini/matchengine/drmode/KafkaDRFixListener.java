package com.solfini.matchengine.drmode;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.common.TopicPartition;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.TradeStateAdminMessageEncoder;
import com.solfini.matchengine.kafka.KafkaListener;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.pool.DRRecieverDataObjectPool;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class KafkaDRFixListener extends KafkaListener implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaDRFixListener.class);
  private static final String ME_KAFKA_TOPIC_PRIMARY = PropertyReader.getProperty("ME_KAFKA_TOPIC_PRIMARY", "me1");

  public static final String API_ADMIN_KAFKA_TOPIC_IN = PropertyReader.getProperty("API_KAFKA_TOPIC_IN", "api1");
  public static final int KAFKA_OFFSET = 17;

  private final long startOffset;
  private long expectedSeqNum = 0;

  public static final int LOAD_STRATEGY_NONE = 0;
  public static final int LOAD_STRATEGY_WAIT_FOR_SNAP = 1;
  public static final int LOAD_STRATEGY_REPLAY_FROM_OFFSET = 2;

  public KafkaDRFixListener(final int loadStrategy) {
    this(loadStrategy, -1);
  }

  public KafkaDRFixListener(final int loadStrategy, final long startOffset) {
    super(ME_KAFKA_TOPIC_PRIMARY);
    this.startOffset = startOffset;
    Context.setLoadStrategy(loadStrategy);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_2, ">>> KafkaDRFixListener topic=", ME_KAFKA_TOPIC_PRIMARY);
    }
  }

  @Override
  public void start() {
    if (LOAD_STRATEGY_WAIT_FOR_SNAP == Context.getLoadStrategy()) {
      // Seek to the end of the input topic
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "WarmStart: Consuming messages from end of the queue");
      }
      final List<TopicPartition> partitions = Arrays.asList(new TopicPartition(ME_KAFKA_TOPIC_PRIMARY, 0));
      getConsumer().assign(partitions);
      getConsumer().seekToEnd(partitions);

      // request new snapshot
      sendStateAdmin();
      return;
    }

    final TopicPartition partition = new TopicPartition(ME_KAFKA_TOPIC_PRIMARY, 0);
    final List<TopicPartition> partitions = Arrays.asList(partition);
    getConsumer().assign(partitions);

    if ((LOAD_STRATEGY_REPLAY_FROM_OFFSET == Context.getLoadStrategy()) && (-1 != startOffset)) {
      // Seek to the correct offset of the input topic
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_2, "WarmStart: Consuming messages from offset ", startOffset);
      }
      getConsumer().seek(partition, startOffset);
      Context.setLoadStrategy(LOAD_STRATEGY_NONE);
    } else {
      // Seek to the end of the input topic
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_1, "WarmStart: Consuming messages from end of the queue");
      }
      getConsumer().seekToEnd(partitions);
    }
  }

  @Override
  public void onShutdown() {
    DecoderThreadCache.shutdown();
  }

  private void sendStateAdmin() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, "WarmStart: Requesting new snapshot: topic=", API_ADMIN_KAFKA_TOPIC_IN);
    }
    try {
      short encodedLength = 2;
      final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
      final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

      final TradeStateAdminMessageEncoder tradeStateAdminMessageEncoder = new TradeStateAdminMessageEncoder();
      com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
          new com.solfini.internal.admin.schema.MessageHeaderEncoder();
      tradeStateAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
      encodedLength += headerEncoder.encodedLength();

      tradeStateAdminMessageEncoder.marketStatus(MarketStatus.RESTATE);
      tradeStateAdminMessageEncoder.routeToDestination("PRIMARY");

      encodedLength += tradeStateAdminMessageEncoder.encodedLength();
      adminMessageBuffer.limit(encodedLength);
      adminMessageUnsafeBuffer.putShort(0, encodedLength);

      byte[] bytes = StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

      KafkaPublisher publisher = new KafkaPublisher(0);
      publisher.sendDirect(API_ADMIN_KAFKA_TOPIC_IN, bytes, KafkaPublisher.ADMIN_API);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  @Override
  public void onMessage(final long seqNum, final long sendTime, final long recordOffset, final byte messageType, final byte[] data) {

    // Ignore sequence validation when the sequence number is 0.
    if (seqNum != 0) {
      if (expectedSeqNum == 0) {
        expectedSeqNum = seqNum;
      } else if (expectedSeqNum != seqNum) {
        LOGGER.warn(LOG_FMT_4, "Unexpected sequence number: expectedSeqNum=", expectedSeqNum, ", seqNum=", seqNum);
        expectedSeqNum = seqNum;
      }
      expectedSeqNum++;
    }

    if (LOGGER.isDebugEnabled()) {
      final long latency = (TimeUtil.getTime() - sendTime) / 1000; // in microseconds
      LOGGER.debug(LOG_FMT_12, "received: seqNum=", seqNum, ", sendTime=", sendTime, ", recordOffset=", recordOffset, MESSAGETYPE_EQ,
          messageType, ", latency=", latency, ", data=", data);
    }

    // use thread queue
    try {
      final RecieverData recieverData = DRRecieverDataObjectPool.get();
      recieverData.set(seqNum, sendTime, recordOffset, messageType, data);
      DecoderThreadCache.add(recieverData);

    } catch (Exception e) {
      // decode error!!!
      final int length = data.length - KAFKA_OFFSET;
      LOGGER.error(LOG_FMT_6, "KafkaDRFixListener decode error, msgType=", messageType, LENGTH_EQ, (long) length, ", sendTime=", sendTime,
          MSGSEQNUM_EQ, seqNum, SB_EQ, data, e);
    }
  }
}
