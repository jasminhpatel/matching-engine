package com.solfini.dbpersister;

import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.matchengine.drmode.PositionReportParser;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.PositionReportDecoder;
import com.solfini.util.LogLevel;
import org.agrona.concurrent.UnsafeBuffer;

import static com.solfini.common.Constants.LOG_FMT_2;

public class MessageDecoder {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MessageDecoder.class);
  private static final int OFFSET = 19;
  private final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final PositionReportDecoder positionReportDecoder = new PositionReportDecoder();
  private final PositionReportParser positionReportParser = new PositionReportParser();

  public Message onMessage(final long seqNum, final long sendTime, final long recordOffset, final byte messageType, final byte[] data) {
    //final long latency = (TimeUtil.getTime() - sendTime) / 1000; // in microseconds
    final int length = data.length - OFFSET;

    try {
      Message message = decode(data, length);;
      if (message != null) {
        message.setSourceSeqNum(seqNum);
        message.setSourceSendTime(sendTime);
        message.setKafkaRecordOffset(recordOffset);
      }
      return message;

    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error("Kafka listener error ", e);
      if (e.getMessage().contains("Queue")) {
        LOGGER.error("Kafka listener Queue error sleeping");
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e1) {
          e1.printStackTrace();
        }
      }
    }
    return null;
  }

  private Message decode(final byte[] data, final int length) {
    decoderUnsafeBuffer.wrap(data);
    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);
    LOGGER.info("MESSAGE_TEMPLATE: " + headerDecoder.templateId());
    try {
      switch (headerDecoder.templateId()) {
        case PositionReportDecoder.TEMPLATE_ID:
          positionReportDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
              headerDecoder.version());

          Message message = positionReportParser.parse(headerDecoder, positionReportDecoder);
          if (message != null) {
            message.setSenderCompId(headerDecoder.senderCompId());
            message.setSequenceNumber(headerDecoder.msgSeqNum());
            message.setSourceSeqNum(headerDecoder.sourceSeqNum());
            message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());

            return PositionReportMessage.createFromDecoder(headerDecoder, positionReportDecoder, (BalanceAdminMessage) message);

          }
          return null;

        default:
          if (LogLevel.warn()) {
            LOGGER.warn(LOG_FMT_2, "Unable to decode message: data=", data);
          }
          return null;
      }
    } catch (Exception e) {
      LOGGER.error("Error in decode", e);
      throw e;
    }
  }
}
