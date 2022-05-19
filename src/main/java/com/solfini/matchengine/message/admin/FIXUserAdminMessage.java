package com.solfini.matchengine.message.admin;

import com.solfini.common.AdminMessage;
import com.solfini.common.MessageType;
import com.solfini.internal.admin.schema.FIXUserAdminMessageDecoder;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.schema.PayloadType;

public class FIXUserAdminMessage extends AdminMessage {
  private String username;
  private String password;
  private UpdateType updateType;
  private int userType;

  public FIXUserAdminMessage(FIXUserAdminMessageDecoder fixUserDecoder, long connectionId) {
    this.connectionId = connectionId;
    this.updateType = fixUserDecoder.updateType();
    this.username = fixUserDecoder.username();
    this.password = fixUserDecoder.password();
    this.senderCompId = fixUserDecoder.senderCompId();
    this.userType = fixUserDecoder.userType();
    this.triggerTimeMillis = fixUserDecoder.triggerTimeMillis();
    this.routeToDestination = fixUserDecoder.routeToDestination();
    this.senderInstanceId = fixUserDecoder.senderInstanceId();
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  @Override
  public final MessageType getMessageType() {
    return MessageType.FIX_USER_ADMIN;
  }

  public final String getUsername() {
    return username;
  }

  public final String getPassword() {
    return password;
  }

  public final UpdateType getUpdateType() {
    return updateType;
  }

  public final int getUserType() {
    return userType;
  }

  public final void setUserType(final int userType) {
    this.userType = userType;
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
    s.append("FIXUserAdminMessage [connectionId=").append(connectionId).append(TRIGGERTIMEMILLIS_EQ).append(triggerTimeMillis)
        .append(USERNAME_EQ).append(username).append(", password=").append(password).append(SENDERCOMPID_EQ).append(senderCompId)
        .append(UPDATETYPE_EQ).append(updateType).append(ROUTETODESTINATION_EQ).append(routeToDestination).append(SOURCESEQNUM_EQ)
        .append(sourceSeqNum).append(SOURCESENDTIME_EQ).append(sourceSendTime).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"FIXUserAdminMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis).append(",\"externalId\":").append(externalId)
        .append(",\"username\":").append("\"").append(username).append("\"").append(",\"updateType\":").append(updateType.value())
        .append(",\"routeToDestination\":").append("\"").append(routeToDestination).append("\"");
    sb.append("}");
    return sb.toString();
  }
}
