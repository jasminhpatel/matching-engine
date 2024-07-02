package com.solfini.matchengine.message.internal;

import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.orderbook.OrderFilterCache;
import com.solfini.sbe.encoder.OrderFilterDecoder;
import com.solfini.sbe.encoder.Side;

public class OrderFilter extends Message {
  private long filterId;
  private long[] orderIdGroup;

  public void set(final OrderFilterDecoder decoder) {
    this.filterId = decoder.filterId();
    OrderFilterDecoder.OrderIdGroupDecoder orderIdGroupDecoder = decoder.orderIdGroup();
    final int orderIdGroupCount = orderIdGroupDecoder.count();
    this.orderIdGroup = new long[orderIdGroupCount];

    for (int k = 0; k < orderIdGroupCount; k++) {
      orderIdGroupDecoder = orderIdGroupDecoder.next();
      this.orderIdGroup[k] = orderIdGroupDecoder.filterOrderId();
    }
  }

  public long getFilterId() {
    return filterId;
  }

  public void setFilterId(final long filterId) {
    this.filterId = filterId;
  }

  public long[] getOrderIdGroup() {
    return orderIdGroup;
  }

  public void setOrderIdGroup(final long[] orderIdGroup) {
    this.orderIdGroup = orderIdGroup;
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.ORDER_FILTER;
  }

  @Override
  public void onMatcher() {
    OrderFilterCache.onModel(this);
  }

  @Override
  public void clear() {
    super.clear();
    filterId = 0;
    orderIdGroup = null;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder sb) {
    sb.append("OrderFilter{");
    sb.append("filterId=").append(filterId);
    sb.append(", orderIdGroup=").append(orderIdGroup);
    sb.append(", senderCompId='").append(senderCompId).append('\'');
    sb.append(", sequenceNumber=").append(sequenceNumber);
    sb.append(", snapId=").append(snapId);
    sb.append(", kafkaRecordOffset=").append(kafkaRecordOffset);
    sb.append(", inputTime=").append(inputTime);
    sb.append(", matchTime=").append(matchTime);
    sb.append(", publishTime=").append(publishTime);
    sb.append(", transactionId=").append(transactionId);
    sb.append('}');

    return sb;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"OrderFilter\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"sourceSeqNum\":")
        .append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime).append(",\"snapId\":").append(snapId)
        .append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"filterId\":\"").append(filterId).append("\"")
        .append(",\"orderIdGroup\":[");
    int count = 0;
    for (long orderId : orderIdGroup) {
      if (count > 0)
        sb.append(',');
      sb.append(orderId);
      count++;
    }

    sb.append("]}");
    return sb.toString();
  }
}
