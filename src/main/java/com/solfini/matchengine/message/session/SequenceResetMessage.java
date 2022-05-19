package com.solfini.matchengine.message.session;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.internal.schema.PayloadType;

/**
 *
 * @author Chris Mack
 *
 */
public class SequenceResetMessage extends Message {
  private long newSeqNo;
  private boolean gapFillFlag;

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.SEQUENCE_RESET;
  }

  public final long getNewSeqNo() {
    return newSeqNo;
  }

  public final void setNewSeqNo(final long newSeqNo) {
    this.newSeqNo = newSeqNo;
  }

  public final boolean isGapFillFlag() {
    return gapFillFlag;
  }

  public final void setGapFillFlag(final boolean gapFillFlag) {
    this.gapFillFlag = gapFillFlag;
  }

  @Override
  public void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("SequenceResetMessage [newSeqNo=").append(newSeqNo).append(", gapFillFlag=").append(gapFillFlag).append("]");
  }

  @Override
  public String toJSON() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"ResendRequestMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"user\":").append("\"").append(user.getId());
    sb.append(",\"newSeqNo\":").append("\"").append(newSeqNo);
    sb.append(",\"gapFillFlag\":").append("\"").append(gapFillFlag);

    sb.append("}");
    return sb.toString();
  }
}
