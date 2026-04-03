package com.solfini.util.blockchain.model;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

public class WithdrawTransaction extends BlockchainTransaction implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(WithdrawTransaction.class);
  private int accountId;
  private int instrumentId;
  private String token;
  private String recipient;
  private String amount;
  private String transactionHash;

  public int getAccountId() {
    return accountId;
  }

  public void setAccountId(int accountId) {
    this.accountId = accountId;
  }

  public int getInstrumentId() {
    return instrumentId;
  }

  public void setInstrumentId(int instrumentId) {
    this.instrumentId = instrumentId;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getRecipient() {
    return recipient;
  }

  public void setRecipient(String recipient) {
    this.recipient = recipient;
  }

  public String getAmount() {
    return amount;
  }

  public void setAmount(String amount) {
    this.amount = amount;
  }

  @Override
  public String getTransactionHash() {
    return transactionHash;
  }

  @Override
  public void setTransactionHash(String transactionHash) {
    this.transactionHash = transactionHash;
  }
}
