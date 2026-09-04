package com.solfini.reconciliation;

import com.solfini.common.Context;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.blockchain.util.RpcUtil;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class FundManagerLoader {
  private static final String RPC_URL = PropertyReader.getProperty("FUND_MANAGER_RPC_READ_URL", "");
  private static final String FUND_MANAGER_CONTRACT_ADDRESS = PropertyReader.getProperty("FUND_MANAGER_CONTRACT_ADDRESS", "");
  private static final String CALLER_ADDRESS = PropertyReader.getProperty("FUND_MANAGER_CONTRACT_ADDRESS", "");

  public static FundManagerSnapData getUserPositions() throws IOException {
    final FundManagerSnapData blockchainSnapData = new FundManagerSnapData();
    int maxUserId =  getMaxUserId();
    final Map<Integer, Long> userPositionsMap = blockchainSnapData.getUserPositionsMap();
    final int batchSize = 300;
    int startUserId = 1;
    String snapshotId = null;
    while (startUserId <= maxUserId + 1) {
      String snapId = getUserPositions(userPositionsMap, startUserId, batchSize);
      if (snapshotId == null) {
        snapshotId = snapId;
      }
      if (!snapshotId.equalsIgnoreCase(snapId)) { //snapshot has been updated reload from the beginning
        snapshotId = snapId;
        startUserId = 1;
        userPositionsMap.clear();
        maxUserId =  getMaxUserId();
      } else {
        startUserId += batchSize;
      }
    }
    blockchainSnapData.setSnapshotId(snapshotId);
    return blockchainSnapData;
  }

  private static int getMaxUserId() throws IOException {
    final Web3j web3j = Web3j.build(new HttpService(RPC_URL));

    final Function function = new Function(
        "maxUserId",
        Collections.emptyList(),
        Collections.emptyList()
    );
    String encodedFunction = FunctionEncoder.encode(function);
    Transaction transaction = Transaction.createEthCallTransaction(CALLER_ADDRESS,
        FUND_MANAGER_CONTRACT_ADDRESS, encodedFunction);
    EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();

    if (response.hasError()) {
      throw new RuntimeException(response.getError().getMessage());
    }

    String rawResult = response.getValue();
    return Numeric.toBigInt(rawResult).intValue();
  }

  private static String getUserPositions(final Map<Integer, Long> userPositionsMap, final int start, final int size) throws IOException {
    final Web3j web3j = Web3j.build(new HttpService(RPC_URL));
    final Uint32 fromUserId = new Uint32(start);
    final Uint32 toUserId = new Uint32(start + size);

    final Function function = new Function(
        "getPositionsPaginated",
        Arrays.asList(fromUserId, toUserId),
        Collections.emptyList()
    );
    String encodedFunction = FunctionEncoder.encode(function);
    Transaction transaction = Transaction.createEthCallTransaction(CALLER_ADDRESS,
        FUND_MANAGER_CONTRACT_ADDRESS, encodedFunction);
    EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();

    if (response.hasError()) {
      throw new RuntimeException(response.getError().getMessage());
    }

    //String rawResult = response.getValue();
    //String decodedCsv = hexToAscii(rawResult);

    // Process the CSV Data
    //return parseCsv(decodedCsv, userPositionsMap);

    byte[] fullResult = Numeric.hexStringToByteArray(response.getValue());

    // Solidity returns dynamic bytes with offset + length — skip first 64 bytes
    int payloadOffset = 32 + 32; // 64 bytes
    byte[] actualPayload = Arrays.copyOfRange(fullResult, payloadOffset, fullResult.length);

    // Now decode the actual payload (e.g., userId, assetId, amount, scale)
    return decodeResponse2(actualPayload, userPositionsMap);
  }

  /**
   * Reads the real on-chain ERC20 symbol() for a token address, generically - no hardcoded
   * per-asset list to maintain as new composite members get added. Used for email titles (see
   * FundManagerV2Reconciliation.assetLabel) where the raw compositeId hex isn't recognizable to a
   * reader. Callers should treat a failure here as non-fatal (fall back to the address itself)
   * since it's a display nicety, not correctness-critical.
   */
  public static String getSymbol(final String network, final String tokenAddress) throws IOException {
    final String callerAddress = PropertyReader.getProperty("FUND_MANAGER_CONTRACT_ADDRESS", "");
    final Web3j web3j = RpcUtil.createWeb3jConnection(network, null, false, false);
    final Function function = new Function(
        "symbol",
        Collections.emptyList(),
        Collections.singletonList(new TypeReference<org.web3j.abi.datatypes.Utf8String>() {})
    );
    final String encodedFunction = FunctionEncoder.encode(function);
    final EthCall response = web3j.ethCall(
        Transaction.createEthCallTransaction(callerAddress, tokenAddress, encodedFunction),
        DefaultBlockParameterName.LATEST
    ).send();
    if (response.hasError()) {
      throw new RuntimeException(response.getError().getMessage());
    }
    final List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    if (decoded.isEmpty()) {
      throw new IllegalStateException("symbol() returned no data for " + tokenAddress);
    }
    return (String) decoded.getFirst().getValue();
  }

  public static double getBalance(final String network, final String contractAddress) throws IOException {
    boolean useSecondary = false, hasProxyError = false;
    final Web3j web3j = RpcUtil.createWeb3jConnection(network, null, useSecondary, hasProxyError);
    final String fundManagerContract = Context.getFundManagerContractByNetworkAndVersion(network, 2);

    final Function balanceOfFunction = new Function(
        "balanceOf",
        List.of(new Address(fundManagerContract)),
        List.of(new TypeReference<Uint256>() {
        })
    );

    final String encodedBalanceOf = FunctionEncoder.encode(balanceOfFunction);

    final List<Type> balanceDecoded = callAndDecodeWithRetry(
        web3j, contractAddress, encodedBalanceOf, balanceOfFunction.getOutputParameters());

    final BigInteger rawBalance = (BigInteger) balanceDecoded.getFirst().getValue();

    final Function decimalsFunction = new Function(
        "decimals",
        Collections.emptyList(),
        Collections.singletonList(new org.web3j.abi.TypeReference<Uint256>() {})
    );

    final String encodedDecimals = FunctionEncoder.encode(decimalsFunction);

    final List<Type> decimalsDecoded = callAndDecodeWithRetry(
        web3j, contractAddress, encodedDecimals, decimalsFunction.getOutputParameters());

    final BigInteger decimals = (BigInteger) decimalsDecoded.get(0).getValue();

    // 3. scale to human-readable
    final BigDecimal divisor = BigDecimal.TEN.pow(decimals.intValue());

    return new BigDecimal(rawBalance).divide(divisor).doubleValue();
  }

  /**
   * Some public RPC endpoints intermittently return an empty (but error-free) result for a
   * proxy-delegated eth_call - observed live against both Alchemy/Sepolia and rpc.apothem.network
   * for calls that succeed on a plain retry moments later. Retrying here avoids failing an entire
   * composite reconciliation over one transient empty response.
   */
  private static List<Type> callAndDecodeWithRetry(final Web3j web3j, final String contractAddress,
      final String encodedFunction, final List<TypeReference<Type>> outputParameters) throws IOException {
    // Read fresh rather than via the CALLER_ADDRESS static field: that field is frozen at
    // FundManagerLoader's first class-load, which can happen before PropertyReader has been
    // initialized depending on caller order (observed live: empty here even though the same key
    // read correctly moments earlier in the same run) - a fresh read is immune to that ordering.
    final String callerAddress = PropertyReader.getProperty("FUND_MANAGER_CONTRACT_ADDRESS", "");
    final int maxAttempts = 3;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      final EthCall response = web3j.ethCall(
          Transaction.createEthCallTransaction(callerAddress, contractAddress, encodedFunction),
          DefaultBlockParameterName.LATEST
      ).send();
      if (response.hasError()) {
        throw new RuntimeException(response.getError().getMessage());
      }
      final List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), outputParameters);
      if (!decoded.isEmpty()) {
        return decoded;
      }
      if (attempt < maxAttempts) {
        try {
          Thread.sleep(1000L * attempt);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    throw new IllegalStateException("eth_call to " + contractAddress
        + " returned an empty result after " + maxAttempts + " attempts (data: " + encodedFunction + ")");
  }

  private static String decodeResponse2(final byte[] data, final Map<Integer, Long> userPositionsMap) {
    final ByteBuffer buffer = ByteBuffer.wrap(data);

    // First 8 bytes: snapshotId (uint64)
    int offset = 8;
    byte[] snapshotIdBytes = new byte[8];
    System.arraycopy(data, 0, snapshotIdBytes, 0, 8);
    long snapshotId = new BigInteger(snapshotIdBytes).longValue();

    while (offset + 4 + 32 + 1 <= data.length) {  // 4 bytes + 32 bytes + 1 byte

      // Extract userId (uint32 - 4 bytes)
      byte[] userIdBytes = new byte[4];
      System.arraycopy(data, offset, userIdBytes, 0, 4);
      int userId = ByteBuffer.wrap(userIdBytes).getInt();
      offset += 4;

      // Extract withdrawable amount (int256 - 32 bytes)
      byte[] withdrawableBytes = new byte[32];
      System.arraycopy(data, offset, withdrawableBytes, 0, 32);
      long amount = new BigInteger(withdrawableBytes).longValue();
      offset += 32;

      // Extract scale (int8 - 1 byte)
      byte scale = data[offset];
      offset += 1;

      userPositionsMap.put(userId, amount);
    }

    return String.valueOf(snapshotId);
  }

  private static String decodeResponse(final byte[] data, final Map<Integer, Long> userPositionsMap) {
    final ByteBuffer buffer = ByteBuffer.wrap(data);

    // First 8 bytes: snapshotId (uint64)
    long snapshotId = buffer.getLong();

    // Each record is 4+4+8+1 = 17 bytes
    while (buffer.remaining() >= 13) {
      int userId = buffer.getInt(); // 4 bytes
      long amount = buffer.getLong(); // 8 bytes
      byte scale = buffer.get(); // 1 byte
      userPositionsMap.put(userId, amount);
    }

    return String.valueOf(snapshotId);
  }

  // Convert Hex String to ASCII (Ethereum Strings are returned in Hex)
  private static String hexToAscii(String hex) {
    if (hex.startsWith("0x")) {
      hex = hex.substring(2);  // Remove "0x"
    }
    StringBuilder output = new StringBuilder();
    for (int i = 0; i < hex.length(); i += 2) {
      String str = hex.substring(i, i + 2);
      int decimal = Integer.parseInt(str, 16);
      if (decimal > 0) {  // Ignore null characters
        output.append((char) decimal);
      }
    }
    return output.toString();
  }

  // Parse CSV Data
  private static String parseCsv(String csvData, final Map<Integer, Long> userPositionsMap) {
    String[] rows = csvData.split("\n");
    String snapId = rows[0].split(":")[1];
    for (int i = 2; i < rows.length; i++) { // Skip 2 header rows
      String[] columns = rows[i].split(":");
      if (columns.length >= 4) {
        int userId = StringUtil.toInt(columns[0]);
          //String userAddress = columns[1];
        long amount = StringUtil.toLong(columns[2]);
        int scale = StringUtil.toInt(columns[3]);
        userPositionsMap.put(userId, amount);
      }
    }
    return snapId;
  }
}
