package com.solfini.matchengine.util.order;

import org.agrona.DirectBuffer;
import com.solfini.internal.schema.InternalMessageWrapperInboundEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class TestMessageWrapper {


  private DirectBuffer directBuffer;

  private InternalMessageWrapperInboundEncoder internalMessage;

  public TestMessageWrapper(DirectBuffer directBuffer,
      InternalMessageWrapperInboundEncoder internalMessage) {
    this.directBuffer = directBuffer;
    this.internalMessage = internalMessage;
  }

  public DirectBuffer getDirectBuffer() {
    return directBuffer;
  }

  public void setFixAsciiBuffer(DirectBuffer directBuffer) {
    this.directBuffer = directBuffer;
  }

  public InternalMessageWrapperInboundEncoder getInternalMessage() {
    return internalMessage;
  }

  public void setInternalMessage(InternalMessageWrapperInboundEncoder internalMessage) {
    this.internalMessage = internalMessage;
  }



}
