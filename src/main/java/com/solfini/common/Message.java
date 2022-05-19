package com.solfini.common;

import java.io.Serializable;
import com.solfini.internal.schema.PayloadType;
import com.solfini.user.User;

/**
 *
 * @author Chris Mack
 *
 */
public abstract class Message implements Appendable, Serializable, Constants {
  protected String senderCompId;
  protected long sequenceNumber;
  protected String errorString;
  protected User user;
  protected long persistTime;
  protected long sourceSeqNum;
  protected long sourceSendTime;
  protected long snapId; // 0 if not snapping
  protected long kafkaRecordOffset; // offset of input

  protected long inputTime;
  protected long decodedTime;
  protected long matchTime;
  protected long publishTime;
  protected long transactionId = 0;
  protected boolean isLastMessageInTransaction = false;

  protected boolean markAsReturned = false;

  public final User getUser() {
    return user;
  }

  public final void setUser(final User user) {
    this.user = user;
  }

  public final String getSenderCompId() {
    return senderCompId;
  }

  public final void setSenderCompId(final String senderCompId) {
    this.senderCompId = senderCompId;
  }

  public final long getSequenceNumber() {
    return sequenceNumber;
  }

  public final void setSequenceNumber(final long sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }

  public final String getError() {
    return errorString;
  }

  public final void setError(final String error) {
    this.errorString = error;
  }

  public final long getPersistTime() {
    return persistTime;
  }

  public final void setPersistTime(final long persistTime) {
    this.persistTime = persistTime;
  }

  public final long getSourceSeqNum() {
    return sourceSeqNum;
  }

  public final void setSourceSeqNum(final long sourceSeqNum) {
    this.sourceSeqNum = sourceSeqNum;
  }

  public final long getSourceSendTime() {
    return sourceSendTime;
  }

  public final void setSourceSendTime(final long sourceSendTime) {
    this.sourceSendTime = sourceSendTime;
  }

  public final long getSnapId() {
    return snapId;
  }

  public final void setSnapId(final long snapId) {
    this.snapId = snapId;
  }

  public final long getKafkaRecordOffset() {
    return kafkaRecordOffset;
  }

  public final void setKafkaRecordOffset(final long kafkaRecordOffset) {
    this.kafkaRecordOffset = kafkaRecordOffset;
  }

  public final long getInputTime() {
    return inputTime;
  }

  public final void setInputTime(final long inputTime) {
    this.inputTime = inputTime;
  }

  public final long getDecodedTime() {
    return decodedTime;
  }

  public final void setDecodedTime(final long decodedTime) {
    this.decodedTime = decodedTime;
  }

  public final long getMatchTime() {
    return matchTime;
  }

  public final void setMatchTime(final long matchTime) {
    this.matchTime = matchTime;
  }

  public abstract PayloadType getPayloadType();

  public abstract MessageType getMessageType();


  public void onMatcher() {
    Context.getMatcherToPublisherQueue().add(this);
  }

  public void onEncodeBufferedPublish() {}

  public void onPublish() {}

  public void onPersist() {}

  public abstract String toJSON();

  public void resetMarkAsReturned() {
    markAsReturned = false;
  }

  public void markAsReturned() {
    markAsReturned = true;
  }

  public boolean isMarkAsReturned() {
    return markAsReturned;
  }

  public void setTransactionId(long transactionId) {
    this.transactionId = transactionId;
  }

  public long getTransactionId() {
    return this.transactionId;
  }

  public void setLastMessageInTransaction(boolean setting) {
    isLastMessageInTransaction = setting;
  }

  public boolean isLastMessageInTransaction() {
    return this.isLastMessageInTransaction;
  }

  public void clear() {
    senderCompId = null;
    sequenceNumber = 0;
    errorString = null;
    user = null;
    persistTime = 0;
    sourceSeqNum = 0;
    sourceSendTime = 0;
    snapId = 0;
    kafkaRecordOffset = 0;
    inputTime = 0;
    decodedTime = 0;
    matchTime = 0;
    publishTime = 0;
    transactionId = 0;
    isLastMessageInTransaction = false;
  }

  @Override
  public String toString() {
    final StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

}
