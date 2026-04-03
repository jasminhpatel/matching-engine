package com.solfini.util.blockchain.model;

import com.solfini.common.Constants;

public class BlockchainTransaction implements Constants {
  protected long id;
  protected int userId;
  protected String contractAddress;
  protected String senderWalletAddress;
  protected String fromWalletAddress;
  protected String toWalletAddress;
  protected String txnData;
  protected String transactionHash;
  protected String receiptUrl;
  protected String chainType;
  protected String reference;
  protected String error;
  protected GasFee gasFee;

  public long getId() {
    return id;
  }

  public void setId(final long id) {
    this.id = id;
  }

  public int getUserId() {
    return userId;
  }

  public void setUserId(final int userId) {
    this.userId = userId;
  }

  public String getContractAddress() {
    return contractAddress;
  }

  public void setContractAddress(final String contractAddress) {
    this.contractAddress = contractAddress;
  }

  public String getSenderWalletAddress() {
    return senderWalletAddress;
  }

  public void setSenderWalletAddress(final String senderWalletAddress) {
    this.senderWalletAddress = senderWalletAddress;
  }

  public String getFromWalletAddress() {
    return fromWalletAddress;
  }

  public void setFromWalletAddress(final String fromWalletAddress) {
    this.fromWalletAddress = fromWalletAddress;
  }

  public String getToWalletAddress() {
    return toWalletAddress;
  }

  public void setToWalletAddress(final String toWalletAddress) {
    this.toWalletAddress = toWalletAddress;
  }

  public String getTxnData() {
    return txnData;
  }

  public void setTxnData(final String txnData) {
    this.txnData = txnData;
  }

  public String getTransactionHash() {
    return transactionHash;
  }

  public void setTransactionHash(final String transactionHash) {
    this.transactionHash = transactionHash;
  }

  public String getReceiptUrl() {
    return receiptUrl;
  }

  public void setReceiptUrl(final String receiptUrl) {
    this.receiptUrl = receiptUrl;
  }

  public String getChainType() {
    return chainType;
  }

  public void setChainType(final String chainType) {
    this.chainType = chainType;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(final String reference) {
    this.reference = reference;
  }

  public String getError() {
    return error;
  }

  public void setError(final String error) {
    this.error = error;
  }

  public GasFee getGasFee() {
    return gasFee;
  }

  public void setGasFee(final GasFee gasFee) {
    this.gasFee = gasFee;
  }
}

