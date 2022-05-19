package com.solfini.matchengine.message.controller;

import java.util.concurrent.CountDownLatch;

import com.solfini.common.Context;
import com.solfini.common.MessageType;
import com.solfini.matchengine.PublisherEncoderCache;

/**
 * The PublishControlMessage is sent to switch on output message publication and to set output topic and sequence.
 */
public class PublishControlMessage extends ControlMessage {

  private final String outputTopic;
  private final long outputSequence;
  private final CountDownLatch latch = new CountDownLatch(1);

  public PublishControlMessage(final String outputTopic, final long outputSequence) {
    super();
    this.outputTopic = outputTopic;
    this.outputSequence = outputSequence;
  }

  public final String getOutputTopic() {
    return outputTopic;
  }

  public final long getOutputSequence() {
    return outputSequence;
  }

  public final void await() throws InterruptedException {
    latch.await();
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.PUBLISH_CONTROL;
  }

  @Override
  public void onMatcher() {
    super.onMatcher();
    latch.countDown();
  }

  @Override
  public void onPublish() {
    PublisherEncoderCache.blockWaitGetLock();

    Context.getMessagePublisher().setOutputTopic(outputTopic);
    Context.getKafkaPublisher().enqueueTopicChange(outputTopic, outputSequence);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("PublishControlMessage (Topic: ").append(outputTopic).append(", Sequence: ").append(outputSequence).append(")");
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"PublishControlMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"outputTopic\":")
        .append(outputTopic).append(",\"outputSequence\":").append(outputSequence).append("}");
    return sb.toString();
  }
}
