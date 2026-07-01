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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public class KafkaOrderProducer {

  // --- Wire protocol constants (must match KafkaInputFixListener) ---
  private static final int KAFKA_OFFSET = 17;  // seqNum(8) + sendTime(8) + msgType(1)
  private static final short HEADER_LENGTH = 2; // leading 2-byte total-length field
  private static final int OFFSET = KAFKA_OFFSET + HEADER_LENGTH; // 19
  private static final byte NORMAL_API = (byte) 2;

  private static final int SECURITY_ID = 52;
  private static int[] USER_IDS = null;
  private static int NO_OF_USERS = 0;

  private static final AtomicInteger msgSeqNum = new AtomicInteger(0);
  private static final AtomicLong seqNum = new AtomicLong(0);

  private static final int ENCODE_BUFFER_SIZE = 4096;

  private static final int ENCODE_THREADS = Runtime.getRuntime().availableProcessors();
  private static final int PRODUCER_COUNT = 4;

  public static void main(String[] args) throws Exception {
    int orderCount = 1;
    if (args.length >= 1) {
      orderCount = Integer.parseInt(args[0]);
    }
    if (args.length >= 2) {
      String[] userIds = args[1].split(",");
      USER_IDS = new int[userIds.length];
      NO_OF_USERS = userIds.length;
      for (int i = 0; i < NO_OF_USERS; i++) {
        USER_IDS[i] = Integer.parseInt(userIds[i]);
      }
    } else {
      NO_OF_USERS = 1;
      USER_IDS = new int[]{2};
    }

    Properties kafkaProps = new Properties();
    kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.20.0.50:9092");
    kafkaProps.put(ProducerConfig.CLIENT_ID_CONFIG, "perf-order-producer");
    kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    kafkaProps.put(ProducerConfig.BATCH_SIZE_CONFIG, "262144");          // 256 KB
    kafkaProps.put(ProducerConfig.LINGER_MS_CONFIG, "0");                // no waiting — send immediately
    kafkaProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "134217728");    // 128 MB send buffer
    kafkaProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
    kafkaProps.put(ProducerConfig.ACKS_CONFIG, "0");                     // fire-and-forget — maximum throughput
    kafkaProps.put(ProducerConfig.RETRIES_CONFIG, "0");
    kafkaProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

    final String inputTopic = "api01";

    System.out.println("Kafka order producer starting");
    System.out.printf("  bootstrap    : %s%n", kafkaProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    System.out.printf("  topic        : %s%n", inputTopic);
    System.out.printf("  orders       : %s%n", format(orderCount));
    System.out.printf("  encode threads: %d%n", ENCODE_THREADS);
    System.out.printf("  producers    : %d%n", PRODUCER_COUNT);
    System.out.println();

    // Phase 1: pre-generate all orders in parallel so sending is pure I/O
    System.out.printf("Pre-generating %s orders across %d threads...%n", format(orderCount), ENCODE_THREADS);
    final byte[][] allOrders = new byte[orderCount][];

    final long genStart = System.nanoTime();
    final ExecutorService genPool = Executors.newFixedThreadPool(ENCODE_THREADS);
    final CountDownLatch genLatch = new CountDownLatch(ENCODE_THREADS);
    final int genChunk = (orderCount + ENCODE_THREADS - 1) / ENCODE_THREADS;

    for (int t = 0; t < ENCODE_THREADS; t++) {
      final int from = t * genChunk;
      final int to = Math.min(from + genChunk, orderCount);
      genPool.submit(() -> {
        final Random random = new Random();
        for (int i = from; i < to; i++) {
          final int orderType = random.nextInt(2);
          final Side side = orderType == 0 ? Side.BUY : Side.SELL;
          final long quantity = 1 + random.nextInt(100_000);
          final long price = orderType == 0
              ? 25_000_00 + random.nextInt(25_000_00)
              : 45_000_00 + random.nextInt(25_000_00);
          final String clOrdId = "C" + (i + 1);
          final byte[] bytes = encodeOrder(USER_IDS[i % NO_OF_USERS], clOrdId, SECURITY_ID, side, price, quantity);
          setKafkaHeader(bytes, NORMAL_API);
          allOrders[i] = bytes;
        }
        genLatch.countDown();
      });
    }

    genLatch.await();
    genPool.shutdown();
    final long genMs = (System.nanoTime() - genStart) / 1_000_000;
    System.out.printf("Pre-generation done in %s ms%n%n", format(genMs));

    // Phase 2: send with multiple producers in parallel — each owns a disjoint slice
    final List<KafkaProducer<String, byte[]>> producers = new ArrayList<>(PRODUCER_COUNT);
    for (int p = 0; p < PRODUCER_COUNT; p++) {
      producers.add(new KafkaProducer<>(kafkaProps));
    }

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("Flushing and closing producers ...");
      producers.forEach(prod -> { prod.flush(); prod.close(); });
    }));

    System.out.printf("Sending %s orders with %d producers...%n", format(orderCount), PRODUCER_COUNT);
    final long sendStart = System.nanoTime();

    final ExecutorService sendPool = Executors.newFixedThreadPool(PRODUCER_COUNT);
    final CountDownLatch sendLatch = new CountDownLatch(PRODUCER_COUNT);
    final int sendChunk = (orderCount + PRODUCER_COUNT - 1) / PRODUCER_COUNT;

    for (int p = 0; p < PRODUCER_COUNT; p++) {
      final int from = p * sendChunk;
      final int to = Math.min(from + sendChunk, orderCount);
      final KafkaProducer<String, byte[]> prod = producers.get(p);
      sendPool.submit(() -> {
        for (int i = from; i < to; i++) {
          prod.send(new ProducerRecord<>(inputTopic, allOrders[i]));
        }
        prod.flush();
        sendLatch.countDown();
      });
    }

    sendLatch.await();
    sendPool.shutdown();

    final long elapsedMs = (System.nanoTime() - sendStart) / 1_000_000;

    System.out.println();
    System.out.println("Done.");
    System.out.printf("  Orders sent  : %s%n", format(orderCount));
    System.out.printf("  Send elapsed : %s ms%n", format(elapsedMs));
    System.out.printf("  Throughput   : %s orders/s%n",
        elapsedMs > 0 ? format((1_000L * orderCount) / elapsedMs) : "N/A");
    System.out.printf("  Total elapsed: %s ms (incl. pre-gen)%n",
        format(genMs + elapsedMs));
  }

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
    orderEncoder.priceScale((short) 1);
    orderEncoder.price2(0);
    orderEncoder.price2Scale((short) 2);
    orderEncoder.qty(qty);
    orderEncoder.qtyScale((short) 6);
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
    orderEncoder.clOrdID(clOrdId);
    orderEncoder.symbol("BTC/USD");
    orderEncoder.platform("perf");
    orderEncoder.accountId(String.valueOf(userId));

    encodedLength += (short) orderEncoder.encodedLength();
    buffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);

    final byte[] bytes = new byte[encodedLength + KAFKA_OFFSET];
    buffer.position(0);
    buffer.get(bytes, KAFKA_OFFSET, encodedLength);
    return bytes;
  }

  private static void setKafkaHeader(final byte[] bytes, final byte messageType) {
    long s = seqNum.incrementAndGet();
    for (int i = 7; i >= 0; i--) {
      bytes[i] = (byte) (s & 0xFF);
      s >>= 8;
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
