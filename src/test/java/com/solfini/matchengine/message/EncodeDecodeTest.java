package com.solfini.matchengine.message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Properties;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.event.Level;
import com.solfini.common.Context;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.drmode.KafkaDRFixListener;
import com.solfini.matchengine.kafka.KafkaInputFixListener;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.sbe.encoder.CancelReplaceOrderEncoder;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.sbe.encoder.NewOrderSingleEncoder;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.util.FixDecoderUtil;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import uk.co.real_logic.artio.EncodingException;
import uk.co.real_logic.artio.fields.DecimalFloat;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

public class EncodeDecodeTest extends ModelTest {

  private KafkaInputFixListener kafkaInputFixListener;
  private KafkaDRFixListener kafkaDRFixListener;
  private final ManyToOneConcurrentArrayQueueCustom<Message> receiverToMatcherQueue;

  private User user = null;
  private InstrumentPair pair = null;

  public EncodeDecodeTest() throws IOException {
    PropertyReader.initialize(null, configure());
    kafkaInputFixListener = new KafkaInputFixListener(false);
    kafkaDRFixListener = new KafkaDRFixListener(KafkaDRFixListener.LOAD_STRATEGY_NONE);
    receiverToMatcherQueue = Context.getReceiverToMatcherQueue();
  }

  private Properties configure() {
    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("QUEUE_CAPACITY", "100");
    properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "1024");
    properties.setProperty("ORDER_POOL_START_CAPACITY", "1024");
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "1024");
    properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "1024");
    properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "1024");
    properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "1024");
    properties.setProperty("POSITION_POOL_START_CAPACITY", "1024");
    properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "1024");
    properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "1024");
    properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "1024");
    properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "1024");
    properties.setProperty("NUM_ENCODER_THREADS", "0");
    properties.setProperty("NUM_DECODER_THREADS", "1");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    properties.setProperty("KAFKA.PRODUCER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.PRODUCER.key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    properties.setProperty("KAFKA.PRODUCER.value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

    return properties;
  }

  @Before
  public void before() {

    while (receiverToMatcherQueue.poll() != null) {
      receiverToMatcherQueue.poll();
    }
    LogLevel.setLevel(Level.TRACE);

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 4, 3));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 2));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");

    pair = InstrumentCache.getPair(BTC_USDT_F);
    user = createUser(38);
    user.addPosition(pair.getId(), 10_000, null);
    expectMessage("userId=38");
    assertMessages();
  }

  @Ignore
  @Test
  public void executionReportPublishDecode() {
    ExecutionReportMessage execReport = new ExecutionReportMessage();
    execReport.setClOrdId("ABC");
    execReport.setSymbol("BTC/USDT[F]");
    execReport.setSide(Side.BUY);
    execReport.setSecurityId(BTC_USDT_F);
    execReport.setOrdType(OrdType.LIMIT);
    execReport.setUser(user);
    execReport.setExecType(ExecType.NEW);
    execReport.setOrdStatus(OrdStatus.NEW);
    execReport.setSenderCompId(Context.getInstanceId());
    execReport.setTimeInForce(TimeInForce.DAY);
    user.copySetPositionArr(execReport);

    execReport.onPublish();
    expectPublishedMessage(MsgType.POSITION_REPORT, KafkaPublisher.NORMAL_API);
    expectPublishedMessage(MsgType.EXECUTION_REPORT, KafkaPublisher.ADMIN_API);


  }

  public static final int KAFKA_OFFSET = 17;
  public static final int HEADER_LENGTH = 2;

  @Test
  public void decodedNewOrderWithoutSecurityId() {
    long msgSeqNum = 1;

    short encodedLength = HEADER_LENGTH;
    ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);

    NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();
    com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder = new com.solfini.sbe.encoder.MessageHeaderEncoder();
    newOrderSingleEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);

    headerEncoder.senderCompId("test");
    headerEncoder.sendingTime(TimeUtil.getTime());
    headerEncoder.msgSeqNum(msgSeqNum++);
    encodedLength += headerEncoder.encodedLength();

    newOrderSingleEncoder.userId(user.getId());
    newOrderSingleEncoder.securityId(14);
    newOrderSingleEncoder.side(Side.BUY);
    newOrderSingleEncoder.ordType(OrdType.LIMIT);
    newOrderSingleEncoder.timeInForce(TimeInForce.GOOD_TILL_CANCEL);
    newOrderSingleEncoder.price(100);
    newOrderSingleEncoder.priceScale((short) 2);
    newOrderSingleEncoder.qty(500);
    newOrderSingleEncoder.qtyScale((short) 2);
    newOrderSingleEncoder.clOrdID("2134124342");
    newOrderSingleEncoder.expireTime(TimeUtil.getTime());

    encodedLength += newOrderSingleEncoder.encodedLength();
    buffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);

    byte[] encodedMsg = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, encodedMsg);

    Message decodedMsg = receiverToMatcherQueue.poll();

    Assert.assertEquals("BUSINESS_REJECT", decodedMsg.getMessageType().toString());
    Assert.assertEquals("INSTRUMENT_NOT_FOUND", ((BusinessRejectMessage) decodedMsg).getBusinessRejectReason().toString());

  }

  @Test
  public void encodedNewOrderWithoutUser() {
    long msgSeqNum = 1;

    short encodedLength = HEADER_LENGTH;
    ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);

    NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();
    com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder = new com.solfini.sbe.encoder.MessageHeaderEncoder();
    newOrderSingleEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);

    headerEncoder.senderCompId("test");
    headerEncoder.sendingTime(TimeUtil.getTime());
    headerEncoder.msgSeqNum(msgSeqNum++);
    encodedLength += headerEncoder.encodedLength();

    // newOrderSingleEncoder.userId(18);
    newOrderSingleEncoder.securityId(BTC_USDT_F);
    newOrderSingleEncoder.side(Side.BUY);
    newOrderSingleEncoder.ordType(OrdType.LIMIT);
    newOrderSingleEncoder.timeInForce(TimeInForce.GOOD_TILL_CANCEL);
    newOrderSingleEncoder.price(100);
    newOrderSingleEncoder.priceScale((short) 2);
    newOrderSingleEncoder.qty(500);
    newOrderSingleEncoder.qtyScale((short) 2);
    newOrderSingleEncoder.clOrdID("2134124342");
    newOrderSingleEncoder.expireTime(TimeUtil.getTime());

    encodedLength += newOrderSingleEncoder.encodedLength();
    buffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);

    byte[] encodedMsg = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);


    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, encodedMsg);

    Message decodedMsg = receiverToMatcherQueue.poll();

    decodedMsg.onMatcher();
    decodedMsg = Context.getMatcherToPublisherQueue().poll();

    Assert.assertEquals("BUSINESS_REJECT", decodedMsg.getMessageType().toString());
    Assert.assertEquals("USER_NOT_FOUND", ((BusinessRejectMessage) decodedMsg).getBusinessRejectReason().toString());

  }


  @Test
  public void encodedCancelReplaceWithoutSecurityId() {
    long msgSeqNum = 1;

    short encodedLength = HEADER_LENGTH;
    ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);

    CancelReplaceOrderEncoder cancelReplaceOrderEncoder = new CancelReplaceOrderEncoder();
    com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder = new com.solfini.sbe.encoder.MessageHeaderEncoder();
    cancelReplaceOrderEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);

    headerEncoder.senderCompId("test");
    headerEncoder.sendingTime(TimeUtil.getTime());
    headerEncoder.msgSeqNum(msgSeqNum++);
    encodedLength += headerEncoder.encodedLength();

    // cancelReplaceRequestEncoder.instrument().securityID(String.valueOf(pair.getId()).toCharArray());
    // cancelReplaceOrderEncoder.securityId(pair.getId());
    cancelReplaceOrderEncoder.clOrdID("4444");
    cancelReplaceOrderEncoder.originalOrderId(23242);
    cancelReplaceOrderEncoder.secondaryOrderId(4444);
    cancelReplaceOrderEncoder.qty2(400);

    cancelReplaceOrderEncoder.side(Side.BUY);
    cancelReplaceOrderEncoder.qty(500);
    cancelReplaceOrderEncoder.qtyScale((short) 2);

    cancelReplaceOrderEncoder.timeInForce(TimeInForce.GOOD_TILL_CANCEL);
    cancelReplaceOrderEncoder.price(100);
    cancelReplaceOrderEncoder.priceScale((short) 2);
    cancelReplaceOrderEncoder.price2(120);
    cancelReplaceOrderEncoder.price2Scale((short) 2);

    byte[] timestamp = StringUtil.getCurrentDateYYYYMMDDHHMMSSsss().getBytes();
    cancelReplaceOrderEncoder.userId(user.getId());
    cancelReplaceOrderEncoder.clOrdID("23242");

    // cancelReplaceOrderEncoder.expireTime(timestamp);

    encodedLength += cancelReplaceOrderEncoder.encodedLength();
    buffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);

    byte[] encodedMsg = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, encodedMsg);

    Message decodedMsg = receiverToMatcherQueue.poll();

    Assert.assertEquals("BUSINESS_REJECT", decodedMsg.getMessageType().toString());
    Assert.assertEquals("SECURITY_ID_IS_MISSING", ((BusinessRejectMessage) decodedMsg).getBusinessRejectReason().toString());
  }

  @Ignore
  @Test
  public void encodedCancelReplaceWithoutPrice2() {
    long msgSeqNum = 1;

    short encodedLength = HEADER_LENGTH;
    ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);

    CancelReplaceOrderEncoder cancelReplaceOrderEncoder = new CancelReplaceOrderEncoder();
    com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder = new com.solfini.sbe.encoder.MessageHeaderEncoder();
    cancelReplaceOrderEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);

    headerEncoder.senderCompId("test");
    headerEncoder.sendingTime(TimeUtil.getTime());
    headerEncoder.msgSeqNum(msgSeqNum++);
    encodedLength += headerEncoder.encodedLength();

    // cancelReplaceRequestEncoder.instrument().securityID(String.valueOf(pair.getId()).toCharArray());
    cancelReplaceOrderEncoder.securityId(pair.getId());
    cancelReplaceOrderEncoder.clOrdID("4444");
    cancelReplaceOrderEncoder.originalOrderId(23242);
    cancelReplaceOrderEncoder.secondaryOrderId(4444);
    cancelReplaceOrderEncoder.qty2(400);

    cancelReplaceOrderEncoder.side(Side.BUY);
    cancelReplaceOrderEncoder.qty(500);
    cancelReplaceOrderEncoder.qtyScale((short) 2);

    cancelReplaceOrderEncoder.timeInForce(TimeInForce.GOOD_TILL_CANCEL);
    cancelReplaceOrderEncoder.price(100);
    cancelReplaceOrderEncoder.priceScale((short) 2);
    cancelReplaceOrderEncoder.price2(0); // cancelReplaceOrderEncoder.price2(120);
    cancelReplaceOrderEncoder.price2Scale((short) 2);

    byte[] timestamp = StringUtil.getCurrentDateYYYYMMDDHHMMSSsss().getBytes();
    cancelReplaceOrderEncoder.userId(user.getId());
    cancelReplaceOrderEncoder.clOrdID("23242");

    // cancelReplaceOrderEncoder.expireTime(timestamp);

    encodedLength += cancelReplaceOrderEncoder.encodedLength();
    buffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);

    byte[] encodedMsg = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, encodedMsg);

    Message decodedMsg = receiverToMatcherQueue.poll();

    Assert.assertEquals("BUSINESS_REJECT", decodedMsg.getMessageType().toString());
    Assert.assertEquals("OTHER", ((BusinessRejectMessage) decodedMsg).getBusinessRejectReason().toString());
  }


  @Test
  public void encodedNewOrderWithoutSymbol() {
    long msgSeqNum = 1;

    short encodedLength = HEADER_LENGTH;
    ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);

    NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();
    com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder = new com.solfini.sbe.encoder.MessageHeaderEncoder();
    newOrderSingleEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);

    headerEncoder.senderCompId("test");
    headerEncoder.sendingTime(TimeUtil.getTime());
    headerEncoder.msgSeqNum(msgSeqNum++);
    encodedLength += headerEncoder.encodedLength();

    newOrderSingleEncoder.userId(user.getId());
    newOrderSingleEncoder.securityId(14);
    newOrderSingleEncoder.side(Side.BUY);
    newOrderSingleEncoder.ordType(OrdType.LIMIT);
    newOrderSingleEncoder.timeInForce(TimeInForce.GOOD_TILL_CANCEL);
    newOrderSingleEncoder.price(100);
    newOrderSingleEncoder.priceScale((short) 2);
    newOrderSingleEncoder.qty(500);
    newOrderSingleEncoder.qtyScale((short) 2);
    newOrderSingleEncoder.clOrdID("2134124342");
    newOrderSingleEncoder.expireTime(TimeUtil.getTime());

    encodedLength += newOrderSingleEncoder.encodedLength();
    buffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);

    byte[] encodedMsg = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, encodedMsg);

    Message decodedMsg = receiverToMatcherQueue.poll();

    Assert.assertEquals("BUSINESS_REJECT", decodedMsg.getMessageType().toString());
    Assert.assertEquals("INSTRUMENT_NOT_FOUND", ((BusinessRejectMessage) decodedMsg).getBusinessRejectReason().toString());

  }

  private Message expectPublishedMessage(MsgType messageType, byte msgCategory) {

    if (messageType == MsgType.EXECUTION_REPORT || messageType == MsgType.POSITION_REPORT) {
      return expectPublishedMessageDR(messageType, msgCategory);
    } else {
      byte[] encoded = Context.getPublisherToKafkaPublisherQueue().poll();
      Assert.assertNotNull(encoded);
      kafkaInputFixListener.onMessage(1, 10, 0, msgCategory, encoded);
      MsgType encodedMsgType = FixDecoderUtil.decodeMsgType(encoded);
      Assert.assertEquals(messageType, encodedMsgType);

      return receiverToMatcherQueue.poll();
    }
  }

  private Message expectPublishedMessageDR(MsgType messageType, byte msgCategory) {
    byte[] encoded = Context.getPublisherToKafkaPublisherQueue().poll();
    Assert.assertNotNull(encoded);
    kafkaDRFixListener.onMessage(1, 10, 0, msgCategory, encoded);
    MsgType encodedMsgType = FixDecoderUtil.decodeMsgType(encoded);
    Assert.assertEquals(messageType, encodedMsgType);
    final ManyToOneConcurrentArrayQueueCustom<Message> queue = Context.getReceiverToMatcherQueue();

    return receiverToMatcherQueue.poll();
  }

}
