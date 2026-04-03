package com.solfini.util.blockchain.evm;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.util.StringUtil;
import com.solfini.util.blockchain.BlockchainTransactionSender;
import com.solfini.util.blockchain.gasstation.GasStationUtil;
import com.solfini.util.blockchain.model.BlockchainTransaction;
import com.solfini.util.blockchain.model.GasFee;
import com.solfini.util.blockchain.util.BlockChainKeyManager;

import com.solfini.util.blockchain.util.RpcUtil;
import com.solfini.util.blockchain.util.HTTPSClient;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;

import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketTimeoutException;

import static com.solfini.common.Constants.*;

public abstract class EVMTransactionSender implements BlockchainTransactionSender {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(EVMTransactionSender.class);
  protected Web3j web3j;
  protected long chainId;
  protected Object synchronizeKey;
  protected final String transactionId;
  protected final String chainType;
  protected final String txnType;
  protected String senderAddress;
  protected String encodedFunction;
  protected String signerUrl;
  protected String signRequest;
  protected String transactionHash;
  protected int attempt;

  protected BlockchainTransaction blockchainTransaction;
  protected GasFee gasFee;
  protected BigInteger gasLimit;
  protected SignedMessageResponse signedMessageResponse;
  protected EthSendTransaction transaction;
  protected TransactionReceipt txReceipt;
  protected BigInteger nonce = new BigInteger("-1");

  protected String error;
  protected String receiptUrl;
  protected String lastProxy;

  protected EVMTransactionSender(final String transactionId, final String chainType, final String txnType,
      final BlockchainTransaction blockchainTransaction) {
    this.transactionId = transactionId;
    this.chainType = chainType;
    this.txnType = txnType;
    this.blockchainTransaction = blockchainTransaction;
  }

  /**
   * template for EVM transaction
   */
  @Override
  public boolean processTransaction(final StringBuilder sb) {
    try {
      LOGGER.info("Blockchain transaction. txnType: " + txnType + " chainType: " + chainType);
      getSenderAccount();
      LOGGER.info("Blockchain sender account. senderAddress: " + senderAddress + " chainType: " + chainType);
      synchronized (synchronizeKey) {//same key cannot be used in concurrent transactions
        encodeFunction();
        calculateGasLimit();
        boolean useSecondary = false, hasProxyError = false;
        for (this.attempt = 1; this.attempt <= 5; this.attempt++) {
          try {
            createWeb3jConnection(useSecondary, hasProxyError);
            getChainId();
            calculateGas();
            createSignRequest();
            try {
              getSignature();
            } catch (Exception e) {
              LOGGER.error("Error occurred while signing. ", e);
              System.out.println("Error occurred while signing. " + e.getMessage());
            }
            if (this.signedMessageResponse.error != null) {
              LOGGER.error(ERROR_LOG, this.signedMessageResponse.error, " transactionId: ",
                  transactionId);
              return processError();
            }
            sendTransaction();
            if (this.transaction.hasError()) {
              final String error = this.transaction.getError().getMessage();
              if (this.attempt > 1 && error.contains(
                  "already known")) {//previous attempt successful
                getTransactionReceipt(useSecondary, hasProxyError);//loads previous transaction

                return processSuccess();
              }
              if (error.contains("insufficient funds for gas") || error.contains("nonce too low")
                  || error.contains("transaction underpriced") || error.contains("transaction gas price below minimum")) {//retry on this specific error
                try {
                  Thread.sleep(60000);
                } catch (final Exception e) {
                  LOGGER.error(ERROR_LOG, e);
                  System.out.println(ERROR_LOG +  e.getMessage());
                }

                LOGGER.error(ERROR_LOG, "Retry transaction after error: ", error, " order: ",
                    transactionId, " system account: ", this.senderAddress);
                System.out.println("Retry transaction after error: " + error + " order: " +
                    transactionId + " system account: " + this.senderAddress);

                retryFailed();
              }
            }
            if (this.transaction == null || this.transaction.hasError()) {
              return processError();
            }
            this.transactionHash = transaction.getTransactionHash();
            getTransactionReceipt(useSecondary, hasProxyError);
            if ("0x1".equalsIgnoreCase(this.txReceipt.getStatus())) {
              return processSuccess();
            } else {
              return processError();
            }

          } catch (ClientConnectionException e) {
            String message = e.getMessage().toLowerCase();
            System.out.println(message);
            if (message.contains("429") || message.contains("too many requests")) {
              sb.append("Failed Reason ").append("RPC error.").append(" Attempt : ").append(this.attempt);
              useSecondary = true;
            } else if (message.contains("502") || message.contains("bad gateway")) {
              sb.append("Failed Reason ").append("Proxy error.").append(" Attempt : ").append(this.attempt);
              hasProxyError = true;
            } else if (message.contains("503") || message.contains("service unavailable")) {
              sb.append("Failed Reason ").append("RPC error.").append(" Attempt : ").append(this.attempt);
              useSecondary = true;
            } else if (message.contains("504") || message.contains("gateway timeout")) {
              sb.append("Failed Reason ").append("RPC error.").append(" Attempt : ").append(this.attempt);
              sb.append("Failed Reason ").append("Proxy error.").append(" Attempt : ").append(this.attempt);
              hasProxyError = true;
              useSecondary = true;
            } else if (message.contains("407") || message.contains("proxy authentication")) {
              sb.append("Failed Reason ").append("Proxy error.").append(" Attempt : ").append(this.attempt);
              hasProxyError = true;
            } else {
              sb.append("Failed Reason ").append("RPC error.").append(" Attempt : ").append(this.attempt);
              sb.append("Failed Reason ").append("Proxy error.").append(" Attempt : ").append(this.attempt);
              hasProxyError = true;
              useSecondary = true;
            }
          } catch (IOException e) {
            System.out.println(e.getMessage());
            hasProxyError = true;
            useSecondary = true;
          }
        }
      }
    } catch (final Exception e) {
      System.out.println(e.getMessage());
      LOGGER.error(ERROR_LOG, e);
    }
    return false;
  }

  protected void getSenderAccount() {
    this.senderAddress = BlockChainKeyManager.getNextKey(this.chainType, this.txnType);
    this.synchronizeKey = BlockChainKeyManager.getLockObject(chainType, this.senderAddress);
  }

  protected void createWeb3jConnection(final boolean useSecondary, final boolean hasProxyError) {
    this.lastProxy = ExternalExchangeUtil.getProxy(this.lastProxy, hasProxyError);

    this.web3j = RpcUtil.createWeb3jConnection(this.chainType, this.lastProxy, useSecondary, hasProxyError);
  }

  protected void getChainId() throws IOException {
    this.chainId = StringUtil.toLong(this.web3j.netVersion().send().getNetVersion());
  }

  protected abstract void encodeFunction();

  protected void calculateGas() throws Exception {
    final double factor = this.gasFee == null ? 1d : 1.2d;
    if (this.gasFee == null) {
      this.gasFee = new GasFee(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
    }
    this.gasFee = GasStationUtil.getGasFees(this.web3j, this.gasFee, factor, this.chainType);
  }

  protected abstract void calculateGasLimit();

  protected abstract void createSignRequest();

  protected void getSignature() {
    final byte[] requestSecret = Context.getSignerRequestSecret();

    String json = null;
    int count = 1;
    final int maxTries = 3;
    while(count <= maxTries) {//retry on network failure
      try {
        LOGGER.info(LOG_FMT_2, "Signer url: ", this.signerUrl);
        json = HTTPSClient.postWithSignature(this.signerUrl, this.signRequest.getBytes(), requestSecret);
        LOGGER.info(LOG_FMT_2, "Signer response: ", json);
        break;
      } catch (final Exception e) {
        System.out.println(e.getMessage());
        LOGGER.error(ERROR_LOG, e);
        LOGGER.error(ERROR_LOG, "Failed to connect to signing service. attempt: ", count , " for order: " , this.transactionId
            + " reason: " + e.getMessage());
        count++;
        if (count <= maxTries) {
          try {
            Thread.sleep(60_000);
          } catch (final InterruptedException ex) {
            System.out.println(ex.getMessage());
            LOGGER.error(ERROR_LOG, ex);
          }
        }
      }
    }
    if (count > maxTries) {//unable to get signed message
      this.signedMessageResponse = new SignedMessageResponse();
      this.signedMessageResponse.setError("failed to sign request");

      return;
    }
    //parse json using custom parser
    String error = null;
    String signedMessage = null;
    String signedPublicAddress = null;
    BigInteger messageNonce = null;

    int index1 = 0;
    int index2 = 0;
    String key = null;
    String value = null;
    int parseState = 0;

    for (int j = 0; j < json.length(); j++) {
      final char c = json.charAt(j);
      switch (parseState) {
        case 1:
          if (DOUBLE_QUOTE == c) {
            index2 = j;
            parseState = 2;
            key = json.substring(index1 + 1, index2);
          }
          break;
        case 2:
          if (COLON == c) {
            index1 = j;
            parseState = 3;
          }
          break;
        case 3:
          if (DOUBLE_QUOTE == c) {
            index1 = j;
            parseState = 4;
          }
          if (CURLY_END == c || COMMA == c) {
            index2 = j;
            parseState = 0;
            value = json.substring(index1 + 1, index2);
            if (CURLY_END == c && j == json.length() - 1) // roll back counter 1 so that the value is processed at the end
              j--;
          }
          break;
        case 4:
          if (c == '\\' && j + 1 < json.length()) {
            j++;
          }
          if (DOUBLE_QUOTE == c) {
            index2 = j;
            parseState = 0;
            value = json.substring(index1 + 1, index2).replaceAll("[\\\\](.)", "$1");
          }
          break;
        case 0:
          if (value != null) {
            switch (key) {
              case ERROR_TXT:
                error = value;
                break;
              case SIGNED_MESSAGE:
                signedMessage = value;
                break;
              case SIGNED_PUBLIC_ADDRESS:
                signedPublicAddress = value;
                break;
              case NONCE:
                messageNonce = new BigInteger(value);
                break;
            }
            value = null;
          }
          if (CURLY_END == c)
            break;
          if (DOUBLE_QUOTE == c) {
            index1 = j;
            parseState = 1;
          }
          break;
      }
    }

    this.signedMessageResponse = new SignedMessageResponse();
    this.signedMessageResponse.setError(error);
    this.signedMessageResponse.setSignedMessage(signedMessage);
    this.signedMessageResponse.setSignedPublicAddress(signedPublicAddress);
    this.signedMessageResponse.setMessageNonce(messageNonce);
    this.nonce = messageNonce;

    return;
  }

  protected void sendTransaction() throws Exception {
    this.transaction = this.web3j.ethSendRawTransaction(this.signedMessageResponse.getSignedMessage()).send();
  }

  protected void getTransactionReceipt(boolean useSecondary , boolean hasProxyError) {
    for (this.attempt = 1; this.attempt <= 5; this.attempt++) {
      try {
        createWeb3jConnection(useSecondary, hasProxyError);
        LOGGER.info(LOG_FMT_2,"Fetching transaction status for transactionId: ", this.transactionId, " transactionHash: ", this.transactionHash);
        final TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(
            web3j, 2000L, 150);//5 mins max
        txReceipt = receiptProcessor
            .waitForTransactionReceipt(this.transactionHash);
      } catch (TransactionException  e) {
        System.out.println(e.getMessage());
        LOGGER.error(ERROR_LOG, e);
        LOGGER.error(ERROR_LOG, "Attempt: ", this.attempt, " failed for order: ",
            transactionId,
            " reason: ", e.getMessage(), " transactionHash: " + this.transactionHash);
        System.out.println(ERROR_LOG + "Attempt: " + this.attempt + " failed for order: " +
            transactionId + " reason: " + e.getMessage() + " transactionHash: " + this.transactionHash);
        if (e.getMessage() != null && e.getMessage().contains("query cancelled")) {
          //use the same node
        } else if (e.getMessage() != null && e.getMessage().contains("not generated")) {
          break;
        }
      } catch (SocketTimeoutException e) {
        System.out.println(e.getMessage());
        LOGGER.error(ERROR_LOG, e);
        LOGGER.error(ERROR_LOG, "Attempt: ", this.attempt, " failed for order: ",
            transactionId,
            " reason: ", e.getMessage(), " transactionHash: " + this.transactionHash);
        System.out.println(ERROR_LOG + "Attempt: " + this.attempt + " failed for order: " +
            transactionId + " reason: " + e.getMessage() + " transactionHash: " + this.transactionHash);
        useSecondary = true;
        hasProxyError = true;
      } catch (Exception e) {
        System.out.println(e.getMessage());
        LOGGER.error(ERROR_LOG, e);
        LOGGER.error(ERROR_LOG, "Attempt: ", this.attempt, " failed for order: ",
            transactionId,
            " reason: ", e.getMessage(), " transactionHash: " + this.transactionHash);
        System.out.println(ERROR_LOG + "Attempt: " + this.attempt + " failed for order: " +
            transactionId + " reason: " + e.getMessage() + " transactionHash: " + this.transactionHash);
        useSecondary = true;
        hasProxyError = true;
      }
    }
  }

  protected final void retryFailed() throws Exception {
    calculateGas();
    LOGGER.info(LOG_FMT_2, "Retrying failed, transactionId: ", this.transactionId, " ", gasFee.toString());
    System.out.println("Retrying failed, transactionId: " + this.transactionId + " " + gasFee.toString());

    createSignRequest();//sign request with updated fees
    getSignature();

    if (signedMessageResponse.getError() != null) {
      System.out.println("Signing failed for transactionId: " + this.transactionId +
          " " + gasFee.toString() + " error: " +  signedMessageResponse.getError());
      LOGGER.error(ERROR_LOG, "Signing failed for transactionId: ", this.transactionId,
          " ", gasFee.toString(), " error: ", signedMessageResponse.getError());
      return;
    }

    this.sendTransaction();
  }

  protected boolean processError() {
    if (this.signedMessageResponse.getError() != null) {
      this.error = this.signedMessageResponse.getError();
    }
    if (this.transaction != null && this.transaction.getError() != null) {
      this.error = this.transaction.getError().getMessage();
    }
    if (this.txReceipt != null) {
      this.error = this.txReceipt.getRevertReason();
    }
    if (this.transactionHash != null) {
      final String scanUrl = Context.getScanUrlByTransaction(this.chainType);
      this.receiptUrl = scanUrl + this.transactionHash;
    }
    this.blockchainTransaction.setError(this.error);
    this.blockchainTransaction.setGasFee(this.gasFee);
    this.blockchainTransaction.setReceiptUrl(this.receiptUrl);
    return processErrorResponse();
  }

  protected boolean processSuccess() {
    if (this.transactionHash != null) {
      final String scanUrl = Context.getScanUrlByTransaction(this.chainType);
      this.receiptUrl = scanUrl + this.transactionHash;
      this.blockchainTransaction.setGasFee(this.gasFee);
      this.blockchainTransaction.setReceiptUrl(this.receiptUrl);
    }
    return processSuccessResponse();
  }

  protected abstract boolean processErrorResponse();

  protected abstract boolean processSuccessResponse();

  protected static class SignedMessageResponse {
    private String error;
    private String signedMessage;
    private String signedPublicAddress;
    private BigInteger messageNonce;

    public String getError() {
      return error;
    }

    public void setError(final String error) {
      this.error = error;
    }

    public String getSignedMessage() {
      return signedMessage;
    }

    public void setSignedMessage(final String signedMessage) {
      this.signedMessage = signedMessage;
    }

    public String getSignedPublicAddress() {
      return signedPublicAddress;
    }

    public void setSignedPublicAddress(final String signedPublicAddress) {
      this.signedPublicAddress = signedPublicAddress;
    }

    public BigInteger getMessageNonce() {
      return messageNonce;
    }

    public void setMessageNonce(final BigInteger messageNonce) {
      this.messageNonce = messageNonce;
    }
  }

}
