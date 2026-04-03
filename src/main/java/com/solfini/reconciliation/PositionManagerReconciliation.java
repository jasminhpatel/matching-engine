package com.solfini.reconciliation;

import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.util.MailUtil;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.snapshot.SnapConverter;
import com.solfini.util.snapshot.SnapTransformer;
import java.util.Comparator;
import java.util.HashMap;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.real_logic.artio.fields.DecimalFloat;

import javax.mail.MessagingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PositionManagerReconciliation {
  private static final Logger LOGGER = LogManager.getLogger(PositionManagerReconciliation.class);

  private static List<Message> loadSnap(final String[] args, String snapfile) {
    final Options options = new Options();
    options.addOption(Option.builder("h").longOpt("help").desc("show help").required(false).build());
    options.addOption(Option.builder("s").longOpt("snapshot").desc("snapshot directory").hasArg().argName("dir").required().build());
    options.addOption(Option.builder("j").longOpt("json").desc("json file path").hasArg().argName("file").required().build());
    options.addOption(
        Option.builder("m").longOpt("mode").desc("mode of operation (export|import)").hasArg().argName("mode").required().build());
    options.addOption(Option.builder().longOpt("debug").desc("enable debug logging").required(false).build());
    options.addOption(Option.builder().longOpt("prune").desc("enable snapshot pruning").required(false).build());
    options.addOption(Option.builder().longOpt("clean").desc("enable snapshot cleaning").required(false).build());
    options.addOption(Option.builder().longOpt("validate").desc("enable snapshot validation").required(false).build());
    options.addOption(Option.builder("c").longOpt("config").desc("configuration file path").hasArg().argName("file").build());
    options.addOption(Option.builder().longOpt("data-port").desc("data port configuration file path").hasArg().argName("file").build());
    options.addOption(Option.builder("").longOpt("transform").desc("apply transformation").hasArgs().argName("name").build());

    for (String arg : args) {
      if (arg.equals("-h") || arg.equals("--help")) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("SnapConverter", options);
        System.out.println();
        return null;
      }
    }

    final CommandLineParser parser = new DefaultParser();
    try {
      final CommandLine cmd = parser.parse(options, args);
      final String snapshot = snapfile;
      final String json = cmd.getOptionValue("j");
      final String mode = cmd.getOptionValue("m").toLowerCase();

      // Load config file
      InputStream stream = null;
      if (cmd.hasOption("c")) {
        File file = new File(cmd.getOptionValue("c"));
        if (file.exists() && file.isDirectory()) {
          file = new File(cmd.getOptionValue("c") + "/config.properties");
        }
        stream = new FileInputStream(file);
      }

      final Properties properties = new Properties();
      properties.setProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", snapshot);
      properties.setProperty("PUBLISH_MARKET_DATA", "false");
      properties.setProperty("NUM_ENCODER_THREADS", "0");
      PoolSize.minimize(properties);

      com.solfini.util.PropertyReader.initialize(stream, properties);

      // Load data port config file
      SnapTransformer transformer = null;
      if (cmd.hasOption("data-port")) {
        final File file = new File(cmd.getOptionValue("data-port"));
        final InputStream s = new FileInputStream(file);
        properties.clear();
        properties.load(s);
        transformer = new SnapTransformer(properties);
      }

      final SnapConverter snapConverter =
          new SnapConverter(cmd.hasOption("debug"), cmd.hasOption("prune"), cmd.hasOption("clean"), transformer);

      if (cmd.hasOption("transform")) {
        for (final String name : cmd.getOptionValues("transform")) {
          snapConverter.addTransform(name);
        }
      }

      if (mode.equals("export")) {
        boolean valid = false;
        final File snapshotPath = new File(snapshot);
        if (snapshotPath.exists() && snapshotPath.isDirectory()) {
          final File doneFile = new File(snapshot + "/done");
          if (doneFile.exists() && doneFile.isFile()) {
            valid = true;
          }
        }
        if (!valid) {
          System.out.println("ERROR: Specified directory " + snapshot + " does not contain a valid snapshot");
          System.exit(1);
        }

/*        final File jsonFile = new File(json);
        if (jsonFile.exists() && !jsonFile.isFile()) {
          System.out.println("ERROR: Specified output file " + json + " already exists and is not a regular file");
          System.exit(1);
        }*/

        List<Message> messages = snapConverter.exportSnapshot(snapshot, json);
        if (cmd.hasOption("validate") && !snapConverter.validate()) {
          System.exit(1);
        }
        return messages;
      } else if (mode.equals("import")) {
        final File snapshotPath = new File(snapshot);
        if (!snapshotPath.exists() || !snapshotPath.isDirectory()) {
          System.out.println("ERROR: Specified directory " + snapshot + " does not exist");
          System.exit(1);
        }

        final File jsonFile = new File(json);
        if (!jsonFile.exists() || !jsonFile.isFile()) {
          System.out.println("ERROR: Specified input file " + json + " does not exist or is not a regular file");
          System.exit(1);
        }

        snapConverter.importSnapshot(json, snapshot);
      } else {
        System.out.println("ERROR: Invalid mode specified - " + mode);
      }
    } catch (final Exception e) {
      System.out.println("ERROR: " + e.getMessage());
      e.printStackTrace();
      System.out.println("Run with --help option for usage information");
      System.exit(1);
    }

    System.exit(0);

    return null;
  }

  private static boolean loadConfigurationFile(final Properties overlay) {
    try {
      String configFile = "./config.properties";

      File file = new File(configFile);
      if (file.exists() && file.isDirectory()) {
        file = new File(configFile + "/config.properties");
      }

      final InputStream stream = new FileInputStream(file);
      PropertyReader.initialize(stream, overlay);

      return true;
    } catch (final Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  public static boolean reconcile(String[] args, final StringBuilder summary, final StringBuilder userPositions,
      final StringBuilder marketMakerPositions) throws IOException, MessagingException {
    boolean success = false;
    summary.append("\n");
    userPositions.append("UserId,AssetId,Symbol,SnapBalance,BlockchainBalance,Status\n");
    marketMakerPositions.append("AssetId,Symbol,MarketMakerBalance,OtherSystemUserBalance,TotalUserBalance,Status\n");

    final Properties overlay = new Properties();
    loadConfigurationFile(overlay);
    int missingUsers = 0, missingPositions = 0, mismatchedPositions = 0, matchedPositions = 0;
    // Load positions from PositionManager Smart Contract
    final PositionManagerSnapData blockchainSnapData = PositionManagerLoader.getUserPositions();
    final Map<Integer, PositionManagerSnapData.UserPositions> blockchainPositionsMap = blockchainSnapData.getUserPositionsMap();
    final Map<Integer, MarketMakerPosition> marketMakerPositionMap = new HashMap<>();

    // load positions from SnapFile
    String snapshotId = blockchainSnapData.getSnapshotId();
    LOGGER.info("Blockchain snap loaded. snapshotId: " + snapshotId);
    String snapFile = PropertyReader.getProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", "") + "/" + snapshotId;
    List<Message> snapUserPositions = loadSnap(args, snapFile);
    // Reconcile
    for (Message message : snapUserPositions) {
      if (message instanceof BalanceAdminMessage balanceAdminMessage) {
        int userId = balanceAdminMessage.getUserId();
        processMarketMakerReconciliation(balanceAdminMessage, marketMakerPositionMap);
        if (userId < 20) {//system users
          continue;
        }
        if (balanceAdminMessage.getBalanceList() != null && !balanceAdminMessage.getBalanceList().isEmpty()) {
          PositionManagerSnapData.UserPositions blockchainUserPositions = blockchainPositionsMap.get(balanceAdminMessage.getUserId());
          if (blockchainUserPositions == null) {
            missingUsers++;
          }

          for (Balance balance : balanceAdminMessage.getBalanceList()) {
            String symbol = "";
            Instrument instrument = InstrumentCache.get(balance.getAssetId());
            if (instrument != null) {
              symbol = instrument.getSymbol();
            } else {
              InstrumentPair pair = InstrumentCache.getPair(balance.getAssetId());
              if (pair != null) {
                symbol = pair.getSymbol();
              }
            }

            if (blockchainUserPositions == null) {
              if (balance.getBalance().compareTo(new DecimalFloat(0)) == 0) { //snap balance is zero and no balance in blockchain
/*                matchedPositions++;
                csv.append(balanceAdminMessage.getUserId()).append(",").append(balance.getAssetId()).append(",").append(symbol).append(",").append(balance.getBalance().toDouble()).append(",")
                    .append(0).append(",Match\n");*/
                continue;
              } else {
                userPositions.append(balanceAdminMessage.getUserId()).append(",").append(balance.getAssetId()).append(",").append(symbol).append(",")
                    .append(balance.getBalance().toDouble()).append(",").append("0").append(",User doesn't exist on blockchain\n");
                continue;
              }
            }

            PositionManagerSnapData.Position blockchainPosition = blockchainUserPositions.getAssetPositions().get(balance.getAssetId());
            if (blockchainPosition == null) {
              if (balance.getBalance().compareTo(new DecimalFloat(0)) == 0) { //snap balance is zero and no balance in blockchain
                //matchedPositions++;
                //csv.append(balanceAdminMessage.getUserId()).append(",").append(balance.getAssetId()).append(",").append(symbol).append(",").append(balance.getBalance().toDouble()).append(",")
                //    .append(0).append(",Match\n");
                continue;
              } else {
                userPositions.append(balanceAdminMessage.getUserId()).append(",").append(balance.getAssetId()).append(",").append(symbol).append(",")
                    .append(balance.getBalance().toDouble()).append(",").append("0").append(",Position doesn't exist on blockchain\n");
                missingPositions++;
                continue;
              }
            }
            if (balance.getBalance().compareTo(blockchainPosition.getBalance()) != 0) {
              mismatchedPositions++;

              userPositions.append(balanceAdminMessage.getUserId()).append(",").append(balance.getAssetId()).append(",").append(symbol).append(",").append(balance.getBalance().toDouble()).append(",")
                  .append(blockchainPosition.getBalance()).append(",Mismatch\n");
            } else {
              if (balance.getBalance().compareTo(new DecimalFloat(0)) == 0) {// skip zero balances in both sides
                continue;
              }
              matchedPositions++;
              userPositions.append(balanceAdminMessage.getUserId()).append(",").append(balance.getAssetId()).append(",").append(symbol).append(",").append(balance.getBalance().toDouble()).append(",")
                  .append(blockchainPosition.getBalance()).append(",Match\n");
            }
          }
        } else {
          //csv.append(userId).append(",").append("0").append(",").append("0").append(",").append("0").append(",User positions doesn't exist on snap file\n");
          //continue;
        }
      }
    }
    List<MarketMakerPosition> sortedMarketMakerPositions =
        marketMakerPositionMap.values()
            .stream()
            .sorted(Comparator.comparingLong(MarketMakerPosition::getSecurityId))
            .toList();
    final double tolerance = 1e-6;
    for (MarketMakerPosition m: sortedMarketMakerPositions) {
      String status = "Mismatched";
      double mm = Math.abs(m.getMarketMakerBalance());
      double total = Math.abs(m.getTotalUserBalance());
      double diff = Math.abs(mm -total);

      if (mm == 0.0 && total == 0.0) {
        status = "Matched";
      } else if (total == 0.0) {
        status = "Mismatched";
      } else if (Math.abs(diff) < tolerance) {
        status = "Matched";
      }
      if (total > 0 && "Mismatched".equalsIgnoreCase(status)) {
        double percentDiff = diff / total;
        if (percentDiff <= 0.05) {
          status = "Matched within 5%";
        }
      }

      marketMakerPositions.append(m.getSecurityId()).append(",").append(m.getSymbol()).append(",")
          .append(m.getMarketMakerBalance()).append(",").append(m.getSystemUserBalance()).append(",")
          .append(m.getTotalUserBalance()).append(",").append(status).append("\n");
    }
    // todo reverse check (users and assets not in snap but exists in blockchain)
    summary.append("PositionManager Reconciliation Summary: \n");
    summary.append("\t Snapshot Id: ").append(blockchainSnapData.getSnapshotId()).append("\n");
    summary.append("\t missingUsers: ").append(missingUsers).append("\n");
    summary.append("\t missingPositions: ").append(missingPositions).append("\n");
    summary.append("\t mismatchedPositions: ").append(mismatchedPositions).append("\n");
    summary.append("\t matchedPositions: ").append(matchedPositions).append("\n");

    System.out.println(summary);
    System.out.println();
    System.out.println(userPositions);
    System.out.println();
    System.out.println(marketMakerPositions);

    if (missingPositions == 0 && mismatchedPositions == 0) {
      success = true;
    }
    return success;
  }

  private static void processMarketMakerReconciliation(final BalanceAdminMessage balanceAdminMessage,  final Map<Integer, MarketMakerPosition> marketMakerPositionMap) {
    int userId = balanceAdminMessage.getUserId();
    if (!balanceAdminMessage.getBalanceList().isEmpty()) {
      for (Balance balance : balanceAdminMessage.getBalanceList()) {
        MarketMakerPosition marketMakerPosition = marketMakerPositionMap.get(balance.getAssetId());
        if (marketMakerPosition == null) {
          String symbol = "";
          Instrument instrument = InstrumentCache.get(balance.getAssetId());
          if (instrument != null) {
            symbol = instrument.getSymbol();
          } else {
            InstrumentPair pair = InstrumentCache.getPair(balance.getAssetId());
            if (pair != null) {
              symbol = pair.getSymbol();
            }
          }
          marketMakerPosition = new MarketMakerPosition(balance.getAssetId(), symbol);
          marketMakerPositionMap.put(balance.getAssetId(), marketMakerPosition);
        }
        double balanceValue = balance.getBalance().toDouble();
        if (userId == 8) { // market maker
          marketMakerPosition.addToMarketMaker(balanceValue);
        } else if (userId < 20) {// system users
          marketMakerPosition.addToSystemUserBalance(balanceValue);
        } else {
          marketMakerPosition.addToTotalUser(balanceValue);
        }
      }
    }
  }

  private static class MarketMakerPosition {
    private final int securityId;
    private final String symbol;
    private double systemUserBalance;
    private double marketMakerBalance;
    private double totalUserBalance;

    private MarketMakerPosition(int securityId, String symbol) {
      this.securityId = securityId;
      this.symbol = symbol;
    }

    public void addToSystemUserBalance(final double balance) {
      this.systemUserBalance += balance;
    }

    public void addToMarketMaker(final double balance) {
      this.marketMakerBalance += balance;
    }

    public void addToTotalUser(final double balance) {
      this.totalUserBalance += balance;
    }

    public int getSecurityId() {
      return securityId;
    }

    public String getSymbol() {
      return symbol;
    }

    public double getSystemUserBalance() {
      return systemUserBalance;
    }

    public double getMarketMakerBalance() {
      return marketMakerBalance;
    }

    public double getTotalUserBalance() {
      return totalUserBalance;
    }
  }
}

