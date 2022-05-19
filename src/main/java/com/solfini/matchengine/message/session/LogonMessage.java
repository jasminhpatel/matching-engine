package com.solfini.matchengine.message.session;

import com.solfini.internal.schema.PayloadType;

import java.util.Arrays;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.Position;
import com.solfini.matchengine.session.SessionInfo;

/**
 *
 * @author Chris Mack
 *
 */
public class LogonMessage extends Message {
  private int heartbeatInterval;
  private SessionInfo sessionInfo;
  private Position[] positionArr = new Position[Math.max(InstrumentCache.getPairCapacity(), 64)]; // copied from user
  private int positionsLength = 0;

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.LOGON;
  }

  public final int getHeartBeatInterval() {
    return heartbeatInterval;
  }

  public void setHeartBeatInterval(final int heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  public final SessionInfo getSessionInfo() {
    return sessionInfo;
  }

  public final void setSessionInfo(final SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }

  public final Position[] getPositionArr() {
    return positionArr;
  }

  public final int getPositionsLength() {
    return positionsLength;
  }

  public final void setPositionsLength(final int positionsLength) {
    this.positionsLength = positionsLength;
  }

  public final Position[] reservePositionArrSize(final int length) {
    if (this.positionArr.length < length) {
      setPositionArr(Arrays.copyOf(this.positionArr, length));
    }

    return positionArr;
  }

  public final void setPositionArr(final Position[] positionArr) {
    this.positionArr = positionArr;
    this.positionsLength = positionArr.length;
  }

  @Override
  public void onMatcher() {
    user.copySetPositionArr(this);

    Context.getMatcherToPublisherQueue().add(this);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("LogonMessage [heartbeatInterval=").append(heartbeatInterval).append(", sessionInfo=").append(sessionInfo)
        .append(USER_EQ).append(user).append("]");
  }

  @Override
  public void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public String toJSON() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"LogonMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset);
    sb.append(",\"user\":").append("\"").append(user.getId());
    sb.append("}");
    return sb.toString();
  }
}
