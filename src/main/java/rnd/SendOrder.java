package rnd;

import com.solfini.common.Context;
import com.solfini.sbe.encoder.*;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class SendOrder {
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder = new com.solfini.sbe.encoder.MessageHeaderEncoder();
  private static final NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();

  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;

  private static final AtomicInteger msgSeqNum = new AtomicInteger();
  private static long seqNum = 0;
  private static final byte NORMAL_API = (byte) 2;

  public static void main(String[] args) {
    String clOrdId = String.valueOf(getTime());
    sendOrder(2, clOrdId, 283, "XXX/USD", Side.BUY, OrdType.MARKET, 100, (short) 2, 1, (short) 0, "YOUTUBE", "ABC123");

    System.out.println("Order sent: clOrdId: " + clOrdId);
  }

  private static void sendOrder(int userId, String clOrdId, int securityId, String symbol, Side side, OrdType ordType, long price, short priceScale,
      long qty, short qtyScale, String platform, String accountId) {
    newOrderSingleEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, HEADER_LENGTH, headerEncoder);
    short encodedLength = HEADER_LENGTH;
    // newOrderSingleEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder);
    headerEncoder.sendingTime(System.currentTimeMillis());
    encodedLength += headerEncoder.encodedLength();
    // set
    newOrderSingleEncoder.orderId(0);
    newOrderSingleEncoder.submitterId(userId);
    newOrderSingleEncoder.userId(userId);
    newOrderSingleEncoder.clOrdID(clOrdId);
    newOrderSingleEncoder.securityId(securityId);
    newOrderSingleEncoder.symbol(symbol);
    newOrderSingleEncoder.side(side);
    newOrderSingleEncoder.ordType(ordType);
    newOrderSingleEncoder.price(price);
    newOrderSingleEncoder.priceScale(priceScale);
    newOrderSingleEncoder.price2(0);
    newOrderSingleEncoder.price2Scale((short) 2);
    newOrderSingleEncoder.qty(qty);
    newOrderSingleEncoder.qtyScale(qtyScale);
    newOrderSingleEncoder.stopPx(0);
    newOrderSingleEncoder.stopPxScale((short) 0);
    newOrderSingleEncoder.targetStrategy(0);
    newOrderSingleEncoder.isHidden(BooleanType.FALSE);
    newOrderSingleEncoder.isLiquidation(BooleanType.FALSE);
    newOrderSingleEncoder.isLastLook(BooleanType.FALSE);
    newOrderSingleEncoder.timeInForce(TimeInForce.GOOD_TILL_CANCEL);
    newOrderSingleEncoder.expireTime(0);
    newOrderSingleEncoder.assetId(0);
    newOrderSingleEncoder.tokenId(0);
    newOrderSingleEncoder.groupAssetId(0);
    newOrderSingleEncoder.selectId(0);
    newOrderSingleEncoder.quoteType(QuoteType.NULL_VAL);
    newOrderSingleEncoder.quoteTargetUserId(0);
    newOrderSingleEncoder.platform(platform);
    newOrderSingleEncoder.accountId(accountId);

    encodedLength += (short) newOrderSingleEncoder.encodedLength();
    buffer.limit(encodedLength);
    encoderUnsafeBuffer.putShort(0, encodedLength);

    final byte[] bytesWithKafkaOffset = bufferToArrayBulk(encoderUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    publish("api01", bytesWithKafkaOffset, NORMAL_API);
  }

  protected static void populateHeader(final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder) {
    headerEncoder.msgSeqNum(msgSeqNum.incrementAndGet());
    headerEncoder.sendingTime(System.currentTimeMillis());
    headerEncoder.sourceSeqNum(0); // sourceSeqNum
    headerEncoder.kafkaRecordOffset(0); // kafkaRecordOffset
    headerEncoder.senderCompId(""); // instance id
    headerEncoder.deliverToCompId(0); // matching engine seq num
  }

  public static final byte[] bufferToArrayBulk(final ByteBuffer buffer, final int length, final int offset) {
    final byte[] bytes = new byte[length + offset];
    buffer.position(0);
    buffer.get(bytes, offset, length);
    return bytes;
  }

  private static void publish(String topic, byte[] bytes, byte messageType) {
    seqNum++;
    long tempSeqNum = seqNum;
    for (int i = 7; i >= 0; i--) {
      bytes[i] = (byte) (tempSeqNum & 0xFF);
      tempSeqNum >>= 8;
    }

    // time
    long now = getTime();
    for (int i = 15; i >= 8; i--) {
      bytes[i] = (byte) (now & 0xFF);
      now >>= 8;
    }

    // messageType
    bytes[16] = messageType;

    KafkaProducer<String, byte[]> producer = getProducer(0);
    producer.send(new ProducerRecord<String, byte[]>(topic, bytes));

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("Stopping Producer");
      producer.close();
    }));
  }

  public static long getTime() {
    return System.currentTimeMillis() * 1_000_000 + (System.nanoTime() % 1_000_000);
  }

  public static KafkaProducer<String, byte[]> getProducer(final long startSeqNum) {
    Properties props = new Properties();
    props.put(ProducerConfig.CLIENT_ID_CONFIG, "MSG_PRODUCER");
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

    return new KafkaProducer<>(props);
  }
}
