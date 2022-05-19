package com.solfini.util.controller;

import java.nio.ByteBuffer;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.MessageHeaderEncoder;
import com.solfini.internal.admin.schema.TradeStateAdminMessageEncoder;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import org.agrona.concurrent.UnsafeBuffer;

public class KafkaInputPublisher extends KafkaPublisher {

  public static final int KAFKA_OFFSET = 17;
  private final String topic;

  public KafkaInputPublisher() {
    super(0);
    this.topic = PropertyReader.getProperty("API_KAFKA_TOPIC_IN", "api1");
  }

  public void requestSnapshot(final String instance) {
    short encodedLength = 2;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final TradeStateAdminMessageEncoder tradeStateAdminMessageEncoder = new TradeStateAdminMessageEncoder();
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    tradeStateAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    tradeStateAdminMessageEncoder.routeToDestination(instance);
    tradeStateAdminMessageEncoder.marketStatus(MarketStatus.RESTATE);

    encodedLength += tradeStateAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    send(KafkaPublisher.ADMIN_API, adminMessageUnsafeBuffer.byteBuffer(), encodedLength);
  }

  private void send(final byte messageType, final ByteBuffer buffer, final int length) {
    final byte[] bytes = StringUtil.bufferToArrayBulk(buffer, length, KAFKA_OFFSET);
    sendDirect(topic, bytes, KafkaPublisher.ADMIN_API);
    getProducer().flush();
  }
}
