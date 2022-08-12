package com.solfini.sbe;

import com.solfini.common.Context;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.sbe.encoder.AssetGroupDecoder;
import com.solfini.sbe.encoder.AssetGroupEncoder;
import com.solfini.sbe.encoder.ExecutionReportDecoder;
import com.solfini.sbe.encoder.ExecutionReportEncoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.util.StringUtil;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

public class AssetGroupEncoderTest {
  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;
  protected static final AtomicInteger msgSeqNum = new AtomicInteger(1);
  // encoder classes
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  private static final ByteBuffer messageBuffer = ByteBuffer.allocateDirect(32768);
  private static final MutableAsciiBuffer mab = new MutableAsciiBuffer(messageBuffer);

  private static final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private static final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private static final AssetGroupDecoder assetGroupDecoder = new AssetGroupDecoder();

  public static void main(String[] args) {
    final AssetGroup assetGroup = new AssetGroup();
    assetGroup.setUpdateType(com.solfini.sbe.encoder.UpdateType.POST);
    assetGroup.setGroupAssetId(1);
    assetGroup.setOwnerUserId(2);
    assetGroup.setName("Test GroupName");
    assetGroup.addAssetId(101, 1);

    byte[] encoded = encode(assetGroup);

    AssetGroup decoded = decode(encoded);
    System.out.println("Done");
  }

  private static byte[] encode(final AssetGroup assetGroup) {
    try {
      final AssetGroupEncoder assetGroupEncoder = new AssetGroupEncoder();
      short encodedLength = HEADER_LENGTH;
      assetGroupEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, encodedLength, headerEncoder);
      populateHeader(headerEncoder);
      headerEncoder.sendingTime(System.currentTimeMillis());

      encodedLength += headerEncoder.encodedLength();

      // set
      assetGroupEncoder.updateType(assetGroup.getUpdateType());
      assetGroupEncoder.id(assetGroup.getId());
      assetGroupEncoder.ownerUserId(assetGroup.getOwnerUserId());
      assetGroupEncoder.groupAssetId(assetGroup.getGroupAssetId());
      assetGroupEncoder.securityId(assetGroup.getSecurityId());
      assetGroupEncoder.updateType(assetGroup.getUpdateType());
      assetGroupEncoder.name(assetGroup.getName());

      final ConcurrentSkipListSet<long[]> assetIdGroup = assetGroup.getAssetIdGroupTreeSet();
      AssetGroupEncoder.PositionsAssetIdGroupEncoder positionsAssetIdGroupEncoder = assetGroupEncoder.positionsAssetIdGroupCount(assetIdGroup.size());
      for (final long[] assetTokenId : assetIdGroup) {
        positionsAssetIdGroupEncoder.next();
        positionsAssetIdGroupEncoder.assetId(assetTokenId[0]);
        positionsAssetIdGroupEncoder.tokenId((int) assetTokenId[1]);
      }

      encodedLength += assetGroupEncoder.encodedLength();
      buffer.limit(encodedLength);
      encoderUnsafeBuffer.putShort(0, encodedLength);

      // System.out.println("ENCODE");
      // System.out.println("Header:\n" + headerEncoder);
      // System.out.println("Encoder:\n" + executionReportEncoder);
      return StringUtil.bufferToArrayBulk(encoderUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private static AssetGroup decode(final byte[] encoded) {
    int offset = 0;
    final int length = encoded.length - OFFSET;
    buffer.clear();
    buffer.put(encoded, OFFSET, length);

    mab.wrap(buffer, 0, length);
    headerDecoder.wrap(mab, offset);

    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);
    headerDecoder.wrap(unsafeBuffer, 0);
    int connectionId = 0;
    assetGroupDecoder.wrap(unsafeBuffer, headerDecoder.encodedLength(), headerDecoder.blockLength(),
        headerDecoder.version());
    final AssetGroup assetGroup = new AssetGroup();
    assetGroup.set(assetGroupDecoder);

    return assetGroup;
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
