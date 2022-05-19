package com.solfini.matchengine;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import com.solfini.internal.schema.InternalMessageWrapperInboundEncoder;
import io.aeron.Publication;

public class PublicationWrapper {
  private final Publication publication;
  private final List<InternalMessageWrapperInboundEncoder> offerList;

  public PublicationWrapper(final Publication publication) {
    this.publication = publication;
    offerList = new ArrayList<>();
  }

  public static ByteBuffer clone(final ByteBuffer original) {
    int start = original.position();
    ByteBuffer clone = ByteBuffer.allocateDirect(original.capacity());
    original.rewind();// copy from the beginning
    clone.put(original);
    original.rewind();
    clone.flip();
    original.position(start);
    return clone;
  }

  public long offer(final InternalMessageWrapperInboundEncoder internalMessageEncoder) {
    if (publication != null)
      return publication.offer(internalMessageEncoder.buffer(), 0, internalMessageEncoder.limit(), null);
    else {
      InternalMessageWrapperInboundEncoder temp = new InternalMessageWrapperInboundEncoder();
      ByteBuffer byteBuffer = internalMessageEncoder.buffer().byteBuffer();
      ByteBuffer byteBuffer2 = clone(byteBuffer);
      temp.wrap(new UnsafeBuffer(byteBuffer2), internalMessageEncoder.offset());
      temp.limit(internalMessageEncoder.limit());
      offerList.add(temp);
      return 0;
    }
  }

  public final Publication getPublication() {
    return publication;
  }

  public final List<InternalMessageWrapperInboundEncoder> getOfferList() {
    return offerList;
  }

  @Override
  public String toString() {
    return "PublicationWrapper [publication=" + publication + ", offerList=" + offerList + "]";
  }

}
