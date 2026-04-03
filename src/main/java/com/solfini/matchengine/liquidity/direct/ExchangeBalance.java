package com.solfini.matchengine.liquidity.direct;

import java.util.Date;
import org.knowm.xchange.currency.Currency;

public final class ExchangeBalance {
  private Currency currency;

  // Invariant:
  // total = available + frozen - borrowed + loaned + withdrawing + depositing;
  private double total;
  private double available;
  private double frozen;
  private double loaned;
  private double borrowed;
  private double withdrawing;
  private double depositing;
  private Date timestamp;

  public ExchangeBalance(final Currency currency, final double total, final double available, final double frozen, final double loaned,
      final double borrowed, final double withdrawing, final double depositing, final Date timestamp) {
    this.currency = currency;
    this.total = total;
    this.available = available;
    this.frozen = frozen;
    this.loaned = loaned;
    this.borrowed = borrowed;
    this.withdrawing = withdrawing;
    this.depositing = depositing;
    this.timestamp = timestamp;
  }

  public void set(final Currency currency, final double total, final double available, final double frozen, final double loaned,
      final double borrowed, final double withdrawing, final double depositing, final Date timestamp) {
    this.currency = currency;
    this.total = total;
    this.available = available;
    this.frozen = frozen;
    this.loaned = loaned;
    this.borrowed = borrowed;
    this.withdrawing = withdrawing;
    this.depositing = depositing;
    this.timestamp = timestamp;
  }

  public final void setCurrency(final Currency currency) {
    this.currency = currency;
  }

  public final Currency getCurrency() {
    return currency;
  }

  public final double getTotal() {
    return total;
  }

  public final void setTotal(final double total) {
    this.total = total;
  }

  public final double getAvailable() {
    return available;
  }

  public final void setAvailable(final double available) {
    this.available = available;
  }

  public final double getFrozen() {
    return frozen;
  }

  public final void setFrozen(final double frozen) {
    this.frozen = frozen;
  }

  public final double getLoaned() {
    return loaned;
  }

  public final void setLoaned(final double loaned) {
    this.loaned = loaned;
  }

  public final double getBorrowed() {
    return borrowed;
  }

  public final void setBorrowed(final double borrowed) {
    this.borrowed = borrowed;
  }

  public final double getWithdrawing() {
    return withdrawing;
  }

  public final void setWithdrawing(final double withdrawing) {
    this.withdrawing = withdrawing;
  }

  public final double getDepositing() {
    return depositing;
  }

  public final void setDepositing(final double depositing) {
    this.depositing = depositing;
  }

  public final Date getTimestamp() {
    return timestamp;
  }

  public final void setTimestamp(final Date timestamp) {
    this.timestamp = timestamp;
  }



}
