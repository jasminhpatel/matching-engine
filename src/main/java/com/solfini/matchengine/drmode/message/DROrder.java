package com.solfini.matchengine.drmode.message;

import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBook;

public class DROrder extends Order {

  private long orderIndex;

  public long getOrderIndex() {
    return orderIndex;
  }

  public void setOrderIndex(final long orderIndex) {
    this.orderIndex = orderIndex;
  }

  @Override
  public void onMatcher() {
    if (getUser() == null) {
      final Message reject = NewOrderSingleHandler.lookupUser(this);
      if (reject != null) {
        reject.onMatcher();
        return;
      }
    }

    final InstrumentPair instrument = InstrumentCache.getPair(securityId);
    if (null != instrument) {
      final OrderBook orderbook = instrument.getOrderBook();
      orderbook.addOrderDR(this);
    }
  }
}
