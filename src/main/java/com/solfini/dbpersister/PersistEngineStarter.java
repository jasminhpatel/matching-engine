package com.solfini.dbpersister;

import com.solfini.matchengine.persist.PersisterPosition;
import com.solfini.reconciliation.FolderUtils;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class PersistEngineStarter {
  private static final Logger LOGGER = LoggerFactory.getLogger(PersistEngineStarter.class);

  public PersistEngineStarter() {
  }

  private void loadSnap() {
    final String snapDirectory = PropertyReader.getProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", "./snap");
    LOGGER.info("CHRONICLE_ENGINE_SNAP_DIRECTORY: " + snapDirectory);
    final List<String> snapshotIds = FolderUtils.getLastNTimestampFolders(snapDirectory, 1);
    final String snapshotId = snapshotIds.getFirst();
    LOGGER.info("snapshotId: " + snapshotId);

    final DbPersistSnapLoader snapLoader = new DbPersistSnapLoader(StringUtil.toLong(snapshotId));
    snapLoader.loadSnapshot();
  }

  private void startProcessionQueue() {
    final String topic = PropertyReader.getProperty("ME_KAFKA_TOPIC_PRIMARY", "me01");
    final long startKafkaOffset = PersisterPosition.getMaxKafkaRecordOffset();
    DbPersistKafkaListener kafkaListener = new DbPersistKafkaListener(topic, startKafkaOffset);
    final Thread listener = new Thread(kafkaListener, "DbPersistKafkaListenerThread");
    listener.start();
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

  public static void main(String[] args) throws InterruptedException {
    System.out.println("Starting Persister Engine.");
    LOGGER.info("Starting Persister Engine.");
    final Properties overlay = new Properties();
    final boolean configurationsLoaded = loadConfigurationFile(overlay);
    if (!configurationsLoaded) {
      System.out.println("Error occurred while loading the configurations.");
      LOGGER.info("Error occurred while loading the configurations.");
    } else {
      System.out.println("Configurations loaded.");
      LOGGER.info("Configurations loaded.");
    }

    final PersistEngineStarter persisterEngine = new PersistEngineStarter();
    persisterEngine.loadSnap();
    System.out.println("Snap loading completed.");
    LOGGER.info("Snap loading completed.");
    persisterEngine.startProcessionQueue();
    System.out.println("Processing started.");
    LOGGER.info("Processing started.");

    CountDownLatch latch = new CountDownLatch(1);

    // Add shutdown hook so you can stop gracefully
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("Shutdown signal received. Releasing latch...");
      LOGGER.info("Shutdown signal received. Releasing latch...");
      latch.countDown();
    }));

    // Block here until latch is released
    latch.await();
    System.out.println("Persister Engine stopped.");
    LOGGER.info("Persister Engine stopped.");
  }
}
