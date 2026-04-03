package com.solfini.reconciliation.pendingwithdraws;

import com.solfini.common.MessageType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.user.User;
import java.util.Set;
import uk.co.real_logic.artio.fields.DecimalFloat;

public class AddressBalance {
  private int id;
  private String senderCompId;
  private int securityId;
  private String symbol;
  private String address;
  private String memo;
  private String name;
  private String txnId;
  private int userId;
  private int managerUserId;
  private User user;
  private long balanceLong;
  private int balance_scale;
  private DecimalFloat balance;
  private long balanceLong_change;
  private int balance_scale_change;
  private DecimalFloat balance_change;
  private int confirms;
  private String source;
  private String updateBy;
  private String signature;
  private int status;
  private String timestamp;
  private String ip;
  private Set<long[]> assetIdtreeSet;

  private long assetId;
  private long tokenId;
  private long groupId;
  private int type;
  private String transactionHash;
  private String chain;
  private String bank;
  private String bankAccount;
  private String swiftCode;
  private String serials;
  private String certificateName;
  private String registryName;
  private String accountNumber;

  //in memory
  private long amount;


  public AddressBalance() {}

  public AddressBalance(final int userId, final String symbol, final String address) {
    this.userId = userId;
    this.symbol = symbol;
    this.address = address;
  }

  public void set() {

  }

  public final int getId() {
    return id;
  }

  public final void setId(final int id) {
    this.id = id;
  }

  public final int getSecurityId() {
    return securityId;
  }

  public final void setSecurityId(final int securityId) {
    this.securityId = securityId;
  }

  public final String getSymbol() {
    return symbol;
  }

  public final void setSymbol(final String symbol) {
    this.symbol = symbol;
  }

  public final String getAddress() {
    return address;
  }

  public final void setAddress(final String address) {
    this.address = address;
  }

  public final int getUserId() {
    return userId;
  }

  public final void setUserId(final int userId) {
    this.userId = userId;
  }

  public final User getUser() {
    return user;
  }

  public final void setUser(final User user) {
    this.user = user;
  }

  public final long getBalanceLong() {
    return balanceLong;
  }

  public final void setBalanceLong(final long balanceLong) {
    this.balanceLong = balanceLong;
  }

  public final int getBalance_scale() {
    return balance_scale;
  }

  public final void setBalance_scale(final int balance_scale) {
    this.balance_scale = balance_scale;
  }

  public final DecimalFloat getBalance() {
    return balance;
  }

  public final void setBalance(final DecimalFloat balance) {
    this.balance = balance;
  }

  public final long getBalanceLong_change() {
    return balanceLong_change;
  }

  public final void setBalanceLong_change(final long balanceLong_change) {
    this.balanceLong_change = balanceLong_change;
  }

  public final int getBalance_scale_change() {
    return balance_scale_change;
  }

  public final void setBalance_scale_change(final int balance_scale_change) {
    this.balance_scale_change = balance_scale_change;
  }

  public final DecimalFloat getBalance_change() {
    return balance_change;
  }

  public final void setBalance_change(final DecimalFloat balance_change) {
    this.balance_change = balance_change;
  }

  public final int getConfirms() {
    return confirms;
  }

  public final void setConfirms(final int confirms) {
    this.confirms = confirms;
  }

  public final String getSource() {
    return source;
  }

  public final void setSource(final String source) {
    this.source = source;
  }

  public final String getUpdateBy() {
    return updateBy;
  }

  public final void setUpdateBy(final String updateBy) {
    this.updateBy = updateBy;
  }

  public final String getSignature() {
    return signature;
  }

  public final void setSignature(final String signature) {
    this.signature = signature;
  }

  public final int getStatus() {
    return status;
  }

  public final void setStatus(final int status) {
    this.status = status;
  }

  public final String getTimestamp() {
    return timestamp;
  }

  public final void setTimestamp(final String timestamp) {
    this.timestamp = timestamp;
  }

  public final String getIp() {
    return ip;
  }

  public final void setIp(final String ip) {
    this.ip = ip;
  }

  public final String getTxnId() {
    return txnId;
  }

  public final void setTxnId(final String txnId) {
    this.txnId = txnId;
  }

  public final int getManagerUserId() {
    return managerUserId;
  }

  public final void setManagerUserId(final int managerUserId) {
    this.managerUserId = managerUserId;
  }

  public final String getMemo() {
    return memo;
  }

  public final void setMemo(final String memo) {
    this.memo = memo;
  }

  public final String getName() {
    return name;
  }

  public final void setName(final String name) {
    this.name = name;
  }

  public final Set<long[]> getAssetIdtreeSet() {
    return assetIdtreeSet;
  }

  public final void setAssetIdtreeSet(final Set<long[]> assetIdtreeSet) {
    this.assetIdtreeSet = assetIdtreeSet;
  }

  public long getAssetId() {
    return assetId;
  }

  public void setAssetId(final long assetId) {
    this.assetId = assetId;
  }

  public long getTokenId() {
    return tokenId;
  }

  public void setTokenId(final long tokenId) {
    this.tokenId = tokenId;
  }

  public long getGroupId() {
    return groupId;
  }

  public void setGroupId(final long groupId) {
    this.groupId = groupId;
  }

  public int getType() {
    return type;
  }

  public void setType(final int type) {
    this.type = type;
  }

  public long getAmount() {
    return amount;
  }

  public void setAmount(final long amount) {
    this.amount = amount;
  }

  public String getTransactionHash() {
    return transactionHash;
  }

  public void setTransactionHash(final String transactionHash) {
    this.transactionHash = transactionHash;
  }

  public String getChain() {
    return chain;
  }

  public void setChain(final String chain) {
    this.chain = chain;
  }

  public String getBank() {
    return bank;
  }

  public void setBank(final String bank) {
    this.bank = bank;
  }

  public String getBankAccount() {
    return bankAccount;
  }

  public void setBankAccount(final String bankAccount) {
    this.bankAccount = bankAccount;
  }

  public String getSwiftCode() {
    return swiftCode;
  }

  public void setSwiftCode(final String swiftCode) {
    this.swiftCode = swiftCode;
  }

  public String getSerials() {
    return serials;
  }

  public void setSerials(final String serials) {
    this.serials = serials;
  }

  public String getCertificateName() {
    return certificateName;
  }

  public void setCertificateName(final String certificateName) {
    this.certificateName = certificateName;
  }

  public String getRegistryName() {
    return registryName;
  }

  public void setRegistryName(String registryName) {
    this.registryName = registryName;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }
}
