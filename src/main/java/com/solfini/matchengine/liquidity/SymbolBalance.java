package com.solfini.matchengine.liquidity;

import com.solfini.matchengine.executionexchange.ExternalSymbol;

public class SymbolBalance {
  private final ExchangeSubscription subscription;
  private final ExternalSymbol externalSymbol;
  private final double availableBalance;

  public SymbolBalance(final ExchangeSubscription subscription, final ExternalSymbol externalSymbol, final double availableBalance) {
    this.subscription = subscription;
    this.externalSymbol = externalSymbol;
    this.availableBalance = availableBalance;
  }

  public ExchangeSubscription getSubscription() {
    return subscription;
  }

  public ExternalSymbol getSymbolData() {
    return externalSymbol;
  }

  public double getAvailableBalance() {
    return availableBalance;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("SymbolBalance{");
    sb.append("subscriptionId=").append(subscription.getId());
    sb.append(", symbolStatus=").append(externalSymbol);
    sb.append(", availableBalance=").append(availableBalance);
    sb.append('}');
    return sb.toString();
  }
}
