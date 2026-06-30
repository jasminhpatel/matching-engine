package com.solfini.performance;

import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.ExecutionReportDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.benchmark.MinMaxAvgLatency;
import com.solfini.util.benchmark.RateBenchmark;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/**
 * Performance test consumer: reads response messages from the matching engine output
 * topic and decodes ExecutionReport messages. All other message types are silently skipped.
 *
 * Usage:  java KafkaOrderResponseConsumer [maxMessages]
 * Default: runs until interrupted (Ctrl-C). Pass a positive integer to stop after N ERs.
 *
 * Wire-protocol layout (mirrors KafkaDRFixListener):
 *   bytes  0-7  : sequence number (big-endian long)
 *   bytes  8-15 : send timestamp  (big-endian long)
 *   byte   16   : message type    (2=NORMAL_API, 4=ADMIN_API)
 *   bytes 17-18 : 2-byte SBE total-length field
 *   bytes 19+   : SBE MessageHeader + SBE payload
 */
public class KafkaOrderResponseConsumer {

  private static final int KAFKA_OFFSET = 17;  // seqNum(8) + sendTime(8) + msgType(1)
  private static final int SBE_OFFSET = 19;    // KAFKA_OFFSET + 2-byte length field
  private final RateBenchmark rateBenchmark = new RateBenchmark("RateBenchmark");
  private final MinMaxAvgLatency latencyBenchmark = new MinMaxAvgLatency("MinMaxAvgLatency");

  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);


  public static void main(String[] args) throws InterruptedException {
    long maxMessages = Long.MAX_VALUE;
    if (args.length >= 1) {
      maxMessages = Long.parseLong(args[0]);
    }

    // ---------- Hardcoded Kafka consumer configuration ----------
    Properties kafkaProps = new Properties();
    kafkaProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    kafkaProps.put(ConsumerConfig.GROUP_ID_CONFIG, "perf-er-consumer");
    kafkaProps.put(ConsumerConfig.CLIENT_ID_CONFIG, "perf-er-consumer");
    kafkaProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    kafkaProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    kafkaProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    kafkaProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    kafkaProps.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, "2097152"); // 2 MB
    kafkaProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1");
    kafkaProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "100");
    // -----------------------------------------------

    final String outputTopic = "me1"; // matches ME_KAFKA_TOPIC_PRIMARY default

    System.out.println("Kafka execution report consumer starting");
    System.out.printf("  bootstrap : %s%n", kafkaProps.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
    System.out.printf("  topic     : %s%n", outputTopic);
    System.out.printf("  maxER     : %s%n", maxMessages == Long.MAX_VALUE ? "unlimited" : format(maxMessages));
    System.out.println();

    final KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(kafkaProps);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("\nShutting down consumer ...");
      consumer.wakeup();
    }));

    final TopicPartition partition = new TopicPartition(outputTopic, 0);
    consumer.assign(Collections.singletonList(partition));
    consumer.seekToEnd(Collections.singletonList(partition));

    // Reusable decoder state
    final UnsafeBuffer decoderBuffer = new UnsafeBuffer();
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final ExecutionReportDecoder erDecoder = new ExecutionReportDecoder();

    long totalMessages = 0;
    long erCount = 0;
    long newCount = 0;
    long tradeCount = 0;
    long cancelCount = 0;
    long otherErCount = 0;
    long skippedCount = 0;

    final long startTime = System.nanoTime();
    System.out.println("Listening for messages (Ctrl-C to stop) ...");
    System.out.println();

    while (erCount < maxMessages) {
      final ConsumerRecords<String, byte[]> records;
      try {
        records = consumer.poll(POLL_TIMEOUT);
      } catch (org.apache.kafka.common.errors.WakeupException e) {
        break;
      }

      for (final ConsumerRecord<String, byte[]> record : records) {
        final byte[] data = record.value();
        totalMessages++;

        if (data.length < KAFKA_OFFSET + 1) {
          skippedCount++;
          continue;
        }

        final byte messageType = data[16];

        // Only handle NORMAL_API messages; skip ADMIN_API and anything else
        if (KafkaPublisher.NORMAL_API != messageType) {
          skippedCount++;
          continue;
        }

        if (data.length < SBE_OFFSET + headerDecoder.encodedLength()) {
          skippedCount++;
          continue;
        }

        decoderBuffer.wrap(data);
        headerDecoder.wrap(decoderBuffer, SBE_OFFSET);

        // Skip non-ExecutionReport message types
        if (ExecutionReportDecoder.TEMPLATE_ID != headerDecoder.templateId()) {
          skippedCount++;
          continue;
        }

        erDecoder.wrap(decoderBuffer, SBE_OFFSET + headerDecoder.encodedLength(),
            headerDecoder.blockLength(), headerDecoder.version());

        erCount++;
        final ExecType execType = erDecoder.execType();
        if (execType == ExecType.NEW || execType == ExecType.RESTATED) {
          newCount++;
        } else if (execType == ExecType.TRADE || execType == ExecType.CALCULATED) {
          tradeCount++;
        } else if (execType == ExecType.CANCELED || execType == ExecType.EXPIRED) {
          cancelCount++;
        } else {
          otherErCount++;
        }

        if (erCount <= 10 || erCount % 100_000 == 0) {
          printExecutionReport(record.offset(), erDecoder, headerDecoder);
        }
      }
    }

    consumer.close();

    final long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
    System.out.println();
    System.out.println("=== Summary ===");
    System.out.printf("  Total messages consumed : %s%n", format(totalMessages));
    System.out.printf("  Execution reports       : %s%n", format(erCount));
    System.out.printf("    NEW / RESTATED        : %s%n", format(newCount));
    System.out.printf("    TRADE / CALCULATED    : %s%n", format(tradeCount));
    System.out.printf("    CANCELED / EXPIRED    : %s%n", format(cancelCount));
    System.out.printf("    Other                 : %s%n", format(otherErCount));
    System.out.printf("  Skipped (non-ER)        : %s%n", format(skippedCount));
    System.out.printf("  Elapsed                 : %s ms%n", format(elapsedMs));
    if (elapsedMs > 0) {
      System.out.printf("  Throughput              : %s ER/s%n", format((1_000L * erCount) / elapsedMs));
    }
  }

  private static void printExecutionReport(final long kafkaOffset,
      final ExecutionReportDecoder er, final MessageHeaderDecoder header) {

    final ExecType execType = er.execType();
    final OrdStatus ordStatus = er.ordStatus();
    final Side side = er.side();
    final OrdType ordType = er.ordType();

    final long priceRaw = er.price();
    final short priceScale = er.priceScale();
    final double price = scale(priceRaw, priceScale);

    final long qtyRaw = er.orderQty();
    final short qtyScale = er.orderQtyScale();
    final double qty = scale(qtyRaw, qtyScale);

    final long leavesRaw = er.leavesQty();
    final short leavesScale = er.leavesQtyScale();
    final double leavesQty = scale(leavesRaw, leavesScale);

    final long avgPxRaw = er.avgPx();
    final short avgPxScale = er.avgPxScale();
    final double avgPx = scale(avgPxRaw, avgPxScale);

    System.out.printf(
        "[offset=%-8d] ER seqNum=%-8d clOrdId=%-20s orderId=%-10d secId=%-4d " +
        "side=%-5s execType=%-12s ordStatus=%-10s " +
        "price=%-14.6f qty=%-14.6f leavesQty=%-14.6f avgPx=%-14.6f%n",
        kafkaOffset,
        header.msgSeqNum(),
        er.clOrdID(),
        er.orderId(),
        er.securityId(),
        side != null ? side : "?",
        execType != null ? execType : "?",
        ordStatus != null ? ordStatus : "?",
        price, qty, leavesQty, avgPx
    );
  }

  private static double scale(final long raw, final short scale) {
    if (scale <= 0) return raw;
    double divisor = 1.0;
    for (int i = 0; i < scale; i++) divisor *= 10;
    return raw / divisor;
  }

  private static String format(final long value) {
    return NumberFormat.getInstance().format(value);
  }
}
