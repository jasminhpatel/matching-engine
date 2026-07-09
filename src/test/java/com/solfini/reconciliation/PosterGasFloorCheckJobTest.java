package com.solfini.reconciliation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import org.junit.Test;

public class PosterGasFloorCheckJobTest {

  @Test
  public void toWei_convertsNativeToWei() {
    assertEquals(new BigInteger("20000000000000000"), PosterGasFloorCheckJob.toWei(0.02));
    assertEquals(new BigInteger("50000000000000000000"), PosterGasFloorCheckJob.toWei(50));
    assertEquals(BigInteger.ZERO, PosterGasFloorCheckJob.toWei(0));
  }

  @Test
  public void isBelowFloor_belowReturnsTrue() {
    assertTrue(PosterGasFloorCheckJob.isBelowFloor(BigInteger.ONE, BigInteger.TEN));
  }

  @Test
  public void isBelowFloor_exactlyAtFloorReturnsTrue() {
    assertTrue(PosterGasFloorCheckJob.isBelowFloor(BigInteger.TEN, BigInteger.TEN));
  }

  @Test
  public void isBelowFloor_aboveReturnsFalse() {
    assertFalse(PosterGasFloorCheckJob.isBelowFloor(BigInteger.valueOf(11), BigInteger.TEN));
  }

  @Test
  public void parseConfigPath_equalsForm_returnsPath() {
    final String path = PosterGasFloorCheckJob.parseConfigPath(new String[] {"-c=./test-config.properties"});
    assertEquals("./test-config.properties", path);
  }

  @Test
  public void parseConfigPath_missing_returnsDefault() {
    final String path = PosterGasFloorCheckJob.parseConfigPath(new String[] {});
    assertEquals("./config.properties", path);
  }

  @Test
  public void parseArg_dashDKeyValue_returnsValue() {
    final String value = PosterGasFloorCheckJob.parseArg(new String[] {"-d", "DRY_RUN=true"}, "DRY_RUN", "false");
    assertEquals("true", value);
  }

  @Test
  public void parseArg_missing_returnsDefault() {
    final String value = PosterGasFloorCheckJob.parseArg(new String[] {}, "DRY_RUN", "false");
    assertEquals("false", value);
  }
}
