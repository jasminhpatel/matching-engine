package com.solfini.reconciliation;

import com.solfini.common.Context;
import java.util.List;

/**
 * Read-only reconciliation preview for one chain/symbol against whatever snapshot is currently
 * published on-chain. Composite-aware since TSYS-131: resolves the given symbol to whichever V6
 * composite currently holds it (by walking getAllComposites/getCompositeTokens - the same
 * discovery FundManagerV2SyncJob already does, just without a forward tokenComposite() lookup of
 * its own) and calls FundManagerV2Reconciliation.reconcileComposite() for that whole composite.
 * Never calls FundManagerV2SnapUpdater.updateComposite() or sendHeartbeatBatch() — nothing is
 * pushed to the blockchain.
 *
 * For a Grouped composite (e.g. Ethereum's USDC+USDT), specifying either member symbol resolves to
 * the same composite and produces the same whole-composite report.
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
      final String tokenAddress = Context.getTokenAddressBySymbol(symbol);
      if (tokenAddress == null) {
        throw new IllegalArgumentException("Unknown symbol: " + symbol);
      }
      final String coreAddress = Context.getFundManagerContractByNetworkAndVersion(network, 2);
      final String viewsAddress = FundingContractV6Loader.getViewsContractAddress(network, coreAddress);
      String matchedCompositeId = null;
      List<String> matchedMemberTokens = null;
      for (final String compositeId : FundingContractV6Loader.getAllComposites(network, viewsAddress)) {
        final List<String> memberTokens = FundingContractV6Loader.getCompositeTokens(network, viewsAddress, compositeId);
        if (memberTokens.stream().anyMatch(t -> t.equalsIgnoreCase(tokenAddress))) {
          matchedCompositeId = compositeId;
          matchedMemberTokens = memberTokens;
          break;
        }
      }
      if (matchedCompositeId == null) {
        throw new IllegalStateException(symbol + " (" + tokenAddress + ") is not a member of any composite on " + network);
      }
      summary.append("Resolved to composite ").append(matchedCompositeId).append(" (members: ")
          .append(matchedMemberTokens).append(")\n");
      FundManagerV2Reconciliation.reconcileComposite(args, summary, true, network, matchedCompositeId, matchedMemberTokens);
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
