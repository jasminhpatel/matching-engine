package com.solfini.matchengine.publisher;

import java.nio.ByteBuffer;
import org.junit.Test;

import com.solfini.common.Context;
import com.solfini.matchengine.kafka.TestKafkaPublisher;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.user.User;
import com.solfini.util.StringUtil;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

public class ByteBufferTest {
  public ByteBufferTest() {

  }

  @Test
  public void run() throws Exception {
    final byte[] data = {56, 61, 70, 73, 88, 46, 52, 46, 52, 1, 57, 61, 51, 49, 52, 1, 51, 53, 61, 65, 80, 1, 52, 57, 61, 109, 101, 48, 49,
        1, 53, 54, 61, 49, 48, 48, 48, 48, 48, 48, 48, 48, 57, 1, 49, 50, 56, 61, 49, 1, 51, 52, 61, 50, 1, 53, 48, 61, 48, 1, 49, 52, 50,
        61, 48, 1, 53, 50, 61, 50, 48, 49, 57, 48, 55, 49, 50, 45, 49, 56, 58, 51, 56, 58, 48, 55, 46, 55, 54, 51, 1, 55, 50, 49, 61, 48, 1,
        55, 50, 56, 61, 48, 1, 55, 49, 53, 61, 50, 48, 49, 57, 48, 55, 49, 50, 45, 49, 56, 58, 51, 56, 58, 48, 54, 46, 51, 54, 48, 1, 49,
        61, 49, 48, 48, 48, 48, 48, 48, 48, 53, 48, 1, 53, 56, 49, 61, 49, 1, 53, 53, 61, 48, 1, 55, 51, 48, 61, 48, 1, 55, 51, 49, 61, 48,
        1, 55, 51, 52, 61, 48, 1, 55, 48, 50, 61, 49, 48, 1, 55, 48, 51, 61, 48, 95, 48, 1, 55, 48, 52, 61, 48, 1, 55, 48, 51, 61, 48, 95,
        49, 1, 55, 48, 52, 61, 48, 1, 55, 48, 51, 61, 48, 95, 50, 1, 55, 48, 52, 61, 48, 1, 55, 48, 51, 61, 48, 95, 51, 1, 55, 48, 52, 61,
        48, 1, 55, 48, 51, 61, 48, 95, 52, 1, 55, 48, 52, 61, 48, 1, 55, 48, 51, 61, 48, 95, 53, 1, 55, 48, 52, 61, 48, 1, 55, 48, 51, 61,
        48, 95, 54, 1, 55, 48, 52, 61, 48, 1, 55, 48, 51, 61, 48, 95, 55, 1, 55, 48, 52, 61, 48, 1, 55, 48, 51, 61, 48, 95, 56, 1, 55, 48,
        52, 61, 48, 1, 55, 48, 51, 61, 48, 95, 57, 1, 55, 48, 52, 61, 49, 53, 54, 50, 57, 53, 54, 54, 56, 55, 55, 53, 56, 1, 49, 48, 61, 48,
        51, 57, 1};
    final ByteBuffer messageBuffer = ByteBuffer.allocateDirect(16384);
    final MutableAsciiBuffer mutableAsciiBuffer = new MutableAsciiBuffer(messageBuffer);

    final int length = 337;
    final int KAFKA_OFFSET = 17;

    // Shift message to start of buffer
    mutableAsciiBuffer.putBytes(0, data);
    mutableAsciiBuffer.byteBuffer().limit(length);

    final byte[] bytes = StringUtil.bufferToArray(mutableAsciiBuffer.byteBuffer(), length, KAFKA_OFFSET);
    final byte[] bytes2 = StringUtil.bufferToArrayBulk(mutableAsciiBuffer.byteBuffer(), length, KAFKA_OFFSET);
    // final byte[] bytes3 = StringUtil.bufferToArray3(mutableAsciiBuffer.byteBuffer(), length, KAFKA_OFFSET);

    System.out.println("Done bytes =" + bytes);
    System.out.println("Done bytes2 =" + bytes2);
    // System.out.println("Done bytes3 =" + bytes3);


    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 1_000_000; i++) {
      final byte[] bytes5 = StringUtil.bufferToArray(mutableAsciiBuffer.byteBuffer(), length, KAFKA_OFFSET);
    }
    System.out.println("t0=" + (System.currentTimeMillis() - t0));

    long t2 = System.currentTimeMillis();
    for (int i = 0; i < 1_000_000; i++) {
      final byte[] bytes6 = StringUtil.bufferToArray(mutableAsciiBuffer.byteBuffer(), length, KAFKA_OFFSET);
    }
    System.out.println("t2=" + (System.currentTimeMillis() - t2));

    long t3 = System.currentTimeMillis();
    for (int i = 0; i < 1_000_000; i++) {
      final byte[] bytes7 = StringUtil.bufferToArray(mutableAsciiBuffer.byteBuffer(), length, KAFKA_OFFSET);
    }
    System.out.println("t3=" + (System.currentTimeMillis() - t3));

    long t4 = System.currentTimeMillis();
    for (int i = 0; i < 1_000_000; i++) {
      final byte[] bytes8 = StringUtil.bufferToArray(mutableAsciiBuffer.byteBuffer(), length, KAFKA_OFFSET);
    }
    System.out.println("t4=" + (System.currentTimeMillis() - t4));
  }
}
