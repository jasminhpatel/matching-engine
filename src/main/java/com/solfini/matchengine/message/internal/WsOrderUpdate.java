package com.solfini.matchengine.message.internal;

public class WsOrderUpdate {
  private String clOrdId;
  private String symbol;
  private double price;
  private double quantity;
  private double fees;
  private boolean filled;
  private String feeCurrency;

  public final String getClOrdId() {
    return clOrdId;
  }

  public final void setClOrdId(final String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public final String getSymbol() {
    return symbol;
  }

  public final void setSymbol(final String symbol) {
    this.symbol = symbol;
  }

  public final double getPrice() {
    return price;
  }

  public final void setPrice(final double price) {
    this.price = price;
  }

  public final double getQuantity() {
    return quantity;
  }

  public final void setQuantity(final double quantity) {
    this.quantity = quantity;
  }

  public final double getFees() {
    return fees;
  }

  public final void setFees(final double fees) {
    this.fees = fees;
  }

  public final boolean isFilled() {
    return filled;
  }

  public final void setFilled(final boolean filled) {
    this.filled = filled;
  }

  public String getFeeCurrency() {
    return feeCurrency;
  }

  public void setFeeCurrency(String feeCurrency) {
    this.feeCurrency = feeCurrency;
  }

  @Override
  public final String toString() {
    final StringBuilder sb = new StringBuilder("{");
    sb.append("clOrdId='").append(clOrdId).append('\'');
    sb.append(", symbol='").append(symbol).append('\'');
    sb.append(", price=").append(price);
    sb.append(", quantity=").append(quantity);
    sb.append(", fees=").append(fees);
    sb.append(", filled=").append(filled);
    sb.append(", feeCurrency='").append(feeCurrency).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
