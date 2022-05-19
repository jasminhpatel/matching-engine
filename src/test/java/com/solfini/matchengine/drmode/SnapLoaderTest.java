package com.solfini.matchengine.drmode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.slf4j.event.Level;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.snapshot.SnapConverter;

public class SnapLoaderTest {
  private static final String JSON_PATH = "SnapLoaderTest.json";
  private Path path = null;

  @Before
  public void before() {
    LogLevel.setLevel(Level.TRACE);

    try {
      path = Files.createTempDirectory(System.getProperty("java.io.tmp"));
      Files.copy(getClass().getResourceAsStream("/" + JSON_PATH), path.resolve(JSON_PATH));
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void loadSnapshot() {
    try {
      Properties properties = new Properties();
      properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "65536");
      properties.setProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", path.toString());
      properties.setProperty("PUBLISH_MARKET_DATA", "false");
      properties.setProperty("NUM_ENCODER_THREADS", "0");
      properties.setProperty("POSITION_REPORT_PARSER_START_CAPACITY", "64");
      properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
      properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
      properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
      PoolSize.minimize(properties);
      PropertyReader.initialize(null, properties);

      SnapConverter converter = new SnapConverter();
      long snapId = converter.importSnapshot(path.resolve(JSON_PATH).toString(), path.toString());

      SnapLoader loader = new SnapLoader(snapId);
      loader.load();

      List<Message> messages = new ArrayList<Message>();
      Context.getReceiverToMatcherQueue().drainTo(messages, 1000);
      Assert.assertEquals(870 - 28 - 152 - 78 - 1, messages.size());

      for (Message message : messages) {
        System.out.println(message);
      }
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }
}
