package com.solfini.matchengine;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import com.solfini.matchengine.copytrade.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.IdleStrategyFactory;
import com.solfini.matchengine.controller.ControllerFactory;
import com.solfini.matchengine.controller.ControllerThread;
import com.solfini.matchengine.drmode.SnapLoader;
import com.solfini.report.ReportUtil;
import com.solfini.util.LogLevel;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;

/**
 *x
 * @author Chris Mack
 *
 */
public class MatchEngineStarter implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(MatchEngineStarter.class);



  public boolean initialize(String[] args) throws Exception {
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
      LOGGER.error(ERROR_LOG, e);
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
        LOGGER.error("Error: Invalid mode specified");
        return false;
      }
    }
    // snapshot
    if (cmd.hasOption("s")) {
      if (cmd.hasOption(COLD_START)) {
        System.err.println("Error: Cannot use snapshot and cold-start options together");
        LOGGER.error("Error: Cannot use snapshot and cold-start options together");
        return false;
      }
      overlay.setProperty("LOAD_FROM_SNAP", cmd.getOptionValue("s"));
    }
    // cold/warm start
    if (cmd.hasOption(WARM_START)) {
      if (cmd.hasOption(COLD_START)) {
        System.err.println("Error: Cannot use warm-start and cold-start options together");
        LOGGER.error("Error: Cannot use warm-start and cold-start options together");
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
      LOGGER.error(ERROR_LOG, e);
      System.err.println("Error: " + e.getMessage());
      return false;
    }

    // setup log level
    LogLevel.setLevel(Level.valueOf(PropertyReader.getProperty(Constants.LOGLEVEL, "INFO").trim().toUpperCase()));

    return true;
  }

  private void loadCachesFromDB() {
    if (Context.isCopyTradeEnabled()) {
      final AtomicInteger loaderCounter = new AtomicInteger(9);
      new Thread(() -> {
        InfluencerSubscriptionCache.loadFromDB(loaderCounter);
        while (true) {
          try {
            Thread.sleep(FIVE_MINUTE);
            InfluencerSubscriptionCache.loadUpdated();
          } catch (Exception e) {
          }
        }
      }).start();

      new Thread(() -> {
        ExternalInstrumentCache.loadFromDB(loaderCounter);
        try {
          ExternalInstrumentCache.loadFromExchange();//async loading
        } catch (Exception e) {
        }
        while (true) {
          try {
            Thread.sleep(ONE_DAY);
            ExternalInstrumentCache.loadFromExchange();
          } catch (Exception e) {
          }
        }
      }).start();

      new Thread(() -> {
        CopyTradeCache.loadFromDB(loaderCounter);
      }).start();

      new Thread(() -> {
        try {
          MarketCapCache.loadFromCoinMarketCap(loaderCounter);
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, "Failed to load Market Cap from CoinMarketCap. ", e);
        }
        while (true) {
          try {
            Thread.sleep(ONE_DAY);
            MarketCapCache.loadFromCoinMarketCap(null);
          } catch (Exception e) {
          }
        }
      }).start();

      new Thread(() -> {
        try {
          MarketDepthCache.loadFromCoinMarketCap(loaderCounter);
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, "Failed to load Market Depth from CoinMarketCap. ", e);
        }
        while (true) {
          try {
            Thread.sleep(ONE_DAY);
            MarketDepthCache.loadFromCoinMarketCap(null);
          } catch (Exception e) {
          }
        }
      }).start();

      new Thread(() -> {
        try {
          InfluencerSymbolsCache.loadFromDB(loaderCounter);
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, "Failed to InfluencerSymbolsCache. ", e);
        }
        while (true) {
          try {
            Thread.sleep(FIFTEEN_MINUTE);
            InfluencerSymbolsCache.loadFromMarketProphit();
          } catch (Exception e) {
          }
        }
      }).start();

      new Thread(() -> {
        ExternalExchangeHandler.loadCoinMarketCapPriceFromMPDB(loaderCounter);
        while (true) {
          try {
            Thread.sleep(TWO_MINUTE);
            ExternalExchangeHandler.loadCoinMarketCapPriceFromMPDB(null);
          } catch (Exception e) {
          }
        }
      }).start();

      new Thread(() -> {
        DefaultExchangeQuoteCache.loadFromDB(loaderCounter);
        while (true) {
          try {
            Thread.sleep(ONE_HOUR);
            DefaultExchangeQuoteCache.loadFromDB(null);
          } catch (Exception e) {
          }
        }
      }).start();

      new Thread(() -> {
        TickerTopBottomAccountCache.loadFromDB(loaderCounter);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Context.getTopBottomReloadHourInCest());
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DATE, 1);
        LocalDateTime nextUpdate = LocalDateTime.ofInstant(calendar.toInstant(), calendar.getTimeZone().toZoneId());

        while (true) {
          try {
            Thread.sleep(FIVE_MINUTE);
            TickerTopBottomAccountCache.loadFromDB(null);
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(nextUpdate)) {
              calendar.add(Calendar.DATE, 1);
              nextUpdate = LocalDateTime.ofInstant(calendar.toInstant(), calendar.getTimeZone().toZoneId());
              LOGGER.info("TickerTopBottomAccountCache.reloadFromDb() next: " + nextUpdate);

              TickerTopBottomAccountCache.reloadTopUsersFromDB();
            }
          } catch (Exception e) {
            LOGGER.error(ERROR_LOG, e);
          }
        }
      }).start();

      while (loaderCounter.get() != 0) {
      }
    }
  }

  private void startPersistThread() {
    LOGGER.info(LOG_FMT_2, ">> startPersistThread, enabled=", Context.isPersistModeEnabled());

    if (Context.isPersistModeEnabled()) {
      Context.setPersistThread(new PersistThread(IdleStrategyFactory.create(Context.getRiskThreadIdle())));
      new Thread(Context.getPersistThread(), "persistThread").start();
      Context.setPersistPositionThread(new PersistPositionThread(IdleStrategyFactory.create(Context.getRiskThreadIdle())));
      new Thread(Context.getPersistPositionThread(), "persistPositionThread").start();
    }
  }

  public void start() throws Exception {

    if (LogLevel.info()) {
      LOGGER.info("Starting up matching engine. version=2020-09-18-1, time={}", System.currentTimeMillis());
    }

    final LoggingThread loggingThread = Context.getLoggingThread();

    loadCachesFromDB();

    final MatchingThread matchingThread = Context.getMatchingThread();
    final PublisherThread publisherThread = Context.getPublisherThread();
    final KafkaPublisherThread kafkaPublisherThread =
        new KafkaPublisherThread(IdleStrategyFactory.create(Context.getPublisherThreadIdle()));

    LOGGER.info("starting threads");

    new Thread(loggingThread, "loggingThread").start();

    // Initialize the controller service thread
    new Thread(ControllerFactory.create(), "controllerService").start();

    new Thread(matchingThread, "matchingThread").start();
    new Thread(publisherThread, "publisherThread").start();
    new Thread(kafkaPublisherThread, "kafkaPublisherThread").start();
    new Thread(Context.getTimeTriggerThread(), "timeTriggerThread").start();

    startPersistThread();

    if (LogLevel.info()) {
      LOGGER.info(">>> Start Summary");
      LOGGER.info(">>> Start Time: {}", StringUtil.getCurrentDateYYYYMMDDHHMMSSsss());
      LOGGER.info(">>> Start InstanceId: {}", Context.getInstanceId());
      LOGGER.info(">>> Start Mode: {}", Context.getControllerMode());
      LOGGER.info(">>> Start isReplayFromFileEnabled: {}", Context.isReplayFromFileEnabled());
      LOGGER.info(">>> Start getReplayFromFileLocation: {}", Context.getReplayFromFileLocation());
      LOGGER.info(">>> Start isStateValidatorEnabled: {}", Context.isStateValidatorEnabled());
      LOGGER.info(">>> Start isCollateralSwapEnabled: {}", Context.isCollateralSwapEnabled());
      LOGGER.info(">>> Start isRejectDuplicateClorIdsEnabled: {}", Context.isRejectDuplicateClorIdsEnabled());
      LOGGER.info(">>> Start isUseOrderPoolEnabled: {}", Context.isUseOrderPoolEnabled());
      LOGGER.info(">>> Start isListenToIpcMarketData: {}", Context.isListenToIpcMarketData());
      LOGGER.info(">>> Start isListenToKafkaMarketData: {}", Context.isListenToKafkaMarketData());
      LOGGER.info(">>> Start isEnableCircuitBreaker: {}", Context.isEnableCircuitBreaker());
      LOGGER.info(">>> Start isEnableBalanceWithdrawExactLimits: {}", Context.isEnableBalanceWithdrawExactLimits());
      LOGGER.info(">>> Start isEnableBalanceWithdrawLimits: {}", Context.isEnableBalanceWithdrawLimits());
      LOGGER.info(">>> Start isPricingThreadEnabled: {}", Context.isPricingThreadEnabled());
      LOGGER.info(">>> Start isUserStatsEnabled: {}", Context.isUserStatsEnabled());
      LOGGER.info(">>> Start isContractExpiryEnabled: {}", Context.isContractExpiryEnabled());
      LOGGER.info(">>> Start getCircuitBreakerTimeInterval: {}", Context.getCircuitBreakerTimeInterval());

      LOGGER.info("inboundThread idle={}", Context.getInboundThreadIdle());
      LOGGER.info("matchingThread idle={}", Context.getMatchingThreadIdle());
      LOGGER.info("publisherThread idle={}", Context.getPublisherThreadIdle());
      LOGGER.info("persistThread idle={}", Context.getMatchingThreadIdle());
      LOGGER.info("riskThread idle={}", Context.getRiskThreadIdle());
      LOGGER.info("Threads started");
      LOGGER.info("Threads started. time={}", System.currentTimeMillis());
    }

    // Initialize the controller thread
    new Thread(new ControllerThread(), "controller").start();

    long j = 0;
    while (true) {
      try {
        Thread.sleep(300_000);

        ReportUtil.logReport();

        if (Context.isPublishHealthReportMail()) {
          if (j % 96 == 0) {
            ReportUtil.generate();
          }
          j++;
        }

      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    MatchEngineStarter starter = new MatchEngineStarter();
    if (starter.initialize(args)) {
      starter.start();
    }
  }
}
