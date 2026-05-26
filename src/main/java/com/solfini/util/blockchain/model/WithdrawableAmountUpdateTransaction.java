package com.solfini.util.blockchain.model;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

import java.util.List;

public class WithdrawableAmountUpdateTransaction extends BlockchainTransaction implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(WithdrawableAmountUpdateTransaction.class);
  private String tokenAddress;
  private String network;
  private int contractVersion;
  private int batchId;
  private int noOfBatches;

  public String getTokenAddress() {
    return tokenAddress;
  }

  public void setTokenAddress(String tokenAddress) {
    this.tokenAddress = tokenAddress;
  }

  public String getNetwork() {
    return network;
  }

  public void setNetwork(String network) {
    this.network = network;
  }

  public int getContractVersion() {
    return contractVersion;
  }

  public void setContractVersion(int contractVersion) {
    this.contractVersion = contractVersion;
  }

  public int getBatchId() {
    return batchId;
  }

  public void setBatchId(int batchId) {
    this.batchId = batchId;
  }

  public int getNoOfBatches() {
    return noOfBatches;
  }

  public void setNoOfBatches(int noOfBatches) {
    this.noOfBatches = noOfBatches;
  }

  private List<UserWithdrawable> userWithdrawables;

  public List<UserWithdrawable> getUserWithdrawables() {
    return userWithdrawables;
  }

  public void setUserWithdrawables(List<UserWithdrawable> userWithdrawables) {
    this.userWithdrawables = userWithdrawables;
  }

  public static class UserWithdrawable {
    private final int userId;
    private final String contractKey;
    private final long quantity;

    public UserWithdrawable(int userId, String contractKey, long quantity) {
      this.userId = userId;
      this.contractKey = contractKey;
      this.quantity = quantity;
    }

    public String getContractKey() {
      return contractKey;
    }

    public int getUserId() {
      return userId;
    }

    public long getQuantity() {
      return quantity;
    }
  }
}
