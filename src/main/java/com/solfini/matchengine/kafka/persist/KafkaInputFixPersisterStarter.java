package com.solfini.matchengine.kafka.persist;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import com.solfini.common.Context;
import com.solfini.matchengine.LoggingThread;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class KafkaInputFixPersisterStarter  {

  public static void main(final String[] args) throws InterruptedException {
    if (args.length < 2) {
      System.out.println("Usage: java KafkaInputFixPersister [config.properties] [snapId] [replayId] [stopTime] [stopTimeString]  [startOffset]");
      System.exit(0);
    }

    // load properties
    try {
      File file = new File(args[0]);
      if (file.exists() && file.isDirectory()) {
        file = new File(args[0] + "/config.properties");
      }
      Properties overlay = new Properties();
      overlay.put("KAFKA.CONSUMER.group.id", "p_"+System.nanoTime());
      overlay.put("REPLAY_FROM_FILE_ENABLED", "FALSE");

      InputStream stream = new FileInputStream(file);
      PropertyReader.initialize(stream, overlay);
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      return;
    }


    final LoggingThread loggingThread = Context.getLoggingThread();
    new Thread(loggingThread, "loggingThread").start();


    final long snapId = args.length > 1 ? StringUtil.toLong(args[1]) : 0;
    final long replayId = args.length > 2 ? StringUtil.toLong(args[2]) : 0;
    long stopTime = args.length > 3 ? StringUtil.toLong(args[3]) : 0;
    final String stopTimeString = args.length > 3 ? args[4] : "";
    if(stopTimeString!=null && stopTimeString.length()>8 ) {
    	stopTime = StringUtil.getMillisFromDateYYYMMDDHHMMSSsss(stopTimeString);
    }
    final long startOffset = args.length > 5 ? StringUtil.toLong(args[5]) : 0;

    KafkaInputFixPersister listener = new KafkaInputFixPersister(snapId, replayId, stopTime, startOffset);
    Thread thread = new Thread(listener, "KafkaInputFixPersister_" + snapId + "_" + replayId);
    thread.start();
    Thread.sleep(1000000000);
  }
}
