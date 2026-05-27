package com.solfini.util.blockchain.gasstation;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.blockchain.model.GasFee;
import com.solfini.util.blockchain.util.RpcUtil;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthFeeHistory;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.http.HttpService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.solfini.common.Constants.*;
import static com.solfini.common.Constants.POLYGON_AMOY;

public class GasStationUtil {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(GasStationUtil.class);

  private static final IGasStation POLYGON_TYPE_TWO_GAS_STATION = new PolygonTypeTwoGasStation();
  private static final IGasStation ETHEREUM_TYPE_TWO_GAS_STATION = new EthereumTypeTwoGasStation();

/*  public static GasFee getGasFees(final Web3j web3j, final GasFee gasFee, final double multiplicationFactor,
      final String chainType) throws Exception {
    return getGasStation(chainType).getGasPrice(web3j, gasFee, multiplicationFactor, chainType);
  }*/

  private static IGasStation getGasStation(final String chainType) {
    if (ETHEREUM.equalsIgnoreCase(chainType) || SEPOLIA.equalsIgnoreCase(chainType)) {
      return ETHEREUM_TYPE_TWO_GAS_STATION;
    } else {
      return POLYGON_TYPE_TWO_GAS_STATION;
    }
  }

  public static GasFee getGasFees(Web3j web3j, final GasFee gasFee, final double factor, final String chainType) throws Exception {
    if (web3j == null) {
      web3j = RpcUtil.createWeb3jConnection(chainType, null, false, false);
    }
    final GasFee newGasFee = fetchGasFees(web3j);
    if (newGasFee == null) {
      return gasFee;
    }
    BigInteger maxPriorityFeePerGas = newGasFee.getMaxPriorityFeePerGas();
    maxPriorityFeePerGas = addSafetyFactor(maxPriorityFeePerGas, gasFee.getMaxPriorityFeePerGas(), factor);
    maxPriorityFeePerGas = setMinMax(maxPriorityFeePerGas, Context.getEthereumMinPriorityGasPrice(), Context.getEthereumMaxPriorityGasPrice(), chainType);

    gasFee.setBaseFeePerGas(newGasFee.getBaseFeePerGas());
    gasFee.setMaxPriorityFeePerGas(maxPriorityFeePerGas);
    BigInteger baseFee = newGasFee.getBaseFeePerGas();
    BigInteger maxFeePerGas = baseFee.multiply(BigInteger.valueOf(2)).add(maxPriorityFeePerGas);
    gasFee.setMaxFeePerGas(maxFeePerGas);

    return gasFee;
  }

  /**
   * Fetch gas fees (EIP-1559 if supported, fallback to legacy gas price).
   */
  public static GasFee fetchGasFees(final Web3j web3j) throws Exception {
    try {
      final EthFeeHistory feeHistory = web3j.ethFeeHistory(
          1, // last 1 block
          DefaultBlockParameterName.LATEST,
          Arrays.asList(50.0) // median percentile for priority fee
      ).send();

      if (feeHistory.hasError()) {
        LOGGER.error("Error in getting type 2 gas prices. " + feeHistory.getError().getMessage());
        // Some chains/networks (e.g. XDC testnet) may not support eth_feeHistory.
        // In that case, fall back to legacy eth_gasPrice so gas isn't left as 0.
        EthGasPrice legacy = web3j.ethGasPrice().send();
        BigInteger gasPrice = legacy.getGasPrice();
        return new GasFee(gasPrice, BigInteger.ZERO, gasPrice);
      }

      // Base fee
      final List<BigInteger> baseFees = feeHistory.getResult().getBaseFeePerGas();
      BigInteger baseFee = baseFees != null && !baseFees.isEmpty() ? baseFees.get(0) : BigInteger.ZERO;

      // Priority fee (tip)
      final List<List<BigInteger>> rewards = feeHistory.getResult().getReward();
      BigInteger priorityFee = BigInteger.ZERO;
      if (rewards != null && !rewards.isEmpty() && !rewards.get(0).isEmpty()) {
        priorityFee = rewards.get(0).get(0);
      }

      final BigInteger maxFee = baseFee.add(priorityFee);

      return new GasFee(baseFee, priorityFee, maxFee);

    } catch (Exception e) {
      // Fallback for non-EIP-1559 chains
      EthGasPrice legacy = web3j.ethGasPrice().send();
      BigInteger gasPrice = legacy.getGasPrice();
      return new GasFee(gasPrice, BigInteger.ZERO, gasPrice);
    }
  }

  private static BigInteger addSafetyFactor(final BigInteger price, final BigInteger previousPrice, final double factor) {
    BigDecimal adjustedPrice = new BigDecimal(getMax(price, previousPrice));

    adjustedPrice = adjustedPrice.multiply(BigDecimal.valueOf(factor));

    return adjustedPrice.toBigInteger();
  }

  private static BigInteger setMinMax(final BigInteger price, final BigInteger min, final BigInteger max, final String chainType) {
    if (ETHEREUM.equalsIgnoreCase(chainType) || SEPOLIA.equalsIgnoreCase(chainType)) {
      if (price.compareTo(Context.getEthereumMaxPriorityGasPrice()) > 0) {
        //return max;
        return Context.getEthereumMaxPriorityGasPrice();
      } else if (price.compareTo(Context.getEthereumMinPriorityGasPrice()) < 0) {
        //return min;
        return Context.getEthereumMinPriorityGasPrice();
      } else {
        return price;
      }
    } else if (POLYGON.equalsIgnoreCase(chainType) || POLYGON_AMOY.equalsIgnoreCase(chainType)) {
      if (price.compareTo(Context.getPolygonMaxPriorityGasPrice()) > 0) {
        //return max;
        return Context.getPolygonMaxPriorityGasPrice();
      } else if (price.compareTo(Context.getPolygonMinPriorityGasPrice()) < 0) {
        //return min;
        return Context.getPolygonMinPriorityGasPrice();
      } else {
        return price;
      }
    } else if (XDC.equalsIgnoreCase(chainType) || XDC_APOTHEM.equalsIgnoreCase(chainType)) {
      if (price.compareTo(Context.getXdcMaxPriorityGasPrice()) > 0) {
        //return max;
        return Context.getPolygonMaxPriorityGasPrice();
      } else if (price.compareTo(Context.getXdcMinPriorityGasPrice()) < 0) {
        //return min;
        return Context.getXdcMinPriorityGasPrice();
      } else {
        return price;
      }
    }
    return BigInteger.ZERO;
  }

  private static BigInteger getMax(final BigInteger a, final BigInteger b) {
    if (a.compareTo(b) >= 0) {
      return a;
    } else {
      return b;
    }
  }
}
