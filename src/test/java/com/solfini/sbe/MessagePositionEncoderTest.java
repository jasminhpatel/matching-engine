package com.solfini.sbe;


import java.nio.ByteBuffer;
import java.security.SecureRandom;
import org.agrona.concurrent.UnsafeBuffer;
import com.solfini.sbe.encoder.AssetType;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.PositionReportDecoder;
import com.solfini.sbe.encoder.PositionReportDecoder.PositionsGroupDecoder;
import com.solfini.sbe.encoder.PositionReportEncoder;
import com.solfini.sbe.encoder.PositionReportEncoder.PositionsGroupEncoder;
import com.solfini.util.StringUtil;

public class MessagePositionEncoderTest {

  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;

  // encoder classes
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(16384);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private static final PositionReportEncoder positionReportEncoder = new PositionReportEncoder();

  private static final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private static final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private static final PositionReportDecoder positionReportDecoder = new PositionReportDecoder();

  private static final SecureRandom random = new SecureRandom();



  public static void main(String[] args) throws InterruptedException {
    byte[] bytesWithKafkaOffset = encode();
    System.out.println("bytesWithKafkaOffset:\n" + StringUtil.fixToString(bytesWithKafkaOffset));
    System.out.println("bytes len:\n" + bytesWithKafkaOffset.length);

    decode(bytesWithKafkaOffset);

    // Thread.sleep(50000000);
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 1_000_000; i++) {
      byte[] bytesWithKafkaOffset2 = encode();
    }
    System.out.println("encoded t0=" + (System.currentTimeMillis() - t0));



    long t2 = System.currentTimeMillis();
    for (int i = 0; i < 1_000_000; i++) {
      decode(bytesWithKafkaOffset);
    }
    System.out.println("decoded t2=" + (System.currentTimeMillis() - t2));



  }

  private static byte[] encode() {
    short encodedLength = HEADER_LENGTH;
    positionReportEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, encodedLength, headerEncoder);


    positionReportEncoder.userId(181);
    positionReportEncoder.posReqResult(4044);

    int positionGroupCount = 128;

    PositionsGroupEncoder group = positionReportEncoder.positionsGroupCount(positionGroupCount);

    for (int i = 0; i < positionGroupCount; i++) {
      group = group.next();
      group.assetType(randomEnum(AssetType.class));
      group.instrumentId(11223 + i);
    }

    headerEncoder.sendingTime(System.currentTimeMillis());
    encodedLength += headerEncoder.encodedLength();

    encodedLength += positionReportEncoder.encodedLength();
    buffer.limit(encodedLength);
    encoderUnsafeBuffer.putShort(0, encodedLength);

    // System.out.println("ENCODE");
    // System.out.println("Header:\n" + headerEncoder);
    // System.out.println("Encoder:\n" + positionReportEncoder);

    byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(encoderUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    return bytesWithKafkaOffset;
  }

  private static void decode(byte[] bytesWithKafkaOffset) {
    decoderUnsafeBuffer.wrap(bytesWithKafkaOffset);


    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);
    positionReportDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
        headerDecoder.version());

    final int userId = positionReportDecoder.userId();
    final int posReqResult = positionReportDecoder.posReqResult();

    PositionsGroupDecoder group = positionReportDecoder.positionsGroup();
    AssetType assetType = group.assetType();
    int instrumentId = group.instrumentId();

    System.out.println("DECODE");
    System.out.println("Header:\n" + headerDecoder);
    System.out.println("Decoder:\n" + positionReportDecoder);
    // System.out.println("decoder.clientOrderId():\n" + executionReportDecoder.clOrdID());

  }

  private static <T extends Enum<?>> T randomEnum(Class<T> clazz) {
    int x = random.nextInt(clazz.getEnumConstants().length);
    return clazz.getEnumConstants()[x];
  }
}
