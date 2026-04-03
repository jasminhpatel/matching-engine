package com.solfini.util.blockchain.gasstation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Context;
import com.solfini.util.blockchain.model.GasFee;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;

import static com.solfini.common.Constants.*;

public interface IGasStation {
  ObjectMapper MAPPER = new ObjectMapper();

  //GasFee getGasPrice(final Web3j web3j, final GasFee gasFee, final double factor, final String chainType) throws Exception;

  default String get(final String httpsURL) {
    final StringBuilder responseData = new StringBuilder();
    try {
      final URL url = new URL(httpsURL);
      final HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
      con.setRequestMethod("GET");
      con.setRequestProperty("User-Agent", "Mozilla/5.0");
      con.setRequestProperty("Accept","*/*");
      InputStream ins;
      if (con.getResponseCode() >= 400) {
        ins = con.getErrorStream();
      } else {
        ins = con.getInputStream();
      }
      final BufferedReader in = new BufferedReader(new InputStreamReader(ins));
      String str;
      while ((str = in.readLine()) != null) {
        responseData.append(str);
      }
      in.close();

    } catch (final IOException e) {
      e.printStackTrace();
    }
    return responseData.toString();
  }

  default BigInteger defaultGasPrice(final Web3j web3j) throws IOException {
    return web3j.ethGasPrice().send().getGasPrice();
  }

  // Function to Get Max Priority Fee
  default BigInteger defaultMaxPriorityGasFeePerGas(final Web3j web3j) throws IOException {
    final EthMaxPriorityFeePerGas response = web3j.ethMaxPriorityFeePerGas().send();
    return response.getMaxPriorityFeePerGas();
  }

  // Function to Get Base Fee for EIP-1559 Transactions
  default BigInteger getBaseFee(final Web3j web3j) throws Exception {
    EthBlock block = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
    return block.getBlock().getBaseFeePerGas();
  }

  default BigInteger setMinMax(final BigInteger price, final BigInteger min, final BigInteger max, final String chainType) {
    if (ETHEREUM.equalsIgnoreCase(chainType) || SEPOLIA.equalsIgnoreCase(chainType)) {
      if (price.compareTo(Context.getEthereumMaxPriorityGasPrice()) > 0) {
        return max;
      } else if (price.compareTo(Context.getEthereumMinPriorityGasPrice()) < 0) {
        return min;
      } else {
        return price;
      }
    } else if (POLYGON.equalsIgnoreCase(chainType) || POLYGON_AMOY.equalsIgnoreCase(chainType)) {
      if (price.compareTo(Context.getPolygonMaxPriorityGasPrice()) > 0) {
        return max;
      } else if (price.compareTo(Context.getPolygonMinPriorityGasPrice()) < 0) {
        return min;
      } else {
        return price;
      }
    }
    return BigInteger.ZERO;
  }

  default BigInteger getMax(final BigInteger a, final BigInteger b) {
    if (a.compareTo(b) >= 0) {
      return a;
    } else {
      return b;
    }
  }

  default BigInteger addSafetyFactor(final BigInteger price, final BigInteger previousPrice, final double factor) {
    BigDecimal adjustedPrice = new BigDecimal(getMax(price, previousPrice));

    adjustedPrice = adjustedPrice.multiply(BigDecimal.valueOf(factor));

    return adjustedPrice.toBigInteger();
  }
}
