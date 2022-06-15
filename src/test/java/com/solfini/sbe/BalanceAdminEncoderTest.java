package com.solfini.sbe;

import com.solfini.internal.admin.schema.BalanceAdminMessageDecoder;
import com.solfini.internal.admin.schema.BalanceAdminMessageEncoder;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.PositionReportDecoder;
import com.solfini.sbe.encoder.PositionReportEncoder;
import com.solfini.util.StringUtil;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

public class BalanceAdminEncoderTest {
  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;
  protected static final short encodedLengthSize = 2;

  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(16384);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private static final BalanceAdminMessageEncoder balanceAdminMessageEncoder = new BalanceAdminMessageEncoder();

  private static final UnsafeBuffer unsafeBuffer = new UnsafeBuffer();
  private static final com.solfini.internal.admin.schema.MessageHeaderDecoder messageHeaderDecoder = new com.solfini.internal.admin.schema.MessageHeaderDecoder();
  private static final BalanceAdminMessageDecoder BALANCE_DECODER = new BalanceAdminMessageDecoder();

  private static final ByteBuffer messageBuffer = ByteBuffer.allocateDirect(32768);
  private static final MutableAsciiBuffer mab = new MutableAsciiBuffer(messageBuffer);

  private static final SecureRandom random = new SecureRandom();

  public static void main(String[] args) {
    byte[] encoded = encode();
    BalanceAdminMessage decoded = decode(encoded);
    System.out.println("Done");
  }

  private static byte[] encode() {
    short encodedLength = encodedLengthSize;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final BalanceAdminMessageEncoder balanceAdminMessageEncoder = new BalanceAdminMessageEncoder();
    final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    balanceAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    balanceAdminMessageEncoder.updateType(UpdateType.PATCH);
    balanceAdminMessageEncoder.userId(121);
    balanceAdminMessageEncoder.feeTier(0);
    balanceAdminMessageEncoder.txType(1);
    balanceAdminMessageEncoder.txId(1223121);

    com.solfini.internal.admin.schema.BalanceAdminMessageEncoder.BalanceGroupEncoder balanceGroupEncoder =
        balanceAdminMessageEncoder.balanceGroupCount(1);
    balanceGroupEncoder = balanceGroupEncoder.next();
    balanceGroupEncoder.assetId(1).balance().value(120).scale(0);

    com.solfini.internal.admin.schema.BalanceAdminMessageEncoder.BalanceGroupEncoder.PositionsAssetIdGroupEncoder assetIdGroupEncoder =
        balanceGroupEncoder.positionsAssetIdGroupCount(2);
    assetIdGroupEncoder.next().assetId(1).tokenId(101);
    assetIdGroupEncoder.next().assetId(2).tokenId(102);

    encodedLength += balanceAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < encodedLength; i++) {
      sb.append((int) adminMessageBuffer.get(i)).append(",");
    }

    final byte[] bytes = StringUtil.bufferToArray(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

    return bytes;
  }

  private static BalanceAdminMessage decode(byte[] encoded) {
    int offset = 0;
    final int length = encoded.length - OFFSET;
    buffer.clear();
    buffer.put(encoded, OFFSET, length);

    mab.wrap(buffer, 0, length);
    messageHeaderDecoder.wrap(mab, offset);

    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);
    messageHeaderDecoder.wrap(unsafeBuffer, 0);
    int connectionId = 0;
    BALANCE_DECODER.wrap(unsafeBuffer, messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    return new BalanceAdminMessage(BALANCE_DECODER, connectionId);
  }
}
