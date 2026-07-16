package com.solfini.util.blockchain.model;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

public class UserRegistrationTransaction extends BlockchainTransaction implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(UserRegistrationTransaction.class);
  private int newUserId;
  private String newUserAddress;
  private String managerType;

  public int getNewUserId() {
    return newUserId;
  }

  public void setNewUserId(int newUserId) {
    this.newUserId = newUserId;
  }

  public String getNewUserAddress() {
    return newUserAddress;
  }

  public void setNewUserAddress(String newUserAddress) {
    this.newUserAddress = newUserAddress;
  }

  // FUND_MANAGEMENT or POSITION_MANAGEMENT; when unset the sender infers it from chainType.
  public String getManagerType() {
    return managerType;
  }

  public void setManagerType(String managerType) {
    this.managerType = managerType;
  }
}
