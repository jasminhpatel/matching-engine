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
public class NetworkStatusMessage extends Message {
  private long requestId;
  private long responseId;
  private long orderSequenceNumber;

  public NetworkStatusMessage(final long requestId, final long responseId, final long orderSequenceNumber, final String senderCompId) {
    this.requestId = requestId;
    this.responseId = responseId;
    this.orderSequenceNumber = orderSequenceNumber;
    this.senderCompId = senderCompId;
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.SEQUENCE_RESET;
  }

  public final long getRequestId() {
    return requestId;
  }

  public final void setRequestId(final long requestId) {
    this.requestId = requestId;
  }

  public final long getResponseId() {
    return responseId;
  }

  public final void setResponseId(final long responseId) {
    this.responseId = responseId;
  }

  public final long getOrderSequenceNumber() {
    return orderSequenceNumber;
  }

  public final void setOrderSequenceNumber(final long orderSequenceNumber) {
    this.orderSequenceNumber = orderSequenceNumber;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("NetworkStatusMessage [requestId=").append(requestId).append(", responseId=").append(responseId)
        .append(ORDERSEQUENCENUMBER_EQ).append(orderSequenceNumber).append("]");
  }

  @Override
  public void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public String toJSON() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"NetworkStatusMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"user\":").append("\"").append(user.getId());
    sb.append(",\"orderSequenceNumber\":").append("\"").append(orderSequenceNumber);
    sb.append(",\"requestId\":").append("\"").append(requestId);
    sb.append(",\"responseId\":").append("\"").append(responseId);

    sb.append("}");
    return sb.toString();
  }
}
