package com.solfini.util.blockchain;

public interface BlockchainTransactionSender {
  String POSITION_MANAGEMENT = "POSITION_MANAGEMENT";
  String FUND_MANAGEMENT = "FUND_MANAGEMENT";

  boolean processTransaction(final StringBuilder sb);
}
