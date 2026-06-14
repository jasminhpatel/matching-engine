package com.solfini.reconciliation;

import com.solfini.util.MailUtil;
import com.solfini.util.PropertyReader;

import java.io.IOException;
import javax.mail.MessagingException;

/**
 * Generic single-chain catch-up job. Sends heartbeat batches for each intermediate snap ID
 * (when ON_CHAIN_SNAP is provided) then runs a real sync at the target snap.
 *
 * Heartbeat batches use totalBatches=2, batchIndex=1, 0 users — they advance the on-chain
 * snapshotId counter without triggering the 22h cadence check (isLastBatch=false).
 *
 * Usage examples:
 *   XDC one step behind:  -d NETWORK=XDC    -d SYMBOL=XUSDC -d FORCE_SNAP_ID=7
 *   ETH USDT far behind:  -d NETWORK=MAINNET -d SYMBOL=USDT  -d ON_CHAIN_SNAP=2 -d FORCE_SNAP_ID=7
 *   ETH USDC far behind:  -d NETWORK=MAINNET -d SYMBOL=USDC  -d ON_CHAIN_SNAP=3 -d FORCE_SNAP_ID=7
 *
 * Omit ON_CHAIN_SNAP (or leave at default -1) to skip heartbeats and send only the final sync.
 */
public class FundManagerV2CatchupJob {

  public static void main(String[] args) {
    final String network     = parseArg(args,     "NETWORK",      "MAINNET");
    final String symbol      = parseArg(args,     "SYMBOL",       "USDC");
    final long   onChainSnap = parseArgLong(args, "ON_CHAIN_SNAP", -1L);
    final long   targetSnap  = parseArgLong(args, "FORCE_SNAP_ID",  0L);

    final StringBuilder summary = new StringBuilder();
    summary.append("Chain catchup: network=").append(network)
        .append(" symbol=").append(symbol)
        .append(" onChainSnap=").append(onChainSnap < 0 ? "n/a" : onChainSnap)
        .append(" target=").append(targetSnap).append("\n");
    System.out.println(summary);

    if (onChainSnap >= 0) {
      for (long snap = onChainSnap + 1; snap < targetSnap; snap++) {
        final boolean ok = FundManagerV2SnapUpdater.sendHeartbeatBatch(args, summary, network, symbol, snap);
        System.out.println(summary);
        if (!ok) {
          summary.append("Heartbeat failed at snap=").append(snap).append(", aborting.\n");
          System.out.println(summary);
          System.exit(1);
        }
      }
    }

    try {
      final boolean success = FundManagerV2SnapUpdater.update(args, summary, null, network, symbol);
      if (success) {
        FundManagerV2Reconciliation.reconcile(args, summary, true, network, symbol);
      } else {
        summary.append("Chain catchup sync failed. network=").append(network)
            .append(" symbol=").append(symbol).append("\n");
        sendFailureEmail(summary, network, symbol);
      }
    } catch (Exception e) {
      summary.append("Chain Catchup Sync Job Failed: ").append(e.getMessage()).append("\n");
      e.printStackTrace();
      try {
        sendFailureEmail(summary, network, symbol);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
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

  private static long parseArgLong(final String[] args, final String key, final long defaultValue) {
    for (int i = 0; i < args.length - 1; i++) {
      if ("-d".equals(args[i]) && args[i + 1].startsWith(key + "=")) {
        try {
          return Long.parseLong(args[i + 1].substring(key.length() + 1));
        } catch (NumberFormatException ignored) {
        }
      }
    }
    return defaultValue;
  }

  private static void sendFailureEmail(final StringBuilder summary, final String network,
      final String symbol) throws MessagingException, IOException {
    summary.insert(0, "Chain Catchup Sync Job Failed (" + network + " " + symbol + "):\n");
    String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com").split(",");
    MailUtil.sendMessage(to, "Chain Catchup Sync Failed (" + network + " " + symbol + ").", summary.toString(), null);
  }
}
