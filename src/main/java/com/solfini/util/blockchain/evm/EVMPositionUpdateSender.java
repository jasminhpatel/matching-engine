package com.solfini.util.blockchain.evm;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.blockchain.model.PositionUpdateTransaction;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static com.solfini.common.Constants.*;

public class EVMPositionUpdateSender extends EVMTransactionSender {
  public static final BigInteger GAS_LIMIT_FOR_POSITION_UPDATE = Context.getGasLimitForPositionUpdate();

  private static final CustomLogger LOGGER = CustomLogger.getLogger(EVMPositionUpdateSender.class);
  private static final String SIGNER_URL = Context.getSignerUrl() + "/signApi/signPositionManagerSnapshotUpdateRequest";
  protected PositionUpdateTransaction positionUpdateTransaction;

  public EVMPositionUpdateSender(final PositionUpdateTransaction positionUpdateTransaction) {
    super(positionUpdateTransaction.getContractAddress() + "-" + positionUpdateTransaction.getId(), positionUpdateTransaction.getChainType(),
        POSITION_MANAGEMENT, positionUpdateTransaction);
    this.positionUpdateTransaction = positionUpdateTransaction;
  }

  @Override
  protected void encodeFunction() {//todo
    final Function function =
        new Function("batchUpdatePositions", List.of(new DynamicBytes(Numeric.hexStringToByteArray(preprocessBatchUpdates()))),
            Collections.emptyList());
    this.encodedFunction = FunctionEncoder.encode(function);
  }

  @Override
  protected void calculateGasLimit() {
    this.gasLimit = GAS_LIMIT_FOR_POSITION_UPDATE;
  }

  @Override
  protected void createSignRequest() {
    this.signerUrl = SIGNER_URL;

    final StringBuilder sb = new StringBuilder();
    sb.append("{").append("\"chainType\":\"").append(this.chainType).append("\",").append("\"contractAddress\":\"")
        .append(this.positionUpdateTransaction.getContractAddress()).append("\",").append("\"encodedFunction\":\"").append(this.encodedFunction)
        .append("\",").append("\"maxPriorityFee\":\"").append(this.gasFee.getMaxPriorityFeePerGas()).append("\",").append("\"maxFeePerGas\":\"")
        .append(this.gasFee.getMaxFeePerGas()).append("\",").append("\"gasLimit\":\"").append(this.gasLimit).append("\",").append("\"nonce\":\"")
        .append(this.nonce).append("\",").append("\"chainId\":").append(this.chainId).append(",")
        .append("\"systemAddress\":\"").append(this.senderAddress).append("\",").append("\"timestamp\":")
        .append(System.currentTimeMillis())//to avoid replay of signed message
        .append("}");
    this.signRequest = sb.toString();
    LOGGER.info(LOG_FMT_4, "Sign request for transactionId: ", this.transactionId, " signRequest: ", this.signRequest);
    System.out.println("Sign request for transactionId: " + this.transactionId + " signRequest: " + this.signRequest);
  }

  @Override
  protected void sendTransaction() throws Exception {
    LOGGER.info(LOG_FMT_14, "Type 2 positionUpdate attempt: ", this.attempt, " contractAddress: ", this.positionUpdateTransaction.getContractAddress(),
        " fromAddress: ", this.positionUpdateTransaction.getFromWalletAddress(), " maxPriorityFee: ", this.gasFee.getMaxPriorityFeePerGas(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " system account: ", this.senderAddress);

    super.sendTransaction();
  }

  @Override
  protected boolean processErrorResponse() {
    LOGGER.info(LOG_FMT_10, " Error in type 2 positionUpdate. contractAddress: ", this.positionUpdateTransaction.getContractAddress(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " reason: ", this.error, " transactionHash: ", this.transactionHash);
    System.out.println("Error in type 2 positionUpdate. contractAddress: " + this.positionUpdateTransaction.getContractAddress() + " maxFeePerGas: " +
        this.gasFee.getMaxFeePerGas() + " gasLimit: " + this.gasLimit + " reason: " + this.error + " transactionHash: " + this.transactionHash);

    return false;
  }

  @Override
  protected boolean processSuccessResponse() {
    LOGGER.info(LOG_FMT_12, "Type 2 positionUpdate successful. contractAddress: ", this.positionUpdateTransaction.getContractAddress(),
        " fromAddress: ", this.positionUpdateTransaction.getFromWalletAddress(), " maxPriorityFee: ", this.gasFee.getMaxPriorityFeePerGas(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " transactionHash: ", this.transactionHash);

    return true;
  }

  private String preprocessBatchUpdates() {
    StringBuilder processed = new StringBuilder("0x");
    final String snapshotIdHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(positionUpdateTransaction.getId())), 8);
    final String batchIdHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(positionUpdateTransaction.getBatchId())), 4);//todo split in to batches
    final String totalBatchesHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(positionUpdateTransaction.getNoOfBatches())), 4);
    processed.append(snapshotIdHex).append(batchIdHex).append(totalBatchesHex);

    for (PositionUpdateTransaction.BlockchainPosition position : positionUpdateTransaction.getUserPositions()) {
      final String userIdHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(position.getUserId())), 4);
      processed.append(userIdHex);
      final String assetIdHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(position.getInstrumentId())), 4);
      processed.append(assetIdHex);
      final String amountHex = zeroPadToBytes(int64ToHex(position.getQuantity()), 8);
      processed.append(amountHex);
    }

    return processed.toString();
  }

  private String zeroPadToBytes(String hexValue, int byteLength) {
    int targetLength = byteLength * 2; // 2 hex chars per byte
    return "0".repeat(Math.max(0, targetLength - hexValue.length())) + hexValue;
  }

  private String int64ToHex(long value) {
    // Convert a long (int64) to an 8-byte hex representation
    ByteBuffer buffer = ByteBuffer.allocate(8);
    buffer.putLong(value);
    return Numeric.toHexStringNoPrefix(buffer.array());
  }
}
