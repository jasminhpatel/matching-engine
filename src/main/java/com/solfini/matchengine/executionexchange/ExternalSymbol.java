package com.solfini.matchengine.executionexchange;

public class ExternalSymbol {
  private long id;
  private String exchange;
  private int instrumentType; // As per Tardis: 1- futures, 2- spot
  private String symbol;
  private String base;
  private String quote;
  private String prompt;
  private boolean futures;
  private boolean tradable;
  private long updated;
  private long openPricePercentage = 100_000;
  private long closePricePercentage = 100_000;
  private int priceScale;
  private int qtyScale;
  /** Minimum trade amount */
  private double minimumAmount;
  /** Maximum trade amount */
  private double maximumAmount;
  /** Amount step size. If set, any amounts must be a multiple of this */
  private double amountStepSize;
  /** Price step size. If set, any price must be a multiple of this */
  private double priceStepSize;

  private int multiplierContract;

  // Option fields
  private String kind;
  private boolean isPut;
  private boolean isCall;
  private long expiryTime;
  private double strike;

  public final String getKey() {
    return (this.getExchange() + "_" + this.getInstrumentType() + "_" + this.getBase() + "_" + this.getQuote()).toLowerCase();
  }

  public final long getId() {
    return id;
  }

  public final void setId(final long id) {
    this.id = id;
  }

  public final String getExchange() {
    return exchange;
  }

  public final void setExchange(final String exchange) {
    this.exchange = exchange;
  }

  public String getSymbol() {
    return symbol;
  }

  public final void setSymbol(final String symbol) {
    this.symbol = symbol;
  }

  public final String getBase() {
    return base;
  }

  public final void setBase(final String base) {
    this.base = base;
  }

  public final String getQuote() {
    return quote;
  }

  public final void setQuote(final String quote) {
    this.quote = quote;
  }

  public String getPrompt() {
    return prompt;
  }

  public final void setPrompt(final String prompt) {
    this.prompt = prompt;
  }

  public final boolean isFutures() {
    return futures;
  }

  public final void setFutures(final boolean futures) {
    this.futures = futures;
  }

  public final boolean isTradable() {
    return tradable;
  }

  public final void setTradable(final boolean tradable) {
    this.tradable = tradable;
  }

  public final long getUpdated() {
    return updated;
  }

  public final void setUpdated(final long updated) {
    this.updated = updated;
  }

  public final long getOpenPricePercentage() {
    return openPricePercentage;
  }

  public final void setOpenPricePercentage(final long openPricePercentage) {
    this.openPricePercentage = openPricePercentage;
  }

  public final long getClosePricePercentage() {
    return closePricePercentage;
  }

  public final void setClosePricePercentage(final long closePricePercentage) {
    this.closePricePercentage = closePricePercentage;
  }

  public final int getPriceScale() {
    return priceScale;
  }

  public final void setPriceScale(final int priceScale) {
    this.priceScale = priceScale;
  }

  public final int getQtyScale() {
    return qtyScale;
  }

  public final void setQtyScale(final int qtyScale) {
    this.qtyScale = qtyScale;
  }

  public int getInstrumentType() {
    return instrumentType;
  }

  public void setInstrumentType(final int instrumentType) {
    this.instrumentType = instrumentType;
  }

  public final int getMultiplierContract() {
    return multiplierContract;
  }

  public final void setMultiplierContract(final int multiplierContract) {
    this.multiplierContract = multiplierContract;
  }

  public final double getMinimumAmount() {
    return minimumAmount;
  }

  public final void setMinimumAmount(final double minimumAmount) {
    this.minimumAmount = minimumAmount;
  }

  public final double getMaximumAmount() {
    return maximumAmount;
  }

  public final void setMaximumAmount(final double maximumAmount) {
    this.maximumAmount = maximumAmount;
  }

  public final double getAmountStepSize() {
    return amountStepSize;
  }

  public final void setAmountStepSize(final double amountStepSize) {
    this.amountStepSize = amountStepSize;
  }

  public final double getPriceStepSize() {
    return priceStepSize;
  }

  public final void setPriceStepSize(final double priceStepSize) {
    this.priceStepSize = priceStepSize;
  }

  public final String getKind() {
    return kind;
  }

  public final void setKind(final String kind) {
    this.kind = kind;
  }

  public final boolean isPut() {
    return isPut;
  }

  public final void setPut(final boolean isPut) {
    this.isPut = isPut;
  }

  public final boolean isCall() {
    return isCall;
  }

  public final void setCall(final boolean isCall) {
    this.isCall = isCall;
  }

  public final long getExpiryTime() {
    return expiryTime;
  }

  public final void setExpiryTime(final long expiryTime) {
    this.expiryTime = expiryTime;
  }

  public final double getStrike() {
    return strike;
  }

  public final void setStrike(final double strike) {
    this.strike = strike;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("{");
    sb.append("exchange='").append(exchange).append('\'');
    sb.append(", base='").append(base).append('\'');
    sb.append(", quote='").append(quote).append('\'');
    sb.append(", prompt='").append(prompt).append('\'');
    sb.append(", futures=").append(futures);
    sb.append(", tradable=").append(tradable);
    sb.append(", updated=").append(updated);
    sb.append(", openPricePercentage=").append(openPricePercentage);
    sb.append(", closePricePercentage=").append(closePricePercentage);
    sb.append(", priceScale=").append(priceScale);
    sb.append(", qtyScale=").append(qtyScale);
    sb.append(", minimumAmount=").append(minimumAmount);
    sb.append(", maximumAmount=").append(maximumAmount);
    sb.append(", amountStepSize=").append(amountStepSize);
    sb.append(", priceStepSize=").append(priceStepSize);
    sb.append(", multiplierContract=").append(multiplierContract);
    sb.append(", kind='").append(kind).append('\'');
    sb.append(", isPut=").append(isPut);
    sb.append(", isCall=").append(isCall);
    sb.append(", expiryTime=").append(expiryTime);
    sb.append(", strike=").append(strike);
    sb.append('}');
    return sb.toString();
  }

  public String toShortString() {
    final StringBuilder sb = new StringBuilder("{");
    sb.append("exchange='").append(exchange).append('\'');
    sb.append(", base='").append(base).append('\'');
    sb.append(", quote='").append(quote).append('\'');
    sb.append(", futures=").append(futures);
    sb.append('}');
    return sb.toString();
  }
}
