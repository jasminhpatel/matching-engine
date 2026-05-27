package com.solfini.util.blockchain.evm;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.blockchain.model.WithdrawableAmountUpdateTransaction;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static com.solfini.common.Constants.*;

public class EVMWithdrawableAmountUpdateSender extends EVMTransactionSender {
  public static final BigInteger GAS_LIMIT_FOR_WITHDRAWABLE_AMOUNT_UPDATE = Context.getGasLimitForWithdrawableAmountUpdate();

  private static final CustomLogger LOGGER = CustomLogger.getLogger(EVMWithdrawableAmountUpdateSender.class);
  private static final String SIGNER_URL = Context.getSignerUrl() + "/signApi/signFundManagerSnapshotUpdateRequest";
  protected WithdrawableAmountUpdateTransaction withdrawableAmountUpdateTransaction;

  public EVMWithdrawableAmountUpdateSender(final WithdrawableAmountUpdateTransaction withdrawableAmountUpdateTransaction) {
    super(withdrawableAmountUpdateTransaction.getContractAddress() + "-" + withdrawableAmountUpdateTransaction.getId(), withdrawableAmountUpdateTransaction.getChainType(),
        FUND_MANAGEMENT, withdrawableAmountUpdateTransaction);
    this.withdrawableAmountUpdateTransaction = withdrawableAmountUpdateTransaction;
  }

  @Override
  protected void encodeFunction() {
    if (withdrawableAmountUpdateTransaction.getContractVersion() == 2) {
      final Function function =
          new Function("batchSetAvailableAssetBalances",
              List.of(new Address(withdrawableAmountUpdateTransaction.getTokenAddress()), new DynamicBytes(Numeric.hexStringToByteArray(preprocessBatchUpdates2()))),
              Collections.emptyList());
      this.encodedFunction = FunctionEncoder.encode(function);
    } else {
      final Function function =
          new Function("batchUpdatePositions",
              List.of(new DynamicBytes(Numeric.hexStringToByteArray(preprocessBatchUpdates()))),
              Collections.emptyList());
      this.encodedFunction = FunctionEncoder.encode(function);
    }
  }

  @Override
  protected void calculateGasLimit() {
    this.gasLimit = GAS_LIMIT_FOR_WITHDRAWABLE_AMOUNT_UPDATE;
  }

  @Override
  protected void createSignRequest() {
    this.signerUrl = SIGNER_URL;

    final StringBuilder sb = new StringBuilder();
    sb.append("{").append("\"chainType\":\"").append(this.chainType).append("\",").append("\"contractAddress\":\"")
        .append(this.withdrawableAmountUpdateTransaction.getContractAddress()).append("\",").append("\"encodedFunction\":\"").append(this.encodedFunction)
        .append("\",").append("\"maxPriorityFee\":\"").append(this.gasFee.getMaxPriorityFeePerGas()).append("\",").append("\"maxFeePerGas\":\"")
        .append(this.gasFee.getMaxFeePerGas()).append("\",").append("\"gasLimit\":\"").append(this.gasLimit).append("\",").append("\"nonce\":\"")
        .append(this.nonce).append("\",").append("\"chainId\":").append(this.chainId).append(",")
        .append("\"systemAddress\":\"").append(this.senderAddress).append("\",").append("\"timestamp\":")
        .append(System.currentTimeMillis())//to avoid replay of signed message
        .append("}");
    System.out.println(sb.toString());
    this.signRequest = sb.toString();
    LOGGER.info(LOG_FMT_4, "Sign request for transactionId: ", this.transactionId, " signRequest: ", this.signRequest);
  }

  @Override
  protected void sendTransaction() throws Exception {
    LOGGER.info(LOG_FMT_14, "Type 2 withdrawableAmountUpdate attempt: ", this.attempt, " contractAddress: ", this.withdrawableAmountUpdateTransaction.getContractAddress(),
        " fromAddress: ", this.withdrawableAmountUpdateTransaction.getFromWalletAddress(), " maxPriorityFee: ", this.gasFee.getMaxPriorityFeePerGas(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " system account: ", this.senderAddress);

    super.sendTransaction();
    if (this.transaction.hasError()) {
      System.out.println("eth_sendRawTransaction error: " + this.transaction.getError().getCode()
          + " - " + this.transaction.getError().getMessage());
      //throw new RuntimeException("Send tx failed: " + this.transaction.getError().getMessage());
    }
  }

  @Override
  protected boolean processErrorResponse() {
    LOGGER.info(LOG_FMT_10, " Error in type 2 withdrawableAmountUpdate. contractAddress: ", this.withdrawableAmountUpdateTransaction.getContractAddress(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " reason: ", this.error, " transactionHash: ", this.transactionHash);

    return false;
  }

  @Override
  protected boolean processSuccessResponse() {
    LOGGER.info(LOG_FMT_12, "Type 2 withdrawableAmountUpdate successful. contractAddress: ", this.withdrawableAmountUpdateTransaction.getContractAddress(),
        " fromAddress: ", this.withdrawableAmountUpdateTransaction.getFromWalletAddress(), " maxPriorityFee: ", this.gasFee.getMaxPriorityFeePerGas(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " transactionHash: ", this.transactionHash);

    return true;
  }

  private String preprocessBatchUpdates() {
    StringBuilder processed = new StringBuilder("0x");
    final String snapshotIdHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(withdrawableAmountUpdateTransaction.getId())), 8);
    final String batchIdHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(withdrawableAmountUpdateTransaction.getBatchId())), 4);//todo split in to batches
    final String totalBatchesHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(
        withdrawableAmountUpdateTransaction.getNoOfBatches())), 4);
    processed.append(snapshotIdHex).append(batchIdHex).append(totalBatchesHex);

    for (WithdrawableAmountUpdateTransaction.UserWithdrawable notional : withdrawableAmountUpdateTransaction.getUserWithdrawables()) {
      final String userIdHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(notional.getUserId())), 4);
      processed.append(userIdHex);
      final String amountHex = zeroPadToBytes(int64ToHex(notional.getQuantity()), 8);
      processed.append(amountHex);
    }

    return processed.toString();
  }

  private String preprocessBatchUpdates2() {
    StringBuilder processed = new StringBuilder("0x");
    final String snapshotIdHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(withdrawableAmountUpdateTransaction.getId())), 8);
    final String batchIdHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(withdrawableAmountUpdateTransaction.getBatchId())), 4);//todo split in to batches
    final String totalBatchesHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(
        withdrawableAmountUpdateTransaction.getNoOfBatches())), 4);
    processed.append(snapshotIdHex).append(batchIdHex).append(totalBatchesHex);

    for (WithdrawableAmountUpdateTransaction.UserWithdrawable notional : withdrawableAmountUpdateTransaction.getUserWithdrawables()) {
      final String userIdHex = zeroPadToBytes(Numeric.toHexStringNoPrefix(BigInteger.valueOf(notional.getUserId())), 4);
      processed.append(userIdHex);
      final String amountHex = zeroPadToBytes(int64ToHex(notional.getQuantity()), 8);
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
