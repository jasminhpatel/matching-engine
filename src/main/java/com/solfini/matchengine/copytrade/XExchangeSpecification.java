package com.solfini.matchengine.copytrade;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;

public class XExchangeSpecification extends ExchangeSpecification {
  public XExchangeSpecification(String exchangeClassName) {
    super(exchangeClassName);
  }

  public XExchangeSpecification(Class<? extends Exchange> exchangeClass) {
    super(exchangeClass);
  }
}
