package com.solfini.util.blockchain.model;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

import java.util.List;

public class WithdrawableAmountUpdateTransaction extends BlockchainTransaction implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(WithdrawableAmountUpdateTransaction.class);

  private int batchId;
  private int noOfBatches;

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
    private final long quantity;

    public UserWithdrawable(int userId, long quantity) {
      this.userId = userId;
      this.quantity = quantity;
    }

    public int getUserId() {
      return userId;
    }

    public long getQuantity() {
      return quantity;
    }
  }
}
