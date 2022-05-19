package com.solfini.util;

// Utility functions for transaction id setup.
public class TransactionUtil {

  private TransactionUtil() {
    // Make sure this class is not instantiated
  }

  public static TransactionData decompose(final String str) {
    final int idx = str.indexOf(':');
    final long transactionId = Long.parseLong(str.substring(0, idx));
    final boolean isTransactionEnd = str.substring(idx + 1, str.length()).equals("1");

    return new TransactionData(transactionId, isTransactionEnd);
  }

  public static String compose(long transactionId, boolean isLastMessage) {
    return transactionId + ":" + (isLastMessage ? "1" : "0");
  }
}
