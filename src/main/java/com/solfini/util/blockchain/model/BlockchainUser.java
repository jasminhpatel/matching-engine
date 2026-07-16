package com.solfini.util.blockchain.model;

public class BlockchainUser {
  private final int userId;
  private final String address;
  private final long createdAt;
  private final String network;
  private final String contractType;

  public BlockchainUser(final int userId, final String address, final long createdAt, final String network, final String contractType) {
    this.userId = userId;
    this.address = address;
    this.createdAt = createdAt;
    this.network = network;
    this.contractType = contractType;
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

  public String getContractType() {
    return contractType;
  }
}