package com.solfini.performance;

import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.NewOrderSingleEncoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.QuoteType;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;


public class KafkaOrderProducer {

  // --- Wire protocol constants (must match KafkaInputFixListener) ---
  private static final int KAFKA_OFFSET = 17;  // seqNum(8) + sendTime(8) + msgType(1)
  private static final short HEADER_LENGTH = 2; // leading 2-byte total-length field
  private static final int OFFSET = KAFKA_OFFSET + HEADER_LENGTH; // 19
  private static final byte NORMAL_API = (byte) 2;

  // Security ID 3 = BTC/USD pair (matches ArrayOrderBookTest)
  private static final int SECURITY_ID = 52;
  private static final int USER_ID = 2;

  private static final AtomicInteger msgSeqNum = new AtomicInteger(0);
  private static long seqNum = 0;

  // Per-call buffers keep encoding simple and avoid stale variable-length field state
  private static final int ENCODE_BUFFER_SIZE = 4096;

  public static void main(String[] args) {
    int orderCount = 1;
    if (args.length >= 1) {
      orderCount = Integer.parseInt(args[0]);
    }

    // ---------- Hardcoded Kafka producer configuration ----------
    Properties kafkaProps = new Properties();
    //kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.20.0.50:9092");
    kafkaProps.put(ProducerConfig.CLIENT_ID_CONFIG, "perf-order-producer");
    kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    // Tune for throughput: large batch, short linger, snappy compression
    kafkaProps.put(ProducerConfig.BATCH_SIZE_CONFIG, "262144");         // 256 KB
    kafkaProps.put(ProducerConfig.LINGER_MS_CONFIG, "5");               // wait up to 5 ms to fill batch
    kafkaProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "134217728");   // 128 MB send buffer
    kafkaProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
    kafkaProps.put(ProducerConfig.ACKS_CONFIG, "1");                    // leader ack only — maximise throughput
    kafkaProps.put(ProducerConfig.RETRIES_CONFIG, "0");
    kafkaProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
    // -----------------------------------------------

    final String inputTopic = "api01"; // matches API_KAFKA_TOPIC_IN default

    System.out.println("Kafka order producer starting");
    System.out.printf("  bootstrap : %s%n", kafkaProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    System.out.printf("  topic     : %s%n", inputTopic);
    System.out.printf("  orders    : %s%n", format(orderCount));
    System.out.println();

    final KafkaProducer<String, byte[]> producer = new KafkaProducer<>(kafkaProps);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("Flushing and closing producer ...");
      producer.flush();
      producer.close();
    }));

    final Random random = new Random();
    int clOrdIdCounter = 0;

    System.out.println("Sending orders ...");
    final long start = System.nanoTime();

    for (int i = 0; i < orderCount; i++) {
      // Mirror ArrayOrderBookTest.makeOrders() exactly
      final int orderType = random.nextInt(2); // 0 = BUY_LIMIT, 1 = SELL_LIMIT
      final Side side = orderType == 0 ? Side.BUY : Side.SELL;
      final long quantity = 1 + random.nextInt(100_000);

      final long price;
      if (orderType == 0) { // BUY_LIMIT
        price = 1 + random.nextInt(1_050_000);
      } else {              // SELL_LIMIT
        price = 1_000_000 + random.nextInt(2_000_000);
      }

      final String clOrdId = "ClOrdId" + (++clOrdIdCounter);

      final byte[] bytes = encodeOrder(USER_ID, clOrdId, SECURITY_ID, side, price, quantity);
      setKafkaHeader(bytes, NORMAL_API);
      producer.send(new ProducerRecord<>(inputTopic, bytes));

      if ((i + 1) % 100_000 == 0) {
        System.out.println("  ... sent " + format(i + 1));
      }
    }

    producer.flush();
    final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    System.out.println();
    System.out.println("Done.");
    System.out.printf("  Orders sent : %s%n", format(orderCount));
    System.out.printf("  Elapsed     : %s ms%n", format(elapsedMs));
    System.out.printf("  Throughput  : %s orders/s%n", format((1_000L * orderCount) / elapsedMs));
  }

  /**
   * Encodes a single limit order using SBE into a byte array ready for Kafka
   * (17-byte prefix left blank for {@link #setKafkaHeader}).
   */
  private static byte[] encodeOrder(final int userId, final String clOrdId, final int securityId,
      final Side side, final long price, final long qty) {

    final ByteBuffer buffer = ByteBuffer.allocateDirect(ENCODE_BUFFER_SIZE);
    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final NewOrderSingleEncoder orderEncoder = new NewOrderSingleEncoder();

    short encodedLength = HEADER_LENGTH;

    orderEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    headerEncoder.senderCompId("perf");
    headerEncoder.sendingTime(getTime());
    headerEncoder.msgSeqNum(msgSeqNum.incrementAndGet());
    headerEncoder.sourceSeqNum(0);
    headerEncoder.kafkaRecordOffset(0);
    headerEncoder.deliverToCompId(0);
    encodedLength += headerEncoder.encodedLength();

    orderEncoder.orderId(0);
    orderEncoder.submitterId(userId);
    orderEncoder.userId(userId);
    orderEncoder.securityId(securityId);
    orderEncoder.side(side);
    orderEncoder.ordType(OrdType.LIMIT);
    orderEncoder.price(price);
    orderEncoder.priceScale((short) 2);
    orderEncoder.price2(0);
    orderEncoder.price2Scale((short) 2);
    orderEncoder.qty(qty);
    orderEncoder.qtyScale((short) 2);
    orderEncoder.stopPx(0);
    orderEncoder.stopPxScale((short) 0);
    orderEncoder.targetStrategy(0);
    orderEncoder.isHidden(BooleanType.FALSE);
    orderEncoder.isLiquidation(BooleanType.FALSE);
    orderEncoder.isLastLook(BooleanType.FALSE);
    orderEncoder.timeInForce(TimeInForce.GOOD_TILL_CANCEL);
    orderEncoder.expireTime(0);
    orderEncoder.assetId(0);
    orderEncoder.tokenId(0);
    orderEncoder.groupAssetId(0);
    orderEncoder.selectId(0);
    orderEncoder.quoteType(QuoteType.NULL_VAL);
    orderEncoder.quoteTargetUserId(0);
    // variable-length fields last, in schema order
    orderEncoder.clOrdID(clOrdId);
    orderEncoder.symbol("BTC/USD");
    orderEncoder.platform("perf");
    orderEncoder.accountId(String.valueOf(userId));

    encodedLength += (short) orderEncoder.encodedLength();
    buffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);

    // Copy into byte array with KAFKA_OFFSET prefix (filled by setKafkaHeader)
    final byte[] bytes = new byte[encodedLength + KAFKA_OFFSET];
    buffer.position(0);
    buffer.get(bytes, KAFKA_OFFSET, encodedLength);
    return bytes;
  }

  /**
   * Writes seqNum (bytes 0–7), send timestamp (bytes 8–15), and message type (byte 16)
   * into the 17-byte Kafka header prefix. Mirrors KafkaPublisher.sendDirect().
   */
  private static void setKafkaHeader(final byte[] bytes, final byte messageType) {
    seqNum++;
    long tempSeqNum = seqNum;
    for (int i = 7; i >= 0; i--) {
      bytes[i] = (byte) (tempSeqNum & 0xFF);
      tempSeqNum >>= 8;
    }

    long now = getTime();
    for (int i = 15; i >= 8; i--) {
      bytes[i] = (byte) (now & 0xFF);
      now >>= 8;
    }

    bytes[16] = messageType;
  }

  private static long getTime() {
    return System.currentTimeMillis() * 1_000_000L + (System.nanoTime() % 1_000_000);
  }

  private static String format(final long value) {
    return NumberFormat.getInstance().format(value);
  }
}