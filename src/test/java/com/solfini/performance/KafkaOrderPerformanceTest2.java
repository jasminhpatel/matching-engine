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

/**
 * Sustained-rate performance test: produces orders at a fixed target rate and measures
 * both latency and throughput while the pipeline is under that steady load.
 *
 * Sends are paced with absolute scheduling: order i is due at startNs + i * interval,
 * and the sender spins (never sleeps) until each deadline. If the sender falls behind,
 * it sends immediately to catch up without skipping orders. Latency is measured from
 * the SCHEDULED send time (coordinated-omission aware), so producer backpressure shows
 * up in the latency numbers instead of being hidden.
 *
 * Usage:
 *   java com.solfini.performance.KafkaOrderPerformanceTest2 [orderCount] [userIds] [waitSeconds] [warmupCount] [targetRatePerSec]
 * Example:
 *   java com.solfini.performance.KafkaOrderPerformanceTest2 500000 1000 120 200 100000
 *
 * orderCount      : measured orders to send (default 500000).
 * userIds         : comma-separated user ids to spread orders across (default 1).
 * waitSeconds     : max seconds to wait for responses (default 120).
 * warmupCount     : orders sent (and awaited) before the measured run to warm the producer,
 *                   broker path, engine and JIT; excluded from latency stats (default 200).
 * targetRatePerSec: target send rate in orders/sec (default 100000).
 */
public final class KafkaOrderPerformanceTest2 {

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

  // clOrdIds are 1-based: 1..orderCount are measured, orderCount+1..orderCount+warmupCount
  // are warm-up. Index 0 is unused so a garbage clOrdId (StringUtil.toInt -> 0) never matches.
  private AtomicLongArray scheduledSendNs;
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
    int orderCount = args.length > 0 ? Integer.parseInt(args[0]) : 500_000;
    int[] userIds = args.length > 1 ? parseUserIds(args[1]) : new int[]{1};
    long waitSeconds = args.length > 2 ? Long.parseLong(args[2]) : 120;
    int warmupCount = args.length > 3 ? Integer.parseInt(args[3]) : 200;
    long targetRatePerSec = args.length > 4 ? Long.parseLong(args[4]) : 100_000;

    if (orderCount <= 0) {
      throw new IllegalArgumentException("orderCount must be greater than zero");
    }
    if (warmupCount < 0) {
      throw new IllegalArgumentException("warmupCount must not be negative");
    }
    if (targetRatePerSec <= 0 || targetRatePerSec > 1_000_000_000L) {
      throw new IllegalArgumentException(
          "targetRatePerSec must be between 1 and 1000000000");
    }

    new KafkaOrderPerformanceTest2().run(
        orderCount, userIds, waitSeconds, warmupCount, targetRatePerSec);
  }

  private void run(int orderCount, int[] userIds, long waitSeconds,
      int warmupCount, long targetRatePerSec) throws Exception {
    measuredOrderCount = orderCount;
    scheduledSendNs = new AtomicLongArray(orderCount + warmupCount + 1);
    receivedClOrdIds = new HashSet<>(orderCount + warmupCount);
    measuredLatenciesNs = new long[orderCount];
    long intervalNs = 1_000_000_000L / targetRatePerSec;

    System.out.println("Kafka sustained-rate order performance test");
    System.out.println("  bootstrap : " + BOOTSTRAP);
    System.out.println("  input     : " + INPUT_TOPIC);
    System.out.println("  output    : " + OUTPUT_TOPIC);
    System.out.println("  orders    : " + format(orderCount));
    System.out.println("  warmup    : " + format(warmupCount));
    System.out.println("  rate      : " + format(targetRatePerSec)
        + " orders/sec (interval " + format(intervalNs) + " ns)");
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

    producer = new KafkaProducer<>(producerProperties());
    try {
      // Force metadata fetch and broker connection before any timed send.
      producer.partitionsFor(INPUT_TOPIC);

      if (warmupCount > 0) {
        byte[][] warmupOrders = generateOrders(warmupCount, userIds, orderCount);
        sendWarmupOrders(warmupOrders, orderCount);
        warmupLatch.await(10, TimeUnit.SECONDS);
        System.out.println("Warm-up complete: "
            + format(warmupCount - warmupLatch.getCount()) + " orders");
      }

      byte[][] orders = generateOrders(orderCount, userIds, 0);

      System.gc();
      System.out.println("  GC count before : " + getGcCount());
      long gcTimeBefore = getGcCollectionTime();
      long sendElapsed = sendOrdersAtRate(orders, intervalNs);

      System.out.printf(
          "Sent %s orders in %.3f ms (target %s, achieved %s orders/sec)%n",
          format(orderCount), millis(sendElapsed),
          format(targetRatePerSec), format(rate(orderCount, sendElapsed)));

      boolean allResponses = responseLatch.await(waitSeconds, TimeUnit.SECONDS);

      stopConsumer();
      consumerThread.join(10_000);
      long gcTimeAfter = getGcCollectionTime();
      System.out.println("  GC count after : " + getGcCount());
      System.out.println("  GC collection time: "
          + (gcTimeAfter - gcTimeBefore) + " ms");
      printSummary(orderCount, targetRatePerSec, sendElapsed, allResponses);
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
          String.valueOf(clOrdIdOffset + i + 1),
          SECURITY_ID,
          side,
          price,
          quantity);
    }
    return orders;
  }

  /** Warm-up sends are unpaced; their (actual) send times only feed the warm-up latch. */
  private void sendWarmupOrders(byte[][] orders, int clOrdIdOffset) {
    for (int i = 0; i < orders.length; i++) {
      byte[] data = orders[i];
      scheduledSendNs.set(clOrdIdOffset + i + 1, System.nanoTime());
      setKafkaHeader(data, NORMAL_API);
      producer.send(new ProducerRecord<>(INPUT_TOPIC, data));
    }
    producer.flush();
  }

  /**
   * Sends orders paced to the target rate using absolute scheduling: order i is due at
   * startNs + i * intervalNs. Spins until each deadline (no sleeping - at 100k/sec the
   * interval is 10 us, far below timer granularity). If behind schedule, sends
   * immediately to catch up; deadlines never drift because they are computed from the
   * fixed start time, not from "now". Returns the send phase elapsed nanos.
   */
  private long sendOrdersAtRate(byte[][] orders, long intervalNs) {
    long startNs = System.nanoTime();
    firstSendNs.set(startNs);

    for (int i = 0; i < orders.length; i++) {
      byte[] data = orders[i];
      long scheduledNs = startNs + i * intervalNs;

      while (System.nanoTime() < scheduledNs) {
        Thread.onSpinWait();
      }

      // Latency is measured from the scheduled time (coordinated-omission aware):
      // if the sender falls behind, the delay counts against the order's latency.
      scheduledSendNs.set(i + 1, System.nanoTime());

      setKafkaHeader(data, NORMAL_API);
      producer.send(new ProducerRecord<>(INPUT_TOPIC, data));
    }
    producer.flush();
    return System.nanoTime() - startNs;
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
          if (clOrdId <= 0 || clOrdId >= scheduledSendNs.length()) {
            unmatchedReports.increment();
            continue;
          }
          long scheduledNs = scheduledSendNs.get(clOrdId);
          if (scheduledNs == 0) {
            unmatchedReports.increment();
            continue;
          }

          // NEW and TRADE can both arrive. Measure only the first ER per order.
          if (receivedClOrdIds.add(clOrdId)) {
            if (clOrdId > measuredOrderCount) {
              // Warm-up order: counts toward warm-up completion only, not latency stats.
              warmupLatch.countDown();
              continue;
            }

            long responseNs = System.nanoTime();
            long latencyNs = responseNs - scheduledNs;
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

  private void printSummary(int orderCount, long targetRatePerSec, long sendElapsed,
      boolean allResponses) {
    long responses = Math.min(latencyWriteIndex.get(), orderCount);
    long responseElapsed = lastResponseNs.get() > firstSendNs.get()
        ? lastResponseNs.get() - firstSendNs.get() : 0;
    long achievedRate = rate(orderCount, sendElapsed);

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
    System.out.printf("Send elapsed            : %.3f ms%n", millis(sendElapsed));
    System.out.printf("Target send rate        : %s orders/sec%n", format(targetRatePerSec));
    System.out.printf("Achieved send rate      : %s orders/sec%n", format(achievedRate));
    if (achievedRate < targetRatePerSec * 0.98) {
      System.out.printf(
          "WARNING: achieved send rate is more than 2%% below target"
              + " (%s vs %s orders/sec) - the producer/host cannot sustain this rate;"
              + " latency numbers include the resulting backlog%n",
          format(achievedRate), format(targetRatePerSec));
    }
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
    System.out.println(
        "NOTE: latency is measured from each order's SCHEDULED send time"
            + " (coordinated-omission aware)");
  }

  /** Nearest-rank percentile over a sorted array; safe for any non-empty sample size. */
  private static long percentile(long[] sorted, double percent) {
    int index = (int) Math.ceil(percent / 100.0 * sorted.length) - 1;
    if (index < 0) index = 0;
    if (index >= sorted.length) index = sorted.length - 1;
    return sorted[index];
  }

  private static Properties producerProperties() {
    Properties p = new Properties();
    p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
    p.put(ProducerConfig.CLIENT_ID_CONFIG, "perf-order-producer");
    p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    p.put(ProducerConfig.BATCH_SIZE_CONFIG, "262144");
    p.put(ProducerConfig.LINGER_MS_CONFIG, "0");
    p.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "134217728");
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
    String[] values = input.split(",");
    int[] result = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = Integer.parseInt(values[i].trim());
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
