package com.solfini.util.blockchain.model;

import java.math.BigInteger;

public class GasFee {
  private BigInteger baseFeePerGas;
  private BigInteger maxPriorityFeePerGas;
  private BigInteger maxFeePerGas;

  public GasFee(final BigInteger baseFeePerGas, final BigInteger maxPriorityFeePerGas, final BigInteger maxFeePerGas) {
    this.baseFeePerGas = baseFeePerGas;
    this.maxPriorityFeePerGas = maxPriorityFeePerGas;
    this.maxFeePerGas = maxFeePerGas;
  }

  public BigInteger getBaseFeePerGas() {
    return baseFeePerGas;
  }

  public void setBaseFeePerGas(final BigInteger baseFeePerGas) {
    this.baseFeePerGas = baseFeePerGas;
  }

  public BigInteger getMaxPriorityFeePerGas() {
    return maxPriorityFeePerGas;
  }

  public void setMaxPriorityFeePerGas(final BigInteger maxPriorityFeePerGas) {
    this.maxPriorityFeePerGas = maxPriorityFeePerGas;
  }

  public BigInteger getMaxFeePerGas() {
    return maxFeePerGas;
  }

  public void setMaxFeePerGas(final BigInteger maxFeePerGas) {
    this.maxFeePerGas = maxFeePerGas;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(" gasPrice: ").append(baseFeePerGas);
    sb.append(", maxPriorityFee: ").append(maxPriorityFeePerGas);
    sb.append(", maxFeePerGas: ").append(maxFeePerGas);

    return sb.toString();
  }
}
