package com.solfini.matchengine.drmode.message;

import com.solfini.matchengine.message.outbound.CancelRejectMessage;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import com.solfini.sbe.encoder.OrderCancelRejectDecoder;

public class DRCancelRejectMessage extends CancelRejectMessage {

  public DRCancelRejectMessage(final OrderCancelRejectDecoder decoder) {
    setCancelId(decoder.cancelId());
    setCancelReplaceId(decoder.cancelReplaceId());
  }

  @Override
  public void onMatcher() {
    // Update from cancel id
    GlobalOrderBook.setOrderIdIfGreater(6, getCancelId());

    // Update from cancel replace id
    GlobalOrderBook.setOrderIdIfGreater(7, getCancelReplaceId());
  }
}
