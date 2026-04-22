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
import com.solfini.util.blockchain.model.PositionUpdateTransaction;
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

  public static boolean update(String[] args, final StringBuilder sb) throws Exception {
    sb.append("Position manager (Polygon) snap update started. time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    initialize(args);
    final Properties overlay = new Properties();
    loadConfigurationFile(overlay);
    loadBlockchainKeyFile();

    final String snapDirectory = PropertyReader.getProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", "");
    final List<String> snapshotIds = FolderUtils.getLastNTimestampFolders(snapDirectory, 2);
    final String symbolsToIgnore = PropertyReader.getProperty("IGNORE_SYMBOL_LIST", "");
    final int positionManagerBatchSize = PropertyReader.getProperty("POSITION_MANAGER_BATCH_SIZE", 200);

    final String snapshotIdNew = snapshotIds.get(0);
    final String snapshotIdPrev = snapshotIds.get(1);
    final String newSnapFile = snapDirectory + File.separator + snapshotIdNew;
    final String prevSnapFile = snapDirectory + File.separator + snapshotIdPrev;

    final Set<Integer> symbolsToIgnoreSet = new HashSet<>();
    if (symbolsToIgnore != null && !symbolsToIgnore.isEmpty()) {
      String[] symbolsToIgnoreArr = symbolsToIgnore.split(",");
      for (String s : symbolsToIgnoreArr) {
        symbolsToIgnoreSet.add(StringUtil.toInt(s));
      }
    }

    LOGGER.info("Loading snap diff between previous: " + snapshotIdPrev + " new: " + snapshotIdNew);
    final List<Message> newUserPositions = loadSnap(args, newSnapFile);
    sb.append("Latest snap file loaded to memory. file: ").append(newSnapFile).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
    UserCache.clearUserCache();
    final List<Message> prevUserPositions = loadSnap(args, prevSnapFile);
    sb.append("Previous snap file loaded to memory. file: ").append(prevSnapFile).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");

    final List<PositionUpdateTransaction.BlockchainPosition> diff = getDifference(prevUserPositions, newUserPositions, snapshotIdPrev, snapshotIdNew);
    sb.append("Difference calculated. #OfUpdates: ").append(diff.size()).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");

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
        LOGGER.info("Processing batch: " + batchId + " noOfBatches: " + noOfBatches);
        final PositionUpdateTransaction transaction = new PositionUpdateTransaction();
        transaction.setId(Long.parseLong(snapshotIdNew));
        transaction.setBatchId(batchId);
        transaction.setNoOfBatches(noOfBatches);
        transaction.setChainType(Context.getPositionManagerChain());
        transaction.setContractAddress(Context.getPositionManagerContractAddress());
        transaction.setUserPositions(userPositions);

        try {
          final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
          if (sender != null) {
            boolean status = sender.processTransaction(sb);
            success = success && status;
            sb.append("Snap updated. batch ").append(" of ").append(noOfBatches).append(" status: ").append(status).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
          } else {
            sb.append("Snap update failed. batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
            LOGGER.info("Sender not found " + transaction.getChainType());
            System.out.println("Sender not found " + transaction.getChainType());
          }
        } catch (Exception e) {
          sb.append("Snap update failed. batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss())
              .append(" ").append(e.getMessage()).append("\n");
          LOGGER.error("Error in sending blockchain snap update to " + transaction.getChainType());
          LOGGER.error(ERROR_LOG, e);
          System.out.println(ERROR_LOG + e.getMessage());
        }

        userPositions = new ArrayList<>();
        batchId++;
        recordCount = 0;
        updateSent = true;
      }
    }
    if (recordCount > 0) {
      updateSent = true;
      LOGGER.info("Processing batch: " + batchId + " noOfBatches: " + noOfBatches);
      final PositionUpdateTransaction transaction = new PositionUpdateTransaction();
      transaction.setId(Long.parseLong(snapshotIdNew));
      transaction.setBatchId(batchId);
      transaction.setNoOfBatches(noOfBatches);
      transaction.setChainType(Context.getPositionManagerChain());
      transaction.setContractAddress(Context.getPositionManagerContractAddress());
      transaction.setUserPositions(userPositions);
      try {
        final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
        if (sender != null) {
          boolean status = sender.processTransaction(sb);
          success = success && status;
          sb.append("Snap updated. batch ").append(" of ").append(noOfBatches).append(" status: ").append(status).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        } else {
          sb.append("Snap update failed. batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
          LOGGER.info("Sender not found " + transaction.getChainType());
          System.out.println("Sender not found " + transaction.getChainType());
        }
      } catch (Exception e) {
        sb.append("Snap update failed. batch ").append(" of ").append(noOfBatches).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss())
            .append(" ").append(e.getMessage()).append("\n");
        LOGGER.error(ERROR_LOG, e);
        System.out.println(ERROR_LOG + e.getMessage());
      }
    }
    if (!updateSent) {
      System.out.println("Processing batch: " + batchId + " noOfBatches: " + noOfBatches);
      final PositionUpdateTransaction transaction = new PositionUpdateTransaction();
      transaction.setId(Long.parseLong(snapshotIdNew));
      transaction.setBatchId(1);
      transaction.setNoOfBatches(1);
      transaction.setChainType(Context.getPositionManagerChain());
      transaction.setContractAddress(Context.getPositionManagerContractAddress());
      transaction.setUserPositions(userPositions);
      try {
        final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(transaction);
        if (sender != null) {
          boolean status = sender.processTransaction(sb);
          sb.append("Blockchain sendTransaction status: ").append(status).append("\n");
          success = success && status;
          sb.append("Snap updated. batch 1").append(" of ").append(1).append(" status: ").append(status).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss()).append("\n");
        } else {
          LOGGER.info("Sender not found " + transaction.getChainType());
          System.out.println("Sender not found " + transaction.getChainType());
          sb.append("Snap update failed. batch ").append(" of ").append(1).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss())
              .append("Sender not found: ").append(transaction.getChainType()).append("\n");
        }
      } catch (Exception e) {
        sb.append("Snap update failed. batch 1").append(" of ").append(1).append(" status: ").append(false).append(" time: ").append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss())
            .append(" ").append(e.getMessage()).append("\n");
        LOGGER.info("Error in sending blockchain snap update to " + transaction.getChainType());
        LOGGER.error(ERROR_LOG, e);
        System.out.println(ERROR_LOG + e.getMessage());
      }
    }

    return success;
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

  private static List<PositionUpdateTransaction.BlockchainPosition> getDifference(final List<Message> prevMessages, final List<Message> newMessages,
      final String previousSnapId, final String currentSnapId) {
    Map<Integer, UserPosition> prevUserPositions = extract(prevMessages);
    Map<Integer, UserPosition> newUserPositions = extract(newMessages);

    final List<PositionUpdateTransaction.BlockchainPosition> userPositionDiff = new FastArrayList<>();
    final StringBuilder sb = new StringBuilder();
    sb.append("\n Changed positions previousSnapId: ").append(previousSnapId).append(" currentSnapId: ").append(currentSnapId);
    for (int userId : newUserPositions.keySet()) {
      final UserPosition oldPositions = prevUserPositions.get(userId);
      final UserPosition newPositions = newUserPositions.get(userId);

      for (int instrumentId : newPositions.getUserPositions().keySet()) {
        long oldPosition = oldPositions != null ? oldPositions.getUserPositions().getOrDefault(instrumentId, 0L) : 0L;
        long newPosition = newPositions.getUserPositions().get(instrumentId);
        if (oldPosition != newPosition) {
          userPositionDiff.add(new PositionUpdateTransaction.BlockchainPosition(userId, (int) instrumentId, newPosition));
          sb.append("\n userId: \t\t").append(userId).append(" instrumentId: \t\t").append(instrumentId).append(" new:\t\t").append(newPosition)
              .append(" old: \t\t").append(oldPosition).append(" diff: \t\t").append((newPosition - oldPosition));
        }
      }
    }
    System.out.println(sb.toString());

    return userPositionDiff;
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
