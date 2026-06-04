package com.solfini.util.blockchain.model;

public class BlockchainUser {
  private final int userId;
  private final String address;
  private final long createdAt;
  private final String network;

  public BlockchainUser(final int userId, final String address, final long createdAt, final String network) {
    this.userId = userId;
    this.address = address;
    this.createdAt = createdAt;
    this.network = network;
  }

  public int getUserId() {
    return userId;
  }

  public String getAddress() {
    return address;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public String getNetwork() {
    return network;
  }
}