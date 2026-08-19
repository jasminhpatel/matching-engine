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
import static com.solfini.common.Constants.USDC;
import static com.solfini.common.Constants.USDT;
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
import java.util.Collections;
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

  // Gas-price gate only makes sense against real Ethereum mainnet economics. Gated on a positive
  // "TEST"-prefixed ENVIRONMENT rather than "not PRODUCTION" so a misconfigured/blank ENVIRONMENT
  // fails safe into keeping the wait, not skipping it.
  private static boolean shouldWaitForLowGas(final String network) {
    final boolean isMainnet = ETHEREUM.equalsIgnoreCase(network) || MAINNET.equalsIgnoreCase(network);
    final String environment = Context.getEnvironment();
    final boolean isTestEnvironment = environment != null && environment.toUpperCase().startsWith("TEST");
    return isMainnet && !isTestEnvironment;
  }
  private static List<Message> cachedSnapMessages = null;
  private static String cachedSnapFile = null;

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
    options.addOption(Option.builder("j").longOpt("json").desc("json export path (used by reconcile, ignored here)").hasArg().required(false).build());

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
    options.addOption(Option.builder("s").longOpt("snapshot").desc("snapshot directory").hasArg().argName("dir").required(false).build());
    options.addOption(Option.builder("j").longOpt("json").desc("json file path").hasArg().argName("file").required(false).build());
    options.addOption(
        Option.builder("m").longOpt("mode").desc("mode of operation (export|import)").hasArg().argName("mode").required(false).build());
    options.addOption(Option.builder().longOpt("debug").desc("enable debug logging").required(false).build());
    options.addOption(Option.builder().longOpt("prune").desc("enable snapshot pruning").required(false).build());
    options.addOption(Option.builder().longOpt("clean").desc("enable snapshot cleaning").required(false).build());
    options.addOption(Option.builder().longOpt("validate").desc("enable snapshot validation").required(false).build());
    options.addOption(Option.builder("c").longOpt("config").desc("configuration file path").hasArg().argName("file").build());
    options.addOption(Option.builder().longOpt("data-port").desc("data port configuration file path").hasArg().argName("file").build());
    options.addOption(Option.builder("").longOpt("transform").desc("apply transformation").hasArgs().argName("name").build());
    options.addOption(Option.builder("d").desc("define configuration property").hasArgs().argName("key>=<value").required(false).build());

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
      final String json = cmd.hasOption("j") ? cmd.getOptionValue("j")
          : System.getProperty("java.io.tmpdir") + "/fund_manager_snap_export.json";
      final String mode = cmd.hasOption("m") ? cmd.getOptionValue("m").toLowerCase() : "export";

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
          throw new RuntimeException("Specified directory " + snapshot + " does not contain a valid snapshot");
        }

/*        final File jsonFile = new File(json);
        if (jsonFile.exists() && !jsonFile.isFile()) {
          System.out.println("ERROR: Specified output file " + json + " already exists and is not a regular file");
          throw new RuntimeException("Specified output file " + json + " already exists and is not a regular file");
        }*/

        List<Message> messages = snapConverter.exportSnapshot(snapshot, json);
        if (cmd.hasOption("validate") && !snapConverter.validate()) {
          throw new RuntimeException("Snapshot validation failed: " + snapshot);
        }
        return messages;
      } else if (mode.equals("import")) {
        final File snapshotPath = new File(snapshot);
        if (!snapshotPath.exists() || !snapshotPath.isDirectory()) {
          throw new RuntimeException("Specified directory " + snapshot + " does not exist");
        }

        final File jsonFile = new File(json);
        if (!jsonFile.exists() || !jsonFile.isFile()) {
          throw new RuntimeException("Specified input file " + json + " does not exist or is not a regular file");
        }

        snapConverter.importSnapshot(json, snapshot);
      } else {
        System.out.println("ERROR: Invalid mode specified - " + mode);
      }
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }

    return Collections.emptyList();
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

  /**
   * Sends a single non-final heartbeat batch (totalBatches=2, batchIndex=1, 0 users) for the
   * given snapId. Used by catchup jobs to advance the on-chain snapshotId counter one step at a
   * time without triggering the 22-hour cadence check (isLastBatch=false when batchIndex < totalBatches).
   */
  public static boolean sendHeartbeatBatch(final String[] args, final StringBuilder sb,
      final String network, final String symbol, final long snapId) {
    try {
      if (!initialize(args)) {
        sb.append("Heartbeat failed: initialization failed snap=").append(snapId).append("\n");
        return false;
      }
      loadBlockchainKeyFile();

      final String tokenAddress = Context.getTokenAddressBySymbol(symbol);
      sb.append("Heartbeat snap=").append(snapId).append(" network=").append(network)
          .append(" symbol=").append(symbol).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");

      final WithdrawableAmountUpdateTransaction transaction = new WithdrawableAmountUpdateTransaction();
      transaction.setTokenAddress(tokenAddress);
      transaction.setNetwork(network.toUpperCase());
      transaction.setContractVersion(2);
      transaction.setId(snapId);
      transaction.setChainType(network.toUpperCase());
      transaction.setContractAddress(Context.getFundManagerContractByNetworkAndVersion(network, 2));
      transaction.setUserWithdrawables(new ArrayList<>());
      transaction.setBatchId(1);
      transaction.setNoOfBatches(2);

      final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
      if (sender != null) {
        final boolean status = sender.processTransaction(sb);
        sb.append("Heartbeat snap=").append(snapId).append(" status: ").append(status)
            .append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        return status;
      } else {
        sb.append("Heartbeat failed: no sender for ").append(network).append("\n");
        return false;
      }
    } catch (final Exception e) {
      sb.append("Heartbeat failed snap=").append(snapId).append(": ").append(e.getMessage()).append("\n");
      LOGGER.error(ERROR_LOG, e);
      return false;
    }
  }

  /**
   * Resolves the snap folder and mapping id once before any chain sync starts. All 3 chains
   * in a FundManagerV2SyncJob run receive the same SnapContext so they share one snapshot
   * folder and one on-chain snapId. Also honours FORCE_SNAP_ID.
   */
  public static SnapContext resolveSnapContext(final String[] args) throws Exception {
    initialize(args);
    final Properties overlay = new Properties();
    loadConfigurationFile(overlay);

    final String snapDirectory = PropertyReader.getProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", "");
    final List<String> snapshotIds = FolderUtils.getLastNTimestampFolders(snapDirectory, 1);
    if (snapshotIds.isEmpty()) {
      throw new IllegalStateException("No completed snapshot folder found under: " + snapDirectory);
    }
    final String resolvedSnapshotId = snapshotIds.getFirst();
    final long snapId = StringUtil.toLong(resolvedSnapshotId);

    final String forcedSnapId = PropertyReader.getProperty("FORCE_SNAP_ID", "");
    final long mappingId = forcedSnapId.isEmpty()
        ? BlockchainNotionalCache.getOrCreateIncrementalId(snapId, "", "")
        : Long.parseLong(forcedSnapId);

    if (mappingId < 0) {
      throw new IllegalStateException("Failed to get or create incremental id for snapshotId: " + snapId);
    }

    final String snapFolder = snapDirectory + File.separator + resolvedSnapshotId;
    LOGGER.info("Resolved shared snap context — folder: " + snapFolder + "  snapId: " + snapId + "  mappingId: " + mappingId);
    return new SnapContext(snapFolder, snapId, mappingId);
  }

  /** Backward-compatible entry point for catchup jobs and manual single-chain runs. */
  public static boolean update(final String[] args, final StringBuilder sb,
      final List<Integer> selectedUsers,
      final String network, final String symbol) throws Exception {
    return update(args, sb, selectedUsers, network, symbol, resolveSnapContext(args));
  }

  /**
   * Context-aware entry point used by FundManagerV2SyncJob. All 3 chains pass the SAME
   * SnapContext so they share the same completed snapshot folder and the same mapping id.
   */
  public static boolean update(final String[] args, final StringBuilder sb,
      final List<Integer> selectedUsers,
      final String network, final String symbol,
      final SnapContext snapContext) throws Exception {
    sb.append("Fund manager V2 (").append(network).append(" snap update started. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    initialize(args);
    final Properties overlay = new Properties();
    loadConfigurationFile(overlay);

    final String symbolsToIgnore = PropertyReader.getProperty("IGNORE_SYMBOL_LIST", "");
    final int fundManagerBatchSize = PropertyReader.getProperty("FUND_MANAGER_BATCH_SIZE", 500);
    final double diffPercentage = PropertyReader.getProperty("ETHEREUM_NOTIONAL_DIFF_PERCENTAGE", 0.05);
    loadBlockchainKeyFile();

    final long snapId = snapContext.snapId();
    final long snapshotMappingId = snapContext.mappingId();
    final String latestSnapFile = snapContext.snapFolder();
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
      // per-asset restriction: each stablecoin's withdrawable must reflect ONLY its own positions,
      // otherwise USDC and USDT syncs compute the same combined total and, since the notional cache
      // key was not asset-specific, whichever ran second always saw a zero diff against what the
      // first one just persisted.
      if (USDC.equalsIgnoreCase(symbol)) { // when processing USDC ignore USDT from withdrawable
        symbolsToIgnoreSet.add(Context.getUsdtId());
      } else if (USDT.equalsIgnoreCase(symbol)) { // when processing USDT ignore USDC from withdrawable
        symbolsToIgnoreSet.add(Context.getUsdcId());
      }
    } else if (XDC.equalsIgnoreCase(network)) { // allows to withdraw only XUSDC, XUSDT
      symbolsToAllowSet.add(Context.getXusdcId());
      symbolsToAllowSet.add(Context.getXusdtId());
    }
    final String contractKey = (network + symbol + "V2").toUpperCase();
    // load from snap
    final Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> latestUserWithdrawables =
        loadFromSnap(latestSnapFile, args, symbolsToIgnoreSet, symbolsToAllowSet, network, contractKey);
    sb.append("Latest snap file loaded to memory. file: ").append(latestSnapFile).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");

    // load previous from DB
    BlockchainNotionalCache.loadFromDB();
    final Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> prevUserWithdrawables = BlockchainNotionalCache.getUserWithdrawableMap(contractKey);
    sb.append("Previous snap status loaded from DB. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");

    final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> diff =
        getChangedNotional(prevUserWithdrawables, latestUserWithdrawables, diffPercentage, selectedUsers);

    sb.append("Difference calculated. #OfUpdates: ").append(diff.size()).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    LOGGER.info("Fund Manager #OfNotionalUpdates: " + diff.size());
    // wait until gas price goes down
    boolean useSecondary = false, hasProxyError = false;
    Web3j web3j = RpcUtil.createWeb3jConnection(network, null, useSecondary, hasProxyError);
    final long windowEnd = System.currentTimeMillis() + ONE_HOUR * 8; // 8 hours
    if (shouldWaitForLowGas(network)) {
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
      final boolean confirmed = BlockchainNotionalCache.confirmSnapMapping(snapId, network.toUpperCase(), symbol.toUpperCase());
      if (!confirmed) {
        LOGGER.error("Fund Manager, snap mapping confirmation failed for " + network + "/" + symbol
            + ". snapId: " + snapId + ". On-chain update succeeded but DB row was not confirmed."
            + " Manual fix: INSERT INTO blockchain_snap_mapping (snapshot_id) VALUES (" + snapId + ")"
            + " ON CONFLICT (snapshot_id) DO NOTHING;");
        sb.append("CRITICAL: On-chain snap update succeeded but DB confirmation failed."
            + " Manual DB fix required. network: ").append(network)
            .append(" symbol: ").append(symbol)
            .append(" snapId: ").append(snapId)
            .append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        return false;
      }
      sb.append("Snap update successful. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    } else {
      sb.append("Snap update failed. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    }

    return success;
  }

  /**
   * FundingContractV6 composite-aware entry point, called from FundManagerV2SyncJob.main() in
   * place of the old hardcoded-(network,symbol) sync() loop. Mirrors update()'s shape (gas-price
   * wait, missing-user registration, batching, DB confirmation — all reused unchanged via the
   * private helpers below) but is keyed by compositeId + its live member-token list instead of
   * (network, symbol), and sends contractVersion 3 (batchSetCompositeBalances) instead of 2
   * (batchSetAvailableAssetBalances). update() itself is left as-is, not deleted — this is a full
   * cutover operationally (V5 stops being scheduled at all), but update()/the V2 ABI path is the
   * natural toolkit for the one-off V5 fund-sweep script planned for later, so it stays as library
   * code even though nothing calls it on a schedule anymore.
   */
  public static boolean updateComposite(final String[] args, final StringBuilder sb,
      final List<Integer> selectedUsers,
      final String network, final String coreAddress, final String compositeId, final List<String> memberTokenAddresses,
      final SnapContext snapContext) throws Exception {
    sb.append("Fund manager V6 composite update started. network: ").append(network).append(" compositeId: ").append(compositeId)
        .append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    initialize(args);
    final Properties overlay = new Properties();
    loadConfigurationFile(overlay);

    final String symbolsToIgnore = PropertyReader.getProperty("IGNORE_SYMBOL_LIST", "");
    final int fundManagerBatchSize = PropertyReader.getProperty("FUND_MANAGER_BATCH_SIZE", 500);
    final double diffPercentage = PropertyReader.getProperty("ETHEREUM_NOTIONAL_DIFF_PERCENTAGE", 0.05);
    loadBlockchainKeyFile();

    final long snapId = snapContext.snapId();
    final long snapshotMappingId = snapContext.mappingId();
    final String latestSnapFile = snapContext.snapFolder();
    LOGGER.info("Fund Manager V6 latestSnapFile: " + latestSnapFile);

    final Set<Integer> memberInstrumentIds = new HashSet<>();
    for (final String tokenAddress : memberTokenAddresses) {
      memberInstrumentIds.add(FundingContractV6Loader.resolveInstrumentId(tokenAddress));
    }

    // DB/cache-only key (network-prefixed) - NOT the same as the raw `compositeId` used for the
    // actual on-chain batchSetCompositeBalances call below. compositeId = keccak256("COMPOSITE",
    // nonce) carries no chain/address salt, so two independently deployed contracts' first
    // composite land on the identical bytes32 value (confirmed empirically against the fresh
    // 2026-07-28 Sepolia/Apothem certification deploys) - without this network prefix, their
    // blockchain_notional_state rows would collide on (userId, contractKey). Same convention
    // PositionManagerSnapUpdater already uses for its own contractKey (see notionalContractKey).
    final String contractKey = network.toUpperCase() + ":" + compositeId;

    // Profit-inclusion is a per-COMPOSITE property, read directly off-chain (Composite.profitInclusive)
    // — no longer a network-string proxy. Two different inclusion shapes, not one:
    final Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> latestUserWithdrawables;
    if (!FundingContractV6Loader.getCompositeProfitInclusive(network, coreAddress, compositeId)) {
      // ALLOW-set = exactly this composite's own member instrument id(s) — nothing else counts,
      // ever, regardless of IGNORE_SYMBOL_LIST. Generalizes the original XDC branch's hardcoded
      // {XUSDC, XUSDT} allow-set correctly for a Segregated (single-token) composite: only ITS
      // own token, not every XDC stablecoin. Any trading/profit-bearing instrument, VToken,
      // staking, or market-maker position is excluded by construction (it's simply not in this
      // set), independent of IGNORE_SYMBOL_LIST.
      latestUserWithdrawables = loadFromSnapComposite(latestSnapFile, args, memberInstrumentIds, true, contractKey);
    } else {
      // IGNORE-set (V1 one-pass shape, docs §9): ignore every known stablecoin instrument that is
      // NOT a member of this composite, plus whatever IGNORE_SYMBOL_LIST configures (that's where
      // VToken/staking/market-maker instrument ids need to live for this branch — same mechanism
      // the original loadFromSnap always relied on, unchanged here). Everything else — this
      // composite's own members, plus any non-stablecoin trading instrument, i.e. profit/loss —
      // is summed in, netting P&L exactly once.
      final Set<Integer> ignoreInstrumentIds = new HashSet<>();
      if (symbolsToIgnore != null && !symbolsToIgnore.isEmpty()) {
        for (final String s : symbolsToIgnore.split(",")) {
          ignoreInstrumentIds.add(StringUtil.toInt(s));
        }
      }
      final Set<Integer> allKnownStableInstrumentIds = Set.of(
          Context.getUsdcId(), Context.getUsdtId(), Context.getXusdcId(), Context.getXusdtId());
      for (final int stableInstrumentId : allKnownStableInstrumentIds) {
        if (!memberInstrumentIds.contains(stableInstrumentId)) {
          ignoreInstrumentIds.add(stableInstrumentId);
        }
      }
      latestUserWithdrawables = loadFromSnapComposite(latestSnapFile, args, ignoreInstrumentIds, false, contractKey);
    }
    sb.append("Latest snap file loaded to memory. file: ").append(latestSnapFile).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");

    // load previous from DB — contractKey is network-prefixed (see comment above); the DB column
    // was widened (varchar(32) -> varchar(80)) since this is wider than the old short V1/V2 labels.
    BlockchainNotionalCache.loadFromDB();
    final Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> prevUserWithdrawables = BlockchainNotionalCache.getUserWithdrawableMap(contractKey);
    sb.append("Previous snap status loaded from DB. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");

    final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> diff =
        getChangedNotional(prevUserWithdrawables, latestUserWithdrawables, diffPercentage, selectedUsers);

    sb.append("Difference calculated. #OfUpdates: ").append(diff.size()).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    LOGGER.info("Fund Manager V6 #OfNotionalUpdates: " + diff.size());

    // wait until gas price goes down — identical policy to update(), reused verbatim.
    boolean useSecondary = false, hasProxyError = false;
    Web3j web3j = RpcUtil.createWeb3jConnection(network, null, useSecondary, hasProxyError);
    final long windowEnd = System.currentTimeMillis() + ONE_HOUR * 8;
    if (shouldWaitForLowGas(network)) {
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
            LOGGER.info("Fund Manager V6, Unable to update the notional within specified time.");
            sb.append("Unable to update the notional within specified time. Gas price is too high. time: ")
                .append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
            return false;
          }
          TimeUnit.MINUTES.sleep(1);
        } catch (ClientConnectionException e) {
          String message = e.getMessage().toLowerCase();
          if (message.contains("429") || message.contains("too many requests")) {
            useSecondary = true;
          } else if (message.contains("502") || message.contains("bad gateway")) {
            hasProxyError = true;
          } else if (message.contains("503") || message.contains("service unavailable")) {
            useSecondary = true;
          } else if (message.contains("504") || message.contains("gateway timeout")) {
            hasProxyError = true;
            useSecondary = true;
          } else if (message.contains("407") || message.contains("proxy authentication")) {
            hasProxyError = true;
          } else {
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

    registerMissingUsers(diff, network, sb);

    final int noOfBatches = Math.max(1, (int) Math.ceil(diff.size() / (double) fundManagerBatchSize));
    final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> batchUserWithdrawables = new ArrayList<>();
    int batchIndex = 1;
    int recordCount = 0;
    boolean updateSent = false;
    boolean success = true;

    for (final WithdrawableAmountUpdateTransaction.UserWithdrawable userWithdrawable : diff) {
      batchUserWithdrawables.add(userWithdrawable);
      recordCount++;
      if (recordCount >= fundManagerBatchSize) {
        updateSent = true;
        success = sendCompositeBatch(network, coreAddress, compositeId, snapshotMappingId, batchIndex, noOfBatches, batchUserWithdrawables, sb) && success;
        batchIndex++;
        recordCount = 0;
        batchUserWithdrawables.clear();
      }
    }

    if (recordCount > 0) {
      updateSent = true;
      success = sendCompositeBatch(network, coreAddress, compositeId, snapshotMappingId, batchIndex, noOfBatches, batchUserWithdrawables, sb) && success;
    }

    if (!updateSent) {
      success = sendCompositeBatch(network, coreAddress, compositeId, snapshotMappingId, 1, 1, batchUserWithdrawables, sb) && success;
    }

    if (success) {
      long updated = System.currentTimeMillis();
      for (final WithdrawableAmountUpdateTransaction.UserWithdrawable userWithdrawable : diff) {
        BlockchainNotionalCache.upsert(userWithdrawable, updated, snapId);
      }
      final boolean confirmed = BlockchainNotionalCache.confirmSnapMapping(snapId, network.toUpperCase(), compositeId);
      if (!confirmed) {
        LOGGER.error("Fund Manager V6, snap mapping confirmation failed for compositeId " + compositeId
            + ". snapId: " + snapId + ". On-chain update succeeded but DB row was not confirmed."
            + " Manual fix: INSERT INTO blockchain_snap_mapping (snapshot_id) VALUES (" + snapId + ")"
            + " ON CONFLICT (snapshot_id) DO NOTHING;");
        sb.append("CRITICAL: On-chain snap update succeeded but DB confirmation failed."
            + " Manual DB fix required. compositeId: ").append(compositeId)
            .append(" snapId: ").append(snapId)
            .append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        return false;
      }
      sb.append("Snap update successful. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    } else {
      sb.append("Snap update failed. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    }

    return success;
  }

  private static boolean sendCompositeBatch(final String network, final String coreAddress, final String compositeId, final long snapshotMappingId,
      final int batchIndex, final int noOfBatches,
      final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> batchUserWithdrawables, final StringBuilder sb) {
    try {
      final WithdrawableAmountUpdateTransaction transaction = new WithdrawableAmountUpdateTransaction();
      transaction.setCompositeId(compositeId);
      transaction.setNetwork(network.toUpperCase());
      transaction.setContractVersion(3);
      transaction.setId(snapshotMappingId);
      transaction.setChainType(network.toUpperCase());
      transaction.setContractAddress(coreAddress);
      transaction.setUserWithdrawables(batchUserWithdrawables);
      transaction.setBatchId(batchIndex);
      transaction.setNoOfBatches(noOfBatches);
      final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
      if (sender != null) {
        boolean status = sender.processTransaction(sb);
        sb.append("Composite snap updated. batch ").append(batchIndex).append(" of ").append(noOfBatches).append(" status: ").append(status).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        return status;
      }
      sb.append("Composite snap update failed (no sender). batch ").append(batchIndex).append(" of ").append(noOfBatches).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
      return false;
    } catch (Exception e) {
      sb.append("Composite snap update failed. batch ").append(batchIndex).append(" of ").append(noOfBatches).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss())
          .append(" ").append(e.getMessage()).append("\n");
      LOGGER.error(ERROR_LOG, e);
      return false;
    }
  }

  /**
   * Composite-aware analog of loadFromSnap. instrumentIdSet is either an ALLOW-set (isAllowList
   * true — only these instrument ids count, everything else is excluded no matter what) or an
   * IGNORE-set (isAllowList false — everything counts except these, i.e. the V1 one-pass shape,
   * docs §9). See updateComposite for which composite gets which mode and why. contractKey here is
   * the network-prefixed DB/cache key ("<NETWORK>:<compositeId>"), not the raw compositeId used
   * for the actual on-chain call - see updateComposite's comment for why the prefix is needed.
   */
  private static Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> loadFromSnapComposite(
      final String snapFile, final String[] args, final Set<Integer> instrumentIdSet, final boolean isAllowList, final String contractKey) {
    if (cachedSnapMessages == null || !snapFile.equals(cachedSnapFile)) {
      cachedSnapMessages = loadSnap(args, snapFile);
      cachedSnapFile = snapFile;
    }
    final List<Message> snapUserPositions = cachedSnapMessages;
    final HashMap<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> userWithdrawables = new HashMap<>();

    for (final Message message : snapUserPositions) {
      if (message instanceof BalanceAdminMessage balanceAdminMessage) {
        double withdrawable = 0;
        for (final Position p : balanceAdminMessage.getPositionArr()) {
          final boolean included = isAllowList
              ? p != null && instrumentIdSet.contains(p.getInstrumentId())
              : p != null && !instrumentIdSet.contains(p.getInstrumentId());
          if (included && p.getQuantity() != 0) {
            withdrawable = withdrawable + p.getUsdValue() /*+ p.getUsdUnrealized()*/;
          }
        }
        // Same unsigned-floor guard as loadFromSnap — see that method's comment.
        withdrawable = Math.max(0, withdrawable);
        final WithdrawableAmountUpdateTransaction.UserWithdrawable userWithdrawable =
            new WithdrawableAmountUpdateTransaction.UserWithdrawable(balanceAdminMessage.getUserId(), contractKey, (long) (withdrawable * 1_000_000));
        userWithdrawables.put(balanceAdminMessage.getUserId(), userWithdrawable);
      }
    }
    return userWithdrawables;
  }

  private static void registerMissingUsers(final List<WithdrawableAmountUpdateTransaction.UserWithdrawable> balanceChangedUser,
      final String network, final StringBuilder sb) {
    BlockchainUserCache.loadFromDb();
    for(final WithdrawableAmountUpdateTransaction.UserWithdrawable balanceUpdate : balanceChangedUser) {
      final BlockchainUser user = BlockchainUserCache.getBlockchainUser(balanceUpdate.getUserId(), network, BlockchainTransactionSender.FUND_MANAGEMENT);
      if (user == null) {
        try {
          final EngineUser engineUser = BlockchainUserCache.getEngineUser(balanceUpdate.getUserId());
          if (engineUser == null) {
            sb.append("User registration failed. user: ").append(balanceUpdate.getUserId()).append(". User not found ").append("\n");
            LOGGER.error("Fund Manager, user registration failed. userId: " + balanceUpdate.getUserId() + " not found in engine users. network: " + network);
            continue;
          }
          if (!engineUser.getAddress().startsWith("0x")) {
            sb.append("User registration failed. user: ").append(balanceUpdate.getUserId())
                .append(". Invalid address: ").append(engineUser.getAddress()).append("\n");
            LOGGER.error("Fund Manager, user registration failed. userId: " + balanceUpdate.getUserId() + " invalid address: " + engineUser.getAddress() + " network: " + network);
            continue;
          }

          final String userAddress = engineUser.getAddress();

          final UserRegistrationTransaction userRegistrationTransaction = new UserRegistrationTransaction();
          userRegistrationTransaction.setId(System.currentTimeMillis());
          userRegistrationTransaction.setNewUserId(balanceUpdate.getUserId());
          userRegistrationTransaction.setNewUserAddress(userAddress);
          userRegistrationTransaction.setChainType(network);
          userRegistrationTransaction.setContractAddress(Context.getFundManagerContractByNetworkAndVersion(network, 2));
          userRegistrationTransaction.setManagerType(BlockchainTransactionSender.FUND_MANAGEMENT);
          final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(userRegistrationTransaction);
          if (sender != null) {
            boolean status = sender.processTransaction(sb);
            if (status) {
              BlockchainUser blockchainUser = new BlockchainUser(engineUser.getUserId(), engineUser.getAddress(), System.currentTimeMillis(), network, BlockchainTransactionSender.FUND_MANAGEMENT);
              BlockchainUserCache.addUser(blockchainUser);
              sb.append("User registered successfully. user: ").append(balanceUpdate.getUserId()).append(" address ").append(userAddress).append(" network: ").append(network).append("\n");
              LOGGER.info("Fund Manager, user registered. userId: " + balanceUpdate.getUserId() + " address: " + userAddress + " network: " + network);
            } else {
              sb.append("User registration failed. user: ").append(balanceUpdate.getUserId()).append(" address ").append(userAddress)
                  .append(" network: ").append(network).append(" reason: ").append(userRegistrationTransaction.getError()).append("\n");
              LOGGER.error("Fund Manager, user registration failed. userId: " + balanceUpdate.getUserId() + " address: " + userAddress
                  + " network: " + network + " reason: " + userRegistrationTransaction.getError());
            }
          } else {
            sb.append("User registration failed. No sender available. user: ").append(balanceUpdate.getUserId()).append(" address ").append(userAddress)
                .append(" network: ").append(network).append("\n");
            LOGGER.error("Fund Manager, user registration failed. No sender available. userId: " + balanceUpdate.getUserId()
                + " address: " + userAddress + " network: " + network);
          }
        } catch (Exception e) {
          sb.append("User registration failed. user: ").append(balanceUpdate.getUserId()).append(" reason: ").append(e.getMessage()).append("\n");
          LOGGER.error(ERROR_LOG, e);
        }
      }
    }
  }

  private static Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> loadFromSnap(final String snapFile, final String[] args,
      final Set<Integer> symbolsToIgnoreSet, final Set<Integer> symbolsToAllowSet, final String network, final String contractKey) {
    if (cachedSnapMessages == null || !snapFile.equals(cachedSnapFile)) {
      cachedSnapMessages = loadSnap(args, snapFile);
      cachedSnapFile = snapFile;
    }
    final List<Message> snapUserPositions = cachedSnapMessages;
    final HashMap<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> userWithdrawables = new HashMap<>();

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
        // A user's isolated per-asset total can go negative (e.g. unrealized losses on other
        // instruments exceeding this asset's balance) now that USDC/USDT are computed separately.
        // The on-chain balance field is unsigned, so a negative value would encode as a huge
        // uint64 and trip the contract's BalanceTooLarge check, reverting the whole batch.
        // Nothing withdrawable in that asset is correctly represented as 0.
        withdrawable = Math.max(0, withdrawable);
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

