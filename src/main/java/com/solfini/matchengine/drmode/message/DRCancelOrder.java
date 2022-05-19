package com.solfini.matchengine.drmode.message;

import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.orderbook.GlobalOrderBook;
import com.solfini.matchengine.orderbook.OrderBook;

public class DRCancelOrder extends CancelOrder {

  public DRCancelOrder() {
    // default constructor
  }

  @Override
  public void onMatcher() {
    GlobalOrderBook.setOrderIdIfGreater(5, getCancelId());

    final InstrumentPair instrumentPair = InstrumentCache.getPair(securityId);
    if (null != instrumentPair) {
      final OrderBook orderbook = instrumentPair.getOrderBook();
      orderbook.cancelOrderDR(this);
    }
  }
}
