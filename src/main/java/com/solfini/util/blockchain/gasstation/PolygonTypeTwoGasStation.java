package com.solfini.util.blockchain.gasstation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.blockchain.model.GasFee;
import com.solfini.util.blockchain.util.RpcUtil;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;

public class PolygonTypeTwoGasStation implements IGasStation {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PolygonTypeTwoGasStation.class);
  //private static final String GAS_STATION_URL = Context.getPolygonGasStationUrl();

  /*@Override
  public GasFee getGasPrice(Web3j web3j, final GasFee gasFee, final double factor, final String chainType) throws Exception {
    *//*try {
      final String response = get(GAS_STATION_URL);
      final MaticGasResponseV2 maticGasResponseV2 = MAPPER.readValue(response, MaticGasResponseV2.class);
      BigInteger maxPriorityFee = Convert.toWei(String.valueOf(
          Math.ceil(maticGasResponseV2.getFast().getMaxPriorityFee())), Convert.Unit.GWEI).toBigInteger();

      maxPriorityFee = addSafetyFactor(maxPriorityFee, gasFee.getMaxPriorityFee(), factor);

      maxPriorityFee = setMinMax(maxPriorityFee, Context.getPolygonMinPriorityGasPrice(), Context.getPolygonMaxPriorityGasPrice(), chainType);
      BigInteger maxFee = Context.getPolygonMaxFeePerGas();

      gasFee.setMaxPriorityFee(maxPriorityFee);
      gasFee.setMaxFeePerGas(maxFee);

    } catch (final Exception e) {
      if (web3j == null) {
        web3j = Web3j.build(new HttpService(Context.getWeb3Provider(chainType)));
      }
      BigInteger maxPriorityFee = defaultMaxPriorityGasFeePerGas(web3j);
      maxPriorityFee = addSafetyFactor(maxPriorityFee, gasFee.getMaxPriorityFee(), factor);
      maxPriorityFee = setMinMax(maxPriorityFee, Context.getPolygonMinPriorityGasPrice(), Context.getPolygonMaxPriorityGasPrice(), chainType);

      gasFee.setMaxPriorityFee(maxPriorityFee);
      gasFee.setMaxFeePerGas(Context.getPolygonMaxFeePerGas());
    }*//*
    if (web3j == null) {
      web3j = RpcUtil.createWeb3jConnection(chainType, null, false, false);
    }
    BigInteger maxPriorityFee = defaultMaxPriorityGasFeePerGas(web3j);
    maxPriorityFee = addSafetyFactor(maxPriorityFee, gasFee.getMaxPriorityFeePerGas(), factor);
    maxPriorityFee = setMinMax(maxPriorityFee, Context.getPolygonMinPriorityGasPrice(), Context.getPolygonMaxPriorityGasPrice(), chainType);

    gasFee.setMaxPriorityFeePerGas(maxPriorityFee);
    BigInteger baseFee = getBaseFee(web3j);
    BigInteger maxFeePerGas = baseFee.multiply(BigInteger.valueOf(3)).add(maxPriorityFee);
    gasFee.setMaxFeePerGas(maxFeePerGas);

    return gasFee;
  }*/

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MaticGasResponseV2 {
    private Fee safeLow;
    private Fee standard;
    private Fee fast;
    private Double estimatedBaseFee;
    private long blockTime;
    private long blockNumber;

    public Fee getSafeLow() {
      return safeLow;
    }

    public void setSafeLow(final Fee safeLow) {
      this.safeLow = safeLow;
    }

    public Fee getStandard() {
      return standard;
    }

    public void setStandard(final Fee standard) {
      this.standard = standard;
    }

    public Fee getFast() {
      return fast;
    }

    public void setFast(final Fee fast) {
      this.fast = fast;
    }

    public Double getEstimatedBaseFee() {
      return estimatedBaseFee;
    }

    public void setEstimatedBaseFee(final Double estimatedBaseFee) {
      this.estimatedBaseFee = estimatedBaseFee;
    }

    public long getBlockTime() {
      return blockTime;
    }

    public void setBlockTime(final long blockTime) {
      this.blockTime = blockTime;
    }

    public long getBlockNumber() {
      return blockNumber;
    }

    public void setBlockNumber(final long blockNumber) {
      this.blockNumber = blockNumber;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Fee {
    private Double maxPriorityFee;
    private Double maxFee;

    public Double getMaxPriorityFee() {
      return maxPriorityFee;
    }

    public void setMaxPriorityFee(final Double maxPriorityFee) {
      this.maxPriorityFee = maxPriorityFee;
    }

    public Double getMaxFee() {
      return maxFee;
    }

    public void setMaxFee(final Double maxFee) {
      this.maxFee = maxFee;
    }
  }
}
