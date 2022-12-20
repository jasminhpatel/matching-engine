package com.solfini.matchengine.message;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Properties;

import com.solfini.internal.admin.schema.TokenType;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.event.Level;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.drmode.DecoderThreadCache;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.outbound.PositionReportMessage;
import com.solfini.matchengine.publisher.PositionReportEncoderCache;
import com.solfini.matchengine.publisher.SessionInfoCache;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.pool.PositionReportObjectPool;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.sbe.encoder.PositionReportDecoder;
import com.solfini.sbe.encoder.PositionReportEncoder;
import com.solfini.user.User;
import com.solfini.util.FixDecoderUtil;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;

public class DecoderTest extends MessageTest {

  private static final int OFFSET = 17;
  private static final PositionReportEncoderCache cache = new PositionReportEncoderCache();
  private static final String INSTANCE_ID = Context.getInstanceId();
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  private static final int USDT_ID = 1;
  private final int ME_SEQ_ID = Context.getMESeqId(); // this can be incremented with failover
  private final int posReqResult = 4000;
  private static final short SHORT_ZERO = 0;
  private static final short USD_SCALE = 2;
  private static final short USD_MULT = 100;


  @BeforeClass
  public static void before() {
    try {
      LogLevel.setLevel(Level.TRACE);

      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "262144");
      properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "131072");
      properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "2000000");
      properties.setProperty("POSITION_POOL_START_CAPACITY", "2000000");
      properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "1048576");
      properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "1048576");
      properties.setProperty("NUM_ENCODER_THREADS", "0");
      properties.setProperty("NUM_DECODER_THREADS", "8");
      properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
      properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
      properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
      PropertyReader.initialize(null, properties);


      createInstruments();

      for (int i = 0; i < 19; ++i) {
        InstrumentCache.addPair(new InstrumentPair(i + 4, "BTC/USD[" + i + "]", "BTC/USD[" + i + "]", InstrumentCache.get(1),
            InstrumentCache.get(2), (short) 6, (short) 6, 0, AssetType.PAIR, 10, 20, 3500, 0));
      }

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }


  @Ignore
  @Test
  public void measurePositionReportDecoderPerformance() {
    final User user = new User(18);
    for (int i = 0; i < user.getPositionArr().length; ++i) {
      user.addPosition(i, i % 2 == 0 ? 0 : 100, null, 0, TokenType.ERC20);
    }

    final PositionReportMessage positionReport = makePositionReport(user);
    final byte[] data = encode(positionReport);
    System.out.println("data=" + StringUtil.fixToString(data));

    MsgType msgType = FixDecoderUtil.decodeMsgType(data);
    Assert.assertEquals(MsgType.POSITION_REPORT, msgType);

    // warmup
    for (int i = 0; i < 10_000; ++i) {
      msgType = FixDecoderUtil.decodeMsgType(data);
    }

    final int NUM_TESTS = 1_000_000;
    final long start = System.nanoTime();
    for (int i = 0; i < NUM_TESTS; ++i) {
      msgType = FixDecoderUtil.decodeMsgType(data);
    }



    final long elapsed = (System.nanoTime() - start) / NUM_TESTS;
    System.out.println("Done in " + format(elapsed) + " ms (" + format(1_000_000_000 / elapsed) + " msg/s)");
    System.out.println("counter=" + DecoderThreadCache.counter.get());
  }

  @Test
  public void measurePositionReportDecoderResetPerformance() {
    PositionReportDecoder decoder = new PositionReportDecoder();

    final long start = System.nanoTime();
    for (int i = 0; i < 1_000_00; ++i) {
      // decoder.reset();
    }

    final long elapsed = (System.nanoTime() - start) / 1_000_00;
    System.out
        .println("Done in " + format(elapsed) + " ms (" + format(1_000_000_000 / elapsed) + " calls/s, " + format(elapsed) + " ns/call)");
  }

  private PositionReportMessage makePositionReport(final User user) {
    Position[] positions = user.getPositionArr();
    return PositionReportMessage.createPositionReportMessage(Context.TX_RESTATE, user, "test", positions, positions.length, 0, 0);
  }

  private byte[] encode(final PositionReportMessage positionReportMessage) {
    final PositionReportEncoder positionReportEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    positionReportEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, positionReportMessage);
    encodedLength += headerEncoder.encodedLength();

    positionReportEncoder.userId(positionReportMessage.getUser() == null ? 0 : positionReportMessage.getUser().getId());
    positionReportEncoder.posReqResult(posReqResult);
    positionReportEncoder.transactTime(System.currentTimeMillis());

    final User user = positionReportMessage.getUser();
    final Position[] positionArr = positionReportMessage.getPositions();
    final int positionsLength = positionReportMessage.getPositionsLength();

    positionReportEncoder.settleCoinChange(0);
    positionReportEncoder.settleCoinChangeScale(SHORT_ZERO);

    // add user risk to first position
    // addRiskDataToPositionMessage(positionReportEncoder, user);

    // add position data
    // addPositionDataToPositionMessage(positionReportEncoder, user, positionArr, positionsLength);

    // convert and publish
    encodedLength += positionReportEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    return bytesWithKafkaOffset;
  }

  private void populateHeader(final MessageHeaderEncoder headerEncoder, final Message message) {
    final SessionInfo sessionInfo = SessionInfoCache.DEFAULT;
    headerEncoder.msgSeqNum(sessionInfo.incrementAndGetMessageSequenceNumber());
    headerEncoder.sendingTime(System.currentTimeMillis());
    headerEncoder.sourceSeqNum(message.getSourceSeqNum()); // sourceSeqNum
    headerEncoder.kafkaRecordOffset(message.getKafkaRecordOffset()); // kafkaRecordOffset
    headerEncoder.senderCompId(Context.getInstanceId()); // instance id
    headerEncoder.deliverToCompId(ME_SEQ_ID); // matching engine seq num
    headerEncoder.transactionId(message.getTransactionId());
    headerEncoder.transactionEnd((short) (message.isLastMessageInTransaction() ? 1 : 0));
  }

}
