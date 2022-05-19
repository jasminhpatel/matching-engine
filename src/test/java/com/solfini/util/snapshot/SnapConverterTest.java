package com.solfini.util.snapshot;

import java.nio.ByteBuffer;
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

public class SnapConverterTest {
  private static final String JSON_PATH = "SnapConverterTest.json";
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
  public void snapshotImportExport() {
    try {
      Properties properties = new Properties();
      properties.setProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", path.toString());
      properties.setProperty("PUBLISH_MARKET_DATA", "false");
      properties.setProperty("NUM_ENCODER_THREADS", "0");
      properties.setProperty("POSITION_REPORT_PARSER_START_CAPACITY", "64");
      PoolSize.minimize(properties);
      PropertyReader.initialize(null, properties);

      SnapConverter converter = new SnapConverter();
      long snapId = converter.importSnapshot(path.resolve(JSON_PATH).toString(), path.toString());
      converter.exportSnapshot(path.resolve(String.valueOf(snapId)).toString(), path.resolve("SnapConverterExport.json").toString());

      Assert.assertTrue(converter.validate());

      JsonReader source = new JsonReader(path.resolve(JSON_PATH).toString());
      JsonReader target = new JsonReader(path.resolve("SnapConverterExport.json").toString());

      JsonObject expected = source.next();
      JsonObject actual = target.next();

      while (actual != null && expected != null) {
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

  @Test
  public void bytesToNumberConverter(){
    ByteBuffer bb = ByteBuffer.wrap(new byte[] {110, 117, 108, 108, 48, 54, 99, 102, 51, 101, 97, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
    long l = bb.getLong();
    System.out.println(l);
  }

  public static void main(String[] args) {
    if (args.length != 2) {
      System.out.println("Usage: SnapConverterTest <json1> <json2>");
      return;
    }

    try {
      JsonReader source = new JsonReader(args[0]);
      JsonReader target = new JsonReader(args[1]);

      JsonObject expected = source.next();
      JsonObject actual = target.next();

      int line = 1;
      int mismatch = 0;
      boolean printed = false;
      while (actual != null && expected != null) {
        if (line % 100000 == 0) {
          System.out.print(".");
          printed = true;
        }

        if (!actual.equals(expected)) {
          if (printed) {
            System.out.println();
            printed = false;
          }

          mismatch++;
          System.out.println("Mismatch found at line " + line);
          System.out.println("Expected:");
          System.out.println(expected.toString());
          System.out.println("Actual:");
          System.out.println(actual.toString());
          System.out.println();
        }

        ++line;
        actual = source.next();
        expected = target.next();
      }

      if (printed) {
        System.out.println();
      }

      if (null != expected) {
        System.out.println("More records available in " + args[0]);
        System.exit(1);
      }

      if (null != actual) {
        System.out.println("More records available in " + args[1]);
        System.exit(1);
      }

      if (mismatch > 0) {
        System.exit(1);
      }

      System.out.println("" + (line - 1) + " records match");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
