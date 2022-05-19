package com.solfini.sbe;


import com.solfini.sbe.encoder.*;
import com.solfini.util.StringUtil;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

public class ExecutionReportEncoderTest {

  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;

  // encoder classes
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(16384);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private static final ExecutionReportEncoder executionReportEncoder = new ExecutionReportEncoder();

  private static final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private static final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private static final ExecutionReportDecoder executionReportDecoder = new ExecutionReportDecoder();

  private static final SecureRandom random = new SecureRandom();



  public static void main(String[] args) throws InterruptedException {
    byte[] bytesWithKafkaOffset = encode();
    System.out.println("bytesWithKafkaOffset:\n" + StringUtil.fixToString(bytesWithKafkaOffset));
    System.out.println("bytes len:\n" + bytesWithKafkaOffset.length);

    decode(bytesWithKafkaOffset);
  }

  private static byte[] encode() {
    short encodedLength = HEADER_LENGTH;
    executionReportEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, encodedLength, headerEncoder);


    executionReportEncoder.avgPx(1011L);
    executionReportEncoder.securityId(12);
    executionReportEncoder.clOrdID("ClOrdId");
    executionReportEncoder.symbol("BTC/USDT[F]");
    executionReportEncoder.side(Side.SELL);
    executionReportEncoder.ordType(OrdType.LIMIT);

    headerEncoder.sendingTime(System.currentTimeMillis());
    encodedLength += headerEncoder.encodedLength();

    encodedLength += executionReportEncoder.encodedLength();
    buffer.limit(encodedLength);
    encoderUnsafeBuffer.putShort(0, encodedLength);

    System.out.println("ENCODE");
    System.out.println("Header:\n" + headerEncoder);
    System.out.println("Encoder:\n" + executionReportEncoder);

    byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(encoderUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    return bytesWithKafkaOffset;
  }

  private static void decode(byte[] bytesWithKafkaOffset) {
    decoderUnsafeBuffer.wrap(bytesWithKafkaOffset);


    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);
    executionReportDecoder
      .wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

    final long avgPx = executionReportDecoder.avgPx();

    System.out.println("DECODE");
    System.out.println("AVG PX: " + avgPx);
    System.out.println("Header:\n" + headerDecoder);
    System.out.println("Decoder:\n" + executionReportDecoder);

  }

  private static <T extends Enum<?>> T randomEnum(Class<T> clazz) {
    int x = random.nextInt(clazz.getEnumConstants().length);
    return clazz.getEnumConstants()[x];
  }
}
