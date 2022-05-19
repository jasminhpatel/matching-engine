package com.solfini.matchengine.message.outbound;

import java.util.Arrays;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.Position;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.persist.Persister;
import com.solfini.matchengine.persist.PersisterPosition;
import com.solfini.matchengine.publisher.PositionReportEncoderCache;
import com.solfini.pool.PersistPositionReportObjectPool;
import com.solfini.pool.PositionReportObjectPool;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.PositionReportDecoder;
import com.solfini.user.User;
import com.solfini.user.UserStats;

/**
 *
 * @author Chris Mack
 *
 */
public class PositionReportMessage extends Message implements Constants {
  private long timestamp = 0;
  private Position[] positions;
  private int txnType;
  private long execId;
  private long orderId;
  private long txnId;

  private int positionsLength = 0; // actual length, since arr is buffered
  public static final double USD_RISK_MULT = 0.01;

  public static PositionReportMessage createPositionReportMessage(final int txnType, final User user, final String senderCompId,
      final Position[] positions, final int positionsLength, final long execId, final long orderId) {
    return createPositionReportMessage(PositionReportObjectPool.get(), txnType, user, senderCompId, positions, positionsLength, execId,
        orderId);
  }

  public static PositionReportMessage createPositionReportMessage(final PositionReportMessage positionReportMessage, final int txnType,
      final User user, final String senderCompId, final Position[] positions, final int positionsLength, final long execId,
      final long orderId) {
    positionReportMessage.txnType = txnType;
    positionReportMessage.user = user;
    positionReportMessage.positions = positions == null ? new Position[0] : positions;
    positionReportMessage.positionsLength = positionsLength;
    positionReportMessage.senderCompId = senderCompId;
    positionReportMessage.execId = execId;
    positionReportMessage.orderId = orderId;

    return positionReportMessage;
  }

  public static final PositionReportMessage createFromDecoder(final MessageHeaderDecoder headerDecoder, final PositionReportDecoder decoder,
      final BalanceAdminMessage balanceAdminMessage) {

    final User user = new User(decoder.userId());
    final double usdValue = ((double) decoder.usdValue()) * USD_RISK_MULT;
    final double usdNotionalPositionValue = ((double) decoder.usdNotionalPositionValue()) * USD_RISK_MULT;
    final double usdMarginMaintValue = ((double) decoder.usdMarginMaintValue()) * USD_RISK_MULT;
    final double usdMarginRequiredValue = ((double) decoder.usdMarginRequiredValue()) * USD_RISK_MULT;
    final double leverageRatio = ((double) decoder.leverageRatio()) * USD_RISK_MULT;
    final double usdUnrealized = ((double) decoder.usdUnrealized()) * USD_RISK_MULT;
    final double usdMarginValue = ((double) decoder.usdMarginValue()) * USD_RISK_MULT;
    final double usdOpenOrdersRequiredValue = ((double) decoder.usdOpenOrdersRequiredValue()) * USD_RISK_MULT;
    final double usdMaxExposurePositionAndOpenOrdersValue = ((double) decoder.usdMaxExposurePositionAndOpenOrdersValue()) * USD_RISK_MULT;
    final int txnType = decoder.posReqResult();
    final long execId = decoder.execId();
    final long orderId = decoder.orderId();
    final long txnId = decoder.txnId();

    user.setUsdValue(usdValue);
    user.setUsdNotionalPositionValue(usdNotionalPositionValue);
    user.setUsdMaxExposurePositionAndOpenOrdersValue(usdMaxExposurePositionAndOpenOrdersValue);
    user.setUsdOpenOrdersRequiredValue(usdOpenOrdersRequiredValue);
    user.setUsdMarginValue(usdMarginValue);
    user.setUsdMarginRequiredValue(usdMarginRequiredValue);
    user.setUsdMarginMaintValue(usdMarginMaintValue);
    user.setLeverageRatio(leverageRatio);
    user.setUsdUnrealized(usdUnrealized);
    user.override(balanceAdminMessage);

    final Position[] positions = clonePositionArr(user);
    final PositionReportMessage message = createPositionReportMessage(PersistPositionReportObjectPool.get(), txnType, user,
        headerDecoder.senderCompId(), positions, positions.length, execId, orderId);
    message.setTxnId(txnId);
    message.setTimestamp(decoder.transactTime());
    message.setSequenceNumber(headerDecoder.msgSeqNum());
    message.setSourceSeqNum(headerDecoder.sourceSeqNum());
    message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
    message.setTransactionId(headerDecoder.transactionId());
    message.setLastMessageInTransaction(headerDecoder.transactionEnd() != 0);

    return message;
  }

  private static final Position[] clonePositionArr(final User user) {
    final Position[] positionArr = user.getPositionArr();
    Position[] cloneArr = new Position[16];

    int cloneIndex = 1;
    for (int i = 0; i < positionArr.length; i++) {
      final Position position = positionArr[i];
      if (position == null)
        continue;
      if (position.getQuantity() == 0 && !position.isTouched() && i > 3)
        continue;

      if (cloneIndex >= cloneArr.length) {
        cloneArr = Arrays.copyOf(cloneArr, positionArr.length);
      }

      cloneArr[cloneIndex] = Position.set(new Position(), user, positionArr[i]);
      cloneIndex++;
    }

    return Arrays.copyOf(cloneArr, cloneIndex);
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public final MessageType getMessageType() {
    return MessageType.POSITION_REPORT;
  }

  public final Position[] getPositions() {
    return positions;
  }

  public final int getPositionsLength() {
    return positionsLength;
  }

  public final long getTimestamp() {
    return timestamp;
  }

  public final void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  public final int getTxnType() {
    return txnType;
  }

  public final void setTxnType(final int txnType) {
    this.txnType = txnType;
  }

  public final long getExecId() {
    return execId;
  }

  public final void setExecId(final long execId) {
    this.execId = execId;
  }

  public final long getOrderId() {
    return orderId;
  }

  public final void setOrderId(final long orderId) {
    this.orderId = orderId;
  }

  public final long getTxnId() {
    return txnId;
  }

  public final void setTxnId(final long txnId) {
    this.txnId = txnId;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    return s.append(POSITIONREPORTMESSAGE_USER_EQ).append((user == null ? 0 : user.getId())).append("POSITIONS_EQ").append(KAFKA_OFFSET_EQ)
        .append(kafkaRecordOffset).append(Arrays.toString(positions)).append(']');
  }

  @Override
  public final void onPublish() {
    final PositionReportEncoderCache positionReportEncoderCache = PositionReportEncoderCache.get();
    Context.getMessagePublisher().publish(this, DEFAULT_POS_RPT, positionReportEncoderCache);
  }

  @Override
  public final void onPersist() {
    if (Context.isUserStatsEnabled()) {
      UserStats.onMessage(this);
    }

    PersisterPosition.onMessage(this);
  }

  @Override
  public void onEncodeBufferedPublish() {
    // Not required
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"PositionReportMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(",\"positionArr\":[");
    if (positions != null) {
      boolean comma = false;
      for (int i = 0; i < positions.length; i++) {
        if (comma)
          sb.append(",");
        if (positions[i] != null) {
          sb.append(positions[i].toJSON());
          comma = true;
        }
      }
    }
    sb.append("]}");
    return sb.toString();
  }

}
