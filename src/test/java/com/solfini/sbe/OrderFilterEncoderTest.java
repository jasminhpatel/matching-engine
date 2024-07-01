package com.solfini.sbe;

import com.solfini.matchengine.message.internal.OrderFilter;
import com.solfini.sbe.encoder.*;
import com.solfini.util.StringUtil;
import org.agrona.collections.LongHashSet;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderFilterEncoderTest {
  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;
  protected static final AtomicInteger msgSeqNum = new AtomicInteger(1);
  // encoder classes
  private static int bufferSize = 4096;//32768;
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  private static final ByteBuffer messageBuffer = ByteBuffer.allocateDirect(bufferSize);
  private static final MutableAsciiBuffer mab = new MutableAsciiBuffer(messageBuffer);

  private static final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private static final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private static final OrderFilterDecoder orderFilterDecoder = new OrderFilterDecoder();

  /*public static void main(String[] args) {
    int filterSize = 400;
    final OrderFilter orderFilter = new OrderFilter();
    orderFilter.setOrderId("12313123123");
    orderFilter.setSecurityId(287);
    orderFilter.setSide(Side.BUY);
    orderFilter.setUserId(2);
    for (int i = 10000000; i < 10000000 + filterSize; i++) {
      orderFilter.addOrderId(i, filterSize);
    }

    byte[] encoded = encode(orderFilter);

    OrderFilter decoded = decode(encoded);
    for (int i = 10000000; i < 10000000 + filterSize; i++) {
      if(!decoded.getOrderIdGroup().contains(i)) {
        System.out.println("ID missing: " + i);
      }
    }
    System.out.println("Done. size: " + decoded.getOrderIdGroup().size());
  }

  private static byte[] encode(final OrderFilter orderFilter) {
    try {
      final OrderFilterEncoder orderFilterEncoder = new OrderFilterEncoder();
      short encodedLength = HEADER_LENGTH;
      orderFilterEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, encodedLength, headerEncoder);
      populateHeader(headerEncoder);
      headerEncoder.sendingTime(System.currentTimeMillis());

      encodedLength += headerEncoder.encodedLength();

      // set
      orderFilterEncoder.orderId(orderFilter.getOrderId());
      orderFilterEncoder.securityId(orderFilter.getSecurityId());
      orderFilterEncoder.side(orderFilter.getSide());
      orderFilterEncoder.userId(orderFilter.getUserId());


      final LongHashSet orderIdGroup = orderFilter.getOrderIdGroup();
      OrderFilterEncoder.OrderIdGroupEncoder orderIdGroupEncoder = orderFilterEncoder.orderIdGroupCount(orderIdGroup.size());
      LongHashSet.LongIterator iterator = orderIdGroup.iterator();
      while (iterator.hasNext()) {
        orderIdGroupEncoder.next();
        orderIdGroupEncoder.orderId(iterator.next());
      }

      encodedLength += orderFilterEncoder.encodedLength();
      buffer.limit(encodedLength);
      encoderUnsafeBuffer.putShort(0, encodedLength);

      return StringUtil.bufferToArrayBulk(encoderUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }*/

  private static OrderFilter decode(final byte[] encoded) {
    int offset = 0;
    final int length = encoded.length - OFFSET;
    buffer.clear();
    buffer.put(encoded, OFFSET, length);

    mab.wrap(buffer, 0, length);
    headerDecoder.wrap(mab, offset);

    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);
    headerDecoder.wrap(unsafeBuffer, 0);
    int connectionId = 0;
    orderFilterDecoder.wrap(unsafeBuffer, headerDecoder.encodedLength(), headerDecoder.blockLength(),
        headerDecoder.version());
    final OrderFilter orderFilter = new OrderFilter();
    orderFilter.set(orderFilterDecoder);

    return orderFilter;
  }

  protected static void populateHeader(final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder) {
    headerEncoder.msgSeqNum(msgSeqNum.incrementAndGet());
    headerEncoder.sendingTime(System.currentTimeMillis());
    headerEncoder.sourceSeqNum(0); // sourceSeqNum
    headerEncoder.kafkaRecordOffset(0); // kafkaRecordOffset
    headerEncoder.senderCompId(""); // instance id
    headerEncoder.deliverToCompId(0); // matching engine seq num
  }
}
