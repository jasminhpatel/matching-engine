package com.solfini.matchengine.message.admin;

import com.solfini.common.AdminMessage;
import com.solfini.common.Context;
import com.solfini.common.MessageType;
import com.solfini.internal.admin.schema.SnapResponseAdminMessageDecoder;
import com.solfini.internal.schema.PayloadType;

/**
 *
 * @author Chris Mack
 *
 */
public class SnapResponseAdminMessage extends AdminMessage {
  private long orderSequenceNumber;
  private long inputKafkaRecordOffset;
  private long outputKafkaRecordOffset;
  private long orderId;
  private long execId;

  public SnapResponseAdminMessage(final long snapId, final long sequenceNumber, final long orderSequenceNumber, final String senderCompId) {
    this.senderCompId = senderCompId;
    this.snapId = snapId;
    this.sequenceNumber = sequenceNumber;
    this.orderSequenceNumber = orderSequenceNumber;
    this.routeToDestination = Context.getInstanceId();
  }

  public SnapResponseAdminMessage(final SnapResponseAdminMessageDecoder decoder, final int connectionId) {
    this.snapId = decoder.snapId();
    this.sequenceNumber = decoder.sequenceNumber();
    this.orderSequenceNumber = decoder.orderSequenceNumber();
    this.externalId = decoder.externalId();
    this.inputKafkaRecordOffset = decoder.inputKafkaRecordOffset();
    this.outputKafkaRecordOffset = decoder.outputKafkaRecordOffset();
    this.sourceSeqNum = decoder.sourceSeqNum();
    this.triggerTimeMillis = decoder.triggerTimeMillis();
    this.routeToDestination = decoder.routeToDestination();
    this.senderInstanceId = decoder.senderInstanceId();
    this.orderId = decoder.orderId();
    this.execId = decoder.execId();
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  public final MessageType getMessageType() {
    return MessageType.SNAP_RESPONSE;
  }

  public final long getOrderSequenceNumber() {
    return orderSequenceNumber;
  }

  public final void setOrderSequenceNumber(long orderSequenceNumber) {
    this.orderSequenceNumber = orderSequenceNumber;
  }

  public final long getInputKafkaRecordOffset() {
    return inputKafkaRecordOffset;
  }

  public final void setInputKafkaRecordOffset(long inputKafkaRecordOffset) {
    this.inputKafkaRecordOffset = inputKafkaRecordOffset;
  }

  public final long getOutputKafkaRecordOffset() {
    return outputKafkaRecordOffset;
  }

  public final void setOutputKafkaRecordOffset(long outputKafkaRecordOffset) {
    this.outputKafkaRecordOffset = outputKafkaRecordOffset;
  }

  public final long getOrderId() {
    return orderId;
  }

  public final void setOrderId(final long orderId) {
    this.orderId = orderId;
  }

  public final long getExecId() {
    return execId;
  }

  public final void setExecId(final long execId) {
    this.execId = execId;
  }

  @Override
  public final void onMatcher() {
    // do nothing
  }

  @Override
  public final void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append("SnapResponseAdminMessage [snapId=").append(snapId).append(", sequenceNumber=").append(sequenceNumber)
        .append(ORDERSEQUENCENUMBER_EQ).append(orderSequenceNumber).append(TRIGGERTIMEMILLIS_EQ).append(triggerTimeMillis)
        .append(ROUTETODESTINATION_EQ).append(routeToDestination).append(", inputKafkaRecordOffset=").append(inputKafkaRecordOffset)
        .append(", outputKafkaRecordOffset=").append(outputKafkaRecordOffset)
        .append(", orderId=").append(orderId).append(", execId=").append(execId)
        .append(SOURCESEQNUM_EQ).append(sourceSeqNum).append(SOURCESENDTIME_EQ).append(sourceSendTime).append("]");
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"SnapResponseAdminMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis).append(",\"externalId\":").append(externalId);
    sb.append(",\"routeToDestination\":").append("\"").append(routeToDestination).append("\"");
    sb.append(",\"orderSequenceNumber\":").append(orderSequenceNumber).append(",\"inputKafkaRecordOffset\":").append(inputKafkaRecordOffset)
        .append(",\"outputKafkaRecordOffset\":").append(outputKafkaRecordOffset);
    sb.append(",\"orderId\":").append(orderId).append(",\"execId\":").append(execId);
    sb.append("}");
    return sb.toString();
  }

}
