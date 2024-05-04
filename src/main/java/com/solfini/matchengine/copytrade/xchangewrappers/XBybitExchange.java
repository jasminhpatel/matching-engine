package com.solfini.matchengine.copytrade.xchangewrappers;

import com.solfini.common.CustomLogger;
import org.knowm.xchange.Exchange;

public class XBybitExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XBybitExchange.class);

  public XBybitExchange(Exchange exchange) {
    super(exchange);
  }
}
