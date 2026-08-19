package com.solfini.util.blockchain.evm;

import static com.solfini.common.Constants.LOG_FMT_10;
import static com.solfini.common.Constants.LOG_FMT_12;
import static com.solfini.common.Constants.LOG_FMT_14;
import static com.solfini.common.Constants.LOG_FMT_4;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.blockchain.model.MembershipExecuteTransaction;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.utils.Numeric;

/**
 * Sends executeCouple/executeDecouple(bytes32 proposalId). Reuses the SAME signer endpoint as
 * balance publishing (Context.getSignerUrl() + "/signApi/signFundManagerSnapshotUpdateRequest",
 * FUND_MANAGEMENT role) — verified against solfini-signer's SignerService: that endpoint signs
 * whatever encodedFunction it is given for the resolved role/chain/address, it does not validate
 * the ABI selector, so no signer-service change is needed for this new call shape.
 */
public class EVMMembershipExecuteSender extends EVMTransactionSender {
  public static final BigInteger GAS_LIMIT_FOR_MEMBERSHIP_EXECUTE = Context.getGasLimitForWithdrawableAmountUpdate();

  private static final CustomLogger LOGGER = CustomLogger.getLogger(EVMMembershipExecuteSender.class);
  private static final String SIGNER_URL = Context.getSignerUrl() + "/signApi/signFundManagerSnapshotUpdateRequest";
  protected MembershipExecuteTransaction membershipExecuteTransaction;

  public EVMMembershipExecuteSender(final MembershipExecuteTransaction membershipExecuteTransaction) {
    // FUND_MANAGEMENT is inherited from BlockchainTransactionSender (implemented by the
    // EVMTransactionSender superclass) — same role EVMWithdrawableAmountUpdateSender uses.
    super(membershipExecuteTransaction.getContractAddress() + "-" + membershipExecuteTransaction.getProposalId(),
        membershipExecuteTransaction.getChainType(), FUND_MANAGEMENT, membershipExecuteTransaction);
    this.membershipExecuteTransaction = membershipExecuteTransaction;
  }

  @Override
  protected void encodeFunction() {
    final String functionName;
    switch (membershipExecuteTransaction.getOp()) {
      case DECOUPLE:
        functionName = "executeDecouple";
        break;
      case MERGE:
        functionName = "executeMerge";
        break;
      case COUPLE:
      default:
        functionName = "executeCouple";
        break;
    }
    final Function function = new Function(functionName,
        List.of(new Bytes32(Numeric.hexStringToByteArray(membershipExecuteTransaction.getProposalId()))),
        Collections.emptyList());
    this.encodedFunction = FunctionEncoder.encode(function);
  }

  @Override
  protected void calculateGasLimit() {
    this.gasLimit = GAS_LIMIT_FOR_MEMBERSHIP_EXECUTE;
  }

  @Override
  protected void createSignRequest() {
    this.signerUrl = SIGNER_URL;

    final StringBuilder sb = new StringBuilder();
    sb.append("{").append("\"chainType\":\"").append(this.chainType).append("\",").append("\"contractAddress\":\"")
        .append(this.membershipExecuteTransaction.getContractAddress()).append("\",").append("\"encodedFunction\":\"").append(this.encodedFunction)
        .append("\",").append("\"maxPriorityFee\":\"").append(this.gasFee.getMaxPriorityFeePerGas()).append("\",").append("\"maxFeePerGas\":\"")
        .append(this.gasFee.getMaxFeePerGas()).append("\",").append("\"gasLimit\":\"").append(this.gasLimit).append("\",").append("\"nonce\":\"")
        .append(this.nonce).append("\",").append("\"chainId\":").append(this.chainId).append(",")
        .append("\"systemAddress\":\"").append(this.senderAddress).append("\",").append("\"timestamp\":")
        .append(System.currentTimeMillis())
        .append("}");
    this.signRequest = sb.toString();
    LOGGER.info(LOG_FMT_4, "Sign request for transactionId: ", this.transactionId, " signRequest: ", this.signRequest);
  }

  @Override
  protected void sendTransaction() throws Exception {
    LOGGER.info(LOG_FMT_14, "MembershipExecute attempt: ", this.attempt, " contractAddress: ", this.membershipExecuteTransaction.getContractAddress(),
        " proposalId: ", this.membershipExecuteTransaction.getProposalId(), " op: ", this.membershipExecuteTransaction.getOp(),
        " maxFeePerGas: ", this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " system account: ", this.senderAddress);
    super.sendTransaction();
  }

  @Override
  protected boolean processErrorResponse() {
    LOGGER.info(LOG_FMT_10, " Error in MembershipExecute. contractAddress: ", this.membershipExecuteTransaction.getContractAddress(), " maxFeePerGas: ",
        this.gasFee.getMaxFeePerGas(), " gasLimit: ", this.gasLimit, " reason: ", this.error, " transactionHash: ", this.transactionHash);
    return false;
  }

  @Override
  protected boolean processSuccessResponse() {
    LOGGER.info(LOG_FMT_12, "MembershipExecute successful. contractAddress: ", this.membershipExecuteTransaction.getContractAddress(),
        " proposalId: ", this.membershipExecuteTransaction.getProposalId(), " maxFeePerGas: ", this.gasFee.getMaxFeePerGas(),
        " gasLimit: ", this.gasLimit, " transactionHash: ", this.transactionHash);
    return true;
  }
}
