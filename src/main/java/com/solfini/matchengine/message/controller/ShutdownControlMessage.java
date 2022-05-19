package com.solfini.matchengine.message.controller;

import com.solfini.common.Context;
import com.solfini.common.MessageType;
import com.solfini.matchengine.PublisherEncoderCache;
import com.solfini.matchengine.controller.Controller;

/**
 * The ShutdownControlMessage is sent to indicate a graceful shutdown.
 */
public class ShutdownControlMessage extends ControlMessage {

  private final Controller controller;

  public ShutdownControlMessage(final Controller controller) {
    super();
    this.controller = controller;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.SHUTDOWN_CONTROL;
  }

  public Controller getController() {
    return controller;
  }

  @Override
  public void onPublish() {
    PublisherEncoderCache.blockWaitGetLock();
    Context.getKafkaPublisher().enqueueShutdown(controller);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("ShutdownControlMessage");
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"ShutdownControlMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append("}");
    return sb.toString();
  }
}
