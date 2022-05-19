package com.solfini.matchengine.controller;

import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.matchengine.message.controller.ModeControlMessage;
import com.solfini.matchengine.model.orderbook.OrderBookTest;
import com.solfini.matchengine.orderbook.ArrayOrderBook;
import org.junit.Assert;
import org.junit.Test;

public class ControllerMessageTest extends OrderBookTest {

  @Test
  public void secondaryToPrimaryModeControlMessage() {

    assert InstrumentCache.getPair(12) != null;
    Assert.assertNull((InstrumentCache.getPair(12).getOrderBook()).getMarketStatus());
    ModeControlMessage modeControlMessage = new ModeControlMessage(Mode.SECONDARY, Mode.PRIMARY);
    Assert.assertEquals("ModeControlMessage (PreviousMode: SECONDARY, Mode: PRIMARY)", modeControlMessage.toString());
    modeControlMessage.onMatcher();
    Assert.assertEquals(MarketStatus.OPEN, (InstrumentCache.getPair(12).getOrderBook()).getMarketStatus());
  }
}
