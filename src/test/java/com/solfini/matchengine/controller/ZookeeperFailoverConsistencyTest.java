package com.solfini.matchengine.controller;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.solfini.common.Context;
import com.solfini.common.IdleStrategyFactory;
import com.solfini.matchengine.KafkaPublisherThread;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.publisher.MessagePublisher;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

public class ZookeeperFailoverConsistencyTest {

  public ZookeeperFailoverConsistencyTest() {}

  public void run() throws Exception {
    System.out.println("Running ZookeeperFailoverConsistencyTest");

    User user = new User(50);
    user.setLogin("test_user");
    user.setPassword("password123");
    user.setFirmId(0);
    user.setFeeTier(2);
    user.setLmm(false);

    ExecutionReportMessage execReport = new ExecutionReportMessage();
    execReport.setClOrdId("ABC");
    execReport.setSymbol("XX");
    execReport.setSide(Side.BUY);
    execReport.setOrdType(OrdType.LIMIT);
    execReport.setUser(user);
    execReport.setExecType(ExecType.NEW);
    execReport.setOrdStatus(OrdStatus.NEW);
    execReport.setSenderCompId(Context.getInstanceId());

    MessagePublisher publisher = new MessagePublisher();
    System.out.println("Setting output topic");
    Context.getKafkaPublisher().setTopic(PropertyReader.getProperty("ME_KAFKA_TOPIC_PRIMARY", "me1"));

    System.out.println("Starting KafkaPublisherThread");
    Thread thread =
        new Thread(new KafkaPublisherThread(IdleStrategyFactory.create(Context.getPublisherThreadIdle())), "kafkaPublisherThread");
    thread.start();

    System.out.println("Publishing exec report");
    publisher.publish(execReport);
    System.out.println("Flushing output");
    Context.getKafkaPublisher().flush();
    System.out.println("Done.");
    System.exit(0);
  }

  public static void main(String[] args) {
    try {
      Options options = new Options();
      options.addOption(Option.builder("h").longOpt("help").desc("show help").required(false).build());
      options.addOption(Option.builder("c").longOpt("conf").desc("configuration file").hasArg().argName("file").required().build());
      options.addOption(Option.builder("i").longOpt("instance").desc("instance name").hasArg().argName("file").required().build());

      for (final String arg : args) {
        if (arg.equals("-h") || arg.equals("--help")) {
          final HelpFormatter formatter = new HelpFormatter();
          formatter.printHelp("IntegrationTest", options);
          System.out.println();
          return;
        }
      }

      CommandLine cmd;
      try {
        CommandLineParser parser = new DefaultParser();
        cmd = parser.parse(options, args);
      } catch (Exception e) {
        System.err.println(e.getMessage());
        System.err.println("Run with --help option for usage information");
        return;
      }

      // Load config
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("NUM_ENCODER_THREADS", "0");
      properties.setProperty("NUM_DECODER_THREADS", "0");
      properties.setProperty("LOG_LEVEL", "DEBUG");

      String instance = cmd.getOptionValue("i", null);
      if (instance != null) {
        properties.setProperty("INSTANCE_ID", instance);
      }

      PropertyReader.initialize(new FileInputStream(new File(cmd.getOptionValue("c"))), properties);

      // Run
      new ZookeeperFailoverConsistencyTest().run();

    } catch (Exception e) {
      System.err.println("ERROR: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
