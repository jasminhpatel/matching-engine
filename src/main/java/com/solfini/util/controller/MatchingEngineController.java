package com.solfini.util.controller;

import java.io.File;
import java.io.FileInputStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.util.PropertyReader;

public class MatchingEngineController implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MatchingEngineController.class);
  private static final String CONTROLLER_KAFKA = "kafka";
  private static final String CONTROLLER_UNIFIED = "unified";
  private static final String CONTROLLER_ZOOKEEPER = "zookeeper";

  private static void primary(final String controller, final String instance) {
    if (controller.equals(CONTROLLER_KAFKA) || controller.equals(CONTROLLER_UNIFIED)) {
      System.out.println("Promoting " + instance + " to primary");
      LOGGER.info(LOG_FMT_3, "Promoting ", instance, " to primary");
      final KafkaControllerPublisher publisher = new KafkaControllerPublisher();
      publisher.primary(instance);
    } else {
      throw new UnsupportedOperationException(OPERATION_UNSUPPORTED_ON + controller + CONTROLLER);
    }
  }

  private static void priority(final String controller, final String instance, final long instancePriority) {
    if (controller.equals(CONTROLLER_ZOOKEEPER)) {
      System.out.println("Setting " + instance + " priority to " + instancePriority);
      LOGGER.info(LOG_FMT_4, "Setting ", instance, " priority to ", instancePriority);
      final KafkaControllerPublisher publisher = new KafkaControllerPublisher();
      publisher.priority(instance, instancePriority);
    } else {
      throw new UnsupportedOperationException(OPERATION_UNSUPPORTED_ON + controller + CONTROLLER);
    }
  }

  private static void shutdown(final String controller, final String instance, final String promoteInstance) {
    if (controller.equals(CONTROLLER_KAFKA) || controller.equals(CONTROLLER_UNIFIED) || controller.equals(CONTROLLER_ZOOKEEPER)) {
      if (null == promoteInstance) {
        System.out.println("Shutting down " + instance);
        LOGGER.info(LOG_FMT_2, "Shutting down ", instance);
        final KafkaControllerPublisher publisher = new KafkaControllerPublisher();
        publisher.shutdown(instance);
      } else {
        System.out.println("Shutting down " + instance + " and promoting " + promoteInstance + " to primary");
        LOGGER.info(LOG_FMT_5, "Shutting down ", instance, " and promoting ", promoteInstance, " to primary");
        final KafkaControllerPublisher publisher = new KafkaControllerPublisher();
        publisher.shutdownAndPromote(instance, promoteInstance);
      }
    } else {
      throw new UnsupportedOperationException(OPERATION_UNSUPPORTED_ON + controller + CONTROLLER);
    }
  }

  private static void snapshot(final String controller, final String instance) {
    System.out.println("Sending snapshot request to " + instance);
    LOGGER.info(LOG_FMT_2, "Sending snapshot request to ", instance);
    final KafkaInputPublisher publisher = new KafkaInputPublisher();
    if (instance.equalsIgnoreCase("secondaries") || instance.equalsIgnoreCase("all-secondaries")) {
      publisher.requestSnapshot("secondary");
    } else {
      publisher.requestSnapshot(instance);
    }
  }

  private static void status(final String controller, final String instance) {
    if (controller.equals(CONTROLLER_KAFKA) || controller.equals(CONTROLLER_UNIFIED) || controller.equals(CONTROLLER_ZOOKEEPER)) {
      System.out.println("Requesting status of " + instance);
      LOGGER.info(LOG_FMT_2, "Requesting status of ", instance);
      final KafkaControllerPublisher publisher = new KafkaControllerPublisher();
      publisher.status(instance);
    } else {
      throw new UnsupportedOperationException(OPERATION_UNSUPPORTED_ON + controller + CONTROLLER);
    }
  }

  public static void main(String[] args) {

    final Options options = new Options();
    options.addOption(Option.builder("h").longOpt("help").desc("show help").required(false).build());
    options.addOption(Option.builder("c").longOpt("conf").desc("configuration file").hasArg().argName("file").required().build());
    options.addOption(Option.builder("i").longOpt("instance").desc("matching engine instance identity").hasArg().argName("id").required().build());
    options.addOption(Option.builder().longOpt("primary").desc("promote to primary").required(false).build());
    options.addOption(Option.builder().longOpt(PRIORITY).desc("set instance priority").hasArg().argName(PRIORITY).required(false).build());
    options.addOption(Option.builder().longOpt("snapshot").desc("generate a snapshot").required(false).build());
    options.addOption(Option.builder().longOpt("shutdown").desc("initiate graceful shutdown").required(false).build());
    options.addOption(Option.builder().longOpt("promote").desc("instance to promote after shutdown").hasArg().argName("id").required(false).build());
    options.addOption(Option.builder().longOpt("status").desc("report instance status on the log").required(false).build());

    for (String arg : args) {
      if (arg.equals("-h") || arg.equals("--help")) {
        System.out.println("MatchingEngineController - Send control messages to matching engines");
        System.out.println();

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("MatchingEngineController", options);
        System.out.println();

        System.out.println("example: Switch matching engine 'me01' to primary");
        System.out.println("         ./scripts/mectrl.sh -c config.properties -i me01 --primary");
        System.out.println();

        System.out.println("example: Generate a snapshot using matching engine 'me01'");
        System.out.println("         ./scripts/mectrl.sh -c config.properties -i me01 --snapshot");
        System.out.println();

        System.out.println("example: Shutdown matching engine 'me01'");
        System.out.println("         ./scripts/mectrl.sh -c config.properties -i me01 --shutdown");
        System.out.println();

        System.out.println("example: Shutdown primary matching engine 'me01' and promote 'me02' as primary");
        System.out.println("         ./scripts/mectrl.sh -c config.properties -i me01 --shutdown --promote me02");
        System.out.println();

        System.out.println("example: Report status of matching engine 'me01'");
        System.out.println("         ./scripts/mectrl.sh -c config.properties -i me01 --status");
        System.out.println();

        System.out.println("example: Set instance priority of matching engine 'me01' to 100");
        System.out.println("         ./scripts/mectrl.sh -c config.properties -i me01 --priority 100");
        System.out.println();

        return;
      }
    }

    try {
      final CommandLineParser parser = new DefaultParser();
      final CommandLine cmd = parser.parse(options, args);

      File file = new File(cmd.getOptionValue("c"));
      if (file.exists() && file.isDirectory()) {
        file = new File(cmd.getOptionValue("c") + "/config.properties");
      }

      PropertyReader.initialize(new FileInputStream(file), null);

      final String controller = PropertyReader.getProperty("CONTROLLER_TYPE", "static");
      final String instance = cmd.getOptionValue("i").trim();
      if (cmd.hasOption("primary")) {
        primary(controller, instance);
      } else if (cmd.hasOption(PRIORITY)) {
        priority(controller, instance, Long.parseLong(cmd.getOptionValue(PRIORITY)));
      } else if (cmd.hasOption("shutdown")) {
        shutdown(controller, instance, cmd.getOptionValue("promote", null));
      } else if (cmd.hasOption("snapshot")) {
        snapshot(controller, instance);
      } else if (cmd.hasOption("status")) {
        status(controller, instance);
      } else {
        throw new IllegalArgumentException("No valid operation specified");
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      System.err.println("ERROR: " + e.getMessage());
      System.err.println("Run with --help option for usage information");
      System.exit(1);
    }

    System.exit(0);
  }
}
