package com.solfini.matchengine.message.session;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.session.SessionInfo;

/**
 *
 * @author Chris Mack
 *
 */
public class LogoutMessage extends Message {
  private SessionInfo sessionInfo;
  private String text;

  private boolean logonFailure;

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  public MessageType getMessageType() {
    return MessageType.LOGOUT;
  }

  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }

  public void setSessionInfo(final SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public boolean isLogonFailure() {
    return logonFailure;
  }

  public void setLogonFailure(final boolean logonFailure) {
    this.logonFailure = logonFailure;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("LogoutMessage [senderCompId=").append(senderCompId).append(", sessionInfo=").append(sessionInfo).append(TEXT_EQ)
        .append(text).append(", logonFailure=").append(logonFailure).append("]");
  }

  @Override
  public void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public String toJSON() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"LogoutMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"user\":").append("\"").append(user.getId());
    sb.append("}");
    return sb.toString();
  }
}
