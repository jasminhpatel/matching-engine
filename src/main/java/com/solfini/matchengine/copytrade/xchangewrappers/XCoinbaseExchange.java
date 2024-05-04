package com.solfini.matchengine.copytrade.xchangewrappers;

import com.solfini.common.CustomLogger;
import org.knowm.xchange.Exchange;

public class XCoinbaseExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XCoinbaseExchange.class);

  public XCoinbaseExchange(Exchange exchange) {
    super(exchange);
  }
}
