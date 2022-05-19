package com.solfini.binance.snapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.event.Level;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import com.google.gson.JsonObject;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.snapshot.JsonReader;
import com.solfini.util.snapshot.SnapConverter;

public class BinanceSnapshotExportTest {
  private static final String SNAP_PATH = "1568632408916376730";
  private static final String JSON_PATH = "1568632408916376730.json";
  private Path path = null;

  @Before
  public void before() {
    LogLevel.setLevel(Level.TRACE);

    try {
      path = Files.createTempDirectory(System.getProperty("java.io.tmp"));
      Files.copy(getClass().getResourceAsStream("/binance/" + JSON_PATH), path.resolve(JSON_PATH));
      for (String file : new String[] { "20190916.cq4", "metadata.cq4t", "done" }) {
        Files.copy(getClass().getResourceAsStream("/binance/" + SNAP_PATH + "/" + file), path.resolve(file));
      }
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void exportSnapshotAndCompare() {
    try {
      Properties properties = new Properties();
      properties.setProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", path.toString());
      properties.setProperty("PUBLISH_MARKET_DATA", "false");
      properties.setProperty("NUM_ENCODER_THREADS", "0");
      properties.setProperty("POSITION_REPORT_PARSER_START_CAPACITY", "64");
      PoolSize.minimize(properties);
      PropertyReader.initialize(null, properties);

      SnapConverter converter = new SnapConverter();
      converter.exportSnapshot(path.toString(), path.resolve("SnapConverterExport.json").toString());

      JsonReader source = new JsonReader(path.resolve(JSON_PATH).toString());
      JsonReader target = new JsonReader(path.resolve("SnapConverterExport.json").toString());

      JsonObject expected = source.next();
      JsonObject actual = target.next();

      while (actual != null && expected != null) {
        if (actual.get("class").getAsString().equals("SecurityDefinitionAdminMessage")) {
          actual.remove("secondaryOrderId");
          actual.remove("secondaryExecId");
          actual.remove("marketStatus");
          actual.remove("expireRollTimeMillis");
          actual.remove("symbolRollCount");
        } else if (actual.get("class").getAsString().equals("SnapResponseAdminMessage")) {
          actual.remove("orderId");
          actual.remove("execId");
        } else if (actual.get("class").getAsString().equals("Order")) {
          actual.remove("availableEstimatedQuantity");
          actual.remove("availableAccumulatedQuantity");
        }

        if (expected.get("class").getAsString().equals("SecurityDefinitionAdminMessage")) {
          expected.remove("secondaryOrderId");
          expected.remove("secondaryExecId");
          expected.remove("marketStatus");
          expected.remove("expireRollTimeMillis");
          expected.remove("symbolRollCount");
        } else if (expected.get("class").getAsString().equals("SnapResponseAdminMessage")) {
          expected.remove("orderId");
          expected.remove("execId");
        } else if (expected.get("class").getAsString().equals("Order")) {
          expected.remove("availableEstimatedQuantity");
          expected.remove("availableAccumulatedQuantity");
        }

        Assert.assertEquals(expected, actual);

        actual = source.next();
        expected = target.next();
      }
      Assert.assertNull(expected);
      Assert.assertNull(actual);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }
}
