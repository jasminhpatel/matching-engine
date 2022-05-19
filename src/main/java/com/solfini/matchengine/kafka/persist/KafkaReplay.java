package com.solfini.matchengine.kafka.persist;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Properties;

import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

public class KafkaReplay {

  public static void main(String[] args) {

    final Options options = new Options();
    options.addOption(Option.builder("h").longOpt("help").desc("show help").required(false).build());
    options.addOption(Option.builder("c").longOpt("config").desc("configuration file").hasArg().argName("file").required().build());
    options.addOption(Option.builder("r").longOpt("replay").desc("replay directory").hasArg().argName("dir").required().build());
    options.addOption(Option.builder("n").desc("limit number of messages").hasArg().argName("limit").required(false).build());
    options.addOption(Option.builder("d").desc("delay between messages (ms)").hasArg().argName("delay").required(false).build());

    for (String arg : args) {
      if (arg.equals("-h") || arg.equals("--help")) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("KafkaReplay", options);
        System.out.println();
        return;
      }
    }

    // initialize
    String replay = null;
    long limit = 0;
    long delay = 0;
    CommandLineParser parser = new DefaultParser();
    try {
      final CommandLine cmd = parser.parse(options, args);

      File file = new File(cmd.getOptionValue("c"));
      if (file.exists() && file.isDirectory()) {
        file = new File(cmd.getOptionValue("c") + "/config.properties");
      }

      final Properties properties = new Properties();
      PoolSize.minimize(properties);

      InputStream stream = new FileInputStream(file);
      PropertyReader.initialize(stream, properties);

      replay = cmd.getOptionValue("r");
      if (cmd.hasOption("n")) {
        limit = Long.parseLong(cmd.getOptionValue("n"));
      }
      if (cmd.hasOption("d")) {
        delay = Long.parseLong(cmd.getOptionValue("d"));
      }
    } catch (Exception e) {
      System.out.println("ERROR: " + e.getMessage());
      e.printStackTrace();
      System.out.println("Run with --help option for usage information");
      System.exit(1);
    }

    // replay
    final ChronicleQueue queue = SingleChronicleQueueBuilder.single(replay).blockSize(1048576).rollCycle(RollCycles.DAILY).build();
    final ExcerptTailer tailer = queue.createTailer();
    final Bytes<ByteBuffer> bytes = Bytes.elasticHeapByteBuffer(32768);

    final KafkaPublisher publisher = new KafkaPublisher(0);
    final String topic = PropertyReader.getProperty("API_KAFKA_TOPIC_IN", "api");

    System.out.println("REPLAY: " + replay + " -> " + topic);
    long count = 1;
    while (true) {
      bytes.clear();
      boolean read = tailer.readBytes(bytes);

      if (read) {
        byte[] data = bytes.underlyingObject().array();
        int length = (int) bytes.readRemaining();

        byte[] message = new byte[length - 8];
        System.arraycopy(data, 8, message, 0, length - 8);
        long sequence = count;
        for (int i = 7; i >= 0; i--) {
          message[i] = (byte) (sequence & 0xFF);
          sequence >>= 8;
        }

        if (delay != 0) {
          try {
            Thread.sleep(delay);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }

        publisher.sendDirect(message, topic);
        if (++count % 1000 == 0) {
          System.out.print(".");
        }

        if ((limit != 0) && (count > limit)) {
          publisher.close();
          System.exit(0);
        }
      }
    }
  }
}
