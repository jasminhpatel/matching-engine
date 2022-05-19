package com.solfini.matchengine.message.controller;

import com.solfini.common.Message;
import com.solfini.internal.schema.PayloadType;

/**
 * The ControlMessage class is the base class for all control messages.
 */
public abstract class ControlMessage extends Message {

  public ControlMessage() {
    super();
    this.transactionId = 1; // Special transaction id, which is passed through by transaction queue.
    this.isLastMessageInTransaction = true;
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.admin;
  }
}
