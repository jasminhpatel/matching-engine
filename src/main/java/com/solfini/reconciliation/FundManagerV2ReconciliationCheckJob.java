package com.solfini.reconciliation;

/**
 * Read-only reconciliation preview for one chain/symbol against whatever snapshot is currently
 * published on-chain. Calls FundManagerV2Reconciliation.reconcile() directly and never calls
 * FundManagerV2SnapUpdater.update() or sendHeartbeatBatch() — nothing is pushed to the blockchain.
 *
 * Reads real on-chain positions and real contract balances (live RPC calls) and sends the same
 * HTML summary email the nightly sync would send, so table/styling changes can be previewed
 * against production data before the next scheduled sync runs the same balance check.
 *
 * Usage:
 *   -c=./config.properties -d NETWORK=XDC -d SYMBOL=XUSDC
 *   -c=./config.properties -d NETWORK=MAINNET -d SYMBOL=USDC
 *
 * To avoid emailing the real RECONCILIATION_ALERT_EMAILS distribution list, point -c at a copy
 * of config.properties with RECONCILIATION_ALERT_EMAILS overridden to a test address.
 */
public class FundManagerV2ReconciliationCheckJob {

  public static void main(String[] args) {
    final String network = parseArg(args, "NETWORK", "MAINNET");
    final String symbol = parseArg(args, "SYMBOL", "USDC");

    final StringBuilder summary = new StringBuilder();
    summary.append("Reconciliation check (read-only, no on-chain push): network=").append(network)
        .append(" symbol=").append(symbol).append("\n");
    System.out.println(summary);

    try {
      FundManagerV2Reconciliation.reconcile(args, summary, true, network, symbol);
    } catch (Exception e) {
      System.out.println("Reconciliation check failed: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
    System.out.println(summary);
    System.exit(0);
  }

  private static String parseArg(final String[] args, final String key, final String defaultValue) {
    for (int i = 0; i < args.length - 1; i++) {
      if ("-d".equals(args[i]) && args[i + 1].startsWith(key + "=")) {
        return args[i + 1].substring(key.length() + 1);
      }
    }
    return defaultValue;
  }
}
