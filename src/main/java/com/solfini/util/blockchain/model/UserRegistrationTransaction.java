package com.solfini.util.blockchain.model;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

public class UserRegistrationTransaction extends BlockchainTransaction implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(UserRegistrationTransaction.class);
  private int newUserId;
  private String newUserAddress;

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
}
