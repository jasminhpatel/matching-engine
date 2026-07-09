package com.solfini.reconciliation;

import static com.solfini.util.blockchain.BlockchainTransactionSender.FUND_MANAGEMENT;
import static com.solfini.util.blockchain.BlockchainTransactionSender.POSITION_MANAGEMENT;

import com.solfini.common.Context;
import com.solfini.util.MailUtil;
import com.solfini.util.PropertyReader;
import com.solfini.util.blockchain.util.BlockChainKeyManager;
import com.solfini.util.blockchain.util.RpcUtil;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

/**
 * Daily check of the native gas balance held by each on-chain snap-posting wallet against a
 * single configured floor per chain. Deliberately stateless: no history, no DB, no tiers - just
 * "is it below floor right now", reported by email every run.
 */
public class PosterGasFloorCheckJob {

  private record Row(String chain, String address, double balanceNative, double floorNative, boolean belowFloor,
      boolean checkFailed) {
  }

  public static void main(final String[] args) {
    loadConfiguration(args);
    final boolean dryRun = Boolean.parseBoolean(parseArg(args, "DRY_RUN", "false"));

    try {
      BlockChainKeyManager.loadKeys(Context.getBlockchainKeyFile());
    } catch (final Exception e) {
      e.printStackTrace();
      if (!dryRun) {
        sendFailureEmail("Poster Gas Floor Check failed to load blockchain key file: " + e.getMessage());
      }
      System.exit(1);
      return;
    }

    final List<Row> rows = new ArrayList<>();
    boolean anyFailed = false;

    for (final String target : resolveTargets()) {
      final String[] parts = target.split(":");
      if (parts.length != 2) {
        anyFailed = true;
        rows.add(new Row(target, "-", 0, 0, false, true));
        continue;
      }
      final String chain = parts[0];
      final String function = parts[1];
      final double floorNative = PropertyReader.getProperty("POSTER_GAS_MIN_BALANCE_" + chain.toUpperCase(), 0.0);
      final List<String> addresses = BlockChainKeyManager.getKeys(chain, function);
      if (addresses.isEmpty()) {
        anyFailed = true;
        rows.add(new Row(chain + ":" + function, "-", 0, floorNative, false, true));
        continue;
      }
      for (final String address : addresses) {
        try {
          final BigInteger balanceWei = readNativeBalanceWei(chain, address);
          final BigInteger floorWei = toWei(floorNative);
          rows.add(new Row(chain, address, weiToNative(balanceWei), floorNative, isBelowFloor(balanceWei, floorWei),
              false));
        } catch (final Exception e) {
          e.printStackTrace();
          anyFailed = true;
          rows.add(new Row(chain, address, 0, floorNative, false, true));
        }
      }
    }

    final String report = buildTextReport(rows);
    System.out.println(report);

    if (!dryRun) {
      sendDailyEmail(rows, report);
    }
    System.exit(anyFailed ? 1 : 0);
  }

  static boolean isBelowFloor(final BigInteger balanceWei, final BigInteger floorWei) {
    return balanceWei.compareTo(floorWei) <= 0;
  }

  static BigInteger toWei(final double nativeAmount) {
    return BigDecimal.valueOf(nativeAmount).multiply(BigDecimal.TEN.pow(18)).toBigInteger();
  }

  static double weiToNative(final BigInteger wei) {
    return new BigDecimal(wei).divide(BigDecimal.TEN.pow(18)).doubleValue();
  }

  private static BigInteger readNativeBalanceWei(final String chain, final String address) throws IOException {
    try {
      return readNativeBalanceWei(RpcUtil.createWeb3jConnection(chain, null, false, false), address);
    } catch (final IOException primaryFailure) {
      return readNativeBalanceWei(RpcUtil.createWeb3jConnection(chain, null, true, false), address);
    }
  }

  private static BigInteger readNativeBalanceWei(final Web3j web3j, final String address) throws IOException {
    return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
  }

  private static List<String> resolveTargets() {
    final String raw = PropertyReader.getProperty("POSTER_GAS_FLOOR_TARGETS",
        "ETHEREUM:" + FUND_MANAGEMENT + ",XDC:" + FUND_MANAGEMENT + ",POLYGON:" + POSITION_MANAGEMENT);
    return List.of(raw.split(","));
  }

  private static String buildTextReport(final List<Row> rows) {
    final StringBuilder summary = new StringBuilder();
    summary.append("Poster Gas Floor Check\n");
    summary.append(String.format("\t %-8s | %-42s | %12s | %12s | %s%n", "Chain", "Poster", "Balance", "Floor",
        "Status"));
    summary.append("\t ").append("-".repeat(90)).append("\n");
    for (final Row row : rows) {
      final String status = row.checkFailed() ? "CHECK FAILED" : row.belowFloor() ? "BELOW FLOOR" : "OK";
      summary.append(String.format("\t %-8s | %-42s | %12.6f | %12.6f | %s%n", row.chain(), row.address(),
          row.balanceNative(), row.floorNative(), status));
    }
    return summary.toString();
  }

  private static void sendDailyEmail(final List<Row> rows, final String report) {
    boolean anyBelow = false;
    boolean anyFailed = false;
    for (final Row row : rows) {
      anyBelow = anyBelow || row.belowFloor();
      anyFailed = anyFailed || row.checkFailed();
    }
    final String subject = anyFailed ? "[POSTER GAS FLOOR: CHECK FAILED] Poster gas floor check"
        : anyBelow ? "[POSTER GAS FLOOR: BELOW FLOOR] Poster gas floor check"
        : "[POSTER GAS FLOOR: OK] Poster gas floor check";
    try {
      MailUtil.sendMessage(alertRecipients(), subject, report, null);
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  private static void sendFailureEmail(final String message) {
    try {
      MailUtil.sendMessage(alertRecipients(), "[POSTER GAS FLOOR: JOB FAILED] Poster gas floor check", message, null);
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  private static String[] alertRecipients() {
    return PropertyReader.getProperty("POSTER_GAS_FLOOR_ALERT_EMAILS",
        PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com")).split(",");
  }

  static String parseConfigPath(final String[] args) {
    for (final String arg : args) {
      if (arg.startsWith("-c=")) {
        return arg.substring(3);
      }
    }
    return "./config.properties";
  }

  static String parseArg(final String[] args, final String key, final String defaultValue) {
    for (int i = 0; i < args.length - 1; i++) {
      if ("-d".equals(args[i]) && args[i + 1].startsWith(key + "=")) {
        return args[i + 1].substring(key.length() + 1);
      }
    }
    return defaultValue;
  }

  private static void loadConfiguration(final String[] args) {
    try (FileInputStream stream = new FileInputStream(parseConfigPath(args))) {
      PropertyReader.initialize(stream, new Properties());
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }
}
