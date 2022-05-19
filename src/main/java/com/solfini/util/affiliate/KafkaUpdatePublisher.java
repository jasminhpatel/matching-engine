package com.solfini.util.affiliate;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import com.solfini.common.Constants;
import com.solfini.internal.admin.schema.BalanceAdminMessageEncoder;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.admin.schema.UserAdminMessageEncoder;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;

import org.agrona.concurrent.UnsafeBuffer;

public class KafkaUpdatePublisher extends KafkaPublisher {

  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;

  public KafkaUpdatePublisher() {
    super(0);
    setTopic(PropertyReader.getProperty("API_KAFKA_TOPIC_IN", "me1"));
  }

  @Override
  public void flush() {
    getProducer().flush();
  }

  public long sendFeeTierUpdate(final int userId, final int feeTier)
      throws ExecutionException, InterruptedException {
    short encodedLength = HEADER_LENGTH;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final UserAdminMessageEncoder userAdminMessageEncoder = new UserAdminMessageEncoder();
    final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();

    userAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    userAdminMessageEncoder.updateType(UpdateType.PATCH);
    userAdminMessageEncoder.patchType(PATCH_FEE_TIER);
    userAdminMessageEncoder.userId(userId);
    userAdminMessageEncoder.feeTier(feeTier);

    encodedLength += userAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return sendDirect(
      getTopic(),
      StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET),
      KafkaPublisher.ADMIN_API).get().offset();
  }

  public long sendBalanceAdjustment(final int userId, final int securityId, final long amount, final short scale)
      throws ExecutionException, InterruptedException {
    short encodedLength = HEADER_LENGTH;
    ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    BalanceAdminMessageEncoder balanceAdminMessageEncoder = new BalanceAdminMessageEncoder();
    com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    balanceAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    balanceAdminMessageEncoder.updateType(UpdateType.PATCH);
    balanceAdminMessageEncoder.userId(userId);
    balanceAdminMessageEncoder.txType(Constants.TX_ADJUSTMENT);
    balanceAdminMessageEncoder.txId(1);

    com.solfini.internal.admin.schema.BalanceAdminMessageEncoder.BalanceGroupEncoder balanceGroupEncoder =
        balanceAdminMessageEncoder.balanceGroupCount(1);
    balanceGroupEncoder.next().assetId(securityId).balance().value(amount).scale(scale);

    encodedLength += balanceAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return sendDirect(
      getTopic(),
      StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET),
      KafkaPublisher.ADMIN_API).get().offset();
  }
}
