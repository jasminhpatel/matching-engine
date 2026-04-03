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
  private int[] priceIdGroup;

  public void set(final OrderFilterDecoder decoder) {
    this.filterId = decoder.filterId();
    OrderFilterDecoder.OrderIdGroupDecoder orderIdGroupDecoder = decoder.orderIdGroup();
    final int orderIdGroupCount = orderIdGroupDecoder.count();
    this.orderIdGroup = new long[orderIdGroupCount];
    this.priceIdGroup = new int[orderIdGroupCount];

    for (int k = 0; k < orderIdGroupCount; k++) {
      orderIdGroupDecoder = orderIdGroupDecoder.next();
      this.orderIdGroup[k] = orderIdGroupDecoder.filterOrderId();
      this.priceIdGroup[k] = orderIdGroupDecoder.priceInt();
    }
  }

  public final long getFilterId() {
    return filterId;
  }

  public final void setFilterId(final long filterId) {
    this.filterId = filterId;
  }

  public final long[] getOrderIdGroup() {
    return orderIdGroup;
  }

  public final void setOrderIdGroup(final long[] orderIdGroup) {
    this.orderIdGroup = orderIdGroup;
  }

  public final int[] getPriceIdGroup() {
    return priceIdGroup;
  }

  public final void setPriceIdGroup(final int[] priceIdGroup) {
    this.priceIdGroup = priceIdGroup;
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public final MessageType getMessageType() {
    return MessageType.ORDER_FILTER;
  }

  @Override
  public final void onMatcher() {
    OrderFilterCache.onModel(this);
  }

  @Override
  public final void clear() {
    super.clear();
    filterId = 0;
    orderIdGroup = null;
    priceIdGroup = null;
  }

  @Override
  public final String toString() {
    final StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public final StringBuilder appendTo(final StringBuilder sb) {
    sb.append("OrderFilter{");
    sb.append("filterId=").append(filterId);
    // sb.append(", orderIdGroup=").append(orderIdGroup);
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
  public final String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"OrderFilter\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"sourceSeqNum\":")
        .append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime).append(",\"snapId\":").append(snapId)
        .append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"filterId\":\"").append(filterId).append("\"")
        .append(",\"orderIdGroup\":[");
    int count = 0;
    for (int i = 0; i < orderIdGroup.length; i++) {
      if (count > 0)
        sb.append(',');
      sb.append("[").append(orderIdGroup[i]).append(",").append(priceIdGroup[i]).append("]");
      count++;
    }

    sb.append("]}");
    return sb.toString();
  }
}
