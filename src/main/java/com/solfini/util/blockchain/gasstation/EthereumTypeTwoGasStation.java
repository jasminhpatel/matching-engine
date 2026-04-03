package com.solfini.util.blockchain.gasstation;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.blockchain.model.GasFee;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthFeeHistory;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.util.List;

public class EthereumTypeTwoGasStation implements IGasStation {
/*  @Override
  public GasFee getGasPrice(Web3j web3j, final GasFee gasFee, final double factor, final String chainType) throws Exception {
    if (web3j == null) {
      web3j = Web3j.build(new HttpService(Context.getWeb3Provider(chainType)));
    }
    BigInteger maxPriorityFee = defaultMaxPriorityGasFeePerGas(web3j);
    maxPriorityFee = addSafetyFactor(maxPriorityFee, gasFee.getMaxPriorityFeePerGas(), factor);
    maxPriorityFee = setMinMax(maxPriorityFee, Context.getEthereumMinPriorityGasPrice(), Context.getEthereumMaxPriorityGasPrice(), chainType);

    gasFee.setMaxPriorityFeePerGas(maxPriorityFee);
    BigInteger baseFee = getBaseFee(web3j);
    BigInteger maxFeePerGas = baseFee.multiply(BigInteger.valueOf(3)).add(maxPriorityFee);
    gasFee.setMaxFeePerGas(maxFeePerGas);

    return gasFee;
  }*/

}
