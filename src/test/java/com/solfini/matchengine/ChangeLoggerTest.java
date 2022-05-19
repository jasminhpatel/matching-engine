package com.solfini.matchengine;

import java.text.NumberFormat;
import java.util.Properties;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.event.Level;

import com.solfini.common.CustomLogger;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

public class ChangeLoggerTest {

  @BeforeClass
  public static void before() {
    LogLevel.setLevel(Level.TRACE);
    NumberFormat.getInstance().setGroupingUsed(true);

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "1000000");
      properties.setProperty("ORDER_POOL_START_CAPACITY", "1000000");
      properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "1000000");
      properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "1000000");
      PropertyReader.initialize(null, properties);

      System.out.println("Warming up object pools");
      OrderObjectPool.init();
      ExecutionReportObjectPool.init();
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testChange() {
    CustomLogger LOGGER = CustomLogger.getLogger(ChangeLoggerTest.class);
    Assert.assertEquals(true, LOGGER.isDebugEnabled());
    Assert.assertEquals(true, LOGGER.isWarnEnabled());

    LogLevel.changeLevelAfterStart(Level.WARN);

    Assert.assertEquals(false, LOGGER.isDebugEnabled());
    Assert.assertEquals(true, LOGGER.isWarnEnabled());

  }

}
