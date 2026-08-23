package com.solfini.matchengine.kafka;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Properties;

import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Test;

import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

/**
 * Where the input queue is resumed from after a restart.
 *
 * The engine restores state from a snapshot and then replays its own output, so by the time the input listener starts,
 * the message identified by getLastInputOffset() has already been applied and is already reflected in that state.
 * Seeking *to* that offset hands it to the matcher a second time. Balance changes are increments and nothing
 * deduplicates them, so a replayed deposit or withdrawal is applied twice -- twice the money.
 *
 * "Already applied" is only true of a record whose output transaction is complete, which is what
 * isAppliedBoundary decides and what the last two tests here cover.
 *
 * No broker is involved. assign() and seek() are local operations on the consumer, and position() after an explicit
 * seek reports the sought offset without contacting a broker, so start() can be driven directly. getLastInputOffset()
 * reads the output topic, so it is stubbed -- it is the input to the arithmetic under test, not part of it.
 *
 * Two limitations of that seam, both to do with getStartPointOffsetOffset() genuinely needing a broker. Every case here
 * runs with replaySelected=false, which means the pendingMessages log arithmetic this change also touched is not
 * exercised, and it means the combination under test is not the one production runs: ControllerThread normalises INPUT
 * to NONE and BOTH to OUTPUT, so SELECTED_INPUT is the only mode that reaches replay=true and it always sets
 * replaySelected=true with it. The seek arithmetic is shared by both, and the replay window filter is unchanged by this
 * patch beyond the comparison fixed alongside it.
 */
public class KafkaInputReplayOffsetTest {

  /** A listener whose notion of "last applied" is whatever the test says it is. */
  private static final class StubbedListener extends KafkaInputFixListener {

    private final long lastInputOffset;

    private StubbedListener(final boolean replay, final long lastInputOffset) {
      super(replay);
      this.lastInputOffset = lastInputOffset;
    }

    @Override
    protected long getLastInputOffset() {
      return lastInputOffset;
    }
  }

  @Before
  public void before() throws IOException {
    final Properties properties = new Properties();
    PoolSize.minimize(properties);
    // A KafkaConsumer can be constructed from these without reaching a broker, which is all start() needs.
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    properties.setProperty("KAFKA.CONSUMER.group.id", "kafka-input-replay-offset-test");
    PropertyReader.initialize(null, properties);
  }

  private long resumedPositionFor(final long lastAppliedOffset) {
    final StubbedListener listener = new StubbedListener(true, lastAppliedOffset);
    listener.start();
    return listener.getConsumer().position(new TopicPartition(listener.getTopic(), 0));
  }

  // The message at the last applied offset has already been applied, so reading resumes after it.
  @Test
  public void replayResumesAfterTheLastAppliedMessage() {
    assertEquals(42L, resumedPositionFor(41L));
  }

  // Nothing known: no output message carries an input offset, or the output topic is empty. Start from whatever the
  // input topic still retains, which is what this did before.
  @Test
  public void replayStartsFromTheBeginningWhenNothingIsKnown() {
    assertEquals(0L, resumedPositionFor(0L));
  }

  // Defensive: getLastInputOffset() is not specified to return a negative, but a negative must not become a seek to
  // -1, which Kafka rejects.
  @Test
  public void replayTreatsANegativeLastAppliedOffsetAsUnknown() {
    assertEquals(0L, resumedPositionFor(-1L));
  }

  // The arithmetic, stated directly. No ceiling case: resumeInputOffset has no clamp, so Long.MAX_VALUE would wrap
  // negative, and Kafka offsets do not get anywhere near 2^63.
  @Test
  public void resumeOffsetIsOnePastTheLastApplied() {
    assertEquals(0L, KafkaInputFixListener.resumeInputOffset(0L));
    assertEquals(2L, KafkaInputFixListener.resumeInputOffset(1L));
    assertEquals(1_000_001L, KafkaInputFixListener.resumeInputOffset(1_000_000L));
  }

  /**
   * An output record only marks a safe resume boundary if its transaction completed.
   *
   * The matcher wraps each input message's output in one transaction and marks only the last record. Publishing is
   * fire-and-forget with retries=0, so a crash can leave a set on the topic without its closing record. During replay
   * the receiver queue buffers any transaction id above 1 until the marked record arrives, and the snapshot replay never
   * flushes the remainder -- so those records carry an input offset and yet were never applied. Resuming past one loses
   * that input message outright, which for a deposit is worse than the duplicate this change exists to prevent.
   */
  @Test
  public void onlyACompleteTransactionMarksTheResumeBoundary() {
    // Marked as the last record of its transaction: applied, so usable.
    assertEquals(true, KafkaInputFixListener.isAppliedBoundary(4242L, 1));

    // Mid-transaction with no closing record on the topic: buffered during replay and never applied.
    assertEquals(false, KafkaInputFixListener.isAppliedBoundary(4242L, 0));
  }

  // Transaction ids 0 and 1 are passed straight through by the receiver queue rather than buffered, so such a record is
  // applied whether or not it is marked as ending anything.
  @Test
  public void nonTransactionalRecordsAlwaysMarkTheResumeBoundary() {
    assertEquals(true, KafkaInputFixListener.isAppliedBoundary(0L, 0));
    assertEquals(true, KafkaInputFixListener.isAppliedBoundary(1L, 0));
  }
}
