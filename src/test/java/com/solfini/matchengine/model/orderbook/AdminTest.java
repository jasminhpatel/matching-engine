package com.solfini.matchengine.model.orderbook;

import java.io.IOException;
import java.util.Properties;
import org.slf4j.event.Level;
import org.junit.Assert;
import org.junit.Test;

import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

/**
 *
 * @author Chris Mack
 *
 */
public class AdminTest extends ModelTest {

  protected void configure() {
    LogLevel.setLevel(Level.TRACE);

    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "4194304");
    properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "4028");
    properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "1024");
    properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_POOL_START_CAPACITY", "32768");
    properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "32768");
    properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "32768");
    properties.setProperty("NUM_ENCODER_THREADS", "0");
    properties.setProperty("NUM_DECODER_THREADS", "8");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    // onConfigure(properties);

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /*
   * Create 2 instruments. Create an instrument pair quotedd on them. Patch an instrument. Patch the instrument pair.
   */
  @Test
  public void testSecurityDefinitionAddAndUpdate() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(150, UpdateType.PUT, "DBG", 4, 3));
    expectMessage(
        "symbol=DBG, securityId=150, assetType=ASSET, priceScale=4, quantityScale=3, orderBookStrategy=2, preOrderCheckStrategy=11");

    final Instrument instrument1 = InstrumentCache.get(150);
    System.out.println("Instrument: " + instrument1);
    Assert.assertNotNull(instrument1);
    Assert.assertEquals("DBG", instrument1.getSymbol());
    Assert.assertNull(InstrumentCache.get(151));

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(151, UpdateType.PUT, "STORM", 2, 2));
    expectMessage(
        "symbol=STORM, securityId=151, assetType=ASSET, priceScale=2, quantityScale=2, orderBookStrategy=2, preOrderCheckStrategy=11");

    final Instrument instrument2 = InstrumentCache.get(151);
    System.out.println("Instrument: " + instrument2);
    Assert.assertNotNull(instrument2);
    Assert.assertEquals("STORM", instrument2.getSymbol());

    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(111, UpdateType.PUT, "DBG/STORM", 150, 151, 2, 2));
    expectMessage("symbol=DBG/STORM, securityId=111, assetType=PERPETUAL_SWAP, baseId=150, quotedId=151, priceScale=2, quantityScale=2");

    InstrumentPair instrumentPair = InstrumentCache.getPair(111);
    Assert.assertNotNull(instrumentPair);
    Assert.assertEquals("DBG/STORM", instrumentPair.getSymbol());
    Assert.assertEquals(2, instrumentPair.getPriceScale());
    Assert.assertEquals(2, instrumentPair.getQuantityScale());
    Assert.assertNotNull(instrumentPair.getOrderBook());

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(150, UpdateType.PATCH, "DBG", 4, 4));
    expectMessage(
        "symbol=DBG, securityId=150, assetType=ASSET, priceScale=4, quantityScale=4, orderBookStrategy=2, preOrderCheckStrategy=11");

    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(111, UpdateType.PATCH, "DBG/STORM", 150, 151, 8, 4));
    expectMessage("symbol=DBG/STORM, securityId=111, assetType=PERPETUAL_SWAP, baseId=150, quotedId=151, priceScale=8, quantityScale=4");

    instrumentPair = InstrumentCache.getPair(111);
    Assert.assertNotNull(instrumentPair);
    Assert.assertEquals("DBG/STORM", instrumentPair.getSymbol());
    Assert.assertEquals(8, instrumentPair.getPriceScale());
    Assert.assertEquals(4, instrumentPair.getQuantityScale());
    Assert.assertEquals(150, instrumentPair.getBaseId());
    Assert.assertEquals(151, instrumentPair.getQuotedId());

    assertMessages();
  }

}
