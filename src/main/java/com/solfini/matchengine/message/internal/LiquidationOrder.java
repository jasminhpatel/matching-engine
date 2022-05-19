package com.solfini.matchengine.message.internal;

import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.orderbook.OrderBook;

/**
 *
 * @author Chris Mack
 *
 */
public class LiquidationOrder extends Order {

  public LiquidationOrder() {
    // default constructor
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public final MessageType getMessageType() {
    return MessageType.LIQUIDATION_ORDER;
  }

  @Override
  public void onMatcher() {
    final InstrumentPair instrument = InstrumentCache.getPair(getSecurityId());
    final OrderBook orderbook = instrument.getOrderBook();
    orderbook.onLiquidationOrder(this);
  }
}
