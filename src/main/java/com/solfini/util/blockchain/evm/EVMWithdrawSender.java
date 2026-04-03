package com.solfini.util.blockchain.evm;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.blockchain.model.WithdrawTransaction;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static com.solfini.common.Constants.*;

public class EVMWithdrawSender extends EVMTransactionSender {
  public static final BigInteger GAS_LIMIT_FOR_POSITION_UPDATE = Context.getGasLimitForWithdrawableAmountUpdate();

  private static final CustomLogger LOGGER = CustomLogger.getLogger(EVMWithdrawSender.class);
  private static final String SIGNER_URL = Context.getSignerUrl() + "/signApi/signWithdrawFundsRequest";
  protected WithdrawTransaction withdrawTransaction;

  public EVMWithdrawSender(final WithdrawTransaction withdrawTransaction) {
    super(withdrawTransaction.getContractAddress() + "-" + withdrawTransaction.getId(), withdrawTransaction.getChainType(),
        FUND_MANAGEMENT, withdrawTransaction);
    this.withdrawTransaction = withdrawTransaction;
  }

  @Override
  protected void encodeFunction() {
    Function function;
    if (withdrawTransaction.getToken() == null) {
      function = new Function("transferETH",
          List.of(new Address(withdrawTransaction.getRecipient()), new Uint256(new BigInteger(withdrawTransaction.getAmount()))),
          Collections.emptyList());
    } else {
      function = new Function("transferTokens",
          List.of(new Uint32(withdrawTransaction.getInstrumentId()), new Address(withdrawTransaction.getRecipient()), new Uint256(new BigInteger(withdrawTransaction.getAmount()))),
          Collections.emptyList());
    }
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
        .append(this.withdrawTransaction.getContractAddress()).append("\",").append("\"encodedFunction\":\"").append(this.encodedFunction)
        .append("\",").append("\"maxPriorityFee\":\"").append(this.gasFee.getMaxPriorityFeePerGas()).append("\",").append("\"maxFeePerGas\":\"")
        .append(this.gasFee.getMaxFeePerGas()).append("\",").append("\"gasLimit\":\"").append(this.gasLimit).append("\",").append("\"nonce\":\"")
        .append(this.nonce).append("\",").append("\"chainId\":").append(this.chainId).append(",")
        .append("\"systemAddress\":\"").append(this.senderAddress).append("\",").append("\"timestamp\":")
        .append(System.currentTimeMillis())//to avoid replay of signed message
        .append("}");
    this.signRequest = sb.toString();
    LOGGER.info(LOG_FMT_4, "Sign request for transactionId: ", this.transactionId, " signRequest: ", this.signRequest);
  }

  @Override
  protected void sendTransaction() throws Exception {
    LOGGER.info(LOG_FMT_14, "Type 2 withdrawTransaction attempt: ", this.attempt, " contractAddress: ", this.withdrawTransaction.getContractAddress(),
        " fromAddress: ", this.withdrawTransaction.getFromWalletAddress(), " maxPriorityFee: ", this.gasFee.getMaxPriorityFeePerGas(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " system account: ", this.senderAddress);

    super.sendTransaction();
  }

  @Override
  protected boolean processErrorResponse() {
    LOGGER.info(LOG_FMT_10, " Error in type 2 withdrawTransaction. contractAddress: ", this.withdrawTransaction.getContractAddress(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " reason: ", this.error, " transactionHash: ", this.transactionHash);

    return false;
  }

  @Override
  protected boolean processSuccessResponse() {
    LOGGER.info(LOG_FMT_12, "Type 2 withdrawTransaction successful. contractAddress: ", this.withdrawTransaction.getContractAddress(),
        " fromAddress: ", this.withdrawTransaction.getFromWalletAddress(), " maxPriorityFee: ", this.gasFee.getMaxPriorityFeePerGas(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " transactionHash: ", this.transactionHash);
    this.withdrawTransaction.setTransactionHash(this.transactionHash);
    return true;
  }

}
