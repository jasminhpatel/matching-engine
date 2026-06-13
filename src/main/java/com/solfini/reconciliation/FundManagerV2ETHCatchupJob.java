package com.solfini.reconciliation;

import static com.solfini.common.Constants.MAINNET;
import static com.solfini.common.Constants.USDC;
import static com.solfini.common.Constants.USDT;

/**
 * One-time ETH catch-up job. Sends heartbeat batches (totalBatches=2, batchIndex=1, 0 users)
 * for each intermediate snapshotId to advance the on-chain counter without triggering the
 * 22h cadence check. Then runs a real sync at the final target snap.
 *
 * Usage:
 *   -d USDT_ON_CHAIN_SNAP=2 -d USDC_ON_CHAIN_SNAP=3 -d FORCE_SNAP_ID=7
 *
 * USDT: heartbeats 3,4,5,6 → real commit at 7
 * USDC: heartbeats 4,5,6   → real commit at 7
 * XDC continues via FundManagerV2SyncJob as normal.
 *
 * After ETH catches up to XDC's snap, retire this job and re-enable ETH in FundManagerV2SyncJob.
 */
public class FundManagerV2ETHCatchupJob {

  public static void main(String[] args) {
    final StringBuilder summary = new StringBuilder();

    final long usdtOnChainSnap = parseArgLong(args, "USDT_ON_CHAIN_SNAP", 2L);
    final long usdcOnChainSnap = parseArgLong(args, "USDC_ON_CHAIN_SNAP", 3L);
    final long targetSnap = parseArgLong(args, "FORCE_SNAP_ID", 7L);

    summary.append("ETH Catchup: USDT on-chain=").append(usdtOnChainSnap)
        .append(" USDC on-chain=").append(usdcOnChainSnap)
        .append(" target=").append(targetSnap).append("\n");
    System.out.println(summary);

    // Heartbeat batches for USDT (advances snapshotId without cadence check)
    for (long snap = usdtOnChainSnap + 1; snap < targetSnap; snap++) {
      final boolean ok = FundManagerV2SnapUpdater.sendHeartbeatBatch(args, summary, MAINNET, USDT, snap);
      System.out.println(summary);
      if (!ok) {
        summary.append("USDT heartbeat failed at snap=").append(snap).append(", aborting.\n");
        System.out.println(summary);
        System.exit(1);
      }
    }

    // Heartbeat batches for USDC
    for (long snap = usdcOnChainSnap + 1; snap < targetSnap; snap++) {
      final boolean ok = FundManagerV2SnapUpdater.sendHeartbeatBatch(args, summary, MAINNET, USDC, snap);
      System.out.println(summary);
      if (!ok) {
        summary.append("USDC heartbeat failed at snap=").append(snap).append(", aborting.\n");
        System.out.println(summary);
        System.exit(1);
      }
    }

    // Real final sync at target snap (FORCE_SNAP_ID in args is used by FundManagerV2SnapUpdater)
    sync(args, MAINNET, USDC);
    sync(args, MAINNET, USDT);
    System.exit(0);
  }

  private static void sync(String[] args, final String network, final String symbol) {
    final StringBuilder summary = new StringBuilder();
    try {
      final boolean success = FundManagerV2SnapUpdater.update(args, summary, null, network, symbol);
      if (!success) {
        summary.append("ETH Catchup sync failed for ").append(network).append(" ").append(symbol).append("\n");
      } else {
        FundManagerV2Reconciliation.reconcile(args, summary, true, network, symbol);
      }
    } catch (Exception e) {
      summary.append("ETH Catchup Sync Job Failed: ").append(e.getMessage()).append("\n");
      e.printStackTrace();
    }
    System.out.println(summary);
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
}
