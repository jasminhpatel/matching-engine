package com.solfini.sbe;


import com.solfini.sbe.encoder.ExecutionReportDecoder;
import com.solfini.sbe.encoder.ExecutionReportEncoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.util.StringUtil;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MessageEncoderTest {

  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;

  // encoder classes
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private static final ExecutionReportEncoder executionReportEncoder = new ExecutionReportEncoder();

  private static final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private static final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private static final ExecutionReportDecoder executionReportDecoder = new ExecutionReportDecoder();



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
    executionReportEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, encodedLength, headerEncoder);
    headerEncoder.sendingTime(System.currentTimeMillis());
    encodedLength += headerEncoder.encodedLength();

    executionReportEncoder.clOrdID("clid500");

    encodedLength += executionReportEncoder.encodedLength();
    buffer.limit(encodedLength);
    encoderUnsafeBuffer.putShort(0, encodedLength);

    // System.out.println("ENCODE");
    // System.out.println("Header:\n" + headerEncoder);
    // System.out.println("Encoder:\n" + executionReportEncoder);

    byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(encoderUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    return bytesWithKafkaOffset;
  }

  private static void decode(byte[] bytesWithKafkaOffset) {
    decoderUnsafeBuffer.wrap(bytesWithKafkaOffset);


    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);
    executionReportDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
        headerDecoder.version());

    final String s = executionReportDecoder.clOrdID();
    final long price = executionReportDecoder.price();
    final long orderQty = executionReportDecoder.orderQty();
    final int userId = executionReportDecoder.userId();


    // System.out.println("DECODE");
    // System.out.println("Header:\n" + headerDecoder);
    // System.out.println("Decoder:\n" + executionReportDecoder);
    // System.out.println("decoder.clientOrderId():\n" + executionReportDecoder.clOrdID());

  }
}
