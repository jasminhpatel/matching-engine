package com.solfini.matchengine.util;

import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import com.solfini.internal.schema.InternalMessageWrapperInboundEncoder;
import com.solfini.internal.schema.MessageHeaderEncoder;
import com.solfini.internal.schema.PayloadType;

/**
 *
 * @author Chris Mack
 *
 */
public class InternalMessageTestingUtil {



  public static InternalMessageWrapperInboundEncoder wrapFIXMessageInInternalFormat(DirectBuffer asciiBuffer, int messageLength,
      long connectionId) {


    InternalMessageWrapperInboundEncoder internalMessageEncoder = createInternalMessageEncoder(connectionId, PayloadType.orderEntry);
    internalMessageEncoder.putFixBody(asciiBuffer, 0, messageLength);

    return internalMessageEncoder;


  }

  public static InternalMessageWrapperInboundEncoder wrapAdminMessageInInternalFormat(DirectBuffer adminBuffer, int messageLength,
      int connectionId) {

    InternalMessageWrapperInboundEncoder internalMessageEncoder = createInternalMessageEncoder(connectionId, PayloadType.admin);
    internalMessageEncoder.putAdminBody(adminBuffer, 0, messageLength);

    return internalMessageEncoder;
  }

  private static InternalMessageWrapperInboundEncoder createInternalMessageEncoder(long connectionId, PayloadType payloadType) {


    ByteBuffer internalMessageBuffer = ByteBuffer.allocateDirect(8192);

    UnsafeBuffer internalMessageUnsafeBuffer = new UnsafeBuffer(internalMessageBuffer);


    InternalMessageWrapperInboundEncoder internalMessageEncoder = new InternalMessageWrapperInboundEncoder();

    MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    internalMessageEncoder.wrapAndApplyHeader(internalMessageUnsafeBuffer, 0, headerEncoder);
    internalMessageEncoder.sequenceVersion(1);
    internalMessageEncoder.sequenceNumber(2);
    internalMessageEncoder.producerId(99);
    internalMessageEncoder.eventId(1);
    internalMessageEncoder.payloadType(payloadType);
    internalMessageEncoder.transactionTime(System.currentTimeMillis());
    internalMessageEncoder.responseChannelId((int) connectionId);
    internalMessageEncoder.infoFlags().endOfEvent(true);


    return internalMessageEncoder;

  }


}
