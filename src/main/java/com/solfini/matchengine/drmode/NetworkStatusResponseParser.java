package com.solfini.matchengine.drmode;

import com.solfini.common.Constants;
import com.solfini.common.Message;
import com.solfini.matchengine.message.session.NetworkStatusMessage;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.NetworkStatusDecoder;

/**
 *
 * @author Chris Mack
 *
 */
public class NetworkStatusResponseParser implements Constants {

  public NetworkStatusResponseParser() {
    // hidden default constructor
  }

  public final Message parse(final MessageHeaderDecoder headerDecoder, final NetworkStatusDecoder networkStatusDecoder) {
    final long requestId = networkStatusDecoder.requestId();
    final long responseId = networkStatusDecoder.responseId();
    final long orderSequenceNumber = networkStatusDecoder.orderSequenceNumber();
    return new NetworkStatusMessage(requestId, responseId, orderSequenceNumber, null);
  }
}
