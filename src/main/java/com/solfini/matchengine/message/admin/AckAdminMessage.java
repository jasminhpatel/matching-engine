package com.solfini.matchengine.message.admin;

import com.solfini.common.AdminMessage;
import com.solfini.common.MessageType;
import com.solfini.internal.admin.schema.AdminAcknowledgementDecoder;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.schema.PayloadType;

/**
 *
 * @author Chris Mack
 *
 */
public class AckAdminMessage extends AdminMessage {
  private RequestStatus requestStatus;

  public AckAdminMessage() {}

  public AckAdminMessage(final AdminAcknowledgementDecoder ACK_DECODER, final long connectionId) {
    this.connectionId = connectionId;
    this.requestStatus = ACK_DECODER.requestStatus();
    this.triggerTimeMillis = ACK_DECODER.triggerTimeMillis();
    this.routeToDestination = ACK_DECODER.routeToDestination();
    this.senderInstanceId = ACK_DECODER.senderInstanceId();
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  public MessageType getMessageType() {
    return MessageType.ACK_ADMIN;
  }

  public RequestStatus getRequestStatus() {
    return requestStatus;
  }

  @Override
  public void onMatcher() {
    // do nothing
  }

  @Override
  public void onPublish() {
    // do nothing
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append("AckAdminMessage [requestStatus=").append(requestStatus).append(TRIGGERTIMEMILLIS_EQ).append(triggerTimeMillis)
        .append(ROUTETODESTINATION_EQ).append(routeToDestination).append("]");
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"AckAdminMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis).append(",\"externalId\":").append(externalId)
        .append(",\"requestStatus\":").append(requestStatus.value()).append(",\"routeToDestination\":").append("\"")
        .append(routeToDestination).append("\"");
    sb.append("}");
    return sb.toString();
  }
}
