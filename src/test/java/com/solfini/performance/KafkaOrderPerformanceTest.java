package com.solfini.performance;

import com.solfini.sbe.encoder.*;
import com.solfini.util.StringUtil;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.*;

import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;

/**
 * Starts the response consumer first, sends N orders, correlates responses by clOrdId,
 * and prints latency and throughput statistics.
 *
 * Usage:
 *   java com.solfini.performance.KafkaOrderPerformanceTest [mode] [orderCount] [userIds] [waitSeconds] [warmupCount] [intervalMicros]
 *
 * mode          : "latency" or "throughput" (case-insensitive, required).
 *                 latency    - paced sends (intervalMicros defaults to 1000, i.e. 1 order/ms),
 *                              producer tuned for latency (linger.ms=0); per-order latency
 *                              stats including percentiles are meaningful.
 *                 throughput - blast mode (intervalMicros forced to 0), producer tuned for
 *                              throughput (linger.ms=5); latency numbers include queueing delay.
 * warmupCount   : orders sent (and awaited) before the measured run to warm the producer,
 *                 broker path, engine and JIT; excluded from latency stats (default 100).
 * intervalMicros: pacing between measured sends in microseconds (latency mode only).
 *
 * Examples:
 *   latency:    java com.solfini.performance.KafkaOrderPerformanceTest latency 10000 1 120 200 1000
 *   throughput: java com.solfini.performance.KafkaOrderPerformanceTest throughput 100000 1 120 200
 */

//java -server -cp ".:config.properties:target/classes:target/test-classes:lib/*" com.solfini.performance.KafkaOrderPerformanceTest [mode] [orderCount] [userIds] [waitSeconds] [warmupCount] [intervalMicros]
//java -server -cp ".:config.properties:target/classes:target/test-classes:lib/*" com.solfini.performance.KafkaOrderPerformanceTest latency 10000 100 120 200 1000
//java -server -cp ".:config.properties:target/classes:target/test-classes:lib/*" com.solfini.performance.KafkaOrderPerformanceTest throughput 100000 100 120 200

public final class KafkaOrderPerformanceTest {

  private enum Mode { LATENCY, THROUGHPUT }

  //private static final String BOOTSTRAP = "10.20.0.50:9092";
  private static final String BOOTSTRAP = "10.20.0.14:9092";
  private static final String INPUT_TOPIC = "api01";
  private static final String OUTPUT_TOPIC = "me01";
  private static final int OUTPUT_PARTITION = 0;

  private static final int KAFKA_OFFSET = 17;
  private static final short LENGTH_FIELD_SIZE = 2;
  private static final int SBE_OFFSET = 19;
  private static final byte NORMAL_API = 2;
  private static final int SECURITY_ID = 3;
  private static final int BUFFER_SIZE = 4096;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(200);

  private static final AtomicInteger MSG_SEQ = new AtomicInteger();
  private static final AtomicLong KAFKA_SEQ = new AtomicLong();

  private AtomicLongArray sendTimesNs;
  private Set<Integer> receivedClOrdIds;
  private int measuredOrderCount;
  private long[] measuredLatenciesNs;
  private final AtomicInteger latencyWriteIndex = new AtomicInteger();
  private final LatencyStats latency = new LatencyStats();

  private KafkaProducer<String, byte[]> producer;

  private final LongAdder totalKafkaMessages = new LongAdder();
  private final LongAdder executionReports = new LongAdder();
  private final LongAdder skippedMessages = new LongAdder();
  private final LongAdder unmatchedReports = new LongAdder();

  private final AtomicLong firstSendNs = new AtomicLong();
  private final AtomicLong lastResponseNs = new AtomicLong();

  private volatile boolean running = true;
  private KafkaConsumer<String, byte[]> consumer;

  public static void main(String[] args) throws Exception {
    Mode mode = parseMode(args.length > 0 ? args[0] : null);
    int orderCount = args.length > 1 ? Integer.parseInt(args[1]) : 1;
    int[] userIds = args.length > 2 ? parseUserIds(args[2]) : new int[]{2};
    long waitSeconds = args.length > 3 ? Long.parseLong(args[3]) : 60;
    int warmupCount = args.length > 4 ? Integer.parseInt(args[4]) : 100;
    long intervalMicros = args.length > 5 ? Long.parseLong(args[5]) : -1;

    if (orderCount <= 0) {
      throw new IllegalArgumentException("orderCount must be greater than zero");
    }
    if (warmupCount < 0) {
      throw new IllegalArgumentException("warmupCount must not be negative");
    }

    if (mode == Mode.LATENCY) {
      if (intervalMicros < 0) {
        intervalMicros = 1_000; // default: 1 order/ms
      }
    } else {
      if (intervalMicros > 0) {
        System.out.println(
            "WARNING: intervalMicros=" + intervalMicros + " ignored in throughput mode (blast).");
      }
      intervalMicros = 0;
    }

    new KafkaOrderPerformanceTest().run(
        mode, orderCount, userIds, waitSeconds, warmupCount, intervalMicros);
  }

  private static Mode parseMode(String arg) {
    if (arg != null) {
      if (arg.equalsIgnoreCase("latency")) return Mode.LATENCY;
      if (arg.equalsIgnoreCase("throughput")) return Mode.THROUGHPUT;
    }
    System.err.println("Invalid or missing mode: " + arg);
    System.err.println();
    System.err.println("Usage:");
    System.err.println("  java com.solfini.performance.KafkaOrderPerformanceTest"
        + " <mode> [orderCount] [userIds] [waitSeconds] [warmupCount] [intervalMicros]");
    System.err.println();
    System.err.println("  mode           latency | throughput (case-insensitive, required)");
    System.err.println("  orderCount     measured orders to send (default 1)");
    System.err.println("  userIds        number of users to spread orders across (default 1 user, id 2)");
    System.err.println("  waitSeconds    max seconds to wait for responses (default 60)");
    System.err.println("  warmupCount    warm-up orders excluded from stats (default 100)");
    System.err.println("  intervalMicros pacing between sends in microseconds;");
    System.err.println("                 latency mode default 1000, forced to 0 in throughput mode");
    System.err.println();
    System.err.println("Examples:");
    System.err.println("  latency:    java com.solfini.performance.KafkaOrderPerformanceTest"
        + " latency 10000 1 120 200 1000");
    System.err.println("  throughput: java com.solfini.performance.KafkaOrderPerformanceTest"
        + " throughput 100000 1 120 200");
    System.exit(1);
    throw new AssertionError("unreachable");
  }

  private void run(Mode mode, int orderCount, int[] userIds, long waitSeconds,
      int warmupCount, long intervalMicros) throws Exception {
    measuredOrderCount = orderCount;
    // clOrdIds 0..orderCount-1 are measured, orderCount..orderCount+warmupCount-1 are warm-up.
    sendTimesNs = new AtomicLongArray(orderCount + warmupCount);
    receivedClOrdIds = new HashSet<>(orderCount + warmupCount);
    measuredLatenciesNs = new long[orderCount];
    System.out.println("Kafka order performance test");
    System.out.println("  mode      : " + mode.name().toLowerCase());
    System.out.println("  bootstrap : " + BOOTSTRAP);
    System.out.println("  input     : " + INPUT_TOPIC);
    System.out.println("  output    : " + OUTPUT_TOPIC);
    System.out.println("  orders    : " + format(orderCount));
    System.out.println("  warmup    : " + format(warmupCount));
    System.out.println("  interval  : " + format(intervalMicros) + " us");
    System.out.println();

    CountDownLatch consumerReady = new CountDownLatch(1);
    CountDownLatch responseLatch = new CountDownLatch(orderCount);
    CountDownLatch warmupLatch = new CountDownLatch(warmupCount);

    Thread consumerThread = new Thread(
        () -> consume(consumerReady, responseLatch, warmupLatch),
        "perf-response-consumer");
    consumerThread.start();

    if (!consumerReady.await(30, TimeUnit.SECONDS)) {
      stopConsumer();
      throw new IllegalStateException("Consumer did not start within 30 seconds");
    }

    producer = new KafkaProducer<>(producerProperties(mode));
    try {
      // Force metadata fetch and broker connection before any timed send.
      producer.partitionsFor(INPUT_TOPIC);

      if (warmupCount > 0) {
        byte[][] warmupOrders = generateOrders(warmupCount, userIds, orderCount);
        sendOrders(warmupOrders, orderCount, 0);
        warmupLatch.await(10, TimeUnit.SECONDS);
        System.out.println("Warm-up complete: "
            + format(warmupCount - warmupLatch.getCount()) + " orders");
      }

      byte[][] orders = generateOrders(orderCount, userIds, 0);

      System.gc();
      System.out.println("  GC count before : " + getGcCount());
      long gcTimeBefore = getGcCollectionTime();
      firstSendNs.set(System.nanoTime());
      long sendStart = firstSendNs.get();
      sendOrders(orders, 0, intervalMicros * 1_000L);
      long sendElapsed = System.nanoTime() - sendStart;

      System.out.printf(
          "Sent %s orders in %.3f ms (%s orders/sec)%n",
          format(orderCount), millis(sendElapsed),
          format(rate(orderCount, sendElapsed)));

      boolean allResponses = responseLatch.await(waitSeconds, TimeUnit.SECONDS);

      stopConsumer();
      consumerThread.join(10_000);
      long gcTimeAfter = getGcCollectionTime();
      System.out.println("  GC count after : " + getGcCount());
      System.out.println("  GC collection time: "
          + (gcTimeAfter - gcTimeBefore) + " ms");
      printSummary(mode, orderCount, sendElapsed, allResponses);
    } finally {
      producer.close();
    }
  }

  private byte[][] generateOrders(int orderCount, int[] userIds, int clOrdIdOffset) {
    byte[][] orders = new byte[orderCount][];
    Random random = new Random();

    for (int i = 0; i < orderCount; i++) {
      Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
      //Side side = i%2 == 0 ? Side.BUY : Side.SELL;
      long quantity = 1L + random.nextInt(100_000);
      long price = side == Side.BUY
          ? 1_000_00L + random.nextInt(1_500_00)  // buy between 1000 to 1500
          : 1_400_00L + random.nextInt(1_900_00); // sell between 1400 to 1900, match between 1400 to 1500

      orders[i] = encodeOrder(
          userIds[i % userIds.length],
          String.valueOf(clOrdIdOffset + i),
          SECURITY_ID,
          side,
          price,
          quantity);
    }
    return orders;
  }

  private void sendOrders(byte[][] orders, int clOrdIdOffset, long intervalNanos) {
    long nextSendNs = System.nanoTime();

    for (int i = 0; i < orders.length; i++) {
      byte[] data = orders[i];

      if (intervalNanos > 0) {
        pace(nextSendNs);
        nextSendNs += intervalNanos;
      }

      // Store local monotonic time immediately before send for accurate latency.
      sendTimesNs.set(clOrdIdOffset + i, System.nanoTime());
      setKafkaHeader(data, NORMAL_API);

      producer.send(new ProducerRecord<>(INPUT_TOPIC, data));
    }
    producer.flush();
  }

  /** Waits until the given nanoTime deadline, parking for the bulk and spinning at the end. */
  private static void pace(long deadlineNs) {
    long remaining;
    while ((remaining = deadlineNs - System.nanoTime()) > 0) {
      if (remaining > 100_000L) {
        LockSupport.parkNanos(remaining - 50_000L);
      } else {
        Thread.onSpinWait();
      }
    }
  }

  private void consume(CountDownLatch ready, CountDownLatch responseLatch,
      CountDownLatch warmupLatch) {
    UnsafeBuffer buffer = new UnsafeBuffer();
    MessageHeaderDecoder header = new MessageHeaderDecoder();
    ExecutionReportDecoder er = new ExecutionReportDecoder();

    try {
      consumer = new KafkaConsumer<>(consumerProperties());
      TopicPartition partition = new TopicPartition(OUTPUT_TOPIC, OUTPUT_PARTITION);
      consumer.assign(Collections.singletonList(partition));
      consumer.seekToEnd(Collections.singletonList(partition));
      ready.countDown();

      System.out.println("Consumer is listening; starting order production...");

      while (running && responseLatch.getCount() > 0) {
        ConsumerRecords<String, byte[]> records = consumer.poll(POLL_TIMEOUT);

        for (ConsumerRecord<String, byte[]> record : records) {
          totalKafkaMessages.increment();
          byte[] data = record.value();

          if (data == null || data.length < SBE_OFFSET || data[16] != NORMAL_API) {
            skippedMessages.increment();
            continue;
          }

          buffer.wrap(data);
          header.wrap(buffer, SBE_OFFSET);

          if (header.templateId() != ExecutionReportDecoder.TEMPLATE_ID) {
            skippedMessages.increment();
            continue;
          }

          er.wrap(buffer, SBE_OFFSET + header.encodedLength(),
              header.blockLength(), header.version());
          executionReports.increment();

          int clOrdId = StringUtil.toInt(er.clOrdID());
          if (clOrdId < 0 || clOrdId >= sendTimesNs.length()) {
            unmatchedReports.increment();
            continue;
          }
          long sentNs = sendTimesNs.get(clOrdId);
          if (sentNs == 0) {
            unmatchedReports.increment();
            continue;
          }

          // NEW and TRADE can both arrive. Measure only the first ER per order.
          if (receivedClOrdIds.add(clOrdId)) {
            if (clOrdId >= measuredOrderCount) {
              // Warm-up order: counts toward warm-up completion only, not latency stats.
              warmupLatch.countDown();
              continue;
            }

            long responseNs = System.nanoTime();
            long latencyNs = responseNs - sentNs;
            latency.record(latencyNs);
            int idx = latencyWriteIndex.getAndIncrement();
            if (idx < measuredLatenciesNs.length) {
              measuredLatenciesNs[idx] = latencyNs;
            }
            lastResponseNs.accumulateAndGet(responseNs, Math::max);
            responseLatch.countDown();

            long count = latencyWriteIndex.get();
            if (count <= 10 || count % 100_000 == 0) {
              System.out.printf(
                  "[response=%d offset=%d] clOrdId=%s execType=%s status=%s latency=%.3f us%n",
                  count, record.offset(), clOrdId, er.execType(), er.ordStatus(), micros(latencyNs));
            }
          }
        }
      }
    } catch (WakeupException e) {
      if (running) throw e;
    } finally {
      ready.countDown();
      if (consumer != null) consumer.close();
    }
  }

  private void stopConsumer() {
    running = false;
    KafkaConsumer<String, byte[]> current = consumer;
    if (current != null) current.wakeup();
  }

  private void printSummary(Mode mode, int orderCount, long sendElapsed, boolean allResponses) {
    long responses = Math.min(latencyWriteIndex.get(), orderCount);
    long responseElapsed = lastResponseNs.get() > firstSendNs.get()
        ? lastResponseNs.get() - firstSendNs.get() : 0;

    System.out.println("\n=== Summary ===");
    System.out.printf("Orders requested        : %s%n", format(orderCount));
    System.out.printf("Orders sent             : %s%n", format(orderCount));
    System.out.printf("Unique responses        : %s%n", format(responses));
    System.out.printf("Missing responses       : %s%n", format(orderCount - responses));
    System.out.printf("All responses received  : %s%n", allResponses ? "yes" : "no");
    System.out.printf("Kafka messages consumed : %s%n", format(totalKafkaMessages.sum()));
    System.out.printf("Execution reports       : %s%n", format(executionReports.sum()));
    System.out.printf("Skipped messages        : %s%n", format(skippedMessages.sum()));
    System.out.printf("Unmatched reports       : %s%n", format(unmatchedReports.sum()));
    System.out.printf("Producer elapsed        : %.3f ms%n", millis(sendElapsed));
    System.out.printf("Producer throughput     : %s orders/sec%n", format(rate(orderCount, sendElapsed)));
    System.out.printf("Response elapsed        : %.3f ms%n", millis(responseElapsed));
    System.out.printf("Response throughput     : %s orders/sec%n", format(rate(responses, responseElapsed)));
    System.out.printf("Latency min             : %.3f us%n", micros(latency.min()));
    System.out.printf("Latency avg             : %.3f us%n", micros(latency.avg()));
    System.out.printf("Latency max             : %.3f us%n", micros(latency.max()));

    int recorded = (int) Math.min(latencyWriteIndex.get(), measuredLatenciesNs.length);
    if (recorded > 0) {
      long[] sorted = Arrays.copyOf(measuredLatenciesNs, recorded);
      Arrays.sort(sorted);
      System.out.printf("Latency p50             : %.3f us%n", micros(percentile(sorted, 50.0)));
      System.out.printf("Latency p90             : %.3f us%n", micros(percentile(sorted, 90.0)));
      System.out.printf("Latency p99             : %.3f us%n", micros(percentile(sorted, 99.0)));
      System.out.printf("Latency p99.9           : %.3f us%n", micros(percentile(sorted, 99.9)));
    }

    if (mode == Mode.THROUGHPUT) {
      System.out.println(
          "NOTE: throughput mode - latency includes queueing delay; use latency mode for per-order latency");
    }
  }

  /** Nearest-rank percentile over a sorted array; safe for any non-empty sample size. */
  private static long percentile(long[] sorted, double percent) {
    int index = (int) Math.ceil(percent / 100.0 * sorted.length) - 1;
    if (index < 0) index = 0;
    if (index >= sorted.length) index = sorted.length - 1;
    return sorted[index];
  }

  private static Properties producerProperties(Mode mode) {
    Properties p = new Properties();
    p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
    p.put(ProducerConfig.CLIENT_ID_CONFIG, "perf-order-producer");
    p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    p.put(ProducerConfig.BATCH_SIZE_CONFIG, "262144");
    // latency mode: send immediately; throughput mode: small linger to fill batches.
    p.put(ProducerConfig.LINGER_MS_CONFIG, mode == Mode.THROUGHPUT ? "5" : "0");
    p.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "134217728");
    // No compression: per-message compression adds latency with no batching benefit at linger.ms=0.
    p.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");
    p.put(ProducerConfig.ACKS_CONFIG, "0");
    p.put(ProducerConfig.RETRIES_CONFIG, "0");
    p.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
    return p;
  }

  private static Properties consumerProperties() {
    Properties p = new Properties();
    p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
    p.put(ConsumerConfig.GROUP_ID_CONFIG, "perf-er-consumer-" + System.currentTimeMillis());
    p.put(ConsumerConfig.CLIENT_ID_CONFIG, "perf-er-consumer");
    p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    p.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, "2097152");
    p.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1");
    p.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "100");
    return p;
  }

  private static byte[] encodeOrder(int userId, String clOrdId, int securityId,
      Side side, long price, long quantity) {

    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    UnsafeBuffer unsafeBuffer = new UnsafeBuffer(byteBuffer);
    MessageHeaderEncoder header = new MessageHeaderEncoder();
    NewOrderSingleEncoder order = new NewOrderSingleEncoder();

    short encodedLength = LENGTH_FIELD_SIZE;
    order.wrapAndApplyHeader(unsafeBuffer, encodedLength, header);
    header.senderCompId("perf");
    header.sendingTime(wireTime());
    header.msgSeqNum(MSG_SEQ.incrementAndGet());
    header.sourceSeqNum(0);
    header.kafkaRecordOffset(0);
    header.deliverToCompId(0);
    encodedLength += header.encodedLength();

    order.orderId(0);
    order.submitterId(userId);
    order.userId(userId);
    order.securityId(securityId);
    order.side(side);
    order.ordType(OrdType.LIMIT);
    order.price(price);
    order.priceScale((short) 2);
    order.price2(0);
    order.price2Scale((short) 2);
    order.qty(quantity);
    order.qtyScale((short) 6);
    order.stopPx(0);
    order.stopPxScale((short) 0);
    order.targetStrategy(0);
    order.isHidden(BooleanType.FALSE);
    order.isLiquidation(BooleanType.FALSE);
    order.isLastLook(BooleanType.FALSE);
    order.timeInForce(TimeInForce.GOOD_TILL_CANCEL);
    order.expireTime(0);
    order.assetId(0);
    order.tokenId(0);
    order.groupAssetId(0);
    order.selectId(0);
    order.quoteType(QuoteType.NULL_VAL);
    order.quoteTargetUserId(0);
    order.clOrdID(clOrdId);
    order.symbol("ETH/USD");
    order.platform("perf");
    order.accountId(String.valueOf(userId));

    encodedLength += (short) order.encodedLength();
    byteBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);

    byte[] bytes = new byte[encodedLength + KAFKA_OFFSET];
    byteBuffer.position(0);
    byteBuffer.get(bytes, KAFKA_OFFSET, encodedLength);
    return bytes;
  }

  private static void setKafkaHeader(byte[] bytes, byte messageType) {
    long sequence = KAFKA_SEQ.incrementAndGet();
    for (int i = 7; i >= 0; i--) {
      bytes[i] = (byte) (sequence & 0xFF);
      sequence >>= 8;
    }

    long time = wireTime();
    for (int i = 15; i >= 8; i--) {
      bytes[i] = (byte) (time & 0xFF);
      time >>= 8;
    }
    bytes[16] = messageType;
  }

  private static int[] parseUserIds(String input) {
    int noOfUsers = Integer.parseInt(input);
    int[] result = new int[noOfUsers];
    for (int i = 0; i < noOfUsers; i++) {
      // leave first 25 admin users
      result[i] = i + 25;
    }
    return result;
  }

  private static long wireTime() {
    return System.currentTimeMillis() * 1_000_000L + (System.nanoTime() % 1_000_000L);
  }

  private static long rate(long count, long elapsedNs) {
    return count <= 0 || elapsedNs <= 0 ? 0
        : Math.round(count * 1_000_000_000.0 / elapsedNs);
  }

  private static double millis(long ns) { return ns / 1_000_000.0; }
  private static double micros(long ns) { return ns / 1_000.0; }
  private static String format(long value) { return NumberFormat.getInstance().format(value); }

  private static final class LatencyStats {
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
    private final LongAdder total = new LongAdder();
    private final LongAdder count = new LongAdder();

    void record(long value) {
      min.accumulateAndGet(value, Math::min);
      max.accumulateAndGet(value, Math::max);
      total.add(value);
      count.increment();
    }

    long min() { return count.sum() == 0 ? 0 : min.get(); }
    long max() { return count.sum() == 0 ? 0 : max.get(); }
    long avg() { return count.sum() == 0 ? 0 : total.sum() / count.sum(); }
  }

  public static long getGcCount() {
    long count = 0;

    for (GarbageCollectorMXBean gc :
        ManagementFactory.getGarbageCollectorMXBeans()) {

      long c = gc.getCollectionCount();

      if (c >= 0) {
        count += c;
      }
    }

    return count;
  }

  public static long getGcCollectionTime() {
    long time = 0;

    for (GarbageCollectorMXBean gc :
        ManagementFactory.getGarbageCollectorMXBeans()) {

      long t = gc.getCollectionTime();

      if (t >= 0) {
        time += t;
      }
    }

    return time;
  }
}
