package com.solfini.reconciliation;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.instrument.Position;

import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.user.UserCache;
import com.solfini.util.*;
import com.solfini.util.blockchain.BlockchainSenderFactory;
import com.solfini.util.blockchain.BlockchainTransactionSender;
import com.solfini.util.blockchain.model.BlockchainUser;
import com.solfini.util.blockchain.model.EngineUser;
import com.solfini.util.blockchain.model.PositionUpdateTransaction;
import com.solfini.util.blockchain.model.PositionUpdateTransaction.BlockchainPosition;
import com.solfini.util.blockchain.model.UserRegistrationTransaction;
import com.solfini.util.blockchain.model.WithdrawableAmountUpdateTransaction;
import com.solfini.util.blockchain.util.BlockChainKeyManager;
import com.solfini.util.snapshot.SnapConverter;
import com.solfini.util.snapshot.SnapTransformer;
import org.apache.commons.cli.*;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

import static com.solfini.common.Constants.*;

public class PositionManagerSnapUpdater {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PositionManagerSnapUpdater.class);

  public static boolean update(String[] args, final StringBuilder sb, final StringBuilder selfHealedSummary) throws Exception {
    sb.append("Position manager (Polygon) snap update started. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    initialize(args);
    final Properties overlay = new Properties();
    loadConfigurationFile(overlay);
    loadBlockchainKeyFile();

    final String snapDirectory = PropertyReader.getProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", "");
    // Only the newest folder is ever loaded/parsed. The second-newest folder's id is used purely as a
    // cheap, cadence-agnostic "what should the last synced snapshot have been" reference for the
    // staleness check below - it's a directory listing, not a second snap load.
    final List<String> snapshotIds = FolderUtils.getLastNTimestampFolders(snapDirectory, 2);
    final int positionManagerBatchSize = PropertyReader.getProperty("POSITION_MANAGER_BATCH_SIZE", 200);

    final String snapshotIdNew = snapshotIds.get(0);
    final long expectedPrevSnapshotId = snapshotIds.size() > 1 ? Long.parseLong(snapshotIds.get(1)) : 0;
    final String newSnapFile = snapDirectory + File.separator + snapshotIdNew;

    LOGGER.info("Loading latest snap: " + snapshotIdNew);
    final List<Message> newUserPositions = loadSnap(args, newSnapFile);
    sb.append("Latest snap file loaded to memory. file: ").append(newSnapFile).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    UserCache.clearUserCache();

    // Diff against the last on-chain-confirmed value per network (blockchain_notional_state, shared
    // with FundManagerV2) instead of yesterday's snap file. A position is included if it differs from
    // EITHER network's last-confirmed value, and the same diff is still pushed to both networks in
    // lockstep per batch, exactly as before. A failed push simply leaves that network's cached value
    // unadvanced, so the affected user/asset reappears in the diff on the next run instead of being
    // silently dropped forever. To force a re-push, delete the row(s) for the affected userId/contractKey
    // from blockchain_notional_state (see caution below on notionalContractKey).
    final Map<Integer, UserPosition> latestPositions = extract(newUserPositions);
    final long snapshotId = Long.parseLong(snapshotIdNew);

    BlockchainNotionalCache.loadFromDB();
    final List<PositionUpdateTransaction.BlockchainPosition> diff = getChangedPositions(latestPositions);
    sb.append("Difference calculated. #OfUpdates: ").append(diff.size()).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");

    // register missing users
    registerMissingUsers(diff, POLYGON, sb);
    registerMissingUsers(diff, XDC, sb);

    final int noOfBatches = (int) Math.ceil(diff.size() / (double) positionManagerBatchSize);
    int recordCount = 0;
    int batchId = 1;
    boolean updateSent = false;
    boolean success = true;

    List<PositionUpdateTransaction.BlockchainPosition> userPositions = new ArrayList<>();
    for (PositionUpdateTransaction.BlockchainPosition position : diff) {
      recordCount++;
      userPositions.add(position);
      if (recordCount >= positionManagerBatchSize) {
        success = pushBatchAndAdvanceCache(POLYGON, snapshotId, expectedPrevSnapshotId, batchId, noOfBatches, userPositions, sb, selfHealedSummary) && success;
        success = pushBatchAndAdvanceCache(XDC, snapshotId, expectedPrevSnapshotId, batchId, noOfBatches, userPositions, sb, selfHealedSummary) && success;

        userPositions = new ArrayList<>();
        batchId++;
        recordCount = 0;
        updateSent = true;
      }
    }
    if (recordCount > 0) {
      updateSent = true;
      LOGGER.info("Processing batch: " + batchId + " noOfBatches: " + noOfBatches);

      success = pushBatchAndAdvanceCache(POLYGON, snapshotId, expectedPrevSnapshotId, batchId, noOfBatches, userPositions, sb, selfHealedSummary) && success;
      success = pushBatchAndAdvanceCache(XDC, snapshotId, expectedPrevSnapshotId, batchId, noOfBatches, userPositions, sb, selfHealedSummary) && success;
    }
    if (!updateSent) {
      System.out.println("Processing batch: " + batchId + " noOfBatches: " + noOfBatches);
      success = pushBatchAndAdvanceCache(POLYGON, snapshotId, expectedPrevSnapshotId, 1, 1, userPositions, sb, selfHealedSummary) && success;
      success = pushBatchAndAdvanceCache(XDC, snapshotId, expectedPrevSnapshotId, 1, 1, userPositions, sb, selfHealedSummary) && success;
    }

    return success;
  }

  private static List<PositionUpdateTransaction.BlockchainPosition> getChangedPositions(final Map<Integer, UserPosition> latestPositions) {
    final List<PositionUpdateTransaction.BlockchainPosition> diff = new ArrayList<>();
    for (final Map.Entry<Integer, UserPosition> userEntry : latestPositions.entrySet()) {
      final int userId = userEntry.getKey();
      for (final Map.Entry<Integer, Long> positionEntry : userEntry.getValue().getUserPositions().entrySet()) {
        final int instrumentId = positionEntry.getKey();
        final long quantity = positionEntry.getValue();
        if (quantityChanged(userId, POLYGON, instrumentId, quantity) || quantityChanged(userId, XDC, instrumentId, quantity)) {
          diff.add(new PositionUpdateTransaction.BlockchainPosition(userId, instrumentId, quantity));
        }
      }
    }
    return diff;
  }

  private static boolean quantityChanged(final int userId, final String network, final int instrumentId, final long quantity) {
    final WithdrawableAmountUpdateTransaction.UserWithdrawable prev =
        BlockchainNotionalCache.getUserWithdrawableMap(notionalContractKey(network, instrumentId)).get(userId);
    if (prev == null) {
      // Never confirmed on this network before - always push at least once, even if the current
      // quantity happens to be 0. Defaulting an unconfirmed row to "assumed 0" would silently skip a
      // real correction whenever current truth is also 0 (e.g. a position that closed out or was
      // liquidated before this network ever got a chance to push its real value).
      return true;
    }
    return prev.getQuantity() != quantity;
  }

  // Pushes one batch to one network, then - only if that specific network confirmed it on-chain -
  // advances blockchain_notional_state for that network so a future run won't re-diff it. If this
  // network's push fails while the other network's succeeds, this network's cache simply stays at its
  // old value and the position naturally reappears in tomorrow's diff (pushed again to both networks,
  // harmlessly re-confirming the one that already had it right).
  private static boolean pushBatchAndAdvanceCache(final String network, final long snapshotId, final long expectedPrevSnapshotId,
      final int batchId, final int noOfBatches, final List<PositionUpdateTransaction.BlockchainPosition> batch, final StringBuilder sb,
      final StringBuilder selfHealedSummary) {
    final boolean status = updateBatch(network, snapshotId, batchId, noOfBatches, batch, sb);
    if (status) {
      final long updated = System.currentTimeMillis();
      for (final PositionUpdateTransaction.BlockchainPosition position : batch) {
        final String contractKey = notionalContractKey(network, position.getInstrumentId());
        // A cached row that's neither brand new (snapshotId 0) nor as-of the immediately preceding
        // snapshot means this network missed at least one full cycle for this position - it just
        // caught up now. Comparing against the actual previous folder id (not a fixed duration) keeps
        // this correct regardless of how often the cron/snapshot cadence changes in the future.
        final WithdrawableAmountUpdateTransaction.UserWithdrawable prev =
            BlockchainNotionalCache.getUserWithdrawableMap(contractKey).get(position.getUserId());
        if (prev != null && missedSyncCycle(prev.getSnapshotId(), expectedPrevSnapshotId)) {
          selfHealedSummary.append("Recovered stale position. network: ").append(network)
              .append(" user: ").append(position.getUserId())
              .append(" instrument: ").append(position.getInstrumentId())
              .append(" lastSyncedSnapshot: ").append(prev.getSnapshotId())
              .append(" nowSyncedSnapshot: ").append(snapshotId)
              .append("\n");
        }
        BlockchainNotionalCache.upsert(
            new WithdrawableAmountUpdateTransaction.UserWithdrawable(position.getUserId(), contractKey, position.getQuantity()),
            updated, snapshotId);
      }
    }
    return status;
  }

  // Package-private (not private) so PositionManagerSnapUpdaterTest can exercise it directly without
  // needing a database or blockchain connection - this is the only pure decision logic in the
  // self-heal detection, everything else here is I/O.
  static boolean missedSyncCycle(final long prevSnapshotId, final long expectedPrevSnapshotId) {
    // snapshotId 0 means either the row has never been synced under this tracking (brand new) or we
    // couldn't determine a previous folder (e.g. the very first run) - neither is a "missed cycle".
    return prevSnapshotId != 0 && expectedPrevSnapshotId != 0 && prevSnapshotId != expectedPrevSnapshotId;
  }

  // Reuses blockchain_notional_state (same table FundManagerV2 uses) keyed by network+instrument
  // instead of network+symbol, since Position Manager tracks every instrument, not just withdrawable
  // stablecoin notional. To force a re-push (e.g. after confirming a stale on-chain value), delete the
  // row(s) for the affected userId/contractKey from blockchain_notional_state.
  // CAUTION: only do this right before a genuinely NEW snapshot folder is picked up (i.e. let the next
  // scheduled run pick it up naturally). PositionManager.sol only resets processedBatches when it sees
  // a snapId it hasn't seen before (batchUpdatePositions: `if (snapId != snapshotId) { ... processedBatches
  // = 0; }`). Manually re-running against the SAME snapshot folder increments processedBatches past
  // totalBatches instead of resetting it, and processedBatches == totalBatches gates withdrawals
  // ("Snapshot update in progress") — so a same-folder forced re-run will block withdrawals until the
  // next new snapshot arrives and resets the counters.
  private static String notionalContractKey(final String network, final int instrumentId) {
    return (network + "_PM_" + instrumentId).toUpperCase();
  }

  private static boolean initialize(String[] args) throws Exception {
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

  private static Map<Integer, UserPosition> extract(final List<Message> allMessages) {
    final Map<Integer, UserPosition> userPositions = new HashMap<>();
    for (Message message : allMessages) {
      if (message instanceof BalanceAdminMessage balanceAdminMessage) {
        final Position[] positionArr = balanceAdminMessage.getPositionArr();
        final int positionsLength = balanceAdminMessage.getPositionsLength();
        if (positionArr == null || positionArr.length == 0 || positionsLength == 0) {
          continue;
        }
        final UserPosition userPosition = new UserPosition(balanceAdminMessage.getUserId());
        userPositions.put(balanceAdminMessage.getUserId(), userPosition);
        for (int i = 1; i < positionsLength; i++) {
          final Position position = positionArr[i];
          userPosition.addPosition(position.getInstrumentId(), position.getAvailableQuantity());
        }
      }
    }
    return userPositions;
  }

  private static boolean updateBatch(final String network, final long snapshotId, final int batchId, final int noOfBatches,
      final List<BlockchainPosition> userPositions, final StringBuilder sb) {
    LOGGER.info("Processing batch: " + batchId + " noOfBatches: " + noOfBatches + " network: " + network);
    final String chain = XDC.equalsIgnoreCase(network) ? Context.getXdcPositionManagerChain() : Context.getPositionManagerChain();
    final String positionManager = XDC.equalsIgnoreCase(network) ? Context.getXdcPositionManagerContractAddress() : Context.getPositionManagerContractAddress();
    final PositionUpdateTransaction transaction = new PositionUpdateTransaction();
    transaction.setId(snapshotId);
    transaction.setBatchId(batchId);
    transaction.setNoOfBatches(noOfBatches);
    transaction.setChainType(chain);
    transaction.setContractAddress(positionManager);
    transaction.setUserPositions(userPositions);

    try {
      final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
      if (sender != null) {
        boolean status = sender.processTransaction(sb);
        sb.append("Snap updated. network: ").append(network).append(" batch ").append(" of ").append(noOfBatches).append(" status: ").append(status).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        return status;
      } else {
        sb.append("Snap update failed. batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        LOGGER.info("Sender not found " + transaction.getChainType());
        System.out.println("Sender not found " + transaction.getChainType());
        return false;
      }
    } catch (Exception e) {
      sb.append("Snap update failed. network: ").append(network).append(" batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss())
          .append(" ").append(e.getMessage()).append("\n");
      LOGGER.error("Error in sending blockchain snap update to " + transaction.getChainType());
      LOGGER.error(ERROR_LOG, e);
      System.out.println(ERROR_LOG + e.getMessage());
      return false;
    }
  }

  private static void registerMissingUsers(final List<PositionUpdateTransaction.BlockchainPosition> balanceChangedUsers, final String network,
      final StringBuilder sb) {
    BlockchainUserCache.loadFromDb();
    final String chain = XDC.equalsIgnoreCase(network) ? Context.getXdcPositionManagerChain() : Context.getPositionManagerChain();
    final String positionManager = XDC.equalsIgnoreCase(network) ? Context.getXdcPositionManagerContractAddress() : Context.getPositionManagerContractAddress();

    final Set<Integer> processedUserIds = new HashSet<>();
    for (final PositionUpdateTransaction.BlockchainPosition position : balanceChangedUsers) {
      final int userId = position.getUserId();
      if (!processedUserIds.add(userId)) {
        continue;
      }

      // blockchain_user_state is keyed by (id, network, contractType): XDC hosts both the Fund
      // Manager V2 contract and the Position Manager contract, so contractType disambiguates which
      // one this row is tracking. Without it, a user already registered on FM V2 XDC would look
      // already-registered here and never actually get registered on the PM XDC contract.
      final BlockchainUser user = BlockchainUserCache.getBlockchainUser(userId, network, BlockchainTransactionSender.POSITION_MANAGEMENT);
      if (user != null) {
        continue;
      }

      try {
        final EngineUser engineUser = BlockchainUserCache.getEngineUser(userId);
        if (engineUser == null) {
          sb.append("User registration failed. user: ").append(userId).append(". User not found ").append("\n");
          LOGGER.error("Position Manager, user registration failed. userId: " + userId + " not found in engine users. network: " + network);
          continue;
        }
        if (!engineUser.getAddress().startsWith("0x")) {
          sb.append("User registration failed. user: ").append(userId)
              .append(". Invalid address: ").append(engineUser.getAddress()).append("\n");
          LOGGER.error("Position Manager, user registration failed. userId: " + userId + " invalid address: " + engineUser.getAddress() + " network: " + network);
          continue;
        }

        final String userAddress = engineUser.getAddress();

        final UserRegistrationTransaction userRegistrationTransaction = new UserRegistrationTransaction();
        userRegistrationTransaction.setId(System.currentTimeMillis());
        userRegistrationTransaction.setNewUserId(userId);
        userRegistrationTransaction.setNewUserAddress(userAddress);
        userRegistrationTransaction.setChainType(chain);
        userRegistrationTransaction.setContractAddress(positionManager);
        userRegistrationTransaction.setManagerType(BlockchainTransactionSender.POSITION_MANAGEMENT);
        final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(userRegistrationTransaction);
        if (sender != null) {
          boolean status = sender.processTransaction(sb);
          if (status) {
            final BlockchainUser blockchainUser = new BlockchainUser(userId, userAddress, System.currentTimeMillis(), network, BlockchainTransactionSender.POSITION_MANAGEMENT);
            BlockchainUserCache.addUser(blockchainUser);
            sb.append("User registered successfully. user: ").append(userId).append(" address ").append(userAddress).append(" network: ").append(network).append("\n");
            LOGGER.info("Position Manager, user registered. userId: " + userId + " address: " + userAddress + " network: " + network);
          } else {
            sb.append("User registration failed. user: ").append(userId).append(" address ").append(userAddress)
                .append(" network: ").append(network).append(" reason: ").append(userRegistrationTransaction.getError()).append("\n");
            LOGGER.error("Position Manager, user registration failed. userId: " + userId + " address: " + userAddress
                + " network: " + network + " reason: " + userRegistrationTransaction.getError());
          }
        } else {
          sb.append("User registration failed. No sender available. user: ").append(userId).append(" address ").append(userAddress)
              .append(" network: ").append(network).append("\n");
          LOGGER.error("Position Manager, user registration failed. No sender available. userId: " + userId
              + " address: " + userAddress + " network: " + network);
        }
      } catch (Exception e) {
        sb.append("User registration failed. user: ").append(userId).append(" reason: ").append(e.getMessage()).append("\n");
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }

  public static class UserPosition {
    private final int userId;
    private final Map<Integer, Long> userPositions = new HashMap<>();

    public UserPosition(int userId) {
      this.userId = userId;
    }

    public int getUserId() {
      return userId;
    }

    public Map<Integer, Long> getUserPositions() {
      return userPositions;
    }

    public void addPosition(int instrumentId, long quantity) {
      userPositions.put(instrumentId, quantity);
    }
  }
}
