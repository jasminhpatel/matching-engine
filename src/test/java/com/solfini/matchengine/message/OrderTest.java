package com.solfini.matchengine.message;

import java.nio.ByteBuffer;
import java.util.Properties;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.event.Level;

import com.solfini.common.Context;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.kafka.KafkaInputFixListener;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.publisher.SessionInfoCache;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.NewOrderSingleDecoder;
import com.solfini.sbe.encoder.NewOrderSingleEncoder;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;

public class OrderTest extends MessageTest {

  private final NewOrderSingleHandler newOrderSingleHandler = new NewOrderSingleHandler();

  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;

  // encoder classes
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder =
      new com.solfini.sbe.encoder.MessageHeaderEncoder();
  private static final NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();

  private static final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private static final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private static final NewOrderSingleDecoder newOrderSingleDecoder = new NewOrderSingleDecoder();

  private static final int ME_SEQ_ID = 1;

  @BeforeClass
  public static void before() {
    LogLevel.setLevel(Level.TRACE);

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "4000000");
      properties.setProperty("ORDER_POOL_START_CAPACITY", "2000000");
      properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
      properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
      properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
      PropertyReader.initialize(null, properties);

      createInstruments();

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
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


  @Test
  public void measureOrderDecoderPerformance() {
    final InstrumentPair instrument = InstrumentCache.getPair(3);
    final User user = createUser(18);
    final Order order = makeOrder(user, instrument);
    final byte[] data = encode(instrument, order);

    KafkaInputFixListener listener = null;
    try {
      listener = new KafkaInputFixListener(false);
      listener.onMessage(0, 0, 0, KafkaPublisher.NORMAL_API, data);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
      return;
    }

    final long start = System.nanoTime();
    for (int i = 0; i < 1_000_000; ++i) {
      listener.onMessage(i + 1, 0, 0, KafkaPublisher.NORMAL_API, data);
    }

    final long elapsed = (System.nanoTime() - start) / 1_000_000;
    System.out.println("Done in " + format(elapsed) + " ms (" + format(1_000_000_000 / elapsed) + " msg/s)");

    final ManyToOneConcurrentArrayQueueCustom<Message> queue = Context.getReceiverToMatcherQueue();
    int count = -1;
    while (count < 1_000_000) {
      ++count;
      Assert.assertFalse(queue.isEmpty());

      Message message = queue.remove();
      Assert.assertNotNull(message);
      Assert.assertTrue(message instanceof Order);
    }

    Assert.assertTrue(queue.isEmpty());
  }

  @Test
  public void measureOrderDecoderResetPerformance() {
    NewOrderSingleDecoder decoder = new NewOrderSingleDecoder();

    final long start = System.nanoTime();
    for (int i = 0; i < 1_000_000; ++i) {
    }

    final long elapsed = (System.nanoTime() - start) / 1_000_000;
    System.out
        .println("Done in " + format(elapsed) + " ms (" + format(1_000_000_000 / elapsed) + " calls/s, " + format(elapsed) + " ns/call)");
  }

  protected User createUser(final int userId, final Balance... balances) {
    UserAdminMessage message = new UserAdminMessage();
    message.setUpdateType(UpdateType.PUT);
    message.setUserId(userId);
    message.setUsername("user_" + userId);
    message.setPassword("pass_" + userId);
    if (null != balances) {
      for (final Balance balance : balances) {
        message.addBalance(balance);
      }
    }

    UserCache.add(message);

    return UserCache.get(userId);
  }


  // Test to prove that the decoder needs to be reset between successive invocations.
  // Otherwise optional fields encountered in the first decoding would still be set when
  // decoding the second message (when the second message does not have the said optional field set).
  @Test
  public void decodeMultipleOrders() {
    final InstrumentPair instrument = InstrumentCache.getPair(3);
    final User user = createUser(18);

    Order order = makeOrder(user, instrument);
    order.setStopPx(50_000, instrument.getPriceScale()); // this is an optional field that would remain in the decoder if not reset

    Order decoded = decode(encode(instrument, order));
    Assert.assertNotNull(decoded);
    Assert.assertEquals(50_000, decoded.getStopPx());
    Assert.assertEquals(instrument.getPriceScale(), decoded.getStopPxScale());

    order = makeOrder(user, instrument);
    decoded = decode(encode(instrument, order));
    Assert.assertNotNull(decoded);
    Assert.assertEquals(0, decoded.getStopPx()); // this should not be set to a non zero value
    Assert.assertEquals(0, decoded.getStopPxScale());
  }

  private byte[] encode(final InstrumentPair instrument, final Order order) {
    short encodedLength = HEADER_LENGTH;
    newOrderSingleEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, order);
    headerEncoder.sendingTime(System.currentTimeMillis());
    encodedLength += headerEncoder.encodedLength();

    // set
    newOrderSingleEncoder.clOrdID(order.getClOrdId());
    newOrderSingleEncoder.securityId(order.getSecurityId());
    //newOrderSingleEncoder.symbol("ABCPQR");
    newOrderSingleEncoder.side(order.getSide());
    newOrderSingleEncoder.ordType(order.getOrdType());
    newOrderSingleEncoder.price(order.getPrice());
    newOrderSingleEncoder.priceScale(order.getPriceScale());
    newOrderSingleEncoder.qty(order.getQty());
    newOrderSingleEncoder.qtyScale(order.getQtyScale());
    newOrderSingleEncoder.stopPx((int) order.getStopPx());
    newOrderSingleEncoder.stopPxScale(order.getStopPxScale());
    newOrderSingleEncoder.userId(order.getUser().getId());
    // newOrderSingleEncoder.transactTime(timestamp);

    encodedLength += newOrderSingleEncoder.encodedLength();
    buffer.limit(encodedLength);
    encoderUnsafeBuffer.putShort(0, encodedLength);

    // System.out.println("ENCODE");
    // System.out.println("Header:\n" + headerEncoder);
    // System.out.println("Encoder:\n" + executionReportEncoder);

    byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(encoderUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    return bytesWithKafkaOffset;
  }

  private Order decode(final byte[] data) {
    decoderUnsafeBuffer.wrap(data);
    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);
    newOrderSingleDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
        headerDecoder.version());

    // System.out.println("DECODE");
    // System.out.println("Header:\n" + headerDecoder);
    // System.out.println("Decoder:\n" + executionReportDecoder);
    // System.out.println("decoder.clientOrderId():\n" + executionReportDecoder.clOrdID());

    try {
      Message message = newOrderSingleHandler.decodeNewOrderSingle(headerDecoder, newOrderSingleDecoder);
      Assert.assertTrue(message.toString(), message instanceof Order);
      return (Order) message;
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    return null;
  }

  public static void main(String[] args) {
    before();
    OrderTest test = new OrderTest();
    test.measureOrderDecoderPerformance();
  }
}
