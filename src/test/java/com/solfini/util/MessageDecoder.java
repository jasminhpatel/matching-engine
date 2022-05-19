package com.solfini.util;

import java.nio.ByteBuffer;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.AdminAcknowledgementDecoder;
import com.solfini.internal.admin.schema.BalanceAdminMessageDecoder;
import com.solfini.internal.admin.schema.FIXUserAdminMessageDecoder;
import com.solfini.internal.admin.schema.FeeAdminMessageDecoder;
import com.solfini.internal.admin.schema.FundingRateCalcAdminMessageDecoder;
import com.solfini.internal.admin.schema.SecurityDefinitionAdminMessageDecoder;
import com.solfini.internal.admin.schema.SnapResponseAdminMessageDecoder;
import com.solfini.internal.admin.schema.TradeStateAdminMessageDecoder;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.admin.schema.UserAdminMessageDecoder;
import com.solfini.matchengine.decoder.InboundAdminMessageHandler;
import com.solfini.matchengine.drmode.ExecutionReportParser;
import com.solfini.matchengine.drmode.NetworkStatusResponseParser;
import com.solfini.matchengine.drmode.PositionReportParser;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.message.outbound.MarketDataSnapMessage;
import com.solfini.matchengine.message.session.HeartbeatMessage;
import com.solfini.matchengine.message.session.LogonMessage;
import com.solfini.matchengine.message.session.LogoutMessage;
import com.solfini.matchengine.message.session.ResendRequestMessage;
import com.solfini.matchengine.message.session.SequenceResetMessage;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.BusinessRejectDecoder;
import com.solfini.sbe.encoder.ExecutionReportDecoder;
import com.solfini.sbe.encoder.HeartbeatDecoder;
import com.solfini.sbe.encoder.LogonDecoder;
import com.solfini.sbe.encoder.LogoutDecoder;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.NetworkStatusDecoder;
import com.solfini.sbe.encoder.PositionReportDecoder;
import com.solfini.sbe.encoder.ResendRequestDecoder;
import com.solfini.sbe.encoder.SequenceResetDecoder;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

public class MessageDecoder {
  public static final int NORMAL_API_OFFSET = 17;
  public static final int ADMIN_API_OFFSET = 19;
  private static final int OFFSET = 19;

  private final ExecutionReportDecoder executionReportDecoder = new ExecutionReportDecoder();
  private final PositionReportDecoder positionReportDecoder = new PositionReportDecoder();
  private final NetworkStatusDecoder networkStatusDecoder = new NetworkStatusDecoder();

  private final NetworkStatusResponseParser networkStatusResponseParser = new NetworkStatusResponseParser();
  private final ExecutionReportParser executionReportParser = new ExecutionReportParser();
  private final PositionReportParser positionReportParser = new PositionReportParser();

  public MessageDecoder() {}

  // Decodes the provided buffer into a message.
  public Message decode(final byte[] data, final long offset, final List<Message> baseMessages) {
    Message message = null;
    byte messageType = data[16];
    if (KafkaPublisher.NORMAL_API == messageType) {
      message = decodeMessage(data, baseMessages);
    } else if (KafkaPublisher.ADMIN_API == messageType) {
      message = decodeAdminMessage(data);
    } else {
      throw new UnsupportedOperationException("Unsupported message type: messageType=" + messageType);
    }

    if (null == message) {
      throw new RuntimeException("Failed to decode message: messageType=" + messageType);
    }

    long seqNum = 0;
    for (int i = 0; i < 8; i++) {
      seqNum <<= 8;
      seqNum |= (data[i] & 0xFF);
    }

    long sendTime = 0;
    for (int i = 8; i < 16; i++) {
      sendTime <<= 8;
      sendTime |= (data[i] & 0xFF);
    }

    message.setSourceSeqNum(seqNum);
    message.setSourceSendTime(sendTime);
    message.setKafkaRecordOffset(offset);

    return message;
  }

  public Message decodeMessage(byte[] data, final List<Message> baseMessages) {
    final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    // wrap bytes
    decoderUnsafeBuffer.wrap(data);
    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);

    Message message = null;

    switch (headerDecoder.templateId()) {
      case ExecutionReportDecoder.TEMPLATE_ID:
        executionReportDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());

        message = executionReportParser.parse(headerDecoder, executionReportDecoder);
        if (message != null) {
          message.setSenderCompId(headerDecoder.senderCompId());
          message.setSequenceNumber(headerDecoder.msgSeqNum());
          message.setSourceSeqNum(headerDecoder.sourceSeqNum());
          message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());

          if (baseMessages != null) {
            baseMessages.add(new ExecutionReportMessage() {
              {
                setOrderId(executionReportDecoder.orderId());
                setAvgPx(executionReportDecoder.avgPx());
              }
            });
          }
        }
        break;

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
        break;

      case NetworkStatusDecoder.TEMPLATE_ID:
        // new snap available
        networkStatusDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());

        message = networkStatusResponseParser.parse(headerDecoder, networkStatusDecoder);
        if (message != null) {
          message.setSequenceNumber(headerDecoder.msgSeqNum());
          message.setSourceSeqNum(headerDecoder.sourceSeqNum());
          message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
        }
        break;

      case LogonDecoder.TEMPLATE_ID:
        final LogonDecoder logonDecoder = new LogonDecoder();
        logonDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());

        LogonMessage logonMessage = new LogonMessage();
        // TODO parse the decoded values to message
        message = logonMessage;
        break;

      case LogoutDecoder.TEMPLATE_ID:
        final LogoutDecoder logoutDecoder = new LogoutDecoder();
        logoutDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());
        // TODO parse the decoded values to message
        LogoutMessage logoutMessage = new LogoutMessage();
        message = logoutMessage;
        break;

      // case HEARTBEAT:
      case BusinessRejectDecoder.TEMPLATE_ID:
        final BusinessRejectDecoder businessMessageRejectDecoder = new BusinessRejectDecoder();
        businessMessageRejectDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());

        // TODO parse the decoded values to message
        BusinessRejectMessage businessRejectMessage = new BusinessRejectMessage();
        businessRejectMessage.setBusinessRejectReason(businessMessageRejectDecoder.businessRejectReason());
        businessRejectMessage.setText(businessMessageRejectDecoder.text());
        businessRejectMessage.setBusinessRejectRefID(String.valueOf(businessMessageRejectDecoder.businessRejectRefId()));
        businessRejectMessage.setOrderId(businessMessageRejectDecoder.orderId());
        message = businessRejectMessage;
        break;

      // case ORDER_CANCEL_REJECT:
      case MarketDataSnapshotFullRefreshDecoder.TEMPLATE_ID:
        final MarketDataSnapshotFullRefreshDecoder mdSnapshotFullRefreshDecoder = new MarketDataSnapshotFullRefreshDecoder();
        mdSnapshotFullRefreshDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());

        MarketDataSnapMessage marketDataSnapMessage = new MarketDataSnapMessage();
        marketDataSnapMessage.setInstrumentPair(InstrumentCache.getPair(mdSnapshotFullRefreshDecoder.securityId()));
        message = marketDataSnapMessage;
        // System.out.println("MARKET_DATA_SNAPSHOT_FULL_REFRESH " +
        // mdSnapshotFullRefreshDecoder);
        break;

      case ResendRequestDecoder.TEMPLATE_ID:
        final ResendRequestDecoder resendRequestDecoder = new ResendRequestDecoder();
        resendRequestDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());

        final ResendRequestMessage resendRequest = new ResendRequestMessage();
        resendRequest.setBeginSeqNo(resendRequestDecoder.beginSequenceNo());
        resendRequest.setEndSeqNo(resendRequestDecoder.endSequenceNo());
        resendRequest.setOriginated(true); // For test purposes
        message = resendRequest;
        break;

      case HeartbeatDecoder.TEMPLATE_ID:
        final HeartbeatDecoder heartbeatDecoder = new HeartbeatDecoder();
        heartbeatDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());

        final HeartbeatMessage heartbeatMessage = new HeartbeatMessage();
        message = heartbeatMessage;
        break;

      case SequenceResetDecoder.TEMPLATE_ID:
        final SequenceResetDecoder sequenceResetDecoder = new SequenceResetDecoder();
        sequenceResetDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());

        final SequenceResetMessage sequenceResetMessage = new SequenceResetMessage();
        sequenceResetMessage.setNewSeqNo(sequenceResetDecoder.newSequenceNo());
        sequenceResetMessage.setGapFillFlag(sequenceResetDecoder.gapFillFlag() == BooleanType.TRUE);
        message = sequenceResetMessage;
        break;

      default:
        throw new UnsupportedOperationException("Unsupported message: messageType=" + headerDecoder.templateId());
    }

    if (message != null) {
      message.setSenderCompId(headerDecoder.senderCompId());
      message.setSequenceNumber(headerDecoder.msgSeqNum());
    }

    return message;
  }

  public Message decodeAdminMessage(byte[] data) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(16384);
    MutableAsciiBuffer buffer = new MutableAsciiBuffer(byteBuffer);

    int length = data.length - ADMIN_API_OFFSET;
    buffer.wrap(data, ADMIN_API_OFFSET, length);

    final com.solfini.internal.admin.schema.MessageHeaderDecoder headerDecoder =
        new com.solfini.internal.admin.schema.MessageHeaderDecoder();
    headerDecoder.wrap(buffer, 0);

    int connectionId = 0;
    UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);
    InboundAdminMessageHandler handler = new InboundAdminMessageHandler();
    Message message = null;

    switch (headerDecoder.templateId()) {
      case AdminAcknowledgementDecoder.TEMPLATE_ID:
        message = handler.decodeAdminAck(unsafeBuffer, headerDecoder, connectionId);
        break;
      case UserAdminMessageDecoder.TEMPLATE_ID:
        message = handler.decodeAdminUserUpdate(unsafeBuffer, headerDecoder, connectionId);
        break;
      case BalanceAdminMessageDecoder.TEMPLATE_ID:
        message = handler.decodeBalanceAdminUserUpdate(unsafeBuffer, headerDecoder, connectionId);
        break;
      case FeeAdminMessageDecoder.TEMPLATE_ID:
        message = handler.decodeFeeAdminUpdate(unsafeBuffer, headerDecoder, connectionId);
        if (message != null) {
          ((FeeAdminMessage) message).setUpdateType(UpdateType.PUT);
        }
        break;
      case SecurityDefinitionAdminMessageDecoder.TEMPLATE_ID:
        message = handler.decodeSecurityDefinitionAdminUpdate(unsafeBuffer, headerDecoder, connectionId);
        if (message != null) {
          ((SecurityDefinitionAdminMessage) message).setUpdateType(UpdateType.PUT);
        }
        break;
      case TradeStateAdminMessageDecoder.TEMPLATE_ID:
        message = handler.decodeTradeStateAdminUpdate(unsafeBuffer, headerDecoder, connectionId);
        break;
      case FIXUserAdminMessageDecoder.TEMPLATE_ID:
        message = handler.decodeFIXUserAdminUpdate(unsafeBuffer, headerDecoder, connectionId);
        break;
      case FundingRateCalcAdminMessageDecoder.TEMPLATE_ID:
        message = handler.decodeFundingRateCalcAdminUpdate(unsafeBuffer, headerDecoder, connectionId);
        break;
      case SnapResponseAdminMessageDecoder.TEMPLATE_ID:
        message = handler.decodeSnapResponseAdminMessage(unsafeBuffer, headerDecoder, connectionId);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported admin message: templateId=" + headerDecoder.templateId());
    }

    if (message != null) {
      final long transactionId = headerDecoder.transactionId();
      final boolean transactionEnd = (headerDecoder.transactionEnd() == 1) ? true : false;

      message.setTransactionId(transactionId);
      message.setLastMessageInTransaction(transactionEnd);
    }

    return message;
  }

}
