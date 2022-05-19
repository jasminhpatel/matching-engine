package com.solfini.integration;

import java.nio.ByteBuffer;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.BalanceAdminMessageEncoder;
import com.solfini.internal.admin.schema.GlobalStateAdminMessageEncoder;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.MessageHeaderEncoder;
import com.solfini.internal.admin.schema.SecurityDefinitionAdminMessageEncoder;
import com.solfini.internal.admin.schema.TradeStateAdminMessageEncoder;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.admin.schema.UserAdminMessageEncoder;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.GlobalStateAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.TradeStateAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.publisher.SessionInfoCache;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.sbe.encoder.ExecutionReportEncoder;
import com.solfini.sbe.encoder.NewOrderSingleEncoder;
import com.solfini.util.LogLevel;
import com.solfini.util.StringUtil;

public class Publisher {
  private static final short HEADER_LENGTH = 2;
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;

  private static final short ENCODED_LENGTH_SIZE = 2;
  private final String topic;
  private static final int ME_SEQ_ID = 1;

  // encoder classes
  private static final ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
  private static final UnsafeBuffer encoderUnsafeBuffer = new UnsafeBuffer(buffer);
  private static final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder =
      new com.solfini.sbe.encoder.MessageHeaderEncoder();
  private static final ExecutionReportEncoder executionReportEncoder = new ExecutionReportEncoder();
  private static final NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();


  public Publisher(final String topic) {
    Log.info("Publish to topic: " + topic);
    this.topic = topic;
  }

  public void send(final UserAdminMessage message) throws Exception {

    short encodedLength = ENCODED_LENGTH_SIZE;
    ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    UserAdminMessageEncoder messageEncoder = new UserAdminMessageEncoder();
    MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    messageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    messageEncoder.updateType(UpdateType.PUT);
    messageEncoder.userId(message.getUserId());
    messageEncoder.username(message.getUsername());
    messageEncoder.password(message.getPassword());
    messageEncoder.feeTier(message.getFeeTier());
    messageEncoder.firmId(message.getFirmId());
    messageEncoder.lmm((short) (message.isLmm() ? 1 : 0));

    encodedLength += messageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    publish(message, adminMessageUnsafeBuffer, encodedLength, KafkaPublisher.ADMIN_API);
  }

  public void send(final BalanceAdminMessage message) throws Exception {

    short encodedLength = ENCODED_LENGTH_SIZE;
    ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    BalanceAdminMessageEncoder messageEncoder = new BalanceAdminMessageEncoder();
    MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    messageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    messageEncoder.updateType(UpdateType.PUT);
    messageEncoder.userId(message.getUserId());
    messageEncoder.feeTier(message.getFeeTier());
    messageEncoder.txType(message.getTxType());
    messageEncoder.txId(message.getTxId());

    List<Balance> balanceList = message.getBalanceList();
    BalanceAdminMessageEncoder.BalanceGroupEncoder balanceGroupEncoder = messageEncoder.balanceGroupCount(balanceList.size());
    for (final Balance balance : balanceList) {
      balanceGroupEncoder.next().assetId(balance.getAssetId()).balance().value(balance.getBalance().value())
          .scale(balance.getBalance().scale());
    }

    encodedLength += messageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    publish(message, adminMessageUnsafeBuffer, encodedLength, KafkaPublisher.ADMIN_API);
  }

  public void send(final SecurityDefinitionAdminMessage message) throws Exception {
    short encodedLength = ENCODED_LENGTH_SIZE;
    ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    SecurityDefinitionAdminMessageEncoder messageEncoder = new SecurityDefinitionAdminMessageEncoder();
    MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    messageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, messageHeaderEncoder);
    encodedLength += messageHeaderEncoder.encodedLength();

    messageEncoder.updateType(UpdateType.PUT);
    messageEncoder.securityId(message.getSecurityId());
    messageEncoder.assetType(message.getAssetType());
    messageEncoder.symbol(message.getSymbol());
    messageEncoder.name(message.getName());
    messageEncoder.quantityScale((short) message.getQuantityScale());
    messageEncoder.priceScale((short) message.getPriceScale());
    if (!message.getAssetType().equals(AssetType.ASSET)) {
      messageEncoder.quotedId(message.getQuotedId());
      messageEncoder.baseId(message.getBaseId());
    }
    messageEncoder.maintMarginPercent(message.getMaintMarginBasisPoints());
    messageEncoder.requiredMarginPercent(message.getRequiredMarginBasisPoints());
    messageEncoder.orderBookStrategy((short) message.getOrderBookStrategy());
    messageEncoder.preOrderCheckStrategy((short) message.getPreOrderCheckStrategy());
    messageEncoder.indexFeedUsdMark(); // TODO check

    encodedLength += messageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    publish(message, adminMessageUnsafeBuffer, encodedLength, KafkaPublisher.ADMIN_API);
  }

  public void send(final GlobalStateAdminMessage message) throws Exception {
    short encodedLength = ENCODED_LENGTH_SIZE;
    ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    GlobalStateAdminMessageEncoder messageEncoder = new GlobalStateAdminMessageEncoder();
    MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    messageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, messageHeaderEncoder);
    encodedLength += messageHeaderEncoder.encodedLength();

    messageEncoder.liquidationMode(message.getLiquidationMode());

    encodedLength += messageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    publish(message, adminMessageUnsafeBuffer, encodedLength, KafkaPublisher.ADMIN_API);
  }


  public void send(final TradeStateAdminMessage message) throws Exception {
    try {
      short encodedLength = ENCODED_LENGTH_SIZE;
      final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
      final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

      final TradeStateAdminMessageEncoder tradeStateAdminMessageEncoder = new TradeStateAdminMessageEncoder();
      com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
          new com.solfini.internal.admin.schema.MessageHeaderEncoder();
      tradeStateAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
      encodedLength += headerEncoder.encodedLength();

      tradeStateAdminMessageEncoder.marketStatus(MarketStatus.RESTATE); // MarketStatus.RESTATE
      // tradeStateAdminMessageEncoder.securityId(tradeStateAdminMessage.getSecurityId());
      // tradeStateAdminMessageEncoder.triggerTimeMillis(tradeStateAdminMessage.getTriggerTimeMillis());
      tradeStateAdminMessageEncoder.routeToDestination(message.getRouteToDestination()); // matching-engine-01

      // tradeStateAdminMessageEncoder.externalId(tradeStateAdminMessage.getExternalId());
      // tradeStateAdminMessageEncoder.sourceSeqNum(tradeStateAdminMessage.getSourceSeqNum()); // sourceSeqNum
      // tradeStateAdminMessageEncoder.kafkaRecordOffset(tradeStateAdminMessage.getKafkaRecordOffset()); // kafkaRecordOffset

      encodedLength += tradeStateAdminMessageEncoder.encodedLength();
      adminMessageBuffer.limit(encodedLength);
      adminMessageUnsafeBuffer.putShort(0, encodedLength);

      if (LogLevel.debug()) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < encodedLength; i++) {
          sb.append((int) adminMessageBuffer.get(i)).append(",");
        }
        Log.debug(
            ">>> sendStateAdmin encodedLength=" + encodedLength + ", sb=" + sb.toString() + ", adminMessageBuffer=" + adminMessageBuffer);
      }

      publish(message, adminMessageUnsafeBuffer, encodedLength, KafkaPublisher.ADMIN_API);
    } catch (Exception e) {
      Log.info("error");
      // e.printStackTrace();
    }
  }

  private void populateHeader(final com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder, final Message message) {
    final SessionInfo sessionInfo = SessionInfoCache.DEFAULT;

    headerEncoder.msgSeqNum(sessionInfo.incrementAndGetMessageSequenceNumber());
    headerEncoder.sendingTime(System.currentTimeMillis());
    headerEncoder.sourceSeqNum(message.getSourceSeqNum()); // sourceSeqNum
    headerEncoder.kafkaRecordOffset(message.getKafkaRecordOffset()); // kafkaRecordOffset
    headerEncoder.senderCompId(Context.getInstanceId()); // instance id
    headerEncoder.deliverToCompId(ME_SEQ_ID); // matching engine seq num
  }

  public void send(final Order message) throws Exception {
    short encodedLength = HEADER_LENGTH;
    newOrderSingleEncoder.wrapAndApplyHeader(encoderUnsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, message);
    headerEncoder.sendingTime(System.currentTimeMillis());
    encodedLength += headerEncoder.encodedLength();

    // set
    newOrderSingleEncoder.clOrdID(message.getClOrdId());
    newOrderSingleEncoder.securityId(message.getSecurityId());
    //newOrderSingleEncoder.symbol("ABCPQR");
    newOrderSingleEncoder.side(message.getSide());
    newOrderSingleEncoder.ordType(message.getOrdType());
    newOrderSingleEncoder.price(message.getPrice());
    newOrderSingleEncoder.priceScale(message.getPriceScale());
    newOrderSingleEncoder.qty(message.getQty());
    newOrderSingleEncoder.qtyScale(message.getQtyScale());
    newOrderSingleEncoder.userId(message.getUser().getId());
    // newOrderSingleEncoder.transactTime(timestamp);

    encodedLength += newOrderSingleEncoder.encodedLength();
    buffer.limit(encodedLength);
    encoderUnsafeBuffer.putShort(0, encodedLength);

    // System.out.println("ENCODE");
    // System.out.println("Header:\n" + headerEncoder);
    // System.out.println("Encoder:\n" + executionReportEncoder);

    byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(encoderUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    publish(message, bytesWithKafkaOffset, KafkaPublisher.NORMAL_API);
  }



  public void send(final MassCancelOrder message) throws Exception {

    short encodedLength = ENCODED_LENGTH_SIZE;
    ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    BalanceAdminMessageEncoder messageEncoder = new BalanceAdminMessageEncoder();
    MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    messageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    messageEncoder.userId(message.getUser().getId());
    messageEncoder.senderInstanceId("EXEC");

    encodedLength += messageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    publish(message, adminMessageUnsafeBuffer, encodedLength, KafkaPublisher.ADMIN_API);
  }

  private void publish(final Message message, final UnsafeBuffer buffer, final short encodedLength, final byte messageType)
      throws Exception {
    Log.debug("TX (" + topic + "): " + message);

    byte[] bytes = StringUtil.bufferToArrayBulk(buffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    Context.getKafkaPublisher().sendDirect(topic, bytes, messageType);
  }

  private void publish(final Message message, final byte[] bytes, final byte messageType) throws Exception {
    Log.debug("TX (" + topic + "): " + message);

    Context.getKafkaPublisher().sendDirect(topic, bytes, messageType);
  }
}
