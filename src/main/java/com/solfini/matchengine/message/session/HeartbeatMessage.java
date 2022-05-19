package com.solfini.matchengine.message.session;

import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.internal.schema.PayloadType;

/**
 *
 * @author Chris Mack
 *
 */
public class HeartbeatMessage extends Message {
  private int heartbeatInterval;

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.HEARTBEAT;
  }

  public int getHeartBeatInterval() {
    return heartbeatInterval;
  }

  public void getHeartBeatInterval(int heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  @Override
  public void onMatcher() {
    // Not required
  }

  @Override
  public void onPublish() {
    // Not required
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("HeartbeatMessage [senderCompId=").append(senderCompId).append(", heartbeatInterval=").append(heartbeatInterval)
        .append("]");
  }

  @Override
  public String toJSON() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"HeartbeatMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);

    sb.append("}");
    return sb.toString();
  }
}
