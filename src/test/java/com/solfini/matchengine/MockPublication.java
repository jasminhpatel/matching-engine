package com.solfini.matchengine;

import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.status.ReadablePosition;
import io.aeron.Aeron;
import io.aeron.DirectBufferVector;
import io.aeron.LogBuffers;
import io.aeron.MockClientConductor;
import io.aeron.Publication;
import io.aeron.ReservedValueSupplier;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.samples.SamplesUtil;

public class MockPublication extends Publication {
  private List<DirectBuffer> offerList = new ArrayList<>();

  protected MockPublication(MockClientConductor clientConductor, String channel, int streamId, int sessionId,
      ReadablePosition positionLimit, int channelStatusId, LogBuffers logBuffers, long originalRegistrationId, long registrationId,
      int maxMessageLength) {
    super(clientConductor, channel, streamId, sessionId, positionLimit, channelStatusId, logBuffers, originalRegistrationId, registrationId,
        maxMessageLength);
    // TODO Auto-generated constructor stub
  }

  public MockPublication() {
    super(null, null, 0, 0, null, 0, new LogBuffers("/aeron-data"), 0, 0, 0);

    final Aeron.Context ctx = new Aeron.Context().availableImageHandler(SamplesUtil::printAvailableImage)
        .unavailableImageHandler(SamplesUtil::printUnavailableImage);
  }

  // public long offer(final DirectBuffer buffer, final int offset, final int length) {
  // return 0;
  // }

  @Override
  public long offer(DirectBuffer buffer, int offset, int length, ReservedValueSupplier reservedValueSupplier) {
    offerList.add(buffer);
    System.out.println("in offer");
    return 0;
  }

  @Override
  public long offer(DirectBufferVector[] vectors, ReservedValueSupplier reservedValueSupplier) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public long tryClaim(int length, BufferClaim bufferClaim) {
    // TODO Auto-generated method stub
    return 0;
  }

  public final List<DirectBuffer> getOfferList() {
    return offerList;
  }

  public final void setOfferList(List<DirectBuffer> offerList) {
    this.offerList = offerList;
  }
}
