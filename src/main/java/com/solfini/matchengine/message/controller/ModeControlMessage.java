package com.solfini.matchengine.message.controller;

import java.util.Iterator;

import com.solfini.common.Context;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.matchengine.controller.Mode;
import com.solfini.user.UserCache;

/**
 * The ModeControlMessage is sent to indicate mode changes (switch to primary, switch to secondary, etc).
 */
public class ModeControlMessage extends ControlMessage {

  private final Mode previousMode;
  private final Mode mode;

  public ModeControlMessage(final Mode previousMode, final Mode mode) {
    super();
    this.previousMode = previousMode;
    this.mode = mode;
  }

  public final Mode getPreviousMode() {
    return previousMode;
  }

  public final Mode getMode() {
    return mode;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.MODE_CONTROL;
  }

  @Override
  public void onMatcher() {
    switch (mode) {
      case PRIMARY:
        if (Mode.SECONDARY == previousMode) {
          if (MarketStatus.DR_MODE == Context.getMarketStatus()) {
            UserCache.resetAvailableBalances();
          }

          final Iterator<InstrumentPair> iterator = InstrumentCache.getPairIterator();
          while (iterator.hasNext()) {
            final InstrumentPair instrumentPair = iterator.next();
            if (instrumentPair != null) {
              instrumentPair.changeState(MarketStatus.DR_TO_OPEN, 0, this);
            }
          }
        }

        Context.setMarketStatus(MarketStatus.OPEN);
        break;

      case SECONDARY:
        Context.setMarketStatus(MarketStatus.DR_MODE);
        break;

      default:
    }

    Context.setControllerMode(mode);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("ModeControlMessage (PreviousMode: ").append(previousMode).append(", Mode: ").append(mode).append(")");
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"ModeControlMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"previousMode\":")
        .append(previousMode).append(",\"mode\":").append(mode).append("}");
    return sb.toString();
  }
}
