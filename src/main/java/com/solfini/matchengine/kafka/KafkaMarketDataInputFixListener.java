package com.solfini.matchengine.kafka;

import java.io.IOException;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.matchengine.decoder.MarketDataHandler;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class KafkaMarketDataInputFixListener extends KafkaListener {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaMarketDataInputFixListener.class);

  private static final String MD_KAFKA_TOPIC_IN = PropertyReader.getProperty("MD_KAFKA_TOPIC_IN", "md1");
  private static final long LATENCY_MILLIS_THRESHOLD = 20_000; // 20 seconds

  private final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final MarketDataSnapshotFullRefreshDecoder marketDataSnapshotFullRefreshDecoder = new MarketDataSnapshotFullRefreshDecoder();
  private final MarketDataHandler marketDataHandler = new MarketDataHandler();

  private long lastSequenceNumber = 0;
  private long lastIpcIndex = 0;

  // process bytes with 17 offset+2
  private static final int OFFSET = 19;

  public KafkaMarketDataInputFixListener() throws IOException {
    super(MD_KAFKA_TOPIC_IN);


    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, ">>> KafkaMarketDataInputFixListener topic=", MD_KAFKA_TOPIC_IN, ", loaded lastSequenceNumber=",
          lastSequenceNumber, ", lastIpcIndex=", lastIpcIndex);
    }
  }

  @Override
  public void onMessage(final long seqNum, final long sendTime, final long recordOffset, final byte messageType, final byte[] data) {
    long latency = (TimeUtil.getTime() - sendTime) / 1000; // in microseconds
    String fixString = StringUtil.fixToString(data);
    int length = data.length - OFFSET;

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, "received: seqNum=", seqNum, ", sendTime=", sendTime, ", recordOffset=", recordOffset, MESSAGETYPE_EQ,
          messageType, ", latency=", latency, ", data=", fixString);
    }

    if ((latency > LATENCY_MILLIS_THRESHOLD) && LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, "OLD market data received: seqNum=", seqNum, ", sendTime=", sendTime, ", recordOffset=", recordOffset,
          MESSAGETYPE_EQ, messageType, ", latency=", latency, ", data=", fixString);
    }

    try {
      // wrap bytes
      decoderUnsafeBuffer.wrap(data);
      headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_14, ">>> handleMessage msgSeqNum=", seqNum, SENDERCOMPID_EQ, headerDecoder.senderCompId(), TARGETCOMPID_EQ,
            headerDecoder.senderCompId(), MSGTYPE_EQ, messageType, LENGTH_EQ, length, MESSAGE_EQ, fixString, HEADERDECODER_EQ,
            headerDecoder.toString());
      }

      try {
        if (MarketDataSnapshotFullRefreshDecoder.TEMPLATE_ID == headerDecoder.templateId()) {
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(MARKET_DATA_RECEIVED);
          }
          marketDataSnapshotFullRefreshDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(),
              headerDecoder.blockLength(), headerDecoder.version());
          Message message = marketDataHandler.decodeMarketData(headerDecoder, marketDataSnapshotFullRefreshDecoder);

          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LOG_FMT_10, "decoded message=", message, " origMessage=", fixString, SENDERCOMPID_EQ, headerDecoder.senderCompId(),
                ", msgType=", messageType, TARGETCOMPID_EQ, headerDecoder.senderCompId());
          }

        }
      } catch (Exception e) {
        // decode error!!!
        LOGGER.error(LOG_FMT_11, "KafkaMarketDataInputFixListener decode error, msgType=", messageType, LENGTH_EQ, length, SENDERCOMPID_EQ,
            headerDecoder.senderCompId(), MSGSEQNUM_EQ, seqNum, SB_EQ, fixString, e);
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
}
