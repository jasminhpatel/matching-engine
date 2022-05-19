package com.solfini.matchengine.drmode.message;

import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import com.solfini.sbe.encoder.BusinessRejectDecoder;

public class DRBusinessRejectMessage extends BusinessRejectMessage {
  public DRBusinessRejectMessage(final BusinessRejectDecoder decoder) {
    for (int i = 0; i < 1000000; i++) {
      try {
        setPairId(decoder.pairId());
        setOrderId(decoder.orderId());
        setCancelId(decoder.cancelId());
        setCancelReplaceId(decoder.cancelReplaceId());
        setSecondaryOrderId(decoder.secondaryOrderId());
        break;
      } catch (IndexOutOfBoundsException e1) {
        try {
          Thread.sleep(0);
        } catch (InterruptedException e2) {
            Thread.currentThread().interrupt();
        }
      }
    }
  }

  @Override
  public void onMatcher() {

    // Update from orderId
    GlobalOrderBook.setOrderIdIfGreater(2, getOrderId());

    // Update from cancelId
    GlobalOrderBook.setOrderIdIfGreater(3, getCancelId());

    // Update from cancelReplaceId
    GlobalOrderBook.setOrderIdIfGreater(4, getCancelReplaceId());

    // Update secondary order id for the instrument
    final InstrumentPair instrumentPair = InstrumentCache.getPair(getPairId());
    if (null != instrumentPair) {
      instrumentPair.getOrderBook().setSecondaryOrderIdIfGreater(getSecondaryOrderId());
    }
  }
}
