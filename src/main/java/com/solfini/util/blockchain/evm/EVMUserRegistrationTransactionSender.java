package com.solfini.util.blockchain.evm;

import static com.solfini.common.Constants.ETHEREUM;
import static com.solfini.common.Constants.LOG_FMT_10;
import static com.solfini.common.Constants.LOG_FMT_12;
import static com.solfini.common.Constants.LOG_FMT_14;
import static com.solfini.common.Constants.LOG_FMT_4;
import static com.solfini.common.Constants.MAINNET;
import static com.solfini.common.Constants.XDC;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.blockchain.model.UserRegistrationTransaction;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint32;

public class EVMUserRegistrationTransactionSender extends EVMTransactionSender {
  public static final BigInteger GAS_LIMIT_FOR_USER_REGISTRATION = Context.getGasLimitForUserRegistration();

  private static final CustomLogger LOGGER = CustomLogger.getLogger(EVMUserRegistrationTransactionSender.class);
  private static final String SIGNER_URL = Context.getSignerUrl() + "/signApi/signUserRegistrationRequest";
  protected UserRegistrationTransaction userRegistrationTransaction;

  public EVMUserRegistrationTransactionSender(final UserRegistrationTransaction userRegistrationTransaction) {
    super(userRegistrationTransaction.getContractAddress() + "-" + userRegistrationTransaction.getId(), userRegistrationTransaction.getChainType(),
        (ETHEREUM.equalsIgnoreCase(userRegistrationTransaction.getChainType()) || MAINNET.equalsIgnoreCase(userRegistrationTransaction.getChainType()) || XDC.equalsIgnoreCase(userRegistrationTransaction.getChainType()))
            ? FUND_MANAGEMENT : POSITION_MANAGEMENT, userRegistrationTransaction);
    this.userRegistrationTransaction = userRegistrationTransaction;
  }

  @Override
  protected void encodeFunction() {
    final int userId = this.userRegistrationTransaction.getNewUserId();
    final String address = this.userRegistrationTransaction.getNewUserAddress();
    final Function function =
        new Function("registerUser", Arrays.asList(new Uint32(userId), new Address(address)),
            Collections.emptyList());
    this.encodedFunction = FunctionEncoder.encode(function);
  }

  @Override
  protected void calculateGasLimit() {
    this.gasLimit = GAS_LIMIT_FOR_USER_REGISTRATION;
  }

  @Override
  protected void createSignRequest() {
    this.signerUrl = SIGNER_URL;

    final StringBuilder sb = new StringBuilder();
    sb.append("{").append("\"chainType\":\"").append(this.chainType).append("\",").append("\"contractAddress\":\"")
        .append(this.userRegistrationTransaction.getContractAddress()).append("\",").append("\"encodedFunction\":\"").append(this.encodedFunction)
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
    LOGGER.info(LOG_FMT_14, "Type 2 userRegistration attempt: ", this.attempt, " contractAddress: ", this.userRegistrationTransaction.getContractAddress(),
        " fromAddress: ", this.userRegistrationTransaction.getFromWalletAddress(), " maxPriorityFee: ", this.gasFee.getMaxPriorityFeePerGas(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " system account: ", this.senderAddress);

    super.sendTransaction();
  }

  @Override
  protected boolean processErrorResponse() {
    LOGGER.info(LOG_FMT_10, " Error in type 2 userRegistration. contractAddress: ", this.userRegistrationTransaction.getContractAddress(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " reason: ", this.error, " transactionHash: ", this.transactionHash);

    return false;
  }

  @Override
  protected boolean processSuccessResponse() {
    LOGGER.info(LOG_FMT_12, "Type 2 userRegistration successful. contractAddress: ", this.userRegistrationTransaction.getContractAddress(),
        " fromAddress: ", this.userRegistrationTransaction.getFromWalletAddress(), " maxPriorityFee: ", this.gasFee.getMaxPriorityFeePerGas(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " transactionHash: ", this.transactionHash);

    return true;
  }

}
