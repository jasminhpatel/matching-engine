package com.solfini.matchengine.drmode;

import java.util.Arrays;

import com.solfini.common.Appendable;
import com.solfini.common.Constants;

/**
 *
 * @author Chris Mack
 *
 */
public class RecieverData implements Constants, Appendable {
  private long seqNum;
  private long sendTime;
  private long recordOffset;
  private byte messageType;
  private byte[] data;

  public RecieverData() {
    // default constructor
  }

  public void set(final long seqNum, final long sendTime, final long recordOffset, final byte messageType, final byte[] data) {
    this.seqNum = seqNum;
    this.sendTime = sendTime;
    this.recordOffset = recordOffset;
    this.messageType = messageType;
    this.data = data;
  }

  public final long getSeqNum() {
    return seqNum;
  }

  public final void setSeqNum(final long seqNum) {
    this.seqNum = seqNum;
  }

  public final long getSendTime() {
    return sendTime;
  }

  public final void setSendTime(final long sendTime) {
    this.sendTime = sendTime;
  }

  public final long getRecordOffset() {
    return recordOffset;
  }

  public final void setRecordOffset(final long recordOffset) {
    this.recordOffset = recordOffset;
  }

  public final byte getMessageType() {
    return messageType;
  }

  public final void setMessageType(final byte messageType) {
    this.messageType = messageType;
  }

  public final byte[] getData() {
    return data;
  }

  public final void setData(final byte[] data) {
    this.data = data;
  }

  public final void clear() {
    this.data = null;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("RecieverData [seqNum=").append(seqNum).append(", sendTime=").append(sendTime).append(", recordOffset=")
        .append(recordOffset).append(MESSAGETYPE_EQ).append(messageType).append(", data=").append(Arrays.toString(data)).append("]");
  }

}
