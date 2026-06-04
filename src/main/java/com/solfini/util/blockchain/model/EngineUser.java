package com.solfini.util.blockchain.model;

public class EngineUser {
  private final int userId;
  private final String address;

  public EngineUser(int userId, String address) {
    this.userId = userId;
    this.address = address;
  }

  public int getUserId() {
    return userId;
  }

  public String getAddress() {
    return address;
  }
}
