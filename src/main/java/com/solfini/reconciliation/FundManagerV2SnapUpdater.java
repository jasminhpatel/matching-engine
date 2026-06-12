package com.solfini.reconciliation;

import static com.solfini.common.Constants.COLD_START;
import static com.solfini.common.Constants.COPY_TRADE_ONLY;
import static com.solfini.common.Constants.ERROR_LOG;
import static com.solfini.common.Constants.ETHEREUM;
import static com.solfini.common.Constants.MODE;
import static com.solfini.common.Constants.ONE_HOUR;
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
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.blockchain.BlockchainSenderFactory;
import com.solfini.util.blockchain.BlockchainTransactionSender;
import com.solfini.util.blockchain.gasstation.GasStationUtil;
import com.solfini.util.blockchain.model.BlockchainUser;
import com.solfini.util.blockchain.model.EngineUser;
import com.solfini.util.blockchain.model.GasFee;
import com.solfini.util.blockchain.model.UserRegistrationTransaction;
import com.solfini.util.blockchain.model.WithdrawableAmountUpdateTransaction;
import com.solfini.util.blockchain.util.BlockChainKeyManager;
import com.solfini.util.blockchain.util.RpcUtil;
import com.solfini.util.snapshot.SnapConverter;
import com.solfini.util.snapshot.SnapTransformer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
import org.web3j.protocol.Web3j;
import org.web3j.protocol.exceptions.ClientConnectionException;

public class FundManagerV2SnapUpdater {
  private static final Logger LOGGER = LogManager.getLogger(FundManagerV2SnapUpdater.class);
  private static final String MAINNET = "MAINNET";
  private static final String XDC = "XDC";

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

  public static boolean update(final String[] args, final StringBuilder sb,
      final List<Integer> selectedUsers,
      final String network, final String symbol) throws Exception {
    sb.append("Fund manager V2 (").append(network).append(" snap update started. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
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
    final long snapshotMappingId = BlockchainNotionalCache.getOrCreateIncrementalId(snapId, network.toUpperCase(), symbol.toUpperCase());
    final String latestSnapFile = snapDirectory + File.separator + latestSnapshotId;
    LOGGER.info("Fund Manager latestSnapFile: " + latestSnapFile);
    final Set<Integer> symbolsToIgnoreSet = new HashSet<>();
    final Set<Integer> symbolsToAllowSet = new HashSet<>();

    if (symbolsToIgnore != null && !symbolsToIgnore.isEmpty()) {
      String[] symbolsToIgnoreArr = symbolsToIgnore.split(",");
      for (String s : symbolsToIgnoreArr) {
        symbolsToIgnoreSet.add(StringUtil.toInt(s));
      }
    }
    if (MAINNET.equalsIgnoreCase(network)) { // allows to withdraw stable coins + profit except XUSDC, XUSDT
      symbolsToIgnoreSet.add(Context.getXusdcId());
      symbolsToIgnoreSet.add(Context.getXusdtId());
      // uncomment if per asset restriction is required.
      /* if (USDC.equalsIgnoreCase(symbol)) { // when processing USDC ignore USDT from withdrawable
        symbolsToIgnoreSet.add(Context.getUsdtId());
      } else if (USDT.equalsIgnoreCase(symbol)) { // when processing USDT ignore USDC from withdrawable
        symbolsToIgnoreSet.add(Context.getUsdcId());
      }*/
    } else if (XDC.equalsIgnoreCase(network)) { // allows to withdraw only XUSDC, XUSDT
      symbolsToAllowSet.add(Context.getXusdcId());
      symbolsToAllowSet.add(Context.getXusdtId());
    }
    // load from snap
    final Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> latestUserWithdrawables =
        loadFromSnap(latestSnapFile, args, symbolsToIgnoreSet, symbolsToAllowSet, network);
    sb.append("Latest snap file loaded to memory. file: ").append(latestSnapFile).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");

    // load previous from DB
    BlockchainNotionalCache.loadFromDB();
    final Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> prevUserWithdrawables = BlockchainNotionalCache.getUserWithdrawableMap((network + "V2").toUpperCase());
    sb.append("Previous snap status loaded from DB. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");

    final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> diff =
        getChangedNotional(prevUserWithdrawables, latestUserWithdrawables, diffPercentage, selectedUsers);

    sb.append("Difference calculated. #OfUpdates: ").append(diff.size()).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    LOGGER.info("Fund Manager #OfNotionalUpdates: " + diff.size());
    // wait until gas price goes down
    boolean useSecondary = false, hasProxyError = false;
    Web3j web3j = RpcUtil.createWeb3jConnection(network, null, useSecondary, hasProxyError);
    final long windowEnd = System.currentTimeMillis() + ONE_HOUR * 8; // 8 hours
    if (ETHEREUM.equalsIgnoreCase(network) || MAINNET.equalsIgnoreCase(network)) {
      while (true) {
        try {
          if (web3j == null) {
            web3j = RpcUtil.createWeb3jConnection(network, null, useSecondary, hasProxyError);
          }
          BigInteger maxFeePerGas = fetchCurrentMaxFeePerGas(web3j);
          if (maxFeePerGas.compareTo(Context.getEthereumMaxFeePerGas()) < 0) {
            break;
          }
          if (System.currentTimeMillis() > windowEnd) {
            LOGGER.info("Fund Manager, Unable to update the notional within specified time.");
            sb.append(
                    "Unable to update the notional within specified time. Gas price is too high. time: ")
                .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
            return false;
          }
          LOGGER.info("Fund Manager, Gas price is too high. Waiting for it to go down... gasPrice: "
              + maxFeePerGas + " threshold: " + Context.getEthereumMaxFeePerGas());
          sb.append("Gas price is too high. Waiting for it to go down... gasPrice: ")
              .append(maxFeePerGas).append(" threshold: ")
              .append(Context.getEthereumMaxFeePerGas()).append(" time: ")
              .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
          TimeUnit.MINUTES.sleep(1);
        } catch (ClientConnectionException e) {
          String message = e.getMessage().toLowerCase();
          if (message.contains("429") || message.contains("too many requests")) {
            sb.append("Failed Reason ").append("RPC error.");
            useSecondary = true;
          } else if (message.contains("502") || message.contains("bad gateway")) {
            sb.append("Failed Reason ").append("Proxy error.");
            hasProxyError = true;
          } else if (message.contains("503") || message.contains("service unavailable")) {
            sb.append("Failed Reason ").append("RPC error.");
            useSecondary = true;
          } else if (message.contains("504") || message.contains("gateway timeout")) {
            sb.append("Failed Reason ").append("RPC error.");
            sb.append("Failed Reason ").append("Proxy error.");
            hasProxyError = true;
            useSecondary = true;
          } else if (message.contains("407") || message.contains("proxy authentication")) {
            sb.append("Failed Reason ").append("Proxy error.");
            hasProxyError = true;
          } else {
            sb.append("Failed Reason ").append("RPC error.");
            sb.append("Failed Reason ").append("Proxy error.");
            hasProxyError = true;
            useSecondary = true;
          }
          web3j = null;
        } catch (IOException e) {
          hasProxyError = true;
          useSecondary = true;
          web3j = null;
        }
      }
    }

    // register missing users
    registerMissingUsers(diff, network, sb);

    final int noOfBatches = Math.max(1, (int) Math.ceil(diff.size() / (double) fundManagerBatchSize));
    final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> batchUserWithdrawables = new ArrayList<>();
    final String tokenAddress = Context.getTokenAddressBySymbol(symbol);
    int batchIndex = 1; // 1-indexed; contract requires batchIndex < totalBatches
    int recordCount = 0;
    boolean updateSent = false;
    boolean success = true;

    for (WithdrawableAmountUpdateTransaction.UserWithdrawable userWithdrawable : diff) {
      batchUserWithdrawables.add(userWithdrawable);
      recordCount++;
      if (recordCount >= fundManagerBatchSize) {
        updateSent = true;
        try {
          final WithdrawableAmountUpdateTransaction transaction = new WithdrawableAmountUpdateTransaction();
          transaction.setTokenAddress(tokenAddress);
          transaction.setNetwork(network.toUpperCase());
          transaction.setContractVersion(2);
          transaction.setId(snapshotMappingId);
          transaction.setChainType(network.toUpperCase());
          transaction.setContractAddress(Context.getFundManagerContractByNetworkAndVersion(network, 2));
          transaction.setUserWithdrawables(batchUserWithdrawables);
          transaction.setBatchId(batchIndex);
          transaction.setNoOfBatches(noOfBatches);
          final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
          if (sender != null) {
            boolean status = sender.processTransaction(sb);
            success = success && status;
            sb.append("Snap updated. batch ").append(batchIndex + 1).append(" of ").append(noOfBatches).append(" status: ").append(status).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
          } else {
            success = false;
            sb.append("Snap update failed. batch ").append(batchIndex + 1).append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
          }
        } catch (Exception e) {
          sb.append("Snap update failed. batch ").append(batchIndex + 1).append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss())
              .append(" ").append(e.getMessage()).append("\n");
          LOGGER.error(ERROR_LOG, e);
        }
        batchIndex++;
        recordCount = 0;
        batchUserWithdrawables.clear();
      }
    }

    if (recordCount > 0) {
      updateSent = true;
      try {
        WithdrawableAmountUpdateTransaction transaction = new WithdrawableAmountUpdateTransaction();
        transaction.setTokenAddress(tokenAddress);
        transaction.setNetwork(network.toUpperCase());
        transaction.setContractVersion(2);
        transaction.setId(snapshotMappingId);
        transaction.setChainType(network.toUpperCase());
        transaction.setContractAddress(Context.getFundManagerContractByNetworkAndVersion(network, 2));
        transaction.setUserWithdrawables(batchUserWithdrawables);
        transaction.setBatchId(batchIndex);
        transaction.setNoOfBatches(noOfBatches);
        final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
        if (sender != null) {
          boolean status = sender.processTransaction(sb);
          success = success && status;
          sb.append("Snap updated. batch ").append(batchIndex + 1).append(" of ").append(noOfBatches).append(" status: ").append(status).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        } else {
          success = false;
          sb.append("Snap update failed. batch ").append(batchIndex + 1).append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        }
      } catch (Exception e) {
        sb.append("Snap update failed. batch ").append(batchIndex + 1).append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss())
            .append(" ").append(e.getMessage()).append("\n");
        LOGGER.error(ERROR_LOG, e);
      }
    }

    if (!updateSent) {//send empty update
      try {
        WithdrawableAmountUpdateTransaction transaction = new WithdrawableAmountUpdateTransaction();
        transaction.setTokenAddress(tokenAddress);
        transaction.setNetwork(network.toUpperCase());
        transaction.setContractVersion(2);
        transaction.setId(snapshotMappingId);
        transaction.setChainType(network.toUpperCase());
        transaction.setContractAddress(Context.getFundManagerContractByNetworkAndVersion(network, 2));
        transaction.setUserWithdrawables(batchUserWithdrawables);
        transaction.setBatchId(1);
        transaction.setNoOfBatches(1);
        final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
        if (sender != null) {
          boolean status = sender.processTransaction(sb);
          success = success && status;
          sb.append("Snap updated. batch ").append(1).append(" of ").append(1).append(" status: ").append(status).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        } else {
          success = false;
          sb.append("Snap update failed. batch ").append(1).append(" of ").append(1).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        }
      } catch (Exception e) {
        sb.append("Snap update failed. batch ").append(1).append(" of ").append(1).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss())
            .append(" ").append(e.getMessage()).append("\n");
        LOGGER.error(ERROR_LOG, e);
      }
    }

    if (success) {
      long updated = System.currentTimeMillis();
      for (WithdrawableAmountUpdateTransaction.UserWithdrawable userWithdrawable : diff) {
        BlockchainNotionalCache.upsert(userWithdrawable, updated, snapId);
      }
      BlockchainNotionalCache.confirmSnapMapping(snapId, network.toUpperCase(), symbol.toUpperCase());
      sb.append("Snap update successful. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    } else {
      sb.append("Snap update failed. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    }

    return success;
  }

  private static void registerMissingUsers(final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> balanceChangedUser,
      final String network, final StringBuilder sb) {
    BlockchainUserCache.loadFromDb();
    for(final WithdrawableAmountUpdateTransaction.UserWithdrawable balanceUpdate : balanceChangedUser) {
      final BlockchainUser user = BlockchainUserCache.getBlockchainUser(balanceUpdate.getUserId(), network);
      if (user == null) {
        try {
          final EngineUser engineUser = BlockchainUserCache.getEngineUser(balanceUpdate.getUserId());
          if (engineUser == null) {
            sb.append("User registration failed. user: ").append(balanceUpdate.getUserId()).append(". User not found ").append("\n");
            continue;
          }
          if (!engineUser.getAddress().startsWith("0x")) {
            sb.append("User registration failed. user: ").append(balanceUpdate.getUserId())
                .append(". Invalid address: ").append(engineUser.getAddress()).append("\n");
            continue;
          }

          final String userAddress = engineUser.getAddress();

          final UserRegistrationTransaction userRegistrationTransaction = new UserRegistrationTransaction();
          userRegistrationTransaction.setId(System.currentTimeMillis());
          userRegistrationTransaction.setNewUserId(balanceUpdate.getUserId());
          userRegistrationTransaction.setNewUserAddress(userAddress);
          userRegistrationTransaction.setChainType(network);
          userRegistrationTransaction.setContractAddress(Context.getFundManagerContractByNetworkAndVersion(network, 2));
          final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(userRegistrationTransaction);
          if (sender != null) {
            boolean status = sender.processTransaction(sb);
            sb.append("Register user: ").append(balanceUpdate.getUserId()).append(" address ").append(userAddress).append("\n");
          } else {
            sb.append("User registration failed. user: ").append(balanceUpdate.getUserId()).append(" address ").append(userAddress).append("\n");
          }
        } catch (Exception e) {
          sb.append("User registration failed. user: ").append(balanceUpdate.getUserId()).append(" reason: ").append(e.getMessage()).append("\n");
          LOGGER.error(ERROR_LOG, e);
        }
      }
    }
  }

  private static Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> loadFromSnap(final String snapFile, final String[] args,
      final Set<Integer> symbolsToIgnoreSet, final Set<Integer> symbolsToAllowSet, final String network) {
    final List<Message> snapUserPositions = loadSnap(args, snapFile);
    final HashMap<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> userWithdrawables = new HashMap<>();
    final String contractKey = (network + "V2").toUpperCase();

    for (Message message : snapUserPositions) {
      if (message instanceof BalanceAdminMessage balanceAdminMessage) {
        double withdrawable = 0;
        for (Position p : balanceAdminMessage.getPositionArr()) {
          if (p != null) {
            if (MAINNET.equalsIgnoreCase(network)) {
              if (!symbolsToIgnoreSet.contains(p.getInstrumentId())) {
                if (p.getQuantity() != 0) {
                  withdrawable = withdrawable + p.getUsdValue() /*+ p.getUsdUnrealized()*/;
                  System.out.println(
                      "InstrumentId: " + p.getInstrumentId() + " USD value " + p.getUsdValue()
                          + " Unrealized " + p.getUsdUnrealized() + " withdrawable: "
                          + withdrawable);
                }
              }
            } else if (XDC.equalsIgnoreCase(network)) {
              if (symbolsToAllowSet.contains(p.getInstrumentId())) {
                if (p.getQuantity() != 0) {
                  withdrawable = withdrawable + p.getUsdValue() /*+ p.getUsdUnrealized()*/;
                  System.out.println(
                      "InstrumentId: " + p.getInstrumentId() + " USD value " + p.getUsdValue()
                          + " Unrealized " + p.getUsdUnrealized() + " withdrawable: "
                          + withdrawable);
                }
              }
            }
          }
        }
        System.out.println(balanceAdminMessage.getUserId() + " - " + withdrawable + " - " + (long) (withdrawable * 1_000_000));
        final WithdrawableAmountUpdateTransaction.UserWithdrawable userWithdrawable =
            new WithdrawableAmountUpdateTransaction.UserWithdrawable(balanceAdminMessage.getUserId(), contractKey, (long) (withdrawable * 1_000_000));
        userWithdrawables.put(balanceAdminMessage.getUserId(), userWithdrawable);
      }
    }
    return userWithdrawables;
  }

  private static List<WithdrawableAmountUpdateTransaction.UserWithdrawable> getChangedNotional(
      final Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> prevUserWithdrawables,
      final Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> latestUserWithdrawables,
      final double changeTolerance, final List<Integer> selectedUsers) {

    final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> list = new ArrayList<>();
    for (Map.Entry<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> entry : latestUserWithdrawables.entrySet()) {

      WithdrawableAmountUpdateTransaction.UserWithdrawable prev = prevUserWithdrawables.get(entry.getKey());
      WithdrawableAmountUpdateTransaction.UserWithdrawable current = entry.getValue();
      if (selectedUsers != null && !selectedUsers.contains(current.getUserId())) {
        continue;
      }
      long prevQuantity = prev != null ? prev.getQuantity() : 0;
      long newQuantity = current != null ? current.getQuantity() : 0;

      if (prevQuantity != 0) {
        double change = Math.abs((newQuantity - prevQuantity) / (double) prevQuantity);
        if (change > changeTolerance) { // more than 5%
          list.add(current);
          System.out.println("UserId: " + current.getUserId() + " percentage: " + change + " prevQuantity: " + prevQuantity + " newQuantity: " + newQuantity);
        }
      } else if (newQuantity != 0) {
        list.add(current);
        System.out.println("UserId: " + current.getUserId() + " prevQuantity: " + prevQuantity + " newQuantity: " + newQuantity);
      }
    }
    return list;
  }

  private static BigInteger fetchCurrentMaxFeePerGas(final Web3j web3j) throws Exception {
    final GasFee gasFee = GasStationUtil.fetchGasFees(web3j);
    if (gasFee != null) {
      return gasFee.getMaxFeePerGas();
    }
    return new BigInteger("-1");
  }
}

