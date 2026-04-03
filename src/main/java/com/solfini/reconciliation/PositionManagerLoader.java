package com.solfini.reconciliation;

import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.utils.Numeric;
import uk.co.real_logic.artio.fields.DecimalFloat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

public class PositionManagerLoader {
  private static final String RPC_URL = PropertyReader.getProperty("POSITION_MANAGER_RPC_READ_URL", "");
  private static final String CONTRACT_ADDRESS = PropertyReader.getProperty("POSITION_MANAGER_CONTRACT_ADDRESS", "");
  private static final String CALLER_ADDRESS = PropertyReader.getProperty("POSITION_MANAGER_CALLER_ADDRESS", "");

  public static PositionManagerSnapData getUserPositions() throws IOException {
    final PositionManagerSnapData blockchainSnapData = new PositionManagerSnapData();
    int maxUserId =  getMaxUserId();
    int maxAssetId = getMaxAssetId();
    final Map<Integer, PositionManagerSnapData.UserPositions> userPositionsMap = blockchainSnapData.getUserPositionsMap();
    final int maxUsersPerBatch = 100;
    final int maxAssetsPerBatch = maxAssetId;// There is a max limit for the return data. We may have to use this if one user has 300+ assets.
    int startUserId = 1;
    String snapshotId = null;
    while (startUserId <= maxUserId + 1) {
      String snapId = getUserPositions(userPositionsMap, startUserId, startUserId + maxUsersPerBatch, 1, maxAssetId);//load all assets of the user.
      if (snapshotId == null) {
        snapshotId = snapId;
      }
      if (!snapshotId.equalsIgnoreCase(snapId)) { //snapshot has been updated reload from the beginning
        snapshotId = snapId;
        startUserId = 1;
        userPositionsMap.clear();
        maxUserId =  getMaxUserId();
      } else {
        startUserId += maxUsersPerBatch;
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
    Transaction transaction = Transaction.createEthCallTransaction(CALLER_ADDRESS, CONTRACT_ADDRESS, encodedFunction);
    EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();

    if (response.hasError()) {
      throw new RuntimeException(response.getError().getMessage());
    }

    String rawResult = response.getValue();
    return Numeric.toBigInt(rawResult).intValue();
  }

  private static int getMaxAssetId() throws IOException {
    final Web3j web3j = Web3j.build(new HttpService(RPC_URL));

    final Function function = new Function(
        "maxAssetId",
        Collections.emptyList(),
        Collections.emptyList()
    );
    String encodedFunction = FunctionEncoder.encode(function);
    Transaction transaction = Transaction.createEthCallTransaction(CALLER_ADDRESS, CONTRACT_ADDRESS, encodedFunction);
    EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();

    if (response.hasError()) {
      throw new RuntimeException(response.getError().getMessage());
    }

    String rawResult = response.getValue();
    return Numeric.toBigInt(rawResult).intValue();
  }

  private static String getUserPositions(final Map<Integer, PositionManagerSnapData.UserPositions> userPositionsMap, final int fromUser,
      final int toUser, final int fromAsset, final int toAsset) throws IOException {
    final Web3j web3j = Web3j.build(new HttpService(RPC_URL));
    final Uint32 fromUserId = new Uint32(fromUser);
    final Uint32 toUserId = new Uint32(toUser);
    final Uint32 fromAssetId = new Uint32(fromAsset);
    final Uint32 toAssetId = new Uint32(toAsset);

    final Function function = new Function(
        "getPositionsPaginated",
        Arrays.asList(fromUserId, toUserId, fromAssetId, toAssetId),
        Collections.emptyList()
    );
    String encodedFunction = FunctionEncoder.encode(function);
    Transaction transaction = Transaction.createEthCallTransaction(CALLER_ADDRESS, CONTRACT_ADDRESS, encodedFunction);
    EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();

    if (response.hasError()) {
      throw new RuntimeException(response.getError().getMessage());
    }

    //String rawResult = response.getValue();
    //String decodedCsv = hexToAscii(rawResult);
    // Process the CSV Data
    //return parseCsv(decodedCsv, userPositionsMap);

    //byte[] result = Numeric.hexStringToByteArray(response.getValue());
    //return decodeResponse(result, userPositionsMap);

    byte[] fullResult = Numeric.hexStringToByteArray(response.getValue());

    // Solidity returns dynamic bytes with offset + length — skip first 64 bytes
    int payloadOffset = 32 + 32; // 64 bytes
    byte[] actualPayload = Arrays.copyOfRange(fullResult, payloadOffset, fullResult.length);

    // Now decode the actual payload (e.g., userId, assetId, amount, scale)
    return decodeResponse(actualPayload, userPositionsMap);
  }

  private static String decodeResponse(final byte[] data, final Map<Integer, PositionManagerSnapData.UserPositions> userPositionsMap) {
    final ByteBuffer buffer = ByteBuffer.wrap(data);

    // First 8 bytes: snapshotId (uint64)
    long snapshotId = buffer.getLong();

    // Each record is 4+4+8+1 = 17 bytes
    while (buffer.remaining() >= 17) {
      int userId = buffer.getInt(); // 4 bytes
      int assetId = buffer.getInt(); // 4 bytes
      long amount = buffer.getLong(); // 8 bytes
      byte scale = buffer.get(); // 1 byte
      final PositionManagerSnapData.UserPositions userPositions = userPositionsMap.computeIfAbsent(userId, v -> new PositionManagerSnapData.UserPositions());
      PositionManagerSnapData.Position position = new PositionManagerSnapData.Position();
      position.setAssetId(assetId);
      position.setBalance(new DecimalFloat(amount, scale));
      userPositions.getAssetPositions().put(position.getAssetId(), position);
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
  private static String parseCsv(String csvData, final Map<Integer, PositionManagerSnapData.UserPositions> userPositionsMap) {
    String[] rows = csvData.split("\n");
    String snapId = rows[0].split(":")[1];
    for (int i = 2; i < rows.length; i++) { // Skip 2 header rows
      String[] columns = rows[i].split(",");
      if (columns.length >= 3) {
        int userId = StringUtil.toInt(columns[0]);
        final PositionManagerSnapData.UserPositions userPositions = userPositionsMap.computeIfAbsent(userId, v -> new PositionManagerSnapData.UserPositions());
        //String userAddress = columns[1];
        String assetsData = columns[2];
        String[] assets = assetsData.split(";");
        for (String asset : assets) {
          String[] assetDetails = asset.split(":");
          if (assetDetails.length == 4) {
            PositionManagerSnapData.Position position = new PositionManagerSnapData.Position();
            position.setAssetId(StringUtil.toInt(assetDetails[0]));
            position.setBalance(new DecimalFloat(StringUtil.toLong(assetDetails[2]), StringUtil.toInt(assetDetails[3])));
            userPositions.getAssetPositions().put(position.getAssetId(), position);
          }
        }
      }
    }
    return snapId;
  }
}
