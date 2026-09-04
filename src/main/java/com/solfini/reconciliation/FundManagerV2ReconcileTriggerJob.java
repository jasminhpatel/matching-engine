package com.solfini.reconciliation;

import com.solfini.common.Context;
import com.solfini.util.PropertyReader;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Properties;

/**
 * On-demand entry point for composite reconciliation, invoked externally (TSYS-176) once a real
 * CompositeBalanceUpdated event is observed on-chain — not run inline by FundManagerV2SyncJob's own
 * cron cadence (see that job's syncComposites() for why). Intended caller is the blockchain-listener
 * repo's event handler (TSYS-100/TSYS-177), shelling out to this job via its own configurable
 * trigger script, passing the exact network + compositeId the event carried.
 *
 * Unlike FundManagerV2ReconciliationCheckJob (a manual SYMBOL-based preview tool), this takes
 * COMPOSITE_ID directly — the caller already has it from the event, so no
 * getAllComposites/getCompositeTokens discovery loop is needed to resolve it.
 *
 * Config: this job's own preliminary calls need ./config.properties loaded up front (loadConfig()
 * below, before Context/FundingContractV6Loader are touched) since
 * FundManagerV2Reconciliation.loadConfigurationFile() - called inside reconcileComposite() -
 * ignores any -c argument and hardcodes that same path anyway. But reconcileComposite() ALSO calls
 * loadSnap(args, ...), which DOES read -c via its own commons-cli parsing - and when absent,
 * re-initializes PropertyReader with only 3 snapshot-related keys, wiping every other loaded
 * property (including this method's own FUND_MANAGER_CONTRACT_ADDRESS) right before the
 * balance-check block runs. So -c=./config.properties below is NOT optional despite
 * loadConfigurationFile() ignoring it directly - it's what keeps loadSnap()'s reload from wiping
 * everything else. The caller (the trigger script) is responsible for cd-ing into the deployment
 * directory first so this relative path resolves correctly.
 *
 * Usage:
 *   -c=./config.properties -d NETWORK=XDC -d COMPOSITE_ID=0x...
 *   -c=./config.properties -d NETWORK=MAINNET -d COMPOSITE_ID=0x...
 */
public class FundManagerV2ReconcileTriggerJob {

  public static void main(String[] args) {
    final String network = parseArg(args, "NETWORK", "MAINNET");
    final String compositeId = parseArg(args, "COMPOSITE_ID", null);

    final StringBuilder summary = new StringBuilder();
    summary.append("Reconciliation trigger (on-demand, no on-chain push): network=").append(network)
        .append(" compositeId=").append(compositeId).append("\n");
    System.out.println(summary);

    try {
      if (compositeId == null || compositeId.isEmpty()) {
        throw new IllegalArgumentException("COMPOSITE_ID is required");
      }
      loadConfig();
      final String coreAddress = Context.getFundManagerContractByNetworkAndVersion(network, 2);
      final String viewsAddress = FundingContractV6Loader.getViewsContractAddress(network, coreAddress);
      final List<String> memberTokens = FundingContractV6Loader.getCompositeTokens(network, viewsAddress, compositeId);
      if (memberTokens.isEmpty()) {
        throw new IllegalStateException(compositeId + " has no member tokens on " + network
            + " (unknown or retired composite)");
      }
      summary.append("Resolved composite members: ").append(memberTokens).append("\n");
      FundManagerV2Reconciliation.reconcileComposite(args, summary, true, network, compositeId, memberTokens);
    } catch (Exception e) {
      System.out.println("Reconciliation trigger failed: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
    System.out.println(summary);
    System.exit(0);
  }

  private static void loadConfig() throws Exception {
    final File file = new File("./config.properties");
    try (FileInputStream stream = new FileInputStream(file)) {
      PropertyReader.initialize(stream, new Properties());
    }
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
