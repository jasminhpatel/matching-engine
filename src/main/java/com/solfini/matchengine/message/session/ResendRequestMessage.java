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
public class ResendRequestMessage extends Message {
  private long beginSeqNo;
  private long endSeqNo;
  private boolean originated;

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.RESEND_REQUEST;
  }

  public long getBeginSeqNo() {
    return beginSeqNo;
  }

  public void setBeginSeqNo(final long beginSeqNo) {
    this.beginSeqNo = beginSeqNo;
  }

  public long getEndSeqNo() {
    return endSeqNo;
  }

  public void setEndSeqNo(final long endSeqNo) {
    this.endSeqNo = endSeqNo;
  }

  public final boolean isOriginated() {
    return originated;
  }

  public final void setOriginated(boolean originated) {
    this.originated = originated;
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
    return s.append("ResendRequestMessage [beginSeqNo=").append(beginSeqNo).append(", endSeqNo=").append(endSeqNo).append(", originated=")
        .append(originated).append("]");
  }

  @Override
  public String toJSON() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"ResendRequestMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"user\":").append("\"").append(user.getId());
    sb.append(",\"beginSeqNo\":").append("\"").append(beginSeqNo);
    sb.append(",\"endSeqNo\":").append("\"").append(endSeqNo);
    sb.append(",\"originated\":").append("\"").append(originated);

    sb.append("}");
    return sb.toString();
  }
}
