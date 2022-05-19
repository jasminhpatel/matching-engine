package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import java.util.Properties;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.integration.Log;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.kafka.TestKafkaPublisher;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.sbe.encoder.NewOrderSingleEncoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;

public class OrderEncoderTest {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(OrderEncoderTest.class);

  private final int userId = 10;
  private final int pairId = 14;


  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;

  private static final short ENCODED_LENGTH_SIZE = 2;
  private final String topic = "test";
  private static final int ME_SEQ_ID = 1;

  // encoder classes
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder =
      new com.solfini.sbe.encoder.MessageHeaderEncoder();
  private static final NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();


  @Before
  public void before() {
    LogLevel.setLevel(Level.TRACE);

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      PropertyReader.initialize(null, properties);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    Context.setKafkaPublisher(new TestKafkaPublisher(1));
  }

  private void populateHeader(final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder, final Message message) {
    final SessionInfo sessionInfo = SessionInfoCache.DEFAULT;

    headerEncoder.msgSeqNum(sessionInfo.incrementAndGetMessageSequenceNumber());
    headerEncoder.sendingTime(System.currentTimeMillis());
    headerEncoder.sourceSeqNum(message.getSourceSeqNum()); // sourceSeqNum
    headerEncoder.kafkaRecordOffset(message.getKafkaRecordOffset()); // kafkaRecordOffset
    headerEncoder.senderCompId(Context.getInstanceId()); // instance id
    headerEncoder.deliverToCompId(ME_SEQ_ID); // matching engine seq num
  }

  // private static final ByteBuffer messageBuffer = ByteBuffer.allocateDirect(4096);


  public void send(final Order message) throws Exception {
    short encodedLength = HEADER_LENGTH;
    newOrderSingleEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, message);
    headerEncoder.sendingTime(System.currentTimeMillis());
    encodedLength += headerEncoder.encodedLength();

    // set
    newOrderSingleEncoder.clOrdID(message.getClOrdId());
    newOrderSingleEncoder.securityId(message.getSecurityId());
    //newOrderSingleEncoder.symbol("ABCPQR");
    newOrderSingleEncoder.side(message.getSide());
    newOrderSingleEncoder.ordType(message.getOrdType());
    newOrderSingleEncoder.price(message.getPrice());
    newOrderSingleEncoder.priceScale(message.getPriceScale());
    newOrderSingleEncoder.qty(message.getQty());
    newOrderSingleEncoder.qtyScale(message.getQtyScale());
    newOrderSingleEncoder.userId(message.getUser().getId());
    // newOrderSingleEncoder.transactTime(timestamp);

    encodedLength += newOrderSingleEncoder.encodedLength();
    buffer.limit(encodedLength);
    encoderUnsafeBuffer.putShort(0, encodedLength);

    // System.out.println("ENCODE");
    // System.out.println("Header:\n" + headerEncoder);
    // System.out.println("Encoder:\n" + executionReportEncoder);

    byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(encoderUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publish(message, bytesWithKafkaOffset, KafkaPublisher.NORMAL_API);

    // Assert.fail(); // TODO: update test

    // byte[] bytes = StringUtil.bufferToArray(mutableAsciiBuffer.byteBuffer(), length, KAFKA_OFFSET);
    // Context.getKafkaPublisher().send(API_KAFKA_TOPIC, bytes, KafkaPublisher.NORMAL_API);

  }

  private void publish(final Message message, final byte[] bytes, final byte messageType) throws Exception {
    Log.debug("TX (" + topic + "): " + message);
    // Context.getKafkaPublisher().sendDirect(topic, bytes, messageType);
    Context.getKafkaPublisher().enqueueToSend(topic, bytes, messageType);
  }

  private final Order order = new Order();
  private final User user = new User(userId);

  @Test
  public void testSendOrder() throws Exception {
    order.setUser(user);
    order.setAccount(userId);
    order.setSecurityId(pairId);
    order.setSide(Side.BUY);
    order.setOrdType(OrdType.LIMIT);
    order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
    order.setPrice(1022, (short) 2);
    order.setQty(5000, (short) 2);


    send(order);

    // Assert.fail(); // TODO:Update test
  }

  @Test
  public void testSendOrderPerformance() throws Exception {
    long t0 = System.currentTimeMillis();

    for (int i = 0; i < 100_000; i++) {
      testSendOrder();
    }

    System.out.println("t0=" + (System.currentTimeMillis() - t0));


    t0 = System.currentTimeMillis();

    for (int i = 0; i < 1_000_000; i++) {
      testSendOrder();
    }

    System.out.println("1_000_000 NewOrderSingle t0=" + (System.currentTimeMillis() - t0));
  }
}
