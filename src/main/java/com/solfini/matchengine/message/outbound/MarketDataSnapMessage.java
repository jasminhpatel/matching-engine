package com.solfini.matchengine.message.outbound;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class MarketDataSnapMessage extends Message {
  private InstrumentPair instrumentPair;
  private SessionInfo sessionInfo;
  private final MarketDataSnapshotFullRefreshEncoder encoder = new MarketDataSnapshotFullRefreshEncoder();
  private final ByteBuffer directBuffer = ByteBuffer.allocateDirect(32768);
  private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private static final int ADMIN_ENCODED_LENGTH_SIZE = 2;

  public MarketDataSnapMessage() {
    // Constructor
  }

  public static MarketDataSnapMessage create(final InstrumentPair instrumentPair, final SessionInfo sessionInfo) {
    final MarketDataSnapMessage marketDataSnapMessage = new MarketDataSnapMessage();
    marketDataSnapMessage.setInstrumentPair(instrumentPair);
    marketDataSnapMessage.setSenderCompId(sessionInfo.getSenderCompId());
    marketDataSnapMessage.build();
    return marketDataSnapMessage;
  }

  // called from a separate MarketDataOutputBuilderThread thread
  public void build() {
    encoder.wrapAndApplyHeader(unsafeBuffer, ADMIN_ENCODED_LENGTH_SIZE, headerEncoder);

    encoder.securityId(instrumentPair.getId());
    encoder.symbol(instrumentPair.getSymbol());
    instrumentPair.getOrderBook().build(encoder);
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public final MessageType getMessageType() {
    return MessageType.MARKET_DATA_SNAP;
  }

  public final String getSymbol() {
    return instrumentPair.getSymbol();
  }

  public final InstrumentPair getInstrumentPair() {
    return instrumentPair;
  }

  public final void setInstrumentPair(final InstrumentPair instrumentPair) {
    this.instrumentPair = instrumentPair;
  }

  public final SessionInfo getSessionInfo() {
    return sessionInfo;
  }

  public final void setSessionInfo(final SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }

  public final MarketDataSnapshotFullRefreshEncoder getEncoder() {
    return encoder;
  }

  public final ByteBuffer getDirectBuffer() {
    return directBuffer;
  }

  public final UnsafeBuffer getUnsafeBuffer() {
    return unsafeBuffer;
  }

  public final MessageHeaderEncoder getHeaderEncoder() {
    return headerEncoder;
  }

  @Override
  public final void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public final void onMatcher() {
    Context.getMatcherToPublisherQueue().addGuaranteed(this);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    return s.append(MARKETDATASNAPMESSAGE_INSTRUMENTPAIR_EQ).append(instrumentPair.getId()).append(KAFKA_OFFSET_EQ)
        .append(kafkaRecordOffset).append(']');
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"MarketDataSnapMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"instrumentPair\":").append(instrumentPair.getId());

    sb.append("}");
    return sb.toString();
  }
}
