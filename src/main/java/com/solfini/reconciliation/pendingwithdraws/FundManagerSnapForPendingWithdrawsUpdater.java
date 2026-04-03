package com.solfini.reconciliation.pendingwithdraws;

import static com.solfini.common.Constants.COLD_START;
import static com.solfini.common.Constants.COPY_TRADE_ONLY;
import static com.solfini.common.Constants.ERROR_LOG;
import static com.solfini.common.Constants.MODE;
import static com.solfini.common.Constants.PRIMARY;
import static com.solfini.common.Constants.SECONDARY;
import static com.solfini.common.Constants.SNAPSHOT_ID;
import static com.solfini.common.Constants.WARM_START;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.Position;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.reconciliation.BlockchainNotionalCache;
import com.solfini.reconciliation.FolderUtils;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.blockchain.BlockchainSenderFactory;
import com.solfini.util.blockchain.BlockchainTransactionSender;
import com.solfini.util.blockchain.model.WithdrawableAmountUpdateTransaction;
import com.solfini.util.blockchain.util.BlockChainKeyManager;
import com.solfini.util.snapshot.SnapConverter;
import com.solfini.util.snapshot.SnapTransformer;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slf4j.event.Level;

public class FundManagerSnapForPendingWithdrawsUpdater {
  private static final Logger LOGGER = LogManager.getLogger(FundManagerSnapForPendingWithdrawsUpdater.class);

  private static boolean initialize(String[] args) throws Exception {
    final Options options = new Options();
    options.addOption(Option.builder("h").longOpt("help").desc("show help").required(false).build());
    options.addOption(
        Option.builder("c").longOpt("conf").desc("configuration file or directory").hasArg().argName("file|dir").required().build());
    options.addOption(
        Option.builder("m").longOpt(MODE).desc("mode of operation (primary|secondary)").hasArg().argName(MODE).required(false).build());
    options.addOption(
        Option.builder("s").longOpt("snapshot").desc("load state from a snapshot").hasArg().argName(SNAPSHOT_ID).required(false).build());
    options.addOption(Option.builder().longOpt(WARM_START).desc("warm start by recovering state from a snapshot").required(false).build());
    options.addOption(Option.builder().longOpt(COLD_START).desc("cold start by not recovering any state").required(false).build());
    options.addOption(
        Option.builder("d").desc("define (or override) configuration property").hasArgs().argName("key>=<value").required(false).build());
    options.addOption(Option.builder().longOpt("trace").desc("enable trace logging").required(false).build());
    options.addOption(Option.builder().longOpt("debug").desc("enable debug logging").required(false).build());
    options.addOption(Option.builder().longOpt("info").desc("enable info logging").required(false).build());
    options.addOption(Option.builder().longOpt("warn").desc("enable warn logging").required(false).build());

    for (final String arg : args) {
      if (arg.equals("-h") || arg.equals("--help")) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("com.solfini.matchengine.MatchEngineStarter", options);
        System.out.println();
        return false;
      }
    }

    CommandLine cmd;
    try {
      final CommandLineParser parser = new DefaultParser();
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      System.err.println("Run with --help option for usage information");
      return false;
    }

    final Properties overlay = new Properties();
    // mode
    if (cmd.hasOption("m")) {
      String mode = cmd.getOptionValue("m").toLowerCase();
      if (mode.equals(PRIMARY) || mode.equals(SECONDARY) || mode.equals(COPY_TRADE_ONLY)) {
        overlay.setProperty("CONTROLLER_MODE", mode);
      } else {
        System.err.println("Error: Invalid mode specified");
        return false;
      }
    }
    // snapshot
    if (cmd.hasOption("s")) {
      if (cmd.hasOption(COLD_START)) {
        System.err.println("Error: Cannot use snapshot and cold-start options together");
        return false;
      }
      overlay.setProperty("LOAD_FROM_SNAP", cmd.getOptionValue("s"));
    }
    // cold/warm start
    if (cmd.hasOption(WARM_START)) {
      if (cmd.hasOption(COLD_START)) {
        System.err.println("Error: Cannot use warm-start and cold-start options together");
        return false;
      }
      overlay.setProperty("PUBLISH_ON_SNAP_AND_REPLAY", "true");
      overlay.setProperty("LOAD_FROM_SNAP_AND_REPLAY", "NONE");
    } else if (cmd.hasOption(COLD_START)) {
      overlay.setProperty("LOAD_FROM_SNAP", "");
      overlay.setProperty("LOAD_FROM_SNAP_AND_REPLAY", "NONE");
      SnapLoader.setSnapLoaderMode(false);
    }
    // logging
    if (cmd.hasOption("trace")) {
      overlay.setProperty(Constants.LOGLEVEL, "TRACE");
    } else if (cmd.hasOption("debug")) {
      overlay.setProperty(Constants.LOGLEVEL, "DEBUG");
    } else if (cmd.hasOption("info")) {
      overlay.setProperty(Constants.LOGLEVEL, "INFO");
    } else if (cmd.hasOption("warn")) {
      overlay.setProperty(Constants.LOGLEVEL, "WARN");
    }

    // defines
    if (cmd.hasOption("d")) {
      for (String define : cmd.getOptionValues("d")) {
        String[] tokens = define.split("=");
        if (tokens.length > 0) {
          overlay.setProperty(tokens[0].trim(), tokens.length > 1 ? tokens[1].trim() : "");
        }
      }
    }

    // load properties
    try {
      File file = new File(cmd.getOptionValue("c"));
      if (file.exists() && file.isDirectory()) {
        file = new File(cmd.getOptionValue("c") + "/config.properties");
      }

      InputStream stream = new FileInputStream(file);
      PropertyReader.initialize(stream, overlay);
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      return false;
    }

    // setup log level
    LogLevel.setLevel(Level.valueOf(PropertyReader.getProperty(Constants.LOGLEVEL, "INFO").trim().toUpperCase()));

    return true;
  }

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

      PropertyReader.initialize(stream, properties);

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

  private static void loadBlockchainKeyFile() {
    // load blockchain keys
    System.out.println("Blockchain key file loading " + Context.isBlockchainPositionManagerEnabled());
    try {
      BlockChainKeyManager.loadKeys(Context.getBlockchainKeyFile());
      System.out.println("Blockchain key file loaded.");
    } catch (final Exception e) {
      e.printStackTrace();
      System.err.println("Error: " + e.getMessage());
    }
  }

  public static boolean update(final String[] args, final StringBuilder sb, List<Integer> userIds) throws Exception {
    sb.append("Fund manager (Ethereum) snap update started. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    initialize(args);
    final Properties overlay = new Properties();
    loadConfigurationFile(overlay);

    final String snapDirectory = PropertyReader.getProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", "");
    final String symbolsToIgnore = PropertyReader.getProperty("IGNORE_SYMBOL_LIST", "");
    final int fundManagerBatchSize = PropertyReader.getProperty("FUND_MANAGER_BATCH_SIZE", 500);
    final double diffPercentage = PropertyReader.getProperty("ETHEREUM_NOTIONAL_DIFF_PERCENTAGE", 0.05);
    loadBlockchainKeyFile();
    final List<String> snapshotIds = FolderUtils.getLastNTimestampFolders(snapDirectory, 1);

    final String latestSnapshotId = snapshotIds.getFirst();
    final long snapId = StringUtil.toLong(latestSnapshotId);
    final String latestSnapFile = snapDirectory + File.separator + latestSnapshotId;
    LOGGER.info("Fund Manager latestSnapFile: " + latestSnapFile);
    final Set<Integer> symbolsToIgnoreSet = new HashSet<>();
    if (symbolsToIgnore != null && !symbolsToIgnore.isEmpty()) {
      String[] symbolsToIgnoreArr = symbolsToIgnore.split(",");
      for (String s : symbolsToIgnoreArr) {
        symbolsToIgnoreSet.add(StringUtil.toInt(s));
      }
    }
    final Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> latestUserWithdrawables =
        loadFromSnap(latestSnapFile, args, symbolsToIgnoreSet);
    sb.append("Latest snap file loaded to memory. file: ").append(latestSnapFile).append(" time: ")
        .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");

    final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> diff = userWithdrawablesToUpdated(latestUserWithdrawables, userIds);

    final int noOfBatches = (int) Math.ceil(diff.size() / (double) fundManagerBatchSize);
    final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> batchUserWithdrawables = new ArrayList<>();
    int batchId = 0;
    int recordCount = 0;
    boolean updateSent = false;
    boolean success = true;
    for (WithdrawableAmountUpdateTransaction.UserWithdrawable userWithdrawable : diff) {
      batchUserWithdrawables.add(userWithdrawable);
      recordCount++;
      if (recordCount >= fundManagerBatchSize) {
        batchId++;
        updateSent = true;
        try {
          final WithdrawableAmountUpdateTransaction transaction = new WithdrawableAmountUpdateTransaction();
          transaction.setId(snapId);
          transaction.setChainType(Context.getFundManagerChain());
          transaction.setContractAddress(Context.getFundManagerContractAddress());
          transaction.setUserWithdrawables(batchUserWithdrawables);
          transaction.setBatchId(batchId);
          transaction.setNoOfBatches(noOfBatches);
          final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
          if (sender != null) {
            boolean status = sender.processTransaction(sb);
            success = success && status;
            sb.append("Snap updated. batch ").append(" of ").append(noOfBatches).append(" status: ").append(status).append(" time: ")
                .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
          } else {
            success = false;
            sb.append("Snap update failed. batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ")
                .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
          }
        } catch (Exception e) {
          sb.append("Snap update failed. batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ")
              .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append(" ").append(e.getMessage()).append("\n");
          LOGGER.error(ERROR_LOG, e);
        }
        recordCount = 0;
        batchUserWithdrawables.clear();
      }
    }

    System.out.println("noOfBatches " + noOfBatches);
    if (recordCount > 0) {
      batchId++;
      updateSent = true;
      try {
        WithdrawableAmountUpdateTransaction transaction = new WithdrawableAmountUpdateTransaction();
        transaction.setId(snapId);
        transaction.setChainType(Context.getFundManagerChain());
        transaction.setContractAddress(Context.getFundManagerContractAddress());
        transaction.setUserWithdrawables(batchUserWithdrawables);
        transaction.setBatchId(batchId);
        transaction.setNoOfBatches(noOfBatches);
        final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
        if (sender != null) {
          boolean status = sender.processTransaction(sb);
          success = success && status;
          sb.append("Snap updated. batch ").append(" of ").append(noOfBatches).append(" status: ").append(status).append(" time: ")
              .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        } else {
          success = false;
          sb.append("Snap update failed. batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ")
              .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        }
      } catch (Exception e) {
        sb.append("Snap update failed. batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ")
            .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append(" ").append(e.getMessage()).append("\n");
        LOGGER.error(ERROR_LOG, e);
      }
    }

    if (!updateSent) {//send empty update
      try {
        WithdrawableAmountUpdateTransaction transaction = new WithdrawableAmountUpdateTransaction();
        transaction.setId(snapId);
        transaction.setChainType(Context.getFundManagerChain());
        transaction.setContractAddress(Context.getFundManagerContractAddress());
        transaction.setUserWithdrawables(batchUserWithdrawables);
        transaction.setBatchId(1);
        transaction.setNoOfBatches(1);
        final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
        if (sender != null) {
          boolean status = sender.processTransaction(sb);
          success = success && status;
          sb.append("Snap updated. batch ").append(" of ").append(noOfBatches).append(" status: ").append(status).append(" time: ")
              .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        } else {
          success = false;
          sb.append("Snap update failed. batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ")
              .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        }
      } catch (Exception e) {
        sb.append("Snap update failed. batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ")
            .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append(" ").append(e.getMessage()).append("\n");
        LOGGER.error(ERROR_LOG, e);
      }
    }

    if (success) {
      long updated = System.currentTimeMillis();
      for (WithdrawableAmountUpdateTransaction.UserWithdrawable userWithdrawable : diff) {
        BlockchainNotionalCache.upsert(userWithdrawable, updated, snapId);
      }
      sb.append("Snap update successful. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    } else {
      sb.append("Snap update failed. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    }
    return success;
  }

  private static Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> loadFromSnap(final String snapFile, final String[] args,
      final Set<Integer> symbolsToIgnoreSet) {
    final List<Message> snapUserPositions = loadSnap(args, snapFile);
    final HashMap<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> userWithdrawables = new HashMap<>();
    for (Message message : snapUserPositions) {
      if (message instanceof BalanceAdminMessage balanceAdminMessage) {
        double withdrawable = 0;
        for (Position p : balanceAdminMessage.getPositionArr()) {
          if (p != null) {
            if (symbolsToIgnoreSet.contains(p.getInstrumentId())) {
              continue;
            }
            withdrawable += p.getUsdValue() + p.getUsdUnrealized();
          }
        }
        System.out.println(balanceAdminMessage.getUserId() + " - " + withdrawable + " - " + (long) (withdrawable * 1_000_000));
        final WithdrawableAmountUpdateTransaction.UserWithdrawable userWithdrawable =
            new WithdrawableAmountUpdateTransaction.UserWithdrawable(balanceAdminMessage.getUserId(), (long) (withdrawable * 1_000_000));
        userWithdrawables.put(balanceAdminMessage.getUserId(), userWithdrawable);
      }
    }
    return userWithdrawables;
  }

  private static List<WithdrawableAmountUpdateTransaction.UserWithdrawable> userWithdrawablesToUpdated(
      final Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> userWithdrawables, final List<Integer> userIds) {
    final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> list = new ArrayList<>();
    for (Integer userId : userIds) {
      if (userWithdrawables.containsKey(userId)) {
        list.add(userWithdrawables.get(userId));
      }
    }
    return list;
  }
}

