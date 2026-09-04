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
      // Strip -d KEY=VALUE pairs injected by CatchupJob/CheckJob before Commons CLI sees them
      final List<String> filteredArgList = new ArrayList<>();
      for (int i = 0; i < args.length; i++) {
        if ("-d".equals(args[i]) && i + 1 < args.length) {
          i++;
        } else {
          filteredArgList.add(args[i]);
        }
      }
      final CommandLine cmd = parser.parse(options, filteredArgList.toArray(new String[0]));
      final String snapshot = snapfile;
      final String json = cmd.getOptionValue("j");
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
    final AssetNotionalAccumulator notionalAccumulator =
        new AssetNotionalAccumulator(Context.getUsdcId(), Context.getUsdtId(), Context.getXusdcId());

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
                    notionalAccumulator.addPosition(userId == 8, p.getInstrumentId(), p.getUsdValue());
                    snapWithdrawable = snapWithdrawable + p.getUsdValue() /*+ p.getUsdUnrealized()*/;
                  }
                }
              } else if (XDC.equalsIgnoreCase(network)) {
                if (symbolsToAllowSet.contains(p.getInstrumentId())) {
                  if (p.getQuantity() != 0) {
                    notionalAccumulator.addPosition(userId == 8, p.getInstrumentId(), p.getUsdValue());
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
    final String networkLabel = networkLabel(network);
    summary.append(networkLabel).append("–User Withdrawable Reconciliation Summary: \n");
    summary.append("\t Snapshot Id: ").append(blockchainSnapData.getSnapshotId()).append("\n");
    summary.append("\t No of missing users in the contract: ").append(missingUsers).append("\n");
    summary.append("\t No of missing Positions: ").append(missingPositions).append("\n");
    summary.append("\t No of mismatched Positions: ").append(mismatchedPositions).append("\n");
    summary.append("\t No of matched Positions: ").append(matchedPositions).append("\n\n");

    final StringBuilder html = new StringBuilder();
    html.append("<div style=\"font-family:Arial,Helvetica,sans-serif;color:#222222;\">");
    html.append("<h2 style=\"margin:0 0 12px;color:#2c3e50;\">").append(networkLabel)
        .append("–User Withdrawable Reconciliation Summary</h2>");
    html.append("<table style=\"border-collapse:collapse;font-size:13px;margin-bottom:16px;\">");
    appendHtmlInfoRow(html, "Snapshot Id", blockchainSnapData.getSnapshotId());
    appendHtmlInfoRow(html, "Missing users in the contract", missingUsers);
    appendHtmlInfoRow(html, "Missing Positions", missingPositions);
    appendHtmlInfoRow(html, "Mismatched Positions", mismatchedPositions);
    appendHtmlInfoRow(html, "Matched Positions", matchedPositions);

    final double totalUserPositionsValue = notionalAccumulator.getTotalUserValue();
    final double totalMarketMakerPositionsValue = notionalAccumulator.getTotalMarketMakerValue();
    summary.append("\t Total of user position in USD: ").append(totalUserPositionsValue).append("\n");
    summary.append("\t Total of market maker positions in USD: ").append(totalMarketMakerPositionsValue).append("\n");
    appendHtmlInfoRow(html, "Total user position (USD)", String.format("%,.2f", totalUserPositionsValue));
    appendHtmlInfoRow(html, "Total market maker positions (USD)", String.format("%,.2f", totalMarketMakerPositionsValue));

    boolean contractBalanceMismatchFound = false;
    if (MAINNET.equalsIgnoreCase(network)) {
      double contractUSDCValue = FundManagerLoader.getBalance(network, Context.getUsdcContract());
      double contractUSDTValue = FundManagerLoader.getBalance(network, Context.getUsdtContract());
      summary.append("\t Total USDC balance of the contract: ").append(contractUSDCValue)
          .append("\n");
      summary.append("\t Total USDT balance of the contract: ").append(contractUSDTValue)
          .append("\n");
      appendHtmlInfoRow(html, "Total USDC balance of the contract", String.format("%,.2f", contractUSDCValue));
      appendHtmlInfoRow(html, "Total USDT balance of the contract", String.format("%,.2f", contractUSDTValue));
      html.append("</table>");

      AssetBalanceCheckResult usdcCheck = ContractBalanceChecker.check(
          "USDC", notionalAccumulator.getUsdcRequiredBacking(), contractUSDCValue, tolerancePercentage);
      AssetBalanceCheckResult usdtCheck = ContractBalanceChecker.check(
          "USDT", notionalAccumulator.getUsdtRequiredBacking(), contractUSDTValue, tolerancePercentage);

      summary.append(ContractBalanceChecker.formatTableHeader());
      summary.append(ContractBalanceChecker.formatTableRow(
          notionalAccumulator.getUsdcUserValue(), notionalAccumulator.getUsdcMmValue(), usdcCheck));
      html.append(ContractBalanceChecker.formatHtmlTableOpen());
      html.append(ContractBalanceChecker.formatHtmlTableRow(
          notionalAccumulator.getUsdcUserValue(), notionalAccumulator.getUsdcMmValue(), usdcCheck));
      if (!usdcCheck.skipped() && !usdcCheck.matched()) {
        contractBalanceMismatchFound = true;
        LOGGER.error("Contract balance mismatch for USDC on {}: required={} actual={} diff={}%",
            network, usdcCheck.requiredBacking(), usdcCheck.actualBalance(), usdcCheck.changePercentage() * 100);
      }
      summary.append(ContractBalanceChecker.formatTableRow(
          notionalAccumulator.getUsdtUserValue(), notionalAccumulator.getUsdtMmValue(), usdtCheck));
      html.append(ContractBalanceChecker.formatHtmlTableRow(
          notionalAccumulator.getUsdtUserValue(), notionalAccumulator.getUsdtMmValue(), usdtCheck));
      if (!usdtCheck.skipped() && !usdtCheck.matched()) {
        contractBalanceMismatchFound = true;
        LOGGER.error("Contract balance mismatch for USDT on {}: required={} actual={} diff={}%",
            network, usdtCheck.requiredBacking(), usdtCheck.actualBalance(), usdtCheck.changePercentage() * 100);
      }
      html.append(ContractBalanceChecker.formatHtmlTableClose());
    } else if (XDC.equalsIgnoreCase(network)) {
      double contractXUSDCValue = FundManagerLoader.getBalance(network, Context.getXusdcContract());
      //double xdcNativeValue = FundManagerLoader.getBalance(network, Numeric.toHexStringWithPrefixZeroPadded(BigInteger.ZERO, 40));
      summary.append("\t Total XUSDC balance of the contract: ").append(contractXUSDCValue)
          .append("\n");
/*      summary.append("\t Total XDC balance of the contract: ").append(xdcNativeValue)
          .append("\n");*/
      appendHtmlInfoRow(html, "Total XUSDC balance of the contract", String.format("%,.2f", contractXUSDCValue));
      html.append("</table>");

      AssetBalanceCheckResult xusdcCheck = ContractBalanceChecker.check(
          "XUSDC", notionalAccumulator.getXusdcRequiredBacking(), contractXUSDCValue, tolerancePercentage);
      summary.append(ContractBalanceChecker.formatTableHeader());
      summary.append(ContractBalanceChecker.formatTableRow(
          notionalAccumulator.getXusdcUserValue(), notionalAccumulator.getXusdcMmValue(), xusdcCheck));
      html.append(ContractBalanceChecker.formatHtmlTableOpen());
      html.append(ContractBalanceChecker.formatHtmlTableRow(
          notionalAccumulator.getXusdcUserValue(), notionalAccumulator.getXusdcMmValue(), xusdcCheck));
      if (!xusdcCheck.skipped() && !xusdcCheck.matched()) {
        contractBalanceMismatchFound = true;
        LOGGER.error("Contract balance mismatch for XUSDC on {}: required={} actual={} diff={}%",
            network, xusdcCheck.requiredBacking(), xusdcCheck.actualBalance(), xusdcCheck.changePercentage() * 100);
      }
      html.append(ContractBalanceChecker.formatHtmlTableClose());
    }
    html.append("</div>");

    System.out.println(summary);
    System.out.println();
    System.out.println(csv);

    if (eod) {
      String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS",
          "alerts.rohanw@gmail.com").split(",");
      String subject = (contractBalanceMismatchFound ? "[CONTRACT BALANCE MISMATCH] " : "")
          + networkLabel + "–User Withdrawable Reconciliation";
      String body = summary.toString();
      String csvFile = csv.toString();
      List<MailAttachment> mailAttachments = new ArrayList<>(2);
      mailAttachments.add(
          new MailAttachment("reconciliation_report.csv", "text/csv", csvFile.toString()));

      MailUtil.sendMessage(to, subject, body, html.toString(), mailAttachments);
    }
  }

  /**
   * Composite-aware counterpart of reconcile(network, symbol) - see FundManagerV2SyncJob.
   * syncComposites() for the caller. Symbol-keyed reconcile() is unchanged and still used by
   * FundManagerV2CatchupJob's V5-only path; this is a parallel method, not a replacement, so that
   * tool stays untouched (TSYS-131).
   *
   * memberTokenAddresses are the composite's member tokens as returned by
   * FundingContractV6Loader.getCompositeTokens - resolved here to engine instrument ids via
   * TokenInstrumentResolver so the snapshot-side comparison covers exactly this composite's members
   * (all of them for a Grouped composite, e.g. Ethereum's USDC+USDT; just one for a Segregated one).
   * A member that can't be resolved (native XDC's zero address - no ASSET instrument registered for
   * it yet) is skipped rather than failing the whole composite's reconciliation.
   */
  public static void reconcileComposite(final String[] args, final StringBuilder summary, final boolean eod,
      final String network, final String compositeId, final List<String> memberTokenAddresses) throws IOException, MessagingException {
    summary.append("\n");

    final StringBuilder csv = new StringBuilder();
    csv.append("UserId,SnapBalance,BlockchainBalance,Status\n");
    LOGGER.info("Fund Manager V2 composite reconciliation started. network=" + network + " compositeId=" + compositeId);
    final Properties overlay = new Properties();
    loadConfigurationFile(overlay);

    // Same resolver FundManagerV2SnapUpdater.updateComposite already uses to build its own
    // member-instrument set (loadFromSnapComposite's ignore-set) - throws IllegalStateException for
    // an unrecognized token or an unregistered native-XDC instrument, which propagates out of this
    // method and is caught per-composite by FundManagerV2SyncJob's caller, same as any other
    // reconciliation failure for that composite.
    final Set<Integer> memberInstrumentIds = new HashSet<>();
    for (final String tokenAddress : memberTokenAddresses) {
      memberInstrumentIds.add(FundingContractV6Loader.resolveInstrumentId(tokenAddress));
    }

    int missingUsers = 0, mismatchedPositions = 0, matchedPositions = 0;
    final FundManagerSnapData blockchainSnapData = FundManagerV2Loader.getUserPositionsByComposite(network, compositeId);
    final Map<Integer, Long> blockchainPositionsMap = blockchainSnapData.getUserPositionsMap();
    final String snapshotMappingId = blockchainSnapData.getSnapshotId();
    final String snapshotId = String.valueOf(BlockchainNotionalCache.getSnapshotIdByMappingId(
        StringUtil.toInt(snapshotMappingId), network.toUpperCase(), compositeId));

    LOGGER.info("Fund Manager snapshotId: " + snapshotId);
    final String snapFile = PropertyReader.getProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", "") + "/" + snapshotId;
    final double tolerancePercentage = PropertyReader.getProperty("ETHEREUM_NOTIONAL_DIFF_PERCENTAGE", 0.05);

    if (cachedSnapMessages == null || !snapFile.equals(cachedSnapFile)) {
      cachedSnapMessages = loadSnap(args, snapFile);
      cachedSnapFile = snapFile;
    }
    final List<Message> snapUserPositions = cachedSnapMessages;
    final AssetNotionalAccumulator notionalAccumulator =
        new AssetNotionalAccumulator(Context.getUsdcId(), Context.getUsdtId(), Context.getXusdcId());

    for (final Message message : snapUserPositions) {
      if (message instanceof BalanceAdminMessage balanceAdminMessage) {
        final int userId = balanceAdminMessage.getUserId();
        final Position[] positionArr = balanceAdminMessage.getPositionArr();
        if (positionArr == null) {
          csv.append(userId).append(",0,0,User positions doesn't exist on snap file\n");
          continue;
        }
        final double snapWithdrawable = CompositeSnapAggregator.sumMemberWithdrawable(positionArr, memberInstrumentIds);
        for (final Position p : positionArr) {
          if (p != null && p.getQuantity() != 0 && memberInstrumentIds.contains(p.getInstrumentId())) {
            notionalAccumulator.addPosition(userId == 8, p.getInstrumentId(), p.getUsdValue());
          }
        }

        final long scaledSnapValue = (long) (snapWithdrawable * 1_000_000D);
        final Long blockchainUserPosition = blockchainPositionsMap.get(userId);
        if (blockchainUserPosition == null) {
          if (scaledSnapValue > 0) {
            csv.append(userId).append(",").append(snapWithdrawable).append(",0,User doesn't exist on blockchain\n");
            missingUsers++;
          }
          continue;
        }
        final long scaledBlockchainValue = blockchainUserPosition;
        if (scaledSnapValue != scaledBlockchainValue) {
          final double changePercentage = scaledBlockchainValue != 0
              ? Math.abs((scaledSnapValue - scaledBlockchainValue) / (double) scaledBlockchainValue)
              : 1;
          if (changePercentage > tolerancePercentage) {
            csv.append(userId).append(",").append(snapWithdrawable).append(",").append(scaledBlockchainValue / 1_000_000D)
                .append(",Positions doesn't match\n");
            mismatchedPositions++;
          } else {
            csv.append(userId).append(",").append(snapWithdrawable).append(",").append(scaledBlockchainValue / 1_000_000D)
                .append(",Matched within 5%\n");
            matchedPositions++;
          }
        } else {
          csv.append(userId).append(",").append(snapWithdrawable).append(",").append(scaledBlockchainValue / 1_000_000D)
              .append(",Matched\n");
          matchedPositions++;
        }
      }
    }

    final String networkLabel = networkLabel(network);
    final String assetLabel = assetLabel(network, memberTokenAddresses);
    summary.append(networkLabel).append("/").append(assetLabel).append(" – User Withdrawable Reconciliation Summary: \n");
    summary.append("\t Composite Id: ").append(compositeId).append("\n");
    summary.append("\t Snapshot Id: ").append(blockchainSnapData.getSnapshotId()).append("\n");
    summary.append("\t No of missing users in the contract: ").append(missingUsers).append("\n");
    summary.append("\t No of mismatched Positions: ").append(mismatchedPositions).append("\n");
    summary.append("\t No of matched Positions: ").append(matchedPositions).append("\n\n");

    final StringBuilder html = new StringBuilder();
    html.append("<div style=\"font-family:Arial,Helvetica,sans-serif;color:#222222;\">");
    html.append("<h2 style=\"margin:0 0 12px;color:#2c3e50;\">").append(networkLabel)
        .append("/").append(assetLabel).append(" – User Withdrawable Reconciliation Summary</h2>");
    html.append("<table style=\"border-collapse:collapse;font-size:13px;margin-bottom:16px;\">");
    appendHtmlInfoRow(html, "Composite Id", compositeId);
    appendHtmlInfoRow(html, "Snapshot Id", blockchainSnapData.getSnapshotId());
    appendHtmlInfoRow(html, "Missing users in the contract", missingUsers);
    appendHtmlInfoRow(html, "Mismatched Positions", mismatchedPositions);
    appendHtmlInfoRow(html, "Matched Positions", matchedPositions);

    final double totalUserPositionsValue = notionalAccumulator.getTotalUserValue();
    final double totalMarketMakerPositionsValue = notionalAccumulator.getTotalMarketMakerValue();
    summary.append("\t Total of user position in USD: ").append(totalUserPositionsValue).append("\n");
    summary.append("\t Total of market maker positions in USD: ").append(totalMarketMakerPositionsValue).append("\n");
    appendHtmlInfoRow(html, "Total user position (USD)", String.format("%,.2f", totalUserPositionsValue));
    appendHtmlInfoRow(html, "Total market maker positions (USD)", String.format("%,.2f", totalMarketMakerPositionsValue));
    html.append("</table>");

    boolean contractBalanceMismatchFound = false;
    summary.append(ContractBalanceChecker.formatTableHeader());
    html.append(ContractBalanceChecker.formatHtmlTableOpen());

    if (memberInstrumentIds.contains(Context.getUsdcId())) {
      final double contractUSDCValue = FundManagerLoader.getBalance(network, Context.getUsdcContract());
      final AssetBalanceCheckResult usdcCheck = ContractBalanceChecker.check(
          "USDC", notionalAccumulator.getUsdcRequiredBacking(), contractUSDCValue, tolerancePercentage);
      summary.append(ContractBalanceChecker.formatTableRow(
          notionalAccumulator.getUsdcUserValue(), notionalAccumulator.getUsdcMmValue(), usdcCheck));
      html.append(ContractBalanceChecker.formatHtmlTableRow(
          notionalAccumulator.getUsdcUserValue(), notionalAccumulator.getUsdcMmValue(), usdcCheck));
      if (!usdcCheck.skipped() && !usdcCheck.matched()) {
        contractBalanceMismatchFound = true;
        LOGGER.error("Contract balance mismatch for USDC on {}: required={} actual={} diff={}%",
            network, usdcCheck.requiredBacking(), usdcCheck.actualBalance(), usdcCheck.changePercentage() * 100);
      }
    }
    if (memberInstrumentIds.contains(Context.getUsdtId())) {
      final double contractUSDTValue = FundManagerLoader.getBalance(network, Context.getUsdtContract());
      final AssetBalanceCheckResult usdtCheck = ContractBalanceChecker.check(
          "USDT", notionalAccumulator.getUsdtRequiredBacking(), contractUSDTValue, tolerancePercentage);
      summary.append(ContractBalanceChecker.formatTableRow(
          notionalAccumulator.getUsdtUserValue(), notionalAccumulator.getUsdtMmValue(), usdtCheck));
      html.append(ContractBalanceChecker.formatHtmlTableRow(
          notionalAccumulator.getUsdtUserValue(), notionalAccumulator.getUsdtMmValue(), usdtCheck));
      if (!usdtCheck.skipped() && !usdtCheck.matched()) {
        contractBalanceMismatchFound = true;
        LOGGER.error("Contract balance mismatch for USDT on {}: required={} actual={} diff={}%",
            network, usdtCheck.requiredBacking(), usdtCheck.actualBalance(), usdtCheck.changePercentage() * 100);
      }
    }
    if (memberInstrumentIds.contains(Context.getXusdcId())) {
      final double contractXUSDCValue = FundManagerLoader.getBalance(network, Context.getXusdcContract());
      final AssetBalanceCheckResult xusdcCheck = ContractBalanceChecker.check(
          "XUSDC", notionalAccumulator.getXusdcRequiredBacking(), contractXUSDCValue, tolerancePercentage);
      summary.append(ContractBalanceChecker.formatTableRow(
          notionalAccumulator.getXusdcUserValue(), notionalAccumulator.getXusdcMmValue(), xusdcCheck));
      html.append(ContractBalanceChecker.formatHtmlTableRow(
          notionalAccumulator.getXusdcUserValue(), notionalAccumulator.getXusdcMmValue(), xusdcCheck));
      if (!xusdcCheck.skipped() && !xusdcCheck.matched()) {
        contractBalanceMismatchFound = true;
        LOGGER.error("Contract balance mismatch for XUSDC on {}: required={} actual={} diff={}%",
            network, xusdcCheck.requiredBacking(), xusdcCheck.actualBalance(), xusdcCheck.changePercentage() * 100);
      }
    }
    // Native XDC composite membership resolves to null above and is skipped - it's also excluded
    // from publishing via EXCLUDE_COMPOSITE_LIST until an ASSET instrument is registered for it,
    // and no contract-balance check exists for it yet either (FundManagerLoader.getBalance is
    // ERC20 balanceOf-based; native coin needs eth_getBalance). Add one here when that gap closes.

    html.append(ContractBalanceChecker.formatHtmlTableClose());
    html.append("</div>");

    System.out.println(summary);
    System.out.println();
    System.out.println(csv);

    if (eod) {
      final String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS",
          "alerts.rohanw@gmail.com").split(",");
      final String subject = (contractBalanceMismatchFound ? "[CONTRACT BALANCE MISMATCH] " : "")
          + networkLabel + "/" + assetLabel + " – User Withdrawable Reconciliation";
      final String body = summary.toString();
      final String csvFile = csv.toString();
      final List<MailAttachment> mailAttachments = new ArrayList<>(2);
      mailAttachments.add(new MailAttachment("reconciliation_report.csv", "text/csv", csvFile));

      MailUtil.sendMessage(to, subject, body, html.toString(), mailAttachments);
    }
  }

  static String networkLabel(final String network) {
    return XDC.equalsIgnoreCase(network) ? "XDC" : "Ethereum";
  }

  /**
   * Human-readable asset list for a composite's email title (e.g. "USDC/USDT" for Ethereum's
   * Grouped composite, "XUSDC" for XDC's Segregated one) - the raw compositeId hex is opaque to a
   * reader and stays available in the body (Composite Id row) for exact traceability. Reads each
   * member token's real on-chain symbol() generically rather than matching against a hardcoded
   * asset list, so a newly added composite member needs no code change here. A per-token failure
   * falls back to a shortened address rather than failing the whole report over a display nicety.
   */
  private static String assetLabel(final String network, final List<String> memberTokenAddresses) {
    final List<String> assets = new ArrayList<>();
    for (final String tokenAddress : memberTokenAddresses) {
      try {
        assets.add(FundManagerLoader.getSymbol(network, tokenAddress));
      } catch (final Exception e) {
        LOGGER.warn("Could not resolve symbol() for " + tokenAddress + ", falling back to address.", e);
        assets.add(tokenAddress.substring(0, 8) + "…");
      }
    }
    return assets.isEmpty() ? "Composite" : String.join("/", assets);
  }

  private static void appendHtmlInfoRow(final StringBuilder html, final String label, final Object value) {
    html.append("<tr>")
        .append("<td style=\"padding:3px 12px 3px 0;color:#555555;\">").append(label).append("</td>")
        .append("<td style=\"padding:3px 0;font-weight:bold;\">").append(value).append("</td>")
        .append("</tr>");
  }
}

