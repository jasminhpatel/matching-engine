package com.solfini.util.blockchain.evm;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.blockchain.model.WithdrawableAmountUpdateTransaction;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.solfini.common.Constants.*;

public class EVMWithdrawableAmountUpdateSender extends EVMTransactionSender {
  public static final BigInteger GAS_LIMIT_FOR_WITHDRAWABLE_AMOUNT_UPDATE = Context.getGasLimitForWithdrawableAmountUpdate();

  private static final CustomLogger LOGGER = CustomLogger.getLogger(EVMWithdrawableAmountUpdateSender.class);
  private static final String SIGNER_URL = Context.getSignerUrl() + "/signApi/signFundManagerSnapshotUpdateRequest";
  private static final Map<String, String> CUSTOM_ERROR_SELECTORS = buildErrorSelectors();

  private static Map<String, String> buildErrorSelectors() {
    String[] sigs = {
        "InvalidBatchData()",
        "SnapshotUpdatePending(address,uint256)",
        "DuplicateBatchIndex(address,uint64,uint32)",
        "SnapshotCommitCadenceNotMet(address,uint256,uint256)",
        "AssetNotSupported(address)",
        "NotBalancePoster()",
        "NotAdmin()",
        "NotAdminOrGuardian()",
        "NotWithdrawalRole()",
        "SignerKeyNotRotated(address)",
        "EmergencyModeActive()",
        "ZeroAddress()",
        "ZeroAmount()",
        "InvalidUserId()",
        "UserAlreadyExists()",
        "UserNotRegistered(address)",
        "UserRegistrationTooRecent(address,uint256,uint256)",
        "AddressAlreadyMapped()",
        "AssetAlreadyRegistered(address)",
        "AssetAlreadyRegisteredWithDifferentKind(address)",
        "InvalidEngineScale(uint8)",
        "InvalidTokenDecimals(uint8)",
        "InsufficientSnapshots()",
        "ExceedsMaxWithdrawable(address,uint256,uint256)",
        "ExceedsAssetWithdrawalRateLimit(address,uint256,uint256,uint256)",
        "InsufficientContractLiquidity(address,uint256,uint256)",
        "BalanceTooLarge(uint256,uint256)",
        "InvalidWithdrawalId()",
        "WithdrawalAlreadyProcessed(bytes32)",
        "WithdrawalCooldownActive(address,uint256)",
        "DailySnapshotNotPublished(address,uint256,uint256)",
        "NativeTransferFailed()",
        "PageTooLarge(uint256,uint256)",
        "NativeRecoveryBlockedOnNativeChain()",
        "InvalidUserAddress(address)",
    };
    Map<String, String> map = new HashMap<>();
    for (String sig : sigs) {
      String hash = Numeric.toHexString(Hash.sha3(sig.getBytes(StandardCharsets.UTF_8)));
      map.put(hash.substring(0, 10).toLowerCase(), sig);
    }
    return map;
  }
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
              List.of(
                  new Address(withdrawableAmountUpdateTransaction.getTokenAddress()),
                  new DynamicBytes(Numeric.hexStringToByteArray(preprocessBatchUpdates2()))),
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
    String revertReason = this.error;
    if (revertReason == null && this.web3j != null && this.encodedFunction != null && this.txReceipt != null) {
      try {
        // Replay at block N-1 so state matches what the node saw when the tx reverted.
        // eth_call at LATEST returns null because the reverting condition no longer holds.
        DefaultBlockParameter replayBlock = DefaultBlockParameter.valueOf(
            this.txReceipt.getBlockNumber().subtract(BigInteger.ONE));
        org.web3j.protocol.core.methods.request.Transaction callTx =
            org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                this.senderAddress,
                this.withdrawableAmountUpdateTransaction.getContractAddress(),
                this.encodedFunction);
        EthCall ethCall = this.web3j.ethCall(callTx, replayBlock).send();
        String raw = ethCall.getValue();
        if ((raw == null || raw.length() < 10) && ethCall.getError() != null) {
          // Standard JSON-RPC returns revert bytes in error.data, not in the result value
          raw = ethCall.getError().getData();
        }
        System.out.println("eth_call revert data (block " + this.txReceipt.getBlockNumber().subtract(BigInteger.ONE) + "): " + raw);
        if (raw != null && raw.length() >= 10) {
          String selector = raw.substring(0, 10).toLowerCase();
          revertReason = CUSTOM_ERROR_SELECTORS.getOrDefault(selector, "unknown custom error selector: " + selector);
        }
      } catch (Exception e) {
        System.out.println("Could not simulate call for revert reason: " + e.getMessage());
      }
    }
    LOGGER.info(LOG_FMT_10, " Error in type 2 withdrawableAmountUpdate. contractAddress: ", this.withdrawableAmountUpdateTransaction.getContractAddress(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " reason: ", revertReason, " transactionHash: ", this.transactionHash);
    System.out.println(" Error in type 2 withdrawableAmountUpdate. contractAddress: " + this.withdrawableAmountUpdateTransaction.getContractAddress() + " maxFeePerGas: " +
        this.gasFee.getMaxFeePerGas() + " gasLimit: " + this.gasLimit + " reason: " + revertReason + " transactionHash: " + this.transactionHash);
    return false;
  }

  @Override
  protected boolean processSuccessResponse() {
    LOGGER.info(LOG_FMT_12, "Type 2 withdrawableAmountUpdate successful. contractAddress: ", this.withdrawableAmountUpdateTransaction.getContractAddress(),
        " fromAddress: ", this.withdrawableAmountUpdateTransaction.getFromWalletAddress(), " maxPriorityFee: ", this.gasFee.getMaxPriorityFeePerGas(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " transactionHash: ", this.transactionHash);
    System.out.println(" Type 2 withdrawableAmountUpdate successful. contractAddress: " + this.withdrawableAmountUpdateTransaction.getContractAddress() + " maxFeePerGas: " +
        this.gasFee.getMaxFeePerGas() + " gasLimit: " + this.gasLimit + " reason: " + this.error + " transactionHash: " + this.transactionHash);
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
    processed.append(zeroPadToBytes(Long.toHexString(withdrawableAmountUpdateTransaction.getId()), 8));
    processed.append(zeroPadToBytes(Integer.toHexString(withdrawableAmountUpdateTransaction.getBatchId()),  4));
    processed.append(zeroPadToBytes(Integer.toHexString(withdrawableAmountUpdateTransaction.getNoOfBatches()), 4));

    for (WithdrawableAmountUpdateTransaction.UserWithdrawable notional : withdrawableAmountUpdateTransaction.getUserWithdrawables()) {
      final String userIdHex = zeroPadToBytes(Long.toHexString((long) notional.getUserId()), 4);
      processed.append(userIdHex);
      final String amountHex = int64ToHex(notional.getQuantity());
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
