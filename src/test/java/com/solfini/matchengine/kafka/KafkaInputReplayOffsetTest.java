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
 * These tests pin the resume position. No broker is involved: assign() and seek() are local operations on the consumer,
 * and position() after an explicit seek reports the sought offset without contacting a broker, so start() can be driven
 * directly. getLastInputOffset() reads the output topic, so it is stubbed -- what is under test is the arithmetic
 * between it and the seek, which is where the defect was.
 *
 * replaySelected is left false throughout, because that path additionally calls getStartPointOffsetOffset(), which does
 * need a live broker. The window filter it feeds is not affected by this change.
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

  // The arithmetic, stated directly, including that it does not overflow into a negative at the long ceiling.
  @Test
  public void resumeOffsetIsOnePastTheLastApplied() {
    assertEquals(0L, KafkaInputFixListener.resumeInputOffset(0L));
    assertEquals(2L, KafkaInputFixListener.resumeInputOffset(1L));
    assertEquals(1_000_001L, KafkaInputFixListener.resumeInputOffset(1_000_000L));
  }
}
