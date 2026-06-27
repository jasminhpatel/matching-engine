package com.solfini.reconciliation;

import static com.solfini.common.Constants.MAINNET;
import static com.solfini.common.Constants.XDC;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.Position;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.util.MailUtil;
import com.solfini.util.MailUtil.MailAttachment;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.snapshot.SnapConverter;
import com.solfini.util.snapshot.SnapTransformer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.mail.MessagingException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.abi.datatypes.Address;
import org.web3j.utils.Numeric;

public class FundManagerV2Reconciliation {
  private static final Logger LOGGER = LogManager.getLogger(FundManagerV2Reconciliation.class);
  private static List<Message> cachedSnapMessages = null;
  private static String cachedSnapFile = null;

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

  public static void reconcile(final String[] args, final StringBuilder summary, final boolean eod,
      final String network, final String symbol) throws IOException, MessagingException {
    summary.append("\n");

    final StringBuilder csv = new StringBuilder();
    csv.append("UserId,SnapBalance,BlockchainBalance,Status\n");
    LOGGER.info("Fund Manager V2 reconciliation started.");
    final Properties overlay = new Properties();
    loadConfigurationFile(overlay);

    int missingUsers = 0, missingPositions = 0, mismatchedPositions = 0, matchedPositions = 0;
    // Load positions from PositionManager Smart Contract
    final FundManagerSnapData blockchainSnapData = FundManagerV2Loader.getUserPositions(network, symbol);
    final Map<Integer, Long> blockchainPositionsMap = blockchainSnapData.getUserPositionsMap();
    // load positions from SnapFile
    String snapshotMappingId = blockchainSnapData.getSnapshotId();
    String snapshotId = String.valueOf(BlockchainNotionalCache.getSnapshotIdByMappingId(
        StringUtil.toInt(snapshotMappingId), network.toUpperCase(), symbol.toUpperCase()));

    LOGGER.info("Fund Manager snapshotId: " + snapshotId);
    String snapFile = PropertyReader.getProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", "") + "/"  + snapshotId;
    String symbolsToIgnore = PropertyReader.getProperty("IGNORE_SYMBOL_LIST", "");
    final double tolerancePercentage = PropertyReader.getProperty("ETHEREUM_NOTIONAL_DIFF_PERCENTAGE", 0.05);

    final Set<Integer> symbolsToIgnoreSet = new HashSet<>();
    final Set<Integer> symbolsToAllowSet = new HashSet<>();

    if (symbolsToIgnore != null && !symbolsToIgnore.isEmpty()) {
      final String[] symbolsToIgnoreArr = symbolsToIgnore.split(",");
      for(String s : symbolsToIgnoreArr) {
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
    if (cachedSnapMessages == null || !snapFile.equals(cachedSnapFile)) {
      cachedSnapMessages = loadSnap(args, snapFile);
      cachedSnapFile = snapFile;
    }
    final List<Message> snapUserPositions = cachedSnapMessages;
    double totalUserPositionsValue = 0;
    double totalMarketMakerPositionsValue = 0;

    // Reconcile
    for (Message message : snapUserPositions) {
      if (message instanceof BalanceAdminMessage balanceAdminMessage) {
        int userId = balanceAdminMessage.getUserId();
        double snapWithdrawable = 0;
        if (balanceAdminMessage.getPositionArr() != null) {
          for (Position p : balanceAdminMessage.getPositionArr()) {
            if (p != null) {
              if (MAINNET.equalsIgnoreCase(network)) {
                if (!symbolsToIgnoreSet.contains(p.getInstrumentId())) {
                  if (p.getQuantity() != 0) {
                    if (userId == 8) { // market maker
                      totalMarketMakerPositionsValue += p.getUsdValue();
                    } else {
                      totalUserPositionsValue += p.getUsdValue();
                    }
                    snapWithdrawable = snapWithdrawable + p.getUsdValue() /*+ p.getUsdUnrealized()*/;
                  }
                }
              } else if (XDC.equalsIgnoreCase(network)) {
                if (symbolsToAllowSet.contains(p.getInstrumentId())) {
                  if (p.getQuantity() != 0) {
                    if (userId == 8) { // market maker
                      totalMarketMakerPositionsValue += p.getUsdValue();
                    } else {
                      totalUserPositionsValue += p.getUsdValue();
                    }
                    snapWithdrawable = snapWithdrawable + p.getUsdValue() /*+ p.getUsdUnrealized()*/;
                  }
                }
              }
            }
          }
        } else {
          csv.append(userId).append(",").append(snapWithdrawable).append(",").append("0").append(",User positions doesn't exist on snap file\n");
          continue;
        }
        long scaledSnapValue = (long) (snapWithdrawable * 1_000_000D);
        Long blockchainUserPosition = blockchainPositionsMap.get(balanceAdminMessage.getUserId());
        if (blockchainUserPosition == null) {
          if (scaledSnapValue > 0) {
            csv.append(userId).append(",").append(snapWithdrawable).append(",").append("0").append(",User doesn't exist on blockchain\n");
            missingUsers++;
          }
          continue;
        }
        long scaledBlockchainValue = blockchainUserPosition;
        if (scaledSnapValue != scaledBlockchainValue) {
          double changePercentage = 0;
          if (scaledBlockchainValue != 0) {
            changePercentage = Math.abs((scaledSnapValue - scaledBlockchainValue)/(double) scaledBlockchainValue);
          } else {
            changePercentage = 1;
          }
          if (changePercentage > tolerancePercentage) {
            csv.append(userId).append(",").append(snapWithdrawable).append(",").append(scaledBlockchainValue / 1_000_000D)
                .append(",Positions doesn't match\n");
            mismatchedPositions++;
          } else {
            csv.append(userId).append(",").append(snapWithdrawable).append(",").append(scaledBlockchainValue/1_000_000D).append(",Matched within 5%\n");
            matchedPositions++;
          }
        } else {
          csv.append(userId).append(",").append(snapWithdrawable).append(",").append(scaledBlockchainValue/1_000_000D).append(",Matched\n");
          matchedPositions++;
        }
      }
    }

    // todo reverse check (users and assets not in snap but exists in blockchain)
    summary.append("Ethereum–User Withdrawable Reconciliation Summary: \n");
    summary.append("\t Snapshot Id: ").append(blockchainSnapData.getSnapshotId()).append("\n");
    summary.append("\t No of missing users in the contract: ").append(missingUsers).append("\n");
    summary.append("\t No of missing Positions: ").append(missingPositions).append("\n");
    summary.append("\t No of mismatched Positions: ").append(mismatchedPositions).append("\n");
    summary.append("\t No of matched Positions: ").append(matchedPositions).append("\n\n");

    summary.append("\t Total of user position in USD: ").append(totalUserPositionsValue).append("\n");
    summary.append("\t Total of market maker positions in USD: ").append(totalMarketMakerPositionsValue).append("\n");

    if (MAINNET.equalsIgnoreCase(network)) {
      double contractUSDCValue = FundManagerLoader.getBalance(network, Context.getUsdcContract());
      double contractUSDTValue = FundManagerLoader.getBalance(network, Context.getUsdtContract());
      summary.append("\t Total USDC balance of the contract: ").append(contractUSDCValue)
          .append("\n");
      summary.append("\t Total USDT balance of the contract: ").append(contractUSDTValue)
          .append("\n");
    } else if (XDC.equalsIgnoreCase(network)) {
      double contractXUSDCValue = FundManagerLoader.getBalance(network, Context.getXusdcContract());
      //double xdcNativeValue = FundManagerLoader.getBalance(network, Numeric.toHexStringWithPrefixZeroPadded(BigInteger.ZERO, 40));
      summary.append("\t Total XUSDC balance of the contract: ").append(contractXUSDCValue)
          .append("\n");
/*      summary.append("\t Total XDC balance of the contract: ").append(xdcNativeValue)
          .append("\n");*/
    }

    System.out.println(summary);
    System.out.println();
    System.out.println(csv);

    if (eod) {
      String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS",
          "alerts.rohanw@gmail.com").split(",");
      String subject = "Ethereum–User Withdrawable Reconciliation";
      String body = summary.toString();
      String csvFile = csv.toString();
      List<MailAttachment> mailAttachments = new ArrayList<>(2);
      mailAttachments.add(
          new MailAttachment("reconciliation_report.csv", "text/csv", csvFile.toString()));

      MailUtil.sendMessage(to, subject, body, mailAttachments);
    }
  }
}

