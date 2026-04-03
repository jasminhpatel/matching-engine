package com.solfini.matchengine.executionexchange.xchangewrappers;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import org.knowm.xchange.Exchange;

import java.util.List;

public class XCoinbaseExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XCoinbaseExchange.class);

  public XCoinbaseExchange(final Exchange exchange) {
    super(exchange);
  }

  @Override
  public List<ExternalSymbol> getExchangeInstrumentsFull() {
    return List.of();
  }
}
